package sh.xana.forum.common.ipc;

import java.util.UUID;
import sh.xana.forum.server.dbutil.DatabaseStorage;

public record ParserResult(UUID nodeId, DatabaseStorage.PageType type, ParserEntry[] subpages) {
  public record ParserEntry(String name, String url, DatabaseStorage.PageType type) {}
}
