# R1：PDF 导出链的 `/AIGC` 注入位置与缓存代际（批次 42，驱动 WS2 items 13–22）

- 日期：2026-11-04
- 性质：**研究，零代码改动**。本项只读源码/文档 + 两条外部一手取证（AOSP 源码、Android 参考页快照）；**未运行 Gradle、未跑设备、未做字节级实跑、未做 git 写**。
- 输入：`.zcode/plans/plan-2026-11-04-batch-42.md` §1 R1、§2 WS2 items 13–22；`docs/research/2026-11-03-batch-41-record.md` §9.3（F7/F8/F10/F11/F12 移交项，与本项无重叠）；`docs/known-defects.md:2894`（KD-75 Fix direction）；`docs/research/2026-11-03-batch-41-record.md` §6.1-1。
- 上一批接缝草案：`docs/research/evidence/2026-11-03-batch-41/ws2-core-export-seam-patch.md`（未应用，本项在其上把"位置/缓存/失败码"三处裁定重算，并**推翻了它的一处前提**，见 §4.4）。
- 字节级格式不重复：注入器算法、Info 合并、xref/trailer 细节见 `docs/research/2026-11-02-pdf-incremental-aigc.md`（下称 R1-40）。

---

## 0. 结论摘要（先给 pick）

1. **注入位置唯一正确点是 `prepare` 的"render → 大小判限 → `validatedPageCount` 之前"，当前 `MistakePdfExporter.kt:128-135` 的第 133/134 行之间。** 该点的三条性质同时成立：`validatedPageCount`（`:134`）替我们验证 PDFium 接受增量段；`writeDigest`（`:135`）对**注入后**字节算摘要，于是 `sha256`/记录摘要/`verifyIntegrity()`/`publish` 的返回摘要四处同源；`publish`（`:136`）的 `renameTo` 让"带标记"与"已发布"是同一个原子事件。
2. **在 `writeDigest` 之后注入的两种写法都会自毁**：摘要与字节不一致 → `findExisting`（`:309-311`）抛 `EXISTING_FILE_INTEGRITY_CONFLICT`。发布后原地改写更糟：`prepare` 每次先走 `findExisting`（`:120`），该键会**持续抛冲突最长 24h**（`:199-204` 只按 TTL 清理），用户对这一题反复导出失败。这是本项最硬的一条排除理由（§2）。
3. **"缓存键升版"本身不足以关闭旧包回退**——这是本项对批次 41 接缝草案的修正。`reopenVerified` 用 token 里的 `inputSha256` 直接拼目录（`:322`），token 来自导出记录的 `input_sha256` 列（`MistakeExportRecord.kt:83-89` → `MistakeExportHubRoute.kt:117-118`）。升级前落库的记录仍指向旧目录（TTL 内存在）→ **旧的无 `/AIGC` 包仍能被分享/打印**。v3→v4 只改新产物的键，不改旧 token 的解析。
4. **因此关闭旧包回退的机制必须同时覆盖"按输入重算"和"按 token 重开"两条路。** 推荐在 `MistakePdfExporter` 内做**代际哨兵 + 一次性清空**（`prepare` 与 `reopenVerified` 各一次，均在既有 `ExportRootLock` 内），哨兵名带策略代际；清空只删"形状完整的已发布对"（沿用 `publishedArtifacts()` 的过滤），因此 `incompleteExistingTargetFailsClosedInsteadOfBeingOverwritten`（`MistakePdfExporterInstrumentedTest.kt:198-218`）的 fail-closed 语义不被削弱。**该机制完全落在 `MistakePdfExporter.kt` 一个文件内**，符合本批"只改 MistakePdfExporter 最小 hunk"的约束。
5. **v3→v4 前缀本批不做**：它在有清空机制后对"新产物"是惰性的（清空后不存在任何可命中的旧产物），且要动 `MistakePdfEligibility.kt:186`、`MistakePdfBatchEligibility.kt:96` 两个**本批禁改**的文件；它的独立价值只剩"跨渠道/降级构建的产物不可达"，登记为残余风险（§4.5），留到改资格层的那一批与 (b) 判据一起做。**若只做 v4、不做清空，则第 3 条的回退路径原样存在。**
6. **判据 (b)"文档含 `MODEL_DOCUMENT_PARSE` 文本"在 `MistakePdfExporter` 内不可计算**：`MistakePdfBlock`（`MistakePdfEligibility.kt:372-425`）不携带 provenance，`questionDocumentSha256` 只是哈希。本批只能落 (a) AI 图判据；(b) 需要 `MistakePdfExportInput` 增字段（连带 batch/paper-edit/draft 四处构造点），**超出本批 hunk 允许范围**，在 §3.3 给出分层计划。**任何判据变更必须换哨兵代际名**（这是清空机制的唯一触发条件）。
7. **失败码复用 `GENERATED_PDF_INVALID`，不新增枚举值。** 这是 `PdfAigcErrorCode.kt:8-11` 已写死的口径（"导出层映射到既有 `GENERATED_PDF_INVALID`，不新增用户可见枚举"），且新增枚举会打断 `MistakePdfExportCopy.kt` 三个 `when` 的穷尽性（`:74-82`、`:108-116`、`:133-141`）——那是第三个禁改文件。
8. **打印部分页确实丢元数据**：整份走 `copyWhole`（`MistakePdfDelivery.kt:129-140`）字节原样，带 `/AIGC`；部分页 `writeSubset`（`:215-270`）用 `PdfRenderer` 逐页重绘进新 `PdfDocument`（`:227`）再 `writeTo`（`:259`），新文档只有 Skia 的 `/Info`（`Producer`），没有 `/AIGC`。处置 = **缓冲子集 → 按 prepared 的 `/AIGC` 重注入 → 一次写出**；重注入失败走既有 `onWriteFailed`（`:158-164`）。该改动落在 `MistakePdfDelivery.kt`，**不在本批允许的 hunk 内**，本项只给方案与判据（§6）。

---

## 1. 事实基线（本会话实读）

