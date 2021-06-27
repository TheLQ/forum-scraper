package sh.xana.forum.server;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.NodeInitRequest;
import sh.xana.forum.common.ipc.NodeResponse;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.common.ipc.ScraperUpload;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;
import sh.xana.forum.server.dbutil.DatabaseStorage.ForumType;

public class WebServer extends NanoHTTPD {
  public static final Logger log = LoggerFactory.getLogger(WebServer.class);
  private static final Date start = new Date();
  public static final int PORT = 8080;
  private static final String MIME_BLOB = "application/octet-stream";
  public static final String NODE_AUTH_KEY = "x-xana-auth";
  private final String nodeAuthValue;

  private final DatabaseStorage dbStorage;
  private final PageManager pageManager;
  private final NodeManager nodeManager;

  public WebServer(
      DatabaseStorage dbStorage,
      PageManager pageManager,
      NodeManager nodeManager,
      ServerConfig config) {
    // Bind to localhost since on aws we are proxied
    super(PORT);
    this.dbStorage = dbStorage;
    this.pageManager = pageManager;
    this.nodeManager = nodeManager;
    this.nodeAuthValue = config.get(config.ARG_SERVER_AUTH);
  }

  public void start() throws IOException {
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    log.info("server running on http://{}:{}", getHostname(), PORT);
  }

