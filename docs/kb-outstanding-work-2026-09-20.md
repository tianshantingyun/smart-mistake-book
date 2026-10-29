> 本文件已过时：知识库未竟清单以 docs/kb-outstanding-research-2026-10-02.md 为准（2026-10-02）。

> **现行数字（2026-10-20 复算）**：知识点 **3,245**（主题 425）/ 教学材料 **64,973** 条（sidecar v2-01..25）/ 课标覆盖 **390**/661（PARTIAL 73、NOT_COVERED 198、UNDECIDED 0）/ 薄料 live **205**（零材料 0）/ 超配（>4）**2,482** / 未判（机械 SKIP）**61,170**。复算入口：`moe-2025-four-subjects-v1.json`（现行 **v20**，批次19 A1 采纳 2026-10-25；2026-10-20 复算时为 v17、批次18 时 v19）、`moe-2025-teaching-support-v2-*.json`、`kb-coverage-alignment-2026-v2.json`、`PYTHONPATH=tools python -m kb_build.report_material_gaps`、`PYTHONPATH=tools python -m kb_coverage.count_unjudged`。下文 2026-09-20 快照与 2026-10-02 注记按日期保留。

> **更新（2026-10-26 批次22）**：现行基线已由 v20 升至 **v21**（contentVersion `9f9e99ceec47cf8c`、主包 `a7192484…`）+ dense **`79a22270…`（44,043 行 / 26,041,347 B）**；检索基线仍 **117/129 = 0.9069767**（非劣，非增益）；验收 §11 与判据状态刷新已落章。下方 §1–§8 的既有数字为历史时点，按日期保留；细则见 §9。

# 知识库未竟工作清单（2026-09-20 快照）

用户指令：未做的先以文档形式记下来；缺料的新增补源已记录（**先不读取**）；
旧资料未读完的部分停止读取、状态如实记档。本文档是恢复工作的入口。

---

## 1. 当前基线（2026-09-20 实测）

- 质量门 **22/22 全绿**（`python tools/build-knowledge-pack.py --gate`；22 项含并行会话新增的
  3 个材料文本指标：非法转义 / shell 展开残迹 / `$` 不成对，均已被 `ec5656d6` 修清零）。
- 规模：**3,580 知识点 / 396 主题 / ~2 万条材料（10 个 sidecar 卷）** / 406 条已声明真前置边。
  〔2026-10-20 现行：3,245 知识点 / 425 主题 / 64,973 条材料 / 25 个 sidecar。〕
- 提交链（本线）：`98919887`（文本残迹+坏名 31 条）→ `7c150f87`（分类报告）→ `cd7c7987`（定位串
  369）→ `021b3a07`（10 条改判）→ `2c4f6753`（章层 12→6）→ `dcaf932f`（删 1）→ `84cc6467`
  （定位串 1306→0）→ `20059b71`（主题去重）→ `058417c2`（结构收尾，章层挂点清零）→
  `cffda989`（前置 1246→406 + 边界 1254 重写）。
- 并行会话（内容入库线）最近：`ec5656d6`（材料损坏 2,265 处 `\1` 回指 + shell 展开全清）、
  `bd417874`（最后 1 个零材料点补料）、`704737c9`（892 条无主材料语义绑定，门首绿）、
  `c11f7875`（错绑第一刀 MATH 33 条）、`2a72cf31`（近重复节点合并）。

## 2. 未竟工作（按优先级）

### W-1 错绑嫌疑池——**已清零（2026-09-25 复核）**
- 账（2026-09-25 复核）：`tools/kb_build/tables/binding_suspects.csv` **0 行嫌疑**（只剩表头）；
  原 237 行（PHYSICS 108 / CHEMISTRY 75 / MATH 33 / BIOLOGY 21）里的 204 条未决**已逐条裁定**——
  `tools/kb_build/tables/binding_suspects_reviewed.csv` 共 204 行 = **KEEP 21 / REBIND 183**
  （执行链 `apply_rebind_verdicts.py` → `material_rebind.py` → `rebind_materials.py`）；
  MATH 33 条更早按"标题同名节点"改绑（`c11f7875`）。
- 复算：`PYTHONPATH=tools python -m kb_build.audit_bindings_by_alias` ⇒ 现输出
  `错绑嫌疑 0 条（已裁定豁免 21 条）`（豁免读 `binding_suspects_reviewed.csv`）。
- 规则：材料标题恰等于同学科另一节点主名且当前绑定不是它 → 嫌疑；**只出表不自动改**，
  逐条人判（上位概念收下位材料是合法的，别一刀切）。
- 验收：`binding_suspects.csv` 清零或每行有裁定记录；改绑走 `material_rebind.csv` +
  `rebind_materials`（无损：材料总数不变、无悬空绑定）；改后四科基准 Recall@5 不得降。

### W-2 薄料节点 1,232 个（补料主线；**2026-10-20 复算现值 205**；2026-10-02 为 571）
- 账：`knowledge-production/node-material-gaps-2026-09.csv`，全部 `thin`（仅 1 条材料），
  `zero` 已归零。每行带"缺什么 + 去哪里找"的 note。
  〔2026-10-02 复算：台账文件 661 行；live 缺口 **571** = 零材料 0 / 仅 1 条 571——
  `PYTHONPATH=tools python -m kb_build.report_material_gaps`。〕
  〔2026-10-20 复算：live 缺口 **205** = 零材料 0 / 仅 1 条 205（BIO 42 / CHEM 56 / MATH 53 / PHYS 54）；台账文件仍 661 行。〕
- 动作：按 note 指向的源补料（第 3 节新资料源优先）；重跑
  `kb_build.report_material_gaps --write` 对比 thin 递减。
- 验收："zero 恒 0、thin 单调递减"（对方定的口径）。

### W-3 块判定主线（旧 2026 资料树）——**已按用户裁定舍弃（2026-09-20）**
- 用户裁定："已经扫描出来内容的不要舍弃；真正需要舍弃的是还没扫描的；扫描过的旧资料直接删掉"，
  随后追加"未扫描的也删掉"——**旧资料树整树清空**。
- **已执行（两批，共 10,482 个文件 / 65.68 GB，源树现存文件 0）**：
  - 批 1：删除扫描过的 **9,679 个 / 54.80 GB**（EXTRACTED 2,295 + CHUNKED 6,185 +
    EMPTY 1,139 + 已转写扫描件 60）；空目录清理 2,636 个。
  - 批 2：删除未扫描的 **803 个 / 10.88 GB**（REJECTED 392 + SKIPPED_NO_CONTENT 411）。
  - 两批清单都在 `knowledge-production/source-deletion-log-2026-09.tsv`（rel/state/
    location/sha16/size/action，可逐条审计）；未扫描 452 个（含 60 已转写）状态机标
    **REJECTED**（ERROR 走合法两步跳转），SKIPPED_NO_CONTENT 行备注"源文件已删除"；
    `extraction_state --verify` 账本完整。
- **内容零丢失的依据**：扫描出的内容全部在仓内——判定入库的在 sidecar（材料 27,000+ 条），
  切块未判定的在 `tools/kb_coverage/tables/extracted_chunks.jsonl`（177,761 块 / 252MB，
  随版本控制）。CHUNKED 的 6,185 个文件按更早指令**不再读取**（冻结），但其块文本保留，
  将来捡回任何一块都不需要源文件。
- 状态机终态（10,482 文件）：EXTRACTED 2,295 / CHUNKED 6,185（冻结）/ EMPTY 1,139 /
  SKIPPED_NO_CONTENT 411 / **REJECTED 452**；源文件 0。
- 后续补料一律走第 3 节的 **2027 版《53知识清单》**（W-2 的 note 指向也改以它为准）。

### W-4 仪器化验证债
- 真机没跑过的：`BundledContentReconciliationInstrumentedTest`（**382 条退役台账**的
  重定向正确性：合并/删除节点在用户库里经 successors 链解析到新节点）、
  `KnowledgeContentUpdateDrillInstrumentedTest`、v45→46 迁移在真库上的表现。
- 环境注意（记忆已录）：只有 test_device/API 34；模拟器会自崩，崩后 `pm` 会坏，
  冷启 `-wipe-data` 才修；`:core:database` 仪器套件资源敏感，判归属看失败类是否漂移。

