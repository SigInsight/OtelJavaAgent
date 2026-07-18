# Removed Instrumentation Modules Review

## Status

- Review state: remediation completed
- Last updated: 2026-07-18
- Baseline: fork based on OpenTelemetry Java Instrumentation 2.29.0-alpha
- Target runtime: Spring Boot 3 applications on Java 17+
- Scope: stale build, runtime, documentation, metadata, compliance, and CI references left after instrumentation removal
- Excluded: restoring intentionally removed instrumentation and unrelated working-tree changes

## Findings

| ID | Severity | Status | Finding | Evidence | Acceptance criteria |
|---|---|---|---|---|---|
| RIM-001 | High | Fixed | FOSSA targets the removed Zipkin Spring Boot starter project. | `.fossa.yml`; `settings.gradle.kts` | Regenerate FOSSA configuration and verify the removed target is absent. |
| RIM-002 | High | Fixed | Tag releases skip tests and do not verify that the tagged commit belongs to `main`. | `.github/workflows/release.yml` | Verify tag ancestry and run focused final-agent tests before publishing. |
| RIM-003 | Medium | Fixed | Spring Boot configuration metadata advertises the removed Zipkin exporter and AWS X-Ray propagator. | `instrumentation/spring/spring-boot-autoconfigure/src/main/resources/META-INF/additional-spring-configuration-metadata.json` | Remove stale values, add a regression test, and pass the module check. |
| RIM-004 | Medium | Fixed | The instrumentation documentation scanner accepts ignored `bin/` build output as instrumentation. | `instrumentation-docs/.../FileManager.java`; generated `docs/instrumentation-list.yaml` | Reject build-output paths and cover the behavior with unit tests. |
| RIM-005 | Medium | Fixed | The generated instrumentation list still advertises removed Hibernate Reactive instrumentation. | `docs/instrumentation-list.yaml`; removal commit `2c078983` | Regenerate the list after fixing discovery and verify the entry is absent. |
| RIM-006 | Medium | Fixed | Instrumentation metadata drift is not a pull-request or release gate. | `.github/workflows/metadata-update.yml` | Add a PR drift check that fails on generated-list differences. |
| RIM-007 | Medium | Fixed | The generated license inventory lists removed exporters and cloud resource providers. | `licenses/licenses.md`; final javaagent runtime classpath | Regenerate from current dependencies and verify removed components are absent. |
| RIM-008 | Low | Fixed | Spring documentation describes a removed Spring Cloud Gateway setting. | `instrumentation/spring/README.md` | Remove the unsupported setting. |
| RIM-009 | Low | Fixed | AWS X-Ray shading relocations remain without a runtime dependency. | javaagent shadowing, distro relocation, and muzzle-check Gradle plugins | Remove dead relocations and pass focused shading/plugin tests. |
| RIM-010 | Low | Fixed | Renovate contains package rules for removed cloud resource and X-Ray components. | `.github/renovate.json5` | Remove only stale package patterns and retain active rule entries. |
| RIM-011 | Medium | Fixed | Existing final-agent tests validate loaded providers but do not assert the expected critical SPI provider set. | `javaagent/src/test/java/io/opentelemetry/javaagent/ShadingTest.java` | Assert the critical `BeforeAgentListener` and `AgentListener` descriptor contents. |
| RIM-012 | High | Fixed | The release artifact glob can select the `-base.jar` instead of the complete shadow agent JAR. | `.github/workflows/release.yml`; `javaagent/build/libs` | Select exactly one non-base, non-sources agent JAR and fail on ambiguity. |

## Product Decisions

The following are intentional capability removals, not implementation defects. They remain accepted unless the target distribution requirements change.

| Capability | Status | Operational impact |
|---|---|---|
| Spring Data Repository instrumentation | Accepted | No repository method-level spans; lower-level database instrumentation may still apply. |
| Spring Scheduling instrumentation | Accepted | No dedicated spans for `@Scheduled` method execution. |
| Micrometer instrumentation | Accepted | No dedicated Micrometer/OpenTelemetry bridge for Spring Boot Actuator metrics. |
| Kafka, RabbitMQ, JMS, and other messaging instrumentation | Accepted | No automatic producer/consumer messaging context propagation for removed libraries. |
| Kotlin coroutine instrumentation | Accepted | OpenTelemetry Kotlin extensions do not replace coroutine context instrumentation. |

