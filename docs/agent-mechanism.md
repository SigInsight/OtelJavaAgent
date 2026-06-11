# OpenTelemetry Java Agent 机制详解

以 `http-url-connection` 模块为例，从全局架构到 JVM 启动到字节码注入的完整链路。

---

## 全局架构概览

```
┌──────────────────────────────────────────────────────────────────┐
│                      OpenTelemetry Java Agent                    │
│                                                                  │
│  JVM 启动                                                        │
│    └─ premain()                                                  │
│        └─ OpenTelemetryAgent                                     │
│            ├─ installBootstrapJar()                               │
│            │   把 Agent API 注入 Bootstrap CL                     │
│            └─ AgentInitializer                                    │
│                ├─ AgentClassLoader (加载 inst/ 目录)              │
│                └─ AgentStarterImpl                                │
│                    ├─ installTransformers() (ASM 预处理)           │
│                    └─ AgentInstaller                              │
│                        ├─ 配置 ByteBuddy 引擎                     │
│                        ├─ installOpenTelemetrySdk()               │
│                        │   → 读取 OTEL_* 环境变量                  │
│                        │   → 初始化 Tracer + OTLP Exporter        │
│                        ├─ configureIgnoredTypes()                 │
│                        │   → 排除 Agent 自身、配置排除的类         │
│                        ├─ 加载 80 个 InstrumentationModule (SPI)  │
│                        │   每个 Module 注册：                      │
│                        │     typeMatcher → 匹配什么类             │
│                        │     advice → 注入什么逻辑                │
│                        ├─ optimize() 合并优化 matcher              │
│                        └─ installOn(inst) 安装到 JVM              │
│                                                                  │
│  运行时                                                           │
│    每个类加载时 →                                                 │
│      ByteBuddy 检查 80 个 typeMatcher                             │
│        ├─ 不匹配 → 跳过（绝大多数类）                             │
│        └─ 匹配 → Muzzle 校验 → 通过 → 注入 Advice 字节码          │
│              方法调用时自动执行：                                  │
│                OnMethodEnter → 创建/恢复 span                     │
│                OnMethodExit  → 记录属性、结束 span                 │
│                                                                  │
│  导出                                                             │
│    Span → BatchSpanProcessor → OTLP Exporter → OkHttp → Collector│
└──────────────────────────────────────────────────────────────────┘
```

---

## 模块架构：两个核心 API 的分工

Agent 的代码分为两层 API，各司其职：

```
┌─────────────────────────────────────────────────┐
│            javaagent-extension-api               │  ← Agent 扩展层
│  InstrumentationModule / TypeInstrumentation     │     「怎么注入字节码」
│  AgentElementMatchers / IgnoredTypesConfigurer   │
├─────────────────────────────────────────────────┤
│             instrumentation-api                  │  ← 遥测采集层
│  Instrumenter / AttributesExtractor              │     「怎么采集遥测」
│  SpanStatusExtractor / OperationListener         │
│  VirtualField / Semantic Conventions             │
├─────────────────────────────────────────────────┤
│            OpenTelemetry SDK (外部)               │  ← 底层 SDK
└─────────────────────────────────────────────────┘
```

### 一句话定位

| 模块 | 一句话定位 |
|------|-----------|
| **`instrumentation-api`** | **「怎么采集遥测数据」**——提供 `Instrumenter`、`AttributesExtractor` 等稳定 API，定义 span/metrics 的创建、属性提取、上下文传播的通用范式 |
| **`javaagent-extension-api`** | **「怎么写 Agent 扩展」**——提供 `InstrumentationModule`、`TypeInstrumentation`、ByteBuddy matcher 等 SPI，定义如何向 Java Agent 注册自定义字节码插桩 |

### 核心差异

| 维度 | `instrumentation-api` | `javaagent-extension-api` |
|------|----------------------|--------------------------|
| **关注点** | 遥测数据的**采集逻辑**（span、属性、状态、metrics） | 字节码插桩的**注册与匹配**（哪些类、怎么改） |
| **核心类** | `Instrumenter<REQ,RES>`, `AttributesExtractor`, `OperationListener` | `InstrumentationModule`, `TypeInstrumentation`, `AgentElementMatchers` |
| **依赖 ByteBuddy** | ❌ 不依赖 | ✅ 强依赖 |
| **稳定性** | **稳定 API**（`otel.stable=true`），承诺向后兼容 | **非稳定**，随 Agent 版本迭代可能变更 |
| **目标用户** | 库作者可内嵌到自己库中；所有插桩模块共用 | **仅 Agent 扩展开发者**——写自定义字节码插桩 |
| **与 Java Agent 耦合** | **无关**——不绑定 javaagent 机制 | **强耦合**——围绕 classloader 隔离、muzzle、helper 注入 |

