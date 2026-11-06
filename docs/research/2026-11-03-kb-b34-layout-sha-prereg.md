# KB B34 `.vec.json` `corpus.layoutSha256`：预注册（2026-11-03）

> **性质**：出数前冻结。§1–§4 写死；执行轮只允许在文末追加 §6，不得回改 §1–§4。
> **写作时点**：2026-11-04T00:20:21+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-03` 是批次日。
> **HEAD**：`0fef943641f53560b92ce2afec4da22bf52f5e86`（`git rev-parse HEAD` 实跑）。
> **上游**：`tools/dense_build/dense_asset.py` 的 `atomic_layout()`（布局权威定义）、`tools/dense_build/check_asset.py`（dense 陈旧性门）、`tools/dense_build/pack_dense_asset.py`（旁车生成）、`core/data/.../dense/DenseVectorAsset.kt`（端侧第二读实现）、`DenseAssetProvenanceTest.kt`（现有三方身份门）。
> **本步唯一新增写盘**：本文件。未改代码/内容包、未跑 Gradle/设备；§0 与 §1.4 的读数是本会话用 `python -I` 实读实算。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-04T00:20:21+0800
git rev-parse HEAD                             # 0fef943641f53560b92ce2afec4da22bf52f5e86
git status --porcelain | wc -l                 # 547
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   441 ??
#   106 M
git diff --cached --name-only | wc -l          # 0
ls docs/research/2026-11-03-kb-b34-layout-sha-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 547 项脏项属他线/已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`。
- 本臂 §4 列出的 5 个既有文件 + 1 个旁车 + 1 个新增测试文件，写前**逐路径** `git status --porcelain -- <路径>` 为空（见 §4）。
- 同目录另有 3 个他线 `M`：`DenseRecallRerankerTest.kt`、`tools/dense_build/gen_device_parity_fixture.py`、`tools/dense_build/model-manifest.json`。**不在本臂触碰面**，不读不改。
- 本臂的 `pre-sha` 都是上述时点的工作树值；不是 HEAD 值。

### 0.2 现状（实读 / 实算）

