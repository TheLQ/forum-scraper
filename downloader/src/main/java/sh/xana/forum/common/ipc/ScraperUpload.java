package sh.xana.forum.common.ipc;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ScraperUpload(
    UUID pageId,
    String exception,
    URI pageUrl,
    List<URI> redirectList,
    byte[] body,
    Map<String, List<String>> headers,
    int responseCode)
    implements IScraperRequest {}
