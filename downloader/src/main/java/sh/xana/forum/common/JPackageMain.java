package sh.xana.forum.common;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.client.ClientMain;
import sh.xana.forum.server.ServerMain;

public class JPackageMain {
  public static final Logger log = LoggerFactory.getLogger(JPackageMain.class);

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.out.println("jpackagemain <client/server> ...");
      System.exit(1);
    }
    log.info("Client start, getting download node list - {}", args);
    String mode = args[0];
    args = ArrayUtils.subarray(args, 1, args.length);

    switch(mode) {
      case "client" -> ClientMain.main(args);
      case "server" -> ServerMain.main(args);
      default -> throw new RuntimeException("unknown server name");
    }
  }
}
