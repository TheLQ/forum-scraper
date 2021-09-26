package sh.xana.forum.server.spider.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public record QueryKeyLinkHandler(
    @NotNull @JsonProperty(required = true) String pathEquals,
    @NotNull @JsonProperty(required = true) List<String> allowedKeys)
    implements LinkHandler {
  public QueryKeyLinkHandler {
    if (allowedKeys.isEmpty()) {
      throw new RuntimeException("allowedKeys is empty");
    }
  }
}
