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
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DlStatus;

public class AuditorFileCache {
  private static final Logger log = LoggerFactory.getLogger(AuditorFileCache.class);
  private static DatabaseStorage dbStorage;

  public static void main(String[] args) throws IOException {
    ServerConfig config = new ServerConfig();
    dbStorage = new DatabaseStorage(config);

    HashSet<UUID> existingFiles = new HashSet<>();
    // find . -type f > filecache-files.txt
    Files.lines(Path.of(config.get(config.ARG_FILE_CACHE), "..", "filecache-files.txt"))
        .map(e -> e.substring(e.lastIndexOf('/') + 1, e.lastIndexOf(".")))
        .map(UUID::fromString)
        .forEach(existingFiles::add);
    log.info("Loaded {} existingFiles", existingFiles.size());

    List<UUID> pageIds =
        dbStorage.getPageIdsOnly(Pages.PAGES.DLSTATUS.in(DlStatus.Parse, DlStatus.Done));
    log.info("Fetched {} Parse/Done ids", pageIds.size());

    int count = 0;
    for (UUID pageId : pageIds) {
      boolean successful = existingFiles.remove(pageId);
      if (!successful) {
        throw new RuntimeException("db contains id " + pageId + " but fs missing. count " + count);
      }
      count++;
    }

    pageIds = dbStorage.getPageIdsOnly();
    log.info("Fetched {} all ids", pageIds.size());
    for (UUID pageId : pageIds) {
      existingFiles.remove(pageId);
    }

    //    existingFiles.forEach(System.out::println);
    System.out.println("remaining " + existingFiles.size());
  }
}
