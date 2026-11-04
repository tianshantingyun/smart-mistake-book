# KD-75 C2 研究：导出内容里「模型解析文本」的可判定识别

> 批次 43 · 研究线 R1（计划 §1 第 6 项）→ 服务实施 WS1 item 1–10（判据、注入点、负向、缓存代际）。
> 本文件**只做研究与落点分析，不改任何代码**；`core/export` 的实际 hunk 由实施线按本文 §8 执行。
>
> **本轮实读/实跑**：`core/model`、`core/export`、`core/data`、`core/domain`、`feature/capture` 相关源码逐文件实读（行号见正文）；`git grep` 全库枚举 `provenance =` / `MODEL_DOCUMENT_PARSE` / `mistake-export-v` / `AIGC_POLICY_SENTINEL`；官方 PDF 下载 + `sha256sum` + `pdftotext` 抽取（§10.1）。
> **本轮未跑**：任何 Gradle / 单测 / 设备 / adb。文中“测试已存在”均指**源码在树**，不代表本轮执行过；新增测试一律标“待实施线跑”。

---

## 1. 结论（五个问题各一句）

| 问题 | 结论 | 依据 |
|---|---|---|
| 数据来源 | `CapturedQuestionDocument.blockEvidence[*].provenance`。生产侧**只有一个**写入点：`OpenAiModelResponseParsers.kt:261`。已提交文档带 `MODEL_DOCUMENT_PARSE` 是合法状态（`LOCAL_POLICY_ACCEPTED` 可提交）。 | §2 |
| 判据函数 | 已在树且零 Android 依赖：`PdfAigcContentPolicy.hasModelParsedText(names)` + `shouldLabel(image, text)`（`PdfAigcContentPolicy.kt:31-38`）。缺的不是判据，是**把判据接到导出输入**。 | §3 |
| 注入点 | 唯一：`MistakePdfExporter.labelPdfInPlace`（`:233-258`）。改法是把 `:236` 的字面 `false` 换成 `input.hasModelParsedText`，**同一个 `shouldLabel` 调用**，不新增第二条 if。 | §4 |
| 纯用户负向 | 纯用户文档的 evidence 是 `USER_TRANSCRIPTION`/`USER_CORRECTION`，不含 `MODEL_DOCUMENT_PARSE` → 不注入，字节逐位不变。三层已有或可加的负向见 §5。 | §5 |
| 缓存代际 | **必须升版**。现行是哨兵 `.aigc-policy-gen-1`（`MistakePdfExporter.kt:199`）+ 指纹字面 `"mistake-export-v3"`（`MistakePdfEligibility.kt:186`）；C2 上线要同时改：指纹字面含“判据代际”、哨兵 gen-1→gen-2、卷面草稿 `FORMAT_VERSION` 1→2。 | §6 |

一句话：**这条链现在是“判据有、事实有、管道没接”**——`PdfAigcContentPolicy` 已存在且被单测/设备用例覆盖（但只在纯函数层），而 `labelPdfInPlace` 硬编码 `hasModelParsedText = false`（`MistakePdfExporter.kt:236`），且 `MistakePdfExportInput` 根本没有这个字段。这正是 KD-75 维持 open 的 C2 缺口（`docs/research/2026-11-04-batch-42-record.md:541`）。

---

## 2. 数据来源：从模型响应到导出资格层的完整事实链

### 2.1 写入点（唯一）

| 环节 | 位置 | 事实 |
|---|---|---|
| 模型响应解析 | `core/data/.../model/OpenAiModelResponseParsers.kt:254-266` | `sanitized.document.blocks.mapIndexed` 逐块构造 `QuestionBlockEvidence`，**每块**固定写 `provenance = QuestionBlockProvenance.MODEL_DOCUMENT_PARSE`（`:261`）、`reviewStatus = NEEDS_REVIEW`（`:263`）、`producerVersion = modelVersion`（`:264`）。 |
| 覆盖的块类型 | 同上 `:190-240` | `paragraph` / `formula` / `choice_group` / `figure` / `figure_request`（后者正文落“图示：…”占位段）/ `diagram_note`（同样落占位段）。**六类块全部带 MODEL_DOCUMENT_PARSE**。 |
| 枚举定义 | `core/model/.../CapturedQuestionDocument.kt:205-213` | 7 个常量：`USER_TRANSCRIPTION`、`USER_CORRECTION`、`LOCAL_OCR`、`OPTIONAL_REMOTE_OCR`、`MODEL_DOCUMENT_PARSE`、`DETERMINISTIC_RULE`、`IMPORTED_STRUCTURE`。 |

**全库枚举（本轮 `git grep -n "provenance ="` 实跑）**：生产主码里对 `QuestionBlockProvenance` 的赋值只有 4 处——

- `OpenAiModelResponseParsers.kt:261` → `MODEL_DOCUMENT_PARSE`
- `LocalOcrQuestionDocumentMapper.kt:44,66` → `LOCAL_OCR`
- `RoomCaptureWorkflowRepository.kt:1116` → `USER_TRANSCRIPTION`（手动建题，`sourceAsset == null`）
- `RoomCaptureWorkflowRepository.kt:1126` → `IMPORTED_STRUCTURE`（照片链初始块）

`OPTIONAL_REMOTE_OCR` 与 `DETERMINISTIC_RULE` **在生产零赋值**（同一次 grep 零命中）。这一条直接决定 §3.2 的“哪些 provenance 算模型文本”。

