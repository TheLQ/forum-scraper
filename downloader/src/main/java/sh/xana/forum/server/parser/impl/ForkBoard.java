package sh.xana.forum.server.parser.impl;

import java.util.Collection;
import javax.annotation.Nonnull;
import org.jsoup.nodes.Element;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.SourcePage;

public class ForkBoard implements AbstractForum {

  @Override
  public ForumType detectForumType(String rawHtml) {
    if (rawHtml.contains("Powered by ForkBoard")) {
      return ForumType.ForkBoard;
    } else {
      return null;
    }
  }

  @Override
  public boolean detectLoginRequired(SourcePage sourcePage) {
    return false;
  }

  @Override
  public @Nonnull Collection<Element> getPageLinks(SourcePage sourcePage) {
    return sourcePage.doc().select(".page_skip");
  }

  @Override
  public @Nonnull Collection<Element> getPostElements(SourcePage sourcePage) {
    return sourcePage.doc().select(".post_container");
  }

  @Override
  public @Nonnull Collection<Element> getSubforumAnchors(SourcePage sourcePage) {
    return sourcePage.doc().select(".child_section .child_section_title a");
  }

  @Override
  public @Nonnull Collection<Element> getTopicAnchors(SourcePage sourcePage) {
    return sourcePage.doc().select(".thread_details div:first-child a");
  }

  @Override
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

  @Override
  public void postProcessing(SourcePage sourcePage, ParserResult result) {}
}
