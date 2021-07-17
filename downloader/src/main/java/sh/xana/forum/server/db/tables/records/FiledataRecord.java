/*
 * This file is generated by jOOQ.
 */
package sh.xana.forum.server.db.tables.records;

import java.util.UUID;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Row2;
import org.jooq.impl.UpdatableRecordImpl;
import sh.xana.forum.server.db.tables.Filedata;

/** This class is generated by jOOQ. */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class FiledataRecord extends UpdatableRecordImpl<FiledataRecord>
    implements Record2<UUID, byte[]> {

  private static final long serialVersionUID = 1L;

  /** Setter for <code>forum-scrape.FileData.pageId</code>. */
  public void setPageid(UUID value) {
    set(0, value);
  }

  /** Getter for <code>forum-scrape.FileData.pageId</code>. */
  public UUID getPageid() {
    return (UUID) get(0);
  }

  /** Setter for <code>forum-scrape.FileData.data</code>. */
  public void setData(byte[] value) {
    set(1, value);
  }

  /** Getter for <code>forum-scrape.FileData.data</code>. */
  public byte[] getData() {
    return (byte[]) get(1);
  }

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------

  @Override
  public Record1<UUID> key() {
    return (Record1) super.key();
  }

  // -------------------------------------------------------------------------
  // Record2 type implementation
  // -------------------------------------------------------------------------

  @Override
  public Row2<UUID, byte[]> fieldsRow() {
    return (Row2) super.fieldsRow();
  }

  @Override
  public Row2<UUID, byte[]> valuesRow() {
    return (Row2) super.valuesRow();
  }

  @Override
  public Field<UUID> field1() {
    return Filedata.FILEDATA.PAGEID;
  }

  @Override
  public Field<byte[]> field2() {
    return Filedata.FILEDATA.DATA;
  }

  @Override
  public UUID component1() {
    return getPageid();
  }

  @Override
  public byte[] component2() {
    return getData();
  }

  @Override
  public UUID value1() {
    return getPageid();
  }

  @Override
  public byte[] value2() {
    return getData();
  }

  @Override
  public FiledataRecord value1(UUID value) {
    setPageid(value);
    return this;
  }

  @Override
  public FiledataRecord value2(byte[] value) {
    setData(value);
    return this;
  }

  @Override
  public FiledataRecord values(UUID value1, byte[] value2) {
    value1(value1);
    value2(value2);
    return this;
  }

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /** Create a detached FiledataRecord */
  public FiledataRecord() {
    super(Filedata.FILEDATA);
  }

  /** Create a detached, initialised FiledataRecord */
  public FiledataRecord(UUID pageid, byte[] data) {
    super(Filedata.FILEDATA);

    setPageid(pageid);
    setData(data);
  }
}