### W-5 安全审计（纪律：不得声称安全）
- Mimosa 多轮 deep 扫描均 `inconclusive`（托管 Semgrep 不可用，KD-14 按宿主环境边界结案）。
- 下次做：先 `mimosa doctor` 装好托管 Semgrep，再 deep，拿到完整密封结论才算数。

### W-6 登记在册的结构残留（不红、不堵，点名备查）
- 刻意保留的同名主题对 2 个（`3 电磁感应现象及应用` 教材位置+综合桶；
  `细胞的多样性和统一性` 教材位置+全能性待归位点）——`test_kb_merge_topics` 钉死；
- 跨册综合桶（`综合复习`/`力学综合` 等）是设计上的跨切面位置；
- 选必3「生物技术与工程」子树已清，但生物主题树整体还没做过"按 2019 目录重排"；
- 2 个 0 点主题定位串仍是"待补章表"（无节点可推众数，等补料后自动补）。

## 3. 缺料新增补源（**已记录，未开始读取**——用户指令）

- 路径：`C:\Users\听云\Desktop\最全知识库原始资料`
- 内容（目录名，未读取任何文件内容）：
  - `2027版高中《53知识清单》彩色版（数学）`
  - `2027版高中《53知识清单》彩色版（物理）`
  - `2027版高中《53知识清单》彩色版（化学）`
  - `2027版高中《53知识清单》彩色版（生物）`
- 用途：W-2 薄料 1,232 个（2026-09-20 口径；2026-10-02 复算 571；**2026-10-20 复算 205**）与 W-3 判定池的**补充来源**（2027 版，比池内 2026 资料新一年）。
- 开始读取前必须做的事（按本仓库既有纪律）：
  1. 来源登记：`audit-knowledge-source-register` 流程走一遍（版本/授权/使用政策；
     教辅内容只能"提取结构化结论与知识目录节点，不保存原文段落"——边界与材料撰写
     的合规判据就是按这条定的）；
  2. 版次核对：2027 版《53》的章节编排 vs 2019 人教版教材目录（章表是 2019 口径，
     若 2027 版重排过章节，先对齐再入库，否则重演"定位串与章表不一致"）;
  3. 进入 W-3 的状态机（extraction_state + 判定表 + 隔离机制），不绕开。

## 4. 旧资料阅读状态（按指令停读，如实冻结）

| 批次 | 状态 |
|---|---|
| 五三 B版专题 / 物理讲册 / 二轮讲义 / 解题觉醒主书（3,609 条材料，30 代理） | **读完**，已入库（K 节） |
| 解题觉醒大招册四科（951 条，10 代理） | **读完**，已入库（J 节） |
| 化学/生物「一轮复习讲义·学生版 + 知识清单·学生版」（9,405 块，31 代理） | **读完**，已入库（O 节，428+729 点） |
| 教师版 / 课件（同内容幻灯片） | **明确不读**（=学生版+答案解析，判了等于重复入库，O 节裁定） |
| 其余 2026 资料树（~15 万块，6,600+ 文件） | **未读完，停止读取**——状态在 W-3 的状态机里，随时可恢复 |
| 扫描件 60 文件（2.4GB） | 已转写入库（J/K 轮），源文件 09-20 批 1 已删（`scan-isolation-log` 存证） |

## 5. 恢复顺序建议

W-1（错绑，池小、影响检索精度）→ W-2（薄料，用第 3 节新源，先走来源登记）→
W-4（攒一批真机跑一次）→ W-5（独立）。W-3 旧块池已归档舍弃，不再排期；
唯一例外是 `extracted_chunks.jsonl` 里冻结的 17.7 万块——若 W-2 补料时发现
某缺口只有旧块池里有干净文本，可按块捡回（不需要源文件）。

## 6. 批次19 补记（2026-10-24/25 执行，2026-10-25 登记）

> 数字来源：`build/agent-batch19/` 各执行员落盘产物实读 + 收口轮脚本实跑；批报 `docs/kb-batch19-2026-10-23.md`、验收补记 `docs/kb-acceptance-2026-10-17.md` §9。基线演进：A0（v19 时期，融合 100/129）→ **A1 采纳（v20，117/129 = 0.9069767）**。

| 项 | 状态 | 读数与证据 |
|---|---|---|
| **批次15 A2 证据** | **done（补录）** | `:core:data:testDebugUnitTest --rerun` 全套 exit=0、**1033/1033**（0 失败 0 错误 0 跳过、132 XML 聚合）+ `GoldenRetrievalJvmTest` 单独 2/2；JVM 判官指纹 sha `62c232fc…`（他人 ` M` 态只记录未触碰）。`build/agent-batch19/{a2-evidence-summary.md,a2-evidence.log,a2-goldenretrieval.log}` |
| **第三杠杆（材料标题向量入 dense）执行结果** | **已执行 → 不判读 → 逐字回滚（未采纳、非新基线）** | 离线链重出 104,959 行资产（+60,918 title 行；38,377 行与既有表面等值、净新表面 22,541）后，装置同路融合零漂移 **symdiff=3 FAIL**（exit=2）——根因 = 装置两侧稠密资产代际错配（APK 内嵌 `0536d0d1…` 23:16 代 vs 工作树 `9d9bf3f5…` 02:46 重导），按预注册「非 0 = 装置缺陷，该臂不判读」；三规则未求值。回滚 21/21 文件 sha 核验、`check_asset.py` 10/10 OK、stateAfter=v20（与 A1 采纳态逐位同）。JVM intel 读数 **118/129**（`title/jvm-fused-metrics.txt`）留档不判读。证据：`build/agent-batch19/{title-final.md,title-touched-files.txt,backup/*.pre-title,title-sha/}`、`title/{zero-drift.out.txt,judge-files-record.md}`；预注册 §7 已填（`docs/research/2026-10-23-title-vector-arm-prereg.md`） |
| **`dense_asset.py` / `model-manifest.json` 遗留处置** | **仍为批次13 遗留在飞修改态，两臂均未触碰，待该线收口** | `dense_asset.py` sha `2dafbfa994a3c664…` 与预注册 §4 冻结值一致（零触碰）；`model-manifest.json` 在标题臂曾获链驱动增量、已随回滚**逐字回 PRE `7f1f6e8d…`**，npy 回 A1 代（`3fecbfcf…`/`8d4a1eed…`）；同态在飞的 `export_bge_int8.py`、`gen_device_parity_fixture.py` 原样保留。处置口径不变：「他臂所有、不代改不回滚」 |
| **他线判官修改态登记** | **记录在案，臂间可比性由零漂移门保证** | JVM `GoldenRetrievalJvmTest.kt` sha `62c232fcbf3ffd11…` git ` M`（3+/3−：融合镜像落点 batch13→batch14，判分逻辑零改动；A1/标题两轮出数同状态）；设备 `GoldenRetrievalInstrumentedTest.kt` sha `c2280255a9b0db2b…` 干净。A1 轮零漂移 PASS/PASS；标题轮 FAIL 已定位为资产代际差、非判官差（`a1/judge-files-record.md`、`title/judge-files-record.md`） |
| **dense-union 跨线状态** | **预注册冻结 → 前置实测 go → 待跨线施工（core/database 并集臂）** | 预注册 `docs/research/2026-10-22-dense-union-prereg.md`（§5 结果节仍待臂）；前置实测（估计非臂）：K=64 口径下 4 条 absent 的 dense 全科名次 = 镁 1 / 活结与死结模型 2 / 力的分解 7 / 等和线 336——**3/4 结构可召回**；并集（词面 512 ∪ dense 64）对 A0 29 条 MISS 结构覆盖率 **28/29**；以 A1（12 MISS）为基线单独并集主集上限约 **119/129**；等和线 1 条转内容侧（已知边界）。结论 **go——值得推进跨线施工**，需他会话协调 core/database 窗口、独立成臂、按预注册全门判读。证据：`docs/research/2026-10-23-kb-dense-union-estimate.md`、`build/agent-batch19/dense-union-estimate/`。批次18 lever-1 同思路已实测并回滚（无采纳态残留，`docs/kb-batch18-2026-10-23.md` §3） |
| **登记待裁定（新增）** | `ProductionLexicalLegExportTest` V1_D1_MRR 两源 ULP 级分歧 | A1 Python④ 锚 `0.6206718346253229` vs Kotlin 回读重算恒为 `0.6206718346253232`（差 ≈3×1e-16；设备词面 MRR 落盘读数即 …232）——由标题臂回滚轮在恢复态补做逐位复核后登记；A1 锚保留不动、不代改不断言放宽，两源口径裁定后复跑转绿（`build/agent-batch19/title-final.md` §四） |

