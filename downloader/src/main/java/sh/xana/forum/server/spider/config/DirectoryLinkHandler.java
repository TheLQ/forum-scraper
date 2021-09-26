package sh.xana.forum.server.spider.config;

import org.jetbrains.annotations.Nullable;

public record DirectoryLinkHandler(
    @Nullable String pathPrefix,
    int directoryDepth,
    @Nullable String idPosition,
    @Nullable String idSep)
    implements LinkHandler {}
