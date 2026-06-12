# Agent JAR 瘦身与定制化修改记录

按 git 提交时间顺序记录每一类修改。每个章节包含简要说明、移除/修改清单，以及相关 commit hash 便于追溯。

## 当前状态

- 最终 JAR: ~16 MB
- Bootstrap libs: 1.2 MB
- Agent libs (relocated): ~14 MB
- OTel API 版本模块: 18 个全部保留（中间桥接节点不可删，详见第 11 步回滚说明）

---

## 第 1 步：Spring Boot 2 与长尾模块大规模清理 [✅ 已完成]

`ea4b523a`

奠定整体精简方向的第一刀。一次性移除 Spring Boot 2 支持、云与平台 SDK、Micrometer/JMX/Runtime telemetry、消息队列与流处理、RPC/服务调用相关支持，以及一批长尾框架。

涉及 settings.gradle.kts 入口、docs/supported-libraries.md、CI workflow（dependency-review、sonatype-guide-dependency-audit-daily）。

---

## 第 2 步：恢复系统级 Metrics 提取 [✅ 已完成]

`e8791fb7`

第 1 步连带删掉了 system metrics 相关的能力，本步把它接回。

恢复内容：

- `instrumentation/oshi-5.0`（host/process metrics 提取）
- `SimpleAsyncTaskExecutorInstrumentation`（spring-core-2.0 临时保留，后续在第 3 步删除）

注：未确认是否需要把 metrics 注入回 spring-boot-starter，待业务验证后决定。

---

## 第 3 步：长尾 Web/UI 框架物理删除 [✅ 已完成]

`b7b24bde / b83d3892 / ed6f1529 / 49f68f65 / 0e47071f`

继续清理长尾 Web/UI 框架，并补齐第 1 步遗漏的 Spring MQ 封装残留源码。

移除：

- `spring-mq-*` 残留源码和文档
- `spring-core-2.0`（连同 `SimpleAsyncTaskExecutorInstrumentation`，并修正所有测试/构建依赖）
- `grails-3.0`、`gwt-2.0`、`jsp-2.3`、`restlet-1.1`、`restlet-2.0`
- `play-mvc`、`play-ws`、`ratpack`、`spark-2.3`
- `wicket-8.0`
- `jsf-*`、`jsf-common-*`
- `avaje-jex-3.0`、`finatra-2.9`、`jfinal-3.2`

---

## 第 4 步：RPC 全链路移除 [✅ 已完成]

`c91e49a8`

第 1 步只在 Spring 封装层删除了 RPC，本步删掉所有 RPC 底层 instrumentation。

移除：

- `instrumentation/rmi/*`、`javaagent-bootstrap` 中的 RmiClientLookupSupplier / RmiContextLookupSupplier / RmiContextServerLookupSupplier / RmiServerLookupSupplier
- `AdditionalLibraryIgnoredTypesConfigurer.java` 中 `org.springframework.remoting` 的 RMI 白名单条目
- `GlobalIgnoredTypesConfigurer.java` 中 `java.rmi.` 与 `sun.rmi.*` 的 allow

保留：所有 HTTP client 和 Spring 出站 HTTP 封装层。

同步更新：`AgentDistributionConfigTest.java` 和 `distribution-config.yaml`。

---

## 第 5 步：云 SDK / 消息队列 / Micrometer / Spring MQ / finagle [✅ 已完成]

`f33f0cca`

物理删除第 1 步标记但当时未删干净的源码，并清理所有衍生引用。

移除源码目录：

- 云与平台 SDK：`aws-sdk-1.11/javaagent`（含 RequestExecutorInstrumentation 等）
- 消息队列与流处理：JMS、Kafka clients 0.11 / 2.6、ActiveMQ、Pulsar、RabbitMQ、RocketMQ、Spring MQ 封装
- Micrometer / JMX / Runtime telemetry 残留
- Apache Dubbo 2.7
- 其它 finagle 相关 instrumentation 源码

同步清理：

- `settings.gradle.kts`
- `docs/supported-libraries.md`、`docs/instrumentation-list.yaml`、`docs/contributing/writing-instrumentation.md`
- `declarative-config-bridge` 测试
- `AdditionalLibraryIgnoredTypesConfigurer.java` 中相应的 ignore/allow 条目
- `spring-boot-common/build.gradle.kts` 的 Kafka 编译/反射残留
- `reflect-config.json` 中相应的反射配置

