package sh.xana.forum.server.spider.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import java.io.IOException;

/** Jackson JSON mapper */
public class LinkHandlerIdResolver extends TypeIdResolverBase {

  private JavaType mBaseType;

  @Override
  public void init(JavaType baseType) {
    mBaseType = baseType;
  }

  @Override
  public Id getMechanism() {
    return Id.CUSTOM;
  }

  @Override
  public String idFromValue(Object obj) {
    return idFromValueAndType(obj, obj.getClass());
  }

  @Override
  public String idFromValueAndType(Object obj, Class<?> suggestedType) {
    return obj.getClass().getSimpleName().replace("LinkHandler", "");
  }

  @Override
  public JavaType typeFromId(DatabindContext context, String id) throws IOException {
    try {
      return context.constructSpecializedType(
          mBaseType, Class.forName("sh.xana.forum.server.spider.config." + id + "LinkHandler"));
    } catch (ClassNotFoundException e) {
      throw new IOException("que?", e);
    }
  }
}
