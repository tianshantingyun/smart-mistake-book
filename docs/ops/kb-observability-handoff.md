# KB 生产观测交接单（观测缺口 → 指标 → 立项）

| 项 | 值 |
| --- | --- |
| 版本 | v1（2026-10-27） |
| 状态 | **交接中**：四项生产指标均未落地；`KB23-R1-16` 保持 open |
| 交接对象 | **智能体面 / 产品线（指标面立项）**；本线（KB 数据/检索线）先补日志 |
| 上游依据 | `build/agent-batch23/obs-audit.md`（只读审计，2026-10-26）、`docs/kb-batch23-2026-10-26.md` §8/§9、`docs/research/2026-10-26-kb-usage-r1-prereg.md` §7.6 |
| 本轮实读 | 2026-10-27 当前工作副本；代码 `path:line` 已逐条回读复核（与审计文一致）；artifact 行号按本轮 grep 实测（审计文 `device-metrics.txt:3` 实为 `:2`，已按实测校正）；命令与输出见 §7 |
| 本轮未做 | 未改代码、未加埋点、未建指标、未跑构建/单测/仪器测试/设备 |

> **证据规则**：本文件是交接口径，不是完成声明。§2 的阈值分两类——**有实测/冻结基线**（注明来源）与**建议初值（无基线，须用首批生产数据校准）**。后者不得当作既有 SLO，也不得写进任何“已达标”结论。

---

## 0. 结论（先给交接判断）

