package sh.xana.forum.server.spider;

public class SpiderException extends RuntimeException {

  public SpiderException(String message) {
    super(message);
  }

  public SpiderException(String message, Throwable cause) {
    super(message, cause);
  }
}
