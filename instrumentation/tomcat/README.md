# Instrumentation for Tomcat request handlers

Tomcat support is divided into the following sub-modules:

- `tomcat-common-7.0:javaagent` contains common type instrumentation, advice helper classes and abstract
  tracer used by the `javaagent` modules of all supported Tomcat versions
- `tomcat-10.0:javaagent` applies Tomcat request handler instrumentation for versions `[10,)`
