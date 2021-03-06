package sh.xana.forum.server.dbutil;

import java.net.URI;
import org.jetbrains.annotations.NotNull;
import org.jooq.Converter;
import sh.xana.forum.common.Utils;

public class UriConverter implements Converter<String, URI> {

  @Override
  public URI from(String databaseObject) {
    return Utils.toURI(databaseObject);
  }

  @Override
  public String to(URI userObject) {
    return userObject.toString();
  }

  @Override
  public @NotNull Class<String> fromType() {
    return String.class;
  }

  @Override
  public @NotNull Class<URI> toType() {
    return URI.class;
  }
}
