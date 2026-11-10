# 批次 51 · D-2 结构审计 + 抽样框冻结 + D-3（5 红 / 召回保持）

> 结论（先说边界）：
> - **D-2 四项（近重复 / 超配 / 容量 / 历史 type）逐项清零或登记，零包写入、零绑定语义改动**。
> - **抽样框已冻结**：`3d-audit/frame/overfull_nodes.snapshot.csv`，sha256
>   `ce7d6823…c69d9d`，62,944 行 / 2,481 节点；**本线未重写 `build/kb-staging/overfull_nodes.csv`**，
>   收工复核 staging 原件与快照 sha 相同。
> - **D-3 五红清零**：5 个类 12 tests / 0 failures / 0 errors（本会话实跑）。
> - **D-3 召回未达“117/129 保持”**：当前实测 **116/129 = 0.8992248062015504**，
>   24 章中 **1 章下降**（物理必修第一册·第四章·牛顿运动定律 1/1 → 0/1），其余 23 章逐位相同。
>   单题翻转已归因到 **KD-24 别名截断位移**（见 §3.3），**未改任何阈值 / 索引 / 融合参数 / 内容**。
>   恢复 117 的每条路径都要动一条既有红线，需主会话裁定（§3.5）。

## 0. 身份与复跑入口

| 项 | 值 |
|---|---|
| shipped pack sha256 | `aaf81be884933b9105db737feee3eb3d84ff440c53f0e2d01c806aafd3790032`（v29，与批次51 P1 一致） |
| staging pack sha256 | 同上（release == staging，本审计开头断言） |
| 节点 / 材料 / 绑定 | 3,279 / 64,850 / 64,850 |
| 金标 | `golden_queries_v2.json` sha256 `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102`，129 条 / 24 章 |
| D-2 审计脚本 | `python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/3d-d2d3/audit_d2_b51.py` → `d2-summary.json` |
| D-3 归因复算 | `python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/3d-d2d3/d3/probe_case97.py` → `d3/probe_case97.out` |
| D-3 端侧复算 | `python -I -X utf8 tools/dense_build/stage3_device_sim.py` → `d3/sim-v29.txt` |
| 五红 | `./gradlew.bat :core:data:testDebugUnitTest --tests "*GoldenRetrievalJvmTest*" --tests "*ProductionLexicalLegExportTest*" --tests "*Stage1LexicalLabTest*" --tests "*Stage2LexicalScoresExportTest*" --tests "*DenseTokenizerParityTest*" --console=plain --rerun` → `d3/five-red/five-red.log` |

## 1. 抽样框冻结（任务书第 2 条）

- 快照：`docs/research/evidence/2026-11-10-batch-51/3d-audit/frame/overfull_nodes.snapshot.csv`
- 清单：`3d-audit/frame/frame_manifest.json`
- **sha256 `ce7d6823787204640a646854561ea85cbbf4f19c48a0809754a3b0f419c69d9d`**，
  15,892,450 bytes，CSV 解析 **62,944 数据行 / 2,481 节点**（`wc -l` 为 62,954，差值是字段内换行）。
- 源文件 mtime：2026-11-08T07:20:55Z（**v28 时代**，批次51 P1 改绑之前生成）。
- 冻结规则与执行：
  1. 本线**没有**运行 `overfull_nodes --write`，也没有以任何方式写 `build/kb-staging/overfull_nodes.csv`；
  2. D-2 审计脚本在读取前断言快照 sha == 上值，并同时断言 staging 原件 sha == 上值
     （`audit_d2_b51.py` 的 `identity` 段；收工再次 `sha256sum` 复核仍为 `ce7d6823…`）；
  3. **“若必须重写，先在快照后停止”**：本线判断 v29 复算有必要（见 §2.2），但**没有**写回
     staging；复算产物另落 `d2-overfull-recompute-v29-nodes.csv`（节点级，行级 16MB 明细在
     `build/b51-d2d3-scratch/overfull_recompute_v29.csv`，gitignored 可重生成）。
