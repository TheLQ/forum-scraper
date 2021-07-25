package sh.xana.forum.server.parser;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple wrapper to require URL validation checks ran inline, vs passing potentially unvalidated
 * URI or String objects around
 */
public class ValidatedUrl {
  private static final Logger log = LoggerFactory.getLogger(ValidatedUrl.class);
  public final String url;

  public ValidatedUrl(String url, AbstractForum parser, SourcePage page) {
    validateUrl(url, page.doc().baseUri(), parser);
    this.url = url;
  }

  public static void validateUrl(String pageUrl, String baseUrl, AbstractForum parser) {
    if (pageUrl.equals(baseUrl)) {
      // this is the root page, skip
      return;
    }
    if (!pageUrl.startsWith(baseUrl)) {
      throw new RuntimeException("Page " + pageUrl + " does not start with base " + baseUrl);
    }

    if (!baseUrl.endsWith("/")) {
      throw new RuntimeException("Missing end / for " + baseUrl);
    }

    pageUrl = parser.postProcessUrl(pageUrl);

    String subUrl = pageUrl.substring(baseUrl.length());
    if (subUrl.startsWith("/")) {
      throw new RuntimeException(
          "Unexpected starting / for " + subUrl + " with page " + pageUrl + " base " + baseUrl);
    }

    // strip non ascii so we can use limited capture groups
    Outer:
    while (true) {
      for (int i = 0; i < subUrl.length(); i++) {
        char character = subUrl.charAt(i);
        if (character < 32 || character > 126) {
          log.info("Replacing char {} in {}", character, subUrl);
          subUrl = subUrl.replace("" + character, "");
          continue Outer;
        }
      }
      break;
    }

    String subUrlFinal = subUrl;
    if (Arrays.stream(parser.validateUrl()).noneMatch(e -> e.matcher(subUrlFinal).matches())) {
      throw new RuntimeException(
          "Failed to match "
              + subUrl
              + " from "
              + pageUrl
              + " in "
              + parser.getClass().getCanonicalName());
    }
  }
}
