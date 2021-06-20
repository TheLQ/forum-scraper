/*
 * This file is generated by jOOQ.
 */
package sh.xana.forum.server.db.tables;

import java.net.URI;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row3;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import sh.xana.forum.server.db.ForumScrape;
import sh.xana.forum.server.db.tables.records.PageredirectsRecord;
import sh.xana.forum.server.dbutil.UriConverter;
import sh.xana.forum.server.dbutil.UuidConverter;

/** This class is generated by jOOQ. */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class Pageredirects extends TableImpl<PageredirectsRecord> {

  private static final long serialVersionUID = 1L;

  /** The reference instance of <code>forum-scrape.PageRedirects</code> */
  public static final Pageredirects PAGEREDIRECTS = new Pageredirects();

  /** The class holding records for this type */
  @Override
  public Class<PageredirectsRecord> getRecordType() {
    return PageredirectsRecord.class;
  }

  /** The column <code>forum-scrape.PageRedirects.pageId</code>. */
  public final TableField<PageredirectsRecord, UUID> PAGEID =
      createField(
          DSL.name("pageId"), SQLDataType.BLOB.nullable(false), this, "", new UuidConverter());

  /** The column <code>forum-scrape.PageRedirects.redirectUrl</code>. */
  public final TableField<PageredirectsRecord, URI> REDIRECTURL =
      createField(
          DSL.name("redirectUrl"),
          SQLDataType.VARCHAR(250).nullable(false),
          this,
          "",
          new UriConverter());

  /** The column <code>forum-scrape.PageRedirects.index</code>. */
  public final TableField<PageredirectsRecord, Byte> INDEX =
      createField(DSL.name("index"), SQLDataType.TINYINT.nullable(false), this, "");

  private Pageredirects(Name alias, Table<PageredirectsRecord> aliased) {
    this(alias, aliased, null);
  }

  private Pageredirects(Name alias, Table<PageredirectsRecord> aliased, Field<?>[] parameters) {
    super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
  }

  /** Create an aliased <code>forum-scrape.PageRedirects</code> table reference */
  public Pageredirects(String alias) {
    this(DSL.name(alias), PAGEREDIRECTS);
  }

  /** Create an aliased <code>forum-scrape.PageRedirects</code> table reference */
  public Pageredirects(Name alias) {
    this(alias, PAGEREDIRECTS);
  }

  /** Create a <code>forum-scrape.PageRedirects</code> table reference */
  public Pageredirects() {
    this(DSL.name("PageRedirects"), null);
  }

  public <O extends Record> Pageredirects(Table<O> child, ForeignKey<O, PageredirectsRecord> key) {
    super(child, key, PAGEREDIRECTS);
  }

  @Override
  public Schema getSchema() {
    return ForumScrape.FORUM_SCRAPE;
  }

  @Override
  public Pageredirects as(String alias) {
    return new Pageredirects(DSL.name(alias), this);
  }

  @Override
  public Pageredirects as(Name alias) {
    return new Pageredirects(alias, this);
  }

  /** Rename this table */
  @Override
  public Pageredirects rename(String name) {
    return new Pageredirects(DSL.name(name), null);
  }

  /** Rename this table */
  @Override
  public Pageredirects rename(Name name) {
    return new Pageredirects(name, null);
  }

  // -------------------------------------------------------------------------
  // Row3 type methods
  // -------------------------------------------------------------------------

  @Override
  public Row3<UUID, URI, Byte> fieldsRow() {
    return (Row3) super.fieldsRow();
  }
}