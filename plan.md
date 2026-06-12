# Agent JAR 瘦身计划

## 当前状态

- 最终 JAR: ~20MB
- Bootstrap libs: 1.2MB
- Agent libs (relocated): 18.8MB
- OTel API 版本模块: 18 个 → **全部保留**（第 1 步收敛已回滚，见下）

## 第1步：OTel API 版本收敛 [❌ 已回滚]

### 回滚原因

执行 `30f3527e "OTel API 版本收敛"` 删除 13 个中间版本后，
`./gradlew :instrumentation:executors:javaagent:test` 中 7 个 `CompletableFutureTest` **全部失败**，
asyncChild span 拿不到 parent context（traceId 不一致，parentSpanContext 为 null）。

二分定位过程：
- `e59b82c2`（fork base）`./gradlew :instrumentation:executors:javaagent:test --tests CompletableFutureTest` → PASS
- `de04b199` → PASS
- `e894a0b0` → PASS
- `30f3527e` → **FAIL** （肇事 commit）

### 根因

被删的 13 个中间版本不是叶子节点，而是 OTel API 桥接演进链路里的中间节点。
应用代码（包括 testing-common 的 `BaggageSpanProcessor` 路径）在跨线程
context 包装时，必须沿着 `应用 API → bridge 1.10 → bridge 1.27 → bridge 1.59 (agent latest)`
逐级路由。中间版本缺失后，`InstrumentationModule` 注册链断裂，
context 传播失败。

### 修复 commit

`a004d129 恢复 OTel API 多版本桥接，修复 CompletableFuture context 传播回归`

- 完全恢复 13 个 `instrumentation/opentelemetry-api/opentelemetry-api-1.X` 目录
- 恢复 `opentelemetry-api-shaded-for-instrumenting/build.gradle.kts`（12 个 v1_XX Deps configuration）
- 恢复 `javaagent/build.gradle.kts` 中 13 行 `baseJavaagentLibs(...)`
- 在 `settings.gradle.kts` 插回 13 行 `include(...)`

注：未恢复 `README.md` / `VERSIONING.md` / `.fossa.yml` 的相关行，因为后续 commit
有改动，整体覆盖会撤销其他工作。这几个文件单独同步即可。

### 教训 / 未来重试该收敛的前置条件

如果将来还想做 OTel API 版本收敛，应先：

1. 阅读 `opentelemetry-api-shaded-for-instrumenting/build.gradle.kts` 完整理解 14 个 v1_XX Deps 之间的依赖关系
2. 跑 `./gradlew :opentelemetry-api-shaded-for-instrumenting:dependencies` 看实际依赖拓扑
3. 每次只删一个"叶子节点"版本，删完立刻跑 `:instrumentation:executors:javaagent:test` 验证
4. 保留 commit 粒度小（每删一个版本一个 commit），便于二分回滚

### 当前保留（全部 18 个）

- `opentelemetry-api-1.0` — 基础桥接模块（不可删，被 reactor/kotlin-ext/inst-api-1.14 直接依赖）
- `opentelemetry-api-1.4` — 1.10/1.15/1.38 的中间依赖
- `opentelemetry-api-1.10` — 1.15/1.31/1.32/1.37/1.38 的中间依赖
- `opentelemetry-api-1.15` — 1.27/1.31/1.32/1.37 的中间依赖
- `opentelemetry-api-1.27` — 1.42/1.47/1.50/1.56 的中间依赖
- `opentelemetry-api-1.31` `1.32` `1.37` `1.38` — 中间版本
- `opentelemetry-api-1.40` — 1.42/1.47/1.50/1.56 的中间依赖
- `opentelemetry-api-1.42` `1.47` `1.50` `1.56` — 非最新但被链路依赖
- `opentelemetry-api-1.52` — testing
- `opentelemetry-api-1.57` — 独立版本
- `opentelemetry-api-1.59` — 依赖 1.0
- `opentelemetry-api-1.61` — testing