| # | 事实 | 位置 / 证据 |
|---|---|---|
| F1 | `prepare` 顺序：锁 → 清 pending → 清 TTL → **查缓存** → 建 pending → render → 大小判限 → `validatedPageCount` → `writeDigest` → `publish` → finally 删 pending | `MistakePdfExporter.kt:114-144`（缓存 `:120`；render `:128`；判限 `:129-133`；页数 `:134`；摘要 `:135`；发布 `:136`；finally `:140-142`） |
| F2 | 缓存目录名 = `input.inputSha256`；`findExisting` 命中即返回（不重渲染、不校验标记） | `:382-385`（`targetDirectoryFor`）、`:295-318`（`findExisting`）、`:120-123` |
| F3 | 发布是 `temporaryDirectory.renameTo(targetDirectory)`，发布前后都只用 `findExisting` 复核 | `:358-380`（`publish`），`renameTo` 在 `:376` |
| F4 | 摘要三处一致：`writeDigest(sha256File(pdf))`、`publish` 返回 `findExisting` 的实测摘要、`verifyIntegrity` 要求"记录摘要 == `sha256` == 实测摘要" | `:135`、`:312-317`、`:64-72` |
| F5 | `reopenVerified` 只用 token 的三个字段，**不重算** `inputSha256` | `:151-163`；目录拼接 `:322`；摘要比对 `:335` |
| F6 | 记录 → token 的真实链路：DB 三列 → `preparedPdfValueOrNull()` → `fromPersistedValue` → `reopenVerified` | `MistakeExportRecord.kt:83-89`；`MistakeExportHubRoute.kt:117-118`、`:121-127`、`:146-165`、`:270-281`、`:312-319` |
| F7 | 唯一生产调用点：`renderPdf = mistakePdfExporter::prepare`（应用装配，worker 侧执行） | `SmartMistakeBookApplication.kt:426`、`:436` |
| F8 | 单题指纹 `"mistake-export-v3"`，覆盖 identity/document 指纹/版式/备注/题面图 URI+sha+role/解析图 role+URI+sha | `MistakePdfEligibility.kt:185-209` |
| F9 | 批量指纹 `"mistake-batch-v3"`，折叠"每题 `inputSha256`"与解析图身份 | `MistakePdfBatchEligibility.kt:94-104` |
| F10 | 卷面编辑指纹 `"mistake-paper-edit-v1"`；卷面块把 `questionImageRole` 带进 `MistakePdfBlock.Image.role` | `MistakePdfPaperEdit.kt:443-449`、`:415-427` |
| F11 | 单题导出**不产生** `MistakePdfBlock.Image`（只有 Paragraph/Formula/ChoiceGroup/Figure） | `MistakePdfEligibility.kt:119-150`（`ContentBlock.Figure → MistakePdfBlock.Figure`，无 Image 分支） |
| F12 | 渲染器实际画的图集合 = `cleanImageLocalUri` + （`includeSolution` 时）`solutionImages` + `blocks.Image`（**排除** role=TUTOR_EXPLANATION） | `DeterministicMistakePdfRenderer.kt:36-46` |
| F13 | AI 图判据：`role ∈ {null, CLEAN_IMAGE, TUTOR_EXPLANATION}` → true；**`null` 也返回 true** | `AiGeneratedImageCaption.kt:30-37` |
| F14 | 文档级判据：`shouldLabel(hasAiImage, hasModelParsedText) = 或`；`hasModelParsedText` 只看 provenance 名 | `PdfAigcContentPolicy.kt:31-38` |
| F15 | `MistakePdfExportInput` 无 provenance/模型文本字段；组件：identity、blocks、layout、三张图的身份字段 | `MistakePdfEligibility.kt:318-355` |
| F16 | 注入器：append-only；同值二次注入返回**原字节**（字节幂等）；输出超限抛 `OUTPUT_TOO_LARGE` | `PdfAigcInjector.kt:24-88`、`:221-223`、`:81-86` |
| F17 | 注入极限：`maxInputBytes=maxOutputBytes=24MiB`；`validateHeader` 用 `>` 判输入 | `PdfCrossReference.kt:7-12`、`:56-59` |
| F18 | 注入失败码文档明文规定"导出层映射到既有 `GENERATED_PDF_INVALID`，不新增用户可见枚举" | `PdfAigcErrorCode.kt:8-11` |
| F19 | `core:export` 只依赖 `core:model` + `core:domain`，**不依赖 `core:data`** | `core/export/build.gradle.kts:22-31` |
| F20 | 7 个 pdf 源文件无 Android import、无 `core:data` 内部依赖；**生产调用方为零**（只有自身测试 + app 的 androidTest 设备用例 import） | `core/data/.../pdf/*.kt` 逐个 import 实读；`git grep -l "core\.data\.pdf"` 命中 15 文件，除 `app/src/androidTest/.../PdfAigcDeviceRoundTripInstrumentedTest.kt` 外全在包内 |
| F21 | `core:data` 不依赖 `core:export`（加依赖不成环） | `core/data/build.gradle.kts:169-171` |
| F22 | 枚举有 5 个值，`MistakePdfExportCopy.kt` 用**无 else 的穷尽 when** 映射三次（单题/批量/编辑），共 6 个分支 | `:74-82`、`:108-116`、`:133-141`；测试用 `entries` 遍历兜底 `MistakePdfExportCopyTest.kt:33-44` |
| F23 | 打印整份 = 复制原文件并比对摘要；部分页 = 逐页重绘进新 `PdfDocument` | `MistakePdfDelivery.kt:129-140`、`:208-212`、`:215-270`（`writeTo` `:259`） |
| F24 | 打印设备夹具用 `MistakeSourceSet.Missing`（`:209`）、`multiPageFixture()`（`:251` 起）里只有文字与程序化网格图，**无 cleanImage/解析图**，所以它的 prepared 本来就不该带 `/AIGC` | `MistakePdfDeliveryRegressionInstrumentedTest.kt:200-257` |
| F25 | Android `PdfDocument` 公开 API 只有 `startPage/finishPage/writeTo/getPages/close`；`PageInfo` 只有宽/高/内容区/页码——**没有任何 metadata/Info 入口** | AOSP `graphics/java/android/graphics/pdf/PdfDocument.java`（main 分支，本会话 `curl …?format=TEXT | base64 -d | grep`：`:115` `startPage`、`:139` `finishPage`、`:169` `writeTo`、`:183` `getPages`、`:197` `close`；`PageInfo` `:267-360`） |
| F26 | 但 Skia 写出的 PDF **必有 `/Info`**（`Producer="Skia/PDF mXX"`），故注入走"合并既有 Info"分支而不是新建 | R1-40 §2.11（对 `google/skia` `src/pdf/SkPDFDocument.cpp:104/153/155/161/163` 的逐行核对） |
| F27 | 旧卷面草稿不会因指纹升版被拒：`toModelOrNull` 只校验 `FORMAT_VERSION`、`sourceInputSha256` 自身一致性；非编辑草的 `inputSha256 == sourceInputSha256` 存的是同一份旧值 | `MistakePaperDraftStore.kt:145-160`、`:227` |

> **口径声明**：F25 是本会话唯一的外部一手执行（`curl` + `base64 -d` + `grep`，输出为方法签名行）。其余为仓库源码实读。**本会话没有编译、没有运行任何 Kotlin 测试或设备用例。**

---

## 2. 注入位置比较：render → digest → publish

### 2.1 候选点（按 `prepare` 的字节生命周期编号）

