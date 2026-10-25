# 知识库封装 · 最终验收标准 v1.0（2026-10-25 定稿）

> 依据：用户 2026-10-25「定下来知识库封装完成的最后验收标准，需要达成的目标；第一性原理、最短路径」指令。
> 推导：知识库的存在目的 = 为错题本垂直领域智能体提供**正确、可溯源、可检索**的四科教学内容（Q1 重定义：
> 三库之一，由智能体调用）。据此验收标准只设五组可测判据（A 内容 / B 检索 / C 接线 / D 卫生 / E 边界），
> 每条给出测量方式；全部达标即宣告「封装完毕」，其后进入维护态 + 增强臂。
> 口径裁定（随本标准冻结）：①主线 43 条 PARTIAL 逐条终审终态化；②dense-union 为验收后增强臂，不挡结案。
> 「达成时点状态」列刷新口径（批次20 结案文书员，2026-10-25）：判据文字与测量方式一字未动，仅末列按**终验快照轮实测**刷新（快照 = `build/agent-batch20/acceptance-snapshot.md`；读数 = 快照轮 2026-10-25 实测/对拍，C1 快照轮未重跑项如实标注），逐条附证据路径；复算命令见 `docs/kb-acceptance-2026-10-17.md` §10 与 `docs/kb-batch20-2026-10-25.md` 附录。

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