1. **生产侧只有“文本日志”，没有“比例”**：KB 主路径 19 处 `Log.*` 调用（§3.1，本轮 grep 复核）、0 个指标 SDK（§7-2 输出 `0`）。安装/重建/稠密回退都有单条日志，但它们都不能回答“多少节点有材料、多少请求零命中、延迟分位是多少、现网停在哪个版本”。
2. **KF-29 不能替代**：它只做“题 → 知识点绑定是否正确”的人工抽样，聚合口径是 `WRONG/(CORRECT+WRONG)`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/BindingAuditAggregator.kt:45-75`），且唯一消费面是 **debug** 复核 Activity（`app/src/debug/kotlin/com/tingyun/smartmistakebook/BindingAuditActivity.kt:50`、`:115`），release 无出口。它不含召回候选、材料、延迟、内容版本。
3. **R1 改变了这件事的性质**：批次23 的 R1 把“全局 `LIMIT` 导致节点拿不到材料”的分配缺陷修掉了——离线 F1 从 46.67%（301/645）升到 100%（645/645）（`docs/kb-batch23-2026-10-26.md:221`）。所以**“53.3% 无材料段”只能作为 R1 前的历史基线，不能当当前覆盖率**（它是旧语义下的离线读数，`docs/research/2026-10-26-kb-usage-surface-audit.md:27`、`:56`）。R1 之后剩下的缺口回落到“内容侧到底有没有材料”，而这只能靠生产按科目统计才知道——这就是四项指标必须立项的直接理由。
4. **本文件的交付物是“采集点 + 口径 + 阈值建议 + 待决清单”**，不是指标实现。归属拆分见 §5：日志部分本线可做，**聚合/导出/分布属指标面，待智能体面/产品线立项**。

---

## 1. 四项指标的共性前提

| 前提 | 事实 | 出处 |
| --- | --- | --- |
| 无遥测 SDK | `*.gradle.kts`/`*.toml`/`*.gradle` 检索 firebase/crashlytics/sentry/bugsnag/datadog/amplitude/posthog/opentelemetry 命中 **0** | §7-2（本轮执行） |
| crash 账本不收 logcat，也不该收业务计数 | 采集面“收窄到定位崩溃/无响应所需的最小字段……不采集设备标识、logcat、系统设置、IP” | `app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics/DiagnosticEvent.kt:3-9` |
| 账本类别只有崩溃/协程/看门狗/系统退出 | `CRASH`/`COROUTINE_FAILURE`/`ANR_WATCHDOG`/`EXIT_*` | `.../diagnostics/DiagnosticEvent.kt:27-58` |
| 账本只在用户主动导出时离设备 | “堆栈里若含用户内容路径，也只会在用户主动导出诊断包时离开设备” | `app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics/DiagnosticEvent.kt:7-8`；导出包构造 `.../DiagnosticsBundleExporter.kt:31-43` |
| 安装失败**不进**账本 | 安装协程自己 `catch (failure: Throwable)`，不会落到 `COROUTINE_FAILURE` | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt:762-776` |
| 唯一进程内计数器不是指标面 | `fullParseCount` 只被 JVM 测试消费 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgePackResources.kt:76-80` |
| 覆盖快照有内存消费方、无 UI 消费方 | `app/`+`feature/` 标识符 grep `knowledgeCoverage` = **0**（本轮执行） | §7-5；core 侧 `core/data/src/main/kotlin/.../study/RoomBackedStudyExperienceRepository.kt:271`、`:321` |

**推论（立项时写死）**：KB 业务计数必须走**独立指标面**，不得复用 `DiagnosticCategories` 的崩溃语义；新增采集面必须先过隐私评审（现有纪律是“零新增隐私面”，`DiagnosticEvent.kt:3-9`）。

---

## 2. 生产需要但缺失的指标（逐条：定义 / 采集点 / 归属 / 阈值）

### 2.1 M1 材料覆盖率

#### M1a 渲染侧覆盖率（学生实际看到的那一面）

| 项 | 内容 |
| --- | --- |
| **定义** | 分子 = 渲染结果中**带材料摘要**的节点行数；分母 = 渲染节点行数。维度 = `entry`（`KNOWLEDGE_READ` / 拍照讲题预披露 / 复习 quiz / 重教 / 前置补救）× `subject`；窗口 = 会话、日、版本。 |
| **采集点建议** | ① `core/data/src/main/kotlin/.../study/RoomTutorToolRunner.kt:345`（`nodeMaterialSummaries` 返回后与 `nodes` 做差）、`:381-390`（每节点最佳一条）；② `core/data/src/main/kotlin/.../knowledge/RoomTutorKnowledgeContextLoader.kt:82-87`（预披露构造完成后）；③ `feature/review/src/main/kotlin/.../KnowledgeReviewQuizLoader.kt:64-70`；④ `core/data/src/main/kotlin/.../study/StudyReviewPlannerService.kt:661-666`；⑤ `core/data/src/main/kotlin/.../study/RoomBackedStudyExperienceRepository.kt:476-480`、`:509-516`。 |
| **归属线** | 日志（每次调用一行计数）= **本线（KB 数据/检索线）**；跨设备“率”与版本交叉 = **指标面（待智能体面/产品线立项）**。 |
| **阈值建议** | **有基线**：沿用批次23 冻结的 F1 **≥90%**（`docs/kb-batch23-2026-10-26.md:167`），实测锚点 R1 前 **46.67%（301/645）**、R1 后 **100%（645/645）**（同文 `:221`）。另设**回归硬条件**：“有绑定材料却渲染无摘要”的行数 **必须 = 0**（离线 0，同文 `:221`）；>0 即判分配回归，与覆盖率百分比分开告警。 |
| **边界（不能据此下结论的部分）** | F1 的分母只含“129×5 中**有绑定材料**的节点行”（`docs/kb-batch23-2026-10-26.md:167`），所以 100% 不等于全库覆盖；且 F1 是**离线镜像 + SQLite 逐字 SQL**，不是真 Room、不是生产（同文 `:229`）。 |

#### M1b 内容侧覆盖率（库里到底有多少节点有材料）

| 项 | 内容 |
| --- | --- |
| **定义** | 分子 = 至少有一条 `status != 'RETIRED'` 材料绑定的**已审校原子节点**数；分母 = 已审校原子节点数。维度 = `subject` × `packId`/`contentVersion`。 |
| **采集点建议** | 口径可复用两条现有 SQL：`core/database/src/main/kotlin/.../dao/KnowledgeTeachingMaterialDao.kt:81-126`（每节点最佳一条；**无材料节点不返回**，可直接当“有材料”集合）与 `core/database/src/main/kotlin/.../dao/ProblemOrganizationDao.kt:232`（`countReviewedKnowledgeNodesBySubject`，已审校分母）。**注意**：`KnowledgeTeachingMaterialDao.kt:64-68` 的 `readForKnowledgeNodes` 是**跨节点全局限额**查询，不能当覆盖率分母（它的 `LIMIT` 是合计，见同文件 `:24-62`）。`core/data/src/main/kotlin/.../knowledge/KnowledgePackCoverage.kt:128-157` 的 `audit()` 生产无调用方（本轮 grep 只命中测试 `core/data/src/test/kotlin/.../ReviewedKnowledgePackJsonCodecTest.kt:101`）。 |
| **归属线** | **指标面（待立项）**：需要新增一条按科计数查询 + 周期上报；跨设备分布只能聚合。 |
| **阈值建议** | **无实测基线**（全库口径从未被任何批次测过）。建议初值（**待校准，不是 SLO**）：单科 <70% 告警、全局 <80% 告警；上线两周后用实测 P10 重定，并把首次实测值回写本表 v2。 |
| **边界** | “已审校原子节点”依赖 `verification_status` 过滤口径（`ProblemOrganizationDao.kt:232` 的 WHERE）；分母取“原子节点”还是“全部节点”会得到不同的覆盖率，**立项时必须冻结一个**，否则跨批次不可比。 |

---

### 2.2 M2 空材料率

| 项 | 内容 |
| --- | --- |
| **定义（必须拆三类，否则不可归因）** | ① `noBindings`：节点无任何有效材料绑定（内容缺口，属 M1b）；② `materialQueryEmpty`：节点**有绑定**但查询返回空（**缺陷**）；③ `renderNoMaterial`：渲染面出现“（该节点无材料）”（学生可见面）。指标 = 每类次数 / 材料查询次数（渲染类用 / 渲染节点行），维度 = `entry` × `subject`。 |
| **采集点建议** | `feature/review/src/main/kotlin/.../KnowledgeReviewQuizLoader.kt:68-70`（`NoMaterial` 出口，当前无日志）；`core/data/src/main/kotlin/.../study/StudyReviewPlannerService.kt:661-666`（整科材料为空 → 静默 `return@forEach`）；`core/data/src/main/kotlin/.../study/RoomBackedStudyExperienceRepository.kt:476-480`（重教 opening 为空）、`:509-516`（前置补救为空）；`core/data/src/main/kotlin/.../study/RoomTutorToolRunner.kt:356-358`（渲染标记，当前无计数）；`core/data/src/main/kotlin/.../knowledge/RoomTutorKnowledgeContextLoader.kt:62-87`（零命中属合法空注入，与“无材料”不可区分）。 |
| **归属线** | 分类日志 = **本线**；率与分布 = **指标面（待立项）**。 |
| **阈值建议** | `materialQueryEmpty` 应恒为 **0**——数据边界实测“64,973 条材料 `summaryMarkdown` 为空 0 条；645 个金标节点行全部有材料”（`docs/kb-batch23-2026-10-26.md:229`），R1 后离线 645/645（`:221`）。**>0 即缺陷**，与阈值无关。`renderNoMaterial` 的目标值由 M1b 决定：**无基线，建议先只观测 2 周**，再定为 `(1 − M1b 目标) + 2pp`。 |
| **边界** | 当前唯一的“无材料”信号是模型可见文案 `（该节点无材料）`（常量在 `RoomTutorToolRunner.kt:402-403`），**不是结构化字段**；从 logcat 抓文案计数会把“合法空”和“查询缺陷”混为一谈。`SavedMistakeTutorRoute.kt:158`、`:186` 的材料/预披露失败直接 `emptyList()`，连文案都没有（`feature/tutor/src/main/java/.../SavedMistakeTutorRoute.kt:151-160`、`:178-188`）。 |

---

### 2.3 M3 检索延迟分布

| 项 | 内容 |
| --- | --- |
| **定义** | `readSubjectKnowledgeRecallCandidates` 单次耗时的 p50/p95/p99/max；维度 = `subject` × `indexPath`(`cacheHit`/`patch`/`rebuild`) × `denseUsed` × `entry`。**首查重建样本单列**（不混入召回分位）。 |
| **采集点建议** | 主点：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt:166-193`——召回全程**无任何计时**（本轮复核，函数体内无 `nanoTime`）。旁证只有：重建 `core/database/src/main/kotlin/.../KnowledgeSearchIndexBuilder.kt:102`、`:152-157`；安装 `core/data/src/main/kotlin/.../knowledge/BundledKnowledgeBaseInstaller.kt:31`、`:45-59`；稠密腿只打条数不打耗时 `core/data/src/main/kotlin/.../knowledge/dense/DenseRecallAssembly.kt:46-50`；索引三分支无日志 `RoomKnowledgeBaseStore.kt:248-284`。 |
| **归属线** | 单次耗时日志 = **本线**；分位数与跨设备分布 = **指标面（待立项）**。 |
| **阈值建议** | **有基线**：设备金标 harness `p50Budget=150ms` / `p95Budget=250ms`（`build/agent-batch23/device/device-metrics.txt:2`；同口径见 `docs/ops/kb-release-runbook.md:398`）；androidTest 预算 `150L * CI_MULTIPLIER`（`core/data/src/androidTest/kotlin/.../KnowledgeContextRetrievalInstrumentedTest.kt:536`）。实测（模拟器）：p95 67ms / p50 45ms / max 170ms（`device-metrics.txt:2`）。**生产建议**：`indexPath=cacheHit|patch` 用 p95 ≤250ms；`rebuild` 单列，观测 SLO ≤30s——实测单科重建 **15,252–28,447ms**（`build/agent-batch23/device/device-logcat.txt:512`、`:593`、`:643`、`:676`）。理由：重建样本若不单列，p95 会被十几秒的样本支配，指标失去意义。 |
| **边界** | 入口归因需要 caller 传标签：同一个 store 方法被多个生产入口共用——`core/data/src/main/kotlin/.../knowledge/RoomTutorKnowledgeContextLoader.kt:67`、`core/data/src/main/kotlin/.../mistake/RoomMistakeOrganizationRepository.kt:227`、`core/data/src/main/kotlin/.../study/RoomTutorToolRunner.kt:326`、`:680`。不改签名就只能得到“合计分布”。 |
| **附带子项** | 稠密腿回退率：成功/失败各有一条 I/W（`DenseRecallAssembly.kt:46-50`、`:55`），但无占比；回退原因只有异常 `toString()`。 |

