package sh.xana.forum.client;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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

  public static void main(String[] args) throws ParseException, URISyntaxException {
    log.info("Client start");

    Options options = new Options();
    options.addOption("server", true, "central server");
    options.addOption("aws", false, "is running on aws");
    options.addOption("h", false, "help");
    CommandLine cmd = new DefaultParser().parse(options, args);
    if (cmd.hasOption("h")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("myapp", "server", options, "", true);
      System.exit(1);
    }
    if (cmd.hasOption("server")) {
      String server = cmd.getOptionValue("server");
      log.info("Setting custom server {}", server);
      Utils.BACKEND_SERVER = server;
    }
    if (cmd.hasOption("aws")) {
      new AwsClientManager().start();
    }

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
      Scraper downloader = new Scraper(entry.domain());
      scrapers.add(downloader);

      downloader.startThread();
    }
  }
}
