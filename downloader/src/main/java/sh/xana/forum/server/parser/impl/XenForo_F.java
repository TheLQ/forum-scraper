package sh.xana.forum.server.parser.impl;

import java.util.Collection;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.Subpage;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.EmptyForumException;
import sh.xana.forum.server.parser.ForumStream;
import sh.xana.forum.server.parser.Soft500Exception;
import sh.xana.forum.server.parser.SourcePage;
import sh.xana.forum.server.parser.ValidatedUrl;

public class XenForo_F implements AbstractForum {

  @Override
  public @Nullable ForumType detectForumType(String rawHtml) {
    if (rawHtml.contains("<html id=\"XF\"")) {
      return ForumType.XenForo_F;
    } else {
      return null;
    }
  }

  @Override
  public void preProcessing(SourcePage sourcePage) {
    Elements messages = sourcePage.doc().select(".blockMessage, .structItem-cell");
    //    log.info("size " + messages.size());
    //    for (Element elem : messages) {
    //        log.info("elem " + elem.outerHtml());
    //    }
    if (messages.size() != 0) {
      String lastMessage = messages.get(messages.size() - 1).text().trim();
      if (lastMessage.equals(
          "Something went wrong. Please try again or contact the administrator.")) {
        throw new Soft500Exception(sourcePage.pageId());
      }

      if (lastMessage.equals("There are no threads in this forum.")) {
        throw new EmptyForumException(sourcePage.pageId());
      }
    }
  }

  @Override
  public @NotNull PageType getPageType(SourcePage sourcePage) {
    return _getPageType(sourcePage.pageUri().toString(), sourcePage.doc().baseUri());
  }

  public PageType _getPageType(String fullUri, String baseUri) {
    String relUri = fullUri.substring(baseUri.length());
    if (relUri.startsWith("forums/")) {
      return PageType.ForumList;
    } else if (relUri.startsWith("threads/")) {
      return PageType.TopicPage;
    } else {
      return PageType.Unknown;
    }
  }

  @Override
  public @NotNull Stream<Subpage> getSubpages(SourcePage sourcePage, PageType currentPageType) {
    return sourcePage
        .doc()
        .select(
            "a[qid=\"page-nav-other-page\"], a[qid=\"forum-item-title\"], a[qid=\"thread-item-title\"]")
        .stream()
        .map(ForumStream::getAnchorFullUrl)
        // TODO: COPY PASTE
        .<Subpage>mapMulti(
            (e, streamMapper) -> {
              Subpage page =
                  getValidLink(
                      e, currentPageType, sourcePage.doc().baseUri(), sourcePage.pageId(), false);
              if (page != null) {
                streamMapper.accept(page);
              }
            });
  }

  @Override
  public @Nullable ParserResult.Subpage getValidLink(
      String link, PageType currentPageType, String baseUri, UUID pageId, boolean throwErrors) {
    return new Subpage("", new ValidatedUrl(link, baseUri, this), _getPageType(link, baseUri));
  }

  @Override
  public @NotNull Collection<Element> getPostElements(SourcePage sourcePage) {
    return sourcePage.doc().select("article[qid=\"post-text\"]");
  }

  private static final String PATTERN_TOPIC_TPL = "[a-zA-Z0-9\\-%_\\(\\)!\\*\\?']+";
  private static final Pattern[] PATTERNS =
      new Pattern[] {
        // forums/general.4/page-55
        Pattern.compile("forums/[a-zA-Z0-9\\-%]+\\.[0-9]+/(page-[0-9]+)?"),
        // threads/my-topic.234/page-163
        // threads/234/page-163 (if thread title is just a period...)
        Pattern.compile("threads/([a-zA-Z0-9\\-%_]+\\.)?[0-9]+/(page-[0-9]+)?"),
        // Plain /forums/ homepage
        Pattern.compile("forums/")
      };

  @Override
  public Pattern[] validateUrl() {
    return PATTERNS;
  }
}
