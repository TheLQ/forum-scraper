package sh.xana.forum.client;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.DownloadNodeEntry;
import sh.xana.forum.server.WebServer;

public class ClientMain {
  public static final Logger log = LoggerFactory.getLogger(ClientMain.class);
  private static final List<Scraper> nodes = new ArrayList<>();

  public static void main(String[] args) {
    log.info("Client start, getting download node list");

    String result = Utils.serverGet(WebServer.PAGE_CLIENT_NODEINIT);

    DownloadNodeEntry[] downloadEntries;
    try {
      downloadEntries = Utils.jsonMapper.readValue(result, DownloadNodeEntry[].class);
    } catch (Exception e) {
      log.info("Failed to get node entries, closing", e);
      return;
    }
    log.info("nodes {}", downloadEntries);

    for (DownloadNodeEntry entry : downloadEntries) {
      log.info("creating node " + entry.domain());
      Scraper downloader = new Scraper(entry.domain());
      nodes.add(downloader);

      downloader.startThread();
    }
  }
}