### 2.2 存储与读取：为什么 `Ready` 里一定有对齐的 evidence

| 环节 | 位置 | 事实 |
|---|---|---|
| 落库 | `core/data/.../mistake/RoomMistakeDetailRepository.kt:200` | `CapturedQuestionDocumentCodec.encode(document)` 整份快照（含 `blockEvidence`）入 `questionDocumentSnapshot` 列。 |
| 读回 | `:389-400` | `decodeCommittedDocument` → `MistakeDetailState.Ready(questionDocument = document)`；快照为空 → `Legacy`，解码/校验失败 → `CorruptSnapshot`。 |
| 读回时的硬门 | `:455-459` | `CapturedQuestionDocumentCodec.decode(snapshot).takeIf { validateForCommit(it).isEmpty() }`；不满足即 `null` → `CorruptSnapshot`。 |
| 校验内容 | `core/model/.../CapturedQuestionDocument.kt:316-336` + `:245-314` | `validateForCommit` ⊇ `validateDraft`：`evidenceIds != blockIds` → `EVIDENCE_DOES_NOT_MATCH_BLOCKS`；非用户来源缺 `producerVersion` 也拒。 |

因此：**任何能到达 `MistakePdfEligibility.check` 的 `Ready` 文档，`blockEvidence` 与 `blocks` 必然 1:1 有序对齐，且 provenance 必然是合法枚举值。** 判据不需要“证据可能缺失”的兜底分支——这不是假设，是读取路径已经在执行的 fail-closed（`:455-459`）。

资格层再校验一次：`MistakePdfEligibility.kt:81-86` 调 `validateForCommit`，失败即 `UNCONFIRMED_OR_INVALID_DOCUMENT`（不可导出）。`Legacy` 在建库读取处就已分流，**markdown 兜底路径无法把无 evidence 的文档送进导出**（`MistakePdfEligibility.kt:68`）。

### 2.3 为什么“已提交文档”仍可能带模型解析文本

1. 解析产物 `reviewStatus = NEEDS_REVIEW`（`OpenAiModelResponseParsers.kt:263`）。
2. 用户在采集页保存时，`CaptureWorkspacePolicy.withFinalEvidenceDefaults`（`feature/capture/.../CaptureWorkspacePolicy.kt:391-407`）把未被用户改动的块标成 `LOCAL_POLICY_ACCEPTED`。
3. `COMMIT_ACCEPTED_STATUSES` 含 `LOCAL_POLICY_ACCEPTED`（`CapturedQuestionDocument.kt:358-361`），提交门只要求状态，**不要求 provenance 改变**。

所以“用户没逐块改 → 直接提交”是正常路径，模型文本会原样留在已提交文档里。这正是 R1 §10.3 推翻“当前 PDF 无 AI 生成文本”的原因（`docs/research/2026-10-26-pdf-implicit-labeling.md:545-554`）。

### 2.4 用户改动如何改写 provenance（判据边界的唯一来源）

`CaptureWorkspacePolicy.kt:144-176`：`editBlock` 把被改块的 evidence 改成——

- 原 `USER_TRANSCRIPTION` → 保持 `USER_TRANSCRIPTION`；
- 其余（含 `MODEL_DOCUMENT_PARSE`）→ `USER_CORRECTION`，并把 `confidence/producerVersion` 清空、`reviewStatus = NEEDS_REVIEW`。

结论：
- **部分改**：文档里同时存在 `MODEL_DOCUMENT_PARSE`（未改块）与 `USER_CORRECTION`（改过的块）→ 任一命中即打标。
- **逐块全改**：文档里不再有 `MODEL_DOCUMENT_PARSE` → **不打标**。这是本判据已知的假阴性边界，见 §5.3 的处置。

---

## 3. 判据函数

### 3.1 现状（已在树，未接管道）

`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcContentPolicy.kt`：

- `:24` `const val MODEL_DOCUMENT_PARSE_PROVENANCE = "MODEL_DOCUMENT_PARSE"`（与枚举 `name` 同名对齐，**字符串对齐而非类型引用**——这一点见 3.2 的风险）
- `:31-34` `fun shouldLabel(hasAiGeneratedImage: Boolean, hasModelParsedText: Boolean): Boolean = hasAiGeneratedImage || hasModelParsedText`
- `:37-38` `fun hasModelParsedText(provenanceNames: Iterable<String>): Boolean = provenanceNames.any { it == MODEL_DOCUMENT_PARSE_PROVENANCE }`（大小写敏感）

已有测试（源码在树，本轮未执行）：

- `core/model/src/test/.../pdf/PdfAigcContentPolicyTest.kt`（4 例：AI 图单独、模型文本单独、两者皆无、从名称集合派生）
- `app/src/androidTest/.../pdf/PdfAigcDeviceRoundTripInstrumentedTest.kt:213-231` `modelParsedTextForcesAigcLabelling`（纯函数 + 注入器读回）、`:233-245` `userOnlyContentStaysUnlabelled`

**缺口不在函数，在调用方**：`core/export/src/main` 对 `blockEvidence`/`QuestionBlockProvenance` 本轮 grep **零命中**（`git grep -n "blockEvidence\|QuestionBlockProvenance" -- 'core/export/src/main/**/*.kt'`），即导出链目前完全看不到 provenance。

