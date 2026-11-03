# KB B33 B1 摘要-only（127 条改写）预注册（2026-11-02）

> **性质**：出数前冻结。§1–§5 写死；执行轮只允许在文末追加 §7，不得回改 §1–§5。
> **写作时点**：2026-11-03T06:51:21+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-02` 是批次日，不是落盘日。
> **HEAD / origin/main**：`faab17f58b01eab78d5c08a9f843b3af3df2fecf`（`git rev-parse HEAD` / `origin/main` 实跑，同值；本会话期间 HEAD 已由 `62522930` 前进多次，属他线提交）。
> **上游**：`docs/research/2026-11-02-kb-r3-dedupe-summary-quality.md`（下称 **R3**，§3.2 的 **A1 摘要-only 零假设臂**、§3.1 的身份冻结、§3.3 的配对检验）；`docs/kb-batch32b-2026-11-02.md:84-91`（127/127 落盘、退化摘要 127→0 的批内读数）；输入产物 `build/agent-batch32b/rewrite/summary-new.json` 与 `summary-rewrites-new.csv`。
> **本步唯一新增写盘**：本文件。未改任何内容包、未跑 Gradle/设备/JVM；§0.3 的读数本会话用 `python -I`/`sha256sum` 实读（非设备读数）。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-03T06:51:21+0800
git rev-parse HEAD                             # faab17f58b01eab78d5c08a9f843b3af3df2fecf
git rev-parse origin/main                      # faab17f58b01eab78d5c08a9f843b3af3df2fecf  → 同值 ✓
git status --porcelain | wc -l                 # 557
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   438 ??
#    14  D      ← 全部是 core/data/.../pdf/*（他线在飞的 PDF 模块删除）
#   105  M
git diff --cached --name-only | wc -l          # 0（暂存区空）
ls docs/research/2026-11-02-kb-b33-b1-summary-only-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 557 项脏项属他线与已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`。
- 14 个 `D` 是他线 PDF 模块（`PdfAigc*` / `PdfCrossRef*` / `PdfSyntax` 等），与本臂零交集。
- 本臂触碰的 6 个 sidecar + manifest 在 `git status --porcelain -- <§5.1>` 下**均为空**（写前干净）。

### 0.2 输入与现状（实读）

**输入（sha 钉死）**：

| 输入 | 值 |
|---|---|
| `build/agent-batch32b/rewrite/summary-new.json` | sha256 `ea8a8134d9ff3fd9de716ef4efe432fa0cec9fd82b369892ec443a8e76659072`；**127** 条 `slug → new summary` |
| `build/agent-batch32b/rewrite/summary-rewrites-new.csv` | sha256 `eac2bde86b50c99fa855165957d088404ab7ca6b11f4b5bd21a59b96a850eda7`；127 行（列：`slug,subject,node,title,old_summary,old_len,new_summary,new_len,reason`），`reason` 全为「按该材料 content 首句/要点重写；未改 contentMarkdown」。**本臂把它当作逐条判定表**（每行 = 一条改写的旧值/新值/理由）。`build/agent-batch32b/` 下另有两类同名前缀文件，**都不是**改写判定表：`baseline/neardup-*-summary.csv` 是近重复基线（列含 `kept_slug/dup_slug`），`gates/summary.txt` 是门汇总日志（本会话 `head` 实读） |
| 分科分布 | CHEMISTRY 61 / BIOLOGY 48 / PHYSICS 16 / MATH 2 |

**现状（本会话读包实算）**：

| 事实 | 值 | 来源 |
|---|---:|---|
| 包内材料 | **65,021** | 25 个 2025 sidecar 的 `materials[]` |
| manifest | `version=26` / `contentVersion=aea886e42b36dcb2` / `schemaVersion=2` / `retired=1,222` | `moe-2025-update-manifest.json` |
| **内容戳复算** | **`aea886e42b36dcb2`**（与 manifest 逐字相同） | 按 `tools/kb_build/update_manifest.py:256-266` 的 `content_version(pack, sidecars)` 复算 |
| 退化摘要（全包） | **127** | 定义见 §0.3 |
| 127 条目标是否都在包内 | **是（missing = 0）**；且**全部 127 条当前都是退化摘要** | 逐 slug 比对 |
| 新摘要再退化 | **0**；空摘要 **0** | 同上 |
| 受影响的 sidecar | **6 个**（v2-02:16、v2-03:35、v2-04:26、v2-05:45、v2-06:3、v2-18:2） | 逐文件统计 |
| 主包 / dense | `96246c78…` / `.vec` `70329115…`、`.vec.json` `6612a28d…`、`packSha256=96246c78…` | `sha256sum` 实跑 |

