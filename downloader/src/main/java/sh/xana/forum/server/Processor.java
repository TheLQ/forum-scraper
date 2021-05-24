package sh.xana.forum.server;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
import sh.xana.forum.common.db.tables.records.PagesRecord;
import sh.xana.forum.common.dbutil.DatabaseStorage;
import sh.xana.forum.common.dbutil.DatabaseStorage.DlStatus;
import sh.xana.forum.common.ipc.DownloadResponse;
import sh.xana.forum.common.ipc.ParserResult;

public class Processor {
  private static final Logger log = LoggerFactory.getLogger(DatabaseStorage.class);
  private static final Path fileCachePath = Path.of("..", "filecache");

  private final DatabaseStorage dbStorage;
  private final Thread spiderThread;
  /**
   * Capacity should be infinite to avoid OOM, but shouldn't be too small or pageSpiderThread will
   * deadlock itself on init/load
   */
  private final BlockingQueue<PagesRecord> spiderQueue = new ArrayBlockingQueue<PagesRecord>(10000);

  public Processor(DatabaseStorage dbStorage) {
    this.dbStorage = dbStorage;
    this.spiderThread = new Thread(this::pageSpiderThread);
    this.spiderThread.setName("ProcessorSpider");
  }

  /** Process responses the download nodes collected */
  public void processResponses(DownloadResponse response) throws IOException {
    for (DownloadResponse.Error error : response.errors()) {
      dbStorage.setPageException(error.id(), error.exception());
    }

    for (DownloadResponse.Success success : response.successes()) {
      log.info("Writing " + success.id().toString() + " response and header");
      Files.write(fileCachePath.resolve(success.id().toString() + ".response"), success.body());
      Files.writeString(
          fileCachePath.resolve(success.id().toString() + ".headers"),
          Utils.jsonMapper.writeValueAsString(success.headers()));

      log.info("Updating database", "asd");
      dbStorage.movePageDownloadToParse(success.id(), success.responseCode());
    }
  }

  public void startSpiderThread() {
    this.spiderThread.start();
  }

  /** */
  private void pageSpiderThread() {
    spiderQueue.addAll(dbStorage.getParserPages());

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

  private boolean pageSpiderCycle() throws InterruptedException, IOException, URISyntaxException {
    PagesRecord page = spiderQueue.take();
    log.info("processing page " + page.getUrl());

    try {
      UUID pageId = Utils.uuidFromBytes(page.getId());
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
      for (ParserResult.ParserEntry result : results.entries()) {
        URI url = new URI(result.url());
        if (url.getHost() == null) {
          URI sourceUrl = new URI(page.getUrl());
          url =
              new URI(
                  sourceUrl.getScheme(),
                  sourceUrl.getUserInfo(),
                  sourceUrl.getHost(),
                  sourceUrl.getPort(),
                  url.getPath(),
                  url.getQuery(),
                  url.getFragment());
        }
        dbStorage.insertPageQueued(
            Utils.uuidFromBytes(page.getSiteid()), List.of(url.toString()), result.type(), pageId);
      }

      dbStorage.setPageStatus(List.of(pageId), DlStatus.Done);
    } catch (Exception e) {
      dbStorage.setPageException(
          Utils.uuidFromBytes(page.getId()), ExceptionUtils.getStackTrace(e));
    }

    return true;
  }
}
