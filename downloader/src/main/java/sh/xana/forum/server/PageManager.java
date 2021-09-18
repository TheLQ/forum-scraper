package sh.xana.forum.server;

import static sh.xana.forum.server.db.tables.Pages.PAGES;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.RecieveRequest;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.ParserEntry;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.common.ipc.ScraperUpload;
import sh.xana.forum.server.db.tables.records.PageredirectsRecord;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.OverviewEntry;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.PageParser;
import sh.xana.forum.server.parser.ParserException;

/** Parse stage. Extract further URLs for downloading */
public class PageManager implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(PageManager.class);

  private final DatabaseStorage dbStorage;
  private final ServerConfig config;
  private final SqsManager sqsManager;
  private final Thread spiderFeederThread;
  private final Thread downloadsThread;
  private final PageParser pageParser;
  private final List<Thread> workerThreads = new ArrayList<>();

  private static final int SPIDER_THREAD_SIZE = 100;
  private static final int SPIDER_THREADS = 2;
  private final ArrayBlockingQueue<ParserPage> spiderQueue =
      new ArrayBlockingQueue<>(SPIDER_THREAD_SIZE * SPIDER_THREADS * 4);
  private final List<ParserPage> spiderWIP = new ArrayList<>();

  public PageManager(DatabaseStorage dbStorage, ServerConfig config, SqsManager sqsManager) {
    this.dbStorage = dbStorage;
    this.config = config;
    this.sqsManager = sqsManager;

    this.spiderFeederThread = new Thread(this::pageSpiderFeederThread);
    this.spiderFeederThread.setName("PageSpiderFeeder");

    this.downloadsThread = new Thread(this::downloadsThread);
    this.downloadsThread.setName("PageDownloads");

    this.pageParser = new PageParser(config);
  }

  public void startThreads() {
    List<Thread> spiderThreads =
        Auditor.threadRunner(SPIDER_THREADS, "PageSpider-", this::pageSpiderThread);
    this.workerThreads.addAll(spiderThreads);
    this.spiderFeederThread.start();
    List<Thread> uploadThreads = Auditor.threadRunner(2, "PageUploads-", this::uploadsThread);
    this.workerThreads.addAll(uploadThreads);
    this.downloadsThread.start();
  }

  public void uploadsThread() {
    while (true) {
      boolean result;
      try {
        result = processUploads();
      } catch (Exception e) {
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
      if (!result) {
        log.warn("mainLoop returned false, stopping");
        break;
      }
    }
    log.info("main loop ended");
  }

  /** Process responses the download scraper collected */
  public boolean processUploads() throws IOException, InterruptedException {
    while (true) {
      List<RecieveRequest<ScraperUpload>> recieveRequests = sqsManager.receiveUploadRequests();
      if (recieveRequests.isEmpty()) {
        log.debug("No uploads to process");
        sleep();
        return true;
      }

      _processUploads(recieveRequests);
    }
  }

  public void _processUploads(List<RecieveRequest<ScraperUpload>> recieveRequests)
      throws IOException, InterruptedException {
    List<PageredirectsRecord> sqlNewRedirects = new ArrayList<>();
    for (RecieveRequest<ScraperUpload> successMessage : recieveRequests) {
      ScraperUpload success = successMessage.obj();
      log.debug("Writing " + success.pageId().toString() + " response and header");
      try {
        PagesRecord page = dbStorage.getPage(success.pageId());
        if (page.getPageurl().equals("asdf")) {
          throw new RuntimeException("que?");
        }
      } catch (Exception e) {
        log.error(
            "Failed to get page "
                + success.pageId()
                + " url "
                + success.pageUrl()
                + " headers "
                + success.headers(),
            e);
        continue;
      }

      // Body and headers can be null if the request just failed
      byte[] body = success.body();
      body = body == null ? new byte[0] : body;

      String headers;
      if (success.headers() == null) {
        headers = "";
      } else {
        headers = Utils.jsonMapper.writeValueAsString(success.headers());
      }

      Files.write(pageParser.getPagePath(success.pageId()), body);
      Files.writeString(pageParser.getPageHeaderPath(success.pageId()), headers);

      dbStorage.movePageDownloadToParse(success.pageId(), success.responseCode());

      if (!success.redirectList().isEmpty()) {
        byte counter = 0;
        URI lastUri = null;
        for (URI newUri : success.redirectList()) {
          sqlNewRedirects.add(new PageredirectsRecord(success.pageId(), newUri, counter++));
          lastUri = newUri;
        }
        try {
          dbStorage.setPageURL(success.pageId(), lastUri);
        } catch (Exception e) {
          if (e.getCause() instanceof SQLIntegrityConstraintViolationException
              && e.getCause().getMessage().contains("Duplicate entry")) {
            // we have redirected to an existing page. So we don't need this anymore
            dbStorage.deletePage(success.pageId());
            continue;
          } else {
            throw e;
          }
        }
      }

      if (success.exception() != null) {
        dbStorage.setPageException(success.pageId(), success.exception());
      }
    }

    if (!sqlNewRedirects.isEmpty()) {
      log.debug("dbsync redirect");
      dbStorage.insertPageRedirects(sqlNewRedirects);
    }

    sqsManager.deleteUploadRequests(recieveRequests);
  }

  private void pageSpiderFeederThread() {
    while (true) {
      try {
        pageSpiderFeederCycle();
      } catch (Exception e) {
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
    }
    log.info("main loop ended");
  }

  private void pageSpiderFeederCycle() throws InterruptedException {
    /*
    Problem: getParserPages can be slow when lots of other traffic going on, starving the
    spiderThread in large audit runs.

    Do not want to grab an unlimited number of pages due to memory constraints. However if you call
    getParser multiple times consecutively you'll get the same pages. WHERE pageId > x ORDER BY pageId
    is too heavy on the DB. So track what's Work in Progress (WIP) and exclude from queries.
     */
    List<UUID> wipIds;
    synchronized (spiderWIP) {
      wipIds = spiderWIP.stream().map(ParserPage::pageId).collect(Collectors.toList());
    }
    log.info("Loading pages");
    List<ParserPage> parserPages =
        dbStorage.getParserPages(
            true,
            PAGES.DLSTATUS.eq(DlStatus.Parse),
            PAGES.EXCEPTION.isNull(),
            PAGES.PAGEID.notIn(wipIds));
    if (parserPages.isEmpty()) {
      sleep();
      return;
    }
    synchronized (spiderWIP) {
      spiderWIP.addAll(parserPages);
    }
    // This is the flow control, will block until spiderQueue is free
    for (ParserPage parserPage : parserPages) {
      spiderQueue.put(parserPage);
    }
  }

  /** */
  private void pageSpiderThread() {

    try {
      spider();
    } catch (Exception e) {
      log.error("Caught exception in mainLoop, stopping", e);
    }

    log.info("main loop ended");
  }

  private void spider() throws InterruptedException {
    List<ParserPage> processedPages = new ArrayList<>();
    List<UUID> sqlDone = new ArrayList<>(SPIDER_THREAD_SIZE);
    List<DatabaseStorage.InsertPage> sqlNewPages = new ArrayList<>();
    while (true) {
      ParserPage page = spiderQueue.poll();
      if (processedPages.size() == SPIDER_THREAD_SIZE
          || (page == null && !processedPages.isEmpty())) {
        // flush but not unnecessarily when empty
        if (!sqlNewPages.isEmpty()) {
          log.debug("dbsync insert");
          dbStorage.insertPagesQueued(sqlNewPages, true);
          sqlNewPages.clear();
        }
        if (!sqlDone.isEmpty()) {
          log.debug("dbsync dlstatus done");
          dbStorage.setPageStatus(sqlDone, DlStatus.Done);
          sqlDone.clear();
        }
        synchronized (spiderWIP) {
          spiderWIP.removeAll(processedPages);
          processedPages.clear();
        }
        log.debug("dbsync done");
      }
      if (page == null) {
        // already flushed above, wait till new requests come in
        log.info("Waiting for new requests...");
        page = spiderQueue.take();
      }
      processedPages.add(page);

      UUID pageId = page.pageId();
      log.info("processing page {}", pageId);

      String output = null;
      try {
        ParserResult results =
            pageParser.parsePage(Files.readAllBytes(pageParser.getPagePath(pageId)), page);

        for (ParserEntry subpage : results.subpages()) {
          sqlNewPages.add(
              new DatabaseStorage.InsertPage(
                  pageId, page.siteId(), Utils.toURI(subpage.url()), subpage.pageType()));
        }

        sqlDone.add(pageId);
      } catch (JsonProcessingException e) {
        log.warn("JSON Parsing failed, found error " + output);
        dbStorage.setPageException(pageId, "NOT JSON\r\n" + output);
      } catch (SpiderWarningException e) {
        // same thing as below, just don't spam the log file
        dbStorage.setPageException(pageId, ExceptionUtils.getStackTrace(e));
      } catch (Exception e) {
        Throwable loggedException = e;
        if (e instanceof ParserException && e.getCause() != null) {
          loggedException = e.getCause();
        }
        dbStorage.setPageException(pageId, ExceptionUtils.getStackTrace(loggedException));
      }
    }
  }

  private void downloadsThread() {
    boolean first = true;

    while (true) {
      boolean result;
      try {
        if (first) {
          createDownloadQueues();
          // fill so downloaders work while we process the upload queue
          refillDownloadQueues();
          first = false;
        }
        result = refillDownloadQueues();
      } catch (Exception e) {
        log.error("Caught exception in mainLoop, stopping", e);
        break;
      }
      if (!result) {
        log.warn("mainLoop returned false, stopping");
        break;
      }
    }
    log.info("main loop ended");
  }

  private void createDownloadQueues() {
    // clear out old domains
    log.info("Updating download queues...");

    Map<UUID, Integer> overviewToParse = dbStorage.getOverviewToParse();

    boolean updated = false;
    for (OverviewEntry overviewEntry : dbStorage.getOverviewSites()) {
      String domain = dbStorage.siteCache.recordById(overviewEntry.siteId()).getDomain();
      String queueNameSafe = SqsManager.getQueueNameSafe(domain);
      Integer parseCount = overviewToParse.get(domain);
      if (overviewEntry.dlStatusCount().get(DlStatus.Queued) == null
          && overviewEntry.dlStatusCount().get(DlStatus.Download) == null
          && (parseCount == null || parseCount == 0)) {
        // delete if queue exists because it's empty
        URI uri =
            sqsManager.getDownloadQueueUrls().stream()
                .filter(e -> e.toString().contains(queueNameSafe))
                .findFirst()
                .orElse(null);
        if (uri != null) {
          log.info("Deleting queue " + uri);
          sqsManager.deleteQueue(uri);
          updated = true;
        }
      } else {
        // create if queue doesn't exist because we have work
        URI queue =
            sqsManager.getDownloadQueueUrls().stream()
                .filter(e -> e.toString().contains(queueNameSafe))
                .findFirst()
                .orElse(null);
        if (queue == null) {
          log.info("New queue " + domain);
          sqsManager.newDownloadQueue(domain);
          updated = true;
        }
      }
    }

    if (updated) {
      sqsManager.updateDownloadQueueUrls();
    } else {
      log.info("All queues already created for domains");
    }
  }

  private boolean refillDownloadQueues() throws InterruptedException {
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

    sleep();
    return true;
  }

  public void sleep() throws InterruptedException {
    log.debug("sleep...");
    TimeUnit.SECONDS.sleep(60);
  }

  @Override
  public void close() throws IOException {
    log.info("close called, stopping thread");
    for (Thread workerThread : workerThreads) {
      closeThread(workerThread);
    }
    closeThread(downloadsThread);
    closeThread(spiderFeederThread);
  }

  private void closeThread(Thread thread) {
    if (thread.isAlive()) {
      thread.interrupt();
    }
  }
}
