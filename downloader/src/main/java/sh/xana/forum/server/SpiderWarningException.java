package sh.xana.forum.server;

public class SpiderWarningException extends RuntimeException {

  public SpiderWarningException(String message) {
    super(message);
  }

  public SpiderWarningException(String message, Throwable cause) {
    super(message, cause);
  }
}
