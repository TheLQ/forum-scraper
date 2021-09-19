package sh.xana.forum.server;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.AuditorExecutor;
import sh.xana.forum.common.Utils;
import sh.xana.forum.server.db.tables.Pages;
import sh.xana.forum.server.db.tables.records.DatasetRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;
import sh.xana.forum.server.dbutil.DlStatus;
import sh.xana.forum.server.parser.PageParser;

/**
 * Concatenate all filecache files to avoid open/close overhead and excessive seeks in audit runs
 *
 * <p>Abandoned after switching from RAID-0 HDDs to an SSD. But may be needed in the future
 */
public class CompressedDataset {
  private static final Logger log = LoggerFactory.getLogger(CompressedDataset.class);
  private final Path datasetDir;
  private final DatabaseStorage dbStorage;

  public CompressedDataset(DatabaseStorage dbStorage) {
    this.dbStorage = dbStorage;
    // datasetDir = Path.of("/mnt/forum");
    datasetDir = Path.of("C:\\Users\\leon\\tmp");
  }

  public static void main(String[] args) throws Exception {
    ServerConfig config = new ServerConfig();
    DatabaseStorage dbStorage = new DatabaseStorage(config);
    PageParser parser = new PageParser(config);

    CompressedDataset impl = new CompressedDataset(dbStorage);
    impl.write(config, parser);
  }

  private record PageData(byte[] in, UUID pageId) {}

  public void write(ServerConfig config, PageParser parser) throws Exception {
    Path cache = Path.of("datasetcache.json");
    List<UUID> pages;
    if (Files.exists(cache)) {
      log.info("Loading query cache");
      pages = Utils.jsonMapper.readValue(cache.toFile(), new TypeReference<List<UUID>>() {});
    } else {
      log.info("query start");
      pages = dbStorage.getPageIdsOnly(Pages.PAGES.DLSTATUS.in(DlStatus.Parse, DlStatus.Done));
      log.info("writing cache cache");
      Utils.jsonMapper.writeValue(cache.toFile(), pages);
    }
    log.info("loaded " + pages.size());

    Path datasetCsv = datasetDir.resolve("dataset.csv");
    BufferedWriter datasetCsvOut = Files.newBufferedWriter(datasetCsv);
    datasetCsvOut.write("uuidHex,byteStart,byteLength" + System.lineSeparator());

    Path dataset = datasetDir.resolve("dataset.dat");
    log.info("writing to {}", dataset);
    BufferedOutputStream datasetOut = new BufferedOutputStream(Files.newOutputStream(dataset));

    AtomicInteger pos = new AtomicInteger(0);
    AuditorExecutor<UUID, PageData> executor = new AuditorExecutor<>(log);
    executor.run(
        pages,
        16,
        (pageId) -> new PageData(Files.readAllBytes(config.getPagePath(pageId)), pageId),
        1,
        (pageData) -> {
          //      datasetOut.write(input.in);
          //      datasetDb.add(new DatasetRecord(input.pageId, pos, (long) input.in.length));
          //      datasetCsvOut.write(
          //          input.pageId.toString() + "," + pos + "," + input.in.length +
          // System.lineSeparator());
          //      datasetCsvOut.newLine();
          pos.addAndGet(pageData.in.length);

          //      if (datasetDb.size() != 0 && datasetDb.size() % 1000 == 0) {
          //        log.debug("flush dataset db");
          //        dbStorage.insertDataset(datasetDb);
          //        datasetDb.clear();
          //      }
        });

    //    dbStorage.insertDataset(datasetDb);
    datasetOut.close();
    datasetCsvOut.close();
  }

  public byte[] read(UUID pageId) throws Exception {
    RandomAccessFile file = new RandomAccessFile("asdf", "r");

    DatasetRecord record = dbStorage.getDataset(pageId);
    file.seek(record.getBytestart());
    // LOSSY: but shouldn't (hopefully) have >2GB files
    byte[] result = new byte[(int) (long) record.getBytelength()];
    if (file.read(result) == -1) {
      throw new RuntimeException("EOF fail");
    }
    return result;
  }
}
