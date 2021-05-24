package sh.xana.forum.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.DownloadRequest;
import sh.xana.forum.common.ipc.DownloadResponse;
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
public class DownloadNode {
  public static final Logger log = LoggerFactory.getLogger(DownloadNode.class);
  /** Number of URLs to request, and size when to do a request. Should be between SIZE - 2xSIZE */
  public static final int URL_QUEUE_REFILL_SIZE = 10;

  private static int CYCLE_SECONDS = 20;
  private static int INSTANCE_COUNTER = 0;

  private final String domain;
  private final List<DownloadRequest> downloadRequests = new ArrayList<>();
  private final List<DownloadResponse> downloadResponses = new ArrayList<>();

  private final Thread thread;

  public DownloadNode(String domain) {
    this.domain = domain;
    this.thread = new Thread(this::mainLoop);
    thread.setName("DownloadNode" + (INSTANCE_COUNTER++));
  }

  public void startThread() {
    thread.start();
  }

  private void mainLoop() {
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
    log.info("main loop ended", ex);
  }

  /**
   * Main execution loop
   *
   * @return true to continue to next loop
   */
  private boolean mainLoopCycle() throws InterruptedException, URISyntaxException, IOException {
    if (downloadRequests.size() < URL_QUEUE_REFILL_SIZE) {
      refillQueue();
    }

    if (downloadRequests.size() == 0) {
      log.warn("Queue is empty, not doing anything");
    } else {
      // pop request and fetch content
      DownloadRequest downloadRequest = downloadRequests.remove(0);

      log.debug("Requesting {} url {}", downloadRequest.id(), downloadRequest.url());
      URI uri = new URI(downloadRequest.url());
      HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
      HttpResponse<byte[]> response = Utils.httpClient.send(request, BodyHandlers.ofByteArray());

      downloadResponses.add(
          new DownloadResponse(
              downloadRequest.id(),
              response.body(),
              response.headers().map(),
              response.statusCode(),
              null));
    }

    // sleep then continue for the next cycle
    log.debug("Queued {} urls, sleeping {} seconds", downloadRequests.size(), CYCLE_SECONDS);
    Thread.sleep(1000 * CYCLE_SECONDS);
    return true;
  }

  /** Fetch new batch of URLs to process, and submit completedBuffer */
  private void refillQueue() {
    log.info("-- Refill, {} requests, {} responses");
    try {
      String newRequestsJSON =
          Utils.serverPost(
              WebServer.PAGE_CLIENT_BUFFER + "?domain=" + this.domain,
              Utils.jsonMapper.writeValueAsString(this.downloadResponses));
      for (DownloadRequest entry :
          Utils.jsonMapper.readValue(newRequestsJSON, DownloadRequest[].class)) {
        downloadRequests.add(entry);
      }
    } catch (Exception e) {
      throw new RuntimeException("failed to parse buffer json", e);
    }
  }
}
