package sh.xana.forum.server.spider.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Position;
import sh.xana.forum.common.SuperStringTokenizer;

public record DirectoryLinkHandler(
    @Nullable String pathPrefix,
    int directoryDepth,
    @Nullable Position idPosition,
    @Nullable String idSep,
    @Nullable String idPrefix)
    implements LinkHandler {
  private static final Logger log = LoggerFactory.getLogger(DirectoryLinkHandler.class);

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
    SuperStringTokenizer linkTok = new SuperStringTokenizer(linkPart, idPosition());

    Integer id;
    boolean checkResult;
    if (idPosition() == Position.start) {
      checkResult = checkPrefix(linkTok);
      id = extractId(linkTok);
    } else {
      id = extractId(linkTok);
      checkResult = checkPrefix(linkTok);
    }
    if (id == null || !checkResult) {
      return false;
    }

    if (this.idSep() != null) {
      if (!linkTok.readStringEquals(this.idSep())) {
        log.trace("missing prefix");
        return false;
      }
    }
    return true;
  }

  private Integer extractId(SuperStringTokenizer linkTok) {
    // grab number
    Integer id = linkTok.readUnsignedInt();
    if (id == null) {
      log.trace("can't find id");
    }
    return id;
  }

  private boolean checkPrefix(SuperStringTokenizer linkTok) {
    // check prefix
    if (this.idPrefix() != null) {
      if (!linkTok.readStringEquals(this.idPrefix())) {
        log.trace("missing prefix");
        return false;
      }
    }
    return true;
  }
}
