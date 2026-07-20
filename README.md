# OpenTelemetry Java 自动注入项目

[![Slack](https://img.shields.io/badge/slack-@cncf/otel--java-blue.svg?logo=slack)](https://cloud-native.slack.com/archives/C014L2KCTE3)

* [项目简介](#项目简介)
* [快速开始](#快速开始)
* [构建体系](#构建体系)
* [手工构建与打包](#手工构建与打包)
* [支持的库与框架](#支持的库与框架)
* [支持策略](docs/support-policy.md)
* [配置与扩展](#配置与扩展)

## 项目简介

本项目是 OpenTelemetry Java Agent 的精简 fork，主要维护 Spring Boot 3、Java 17 和 Java 21
应用的自动插桩。它提供一个 Java Agent JAR，在不修改业务代码的前提下采集 traces、metrics
和 logs。

具体库和 JVM 的兼容性取决于模块级测试与发行内容；本 fork 不对主要维护范围之外的组合作出
通用支持承诺。维护范围、CI 验证和尽力兼容的定义见[支持策略](docs/support-policy.md)。


## 快速开始

当前不提供已验证的公共二进制下载地址。请从本仓库构建完整 Agent JAR：

```bash
./gradlew :javaagent:shadowJar

AGENT_JAR="$(find javaagent/build/libs -maxdepth 1 -type f \
  -name 'opentelemetry-javaagent-*.jar' \
  ! -name '*-base.jar' \
  ! -name '*-sources.jar' \
  -print -quit)"
```

再通过 `-javaagent` 参数启用：

```bash
java -javaagent:"$AGENT_JAR" \
     -Dotel.resource.attributes=service.name=your-service-name \
     -jar myapp.jar
```

默认使用 [OTLP exporter](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp)，发送到 `http://localhost:4318`。
配置参数可通过 `-D` 参数或环境变量传入，详见 [agent configuration](https://opentelemetry.io/docs/zero-code/java/agent/configuration/)。

## 构建体系

### 核心框架模块（提供 Agent 能力，不含具体框架插桩）

```
opentelemetry-java-instrumentation/
│
├── javaagent-bootstrap/                    Bootstrap 层 API
│   ├── OpenTelemetryAgent.java             入口（premain）
│   ├── AgentInitializer.java               初始化
│   ├── AgentClassLoader.java               Agent 类加载器
│   ├── AgentStarter.java                   启动接口
│   └── LambdaTransformer*.java             Lambda 插桩
│   构建产物 → bootstrapLibs.jar (1.2MB)
│
├── javaagent-extension-api/                扩展 API（外部扩展用接口）
│   ├── InstrumentationModule               定义插桩模块的接口
│   ├── TypeInstrumentation                 定义类型插桩的接口
│   ├── AgentListener                       Agent 生命周期回调
│   └── AgentExtension                      扩展点接口
│   构建产物 → 分拆入 bootstrapLibs + baseJavaagentLibs
│
├── javaagent-tooling/                      Agent 引擎核心（最大模块）
│   ├── AgentInstaller.java                 ByteBuddy 安装器
│   ├── AgentStarterImpl.java               启动实现
│   ├── ExtensionClassLoader.java           扩展类加载器
│   ├── OpenTelemetryInstaller.java         OTel SDK 初始化
│   ├── instrumentation/                    插桩加载引擎（SPI 发现 + 模块安装）
│   ├── config/                             配置管理
│   ├── field/                              Virtual Field 实现
│   ├── ignore/                             忽略规则引擎
│   └── muzzle/                             兼容性检查
│   构建产物 → baseJavaagentLibs-relocated.jar (~12MB, 含第三方依赖)
│
├── javaagent-tooling/javaagent-tooling-java9/   Java 9+ 特殊处理
├── javaagent-tooling/jdk18-testing/             JDK 18 测试支持
│
├── javaagent-internal-logging-application/ 应用日志集成
├── javaagent-internal-logging-simple/      内置简单日志实现
│
├── muzzle/                                 Muzzle 兼容性检查框架
│
├── instrumentation-api/                    插桩公共 API
│   ├── Instrumenter                        span 创建/结束的标准 API
│   └── VirtualField                        虚拟字段
│   构建产物 → bootstrapLibs.jar
│
├── instrumentation-api-incubator/          实验性 API
├── instrumentation-annotations/            @WithSpan 等注解
├── instrumentation-annotations-support/    注解支持
│
├── sdk-autoconfigure-support/              SDK 自动配置支持
├── declarative-config-bridge/              YAML 声明式配置桥接
│
├── opentelemetry-api-shaded-for-instrumenting/    OTel API 版本桥接（保留全部 18 个版本：v1.0、1.4、1.10、1.15、1.27、1.31、1.32、1.37、1.38、1.40、1.42、1.47、1.50、1.52、1.56、1.57、1.59、1.61）
├── opentelemetry-ext-annotations-shaded-for-instrumenting/  旧注解桥接
├── opentelemetry-instrumentation-api-shaded-for-instrumenting/  Instrumentation API 桥接
│
├── javaagent/                              最终组装
│   构建产物 → opentelemetry-javaagent-2.29.0-SNAPSHOT.jar (15.8MB)
│
├── bom/ / bom-alpha/                       发布用 BOM
├── dependencyManagement/                   统一版本约束
```

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
Layer 3  opentelemetry-*-shaded-for-instrumenting   OTel API 版本屏蔽（保留全部 18 个版本，含中间桥接节点）
         │
Layer 4  :instrumentation:*:javaagent   各框架的插桩实现（80 子模块）
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
  ├── bootstrapLibs    ← instrumentation-api, annotations-support, javaagent-bootstrap 等
  ├── baseJavaagentLibs ← tooling, muzzle, internal, logging, OTel API 桥接等
  └── javaagentLibs    ← 所有 javaagent-instrumentation 插件模块
         │
         ▼
三阶段 Shadow:
  1. buildBootstrapLibs          → bootstrapLibs.jar (1.2MB)
  2. relocateBaseJavaagentLibs   → baseJavaagentLibs-relocated.jar (~12MB)
  3. relocateJavaagentLibs       → javaagentLibs-relocated.jar (~2.6MB)
         │
         ▼
shadowJar → opentelemetry-javaagent.jar (15.8MB)
  ├── 根目录:    bootstrap 类（应用 classloader 可见）
  └── inst/:     agent 类（.classdata 后缀，包名重定位，应用不可见）
```

### JAR 体积分布

```
最终 JAR (15.8MB)
├── bootstrapLibs (1.2MB, 7.6%)        ← API 层，应用可见
│   ├── opentelemetry-api
│   ├── instrumentation-api
│   ├── instrumentation-annotations-support
│   └── javaagent-bootstrap + extension-api(bootstrap部分)
│
└── inst/ (14.6MB, 92.4%)              ← Agent 内部层，应用不可见
    ├── baseJavaagentLibs (~12MB)      ← 引擎 + 第三方依赖
    │   ├── ByteBuddy + ASM (~5MB)     字节码引擎
    │   ├── OTel SDK (~3MB)            Trace/Metric/Log SDK
    │   ├── Jackson (~2MB)             声明式配置 YAML 解析
    │   ├── OkHttp + OkIO (~2MB)       OTLP HTTP 客户端
    │   ├── Kotlin stdlib (~1.3MB)     OkIO 传递依赖（不可移除）
    │   └── javaagent-tooling 引擎代码
    │
    └── javaagentLibs (~2.6MB)         ← 具体框架插桩模块
        ├── servlet, spring-webmvc, spring-webflux...
        ├── jdbc, hibernate, mongo, redis...
        ├── okhttp, netty, reactor...
        └── opentelemetry-api 桥接模块
```

### ClassLoader 隔离

Agent 的类加载采用三级隔离，避免与用户应用的依赖冲突：

```
Bootstrap ClassLoader
  ← Agent 公共 API（opentelemetry-api、instrumentation-api、注解）
  ← 应用代码和 Agent 都能看到

AgentClassLoader（自定义，加载 inst/ 目录）
  ← Agent 内部实现（ByteBuddy、OTel SDK、Exporter）
  ← 应用代码看不到，包名被重定位（io.opentelemetry → io.opentelemetry.javaagent.shaded.io.opentelemetry）

Application ClassLoader
  ← 用户的应用代码和第三方库
  ← 和 Agent 内部实现完全隔离
```

### Shadow Jar 使用清单

| 模块 | 作用 |
|------|------|
| `:javaagent` | 最终产物，三阶段 shadow 合并所有模块 |
| `:javaagent-internal-logging-simple` | SLF4J → `io.opentelemetry.javaagent.slf4j` |
| `:opentelemetry-api-shaded-for-instrumenting` | OTel API 版本 shade（保留全部 18 个版本桥接，1.0 ~ 1.61）|
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

## 跑测试与排错

### 先清旧测试报告，再跑

Gradle 不会自动清 `build/test-results/`、`build/reports/`，老的失败 xml 会留下来。

```bash
# 推荐：整库范围清测试结果（秒级，不动构建产物）
find . -type d \( -name test-results -o -name reports \) -path '*/build/*' \
  -prune -exec rm -rf {} +

# 更彻底：连构建产物一起清（耗时，慎用）
./gradlew clean
```

### 跑全量测试

```bash
./gradlew test
```

跑单模块（推荐）：

```bash
./gradlew :instrumentation:jmx-metrics:library:test
./gradlew :declarative-config-bridge:test
```

跑单个测试类 / 方法：

```bash
./gradlew :instrumentation:jmx-metrics:library:test --tests "TomcatTest"
./gradlew :declarative-config-bridge:test \
  --tests "*ConfigPropertiesBackedDeclarativeConfigPropertiesTest.testJmxPrefix"
```

### 提取失败用例

跑完拿失败列表（不必翻 HTML 报告）：

```bash
find . -path '*/build/test-results/test/*.xml' \
  -exec grep -l '<failure\|<error' {} \;
```

每个失败 xml 里的失败方法名 + 首行消息：

```bash
for f in $(find . -path '*/build/test-results/test/*.xml' -exec grep -l '<failure\|<error' {} \;); do
  python3 -c "
import re
x=open('$f').read()
for m in re.finditer(r'<testcase name=\"([^\"]+)\"[^>]*>\s*<(failure|error)[^>]*message=\"([^\"]+)\"', x):
  print(m.group(1)+'  →  '+m.group(3)[:200].replace('&#10;',' | ').replace('&quot;','\"'))
"
done
```

## 支持的库与框架

[`docs/instrumentation-list.yaml`](docs/instrumentation-list.yaml) 是由 Gradle 从已注册模块生成的
发行清单；其中 `has_javaagent: true` 表示模块包含在默认 Java Agent 中。

[支持的库与框架索引](docs/supported-libraries.md#libraries--frameworks) 按类别汇总常用库、独立
library artifact 和默认禁用项。Spring Boot 3 的包含范围与明确排除项见
[Spring Boot 3 集成](instrumentation/spring/README.md)。

## 配置与扩展

- **Agent 配置**: https://opentelemetry.io/docs/zero-code/java/agent/configuration/
- **SDK 配置**: https://opentelemetry.io/docs/languages/java/configuration/
- **Spring Boot 3 集成**: [instrumentation/spring/README.md](instrumentation/spring/README.md)
- **维护与性能评估**: [docs/maintenance-policy.md](docs/maintenance-policy.md)
- **创建扩展**: [examples/extension/README.md](examples/extension/README.md)
- **创建发行版**: [examples/distro/README.md](examples/distro/README.md)
- **手动埋点**: https://opentelemetry.io/docs/languages/java/instrumentation/#manual-instrumentation
- **Logger MDC 自动注入**: [docs/logger-mdc-instrumentation.md](docs/logger-mdc-instrumentation.md)
- **调试日志**: `-Dotel.javaagent.debug=true`（非常冗长，仅排查问题时开启）
