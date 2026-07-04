/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

// TODO configure renovate to update these versions
public class TestImageVersions {

  // smoke-test-spring-boot (Spring Boot 3.x image, JDK 17/21/25)
  public static final String SPRING_BOOT_VERSION = "20260704.28692840723";

  // smoke-test-spring-boot (Spring Boot 2.x image, JDK 8/11/17/21/25), built with -PspringBootMajor=2.
  // Shares the publish-run tag with SPRING_BOOT_VERSION (both image families build in one run).
  public static final String SPRING_BOOT_2_VERSION = "20260704.28692840723";

  // smoke-test-zulu-openjdk-8u31
  public static final String ZULU_OPENJDK_8U31_VERSION = "20260703.28649314617";

  // smoke-test-servlet-* (all servlet variants)
  public static final String SERVLET_VERSION = "20260703.28665068014";

  private TestImageVersions() {}
}
