# Overhead tests

- [Process](#process)
- [What do we measure?](#what-do-we-measure)
- [Config](#config)
- [Agents](#agents)
- [Automation](#automation)
- [Setup and Usage](#setup-and-usage)
- [Visualization](#visualization)

This directory will contain tools and utilities
that help us to measure the performance overhead introduced by
the agent and to measure how this overhead changes over time.

The overhead tests here should be considered a "macro" benchmark. They serve to measure high-level
overhead as perceived by the operator of a "typical" application. The scheduled workflow runs on
Eclipse Temurin Java 21, matching the repository's `.java-version`.

## Process

There is one dynamic test here called [OverheadTests](src/test/java/io/opentelemetry/OverheadTests.java).
The `@TestFactory` method creates a test pass for each of the [defined configurations](src/test/java/io/opentelemetry/config/Configs.java).
Before the tests run, a single collector instance is started. Each test pass has one or more agents configured and those are tested in series.
For each agent defined in a configuration, the test runner (using [testcontainers](https://www.testcontainers.org/)) will:

1. create a fresh postgres instance and populate it with initial data.
2. create a fresh instance of [spring-petclinic-rest](https://github.com/spring-petclinic/spring-petclinic-rest) instrumented with the specified agent
3. measure the time until the petclinic app is marked "healthy" and then write it to a file.
4. if configured, perform a warmup phase. During the warmup phase, traffic is generated to get the application into a steady state (primarily helping facilitate JIT compilations). The current configuration uses a 60 second warmup.
5. start a JFR recording by running `jcmd` inside the petclinic container
6. run the [k6 test script](k6/basic.js) with the configured number of iterations through the file and the configured number of concurrent virtual users (VUs).
7. after k6 completes, petclinic is shut down
8. after petclinic is shut down, postgres is shut down

And this repeats for every agent configured in each test configuration.

After all the tests are complete, the results are written to `benchmark-overhead/results` as CSV and summary text files. The scheduled workflow copies them to `benchmark-overhead/results` on the GitHub-only `gh-pages` branch; they are not committed to `main`.

## What do we measure?

For each test pass, we record the following metrics in order to compare agents and determine
relative overhead.

| metric name              | units  | description                                                                  |
| ------------------------ | ------ | ---------------------------------------------------------------------------- |
| Startup time             | ms     | How long it takes for the spring app to report "healthy"                     |
| Total allocated mem      | bytes  | Across the life of the application                                           |
| Heap (min)               | bytes  | Smallest observed heap size                                                  |
| Heap (max)               | bytes  | Largest observed heap size                                                   |
| Thread switch rate       | # / s  | Max observed thread context switch rate                                      |
| GC time                  | ms     | Total amount of time spent paused for garbage collection                     |
| Request mean             | ms     | Average time to handle a single web request (measured at the caller)         |
| Request p95              | ms     | 95th percentile time to handle a single web request (measured at the caller) |
| Iteration mean           | ms     | average time to do a single pass through the k6 test script                  |
| Iteration p95            | ms     | 95th percentile time to do a single pass through the k6 test script          |
| Peak threads             | #      | Highest number of running threads in the VM, including agent threads         |
| Network read mean        | bits/s | Average network read rate                                                    |
| Network write mean       | bits/s | Average network write rate                                                   |
| Average JVM user CPU     | %      | Average observed user CPU (range 0.0-1.0)                                    |
| Max JVM user CPU         | %      | Max observed user CPU used (range 0.0-1.0)                                   |
| Average machine tot. CPU | %      | Average percentage of machine CPU used (range 0.0-1.0)                       |
| Total GC pause nanos     | ns     | JVM time spent paused due to GC                                              |
| Run duration ms          | ms     | Duration of the test run, in ms                                              |

## Config

Each config contains the following:

- name
- description
- list of agents (see below)
- maxRequestRate (optional, used to throttle traffic)
- concurrentConnections (number of concurrent virtual users [VUs])
- totalIterations - the number of passes to make through the k6 test script
- warmupSeconds - how long to wait before starting conducting measurements

Currently, we test:

- no agent
- the latest upstream OpenTelemetry Java agent release
- the current fork agent built from the checked-out source tree
- the current fork agent with Indy enabled

The upstream release is a comparison baseline. The fork variants come from the local
`javaagent/build/libs` output, so run a root-project assemble before running the benchmark.
The workflow records trends but does not enforce an automatic regression threshold; review material
changes against comparable runs manually.

Additional configurations belong in the `Configs` class.

### Agents

An agent is defined in code as a name, description, optional URL, and optional additional
arguments to be passed to the JVM (not including `-javaagent:`). New agents may be defined
by creating new instances of the `Agent` class. The `AgentResolver` is used to download
the relevant agent jar for an `Agent` definition.

## Automation

`.github/workflows/overhead-benchmark-weekly.yml` runs every Monday at 05:07 UTC and on manual
dispatch. It builds the current fork snapshot, runs the benchmark, and commits updated results to
the GitHub-only `gh-pages` branch. That branch must already contain
`benchmark-overhead/results/` and must not be mirrored from GitLab.

## Setup and Usage

The tests require Docker to be running. Build the agent first, then run `OverheadTests` in your IDE.

Alternatively, you can run the tests from
the command line with gradle:

```
./gradlew :javaagent:assemble -x javadoc
cd benchmark-overhead
./gradlew test
```

## Visualization

The workflow persists raw CSV and summary files on `gh-pages`; no visualization is maintained in
this fork.