## 7. 批次20 终态行（2026-10-25 终验落章：**封装完毕，转入维护态**）

> 数字来源：`build/agent-batch20/` 各执行员落盘产物实读 + 结案文书轮实跑（覆盖计数复算、verify 脚本、包身份、锚单测 XML）。批报 `docs/kb-batch20-2026-10-25.md`、验收落章 `docs/kb-acceptance-2026-10-17.md` §10、判据 `docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md`。终验基线：HEAD `558185c9` + 包 v20（`ebf477816c318704`）+ dense `0536d0d1…`（44,041 行）+ 检索基线 117/129 = 0.9069767 + 主线覆盖终态 400/426 = 0.9390。

| 项 | 终态 | 读数与证据 |
|---|---|---|
| **ULP 锚（§6 末行登记待裁定）** | **closed（fixed-green）** | 裁定采纳 Kotlin/设备两源一致口径 `0.6206718346253232`（…229 为 Python④ stage3 外源单源），`V1_D1_MRR` 重钉 + 裁定注释落码（`ProductionLexicalLegExportTest.kt:365-366`，备份 `build/agent-batch20/backup/ProductionLexicalLegExportTest.kt.pre-ulp-2026-10-25`）；单测 ProductionLexicalLegExportTest 1/1 + DenseAssetProvenanceTest 2/2 全绿 0 skip（test-results XML）；八门 rc 全 0 |
| **主线 43 条 PARTIAL 终审** | **done（applied）** | 43 行裁定 UPGRADE 26 / KEEP 16 / WAIVE 1（`build/agent-batch20/partial-review/decisions.json`，sidecar-content-evidence 逐行取证）；台账 `finalReview` 43 行落账、CSV 投影 27 行、豁免 9+1；**主线 426 分母终态 COVERED 400 / PARTIAL 16 / NOT_COVERED 10 / UNDECIDED 0（覆盖 0.9390）**，三口径+四分科独立重算全 MATCH；`final_review_verify_r20.py` 7 项断言 exit=0（结案复跑）。逐行表：`knowledge-production/coverage-final-review-2026-10-25.md` |
| **标题向量臂（第三杠杆）** | **settled——clean 重测终局不采纳（rolled-back）** | 批次20 同代装置复评（预注册 §4 复评条款兑现）：118/129 = 0.9147287、同路零漂移 PASS/PASS（批19 的装置代际错配已消解）、显著性 +0.0078 CI [−0.0208, 0.0441] p=0.9247；预注册 §2 规则②否决（数学选必二第四章·数列 10/11→9/11 严格更低；翻转 = 修 2 新 1），裁定 adopted=false、逐字回滚 21/21 sha 核验；stateAfter = v20 + `0536d0d1…`（HEAD `558185c9`）。预注册 §7.11 终局已填（`docs/research/2026-10-23-title-vector-arm-prereg.md`）；裁定记录 `build/agent-batch20/title-final-r20.md`。该臂**收束、不再排期**；dense-union 并集臂（预注册冻结、前置实测 go）仍待跨线施工，是检索增强的剩余通道 |
| **等和线（B4 已知边界）** | **边界维持 + 维护项** | 检索词汇缺口登记不变：内容不缺（23 别名 / 32 块材料挂对节点），dense 全科名次 336、并集结构救不了（单独并集上限约 119/129 的唯一不可救条）；快照轮 fused MISS 12 条零新增（`build/agent-batch20/drill/device-fused-misses.txt`）。词汇缺口补料走内容侧预案（维护态执行，评测禁止面向结果定向） |
| **薄料 3 条（终审 thinAssessment）** | **2 升 1 维持；维持项转维护** | 行 07（导数定义在极限中的计算）、行 26（电磁说-麦克斯韦）终审 UPGRADE（命中块可作答，无需源料评估）；行 23（必备实验仪器的原理及使用）KEEP——「电路元器件识别及其作用」半边三处料源检索 0 命中，已有 `phy-basic-instruments-principle-use` 可补「会使用多用电表」半边；**只评估未写入**（终审零写包），补料列维护项（§3 的 2027 版《53》源登记流程不变） |
| **验收宣告** | **封装完毕（最终验收标准 v1.0 达成，2026-10-25）** | 五组判据 A 3/3 · B 4/4 · C 2/2 · D 3/3 全部「实测达标 + 证据路径可复算」，E 组（E1–E4）逐条登记终态；快照=done（`build/agent-batch20/acceptance-snapshot.md`）；宣告落章 `docs/kb-acceptance-2026-10-17.md` §10.7。**维护态约束**：包零写（新增材料须走自产料池门 + 晋升，会使已测检索基线失效）；E2 用户复核开放中（182/114/402 预裁，复核走后续维护版本）；剩余通道 = dense-union 并集臂 + 系统性别名扩充（均验收后增强臂） |

## 8. 批次21 维护态行（2026-10-26：dense-union 并集臂 parked-gate-red）

> 数字来源：`build/agent-batch21/` 各执行员落盘产物实读 + 本文书轮复算；批报 `docs/kb-batch21-2026-10-26.md`、预注册 §7（`docs/research/2026-10-22-dense-union-prereg.md`）。**基线不变**：HEAD `ce01508f` + 包 v20（`ebf477816c318704`）+ dense `0536d0d1…`（44,041 行）+ 检索基线 A1 **117/129 = 0.9069767**。

| 项 | 维护态 | 读数与证据 |
|---|---|---|
| **dense-union 并集臂（批次21）** | **parked-gate-red——未采纳、未回滚，实现留在工作树** | 设备融合 **117/129 = 0.9069767441860465**、MRR **0.7322997416020672**，与 A1 **逐位相同**；词面 102/129、MRR 0.6206718346253232；p50=45 / p95=59 / max=87ms、overBudget=0、`denseLegLive=true`（`build/agent-batch21/union/device-metrics.txt:2-5,32-35`）。24 章逐章与 A1 逐行相同；fused MISS 12 条 query 同集（`diff` rc=0）。并集**确实执行**：logcat 658 行 `denseOnlyAppended=64`（例 `d3/device-logcat.txt:4229`）。`活结与死结模型`/`力的分解` 从 `absent` → 探针名次 **17/28**（仍未进 top-5）；`等和线` 仍 `absent`；`同分异构…` 12→20、`共点力的动态平衡` 51→53（仍 MISS）。**B2 同路零漂移 `NOT_COMPUTABLE`**（JVM 镜像红：他线 `EloCorrectnessPredictor.kt:288-289` `roundTo` 编译阻断 + `RetrievalBenchmark.fusedRecall` 对 dense-only id 抛 `NoSuchElementException`，全量 `:core:data` 1109/1 红）；显著性 `NOT_RUN`；三规则未正式判读（规则① 117=117 不成立）。实现 8 个 tracked 文件 `M` + `DenseUnionRecallTest.kt` `??`，**未回滚**；下一步：修镜像/编译阻断 → 复跑 B2+显著性+三规则；若仍 117/129，按预注册 §6.1.3-8 逐字回滚，**不得当新基线**。证据：`build/agent-batch21/{acceptance-snapshot.md,union/,d3/,tests/}`；预注册 §7 |
| **B2/JVM 融合镜像** | **阻塞登记（属 KB 装置面，不是判官）** | `RetrievalBenchmark.kt:482` `byId.getValue(...)` 只映射词面召回集，并集新增 id 必抛 `NoSuchElementException`；本臂判官/镜像零改（设备判官 `c2280255…`、JVM 判官 `62c232fc…`、镜像 `8c2a2ef0…`，均与 A1 记录同值）。修复镜像需单独裁定并重新预注册测量路径，不能顺手改判官（`build/agent-batch21/union/zero-drift.json:9`；`feasibility.md:132-141`） |
| **薄料 3 条（终审 thinAssessment）→ 专版维护项** | **2 升 1 维持；补料会使基线失效** | 行 07（导数定义在极限中的计算）、行 26（电磁说-麦克斯韦）已 UPGRADE（命中块可作答，无需源料评估）；行 23（必备实验仪器的原理及使用）KEEP——「电路元器件识别及其作用」半边三处料源检索 0 命中，已有 `phy-basic-instruments-principle-use` 可补「会使用多用电表」半边。**只评估未写入**。**补料会使已测检索基线（117/129）失效**，故列**专版维护项**：维护态零写包，补料须走自产料池门 + 晋升（2027 版《53》源登记流程不变）。证据：`docs/kb-batch20-2026-10-25.md:100,174`；`docs/kb-acceptance-2026-10-17.md` §10.5 |
| **等和线（B4 已知边界）** | **边界维持 + 维护项** | 内容不缺（23 别名 / 32 块材料挂对节点），dense 全科名次 **336**；本臂并集**未救回**（fused MISS 仍 `absent`）；词汇缺口补料走内容侧预案，维护态执行，**评测禁止面向结果定向**（不加题干式别名）。证据：`build/agent-batch21/union/device-fused-misses.txt:8`；`docs/kb-batch20-2026-10-25.md:145` |
| **182/114/402 待复核（E2）** | **用户复核开放中，走后续维护版本** | 182 拆分提案（50 REJECT / 132 DEFER）、114 重开提案、402 同键分类 = AI 预裁完成；**用户复核未完成**，本批未处理。证据：`docs/kb-batch20-2026-10-25.md:175`；`docs/kb-acceptance-2026-10-17.md` §10.5 |
| **验收状态（本臂未采纳）** | **B1 仍为 A1 117/129；验收文书未改** | 本臂不构成新 B1 验收（B2 未过）；`docs/kb-acceptance-2026-10-17.md` §11 未追加、`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md` 状态列未刷新（按 ask 第 3 条“若采纳”条件不成立）。批次20 验收宣告（封装完毕）与 E 组登记维持不变 |

