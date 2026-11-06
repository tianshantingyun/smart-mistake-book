# R1：PDF 增量更新与 TC260 `/AIGC` 注入（批次 40，驱动 WS1 8 项）

- 日期：2026-11-02
- 性质：研究 + 本地可执行 PoC；**未改仓库代码、未运行 Gradle、未做 git 写操作**
- 输入：`.zcode/plans/plan-2026-11-02-batch-40-5x.md:8`、`:16-23`、`:83`；`docs/research/2026-10-25-ai-content-labeling-compliance.md:69`、`:156`、`:202-218`；`docs/research/2026-10-26-pdf-implicit-labeling.md:53-128`；`docs/known-defects.md:2844`（KD-75）
- 结论口径：**PDF 的 `/AIGC` 不是 PDF 子字典，而是 Info 字典里一个"值为 JSON 文本串"的条目**；增量更新是当前零依赖约束下唯一与官方载体一致的写入方式。本文给出可直接照做的字节格式、合并规则、fail-closed 分类与参考实现评分，并用本地 PoC 在表型/流型两种 xref 上验证。

---

## 0. 结论摘要

1. **`/AIGC` 的载体是 Info 字典条目，值是字符串**：TC260-PG-20258A §6.3 原文为 `/AIGC({"Label":"value1",...})`，附录 B.5 图 10 的官方 PDF 样例也是同一个 Info 对象里的 `/AIGC({...})`，与 `/Title`、`/Author` 并列（本会话下载官方 PDF，PDF 第 9 页正文第 4 页、PDF 第 17 页正文第 12 页；§8.1）。**不要**写成 `/AIGC <<...>>`，也**不要**写 XMP（XMP 是 20259A 的图片文件载体，不是 PDF 载体）。
2. **增量更新语义**：只追加，不改原字节；新 xref 段只含变更对象；新 trailer 复制上一 trailer 的全部条目（`/Prev` 除外）并加 `/Prev` 指向上一 xref 段；每个 trailer 自带 `%%EOF`；读者以最新副本为准（ISO 32000-1 §7.5.6）。
3. **两种 xref 必须都支持，且更新段要与上一段同型**：上一段是表就写表，是流就写流。pyHanko、pdf.js、PdfPig 三份参考实现都这样做（§6）。
4. **`startxref` 的语义分型**：表型指向 `xref` 关键字；流型指向 xref 流对象的 `N G obj` 头（ISO 32000-1 §7.5.5、§7.5.8.1）。`%%EOF` 必须是文件最后一行。
5. **对象身份是 `(对象号, 生成号)`**：重定义 Info 必须保留原 `/Info N G R` 的 `N` 与 `G`；新建对象用 `max(Size, 当前最高对象号+1)`、生成号 0；`Size = 最高对象号+1`。不能想当然用"对象号不变、生成号 0"。
6. **免费链表不参与本场景**：本注入只重定义/新增对象，不删除对象；表型不要重写对象 0 的 free 头（可能截断原空闲链表），流型可写规范 free 头也可从上一段继承。free 条目只在删除对象时写（§2.7）。
7. **Info 合并不覆盖**：对既有 Info 对象做 token-aware 字节拼接，把 `/AIGC` 插到最外层 `>>` 前；已有 `/AIGC` 则替换其值 token，绝不产生第二个键；Title/Author/Subject/Keywords/Creator/Producer/CreationDate/ModDate/Trapped 以及未知自定义键逐字节保留（§4；PoC 实测 pypdf 与 PyMuPDF 均读回原标题/作者）。
8. **`/AIGC` schema 建议 7 键全量**：`Label`、`ContentProducer`、`ProduceID`、`ReservedCode1`、`ContentPropagator`、`PropagateID`、`ReservedCode2`；后 4 个写空串；`Label="1"`、`ContentProducer="SmartMistakeBook"`、`ProduceID=inputSha256`；只允许 ASCII，写前/读回都校验（§3）。这与图片路径当前只写 3 字段（`GeneratedFigureMetadata.kt:167-180`）存在已登记的差异，建议统一为一个 builder。
9. **畸形 PDF 全部 fail-closed**：给出 36 行拒绝码表（非 `%PDF-` 头、缺/坏 `startxref`、坏偏移、坏 xref 条目、坏 trailer、缺 `/Root`、Info 非间接/非字典/截断/在对象流里、加密、线性化、hybrid、对象号超限、xref 流 W/Index/Filter/Length 非法等），失败时**不产出任何文件**；不静默降级为"无标识 PDF"（§5）。
10. **参考实现评分**：PdfPig 92 > pyHanko 87 > pdf.js 74（满分 100，维度与权重见 §6.5）。三者都无 JVM/Kotlin 版本；**只移植算法，不引依赖**，以 PdfPig 的 269 行 builder 为主蓝图、pyHanko 补严格读取/trailer 语义、pdf.js 补流型字节布局。
11. **PoC 实测**：表型 1302→1824 字节（+522）、流型 1223→1845 字节（+622）；原始前缀逐字节保留；pypdf 读回 `/AIGC` 与标题/作者；PyMuPDF 均 2 页；二次注入最新 Info 窗口只有 1 个 `/AIGC`；两种形态 `/ID` 第一段保留、第二段更新（§8）。
12. **两处必须先定的落点问题**：① 计划写"落 `core/data` pdf 包"，但 `core:export` 只依赖 `core:model`/`core:domain`（`core/export/build.gradle.kts:23-24`），没有 `core:data` 依赖；直接把注入器放 `core:data` 再接进 `MistakePdfExporter.prepare` 需要新增依赖（会把 database/ML/okhttp 拖进导出）或加接口 seam（§7.1）。② Android `PdfRenderer`/PDFium 对追加段的接受度本批**未跑设备**，是 WS1 设备门的必过项（§9）。

---

## 1. 任务、现状与边界

### 1.1 计划中的 8 项（WS1）

| # | 计划原文（`.zcode/plans/plan-2026-11-02-batch-40-5x.md:16-23`） | 本文对应 |
|---|---|---|
| 1 | 增量更新注入器核心：尾部 `startxref`/xref/trailer 解析 → 追加 Info 对象 + 新 xref 段 + `/Prev` trailer + `%%EOF`（纯 Kotlin 零依赖） | §2、§7.2 |
| 2 | xref **表型**（PDF 1.4 经典）支持 + 用例 | §2.3、§8.3 |
| 3 | xref **流型**（PDF 1.5+ `/Type /XRef`）支持 + 用例 | §2.5、§8.3 |
| 4 | 畸形 PDF fail-closed（非 `%PDF-` 头/缺 startxref/坏偏移/截断）四类有码拒绝 | §5（36 行码表） |
| 5 | `/AIGC` 字典 schema 按 TC260 实现 + 字段校验 | §3 |
| 6 | 「仅 AI 图」判据补**模型解析文本** | §3.7 |
| 7 | 既有 Info 字典合并不覆盖（Title/Author 保留）+ 幂等 | §4 |
| 8 | 字节级往返测试：`PdfRenderer` 可开、页数不变、Info 读回 `/AIGC`、原始字节前缀完整 | §7.4、§8.7（设备项未跑） |

### 1.2 当前代码事实（本会话直接读取）

| 事实 | 证据 |
|---|---|
| PDF 由 `android.graphics.pdf.PdfDocument`（Skia 后端）生成，不引任何 PDF 库 | `core/export/.../DeterministicMistakePdfRenderer.kt:62`（`val document = PdfDocument()`）、`:153`（`document.writeTo(buffered)`）、`:155`（`fd.sync()`） |
| 现有导出管线：render → 大小检查 → `PdfRenderer` 页数 → sha256 → publish | `core/export/.../MistakePdfExporter.kt:128`、`:130`、`:134`、`:135`、`:136` |
| 缓存键是 `inputSha256`，指纹前缀 `mistake-export-v3` | `core/export/.../MistakePdfEligibility.kt:185-186`；`inputSha256` 字段 `:329` |
| 导出输入没有块级 provenance 字段 | `MistakePdfExportInput` 字段表 `MistakePdfEligibility.kt:318-370`（只有 `blocks`、`questionDocumentSha256`、`inputSha256` 等） |
| 模型解析证据确实能进已提交文档 | `core/model/.../CapturedQuestionDocument.kt:17`（`blockEvidence` 字段）、`:210`（`MODEL_DOCUMENT_PARSE`）；parser 写入点 `core/data/.../OpenAiModelResponseParsers.kt:261` |
| 文档指纹覆盖整个文档（含 `blockEvidence`） | `CapturedQuestionDocument.kt:394-397`（`sha256(CapturedQuestionDocumentCodec.encode(value))`）、`:370`（`encodeDefaults = true`） |
| 图片路径已写 TC260 XMP，但只写 3 字段 | `core/data/.../capture/GeneratedFigureMetadata.kt:154`（命名空间）、`:157`（Label=1）、`:164`（ContentProducer）、`:167-180`（只写规范必填三字段的说明与 `aigcJson`） |
| 逐图显式图注判据是角色单源 | `core/export/.../AiGeneratedImageCaption.kt:27`（固定文案）、`:30-36`（`isAiGeneratedExportImage`：`null`/`CLEAN_IMAGE`/`TUTOR_EXPLANATION` 为 true，其余 false） |
| 打印部分页会重绘并丢 Info | `core/export/.../MistakePdfDelivery.kt:215`（`writeSubset`）、`:227`（新 `PdfDocument()`）、`:259`（`writeTo`） |
| 当前没有注入器、没有 `core/.../pdf` 包 | `find core -type d -name pdf` 无输出；`grep -rln "PdfAigc\|AIGC" core/export/src` 零命中（`AIGC` 只在 `core/data` 的图片路径与其测试里） |
| `core/export` 当前仍有他线改动 | `git status --porcelain core/export` → 3 个测试 `M` + 未跟踪 `gradle.lockfile`；**整目录按禁改热区对待** |
| KD-75 仍 open，口径是"不得声称合规，也不得声称法规未要求" | `docs/known-defects.md:2844` |

### 1.3 范围边界

- **只做 PDF 文档级隐式标识**（Info `/AIGC`），不做 DOCX 的 `docProps/custom.xml`（20258A §6.1 的对称缺口，另立条目）。
- **不写 XMP**（理由 §3.6）。
- **不引第三方 PDF 库**（理由 §6.6）。
- **不改 UI**、不改可见图注；图注是《办法》第四条第二款的显式义务，`/AIGC` 是第五条的隐式义务，两者并列，不能互相替代（既有研究 `2026-10-26-pdf-implicit-labeling.md:14` 已给出条文链）。
- **不修改签名/加密/线性化 PDF**；遇到即拒绝（§5.3）。

---

## 2. 增量更新规范细节（ISO 32000-1）

> 依据：Adobe 公开的 ISO 32000-1:2008 全文 PDF（本会话下载，22,491,828 字节，sha256 `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff`，§10）。条款号与页码为 PDF 原文；PDF 2.0（ISO 32000-2:2020）未取得全文，本文机制以 1.7 为准，PDF 2.0 差异列为待验证（§9）。

### 2.1 文件结构与更新语义

ISO 32000-1 §7.5.1：基本 PDF 由四部分组成——头、体、交叉引用表、trailer；后续更新"在文件末尾追加额外元素"（§7.5.1、§7.5.6）。

§7.5.6 原文要点：

- "When updating a PDF file incrementally, changes shall be appended to the end of the file, leaving its original contents intact."
- 更新段的 xref "shall contain entries only for objects that have been changed, replaced, or deleted."
- 删除对象"shall be left unchanged in the file, but shall be marked as deleted by means of their cross-reference entries."
- 新 trailer "shall contain all the entries except the Prev entry (if present) from the previous trailer, whether modified or not"，并额外含 `Prev` 指向上一交叉引用段。
- "Each trailer shall be terminated by its own end-of-file (`%%EOF`) marker."
- 同一 `(对象号, 生成号)` 可以在文件中出现多份；读者构建 xref 时"the most recent copy of each object shall be the one accessed from the file."

