package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.PerformanceCounter;
import sh.xana.forum.common.Utils;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ParserPage;

public class AuditorCleanJsoup {
  private static final Logger log = LoggerFactory.getLogger(AuditorCleanJsoup.class);

  public static void main(String[] args) throws IOException {
    ServerConfig config = new ServerConfig();
    DatabaseStorage dbStorage = new DatabaseStorage(config);

    log.info("query start");

    List<String> domains = List.of("kiwifarms.net", "xlforum.net");
    log.info("domains {}", domains);
    List<ParserPage> pages =
        dbStorage.getParserPages(
            false,
//            Pages.PAGES.SITEID.in(
//                dbStorage.siteCache.mapByDomains(domains, SitesRecord::getSiteid)),
            Pages.PAGES.DLSTATUS.in(DlStatus.Parse, DlStatus.Done));

    PerformanceCounter counter = new PerformanceCounter(log, 1000);
    Utils.threadRunner(
        16,
        "parser",
        () -> {
          try {
            int idx;
            while ((idx = counter.incrementAndLog(pages)) < pages.size()) {
              ParserPage page = pages.get(idx);
              Document doc =
                  Jsoup.parse(
                      config.getPagePath(page.pageId()).toFile(), null, page.siteUrl().toString());
              Files.writeString(config.getPageXMLPath(page.pageId()), doc.outerHtml());


              //              OutputStream fileOut = Files.newOutputStream(
              //                  config.getPageJsoupPath(page.pageId()));
              //              try (ObjectOutputStream objOut = new ObjectOutputStream(new
              // BufferedOutputStream(fileOut))) {
              //                objOut.writeObject(doc);
              //              }

//              ByteArrayOutputStream out = new ByteArrayOutputStream(150_998);
//              try (ObjectOutputStream objOut = new ObjectOutputStream(out)) {
//                objOut.writeObject(doc);
//              }
//              try (OutputStream out2 =
//                  new FileOutputStream(config.getPageJsoupPath(page.pageId()).toFile())) {
//                out2.write(out.toByteArray());
//              }

              //              Files.write(config.getPageJsoupPath(page.pageId()), );
            }
          } catch (Exception e) {
            log.error("PARSER CRASH", e);
          }
        });
  }
}
