package sh.xana.forum.server;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.NodeInitRequest;
import sh.xana.forum.common.ipc.NodeResponse;
import sh.xana.forum.common.ipc.ScraperRequest;
import sh.xana.forum.common.ipc.ScraperResponse;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;

public class WebServer extends NanoHTTPD {
  public static final Logger log = LoggerFactory.getLogger(WebServer.class);
  private static final Date start = new Date();
  public static final int PORT = 8080;

  private final DatabaseStorage dbStorage;
  private final Processor processor;
  private final NodeManager nodeManager;

  public WebServer(DatabaseStorage dbStorage, Processor processor, NodeManager nodeManager)
      throws IOException {
    super(PORT);
    this.dbStorage = dbStorage;
    this.processor = processor;
    this.nodeManager = nodeManager;
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    log.info("server running on http://{}:{}", getHostname(), PORT);
  }

  /** Router */
  @Override
  public Response serve(IHTTPSession session) {
    // Get page, stripping the initial /
    String page = session.getUri().substring(1);

    log.info("Processing page {}", session.getUri());
    try {
      switch (page) {
        case "":
          return newFixedLengthResponse(
              "Xana Forum Downloader started " + start + " currently " + new Date());
        case PAGE_SITE_ADD:
          return newFixedLengthResponse(pageAddSite(session));
        case PAGE_OVERVIEW:
          return newFixedLengthResponse(pageOverview());
        case PAGE_CLIENT_NODEINIT:
          return newFixedLengthResponse(pageClientNodeInit(session));
        case PAGE_CLIENT_BUFFER:
          return newFixedLengthResponse(pageClientBuffer(session));
        case PAGE_OVERVIEW_ERRORS:
          return newFixedLengthResponse(pageOverviewErrors());
        case PAGE_OVERVIEW_ERRORS_CLEAR:
          return newFixedLengthResponse(pageOverviewErrorsClear(session));
        default:
          return newFixedLengthResponse(
              Response.Status.NOT_FOUND, "text/plain", "page " + page + " does not exist");
      }
    } catch (Exception e) {
      log.error("Caught exception while processing page {}", session.getUri(), e);
      return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.toString());
    }
  }

  public static final String PAGE_SITE_ADD = "site/add";

  String pageAddSite(NanoHTTPD.IHTTPSession session) {
    URI siteUrl = Utils.toURI(WebServer.getRequiredParameter(session, "siteurl"));

    UUID siteId = dbStorage.insertSite(siteUrl);

    dbStorage.insertPageQueued(siteId, List.of(siteUrl), DatabaseStorage.PageType.ForumList, null);

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

  public static final String PAGE_OVERVIEW_ERRORS = "overview/errors";

  private String pageOverviewErrors() {
    StringBuilder result = new StringBuilder();
    result.append("<style>th, td { border: 1px solid; }</style>");
    result.append("<table border=1>");

    result.append("<thead><tr>");
    result.append("<th>PageId</th>");
    result.append("<th>DlStatus</th>");
    result.append("<th>PageType</th>");
    result.append("<th>Updated</th>");
    result.append("<th>URL</th>");
    result.append("</tr><thead>");

    result.append("<tbody>");
    List<PagesRecord> pages = dbStorage.getPages(Pages.PAGES.EXCEPTION.isNotNull());
    for (PagesRecord page : pages) {
      result.append("<tr>");
      result.append("<td>").append(page.getId()).append("</td>");
      result.append("<td>").append(page.getDlstatus()).append("</td>");
      result.append("<td>").append(page.getPagetype()).append("</td>");
      result.append("<td>").append(page.getUpdated()).append("</td>");
      result.append("<td>").append(page.getUrl()).append("</td>");
      result.append("</tr>");

      result
          .append("<tr><td colspan=5><pre>")
          // need slash for base url
          .append("<a href='/")
          .append(PAGE_OVERVIEW_ERRORS_CLEAR)
          .append("?pageId=")
          .append(page.getId())
          .append("'>clear</a><br/>")
          .append(page.getException())
          .append("</pre></td></tr>");
    }
    result.append("</tbody>");

    result.append("</table>");
    return result.toString();
  }

  public static final String PAGE_OVERVIEW_ERRORS_CLEAR = "overview/errors/clear";

  private String pageOverviewErrorsClear(NanoHTTPD.IHTTPSession session)
      throws InterruptedException {
    UUID pageId = UUID.fromString(getRequiredParameter(session, "pageId"));

    dbStorage.setPageExceptionNull(pageId);

    PagesRecord page = dbStorage.getPage(pageId);
    if (page.getDlstatus().equals(DlStatus.Parse.toString())) {
      processor.queuePage(pageId);
      return "ok and queued parse page";
    }
    return "ok";
  }

  public static final String PAGE_CLIENT_NODEINIT = "client/nodeinit";

  String pageClientNodeInit(NanoHTTPD.IHTTPSession session) throws IOException {
    byte[] input = readPostInput(session);
    NodeInitRequest request = Utils.jsonMapper.readValue(input, NodeInitRequest.class);
    UUID nodeId = nodeManager.registerNode(request.ip(), request.hostname());

    NodeResponse response =
        new NodeResponse(
            nodeId, dbStorage.getScraperDomainsIPC().toArray(new NodeResponse.ScraperEntry[0]));

    return Utils.jsonMapper.writeValueAsString(response);
  }

  public static final String PAGE_CLIENT_BUFFER = "client/buffer";

  String pageClientBuffer(NanoHTTPD.IHTTPSession session) throws IOException, InterruptedException {
    String domain = WebServer.getRequiredParameter(session, "domain");
    byte[] input = readPostInput(session);
    ScraperResponse responses = Utils.jsonMapper.readValue(input, ScraperResponse.class);
    processor.processResponses(responses);

    List<ScraperRequest.SiteEntry> requests = dbStorage.movePageQueuedToDownloadIPC(domain);
    log.info(
        "client {} downloaded {} download error {} sent",
        responses.successes().size(),
        responses.errors().size(),
        requests.size());
    return Utils.jsonMapper.writeValueAsString(requests);
  }

  private static String getRequiredParameter(IHTTPSession session, String key) {
    var params = session.getParameters();
    try {
      List<String> values = params.get(key);
      if (values == null) {
        throw new NoSuchElementException(key);
      } else if (values.size() != 1) {
        throw new RuntimeException("Found duplicate keys " + values);
      }
      return values.get(0);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to get key "
              + key
              + " from url "
              + session.getUri()
              + " | "
              + session.getQueryParameterString(),
          e);
    }
  }

  private static byte[] readPostInput(IHTTPSession session) throws IOException {
    if (session.getMethod() != Method.POST) {
      throw new RuntimeException("Expected POST got " + session.getMethod());
    }

    // Clients seem to expect us to close the stream once content-length is reached.
    // If you read beyond (eg readAllBytes(), while (input.read() != -1), etc) the
    // connections deadlocks, then hits the socket timeout and kills the connection
    int length = Integer.parseInt(session.getHeaders().get("content-length"));
    byte[] data = new byte[length];
    int currentLength = 0;
    while (length != currentLength) {
      currentLength += session.getInputStream().read(data, currentLength, length - currentLength);
    }
    return data;
  }
}
