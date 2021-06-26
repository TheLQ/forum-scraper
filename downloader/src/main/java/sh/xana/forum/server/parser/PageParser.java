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
    PageParser parser = new PageParser(config);

    if (args.length == 0) {
      throw new RuntimeException("no args");
    } else if (args[0].equals("file")) {
      UUID pageId = UUID.fromString(args[1]);
      Path path = parser.getPagePath(pageId);
      parser.parsePage(Files.readAllBytes(path), pageId, "http://1/");

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

  public PageParser(ServerConfig config) {
    this.config = config;
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

                byte[] data = Files.readAllBytes(getPagePath(page.getPageid()));
                ParserResult result = parsePage(data, page.getPageid(), "http://1/");
                if (result.subpages().size() == 888888) {
                  System.out.println("asdf");
                }
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
    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
        threads,
        threads,
        0L,
        TimeUnit.MILLISECONDS,
        new ThreadPoolQueue<>(100)
    );
//    threadPoolExecutor.setRejectedExecutionHandler();

    AtomicInteger counter = new AtomicInteger();
    for (PagesRecord page : pages) {
        threadPoolExecutor.submit((Callable<Void>) () -> {
          int idx = counter.incrementAndGet();
          if (idx % 1000 == 0) {
            log.info("{} of {}", idx, pages.size());
          }

          byte[] data = Files.readAllBytes(getPagePath(page.getPageid()));
          ParserResult result = parsePage(data, page.getPageid(), "http://1/");
          if (result.subpages().size() == 888888) {
            System.out.println("asdf");
          }
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
//                        if (idx % 1000 == 0) {
                          log.info("{} of {}", idx, pages.size());
//                        }

                        try {
                          QueueEntry take = open.take();
                          parsePage(take.in().readAllBytes(), take.pageId(), take.baseUrl());
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
                        new FileInputStream(getPagePath(pageId).toFile()), pageId, "http://1/"));
              } catch (Exception e) {
                log.error("FAILED TO PUT", e);
                System.exit(1);
              }
            });
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
      ParserEntry parser = new ParserEntry(element.text(), element.attr("href"), pageType);
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
