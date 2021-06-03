package sh.xana.forum.client;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.NodeInitRequest;
import sh.xana.forum.common.ipc.NodeResponse;
import sh.xana.forum.common.ipc.NodeResponse.ScraperEntry;
import sh.xana.forum.server.WebServer;

public class ClientMain {
  public static final Logger log = LoggerFactory.getLogger(ClientMain.class);
  public static final List<Scraper> scrapers = new ArrayList<>();

  private static String PUBLIC_ADDRESS = null;
  private static final String HOSTNAME = System.getenv("HOSTNAME");
  public static UUID NODE_ID;

  public static void main(String[] args) throws URISyntaxException, IOException {
    log.info("Client start");

    ClientConfig config = new ClientConfig();

    Utils.BACKEND_SERVER = config.getRequiredArg(config.ARG_SERVER_ADDRESS);
    log.info("Setting custom server {}", Utils.BACKEND_SERVER);

    if (config.getOrDefault(config.ARG_ISAWS, "false").equals("true")) {
      new AwsClientManager().start();
    }

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
    NodeResponse nodeResponse;
    try {
      NodeInitRequest request = new NodeInitRequest(PUBLIC_ADDRESS, HOSTNAME);
      String requestPost = Utils.jsonMapper.writeValueAsString(request);
      String rawResponse = Utils.serverPostBackend(WebServer.PAGE_CLIENT_NODEINIT, requestPost);
      nodeResponse = Utils.jsonMapper.readValue(rawResponse, NodeResponse.class);
    } catch (Exception e) {
      log.info("Failed to get node entries, closing", e);
      return;
    }
    NODE_ID = nodeResponse.nodeId();
    log.info(
        "Registered node {} got scrapers {}", NODE_ID, Arrays.toString(nodeResponse.scraper()));

    for (ScraperEntry entry : nodeResponse.scraper()) {
      log.info("creating node " + entry.domain());
      Scraper downloader = new Scraper(config, entry.domain());
      scrapers.add(downloader);

      downloader.startThread();
    }
  }

  public static void close() throws IOException, InterruptedException {
    for (Scraper scraper : ClientMain.scrapers) {
      scraper.close();
    }
    for (Scraper scraper : ClientMain.scrapers) {
      scraper.waitForThreadDeath();
    }
  }
}