---

### 2.4 M4 版本分布

| 项 | 内容 |
| --- | --- |
| **定义** | 按设备/会话统计四元组分布：`packId` + `contentVersion`、`indexVersion`、稠密资产 `sha256`、app `versionName`/`versionCode`。产出：当前版占比、滞后版占比、以及“版本 × M1/M2/M3”交叉（升级后指标是否劣化）。 |
| **采集点建议** | 安装完成：`core/data/src/main/kotlin/.../knowledge/BundledKnowledgeBaseInstaller.kt:45-59`——三条安装日志**均不打印** `packId`/`contentVersion`（本轮复核）；落库锚点 `:135-143`（`content_install_state`）；索引锚点 `KnowledgeSearchIndexBuilder.kt:141-150` + 常量 `core/database/src/main/kotlin/.../KnowledgeSearchFeatureExtractor.kt:28`；稠密 sha 只有代码常量 `core/data/src/main/kotlin/.../knowledge/dense/DenseRecallAssembly.kt:108`、运行期校验在 `:138`，加载日志 `DenseQueryEncoder.kt:169` **不含 sha**；manifest 当前值 `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json:3-5`（`packId=moe-2025-four-subjects-v1`、`version=21`、`contentVersion=9f9e99ceec47cf8c`）。 |
| **归属线** | **指标面（待立项）**——跨设备分布必须聚合，单机 logcat 只能逐台 `adb` 看；单机可先加一行日志（这一小步 = **本线**）。 |
| **阈值建议** | **无生产实测基线**，但机制可推出硬条件：<br>① `indexVersion` 与当前值不等会触发**整科重建**（版本锚点分支，`RoomKnowledgeBaseStore.kt:248-284`）——若某设备长期存在不匹配，说明重建无法完成 → 告警；<br>② 稠密 sha 不匹配会**失败关闭**（`DenseRecallAssembly.kt:138` 的 `expectedSha256`），腿静默不可用、只有一条 W（`:55`）→ 计数应恒 0；<br>③ `contentVersion` 分布：快路径按戳一致直接跳过解析（`BundledKnowledgeBaseInstaller.kt:40-51` 的 `isInstallUpToDate` 判定 + `:45-49` 日志），所以“停在旧版”是可长期存在的状态。**建议初值（无基线）**：发布后 7 天当前版占比 ≥95%、14 天 ≥99%。 |
| **边界** | 诊断账本虽已有 `versionName`/`versionCode`（`DiagnosticEvent.kt:17-20`），但只在用户主动导出时离开设备（`DiagnosticsBundleExporter.kt:31-43`），不能当分布数据源。 |

