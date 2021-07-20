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
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record7;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.conf.ThrowExceptions;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.server.ServerConfig;
import sh.xana.forum.server.db.tables.records.PageredirectsRecord;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.db.tables.records.SitesRecord;

public class DatabaseStorage {
  private static final Logger log = LoggerFactory.getLogger(DatabaseStorage.class);

  private final DSLContext context;

  public DatabaseStorage(ServerConfig config) {
    log.info("Connecting to database");

    // Hide giant logo it writes to logs on first load
    System.setProperty("org.jooq.no-logo", "true");

    // need to load this since at runtime using cp not modules, DriverManager isn't finding it
    try {
      Class.forName("org.mariadb.jdbc.Driver");
    } catch (Exception e) {
      throw new RuntimeException("failed to load class", e);
    }

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

    context =
        DSL.using(
            dataSource,
            SQLDialect.MARIADB,
            new Settings().withThrowExceptions(ThrowExceptions.THROW_ALL));
    log.info("Connected to database");
  }

  /** Stage: init client */
  public List<String> getScraperDomainsIPC() {
    return context.selectDistinct(PAGES.DOMAIN).from(PAGES).fetch(PAGES.DOMAIN);
  }

  /** Stage: Load pages to be downloaded by Scraper */
  public List<ScraperDownload> movePageQueuedToDownloadIPC(String domain, int size) {
    // TODO: VERY SUSPICIOUS RACE. Seems multiple clients can request the page,
    List<ScraperDownload> pages =
        context
            .select()
            .from(PAGES)
            .where(PAGES.DOMAIN.eq(domain))
            .and(PAGES.DLSTATUS.eq(DlStatus.Queued))
            .and(PAGES.EXCEPTION.isNull())
            // Select ForumList first, which alphabetically is before TopicPage
            .orderBy(PAGES.PAGETYPE)
            .limit(size)
            .fetch()
            .map(r -> new ScraperDownload(r.get(PAGES.PAGEID), r.get(PAGES.PAGEURL)));

    setPageStatus(
        pages.stream().map(ScraperDownload::pageId).collect(Collectors.toList()),
        DlStatus.Download);

    return pages;
  }

