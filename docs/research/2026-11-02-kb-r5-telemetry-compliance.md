# 端侧可观测性与合规研究（R5：本地指标面 + 同意后上报）

- 日期：2026-11-02
- 状态：研究完成；本文只给事实、schema 提案、开关设计要求和可验证判据，不改产品代码
- 目标仓库：`D:\smart mistake book`（Android 离线知识库 App，当前只有 logcat + 本机诊断账本）
- 方法：`mcp__searchd__search_text` / `extract_content` + `WebFetch` 抓官方原文；法规优先 `cac.gov.cn` / `gov.cn` / `npc.gov.cn`，技术规范优先 `open-telemetry.io` / `prometheus.io` / `developer.android.*`；仓库结论带 `file:line`
- 证据等级：
  - **A**：法规、部门规章、国家标准原文（直接引用）
  - **B**：官方技术规范/官方文档原文
  - **C**：本仓库源码、测试、清单（`file:line`）
  - **D**：二手来源或由 A/B/C 推出的工程判断
  - **U**：未验证/单一来源，不得当作定论

> 结论先行：**端侧指标可以采，但“默认开启”在中国法域下不可接受。** 遥测一旦能关联到用户、设备或会话，就是《个人信息保护法》意义上的个人信息；GB/T 35273—2020 附录 A 更直接把“个人上网记录”和“个人常用设备信息”列为个人信息示例。教育类 App 还叠加未成年人保护约束。工程上必须做到：默认关、单独明示同意、最小必要、最短留存、可撤回、<14 周岁监护人同意、拒绝不影响基本功能，并能用代码/测试/抓包证明这些不是文案承诺。

---

## 1. 事实

### 1.1 SRE 四信号在移动端的对应物

Google SRE 的原始定义（A/B）：

> “The four golden signals of monitoring are latency, traffic, errors, and saturation. If you can only measure four metrics of your user-facing system, focus on these four.”
> — https://sre.google/sre-book/monitoring-distributed-systems/

四个信号在服务端的原义与移动端可测物并不一一对应。移动端没有“服务器请求”，但有进程启动、帧渲染、本地数据库/检索、会话与设备资源。对应关系如下。

| SRE 信号 | SRE 原文要点 | 移动端对应物 | 依据 |
|---|---|---|---|
| Latency 延迟 | “The time it takes to service a request”；要区分成功请求与失败请求的延迟，慢错误比快错误更糟 | 冷/温/热启动；TTID（首帧）与 TTFD（可交互）；检索各阶段耗时；首次查询时延；帧渲染 | Android 启动定义与 TTID/TTFD：https://developer.android.google.cn/topic/performance/vitals/launch-time ；OTel Android 的 startup/slow/frozen frame 自动插桩：https://opentelemetry.io/docs/platforms/client-apps/android/ |
| Traffic 流量 | “A measure of how much demand is being placed on your system”，按请求类型拆分 | 会话数、查询次数、活跃天数、功能入口次数；本地知识库被请求的频度 | OTel Android 的 session instrumentation（可配置 inactivity timeout / max lifetime）；Firebase Performance 的生命周期数据：https://firebase.google.cn/docs/perf-mon/get-started-android?hl=zh-cn |
| Errors 错误 | “The rate of requests that fail, either explicitly..., implicitly..., or by policy”；隐式错误需要二级协议才能发现 | 崩溃率、ANR 率、检索异常；**空结果/无相关命中**属于“隐式错误”；错绑/答非所问也属于隐式错误 | Android vitals 的 crash rate / ANR rate：https://developer.android.google.cn/topic/performance/vitals ；OTel Android 的 ANR/crash instrumentation |
| Saturation 饱和度 | “How full your service is”，强调最受限资源；性能在 100% 之前就会退化 | 内存（Anonymous RSS + Swap）、存储余量、数据库大小、检索候选队列深度、遥测缓冲占用、电量/唤醒锁 | Android vitals 的 memory（Anonymous RSS + Swap）、excessive partial wake locks、阈值：crash 1.09% overall / ANR 0.47% overall / wake locks 5% overall；内存无统一阈值，随 RAM 档位变化 |

**工程判断（D）**：不能把服务端 SLO 直接搬到离线 App。离线 App 的“流量”是本地需求，不是 QPS；“错误”里最有价值的是语义失败（空结果、无相关命中、降级到主题兜底），而不是 HTTP 5xx；“饱和度”主要是设备资源与本地索引规模。Play 的 1.09% / 0.47% 是 Google Play 的展示阈值，不是本产品的 SLO，只能作为外部参照。

**一个容易漏掉的点（C）**：当前检索在精排零命中时会**静默回退到主题节点**，所以“最终结果为空”几乎不会发生，直接统计空结果率会得到接近 0 的假象。

- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeContextRetriever.kt:59-79`：先算 `rankedAtoms`；`rankedAtoms.isEmpty()` 时返回 `TOPIC` 节点。
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorKnowledgeContextLoader.kt:60-79`：题面特征为空时直接 `emptyList()`；否则 512 召回 + 精排。

因此 §2 把“空结果”拆成 `no_relevant_hit`（精排零命中，回退前）与 `empty_context`（最终上下文为空）两个指标，否则测不到真实的检索质量退化。

---

### 1.2 直方图桶边界：有没有权威建议

#### 1.2.1 OpenTelemetry 的默认桶

OpenTelemetry Metrics SDK 规范（B）：

> “The default value is `[ 0, 5, 10, 25, 50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 7500, 10000 ]`”
> “SDKs SHOULD use the default value when boundaries are not explicitly provided”
> — https://opentelemetry.io/docs/specs/otel/metrics/sdk/ （Explicit Bucket Histogram Aggregation）

要点：

- 规范**没有绑定单位**；边界按 instrument 的单位解释。对毫秒 instrument 就是 0–10000 ms；对秒 instrument 就是 0–10000 s（显然不适合 HTTP 延迟）。
- 默认 `RecordMinMax = true`；桶之外还有 count/sum/min/max。
- 这套默认桶的低端粒度较粗（0, 5, 10, 25…），适合“通用值”，不适合大量亚毫秒/个位数毫秒的本地 SQLite/JS 阶段。

#### 1.2.2 OpenTelemetry HTTP 语义约定的专用桶

OTel HTTP semantic conventions（B）对 `http.server.request.duration` 与 `http.client.request.duration` 给出**秒**为单位的推荐边界：

> `[0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10]`
> “This metric SHOULD be specified with `ExplicitBucketBoundaries` advisory parameter.”
> — https://opentelemetry.io/docs/specs/semconv/http/http-metrics/

`http.client.connection.duration` 另给一组秒桶：`[0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2, 5, 10, 30, 60, 120, 300]`。这组桶可作为 BYOK 网络调用（若未来测量）的参考。

#### 1.2.3 SRE 的指数分桶经验

SRE 书（A/B）明确建议按延迟分桶，而不是只留平均值：

> “The simplest way to differentiate between a slow average and a very slow ‘tail’ of requests is to collect request counts bucketed by latencies... Distributing the histogram boundaries approximately exponentially (in this case by factors of roughly 3) is often an easy way to visualize the distribution of your requests.”
> — https://sre.google/sre-book/monitoring-distributed-systems/

这给出的是“边界按约 3 倍递增”的经验，不是固定数值。

#### 1.2.4 Prometheus 的实践

Prometheus 官方实践文档（B）：

> “If you can, use native histograms and prefer them over both classic histograms and summaries.”
> “If you cannot use native histograms, but you need aggregations, you have to use classic histograms, which requires you to set appropriate bucket boundaries, covering the correct range of values and finding the right trade-off between the cost for the buckets and the required accuracy.”
> — https://prometheus.io/docs/practices/histograms/

同页还指出：classic histogram 做分位数/成功率时，**SLO 边界必须精确存在**（例如 SLO 是 300 ms，就必须有 `le="0.3"` 的桶），否则不会插值、直接没有结果；桶越多，序列成本越高；native histogram 用指数桶，改 SLO 不需要重设 instrumentation。

#### 1.2.5 结论

