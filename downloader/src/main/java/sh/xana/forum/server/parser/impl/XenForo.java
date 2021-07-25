package sh.xana.forum.server.parser.impl;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.parser.EmptyForumException;
import sh.xana.forum.server.parser.Soft500Exception;
import sh.xana.forum.server.parser.SourcePage;

public class XenForo /*implements AbstractForum*/ {
  private static final Logger log = LoggerFactory.getLogger(XenForo.class);

  public @Nullable ForumType detectForumType(String rawHtml) {
    if (rawHtml.contains("<html id=\"XF\"") || rawHtml.contains("<html id=\"XenForo\"")) {
      return ForumType.XenForo;
    } else {
      return null;
    }
  }

  public boolean detectLoginRequired(SourcePage sourcePage) {
    return false;
  }

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

  public @NotNull Collection<Element> getPageLinks(SourcePage sourcePage) {
    return sourcePage.doc().select(".pageNav-page a, .PageNav a");
  }

  public @NotNull Collection<Element> getPostElements(SourcePage sourcePage) {
    return sourcePage.doc().select(".messageContent, .message-content");
  }

  public @NotNull Collection<Element> getSubforumAnchors(SourcePage sourcePage) {
    return sourcePage.doc().select(".node-title a, .nodeTitle a");
  }

  public @NotNull Collection<Element> getTopicAnchors(SourcePage sourcePage) {
    // return sourcePage.doc().select("a[qid=\"thread-item-title\"]");
    Elements elements = sourcePage.doc().select(".title a, .structItem-title a");
    return elements.stream()
        // filter "Similar Threads" widget on topics
        .filter(
            e -> {
              for (Element parent : e.parents()) {
                if (parent.hasAttr("data-widget-key")
                    && parent
                        .attr("data-widget-key")
                        .equals("xfes_thread_view_below_quick_reply_similar_threads")) {
                  return false;
                } else if (parent.hasClass("similarThreads")) {
                  return false;
                }
              }
              return true;
            })
        .collect(Collectors.toList());
  }

  public void postProcessing(SourcePage sourcePage, ParserResult result) {
    // filter "forum" that's really a link to a setup guide
    result
        .subpages()
        .removeIf(e -> e.url().endsWith("/PicturePerfect/") || e.url().endsWith("/newsletter/"));
  }

  private static final Pattern[] PATTERN_URI =
      new Pattern[] {
        // forums/general.4/page-55
        // audio for strange site 20e59055-7511-4422-a245-16c1246432d9
        // link-forums shortcuts spread out on homepage...
        Pattern.compile(
            "(categories|link-forums|forums|audio)/[a-zA-Z0-9\\-%]+\\.[0-9]+/(page-[0-9]+)?"),
        // threads/my-topic.234/page-163
        Pattern.compile("threads/[a-zA-Z0-9\\-%_]+\\.[0-9]+/(page-[0-9]+)?"),
        // threads/my-topic.234/page-163
        Pattern.compile("threads/[0-9]+/(page-[0-9]+)?"),
        // Plain /forums/ link on special XenForo platform
        Pattern.compile("forums/")
      };

  public Pattern[] validateUrl() {
    return PATTERN_URI;
  }
}
