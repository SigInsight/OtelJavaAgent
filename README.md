# OpenTelemetry Java 自动注入项目

[![Release](https://img.shields.io/github/v/release/open-telemetry/opentelemetry-java-instrumentation?include_prereleases&style=)](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/open-telemetry/opentelemetry-java-instrumentation/badge)](https://scorecard.dev/viewer/?uri=github.com/open-telemetry/opentelemetry-java-instrumentation)
[![Slack](https://img.shields.io/badge/slack-@cncf/otel--java-blue.svg?logo=slack)](https://cloud-native.slack.com/archives/C014L2KCTE3)

* [项目简介](#项目简介)
* [快速开始](#快速开始)
* [构建体系](#构建体系)
* [手工构建与打包](#手工构建与打包)
* [支持的库与框架](#支持的库与框架)
* [配置与扩展](#配置与扩展)

## 项目简介

本项目提供一个 Java Agent JAR，可以附加到任意 Java 8 及以上应用上，动态注入字节码，从大量常见库和框架中采集遥测数据。
最终效果是不需要修改业务代码，就可以从 Java 应用中采集 traces、metrics 和 logs。

本仓库也会发布若干库的独立 instrumentation，可以不依赖完整 Agent 直接使用。

## 快速开始

下载 [最新版本](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar)，通过 `-javaagent` 参数启用：

```bash
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.resource.attributes=service.name=your-service-name \
     -jar myapp.jar
```

默认使用 [OTLP exporter](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp)，发送到 `http://localhost:4318`。
配置参数可通过 `-D` 参数或环境变量传入，详见 [agent configuration](https://opentelemetry.io/docs/zero-code/java/agent/configuration/)。

## 构建体系

### 分层架构

```
Layer 0  dependencyManagement        所有第三方依赖版本集中管理（BOM）
         │
Layer 1  instrumentation-api         核心插桩 API，所有模块依赖
         instrumentation-api-incubator  实验性 API（SQL 解析器等）
         instrumentation-annotations    @WithSpan 等注解
         │
Layer 2  javaagent-bootstrap          注入 bootstrap classloader 的类
         javaagent-extension-api       Agent 扩展 SPI
         muzzle                        字节码安全校验（版本兼容性）
         javaagent-tooling             Agent 主引擎（classloader 管理、类型匹配）
         javaagent-internal-logging-*  内部日志（SLF4J 重定位）
         sdk-autoconfigure-support     SDK 自动配置
         declarative-config-bridge     声明式配置桥接
         │
Layer 3  opentelemetry-*-shaded-for-instrumenting   OTel API 版本屏蔽（保留 1.0 + 1.57 + 1.59）
         │
Layer 4  :instrumentation:*:javaagent   各框架的插桩实现（100+ 子模块）
         :instrumentation:*:library     独立 library 插桩（不依赖 agent）
         :instrumentation:*:bootstrap   bootstrap classloader 代码
         │
Layer 5  :javaagent                   收集所有模块 → shadowJar → 最终 Agent JAR
```

### Agent JAR 组装管线

```
各 :instrumentation:X:javaagent 模块（各自独立编译 + shade + muzzle 校验）
         │
         ▼
:javaagent 模块动态扫描所有 instrumentation 子项目
  ├── bootstrapLibs    ← servlet-common, jdbc, executors 等基础模块
  ├── baseJavaagentLibs ← tooling, muzzle, internal, logging 等
  └── javaagentLibs    ← 所有 javaagent-instrumentation 插件的模块
         │
         ▼
三阶段 Shadow:
  1. buildBootstrapLibs          → bootstrapLibs.jar
  2. relocateBaseJavaagentLibs   → baseJavaagentLibs-relocated.jar
  3. relocateJavaagentLibs       → javaagentLibs-relocated.jar
         │
         ▼
shadowJar → opentelemetry-javaagent.jar
  ├── 根目录:    bootstrap 类（应用 classloader 可见）
  └── inst/:     agent 类（.classdata 后缀，按需加载）
```

### Shadow Jar 使用清单

| 模块 | 作用 |
|------|------|
| `:javaagent` | 最终产物，三阶段 shadow 合并所有模块 |
| `:javaagent-internal-logging-simple` | SLF4J → `io.opentelemetry.javaagent.slf4j` |
| `:opentelemetry-api-shaded-for-instrumenting` | OTel API 版本 shade（latest + v1.57 + v1.59） |
| `:opentelemetry-instrumentation-api-shaded-for-instrumenting` | Instrumentation API shade |
| `:testing:dependencies-shaded-for-testing` | 测试依赖隔离（Armeria, Netty, Jackson 等） |
| `:testing:agent-for-testing` | 测试用 Agent JAR |
| 每个 `:instrumentation:X:javaagent` | OTel API → shaded, Logger → PatchLogger |
| `:instrumentation:jdbc:library` | Library 级 shade |
| `:instrumentation:spring:spring-boot-autoconfigure` | Spring Boot 自动配置 shade |

### 测试层级

| 模块 | 作用 |
|------|------|
| `testing-common` | 单元测试工具（JUnit 5, AssertJ, Mockito） |
| `testing:dependencies-shaded-for-testing` | Shade 测试依赖避免冲突 |
| `testing:agent-for-testing` | 提供测试用 Agent JAR |
| `testing:agent-exporter` | 收集 trace/span 用于断言 |
| `smoke-tests` | Docker + Testcontainers 集成测试 |
| `benchmark-overhead-jmh` | JMH 性能基准测试 |

### Convention 插件链

每个 `:instrumentation:X:javaagent` 模块经过的插件链：

```
otel.dsl-conventions                      定义 otelProps 扩展
  └→ otel.java-conventions                java-library, errorprone, dependencyManagement
       └→ io...instrumentation.base        library/testLibrary 配置, 版本文件生成
            └→ io...javaagent-testing      shadow 插件, -javaagent 测试配置
                 └→ io...javaagent-instrumentation  muzzle 校验
                      └→ otel.javaagent-instrumentation  发布, archivesName
```

### 其他模块

| 模块 | 作用 |
|------|------|
| `bom` / `bom-alpha` | 发布用 BOM，不影响构建 |
| `custom-checks` | Error Prone 自定义检查（编译期校验） |
| `instrumentation-docs` | 文档生成工具 |
| `conventions` | Composite build，提供所有 Gradle convention 插件 |
| `gradle-plugins` | Muzzle generation/check 插件 |

## 手工构建与打包

### 一键构建

```bash
./gradlew clean :javaagent:shadowJar
```

最终产物在 `javaagent/build/libs/opentelemetry-javaagent-*.jar`。

同目录下还有两个变体：
- `*-base.jar` — 仅含 agent machinery，用于自定义发行版
- `*-dontuse.jar` — 普通 jar 输出，不可直接使用

### 中间产物

| 文件 | 作用 |
|------|------|
| `bootstrapLibs.jar` | bootstrap 依赖聚合包 |
| `javaagentLibs-relocated.jar` | 主 agent 依赖 relocate 后的聚合包 |
| `baseJavaagentLibs-relocated.jar` | base 变体的 relocate 聚合包 |

### 使用

```bash
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.resource.attributes=service.name=your-service-name \
     -jar myapp.jar
```

## 支持的库与框架

项目开箱即用地支持大量 [库与框架](docs/supported-libraries.md#libraries--frameworks)。
完整清单、禁用方法见 [supported-libraries.md](docs/supported-libraries.md)。

## 配置与扩展

- **Agent 配置**: https://opentelemetry.io/docs/zero-code/java/agent/configuration/
- **SDK 配置**: https://opentelemetry.io/docs/languages/java/configuration/
- **创建扩展**: [examples/extension/README.md](examples/extension/README.md)
- **创建发行版**: [examples/distro/README.md](examples/distro/README.md)
- **手动埋点**: https://opentelemetry.io/docs/languages/java/instrumentation/#manual-instrumentation
- **Logger MDC 自动注入**: [docs/logger-mdc-instrumentation.md](docs/logger-mdc-instrumentation.md)
- **调试日志**: `-Dotel.javaagent.debug=true`（非常冗长，仅排查问题时开启）
