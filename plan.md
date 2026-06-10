# Agent JAR 瘦身计划

## 当前状态

- 最终 JAR: ~20MB
- Bootstrap libs: 1.2MB
- Agent libs (relocated): 18.8MB
- OTel API 版本模块: 18 个 → 保留 5 个，删除 13 个

## 第1步：OTel API 版本收敛 [进行中]

### 依赖链分析

```
1.0 ← 基础（不可删，被 reactor/kotlin-ext/inst-api-1.14 直接依赖）
1.57 ← 独立（无依赖）
1.59 ← 依赖 1.0
1.52, 1.61 ← 仅 testing
```

### 删除清单（13 个版本）

| 版本 | 主文件数 | 依赖 | 原因 |
|------|---------|------|------|
| 1.4 | 4 | 1.0 | 仅被 1.10/1.15/1.38 依赖，随它们一起删 |
| 1.10 | 32 | 1.0, 1.4 | 中间版本 |
| 1.15 | 5 | 1.0, 1.4, 1.10 | 中间版本 |
| 1.27 | 11 | 1.0, 1.4, 1.10, 1.15 | 被 1.42/1.47/1.50/1.56 依赖，随它们一起删 |
| 1.31 | 12 | 1.0, 1.10, 1.15 | 中间版本 |
| 1.32 | 12 | 1.0, 1.10, 1.15 | 中间版本 |
| 1.37 | 13 | 1.0, 1.10, 1.15 | 中间版本 |
| 1.38 | 14 | 1.0, 1.4, 1.10 | 中间版本 |
| 1.40 | 25 | 1.0, 1.10, 1.15 | 被 1.42/1.47/1.50/1.56 依赖，随它们一起删 |
| 1.42 | 9 | 1.0, 1.27, 1.40 | 非最新 |
| 1.47 | 7 | 1.0, 1.27, 1.40 | 非最新 |
| 1.50 | 10 | 1.0, 1.27, 1.40 | 非最新 |
| 1.56 | 5 | 1.0, 1.27, 1.40 | 非最新 |

### 涉及文件

1. `settings.gradle.kts` — 删除 13 个 include 行
2. `opentelemetry-api-shaded-for-instrumenting/build.gradle.kts` — 删除对应的 shaded 配置 (v1_10, v1_15, v1_27, v1_31, v1_32, v1_37, v1_38, v1_40, v1_42, v1_47, v1_50, v1_56)
3. 删除 13 个目录
4. `docs/instrumentation-list.yaml` — 自动重生成
5. `.fossa.yml` — 清理残留 target

### 保留

- `opentelemetry-api-1.0` — 基础桥接模块
- `opentelemetry-api-1.52` — testing
- `opentelemetry-api-1.57` — 最新独立版本
- `opentelemetry-api-1.59` — 次新版本
- `opentelemetry-api-1.61` — testing

---

## 第2步：同框架老版本清理

| 框架 | 当前版本 | 保留 | 删除数 |
|------|---------|------|--------|
| Hibernate | 3.3, 4.0, 6.0, reactive-1.0 | 6.0 | 3 |
| MongoDB | 3.1, 3.7, 4.0, async-3.3 | 4.0 | 3 |
| Jedis | 1.4, 3.0, 4.0 | 4.0 | 2 |
| Lettuce | 4.0, 5.0, 5.1 | 5.1 | 2 |
| Redisson | 3.0, 3.17 | 3.17 | 1 |
| Apache HttpClient | 2.0, 4.0, 4.3, 5.0, 5.2 | 4.3, 5.2 | 3 |
| Netty | 3.8, 4.0, 4.1 | 4.1 | 2 |
| Spring WebMVC | 3.1, 6.0 | 6.0 | 1 |

---

## 第3步：Apache HttpClient / Netty 老版本清理

- 删除 apache-httpclient-2.0, 4.0, 5.0（保留 4.3, 5.2）
- 删除 netty-3.8, 4.0（保留 4.1）

---

## 第4步：小众框架移除

- oshi-5.0（系统指标，体积大）
- opencensus-shim（遗留兼容层）
- jodd-4.2（极少用）
- iceberg-1.8（数据湖垂直领域）
- dropwizard-metrics-4.0（老项目）

---

## 第5步：Reactive 版本合并

- rxjava: 保留 3.x，删除 2.0
- reactor: 保留 3.4，删除 3.1

---

## 执行原则

- 每步之后跑 `./gradlew compileJava` 验证
- 先删目录 + settings.gradle.kts，再清理衍生引用
- docs/instrumentation-list.yaml 通过 `./gradlew :instrumentation-docs:run` 重生成
