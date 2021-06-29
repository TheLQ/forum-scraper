package sh.xana.forum.client;

import java.io.IOException;
import java.net.URI;

public class RequestException extends RuntimeException {

  public RequestException(URI uri, int httpCode, Object body) {
    super(builder(uri, httpCode, body));
  }

  public RequestException(String message, IOException e) {
    super(message, e);
  }

  private static String builder(URI uri, int httpCode, Object body) {
    String msg = "HTTP Error " + httpCode + " on url " + uri;
    if (body instanceof String) {
      msg += " body length " + msg.length() + System.lineSeparator() + msg;
    } else if (body.getClass().isArray()) {
      msg += " body array length " + ((Object[])body).length;
    } else {
      msg += " body " + body;
    }
    return msg;
  }
}
