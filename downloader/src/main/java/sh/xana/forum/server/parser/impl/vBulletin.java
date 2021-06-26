package sh.xana.forum.server.parser.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.jsoup.nodes.Element;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.ParserEntry;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.ForumType;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.ForumUtils;
import sh.xana.forum.server.parser.SourcePage;

public class vBulletin implements AbstractForum {

  @Override
  public DatabaseStorage.ForumType detectForumType(String rawHtml) {
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
    List<Element> elements = new ArrayList<>();

    // normal page nav list
    for (Element elem : sourcePage.doc().select(".pagenav a, link[rel='next']")) {
      if (ForumUtils.anchorIsNotNavLink(elem)) {
        continue;
      }
      elements.add(elem);
    }

    return elements;
  }

  @Override
  public @Nonnull Collection<Element> getPostElements(SourcePage sourcePage) {
    Matcher matcher = Pattern.compile("id=\"(?<id>td_post_[0-9]+)\"").matcher(sourcePage.rawHtml());

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
    Matcher matcher = Pattern.compile("id=\"(?<id>f[0-9]+)\"").matcher(sourcePage.rawHtml());

    List<Element> result = new ArrayList<>();
    while (matcher.find()) {
      String id = ForumUtils.assertNotBlank(matcher.group("id"));
      Element anchor =
          ForumUtils.selectOnlyOne(
              sourcePage.doc(),
              "div[id='ID'] h2 a, div[id='ID'] h3 a, td[id='ID'] a".replaceAll("ID", id),
              "for forum " + id);
      result.add(anchor);
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

  @Override
  public void postProcessing(SourcePage sourcePage, ParserResult result) {
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

        // The infinite scroll plugin uses a ?ispreloading magic url
        newUrl = newUrl.replace("?ispreloading=1", "");

        if (!entry.url().equals(newUrl)) {
          entry = new ParserEntry(entry.name(), newUrl, entry.pageType());
          itr.set(entry);
        } else {
          break;
        }
      }
    }
  }
}