**对本项目的直接含义**：注入只追加一个 Info 对象的新副本 + 一个 xref 段 + 一个 trailer；原 `%%EOF` 留在文件中段是合法的；不要重写原文件、不要重排对象、不要改页树。

### 2.2 `startxref` 与 `%%EOF`

§7.5.5：

- "The last line of the file shall contain only the end-of-file marker, `%%EOF`."
- 前两行依次是 `startxref` 和"the byte offset in the decoded stream from the beginning of the file to the beginning of the `xref` keyword in the last cross-reference section."
- trailer 字典在 `trailer` 关键字之后，结构为 `trailer << ... >> startxref <offset> %%EOF`。

§7.5.8.1（流型）：

- "The value following the `startxref` keyword shall be the offset of the cross-reference stream rather than the `xref` keyword."
- 示例中 `startxref` 指向 `12 0 obj` 的对象头。

**实现规则**：

| 上一段类型 | `startxref` 指向 | 校验 |
|---|---|---|
| 表型 | `xref` 关键字的第一个字节 | 该偏移处必须精确匹配 `xref` |
| 流型 | xref 流对象的 `N G obj` 头 | 该偏移处必须能解析出 `N G obj`，且字典含 `/Type /XRef` |

- 偏移是**从文件第一个字节起算的绝对字节偏移**，不是行号、不是对象内偏移。
- 查找最后一个 `startxref` 时从文件尾向前搜；建议窗口 `min(64 KiB, size)`。不要取第一个匹配（增量更新后文件里有多个 `startxref`）。
- 追加前若原文件最后一个字节不是 `\n`/`\r`，先补一个 EOL，避免 `%%EOF` 与 `N 0 obj` 粘连（pdf.js 与 PdfPig 都这样做；见 §6）。
- `startxref` 的数字后面允许 EOL；`%%EOF` 之后允许尾随空白；若原文件尾随非空白垃圾，仍可追加（我们的新 `startxref`/`%%EOF` 会成为最后的），但应在诊断里记一笔。

### 2.3 xref 表型（经典表）

§7.5.4 原文要点：

- 每个 xref 段以 `xref` 行开始，后接一个或多个子段；子段头是两个整数：`起始对象号 条目数`。
- 每个条目**恰好 20 字节（含 EOL）**：`nnnnnnnnnn ggggg n eol` 或 `nnnnnnnnnn ggggg f eol`。
  - `nnnnnnnnnn`：10 位十进制字节偏移，前导零补齐；
  - `ggggg`：5 位生成号，前导零补齐；
  - `n` = in-use，`f` = free；
  - `eol` = `SP CR`、`SP LF` 或 `CR LF` 之一（因此条目总长恒为 20）。
- 同一对象号在一个段内不得出现在多个子段。
- 子段可任意顺序。
- 表（原始段 + 所有更新段）应为 0 到最大对象号的每个对象号都有一条记录。

**追加段的写法**（只重定义一个对象时）：

```
xref
<N> 1
<10 位偏移> <5 位生成号> n \n
```

- 只写变更对象；不要把整个表重抄一遍。
- 不要写对象 0（理由 §2.7）。
- 表型更新段**不需要** `trailer` 之外的关键字；`trailer` 紧跟最后一个子段。

### 2.4 trailer 字典

§7.5.5 Table 15：

| 键 | 类型 | 语义 | 本注入处置 |
|---|---|---|---|
| `Size` | integer（不得为间接引用） | 交叉引用表总条目数，等于最高对象号 + 1 | 重算：`max(上一 Size, 新最高对象号+1)`；新建对象时必增 |
| `Prev` | integer（**原文 prose 写"shall be an indirect reference"，与 Type 列 integer 自相矛盾**） | 上一交叉引用段的字节偏移 | 写**直接整数**；三份参考实现均写直接整数（§6），PoC `/Prev 934` 被 pypdf/MuPDF 接受 |
| `Root` | dictionary（间接引用） | Catalog | 必须存在且为 `N G R`；缺 → fail-closed |
| `Encrypt` | dictionary | 加密字典 | 存在即拒绝（§5.3） |
| `Info` | dictionary（间接引用） | 文档信息字典 | 保留/新建/重指向（§4） |
| `ID` | array（两个 byte string） | 文件标识 | 有则第一段保留、第二段更新；无则省略（§2.9） |
| `XRefStm` | integer | hybrid-reference 文件的 xref 流偏移 | 存在即拒绝（§5.3） |

**必须复制上一 trailer 的全部条目（`Prev` 除外）**，包括未知自定义键；这是 §7.5.6 的明文要求。pdf.js 只复制 `Root/Info/Encrypt`（见 §6.3），是它的一个缺口，不要照抄。

### 2.5 xref 流型（PDF 1.5+）

§7.5.8：

- xref 流是**流对象**，字典承担 trailer 职责，流数据是条目。
- 字典键（Table 17）：
  - `/Type /XRef`（必需）；
  - `/Size`（必需；"one greater than the highest object number used in this section or in any section for which this shall be an update"）；
  - `/Index`（可选；默认 `[0 Size]`；每对整数 = 起始对象号、条目数；按对象号升序、不得重叠）；
  - `/Prev`（仅当有上一 xref 流；语义同 trailer 的 `Prev`）；
  - `/W`（必需；三个整数，分别是 type/field2/field3 的字节宽度；PDF 1.5 恒为 3 个）；
  - 另可带 `/Filter`、`/DecodeParms`、`/Length` 等流字典键。
- 流数据条目（Table 18）：
  - type 0（free）：field2 = 下一个空闲对象号，field3 = 以后复用时的生成号；
  - type 1（in-use，未压缩）：field2 = 字节偏移，field3 = 生成号；
  - type 2（compressed）：field2 = 对象流对象号，field3 = 对象流内索引。
- 多字节字段**大端**；`W` 某一位为 0 表示该字段不出现，第一字段为 0 时 type 默认 1。
- 流本身必须是间接对象，并且它自己也应在某个 xref 段里有条目（通常是它自己）。
- `startxref` 指向流对象头，不是 `xref` 关键字。

**本注入的流型写法**：

- 新段只含：变更的 Info 对象（type 1）、xref 流对象自身（type 1）；可选对象 0（type 0，`next=0, gen=65535`）。
- `W` 建议 `[1, offsetWidth, genWidth]`；本项目文件 ≤ 24 MB，`offsetWidth=4`、`genWidth=2` 足够；实现应按最大值动态算宽度，不能硬编码 4/2 到未来大文件。
- `Index` 只列实际条目，按对象号升序分组；例如 `[10 1 14 1]`。
- `Filter` 可用 `/FlateDecode`（用 `java.util.zip.Deflater`，零新依赖）；也可不压缩（pdf.js 就不压缩）。**不要**用 PNG predictor 写，除非同时实现 predictor 读；写不压缩或纯 Flate 最稳。
- 读入既有 xref 流时必须支持 `/DecodeParms /Predictor 12`（PNG Up 等）。本 PoC 的流型夹具没有 predictor，**未覆盖**；生产实现必须补，否则大量真实 PDF 1.5+ 文件会读不出。
- `/Length` 可能是间接引用（Table 5）；生产实现要么沿 xref 解析，要么对间接 `/Length` fail-closed；本 PoC 只支持直接 `/Length`。

### 2.6 对象编号与生成号

- 对象身份 = `(对象号, 生成号)`；xref 条目的 `ggggg` 是生成号。
- 除对象 0 外，对象初始生成号为 0；对象被删除时其 xref 条目标 free，生成号 +1；生成号到 65,535 后不再复用（§7.5.4）。
- **重定义既有对象**：保留原 `(N, G)`，只改偏移；读者按最新 xref 段取该 `(N, G)` 的新副本。若把生成号改成 0 而原引用是 `N 2 R`，那是另一个对象，`/Info` 仍指向旧对象，标签不会生效。
- **新建对象**（原文件无 `/Info`）：`N = max(上一 Size, 当前最高对象号 + 1)`，`G = 0`；`Size = N + 1`；trailer `/Info N 0 R`。
  - 为什么不能只用 `Size`：PdfPig 的测试专门覆盖了"`/Size` 偏小、实际对象号更大"的文件（linearized 样本 `/Size 9` 但对象到 29），只用 `Size` 会撞号（§6.4）。用 `max(Size, highest+1)` 才安全。
- **上限**：ISO 32000-1 Annex C 规定间接对象数上限 8,388,607；超过即 fail-closed。
- **不要复用 free 对象号**：新对象取 `max(Size, highest+1)` 天然避开 free 链表；若复用，必须同时维护 free 链表头和生成号，本场景不需要。

### 2.7 免费链表（free list）

§7.5.4 原文：

- 对象 0 永远是 free，生成号 65,535，是 free 链表头；链表尾指回 0。
- 第二种机制：其他 free 条目也可以直接指回 0、生成号 65,535，但不在链表里。
- 删除对象时：条目标 free，生成号 +1，加入链表；复用时用新生成号。

**本注入的处置**：

- **不删除任何对象**，所以新 xref 段**不写任何 free 条目**。
- **不要重写对象 0**：原文件的 free 链表头可能指向某个空闲对象；用一个 `0 1` 子段 + `0000000000 65535 f` 覆盖会把它改成"指向自己"，截断原链表。对本文件后续不再复用 free 对象，实际影响小，但这是无谓的语义破坏。
- 如果原文件的对象 0 条目缺失/非法：fail-closed `XREF_FREE_HEAD_MISSING`，不要"顺手修"。
- 流型里 PdfPig 会写对象 0（`(0, 0, 0, 65535)`，§6.4），本 PoC 的流型也写了并通过（`/Index [0 1 10 1 13 1]`，§8.5）。生产建议：**表型不写；流型可写可不写**——写则用规范 free 头，不写则从上一段继承；两种都不得破坏原 free 链表。

### 2.8 Info 字典与字符串编码

§14.3.3：

- Info 是 trailer 的可选 `/Info` 条目，值必须是**间接引用**的字典。
- "Any entry whose value is not known should be omitted from the dictionary rather than included with an empty string as its value."（这条约束的是 **Info 字典的键**，不是 `/AIGC` JSON 的内部字段；§3.3 会用到这个区分。）
- "The value associated with any key not specifically mentioned in Table 317 shall be a text string." → **`/AIGC` 必须是 text string**，不能是字典/数组/名字。
- 标准键：`Title`、`Author`、`Subject`、`Keywords`、`Creator`、`Producer`、`CreationDate`、`ModDate`、`Trapped`。

字符串语法（§7.3.4.2/§7.3.4.3、§7.9.2.2）：

- 字面串 `(...)`：`\`、未配对的 `(`/`)` 必须转义；`\n \r \t \b \f \( \) \\ \ddd`；JSON 的双引号 `"` 在 PDF 字面串里**不需要**转义（官方图 10 就是直接写的）。
- 十六进制串 `<...>`：任意字节；奇数位末位补 0。
- text string 编码：PDFDocEncoding 或 UTF-16BE（前两字节 `FE FF`）。
- 本项目的值全部是 ASCII（Label/生产者名/64 位 hex），用字面串即可；**非 ASCII 建议直接拒绝**，不要引入"UTF-16BE 包 JSON、检测器却按 UTF-8 解析"的互操作风险（§3.4）。

### 2.9 文件标识 `/ID`

§14.4：

- `ID` 是两个 byte string 的数组；第一段是文件创建时的永久标识，"shall not change when the file is incrementally updated"；第二段是"changing identifier based on the file's contents at the time it was last updated"；首次写入时两段相同。
- 有 `Encrypt` 时 `ID` 必填且不得加密；本场景拒绝加密，所以 `ID` 可选。

