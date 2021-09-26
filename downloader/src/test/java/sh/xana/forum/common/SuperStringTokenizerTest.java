package sh.xana.forum.common;

import static org.testng.Assert.*;

import org.testng.annotations.Test;

public class SuperStringTokenizerTest {
  @Test
  public void startNumber() {
    // - (minus) asserts that int is unsigned
    SuperStringTokenizer tok = new SuperStringTokenizer("1234abc-5d", Position.start);
    assertEquals(tok.readUnsignedInt(), (Integer) 1234);
    assertEquals(tok.readString(4), "abc-");
    assertEquals(tok.readUnsignedInt(), (Integer) 5);
    assertEquals(tok.readString(1), "d");
  }

  @Test
  public void startChar() {
    SuperStringTokenizer tok = new SuperStringTokenizer("abc-1234d5", Position.start);
    assertEquals(tok.readString(4), "abc-");
    assertEquals(tok.readUnsignedInt(), (Integer) 1234);
    assertEquals(tok.readString(1), "d");
    assertEquals(tok.readUnsignedInt(), (Integer) 5);
  }

  @Test
  public void endNumber() {
    // - (minus) asserts that int is unsigned
    SuperStringTokenizer tok = new SuperStringTokenizer("1234abc-5d", Position.end);
    assertEquals(tok.readString(1), "d");
    assertEquals(tok.readUnsignedInt(), (Integer) 5);
    assertEquals(tok.readString(4), "abc-");
    assertEquals(tok.readUnsignedInt(), (Integer) 1234);
  }

  @Test
  public void endChar() {
    SuperStringTokenizer tok = new SuperStringTokenizer("abc-1234d5", Position.end);
    assertEquals(tok.readUnsignedInt(), (Integer) 5);
    assertEquals(tok.readString(1), "d");
    assertEquals(tok.readUnsignedInt(), (Integer) 1234);
    assertEquals(tok.readString(4), "abc-");
  }
}