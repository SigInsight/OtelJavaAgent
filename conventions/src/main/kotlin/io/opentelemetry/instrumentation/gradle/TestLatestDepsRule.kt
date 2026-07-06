package io.opentelemetry.instrumentation.gradle

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.CacheableRule

@CacheableRule
abstract class TestLatestDepsRule : ComponentMetadataRule {
  override fun execute(context: ComponentMetadataContext) {
    if (!isStableVersion(context.details.id.version)) {
      context.details.status = "milestone"
    }
  }
}

private fun isStableVersion(version: String): Boolean {
  val versionString = version.lowercase()
  val draftVersion = versionString.contains("rc")
    || versionString.contains(".cr")
    || versionString.contains("alpha")
    || versionString.contains("beta")
    || versionString.contains("-b")
    || versionString.contains(".m")
    || versionString.contains("-m")
    || versionString.contains("-dev")
    || versionString.contains("-ea")
    || versionString.contains("-atlassian-")
    || versionString.contains("public_draft")
    || versionString.contains("snapshot")
    || versionString.contains("test")
    || versionString.endsWith("-nf-execution")
    || versionString.startsWith("0.0.0-")
    || Regex("^.*-[0-9a-f]{7,}$").matches(versionString)
    || Regex("^\\d{4}-\\d{2}-\\d{2}t\\d{2}-\\d{2}-\\d{2}.*$").matches(versionString)
  return !draftVersion
}