---

### 2.5 支撑计数（不单列阈值，服务于上面四项的归因）

| 计数 | 现状 | 采集点 | 归属 |
| --- | --- | --- | --- |
| 零命中率（分入口） | 仅归类入口有（`RoomMistakeOrganizationRepository.kt:269-275` 打 `recalled=<n>` + 检索词）；其余三处不打 | 缺：`RoomTutorToolRunner.kt:334-342`、`RoomTutorKnowledgeContextLoader.kt:62-80`（空 `features` 直接空集）、`StudyReviewPlannerService.kt:210-211` | 本线（日志）；分布 = 指标面 |
| 索引路径三分支 | `ensureKnowledgeSearchIndex` 的 cacheHit / patch / rebuild 均无日志 | `RoomKnowledgeBaseStore.kt:248-284`（三个 return/rebuild 出口） | 本线 |
| 稠密回退率 | 有 I/W 两条，无占比 | `DenseRecallAssembly.kt:46-50`、`:55` | 本线 |
| 重建时长/次数 | **已有**日志 | `KnowledgeSearchIndexBuilder.kt:152-157` | 已具备（单机） |
| 安装路径/耗时 | **已有**日志（快路径/全量两条） | `BundledKnowledgeBaseInstaller.kt:45-59` | 已具备（单机） |
| 安装重试失败 | **静默**（`catch (_: Throwable)` 后仅回写就绪位） | `SmartMistakeBookApplication.kt:1033-1036` | 本线（新增一行） |

---

## 3. 现有 logcat 可观测点清单与限制

> 口径：本文件列的是**当前工作副本**的调用点。`grep "Log\.\(d\|i\|w\|e\)"` 命中 21 行，其中 2 行是 KDoc/注释（`RoomTutorKnowledgeContextLoader.kt:26`、`CapturedTutorSessionRoute.kt:419`），**实际调用 19 处**（§7-1 本轮执行）。

### 3.1 KB 主路径 19 处 Log 调用

