package sh.xana.forum.server.parser;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.Subpage;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.dbutil.ParserPage;
import sh.xana.forum.server.parser.impl.vBulletin_Url1;
import sh.xana.forum.server.parser.impl.vBulletin_Url2;

public class PageParser {
  private static final Logger log = LoggerFactory.getLogger(PageParser.class);
  public static final Map<ForumType, AbstractForum> PARSERS =
      Map.of(
          //          ForumType.vBulletin,
          //          new vBulletin(),
          //          ForumType.vBulletin_IB,
          //          new vBulletin_IB(),
          ForumType.vBulletin_Url1,
          new vBulletin_Url1(),
          ForumType.vBulletin_Url2,
          new vBulletin_Url2()
          //          ForumType.XenForo_F,
          //          new XenForo_F(),
          //          ForumType.ForkBoard,
          //          new ForkBoard()
          //          ForumType.SMF,
          //          new SMF(),
          );

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

    AbstractForum parser = null;
    List<Subpage> subpages = new ArrayList<>();
    try {
      String rawHtml = new String(data);

      // make sure selected parser can handle this but nothing else can
      parser = PARSERS.get(forumType);
      if (parser == null) {
        throw new NullPointerException("Cannot find parser for " + forumType);
      }
      //       log.trace("Type {} parser {}", forumType, parser.getClass());
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

      // Above should of handled HTTP 403 login required, so stop here if nothing has done so
      earlyThrowIfHttpError(page);

      PageType pageType = parser.getPageType(sourcePage);
      if (pageType == PageType.Unknown) {
        throw new ParserException("Unknown page", pageId);
      }

      parser.getSubpages(sourcePage, pageType).forEach(subpages::add);

      ParserResult result = new ParserResult(pageType, forumType, subpages);
      return result;
    } catch (ParserException e) {
      throw e;
    } catch (Exception e) {
      throw new ParserException("Failed inside parser " + forumType, pageId, e);
    } finally {
      if (parser != null) {
        parser.pageDone();
      }
    }
  }

  private void earlyThrowIfHttpError(ParserPage page) {
    int dlstatusCode = page.dlstatusCode();
    if (dlstatusCode != 200) {
      throw new ParserException("HTTP Error " + dlstatusCode, page.pageId());
    }
  }
}
