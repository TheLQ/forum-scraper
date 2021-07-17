package sh.xana.forum.server.parser.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.ParserEntry;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.ForumUtils;
import sh.xana.forum.server.parser.SourcePage;

public class vBulletin implements AbstractForum {
  private static final Logger log = LoggerFactory.getLogger(vBulletin.class);

  @Override
  public ForumType detectForumType(String rawHtml) {
    if (rawHtml.contains("vBulletin_init();")) {
      return ForumType.vBulletin;
    } else {
      return null;
    }
  }

  @Override
  public boolean detectLoginRequired(SourcePage sourcePage) {
    return sourcePage.rawHtml().contains("<!-- permission error message - user not logged in -->");
  }

  @Override
  public @Nonnull Collection<Element> getPageLinks(SourcePage sourcePage) {
    return sourcePage.doc().select(".pagenav a, .pagination a, link[rel='next']");
  }

  @Override
  public @Nonnull Collection<Element> getPostElements(SourcePage sourcePage) {
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
  public @Nonnull Collection<Element> getSubforumAnchors(SourcePage sourcePage) {
    Matcher matcher = Pattern.compile("id=\"(?<id>f(orum)?[0-9]+)\"").matcher(sourcePage.rawHtml());

    List<Element> result = new ArrayList<>();
    while (matcher.find()) {
      String id = ForumUtils.assertNotBlank(matcher.group("id"));
      // for td, older implementations seem to put all subforums under the main one without a
      // dedicated id. So cannout use selectOnlyOne
      Elements anchors =
          ForumUtils.selectOneOrMore(
              sourcePage.doc(),
              "div[id='ID'] h2 a, div[id='ID'] h3 a, td[id='ID'] a, li[id='ID'] .forumtitle a"
                  .replaceAll("ID", id),
              "for forum " + id);
      result.addAll(anchors);
    }
    return result;
  }

  @Override
  public @Nonnull Collection<Element> getTopicAnchors(SourcePage sourcePage) {
    Matcher matcher =
        Pattern.compile("id=\"(?<id>thread_title_[0-9]+)\"").matcher(sourcePage.rawHtml());

    List<Element> result = new ArrayList<>();
    while (matcher.find()) {
      String id = ForumUtils.assertNotBlank(matcher.group("id"));
      Element anchor = ForumUtils.selectOnlyOne(sourcePage.doc(), "#" + id, "for topic " + id);
      result.add(anchor);
    }
    return result;
  }

  private final Pattern PATTERN_SEARCH_ID = Pattern.compile("s=[0-9a-zA-Z]{32}[&]?");
  private final Pattern PATTERN_DUPLICATE_SEP = Pattern.compile("(?<!http[s]?:)//");
  private final Pattern PATTERN_DUPLICATE_PAGE =
      Pattern.compile("(?<first>/page-[0-9]+/)page-[0-9]+/");
  private final Pattern PATTERN_DUPLICATE_DOMAIN = Pattern.compile("http[s]?://[a-zA-Z0-9.]+/");

  @Override
  public void postProcessing(SourcePage sourcePage, ParserResult result) {
    outer:
    for (var itr = result.subpages().listIterator(); itr.hasNext(); ) {
      ParserEntry entry = itr.next();
      while (true) {
        /*
        The marketplace module seems to use either client js or cookies for state tracking.
        But if your not a browser, it falls back tacking on the next page to end of the url
        eg page-50/page-70 to page-50/page-70/page-90 to page page-50/page-70/page-90/page-110

        Sometimes a custom id is also added (but not on my browser?), which similarly infinitely spans

        Not only is this url useless to archive, it causes "Data too long for column" SQL errors
        */

        String newUrl = entry.url();
        // match s=[32 character hex id]
        newUrl = PATTERN_SEARCH_ID.matcher(newUrl).replaceAll("");
        // match double directory separator // but not the http:// one
        newUrl = PATTERN_DUPLICATE_SEP.matcher(newUrl).replaceAll("/");

        // match first duplicate of /page-50/page-70/
        Matcher pages = PATTERN_DUPLICATE_PAGE.matcher(newUrl);
        if (pages.find()) {
          newUrl = newUrl.replace(pages.group("first"), "/");
        }

        // site 60b02d52-f85a-4c09-9ba5-44e25f405f00 can give us a very broken link in
        // <link rel=next> , maybe for anti-scraping :|
        // First, match http://domain.com/http://domain.com
        Matcher domains = PATTERN_DUPLICATE_DOMAIN.matcher(newUrl);
        if (domains.find() && domains.find()) {
          // Just drop this nonsense, it's difficult to parse back into a real url, and pageNav
          // already covers it
          itr.remove();
          continue outer;
          // String match = domains.group();
          // newUrl = StringUtils.replace(newUrl, match, "", 1);
          //
          // // Then match search/&page=2 , which literally nothing else references that format
          // newUrl = newUrl.replace("search/&page=", "search/page-");
          //
          // // Then make sure the url ends with /
          // if (!newUrl.endsWith("/")) {
          //   newUrl = newUrl + "/";
          // }
        }

        // The infinite scroll plugin uses a ?ispreloading magic url
        newUrl = newUrl.replace("?ispreloading=1", "");

        if (newUrl.endsWith("&")) {
          newUrl = newUrl.substring(0, newUrl.length() - 1);
        }

        if (!entry.url().equals(newUrl)) {
          entry = new ParserEntry(entry.name(), newUrl, entry.pageType());
          itr.set(entry);
        } else {
          break;
        }
      }
    }
  }

  private static final String PATTERN_TOPIC_TPL = "[a-zA-Z0-9\\-%_\\(\\)!\\*\\?']+";
  private static final Pattern[] PATTERN_URI =
      new Pattern[] {
        // forumdisplay.php?f=3&order=desc&page=4
        Pattern.compile("forumdisplay.php\\?f=[0-9]+(&order=desc)?(&page=[0-9]+)?"),
        // forumdisplay.php?5-cars
        Pattern.compile(
            "forumdisplay.php\\?[0-9]+-TOPIC_TPL(/page[0-9]+)?"
                .replace("TOPIC_TPL", PATTERN_TOPIC_TPL)),
        // cars-2/page9/
        Pattern.compile("[a-zA-Z0-9\\-]+-[0-9]+/(page[0-9]+/)?"),
        // showthread.php?t=5&page=5
        Pattern.compile("showthread.php\\?t=[0-9]+(&page=[0-9]+)?"),
        // showthread.php?9-my-topic/page7 (-my-topic is optional...)
        Pattern.compile(
            "showthread.php\\?[0-9]+(-TOPIC_TPL)?(/page[0-9]+)?"
                .replace("TOPIC_TPL", PATTERN_TOPIC_TPL)),
        // cars-2/my-topic-9/page5
        Pattern.compile(
            "[a-zA-Z0-9\\-]+-[0-9]+/TOPIC_TPL-[0-9]+/(page[0-9]+/)?"
                .replace("TOPIC_TPL", PATTERN_TOPIC_TPL)),
        // marketplace/parts/search/page-42
        Pattern.compile("marketplace/[a-z]+/search/(page-[0-9]+/)?")
      };

  @Override
  public Pattern[] validateUrl() {
    return PATTERN_URI;
  }
}
