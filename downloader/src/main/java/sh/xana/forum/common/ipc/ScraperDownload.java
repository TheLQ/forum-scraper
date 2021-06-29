package sh.xana.forum.common.ipc;

import java.net.URI;
import java.util.List;
import java.util.UUID;

public record ScraperDownload(List<SiteEntry> entries) {
  public record SiteEntry(UUID pageId, URI url) {}
}
