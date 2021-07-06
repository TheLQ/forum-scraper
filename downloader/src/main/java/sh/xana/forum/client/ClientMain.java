package sh.xana.forum.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.common.Utils;

public class ClientMain {
  public static final Logger log = LoggerFactory.getLogger(ClientMain.class);
  public static final List<Scraper> scrapers = new ArrayList<>();

  private static String PUBLIC_ADDRESS = null;
  private static final String HOSTNAME = System.getenv("HOSTNAME");

  public static void main(String[] args) throws URISyntaxException, IOException {
    log.info("Client start");

    ClientConfig config = new ClientConfig();

    Utils.BACKEND_SERVER = config.getRequiredArg(config.ARG_SERVER_ADDRESS);
    log.info("Setting custom server {}", Utils.BACKEND_SERVER);

    if (config.getOrDefault(config.ARG_ISAWS, "false").equals("true")) {
      new AwsClientManager().start();
    }

    SqsManager sqsManager = new SqsManager(config);

    // load webserver secret key
    Utils.BACKEND_KEY = config.getRequiredArg(config.ARG_SERVER_AUTH);

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

    PUBLIC_ADDRESS = Utils.serverGet("https://xana.sh/myip");
    log.info("Running on IP {} hostname {}", PUBLIC_ADDRESS, HOSTNAME);

    // Init scrapers
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
      Scraper downloader = new Scraper(config, sqsManager, queue);
      scrapers.add(downloader);

      downloader.startThread();
    }
  }

  public static void close() throws InterruptedException {
    for (Scraper scraper : ClientMain.scrapers) {
      scraper.close();
    }
    for (Scraper scraper : ClientMain.scrapers) {
      scraper.waitForThreadDeath();
    }
  }
}
