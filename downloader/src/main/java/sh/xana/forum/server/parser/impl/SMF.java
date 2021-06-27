package sh.xana.forum.server.parser.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import sh.xana.forum.common.ipc.ParserResult;
import sh.xana.forum.common.ipc.ParserResult.ParserEntry;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.ForumType;
import sh.xana.forum.server.parser.AbstractForum;
import sh.xana.forum.server.parser.ForumUtils;
import sh.xana.forum.server.parser.SourcePage;

public class SMF implements AbstractForum {

  @Override
  public DatabaseStorage.ForumType detectForumType(String rawHtml) {
    if (rawHtml.contains("var smf_theme_url")) {
      return ForumType.SMF;
    } else {
      return null;
    }
  }

  private final Pattern PATTERN_AD_IFRAME = Pattern.compile("<iframe [a-zA-Z0-9 \"'=:/.;]+>");

  @Override
  public Document newDocument(String rawHtml, String baseUrl) {
    /*
    site 2432457d-49f9-4de3-b2b9-8716a8d51dde has a really broken ad module that randomly injects an iframe. This breaks html parsing in both jsoup and Firefox(!)
    */
    rawHtml = PATTERN_AD_IFRAME.matcher(rawHtml).replaceFirst("");

    return AbstractForum.super.newDocument(rawHtml, baseUrl);
  }

  @Override
  public boolean detectLoginRequired(SourcePage sourcePage) {
    return sourcePage.rawHtml().contains("document.forms.frmLogin.user.focus");
  }

  @Override
  public @Nonnull Collection<Element> getPageLinks(SourcePage sourcePage) {
    return sourcePage.doc().select(".pagelinks .navPages");
  }

  @Override
  public @Nonnull Collection<Element> getPostElements(SourcePage sourcePage) {
    // both the topiclist entry and the message posts use the same id... so make sure we are on the
    // post page
    if (!sourcePage.doc().select("#messageindex").isEmpty()) {
      return Collections.emptyList();
    }
    return getMessage(sourcePage, "");
  }

  @Override
  public @Nonnull Collection<Element> getSubforumAnchors(SourcePage sourcePage) {
    Matcher matcher = Pattern.compile("name=\"(?<id>b[0-9]{1,4})\"").matcher(sourcePage.rawHtml());

    List<Element> result = new ArrayList<>();
    while (matcher.find()) {
      String id = ForumUtils.assertNotBlank(matcher.group("id"));
      Element anchor =
          ForumUtils.selectOnlyOne(
              sourcePage.doc(), "a[name='%s']".formatted(id), "for forum " + id);
      result.add(anchor);
    }
    return result;
  }

  @Override
  public @Nonnull Collection<Element> getTopicAnchors(SourcePage sourcePage) {
    // both the topiclist entry and the message posts use the same id... so make sure we are on the
    // forumlist page
    if (sourcePage.doc().select("#messageindex").isEmpty()) {
      return Collections.emptyList();
    }
    return getMessage(sourcePage, " a");
  }

  private final Pattern MATCHER_MESSAGE = Pattern.compile("id=\"(?<id>msg_[0-9]+)\"");

  private List<Element> getMessage(SourcePage sourcePage, String extraQuery) {
    List<Element> result = new ArrayList<>();
    Matcher matcher = MATCHER_MESSAGE.matcher(sourcePage.rawHtml());
    while (matcher.find()) {
      String id = ForumUtils.assertNotBlank(matcher.group("id"));

      Element element =
          ForumUtils.selectOnlyOne(sourcePage.doc(), "#" + id + extraQuery, "post/topic id " + id);
      result.add(element);
    }
    return result;
  }

  private final Pattern PATTERN_SID = Pattern.compile("\\?PHPSESSID=[A-Za-z0-9]+");
  @Override
  public void postProcessing(SourcePage sourcePage, ParserResult result) {
    for (var itr = result.subpages().listIterator(); itr.hasNext(); ) {
      ParserEntry entry = itr.next();
      String newUrl = entry.url();
      newUrl = PATTERN_SID.matcher(newUrl).replaceFirst("");

      if (!newUrl.equals(entry.url())) {
        itr.set(new ParserEntry(entry.name(), newUrl, entry.pageType()));
      }
    }
  }
}