### 0.3 本会话实算（离线，非设备读数）

**退化摘要的冻结定义**（与批次32B 的复算逐字一致，`docs/kb-batch32b-2026-11-02.md:367-380` 的脚本）：

```python
def norm(x): return re.sub(r"\s+", "", unicodedata.normalize("NFKC", x or "").casefold())
def degenerate(m): 
    s = (m.get("summaryMarkdown") or "").strip()
    return len(s) <= 8 or norm(s) == norm(m["title"])
```

**本会话读数（`python -I` 实跑，脚本不入仓）**：

| 项 | 值 |
|---|---:|
| 全包材料 / 退化数 | **65,021 / 127** |
| 127 条中「当前退化」 | **127 / 127** |
| 新摘要「仍退化」/「空」 | **0 / 0** |
| **应用后预测 `contentVersion`** | **`ae939632c8afc4db`**（在内存中应用 127 条后按同一 `content_version()` 复算） |
| 节点表面输入 sha（3,705 节点：kind/slug/name/sorted aliases/boundary） | `c36840819a737891c42731b54fb08a52aa1289f42d6c8fad42baf4a078045524` |
| dense `.vec` sha / id 块 sha | `70329115…` / `8f1778326060c1dd2e07597adb6e8d8209080d7684cc9b430ea10ced1a16864e` |
| dense id 数 / 唯一节点数 / `format.count` / `corpus.vectorCount` | **44,121 / 3,280 / 44,121 / 44,121** |
| 25 sidecar 聚合 sha（定义：按索引顺序 `name\t sha \n` 串联） | `eac4eff78c24cf27b921183fc58608347550d0d858a67a60997ada265d641bcb`（基线） |
| 序列化格式 | `json.dumps(ensure_ascii=False, indent=1)`、**无尾换行**；对 v2-02/v2-18/主包三件复序列化**逐字节相同**（本会话实跑） |

### 0.4 与 R3 / 批次32B 的关系

- 本臂 = **R3 §3.2 的 A1**：只回填 127 条摘要，标题/去重/别名不动；**预期 129 题指标逐位不变**；若变化，说明指标读到了未文档化的通路（R3:274，强证据）。
- 批次32B 把摘要+标题+去重+sourceUri **合在一起**写成候选；R3 §0.1 已证明「合并效应 119→113」不能归因到摘要（摘要对 129 题无通路）。本臂把摘要单独拿出来，先验证 **零假设**，再谈其它三件。
- 批次32B 的 127→0 是在 **v32 候选（64,839 材料）** 上读的（`docs/kb-batch32b-2026-11-02.md:91`）；本臂在 **v26 发布包（65,021）** 上复算仍是 **127→0**（§0.3），因为 127 条目标全在发布包内且当前全为退化。

---

## 1. 变更范围（冻结）

### 1.1 唯一变更：127 条 `summaryMarkdown`

- 对 25 个 2025 sidecar 中 **slug 命中 `summary-new.json` 的 127 条材料**，把 `summaryMarkdown` 替换为新值。
- 写入前逐条断言：包内旧值 == CSV 的 `old_summary`（逐字节）；不等即 `INPUT_DRIFT`，停。
- 写入后逐条断言：包内新值 == `summary-new.json` 的值（逐字节）。
- 序列化保持现状：`json.dumps(ensure_ascii=False, indent=1)`、无尾换行（本会话已验证三件逐字节相同），使 diff 只落在被改的摘要行。

### 1.2 不动（逐项，全部禁止）

| 类别 | 具体 |
|---|---|
| 标题 | **不应用** `title-shortforms-new.csv` 的 153 条；`materials[].title` 逐字节不变 |
| 去重 | 不执行任何退休/合并；`materials[]` 条数 65,021 不变；manifest `retired` 1,222 不变 |
| sourceUri | 不改 `sources[]` 与材料的来源字段 |
| 正文/边界/适用 | `contentMarkdown` / `boundaryMarkdown` / `applicabilityMarkdown` 不变 |
| 绑定/关系/节点 | `bindings[]`、`knowledge_node` 侧的任何字段（canonicalName / aliases / boundary）不变 |
| 其它字段 | `material_id`（由 `stableCode` 派生）、`slug`、`subject`、`type`、`derivationKind`、`contentAttribute`、`contentId` 等全部不变 |
| 资产 | 主包、2020 包/侧车、sidecar 索引、dense `.vec`/`.vec.json`、金标、代码 |
| 其它 19 个 sidecar | 零改（只有 §0.2 的 6 个文件含目标 slug） |

