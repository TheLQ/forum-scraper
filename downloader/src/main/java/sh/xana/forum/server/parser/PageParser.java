package sh.xana.forum.server.parser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.ParserEntry;
import sh.xana.forum.server.ServerConfig;
import sh.xana.forum.server.dbutil.DatabaseStorage.ForumType;
import sh.xana.forum.server.dbutil.DatabaseStorage.PageType;
import sh.xana.forum.server.parser.impl.ForkBoard;
import sh.xana.forum.server.parser.impl.SMF;
import sh.xana.forum.server.parser.impl.XenForo_F;
import sh.xana.forum.server.parser.impl.vBulletin;

public class PageParser {
  private static final Logger log = LoggerFactory.getLogger(PageParser.class);
  private static final AbstractForum[] PARSERS =
      new AbstractForum[] {new vBulletin(), new XenForo_F(), new SMF(), new ForkBoard()};

  private final ServerConfig config;

  public PageParser(ServerConfig config) {
    this.config = config;
  }

  public Path getPagePath(UUID pageId) {
    return Path.of(config.get(config.ARG_FILE_CACHE), pageId + ".response");
  }

  public ParserResult parsePage(byte[] data, UUID pageId, String baseUrl) {
    try {
      String rawHtml = new String(data);

      PageType pageType = PageType.Unknown;
      List<ParserEntry> subpages = new ArrayList<>();
      for (AbstractForum parser : PARSERS) {
        ForumType forumType = parser.detectForumType(rawHtml);
        if (forumType == null) {
          continue;
        }

        SourcePage sourcePage = new SourcePage(rawHtml, parser.newDocument(rawHtml, baseUrl));

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
            throw new ParserException(
                "detected both post elements and subforum/topic links", pageId);
          } else {
            pageType = PageType.TopicPage;
          }
        }

        addAnchorSubpage(parser.getPageLinks(sourcePage), subpages, pageType);

        ParserResult result = new ParserResult(false, pageType, forumType, subpages);
        parser.postProcessing(sourcePage, result);
        return result;
      }
    } catch (ParserException e) {
      throw e;
    } catch (Exception e) {
      throw new ParserException("Failed inside parser", pageId);
    }
    throw new ParserException("No parsers handled this file", pageId);
  }

  private void addAnchorSubpage(
      Collection<Element> elements, List<ParserEntry> subpages, PageType pageType) {
    for (Element element : elements) {
      ParserEntry parser = new ParserEntry(element.text(), element.absUrl("href"), pageType);
      subpages.add(parser);
    }
  }

  public static class ParserException extends RuntimeException {

    public ParserException(String message, UUID pageId) {
      this(message, null, pageId);
    }

    public ParserException(String message, Throwable cause, UUID pageId) {
      super(message + " Page " + pageId, cause);
    }
  }
}
