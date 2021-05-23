package sh.xana.forum.server;

import fi.iki.elonen.NanoHTTPD;
import java.util.List;
import java.util.UUID;
import sh.xana.forum.common.dbutil.DatabaseStorage;

public record WebPages(DatabaseStorage dbStorage) {
  public static final String PAGE_SITE_ADD = "site/add";

  String pageAddSite(NanoHTTPD.IHTTPSession session) {
    String siteUrl = WebServer.getRequiredParameter(session, "siteurl");

    UUID siteId = dbStorage.insertSite(siteUrl);

    dbStorage.insertPageQueued(siteId, List.of(siteUrl), DatabaseStorage.PageType.ForumList);

    return siteId.toString();
  }

  public static final String PAGE_OVERVIEW = "overview";

  String pageOverview() {
    StringBuilder result = new StringBuilder();
    for (DatabaseStorage.OverviewEntry entry : dbStorage.getOverviewSites()) {
      result.append(entry.toString()).append("\r\n");
    }
    return result.toString();
  }

  public static final String PAGE_CLIENT_NODEINIT = "client/nodeinit";

  String pageClientNodeInit() {
    return dbStorage.getDownloadNodesJSON();
  }

  public static final String PAGE_CLIENT_BUFFER = "client/buffer";

  String pageClientBuffer(NanoHTTPD.IHTTPSession session) {
    String domain = WebServer.getRequiredParameter(session, "domain");

    return dbStorage.getNodeBufferJSON(domain);
  }
}