### 3.2 判据的“哪些来源算模型文本”——建议收成显式白名单 + 防漂移断言

不建议直接把“非用户来源都算”。按本轮枚举结果给出裁定表：

| provenance | 算模型文本？ | 理由 | 依据 |
|---|---|---|---|
| `MODEL_DOCUMENT_PARSE` | **是** | 唯一生产写入点，语义就是“模型解析出的正文” | `OpenAiModelResponseParsers.kt:261` |
| `USER_TRANSCRIPTION` | 否 | 用户手敲；《办法》第十条“不得伪造标识”的反面 | `RoomCaptureWorkflowRepository.kt:1116`、`CaptureWorkspacePolicy.kt:156-163` |
| `USER_CORRECTION` | 否 | 改的是别人产出的块，但**改后的文本由用户写**；是否继承“AI 血统”缺一个可靠字段（见 §5.3） | 同上 `:165` |
| `LOCAL_OCR` | 否 | 本地 OCR 是确定性识别，不是生成式推理；官方指南的触发对象是“AI 生成合成内容” | `LocalOcrQuestionDocumentMapper.kt:44,66`；20258A 适用于文本文件**标识方法**，触发条件见《办法》§3.1（R1 转录） |
| `OPTIONAL_REMOTE_OCR` | 否（**当前零生产赋值**） | 若将来接入，属远程识别而非生成；接之前先按 3.3 的漂移测试迫使人做决定 | 本轮 grep 零命中 |
| `DETERMINISTIC_RULE` | 否 | 确定性规则 | 本轮 grep 零命中 |
| `IMPORTED_STRUCTURE` | 否 | 照片链初始块，正文是空串/占位，不是模型产出 | `RoomCaptureWorkflowRepository.kt:1126` |

建议把判据改成**显式集合**而不是单常量比较：

```kotlin
// core/model/.../pdf/PdfAigcContentPolicy.kt
val AI_PARSED_PROVENANCE: Set<String> = setOf(MODEL_DOCUMENT_PARSE_PROVENANCE)
fun hasModelParsedText(names: Iterable<String>) = names.any(AI_PARSED_PROVENANCE::contains)
```

### 3.3 必须配的防漂移测试（本批“只可新增防漂移不变量测试”的落点之一）

三处真会漂的接口：

1. **枚举加值**：`QuestionBlockProvenance` 新增第 8 个常量（例如将来接 `OPTIONAL_REMOTE_OCR`）时，判据必须显式表态算不算。测试写成：

   ```kotlin
   // core/model/src/test/.../pdf/PdfAigcContentPolicyTest.kt
   @Test fun `every provenance is explicitly classified`() {
       assertEquals(
           setOf(QuestionBlockProvenance.MODEL_DOCUMENT_PARSE.name),
           QuestionBlockProvenance.entries
               .map { it.name }
               .filter { name -> PdfAigcContentPolicy.hasModelParsedText(listOf(name)) },
       )
   }
   ```
   新增枚举 → 该断言红 → 被迫决定。消灭的失败：**新模型来源静默漏标**。

2. **字符串与枚举脱钩**：`MODEL_DOCUMENT_PARSE_PROVENANCE` 是手写字符串（`:24`）。加一条 `assertEquals(QuestionBlockProvenance.MODEL_DOCUMENT_PARSE.name, PdfAigcContentPolicy.MODEL_DOCUMENT_PARSE_PROVENANCE)`。消灭的失败：枚举改名后常量不跟着改 → 判据永远 false（**全量漏标且测试全绿**，最隐蔽的一种）。

3. **判据代际与缓存代际一致性**：见 §6.4。

---

## 4. 与 AI 图同一注入点的接法

### 4.1 注入点唯一性（先证“只有一个”）

`MistakePdfExporter.prepare`（`:119-151`）的顺序是：

```
render(input, temporaryPdf)            :134
→ 未打标大小判限                       :135-139
→ labelPdfInPlace(temporaryPdf, input) :140   ← 唯一注入点
→ validatedPageCount(temporaryPdf)     :141   （PdfRenderer 真打开注入后字节）
→ writeDigest(sha256File)              :142   （摘要取注入后字节）
→ publish (rename 原子)                :143
```

`labelPdfInPlace`（`:233-258`）内部就是一次 `shouldLabel` → 大小护栏 → `PdfAigcInjector().inject` → 回写。**AI 图与模型文本必须共用这一次调用**：若另起一条 `if (input.hasModelParsedText) inject(...)`，两条分支的字节顺序/摘要/原子性各自演化，正是 R1 §2.4 警告的那类“注入点漂移”。

### 4.2 最小改法（三处 hunk + 一处 KDoc 纠偏）

**(a) 输入加事实字段**——`MistakePdfEligibility.kt:318-370` `class MistakePdfExportInput`：

```kotlin
/** 文档块证据里出现过 MODEL_DOCUMENT_PARSE（资格层派生，见 PdfAigcContentPolicy）。 */
val hasModelParsedText: Boolean,
```

- **建议无默认值**（必填）。理由：`internal constructor` 把构造点锁在 `core:export` 模块内，全部构造点 = 4 个生产 + 12 个测试构造（9 个测试文件，本轮 grep 实列，见 4.3）；必填让**每个**构造点在编译期表态。若给 `= false` 默认值，将来新增构造点会静默漏标——那正是本项要消灭的失败。
- **不建议**携带 `List<String>` 或整份 evidence：判据只需一个布尔，多带的字段进入指纹/草稿 DTO 只会扩大漂移面（§12.2：指认不出消灭的失败就不加）。