- **没有找到“移动端阶段耗时”的官方默认桶标准（U）**。OTel 的默认桶是通用值，HTTP semconv 只覆盖 HTTP 延迟，SRE 只给指数经验，Prometheus 只给选择原则。
- 可落地的组合（D）：
  1. 以 OTel 默认桶为基线，保证和生态一致；
  2. 对本地阶段（亚毫秒到数秒）把低端加密，仍保持约 2–3 倍递增；
  3. 把 SLO 边界（如 300 ms、1 s、3 s）精确放进桶；
  4. 单指标桶数控制在 ≤15 个边界，避免本地存储和聚合基数膨胀；
  5. 导出到 OTel 时用秒、指标名 `*_seconds`；本地存毫秒，避免浮点。

---

### 1.3 本地环形缓冲 + 批量上报的成熟模式

#### 1.3.1 OpenTelemetry 的批处理默认值（B）

| 处理器 | 队列上限 | 单批上限 | 调度延迟 | 导出超时 | 队列满行为 |
|---|---:|---:|---:|---:|---|
| BatchLogRecordProcessor | 2048 | 512 | 1000 ms | 30000 ms | “After the size is reached logs are dropped.” |
| BatchSpanProcessor | 2048 | 512 | 5000 ms | 30000 ms | “After the size is reached, spans are dropped.” |
| PeriodicMetricReader | — | 默认不限 | 60000 ms | 30000 ms | — |

来源：
- Logs SDK：https://opentelemetry.io/docs/specs/otel/logs/sdk/
- Trace SDK：https://opentelemetry.io/docs/specs/otel/trace/sdk/
- Metrics SDK：https://opentelemetry.io/docs/specs/otel/metrics/sdk/

这组数字说明官方成熟实现的选择：**有界队列 + 定时批量 + 队列满丢弃**，而不是无限缓存或每条立即落盘/上报。

#### 1.3.2 OpenTelemetry Android 的采集语义（B）

> “All instrumentations are enabled by default.”
> “Batch processing is enabled by default to reduce network calls and battery consumption.”
> “Disk persistence to buffer telemetry data when the device is offline, ensuring no data loss during network outages.”
> “Disk persistence is enabled by default to handle intermittent connectivity.”
> — https://opentelemetry.io/docs/platforms/client-apps/android/

同时，该页只给出 session 的 `backgroundInactivityTimeout` / `maxLifetime` 配置示例，**没有给出磁盘缓冲上限、批量大小、重试策略的数值（U）**。这意味着：

- 采用 OTel Android 时，**必须显式关闭默认 instrumentation 和 exporter，直到用户同意**；否则“装上就开始采”会直接撞上中国法下的“未经同意收集”。
- 若使用其磁盘持久化，必须自己加容量上限和清理策略；官方页面没有承诺有界。

#### 1.3.3 Firebase Performance 的采集语义（B）

> “添加 Performance Monitoring SDK 后，Firebase 会自动开始收集应用的界面渲染数据以及与应用生命周期相关的数据（例如应用启动时间）。”
> “Performance Monitoring SDK 在本地对事件进行批处理，然后定期（每 30 秒）将事件发送到 Firebase。”
> — https://firebase.google.cn/docs/perf-mon/get-started-android?hl=zh-cn

Firebase 文档也提供构建期/运行期停用路径：
- https://firebase.google.cn/docs/perf-mon/disable-sdk?hl=zh-cn

但需要注意：

- “自动开始收集”是默认行为；在中国法域下，**默认开启的第三方性能 SDK 本身就是风险**（见 §1.4 的《App违法违规收集使用个人信息行为认定方法》第三条第 1、4 项）。
- 该停用页本次抓取到的正文以 iOS `Info.plist` 的 `firebase_performance_collection_enabled` 为例；Android 对应的 manifest meta-data 键名未在该页正文出现（**U**，需以 Android 页/实际合并清单核对）。
- 若使用 Firebase，还涉及数据出境（PIPL 第 38/39 条）与第三方 SDK 的“向他人提供”定性。对离线教育 App，最稳妥是**不接默认开启的第三方分析 SDK**。

#### 1.3.4 Android 官方的调度原语：WorkManager（B）

WorkManager 文档给出：

- `Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)` / `BatteryNotLow`；
- `setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)`；
- 默认退避策略是 `EXPONENTIAL`，延迟 30 秒；
- `Result.retry()` 触发重试。
- 来源：https://developer.android.google.cn/topic/libraries/architecture/workmanager/how-to/define-work

这适合“同意后、有网、非低电量”的批量上传；不适合在 `Application.onCreate` 里直接起线程上传。

#### 1.3.5 本仓库已有的环形缓冲先例（C）

`CrashLedger` 是现成的、可复用的本地有界账本模式：

- 三重封顶：文件数 6 × 单文件 64 KiB × 总条目 300；超限丢最旧；
- 追加写 JSONL；读时跳过崩溃中断的半行；
- 放 `filesDir/diagnostics/`，不放 `cacheDir`（cache 可被系统清理）；
- 不进 Room/DataStore/备份域；零外发，导出由用户经 SAF 显式选择目标。
- 证据：`app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics/CrashLedger.kt:7-8,154-156`；`app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics/DiagnosticsBundleExporter.kt:26`。

**复用建议（D）**：遥测缓冲沿用“有界文件 + 丢最旧 + 半行容错”，但不要照搬“每条事件立即 `appendText`”。遥测应先在内存做**定长聚合**（计数器/直方图桶），再按批写快照；否则高频阶段事件会造成频繁 IO。

#### 1.3.6 避免内存膨胀 / 频繁 IO 的工程模式（D，基于 A/B/C）

1. **有界内存聚合**：固定大小的计数器数组 + 固定桶数组；不要用 `MutableList<Event>` 无界收集。
2. **固定基数**：维度只允许枚举（stage 8 个、outcome 4 个、subject 9 个、trigger 3 个）；禁止把题面、节点 ID、文件名当标签。
3. **批量落盘**：达到 N 条或 T 秒，或 App 进入后台时，写一次快照；不要每条 `fsync`。
4. **原子替换**：写临时文件后 `rename`，避免半写文件；读取时像 `CrashLedger` 一样跳过半行。
5. **队列满即丢**：生产者用 `tryOffer`，绝不阻塞主线程；丢弃计数本身要作为指标（`telemetry_dropped_total`）。
6. **上传走 WorkManager**：唯一工作名、网络/电量约束、指数退避；撤回同意时 `cancelUniqueWork`。
7. **采样**：正常会话按比例采样；错误/空命中/回退全采；按批随机，不持久化设备级 salt。
8. **严格禁止主线程 IO/网络**：debug 用 `StrictMode` 把主线程磁盘/网络暴露出来。

---

### 1.4 中国法域合规：逐条条文与出处

#### 1.4.1 《个人信息保护法》（PIPL，2021-11-01 施行）

全文（官方）：http://www.npc.gov.cn/npc/c2/c30834/202108/t20210820_313088.html
国家法律法规数据库：https://flk.npc.gov.cn/detail?id=ff8081817b6472a3017b656cc2040044

> 说明：截至 2026-11-02，`gov.cn` 上常见的 PIPL 旧链接（`/xinwen/2021-08/20/content_5632486.htm`）实测 404；以中国人大网/国家法律法规数据库为准。用户要求的“gov.cn 原文链接”在 PIPL 上未能取得，特此标注。

