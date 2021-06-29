package sh.xana.forum.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import sh.xana.forum.client.RequestException;
import sh.xana.forum.server.WebServer;

public class Utils {
  private Utils() {}

  public static final Logger log = LoggerFactory.getLogger(Utils.class);
  public static final HttpClient httpClient = HttpClient.newHttpClient();
  public static final ObjectMapper jsonMapper = new ObjectMapper();

  public static String BACKEND_SERVER;
  public static String BACKEND_KEY;

  public static URI toURI(String raw) {
    try {
      return new URI(raw);
    } catch (URISyntaxException e) {
      throw new RuntimeException("Failed to make URI from " + raw, e);
    }
  }

  //  public static String serverGetBackend(String path) {
  //    HttpRequest request = HttpRequest.newBuilder().uri(newUri(BACKEND_SERVER +
  // path)).GET().build();
  //    return serverRequest(request, BodyHandlers.ofString()).body();
  //  }

  public static String serverGet(String urlRaw) {
    HttpRequest request = HttpRequest.newBuilder().uri(newUri(urlRaw)).build();
    return serverRequest(request, BodyHandlers.ofString()).body();
  }

  public static String serverPostBackend(String path, String postData) throws InterruptedException {
    Exception lastException = null;
    for (int i = 0; i < 30; i++) {
      try {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(newUri(BACKEND_SERVER + path))
                .POST(BodyPublishers.ofString(postData))
                .header(WebServer.NODE_AUTH_KEY, BACKEND_KEY)
                .build();
        return serverRequest(request, BodyHandlers.ofString()).body();
      } catch (RequestException e) {
        lastException = e;
        int waitMinutes = i + /*0-base*/ 1;
        log.warn("Request failed, waiting " + waitMinutes + " minutes", e);
        TimeUnit.MINUTES.sleep(waitMinutes);
      }
    }
    throw new RuntimeException("FAILED AFTER EXPONENTIAL BACKOFF, last exception attached", lastException);
  }

  public static <T> HttpResponse<T> serverRequest(
      HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
    try {
      HttpResponse<T> response = httpClient.send(request, responseBodyHandler);
      if (response.statusCode() != 200) {
        throw new RequestException(request.uri(), response.statusCode(), response.body());
      }
      return response;
    } catch (RequestException e) {
      throw e;
    } catch (IOException e) {
      throw new RequestException("Failed to connect", e);
    } catch (Exception e) {
      throw new RuntimeException("Failure on " + request.uri(), e);
    }
  }

  public static String format(String message, Object... args) {
    return MessageFormatter.basicArrayFormat(message, args);
  }

  private static URI newUri(String uri) {
    try {
      return new URI(uri);
    } catch (Exception e) {
      throw new RuntimeException("Invalid uri " + uri);
    }
  }
}
