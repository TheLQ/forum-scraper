package sh.xana.forum.client;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ScraperRequest;
import sh.xana.forum.common.ipc.ScraperResponse;
import sh.xana.forum.server.WebServer;

/**
 * Each node handles 1 site. Nodes are distributed among multiple instances on different IPs
 *
 * <p>Rate limits all traffic to a particular site
 *
 * <p>Keeps a buffer of X TODO urls
 *
 * <p>When buffer is below Y, refill so download queue can keep going
 */
public class Scraper {
  public static final Logger log = LoggerFactory.getLogger(Scraper.class);
  /** Number of URLs to request, and size when to do a request. Should be between SIZE - 2xSIZE */
  public static final int URL_QUEUE_REFILL_SIZE = 10;

  private static final int CYCLE_SECONDS = 20;
  private static int INSTANCE_COUNTER = 0;

  private final String domain;
  private final List<ScraperRequest.SiteEntry> scraperRequests = new ArrayList<>();
  private final List<ScraperResponse.Success> responseSuccess = new ArrayList<>();
  private final List<ScraperResponse.Error> responseError = new ArrayList<>();

  private final Thread thread;

  public Scraper(String domain) {
    this.domain = domain;
    this.thread = new Thread(this::downloadThread);
    thread.setName("Scraper" + (INSTANCE_COUNTER++));
  }

  public void startThread() {
    thread.start();
  }

  private void downloadThread() {
    Exception ex = null;
    while (true) {
      boolean result;
      try {
        result = mainLoopCycle();
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
    log.info("main loop ended");
  }

  /**
   * Main execution loop
   *
   * @return true to continue to next loop
   */
  private boolean mainLoopCycle() throws InterruptedException {
    if (scraperRequests.size() < URL_QUEUE_REFILL_SIZE) {
      refillQueue();
    }

    if (scraperRequests.size() == 0) {
      log.warn("Queue is empty, not doing anything");
    } else {
      // pop request and fetch content
      ScraperRequest.SiteEntry scraperRequest = scraperRequests.remove(0);
      try {
        log.debug("Requesting {} url {}", scraperRequest.siteId(), scraperRequest.url());
        URI uri = new URI(scraperRequest.url());

        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        HttpResponse<byte[]> response = Utils.httpClient.send(request, BodyHandlers.ofByteArray());

        responseSuccess.add(
            new ScraperResponse.Success(
                scraperRequest.siteId(),
                response.body(),
                response.headers().map(),
                response.statusCode()));
      } catch (Exception e) {
        log.debug("exception during run", e);
        responseError.add(
            new ScraperResponse.Error(scraperRequest.siteId(), ExceptionUtils.getStackTrace(e)));
      }
    }

    // sleep then continue for the next cycle
    log.debug("Queued {} urls, sleeping {} seconds", scraperRequests.size(), CYCLE_SECONDS);
    Thread.sleep(1000L * CYCLE_SECONDS);
    return true;
  }

  /** Fetch new batch of URLs to process, and submit completedBuffer */
  private void refillQueue() {
    log.info(
        "-- Refill, {} requests, {} response success, {} response error",
        scraperRequests.size(),
        responseSuccess.size(),
        responseError.size());
    try {
      ScraperResponse downloads =
          new ScraperResponse(ClientMain.NODE_ID, responseSuccess, responseError);
      String newRequestsJSON =
          Utils.serverPostBackend(
              WebServer.PAGE_CLIENT_BUFFER + "?domain=" + this.domain,
              Utils.jsonMapper.writeValueAsString(downloads));

      Collections.addAll(
          scraperRequests,
          Utils.jsonMapper.readValue(newRequestsJSON, ScraperRequest.SiteEntry[].class));
      responseSuccess.clear();
      responseError.clear();
    } catch (Exception e) {
      throw new RuntimeException("failed to parse buffer json", e);
    }
  }
}
