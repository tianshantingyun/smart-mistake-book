# 内核五维全量检查：补充卷（稳定 · 精确 · 灵活 · 高质量 · 大数据容量）

> **实施顺序以** `docs/research/2026-09-28-kernel-remediation-roadmap.md` **为准**（六波次执行手册，
> 本卷的 P/F/Q/S 编号在其中按波次排入）。

- 日期：2026-09-28
- 状态：三个只读审计代理（投影规模化 / 排程检索规模化 / 精确灵活高质量）完成；本卷是
  `docs/research/2026-09-28-algorithm-fix-plan.md`（KF-01~34）的**补充卷**，新增编号沿用 KF 体系
  （KF-35 起）。
- 证据口径：每 claim 带 `file:line`；行数与耗时为推算，标 UNVERIFIED。

---

## 1. 五维总判定

| 维度 | 判定 | 一句话 |
|---|---|---|
| **稳定（机制）** | 修复后成立；规模化不成立 | 账本/CAS 一致性设计健康；但投影"全删全插"与排程 O(N²) 在**数千题量级就痛，不是三年后** |
| **精确** | 不成立（12 条新缺口） | 根因模式="同一个数多种推导/口径"：本地日三套、rating 基址三处、原档逆向映射被折扣链打破 |
| **灵活** | 有方向、缺地基 | D-Q9 引导模式的新建量被低估：提示层级零生产者、作答前封顶无本地强制、L0–L4 与 FSRS 四档两套数轴易混 |
| **高质量** | 不成立（6 条缺口） | 最要命：**无投影版本回退/防降级**、**计划指纹无读时校验**——改了数值无法安全回退 |
| **大数据容量** | 有明确路径但现状会痛 | 5 万事件量级投影链四重放大；一刀切修复=候选池到期预筛 |

---

## 2. 精确（12 条新缺口，编号 P1–P12）

| # | 缺口 | 证据 | 改法方向 | 阶段 |
|---|---|---|---|---|
| P1 | **本地日三套互不同源**：StudyWriteContext 用 zone 规则；LearningProjector 用固定偏移算术；ReviewLogSink 用当前 zone 重算上一复习日（连事件偏移都不用） | `StudyWriteContext.kt:32-42`、`LearningProjector.kt:1223-1224`、`ReviewLogSink.kt:69-73` | 统一为"账本存原始时间戳+偏移，日序一律重放期派生"；ReviewLogSink 的写盘快照改为可复算 | 3A（KF-33 的扩展） |
| P2 | 门校准类内混用 `System.currentTimeMillis()` 与注入 Clock | `StudySchedulingCalibration.kt:67` vs `:37` | 全用注入 Clock | 3A |
| P3 | duration 全程整秒（Int），RT 对数正态拟合在量化数据上退化 | `LearningState.kt:308`、`ReviewSessionScreen.kt:925-927`、`ReviewLogSink.kt:93` | duration 升毫秒列；采集端不截断 | 3A（KF-14/15 前置） |
| P4 | **RTE 分母"展示时长"未采集**——修正 KF-15 的前提表述 | 全库无 presentedAt/displayDuration（grep 无命中） | 补展示时长采集后 KF-15 才可实施 | 3A |
| P5 | 首答计入跨日连击（毕业连击实际 2 次即触发）+ 曝光 `answerRevealCount` 只在首条 +1 不对称 | `LearningProjector.kt:902`、`:839` | 首答不算 cross-day；曝光计数语义统一 | 3A |
| P6 | **"原档以 evidence_weight 存留"承诺被折扣链打破**——折扣后值落库，四键逆向映射会读错档（四键通道现无生产写入方，潜伏态；D-Q9 接线即爆雷） | `StudySubmissionPreparer.kt:73-80` vs `FsrsScheduleMath.kt:190-194,255-260` | 原档与折扣分列存储；或逆向映射改为消费折扣后值 | 3A/阶段 2 |
| P7 | 逆向原档与调度档对同一行给出两种评级——KF-20 解释层会错源 | `FsrsScheduleMath.kt:210-271` | 与 P6 一并定"唯一评级来源" | 3A |
| P8 | 同 KC 三种数值（raw EMA / conservative / smoothed）四个展示面；UI 档位（0.7/0.4）与 MASTERED 判据（0.85/mass 2.0）两套阈值 | `MasteryBands.kt:8-12`、`ClearlyMasteredForSkipPolicy.kt:43-44`、三处展示面一致（好）但内部口径三分 | 展示统一用 conservative+区间；阈值体系合并进 KF-09/20 | 3A/3B |
| P9 | rating 基址三处手写换算（0/1-based）+ AVOIDANCE 常量基址语义悬空 | `ReviewLogSink.kt:91,251-253`、`FsrsScheduleMath.kt:109-113,162-164`、`AttentionSignal.kt:58` | 统一枚举与换算函数 | 3A |
| P10 | **`toFixedDays` 半值舍入与 py-fsrs 不一致**（Kotlin round=四舍五入，Python round=银行家舍入）——修正修复计划对照表的"round ✓ 一致"，tie 用例未覆盖 | `FsrsScheduleMath.kt:88-91` | 对齐 py-fsrs 银行家舍入 + tie 对照测试 | 3A |
| P11 | 预测审计轨用"批终态"当"预测时刻"；`FUTURE_TIMESTAMP_CLAMPED` 分支不可达 | `LearningProjector.kt:1162-1196`、`:244,446,1011-1013` | 预测语义修正或删除；死分支删除 | 3A/3C |
| P12 | 两指纹列恒同值、plannerVersion 无读时校验 | `StudyReviewPlannerService.kt:339-342` | 见 Q4 | 3A |