| 点 | 位置（当前行） | 注入发生在 | 判据/平台验证覆盖面 | 失败形状 | 结论 |
|---|---|---|---|---|---|
| P0 | 渲染器内部（`DeterministicMistakePdfRenderer.kt:62-157`） | `PdfDocument` → `writeTo` 期间 | — | **物理不可能**：`PdfDocument` 无 metadata API（F25）；`PageInfo` 只有几何。除非改渲染后端 | 拒绝 |
| P1 | render 后、大小判限前（`:128` 与 `:129` 之间） | 未判限的字节 | 判限作用在注入后字节上 | 对**超大**产物先做一次 O(n) 全量解析/复制（注入器读入整份 `ByteArray`，`PdfAigcInjector.kt:24-29`），本来第 129 行会立刻拒掉 | 拒绝（白付内存与时间） |
| **P2** | **大小判限后、`validatedPageCount` 前（`:133` 与 `:134` 之间）** | **已判限、未定页数、未写摘要** | ① 注入后由 `PdfRenderer` 真实打开并数页（`:165-181`）；② 摘要对注入后字节（`:135`）；③ 发布原子 | 注入器任何 typed error → `GENERATED_PDF_INVALID`，pending 目录被 finally 删（`:140-142`），发布目录不出现 | **采纳** |
| P3 | `validatedPageCount` 后、`writeDigest` 前（`:134` 与 `:135` 之间） | 已验页数 | 摘要仍对注入后字节成立 | **丢掉唯一的"平台接受增量段"检查**：`PdfRenderer` 验的是未注入字节。PDFium 对追加段的行为是 KD-75 U3 的未知项（R9 §10.2） | 拒绝（省不下任何东西，只少一道门） |
| P4 | `writeDigest` 后、`publish` 前（`:135` 与 `:136` 之间） | 已写摘要 | 无 | **必然失败**：`publish` 第一步 `findExisting`（`:363`）读回记录摘要与实测不符 → `integrityConflict()`（`:309-311`）；即使补写摘要，也是"摘要与字节两次落盘"的中间态，崩溃即半个不一致对 | 拒绝 |
| P5 | `publish` 之后原地改写已发布文件 | 已发布 | 无 | **最坏**：崩溃/中断留下"PDF 已换、摘要未换"的目录；此后每次 `prepare` 先 `findExisting`（`:120`）→ 抛 `EXISTING_FILE_INTEGRITY_CONFLICT`；`cleanupPublishedArtifacts`（`:199-204`）只在 `lastAccessedAt` 超 24h 后才删 → **该题最长 24h 无法导出**，且 `reopenVerified` 因 token 的 `pdfSha256` 不匹配而返回 null（`:335`）。另外 `Sphere` 之外的读者（UI 预览）可能读到半写文件 | 拒绝 |

### 2.2 为什么"必须在 `writeDigest` 之前"是硬约束

摘要不是装饰，它同时是三处的身份（F4）：磁盘上的 `mistake.pdf.sha256`、`PreparedMistakePdf.sha256`（进 token、进 DB 三列）、`verifyIntegrity()` 的合取项。任何"先算摘要、后改字节"的顺序都会让这三者分叉，而分叉的后果不是"检测不到"，而是**检测到后把键锁死**（P4/P5）。

### 2.3 为什么"必须在 `validatedPageCount` 之前"是硬需求

平台接受性是这个链上唯一不需要额外成本的真验证：`validatedPageCount`（`:165-181`）已经用 `PdfRenderer` 打开文件并读 `pageCount`。把它放在注入之后，等于**免费**获得"Android PDFium 接受这段增量更新"的证据——这正是 R9 列为未验证项的那一条。P3 把这份证据浪费掉，之后要单独再开一次 `PdfRenderer`（多一次 8–24MiB 文件打开），或者干脆不验。

### 2.4 注入点与锁

`prepare` 全程在 `ExportRootLock.withLock`（`:116`）内，注入在锁内完成 ⇒ 与清理、`findExisting`、`reopenVerified` 互斥；`PdfAigcInjector` 无共享可变状态（`PdfAigcInjector.kt:17`）⇒ 单例化也无害。

---

## 3. 谁该打标：判据在 `MistakePdfExporter` 内能算到哪一步

### 3.1 (a) AI 图：可算，但必须"镜像渲染器实际画的图集合"

直接写 `input.blocks.any { it is Image }` 或只看 `cleanImageLocalUri` 都会与渲染器不一致。按 F12 的三个来源逐一对齐：

```kotlin
// 与 DeterministicMistakePdfRenderer.kt:36-46 的取图顺序一一对应
private fun MistakePdfExportInput.paintsAiGeneratedImage(): Boolean {
    val question = cleanImageLocalUri != null && isAiGeneratedExportImage(questionImageRole)
    val solution = layout.includeSolution &&
        solutionImages.any { isAiGeneratedExportImage(it.role) }
    val blockFigures = blocks.filterIsInstance<MistakePdfBlock.Image>()
        .filterNot { it.role == VisualRole.TUTOR_EXPLANATION.name }   // 渲染器同款排除
        .any { isAiGeneratedExportImage(it.role) }
    return question || solution || blockFigures
}
```

两个必须写进 KDoc 的陷阱：

- **`isAiGeneratedExportImage(null) == true`**（F13）：所以 `cleanImageLocalUri != null` 必须与它**与**起来，否则"任何没有图的导出"都会被打标——那正是《办法》第十条禁止的伪造标识。
- **role=TUTOR_EXPLANATION 的块**：渲染器在题目区排除它（`:44`），而它的图走 `solutionImages`（`:39-41`）。若判据不过滤 TUTOR，则"卷面里有一张从未被画出的讲解图块"会被误标。当前生产路径不产生这种块（F11、F10），但草稿 DTO 会原样往返块（`MistakePaperDraftStore.kt:263/292`），所以按渲染器口径写死过滤器，而不是依赖"现在不会有"。

### 3.2 打标判据的输入是否都在缓存键里

逐字段核对（这决定"同键必同判据"，也就决定 §4 里"要不要把布尔拼进 hash"）：

| 判据输入 | 进 `inputSha256` 吗 | 位置 |
|---|---|---|
| `cleanImageLocalUri` / `questionImageRole` | 是 | `MistakePdfEligibility.kt:196,198` |
| `solutionImages` 的 role/URI/sha/数量 | 是（含 `layout.includeSolution` 的 `canonicalForm`） | `:194,199-208` |
| 单题 `blocks.Image.role` | 不直接进，但单题根本不产生 Image 块（F11） | `:119-150` |
| 批量 `blocks.Image.role` | 经由"每题 `inputSha256`"间接进（批量块的 role 来自该题的 `questionImageRole`） | `MistakePdfBatchEligibility.kt:94,103`；`MistakePdfPaperEdit.kt:425` |
| 卷面编辑的块 role | 直接进 `paperContentFingerprint` | `MistakePdfPaperEdit.kt:461-486`（`:469` 带 role，`:481-483` 逐块 `contentFingerprint`） |

结论：**同一 `inputSha256` 在同一代际代码下判据恒同**，不需要把布尔值拼进 hash；需要变的是"代际"（§4）。

### 3.3 (b) 模型解析文本：本批不可达（硬事实）

