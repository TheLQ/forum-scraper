package sh.xana.forum.server;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.PerformanceCounter;
import sh.xana.forum.common.ipc.Subpage;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.records.SitesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.LoginRequiredException;
import sh.xana.forum.server.parser.ParserException;
import sh.xana.forum.server.spider.Spider;

/** Audits complete file cache */
public class Auditor {
  private static final Logger log = LoggerFactory.getLogger(Auditor.class);
  private final DatabaseStorage dbStorage;
  private final ServerConfig config;
  private final Spider spider;
  private final AuditorCache cache;
  private final ConcurrentSkipListSet<String> errors = new ConcurrentSkipListSet<>();
  private final PerformanceCounter counter = new PerformanceCounter(log, 10);
  private long totalSize;

  public static void main(String[] args) throws Exception {
    Auditor audit = new Auditor();

    if (args.length == 0) {
      throw new RuntimeException("no args");
    } else if (args[0].equals("file")) {
      audit.singleFile(args);
    } else {
      audit.massAudit();
    }
  }

  public Auditor() throws IOException {
    config = new ServerConfig();
    dbStorage = new DatabaseStorage(config);

    spider = new Spider();
    cache = new AuditorCache(config);
  }

  public void singleFile(String[] args) {
    Path path = Path.of(args[1]);

    ParserPage page;
    if (Files.exists(path)) {
      if (args.length != 5) {
        System.out.println("file <path> <baseUrl> <forumType> <pageType>");
        return;
      }
      page =
          new ParserPage(
              UUID.fromString("00000000-0000-0000-0000-000000000000"),
              URI.create("http://google.com/"),
              PageType.valueOf(args[4]),
              200,
              UUID.fromString("00000000-0000-0000-0000-000000000000"),
              URI.create(args[2]),
              ForumType.valueOf(args[3]));
    } else {
      UUID pageId = UUID.fromString(args[1]);
      page = dbStorage.getParserPages(true, Pages.PAGES.PAGEID.eq(pageId)).get(0);
      Path oldPath = path;
      path = config.getPagePath(pageId);
      log.info("Path {} does not exist, loaded {}", oldPath, path);
      log.info("Page URL " + page.pageUri());
    }

    List<String> errors = new ArrayList<>();
//    List<Subpage> subpages = runParser(new QueueEntry(page), errors);
//
//    log.info("error size" + errors.size());
//    for (String error : errors) {
//      log.info("Error " + error);
//    }
//    for (Subpage subpage : subpages) {
//      log.info("Subpage {} {}", subpage.pageType(), subpage.link());
//    }
//    return;
  }

  public void massAudit() throws InterruptedException, ExecutionException {
      log.info("query start");

      List<String> domains =
          List.of(
              // Validated with XenForo_F @ 8d57e93464511ff6b2d51c7c01949bea40720492
              // "Fix Java Warnings"
              // Only errors are on the home page
              // "www.avsforum.com",
              // "www.b15sentra.net",
              // "www.b15u.com",
              // "www.clubwrx.net",
              // "www.iwsti.com",
              // "www.kboards.com",
              // "www.nissancubelife.com",
              // "www.nissanforums.com",
              // "www.subaruforester.org",
              // "www.subaruxvforum.com",
              // "www.wrxtuners.com"
              //
              // vBulletin_IB
              // "www.corvetteforum.com", "www.rx7club.com", "www.rx8club.com"
              //
              // vBulletin_Url1
              // "forum.miata.net"
              // vBulletin_Url2
              // "forums.nasioc.com"
              // "xlforum.net"
              //
              // Forkboard
              // "www.sr20-forum.com"
              //
              // other
              "kiwifarms.net");
    log.info("domains {}", domains);
    List<UUID> domainSiteIds = dbStorage.siteCache.mapByDomains(domains, SitesRecord::getSiteid);
    Predicate<ParserPage> pageIsDomain = e -> domainSiteIds.contains(e.siteId());

    var result = cache.stream()
        .filter(pageIsDomain)
        .parallel()
        .map(this::runParser)
        .collect(Collectors.toList());

    // calc size
//    long totalSizeStartTime = System.currentTimeMillis();
//    totalSize = cache.stream().filter(pageIsDomain).count();
//    log.info("Found size {} in {}", totalSize, DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - totalSizeStartTime));
//
//    // preload cachedUrls
//    cache.getPageUrls();
//
//    log.info("start");
//
//    ExecutorService pool = Executors.newFixedThreadPool(16);
//    List<? extends Future<?>> collect = cache.stream().filter(pageIsDomain)
//        .map(e -> (Runnable) () -> runParser(e)).map(pool::submit).collect(Collectors.toList());
//
//    for (Future<?> future : collect) {
//      future.get();
//    }
  }

  private List<Subpage> runParser(ParserPage page) {
    try {
      counter.incrementAndLog(totalSize);

      Document doc = spider.loadPage(config.getPagePath(page.pageId()), page.siteUrl().toString());
      Stream<Subpage> subpages = spider.spiderPage(page, doc);
      getErrors(subpages);
    } catch (LoginRequiredException e) {
      // nothing
    } catch (ParserException e) {
      if (e.getCause() != null) {
        log.error("FAILED TO PARSE", e);
      } else {
        log.error(e.getClass().getCanonicalName() + " " + e.getMessage());
      }
    } catch (Exception e) {
      log.error("FAILED TO PARSE", e);
    }
    return List.of();
  }

  private List<Subpage> getErrors(Stream<Subpage> subpagesStream) {
    List<Subpage> subpages = subpagesStream.collect(Collectors.toList());
//    List<ValidationRecord> pages =
//        dbStorage.getPageByUrl(subpages.stream().map(Subpage::link).collect(Collectors.toList()));

    for (String url : cache.getPageUrls()) {
      if (subpages.stream()
          .noneMatch(parserEntry -> parserEntry.link().equals(url))) {
        errors.add("parser missing url " + url);
      }
    }
    for (Subpage subpage : subpages) {
      if (cache.getPageUrls().stream().noneMatch(page -> page.equals(subpage.link()))) {
        errors.add("db missing url " + subpage.link());
      }
    }
    return subpages;
  }
}
