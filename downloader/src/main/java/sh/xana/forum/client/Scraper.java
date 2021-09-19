package sh.xana.forum.client;

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
import sh.xana.forum.common.AbstractTaskThread;
import sh.xana.forum.common.RecieveRequest;
import sh.xana.forum.common.SqsManager;
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
public class Scraper extends AbstractTaskThread {
  private static final Logger log = LoggerFactory.getLogger(Scraper.class);
  private static final int CYCLE_SECONDS = 2;

  private final ClientConfig config;
  private final SqsManager sqsManager;
  private final URI queue;
  private final List<RecieveRequest<ScraperDownload>> scraperDownloads = new ArrayList<>();
  /** Message is from the original ScraperDownload */
  private final List<RecieveRequest<ScraperUpload>> scraperUploads = new ArrayList<>();

  public Scraper(ClientConfig config, SqsManager sqsManager, URI queue) {
    super(SqsManager.getQueueName(queue), TimeUnit.SECONDS.toMillis(CYCLE_SECONDS));
    this.config = config;
    this.sqsManager = sqsManager;
    this.queue = queue;
  }

  @Override
  protected boolean runCycle() throws InterruptedException {
    if (scraperDownloads.size() < SqsManager.QUEUE_SIZE) {
      refillQueue(true);
    }

    if (scraperDownloads.size() == 0) {
      log.info("Queue is empty, not doing anything");
    } else {
      // pop request and fetch content
      fetch(scraperDownloads.remove(0));
    }

    // sleep then continue for the next cycle
    log.debug(
        "{} download requests, {} upload requests, sleeping {} seconds",
        scraperDownloads.size(),
        scraperUploads.size(),
        CYCLE_SECONDS);
    return true;
  }

  private void fetch(RecieveRequest<ScraperDownload> scraperRequestMessage)
      throws InterruptedException {
    ScraperDownload scraperRequest = scraperRequestMessage.obj();

    List<URI> redirectList = new ArrayList<>();
    Exception caughtException = null;
    HttpResponse<byte[]> response = null;
    try {
      URI url = scraperRequest.url();
      log.debug("Requesting {} url {}", scraperRequest.pageId(), scraperRequest.url());
      HttpRequest request =
          HttpRequest.newBuilder()
              // Fix VERY annoying forum that gives different html without this, breaking parser
              .header("User-Agent", config.get(config.ARG_CLIENT_USERAGENT))
              .uri(url)
              .timeout(Duration.ofSeconds(60))
              .build();
      response = Utils.httpClient.send(request, BodyHandlers.ofByteArray());

      HttpResponse<byte[]> previousResponse = response.previousResponse().orElse(null);
      while (previousResponse != null) {
        redirectList.add(0, previousResponse.uri());
        previousResponse = previousResponse.previousResponse().orElse(null);
      }
      if (!redirectList.isEmpty()) {
        // add final destination url
        redirectList.add(response.uri());
      }
    } catch (InterruptedException e) {
      throw e;
    } catch (Exception e) {
      log.debug("exception during run", e);
      caughtException = e;
    }

    scraperUploads.add(
        new RecieveRequest<>(
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

  /** Fetch new batch of URLs to process, and submit completedBuffer */
  private void refillQueue(boolean requestMore) {
    log.info(
        "-- Refill, {} downloads, {} pending upload, {} request more",
        scraperDownloads.size(),
        scraperUploads.size(),
        requestMore);
    try {
      if (!scraperUploads.isEmpty()) {
        sqsManager.sendUploadRequests(
            scraperUploads.stream().map(RecieveRequest::obj).collect(Collectors.toList()));
        // deleted the processed scraperDownload messages that we copied to the scraperUpload list
        sqsManager.deleteQueueMessage(queue, scraperUploads);
      }

      if (requestMore) {
        scraperDownloads.addAll(sqsManager.receiveDownloadRequests(queue));
      }

      scraperUploads.clear();
    } catch (Exception e) {
      throw new RuntimeException("failed to parse buffer json", e);
    }
  }

  @Override
  protected void onInterrupt() {
    log.info("Captured interrupt, sending last request");
    refillQueue(false);
  }
}
