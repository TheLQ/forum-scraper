package sh.xana.forum.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.time.StopWatch;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.AuditorExecutor;
import sh.xana.forum.common.PerformanceCounter;
import sh.xana.forum.common.ipc.Subpage;
import sh.xana.forum.server.AuditorFileServer.Client;
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
  private final HashSet<String> errors = new HashSet<>();
  private final AuditorExecutor auditorPool = new AuditorExecutor(log);

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

  public void singleFile(String[] args) throws IOException {
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

    cache.loadPageUrls(List.of(page.siteUrl().toString()));

    List<Subpage> subpages = runSpider(new LoadedPage(page, Files.readAllBytes(path)));

    log.info("error size" + errors.size());
    for (String error : errors) {
      log.info("Error " + error);
    }
    for (Subpage subpage : subpages) {
      log.info("Subpage {} {}", subpage.pageType(), subpage.link());
    }
  }

  public void massAudit() throws InterruptedException, ExecutionException, IOException {
    List<String> domains =
        List.of(
            // xf_dir - validated
            // "kiwifarms.net"
            // "forums.tomshardware.com"
            //
            // xf_dir / XenForo_F - validated (though has lots of old site redirects)
            "www.avsforum.com",
            "www.b15sentra.net",
            "www.b15u.com",
            "www.clubwrx.net",
            "www.iwsti.com",
            "www.kboards.com",
            "www.nissancubelife.com",
            "www.nissanforums.com",
            "www.subaruforester.org",
            "www.subaruxvforum.com",
            "www.wrxtuners.com"
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
            );
    log.info("domains {}", domains);
    List<UUID> domainSiteIds = dbStorage.siteCache.mapByDomains(domains, SitesRecord::getSiteid);
    Predicate<ParserPage> pageIsDomain = e -> domainSiteIds.contains(e.siteId());

    List<ParserPage> pages = cache.loadPagesParallel(pageIsDomain);

    // preload cachedUrls
    StopWatch timer = new StopWatch();
    timer.start();
    int urlCount = cache.loadPageUrls(dbStorage.siteCache.siteUrlsByDomains(domains));
    log.info("Loaded {} urls in {}", urlCount, timer.formatTime());

    log.info("start");
    PerformanceCounter counter = new PerformanceCounter(log, 1000);
    BlockingQueue<LoadedPage> toSpiderQueue = new ArrayBlockingQueue<>(2000);

    ThreadLocal<Client> clientLocal = new ThreadLocal<>();
    Iterator<ParserPage> parserPageIterator = pages.iterator();
    auditorPool.startConsumerForSupplierToSize(
        "reader",
        16,
        parserPageIterator::next,
        pages.size(),
        e -> {
          Client client = clientLocal.get();
          if (client == null) {
            client = new Client();
            clientLocal.set(client);
          }
          // String s = new String(client.request(e.pageId()));
          byte[] s = client.request(e.pageId());
          toSpiderQueue.put(new LoadedPage(e, s));
        });

    auditorPool.startConsumerForSupplierToSize(
        "spider",
        16,
        toSpiderQueue::take,
        pages.size(),
        e -> {
          counter.incrementAndLog(pages.size(), toSpiderQueue.size());
          runSpider(e);
        });

    log.info("waiting...");
    auditorPool.waitForAllThreads();
    log.info("DONE");

    Files.write(Path.of("errors.log"), errors);
    log.info("Wrote {} errors", errors.size());
  }

  public record LoadedPage(ParserPage page, byte[] data) {}

  private List<Subpage> runSpider(LoadedPage loadedPage) {
    try {
      if (loadedPage.data().length == 0) {
        throw new RuntimeException("BLANK PAGE");
      }
      ParserPage page = loadedPage.page();

      // Document doc = spider.loadPage(loadedPage.data(), page.siteUrl().toString());
      Document doc =
          Jsoup.parse(new ByteArrayInputStream(loadedPage.data()), null, page.siteUrl().toString());
      Stream<Subpage> subpagesStream = spider.spiderPage(page, doc);
      //            subpagesStream.collect(Collectors.toList());
      return getErrors(subpagesStream, page.siteUrl().toString(), page.pageId());
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

  private List<Subpage> getErrors(Stream<Subpage> subpagesStream, String siteUrl, UUID pageId) {
    List<Subpage> subUrls = subpagesStream.collect(Collectors.toList());
    if (subUrls.isEmpty()) {
      errors.add(pageId + " No pages matched");
      return List.of();
    }

    Collection<String> dbUrls = cache.getPageUrls(siteUrl);
    for (Subpage subUrl : subUrls) {
      String link = subUrl.link();
      if (!dbUrls.contains(link)) {
        errors.add("db missing url " + link);
      }
    }
    return subUrls;
  }
}
