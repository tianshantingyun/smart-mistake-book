# KB B33 B4 来源 URI 三分类与契约门：预注册（2026-11-02）

> **性质**：出数前冻结。§1–§4 写死；执行轮只允许在文末追加 §6，不得回改 §1–§4。
> **写作时点**：2026-11-03T08:48:41+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-02` 是批次日。
> **HEAD**：`faab17f58b01eab78d5c08a9f843b3af3df2fecf`（`git rev-parse` 实跑）。**注意：B3 去重已在会话内落盘**（manifest 27 / `2cd974dbada7cb2f` / 材料 64,853），本文件以**该状态**为写前基线。
> **上游**：批次32B §4（49 源 / 206 行 / A1·B4·C44 的分类与 `fix_sourceuri.py`）；R3 §1.4/§2.2；`tools/audit-knowledge-source-register.ps1`（F24）；`core/database/.../KnowledgeBaseImportContract.kt`（契约实现）。
> **本会话收到的裁定（2026-11-03）**：占位域检查**全量严格**、URI 必填**只对 LICENSED**；PUBLIC_OFFICIAL / REFERENCE_ONLY 允许 `sourceUri=null` 但必须带可核实的引用三元组；主包顶层先查真实 URI，没有则置 null；**主包侧占位残留读数按 0 写**；F24 不承诺转绿。
> **本步唯一新增写盘**：本文件。未改内容包/代码、未跑 Gradle/设备/JVM；§0.3 的读数是本会话用 `python -I` 实读实算。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-03T08:48:41+0800
git rev-parse HEAD                             # faab17f58b01eab78d5c08a9f843b3af3df2fecf
git status --porcelain | wc -l                 # 574
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   442 ??
#    14  D      ← 他线 core/data/.../pdf/*
#   118  M     ← 含 B3 落盘后的 11 sidecar + manifest + judgments
git diff --cached --name-only | wc -l          # 0
ls docs/research/2026-11-02-kb-b33-b4-sourceuri-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- **写前基线已是 B3 之后**：manifest `version=27` / `contentVersion=2cd974dbada7cb2f` / 材料 64,853。B4 的 pre-sha 都是 08:48 时点的工作树值。
- 本臂触碰的代码/主包/dense 写前**干净**（`git status --porcelain -- <§4.1/§4.2>` 为空）；25 个 2025 sidecar 中 11 个带 B3 的 `M`，本臂在其现有内容之上编辑。
- 574 项脏项属他线与已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`。

### 0.2 现状（实读 / 实算）

| 事实 | 值 | 来源 |
|---|---:|---|
| 源行 / 唯一 `sourceId` | **206 / 49** | 25 个 2025 sidecar（205 行 / 48 id）+ 1 个 2020 sidecar（1 行） |
| 当前 licenseStatus | **206 行全部 `REFERENCE_ONLY`** | 实读 |
| 当前 sourceType | **206 行全部 `AUTHORIZED_EDUCATION_MATERIAL`** | 实读 |
| 当前 `sourceUri` | **206 行全部 `https://www.example.edu/material`**（占位域） | 实读 |
| 2025 主包顶层 `sourceUri` | `https://example.edu/curriculum/2025-four-subjects`（占位域） | `moe-2025-four-subjects-v1.json` |
| 2020 主包顶层 `sourceUri` | `https://www.moe.gov.cn/srcsite/A26/s8001/202006/W020200603315372317586.zip`（真实） | `moe-2020-foundation-v1.json` |
| 2025 包 baseline | `moe-high-school-2017-2025`（「日常修订版（2017年版2025年修订）」） | `coverage.baselineId`；`standardEdition()` `:591-595` |
| 三元组字段完整性 | `publisher` / `title` / `edition` **206/206 非空** | 本会话逐行实算 |
| 主包合成源 | 主包顶层 `sourceUri` 经 `toSource` 变成 **4 条** `PUBLIC_OFFICIAL` 合成源（每科一条） | `BundledKnowledgePackResources.kt:230`、`:239-241`、`:409-425` |

### 0.3 裁定的落地读数（本会话实算）

| 项 | 值 |
|---|---|
| 分类 | A=**1** 源（1 行）、B=**4** 源（4 行）、C=**44** 源（201 行） |
| 2025 sidecar 受影响文件 | **25/25**（205 行全部改写） |
| 2020 sidecar | 1 行（A 类） |
| 主包顶层 | `sourceUri` → **`null`**（见 §1.3 的理由） |
| 主包合成源 | 4 条 → `sourceUri=null` + 三元组（publisher/edition 由 loader 现给） |
| 占位域出现数（写前 → 写后） | **静态 207 → 0**（205 个 2025 sidecar 行 + 1 个 2020 sidecar 行 + 1 个主包顶层）；**运行时合成源另 4 → 0**；合计 **211 → 0** |
| 新主包 sha | **`10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670`** |
| 新 `contentVersion` | **`ce8edb8f89d91fe7`** |
| 新 `.vec.json` sha（只改 `packSha256`） | `6c1c448f2428091c5b91ed8888aa4c1ef90539201f47ef82db4682c2f02a07fc` |
| `.vec` / `vectorCount` | `70329115…` / **44,121**（不变） |
| manifest | `version` 27→**28** |

### 0.4 与批次32B / R3 的关系

- 批次32B §4 已给出 A/B/C 的分类与字段去向；本臂把它**落到当前发布包**（v26 内容 + B3 去重，manifest **27** / `2cd974dbada7cb2f` / 材料 64,853），并补上批次32B 未做的两件事：**契约语义**（占位域/三元组）与 **主包顶层**（批次32B 只改了 sidecar + manifest，主包顶层仍是 `example.edu`）。
- 批次32B 的 B/C 用 `sourceUri=null`，但当前契约对非 LICENSED 源**要求 URI**（`KnowledgeBaseImportContract.kt:255-260`），且枚举里没有 `UNVERIFIED_PLACEHOLDER`——所以批次32B 的包**过不了当前契约**。本臂必须同时改契约与枚举，这是本次的核心。

---

## 1. 三分类与内容变更（冻结）

### 1.1 A / B / C 的逐类定义

| 类 | 判定 | 源数 | 行数 | `sourceUri` | `sourceType` | `licenseStatus` | attribution |
|---|---|---:|---:|---|---|---|---|
| **A** 真实可核 URI | `sourceId == source:moe-2020:math:teaching` | **1** | **1** | `https://www.moe.gov.cn/srcsite/A26/s8001/202006/W020200603315372317586.zip` | `OFFICIAL_CURRICULUM_STANDARD` | `PUBLIC_OFFICIAL` | 不变 |
| **B** 来源不明（占位） | `sourceId` 以 `registry-gap-gaokao-2025:` 开头 | **4** | **4** | **`null`** | `UNVERIFIED_PLACEHOLDER` | `UNVERIFIED_PLACEHOLDER` | 写明「来源不明（占位）……仅参考、独立重写」 |
| **C** 可定位但无公开可核 URI | 其余 | **44** | **201** | **`null`** | `UNVERIFIED_PLACEHOLDER` | `REFERENCE_ONLY` | 追加「仅参考、独立重写，不保存原文」 |

- 分科/来源身份来自 `build/agent-batch32b/scripts/fix_sourceuri.py` 的 `classify()`（`A_SOURCE_ID` / `B_PREFIX`），**逐条可核**；不接受“按 title 猜”。
- C 的 44 源含 17 `desktop-src-*`、4 `registry-wusan-*`、4 `registry-lixiangshu-*`、17 `registry-kb-*` 等（批次32B §4.1 的口径）。
- **不改**：`title` / `publisher` / `edition` / `contentFingerprint` / `importedAtEpochMillis` / `contentUsePolicy` / `modelUsePolicy`。

