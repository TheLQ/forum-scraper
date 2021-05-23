package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.dbutil.DatabaseStorage;
import sh.xana.forum.common.ipc.DownloadResponse;

public record Processor(DatabaseStorage dbStorage) {
  private static final Logger log = LoggerFactory.getLogger(DatabaseStorage.class);
  private static final Path fileCachePath = Path.of("filecache");

  public void processResponses(DownloadResponse[] responses) throws IOException {
    List<UUID> pageIds = new ArrayList<>(responses.length);
    for (DownloadResponse response : responses) {
      log.info("Writing " + response.id().toString() + " response and header");
      Files.write(fileCachePath.resolve(response.id().toString() + ".response"), response.body());
      Files.writeString(
          fileCachePath.resolve(response.id().toString() + ".headers"),
          Utils.jsonMapper.writeValueAsString(response.headers()));

      log.info("Updating database");
      pageIds.add(response.id());
    }
  }
}
