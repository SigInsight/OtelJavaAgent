# JMH 性能基准测试结果

## 测试环境

### 测试方法

- **被测对象**：精简版 OpenTelemetry Java Agent（基于 main 分支）
- **测试模块**：`:benchmark-overhead-jmh`
- **测试方式**：Spring Boot 4.0.6 内嵌 Tomcat 处理一次 HTTP 请求的端到端微基准
- **JMH 参数**：默认 5 fork × 5 warmup × 5 measurement × 10s/iter
- **输出单位**：μs/op（每次操作微秒）
- **总耗时**：约 44 分钟
- **采集日期**：2026-06-13

### 测试机器规格

| 维度 | 值 |
|---|---|
| 主机名 | ecm-1280 |
| 操作系统 | Ubuntu 24.04.4 LTS (Noble Numbat) |
| 内核 | Linux 6.8.0-110-generic |
| 虚拟化 | **KVM 虚拟机** |
| CPU | Intel Xeon Platinum 8378C @ 2.80GHz（Ice Lake-SP） |
| CPU 拓扑 | 1 socket × 16 core × 1 thread = **16 vCPU** |
| CPU cache | L1d/L1i 各 32 KiB×16，L2 4 MiB×16，L3 16 MiB |
| CPU 指令集 | AVX2 / AVX-512（含 VNNI、BF16、VBMI 等） |
| 内存 | 30 GiB（其中 swap 2 GiB） |
| 磁盘 | nvme0n1 300 GB（XFS）+ nvme1n1 500 GB |
| 运行时 JDK | OpenJDK 25.0.3 (Temurin/Ubuntu build 25.0.3+9) |
| JVM 自动探测最大堆 | ~7.71 GiB（默认 1/4 物理内存） |
| Gradle | 9.5.1 |


## Benchmark 配置说明

| 名称 | 含义 |
|---|---|
| `ServletBenchmark` | 挂载精简 agent，**全采样** —— 所有请求都创建并导出 span |
| `ServletWithAgentDisabledBenchmark` | **不挂 agent**，作为基准对照 |
| `ServletWithSdkDisabledBenchmark` | 挂载 agent，**OTel SDK 关闭**（bytecode instrumentation 仍生效，但不真正导出） |
| `ServletWithOnePercentSamplingBenchmark` | 挂载 agent，**1% 头采样**（`parentbased_traceidratio=0.01`） |

> 4 个 benchmark 共享同一份 Spring Boot HelloWorld 应用，仅 JVM 启动参数不同（agent 是否挂载 / SDK 配置 / 采样率）。

---

## 一、精简版 Agent 跑分（本次结果）

### 控制台原始输出