### 1.2 引用三元组（冻结）

- 三元组 = **`issuer` + `title` + `yearOrEdition`**，在本仓字段上的映射写死为：
  - `issuer` = `publisher`
  - `title` = `title`
  - `yearOrEdition` = `edition`
- 三者**全部非空**（trim 后）才算“可核实”；任一为空 ⇒ 契约拒收（§2.3）。
- 本会话实算：206/206 行的 `publisher`/`title`/`edition` 均非空——所以 B/C 的 null-URI 路径在本包上**可满足**，不需要编造任何字段。
- **禁止**为了过门而新造 URI 或版次；三元组只允许取现有记录值。

### 1.3 主包顶层 `sourceUri`：置 `null`

- **先查真实 URI**：仓内 `knowledge-production/source-register-2025-v1.json` 的 `registry:moe:2020:high-school-standards-archive.documentUri` 是 **2017 年版 2020 年修订** 的官方附件；本 2025 包的 `baselineId=moe-high-school-2017-2025`，版次是「日常修订版（2017年版2025年修订）」——**不是同一版次**。按裁定，不得据此推断 2025 版次，**不复用**该 URI。
- 因此主包顶层 `sourceUri` 置 **`null`**；主包侧占位残留 = **0**。
- **loader 必须允许 null**：`BundledKnowledgePackResources.kt:230` 现在是 `root.requiredString("sourceUri")`，需改成“键必须存在、值可为 null”，并让 `toSource(sourceUri: String?)` 接受 null。
- 合成源（每科一条，`sourceType=OFFICIAL_CURRICULUM_STANDARD`、`licenseStatus=PUBLIC_OFFICIAL`）在 `sourceUri=null` 时靠三元组过门：`publisher=中华人民共和国教育部`、`title=普通高中X课程标准（…）`、`edition=日常修订版（2017年版2025年修订）`——三者均由 loader 现有代码给出（`BundledKnowledgePackResources.kt:414-425`、`:591-595`），**不新增字段、不编造**。

### 1.4 明确不改（一条都不许动）

1. 节点/别名/边界/关系/材料（B3 的 168 条退休不碰）；金标；alias_map；anchors。
2. `.vec` 向量字节（`70329115…`）与 `vectorCount`（44,121）；只改 `.vec.json` 的 `packSha256`（§3.2）。
3. `knowledge-production/source-register-2025-v1.json`（F24 的输入，本臂不碰）。
4. F24 的生产门判据与九科证据要求。

---

## 2. 契约门（冻结；按裁定作为**契约语义**实现，不是豁免）

### 2.1 占位域检查：对**任何出现的 URI** 生效

- 适用字段：`source.sourceUri`、`source.licenseUri`，以及**主包合成源**经 `toSource` 携带的 URI——一视同仁。
- 占位 host 冻结表（命中即拒收，`DatabaseContractViolationException`）：
  - 精确：`example.com`、`example.net`、`example.org`、`example.edu`、`localhost`、`127.0.0.1`、`0.0.0.0`、`::1`
  - 后缀：`.invalid`、`.test`、`.local`、`.localhost`、`.example`
- 判定：`host` 先 `lowercase(Locale.ROOT)` 去尾点；等于精确项，或以 `"." + suffix` 结尾 ⇒ 拒收。
- **不放松**：即使 `licenseStatus=PUBLIC_OFFICIAL` 或 `UNVERIFIED_PLACEHOLDER`，只要 URI 非 null 且命中占位域，一律拒收。

### 2.2 URI 必填**只对 `LICENSED`**

| licenseStatus | `sourceUri` | 额外要求 |
|---|---|---|
| `LICENSED` | **必须非 null**（且过 §2.1、过 HTTPS/无凭据/无 fragment） | 现有 `licenseExpression` + `licenseUri` 规则不变 |
| `PUBLIC_OFFICIAL` | 可 null | null 时必须有三元组（§2.3）；非 null 时必须过 §2.1 |
| `REFERENCE_ONLY` | 可 null | null 时必须有三元组 + `contentUsePolicy=REVIEWED_SYNTHESIS_ONLY` + attribution 含「仅参考、独立重写」 |
| `UNVERIFIED_PLACEHOLDER` | **必须为 null** | 必须有三元组 + `contentUsePolicy=REVIEWED_SYNTHESIS_ONLY` + attribution 含「来源不明（占位）」 |

- 现有 `validateSourceUri` 的 `uriRequired = licenseStatus != LICENSED` 改为 **`uriRequired = licenseStatus == LICENSED`**；其余分支按上表。
- “空 license”口径：`licenseStatus` 必须非空且在枚举内；`UNVERIFIED_PLACEHOLDER` 是**唯一**允许的显式占位类。直接复用（`contentUsePolicy != REVIEWED_SYNTHESIS_ONLY`）仍要求 `licenseExpression` + 非占位 `licenseUri`（现有规则保留）。

### 2.3 三元组门（null URI 的必要条件）

```kotlin
// 伪代码，写进 validateSource
if (sourceUri == null) {
    requireImport(source.licenseStatus != LICENSED.name) { "Licensed sources need a source URI" }
    text(source.publisher.orEmpty(), "source.publisher", 500)   // issuer 非空
    text(source.edition.orEmpty(), "source.edition", 500)       // yearOrEdition 非空
    // title 已由 validateSource 的 text(source.title, ..., 1_000) 保证非空
}
```

- 三元组任一为空 ⇒ `source.publisher` / `source.edition` 违约，拒收。
- 该门对**主包合成源**同样生效（它们走同一 `validateSource`）。

### 2.4 枚举扩展

- `core/model/.../ProblemCatalog.kt`：
  - `KnowledgeSourceType` 增加 **`UNVERIFIED_PLACEHOLDER`**。
  - `KnowledgeSourceLicenseStatus` 增加 **`UNVERIFIED_PLACEHOLDER`**。
- 契约的 `sourceTypes` / `licenseStatuses` 由 `enumValues<>()` 派生（`KnowledgeBaseImportContract.kt:33`），枚举一加即被接受；**不需要**改 DB schema（列是 TEXT，无 CHECK 约束）。

### 2.5 契约测试（四类负例 + 正例，全部冻结）

| # | 用例 | 期望 |
|---:|---|---|
| N1 | `sourceUri = "https://www.example.edu/material"`（PUBLIC_OFFICIAL 与 REFERENCE_ONLY 各一） | 拒收（占位域） |
| N2 | `licenseStatus=LICENSED` 且 `sourceUri=null` | 拒收（LICENSED 必须 URI） |
| N3 | `licenseStatus=PUBLIC_OFFICIAL`、`sourceUri=null`、`publisher` 或 `edition` 为空 | 拒收（三元组缺失） |
| N4 | `licenseStatus=REFERENCE_ONLY`、`sourceUri=null`、`publisher` 或 `edition` 为空 | 拒收（三元组缺失） |
| N5 | `licenseStatus=UNVERIFIED_PLACEHOLDER` 且 `sourceUri` 非 null | 拒收（占位类必须 null） |
| N6 | 合成源路径：主包顶层 `sourceUri` 为占位域 → loader 产出的 4 条合成源 | 拒收（与 sidecar 同一条规则） |
| P1 | A 类：真实 MOE URI + PUBLIC_OFFICIAL | 通过 |
| P2 | B 类：null + UNVERIFIED_PLACEHOLDER + 三元组 + marker | 通过 |
| P3 | C 类：null + REFERENCE_ONLY + 三元组 + 笔记 | 通过 |
| P4 | 合成源：主包顶层 `sourceUri=null` + loader 生成的三元组 | 通过 |