| 条文 | 原文要点（节选） | 对遥测的含义 |
|---|---|---|
| 第 4 条 | “个人信息是以电子或者其他方式记录的与已识别或者可识别的自然人有关的各种信息，不包括匿名化处理后的信息。” | 能关联到用户/设备/会话的遥测就是个人信息；只有真正匿名化才排除。 |
| 第 5、6 条 | “合法、正当、必要和诚信”；“收集个人信息，应当限于实现处理目的的最小范围，不得过度收集个人信息。” | 只采与“定位慢阶段/空结果”直接相关的字段；不采题面、图片、账号、设备标识。 |
| 第 13 条 | 处理个人信息的合法性基础：同意；合同必需；法定义务；公共卫生/紧急情况；新闻报道/舆论监督；已公开信息；其他法定情形。 | “改善产品质量”不在合同必需之列；PIPL 没有 GDPR 式“合法利益”独立基础（D），因此**不能靠合法利益默认采集**。 |
| 第 14 条 | “该同意应当由个人在充分知情的前提下自愿、明确作出。法律、行政法规规定处理个人信息应当取得个人单独同意或者书面同意的，从其规定。”；“处理目的、处理方式和处理的个人信息种类发生变更的，应当重新取得个人同意。” | 同意必须是主动、明确、可撤回；变更目的/类型/方式要重新同意。 |
| 第 15 条 | “个人有权撤回其同意。个人信息处理者应当提供便捷的撤回同意的方式。” | 设置页必须有一键停止；撤回后立即停采。 |
| 第 16 条 | “不得以个人不同意处理其个人信息或者撤回同意为由，拒绝提供产品或者服务；处理个人信息属于提供产品或者服务所必需的除外。” | 遥测不是离线知识库的“必需”，拒绝/撤回不得降级基本功能。 |
| 第 17 条 | 处理前以显著方式、清晰易懂语言告知：处理者名称/联系方式；处理目的、方式、种类、保存期限；权利行使方式；其他法定事项。 | 同意页必须写清“采什么、为什么、存多久、给谁、怎么撤回”。 |
| 第 19 条 | “个人信息的保存期限应当为实现处理目的所必要的最短时间。” | 遥测原始数据按天/周级保留，不能“先留着再说”。 |
| 第 23 条 | 向其他个人信息处理者提供时，告知接收方名称/联系方式/目的/方式/种类，并取得**单独同意**。 | 若接第三方分析 SDK/云厂商由其独立决定用途，需要单独同意；委托处理则走第 21 条合同+监督。 |
| 第 28 条 | 敏感个人信息包括“不满十四周岁未成年人的个人信息”；只有特定目的、充分必要性、严格保护措施方可处理。 | 教育 App 一旦触达 <14，遥测即敏感个人信息，门槛最高。 |
| 第 29 条 | “处理敏感个人信息应当取得个人的单独同意”；法律、行政法规要求书面同意的从其规定。 | <14 场景不能把遥测塞进隐私政策一揽子同意。 |
| 第 31 条 | 处理 <14 未成年人个人信息，应取得父母或其他监护人同意；应制定专门的个人信息处理规则。 | 需要监护人同意流程 + 专门规则；仅“学生点同意”不够。 |
| 第 39 条 | 向境外提供个人信息，告知境外接收方并取得**单独同意**。 | 用境外 Firebase/Google 端点会触发；建议境内自建端点。 |
| 第 47 条 | 个人撤回同意等情形，应主动删除；未删除的个人有权请求删除。 | 撤回后本地缓冲、服务端批次都要删。 |
| 第 51 条 | 采取加密、去标识化等安全技术措施；分类管理；权限控制。 | 传输 TLS、静态加密/沙箱、服务端访问控制；去标识化后仍是个人信息，不能当作匿名。 |
| 第 55、56 条 | 处理敏感个人信息、委托/提供/公开、跨境、自动化决策等，应事前做个人信息保护影响评估（PIPIA）；评估报告和处理情况记录至少保存三年。 | 触达未成年人/使用云处理器 → 先做 PIPIA，报告留 3 年。 |

#### 1.4.2 《生成式人工智能服务管理暂行办法》（2023-08-15 施行）

全文（官方）：https://www.cac.gov.cn/2023-07/13/c_1690898327029107.htm

| 条文 | 原文要点（节选） | 对遥测的含义 |
|---|---|---|
| 第 2 条 | 适用于“利用生成式人工智能技术向中华人民共和国境内公众提供生成文本、图片、音频、视频等内容的服务”。 | 离线 App 若内置生成式讲题/生图并对公众提供服务，可能落入“提供者”；仅作为用户 BYOK 客户端是否构成“提供者”存在解释空间（**U**，需法律意见）。 |
| 第 9 条 | 提供者“涉及个人信息的，依法承担个人信息处理者责任，履行个人信息保护义务”。 | 即使不是典型“生成式 AI 服务”，PIPL 仍然适用；第 9 条只是重申。 |
| 第 10 条 | 明确适用人群、场合、用途；防范未成年人过度依赖或沉迷。 | 教育 App 的 AI 讲题功能要防沉迷；遥测不应成为“延长使用”的优化工具。 |
| 第 11 条 | “不得收集非必要个人信息，不得非法留存能够识别使用者身份的输入信息和使用记录，不得非法向他人提供使用者的输入信息和使用记录。” | **直接约束“使用记录”**：题面/对话/操作日志不能以“分析”为名无限留存；遥测只能采非识别性的聚合指标。 |
| 第 12 条 | 按《互联网信息服务深度合成管理规定》对图片、视频等生成内容进行标识。 | 生成内容标识是另一条义务；不能把标识元数据当遥测素材。 |

#### 1.4.3 《人工智能生成合成内容标识办法》（2025-09-01 施行）

全文（官方）：https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm
国务院部门文件：https://www.gov.cn/zhengce/zhengceku/202503/content_7014286.htm

| 条文 | 原文要点（节选） | 对遥测的含义 |
|---|---|---|
| 第 2 条 | 适用于符合算法推荐、深度合成、生成式 AI 规定情形的“网络信息服务提供者”。 | 先判断 App 是否属于该范围；属于则标识义务适用。 |
| 第 3 条 | 标识包括显式标识与隐式标识；隐式标识是“采取技术措施在生成合成内容文件数据中添加的，不易被用户明显感知到的标识”。 | 隐式标识是内容文件元数据，不是用户行为遥测。 |
| 第 5 条 | 在生成合成内容的文件元数据中添加隐式标识，包含生成合成内容属性信息、服务提供者名称/编码、内容编号等制作要素。 | 这些字段会随导出文件离开设备；若把它们上传到分析后端，可能与具体内容/用户关联，应按个人信息处理。 |
| 第 8 条 | 在用户服务协议中说明标识方法、样式等规范，提示用户理解标识管理要求。 | 需要产品文案与协议更新；与遥测同意分开。 |
| 第 9 条 | 用户申请提供没有显式标识的生成内容时，可在协议明确标识义务后提供，并“依法留存提供对象信息等相关日志不少于六个月”。 | **这是特定场景的日志留存义务，不是通用遥测授权**；不能拿它当“可以多采”的依据。 |
| 第 14 条 | 自 2025-09-01 起施行。 | 时间点明确。 |

#### 1.4.4 支撑性规则与标准

| 文件 | 关键条文/要点 | 出处 |
|---|---|---|
| 《App违法违规收集使用个人信息行为认定方法》（2019） | 三.1“征得用户同意前就开始收集个人信息或打开可收集个人信息的权限”；三.4“以默认选择同意隐私政策等非明示方式征求用户同意”；三.8“未向用户提供撤回同意收集个人信息的途径、方式”；四.2“因用户不同意收集非必要个人信息或打开非必要权限，拒绝提供业务功能”；四.5“仅以改善服务质量、提升用户体验、定向推送信息、研发新产品等为由，强制要求用户同意收集个人信息” | https://www.cac.gov.cn/2019-12/27/c_1578986455686625.htm |
| 《常见类型移动互联网应用程序必要个人信息范围规定》（2021-05-01 施行） | 第 4 条“App 不得因为用户不同意提供非必要个人信息，而拒绝用户使用其基本功能服务”；第 5 条（十九）学习教育类：基本功能“在线辅导、网络课堂等”，必要个人信息为“注册用户移动电话号码” | https://www.gov.cn/zhengce/zhengceku/2021-03/23/content_5595088.htm |
| GB/T 35273—2020《信息安全技术 个人信息安全规范》 | 3.6 明示同意 = 主动勾选/点击“同意”等肯定性动作；5.2 最小必要；5.3 b) 以肯定性动作作为开启条件，开启后才收集；5.3 c) 关闭/退出与开启同样方便；5.3 e) 不得因拒绝而暂停其他功能或降低质量；5.3 f) 不得仅以改善质量/体验/研发新产品为由强制同意；5.4 d) 14+ 未成年人需本人或监护人明示同意，<14 需监护人明示同意；6.1 存储时间最小化；附录 A 将“个人上网记录”“个人常用设备信息”列为个人信息示例；附录 B 将 14 岁以下儿童个人信息列为敏感信息 | 官方状态页（现行，2020-03-06 发布，2020-10-01 实施）：https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=4568F276E0F8346EB0FBA097AA0CE05E |
| 《网络安全法》（2016，2025 修正，2026-01-01 施行） | 旧法第 21 条第（三）项：“采取监测、记录网络运行状态、网络安全事件的技术措施，并按照规定留存相关的网络日志不少于六个月”；旧法第 41 条：收集使用个人信息应合法、正当、必要，明示目的/方式/范围并经被收集者同意；旧法第 42 条：未经同意不得向他人提供，匿名化且不能复原的除外 | 旧法全文：https://www.cac.gov.cn/2016-11/07/c_1119867116.htm ；2025 修正决定：https://www.gov.cn/yaowen/liebiao/202510/content_7046194.htm |
| 《儿童个人信息网络保护规定》（2019-10-01 施行） | 第 2 条儿童 = 不满 14 周岁；第 8 条专门规则 + 指定专人；第 9 条显著清晰告知监护人并征得监护人同意；第 10 条提供拒绝选项并告知目的/方式/范围、存储地点/期限/到期处理、安全措施、拒绝后果、投诉渠道、更正删除途径；第 11 条不得收集无关信息；第 12 条不得超期存储；第 13 条加密存储；第 15 条最小授权、访问审批与记录；第 20 条监护人撤回同意等应删除 | https://www.cac.gov.cn/2019-08/23/c_1124913903.htm ；https://www.gov.cn/zhengce/zhengceku/2019-08/22/content_5458118.htm |
| 《未成年人网络保护条例》（2024-01-01 施行） | 第 28 条 以未成年人为服务对象的在线教育产品应按年龄阶段和认知能力提供；第 32 条 不得强制未成年人/监护人同意非必要个人信息处理，不得因不同意/撤回而拒绝基本功能；第 34 条 查阅/复制/更正/删除的便捷途径；第 35 条 泄露/篡改/丢失时告知未成年人及其监护人；第 36 条 最小授权、访问审批、访问记录；第 37 条 每年对处理未成年人个人信息合规情况做审计并报告 | https://www.gov.cn/zhengce/content/202310/content_6911288.htm |