- **框与当前包的差异（必须让 D-1 抽样方知道）**：快照是 v28 成员面；v29 只读复算为
  2,483 节点 / 62,969 条（+2 节点 / +25 条），节点级 diff 见 `frame-divergence.json`：
  - 掉出超配 1 个：`PHYSICS/数据处理-x1`；
  - 新进超配 3 个：`BIOLOGY/管家基因与奢侈基因`、`CHEMISTRY/化学反应的本质`、
    `PHYSICS/电磁波谱与各波段的特性`；
  - 50 个节点材料数变化（±1 为主，来自 40 条改绑）。
- 读法：**D-1 的 overfull 层抽样如需与冻结框严格一致，应使用快照**；若主会话决定改用
  v29 复算面，那是**换框**，必须先记录新 sha 与新 audit id（本线不擅自替换）。

## 2. D-2 结构审计（只读；零包写入）

### 2.1 近重复

| 口径 | 命令 | 读数 |
|---|---|---|
| 节点级 | `kb_build.scan_duplicate_nodes --json` | **`{"same": [], "prefix_pairs": []}`**（0 组 / 0 对）→ `d2-near-dup-scan.json` |
| 候选区 2 组 | `audit_d2_b51.py` | 两组均**在包、同节点、正文近同**：CHEMISTRY/乙烯… `cmp-che-02368`(242字) vs `cmp-che-02639`(240字)；MATH/特殊的棱柱和棱锥 `cmp-mat-00280`(164) vs `cmp-mat-00065`(161)。裁决 **保留长正文条、退休另一条（未执行）** → `d2-near-dup-dispositions.csv` |
| 材料级 | `kb_coverage.dedupe_near_dup_materials --dry-run --out-dir build/b51-d2d3-scratch/dedupe --json` | 材料 64,850 / 节点 3,279；**候选对 173 / 簇 50 / 拟退休 1 / SKIP 20**；保护：别名 35,535、锚点 10、金标 3,852（128 节点）、THIN 633（438 节点），合计 37,536 → `d2-dedupe-dryrun.txt` / `d2-dedupe-retire-list.csv` |

- 拟退休 1 条：`ext-che-2728b2ed9a-016` → 保留 `ext-che-4f82967fc2-009`（`纯净物` 簇）。
  **未执行**（本线零内容写窗口；执行判据同批次50：drop → `promote --dry-run` 23/23 → 材料数 −1、无悬空、别名唯一支撑检查过）。
- 上述 2 组候选区近重复**未进拟退休**（实体替换保护门的已知假阴性），与批次50 结论一致。

### 2.2 超配（>4 条 / 节点）

- **冻结框**（权威抽样框）：**2,481 节点 / 62,944 条 / 超出 53,020 条 / 最大 146（MATH 三角函数的图象与性质）**；
  零 2-gram 5,954 条（超配段 4,918）；分诊 PRIORITY_REVIEW 49 / REVIEW 1,009 / NONE 1,423。
- **v29 只读复算**（与 `tools/kb_build/overfull_nodes.py` 同口径；工具自跑读数逐位一致）：
  **2,483 节点 / 62,969 条 / 超出 53,037 条**；节点内 ≥4 共 2,657（=4 共 174）；最大 146；
  零 2-gram 5,952（超配段 4,918）。→ `overfull-report-v29.txt`、`d2-overfull-recompute-v29-nodes.csv`
- **保留策略：`RETAIN_NO_CAP`**——运行时无每节点条数门（整批取数上界 1,024 行；展示由 20,000 字符
  预算按「角色秩→重教类型→title→materialId」逐条装填），超配不是失效绑定 ⇒ 不删、不解绑；
  分诊标记只决定复核顺序。台账：`d2-overfull-ledger-frozen-frame.csv`（冻结框，2,481 行）。
- **新增登记（结构面，非判定）**：别名容量 >> 索引容量——v29 包 **75.85%（2,487/3,279）节点片段数 >16**，
  其中 1,284 个「名称+别名」就超；≥40 个别名的节点 158 个（`alias-saturation-v29.txt`）。
  这是 KD-24 的 open 缺陷面，也是 §3.3 单题翻转的结构条件。

### 2.3 容量拦截 91 条（前提已被运行时推翻）

`knowledge-production/candidate-merge/capacity_blocked.csv` 91 行逐行登记（`d2-capacity-dispositions.csv`）：

