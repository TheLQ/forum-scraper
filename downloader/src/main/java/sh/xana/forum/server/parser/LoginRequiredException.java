package sh.xana.forum.server.parser;

import java.util.UUID;

public class LoginRequiredException extends ParserException {

  public LoginRequiredException(UUID pageId) {
    super(pageId);
  }
}
