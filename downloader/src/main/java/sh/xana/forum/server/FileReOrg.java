package sh.xana.forum.server;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileReOrg {
  private static final Logger log = LoggerFactory.getLogger(FileReOrg.class);

  public static void main(String[] args) throws Exception {
    ServerConfig config = new ServerConfig();

    String[] hexChars = new String[6 + 10];
    int hexIndex = 0;
    for (char i = 'a'; i <= 'f'; i++) {
      hexChars[hexIndex++] = "" + i;
    }
    for (int i = 0; i <= 9; i++) {
      hexChars[hexIndex++] = "" + i;
    }

    Path fileCacheDir = Path.of(config.get(config.ARG_FILE_CACHE));
    for (String outer : hexChars) {
      for (String inner : hexChars) {
        try {
          Path dir = fileCacheDir.resolve("" + outer).resolve("" + inner);
          log.info("mkdir {}", dir);
          Files.createDirectory(dir);
        } catch (FileAlreadyExistsException e) {
          // ignore...
        }
      }
    }

    for (String outer : hexChars) {
      log.info("dir {}", outer);
      Path outerDir = fileCacheDir.resolve("" + outer);
      Files.list(outerDir)
          .forEach(
              e -> {
                if (Files.isDirectory(e)) {
                  return;
                }
                String name = e.getFileName().toString();
                Path newPath = outerDir.resolve("" + name.charAt(1)).resolve(name);
                try {
                  Files.move(e, newPath);
                } catch (Exception ex) {
                  throw new RuntimeException(ex);
                }
              });
    }
  }
}
