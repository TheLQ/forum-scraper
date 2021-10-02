package sh.xana.forum.server;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.dbutil.ParserPage;

public class AuditorCache implements Iterable<byte[]> {
  private static final Logger log = LoggerFactory.getLogger(AuditorCache.class);

  public static void main(String[] args) throws Exception {
    ServerConfig config = new ServerConfig();
    DatabaseStorage dbStorage = new DatabaseStorage(config);

    AuditorCache cache = new AuditorCache(config);
    cache.writeUrlAll(dbStorage);
  }

  private final Path parserPagePath;
  private final Path parserPageSizePath;
  private final Path urlAllPath;
  private Integer cacheSize = null;
  private Set<String> cachedUrlAll = null;

  public AuditorCache(ServerConfig config) throws IOException {
    parserPagePath = Path.of("parserPage.jsonChain");
    parserPageSizePath = Path.of("parserPage.jsonChainSize");
    urlAllPath = Path.of("urlAll.txt");
    // cachePath = config.getFileCache().resolve("..").resolve("parserPage.jsonChain");
    // cacheSizePath = config.getFileCache().resolve("..").resolve("parserPage.jsonChainSize");

    Utils.jsonMapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
    Utils.jsonMapper.configure(Feature.AUTO_CLOSE_SOURCE, false);
    Utils.jsonMapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false);
  }

  public void writeParserPage(DatabaseStorage dbStorage) throws IOException {
    ObjectOutputStream dataOutput =
        new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(parserPagePath)));

    AtomicInteger counter = new AtomicInteger();
    MutableBoolean running = new MutableBoolean(false);
    log.info("starting");
    dbStorage
        .getParserPagesStream(false, Pages.PAGES.DLSTATUS.in(DlStatus.Parse, DlStatus.Done))
        .peek(
            e -> {
              if (running.isFalse()) {
                log.info("query finished");
                running.setTrue();
              }
            })
        .forEach(
            e -> {
              try {
                counter.incrementAndGet();
                ByteArrayOutputStream myOutput = new ByteArrayOutputStream();
                Utils.jsonMapper.writeValue(myOutput, e);
                dataOutput.writeObject(myOutput.toByteArray());
              } catch (Exception ex) {
                throw new RuntimeException("ex", ex);
              }
            });

    dataOutput.close();

    Files.writeString(parserPageSizePath, counter.toString());
  }

  public void writeUrlAll(DatabaseStorage dbStorage) throws IOException {
    log.info("starting");
    List<URI> pageUrlsOnly = dbStorage.getPageUrlsOnly();
    log.info("fetched {} size", pageUrlsOnly.size());
    Files.write(urlAllPath, (Iterable<String>) pageUrlsOnly.stream().map(URI::toString)::iterator);
    log.info("done");
  }

  public class RawIterator implements Iterator<byte[]>, AutoCloseable {
    private static final ParserPage NULL_PAGE =
        new ParserPage(null, null, null, 0, null, null, null);
    private final ObjectInputStream in;
    private byte[] next = null;

    private RawIterator() {
      try {
        in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(parserPagePath)));
      } catch (Exception e) {
        throw new RuntimeException("failed to create", e);
      }
    }

    @Override
    public boolean hasNext() {
      fetchNext();
      return next != null;
    }

    @Override
    public byte[] next() {
      fetchNext();
      byte[] result = next;
      if (next == null) {
        throw new IllegalStateException("Already at EOF");
      }
      next = null;
      return result;
    }

    private void fetchNext() {
      if (next != null) {
        return;
      }

      try {
        next = (byte[]) in.readObject();
      } catch (EOFException e) {
        try {
          close();
        } catch (Exception e2) {
          throw new RuntimeException("Failed to close " + parserPagePath, e2);
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to read " + parserPagePath, e);
      }
    }

    @Override
    public void close() throws Exception {
      in.close();
    }
  }

  public Iterator<byte[]> iterator() {
    return new RawIterator();
  }

  public ParserPage toPage(byte[] bytes) {
    try {
      return Utils.jsonMapper.readValue(bytes, ParserPage.class);
    } catch (IOException e) {
      throw new RuntimeException("Cannot read", e);
    }
  }

  public int getCacheSize() {
    if (cacheSize == null) {
      String str;
      try {
        str = Files.readString(parserPageSizePath);
      } catch (IOException e) {
        throw new RuntimeException("Failed to read", e);
      }
      cacheSize = Integer.parseInt(str);
    }
    return cacheSize;
  }

  public Set<String> getPageUrls() {
    if (cachedUrlAll == null) {
      try {
        // Performance: This will be processed in multiple threads. Normal HashSet is extremely slow
        // because keySet (the actual set values) are generated *on demand*
        cachedUrlAll = Files.lines(urlAllPath).collect(Collectors.toUnmodifiableSet());
      } catch (Exception e) {
        throw new RuntimeException("Failed to read " + urlAllPath, e);
      }
    }
    return cachedUrlAll;
  }
}
