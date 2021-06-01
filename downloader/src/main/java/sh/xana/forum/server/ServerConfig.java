package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.Path;
import sh.xana.forum.common.CommonConfig;

public class ServerConfig extends CommonConfig {
  public final String ARG_FILE_CACHE = "fileCache";
  public final String ARG_NODE_CMD = "nodeCmd";
  public final String ARG_PARSER_SCRIPT = "parserScript";

  public final String ARG_DB_CONNECTIONSTRING = "db.connectionString";
  public final String ARG_DB_USER = "db.user";
  public final String ARG_DB_PASS = "db.pass";

  public ServerConfig() throws IOException {
    super(Path.of("config-server.properties"));
  }
}