---

## 第2步：同框架老版本清理（保守策略）[✅ 已完成]

仅删除真正老旧的版本，保留多个近期版本保证兼容性。

| 框架                | 删除（极老）        | 保留（近期 + 共用依赖）                                                   |
|-------------------|---------------|-----------------------------------------------------------------|
| Hibernate         | 3.3           | 4.0, 6.0, reactive-1.0, common-3.3, procedure-call-4.3, testing |
| MongoDB           | 3.1:javaagent | 3.7, 4.0, async-3.3, 3.1:library, 3.1:testing, common:testing   |
| Jedis             | 1.4:javaagent | 3.0, 4.0, common-1.4                                            |
| Lettuce           | 4.0           | 5.0, 5.1, common:library                                        |
| Redisson          | 3.0           | 3.17, common-3.0                                                |
| Apache HttpClient | 2.0           | 4.0, 4.3, 5.0, 5.2                                              |
| Netty             | 3.8           | 4.0, 4.1, common-4.0, common                                    |

---

## 第3步：小众框架移除 [✅ 已完成]

- opencensus-shim（遗留兼容层）
- jodd-4.2（极少用）
- iceberg-1.8（数据湖垂直领域）
- dropwizard-metrics-4.0（老项目）

---

## 第4步：小众 Web/HTTP 模块移除 [✅ 已完成]

### 已删除

- vertx 全族（web、http-client、redis-client、rx-java、sql-client 共 11 个模块）
- spring-ws-2.0（SOAP 遗留协议）
- spring-cloud-gateway（webmvc-4.3 + common，仅 Spring Cloud 网关场景）
- okhttp-2.2（2014 年版本，okhttp 3.0 覆盖现代版本）
- async-http-client-1.8 + 1.9 + common-1.8（极老版本，保留 2.0）
- google-http-client-1.19（仅 Google API 生态）

### 保留（有重要测试依赖）

- **armeria-1.3**：虽然 Armeria 框架本身小众，但 `testing/dependencies-shaded-for-testing` 依赖 `armeria-junit5` 作为 HTTP 测试基础设施（WebClient、ServerExtension 等），几乎所有 HTTP 相关测试（servlet、spring-webmvc、spring-webflux、netty、reactor-netty、smoke-tests）都使用它。删除会导致测试体系崩溃。

---

## 第5步：移除云资源探测（AWS/GCP/Azure/CloudFoundry） [✅ 已完成]

### 移除的依赖

从 `javaagent-tooling/build.gradle.kts` 和 `spring-boot-starter/build.gradle.kts` 中移除：
- `opentelemetry-azure-resources`
- `opentelemetry-aws-resources`
- `opentelemetry-gcp-resources`
- `opentelemetry-cloudfoundry-resources`

从 `dependencyManagement/build.gradle.kts` 移除对应的版本约束。

### 代码变更

- `ResourceProviderPropertiesCustomizer.java` — 移除 12 个云 provider 的注册（Azure 5个、AWS 5个、GCP 1个、CloudFoundry 1个）
- `OpenTelemetryAutoConfigurationTest.java` — 更新测试断言，移除云 provider 类名

### 保留

- 本地资源探测（`instrumentation:resources:library`）：container.id、host.name/host.id/host.arch、process.pid/process.runtime.*、os.type/os.description
- `telemetry.distro.name` / `telemetry.distro.version` 属性

### 影响评估

- 丢失：AWS/GCP/Azure/CloudFoundry 的自动资源属性探测（cloud.provider、cloud.region、instance.id 等）
- 不影响：trace/metrics/logs 的收集和导出
- 不影响：本地资源属性（host、process、container、os）

---

## 第6步：移除 Prometheus Exporter + Protobuf [✅ 已完成]

### 变更

- 移除 `javaagent-tooling/build.gradle.kts` 中的 `opentelemetry-exporter-prometheus` 依赖
- `protobuf-java:4.34.0` 作为 Prometheus exporter 的传递依赖随之移除
- Prometheus exporter 通过 SPI 自动注册，移除依赖即自动不可用，无需额外代码修改

