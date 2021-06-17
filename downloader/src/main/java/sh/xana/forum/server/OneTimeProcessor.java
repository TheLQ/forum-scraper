package sh.xana.forum.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    //    List<PagesRecord> pages = dbStorage
    //        .getPages(Pages.PAGES.DOMAIN.in("www.rx7club.com", "www.rx8club.com"),
    //            Pages.PAGES.URL.likeRegex(".*/page.*"));
    //    for (PagesRecord page : pages) {
    //      if (!page.getUrl().toString().endsWith("/")) {
    //        System.out.println(page.getId() + " - " + page.getUrl());
    //        dbStorage.oneTimeDb(page);
    //      }
    //    }
    //    log.info("end");

//    Path fileCacheDir = Path.of("M:\\vault-data\\forum-scrape\\filecache");
//    HashSet<UUID> ids = new HashSet<>();
//    Files.list(fileCacheDir)
//        .forEach(e -> {
//          String name = e.getFileName().toString();
//          if (name.equals("lost+found")) {
//            return;
//          }
//          ids.add(UUID.fromString(name.substring(0, name.indexOf('.'))));
//        });
//
//    log.info("start query");
//    List<PagesRecord> pages = dbStorage.tmp();
//    log.info("got query of " + pages.size());
//
//    for (PagesRecord page : pages) {
//        ids.remove(page.getId());
//    }
//
//    for (UUID id : ids) {
////      log.info("{} {} {} {}", page.getDlstatus(), page.getDlstatuscode(), page.getUrl(), page.getException());
////      if (page.getDlstatus() == DlStatus.Queued) {
//        Files.deleteIfExists(fileCacheDir.resolve(id + ".response"));
//        Files.deleteIfExists(fileCacheDir.resolve(id + ".headers"));
////      }
//    }

//    log.info("query start");
//    List<PagesRecord> pages = dbStorage.tmp();
//    log.info("query done");
//    AtomicInteger counter = new AtomicInteger();
//    pages.parallelStream()
//        .forEach(
//            page -> {
//              if (counter.incrementAndGet() % 10000 == 0) {
//                log.info("count " + counter);
//              }
//              if (!Files.exists(fileCacheDir.resolve(page.getId() + ".response"))) {
//                log.info("missing response for " + page.getId());
//              }
//              if (!Files.exists(fileCacheDir.resolve(page.getId() + ".headers"))) {
//                log.info("missing headers for " + page.getId());
//              }
//            });
  }
}
