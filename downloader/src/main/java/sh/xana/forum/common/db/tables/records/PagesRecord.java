/*
 * This file is generated by jOOQ.
 */
package sh.xana.forum.common.db.tables.records;

import java.time.LocalDateTime;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record10;
import org.jooq.Row10;
import org.jooq.impl.UpdatableRecordImpl;
import sh.xana.forum.common.db.tables.Pages;

/** This class is generated by jOOQ. */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class PagesRecord extends UpdatableRecordImpl<PagesRecord>
    implements Record10<
        byte[], byte[], byte[], String, String, String, LocalDateTime, String, Integer, String> {

  private static final long serialVersionUID = 1L;

  /** Setter for <code>Pages.id</code>. */
  public void setId(byte[] value) {
    set(0, value);
  }

  /** Getter for <code>Pages.id</code>. */
  public byte[] getId() {
    return (byte[]) get(0);
  }

  /** Setter for <code>Pages.sourceId</code>. */
  public void setSourceid(byte[] value) {
    set(1, value);
  }

  /** Getter for <code>Pages.sourceId</code>. */
  public byte[] getSourceid() {
    return (byte[]) get(1);
  }

  /** Setter for <code>Pages.siteid</code>. */
  public void setSiteid(byte[] value) {
    set(2, value);
  }

  /** Getter for <code>Pages.siteid</code>. */
  public byte[] getSiteid() {
    return (byte[]) get(2);
  }

  /** Setter for <code>Pages.url</code>. */
  public void setUrl(String value) {
    set(3, value);
  }

  /** Getter for <code>Pages.url</code>. */
  public String getUrl() {
    return (String) get(3);
  }

  /** Setter for <code>Pages.pageType</code>. */
  public void setPagetype(String value) {
    set(4, value);
  }

  /** Getter for <code>Pages.pageType</code>. */
  public String getPagetype() {
    return (String) get(4);
  }

  /** Setter for <code>Pages.dlstatus</code>. */
  public void setDlstatus(String value) {
    set(5, value);
  }

  /** Getter for <code>Pages.dlstatus</code>. */
  public String getDlstatus() {
    return (String) get(5);
  }

  /** Setter for <code>Pages.updated</code>. */
  public void setUpdated(LocalDateTime value) {
    set(6, value);
  }

  /** Getter for <code>Pages.updated</code>. */
  public LocalDateTime getUpdated() {
    return (LocalDateTime) get(6);
  }

  /** Setter for <code>Pages.domain</code>. */
  public void setDomain(String value) {
    set(7, value);
  }

  /** Getter for <code>Pages.domain</code>. */
  public String getDomain() {
    return (String) get(7);
  }

  /** Setter for <code>Pages.dlStatusCode</code>. */
  public void setDlstatuscode(Integer value) {
    set(8, value);
  }

  /** Getter for <code>Pages.dlStatusCode</code>. */
  public Integer getDlstatuscode() {
    return (Integer) get(8);
  }

  /** Setter for <code>Pages.exception</code>. */
  public void setException(String value) {
    set(9, value);
  }

  /** Getter for <code>Pages.exception</code>. */
  public String getException() {
    return (String) get(9);
  }

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------

  @Override
  public Record1<byte[]> key() {
    return (Record1) super.key();
  }

  // -------------------------------------------------------------------------
  // Record10 type implementation
  // -------------------------------------------------------------------------

  @Override
  public Row10<
          byte[], byte[], byte[], String, String, String, LocalDateTime, String, Integer, String>
      fieldsRow() {
    return (Row10) super.fieldsRow();
  }

  @Override
  public Row10<
          byte[], byte[], byte[], String, String, String, LocalDateTime, String, Integer, String>
      valuesRow() {
    return (Row10) super.valuesRow();
  }

  @Override
  public Field<byte[]> field1() {
    return Pages.PAGES.ID;
  }

  @Override
  public Field<byte[]> field2() {
    return Pages.PAGES.SOURCEID;
  }

  @Override
  public Field<byte[]> field3() {
    return Pages.PAGES.SITEID;
  }

  @Override
  public Field<String> field4() {
    return Pages.PAGES.URL;
  }

  @Override
  public Field<String> field5() {
    return Pages.PAGES.PAGETYPE;
  }

  @Override
  public Field<String> field6() {
    return Pages.PAGES.DLSTATUS;
  }

  @Override
  public Field<LocalDateTime> field7() {
    return Pages.PAGES.UPDATED;
  }

  @Override
  public Field<String> field8() {
    return Pages.PAGES.DOMAIN;
  }

  @Override
  public Field<Integer> field9() {
    return Pages.PAGES.DLSTATUSCODE;
  }

  @Override
  public Field<String> field10() {
    return Pages.PAGES.EXCEPTION;
  }

  @Override
  public byte[] component1() {
    return getId();
  }

  @Override
  public byte[] component2() {
    return getSourceid();
  }

  @Override
  public byte[] component3() {
    return getSiteid();
  }

  @Override
  public String component4() {
    return getUrl();
  }

  @Override
  public String component5() {
    return getPagetype();
  }

  @Override
  public String component6() {
    return getDlstatus();
  }

  @Override
  public LocalDateTime component7() {
    return getUpdated();
  }

  @Override
  public String component8() {
    return getDomain();
  }

  @Override
  public Integer component9() {
    return getDlstatuscode();
  }

  @Override
  public String component10() {
    return getException();
  }

  @Override
  public byte[] value1() {
    return getId();
  }

  @Override
  public byte[] value2() {
    return getSourceid();
  }

  @Override
  public byte[] value3() {
    return getSiteid();
  }

  @Override
  public String value4() {
    return getUrl();
  }

  @Override
  public String value5() {
    return getPagetype();
  }

  @Override
  public String value6() {
    return getDlstatus();
  }

  @Override
  public LocalDateTime value7() {
    return getUpdated();
  }

  @Override
  public String value8() {
    return getDomain();
  }

  @Override
  public Integer value9() {
    return getDlstatuscode();
  }

  @Override
  public String value10() {
    return getException();
  }

  @Override
  public PagesRecord value1(byte[] value) {
    setId(value);
    return this;
  }

  @Override
  public PagesRecord value2(byte[] value) {
    setSourceid(value);
    return this;
  }

  @Override
  public PagesRecord value3(byte[] value) {
    setSiteid(value);
    return this;
  }

  @Override
  public PagesRecord value4(String value) {
    setUrl(value);
    return this;
  }

  @Override
  public PagesRecord value5(String value) {
    setPagetype(value);
    return this;
  }

  @Override
  public PagesRecord value6(String value) {
    setDlstatus(value);
    return this;
  }

  @Override
  public PagesRecord value7(LocalDateTime value) {
    setUpdated(value);
    return this;
  }

  @Override
  public PagesRecord value8(String value) {
    setDomain(value);
    return this;
  }

  @Override
  public PagesRecord value9(Integer value) {
    setDlstatuscode(value);
    return this;
  }

  @Override
  public PagesRecord value10(String value) {
    setException(value);
    return this;
  }

  @Override
  public PagesRecord values(
      byte[] value1,
      byte[] value2,
      byte[] value3,
      String value4,
      String value5,
      String value6,
      LocalDateTime value7,
      String value8,
      Integer value9,
      String value10) {
    value1(value1);
    value2(value2);
    value3(value3);
    value4(value4);
    value5(value5);
    value6(value6);
    value7(value7);
    value8(value8);
    value9(value9);
    value10(value10);
    return this;
  }

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /** Create a detached PagesRecord */
  public PagesRecord() {
    super(Pages.PAGES);
  }

  /** Create a detached, initialised PagesRecord */
  public PagesRecord(
      byte[] id,
      byte[] sourceid,
      byte[] siteid,
      String url,
      String pagetype,
      String dlstatus,
      LocalDateTime updated,
      String domain,
      Integer dlstatuscode,
      String exception) {
    super(Pages.PAGES);

    setId(id);
    setSourceid(sourceid);
    setSiteid(siteid);
    setUrl(url);
    setPagetype(pagetype);
    setDlstatus(dlstatus);
    setUpdated(updated);
    setDomain(domain);
    setDlstatuscode(dlstatuscode);
    setException(exception);
  }
}
