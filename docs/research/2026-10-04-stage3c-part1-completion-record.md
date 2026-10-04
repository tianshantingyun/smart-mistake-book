# 阶段 3C 前半 · 绑定监测与自锁打破（KF-29 + KF-30 + KF-07）完成记录（2026-10-04/05）

> 依据：`docs/research/2026-10-04-stage3c-part1-plan.md`（提交 `b9c2f307`）、fix-plan P3 组
> （KF-29 `:372-385`、KF-30 `:387-392`、KF-07 `:148-156`）、主线 3C 行。
> **触发**：阶段 5 因 3D 硬门未过挂起（`docs/research/2026-10-04-stage5-gate-check.md`），
> 用户裁定转 3C 前半。**范围**：KF-29 监测管线 + KF-30 自锁打破 + KF-07 兜底改落 pseudo；
> **KF-31 未启**（按原前置"KF-29 有真实错误率数据后才开"）；S9/S17/S18/S20 留 3C 后半。
> 实施方式：多 Agent 协作（实施 → 正式门 → 独立只读复核 → 修复轮 → 显式清单提交）。

## 1. 交付与提交

| 批 | 提交 | 内容 |
|---|---|---|
| 计划 | `b9c2f307` | 3C 前半实施计划（范围/锚点/版本预算/门/风险） |
| 批 1 | `8ea6fc59` | **KF-29 绑定正确性监测管线**：新表 `binding_audit_sample` + **schema 61→62**（非破坏，DDL 与 62.json 逐字一致）；每科每周首 5 条新绑定抽样（UTC ISO 周窗口；内容寻址样本 id；配额计数+插入同事务，16 路并发恰好封顶用例）；hook 在组织写侧三路（自动接受/用户确认/离线纠正）的绑定成功落库之后；快照带题面（8k 截断标记）/绑定点/分类理由/步骤证据/模型版本与供应方；抽样失败 best-effort（只记 logcat，绝不把成功的组织写入报成失败；构造失败与落库失败各有用例）；debug 复核屏 `BindingAuditActivity`（题面+绑定点+原文依据+一键 CORRECT/WRONG/AMBIGUOUS，CAS 只落一次，聚合口径与报告同源）；聚合 `BindingAuditAggregator`（按科/模型版本错误率）；报告 `docs/research/2026-10-04-kf29-binding-audit-pipeline-report.md` |
| 批 2 | `b603d076` | **KF-30 自锁打破**：`mergeAutomaticClassifications` 重写（自动接受以 `acceptanceSource` 判源；存量 auto 以接受门常量 `CLASSIFICATION_ACCEPTANCE_CONFIDENCE=0.78` 为基线；严格更高置信的本轮提议可覆盖；用户权威 1.0 受保护；合并=置信度降序稳定排序→（维度+归一化名）去重→每维先钉受保护再填预算）。**KF-07 兜底改落 pseudo**：删除"全绑全部 topic 节点"兜底（三路触发），绑定只由 grounded 原子对生成，空则空绑定，pseudo 交下游 `ensurePseudoKnowledgeBinding`。**DB 不变量随规格放宽**：`validateCommand` 删除"至少一条绑定"（KF-07 规格原文"返回空绑定"），保留"不得混绑"；新增 JVM 校验用例（含变红证明） |

## 2. 门

| 门 | 结果 |
|---|---|
| 全量 JVM（批 1 后 / 批 2 修复后单独重跑） | `test --continue --rerun-tasks` 243 任务 ✓（批 2 一次失败已归因**并发构建污染**——门与实施者自验同时开跑，`NoClassDefFoundError` 在类加载处；单独重跑即绿，非回归） |
| DB 仪器化（批 1 门 + 综合门） | **213/0/0**（批 1，含迁移矩阵 1→62）→ **214/0/0**（综合门，2026-10-04T19:29:01Z） |
| core:data 定向仪器化 | 组织路径 **10/0/0**（批 1 门；批 2 回归修复后同集重跑 ✓） |
| app 仪器化（批 1 门 + 综合门） | 三屏 5/0/0（批 1）→ **全量 53/0/0**（综合门，19:36:20Z） |
| R8 冒烟（综合门） | `:app:assembleLocalFirstRelease` ✓ → 装机 ✓ → monkey 启动 ✓（包名 `com.tingyun.smartmistakebook.localfirst`）→ PID **32185** → logcat 崩溃标记 **0** → 截图=首页 |
| 复核屏真机实测（批 1 F3） | 重打 debug APK → `adb am start` 拉起 ✓（`topResumedActivity=...BindingAuditActivity`）→ 渲染正确（标题/计数/口径行/"错误率不适用（不是 0）"诚实空态/抽样说明）→ 截图存证 |

