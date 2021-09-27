package sh.xana.forum.common.ipc;

import sh.xana.forum.server.dbutil.PageType;

public record Subpage(String link, PageType pageType) {}
