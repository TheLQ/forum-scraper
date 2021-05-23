package sh.xana.forum.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  static final String SERVER_URL = "http://127.0.0.1:8080";

  final String site;
  final List<String> urlQueue = new ArrayList<>();
  final List<String> submissionBuffer = new ArrayList<>();
  final HttpClient httpClient = HttpClient.newHttpClient();

  Thread thread;

  public DownloadNode(String site) {
    this.site = site;
  }

  public void startThread() {
    thread =
        new Thread(
            () -> {
              while (true) {
                boolean result;
                try {
                  result = mainLoop();
                } catch (Exception e) {
                  log.error("Caught exception in mainLoop, stopping");
                  break;
                }
                if (!result) {
                  log.warn("mainLoop returned false, stopping");
                  break;
                }
              }
            });
    thread.setName("DownloadNode");
    thread.start();
  }

  /**
   * Main execution loop
   *
   * @return true to continue to next loop
   */
  public boolean mainLoop() {
    if (urlQueue.size() < URL_QUEUE_REFILL_SIZE) {
      refillQueue();
    }

    return true;
  }

  /** Fetch new batch of URLs to process, and submit completedBuffer */
  public void refillQueue() {
    log.info("-- refill queue");

    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(SERVER_URL + "/hello")).build();
    String result =
        httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body)
            //                .thenAccept(System.out::println)
            .join();
    log.info("got result " + result);
  }

  /** First time seeing a site, create forums */
  public static void firstTimeSiteInit() {}
}
