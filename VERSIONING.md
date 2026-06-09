## shadowJar 精简候选名单

### 打包构成判断

`javaagent:shadowJar` 主要由三部分组成：

- `bootstrapLibs`
  - `instrumentation-api`
  - `instrumentation-api-incubator`
  - `instrumentation-annotations-support`
  - `javaagent-bootstrap`
  - `javaagent-extension-api` 的 bootstrap 部分
- `baseJavaagentLibs`
  - `javaagent-tooling`
  - `muzzle`
  - `javaagent` 内部 logging
  - `executors`
  - `internal-*`
  - 多个 `opentelemetry-api-*` / `opentelemetry-instrumentation-api-*` 兼容模块
- `javaagentLibs`
  - `instrumentation/**` 下所有启用 `id("otel.javaagent-instrumentation")` 的模块
  - `otel.sdk-extension` 模块

结论：

- 真正决定最终 agent 体积的大头是 `javaagentLibs`
- 如果目标是快速缩小产物体积，应优先删除整族 instrumentation，而不是先动 bootstrap / tooling

### 当前进入 shadowJar 的高密度模块家族

按 `otel.javaagent-instrumentation` 模块数量统计，当前体积压力较大的家族包括：

- `opentelemetry-api` 兼容层：16
- `jaxrs`：13
- `vertx`：10
- `spring`：9
- `elasticsearch`：9
- `couchbase`：7
- `hibernate`：6
- `log4j`：5
- `servlet`：4
- `reactor`：4
- `opensearch`：4
- `netty`：4
- `mongo`：4
- `jetty`：4
- `jedis`：4
- `async-http-client`：4

说明：

- 其中 `opentelemetry-api`、`executors`、`internal-*` 属于 agent 基础兼容层，不建议优先删除
- 高收益的精简对象应优先从长尾框架、重复版本矩阵、边缘数据访问生态中选择

### 建议立刻删除

这些模块家族对缩小 `shadowJar` 的收益高，同时不在当前保留能力的核心主线上：

- `jaxrs` 全家
  - 原因：版本分叉多，模块数高，属于典型兼容矩阵膨胀项
  - 影响：会移除 Jersey / Resteasy / CXF / JAX-RS server 相关自动插桩
- `elasticsearch` 全家
  - 原因：REST / transport 多代并存，模块密度高
  - 影响：移除 Elasticsearch client / transport tracing
- `opensearch` 全家
  - 原因：与 Elasticsearch 类似，属于另一套搜索客户端生态
  - 影响：移除 OpenSearch client tracing
- `couchbase` 全家
  - 原因：版本碎片多，含额外 shaded tracing 适配
  - 影响：移除 Couchbase tracing
- `cassandra` 全家
  - 原因：多版本 Java driver 兼容矩阵
  - 影响：移除 Cassandra tracing
- `clickhouse` 全家
  - 原因：多版本客户端分裂
  - 影响：移除 ClickHouse tracing
- `graphql-java` 全家
  - 原因：附加框架能力，不属于基础 HTTP / DB / JDBC 主线
  - 影响：移除 GraphQL instrumentation
- `geode-1.4`
  - 原因：边缘数据网格生态
- `influxdb-2.4`
  - 原因：边缘时序库生态
- `twilio-6.6`
  - 原因：单点第三方 SDK 支持，收益高于成本
- `spymemcached-2.12`
  - 原因：老旧客户端，长尾
- `nats-2.17`
  - 原因：消息中间件生态，已不在当前优先保留范围
- `mybatis-3.2`
  - 原因：附加 ORM / DAO 生态，不影响 JDBC 主链路
- `rediscala-1.8`
  - 原因：Scala 长尾客户端

### 第二批优先删除

这些模块属于长尾 Web 框架或应用服务器，通常可以整族移除：

- `helidon-4.3`
  - 原因：独立微服务框架，使用面窄
- `grizzly-2.3`
  - 原因：老旧服务器 / 网络栈
- `liberty-*`
  - 原因：企业应用服务器长尾支持
- `payara-5.2020`
  - 原因：企业应用服务器长尾支持
- `quarkus-resteasy-reactive-1.11`
  - 原因：专用框架支持，不属于当前主线
- `activej-http-6.0`
  - 原因：小众 HTTP 框架
- `apache-shenyu-2.4`
  - 原因：边缘网关生态
- `javalin-5.0` / `javalin-7.0`
  - 原因：轻量 Web 框架，长尾
- `ktor-2.0` / `ktor-3.0`
  - 原因：Kotlin Web 生态，非当前重点
- `zio-2.0` / `zio-http-3.0`
  - 原因：Scala / FP 长尾生态
- `undertow-1.4`
  - 原因：如果不保留多容器兼容，可整体移除
- `jetty-*`
  - 原因：如果仅保留 Spring / Tomcat 主线，Jetty 可整族移除
- `tomcat-7.0`
  - 原因：老版本容器兼容层，价值低于维护成本
- `servlet-2.2`
  - 原因：老 Java EE 兼容层

### 第三批评估后删除

这些模块会明显影响通用 HTTP 客户端 / reactive 出站 tracing，只有在明确不需要时才应删除：

- `async-http-client-*`
- `apache-httpclient-*`
- `okhttp-*`
- `jetty-httpclient-*`
- `vertx-http-client-*`
- `java-http-client`
- `google-http-client-1.19`
- `armeria`
- `jodd-http-4.2`

原因：

- 它们虽然也占据一定体积，但与“保留通用出站 HTTP tracing”目标直接相关
- 删除时应先确定实际业务中使用的 HTTP 客户端清单，再保留最少子集

### 当前建议保留

这些模块当前不应作为首批 jar 体积精简对象：

- `javaagent-tooling`
- `muzzle`
- `javaagent-bootstrap`
- `javaagent-extension-api`
- `executors`
- `internal-*`
- `opentelemetry-api-*` 兼容层
- `jdbc`
- `r2dbc`
- `runtime-telemetry`
- `oshi`
- `servlet-common`
- `spring-web*`
- `spring-webmvc*`
- `spring-webflux*`
- `netty`
- `reactor`
- `http-url-connection`
- `java-http-client`

原因：

- 这些模块要么是 agent 基础设施
- 要么是当前明确保留的核心能力：JDBC、runtime/system metrics、Spring 主线、通用出站 HTTP tracing

### 后续执行顺序建议

为了尽快获得体积收益，同时降低回归风险，建议按下列顺序分批执行：

1. `jaxrs`
2. `elasticsearch` + `opensearch`
3. `couchbase` + `cassandra` + `clickhouse`
4. `helidon` / `grizzly` / `liberty` / `payara` / `quarkus-resteasy-reactive` / `activej-http` / `apache-shenyu`
5. `graphql-java` / `geode` / `influxdb` / `twilio` / `spymemcached` / `nats` / `mybatis` / `rediscala`
6. 最后再评估各类 HTTP client / reactive client 是否还能继续收缩

执行原则：

- 先删整族，避免留下共用模块和测试残骸
- 每一批都同步清理：
  - `settings.gradle.kts`
  - `docs/supported-libraries.md`
  - `instrumentation-docs` 相关断言
  - 对应 testing / smoke test / 构建依赖
- 每一批删除后至少验证：
  - `./gradlew compileJava compileTestJava`
  - 受影响模块的 `test` 或 `check`

## 工作区附带变更

当前还存在一个与本次可编译状态无直接冲突的工作区改动：

- `.gitignore`
  - 新增 `.kotlin/`
  - 新增 `tmp/`

此项不影响编译结果。