`MistakePdfExportInput`（F15）只有 `questionDocumentSha256`——哈希不可逆；`MistakePdfBlock`（`:372-425`）没有 provenance 字段。要判 (b) 必须由资格层从 `document.blockEvidence`（`CapturedQuestionDocument.kt:176-185`、`:205-213`）派生一个布尔并随输入传递，即改：

1. `MistakePdfEligibility.kt`（构造点 `:161`、`exportFingerprint` 前派生）；
2. `MistakePdfBatchEligibility.kt:105`（`inputs.any(...)`）；
3. `MistakePdfPaperEdit.kt`（从 `sourceInput` 继承）；
4. `MistakePaperDraftStore.kt`（DTO 双向 + `FORMAT_VERSION` 1→2，`MistakePaperDraftStore.kt:227`）。

四处全在**本批禁改**范围。因此本批的 `labelPdfInPlace` 应以 `PdfAigcContentPolicy.shouldLabel(paintsAiGeneratedImage(), hasModelParsedText = false)` 的形式调用**同一个判据函数**，把缺口显式留在实参上（而不是另写一个 `if`），并在 KDoc 里写清"false = 本代输入无 provenance；补 (b) 时换代际名"。

**注意**：本批只做 (a) 有一个副作用是好的——旧卷面草稿不需要失效（F27），因为判据从块 role 现算；(b) 落地时才必须动草稿版本。

---

## 4. 缓存键与旧包回退

### 4.1 键 = `inputSha256` = 目录名；命中即返回

`findExisting`（`:295-318`）在校验"形状、长度、记录摘要 == 实测摘要"后直接返回，**不会**检查 `/AIGC`。旧代码产出的无标记包只要摘要自洽，就永远命中。

### 4.2 v3→v4 能做什么、不能做什么

- 能做：新输入的键整体换值 ⇒ `findExisting` 再也命中不到 v3 目录。
- **不能做**：`reopenVerified`（F5）与 token 的关系。旧记录的 `input_sha256` 是 v3 值，`findExact` 用它拼路径（`:322`），目录仍在 ⇒ 摘要匹配 ⇒ 返回旧的无标记 PDF ⇒ 「导出成果」里能保存/分享/打印（F6 四条调用路径）。窗口 = 缓存 TTL 24h（`MistakePdfExportLimits.CACHE_TTL_MILLIS`）或容量淘汰之前。

**这是批次 41 接缝草案 §3.2 的推理缺口**（它写"旧目录不可达；旧 token 的 `reopenVerified` 自然失败"）——旧 token 恰恰不依赖新指纹，所以"自然失败"不成立。

### 4.3 推荐机制：策略代际哨兵 + 一次性清空（单文件）

在 `MistakePdfExporter.kt` 内加：

```kotlin
// companion（现 :183-186，只有 EXPORT_DIRECTORY）
private const val AIGC_POLICY_SENTINEL = ".aigc-policy-gen-1"
```

`PreparedPdfStore` 加一个方法（放在 `cleanupPublishedArtifacts` `:198-225` 之后）：

```kotlin
/** 策略代际变更的一次性清空：只删"形状完整的已发布对"，与 TTL 清理同一套守卫。 */
fun ensurePolicyGeneration(sentinelName: String) {
    val sentinel = File(directory, sentinelName)
    if (sentinel.exists()) return
    publishedArtifacts().forEach(::deletePublishedArtifact)   // :256-277 / :279-293
    if (!sentinel.createNewFile() && !sentinel.exists()) publishFailed()
}
```

调用点两个：`prepare` 的 `:117`（`cleanupTemporaryDirectories()` 之后、`findExisting` 之前）；`reopenVerified` 的 `:155`（`withLock` 内、`findExact` 之前）。两处都已在锁内。

逐条性质：

| 性质 | 依据 |
|---|---|
| 幂等、崩溃安全 | 哨兵**最后**创建：中途崩溃 → 下次重跑；已删的不会复活 |
| 不削弱既有 fail-closed | 只删 `publishedArtifacts()` 返回的对象（形状 = 两个文件且名字精确，`:262-268`）；半成品目录（1 个文件）不被删 ⇒ `incompleteExistingTargetFailsClosedInsteadOfBeingOverwritten`（`MistakePdfExporterInstrumentedTest.kt:198-218`）语义不变 |
| 不影响清理语义 | `cleanupTemporaryDirectories`（`:191-196`）只看 `.pending-`；`publishedArtifacts` 只收 64-hex 目录名 ⇒ 哨兵文件不被当产物 |
| 不影响分享/预览 | `verifyIntegrity` 只看产物目录内部（`:64-72`）；`MistakePdfShareUris.uriFor` 要求 `parentFile == allowedDirectory`（`:417-423`），产物仍在根下一层 |
| 不让既有 TTL 测试变空转 | 哨兵由**第一次** `prepare` 创建；`preparingAnotherExportRemovesPublishedArtifactsOlderThanOneDay`（`:135-150`）的第二次 `prepare` 因此跳过清空，仍由 TTL 逻辑删掉过期目录 |
| 覆盖旧 token 路径 | `reopenVerified` 清空后 `findExact` 找不到目录 → null → `MistakeExportHubRoute` 的既有文案"文件已清理，无法保存/分享。请重新导出这一份。"（`:162`、`:305`）——**fail-closed 且对用户诚实** |
| 预期行为，别被吓到 | 升级后**第一次打开「导出成果」页**（`artifactAvailable` → `reopenVerified`，`MistakeExportHubRoute.kt:121-127`）就会触发清空：所有升级前的记录当场变成"文件已清理"。这正是要的结果（宁可让用户重导，也不交付无标记包），不是 bug |
| 消灭的具体失败 | "升级后 24h 内，升级前落库的导出记录仍能以无 `/AIGC` 的字节交付给用户" |
| 代价 | 升级后第一次导出/打开成果页要删 ≤24 个目录（≤64MiB，旧上限 `:32-33`），随后该次导出重渲染 |

### 4.4 被拒绝的替代方案

| 方案 | 拒绝理由 |
|---|---|
| 只做 v3→v4（KD-75 原文 / 接缝草案 §3.2） | 不关闭 §4.2 的 token 路径；且要动两个禁改文件。**作为唯一手段不成立**；作为清空的补充见 §4.5 |
| 产物加第三个文件（`label.json` 之类的 marker），旧目录因"不是三件套"而 `findExact` 返回 null | 会改 `PREPARED_PAIR_NAMES` 的 8 处使用（`:70,76,263,285,302,326,366,411`）；且旧的两文件目录被 `publishedArtifacts` 过滤掉 ⇒ **永不进入清理**，磁盘残留无界（旧上限 64MiB 之外还活着）。收益与哨兵清空相同，副作用更多 |
| 目录加代际子目录（`exports/<gen>/<hash>`） | `MistakePdfShareUris.uriFor:417-423` 要求"父目录 == 根"，同时 `publishedArtifacts` 的 `listFiles` 只扫一层（`:257-260`）⇒ 分享与清理同时坏，要再改一个文件 |
| 运行时开关（`BuildConfig`/DataStore）控制是否注入 | 判据变成不可从输入推导的隐藏状态 ⇒ 同键可产出不同字节，摘要/缓存/复算全乱。拒绝 |