**(b) 资格层派生**——`MistakePdfEligibility.checkReady`（`:117-212`）：

```kotlin
val provenanceNames = document.blockEvidence.map { it.provenance.name }
val hasModelParsedText = PdfAigcContentPolicy.hasModelParsedText(provenanceNames)
```

并在 `MistakePdfExportInput(...)`（`:161-212`）传入。**不要**在 `blocks.map{}`（`:119-152`）里逐块塞字段——块映射只承载渲染需要的东西，provenance 是输入级事实。

**(c) 注入点改一行**——`MistakePdfExporter.kt:233-240`：

```kotlin
if (!PdfAigcContentPolicy.shouldLabel(
        hasAiGeneratedImage = input.paintsAiGeneratedImage(),
        hasModelParsedText = input.hasModelParsedText,   // :236 由字面 false 改为字段
    )
) { return }
```

**(d) KDoc 纠偏（必做，不是美化）**：

- `MistakePdfExporter.kt:229-231` 现写“`hasModelParsedText = false` 是本代输入的硬事实”——改完后这句话变成**反向断言**，会让下一位读者以为 C2 仍未接。
- `PdfAigcContentPolicy.kt:19` 现写“本批只落判据，不改进导出调用点”——同样过期。

这两条属于 §12.1 的“内里未闭合”：注释与代码相反时，下一次审计会以注释为准判定“已/未完成”。**消灭的失败：后续批次据过期 KDoc 把 KD-75 误判为已实现或反之。**

### 4.3 全部构造点（`git grep -n "MistakePdfExportInput("` 本轮实跑）

| 文件 | 行 | 需要做什么 |
|---|---|---|
| `core/export/.../MistakePdfEligibility.kt` | 161 | **派生**（唯一真值来源） |
| `core/export/.../MistakePdfBatchEligibility.kt` | 105 | **任一为真**：`inputs.any(MistakePdfExportInput::hasModelParsedText)` |
| `core/export/.../MistakePdfPaperEdit.kt` | 219 | 透传 `sourceInput.hasModelParsedText`（卷面编辑不改变内容出处） |
| `core/export/.../MistakePaperDraftStore.kt` | 187 | 草稿 DTO 往返，见 §6.3 |
| 测试 12 处（9 文件） | `AiGeneratedImageCaptionTest:207,227`、`DocxMistakeRendererTest:509`、`MistakeExportVisualRoleTest:752,774`、`MistakePdfExporterAigcJvmTest:162`、`MistakePdfLayoutTest:113`、`MistakePdfPagePlannerLayoutTest:458`、`MistakePdfPagePlannerTest:264`、`MistakePdfPaperEditTest:335,502`、`MistakePdfPlannerGoldenTest:133` | 补 `hasModelParsedText = false`（非本项语义的既有夹具），或按用例意图给 true |

**批量链的传递性**：`buildBatchExportInput` 的 `combinedFingerprint` 收进了每道题的 `inputSha256`（`MistakePdfBatchEligibility.kt:94-104`），而单题 `inputSha256` 的组成里含 `documentFingerprint`（`MistakePdfEligibility.kt:193`），后者 = 整份 `CapturedQuestionDocument` 的快照摘要（`CapturedQuestionDocument.kt:394-397`，`blockEvidence` 在序列化体里，`CapturedQuestionDocumentCodec.encode` `:375-382`）。**所以 provenance 变化本身已经改变缓存键**——这一点必须用一条测试钉住（见 §6.4 第 2 条），否则后续谁把 `documentFingerprint` 换成“只对 blocks 取摘要”就会静默引入跨 provenance 的缓存串用。

### 4.4 顺序与失败语义（不改）

- 打标仍在 `validatedPageCount` 之前：注入后的字节由 `PdfRenderer` 真实打开（`:141`）。
- 摘要仍在注入之后（`:142`），`publish` 的 rename 让“带标记”与“已发布”原子。
- 注入失败 → `MistakePdfExportException(GENERATED_PDF_INVALID)`（`:245-249`），pending 目录由 `finally` 删除（`:147-149`），**不产出无标记文件**。模型文本走同一路径，无需新增错误码。
- 大小护栏两道：注入前 `file.length() > 12 MiB`（`:242-244`），注入后 `> input.maxPdfBytes`（`:250-252`）。纯文本 PDF 注入增量约数百字节，正常不会触顶；触顶即显式失败。

---

## 5. 纯用户内容的负向

### 5.1 三层负向（现状 + 待补）

| 层 | 断言 | 现状 |
|---|---|---|
| 判据层 | `shouldLabel(false, false) == false`；`hasModelParsedText` 对纯用户 provenance 名集合为 false | 部分在树：`PdfAigcContentPolicyTest.kt` 已有 `shouldLabel(false,false)` 与“名称集合派生”两例，但用的是**合成名**（`USER_ENTERED`/`OCR_TRANSCRIPT`），不是真实枚举名 → 需用 `USER_TRANSCRIPTION`/`USER_CORRECTION` 补一例（与 §3.3 第 1 条同一文件） |
| 导出输入层 | 纯用户 `Ready` → `input.hasModelParsedText == false` | **待补**（`MistakePdfEligibilityTest.readyState` `:349-371` 现在把 evidence 固定写成 `USER_CORRECTION`，正好是负向夹具，只需补断言） |
| 字节层 | `labelPdfInPlace(file, input)` 对纯用户内容**字节逐位不变**、读回无 `/AIGC` | 在树：`MistakePdfExporterAigcJvmTest.kt:123-132`；设备侧 `pure-user-unlabeled.pdf`（批次 42 `E-42-DEVICE-01 §1`，无 `/AIGC`） |