## 3. 独立复核（逐批；新 Agent、只读、对抗性）

- **批 1**：approve-with-notes。规格符合、迁移非破坏（DDL 与 62.json 独立比对 EXACT MATCH）、抽样/幂等/落判/聚合逐条核过；P2×5 全处理（并发封顶补用例、取舍清单补延迟代价、构造失败补反例、报告补"同一周内"限定）。**协调方实测发现并修复一条复核未抓到的真缺陷**：复核屏 `exported="false"` 与 KDoc 声称的"adb 驱动"矛盾（`SecurityException: not exported from uid`）→ 照 `TestSeedModelConfigReceiver` 先例改 `exported="true"`（debug 源集为边界）并更正 KDoc。
- **批 2**：approve-with-notes（有条件）。F1（KF-07 依赖的 store 放宽 + 新校验测试必须随批提交）**已满足**（5 文件同批提交）；F2（"用户标签保护"分支生产不可达 → KDoc 如实标注为纵深防御）、F4（KF-32 空集重放语义登记）、F5（下游表征测试定位标注）均处理。

## 4. 登记与边界（遗留）

1. **KF-31 未启**（按原前置）：等 KF-29 积累真实错误率数据后再开自动重审。当前 KF-29 样本量 **0**——首份报告如实标注，**不得**表述为"错误率已达标"。
2. **KF-30 的 0.78 基线是实施决策**（分类绑定不持久化置信度，零 schema 下唯一可复核口径；同源引用接受门常量）。三条残余语义登记：(a) 恰好 0.78 的本轮提议与存量同值、永不覆盖（该值恰是门下限）；(b) 存量真实置信度不落库、一律按 0.78 估值 → 0.79 的新提议可顶掉曾以 0.99 接受的老标签（零 schema 固有不对称）；(c) 名字不同的错误 auto 标签只在预算争用时被挤出（章 3/知识点 8），预算未满则留存（add-only 既有语义）。正解 = 未来 schema 增加 accepted-confidence 列，不在本批。
3. **KF-30 用户标签保护分支生产不可达**（调用方 `hasUserCorrection` 早退 + store 权威冲突第二道门把守）——保留为纵深防御，KDoc 已如实标注。
4. **KF-32 空集重放语义（登记，未实现）**：空绑定确认会触发 `BINDING_CHANGED`，但重放派生为空后退回写时快照（`LearningProjector` 的 `ifEmpty` 兜底），历史证据仍挂旧 topic、不清零也不改挂 pseudo——本批只止住**新**证据的错误归属；空集重放语义与仪器化用例留 KF-32 后续。
5. **离线纠正/用户路径的知识分类不携带 labelId**（`UserProblemClassification` 只有 dimension+displayName）→ 改后其绑定为空、由 pseudo 接管；"用户点选的真实树节点未绑定"是**既有缺口**，不在本批。
6. **62.json 资产拷贝顺序边界**：KSP 首次生成 62.json 早于 androidTest assets 的 copy 输入快照（本地修复一次并验证两模块资产均含 62.json）；CI 未复现验证。
7. `validateCommand` 由 `private` 改 `internal`（JVM 纯函数回归用例的测试缝；若要求移除该缝，需改为仪器化用例，代价是同类回归无法在 JVM 门提前变红）。
8. 3D 硬门仍阻塞阶段 5（`docs/research/2026-10-04-stage5-gate-check.md`）；D-0 字段属内核线、与知识包侧车格式相邻，需与 KB 线协调写入窗口。

## 5. 剩余风险

- KF-29 管线"可跑"已证，但**错误率结论要等真实数据**；在数据到位前 3C 的自愈闭环（KF-31）不可开。
- KF-30 残余语义 (a)(b)(c) 在真实使用中可能表现为"老标签被顶掉"——当前无用户数据，风险未量化；正解依赖后续 schema 列。
- KF-07 放宽 DB 不变量后，"确认单元零绑定"成为合法状态，其正确性依赖下游 pseudo 物化（规划/证据侧既有路径）——历史数据不回溯，旧题维持原状。
- 并发构建教训（本条线）：**门的 Gradle 与实施者自验的 Gradle 不得同时开跑**（本轮实测一次污染，归因后单独重跑即绿）。
- Mimosa 扫描长期 inconclusive——不声称任何安全结论。
