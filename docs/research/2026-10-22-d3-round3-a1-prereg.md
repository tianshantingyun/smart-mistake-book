# D-3 Round-3：A1 保守别名臂 · 预注册（2026-10-22）

> 出数前冻结；事后 §1–§6 一字不动，只填 §7。依据：`docs/kb-direction-2026-10-22.md` 与批次16 弱章诊断。

## 1. 臂定义

A1 = 把 `knowledge-production/alias-exceptions-candidate-2026-10-22.csv`（**31 条 / 21 节点，T/C-only**，材料标题/正文派生；题干式 Q 类已全部剔除）并入 `tools/kb_build/tables/alias_exceptions.csv` →
`rebuild_aliases --write`（干跑已核：应用 33=既有 2+新增 31、冲突 0、拒绝 0、变更节点 21）→
`promote`（**v20**；v19 已由 19 条旧标签修正占用）→ `tools/dense_build` 全链重打 + 重钉。

## 2. 判读

- 主判读：设备金标融合臂（真 SQL + LiteRT + `DenseFusion`，B512 召回 + 重排 + top5），基线 **A0 = 100/129 = 0.7752**（`build/agent-batch15/d3/device-metrics.txt`）。
- 对照：设备词面臂（A0 = 89/129 = 0.6899）；JVM 双路仅作装置保真，不判分。
- **三采纳规则**：主集严格更高 + 24 章逐章不劣（任一章严格更低即不采纳）+ 负向守卫 CI 上界 ≥0。
- **零漂移**：device↔JVM 同路 MISS 题面集合对称差必须 0；非 0 = 装置缺陷，该臂不判读。
- 显著性：按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；A=A1、B=A0。

## 3. 装置门（写死，未过不得启动）

`:core:data:compileDebugUnitTestKotlin` 必须为 **0**。2026-10-22 实测 **1**，错在他线在飞文件
`core/data/src/main/kotlin/.../capture/ExplicitRedrawService.kt:65 Unresolved reference 'SHA_256_HEX_CHARS'`
（此前为 `FigureGenerationAuditRecorderTest.kt:439 eventsAttemptId`，已被他线修掉）。门未过时：不写 A1 包、不跑设备、不判读。

## 4. 冻结输入（实算，2026-10-22）

| 项 | 值 |
|---|---|
| 包 | `moe-2025-update-manifest.json` version=**19**、contentVersion=**c4a72c0755dc6645**；主包 sha `f6098c41d4901d56…`（v18→v19 仅改 sidecar 文本，主包未变） |
| 点数/材料 | 425 主题 / 3,245 点 / 64,973 材料 / 25 sidecar |
| dense | `.vec` sha `8e216ee1719b8a18…`（44,010 条 = 3,245 + 40,765） |
| 金标 | `golden_queries_v2.json` sha `eabef102…`（129 条 / 24 章） |
| A0 账本 | `build/agent-batch15/d3/a0-device-{fused,lexical}-misses.txt`、`a0-jvm-{fused,lexical}-misses.txt` |
| 补丁 | `knowledge-production/alias-exceptions-candidate-2026-10-22.csv`（31 行数据） |
| 判官 | 设备 `GoldenRetrievalInstrumentedTest.kt`、JVM `GoldenRetrievalJvmTest.kt`（现状冻结，不改判据） |

## 5. 回滚

写盘前记录：包主文件、25 sidecar、`.vec`、`.vec.json`、`alias_map.csv`、`alias_dropped.csv`、`DenseRecallAssembly.VECTOR_ASSET_SHA256`、`ProductionLexicalLegExportTest` 锚点、dense 夹具（scan/order/golden-query-vectors/tokenizer 摘要）的 sha。
不采纳：逐字回滚并核验每个 sha 回到基线；采纳：按实测重钉上述钉值并留因果注释。

## 6. 预期与边界（不承诺）

- 模拟词面 **89 → 102（+13）**；29 条融合 MISS 中 **13 条**有望进词面 top5（氢氧化铝、铁、镁、酯化、亚硫酸、对勾、圆锥、二项式、隔板、特征根、斐波那契、等比、胡克）。
- **到不了 0.90**（需 117/129，即 +17）；4 个 absent 中只能救回**镁**，等和线/活结与死结模型/力的分解仍需 dense-union 或内容侧施工。
- 截断观察点：净增来自片段重选而非别名入索引的行（见干跑截断清单），换顺序/再增删别名可能消失。

## 7. 结果节

（待填：A1 实测主集/逐章/MRR/墙钟/零漂移/显著性/三规则逐条/采纳或回滚与 sha 核验）
