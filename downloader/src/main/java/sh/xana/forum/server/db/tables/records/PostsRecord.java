/*
 * This file is generated by jOOQ.
 */
package sh.xana.forum.server.db.tables.records;

import java.util.UUID;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.Row3;
import org.jooq.impl.UpdatableRecordImpl;
import sh.xana.forum.server.db.tables.Posts;

/** This class is generated by jOOQ. */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class PostsRecord extends UpdatableRecordImpl<PostsRecord>
    implements Record3<UUID, String, Integer> {

  private static final long serialVersionUID = 1L;

  /** Setter for <code>forum-scrape.Posts.postId</code>. */
  public void setPostid(UUID value) {
    set(0, value);
  }

  /** Getter for <code>forum-scrape.Posts.postId</code>. */
  public UUID getPostid() {
    return (UUID) get(0);
  }

  /** Setter for <code>forum-scrape.Posts.body</code>. */
  public void setBody(String value) {
    set(1, value);
  }

  /** Getter for <code>forum-scrape.Posts.body</code>. */
  public String getBody() {
    return (String) get(1);
  }

  /** Setter for <code>forum-scrape.Posts.origId</code>. */
  public void setOrigid(Integer value) {
    set(2, value);
  }

  /** Getter for <code>forum-scrape.Posts.origId</code>. */
  public Integer getOrigid() {
    return (Integer) get(2);
  }

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------

  @Override
  public Record1<UUID> key() {
    return (Record1) super.key();
  }

  // -------------------------------------------------------------------------
  // Record3 type implementation
  // -------------------------------------------------------------------------

  @Override
  public Row3<UUID, String, Integer> fieldsRow() {
    return (Row3) super.fieldsRow();
  }

  @Override
  public Row3<UUID, String, Integer> valuesRow() {
    return (Row3) super.valuesRow();
  }

  @Override
  public Field<UUID> field1() {
    return Posts.POSTS.POSTID;
  }

  @Override
  public Field<String> field2() {
    return Posts.POSTS.BODY;
  }

  @Override
  public Field<Integer> field3() {
    return Posts.POSTS.ORIGID;
  }

  @Override
  public UUID component1() {
    return getPostid();
  }

  @Override
  public String component2() {
    return getBody();
  }

  @Override
  public Integer component3() {
    return getOrigid();
  }

  @Override
  public UUID value1() {
    return getPostid();
  }

  @Override
  public String value2() {
    return getBody();
  }

  @Override
  public Integer value3() {
    return getOrigid();
  }

  @Override
  public PostsRecord value1(UUID value) {
    setPostid(value);
    return this;
  }

  @Override
  public PostsRecord value2(String value) {
    setBody(value);
    return this;
  }

  @Override
  public PostsRecord value3(Integer value) {
    setOrigid(value);
    return this;
  }

  @Override
  public PostsRecord values(UUID value1, String value2, Integer value3) {
    value1(value1);
    value2(value2);
    value3(value3);
    return this;
  }

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /** Create a detached PostsRecord */
  public PostsRecord() {
    super(Posts.POSTS);
  }

  /** Create a detached, initialised PostsRecord */
  public PostsRecord(UUID postid, String body, Integer origid) {
    super(Posts.POSTS);

    setPostid(postid);
    setBody(body);
    setOrigid(origid);
  }
}
