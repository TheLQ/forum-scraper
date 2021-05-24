package sh.xana.forum.server;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.dbutil.DatabaseStorage;
import sh.xana.forum.common.ipc.DownloadResponse;

public class WebServer extends NanoHTTPD {
  public static final Logger log = LoggerFactory.getLogger(WebServer.class);
  private static final Date start = new Date();
  public static final int PORT = 8080;

  private final DatabaseStorage dbStorage;
  private final Processor processor;

  public WebServer(DatabaseStorage dbStorage, Processor processor) throws IOException {
    super(PORT);
    this.dbStorage = dbStorage;
    this.processor = processor;
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
          return newFixedLengthResponse(pageClientNodeInit());
        case PAGE_CLIENT_BUFFER:
          return newFixedLengthResponse(pageClientBuffer(session));
        default:
          return newFixedLengthResponse(
              Response.Status.NOT_FOUND, "text/plain", "page " + page + " does not exist");
      }
    } catch (Exception e) {
      log.error("Caught exception while processing page", session.getUri(), e);
      return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.toString());
    }
  }

  public static final String PAGE_SITE_ADD = "site/add";

  String pageAddSite(NanoHTTPD.IHTTPSession session) {
    String siteUrl = WebServer.getRequiredParameter(session, "siteurl");

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

  public static final String PAGE_CLIENT_NODEINIT = "client/nodeinit";

  String pageClientNodeInit() {
    return dbStorage.getScraperDomainsJSON();
  }

  public static final String PAGE_CLIENT_BUFFER = "client/buffer";

  String pageClientBuffer(NanoHTTPD.IHTTPSession session) throws IOException {
    String domain = WebServer.getRequiredParameter(session, "domain");
    byte[] input = readPostInput(session);
    DownloadResponse responses = Utils.jsonMapper.readValue(input, DownloadResponse.class);
    processor.processResponses(responses);

    return dbStorage.movePageQueuedToDownloadJSON(domain);
  }

  static String getRequiredParameter(IHTTPSession session, String key) {
    var params = session.getParameters();
    try {
      List<String> values = params.get(key);
      if (values.size() != 1) {
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
              + session.getQueryParameterString());
    }
  }

  byte[] readPostInput(IHTTPSession session) throws IOException {
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
