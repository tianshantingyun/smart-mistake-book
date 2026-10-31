# 知识库封装 · 最终验收标准 v1.0（2026-10-25 定稿）

> 依据：用户 2026-10-25「定下来知识库封装完成的最后验收标准，需要达成的目标；第一性原理、最短路径」指令。
> 推导：知识库的存在目的 = 为错题本垂直领域智能体提供**正确、可溯源、可检索**的四科教学内容（Q1 重定义：
> 三库之一，由智能体调用）。据此验收标准只设五组可测判据（A 内容 / B 检索 / C 接线 / D 卫生 / E 边界），
> 每条给出测量方式；全部达标即宣告「封装完毕」，其后进入维护态 + 增强臂。
> 口径裁定（随本标准冻结）：①主线 43 条 PARTIAL 逐条终审终态化；②dense-union 为验收后增强臂，不挡结案。
> 「达成时点状态」列刷新口径（批次20 结案文书员，2026-10-25）：判据文字与测量方式一字未动，仅末列按**终验快照轮实测**刷新（快照 = `build/agent-batch20/acceptance-snapshot.md`；读数 = 快照轮 2026-10-25 实测/对拍，C1 快照轮未重跑项如实标注），逐条附证据路径；复算命令见 `docs/kb-acceptance-2026-10-17.md` §10 与 `docs/kb-batch20-2026-10-25.md` 附录。
> 批次22 v21 状态刷新（2026-10-26 追加）：v21 内容更新 adopted=true；判据文字与测量方式未动，原「达成时点状态」列保留 2026-10-25 v20 终验快照；v21 刷新见文末「批次22 v21 达成状态刷新」节。依据：`docs/research/2026-10-26-kb-v21-content-update-prereg.md`、`docs/kb-batch22-2026-10-26.md`、`docs/kb-acceptance-2026-10-17.md` §11。

## A. 内容完备与正确

| # | 判据 | 测量方式 | 达成时点状态 |
|---|---|---|---|
| A1 | 结构与卫生门：425 主题 / 3,245 点 / 64,973 材料 / 25 侧车；乱码、残迹、判定源↔包漂移、切片、资产门全绿 | `run_kb_checks.py` 等（八门） | **v20 已绿（快照轮实跑）**：八门 rc 全 0、overall red=0（2026-10-25 16:51–16:55；`build/agent-batch20/gates/summary.txt` + 逐门 log ×8） |
| A2 | 覆盖终态（F14 主线分母 426）：COVERED + 豁免 + PARTIAL **每条终态化**——43 条逐条「未单设子面在目标节点材料正文的命中核实」，三终态：升 COVERED（证据落库）/ 维持 PARTIAL（带核实注记）/ 转豁免（性质判定格式：性质判定+无承载+检索证据） | `kb-coverage-alignment-2026-v2.json` 的 `finalReview` 字段 + CSV 投影 + `alignment --check`；台账重算 | **已 applied（终审落账）**：43 行裁定 UPGRADE 26 / KEEP 16 / WAIVE 1（`build/agent-batch20/partial-review/decisions.json`；逐行 43 行表 = `knowledge-production/coverage-final-review-2026-10-25.md`）；主线 426 = COVERED **400** / PARTIAL **16** / NOT_COVERED **10** / UNDECIDED 0，覆盖 **0.9390**（独立重算 == 台账 counts == 书记员对拍三口径+四分科全 MATCH；`build/agent-batch20/gates/coverage-recalc-r20.log`）；`finalReview` 43 行形态/零改写/行序 + CSV 27 行差异 + 豁免 9+1 断言 exit 0（收口复跑 `build/agent-batch20/scripts/final_review_verify_r20.py`） |
| A3 | 溯源诚实：豁免格式合规、19 条旧标签已修（v19 落地）、零伪造 provenance | 豁免 CSV 与台账对拍；sidecar 来源字段抽验 | **终审轮复验绿**：`f14-waivers.csv` 既有 9 行字节不动 + 新增 1 行 WAIVE（`required-chemistry-social-development-candidate-001`，reason=性质判定+无承载+登记豁免）= 10 行，与台账对拍一致（verify 脚本断言[3]）；零伪造 provenance 由 kb_checks（consistency/judgment_drift/judgment_fp rc=0）覆盖 |

