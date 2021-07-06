package sh.xana.forum.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

public class CommonConfig {
  public static final String PROPERTY_LOGBACK_TYPE = "logbackType";
  public final String ARG_SERVER_AUTH = "server.auth";

  public final String ARG_QUEUE_S3 = "queue.s3";
  public final String ARG_QUEUE_UPLOADNAME = "queue.uploadName";

  private final Properties map = new Properties();

  public CommonConfig(Path specificPropertiesFile) throws IOException {
    map.load(Files.newBufferedReader(specificPropertiesFile));
  }

  public boolean hasArg(String key) {
    String value = map.getProperty(key);
    return StringUtils.isNotBlank(value);
  }

  public String get(String key) {
    return map.getProperty(key);
  }

  public String getRequiredArg(String key) {
    String value = map.getProperty(key);
    if (StringUtils.isBlank(value)) {
      throw new NoSuchElementException(key + " in properties files");
    }
    return value;
  }

  public String getOrDefault(String key, String defaultValue) {
    String value = map.getProperty(key);
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }
    return value;
  }
}
