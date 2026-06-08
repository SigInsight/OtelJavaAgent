import org.eclipse.aether.util.version.GenericVersionScheme

fun isStableVersion(version: String): Boolean {
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

tasks {
  val resolveLatestDepVersions by registering {
    group = "help"
    description = "Resolve latest dependency versions and write to .github/config/latest-dep-versions.json"

    // This task intentionally walks every subproject's resolved configurations from the root
    // build at execution time, which is incompatible with the configuration cache and project
    // isolation. It is run only on demand (nightly latest-deps refresh), so opt out explicitly
    // rather than block configuration-cache progress for the rest of the build.
    notCompatibleWithConfigurationCache("walks all subprojects' resolved configurations")

    doLast {
      if (gradle.startParameter.projectProperties["testLatestDeps"] != "true" ||
        gradle.startParameter.projectProperties["resolveLatestDeps"] != "true") {
        throw GradleException("Must run with -PtestLatestDeps=true -PresolveLatestDeps=true")
      }

      // Seed the versions map with the existing JSON so that entries which fail to resolve
      // (broken transitive deps, missing repos, etc.) are preserved rather than deleted.
      val outputFile = file(".github/config/latest-dep-versions.json")
      @Suppress("UNCHECKED_CAST")
      val existingVersions: Map<String, String> =
        if (outputFile.exists()) groovy.json.JsonSlurper().parse(outputFile) as Map<String, String>
        else emptyMap()

      val versions = sortedMapOf<String, String>()
      versions.putAll(existingVersions)

      val versionScheme = GenericVersionScheme()

      fun recordVersion(key: String, version: String) {
        val existing = versions[key]
        if (existing == null) {
          versions[key] = version
        } else {
          val existingStable = isStableVersion(existing)
          val newStable = isStableVersion(version)
          // Prefer stable over pre-release even if numerically lower, so that a pre-release
          // already in the JSON (e.g. 5.7.0-beta1) gets replaced by the latest stable (5.6.4).
          if ((!existingStable && newStable) ||
            (existingStable == newStable && versionScheme.parseVersion(version) > versionScheme.parseVersion(existing))) {
            versions[key] = version
          }
        }
      }

      val stableVersionCache = mutableMapOf<Triple<String, String, String>, String?>()

      // Resolve an artifact with a stability filter, rejecting pre-release versions.
      // Results are cached to avoid repeated Maven Central lookups for the same artifact.
      fun resolveStableVersion(project: Project, group: String, module: String, version: String): String? {
        return stableVersionCache.getOrPut(Triple(group, module, version)) {
          val detached = project.configurations.detachedConfiguration(
            project.dependencies.create("$group:$module:$version")
          )
          // Disable transitive resolution: we only need to know the latest stable version of
          // this artifact itself, not its full dependency graph. Transitive resolution can fail
          // on classifier-specific native JARs or other platform-specific artifacts, causing
          // resolveStableVersion() to return null and fall back to a pre-release version.
          detached.isTransitive = false
          detached.resolutionStrategy.componentSelection.all {
            if (!isStableVersion(candidate.version)) {
              reject("pre-release version")
            }
          }
          val resolutionResult = detached.incoming.resolutionResult
          resolutionResult.rootComponent.get()
            .dependencies
            .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
            .firstOrNull()
            ?.selected?.moduleVersion?.version
        }
      }

      subprojects {
        configurations
          .filter {
            it.isCanBeResolved &&
              ((it.name.contains("test", ignoreCase = true) && it.name.endsWith("RuntimeClasspath")) ||
                it.name == "compileClasspath")
          }
          .forEach { config ->
            config.incoming.resolutionResult.allDependencies.forEach { dep ->
              if (dep is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                val requested = dep.requested
                if (requested is org.gradle.api.artifacts.component.ModuleComponentSelector) {
                  val reqVersion = requested.version
                  val selectedVersion = dep.selected.moduleVersion?.version ?: return@forEach
                  val version = if (isStableVersion(selectedVersion)) selectedVersion
                    else resolveStableVersion(this@subprojects, requested.group, requested.module, reqVersion)
                    ?: selectedVersion // Fall back to pre-release if no stable version exists in range
                  if (reqVersion == "latest.release") {
                    recordVersion("${requested.group}:${requested.module}#+", version)
                  } else if (reqVersion.contains("+")) {
                    recordVersion("${requested.group}:${requested.module}#$reqVersion", version)
                  }
                }
              }
            }
          }
      }

      // Resolve Spring Boot catalog versions using a detached configuration.
      // The subproject scan doesn't capture these because the Spring Boot Gradle plugin
      // applies the BOM at an already-resolved version, not the original "3.+"/"4.+" range.
      listOf("3.+", "4.+").forEach { range ->
        val resolvedVersion = resolveStableVersion(project, "org.springframework.boot", "spring-boot-dependencies", range)
        if (resolvedVersion != null) {
          recordVersion("org.springframework.boot:spring-boot-dependencies#$range", resolvedVersion)
        }
      }

      // Resolve muzzle-only artifacts that don't appear in test classpaths.
      // muzzle pass/fail directives reference artifacts that resolveUpperBound() needs pinned,
      // but many of these are never pulled into a testLatestDeps configuration.
      val muzzleArtifacts = mutableSetOf<String>()
      subprojects {
        val muzzleExt = extensions.findByName("muzzle") ?: return@subprojects
        val directives = muzzleExt.javaClass.getMethod("getDirectives").invoke(muzzleExt)
        val directiveList = directives.javaClass.getMethod("get").invoke(directives) as Iterable<*>
        directiveList.forEach { directive ->
          if (directive == null) return@forEach
          val coreJdkProperty = directive.javaClass.getMethod("getCoreJdk").invoke(directive)
          val isCoreJdk = coreJdkProperty.javaClass.getMethod("getOrElse", Any::class.java)
            .invoke(coreJdkProperty, false) as Boolean
          if (isCoreJdk) return@forEach

          val groupProperty = directive.javaClass.getMethod("getGroup").invoke(directive)
          val moduleProperty = directive.javaClass.getMethod("getModule").invoke(directive)
          val group = groupProperty.javaClass.getMethod("getOrNull").invoke(groupProperty) as String?
            ?: return@forEach
          val module = moduleProperty.javaClass.getMethod("getOrNull").invoke(moduleProperty) as String?
            ?: return@forEach
          // Skip template variables like play_$scalaVersion that can't be resolved statically
          if (group.contains("\$") || module.contains("\$")) return@forEach
          muzzleArtifacts.add("$group:$module")
        }
      }
      muzzleArtifacts.forEach { coords ->
        val key = "$coords#+"
        val (group, module) = coords.split(":")
        // Use "latest.release" first; fall back to "+" when <release> metadata points to a
        // pre-release version (e.g. javax.servlet:servlet-api whose <release> is 3.0-alpha-1).
        val resolvedVersion = resolveStableVersion(project, group, module, "latest.release")
          ?: resolveStableVersion(project, group, module, "+")
        if (resolvedVersion != null) {
          recordVersion(key, resolvedVersion)
        }
        // If resolvedVersion is null the existing entry (seeded from the JSON at startup) is
        // preserved.  For artifacts that genuinely don't exist on any accessible Maven repo
        // (e.g. a fail-directive artifact or an intentional no-op pass directive), add a
        // sentinel entry manually:  "group:module#+": "0.0"
        // The 0.0 upper bound causes filterVersions() to skip all real versions so no muzzle
        // tasks are created.  See resolveUpperBound() in muzzle-check.gradle.kts for details.
      }

      outputFile.parentFile.mkdirs()
      outputFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(versions)) + "\n")

      logger.lifecycle("Wrote ${versions.size} pinned versions to ${outputFile.path}")
    }
  }
}
