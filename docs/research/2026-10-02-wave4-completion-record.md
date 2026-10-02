# Wave 4 规模化（3A 尾）完成记录 · 2026-10-02

> 依据：roadmap Wave 4（`docs/research/2026-09-28-kernel-remediation-roadmap.md:228-242`）、
> execution-steps Step 8、审计 `2026-09-28-kernel-scale-precision-audit.md` 的 S1–S21。
> 执行方式：单一长工作流（4 批串行 + 逐批脚本门 + 独立复核；`dwfrun-*` 系列，9h34m）+
> W4-4 由主会话接手（原因见 §5）。
> 记录落本文件（独立成文）：台账与 master line 含另一线未提交的在飞改动，指针待其提交后回填。

## 1. 批次、提交与要点

| 批 | 提交 | 内容 | 关键数字/证据 |
|---|---|---|---|
| W4-1 排程放大 | `e74e605e`（10 文件） | S12–S16：antiOscillation 备选池精确化（AlternativePool）、usedPracticeUnits 每步一次、beam 步数按预算收敛、localSwap 增量维护、confusable 共享先修倒排、MasterySmoothing 单遍、组装批量化（考试表/样本表/难度档一次读）；新建 `ReviewPlannerScaleBenchmarkTest`（5000 候选 + 等价金样） | 5000 候选 **1054ms → 168ms**（单类隔离；整套并发 631ms，门 <1000ms）；等价性：3 组金样（60/500/5000）选卡序列 + reasons + 分值 hex 改前捕获、改后逐位一致；33 组 A/B 夹具 `planFingerprint` diff 空；**零 bump**（§3.7）；预筛未加（等价硬约束下无安全上界，登记理由）；KF-17 勘定 `deferred-to-projection` |
| W4-2 投影热路径 | `f896fafa`（12 文件）+ `620d3b19`（复核补证 2 文件） | S1 commitProjection 全删全插 → 变更行 @Upsert + 消失行主键差集删除 + 观察表增量 append；S3 观察列表单遍聚合（无拷贝）；S4 drain 批内快照复用；S7 读路径懒校验（新增 `verifyIntegrity()` 显式入口，写路径调用）；S10 drainer 显式 Dispatchers；**裁决 26**（独立看答案 lapse 该题 KC 记忆卡）+ **KF-17**（同批同 source 到期日 +0/+1/+2 天错峰） | S1 不变量：提交后各表与旧实现逐位一致（新增真 Room 收缩用例覆盖五删除分支）；S7 `ModelTaskIntegrityTest` 4 例（篡改仍被 verifyIntegrity 捕获）；裁决 26 锚点：KC 卡稳定度 7.3153→1.0898；**bump：projector-v11 + evidence-v5 + attribution-v3**（复合串 `learning-core-v11`），archive/重放走既有 W0-1 机制 |
| W4-3 重放路径 | `bc657820`（9 文件）+ `4b4dae2f`（复核阻断项修复 3 文件） | S5 `loadLearningLedger` 事务外分块 + 每块每表一次批读（910 事件 2731 条查询 → 16 条）；分配头与块行同事务快照（复核抓出的并发追写假 GAP 已修）；S8 事件指纹单遍记忆化（读边界已验值转发，不再重算 3-4 次） | 50k 事件 `replay(读边界)==replay(重算)` 整个结果数据类逐位相等；50k 重放 **1076ms → 176ms**；假 GAP A/B 复现→修复；**零 bump**（§3.9） |
| W4-4 KB 索引 | （本批，见 §5） | **S19：诊断后判"不加索引"**（现查已用复合 PK 的内部覆盖索引；显式索引实测变慢 4.7×；ANALYZE 无差）；**S21：整科重建挪到内容安装期**（新 `KnowledgeSearchIndexBuilder`，读时自愈改委托同一实现）；**附带：安装链四条批量 IN 删除分块**（他线在飞 5 万材料包越过 SQLite 绑定变量上限的修复） | 桌面 SQLite 3.50.4 实测量级（3.6 万节点/86.4 万特征行）：召回 p50 6.1ms、`SCAN` 计数 2.7ms、DISTINCT 计数 9.9ms；诊断与原始输出落 `docs/research/2026-10-02-s19-retrieval-diagnosis.md`；**零 bump**（§3.10） |