| # | tag | 级别 | 位置 | 实际内容 | 能回答 | 不能回答 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `KnowledgeInstall` | D | `core/data/src/main/kotlin/.../knowledge/BundledKnowledgeBaseInstaller.kt:45` | `install finished in <ms> ms (fast path: content stamp matches, no parse, no reconcile)` | 快路径命中 + 总耗时 | 命中的是哪个 `contentVersion` |
| 2 | `KnowledgeInstall` | D | `BundledKnowledgeBaseInstaller.kt:56` | `install finished in <ms> ms (full path: parse + reconcile)` | 全量路径总耗时 | 同上 |
| 3 | `KnowledgeReconcile` | W | `BundledKnowledgeBaseInstaller.kt:148` | `pack <packId> skipped <n> object(s); first: <...>` | 有对象被跳过时的包 id 与条数 | 全量明细（只进前 50 条，`:28`） |
| 4 | `KnowledgeInstall` | D | `core/data/src/main/kotlin/.../knowledge/BundledKnowledgePackResources.kt:89` | `packs fully parsed in <ms> ms (<n> packs)` | 全量解析是否发生、耗时、包数 | 包 id / `contentVersion` |
| 5 | `TutorKnowledgeContext` | I | `core/data/src/main/kotlin/.../knowledge/RoomTutorKnowledgeContextLoader.kt:46` | `knowledge base not ready (<state>): pre-disclosure withheld for subject=<s>` | 就绪位拦截 | 拦截时长/占比 |
| 6 | `TutorKnowledgeContext` | W | `RoomTutorKnowledgeContextLoader.kt:91` | `pre-disclosure load failed for subject=<s>: <t>` | 预披露整体失败 | 失败细分（哪个查询） |
| 7 | `DenseEncoder` | I | `core/data/src/main/kotlin/.../knowledge/dense/DenseQueryEncoder.kt:169` | `dense model loaded: inputs=... seqLen=...` | 模型一次性加载成功 | 资产 sha、加载失败 |
| 8 | `DenseRecall` | I | `core/data/src/main/kotlin/.../knowledge/dense/DenseRecallAssembly.kt:46` | `dense rerank on subject=<s> candidates=<n> denseNodeScores=<n>` | 稠密腿参与、候选域大小 | 耗时、版本 |
| 9 | `DenseRecall` | W | `DenseRecallAssembly.kt:55` | `dense leg unavailable for subject=<s>: <t>` | 稠密腿不可用及异常 | 回退占比 |
| 10 | `KnowledgeEvidenceWriter` | W | `core/data/src/main/kotlin/.../study/KnowledgeEvidenceWriter.kt:188` | 证据写入撞同身份拒绝行后的补偿结果 | 写侧异常（掌握度证据） | 正常写入量 |
| 11 | `KnowledgeOrganization` | W | `core/data/src/main/kotlin/.../mistake/RoomMistakeOrganizationRepository.kt:269` | `no knowledge context for subject=<s> recalled=<n> features=<set>` | 归类路径**零命中** + 召回条数/检索词 | 非零命中的条数与耗时 |
| 12 | `KnowledgeOrganization` | W | `RoomMistakeOrganizationRepository.kt:304` | `candidates=<n> offered=<n> aliasTruncated=<n> dropped=<n> [...]` | 候选菜单适配损失 | 材料覆盖 |
| 13 | `BindingAudit` | W | `RoomMistakeOrganizationRepository.kt:925` | 抽样构造失败 | KF-29 抽样自身失败 | 抽样成功/被配额挡下 |
| 14 | `BindingAudit` | W | `RoomMistakeOrganizationRepository.kt:928` | 抽样落库失败 | 同上 | 同上 |
| 15 | `TutorKnowledgeContext` | I | `core/data/src/main/kotlin/.../study/RoomTutorToolRunner.kt:311` | `KNOWLEDGE_READ withheld: knowledge base not ready (<state>)` | 工具路径被就绪位拦截 | 拦截占比 |
| 16 | `CapturedTutorSession` | W | `feature/tutor/src/main/java/.../CapturedTutorSessionRoute.kt:456` | `teaching references load failed: <t>` | 拍照讲题材料加载失败 | 零命中、合法空集（不打点） |
| 17 | `CapturedTutorSession` | W | `CapturedTutorSessionRoute.kt:470` | `knowledge context load failed: <t>` | 知识上下文加载失败 | 同上 |
| 18 | `KnowledgeRecall` | W | `core/database/src/main/kotlin/.../RoomKnowledgeBaseStore.kt:219`（tag 定义 `:44`） | `dense rerank threw for subject=<s>: <t>` | 稠密重排抛错 | 召回成功/条数/耗时——本 tag 在正常路径**一条都不打** |
| 19 | `KnowledgeIndexRebuild` | D | `core/database/src/main/kotlin/.../KnowledgeSearchIndexBuilder.kt:152` | `rebuild subject=<s> nodes=<n> features=<n> deleteChunks=<n> insertChunks=<n> tookMs=<n>` | 某科**发生重建**时的规模与耗时 | 触发原因、未重建时的缓存命中/只补缺 |

**应用层另 3 条（不在上面 19 处内；其中 1 条是静默分支，无日志）**

| # | 位置 | 内容 | 能回答 | 不能回答 |
| --- | --- | --- | --- | --- |
| 20 | `app/src/main/kotlin/.../SmartMistakeBookApplication.kt:763-767` | `Bundled knowledge install failed` + 堆栈 | 安装失败与诊断编号来源 | 失败率（无计数） |
| 21 | 用户面诊断编号：`SmartMistakeBookApplication.kt:768-776` → 横幅 `app/src/main/kotlin/.../StartupStateBanner.kt:85` | `startup:knowledge:<hash>` | 学生可见、可与 logcat 对照 | 不进任何聚合账本 |
| 22 | `SmartMistakeBookApplication.kt:1033-1036` | **无日志**：重试 `catch (_: Throwable)` 后仅回写就绪位 | — | 重试失败次数/原因（被吞） |

### 3.2 同一路径上的静默分支（构成缺口，均无结构化信号）