| 事实 | 值 | 来源 |
|---|---:|---|
| 主包 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | `dense_asset.PACK_RELATIVE` |
| 主包 sha256 | `10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670` | `sha256sum` 实跑 |
| `.vec` | `knowledge/dense/bge-small-zh-int8.vec` | 同目录 |
| `.vec` sha256 / 字节 | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` / 26,087,046 B | `sha256sum`、`check_asset` 实跑 |
| `.vec` 头 | dim=512 / count=44,121 / version=1 | `check_asset` 实跑 |
| 旁车 | `knowledge/dense/bge-small-zh-int8.vec.json`（3,631 B） | 实读 |
| 旁车文件 sha256 | `6c1c448f2428091c5b91ed8888aa4c1ef90539201f47ef82db4682c2f02a07fc` | `sha256sum` 实跑 |
| 旁车 `corpus` | `atomicNodes=3280` / `canonicalVectors=3280` / `aliasVectors=40841` / `vectorCount=44121` / `includeTopics=false` | 实读 |
| 旁车 `corpus.layoutSha256` | **不存在** | `python -I` 实读；见 §0.3 |
| 布局行数 | **44,121 = 3,280 canonical + 40,841 alias** | `atomic_layout()` 实算 |
| 唯一 `node_id` | **3,280** | 实算 |
| 布局载荷字节 | **3,312,905 B**（UTF-8） | 实算 |
| **基线 layoutSha256** | **`1aa8142e381d1b2b40b2ba3d5eec2783e5781830321613fcb78fff7f61e9d75c`** | 本会话实算，与 ask 给定值逐字符相同 |
| `check_asset` 写前 | **exit 0**，10 项检查全 OK | `python -I tools/dense_build/check_asset.py` 实跑 |
| dense 门测试写前 | **6 tests OK**（0.552 s） | `python -I tools/tests/test_dense_asset_gate.py` 实跑 |

- ask 末尾的 `ov` **不是哈希串的一部分**：给定值是以 `…d75c` 结束的 64 位小写十六进制；本会话按 64 位值复算并逐字符命中。§6 记录时也只登记 64 位值。

### 0.3 本臂要消灭的洞（写前实测）

现有 `check_asset` 的 `layout` 检查只比 **ids**（`header["ids"] == rows[*].node_id`，`check_asset.py:117-126`）。`packSha256` 又只是整包字节哈希。于是存在一条真实可复现的绕过路径：

1. 改包里的 **canonical name**（slug 不变）或 **等量替换一个别名**（别名条数不变）；
2. 把旁车 `packSha256` 重钉到改后的包（"把哈希改对"）；
3. ids、行数、向量字节、词表、模型清单全不变 ⇒ **当前门 10/10 全绿**。

本会话在临时副本上实跑了这两个负例（不碰真资产）：

| 负例 | 改法 | ids 不变 | 行数不变 | 新 layoutSha256 | 当前门（`packSha256` 已重钉） |
|---|---|---:|---:|---|---|
| **N1** | 首个非空知识点 `函数的概念` 的 `name` → `函数的概念（改）` | **是** | 44,121 | `28fc9985374da6cbde5944d1c856a07e9e90d543cc2d92d8020e51850c4c9296` | **exit 0 / `failed=[]`** |
| **N2** | 同点 `aliases[0]`（`函数的概念`）→ `等量替换别名-函数的概念`，别名仍 11 条 | **是** | 44,121 | `235faec73c011bc7fec8c3154a23b33763b0c6d9aee25463942fb49f8eea6494` | **exit 0 / `failed=[]`** |

- 复现命令（本会话实跑）：在 `tempfile.mkdtemp()` 里改包 → `json.dumps` 落临时包 → 复制旁车并把 `packSha256` 设为临时包实际 sha → `check_asset.evaluate(REPO, pack_path=临时包, sidecar_path=临时旁车)`。
- **这就是新增 `corpus.layoutSha256` 的唯一理由**：ids 只证明"向量行属于哪些节点"，不证明"这些行对应的文本是什么"。canonical name 与别名文本变了而 ids/行数不变时，现有门没有任何一项能红。
- 本臂不改变这个事实的另一面：`.vec` 向量字节、`vectorCount`、`packSha256`、`VECTOR_ASSET_SHA256` 都**不动**；新增的只是旁车里的一个布局指纹与两端的校验。

---

## 1. 冻结定义

### 1.1 行集（与 `atomic_layout` 逐条同序）

`rows` 的构造**完全复用** `tools/dense_build/dense_asset.py:323-359` 的 `atomic_layout()`，不另写遍历：

1. 按 `pack["subjects"]` 的数组顺序；
2. 每科 `subjectKey = subject["subject"].lower()`；按 `subject["topics"]` 的数组顺序；
3. 每个 topic 的 `topic_id = "kb:<taxonomyVersion>:<subjectKey>:topic:<topic.slug>"`；
4. 按 `topic["knowledgePoints"]` 的数组顺序；每个 point 的
   `node_id = "kb:<taxonomyVersion>:<subjectKey>:atomic:<point.slug>"`；
5. 别名先做**保序去重**（Python `dict.fromkeys(aliases)`；Kotlin `distinct()`），再逐条出行；
6. 每个节点先出一行 canonical，再按别名顺序出 alias：
   - `(node_id, "canonical", point["name"])`
   - `(node_id, "alias", alias)`（每条别名一行）
7. 不包含 topic 行（`includeTopics=false`）；`kind` 的取值域冻结为 **`"canonical"` / `"alias"`** 两个字面量。

`rows` 的行序 = `.vec` 的向量行序 = 端侧"同一节点向量连续"的段序。三者任一漂移，本臂的 ids 或 layoutSha 必须红。

### 1.2 规范序列化（写死；不做任何转义）

```python
payload = b"".join(
    (node_id + "\t" + kind + "\t" + surface + "\n").encode("utf-8")
    for (node_id, kind, surface) in rows
)
layout_sha256 = hashlib.sha256(payload).hexdigest()
```

- 分隔符：单个 **TAB**（`0x09`）；行尾：单个 **LF**（`0x0A`）；**每条 row 都以 LF 结束**（含最后一行）。
- 编码：UTF-8，无 BOM；字符串按 JSON 解析后的**实际字符**写入（不保留 JSON 的 `\n` 转义形式，不二次转义反斜杠）。
- 哈希：SHA-256，输出 64 位**小写**十六进制。
- 这是"有序 (node_id, kind, surface)"的冻结实现；`kind` 参与哈希（canonical↔alias 互换也会红）。
- 该序列化**与 ask 给定基线逐字符吻合**（§0.2），因此不许改成带长度前缀/JSON 行等其它编码；换编码 = 换基线，不在本臂范围。

### 1.3 旁车字段

- 位置：`corpus.layoutSha256`（与 `corpus.vectorCount` 同级）。
- 类型：非空字符串，64 位小写十六进制；**必填**（缺失按红处理，不按"未验证"跳过）。
- 语义：生成该 `.vec` 时所用 `rows` 的 §1.2 摘要。它**不替代** `packSha256`、`sha256`、`vectorCount`，四者并存、各证一层：

| 字段 | 证明什么 | 不证明什么 |
|---|---|---|
| `sha256` | `.vec` 字节就是这一次生成的那一份 | 字节对应的文本是不是当前包 |
| `packSha256` | 旁车/向量链声称的包就是当前包字节 | 包改后是否被"重钉哈希"绕过 |
| `corpus.vectorCount` / ids | 行数、行所属节点与当前包一致 | 行的 `surface` 文本与 `kind` 是否一致 |
| **`corpus.layoutSha256`** | **行的 `(node_id, kind, surface)` 有序序列与当前包一致** | 向量数值本身是否正确（那由 `sha256`/对拍门管） |

### 1.4 基线读数（本会话实算）

| 项 | 值 |
|---|---|
| `layoutSha256` | **`1aa8142e381d1b2b40b2ba3d5eec2783e5781830321613fcb78fff7f61e9d75c`** |
| rows / canonical / alias | **44,121 / 3,280 / 40,841** |
| 唯一 `node_id` | **3,280** |
| payload 字节 | **3,312,905** |
| surface 含 TAB / CR | **0 / 0** |
| surface 含 LF | **6**（全部是 alias；见下） |

**已知边界（必须写进批报，不许隐去）**：当前包有 **6 条别名含换行符**：

| node_id | alias（`repr`） |
|---|---|
| `kb:moe-2025-four-subjects-v1:biology:atomic:物质出入细胞的方式` | `'物质进出细胞方式·运输\n方向'` |
| `kb:moe-2025-four-subjects-v1:biology:atomic:酶在代谢中的作用` | `'酶在细胞代谢中的·无关\n变量'` |
| `kb:moe-2025-four-subjects-v1:biology:atomic:有丝分裂` | `'动物细胞有丝分裂·不\n同\n点'` |
| `kb:moe-2025-four-subjects-v1:biology:atomic:有丝分裂` | `'动物细胞有丝分裂·染色体形\n态变化'` |
| `kb:moe-2025-four-subjects-v1:biology:atomic:有丝分裂` | `'动物细胞有丝分裂·染色单体\n数目变化'` |
| `kb:moe-2025-four-subjects-v1:biology:atomic:探究环境因素对光合作用强度的影响` | `'探究环境因素对光·对无关变量\n进行控制'` |

- 因此 §1.2 的纯 TAB/LF 拼接在**理论上**不是无歧义编码（surface 内的 LF 与行分隔符同形）。本门的用途是**漂移检测**与"改哈希绕过"的封堵，**不宣称构造性碰撞安全**；两端都只对同一份 `rows` 做同一字节拼接，故跨语言一致性不受影响。
- 若将来要求抗构造碰撞，应另立预注册换成带长度前缀的编码并**同时更换基线值**；本臂不得顺手改编码。

### 1.5 明确不改（一条都不许动）

1. `.vec` 向量字节、`vectorCount`、dim、量化字段、`sha256`；`DenseRecallAssembly.VECTOR_ASSET_SHA256`。
2. 主包 `moe-2025-four-subjects-v1.json`（字节与 sha 都不动）。
3. 旁车的**其它**字段（`packSha256` / `sha256` / `corpus.*` 的既有键 / `model.*` / `quantization.*` / `productionLexicalLeg.*`）。
4. 别名/节点/材料/绑定/关系/金标/锚点；`tools/kb_build/**`。
5. `DenseAssetProvenanceTest.kt`（保持原样；新门另开测试类，避免把三处身份门与布局门混在一起）。
6. 运行期装配（`DenseRecallAssembly`）：不解析 30MB 主包、不做布局复核；理由见 §2.2。

---

## 2. 双端校验（冻结）

### 2.1 Python 侧：写字段 + 校验字段

**`tools/dense_build/dense_asset.py`** 新增唯一实现（三处共用，禁止各自另写）：

```python
LAYOUT_TAB = "\t"
LAYOUT_LF = "\n"

def layout_sha256(rows):
    """§1.2 的规范摘要：rows 为 atomic_layout() 的返回，顺序即向量行序。"""
    digest = hashlib.sha256()
    for row in rows:
        digest.update(("%s\t%s\t%s\n" % (row["node_id"], row["kind"], row["surface"])).encode("utf-8"))
    return digest.hexdigest()
```

**`tools/dense_build/pack_dense_asset.py`**：在 `corpus=dict(...)` 里增加
`layoutSha256=D.layout_sha256(rows)`；其余键、键序（`sort_keys=True`）与文件落盘方式不变。

**`tools/dense_build/check_asset.py`**：在现有 `layout`（ids）检查之后新增：

```python
expected_layout = D.layout_sha256(rows)
recorded_layout = ((sidecar.get("corpus") or {}).get("layoutSha256")) or ""
record("layoutSha256", expected_layout == recorded_layout,
       "当前包布局 sha256=%s；旁车 corpus.layoutSha256=%s"
       % (expected_layout[:16], (recorded_layout or "(缺)")[:16]))
```

- **检查名冻结为 `layoutSha256`**；失败信息必须出现 `corpus.layoutSha256` 字样与两侧前 16 位。
- `layout`（ids）与 `layoutSha256` 是**两项独立检查**：ids 红不掩盖 layoutSha 红，反之亦然。
- 缺字段 ⇒ `(缺)` + 红；不得当作"未验证/跳过"。
- `check_asset` 的检查项由 10 项变 **11 项**；正侧仍须 **exit 0**。

### 2.2 Kotlin 侧：ids + layout 同时校验（JVM 门，不进运行期）

**范围决定（写死）**：校验放在 **JVM 单元测试**，不放进 `DenseRecallAssembly` 的运行期加载路径。理由：

- 运行期加载已经用 `VECTOR_ASSET_SHA256` 钉住 `.vec` 字节；再为校验布局解析 30MB 主包，会把一次启动成本变成每次进程启动成本，而它要防的失败（包/旁车/向量三者在构建期漂移）在设备上无法被修复，只能让稠密腿静默回退——那正是现有设计要避免的"检索质量静默掉档"。
- 现有 `DenseAssetProvenanceTest` 就是 Kotlin 侧的构建期身份门；布局门与它同层，失败发生在 `testDebugUnitTest`，而不是用户设备上。

**生产代码唯一新增**：`core/data/.../dense/DenseVectorAsset.kt` 增加一个只读访问器，把"逐行 node_id"暴露给同包测试（现有类只暴露 3,280 个唯一节点 id 与段边界，测试无法重建 44,121 行的行序）：

```kotlin
/** 逐向量行的 node_id（同节点连续，长度 = count）。供布局门核对行序，不参与运行期评分。 */
fun rowNodeIds(): List<String> {
    val rows = ArrayList<String>(count)
    for (node in nodeIdAt.indices) {
        for (row in nodeStart[node] until nodeEnd[node]) rows.add(nodeIdAt[node])
    }
    return rows
}
```

**新增测试类**：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseLayoutIdentityTest.kt`。

