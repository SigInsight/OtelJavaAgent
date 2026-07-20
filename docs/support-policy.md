# Support policy

This project is a trimmed OpenTelemetry Java Agent distribution. The support levels below describe
the maintenance commitment for this distribution; they do not change the upstream compatibility
claims of individual OpenTelemetry library artifacts.

## Maintained scope

The maintained product scope is Spring Boot 3 applications on Java 17 and Java 21. Work in this
scope includes the default Java Agent, the Spring Boot starter, the generated instrumentation
inventory, release qualification, and fixes for reproducible defects.

The [generated instrumentation list](instrumentation-list.yaml) is the authoritative inventory of
modules bundled in the default agent. A module being listed means it is packaged in the agent; it
does not by itself make every library version or deployment combination a maintained product
configuration.

## CI-validated scope

An integration is CI-validated only when the repository's current Gradle tests or smoke tests run
that integration. The relevant test task or workflow is the evidence for this level. CI coverage is
continuously updated, so it is not a substitute for a fixed compatibility matrix or an application
acceptance test.

## Best-effort compatibility

Some bundled general Java instrumentation is retained outside the maintained Spring Boot 3 scope.
It may work for an application and can receive build, security, or dependency maintenance, but this
project does not promise new compatibility coverage or a response-time target for it.

## Explicit exclusions

The following upstream integrations are intentionally absent from the default distribution:

- Spring Data Repository method instrumentation
- Spring `@Scheduled` method instrumentation
- Micrometer and Spring Boot Actuator metric bridging
- Kafka, RabbitMQ, JMS, and other messaging instrumentation
- Kotlin coroutine context instrumentation

An exclusion applies to OpenTelemetry automatic instrumentation only. It does not prohibit the
application from using the corresponding library.

## Maintaining the policy

When adding, removing, or changing the maintenance level of an integration, update the relevant
product documentation in the same change. Regenerate `instrumentation-list.yaml` after changing
the registered instrumentation modules, and update the Spring guide when the Spring Boot product
scope or an explicit exclusion changes.