### 4.5 保留的残余风险（登记，不本批修）

- **跨渠道/降级构建**：若同一 appId 上先装了"只做 (a)"的构建、再装"做 (a)+(b)"的构建，且哨兵名没换代际，则升级后第一次打开时哨兵已存在 ⇒ 不触发清空 ⇒ 上一构建产出的、对 (b) 文档无标记的产物仍可在 TTL 内命中。**规则**：任何判据变更必须同时改 `AIGC_POLICY_SENTINEL` 的代际号；改资格层的那一批顺手把 `mistake-export-v3`/`mistake-batch-v3` 前缀一起升（那时这两个文件本来就要改），让键也带代际。本批不做前缀升，是因为它在清空之后对"新产物"是惰性的（§0-5）。
- **v3 目录的磁盘残留**：清空后旧目录被删；若清空在崩溃前只完成一半，剩余目录由既有 TTL/容量清理回收（`:198-225`），不存在"永久不可删"的分支。

---

## 5. 失败 fail-closed

### 5.1 异常映射：复用 `GENERATED_PDF_INVALID`

```kotlin
val labeled = try {
    PdfAigcInjector().inject(file.readBytes(), PdfAigcMetadata.document(input.inputSha256))
} catch (failure: PdfAigcInjectionException) {
    throw MistakePdfExportException(MistakePdfExportFailure.GENERATED_PDF_INVALID, failure)
}
```

- 依据 F18：错误码是诊断面，不是 UI 面；导出层不新增用户可见枚举。
- 若新增枚举值：`MistakePdfExportCopy.kt` 的三个穷尽 `when`（F22）编译失败 ⇒ 必须改该文件 ⇒ 超出 hunk 允许范围。**且** `MistakePdfExportCopyTest.kt:35-43` 用 `entries` 遍历，新增值会被自动要求非空文案（测试不会红，但会掩盖"忘了给文案"的编译错误路径）。
- 语义上也不缺：本项目的 PDF 来自 `PdfDocument`（Skia），**从不加密/线性化/带对象流 Info**（R1-40 §2.11），所以注入失败等价于"我们生成的字节形态异常"，正是 `GENERATED_PDF_INVALID` 的含义。

### 5.2 fail-closed 的结构保证

| 失败 | 结果 | 依据 |
|---|---|---|
| 注入器抛任何 `PdfAigcInjectionException` | `prepare` 抛出；`finally` 删 pending（`:140-142`）；**无发布目录、无摘要文件、无半成品** | `PdfAigcInjector.kt:15`（不返回半成品）、`:24-88` |
| 注入后超过 `input.maxPdfBytes` | `PDF_SIZE_LIMIT_EXCEEDED`（复用现有码），不发布 | 需在注入后补一次判限（§7 改动点 4） |
| 写回失败（磁盘满） | `FILE_PUBLISH_FAILED`/IO 逃逸到 worker 的 `catch (Exception)` → 失败文案（`MistakeExportJobRunner.kt:59-68`） | 既有 |
| 进程被杀 | pending 目录下次 `cleanupTemporaryDirectories` 删（`:117`、`:191-196`） | 既有 |
| **`OutOfMemoryError`** | **不被任何一层捕获**：`prepare` 无 catch；`MistakeExportJobRunner` 只 `catch (Exception)`（`:48`、`:59`）⇒ Error 冒泡，worker 线程崩溃 | 本项新发现，需守卫（§5.3） |

### 5.3 内存峰值：注入器是 O(n) 且多次复制

`inject` 的分配序列（`PdfAigcInjector.kt`）：`sourceWithEol`（`:55`，+1 字节但整份复制）→ `base = sourceWithEol + newInfoBytes`（`:57`）→ `ByteArrayOutputStream(base.size + 256)` 再 `write(base)`（`:264-265` 或 `:327-328`）→ `toByteArray()`（`:272`/`:335`）。峰值约 **4–5 倍**文件长度：单题上限 8MiB → ~40MiB；批量上限 24MiB → **~120MiB**。

处置（本批最小）：在 `labelPdfInPlace` 开头加尺寸守卫，超过 `MAX_LABELED_PDF_BYTES`（建议 12MiB）直接 `throw MistakePdfExportException(PDF_SIZE_LIMIT_EXCEEDED)`；**不去捕获 `Error`**（捕获 OOM 只会把 "内存已破" 变成更难诊断的后续失败），而是用守卫把可预见的峰值挡在分配之前。**代价**：12–24MiB 的批量产物会从"能导出但可能 OOM"变成"明确失败"；按当前渲染规模（`MAX_BATCH_PAGES=120`、文本页 5–20KiB）不可达，若实测出现，正解是改注入器为流式写出（append-only 天然可流式：源字节只需顺序复制），**不是抬高这个上限**。该改动属 `core:data` 的 pdf 包，本批不动。

> 24MiB 边界的另一个已知退化：`validateHeader` 用 `>` 判输入（F17），恰好 24MiB 的渲染产物能进注入器，但追加后必超 `maxOutputBytes` ⇒ `OUTPUT_TOO_LARGE` ⇒ 该题无法导出。按当前渲染规模（120 页 / 单页 5–20KiB 文本）不可达；同上被 12MiB 守卫先挡住。

---

## 6. 打印部分页 `writeSubset` 的元数据丢失

### 6.1 事实

| 路径 | 字节来源 | 带 `/AIGC` 吗 |
|---|---|---|
| 整份（`requested.size == prepared.pageCount`，`:129`） | `copyWhole` 流式复制 prepared 文件（`:208-212`），并比对摘要（`:132-136`） | **带**（原样） |
| 部分页（`:141-156`） | `writeSubset`：`PdfRenderer` 逐页渲染到位图（`:231-244`）→ 新 `PdfDocument` 画位图（`:245-251`）→ `document.writeTo`（`:259`） | **不带**：新文档只有 Skia 的 `/Info`（Producer） |

后果：页面上**像素级**的显式图注"本图由 AI 生成"（`AI_GENERATED_IMAGE_CAPTION`，由渲染器画进内容流）会随重绘保留（位图里有），但**文档级隐式标识丢失**。按《办法》口径，这只影响"元数据隐式标识"，比"完全无标识"轻，但仍是同一失败形状：AI 内容离开 App 时缺少机器可读标识。

### 6.2 处置（推荐：缓冲后重注入）

