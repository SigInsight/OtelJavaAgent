plugins {
  id("otel.java-conventions")
}

val springBootVersion = "3.2.4"

dependencies {
  compileOnly("org.springframework.boot:spring-boot-restclient:4.0.0")

  compileOnly("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-data-r2dbc:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")

  compileOnly(project(":instrumentation:spring:spring-boot-autoconfigure"))
  compileOnly("io.opentelemetry.javaagent:opentelemetry-testing-common")
  compileOnly("io.opentelemetry:opentelemetry-sdk")
  compileOnly("io.opentelemetry:opentelemetry-sdk-testing")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
}