`javaagent-extension-api` **向下依赖** `instrumentation-api`。典型工作流：

1. 用 `javaagent-extension-api` 的 `InstrumentationModule` 注册你想拦截哪些类（`TypeInstrumentation` + ByteBuddy matcher）
2. 在 advice 类中调用 `instrumentation-api` 的 `Instrumenter.start()` / `.end()` 来实际创建 span 和记录遥测数据

**一个是「手术刀的握柄」，一个是「手术刀的刀刃」。**

### 包结构一览

**instrumentation-api：**

| 包路径 | 用途 |
|--------|------|
| `io.opentelemetry.instrumentation.api.instrumenter` | `Instrumenter` 核心框架、`AttributesExtractor`、`OperationListener` |
| `io.opentelemetry.instrumentation.api.semconv` | 语义约定实现（HTTP、Network、URL 等） |
| `io.opentelemetry.instrumentation.api.util` | `VirtualField` 等工具类 |
| `io.opentelemetry.instrumentation.api.internal` | 内部实现（不稳定） |

**javaagent-extension-api：**

| 包路径 | 用途 |
|--------|------|
| `io.opentelemetry.javaagent.extension.instrumentation` | `InstrumentationModule`、`TypeInstrumentation`、`TypeTransformer` |
| `io.opentelemetry.javaagent.extension.matcher` | `AgentElementMatchers` 安全的 ByteBuddy 匹配器 |
| `io.opentelemetry.javaagent.extension.ignore` | `IgnoredTypesConfigurer` 控制插桩范围 |
| `io.opentelemetry.javaagent.bootstrap` | Bootstrap 类加载器工具（`CallDepth`、`Java8BytecodeBridge`） |
| `io.opentelemetry.javaagent.bootstrap.http` | HTTP 响应定制化 SPI |

---

## 第一层：JVM 怎么加载 Agent

启动命令：

```bash
java -javaagent:/opentelemetry-javaagent.jar -jar app.jar
```

JVM 在执行 `app.jar` 的 `main()` **之前**，先执行 Agent 的入口。入口类在 JAR manifest 中声明：

```
Premain-Class: io.opentelemetry.javaagent.OpenTelemetryAgent
Can-Retransform-Classes: true
```

`OpenTelemetryAgent.premain()` 做三件事：

```java
// javaagent-bootstrap/.../OpenTelemetryAgent.java
public static void premain(String agentArgs, Instrumentation inst) {
    File javaagentFile = installBootstrapJar(inst);     // ①
    InstrumentationHolder.setInstrumentation(inst);      // ②
    AgentInitializer.initialize(inst, javaagentFile, true, agentArgs);  // ③
}
```

**① `installBootstrapJar`**：`inst.appendToBootstrapClassLoaderSearch(agentJar)` 把 Agent JAR 加入 Bootstrap ClassLoader 搜索路径。这样 `java.lang.Thread` 等核心类就能访问 Agent 的 API（如 `io.opentelemetry.context.Context`）。

**② `InstrumentationHolder.set`**：保存 `Instrumentation` 引用供后续使用。

**③ `AgentInitializer.initialize`**：进入真正的初始化。

---

## 第二层：ClassLoader 隔离

Agent 面临的核心问题：Agent 自己的依赖（OTel SDK 1.62、OkHttp）和用户应用的依赖（OTel SDK 1.28、Spring）可能冲突。

解决方案——三级隔离：

```
Bootstrap ClassLoader
  ← Agent 公共 API（opentelemetry-api、instrumentation-api、注解）
  ← 应用代码和 Agent 都能看到

AgentClassLoader（自定义，加载 inst/ 目录）
  ← Agent 内部实现（ByteBuddy、OTel SDK、Exporter）
  ← 应用代码看不到，包名被重定位

ExtensionClassLoader
  ← 用户提供的扩展 JAR
  ← 隔离加载，防止干扰

Application ClassLoader
  ← 用户的应用代码和第三方库
  ← 和 Agent 内部实现完全隔离
```

体现在 JAR 结构中：

```
opentelemetry-javaagent.jar
├── io/opentelemetry/api/...                ← Bootstrap 层，App 可见
├── io/opentelemetry/instrumentation/api/   ← Bootstrap 层，App 可见
├── inst/                                   ← Agent 内部层
│   ├── io/opentelemetry/javaagent/shaded/io/opentelemetry/sdk/...
│   ├── io/opentelemetry/javaagent/shaded/okhttp3/...
│   └── net/bytebuddy/...
└── META-INF/services/...
```

