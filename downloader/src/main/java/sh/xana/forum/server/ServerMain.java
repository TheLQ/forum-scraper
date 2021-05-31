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
  private static final String ARG_FILE_CACHE = "fileCache";
  private static final String ARG_NODE_CMD = "nodeCmd";
  private static final String ARG_PARSER_SCRIPT = "parserScript";

  public static void main(String[] args) throws Exception {
    Options options = new Options();
    options.addOption(ARG_FILE_CACHE, true, "filecache directory");
    options.addOption(ARG_NODE_CMD, true, "node command path");
    options.addOption(ARG_PARSER_SCRIPT, true, "parser script path");
    options.addOption("h", false, "help");
    CommandLine cmd = new DefaultParser().parse(options, args);
    if (cmd.hasOption("h")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("myapp", "server", options, "", true);
      System.exit(1);
    }

    Path fileCachePath = Path.of("..", "filecache");
    if (cmd.hasOption(ARG_FILE_CACHE)) {
      String server = cmd.getOptionValue(ARG_FILE_CACHE);
      fileCachePath = Path.of(server);
    }
    if (!Files.exists(fileCachePath)) {
      throw new RuntimeException("fileCachePath does not exist " + fileCachePath);
    }

    String nodeCmd = "node";
    if (cmd.hasOption(ARG_NODE_CMD)) {
      nodeCmd = cmd.getOptionValue(ARG_NODE_CMD);
    }

    String parserScript = "../parser/parser.js";
    if (cmd.hasOption(ARG_PARSER_SCRIPT)) {
      parserScript = cmd.getOptionValue(ARG_PARSER_SCRIPT);
    }

    // Hide giant logo it writes to logs on first load
    System.setProperty("org.jooq.no-logo", "true");

    DatabaseStorage dbStorage = new DatabaseStorage();
    Processor processor = new Processor(dbStorage, fileCachePath, nodeCmd, parserScript);
    NodeManager nodeManager = new NodeManager();

    WebServer server = new WebServer(dbStorage, processor, nodeManager);
    server.start();
    processor.startSpiderThread();
  }
}
