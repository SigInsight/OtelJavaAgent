/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.util.Locale;

// TODO configure renovate to update these versions
public class ImageVersions {

  // ghcr.io repository path for the smoke-test images. Defaults to this fork's org so local
  // `:smoke-tests:test` pulls the fork's public images; $GITHUB_REPOSITORY (lowercased) overrides
  // it in CI (same value for this fork). Mirrors the `repo` value in images/*/build.gradle.kts.
  public static final String GHCR_REPOSITORY =
      System.getenv("GITHUB_REPOSITORY") == null
          ? "siginsight/oteljavaagent"
          : System.getenv("GITHUB_REPOSITORY").toLowerCase(Locale.ROOT);

  // smoke-test-fake-backend
  public static final String FAKE_BACKEND_VERSION = "20260703.28649343291";

  // smoke-test-fake-backend-windows
  public static final String FAKE_BACKEND_WINDOWS_VERSION = "20260703.28649343291";

  private ImageVersions() {}
}
