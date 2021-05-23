package sh.xana.forum.common.ipc;

import java.util.UUID;

public record DownloadRequest(UUID id, String url) {}
