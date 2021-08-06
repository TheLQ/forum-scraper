package sh.xana.forum.server;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.server.db.tables.Pageredirects;
import sh.xana.forum.server.db.tables.records.PageredirectsRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.parser.PageParser;

public class AuditRedirectsNotRemoved {
  private static final Logger log = LoggerFactory.getLogger(AuditRedirectsNotRemoved.class);
  private static DatabaseStorage dbStorage;
  private static PageParser parser;

  public static void main(String[] args) throws IOException {
    ServerConfig config = new ServerConfig();
    dbStorage = new DatabaseStorage(config);
    parser = new PageParser(config);

    log.info("Loading page redirects...");
    List<PageredirectsRecord> pageRedirects =
        dbStorage.getPageRedirects(Pageredirects.PAGEREDIRECTS.INDEX.greaterThan((byte) 1));

    log.info("Loaded {} redirects", pageRedirects.size());

    List<URI> pageUrlsOnly = dbStorage.getPageUrlsOnly();
    for (URI pageUrl : pageUrlsOnly) {
      // find page redirect
      PageredirectsRecord record = null;
      for (PageredirectsRecord pageRedirect : pageRedirects) {
        //        if (pageUrl.equals(pageRedirect.getRedirecturl())) {
        //          record =
        //        }
      }
    }
  }
}
