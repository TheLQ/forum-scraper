package sh.xana.forum.server.spider.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import sh.xana.forum.server.spider.LinkBuilder;

@JsonTypeInfo(use = Id.CUSTOM, property = "type")
@JsonTypeIdResolver(LinkHandlerIdResolver.class)
// TODO Sealed
// public sealed interface LinkHandler permits DirectoryLinkHandler, QueryKeyLinkHandler {
public interface LinkHandler {

  /** @return true if handled */
  boolean processLink(LinkBuilder link);
}