- 现有 `KnowledgeBaseImportContractTest` 里用 `https://example.edu/...` 的正向夹具（`:108`、`:204` 等）必须改为**非占位 host**（如 `https://www.moe.gov.cn/...`），否则 N1 会把它们全打红；这是测试夹具的机械更新，不改断言口径。
- 仪器化夹具 `KnowledgeContextRetrievalInstrumentedTest.kt:431`、`KnowledgeNodeChangeSignalInstrumentedTest.kt:127` 各 1 处 `https://example.edu/...`：若这些用例走 `applyKnowledgeContentUpdate`（会过契约），host 必须同步替换；执行轮以门结果为准，失败即改夹具。

### 2.6 合成源路径（必须被负例覆盖）

- `BundledKnowledgePackResources.decodeShared`：`sourceUri = root.requiredString("sourceUri")`（`:230`）→ 4 条 `toSource`（`:239-241`）→ `:409-425`。
- 新 loader 行为：顶层键存在、值可为 null；`toSource` 接 `String?`；合成源的 `sourceUri` 原样传入。
- N6 必须走**真实 loader**生成合成源再喂契约（不能只测手写 fixture），否则「主包顶层占位域」这条路径没有被覆盖。

### 2.7 每项新增消灭的具体失败

| 新增 | 消灭的**具体**失败 |
|---|---|
| 占位域拒收 | 包里继续出现 `example.edu` 这类伪造来源，学生/审阅者无法追溯（批次32B 只改了 sidecar，主包顶层与合成源仍是假域名） |
| URI 必填只对 LICENSED | `PUBLIC_OFFICIAL`/`REFERENCE_ONLY` 被无意义地要求一个 URL，逼出「填假域名」或「填一个不相关的镜像」 |
| 三元组门 | 把「来源身份」从 URL 换成可核实的 机构+文件名+版次；缺一即拒收，杜撰 URL 不再是唯一出路 |
| `UNVERIFIED_PLACEHOLDER` 枚举 | 显式占位类在类型上可见（不再用 `REFERENCE_ONLY` 掩盖“来源不明”），且契约能对它施加更严的 null/marker 规则 |
| 主包顶层 null + loader 接受 null | 主包合成源不再携带假域名；`requiredString` 不再强制一个必须伪造的字段 |
| 四类负例 + 合成源负例 | 「改了 sidecar 但主包/合成源仍带占位域」这类漏检（批次32B 的形态） |

---

## 3. 判读（写死）

### 3.1 判据一：特征快照 **diff = 0**

- 本臂只改 `sources[]` 与主包顶层 `sourceUri`；节点/别名/边界/关系/材料一字不动。
- 三层（与 B1/B3 同口径）：
  1. 3,705 节点 `(kind, slug, name, sorted aliases, boundary)` 聚合 sha = 写前 `c36840819a737891c42731b54fb08a52aa1289f42d6c8fad42baf4a078045524`。
  2. 真实 `KnowledgeSearchFeatureExtractor.fromNode` 的 `(node_id, feature)` 排序 sha 写前=写后。
  3. `build/golden-jvm-metrics.txt` 与 `build/production-lexical-leg.tsv` **逐字节相同**。
- 非 0 ⇒ 停：说明来源字段进了特征路径（未文档化）。

### 3.2 判据二：vec-ids **diff = 0**（`.vec` 字节不变；`.vec.json` 只改 `packSha256`）

| 检查 | 写前 | 写后 |
|---|---|---|
| `.vec` sha | `70329115…` | **必须相同** |
| id 块 sha / 行数 / 唯一节点 | `8f177832…` / 44,121 / 3,280 | **必须相同** |
| `vectorCount` / `format.count` | 44,121 / 44,121 | **必须相同** |
| `.vec.json` `packSha256` | `96246c78…` | **`10ede1bf…`（= 新主包 sha）** |
| `.vec.json` 其它字段 | — | 逐字段不变（只 `packSha256` 变） |
| `.vec.json` 文件 sha | `6612a28d…` | `6c1c448f…`（仅因 packSha256 改变） |

- **为什么必须 re-pin**：主包顶层 `sourceUri` 改了 → 主包 sha 变 → dense `.vec.json.packSha256` 与主包不再一致，`tools/dense_build/check_asset.py:71-72` 会红。re-pin 只改一个元数据字段，**不重算向量**（ids/矩阵/scale 全不变）。
- 若 `.vec` 字节或 id 集变化 ⇒ 有人重建了向量，本臂越界。

### 3.3 判据三：占位域残留 = **0**（逐文件计数，如实报告）

| 位置 | 写前 | 写后 |
|---|---:|---:|
| 25 个 2025 sidecar 的 `sources[].sourceUri` | 205 | **0** |
| 2020 sidecar 的 `sources[].sourceUri` | 1 | **0**（A 类真实 URI） |
| 2025 主包顶层 `sourceUri` | 1 | **0**（null） |
| 主包合成源（4 条，运行时生成） | 4（example.edu） | **0**（null + 三元组） |
| **合计（静态 + 合成）** | **211** | **0** |

- 计数必须**逐文件、逐类**报，不得只报总数；任何一处非 0 ⇒ 不采纳。
- 判据：对 `core/data/src/main/resources/knowledge/**` 全量扫描 `example.edu|example.com|example.net|example.org|localhost`，命中 = 0；再对 loader 生成的 4 条合成源断言 `sourceUri == null`。

### 3.4 判据四：契约测试全绿

- `:core:database:testDebugUnitTest --tests "*KnowledgeBaseImportContractTest*"` exit 0，且 N1–N6 / P1–P4 全部在位（不是只跑旧用例）。
- `:core:data:testDebugUnitTest --tests "*BundledKnowledgeNodesContractTest*"` exit 0（loader null 顶层 + 合成源三元组）。
- 新负例的失败信息必须指名违约字段（`source.sourceUri` / `source.publisher` / `source.edition` / `source.licenseStatus`），不能只抛泛化异常。

### 3.5 判据五：三方一致

| 身份 | 写后期望 |
|---|---|
| A 内容 | `version=28` / `contentVersion=ce8edb8f89d91fe7`（== 写后 `content_version(pack, sidecars)` 复算；≠ 即 `INPUT_DRIFT`，停） |
| B 主包 | **`10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670`**（唯一变化 = 顶层 `sourceUri` → null） |
| C dense | `packSha256 == B`（re-pin 后 `10ede1bf…`）；`vectorCount=44,121`；`.vec` `70329115…`；`.vec.json` `6c1c448f…` |
| 副本 | release ↔ staging 30/30 逐字节相同 |

### 3.6 范围守卫（只允许这些变化）

1. 25 个 2025 sidecar + 1 个 2020 sidecar：只允许 `sources[]` 的 `sourceUri/sourceType/licenseStatus/attributionText`（B/C）与 A 的四个字段变化；`sources[]` 行数不变；材料/绑定/正文零变化。
2. 主包：只允许顶层 `sourceUri` 从占位域变 null；`subjects/topics/knowledgePoints` 解析后 deep-equal（文件按现有 `indent=1` 重序列化，理论差异只落在 `"sourceUri"` 一行；若出现其它字节差异，停）。
3. manifest：只改 `version`/`contentVersion`。
4. dense：只改 `.vec.json.packSha256`；`.vec` 不变。
5. **零改**：`source-register-2025-v1.json`、alias_map、anchors、金标、B3 的 retire 台账/判定表、`moe-2025-teaching-support-v2-index.json`。

### 3.7 F24 生产门：**不承诺转绿**

- `pwsh tools/audit-knowledge-source-register.ps1 -RequireProductionReady` 读的是 **来源登记册**，不是包内 `sources[]`；本臂不碰登记册，也不补九科 current-text / teaching / method / worked-example 证据。
- 预期：F24 **仍然红**（`sourceProductionReady=false`，缺九科证据）。§6 必须如实报告其写前/写后状态（`NOT_RUN` 或 `EXIT=1`），**不得**写“来源门已修”或“F24 转绿”。
- 本臂的解释力边界（必须写进批报）：“包内没有伪造 URI + 来源身份可核实（三元组）+ 契约拒收占位域”，**不等于**“九科来源证据齐全”。

