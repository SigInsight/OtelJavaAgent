#!/usr/bin/env bash

set -euo pipefail

# This file is sourced by collect.sh and ci-collect.sh

readonly INSTRUMENTATIONS=(
  # <module path (colon-separated)> : <javaagent|library> : [ gradle-task-suffix ]
  "apache-httpasyncclient-4.1:javaagent:test"
  "apache-httpasyncclient-4.1:javaagent:testStableSemconv"
  "apache-httpclient:apache-httpclient-4.0:javaagent:test"
  "apache-httpclient:apache-httpclient-4.0:javaagent:testStableSemconv"
  "apache-httpclient:apache-httpclient-4.3:library:test"
  "apache-httpclient:apache-httpclient-5.0:javaagent:test"
  "apache-httpclient:apache-httpclient-5.0:javaagent:testStableSemconv"
  "apache-httpclient:apache-httpclient-5.2:library:test"
  "armeria:armeria-1.3:javaagent:test"
  "armeria:armeria-1.3:javaagent:testStableSemconv"
  "async-http-client:async-http-client-2.0:javaagent:test"
  "async-http-client:async-http-client-2.0:javaagent:testStableSemconv"
  "clickhouse:clickhouse-client-v1-0.5:javaagent:test"
  "clickhouse:clickhouse-client-v1-0.5:javaagent:testStableSemconv"
  "clickhouse:clickhouse-client-v2-0.8:javaagent:test"
  "clickhouse:clickhouse-client-v2-0.8:javaagent:testStableSemconv"
  "hibernate:hibernate-4.0:javaagent:test"
  "hibernate:hibernate-4.0:javaagent:testExperimental"
  "hibernate:hibernate-6.0:javaagent:test"
  "hibernate:hibernate-6.0:javaagent:testExperimental"
  "hibernate:hibernate-procedure-call-4.3:javaagent:test"
  "hibernate:hibernate-procedure-call-4.3:javaagent:testExperimental"
  "hikaricp-3.0:javaagent:test"
  "hikaricp-3.0:javaagent:testStableSemconv"
  "http-url-connection:javaagent:test"
  "java-http-client:javaagent:test"
  "java-http-server:javaagent:test"
  "jdbc:javaagent:test"
  "jdbc:javaagent:testStableSemconv"
  "jedis:jedis-3.0:javaagent:test"
  "jedis:jedis-3.0:javaagent:testStableSemconv"
  "jedis:jedis-4.0:javaagent:test"
  "jedis:jedis-4.0:javaagent:testStableSemconv"
  "ktor:ktor-1.0:library:test"
  "lettuce:lettuce-5.0:javaagent:test"
  "lettuce:lettuce-5.0:javaagent:testExperimental"
  "lettuce:lettuce-5.0:javaagent:testStableSemconv"
  "lettuce:lettuce-5.1:javaagent:test"
  "mongo:mongo-3.7:javaagent:test"
  "mongo:mongo-3.7:javaagent:testStableSemconv"
  "mongo:mongo-4.0:javaagent:test"
  "mongo:mongo-4.0:javaagent:testStableSemconv"
  "mongo:mongo-async-3.3:javaagent:test"
  "mongo:mongo-async-3.3:javaagent:testStableSemconv"
  "mybatis-3.2:javaagent:test"
  "netty:netty-4.0:javaagent:test"
  "netty:netty-4.1:javaagent:test"
  "okhttp:okhttp-3.0:javaagent:test"
  "okhttp:okhttp-3.0:javaagent:testStableSemconv"
  "oshi-5.0:javaagent:test"
  "oshi-5.0:javaagent:testExperimental"
  "r2dbc-1.0:javaagent:test"
  "r2dbc-1.0:javaagent:testStableSemconv"
  "reactor:reactor-netty:reactor-netty-0.9:javaagent:test"
  "reactor:reactor-netty:reactor-netty-1.0:javaagent:test"
  "redisson:redisson-3.17:javaagent:test"
  "redisson:redisson-3.17:javaagent:testStableSemconv"
  "runtime-telemetry:library:check"
  "spring:spring-web:spring-web-3.1:library:test"
  "spring:spring-web:spring-web-3.1:library:testStableSemconv"
  "spring:spring-web:spring-web-6.0:javaagent:test"
  "spring:spring-web:spring-web-6.0:javaagent:testStableSemconv"
  "spring:spring-web:spring-web-6.0:javaagent:testExperimental"
  "spring:spring-webflux:spring-webflux-5.0:javaagent:test"
  "spring:spring-webflux:spring-webflux-5.0:javaagent:testStableSemconv"
  "spring:spring-webflux:spring-webflux-5.3:library:test"
  "spring:spring-webflux:spring-webflux-5.3:library:testStableSemconv"
  "spring:spring-webmvc:spring-webmvc-3.1:javaagent:test"
  "spring:spring-webmvc:spring-webmvc-3.1:javaagent:testExperimental"
  "spring:spring-webmvc:spring-webmvc-5.3:library:test"
  "spring:spring-webmvc:spring-webmvc-6.0:javaagent:test"
  "spring:spring-webmvc:spring-webmvc-6.0:javaagent:testExperimental"
  "tomcat:tomcat-10.0:javaagent:test"
)

#  Some instrumentation test suites don't run ARM, so we use colima to run them in an x86_64
#  container.
readonly COLIMA_INSTRUMENTATIONS=(
)

# Some instrumentation test suites need to run with -PtestLatestDeps=true to collect
# metrics telemetry or test against latest library versions.
readonly TEST_LATEST_DEPS_INSTRUMENTATIONS=(
  "lettuce:lettuce-5.1:javaagent:test"
  "lettuce:lettuce-5.1:javaagent:testStableSemconv"
)