**合规结论（D）**：

1. 遥测若关联到用户/设备/会话，属于个人信息；“设备信息”不是法外之地。
2. “改善产品质量/体验”不能作为强制同意或默认开启的理由（认定方法四.5、GB/T 35273 5.3 f）。
3. 默认开启 + 先采后同意，直接命中认定方法三.1/三.4。
4. 拒绝/撤回不得导致基本功能不可用（PIPL 16、认定方法四.2、必要信息规定第 4 条、未成年人条例第 32 条）。
5. 教育 App 若有账号，必要个人信息最多是注册手机号；离线无账号则连这项都不需要。遥测永远不是“必要”。
6. <14 场景：敏感个人信息 + 监护人同意 + 单独同意 + 专门规则 + PIPIA；14–17 场景仍受未成年人条例约束。
7. 跨境（Firebase/Google/境外云）触发 PIPL 38/39；能境内自建就境内自建。

---

### 1.5 教育类 App 与未成年人的额外约束

| 约束 | 具体内容 | 对埋点的直接影响 |
|---|---|---|
| 未成年人条例第 32 条 | 不得强制未成年人或其监护人同意非必要的个人信息处理；不得因不同意或撤回而拒绝基本功能 | 遥测开关必须“暂不”可用；拒绝后所有学习功能照常。 |
| 未成年人条例第 36 条 | 工作人员访问未成年人个人信息以最小授权为原则，审批 + 记录访问情况 | 若遥测含未成年人个人信息，服务端也要有审批/审计；更好的做法是根本不上传可识别数据。 |
| 未成年人条例第 37 条 | 每年对处理未成年人个人信息合规情况做审计并报告网信等部门 | 遥测纳入年度合规审计范围；保留审计证据。 |
| 未成年人条例第 28 条 | 在线教育产品按不同年龄阶段身心发展特点和认知能力提供 | 埋点不能用于“诱导使用/延长停留”；指标设计避免成瘾性优化。 |
| PIPL 第 28/29/31 条 | <14 个人信息属敏感信息；单独同意；监护人同意；专门处理规则 | 年龄门 + 监护人同意 + 单独开关；不能与隐私政策一揽子勾选。 |
| 儿童规定第 9/10 条 | 监护人同意；提供拒绝选项；告知存储地点/期限/到期处理/安全措施/拒绝后果/投诉/更正删除 | 同意页要逐项写清；拒绝要有同等显著入口。 |
| 生成式 AI 办法第 10 条 | 防范未成年人过度依赖或沉迷 | 不把“使用时长/频次”作为增长指标；遥测不服务于成瘾性实验。 |
| 必要信息规定（十九） | 学习教育类必要个人信息 = 注册用户移动电话号码 | 若产品无账号体系，则无必要个人信息；遥测不在必要范围。 |

**工程含义（D）**：教育 App 的最稳做法不是“给未成年人也做一个开关”，而是**默认对所有用户关闭；只有明确成年/监护人确认后才允许上传**；如果产品无法可靠区分年龄，v1 干脆只做“本机指标面板”，不做出境/出端上报。

---

### 1.6 哪些字段属于个人信息，哪些只是设备信息

#### 1.6.1 法律定义

- PIPL 第 4 条：个人信息 = 与已识别或可识别的自然人有关的各种信息；不包括匿名化后的信息。
- PIPL 第 73 条：
  - （三）去标识化：处理后，不借助额外信息无法识别特定自然人——**仍然是个人信息**；
  - （四）匿名化：处理后无法识别特定自然人且不能复原——**才不是个人信息**。
- GB/T 35273—2020 附录 A 明确把以下列为个人信息示例：
  - **个人上网记录**：“指通过日志储存的个人信息主体操作记录，包括网站浏览记录、软件使用记录、点击记录、收藏列表等”；
  - **个人常用设备信息**：“指包括硬件序列号、设备 MAC 地址、软件列表、唯一设备识别码（如 IMEI/Android ID/IDFA/OpenUDID/GUID/SIM 卡 IMSI 信息等）等在内的描述个人常用设备基本情况的信息”。
- GB/T 35273 附录 B：14 岁以下（含）儿童的个人信息属于个人敏感信息。

#### 1.6.2 分类表

| 类别 | 例子 | 法律地位 | 遥测是否允许 |
|---|---|---|---|
| 直接标识 | 姓名、手机号、邮箱、学号、账号 ID、身份证号 | 个人信息；部分敏感 | 禁止 |
| 设备标识 | Android ID、IMEI、MEID、MAC、序列号、OAID/IDFA、广告 ID、SIM IMSI | GB/T 35273 明列为“个人常用设备信息” | 禁止 |
| 网络标识 | IP、SSID/BSSID、精确位置、运营商 | 个人信息；位置可能敏感 | 禁止/至少不采 |
| 使用日志 | 查询时间、阶段耗时、点击/功能入口、空结果、学科、知识点 ID、错题 ID | 能关联到用户/会话/设备时是“个人上网记录” | 只允许聚合、去标识、无 ID、粗时间桶 |
| 设备概况 | OS API、RAM 档位、locale 大类 | 单独看不一定可识别；与其他字段组合可能可识别 | 谨慎，最小化；不上传机型/厂商 |
| 聚合统计 | 某版本某阶段的计数/直方图，单元格 ≥k，无 ID、无精确时间 | 可能构成匿名化；但小样本仍可能重识别 | 需匿名化评估 + k-匿名；否则按个人信息处理 |
| 敏感信息 | <14 儿童任何个人信息、生物识别、健康、金融、精确位置 | 敏感个人信息 | 原则上不采；确需则单独同意 + PIPIA + 严格保护 |

**关键结论（D）**：不存在“设备信息所以不受管”的豁免。GB/T 35273 把唯一设备识别码、MAC、软件列表直接列为个人信息示例。**“匿名化”是高门槛**：去标识化后的数据仍是个人信息；只有无法识别且不能复原，才落入 PIPL 第 4 条的除外。对用户量不大的教育 App，聚合单元格很容易小到可重识别，因此本文建议**即使聚合也按个人信息对待，走同意**。

---

## 2. 指标 schema 提案

### 2.1 设计原则

