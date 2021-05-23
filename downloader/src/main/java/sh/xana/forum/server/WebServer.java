package sh.xana.forum.server;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServer extends NanoHTTPD {
  public static final Logger log = LoggerFactory.getLogger(WebServer.class);
  private static final Date start = new Date();
  public static final int PORT = 8080;

  private final WebPages pages;

  public WebServer(WebPages pages) throws IOException {
    super(PORT);
    this.pages = pages;
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    log.info("server running on http://localhost:{}", PORT);
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
        case "addSite":
          return newFixedLengthResponse(this.pages.pageAddSite(session));
        case "overview":
          return newFixedLengthResponse(this.pages.pageOverview());
        default:
          return newFixedLengthResponse(
              Response.Status.NOT_FOUND, "text/plain", "page " + page + " does not exist");
      }
    } catch (Exception e) {
      log.error("Caught exception while processing page", session.getUri(), e);
      return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.toString());
    }
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
}
