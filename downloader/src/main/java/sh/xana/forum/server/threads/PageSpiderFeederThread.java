package sh.xana.forum.server.threads;

import static sh.xana.forum.server.db.tables.Pages.PAGES;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.xml.crypto.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  public final ArrayBlockingQueue<Task> spiderQueue =
      new ArrayBlockingQueue<>(PageSpiderThread.SPIDER_THREAD_SIZE * PageSpiderThread.SPIDER_THREADS * 4);
  private final DatabaseStorage dbStorage;

  public PageSpiderFeederThread(DatabaseStorage dbStorage) {
    // Use queue blocking instead of post-cycle sleeps
    super("SpiderFeeder", 0);
    this.dbStorage = dbStorage;
  }

  @Override
  protected boolean runCycle() throws Exception {
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
      // manual sleep to avoid spinning on no data
      TimeUnit.MINUTES.sleep(1);
      return true;
    }
    synchronized (spiderWIP) {
      spiderWIP.addAll(parserPages);
    }
    // This is the flow control, will block until spiderQueue is free
    for (ParserPage parserPage : parserPages) {
      spiderQueue.put(parserPage);
    }

    return true;
  }

  public static class Task {
    public final ParserPage page;
    public State state;

    public Task(ParserPage page) {
      this.page = page;
    }
  }

  public enum State {
    QUEUED,
    PARSING,
    DONE,
  }
}
