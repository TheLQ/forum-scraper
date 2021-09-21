package sh.xana.forum.server.parser;

import java.net.URISyntaxException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    URIBuilder linkBuilder = null;
    try {
      linkBuilder = new URIBuilder(linkRaw);
    } catch (URISyntaxException e) {
      // attempt to extract a usable url
      for (Pattern pattern : validateUrl()) {
        try {
          Matcher matcher = pattern.matcher(linkRaw);
          if (matcher.find()) {
            // must prepend baseUri since the validator only grabs the end path
            String extracted = baseUri + matcher.group();
            linkBuilder = new URIBuilder(extracted);
            log.warn("Extracted {} from broken {}", extracted, linkRaw);
            break;
          }
        } catch (URISyntaxException e2) {
          // continue...
        }
      }
      if (linkBuilder == null) {
        softThrow(pageId + " unable to convert to uri " + linkRaw, e, throwErrors);
        return null;
      }
    }

    PageType pageType = _getPageType(linkBuilder);
    if (getValidLink_pre(baseUri, linkBuilder) == ProcessState.STOP) {
      return null;
    }
    switch (pageType) {
      case ForumList -> ForumStream.cleanUrl(linkBuilder, queryKeysForum);
      case TopicPage -> ForumStream.cleanUrl(linkBuilder, queryKeysTopic);
      case Unknown -> {
        return null;
      }
    }
    if (getValidLink_post(baseUri, linkBuilder) == ProcessState.STOP) {
      return null;
    }

    String linkFinal = linkBuilder.toString();
    ValidatedUrl validatedUrl;
    try {
      validatedUrl = new ValidatedUrl(linkFinal, baseUri, this);
    } catch (ValidatedUrlException e) {
      softThrow(pageId + " unable to validate final " + linkFinal, e, throwErrors);
      return null;
    }

    return new Subpage("", validatedUrl, pageType);
  }

  @NotNull
  protected ProcessState getValidLink_pre(String baseUri, URIBuilder uriBuilder) {
    return ProcessState.CONTINUE;
  }

  @NotNull
  protected ProcessState getValidLink_post(String baseUri, URIBuilder uriBuilder) {
    return ProcessState.CONTINUE;
  }

  private void softThrow(String message, Exception e, boolean throwErrors) {
    if (throwErrors) {
      throw new RuntimeException(message, e);
    } else {
      log.warn(message + " || " + e.getMessage());
    }
  }

  protected enum ProcessState {
    CONTINUE,
    STOP
  }
}
