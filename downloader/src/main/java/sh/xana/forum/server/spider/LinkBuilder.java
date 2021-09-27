package sh.xana.forum.server.spider;

import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkBuilder {
  private static final Logger log = LoggerFactory.getLogger(LinkBuilder.class);
  private final String originalUri;
  private final URIBuilder builder;
  private final String baseUri;
  @Nullable private String linkFullCached;
  @Nullable private String linkRelativeCached;

  public LinkBuilder(String originalUri, String baseUri) throws URISyntaxException {
    this.originalUri = originalUri;
    this.builder = new URIBuilder(originalUri);
    this.baseUri = baseUri;

    clean();
  }

  private void clean() {
    if (!baseUri.endsWith("/")) {
      throw new LinkValidationException("Missing end / for " + baseUri);
    }

    // url pre-process - make sure we have a base path
    if (builder.getPath().equals("")) {
      invalidateCachedString();
      builder.setPath("/");
    }

    // url pre-process - convert double directory path
    String newPath = builder.getPath();
    int newPathOrig = newPath.length();
    newPath = StringUtils.replace(newPath, "//", "/");
    // >0 due to empty path causing IndexOutOfBounds
    // >1 since we do want empty paths
    while (newPath.length() > 1 && newPath.charAt(0) == '/') {
      newPath = newPath.substring(1);
    }
    if (newPath.length() != newPathOrig) {
      invalidateCachedString();
      builder.setPath(newPath);
    }

    // safe to test now
    // test - make sure we are in the right directory
    if (!fullLink().startsWith(baseUri)) {
      throw new LinkValidationException(
          "link " + originalUri + " is not starting with baseUri " + baseUri);
    }

    // TODO: redundant?
    // test - make sure we don't have a double slash
    if (relativeLink().startsWith("/")) {
      throw new LinkValidationException(
          "unexpected relative with / relative "
              + relativeLink()
              + " baseUri "
              + baseUri
              + " url "
              + fullLink());
    }

    // url post-process - don't care about fragment
    String oldFragment = builder.getFragment();
    if (oldFragment != null) {
      // log.trace("Removing {} fragment {}", linkString, oldFragment);
      invalidateCachedString();
      builder.setFragment(null);
    }

    // url post-process - remove empty ?
    if (builder.getQueryParams().isEmpty()) {
      invalidateCachedString();
      builder.clearParameters();
    }
  }

  public String fullLink() {
    if (linkFullCached == null) {
      linkFullCached = builder.toString();
    }
    return linkFullCached;
  }

  public String relativeLink() {
    if (linkRelativeCached == null) {
      linkRelativeCached = builder.toString();
    }
    return linkRelativeCached;
  }

  public List<NameValuePair> queryParams() {
    // safe because builder gives us a copy
    return builder.getQueryParams();
  }

  public void queryParams(List<NameValuePair> newQueryParams) {
    if (newQueryParams.isEmpty()) {
      builder.clearParameters();
    } else {
      builder.setParameters(newQueryParams);
    }
    invalidateCachedString();
  }

  public void pathMustEndWithSlash() {
    if (builder.getPath().endsWith("/")) {
      return;
    }
    builder.setPath(builder.getPath() + "/");
    invalidateCachedString();
  }

  public void validate(List<Pattern> patterns) {
    String subUrl = relativeLink();

    // strip non ascii so we can use limited capture groups
    Outer:
    while (true) {
      for (int i = 0; i < subUrl.length(); i++) {
        char character = subUrl.charAt(i);
        if (character < 32 || character > 126) {
          log.trace("Replacing char {} in {}", character, subUrl);
          subUrl = subUrl.replace("" + character, "");
          continue Outer;
        }
      }
      break;
    }

    String subUrlFinal = subUrl;
    if (patterns.stream().noneMatch(e -> e.matcher(subUrlFinal).matches())) {
      throw new LinkValidationException(
          "Failed to regex validate " + subUrl + " from " + fullLink());
    }
  }

  private void invalidateCachedString() {
    linkFullCached = null;
    linkRelativeCached = null;
  }
}
