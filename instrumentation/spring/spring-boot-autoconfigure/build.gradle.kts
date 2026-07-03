plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

base.archivesName.set("opentelemetry-spring-boot-autoconfigure")
group = "io.opentelemetry.instrumentation"

val springBootVersion = "3.2.4"

// r2dbc-proxy is shadowed to prevent org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration
// from being loaded by Spring Boot (by the presence of META-INF/services/io.r2dbc.spi.ConnectionFactoryProvider) - even if the user doesn't want to use R2DBC.
sourceSets {
  main {
    val shadedDep = project(":instrumentation:r2dbc-1.0:library-instrumentation-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow-spring"),
      "builtBy" to ":instrumentation:r2dbc-1.0:library-instrumentation-shaded:extractShadowJarSpring",
    )
  }
  create("javaSpring3") {
    java {
      setSrcDirs(listOf("src/main/javaSpring3"))
    }
  }
  create("javaSpring4") {
    java {
      setSrcDirs(listOf("src/main/javaSpring4"))
    }
  }
}

configurations {
  named("javaSpring3CompileOnly") {
    extendsFrom(configurations["compileOnly"])
  }
  named("javaSpring4CompileOnly") {
    extendsFrom(configurations["compileOnly"])
  }
}

dependencies {
  compileOnly("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
  annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:$springBootVersion")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")
  implementation(project(":instrumentation-annotations-support"))
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))
  compileOnly(
    project(
      path = ":instrumentation:r2dbc-1.0:library-instrumentation-shaded",
      configuration = "shadow"
    )
  )
  implementation(project(":instrumentation:spring:spring-web:spring-web-3.1:library"))
  implementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:library"))
  implementation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.3:library"))
  implementation(project(":instrumentation:log4j:log4j-appender-2.17:library"))
  compileOnly("org.apache.logging.log4j:log4j-core:2.17.0")
  implementation(project(":instrumentation:logback:logback-appender-1.0:library"))
  implementation(project(":instrumentation:logback:logback-mdc-1.0:library"))
  compileOnly("ch.qos.logback:logback-classic:1.0.0")
  implementation(project(":instrumentation:jdbc:library"))
  implementation(project(":instrumentation:runtime-telemetry:library"))

  library("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-aop:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-data-mongodb:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-data-r2dbc:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-data-jdbc:$springBootVersion")

  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-declarative-config")
  implementation(project(":sdk-autoconfigure-support"))
  implementation(project(":declarative-config-bridge"))
  compileOnly("io.opentelemetry:opentelemetry-extension-trace-propagators")
  // xray-propagator 已移除
  compileOnly("io.opentelemetry:opentelemetry-exporter-logging")
  compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")
  // zipkin exporter 已移除
  compileOnly(project(":instrumentation-annotations"))

  compileOnly(project(":instrumentation:resources:library"))
  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")

  testLibrary("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }

  testImplementation("jakarta.servlet:jakarta.servlet-api:5.0.0")
  testRuntimeOnly("com.h2database:h2:1.4.197")
  testRuntimeOnly("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")

  testImplementation("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
  testImplementation(project(":instrumentation:resources:library"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  // xray-propagator 已移除
  testImplementation("io.opentelemetry:opentelemetry-exporter-logging")
  testImplementation("io.opentelemetry:opentelemetry-exporter-otlp")
  // zipkin exporter 已移除
  testImplementation(project(":instrumentation-annotations"))
  testImplementation(project(":instrumentation:spring:spring-boot-autoconfigure:testing"))

  // give access to common classes
  add("javaSpring3CompileOnly", files(sourceSets.main.get().output.classesDirs))
  add("javaSpring3CompileOnly", "org.springframework.boot:spring-boot-starter-web:3.2.4")
  add("javaSpring3CompileOnly", "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  add(
    "javaSpring3CompileOnly",
    project(":instrumentation:spring:spring-web:spring-web-3.1:library")
  )
  add(
    "javaSpring3CompileOnly",
    project(":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:library")
  )

  // Spring Boot 4
  add("javaSpring4CompileOnly", files(sourceSets.main.get().output.classesDirs))
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-autoconfigure:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-jdbc:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-starter-jdbc:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-restclient:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-webclient:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-starter-data-mongodb:4.0.0")
  add("javaSpring4CompileOnly", project(":instrumentation:mongo:mongo-3.1:library"))
  add(
    "javaSpring4CompileOnly",
    project(":instrumentation:spring:spring-web:spring-web-3.1:library")
  )
  add("javaSpring4CompileOnly", project(":instrumentation:spring:spring-webflux:spring-webflux-5.3:library"))
}

// 因为项目去掉了对 Spring Boot 2 的兼容，现在全部测试都必须基于 Spring Boot 3（需要 Java 17），
// 所以把最低 Java 版本强制设为 17，避免在旧版本 JDK 上运行时因类版本不兼容而失败。
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

val testJavaVersion = otelProps.testJavaVersion
val testSpring3 =
  (testJavaVersion == null || testJavaVersion.compareTo(JavaVersion.VERSION_17) >= 0)

testing {
  suites {
    val testLogbackAppender by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation("io.opentelemetry:opentelemetry-sdk")
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

        implementation(project(":instrumentation:logback:logback-appender-1.0:library"))
        implementation(project(":instrumentation:logback:logback-mdc-1.0:library"))
        // using the same versions as in the spring-boot-autoconfigure
        // Spring Boot 3.2.4 配套 logback 1.4.14 / slf4j 2.0.12；3.2 的 LogbackLoggingSystem
        // 引用 logback-core 1.4+ 才有的 SanityChecker，旧的 1.2.11 / 1.7.32 锁会 NoClassDefFoundError。
        // ea4b523a 升 springBootVersion 到 3.2.4 时漏了这里。
        implementation("ch.qos.logback:logback-classic") {
          version {
            strictly("1.4.14")
          }
        }
        implementation("org.slf4j:slf4j-api") {
          version {
            strictly("2.0.12")
          }
        }
      }
    }

    val testLogbackMissing by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

        implementation("org.slf4j:slf4j-api") {
          version {
            strictly("1.7.32")
          }
        }
      }
    }

    val testSpring3 by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        val version = baseVersion("3.2.4").orLatest("3.+")
        implementation("org.springframework.boot:spring-boot-starter-web:$version")
        implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
        implementation(project(":instrumentation:spring:spring-web:spring-web-3.1:library"))
        implementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:library"))
        implementation("jakarta.servlet:jakarta.servlet-api:5.0.0")
        implementation("org.springframework.boot:spring-boot-starter-test:$version")
        implementation(project(":instrumentation:spring:spring-boot-autoconfigure:testing"))
      }
    }

    val testSpring4 by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
        val version = baseVersion("4.0.0").orLatest()
        implementation("org.springframework.boot:spring-boot-starter-jdbc:$version")
        implementation("org.springframework.boot:spring-boot-restclient:$version")
        implementation("org.springframework.boot:spring-boot-webclient:$version")
        implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:$version")
        implementation("io.opentelemetry:opentelemetry-sdk")
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation(project(":instrumentation-api"))
        implementation(project(":instrumentation:spring:spring-boot-autoconfigure:testing"))
        implementation("org.springframework.boot:spring-boot-starter-test:$version")
        runtimeOnly("com.h2database:h2:1.4.197")
        runtimeOnly("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")
      }
    }

    val testDeclarativeConfig by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation("io.opentelemetry:opentelemetry-sdk")
        implementation("io.opentelemetry:opentelemetry-exporter-otlp")
        implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
        implementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
          exclude("org.junit.vintage", "junit-vintage-engine")
        }
        implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
      }
    }
  }
}

configurations.configureEach {
  if (name.contains("testLogbackMissing")) {
    exclude("ch.qos.logback", "logback-classic")
  }
}

tasks {
  compileTestJava {
    options.compilerArgs.add("-parameters")
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)

    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    // for @SetEnvironmentVariable
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  named<JavaCompile>("compileJavaSpring3Java") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  named<JavaCompile>("compileTestSpring3Java") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  named<Test>("testSpring3") {
    isEnabled = testSpring3
  }

  named<JavaCompile>("compileJavaSpring4Java") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  named<JavaCompile>("compileTestSpring4Java") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  named<Test>("testSpring4") {
    isEnabled = testSpring3 // same condition as Spring 3 (requires Java 17+)
  }

  named<Jar>("jar") {
    from(sourceSets["javaSpring3"].output)
    from(sourceSets["javaSpring4"].output)
  }

  named<Jar>("sourcesJar") {
    from(sourceSets["javaSpring3"].java)
    from(sourceSets["javaSpring4"].java)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testing.suites, testStableSemconv)
  }
}