1. **端侧聚合，不上传原始事件**：只上传计数器/直方图/仪表盘的聚合快照。
2. **无持久标识**：不采 Android ID/IMEI/MAC/OAID/广告 ID；`batch_id` 每批随机，批次之间不可关联。
3. **无自由文本**：所有维度都是枚举；禁止题面、OCR 文本、文件名、节点名称、错题 ID、知识代号。
4. **粗时间**：只保留 1 小时窗口，不上传毫秒时间戳。
5. **有界基数**：单指标维度组合 ≤ 约 2000；小单元格 k-匿名后合并/丢弃。
6. **本地毫秒、导出秒**：本地 schema 用 `ms` 避免浮点；对接 OTel 时转 `s` 并用 `*_seconds` 命名。
7. **schema 版本化**：`schema=1`；只做加法，服务端拒绝未知主版本。
8. **同意版本化**：每批带 `policy_version`/`policy_hash`，证明该批落在哪次同意范围内。

### 2.2 批次 JSON（示意）

```json
{
  "schema": 1,
  "batch_id": "b_9f1c2d7e-4a6b-4c2e-9f10-2b7d8e1a3c55",
  "window": { "start_utc": "2026-11-02T10:00:00Z", "end_utc": "2026-11-02T11:00:00Z" },
  "app": { "version_name": "1.2.3", "version_code": 123, "flavor": "localFirst" },
  "env": { "api": 34, "ram_class": "mid" },
  "consent": { "policy_version": "2026-11-01", "policy_hash": "sha256:...", "scope": "product_metrics" },
  "sampling": { "rate": 0.1 },
  "metrics": [
    {
      "name": "kb_stage_duration_ms",
      "type": "histogram",
      "unit": "ms",
      "bounds": [0, 1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000],
      "counts": [12, 7, 5, 9, 4, 2, 1, 1, 1, 0, 0, 0, 0, 0, 0],
      "count": 42,
      "sum": 1234,
      "attrs": { "stage": "sql_recall", "outcome": "ok", "cold_start": true, "subject": "math" }
    },
    {
      "name": "kb_query_total",
      "type": "counter",
      "unit": "count",
      "value": 180,
      "attrs": { "trigger": "tutor", "outcome": "hit", "kb_ready": true }
    },
    {
      "name": "kb_catalog_coverage_ratio",
      "type": "gauge",
      "unit": "ratio",
      "value": 1.0,
      "numerator": 9,
      "denominator": 9,
      "attrs": { "layer": "catalog_subjects", "baseline_id": "moe-high-school-2017-2025" }
    }
  ]
}
```

字段约束：

| 字段 | 类型 | 必填 | 允许值/说明 |
|---|---|---|---|
| `schema` | int | 是 | 当前 1 |
| `batch_id` | string | 是 | UUIDv4；**不是**设备 ID，不跨批复用 |
| `window.start_utc/end_utc` | string | 是 | ISO8601 UTC；只到小时 |
| `app.version_name` | string | 是 | 版本名 |
| `app.version_code` | long | 是 | 版本号 |
| `app.flavor` | enum | 是 | `strictOffline` / `localFirst` |
| `env.api` | int | 是 | Android API level |
| `env.ram_class` | enum | 否 | `low` / `mid` / `high`；不传机型/厂商/内存精确值 |
| `consent.policy_version` | string | 是 | 同意时展示的隐私/遥测政策版本 |
| `consent.policy_hash` | string | 是 | 政策文本哈希，防“同意的是旧版” |
| `consent.scope` | enum | 是 | `product_metrics`（未来可扩 `crash_upload`，但必须独立同意） |
| `sampling.rate` | float | 是 | 0–1；本批采样率 |
| `metrics[].name` | enum | 是 | 见 §2.3 |
| `metrics[].type` | enum | 是 | `counter` / `histogram` / `gauge` |
| `metrics[].unit` | enum | 是 | `ms` / `bytes` / `count` / `ratio` |
| `metrics[].bounds` | int[] | histogram 必填 | 严格递增；≤15 个 |
| `metrics[].counts` | long[] | histogram 必填 | 长度 = bounds+1 |
| `metrics[].count/sum` | long/double | histogram 必填 | count = Σcounts |
| `metrics[].value` | double | counter/gauge 必填 | — |
| `metrics[].numerator` | long | 覆盖率 gauge 推荐 | 分子；便于聚合，避免只存比例 |
| `metrics[].denominator` | long | 覆盖率 gauge 推荐 | 分母；与 numerator 同口径 |
| `metrics[].attrs` | object | 是 | 只允许枚举键/值；不允许自由文本 |

### 2.3 指标目录

| 指标名 | 类型 | 单位 | 维度 | 定义 | 对应信号 |
|---|---|---|---|---|---|
| `kb_stage_duration_ms` | histogram | ms | `stage`, `outcome`, `cold_start`, `subject`, `trigger` | 单个检索阶段的耗时；`stage` ∈ feature_extract / sql_recall / dense_encode / dense_search / rerank / prereq_hop / material_load / render；`outcome` ∈ ok / empty / error / cancelled | Latency |
| `kb_first_query_duration_ms` | histogram | ms | `cold_start`, `kb_ready`, `trigger` | 用户动作被接受 → 首个结果渲染完成；每个进程只记首次真实检索；不含知识库安装/更新、不含模型网络时间 | Latency |
| `kb_query_total` | counter | count | `trigger`, `outcome`, `subject`, `kb_ready` | 进入真实检索的查询数；排除 `confirmed_binding`、空题面、未就绪、加载失败（这些进 `kb_query_skipped_total`） | Traffic |
| `kb_query_no_relevant_hit_total` | counter | count | `subject`, `trigger` | 召回候选非空，但精排 `rankedAtoms` 为空（回退主题之前）；对应 `KnowledgeContextRetriever.kt:73` | Errors（隐式） |
| `kb_query_fallback_total` | counter | count | `fallback`, `trigger` | 精排零命中后回退到主题；`fallback=topic` | Errors（隐式/降级） |
| `kb_query_empty_context_total` | counter | count | `reason`, `trigger` | 最终上下文为空；`reason` ∈ no_features / no_candidates / kb_not_ready / load_failed | Errors |
| `kb_query_skipped_total` | counter | count | `reason`, `trigger` | 未进入检索的查询；`reason` ∈ no_features / kb_not_ready / load_failed / cancelled | Traffic/质量 |
| `kb_query_error_total` | counter | count | `stage`, `error_class`, `retryable` | 检索阶段异常；`error_class` 用有界枚举（如 `SQLITE` / `IO` / `DENSE_MODEL` / `PARSE` / `UNKNOWN`） | Errors |
| `kb_recall_candidate_count` | histogram | count | `subject`, `trigger` | 512 召回后的候选数；容量/饱和度代理 | Saturation |
| `kb_catalog_coverage_ratio` | gauge | ratio | `layer`, `baseline_id` | 有已审核原子节点的学科数 / 9；`layer=catalog_subjects` | Coverage |
| `kb_teaching_coverage_ratio` | gauge | ratio | `layer`, `baseline_id` | 有绑定教学材料的原子节点数 / 原子节点总数；`layer=teaching_nodes` | Coverage |
| `kb_index_ready` | gauge | 0/1 | `baseline_id` | 知识库是否 Ready | Saturation/Readiness |
| `kb_db_size_bytes` | gauge | bytes | 无 | 知识库/学习数据库文件大小 | Saturation |
| `device_free_storage_bytes` | gauge | bytes | 无 | 应用私有存储可用空间（粗档位也可） | Saturation |
| `process_anon_rss_bytes` | gauge | bytes | 无 | 进程 Anonymous RSS + Swap（若可读；读不到就不报） | Saturation |
| `telemetry_buffer_bytes` | gauge | bytes | 无 | 遥测缓冲占用 | 自观测 |
| `telemetry_buffer_entries` | gauge | count | 无 | 遥测缓冲条目数 | 自观测 |
| `telemetry_dropped_total` | counter | count | `reason` | 队列满/超限丢弃；`reason` ∈ queue_full / disk_cap / sampling | 自观测 |
| `telemetry_upload_total` | counter | count | `outcome`, `http_class` | 上传结果；`outcome` ∈ ok / retry / drop / disabled | 自观测 |
| `telemetry_upload_duration_ms` | histogram | ms | `http_class` | 上传耗时 | 自观测 |

**“覆盖率”必须区分两层（D/C）**：