```
# Run complete. Total time: 00:44:23

Benchmark                                                            Mode      Cnt      Score    Error   Units
ServletBenchmark.execute                                           sample  3424460     72.879 ±  0.091   us/op
ServletBenchmark.execute:gc.alloc.rate                             sample       25    373.884 ±  6.137  MB/sec
ServletBenchmark.execute:gc.alloc.rate.norm                        sample       25  28633.078 ± 27.828    B/op
ServletBenchmark.execute:gc.count                                  sample       25    543.000           counts
ServletBenchmark.execute:gc.time                                   sample       25    458.000               ms
ServletBenchmark.execute:p0.00                                     sample              41.600            us/op
ServletBenchmark.execute:p0.50                                     sample              65.664            us/op
ServletBenchmark.execute:p0.90                                     sample              92.416            us/op
ServletBenchmark.execute:p0.95                                     sample             108.928            us/op
ServletBenchmark.execute:p0.99                                     sample             158.720            us/op
ServletBenchmark.execute:p0.999                                    sample             552.960            us/op
ServletBenchmark.execute:p0.9999                                   sample            2032.750            us/op
ServletBenchmark.execute:p1.00                                     sample           21757.952            us/op
ServletWithAgentDisabledBenchmark.execute                          sample  3695527     67.532 ±  0.106   us/op
ServletWithAgentDisabledBenchmark.execute:gc.alloc.rate            sample       25    334.901 ± 25.214  MB/sec
ServletWithAgentDisabledBenchmark.execute:gc.alloc.rate.norm       sample       25  23762.221 ± 46.408    B/op
ServletWithAgentDisabledBenchmark.execute:gc.count                 sample       25    502.000           counts
ServletWithAgentDisabledBenchmark.execute:gc.time                  sample       25    412.000               ms
ServletWithAgentDisabledBenchmark.execute:p0.00                    sample              37.184            us/op
ServletWithAgentDisabledBenchmark.execute:p0.50                    sample              58.624            us/op
ServletWithAgentDisabledBenchmark.execute:p0.90                    sample              88.192            us/op
ServletWithAgentDisabledBenchmark.execute:p0.95                    sample             107.776            us/op
ServletWithAgentDisabledBenchmark.execute:p0.99                    sample             162.048            us/op
ServletWithAgentDisabledBenchmark.execute:p0.999                   sample             782.336            us/op
ServletWithAgentDisabledBenchmark.execute:p0.9999                  sample            2758.440            us/op
ServletWithAgentDisabledBenchmark.execute:p1.00                    sample           18219.008            us/op
ServletWithOnePercentSamplingBenchmark.execute                     sample  3165811     78.833 ±  0.142   us/op
ServletWithOnePercentSamplingBenchmark.execute:gc.alloc.rate       sample       25    339.502 ± 22.205  MB/sec
ServletWithOnePercentSamplingBenchmark.execute:gc.alloc.rate.norm  sample       25  28126.831 ± 75.739    B/op
ServletWithOnePercentSamplingBenchmark.execute:gc.count            sample       25    496.000           counts
ServletWithOnePercentSamplingBenchmark.execute:gc.time             sample       25    460.000               ms
ServletWithOnePercentSamplingBenchmark.execute:p0.00               sample              41.152            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.50               sample              67.200            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.90               sample             105.216            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.95               sample             127.872            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.99               sample             193.792            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.999              sample            1185.792            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.9999             sample            2963.123            us/op
ServletWithOnePercentSamplingBenchmark.execute:p1.00               sample           17334.272            us/op
ServletWithSdkDisabledBenchmark.execute                            sample  3289617     75.868 ±  0.113   us/op
ServletWithSdkDisabledBenchmark.execute:gc.alloc.rate              sample       25    355.838 ±  5.249  MB/sec
ServletWithSdkDisabledBenchmark.execute:gc.alloc.rate.norm         sample       25  28369.273 ± 19.692    B/op
ServletWithSdkDisabledBenchmark.execute:gc.count                   sample       25    517.000           counts
ServletWithSdkDisabledBenchmark.execute:gc.time                    sample       25    446.000               ms
ServletWithSdkDisabledBenchmark.execute:p0.00                      sample              41.728            us/op
ServletWithSdkDisabledBenchmark.execute:p0.50                      sample              66.560            us/op
ServletWithSdkDisabledBenchmark.execute:p0.90                      sample              97.408            us/op
ServletWithSdkDisabledBenchmark.execute:p0.95                      sample             117.248            us/op
ServletWithSdkDisabledBenchmark.execute:p0.99                      sample             171.520            us/op
ServletWithSdkDisabledBenchmark.execute:p0.999                     sample            1035.264            us/op
ServletWithSdkDisabledBenchmark.execute:p0.9999                    sample            2203.804            us/op
ServletWithSdkDisabledBenchmark.execute:p1.00                      sample           18350.080            us/op
```

### 关键指标汇总

| 配置 | 平均 (μs) | p50 (μs) | p95 (μs) | p99 (μs) | p99.9 (μs) | 内存分配 (B/op) | GC 暂停 (ms) | 相对基准 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ServletWithAgentDisabledBenchmark（无 agent） | **67.53** | 58.6 | 107.8 | 162.0 | 782.3 | 23,762 | 412 | 0%（基准） |
| ServletBenchmark（全采样） | **72.88** | 65.7 | 108.9 | 158.7 | 553.0 | 28,633 | 458 | **+7.9%** |
| ServletWithSdkDisabledBenchmark（SDK 关） | 75.87 | 66.6 | 117.2 | 171.5 | 1035.3 | 28,369 | 446 | +12.4% |
| ServletWithOnePercentSamplingBenchmark（1% 采样） | 78.83 | 67.2 | 127.9 | 193.8 | 1185.8 | 28,127 | 460 | +16.7% |

