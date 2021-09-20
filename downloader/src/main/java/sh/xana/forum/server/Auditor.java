package sh.xana.forum.server;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.AuditorExecutor;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.Subpage;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.records.SitesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.ValidationRecord;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.LoginRequiredException;
import sh.xana.forum.server.parser.PageParser;
import sh.xana.forum.server.parser.ParserException;

/** Audits complete file cache */
public class Auditor {
  private static final Logger log = LoggerFactory.getLogger(Auditor.class);
  private static DatabaseStorage dbStorage;
  private static PageParser parser;
  private static ServerConfig config;

  public static void main(String[] args) throws Exception {
    config = new ServerConfig();
    dbStorage = new DatabaseStorage(config);
    parser = new PageParser();

    if (args.length == 0) {
      throw new RuntimeException("no args");
    } else if (args[0].equals("file")) {
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
        log.info("file exists for {} is {}", path, Files.exists(path));
        UUID pageId = UUID.fromString(args[1]);
        page = dbStorage.getParserPages(true, Pages.PAGES.PAGEID.eq(pageId)).get(0);
        path = config.getPagePath(pageId);
      }

      ParserResult result = parser.parsePage(Files.readAllBytes(path), page);
      List<String> errors = new ArrayList<>();
      getErrors(page.pageId(), result, errors);
      log.info("error size" + errors.size());
      for (String error : errors) {
        log.info("Error " + error);
      }
      log.info(
          "result {}",
          Utils.jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
      return;
    } else if (args[0].equals("delete")) {
      log.error("================== DELETE");
      log.error("================== DELETE");
      log.error("================== DELETE... 5 sec");
      Thread.sleep(5000);

      log.info("Fetching page ids...");
      List<UUID> pagesIds =
          dbStorage.getParserPages(false, Pages.PAGES.DLSTATUS.eq(DlStatus.Parse)).stream()
              .map(ParserPage::pageId)
              .collect(Collectors.toList());
      log.info("Fetched {} page ids", pagesIds.size());

      log.info("Listing directory");
      AtomicInteger pageCounter = new AtomicInteger();
      AtomicInteger deleteCounter = new AtomicInteger();
      Files.walk(Path.of(config.get(config.ARG_FILE_CACHE)), 1)
          .forEach(
              path -> {
                String rawId = path.getFileName().toString();
                if (!rawId.endsWith(".response")) {
                  return;
                }
                if (pageCounter.incrementAndGet() % 1000 == 0) {
                  log.info("Processed " + pageCounter);
                }

                rawId = rawId.substring(0, rawId.indexOf('.'));
                UUID pageId = UUID.fromString(rawId);
                if (!pagesIds.contains(pageId)) {
                  deleteCounter.incrementAndGet();
                  try {
                    Files.delete(path);
                  } catch (IOException e) {
                    throw new RuntimeException("fail", e);
                  }
                }
              });
      log.info("Processed {} pages, deleted {}", pageCounter, deleteCounter);

      return;
    }

    Path auditorCache = Path.of("auditorCache.json");
    List<ParserPage> pages;
    if (Files.exists(auditorCache)) {
      log.info("Loading query cache");
      pages =
          Utils.jsonMapper.readValue(
              auditorCache.toFile(), new TypeReference<List<ParserPage>>() {});
    } else {
      log.info("query start");
      pages =
          dbStorage.getParserPages(
              false,
              // Pages.PAGES.EXCEPTION.isNull(),
              // Pages.PAGES.DLSTATUS.eq(DlStatus.Done)
              Pages.PAGES.SITEID.in(
                  dbStorage.siteCache.mapByDomains(
                      List.of(
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
                          // "www.corvetteforum.com"
                          // "www.rx7club.com",
                          // "www.rx8club.com"
                          //
                          // "www.sr20-forum.com"
                          ),
                      SitesRecord::getSiteid)),
              Pages.PAGES.DLSTATUS.in(DlStatus.Parse, DlStatus.Done));
      log.info("writing " + pages.size() + " rows to " + auditorCache);
      Utils.jsonMapper.writeValue(auditorCache.toFile(), pages);
    }

    log.info("start");
    switch (args[0]) {
      case "simple" -> start_simple(pages);
      case "pre" -> start_preopen(pages);
      case "thread" -> start_thread(pages);
      default -> throw new RuntimeException("unknown");
    }
  }

