package sh.xana.forum.server.threads;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.exception.ExceptionUtils;
import sh.xana.forum.common.AbstractTaskThread;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.ParserEntry;
import sh.xana.forum.server.SpiderWarningException;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.ParserException;

public class PageSpiderThread extends AbstractTaskThread {
  public static final int SPIDER_THREAD_SIZE = 100;
  public static final int SPIDER_THREADS = 2;

  public PageSpiderThread(String name, long iterationSleepMillis) {
    super("Spider", 0);
  }

  @Override
  protected boolean runCycle() throws Exception {
    return false;
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
            pageParser.parsePage(Files.readAllBytes(config.getPagePath(pageId)), page);

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
}
