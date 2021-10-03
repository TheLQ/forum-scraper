package sh.xana.forum.server.spider;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

@JsonTypeInfo(use = Id.CUSTOM, property = "type")
@JsonTypeIdResolver(LinkHandlerIdResolver.class)
public sealed interface LinkHandler permits DirectoryLinkHandler, QueryKeyLinkHandler {

  /** @return true if handled */
  boolean processLink(LinkBuilder link);
}
