package sh.xana.forum.server;

import fi.iki.elonen.NanoHTTPD;
import java.util.List;
import java.util.UUID;
import sh.xana.forum.common.dbutil.DatabaseStorage;

record WebPages(DatabaseStorage dbStorage) {
  String pageAddSite(NanoHTTPD.IHTTPSession session) {
    String siteUrl = WebServer.getRequiredParameter(session, "siteurl");

    UUID siteId = dbStorage.insertSite(siteUrl);

    dbStorage.insertPageQueued(siteId, List.of(siteUrl), DatabaseStorage.PageType.ForumList);

    return siteId.toString();
  }

  String pageOverview() {
    StringBuilder result = new StringBuilder();
    for (DatabaseStorage.OverviewEntry entry : dbStorage.getOverviewSites()) {
      result.append(entry.toString()).append("\r\n");
    }
    return result.toString();
  }
}