测试内的行构造是**独立的第二实现**（与 `DenseVectorAsset` 对 `dense_asset.py` 的关系相同：Python 权威、Kotlin 第二读实现），逐条按 §1.1（下面的 `string/array/obj` 是测试内小扩展，略）：

```kotlin
private data class LayoutRow(val nodeId: String, val kind: String, val surface: String)

private fun layoutRows(pack: JsonObject): List<LayoutRow> {
    val taxonomy = pack.string("taxonomyVersion")
    val rows = ArrayList<LayoutRow>()
    for (subject in pack.array("subjects")) {
        val subjectKey = subject.obj.string("subject").lowercase(Locale.ROOT)
        for (topic in subject.obj.array("topics")) {
            for (point in topic.obj.array("knowledgePoints")) {
                val nodeId = "kb:$taxonomy:$subjectKey:atomic:${point.obj.string("slug")}"
                rows += LayoutRow(nodeId, "canonical", point.obj.string("name"))
                val aliases = point.obj["aliases"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                for (alias in aliases.distinct()) rows += LayoutRow(nodeId, "alias", alias)
            }
        }
    }
    return rows
}

private fun layoutSha256(rows: List<LayoutRow>): String {
    val payload = buildString {
        for (row in rows) append(row.nodeId).append('\t').append(row.kind).append('\t')
            .append(row.surface).append('\n')
    }
    return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
```

