package sh.xana.forum.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.Record7;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ScraperUpload;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.Sites;
import sh.xana.forum.server.db.tables.records.PageredirectsRecord;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.parser.PageParser;

/** Parse stage. Extract further URLs for downloading */
public class PageManager implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(PageManager.class);

  private final DatabaseStorage dbStorage;
  private final ServerConfig config;
  private final Thread spiderThread;
  /**
   * Capacity should be infinite to avoid OOM, but shouldn't be too small or pageSpiderThread will
   * deadlock itself on init/load
   */
  private final ArrayBlockingQueue<Boolean> spiderSignal = new ArrayBlockingQueue<>(10);
  /**
   * Very carefully lock all DlStatus state changes to avoid races, especially as the database grows
   * and individual queries take longer
   */
  public final Object PROCESSOR_LOCK = new Object();

  private final Path fileCachePath;
  private final PageParser pageParser;

  public PageManager(DatabaseStorage dbStorage, ServerConfig config) {
    this.dbStorage = dbStorage;
    this.config = config;

    this.spiderThread = new Thread(this::pageSpiderThread);
    this.spiderThread.setName("ProcessorSpider");

    this.fileCachePath = Path.of(config.get(config.ARG_FILE_CACHE));

    this.pageParser = new PageParser(config);
  }

  /** Process responses the download scraper collected */
  public void processResponses(ScraperUpload response) throws IOException, InterruptedException {
    // TODO: ALSO VERY RACY
    for (ScraperUpload.Error error : response.errors()) {
      log.debug("Found error for {} {}", error.id(), error.exception());
      dbStorage.setPageException(error.id(), error.exception());
    }

    List<PageredirectsRecord> sqlNewRedirects = new ArrayList<>();

    for (ScraperUpload.Success success : response.successes()) {
      log.debug("Writing " + success.pageId().toString() + " response and header");
      try {
        PagesRecord page = dbStorage.getPage(success.pageId());
        if (page.getPageurl().equals("asdf")) {
          throw new RuntimeException("que?");
        }
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to get page "
                + success.pageId()
                + " url "
                + success.pageUrl()
                + " headers "
                + success.headers(),
            e);
      }
      Files.write(fileCachePath.resolve(success.pageId() + ".response"), success.body());
      Files.writeString(
          fileCachePath.resolve(success.pageId() + ".headers"),
          Utils.jsonMapper.writeValueAsString(success.headers()));

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
          } else {
            throw e;
          }
        }
      }
    }

    if (!sqlNewRedirects.isEmpty()) {
      log.debug("dbsync redirect");
      dbStorage.insertPageRedirects(sqlNewRedirects);
    }
    if (!response.successes().isEmpty()) {
      signalSpider();
    }
  }

  public void signalSpider() throws InterruptedException {
    // insert request but do not block to avoid deadlocking PROCESSOR_LOCK
    spiderSignal.offer(true);
  }

  public void startSpiderThread() {
    this.spiderThread.start();
  }

  /** */
  private void pageSpiderThread() {
    while (true) {
      boolean result;
      try {
        result = pageSpiderCycle();
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

  private boolean pageSpiderCycle() throws InterruptedException {
    Result<Record7<UUID, UUID, URI, PageType, String, URI, ForumType>> parserPages;
    synchronized (PROCESSOR_LOCK) {
      parserPages = dbStorage.getParserPages();
    }
    if (parserPages.size() == 0) {
      log.debug("Waiting for next signal");
      // wait until we get signaled there is stuff to do, then restart
      spiderSignal.take();
      return true;
    }

    // wipe out remaining triggers
    spiderSignal.clear();

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
        log.warn("Failed in parser", e);
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

  private static class SpiderWarningException extends RuntimeException {

    public SpiderWarningException(String message) {
      super(message);
    }

    public SpiderWarningException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @Override
  public void close() throws IOException {
    log.info("close called, stopping thread");
    if (spiderThread.isAlive()) {
      spiderThread.interrupt();
    }
  }
}