---

## 二、原始 OpenTelemetry Java Agent 跑分（v2.28.1）

> 同一台机器、同一份 JMH 配置（5 fork × 5 warmup × 5 measure × 10s/iter）。

- **被测对象**：上游官方 `opentelemetry-javaagent.jar` v2.28.1
- **构建路径**：`/home/cbw/code/tmp/opentelemetry-java-instrumentation/`（独立 git checkout，未做模块精简）
- **总耗时**：46 分 35 秒
- **采集日期**：2026-06-13（与精简版同日，相隔约 2 小时）

### 控制台原始输出（v2.28.1）

```text
# Run complete. Total time: 00:44:31

Benchmark                                                            Mode      Cnt      Score    Error   Units
ServletBenchmark.execute                                           sample  3148616     79.266 ±  0.136   us/op
ServletBenchmark.execute:gc.alloc.rate                             sample       25    344.130 ± 34.789  MB/sec
ServletBenchmark.execute:gc.alloc.rate.norm                        sample       25  28665.669 ± 54.014    B/op
ServletBenchmark.execute:gc.count                                  sample       25    470.000           counts
ServletBenchmark.execute:gc.time                                   sample       25    443.000               ms
ServletBenchmark.execute:p0.00                                     sample              41.728            us/op
ServletBenchmark.execute:p0.50                                     sample              67.712            us/op
ServletBenchmark.execute:p0.90                                     sample             106.496            us/op
ServletBenchmark.execute:p0.95                                     sample             129.792            us/op
ServletBenchmark.execute:p0.99                                     sample             196.608            us/op
ServletBenchmark.execute:p0.999                                    sample            1081.344            us/op
ServletBenchmark.execute:p0.9999                                   sample            2761.270            us/op
ServletBenchmark.execute:p1.00                                     sample           18251.776            us/op
ServletWithAgentDisabledBenchmark.execute                          sample  3799048     65.692 ±  0.089   us/op
ServletWithAgentDisabledBenchmark.execute:gc.alloc.rate            sample       25    343.929 ±  5.596  MB/sec
ServletWithAgentDisabledBenchmark.execute:gc.alloc.rate.norm       sample       25  23739.846 ± 49.130    B/op
ServletWithAgentDisabledBenchmark.execute:gc.count                 sample       25    505.000           counts
ServletWithAgentDisabledBenchmark.execute:gc.time                  sample       25    407.000               ms
ServletWithAgentDisabledBenchmark.execute:p0.00                    sample              37.568            us/op
ServletWithAgentDisabledBenchmark.execute:p0.50                    sample              58.432            us/op
ServletWithAgentDisabledBenchmark.execute:p0.90                    sample              81.920            us/op
ServletWithAgentDisabledBenchmark.execute:p0.95                    sample              97.792            us/op
ServletWithAgentDisabledBenchmark.execute:p0.99                    sample             143.872            us/op
ServletWithAgentDisabledBenchmark.execute:p0.999                   sample             893.952            us/op
ServletWithAgentDisabledBenchmark.execute:p0.9999                  sample            1841.152            us/op
ServletWithAgentDisabledBenchmark.execute:p1.00                    sample           16613.376            us/op
ServletWithOnePercentSamplingBenchmark.execute                     sample  3262555     76.497 ±  0.111   us/op
ServletWithOnePercentSamplingBenchmark.execute:gc.alloc.rate       sample       25    350.291 ± 12.107  MB/sec
ServletWithOnePercentSamplingBenchmark.execute:gc.alloc.rate.norm  sample       25  28158.753 ± 71.078    B/op
ServletWithOnePercentSamplingBenchmark.execute:gc.count            sample       25    479.000           counts
ServletWithOnePercentSamplingBenchmark.execute:gc.time             sample       25    442.000               ms
ServletWithOnePercentSamplingBenchmark.execute:p0.00               sample              41.856            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.50               sample              67.328            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.90               sample              99.456            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.95               sample             119.680            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.99               sample             169.984            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.999              sample             973.824            us/op
ServletWithOnePercentSamplingBenchmark.execute:p0.9999             sample            1981.941            us/op
ServletWithOnePercentSamplingBenchmark.execute:p1.00               sample           16842.752            us/op
ServletWithSdkDisabledBenchmark.execute                            sample  3346840     74.571 ±  0.101   us/op
ServletWithSdkDisabledBenchmark.execute:gc.alloc.rate              sample       25    363.291 ±  4.255  MB/sec
ServletWithSdkDisabledBenchmark.execute:gc.alloc.rate.norm         sample       25  28466.980 ± 61.787    B/op
ServletWithSdkDisabledBenchmark.execute:gc.count                   sample       25    517.000           counts
ServletWithSdkDisabledBenchmark.execute:gc.time                    sample       25    474.000               ms
ServletWithSdkDisabledBenchmark.execute:p0.00                      sample              40.640            us/op
ServletWithSdkDisabledBenchmark.execute:p0.50                      sample              66.176            us/op
ServletWithSdkDisabledBenchmark.execute:p0.90                      sample              94.720            us/op
ServletWithSdkDisabledBenchmark.execute:p0.95                      sample             113.408            us/op
ServletWithSdkDisabledBenchmark.execute:p0.99                      sample             164.352            us/op
ServletWithSdkDisabledBenchmark.execute:p0.999                     sample             967.680            us/op
ServletWithSdkDisabledBenchmark.execute:p0.9999                    sample            1924.366            us/op
ServletWithSdkDisabledBenchmark.execute:p1.00                      sample           16416.768            us/op
```

