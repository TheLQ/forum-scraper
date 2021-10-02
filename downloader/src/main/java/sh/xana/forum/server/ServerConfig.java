package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import sh.xana.forum.common.CommonConfig;

public class ServerConfig extends CommonConfig {
  public final String ARG_PARSER_SERVER = "parser.server";
  public final String ARG_FILE_CACHE = "fileCache";

  public final String ARG_DB_CONNECTIONSTRING = "db.connectionString";
  public final String ARG_DB_USER = "db.user";
  public final String ARG_DB_PASS = "db.pass";

  public ServerConfig() throws IOException {
    super(Path.of("config-server.properties"));
  }

  public Path getPagePath(UUID pageId) {
    return getPathFolder(pageId).resolve(pageId + ".response");
  }

  public Path getPageXMLPath(UUID pageId) {
    return getPathFolder(pageId).resolve(pageId + ".response.xml");
  }

  public Path getPageJsoupPath(UUID pageId) {
    return getPathFolder(pageId).resolve(pageId + ".response.jsoup");
  }

  public Path getPageHeaderPath(UUID pageId) {
    return getPathFolder(pageId).resolve(pageId + ".headers");
  }

  private Path getPathFolder(UUID pageId) {
    String pageIdStr = pageId.toString();
    return Path.of(get(ARG_FILE_CACHE), "" + pageIdStr.charAt(0), "" + pageIdStr.charAt(1));
  }

  public Path getFileCache() {
    return Path.of(get(ARG_FILE_CACHE));
  }
}
