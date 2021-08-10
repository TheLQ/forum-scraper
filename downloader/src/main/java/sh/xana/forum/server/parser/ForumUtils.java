package sh.xana.forum.server.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

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
      throw new RuntimeException("0 elements for  for " + error);
    }
    return elems;
  }

  public static boolean anchorIsNotNavLink(Element elem) {
    return elem.attr("href").isBlank()
        || elem.attr("href").startsWith("javascript://")
        || elem.attr("href").startsWith("#");
  }

  public static String assertNotBlank(String in) {
    if (in.isBlank()) {
      throw new RuntimeException("blank");
    }
    return in.trim();
  }

  public static Collection<ValidatedUrl> elementToUrl(
      Collection<Element> elements, AbstractForum parser, SourcePage sourcePage) {
    return elementToUrl(elements, parser, sourcePage, new ArrayList<>());
  }

  public static Collection<ValidatedUrl> elementToUrl(
      Collection<Element> elements,
      AbstractForum parser,
      SourcePage sourcePage,
      Collection<ValidatedUrl> results) {
    for (Element element : elements) {
      if (ForumUtils.anchorIsNotNavLink(element)) {
        continue;
      }

      String url = element.absUrl("href");
      if (StringUtils.isBlank(url)) {
        throw new RuntimeException("href blank in " + element.outerHtml());
      }

      results.add(new ValidatedUrl(url, parser, sourcePage));
    }
    return results;
  }

  public static Collection<Element> findElementByIdPrefix(Document doc, String... prefixes) {
    return findElementByIdPrefix(doc, (e, result) -> result.add(e), prefixes);
  }

  public static Collection<Element> findElementByIdPrefix(
      Document doc, BiConsumer<Element, List<Element>> processor, String... prefixes) {
    List<Element> result = new ArrayList<>();
    NodeTraversor.traverse(
        new NodeVisitor() {
          @Override
          public void head(Node node, int depth) {
            if (node instanceof Element) {
              String id = node.attr("id");
              if (id.equals("")) {
                return;
              }
              for (String prefix : prefixes) {
                if (id.startsWith(prefix) && NumberUtils.isDigits(id.substring(prefix.length()))) {
                  processor.accept((Element) node, result);
                  return;
                }
              }
            }
          }

          @Override
          public void tail(Node node, int depth) {}
        },
        doc);
    return result;
  }
}
