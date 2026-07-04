import com.google.cloud.tools.jib.gradle.JibTask
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("otel.java-conventions")

  id("com.google.cloud.tools.jib")
  id("org.springframework.boot") version "4.0.6"
}

// A single build script produces two image families selected by -PspringBootMajor:
//   3 (default): Spring Boot 3.5.14 on JDK 17/21/25, tagged jdk<N>-<tag>
//   2          : Spring Boot 2.6.15 on JDK 8/11, tagged sb2-jdk<N>-<tag>
// The SB 2.x image backs the cross-JDK compatibility smoke tests (Logs, SdkDisabled,
// Sampling, AgentDebugLogging); SB 3.x backs SpringBootSmokeTest + PropagationTest.
val springBootMajor = (project.findProperty("springBootMajor") as String?) ?: "3"
val springBootBomVersion = if (springBootMajor == "2") "2.6.15" else "3.5.14"
val imageTagPrefix = if (springBootMajor == "2") "sb2-jdk" else "jdk"

dependencies {
  implementation(platform("io.opentelemetry:opentelemetry-bom"))
  implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootBomVersion"))

  implementation("io.opentelemetry:opentelemetry-api")
  implementation(project(":instrumentation-annotations"))
  implementation("org.springframework.boot:spring-boot-starter-web")
}

if (springBootMajor == "2") {
  configurations.runtimeClasspath {
    resolutionStrategy {
      // SB 2.x requires old logback (and therefore also old slf4j)
      force("ch.qos.logback:logback-classic:1.2.13")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}

val targetJDK = project.findProperty("targetJDK") ?: "17"

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

java {
  if (springBootMajor == "2") {
    // Jib detects the Java version from sourceCompatibility to determine the entrypoint format.
    // Java 8 doesn't support the @argfile syntax (added in Java 9), so Jib needs to know
    // to use an expanded classpath format instead (e.g., /app/classes:/app/libs/*).
    if (targetJDK == "8") {
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
    } else if (targetJDK == "11") {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
    } else {
      sourceCompatibility = JavaVersion.VERSION_17
      targetCompatibility = JavaVersion.VERSION_17
    }
  } else {
    // SB 3.x requires Java 17+; the app targets 17 bytecode and runs on JDK 17/21/25.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

springBoot {
  buildInfo {
    properties {
      version = "1.2.3"
    }
  }
}

val repo = (System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation").lowercase()
val bootJarTask = tasks.named<Jar>("bootJar")

// Two image layouts coexist in this script:
//   SB 3.x (useBootJarLauncher=true): packages the bootJar and launches `java -jar /app/app.jar`
//     to exercise Spring Boot's JarLauncher/classloader.
//   SB 2.x (useBootJarLauncher=false): uses Jib's default exploded layout (classes in
//     /app/classes, deps in /app/libs/*, main-class entrypoint). This matches upstream's
//     pre-2026-04-13 smoke image — commit 15075b96 switched to the bootJar launcher, whose
//     SB 4.x JarLauncher is Java 17 bytecode and crashes on JDK 8/11 with
//     UnsupportedClassVersionError. Upstream's published jdk8/jdk11 image still uses the old
//     exploded layout (so upstream CI stays green); the fork rebuilds its own SB 2.x image,
//     so it must keep the exploded layout for JDK 8/11 compatibility.
val useBootJarLauncher = springBootMajor != "2"

val prepareBootJarForImage by tasks.registering(Sync::class) {
  // Copy the bootJar into the Jib extra directory so the bootJar-launcher image exercises
  // the typical `java -jar` packaging. Only wired up for SB 3.x (useBootJarLauncher=true).
  from(bootJarTask)
  into(layout.buildDirectory.dir("jib-extra/app"))
  rename { "app.jar" }
}

jib {
  from.image = "eclipse-temurin:$targetJDK"
  to.image = "ghcr.io/$repo/smoke-test-spring-boot:$imageTagPrefix$targetJDK-$tag"
  container.ports = listOf("8080")
  if (useBootJarLauncher) {
    // bootJar layout: launch the packaged jar with Spring Boot's JarLauncher.
    container.entrypoint = listOf("java", "-jar", "/app/app.jar")
    extraDirectories {
      paths {
        path {
          setFrom(layout.buildDirectory.dir("jib-extra").get().asFile.toPath())
          into = "/"
        }
      }
    }
  } else {
    // Exploded layout: Jib places classes in /app/classes and deps in /app/libs/* and
    // generates `java -cp /app/classes:/app/libs/* <mainClass>`. No JarLauncher is involved,
    // so the image runs on JDK 8/11. mainClass is set explicitly (the app's only main class)
    // rather than relying on Jib auto-detection.
    container.mainClass = "io.opentelemetry.smoketest.springboot.SpringbootApplication"
  }
}

tasks {
  withType<JibTask>().configureEach {
    if (useBootJarLauncher) {
      dependsOn(prepareBootJarForImage)
    }
    // Jib tasks access Task.project at execution time which is not compatible with configuration cache
    notCompatibleWithConfigurationCache("Jib task accesses Task.project at execution time")
  }

  val springBootJar by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
  }

  artifacts {
    add("springBootJar", bootJar)
  }
}
