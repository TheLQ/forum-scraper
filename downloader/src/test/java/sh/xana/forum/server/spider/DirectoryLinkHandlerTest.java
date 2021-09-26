package sh.xana.forum.server.spider;

import java.net.URISyntaxException;
import org.apache.http.client.utils.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;
import sh.xana.forum.server.spider.config.DirectoryLinkHandler;
import sh.xana.forum.common.Position;

@Test
public class DirectoryLinkHandlerTest {
  @Test
  public void endAndIdPrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.end, null, "t");
    Assert.assertTrue(process(h, "http://example.com/app/", "mytopic-t555"));
    Assert.assertFalse(process(h, "http://example.com/app/", "mytopic-x555"));
  }

  @Test
  public void endAndIdSep() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.end, "-", null);
    Assert.assertTrue(process(h, "http://example.com/app/", "mytopic-555"));
    Assert.assertFalse(process(h, "http://example.com/app/", "mytopic+555"));
  }

  @Test
  public void endAndIdSepAndPrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.end, "-", "t");
    Assert.assertTrue(process(h, "http://example.com/app/", "mytopic-t555"));
    Assert.assertFalse(process(h, "http://example.com/app/", "mytopic+t555"));
    Assert.assertFalse(process(h, "http://example.com/app/", "mytopic-x555"));
  }

  private boolean process(DirectoryLinkHandler h, String linkBase, String linkRelative)
      throws URISyntaxException {
    return h.processLink(new URIBuilder(linkBase + linkRelative), linkRelative);
  }
}
