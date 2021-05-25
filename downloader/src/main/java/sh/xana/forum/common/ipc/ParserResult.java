package sh.xana.forum.common.ipc;

import sh.xana.forum.server.dbutil.DatabaseStorage;

public record ParserResult(DatabaseStorage.PageType type, ParserEntry[] subpages) {
  public record ParserEntry(String name, String url, DatabaseStorage.PageType type) {}
}