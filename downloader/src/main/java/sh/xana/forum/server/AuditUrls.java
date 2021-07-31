package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.server.db.tables.Sites;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.PageUrl;
import sh.xana.forum.server.dbutil.ForumType;

public class AuditUrls {
  private static final Logger log = LoggerFactory.getLogger(AuditUrls.class);
  private static DatabaseStorage dbStorage;

  public static void main(String[] args) throws IOException {
    ServerConfig config = new ServerConfig();
    dbStorage = new DatabaseStorage(config);

    System.setProperty("org.jooq.no-tips", "true");

    List<PageUrl> pageUrls;
    Path cacheUrlsPath = Path.of("AuditUrls.cache.txt");
    if (Files.exists(cacheUrlsPath)) {
      log.info("loading cache");

      Files.lines(cacheUrlsPath)
          .map(
              line -> {
                String[] split = line.split(",", 3);
                return new PageUrl(
                    Utils.toURI(split[2]), Utils.toURI(split[1]), ForumType.valueOf(split[0]));
              })
          .forEach(AuditUrls::eval);
    } else {
      log.info("Fetching database...");
      pageUrls = dbStorage.getPageUrls(Sites.SITES.FORUMTYPE.eq(ForumType.ForkBoard));
      log.info("Fetched {} ids", pageUrls.size());

      Files.write(
          cacheUrlsPath,
          pageUrls.stream()
              .map(e -> e.forumType() + "," + e.siteUrl() + "," + e.pageUrl())
              .collect(Collectors.toList()));

      for (PageUrl pageUrlEntry : pageUrls) {
        eval(pageUrlEntry);
      }
    }
    log.info("Done");
  }

  private static void eval(PageUrl pageUrlEntry) {
    throw new UnsupportedOperationException();
    //    try {
    //      validateUrl(
    //          pageUrlEntry.pageUrl().toString(),
    //          pageUrlEntry.siteUrl().toString(),
    //          pageUrlEntry.forumType());
    //    } catch (Exception e) {
    //      log.error("FAIL " + e.getMessage());
    //    }
  }
}
