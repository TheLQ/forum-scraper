package sh.xana.forum.server.parser;

import java.net.URISyntaxException;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.Subpage;
import sh.xana.forum.server.dbutil.PageType;

public abstract class AbstractUrlForum implements AbstractForum {
  private static final Logger log = LoggerFactory.getLogger(AbstractUrlForum.class);
  private final String[] filenameForum;
  private final String[] filenameTopic;
  private final String[] queryKeysForum;
  private final String[] queryKeysTopic;

  public AbstractUrlForum(
      String[] filenameForum,
      String[] filenameThread,
      String[] queryKeysForum,
      String[] queryKeysTopic) {
    this.filenameForum = filenameForum;
    this.filenameTopic = filenameThread;
    this.queryKeysForum = queryKeysForum;
    this.queryKeysTopic = queryKeysTopic;
  }

  @Override
  public @NotNull PageType getPageType(SourcePage sourcePage) {
    return _getPageType(new URIBuilder(sourcePage.pageUri()));
  }

  @NotNull
  private PageType _getPageType(URIBuilder linkBuilder) {
    if (ForumStream.fileIs(linkBuilder, filenameForum)) {
      return PageType.ForumList;
    } else if (ForumStream.fileIs(linkBuilder, filenameTopic)) {
      return PageType.TopicPage;
    } else {
      return PageType.Unknown;
    }
  }

  @Override
  public @NotNull Stream<Subpage> getSubpages(SourcePage sourcePage, PageType currentPageType) {
    Document doc = sourcePage.doc();
    var stream =
        doc.getElementsByTag("a").stream()
            // must be link
            .filter(ForumStream::anchorIsNavLink)
            .map(ForumStream::getAnchorFullUrl)
            .filter(e -> ForumStream.isBaseUri(e, doc))
            .<Subpage>mapMulti(
                (e, streamMapper) -> {
                  Subpage page =
                      getValidLink(
                          e,
                          currentPageType,
                          sourcePage.doc().baseUri(),
                          sourcePage.pageId(),
                          false);
                  if (page != null) {
                    streamMapper.accept(page);
                  }
                });
    return stream;
  }

  @Override
  public @Nullable ParserResult.Subpage getValidLink(
      String linkRaw, PageType currentPageType, String baseUri, UUID pageId, boolean throwErrors) {
    URIBuilder linkBuilder;
    try {
      linkBuilder = new URIBuilder(linkRaw);
    } catch (URISyntaxException e) {
      String msg = pageId + " unable to convert to uri " + linkRaw;
      if (throwErrors) {
        throw new RuntimeException(msg, e);
      } else {
        log.warn(msg + " || " + e.getMessage());
        return null;
      }
    }

    PageType pageType = _getPageType(linkBuilder);
    getValidLink_pre(linkBuilder);
    switch (pageType) {
      case ForumList -> ForumStream.cleanUrl(linkBuilder, queryKeysForum);
      case TopicPage -> ForumStream.cleanUrl(linkBuilder, queryKeysTopic);
      case Unknown -> {
        return null;
      }
    }
    getValidLink_post(linkBuilder);

    String linkFinal = linkBuilder.toString();
    ValidatedUrl validatedUrl;
    try {
      validatedUrl = new ValidatedUrl(linkFinal, baseUri, this);
    } catch (ValidatedUrlException e) {
      String msg = pageId + " unable to validate final " + linkFinal;
      if (throwErrors) {
        throw new RuntimeException(msg, e);
      } else {
        log.warn(msg + " || " + e.getMessage());
        return null;
      }
    }

    return new Subpage("", validatedUrl, pageType);
  }

  protected void getValidLink_pre(URIBuilder uriBuilder) {}

  protected void getValidLink_post(URIBuilder uriBuilder) {}
}
