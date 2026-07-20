# ByteBuddy 字节码织入原理与本项目调用链

> 整理自代码走读，目标读者：想搞清楚 OTel javaagent **底层是怎么把 trace 代码"塞"进 Spring/JDBC/OkHttp 等第三方库**的开发者。
>
> 相关文档：
>
> - [javaagent-structure.md](javaagent-structure.md) — agent 的整体模块结构
> - [writing-instrumentation-module.md](writing-instrumentation-module.md) — 怎么写一个 InstrumentationModule
> - [debugging.md](debugging.md) — 怎么调试 Advice
> - [muzzle.md](muzzle.md) — 版本兼容性安全网

---

## 1. Advice、被增强程序、ClassFileTransformer 三者关系

### 三个角色各自是什么

| 角色 | 本质 | 谁写 | 何时存在 |
|---|---|---|---|
| **被增强的 Java 程序** | 普通的 `.class` 字节码（Spring、JDBC 驱动、用户业务类等） | 第三方/用户 | 一直在 |
| **Advice** | 一段带 `@Advice.OnMethodEnter` / `@OnMethodExit` 注解的 Java 类，描述"进/出方法时要插入的逻辑" | OTel agent 作者 | 编译进 agent jar |
| **ClassFileTransformer** | `java.lang.instrument.ClassFileTransformer` 接口实现，签名是 `byte[] transform(..., byte[] classfileBuffer)`：**输入原始字节码 → 输出改写后的字节码** | ByteBuddy 自动生成 | 运行时由 agent 注册到 JVM |

### 正确的因果链

> ❌ "Advice 把额外字节码加到 Java 程序上，然后通过 ClassFileTransformer 注册到 JVM"
>
> ✅ "**ClassFileTransformer 注册到 JVM**；类加载时 JVM 回调它；它**根据 Advice 的模板**把额外字节码织入到被增强程序的方法入口/出口"

一句话：

> **Advice 是"插什么"的说明书，ClassFileTransformer 是"在类加载这个时机去插"的执行者，被增强的 Java 程序是"被插的对象"。说明书自己不动手，执行者拿着说明书在 JVM 给的回调时机动手。**

### 时序图

```
[启动阶段]  premain(String args, Instrumentation inst)
              │
              │ 1) 读 OTel 的 InstrumentationModule 列表
              │ 2) ByteBuddy 把每个 module 的"类匹配 + 方法匹配 + Advice"
              │    编译成一个 ClassFileTransformer
              │ 3) inst.addTransformer(transformer, true)  ← 注册到 JVM
              ▼
[运行阶段]  JVM 每加载一个类，就回调 transformer.transform(原始字节码)
              │
              │ 4) ByteBuddy 在 transformer 内部判断：
              │    - 这个类我要不要改？   (typeMatcher)
              │    - 类里哪些方法要改？   (methodMatcher)
              │    - 命中的方法，按 Advice 的模板把字节码插到方法入口/出口
              ▼
              返回新的 byte[] → JVM 用新字节码定义这个类
              │
              ▼
[执行阶段]  应用调用这个方法，执行的就是 "Advice 入口代码 + 原方法体 + Advice 出口代码"
```

---

## 2. ClassFileTransformer 只有一个

**业务插桩共用同一个 ClassFileTransformer**，所有 `InstrumentationModule` 实现类合并到同一个 ByteBuddy `AgentBuilder`，最后只产出一个转换器。

> 数量级参考：上游 OTel Java Agent v2.29.0-alpha 基线（commit `e59b82c2`，分发 JAR 24.2 MB）共 **271** 个 `InstrumentationModule` 实现类（以 `git grep -l "extends InstrumentationModule" -- 'instrumentation/**/*.java' | wc -l` 在根提交上统计为准）；当前精简版收敛至 **78** 个（JAR 16.0 MB，−33.9%）。注意这与 `instrumentation/` 下 `build.gradle.kts` 文件数（基线 556 / 当前 190）并非同一口径——后者包含 `library/` / `javaagent/` / `testing/` 等子目录的独立构建脚本。

