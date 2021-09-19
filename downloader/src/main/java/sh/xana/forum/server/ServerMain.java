package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import sh.xana.forum.client.ClientMain;
import sh.xana.forum.common.AbstractTaskThread;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.threads.PageDownloadsThread;
import sh.xana.forum.server.threads.PageUploadsThread;
import sh.xana.forum.server.threads.RuntimeDebugThread;
import sh.xana.forum.server.threads.WebServer;

public class ServerMain implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(ClientMain.class);
  private final List<AutoCloseable> closableComponents = new ArrayList<>();
  private final ServerConfig config;

  public static void main(String[] args) throws Exception {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();

    new ServerMain().init();
  }

  private ServerMain() throws IOException {
    config = new ServerConfig();
  }

  private void init() throws IOException {
    if (config.isDebugMode()) {
      log.warn("DEBUG MODE, not starting processor");
    } else {
      log.info("Production mode");
    }

    Path fileCachePath = Path.of("..", "filecache");
    if (config.hasArg(config.ARG_FILE_CACHE)) {
      String server = config.get(config.ARG_FILE_CACHE);
      fileCachePath = Path.of(server);
    }
    if (!config.isDebugMode() && !Files.exists(fileCachePath)) {
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

    // -- Core database
    DatabaseStorage dbStorage = new DatabaseStorage(config);
    closableComponents.add(dbStorage);

    // ...
    NodeManager nodeManager = new NodeManager();

    // -- Web server - safe to always start
    WebServer server = new WebServer(dbStorage, nodeManager, config);
    server.start();
    closableComponents.add(server);

    // -- Production debug
    createTask(new RuntimeDebugThread(this));

    // -- Production Upload/Download Queue management
    SqsManager sqsManager = new SqsManager(config);
    closableComponents.add(sqsManager);
    createTask(new PageDownloadsThread(dbStorage, sqsManager));
    createTasks(2, () -> new PageUploadsThread(config, dbStorage, sqsManager));

    // -- Production spider
    /*
    PageParser parser = new PageParser();
    PageSpiderFeederThread feeder = createTask(new PageSpiderFeederThread(dbStorage));
    createTasks(PageSpiderThread.NUM_THREADS, () -> new PageSpiderThread(config, dbStorage, feeder, parser));
     */
  }

  private <T extends AbstractTaskThread> T createTask(T thread) {
    closableComponents.add(thread);
    if (!config.isDebugMode()) {
      thread.start();
    }
    return thread;
  }

  private void createTasks(int numThreads, Supplier<AbstractTaskThread> threadSupplier) {
    for (int i = 0; i < numThreads; i++) {
      AbstractTaskThread thread = threadSupplier.get();
      closableComponents.add(thread);
      if (!config.isDebugMode()) {
        thread.start();
      }
    }
  }

  public void close() throws Exception {
    log.error("CLOSING");
    for (AutoCloseable thread : closableComponents) {
      thread.close();
    }
  }
}