## Validation Log

| Date | Finding | Command or evidence | Result |
|---|---|---|---|
| 2026-07-17 | Baseline | `./gradlew projects --console=plain` | Passed; no retained Gradle project references a missing project. |
| 2026-07-17 | RIM-004 | `./gradlew :instrumentation-docs:test --console=plain` | Passed, confirming the stale build-output case lacked regression coverage. |
| 2026-07-17 | RIM-007 | `:javaagent:dependencies --configuration runtimeClasspath` search | Removed Zipkin, Prometheus, X-Ray, and cloud resource components were absent. |
| 2026-07-18 | RIM-001 | `./gradlew generateFossaConfiguration --console=plain` | Passed; configuration regenerated from the current publishable project set. |
| 2026-07-18 | RIM-001 | Zipkin target search and `./gradlew projects --console=plain` | Removed target absent; Gradle project model passed. |
| 2026-07-18 | RIM-003 | `./gradlew :instrumentation:spring:spring-boot-autoconfigure:check --console=plain` | Passed after removing stale metadata and adding a resource regression test. |
| 2026-07-18 | RIM-008 | Removed-setting search and `git diff --check` | Unsupported Spring Cloud Gateway setting absent; formatting passed. |
| 2026-07-18 | RIM-010 | Removed-package search and active-rule checks | Stale cloud/X-Ray rules absent; baggage, proto, and semconv rules retained. |
| 2026-07-18 | RIM-009 | Focused javaagent tests and Kotlin build-script compilation | Shading/classloading tests passed; conventions, Gradle plugins, and distro buildSrc compiled. |
| 2026-07-18 | RIM-009 | X-Ray source/runtime dependency searches | No X-Ray relocation or runtime dependency remained. |
| 2026-07-18 | RIM-004 | `./gradlew :instrumentation-docs:test --console=plain` | All 77 tests passed with build-output and missing-build-file regression coverage. |
| 2026-07-18 | RIM-005 | `./gradlew :instrumentation-docs:runAnalysis --console=plain` | Generated list removed only Hibernate Reactive and the `bin`-derived pseudo-module. |
| 2026-07-18 | RIM-011 | `./gradlew :javaagent:test --tests io.opentelemetry.javaagent.ShadingTest --console=plain` | Passed with exact critical lifecycle SPI provider assertions. |
| 2026-07-18 | RIM-002, RIM-006 | VS Code YAML diagnostics and `git diff --check` | Workflow syntax diagnostics and formatting passed; `actionlint` was unavailable. |
| 2026-07-18 | RIM-007 | Isolated `./gradlew generateLicenseReport --console=plain` | Generated report removed stale exporters/resource providers; existing user license-file edits were preserved. |
| 2026-07-18 | RIM-012 | Final artifact candidate search | Exactly one non-base, non-sources javaagent JAR was selected. |
| 2026-07-18 | Final | Instrumentation docs tests and direct-module Spotless checks | Passed for instrumentation-docs, Spring Boot autoconfigure, and javaagent. |
| 2026-07-18 | Final | Focused javaagent shading, SPI, and classloading tests | Passed against the final shadow agent JAR. |
| 2026-07-18 | Final | Product-source residual search and `git diff --check` | No target residuals or diff whitespace errors found. |

## Implementation Notes

- Generated files must be updated through their repository tasks rather than edited by hand.
- The root `plan.md` deletion is an unrelated user change and must remain untouched.
- Existing modifications under `licenses/` predate remediation. The report was regenerated in an isolated worktree so those files remained untouched.
- A complete golden list for every `InstrumentationModule` provider is intentionally out of scope; this review covers critical lifecycle SPI descriptors first.
- `:conventions:spotlessCheck` reports pre-existing formatting in the ignored `conventions/bin` generated copy. The modified conventions source compiled successfully and the generated copy was not changed.