**批次21 终裁补记（主循环，2026-10-26）**：`dense-union 并集臂` 已于主循环逐字回滚（8 tracked 文件 checkout + `DenseUnionRecallTest.kt` 删除，porcelain 空）并复跑绿（JVM 判官 + DenseRecall* **BUILD SUCCESSFUL 1m24s**）；终裁 **不采纳、结案**（设备与 A1 逐位相同 + 并入执行确证 + 批18 同口径二次复现；JVM 镜像不兼容超集为不可判读装置原因）。重开条件见预注册 §7.10；基线维持 **A1 117/129**。

**批次22 追记（2026-10-26）**：§8 末行所述「验收文书未改 / §11 未追加 / 判据状态未刷新」已被批次22 取代——验收 §11 已追加、判据状态已刷新；批次21 dense-union 仍「不采纳、已回滚」；当前基线 = **v21**（见 §9）。

## 9. 批次22 v21 维护态行（2026-10-26：内容更新 adopted，基线 v21）

> 数字来源：`build/agent-batch22/` 产物实读 + 批报 `docs/kb-batch22-2026-10-26.md` + 验收 §11 + 判据刷新节。基线：HEAD `062e18f0` + 包 **v21**（contentVersion `9f9e99ceec47cf8c`，主包 `a7192484…`）+ dense **`79a22270…`（44,043 行）** + 检索基线 **117/129 = 0.9069767**（与 A1 持平，非增益）。v20 旧数字见 §7/§8，按日期保留。

| 项 | 维护态 | 读数与证据 |
|---|---|---|
| **v21 内容更新（批次22）** | **adopted=true——新基线 v21；未回滚** | 2 条正文派生别名写入 `MATH/等和线`：`其和为 k 的等和线`、`k 的取值影响等和线的位置`（S2_STRUCTURAL，材料 `ext-mat-91e0c2ace8-058b` 正文加粗 span）。`alias_exceptions` 35 / `alias_map` 37,553 / `alias_dropped` 22,559 数据行；别名 40,796→40,798；promote **v21**（contentVersion `9f9e99ceec47cf8c`、主包 `a7192484…`）；dense **44,043 行 / 26,041,347 B / `79a22270…`**（旁车 `68ed524a…`）。设备融合 **117/129 = 0.9069767441860465**、MRR **0.7322997416020672**、p50=40 / p95=57 / max=164、overBudget=0；词面 102/129、MRR 0.6206718346253232；24/24 逐章全等；同路零漂移 **PASS/PASS**（lexical 27/27、fused 12/12）；显著性（按章 cluster 配对 bootstrap，20k/seed0，A=v21、B=A1）delta **0.0**、95% CI **[0.0,0.0]**、p=1.0；**无分数增益**。证据：批报 §4–§6；`build/agent-batch22/consistency.txt`；`build/agent-batch22/v21/device-metrics.txt:2-5,32-35`；`build/agent-batch22/v21/zero-drift.json`；`build/agent-batch22/v21/significance-*-v21-vs-A1.json` |
| **等和线（别名结果 + 单点状态）** | **别名 23→25；单点仍 absent，只报不判** | 新增 2 条 S2_STRUCTURAL 正文派生别名（非金标题反推）；节点别名 **23→25**（含主名）、32 块材料不变。单点四路 `rank=absent`：`build/agent-batch22/v21/device-fused-misses.txt:8`、`build/agent-batch22/v21/device-lexical-misses.txt:13`、`build/agent-batch22/v21/jvm-fused-misses.txt:8`、`build/agent-batch22/v21/jvm-lexical-misses.txt:13`；当前口径无已知翻转入径；**不得因单点结果改别名/阈值/参数**。剩余 4 条候选（`其和为 1 的等和线（三点共线的充要条件）`、`&#124;k&#124; 的几何意义`、`等和线过 O 点时 k=0`、`直线 AB 及与它平行的直线称为等和线`）**不写入、不测量、不判读**。证据：预注册 §1.1/§2.4；批报 §3/§5.2/§8.1 |
| **薄料（补/登记）** | **blocked——本批零写；专版维护项** | 薄料 3 条终审结论维持：行 07（导数定义在极限中的计算）、行 26（电磁说-麦克斯韦）已 UPGRADE；行 23（必备实验仪器的原理及使用）KEEP——仍有 1 条 PRIMARY 材料 `phy-basic-instruments-principle-use`（不是错挂），缺的「电路元器件识别」半边仍在 `电路中的基本概念`。全包 **0 待执行 REBIND / 0 未绑 / 全 PRIMARY / 无 SUPPORTING 现行通道**。补料会使已测 117/129 基线失效，须走自产料池门 + 晋升（2027 版《53》源登记流程不变）。证据：`build/agent-batch22/material-touched-files.txt:1-15`；预注册 §1.2；批报 §8.2 |
| **使用面（交接项）** | **R1–R6 风险登记；本批未修复** | R1 P1 材料摘要非每节点一条（53.3% 节点无材料段；6.1% 误区材料占 76.1% 槽位）；R2 P1 材料标题/正文缺失（只 ≤60 字摘要）；R3 P1 `take(60)` 静默截断（27.2% 被切、640 条 LaTeX 损伤）；R4 P2 boundary `take(80)`（82.3% 被切、58.7% 范围短语在 80 字后）；R5 P2 2k 预算名义化（最大 2,079 B > 2,000 B）；R6 P3 13 条 summary / 6 条 boundary 含换行。归属：R1/R6 本线可改；R2/R5 智能体面契约侧；R3/R4 两侧。交接下一步：契约侧决定正文/标题披露与 2k 单位/执行点；本线侧改每节点材料取数、摘要 ≤60 字公式安全、boundary 范围前置、换行归一。本批未改 `RoomTutorToolRunner`、未改契约。证据：`docs/research/2026-10-26-kb-usage-surface-audit.md:13-40,46-233,333-339`；批报 §7 |
| **验收状态（v21）** | **验收 §11 已追加；判据状态已刷新；v1.0 达成宣告维持（不撤销）** | B1 **117/129 = 0.9069767441860465 ≥ 0.90**；B2 PASS/PASS；B3 p50=40 / p95=57 / max=164、overBudget=0；B4 单点 absent 登记；A/C/D/E 状态见验收 §11 与判据刷新节。v21 为内容更新后新基线，**不构成涨分宣告**。dense-union 批次21 仍不采纳、已回滚；标题向量臂已收束；E2 用户复核开放。C1 全套未重跑（仅定向 JVM 4 类 + 判官），沿用批次19/20 存档 1033/1033，不写成“v21 全套绿”。证据：`docs/kb-acceptance-2026-10-17.md` §11；`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md` 刷新节；批报 §6/§8 |

## 10. 批次23 R1 使用面修复行（2026-10-26：F1–F5 达标，交付 PARTIAL）

