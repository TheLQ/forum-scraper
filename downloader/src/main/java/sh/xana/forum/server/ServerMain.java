package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import sh.xana.forum.client.ClientMain;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.server.dbutil.DatabaseStorage;

public class ServerMain {
  private static final Logger log = LoggerFactory.getLogger(ClientMain.class);

  private static PageManager pageManager;
  private static RuntimeDebugThread debugThread;

  public static void main(String[] args) throws Exception {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();

    boolean debugMode = false; // System.getProperty(CommonConfig.PROPERTY_LOGBACK_TYPE) == null;
    if (debugMode) {
      log.warn("DEBUG MODE, not starting processor");
    } else {
      log.info("Production mode");
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

    SqsManager sqsManager = new SqsManager(config);
    DatabaseStorage dbStorage = new DatabaseStorage(config);
    //    if (true) {
    //      PagesRecord page =
    // dbStorage.getPage(UUID.fromString("036afcb1-3f90-4569-a3e6-96dcffd5f3c9"));
    //      log.info(page.toString());
    //      return;
    //    }
    pageManager = new PageManager(dbStorage, config, sqsManager);
    NodeManager nodeManager = new NodeManager();

    WebServer server = new WebServer(dbStorage, pageManager, nodeManager, config);
    server.start();

    debugThread = new RuntimeDebugThread();
    if (!debugMode) {
      pageManager.startThreads();
      debugThread.start();
      // ClientMain.main(new String[0]);
    }
  }

  public static void close() throws InterruptedException, IOException {
    log.error("CLOSING");
    pageManager.close();
    debugThread.close();
  }
}
