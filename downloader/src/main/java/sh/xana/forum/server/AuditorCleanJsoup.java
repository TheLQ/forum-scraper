package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.AuditorExecutor;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.ParserPage;

public class AuditorCleanJsoup {
  private static final Logger log = LoggerFactory.getLogger(AuditorCleanJsoup.class);
  private static final PageBytes BYTE_INPUT_DEATH = new PageBytes(null, null);

  private record PageBytes(ParserPage page, byte[] data) {}

  private record PageAsXmlString(UUID pageId, String data) {}

  public static void main(String[] args) throws IOException, InterruptedException {
    log.info("v2");
    ServerConfig config = new ServerConfig();
    DatabaseStorage dbStorage = new DatabaseStorage(config);

    AuditorCache cache = new AuditorCache(config);
    AuditorCache.CacheIterator cacheItr = cache.iterator();

    BlockingQueue<ParserPage> pagesToRead = new ArrayBlockingQueue<>(2000);
    BlockingQueue<PageBytes> pagesToParse = new ArrayBlockingQueue<>(2000);
    BlockingQueue<PageAsXmlString> pagesToSave = new ArrayBlockingQueue<>(2000);

    log.info("starting");
    // Parsing
    AuditorExecutor executor = new AuditorExecutor(log);

    executor.startConverterForSupplierToSize(
        "pageReader",
        6,
        pagesToRead::take,
        cache.getCacheSize(),
        e -> new PageBytes(e, Files.readAllBytes(config.getPagePath(e.pageId()))),
        pagesToParse
    );

    executor.startConverterForSupplierToNull(
        "pageParser",
        6,
        pagesToParse::take,
        pageBytes -> {
          Document doc =
              Jsoup.parse(
                  config.getPagePath(pageBytes.page().pageId()).toFile(),
                  null,
                  pageBytes.page().siteUrl().toString());
          return new PageAsXmlString(pageBytes.page().pageId(), doc.outerHtml());
        },
        pagesToSave
    );

    executor.startConsumer(pagesToSave, 2, e -> {
      Files.writeString(config.getPageXMLPath(e.pageId()), e.data());
    });

    for (ParserPage parserPage : cache) {
      pagesToRead.put(parserPage);
    }
    log.info("done with main");
  }
}
