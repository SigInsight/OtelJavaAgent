plugins {
  id("otel.javaagent-testing")
}

dependencies {
  compileOnly("javax.servlet:javax.servlet-api:3.0.1")

  testImplementation(project(":instrumentation:servlet:servlet-3.0:testing"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testImplementation(project(":instrumentation:servlet:servlet-common:bootstrap"))

  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:8.0.41")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:8.0.41")

  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-core:9.+") // see servlet-5.0 module
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:9.+") // see servlet-5.0 module
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter")
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}

if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
