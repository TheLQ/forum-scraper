package sh.xana.forum.common.ipc;

import java.util.UUID;

public record NodeResponse(UUID nodeId, ScraperEntry[] scraper) {
  public record ScraperEntry(String domain) {}
}
