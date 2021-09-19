package sh.xana.forum.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTaskThread implements AutoCloseable {
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

        boolean keepRunning = runCycle();
        if (!keepRunning) {
          log.warn("mainLoop returned false, stopping");
          break;
        }

        // Warning about busy-waiting, but Executors.newSingleThreadScheduledExecutor is overkill
        //noinspection BusyWait
        Thread.sleep(cycleSleepMillis);
      } catch (InterruptedException e) {
        log.info("Thread interrupted, closing");
        onInterrupt();
        break;
      } catch (Exception e) {
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
    }
    log.info("main loop ended");
  }

  protected void firstCycle() throws Exception {}

  /**
   * @return true to continue, false to stop
   * @throws Exception
   */
  protected abstract boolean runCycle() throws Exception;

  protected void onInterrupt() {}

  public void close() {
    Utils.closeThread(log, thread);
  }

  public void waitForThreadDeath() throws InterruptedException {
    log.info("Waiting for death");
    this.thread.join();
  }
}