### 1.3 应用方式（冻结）

1. **不要运行** `build/agent-batch32b/rewrite/apply_rewrites.py`：它同时应用 `title-shortforms-new.csv`（标题），违反 §1.2。只读 `summary-new.json`。
2. 应用脚本（新增，§5.2）先在**备份**上跑一遍，输出语义 diff 报告；再写 release 目录。
3. 写 manifest：只改 `version`（26→27）与 `contentVersion`（`ae939632c8afc4db`，写后复核）；`schemaVersion` / `packId` / `retired` 逐字节不变。
4. 不重建 dense、不重跑 dedupe/title/sourceUri、不改 `moe-2025-teaching-support-v2-index.json`。

### 1.4 每一项变更消灭的具体失败（逐个指认）

| 变更 | 消灭的**具体**失败 | 证据/先例 |
|---|---|---|
| 127 条摘要改写 | 全包 127 条退化摘要（≤8 字或复读标题）——学生看到的「摘要」与标题同义，等于没有摘要（F21 的定义，`docs/kb-batch32b-2026-11-02.md:295`） | §0.3 的 127→0 |
| 只改 summary、标题/去重不动 | 把摘要改造与标题/去重混在一起，导致 119→113 无法归因（R3 §0.1） | R3:274 的 A1 |
| 输入旧值逐条断言 | 对错包/错版本应用新摘要，悄悄覆盖他人改动 | §1.1 |
| `contentVersion` 刷新 | 内容变了但戳不变 → 安装器快路径命中、设备永远拿不到新摘要 | `BundledKnowledgeBaseInstaller.kt:44-55` |
| dense 不动 | 摘要进入向量会改变 44,121 条向量 → 归因被污染，且需重打 26 MB 资产 | R3:274 的「零差异」前提 |

---

## 2. 包身份三方一致（冻结）

### 2.1 三方定义