**正例断言**（同一测试方法内，按顺序）：

1. 从 classpath 读真包 `knowledge/moe-2025-four-subjects-v1.json` 与旁车 `knowledge/dense/bge-small-zh-int8.vec.json`（`DenseTestFixtures`）。
2. `rows.size == 44_121`；`rows.count { it.kind == "canonical" } == 3_280`；`rows.count { it.kind == "alias" } == 40_841`。
3. `sidecar["corpus"]["layoutSha256"] == layoutSha256(rows)`。
4. `DenseVectorAsset.read(assetBytes, expectedSha256 = DenseRecallAssembly.VECTOR_ASSET_SHA256)` 成功；`asset.count == rows.size`；`asset.nodeCount == rows.map { it.nodeId }.distinct().size`。
5. `asset.rowNodeIds() == rows.map { it.nodeId }`（逐行、逐序；失败信息打印首个差异下标与两侧 node_id）。
6. 旁车 `format.count == rows.size`、`corpus.vectorCount == rows.size`（与现有 provenance 门重叠也无妨：这里是"布局门自洽"的一部分，不是替代）。

**负例断言**：负例改的是**解析后的 `JsonObject`**（不是行列表），再用同一个 `layoutRows()` 重建，确保覆盖"解析 → 行构造 → 摘要"整条链；改后先断言 ids 序列不变，再断言 `layoutSha256` 命中 §2.3 的冻结值，最后断言门函数抛错。