```kotlin
// writeSubset 内，替换 :257-262
if (!cancellationSignal.isCanceled) {
    val subset = java.io.ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
    val labeled = relabelSubset(prepared, subset)   // 失败即抛，不写
    FileOutputStream(destination.fileDescriptor).use { output ->
        output.write(labeled); output.flush()
    }
}

private fun relabelSubset(prepared: PreparedMistakePdf, subset: ByteArray): ByteArray {
    val aigc = PdfAigcReader.readLatestAigc(prepared.file.readBytes()) ?: return subset
    return try {
        PdfAigcInjector().inject(subset, PdfAigcMetadata.parse(aigc.json))
    } catch (failure: PdfAigcInjectionException) {
        throw MistakePdfExportException(MistakePdfExportFailure.GENERATED_PDF_INVALID, failure)
    }
}
```

要点与代价：

- **判据来源只能是 prepared 自身的 `/AIGC`**：打印适配器只拿得到 `PreparedMistakePdf`（`MistakePdfDelivery.kt:81-86` 的构造字段），拿不到 `MistakePdfExportInput`。所以"按同一份 JSON 重注入"是唯一不猜的做法；`PdfAigcReader` 也是自写解析器（`PdfAigcReader.kt:10-11` 自述），只能证明自洽——设备侧仍须独立读者复核。
- **无标记文档照旧透传**：`readLatestAigc` 返回 null 时返回原字节，纯用户内容的部分页打印不会被误标。
- **必须一次写出**：先缓冲再写，注入失败时 `destination` 一个字节都不写（既有 catch `:158-164` → `onWriteFailed`）。若沿用"先 `writeTo(destination)` 再改"的写法，失败时 spooler 里已有无标记/半截文件——这正是 KD-79 记录过的 fail-open 形状（`docs/research/2026-10-29-pdf-write-safety.md:153-164`）。
- **内存**：`prepared.file.readBytes()`（≤24MiB）+ `subset`（选中页位图 PDF，通常远小于整份）+ 注入副本。打印在 `executor`（`:120`）上，非主线程；批量 24MiB 场景仍建议复用 §5.3 的守卫（本路径按同样的 `MAX_LABELED_PDF_BYTES` 先判）。
- **落点**：`MistakePdfDelivery.kt`（禁改文件）。本项只出方案；改动时机应与 §7 的接缝一起，否则会出现"prepare 打标、打印丢标"的半接线。

### 6.3 为什么不在交付层"整份也重注入"

整份路径现在逐字节复制并比对摘要（`MistakePdfDeliveryRegressionInstrumentedTest.kt:96-101`）。重注入会改变字节 ⇒ 现有"打印字节 == prepared 字节"的硬断言失效，而它守护的是"打印不悄悄换一份内容"。**不采纳**。

---

## 7. `MistakePdfExporter` 的具体改动点

### 7.1 先说阻断：只改 `MistakePdfExporter.kt` 编译不过

`MistakePdfExporter.kt` 目前 import 的是 `android.*` 与 `java.*`（`:3-14`）。要调 `PdfAigcInjector`，必须让 `core:export` 的类路径上有 `com.tingyun.smartmistakebook.core.data.pdf.*`。两条路（F19–F21）：

| 落位方案 | 改动面 | 优点 | 代价 |
|---|---|---|---|
| **B1 移包**（批次 41 草案 §2.1）：`core/data/.../pdf/` 7 文件 → `core/export/.../export/pdf/`，包名同步 | **8 个文件移动 + 6 个测试 + 1 夹具 + app 设备用例的 6 条 import** | 分层正确（导出物打标归导出层）；该包零生产调用（F20），移动是机械的；无 Gradle 模块变更 | 超出本批"只改一个文件"的允许范围；一次大 diff |
| **B2 加依赖**：`core/export/build.gradle.kts:22` 增 `implementation(project(":core:data"))` | **1 行** | 最小可行 | 把 Room/ML/OkHttp 拉进导出模块编译类路径；`core:data` 的任何变更都扩大 `core:export` 的构建面；依赖方向与"导出只依赖 model/domain"相反（无环，F21） |

**本项建议**：本批先定"注入点 + 判据 + 清空"，落位选 **B1**，但把"移动"作为独立小提交（纯 rename，可逐文件核对）；若本批必须一次落完且只允许 +1 文件，则用 B2 并在 `core/export/build.gradle.kts` 上写清"临时，待 B1"。**无论哪条，都必须由主会话把 hunk 允许范围从"1 个文件"放宽到"这个文件 + 落位所需的 1 处"**——否则 item 13 无法编译，不能声称完成。

### 7.2 改动点清单（行号按当前树 `MistakePdfExporter.kt`，475 行）

| # | 方法 / 位置 | 改动 | 消灭的具体失败 | 可失败判据 |
|---|---|---|---|---|
| 1 | 文件头 `:1-14` import | `+ PdfAigcInjector / PdfAigcInjectionException / PdfAigcMetadata / PdfAigcContentPolicy`（B1 下是 `core.export.pdf.*`）；如需镜像渲染器过滤再加 `+ core.model.VisualRole` | —（编译前提） | 门禁阶段 `:core:export:compileLocalFirstDebugKotlin` exit=0 |
| 2 | companion `:183-186` | `+ private const val AIGC_POLICY_SENTINEL = ".aigc-policy-gen-1"`；`+ private const val MAX_LABELED_PDF_BYTES = 12L * 1024 * 1024` | —（两个机制的参数唯一来源） | 无独立判据（被 4/5 覆盖） |
| 3 | `prepare` `:117` 之后 | `store.ensurePolicyGeneration(AIGC_POLICY_SENTINEL)`（**必须在 `findExisting`（`:120`）之前**） | 升级后同键命中旧无标记包 | 造一个 v3 目录 + 同 key 的输入 → `prepare` 后该目录消失、新产物带 `/AIGC` |
| 4 | `prepare` `:133` 与 `:134` 之间 | `labelPdfInPlace(temporaryPdf, input)` | AI 内容导出无文档级标识（KD-75 主体） | §8 C1–C5 |
| 5 | 新私有方法（放在 `validatedPageCount` `:165` 之前）`labelPdfInPlace(file, input)` | ① `if (!input.paintsAiGeneratedImage()) return`（判据见 §3.1，经 `PdfAigcContentPolicy.shouldLabel(..., false)`）；② `if (file.length() > MAX_LABELED_PDF_BYTES) throw PDF_SIZE_LIMIT_EXCEEDED`；③ `inject(file.readBytes(), PdfAigcMetadata.document(input.inputSha256))`；④ 注入后 `> input.maxPdfBytes` → `PDF_SIZE_LIMIT_EXCEEDED`；⑤ 写回 + `fd.sync()` | 无标记发布；OOM 崩溃；注入后超限；**掉电后"PDF 已写、摘要未写"的截断发布**（`writeDigest` 会 fsync `:351-355`，渲染器也 fsync `DeterministicMistakePdfRenderer.kt:152-157`，注入写回不 fsync 就是这条链上唯一不持久的写） | §8 C6、C11 |
| 6 | `reopenVerified` `:155` 之后（`withLock` 内、`findExact` 之前） | `store.ensurePolicyGeneration(AIGC_POLICY_SENTINEL)` | **旧记录 token 重开旧无标记包**（§4.2；这是 v3→v4 单独做不到的那条） | §8 C7：伪旧产物 + 真 token → `reopenVerified == null` |
| 7 | `PreparedPdfStore`（`cleanupPublishedArtifacts` `:225` 之后） | `fun ensurePolicyGeneration(sentinelName: String)`（§4.3 实现，复用 `publishedArtifacts`/`deletePublishedArtifact`） | 同 #3/#6 | §8 C7 + C8（半成品目录不被删） |

