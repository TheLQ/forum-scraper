package sh.xana.forum.server;

import sh.xana.forum.common.dbutil.DatabaseStorage;

public class ServerMain {
  public static void main(String[] args) throws Exception {
    // Hide giant logo it writes to logs on first load
    System.setProperty("org.jooq.no-logo", "true");

    DatabaseStorage dbStorage = new DatabaseStorage();
    Processor processor = new Processor(dbStorage);

    WebServer server = new WebServer(dbStorage, processor);
    processor.startSpiderThread();
  }
}
