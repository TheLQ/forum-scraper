package sh.xana.forum.server.spider;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
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
  public Result processLink(LinkBuilder link) {
    // must start with prefix
    if (this.pathPrefix() != null && !link.relativeLink().startsWith(this.pathPrefix())) {
      log.trace("{} does not start {} with prefix {}", link, link.relativeLink(), this.pathPrefix());
      return Result.FAILED;
    }

    // must end with dir slash (everyone does it?)
    link.pathMustEndWithSlash();

    // strip extras
    for (NameValuePair queryParam : link.queryParams()) {
      log.trace("{} stripping key {}", link, queryParam.getName());
    }
    link.queryParams(List.of());

    // path must be at our index
    String[] linkParts = StringUtils.split(link.relativeLink(), "/");

    String linkPart = linkParts[this.directoryDepth()];
    SuperStringTokenizer linkTok = new SuperStringTokenizer(linkPart, idPosition());

    Integer id;
    boolean checkResult;
    if (idPosition() == Position.start) {
      checkResult = checkPrefix(linkTok, link);
      id = extractId(linkTok, link);
    } else {
      id = extractId(linkTok, link);
      checkResult = checkPrefix(linkTok, link);
    }
    if (id == null || !checkResult) {
      return Result.FAILED;
    }

    if (this.idSep() != null) {
      if (!linkTok.readStringEquals(this.idSep())) {
        log.trace("{} missing sep {}", link, this.idSep());
        return Result.FAILED;
      }
    }

    if (linkParts.length != this.directoryDepth() + 1) {
      log.trace("{} depth is wrong", link);
      return Result.MATCHED_PARTIAL;
    } else {
      return Result.MATCHED;
    }
  }

  private Integer extractId(SuperStringTokenizer linkTok, LinkBuilder link) {
    // grab number
    Integer id = linkTok.readUnsignedInt();
    if (id == null) {
      log.trace("{} can't find id", link);
    }
    return id;
  }

  private boolean checkPrefix(SuperStringTokenizer linkTok, LinkBuilder link) {
    // check prefix
    if (this.idPrefix() != null) {
      if (!linkTok.readStringEquals(this.idPrefix())) {
        log.trace("{} missing prefix {}", link, this.idPrefix());
        return false;
      }
    }
    return true;
  }
}