```kotlin
private fun verifyLayout(pack: JsonObject, sidecar: JsonObject) {
    val rows = layoutRows(pack)
    val recorded = sidecar["corpus"]!!.jsonObject["layoutSha256"]!!.jsonPrimitive.content
    assertEquals("corpus.layoutSha256 与当前包布局不一致", layoutSha256(rows), recorded)
}
```

- 每次负例都调用 `assertThrows(AssertionError::class.java) { verifyLayout(mutatedPack, sidecar) }`；失败信息必须含 `corpus.layoutSha256`。
- 另断言 `mutatedIds == baselineIds`（证明"ids 不变"这一前提成立），以及 `layoutSha256(mutatedRows)` 等于 §2.3 的冻结值（跨语言钉住序列化）。

### 2.3 负例（冻结；N1/N2 为 ask 指定，N3 由"有序"推出）

| # | 改法（精确到字符串） | ids / 行数 | 期望 `layoutSha256` | 期望红项 |
|---|---|---|---|---:|
| **N1** | 首个非空知识点（MATH → `数学必修第一册·第三章·函数的概念与性质` → `函数的概念`）的 `name`：`函数的概念` → **`函数的概念（改）`** | 不变 / 44,121 | `28fc9985374da6cbde5944d1c856a07e9e90d543cc2d92d8020e51850c4c9296` | 仅 `layoutSha256` |
| **N2** | 同点 `aliases[0]`：`函数的概念` → **`等量替换别名-函数的概念`**；数组长度仍 11 | 不变 / 44,121 | `235faec73c011bc7fec8c3154a23b33763b0c6d9aee25463942fb49f8eea6494` | 仅 `layoutSha256` |
| **N3**（补充） | 同点交换 `aliases[0]` 与 `aliases[1]`（`函数的概念` ↔ `函数定义的“任意性”与“唯一性”`），集合不变、条数不变 | 不变 / 44,121 | `ca519f35e78b6c96a8c9dd9e9eae0845bb10e5e0e2dbd052b796510bbe774d3d` | 仅 `layoutSha256` |

- N1/N2 的冻结值绑定 **主包 sha `10ede1bf…`**。执行轮若发现主包 sha 不是该值 ⇒ **停**，按 `INPUT_DRIFT` 重新冻结；不得就地更新这三个期望值来"变绿"。
- 三个负例的共同前提：**ids 序列与行数完全不变**。因此现有 `layout`（ids）检查必须保持绿——这正好把"新检查才是抓住它的一项"证明干净。
- Python 侧的 N1/N2（N3）还要把旁车 `packSha256` **重钉**到改后的临时包，并断言 `failed == ["layoutSha256"]`（`packSha256` 不在其中）。若 `packSha256` 也红，说明重钉没做对，测试本身失败。
- Kotlin 侧的负例不需要重钉 `packSha256`：Kotlin 门只比 `layoutSha256`，改包后旁车布局值仍是基线 ⇒ 直接红。

### 2.4 每项新增消灭的具体失败