> 数字来源：`build/agent-batch23/` 产物实读 + 批报 `docs/kb-batch23-2026-10-26.md` + 验收 §12 + 判据 §F + 预注册 `docs/research/2026-10-26-kb-usage-r1-prereg.md` + `build/agent-batch23/usage-r1/retest-r1.log`。本行**取代 §9「使用面（交接项）」中 R1 的“未修复”状态**；R2–R6 仍按 §9/本节交接。基线不变：包 **v21**（contentVersion `9f9e99ceec47cf8c`，主包 `a7192484…`）+ dense **`79a22270…`** + 检索 **117/129**；本批零内容/零资产写入。

### 10.1 R1 修复结果（F1–F5 新数字）

| # | 判据 | 基线（旧语义，同装置重算） | 批次23 实测 | 判定 | 证据 |
|---|---|---:|---:|---|---|
| F1 | 每节点材料覆盖 ≥90% | 46.67%（301/645） | **100.0%（645/645）** | **PASS** | `retest-r1.log:4,6` |
| F2 | 期望节点有材料 ≥90% | 50.39%（65/129） | **90.7%（117/129）**；= 召回命中上限（12 条 MISS 结构上不可能有材料；117/117 命中查询全覆盖；余量 0.70pp） | **PASS**（贴上限） | `retest-r1.log:4,8` |
| F3 | 误区槽位 ≤20% | 76.12%（491/645） | **1.24%（8/645）**；`CONCEPT_EXPLANATION 563 / METHOD_MODEL 74 / MISCONCEPTION_GUIDE 8` | **PASS** | `retest-r1.log:4,10-11` |
| F4 | 无材料节点显式标注 | 无标注（静默省略） | **金标 0/0（空真）**；单测夹具证明 `｜（该节点无材料）` 真实渲染 | **PASS（依赖夹具）** | 审计 `:78`；`retest-r1.log:13`；`UsageMaterialAllocationTest.kt:181-233`；`KnowledgeReadRecallBudgetTest.kt:195-250` |
| F5 | 预算与形态不变 | 5/查询 / 名义 2k | **5 条/查询（129/129）**；chars min/median/max **660 / 743 / 838**（max **41.9%** of 2,000 chars）；UTF-8 max **2,267 B**（**113.3%** of 2,000 B，R5 契约侧）；`summary.take(60)`、boundary `take(80)` 形态不变 | **PASS（字符口径）**；字节越界属 R5 未关 | 审计 `:29,37`；`retest-r1.log:14-18` |

**复算**：`python "C:/Users/听云/AppData/Local/Temp/retest_r1_batch23.py"`（复算命令；本轮只读引用 `retest-r1.log`，未重跑该脚本）。关键读数 = `F1 100.0`、`F2 117/129 90.7`、`F3 1.24`、`F4 0/0/0`、`F5 5/查询 / max 838 chars / UTF-8 2267 B`、`mismatch 0`；完整输出见批报 §4.3 / 验收 §12.5。

**结果要点**：

- **R1 实现**：旧 `KNOWLEDGE_READ` 使用“跨节点合计 `LIMIT=nodes.size` + 补救序”，导致 53.3% 节点静默无材料；本批新增专用 DAO/port/store/db 方法，按**节点各自取 1 条**、教学优先序（`CONCEPT_EXPLANATION → … → COMPLETE_SOLUTION`），runner 只切 `KNOWLEDGE_READ`；旧 SQL / 旧 port / 旧 store / 旧补救消费方 **零改**（批报 §2/§4.2）。
- **无回归**：设备融合 **117/129 = 0.9069767441860465**、MRR **0.7322997416020672**；词面 **102/129 = 0.7906976744186046**；同路零漂移 lexical 27/27、fused 12/12、symdiff 0；JVM 同；八门 8/8 rc=0；consistency 6/6 PASS；`py_suite` 808 tests OK（`build/agent-batch23/device/device-metrics.txt:2-5,32-35`；`device/zero-drift.json`；`gates/summary.txt`；`consistency.txt:136-143`）。
- **交付 PARTIAL，不称全绿**：R1 代码 **7 tracked ` M` + 1 个新测试 `??` 未提交**；预注册计划中的**真 Room 仪器化测试未创建**（`KnowledgeTeachingMaterialPerNodeQueryInstrumentedTest` 不存在）；**独立红测轮日志缺失**；F1–F5 是**离线镜像 + SQLite 逐字 SQL**，不是生产遥测；设备为 emulator，真机/API 23 未跑（批报 §9；验收 §12.4）。

### 10.2 R2–R6 交接 / 维护

| ID | 内容 | 归属 | 批次23 状态 |
|---|---|---|---|
| R2 | 材料标题/正文完全缺失，只给 ≤60 字摘要 | **契约侧交接** | 不在本批；另立契约侧任务（审计 `:83-112`，归属 `:98-100`） |
| R3 | `take(60)` 静默截断（27.2% 被切、640 条 LaTeX 损伤） | **契约侧交接**；摘要生成侧 ≤60 字/公式安全为配合项 | 不在本批（审计 `:113-147`，归属 `:133-135`） |
| R4 | boundary `take(80)`（82.3% 被切、58.7% 范围短语看不到） | **契约侧交接**；boundary 改写为配合项 | 不在本批（审计 `:148-179`，归属 `:165-167`） |
| R5 | 2k 名义值；`KNOWLEDGE_READ` 无运行时检查点；UTF-8 max 2,267 B > 2,000 B | **契约侧交接**（单位/执行点/口径） | 不在本批；F5 仍按字符判（审计 `:180-208`，归属 `:194-196`） |
| R6 | 13 条 summary / 6 条 boundary 含真实换行（LaTeX `\ne`、PHYSICS 结构换行） | **数据级维护项**（sidecar JSON 转义/换行归一） | 走维护版本；不影响 F1–F5（审计 `:209-240`，归属 `:221`） |

**归属边界**：R1 实现完成（未提交：每节点一条 + 教学序 + 无材料显式标记 + 渲染形态）；**R2–R5 属契约侧交接，R6 属数据级维护项**；不得把 F1–F5 达标外推为 R2–R6 已关闭（验收 §12.3）。

### 10.3 观测缺口

- KB 主路径共 **19 处 `Log.*` 调用**，全部是可读文本，无结构化字段/采样/聚合；**0 个指标/遥测 SDK**；KF-29 只覆盖“题→知识点绑定正确性”，且唯一聚合消费面是 debug 复核 Activity，release 无出口（`build/agent-batch23/obs-audit.md:13-16,85-97`）。
- **G1 材料覆盖率**（全库/跨设备需指标面）、**G2 空材料率**、**G3 检索延迟分布**、**G4 零命中率（分入口）**、**G5 版本分布**、**G6 索引健康/检索质量**：现状与归属见 `obs-audit.md:126-200`。
- **R1 修复本身未新增任何日志/指标**；F1–F5 是离线镜像读数，生产侧仍无法计数“材料覆盖率/空材料率”。这是 v1.1 交付 PARTIAL 的观测面缺口，不是 F1–F5 数值不达标（批报 §8；验收 §12.4）。

### 10.4 Runbook 落点

- 设备七段：`build/agent-batch23/device/step0_samegen_check.sh`（工作树/本地 APK/已装 APK 同代）→ `step1_coldstart_emulator.sh`（`emulator-5584` / `kb_test_device` / `-wipe-data`）→ `step2_gate_and_build.sh`（编译门 + assemble）→ `step3_install_run_device.sh`（install + 同代复核 + `am instrument`）→ `step4_pull_ledgers.sh`（MISS 账本 stamp 校验）→ `step5_run_jvm.sh`（JVM 判官）→ `step6_zero_drift.sh`（同路零漂移）。
- 门与快照：`build/agent-batch23/scripts/run_gates_b23.sh`（八门，任一门 rc≠0 退出 1）；`build/agent-batch23/scripts/consistency_check.py`（六项一致性，会重写 `consistency.txt`）。
- 停止条件与回滚程序见批报 §6.1/§6.4；本批是代码轨，`docs/ops/kb-release-runbook.md` 的内容发布链（别名/材料/promote/dense 重打）**不适用**（零内容/零资产写入）。

### 10.5 演练读数

