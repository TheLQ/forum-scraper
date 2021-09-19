package sh.xana.forum.server;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import sh.xana.forum.client.ClientMain;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.threads.RuntimeDebugThread;
import sh.xana.forum.server.threads.WebServer;

public class ServerMain implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(ClientMain.class);
  private final List<Closeable> closableComponents = new ArrayList<>();

  public static void main(String[] args) throws Exception {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();

    new ServerMain();
  }

  private ServerMain() throws IOException {
    ServerConfig config = new ServerConfig();

    boolean debugMode = config.isDebugMode();
    if (debugMode) {
      log.warn("DEBUG MODE, not starting processor");
    } else {
      log.info("Production mode");
    }

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

    SqsManager sqsManager = new SqsManager(config, debugMode);
    closableComponents.add(sqsManager);
    RuntimeDebugThread debugThread = new RuntimeDebugThread(this);
    closableComponents.add(debugThread);
    NodeManager nodeManager = new NodeManager();

    DatabaseStorage dbStorage = new DatabaseStorage(config);
    closableComponents.add(dbStorage);

    WebServer server = new WebServer(dbStorage, nodeManager, config);
    server.start();





    if (!debugMode) {
      pageManager.startThreads();
      debugThread.start();
      // ClientMain.main(new String[0]);
    }
  }

  public void close() throws IOException {
    log.error("CLOSING");
    for (Closeable thread : closableComponents) {
      thread.close();
    }
  }
}
