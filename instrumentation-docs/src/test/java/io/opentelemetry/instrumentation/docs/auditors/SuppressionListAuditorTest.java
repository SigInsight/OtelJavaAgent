/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.auditors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SuppressionListAuditorTest {

  @Test
  void testPerformAuditWithNoMissingItems() throws IOException, InterruptedException {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
        .thenReturn(mockResponse);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(createDisableListContent());

    try (MockedStatic<FileManager> fileManagerMock = mockStatic(FileManager.class)) {
      fileManagerMock
          .when(() -> FileManager.readFileToString(any()))
          .thenReturn(createInstrumentationListContent());

      SuppressionListAuditor auditor = new SuppressionListAuditor();
      Optional<String> result = auditor.performAudit(mockClient);

      assertThat(result).isEmpty();
    }
  }

  @Test
  void testPerformAuditWithMissingItems() throws IOException, InterruptedException {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
        .thenReturn(mockResponse);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(createDisableListContentMissing());

    try (MockedStatic<FileManager> fileManagerMock = mockStatic(FileManager.class)) {
      fileManagerMock
          .when(() -> FileManager.readFileToString(any()))
          .thenReturn(createInstrumentationListContent());

      SuppressionListAuditor auditor = new SuppressionListAuditor();
      Optional<String> result = auditor.performAudit(mockClient);

      assertThat(result).isPresent();
      assertThat(result.get()).contains("Missing Disable List (1 item(s) missing):");
      assertThat(result.get()).contains("- activej-http");
    }
  }

  @Test
  void testGetAuditorName() {
    SuppressionListAuditor auditor = new SuppressionListAuditor();
    assertThat(auditor.getAuditorName()).isEqualTo("Suppression List Auditor");
  }

  @Test
  void testParseDocumentationDisabledList() {
    String testFile =
"""
## Enable manual instrumentation only

You can suppress all auto instrumentations but have support for manual
instrumentation with `@WithSpan` and normal API interactions by using
`-Dotel.instrumentation.common.default-enabled=false -Dotel.instrumentation.opentelemetry-api.enabled=true -Dotel.instrumentation.opentelemetry-instrumentation-annotations.enabled=true`

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries.

{{% config_option name="otel.instrumentation.[name].enabled" %}} Set to `false`
to suppress agent instrumentation of specific libraries, where [name] is the
corresponding instrumentation name: {{% /config_option %}}

| Library/Framework                                | Instrumentation name                        |
| ------------------------------------------------ | ------------------------------------------- |
| Additional methods tracing                       | `methods`                                   |
| Additional tracing annotations                   | `external-annotations`                      |
| Apache Axis2                                     | `axis2`                                     |
""";

    var result = SuppressionListAuditor.parseDocumentationDisabledList(testFile);
    assertThat(result).hasSize(3);
    assertThat(result)
        .containsExactlyInAnyOrder(
            "methods", "external-annotations", "axis2");
  }

  @Test
  void testParseInstrumentationList() {
    String testList =
"""
libraries:
  activej:
  - name: activej-http-6.0
    description: This instrumentation enables SERVER spans and metrics for the ActiveJ
      HTTP server.
    source_path: instrumentation/activej-http-6.0
    minimum_java_version: 17
    scope:
      name: io.opentelemetry.activej-http-6.0
    target_versions:
      javaagent:
      - io.activej:activej-http:[6.0,)
""";
    var result = SuppressionListAuditor.parseInstrumentationList(testList);

    assertThat(result).hasSize(1);
    assertThat(result).containsExactly("activej-http-6.0");
  }

  @Test
  void testIdentifyMissingItems() {
    var documentationDisabledList = List.of("methods");
    var instrumentationList =
        List.of("methods", "activej-http-6.0");

    var missingItems =
        SuppressionListAuditor.identifyMissingItems(documentationDisabledList, instrumentationList);
    assertThat(missingItems).hasSize(1);
    assertThat(missingItems).containsExactly("activej-http");
  }

  @Test
  void testIdentifyMissingItemsWithHyphenatedMatch() {
    var documentationDisabledList = List.of("clickhouse");
    var instrumentationList = List.of("clickhouse-client-0.5");

    var missingItems =
        SuppressionListAuditor.identifyMissingItems(documentationDisabledList, instrumentationList);
    assertThat(missingItems).isEmpty();
  }

  private static String createDisableListContent() {
    return
"""
## Enable manual instrumentation only

You can suppress all auto instrumentations but have support for manual
instrumentation with `@WithSpan` and normal API interactions by using
`-Dotel.instrumentation.common.default-enabled=false -Dotel.instrumentation.opentelemetry-api.enabled=true -Dotel.instrumentation.opentelemetry-instrumentation-annotations.enabled=true`

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries.

{{% config_option name="otel.instrumentation.[name].enabled" %}} Set to `false`
to suppress agent instrumentation of specific libraries, where [name] is the
corresponding instrumentation name: {{% /config_option %}}

| Library/Framework                                | Instrumentation name                        |
| ------------------------------------------------ | ------------------------------------------- |
| Additional methods tracing                       | `methods`                                   |
| ActiveJ                                          | `activej-http`                              |
""";
  }

  private static String createDisableListContentMissing() {
    return
"""
## Enable manual instrumentation only

You can suppress all auto instrumentations but have support for manual
instrumentation with `@WithSpan` and normal API interactions by using
`-Dotel.instrumentation.common.default-enabled=false -Dotel.instrumentation.opentelemetry-api.enabled=true -Dotel.instrumentation.opentelemetry-instrumentation-annotations.enabled=true`

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries.

{{% config_option name="otel.instrumentation.[name].enabled" %}} Set to `false`
to suppress agent instrumentation of specific libraries, where [name] is the
corresponding instrumentation name: {{% /config_option %}}

| Library/Framework                                | Instrumentation name                        |
| ------------------------------------------------ | ------------------------------------------- |
| Additional methods tracing                       | `methods`                                   |
""";
  }

  private static String createInstrumentationListContent() {
    return
"""
libraries:
  activej:
  - name: activej-http-6.0
    description: This instrumentation enables SERVER spans and metrics for the ActiveJ
      HTTP server.
    source_path: instrumentation/activej-http-6.0
    minimum_java_version: 17
    scope:
      name: io.opentelemetry.activej-http-6.0
    target_versions:
      javaagent:
      - io.activej:activej-http:[6.0,)
""";
  }
}
