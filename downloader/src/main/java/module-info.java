module sh.xana.forum {
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires nanohttpd;
  requires org.apache.commons.io;
  requires org.apache.commons.lang3;
  requires org.jooq;
  //  requires org.jooq.codegen;
  //  requires org.jooq.meta;
  requires org.slf4j;
  requires logback.awslogs.appender;
  requires jul.to.slf4j;
  requires org.mariadb.jdbc;
  requires commons.dbcp2;
  requires org.apache.commons.pool2;
  requires java.management;

  exports sh.xana.forum.common.ipc;
}
