package sh.xana.forum.server.dbutil;

import java.net.URI;
import org.jooq.Converter;
import sh.xana.forum.common.Utils;
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;
import sh.xana.forum.server.dbutil.DatabaseStorage.PageType;

/** Run jOOq CodeGen. Automatically handles removing old files too */
public class JooqGenerator {
  //  public static void main(String[] args) throws Exception {
  //    Configuration config =
  //        new Configuration()
  //            .withJdbc(new Jdbc().withDriver("org.sqlite.JDBC").withUrl("jdbc:sqlite:sample.db"))
  //            .withGenerator(
  //                new Generator()
  //                    .withDatabase(
  //                        new Database()
  //                            .withName("org.jooq.meta.sqlite.SQLiteDatabase")
  //                            .withIncludes(".*")
  //                            .withForcedTypes(
  //                                new
  // ForcedType().withName("UUID").withIncludeExpression(".*[iI]d"),
  //                                new ForcedType()
  //                                    .withUserType(
  //                                        "sh.xana.forum.server.dbutil.DatabaseStorage.PageType")
  //                                    .withConverter(
  //
  // "sh.xana.forum.server.dbutil.JooqGenerator.PageTypeConverter")
  //                                    .withIncludeExpression("pageType"),
  //                                new ForcedType()
  //                                    .withUserType(
  //                                        "sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus")
  //                                    .withConverter(
  //
  // "sh.xana.forum.server.dbutil.JooqGenerator.DlStatusConverter")
  //                                    .withIncludeExpression("dlstatus"),
  //                                new ForcedType()
  //                                    .withUserType("java.net.URI")
  //                                    .withConverter(
  //
  // "sh.xana.forum.server.dbutil.JooqGenerator.UriConverter")
  //                                    .withIncludeExpression("url")))
  //                    .withTarget(
  //                        new Target()
  //                            .withPackageName("sh.xana.forum.server.db")
  //                            .withDirectory("src/main/java")));
  //    GenerationTool.generate(config);
  //  }

  private abstract static class EnumConverter<T extends Enum<T>> implements Converter<String, T> {

    private final Class<T> clazz;

    EnumConverter(Class<T> clazz) {
      this.clazz = clazz;
    }

    @Override
    public T from(String databaseObject) {
      return Enum.valueOf(clazz, databaseObject);
    }

    @Override
    public String to(T userObject) {
      return userObject.toString();
    }

    @Override
    public Class<String> fromType() {
      return String.class;
    }

    @Override
    public Class<T> toType() {
      return clazz;
    }
  }

  public static class PageTypeConverter extends EnumConverter<PageType> {

    public PageTypeConverter() {
      super(PageType.class);
    }
  }

  public static class DlStatusConverter extends EnumConverter<DlStatus> {

    public DlStatusConverter() {
      super(DlStatus.class);
    }
  }

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
}
