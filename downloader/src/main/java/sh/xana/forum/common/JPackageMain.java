package sh.xana.forum.common;

import sh.xana.forum.client.ClientMain;
import sh.xana.forum.server.ServerMain;

public class JPackageMain {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.out.println("jpackagemain <client/server> ...");
    }

    switch(args[0]) {
      case "client" -> ClientMain.main(args);
      case "server" -> ServerMain.main(args);
      default -> throw new RuntimeException("unknown server name");
    }
  }
}