### 5.2 负向的边界（必须写进测试名，不要只写在文档里）

以下“看起来像 AI、实际不标”的情形，各需要一条负向/边界用例：

1. `QUESTION_DIAGRAM` 题图（题目自带示意图，非模型产物）→ 不标。已有：`MistakePdfExporterAigcJvmTest.kt:46-53`。
2. `MistakePdfBlock.Figure` 程序化图形（确定性代码绘制）→ 不标。依据：R1 §4.3（`docs/research/2026-10-26-pdf-implicit-labeling.md:295-305`）。
3. 纯用户文本 + 无图 → 不标。已有（§5.1）。
4. **无图陷阱**：`isAiGeneratedExportImage(null) == true`，必须与“确实有这张图”相与（`MistakePdfExporter.kt:210-219` 的 `cleanImageLocalUri != null && ...`）。已有：`missing image stays unlabeled even though a null role means AI`（`MistakePdfExporterAigcJvmTest.kt:55-61`）。
5. 模型只产出 `Figure` 块（标题/替代文本来自模型）→ **标**（保守）。这条是正向后置条件，建议单独一例，防止“只有文本块才算”被误实现。

### 5.3 已知假阴性：全部块都被用户改过

- 事实：`editBlock` 把 `MODEL_DOCUMENT_PARSE` 改成 `USER_CORRECTION`（`CaptureWorkspacePolicy.kt:160-166`）；若用户逐块改完全部块，文档里不再有 `MODEL_DOCUMENT_PARSE` → 不标。
- 判断：**保留现状，不改 schema**。理由：(a) 逐块重写的正文在语义上已是用户内容；(b) 要精确处理必须新增“该块由模型产出后被用户改正”的血统字段（如 `correctedFromProvenance`），那是 `CapturedQuestionDocument` schema 变更 + 全链指纹/迁移，消灭的失败面很窄；(c) 反向风险（把全用户内容打标）会直接违反《办法》第十条“不得伪造标识”。
- 登记方式：`docs/known-defects.md` KD-75 的边界段写一句“逐块全改的文档不触发 `/AIGC`，属有意保守的反向边界”，并给一条用例把该行为**钉死**（`fully corrected document stays unlabeled`），避免将来有人当成 bug 去“修”成过度打标。

> 若主会话要求收紧，唯一诚实做法是加血统字段；**不要**用“只要 block 数量 ≥1 且曾有模型任务”这类外部状态反推——那会让同一份内容在不同会话得到不同标识。

---

## 6. 缓存代际升版（C4）

### 6.1 现行为什么不够

`prepare` 的第一步就是 `store.findExisting(input)`（`MistakePdfExporter.kt:126-129`），命中即直接返回**不校验是否带 `/AIGC`**；`publish` 内部还会再 `findExisting` 一次（`:452`）。而缓存目录名 = `input.inputSha256`（`:471-474`）。

因此：**同一份文档、同一个 provenance、在同一台设备上先导出过（旧判据，无标记），升级后必须不能复用它。** 这就是“判据代际”必须进键的原因，与 `documentFingerprint` 是否包含 provenance 无关——后者的变化只能覆盖“内容变了”，覆盖不了“判据变了”。

### 6.2 三条机制（现状）

| 机制 | 位置 | 语义 |
|---|---|---|
| 指纹前缀字面 | `MistakePdfEligibility.kt:185-209`，`:186` `"mistake-export-v3"` | 单题缓存键；批量键 = `"mistake-batch-v3"` + 各题 `inputSha256`（`MistakePdfBatchEligibility.kt:94-104`）；卷面编辑键 = `paperContentFingerprint(sourceInputSha256, ...)`（`MistakePdfPaperEdit.kt:213-218`） |
| 策略代际哨兵 | `MistakePdfExporter.kt:199` `.aigc-policy-gen-1`，消费点 `:123`/`:163` | `ensurePolicyGeneration`：哨兵不存在 → **一次性删除全部已发布产物** → 最后创建哨兵（`:309-314`）。`reopenVerified` 也会触发，因此旧 token 同样失效 |
| 草稿格式版本 | `MistakePaperDraftStore.kt:227` `FORMAT_VERSION = 1` | 版本不符 → `toModelOrNull` 返回 null → 编辑稿导出如实失败 |

KD-75 的 KDoc 已把这条写成硬约束：`MistakePdfExporter.kt:229-231`“补 (b) 判据时必须同时改 `AIGC_POLICY_SENTINEL` 的代际名”。本项必须兑现它。

### 6.3 建议的升版组合（三处一起改）