| 新增 | 消灭的**具体**失败 |
|---|---|
| `dense_asset.layout_sha256()` | 三处（打包/CI 门/Kotlin 测试）各写一份序列化会漂移；单一实现让"同一 rows 在不同脚本里算出不同摘要"不可能发生 |
| 旁车 `corpus.layoutSha256` | canonical name 或等量别名被改、ids/行数不变时，旁车没有任何字段能证明"这些 ids 对应的文本还是不是当前包的文本" |
| `check_asset` 的 `layoutSha256` 检查 | **§0.3 实测的绕过路径**：改包 + 重钉 `packSha256` 后，现有 10 项全绿；新检查让这条路径只剩"重打向量"一条合法出路 |
| `DenseVectorAsset.rowNodeIds()` | 端侧第二读实现只有唯一节点 id 与段边界，无法核对 44,121 行的**行序与每节点向量条数**；没有它，Kotlin 侧无法做 ids 逐行校验 |
| `DenseLayoutIdentityTest` | 旁车 `layoutSha256` 过期/写错、`.vec` 行序与包布局不一致、canonical/alias 文本漂移，这三类在 JVM 构建期无人发现；也让 Python 与 Kotlin 的序列化实现互相钉住 |
| N1 / N2（/N3）负例 | "ids 不变所以布局没变"的错觉；以及"改了包只重钉哈希"的假绿 |

---

## 3. 判读（写死）

### 3.1 门 1：Python dense 门正侧

```bash
python -I tools/dense_build/check_asset.py
```

- 通过条件：**exit 0**，且输出含 `OK layout`（ids，44,121 条）与 `OK layoutSha256`；检查项共 **11** 项。
- 失败信息若出现 `(缺)`，按"旁车没重生成"处理，不按未验证跳过。

### 3.2 门 2：两个（三个）负例必须红

```bash
python -I tools/tests/test_dense_asset_gate.py
```

- 通过条件：**exit 0**，且新用例满足：

| 用例 | 断言 |
|---|---|
| `test_canonicalNameChangeWithStableIdsIsRed`（N1） | `failed == ["layoutSha256"]`；`layout` 不在 failed；`packSha256` 不在 failed；改后 `layoutSha256 == 28fc9985…`；ids 不变 |
| `test_equalCountAliasReplacementIsRed`（N2） | `failed == ["layoutSha256"]`；`layout` 不在 failed；`packSha256` 不在 failed；改后 `layoutSha256 == 235faec7…`；ids 不变 |
| `test_aliasOrderSwapIsRed`（N3，补充） | `failed == ["layoutSha256"]`；改后 `layoutSha256 == ca519f35…`；ids 不变 |

- 既有 6 条用例保持绿；正侧用例的期望检查名列表增加 `layoutSha256`。
- **不许**把 `failed` 断言放宽成 `assertIn` 而丢掉"仅此一项"的约束；那会把"其它检查也红了"掩盖掉。

### 3.3 门 3：Kotlin 布局门

```bash
./gradlew :core:data:testDebugUnitTest --tests "*DenseLayoutIdentityTest*" --tests "*DenseAssetProvenanceTest*" --console=plain
```

- 通过条件：**exit 0**；`DenseLayoutIdentityTest` 正例 + N1/N2（+N3）全绿（负例以 `assertThrows` 的形式绿）；`DenseAssetProvenanceTest` 原有两条仍绿。
- 正例必须真的读到主包与旁车（classpath 资源）；读不到即失败，不许 skip。
- Kotlin 计算的基线摘要必须等于 `1aa8142e…`；N1/N2/N3 必须分别等于 §2.3 的冻结值。三处（Python 基线/负例、Kotlin 正例/负例）**逐字符相同**才算跨语言实现一致。

### 3.4 判据：旁车只增一行

| 检查 | 写前 | 写后 |
|---|---|---|
| `corpus.layoutSha256` | 不存在 | **`1aa8142e381d1b2b40b2ba3d5eec2783e5781830321613fcb78fff7f61e9d75c`** |
| 旁车文件 sha256 | `6c1c448f…` | **预测 `f0c947950554a7f368fcdfbe3e94e3352d31cfa4653d6ff83da9b082012070e8`** |
| 旁车字节 | 3,631 B | 3,719 B（+88 B） |
| 语义 diff | — | **只多一行**：`    "layoutSha256": "1aa8142e…",`（位于 `includeTopics` 与 `layoutSource` 之间） |
| `.vec` sha256 | `70329115…` | **必须相同** |
| 主包 sha256 | `10ede1bf…` | **必须相同** |
| `packSha256` / `vectorCount` / `format.count` | — | **必须相同** |
| `DenseRecallAssembly.VECTOR_ASSET_SHA256` | `70329115…` | **必须相同** |

