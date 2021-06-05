package sh.xana.forum.server;

import java.io.Closeable;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ScraperUpload;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.db.tables.records.SitesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;

/** Parse stage. Extract further URLs for downloading */
public class Processor implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(Processor.class);

  private final DatabaseStorage dbStorage;
  private final Thread spiderThread;
  /**
   * Capacity should be infinite to avoid OOM, but shouldn't be too small or pageSpiderThread will
   * deadlock itself on init/load
   */
  private final BlockingQueue<UUID> spiderQueue = new ArrayBlockingQueue<>(10000);
  /** storage of downloaded content */
  private final Path fileCachePath;

  private final String nodeCmd;
  private final String parserScript;

  public Processor(
      DatabaseStorage dbStorage, Path fileCachePath, String nodeCmd, String parserScript) {
    this.dbStorage = dbStorage;
    this.fileCachePath = fileCachePath;
    this.nodeCmd = nodeCmd;
    this.parserScript = parserScript;

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

    String[] cmd =
        new String[] {
          nodeCmd,
          // needed for es6 modules
          "--es-module-specifier-resolution=node",
          parserScript,
          fileCachePath.resolve(pageId + ".response").toString()
        };
    try {
      ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
      Process process = pb.start();
      String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).trim();

      // suspicous....
      if (process.waitFor() != 0) {
        throw new RuntimeException("node parser exit " + process.exitValue() + "\r\n" + output);
      }
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
      SitesRecord site = dbStorage.getSite(page.getSiteid());
      if (!results.forumType().equals(site.getForumtype())) {
        throw new RuntimeException(
            "Expected forumType " + site.getForumtype() + " got " + results.forumType());
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
          dbStorage.insertPageQueued(page.getSiteid(), List.of(url), result.pageType(), pageId);
        } else {
          log.info("Ignoring duplicate page " + url);
        }
      }

      dbStorage.setPageStatus(List.of(pageId), DlStatus.Done);
    } catch (Exception e) {
      log.warn("Failed in parser, command " + StringUtils.join(cmd, " "), e);
      dbStorage.setPageException(page.getId(), ExceptionUtils.getStackTrace(e));
    }

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
