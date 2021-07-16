package sh.xana.forum.server.parser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
import sh.xana.forum.server.parser.impl.ForkBoard;
import sh.xana.forum.server.parser.impl.SMF;
import sh.xana.forum.server.parser.impl.XenForo;
import sh.xana.forum.server.parser.impl.vBulletin;

public class PageParser {
  private static final Logger log = LoggerFactory.getLogger(PageParser.class);
  private static final AbstractForum[] PARSERS =
      new AbstractForum[] {new vBulletin(), new XenForo(), new SMF(), new ForkBoard()};

  private final ServerConfig config;

  public PageParser(ServerConfig config) {
    this.config = config;
  }

  public Path getPagePath(UUID pageId) {
    return Path.of(config.get(config.ARG_FILE_CACHE), pageId + ".response");
  }

  public ParserResult parsePage(byte[] data, UUID pageId, String baseUrl) {
    if (new String(data).trim().equals("")) {
      log.warn("Page " + pageId + " is empty");
    }

    ForumType forumType = null;
    try {
      String rawHtml = new String(data);

      PageType pageType = PageType.Unknown;
      List<ParserEntry> subpages = new ArrayList<>();
      for (AbstractForum parser : PARSERS) {
        forumType = parser.detectForumType(rawHtml);
        if (forumType == null) {
          continue;
        }

        SourcePage sourcePage = new SourcePage(rawHtml, parser.newDocument(rawHtml, baseUrl));

        parser.preProcessing(sourcePage);

        if (parser.detectLoginRequired(sourcePage)) {
          return new ParserResult(true, pageType, forumType, subpages);
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
            throw new ParserException(
                "detected both post elements and subforum/topic links", pageId);
          } else {
            pageType = PageType.TopicPage;
          }
        }

        addAnchorSubpage(parser.getPageLinks(sourcePage), subpages, pageType);

        pageType = parser.postForcePageType(sourcePage, pageType);

        ParserResult result = new ParserResult(false, pageType, forumType, subpages);
        parser.postProcessing(sourcePage, result);
        return result;
      }
    } catch (ParserException e) {
      throw e;
    } catch (Exception e) {
      throw new ParserException("Failed inside parser " + forumType, pageId, e);
    }
    throw new ParserException("No parsers handled this file", pageId);
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

  public static class ParserException extends RuntimeException {

    public ParserException(String message, UUID pageId) {
      this(message, pageId, null);
    }

    public ParserException(String message, UUID pageId, Throwable cause) {
      super(message + " Page " + pageId, cause);
    }
  }
}
