package sh.xana.forum.common.ipc;

import sh.xana.forum.server.dbutil.DatabaseStorage;

public record ParserResult(
    boolean loginRequired,
    DatabaseStorage.PageType pageType,
    DatabaseStorage.ForumType forumType,
    ParserEntry[] subpages) {
  public record ParserEntry(String name, String url, DatabaseStorage.PageType pageType) {}
}
