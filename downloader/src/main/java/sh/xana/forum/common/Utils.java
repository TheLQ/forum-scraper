package sh.xana.forum.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
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
//    HttpRequest request = HttpRequest.newBuilder().uri(newUri(BACKEND_SERVER + path)).GET().build();
//    return serverRequest(request, BodyHandlers.ofString()).body();
//  }

  public static String serverGet(String urlRaw) {
    HttpRequest request = HttpRequest.newBuilder().uri(newUri(urlRaw)).build();
    return serverRequest(request, BodyHandlers.ofString()).body();
  }

  public static String serverPostBackend(String path, String postData) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(newUri(BACKEND_SERVER + path))
            .POST(BodyPublishers.ofString(postData))
            .header(WebServer.NODE_AUTH_KEY, BACKEND_KEY)
            .build();
    return serverRequest(request, BodyHandlers.ofString()).body();
  }

  public static String serverPost(String urlRaw, String postData) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(newUri(urlRaw))
            .POST(BodyPublishers.ofString(postData))
            .build();
    return serverRequest(request, BodyHandlers.ofString()).body();
  }

  public static <T> HttpResponse<T> serverRequest(
      HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
    try {
      HttpResponse<T> response = httpClient.send(request, responseBodyHandler);

      if (response.statusCode() != 200) {
        String debug = "";
        if (response.body() instanceof String) {
          debug =
              ". Response length "
                  + ((String) response.body()).length()
                  + " '"
                  + response.body()
                  + "'";
        }
        throw new RuntimeException("Failing status code " + response.statusCode() + debug);
      }
      return response;
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
