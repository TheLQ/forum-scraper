package sh.xana.forum.server.parser;

import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple wrapper to require URL validation checks ran inline, vs passing potentially unvalidated
 * URI or String objects around
 */
public class ValidatedUrl {
  private static final Logger log = LoggerFactory.getLogger(ValidatedUrl.class);
  public final String urlStr;

  public ValidatedUrl(String urlStr, SourcePage page, AbstractForum parser) {
    this(urlStr, page.doc().baseUri(), parser);
  }

  public ValidatedUrl(String urlStr, String baseUri, AbstractForum parser) {
    this.urlStr = validateUrl(urlStr, baseUri, List.of(parser.validateUrl()));
  }

  public ValidatedUrl(String pageUrl, String baseUrl, List<Pattern> patterns) {
    this.urlStr = validateUrl(pageUrl, baseUrl, patterns);
  }

  public static String validateUrl(String pageUrl, String baseUrl, List<Pattern> patterns) {
    if (pageUrl.equals(baseUrl)) {
      // this is the root page, skip
      return pageUrl;
    }
    if (!pageUrl.startsWith(baseUrl)) {
      throw new ValidatedUrlException("Page " + pageUrl + " does not start with base " + baseUrl);
    }

    if (!baseUrl.endsWith("/")) {
      throw new ValidatedUrlException("Missing end / for " + baseUrl);
    }

    String subUrl = pageUrl.substring(baseUrl.length());
    if (subUrl.startsWith("/")) {
      throw new ValidatedUrlException(
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
    if (patterns.stream().noneMatch(e -> e.matcher(subUrlFinal).matches())) {
      throw new ValidatedUrlException("Failed to regex validate " + subUrl + " from " + pageUrl);
    }
    return pageUrl;
  }
}
