package sh.xana.forum.server.spider;

import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ParserResult.Subpage;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.ForumStream;
import sh.xana.forum.server.parser.ValidatedUrl;
import sh.xana.forum.server.parser.ValidatedUrlException;
import sh.xana.forum.server.spider.config.SpiderConfig;

public class Spider {
  private static final Logger log = LoggerFactory.getLogger(Spider.class);
  private final List<SpiderConfig> configs;

  public Spider() {
    configs = SpiderConfig.load();
  }

  public Stream<Subpage> spiderPage(ParserPage page, byte[] rawPage) {
    try {
      String domain = page.pageUri().getHost();
      SpiderConfig spiderConfig =
          configs.stream()
              .filter(e -> e.domains().contains(domain))
              .findFirst()
              .orElseThrow(
                  () -> new RuntimeException("cannot find spider config for domain " + domain));
      // log.info("found " + spiderConfig.source());

      Document doc = Jsoup.parse(new String(rawPage), page.siteUrl().toString());

      return doc.getElementsByTag("a").stream()
          // must be link
          .filter(ForumStream::anchorIsNavLink)
          .map(ForumStream::getAnchorFullUrl)
          .filter(e -> ForumStream.isBaseUri(e, doc))
          .<Subpage>mapMulti(
              (e, streamMapper) -> {
                Subpage subpage;
                try {
                  subpage = spiderPage(spiderConfig, page, e, doc.baseUri(), true);
                } catch (Exception ex) {
                  throw new SpiderException("Failed to create subpage for " + page.pageId(), ex);
                }

                if (subpage != null) {
                  streamMapper.accept(subpage);
                }
              });
    } catch (Exception e) {
      throw new SpiderException("Failed for " + page.pageId(), e);
    }
  }

  private Subpage spiderPage(
      SpiderConfig config, ParserPage page, String linkRaw, String baseUri, boolean throwErrors) {
    UUID pageId = page.pageId();

    URIBuilder link = null;
    try {
      link = new URIBuilder(linkRaw);
    } catch (URISyntaxException e) {
      // attempt to extract a usable url
      for (Pattern pattern : config.validRegex()) {
        try {
          Matcher matcher = pattern.matcher(linkRaw);
          if (matcher.find()) {
            // must prepend baseUri since the validator only grabs the end path
            String extracted = baseUri + matcher.group();
            link = new URIBuilder(extracted);
            log.warn("Extracted {} from broken {}", extracted, linkRaw);
            break;
          }
        } catch (URISyntaxException e2) {
          // continue...
        }
      }
      if (link == null) {
        log.warn(pageId + " unable to convert to uri " + linkRaw + " - " + e);
        return null;
      }
    }

    if (!linkRaw.startsWith(baseUri)) {
      throw new RuntimeException("link " + linkRaw + " is not starting with baseUri " + baseUri);
    }
    String linkRelative = linkRaw.substring(baseUri.length());
    if (linkRelative.startsWith("/")) {
      throw new RuntimeException(
          "unexpected relative with / relative "
              + linkRelative
              + " baseUri "
              + baseUri
              + " url "
              + linkRaw);
    }

    // url pre-process - don't care about fragment
    String oldFragment = link.getFragment();
    if (oldFragment != null) {
      log.trace("Removing {} fragment {}", link, oldFragment);
      link.setFragment(null);
    }

    // url pre-process - convert double directory path
    String newPath = link.getPath();
    int newPathLen = newPath.length();
    newPath = StringUtils.replace(newPath, "//", "/");
    if (newPath.length() != newPathLen) {
      link.setPath(newPath);
    }

    // url pre-process - handle home page alias

    boolean handled = false;
    if (config.linkMultipage() != null) {
      handled = config.linkMultipage().processLink(link, linkRelative);
    }
    if (!handled) {
      handled = config.linkTopic().processLink(link, linkRelative);
    }
    if (!handled) {
      handled = config.linkForum().processLink(link, linkRelative);
    }
    if (!handled) {
      return null;
    }

    // url pre-process - remove empty ?
    if (link.getQueryParams().isEmpty()) {
      link.clearParameters();
    }

    String linkFinal = link.toString();
    ValidatedUrl validatedUrl;
    try {
      validatedUrl = new ValidatedUrl(linkFinal, baseUri, config.validRegex());
    } catch (ValidatedUrlException e) {
      //      softThrow(pageId + " unable to validate final " + linkFinal, e, throwErrors);
      //      return null;
      throw e;
    }

    return new Subpage("", validatedUrl, page.pageType());
  }

  private void softThrow(@NotNull String message, @Nullable Exception e, boolean throwErrors) {
    if (throwErrors) {
      throw new RuntimeException(message, e);
    } else {
      String exceptionMessage = e != null ? " || " + e.getMessage() : "";
      log.warn(message + exceptionMessage);
    }
  }
}
