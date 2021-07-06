package sh.xana.forum.common.ipc;

import java.net.URI;
import java.util.UUID;

public record ScraperDownload(UUID pageId, URI url) implements IScraperRequest {}