**边界（登记，不属于 A 组失败）**：选修 235 单列（无源）；A 层 60,512 封层；84 空点撤回；判定池已清零 ⇒ 新增材料需启用自产料池走门 + 晋升，会使已测检索基线失效，故本批**零写包**，薄料补料列验收后维护项。

## B. 检索质量（金标冻结：`golden_queries_v2.json` 129 条 / 24 章 / top5 / 512 深度 / 256 探针；读数不外推）

| # | 判据 | 测量方式 | 达成时点状态 |
|---|---|---|---|
| B1 | 设备融合主集 **Recall@5 ≥ 0.90**，且该读数经全部门（三规则 + 零漂移 + 显著性 + 预算） | 设备 `GoldenRetrievalInstrumentedTest`（判官不改）；验收数字 = 终验快照轮实测 | **0.9069767（117/129）已达成**（A1 臂采纳：B−A=+0.1318，CI [0.0769,0.1926]，p=0.0001；24 章 12 升 12 平 0 降；修复 17 零新增）；**终验快照轮 2026-10-25 实测复现同值**（117/129、MRR 0.7322997416020672、`am instrument` OK (1 test)；装置同代核验 worktree=local=installed=`0536d0d1…` MATCH——`build/agent-batch20/drill/device-metrics.txt:2-5`、`drill/apk-samegen-check.log`） |
| B2 | 同路零漂移：device↔JVM MISS 集合对称差 = 0 | 零漂移门 | **快照轮 PASS/PASS**：fused symdiff=0 / lexical symdiff=0（`build/agent-batch20/drill/zero-drift.json`、`drill/{device,jvm}-{fused,lexical}-misses.txt`）；账本链 A0（batch15/d3）→ A1（batch19/a1）→ 快照轮（batch20/drill）在位 |
| B3 | 预算：融合 p95 ≤250ms / p50 ≤150ms、overBudgetSamples=0 | 设备指标行 | **快照轮实测 p50=38 / p95=48 / max=116ms，overBudgetSamples=0**（`build/agent-batch20/drill/device-metrics.txt:2`，预算行明示 250/150ms；词面 p50=7 / p95=13，`:32`；A1 轮历史读数 38/51 保留于批次19 文书） |
| B4 | 剩余 MISS 逐条登记为已知边界：9 条域内排序（dense 名次 8/9 在 K=64 内）+ 2 条 absent（活结 dense 第 2、力的分解第 7，union 结构可救）+ 等和线词汇缺口（内容不缺：23 别名 / 32 块材料挂对节点；dense 全科 336；题干式别名按「评测禁止面向结果」禁用） | `a1/device-fused-misses.txt` + `dense-union-estimate.json`；并集结构上限 ≈119/129 | **已登记（快照轮续记）**：快照轮 fused MISS 12 条 = A1 采纳时 12 条同集、零新增（`build/agent-batch20/drill/device-fused-misses.txt`；边界结构同 A1 轮定性）；并集结构上限 ≈119/129 与 dense-union 预估维持批 19 登记 |

## C. 生产接线

| # | 判据 | 测量方式 | 达成时点状态 |
|---|---|---|---|
| C1 | `KNOWLEDGE_READ` 查询深度 = 512（`MAX_KNOWLEDGE_RECALL_CANDIDATES`）+ 模型可见 top-5 + 2k 预算 | `KnowledgeReadRecallBudgetTest` 钉住；`:core:data` 全套单测绿 | 1033/1033 绿（**批次19/20 存档读数**，`build/agent-batch19/a2-evidence-summary.md`；快照轮未重跑——如实登记，见快照 §8） |
| C2 | 端侧融合在产：denseLegLive=true | 设备指标行 | **true（快照轮实测）**：`build/agent-batch20/drill/device-metrics.txt:2` `denseLegLive=true` |

