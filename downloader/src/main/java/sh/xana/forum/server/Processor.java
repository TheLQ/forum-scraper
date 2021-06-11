package sh.xana.forum.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ScraperUpload;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.db.tables.records.SitesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;

/** Parse stage. Extract further URLs for downloading */
public class Processor implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(Processor.class);

  private final DatabaseStorage dbStorage;
  private final ServerConfig config;
  private final Thread spiderThread;
  /**
   * Capacity should be infinite to avoid OOM, but shouldn't be too small or pageSpiderThread will
   * deadlock itself on init/load
   */
  private final BlockingQueue<Boolean> spiderSignal = new ArrayBlockingQueue<>(10);

  private final Path fileCachePath;

  public Processor(DatabaseStorage dbStorage, ServerConfig config) {
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

    for (ScraperUpload.Success success : response.successes()) {
      log.debug("Writing " + success.id().toString() + " response and header");
      Files.write(fileCachePath.resolve(success.id() + ".response"), success.body());
      Files.writeString(
          fileCachePath.resolve(success.id() + ".headers"),
          Utils.jsonMapper.writeValueAsString(success.headers()));

      dbStorage.movePageDownloadToParse(success.id(), success.responseCode());
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
    List<PagesRecord> parserPages = dbStorage.getParserPages();
    if (parserPages.size() == 0) {
      log.debug("Waiting for next signal");
      // wait until we get signaled there is stuff to do, then restart
      spiderSignal.take();
      spiderSignal.clear();
      return true;
    }

    List<SitesRecord> sites = dbStorage.getSites();

    List<UUID> sqlDone = new ArrayList<>(parserPages.size());
    List<PagesRecord> sqlNewPages = new ArrayList<>();
    for (PagesRecord page : parserPages) {
      log.info("processing page {} {}", page.getUrl(), page.getId());

      try {
        if (Files.size(fileCachePath.resolve(page.getId() + ".response")) == 0) {
          throw new RuntimeException("EmptyResponse");
        }

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(new URI(config.get(config.ARG_PARSER_SERVER) + "/" + page.getId()))
                .build();
        HttpResponse<String> response = Utils.httpClient.send(request, BodyHandlers.ofString());
        if (response.statusCode() != 200) {
          throw new RuntimeException("Unexpected status code " + response.statusCode());
        }
        String output = response.body();
        if (output.equals("")) {
          throw new RuntimeException("Output is empty");
        }

        ParserResult results = Utils.jsonMapper.readValue(output, ParserResult.class);
        if (results.loginRequired()) {
          throw new RuntimeException("LoginRequired");
        }
        if (!results.pageType().equals(page.getPagetype())) {
          throw new RuntimeException(
              "Expected pageType " + page.getPagetype() + " got " + results.pageType());
        }

        SitesRecord site =
            sites.stream()
                .filter(entry -> entry.getId().equals(page.getSiteid()))
                .findFirst()
                .orElseThrow();
        if (!results.forumType().equals(site.getForumtype())) {
          throw new RuntimeException(
              "Expected forumType " + site.getForumtype() + " got " + results.forumType());
        }

        for (ParserResult.ParserEntry result : results.subpages()) {
          if (!result.url().startsWith("http://" + page.getDomain() + "/")
              && !result.url().startsWith("https://" + page.getDomain() + "/")) {
            throw new RuntimeException(
                "Got prefix " + result.url() + " expected " + "https://" + page.getDomain() + "/");
          }
          URI url = new URI(result.url());
          if (!page.getDomain().equals(url.getHost())) {
            throw new RuntimeException(
                "Expected domain "
                    + page.getDomain()
                    + " got "
                    + url.getHost()
                    + " for url "
                    + url);
          }

          PagesRecord newPage = new PagesRecord();
          newPage.setSiteid(page.getSiteid());
          newPage.setUrl(url);
          newPage.setPagetype(result.pageType());
          newPage.setDlstatus(DlStatus.Queued);
          newPage.setDomain(url.getHost());
          newPage.setSourceid(page.getId());
          sqlNewPages.add(newPage);
        }

        sqlDone.add(page.getId());
      } catch (Exception e) {
        log.warn("Failed in parser", e);
        dbStorage.setPageException(page.getId(), ExceptionUtils.getStackTrace(e));
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

  public void waitForThreadDeath() throws InterruptedException {
    spiderThread.join();
  }
}
