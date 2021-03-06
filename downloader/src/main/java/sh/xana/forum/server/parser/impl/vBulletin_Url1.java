package sh.xana.forum.server.parser.impl;

import java.util.Collection;
import java.util.regex.Pattern;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.parser.AbstractUrlForum;
import sh.xana.forum.server.parser.ForumUtils;
import sh.xana.forum.server.parser.SourcePage;

public class vBulletin_Url1 extends AbstractUrlForum {
  private static final Logger log = LoggerFactory.getLogger(vBulletin_Url1.class);

  public vBulletin_Url1() {
    super(
        new String[] {"forumdisplay.php", "index.php"},
        new String[] {"showthread.php"},
        new String[] {"f", "page"},
        new String[] {"t", /*old way??*/ "p", "page"});
  }

  @Override
  public @Nullable ForumType detectForumType(String rawHtml) {
    return null;
  }

  @Override
  public @NotNull Collection<Element> getPostElements(SourcePage sourcePage) {
    return ForumUtils.findElementByIdPrefix(sourcePage.doc(), "post");
  }

  @Override
  protected @NotNull ProcessState getValidLink_pre(String baseUri, URIBuilder e) {
    if (e.toString().substring(baseUri.length()).startsWith("archive/")) {
      return ProcessState.STOP;
    } else {
      return ProcessState.CONTINUE;
    }
  }

  private static final Pattern[] PATTERNS =
      new Pattern[] {
        // index.php homepage
        Pattern.compile("index.php"),
        // forumdisplay.php?f=139&order=desc&page=5
        Pattern.compile("forumdisplay.php\\?f=[0-9]+(&page=[0-9]+)?"),
        // showthread.php?t=717252&page=2
        Pattern.compile("showthread.php\\?[tp]=[0-9]+(&page=[0-9]+)?")
      };

  @Override
  public Pattern[] validateUrl() {
    return PATTERNS;
  }
}
