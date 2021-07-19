/*
 * This file is generated by jOOQ.
 */
package sh.xana.forum.server.db;

import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;
import sh.xana.forum.server.db.tables.Pageredirects;

/** A class modelling indexes of tables in forum-scrape. */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class Indexes {

  // -------------------------------------------------------------------------
  // INDEX definitions
  // -------------------------------------------------------------------------

  public static final Index PAGEREDIRECTS_REDIRECTURL =
      Internal.createIndex(
          DSL.name("redirectUrl"),
          Pageredirects.PAGEREDIRECTS,
          new OrderField[] {Pageredirects.PAGEREDIRECTS.REDIRECTURL},
          false);
}