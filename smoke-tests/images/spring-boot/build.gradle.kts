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

dependencies {
  implementation(platform("io.opentelemetry:opentelemetry-bom"))
  implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))

  implementation("io.opentelemetry:opentelemetry-api")
  implementation(project(":instrumentation-annotations"))
  implementation("org.springframework.boot:spring-boot-starter-web")
}

val targetJDK = project.findProperty("targetJDK") ?: "17"

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

java {
  // SB 3.x requires Java 17+; the app targets 17 bytecode and runs on JDK 17/21/25.
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
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

val prepareBootJarForImage by tasks.registering(Sync::class) {
  // Preserve Spring Boot's packaged runtime instead of Jib's default exploded layout so
  // smoke tests exercise the typical bootJar launcher/classloader behavior used by java -jar.
  from(bootJarTask)
  into(layout.buildDirectory.dir("jib-extra/app"))
  rename { "app.jar" }
}

jib {
  from.image = "eclipse-temurin:$targetJDK"
  to.image = "ghcr.io/$repo/smoke-test-spring-boot:jdk$targetJDK-$tag"
  container.entrypoint = listOf("java", "-jar", "/app/app.jar")
  container.ports = listOf("8080")
  extraDirectories {
    paths {
      path {
        setFrom(layout.buildDirectory.dir("jib-extra").get().asFile.toPath())
        into = "/"
      }
    }
  }
}

tasks {
  withType<JibTask>().configureEach {
    dependsOn(prepareBootJarForImage)
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
