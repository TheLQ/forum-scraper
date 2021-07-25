package sh.xana.forum.server.parser;

import java.util.Collection;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.PageType;

public interface AbstractForum {
  @Nullable
  ForumType detectForumType(String rawHtml);

  default Document newDocument(String rawHtml, String baseUrl) {
    return Jsoup.parse(rawHtml, baseUrl);
  }

  default void preProcessing(SourcePage sourcePage) {}

  boolean detectLoginRequired(SourcePage sourcePage);

  @Nonnull
  Collection<ValidatedUrl> getPageLinks(SourcePage sourcePage);

  @Nonnull
  Collection<Element> getPostElements(SourcePage sourcePage);

  @Nonnull
  Collection<ValidatedUrl> getSubforumAnchors(SourcePage sourcePage);

  @Nonnull
  Collection<ValidatedUrl> getTopicAnchors(SourcePage sourcePage);

  default PageType postForcePageType(SourcePage sourcePage, PageType currentType) {
    return currentType;
  }

  default String postProcessUrl(String url) {
    return url;
  }

  Pattern[] validateUrl();
}
