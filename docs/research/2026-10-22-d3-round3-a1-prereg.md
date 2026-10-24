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

> 填写口径（批次19 收口文书员，2026-10-25）：§1–§6 一字未动。数字三类标注——**既有产物实读**（`build/agent-batch19/` 两臂执行轮落盘产物）、**本代理实跑**（收口轮命令与脚本，逐处注明）、**执行轮实跑**（臂执行员日志）。裁定依据 = `build/agent-batch19/a1-final.md`（A1 臂收束结果：**adopted**）。

### 7.1 出数时间线（执行轮实跑，`a1/judge-files-record.md`）

- 2026-10-24 22:15 装置门首跑 **RED**：他线在飞未跟踪新文件 `core/domain/.../PaperAnswerKeyChain.kt:139` 块注释未闭合（Kotlin 嵌套注释，`a1/comment_nesting_probe.py` 定位 EOF depth=2）。按 §3 门未过：不写包、不跑设备、不判读，轮询待命。
- 他线 22:35 自行修掉该注释；门经 7 轮轮询于 **01:01:38 转绿**（`:core:data:compileDebugUnitTestKotlin` exit=0，`a1/step1-gate-poll2.log`）；01:07 门 + `:core:data:assembleDebugAndroidTest` exit=0，BUILD SUCCESSFUL（`a1/step1_compile_and_assemble.log`，APK sha `5deb90d7…`）。
- 01:10–01:17 设备 `am instrument` **OK (1 test)** + 两账本取回；01:18 JVM 判官 BUILD SUCCESSFUL；01:19:41 零漂移 exit=0；01:24–01:25 显著性落盘。判定 **adopted**（`build/agent-batch19/a1-final.md`）。

### 7.2 主判读读数（设备融合臂，既有产物实读）

- 融合主集 **Recall@5 = 0.9069767441860465（117/129）**、**MRR = 0.7322997416020672**（`a1/device-metrics.txt:3-5`）；冻结基线 A0 = 100/129 = 0.7752（§2）。
- 逐章 24 章分布（`a1/device-metrics.txt:6-30`）：12 章严格更高、12 章相同、**0 章更低**（本代理实跑核验：读 `a1/significance-fused-vs-A0.json` 的 `a.per_chapter`（A0）与 `b.per_chapter`（A1）逐章对比，24/24 可比、strictly_lower_in_b=0、equal=12）。
- 融合 MISS **12 条**（`a1/device-fused-misses.txt`）：9 条域内（rank 7–51）+ 3 条 absent（等和线、活结与死结模型、力的分解）。
- 墙钟：融合路由 **p50=38ms / p95=51ms / max=84ms**，p95Budget=250ms / p50Budget=150ms，**overBudgetSamples=0**，denseLegLive=true（`a1/device-metrics.txt:2`）。

### 7.3 对照臂与 JVM 保真（既有产物实读）

- 设备词面臂 **102/129 = 0.7906976744186046**、MRR 0.6206718346253232、p50=7ms / p95=13ms / max=18ms（`a1/device-metrics.txt:32-35`）——§6 预测「模拟词面 89→102（+13）」**逐位兑现**；词面 MISS 27 条。
- JVM 保真（不判分）：融合镜像 **117/129、MRR 0.7322997416020672 与设备逐位同**（`a1/jvm-fused-metrics.txt`）；B-route 词面镜像 **102/129、MRR 0.6206718346253232 同**（`a1/jvm-metrics.txt` `== B-route-mirror(v1-bare-B5, matched-first) ==` 段）。
- 翻转分解（本代理实跑，四份 MISS 账本集合运算：`build/agent-batch15/d3/a0-device-{fused,lexical}-misses.txt` 29/40 条 vs `a1/device-{fused,lexical}-misses.txt` 12/27 条）：A0→A1 融合修复 **17 条、新增 MISS 0 条**（symdiff=17 与 29→12 联立 common=12 ⇒ A1 的 12 条 MISS 全部是 A0 既有 MISS）；17 条中 **13 条 = 词面别名增益带入融合**、**4 条 = 融合重排翻进 top5**（细胞呼吸的易错辨析、溶度积、利用基本不等式求最值、电源的内阻）。

### 7.4 零漂移门（§2 口径：同路对称差必须 0）

- 同路词面 device↔JVM **symdiff=0**、同路融合 device↔JVM **symdiff=0** ⇒ 末行 **`verdict: lexical=PASS fused=PASS`**（`a1/zero-drift.out.txt:1-2,9`）；判读员现场复跑 `python build/agent-batch19/a1/zero_drift.py` exit=0（`a1-final.md` §一）。
- intel-only（预登记非门对象）：cross-route 15、device legs 15、device_fused↔A0 17、device_lexical↔A0 13、jvm_fused↔A0 17、jvm_lexical↔A0 13（`a1/zero-drift.out.txt:3-8`）。

### 7.5 显著性（§2 参数：按章 cluster 配对 bootstrap `--reps 20000 --seed 0`）

- 融合：**配对差 B−A = +0.131782945736434，95% CI（按章 cluster）[0.07692307692307693, 0.1925925925925926]，双侧 p = 9.9995e-05 ≈ 0.0001**（`a1/significance-fused-vs-A0.json` `paired.delta_recall_b_minus_a` / `ci95_chapter` / `p_two_sided` 字段实读；`a1/significance-fused-vs-A0.txt:29-30` 同值）。**方向**：落盘 B 路 = 批次19 A1 = 0.9070（117/129）、A 路 = A0 = 0.7752（100/129）（JSON `a.recall=0.7751937984496124` / `b.recall=0.9069767441860465` 实读；与 §2 预写「A=A1、B=A0」的字母标注相反，以落盘 JSON 实际方向为准——差值为正即 A1 更高）。
- 词面对照：B−A = **+0.1008**，95% CI（按章）[0.0448, 0.1606]，双侧 p = 0.0001（`a1/significance-lexical-vs-A0.txt` 末行）。

