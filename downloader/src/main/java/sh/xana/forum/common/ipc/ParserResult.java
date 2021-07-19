package sh.xana.forum.common.ipc;

import java.util.List;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;

public record ParserResult(PageType pageType, ForumType forumType, List<ParserEntry> subpages) {
  public record ParserEntry(String name, String url, PageType pageType) {}
}
