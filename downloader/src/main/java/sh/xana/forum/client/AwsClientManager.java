package sh.xana.forum.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;

/**
 * https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-service.html
 */
public class AwsClientManager {
  private static final Logger log = LoggerFactory.getLogger(AwsClientManager.class);
  private final Thread thread;

  private String metadataToken;

  public AwsClientManager() {
    thread = new Thread(this::awsThread);
    thread.setName("AwsManager");
  }

  public void start() throws URISyntaxException {
    newToken();
    thread.start();
  }

  private void newToken() throws URISyntaxException {
    log.info("Requesting AWS token...");
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("http://169.254.169.254/latest/api/token"))
            .header("X-aws-ec2-metadata-token-ttl-seconds", "21600")
            .PUT(BodyPublishers.noBody())
            .build();
    metadataToken = Utils.serverRequest(request, BodyHandlers.ofString()).body();
    log.info("Got metadata token {}", metadataToken);
  }

  private void awsThread() {
    log.info("Starting aws thread");
    while (true) {
      try {
        boolean continueLoop = awsThreadCycle();
        if (!continueLoop) {
          log.info("Stopping thread per request");
        }
      } catch (Exception e) {
        log.error("aws fail!", e);
        break;
      }
    }
  }

  private boolean awsThreadCycle() throws InterruptedException, URISyntaxException, IOException {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("http://169.254.169.254/latest/meta-data/spot/instance-action"))
            .header("X-aws-ec2-metadata-token", metadataToken)
            .PUT(BodyPublishers.noBody())
            .build();
    HttpResponse<String> response = Utils.httpClient.send(request, BodyHandlers.ofString());
    switch (response.statusCode()) {
      case 401:
        newToken();
        break;
      case 404:
        // no active request
        break;
      case 200:
        log.warn("AWS Requesting shutdown " + response);
        for (Scraper scraper : ClientMain.scrapers) {
          scraper.close();
        }
        return false;
      default:
        throw new RuntimeException(
            "Got status " + response.statusCode() + " when refreshing metadata " + response.body());
    }

    Thread.sleep(10 * 1000);
    return true;
  }
}
