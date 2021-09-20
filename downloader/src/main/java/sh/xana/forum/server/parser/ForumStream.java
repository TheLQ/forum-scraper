package sh.xana.forum.server.parser;

import java.net.URISyntaxException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForumStream {
  private static final Logger log = LoggerFactory.getLogger(ForumStream.class);

  public static boolean anchorIsNavLink(Element e) {
    return !ForumUtils.anchorIsNotNavLink(e);
  }

  public static String getAnchorFullUrl(Element e) {
    return e.absUrl("href");
  }

  public static boolean isBaseUri(String uri, Document doc) {
    return uri.startsWith(doc.baseUri());
  }

  public static void toValidUriBuilder(String uri, Consumer<URIBuilder> streamMapper) {
    try {
      URIBuilder uriBuilder = new URIBuilder(uri);
      streamMapper.accept(uriBuilder);
    } catch (URISyntaxException e) {
      log.warn("Soft fail could not parse Uri " + uri + " exception " + e.getMessage());
    }
  }

  public static boolean containsQueryKey(URIBuilder uriBuilder, String arg) {
    return uriBuilder.getQueryParams().stream().anyMatch(e -> e.getName().equals(arg));
  }

  public static boolean fileIs(URIBuilder uriBuilder, String... filenames) {
    for (String filename : filenames) {
      // log.trace("path {} filename {}", uri.getPath(), filename);
      if (uriBuilder.getPath().endsWith(filename)) {
        return true;
      }
    }
    return false;
  }

  public static String cleanUrl(URIBuilder uriBuilder, String... args) {
    // strip fragment
    String oldFragment = uriBuilder.getFragment();
    if (oldFragment != null) {
      log.trace("Removing {} fragment {}", uriBuilder, oldFragment);
      uriBuilder.setFragment(null);
    }

    // strip any args that are not in our list
    List<NameValuePair> actualArgs = uriBuilder.getQueryParams();
    int actualArgsOrigSize = actualArgs.size();
    actualArgs.removeIf(
        next -> {
          boolean doRemove = !ArrayUtils.contains(args, next.getName());
          if (doRemove) {
            log.trace("Removing {} query {}={}", uriBuilder, next.getName(), next.getValue());
          }
          return doRemove;
        });
    if (actualArgs.size() != actualArgsOrigSize) {
      uriBuilder.setParameters(actualArgs);
    }

    String newUri = uriBuilder.toString();
    if (newUri.endsWith("/")) {
      newUri = newUri.substring(0, newUri.length() - 1);
    }
    return newUri;
  }

  public static BiConsumer<String, Consumer<ValidatedUrl>> softLogFail(
      Function<String, ValidatedUrl> supplier, SourcePage sourcePage) {
    return (uri, streamMapper) -> {
      try {
        streamMapper.accept(supplier.apply(uri));
      } catch (ValidatedUrlException e) {
        log.warn("Soft fail page {} error {}", sourcePage.pageId(), e.getMessage());
      }
    };
  }
}