- **内容覆盖率**是静态声明，不是查询结果。仓库已有 `KnowledgePackCoverage`/`KnowledgeCoverageContract`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgePackCoverage.kt:47-52`），其中 `catalogLevel`/`teachingSupportLevel` 与 `fullCoverageReady` 是审核后的声明；指标应上报“分子/分母”，不要只报一个无法聚合的比例。
- **检索覆盖率**是运行时的：`queries with ≥1 candidate / queries`。它只能由 `kb_query_total` 与 `kb_query_no_relevant_hit_total` 派生，不建议单独存 gauge，否则口径会和分母漂移。

**“空结果率”必须按回退前口径（C）**：

- 直接统计最终 `candidateNodeIds.isEmpty()` 会漏掉“精排零命中但回退主题”的情况；应同时上报 `kb_query_no_relevant_hit_total` 与 `kb_query_fallback_total`。
- 分母 `kb_query_total` 只含“题面特征非空 + KB Ready + 非 confirmed_binding”的查询；其余进 `kb_query_skipped_total`，否则空结果率会被“没检索”稀释。

### 2.4 桶边界提案

| 指标 | 边界（ms 或 count） | 理由 |
|---|---|---|
| `kb_stage_duration_ms` | `[0, 1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000]` | 本地 SQLite/JS/稠密腿会出现亚毫秒到几十毫秒；OTel 默认低端太粗；约 2–3 倍递增，含 100/500/1000/5000 等 SLO 常用点 |
| `kb_first_query_duration_ms` | `[0, 50, 100, 250, 500, 1000, 2000, 3000, 5000, 8000, 10000, 15000, 30000]` | 覆盖 TTID/TTFD 常见区间；首查可能触发索引打开/模型加载；上限 30 s 便于发现“卡死” |
| `kb_recall_candidate_count` | `[0, 1, 5, 10, 20, 50, 100, 200, 512]` | 512 是当前召回上限；观察候选规模是否异常膨胀/过窄 |
| BYOK/网络延迟（若未来采） | `[0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10]`（秒） | 直接采用 OTel HTTP semconv 推荐桶 |
| 上传耗时 | `[0, 50, 100, 250, 500, 1000, 2000, 5000, 10000, 30000]` | 移动网络抖动大；30 s 对齐 OTel 导出超时 |

若未来接入 Prometheus classic histogram，必须把 SLO 边界（例如 300 ms、1 s）精确加入；Prometheus 文档明确指出边界不存在时不会插值。

### 2.5 保留期提案

| 数据 | 本地 | 服务端 | 依据/说明 |
|---|---|---|---|
| 聚合缓冲 | 7 天或 10 MiB，先到先删 | 原始批次 30 天 | PIPL 19 最短必要；7 天足够覆盖离线/重试 |
| 聚合后指标 | 30 天 | 90 天 | 只保留聚合，不含 ID；到期删除或再匿名化 |
| 同意记录/政策哈希 | 撤回后 3 年 | 3 年 | PIPL 56 处理情况记录至少 3 年；记录本身只含时间/版本/动作 |
| PIPIA 报告 | 3 年 | 3 年 | PIPL 56 |
| 崩溃/ANR 诊断账本 | 300 条 / 384 KiB（现状） | 不上传；仅用户手动导出 | 复用 `CrashLedger`；与产品遥测分开 |
| 未成年人遥测 | 7 天原始 / 30 天聚合 | 30 天 | 更短；无持久 ID；不做跨会话关联 |
| 服务端安全日志 | — | 6 个月（如适用） | 《网络安全法》旧法 21(3) 的网络日志留存；**与产品遥测分开**，不能拿它当分析留存依据 |
| 备份 | 不进入用户备份 | 不适用 | 遥测不是学生数据；删除全部数据时应一并删除 |

### 2.6 采样、基数与 k-匿名

- 正常会话采样 10%；`error` / `no_relevant_hit` / `fallback` / `first_query` 100% 采样。
- 每会话每日最多 200 条指标、每小时最多 1 批、每批 ≤50 KiB。
- 维度基数：subject 9 × stage 8 × outcome 4 × cold 2 × trigger 3 ≈ 1728，可接受；超过时把 `subject` 合并为 `other`。
- 上传前对每个 `(metric, attrs)` 单元格做 k-匿名：`count < 20` 的单元格丢弃或合并；小用户量产品可能永远达不到 k，此时**不要上传**，只保留本机面板。
- 采样用每批随机，不持久化设备级 salt；如果产品坚持会话级一致采样，salt 只存内存、撤回即清。

### 2.7 禁止字段清单

以下字段**不得**出现在遥测批次、日志、崩溃附件或服务端分析表中：

- 题面/ OCR 文本 / 对话内容 / 提示词 / 模型响应原文；
- 图片、文件名、文件路径、URI、ContentProvider 路径；
- 知识点 ID、知识代号、错题 ID、练习 ID、课程 ID（可反向定位学习内容）；
- 手机号、邮箱、姓名、学号、账号 ID、OpenID、设备昵称；
- Android ID、IMEI/MEID、MAC、序列号、OAID/IDFA/广告 ID、SIM IMSI、SSID/BSSID；
- 精确位置、IP（服务端也不记录原始 IP；如需风控，截断/哈希并单独告知）；
- 已安装应用列表、进程列表、通讯录、剪贴板；
- 任何自由文本 `message`/`extra`/`tags` 字段；
- 完整堆栈（崩溃堆栈走独立的本机诊断通道，默认不上传）。

---

## 3. 开关与同意门的设计要求

### 3.1 单一事实源与状态机

```
UNKNOWN --grant(policy)--> GRANTED --withdraw()--> WITHDRAWN --grant(policy)--> GRANTED
GRANTED --policyChanged()--> UNKNOWN   // 必须重新同意，旧同意失效
```

- `UNKNOWN` 是首次安装默认值，等价于 **OFF**；`WITHDRAWN` 也等价于 OFF，且不自动反复弹窗。
- 存储用枚举而不是 `Boolean enabled`：布尔字段一旦被某处默认写 `true`，很难审计；枚举的默认值只能是 `UNKNOWN`。
- 唯一写入口：`TelemetryConsentStore.grant()` / `withdraw()` / `invalidateForPolicyChange()`。其他类不得直接写 DataStore。
- 同意记录字段：`state`、`policy_version`、`policy_hash`、`granted_at`、`withdrawn_at`、`age_band`（可选）、`guardian_confirmed`（可选）。不要存学生身份信息。

### 3.2 收集门（fail-closed）

```kotlin
fun isCollectionAllowed(
    consent: TelemetryConsentState,
    build: TelemetryBuildConfig,
    hasInternetPermission: Boolean,
    currentPolicyHash: String,
): Boolean =
    consent is TelemetryConsentState.Granted &&
        build.telemetryBuildEnabled &&
        hasInternetPermission &&
        consent.policyHash == currentPolicyHash &&
        consent.ageGateSatisfied