  /** Stage: Page is finished downloading */
  public void movePageDownloadToParse(UUID pageId, int statusCode) {
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
  public Result<Record7<UUID, UUID, URI, PageType, String, URI, ForumType>> getParserPages() {
    var query =
        context
            .select(
                PAGES.PAGEID,
                PAGES.SITEID,
                PAGES.PAGEURL,
                PAGES.PAGETYPE,
                PAGES.DOMAIN,
                SITES.SITEURL,
                SITES.FORUMTYPE)
            .from(PAGES)
            .innerJoin(SITES)
            .on(PAGES.SITEID.eq(SITES.SITEID))
            .where(
                PAGES.DLSTATUS.eq(DlStatus.Parse),
                PAGES.EXCEPTION.isNull(),
                PAGES.PAGEID.eq(UUID.fromString("58cf6358-e896-4372-8026-327d3ab0e145")))
            .limit(1000);
    log.info(query.toString());
    return query.fetch();
  }

  /** Stage: reporting monitor */
  public List<OverviewEntry> getOverviewSites() {
    var query =
        context
            .select(DSL.count(PAGES.PAGEID), PAGES.DLSTATUS, PAGES.SITEID, SITES.SITEURL)
            .from(PAGES)
            .innerJoin(SITES)
            .on(PAGES.SITEID.eq(SITES.SITEID))
            // SITEID is redundant but mysql doesn't like the free floating column
            .groupBy(PAGES.DLSTATUS, PAGES.DOMAIN, PAGES.SITEID);
    log.info("QUERY " + query);
    var pages = query.fetch();
    List<OverviewEntry> result = new ArrayList<>();
    while (!pages.isEmpty()) {
      Map<DlStatus, Integer> counter = new HashMap<>();

      var pageIterator = pages.iterator();
      URI siteUrl = null;
      UUID siteId = null;
      do {
        var curPage = pageIterator.next();
        if (siteId == null) {
          siteId = curPage.get(PAGES.SITEID);
          siteUrl = curPage.get(SITES.SITEURL);
        } else if (!curPage.get(PAGES.SITEID).equals(siteId)) {
          continue;
        }
        counter.put(curPage.get(PAGES.DLSTATUS), curPage.value1());
        pageIterator.remove();
      } while (pageIterator.hasNext());

      result.add(new OverviewEntry(siteId, siteUrl, counter));
    }
    return result;
  }

  public List<PagesRecord> getOverviewPage(UUID id) {
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

  public List<SitesRecord> getSites(Condition... conditions) {
    return context.select().from(SITES).where(conditions).fetchInto(SitesRecord.class);
  }

  public SitesRecord getSite(UUID siteId) {
    List<SitesRecord> sites = getSites(SITES.SITEID.eq(siteId));
    if (sites.size() != 1) {
      throw new RuntimeException(
          "Expected 1 row, got " + sites.size() + " for siteId " + siteId + "\r\n" + sites);
    }
    return sites.get(0);
  }

  public List<PagesRecord> getPages(Condition... conditions) {
    return context.select().from(PAGES).where(conditions).fetchInto(PagesRecord.class);
  }

  public record PageId(UUID pageId, URI siteUrl) {}
  ;

  public List<PageId> getPagesIds(Condition... conditions) {
    return context
        .select(PAGES.PAGEID, SITES.SITEURL)
        .from(PAGES)
        .innerJoin(SITES)
        .on(PAGES.SITEID.eq(SITES.SITEID))
        .where(conditions)
        .fetch()
        .map(e -> new PageId(e.get(PAGES.PAGEID), e.get(SITES.SITEURL)));
  }

  public record PageUrl(URI pageUrl, URI siteUrl, ForumType forumType) {}

  public List<PageUrl> getPageUrls(Condition... conditions) {
    var query =
        context
            .select(PAGES.PAGEURL, SITES.SITEURL, SITES.FORUMTYPE)
            .from(PAGES)
            .innerJoin(SITES)
            .on(PAGES.SITEID.eq(SITES.SITEID))
            .where(conditions);
    log.info(query.toString());
    return query.fetch().map(e -> new PageUrl(e.component1(), e.component2(), e.component3()));
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

  public URI getPageDomain(UUID pageId) {
    return context
        .select(SITES.SITEURL)
        .from(PAGES)
        .innerJoin(SITES)
        .on(PAGES.SITEID.eq(SITES.SITEID))
        .where(PAGES.PAGEID.eq(pageId))
        .fetchOne(SITES.SITEURL);
  }

  public PagesRecord getPage(UUID pageId) {
    List<PagesRecord> pages = getPages(PAGES.PAGEID.eq(pageId));
    if (pages.size() != 1) {
      throw new RuntimeException(
          "Expected 1 row, got " + pages.size() + " for pageId " + pageId + "\r\n" + pages);
    }
    return pages.get(0);
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
    */
    if (ignoreDuplicates) {
      List<URI> existingUrls =
          context
              .select(PAGES.PAGEURL)
              .from(PAGES)
              .where(
                  PAGES.PAGEURL.in(
                      pages.stream().map(InsertPage::pageUrl).collect(Collectors.toList())))
              .fetch(PAGES.PAGEURL);
      pages =
          pages.stream()
              .filter(e -> !existingUrls.contains(e.pageUrl()))
              .collect(Collectors.toList());
    }

    var query =
        context.insertInto(
            PAGES,
            PAGES.PAGEID,
            PAGES.SITEID,
            PAGES.PAGEURL,
            PAGES.PAGETYPE,
            PAGES.DLSTATUS,
            PAGES.PAGEUPDATED,
            PAGES.DOMAIN,
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
          page.pageUrl().getHost(),
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

  public record OverviewEntry(UUID siteId, URI siteUrl, Map<DlStatus, Integer> dlStatusCount) {}
}