**处置**：

- 原 trailer 有 `/ID` 且是两元素数组：第一段**原样**保留，第二段用新内容的摘要（建议 SHA-256 截断 16 字节；不要用 MD5）。
- 原 trailer 无 `/ID`：**省略**（Skia 默认无 `/ID`，见 §2.11；不必新增）。
- 原 `/ID` 不是两元素数组：fail-closed `ID_MALFORMED`（不要"修"）。

### 2.10 加密、线性化、签名的影响

| 情形 | 影响 | 处置 |
|---|---|---|
| `/Encrypt` 存在 | Info 字符串必须用文档密钥加密；明文写入会破坏/不可读 | 拒绝 `ENCRYPTED` |
| `/Linearized` 存在 | 增量更新后不再是线性化文件，hint 表/偏移失效；规范允许但产品不需要 | 拒绝 `LINEARIZED` |
| hybrid-reference（trailer 有 `/XRefStm`） | 更新段要么同时写表和流，要么正确处理 `XRefStm` 优先级；复杂度不成比例 | 拒绝 `HYBRID_XREF` |
| 数字签名 / DocMDP | 追加修订可能使认证签名失效；签名语义需按签名策略判断 | 本产品产物无签名；遇到 `/Perms` 或签名域不在本批范围，登记为残留 |
| 对象流里的 Info（xref type 2） | 不能做字节拼接；要重写整个对象流 | 拒绝 `INFO_IN_OBJECT_STREAM` |

### 2.11 本项目产物的最简形态

Skia 的 PDF 写入器（本会话核对 `google/skia` main）：

- 写经典 xref 表：`s->writeText("xref\n0 ")`（`src/pdf/SkPDFDocument.cpp:104`）；
- trailer 插 `/Root`、`/Info`（`:153`、`:155`）；
- `startxref` + `%%EOF`（`:161`、`:163`）；
- Android `PdfDocument` 没有 metadata API（AOSP `graphics/java/android/graphics/pdf/PdfDocument.java` 只有 `startPage/finishPage/writeTo/close`，无 `metadata` 命中）；
- Skia 默认 `Producer = "Skia/PDF mXX"`，只写非空标准键；默认无 `/ID`、无 XMP、无加密、无线性化（前次研究 `2026-10-26-pdf-implicit-labeling.md:144-153` 已逐行核对，本会话复核了 xref/trailer 行号）。

因此我们的输入契约是：**经典 xref 表 + 必有 `/Info` + 无 `/ID` + 无 `/Encrypt` + 无 `/Linearized`**。注入器仍应对通用形态（有 ID、流型、无 Info）给出确定行为，但不在本项目产物上赌"一定是 1.4 表型"。

---

## 3. `/AIGC` 结构与字段

### 3.1 官方条文（本会话一手核对）

**TC260-PG-20258A《网络安全标准实践指南——人工智能生成合成内容标识方法 文件元数据隐式标识 文本文件（V1.0-202508）》**

- 官方 PDF：`https://www.tc260.org.cn/tc260/sjzn/202508/f0a84ada0c11458e9cb6da56c1cb2426/files/760387fb30e94c058c31b8c2b2230020.pdf`
- 本会话 `curl` HTTP 200，3,272,625 字节，PDF 1.7，20 页；sha256 `6bf6c1189208856099bfbf648bdf386da288b1df00f430502b395b874dbf3b0e`（与既有研究 `2026-10-26-pdf-implicit-labeling.md:57` 一致）。
- §6.3（PDF 第 9 页，正文第 4 页）原文（PyMuPDF 提取，保留换行）：

```
6.3 基于PDF 的文本文件
基于PDF 的文本文件，方案如下：
a) 存储位置为Document Information Dictionary;
b) 写入方式为：
/AIGC({"Label":"value1","ContentProducer":"value2","ProduceID":"val
ue3","ReservedCode1":"value4","ContentPropagator":"value5","Propagat
eID":"value6","ReservedCode2":"value7"})。
```

- 附录 B.5（PDF 第 17 页，正文第 12 页）图 10 的官方样例（本会话把该页渲染为图片逐字读取）：

```
172 0 obj
<< /ModDate (D:20250407152043+08'00') /Trapped (False) /Producer ()
/AIGC({"Label":"value1","ContentProducer":"value2","ProduceID":"value3","ReservedCode1":"value4","ContentPropagator":"value5","PropagateID":"value6","ReservedCode2":"value7"})
/SourceModified (D:20250217152418+08'00') /Comments () /Title (标准文档) /Keywords()
/Author (wps) /Creator (WPS 文字) /CreationDate (D:20250217152418+08'00') /Company()>>
endobj
```

**三条硬事实**：

1. `/AIGC` 与 `/Title`、`/Author` 在**同一个 Info 对象**里 —— 官方样例本身就是"合并不覆盖"的形态。
2. 值是 `(...)` 字面串，不是 `<<...>>` 字典；JSON 的双引号未转义。
3. 官方样例写全 7 个键；`value1..value7` 是占位符，不是要求照抄的字面值。
4. 仓内合规文档 `2026-10-25-ai-content-labeling-compliance.md:69` 已列同一 7 字段结构（`Label / ContentProducer / ProduceID / ReservedCode1 / ContentPropagator / PropagateID / ReservedCode2`）；本文把它用于 **PDF Info 载体**，字段名一致、载体不同（该文档当时讨论的是图片 XMP）。

**20259A（图片文件指南）**：本会话复核下载，2,190,188 字节，11 页，sha256 `e4f7fe820a181b2e24a5e3214b751e67c04672ec68fb4d66484da4e77b02a02f`；其 §6 规定图片文件的 XMP 载体与 `TC260:AIGC` 命名空间，**格式清单不含 PDF**（前次研究 `2026-10-26-pdf-implicit-labeling.md:71-86` 已逐条摘录，本会话复核了 hash）。

### 3.2 字段表与语义

| JSON 键 | 本注入建议值 | 语义/来源 | 校验 |
|---|---|---|---|
| `Label` | `"1"` | 内容含 AI 生成合成内容；`"2"/"3"` 是传播平台的"可能/疑似"语义，本方不写 | 必须等于 `"1"`；读回允许 `1/2/3` 但测试断言 `1` |
| `ContentProducer` | `"SmartMistakeBook"` | 服务提供者名称口径（《办法》第五条允许"名称或者编码"；27 位编码口径未证，不采） | 非空、ASCII、`[A-Za-z0-9 ._-]{1,128}` |
| `ProduceID` | `input.inputSha256`（64 位 hex） | 内容编号，可对账到导出输入指纹；是哈希，不是学号/用户 ID | `[a-f0-9]{64}`（本项目）；非空、ASCII |
| `ReservedCode1` | `""` | 预留防篡改位；本方无数据 | 必须存在，字符串，可空 |
| `ContentPropagator` | `""` | 传播平台名称/编码；本方是生成方，不是传播平台 | 必须存在，字符串，可空 |
| `PropagateID` | `""` | 传播平台内容编号；同上 | 必须存在，字符串，可空 |
| `ReservedCode2` | `""` | 预留 | 必须存在，字符串，可空 |

- 键顺序固定为模板顺序：`Label, ContentProducer, ProduceID, ReservedCode1, ContentPropagator, PropagateID, ReservedCode2`。
- 不写额外键；不允许嵌套对象/数组；值全部是字符串。
- `ProduceID` 的粒度：图片路径用 `figureId`（`GeneratedFigureMetadata.kt:176-180`），PDF 用 `inputSha256`（整份导出输入）；两者都是"内容编号"，但一个是图级、一个是文档级，不要互相替换。
- 空串的语义是"该要素不适用/无数据"，不是"未知"；这与 PDF §14.3.3 的"未知 Info 键应省略"不冲突（那条约束的是 Info 字典键，不是 JSON 内部字段）。

### 3.3 3 字段 vs 7 字段：裁定与理由

**现状冲突**：

- 官方 §6.3 模板与附录 B.5 图 10 都是 **7 键**。
- 仓内图片路径 `GeneratedFigureMetadata.kt:167-180` 明确"**只写规范必填三字段**"（Label/ContentProducer/ProduceID），理由是传播要素/预留位属于传播平台/预留语义；读回 `parseAigcXmpPacket` 也只要求三字段（`:223-225`）。
- 这是既有登记差异（前次研究 `2026-10-26-pdf-implicit-labeling.md:408` 的 U4；合规文档 `2026-10-25-ai-content-labeling-compliance.md:156` 的 G4）。

**本文裁定（建议，供 WS1/主会话确认）**：**PDF 路径写 7 键全量，后 4 键空串。**

理由：

1. 官方 PDF 样例（图 10）逐字写全 7 键；按模板同形写入，检测器按固定键集解析时不会缺键。
2. PDF §14.3.3 的"未知值省略"约束的是 Info 字典的键；`/AIGC` 是一个 text string，内部 JSON 由 TC260 定义，7 个键都是 schema 里的已定义键，空串是"已定义但无值"，不是"未知键"。
3. 传播/预留字段空串在语义上诚实：本 App 不是传播平台，也没有预留数据；没有冒充 `ContentPropagator`。
4. GB 45438-2025 附录 E 的"是否允许省略 4 个可选字段"**仍未取得全文**（§9-U1）。在无法证明"可省略"之前，7 键是更保守的默认；若将来证明可省略，改写 3 键只需改一个 builder，不影响注入器字节逻辑。

**反方意见（必须记录）**：若 GB 附录 E 明确这 4 个字段是"条件可选"且空串不被接受，则应改为**省略**而不是空串；这需要标准原文，不能靠二手转述定。裁定前不要把 7 键写成"合规已证"，只能写"按官方模板同形"。

**单一事实源**：无论最终 3 还是 7，**必须只有一个 AIGC JSON builder**，图片路径与 PDF 路径共用，禁止两套字符串拼接漂移。图片路径的 builder 在 `core:data` 的 `capture` 包（`GeneratedFigureMetadata.kt:176`）；若 PDF 注入器也落 `core:data`，可直接复用；若落 `core:export`，需要把 builder 移到共享层（`core:model`）或通过接口暴露，不能复制（§7.1）。

### 3.4 值编码、转义与校验

**写前校验（fail-closed，任一不满足即拒绝）**：

