package sh.xana.forum.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;

public class OneTimeProcessor {
private static final Logger log = LoggerFactory.getLogger(OneTimeProcessor.class);

  public static void main(String[] args) throws Exception {
    ServerConfig config = new ServerConfig();

    DatabaseStorage dbStorage = new DatabaseStorage(config);

    log.info("start error");
    var ids = Files.lines(Path.of("empty.txt"))
        .map(UUID::fromString)
        .collect(Collectors.toList());
    dbStorage.setPageStatus(ids, DlStatus.Queued);
    for (UUID id : ids) {
      dbStorage.setPageExceptionNull(id);
    }
    log.info("end");
  }
}
