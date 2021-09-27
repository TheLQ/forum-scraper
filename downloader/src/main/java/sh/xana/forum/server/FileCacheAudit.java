package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DlStatus;

public class FileCacheAudit {
  private static final Logger log = LoggerFactory.getLogger(FileCacheAudit.class);
  private static DatabaseStorage dbStorage;

  public static void main(String[] args) throws IOException {
    ServerConfig config = new ServerConfig();
    dbStorage = new DatabaseStorage(config);

    HashSet<UUID> existingFiles = new HashSet<>();
    // find . -type f > filecache-files.txt
    Files.lines(Path.of(config.get(config.ARG_FILE_CACHE), "..", "filecache-files.txt"))
        .filter(e -> e.endsWith(".header") || e.endsWith(".response"))
        .map(e -> e.substring(e.lastIndexOf('/') + 1, e.lastIndexOf(".")))
        .map(UUID::fromString)
        .forEach(existingFiles::add);
    log.info("Loaded {} existingFiles", existingFiles.size());

    //    List<UUID> dbPageIds =
    //        dbStorage.getPageUrls(Pages.PAGES.DLSTATUS.in(DlStatus.Parse, DlStatus.Done));
    var dbPageIds =
        dbStorage.getPages(
            List.of(Pages.PAGES.PAGEURL, Pages.PAGES.PAGEID),
            Pages.PAGES.DLSTATUS.in(DlStatus.Parse, DlStatus.Done));
    log.info("Fetched {} Parse/Done ids", dbPageIds.size());

    int missingCount = 0;
    for (PagesRecord dbPageId : dbPageIds) {
      boolean successful = existingFiles.remove(dbPageId.getPageid());
      if (!successful) {
        log.warn(
            "db contains id "
                + dbPageId.getPageid()
                + " but fs missing. count "
                + missingCount++
                + " url "
                + dbPageId.getPageurl());
      }
    }

    //    dbPageIds = dbStorage.getPageIdsOnly();
    //    log.info("Fetched {} all ids", dbPageIds.size());
    //    for (UUID pageId : dbPageIds) {
    //      existingFiles.remove(pageId);
    //    }

    //    existingFiles.forEach(System.out::println);
    //    System.out.println("remaining " + existingFiles.size());
  }
}