---

## 第 6 步：基础设施模块批量清理 [✅ 已完成]

`8d7dd0f6 / 4993d228 / f7d5d78f / be881e4d`

### Apache Camel（`8d7dd0f6`）

`camel-2.20` 大量测试依赖已被前面步骤删干净（HTTP client、JMS、Kafka 等），整体移除。

### 任务调度 / 批处理 / 作业系统（`4993d228`）

移除：`quartz-2.0`、`spring-scheduling-3.1`、`spring-batch-3.0`、`xxl-job-2.1`、`powerjob-4.0`、`apache-elasticjob-3.0`。

### benchmark-jfr-analyzer（`f7d5d78f`）

移除性能基准测试工具，不影响 agent 功能。

### 异步 / 并发 / 响应式部分非核心模块（`be881e4d`）

保守删除与 OTel context propagation 关联较弱的模块：
`failsafe-3.0`、`hystrix-1.4`、`guava-10.0`、`kotlinx-coroutines`、`scala-forkjoin-2.8`、`akka-actor`、`pekko-actor`。

---

## 第 7 步：到达可编译状态 [✅ 已完成]

`de04b199`

第 1-6 步累积删除产生大量编译错误，本步统一修补：

- 修正 settings.gradle.kts 残留 include
- 删除/调整对已删类的引用
- 清理孤儿 build.gradle.kts

里程碑节点 — 后续二分调试时常用作"已知通过"基线。

---

## 第 8 步：数据库连接池精简 [✅ 已完成]

`8114bec6 / 81ecdfd2`

### 移除

整批连接池 instrumentation 模块：

- `alibaba-druid-1.0`
- `apache-dbcp-2.0`
- `c3p0-0.9`
- `hikaricp-3.0`
- `oracle-ucp-11.2`
- `vibur-dbcp-11.0`
- `tomcat-jdbc-8.5`

同步清理：

- `settings.gradle.kts` 构建入口
- `docs/supported-libraries.md` 中对这些连接池的正式支持条目
- `JDBC 公共测试`里对 tomcat/hikari/c3p0/druid 的依赖、样例与兼容分支
- 公共 helper：`DbConnectionPoolMetrics`、`DbConnectionPoolMetricsAssertions`
- `IgnoredTypesMatcher` 中 c3p0 特判
- 文档测试样例
- `DATABASE_POOL_METRICS` 枚举项
- `smoke-tests-otel-starter` 中 Hikari 的 native-image 反射配置残留

### 保留

- JDBC 主 trace 逻辑
- `jdbc-datasource` 的 `DataSource#getConnection` trace 逻辑

### 配套修复（`81ecdfd2`）

修复 `:instrumentation:jdbc:library:test` 的失败：derby URL 加 `;create=true`，并整理 `ConfigPropertiesBackedDeclarativeConfigPropertiesTest`。

### Fix（事后修复，`c5101f6f`）

第 8 步删除连接池模块时，把 `AbstractJdbcInstrumentationTest` 里依赖连接池的脚手架（`@BeforeAll prepareConnectionPoolDatasources()`、`keepDatabasesAlive()`、参数化测试矩阵）一并精简掉了。后果是 H2 内存数据库在 `@BeforeEach clearAllExportedData()` 之后才完成内部初始化，`INFORMATION_SCHEMA.LOB_*` 等内部 SQL 污染了 trace 断言，导致 `:instrumentation:jdbc:javaagent:test` 中 8 个测试（含 `testFailedStatement`）失败。

修复策略：把 `AbstractJdbcInstrumentationTest` 恢复到 `de04b199` 时的版本，重新引入 `tomcat-jdbc / tomcat-juli / HikariCP / c3p0` 4 个**测试侧依赖**（`compileOnly` / `testLibrary`）。这些依赖只服务于测试脚手架，不会进入生产 Agent JAR，**不重新启用任何连接池 instrumentation**。

---

## 第 9 步：长尾 instrumentation 第二轮清理 [✅ 已完成]

`f2808cc8 / bc4d02ea`

### 数据库 / 搜索 / 缓存（`f2808cc8`）