```

要求：

1. **先过门，再构造事件**：`TelemetryRecorder.record()` 第一行检查门；OFF 时直接 return，不分配对象、不拼字符串、不写文件。
2. **门是纯函数**：便于单元测试；不要在门里读网络、读数据库、读系统设置。
3. **任一层失败即 OFF**：同意存储读失败、政策哈希不匹配、构建开关关闭、无 INTERNET 权限、年龄门未满足，全部按 OFF 处理；不得“默认放行”。
4. **无隐式初始化**：不得在 `Application.onCreate` 里初始化任何会采集的 SDK；不得用 `ContentProvider`/`androidx.startup` 自动初始化遥测；首个 `record()` 被调用且门通过后才懒加载。
5. **无预同意副作用**：同意前不得创建缓冲文件、不得 enqueue WorkManager、不得发任何网络请求（包括配置下发/远程开关）。

### 3.3 同意界面要求

- **独立入口**：设置 > 隐私 > “发送匿名使用统计”；首次可在非阻断的卡片/引导页出现，但不能挡住主流程。
- **肯定性动作**：默认无勾选；用户主动点“允许”才进入 GRANTED；“暂不”同等显著，进入主界面。
- **逐项告知**（对齐 PIPL 17、儿童规定 10）：
  - 采什么：阶段耗时、查询次数、空结果/回退次数、覆盖率、版本/OS/RAM 档位；
  - 为什么：定位慢阶段、发现检索空结果、评估知识库覆盖；
  - 存哪里/多久：境内端点、原始 30 天/聚合 90 天；
  - 给谁：不向第三方提供；云厂商仅作为受托处理者（如适用）；
  - 怎么撤回：设置里一键停止，立即生效；
  - 拒绝后果：不影响任何学习功能；
  - 联系方式/投诉渠道；
  - 可查看“将发送的示例数据”。
- **不得捆绑**：不能把遥测同意塞进隐私政策“同意并继续”、模型配置、账号注册或首次启动的一揽子勾选。仓库里 `ModelAgentConsentStore` 被删除、改成“配置模型 = 全局同意”的历史（`docs/design/tutor-agent-consent-design.md:21`）是一个明确警示：**配置行为不等于对遥测的同意**。
- **政策变更**：目的/类型/接收方/保留期任一实质变化，`state` 回到 `UNKNOWN`，重新展示同意；旧 `policy_hash` 的批次服务端应拒绝。

### 3.4 未成年人

- 首次同意前问一个**最小年龄门**：`未满14` / `14–17` / `18+` / `不愿透露`。只存档位，不存生日。
- `未满14`：必须走监护人同意流程；没有监护人确认 → OFF。建议同时展示专门的儿童个人信息处理规则（PIPL 31、儿童规定 8/9/10）。
- `14–17`：本人同意 + 监护人告知；仍受未成年人条例约束；保守做法是同样要求监护人确认。
- `不愿透露`：按 OFF 处理（fail-closed），或仅保留本机面板。
- 若产品无法可靠实现年龄门/监护人确认，**v1 不做出端上报**，只做本机指标面板；这是最稳的合规路径。

### 3.5 撤回

- 设置页一键“停止发送使用统计”；点击后立即：
  1. `state = WITHDRAWN`；
  2. `TelemetryGate` 立即返回 false；
  3. `WorkManager.cancelUniqueWork("telemetry-upload")`；
  4. 删除本地缓冲文件/数据库表；
  5. 若服务端已收过批次，调用删除接口或登记待删 `batch_id`；
  6. 轮换上传令牌（如有）；
  7. 展示“已停止；已删除本机待发送数据”。
- 撤回的入口便利程度不低于开启（GB/T 35273 5.3 c）。
- 撤回后不得反复弹窗劝诱（认定方法三.2）。

### 3.6 构建与部署门

- `BuildConfig.TELEMETRY_BUILD_ENABLED` 默认 `false`；release 只有在显式 Gradle 属性 + 隐私评审通过时才置 true；CI 检查“属性存在但评审标记缺失”直接失败。
- `strictOffline` flavor 已在清单合并时移除 `INTERNET` 与 `ACCESS_NETWORK_STATE`（`app/src/strictOffline/AndroidManifest.xml:4-9`），因此该变体**结构上不可能上传**；这是比运行时开关更强的证据。
- `localFirst` 的遥测端点默认空字符串；未配置端点时门为 false。
- 不接默认开启的第三方分析/性能 SDK；如必须接，先证明其初始化可被延迟到同意之后，且无 `ContentProvider` 自动启动。
- 服务端：境内区域、TLS、静态加密、访问控制、最小权限、删除任务、保留期任务、受托处理合同（PIPL 21）；不记录原始 IP；不接受含禁止字段的批次。

### 3.7 与现有诊断通道的关系

- `DiagnosticsBootstrap`/`CrashLedger` 是**本机诊断**：崩溃、ANR、协程失败、系统退出对账；默认只落本机，导出由用户经 SAF 显式选择目标（`DiagnosticsBundleExporter.kt:26`）。
- 遥测是**另一条通道**：聚合指标、同意后上传。两者不得共用一个开关、一个缓冲区、一个上传 Worker。
- 崩溃堆栈可能含学生内容路径，绝不能因为“用户同意了产品遥测”就自动上传；如未来要做崩溃上传，必须单独同意、单独告知、单独保留期。
- `ModelCallFailureDiagnostics` 的“默认关 + 只随 debug 源集存在”是很好的**结构性默认关**先例（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/ModelCallFailureDiagnostics.kt:31-37`），但它是调试缝，不是用户同意机制；release 遥测需要单独的、用户可见的同意门。

### 3.8 数据流

```
生产点（检索/渲染/安装）
  -> TelemetryRecorder.record()  [先过 gate；OFF 即 return]
  -> 内存聚合器（定长计数器/直方图；有界队列）
  -> 阈值/后台 -> 追加快照到 filesDir/telemetry 环形缓冲（有界）
  -> WorkManager（唯一工作名；NetworkType + BatteryNotLow；指数退避）
  -> HTTPS 境内端点（批次；无 ID；k-匿名后）
  -> 服务端聚合/保留期任务

撤回 -> gate=false -> cancelUniqueWork -> 删除本地缓冲 -> 服务端删除请求
```

---

## 4. 可验证判据：怎样证明“默认关闭 + 同意后才上报”是真的

### 4.1 证据等级

| 等级 | 证据 | 能证明什么 |
|---|---|---|
| E1 | 代码审阅（`file:line`） | 默认值、唯一写入口、门的位置 |
| E2 | JVM 单元测试 | 纯逻辑：默认 OFF、同意开、撤回关、序列化白名单、缓冲上限 |
| E3 | Instrumented 测试 | DataStore 持久化、文件删除、WorkManager 取消、UI 流程 |
| E4 | 设备/网络抓包 | 同意前零请求、同意后只到允许端点、撤回后零请求 |
| E5 | 构建产物/合并清单 | `strictOffline` 无 INTERNET；无第三方 SDK provider |
| E6 | 审计文档 | 同意记录、PIPIA、保留期配置、服务端删除凭证 |

### 4.2 判据清单

| ID | 要求 | 代码级开关点 | 测试/命令 | 通过标准 |
|---|---|---|---|---|
| V1 | 首次安装默认 OFF | `TelemetryConsentStore` 默认 `UNKNOWN`；`TelemetryGate` 对非 `GRANTED` 返回 false | 单测 `freshInstall_defaultsToOff`；`grep -rn "TelemetryConsentState.Granted" app/src core` 只允许出现在 store 的 `grant()` 及其测试 | 默认状态为 `UNKNOWN`；gate=false |
| V2 | 同意前不产生任何采集副作用 | `TelemetryRecorder.record()` 门在事件构造之前；无 `Application.onCreate` 初始化；无 ContentProvider | 单测 fake sink 计数 0；E3 冷启动后检查 `filesDir/telemetry` 不存在/为空 | 0 条记录、0 字节、无文件 |
| V3 | 同意前零网络 | 无 WorkManager enqueue；无 SDK 初始化；端点未配置 | 代理抓包（mitmproxy/Charles）+ `adb shell settings put global http_proxy`；`dumpsys netstats` UID 流量对比 | 0 请求到遥测域；无遥测连接 |
| V4 | 同意后才开始采集 | `grant()` 后 gate=true；`record()` 进入缓冲 | 单测 `grant_enablesCollection`；E3 `grant()` 后 `record()` 有 1 条；E4 代理只看到允许端点 | 采集/上传仅在 grant 后发生 |
| V5 | 撤回立即停止并清理 | `withdraw()` 设置 `WITHDRAWN`；`cancelUniqueWork`；删除缓冲 | 单测 gate=false、sink 不增；E3 缓冲文件不存在、WorkInfo 全部 finished；E4 撤回后 0 请求 | 不再采集、不再上传、本地无残留 |
| V6 | 未成年人门 | `ageGateSatisfied`：<14 需 `guardianConfirmed`；未知按 false | 单测 `under14WithoutGuardian_isBlocked`；Compose UI 测试“暂不”进入主界面 | <14 无监护人 → 永不上传 |
| V7 | 数据最小化 | `TelemetryPayload` 是 sealed/固定字段；无自由文本；维度枚举 | 单测序列化随机事件，断言禁止键不存在；E4 检查真实批次 | 无题面/节点 ID/设备 ID/路径 |
| V8 | 保留期有界 | `TelemetryBuffer` 复用 `CrashLedger` 的三重上限；服务端 TTL | 单测 300 条/384 KiB 上限、丢最旧；E3 8 天后旧数据不存在 | 超限丢最旧；到期删除 |
| V9 | `strictOffline` 结构上不能上传 | 清单 `tools:node="remove"` INTERNET/ACCESS_NETWORK_STATE | `./gradlew :app:assembleStrictOfflineDebug`；`aapt2 dump permissions`/`apkanalyzer manifest print` | 合并清单无 INTERNET |
| V10 | 无默认开启的第三方 SDK | 依赖清单无 Firebase/OTel Android 自动初始化；无 analytics provider | `./gradlew :app:dependencies` + 合并清单 grep `provider`/`firebase`/`opentelemetry` | 无默认采集 SDK，或已证明延迟初始化 |
| V11 | 同意记录可审计 | store 写 `policy_version/hash/granted_at/withdrawn_at`；本地 append-only 审计 | 单测字段完整；E6 导出同意记录 | 每次同意/撤回可追溯，且不含学生身份 |
| V12 | 服务端删除可验证 | 删除 API/批次登记；服务端保留期任务 | 接口测试/运维凭证（若已有服务端） | 撤回后批次在承诺期内删除 |