### 7.6 三采纳规则逐条（§2）

| # | 预注册规则 | 求值 | 依据 |
|---|---|---|---|
| 1 | 主集严格更高 | **成立**：117/129 > 100/129（+17；17 条全为单向修复、0 新增 MISS，见 7.3） | `a1/device-metrics.txt:3`；`a1/zero-drift.out.txt:5` |
| 2 | 24 章逐章不劣（任一章严格更低即不采纳） | **成立**：0 章严格更低（12 更高 / 12 相同） | `a1/significance-fused-vs-A0.json` per_chapter 对比（本代理实跑，7.2） |
| 3 | 负向守卫 CI 上界 ≥0 | **成立**：CI [0.0769, 0.1926]，上界 0.1926 ≥ 0（下界亦 >0） | `a1/significance-fused-vs-A0.json` `paired.ci95_chapter` |
| — | 零漂移一票否决（§2） | 未触发（同路 0/0，PASS/PASS，7.4） | `a1/zero-drift.out.txt` |

### 7.7 采纳执行与 sha 核验（执行轮实跑 + 既有产物实读）

- 干跑复核与 §1 冻结读数一致：**应用 33 = 既有 2 + 新增 31、冲突 0、拒绝 0、变更节点 21**（`a1-dryrun.out.txt`）。
- `rebuild_aliases --write`：节点 3,245（有别名 3,152）、`alias_map.csv` 37,551 数据行、别名总数 **40,765 → 40,796**、变更节点 21、丢弃候选 22,559 按八因登记（`a1-rebuild-write.out.txt`）。
- `promote`：**version → 20、内容戳 → `ebf477816c318704`**，28 个文件晋升、压平 0（`a1-promote-run.log`）；主包 sha `298f4c0c41519d51…`（`build/agent-batch19/dense-union-estimate/` 文书 §2 实读同值）。
- dense 全链重打 + 重钉：`.vec` sha256 **`0536d0d1cda99bd5614bc94d9176628d7ce999938db1c289b3b44600ae13b23a`**（**44,041 行** = 3,245 canonical + 40,796 alias；写前 `8e216ee1…`）、旁车 vectorCount 44,041 / packSha256 `298f4c0c`、`DenseRecallAssembly.kt:107` `VECTOR_ASSET_SHA256` 同指 `0536d0d1`——三方一致（`a1-final.md` §三、§五实测重算）。
- 采纳前置两处锚点重钉（`a1-pending-repins.md` 处置，复跑 exit=0）：`Stage1LexicalLabTest` main **0.5736434108527132**（74/129、hits 74、MRR 0.15943152454780354）；`Stage2LexicalScoresExportTest` hits **101** / ARM_A_MAIN 0.7829457364341085 / ARM_A_MRR 0.6246770025839794（`a1-final-stage{1,2}-run{1,2}.log`；断言未放宽，纯按实测重钉；金标 sha 未变仍 `eabef102…`）。
- **stateAfter：v20 + dense `0536d0d1…`（44,041 行）= 新基线**；后续臂的零漂移/A0 对照以本状态为参照（`a1-final.md` §三）。

### 7.8 边界与未竟

- §6 预期「到不了 0.90（需 117/129，即 +17）」：实测融合恰为 **117/129 = 0.9069767，已达成 0.90**——超出预注册预期，增量分解见 7.3（词面 +13 逐位兑现 + 融合重排 +4）。
- §6 预测「4 个 absent 中只能救回镁」：兑现——镁的化学性质经 A1 别名（`镁着火不能用二氧化碳灭火器`）由 absent 转为命中；余 3 条 absent（等和线、活结与死结模型、力的分解）仍未解，dense-union 前置实测（K=64）判活结与力的分解结构可救、等和线不可（`build/agent-batch19/dense-union-estimate/`，批次19 批报 §5）。
- **登记待裁定**：`ProductionLexicalLegExportTest` V1_D1_MRR 两源 ULP 级分歧（A1 记载锚 = Python④ `0.6206718346253229`；Kotlin 侧回读重算恒为 `0.6206718346253232`，差 ≈3×1e-16；设备词面 MRR 落盘读数即 …232，`a1/device-metrics.txt:35`）。A1 的 JVM 复核轮因他线在飞编译红从未跑成，该逐位复核由标题臂回滚轮在恢复态补做（`build/agent-batch19/title-final.md` §四）；A1 锚 …229 保留不动、不代改，两源口径待裁定。**该登记不影响本臂判读**（判分对象是设备主集，非该 JVM 锚测试）。
- 设备读数来自 emulator-5554（`OK (1 test)`），非真机；本节未重跑设备。

### 7.9 结果节证据路径

`build/agent-batch19/a1-final.md`（裁定与 stateAfter）；`a1/{device-metrics.txt,device-fused-misses.txt,device-lexical-misses.txt,jvm-fused-metrics.txt,jvm-metrics.txt,zero-drift.out.txt,zero-drift.json,significance-fused-vs-A0.{json,txt},significance-lexical-vs-A0.txt,step1-gate-poll2.log,step1_compile_and_assemble.log,judge-files-record.md}`；`build/agent-batch19/{a1-dryrun.out.txt,a1-rebuild-write.out.txt,a1-promote-run.log,a1-pending-repins.md,a1-final-stage1-run{1,2}.log,a1-final-stage2-run{1,2}.log,a1-touched-files.txt,backup/}`；A0 账本 `build/agent-batch15/d3/a0-device-{fused,lexical}-misses.txt`。