## D. 工程卫生

| # | 判据 | 测量方式 | 达成时点状态 |
|---|---|---|---|
| D1 | 八门全绿且**全库无登记红**（ProductionLexicalLegExportTest 的 ULP 锚分歧裁定后） | 八门脚本 + 单测 | **裁定已落码、门转绿（fixed-green）**：`V1_D1_MRR` 重钉 `0.6206718346253232` 并附裁定注释（Kotlin 回读重算 + 设备实测两源一致；…229 为 Python④ stage3 外源单源——`ProductionLexicalLegExportTest.kt:365-366`；备份 `build/agent-batch20/backup/ProductionLexicalLegExportTest.kt.pre-ulp-2026-10-25`）；本轮单测 ProductionLexicalLegExportTest **1/1** 绿 + DenseAssetProvenanceTest **2/2** 绿（test-results XML 实读）；八门 rc 全 0（快照 §4） |
| D2 | 版本/钉值/账本一致：manifest（v20，`ebf477816c318704`）、`VECTOR_ASSET_SHA256`（0536d0d1…）、词面锚、dense 夹具、A0/A1 账本链 | 逐 sha 对拍脚本 | **快照轮逐项对拍 ✓**（快照 §6）：manifest v20/`ebf477816c318704`（manifest:4–5）；`.vec` sha `0536d0d1…` 本轮实算 == `DenseRecallAssembly.kt:107` 钉值 == APK 内嵌（同代 MATCH）；词面锚 …232 与设备词面 MRR 逐位一致；A0/A1/快照轮账本链在位；包面卫生 `git status --porcelain -- core/data/src/main/resources/` = 空 |
| D3 | 文书可复算：验收文书（含本标准 §10 落章）、批报、预注册结果节，逐项附复算命令 | 人工 + 复算脚本 | **本批落章**：本列已按快照刷新；验收 §10 落章（`docs/kb-acceptance-2026-10-17.md`）；批报 `docs/kb-batch20-2026-10-25.md`（附录复算命令）；标题臂预注册 §7 补批次20 clean 重测收束（§7.11）；五组判据逐条复算命令见验收 §10 逐判据表（`docs/kb-acceptance-2026-10-17.md` §10.1–§10.4） |

## E. 边界与移交（登记终态，不挡验收）

- E1 A 层 60,512 封层（批 14 预注册裁量）/ 选修 235 单列（无源）/ 84 空点撤回 / 91 容量历史关闭 / 3 条金标-绑定冲突接受为边界。
- E2 182 拆分提案（50 REJECT / 132 DEFER）、114 重开提案、402 同键分类 = AI 预裁完成，**用户复核开放中**；复核结果走后续维护版本。
- E3 验收后增强臂（预注册在库，不挡结案）：dense-union 并集臂（core/database 跨线，结构上限 ≈119/129）、标题向量臂（**批次20 clean 重测已收束**：同代装置出数 118/129、零漂移 PASS/PASS，预注册 §2 规则②否决——数学选必二第四章·数列 10/11→9/11 严格更低，裁定 adopted=false、逐字回滚，stateAfter=v20+`0536d0d1…`；`build/agent-batch20/title-final-r20.md`、预注册 §7.11）、系统性别名扩充（不针对金标定向）。
- E4 等和线：检索词汇缺口，登记；「灯被两绳吊」实例材料缺失（力的分解）同登。

## 达成宣告条件

A1–A3、B1–B4、C1–C2、D1–D3 全部「实测达标 + 证据路径可复算」且 E 组逐条登记 → 验收文书 §10 落章宣告**封装完毕**，转入维护态。

