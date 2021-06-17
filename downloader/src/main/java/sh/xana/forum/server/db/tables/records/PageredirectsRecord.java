/*
 * This file is generated by jOOQ.
 */
package sh.xana.forum.server.db.tables.records;

import java.net.URI;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.Record3;
import org.jooq.Row3;
import org.jooq.impl.TableRecordImpl;
import sh.xana.forum.server.db.tables.Pageredirects;

/** This class is generated by jOOQ. */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class PageredirectsRecord extends TableRecordImpl<PageredirectsRecord>
    implements Record3<UUID, URI, Byte> {

  private static final long serialVersionUID = 1L;

  /** Setter for <code>forum-scrape.PageRedirects.id</code>. */
  public void setId(UUID value) {
    set(0, value);
  }

  /** Getter for <code>forum-scrape.PageRedirects.id</code>. */
  public UUID getId() {
    return (UUID) get(0);
  }

  /** Setter for <code>forum-scrape.PageRedirects.url</code>. */
  public void setUrl(URI value) {
    set(1, value);
  }

  /** Getter for <code>forum-scrape.PageRedirects.url</code>. */
  public URI getUrl() {
    return (URI) get(1);
  }

  /** Setter for <code>forum-scrape.PageRedirects.index</code>. */
  public void setIndex(Byte value) {
    set(2, value);
  }

  /** Getter for <code>forum-scrape.PageRedirects.index</code>. */
  public Byte getIndex() {
    return (Byte) get(2);
  }

  // -------------------------------------------------------------------------
  // Record3 type implementation
  // -------------------------------------------------------------------------

  @Override
  public Row3<UUID, URI, Byte> fieldsRow() {
    return (Row3) super.fieldsRow();
  }

  @Override
  public Row3<UUID, URI, Byte> valuesRow() {
    return (Row3) super.valuesRow();
  }

  @Override
  public Field<UUID> field1() {
    return Pageredirects.PAGEREDIRECTS.ID;
  }

  @Override
  public Field<URI> field2() {
    return Pageredirects.PAGEREDIRECTS.URL;
  }

  @Override
  public Field<Byte> field3() {
    return Pageredirects.PAGEREDIRECTS.INDEX;
  }

  @Override
  public UUID component1() {
    return getId();
  }

  @Override
  public URI component2() {
    return getUrl();
  }

  @Override
  public Byte component3() {
    return getIndex();
  }

  @Override
  public UUID value1() {
    return getId();
  }

  @Override
  public URI value2() {
    return getUrl();
  }

  @Override
  public Byte value3() {
    return getIndex();
  }

  @Override
  public PageredirectsRecord value1(UUID value) {
    setId(value);
    return this;
  }

  @Override
  public PageredirectsRecord value2(URI value) {
    setUrl(value);
    return this;
  }

  @Override
  public PageredirectsRecord value3(Byte value) {
    setIndex(value);
    return this;
  }

  @Override
  public PageredirectsRecord values(UUID value1, URI value2, Byte value3) {
    value1(value1);
    value2(value2);
    value3(value3);
    return this;
  }

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /** Create a detached PageredirectsRecord */
  public PageredirectsRecord() {
    super(Pageredirects.PAGEREDIRECTS);
  }

  /** Create a detached, initialised PageredirectsRecord */
  public PageredirectsRecord(UUID id, URI url, Byte index) {
    super(Pageredirects.PAGEREDIRECTS);

    setId(id);
    setUrl(url);
    setIndex(index);
  }
}
