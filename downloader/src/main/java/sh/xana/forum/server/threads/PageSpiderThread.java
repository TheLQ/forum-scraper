package sh.xana.forum.server.threads;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.AbstractTaskThread;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.Subpage;
import sh.xana.forum.server.ServerConfig;
import sh.xana.forum.server.SpiderWarningException;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.UpdatePageException;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.PageParser;
import sh.xana.forum.server.parser.ParserException;

public class PageSpiderThread extends AbstractTaskThread {
  private static final Logger log = LoggerFactory.getLogger(PageSpiderThread.class);
  public static final int NUM_THREADS = Runtime.getRuntime().availableProcessors() * 2;
  /** Wait until any queue is this big to flush */
  private static final int SQL_FLUSH_LIMIT = 100;

  private static int THREAD_COUNTER = 0;

  private final ServerConfig config;
  private final DatabaseStorage dbStorage;
  private final PageSpiderFeederThread feeder;
  private final PageParser pageParser;

  /** Page IDs queued to be bulk marked done in the database */
  private final List<UUID> sqlDone = new ArrayList<>();
  /** Pages queued to be bulk inserted into the database */
  private final List<DatabaseStorage.InsertPage> sqlNewPages = new ArrayList<>();
  /** Pages queued to be bulk updated with exceptions */
  private final List<DatabaseStorage.UpdatePageException> sqlExceptions = new ArrayList<>();

  public PageSpiderThread(
      ServerConfig config,
      DatabaseStorage dbStorage,
      PageSpiderFeederThread feeder,
      PageParser pageParser) {
    // Use queue blocking instead of post-cycle sleeps
    super("Spider" + THREAD_COUNTER++, 0);
    this.config = config;
    this.dbStorage = dbStorage;
    this.feeder = feeder;
    this.pageParser = pageParser;
  }

  @Override
  protected boolean runCycle() throws Exception {
    ParserPage page = feeder.getPageToParse_Poll();
    boolean nothingToParse = page == null;

    flushToDatabase(nothingToParse);

    if (nothingToParse) {
      // already flushed above, wait till new requests come in
      log.info("Waiting for new requests...");
      page = feeder.getPageToParse_Blocking();
    }

    spider(page);

    return true;
  }

  private void flushToDatabase(boolean flushNow) {
    // flush to db if full, or we have nothing else to do
    if (sqlNewPages.size() == SQL_FLUSH_LIMIT || (flushNow && !sqlNewPages.isEmpty())) {
      log.debug("dbsync insert");
      dbStorage.insertPagesQueued(sqlNewPages, true);
      sqlNewPages.clear();
    }
    if (sqlDone.size() == SQL_FLUSH_LIMIT || (flushNow && !sqlDone.isEmpty())) {
      log.debug("dbsync dlstatus done");
      dbStorage.setPageStatus(sqlDone, DlStatus.Done);
      feeder.setPageDone(sqlDone);
      sqlDone.clear();
    }
    if (sqlExceptions.size() == SQL_FLUSH_LIMIT || (flushNow && !sqlExceptions.isEmpty())) {
      log.debug("dbsync exceptions");
      dbStorage.setPageException(sqlExceptions);
      for (UpdatePageException sqlException : sqlExceptions) {
        feeder.setPageDone(sqlException.pageId());
      }
      sqlExceptions.clear();
    }
  }

  private void spider(ParserPage page) {
    UUID pageId = page.pageId();
    log.info("processing page {}", pageId);

    String output = null;
    try {
      ParserResult results =
          pageParser.parsePage(Files.readAllBytes(config.getPagePath(pageId)), page);

      for (Subpage subpage : results.subpages()) {
        sqlNewPages.add(
            new DatabaseStorage.InsertPage(
                pageId, page.siteId(), Utils.toURI(subpage.url().urlStr), subpage.pageType()));
      }

      sqlDone.add(pageId);
    } catch (JsonProcessingException e) {
      log.warn("JSON Parsing failed, found error " + output);
      sqlExceptions.add(new UpdatePageException(pageId, "NOT JSON\r\n" + output));
    } catch (SpiderWarningException e) {
      // same thing as below, just don't spam the log file
      sqlExceptions.add(new UpdatePageException(pageId, ExceptionUtils.getStackTrace(e)));
    } catch (Exception e) {
      Throwable loggedException = e;
      if (e instanceof ParserException && e.getCause() != null) {
        loggedException = e.getCause();
      }
      sqlExceptions.add(
          new UpdatePageException(pageId, ExceptionUtils.getStackTrace(loggedException)));
    }
  }

  @Override
  protected void onInterrupt() {
    log.info("flushing");
    flushToDatabase(true);
  }
}
