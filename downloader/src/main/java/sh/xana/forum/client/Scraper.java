package sh.xana.forum.client;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.common.SqsManager.RecieveMessage;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.common.ipc.ScraperUpload;

/**
 * Each scraper handles 1 site. Scrapers are distributed among multiple nodes on different IPs
 *
 * <p>Rate limits all traffic to a particular site
 *
 * <p>Keeps a buffer of X TODO urls
 *
 * <p>When buffer is below Y, refill so download queue can keep going
 */
public class Scraper implements Closeable {
  public static final Logger log = LoggerFactory.getLogger(Scraper.class);

  private static final int CYCLE_SECONDS = 10;
  private static int INSTANCE_COUNTER = 0;

  private final ClientConfig config;
  private final SqsManager sqsManager;
  private final URI queue;
  private final List<RecieveMessage<ScraperDownload>> scraperDownloads = new ArrayList<>();
  /** Message is from the original ScraperDownload */
  private final List<RecieveMessage<ScraperUpload>> scraperUploads = new ArrayList<>();

  private final Thread thread;

  public Scraper(ClientConfig config, SqsManager sqsManager, URI queue) {
    this.config = config;
    this.sqsManager = sqsManager;
    this.queue = queue;
    this.thread = new Thread(this::downloadThread);

    thread.setName(SqsManager.getQueueName(queue));
  }

  public void startThread() {
    thread.start();
  }

  private void downloadThread() {
    while (true) {
      boolean isClosing;
      try {
        isClosing = mainLoopCycle();
      } catch (Exception e) {
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
      if (isClosing) {
        log.warn("mainLoop closing, stopping");
        break;
      }
    }
    log.info("main loop ended");
  }

  /**
   * Main execution loop
   *
   * @return isClosing should stop thread
   */
  private boolean mainLoopCycle() throws InterruptedException {
    if (scraperDownloads.size() < SqsManager.QUEUE_SIZE) {
      boolean isClosing = refillQueue(true);
      if (isClosing) {
        return true;
      }
    }

    if (scraperDownloads.size() == 0) {
      log.info("Queue is empty, not doing anything");
    } else {
      // pop request and fetch content
      var scraperRequestMessage = scraperDownloads.remove(0);
      ScraperDownload scraperRequest = scraperRequestMessage.obj();

      List<URI> redirectList = new ArrayList<>();
      Exception caughtException = null;
      HttpResponse<byte[]> response = null;
      try {
        URI url = scraperRequest.url();
        while (true) {
          log.debug("Requesting {} url {}", scraperRequest.pageId(), scraperRequest.url());
          HttpRequest request =
              HttpRequest.newBuilder()
                  // Fix VERY annoying forum that gives different html without this, breaking parser
                  .header("User-Agent", config.get(config.ARG_CLIENT_USERAGENT))
                  .uri(url)
                  .timeout(Duration.ofSeconds(60))
                  .build();
          response = Utils.httpClient.send(request, BodyHandlers.ofByteArray());

          if (response.statusCode() == 301 || response.statusCode() == 302) {
            redirectList.add(url);
            url =
                new URI(
                    response
                        .headers()
                        .firstValue("Location")
                        .orElseThrow(() -> new RuntimeException("Missing location")));
          } else {
            break;
          }
        }
        if (!redirectList.isEmpty()) {
          // add last url as final destination url
          redirectList.add(url);
        }
      } catch (InterruptedException e) {
        throw e;
      } catch (Exception e) {
        log.debug("exception during run", e);
        caughtException = e;
      }

      scraperUploads.add(
          new RecieveMessage<>(
              scraperRequestMessage.message(),
              new ScraperUpload(
                  scraperRequest.pageId(),
                  caughtException == null ? null : ExceptionUtils.getStackTrace(caughtException),
                  scraperRequest.url(),
                  redirectList,
                  response == null ? null : response.body(),
                  response == null ? null : response.headers().map(),
                  response == null ? 0 : response.statusCode())));
    }

    // sleep then continue for the next cycle
    log.debug("Queued {} urls, sleeping {} seconds", scraperDownloads.size(), CYCLE_SECONDS);
    try {
      TimeUnit.SECONDS.sleep(CYCLE_SECONDS);
    } catch (InterruptedException e) {
      log.info("Captured interrupt, sending last request");
      refillQueue(false);
      return true;
    }
    return false;
  }

  /** Fetch new batch of URLs to process, and submit completedBuffer */
  private boolean refillQueue(boolean requestMore) {
    log.info(
        "-- Refill, {} downloads, {} pending upload, {} request more",
        scraperDownloads.size(),
        scraperUploads.size(),
        requestMore);
    try {
      if (!scraperUploads.isEmpty()) {
        sqsManager.sendUploadRequests(
            scraperUploads.stream().map(RecieveMessage::obj).collect(Collectors.toList()));
        // deleted the processed scraperDownload messages that we copied to the scraperUpload list
        sqsManager.deleteQueueMessage(queue, scraperUploads);
      }
      scraperDownloads.addAll(sqsManager.receiveDownloadRequests(queue));

      scraperUploads.clear();
    } catch (Exception e) {
      throw new RuntimeException("failed to parse buffer json", e);
    }
    return false;
  }

  @Override
  public void close() {
    log.info("close called, stopping thread");
    thread.interrupt();
  }

  public void waitForThreadDeath() throws InterruptedException {
    thread.join();
  }
}