---

## 3. 灵活（D-Q9 的地基缺口，编号 F1–F8）

| # | 缺口 | 证据 | 改法方向 | 阶段 |
|---|---|---|---|---|
| F1 | 提示层级**零生产者**：`hint_level` 两列 schema 就绪但无写无读；`TutorAssistanceKind` 只有 HINT/ANSWER_REVEAL 二元 | `M1CuratedStudySeed.kt:348`（=0 种子）、`Assessment.kt:93-96` | 新增 level 字段（assistance 事件）+ 阶梯策略 + 模式状态——全部新建 | 阶段 2/5 |
| F2 | 提示定价二元（用没用提示）；5 级→证据权重映射需新建 | `MasteryEvidencePolicy.kt:100-107,133-141` | 新建 hint 档位→weight/rating 映射表 | 阶段 2 |
| F3 | **`hintWasUsed` 输入链断**——choice 路径从不传 persistedAssistance，现网连二元提示都不生效 | `StudySubmissionPreparer.kt:53-59` | 与 KF-02 同批修 | 3A |
| F4 | 作答前 L2 封顶**无本地强制层**（只靠 prompt 声明） | `OpenAiModelTaskAdapters.kt:306-318` | 新建本地执行层强制 | 阶段 2 |
| F5 | 模式切换需动 `TutorModelTaskPolicy`（mode+level 进请求指纹，否则同文本换档位命中旧缓存）+ `FsrsEvidenceRatingMapper`；`ModelEgress` 基本不动 | `TutorModelTaskPolicy.kt:189-239,369-423` | 请求指纹纳入 mode+level | 阶段 2 |
| F6 | 卡点检测缺：attempt 级档位历史、展示时长、按 presentation 的连续失败聚合查询 | — | 与 P3/P4 同批补 | 阶段 2/5 |
| F7 | pretest×引导模式交叉（已裁：pretest 不给提示、封顶 L0，台账裁决 2） | `PretestRouting.kt:22-31`（无生产调用方） | 已裁 | 已裁 |
| F8 | **L0–L4 脚手架与 FSRS 四档是两套数轴**，接入必须显式区分（否则被 P6 的逆向映射搅在一起） | `FsrsScheduleMath.kt:196-201` | 设计文档显式声明两套数轴边界 | 阶段 2 |

---