| 步骤 | 读数 | 证据 |
|---|---|---|
| 干净安装 + 冷启（app `com.tingyun.smartmistakebook.localfirst`，APK `2c3ceb76…`） | `install exit=0`；冷启 `TotalTime=4201ms`；`install finished in 186574ms (full path: parse + reconcile)`；全量解析 26,875ms | `build/agent-batch23/prod-drill/step1_clean_install_launch.log` |
| 首查（`GoldenRetrievalInstrumentedTest`） | **`OK (1 test)`，`Time: 220.766`**；融合 **117/129**、MRR **0.7322997416020672**；p50 43 / p95 69 / max 293ms，**`overBudgetSamples=1`**；词面 102/129 | `prod-drill/step2_first_query.log` |
| 二次冷启（force-stop 后） | 冷启 `TotalTime=2949ms`；`install finished in 446 ms (fast path: content stamp matches, no parse, no reconcile)` | `prod-drill/step3_second_launch_fastpath.log` |
| 安装器 JVM 测试 | **`gradle exit=0`；`BUILD SUCCESSFUL in 35s`** | `prod-drill/step4_installer_jvm_tests.log` |

**边界**：演练是 emulator、非真实学生数据；首查 `overBudgetSamples=1` 与主设备跑 0（max 170ms）不同，属模拟器波动，不据此改预算口径，也不归因 R1（R1 不动召回）。`uninstall` 的 `DELETE_FAILED_INTERNAL_ERROR` 不影响 `install exit=0` / `pre_installed=no`（`step1_clean_install_launch.log:15-18`）。

### 10.6 182/114/402 待复核

- **仍待复核，本批未处理**：182 拆分提案（50 REJECT / 132 DEFER）、114 重开提案、402 同键分类 = AI 预裁完成；用户复核未完成，走后续维护版本。证据：`docs/kb-batch20-2026-10-25.md:175`；`docs/kb-acceptance-2026-10-17.md` §10.5；本节 §8/§9 同项。
- **不因 R1 修复改变**：F1–F5 是使用面材料分配验收，与 182/114/402 的内容复核队列无交集；不得把 F1–F5 达标当作 E2 复核完成。

### 10.7 验收状态

- **v1.1 判据已落章**：判据 §F（`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md`）与验收 §12（`docs/kb-acceptance-2026-10-17.md`）已追加；F1–F5 按上表判定，**v1.0 的 A–E / B1–B4 保持原判**（B1 117/129、MRR 0.7322997、词面 102/129、零漂移 symdiff 0），**不主张涨分**。
- **交付状态 PARTIAL**：判据达标，但 R1 代码未提交、真 Room 测试未建、红测日志缺失、F1–F5 为离线镜像、真机/API 23 未跑、生产遥测缺失；**不称 v1.1 全绿**。关闭条件见验收 §12.4（提交 R1、补真 Room 测试或登记不覆盖、复算脚本入仓、真机/API 23 冒烟、回写预注册 §7）。

## 11. 批次24 R1 证据闭环行（2026-10-27：02/06 关闭，03/15/16 保持 open）

> 数字来源：`docs/kb-batch24-2026-10-26.md`（下称“批24 报”）、`build/agent-batch24/` 产物实读、预注册 §7（`docs/research/2026-10-26-kb-usage-r1-prereg.md:315-418`）。本行**取代 §10.7 的 PARTIAL 状态**；§10 保留为批次23 时点。基线不变：HEAD `d783ae846526b2da3fbe0a041a3695bf23188820` + 包 **v21**（`9f9e99ceec47cf8c` / 主包 `a7192484…`）+ dense **`79a22270…`** + 检索 **117/129**；本批**零内容/零资产写入**；八门 **8/8 rc=0**、一致性 **4/4 PASS**、`promote --dry-run` **rc=0（version→22，未写盘）**。

### 11.1 PARTIAL 项逐条新状态

| ID | 批次23 时点（§10.7） | 批次24 新状态 | 依据 / 边界 |
|---|---|---|---|
| `KB23-R1-02` 真实 Room 仪器化测试 | 未创建 | **关闭（验证面）** | 真 Room `TeachingMaterialAllocationInstrumentedTest` **4/4、0 failed、0 ignored**（批24 报 §3；`build/agent-batch24/device/teaching-material-allocation-result.txt`：`OK (4 tests)`、`am_instrument_shell_rc=0`）。设备 `emulator-5584`、API 34。**提交面未闭**：测试文件当前 `??`，须列入显式提交清单；测试钉的是 port 可观测语义，不是 DAO 物理行数/性能门（批24 报 §3.3） |
| `KB23-R1-03` 独立红测轮日志 | 缺失 | **保留 UNVERIFIED** | 旧实现已被新实现替换，当前工作树**无法重建旧实现**；测试文件 mtime/“先写测试”的时序**不能**当红跑证据。因此不声称“先红后绿”，只保留 `UNVERIFIED`（预注册 §7.4；`docs/kb-batch23-2026-10-26.md:197,394`） |
| `KB23-R1-06` 预注册 §7 | 未追加 | **关闭** | §7 已回写：**+107 / −0**，§1–§6 前 311 行逐字节相同（`git diff --numstat` = `107 0`；`cmp` 核验 `FIRST_311_IDENTICAL`）。内容含 F1–F5、真 Room、设备无回归、02/03/06/15/16 状态、API23 段、观测交接（批24 报 §4；预注册 `:315-418`） |
| `KB23-R1-15` 真机 / API 23 | 未跑 | **open（已登记 KD-77）** | `docs/known-defects.md:2831-2839`：本机无 `platforms;android-23` / `system-images;android-23*`，无 `api23_smoke` AVD，未安装 v21 APK、未做最小冒烟。**API 34 结果不能替代 API 23**；关闭条件见 KD-77（装机+冷启动+最小冒烟+logcat 四类崩溃签名 0） |
| `KB23-R1-16` 生产遥测 | 缺失 | **open（已交接）** | `docs/ops/kb-observability-handoff.md:6-7,223-239`：四项生产指标（M1 材料覆盖率、M2 空材料率、M3 检索延迟分布、M4 版本分布）均未落地；`KB23-R1-16` **保持 open**，交接单是凭证不是关闭证据。关闭条件 = 交接单 §5.3 的 C1–C4 四项全过 |

**相邻状态（不改 02/03/06/15/16 的判定）**：

- `KB23-R1-01`（R1 实现）：**已随 `8bb79efd` 入库**（`git show --stat 8bb79efd`）；本批只验证，不重复提交。§10.7 的“R1 代码未提交”是批次23 时点文本。
- `KB23-R1-14`（复算脚本入仓）：**部分关闭**。驱动 `tools/kb_coverage/retest_usage_r1.py` 已纳入索引（`A`，未提交），逻辑与原临时脚本从 docstring 起逐字节相同；但 `audit_knowledge_read.py` 仍在 `C:/Users/听云/AppData/Local/Temp/`，换机不可复跑（批24 报 §5.1）。

### 11.2 R6 结论（评估完成，不施工）

- **R6 仍归 `KB23-R1-11` open**；本批只评估，未改任何 sidecar/pack。
- 计数（批24 报 §9；`build/agent-batch24/r6-assessment.md:9-40`）：node boundary 含换行 14 条（前 80 字内 **6**）；summary 含换行 **13** 条（前 60 字内 12）；材料 64,973；相邻发现 material boundary 另有 10 条含换行（9 条 `\ne`），**不并入 R6 的 13+6**。
- **检索结论**（`build/agent-batch24/r6-assessment.md:99-137,157-183`）：
  - 13 条 `summaryMarkdown` **不进词面特征表、不进 dense、不参与 recall/rerank**；修 `\ne` 属**纯形态**，不改变检索。
  - 6 条 node `boundary` **进词面特征**（`KnowledgeSearchFeatureExtractor.fromNode`），但“换行→空格/分号”是**分隔符等价替换**（`[^\p{L}\p{N}]+`），特征集不变；dense 只吃 `canonicalName + aliases`，不含 boundary。**不得删除换行直接拼接**（会合并词片段）。
  - 材料**不进 FTS**：知识召回走 `knowledge_search_feature` 特征表（不是 FTS）；全仓唯一 FTS 是错题本库 `library_search_fts`。