## 批次22 v21 达成状态刷新（2026-10-26 追加）

> 本表只刷新「达成时点状态」，不改判据文字与测量方式；原表（上）保留 2026-10-25 v20 终验快照。v21 内容更新裁定 **adopted=true**，四条非劣判据全过，未回滚；v21 为内容更新后新基线，**不主张涨分**。证据主体 = `docs/kb-batch22-2026-10-26.md`、`build/agent-batch22/` 产物、`docs/kb-acceptance-2026-10-17.md` §11。

| # | v20 终态（2026-10-25） | v21 刷新（2026-10-26） | 证据 |
|---|---|---|---|
| A1 | 八门 rc=0、overall red=0（v20 快照） | **维持**：八门 rc=0、overall red=0；结构 425 主题 / 3,245 点 / 64,973 材料 / 25 侧车（包结构只改别名；dense 资产/测试夹具另有重打） | `build/agent-batch22/gates/summary.txt:1-17`；`build/agent-batch22/consistency.txt` §1/§6 |
| A2 | 400/426 = 0.9390（43 行终审 applied） | **维持**：本批未改覆盖台账、未重跑覆盖重算；数值沿用 v20 终验 | 本文件 A2；验收 §10.2（本批未重跑） |
| A3 | 终审复验绿（豁免 10 行、零伪造 provenance） | **维持**：25 sidecar 聚合 `ed1c6d4f…` 不变；本批材料零写 | `build/agent-batch22/consistency.txt` §6；`build/agent-batch22/material-touched-files.txt:1-15` |
| B1 | 117/129 = 0.9069767；MRR 0.7322997416020672 | **117/129 = 0.9069767441860465**；MRR **0.7322997416020672**；p50=40 / p95=57 / max=164、overBudget=0；同代 MATCH | `build/agent-batch22/v21/device-metrics.txt:2-5`；`build/agent-batch22/v21/step3_run_device.log:19-24`；`build/agent-batch22/v21/post-install-samegen.txt:2-5` |
| B2 | PASS/PASS（fused/lexical symdiff=0） | **PASS/PASS**：lexical 27/27/common 27、fused 12/12/common 12，symdiff 0 | `build/agent-batch22/v21/zero-drift.json`；批报 §6.1 |
| B3 | p50=38 / p95=48 / max=116、0 超预算 | **p50=40 / p95=57 / max=164、0 超预算**（预算 150/250） | `build/agent-batch22/v21/device-metrics.txt:2` |
| B4 | 12 条 fused MISS；等和线 23 别名 / 32 块材料 | **12 条同集**；等和线 **25 别名**（23→25，含主名）/ 32 块材料；单点四路 `absent`；无新增 MISS | `build/agent-batch22/v21/device-fused-misses.txt`；预注册 §1.1/§2.4；批报 §5.2/§8 |
| C1 | 1033/1033（批次19/20 存档；快照轮未重跑） | **未重跑全套**；批次22 仅定向 `GoldenRetrievalJvmTest` + dense 4 类；全套证据仍为存档 1033/1033 | `build/agent-batch22/dense-jvm-tests.log:1,100`；`build/agent-batch22/v21/step5_run_jvm.log`；验收 §11.3 |
| C2 | denseLegLive=true（快照轮实测） | **true** | `build/agent-batch22/v21/device-metrics.txt:2` |
| D1 | fixed-green（ULP 锚重钉 …232；1/1+2/2 绿；八门 rc=0） | **八门 rc=0；ProductionLexicalLegExportTest 锚未漂（102 / 0.7906976744186046 / 0.6206718346253232）；定向 4 类绿；全库单测未重跑** | `build/agent-batch22/gates/summary.txt`；`build/agent-batch22/consistency.txt` §3；`build/agent-batch22/dense-jvm-tests.log` |
| D2 | v20 / `ebf477816c318704` / `0536d0d1…` | **v21 / `9f9e99ceec47cf8c` / 主包 `a7192484…` / `.vec 79a22270…`（44,043 行）/ alias 35·37,553·22,559** | `build/agent-batch22/consistency.txt` §1-§3；本文书轮 sha 复核 |
| D3 | v20 落章（验收 §10 + 批报 + 预注册） | **v21 落章**：验收 §11 + 批报 + 预注册 §7.10 + 本刷新节 | `docs/kb-batch22-2026-10-26.md`；`docs/kb-acceptance-2026-10-17.md` §11 |
| E1 | 登记终态 | **维持**（A 层封层 / 选修 235 / 84 空点 / 91 容量 / 3 条冲突） | 验收 §10.5 |
| E2 | 182/114/402 用户复核开放中 | **维持**（本批未处理） | 验收 §10.5 |
| E3 | dense-union 待跨线；标题向量臂已收束 | **dense-union 批次21 终裁不采纳、已逐字回滚；标题臂已收束；v21 内容更新不是增强臂** | `docs/kb-batch21-2026-10-26.md:280-285`；验收 §11.3 |
| E4 | 等和线词汇缺口；薄料维护项 | **等和线 25 别名、单点四路 absent、32 块材料；剩余 4 条候选不写；薄料行 23 KEEP、本批零写** | 预注册 §1.1/§2.4；`build/agent-batch22/material-touched-files.txt:1-15`；批报 §8 |

