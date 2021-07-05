package sh.xana.forum.server.parser.impl;

import java.util.Collection;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.Soft500Exception;
import sh.xana.forum.server.parser.SourcePage;

public class XenForo implements AbstractForum {
  private static final Logger log = LoggerFactory.getLogger(XenForo.class);

  @Override
  public @Nullable ForumType detectForumType(String rawHtml) {
    if (rawHtml.contains("<html id=\"XF\"") || rawHtml.contains("<html id=\"XenForo\"")) {
      return ForumType.XenForo;
    } else {
      return null;
    }
  }

  @Override
  public boolean detectLoginRequired(SourcePage sourcePage) {
    return false;
  }

  @Override
  public void preProcessing(SourcePage sourcePage) {
    Elements messages = sourcePage.doc().select(".blockMessage");
    //    log.info("size " + messages.size());
    //    for (Element elem : messages) {
    //        log.info("elem " + elem.outerHtml());
    //    }
    if (messages.size() != 0) {
      String lastMessage = messages.get(messages.size() - 1).text().trim();
      if (lastMessage.equals(
          "Something went wrong. Please try again or contact the administrator.")) {
        throw new Soft500Exception("Something went wrong message");
      }
    }
  }

  @Override
  public @NotNull Collection<Element> getPageLinks(SourcePage sourcePage) {
    return sourcePage.doc().select(".pageNav-page a, .PageNav a");
  }

  @Override
  public @NotNull Collection<Element> getPostElements(SourcePage sourcePage) {
    return sourcePage.doc().select(".messageContent, .message-content");
  }

  @Override
  public @NotNull Collection<Element> getSubforumAnchors(SourcePage sourcePage) {
    return sourcePage.doc().select(".node-title a, .nodeTitle a");
  }

  @Override
  public @NotNull Collection<Element> getTopicAnchors(SourcePage sourcePage) {
    // return sourcePage.doc().select("a[qid=\"thread-item-title\"]");
    return sourcePage.doc().select(".title a, .structItem-title a");
  }

  @Override
  public void postProcessing(SourcePage sourcePage, ParserResult result) {}
}
