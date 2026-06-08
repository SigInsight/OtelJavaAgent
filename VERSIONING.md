# Buildable State Change List

本文档记录本次对 `opentelemetry-java-instrumentation` 的裁剪、清理和编译修复结果。

## 目标

- 移除一批非核心异步 / 并发 / 响应式模块及相关基准模块
- 清理构建、文档、测试中的残余引用
- 保证项目在当前仓库状态下可以通过全量编译

## 已移除的模块

### benchmark

- `benchmark-jfr-analyzer`

### async / concurrency / reactive

- `instrumentation/akka/akka-actor-2.3`
- `instrumentation/akka/akka-actor-forkjoin-2.5`
- `instrumentation/akka/akka-http-10.0`
- `instrumentation/pekko/pekko-actor-1.0`
- `instrumentation/pekko/pekko-http-1.0`

### 本轮最终从工作区清理掉的残余目录

- `instrumentation/failsafe-3.0`
- `instrumentation/hystrix-1.4`
- `instrumentation/guava-10.0`
- `instrumentation/kotlinx-coroutines`
- `instrumentation/scala-forkjoin-2.8`

说明：

- 上述目录中，`akka* / pekko* / benchmark-jfr-analyzer` 已经完成源码级移除。
- `failsafe / hystrix / guava / kotlinx-coroutines / scala-forkjoin` 在本次最终清理前仅剩未受 Git 管理的本地残留目录，已从工作区删除。
- Kotlin 支持本身未移除，仓库仍保留 Kotlin 相关构建与测试模块。

## 已同步的构建与引用清理

### settings / 构建入口

- `settings.gradle.kts`
  - 已移除被删除模块的 `include(...)`

### 下游依赖引用

- `instrumentation/ktor/ktor-2.0/javaagent/build.gradle.kts`
- `instrumentation/ktor/ktor-3.0/javaagent/build.gradle.kts`
- `instrumentation/cassandra/cassandra-3.0/javaagent/build.gradle.kts`

这些模块里对已删除 instrumentation 的项目依赖已清理。

## 已同步的文档与校验

- `docs/supported-libraries.md`
- `docs/instrumentation-list.yaml`
- `docs/contributing/documenting-instrumentation.md`
- `instrumentation-docs/src/test/java/io/opentelemetry/instrumentation/docs/auditors/SupportedLibrariesAuditorTest.java`
- `instrumentation-docs/src/test/java/io/opentelemetry/instrumentation/docs/auditors/SuppressionListAuditorTest.java`

这些文件中的已删除模块条目和相关断言已同步更新。

## 构建修复

### 1. Gradle precompiled script plugin 兼容性修复

为避免 Kotlin DSL accessor / precompiled script 在当前仓库状态下出现编译问题，做了以下源码级调整：

- `gradle-plugins/src/main/kotlin/io.opentelemetry.instrumentation.muzzle-check.gradle.kts`
  - 将 `tasks.jar`、`configurations.runtimeClasspath` 等 typed accessor 写法改为显式 API 调用
  - 例如改用 `tasks.named<Jar>("jar")`、`configurations.named("runtimeClasspath")`

- `gradle-plugins/src/main/kotlin/io.opentelemetry.instrumentation.muzzle-generation.gradle.kts`
  - 将 `sourceSets.main`、`configurations.runtimeClasspath` 等 typed accessor 写法改为显式 `SourceSetContainer` / `named(...)` 访问

目的：

- 避免 Kotlin DSL 生成不稳定 accessor
- 保持 `gradle-plugins:compileKotlin` 和根工程 clean 编译稳定

### 2. conventions 对 muzzle 内部类型的编译期解耦

- `conventions/src/main/kotlin/io.opentelemetry.instrumentation.base.gradle.kts`
  - 去除对 `io.opentelemetry.javaagent.muzzle.AcceptableVersions` 的直接依赖
  - 改为本地 `isStableVersion(version: String)` 判断

- `conventions/src/main/kotlin/otel.resolve-latest-dep-versions.gradle.kts`
  - 去除对 `AcceptableVersions` 和 `MuzzleExtension` 的直接编译期引用
  - 使用本地 `isStableVersion(...)`
  - 通过反射访问名为 `muzzle` 的 extension

目的：

- 降低 build logic 对 `muzzle` 实现细节的耦合
- 保证 `conventions:compileKotlin` 可通过

## 已验证结果

已验证通过：

- `./gradlew --no-daemon --console=plain -p gradle-plugins clean compileKotlin`
- `./gradlew --no-daemon --console=plain --no-build-cache --no-configuration-cache clean compileJava compileTestJava`

结论：

- 当前仓库状态可编译
- `gradle-plugins` / `conventions` / 根工程 clean 编译链路已打通

## 当前仍保留但不属于本次移除范围

- Kotlin 支持与 Kotlin 相关模块仍保留
- `instrumentation/opentelemetry-extension-kotlin-1.0`
- `instrumentation/spring/spring-data/spring-data-3.0/kotlin-testing`
- 与 Kotlin / coroutines 相关的注释、测试依赖、说明文字仍可能存在，这是预期状态

## 工作区附带变更

当前还存在一个与本次可编译状态无直接冲突的工作区改动：

- `.gitignore`
  - 新增 `.kotlin/`
  - 新增 `tmp/`

此项不影响编译结果。
