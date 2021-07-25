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
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.parser.ForumUtils;
import sh.xana.forum.server.parser.SourcePage;

public class vBulletin /*implements AbstractForum*/ {
  private static final Logger log = LoggerFactory.getLogger(vBulletin.class);

  public ForumType detectForumType(String rawHtml) {
    if (rawHtml.contains("vBulletin_init();") || rawHtml.contains("vBulletin Version")) {
      return ForumType.vBulletin;
    } else {
      return null;
    }
  }

  public boolean detectLoginRequired(SourcePage sourcePage) {
    return sourcePage.rawHtml().contains("<!-- permission error message - user not logged in -->");
  }

  public @Nonnull Collection<Element> getPageLinks(SourcePage sourcePage) {
    return sourcePage.doc().select(".pagenav a, .threadpagenav .pagination a, link[rel='next']");
  }

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

  private final Pattern PATTERN_SUBFORUM1 = Pattern.compile("id=\"(?<id>f(orum)?_?[0-9]+)\"");
  private final Pattern PATTERN_SUBFORUM2 =
      Pattern.compile("class=\"[a-zA-Z ] (?<id>forum[0-9]+\")");

  public @Nonnull Collection<Element> getSubforumAnchors(SourcePage sourcePage) {
    // id="f266"
    // id="forum266"
    // class="forum_266"
    Matcher matcher = PATTERN_SUBFORUM1.matcher(sourcePage.rawHtml());

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

    // v2
    if (!sourcePage.doc().select(".forum").isEmpty()) {
      Elements forumsAnchors =
          ForumUtils.selectOneOrMore(sourcePage.doc(), "a[class='forum']", "v2");
      result.addAll(forumsAnchors);
    }

    return result;
  }

  private static final Pattern PATTERN_TOPIC =
      Pattern.compile("id=\"(?<id>thread(_title_)?[0-9]+)\"");

  public @Nonnull Collection<Element> getTopicAnchors(SourcePage sourcePage) {
    Matcher matcher = PATTERN_TOPIC.matcher(sourcePage.rawHtml());

    List<Element> result = new ArrayList<>();
    while (matcher.find()) {
      String id = ForumUtils.assertNotBlank(matcher.group("id"));
      Element anchor =
          ForumUtils.selectOnlyOne(
              sourcePage.doc(), "a#ID, #ID .thread_title".replaceAll("ID", id), "for topic " + id);
      result.add(anchor);
    }

    //    if (!sourcePage.doc().select("#threadlist").isEmpty()) {
    //      ForumUtils.selectOneOrMore(sourcePage.doc(), ".threadlist .thread")
    //    }

    return result;
  }

  public void postProcessing(SourcePage sourcePage, ParserResult result) {}

  private static final String PATTERN_TOPIC_TPL = "[a-zA-Z0-9\\-%_\\(\\)!\\*\\?']+";
  private static final Pattern[] PATTERN_URI =
      new Pattern[] {
        // forumdisplay.php?f=3&order=desc&page=4
        // forumdisplay.php?forumid=3&order=desc&page=4
        Pattern.compile("forumdisplay.php\\?(f|forumid)=[0-9]+(&order=desc)?(&page=[0-9]+)?"),
        // forumdisplay.php?5-cars
        Pattern.compile(
            "forumdisplay.php[?|/][0-9]+-TOPIC_TPL(/page[0-9]+)?"
                .replace("TOPIC_TPL", PATTERN_TOPIC_TPL)),

        // forums/46-myforum/page2?order=desc
        Pattern.compile(
            "forums/[0-9]+-TOPIC_TPL(/page[0-9]+)?(\\?order=desc)?"
                .replace("TOPIC_TPL", PATTERN_TOPIC_TPL)),
        // forum/index2.html ...
        Pattern.compile("TOPIC_TPL/index[0-9]+.html".replace("TOPIC_TPL", PATTERN_TOPIC_TPL)),
        // showthread.php?t=5&page=5
        Pattern.compile("showthread.php\\?(t|threadid)=[0-9]+(&page=[0-9]+)?"),
        // showthread.php?9-my-topic/page7 (-my-topic is optional...)
        Pattern.compile(
            "showthread.php[?|/][0-9]+(-TOPIC_TPL)?(/page[0-9]+)?"
                .replace("TOPIC_TPL", PATTERN_TOPIC_TPL)),

        // threads/9-mytopic/page2
        Pattern.compile(
            "threads/[0-9]+-TOPIC_TPL(/page[0-9]+)?(\\?order=desc)?"
                .replace("TOPIC_TPL", PATTERN_TOPIC_TPL)),
        // forum/484-mytopic.html
        Pattern.compile(
            "TOPIC_TPL/[0-9]+-TOPIC_TPL.html".replaceAll("TOPIC_TPL", PATTERN_TOPIC_TPL)),
      };

  public Pattern[] validateUrl() {
    return PATTERN_URI;
  }
}
