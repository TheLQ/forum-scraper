package sh.xana.forum.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.AbstractTaskThread;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.common.Utils;
import sh.xana.forum.server.threads.RuntimeDebugThread;

public class ClientMain implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(ClientMain.class);

  private final ClientConfig config;
  private final List<AutoCloseable> closableComponents = new ArrayList<>();

  public static void main(String[] args) throws URISyntaxException, IOException {
    new ClientMain().start();
  }

  public ClientMain() throws IOException {
    this.config = new ClientConfig();

    // -- Init backend
    Utils.BACKEND_SERVER = config.getRequiredArg(config.ARG_SERVER_ADDRESS);
    log.info("Setting custom server {}", Utils.BACKEND_SERVER);
    Utils.BACKEND_KEY = config.getRequiredArg(config.ARG_SERVER_AUTH);

    // PUBLIC_ADDRESS = Utils.serverGet("https://xana.sh/myip");
    // log.info("Running on IP {} hostname {}", PUBLIC_ADDRESS, HOSTNAME);
  }

  private void start() {
    log.info("Client start");

    // -- Add graceful shutdown hook
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    close();
                  } catch (Exception e) {
                    throw new RuntimeException("Failed to close");
                  }
                }));

    // -- Production debug and AWS controls
    if (config.getOrDefault(config.ARG_ISAWS, "false").equals("true")) {
      AwsClientManager awsClient = createTask(new AwsClientManager(this));
      awsClient.start();
    }
    createTask(new RuntimeDebugThread(this));

    // -- Production SQS Backend
    SqsManager sqsManager = new SqsManager(config);
    closableComponents.add(sqsManager);

    // -- Production Scrapers
    log.info("Request node init and scraper domain list");
    List<URI> queues;
    try {
      queues = sqsManager.getDownloadQueueUrls();
    } catch (Exception e) {
      log.info("Failed to get node entries, closing", e);
      return;
    }
    log.info("Registered node got scrapers {}", queues.size());

    for (URI queue : queues) {
      log.info("creating node " + queue);
      createTask(new Scraper(config, sqsManager, queue));
    }
  }

  private <T extends AbstractTaskThread> T createTask(T thread) {
    return Utils.createTask(config, closableComponents, thread);
  }

  public void close() throws Exception {
    for (AutoCloseable component : closableComponents) {
      component.close();
    }
  }
}