## 2. 版本与指纹（Wave 4 总账）

- **bump（一次投影 bump 覆盖两处输出变化）**：`PROJECTOR` v10→**v11**；`EVIDENCE` v4→**v5**；
  `ATTRIBUTION` v2→**v3**；复合串 `learning-core-v10`→**`learning-core-v11`**（PROJECTION/REVIEW/SELECTOR 三串同前缀）。
  依据：裁决 26（看答案语义）+ KF-17（到期日错峰）都是投影输出变化，合并一次重放；archive 先归档。
- **零 bump（逐位等价，各自写明理由）**：W4-1（S12–S16）、W4-3（S5/S8）、W4-4（S19/S21）。
- **未动**：`SKIP_POLICY` skip-v4、`FORGETTING_CURVE` curve-v3、`LEDGER` ledger-v2、
  `STUDY_DATABASE_VERSION` 56、`REVIEW_PLANNER` review-planner-v8、`ReviewPlannerV2` v4 + canonical-v9、`SELECTOR` selector-v7。
- 台账行：§3.7（W4-1）/§3.8（W4-2）/§3.9（W4-3）/§3.10（W4-4）。

## 3. 验证（本跑实跑；口径如实）

| 门 | 命令 | 结果 |
|---|---|---|
| 各批快档 JVM | `gradlew :core:domain:test :core:data:testDebugUnitTest :core:database:testDebugUnitTest` | 全绿（工作树含他线在飞的 dense/KB 夹具改动；**5 条 KB 金标既有红在其当前状态下实测为绿**，本波未触碰这些文件） |
| 全量 JVM | `gradlew test --continue` | **2127 例 / 0 失败**（app 两 flavor + 各 feature/core 模块；变更输入的任务当轮重跑，其余为同输入 up-to-date） |
| core:database 仪器化全套 | `gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.ciSlowRunner=1`（**CI 同形**，性能预算 4×） | **188/0**（冷启健康模拟器 5m15s；矩阵单测 119s。**注**：同一套件在此前被两条会话长时使用的污染态模拟器上曾出现矩阵单测 41 分钟病理性时长 + 系统崩溃（空 `<failure>` + `INSTRUMENTATION_ABORTED`），按既定处置冷启 `-wipe-data` 后复跑全绿——判**环境档**，失败类可复现指向设备而非代码） |
| W4-4 定向仪器化 | 新装预热 + 内容调和（含新分块回归） | `KnowledgeSearchIndexInstallInstrumentedTest` 2/2、`KnowledgeContentReconciliationInstrumentedTest` **7/7**（含 1,200 节点+1,200 材料分块用例） |
| 检索端到端（core:data） | `KnowledgeContextRetrievalInstrumentedTest`（2 例） | **2/2**——`largeSubjectRecallRemainsBoundedOnRoom`（2 万点 + 完整 Room 召回路径，p95 预算门）与 `bundledSubjectsRecall…`（**真实 5 万材料包**）；后者在分块修复前以 `SQLITE_ERROR: too many SQL variables` 失败（见 §5） |
| app 三屏仪器化 | `:app:connectedLocalFirstDebugAndroidTest`（LearningMastery/SchedulingSettings/SourceCalibration） | 5/5 |
| R8 冒烟 | `:app:assembleLocalFirstRelease`（debug keystore 口径，同前两波）+ 装机/启动/截图/logcat | 构建 BUILD SUCCESSFUL 1m58s；装机 Success、进程存活（pidof）、logcat 零 FATAL/反射缺失；截图两版对照：分块修复前主页带「本地知识包尚未准备好」横幅、修复后干净（`build/r8-smoke-wave4b-2026-10-02.png`） |

**性能门口径（重要）**：本机共享 AVD 上 `PerformanceGateTest.firstScreenPerformance` 在**无倍数**口径下实测
810ms > 500ms（Wave 3 同类记录：首屏 725ms、并发搜索 1044.5ms）——属既有的本机环境档，
**不声称绿、不记回归**；本波的全套仪器化门以 CI 同形的 `ciSlowRunner=1`（4× 预算）运行并如实标注。

## 4. S19/S21 要点

见 `docs/research/2026-10-02-s19-retrieval-diagnosis.md`（含 EXPLAIN 原始输出、四方案对照、登记项）。

