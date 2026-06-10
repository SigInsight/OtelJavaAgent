plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.asynchttpclient")
    module.set("async-http-client")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.asynchttpclient:async-http-client:2.0.0")

  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
}

val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()

if (!otelProps.testLatestDeps) {
  otelJava {
    // AHC uses Unsafe and so does not run on later java version
    maxJavaVersionForTests.set(JavaVersion.VERSION_1_8)
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    // async-http-client 3.0 requires java 11
    // We are not using minJavaVersionSupported for latestDepTest because that way the instrumentation
    // gets compiled with java 11 when running latestDepTest. Some Java 8-only latest dependency test
    // suites require the instrumentation itself to stay Java 8-compatible in order for muzzle to apply.
    if (otelProps.testLatestDeps && testJavaVersion.isJava8) {
      enabled = false
    }

    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

// async-http-client 2.0.0 does not work with Netty versions newer than this due to referencing an
// internal file.
if (!otelProps.testLatestDeps) {
  configurations.configureEach {
    if (!name.contains("muzzle")) {
      resolutionStrategy {
        eachDependency {
          // specifying a fixed version for all libraries with io.netty' group
          if (requested.group == "io.netty" && requested.name != "netty-bom") {
            useVersion("4.0.34.Final")
          }
        }
      }
    }
  }
}