## 4. 高质量（版本化/回退/指纹/常数，编号 Q1–Q6）

| # | 缺口 | 证据 | 改法方向 | 阶段 |
|---|---|---|---|---|
| Q1 | 投影版本仅 3 次 bump 历史（v4→v7），无发布清单 | `LearningCoreVersions.kt:31-32` | 建立版本变更清单纪律 | 3A 起 |
| Q2 | **无投影版本回退/防降级**：旧二进制会静默用旧 VERSION 重放并覆盖新投影（replay 不调 requireCompatibleSnapshot） | `StudyProjectionDrainer.kt:79-94`、`LearningProjector.kt:91,642-655` | ① replay 也做版本守卫；② 落库前比较版本，拒绝降级；③ 每次 bump 前落旧投影快照（SCD2） | **3B（高优先）** |
| Q3 | 计划指纹 schema 双版本并存（v5/v7，V1/V2 切换时同表混存） | `ReviewPlannerV2.kt:911` vs `ReviewPlanner.kt:606` | M6 删 V1 后自然收敛；期间读时容错 | 3A |
| Q4 | **指纹/plannerVersion 无读时校验**：旧计划跨算法版本续跑（有 checkpoint 保护，无算法版本保护） | `ReviewDao.kt:505` 只 CAS checkpoint | 读回时比对 plannerVersion/指纹，不匹配则重排 | **3A（高优先）** |
| Q5 | 82 文件散落数值常数；两套排程权重表已分叉；DAY_MILLIS 至少 4 处重复 | `ReviewPlanner.kt:581-606` vs `ReviewPlannerV2.kt:872-912` 等 | 常数注册表（随 KF-13 扩容） | 3A |
| Q6 | 门校准通道无消费端（报告无人看）+ 统计窗口/时间源不一致 | `ChatEvidenceGateCalibration` 纯函数、feature/app 无消费 | 校准报告进"它最近做了什么"或设置页 | 3A/3C |

---

## 5. 大数据容量（热点合并清单，编号 S1–S21）

**投影/账本侧（S1–S11）**：

| # | 热点 | 复杂度 | 拐点（UNVERIFIED） | 修复方向 |
|---|---|---|---|---|
| S1 | `commitProjection` 每 100 事件批全删全插 7 张投影表 | O(整投影) | mastery 数千 + observation 数万时秒级 | 改 upsert 变化行；observation 增量 append |
| S2 | `verifyAppliedEventWindows` 每提交全窗口 ≤1.6 万行×2-3 查询 + SHA-256 | O(4×4096×3) | 与批大小无关恒为上限 | 只验本批新增；全窗口校验降频 |
| S3 | `projectMastery` per-KC observation 全拷贝 + 4-6 遍扫描 | 每事件 O(K)，per-KC 累计 O(K²) | 单 KC 数千 observation | observation 截断/滑动窗 + 预聚合 |
| S4 | `readCurrentSnapshot` 每 drain 批全投影读回 + groupBy | O(整投影) | 每批、每次排程 | 批内复用快照；排程读走窄查询 |
| S5 | `loadLearningLedger` 单事务每行 2-3 查询 + SHA-256 | O(N) 查询 | N>1 万分钟级 | 联表批量读出；事务外分块 |
| S6 | `generatePredictions` O(N×A) 对象且**无生产消费者** | O(N×A) | 每次全量重放 | 删除或可选化 |
| S7 | `toSnapshot` 每次读重算 JSON+SHA-256 | O(payload) | 任务列表数百行 + 高频发射 | 存指纹只比对；懒解码 |
| S8 | 事件指纹 SHA-256 全管道重复 3-4 次/事件 | O(事件体)×4 | 追赶 5 万事件可见 | 只在不信任边界重算 |
| S9 | `readIndependentErrorAggregates` 全量 attempt×attribution JOIN | O(作答数) | 每次 MASTERY_READ | 预聚合/物化 |
| S10 | drain 纯计算跑在调用方 dispatcher，UI 路径=Main | — | 全量重放最危险 | 显式切 Default/IO |
| S11 | `projection_consumption`/`review_log`/`chat_evidence` 只增不删 | O(N) 存储 | 无硬拐点 | 按年归档/汇总 |

