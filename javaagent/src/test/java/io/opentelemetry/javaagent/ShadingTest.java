/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class ShadingTest {

  private static final List<String> EXPECTED_ENTRY_PREFIXES =
      asList("io/opentelemetry/javaagent/", "inst/", "META-INF/");

  @Test
  void agentJarContainsOnlyExpectedEntries() throws Exception {
    String agentJarPath = getAgentJarPath();
    assertThat(agentJarPath).isNotNull();

    File agentJar = new File(agentJarPath);
    assertThat(agentJar).exists();
    assertThat(agentJar).isFile();

    List<String> unexpectedEntries = new ArrayList<>();

    try (JarFile jarFile = new JarFile(agentJar)) {
      jarFile.stream()
          .map(JarEntry::getName)
          .filter(entryName -> !entryName.endsWith("/")) // Skip directories
          .forEach(
              entryName -> {
                boolean isExpected =
                    EXPECTED_ENTRY_PREFIXES.stream().anyMatch(entryName::startsWith);
                if (!isExpected) {
                  unexpectedEntries.add(entryName);
                }
              });
    }

    assertThat(unexpectedEntries).isEmpty();
  }

  @Test
  void agentJarContainsExpectedLifecycleProviders() throws Exception {
    File agentJar = new File(getAgentJarPath());

    try (JarFile jarFile = new JarFile(agentJar)) {
      assertThat(
              readServiceProviders(
                  jarFile, "io.opentelemetry.javaagent.tooling.BeforeAgentListener"))
          .containsExactlyInAnyOrder(
              "io.opentelemetry.javaagent.instrumentation.runtimetelemetry.JarAnalyzerInstaller",
              "io.opentelemetry.javaagent.tooling.instrumentation.http.RegexUrlTemplateCustomizerInitializer");
      assertThat(
              readServiceProviders(jarFile, "io.opentelemetry.javaagent.extension.AgentListener"))
          .containsExactlyInAnyOrder(
              "io.opentelemetry.javaagent.instrumentation.jmx.JmxMetricInsightInstaller",
              "io.opentelemetry.javaagent.instrumentation.oshi.v5_0.OshiMetricsInstaller",
              "io.opentelemetry.javaagent.instrumentation.runtimetelemetry.RuntimeTelemetryInstaller");
    }
  }

  private static List<String> readServiceProviders(JarFile jarFile, String serviceName)
      throws Exception {
    JarEntry serviceEntry = jarFile.getJarEntry("inst/META-INF/services/" + serviceName);
    assertThat(serviceEntry).isNotNull();

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(jarFile.getInputStream(serviceEntry), UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .collect(toList());
    }
  }

  private static String getAgentJarPath() {
    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    for (String arg : runtimeMxBean.getInputArguments()) {
      if (arg.startsWith("-javaagent:")) {
        return arg.substring("-javaagent:".length());
      }
    }
    return null;
  }
}