`AgentClassLoader` 接收 `"inst"` 作为参数，只从 JAR 内的 `inst/` 子目录加载类。`.class` 文件被重命名为 `.classdata`，防止被 App ClassLoader 意外加载。

---

## 第三层：初始化流程

```
AgentInitializer.initialize()
  │
  ├─ setSystemProperties()         解析 -javaagent 参数中的 key=value
  │
  ├─ createAgentClassLoader("inst") 创建 AgentClassLoader
  │
  ├─ createAgentStarter()           反射创建 AgentStarterImpl
  │   ↑ 为什么用反射？AgentStarterImpl 在 inst/ 里（AgentClassLoader），
  │   ↑ AgentInitializer 在 Bootstrap CL 里，跨 ClassLoader 必须反射
  │
  └─ agentStarter.start()
      │
      ├─ installTransformers()      用原始 ASM 修改几个 JDK/Agent 内部类
      │   ├── InetAddressClassFileTransformer
      │   └── AgentBuilderDefaultClassFileTransformer
      │
      ├─ createExtensionClassLoader() 创建扩展类加载器
      │
      └─ AgentInstaller.installBytebuddyAgent()  ← 核心！
```

---

## 第四层：ByteBuddy 插桩引擎安装

`AgentInstaller.installBytebuddyAgent()` 是 Agent 最核心的方法：

```java
// 简化的核心流程
void installBytebuddyAgent(Instrumentation inst, ClassLoader extensionClassLoader) {

    // ① 配置 ByteBuddy 引擎
    AgentBuilder agentBuilder = new AgentBuilder.Default(new ByteBuddy()
        .with(MethodGraph.Compiler.ForDeclaredMethods.INSTANCE)  // 只看声明的方法，不遍历继承链
        .with(InstrumentedType.Factory.Default.FROZEN))          // 冻结类型
        .with(TypeStrategy.Default.DECORATE)       // 装饰模式，不修改原始类结构
        .disableClassFormatChanges()                // 禁止修改类格式
        .with(RedefinitionStrategy.RETRANSFORMATION); // 支持重新转换已加载的类

    // ② 安装"早期插桩"（Virtual Fields 注入）
    installEarlyInstrumentation(agentBuilder, inst);
    //   → 只注入 Virtual Field，不能改已加载的类

    // ③ 初始化 OpenTelemetry SDK
    AutoConfiguredOpenTelemetrySdk sdk = installOpenTelemetrySdk(extensionClassLoader);
    //   → 读取 OTEL_* 环境变量，初始化 TracerProvider + Exporter

    // ④ 配置忽略规则
    agentBuilder = configureIgnoredTypes(config, extensionClassLoader, agentBuilder);
    //   → 忽略 Agent 自身类、exclude_classes 配置的类

    // ⑤ 加载所有插桩模块（80 个 InstrumentationModule）
    for (AgentExtension ext : ServiceLoader.load(AgentExtension.class, extensionClassLoader)) {
        agentBuilder = ext.extend(agentBuilder, sdkConfig);
    }

    // ⑥ 优化并安装到 JVM
    agentBuilder = AgentBuilderUtil.optimize(agentBuilder);
    agentBuilder.installOn(inst);
    //   → 从此刻起，每个类加载都会经过 ByteBuddy 的 typeMatcher 检查
}
```

---

## 第五层：SPI 发现——InstrumentationModule 怎么被加载

### 发现链

```
AgentInstaller
  └─ ServiceLoader.load(AgentExtension.class, extensionClassLoader)
      └─ InstrumentationLoader  (@AutoService(AgentExtension.class) 注册)
          └─ ServiceLoader.load(InstrumentationModule.class)
              └─ 发现所有 80 个 InstrumentationModule 实现
```

`InstrumentationLoader` 是 `AgentExtension` 的实现，负责发现并安装所有插桩模块：

```java
@AutoService(AgentExtension.class)
public class InstrumentationLoader implements AgentExtension {
    @Override
    public AgentBuilder extend(AgentBuilder builder, ConfigProperties config) {
        for (InstrumentationModule module : loadOrdered(InstrumentationModule.class)) {
            builder = InstrumentationModuleInstaller.install(builder, module, config);
        }
        return builder;
    }
}
```

### SPI 注册机制

每个 `InstrumentationModule` 实现用 `@AutoService` 注解，编译时自动生成：