**排程/检索侧（S12–S21）**：

| # | 热点 | 复杂度 | 拐点 | 修复方向 |
|---|---|---|---|---|
| S12 | beam search 全量候选×全量 penalty | O(beam×steps×N²)=60N² | N≈1000 亚秒级；5000 数十秒 | `antiOscillationPenalty` 用 fitting 非全量；usedPracticeUnits 增量 set；步数按预算 |
| S13 | localSwap 重复全量扫 | O(K×N×(N+K)) | 同上叠加 | 增量维护；限制未选池 top-M |
| S14 | computeConfusablePartners 全成对 | O(N²) | N≈3000 秒级 | 按共享前置倒排只配共享对 |
| S15 | scoreCandidate 三遍 observation 迭代 | O(C×K×Obs)×3 | Obs 数百/KC 时可见 | 一遍产出多值；缓存复用 |
| S16 | 组装 N+1（exam 解码/时延查询/咨询查询/无绑定写） | O(N×E)+… | 数千题毫秒-秒累积 | 一次读缓存；批量化 |
| S17 | observeActiveMistakes 每行 2-4 相关子查询 + 60+ 表失效全表重查 | O(N×子查询) | 5000 行 | 改 LEFT JOIN 聚合；必要时物化 |
| S18 | library_catalog 3 相关子查询 + instr 全扫 ×5 条分面 | O(N×子查询)+O(N×文本) | 5 万行 | 物化 mastery/标签列；搜索走既有 FTS |
| S19 | KB 召回未索引谓词（无 (subject, search_feature) 索引） | 全 subject 扫描 | **已实测 p95 143ms / max 16.6s** | 加复合索引；结果缓存 |
| S20 | 驻留快照每次事件全量重建 | O(N) | 高频触发 | 去抖/合并；后继映射缓存 |
| S21 | 首装/首访问索引重建 | 一次性秒级 | 3.5 万节点首查 | 并入构建产物预填充 |

**一刀切修复**：候选池加**到期/相关性预筛**，把 N 从数千压到数百——同时缓解 S12–S16。

---

## 6. 阶段归属汇总（新增缺口 → 阶段）

- **3A 批内新增（与 KF-01~34 同排）**：P1–P12（精确全组）、Q3/Q4/Q5/Q6、S 组的修复优先级
  S1/S3/S4/S7/S10（投影热路径）+ S19（KB 索引）+ S12–S16（排程放大，与 KF-08 硬过滤同批）；
  F3（与 KF-02 同批）。
- **3B**：Q2（投影版本回退/防降级）、S5/S6/S8（重放路径）、S2/S11（投影存储口径）。
- **3C**：P11 的预测审计修正、S9（MASTERY_READ 预聚合）、S17/S18/S20（错题本/快照物化，与阶段 4 联动）。
- **阶段 2/5（智能体面/复习栏）**：F1/F2/F4/F5/F6/F8（D-Q9 引导模式地基）、P3/P4/P6 的采集侧。
- **3A 前裁决新增**：F7（pretest×引导模式交叉）——已裁（pretest 不给提示，台账裁决 2）。

## 7. 对 fix-plan 的修正声明

- **KF-15 前提修正**：P4 证实"展示时长已采集"不实——KF-15 实施前必须先补采集。
- **对照表修正**：P10 证实 `toFixedDays` 的 tie 舍入与 py-fsrs 不一致——对照表"round ✓"改为
  "✓（tie 用例除外，待补）"。
- **KF-33 扩展**：P1 证实本地日有三套推导（非两处）——KF-33 的范围扩为"统一三源"。

## 8. 未验证边界（不得当事实）

- 所有量级拐点（秒级/数十秒级）为复杂度推算，无真机基准。
- S19 的 16.6s 长尾归因（SQL vs 设备 GC）未完全定案。
- 行数估算（5 万事件等）按 3 年使用量推算。
