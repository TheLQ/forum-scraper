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
import org.jooq.Row10;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import sh.xana.forum.server.db.DefaultSchema;
import sh.xana.forum.server.db.Keys;
import sh.xana.forum.server.db.tables.records.PagesRecord;
import sh.xana.forum.server.dbutil.DatabaseStorage.DlStatus;
import sh.xana.forum.server.dbutil.DatabaseStorage.PageType;
import sh.xana.forum.server.dbutil.JooqGenerator.DlStatusConverter;
import sh.xana.forum.server.dbutil.JooqGenerator.PageTypeConverter;
import sh.xana.forum.server.dbutil.JooqGenerator.UriConverter;

/** This class is generated by jOOQ. */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class Pages extends TableImpl<PagesRecord> {

  private static final long serialVersionUID = 1L;

  /** The reference instance of <code>Pages</code> */
  public static final Pages PAGES = new Pages();

  /** The class holding records for this type */
  @Override
  public Class<PagesRecord> getRecordType() {
    return PagesRecord.class;
  }

  /** The column <code>Pages.id</code>. */
  public final TableField<PagesRecord, UUID> ID =
      createField(DSL.name("id"), SQLDataType.UUID.nullable(false), this, "");

  /** The column <code>Pages.sourceId</code>. */
  public final TableField<PagesRecord, UUID> SOURCEID =
      createField(DSL.name("sourceId"), SQLDataType.UUID, this, "");

  /** The column <code>Pages.siteid</code>. */
  public final TableField<PagesRecord, UUID> SITEID =
      createField(DSL.name("siteid"), SQLDataType.UUID.nullable(false), this, "");

  /** The column <code>Pages.url</code>. */
  public final TableField<PagesRecord, URI> URL =
      createField(DSL.name("url"), SQLDataType.CLOB.nullable(false), this, "", new UriConverter());

  /** The column <code>Pages.pageType</code>. */
  public final TableField<PagesRecord, PageType> PAGETYPE =
      createField(
          DSL.name("pageType"),
          SQLDataType.VARCHAR(10).nullable(false),
          this,
          "",
          new PageTypeConverter());

  /** The column <code>Pages.dlstatus</code>. */
  public final TableField<PagesRecord, DlStatus> DLSTATUS =
      createField(
          DSL.name("dlstatus"),
          SQLDataType.VARCHAR(10).nullable(false),
          this,
          "",
          new DlStatusConverter());

  /** The column <code>Pages.updated</code>. */
  public final TableField<PagesRecord, LocalDateTime> UPDATED =
      createField(DSL.name("updated"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "");

  /** The column <code>Pages.domain</code>. */
  public final TableField<PagesRecord, String> DOMAIN =
      createField(DSL.name("domain"), SQLDataType.VARCHAR(50).nullable(false), this, "");

  /** The column <code>Pages.dlStatusCode</code>. */
  public final TableField<PagesRecord, Integer> DLSTATUSCODE =
      createField(DSL.name("dlStatusCode"), SQLDataType.INTEGER, this, "");

  /** The column <code>Pages.exception</code>. */
  public final TableField<PagesRecord, String> EXCEPTION =
      createField(DSL.name("exception"), SQLDataType.CLOB, this, "");

  private Pages(Name alias, Table<PagesRecord> aliased) {
    this(alias, aliased, null);
  }

  private Pages(Name alias, Table<PagesRecord> aliased, Field<?>[] parameters) {
    super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
  }

  /** Create an aliased <code>Pages</code> table reference */
  public Pages(String alias) {
    this(DSL.name(alias), PAGES);
  }

  /** Create an aliased <code>Pages</code> table reference */
  public Pages(Name alias) {
    this(alias, PAGES);
  }

  /** Create a <code>Pages</code> table reference */
  public Pages() {
    this(DSL.name("Pages"), null);
  }

  public <O extends Record> Pages(Table<O> child, ForeignKey<O, PagesRecord> key) {
    super(child, key, PAGES);
  }

  @Override
  public Schema getSchema() {
    return DefaultSchema.DEFAULT_SCHEMA;
  }

  @Override
  public UniqueKey<PagesRecord> getPrimaryKey() {
    return Keys.PK_PAGES;
  }

  @Override
  public List<UniqueKey<PagesRecord>> getKeys() {
    return Arrays.<UniqueKey<PagesRecord>>asList(Keys.PK_PAGES, Keys.SQLITE_AUTOINDEX_PAGES_2);
  }

  @Override
  public Pages as(String alias) {
    return new Pages(DSL.name(alias), this);
  }

  @Override
  public Pages as(Name alias) {
    return new Pages(alias, this);
  }

  /** Rename this table */
  @Override
  public Pages rename(String name) {
    return new Pages(DSL.name(name), null);
  }

  /** Rename this table */
  @Override
  public Pages rename(Name name) {
    return new Pages(name, null);
  }

  // -------------------------------------------------------------------------
  // Row10 type methods
  // -------------------------------------------------------------------------

  @Override
  public Row10<UUID, UUID, UUID, URI, PageType, DlStatus, LocalDateTime, String, Integer, String>
      fieldsRow() {
    return (Row10) super.fieldsRow();
  }
}
