# OpenTelemetry Spring Boot Starter

This starter initializes the OpenTelemetry SDK through Spring Boot auto-configuration. It includes the
Spring Boot autoconfigure module, OpenTelemetry SDK and OTLP exporter dependencies, resource providers,
and the Spring-aware library instrumentation used by this distribution.

Add the published starter artifact to a Spring Boot application, then configure OpenTelemetry with the
standard `otel.*` Spring properties, JVM system properties, or environment variables. For example:

```properties
otel.service.name=my-service
otel.traces.exporter=otlp
otel.exporter.otlp.endpoint=http://localhost:4318
```

The starter is tested with Spring Boot 3.1 and 3.2 smoke tests. Spring Boot 4 test coverage also exists,
but the primary target of this distribution is Spring Boot 3 on Java 17 or 21.

Use either this starter or the Java agent for a service unless their combination has been explicitly
tested in that service. The starter configures SDK initialization; the agent performs runtime bytecode
instrumentation of supported libraries.

See the [Spring and Spring Boot guide](../../README.md) for included and excluded capabilities. For
upstream starter documentation, see the [OpenTelemetry Spring Boot guide](https://opentelemetry.io/docs/zero-code/java/spring-boot/).
