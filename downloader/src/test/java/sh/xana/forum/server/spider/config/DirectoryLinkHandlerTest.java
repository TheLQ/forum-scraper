package sh.xana.forum.server.spider.config;

import java.net.URISyntaxException;
import org.apache.http.client.utils.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;
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

  @Test
  public void startAndIdPrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.start, null, "t");
    Assert.assertTrue(process(h, "http://example.com/app/", "t555-mytopic"));
    Assert.assertFalse(process(h, "http://example.com/app/", "x555-mytopic"));
  }

  @Test
  public void startAndIdSep() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.start, "-", null);
    Assert.assertTrue(process(h, "http://example.com/app/", "555-mytopic"));
    Assert.assertFalse(process(h, "http://example.com/app/", "555+mytopic"));
  }

  @Test
  public void startAndIdSepAndPrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.start, "-", "t");
    Assert.assertTrue(process(h, "http://example.com/app/", "t555-mytopic"));
    Assert.assertFalse(process(h, "http://example.com/app/", "t555+mytopic"));
    Assert.assertFalse(process(h, "http://example.com/app/", "x555-mytopic"));
  }

  @Test
  public void ignorePrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler("forum/", 1, Position.end, null, null);
    Assert.assertTrue(process(h, "http://example.com/app/", "forum/myTopic51"));
    Assert.assertFalse(process(h, "http://example.com/app/", "other/page51"));
  }

  @Test
  public void depthWrong() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 1, Position.end, null, null);
    Assert.assertTrue(process(h, "http://example.com/app/", "forum55/myTopic51"));
    Assert.assertFalse(process(h, "http://example.com/app/", "forum55"));
    Assert.assertFalse(process(h, "http://example.com/app/", "forum55/myTopic51/page1"));
  }

//  @Test
//  public void baseUriTooBig() throws URISyntaxException {
//    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 1, Position.end, null, null);
//    Assert.assertFalse(h.processLink(new URIBuilder("http://example.com"), "http://example.com/"));
//  }

  private boolean process(DirectoryLinkHandler h, String linkBase, String linkRelative)
      throws URISyntaxException {
    return h.processLink(new URIBuilder(linkBase + linkRelative), linkBase);
  }
}