### 3.8 装置门

| # | 命令 | 通过条件 |
|---:|---|---|
| 1 | `./gradlew :core:database:testDebugUnitTest --tests "*KnowledgeBaseImportContractTest*" --console=plain` | exit 0 |
| 2 | `./gradlew :core:data:testDebugUnitTest --tests "*BundledKnowledgeNodesContractTest*" --console=plain` | exit 0 |
| 3 | `python tools/kb_build/check_pack_contract.py`（或仓内等价包契约脚本） | exit 0 |
| 4 | `python tools/dense_build/check_asset.py` | exit 0（`packSha256 == 新主包`） |
| 5 | `./gradlew :app:assembleDebug --console=plain` | exit 0 |

- 门 1/2 必须包含 §2.5 的新用例；门 4 验证 re-pin；门 3 若有来源规则，按 §2 对齐。
- 任一门红 ⇒ 不写内容包/不采纳。

### 3.9 报告义务（§6 至少包含）

- 49 源的逐条分类（A/B/C、sourceId、行数、目标字段）。
- 206 行的三元组完整性；B/C 的 marker 落盘。
- 占位域残留逐文件计数（写前 205+1+1+4，写后 0）。
- 四类负例 + 正例的测试名与结果；合成源负例走真实 loader 的证据。
- 特征三层 diff、dense 三值 + `.vec.json` 单字段 diff、三方一致、release↔staging 30/30。
- F24 的写前/写后状态（预期仍红）与边界说明。
- 门 1–5 的 exit code；§4 逐文件 post-sha。

---

## 4. 回滚（触碰清单 + pre/post sha）

### 4.1 A. 代码（写前干净）

| # | 路径 | 预期动作 | pre-sha256（写前） | post-sha256 |
|---:|---|---|---|---|
| 1 | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ProblemCatalog.kt` | 两个枚举各加 `UNVERIFIED_PLACEHOLDER` | `7d6eca0ba8b095869f0ef319b9573b4856312fb4dde8ce4bfa1b6b5e9aee25ec` | §6 |
| 2 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeBaseImportContract.kt` | 占位域表、URI 只对 LICENSED、三元组门、占位类规则 | `34d9a1e74e51df0e9497b98312bbe8ef7f0ee72eee8856ca8c5f79cf172985a6` | §6 |
| 3 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgePackResources.kt` | 顶层 `sourceUri` 可 null；`toSource(String?)` | `05e1e79d339ecb44e3bdd59c8047bff5af3afff89c29b3bd804a23c56989c4a9` | §6 |
| 4 | `core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeBaseImportContractTest.kt` | 夹具 host 去占位；N1–N6 / P1–P4 | `f72177e830fd28b39282a2db9298fad3898163848ad308200814453b467e848a` | §6 |
| 5 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgeNodesContractTest.kt` | loader null 顶层 + 合成源三元组 | `67145ce6004eb7c57141451d0348af8dfcf93d15a00cc7d633c076f49c31fd96` | §6 |
| 6 | `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeContextRetrievalInstrumentedTest.kt` | 若过契约：`example.edu` host 替换 | `fcbf63571ca7ba677883798c8d22b21365061375e4947050c6456ec86ba733fb` | §6 |
| 7 | `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeNodeChangeSignalInstrumentedTest.kt` | 同上 | `8f17853c18fd32a2119d6a2b8ce4ecc2bb6db721a03ccecc47341a03a238bec4` | §6 |

### 4.2 B. 内容（25 个 2025 sidecar + 1 个 2020 sidecar + 主包 + manifest + `.vec.json`）

**25 个 2025 sidecar 全部改写 `sources[]`**（pre-sha 为 08:48 时点工作树值；带 `*` 的 11 个已含 B3 改动）：

| # | 文件 | pre-sha256 |
|---:|---|---|
| 1 | v2-01* | `53541dda2a5f9a8e0f0e54a5dd83ef441a10fad6896da3ef93ffe68a474b3c73` |
| 2 | v2-02* | `ced831d92dad43e16c0d512ba1c64f018be00875cf1ef202d88e720e0f528842` |
| 3 | v2-03* | `e2adbc575830ba499f8573c7ce583b45aa54813da7ffd0980da50187cffb2a5b` |
| 4 | v2-04 | `fa7378a77683f684e62075e630315d74c46ff06dda77151e6408ee92432adb71` |
| 5 | v2-05* | `b59706faa60a8630705cb34caf5b03699989b8064edf4915366007743bc29a8d` |
| 6 | v2-06 | `83169541da5228889bd418093adf2e078a86acd77c543e359dbb00cf4220860e` |
| 7 | v2-07 | `b028f97be1a581008636160a63ba1f4fd0040ccec2befae65c19f3a7261176ae` |
| 8 | v2-08 | `0a18acc8ed221db21af277fc6b70b1a902b47d3284254e78a812c490a0d776fe` |
| 9 | v2-09 | `6d430b34b379aaab201a2038a89d9006807dc033582b347019a7cb496c124cf1` |
| 10 | v2-10 | `dbe1bef32e875b2366880afae7ef6ce96e06dafca95f2f77a9576be82a19d748` |
| 11 | v2-11 | `de218526103a840b595b03071e9d4196798c9e490f147b123b3beb9ae9c98da1` |
| 12 | v2-12 | `12dc6a55f835232c42369149b9c921b5714eb18e981d0eb9f439d4c5d00ff95f` |
| 13 | v2-13* | `ee1eb0d040f86cc0fb6121e5df311b0f0ad121879a9b24942b9bc4e3370da396` |
| 14 | v2-14* | `396c0a7440b76aff185283197e321e89de117c8e8b78157db8ebd9b46b9f6ee1` |
| 15 | v2-15 | `e30a257f08ac94ac8721bda66f40d10ebac10ed0d5780106360c891d04ad43af` |
| 16 | v2-16* | `a3e3e11b501b246fba4274d61cfd529e290fb695ce55775255478c2edafa9764` |
| 17 | v2-17 | `98a76e355c167a5b9758c09cac8010adb2504fb835e0767cf09b25b71f5246c8` |
| 18 | v2-18 | `8ae8b5183dd083f4a047b0abe96f1b72bfb73852c63eb3f00ddf84b0adfcc897` |
| 19 | v2-19* | `ffccae57c43331b483cb7e556fe20b22af1ac020ce922bcc96acd0ee17c93e2c` |
| 20 | v2-20* | `3f0a285974bbdfab4e06ebf59b6e6b797b04c020b703308798d69948a9763980` |
| 21 | v2-21* | `9290efca1eb20310dc24e6d302a6c2787dd0d2cdd65a90835e0d8ca835c69628` |
| 22 | v2-22* | `ba4f7bb06c240a286933de8434f3dc827608a583d5ac4a15403060612f968e39` |
| 23 | v2-23 | `bfe6ddb100c89ab743f5d61a053dbe06247a8db0669e7cae6f8e497502f16703` |
| 24 | v2-24 | `d5c685414c7110a7bf22161663217dab7d38bb37eeb017882d23dea844f2e064` |
| 25 | v2-25 | `932691822c12510e64385093255b91dc6f52ab3e7c44f3dfac01ac466e501552` |

**其它内容文件**：