```
META-INF/services/
  io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule
    → io.opentelemetry.javaagent.instrumentation.httpurlconnection.HttpUrlConnectionInstrumentationModule
    → io.opentelemetry.javaagent.instrumentation.okhttp.OkHttp3InstrumentationModule
    → ... (80 个模块)
```

这些 SPI 文件来自**外部依赖 JAR 包内部**，不是本项目代码生成的。每个插桩模块被打包成独立的 JAR（如 `http-url-connection-javaagent-2.29.0-SNAPSHOT.jar`），JAR 内自带 SPI 注册文件。Shadow JAR 构建时把所有依赖 JAR 解压合并，SPI 文件也被合并。

---

## 第六层：具体例子——http-url-connection 完整链路

### 6.1 InstrumentationModule 定义

```java
// http-url-connection/.../HttpUrlConnectionInstrumentationModule.java
@AutoService({InstrumentationModule.class, EarlyInstrumentationModule.class})
public class HttpUrlConnectionInstrumentationModule extends InstrumentationModule
    implements EarlyInstrumentationModule {

  public HttpUrlConnectionInstrumentationModule() {
    super("http-url-connection");
    // ↑ 模块名，对应配置项 otel.instrumentation.http-url-connection.enabled
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpUrlConnectionInstrumentation());
  }
}
```

这个类同时实现了 `EarlyInstrumentationModule`，意味着它会在 `AgentInstaller.installEarlyInstrumentation()` 阶段就安装 Virtual Field（`HTTP_URL_STATE`），而不是等到主安装阶段。

### 6.2 TypeInstrumentation——匹配什么类、改什么方法

```java
// http-url-connection/.../HttpUrlConnectionInstrumentation.java
class HttpUrlConnectionInstrumentation implements TypeInstrumentation {

  // ① 哪些类需要被插桩
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("java.net.")
        .or(nameStartsWith("sun.net"))
        .or(named("weblogic.net.http.HttpURLConnection"))   // WebLogic 特殊处理
        .and(not(named("sun.net.www.protocol.https.HttpsURLConnectionImpl"))) // 排除简单委托类
        .and(extendsClass(named("java.net.HttpURLConnection"))); // 必须继承 HttpURLConnection
  }

  // ② 对匹配到的类，改哪些方法
  @Override
  public void transform(TypeTransformer transformer) {
    // 对 connect/getOutputStream/getInputStream/plainConnect 注入 HttpUrlConnectionAdvice
    transformer.applyAdviceToMethod(
        isPublic().and(namedOneOf("connect", "getOutputStream", "getInputStream"))
            .or(isProtected().and(named("plainConnect"))),
        getClass().getName() + "$HttpUrlConnectionAdvice");

    // 对 getResponseCode 注入 GetResponseCodeAdvice
    transformer.applyAdviceToMethod(
        isPublic().and(named("getResponseCode")),
        getClass().getName() + "$GetResponseCodeAdvice");
  }
}
```

### 6.3 Advice——注入的字节码逻辑

这是最复杂的部分。`HttpURLConnection` 的特殊性在于：一个 HTTP 请求可能跨越多个方法调用（先 `connect()`，再 `getInputStream()`），所以需要用 **Virtual Field** 在方法之间保持状态。

#### Virtual Field——跨方法的"附加字段"

```java
// HttpUrlConnectionSingletons.java
public static final VirtualField<HttpURLConnection, HttpUrlState> HTTP_URL_STATE =
    VirtualField.find(HttpURLConnection.class, HttpUrlState.class);
```

Virtual Field 是 Agent 给已有类"注入"额外字段的机制。`VirtualField.find(HttpURLConnection.class, HttpUrlState.class)` 相当于给 `HttpURLConnection` 添加了一个类型为 `HttpUrlState` 的隐藏字段，不需要修改 `HttpURLConnection` 的源码。

#### HttpUrlState——请求状态

```java
// HttpUrlState.java
public class HttpUrlState {
  public Context context;    // OTel Context，携带当前 span
  public boolean finished;   // 请求是否已结束
  public int statusCode = 0; // HTTP 状态码

  public HttpUrlState(Context context) {
    this.context = context;
  }

  public void finish() {
    finished = true;
    context = null;  // 清理避免内存泄漏
  }
}
```

#### 主 Advice——HttpUrlConnectionAdvice

