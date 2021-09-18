package sh.xana.forum.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.Utils;

public class NodeManager {
  private static final Logger log = LoggerFactory.getLogger(NodeManager.class);
  private final List<NodeInfo> nodes = new ArrayList<>();

  public UUID registerNode(String ip, String hostname) {
    for (NodeInfo node : nodes) {
      if (node.ip.equals(ip)) {

        String error =
            Utils.format(
                "Duplicate node! New node ip {} hostname {} has same ip as other node {} ip {} hostname {}",
                ip,
                hostname,
                node.id,
                node.ip,
                node.hostname);
        log.error(error);
        throw new RuntimeException(error);
      }
    }

    UUID id = UUID.randomUUID();
    nodes.add(new NodeInfo(id, new Date(), ip, hostname));
    return id;
  }

  public record NodeInfo(UUID id, Date created, String ip, String hostname) {}
}