| 分支 | 位置 | 现状 |
| --- | --- | --- |
| `KNOWLEDGE_READ` 检索零命中 | `core/data/src/main/kotlin/.../study/RoomTutorToolRunner.kt:334-342` | 直接返回“知识库里没有匹配的知识点。”，不打点 |
| 每节点材料摘要缺失 | `RoomTutorToolRunner.kt:356-358`；标记常量 `:402-403` | 仅渲染 `（该节点无材料）`，不打点 |
| 预披露检索零命中 | `core/data/src/main/kotlin/.../knowledge/RoomTutorKnowledgeContextLoader.kt:62-80` | 合法空注入（`loadFailed=false`），不打点 |
| 全库扫描行为空 | `core/data/src/main/kotlin/.../study/StudyReviewPlannerService.kt:210-211` | 直接 `emptyMap()`，不打点 |
| 某科材料为空跳过整科 | `StudyReviewPlannerService.kt:661-666` | `return@forEach`，静默 |
| quiz 取不到材料 | `feature/review/src/main/kotlin/.../KnowledgeReviewQuizLoader.kt:68-70` | 返回 `NoMaterial`，无 Log |
| 重教/前置补救材料为空 | `core/data/src/main/kotlin/.../study/RoomBackedStudyExperienceRepository.kt:476-480`、`:509-516` | 返回 `null` / 空补救，无 Log |
| 讲题页材料/预披露失败 | `feature/tutor/src/main/java/.../SavedMistakeTutorRoute.kt:151-160`、`:178-188` | `emptyList()`，**无 Log** |
| 索引 cacheHit/patch 分支 | `RoomKnowledgeBaseStore.kt:248-284` | 无日志，只有 rebuild 分支内部打点 |
| 召回全程 | `RoomKnowledgeBaseStore.kt:166-193` | 无计时、无条数、无入口标签 |

### 3.3 落库但无导出面（本机 Room，不是遥测）

| 数据 | 位置 | 内容 | 可查询性 |
| --- | --- | --- | --- |
| 内容安装进度戳 | `BundledKnowledgeBaseInstaller.kt:135-143` | `packId`/`contentVersion`/`appliedAtEpochMillis`/`skippedCount`/`skippedDetail`（前 50 条，`:28`） | 仅本机；无导出/上报 |
| 检索索引锚点 | `KnowledgeSearchIndexBuilder.kt:141-150` | `(subject, index_version)` | 仅本机 |
| 绑定抽样队列 | `core/database/src/main/kotlin/.../dao/BindingAuditSampleDao.kt:81-96`；读取口 `core/database/src/main/kotlin/.../RoomStudyDatabase.kt:1933-1940` | 抽样快照/状态/判定 | 仅本机；该表进备份选择计划（`core/data/src/main/kotlin/.../backup/SmbkSelectionPlan.kt:99`、`:237`） |
| 已审校覆盖快照 | `RoomKnowledgeBaseStore.kt:668-671` → `RoomBackedStudyExperienceRepository.kt:271`、`:321` | 每科 topic/atomic/source 计数、待补缺口 | 进内存快照；`app/`+`feature/` **无 UI 消费方**（§7-5） |
| 知识点缺口（grounding） | `RoomKnowledgeBaseStore.kt:650-671`、`:673-689` | 待归位请求/汇总/已审校覆盖 | 仅本机 |

### 3.4 限制（清单式，立项时必须逐条回应）

1. **纯文本、无字段契约**：无 schema/version，改文案即断采集（例：`packs fully parsed in 23380 ms (2 packs)`，`device-logcat.txt:230`）。
2. **tag 复用造成语义漂移**：`KnowledgeInstall` 同时覆盖“解析”和“安装”；`TutorKnowledgeContext` 同时覆盖“就绪拦截”和“预披露失败”；`DenseRecall` 同时是成功 I 与失败 W。
3. **正常路径大面积不打点**：`KnowledgeRecall` 只在抛错时出现（`RoomKnowledgeBaseStore.kt:219`）；材料、零命中、索引三分支都没有点（§3.2）。
4. **无采样、无聚合**：既有 artifact 里 `DenseRecall` 单 tag 就有 **658 行**（总 1,599 行，§7-3）；logcat 环形缓冲会丢，无法当账本。
5. **无入口归因**：同一 store 方法被 4 个生产入口共用（§2.3），日志无法区分 `KNOWLEDGE_READ` / 预披露 / 归类 / 复习。
6. **无内容版本**：三条安装日志都不打 `packId`/`contentVersion`（`:45-59`、`BundledKnowledgePackResources.kt:89-92`）。
7. **不能代表学生分布**：批次23 的 logcat 来自测试负载（小规模科先建、后全量安装，`build/agent-batch23/obs-audit.md:118`）；`658/13/2/1` 只证明“可按文本抓”。
8. **release 日志裁剪未核实**：仓库未见日志剥离规则（`obs-audit.md:209`）；若后续引入 R8 日志剥离，§3.1 的打点需重新核对。
9. **隐私边界不可挪用**：crash 账本明确不收 logcat（`DiagnosticEvent.kt:3-9`）；把 KB 业务计数塞进 `CRASH`/`COROUTINE_FAILURE` 语义会改变其隐私边界与含义（`obs-audit.md:221`）。

---

## 4. 实施顺序（先日志，后指标；只给落点，不改代码）

| 序 | 一行日志 | 位置 | 建议字段（固定 `k=v`） | 回答 | 归属 |
| --- | --- | --- | --- | --- | --- |
| 1 | 召回结果 | `RoomKnowledgeBaseStore.kt:166-193` | `entry= subject= features= candidates= denseUsed= indexPath= elapsedMs=` | M3、M1a 分母、零命中 | 本线 |
| 2 | 材料命中 | `RoomTutorToolRunner.kt:345`（+ `:381-390`）、`RoomTutorKnowledgeContextLoader.kt:82-87`、`KnowledgeReviewQuizLoader.kt:64-70`、`StudyReviewPlannerService.kt:661-666` | `entry= subject= nodes= withMaterial= noBindings= queryEmpty=` | M1、M2 | 本线 |
| 3 | 索引路径 | `RoomKnowledgeBaseStore.kt:248-284` 三个出口 | `subject= indexPath=cacheHit\|patch\|rebuild` | M3 归因、索引健康 | 本线 |
| 4 | 版本三元组 | `BundledKnowledgeBaseInstaller.kt:45-59` | `packId= contentVersion= indexVersion= denseSha=` | M4（单机可核对） | 本线 |
| 5 | **指标面** | 新建聚合/导出面 | 见 §5 待决 D1 | M1–M4 的“率/分布/跨设备” | **待立项** |

