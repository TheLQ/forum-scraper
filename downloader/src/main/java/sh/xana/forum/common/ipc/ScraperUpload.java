package sh.xana.forum.common.ipc;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ScraperUpload(
    UUID nodeId, String domain, boolean requestMore, List<Success> successes, List<Error> errors) {
  public record Success(
      UUID pageId,
      URI pageUrl,
      List<URI> redirectList,
      byte[] body,
      Map<String, List<String>> headers,
      int responseCode) {}

  public record Error(UUID id, String exception) {}
}
