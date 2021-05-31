package sh.xana.forum.server;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ScraperUpload;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;

/** Parse stage. Extract further URLs for downloading */
public class Processor {
  private static final Logger log = LoggerFactory.getLogger(Processor.class);

  private final DatabaseStorage dbStorage;
  private final Thread spiderThread;
  /**
   * Capacity should be infinite to avoid OOM, but shouldn't be too small or pageSpiderThread will
   * deadlock itself on init/load
   */
  private final BlockingQueue<UUID> spiderQueue = new ArrayBlockingQueue<>(10000);
  /**
   * storage of downloaded content
   */
  private final Path fileCachePath;

  public Processor(DatabaseStorage dbStorage, Path fileCachePath) {
    this.dbStorage = dbStorage;
    this.fileCachePath = fileCachePath;

    this.spiderThread = new Thread(this::pageSpiderThread);
    this.spiderThread.setName("ProcessorSpider");
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
      queuePage(success.id());
    }
  }

  public void queuePage(UUID page) throws InterruptedException {
    spiderQueue.put(page);
  }

  public void startSpiderThread() {
    this.spiderThread.start();
  }

  /** */
  private void pageSpiderThread() {
    // fetch missed parse records from last run
    dbStorage.getParserPages().stream().map(PagesRecord::getId).forEach(spiderQueue::add);

    Exception ex = null;
    while (true) {
      boolean result;
      try {
        result = pageSpiderCycle();
      } catch (Exception e) {
        ex = e;
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
      if (!result) {
        log.warn("mainLoop returned false, stopping");
        break;
      }
    }
    log.info("main loop ended", ex);
  }

  private boolean pageSpiderCycle() throws InterruptedException {
    UUID pageId = spiderQueue.take();
    List<PagesRecord> pages = dbStorage.getPages(Pages.PAGES.ID.eq(pageId));
    if (pages.size() != 1) {
      throw new RuntimeException("found " + pages.size() + " pages for " + pageId);
    }
    PagesRecord page = pages.get(0);
    log.info("processing page " + page.getUrl());

    try {
      String pageIdStr = pageId.toString();
      ProcessBuilder pb =
          new ProcessBuilder(
                  "node", "../parser/parser.js", "../filecache/" + pageIdStr + ".response")
              .redirectErrorStream(true);
      Process process = pb.start();
      String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);

      if (process.exitValue() != 0) {
        throw new RuntimeException("node parser exit " + process.exitValue() + "\r\n" + output);
      }

      ParserResult results = Utils.jsonMapper.readValue(output, ParserResult.class);
      if (!results.type().equals(page.getPagetype())) {
        throw new RuntimeException(
            "Unexpected pageType " + results.type() + " expected " + page.getPagetype());
      }
      for (ParserResult.ParserEntry result : results.subpages()) {
        URI url = new URI(result.url());
        if (url.getHost() == null) {
          URI sourceUrl = page.getUrl();
          String path = url.getPath();
          if (!path.startsWith("/")) {
            path = "/" + path;
          }
          url =
              new URI(
                  sourceUrl.getScheme(),
                  sourceUrl.getUserInfo(),
                  sourceUrl.getHost(),
                  sourceUrl.getPort(),
                  path,
                  url.getQuery(),
                  url.getFragment());
        }

        if (dbStorage.getPages(Pages.PAGES.URL.eq(url)).size() == 0) {
          log.info("New page {}", url);
          dbStorage.insertPageQueued(page.getSiteid(), List.of(url), result.type(), pageId);
        } else {
          log.info("Ignoring duplicate page " + url);
        }
      }

      dbStorage.setPageStatus(List.of(pageId), DlStatus.Done);
    } catch (Exception e) {
      log.warn("Failed in parser", e);
      dbStorage.setPageException(page.getId(), ExceptionUtils.getStackTrace(e));
    }

    return true;
  }
}
