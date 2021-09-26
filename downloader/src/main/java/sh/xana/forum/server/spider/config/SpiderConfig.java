package sh.xana.forum.server.spider.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;

public record SpiderConfig(
    @NotNull @JsonProperty(required = true) List<String> domains,
    @NotNull @JsonProperty(required = true) LinkHandler linkForum,
    @NotNull @JsonProperty(required = true) LinkHandler linkTopic,
    @Nullable LinkHandler linkMultipage,
    @Nullable String excludePathStart,
    @Nullable String homepageAlias,
    @NotNull @JsonProperty(required = true) List<Pattern> validRegex) {
  private static final Logger log = LoggerFactory.getLogger(SpiderConfig.class);

  public SpiderConfig {
    // clean comments
    domains.removeIf(s -> s.startsWith("/"));

    // validation
    if (domains.isEmpty()) {
      throw new RuntimeException("domains empty");
    }
    if (validRegex.isEmpty()) {
      throw new RuntimeException("regex empty");
    }
  }

  public static SpiderConfig load(URL path) {
    log.debug("loading {}", path);
    SpiderConfig spiderConfig;
    try {
      spiderConfig = Utils.jsonMapper.readValue(path, SpiderConfig.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load " + path, e);
    }

    return spiderConfig;
  }

  public static List<SpiderConfig> load() {
    List<SpiderConfig> result =
        Utils.listResourceDirectory("siteConfig", ".json")
            .map(SpiderConfig::load)
            .collect(Collectors.toList());
    if (result.isEmpty()) {
      throw new RuntimeException("no configs loaded");
    }
    return result;
  }
}