| 处置 | 行数 | 含义 |
|---|---|---|
| `ALREADY_IN_PACK_SAME_NODE` | 22 | 已在包且绑定就是当时目标节点 |
| `IN_PACK_OTHER_NODE` | 12 | 已在包但绑到别的节点（内容线后续改绑结果；需内容线复核） |
| `NOT_ADMITTED_HISTORICAL` | 44 | 未入包、节点仍在 ⇒ 历史候选，不据此补/删 |
| `OBSOLETE_NODE` | 13 | 未入包、目标节点已不在包（退役/改名） |

- 废止依据不变：`tools/kb_build/merge_candidate_verdicts.py:165` `MAX_VISIBLE = 4` 与 `:209-216`
  ≥4 即 return，而运行时无该门。
- 批次50 的 `d2-capacity-deprecation.patch` 仍 **NOT_APPLIED**（目标文件属他线在飞面，本线不抢占）。

### 2.4 历史 type

- 64,850 条材料：`CONCEPT_EXPLANATION` 35,484 / `METHOD_MODEL` 25,436 / `MISCONCEPTION_GUIDE` 3,930；
  **三值外 = 0**（`d2-type-distribution.csv`）。
- 白名单单源不变：`tools/kb_coverage/materialize.py:66`、`tools/kb_coverage/merge_text_judgments.py:47`、
  `docs/kb-chunk-judgment-protocol.md:62/90`（三处均 3 值）。

### 2.5 工具测试

```
python -I -X utf8 -m pytest tools/tests/test_kb_scan_duplicate_nodes.py \
  tools/tests/test_kb_overfull_nodes.py tools/tests/test_kb_materialize.py -q
# → 26 passed in 1.70s   （d2-tool-tests.txt）
```

## 3. D-3：五红 + 召回保持

### 3.1 五红清零（本会话实跑）

命令见 §0；结果 **BUILD SUCCESSFUL / EXIT=0**，逐类：

| 类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| GoldenRetrievalJvmTest | 2 | 0 | 0 | 0 |
| ProductionLexicalLegExportTest | 1 | 0 | 0 | 0 |
| Stage1LexicalLabTest | 1 | 0 | 0 | 0 |
| Stage2LexicalScoresExportTest | 1 | 0 | 0 | 0 |
| DenseTokenizerParityTest | 7 | 0 | 0 | 0 |

存证：`d3/five-red/TEST-*.xml`、`d3/five-red/five-red.log`。
**如实边界**：这证明“当前树 12/12 绿”，不是“本批修复让五红转绿”（五红成因在批次13 已重钉；
本批 P1 改绑后 `ProductionLexicalLegExportTest` 的 MRR 锚已按实测重钉为 `0.6184754521963827`，
`Git` 可见该改动属 P1，不是本线）。

### 3.2 召回读数（本会话实跑，两套镜像一致）

| 路线 | 读数 | 来源 |
|---|---|---|
| 端侧算法（词面召回域内排序） | **116/129 = 0.8992248062015504**，MRR 0.7174418604651163 | `d3/sim-v29.txt` |
| 端侧算法（整科域排序） | 116/129（同值） | 同上 |
| JVM 融合（真 SQL 镜像 + 假编码器） | **116/129 = 0.8992248062015504**，MRR 0.7210594315245478，MISS 13 | `d3/five-red/TEST-GoldenRetrievalJvmTest.xml`、`d3/jvm-fused-metrics-v29.txt` |
| 词面腿（生产 v1，D1 形） | 101/129 = 0.7829457364341085，MRR 0.6184754521963827 | 同上 |
| 离线参考 refFusedD1（整科域，非端侧） | 118/129（批次50 同口径也是 118） | `d3/sim-v29.txt` |

**24 章不降：不成立。** 与批次50 v28 基线逐章 diff（`d3/fused-chapters-vs-batch50.diff`）：

```
19c19
<   物理必修第一册·第四章·牛顿运动定律 = 1/1 = 1.0 (MISS 0)
---
>   物理必修第一册·第四章·牛顿运动定律 = 0/1 = 0.0 (MISS 1)
```

