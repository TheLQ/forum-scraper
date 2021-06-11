package sh.xana.forum.server;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;

public class OneTimeProcessor {
private static final Logger log = LoggerFactory.getLogger(OneTimeProcessor.class);

  public static void main(String[] args) throws Exception {
    ServerConfig config = new ServerConfig();

    DatabaseStorage dbStorage = new DatabaseStorage(config);

    log.info("start error");
//    var ids = Files.lines(Path.of("empty.txt"))
//        .map(UUID::fromString)
//        .collect(Collectors.toList());
//    dbStorage.setPageStatus(ids, DlStatus.Queued);
//    for (UUID id : ids) {
//      dbStorage.setPageExceptionNull(id);
//    }

    List<PagesRecord> pages = dbStorage
        .getPages(Pages.PAGES.DOMAIN.in("www.rx7club.com", "www.rx8club.com"),
            Pages.PAGES.URL.likeRegex(".*/page.*"));
    for (PagesRecord page : pages) {
      if (!page.getUrl().toString().endsWith("/")) {
        System.out.println(page.getId() + " - " + page.getUrl());
        dbStorage.oneTimeDb(page);
      }
    }
    log.info("end");
  }
}
