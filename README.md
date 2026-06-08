# OpenTelemetry Java 自动注入项目

[![Release](https://img.shields.io/github/v/release/open-telemetry/opentelemetry-java-instrumentation?include_prereleases&style=)](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/open-telemetry/opentelemetry-java-instrumentation/badge)](https://scorecard.dev/viewer/?uri=github.com/open-telemetry/opentelemetry-java-instrumentation)
[![Slack](https://img.shields.io/badge/slack-@cncf/otel--java-blue.svg?logo=slack)](https://cloud-native.slack.com/archives/C014L2KCTE3)

* [项目简介](#项目简介)
* [快速开始](#快速开始)
* [配置 Agent](#配置-agent)
* [支持的库、框架与应用服务器](#支持的库框架与应用服务器)
* [创建 Agent 扩展](#创建-agent-扩展)
* [手动埋点](#手动埋点)
* [Logger MDC 自动注入](#logger-mdc-自动注入)
* [问题排查](#问题排查)
* [参与贡献](#参与贡献)
* [手工构建与打包](#手工构建与打包)

## 项目简介

本项目提供一个 Java Agent JAR，可以附加到任意 Java 8 及以上应用上，动态注入字节码，从大量常见库和框架中采集遥测数据。
这些遥测数据可以导出为多种格式。
你也可以通过命令行参数或环境变量配置 agent 和 exporter。
最终效果是不需要修改业务代码，就可以从 Java 应用中采集遥测数据。

本仓库也会发布若干库的独立 instrumentation，并且还在持续增加。
如果你更倾向于直接使用这些独立 instrumentation，而不是使用完整 Java agent，可以查看 [Supported Libraries](docs/supported-libraries.md#libraries--frameworks) 中的 standalone library instrumentation 列。

## 快速开始

你可以下载
[最新版本](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar)。

这个发行包同时包含：

- instrumentation agent
- 所有已支持库的自动注入逻辑
- 所有可用的数据 exporter

它提供的是开箱即用的自动化体验。

通过 JVM 的 `-javaagent` 参数启用该 agent：

```bash
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -jar myapp.jar
```

默认情况下，OpenTelemetry Java agent 使用 [OTLP exporter](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp)，并把数据发送到本地 `http://localhost:4318` 上的 [OpenTelemetry collector](https://github.com/open-telemetry/opentelemetry-collector/blob/main/receiver/otlpreceiver/README.md)。

配置参数可以通过 Java system properties（`-D` 参数）或环境变量传入。完整配置项请参考 [configuration documentation][config-agent]。例如：

```bash
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.resource.attributes=service.name=your-service-name \
     -Dotel.traces.exporter=zipkin \
     -jar myapp.jar
```

## 配置 Agent

这个 agent 的可配置项很多。你可以根据需要调整它的行为，例如：

- exporter 类型
- exporter 配置（例如上报地址）
- trace context 传播头
- 以及其他大量运行参数

详细的 agent 配置项请参考 [agent configuration docs][config-agent]。

额外的 SDK 配置环境变量和 system properties，请参考 [SDK configuration docs][config-sdk]。

注意：配置参数名未来仍可能变化。使用新版本时，建议回到这里重新确认。
如果你发现 bug 或异常行为，请提交 issue：
<https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues>

## 支持的库、框架与应用服务器

项目开箱即用地支持大量 [库与框架](docs/supported-libraries.md#libraries--frameworks)，以及主流 [应用服务器](docs/supported-libraries.md#application-servers)。

完整清单请查看 [supported-libraries.md](docs/supported-libraries.md)，其中也包含：

- 被禁用的 instrumentation
- 如何关闭不需要的 instrumentation

相关说明见：[disabled instrumentation](docs/supported-libraries.md#disabled-instrumentations) 和 [suppress unwanted instrumentation][suppress]。

## 创建 Agent 扩展

[Extensions](examples/extension/README.md) 可以在不单独维护一个发行版、也不 fork 本仓库的前提下，为 agent 增加功能。
例如你可以新增：

- 自定义 sampler
- 自定义 span exporter
- 新的默认配置

并把这些内容打进同一个 agent jar 中。

## 创建 Agent 发行版

[Distribution](examples/distro/README.md) 提供了如何创建单独发行版的说明，作为一组扩展 OpenTelemetry Java instrumentation agent 功能的示例。
它也展示了如何在加入自定义功能后重新打包 agent。

对大多数用户来说，更推荐使用 [Agent 扩展](#创建-agent-扩展)，因为方式更简单，而且不需要在每次 OpenTelemetry Java agent 发布新版本后都重新构建。

## 手动埋点

对大多数用户而言，开箱即用的自动注入已经足够，不需要再做额外工作。
但有时你可能仍然希望：

- 给自动生成的 span 增加额外属性
- 为你自己的业务代码手动创建 span

详细说明见 [Manual instrumentation][manual]。

## Logger MDC 自动注入

你可以把 trace ID、span ID 等链路信息自动注入到应用日志中。
详见 [Logger MDC auto-instrumentation](docs/logger-mdc-instrumentation.md)。

## 问题排查

如需打开 agent 内部调试日志，可添加：

`-Dotel.javaagent.debug=true`

注意：调试日志非常冗长，只应在排查问题时开启。
开启后会对应用性能产生负面影响。

## 参与贡献

参考 [CONTRIBUTING.md](CONTRIBUTING.md)。

### Maintainers

- [Lauri Tulmin](https://github.com/laurit), Splunk
- [Trask Stalnaker](https://github.com/trask), Microsoft

Maintainer 角色说明见：
<https://github.com/open-telemetry/community/blob/main/guides/contributor/membership.md#maintainer>

### Approvers

- [Gregor Zeitlinger](https://github.com/zeitlinger), Grafana Labs
- [Jack Berg](https://github.com/jack-berg), Grafana Labs
- [Jason Plumb](https://github.com/breedx-splk), Splunk
- [Jay DeLuca](https://github.com/jaydeluca), Grafana Labs
- [Jean Bisutti](https://github.com/jeanbisutti), Microsoft
- [John Watson](https://github.com/jkwatson), Sublime Security
- [Jonas Kunz](https://github.com/JonasKunz), Elastic
- [Steve Rao](https://github.com/steverao), Alibaba
- [Sylvain Juge](https://github.com/SylvainJuge), Elastic

Approver 角色说明见：
<https://github.com/open-telemetry/community/blob/main/guides/contributor/membership.md#approver>

### Emeritus

- [Mateusz Rzeszutek](https://github.com/mateuszrzeszutek), Maintainer
- [Nikita Salnikov-Tarnovski](https://github.com/iNikem), Maintainer
- [Tyler Benson](https://github.com/tylerbenson), Maintainer

Emeritus 角色说明见：
<https://github.com/open-telemetry/community/blob/main/guides/contributor/membership.md#emeritus-maintainerapprovertriager>

### 感谢所有贡献者

<a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/graphs/contributors">
  <img alt="Repo contributors" src="https://contrib.rocks/image?repo=open-telemetry/opentelemetry-java-instrumentation" />
</a>

## 手工构建与打包

如果你要从当前工作区手动清理构建，并一路编译到最终可挂载 JVM 的主 agent 包，可以按下面顺序执行。

### 1. 清理历史构建产物

```bash
./gradlew clean
```

这一步会清理各模块下的 `build/` 目录，重点包括：

- `javaagent/build/`
- `javaagent-bootstrap/build/`
- `javaagent-tooling/build/`
- 各 `instrumentation/**/build/`

### 2. 先完成主源码编译

```bash
./gradlew --no-daemon --console=plain compileJava compileTestJava
```

如果你只想验证主 agent 模块相关内容，也可以单独执行：

```bash
./gradlew --no-daemon --console=plain :javaagent:compileJava
```

编译阶段的主要中间产物位置：

- `javaagent/build/classes/`：`javaagent` 模块编译后的 class 文件
- `javaagent/build/tmp/`：`javaagent` 模块任务的临时文件
- `*/build/classes/`：其他模块编译后的 class 文件
- `*/build/tmp/`：其他模块的临时文件

### 3. 生成最终主 agent 包

```bash
./gradlew --no-daemon --console=plain :javaagent:shadowJar
```

这个任务会先生成并消费几个中间 jar，然后再组装最终 agent：

- `javaagent/build/libs/bootstrapLibs.jar`
  作用：bootstrap 相关依赖的聚合包
- `javaagent/build/libs/javaagentLibs-relocated.jar`
  作用：主 agent 依赖 relocate 后的聚合包
- `javaagent/build/libs/baseJavaagentLibs-relocated.jar`
  作用：`base` 变体使用的 relocate 聚合包

### 4. 查看最终产物

最终可直接通过 `-javaagent:` 挂载到 JVM 的主包位于：

- `javaagent/build/libs/opentelemetry-javaagent-2.29.0-alpha-SNAPSHOT.jar`

这是 `:javaagent:shadowJar` 的输出，manifest 中包含：

- `Main-Class: io.opentelemetry.javaagent.OpenTelemetryAgent`
- `Agent-Class: io.opentelemetry.javaagent.OpenTelemetryAgent`
- `Premain-Class: io.opentelemetry.javaagent.OpenTelemetryAgent`

因此它才是最终主 agent 包。

同目录下还可能出现两个容易混淆的产物：

- `javaagent/build/libs/opentelemetry-javaagent-2.29.0-alpha-SNAPSHOT-base.jar`
  这是 `baseJavaagentJar` 产物，只包含 agent machinery 和必要 instrumentation，不是默认完整包。
- `javaagent/build/libs/opentelemetry-javaagent-2.29.0-alpha-SNAPSHOT-dontuse.jar`
  这是普通 `jar` 任务输出，构建脚本明确标注为不可直接使用。

### 5. 一条命令从 clean 走到最终主包

如果你希望一步完成清理、编译和最终打包，可以直接执行：

```bash
./gradlew --no-daemon --console=plain clean :javaagent:shadowJar
```

如果你还希望同时产出 `base` 变体，可执行：

```bash
./gradlew --no-daemon --console=plain clean :javaagent:assemble
```

因为 `:javaagent:assemble` 依赖：

- `shadowJar`
- `baseJavaagentJar`

### 6. 使用示例

```bash
java -javaagent:/absolute/path/to/javaagent/build/libs/opentelemetry-javaagent-2.29.0-alpha-SNAPSHOT.jar \
     -jar myapp.jar
```

[config-agent]: https://opentelemetry.io/docs/zero-code/java/agent/configuration/
[config-sdk]: https://opentelemetry.io/docs/languages/java/configuration/
[manual]: https://opentelemetry.io/docs/languages/java/instrumentation/#manual-instrumentation
[suppress]: https://opentelemetry.io/docs/zero-code/java/agent/disable/
