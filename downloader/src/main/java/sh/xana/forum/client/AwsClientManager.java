package sh.xana.forum.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.AbstractTaskThread;
import sh.xana.forum.common.Utils;

/**
 * https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-service.html
 */
public class AwsClientManager extends AbstractTaskThread {
  private static final Logger log = LoggerFactory.getLogger(AwsClientManager.class);
  private final AutoCloseable main;
  private String metadataToken;

  public AwsClientManager(AutoCloseable main) {
    super("AwsManager", TimeUnit.SECONDS.toMillis(10));
    this.main = main;
  }

  @Override
  protected void firstCycle() throws Exception {
    newToken();
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

  @Override
  protected boolean runCycle() throws Exception {
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
        main.close();
        return false;
      default:
        throw new RuntimeException(
            "Got status " + response.statusCode() + " when refreshing metadata " + response.body());
    }
    return true;
  }
}
