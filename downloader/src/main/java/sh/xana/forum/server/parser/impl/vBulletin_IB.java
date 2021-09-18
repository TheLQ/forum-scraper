package sh.xana.forum.server.parser.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.ForumUtils;
import sh.xana.forum.server.parser.SourcePage;
import sh.xana.forum.server.parser.ValidatedUrl;

public class vBulletin_IB implements AbstractForum {
  private static final Logger log = LoggerFactory.getLogger(vBulletin_IB.class);

  @Override
  public boolean detectLoginRequired(SourcePage sourcePage) {
    return sourcePage
        .rawHtml()
        .contains("<div class=\"h2\"><strong>Join The Conversation</strong></div>");
  }

  @Override
  public @Nullable ForumType detectForumType(String rawHtml) {
    throw new UnsupportedOperationException();
  }

  private static final Pattern PATTERN_PAGES = Pattern.compile("Page [0-9]+ of ([0-9]+)");
  private static final Pattern PATTERN_FORUM_NUM_THREADS =
      Pattern.compile("Showing threads [01] to ([0-9]+) of ([0-9]+)");

  @Override
  public @NotNull Collection<ValidatedUrl> getPageLinks(SourcePage sourcePage) {
    if (sourcePage.pageUri().toString().equals(sourcePage.doc().baseUri())) {
      // homepage, ignore
      return List.of();
    }

    int pageTotalNum;
    Elements navListElements = sourcePage.doc().select(".pagenav .vbmenu_control");
    Elements navNextElements = sourcePage.doc().select("#mb_page_total");
    if (!navListElements.isEmpty()) {
      // forum page where you can select each page
      Matcher nav = PATTERN_PAGES.matcher(navListElements.get(0).text().trim());
      if (!nav.matches()) {
        throw new RuntimeException("Unexpected page format");
      }
      pageTotalNum = Integer.parseInt(nav.group(1));
    } else if (!navNextElements.isEmpty()) {
      // topic page with infinite scroll
      pageTotalNum = Integer.parseInt(navNextElements.get(0).text().trim());
    } else {
      Matcher matcher = PATTERN_FORUM_NUM_THREADS.matcher(sourcePage.rawHtml());
      if (matcher.find() && matcher.group(1).equals(matcher.group(2))) {
        // detect ForumList with no subpages, ignore
        return List.of();
      } else {
        throw new RuntimeException("Cannot find any page nav");
      }
    }

    List<ValidatedUrl> result = new ArrayList<>();
    String pageUri = sourcePage.pageUri().toString();
    // For page 1 the page parameter is removed, so skip generating it
    if (sourcePage.pageUri().getQuery() != null) {
      for (int i = 2; i <= pageTotalNum; i++) {
        result.add(new ValidatedUrl(pageUri + "&page=" + i, this, sourcePage));
      }
    } else {
      pageUri = getUrlWithoutPage(pageUri);
      String pageSep = pageUri.contains("/marketplace/") ? "-" : "";
      for (int i = 2; i <= pageTotalNum; i++) {
        result.add(new ValidatedUrl(pageUri + "page" + pageSep + i + "/", this, sourcePage));
      }
    }

    return result;
  }

  private static final Pattern PATTERN_URL_HAS_PAGE = Pattern.compile("/(page-?[0-9]+/?)$");

  private String getUrlWithoutPage(String pageUri) {
    // get root url
    Matcher pageUriMatcher = PATTERN_URL_HAS_PAGE.matcher(pageUri);
    if (pageUriMatcher.find()) {
      pageUri =
          pageUri.substring(
              0, pageUri.length() - pageUriMatcher.group().length() + /*include slash*/ 1);
    }
    if (!pageUri.endsWith("/")) {
      throw new RuntimeException(
          "Expected url to end with / uri " + pageUri + " source " + pageUri);
    }
    return pageUri;
  }

  @Override
  public @NotNull Collection<Element> getPostElements(SourcePage sourcePage) {
    Matcher matcher =
        Pattern.compile("id=\"(?<id>(td_post|post_message)_[0-9]+)\"")
            .matcher(sourcePage.rawHtml());

    List<Element> result = new ArrayList<>();
    while (matcher.find()) {
      String id = ForumUtils.assertNotBlank(matcher.group("id"));
      Element anchor = ForumUtils.selectOnlyOne(sourcePage.doc(), "#" + id, "for post " + id);
      result.add(anchor);
    }
    return result;
  }