```java
public static class HttpUrlConnectionAdvice {

  // AdviceScope 封装了一次方法调用的上下文
  public static class AdviceScope {
    private final CallDepth callDepth;
    private final HttpUrlState httpUrlState;
    private final Scope scope;

    public static AdviceScope start(CallDepth callDepth, HttpURLConnection connection) {
      // ① 防止递归：connect() 可能内部调用 getInputStream()，避免重复创建 span
      if (callDepth.getAndIncrement() > 0) {
        return new AdviceScope(callDepth, null, null);
      }

      // ② 检查是否应该创建 span（sampling 决策）
      Context parentContext = Context.current();
      if (!instrumenter().shouldStart(parentContext, connection)) {
        return new AdviceScope(callDepth, null, null);
      }

      // ③ 从 Virtual Field 取出已有的状态
      HttpUrlState httpUrlState = HTTP_URL_STATE.get(connection);

      if (httpUrlState != null) {
        if (!httpUrlState.finished) {
          // 请求还在进行中，复用已有的 span
          return new AdviceScope(callDepth, httpUrlState, httpUrlState.context.makeCurrent());
        }
        return new AdviceScope(callDepth, httpUrlState, null);
      }

      // ④ 创建新的 span
      Context context = instrumenter().start(parentContext, connection);
      httpUrlState = new HttpUrlState(context);
      HTTP_URL_STATE.set(connection, httpUrlState);  // 存入 Virtual Field
      return new AdviceScope(callDepth, httpUrlState, context.makeCurrent());
    }

    public void end(HttpURLConnection connection, int responseCode,
                    Throwable throwable, String methodName) {
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      callDepth.getAndIncrement();  // 防止 end() 内的调用再次触发
      try {
        scope.close();

        if (throwable != null) {
          if (responseCode >= 400) {
            // HttpURLConnection 对 4xx/5xx 不必要地抛异常，不记录为 span 错误
            instrumenter().end(httpUrlState.context, connection, responseCode, null);
          } else {
            instrumenter().end(httpUrlState.context, connection, responseCode, throwable);
          }
          httpUrlState.finish();
        } else if (methodName.equals("getInputStream") && responseCode > 0) {
          instrumenter().end(httpUrlState.context, connection, responseCode, null);
          httpUrlState.finish();
        }
      } finally {
        callDepth.decrementAndGet();
      }
    }
  }

  // 注入到方法入口
  @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
  public static AdviceScope methodEnter(@Advice.This HttpURLConnection connection) {
    CallDepth callDepth = CallDepth.forClass(HttpURLConnection.class);
    return AdviceScope.start(callDepth, connection);
  }

  // 注入到方法出口
  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
  public static void methodExit(
      @Advice.This HttpURLConnection connection,
      @Advice.FieldValue("responseCode") int responseCode,    // 读取原类的字段
      @Advice.Thrown Throwable throwable,
      @Advice.Origin("#m") String methodName,                  // 获取方法名
      @Advice.Enter AdviceScope adviceScope) {                 // 接收 OnMethodEnter 的返回值
    adviceScope.end(connection, responseCode, throwable, methodName);
  }
}
```

#### 辅助 Advice——GetResponseCodeAdvice

```java
public static class GetResponseCodeAdvice {

  @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
  public static void methodExit(
      @Advice.This HttpURLConnection connection,
      @Advice.Return int returnValue) {
    // 仅仅把 responseCode 存到 Virtual Field 中，供后续 HttpUrlConnectionAdvice 使用
    HttpUrlState httpUrlState = HTTP_URL_STATE.get(connection);
    if (httpUrlState != null) {
      httpUrlState.statusCode = returnValue;
    }
  }
}
```

### 6.4 Instrumenter——span 的创建和结束

`HttpUrlConnectionSingletons.instrumenter()` 是 `Instrumenter<HttpURLConnection, Integer>` 实例，封装了 span 生命周期：

```
instrumenter().shouldStart(parentContext, connection)  // sampling 决策
instrumenter().start(parentContext, connection)         // 创建 span，返回新 Context
instrumenter().end(context, connection, responseCode, throwable)  // 结束 span
```

`Instrumenter` 内部会：
1. 提取 HTTP 属性（method、url、host 等）通过 `HttpUrlHttpAttributesGetter`
2. 应用 SEMCONV 语义约定（`http.request.method`、`url.full`、`server.address` 等）
3. 创建 span 并关联到当前 trace

### 6.5 运行时效果

用户代码不变：

