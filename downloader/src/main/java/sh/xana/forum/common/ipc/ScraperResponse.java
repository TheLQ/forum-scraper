package sh.xana.forum.common.ipc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ScraperResponse(UUID nodeId, List<Success> successes, List<Error> errors) {
  public record Success(
      UUID id, byte[] body, Map<String, List<String>> headers, int responseCode) {}

  public record Error(UUID id, String exception) {}
}
