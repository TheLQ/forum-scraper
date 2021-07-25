package sh.xana.forum.server.parser;

import java.util.UUID;

public class ParserException extends RuntimeException {

  protected ParserException(UUID pageId) {
    this(null, pageId);
  }

  public ParserException(String message, UUID pageId) {
    this(message, pageId, null);
  }

  public ParserException(String message, UUID pageId, Throwable cause) {
    super((message != null ? message + " " : "") + "Page " + pageId, cause);
  }
}
