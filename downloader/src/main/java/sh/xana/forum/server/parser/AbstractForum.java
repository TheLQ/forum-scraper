package sh.xana.forum.server.parser;

import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.server.dbutil.ForumType;

public interface AbstractForum {
  @Nullable
  ForumType detectForumType(String rawHtml);

  default Document newDocument(String rawHtml, String baseUrl) {
    return Jsoup.parse(rawHtml, baseUrl);
  }

  boolean detectLoginRequired(SourcePage sourcePage);

  @Nonnull
  Collection<Element> getPageLinks(SourcePage sourcePage);

  @Nonnull
  Collection<Element> getPostElements(SourcePage sourcePage);

  @Nonnull
  Collection<Element> getSubforumAnchors(SourcePage sourcePage);

  @Nonnull
  Collection<Element> getTopicAnchors(SourcePage sourcePage);

  void postProcessing(SourcePage sourcePage, ParserResult result);
}
