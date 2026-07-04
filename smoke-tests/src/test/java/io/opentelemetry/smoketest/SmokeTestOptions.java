/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Assertions;

public class SmokeTestOptions<T> {

  Function<T, String> getImage;
  String[] command;
  String jvmArgsEnvVarName = "JAVA_TOOL_OPTIONS";
  boolean setServiceName = true;
  final Map<String, String> extraEnv = new HashMap<>();
  List<ResourceMapping> extraResources = List.of();
  TargetWaitStrategy waitStrategy;
  List<Integer> extraPorts = List.of();
  Duration telemetryTimeout = Duration.ofSeconds(30);

  /** Sets the container image to run. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> image(Function<T, String> getImage) {
    this.getImage = getImage;
    return this;
  }

  /** Configure test for the Spring Boot 3.x test app (JDK 17/21/25). */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> springBoot() {
    image(
        jdk ->
            String.format(
                "ghcr.io/%s/smoke-test-spring-boot:jdk%s-%s",
                ImageVersions.GHCR_REPOSITORY, jdk, TestImageVersions.SPRING_BOOT_VERSION));
    waitStrategy(
        new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Started SpringbootApplication in.*"));
    return this;
  }

  /**
   * Configure test for the Spring Boot 2.x test app, used for JDK 8/11/17/21/25 compatibility smoke
   * tests. Fails loudly (red) when {@link TestImageVersions#SPRING_BOOT_2_VERSION} is still "UNSET",
   * so an unbuilt SB 2.x image surfaces immediately instead of leaving CI green with skipped tests.
   * A typo'd tag (not "UNSET") is caught separately by the short testcontainers pull timeout
   * (see smoke-tests/src/test/resources/testcontainers.properties).
   */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> springBoot2() {
    if ("UNSET".equals(TestImageVersions.SPRING_BOOT_2_VERSION)) {
      Assertions.fail(
          "Spring Boot 2.x smoke image is not configured: TestImageVersions.SPRING_BOOT_2_VERSION"
              + " is still \"UNSET\". Trigger the Spring Boot image publish workflow"
              + " (.github/workflows/publish-smoke-test-spring-boot-images.yml) and set"
              + " SPRING_BOOT_2_VERSION to the published tag.");
    }
    image(
        jdk ->
            String.format(
                "ghcr.io/%s/smoke-test-spring-boot:sb2-jdk%s-%s",
                ImageVersions.GHCR_REPOSITORY, jdk, TestImageVersions.SPRING_BOOT_2_VERSION));
    waitStrategy(
        new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Started SpringbootApplication in.*"));
    return this;
  }

  /** Sets the command to run in the target container. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> command(String... command) {
    this.command = command;
    return this;
  }

  /** Sets the environment variable name used to pass JVM arguments to the target application. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> jvmArgsEnvVarName(String jvmArgsEnvVarName) {
    this.jvmArgsEnvVarName = jvmArgsEnvVarName;
    return this;
  }

  /** Enables or disables setting the default service name for the target application. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> setServiceName(boolean setServiceName) {
    this.setServiceName = setServiceName;
    return this;
  }

  /** Adds an environment variable to the target application's environment. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> env(String key, String value) {
    this.extraEnv.put(key, value);
    return this;
  }

  /** Specifies additional files to copy to the target container. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> extraResources(ResourceMapping... resources) {
    this.extraResources = List.of(resources);
    return this;
  }

  /** Sets the wait strategy for the target container startup. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> waitStrategy(@Nullable TargetWaitStrategy waitStrategy) {
    this.waitStrategy = waitStrategy;
    return this;
  }

  /** Specifies additional ports to expose from the target container. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> extraPorts(Integer... ports) {
    this.extraPorts = List.of(ports);
    return this;
  }

  /** Sets the timeout duration for retrieving telemetry data. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> telemetryTimeout(Duration telemetryTimeout) {
    this.telemetryTimeout = telemetryTimeout;
    return this;
  }
}