| # | 路径 | 预期动作 | pre-sha256 |
|---:|---|---|---|
| 26 | `core/data/src/main/resources/knowledge/moe-2020-teaching-support-v1.json` | A 类 1 行 | `5aea4e60867c50cd780f249b6d0f46855d6ceebf7a54a971ecf321f849041006` |
| 27 | `.../moe-2025-four-subjects-v1.json` | 顶层 `sourceUri` → null | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| 28 | `.../moe-2025-update-manifest.json` | 27→28；contentVersion → `ce8edb8f89d91fe7` | `a08cbfcb67bf26edbeaf0d7b022674a82951c4eb7fc4c8007988ee8c84e99a22` |
| 29 | `.../dense/bge-small-zh-int8.vec.json` | 只改 `packSha256` → `10ede1bf…` | `6612a28d9dd71e140e91f4300c142f8ca8dae027ca58afde88e0f7d3f749dc4b` |

### 4.3 C. 新增文件

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/database/src/test/kotlin/.../SourceUriContractTest.kt` | N1–N6 / P1–P4 独立用例（若并入现有 ContractTest 则不必新建；二选一，不重复） |
| 2 | `core/data/src/test/kotlin/.../SourceUriPlaceholderTest.kt` | 全包扫描：0 个占位域 + 49 源三分类计数 |

### 4.4 D. 零触碰

| # | 路径 | pre-sha256 |
|---:|---|---|
| 1 | `.../dense/bge-small-zh-int8.vec` | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 2 | `.../moe-2025-teaching-support-v2-index.json` | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 3 | `knowledge-production/source-register-2025-v1.json` | 写前 sha 见 §6（本会话未算） |
| 4 | `tools/kb_build/tables/alias_map.csv` | `5e8ed8baec36b30212fd81cbf4e7e9280d8d3346489f7035a0920c7317682451` |
| 5 | `tools/kb_build/tables/material_retire.csv`（B3） | `b9948a1c4ed7377e4f3a32da4216507e753ef4be6454e9251660988f624de360` |
| 6 | `tools/kb_coverage/tables/golden_queries_v2.json` | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |

### 4.5 写前备份 / 回滚 / 采纳

**写前备份**

1. 把 §4.1 的 7 件、§4.2 的 29 件复制到 `build/agent-batch33/b4-backup/`，落 `sha256sum` 全表。
2. `git status --porcelain -- <§4.1 路径>` 必须为空；§4.2 的 11 个 `M` sidecar 按当前工作树备份，**不得**回滚到 B3 之前。
3. 复核：manifest `27/2cd974dbada7cb2f`；材料 64,853；主包 `96246c78…`；`.vec` `70329115…`；49 源分类计数 A1/B4/C44。

**回滚**

1. 不得整树 `git checkout`；用备份覆盖 §4.1/§4.2，删除 §4.3 的新测试（若已入库则回滚提交）。
2. 设备侧：内容戳回到 `2cd974db…`，下次启动全量调和（≈20 s）；无需 `pm clear`。
3. 核验：主包回到 `96246c78…`；`.vec.json` 回到 `6612a28d…`；manifest 回到 `a08cbfcb…`；sidecar 回到 §4.2 的 pre-sha；占位域恢复为写前的 207（静态）+ 4（合成）。
4. 复跑 §3.8 门 1/2：应回到写前用例集（N1–N6 不存在）。

**采纳**

保留 §4.1/§4.2/§4.3；§6 登记 49 源分类、206 行三元组、占位残留 0、契约测试、三方一致、F24 仍红。

---

## 5. 未做 / 未验证（写作时点）

- **未改代码/内容包**：§1–§2 是冻结定义；`ce8edb8f89d91fe7` / `10ede1bf…` / `6c1c448f…` 都是**预测值**。
- **未跑 Gradle/设备/JVM**：§3 的门均 `NOT_RUN`。
- **契约测试未写**：N1–N6/P1–P4 是设计，不是已存在用例。
- **`check_pack_contract.py` 的来源规则未核实**：本会话只读了 Kotlin 契约与 PS 审计；Python 包契约是否也校验 sourceUri 未逐行确认（门 3 若没有来源规则，以 Kotlin 门为准并在 §6 注明）。
- **F24 未重跑**：预期仍红；本会话未执行 `pwsh`。
- **2020 sidecar 的 A 类字段**：`edition=2020版`；A 的真实 URI 来自登记册 `documentUri`，未在浏览器复核可达性。
- **仪器化夹具**是否走契约未逐行确认：`KnowledgeContextRetrievalInstrumentedTest`/`KnowledgeNodeChangeSignalInstrumentedTest` 的 `example.edu` 是否会被门 1/2 覆盖，执行轮以实际结果为准。
- **主包顶层真实 URI 的可得性**：仓内登记只有 2020 版官方 ZIP 与 2025 版各科镜像；没有 2025 四科合一的官方 URI，故置 null（不推断版次）。

---

## 6. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；备份与回滚核验；§4.1/§4.2 逐文件 post-sha；49 源分类复核（A1/B4/C44）与 206 行三元组；占位域逐文件计数（写后 0）；四类负例+正例与合成源负例（真实 loader）结果；特征三层 diff；`.vec`/id 块/`.vec.json`（只 packSha256 变）与 `check_asset` exit；三方一致（28/`ce8edb8f89d91fe7`/`10ede1bf…`/`6c1c448f…`）；release↔staging 30/30；F24 写前/写后状态（预期仍红，不得写转绿）；门 1–5 exit code。未跑项一律 `NOT_RUN` / `UNVERIFIED`。

---

## 6.1 写前 / 写后环境（实跑）

| 项 | 写前（2026-11-03T08:53:14+0800） | 写后（2026-11-03T09:34:36+0800） |
|---|---|---|
| `HEAD` | `faab17f58b01eab78d5c08a9f843b3af3df2fecf` | 同（本臂未提交） |
| `git status --porcelain \| wc -l` | 575（443 `??` / 14 `D` / 118 `M`） | 575 + 本臂新增 `M`（全部在 §6.2 清单内）；`git diff --cached` 仍 0 |
| 写前 pre-sha 守卫 | §4.1/§4.2 共 29 件逐件实算，**29/29 与 §4.1/§4.2 的 frozen pre-sha 相同** | — |

- 备份：`build/agent-batch33/b4/backup/`（§4.1 代码 9 件 + §4.2 内容 29 件），`sha256sum.txt` 全表。
- 回滚核验（实做）：把 29 件内容文件整体还原为备份值后跑三条指标测试，再整体写回；29 件 post-sha 与 `rewrite-report.json` **逐件相同**，主包回到并保持 `10ede1bf…`。
- 内容改写脚本：`build/agent-batch33/b4/scripts/rewrite_sources.py`（写前 frozen-sha 守卫 + 分类/三元组守卫 + **先在内存算预测值，等于 §0.3 才落盘**）。实跑输出：`guard1 OK files=29` / `guard2 OK A=(1,1) B=(4,4) C=(44,201) rows=206 ids=49` / `guard3 predictions OK`（三个预测值逐字符命中）/ `WRITTEN files=29`，exit 0。

## 6.2 触碰清单（本臂实际改动）

| # | 文件 | 动作 |
|---:|---|---|
| 1 | `core/model/.../ProblemCatalog.kt` | 两个枚举各加 `UNVERIFIED_PLACEHOLDER` |
| 2 | `core/database/.../KnowledgeBaseImportContract.kt` | 占位域表（精确+子域+保留后缀）、URI 只对 `LICENSED`、三元组门、占位类 null/marker 规则 |
| 3 | `core/data/.../BundledKnowledgePackResources.kt` | 顶层 `sourceUri` 可 null（键必须存在）、`toSource(String?)`、新增 `requiredNullableString` |
| 4 | `core/database/src/test/.../KnowledgeBaseImportContractTest.kt` | 夹具 host 去占位 + N1–N5/空 license/marker 负例 + P1–P3/4 正例 |
| 5 | `core/data/src/test/.../BundledKnowledgeNodesContractTest.kt` | 原节点门 + P4（合成源过契约）+ N6（真实 loader + 顶层占位域→拒收）+ 49 源三分与 0 占位扫描 |
| 6 | `core/data/src/androidTest/.../KnowledgeContextRetrievalInstrumentedTest.kt` | 夹具 host 去占位（走 `importKnowledgeBase`→会过契约） |
| 7 | `core/data/src/androidTest/.../study/KnowledgeNodeChangeSignalInstrumentedTest.kt` | 同上（走 `applyKnowledgeContentUpdate`） |
| 8 | **§4.1 之外** `core/domain/.../KnowledgeResearch.kt` | 新增枚举后 `researchPriority()` 的 `when` 需穷尽——加 `UNVERIFIED_PLACEHOLDER -> 4`（否则 `:core:domain` 编译失败） |
| 9 | **§4.1 之外** `core/database/src/test/.../KnowledgeTeachingMaterialContractTest.kt` | 夹具 `sourceUri` `example.org`→`www.pep.com.cn` |
| 10 | **§4.1 之外** `core/database/src/test/.../ReviewedKnowledgePackContractTest.kt` | 夹具 `example.edu`→`www.moe.gov.cn` |
| 11 | **§4.1 之外** `core/data/src/test/.../ReviewedKnowledgePackJsonCodecTest.kt` | 两处夹具 host 去占位（顶层 legacy 包 + sidecar） |
| 12 | **§4.1 之外** `core/data/src/test/.../ReviewedTeachingMaterialSidecarBindingVerdictTest.kt` | 夹具 host 去占位 |
| 13 | **§4.1 之外** `core/data/src/test/.../ReviewedTeachingMaterialSidecarProvenanceTest.kt` | 夹具 host 去占位 |
| 14–42 | §4.2 的 25 sidecar + 2020 sidecar + 主包 + manifest + `.vec.json` | 三分类改写 / 顶层 null / re-pin |

- §4.3 的两份新测试文件**未新建**：N1–N6/P1–P4 与「0 占位 + 49 源三分」并入现有两个测试类（§4.3 允许二选一），门 2 因此直接覆盖新用例。
- 8–13 是**契约门本身的连带修复**：6 个夹具原本用 `example.org/example.edu`，新规则下会被拒收。`KnowledgeCoverageContractTest` 的 `example.org` 不过导入契约，未改。
- 门 3 依赖的 `build/kb-staging` 由 release 同步（复制，非内容创作）：28 件更新，`release↔staging 30/30 逐字节相同`（见 §6.7）。**写前 staging 已与 release 有 11 件不一致**（B3 遗留），本臂把它拉平。

## 6.3 §4.2 逐文件 post-sha（实算）

| 文件 | post-sha256 |
|---|---|
| `moe-2025-teaching-support-v2-01.json` | `48914f3676222a0509fa09a30f051312383742e314daa1d43875a4ee41e549d9` |
| `moe-2025-teaching-support-v2-02.json` | `d7b11f02e1e81820258622b6b2b86551e7ccf9b7efb8249a838ad45066b49543` |
| `moe-2025-teaching-support-v2-03.json` | `6b9a3df7041d4e1e87bf8fcdca55526cbcfb97633c72810bdf85e0df6337e777` |
| `moe-2025-teaching-support-v2-04.json` | `8825ebc738f028ff307cce4d672d291e130d39baaaeaadbcbdba59d215c65f46` |
| `moe-2025-teaching-support-v2-05.json` | `349cfbcb032ec2ffa818536ffb46549c7228c8a9b8e43d65443417b3c932c188` |
| `moe-2025-teaching-support-v2-06.json` | `315b27044638884ad0dd063047de8ad8d5bd45ebab54486ed039a7229989d8a3` |
| `moe-2025-teaching-support-v2-07.json` | `6b76cd821f08e0eeb44d70e6c10d6e3455502ccd247ce4975964d181232c4581` |
| `moe-2025-teaching-support-v2-08.json` | `e9e32476ea1187eaa17998e611d4303380858cd427320d4fd2fb31628aeec0bd` |
| `moe-2025-teaching-support-v2-09.json` | `7b69608f5fa13b7bcc39d3b10a845745ed1884fc76053c6c98a64341b7ad34d5` |
| `moe-2025-teaching-support-v2-10.json` | `387cb12487059a9968e6f9942aa90f6b32c9e925a8a667bf1a27675c943d434d` |
| `moe-2025-teaching-support-v2-11.json` | `4a44f95c8b87b3ef8987ebe19dca3d5c269f871aa54daad270e39fa0c8c34d35` |
| `moe-2025-teaching-support-v2-12.json` | `2e365970763041dcc767726836cf08588683457c3181396fbf8f58aea68fac2d` |
| `moe-2025-teaching-support-v2-13.json` | `b5b22b7f73b69a4c9a3d0ec5815efd9227288fd8205690dfbda496cd13b3b4fa` |
| `moe-2025-teaching-support-v2-14.json` | `0aa11eb0022833e738cef851ddcee5d19f03500d1172ee545a55c4721e2a1338` |
| `moe-2025-teaching-support-v2-15.json` | `b101bbb69f45a5af8806c19a9e85ba451309b2dbc3e2d552bff16d5db47474e1` |
| `moe-2025-teaching-support-v2-16.json` | `116f65560367ac1a37fae26e130e6be8a78856bfd5943451b840f6d95a4bc6c1` |
| `moe-2025-teaching-support-v2-17.json` | `f34359cef01495a65b8bfc4ab44a0c7947788545e03774833df483ec20deabb0` |
| `moe-2025-teaching-support-v2-18.json` | `f48aa05c6781d1a2d6eaddadb26c7ad51a12b9f4991f049cd72572ead27a4910` |
| `moe-2025-teaching-support-v2-19.json` | `f64169ccd6adf765fc4b55a4784e1c5e247923c4b09352fa0bd1480a94302cbc` |
| `moe-2025-teaching-support-v2-20.json` | `56b85a4cd476e60b2b377ed63c96d8cd1d3b1608445dd2e032ebdea12c415220` |
| `moe-2025-teaching-support-v2-21.json` | `477ba1ee99d1cbb46ae9ebdd6f313b65523dbaef1c106c82415bdef6f3ca3c53` |
| `moe-2025-teaching-support-v2-22.json` | `b1a213fcf4f32b9728e2198ad615357df45ccbc2c214e3c66c5cddf7935b277a` |
| `moe-2025-teaching-support-v2-23.json` | `b5123a93de1a2d105f7187e3a01ecbd0837ffd8014d206b9bf5e1ee10f7457e4` |
| `moe-2025-teaching-support-v2-24.json` | `9559d5001aa68e9a8cd61bffafd530315517177c30662fe16e9b8afffc3cb393` |
| `moe-2025-teaching-support-v2-25.json` | `858e58d25cafc10ae76a952c8666be7caa87bdf66c046deb021d1ef1f7708d82` |
| `moe-2020-teaching-support-v1.json` | `69e8eeb57cde95a4d2f0fbc3f93c8731a011e620bf4cfcc46320ca8504ca7806` |
| `moe-2025-four-subjects-v1.json` | `10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670` |
| `moe-2025-update-manifest.json` | `d69ebb8b949d8b3cab1b1d92bc627c2aadd813186f53de300c4c899927d031a4` |
| `dense/bge-small-zh-int8.vec.json` | `6c1c448f2428091c5b91ed8888aa4c1ef90539201f47ef82db4682c2f02a07fc` |

代码 post-sha：

| 文件 | post-sha256 |
|---|---|
| `ProblemCatalog.kt` | `ba103b8584d721e5f416cb43a1995584d3fae6c18f6c447d34cb0a64b7056681` |
| `KnowledgeResearch.kt` | `078a203105fd9052590312cefb461e524a0bfa68e352fb7a9cf94446ddc11b4a` |
| `KnowledgeBaseImportContract.kt` | `dd74feb180feb216d2372323243011e799691f86ed14380948b4656cee473bc0` |
| `BundledKnowledgePackResources.kt` | `4525edaa1f65b5457e8fc80116ec700df5dc15cac5868f6526108932598bd7d2` |
| `KnowledgeBaseImportContractTest.kt` | `63a174579e2f244f38efb649aaf10ee4590f2dd413aa5e7b0d7863e936721c4a` |
| `KnowledgeTeachingMaterialContractTest.kt` | `67be06b105e0c17c764e3766f7c9293abb62ca738a5a486571f21fbf6343482a` |
| `ReviewedKnowledgePackContractTest.kt` | `90a9e84f1606cd15b3013734755fa40da25d3851d48375a508f3a2f018d267fd` |
| `BundledKnowledgeNodesContractTest.kt` | `6173f2bf8db9506669a29f05e6539ddac8d04d0e0cbba66840d6d85d7cde4d4c` |
| `ReviewedKnowledgePackJsonCodecTest.kt` | `c4d5b76ea1bef380ccb3b6aa52fcc9cc1f1d0208d5a856ed4d10420c33970df7` |
| `ReviewedTeachingMaterialSidecarBindingVerdictTest.kt` | `2631c14b35e14f85322a8308f07f4dec5556212e220e585ac06d244089c6a199` |
| `ReviewedTeachingMaterialSidecarProvenanceTest.kt` | `c0ca9ef9ead99e6d8b57ff89f3b7d8108fe2b6d81d0f0a60006ea807819fdeac` |
| `KnowledgeContextRetrievalInstrumentedTest.kt` | `4d2ff5face874edbf28336c23059f3de92cadec2c9c0612261ded0f86d62b808` |
| `KnowledgeNodeChangeSignalInstrumentedTest.kt` | `3e7790b66d0f1b913bf752c39a108e3831eb5fe3a80f82ab4e7b1bb9ea6b120e` |

## 6.4 49 源分类 / 206 行三元组 / 占位残留（实算）

| 项 | 值 |
|---|---|
| 分类（源 / 行） | A=**1/1**、B=**4/4**、C=**44/201**；合计 **49 源 / 206 行** |
| 2025 sidecar | 25/25 文件被改写；48 个唯一 sourceId |
| 2020 sidecar | 1 行（A 类）；legacy schema 1，**未新增 attributionText 键**（codec 的 `requireOnlyKeys` 不允许） |
| 三元组完整性 | `publisher` / `title` / `edition` **206/206 非空**（脚本逐行断言通过） |
| 语义作用域 | 逐文件 deep-diff：sidecar 只动 `sources[*].{sourceUri,sourceType,licenseStatus,attributionText}`（2020 只动前三个）；主包只动 `/sourceUri`；manifest 只动 `version/contentVersion`；`.vec.json` 只动 `packSha256`；`materials` 全部 deep-equal |
| 占位域残留（静态） | **207 → 0**（25 sidecar 205 + 2020 sidecar 1 + 主包顶层 1；逐文件计数见 `build/agent-batch33/b4/placeholder-after.txt`） |
| 占位域残留（合成源） | **4 → 0**（2025 包 4 条合成源 `sourceUri=null`，P4 实跑断言） |
| 写后 sourceType | `UNVERIFIED_PLACEHOLDER` 205 / `OFFICIAL_CURRICULUM_STANDARD` 1 |
| 写后 licenseStatus | `REFERENCE_ONLY` 201 / `UNVERIFIED_PLACEHOLDER` 4 / `PUBLIC_OFFICIAL` 1 |

## 6.5 契约用例（门 1 / 门 2，实跑）

| # | 用例（测试方法） | 期望 | 实结果 |
|---:|---|---|---|
| N1a | `rejectsPlaceholderHostForPublicOfficialSource` | 拒收（占位域） | PASS |
| N1b | `rejectsPlaceholderHostForReferenceOnlySource` | 拒收（占位域） | PASS |
| N2 | `rejectsLicensedSourceWithoutAnyUri` | 拒收（`source.sourceUri`） | PASS |
| N3 | `rejectsPublicOfficialSourceWithoutTheCitationTriplet` | 拒收（`source.edition`） | PASS |
| N4 | `rejectsReferenceOnlySourceWithoutTheCitationTriplet` | 拒收（`source.publisher`） | PASS |
| N5 | `rejectsPlaceholderStatusThatStillCarriesAUri` | 拒收（`source.sourceUri`） | PASS |
| N6 | `syntheticSourcesAreRejectedWhenThePackTopLevelUriIsAPlaceholder` | 拒收（`source.sourceUri`） | PASS |
| L0 | `rejectsEmptyLicenseStatus` | 拒收（`source.licenseStatus`） | PASS |
| M0 | `rejectsPlaceholderSourceWithoutTheAttributionMarker` | 拒收（`source.attributionText`） | PASS |
| P1 | `acceptsRealOfficialUriWithPublicOfficialStatus` | 通过 | PASS |
| P2 | `acceptsPlaceholderStatusWithNullUriAndTriplet` | 通过 | PASS |
| P3 | `acceptsReferenceOnlyWithNullUriAndTriplet` | 通过 | PASS |
| P4 | `syntheticSourcesProducedByTheLoaderPassTheImportContract` | 通过 | PASS |

- 门 1 `./gradlew :core:database:testDebugUnitTest --tests "*KnowledgeBaseImportContractTest*" --console=plain` → **BUILD SUCCESSFUL，23 tests / 0 failures**（新用例与旧 11 条同轮）。
- 门 2 `./gradlew :core:data:testDebugUnitTest --tests "*BundledKnowledgeNodesContractTest*" --console=plain` → **BUILD SUCCESSFUL，5 tests / 0 failures**。
- **N6 走真实 loader**：读取 `src/main/resources` 的真实主包 → `ReviewedKnowledgePackJsonCodec.decode`（→ `requiredNullableString` → `toSource`）→ 4 条合成源 → `KnowledgeBaseImportContract.validateSourcesOnly` → `source.sourceUri must not use a placeholder host 'www.example.edu'`。（先以不可变 JsonObject 改写顶层 `sourceUri`，其余键原样。）
- **与冻结 `§2.1` 的一处冲突（必须记）**：§2.1 字面判定（「等于精确项，或以 `.`+后缀结尾」）**拒不掉** N1 的 `https://www.example.edu/material`（`www.example.edu` 既不等于 `example.edu`，也不以 `.invalid/.test/.local/.localhost/.example` 结尾）——两处 frozen 文本互相矛盾。实现按 **N1** 取「**精确项的子域也算占位域**」（`host.endsWith(".$exact")`），其余照 §2.1；IPv6 的 `uri.host` 带方括号，先去括号再比对。这是**加严**，不是放松。

