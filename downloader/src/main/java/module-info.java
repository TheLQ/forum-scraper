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
  //  requires ch.qos.logback.core;
  requires commons.cli;
  requires logback.awslogs.appender;

  exports sh.xana.forum.common.ipc;
}
