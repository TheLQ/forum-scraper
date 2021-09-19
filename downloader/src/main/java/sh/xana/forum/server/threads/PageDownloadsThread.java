package sh.xana.forum.server.threads;

import java.net.URI;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.OverviewEntry;
import sh.xana.forum.server.dbutil.DlStatus;

public class PageDownloadsThread extends AbstractTaskThread {
  private static final Logger log = LoggerFactory.getLogger(PageDownloadsThread.class);
  private final DatabaseStorage dbStorage;
  private final SqsManager sqsManager;

  public PageDownloadsThread(DatabaseStorage dbStorage, SqsManager sqsManager) {
    super("PageDownloads", TimeUnit.MINUTES.toMillis(1));
    this.dbStorage = dbStorage;
    this.sqsManager = sqsManager;
  }

  @Override
  protected void firstCycle() {
    createDownloadQueues();
  }

  @Override
  protected boolean runCycle() {
    return refillDownloadQueues();
  }

  private void createDownloadQueues() {
    log.info("Updating download queues...");

    final MutableBoolean updated = new MutableBoolean(false);
    for (OverviewEntry overviewEntry : dbStorage.getOverviewSites()) {
      String domain = dbStorage.siteCache.recordById(overviewEntry.siteId()).getDomain();
      String queueNameSafe = SqsManager.getQueueNameSafe(domain);

      if (overviewEntry.dlStatusCount().get(DlStatus.Queued) == null
          && overviewEntry.dlStatusCount().get(DlStatus.Download) == null
          && overviewEntry.dlStatusCount().getOrDefault(DlStatus.Parse, 0)
                  - overviewEntry.parseLoginRequired().getValue()
              == 0) {
        // delete queue because no pages queued, no pages downloading,
        // no pages to parse that aren't LoginExceptions
        sqsManager.getDownloadQueueUrls().stream()
            .filter(e -> e.toString().contains(queueNameSafe))
            .findFirst()
            .ifPresent(
                uri -> {
                  log.info("Deleting queue " + uri);
                  sqsManager.deleteQueue(uri);
                  updated.setTrue();
                });
      } else {
        // create if queue doesn't exist because we have work
        if (sqsManager.getDownloadQueueUrls().stream()
            .filter(e -> e.toString().contains(queueNameSafe))
            .findFirst()
            .isEmpty()) {
          log.info("New queue " + domain);
          sqsManager.newDownloadQueue(domain);
          updated.setTrue();
        }
      }
    }

    if (updated.isTrue()) {
      sqsManager.updateDownloadQueueUrls();
    } else {
      log.info("All queues already created for domains");
    }
  }

  private boolean refillDownloadQueues() {
    final int expectedQueueSize = SqsManager.QUEUE_SIZE * 10;
    Set<Entry<URI, Integer>> entries = sqsManager.getDownloadQueueSizes().entrySet();
    log.info("found {} queuss", entries.size());
    for (var entry : entries) {
      if (entry.getValue() > expectedQueueSize) {
        continue;
      }
      String domain = SqsManager.getQueueDomain(entry.getKey());
      domain = SqsManager.getQueueNameSafeOrig(domain);
      UUID siteid = dbStorage.siteCache.recordByDomain(domain).getSiteid();
      // Give the queues sufficient lead time
      log.debug("domain " + domain);
      List<ScraperDownload> scraperDownloads =
          dbStorage.movePageQueuedToDownloadIPC(siteid, expectedQueueSize * 10);
      if (!scraperDownloads.isEmpty()) {
        log.debug(
            "Pushing {} download requests for {}",
            scraperDownloads.size(),
            SqsManager.getQueueName(entry.getKey()));
        sqsManager.sendDownloadRequests(entry.getKey(), scraperDownloads);
      }
    }

    return true;
  }
}