其余 23 章逐位相同；该章只有 1 道金标题（case #97），所以单题翻转 = 该章 100% 下降。

### 3.3 单题翻转归因（117→116，全部本会话实算）

**case #97**：`expectedSlug=超重与失重的判断方法`，query「电梯向上减速的过程中…看加速度还是看速度方向？」。

因果链（每一步都有实跑产物）：

1. **P1 改绑（内容正确）**：材料 `phys-chao-zhong-shi-zhong-pan-duan`（title
   `超重与失重的判断（看加速度方向）`，正文「加速度沿斜面向上（有竖直向上的分加速度）时处于超重状态…」）
   从 `加速度方向突变` 改绑到 `超重与失重的判断方法`。
2. **别名闭合（P1 规则本来如此：别名 = 该节点绑定材料的标题）**：目标节点 aliases 40 → 41，
   新增正是该标题；源节点 3 → 2。name / boundary 均未变（`d3/alias-diff.json` 实读）。
3. **索引位移（KD-24）**：该标题被括号切成 **2 个片段**，节点片段数 70 → 72；
   `KnowledgeSearchFeatureExtractor.fromNode` 的 v1 形状是「16 片段上限 + 按位置平衡采样」，
   +2 片段使平衡采样选中集合整体换了一批（`d3/feature-set-v28-vs-v29.txt`）：
   - v28 选中 `受力角度与加速度角度` 与边界片段 `斜向加速度要取其在竖直方向的投影`；
   - v29 两者都没进 16 片段预算，节点命中查询的特征由 **7 个降为 4 个**
     （丢掉 `加速 / 加速度 / 速度`，只剩 `判断 / 失重 / 方向 / 超重`）。
4. **融合名次**：词面计数 7 → 4 ⇒ 词面 min-max 0.6364 → 0.3333；稠密腿仍是全科最高（1.0000），
   但 α=0.5 下 fused 0.8182 → 0.6667，名次 **3 → 7**（top-5 外）。
5. **隔离证明**（`d3/isolation97.txt`、`d3/probe_case97.py`）：固定 v29 稠密分与 v29 候选域，
   **只**把期望节点的词面计数 4 换回 7 ⇒ rank **7 → 4（命中）**。翻转由该节点的词面计数单独造成，
   与稠密腿、节点删除（3,280→3,279）、min-max 分母无关。
6. **KD-24 早已逐字预言这一类**：`docs/kb-batch22-2026-10-26.md:190`「新增 2 条别名**可能不增加净特征，
   反而挤掉既有片段**」；`docs/known-defects.md:875` 记该缺陷 open、归宿=dense 兜底、
   **词面侧不再以去截断方式修**（v2 实测净伤害：主集 0.5222/0.3444、p95 663ms）。

### 3.4 未改项（硬约束）

- **零阈值**：3D 0.95/0.90、D-3 召回判据、`DenseFusion.ALPHA=0.5`、词面/稠密地板、p50/p95 门、金标 sha 均未动。
- **零索引**：`MAX_SEARCH_FRAGMENTS=16` / `MAX_NODE_FEATURES=192` / `INDEX_VERSION=1` 均未动，
  没有建第二套词面实验（KD-24 批次40 移交边界：KB 线不改 `INDEX_VERSION`、不动锚点换血语义）。
- **零内容/零绑定**：pack/材料/绑定/别名均未写（本线只读）；P1 的改绑与别名闭合原样保留。
- **零断言放宽**：五红的断言未改；`GoldenRetrievalJvmTest` 本来就是“测量台不断言质量阈值”。

### 3.5 为什么没有“实修”117：每条恢复路径都撞红线（需裁定）

| 路径 | 代价 | 触及的红线 |
|---|---|---|
| (a) 删掉该条别名（回到 v28 别名集） | 预计恢复 117（隔离已证单题归因），但 `aliases == 绑定材料标题` 的闭合被破坏；`rebuild_aliases --write` 下次会**回加**，回归不可持久 | 内容不变量 + “不为过门改内容” |
| (b) 改词面索引（冗余片段选择 / 提额） | 需 `INDEX_VERSION` bump + 三组数对照（设备 + JVM 零漂移 + 19 例回归），且会重钉 Production/Stage1/Stage2 三个测量锚 | KD-24 移交边界（不改索引版本/不建第二套词面实验） |
| (c) 调融合 α 或词面归一 | 不改内容即可过 117 | “不得调阈值/调参”、预注册 α=0.5 |
| (d) 如实记 116（本线交付） | D-3 的“117 保持 + 24 章不降”不成立，3D 门判需主会话另行处置 | 无（最保守） |

