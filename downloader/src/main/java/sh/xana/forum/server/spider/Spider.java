package sh.xana.forum.server.spider;

import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
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
import sh.xana.forum.server.spider.config.DirectoryLinkHandler;
import sh.xana.forum.server.spider.config.LinkHandler;
import sh.xana.forum.server.spider.config.QueryKeyLinkHandler;
import sh.xana.forum.server.spider.config.SpiderConfig;

public class Spider {
  private static final Logger log = LoggerFactory.getLogger(Spider.class);
  private final List<SpiderConfig> configs;

  public Spider() {
    configs = SpiderConfig.load();
  }

  public void spiderPage(ParserPage page, byte[] rawPage) {
    try {
      SpiderConfig spiderConfig =
          configs.stream()
              .filter(e -> e.domains().contains(page.pageUri().getHost()))
              .findFirst()
              .orElseThrow(() -> new RuntimeException("cannot find spider config"));

      Document doc = Jsoup.parse(new String(rawPage), page.siteUrl().toString());

      doc.getElementsByTag("a").stream()
          // must be link
          .filter(ForumStream::anchorIsNavLink)
          .map(ForumStream::getAnchorFullUrl)
          .filter(e -> ForumStream.isBaseUri(e, doc))
          .<Subpage>mapMulti(
              (e, streamMapper) -> {
                Subpage subpage = spiderPage(spiderConfig, page, e, doc.baseUri(), true);
                if (subpage != null) {
                  streamMapper.accept(subpage);
                }
              });
    } catch (SpiderException e) {
      throw e;
    } catch (Exception e) {
      throw new SpiderException("Failed for " + page.pageId());
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
        softThrow(pageId + " unable to convert to uri " + linkRaw, e, throwErrors);
        return null;
      }
    }

    if (!linkRaw.startsWith(baseUri)) {
      throw new RuntimeException("link " + linkRaw + " is not starting with baseUri " + baseUri);
    }
    String linkRelative = linkRaw.substring(baseUri.length());

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

    boolean handled = processLink(config.linkMultipage(), link, linkRelative);
    if (!handled) {
      handled = processLink(config.linkTopic(), link, linkRelative);
    }
    if (!handled) {
      handled = processLink(config.linkForum(), link, linkRelative);
    }
    if (!handled) {
      log.trace("{} link not handled {}", pageId, linkRaw);
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

  private boolean processLink(LinkHandler config, URIBuilder link, String linkRelative) {
    if (config instanceof DirectoryLinkHandler e) {
      return processLinksDirectory(e, link, linkRelative);
    } else if (config instanceof QueryKeyLinkHandler e) {
      return processLinksQueryKey(e, link, linkRelative);
    } else {
      return false;
    }
    //    return switch(linkHandler) {
    //      case DirectoryLinkHandler e -> processLinksDirectory(linkBuilder, e);
    //      case QueryKeyLinkHandler f -> processLinksQueryKey(linkBuilder, f);
    //      case null -> false;
    //      default -> throw new IllegalStateException("Unexpected value: " + linkHandler);
    //    };
  }

  private boolean processLinksDirectory(
      DirectoryLinkHandler config, URIBuilder link, String linkRelative) {
    return true;
  }

  private boolean processLinksQueryKey(
      QueryKeyLinkHandler config, URIBuilder link, String linkRelative) {
    if (linkRelative.startsWith(config.pathEquals())) {
      return false;
    }

    // strip any args that are not in our list
    List<NameValuePair> actualArgs = link.getQueryParams();
    int actualArgsOrigSize = actualArgs.size();
    actualArgs.removeIf(
        e -> {
          boolean doRemove = !config.allowedKeys().contains(e.getName());
          if (doRemove) {
            log.trace("Removing {} query {}={}", link, e.getName(), e.getValue());
          }
          return doRemove;
        });
    if (actualArgs.size() == 0) {
      // remove empty ?
      link.clearParameters();
    } else if (actualArgs.size() != actualArgsOrigSize) {
      link.setParameters(actualArgs);
    }

    // remove unnessesary /
    //    String newUri = uriBuilder.toString();
    //    if (newUri.endsWith("/")) {
    //      newUri = newUri.substring(0, newUri.length() - 1);
    //    }
    return true;
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