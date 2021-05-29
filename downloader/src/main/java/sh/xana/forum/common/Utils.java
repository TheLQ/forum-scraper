package sh.xana.forum.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

public class Utils {
  private Utils() {}

  public static final Logger log = LoggerFactory.getLogger(Utils.class);
  public static final HttpClient httpClient = HttpClient.newHttpClient();
  public static final ObjectMapper jsonMapper = new ObjectMapper();

  public static String BACKEND_SERVER = "http://127.0.0.1:8080/";

  public static UUID uuidFromBytes(byte[] bytes) {
    ByteBuffer bb = ByteBuffer.wrap(bytes);
    long firstLong = bb.getLong();
    long secondLong = bb.getLong();
    return new UUID(firstLong, secondLong);
  }

  public static byte[] uuidAsBytes(UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return bb.array();
  }

  public static String serverGetBackend(String path) {
    String urlRaw = BACKEND_SERVER + path;
    return serverGet(urlRaw);
  }

  public static String serverGet(String urlRaw) {
    HttpRequest request = HttpRequest.newBuilder().uri(newUri(urlRaw)).build();
    return serverRequest(request);
  }

  public static String serverPostBackend(String path, String postData) {
    return serverPost(BACKEND_SERVER + path, postData);
  }

  public static String serverPost(String urlRaw, String postData) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(newUri(urlRaw))
            .POST(BodyPublishers.ofString(postData))
            .build();
    return serverRequest(request);
  }

  public static String serverRequest(HttpRequest request) {
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "Failing status code "
                + response.statusCode()
                + ". Response length "
                + response.body().length()
                + " '"
                + response.body()
                + "'");
      }
      return response.body();
    } catch (Exception e) {
      throw new RuntimeException("Failure on " + request.uri(), e);
    }
  }

  private static String parameterBuilder(Iterator<Param> parameters) {
    StringBuilder sb = new StringBuilder();
    while (parameters.hasNext()) {
      Param param = parameters.next();
      sb.append(param.key()).append('=').append(param.value());
      if (parameters.hasNext()) {
        sb.append('&');
      }
    }
    return sb.toString();
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
