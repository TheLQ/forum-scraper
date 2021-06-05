package sh.xana.forum.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import sh.xana.forum.client.ClientMain;
import sh.xana.forum.common.CommonConfig;
import sh.xana.forum.server.dbutil.DatabaseStorage;

public class ServerMain {
  public static final Logger log = LoggerFactory.getLogger(ClientMain.class);

  private static Processor processor;

  public static void main(String[] args) throws Exception {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();

    boolean debugMode = System.getProperty(CommonConfig.PROPERTY_LOGBACK_TYPE) == null;
    if (debugMode) {
      log.warn("DEBUG MODE, not starting processor");
    }

    ServerConfig config = new ServerConfig();

    Path fileCachePath = Path.of("..", "filecache");
    if (config.hasArg(config.ARG_FILE_CACHE)) {
      String server = config.get(config.ARG_FILE_CACHE);
      fileCachePath = Path.of(server);
    }
    if (!debugMode && !Files.exists(fileCachePath)) {
      throw new RuntimeException(config.ARG_FILE_CACHE + " does not exist " + fileCachePath);
    }

    String nodeCmd = config.getOrDefault(config.ARG_NODE_CMD, "node");
    // start() will throw if the node cmd doesn't exist
    if (!debugMode) {
      Process process = new ProcessBuilder(nodeCmd, "-v").redirectErrorStream(true).start();
      String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
      log.info("Node version " + output.trim());
    }

    String parserScript = config.getOrDefault(config.ARG_PARSER_SCRIPT, "../parser/dist/parser.js");
    if (!debugMode && !Files.exists(Path.of(parserScript))) {
      throw new RuntimeException(config.ARG_PARSER_SCRIPT + " does not exist " + parserScript);
    }

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

    // Hide giant logo it writes to logs on first load
    System.setProperty("org.jooq.no-logo", "true");

    DatabaseStorage dbStorage = new DatabaseStorage(config);
    processor = new Processor(dbStorage, fileCachePath, nodeCmd, parserScript);
    NodeManager nodeManager = new NodeManager();

    WebServer server = new WebServer(dbStorage, processor, nodeManager, config);
    server.start();

    if (!debugMode) {
      processor.startSpiderThread();
    }
  }

  public static void close() throws InterruptedException, IOException {
    processor.close();
    processor.waitForThreadDeath();
  }
}