**本线建议**：先按 (d) 记 116 与完整归因（数据可复算），把 (a)/(b) 作为**需用户裁定的变更**
提交，不要在 D-2/D-3 内静默执行任何一个。

## 4. 未做 / 边界

- **未跑设备仪表化**（`GoldenRetrievalInstrumentedTest`）：本机 `adb` 不在 PATH（`adb devices` →
  `command not found`），本阶段未起模拟器；116 的两个读数都来自离线/ JVM 镜像
  （批次50 两者曾逐位一致 117，故不预期设备另有 117）。
- 未跑 2h soak、未跑跨模块全量单测；Gradle 只跑了五红 5 类 + 稠密 3 类（共 21 tests）。
- `stage3_device_sim.py` 会重生成 `core/data/src/test/resources/dense/dense-device-order-reference.txt`
  （本会话运行时间 01:26，晚于 P1 的 01:07）。重生成后 `DenseRecallRerankerTest`(5) /
  `DenseVectorAssetTest`(5) / `DenseLayoutIdentityTest`(4) 全绿（`d3/dense-tests.log`）——
  即该 fixture 与当前 leg/资产自洽；但这是**改动了他线生成物**，在此如实登记。
- D-2 的 v29 复算与 alias 饱和统计都用了 Python 近似 Kotlin 的 `\p{L}\p{N}` 切分；
  计数级结论可信，**逐节点逐位一致未验证**（case #97 节点单独与 Kotlin 导出对过：4 == 4）。
- 未对 2,483 个超配节点做逐节点内容裁决（本批给保留策略 + 台账 + 分诊顺序，不是“逐节点已判定”）。
- 未应用批次50 的容量补丁；未执行近重复退休（两条候选 + 材料级 1 条）。

## 5. 文件清单

| 文件 | 内容 |
|---|---|
| `3d-audit/frame/overfull_nodes.snapshot.csv` | 冻结抽样框（sha256 `ce7d6823…`，62,944 行 / 2,481 节点） |
| `3d-audit/frame/frame_manifest.json` | 冻结清单（sha/字节/行数/列/规则/stale 说明） |
| `audit_d2_b51.py` / `audit-d2-b51.out` / `d2-summary.json` | D-2 审计脚本与汇总 |
| `d2-overfull-ledger-frozen-frame.csv` | 冻结框节点级台账（2,481 行，RETAIN_NO_CAP + 分诊） |
| `d2-overfull-recompute-v29-nodes.csv` / `overfull-report-v29.txt` / `frame-divergence.json` | v29 只读复算 + 与冻结框差异 |
| `d2-near-dup-scan.json` / `d2-near-dup-dispositions.csv` / `d2-dedupe-dryrun.txt` / `d2-dedupe-retire-list.csv` | 近重复三项 |
| `d2-capacity-dispositions.csv` | 容量 91 行逐行处置 |
| `d2-type-distribution.csv` | 历史 type 分布（三值外 0） |
| `alias-saturation-v29.txt` | 别名/索引容量饱和（KD-24 面，登记用） |
| `d2-tool-tests.txt` | D-2 工具测试 26 passed |
| `d3/five-red/` | 五红 5 个 XML + gradle 日志 |
| `d3/sim-v29.txt` / `d3/jvm-fused-metrics-v29.txt` / `d3/fused-chapters-vs-batch50.diff` | 召回读数与 24 章 diff |
| `d3/probe_case97.py` / `probe_case97.out` / `isolation97.txt` / `feature-set-v28-vs-v29.txt` / `feature-matched-v28-vs-v29.txt` / `case97-breakdown.txt` / `alias-diff.json` | 单题翻转归因链 |
| `d3/dense-tests.log` | fixture 重生成后的稠密 3 类测试 |
