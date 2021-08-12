package sh.xana.forum.server.parser;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import sh.xana.forum.server.parser.impl.XenForo_F;
import sh.xana.forum.server.parser.impl.vBulletin_IB;

public class PageParser {
  private static final Logger log = LoggerFactory.getLogger(PageParser.class);
  public static final Map<ForumType, AbstractForum> PARSERS =
      Map.of(
          //          ForumType.vBulletin,
          //          new vBulletin(),
          ForumType.vBulletin_IB,
          new vBulletin_IB(),
          ForumType.XenForo_F,
          new XenForo_F(),
          ForumType.ForkBoard,
          new ForkBoard()
          //          ForumType.SMF,
          //          new SMF(),
          );

  private final ServerConfig config;

  public PageParser(ServerConfig config) {
    this.config = config;
  }

  public Path getPagePath(UUID pageId) {
    String pageIdStr = pageId.toString();
    return Path.of(
        config.get(config.ARG_FILE_CACHE),
        "" + pageIdStr.charAt(0),
        "" + pageIdStr.charAt(1),
        pageIdStr + ".response");
  }

  public Path getPageHeaderPath(UUID pageId) {
    String pageIdStr = pageId.toString();
    return Path.of(
        config.get(config.ARG_FILE_CACHE), "" + pageIdStr.charAt(0), pageIdStr + ".headers");
  }

  public ParserResult parsePage(byte[] data, ParserPage page) {
    UUID pageId = page.pageId();
    PageType expectedPageType = page.pageType();
    UUID siteId = page.siteId();
    URI siteUrl = page.siteUrl();
    ForumType forumType = page.forumType();

    if (new String(data).trim().equals("")) {
      earlyThrowIfHttpError(page);
      throw new ParserException("Page is empty", pageId);
    }

    PageType pageType = PageType.Unknown;
    List<ParserEntry> subpages = new ArrayList<>();
    try {
      String rawHtml = new String(data);

      // make sure selected parser can handle this but nothing else can
      AbstractForum parser = PARSERS.get(forumType);
      if (parser == null) {
        throw new NullPointerException("Cannot find parser for " + forumType);
      }
      //      if (parser.detectForumType(rawHtml) == null) {
      //        earlyThrowIfHttpError(page);
      //        throw new ParserException("No parsers handled this file", pageId);
      //      }
      //
      //      for (AbstractForum detectedParser : PARSERS.values()) {
      //        if (detectedParser == parser) {
      //          continue;
      //        }
      //        ForumType detectedForumType = detectedParser.detectForumType(rawHtml);
      //        if (detectedForumType != null) {
      //          earlyThrowIfHttpError(page);
      //          throw new ParserException(
      //              "Expected forumType "
      //                  + forumType
      //                  + " got "
      //                  + detectedForumType
      //                  + " for "
      //                  + parser
      //                  + " and "
      //                  + detectedParser,
      //              pageId);
      //        }
      //      }

      SourcePage sourcePage =
          new SourcePage(
              page.pageId(),
              rawHtml,
              parser.newDocument(rawHtml, siteUrl.toString()),
              page.pageUri());

      parser.preProcessing(sourcePage);

      if (parser.detectLoginRequired(sourcePage)) {
        throw new LoginRequiredException(pageId);
      }
      // Above should of handled HTTP 403 login required, so stop here if nothing has done so
      earlyThrowIfHttpError(page);

      addUrls(parser.getSubforumAnchors(sourcePage), subpages, PageType.ForumList);
      addUrls(parser.getTopicAnchors(sourcePage), subpages, PageType.TopicPage);
      if (!subpages.isEmpty()) {
        pageType = PageType.ForumList;
      }

      // TODO: Iterate through to build post history
      if (!parser.getPostElements(sourcePage).isEmpty()) {
        if (pageType != PageType.Unknown) {
          for (ValidatedUrl url : parser.getSubforumAnchors(sourcePage)) {
            log.info("forum " + url.url);
          }
          for (ValidatedUrl url : parser.getTopicAnchors(sourcePage)) {
            log.info("topic " + url.url);
          }
          for (Element elem : parser.getPostElements(sourcePage)) {
            log.info("post " + elem);
          }
          throw new ParserException("detected both post elements and subforum/topic links", pageId);
        } else {
          pageType = PageType.TopicPage;
        }
      }

      addUrls(parser.getPageLinks(sourcePage), subpages, pageType);

      pageType = parser.postForcePageType(sourcePage, pageType);
      if (pageType != expectedPageType) {
        throw new ParserException(
            "Expected pageType " + expectedPageType + " got " + pageType, pageId);
      }

      ParserResult result = new ParserResult(pageType, forumType, subpages);

      return result;
    } catch (ParserException e) {
      throw e;
    } catch (Exception e) {
      throw new ParserException("Failed inside parser " + forumType, pageId, e);
    }
  }

  private void addUrls(
      Collection<ValidatedUrl> urls, List<ParserEntry> subpages, PageType pageType) {
    for (ValidatedUrl url : urls) {
      ParserEntry parser = new ParserEntry("", url.url, pageType);
      subpages.add(parser);
    }
  }

  private void earlyThrowIfHttpError(ParserPage page) {
    int dlstatusCode = page.dlstatusCode();
    if (dlstatusCode != 200) {
      throw new ParserException("HTTP Error " + dlstatusCode, page.pageId());
    }
  }
}
