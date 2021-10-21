package sh.xana.forum.server.spider;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;

public record SpiderConfig(
    String source,
    @NotNull @JsonProperty(required = true) List<String> domains,
    @NotNull @JsonProperty(required = true) LinkHandler linkForum,
    @NotNull @JsonProperty(required = true) LinkHandler linkTopic,
    @Nullable LinkHandler linkMultipage,
    @Nullable List<String> excludePathStart,
    @Nullable String homepageAlias,
    @NotNull @JsonProperty(required = true) List<Pattern> validRegex) {
  private static final Logger log = LoggerFactory.getLogger(SpiderConfig.class);
  /**
   * Horrible Dependency Injection hack. InjectableValues.Std fails with "Can not set final field"
   * since this is a record
   */
  private static final ThreadLocal<String> objPass = new ThreadLocal<>();

  public SpiderConfig {
    source = objPass.get();

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

  public static SpiderConfig findForDomain(List<SpiderConfig> configs, String domain) {
    for (SpiderConfig config : configs) {
      if (config.domains().contains(domain)) {
        return config;
      }
    }
    throw new RuntimeException("cannot find spider config for domain " + domain);
  }

  public static SpiderConfig load(URL path) {
    log.debug("loading {}", path);
    SpiderConfig spiderConfig;
    try {
      objPass.set(path.toString());
      spiderConfig = Utils.jsonMapper.readValue(path, SpiderConfig.class);
      objPass.remove();
    } catch (Throwable e) {
      throw new RuntimeException("Failed to load " + path, e);
    }

    return spiderConfig;
  }

  public static SpiderConfig loadResource(String path) {
    log.debug("loading {}", path);
    SpiderConfig spiderConfig;
    try {
      // This works even for non-module runtimes
      InputStream in = SpiderConfig.class.getModule().getResourceAsStream(path);

      objPass.set(path);
      spiderConfig = Utils.jsonMapper.readValue(in, SpiderConfig.class);
      objPass.remove();
    } catch (Throwable e) {
      throw new RuntimeException("Failed to load " + path, e);
    }

    return spiderConfig;
  }

  public static List<SpiderConfig> load() {
    List<SpiderConfig> result =
        Utils.listResourceDirectory("siteConfig", ".json")
            .map(SpiderConfig::loadResource)
            .collect(Collectors.toList());
    if (result.isEmpty()) {
      throw new RuntimeException("no configs loaded");
    }

    HashMap<String, String> domainsToConfig = new HashMap<>();
    for (SpiderConfig spiderConfig : result) {
      for (String domain : spiderConfig.domains()) {
        String existingSource = domainsToConfig.get(domain);
        if (existingSource != null) {
          throw new IllegalArgumentException(
              "Duplicate domain "
                  + domain
                  + " exists in "
                  + existingSource
                  + " and "
                  + spiderConfig.source());
        }
        domainsToConfig.put(domain, spiderConfig.source());
      }
    }

    return result;
  }
}