移除：`jaxrs`、`elasticsearch`、`opensearch`、`couchbase`、`cassandra`、`graphql-java`、`geode-1.4`、`influxdb-2.4`、`twilio-6.6`、`spymemcached-2.12`、`rediscala-1.8`。

### 长尾 Web 框架与应用服务器（`bc4d02ea`）

移除：`helidon-4.3`、`grizzly-2.3`、`liberty-*`、`payara-5.2020`、`undertow-1.4`、`jetty-*`、`tomcat-7.0`、`servlet-2.2`、`quarkus-resteasy-reactive-1.11`、`activej-http-6.0`、`apache-shenyu-2.4`、`javalin-5.0 / 7.0`、`ktor-2.0 / 3.0`、`zio-2.0`、`zio-http-3.0`。

---

## 第 10 步：僵尸目录与残留引用清理 [✅ 已完成]

`e894a0b0`

清理前面几步留下的空目录和悬空引用：`.fossa.yml`、CI 配置（`latest-dep-versions.json`）、`docs/instrumentation-list.yaml`、`docs/supported-libraries.md`、各模块 build.gradle.kts 中的 instrumentation 测试依赖等。

---

## 第 11 步：OTel API 版本收敛 [❌ 已回滚]

`30f3527e / 05027adc`（执行）→ `a004d129 / d6901a58`（回滚）

### 原计划

`opentelemetry-api-shaded-for-instrumenting` 从 18 个版本收敛到 5 个（保留 `1.0` / `1.52` / `1.57` / `1.59` / `1.61`），删除 13 个中间版本：`1.4 / 1.10 / 1.15 / 1.27 / 1.31 / 1.32 / 1.37 / 1.38 / 1.40 / 1.42 / 1.47 / 1.50 / 1.56`。

### 回滚原因

被删除的 13 个版本不是叶子节点，而是 OTel API 桥接演进链路上的中间节点。当应用代码或 testing-common 的 `BaggageSpanProcessor` 路径在跨线程包装 context 时，需要沿着 `应用 API → bridge 1.10 → bridge 1.27 → bridge 1.59` 逐级路由。中间版本缺失 → `InstrumentationModule` 注册链断裂 → context 传播失败。

二分定位：`e59b82c2` PASS → `e894a0b0` PASS → `30f3527e` FAIL，确定 `30f3527e` 是肇事 commit。表现为 `:instrumentation:executors:javaagent:test` 中 7 个 `CompletableFutureTest` 全失败（`Timeout waiting for 1 traces`）。

### 修复（`a004d129`）

完整恢复 13 个 `instrumentation/opentelemetry-api/opentelemetry-api-1.X` 目录、`opentelemetry-api-shaded-for-instrumenting/build.gradle.kts`、`javaagent/build.gradle.kts`、`settings.gradle.kts` 中相应入口。

### 教训

未来若要重试该收敛，需要：

1. 阅读 `opentelemetry-api-shaded-for-instrumenting/build.gradle.kts` 的完整依赖关系
2. 用 `./gradlew :opentelemetry-api-shaded-for-instrumenting:dependencies` 看实际依赖拓扑
3. 每次只删一个叶子节点版本，删完立刻跑 `:instrumentation:executors:javaagent:test` 验证
4. 保留小粒度 commit 便于回滚

---

## 第 12 步：小众框架与 Web/HTTP 模块清理 [✅ 已完成]

`7e60127a / 6cf4bb96`

### 小众框架（`7e60127a`）

移除：`opencensus-shim`（遗留兼容层）、`jodd-4.2`（极少用）、`iceberg-1.8`（数据湖垂直领域）、`dropwizard-metrics-4.0 / dropwizard-views-0.7`。

### 小众 Web/HTTP 模块（`6cf4bb96`）

移除：vertx 全族（web、http-client、redis-client、rx-java、sql-client，共 11 个模块）、`spring-ws-2.0`、`spring-cloud-gateway`、`okhttp-2.2`、`async-http-client-1.8 / 1.9 / common-1.8`、`google-http-client-1.19`。

保留：**armeria-1.3**（`testing/dependencies-shaded-for-testing` 依赖 `armeria-junit5` 作为 HTTP 测试基础设施，几乎所有 HTTP 相关测试都使用它，删除会导致测试体系崩溃）。

---

## 第 13 步：移除云资源探测 [✅ 已完成]

`a8eb26d3`

### 移除依赖

