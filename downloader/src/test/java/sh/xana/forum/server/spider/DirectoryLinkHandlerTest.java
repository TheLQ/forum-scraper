package sh.xana.forum.server.spider;

import java.net.URISyntaxException;
import org.testng.Assert;
import org.testng.annotations.Test;
import sh.xana.forum.common.Position;
import sh.xana.forum.server.spider.LinkHandler.Result;

@Test
public class DirectoryLinkHandlerTest {
  @Test
  public void endAndIdPrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.end, null, "t", false);
    Assert.assertTrue(process(h, "http://example.com/app/", "mytopic-t555"));
    Assert.assertFalse(process(h, "http://example.com/app/", "mytopic-x555"));
  }

  @Test
  public void endAndIdSep() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.end, "-", null, false);
    Assert.assertTrue(process(h, "http://example.com/app/", "mytopic-555"));
    Assert.assertFalse(process(h, "http://example.com/app/", "mytopic+555"));
  }

  @Test
  public void endAndIdSepAndPrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.end, "-", "t", false);
    Assert.assertTrue(process(h, "http://example.com/app/", "mytopic-t555"));
    Assert.assertFalse(process(h, "http://example.com/app/", "mytopic+t555"));
    Assert.assertFalse(process(h, "http://example.com/app/", "mytopic-x555"));
  }

  @Test
  public void startAndIdPrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.start, null, "t", false);
    Assert.assertTrue(process(h, "http://example.com/app/", "t555-mytopic"));
    Assert.assertFalse(process(h, "http://example.com/app/", "x555-mytopic"));
  }

  @Test
  public void startAndIdSep() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.start, "-", null, false);
    Assert.assertTrue(process(h, "http://example.com/app/", "555-mytopic"));
    Assert.assertFalse(process(h, "http://example.com/app/", "555+mytopic"));
  }

  @Test
  public void startAndIdSepAndPrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 0, Position.start, "-", "t", false);
    Assert.assertTrue(process(h, "http://example.com/app/", "t555-mytopic"));
    Assert.assertFalse(process(h, "http://example.com/app/", "t555+mytopic"));
    Assert.assertFalse(process(h, "http://example.com/app/", "x555-mytopic"));
  }

  @Test
  public void ignorePrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler("forum/", 1, Position.end, null, null, false);
    Assert.assertTrue(process(h, "http://example.com/app/", "forum/myTopic51"));
    Assert.assertFalse(process(h, "http://example.com/app/", "other/page51"));
  }

  @Test
  public void depthWrong() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 1, Position.end, null, null, false);
    Assert.assertTrue(process(h, "http://example.com/app/", "forum55/myTopic51"));
    Assert.assertFalse(process(h, "http://example.com/app/", "forum55"));
    Assert.assertFalse(process(h, "http://example.com/app/", "forum55/myTopic51/page1"));
  }

//  @Test
//  public void baseUriTooBig() throws URISyntaxException {
//    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 1, Position.end, null, null);
//    Assert.assertFalse(h.processLink(new URIBuilder("http://example.com"), "http://example.com/"));
//  }

  @Test
  public void idPrefix() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 2, Position.end, null, "page-", false);
    Assert.assertTrue(process(h, "http://example.com/app/", "threads/topic-name.123/page-54/"));
  }

  @Test
  public void idOnly() throws URISyntaxException {
    DirectoryLinkHandler h = new DirectoryLinkHandler(null, 1, Position.end, ".", null, false);
    Assert.assertTrue(process(h, "http://example.com/app/", "threads/123/"));
  }

  private boolean process(DirectoryLinkHandler h, String linkBase, String linkRelative)
      throws URISyntaxException {
    return h.processLink(new LinkBuilder(linkBase + linkRelative, linkBase)) == Result.MATCHED;
  }
}
