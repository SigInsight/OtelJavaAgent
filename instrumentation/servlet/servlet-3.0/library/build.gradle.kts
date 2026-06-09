plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("javax.servlet:javax.servlet-api:3.0.1")
  implementation(project(":instrumentation:servlet:servlet-common:library"))
  api(project(":instrumentation:servlet:servlet-common-javax:library"))

  testImplementation(project(":instrumentation:servlet:servlet-3.0:testing"))

  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:8.0.41")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:8.0.41")

  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-core:9.+") // see servlet-5.0 module
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:9.+") // see servlet-5.0 module
}

tasks {
  test {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  if (otelProps.testLatestDeps) {
    compileTestJava {
      options.release.set(11)
    }
  }
}
