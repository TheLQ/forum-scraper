package sh.xana.forum.server.spider.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record DirectoryLinkHandler(
    @Nullable String pathPrefix,
    int directoryDepth,
    @Nullable Position idPosition,
    @Nullable String idSep,
    @Nullable String idPrefix)
    implements LinkHandler {
  private static final Logger log = LoggerFactory.getLogger(DirectoryLinkHandler.class);

  public DirectoryLinkHandler {
    if (StringUtils.isNotBlank(idPrefix) && idPosition == Position.start) {
      throw new IllegalArgumentException("can't prefix start");
    }
  }

  @Override
  public boolean processLink(URIBuilder link, String linkRelative) {
    // must start with prefix
    if (this.pathPrefix() != null && !linkRelative.startsWith(this.pathPrefix())) {
      return false;
    }

    // path must be at our index
    String[] linkParts = StringUtils.split(linkRelative, "/");
    if (linkParts.length != this.directoryDepth() + 1) {
      log.trace("depth is wrong");
      return false;
    }
    String linkPart = linkParts[this.directoryDepth()];

    // grab number
    int linkOffset = 1;
    Integer id = null;
    for (; true; linkOffset++) {
      String numStr;
      if (this.idPosition() == Position.start) {
        numStr = linkPart.substring(0, linkOffset);
      } else {
        numStr = linkPart.substring(linkPart.length() - linkOffset);
      }
      try {
        id = Integer.parseInt(numStr);
      } catch (NumberFormatException e) {
        // reset back to previous pos
        linkOffset--;
        break;
      }
    }
    if (id == null) {
      log.trace("can't find id");
      return false;
    }

    // check prefix
    if (this.idPrefix() != null) {
      String expectedIdPrefix;
      if (this.idPosition() == Position.start) {
        throw new UnsupportedOperationException();
      } else {
        expectedIdPrefix = linkPart.substring(linkPart.length() - linkOffset - this.idPrefix().length(), linkPart.length() - linkOffset);
      }
      if (!this.idPrefix().equals(expectedIdPrefix)) {
        log.trace("expected prefix {} found {}", this.idPrefix(), expectedIdPrefix);
        return false;
      }
      linkOffset = linkOffset - this.idPrefix().length();
    }

    if (this.idSep() != null) {
      String expectedSep;
      if (this.idPosition() == Position.start) {
        expectedSep = linkRelative.substring(linkOffset, linkOffset + this.idSep().length());
      } else {
        expectedSep = linkRelative.substring(linkPart.length() - linkOffset - this.idSep().length(), linkPart.length() - linkOffset);
      }
      if (!this.idSep().equals(expectedSep)) {
        log.trace("expected prefix {} found {}", this.idSep(), expectedSep);
        return false;
      }
    }
    return true;
  }

  public enum Position {
    start,
    end
  }
}
