package sh.xana.forum.server.parser;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import sh.xana.forum.server.dbutil.PageType;

public abstract class AbstractUrlForum implements AbstractForum {
  private final String pageForum;
  private final String pageThread;
  private final String[] queryKeysForum;
  private final String[] queryKeysTopic;
  private final String[] queryKeysPage;
  private final ThreadLocal<URIBuilder[]> fetchedLinks = new ThreadLocal<>();

  protected AbstractUrlForum(
      String pageForum, String pageThread, String[] queryKeysForum, String[] queryKeysTopic) {
    this.pageForum = pageForum;
    this.pageThread = pageThread;
    this.queryKeysForum = queryKeysForum;
    this.queryKeysTopic = queryKeysTopic;

    queryKeysPage =
        Stream.concat(Stream.of(queryKeysForum), Stream.of(queryKeysTopic))
            .distinct()
            .toArray(String[]::new);
  }

  @Override
  public @NotNull PageType forcePageType(SourcePage sourcePage) {
    URIBuilder path = new URIBuilder(sourcePage.pageUri());
    if (ForumStream.fileIs(path, pageForum, "index.php")) {
      return PageType.ForumList;
    } else if (ForumStream.fileIs(path, pageThread)) {
      return PageType.TopicPage;
    } else {
      return PageType.Unknown;
    }
  }

  @Override
  public @NotNull Collection<ValidatedUrl> getPageLinks(SourcePage sourcePage) {
    Stream<String> stream =
        getBaseUrls(sourcePage.doc())
            // must be something with a page param
            .filter(e -> ForumStream.containsQueryKey(e, "page"))
            // reduce to core params
            .map(e -> ForumStream.cleanUrl(e, queryKeysPage));
    return getValidatedUrls(sourcePage, stream);
  }

  @Override
  public @NotNull Collection<ValidatedUrl> getSubforumAnchors(SourcePage sourcePage) {
    Stream<String> stream =
        getBaseUrls(sourcePage.doc())
            // forum files
            .filter(e -> ForumStream.fileIs(e, pageForum))
            // reduce to core forum params
            .map(e -> ForumStream.cleanUrl(e, queryKeysForum));
    return getValidatedUrls(sourcePage, stream);
  }

  @Override
  public @NotNull Collection<ValidatedUrl> getTopicAnchors(SourcePage sourcePage) {
    Stream<String> stream =
        getBaseUrls(sourcePage.doc())
            // topic files
            .filter(e -> ForumStream.fileIs(e, pageThread))
            // reduce to core topic params
            .map(e -> ForumStream.cleanUrl(e, queryKeysTopic));
    return getValidatedUrls(sourcePage, stream);
  }

  protected Stream<URIBuilder> getBaseUrls(Document doc) {
    URIBuilder[] links = fetchedLinks.get();
    if (links == null) {
      links =
          doc.getElementsByTag("a").stream()
              // must be link
              .filter(ForumStream::anchorIsNavLink)
              .map(ForumStream::getAnchorFullUrl)
              .filter(e -> ForumStream.isBaseUri(e, doc))
              .mapMulti(ForumStream::toValidUriBuilder)
              .toArray(URIBuilder[]::new);
      fetchedLinks.set(links);
    }
    return Arrays.stream(links);
  }

  private Collection<ValidatedUrl> getValidatedUrls(
      SourcePage sourcePage, Stream<String> uriStream) {
    // done
    return uriStream
        .mapMulti(ForumStream.softLogFail(e -> new ValidatedUrl(e, this, sourcePage), sourcePage))
        .collect(Collectors.toList());
  }

  @Override
  public void pageDone() {
    fetchedLinks.remove();
  }
}
