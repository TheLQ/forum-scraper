package sh.xana.forum.server.spider;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Position;
import sh.xana.forum.common.SuperStringTokenizer;
import sh.xana.forum.server.spider.config.LinkHandler;

public record DirectoryLinkHandler(
    @Nullable String pathPrefix,
    int directoryDepth,
    @Nullable Position idPosition,
    @Nullable String idSep,
    @Nullable String idPrefix)
    implements LinkHandler {
  private static final Logger log = LoggerFactory.getLogger(DirectoryLinkHandler.class);

  @Override
  public boolean processLink(LinkBuilder link) {
    // must start with prefix
    if (this.pathPrefix() != null && !link.relativeLink().startsWith(this.pathPrefix())) {
      return false;
    }

    // must end with dir slash (everyone does it?)
    link.pathMustEndWithSlash();

    // strip extras
    for (NameValuePair queryParam : link.queryParams()) {
      log.trace("stripping key {} from {}", queryParam.getName(), link);
    }
    link.queryParams(List.of());

    // path must be at our index
    String[] linkParts = StringUtils.split(link.relativeLink(), "/");
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