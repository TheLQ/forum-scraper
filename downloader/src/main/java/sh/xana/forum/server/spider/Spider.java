package sh.xana.forum.server.spider;

import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.Subpage;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.ForumStream;
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
                LinkBuilder link = rawToUsableLink(spiderConfig, page.pageId(), e, doc.baseUri());
                if (link == null) {
                  return;
                }

                Subpage subpage;
                try {
                  subpage = linkToSubpage(spiderConfig, page, link);
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

  private LinkBuilder rawToUsableLink(
      SpiderConfig config, UUID pageId, String linkRaw, String baseUri) {
    try {
      return new LinkBuilder(linkRaw, baseUri);
    } catch (URISyntaxException e) {
      // attempt to extract a usable url
      for (Pattern pattern : config.validRegex()) {
        try {
          Matcher matcher = pattern.matcher(linkRaw);
          if (matcher.find()) {
            // must prepend baseUri since the validator only grabs the end path
            String extracted = baseUri + matcher.group();
            LinkBuilder link = new LinkBuilder(linkRaw, baseUri);
            log.warn("Extracted {} from broken {}", extracted, linkRaw);
            return link;
          }
        } catch (URISyntaxException e2) {
          // continue...
        }
      }

      // loop ended, log failure
      log.warn(pageId + " unable to convert to uri " + linkRaw + " - " + e);
      return null;
    }
  }

  private Subpage linkToSubpage(SpiderConfig config, ParserPage page, LinkBuilder link) {

    PageType pageType = page.pageType();
    boolean handled = false;
    if (config.linkMultipage() != null) {
      handled = config.linkMultipage().processLink(link);
    }
    if (!handled) {
      handled = config.linkTopic().processLink(link);
      if (handled) {
        pageType = PageType.TopicPage;
      }
    }
    if (!handled) {
      handled = config.linkForum().processLink(link);
      if (handled) {
        pageType = PageType.ForumList;
      }
    }
    if (!handled) {
      return null;
    }

    link.validate(config.validRegex());

    return new Subpage(link.fullLink(), pageType);
  }
}
