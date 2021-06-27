package sh.xana.forum.server.parser;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.ParserEntry;
import sh.xana.forum.server.ServerConfig;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;
import sh.xana.forum.server.dbutil.DatabaseStorage.ForumType;
import sh.xana.forum.server.dbutil.DatabaseStorage.PageType;
import sh.xana.forum.server.dbutil.DatabaseStorage.ValidationRecord;
import sh.xana.forum.server.parser.impl.ForkBoard;
import sh.xana.forum.server.parser.impl.SMF;
import sh.xana.forum.server.parser.impl.XenForo_F;
import sh.xana.forum.server.parser.impl.vBulletin;

public class PageParser {
  private static final Logger log = LoggerFactory.getLogger(PageParser.class);
  private static final AbstractForum[] PARSERS =
      new AbstractForum[] {new vBulletin(), new XenForo_F(), new SMF(), new ForkBoard()};

  public static void main(String[] args) throws Exception {
    ServerConfig config = new ServerConfig();
    DatabaseStorage storage = new DatabaseStorage(config);
    PageParser parser = new PageParser(config, storage);

    if (args.length == 0) {
      throw new RuntimeException("no args");
    } else if (args[0].equals("file")) {
      UUID pageId = UUID.fromString(args[1]);
      Path path = parser.getPagePath(pageId);
      ParserResult result =
          parser.parsePage(
              Files.readAllBytes(path), pageId, storage.getPageDomain(pageId).toString());
      parser.postValidator(pageId, result);
      return;
    }

    log.info("query start");
    List<PagesRecord> pages =
        storage.getPagesIds(
            //
            //
            // Pages.PAGES.SITEID.eq(UUID.fromString("df613af3-caca-439a-b881-f8cbc34d779c")),
            Pages.PAGES.EXCEPTION.isNull(), Pages.PAGES.DLSTATUS.eq(DlStatus.Done));
    log.info("query end");

    switch (args[0]) {
      case "simple" -> parser.start_simple(pages);
      case "pre" -> parser.start_preopen(pages);
      case "thread" -> parser.start_thread(pages);
      default -> throw new RuntimeException("unknown");
    }
  }

  private final ServerConfig config;
  private final DatabaseStorage dbStorage;

  public PageParser(ServerConfig config, DatabaseStorage dbStorage) {
    this.config = config;
    this.dbStorage = dbStorage;
  }

  public void start_simple(List<PagesRecord> pages) {
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
                byte[] data = Files.readAllBytes(getPagePath(pageId));
                ParserResult result =
                    parsePage(data, pageId, dbStorage.getPageDomain(pageId).toString());
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

  public void start_thread(List<PagesRecord> pages) {
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
                byte[] data = Files.readAllBytes(getPagePath(pageId));
                ParserResult result =
                    parsePage(data, pageId, dbStorage.getPageDomain(pageId).toString());
                postValidator(pageId, result);
                return null;
              });
    }
  }

  private record QueueEntry(InputStream in, UUID pageId, String baseUrl) {}

  public void start_preopen(List<PagesRecord> pages) {
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
                              parsePage(take.in().readAllBytes(), take.pageId(), take.baseUrl());
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
                        new FileInputStream(getPagePath(pageId).toFile()),
                        pageId,
                        dbStorage.getPageDomain(pageId).toString()));
              } catch (Exception e) {
                log.error("FAILED TO PUT", e);
                System.exit(1);
              }
            });
  }

  private void postValidator(UUID pageId, ParserResult result) {
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

  private Path getPagePath(UUID pageId) {
    return Path.of(config.get(config.ARG_FILE_CACHE), pageId + ".response");
  }

  private ParserResult parsePage(byte[] data, UUID pageId, String baseUrl) {
    String rawHtml = new String(data);

    PageType pageType = PageType.Unknown;
    List<ParserEntry> subpages = new ArrayList<>();
    for (AbstractForum parser : PARSERS) {
      ForumType forumType = parser.detectForumType(rawHtml);
      if (forumType == null) {
        continue;
      }

      SourcePage sourcePage = new SourcePage(rawHtml, parser.newDocument(rawHtml, baseUrl));

      if (parser.detectLoginRequired(sourcePage)) {
        return new ParserResult(true, pageType, forumType, subpages);
      }

      addAnchorSubpage(parser.getSubforumAnchors(sourcePage), subpages, PageType.ForumList);
      addAnchorSubpage(parser.getTopicAnchors(sourcePage), subpages, PageType.TopicPage);
      if (!subpages.isEmpty()) {
        pageType = PageType.ForumList;
      }

      // TODO: Iterate through to build post history
      if (!parser.getPostElements(sourcePage).isEmpty()) {
        if (pageType != PageType.Unknown) {
          throw new ParserException("detected both post elements and subforum/topic links");
        } else {
          pageType = PageType.TopicPage;
        }
      }

      addAnchorSubpage(parser.getPageLinks(sourcePage), subpages, pageType);

      ParserResult result = new ParserResult(false, pageType, forumType, subpages);
      parser.postProcessing(sourcePage, result);
      return result;
    }
    throw new ParserException("No parsers handled this file");
  }

  private void addAnchorSubpage(
      Collection<Element> elements, List<ParserEntry> subpages, PageType pageType) {
    for (Element element : elements) {
      ParserEntry parser = new ParserEntry(element.text(), element.absUrl("href"), pageType);
      subpages.add(parser);
    }
  }

  public static class ParserException extends RuntimeException {

    public ParserException(String message) {
      super(message);
    }

    public ParserException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
