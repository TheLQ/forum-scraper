package sh.xana.forum.server.parser;

import org.jsoup.nodes.Document;

public record SourcePage(String rawHtml, Document doc) {}