**字段契约建议（v1 形状，供立项评审）**：

- 每行固定前缀 + 版本：`kbobs v=1 ...`；值只允许枚举/整数/科目代码，**不出现题面、摘要、知识点名等用户或内容文本**（延续“零新增隐私面”，`DiagnosticEvent.kt:3-9`）。
- `entry` 枚举固定为：`knowledge_read` / `pre_disclosure` / `organization` / `review_quiz` / `reteach` / `prereq_remediation` / `scan`。
- 采样：低频事件（安装、重建）全量；高频事件（召回、材料）按会话 1/N 采样，采样率随行输出，便于还原总量。
- **不塞进** `DiagnosticEvent` / `DiagnosticCategories`（`DiagnosticEvent.kt:27-58`）。

---

## 5. 交接状态与立项

### 5.1 归属表

| 指标 | 日志采集（本线） | 聚合/导出/分布（指标面） | 阈值与告警处置（产品线） |
| --- | --- | --- | --- |
| M1a 渲染侧覆盖率 | 可做（§2.1 五处） | **待立项** | 沿用 F1 ≥90%（已有冻结基线）；处置流程待定 |
| M1b 内容侧覆盖率 | 依赖计数查询 | **待立项** | 无基线 → 需产品线定初值并校准 |
| M2 空材料率 | 可做（分三类） | **待立项** | `materialQueryEmpty=0` 可立即定；`renderNoMaterial` 待 M1b |
| M3 延迟分布 | 可做（单次耗时） | **待立项**（分位数必须聚合） | 250/150ms 沿用现有预算；rebuild SLO 待定 |
| M4 版本分布 | 可做（单机一行） | **待立项**（分布必须跨设备） | 7 天 95% 属建议初值，待校准 |

### 5.2 交接状态

> **状态：待智能体面 / 产品线立项。**
>
> `KB23-R1-16` **保持 open**，不在批次23 关闭（依据 `docs/kb-batch23-2026-10-26.md:407`、`:532`；`docs/research/2026-10-26-kb-usage-r1-prereg.md:397`、`§7.6`）。本文件是**交接凭证**，不是关闭证据。

**待决事项（需要智能体面/产品线裁定，本线不擅自定）**

| ID | 待决 | 为什么必须由产品线/智能体面定 |
| --- | --- | --- |
| D1 | 指标面载体：本地统计 + 用户主动导出，还是接入遥测 SDK？ | 隐私边界（`DiagnosticEvent.kt:3-9` 的“零新增隐私面”）与合规口径只能由产品线签；接 SDK 会引入新的数据出境面 |
| D2 | 阈值与告警处置 owner、告警通道、误报处理 | 告警会改变发布判断，须有责任人 |
| D3 | 覆盖率分母冻结：原子节点 vs 全部节点；是否过滤 `verification_status` | 口径一变跨批次不可比（§2.1 M1b 边界） |
| D4 | release 是否保留 KB 日志、是否允许 R8 剥离 | 影响所有日志采集点是否在现网存在 |
| D5 | 是否允许按会话采样（比例、抽样键） | 涉及统计有效性与隐私 |

### 5.3 `KB23-R1-16` 关闭条件（可验证，缺一不可）

1. **C1 四项主指标各有“生产采集点 + 聚合 + 可查询”**——只有日志（§4 序 1–4）**不算**关闭。审计的判定是“单条 logcat 回答不了分布”（`obs-audit.md:124`、`:155`、`:169`）。
2. **C2 至少一次真实设备/真实学生路径的读数落盘**，且注明设备、版本四元组、时间窗；不得再用离线镜像/SQLite 逐字 SQL 替代（批次23 的 F1–F5 已明确“不是生产遥测”，`docs/kb-batch23-2026-10-26.md:229`；`docs/research/2026-10-26-kb-usage-r1-prereg.md` §7.6）。
3. **C3 阈值由产品线签字并回写本文件 v2**，把 §2 中所有“建议初值”替换为“实测基线 + 冻结阈值”。
4. **C4 隐私边界评审通过**（D1/D5 有正式结论）。

在此之前，任何“观测已补齐”“生产可观测”的表述都不成立。

---

## 6. 未运行 / 未验证

- **未改代码、未加埋点、未建指标**；未跑构建、单元测试、仪器测试、设备采集。本文件是只读交接。
- **未在任何真实设备/真实学生数据上采集**；§3.4 与 §7-3 的 `658/13/2/1` 是对既有 artifact 的计数，不能推断现网分布。
- **未核实 release 构建是否剥离日志**（仓库未见剥离规则；若引入 R8 剥离需重核全部打点）。
- **未核实 KF-29 当前样本量**：沿用 KD 文档“样本量 0”（`docs/research/2026-10-05-stage3c-part2-completion-record.md:143`），本轮未查库。
- **未验证 `observeReviewedKnowledgeCoverage` 是否有反射/动态装配消费方**：只做了 `app/`+`feature/` 标识符 grep（0 命中，§7-5）。
- **§2 中所有标“建议初值/无实测基线”的阈值均为建议，不是 SLO**：M1b（70%/80%）、M2 `renderNoMaterial`、M3 rebuild ≤30s、M4 7 天 95% / 14 天 99%。

