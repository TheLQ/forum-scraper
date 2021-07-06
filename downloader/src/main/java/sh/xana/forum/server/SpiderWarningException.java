package sh.xana.forum.server;

class SpiderWarningException extends RuntimeException {

  public SpiderWarningException(String message) {
    super(message);
  }

  public SpiderWarningException(String message, Throwable cause) {
    super(message, cause);
  }
}
