> 本文件已过时：知识库未竟清单以 docs/kb-outstanding-research-2026-10-02.md 为准（2026-10-02）。

> **现行数字（2026-10-20 复算）**：知识点 **3,245**（主题 425）/ 教学材料 **64,973** 条（sidecar v2-01..25）/ 课标覆盖 **390**/661（PARTIAL 73、NOT_COVERED 198、UNDECIDED 0）/ 薄料 live **205**（零材料 0）/ 超配（>4）**2,482** / 未判（机械 SKIP）**61,170**。复算入口：`moe-2025-four-subjects-v1.json`（现行 **v20**，批次19 A1 采纳 2026-10-25；2026-10-20 复算时为 v17、批次18 时 v19）、`moe-2025-teaching-support-v2-*.json`、`kb-coverage-alignment-2026-v2.json`、`PYTHONPATH=tools python -m kb_build.report_material_gaps`、`PYTHONPATH=tools python -m kb_coverage.count_unjudged`。下文 2026-09-20 快照与 2026-10-02 注记按日期保留。

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
