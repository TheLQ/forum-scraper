package sh.xana.forum.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.PerformanceCounter;
import sh.xana.forum.common.Utils;

public class AuditorDragRace {
  private static final Logger log = LoggerFactory.getLogger(AuditorDragRace.class);
  public static final DocumentBuilderFactory documentBuilderFactory =
      DocumentBuilderFactory.newInstance();

  public static void main(String[] args) throws IOException {
    ServerConfig config = new ServerConfig();

    Mode mode = Mode.valueOf(args[0].toUpperCase());

    List<Path> paths =
        Files.lines(
                Path.of(config.get(config.ARG_FILE_CACHE))
                    .resolve("..")
                    .resolve("filecache-files.txt"))
            .filter(
                e ->
                    switch (mode) {
                      case JSOUP -> e.endsWith(".jsoup");
                      case HTML, XML, DOM -> e.endsWith(".xml");
                      default -> throw new UnsupportedOperationException();
                    })
            .map(
                e -> {
                  if (e.endsWith(".xml") && mode == Mode.HTML) {
                    return e.substring(0, e.length() - 4);
                  } else {
                    return e;
                  }
                })
            // .map(e -> "m:/" + e.substring(4))
            .map(Path::of)
            .collect(Collectors.toList());
    log.info("Found {} paths", paths.size());

    PerformanceCounter counter = new PerformanceCounter(log, 1000);

    //    AuditorExecutor<Path, Void> executor = new AuditorExecutor<>(log);

    Utils.threadRunner(
        16,
        "parser",
        () -> {
          Path path = null;
          try {
            int idx;
            while ((idx = counter.incrementAndLog(paths)) < paths.size()) {
              path = paths.get(idx);

              Document doc = null;
              switch (mode) {
                case HTML -> {
                  String s = Files.readString(path);
                  doc = Jsoup.parse(s, "http://example.com", Parser.htmlParser());
                }
                case XML -> {
                  String s = Files.readString(path);
                  doc = Jsoup.parse(s, "http://example.com", Parser.xmlParser());
                }
                case DOM -> {
                  String s = Files.readString(path);
                  //                  DocumentBuilder documentBuilder =
                  // documentBuilderFactory.newDocumentBuilder();
                  //                  org.w3c.dom.Document parse =
                  // documentBuilder.parse(path.toFile());
                  //                  if (parse.getChildNodes().getLength() == Integer.MAX_VALUE) {
                  //                    throw new UnsupportedOperationException("what?");
                  //                  }
                  //                  Reader stringReader = new StringReader(s);
                  //                  HTMLEditorKit htmlKit = new HTMLEditorKit();
                  //                  HTMLDocument htmlDoc = (HTMLDocument)
                  // htmlKit.createDefaultDocument();
                  //                  htmlKit.read(stringReader, htmlDoc, 0);
                }
                case JSOUP -> {
                  byte[] s = Files.readAllBytes(path);
                  if (s.length == 0) {
                    log.trace("skip");
                    continue;
                  }
                  doc = (Document) new ObjectInputStream(new ByteArrayInputStream(s)).readObject();
                }
                default -> throw new UnsupportedOperationException();
              }

              if (doc != null && doc.childrenSize() == Integer.MAX_VALUE) {
                throw new UnsupportedOperationException("que?");
              }
            }
          } catch (Exception e) {
            log.error("PARSER CRASH " + path, e);
          }
        });
  }

  enum Mode {
    XML,
    HTML,
    JSON,
    DOM,
    JSOUP
  }
}
