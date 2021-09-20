package sh.xana.forum.server.parser;

import java.util.Collection;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  @NotNull
  default PageType forcePageType(SourcePage sourcePage) {
    return PageType.Unknown;
  }

  boolean detectLoginRequired(SourcePage sourcePage);

  @NotNull
  Collection<ValidatedUrl> getPageLinks(SourcePage sourcePage);

  @NotNull
  Collection<Element> getPostElements(SourcePage sourcePage);

  @NotNull
  Collection<ValidatedUrl> getSubforumAnchors(SourcePage sourcePage);

  @NotNull
  Collection<ValidatedUrl> getTopicAnchors(SourcePage sourcePage);

  default PageType postForcePageType(SourcePage sourcePage, PageType currentType) {
    return currentType;
  }

  default String postProcessUrl(String url) {
    return url;
  }

  Pattern[] validateUrl();

  default void pageDone() {}
}