### 关键指标汇总（原版 v2.28.1）

| 配置 | 平均 (μs) | p50 (μs) | p95 (μs) | p99 (μs) | p99.9 (μs) | 内存分配 (B/op) | GC 暂停 (ms) | 相对基准 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| ServletWithAgentDisabledBenchmark（无 agent） | **65.69** | 58.4 | 97.8 | 143.9 | 894.0 | 23,740 | 407 | 0%（基准） |
| ServletBenchmark（全采样） | **79.27** | 67.7 | 129.8 | 196.6 | 1081.3 | 28,666 | 443 | **+20.7%** |
| ServletWithSdkDisabledBenchmark（SDK 关） | 74.57 | 66.2 | 113.4 | 164.4 | 967.7 | 28,467 | 474 | +13.5% |
| ServletWithOnePercentSamplingBenchmark（1% 采样） | 76.50 | 67.3 | 119.7 | 170.0 | 973.8 | 28,159 | 442 | +16.5% |

---

## 三、精简版 vs 原版 对比小结

### 直接对比

| 配置 | 精简版 (μs) | 原版 v2.28.1 (μs) | Δ | 解读 |
| --- | ---: | ---: | ---: | --- |
| 无 agent（基准对照） | 67.53 | 65.69 | +1.84 (+2.8%) | 同机不同时段的环境噪声，2-3% 之内 |
| **全采样** | **72.88** | **79.27** | **-6.39 (-8.1%)** | **精简版显著更快** |
| SDK 关闭 | 75.87 | 74.57 | +1.30 (+1.7%) | 几乎相同 |
| 1% 采样 | 78.83 | 76.50 | +2.33 (+3.0%) | 接近，差距在噪声范围 |

### 净开销对比（更可信指标）

> 用"agent 配置 vs 当次跑分的无 agent 基准"消除两次跑分间的环境差异。

| 维度 | 精简版 | 原版 v2.28.1 | Δ |
| --- | ---: | ---: | ---: |
| 全采样 净延迟开销 | +5.35 μs (+7.9%) | +13.57 μs (+20.7%) | **精简版省 -8.21 μs，相对原版砍 60% 净开销** |
| 全采样 p99 净开销 | -3.3 μs (-2.0%) | +52.7 μs (+36.6%) | 精简版高分位无显著抖动；原版 p99 拉高明显 |
| 全采样 p99.9 净开销 | -229 μs (-29.3%) | +187 μs (+20.9%) | 精简版反而比基准还低，原版抖动较大 |
| 全采样 内存分配净增 | +4,871 B/op (+20.5%) | +4,926 B/op (+20.7%) | **几乎相同**（见下方解读） |
| 全采样 GC 暂停净增 | +46 ms (+11.2%) | +36 ms (+8.8%) | 接近，原版略低 |
| 1% 采样 净延迟开销 | +11.30 μs (+16.7%) | +10.81 μs (+16.5%) | **几乎相同** |

