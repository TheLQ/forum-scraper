package sh.xana.forum.server.dbutil;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.server.db.tables.records.SitesRecord;

public class SiteCache {
  private static final Logger log = LoggerFactory.getLogger(SiteCache.class);
  private final DatabaseStorage dbStorage;
  private final AtomicReference<List<SitesRecord>> sitesRef = new AtomicReference<>();

  public SiteCache(DatabaseStorage dbStorage) {
    this.dbStorage = dbStorage;
  }

  public void refresh() {
    sitesRef.set(dbStorage.getSites());
  }

  private List<SitesRecord> lazyGet() {
    List<SitesRecord> res = sitesRef.get();
    if (res == null) {
      refresh();
    }
    return sitesRef.get();
  }

  public SitesRecord recordById(UUID siteId) {
    return lazyGet().stream()
        .filter(e -> e.getSiteid().equals(siteId))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Could not find siteId " + siteId));
  }

  public SitesRecord recordByDomain(String domain) {
    return lazyGet().stream()
        .filter(e -> e.getDomain().equals(domain))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Could not find domain " + domain));
  }

  public List<SitesRecord> recordByDomains(Collection<String> domains) {
    return mapByDomains(domains, e -> e);
  }

  public List<String> siteUrlsByDomains(Collection<String> domains) {
    List<String> res =
        lazyGet().stream()
            .filter(e -> domains.contains(e.getDomain()))
            .map(SitesRecord::getSiteurl)
            .map(URI::toString)
            .collect(Collectors.toList());
    if (res.size() != domains.size()) {
      throw new RuntimeException("Asked for " + domains.size() + " but found " + res.size());
    }
    return res;
  }

  public <T> List<T> mapByDomains(Collection<String> domains, Function<SitesRecord, T> mapper) {
    List<T> res =
        lazyGet().stream()
            .filter(e -> domains.contains(e.getDomain()))
            .map(mapper)
            .collect(Collectors.toList());
    if (res.size() != domains.size()) {
      throw new RuntimeException("Asked for " + domains.size() + " but found " + res.size());
    }
    return res;
  }

  public <T> List<T> filterMap(Predicate<SitesRecord> include, Function<SitesRecord, T> mapper) {
    return lazyGet().stream().filter(include).map(mapper).collect(Collectors.toList());
  }

  public List<UUID> idsByForumType(ForumType... forumTypes) {
    return lazyGet().stream()
        .filter(e -> ArrayUtils.contains(forumTypes, e.getForumtype()))
        .map(SitesRecord::getSiteid)
        .collect(Collectors.toList());
  }
}
