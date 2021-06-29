package sh.xana.forum.server.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ForumUtils {
  public static Element selectOnlyOne(Document doc, String selector, String error) {
    Elements elems = doc.select(selector);
    if (elems.size() != 1) {
      throw new RuntimeException("too many " + elems.size() + " for " + error);
    }
    return elems.get(0);
  }

  public static Elements selectOneOrMore(Document doc, String selector, String error) {
    Elements elems = doc.select(selector);
    if (elems.size() == 0) {
      throw new RuntimeException("not enough " + elems.size() + " for " + error);
    }
    return elems;
  }

  public static boolean anchorIsNotNavLink(Element elem) {
    return (!elem.attr("name").isBlank() && elem.attr("href").isBlank())
        || elem.attr("href").startsWith("javascript://");
  }

  public static String assertNotBlank(String in) {
    if (in.isBlank()) {
      throw new RuntimeException("blank");
    }
    return in.trim();
  }
}
