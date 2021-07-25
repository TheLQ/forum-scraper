package sh.xana.forum.server.parser;

import java.net.URI;
import java.util.UUID;
import org.jsoup.nodes.Document;

public record SourcePage(UUID pageId, String rawHtml, Document doc, URI pageUri) {}