  public static void start_simple(Collection<ParserPage> pages) {
    AtomicInteger counter = new AtomicInteger();
    pages.parallelStream()
        .forEach(
            page -> {
              int idx = counter.incrementAndGet();
              if (idx % 1000 == 0) {
                log.info("{} of {}", idx, pages.size());
              }
              try {
                byte[] data = Files.readAllBytes(config.getPagePath(page.pageId()));
                ParserResult result = parser.parsePage(data, page);
                getErrors(page.pageId(), result, new ArrayList<>());
              } catch (Exception e) {
                log.error("FAIL ON " + page, e);
                System.exit(1);
              }
            });
  }

  public static final class ThreadPoolQueue<T> extends ArrayBlockingQueue<T> {

    public ThreadPoolQueue(int capacity) {
      super(capacity);
    }

    @Override
    public boolean offer(T e) {
      try {
        put(e);
      } catch (InterruptedException e1) {
        Thread.currentThread().interrupt();
        return false;
      }
      return true;
    }
  }

  public static void start_thread(Collection<ParserPage> pages) {
    int threads = 16;
    ThreadPoolExecutor threadPoolExecutor =
        new ThreadPoolExecutor(
            threads, threads, 0L, TimeUnit.MILLISECONDS, new ThreadPoolQueue<>(100));
    //    threadPoolExecutor.setRejectedExecutionHandler();

    AtomicInteger counter = new AtomicInteger();
    for (ParserPage page : pages) {
      threadPoolExecutor.submit(
          (Callable<Void>)
              () -> {
                int idx = counter.incrementAndGet();
                if (idx % 1000 == 0) {
                  log.info("{} of {}", idx, pages.size());
                }

                byte[] data = Files.readAllBytes(config.getPagePath(page.pageId()));
                runParser(data, page, new ArrayList<>());
                return null;
              });
    }
  }

  private record QueueEntry(byte[] in, ParserPage pageId) {}

  public static void start_preopen(List<ParserPage> pages)
      throws InterruptedException, IOException {
    ConcurrentSkipListSet<String> errors = new ConcurrentSkipListSet<>();

    AuditorExecutor<ParserPage, QueueEntry> executor = new AuditorExecutor<>(log);
    executor.run(
        pages,
        16,
        (pageId) -> new QueueEntry(Files.readAllBytes(config.getPagePath(pageId.pageId())), pageId),
        Runtime.getRuntime().availableProcessors() * 2,
        (pageData) -> runParser(pageData.in(), pageData.pageId(), errors));
    log.info("writing {} auditor errors", errors.size());
    Files.write(Path.of("auditor-errors.log"), errors);
  }

  private static List<String> runParser(byte[] data, ParserPage page, Collection<String> errors) {
    try {
      ParserResult result = parser.parsePage(data, page);
      getErrors(page.pageId(), result, errors);
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

  private static void getErrors(UUID pageId, ParserResult result, Collection<String> errors) {
    List<ValidationRecord> pages =
        dbStorage.getPageByUrl(
            result.subpages().stream().map(Subpage::url).collect(Collectors.toList()));
    for (ValidationRecord page : pages) {
      if (result.subpages().stream()
          .noneMatch(parserEntry -> parserEntry.url().equals(page.url().toString()))) {
        errors.add("parser missing url " + page.url());
      }
    }
    for (Subpage subpage : result.subpages()) {
      if (pages.stream().noneMatch(page -> page.url().toString().equals(subpage.url()))) {
        errors.add("db missing url " + subpage.url());
      }
    }
  }
}