## 6.6 判据一/二/三：特征与 dense（实跑）

**判据一（三层）**

| 层 | 写前 | 写后 | 结论 |
|---|---|---|---|
| 1a 节点表面 | `subjects` 子树 canonical sha `cd62bd22b674d5663b39be0db102fa5b6594027fbec15878f78c7188066772e1` | 同 | **deep-equal** |
| 1b 自记口径聚合（3,705 行 `kind/slug/name/sorted aliases/boundary`） | `2e7e433d4948d0c3329bd5733a01e332c5ce81f684fd6822d3232775f024fdb2` | 同 | **IDENTICAL** |
| 2 真实 `KnowledgeSearchFeatureExtractor.fromNode`（JVM 探针，3,705 节点 / 546,119 特征，`(node_id,feature)` 排序 sha） | `f0593a4dc22ff7074a5f4530b831d29d2ee9fc6a1880348395ccc549d8927e36` | 同 | **IDENTICAL**（`build/agent-batch33/b4/feature-layer2.log`） |
| 3a `build/golden-jvm-metrics.txt` | `5126f18740c3466ab9f5069211b6b7d6efe25a2173291bd4947c45a2ba3673e3` | 同 | **逐字节相同** |
| 3b `build/production-lexical-leg.tsv` | `690402f8c568eccacf8e644a6f4a9041e463c71cf601216dcd5338eb6df74bdf` | 同 | **逐字节相同** |

