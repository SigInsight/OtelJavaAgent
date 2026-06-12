/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  api("com.google.guava:guava")

  compileOnly("com.h2database:h2:1.3.169")
  compileOnly("org.apache.derby:derby:10.6.1.0")
  compileOnly("org.hsqldb:hsqldb:2.0.0")
}