| 身份 | 组成 | 本臂后的期望 |
|---|---|---|
| **A 内容身份** | `moe-2025-update-manifest.json` 的 `(packId, version, contentVersion)` | `moe-2025-four-subjects-v1` / **27** / **`ae939632c8afc4db`** |
| **B 主包身份** | `moe-2025-four-subjects-v1.json` 的 sha256 | **不变**：`96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| **C dense 身份** | `.vec.json` 的 `(packSha256, corpus.vectorCount, format.count)` + `.vec` sha256 + id 块 sha256 | `packSha256 == B`；`44,121 == 44,121`；`.vec` `70329115…`；id 块 `8f177832…` |

### 2.2 判据（写死）

1. **A 自洽**：写后 `contentVersion` == 按 `tools/kb_build/update_manifest.py:256-266` 对**写后** `(pack, sidecars)` 的复算值；且等于 §0.3 的预测 `ae939632c8afc4db`。不等 ⇒ `INPUT_DRIFT` / `SERIALIZATION_DRIFT`，停。
2. **B == C**：`dense .vec.json.packSha256` == 主包 sha（B），且主包 sha 与写前逐字相同——摘要不进主包，主包必须零改。
3. **C 计数自洽**：`corpus.vectorCount` == `format.count` == `.vec` id 块行数 == **44,121**；唯一节点 id 数 == **3,280**。
4. **副本一致**：release 的 `core/data/src/main/resources/knowledge/` 30 个文件与构建 staging 逐字节一致（30/30，diff none）；若不使用 staging，则 release 目录即唯一副本，跳过本条但登记。
5. 25 sidecar 聚合 sha 按 §0.3 的定义在写后重算并登记（基线 `eac4eff7…`；写后值必须**不同**——内容确实变了）。

### 2.3 为什么是这三方

内容戳回答「设备该不该重导」；主包回答「节点/别名表面有没有动」；dense 回答「向量资产是否仍绑定同一主包」。摘要-only 臂必须让 **A 变、B 不变、C 仍绑定 B**；任何一方不满足，就说明改到了不该改的层（R3 §3.1 的身份冻结）。

---

## 3. 判读（写死）

### 3.1 判据一：特征集**逐字节不变**（预期零差异）

三层，全部要求**相等**，不是"不劣"：

1. **表面输入层（本会话已实算基线）**：包内 3,705 个节点（3,280 原子 + 425 主题）的 `(kind, slug, name, sorted aliases, boundary)` 聚合 sha **必须仍是** `c36840819a737891c42731b54fb08a52aa1289f42d6c8fad42baf4a078045524`（写后重算）。该 sha 把 `kind/slug` 也纳入身份（比抽取器实际读取的字段更严），所以它能同时抓住「改名/换 id」这类表面变化。
2. **抽取器层（执行轮，JVM）**：对写前/写后两份包，用**真实** `KnowledgeSearchFeatureExtractor.fromNode`（`KnowledgeSearchFeatureExtractor.kt:44-57`，输入 = `canonicalName + aliases.sorted() + boundaryMarkdown`）逐节点抽特征；把 `(node_id, feature)` 按 node_id、feature 排序后求 sha256。**两份相等**。期望差异 = 0；非 0 ⇒ 抽取器读到了材料字段（未文档化通路）。
3. **金标装置层（执行轮，JVM）**：同一份金标跑 `GoldenRetrievalJvmTest`；`build/golden-jvm-metrics.txt` 写前/写后**逐字节相同**（该文件不含墙钟，只有 Recall/MRR/逐章/MISS/逐题 hit/rank/top3，`RetrievalBenchmark.kt:620-648`）；`build/production-lexical-leg.tsv` 亦**逐字节相同**。非 0 ⇒ 129 题读到了摘要（与 R3 §0.1 的代码事实矛盾，必须停）。

### 3.2 判据二：dense 向量 id 集**逐字节不变**（预期零差异）

| 检查 | 期望 |
|---|---|
| `bge-small-zh-int8.vec` sha256 | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5`（不变） |
| id 块 sha256（header 24B 之后的 `u32 len + utf8` 序列，3,320,586 B） | `8f1778326060c1dd2e07597adb6e8d8209080d7684cc9b430ea10ced1a16864e`（不变） |
| `.vec.json` sha256 | `6612a28d9dd71e140e91f4300c142f8ca8dae027ca58afde88e0f7d3f749dc4b`（不变） |
| `corpus.vectorCount` / `format.count` / id 行数 | `44,121` / `44,121` / `44,121` |
| `packSha256` | `96246c78…`（== 主包） |

### 3.3 判据三：交付指标（摘要退化 = 0）

| 指标 | 写前 | 写后 |
|---|---:|---:|
| 退化摘要（§0.3 定义）/ 全包 | **127 / 65,021** | **必须 = 0 / 65,021** |
| 空摘要 | 0 | **必须 = 0** |
| 127 条目标落盘 | — | **127 / 127**，逐 slug 与 `summary-new.json` 逐字节相同 |
| 非目标材料的 summary | — | 逐字节不变 |

### 3.4 判据四：范围守卫（只改 127 个摘要字段）

对写前（`git show HEAD:<path>`）与写后逐 sidecar 做**语义 deep-diff**：

1. 差异只允许出现在 `materials[i].summaryMarkdown`，且恰好 **127** 处（6 个文件：v2-02/03/04/05/06/18）；
2. 每个差异的 `(slug, old, new)` 必须与 CSV 的 `(slug, old_summary, new_summary)` 逐字节匹配；
3. 其它所有字段 deep-equal；`materials[]` 条数不变；无新增/删除材料；
4. manifest 只有 `version` 与 `contentVersion` 两个字段变化；
5. 其余 19 个 sidecar、index、主包、2020 两件、dense 两件**逐字节不变**。

### 3.5 判据五：包身份三方一致

按 §2.2 全过。

### 3.6 零差异的归因含义（写死，防误读）

- 判据一/二/五任一出现**非零差异** ⇒ 本臂**不采纳**，并且**不得**把差异解释为「摘要确实影响了检索」。结论只能是：**装置或管线存在未文档化的通路**（R3:274），先停、先定位。
- 若全部为零差异 ⇒ 本臂证明「摘要-only 对 129 题无影响」，因此任何把 v28 的 119→113 归因于摘要改写的说法**被证伪**；后续只允许把差异归因到标题/去重/别名表（R3 §3.2 的 A2–A4）。
- **不得**因为"预期零差异"就跳过任何一项；零差异本身就是本臂的**产出**。

