package sh.xana.forum.server.parser;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.ParserEntry;
import sh.xana.forum.server.ServerConfig;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.ValidationRecord;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.dbutil.ParserPage;

/** Audits complete file cache */
public class Auditor {
  private static final Logger log = LoggerFactory.getLogger(Auditor.class);
  private static DatabaseStorage dbStorage;
  private static PageParser parser;

  public static void main(String[] args) throws Exception {
    ServerConfig config = new ServerConfig();
    dbStorage = new DatabaseStorage(config);
    parser = new PageParser(config);

    if (args.length == 0) {
      throw new RuntimeException("no args");
    } else if (args[0].equals("file")) {
      if (args.length != 5) {
        System.out.println("file <path> <baseUrl> <forumType> <pageType>");
      }
      Path path = Path.of(args[0]);

      ParserPage page;
      if (Files.exists(path)) {
        page =
            new ParserPage(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                PageType.valueOf(args[4]),
                200,
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                URI.create(args[2]),
                ForumType.valueOf(args[3]));
      } else {
        UUID pageId = UUID.fromString(args[1]);
        page = dbStorage.getParserPages(true, Pages.PAGES.PAGEID.eq(pageId)).get(0);
        path = parser.getPagePath(pageId);
      }

      ParserResult result = parser.parsePage(Files.readAllBytes(path), page);
      postValidator(page.pageId(), result);
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

    log.info("query start");
    List<ParserPage> pages =
        dbStorage.getParserPages(
            false, Pages.PAGES.EXCEPTION.isNull(), Pages.PAGES.DLSTATUS.eq(DlStatus.Done));
    log.info("query end for " + pages.size());

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
                byte[] data = Files.readAllBytes(parser.getPagePath(page.pageId()));
                ParserResult result = parser.parsePage(data, page);
                postValidator(page.pageId(), result);
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

                byte[] data = Files.readAllBytes(parser.getPagePath(page.pageId()));
                ParserResult result = parser.parsePage(data, page);
                postValidator(page.pageId(), result);
                return null;
              });
    }
  }

  private record QueueEntry(byte[] in, ParserPage pageId) {}

  public static void start_preopen(List<ParserPage> pages) throws InterruptedException {
    BlockingQueue<QueueEntry> open = new ArrayBlockingQueue<>(5000);

    long start = System.currentTimeMillis();
    AtomicInteger counter = new AtomicInteger();
    threadRunner(
        Runtime.getRuntime().availableProcessors() * 16,
        "processor-",
        () -> {
          log.info("STARTING THREAD");
          while (true) {
            int idx = counter.incrementAndGet();
            if (idx % 100 == 0) {
              log.info(
                  "{} of {} - open {} /sec {}",
                  idx,
                  pages.size(),
                  open.size(),
                  idx / ((System.currentTimeMillis() - start) / 1000));
            }

            try {
              QueueEntry take = open.take();
              if (new String(take.in()).trim().equals("")) {
                log.warn("Page " + take.pageId() + " is empty");
              }
              ParserResult result = parser.parsePage(take.in(), take.pageId());
              postValidator(take.pageId().pageId(), result);
            } catch (Exception e) {
              log.error("FAILED TO PARSE", e);
              // System.exit(1);
            }
          }
        });

    AtomicInteger readCounter = new AtomicInteger();
    threadRunner(
        Runtime.getRuntime().availableProcessors() * 4,
        "reader-",
        () -> {
          while (true) {
            int idx = readCounter.getAndIncrement();
            try {
              ParserPage page = pages.get(idx);
              if (idx % 100 == 0) {
                //                log.info("read {} of {} - size {}", idx, pages.size(),
                // open.size());
              }

              open.put(new QueueEntry(Files.readAllBytes(parser.getPagePath(page.pageId())), page));
            } catch (Exception e) {
              log.error("FAILED TO PUT", e);
              System.exit(1);
            }
          }
        });

    while (readCounter.get() != pages.size()) {
      TimeUnit.MINUTES.sleep(1);
    }
  }

  public static void threadRunner(int threads, String namePrefix, Runnable runner) {
    for (int i = 0; i < threads; i++) {
      Thread thread = new Thread(runner);
      thread.setName(namePrefix + i);
      thread.setDaemon(false);
      thread.start();
    }
  }

  private static void postValidator(UUID pageId, ParserResult result) {
    //    List<PagesRecord> pages = dbStorage.getPages(Pages.PAGES.SOURCEPAGEID.eq(pageId));
    List<ValidationRecord> pages =
        dbStorage.getPageByUrl(
            result.subpages().stream().map(ParserEntry::url).collect(Collectors.toList()));

    List<String> errors = new ArrayList<>();
    for (ValidationRecord page : pages) {
      if (result.subpages().stream()
          .noneMatch(parserEntry -> parserEntry.url().equals(page.url().toString()))) {
        errors.add("parser missing url " + page.url());
      }
    }
    for (ParserEntry subpage : result.subpages()) {
      if (pages.stream().noneMatch(page -> page.url().toString().equals(subpage.url()))) {
        errors.add("db missing url " + subpage.url());
      }
    }

    if (!errors.isEmpty()) {
      log.error(
          "Failed to parse page "
              + pageId
              + System.lineSeparator()
              + StringUtils.join(errors, System.lineSeparator()));
    }
  }
}