### 体积对比

| | 体积 |
|---|---|
| 移除前 | 18.5 MB |
| 移除后 | 16.3 MB |
| **减少** | **2.2 MB（11.9%）** |

### 影响评估

- `OTEL_METRICS_EXPORTER=otlp` → ✅ 正常工作
- `OTEL_METRICS_EXPORTER=prometheus` → ❌ 不可用（exporter 不存在）
- `OTEL_METRICS_EXPORTER=none` → ✅ 正常
- OTLP trace/metrics/logs 导出完全不受影响

---

## 第7步：移除非必要运行时依赖 [✅ 已完成]

### 移除的依赖

从 `javaagent-tooling/build.gradle.kts` 移除：
- `opentelemetry-exporter-zipkin`（含传递依赖 zipkin-reporter、zipkin-sender-okhttp3）
- `opentelemetry-sdk-extension-jaeger-remote-sampler`
- `opentelemetry-aws-xray-propagator`
- `opentelemetry-contrib-samplers`

从 `spring-boot-autoconfigure/build.gradle.kts` 移除 xray-propagator 和 zipkin 的 compileOnly/testImplementation。

从 `settings.gradle.kts` 移除 `zipkin-spring-boot-starter`。

从 `dependencyManagement/build.gradle.kts` 移除 xray-propagator、contrib-samplers 版本约束。

### 代码变更

- `RemappingUrlConnection.java` — 移除 AWS X-Ray shading 规则

### 保留（有测试依赖）

- **baggage-processor**：`testing-common` 的 `LibraryTestRunner` 使用 `BaggageSpanProcessor` 将 baggage 数据附加到 span 上用于测试验证，不可移除。

### 体积对比

| | 体积 |
|---|---|
| 移除前 | 16.3 MB |
| 移除后 | 15.8 MB |
| **减少** | **0.5 MB（3.0%）** |

### 性能影响

- 减少 SPI 自动发现组件数量，降低启动时 ServiceLoader 扫描开销
- 减少 ByteBuddy 注册的 exporter/propagator 数量

### 影响评估

- `OTEL_TRACES_EXPORTER=otlp` → ✅ 正常
- `OTEL_TRACES_EXPORTER=zipkin` → ❌ 不可用
- `OTEL_PROPAGATORS=xray` → ❌ 不可用
- OTLP trace/metrics/logs 导出完全不受影响

---

## 保留：声明式配置（Declarative Config）

- 依赖：Jackson-databind + SnakeYAML + declarative-config SDK
- **保留原因**：提供 `default_enabled + enabled 列表` 的全局插桩控制、方法级排除的结构化配置、URL 模板规则等高级能力，环境变量无法完全替代。虽然当前未使用，但未来复杂场景下可能需要。

## 保留：Kotlin 协程扩展（opentelemetry-extension-kotlin）

- 依赖：`opentelemetry-extension-kotlin`（传递引入 `kotlin-stdlib`）
- **保留原因**：
  - **零体积收益**：`kotlin-stdlib` 通过 OkIO 3.x → OkHttp → OTLP Exporter 链路已存在于运行时，即使移除 `opentelemetry-extension-kotlin`，kotlin-stdlib 仍然会被带入 JAR
  - **有破坏 Kotlin 用户 trace 链路的风险**：移除后 Kotlin 协程的 context 传播失效，导致 Kotlin/Ktor 用户的分布式 trace 断链
  - 关联模块 `instrumentation:opentelemetry-extension-kotlin-1.0` 提供桥接插桩，移除需同步处理

---

## 执行原则

- 每步之后跑 `./gradlew compileJava` 验证
- 先删目录 + settings.gradle.kts，再清理衍生引用
- docs/instrumentation-list.yaml 通过 `./gradlew :instrumentation-docs:run` 重生成