**刷新后的达成判断**：v1.0 达成宣告**维持（不撤销）**——B1 **117/129 = 0.9069767441860465 ≥ 0.90**；B2/B3/B4 按 v21 实测；B1/B2/B3/B4/D2/D3 已按 v21 刷新；A1/D1 八门与结构对拍通过；A2/C1 未重跑/未重算，沿用 v20 终验并如实标注；E 组维持登记。v21 为新基线，**不主张涨分**；C1 全套未在 v21 重跑，不能写成“v21 全套绿”或“v21 全套复验通过”。

## F. 使用面交付（v1.1 增补，2026-10-26）

> 本节把 `KNOWLEDGE_READ` 的**材料分配与渲染形态**纳入验收；v1.0 的 A–E 判据与 v21 刷新**原文不动**，本增补使标准从 v1.0 升为 **v1.1（生产成熟口径）**。判据来源 = `docs/research/2026-10-26-kb-usage-r1-prereg.md`（出数前冻结）；实现与读数 = `docs/kb-batch23-2026-10-26.md`；复测 = `build/agent-batch23/usage-r1/retest-r1.log`。F1–F5 只判**使用面**，不重判 B1–B4 检索质量，也不主张检索涨分。

### F.1 判据与实测

| # | 判据（v1.1） | 测量 / 口径 | 实测值 | 证据 | 复算 |
|---|---|---|---|---|---|
| F1 | 每节点材料覆盖 **≥90%** | 分母 = 129×5 中**有绑定材料**的节点行；分子 = 其中获得材料摘要的节点行 | **100.0%（645/645）** | `build/agent-batch23/usage-r1/retest-r1.log:6` | `python "C:/Users/听云/AppData/Local/Temp/retest_r1_batch23.py"` |
| F2 | 期望节点有材料 **≥90%** | 分母 = 129；分子 = `expectedSlug` 在 top-5 内**且**获得材料摘要的查询数 | **90.7%（117/129）**；= 召回命中上限（12 条 MISS 结构上不可能有材料；117/117 命中查询全覆盖；余量 **0.70pp**） | `retest-r1.log:8` | `python "C:/Users/听云/AppData/Local/Temp/retest_r1_batch23.py"` |
| F3 | 误区槽位占比 **≤20%** | 分母 = 645 个被选中材料槽；分子 = `MISCONCEPTION_GUIDE` 槽 | **1.24%（8/645）**；`CONCEPT_EXPLANATION 563 / METHOD_MODEL 74 / MISCONCEPTION_GUIDE 8` | `retest-r1.log:10-11` | `python "C:/Users/听云/AppData/Local/Temp/retest_r1_batch23.py"` |
| F4 | 无材料节点**显式标注** | 金标 top-5 中绑定材料数为 0 的节点行；每行必须出现 `（该节点无材料）`（渲染为 `｜（该节点无材料）`），且不得出现 `｜材料：` | **金标 0/0（空真）**；由单测夹具证明标记真实渲染 | `retest-r1.log:13`；`core/data/src/test/.../UsageMaterialAllocationTest.kt:181-233`；`KnowledgeReadRecallBudgetTest.kt:195-250` | `./gradlew :core:data:testDebugUnitTest --tests '*UsageMaterialAllocationTest*' --tests '*KnowledgeReadRecallBudgetTest*'` |
| F5 | **预算与形态不变** | 材料条数/查询 = 5；渲染文本 `length`（UTF-16 码元）≤ 2,000；`summary.take(60)`、boundary `take(80)`、header/代号/行结构不变 | **5 条/查询（129/129）**；chars min/median/max **660 / 743 / 838**（max **41.9%** of 2,000 chars）；UTF-8 max **2,267 B**（**113.3%** of 2,000 B，R5 契约侧） | `retest-r1.log:14-18`；`UsageMaterialAllocationTest.kt:287-389` | `python "C:/Users/听云/AppData/Local/Temp/retest_r1_batch23.py"` |