- **金标影响**（`build/agent-batch24/r6-assessment.md:314-333`）：13 条受影响 summary **均未被** 129 条金标 top-5 选中；6 条受影响 node boundary 中 **1 条**（`电源的内阻-...`）被选中，换行→空格只改渲染形态、字符数不变。因此 v21 金标的 F1–F5/117–129/838 chars 理论不变，但这是静态推导，不是新包读数。
- **发布结论：需 v22 整链**（`build/agent-batch24/r6-assessment.md:184-246`）。sidecar 正文进 `contentVersion`；不改戳会被安装器快路径跳过；主包改动使 dense 旁车 `packSha256` 失配；runbook 明写任何材料/sidecar 正文写入都会使 117/129 基线失效；漂移门要求 device/JVM 同新包重跑。
- **本批不施工**。若未来做：另立 v22 预注册，只做“JSON 转义修正（`\n`→`\\n`）”与“换行→空格/分号”，不与其他内容变更混批。

### 11.3 Runbook 校验引用

- `docs/ops/kb-release-runbook.md:130`：**任何材料/sidecar 正文写入都会使已测 117/129 基线失效**，必须走“自产料池门 + 晋升”并另立预注册；不得在同一批里拿“补料”解释分数变化。
- `docs/ops/kb-release-runbook.md:144`：manifest 的 `version` 与 `contentVersion` **只在 promote 落盘这一刻刷新**（`tools/kb_build/promote.py:1-24`）。
- `docs/ops/kb-release-runbook.md:250`：**零改项复核**——判官两件、零漂移镜像、金标、**25 sidecar 正文**、模型资产跑前跑后 sha 必须相同。
- 批次24 对上述规则的校验读数：
  - `build/agent-batch24/gates/promote_dry.log:31-36`：`promote --dry-run` **rc=0**，`version→22`，**未写盘**；这是 `build/kb-staging` 干跑，**不是 v22 发布**。
  - `build/agent-batch24/consistency.txt:10-11,84-85`：manifest 仍是 **v21**（`9f9e99ceec47cf8c`），一致性 **4/4 PASS**、`EXIT=0`。
  - `build/agent-batch24/gates/summary.txt:3-18`：八门 **8/8 rc=0**、`overall red=0`。
- 因此：R6 **不能**作为“纯形态”直接改在 v21 上发布；检索理论上不变，但任何实际写入都必须走 v22 整链并重跑 device/JVM/zero-drift，不能沿用 117/129 作为新包证据。

### 11.4 最小复核命令

```bash
cd "D:/smart mistake book"

# F1–F5（只读；rc=0 表示脚本自身跑通，不是设备读数）
python tools/kb_coverage/retest_usage_r1.py

# 真 Room（device=emulator-5584；本轮实测 OK (4 tests)）
adb -s emulator-5584 logcat -c
adb -s emulator-5584 shell am instrument -w \
  -e class com.tingyun.smartmistakebook.core.data.knowledge.TeachingMaterialAllocationInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner

# API23 边界（No such file = 未安装，不得当通过）
ls -d /c/Android/Sdk/platforms/android-23 /c/Android/Sdk/system-images/android-23
adb -s emulator-5584 shell getprop ro.build.version.sdk   # 34

# 发布链干跑（只读检查；本批落盘 rc=0、version→22、未写盘）
PYTHONPATH=tools python tools/kb_build/promote.py --dry-run
```

## 12. 三件终裁落账（2026-10-29/30：182 / 114 / 402 → FINAL_ADJUDICATED）

> 委托来源：用户 2026-10-29「不需要我来复核，你直接判断」。裁决人：`zcode-principal(delegated-by-user)`。
> 性质：**文书/台账终裁，零写包**；未建点、未重绑、未改判定源、未 promotion。单一跨引用入口：`docs/research/2026-10-29-kb-final-adjudications.md`。

| 项 | 终态 | 读数与证据 |
|---|---|---|
| **182 拆分提案（E2）** | **FINAL_ADJUDICATED——REJECT 50 维持 / DEFER 132 NOT_APPLIED / ACT_NOW 0** | 50 REJECT 证据复核仍成立（contentId 23/23、节点 43/43、对齐表 11/11）；132 DEFER 三条件不齐（② 弱章诊断内容/绑定缺口 0；③ `promotionAllowed=false` 25/25 模块）。决策文件 `reviewState=FINAL_ADJUDICATED`，182 行均含 `finalDecision`。证据：`build/agent-batch25/adjudication/182-final.json`、`182-final-summary.md`；`knowledge-production/curriculum-warning-human-review-decisions-2025-v1.json` |
| **114 重开提案** | **FINAL_ADJUDICATED——REOPEN_NOW 68 / NOT_APPLIED 46**（BUILD 35/1、MERGE 33/3、REJECT 0/42） | 114 chunk 在输入与判定源 114/114；BUILD 36 规格齐、精确 name/alias 0 命中、parent 35/36；MERGE 目标 36/36（字面别名 25、语义 11）；REJECT 42 现包 slug 0 命中。`REOPEN_NOW` 仅为批准标记，本批零写包；执行触发见 CSV `trigger`。证据：`build/agent-batch25/adjudication/114-402-final.csv`、`114-402-final-summary.md` |
| **402 同键分类** | **FINAL_ADJUDICATED——KEEP_ALL 402 / DUPLICATE 0** | 402 组 / 827 行；去重载荷 + 归一化 content/summary/boundary/note + 同节点 title 四口径重复 0；判定源 sha256 `57aa7614…3cb3f` 未变，不改判定源。证据：`build/agent-batch18/scripts/verify_multi_midx.py`（`PASS`, rc=0）；`build/agent-batch25/adjudication/114-402-final.csv` |

- 原「E2 用户复核开放中」状态**自此由主控委托终裁取代**；三件均有终态计数与逐条去向，不再作为待用户复核项。
- 本批**不执行** 182 的 ACT_NOW（0 条）与 114 的 68 条 REOPEN_NOW：零写包边界未开；114 的执行条件（判定源改判 + 建点/重绑 + 重建包/晋升）已逐行写入 `trigger`。

## 13. 批次25 v22 写入链与终裁行（2026-10-29/30：v22 written-not-adopted；API23 smoke+DAO passed；三件终裁 FINAL_ADJUDICATED）

> 数字来源：`build/agent-batch25/` 产物实读 + 批报 `docs/kb-batch25-2026-10-29.md` + `build/kd77-api23-smoke/` 设备证据 + 本文书轮只读复算。基线：HEAD **`902d7acc`**（origin 同步 0/0）；HEAD 提交基线仍是 **v21**（manifest `9f9e99ceec47cf8c` / 主包 `a7192484…` / dense `79a22270…` 44,043）。工作树曾到达 **v22 检查点**（manifest `22` / `efda0a366fecc1ac`、主包 `1db011e1…`、dense `f2c4e68d…` / 44,305），但**未采纳**；验收基线仍为 **A1 117/129 = 0.9069767441860465**。本行不改变 §12 的三件终裁终态，只补批次25 的 v22/API23/S3/薄料与残留。