```java
URL url = new URL("http://10.0.0.3:4318/v1/traces");
HttpURLConnection conn = (HttpURLConnection) url.openConnection();
conn.setRequestMethod("POST");
os = conn.getOutputStream();  // ← 触发 HttpUrlConnectionAdvice.OnMethodEnter
                               //   → 创建 span，存入 VirtualField
                               // ← 触发 HttpUrlConnectionAdvice.OnMethodExit
                               //   → 请求尚未结束，不结束 span
int code = conn.getResponseCode(); // ← 触发 GetResponseCodeAdvice.OnMethodExit
                                   //   → 把 statusCode=200 存入 VirtualField
is = conn.getInputStream();   // ← 触发 HttpUrlConnectionAdvice.OnMethodEnter
                               //   → 从 VirtualField 取出已有 span，makeCurrent
                               // ← 触发 HttpUrlConnectionAdvice.OnMethodExit
                               //   → methodName="getInputStream", responseCode=200
                               //   → 调用 instrumenter().end() 结束 span
```

---

## 第七层：关键机制总结

### CallDepth——防止递归

`connect()` 内部可能调用 `getInputStream()`，`getInputStream()` 又会触发 Advice。`CallDepth.forClass(HttpURLConnection.class)` 用 ThreadLocal 计数器确保只有最外层调用创建 span：

```
第一次进入（connect）:  callDepth=0→1, 创建 span
  内部调用（getInputStream）: callDepth=1→2, 跳过，不创建 span
  内部退出:             callDepth=2→1
第一次退出（connect）:   callDepth=1→0, 但请求未结束，不结束 span

后续外部调用（getInputStream）: callDepth=0→1, 从 VirtualField 取出已有 span
后续退出:                      callDepth=1→0, 请求结束，结束 span
```

### Virtual Field——跨方法状态传递

普通方法插桩只能处理"一个方法"的范围。但 HTTP 请求跨越 `connect()` → `getOutputStream()` → `getInputStream()` 多个方法。

Virtual Field 机制让 Agent 可以在不修改 `HttpURLConnection` 源码的情况下，给它的实例附加额外数据（`HttpUrlState`），实现跨方法的状态传递。

### Advice inline=false

`@Advice.OnMethodEnter(suppress = Throwable.class, inline = false)` 中的 `inline = false` 意味着 Advice 代码不会被内联到目标方法中，而是作为独立的方法调用。这样减少了目标类的字节码膨胀，但增加了一次方法调用开销。

---

## 第八层：Muzzle——编译期 + 运行时的版本兼容性校验框架

### 8.1 解决什么问题

Agent 的插桩代码（Advice）引用了目标库的特定类、方法、字段。但用户实际运行时，目标库的版本可能不同——也许某个方法在旧版本不存在，也许类被重命名了。如果盲目注入字节码，运行时会抛 `NoSuchMethodError` / `NoClassDefFoundError`。

**Muzzle 的作用就是：在注入字节码之前，先验证目标类路径上是否存在 Advice 所引用的所有类/方法/字段。如果不存在，就安全跳过这个插桩模块。**

### 8.2 两阶段工作流程

```
┌─────────────────────────────────────────────────────────┐
│ 阶段一：编译期（MuzzleCodeGenerator + ReferenceCollector）│
│                                                         │
│  编译每个 :instrumentation:X:javaagent 模块时            │
│    ① 扫描 Advice 类的字节码（ASM）                       │
│    ② 收集所有对外部类的引用（类、方法、字段）              │
│    ③ 剪枝：去掉 Agent 内部类的引用，只保留目标库引用      │
│    ④ 把引用清单编码成字节码，注入到 InstrumentationModule │
│       → getMuzzleReferences() 返回 Map<String, ClassRef>│
│       → getMuzzleHelperClassNames() 返回 List<String>   │
│                                                         │
│  同步做 muzzle {} 校验：                                  │
│    pass → 下载指定版本范围的目标库，验证引用全部匹配       │
│    fail → 下载指定版本范围的目标库，验证引用确实不匹配     │
├─────────────────────────────────────────────────────────┤
│ 阶段二：运行时（ReferenceMatcher + MuzzleMatcher）        │
│                                                         │
│  ByteBuddy 准备对某个类注入字节码时                       │
│    ① MuzzleMatcher.matches() 被调用                      │
│    ② 取出编译期预生成的引用清单                           │
│    ③ 用 TypePool 在当前 ClassLoader 中查找每个引用        │
│    ④ 全部匹配 → 允许注入                                 │
│       有不匹配 → 安全跳过，打印 debug 日志                │
│    ⑤ 结果按 ClassLoader 缓存，不重复检查                 │
└─────────────────────────────────────────────────────────┘
```

### 8.3 具体例子：Netty 4.1