### 3.7 旁证（不参与判读，但采纳前必须做一次设备冷装）

- 装新包后 `content_install_state.content_version` == `ae939632c8afc4db`；
- `install finished in` **≤60,000 ms**（F6 门；本臂只改 127 条材料，仍要全量解析 26 卷 ≈ 20 s）；
- 日志中**不得**出现 `rebuild subject=`——**仅限已有库的更新安装**；全新安装本来就会逐科重建（既有行为，与本臂无关）。本臂无节点增改，更新安装若出现重建即 §1.2 被破坏；
- 设备金标逐题 `hit/rank` 与 MRR 与写前**逐题相同**（只比位图/名次，不比墙钟）。

### 3.8 装置门

| # | 命令 | 通过条件 |
|---:|---|---|
| 1 | `python -I tools/kb_build/verify_summary_only.py --release core/data/src/main/resources/knowledge --before <backup-dir>` | **exit 0**，且打印 §2/§3 的全部读数 |
| 2 | `./gradlew :app:assembleDebug --console=plain` | **exit 0**（资源打进 APK 的编译/打包门） |

- 门 1 是本臂的**主证据**；门 2 只证明包仍可打包，不证明内容正确。
- 任一门红 ⇒ 不跑设备、不采纳。

### 3.9 报告义务（§7 至少包含）

- 写前/写后 manifest `(version, contentVersion)`、6 个 sidecar 的 sha、25 sidecar 聚合 sha。
- §2.2 三方读数与 release==staging 结果。
- §3.1 的三层 sha（表面输入 / 抽取器行集 / 两个 JVM 产物逐字节 diff）。
- §3.2 的 `.vec` / id 块 / `.vec.json` sha 与计数。
- §3.3 的退化/空/落盘计数；§3.4 的语义 diff 明细（6 文件 × 127 行）。
- 设备旁证（§3.7）：contentVersion、install 毫秒、`rebuild subject=` 是否存在、逐题位图。
- 未跑项一律 `NOT_RUN` / `UNVERIFIED`。

---

## 4. 明确不做 / 未验证（写作时点）

- **未改任何内容包/清单**：本文件只冻结规则；§0.3 的 `ae939632c8afc4db` 是预测值，不是已写值。
- **未跑 Gradle/设备/JVM**：§3.1 的抽取器层与金标层、§3.7 的设备旁证均 `NOT_RUN`。
- **抽取器层 sha 未预算**：只算了**输入面** sha（`c3684081…`）；特征行集 sha 必须由执行轮用真 Kotlin 抽取器算（Python 近似不能当证据）。
- **批量32B 的 127→0 是 v32 候选读数**：本臂在 v26 上复算同为 127→0，但两套材料的 slug 集合是否完全一致未逐条比对（只验证了 127 个目标全在 v26 且全退化）。
- **`version` 26→27 未由发布脚本执行**：只冻结目标值；`promote.py` 的完整流程（门、版本台账）不在本臂范围内。
- **release==staging 未验证**：本会话直接读 `core/data/src/main/resources/knowledge/`；staging 目录是否存在、是否一致，执行轮按 §2.2-4 检查。
- **设备导入不重建索引是推断**：基于「无节点 upsert ⇒ 不调用 `rebuildSubject`」（`RoomKnowledgeContentReconciler.kt:98-104`），未在设备上验证。

---

## 5. 回滚（触碰清单 + pre/post sha）

### 5.1 A. 内容文件（写前干净；pre-sha 本会话实算）

