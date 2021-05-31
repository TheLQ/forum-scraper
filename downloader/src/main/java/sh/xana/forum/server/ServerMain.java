package sh.xana.forum.server;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.client.ClientMain;
import sh.xana.forum.server.dbutil.DatabaseStorage;

public class ServerMain {
  public static final Logger log = LoggerFactory.getLogger(ClientMain.class);

  public static void main(String[] args) throws Exception {
    Options options = new Options();
    options.addOption("filecache", true, "filecache directory");
    options.addOption("h", false, "help");
    CommandLine cmd = new DefaultParser().parse(options, args);
    if (cmd.hasOption("h")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("myapp", "server", options, "", true);
      System.exit(1);
    }

    Path fileCachePath = Path.of("..", "filecache");
    if (cmd.hasOption("filecache")) {
      String server = cmd.getOptionValue("filecache");
      fileCachePath = Path.of(server);
    }
    if (!Files.exists(fileCachePath)) {
      throw new RuntimeException("fileCachePath does not exist " + fileCachePath);
    }

    // Hide giant logo it writes to logs on first load
    System.setProperty("org.jooq.no-logo", "true");

    DatabaseStorage dbStorage = new DatabaseStorage();
    Processor processor = new Processor(dbStorage, fileCachePath);
    NodeManager nodeManager = new NodeManager();

    WebServer server = new WebServer(dbStorage, processor, nodeManager);
    server.start();
    processor.startSpiderThread();
  }
}
