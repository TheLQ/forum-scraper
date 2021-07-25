package sh.xana.forum.server.parser;

import java.util.UUID;

public class EmptyForumException extends ParserException {
  public EmptyForumException(UUID pageId) {
    super(pageId);
  }
}
