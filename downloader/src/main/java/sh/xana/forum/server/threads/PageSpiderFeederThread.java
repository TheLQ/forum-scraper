package sh.xana.forum.server.threads;

import static sh.xana.forum.server.db.tables.Pages.PAGES;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.AbstractTaskThread;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ParserPage;

/**
 * Problem: getParserPages can be slow when lots of other traffic going on, starving the
 * spiderThread in large audit runs.
 *
 * <p>Do not want to grab an unlimited number of pages due to memory constraints. However if you
 * call getParser multiple times consecutively you'll get the same pages. WHERE pageId > x ORDER BY
 * pageId is too heavy on the DB. So track what's Work in Progress (WIP) and exclude from queries.
 */
public class PageSpiderFeederThread extends AbstractTaskThread {
  private static final Logger log = LoggerFactory.getLogger(PageSpiderFeederThread.class);
  private static final int QUEUE_SIZE = PageSpiderThread.NUM_THREADS * 200;
  private final DatabaseStorage dbStorage;
  /**
   * All pages either parsing or waiting to be parsed. Used to not re-fetch the same pages from the
   * db
   */
  private final List<UUID> pagesWIP = new ArrayList<>();
  /** Fetched pages ready for PageSpiderThread */
  private final BlockingQueue<ParserPage> queuedForParsing = new ArrayBlockingQueue<>(QUEUE_SIZE);
  /**
   * Exists so pagesWIP can be given to jOOq for a long query, instead of synchronizing on it then
   * blocking PageSpiderThread's remove() for an unnecessarily long time, or ugly copying
   *
   * <p><b>public write</b>
   */
  private final List<UUID> queuedForWIPDone = new ArrayList<>(QUEUE_SIZE);

  public PageSpiderFeederThread(DatabaseStorage dbStorage) {
    // Use queue blocking instead of post-cycle sleeps
    super("SpiderFeeder", 0);
    this.dbStorage = dbStorage;
  }

  @Override
  protected boolean runCycle() throws Exception {
    fill();
    return true;
  }

  protected void fill() throws InterruptedException {
    // Make sure pagesWIP is accurate
    synchronized (queuedForWIPDone) {
      pagesWIP.removeAll(queuedForWIPDone);
      queuedForWIPDone.clear();
    }

    log.info("Loading pages excluding {} WIP pages", pagesWIP.size());
    List<ParserPage> parserPages =
        dbStorage.getParserPages(
            true,
            PAGES.DLSTATUS.eq(DlStatus.Parse),
            PAGES.EXCEPTION.isNull(),
            PAGES.PAGEID.notIn(pagesWIP));
    if (parserPages.isEmpty()) {
      // Scheduling control: manual sleep to avoid spinning on no data
      TimeUnit.MINUTES.sleep(1);
      return;
    }

    for (ParserPage parserPage : parserPages) {
      pagesWIP.add(parserPage.pageId());
      // Scheduling control: will block until queue has room for more
      queuedForParsing.put(parserPage);
    }
  }

  public ParserPage getPageToParse_Poll() {
    return queuedForParsing.poll();
  }

  public ParserPage getPageToParse_Blocking() throws InterruptedException {
    return queuedForParsing.take();
  }

  public void setPageDone(UUID page) {
    synchronized (queuedForWIPDone) {
      queuedForWIPDone.add(page);
    }
  }

  public void setPageDone(Collection<UUID> pages) {
    synchronized (queuedForWIPDone) {
      queuedForWIPDone.addAll(pages);
    }
  }
}