1. **判据代际单源**：在 `core/model/.../pdf/PdfAigcContentPolicy.kt` 增

   ```kotlin
   /** 判据/注入行为的代际：改判据、改写入范围、改注入算法都必须 +1。 */
   const val GENERATION: Int = 2
   ```

   （实现建议：**用 `internal val`（非 `const`）派生**，这样不依赖 `const val` 的常量表达式规则；引用点只有 `MistakePdfExporter.kt:123,163` 与 androidTest 两处（`MistakePdfExporterAigcInstrumentedTest.kt:162,190`），`const`→`val` 对它们透明。**注**：本轮无 `kotlinc`（`command -v kotlinc` 未安装），未验证 `const val` 能否吃 `Int` 模板，故不把“必须改 val”写成语言事实。）

   ```kotlin
   internal val AIGC_POLICY_SENTINEL = ".aigc-policy-gen-${PdfAigcContentPolicy.GENERATION}"
   ```

2. **指纹字面含代际**：`MistakePdfEligibility.kt:186` 的 `"mistake-export-v3"` 改为

   ```kotlin
   "mistake-export-v4-aigc-gen-${PdfAigcContentPolicy.GENERATION}"
   ```

   命名常量放 `core:export`（如 `internal fun exportFingerprintPolicyTag(): String`），资格层与测试共用同一函数。**为什么既要 v3→v4 又要带 gen**：v4 表达“本代与上一代键空间不相交”，gen 表达“判据世代”，两者同源后，下次改判据只需 `GENERATION + 1` 而不会忘（§6.4 第 1 条测试会红着提醒）。

3. **草稿格式版本 1→2**：`MistakePaperDraftStore.kt:227`。原因见 §6.5。

### 6.4 必须配的三条防漂移测试

1. **代际三方一致**（新，`core:export` JVM）：

   ```kotlin
   assertEquals(".aigc-policy-gen-2", MistakePdfExporter.AIGC_POLICY_SENTINEL)
   assertEquals("mistake-export-v4-aigc-gen-2", exportFingerprintPolicyTag())
   assertEquals(2, PdfAigcContentPolicy.GENERATION)
   ```
   消灭的失败：**改了判据忘了升某一处** → 旧无标包在 TTL 内被复用（KD-75 的 reopen condition 明写“旧缓存指纹已升版且无旧无标识产物”）。

2. **provenance 进入文档指纹**（新，`core:model` JVM）：
   两份 blocks 完全相同、仅 `blockEvidence[0].provenance` 分别为 `USER_TRANSCRIPTION` / `MODEL_DOCUMENT_PARSE` → `CapturedQuestionDocumentFingerprint.of(a) != of(b)`。
   消灭的失败：将来有人把文档指纹改成“只对 blocks 取摘要”，模型文本题与纯用户题的缓存键相撞，互相复用对方产物（一个漏标、一个多标）。

3. **同键旧无标产物必须被拒**（设备，**扩展现有**）：
   `MistakePdfExporterAigcInstrumentedTest.kt:155-194` 的 `oldUnmarkedArtifactUnderTheSameKeyIsRejectedAndRebuiltWithAigc` 现在只用“含 AI 图”的输入造 stale 产物。**加一条同构用例，输入换成“只有模型解析文本、无图”**：`reopenVerified(oldToken) == null`、`prepare` 后读回 `Label=1`、`ProduceID == inputSha256`。
   消灭的失败：C2 的判据在 prepare 生效、但 reopen/复用路径仍放行旧包。

### 6.5 升版的连带影响（必须一起交代，别当成“顺手”）

| 连带项 | 事实 | 处置 |
|---|---|---|
| **DOCX 共用同一把键** | `MistakeDocxExporter.kt:69,84` 用 `input.inputSha256` 作目录名/键 | 指纹升版会同时失效 Word 暖缓存 → 重渲染一次。行为不变、代价可接受；**必须知道**，否则会误以为“只影响 PDF” |
| **历史导出记录的 token 失效** | `MistakeExportRecord.preparedPdfValueOrNull()` 拼 `input:pdf:pages`（`core/domain/.../MistakeExportRecord.kt:83-89`）；`MistakeExportHubRoute.kt:117-127` 的 `artifactAvailable` 走 `reopenVerified` | `reopenVerified` 返回 null → `artifactAvailable=false` → UI 提示重新导出（`:142` 已有“文件状态已变化，请重新打开后保存。”）。fail-closed 行为已存在，无需新 UI |
| **卷面草稿 DTO 缺字段** | `PaperInputDto`（`MistakePaperDraftStore.kt:162-184`）没有 provenance，且 `Json { ignoreUnknownKeys = false }`（`:234-240`） | 已发出的旧草稿（TTL 48h，`:230`）没有该字段。**隐式结果**：DTO 字段无默认值时旧 JSON 解码抛缺键 → `runCatching` 返回 null（旧草稿被拒）；但**不要依赖这个隐式行为**（将来谁给字段加个 `= false` 默认值，旧草稿就会静默变成“无模型文本”→ 漏标）。**显式做法**：`FORMAT_VERSION 1→2`，让拒绝在评审里可见，失败文案为“这份编辑后的卷面没有保存下来，请重新编辑后再导出。”（`MistakeExportJobRunner.kt:139-142`） |
| **基线会变** | 单题/批量/编辑三条键全变 | `MistakePdfPlannerGoldenTest` 等若断言了 `inputSha256` 字面量需同步；本轮未逐一核对哪些 golden 钉了该值 → 标 UNVERIFIED（§9） |

---

## 7. 影响面闭合表（§12.1：改一处，逐项写“改到/为何不用改”）

