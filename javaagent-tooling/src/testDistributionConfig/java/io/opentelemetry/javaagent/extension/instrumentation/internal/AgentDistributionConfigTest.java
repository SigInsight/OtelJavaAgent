/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.tooling.OpenTelemetryInstaller;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AgentDistributionConfigTest {

  @BeforeAll
  static void setUp() {
    // Initialize OpenTelemetry SDK to load declarative configuration
    OpenTelemetryInstaller.installOpenTelemetrySdk(
        AgentDistributionConfigTest.class.getClassLoader());
  }

  @Test
  void testIndyDevelopmentProperty() {
    assertThat(AgentDistributionConfig.get().isIndyEnabled()).isTrue();
  }

  @Test
  void testForceSynchronousAgentListeners() {
    assertThat(AgentDistributionConfig.get().isForceSynchronousAgentListeners()).isFalse();
  }

  @Test
  void testExcludeClasses() {
    assertThat(AgentDistributionConfig.get().getExcludeClasses())
        .containsExactly("com.example.excluded.Class1", "com.example.excluded.Class2");
  }

  @Test
  void testExcludeClassLoaders() {
    assertThat(AgentDistributionConfig.get().getExcludeClassLoaders())
        .containsExactly("com.example.ExcludedClassLoader");
  }

  @Test
  void testInstrumentationDefaultEnabledByDefault() {
    assertThat(AgentDistributionConfig.get().isInstrumentationDefaultEnabled()).isTrue();
  }

  @Test
  void testV3PreviewProperty() {
    assertThat(AgentCommonConfig.get().isV3Preview()).isTrue();
  }

  @Test
  void testInstrumentationEnabled() {
    AgentDistributionConfig config = AgentDistributionConfig.get();
    assertThat(config.isInstrumentationEnabled("tomcat", false)).isTrue();
    assertThat(config.isInstrumentationEnabled("spring_webmvc", false)).isTrue();
    assertThat(config.isInstrumentationEnabled("unknown", false)).isFalse();
  }

  @Test
  void testUnknownInstrumentationUsesDefaultEnabled() {
    // An unknown instrumentation with defaultEnabled=true should be enabled
    AgentDistributionConfig config = AgentDistributionConfig.get();
    assertThat(config.isInstrumentationEnabled("unknown", true)).isTrue();
  }

  @Test
  void testInstrumentationEnabledOrderMatters() {
    // spring-webflux is enabled, spring-web is disabled
    // first matching name wins (matches ConfigProperties behavior)
    AgentDistributionConfig config = AgentDistributionConfig.get();

    // spring-web listed first: disabled wins
    assertThat(
            config.isInstrumentationEnabled(
                asList("spring-web", "spring-web-6.0", "spring-webflux", "spring-webflux-5.0"),
                true))
        .isFalse();

    // spring-webflux listed first: enabled wins
    assertThat(
            config.isInstrumentationEnabled(
                asList("spring-webflux", "spring-webflux-5.0", "spring-web", "spring-web-6.0"),
                true))
        .isTrue();

    // spring-webflux alone should be enabled
    assertThat(
            config.isInstrumentationEnabled(asList("spring-webflux", "spring-webflux-5.0"), true))
        .isTrue();
  }
}