### 代码佐证

[javaagent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/AgentInstaller.java:196](../../javaagent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/AgentInstaller.java#L196)：

```java
agentBuilder = AgentBuilderUtil.optimize(agentBuilder);
ClassFileTransformer transformer = agentBuilder.installOn(inst);   // ← 只调一次
```

### 为什么是一个

1. **类加载是热路径**：JVM 每加载一个类会**遍历所有已注册 transformer**。如果每模块一个 transformer，按基线 271 个 InstrumentationModule 计算就要回调 271 次，光"我管不管这个类"的判断开销就能让启动慢上几倍。
2. **优化决策树**：合并后 ByteBuddy 可以把所有 `typeMatcher` 编译成一棵决策树（[`AgentBuilderUtil.optimize`](../../javaagent-tooling/src/main/java/net/bytebuddy/agent/builder/AgentBuilderUtil.java)），一次匹配就决定哪些模块命中。
3. **多模块改同一方法不丢失**：合并后 ByteBuddy 用 Advice 链式叠加机制，让多个模块的 Advice 在同一方法上**层层包裹**；独立 transformer 会互相覆盖。

### 例外：3 个 agent 自身的补丁 transformer

[AgentStarterImpl.java:126-135](../../javaagent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/AgentStarterImpl.java#L126-L135) 还另外注册了 3 个手写 ASM 的独立 transformer，**给 agent 自身打补丁**用，不是给业务库插桩用：

```java
instrumentation.addTransformer(new InetAddressClassFileTransformer(), true);          // 修 InetAddress 死锁
instrumentation.addTransformer(new AgentBuilderDefaultClassFileTransformer(), true);  // 修 ByteBuddy 内部
instrumentation.addTransformer(new CallbackRegistrationClassFileTransformer(), true); // 修 lambda 回调注册
```

---

## 3. ByteBuddy 操作字节码的原理

### 起点：JDK 的 Instrumentation API

```java
public interface ClassFileTransformer {
    byte[] transform(ClassLoader loader,
                     String className,
                     Class<?> classBeingRedefined,
                     ProtectionDomain protectionDomain,
                     byte[] classfileBuffer) throws IllegalClassFormatException;
}
```

JVM 的承诺：**每次要加载一个类（还没 verify、还没 link）时，先把这个类的 `.class` 原始字节数组交给所有已注册的 transformer，谁返回新的 `byte[]`，JVM 就用新的去定义类**。ByteBuddy 整个体系建立在这一个钩子之上。

### ByteBuddy 内部三层

```
┌──────────────────────────────────────────────────────┐
│ 高层 DSL：AgentBuilder / Advice / @OnMethodEnter     │  ← 业务代码写的
├──────────────────────────────────────────────────────┤
│ 中层模型：TypeDescription / MethodDescription /      │  ← ByteBuddy 自己的"反射"
│           ByteCodeAppender / Implementation          │
├──────────────────────────────────────────────────────┤
│ 底层引擎：ASM (org.objectweb.asm)                    │  ← 真正的字节码读写
└──────────────────────────────────────────────────────┘
```

- **底层是 ASM**：业界标准的字节码读写库（`ClassReader` / `ClassWriter` / `MethodVisitor`）。ByteBuddy 把 ASM 当后端。
- **中层是模型**：把 `byte[]` 解析成 `TypeDescription`（≈ `Class`）、`MethodDescription`（≈ `Method`）。
- **高层是 DSL**：让你写普通 Java 代码就能描述"在某方法入口插一段逻辑"。

### Advice 的关键魔法：源码级"剪切粘贴"字节码

`@Advice.OnMethodEnter` 不是"在运行时调用我"，而是**编译期就把这个方法的字节码"剪切粘贴"到目标方法的头部**。

例子：

```java
public static class StatementAdvice {
    @Advice.OnMethodEnter
    public static long onEnter(@Advice.Argument(0) String sql) {
        return System.nanoTime();
    }
    @Advice.OnMethodExit
    public static void onExit(@Advice.Enter long start, @Advice.Argument(0) String sql) {
        System.out.println(sql + " took " + (System.nanoTime() - start));
    }
}
```

ByteBuddy 拿到 `Statement.execute(String sql)` 的原始字节码后做的事：

1. **读 Advice 类的字节码**，提取 `onEnter` 方法体的 ASM 指令序列
2. **替换参数引用**：把 `@Advice.Argument(0)` 替换为读取目标方法对应的局部变量槽
3. **拼接**：在 `execute` 方法的第一条指令前，插入 `onEnter` 的指令序列；返回值（`long`）写入新分配的局部变量槽
4. 对 `onExit` 同理，但插在所有 `RETURN/ARETURN/...` 指令之前；`@Advice.Enter` 替换为读取那个局部变量槽
5. **重新计算 stack map frame、max stack、max locals**（JVM 字节码校验器要求的元数据）
6. **输出新的 byte[]**

最终 `execute` 方法体大致变成（伪代码）：

```java
public boolean execute(String sql) {
    // ↓↓↓ 从 onEnter 内联进来的字节码
    long $$enter = System.nanoTime();
    // ↑↑↑

    boolean result = doRealExecute(sql);   // 原方法体

    // ↓↓↓ 从 onExit 内联进来的字节码
    System.out.println(sql + " took " + (System.nanoTime() - $$enter));
    // ↑↑↑
    return result;
}
```

**所以 Advice 在运行时根本不存在一次"方法调用"——它的指令直接是目标方法体的一部分**。这导致了三个直接结果：

- Advice 调用零开销（无 invoke 指令、无栈帧）
- Advice 里打不上断点（IDE 找的是 Advice 类，但运行的字节码长在目标类里）
- Advice 里只能用 static 方法、不能引用外部状态（因为它要被搬到任意类里执行）

`inline=false` 就是关掉这个剪切粘贴，改成生成一条 `invokestatic` 调用 Advice 类——这时候才有真正的方法调用，才能打断点。

### `@Advice.Argument(N)` vs JVM 局部变量槽

这两套编号容易混淆：

| 编号 | 视角 | 从几起 | 数什么 |
|---|---|---|---|
| **`@Advice.Argument(N)`** | 源码层 / Java 程序员 | **0** | 方法的第 N 个**显式参数**（Java 代码里写的） |
| **JVM 局部变量槽** | 字节码层 / JVM | **0** | 方法栈帧里所有局部变量的存放位置，**包括隐藏的 `this`** |

实例方法 `boolean execute(String sql)`：

```
slot 0 : this           ← JVM 隐式塞进去的，源码里看不见
slot 1 : sql            ← 第一个显式参数（@Advice.Argument(0)）
slot 2+: 方法体里的局部变量
```

static 方法 `static boolean execute(String sql)`：

```
slot 0 : sql            ← 没有 this，参数从 0 开始
```

`long`/`double` 占**两个槽**：

```java
void foo(long a, int b)   // 实例方法
```

```
slot 0 : this
slot 1 : a (低 32 位)
slot 2 : a (高 32 位)
slot 3 : b               ← @Advice.Argument(1) 在槽 3
```

ByteBuddy 知道方法是不是 static、前面有没有 `long`/`double`，会**自动算出正确的槽号**。验证可以用 `javap -v` 看 `LocalVariableTable`。

### HelperInjector：跨类加载器的难题

Advice 内联进 `java.sql.Statement` 后，里面引用了 `JdbcSingletons.processSql(...)`——但这个类是 OTel agent 里的，**目标应用的类加载器看不到它**。

ByteBuddy 提供 `AgentBuilder.Transformer`（注意不是 `ClassFileTransformer`），可以在每次匹配命中时往**目标类的 ClassLoader** 里注入一批 helper 类的 `byte[]`，让它能解析 Advice 引用的符号。这就是 [InstrumentationModuleInstaller.java:209](../../javaagent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/instrumentation/InstrumentationModuleInstaller.java#L209) 那行 `extendableAgentBuilder.transform(helperInjector)`。

---

## 4. 本项目的调用链

调用 ByteBuddy 的代码不在某个 instrumentation 模块里，而在**总装车间** [javaagent-tooling/](../../javaagent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/) 里：

```
AgentInstaller.execute()                                   ← 入口
    └─ for each InstrumentationModule:
       InstrumentationModuleInstaller.install(module, agentBuilder)
            └─ installIndyModule(module, agentBuilder)     ← 真正调 ByteBuddy
                  ├─ agentBuilder.type(typeMatcher)        ← 声明匹配哪些类
                  ├─ .transform(helperInjector)            ← 注入 helper 类
                  ├─ typeInstrumentation.transform(typeTransformer)
                  │     └─ transformer.applyAdviceToMethod(methodMatcher, AdviceClassName)
                  │            ↓
                  │     extendableAgentBuilder
                  │       .visit(Advice.to(AdviceClass).on(methodMatcher))   ← ByteBuddy 核心调用
                  └─ 返回新的 agentBuilder
    └─ agentBuilder.installOn(inst)                        ← 一次性产出 ClassFileTransformer 注册到 JVM
```

---

## 5. 完整例子：JDBC `Statement.execute(String)` 是怎么被增强的

### 第 1 步：模块声明"我要管哪些类、哪些方法、用哪个 Advice"

[instrumentation/jdbc/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/jdbc/StatementInstrumentation.java:30-58](../../instrumentation/jdbc/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/jdbc/StatementInstrumentation.java#L30-L58)：

```java
class StatementInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.Statement"));   // ① 匹配所有 implements Statement 的类
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        nameStartsWith("execute").and(takesArgument(0, String.class)).and(isPublic()),
        getClass().getName() + "$StatementAdvice");            // ② 把 StatementAdvice 套到 execute(String...) 上
    ...
  }

  public static class StatementAdvice {
    @Advice.OnMethodEnter(inline = false)                      // ③ 入口要插的代码
    public static Object[] onEnter(
        @Advice.Argument(0) String sql, @Advice.This Statement statement) { ... }

    @Advice.OnMethodExit(onThrowable = Throwable.class, inline = false)   // ④ 出口要插的代码
    public static void stopSpan(
        @Advice.Thrown Throwable throwable, @Advice.Enter Object[] enterResult) { ... }
  }
}
```

OTel 自己写的代码到这里就结束了——它只是声明了 4 件事，没有任何"操作字节码"的痕迹。

### 第 2 步：tooling 层把声明翻译成 ByteBuddy 调用

[InstrumentationModuleInstaller.java:198-218](../../javaagent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/instrumentation/InstrumentationModuleInstaller.java#L198-L218)：

```java
AgentBuilder agentBuilder = parentAgentBuilder;
for (TypeInstrumentation typeInstrumentation : instrumentationModule.typeInstrumentations()) {
  AgentBuilder.Identified.Extendable extendableAgentBuilder =
      setTypeMatcher(agentBuilder, instrumentationModule, typeInstrumentation)   // ← agentBuilder.type(typeMatcher)
          .and(muzzleMatcher)
          .transform(ConstantAdjuster.instance())
          .transform(new ForwardIndyAdviceTransformer(helperInjector));

  extendableAgentBuilder = extendableAgentBuilder.transform(helperInjector);    // ← 注入 helper 到目标 ClassLoader
  extendableAgentBuilder = contextProvider.injectHelperClasses(extendableAgentBuilder);
  IndyTypeTransformerImpl typeTransformer =
      new IndyTypeTransformerImpl(extendableAgentBuilder, instrumentationModule);
  typeInstrumentation.transform(typeTransformer);                               // ← 触发 applyAdviceToMethod 回调
  extendableAgentBuilder = typeTransformer.getAgentBuilder();
  ...
}
```

`typeInstrumentation.transform(typeTransformer)` 这一行回调进 `StatementInstrumentation.transform(...)`，`applyAdviceToMethod` 在 `IndyTypeTransformerImpl` 里实现成（简化）：

```java
extendableAgentBuilder = extendableAgentBuilder
    .visit(Advice.to(StatementAdvice.class).on(methodMatcher));
```

**`Advice.to(...).on(...)` 就是 ByteBuddy 的核心 API**——告诉 ByteBuddy"把 `StatementAdvice` 类里 `@OnMethodEnter`/`@OnMethodExit` 的字节码内联到匹配 `methodMatcher` 的方法的入口/出口"。

### 第 3 步：所有模块装完，一次性 installOn

[AgentInstaller.java:196](../../javaagent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/AgentInstaller.java#L196)：

```java
ClassFileTransformer transformer = agentBuilder.installOn(inst);
```

ByteBuddy 在这里：

1. 把所有模块累积的 `type(...)` 匹配器编译成决策树
2. 把所有模块的 `Advice.to(...)` 包装成一个 `ClassFileTransformer`
3. 调 `inst.addTransformer(transformer, true)` 注册到 JVM

### 第 4 步：运行时——HikariCP 加载它的 `HikariProxyStatement` 时

```
JVM 调 transformer.transform(loader, "HikariProxyStatement", null, pd, originalBytes)
    │
    ▼
ByteBuddy 内部：
    1. ASM ClassReader 解析 originalBytes → TypeDescription
    2. 跑决策树：implementsInterface(Statement) → 命中 StatementInstrumentation
    3. 遍历方法，找 nameStartsWith("execute") + 一个 String 参数 → 命中 execute(String)
    4. 拿 StatementAdvice.class 的字节码（从 agent jar 里读）
    5. 用 ASM MethodVisitor 重写 execute(String) 的方法体：
         - 在第一条指令前，插入 onEnter 方法体
           （inline=false 时实际是 invokestatic StatementAdvice.onEnter(...) 调用）
         - 在每个 RETURN 前，插入 onExit 方法体
           （@Advice.Enter 替换为读 local 槽）
    6. 重算 stack map frames / maxStack / maxLocals
    7. ClassWriter 输出新 byte[]
    │
    ▼
返回新 byte[] → JVM 用它定义 HikariProxyStatement 类
```

之后业务代码每次调 `stmt.execute("SELECT ...")`：

1. 进方法第一件事执行（或调用）`StatementAdvice.onEnter`，启动 span
2. 真正的 SQL 执行
3. 方法 return / throw 之前执行 `StatementAdvice.stopSpan`，结束 span

业务代码**一行没改**，HikariCP 也**一行没改**。

---

## 6. 一句话总结

> **ByteBuddy 的原理**：基于 JVM 的 `ClassFileTransformer` 钩子，用 ASM 在类加载瞬间读写 `byte[]`，把 Advice 类的方法体字节码**内联**到目标类的方法入口/出口（`inline=false` 时改为 `invokestatic` 调用）。
>
> **本项目的调用点**：[InstrumentationModuleInstaller.installIndyModule()](../../javaagent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/instrumentation/InstrumentationModuleInstaller.java#L135) 把每个 `TypeInstrumentation` 翻译成一组 `agentBuilder.type(...).transform(...).visit(Advice.to(...).on(...))`；最后 [AgentInstaller.java:196](../../javaagent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/AgentInstaller.java#L196) 的 `agentBuilder.installOn(inst)` 编译出唯一的 `ClassFileTransformer` 注册到 JVM。
>
> **JDBC 例子**：[StatementInstrumentation](../../instrumentation/jdbc/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/jdbc/StatementInstrumentation.java) 声明"匹配 `Statement` 的 `execute(String)`，套上 `StatementAdvice`"，tooling 层把声明翻译成 ByteBuddy 调用，运行时 HikariCP 的 `HikariProxyStatement` 被加载时，`execute(String)` 的方法体被改写，入口/出口多了 span 启停逻辑。
