package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ParserResult.Subpage;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.Sites;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.PageParser;

public class AuditUrls {
  private static final Logger log = LoggerFactory.getLogger(AuditUrls.class);
  private static DatabaseStorage dbStorage;

  public static void main(String[] args) throws IOException {
    ServerConfig config = new ServerConfig();

    dbStorage = new DatabaseStorage(config);

    //    Stream<PageUrl> pageUrls;
    //    Path cacheUrlsPath = Path.of("AuditUrls.cache.txt");
    //    if (Files.exists(cacheUrlsPath)) {
    //      log.info("loading cache");
    //
    //      pageUrls =
    //          Files.lines(cacheUrlsPath)
    //              .map(
    //                  line -> {
    //                    String[] split = line.split("\0", 3);
    //                    return new PageUrl(
    //                        Utils.toURI(split[0]), Utils.toURI(split[1]),
    // ForumType.valueOf(split[2]));
    //                  });
    //    } else {
    //      log.info("Fetching database...");
    //      List<PageUrl> dbPageUrls =
    //          dbStorage.getPageUrls(
    //              // Sites.SITES.FORUMTYPE.eq(ForumType.XenForo_F)
    //              // dbStorage.siteCache.mapByDomains(List.of("xlforum.net"),
    // SitesRecord::getSiteid)
    //              Sites.SITES.DOMAIN.eq("xlforum.net"));
    //      log.info("Fetched {} ids", dbPageUrls.size());
    //
    //      Files.write(
    //          cacheUrlsPath,
    //          dbPageUrls.stream()
    //              .map(e -> e.pageUrl() + "\0" + e.siteUrl() + "\0" + e.forumType())
    //              .collect(Collectors.toList()));
    //
    //      pageUrls = dbPageUrls.stream();
    //    }

    var result = dbStorage.getPageUrls(Sites.SITES.DOMAIN.in("forum.miata.net", "xlforum.net"));
    log.info("Loaded {} pages", NumberFormat.getNumberInstance().format(result.size()));

    Iterator<CharSequence> errors =
        result.parallelStream().map(AuditUrls::eval).filter(Objects::nonNull).iterator();
    try {
      Files.write(Path.of("auditorUrl.errors.log"), () -> errors);
    } catch (IOException e) {
      e.printStackTrace();
    }

    log.info("Done");
  }

  private static CharSequence eval(Record entry) {
    AbstractForum forum = PageParser.PARSERS.get(entry.get(Sites.SITES.FORUMTYPE));
    try {
      String linkOrig = entry.get(Pages.PAGES.PAGEURL).toString();
      UUID pageId = entry.get(Pages.PAGES.PAGEID);
      Subpage validLink =
          forum.getValidLink(
              linkOrig,
              entry.get(Pages.PAGES.PAGETYPE),
              entry.get(Sites.SITES.SITEURL).toString(),
              pageId,
              true);
      if (validLink == null) {
        log.warn("invalid link " + linkOrig);
      } else if (!linkOrig.equals(validLink.url().urlStr)) {
        log.warn("Got different link {} orig {}", validLink.url().urlStr, linkOrig);
        try {
          dbStorage.setPageUrl(pageId, validLink.url().urlStr);
        } catch (DataAccessException e) {
          if (e.getMessage().contains("Duplicate entry")) {
            dbStorage.deletePage(pageId);
          } else {
            throw e;
          }
        }

        //        return (Utils.format("Got different link {}\t{}", validLink.url().urlStr,
        // linkOrig));
        return null;
      }
    } catch (Exception e) {
      log.warn("{} soft fail {}", e);
    }
    return null;
  }
}
