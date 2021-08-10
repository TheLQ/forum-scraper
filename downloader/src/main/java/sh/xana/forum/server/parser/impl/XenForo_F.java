package sh.xana.forum.server.parser.impl;

import java.util.Collection;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.EmptyForumException;
import sh.xana.forum.server.parser.ForumUtils;
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
  public boolean detectLoginRequired(SourcePage sourcePage) {
    return false;
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
  public Collection<ValidatedUrl> getPageLinks(SourcePage sourcePage) {
    return ForumUtils.elementToUrl(
        sourcePage.doc().select("a[qid=\"page-nav-other-page\"]"), this, sourcePage);
  }

  @Override
  public Collection<Element> getPostElements(SourcePage sourcePage) {
    return sourcePage.doc().select("article[qid=\"post-text\"]");
  }

  @Override
  public Collection<ValidatedUrl> getSubforumAnchors(SourcePage sourcePage) {
    return ForumUtils.elementToUrl(
        sourcePage.doc().select("a[qid=\"forum-item-title\"]"), this, sourcePage);
  }

  @Override
  public Collection<ValidatedUrl> getTopicAnchors(SourcePage sourcePage) {
    return ForumUtils.elementToUrl(
        sourcePage.doc().select("a[qid=\"thread-item-title\"]"), this, sourcePage);
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
