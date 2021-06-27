package sh.xana.forum.server.parser;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;
import sh.xana.forum.server.dbutil.DatabaseStorage.ValidationRecord;

/** Audits complete file cache */
public class Auditor {
  private static final Logger log = LoggerFactory.getLogger(Auditor.class);
  private static DatabaseStorage dbStorage;
  private static PageParser parser;

  public static void main(String[] args) throws Exception {
    ServerConfig config = new ServerConfig();
    dbStorage = new DatabaseStorage(config);
    parser = new PageParser(config);

    //    if (true) {
    ////      log.info("match " +
    // SMF.PATTERN_SID.matcher("https://www.eevblog.com/forum/beginners/1600/?PHPSESSID=qf571lncip3uqm5uv9otgklpr1").replaceAll(""));
    //      storage.tmp();
    //      return;
    //    }

    if (args.length == 0) {
      throw new RuntimeException("no args");
    } else if (args[0].equals("file")) {
      UUID pageId = UUID.fromString(args[1]);
      Path path = parser.getPagePath(pageId);
      ParserResult result =
          parser.parsePage(
              Files.readAllBytes(path), pageId, dbStorage.getPageDomain(pageId).toString());
      postValidator(pageId, result);
      log.info(
          "result {}",
          Utils.jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
      return;
    }

    log.info("query start");
    List<PagesRecord> pages =
        dbStorage.getPagesIds(
            //
            //
            // Pages.PAGES.SITEID.eq(UUID.fromString("df613af3-caca-439a-b881-f8cbc34d779c")),
            Pages.PAGES.EXCEPTION.isNull(), Pages.PAGES.DLSTATUS.eq(DlStatus.Done));
    log.info("query end");

    switch (args[0]) {
      case "simple" -> start_simple(pages);
      case "pre" -> start_preopen(pages);
      case "thread" -> start_thread(pages);
      default -> throw new RuntimeException("unknown");
    }
  }

  public static void start_simple(List<PagesRecord> pages) {
    AtomicInteger counter = new AtomicInteger();
    pages.parallelStream()
        .forEach(
            page -> {
              int idx = counter.incrementAndGet();
              if (idx % 1000 == 0) {
                log.info("{} of {}", idx, pages.size());
              }
              try {
                UUID pageId = page.getPageid();
                byte[] data = Files.readAllBytes(parser.getPagePath(pageId));
                ParserResult result =
                    parser.parsePage(data, pageId, dbStorage.getPageDomain(pageId).toString());
                postValidator(pageId, result);
              } catch (Exception e) {
                log.error("FAIL ON " + page.getPageid(), e);
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

  public static void start_thread(List<PagesRecord> pages) {
    int threads = 16;
    ThreadPoolExecutor threadPoolExecutor =
        new ThreadPoolExecutor(
            threads, threads, 0L, TimeUnit.MILLISECONDS, new ThreadPoolQueue<>(100));
    //    threadPoolExecutor.setRejectedExecutionHandler();

    AtomicInteger counter = new AtomicInteger();
    for (PagesRecord page : pages) {
      threadPoolExecutor.submit(
          (Callable<Void>)
              () -> {
                int idx = counter.incrementAndGet();
                if (idx % 1000 == 0) {
                  log.info("{} of {}", idx, pages.size());
                }

                UUID pageId = page.getPageid();
                byte[] data = Files.readAllBytes(parser.getPagePath(pageId));
                ParserResult result =
                    parser.parsePage(data, pageId, dbStorage.getPageDomain(pageId).toString());
                postValidator(pageId, result);
                return null;
              });
    }
  }

  private record QueueEntry(InputStream in, UUID pageId, String baseUrl) {}

  public static void start_preopen(List<PagesRecord> pages) {
    BlockingQueue<QueueEntry> open = new ArrayBlockingQueue<>(100);

    AtomicInteger counter = new AtomicInteger();
    ExecutorService executorService =
        Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r ->
                new Thread(
                    () -> {
                      log.info("STARTING THREAD");
                      while (true) {
                        int idx = counter.incrementAndGet();
                        if (idx % 1000 == 0) {
                          log.info("{} of {}", idx, pages.size());
                        }

                        try {
                          QueueEntry take = open.take();
                          ParserResult result =
                              parser.parsePage(
                                  take.in().readAllBytes(), take.pageId(), take.baseUrl());
                          postValidator(take.pageId(), result);
                        } catch (Exception e) {
                          log.error("FAILED TO PARSE", e);
                          System.exit(1);
                        }
                      }
                    }));
    for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
      executorService.submit(() -> log.info("WHY ARE YOU CALLING ME"));
    }

    pages.parallelStream()
        .forEach(
            page -> {
              UUID pageId = page.getPageid();
              try {
                open.put(
                    new QueueEntry(
                        new FileInputStream(parser.getPagePath(pageId).toFile()),
                        pageId,
                        dbStorage.getPageDomain(pageId).toString()));
              } catch (Exception e) {
                log.error("FAILED TO PUT", e);
                System.exit(1);
              }
            });
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
