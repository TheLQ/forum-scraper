package sh.xana.forum.server.parser;

import java.util.Collection;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import sh.xana.forum.common.ipc.Subpage;
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
  PageType getPageType(SourcePage sourcePage);

  @NotNull
  Stream<Subpage> getSubpages(SourcePage sourcePage, PageType currentPageType);

  @Nullable
  Subpage getValidLink(
      String link, PageType currentPageType, String baseUri, UUID pageId, boolean throwErrors);

  @NotNull
  Collection<Element> getPostElements(SourcePage sourcePage);

  default PageType postForcePageType(SourcePage sourcePage, PageType currentType) {
    return currentType;
  }

  Pattern[] validateUrl();

  default void pageDone() {}
}