  @Override
  public @NotNull Collection<ValidatedUrl> getSubforumAnchors(SourcePage sourcePage) {
    return ForumUtils.elementToUrl(
        ForumUtils.findElementByIdPrefix(
            sourcePage.doc(),
            (element, elements) -> {
              // first link is forum, subsequent might be description links
              elements.add(element.select("a").get(0));

              // subforums
              elements.addAll(element.select(".subforums a"));
            },
            "f"),
        this,
        sourcePage,
        new ArrayList<>());
  }

  @Override
  public @NotNull Collection<ValidatedUrl> getTopicAnchors(SourcePage sourcePage) {
    return ForumUtils.elementToUrl(
        ForumUtils.findElementByIdPrefix(sourcePage.doc(), "thread_title_"),
        this,
        sourcePage,
        new ArrayList<>());
  }

  private final Pattern PATTERN_SEARCH_ID = Pattern.compile("s=[0-9a-zA-Z]{32}[&]?");
  private final Pattern PATTERN_DUPLICATE_SEP = Pattern.compile("(?<!http[s]?:)//");
  private final Pattern PATTERN_DUPLICATE_PAGE =
      Pattern.compile("(?<first>/page-[0-9]+/)page-[0-9]+/");
  private final Pattern PATTERN_DUPLICATE_DOMAIN = Pattern.compile("http[s]?://[a-zA-Z0-9.]+/");

  @Override
  public String postProcessUrl(String url) {
    while (true) {
      /*
      The marketplace module seems to use either client js or cookies for state tracking.
      But if your not a browser, it falls back tacking on the next page to end of the url
      eg page-50/page-70 to page-50/page-70/page-90 to page page-50/page-70/page-90/page-110

      Sometimes a custom id is also added (but not on my browser?), which similarly infinitely spans

      Not only is this url useless to archive, it causes "Data too long for column" SQL errors
      */

      String newUrl = url;
      // match s=[32 character hex id]
      newUrl = PATTERN_SEARCH_ID.matcher(newUrl).replaceAll("");
      // match double directory separator // but not the http:// one
      newUrl = PATTERN_DUPLICATE_SEP.matcher(newUrl).replaceAll("/");

      // match first duplicate of /page-50/page-70/
      Matcher pages = PATTERN_DUPLICATE_PAGE.matcher(newUrl);
      if (pages.find()) {
        newUrl = newUrl.replace(pages.group("first"), "/");
      }

      //        // site 60b02d52-f85a-4c09-9ba5-44e25f405f00 can give us a very broken link in
      //        // <link rel=next> , maybe for anti-scraping :|
      //        // First, match http://domain.com/http://domain.com
      //        Matcher domains = PATTERN_DUPLICATE_DOMAIN.matcher(newUrl);
      //        if (domains.find() && domains.find()) {
      //          // Just drop this nonsense, it's difficult to parse back into a real url, and
      // pageNav
      //          // already covers it
      //          itr.remove();
      //          continue outer;
      //          // String match = domains.group();
      //          // newUrl = StringUtils.replace(newUrl, match, "", 1);
      //          //
      //          // // Then match search/&page=2 , which literally nothing else references that
      // format
      //          // newUrl = newUrl.replace("search/&page=", "search/page-");
      //          //
      //          // // Then make sure the url ends with /
      //          // if (!newUrl.endsWith("/")) {
      //          //   newUrl = newUrl + "/";
      //          // }
      //        }

      // The infinite scroll plugin uses a ?ispreloading magic url
      newUrl = newUrl.replace("?ispreloading=1", "");

      if (newUrl.endsWith("&") || newUrl.endsWith("?")) {
        newUrl = newUrl.substring(0, newUrl.length() - 1);
      }

      if (!newUrl.equals(url)) {
        url = newUrl;
      } else {
        break;
      }
    }
    return url;
  }

  private static final String PATTERN_TOPIC_TPL = "[a-zA-Z0-9\\-%_\\(\\)!\\*\\?']+";
  private static final Pattern[] PATTERNS =
      new Pattern[] {
        // cars-2/page9/
        Pattern.compile("[a-zA-Z0-9\\-]+-[0-9]+/(page[0-9]+/)?"),
        // cars-2/my-topic-9/page5
        Pattern.compile(
            "[a-zA-Z0-9\\-]+-[0-9]+/TOPIC_TPL-[0-9]+/(page[0-9]+/)?"
                .replace("TOPIC_TPL", PATTERN_TOPIC_TPL)),
        // showthread.php?t=5&page=5
        Pattern.compile("showthread.php\\?t=[0-9]+(&page=[0-9]+)?"),
        // marketplace/parts/search/page-42
        Pattern.compile("marketplace/[a-z]+/search/(page-[0-9]+/)?")
      };

  @Override
  public Pattern[] validateUrl() {
    return PATTERNS;
  }
}
