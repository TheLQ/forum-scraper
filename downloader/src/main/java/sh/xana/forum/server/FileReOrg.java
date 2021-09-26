package sh.xana.forum.server;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Re-organize filecache directory to implement and/or verify a/b/abcd... paths */
public class FileReOrg {
  private static final Logger log = LoggerFactory.getLogger(FileReOrg.class);

  public static void main(String[] args) throws Exception {
    ServerConfig config = new ServerConfig();
    Path fileCacheDir = Path.of(config.get(config.ARG_FILE_CACHE));

    // pregenerate hex character list
    String[] hexChars = new String[6 + 10];
    int hexIndex = 0;
    for (char i = 'a'; i <= 'f'; i++) {
      hexChars[hexIndex++] = "" + i;
    }
    for (int i = 0; i <= 9; i++) {
      hexChars[hexIndex++] = "" + i;
    }

    // create directories
    for (String level1 : hexChars) {
      for (String level2 : hexChars) {
        Path dir = fileCacheDir.resolve("" + level1).resolve("" + level2);
        if (!Files.isDirectory(dir)) {
          log.info("mkdir {}", dir);
          Files.createDirectory(dir);
        }
      }
    }

    AtomicInteger counter = new AtomicInteger();
    AtomicInteger movedCounter = new AtomicInteger();
    Files.walk(fileCacheDir)
        .parallel()
        .forEach(
            currentPath -> {
              if (Files.isDirectory(currentPath)) {
                return;
              }

              int idx = counter.incrementAndGet();
              if (idx % 10000 == 0) {
                log.info("Processed {} moved {}", idx, movedCounter);
              }

              String name = currentPath.getFileName().toString();
              Path newPath =
                  fileCacheDir
                      .resolve("" + name.charAt(0))
                      .resolve("" + name.charAt(1))
                      .resolve(name);
              if (!currentPath.equals(newPath)) {
                movedCounter.incrementAndGet();
                try {
                  Files.move(currentPath, newPath);
                } catch (FileAlreadyExistsException ex) {
                  try {
                    if (Files.getLastModifiedTime(currentPath)
                            .compareTo(Files.getLastModifiedTime(newPath))
                        > 0) {
                      log.debug("overwriting older duplicate {}", currentPath);
                      Files.move(currentPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                      log.debug("deleting duplicate {}", currentPath);
                      Files.delete(currentPath);
                    }
                  } catch (IOException ex2) {
                    throw new RuntimeException("nested fail", ex2);
                  }
                } catch (IOException ex) {
                  throw new RuntimeException("io", ex);
                }
              }
            });
  }
}
