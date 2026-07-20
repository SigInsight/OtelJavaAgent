# Spring and Spring Boot

This distribution is primarily maintained for Spring Boot 3 applications running on Java 17 or 21. It
contains automatic instrumentation for Spring Web MVC, Spring WebFlux, Spring HTTP clients, JDBC,
Hibernate, R2DBC, MongoDB, Logback, and Log4j. The complete module-level inventory is generated in
[`docs/instrumentation-list.yaml`](../../docs/instrumentation-list.yaml). The distinction between
maintained, CI-validated, and best-effort integrations is defined in the
[support policy](../../docs/support-policy.md).

## Choose an integration

Use the Java agent when you want automatic instrumentation without adding OpenTelemetry dependencies to
application code:

```bash
java -javaagent:path/to/opentelemetry-javaagent.jar \
	-Dotel.resource.attributes=service.name=my-service \
	-jar app.jar
```

Use the [Spring Boot starter](starters/spring-boot-starter/README.md) when OpenTelemetry SDK
initialization should be managed by Spring Boot auto-configuration. Choose one integration mechanism per
service unless the combination has been explicitly tested for that service.

## Included Spring Boot capabilities

The starter configures the OpenTelemetry SDK and includes Spring-aware library instrumentation for:

- Spring Web MVC and Spring WebFlux
- Spring HTTP clients
- JDBC, R2DBC, and MongoDB clients
- Logback and Log4j appenders and MDC support
- Spring Boot resource and runtime telemetry providers

The agent additionally instruments supported libraries that it discovers at runtime, including servlet
containers and common HTTP clients. See [`docs/supported-libraries.md`](../../docs/supported-libraries.md)
for the user-facing support matrix.

## Excluded capabilities

The following upstream integrations are intentionally not bundled in this distribution:

- Spring Data Repository method instrumentation
- Spring `@Scheduled` method instrumentation
- Micrometer and Spring Boot Actuator metric bridging
- Kafka, RabbitMQ, JMS, and other messaging instrumentation
- Kotlin coroutine context instrumentation

Applications can still use these libraries. The excluded integration is the dedicated OpenTelemetry
automatic instrumentation, not the application library itself.

## Settings

| System property | Type | Default | Description |
|---|---|---|---|
| `otel.instrumentation.spring-webmvc.experimental-span-attributes` | Boolean | `false` | Enable capture of experimental span attributes for Spring Web MVC. |