## 5. W4-4 由主会话接手的说明（诚实记录）

长工作流在 W4-4 的 `core:database` 仪器化门上停机——失败者是
`PerformanceGateTest.firstScreenPerformance`（810ms>500ms，环境档，见 §3 说明）；工作流脚本的门谓词
**没有豁免该类**，导致误停；且其索引批实施未留下持久实现（工作树核实：无 W4-4 相关改动/诊断文档）。
主会话接手：桌面诊断（§4）+ S21 实现 + 新仪器化用例 + 分块修复（下段）+ 本记录。

**接手时发现并修复的第二个真实缺陷（批量 IN 删除未分块）**：接手跑检索端到端时，
`bundledSubjectsRecallExpectedKnowledgeWithoutCrossSubjectCandidates` 以
`android.database.sqlite.SQLiteException: too many SQL variables` 崩在
`DELETE FROM knowledge_teaching_material_node_binding WHERE material_id IN (…)`
（`KnowledgeTeachingMaterialDao.kt:95`）。根因：**另一条会话在飞的 v2 教学支持侧车已达 50,383 条
材料**，而安装链把全包 id 一次传入 `… IN (…)`——超过 SQLite 绑定变量上限（旧平台 999；
实测 API 34 上越过 32766）。归属核实：该语句与调用路径**不在 W4-4 的 diff 内**（W4-4 只改
`RoomKnowledgeBaseStore.kt` + `RoomKnowledgeContentReconciler.kt` 的 11 行插入，且插入点在崩溃点**下游**；
DAO 文件零改动）。修复：`RoomKnowledgeContentReconciler` 四条批量删除（材料绑定/来源绑定/前置边/
搜索特征）按 **900 分块**（`MAX_IN_PARAMETERS`，留 999 上限余量），并加回归用例
`bulkDeletesChunkPastTheSqliteVariableCapOnLargePacks`（1,200 节点 + 1,200 材料整包落地、重跑幂等）。
修复验证：bundled 检索用例转绿（2/2）、首装横幅消失（截图对照 §3）。**这条修复同样服务于生产**：
他线把包推向 5 万材料后，未分块的安装链在真机上必炸（minSdk 23 的 999 上限更早）。

## 6. UNVERIFIED 与登记项（不藏）

1. **候选池预筛未加**（W4-1 裁决）：等价硬约束下"安全上界"保留近乎全池、无收益；达标由 S12–S16 承担（已实测 1050ms→168ms）。
2. **选择器先修接线**（批次 R 登记延续）：`AdaptiveSelectionRequest` 不带先修图，KF-16 压制在该路径不生效。
3. **每次召回的固定读数开销 ≈12.6ms（桌面档）**：`ensureKnowledgeSearchIndex` 自愈的两条 COUNT 每查询都跑；
   建议改为"每进程每科一次"或写侧失效驱动——本批只登记（数字在诊断文档 §2.3），涉及多写入口接线，另立单项。
4. **S3 滑动窗**（语义变更）与 **KF-17 单题不受影响**之外的边界：按计划登记/已测。
5. `PerformanceGateTest` 环境档、真实模型路径不冒烟：口径同前两波。
6. 16.6s 长尾的因果（首访问重建 vs 设备 GC）：桌面不可复现；S21 移除了最可疑来源，
   真机若仍见长尾按证据如实记录。
7. **分块修复的其余影响面**：本次分块覆盖安装链四条删除；`upsertMaterials/upsertBindings/@Upsert`
   列表走 Room 逐行语句，无绑定变量上限问题（未实测 5 万行 @Upsert 的耗时——他线包规模下的
   安装耗时未建立基线，登记）。模拟器在污染态下的矩阵病理性时长（41 分钟 vs 冷启 119s）
   提示**共享 AVD 长时使用后的性能观测不可作基准**，后续性能类仪器化以冷启后为准。

## 7. 共享工作树

台账（`docs/agent-first-refactor-decisions-2026-09-23.md`）与 `docs/REFACTOR-MASTER-LINE.md`
当前含另一线未提交的在飞改动——本记录独立成文；**完成记录段落与 master line 的行状态更新
（3A 行 Wave 4 ✅、3B 行 S5/S8 部分 ✅）待他线提交后一笔最小 diff 回填**。
