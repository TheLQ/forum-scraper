package sh.xana.forum.server.dbutil;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jooq.CloseableDSLContext;
import org.jooq.Condition;
import org.jooq.Query;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.client.Scraper;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.DownloadNodeEntry;
import sh.xana.forum.common.ipc.DownloadRequest;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.Sites;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.db.tables.records.SitesRecord;

public class DatabaseStorage {
  private static final Logger log = LoggerFactory.getLogger(DatabaseStorage.class);

  private final CloseableDSLContext context = DSL.using("jdbc:sqlite:sample.db");

  /** Stage: init client */
  public String getScraperDomainsJSON() {
    var pages =
        context.selectDistinct(Pages.PAGES.DOMAIN).from(Pages.PAGES).fetchInto(PagesRecord.class);

    List<DownloadNodeEntry> resultList = new ArrayList<>(pages.size());
    for (PagesRecord page : pages) {
      resultList.add(new DownloadNodeEntry(page.getDomain()));
    }

    try {
      return Utils.jsonMapper.writeValueAsString(resultList);
    } catch (Exception e) {
      throw new RuntimeException("Failed to JSON", e);
    }
  }

  /** Stage: Load pages to be downloaded by Scraper */
  public String movePageQueuedToDownloadJSON(String domain) {
    List<DownloadRequest> pages =
        context
            .select()
            .from(Pages.PAGES)
            .where(Pages.PAGES.DOMAIN.eq(domain))
            .and(Pages.PAGES.DLSTATUS.eq(DlStatus.Queued.toString()))
            .and(Pages.PAGES.EXCEPTION.isNull())
            .limit(Scraper.URL_QUEUE_REFILL_SIZE)
            .fetch()
            .map(
                r ->
                    new DownloadRequest(
                        Utils.uuidFromBytes(r.get(Pages.PAGES.ID)), r.get(Pages.PAGES.URL)));

    setPageStatus(
        pages.stream().map(DownloadRequest::id).collect(Collectors.toList()), DlStatus.Download);

    try {
      return Utils.jsonMapper.writeValueAsString(pages);
    } catch (Exception e) {
      throw new RuntimeException("Stuff", e);
    }
  }

  /** Stage: Page is finished downloading */
  public void movePageDownloadToParse(UUID pageId, int statusCode) {
    Query query =
        context
            .update(Pages.PAGES)
            .set(Pages.PAGES.DLSTATUS, DlStatus.Parse.toString())
            .set(Pages.PAGES.DLSTATUSCODE, statusCode)
            .set(Pages.PAGES.UPDATED, LocalDateTime.now())
            .where(Pages.PAGES.ID.in(Utils.uuidAsBytes(pageId)));

    executeOneRow(query);
  }

  /** Stage: Load pages for Parser */
  public List<PagesRecord> getParserPages() {
    return getPages(
        Pages.PAGES.DLSTATUS.eq(DlStatus.Download.toString()), Pages.PAGES.EXCEPTION.isNull());
  }

  /** Stage: reporting monitor */
  public List<OverviewEntry> getOverviewSites() {
    // TODO: SLOW!!! But easy to write version
    List<SitesRecord> sites = getSites();
    Map<UUID, OverviewEntry> result = new HashMap<>();
    for (PagesRecord page : getPages()) {
      result.compute(
          Utils.uuidFromBytes(page.getSiteid()),
          (k, v) -> {
            if (v == null) {
              SitesRecord site =
                  sites.stream()
                      .filter(r -> Arrays.equals(r.getId(), page.getSiteid()))
                      .findFirst()
                      .orElseThrow();
              v =
                  new OverviewEntry(
                      k,
                      site.getUrl(),
                      new AtomicInteger(),
                      new AtomicInteger(),
                      new AtomicInteger());
            }
            switch (DlStatus.valueOf(page.getDlstatus())) {
              case Done -> v.totalDone.incrementAndGet();
              case Download -> v.totalDownload.incrementAndGet();
              case Queued -> v.totalQueued.incrementAndGet();
              default -> throw new RuntimeException("unknown status");
            }
            return v;
          });
    }
    return List.copyOf(result.values());
  }