- 预测值的依据：当前旁车用 `json.dumps(..., ensure_ascii=False, indent=2, sort_keys=True) + "\n"` **重序列化后与现有字节逐字节相同**（本会话实跑 `re-serialize identical? True`），因此只插入一个键即可确定性推出新字节与新 sha；执行轮必须复算并与该预测对账。
- 若旁车出现**第二处**字节差异（例如 `quantization.*` 被重算），停：说明有人跑了 `pack_dense_asset.py` 全链，超出"只加一个字段"的范围。

### 3.5 范围守卫（只允许这些变化）

1. `tools/dense_build/dense_asset.py`：只加 `layout_sha256()`（及其字符串常量/注释）。
2. `tools/dense_build/pack_dense_asset.py`：只在 `corpus=dict(...)` 增加 `layoutSha256=` 一项。
3. `tools/dense_build/check_asset.py`：只加 `layoutSha256` 检查（保留原 `layout` 检查名与语义）。
4. `tools/tests/test_dense_asset_gate.py`：只加 N1/N2（/N3）用例与正侧检查名列表。
5. `core/data/.../dense/DenseVectorAsset.kt`：只加 `rowNodeIds()`，不改 `read()` 的任何校验。
6. `core/data/src/test/.../dense/DenseLayoutIdentityTest.kt`：新增文件。
7. `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json`：只加 `corpus.layoutSha256` 一行。
8. **零改**：`.vec`、主包、`DenseRecallAssembly.kt`、`DenseAssetProvenanceTest.kt`、`model-manifest.json`、`tools/dense_build/model-manifest.json`（他线 `M`，不碰）、词表/分词器、金标、alias_map。

### 3.6 装置门与报告义务

| # | 命令 | 通过条件 |
|---:|---|---|
| 1 | `python -I tools/dense_build/check_asset.py` | exit 0；11 项全 OK |
| 2 | `python -I tools/tests/test_dense_asset_gate.py` | exit 0；N1/N2（/N3）的 `failed == ["layoutSha256"]` |
| 3 | `./gradlew :core:data:testDebugUnitTest --tests "*DenseLayoutIdentityTest*" --tests "*DenseAssetProvenanceTest*" --console=plain` | exit 0 |
| 4 | `sha256sum core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | `.vec`=70329115…；旁车=f0c94795…；主包=10ede1bf… |

- §6 至少报告：写前/写后 porcelain 与 HEAD；§4 逐文件 post-sha；门 1–4 的 exit code 与关键输出；N1/N2（/N3）的实际 `failed` 列表与三个摘要；旁车语义 diff（只一行）；`.vec`/主包/常量零变的实测 sha；Python 与 Kotlin 两侧摘要逐字符一致的证据；未跑项一律 `NOT_RUN`/`UNVERIFIED`。

---

## 4. 回滚（触碰清单 + pre/post sha）

### 4.1 A. 代码（写前均干净）

| # | 路径 | 预期动作 | pre-sha256（写前实测） | post-sha256 |
|---:|---|---|---|---|
| 1 | `tools/dense_build/dense_asset.py` | 加 `layout_sha256(rows)` | `2dafbfa994a3c664ec7f021e522daa138ba797c02c024c68b078a874d901ae46` | §6 |
| 2 | `tools/dense_build/pack_dense_asset.py` | `corpus` 加 `layoutSha256` | `50a754af30f2cfe413ed743f185b6e9cd4165b1095550a66bc98642d0f7c452d` | §6 |
| 3 | `tools/dense_build/check_asset.py` | 加 `layoutSha256` 检查 | `f06658582340def6099e58bae13989781fa91e3d45de997fd519c2dbddcaac4e` | §6 |
| 4 | `tools/tests/test_dense_asset_gate.py` | N1/N2（/N3）+ 正侧检查名 | `4caab16d0a217f32a956a05ec11dcb47ea0b1de878c6d2250da9da601dec5901` | §6 |
| 5 | `core/data/src/main/kotlin/.../dense/DenseVectorAsset.kt` | 加 `rowNodeIds()` | `c95fe9ed4c7187ddadb298ca9d4a1ca52f8ec90c9a6ecc05734de9dd3eb05334` | §6 |

### 4.2 B. 新增文件

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/data/src/test/kotlin/.../dense/DenseLayoutIdentityTest.kt` | 新增：正例 + N1/N2（/N3） |

