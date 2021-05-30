package sh.xana.forum.common.ipc;

import java.net.URI;
import java.util.UUID;

public record ScraperDownload(SiteEntry[] entries) {
  public record SiteEntry(UUID siteId, URI url) {}
}
