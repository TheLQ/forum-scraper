// package sh.xana.forum.server.dbutil;
//
// import org.jooq.codegen.GenerationTool;
// import org.jooq.meta.jaxb.*;
//
/// ** Run jOOq CodeGen. Automatically handles removing old files too */
// public class JooqGenerator {
//  public static void main(String[] args) throws Exception {
//    Configuration config =
//        new Configuration()
//            .withJdbc(new Jdbc().withDriver("org.sqlite.JDBC").withUrl("jdbc:sqlite:sample.db"))
//            .withGenerator(
//                new Generator()
//                    .withDatabase(
//                        new Database()
//                            .withName("org.jooq.meta.sqlite.SQLiteDatabase")
//                            .withIncludes(".*"))
//                    .withTarget(
//                        new Target()
//                            .withPackageName("sh.xana.forum.server.db")
//                            .withDirectory("src/main/java")));
//    GenerationTool.generate(config);
//  }
// }
