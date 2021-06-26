module sh.xana.forum {
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires nanohttpd;
  requires org.apache.commons.io;
  requires org.apache.commons.lang3;
  requires org.jooq;
  requires org.slf4j;
  requires jul.to.slf4j;
  requires org.mariadb.jdbc;
  requires commons.dbcp2;
  requires org.apache.commons.pool2;
  requires java.management;
  requires java.sql;
  requires java.sql.rowset;
  requires org.jsoup;
  requires checker.framework;

  opens sh.xana.forum.common.ipc to
      com.fasterxml.jackson.databind;
  opens sh.xana.forum.server.db.tables.records to
      org.jooq;
  opens sh.xana.forum.server.dbutil to
      com.fasterxml.jackson.databind;
}