| 项 | 状态 | 读数与证据 |
|---|---|---|
| **v22 写入链（R6 + S3 + promote + dense）** | **written-not-adopted** | R6 **29 条**（13 summary + 6 node boundary within80 + 10 material boundary）；S3 **262 行 / 174 节点**；`promote` **24 项自检 0 失败 / roundtrip 28-0 / version 22 / contentVersion `efda0a366fecc1ac`**；dense **44,305 向量 / 26,196,311 B / `f2c4e68d…`**，旁车 `d94ff211…`、packSha `1db011e1…`、aliasVectors 41,060；tracked 写入 16 个、post-sha 16/16。证据：`build/agent-batch25/content-write.log:22-128,134-163`；`promote-post-verify.json:29-40`；`dense.log:246-248`；`content-touched-files.txt:1-74` |
| **v22 读数 / 非劣裁定** | **NOT_MEASURED / NOT_ADJUDICATED** | 设备融合、JVM 双臂、24 章逐章均未测；零漂移 **NOT_COMPUTABLE**；显著性 **NOT_RUN**；四条非劣判据无法求值，不能判 PASS/FAIL。离线 expectation 词面 **101/129**、dense **104/129**、融合 **114/129**、MRR **0.718475…** 只作风险登记（114 < A1 117），不是设备读数。证据：`dense.log:253-258,281-286`；预注册 `docs/research/2026-10-29-kb-v22-content-update-prereg.md:118-129` |
| **v22 最终门 / 重钉 / 漂移** | **blocked（2 门红 + 重钉缺失 + 包漂移）** | 八门 **6/8 rc=0**；`py_suite` rc=1（3 failures：alias_map 与包不一致、contentVersion 复算 `ebf03c9…` ≠ manifest `efda0a36…`、dense 资产门）；`asset` rc=1（当前包 `e5dc3303…` vs 旁车 `1db011e1…`；layout 44,305 vs 44,304）。当前主包 `e5dc3303…` 在别名结构上比 staging **少 1 条别名 `AABB×aabb`**（BIOLOGY/自由组合定律中的特殊分离比问题分析；未逐字段比较其他字段），mtime 02:06:07，**不在本批 16 个 tracked 写入清单内**；写入者未登记。重钉未做：`VECTOR_ASSET_SHA256` 仍 `79a22270…`（实包 `f2c4e68d…`）、词面测试仍钉 102（实测 101）、reranker 仍钉 1,074（新 1,064）。dense export `int8_docs` cosine **0.997438788 < 0.999**（EXIT=1）；端到端 min 0.997334（记录非硬门）；量化还原 0.999746 > 0.999。证据：`gates/summary.txt:1-62`；`consistency.txt:12-44`；`dense.log:281-286`；`dense-export.log:199-211`；`dense-pack.log:5-9` |
| **S3 别名轨** | **applied（262/174），随 v22 未采纳** | 规则 `body-alias-v1/S3@1`、rule_hash `938e2026…ae10`；候选 262 行/174 节点（MATH 88/58、PHYSICS 117/73、CHEMISTRY 45/35、BIOLOGY 12/8）；cap 3 / max 3 / violations 0；span **107,127**（d1 106,262 / d2 **865**，不采用 86）；dry-run 冲突 0、applied 262/262；别名 **40,798→41,060**；`alias_exceptions` **35→297**；`alias_map` **37,553→37,815**；`alias_dropped` 22,559 不变。证据：`gen_s3_aliases.out.txt:3-7`；`validate_s3.out.txt:1-4`；`dryrun_s3.out.txt:7-15`；`s3-verify.out.txt:1-16` |
| **薄料轨** | **blocked / 零写** | 行 23「必备实验仪器的原理及使用」维持；运行时/写入链**支持** SUPPORTING 第二绑定（schema 复合主键、契约 1..16 且恰一 PRIMARY、DAO 角色序、调和器整组替换），但作者工具链**不能产出**（`build.py` 只写单元素 PRIMARY、`:583-588` 拒绝 `>1`、`tables.py` 一材料一目标、`rebind_materials` 是改指+去重）；全库 **64,973 条材料全 1 条绑定、全 PRIMARY、SUPPORTING 0**。需扩三处（`build.py` 规则、绑定表单目标模型、追加绑定命令）。证据：`build/agent-batch25/baseline/identity.md:233-375`；`content-write.log:89-93`；本文书轮成品 25 sidecar 复算 `64973 / {1:64973} / {'PRIMARY':64973}` |
| **KD-77（API23）** | **open / partial——smoke+DAO passed；融合 golden 红** | API23 `api23_smoke`（Android 6.0 / google_apis x86_64）：`com.tingyun.smartmistakebook.offline` 冷启动 `Displayed ... +2s873ms`；6 份 logcat 四类崩溃签名（FATAL/ANR/NoSuchMethod/NoClassDef/UnsatisfiedLink）均 **0**；`TeachingMaterialAllocationInstrumentedTest` **OK (4 tests)**（API23 1.237/1.169；API34 `emulator-5584` 5.177/4.634）。但 `GoldenRetrievalInstrumentedTest` API23 **1/1 红**：`dense asset sha256 mismatch f2c4e68d… != 79a22270…`（重钉缺失，不是 API23 平台差异；API34 融合本批未复测）。KD-77 不得整体标绿；关闭仍需重钉后在 API23 复跑融合判官并落盘。证据：`logcat-coldstart.txt:261`；`am-instrument-teaching-allocation.txt:4-6`；`5584/am-instrument-teaching-allocation-5584.txt:4-6`；`logcat-golden.txt:807-810`；`docs/known-defects.md:2899-2919` |
| **182 拆分提案终裁** | **FINAL_ADJUDICATED** | **REJECT 50 维持 / 0 推翻；DEFER 132 → ACT_NOW 0 / NOT_APPLIED 132**；182 行均含 `finalDecision`；c1 形态 **91 / 35 / 5 / 1**；reviewState=FINAL_ADJUDICATED。证据：`build/agent-batch25/adjudication/182-final-summary.md:14-55`；`docs/research/2026-10-29-kb-final-adjudications.md:10-20,43-54` |
| **114 重开提案终裁** | **FINAL_ADJUDICATED** | **REOPEN_NOW 68 / NOT_APPLIED 46**（BUILD **35/1**、MERGE **33/3**、REJECT **0/42**）；114/114 chunk 在位；BUILD 36 规格齐、精确 name/alias 0 命中、parent 35/36；MERGE 目标 36/36；REJECT 42 现包 0 命中。`REOPEN_NOW` 仅为批准标记，本批零写包、未执行；执行触发见 CSV `trigger`。证据：`114-402-final-summary.md:14-40`；`docs/research/2026-10-29-kb-final-adjudications.md:79-106` |
| **402 同键分类终裁** | **FINAL_ADJUDICATED** | **KEEP_ALL 402 / DUPLICATE 0**；402 组 / 827 行；四口径重复 0；判定源 sha256 `57aa7614…3cb3f` 未变。证据：`114-402-final-summary.md:42-50`；`docs/research/2026-10-29-kb-final-adjudications.md:119-138` |
| **验收文书 v22 小节** | **未追加（条件不成立）** | v22 未采纳、读数未测得；按 ask“若采纳”条件不成立。E2 终裁跨引用已在 `docs/kb-acceptance-2026-10-17.md:650,707` 存在（本批未改）。 |

**批次25 最终残留清单（按阻塞程度）**：

1. **重钉缺失**：`DenseRecallAssembly.VECTOR_ASSET_SHA256`（`79a22270…`→`f2c4e68d…`）、`ProductionLexicalLegExportTest`（102→101 等）、`DenseRecallRerankerTest`（1,074→1,064）、Stage1/Stage2 锚点；不重钉则设备融合腿回退纯词面（`logcat-golden.txt:807`）。
2. **post-promote 包漂移**：当前主包 `e5dc3303…` 少 `AABB×aabb`，导致 `asset` 门 `packSha256`/`layout` 红与 `py_suite` 表/戳红；需查明写入者并恢复/重跑 promote。
3. **八门红**：`py_suite` 3 failures、`asset` 2 FAIL、`overall red=1`（`gates/summary.txt:62`）。
4. **dense export 门**：`int8_docs` cosine 0.997438 < 0.999（EXIT=1）；端到端 min 0.997334 < 0.999（记录非硬门）。
5. **读数缺失**：设备/JVM 融合/词面/逐章/MRR 未测；零漂移 `NOT_COMPUTABLE`；显著性 `NOT_RUN`；非劣 `NOT_ADJUDICATED`。
6. **API23 融合**：smoke/DAO 过，融合 golden 红；重钉后需在 API23 复跑并落盘。
7. **R2–R6 / 观测面**：R2–R5 契约侧、R6 已修但随 v22 未采纳；生产遥测 M1–M4 未落地。
8. **114 执行**：68 条 `REOPEN_NOW` 未执行；需改判定源、建点/重绑、重建包/晋升。
9. **薄料工具链**：需扩 `build.py` 的 `>1` 规则、绑定表单目标模型、追加绑定命令；本批零写。

**最小复核命令**：

```bash
cd "D:/smart mistake book"
git rev-parse HEAD                     # 902d7acc…
python tools/dense_build/check_asset.py # RC=1；packSha/layout 两条 FAIL
python build/agent-batch18/scripts/verify_multi_midx.py   # PASS；KEEP_ALL 402
python build/agent-batch18/verify_reopen_decisions.py     # rows 114；RC=0
python - <<'PY'
import json,csv,collections
d=json.load(open('knowledge-production/curriculum-warning-human-review-decisions-2025-v1.json',encoding='utf-8'))
print(d['reviewState'],len(d['decisions']))
r=list(csv.DictReader(open('build/agent-batch25/adjudication/114-402-final.csv',encoding='utf-8-sig')))
print(len(r),collections.Counter((x['track'],x['final_decision']) for x in r))
PY
# FINAL_ADJUDICATED 182；516 Counter({('402','KEEP_ALL'):402,('114','REOPEN_NOW'):68,('114','NOT_APPLIED'):46})
```