### F.2 判读

- **F1/F2/F3/F5 为实测**：同一批 129 条金标 top-5、同一检索/渲染镜像；top-5 的融合 Recall@5 仍为 **117/129 = 0.907**（`retest-r1.log:1-2`），与仓库 JVM 账本一致，未重挑样本。
- **F2 的算术上限**：117/129 = 90.70% 是检索召回本身的天花板；12 条 MISS 的期望节点不在 top-5，结构上不可能有材料。F2 **贴着上限通过**（117/117 命中查询全覆盖），任何一条命中查询丢材料都会跌破 90%。
- **F4 的空真**：金标 645 行全部有材料，`金标 F4 = 0/0` **不算通过**；通过依据是单测夹具：`UsageMaterialAllocationTest.aNodeWithoutMaterialsRendersTheExplicitMarkerWithoutDisturbingMaterialNodes` 与 `KnowledgeReadRecallBudgetTest.everyRenderedNodeGetsItsOwnMaterialAndAMaterialslessNodeIsMarkedExplicitly`。
- **F5 的单位**：2,000 是**字符**（UTF-16 码元），不是 UTF-8 字节；max 838 chars 仅 41.9%。UTF-8 max 2,267 B 已越 2,000 B，这是 **R5 的契约侧问题**，不得写成“2k 字节通过”。
- **排序 ≠ 语义质量**：F3 下降是冻结教学序（`CONCEPT_EXPLANATION → … → COMPLETE_SOLUTION`）的排序结果；未做人工语义评分，不主张摘要/材料语义最优。

### F.3 复算命令（本文书轮实跑）