  /** Router */
  @Override
  public Response serve(IHTTPSession session) {
    // Get page, stripping the initial /
    String page = session.getUri();

    log.info("Processing page {}", page);
    try {
      if (page.startsWith(PAGE_FILE)) {
        return pageFile(session);
      }
      switch (page) {
        case "/":
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
        case PAGE_OVERVIEW_PAGE:
          return newFixedLengthResponse(pageOverviewPage(session));
        case PAGE_OVERVIEW_ERRORS:
          return newFixedLengthResponse(pageOverviewErrors());
        case PAGE_OVERVIEW_ERRORS_CLEAR:
          return newFixedLengthResponse(pageOverviewErrorsClear(session));
        case PAGE_OVERVIEW_ERRORS_CLEARALL:
          return newFixedLengthResponse(pageOverviewErrorsClearAll(session));
        case "/favicon.ico":
          // Stop "java.net.SocketException: An established connection was aborted by the software
          // in your host machine"
          return newFixedLengthResponse(Response.Status.NOT_FOUND, "image/x-icon", null);
        default:
          return newFixedLengthResponse(
              Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
      }
    } catch (Exception e) {
      log.error("Caught exception while processing page {}", session.getUri(), e);
      return newFixedLengthResponse(
          Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_HTML, e.toString());
    }
  }

  public static final String PAGE_SITE_ADD = "/site/add";

  String pageAddSite(NanoHTTPD.IHTTPSession session) {
    assertAuth(session);
    URI siteUrl = Utils.toURI(WebServer.getRequiredParameter(session, "siteurl"));
    String forumType = WebServer.getRequiredParameter(session, "forumType");

    UUID siteId = dbStorage.insertSite(siteUrl, ForumType.valueOf(forumType));

    dbStorage.insertPageQueued(siteId, List.of(siteUrl), DatabaseStorage.PageType.ForumList, null);

    return siteId.toString();
  }

  public static final String PAGE_OVERVIEW = "/overview";

  String pageOverview() {
    StringBuilder result = new StringBuilder();
    result.append("<pre>");
    for (DatabaseStorage.OverviewEntry entry : dbStorage.getOverviewSites()) {
      result.append(entry.toString()).append("\r\n");
    }
    result.append("</pre>");
    return result.toString();
  }

  public static final String PAGE_OVERVIEW_PAGE = "/overview/page";

  private String pageOverviewPage(IHTTPSession session) {
    UUID pageId = UUID.fromString(getRequiredParameter(session, "pageId"));

    List<PagesRecord> pages = dbStorage.getOverviewPage(pageId);
    return _overviewPage(pages);
  }

  public static final String PAGE_OVERVIEW_ERRORS = "/overview/errors";

  private String pageOverviewErrors() {
    List<PagesRecord> pages = dbStorage.getOverviewErrors();
    return _overviewPage(pages);
  }

  private String _overviewPage(List<PagesRecord> pages) {
    StringBuilder result = new StringBuilder();
    result.append("<style>th, td { border: 1px solid; }</style>");
    result.append("<a href='").append(PAGE_OVERVIEW_ERRORS_CLEARALL).append("'>Clear All</a>");
    result.append("<table border=1>");

    result.append("<thead><tr>");
    result.append("<th>PageId</th>");
    result.append("<th>DlStatus</th>");
    result.append("<th>PageType</th>");
    result.append("<th>Updated</th>");
    result.append("<th>URL</th>");
    result.append("<th>DL Status Code</th>");
    result.append("</tr><thead>");

    result.append("<tbody>");
    for (PagesRecord page : pages) {
      result.append("<tr>");
      result.append("<td>").append(page.getPageid()).append("</td>");
      result.append("<td>").append(page.getDlstatus()).append("</td>");
      result.append("<td>").append(page.getPagetype()).append("</td>");
      result.append("<td>").append(page.getPageupdated()).append("</td>");
      result.append("<td>").append(page.getPageurl()).append("</td>");
      result.append("<td>").append(page.getDlstatuscode()).append("</td>");
      result.append("</tr>");

      result
          .append("<tr><td colspan=7><pre>")
          // clear errors
          .append("<a href='")
          .append(PAGE_OVERVIEW_ERRORS_CLEAR)
          .append("?pageId=")
          .append(page.getPageid())
          .append("'>clear</a>")
          .append(" <a href='")
          .append(PAGE_OVERVIEW_PAGE)
          .append("?pageId=")
          .append(page.getPageid())
          .append("'>Page Info</a><br/>")
          .append(page.getException())
          .append("</pre></td></tr>");
    }
    result.append("</tbody>");

    result.append("</table>");
    return result.toString();
  }

  public static final String PAGE_OVERVIEW_ERRORS_CLEAR = "/overview/errors/clear";

  private String pageOverviewErrorsClear(NanoHTTPD.IHTTPSession session)
      throws InterruptedException {
    UUID pageId = UUID.fromString(getRequiredParameter(session, "pageId"));
    return pageOverviewErrorsClear_util(pageId);
  }

  public static final String PAGE_OVERVIEW_ERRORS_CLEARALL = "/overview/errors/clearAll";

  private String pageOverviewErrorsClearAll(NanoHTTPD.IHTTPSession session)
      throws InterruptedException {
    List<PagesRecord> pages = dbStorage.getPages(Pages.PAGES.EXCEPTION.isNotNull());
    StringBuilder result = new StringBuilder("<pre>");
    for (PagesRecord page : pages) {
      String clearResult = pageOverviewErrorsClear_util(page.getPageid());
      result.append(clearResult).append("\r\n");
    }
    result.append("</pre>");
    return result.toString();
  }

  private String pageOverviewErrorsClear_util(UUID pageId) throws InterruptedException {
    dbStorage.setPageExceptionNull(pageId);

    PagesRecord page = dbStorage.getPage(pageId);
    if (page.getDlstatus().equals(DlStatus.Parse)) {
      pageManager.signalSpider();
      return "ok and queued parse page";
    }
    return "ok";
  }

  public static final String PAGE_CLIENT_NODEINIT = "/client/nodeinit";

  String pageClientNodeInit(NanoHTTPD.IHTTPSession session) throws IOException {
    assertAuth(session);
    byte[] input = readPostInput(session);
    NodeInitRequest request = Utils.jsonMapper.readValue(input, NodeInitRequest.class);
    UUID nodeId = nodeManager.registerNode(request.ip(), request.hostname());

    NodeResponse response =
        new NodeResponse(
            nodeId, dbStorage.getScraperDomainsIPC().toArray(new NodeResponse.ScraperEntry[0]));

    return Utils.jsonMapper.writeValueAsString(response);
  }

  public static final String PAGE_CLIENT_BUFFER = "/client/buffer";

  String pageClientBuffer(NanoHTTPD.IHTTPSession session) throws IOException, InterruptedException {
    assertAuth(session);
    byte[] input = readPostInput(session);
    ScraperUpload scraperUpload = Utils.jsonMapper.readValue(input, ScraperUpload.class);
    pageManager.processResponses(scraperUpload);

    List<ScraperDownload.SiteEntry> requests;
    if (scraperUpload.requestMore()) {
      requests = dbStorage.movePageQueuedToDownloadIPC(scraperUpload.domain());
    } else {
      requests = List.of();
    }
    ScraperDownload download = new ScraperDownload(requests);

    log.info(
        "client - {} download success {} download error {} sent because {}",
        scraperUpload.successes().size(),
        scraperUpload.errors().size(),
        requests.size(),
        scraperUpload.requestMore());
    return Utils.jsonMapper.writeValueAsString(download);
  }

  public static final String PAGE_FILE = "/file";

  private Response pageFile(NanoHTTPD.IHTTPSession session) throws IOException {
    assertAuth(session);
    String mimeType = MIME_BLOB;
    if (session.getUri().endsWith(".service")) {
      mimeType = MIME_PLAINTEXT;
    }

    String filenameStr = session.getUri().substring(PAGE_FILE.length() + /*dir sep slash*/ 1);
    Path filename = Path.of(filenameStr);
    if (!Files.exists(filename)) {
      log.debug("Cannot find path {} full {}", filename, filename.toAbsolutePath());
      log.trace("Cannot find path {} full {}", filename, filename.toAbsolutePath());
      return newFixedLengthResponse(
          Response.Status.NOT_FOUND, NanoHTTPD.MIME_HTML, "file not found " + filenameStr);
    }
    long length = Files.size(filename);

    return newFixedLengthResponse(
        Response.Status.OK, mimeType, Files.newInputStream(filename), length);
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

  private void assertAuth(IHTTPSession session) {
    String value = session.getHeaders().get(NODE_AUTH_KEY);
    if (value == null || !value.equals(nodeAuthValue)) {
      throw new RuntimeException("Protected endpoint " + session.getHeaders());
    }
  }
}