1. JSON 能解析为对象，且**恰好** 7 个键、无重复、无额外键。
2. `Label == "1"`。
3. `ContentProducer` 非空、ASCII、正则 `[A-Za-z0-9 ._-]{1,128}`。
4. `ProduceID` 非空、ASCII、本项目为 `[a-f0-9]{64}`。
5. 其余 4 键存在且为字符串（允许空）。
6. 所有值 ASCII（0x20–0x7E）；**不允许**控制字符、换行、`\`、未配对括号。JSON 的 `"` 合法（PDF 字面串不需要转义）。
7. 序列化后总长 ≤ 1024 字节（本项目约 200 字节）。
8. 再做一次 PDF 字面串转义：`\` → `\\`、`(` → `\(`、`)` → `\)`；控制字符用 `\ddd`；虽然校验已排除，仍保留转义作为纵深防御。

**为什么拒绝非 ASCII**：官方样例是 ASCII；若把 JSON 用 UTF-16BE（`FE FF`）包进 PDF text string，PDF 读者能读，但 TC260 检测器是否按 UTF-16BE 再解析 JSON **未验证**；不要引入这个互操作风险。未来若 `ContentProducer` 需要中文，先做检测器兼容性验证，再改。

**读回校验**（用于幂等/测试）：

1. 从 Info 字典取 `/AIGC` 的字符串 token（字面串或 hex 串）。
2. 解码：hex → bytes；字面串 → 反转义；若以 `FE FF` 开头按 UTF-16BE，否则按 ASCII/PDFDocEncoding。
3. JSON 解析；校验 7 键与 `Label`。
4. 解析失败 → 视为"无可读标签"（测试失败/诊断），不要静默当成"无 AI 内容"。

### 3.5 `/AIGC` 与 XMP 的关系

| 载体 | 适用 | 本注入 |
|---|---|---|
| Info 字典 `/AIGC` | **PDF 文本文件**（20258A §6.3） | 做 |
| XMP `TC260:AIGC` | **图片文件**（20259A §6：JPEG APP1、PNG iTXt、TIFF IFD0 等） | 不做 |
| DOCX `docProps/custom.xml` 的 `AIGC` 属性 | OOXML 文本文件（20258A §6.1） | 不在本批 |

### 3.6 不写 XMP 的理由

1. 官方 PDF 载体就是 Info 字典；写 XMP 不是"更合规"，而是换了载体。
2. Skia 产物默认没有 `/Metadata` 流（前次研究 `2026-10-26-pdf-implicit-labeling.md:150`）；要写 XMP 必须重定义 Catalog + 新增流对象 + 追加 xref，工作量和风险约 2 倍。
3. XMP 不消灭任何 Info 方案未消灭的失败：合规检测读的是 Info `/AIGC`；图片本身的 XMP 已在重绘进 PDF 时丢失，PDF 级补 XMP 也不会让图片恢复 XMP。
4. `core/export` 是热区，最小改动优先。

### 3.7 写入判据：AI 图 + 模型解析文本

**判据必须同时覆盖两类内容**（KD-75 第二半，`docs/known-defects.md:2844`）：

```kotlin
// 概念签名；实际落点见 §7.1
internal fun containsAiGeneratedExportContent(input: MistakePdfExportInput): Boolean =
    input.cleanImageLocalUri != null && isAiGeneratedExportImage(input.questionImageRole) ||
        input.blocks.filterIsInstance<MistakePdfBlock.Image>()
            .any { isAiGeneratedExportImage(it.role) } ||
        input.solutionImages.isNotEmpty() ||
        input.hasModelParsedText
