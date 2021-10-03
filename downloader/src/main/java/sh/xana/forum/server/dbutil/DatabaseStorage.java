package sh.xana.forum.server.dbutil;

import static sh.xana.forum.server.db.tables.Pageredirects.PAGEREDIRECTS;
import static sh.xana.forum.server.db.tables.Pages.PAGES;
import static sh.xana.forum.server.db.tables.Sites.SITES;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.ComparatorUtils;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.TableField;
import org.jooq.conf.Settings;
import org.jooq.conf.ThrowExceptions;
import org.jooq.impl.DSL;
import org.jsoup.helper.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.server.ServerConfig;
import sh.xana.forum.server.db.tables.Dataset;
import sh.xana.forum.server.db.tables.records.DatasetRecord;
import sh.xana.forum.server.db.tables.records.PageredirectsRecord;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.db.tables.records.SitesRecord;

public class DatabaseStorage implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(DatabaseStorage.class);
  public final SiteCache siteCache;

  private final DSLContext context;
  private final AutoCloseable closableConnection;

  public DatabaseStorage(ServerConfig config) {
    log.info("Connecting to database");

    // Hide giant logo it writes to logs on first load
    System.setProperty("org.jooq.no-logo", "true");

    // creates actual DB connections
    ConnectionFactory connectionFactory =
        new DriverManagerConnectionFactory(
            config.getRequiredArg(config.ARG_DB_CONNECTIONSTRING),
            config.get(config.ARG_DB_USER),
            config.get(config.ARG_DB_PASS));
    // wraps actual connections with pooled ones
    PoolableConnectionFactory poolableConnectionFactory =
        new PoolableConnectionFactory(connectionFactory, null);
    // object pool impl
    ObjectPool<PoolableConnection> connectionPool =
        new GenericObjectPool<>(poolableConnectionFactory);
    poolableConnectionFactory.setPool(connectionPool);
    // jdbc DataSource
    PoolingDataSource<PoolableConnection> dataSource = new PoolingDataSource<>(connectionPool);
    closableConnection = dataSource;

    context =
        DSL.using(
            dataSource,
            SQLDialect.MARIADB,
            new Settings().withThrowExceptions(ThrowExceptions.THROW_ALL).withFetchWarnings(false));
    log.info("Connected to database");

    siteCache = new SiteCache(this);
  }

  /** Stage: Load pages to be downloaded by Scraper */
  public List<ScraperDownload> movePageQueuedToDownloadIPC(UUID siteId, int size) {
    Validate.notNull(siteId, "siteId");
    // TODO: VERY SUSPICIOUS RACE. Seems multiple clients can request the page,
    List<ScraperDownload> pages =
        context
            .select(PAGES.PAGEID, PAGES.PAGEURL)
            .from(PAGES)
            .where(
                PAGES.SITEID.eq(siteId),
                PAGES.DLSTATUS.eq(DlStatus.Queued),
                PAGES.EXCEPTION.isNull())
            // Select ForumList first, which alphabetically is before TopicPage
            .orderBy(PAGES.PAGETYPE)
            .limit(size)
            .fetch()
            .map(r -> new ScraperDownload(r.value1(), r.value2()));

    setPageStatus(
        pages.stream().map(ScraperDownload::pageId).collect(Collectors.toList()),
        DlStatus.Download);

    return pages;
  }

  /** Stage: Page is finished downloading */
  public void movePageDownloadToParse(UUID pageId, int statusCode) {
    Validate.notNull(pageId, "siteId");
    Query query =
        context
            .update(PAGES)
            .set(PAGES.DLSTATUS, DlStatus.Parse)
            .set(PAGES.DLSTATUSCODE, statusCode)
            .set(PAGES.PAGEUPDATED, LocalDateTime.now())
            .where(PAGES.PAGEID.eq(pageId));

    executeOneRow(query);
  }

  /** Stage: Load pages for Parser */
  public Stream<ParserPage> getParserPagesStream(boolean limited, Condition... conditions) {
    StopWatch timer = new StopWatch();
    timer.start();
    var query =
        context
            .select(
                PAGES.PAGEID,
                PAGES.PAGEURL,
                PAGES.PAGETYPE,
                PAGES.DLSTATUSCODE,
                PAGES.SITEID,
                SITES.SITEURL,
                SITES.FORUMTYPE)
            .from(PAGES)
            .innerJoin(SITES)
            .using(PAGES.SITEID)
            .where(conditions);
    if (limited) {
      query.limit(8000);
    }
    return query.stream()
        .map(
            e ->
                new ParserPage(
                    e.value1(),
                    e.value2(),
                    e.value3(),
                    e.value4(),
                    e.value5(),
                    e.value6(),
                    e.value7()));
  }

  public List<ParserPage> getParserPages(boolean limited, Condition... conditions) {
    //    StopWatch timer = new StopWatch();
    //    timer.start();
    //
    //    log.trace(
    //        "getParserPages fetched " + res.size() + " in " + timer + System.lineSeparator() +
    // query);
    //    return res;
    return getParserPagesStream(limited, conditions).collect(Collectors.toList());
  }

  public record OverviewEntry(
      UUID siteId,
      Map<DlStatus, Integer> dlStatusCount,
      MutableInt parseLoginRequired,
      MutableInt parseSoft500,
      MutableInt parseOtherException) {}

  /** Stage: reporting monitor */
  @SuppressWarnings("unchecked")
  public List<OverviewEntry> getOverviewSites() {
    var query =
        context
            .select(
                DSL.count(),
                PAGES.DLSTATUS,
                PAGES.SITEID,
                DSL.count(DSL.when(PAGES.EXCEPTION.like("%LoginRequired%"), 1)),
                DSL.count(DSL.when(PAGES.EXCEPTION.like("%Soft500Exception%"), 1)),
                DSL.count(DSL.when(PAGES.EXCEPTION.isNotNull(), 1)))
            .from(PAGES)
            .groupBy(PAGES.DLSTATUS, PAGES.SITEID);
    log.info("QUERY " + query);

    // Convert (Site,Status1,#),(Site,Status2,#),etc to
    // (Site1,Map<DlStatus,#>),(Site2,Map<DlStatus,#>),etc
    List<OverviewEntry> result = new ArrayList<>();
    for (var page : query.fetch()) {
      UUID siteId = page.value3();
      OverviewEntry entry =
          result.stream()
              .filter(e -> e.siteId.equals(siteId))
              .findFirst()
              .orElseGet(
                  () -> {
                    OverviewEntry newEntry =
                        new OverviewEntry(
                            siteId,
                            new HashMap<>(),
                            new MutableInt(),
                            new MutableInt(),
                            new MutableInt());
                    result.add(newEntry);
                    return newEntry;
                  });

      entry.dlStatusCount().put(page.value2(), page.value1());

      int loginRequired = page.value4();
      int soft500 = page.value5();
      int allExceptions = page.value6();
      if ((loginRequired != 0 || soft500 != 0 || allExceptions != 0)) {
        if (!page.value2().equals(DlStatus.Parse)) {
          throw new IllegalStateException(
              "Got exception values when DlStatus is not Parse" + System.lineSeparator() + page);
        }
        entry.parseLoginRequired().setValue(loginRequired);
        entry.parseSoft500().setValue(soft500);
        entry.parseOtherException().setValue(allExceptions - loginRequired - soft500);
      }
    }

    /*
    TODO: Fix annoying "Unchecked generics array creation for varargs parameter" without making a
    redundant list that chainedComparator just grabs the array from. Then remove @SuppressWarnings
    */
    result.sort(
        ComparatorUtils.chainedComparator(
            (o1, o2) ->
                o2.dlStatusCount().getOrDefault(DlStatus.Parse, 0)
                    - o1.dlStatusCount().getOrDefault(DlStatus.Parse, 0),
            (o1, o2) ->
                o2.dlStatusCount().getOrDefault(DlStatus.Download, 0)
                    - o1.dlStatusCount().getOrDefault(DlStatus.Download, 0),
            (o1, o2) ->
                o2.dlStatusCount().getOrDefault(DlStatus.Queued, 0)
                    - o1.dlStatusCount().getOrDefault(DlStatus.Queued, 0),
            (o1, o2) ->
                o2.dlStatusCount().getOrDefault(DlStatus.Done, 0)
                    - o1.dlStatusCount().getOrDefault(DlStatus.Done, 0)));

    return result;
  }

  public List<PagesRecord> getOverviewPageHistory(UUID id) {
    List<PagesRecord> results = new ArrayList<>();
    UUID nextId = id;
    for (int i = 0; i < Integer.MAX_VALUE; i++) {
      PagesRecord record =
          context
              .select()
              .from(PAGES)
              .where(PAGES.PAGEID.eq(nextId))
              .fetchOneInto(PagesRecord.class);
      if (record.getPageid() == null) {
        if (i == 0) {
          throw new RuntimeException("ID not found " + id);
        } else {
          break;
        }
      }
      results.add(record);

      nextId = record.getSourcepageid();
      if (nextId == null) {
        break;
      }
    }
    return results;
  }

  public List<PagesRecord> getOverviewErrors() {
    return context
        .select()
        .from(PAGES)
        .where(
            PAGES.EXCEPTION.isNotNull(),
            PAGES.EXCEPTION.notLike("%LoginRequired%"),
            PAGES.EXCEPTION.notLike("%Soft500Exception%"))
        .orderBy(PAGES.PAGEUPDATED)
        .fetchInto(PagesRecord.class);
  }

  // **************************** Utils ******************

  public Result<SitesRecord> getSites(Condition... conditions) {
    return context.select().from(SITES).where(conditions).fetchInto(SITES);
  }

  public List<PagesRecord> getPages(Condition... conditions) {
    return getPages(List.of(), conditions);
  }

  public List<PagesRecord> getPages(
      Collection<TableField<PagesRecord, ?>> field, Condition... conditions) {
    return context.select(field).from(PAGES).where(conditions).fetchInto(PagesRecord.class);
  }

  public List<URI> getPageUrlsOnly(Condition... conditions) {
    return context.select(PAGES.PAGEURL).from(PAGES).where(conditions).fetch(PAGES.PAGEURL);
  }

  public List<UUID> getPageIdsOnly(Condition... conditions) {
    return context.select(PAGES.PAGEID).from(PAGES).where(conditions).fetch(PAGES.PAGEID);
  }

  public Result<Record> getPageUrls(Condition... conditions) {
    var query =
        context
            .select(PAGES.PAGEID, PAGES.PAGEURL, PAGES.PAGETYPE, SITES.SITEURL, SITES.FORUMTYPE)
            .from(PAGES)
            .innerJoin(SITES)
            .on(PAGES.SITEID.eq(SITES.SITEID))
            .where(conditions);
    return (Result<Record>) (Object) query.fetch();
  }

  public void setPageUrl(UUID pageId, String pageUrl) {
    Query query =
        context
            .update(PAGES)
            .set(PAGES.PAGEURL, Utils.toURI(pageUrl))
            .where(PAGES.PAGEID.eq(pageId));

    executeOneRow(query);
  }

  public record ValidationRecord(UUID pageId, URI url, boolean isRedirect) {}

  public List<ValidationRecord> getPageByUrl(List<String> url) {
    List<ValidationRecord> result = new ArrayList<>();
    for (var obj :
        context.select(PAGES.PAGEID, PAGES.PAGEURL).from(PAGES).where(PAGES.PAGEURL.in(url))) {
      result.add(new ValidationRecord(obj.value1(), obj.value2(), false));
    }
    for (var obj :
        context
            .select(PAGEREDIRECTS.PAGEID, PAGEREDIRECTS.REDIRECTURL)
            .from(PAGEREDIRECTS)
            .where(PAGEREDIRECTS.REDIRECTURL.in(url), PAGEREDIRECTS.INDEX.eq((byte) 0))) {
      result.add(new ValidationRecord(obj.value1(), obj.value2(), true));
    }
    return result;
  }

  public PagesRecord getPage(UUID pageId) {
    List<PagesRecord> pages = getPages(PAGES.PAGEID.eq(pageId));
    if (pages.size() != 1) {
      throw new RuntimeException(
          "Expected 1 row, got " + pages.size() + " for pageId " + pageId + "\r\n" + pages);
    }
    return pages.get(0);
  }

  public List<PageredirectsRecord> getPageRedirects(Condition... conditions) {
    return context.select().from(PAGEREDIRECTS).where(conditions).fetchInto(PAGEREDIRECTS);
  }

  /**
   * New site
   *
   * @return pageId
   */
  public UUID insertSite(URI url, ForumType forumType) {
    UUID id = UUID.randomUUID();
    executeOneRow(
        context
            .insertInto(SITES, SITES.SITEID, SITES.SITEURL, SITES.SITEUPDATED, SITES.FORUMTYPE)
            .values(id, url, LocalDateTime.now(), forumType));
    return id;
  }

  public record InsertPage(UUID sourcePageId, UUID siteId, URI pageUrl, PageType type) {}

  public void insertPagesQueued(List<InsertPage> pages, boolean ignoreDuplicates) {
    /*
    PageParser just grabs every URL, but some may have been processed already

    ON CONFLICT is SQLite specific
    onDuplicateKeyIgnore / INSERT IGNORE is dangerous because URLs are silently(!!) truncated

    So SELECT to filter out duplicates before INSERT

    Also for some reason need toString
    */
    if (true) {
      // lazy fix, idk why the select isn't working
      for (InsertPage page : pages) {
        UUID id = UUID.randomUUID();
        try {
          context
              .insertInto(
                  PAGES,
                  PAGES.PAGEID,
                  PAGES.SITEID,
                  PAGES.PAGEURL,
                  PAGES.PAGETYPE,
                  PAGES.DLSTATUS,
                  PAGES.PAGEUPDATED,
                  PAGES.SOURCEPAGEID)
              .values(
                  id,
                  page.siteId(),
                  page.pageUrl(),
                  page.type(),
                  DlStatus.Queued,
                  LocalDateTime.now(),
                  page.sourcePageId())
              .execute();
        } catch (Exception e) {
          Throwable root = e;
          while (root.getCause() != null) {
            root = root.getCause();
          }

          //noinspection StatementWithEmptyBody
          if (root.getMessage().startsWith("Duplicate entry")
              && root.getMessage().endsWith("for key 'Pages.url'")) {
            // silently ignore...
          } else if (root.getMessage().startsWith("Data too long for column 'pageUrl'")) {
            String pageUrl = page.pageUrl().toString();
            log.warn("+++ pageUrl too long size {} url {}", pageUrl.length(), pageUrl);
          } else {
            throw e;
          }
        }
      }

      return;
    }

    int initSize = pages.size();
    if (ignoreDuplicates) {
      List<String> existingUrls =
          context
              .select(PAGES.PAGEURL)
              .from(PAGES)
              .where(
                  PAGES.PAGEURL.in(
                      pages.stream().map(InsertPage::pageUrl).collect(Collectors.toList())))
              .fetch(e -> e.get(PAGES.PAGEURL).toString());
      pages.removeIf(e -> existingUrls.contains(e.pageUrl().toString()));
    }
    log.info("init size {} new size {} ", initSize, pages.size());

    var query =
        context.insertInto(
            PAGES,
            PAGES.PAGEID,
            PAGES.SITEID,
            PAGES.PAGEURL,
            PAGES.PAGETYPE,
            PAGES.DLSTATUS,
            PAGES.PAGEUPDATED,
            PAGES.SOURCEPAGEID);

    for (InsertPage page : pages) {
      UUID id = UUID.randomUUID();

      query.values(
          id,
          page.siteId(),
          page.pageUrl(),
          page.type(),
          DlStatus.Queued,
          LocalDateTime.now(),
          page.sourcePageId());
    }

    query.execute();
  }

  public void insertPageRedirects(Collection<PageredirectsRecord> redirects) {
    var query =
        context.insertInto(
            PAGEREDIRECTS, PAGEREDIRECTS.PAGEID, PAGEREDIRECTS.REDIRECTURL, PAGEREDIRECTS.INDEX);
    for (PageredirectsRecord redirect : redirects) {
      query.values(redirect.getPageid(), redirect.getRedirecturl(), redirect.getIndex());
    }
    query.execute();
  }

  /** Change page status */
  public void setPageStatus(Collection<UUID> pageIds, DlStatus status) {
    if (pageIds.isEmpty()) {
      throw new IllegalArgumentException("No pages provided");
    }
    Query query =
        context
            .update(PAGES)
            .set(PAGES.DLSTATUS, status)
            // do not update status for anything but download to parse
            // .set(PAGES.PAGEUPDATED, LocalDateTime.now())
            .where(PAGES.PAGEID.in(pageIds));

    executeRows(query, pageIds.size());
  }

  public void setPageException(UUID pageId, String exception) {
    Query query =
        context.update(PAGES).set(PAGES.EXCEPTION, exception).where(PAGES.PAGEID.eq(pageId));

    executeOneRow(query);
  }

  public record UpdatePageException(UUID pageId, String exception) {}

  public void setPageException(List<UpdatePageException> updates) {
    if (updates.isEmpty()) {
      throw new IllegalArgumentException("No updates provided");
    }
    var batchQuery =
        context.batch(
            context
                .update(PAGES)
                .set(PAGES.EXCEPTION, DSL.param(PAGES.EXCEPTION))
                .where(PAGES.PAGEID.eq(DSL.param(PAGES.PAGEID))));
    for (UpdatePageException entry : updates) {
      batchQuery.bind(entry.exception(), entry.pageId());
    }
    int[] results = batchQuery.execute();

    boolean errors = false;
    int i = 0;
    for (int result : results) {
      if (result != 1) {
        log.error("set {} rows expected 1 row for {}", result, updates.get(i));
        errors = true;
      }
      i++;
    }
    if (errors) {
      throw new RuntimeException("See logged errors");
    }
  }

  public void setPageExceptionNull(UUID pageId) {
    Query query =
        context.update(PAGES).set(PAGES.EXCEPTION, (String) null).where(PAGES.PAGEID.eq(pageId));

    executeOneRow(query);
  }

  public void setPageURL(UUID pageId, URI url) {
    Query query = context.update(PAGES).set(PAGES.PAGEURL, url).where(PAGES.PAGEID.eq(pageId));

    executeOneRow(query);
  }

  public void deletePage(UUID pageId) {
    Query query = context.delete(PAGES).where(PAGES.PAGEID.eq(pageId));

    executeOneRow(query);
  }

  public void insertDataset(List<DatasetRecord> records) {
    context.batchInsert(records).execute();
  }

  public DatasetRecord getDataset(UUID pageId) {
    return context
        .select()
        .from(Dataset.DATASET)
        .where(Dataset.DATASET.PAGEID.eq(pageId))
        .fetchOneInto(DatasetRecord.class);
  }

  // ******************************

  public int _debug_resetPagesToQueued(List<UUID> pageId) {
    //    var query = context
    //        .update(PAGES)
    //        .set(PAGES.EXCEPTION, (String) null)
    //        .set(PAGES.DLSTATUS, DlStatus.Queued)
    //        .set(PAGES.DLSTATUSCODE, (Integer) null)
    //        .where(PAGES.PAGEID.in(pageId));
    ////    log.info("query " + query);
    //    return query.execute();
    throw new UnsupportedOperationException();
  }

  public int _debug_resetPagesToParse(List<UUID> siteId) {
    //    return context
    //        .update(PAGES)
    //        .set(PAGES.EXCEPTION, (String) null)
    //        .set(PAGES.DLSTATUS, DlStatus.Parse)
    //        .where(PAGES.SITEID.in(siteId))
    //        .execute();
    throw new UnsupportedOperationException();
  }

  public int _debug_setPageStatusCode(List<UUID> pageId, int code) {
    //    return context
    //        .update(PAGES)
    //        .set(PAGES.DLSTATUSCODE, 0)
    //        .where(PAGES.PAGEID.in(pageId))
    //        .execute();
    throw new UnsupportedOperationException();
  }

  // *******************************

  private static void executeOneRow(Query query) {
    executeRows(query, 1);
  }

  private static void executeRows(Query query, int expectedRows) {
    int numRowsInserted = query.execute();
    if (numRowsInserted != expectedRows) {
      throw new RuntimeException(
          "Expected result of "
              + expectedRows
              + " row, got "
              + numRowsInserted
              + ". Query "
              + query);
    }
  }

  public void close() throws Exception {
    closableConnection.close();
  }
}