---

## 7. 本轮执行的复核命令与输出（可复跑）

工作目录：`D:/smart mistake book`（Git Bash）。

**7-1 KB 主路径日志点计数（命中 21 行；2 行是 KDoc/注释 → 实际调用 19 处）**

```bash
grep -rn "Log\.\(d\|i\|w\|e\)" --include="*.kt" \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/Knowledge*.kt \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeOrganizationRepository.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeEvidenceWriter.kt \
  feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/CapturedTutorSessionRoute.kt \
  | grep -v "/build/"
```

输出（逐行，共 21 行）：`BundledKnowledgeBaseInstaller.kt:45/56/148`、`BundledKnowledgePackResources.kt:89`、`RoomTutorKnowledgeContextLoader.kt:26`（注释）/`46`/`91`、`DenseQueryEncoder.kt:169`、`DenseRecallAssembly.kt:46/55`、`KnowledgeEvidenceWriter.kt:188`、`RoomMistakeOrganizationRepository.kt:269/304/925/928`、`RoomTutorToolRunner.kt:311`、`CapturedTutorSessionRoute.kt:419`（注释）/`456`/`470`、`RoomKnowledgeBaseStore.kt:219`、`KnowledgeSearchIndexBuilder.kt:152`。

**7-2 指标/遥测 SDK 依赖（0）**

```bash
grep -rniE "firebase|crashlytics|sentry|bugsnag|datadog|amplitude|posthog|opentelemetry" \
  --include="*.gradle.kts" --include="*.toml" --include="*.gradle" . | grep -v "/build/" | wc -l
```

输出：`0`

**7-3 既有设备日志标签分布（artifact 读取，非本次生产采集）**

```bash
grep -oE "(KnowledgeInstall|KnowledgeReconcile|KnowledgeIndexRebuild|KnowledgeRecall|DenseRecall|DenseEncoder|TutorKnowledgeContext|KnowledgeOrganization|BindingAudit|CapturedTutorSession|KnowledgeEvidenceWriter)" \
  build/agent-batch23/device/device-logcat.txt | sort | uniq -c | sort -rn
```

输出：`658 DenseRecall` / `13 KnowledgeIndexRebuild` / `2 KnowledgeInstall` / `1 DenseEncoder`；文件共 `1599` 行。

**7-4 重建耗时（原文行，用于 M3 的 rebuild 单列阈值依据）**

```bash
grep -n "KnowledgeIndexRebuild" build/agent-batch23/device/device-logcat.txt
```

关键行：`device-logcat.txt:512` `rebuild subject=MATH nodes=890 … tookMs=25001`；`:593` `PHYSICS … tookMs=25612`；`:643` `CHEMISTRY … tookMs=28447`；`:676` `BIOLOGY … tookMs=15252`（安装期小规模科为 `39–63ms`，`:233-241`）。

**7-5 覆盖快照是否有 UI 消费方（app/feature 0 命中）**

```bash
grep -rn "knowledgeCoverage" --include="*.kt" app feature | grep -v "/build/" | wc -l
```

输出：`0`（core 侧消费方见 `RoomBackedStudyExperienceRepository.kt:271`、`:321`）

**7-6 延迟预算与实测读数（用于 M3 阈值）**

```bash
grep -n "p95Budget\|p95=" build/agent-batch23/device/device-metrics.txt
grep -rn "RECALL_P95_BUDGET_MILLIS" --include="*.kt" core | grep -v "/build/"
```

输出：`device-metrics.txt:2` `route=B-bare->top5 … p95=67ms p50=45ms max=170 denseLegLive=true p95Budget=250ms p50Budget=150ms overBudgetSamples=0`（`:32` 为 lexical 腿）；`core/data/src/androidTest/kotlin/.../KnowledgeContextRetrievalInstrumentedTest.kt:536` `val RECALL_P95_BUDGET_MILLIS = 150L * CI_MULTIPLIER`。

---

## 8. 一页版交接（可复制进立项会）

- **要什么**：四项指标——材料覆盖率（渲染侧/内容侧）、空材料率（三分）、检索延迟分布（分路径）、版本分布（四元组）。
- **现在有什么**：19 处 logcat 文本 + 本机落库锚点 + debug 专用绑定复核；**0 指标面**。
- **为什么现在必须做**：R1 修掉了“分配缺口”（46.67%→100% 离线），**剩余缺口的性质变成了“内容侧有没有材料”**，这只能靠生产统计；而现有日志连“有材料”都不计数。
- **谁做**：日志四行 = 本线（KB 数据/检索线）；聚合/导出/分布 = **待智能体面/产品线立项**；阈值签字 = 产品线。
- **验收**：§5.3 的 C1–C4，四项全过才关 `KB23-R1-16`；在此之前保持 open。
