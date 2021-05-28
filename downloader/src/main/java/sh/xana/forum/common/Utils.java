package sh.xana.forum.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  public static String serverGet(String path) {
    String urlRaw = BACKEND_SERVER + path;
    try {
      HttpRequest request = HttpRequest.newBuilder().uri(new URI(urlRaw)).build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException("Failing status code " + response.statusCode());
      }
      return response.body();
    } catch (Exception e) {
      throw new RuntimeException("Failure on " + urlRaw, e);
    }
  }

  public static String serverPost(String path, String postData) {
    String urlRaw = BACKEND_SERVER + path;
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(urlRaw))
              .POST(BodyPublishers.ofString(postData))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "Failing status code " + response.statusCode() + "\r\n" + response.body());
      }
      return response.body();
    } catch (Exception e) {
      throw new RuntimeException("Failure on " + urlRaw + " postdata:\r\n" + postData, e);
    }
  }
}
