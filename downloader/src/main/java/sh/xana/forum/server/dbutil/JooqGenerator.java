package sh.xana.forum.server.dbutil;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.jooq.Converter;
import sh.xana.forum.common.Utils;

/** Run jOOq CodeGen. Automatically handles removing old files too */
public class JooqGenerator {
  //  public static void main(String[] args) throws Exception {
  //    ServerConfig config = new ServerConfig();
  //
  //    // Use full class paths to for easy import
  //    org.jooq.meta.jaxb.Configuration jooqConfig =
  //        new org.jooq.meta.jaxb.Configuration()
  //            .withJdbc(
  //                new Jdbc()
  //                    .withUrl(config.get(config.ARG_DB_CONNECTIONSTRING))
  //                    .withUser(config.get(config.ARG_DB_USER))
  //                    .withPassword(config.get(config.ARG_DB_PASS)))
  //            .withGenerator(
  //                new org.jooq.meta.jaxb.Generator()
  //                    .withDatabase(
  //                        new org.jooq.meta.jaxb.Database()
  //                            // .withName("org.jooq.meta.sqlite.SQLiteDatabase")
  //                            .withName("org.jooq.meta.mariadb.MariaDBDatabase")
  //                            .withIncludes(".*")
  //                            .withInputSchema("forum-scrape")
  //                            .withForcedTypes(
  //                                new ForcedType()
  //                                    .withUserType("java.util.UUID")
  //                                    .withConverter(
  //
  // "sh.xana.forum.server.dbutil.JooqGenerator.UUIDConverter")
  //                                    .withIncludeExpression(".*[iI]d"),
  //                                new ForcedType()
  //                                    .withUserType(
  //                                        "sh.xana.forum.server.dbutil.DatabaseStorage.PageType")
  //                                    .withIncludeExpression("pageType")
  //                                    .withEnumConverter(true),
  //                                new ForcedType()
  //                                    .withUserType(
  //                                        "sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus")
  //                                    .withEnumConverter(true)
  //                                    .withIncludeExpression("dlstatus"),
  //                                new ForcedType()
  //                                    .withUserType(
  //                                        "sh.xana.forum.server.dbutil.DatabaseStorage.ForumType")
  //                                    .withEnumConverter(true)
  //                                    .withIncludeExpression("ForumType"),
  //                                new ForcedType()
  //                                    .withUserType("java.net.URI")
  //                                    .withConverter(
  //
  // "sh.xana.forum.server.dbutil.JooqGenerator.UriConverter")
  //                                    .withIncludeExpression("url")))
  //                    .withTarget(
  //                        new org.jooq.meta.jaxb.Target()
  //                            .withPackageName("sh.xana.forum.server.db")
  //                            .withDirectory("src/main/java")));
  //    org.jooq.codegen.GenerationTool.generate(jooqConfig);
  //  }

  public static class UriConverter implements Converter<String, URI> {

    @Override
    public URI from(String databaseObject) {
      return Utils.toURI(databaseObject);
    }

    @Override
    public String to(URI userObject) {
      return userObject.toString();
    }

    @Override
    public Class<String> fromType() {
      return String.class;
    }

    @Override
    public Class<URI> toType() {
      return URI.class;
    }
  }

  /**
   * For some reason, forcing the type to UUID doesn't make jOOq automatically convert, so need our
   * own converter.
   */
  public static class UUIDConverter implements Converter<byte[], UUID> {

    @Override
    public UUID from(byte[] databaseObject) {
      // Avoid breaking ByteBuffer.wrap
      // We never have null UUIDs, maybe it's from object init?
      if (databaseObject == null) {
        return null;
      }
      return uuidFromBytes(databaseObject);
    }

    @Override
    public byte[] to(UUID userObject) {
      // Avoid breaking ByteBuffer.wrap
      // This REALLY should never be null, wtf jOOq
      if (userObject == null) {
        return null;
      }
      return uuidAsBytes(userObject);
    }

    @Override
    public Class<byte[]> fromType() {
      return byte[].class;
    }

    @Override
    public Class<UUID> toType() {
      return UUID.class;
    }

    private static UUID uuidFromBytes(byte[] bytes) {
      ByteBuffer bb = ByteBuffer.wrap(bytes);
      long firstLong = bb.getLong();
      long secondLong = bb.getLong();
      return new UUID(firstLong, secondLong);
    }

    private static byte[] uuidAsBytes(UUID uuid) {
      ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
      bb.putLong(uuid.getMostSignificantBits());
      bb.putLong(uuid.getLeastSignificantBits());
      return bb.array();
    }
  }
}