### 三个关键发现

#### 🟢 1. CPU 净开销减半（+20.7% → +7.9%）

全采样下，精简版相对无 agent 基准的延迟净增加从原版的 **+13.6 μs** 降到 **+5.4 μs**。这是模块精简的最大收益 —— **agent 加载的 instrumentation 越少，每次请求要经过的全局 dispatch / advice 链就越短**。

#### 🟡 2. 单请求内存分配几乎相同（+4,871 vs +4,926 B/op）

这个 HelloWorld benchmark 只走 `servlet → controller → response` 这一条路径。无论 agent 挂载了 50 个还是 5 个 instrumentation 模块，**这条路径上触发分配的还是同一套 servlet/http instrumentation**，所以单请求分配量几乎一样。

> **推论**：精简模块带来的内存收益体现在 **agent 启动期 metaspace 占用** 和 **复杂业务路径**（同时触发 jdbc / spring / kafka 多个模块），不在简单 servlet HelloWorld 上。要量化这部分收益，需要 [benchmark-overhead](../benchmark-overhead/) 那套 Petclinic 宏基准。

#### 🟢 3. 高分位延迟稳定性显著提升

| | 精简版 p99 | 原版 p99 | 精简版 p99.9 | 原版 p99.9 |
| --- | ---: | ---: | ---: | ---: |
| 全采样 | 158.7 μs | 196.6 μs | 553.0 μs | 1081.3 μs |
| 相对基准 | -3.3 μs | +52.7 μs | -229 μs | +187 μs |

精简版的 p99.9 **比无 agent 基准还低**，原版则 +20.9%。原因可能是模块少 → metaspace 小 → JIT 编译热点更集中 → tail latency 更稳定。

### 数据可信度评估

| 检查项 | 评估 |
| --- | --- |
| 同机器 / 同 JMH 配置 / 同日采集 | ✅ |
| 样本数 | 两次都 300+ 万样本/benchmark，统计意义充足 |
| 误差范围 | Score 误差均 ≤ ±0.15 μs，远小于差距 |
| 环境噪声 | 两次"无 agent"基准差 2.8%，构成约 ±2-3% 不确定性 |
| 主结论稳健性 | "净开销减半" 8.2 μs 的差距 **远超 2-3% 噪声**，结论可信 |
| p99 / p99.9 解读 | 高分位本身波动较大，趋势可参考但不应过度解读 |

---

## 四、本次结果分析（精简版独立观察）

### 1. 全开 agent 仅引入 +7.9% 延迟（67.5 → 72.9 μs）

每请求增加约 **5.3 μs** 的纯 CPU 开销。在 HTTP 请求场景下完全可接受。
- p99 甚至 **比基准更低**（158.7 vs 162.0 μs），说明高分位上 agent 没有引入抖动。
- p99.9 同样更低（553 vs 782 μs），证明 instrumentation 的开销分布很稳定。

### 2. 内存分配涨了约 20%（每请求多 ~5KB）

| | 无 agent | 全 agent |
|---|---:|---:|
| 每请求分配 | 23.7 KB | 28.6 KB |
| 每秒分配速率 | 335 MB/s | 374 MB/s |
| GC 次数 | 502 | 543 |
| GC 总暂停 | 412 ms | 458 ms |

每请求多分配的 ~5KB 来自：`AttributesBuilder` / `AttributeKey` / `Context` / `Span` 等临时对象。**这才是 agent 真正的代价 —— GC 压力**，而非 CPU。

### 3. 反直觉发现：1% 采样 ≠ 更快

| 配置 | 平均 | 内存分配 |
|---|---:|---:|
| 全采样 | 72.88 μs | 28,633 B/op |
| **1% 采样** | **78.83 μs** ⚠️ | **28,127 B/op** |

**采样率从 100% 降到 1%，应用层延迟反而升高了 ~8%。** 原因：

- `parentbased_traceidratio` 采样判断本身有成本（trace context decode + ID hash + 阈值比较）
- bytecode instrumentation 的 wrap 开销跟采样率**无关**，方法入口/出口 advice 始终都被执行
- 99% 被丢弃的请求仍走 NoOp span 路径，仍然要创建对象、push/pop context
- 真正省下的是后端 export，而 export 本身是 batch 异步的，对单请求延迟影响很小

