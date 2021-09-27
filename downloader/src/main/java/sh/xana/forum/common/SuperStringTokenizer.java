package sh.xana.forum.common;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** <b>Reversible</b>, able to read int, lightweight (not DataInputStream) tokenizer */
public class SuperStringTokenizer {
  private static final Logger log = LoggerFactory.getLogger(SuperStringTokenizer.class);
  private final String str;
  private final Position direction;

  private int offset = 0;

  public SuperStringTokenizer(String str, Position direction) {
    this.str = str;
    this.direction = direction;
  }

  @Nullable
  public Integer readUnsignedInt() {
    try {
      return _readUnsignedInt();
    } catch (Exception e) {
      throw new RuntimeException("Failed on url " + str, e);
    }
  }

  @Nullable
  private Integer _readUnsignedInt() {
    char[] buffer = new char[str.length()];
    int bufferPos = 0;

    // Find all digits
    for (; ; ) {
      char characterToTest;
      if (offset + bufferPos >= str.length()) {
        // End-of-line
        break;
      }
      if (direction == Position.start) {
        characterToTest = str.charAt(offset + bufferPos);
      } else {
        characterToTest = str.charAt(str.length() - offset - bufferPos - 1);
      }

      if (Character.isDigit(characterToTest)) {
        buffer[bufferPos++] = characterToTest;
      } else {
        break;
      }
    }
    if (bufferPos == 0) {
      // didn't find anything
      return null;
    }
    offset = offset + bufferPos;

    if (direction == Position.end) {
      // we read the string backwards
      ArrayUtils.reverse(buffer, 0, bufferPos);
    }
    String intStr = new String(buffer, 0, bufferPos);
    return Integer.parseInt(intStr);
  }

  @Nullable
  public String readString(int length) {
    try {
      return _readString(length);
    } catch (Exception e) {
      throw new RuntimeException("Failed on url " + str, e);
    }
  }

  @Nullable
  private String _readString(int length) {
    if (length <= 0) {
      throw new IllegalArgumentException("cannot be <0");
    }
    if (offset + length >= str.length()) {
      return null;
    }

    String actual;
    if (direction == Position.start) {
      actual = str.substring(offset, offset + length);
    } else {
      actual = str.substring(str.length() - offset - length, str.length() - offset);
    }
    offset = offset + length;
    return actual;
  }

  public boolean readStringEquals(@NotNull String expected) {
    if (StringUtils.isEmpty(expected)) {
      throw new IllegalArgumentException("Can't get empty string");
    }
    String actual = readString(expected.length());
    if (actual == null) {
      return false;
    }
    return actual.equals(expected);
  }
}
