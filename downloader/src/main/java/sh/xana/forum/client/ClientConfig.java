package sh.xana.forum.client;

import java.io.IOException;
import java.nio.file.Path;
import sh.xana.forum.common.CommonConfig;

public class ClientConfig extends CommonConfig {
  public final String ARG_SERVER_ADDRESS = "server.address";
  public final String ARG_ISAWS = "isAws";

  public ClientConfig() throws IOException {
    super(Path.of("config-client.properties"));
  }
}