```bash
cd "D:/smart mistake book"

# 只读复测：同 129 条金标 top-5 + 批次22 审计镜像 + SQLite 逐字新 DAO SQL
python "C:/Users/听云/AppData/Local/Temp/retest_r1_batch23.py"
# 关键输出（逐字）：
# FUSED mirror Recall@5 117 / 129 = 0.907
# base rows 645 with_material 645 summary 301 F1 46.67 | F2 65 / 129 50.39 | F3 491 / 645 76.12
# rows 645 rows_with_material 645 py_summary_rows 645 F1 100.0 | sql_summary_rows 645 F1_sql 100.0
# py 117 / 129 90.7 | sql 117 / 129 90.7 | recall hits 117
# py slots 645 {"CONCEPT_EXPLANATION": 563, "METHOD_MODEL": 74, "MISCONCEPTION_GUIDE": 8} misconception 8 F3 1.24
# no_material_rows 0 marked 0 fake_material_segments 0
# rows/query py {"5": 129}
# render chars(UTF-16) min/median/max 660 743 838 | max pct of 2000 chars 41.9
# render utf8 bytes min/median/max 1707 1985 2267 | max pct of 2000 bytes 113.3
# checked_sql_rows 645 mismatch_queries 0 mismatch_nodes 0

# 夹具与定向测试（批次已跑；本文书轮未重跑 Gradle）
# 完整 argv 未落盘；下列为 batch23-targeted.log:1 记录的任务/过滤
:core:data:testDebugUnitTest :core:model:test :core:domain:test \
  --tests *UsageMaterialAllocationTest* --tests *KnowledgeReadRecallBudgetTest* --tests *TutorTool*
# 结果：batch23-targeted.end.txt EXIT_CODE=0；batch23-targeted.log:7095 BUILD SUCCESSFUL in 4m 21s

# 读数原件
cat build/agent-batch23/usage-r1/retest-r1.log
```

### F.4 交付边界与 R2–R6 归属

- **F1–F5 判据达标**，但 **v1.1 交付状态为 PARTIAL，不称全绿**：R1 代码当前 **7 tracked ` M` + 1 个新测试 `??` 未提交**；预注册计划中的**真 Room 仪器化测试未创建**（`KnowledgeTeachingMaterialPerNodeQueryInstrumentedTest` 不存在）；**独立红测轮日志缺失**；F1–F5 是离线镜像，不是生产遥测；设备为 emulator，未在真机/API 23 跑。
- **R2–R5 属契约侧交接**（智能体面披露/截断/预算语义，本批不修）：
  - **R2** 材料标题/正文缺失（只给 ≤60 字摘要）——审计 `docs/research/2026-10-26-kb-usage-surface-audit.md:83-112`，归属 `:98-100`；
  - **R3** `take(60)` 静默截断（27.2% 展示摘要被切、640 条 LaTeX 切口损坏）——审计 `:113-147`，归属 `:133-135`；
  - **R4** boundary `take(80)`（82.3% 被切、58.7% 范围短语看不到）——审计 `:148-179`，归属 `:165-167`；
  - **R5** 2k 名义值、`KNOWLEDGE_READ` 无运行时检查点、UTF-8 max 2,267 B > 2,000 B——审计 `:180-208`，归属 `:194-196`。
- **R6 属数据级维护项**：13 条 `summaryMarkdown` / 6 条 boundary 含真实换行（LaTeX `\ne` 转义、PHYSICS 结构换行）——审计 `:209-240`，归属 `:221`；修 sidecar，不改本批 F1–F5 判据。
- **R1 本身**（本批修复）只覆盖「每节点一条 + 教学序 + 无材料显式标记 + 渲染形态」；不把 R2–R6 算作已修，也不把 F1–F5 达标外推为 R2–R6 关闭。

### F.5 性能门与交付门（v1.2 增补，2026-10-31）

> 本节把**首启导入性能**与 **R3/R4 截断安全**纳入验收；A–E 与 F1–F5 判据文字**原文不动**，本节使标准从 v1.1 升为 **v1.2**。F6 是**新增红门**（旧基线从未满足 60 s 门槛），F7 是 R3/R4 的交付判据。判据来源与读数 = `docs/kb-batch27-2026-10-31.md` §4–§5；证据 = `build/kb-perf/`、`build/agent-batch23/prod-drill/`、`core/data/.../SafeTruncationTest.kt`。本节不重判 B1–B4、不主张检索涨分、不撤销 v1.0/v1.1 的已落章结论。

