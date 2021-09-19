package sh.xana.forum.server.threads;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.AbstractTaskThread;
import sh.xana.forum.common.RecieveRequest;
import sh.xana.forum.common.SqsManager;
import sh.xana.forum.common.Utils;
import sh.xana.forum.common.ipc.ScraperUpload;
import sh.xana.forum.server.ServerConfig;
import sh.xana.forum.server.db.tables.records.PageredirectsRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage;

/** Process upload requests in SQS Queue from Clients containing page data */
public class PageUploadsThread extends AbstractTaskThread {
  private static final Logger log = LoggerFactory.getLogger(PageUploadsThread.class);
  private static int THREAD_COUNTER = 0;
  private final ServerConfig config;
  private final SqsManager sqsManager;
  private final DatabaseStorage dbStorage;

  public PageUploadsThread(ServerConfig config, DatabaseStorage dbStorage, SqsManager sqsManager) {
    super("PageUploads-" + THREAD_COUNTER++, TimeUnit.MINUTES.toMillis(1));
    this.config = config;
    this.dbStorage = dbStorage;
    this.sqsManager = sqsManager;
  }

  @Override
  protected boolean runCycle() throws IOException {
    List<RecieveRequest<ScraperUpload>> recieveRequests = sqsManager.receiveUploadRequests();
    if (recieveRequests.isEmpty()) {
      log.debug("No uploads to process");
      return true;
    }

    processUploads(recieveRequests);
    return true;
  }

  private void processUploads(List<RecieveRequest<ScraperUpload>> recieveRequests)
      throws IOException {
    List<PageredirectsRecord> sqlNewRedirects = new ArrayList<>();
    for (RecieveRequest<ScraperUpload> successMessage : recieveRequests) {
      ScraperUpload success = successMessage.obj();
      log.debug("Writing " + success.pageId().toString() + " response and header");

      // pageId could potentially be old. Make sure it's still current to avoid writing trash
      try {
        dbStorage.getPage(success.pageId());
      } catch (Exception e) {
        log.error(
            "Failed to get page "
                + success.pageId()
                + " url "
                + success.pageUrl()
                + " headers "
                + success.headers(),
            e);
        continue;
      }

      // Body and headers can be null if the request just failed
      byte[] body = success.body();
      body = body == null ? new byte[0] : body;

      String headers;
      if (success.headers() == null) {
        headers = "";
      } else {
        headers = Utils.jsonMapper.writeValueAsString(success.headers());
      }

      Files.write(config.getPagePath(success.pageId()), body);
      Files.writeString(config.getPageHeaderPath(success.pageId()), headers);

      dbStorage.movePageDownloadToParse(success.pageId(), success.responseCode());

      if (!success.redirectList().isEmpty()) {
        byte counter = 0;
        URI lastUri = null;
        for (URI newUri : success.redirectList()) {
          sqlNewRedirects.add(new PageredirectsRecord(success.pageId(), newUri, counter++));
          lastUri = newUri;
        }
        try {
          dbStorage.setPageURL(success.pageId(), lastUri);
        } catch (Exception e) {
          if (e.getCause() instanceof SQLIntegrityConstraintViolationException
              && e.getCause().getMessage().contains("Duplicate entry")) {
            // we have redirected to an existing page. So we don't need this anymore
            dbStorage.deletePage(success.pageId());
            continue;
          } else {
            throw e;
          }
        }
      }

      if (success.exception() != null) {
        dbStorage.setPageException(success.pageId(), success.exception());
      }
    }

    if (!sqlNewRedirects.isEmpty()) {
      log.debug("dbsync redirect");
      dbStorage.insertPageRedirects(sqlNewRedirects);
    }

    sqsManager.deleteUploadRequests(recieveRequests);
  }
}
