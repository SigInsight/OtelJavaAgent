# Maintenance and performance policy

This document defines the operational maintenance rules for this Spring Boot 3-focused agent
distribution. The [support policy](support-policy.md) defines the product support levels.

## Release qualification

Releases are created by pushing a `vX.Y.Z` tag whose commit is an ancestor of `main`. The release
workflow verifies the final-agent shading tests, builds the distribution, and attaches exactly one
full Java Agent JAR to the GitHub Release. The `*-base.jar` and `*-sources.jar` artifacts are never
release candidates.

Before advertising a public download URL, verify that the GitHub mirror and the release asset are
publicly reachable. Until that is true, users should build the agent from the source repository.

## Instrumentation inventory

The Gradle project model is the source of truth for registered instrumentation modules. When adding,
removing, or materially changing a registered module, run:

```bash
./.github/scripts/metadata-ci-collect.sh
./gradlew :instrumentation-docs:runAnalysis
```

Commit a resulting `docs/instrumentation-list.yaml` change in the same change set. Pull requests
regenerate the file and fail on drift. A separate weekly GitHub workflow repeats the collection and
opens or updates a reminder issue; it does not write to the GitLab source repository.

## Performance evaluation

Two benchmark suites answer different questions and must not be compared as if they were the same
measurement.

| Suite | Purpose | Automation | Interpretation |
| --- | --- | --- | --- |
| `benchmark-overhead` | Macro overhead of a Petclinic deployment using containers, k6, and JFR | Every Monday and manually from GitHub Actions | Trend review against comparable historical runs; no automatic regression threshold |
| `benchmark-overhead-jmh` | Focused in-process Spring Boot request microbenchmark | Manual | Reproducible experiment on a declared machine and JDK; results are not cross-host release gates |

The weekly macro benchmark measures the current fork snapshot against the latest upstream agent
release and a no-agent baseline. Results are stored only on the GitHub `gh-pages` branch. Review
changes using comparable JDK, application image, benchmark settings, and execution environment.

When publishing a manual JMH result, record the source revision, JDK, machine or runner details,
JMH parameters, agent configuration, sampling/export configuration, and comparison baseline. Treat
one-off results as evidence for investigation, not as a universal performance claim.