从 `javaagent-tooling/build.gradle.kts` 和 `spring-boot-starter/build.gradle.kts` 移除：

- `opentelemetry-azure-resources`
- `opentelemetry-aws-resources`
- `opentelemetry-gcp-resources`
- `opentelemetry-cloudfoundry-resources`

从 `dependencyManagement/build.gradle.kts` 移除版本约束。

### 代码变更

- `ResourceProviderPropertiesCustomizer.java`：移除 12 个云 provider 注册（Azure 5 个、AWS 5 个、GCP 1 个、CloudFoundry 1 个）
- `OpenTelemetryAutoConfigurationTest.java`：更新断言

### 保留

- 本地资源探测（`instrumentation:resources:library`）：`container.id`、`host.name/host.id/host.arch`、`process.pid/process.runtime.*`、`os.type/os.description`
- `telemetry.distro.name` / `telemetry.distro.version`

### 影响评估

- 丢失：AWS/GCP/Azure/CloudFoundry 自动资源属性（`cloud.provider`、`cloud.region`、`instance.id` 等）
- 不影响：trace/metrics/logs 的收集、导出，以及本地资源属性

---

## 第 14 步：移除 Prometheus Exporter + Protobuf [✅ 已完成]

`2b60f462`

移除 `javaagent-tooling/build.gradle.kts` 中的 `opentelemetry-exporter-prometheus`，`protobuf-java:4.34.0` 作为传递依赖随之移除。

### 影响评估

- `OTEL_METRICS_EXPORTER=otlp` → 正常
- `OTEL_METRICS_EXPORTER=prometheus` → 不可用
- OTLP trace/metrics/logs 导出完全不受影响

---

## 第 15 步：移除非必要运行时依赖 [✅ 已完成]

`77c2d247 / 1288a3c7`

### 移除依赖

从 `javaagent-tooling/build.gradle.kts`：

- `opentelemetry-exporter-zipkin`（含传递依赖 zipkin-reporter、zipkin-sender-okhttp3）
- `opentelemetry-sdk-extension-jaeger-remote-sampler`
- `opentelemetry-aws-xray-propagator`
- `opentelemetry-contrib-samplers`

从 `spring-boot-autoconfigure/build.gradle.kts` 移除 xray-propagator 与 zipkin 的 compileOnly/testImplementation。

从 `settings.gradle.kts` 移除 `zipkin-spring-boot-starter`。

从 `dependencyManagement/build.gradle.kts` 移除对应版本约束。

### 代码变更

- `RemappingUrlConnection.java`：移除 AWS X-Ray shading 规则

### 配套修复（`1288a3c7`）

补充清理第 15 步遗漏的 zipkin/xray 引用。同时确认 `baggage-processor` 因测试依赖（`testing-common` 的 `LibraryTestRunner` 使用 `BaggageSpanProcessor`）必须保留，先保留在 `testing-common/build.gradle.kts`。

### Fix（事后修复，`5fe83d8d`）

`77c2d247` 把 `baggage-processor` 从 `javaagent-tooling/build.gradle.kts` 删了，导致 agent runtime 缺失该 processor。`testing-common` 编译能过（自己的 build.gradle.kts 还在依赖），但运行时 `-Dotel.java.experimental.span-attributes.copy-from-baggage` 机制失效，HTTP server 和其它 instrumentation 的 baggage 测试断言 `test-baggage-key-1` 缺失。

修复：把 `implementation("io.opentelemetry.contrib:opentelemetry-baggage-processor")` 加回 `javaagent-tooling/build.gradle.kts`。

### 影响评估

- `OTEL_TRACES_EXPORTER=otlp` → 正常
- `OTEL_TRACES_EXPORTER=zipkin` → 不可用
- `OTEL_PROPAGATORS=xray` → 不可用
- OTLP trace/metrics/logs 导出完全不受影响

---

## 第 16 步：同框架老版本清理（保守策略）[✅ 已完成]

`05027adc`（与第 11 步同 commit，已收敛到本步）

仅删除真正老旧的版本，保留多个近期版本保证兼容性。