以 `instrumentation/netty/netty-4.1/javaagent/build.gradle.kts` 为例：

```kotlin
muzzle {
  // ✅ 指令1：netty-codec-http 4.1.0 ~ 5.0.0 应该全部通过
  pass {
    group.set("io.netty")
    module.set("netty-codec-http")
    versions.set("[4.1.0.Final,5.0.0)")  // Maven 版本范围
    assertInverse.set(true)               // 还要验证范围外版本确实失败
  }

  // ❌ 指令3：旧包名 netty（3.x）必须失败
  fail {
    group.set("io.netty")
    module.set("netty")
    versions.set("[,]")  // 所有版本
  }
}
```

**编译期发生了什么：**

1. `MuzzleCodeGenerator` 扫描 Netty 4.1 的 Advice 类字节码
2. 发现 Advice 引用了 `io.netty.handler.codec.http.HttpRequest`、`io.netty.channel.Channel` 等 Netty 4.1 的类和方法
3. 生成引用清单，注入到 `Netty41InstrumentationModule` 的 `getMuzzleReferences()` 方法中
4. 根据 `pass` 指令，Gradle 自动下载 `netty-codec-http:4.1.0.Final`、`4.1.50.Final`、...、最新版，逐个验证引用全部能解析
5. 根据 `fail` 指令，下载旧包名 `netty:3.x`，验证引用确实**不能**解析（类名完全不同）

**运行时发生了什么：**

用户应用使用 Netty 4.1.68 → 所有引用匹配 → 允许注入

用户应用使用 Netty 3.x（旧包名 `org.jboss.netty`） → 引用不匹配 → 安全跳过

### 8.4 编译期引用收集的核心代码

`ReferenceCollector.collectReferencesFromAdvice()` 用 ASM 扫描 Advice 类的字节码：

```java
// ReferenceCollector.java (编译期运行)
public void collectReferencesFromAdvice(String adviceClassName) {
    Queue<String> instrumentationQueue = new ArrayDeque<>(singleton(adviceClassName));
    boolean isAdviceClass = true;

    while (!instrumentationQueue.isEmpty()) {
        String visitedClassName = instrumentationQueue.remove();
        // 用 ASM 读取 .class 文件
        ReferenceCollectingClassVisitor cv =
            new ReferenceCollectingClassVisitor(helperClassPredicate, isAdviceClass);
        ClassReader reader = new ClassReader(in);
        reader.accept(cv, ClassReader.SKIP_FRAMES);

        // 从字节码中提取对其他类的引用
        for (Map.Entry<String, ClassRef> entry : cv.getReferences().entrySet()) {
            String refClassName = entry.getKey();
            // 如果引用的是 Agent 内部类，继续递归扫描
            if (helperClassPredicate.isHelperClass(refClassName)) {
                instrumentationQueue.add(refClassName);
            }
            addReference(refClassName, entry.getValue());
        }
    }
}
```

`ReferenceCollectingClassVisitor` 在 ASM 层面拦截所有 `visitMethodInsn`、`visitFieldInsn`、`visitTypeInsn` 等，记录下 Advice 代码引用了哪些外部类/方法/字段。

### 8.5 运行时校验的核心代码

`ReferenceMatcher.matches()` 用 ByteBuddy 的 TypePool 做运行时检查：

```java
// ReferenceMatcher.java (运行时)
public boolean matches(ClassLoader loader) {
    TypePool typePool = createTypePool(loader);  // 基于 classLoader 创建类型池
    for (ClassRef reference : references.values()) {  // 遍历编译期收集的引用
        if (!checkMatch(reference, typePool, loader).isEmpty()) {
            return false;  // 任何一个不匹配就跳过
        }
    }
    return true;  // 全部匹配，允许注入
}

// 检查单个引用
private List<Mismatch> checkMatch(ClassRef reference, TypePool typePool, ClassLoader loader) {
    TypePool.Resolution resolution = typePool.describe(reference.getClassName());
    if (!resolution.isResolved()) {
        return singletonList(new Mismatch.MissingClass(reference));  // 类不存在
    }
    return checkThirdPartyTypeMatch(reference, resolution.resolve());  // 检查方法/字段
}
```

### 8.6 Muzzle Gradle 插件调用链

`muzzle {}` DSL 不是 Gradle 内置的，而是项目自定义的。完整调用链如下：

