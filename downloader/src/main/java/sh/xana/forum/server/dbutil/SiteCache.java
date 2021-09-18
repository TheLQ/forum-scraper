package sh.xana.forum.server.dbutil;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.server.db.tables.records.SitesRecord;

public class SiteCache {
  private static final Logger log = LoggerFactory.getLogger(SiteCache.class);
  private final DatabaseStorage dbStorage;
  private final AtomicReference<List<SitesRecord>> sitesRef = new AtomicReference<>();

  public SiteCache(DatabaseStorage dbStorage) {
    this.dbStorage = dbStorage;

    refresh();
  }

  public void refresh() {
    log.debug("refresh...");
    sitesRef.set(dbStorage.getSites());
  }

  public SitesRecord recordById(UUID siteId) {
    return sitesRef.get().stream()
        .filter(e -> e.getSiteid().equals(siteId))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Could not find siteId " + siteId));
  }

  public SitesRecord recordByDomain(String domain) {
    return sitesRef.get().stream()
        .filter(e -> e.getDomain().equals(domain))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Could not find domain " + domain));
  }

  public List<SitesRecord> recordByDomains(Collection<String> domains) {
    return mapByDomains(domains, e -> e);
  }

  public <T> List<T> mapByDomains(Collection<String> domains, Function<SitesRecord, T> mapper) {
    List<T> res =
        sitesRef.get().stream()
            .filter(e -> domains.contains(e.getDomain()))
            .map(mapper)
            .collect(Collectors.toList());
    if (res.size() != domains.size()) {
      throw new RuntimeException("Asked for " + domains.size() + " but found " + res.size());
    }
    return res;
  }
}