```

- `isAiGeneratedExportImage` 复用 `AiGeneratedImageCaption.kt:30-36`，不要新写一套角色判断。
- `solutionImages` 只允许 `TUTOR_EXPLANATION`（`MistakePdfExportInput` 的 `init` 已校验），非空即 AI。
- `QUESTION_DIAGRAM` 且没有其它 AI 内容 → **不写** `/AIGC`（避免《办法》第十条"伪造标识"）。
- `MistakePdfBlock.Figure`（程序化 JS 图形）→ **不写**（确定性代码绘制，不是模型推理；合规文档 `2026-10-25-ai-content-labeling-compliance.md:92` 的判定）。
- 纯用户录入/OCR 转录/备注 → 不写。

**模型解析文本的数据流缺口**：

- `CapturedQuestionDocument.blockEvidence`（`core/model/.../CapturedQuestionDocument.kt:17`）里有 `QuestionBlockProvenance.MODEL_DOCUMENT_PARSE`（`:210`）；parser 在每个块上写该值（`core/data/.../OpenAiModelResponseParsers.kt:261`）。
- 提交校验只要求 `reviewStatus ∈ {LOCAL_POLICY_ACCEPTED, USER_CONFIRMED}`（`CapturedQuestionDocument.kt:358-361`），**不要求** provenance 改变，所以已提交文档仍可能带模型解析证据。
- `MistakePdfEligibility` 只把 `blocks` 放进 `MistakePdfExportInput`（`:170`），输入类没有 provenance 字段（`:318-370`）。
- 因此必须在资格层派生一个布尔值（如 `hasModelParsedText = document.blockEvidence.any { it.provenance == MODEL_DOCUMENT_PARSE }`）并随输入传递。**不要**在导出层重新读文档（它拿不到 `blockEvidence`）。
- 指纹：`CapturedQuestionDocumentCodec.encode` 用 `encodeDefaults = true`，`CapturedQuestionDocumentFingerprint` 对整份文档（含 `blockEvidence`）做 SHA-256（`CapturedQuestionDocument.kt:370`、`:394-397`）；`documentFingerprint` 已进入 `inputSha256`（`MistakePdfEligibility.kt:185-186`）。所以 **provenance 变化已经改变缓存键**，不需要再单独把布尔值拼进指纹；但 **标签 schema 版本必须进指纹**（v3→v4，§7.3）。
- 判据的保守性：只要证据里出现过 `MODEL_DOCUMENT_PARSE` 就打标，即使后来被用户修正。这会偶尔"多标"；多标一个 `Label=1` 的风险低于漏标。若将来 `QuestionBlockEvidence` 定义了"最终 provenance"优先级，再收紧。

---

## 4. 既有 Info 字典的合并（不覆盖 Title/Author）

### 4.1 合并语义矩阵

| 原状态 | 行为 | 备注 |
|---|---|---|
| trailer 无 `/Info` | 新建对象 `N 0 obj << /AIGC (...) >> endobj`；`N = max(Size, highest+1)`；trailer 加 `/Info N 0 R`；`Size = N+1` | 官方样例没有这种情形，但规范允许 `/Info` 可选 |
| `/Info` 是间接引用，对象是字典 | 在最外层 `>>` 前插入 `/AIGC (...)`；已有 `/AIGC` 则替换其值 token | 主路径 |
| `/Info` 是直接字典 | 拒绝 `INFO_NOT_INDIRECT` | §14.3.3 要求间接引用 |
| `/Info` 指向 free/缺失对象 | 拒绝 `INFO_OBJECT_MISSING` | 不猜、不扫全文件找"像 Info 的对象" |
| `/Info` 对象在对象流里（xref type 2） | 拒绝 `INFO_IN_OBJECT_STREAM` | 本产品产物没有对象流 |
| Info 对象不是字典（如 stream） | 拒绝 `INFO_NOT_DICTIONARY` | |
| Info 字典已有 `/AIGC`，值是字符串 | 替换值；同值则**不追加**（no-op） | 幂等 |
| Info 字典已有 `/AIGC`，值不是字符串 | 拒绝 `AIGC_VALUE_NOT_STRING` | 不静默覆盖别人的结构 |
| Info 字典截断/括号不配对 | 拒绝 `INFO_OBJECT_TRUNCATED` | |

### 4.2 字节拼接算法（token-aware）

不要用"从 `obj` 到 `endobj` 整段重写 + 正则找 `>>`"：Info 的字符串值里可能出现 `>>`、`)`、`(`。正确做法：

1. 从 xref 链取 `/Info` 的 `(N, G)`，再取该对象的字节偏移；校验偏移处是 `N G obj`（生成号也要匹配）。
2. 从 `obj` 后跳过空白，要求 `<<`。
3. 用 tokenizer 扫描字典：
   - 遇到 `(` → 按字面串规则跳到配对的 `)`（处理 `\` 转义与嵌套括号）；
   - 遇到 `<` 且下一个不是 `<` → 跳到 `>`（hex 串）；
   - 遇到 `<<` → 深度 +1；遇到 `>>` → 深度 -1；深度归 0 的位置就是外层字典结束；
   - 遇到 `%` → 跳到行尾（注释）；
   - 其他 token 正常推进。
4. 记录每个键的 `key_start`、值 token 的 `value_start/value_end`。
5. 若已有 `/AIGC`：
   - 值是字面串/hex 串 → 用新值 token 替换 `[value_start, value_end)`；
   - 其他类型 → 拒绝。
6. 若无 `/AIGC`：在 `dict_end - 2`（外层 `>>` 前）插入 `\n/AIGC <new token>\n`。
7. 新对象字节 = `原对象头到 dict_start` + 修改后的字典体 + `\nendobj\n`。
   - 这样 Title/Author/Subject/... 以及未知键的原始字节（含它们的编码、空白、转义）**逐字节保留**；对象 `>>` 与 `endobj` 之间的尾部空白/注释不保留（不属于字典内容）。
8. **不要重新序列化整个字典**：重新序列化会改变字符串编码、键顺序、空白，违反"不覆盖"的可验证性。

**为什么不用"新对象 + trailer `/Info` 指向新对象，只复制 Title/Author"**：那仍然要解析并重写所有值，而且会丢掉未知自定义键；字节拼接更小、更可证。只有"原 Info 解析失败"时才考虑回退，但本设计选择 fail-closed，不静默降级。

### 4.3 幂等

三种强度，按强弱排列：

| 强度 | 行为 | 测试 |
|---|---|---|
| 语义幂等 | 二次注入不产生第二个 `/AIGC` 键；最新 Info 只有一个 `/AIGC` | PoC 已验证（§8.4） |
| 字节幂等 | 若目标 JSON 与已有值**完全相同**，直接返回原字节（不追加修订） | 生产建议；测试 `inject(inject(x)) == inject(x)` |
| 内容幂等 | 若值不同，替换值并追加新修订；旧修订留在文件中，最新生效 | 测试读回最新值 |

建议实现"相同值 no-op、不同值替换"。这样重复导出同一输入不会让文件无限增长，摘要也稳定。

### 4.4 对象号、生成号、Size 与 trailer 指针

| 场景 | 对象号 | 生成号 | `Size` | trailer |
|---|---|---|---|---|
| 重定义既有 Info | 原 `N` | 原 `G`（不是 0） | `max(上一 Size, N+1)` | `/Info` 原值不变 |
| 新建 Info | `max(上一 Size, highest+1)` | 0 | `N+1` | 加/改 `/Info N 0 R` |
| 其它对象 | 不动 | 不动 | 不动 | 不动 |

- **生成号必须来自 `/Info` 的引用**，不是对象头"碰巧"的 0。若引用是 `10 2 R`，新对象写 `10 2 obj`。
- `Size` 是"最高对象号 + 1"，不是"条目数"；重定义不增加最高号时 `Size` 不变。
- 如果原 `Size` 偏小（< 实际最高+1），新 `Size` 用 `max(上一 Size, 本次最高+1)`，顺手修正但不依赖它。

### 4.5 ModDate 的处置

- **不修改**既有 `/ModDate`：修改它会覆盖原有值，违反"不覆盖"；而且 TC260 没有要求。
- 原文件没有 `/ModDate` 时也**不要**新增：官方样例有，但不是 `/AIGC` 的必需条件；新增会让字节变化更大，且日期格式/时区容易出错。
- 如果产品要求"导出时间"可追溯，应通过 `ProduceID`/日志，而不是改 Info 的 ModDate。

### 4.6 反例（不要做）

| 反例 | 后果 |
|---|---|
| 新建一个只含 `/AIGC` 的对象，trailer `/Info` 指向它 | Title/Author 全丢，违反需求 |
| 重写整个 Info 字典，按标准键重新序列化 | 未知自定义键丢失、字符串编码变化、Title/Author 字节变 |
| 用正则找最后一个 `>>` 插入 | 字符串值含 `>>` 时插错位置，PDF 损坏 |
| 二次注入再追加一个 `/AIGC` 键 | 同一字典两个同名键，读者行为不确定 |
| 用对象号不变、生成号 0 重定义 `N 2 R` | 标签不生效（对象身份不匹配） |
| 追加后不校验 `PdfRenderer` 就发布 | 可能发布损坏 PDF，且缓存/摘要已固化 |
| 注入失败时降级为无标识 PDF | 违反《办法》第五条，且制造"看起来成功"的假象 |

---

## 5. 畸形 PDF 的 fail-closed 分类

### 5.1 失败语义

- **fail-closed**：任何无法证明"能安全追加"的输入，抛 typed error，**不产出输出文件**；调用方把该次导出判失败，不发布、不写摘要、不返回半成品。
- **不静默降级**：不允许"注入失败就返回原 PDF"；那会产出无标识的 AI 内容文件，是合规失败，也是"假成功"。
- **不修改源文件**：读入内存/临时文件，输出到新临时文件；校验通过后才原子发布。
- **可观测**：错误码 + 输入字节数 + `startxref` 偏移（若有）+ 失败阶段；不记录 PDF 内容、不记录 JSON 值中的敏感信息（本项目只有 hash）。
- **错误类型**：`PdfAigcInjectionException(code, cause)`；导出层映射到既有 `MistakePdfExportFailure.GENERATED_PDF_INVALID`（`MistakePdfExporter.kt:36-43`），把 `code` 放在 cause/诊断里。这样不新增 UI 枚举、不改用户可见文案。

### 5.2 分类表（36 行）

> "类"：A = 结构不可读；B = 不支持但合法的 PDF 形态；C = 本注入的前置条件不满足；D = 资源/写出限制。所有类都 fail-closed，但诊断与告警级别可不同。

| # | 码 | 类 | 触发 | 检测点 | 测试夹具/断言 |
|---|---|---|---|---|---|
| 1 | `NOT_PDF_HEADER` | A | 前 5 字节不是 `%PDF-` | 读头 | 改首 5 字节 → 抛，无输出 |
| 2 | `HEADER_TRUNCATED` | A | 文件 < 8 字节 | 读头 | 空/半截文件 |
| 3 | `HEADER_VERSION_INVALID` | A | `%PDF-` 后不是 `1.x`/`2.0` 形式 | 读头 | `%PDF-x.y` |
| 4 | `STARTXREF_MISSING` | A | 尾窗内无 `startxref` | 尾扫 64 KiB | 改 `startxref` 拼写 → 抛 |
| 5 | `STARTXREF_NOT_INTEGER` | A | `startxref` 后不是十进制整数 | 尾扫 | 改成 `abc` |
| 6 | `STARTXREF_OUT_OF_RANGE` | A | 偏移 ≤ 0 或 ≥ 文件长度 | 尾扫 | 改 999999999 → 抛（实测） |
| 7 | `XREF_KEYWORD_MISSING` | A | 偏移处既不是 `xref` 也不是 `N G obj` | 定位 | 偏移指向正文 |
| 8 | `XREF_TABLE_MALFORMED` | A | 子段头不是两整数；条目不足 20 字节；`n/f` 非法；计数为负 | 表解析 | 改条目为 `00000000AB ...` → 抛（实测） |
| 9 | `XREF_FREE_HEAD_MISSING` | A | 原文件首个 xref 段的对象 0 条目缺失/非 free/生成号 ≠ 65535 | 表解析 | 删对象 0 子段 |
| 10 | `TRAILER_MISSING` | A | 找不到 `trailer` | 表解析 | 删 `trailer` |
| 11 | `TRAILER_MALFORMED` | A | `<<`/`>>` 不配对、键不是 name、值缺失 | 字典解析 | 截断 trailer |
| 12 | `TRAILER_ROOT_MISSING` | A | trailer 无 `/Root` | 字典解析 | 删 `/Root` |
| 13 | `TRAILER_ROOT_NOT_INDIRECT` | A | `/Root` 不是 `N G R` | 字典解析 | 改成直接字典 |
| 14 | `SIZE_MISSING` / `SIZE_INVALID` | A | 无 `/Size` 或不是非负整数 | 字典解析 | 删/改 `/Size` |
| 15 | `ID_MALFORMED` | A | `/ID` 存在但不是两元素数组 | 字典解析 | 改 `/ID 1` |
| 16 | `XREF_STREAM_MALFORMED` | A | 流对象头/字典/`stream` 关键字/`/Length` 非法 | 流解析 | 改 `/Length` |
| 17 | `XREF_STREAM_W_INVALID` | A | `/W` 不是 3 个非负整数 | 流解析 | 改 `/W [1 2]` |
| 18 | `XREF_STREAM_INDEX_INVALID` | A | `/Index` 奇数个/负数/重叠/越界 | 流解析 | 构造重叠 |
| 19 | `XREF_STREAM_DATA_TRUNCATED` | A | 解码后字节数 < `Index` 要求 | 流解析 | 截断流数据 |
| 20 | `XREF_STREAM_UNSUPPORTED_FILTER` | B | `/Filter` 不是 FlateDecode/None（或含未实现的 predictor/数组链） | 流解析 | `/Filter /ASCIIHexDecode` |
| 21 | `INFO_MISSING_ALLOWED` | — | 不是错误：`/Info` 缺失走新建分支 | — | 无 Info 夹具 → 新建 |
| 22 | `INFO_NOT_INDIRECT` | C | `/Info` 不是 `N G R` | 字典解析 | 直接字典 |
| 23 | `INFO_OBJECT_MISSING` | C | xref 链找不到该 `(N,G)`；偏移处头不匹配 | xref 链 | 删 Info 对象 |
| 24 | `INFO_NOT_DICTIONARY` | C | Info 对象值不是 `<<...>>` | 对象解析 | 改成 stream |
| 25 | `INFO_OBJECT_TRUNCATED` | C | 字典/字符串/hex 未闭合 | 对象解析 | 截断 |
| 26 | `INFO_IN_OBJECT_STREAM` | B | Info 条目是 xref type 2 | xref 链 | 对象流夹具 |
| 27 | `AIGC_VALUE_NOT_STRING` | C | 已有 `/AIGC` 值不是字符串 | Info 字典 | 改成 name |
| 28 | `ENCRYPTED` | B | trailer 有 `/Encrypt` | trailer | 加 `/Encrypt` → 抛（实测） |
| 29 | `LINEARIZED` | B | 首个对象字典含 `/Linearized`（前 8 KiB 粗筛后解析确认） | 头对象 | 加 `/Linearized` → 抛（实测） |
| 30 | `HYBRID_XREF` | B | trailer 有 `/XRefStm` | trailer | 加 `/XRefStm` |
| 31 | `OBJECT_NUMBER_LIMIT_EXCEEDED` | D | 新对象号 > 8,388,607 | 分配 | 合成 Size 极大 |
| 32 | `DECODED_STREAM_TOO_LARGE` | D | xref 流解码 > 16 MiB | 流解析 | 压缩炸弹夹具 |
| 33 | `INPUT_TOO_LARGE` | D | 输入 > `maxPdfBytes` | 入口 | 24 MB+1 |
| 34 | `OUTPUT_TOO_LARGE` | D | 注入后 > `maxPdfBytes` | 出口 | 注入前刚好上限 |
| 35 | `WRITE_IO` | D | 写临时文件/fsync/rename 失败 | 出口 | 只读目录/磁盘满 |
| 36 | `AIGC_SCHEMA_INVALID` | C | JSON 不是 7 键/类型不对/非 ASCII/超长 | 写前校验 | 每个约束一条 |

> 表里 36 行，除 `INFO_MISSING_ALLOWED`（不是错误，走新建分支）外都是拒绝码；落地时可合并同义码（例如 `SIZE_MISSING`/`SIZE_INVALID` 合一个），但**每个码必须有测试**。为避免计数争议，以本表为准；§5.4 列"本会话实测 6 例"，其余为设计覆盖项，未逐条跑。

### 5.3 支持但拒绝（合法 PDF 形态）

这些不是"畸形"，而是本注入**主动不支持**的形态。仍然 fail-closed，但错误码归 B 类，便于运营区分"文件坏了"和"文件是另一种合法形态"：

- **加密**：Info 字符串必须加密；明文写会破坏文件。拒绝。
- **线性化**：增量更新后不再是线性化文件；hint 表失效。规范允许更新但产品不需要。拒绝。
- **hybrid-reference**：`/XRefStm` 的查找优先级复杂；本产品产物没有。拒绝。
- **Info 在对象流里**：需要重写对象流，超出"字节拼接"范围。拒绝。
- **xref 流用未实现的 Filter/ predictor**：先拒绝，后续按真实样本补；不要"猜着解码"。

### 5.4 本会话实测的 6 例

从 `fixtures/table.pdf` 变异出 6 个夹具，逐一跑原型，输出：

```
bad-header       PdfError: NOT_PDF_HEADER:
no-startxref     PdfError: STARTXREF_MISSING:
bad-offset       PdfError: STARTXREF_OUT_OF_RANGE: 999999999
bad-xref-entry   PdfError: XREF_TABLE_MALFORMED: bad entry b'00000000AB 00000 n \n'
encrypted        PdfError: ENCRYPTED:
linearized       PdfError: LINEARIZED:
```

覆盖了计划点名的前三类（非头/缺 startxref/坏偏移）+ 加密/线性化两个"支持但拒绝"；第四类"截断"没有独立夹具（`bad-xref-entry` 是条目格式坏，不是截断），截断路径由设计表中的 `INFO_OBJECT_TRUNCATED`/`XREF_STREAM_DATA_TRUNCATED` 覆盖，**未跑**（§8.7）。

### 5.5 不应误判的"看起来畸形"

| 现象 | 为什么不拒绝 | 依据 |
|---|---|---|
| `/Size` 小于实际最高对象号 | 真实文件有（PdfPig 测试 `UpdatedDocumentKeepsTheOriginalAndReadsTheChanges` 的 linearized 样本 `annotation-comments.pdf`：主段 `/Size 9` 但对象到 29）；用 `max(Size, highest+1)` 规避 | §6.4 测试清单 |
| 文件尾 `%%EOF` 后有空白/换行 | 规范只要求最后一行是 `%%EOF`；尾随空白不影响追加 | §7.5.5；pdf.js 追加前只补 EOL |
| 多个 `startxref`/多个 trailer | 增量更新的正常形态；取最后一个 | §7.5.6 |
| 无 `/ID` | 规范可选；Skia 默认无 | §14.4、§2.11 |
| 无 `/Info` | 规范可选；走新建分支 | §14.3.3 |
| 对象 0 的 free 条目不存在于更新段 | 更新段只写变更对象；对象 0 在原段已有 | §7.5.6 |
| xref 表条目用 `\r\n` 而不是 ` \n` | 规范允许三种 EOL | §7.5.4 |

---

## 6. 参考实现评分

### 6.1 评分维度与权重（满分 100）

| 维度 | 权重 | 说明 |
|---|---|---|
| 规范保真（表/trailer/Prev/增量语义） | 25 | 是否逐条对齐 ISO 32000-1 §7.5 |
| xref 流支持 | 15 | `/Type /XRef`、W/Index/Prev、流对象自身条目 |
| Info/元数据合并语义 | 10 | 是否保留既有键、是否支持自定义键、幂等 |
| 安全/fail-closed | 15 | 加密、坏 xref、暴力重建、拒绝而非静默 |
| 可移植到 Kotlin/JVM 零依赖 | 15 | 算法清晰度、语言距离、运行时可复用性 |
| 维护度/许可证 | 10 | license、最近提交、issue、发布节奏 |
| 测试/行为 oracle | 10 | 是否有覆盖增量边界的测试 |

评分是**工程判断**，不是基准测试；所有仓库元数据于 2026-11-02 用 `gh api` 读取（§8.8）。

### 6.2 pyHanko（Python，MIT）

**事实**：

- `IncrementalPdfFileWriter` 类文档："Incremental updates to a PDF file append modifications to the end of the file. This is critical when the original file contents are not to be modified directly (e.g. when it contains digital signatures)."（`pkgs/pyhanko/src/pyhanko/pdf_utils/incremental_writer.py:20-31`）
- 构造时 `obj_id_start=trailer['/Size']`、`stream_xrefs=prev.has_xref_stream`（同文件 `:76-83`）——**沿用上一段的 xref 类型**。
- `/Info` 可选：`try: info_ref = trailer.raw_get('/Info') except KeyError: info_ref = None`（`:64-70`）。
- `/Prev` 写直接整数：`trailer[pdf_name('/Prev')] = generic.NumberObject(self.prev.last_startxref)`（`:201-203`）。
- ID：第一段保留，第二段重新生成；注释明确引 ISO 32000 §7.6.3.3 与 §14.4（`:110-140`）。
- 表型条目：`f'{position:010d} {generation:05d} n \n'`（`pdf_utils/xref.py:1395-1398`）；对象 0 只在首个子段从 1 开始时写入（`:1410-1422`）；**删除对象未实现**（`TODO support deleting objects`，`:1406`）。
- 加密：`_populate_trailer` 检查 `self.prev.encrypted and not self.security_handler.is_authenticated()`，未认证则抛 `PdfWriteError`（`incremental_writer.py:205-211`）。
- 元数据：`2026-11-02`：MIT、794★、13 open issues、`pushed_at=2026-11-01T20:51:59Z`、最新提交 `56d28075ee810eab089142f356615e96cc7696af`。
- 文档：`https://docs.pyhanko.eu/en/latest/lib-guide/reading-writing.html` 明确 `write_in_place()` 追加到同一流、`write()` 写到不同文件/缓冲。

**评分**：规范 5、流 5、Info 4、安全 5、可移植 2、维护 5、测试 4 → 87/100。

**用法**：作为**严格读取/trailer 语义**的 oracle；不要移植它的加密/签名栈。

### 6.3 pdf.js（JavaScript，Apache-2.0）

**事实**：

- `src/core/writer.js` 有 `incrementalUpdate({ originalData, xrefInfo, changes, ..., useXrefStream })`（`:498`）。
- 追加前若原数据最后一个字节不是 `\n`/`\r`，补一个 EOL，避免 `%%EOF` 与对象粘连（`:536-541`）。
- `getTrailerDict`：`Prev`、`Size`、`Root`、`Info`、`Encrypt`（`:455-472`）；**不复制**上一 trailer 的其它自定义条目——这是与 §7.5.6"复制全部条目"的偏差。
- 表型条目 20 字节，`\r\n` 结尾（`:361-376`）；free 条目 `0000000000` + `gen+1` + `f`（`:377-381`）。
- 流型：`/Type /XRef`、`/Size`、`/Index`、`/W [1 offsetSize maxGenSize]`；流数据不压缩（`:395-440`）。
- ID：`computeIDs` 保留 `fileIds[0]`，第二段用 MD5（`:443-448`）；MD5 只用于 PDF 的 changing ID，不是安全用途，但若移植建议换 SHA-256。
- 选择同型：`src/core/worker.js:933` 注释 "Use the same kind of XRef as the previous one."，`useXrefStream: isDict(xref.topDict, "XRef")`。
- 元数据：`2026-11-02`：Apache-2.0、54,072★、467 open issues、`pushed_at=2026-11-01T18:27:22Z`、最新提交 `6a12ce7c943bbed354f75243812b18bfe17ba480`。

**评分**：规范 4（trailer 复制缺口）、流 5、Info 3、安全 3、可移植 2、维护 5、测试 4 → 74/100。

**用法**：作为**流型字节布局**的参考（Index/W/流对象自身条目）；不要照抄它"只复制 Root/Info/Encrypt"的 trailer 逻辑。

### 6.4 PdfPig（C#/.NET，Apache-2.0）

**事实**（本会话读到的 2026-11-01 新提交）：

- `PdfIncrementalUpdateBuilder` 269 行，提交说明明确 "ISO 32000-1, 7.5.6"；原始字节不变，后接新/替换对象 + 新 xref 段 + 新 trailer。
- 拒绝加密：`if (document.IsEncrypted) throw new NotSupportedException(...)`；拒绝暴力重建的 xref：`CrossReferenceTable.IsBruteForced`（构造函数）。
- 新对象号：`nextObjectNumber = Math.Max(Math.Max(previousSize, structure.Trailer.Size), highestObjectNumber + 1)`，注释说明 "Size is too small in some files"（构造函数）。
- 追加前 `update.WriteNewLine()`，处理原文件 `%%EOF` 后无换行。
- 上一段是流就写流（`previous.Type == CrossReferenceType.Stream`），否则写表。
- trailer：表型复制上一字典除 `Prev`/`XRefStm` 外的全部条目；流型只取 `Root`/`Info`（`CreateTrailer`）。
- ID：第一段保留，第二段新 GUID；无 ID 时两段相同（`CreateTrailer`）。
- 流型条目：对象 0（free，`65535`）、xref 流自身、变更对象；`W` 宽度按最大值算；`/Index` 按连续区间分组；`/Filter /FlateDecode`（`WriteCrossReferenceStream`）。
- 测试：`PdfIncrementalUpdateBuilderTests.cs` 覆盖原始前缀、替换 Info、trailer 延续（`Size 17`→`18`、`Prev 27833`）、ID 语义、xref 流保持、linearized 样本（`/Size 9` 但对象到 29）、加密拒绝、暴力 xref 拒绝。
- issue #1289（2026-05-06 开、2026-05-21 关）标题就是 "Reintroduce CrossReferenceTable / expose low-level xref and trailer access for incremental updates"——证明低层 xref 访问是增量更新的硬前提。
- 元数据：`2026-11-02`：Apache-2.0、2,612★、26 open issues、`pushed_at=2026-11-01T13:53:19Z`、最新提交 `95f4fecbd052d481e1465ec045b7f52fa6c29832`。

**评分**：规范 5、流 5、Info 5、安全 5、可移植 3、维护 4（功能只有 1 天）、测试 5 → 92/100。

**用法**：**主蓝图**。它的 builder 与我们的任务同形（只写 Info 类对象、两种 xref、拒绝加密/暴力重建），测试可直接翻译成 JVM 用例。

### 6.5 总分与选型

| 实现 | 规范 25 | 流 15 | Info 10 | 安全 15 | 可移植 15 | 维护 10 | 测试 10 | 总分 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| PdfPig | 25 | 15 | 10 | 15 | 9 | 8 | 10 | **92** |
| pyHanko | 25 | 15 | 8 | 15 | 6 | 10 | 8 | **87** |
| pdf.js | 20 | 15 | 6 | 9 | 6 | 10 | 8 | **74** |

**选型**：

1. **不引依赖**：三者都不是 JVM/Kotlin；pyHanko 是 Python，pdf.js 是 JS，PdfPig 是 .NET。为写一个 Info 键引入运行时桥/整库不成比例（前次 pdfbox-android 体积实测也否决了整库路线：`2026-10-26-pdf-implicit-labeling.md:195-214`）。
2. **移植算法**：以 PdfPig 的 269 行 builder 为主蓝图；用 pyHanko 补"严格读取、`/Info` 可选、ID 语义、加密拒绝"；用 pdf.js 补"流型 W/Index/无压缩字节布局"。
3. **不要复制代码**：三者许可证都允许（MIT/Apache-2.0），但语言不同、代码风格不同；重写 ~200-300 行 Kotlin 比移植 + 适配 + NOTICE 更干净。
4. **行为 oracle**：PdfPig 的测试用例（linearized/Size 偏小/ID/流型/加密）逐条翻译成 JVM 测试；pyHanko 用于"注入后能否被严格读者读回"的交叉验证。

### 6.6 许可证与维护快照

| 项目 | License | ★ | Open issues | 最近 push | 最新提交 | 结论 |
|---|---|---:|---:|---|---|---|
| pyHanko | MIT | 794 | 13 | 2026-11-01T20:51:59Z | `56d28075` | 活跃、可参考 |
| pdf.js | Apache-2.0 | 54,072 | 467 | 2026-11-01T18:27:22Z | `6a12ce7c` | 极活跃、可参考 |
| PdfPig | Apache-2.0 | 2,612 | 26 | 2026-11-01T13:53:19Z | `95f4fecb` | 活跃、功能新 |

---

## 7. 落地设计（供 WS1 实施）

### 7.1 模块落点：计划与依赖图的矛盾

**证据**：

- `core/export/build.gradle.kts:23-24`：只依赖 `:core:model`、`:core:domain`，没有 `:core:data`。
- `core/data/build.gradle.kts:169-199`：依赖 `:core:model`、`:core:domain`、`:core:database`、AndroidX、ML Kit、LiteRT、OkHttp、QuickJS 等。
- 生产构造点：`app/.../SmartMistakeBookApplication.kt:426`、`feature/library/.../MistakeExportHubRoute.kt:109` 都是 `MistakePdfExporter(context)`。
- 计划 `.zcode/plans/plan-2026-11-02-batch-40-5x.md:16` 写"落 `core/data` pdf 包"。

**矛盾**：注入必须在 `MistakePdfExporter.prepare` 内、`render` 之后、`writeDigest` 之前（`MistakePdfExporter.kt:128-136`），否则发布/摘要/缓存都基于未标识字节。但 `core:export` 不能直接调用 `core:data`。

**三个方案**：

| 方案 | 做法 | 优点 | 代价/风险 |
|---|---|---|---|
| A（最小） | 注入器直接放 `core:export`（新文件，不碰现有 M 文件/未跟踪 lockfile） | 无新依赖、无接口、调用点最少 | `core/export` 当前是禁改热区；需等热区空出 |
| B（计划兼容） | 在 `core:model` 定义极小接口 `PdfAigcLabeler`（`fun label(bytes, json): ByteArray`），`core:data` pdf 包实现；`MistakePdfExporter` 构造参数注入；app/feature 两个构造点传入实现 | 符合"落 core/data"；`core:export` 不依赖 `core:data`；schema builder 可与图片路径共用 | 改 `core:model`（设备绿快照绑定源，需重跑设备门）+ 改 app/feature 两个热区构造点 + 改 core/export 测试构造点 |
| C（最干净） | 新建 `core:pdf` 纯 JVM 模块，`core:export` 依赖它 | 职责单一、零 Android 依赖、JVM 测试快 | `settings.gradle.kts` 是禁改热区；本批不可行 |

**建议**：

- 若 WS1 可以等 `core/export` 空出：选 **A**，最小、最可证；注入器是 `ByteArray -> ByteArray` 的纯函数，JVM 测试不需要 Android。
- 若必须按计划落 `core:data` 且不能动 `core:export` 的依赖：选 **B**，但把 `core:model` 的接口做到最小（一个方法、无 Android 类型），并接受设备绿重跑；**绝不要**给 `core:export` 加 `:core:data` 依赖（会把 database/ML/okhttp 拖进导出，破坏模块分层）。
- 若将来 settings 可动：选 **C**，把 A 的实现整体搬过去。

> 这一条是**研究结论，不是实施授权**；落点需主会话按热区现状裁定。

### 7.2 注入算法（可直接照做）

```
fun inject(source: ByteArray, json: String, limits: Limits): ByteArray
 1. 校验 json（§3.4）；不合法 → AIGC_SCHEMA_INVALID
 2. 校验 size <= limits.maxInputBytes；读头 %PDF- + 版本
 3. 尾扫 64 KiB 找最后一个 startxref；解析十进制偏移
 4. 若前 8 KiB 含 /Linearized → 解析首个间接对象字典确认；确认后 LINEARIZED（粗筛命中但对象字典无该键 = 正文/注释字面量，继续）
 5. 定位 startxref：
      - 是 "xref" → 表型：解析子段/条目，找最后一个 trailer，解析字典
      - 是 "N G obj" → 流型：解析字典，要求 /Type /XRef；解码流（Flate + 可选 PNG predictor）
      - 否则 → XREF_KEYWORD_MISSING
 6. 检查 trailer/流字典：/Encrypt → ENCRYPTED；/XRefStm → HYBRID_XREF；/Root 必须存在且为引用
 7. 取 /Info：
      - 无 → 新建对象 N = max(Size, highest+1), G=0；新字典 << /AIGC (...) >>；trailer /Info = N 0 R
      - 有 → 必须 N G R；沿 /Prev 链找 (N,G) 的 type 1 偏移；校验对象头；token-aware 解析字典
 8. 合并 /AIGC：
      - 无 → 插到外层 >> 前
      - 有且是字符串 → 同值 no-op；不同值替换 token
      - 有非字符串 → AIGC_VALUE_NOT_STRING
 9. 生成新对象字节；原字节 + EOL（若需要）+ 新对象
10. 写 xref：
      - 表型：xref\nN 1\n%010d %05d n \n
      - 流型：新流对象号 M = max(Size, highest+1)；条目 = 变更对象 + M（+ 可选 0）；
              W=[1, offsetWidth, genWidth]；Index 分组；Flate 或不压缩；Type/Size/Index/W/Prev/Root/Info/ID
11. 写 trailer：复制上一字典除 Size/Prev/XRefStm/流键外的全部条目；Size=max(...); Prev=旧 startxref；ID 第一段保留/第二段新摘要
12. 写 startxref\n<新段偏移>\n%%EOF\n
13. 返回新字节；任何异常 → 抛 typed error，不返回部分结果
```

**写盘策略**：不要在 `temporaryPdf` 上原地读改写；`render → raw.pdf`，`inject(raw.pdf, labeled.pdf)`，后续大小/页数/摘要都用 `labeled.pdf`，最后原子替换/发布。若注入失败，删除临时目录，不发布。

### 7.3 调用顺序与缓存

在 `MistakePdfExporter.prepare`（`MistakePdfExporter.kt:114-140`）中：

```
render(input, rawPdf)
val labeledPdf = if (containsAiGeneratedExportContent(input)) {
    inject(rawPdf, aigcJson(input))
} else {
    rawPdf
}
检查 labeledPdf.length <= maxPdfBytes        // 注入后必须再查
pageCount = validatedPageCount(labeledPdf)   // PdfRenderer 打开 = 解析门
store.writeDigest(..., sha256File(labeledPdf))
store.publish(input, ..., pageCount)
```

- **缓存指纹升版**：`MistakePdfEligibility.kt:186` 的 `"mistake-export-v3"` → `"mistake-export-v4"`（或把 `AIGC_SCHEMA_VERSION` 拼进指纹）。否则旧暖缓存会继续返回无 `/AIGC` 的 PDF，且 `publish` 先 `findExisting` 会丢弃新渲染结果（前次研究 `2026-10-26-pdf-implicit-labeling.md:171-179` 已论证）。
- **打印部分页**：`MistakePdfDelivery.writeSubset`（`:215-259`）重绘后无 Info。若"打印到 PDF"算导出，则对子集缓冲后注入；否则登记残留。建议注入（同一失败形状：AI 内容离开 App 无标识）。
- **打印整份**：`copyWhole` 复制 prepared 文件，随主落点已带标签，无需改。
- **大小**：标签约 +500 字节（PoC 表型 +522、流型 +622）；对 24 MB 上限影响可忽略，但必须在注入后检查。
- **摘要**：必须覆盖注入后的最终字节；`PreparedMistakePdf.verifyIntegrity` 会比对，注入必须在 `writeDigest` 之前。

### 7.4 测试矩阵

**JVM（`core:export/src/test` 或 `core:data/src/test`，按落点）**：

| # | 夹具 | 断言 |
|---|---|---|
| 1 | 表型 + Info + 无 ID | `/AIGC` 读回；Title/Author 原字节；原始前缀相等；页数不变（对象图未动） |
| 2 | 表型 + ID | ID 第一段保留、第二段变化 |
| 3 | 表型 + 已有 `/AIGC` 同值 | 字节幂等（`inject(inject(x)) == inject(x)`） |
| 4 | 表型 + 已有 `/AIGC` 不同值 | 最新 Info 只有一个 `/AIGC`，读回新值 |
| 5 | 流型 + 对象流 | 新 xref 流；`/Prev` 链；读回 `/AIGC`；页数不变 |
| 6 | 无 `/Info` | 新对象号 = max(Size, highest+1)；`Size+1`；trailer `/Info` 指向新对象 |
| 7 | `/Info 10 2 R` | 新对象头 `10 2 obj`；生成号保留 |
| 8 | 非 ASCII 值 | `AIGC_SCHEMA_INVALID`（不写） |
| 9 | `/Encrypt` | `ENCRYPTED`，无输出 |
| 10 | `/Linearized` | `LINEARIZED`，无输出 |
| 11 | `/XRefStm` | `HYBRID_XREF`，无输出 |
| 12 | 坏头/缺 startxref/坏偏移/坏条目/坏 trailer/截断 Info | 对应码；无输出文件 |
| 13 | Size 偏小 | 新对象号不撞号；Size 修正 |
| 14 | xref 流 `/DecodeParms /Predictor 12` | 能解码（生产必测；PoC 未覆盖） |
| 15 | 注入后超 `maxPdfBytes` | `OUTPUT_TOO_LARGE` |
| 16 | 二次注入（不同值） | 文件增长但只有一个最新 `/AIGC` |

**仪器化（`core/export/src/androidTest`，设备门）**：

1. 真实 `DeterministicMistakePdfRenderer.render` → 注入 → `PdfRenderer` 打开，页数与渲染计划一致。
2. `PreparedMistakePdf.verifyIntegrity()` == true；`sha256File == prepared.sha256`。
3. 读回 `/AIGC`：`Label=="1"`、`ContentProducer=="SmartMistakeBook"`、`ProduceID==input.inputSha256`。
4. 负向：纯 `QUESTION_DIAGRAM`/用户文本且无模型解析证据 → 读不到 `/AIGC`。
5. 含 `MODEL_DOCUMENT_PARSE` 证据 → 读得到 `/AIGC`。
6. 打印子集路径（若决定覆盖）→ 子集 PDF 读得到 `/AIGC`。
7. 独立读者：测试 APK 内 `pdfbox-android`（只进 test，不进生产）或拉回产物用 pypdf/PyMuPDF；不得只靠自写读回器自证（前次研究 `2026-10-26-pdf-implicit-labeling.md:377-380`）。

**设备未跑项**：`PdfRenderer`/PDFium 接受追加段（U3）；PDFium 对 xref 流 `Prev` 链的处理；MuPDF 已在本会话作为独立读者通过（桌面），不等于 Android PDFium。

### 7.5 验收判据（WS1 8 项）

- JVM 单测全绿：表型/流型/无 Info/ID/幂等/§5 码表覆盖/非 ASCII/Size 偏小。
- 字节级：`out.startsWith(original)`；页数不变；Info 原键逐字节保留；`/AIGC` 读回 7 字段。
- 设备：真实导出 → 注入 → `PdfRenderer` 页数一致；独立读者读回；负向不伪造。
- 缓存：v3→v4（或 schema 版本进指纹）；旧缓存不再返回无标签产物。
- 失败：任何注入失败 → 导出失败，无半成品；诊断码可查。
- 显式图注逐位不变（本批不碰图注）。
- **不得**以"法规未要求"关单（20258A §6.3 明文有 PDF 方法；`docs/known-defects.md:2844`）。

### 7.6 可观测性

- 成功：`pdf_aigc_injected` + `shape=table|stream` + `bytes_added` + `schema=7` + `has_model_text=true|false`。
- 失败：`pdf_aigc_failed` + code + stage + input bytes + startxref offset（若有）；不记 PDF 内容。
- 指标：注入失败率、平均增加字节、流型占比（若为 0，说明我们的 Skia 输入永远是表型，流型支持只是防御）。

---

## 8. 本轮实际执行的验证

> 所有命令在 `D:/pdfres/2026-11-02`（仓库外的工作目录）执行；脚本在 `proto/`，下载物在 `tc260/`、`spec/`，夹具在 `fixtures/`，产物在 `out/`。没有在仓库内写任何文件，除本文档。

### 8.1 官方文档下载与 hash

```
$ curl ... tc260-20258a.pdf
http=200 size=3272625
$ sha256sum tc260-20258a.pdf
6bf6c1189208856099bfbf648bdf386da288b1df00f430502b395b874dbf3b0e

$ curl ... tc260-20259a.pdf
http=200 size=2190188
$ sha256sum tc260-20259a.pdf
e4f7fe820a181b2e24a5e3214b751e67c04672ec68fb4d66484da4e77b02a02f

$ curl ... PDF32000_2008.pdf
http=200 size=22491828
$ sha256sum pdf32000.pdf
9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff
```

- 20258A 用 PyMuPDF 提取 §6.3 与附录 B.5；第 17 页渲染为 PNG 后逐字读取图 10。
- GB 45438-2025 著录页：`curl` HTTP 200，页面文本含 `标准号：GB 45438-2025`、`标准名称：网络安全技术 人工智能生成合成内容标识方法`、`状态：现行`、`发布日期 2025-02-28`、`实施日期 2025-09-01`；`showGb?type=online` 302 回著录页，**全文/附录 E 未取得**。

### 8.2 夹具

```
$ python -I proto/make_fixtures.py D:/pdfres/2026-11-02/fixtures
wrote .../table.pdf 1302
wrote .../stream.pdf 1223
```

两份均 2 页，元数据 `Title=Original Title`、`Author=Original Author`、`Producer=research-fixture`；`stream.pdf` 用 `use_objstms=1` 生成 xref 流 + 对象流，`table.pdf` 为经典表。

### 8.3 注入结果

```
$ python -I proto/inject.py fixtures/table.pdf out/table-aigc.pdf "$JSON"
shape=table info=10 0 R original=1302 appended=522 total=1824
$ python -I proto/inject.py fixtures/stream.pdf out/stream-aigc.pdf "$JSON"
shape=stream info=10 0 R original=1223 appended=622 total=1845
$ python -I proto/inject.py out/table-aigc.pdf out/table-aigc-twice.pdf "$JSON"
shape=table info=10 0 R original=1824 appended=523 total=2347
$ python -I proto/inject.py out/stream-aigc.pdf out/stream-aigc-twice.pdf "$JSON"
shape=stream info=10 0 R original=1845 appended=623 total=2468
```

`JSON` 为 7 字段：

```json
{"Label":"1","ContentProducer":"SmartMistakeBook","ProduceID":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","ReservedCode1":"","ContentPropagator":"","PropagateID":"","ReservedCode2":""}
```

### 8.4 独立读回（pypdf + PyMuPDF）

```
$ python -I proto/verify.py D:/pdfres/2026-11-02
=== table
prefix_preserved=True pages_pypdf=2 pages_mupdf=2
title='Original Title' author='Original Author'
aigc='{"Label":"1",...}'
mupdf title='Original Title' author='Original Author'
aigc_occurrences_in_latest_info_window=1
RESULT PASS
=== table-twice ... RESULT PASS
=== stream ... RESULT PASS
=== stream-twice ... RESULT PASS
```

**证明**：表型/流型、首次/二次注入共 4 组，全部满足：原始前缀保留、两个独立读者均 2 页、Title/Author 保留、`/AIGC` 读回、最新 Info 窗口只有一个 `/AIGC`。

### 8.5 结构断言

```
table:  startxref 1639 → bytes@startxref b'xref\n10 1\n0000001302'
        trailer Prev 934, Size 11 (orig 11)
stream: startxref 1560 → bytes@startxref b'13 0 obj\n<<\n/Info 10 ...'
        raw dict /Prev 940, /Size 14, /Type /XRef, /Index [0 1 10 1 13 1], /W [1 4 2]
ID:     table 第一段保留=True 第二段变化=True
        stream 第一段保留=True 第二段变化=True
```

- 表型 `startxref` 指向 `xref` 关键字；流型指向 `13 0 obj` 对象头——与 §2.2 一致。
- `/Prev` 是直接整数（934/940），被 pypdf/MuPDF 接受。
- 流型 `/Size 14`、`/Index`、`/W` 与 §2.5 一致；`/Prev` 在 pypdf 的高层 `.trailer` 视图里不显示，但原始字节里有（这是 pypdf 的呈现方式，不是缺陷）。

### 8.6 畸形 6 例

```
bad-header       PdfError: NOT_PDF_HEADER:
no-startxref     PdfError: STARTXREF_MISSING:
bad-offset       PdfError: STARTXREF_OUT_OF_RANGE: 999999999
bad-xref-entry   PdfError: XREF_TABLE_MALFORMED: bad entry b'00000000AB 00000 n \n'
encrypted        PdfError: ENCRYPTED:
linearized       PdfError: LINEARIZED:
```

### 8.7 未运行项

- **Android `PdfRenderer`/PDFium** 对追加段的接受度：未跑设备/模拟器（本批不允许 Gradle）。
- **Kotlin 实现**：未写；本文只有 Python 原型，用于验证字节算法。
- **PNG predictor（`/DecodeParms /Predictor 12`）**：原型未实现，夹具未覆盖。
- **xref 流 `/Length` 间接引用**：原型只支持直接 `/Length`。
- **对象流里的 Info**：原型直接拒绝，未构造成功路径（设计上就是拒绝）。
- **截断 Info 对象 / 坏 trailer / 缺 Root / 坏 Size / 坏 ID**：设计表覆盖，未逐条跑。
- **`/Linearized` 的"确认"步骤**：原型只做前 8 KiB 原始字节粗筛，未解析首个对象字典确认；"加 `/Linearized` → LINEARIZED"的实测对应粗筛路径，生产实现需补确认步骤并加"正文含 `/Linearized` 字面量不误拒"的用例。
- **DOCX `docProps/custom.xml`**：不在本文范围。
- **PDF 2.0**：未取得 ISO 32000-2 全文，未验证 2.0 的 UTF-8 text string / xref 流差异。

### 8.8 仓库元数据命令

```
$ gh api repos/MatthiasValvekens/pyHanko --jq '{stargazers_count,license:.license.spdx_id,pushed_at,open_issues_count}'
{"stargazers_count":794,"license":"MIT","pushed_at":"2026-11-01T20:51:59Z","open_issues_count":13}
$ gh api repos/mozilla/pdf.js ...
{"stargazers_count":54072,"license":"Apache-2.0","pushed_at":"2026-11-01T18:27:22Z","open_issues_count":467}
$ gh api repos/UglyToad/PdfPig ...
{"stargazers_count":2612,"license":"Apache-2.0","pushed_at":"2026-11-01T13:53:19Z","open_issues_count":26}
```

---

## 9. 待验证项与假设

| # | 项 | 当前证据 | 状态 | 消解动作 |
|---|---|---|---|---|
| U1 | GB 45438-2025 附录 E 的字段必选性（7 键是否必须全写、4 个可选字段能否省略/空串） | 著录页元信息已核（现行/2025-02-28/2025-09-01）；全文与附录未取得（openstd `showGb` 302） | **未取得** | 用标准全文系统预览/向 TC260 索取；落地前复核 |
| U2 | 只含 AI 图、不含 AI 文本的 PDF 是否直接触发 20258A | §1 限定"文本内容"；§5.2"其他文件格式可参考处理" | **解释未定** | 法务/合规书面裁定；本文建议保守按 §6.3 写 |
| U3 | Android PDFium/`PdfRenderer` 是否接受追加的增量段 | 桌面 pypdf/PyMuPDF 通过；设备未跑 | **未验证** | 仪器化用例：注入后 `PdfRenderer` 打开并比对页数 |
| U4 | 图片路径 3 字段 vs PDF 7 字段 | 官方 7 键；图片路径 3 键 | **已登记差异** | 统一 builder；由用户/法务裁定 3 vs 7；本文建议 PDF 先 7 |
| U5 | `ContentProducer` 用名称 vs 27 位编码 | 《办法》允许"名称或者编码"；编码结构单源 | **口径已选名称，法律待核** | 随 U1/法务 |
| U6 | 模型解析文本是否属于 20258A 的"生成合成文本内容" | KD-75 已证 provenance 可进已提交文档 | **解释未定** | 保守打标；法务复核 |
| U7 | xref 流 PNG predictor 的完整支持 | 真实 PDF 常见；PoC 未覆盖 | **未验证** | 用真实样本补 Predictor 12 用例 |
| U8 | 打印子集是否属于"导出" | 《办法》§4(2) 列举下载/复制/导出；打印到 PDF 未明示 | **解释未定** | 产品/法务裁定；本文建议至少登记，最好注入 |
| U9 | `Prev` 的"间接引用"表述 | ISO 32000-1 Table 15 Type 列 integer、prose 说 indirect，自相矛盾；三份实现均写直接整数 | **按实践写直接整数** | 与 PDF 2.0/勘误核对；不改 |
| U10 | PDF 2.0 的文本串/ xref 流差异 | 未取得 ISO 32000-2 全文 | **未验证** | 若未来接入 PDF 2.0 输入，再核 |
| U11 | 注入后文件大小/性能 | 表型 +522 B、流型 +622 B；未测 24 MB 边界 | **部分验证** | 设备/基准测试 |
| U12 | 设备绿快照与 `core:model` 改动 | `core:model` 在设备绿绑定源内 | **未验证** | 若选方案 B，改后必须重跑设备门 |

---

## 10. 来源清单

### 官方/标准（本会话实际下载或访问）

- TC260-PG-20258A 文本文件指南（官方 PDF，HTTP 200，3,272,625 B，20 页，sha256 `6bf6c1189208856099bfbf648bdf386da288b1df00f430502b395b874dbf3b0e`）：<https://www.tc260.org.cn/tc260/sjzn/202508/f0a84ada0c11458e9cb6da56c1cb2426/files/760387fb30e94c058c31b8c2b2230020.pdf>
- TC260-PG-20259A 图片文件指南（HTTP 200，2,190,188 B，11 页，sha256 `e4f7fe820a181b2e24a5e3214b751e67c04672ec68fb4d66484da4e77b02a02f`）：<https://www.tc260.org.cn/tc260/sjzn/202508/7227bfecdf344c14abc647fbbcf69edf/files/37774d2b3149456ba7179a81bc07d3d5.pdf>
- ISO 32000-1:2008 全文（Adobe 公开版，HTTP 200，22,491,828 B，sha256 `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff`）：<https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf>
- GB 45438-2025 著录页（本会话 `curl` 200，页面含"现行"、发布 2025-02-28、实施 2025-09-01）：<https://openstd.samr.gov.cn/bzgk/gb/newGbInfo?hcno=F32EA2A561F1886CD8D606513512D547>
- 《人工智能生成合成内容标识办法》（cac.gov.cn；本会话未重取，条文经仓内合规文档 `2026-10-25-ai-content-labeling-compliance.md` 引述）：<https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm>

### 参考实现

- pyHanko 仓库：<https://github.com/MatthiasValvekens/pyHanko>；本会话读取 `pkgs/pyhanko/src/pyhanko/pdf_utils/incremental_writer.py`、`.../writer.py`、`.../xref.py`（commit `56d28075ee810eab089142f356615e96cc7696af`）；文档：<https://docs.pyhanko.eu/en/latest/lib-guide/reading-writing.html>
- pdf.js 仓库：<https://github.com/mozilla/pdf.js>；本会话读取 `src/core/writer.js`、`src/core/worker.js`（commit `6a12ce7c943bbed354f75243812b18bfe17ba480`）
- PdfPig 仓库：<https://github.com/UglyToad/PdfPig>；本会话读取 `src/UglyToad.PdfPig/Writer/PdfIncrementalUpdateBuilder.cs`、`src/UglyToad.PdfPig.Tests/Writer/PdfIncrementalUpdateBuilderTests.cs`、`README.md`（commit `95f4fecbd052d481e1465ec045b7f52fa6c29832`）；issue #1289：<https://github.com/UglyToad/PdfPig/issues/1289>

### 平台/实现细节

- AOSP `PdfDocument.java`（本会话核对：无 metadata API，只有 `startPage/finishPage/writeTo/close`）：<https://github.com/aosp-mirror/platform_frameworks_base/blob/master/graphics/java/android/graphics/pdf/PdfDocument.java>
- Skia `SkPDFDocument.cpp`（本会话核对行 104/153/155/161/163：经典 xref、`/Root`、`/Info`、`startxref`、`%%EOF`）：<https://github.com/google/skia/blob/main/src/pdf/SkPDFDocument.cpp>

### 仓内（本会话读取）

- 计划：`.zcode/plans/plan-2026-11-02-batch-40-5x.md:8`、`:16-23`、`:83`
- 合规研究：`docs/research/2026-10-25-ai-content-labeling-compliance.md:69`、`:156`、`:202-218`
- 前次 PDF 研究：`docs/research/2026-10-26-pdf-implicit-labeling.md:53-128`、`:144-153`、`:171-179`、`:195-214`、`:377-380`、`:408`
- KD-75：`docs/known-defects.md:2844`
- `core/export/.../DeterministicMistakePdfRenderer.kt:62`、`:153`、`:155`
- `core/export/.../MistakePdfExporter.kt:36-43`、`:128`、`:130`、`:134`、`:135`、`:136`
- `core/export/.../MistakePdfEligibility.kt:170`、`:185-186`、`:318-329`
- `core/export/.../AiGeneratedImageCaption.kt:27`、`:30-36`
- `core/export/.../MistakePdfDelivery.kt:215`、`:227`、`:259`
- `core/data/.../capture/GeneratedFigureMetadata.kt:154`、`:157`、`:164`、`:167-180`
- `core/model/.../CapturedQuestionDocument.kt:17`、`:210`、`:370`、`:394-397`
- `core/data/.../model/OpenAiModelResponseParsers.kt:261`
- `core/export/build.gradle.kts:23-24`；`core/data/build.gradle.kts:169-199`
- `app/.../SmartMistakeBookApplication.kt:426`；`feature/library/.../MistakeExportHubRoute.kt:109`

### 本会话执行记录（可复跑）

```
python -I proto/make_fixtures.py D:/pdfres/2026-11-02/fixtures
python -I proto/inject.py fixtures/table.pdf out/table-aigc.pdf "$JSON"
python -I proto/inject.py fixtures/stream.pdf out/stream-aigc.pdf "$JSON"
python -I proto/inject.py out/table-aigc.pdf out/table-aigc-twice.pdf "$JSON"
python -I proto/inject.py out/stream-aigc.pdf out/stream-aigc-twice.pdf "$JSON"
python -I proto/verify.py D:/pdfres/2026-11-02
python -I proto/make_bad.py D:/pdfres/2026-11-02
# 然后对 6 个 bad 夹具逐一 inject，输出见 §8.6
```

工具版本：Python 3.13.14、pypdf 6.16.2、PyMuPDF 1.28.2。

---

## 11. 一句话给主会话

**`/AIGC` 是 Info 字典里的一个 JSON 文本串，不是 PDF 子字典；零依赖的正确做法是追加 Info 新副本 + 同型 xref 段 + 带直接 `/Prev` 的 trailer + `%%EOF`，原字节不动，Info 做 token-aware 字节拼接以保留 Title/Author，已有 `/AIGC` 则替换不重复；7 键按官方模板写、后 4 键空串，非 ASCII 拒绝；畸形/加密/线性化/hybrid/对象流 Info 一律有码 fail-closed。PdfPig 92 / pyHanko 87 / pdf.js 74，只移植算法不引依赖。PoC 在表型/流型上 4/4 通过，但 Android PDFium 接受度未跑设备，且计划"落 core/data"与 `core:export` 的依赖图存在接缝矛盾——这两项是 WS1 落地前必须先裁定的。**
