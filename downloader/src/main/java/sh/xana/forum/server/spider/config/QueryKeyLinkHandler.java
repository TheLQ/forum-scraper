package sh.xana.forum.server.spider.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record QueryKeyLinkHandler(
    @NotNull @JsonProperty(required = true) String pathEquals,
    @NotNull @JsonProperty(required = true) List<String> allowedKeys,
    @Nullable Map<String, String> remapKeys)
    implements LinkHandler {
  private static final Logger log = LoggerFactory.getLogger(QueryKeyLinkHandler.class);

  public QueryKeyLinkHandler {
    if (allowedKeys.isEmpty()) {
      throw new RuntimeException("allowedKeys is empty");
    }
  }

  @Override
  public boolean processLink(URIBuilder link, String linkRelative) {
    if (!linkRelative.startsWith(this.pathEquals())) {
      return false;
    }

    List<NameValuePair> queryParams = link.getQueryParams();
    MutableBoolean changed = new MutableBoolean(false);

    // remap
    if (this.remapKeys() != null) {
      for (var iter = queryParams.listIterator(); iter.hasNext(); ) {
        NameValuePair e = iter.next();
        String remappedKey = this.remapKeys().get(e.getName());
        if (remappedKey != null) {
          log.trace("remaping {} key {} to {}", link, e.getName(), remappedKey);
          iter.set(new BasicNameValuePair(remappedKey, e.getValue()));
          changed.setTrue();
        }
      }
    }

    // strip any args that are not in our list
    queryParams.removeIf(
        e -> {
          boolean doRemove = !this.allowedKeys().contains(e.getName());
          if (doRemove) {
            changed.setTrue();
            log.trace(
                "Removing {} query {}={} valid {} rel {}",
                link,
                e.getName(),
                e.getValue(),
                this.allowedKeys(),
                linkRelative);
          }
          return doRemove;
        });
    if (queryParams.size() == 0) {
      // remove empty ?
      link.clearParameters();
    } else if (changed.isTrue()) {
      link.setParameters(queryParams);
    }

    // remove unnessesary /
    //    String newUri = uriBuilder.toString();
    //    if (newUri.endsWith("/")) {
    //      newUri = newUri.substring(0, newUri.length() - 1);
    //    }
    return true;
  }
}