- 镜像装置（`export_invariants.py --diff-against build/agent-batch33/content`）：`vec-ids-baseline.txt` **IDENTICAL** `dedc652d…`；`features-baseline.txt` 仅**第 3 行头** `# packSha256=` 变（body 逐字节相同，`f460819a…` → `777e10c6…`，`bytes` 两版均 9,985,934）；`materials-sample-20.txt` 仅**三行头**（packSha / `corpus=65021→64853` / 逐格计数）不同，20 条样本文本逐字节相同。
- **未复现冻结的 `c3684081…`**：仓库内找不到该聚合的生成脚本/口径（`grep -rn c3684081` 仅命中三份预注册），按 3 轮以内换式尝试 10 余种配方均不命中，故**不声称复现**；改用 1a（更强：整个 `subjects` 子树 deep-equal）+ 1b（自记口径，写前=写后）+ 第 2/3 层（真实抽取器与 JVM 产物逐字节相同）共同支撑「来源字段未进特征路径」。这是**口径记录缺失**，不是判据一未过。

**判据二**

| 检查 | 值 | 结论 |
|---|---|---|
| `.vec` sha | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` | 不变 |
| id 块 sha / 行数 / 唯一节点 | `8f1778326060c1dd2e07597adb6e8d8209080d7684cc9b430ea10ced1a16864e` / 44,121 / 3,280 | 不变 |
| `vectorCount` / `format.count` / `dim` | 44,121 / 44,121 / 512 | 不变 |
| `.vec.json` `packSha256` | `96246c78…` → **`10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670`** | 只此一字段变 |
| `.vec.json` 文件 sha | `6612a28d…` → **`6c1c448f2428091c5b91ed8888aa4c1ef90539201f47ef82db4682c2f02a07fc`** | 与预测一致 |
| 门 4 `python -I tools/dense_build/check_asset.py` | **exit 0**，`packSha256=10ede1bf449de38f` == 旁车记录 | PASS |

**判据三**：静态 207 → **0**（逐文件 0）、合成源 4 → **0**、`knowledge/**` 全文扫描 `example.edu|example.com|example.net|example.org|localhost` = **0**。

## 6.7 三方一致 / 副本 / 门 exit code

| 身份 | 写后期望 | 实测 |
|---|---|---|
| A 内容 | `version=28` / `contentVersion=ce8edb8f89d91fe7` | `28` / `ce8edb8f89d91fe7`；`content_version(pack, sidecars)` 复算 **== 落账**；材料 **64,853**（不变）；retired **1,222**（不变） |
| B 主包 | `10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670` | 同 |
| C dense | `packSha256 == B`；`vectorCount=44,121`；`.vec` `70329115…`；`.vec.json` `6c1c448f…` | 全部命中 |
| 副本 | release ↔ staging 30/30 逐字节 | **30/30**（本臂把 B3 遗留的 11 件差异一并拉平） |

| 门 | 命令 | exit |
|---:|---|---:|
| 1 | `./gradlew :core:database:testDebugUnitTest --tests "*KnowledgeBaseImportContractTest*" --console=plain` | **0**（23 tests / 0 fail） |
| 2 | `./gradlew :core:data:testDebugUnitTest --tests "*BundledKnowledgeNodesContractTest*" --console=plain` | **0**（5 tests / 0 fail） |
| 3 | `PYTHONPATH=tools python -m kb_build.check_pack_contract` | **0**（写前 staging 也 0；同步后在新包上复跑仍 0。**该脚本只校验主题/节点/五表，不校验 `sources[]`**——来源规则以 Kotlin 门为准） |
| 4 | `python -I tools/dense_build/check_asset.py` | **0** |
| 5 | `./gradlew :app:assembleDebug --console=plain` | **0**（BUILD SUCCESSFUL，266 tasks） |

## 6.8 更宽回归与 F24（如实）

| 范围 | 结果 | 归因 |
|---|---|---|
| `:core:domain:testDebugUnitTest` + `:core:database:testDebugUnitTest` | 248 tests，**1 failed**：`KernelWave0SchemaContractTest > the study database version matches the latest exported schema`（`expected:<71> but was:<73>`） | **非本臂**：本臂零改 Room schema/迁移/版本；失败信息指向其它批次把 schema 推到 73 而测试字面量仍 71，且该测试文件与 `schemas/` 均不在本臂触碰面 |
| `:core:data:testDebugUnitTest`（全量） | 1,586 tests，**3 failed**：`ProductionLexicalLegExportTest`（MRR 期望 `0.6130490956072351` 实得 `0.6178294573643413`）、`Stage1LexicalLabTest`（`0.16072351421188627` 实得 `0.15930232558139526`）、`Stage2LexicalScoresExportTest`（命中 100 实得 101） | **写前已红**：把 29 件内容整体还原为 pre-sha 后**同跑三条，3/3 仍失败**（控制实验）；且三份测试源码在本会话开始时即带 `M`（他线未提交的钉值改动），三者均不读取 `sources`/`sourceUri`，材料与 `.vec` 逐字节未变 |
| 契约门连带失败 | 修复前 6 条 `example.org` 夹具失败 → 改 host 后 `ReviewedKnowledgePackJsonCodecTest`/`ReviewedTeachingMaterialSidecar*` **全绿** | 本臂新规则的应有连带，已修 |
| F24 `pwsh -NoProfile -File tools/audit-knowledge-source-register.ps1 -RequireProductionReady` | **EXIT=1**（`Knowledge source production gate is not ready`：九科 current-text / teaching / method / worked-example 证据全缺；`sourceCounts.total=32`） | **写前未单独跑**（`NOT_RUN`），但本臂**零改** `knowledge-production/source-register-2025-v1.json`（sha `cdfa377570369a650aa7962b77a8131d3fe638061e1f5f0dbc23eb81eebfee49`），故写前状态与写后一致。**不得**表述为「来源门已修」或「F24 转绿」。 |

**解释力边界**：本臂证明的是「包内没有伪造 URI + 来源身份可核实（三元组）+ 契约拒收占位域」，**不等于**「九科来源证据齐全」。

## 6.9 未做 / 未验证

- `UNVERIFIED`：仪器化测试（`androidTest`）**未执行**（无设备/未跑 `connectedAndroidTest`）。本臂只对 §6.2 #6/#7 两处已知夹具做了 host 替换；`core/data`、`core/database` 的 `androidTest` 中仍有其它 `example.edu/org` 夹具（如 `KnowledgeBaseImportInstrumentedTest`、`KnowledgeContentReconciliationInstrumentedTest`、`ReviewedKnowledgePackInstrumentedTest` 等），它们同样会过契约、**在设备上会被新规则拒收**——本轮未逐个改写、也未验证。
- `UNVERIFIED`：`.vec.json` 的 `generatedBy` 等生成链元数据未按新包重跑 `tools/dense_build/pack_dense_asset.py`；只按 §3.2 手工 re-pin 一个字段，`check_asset.py` 与 id 块/向量字节均证明未重算向量。
- `UNVERIFIED`：`§0.3` 的 `6c1c448f…`、`ce8edb8f89d91fe7`、`10ede1bf…` 三项预测值与实测**逐字符相同**（本会话实算），但 49 源逐条的「可核实性」只做了字段非空与登记册对照，未做外部官网在线复核。

