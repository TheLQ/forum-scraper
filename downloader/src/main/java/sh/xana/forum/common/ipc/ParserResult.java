package sh.xana.forum.common.ipc;

import java.util.List;
import sh.xana.forum.server.dbutil.DatabaseStorage;

public record ParserResult(
    boolean loginRequired,
    DatabaseStorage.PageType pageType,
    DatabaseStorage.ForumType forumType,
    List<ParserEntry> subpages) {
  public record ParserEntry(String name, String url, DatabaseStorage.PageType pageType) {}
}
