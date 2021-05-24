package sh.xana.forum.common.ipc;

import sh.xana.forum.common.dbutil.DatabaseStorage;

public record ParserResult(ParserEntry[] entries) {
  public record ParserEntry(String name, String url, DatabaseStorage.PageType type) {}
}
