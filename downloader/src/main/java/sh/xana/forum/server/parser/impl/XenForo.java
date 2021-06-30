package sh.xana.forum.server.parser.impl;

import java.util.Collection;
import javax.annotation.Nullable;
import org.jsoup.nodes.Element;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.SourcePage;

public class XenForo implements AbstractForum {
  @Override
  public @Nullable ForumType detectForumType(String rawHtml) {
    if (rawHtml.contains("<html id=\"XF\"")) {
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
  public Collection<Element> getPageLinks(SourcePage sourcePage) {
    return sourcePage.doc().select(".pageNav-page a, .PageNav a");
  }

  @Override
  public Collection<Element> getPostElements(SourcePage sourcePage) {
    return sourcePage.doc().select(".messageContent, .message-content");
  }

  @Override
  public Collection<Element> getSubforumAnchors(SourcePage sourcePage) {
    return sourcePage.doc().select(".node-title a, .nodeTitle a");
  }

  @Override
  public Collection<Element> getTopicAnchors(SourcePage sourcePage) {
    //return sourcePage.doc().select("a[qid=\"thread-item-title\"]");
    return sourcePage.doc().select(".title a, .structItem-title a");
  }

  @Override
  public void postProcessing(SourcePage sourcePage, ParserResult result) {}
}
