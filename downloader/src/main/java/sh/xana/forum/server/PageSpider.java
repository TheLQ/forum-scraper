package sh.xana.forum.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;
import sh.xana.forum.server.dbutil.DatabaseStorage.ForumType;
import sh.xana.forum.server.dbutil.DatabaseStorage.PageType;

/** Parse stage. Extract further URLs for downloading */
public class PageSpider implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(PageSpider.class);

  private final DatabaseStorage dbStorage;
  private final ServerConfig config;
  private final Thread spiderThread;
  /**
   * Capacity should be infinite to avoid OOM, but shouldn't be too small or pageSpiderThread will
   * deadlock itself on init/load
   */
  private final ArrayBlockingQueue<Boolean> spiderSignal = new ArrayBlockingQueue<>(10);

  private final Path fileCachePath;

  public PageSpider(DatabaseStorage dbStorage, ServerConfig config) {
    this.dbStorage = dbStorage;
    this.config = config;

    this.spiderThread = new Thread(this::pageSpiderThread);
    this.spiderThread.setName("ProcessorSpider");

    this.fileCachePath = Path.of(config.get(config.ARG_FILE_CACHE));
  }

  /** Process responses the download scraper collected */
  public void processResponses(ScraperUpload response) throws IOException, InterruptedException {
    for (ScraperUpload.Error error : response.errors()) {
      log.debug("Found error for {} {}", error.id(), error.exception());
      dbStorage.setPageException(error.id(), error.exception());
    }

    List<PageredirectsRecord> sqlNewRedirects = new ArrayList<>();

    for (ScraperUpload.Success success : response.successes()) {
      log.debug("Writing " + success.id().toString() + " response and header");
      Files.write(fileCachePath.resolve(success.id() + ".response"), success.body());
      Files.writeString(
          fileCachePath.resolve(success.id() + ".headers"),
          Utils.jsonMapper.writeValueAsString(success.headers()));

      dbStorage.movePageDownloadToParse(success.id(), success.responseCode());

      if (!success.redirectList().isEmpty()) {
        byte counter = 0;
        URI lastUri = null;
        for (URI newUri : success.redirectList()) {
          sqlNewRedirects.add(new PageredirectsRecord(success.id(), newUri, counter++));
          lastUri = newUri;
        }
        try {
          dbStorage.setPageURL(success.id(), lastUri);
        } catch (Exception e) {
          if (e.getCause() instanceof SQLIntegrityConstraintViolationException
              && e.getCause().getMessage().contains("Duplicate entry")) {
            // we have redirected to an existing page. So we don't need this anymore
            dbStorage.deletePage(success.id());
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
    spiderSignal.put(true);
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
    var parserPages = dbStorage.getParserPages();
    if (parserPages.size() == 0) {
      log.debug("Waiting for next signal");
      // wait until we get signaled there is stuff to do, then restart
      spiderSignal.take();
      return true;
    }

    // wipe out remaining triggers
    spiderSignal.clear();

    // Batch requests for improved IPC performance
    List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
    for (var page : parserPages) {
      UUID pageId = page.get(Pages.PAGES.PAGEID);
      URI siteBaseUrl = page.get(Sites.SITES.SITEURL);
      try {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(
                    new URI(
                        config.get(config.ARG_PARSER_SERVER) + "/" + pageId + "?" + siteBaseUrl))
                .build();
        CompletableFuture<HttpResponse<String>> responseFuture =
            Utils.httpClient.sendAsync(request, BodyHandlers.ofString());
        futures.add(responseFuture);
      } catch (Exception e) {
        log.warn("Failed in parser HTTP init", e);
        dbStorage.setPageException(pageId, ExceptionUtils.getStackTrace(e));
        futures.add(null);
      }
    }

    List<UUID> sqlDone = new ArrayList<>(parserPages.size());
    List<PagesRecord> sqlNewPages = new ArrayList<>();
    int counter = 0;
    for (CompletableFuture<HttpResponse<String>> future : futures) {
      var page = parserPages.get(counter++);

      UUID pageId = page.get(Pages.PAGES.PAGEID);

      log.info("processing page {} {}", page.get(Pages.PAGES.PAGEURL), pageId);
      if (future == null) {
        throw new RuntimeException("Future is empty, lets stop and investigate");
      }

      String output = null;
      try {
        HttpResponse<String> response = future.get();
        if (response.statusCode() != 200) {
          throw new RuntimeException("Unexpected status code " + response.statusCode());
        }
        output = response.body();
        if (output.equals("")) {
          throw new RuntimeException("Output is empty");
        }

        PageType pageType = page.get(Pages.PAGES.PAGETYPE);
        ParserResult results = Utils.jsonMapper.readValue(output, ParserResult.class);
        if (results.loginRequired()) {
          throw new RuntimeException("LoginRequired");
        }
        if (!results.pageType().equals(pageType)) {
          throw new RuntimeException(
              "Expected pageType " + pageType + " got " + results.pageType());
        }

        ForumType siteType = page.get(Sites.SITES.FORUMTYPE);
        if (!results.forumType().equals(siteType)) {
          throw new RuntimeException(
              "Expected forumType " + siteType + " got " + results.forumType());
        }

        String pageDomain = page.get(Pages.PAGES.DOMAIN);
        for (ParserResult.ParserEntry result : results.subpages()) {
          if (!result.url().startsWith("http://" + pageDomain + "/")
              && !result.url().startsWith("https://" + pageDomain + "/")) {
            throw new RuntimeException(
                "Got prefix " + result.url() + " expected " + "https://" + pageDomain + "/");
          }
          URI url = new URI(result.url());
          if (!pageDomain.equals(url.getHost())) {
            throw new RuntimeException(
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

  @Override
  public void close() throws IOException {
    log.info("close called, stopping thread");
    if (spiderThread.isAlive()) {
      spiderThread.interrupt();
    }
  }
}