package sh.xana.forum.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.common.ipc.ScraperUpload;
import sh.xana.forum.server.WebServer;

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
  /** Number of URLs to request, and size when to do a request. Should be between SIZE - 2xSIZE */
  public static final int URL_QUEUE_REFILL_SIZE = 3;

  private static final int CYCLE_SECONDS = 20;
  private static int INSTANCE_COUNTER = 0;

  private final ClientConfig config;

  private final String domain;
  private final List<ScraperDownload.SiteEntry> scraperRequests = new ArrayList<>();
  private final List<ScraperUpload.Success> responseSuccess = new ArrayList<>();
  private final List<ScraperUpload.Error> responseError = new ArrayList<>();

  private final Thread thread;
  private final CountDownLatch threadCloser = new CountDownLatch(1);

  public Scraper(ClientConfig config, String domain) {
    this.config = config;
    this.domain = domain;
    this.thread = new Thread(this::downloadThread);
    thread.setName("Scraper" + (INSTANCE_COUNTER++));
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
    if (scraperRequests.size() < URL_QUEUE_REFILL_SIZE) {
      boolean isClosing = refillQueue(true);
      if (isClosing) {
        return true;
      }
    }

    if (scraperRequests.size() == 0) {
      log.warn("Queue is empty, not doing anything");
    } else {
      // pop request and fetch content
      ScraperDownload.SiteEntry scraperRequest = scraperRequests.remove(0);
      try {
        log.debug("Requesting {} url {}", scraperRequest.siteId(), scraperRequest.url());
        HttpRequest request = HttpRequest.newBuilder()
            // Fix VERY annoying forum that gives different html without this, breaking parser
            .header("User-Agent", config.get(config.ARG_CLIENT_USERAGENT))
            .uri(scraperRequest.url()).build();
        HttpResponse<byte[]> response = Utils.httpClient.send(request, BodyHandlers.ofByteArray());

        if (response.body().length == 0) {
          throw new Exception("EmptyResponse");
        }

        responseSuccess.add(
            new ScraperUpload.Success(
                scraperRequest.siteId(),
                response.body(),
                response.headers().map(),
                response.statusCode()));
      } catch (Exception e) {
        log.debug("exception during run", e);
        responseError.add(
            new ScraperUpload.Error(scraperRequest.siteId(), ExceptionUtils.getStackTrace(e)));
      }
    }

    // sleep then continue for the next cycle
    log.debug("Queued {} urls, sleeping {} seconds", scraperRequests.size(), CYCLE_SECONDS);
    boolean isClosing = threadCloser.await(CYCLE_SECONDS, TimeUnit.SECONDS);
    if (isClosing) {
      // send what we have
      refillQueue(false);
    }
    return isClosing;
  }

  /** Fetch new batch of URLs to process, and submit completedBuffer */
  private boolean refillQueue(boolean requestMore) {
    log.info(
        "-- Refill, {} requests, {} response success, {} response error {} request more",
        scraperRequests.size(),
        responseSuccess.size(),
        responseError.size(),
        requestMore);
    try {
      ScraperUpload request =
          new ScraperUpload(
              ClientMain.NODE_ID, this.domain, requestMore, responseSuccess, responseError);
      String newRequestsJSON = null;
      for (int i = 0; i < 30; i++) {
        try {
          newRequestsJSON =
              Utils.serverPostBackend(
                  WebServer.PAGE_CLIENT_BUFFER, Utils.jsonMapper.writeValueAsString(request));
          break;
        } catch (Exception e) {
          if (e.getCause() instanceof ConnectException) {
            int waitMinutes = i + /*0-base*/ 1;
            log.warn("Failed to connect, waiting for {} minutes", waitMinutes, e);
            boolean isClosing = threadCloser.await(waitMinutes, TimeUnit.MINUTES);
            if (isClosing) {
              // close early
              return true;
            }
            // continue onto next loop
          } else {
            throw e;
          }
        }
      }

      ScraperDownload response = Utils.jsonMapper.readValue(newRequestsJSON, ScraperDownload.class);
      scraperRequests.addAll(response.entries());

      responseSuccess.clear();
      responseError.clear();
    } catch (Exception e) {
      throw new RuntimeException("failed to parse buffer json", e);
    }
    return false;
  }

  @Override
  public void close() throws IOException {
    log.info("close called, stopping thread");
    if (thread.isAlive()) {
      threadCloser.countDown();
    }
  }

  public void waitForThreadDeath() throws InterruptedException {
    thread.join();
  }
}