| # | 路径 | 预期动作 | pre-sha256（写前） | post-sha256 |
|---:|---|---|---|---|
| 1 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-02.json` | 16 条 summary | `ba443288dca7e10e598a26934516348b47055eb017f4fd9bcda1f7d9f6ec56e1` | §7 |
| 2 | `.../moe-2025-teaching-support-v2-03.json` | 35 条 summary | `23e08d3a63c8ab4a17216cb1ea3e856a01c33a95f15b05eb02ecfa88d540a7ad` | §7 |
| 3 | `.../moe-2025-teaching-support-v2-04.json` | 26 条 summary | `fa7378a77683f684e62075e630315d74c46ff06dda77151e6408ee92432adb71` | §7 |
| 4 | `.../moe-2025-teaching-support-v2-05.json` | 45 条 summary | `5d2fc2623a5efc0f8849fc99d3cea967c813263e9ee879a54f6dbf776fe96fb8` | §7 |
| 5 | `.../moe-2025-teaching-support-v2-06.json` | 3 条 summary | `83169541da5228889bd418093adf2e078a86acd77c543e359dbb00cf4220860e` | §7 |
| 6 | `.../moe-2025-teaching-support-v2-18.json` | 2 条 summary | `8ae8b5183dd083f4a047b0abe96f1b72bfb73852c63eb3f00ddf84b0adfcc897` | §7 |
| 7 | `.../moe-2025-update-manifest.json` | `version` 26→27；`contentVersion` → 复算值 | `370b0979ec011806258439877d837a33e6f0ea3dcd7db502b201de40d8853608` | §7 |

### 5.2 B. 新增文件（写前不存在）

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `tools/kb_build/verify_summary_only.py` | 应用 + 复核：输入 sha 守卫、旧值断言、摘要写入、§2/§3 全量检查、机器可读报告 |

- 该脚本只读 `summary-new.json`，**不得**读 title CSV / dedupe 表 / sourceUri 表。
- 脚本必须支持 `--before <dir>`：对备份目录做语义 diff，证明只有 127 个摘要变化。

### 5.3 C. 零触碰（登记供核验，不得改）

| # | 路径 | 期望 | pre-sha256 |
|---:|---|---|---|
| 1 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | 零改（主包） | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| 2 | `.../moe-2025-teaching-support-v2-index.json` | 零改（卷序/卷数） | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 3 | `.../moe-2020-foundation-v1.json` | 零改 | `9ceca92787d6819e4b6a0bb927aa8da555f748696a5630168649f719daba05e1` |
| 4 | `.../moe-2020-teaching-support-v1.json` | 零改 | `5aea4e60867c50cd780f249b6d0f46855d6ceebf7a54a971ecf321f849041006` |
| 5 | `.../dense/bge-small-zh-int8.vec` | 零改 | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 6 | `.../dense/bge-small-zh-int8.vec.json` | 零改 | `6612a28d9dd71e140e91f4300c142f8ca8dae027ca58afde88e0f7d3f749dc4b` |
| 7 | 其余 19 个 2025 sidecar | 零改 | 见 §7 的备份 `sha256sum` 全表 |
| 8 | `core/database/.../KnowledgeSearchFeatureExtractor.kt` | 零改（抽取规则） | `b65a6e9988a59e7b5c7b12822b9f66c42965909f136c2460e393595e8bac699c` |
| 9 | `tools/kb_build/update_manifest.py` | 只读调用 `content_version` | `07a00861c196f0c6f78b0ee47bd64c6d9660724a10af48cabe6c1d8bc9ad68f0` |
| 10 | `tools/dense_build/dense_asset.py` | 零改/不执行 | `2dafbfa994a3c664ec7f021e522daa138ba797c02c024c68b078a874d901ae46` |
| 11 | `tools/kb_coverage/tables/golden_queries_v2.json` | 零改 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |
| 12 | `build/agent-batch32b/rewrite/summary-new.json` / `summary-rewrites-new.csv` | **只读**（sha 钉死，§0.2） | `ea8a8134…` / `eac2bde8…` |

### 5.4 写前备份 / 回滚 / 采纳

**写前备份（执行轮第 1 步之前）**

1. 把 §5.1 的 7 件 + 其余 19 个 sidecar 复制到 `build/agent-batch33/b33-b1-summary/backup/`，落 `sha256sum` 全表；再复制一份为 `before/` 供 §5.2 的语义 diff。
2. `git status --porcelain -- <§5.1 路径>` 必须为空；若非空先归因他线并停止。
3. 复核身份：manifest `version=26` / `contentVersion=aea886e42b36dcb2`；主包 `96246c78…`；dense `70329115…`；金标 `eabef102…`；输入两个 sha 与 §0.2 一致。

**回滚（任一判据失败、范围守卫失败、三方不一致）**

1. **不得**整树 `git checkout`；按 §5.1 逐文件恢复，删除 §5.2 的脚本（若它已入库则回滚提交）。
2. 设备侧：内容戳从 `ae939632…` 回到 `aea886e4…`，下次启动会再做一次全量调和（≈20 s，无数据丢失）；**不需要** `pm clear`、**不需要** DB 迁移。
3. 核验：25 sidecar 聚合 sha 回到 `eac4eff7…`；主包/dense/金标三件 sha 与 §5.3 一致；manifest 回到 `370b0979…`。
4. 复跑 §3.8 门 1（以备份为 before，恢复到写前应报 127 条退化、内容戳 `aea886e4…`）。

**采纳**

保留 §5.1 的 7 件写面与 §5.2 的脚本；§7 登记全部读数与逐文件 post-sha。**不**改 dense、**不**改标题/去重/sourceUri、**不**把本臂与 A2–A5 合并。

---

## 6. 执行轮追加位（只追加，不改 §1–§5）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；备份与回滚核验；§5.1 逐文件写后 sha；§2.2 三方读数（`version`/`contentVersion` 复算、主包 sha、dense packSha256/vectorCount/id 块 sha）；release==staging 结果；§3.1 三层 sha 与两个 JVM 产物的逐字节 diff；§3.2 的 `.vec`/id 块/`.vec.json` sha；§3.3 的 127→0、空摘要 0、127/127 落盘；§3.4 的语义 diff 明细（6 文件 × 127 行）；§3.7 设备旁证（contentVersion、install 毫秒、`rebuild subject=` 有无、逐题位图）；门 1/门 2 的 exit code。未跑项一律 `NOT_RUN` / `UNVERIFIED`。

---

## 7. 执行轮记录（2026-11-03 07:0x，单次执行；§1–§5 未改）

### 7.1 写前候选状态

| 项 | 值 |
|---|---|
| HEAD / origin/main | `faab17f58b01eab78d5c08a9f843b3af3df2fecf` / 同值 |
| porcelain 行数 | 558（他线 557 + 本轮 tools 新文件） |
| `git status --porcelain -- core/data/src/main/resources/knowledge/` | 空 |
| staging vs release（30 个 JSON） | 30/30 逐字节相同（写窗口前为干净镜像） |
| §0.2 全部输入 sha | 一致（`ea8a8134…` / `eac2bde8…` / 主包 `96246c78…` / `.vec` `70329115…` / `.vec.json` `6612a28d…` / 台账 `370b0979…` / 金标 `eabef102…`） |

备份：30 个 JSON → `build/agent-batch33/b33-b1-summary/backup/`，sha 全表 `backup-sha256.txt`；另留 `before/` 供语义 diff。
另：`staging_freeze.py --snapshot`（写前）→ 写后 `--compare`：`changed=7, added=0, removed=0`（无并发写入）。

### 7.2 应用与晋升

- `python -I tools/kb_build/verify_summary_only.py --apply --target build/kb-staging`
  → `total=127`（v2-02×16 / v2-03×35 / v2-04×26 / v2-05×45 / v2-06×3 / v2-18×2），exit 0。
  应用前逐条断言包内旧值 == CSV `old_summary`（0 处不符）；写后逐条 == `summary-new.json`（127/127）。
- `PYTHONPATH=tools python -m kb_build.promote` → exit 0，`gates / consistency / roundtrip` 全 OK，
  `version→27`，`内容戳 → ae939632c8afc4db`，`主包 sha256 → 96246c78…（staging == release 已对拍）`，
  已晋升 28 个文件（6 sidecar + 其余 22）。日志 `build/agent-batch33/b33-b1-summary/promote.log`。

### 7.3 §5.1 写后 sha

| # | 文件 | post-sha256 |
|---:|---|---|
| 1 | v2-02 | `0161dc3848b00eb3c03424b702917748ed4ddcae91b532042275c3b90fb1a7ae` |
| 2 | v2-03 | `9ad65370ee30603089b2c36c7d35d6f33806c2c8602f86abb3ea230a43416d5b` |
| 3 | v2-04 | `e8f2364fba089a553e00d0b0f44d2a039882278448bab494ddb996f927273159` |
| 4 | v2-05 | `69687001f3dc7460d2728d2e8022322d10c3d9132a031d72d7c3cf36b713de3e` |
| 5 | v2-06 | `f86061a49460f8de0fcba7f4aa845f6de4d85718ce6a20f1f0af28a918210d01` |
| 6 | v2-18 | `f506b9deff1e6dac3342bc0e70d20218ee23ac44607ab077432c9ff00e2c4dec` |
| 7 | manifest | `4bfbf0642372d300d1a1875ce643966bd97405836b224608fbf95c41026d3c6c` |

### 7.4 §2.2 三方一致

| 身份 | 读数 |
|---|---|
| A | `(moe-2025-four-subjects-v1, 27, ae939632c8afc4db)`；复算 == 落账 == **预测值** |
| B | 主包 `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f`（写前写后逐字节相同） |
| C | `.vec.json.packSha256` == B；`vectorCount == format.count == id 行数 == 44,121`；唯一节点 `3,280`；分科 856/891/1,019/514 |
| 副本 | release 30 个 JSON == staging 30/30（`mismatch=[]`） |
| sidecar 聚合 sha | `eac4eff7…` → `ec31463b1f0e4d284e33d386efd7382eece2a3adc074001e681b7f2469e9d840`（已变，符合预期） |

### 7.5 §3.1 三层

1. **表面输入层**：主包**逐字节**不变（`96246c78…`）⇒ 3,705 节点的 `kind/slug/name/sorted aliases/boundary` 全部未变。
   预注册的 `c3684081…` 聚合定义**未落盘**，本会话试了 100+ 种序列化（TSV/JSON、tab/pipe/US/NUL、排序/原序、五/六字段）均不能复现
   ⇒ 该具体值记 `UNVERIFIED`；替代证据（主包字节级不变）严格更强，故本层判通过。
2. **抽取器层（JVM）**：`NOT_RUN`（须真 Kotlin 抽取器逐节点出特征 sha；Python 近似不作证据）。
3. **金标层（JVM）**：`./gradlew :core:data:testDebugUnitTest --tests '*GoldenRetrievalJvmTest*' --tests '*ProductionLexicalLegExportTest*'`
   → `GoldenRetrievalJvmTest` **2/2 通过**；`build/golden-jvm-metrics.txt` 与写前（05:51 副本）**逐字节相同**
   （`5126f18740c3466ab9f5069211b6b7d6efe25a2173291bd4947c45a2ba3673e3`）；
   `build/production-lexical-leg.tsv` 同样**逐字节相同**（`690402f8…`，39,468 数据行，与 `.vec.json` 记录值一致）。
   `ProductionLexicalLegExportTest` 有 1 条断言红：期望 `0.6130490956072351`、实测 `0.6178294573643413`。
   实测值 == **HEAD（32B 之前）的 pin**（`git show HEAD:…/ProductionLexicalLegExportTest.kt:236,381`），
   而工作区该文件已被他线改成 32B/v32 的 pin（该文件在写前 porcelain 中即为 `M`）。
   即：**红是预存的 pin 漂移（他线改测试未随包晋升），不是本臂引起**；本臂产出的是 v27（无 title 153 / 无去重），
   实测回到 0.6178… 正是"摘要不进词面腿"的正面证据。该文件**未改动**。

### 7.6 §3.2 dense / §3.3 交付 / §3.4 范围

- dense：`.vec` `70329115…`、id 块 `8f177832…`（3,320,586 B）、`.vec.json` `6612a28d…` —— 三者均不变。
- 交付：材料 **65,021**（不变）；退化摘要 **127 → 0**；空摘要 **0**；目标落盘 **127/127**。
- 范围：`git diff --stat` 7 文件 / `129 insertions, 129 deletions` = 127 条摘要行 + 2 条台账行；
  语义深 diff（`--before`）**violations = 0**，`summary_diff_per_file` = 16/35/26/45/3/2，`(slug,old,new)` 与 CSV 逐字节一致；台账只 `.version` / `.contentVersion` 两键变。

### 7.7 门

| 门 | 命令 | 结果 |
|---|---|---|
| 1 | `python -I tools/kb_build/verify_summary_only.py --release … --before … --staging … --report …` | **exit 0**（28 checks，2 项 NOT_RUN = 抽取器层/金标层 JVM 明细） |
| 2 | `./gradlew :app:assembleDebug --console=plain` | **exit 0**（BUILD SUCCESSFUL in 1m 29s） |

### 7.8 未跑 / 未验证

- §3.7 设备冷装旁证（`content_install_state.content_version`、install ms、`rebuild subject=`、逐题位图）：**NOT_RUN**
- §3.1 层 2（真 Kotlin 抽取器特征 sha）：**NOT_RUN**
- 预注册 `c3684081…`：**UNVERIFIED**（定义缺失，替代证据见 7.5-1）

证据目录：`build/agent-batch33/b33-b1-summary/`（`report.json` / `gate1.log` / `gate2-assemble.log` / `gate-jvm.log` / `scope-diff-127.tsv` / `post/`）；触碰清单：该目录 `touched-files.txt`。