**不做**（每条都能指认"改了反而多一个失败"）：

- 改 `writeDigest`/`publish`/`verifyIntegrity` 的摘要口径 —— 注入在摘要之前，口径不变（§2.2）。
- 在 `findExisting` 命中分支里再读一次 `/AIGC` 校验 —— 同键同判据（§3.2）+ 代际清空已覆盖；多一次整文件解析只增加失败面。
- 新增 `MistakePdfExportFailure` 值 —— §5.1。
- 动 `MistakePdfExportLimits` 的 24h/24 个/64MiB —— 清空是独立机制，不该借 TTL 表达。

### 7.3 `prepare` 改动后的完整时序（含行号）

```
:116 锁
:117 cleanupTemporaryDirectories()
新    store.ensurePolicyGeneration(...)          ← #3
:119 cleanupPublishedArtifacts(now, "")
:120 findExisting → 命中即返回（此时产物必为当代际：清空已跑过）
:125 createTemporaryDirectory()
:128 DeterministicMistakePdfRenderer.render(input, temporaryPdf)
:129 大小判限（未注入）
新    labelPdfInPlace(temporaryPdf, input)       ← #4
:134 validatedPageCount(temporaryPdf)            ← 注入后字节过 PDFium
:135 writeDigest(sha256File(temporaryPdf))       ← 注入后摘要
:136 publish(...)                                ← rename 原子发布
:140 finally deleteTemporaryDirectory
```

---

## 8. 判据（每条都能失败；本会话均未执行，标 UNVERIFIED）

| # | 判据 | 层 | 期望 |
|---|---|---|---|
| C1 | 完整性 | androidTest | `prepared.verifyIntegrity() == true`，且 `readRecordedDigest == sha256 == sha256File`（注入后字节） |
| C2 | 平台接受 | androidTest | 注入后 `PdfRenderer.pageCount` == 同输入未注入基线；第 0 页 ink > 0 |
| C3 | 语义读回（自写读者） | androidTest | `PdfAigcReader.readLatestAigc` → `Label=="1"`、`ContentProducer=="SmartMistakeBook"`、`ProduceID==input.inputSha256`，`readLatestAigcKeyCount==1` |
| C4 | 语义读回（**独立读者**） | host pypdf | 把设备落盘的 prepared 拉回宿主，`/Info` 有 7 键 `/AIGC`，键名与顺序同 `PdfAigcMetadata.KEY_ORDER` |
| C5 | 负向：纯用户内容 | androidTest | 无 AI 图（纯文本/仅 QUESTION_DIAGRAM）→ `readLatestAigc == null`，且 Info 无 `AIGC` 键 |
| C6 | 幂等 | androidTest | 同输入 `prepare` 两次（第二次命中缓存）→ 字节一致、`AIGC` 键数 1、`sha256` 不变 |
| C7 | 代际清空 / 旧 token 拒绝 | androidTest | 手工造"形状完整"的旧产物目录 + 用其链值构造 token → `reopenVerified == null`；再 `prepare` → 目录重建且带 `/AIGC` |
| C8 | 半成品仍 fail-closed | androidTest | 既有 `incompleteExistingTargetFailsClosedInsteadOfBeingOverwritten`（`:198-218`）仍抛 `EXISTING_FILE_INTEGRITY_CONFLICT`，且半成品字节未被删改 |
| C9 | 打印整份 | androidTest | 既有断言继续成立：输出与 prepared 逐字节相同（`MistakePdfDeliveryRegressionInstrumentedTest.kt:96-101`） |
| C10 | 打印部分页 | androidTest | **需新夹具**（现有 `multiPageFixture` 无图，F24）：带 CLEAN_IMAGE 的多页卷 → `writeSubset` 输出页数 == 选中页数，且 `readLatestAigc` 读回同一份 JSON；无标记卷的部分页输出 `readLatestAigc == null` |
| C11 | 显式图注不变 | androidTest | 注入前后同一页 ink/像素抽样一致（前缀逐字节保留 + 图注是内容流文本）；图注文案仍为 `AI_GENERATED_IMAGE_CAPTION` |
| C12 | 失败闭包 | androidTest | 注入器强制抛错（可注入 fake 或截断 Info 的夹具）→ `prepare` 抛 `GENERATED_PDF_INVALID`；`cache/mistake_pdf_exports` 下无新发布目录、无 `.pending-` 残留、旧同键产物未被覆盖 |
| C13 | 守卫 | androidTest（或把守卫抽成纯函数后 JVM） | `MAX_LABELED_PDF_BYTES` 之上的输入 → `PDF_SIZE_LIMIT_EXCEEDED`（**无 OOM**） |

**JVM 可行性必须先说清**：`core:export` 的单元测试目录**没有任何** `MistakePdfExporter` 引用，仓库也**没有 Robolectric**（`grep robolectric core/export/build.gradle.kts gradle/libs.versions.toml` → 无命中）。`PdfDocument`/`PdfRenderer` 都是 `android.graphics.pdf`，在 JVM 单测里不可用 ⇒ **计划 item 17"JVM：导出→注入→PdfRenderer 读回（真实 exporter 路径）"按字面不可达**。可行的分层是：

- 纯 JVM（`core/export/src/test`）：判据来源函数（`paintsAiGeneratedImage`）的 4 类输入矩阵；注入器本身的 JVM 往返（放 `core:data` 或移包后放 `core/export`，用合成字节，已有 6 个测试文件）；
- androidTest（`core/export/src/androidTest`）：C1/C2/C3/C5/C6/C7/C8/C9/C10/C11/C12；
- host pypdf：C4。

---

## 9. 未验证 / 阻塞 / runbook

**UNVERIFIED（本会话）**

- 任何编译：`:core:export`、`:app` 均未跑 Gradle（本批纪律：Gradle 只在门禁阶段串行运行）。
- 任何设备行为：`PdfRenderer` 对注入后字节的接受度（KD-75 U3）仍无本项证据；C1–C12 全部未执行。
- 注入器在**真实 `prepare` 产物**上的行为：R1-40 与批次 41 的设备用例用的是合成/直构 PDF（`PdfAigcDeviceRoundTripInstrumentedTest`），不是 `DeterministicMistakePdfRenderer` 的输出。二者差在 Skia 的 `/Info`（Producer）与无 `/ID`（R1-40 §2.11），理论上走"合并 Info"分支，但**未实测**。
- 内存峰值 4–5 倍的估算来自代码阅读（分配序列在 `PdfAigcInjector.kt`），未用 profiler 测。
- 打印部分页重注入后的字节，未在设备上产出/读回。
- 旧 token 回退窗口（24h）来自 `CACHE_TTL_MILLIS` 常量与 `cleanupPublishedArtifacts` 逻辑，未做真机时间旅行验证。

