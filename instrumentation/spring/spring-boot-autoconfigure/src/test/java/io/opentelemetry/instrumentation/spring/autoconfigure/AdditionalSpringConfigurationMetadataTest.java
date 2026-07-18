/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class AdditionalSpringConfigurationMetadataTest {

  @Test
  void doesNotAdvertiseRemovedProviders() throws IOException {
    try (InputStream metadata =
        getClass()
            .getClassLoader()
            .getResourceAsStream("META-INF/additional-spring-configuration-metadata.json")) {
      assertThat(metadata).isNotNull();

      String contents = new String(metadata.readAllBytes(), UTF_8);
      assertThat(contents)
          .doesNotContain("otel.exporter.zipkin.endpoint")
          .doesNotContain("\"value\": \"zipkin\"")
          .doesNotContain("\"value\": \"xray\"");
    }
  }
}