| 被牵动方 | 是否改 | 说明 |
|---|---|---|
| `PdfAigcContentPolicy`（core:model） | 改 | 加 `GENERATION`、可选显式集合、KDoc 纠偏 |
| `MistakePdfExportInput`（core:export） | 改 | 加必填 `hasModelParsedText` |
| `MistakePdfEligibility`（core:export） | 改 | 派生 + 指纹字面 |
| `MistakePdfBatchEligibility` | 改 | OR 汇总（键自动随单题指纹变化） |
| `MistakePdfPaperEdit` | 改 | 透传 |
| `MistakePaperDraftStore` | 改 | DTO 字段 + `FORMAT_VERSION` |
| `MistakePdfExporter` | 改 | 注入点一行 + 哨兵 + KDoc |
| `MistakeDocxExporter` / `DocxMistakeRenderer` | **不改** | 不写 `/AIGC`（`docProps/custom.xml` 是 20258A §6.1 的对称缺口，另立条目，R1 U9）；只承受键升版的缓存失效 |
| `MistakePdfDelivery.writeSubset`（`:215-270`） | **不改，但要登记** | 部分页打印用 `PdfRenderer` 位图重绘进新的 `PdfDocument`（`:217-266`），**文档级 `/AIGC` 必然丢失**（这是 C8，不在本项范围）；整份打印走 `copyWhole`（`:208-213`）保留标记 |
| `DeterministicMistakePdfRenderer` / 页面规划 | 不改 | 判据只影响 Info 字典，不影响版面与像素 |
| `MinimalDocxWriter` | 不改 | 同上 |
| `core/domain` / `core/ui` / `feature/*` / `docs/current` | **不改** | 本项全部落点都不在此；`CaptureWorkspacePolicy` 只是被读，不动 |
| `settings.gradle.kts` / `Application` / `Root` | 不改 | — |
| 数据库 schema | **不改** | 无新表/列；`blockEvidence` 已在既有快照 JSON 内 |
| 请求 schema / `availableMethods` / JS 广告面 | **不改** | 判据纯本地，不派发任务 |

---

## 8. 实施线落点（映射计划 WS1 item 1–10）

| item | 落点 | 先红用例（建议名） |
|---|---|---|
| 1 识别来源 + 判据 | `PdfAigcContentPolicy` + `MistakePdfEligibility` | `model parsed evidence sets hasModelParsedText`（`MistakePdfEligibilityTest`） |
| 2 同一注入点 | `MistakePdfExporter.kt:233-240` | `labelPdfInPlace labels model parsed text without any image`（`MistakePdfExporterAigcJvmTest`） |
| 3 纯用户负向 | 三层（§5.1） | `pure user document stays unlabeled through eligibility`；边界 5 条（§5.2） |
| 4 字面版本升版 | §6.3 三处 | `policy generation is consistent across sentinel, fingerprint and policy` |
| 5 JVM 三类内容 | `MistakePdfExporterAigcJvmTest` | AI 图 / 解析文本 / 纯用户 三例同文件，共用 `labelPdfInPlace` |
| 6 设备：解析文本导出读回 | `MistakePdfExporterAigcInstrumentedTest` 新例 | 真实 `prepare` → `PdfAigcReader` 读回；另用 pypdf 独立读回（独立读者纪律，见批次 42 `E-42-DEVICE-01 §1`） |
| 7 设备：纯用户无 `/AIGC` | 同上 | 复用 `pure-user-unlabeled.pdf` 口径 |
| 8 旧无标记缓存不回退 | §6.4 第 3 条 | 现有用例加“只含模型文本”参数化 |
| 9 C1–C8 对账 | `docs/known-defects.md` KD-75 段 | 逐条写“满足/不满足 + 证据” |
| 10 台账回写 | 同上 | 依实测决定关闭或维持 |

**先红后绿的顺序**（否则无法证明测到了东西）：先加判据/输入字段与**会红的导出用例**（字面 `false` 仍在，断言 `/AIGC` 存在 → 红）→ 再改注入点（绿）→ 最后升版（键变化的用例红→绿）。

---

## 9. 未验证与风险

| # | 项 | 状态 |
|---|---|---|
| U1 | 上述新增/修改测试**一条都没跑** | UNVERIFIED（本轮零 Gradle，纪律要求门禁期串行跑） |
| U2 | 指纹升版后哪些 golden/字面断言会红 | **未核对**（未逐个读 `MistakePdfPlannerGoldenTest` 等是否钉 `inputSha256`） |
| U3 | `MODEL_DOCUMENT_PARSE` 在真实 Provider 输出上的块类型分布 | UNVERIFIED（无 Provider；`FakeModelGateway.kt:125` 只覆盖测试替身） |
| U4 | 设备 pypdf 对“只含文本、无图”的导出 PDF 的读回 | 未执行；批次 42 只验了含图 PDF（`real-export-labeled.pdf`） |
| U5 | 部分页打印丢 `/AIGC`（C8） | 已知缺口，**不在本项**，`MistakePdfDelivery.writeSubset:215-270` |
| U6 | DOCX 侧 `docProps/custom.xml` | 已知缺口（R1 U9），**不在本项** |
| R1 | 必填字段会触及 12 个测试构造点（9 文件） | 预计一次性编译红，属预期成本；若被要求“最小 hunk”，退路是把默认值设为 `false` **并**同时加 §6.4 第 1/2 条测试兜底（但会留下“新构造点静默漏标”的口子，**不推荐**） |
| R2 | 把 `const val` 改成 `val` 触发 `AIGC_POLICY_SENTINEL` 的可见性变化 | 仅 `internal`，影响点已列（2 生产 + 2 测试） |