  /****************************** Utils ******************/

  /** @return */
  private List<SitesRecord> getSites() {
    return context.select().from(Sites.SITES).fetchInto(SitesRecord.class);
  }

  private List<PagesRecord> getPages(Condition... conditions) {
    return context.select().from(Pages.PAGES).where(conditions).fetchInto(PagesRecord.class);
  }

  /**
   * New site
   *
   * @return id
   */
  public UUID insertSite(String url) {
    UUID id = UUID.randomUUID();
    executeOneRow(
        context
            .insertInto(Sites.SITES, Sites.SITES.ID, Sites.SITES.URL, Sites.SITES.UPDATED)
            .values(Utils.uuidAsBytes(id), url, LocalDateTime.now()));
    return id;
  }

  /**
   * New page to be queued for later download
   *
   * @return id
   */
  public List<UUID> insertPageQueued(UUID siteId, List<String> urls, PageType type, UUID sourceId) {
    List<UUID> result = new ArrayList<>(urls.size());

    var query =
        context.insertInto(
            Pages.PAGES,
            Pages.PAGES.ID,
            Pages.PAGES.SITEID,
            Pages.PAGES.URL,
            Pages.PAGES.PAGETYPE,
            Pages.PAGES.DLSTATUS,
            Pages.PAGES.UPDATED,
            Pages.PAGES.DOMAIN,
            Pages.PAGES.SOURCEID);

    for (String entry : urls) {
      UUID id = UUID.randomUUID();
      result.add(id);

      String host;
      try {
        host = new URL(entry).getHost();
      } catch (Exception e) {
        throw new RuntimeException("URL", e);
      }

      query =
          query.values(
              Utils.uuidAsBytes(id),
              Utils.uuidAsBytes(siteId),
              entry,
              type.toString(),
              DlStatus.Queued.toString(),
              LocalDateTime.now(),
              host,
              Utils.uuidAsBytes(sourceId));
    }

    executeRows(query, urls.size());

    return result;
  }

  /** Change page status */
  public void setPageStatus(Collection<UUID> pageIds, DlStatus status) {
    Collection<byte[]> uuidBytes =
        pageIds.stream().map(Utils::uuidAsBytes).collect(Collectors.toList());
    Query query =
        context
            .update(Pages.PAGES)
            .set(Pages.PAGES.DLSTATUS, status.toString())
            .set(Pages.PAGES.UPDATED, LocalDateTime.now())
            .where(Pages.PAGES.ID.in(uuidBytes));

    executeRows(query, pageIds.size());
  }

  public void setPageException(UUID pageId, String exception) {
    Query query =
        context
            .update(Pages.PAGES)
            .set(Pages.PAGES.EXCEPTION, exception)
            .where(Pages.PAGES.ID.in(Utils.uuidAsBytes(pageId)));

    executeOneRow(query);
  }

  /********************************/

  private static void executeOneRow(Query query) {
    executeRows(query, 1);
  }

  private static void executeRows(Query query, int expectedRows) {
    int numRowsInserted = query.execute();
    if (numRowsInserted != expectedRows) {
      throw new RuntimeException(
          "Expected result of " + expectedRows + " row, got " + numRowsInserted);
    }
  }

  /** Pages.DlStatus column */
  public enum DlStatus {
    Queued,
    Download,
    Parse,
    Done,
    Supersede,
    Error,
  }

  /** Pages.PageType column */
  public enum PageType {
    ForumList,
    TopicPage,
  }

  public record OverviewEntry(
      UUID siteId,
      String siteUrl,
      AtomicInteger totalQueued,
      AtomicInteger totalDownload,
      AtomicInteger totalDone) {}
}