```
① Gradle Convention 插件
   conventions/src/main/kotlin/io.opentelemetry.instrumentation.javaagent-instrumentation.gradle.kts
   apply(plugin = "io.opentelemetry.instrumentation.muzzle-generation")
        │
        ▼
② Muzzle Generation 插件（定义 muzzle DSL + ByteBuddy 编译任务）
   gradle-plugins/src/main/kotlin/io.opentelemetry.instrumentation.muzzle-generation.gradle.kts
        │
        ├── 注册 codegen 配置（包含 muzzle 模块本身）
        ├── 创建 ByteBuddySimpleTask（编译后自动运行）
        │     discoverySet = codegen  ← 告诉 ByteBuddy 从 codegen 配置找 Plugin
        │
        └── 注册 muzzle {} DSL 扩展
              gradle-plugins/src/main/kotlin/.../MuzzleExtension.kt
              定义 pass {} 和 fail {} 方法
              │
              ▼
③ ByteBuddy 编译任务执行时
   在 classpath 中发现 MuzzleCodeGenerationPlugin（实现了 Plugin 接口）
   自动对每个编译产物 .class 调用：
        │
        ▼
④ MuzzleCodeGenerationPlugin.matches()
   检查这个类是不是 InstrumentationModule 的非抽象子类
   → 是：调用 apply()
        │
        ▼
⑤ MuzzleCodeGenerationPlugin.apply()
   return builder.visit(new MuzzleCodeGenerator(classLoader));
   把 MuzzleCodeGenerator（ASM Visitor）挂到字节码处理管线上
        │
        ▼
⑥ MuzzleCodeGenerator.visitEnd()
   ⑥a: 实例化 InstrumentationModule，收集所有 Advice 类名
   ⑥b: 用 ReferenceCollector 扫描 Advice 字节码，收集外部引用
   ⑥c: 用 ASM 直接生成 4 个方法的字节码，注入到 .class 文件中
        - getMuzzleReferences()
        - getMuzzleHelperClassNames()
        - registerMuzzleVirtualFields()
        - getMuzzleUseIsolatedHelperClasses()
   ⑥d: 给类添加 InstrumentationModuleMuzzle 接口
```

**关键代码：**

```kotlin
// MuzzleExtension.kt — 定义 pass/fail DSL
abstract class MuzzleExtension {
    fun pass(action: Action<MuzzleDirective>) {
        val pass = objectFactory.newInstance(MuzzleDirective::class.java)
        action.execute(pass)
        pass.assertPass.set(true)   // 标记为"应该通过"
        directives.add(pass)
    }

    fun fail(action: Action<MuzzleDirective>) {
        val fail = objectFactory.newInstance(MuzzleDirective::class.java)
        action.execute(fail)
        fail.assertPass.set(false)  // 标记为"应该失败"
        directives.add(fail)
    }
}
```

```java
// MuzzleCodeGenerationPlugin.java — ByteBuddy 入口
public final class MuzzleCodeGenerationPlugin implements Plugin {
    @Override
    public boolean matches(TypeDescription target) {
        // 只匹配 InstrumentationModule 的非抽象子类
    }

    @Override
    public DynamicType.Builder<?> apply(...) {
        // 把 MuzzleCodeGenerator（ASM Visitor）挂上去
        return builder.visit(new MuzzleCodeGenerator(classLoader));
    }
}
```

### 8.7 缓存优化

`MuzzleMatcher` 按 ClassLoader 缓存匹配结果：

```java
// MuzzleMatcher.java
private final Cache<ClassLoader, Boolean> matchCache = Cache.weak();

public boolean matches(TypeDescription typeDescription, ClassLoader classLoader, ...) {
    return matchCache.computeIfAbsent(classLoader, cl -> doesMatch(cl));
}
```

同一个 ClassLoader 只检查一次，后续类加载直接用缓存结果。

### 8.8 总结

| 阶段 | 时机 | 做什么 |
|------|------|--------|
| **编译期收集** | `gradle build` | ASM 扫描 Advice 字节码，提取所有外部类/方法/字段引用，生成 `getMuzzleReferences()` |
| **编译期验证** | `gradle build` | 根据 `muzzle { pass/fail }` 声明，下载指定版本的目标库，验证引用是否如预期匹配/不匹配 |
| **运行时校验** | Agent 启动后 | 每次类加载触发 `MuzzleMatcher`，用 TypePool 检查当前 ClassLoader 是否满足引用清单 |
| **运行时决策** | 类加载时 | 全部匹配→注入字节码；有不匹配→安全跳过，不报错 |

Muzzle 本质上是一个**编译时分析 + 运行时守卫**的双重保险机制，确保 Agent 在面对用户千差万别的依赖版本时，不会因为找不到目标类/方法而崩溃。
