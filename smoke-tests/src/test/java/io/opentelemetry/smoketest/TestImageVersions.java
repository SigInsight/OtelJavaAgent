/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

// TODO configure renovate to update these versions
public class TestImageVersions {

  // smoke-test-spring-boot
  public static final String SPRING_BOOT_VERSION = "20260703.28666040689";

  // smoke-test-zulu-openjdk-8u31
  public static final String ZULU_OPENJDK_8U31_VERSION = "20260703.28649314617";

  // smoke-test-servlet-* (all servlet variants)
  public static final String SERVLET_VERSION = "20260703.28665068014";

  private TestImageVersions() {}
}
