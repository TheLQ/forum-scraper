/*
 * This file is generated by jOOQ.
 */
package sh.xana.forum.server.db.tables;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row5;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.EnumConverter;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import sh.xana.forum.server.db.ForumScrape;
import sh.xana.forum.server.db.Keys;
import sh.xana.forum.server.db.tables.records.SitesRecord;
import sh.xana.forum.server.dbutil.ForumType;
import sh.xana.forum.server.dbutil.UriConverter;
import sh.xana.forum.server.dbutil.UuidConverter;

/** This class is generated by jOOQ. */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class Sites extends TableImpl<SitesRecord> {

  private static final long serialVersionUID = 1L;

  /** The reference instance of <code>forum-scrape.Sites</code> */
  public static final Sites SITES = new Sites();

  /** The class holding records for this type */
  @Override
  public Class<SitesRecord> getRecordType() {
    return SitesRecord.class;
  }

  /** The column <code>forum-scrape.Sites.siteId</code>. */
  public final TableField<SitesRecord, UUID> SITEID =
      createField(
          DSL.name("siteId"),
          SQLDataType.BINARY(16).nullable(false),
          this,
          "",
          new UuidConverter());

  /** The column <code>forum-scrape.Sites.siteUrl</code>. */
  public final TableField<SitesRecord, URI> SITEURL =
      createField(
          DSL.name("siteUrl"),
          SQLDataType.VARCHAR(255).nullable(false),
          this,
          "",
          new UriConverter());

  /** The column <code>forum-scrape.Sites.siteUpdated</code>. */
  public final TableField<SitesRecord, LocalDateTime> SITEUPDATED =
      createField(DSL.name("siteUpdated"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "");

  /** The column <code>forum-scrape.Sites.ForumType</code>. */
  public final TableField<SitesRecord, ForumType> FORUMTYPE =
      createField(
          DSL.name("ForumType"),
          SQLDataType.VARCHAR(12).nullable(false),
          this,
          "",
          new EnumConverter<String, ForumType>(String.class, ForumType.class));

  /** The column <code>forum-scrape.Sites.domain</code>. */
  public final TableField<SitesRecord, String> DOMAIN =
      createField(DSL.name("domain"), SQLDataType.VARCHAR(45).nullable(false), this, "");

  private Sites(Name alias, Table<SitesRecord> aliased) {
    this(alias, aliased, null);
  }

  private Sites(Name alias, Table<SitesRecord> aliased, Field<?>[] parameters) {
    super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
  }

  /** Create an aliased <code>forum-scrape.Sites</code> table reference */
  public Sites(String alias) {
    this(DSL.name(alias), SITES);
  }

  /** Create an aliased <code>forum-scrape.Sites</code> table reference */
  public Sites(Name alias) {
    this(alias, SITES);
  }

  /** Create a <code>forum-scrape.Sites</code> table reference */
  public Sites() {
    this(DSL.name("Sites"), null);
  }

  public <O extends Record> Sites(Table<O> child, ForeignKey<O, SitesRecord> key) {
    super(child, key, SITES);
  }

  @Override
  public Schema getSchema() {
    return aliased() ? null : ForumScrape.FORUM_SCRAPE;
  }

  @Override
  public UniqueKey<SitesRecord> getPrimaryKey() {
    return Keys.KEY_SITES_PRIMARY;
  }

  @Override
  public List<UniqueKey<SitesRecord>> getUniqueKeys() {
    return Arrays.asList(Keys.KEY_SITES_URL, Keys.KEY_SITES_DOMAIN_UNIQUE);
  }

  @Override
  public Sites as(String alias) {
    return new Sites(DSL.name(alias), this);
  }

  @Override
  public Sites as(Name alias) {
    return new Sites(alias, this);
  }

  /** Rename this table */
  @Override
  public Sites rename(String name) {
    return new Sites(DSL.name(name), null);
  }

  /** Rename this table */
  @Override
  public Sites rename(Name name) {
    return new Sites(name, null);
  }

  // -------------------------------------------------------------------------
  // Row5 type methods
  // -------------------------------------------------------------------------

  @Override
  public Row5<UUID, URI, LocalDateTime, ForumType, String> fieldsRow() {
    return (Row5) super.fieldsRow();
  }
}