| # | 判据（v1.2） | 测量 / 口径 | 实测值 | 判定 | 证据 / 复算 |
|---|---|---|---|---|---|
| F6 | **首启导入 ≤60 s**（冷装全量路径） | `KnowledgeInstall: install finished in … ms (full path: parse + reconcile)`；同一装置、干净安装 | 批次23 存档 **186,574 ms**；10-31 冷装演练 **256,398 ms**（墙钟 **264,438 ms**）；快路径仅 **446 / 1,378 ms** | **NOT_MET（红）** | `build/agent-batch23/prod-drill/step1_clean_install_launch.log:60`；`build/kb-perf/cold-install-drill.log:22,38,53`；复算见批报 §4 |
| F7 | **截断安全**（R3/R4） | `safeTruncateMarkdown`：公式内切口退到公式起点；否则退到句号/分号；无安全点回退旧 `take`；形态仍 5 行 / boundary 80 / 摘要 60 / 2k 字符 | `SafeTruncationTest` **11 tests / 0 failures**（JVM）；设备端真实 `KNOWLEDGE_READ` 截断抽验**未做** | **PASS（JVM）**；设备端 **UNVERIFIED** | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/SafeTruncationTest.kt`；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt:91,447,449`；`./gradlew :core:data:testDebugUnitTest --tests "*SafeTruncationTest"` |

- **F6 是性能事实的显式化，不是回归**：旧读数 149–178 s（批次20–22）本就高于 60 s；本门槛首次把“首启导入慢”判红。搜索索引重建占 59.8%（186,574 ms 轮）至 67.1%（256,398 ms 轮）。
- **F7 的绿只覆盖纯函数 + JVM 夹具**：公式回退、句界回退、保底 `take`、2k 字符预算均有断言；未在设备上用真实 `KNOWLEDGE_READ` 结果抽验，也未覆盖 R2（标题/正文缺失）与 R5（2k 字节越界）。
- **v1.2 状态**：F6 红、F7 JVM 绿 ⇒ **v1.2 未全部达标**；F1–F5 的 v1.1 PARTIAL 状态不变。不得把本节写成“性能已达标”。
- **批次28 v23 复测（2026-10-31，只追加）**：v23 首启全量 **127,014 ms**（`build/agent-batch28/v23/device-logcat.txt:1609`），仍 >60 s ⇒ **F6 维持 NOT_MET**；本轮四科索引重建合计 **88,913 ms（70.0%）**，其中 CHEMISTRY 1,126 节点 / 169,652 特征行 / 18 插 / 1 删 / **27,042 ms**（`:1244`）。批次23 device-logcat 轮 install **163,334 ms**（`:677`），CHEMISTRY 分段 1,118 / 168,290 / 18 / 1 / **28,447 ms**（`:643`）。这些读数不是同一轮，不作跨轮性能结论。
- **批次29 F6 复测与状态更新（2026-10-31，只追加）**：导入路径优化后，在本机做 **三次同口径冷装**（uninstall → install → 冷启动；同 APK sha `2c9227b6…`、同 `emulator-5584` / API 34、同 v23 内容包）测得 `KnowledgeInstall: install finished in` **47,504 / 51,775 / 51,646 ms**（中位 **51,646 ms**，最大 **51,775 ms**）——**三次全部 ≤60 s ⇒ F6 达成（MET）**。证据：`build/f6-retest-20261031/run{1,2,3}/install-finished.txt`；四科索引段 88,913 → 5,436–5,928 ms（−93.3% ~ −93.9%），解析 20.7–21.2 s 成为主段；复算与本文书轮实跑见 `docs/kb-batch29-2026-10-31.md` §2–§4。**边界**：装置为 emulator，非真机；最大读数距门槛仅 **8,225 ms（13.7%）**；设备端检索指标本轮未重测。F7 状态不变（JVM 11/11 PASS，设备端 UNVERIFIED）。
- **当前状态（截至批次29）**：F6 **MET**、F7 JVM 绿但设备端 UNVERIFIED ⇒ 性能门已过，但 **v1.2 不能称全绿**；F1–F5 的 v1.1 PARTIAL 状态不变。