### 4.3 C. 数据（旁车）

| # | 路径 | 预期动作 | pre-sha256 | post-sha256（预测） |
|---:|---|---|---|---|
| 1 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json` | 只加 `corpus.layoutSha256` 一行 | `6c1c448f2428091c5b91ed8888aa4c1ef90539201f47ef82db4682c2f02a07fc` | `f0c947950554a7f368fcdfbe3e94e3352d31cfa4653d6ff83da9b082012070e8`（§6 复算） |

### 4.4 D. 零触碰

| # | 路径 | pre-sha256 |
|---:|---|---|
| 1 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 2 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | `10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670` |
| 3 | `core/data/src/test/kotlin/.../dense/DenseAssetProvenanceTest.kt` | `4dbe99aafb0e86db80300cae9370c2bcf84ac180ec34974fd66b515e48f71c6f` |
| 4 | `core/data/src/main/kotlin/.../dense/DenseRecallAssembly.kt` | 写前未单算（`NOT_RUN`）；执行轮用 `git status --porcelain --` 证明零改 |
| 5 | `tools/dense_build/model-manifest.json` | 他线 `M`：**不碰、不备份、不回滚** |

### 4.5 写前备份 / 回滚 / 采纳

**写前备份**

1. 把 §4.1 的 5 件与 §4.3 的 1 件复制到 `build/agent-batch34/b4-layout-sha/backup/`，落 `sha256sum` 全表。
2. 复核：§4.1/§4.3 的 6 个 pre-sha 与上表逐件相同；`git status --porcelain -- <6 路径>` 为空。
3. 复核基线：`layoutSha256=1aa8142e…`、rows 44,121、`.vec` `70329115…`、主包 `10ede1bf…`、旁车 `6c1c448f…`。

**回滚**

1. 不得整树 `git checkout`；用备份覆盖 §4.1/§4.3，删除 §4.2 的新测试（若已入库则回滚提交）。
2. 复核：旁车回到 `6c1c448f…`；`.vec`/主包不变；门 1 回到 10 项检查的写前形态。
3. 复跑 `python -I tools/dense_build/check_asset.py` 应 exit 0（但不含 `layoutSha256`）。

**采纳**

保留 §4.1/§4.2/§4.3；§6 登记 post-sha、门 1–4、三个负例摘要、旁车单行 diff、跨语言一致结论。

---

## 5. 未做 / 未验证（写作时点）

- **未改任何代码/旁车**：§2 的 helper、检查项、Kotlin 测试都是冻结设计；`f0c94795…` 是**预测值**，不是已落盘结果。
- **未跑 Gradle**：门 3 `NOT_RUN`；`DenseLayoutIdentityTest` 尚未创建。
- **未跑 `pack_dense_asset.py`**：它需要 `build/dense-model/int8-docs.npy` 与 `build/production-lexical-leg.tsv`；本臂只加一个旁车字段，执行轮按"确定性重序列化 + 只插入一行"落盘，并按 §3.4 对账。若执行轮选择跑全链重生成，必须证明 `quantization.*` 等其余字段逐字节不变，否则视为越界。
- **`ov` 口径**：ask 末尾 `ov` 未作为哈希内容；按 64 位值复算命中。若 owner 本意另有含义，§6 前需澄清。
- **换行歧义**：§1.4 的 6 条含 LF 别名意味着 §1.2 不是无歧义编码；本臂只声称漂移检测，不声称抗构造碰撞。未做碰撞构造实验。
- **Kotlin 解析开销**：`DenseLayoutIdentityTest` 会解析约 30MB 主包；本会话未跑 JVM，实际耗时/堆占用 `UNVERIFIED`。若测试超时，只允许调测试超时/堆，不允许跳过正例。
- **其它 dense 消费者**：`stage3_expectation.py`、`gen_dense_device_fixture.py` 等也调用 `atomic_layout()`，但本臂不要求它们写/读 `layoutSha256`；它们是否受影响未逐个核。

---

## 6. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 HEAD 与 porcelain；备份与回滚核验；§4.1/§4.2/§4.3 逐文件 post-sha；门 1–4 的 exit code 与关键输出；N1/N2（/N3）的实际 `failed` 列表、改后 ids 与三个摘要；基线摘要复算；旁车语义 diff（只一行）与字节数；`.vec`/主包/`VECTOR_ASSET_SHA256`/`packSha256`/`vectorCount` 零变实测；Python 与 Kotlin 摘要逐字符一致的证据。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
