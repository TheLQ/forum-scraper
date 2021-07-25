package sh.xana.forum.server.parser;

import java.util.UUID;

public class Soft500Exception extends ParserException {
  public Soft500Exception(UUID pageId) {
    super(pageId);
  }
}
