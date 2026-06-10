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

| 版本   | 主文件数 | 依赖                   | 原因                              |
|------|------|----------------------|---------------------------------|
| 1.4  | 4    | 1.0                  | 仅被 1.10/1.15/1.38 依赖，随它们一起删     |
| 1.10 | 32   | 1.0, 1.4             | 中间版本                            |
| 1.15 | 5    | 1.0, 1.4, 1.10       | 中间版本                            |
| 1.27 | 11   | 1.0, 1.4, 1.10, 1.15 | 被 1.42/1.47/1.50/1.56 依赖，随它们一起删 |
| 1.31 | 12   | 1.0, 1.10, 1.15      | 中间版本                            |
| 1.32 | 12   | 1.0, 1.10, 1.15      | 中间版本                            |
| 1.37 | 13   | 1.0, 1.10, 1.15      | 中间版本                            |
| 1.38 | 14   | 1.0, 1.4, 1.10       | 中间版本                            |
| 1.40 | 25   | 1.0, 1.10, 1.15      | 被 1.42/1.47/1.50/1.56 依赖，随它们一起删 |
| 1.42 | 9    | 1.0, 1.27, 1.40      | 非最新                             |
| 1.47 | 7    | 1.0, 1.27, 1.40      | 非最新                             |
| 1.50 | 10   | 1.0, 1.27, 1.40      | 非最新                             |
| 1.56 | 5    | 1.0, 1.27, 1.40      | 非最新                             |

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

## 第2步：同框架老版本清理（保守策略）

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

## 第3步：小众框架移除

- opencensus-shim（遗留兼容层）
- jodd-4.2（极少用）
- iceberg-1.8（数据湖垂直领域）
- dropwizard-metrics-4.0（老项目）

---

## 执行原则

- 每步之后跑 `./gradlew compileJava` 验证
- 先删目录 + settings.gradle.kts，再清理衍生引用
- docs/instrumentation-list.yaml 通过 `./gradlew :instrumentation-docs:run` 重生成