---

## 10. 来源与采纳/拒绝理由

### 10.1 采纳（本轮实跑验证）

1. **TC260-PG-20258A《网络安全标准实践指南—人工智能生成合成内容标识方法 文件元数据隐式标识 文本文件（V1.0-202508）》**
   - URL：<https://www.tc260.org.cn/tc260/sjzn/202508/f0a84ada0c11458e9cb6da56c1cb2426/files/760387fb30e94c058c31b8c2b2230020.pdf>
   - 本轮命令与结果（**实跑**）：
     ```
     curl -sSL --max-time 45 -o tc260-20258a.pdf <上述 URL>     → exit 0
     sha256sum tc260-20258a.pdf → 6bf6c1189208856099bfbf648bdf386da288b1df00f430502b395b874dbf3b0e（3,272,625 B）
     file → PDF 1.7, 20 pages
     pdftotext tc260-20258a.pdf - | grep -n "5\.1\|6\.3" →
       "5.1 ... c)  PDF  pdf"
       "6.3  PDF   PDF  a)  Document Information Dictionary; b) /AIGC({...})"
     ```
   - 采纳理由：官方一手；文件完整性与批次 23 记录的 `6bf6c118…` 前缀一致；§6.3 的载体（Document Information Dictionary）与键名 `/AIGC` **本轮直接抽取原文确认**。
   - 用途：只用于**方法**（写哪里、写什么键）；不用于判断“本 App 的某份 PDF 是否被触发”。

2. **项目内 R1《PDF 隐式标识》** — `docs/research/2026-10-26-pdf-implicit-labeling.md`
   - 采纳理由：内部一手设计件，含 §10.3 对“当前无 AI 文本”的自我推翻与修正判据（`:545-554`）、§4.5 注入算法、§2.4 缓存失效三方案。本文件的判据表直接续用它。
   - 用途：§3 判据边界、§6.1 缓存机制、§5.2 图/文本分类。

3. **项目源码** — 见正文行号。采纳理由：判据的“可判定性”最终只能由代码事实决定；本轮逐文件实读。

### 10.2 拒绝 / 不作为依据

1. **TC260-PG-20259A（图片文件）** — 拒绝对 PDF 场景引用其载体：它规定的是**图片文件**的 XMP `TC260:AIGC`（R1 记录 §6），本项是 PDF Info 字典；混用会写出标准读者不读的元数据。理由与 R1 §3.4 一致（`2026-10-26-pdf-implicit-labeling.md:541`）。
   URL（R1 记录，本轮未重下）：<https://www.tc260.org.cn/tc260/sjzn/202508/7227bfecdf344c14abc647fbbcf69edf/files/37774d2b3149456ba7179a81bc07d3d5.pdf>
2. **XMP 路线** — 拒绝：要重定义 Catalog + 新增流对象，成本约 2 倍，且不消灭 Info 方案未消灭的任何失败（R1 §3.4 / §2.2）。
3. **《人工智能生成合成内容标识办法》条文** — **本轮未重新取原文，也未取得官方发布页 URL**，只以 R1 转录的条文编号为准（“AI 生成合成文本内容”的适用范围、第十条禁止伪造标识）。标为**转录/待验证**：它不影响本项的技术判定（C2 是 R1 §10.3 已裁定的工程口径），只影响“是否必须标”的法规强度表述。若主会话需要法规级结论，应另起一次对官方发布页的抓取。
4. **`.worktrees/*` 下的同名文件** — 拒绝：那是其他工作树的旧快照（如 `mistake-batch-v1`），不是主树事实。主树现为 `mistake-batch-v3` / `mistake-export-v3`。

### 10.3 检索通道说明

- 外部抓取通道：`WebFetch` 对该 PDF 返回 `Unsupported WebFetch content type: application/pdf`（**通道失败，不等于来源不可用**）→ 改走 `curl` + `pdftotext` 成功。
- 本地枚举通道：`git grep` 覆盖 `*.kt`，命中唯一写入点；同时对 `OPTIONAL_REMOTE_OCR`/`DETERMINISTIC_RULE` **零命中**——按“零命中换渠道再判”，已换用 `provenance =` 全量枚举（而不只搜枚举名）复核，仍为零，判定为“生产未接入”而非“搜索遗漏”。

---

## 11. 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md` §1 R1、§2 WS1 1–10、§4 硬约束（`core/export` 只做 KD-75 最小 hunk；`core/domain`/`core/ui`/`feature/*`/`docs/current` 禁改）。
- 上一批：`docs/research/2026-11-04-batch-42-record.md` §9.5（KD-75 维持 open：C2/C4 未达）、§2.3（WS1 修复现状）。
- 判据与法规基线：`docs/research/2026-10-26-pdf-implicit-labeling.md` §3.4/§4.3/§4.5/§2.4/§10.3。
- 台账：`docs/known-defects.md:2884-2898`（KD-75 正文与 reopen condition）、`:2906`（Progress 块）。
- 设备口径：`docs/research/evidence/2026-11-04-batch-42/ws2-kd75-closeout.md`（C1–C8 表）、`E-42-DEVICE-01 §1`（pypdf 独立读回）。
- 本文件对应实施：批次 43 WS1 item 1–10；测试与门禁结果由实施/门禁线回填，本文不声称任何一条已通过。
