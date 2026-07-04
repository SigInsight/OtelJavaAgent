/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

// TODO configure renovate to update these versions
public class TestImageVersions {

  // smoke-test-spring-boot (Spring Boot 3.x image, JDK 17/21/25)
  public static final String SPRING_BOOT_VERSION = "20260703.28666040689";

  // smoke-test-spring-boot (Spring Boot 2.x image, JDK 8/11/17/21/25), built with -PspringBootMajor=2.
  // Keep "UNSET" until the SB 2.x image is published: while UNSET, SB 2.x smoke tests fail loudly
  // (see SmokeTestOptions#springBoot2) rather than pulling a non-existent tag.
  public static final String SPRING_BOOT_2_VERSION = "UNSET";

  // smoke-test-zulu-openjdk-8u31
  public static final String ZULU_OPENJDK_8U31_VERSION = "20260703.28649314617";

  // smoke-test-servlet-* (all servlet variants)
  public static final String SERVLET_VERSION = "20260703.28665068014";

  private TestImageVersions() {}
}
