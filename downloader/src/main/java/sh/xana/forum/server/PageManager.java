package sh.xana.forum.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.Record7;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.RecieveRequest;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.common.ipc.ScraperUpload;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.Sites;
import sh.xana.forum.server.db.tables.records.PageredirectsRecord;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.OverviewEntry;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.parser.Auditor;
import sh.xana.forum.server.parser.PageParser;

/** Parse stage. Extract further URLs for downloading */
public class PageManager implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(PageManager.class);

  private final DatabaseStorage dbStorage;
  private final ServerConfig config;
  private final SqsManager sqsManager;
  private final Thread spiderThread;
  //  private final Thread uploadsThread;
  private final Thread downloadsThread;

  private final PageParser pageParser;

  public PageManager(DatabaseStorage dbStorage, ServerConfig config, SqsManager sqsManager) {
    this.dbStorage = dbStorage;
    this.config = config;
    this.sqsManager = sqsManager;

    this.spiderThread = new Thread(this::pageSpiderThread);
    this.spiderThread.setName("PageSpider");

    //    this.uploadsThread = new Thread(this::uploadsThread);
    //    this.uploadsThread.setName("PageUploads");
    Auditor.threadRunner(2, "PageUploads-", this::uploadsThread);

    this.downloadsThread = new Thread(this::downloadsThread);
    this.downloadsThread.setName("PageDownloads");

    this.pageParser = new PageParser(config);
  }

  public void startThreads() {
    this.spiderThread.start();
    //    this.uploadsThread.start();
    this.downloadsThread.start();
  }

  public void uploadsThread() {
    while (true) {
      boolean result;
      try {
        result = processUploads();
      } catch (Exception e) {
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
      if (!result) {
        log.warn("mainLoop returned false, stopping");
        break;
      }
    }
    log.info("main loop ended");
  }

  /** Process responses the download scraper collected */
  public boolean processUploads() throws IOException, InterruptedException {
    while (true) {
      List<RecieveRequest<ScraperUpload>> recieveRequests = sqsManager.receiveUploadRequests();
      if (recieveRequests.isEmpty()) {
        log.debug("No uploads to process");
        sleep();
        return true;
      }

      _processUploads(recieveRequests);
    }
  }

  public void _processUploads(List<RecieveRequest<ScraperUpload>> recieveRequests)
      throws IOException, InterruptedException {
    List<PageredirectsRecord> sqlNewRedirects = new ArrayList<>();
    for (RecieveRequest<ScraperUpload> successMessage : recieveRequests) {
      ScraperUpload success = successMessage.obj();
      log.debug("Writing " + success.pageId().toString() + " response and header");
      try {
        PagesRecord page = dbStorage.getPage(success.pageId());
        if (page.getPageurl().equals("asdf")) {
          throw new RuntimeException("que?");
        }
      } catch (Exception e) {
        log.error(
            "Failed to get page "
                + success.pageId()
                + " url "
                + success.pageUrl()
                + " headers "
                + success.headers(),
            e);
        continue;
      }

      // Body and headers can be null if the request just failed
      byte[] body = success.body();
      body = body == null ? new byte[0] : body;

      String headers;
      if (success.headers() == null) {
        headers = "";
      } else {
        headers = Utils.jsonMapper.writeValueAsString(success.headers());
      }

      Files.write(pageParser.getPagePath(success.pageId()), body);
      Files.writeString(pageParser.getPageHeaderPath(success.pageId()), headers);

      dbStorage.movePageDownloadToParse(success.pageId(), success.responseCode());

      if (!success.redirectList().isEmpty()) {
        byte counter = 0;
        URI lastUri = null;
        for (URI newUri : success.redirectList()) {
          sqlNewRedirects.add(new PageredirectsRecord(success.pageId(), newUri, counter++));
          lastUri = newUri;
        }
        try {
          dbStorage.setPageURL(success.pageId(), lastUri);
        } catch (Exception e) {
          if (e.getCause() instanceof SQLIntegrityConstraintViolationException
              && e.getCause().getMessage().contains("Duplicate entry")) {
            // we have redirected to an existing page. So we don't need this anymore
            dbStorage.deletePage(success.pageId());
            continue;
          } else {
            throw e;
          }
        }
      }

      if (success.exception() != null) {
        dbStorage.setPageException(success.pageId(), success.exception());
      }
    }

    if (!sqlNewRedirects.isEmpty()) {
      log.debug("dbsync redirect");
      dbStorage.insertPageRedirects(sqlNewRedirects);
    }

    sqsManager.deleteUploadRequests(recieveRequests);
  }

  /** */
  private void pageSpiderThread() {
    while (true) {
      boolean result;
      try {
        result = spider();
      } catch (Exception e) {
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
      if (!result) {
        log.warn("mainLoop returned false, stopping");
        break;
      }
    }
    log.info("main loop ended");
  }

  private boolean spider() throws InterruptedException {
    while (_spider()) {}

    sleep();
    return true;
  }

  private boolean _spider() throws InterruptedException {
    Result<Record7<UUID, UUID, URI, PageType, String, URI, ForumType>> parserPages;
    parserPages = dbStorage.getParserPages();
    if (parserPages.isEmpty()) {
      return false;
    }

    List<UUID> sqlDone = new ArrayList<>(parserPages.size());
    List<PagesRecord> sqlNewPages = new ArrayList<>();
    for (var page : parserPages) {
      UUID pageId = page.get(Pages.PAGES.PAGEID);
      URI siteBaseUrl = page.get(Sites.SITES.SITEURL);
      PageType pageType = page.get(Pages.PAGES.PAGETYPE);

      log.info("processing page {} {}", page.get(Pages.PAGES.PAGEURL), pageId);

      String output = null;
      try {
        ParserResult results =
            pageParser.parsePage(
                Files.readAllBytes(pageParser.getPagePath(pageId)), pageId, siteBaseUrl.toString());
        if (results.loginRequired()) {
          throw new SpiderWarningException("LoginRequired");
        }
        if (!results.pageType().equals(pageType)) {
          throw new SpiderWarningException(
              "Expected pageType " + pageType + " got " + results.pageType());
        }

        ForumType siteType = page.get(Sites.SITES.FORUMTYPE);
        if (!results.forumType().equals(siteType)) {
          throw new SpiderWarningException(
              "Expected forumType " + siteType + " got " + results.forumType());
        }

        String pageDomain = page.get(Pages.PAGES.DOMAIN);
        for (ParserResult.ParserEntry result : results.subpages()) {
          if (!result.url().startsWith("http://" + pageDomain + "/")
              && !result.url().startsWith("https://" + pageDomain + "/")) {
            log.warn(
                "Got prefix {} expected https://{}/ for pageId {}",
                result.url(),
                pageDomain,
                pageId);
          }
          URI url = new URI(result.url());
          if (!pageDomain.equals(url.getHost())) {
            throw new SpiderWarningException(
                "Expected domain " + pageDomain + " got " + url.getHost() + " for url " + url);
          }

          PagesRecord newPage = new PagesRecord();
          newPage.setSiteid(page.get(Pages.PAGES.SITEID));
          newPage.setPageurl(url);
          newPage.setPagetype(result.pageType());
          newPage.setDlstatus(DlStatus.Queued);
          newPage.setDomain(pageDomain);
          newPage.setSourcepageid(pageId);
          sqlNewPages.add(newPage);
        }

        sqlDone.add(pageId);
      } catch (JsonProcessingException e) {
        log.warn("JSON Parsing failed, found error " + output);
        dbStorage.setPageException(pageId, "NOT JSON\r\n" + output);
      } catch (SpiderWarningException e) {
        // same thing as below, just don't spam the log file
        dbStorage.setPageException(pageId, ExceptionUtils.getStackTrace(e));
      } catch (Exception e) {
//        log.warn("Failed in parser", e);
        dbStorage.setPageException(pageId, ExceptionUtils.getStackTrace(e));
      }
    }

    if (!sqlDone.isEmpty()) {
      log.debug("dbsync dlstatus done");
      dbStorage.setPageStatus(sqlDone, DlStatus.Done);
    }
    if (!sqlNewPages.isEmpty()) {
      log.debug("dbsync insert");
      dbStorage.insertPages(sqlNewPages, true);
    }
    log.debug("dbsync done");

    return true;
  }

  private void downloadsThread() {
    boolean first = true;

    while (true) {
      boolean result;
      try {
        if (first) {
          createDownloadQueues();
          // fill so downloaders work while we process the upload queue
          refillDownloadQueues();
          first = false;
        }
        result = refillDownloadQueues();
      } catch (Exception e) {
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
      if (!result) {
        log.warn("mainLoop returned false, stopping");
        break;
      }
    }
    log.info("main loop ended");
  }

  private void createDownloadQueues() {
    // clear out old domains
    log.info("Updating download queues...");
    boolean updated = false;
    for (OverviewEntry overviewEntry : dbStorage.getOverviewSites()) {
      String queueNameSafe = SqsManager.getQueueNameSafe(overviewEntry.siteUrl().getHost());
      if (overviewEntry.dlStatusCount().get(DlStatus.Queued) == null
          && overviewEntry.dlStatusCount().get(DlStatus.Download) == null) {
        // delete if queue exists because it's empty
        URI uri =
            sqsManager.getDownloadQueueUrls().stream()
                .filter(e -> e.toString().contains(queueNameSafe))
                .findFirst()
                .orElse(null);
        if (uri != null) {
          log.info("Deleting queue " + uri);
          sqsManager.deleteQueue(uri);
          updated = true;
        }
      } else {
        // create if queue doesn't exist because we have work
        URI queue =
            sqsManager.getDownloadQueueUrls().stream()
                .filter(e -> e.toString().contains(queueNameSafe))
                .findFirst()
                .orElse(null);
        if (queue == null) {
          log.info("New queue " + overviewEntry.siteUrl().getHost());
          sqsManager.newDownloadQueue(overviewEntry.siteUrl().getHost());
          updated = true;
        }
      }
    }

    if (updated) {
      sqsManager.updateDownloadQueueUrls();
    } else {
      log.info("All queues already created for domains");
    }
  }

  private boolean refillDownloadQueues() throws InterruptedException {
    final int expectedQueueSize = SqsManager.QUEUE_SIZE * 10;
    Set<Entry<URI, Integer>> entries = sqsManager.getDownloadQueueSizes().entrySet();
    log.info("found {} queuss", entries.size());
    for (var entry : entries) {
      if (entry.getValue() > expectedQueueSize) {
        continue;
      }
      String domain = SqsManager.getQueueDomain(entry.getKey());
      domain = SqsManager.getQueueNameSafeOrig(domain);
      // Give the queues sufficient lead time
      List<ScraperDownload> scraperDownloads =
          dbStorage.movePageQueuedToDownloadIPC(domain, expectedQueueSize * 10);
      log.debug("domain " + domain);
      if (!scraperDownloads.isEmpty()) {
        log.debug(
            "Pushing {} download requests for {}",
            scraperDownloads.size(),
            SqsManager.getQueueName(entry.getKey()));
        sqsManager.sendDownloadRequests(entry.getKey(), scraperDownloads);
      }
    }

    sleep();
    return true;
  }

  public void sleep() throws InterruptedException {
    log.debug("sleep...");
    TimeUnit.SECONDS.sleep(60);
  }

  @Override
  public void close() throws IOException {
    log.info("close called, stopping thread");
    if (spiderThread.isAlive()) {
      spiderThread.interrupt();
    }
  }
}
