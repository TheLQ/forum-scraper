module sh.xana.forum {
  requires amazon.sqs.java.extended.client.lib;
  requires com.fasterxml.jackson.databind;
  requires com.github.luben.zstd_jni;
  requires com.google.common;
  requires commons.dbcp2;
  requires java.management;
  requires java.net.http;
  requires jul.to.slf4j;
  requires nanohttpd;
  requires org.apache.commons.collections4;
  requires org.apache.commons.io;
  requires org.apache.commons.lang3;
  requires org.apache.commons.pool2;
  requires org.apache.httpcomponents.httpclient;
  requires org.apache.httpcomponents.httpcore;
  requires org.jetbrains.annotations;
  requires org.jooq;
  requires org.jsoup;
  requires org.reactivestreams;
  requires org.reflections;
  requires org.slf4j;
  requires software.amazon.awssdk.core;
  requires software.amazon.awssdk.http;
  requires software.amazon.awssdk.services.s3;
  requires software.amazon.awssdk.services.sqs;

  opens sh.xana.forum.common to
      com.fasterxml.jackson.databind;
  opens sh.xana.forum.server.dbutil to
      com.fasterxml.jackson.databind;
  opens sh.xana.forum.server.spider to
      com.fasterxml.jackson.databind;
  opens sh.xana.forum.server.db.tables.records to
      org.jooq;
}
