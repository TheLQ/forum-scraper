package sh.xana.forum.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.RecieveRequest;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.ParserEntry;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.common.ipc.ScraperUpload;
import sh.xana.forum.server.db.tables.records.PageredirectsRecord;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.OverviewEntry;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.Auditor;
import sh.xana.forum.server.parser.PageParser;

/** Parse stage. Extract further URLs for downloading */
public class PageManager implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(PageManager.class);

  private final DatabaseStorage dbStorage;
  private final ServerConfig config;
  private final SqsManager sqsManager;
  private final Thread spiderThread;
  private final Thread downloadsThread;

  private final PageParser pageParser;

  public PageManager(DatabaseStorage dbStorage, ServerConfig config, SqsManager sqsManager) {
    this.dbStorage = dbStorage;
    this.config = config;
    this.sqsManager = sqsManager;

    this.spiderThread = new Thread(this::pageSpiderThread);
    this.spiderThread.setName("PageSpider");

    this.downloadsThread = new Thread(this::downloadsThread);
    this.downloadsThread.setName("PageDownloads");

    this.pageParser = new PageParser(config);
  }

  public void startThreads() {
    this.spiderThread.start();
    Auditor.threadRunner(2, "PageUploads-", this::uploadsThread);
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
    List<ParserPage> parserPages = dbStorage.getParserPages(true);
    if (parserPages.isEmpty()) {
      return false;
    }

    List<UUID> sqlDone = new ArrayList<>(parserPages.size());
    List<DatabaseStorage.InsertPage> sqlNewPages = new ArrayList<>();
    for (ParserPage page : parserPages) {
      UUID pageId = page.pageId();
      log.info("processing page {}", pageId);

      String output = null;
      try {
        ParserResult results =
            pageParser.parsePage(Files.readAllBytes(pageParser.getPagePath(pageId)), page);

        for (ParserEntry subpage : results.subpages()) {
          sqlNewPages.add(
              new DatabaseStorage.InsertPage(
                  pageId, page.siteId(), Utils.toURI(subpage.url()), subpage.pageType()));
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

    if (!sqlNewPages.isEmpty()) {
      log.debug("dbsync insert");
      dbStorage.insertPagesQueued(sqlNewPages, true);
    }
    if (!sqlDone.isEmpty()) {
      log.debug("dbsync dlstatus done");
      dbStorage.setPageStatus(sqlDone, DlStatus.Done);
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

    Map<String, Integer> overviewToParse = dbStorage.getOverviewToParse();

    boolean updated = false;
    for (OverviewEntry overviewEntry : dbStorage.getOverviewSites()) {
      String domain = overviewEntry.siteUrl().getHost();
      String queueNameSafe = SqsManager.getQueueNameSafe(domain);
      Integer parseCount = overviewToParse.get(domain);
      if (overviewEntry.dlStatusCount().get(DlStatus.Queued) == null
          && overviewEntry.dlStatusCount().get(DlStatus.Download) == null
          && (parseCount == null || parseCount == 0)) {
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
