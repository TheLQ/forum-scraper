package sh.xana.forum.server.threads;

import java.io.Closeable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTaskThread implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(AbstractTaskThread.class);
  private final Thread thread;
  private final long cycleSleepMillis;
  private boolean isFirstIteration = false;

  protected AbstractTaskThread(String name, long cycleSleepMillis) {
    this.cycleSleepMillis = cycleSleepMillis;
    this.thread = new Thread(this::mainLoop);
    this.thread.setName(name);
    this.thread.setDaemon(false);
  }

  public void start() {
    this.thread.start();
  }

  private void mainLoop() {
    while (true) {
      try {
        if (isFirstIteration) {
          firstCycle();
          isFirstIteration = false;
        }

        boolean result = runCycle();
        if (!result) {
          log.warn("mainLoop returned false, stopping");
          break;
        }

        // Warning about busy-waiting, but Executors.newSingleThreadScheduledExecutor is overkill
        //noinspection BusyWait
        Thread.sleep(cycleSleepMillis);
      } catch (InterruptedException e) {
        log.info("Thread interrupted, closing");
        break;
      } catch (Exception e) {
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
    }
    log.info("main loop ended");
  }

  protected void firstCycle() throws Exception {}

  protected abstract boolean runCycle() throws Exception;

  public void close() {
    log.info("Closing thead");
    this.thread.interrupt();
  }

  public void waitForThreadDeath() throws InterruptedException {
    log.info("Waiting for death");
    this.thread.join();
  }
}