### 4.3 单测骨架（示意）

```kotlin
@Test
fun freshInstall_isOff_andCollectsNothing() = runBlocking {
    val store = TelemetryConsentStore(isolatedContext())
    val sink = FakeSink()
    val recorder = TelemetryRecorder(
        consent = store,
        gate = TelemetryGate(
            build = TelemetryBuildConfig(telemetryBuildEnabled = true),
            hasInternetPermission = true,
            currentPolicyHash = CURRENT_POLICY_HASH,
        ),
        sink = sink,
    )

    assertEquals(TelemetryConsentState.Unknown, store.current().state)
    recorder.record(StageDuration(stage = "sql_recall", outcome = "ok", ms = 12))
    assertEquals(0, sink.count)
    assertFalse(telemetryBufferFile().exists())
}

@Test
fun grant_thenWithdraw_stopsAndPurges() = runBlocking {
    val store = TelemetryConsentStore(isolatedContext())
    val sink = FakeSink()
    val recorder = TelemetryRecorder(store, gate, sink)

    store.grant(policy = CURRENT_POLICY)
    recorder.record(StageDuration(stage = "rerank", outcome = "ok", ms = 3))
    assertEquals(1, sink.count)

    store.withdraw()
    recorder.record(StageDuration(stage = "rerank", outcome = "ok", ms = 3))
    assertEquals(1, sink.count)
    assertFalse(telemetryBufferFile().exists())
    assertTrue(
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("telemetry-upload").get()
            .all { it.state.isFinished },
    )
}
```

### 4.4 设备/网络证明步骤（E4）

1. 全新安装 `localFirst` 变体，**不配置模型、不点同意**。
2. 在模拟器/测试机设置全局代理到本机 mitmproxy/Charles；或使用 PC 端 VPN 抓包。
3. 走完首次启动、知识库安装、3 次检索、进入设置再返回；等待 ≥1 小时。
4. 断言：代理日志中**没有**遥测端点请求；`filesDir/telemetry` 不存在或为空；WorkManager 无 `telemetry-upload`。
5. 点“允许”，再执行检索；触发一次上传；断言：只访问配置的境内端点、批次 JSON 符合 §2、无禁止字段。
6. 点“停止发送”，再执行检索；断言：代理日志无新请求，缓冲文件被删除，WorkManager 无活动任务。

### 4.5 构建产物证明（E5）

```bash
./gradlew :app:assembleLocalFirstDebug :app:assembleStrictOfflineDebug

# 任选其一（路径随 AGP 版本可能不同）
aapt2 dump permissions app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
apkanalyzer manifest print app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk

# 合并清单直接看
grep -n "INTERNET\|ACCESS_NETWORK_STATE" \
  app/build/intermediates/merged_manifest/strictOfflineDebug/AndroidManifest.xml
```

通过标准：`strictOffline` 合并清单没有 `android.permission.INTERNET`；`localFirst` 有 INTERNET，但遥测端点默认空、无第三方分析 provider。

### 4.6 会推翻“默认关闭”结论的证据

出现以下任一情况，即说明“默认关闭”不成立：

- 同意前任何一次到遥测端点的 HTTP 请求；
- 同意前创建了遥测缓冲文件/数据库行；
- 代码里存在 `GRANTED` 的默认值或自动写入（非用户动作）；
- 任何第三方 SDK 在 `Application.onCreate`/ContentProvider 阶段自动初始化并采集；
- 批次中出现题面、节点 ID、设备 ID、文件路径、手机号等禁止字段；
- `strictOffline` 构建产物含 INTERNET 权限；
- 撤回后仍在采集/上传；
- <14 用户无监护人确认却产生上传；
- 保留期超过承诺（本地/服务端）；
- 政策变更后未重新同意却继续上传。

### 4.7 审计材料

- 同意页截图 + 文案版本 + `policy_hash`；
- 同意/撤回本地审计记录（时间、动作、政策版本；不含学生身份）；
- PIPIA 报告（触达敏感信息/未成年人/委托处理/跨境时）；
- 保留期配置与服务端 TTL 证明；
- 受托处理合同/云厂商数据位置；
- 设备抓包记录（同意前/后/撤回后三段）与构建产物权限截图；
- 第三方 SDK 清单与初始化时机说明。

---

## 5. 未验证项

| # | 未验证项 | 影响 | 下一步 |
|---|---|---|---|
| U1 | 《网络安全法》2025 修正后，旧第 21 条第（三）项“网络日志不少于六个月”对应的**新条文序号** | 引用条文号可能出错；义务本身未被修正决定删除 | 以全国人大/国务院重新公布的 2026 版全文核对；本文只把旧序号当“义务内容”引用，不把新序号当定论 |
| U2 | 离线 BYOK App 是否构成《生成式人工智能服务管理暂行办法》第 2 条的“向境内公众提供生成式人工智能服务”的提供者 | 决定第 11 条是否直接适用；不构成时 PIPL 仍适用 | 需法律意见；保守按“可能构成”设计 |
| U3 | 聚合指标在什么 k/什么维度组合下可被认定为 PIPL 第 73 条（四）的“匿名化” | 决定能否不按个人信息处理；小用户量产品风险高 | 做匿名化/重识别评估；在评估前按个人信息处理 |
| U4 | OpenTelemetry Android 磁盘持久化的上限、清理策略、重试策略 | 若直接采用，可能磁盘无界/行为不可控 | 读其源码/配置文档；自研时自设上限 |
| U5 | Firebase Performance Android 构建期停用的 manifest meta-data 键名与初始化时机 | 默认采集风险；可能先于同意初始化 | 以 Android 页/合并清单/实测确认；更稳的是不接 |
| U6 | GB/T 35273—2020 是否已被 2026 年修订版替代 | 引用版本可能过时 | 本次抓取 openstd 状态为“现行”；另见 2026 修订草案（二手来源，未验证），需持续跟踪官方状态 |
| U7 | Android vitals 对“slow cold start”的精确阈值/定义 | 不能把 Play 阈值当本产品 SLO | 本文只用 TTID/TTFD 定义与 crash/ANR/内存阈值；slow start 阈值另查 |
| U8 | 是否存在“移动端阶段耗时”的官方默认桶标准 | 桶边界只能算工程提案 | 未找到；本文用 OTel 默认 + SRE 指数 + Prometheus SLO 边界组合 |
| U9 | 监护人同意的“合理验证”强度（仅本地确认是否足够） | 影响 <14 流程合法性 | 需法律意见；无法可靠验证时 v1 不做出端上报 |
| U10 | 若自建遥测后端，《网络安全法》第 21(3) 条的安全日志 6 个月与产品遥测保留期如何分账 | 可能被要求留存安全日志；不能拿它当分析留存依据 | 法律/运维共同定；分表、分目的、分保留期 |
| U11 | `strictOffline` 移除 `ACCESS_NETWORK_STATE` 是否影响 WorkManager 约束行为 | 该变体本就不上传，影响有限 | 构建后跑 WorkManager 最小用例确认 |
| U12 | Google/Firebase SDK 是否通过 ContentProvider 在 `Application.onCreate` 前初始化 | 可能导致“同意前已采集” | 合并清单/启动日志/抓包验证；不接最稳 |

---

## 附：一句话工程裁决

**v1 最稳的形态是“只有本机指标面板，没有出端上报”。** 若产品坚持上报，则必须同时满足：默认 OFF 的三层默认、独立明示同意、未成年人监护人门、聚合无 ID、k-匿名、境内端点、最短保留、一键撤回、PIPIA 与年度审计；并且用 §4 的 E1–E6 证据证明，而不是只写隐私政策。
