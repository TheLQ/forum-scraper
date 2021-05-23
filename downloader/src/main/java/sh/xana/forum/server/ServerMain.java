package sh.xana.forum.server;

import sh.xana.forum.common.dbutil.DatabaseStorage;

public class ServerMain {
  public static void main(String[] args) throws Exception {
    DatabaseStorage dbStorage = new DatabaseStorage();

    WebServer server = new WebServer(new WebPages(dbStorage));
  }
}