| 框架 | 删除（极老） | 保留（近期 + 共用依赖） |
|------|---------------|-------------------------|
| Hibernate | 3.3 | 4.0、6.0、reactive-1.0、common-3.3、procedure-call-4.3、testing |
| MongoDB | 3.1:javaagent | 3.7、4.0、async-3.3、3.1:library、3.1:testing、common:testing |
| Jedis | 1.4:javaagent | 3.0、4.0、common-1.4 |
| Lettuce | 4.0 | 5.0、5.1、common:library |
| Redisson | 3.0 | 3.17、common-3.0 |
| Apache HttpClient | 2.0 | 4.0、4.3、5.0、5.2 |
| Netty | 3.8 | 4.0、4.1、common-4.0、common |

---

## 第 17 步：残留目录与文档死引用清理 [✅ 已完成]

`9a167cc4 / 155e47bb`

### 残留模块目录（`9a167cc4`）

删除前面步骤遗留下来的空目录或 settings 已不引用但目录残存的模块：

- `instrumentation/jedis/jedis-1.4/`（仅剩 `metadata.yaml`）
- `instrumentation/spring/starters/zipkin-spring-boot-starter/`（仅剩 build.gradle.kts + README.md）
- `smoke-tests/images/grpc/`（已删 gRPC 但 smoke-test 镜像目录残留）
- `instrumentation/quarkus-resteasy-reactive-1.11/`（已删但目录残存）

### 文档死引用（`155e47bb`）

`docs/supported-libraries.md` 中删除已移除模块对应的描述行（11 行表格行 + 1 处 Disabled instrumentations）：Apache Iceberg、Dropwizard Metrics/Views、Jodd Http、NATS Client、OpenAI Java SDK、Spring Cloud Gateway、Vert.x（5 个子模块）。保留 Ktor 行（ktor-1.0 仍在）。

---

## 第 18 步：修复 testInstrumentationEnabledOrderMatters 测试 [✅ 已完成]

`648ae941`

`c91e49a8`（第 4 步）改测试时把测试用例从 `armeria/armeria_grpc` 换成 `spring-web/spring-webflux`，但配套的 `distribution-config.yaml` 没同步声明这两个的 enabled/disabled，导致测试期望 spring-web 禁用但实际未配置而生效。

修复：在 `distribution-config.yaml` 中追加 `spring-webflux` 到 enabled、新增 `spring-web` 到 disabled。

---

## 第 19 步：同步上游 dca884a2a9 [✅ 已完成]

`99d469d2`

跟进上游 PR #18923：`YamlHelperTest` 把测试中重复的 `StringWriter`/`BufferedWriter` 样板抽成私有 helper 并用 try-with-resources 关闭 BufferedWriter，断言方向调整为 `assertThat(result).isEqualTo(expectedYaml)`。

---

## 保留：声明式配置（Declarative Config）

- 依赖：Jackson-databind + SnakeYAML + declarative-config SDK
- **保留原因**：提供 `default_enabled + enabled 列表` 的全局插桩控制、方法级排除的结构化配置、URL 模板规则等高级能力，环境变量无法完全替代。虽然当前未使用，但未来复杂场景下可能需要。

---

## 保留：Kotlin 协程扩展（opentelemetry-extension-kotlin）

- 依赖：`opentelemetry-extension-kotlin`（传递引入 `kotlin-stdlib`）
- **保留原因**：
  - **零体积收益**：`kotlin-stdlib` 通过 OkIO 3.x → OkHttp → OTLP Exporter 链路已存在于运行时，移除不能减小 JAR
  - **trace 链路风险**：移除后 Kotlin 协程 context 传播失效，Kotlin/Ktor 用户的分布式 trace 会断链
  - 关联模块 `instrumentation:opentelemetry-extension-kotlin-1.0` 提供桥接插桩，移除需同步处理

---

## 执行原则

- 每步之后跑 `./gradlew compileJava` & `./gradlew check --rerun-tasks` 验证
- 先删目录与 `settings.gradle.kts`，再清理衍生引用（fossa、文档、测试依赖、ignore 配置）
- `docs/instrumentation-list.yaml` 通过 `./gradlew :instrumentation-docs:run` 重生成
- 涉及测试基础设施（`testing-common`、各模块 `:testing`）的删除要单独验证：先跑相关 instrumentation 的 `:test`，避免编译过但运行时 NPE 或 trace 缺失
- 高敏感度模块（OTel API 版本桥接、context 传播链路、baggage processor、ignore 配置）改动前先理解上下游依赖拓扑
