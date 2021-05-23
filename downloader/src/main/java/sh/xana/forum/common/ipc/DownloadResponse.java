package sh.xana.forum.common.ipc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DownloadResponse(
    UUID id, byte[] body, Map<String, List<String>> headers, int responseCode, Exception e) {}
