/*
 * This file is generated by jOOQ.
 */
package sh.xana.forum.server.db.tables.records;

import java.util.UUID;
import org.jooq.Field;
import org.jooq.Record5;
import org.jooq.Row5;
import org.jooq.impl.TableRecordImpl;
import sh.xana.forum.server.db.tables.Forums;

/** This class is generated by jOOQ. */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class ForumsRecord extends TableRecordImpl<ForumsRecord>
    implements Record5<UUID, UUID, UUID, String, String> {

  private static final long serialVersionUID = 1L;

  /** Setter for <code>forum-scrape.Forums.forumId</code>. */
  public void setForumid(UUID value) {
    set(0, value);
  }

  /** Getter for <code>forum-scrape.Forums.forumId</code>. */
  public UUID getForumid() {
    return (UUID) get(0);
  }

  /** Setter for <code>forum-scrape.Forums.parentForumId</code>. */
  public void setParentforumid(UUID value) {
    set(1, value);
  }

  /** Getter for <code>forum-scrape.Forums.parentForumId</code>. */
  public UUID getParentforumid() {
    return (UUID) get(1);
  }

  /** Setter for <code>forum-scrape.Forums.siteId</code>. */
  public void setSiteid(UUID value) {
    set(2, value);
  }

  /** Getter for <code>forum-scrape.Forums.siteId</code>. */
  public UUID getSiteid() {
    return (UUID) get(2);
  }

  /** Setter for <code>forum-scrape.Forums.name</code>. */
  public void setName(String value) {
    set(3, value);
  }

  /** Getter for <code>forum-scrape.Forums.name</code>. */
  public String getName() {
    return (String) get(3);
  }

  /** Setter for <code>forum-scrape.Forums.description</code>. */
  public void setDescription(String value) {
    set(4, value);
  }

  /** Getter for <code>forum-scrape.Forums.description</code>. */
  public String getDescription() {
    return (String) get(4);
  }

  // -------------------------------------------------------------------------
  // Record5 type implementation
  // -------------------------------------------------------------------------

  @Override
  public Row5<UUID, UUID, UUID, String, String> fieldsRow() {
    return (Row5) super.fieldsRow();
  }

  @Override
  public Row5<UUID, UUID, UUID, String, String> valuesRow() {
    return (Row5) super.valuesRow();
  }

  @Override
  public Field<UUID> field1() {
    return Forums.FORUMS.FORUMID;
  }

  @Override
  public Field<UUID> field2() {
    return Forums.FORUMS.PARENTFORUMID;
  }

  @Override
  public Field<UUID> field3() {
    return Forums.FORUMS.SITEID;
  }

  @Override
  public Field<String> field4() {
    return Forums.FORUMS.NAME;
  }

  @Override
  public Field<String> field5() {
    return Forums.FORUMS.DESCRIPTION;
  }

  @Override
  public UUID component1() {
    return getForumid();
  }

  @Override
  public UUID component2() {
    return getParentforumid();
  }

  @Override
  public UUID component3() {
    return getSiteid();
  }

  @Override
  public String component4() {
    return getName();
  }

  @Override
  public String component5() {
    return getDescription();
  }

  @Override
  public UUID value1() {
    return getForumid();
  }

  @Override
  public UUID value2() {
    return getParentforumid();
  }

  @Override
  public UUID value3() {
    return getSiteid();
  }

  @Override
  public String value4() {
    return getName();
  }

  @Override
  public String value5() {
    return getDescription();
  }

  @Override
  public ForumsRecord value1(UUID value) {
    setForumid(value);
    return this;
  }

  @Override
  public ForumsRecord value2(UUID value) {
    setParentforumid(value);
    return this;
  }

  @Override
  public ForumsRecord value3(UUID value) {
    setSiteid(value);
    return this;
  }

  @Override
  public ForumsRecord value4(String value) {
    setName(value);
    return this;
  }

  @Override
  public ForumsRecord value5(String value) {
    setDescription(value);
    return this;
  }

  @Override
  public ForumsRecord values(UUID value1, UUID value2, UUID value3, String value4, String value5) {
    value1(value1);
    value2(value2);
    value3(value3);
    value4(value4);
    value5(value5);
    return this;
  }

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /** Create a detached ForumsRecord */
  public ForumsRecord() {
    super(Forums.FORUMS);
  }

  /** Create a detached, initialised ForumsRecord */
  public ForumsRecord(
      UUID forumid, UUID parentforumid, UUID siteid, String name, String description) {
    super(Forums.FORUMS);

    setForumid(forumid);
    setParentforumid(parentforumid);
    setSiteid(siteid);
    setName(name);
    setDescription(description);
  }
}
