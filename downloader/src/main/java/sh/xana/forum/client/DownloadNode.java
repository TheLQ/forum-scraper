package sh.xana.forum.client;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.NodeBufferEntry;
import sh.xana.forum.server.WebPages;

/**
 * Each node handles 1 site. Nodes are distributed among multiple instances on different IPs
 *
 * <p>Rate limits all traffic to a particular site
 *
 * <p>Keeps a buffer of X TODO urls
 *
 * <p>When buffer is below Y, refill so download queue can keep going
 */
public class DownloadNode {
  public static final Logger log = LoggerFactory.getLogger(DownloadNode.class);
  /** Number of URLs to request, and size when to do a request. Should be between SIZE - 2xSIZE */
  public static final int URL_QUEUE_REFILL_SIZE = 10;
  private static int INSTANCE_COUNTER = 0;

  final String domain;
  final List<NodeBufferEntry> urlQueue = new ArrayList<>();
  final List<String> submissionBuffer = new ArrayList<>();

  Thread thread;

  public DownloadNode(String domain) {
    this.domain = domain;
    this.thread = new Thread(this::mainLoop);
    thread.setName("DownloadNode" + (INSTANCE_COUNTER++));
  }

  public void startThread() {
    thread.start();
  }

  public void mainLoop() {
    while (true) {
      boolean result;
      try {
        result = mainLoopCycle();
      } catch (Exception e) {
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
      if (!result) {
        log.warn("mainLoop returned false, stopping");
        break;
      }
    }
  }

  /**
   * Main execution loop
   *
   * @return true to continue to next loop
   */
  public boolean mainLoopCycle() {
    if (urlQueue.size() < URL_QUEUE_REFILL_SIZE) {
      refillQueue();
    }

    log.info("Queue");
    for (var queueEntry : urlQueue) {
      log.info("queue entry {}", queueEntry);
    }
    return false;
  }

  /** Fetch new batch of URLs to process, and submit completedBuffer */
  public void refillQueue() {
    log.info("-- refill queue");

    try {
      NodeBufferEntry[] newBuffer =
          Utils.jsonMapper.readValue(
              Utils.serverGet(WebPages.PAGE_CLIENT_BUFFER + "?domain=" + this.domain), NodeBufferEntry[].class);
      for (NodeBufferEntry entry : newBuffer) {
        urlQueue.add(entry);
      }
    } catch (Exception e) {
      throw new RuntimeException("failed to parse buffer json", e);
    }
  }
}