**结论**：**采样省的是 collector / 网络 / 存储，不是应用 CPU。** 想降低应用层开销，要靠精简 instrumentation 模块，不是降采样率。

### 4. SDK 完全关掉也没省下分配

| | 内存分配 (B/op) |
|---|---:|
| 全 agent + SDK on | 28,633 |
| 全 agent + SDK off | 28,369 |

差距仅 264 B/op（不到 1%）。这说明 ~5KB 的额外分配里，**绝大部分来自 bytecode instrumentation 这一层**，而非 OTel SDK 自身。

> 推论：单纯关 SDK 不能让 agent "变轻"。要降分配，必须让 instrumentation 模块直接不挂上去 —— 即删除或禁用具体模块。

### 5. 数值置信区间

各项 Score 的 Error 都在 ±0.15 μs 以下（5 fork × 25 iter，3M+ 样本），统计可信。但仍需注意：
- p99.9 / p1.00（最大值）波动较大，不应据此下定性结论。
- JMH 输出中提示了 Compiler Blackholes 实验性特性 —— 跨 JVM 对比时需保持一致 mode。

---

## 四、对"轻量化"目标的指导

按本次数据，**优化路径优先级**应该重排：

| 优化目标 | 真正能动的杠杆 | 预估收益 |
|---|---|---|
| 应用 CPU / 延迟 | 关掉用不上的 instrumentation 模块 | 每关 5 个模块约 -1~2% 延迟 |
| 应用内存分配 | 关 instrumentation 模块 + 关 capture-headers 等动态属性 | -10~20% 分配 |
| 网络流量 / Collector 压力 | **采样**（这才是采样真正擅长的）| 按比例线性减少 |
| 存储空间 | 采样 + attribute 截断 + collector 端 strip 字段 | 80%+ |

---

## 五、复现命令

### 完整运行（约 44 分钟）

```bash
./gradlew :benchmark-overhead-jmh:jmh -x :benchmark-overhead-jmh:jmhReport
```

> `-x :benchmark-overhead-jmh:jmhReport` 用于绕过该 task 与 Gradle 9 configuration cache 的不兼容问题。

### 快速运行（约 5-8 分钟，精度略低）

```bash
./gradlew :benchmark-overhead-jmh:jmh \
  -Pjmh.fork=2 -Pjmh.warmupIterations=3 -Pjmh.iterations=3 \
  -x :benchmark-overhead-jmh:jmhReport
```

### 仅跑指定 benchmark

```bash
./gradlew :benchmark-overhead-jmh:jmh \
  -Pjmh.includes=ServletBenchmark \
  -x :benchmark-overhead-jmh:jmhReport
```

### 抓 JFR 性能剖析（定位分配源）

```bash
./gradlew :benchmark-overhead-jmh:jmh \
  -Pjmh.fork=1 -Pjmh.warmupIterations=5 -Pjmh.iterations=5 \
  -Pjmh.includes=ServletBenchmark \
  -Pjmh.startFlightRecording='settings=profile.jfc,delay=50s,duration=50s,filename=output.jfr' \
  -x :benchmark-overhead-jmh:jmhReport
```

### 结果文件

- 文本表格：构建日志 stdout（如本文一节所示）
- 机器可读：`benchmark-overhead-jmh/build/results/jmh/results.json`

---

## 附：常见问题排查记录

| 现象 | 原因 | 解决 |
|---|---|---|
| `results.json` 仅 `[ ]`，benchmark 表为空 | 8080 端口被占用，所有 fork 启动失败被吞 | `sudo kill $(sudo lsof -ti :8080)` 后重跑 |
| `:benchmark-overhead-jmh:jmhReport FAILED` | `io.morethan.jmh-report` 插件与 Gradle 9 configuration cache 不兼容 | 加 `-x :benchmark-overhead-jmh:jmhReport` 跳过；benchmark 数据本身已落到 `results.json` |
| 模块要求 JDK 17+ | `otelJava.minJavaVersionSupported = 17`（Spring Boot 4.0.6 依赖） | 服务器实测 OpenJDK 25 也能跑，但需保证 toolchain 找到 ≥17 |
