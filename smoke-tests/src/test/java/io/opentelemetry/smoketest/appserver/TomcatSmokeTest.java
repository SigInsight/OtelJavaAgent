/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.ImageVersions;
import io.opentelemetry.smoketest.SmokeTestOptions;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;

abstract class TomcatSmokeTest extends AppServerTest {

  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    options
        .image(
            appServerImage(
                "ghcr.io/" + ImageVersions.GHCR_REPOSITORY + "/smoke-test-servlet-tomcat"))
        .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Server startup in.*"));
  }

  @AppServer(version = "10.1.48", jdk = "11")
  static class Tomcat10Jdk11 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.48", jdk = "11-openj9")
  static class Tomcat10Jdk11Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.48", jdk = "17")
  static class Tomcat10Jdk17 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.48", jdk = "17-openj9")
  static class Tomcat10Jdk17Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.48", jdk = "21")
  static class Tomcat10Jdk21 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.48", jdk = "21-openj9")
  static class Tomcat10Jdk21Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.48", jdk = "25")
  static class Tomcat10Jdk25 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.48", jdk = "25-openj9")
  static class Tomcat10Jdk25Openj9 extends TomcatSmokeTest {}
}