**BLOCKED（外部/裁定）**

1. **落位方案（B1 vs B2）需主会话裁定**，且需要把本批 hunk 允许范围从"仅 MistakePdfExporter.kt"放宽 1 处（移动或 1 行依赖）。否则 item 13 不可编译。
2. **(b) 判据（模型解析文本）**：需改 4 个禁改文件，本批不可做；不阻塞 (a)。
3. **`writeSubset` 重注入**落在 `MistakePdfDelivery.kt`（禁改）。
4. 独立读者（pypdf）需要设备产物落盘 + 宿主回拉，属设备轨。

**Runbook（门禁阶段照做）**

```bash
# 1) 编译（最小面）
./gradlew :core:export:compileLocalFirstDebugKotlin --rerun-tasks

# 2) JVM：判据矩阵 + 注入器往返（放 core:export 或 core:data，取决于落位）
./gradlew :core:export:testLocalFirstDebugUnitTest --tests '*Aigc*'

# 3) 设备：强制重编 androidTest 后单类执行（禁止 :app:connectedLocalFirstDebugAndroidTest）
./gradlew :core:export:assembleLocalFirstDebugAndroidTest --rerun-tasks
adb install -r -t core/export/build/outputs/apk/androidTest/localFirst/debug/core-export-localFirst-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.export.MistakePdfExporterAigcInstrumentedTest \
  com.tingyun.smartmistakebook.core.export.test/androidx.test.runner.AndroidJUnitRunner

# 4) 独立读者：把 prepared 拉回宿主后
python -I -c "import pypdf,sys; r=pypdf.PdfReader(sys.argv[1]); print(r.metadata)" /path/to/exported.pdf
```

（APK 路径按实际 flavor/output 目录核对；本会话未执行上述任一命令。）

---

## 10. 来源与采纳/拒绝理由

| 来源 | 类型 | 用于 | 采纳/拒绝 |
|---|---|---|---|
| AOSP `graphics/java/android/graphics/pdf/PdfDocument.java`（main）<https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/pdf/PdfDocument.java> | **一手源码**（本会话 `curl …?format=TEXT \| base64 -d \| grep -nE "public \|nativeWriteTo\|Info"`） | F25：无 metadata API；`writeTo` 走 `nativeWriteTo` | **采纳**（唯一的 P0 排除依据） |
| <https://developer.android.com/reference/android/graphics/pdf/PdfDocument> | 官方参考页（WebFetch 超时；改用 searchd `extract_content`，返回被站点导航截断，仅拿到摘要句） | 交叉确认类用途（"create → startPage → writeTo → close"，无 metadata 描述） | **部分采纳**（导航截断，未取到方法表；结论以 AOSP 源码为准） |
| `docs/research/2026-11-02-pdf-incremental-aigc.md` §2.11、§3.7、§5.2 | 仓库内前序研究（含对 `google/skia` 行号的一手核对） | F26：Skia 必写 `/Info`；判据 (a)+(b) 的口径；失败码分类 | **采纳**（结论一致，未复跑其 PoC） |
| `docs/research/evidence/2026-11-03-batch-41/ws2-core-export-seam-patch.md` §2.1、§3.2、§3.4–3.5 | 仓库内草案 | 落位方案 B1/B2、注入位置、`writeSubset` 方案 | **部分采纳**：§2.1/§3.4/§3.5 采纳；**§3.2"旧 token 自然失败"拒绝**（§4.2 证明不成立） |
| `docs/known-defects.md:2894`（KD-75 Fix direction） | 仓库内台账 | "v3→v4、writeSubset 处置、判据 (a)+(b)" | **部分采纳**：v3→v4 本项判为"不足且本批越界"（§0-5、§4.2）；其余采纳 |
| `docs/research/2026-10-29-pdf-write-safety.md:153-164` | 仓库内研究 | `writeSubset` 无缓冲 = fail-open 形状 | 采纳（作为"必须缓冲后一次写出"的依据） |
| TC260《网络安全标准实践指南——人工智能生成合成内容标识方法 文件元数据隐式标识》（2025-08）<https://www.tc260.org.cn/tc260/sjzn/202508/e1d2ed74976341cfafdc709243070d58/files/8d7cd66e0f5b464cb778143d7500d0a5.pdf>；编码规则（2025-03）<https://www.tc260.org.cn/tc260/sjzn/202503/44ac104a3559429eb9260d0826212be7/files/3534b47327704191bea0eceb0f3e0f7f.pdf> | **官方原文（本会话只拿到检索命中，未解析 PDF 正文）** | `/AIGC` 是 Info 条目、7 键模板 | **未一手核对**：本项不重复该结论，沿用 R1-40 §3.1 的一手核对结果；**单一来源链（R1-40）→ 标"待验证"**，本项所有裁定不依赖对模板的新解释，只依赖"文档级元数据要写进 Info"这一点（该点在 R1-40 有官方 PDF 引文与页码） |
| ISO 32000-1 §7.5.6（增量更新；经 R1-40 §2.1 转述） | 标准（二手转述） | "读者以最新修订为准" | 采纳为背景，不作本项裁定依据 |

**零命中/失败的通道**：`WebFetch` → `developer.android.com` 连接超时（10s×5 地址）；改走 searchd `search_text` + `extract_content` 后拿到摘要与 AOSP 链接。TC260 两份官方 PDF 未下载解析（体积与截断风险），按纪律**不当成"不存在"，也不当成"已核对"**。

---

## 11. 与计划项的对应（本项交付的是研究，不是实现）

| 计划 item | 本项给的输入 | 状态 |
|---|---|---|
| 13 注入点 | §2（P2）、§3（判据 (a)）、§7（改动点 1/4/5） | 方案可执行；**受 §7.1 落位阻断** |
| 14 缓存键/指纹升版 | §4（清空 > 升版；升版不足的证明） | 方案改为"代际哨兵 + 清空"，需主会话确认改向 |
| 15 纯用户不注入 | §3.1（两处陷阱）、§8 C5 | 判据已给 |
| 16 fail-closed | §5（复用 `GENERATED_PDF_INVALID`、pending/rename、OOM、尺寸） | 判据已给 |
| 17 JVM 往返 | §8 末（JVM 不可达的证明与替代分层） | **需改口径**：真实 exporter 路径只能 androidTest |
| 18/19 设备读回 | §8 C3/C4/C5 + §9 runbook | 待设备轨 |
| 20 图注逐位不变 | §8 C11（前缀保留 + 像素对照） | 待设备轨 |
| 21 writeSubset 处置 | §6（缓冲重注入，含参数来源与内存界） | 落在禁改文件，需另一批 |
| 22 旧包不回退 | §4.2/§4.3/§8 C7 | 方案已给，落在允许文件内 |
