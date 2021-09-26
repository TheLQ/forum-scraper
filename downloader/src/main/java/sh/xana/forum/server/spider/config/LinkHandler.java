package sh.xana.forum.server.spider.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import org.apache.http.client.utils.URIBuilder;

@JsonTypeInfo(use = Id.CUSTOM, property = "type")
@JsonTypeIdResolver(LinkHandlerIdResolver.class)
public interface LinkHandler {

  /** @return true if handled */
  boolean processLink(URIBuilder link, String linkRelative);
}
