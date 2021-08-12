package sh.xana.forum.server.parser.impl;

import java.util.Collection;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.EmptyForumException;
import sh.xana.forum.server.parser.ForumUtils;
import sh.xana.forum.server.parser.SourcePage;
import sh.xana.forum.server.parser.ValidatedUrl;

public class ForkBoard implements AbstractForum {

  public ForumType detectForumType(String rawHtml) {
    if (rawHtml.contains("Powered by ForkBoard")) {
      return ForumType.ForkBoard;
    } else {
      return null;
    }
  }

  public boolean detectLoginRequired(SourcePage sourcePage) {
    return false;
  }

  @Override
  public void preProcessing(SourcePage sourcePage) {
    Elements messages = sourcePage.doc().select(".section_header_description");
    if (messages.size() >= 2) {
      Element lastMessage = messages.get(1);
      if (lastMessage.text().equals("Threads: 1-0 of 0")) {
        throw new EmptyForumException(sourcePage.pageId());
      }
    }
  }

  public @Nonnull Collection<ValidatedUrl> getPageLinks(SourcePage sourcePage) {
    return ForumUtils.elementToUrl(sourcePage.doc().select(".page_skip"), this, sourcePage);
  }

  public @Nonnull Collection<Element> getPostElements(SourcePage sourcePage) {
    return sourcePage.doc().select(".post_container");
  }

  public @Nonnull Collection<ValidatedUrl> getSubforumAnchors(SourcePage sourcePage) {
    return ForumUtils.elementToUrl(
        sourcePage.doc().select(".child_section .child_section_title a"), this, sourcePage);
  }

  public @Nonnull Collection<ValidatedUrl> getTopicAnchors(SourcePage sourcePage) {
    return ForumUtils.elementToUrl(
        sourcePage.doc().select(".thread_details div:first-child a"), this, sourcePage);
  }

  public PageType postForcePageType(SourcePage sourcePage, PageType currentType) {
    if (currentType == PageType.Unknown
        && sourcePage.rawHtml().contains("<a href=\"/post_new.php?")) {
      // handle strange edge case where deleted users or posts can leave a topic with 0 posts
      // Then the parser thinks the page type is unknown because there are no post elements
      return PageType.TopicPage;
    } else {
      return currentType;
    }
  }

  public void postProcessing(SourcePage sourcePage, ParserResult result) {}

  private static final Pattern[] PATTERN_URI =
      new Pattern[] {
        // home page
        Pattern.compile("home.php"),
        // forum/?page=3
        Pattern.compile("[a-z0-9\\-]+/(\\?page=[0-9]+)?"),
        // forum/!434-my-topic.html?page=4
        Pattern.compile("[a-z0-9\\-]+/![0-9]+-[a-zA-Z0-9\\-]*.html(\\?page=[0-9]+)?"),
      };

  public Pattern[] validateUrl() {
    return PATTERN_URI;
  }
}
