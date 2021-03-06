package sh.xana.forum.server.spider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.Subpage;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.ForumStream;
import sh.xana.forum.server.spider.LinkHandler.Result;

public class Spider {
  private static final Logger log = LoggerFactory.getLogger(Spider.class);
  public static boolean PROD_THROW_ON_REGEX_FAIL = false;
  private final List<SpiderConfig> configs;

  public Spider() {
    configs = SpiderConfig.load();
  }

  public Document loadPage(String data, String baseUri) throws IOException {
    return Jsoup.parse(data, baseUri, Parser.htmlParser());
  }

  public Document loadPage(String data, String baseUri, Parser parser) throws IOException {
    return Jsoup.parse(data, baseUri, Parser.htmlParser());
  }

  /** Use Jsoup's build while reading perf optimization. Actually the slowest part */
  public Document loadPage(Path path, String baseUri) throws IOException {
    return loadPage(path, baseUri, Parser.htmlParser());
  }

  public Document loadPage(Path path, String baseUri, Parser parser) throws IOException {
    return Jsoup.parse(Files.readString(path), baseUri, parser);
    //    return Jsoup.parse(new BufferedInputStream(Files.newInputStream(path,
    // StandardOpenOption.READ)), null, baseUri);
    //     return Jsoup.parse(path.toFile(), StandardCharsets.UTF_8.toString(), baseUri);
  }

  @Deprecated
  public Stream<Subpage> spiderPage(ParserPage page, byte[] rawPage) {
    return spiderPage(page, Jsoup.parse(new String(rawPage), page.siteUrl().toString()));
  }

  public Stream<Subpage> spiderPage(ParserPage page, Document doc) {
    try {
      String domain = page.pageUri().getHost();
      SpiderConfig spiderConfig = SpiderConfig.findForDomain(configs, domain);
      // log.info("found " + spiderConfig.source());

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
      log.warn(
          "{} unable to convert to uri {} - {}",
          pageId,
          Utils.newlinePlaceholder(linkRaw),
          Utils.newlinePlaceholder(e.toString()));
      return null;
    }
  }

  private Subpage linkToSubpage(SpiderConfig config, ParserPage page, LinkBuilder link) {
    if (config.excludePathStart() != null) {
      for (String relativeBlock : config.excludePathStart()) {
        if (link.relativeLink().startsWith(relativeBlock)) {
          return null;
        }
      }
    }

    log.trace("{} start", link);
    PageType pageType = page.pageType();
    Result handled;

    log.trace("{} trying topic", link);
    handled = config.linkTopic().processLink(link);
    if (handled == Result.MATCHED) {
      pageType = PageType.TopicPage;
    }

    if (handled == Result.FAILED) {
      log.trace("{} trying forum", link);
      handled = config.linkForum().processLink(link);
      if (handled == Result.MATCHED) {
        pageType = PageType.ForumList;
      }
    }

    // TODO: Very specific to DirectoryLinkHandler
    if (handled == Result.MATCHED_PARTIAL && config.linkMultipage() != null) {
      handled = config.linkMultipage().processLink(link);
    }

    if (handled == Result.FAILED) {
      log.debug("{} failed to process link", link);
      return null;
    }

    try {
      link.validate(config.validRegex());
    } catch (LinkValidationException e) {
      // Some urls pass initial validation but are ultimately invalid. In prod mode, ignore
      if (PROD_THROW_ON_REGEX_FAIL) {
        throw e;
      } else {
        log.warn("{} Failed validation", link);
        return null;
      }
    }

    return new Subpage(link.fullLink(), pageType);
  }
}
