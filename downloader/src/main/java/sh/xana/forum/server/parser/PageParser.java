package sh.xana.forum.server.parser;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.ParserEntry;
import sh.xana.forum.server.ServerConfig;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.impl.ForkBoard;
import sh.xana.forum.server.parser.impl.SMF;
import sh.xana.forum.server.parser.impl.XenForo;
import sh.xana.forum.server.parser.impl.vBulletin;

public class PageParser {
  private static final Logger log = LoggerFactory.getLogger(PageParser.class);
  public static final Map<ForumType, AbstractForum> PARSERS =
      Map.of(
          ForumType.vBulletin,
          new vBulletin(),
          ForumType.XenForo,
          new XenForo(),
          ForumType.SMF,
          new SMF(),
          ForumType.ForkBoard,
          new ForkBoard());

  private final ServerConfig config;

  public PageParser(ServerConfig config) {
    this.config = config;
  }

  public Path getPagePath(UUID pageId) {
    String pageIdStr = pageId.toString();
    return Path.of(
        config.get(config.ARG_FILE_CACHE), "" + pageIdStr.charAt(0), pageIdStr + ".response");
  }

  public Path getPageHeaderPath(UUID pageId) {
    String pageIdStr = pageId.toString();
    return Path.of(
        config.get(config.ARG_FILE_CACHE), "" + pageIdStr.charAt(0), pageIdStr + ".headers");
  }

  public ParserResult parsePage(byte[] data, ParserPage page) {
    UUID pageId = page.pageId();
    PageType expectedPageType = page.pageType();
    int dlstatusCode = page.dlstatusCode();
    UUID siteId = page.siteId();
    URI siteUrl = page.siteUrl();
    ForumType forumType = page.forumType();

    if (dlstatusCode != 200) {
      throw new ParserException("HTTP Error " + dlstatusCode, pageId);
    }

    if (new String(data).trim().equals("")) {
      throw new ParserException("Page is empty", pageId);
    }

    PageType pageType = PageType.Unknown;
    List<ParserEntry> subpages = new ArrayList<>();
    try {
      String rawHtml = new String(data);

      // make sure selected parser can handle this but nothing else can
      AbstractForum parser = PARSERS.get(forumType);
      if (parser.detectForumType(rawHtml) == null) {
        throw new ParserException("No parsers handled this file", pageId);
      }

      for (AbstractForum detectedParser : PARSERS.values()) {
        if (detectedParser == parser) {
          continue;
        }
        ForumType detectedForumType = parser.detectForumType(rawHtml);
        if (detectedForumType != null) {
          throw new ParserException(
              "Expected forumType " + forumType + " got " + detectedForumType, pageId);
        }
      }

      SourcePage sourcePage =
          new SourcePage(rawHtml, parser.newDocument(rawHtml, siteUrl.toString()));

      parser.preProcessing(sourcePage);

      if (parser.detectLoginRequired(sourcePage)) {
        throw new ParserException("LoginRequired", pageId);
      }

      addAnchorSubpage(parser.getSubforumAnchors(sourcePage), subpages, PageType.ForumList);
      addAnchorSubpage(parser.getTopicAnchors(sourcePage), subpages, PageType.TopicPage);
      if (!subpages.isEmpty()) {
        pageType = PageType.ForumList;
      }

      // TODO: Iterate through to build post history
      if (!parser.getPostElements(sourcePage).isEmpty()) {
        if (pageType != PageType.Unknown) {
          for (Element elem : parser.getSubforumAnchors(sourcePage)) {
            log.info("forum " + elem);
          }
          for (Element elem : parser.getTopicAnchors(sourcePage)) {
            log.info("topic " + elem);
          }
          for (Element elem : parser.getPostElements(sourcePage)) {
            log.info("post " + elem);
          }
          throw new ParserException("detected both post elements and subforum/topic links", pageId);
        } else {
          pageType = PageType.TopicPage;
        }
      }

      addAnchorSubpage(parser.getPageLinks(sourcePage), subpages, pageType);

      pageType = parser.postForcePageType(sourcePage, pageType);
      if (pageType != expectedPageType) {
        throw new ParserException(
            "Expected pageType " + expectedPageType + " got " + pageType, pageId);
      }

      ParserResult result = new ParserResult(pageType, forumType, subpages);
      parser.postProcessing(sourcePage, result);

      for (var subpage : result.subpages()) {
        validateUrl(subpage.url(), siteUrl.toString(), forumType);
      }

      return result;

    } catch (ParserException e) {
      throw e;
    } catch (Exception e) {
      throw new ParserException("Failed inside parser " + forumType, pageId, e);
    }
  }

  private void addAnchorSubpage(
      Collection<Element> elements, List<ParserEntry> subpages, PageType pageType) {
    for (Element element : elements) {
      if (ForumUtils.anchorIsNotNavLink(element)) {
        continue;
      }

      String url = element.absUrl("href");
      if (StringUtils.isBlank(url)) {
        throw new RuntimeException("href blank in " + element.outerHtml());
      }

      ParserEntry parser = new ParserEntry(element.text(), url.trim(), pageType);
      subpages.add(parser);
    }
  }

  public static void validateUrl(String pageUrl, String baseUrl, ForumType forumType) {
    AbstractForum parser = PageParser.PARSERS.get(forumType);

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
    String subUrl = pageUrl.substring(baseUrl.length());
    if (subUrl.startsWith("/")) {
      throw new RuntimeException(
          "Unexpected starting / for " + subUrl + " with page " + pageUrl + " base " + baseUrl);
    }

    // strip broken ascii so we can use limited capture groups
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
          "Failed to match " + subUrl + " from " + pageUrl + " in " + forumType);
    }
  }

  public static class ParserException extends RuntimeException {

    public ParserException(String message, UUID pageId) {
      this(message, pageId, null);
    }

    public ParserException(String message, UUID pageId, Throwable cause) {
      super(message + " Page " + pageId, cause);
    }
  }
}
