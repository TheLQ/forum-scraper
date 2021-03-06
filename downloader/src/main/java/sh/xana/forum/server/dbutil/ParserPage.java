package sh.xana.forum.server.dbutil;

import java.net.URI;
import java.util.UUID;

public record ParserPage(
    UUID pageId,
    URI pageUri,
    PageType pageType,
    int dlstatusCode,
    UUID siteId,
    URI siteUrl,
    ForumType forumType) {}
