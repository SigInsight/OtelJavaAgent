import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
  id("otel.nullaway-conventions")
}

description = "opentelemetry-api shaded for internal javaagent usage"
group = "io.opentelemetry.javaagent"

val latestDeps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}
val v1_57Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_59Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
// configuration for publishing the shadowed artifact
val v1_57 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_59 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}

dependencies {
  latestDeps("io.opentelemetry:opentelemetry-api")

  listOf("opentelemetry-api-incubator").forEach {
    v1_57Deps("io.opentelemetry:$it") {
      version {
        strictly("1.57.0-alpha")
      }
    }
    v1_59Deps("io.opentelemetry:$it") {
      version {
        strictly("1.59.0-alpha")
      }
    }
  }
}

// OpenTelemetry API shaded so that it can be used in instrumentation of OpenTelemetry API itself,
// and then its usage can be unshaded after OpenTelemetry API is shaded
// (see more explanation in opentelemetry-api-1.0.gradle)
tasks {
  withType<ShadowJar>().configureEach {
    relocate("io.opentelemetry", "application.io.opentelemetry")
  }

  shadowJar {
    configurations = listOf(latestDeps)
  }

  val v1_57Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_57Deps)
    archiveClassifier.set("v1_57")
  }
  val v1_59Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_59Deps)
    archiveClassifier.set("v1_59")
  }

  artifacts {
    add(v1_57.name, v1_57Shadow)
    add(v1_59.name, v1_59Shadow)
  }
}
