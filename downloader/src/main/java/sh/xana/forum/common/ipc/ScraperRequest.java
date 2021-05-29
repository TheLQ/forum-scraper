package sh.xana.forum.common.ipc;

import java.util.UUID;

public record ScraperRequest(SiteEntry[] entries) {
  public record SiteEntry(UUID siteId, String url) {}
}
