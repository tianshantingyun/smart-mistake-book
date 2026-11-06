# WS2 item 17：`core/export` 产品接缝 patch 草案（未应用）

| 字段 | 值 |
|---|---|
| evidence-id | WS2-2026-11-03-core-export-seam |
| 批次 | 41（计划 §2 WS2 item 17；输入 = R9 `docs/research/2026-11-03-instrumentation-pdf-verification.md` §7.6） |
| head | `62522930bc8bae51be1523c954f39449e054b6af`（本会话未做任何 git 写） |
| 状态 | **草案；未应用到 `core/export`（热区）。** 未运行 Gradle、未运行设备、未做字节级实跑 |
| verdict | 接缝位置与判据已给出；§2.1 的落位前置需主会话裁定 |

> 本文只出 diff 草案与判据，不改 `core/export`、不改 `core/domain`、不改 `settings.gradle.kts`。
> diff 的行号按当前树（2026-11-02/03），**不是可直接 `git apply` 的补丁**：落位方案（§2）会改变
> import 与文件位置，评审后再生成正式补丁。

---

## 1. 当前事实（本会话实读）

| 事实 | 位置 / 命令 |
|---|---|
| `core/export` 对 `PdfAigc*` **零引用** | `grep -rln PdfAigc core/export` → 无输出（本会话实跑） |
| `core/export` 只依赖 `core:model` / `core:domain`；**不依赖 `core:data`** | `core/export/build.gradle.kts:22-26`（`:23-24` 两个 project 依赖） |
| `core:data` 只依赖 `core:model` / `core:domain` / `core:database`；不依赖 `core:export` | `core/data/build.gradle.kts:169-171`（无环，但见 §2.2 的分层代价） |
| 纯 PDF/AIGC 代码在 `core/data/.../pdf/`，7 个 main 文件**无 Android import** | `grep -rn "^import android" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/pdf/` → none |
| 该包只被自身测试引用，`core:data` 生产代码零调用 | 本会话 `grep -rln PdfAigc core/data/src/main` 只命中 `pdf/` 内文件 |
| `prepare` 顺序：render → 大小 → pageCount → digest → publish | `core/export/.../MistakePdfExporter.kt:114-142`（`render` :128，`validatedPageCount` :134，`writeDigest` :135） |
| `PreparedMistakePdf.verifyIntegrity()` 校验文件字节、记录摘要与 `sha256` 三者一致 | `MistakePdfExporter.kt:50-73` |
| 缓存目录名 = `input.inputSha256` | `MistakePdfExporter.kt:384`；`findExisting` 先查缓存（`:120`） |
| 单题指纹 `"mistake-export-v3"` | `MistakePdfEligibility.kt:185-186` |
| 批量指纹 `"mistake-batch-v3"` | `MistakePdfBatchEligibility.kt:96`（KDoc :78） |
| 卷面编辑指纹 `"mistake-paper-edit-v1"` | `MistakePdfPaperEdit.kt:448` |
| `MistakePdfExportInput` 字段（`inputSha256` 在 :329；无 provenance/模型文本布尔） | `MistakePdfEligibility.kt:318-370` |
| 资格层已有 `document.blockEvidence`（含 `provenance`） | `core/model/.../CapturedQuestionDocument.kt:15-17`、`:176-185`；枚举 `:205-213` |
| 内容判据 `shouldLabel(hasAiGeneratedImage, hasModelParsedText)` / `hasModelParsedText(names)` | `core/data/.../pdf/PdfAigcContentPolicy.kt:31-38` |
| AI 图判据 `isAiGeneratedExportImage(role)`；`null` 视为 `CLEAN_IMAGE` | `core/export/.../AiGeneratedImageCaption.kt:30-38` |
| 打印整份走 `copyWhole`（原文件含 `/AIGC`），部分页走 `writeSubset` **重绘新 PdfDocument** | `MistakePdfDelivery.kt:130-155`（分支）、`:215-266`（重绘）；`:259` `document.writeTo` |
| 草稿 DTO 格式版本 `FORMAT_VERSION = 1`；解码失败返回 null | `MistakePaperDraftStore.kt:101`（decode）、`:106`（catch）、`:146`（版本判定）、`:227`（常量） |

---

## 2. 接缝位置与落位方案（先裁定，再落地）

### 2.1 推荐：把纯 PDF/AIGC 代码移入 `core/export`

把 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/pdf/` 的 7 个文件与
`core/data/src/test/kotlin/.../pdf/` 的 6 个测试 + 1 个夹具（`PdfTestFixtures.kt`）**整体移动**到：

```text
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/pdf/
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/pdf/
```

包名改为 `com.tingyun.smartmistakebook.core.export.pdf`。理由：

- 该包无 Android import、无 `core:data` 内部类型依赖（本会话实读），移动是机械的；
- `core:data` 生产代码零调用，移动不改变任何现有数据链；
- `core:export` 直接拥有“导出物打标”逻辑，避免 `core:export → core:data` 的分层倒置；
- 不新增 Gradle 模块，不需要改共享热区 `settings.gradle.kts`。

代价/必改项：`app/src/androidTest/.../pdf/PdfAigcDeviceRoundTripInstrumentedTest.kt` 的 6 个
`com.tingyun.smartmistakebook.core.data.pdf.*` import 改为
`com.tingyun.smartmistakebook.core.export.pdf.*`（本批已落的设备用例，移动时同步）；JVM 测试的
`package` 行随包名改为 `core.export.pdf`；`PdfTestFixtures` 仍是同包 `internal`，可见性不变。

### 2.2 备选：`core:export` 增加 `implementation(project(":core:data"))`

可行且无环（§1），但：

- 把 Room/Android 数据层拉进导出模块的编译类路径，导出模块的 JVM 单测会间接加载 `core:data` 的 Android 引用（mockable android.jar 能过，但依赖方向与“导出只依赖 model/domain”的现状相反）；
- `core:data` 的变更会扩大 `core:export` 的构建影响面。

**不推荐**，只在 §2.1 被否决时使用。若采用，diff 的 import 保持
`com.tingyun.smartmistakebook.core.data.pdf.*`，其余 hunk 不变；另加一行：

```diff
--- a/core/export/build.gradle.kts
+++ b/core/export/build.gradle.kts
@@ dependencies {
     implementation(project(":core:model"))
     implementation(project(":core:domain"))
+    implementation(project(":core:data"))
```

### 2.3 拒绝的方案

- **复制注入器到 `core/export`**：双源漂移，违反单源；不采纳。
- **进程级可变 registry（`object { var labeller }`）**：测试顺序敏感、漏装时静默不标；不采纳。
- **在 `MistakePdfExportInput` 里放函数/labeller**：持久化 DTO 不能序列化行为；不采纳。

---

## 3. diff 草案（假设 §2.1 已落位）

> 以下 hunk 头用 `@@` 占位（§2.1 的移动会改 import 与行号）；正文里的 `:line` 按当前树标注，
> 正式补丁由评审后重算。缩进/上下文足以表达改法，不声称可直接 `git apply`。

### 3.1 导出输入携带“模型解析文本”布尔（必需，不设默认）

```diff
--- a/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt
+++ b/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt
@@ class MistakePdfExportInput internal constructor(
     val blocks: List<MistakePdfBlock>,
     val questionDocumentSha256: String,
+    /**
+     * 文档块证据里是否出现过 `MODEL_DOCUMENT_PARSE`。
+     *
+     * 由资格层从 `CapturedQuestionDocument.blockEvidence` 派生（不取模型回填）。**不进
+     * `inputSha256`**：`documentFingerprint` 已覆盖整个 `CapturedQuestionDocument`（含
+     * `blockEvidence`），再次拼接是冗余；真正需要失效旧缓存的是 §3.2 的版本前缀。
+     */
+    val hasModelParsedText: Boolean,
     val inputSha256: String,
```

消灭的具体失败：导出层此前只能看到 `blocks`，看不到块级 provenance，因此**含模型解析文本的
已提交文档会漏标**（KD-75 第二处缺口；R1 §4.3、`known-block` KD-75）。

### 3.2 资格层派生 + 缓存版本升版

```diff
--- a/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt
+++ b/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt
@@ import com.tingyun.smartmistakebook.core.model.VisualRole
+import com.tingyun.smartmistakebook.core.export.pdf.PdfAigcContentPolicy
@@
         val document = state.questionDocument
+        val hasModelParsedText = PdfAigcContentPolicy.hasModelParsedText(
+            document.blockEvidence.map { it.provenance.name },
+        )
@@
                 userNote = state.detail.userNote?.let(::safeInlineText)?.takeIf(String::isNotBlank),
                 questionDocumentSha256 = documentFingerprint,
+                hasModelParsedText = hasModelParsedText,
                 inputSha256 = exportFingerprint(
-                    "mistake-export-v3",
+                    "mistake-export-v4",
```

为什么升版本前缀而不是把布尔拼进 hash：含模型文本的文档在旧代码下**已经**有唯一的
`inputSha256`（`documentFingerprint` 含 `blockEvidence`），旧缓存里的问题是“同一指纹下没有
`/AIGC`”。只改判据不升版本会让 `findExisting` 继续命中旧的无标识 PDF
（`MistakePdfExporter.kt:120`、`:384`）。v3→v4 一次性让旧目录不可达（R1 §2.4；KD-75
Fix direction 原文）。

### 3.3 批量 / 卷面编辑 / 草稿持久化

```diff
--- a/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfBatchEligibility.kt
+++ b/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfBatchEligibility.kt
@@
     return MistakePdfExportInput(
@@
         solutionImages = solutionImages,
+        // 批量卷只要有一题含模型解析文本，整份卷面就必须打标（判据不因合卷而丢失）。
+        hasModelParsedText = inputs.any(MistakePdfExportInput::hasModelParsedText),
         layout = layout,
@@
-        "mistake-batch-v3",
+        "mistake-batch-v4",
```

```diff
--- a/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt
+++ b/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt
@@
             solutionImages = if (layout.includeSolution) {
                 aggregateSolutionImages(questions)
             } else {
                 emptyList<MistakePdfExportImage>()
             },
+            // 卷面编辑不产生新的题面 revision，模型解析证据随来源输入原样保留。
+            hasModelParsedText = sourceInput.hasModelParsedText,
             layout = layout,
@@
-    "mistake-paper-edit-v1",
+    "mistake-paper-edit-v2",
```

```diff
--- a/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePaperDraftStore.kt
+++ b/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePaperDraftStore.kt
@@
         questionDocumentSha256 = questionDocumentSha256,
+        hasModelParsedText = hasModelParsedText,
         inputSha256 = inputSha256,
@@
             questionDocumentSha256 = questionDocumentSha256,
+            hasModelParsedText = hasModelParsedText,
             inputSha256 = inputSha256,
@@
 private data class PaperInputDto(
@@
     val questionDocumentSha256: String,
+    /**
+     * 旧草稿没有这个字段；[FORMAT_VERSION] 1→2 会先拒绝旧草稿，因此默认 false 只用于
+     * 解码容错，不会让旧的无标识草稿继续走新导出链。
+     */
+    val hasModelParsedText: Boolean = false,
     val inputSha256: String,
@@
-    const val FORMAT_VERSION = 1
+    const val FORMAT_VERSION = 2
```

草稿失效是**有意的 fail-closed**：`PaperDraftFileDto.toModelOrNull()` 先看
`version != FORMAT_VERSION`（`MistakePaperDraftStore.kt:146`），旧草稿直接返回 null，不会以
“缺少模型文本标记”的旧形态进入导出。

### 3.4 导出器在 render 与 digest 之间注入

```diff
--- a/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt
+++ b/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt
@@ import java.util.UUID
+import com.tingyun.smartmistakebook.core.export.pdf.PdfAigcContentPolicy
+import com.tingyun.smartmistakebook.core.export.pdf.PdfAigcInjectionException
+import com.tingyun.smartmistakebook.core.export.pdf.PdfAigcInjector
+import com.tingyun.smartmistakebook.core.export.pdf.PdfAigcMetadata
@@
                 val temporaryPdf = File(temporaryDirectory, PREPARED_PDF_FILE_NAME)
                 DeterministicMistakePdfRenderer.render(input, temporaryPdf)
                 if (temporaryPdf.length() !in 1..input.maxPdfBytes) {
                     throw MistakePdfExportException(
                         MistakePdfExportFailure.PDF_SIZE_LIMIT_EXCEEDED,
                     )
                 }
+                // 先确认渲染产物非空/未超限，再注入；注入后的再次判限在 labelPdfInPlace 内。
+                labelPdfInPlace(temporaryPdf, input)
                 val pageCount = validatedPageCount(temporaryPdf)
@@
     private fun validatedPageCount(file: File): Int = try {
```

新增私有方法（放在 `validatedPageCount` 之前）：

```kotlin
    /**
     * 在 `prepare` 的 render 与 digest 之间写文档级 `/AIGC`。顺序要求：
     * - 必须在 `validatedPageCount` 之前，让 PDFium 真的接受增量段（KD-75 U3）；
     * - 必须在 `writeDigest` 之前，摘要/`verifyIntegrity()`/发布物绑定的是注入后的字节；
     * - fail-closed：注入器任何 typed error 都映射到既有 `GENERATED_PDF_INVALID`，不发布半成品。
     */
    private fun labelPdfInPlace(file: File, input: MistakePdfExportInput) {
        // 注意：isAiGeneratedExportImage(null) == true（旧调用方按 CLEAN_IMAGE 处理）。
        // 因此只在“确实有图”时才调用它，避免纯文本导出被误标。
        val hasAiGeneratedImage =
            (input.cleanImageLocalUri != null && isAiGeneratedExportImage(input.questionImageRole)) ||
                (input.solutionImages.isNotEmpty() &&
                    input.solutionImages.any { isAiGeneratedExportImage(it.role) }) ||
                input.blocks
                    .filterIsInstance<MistakePdfBlock.Image>()
                    .any { isAiGeneratedExportImage(it.role) }
        if (!PdfAigcContentPolicy.shouldLabel(hasAiGeneratedImage, input.hasModelParsedText)) return

        val labeled = try {
            PdfAigcInjector().inject(
                file.readBytes(),
                // 文档级 ProduceID = 输入指纹（R1 §3.2）；不是注入后字节的 hash，避免自指。
                PdfAigcMetadata.document(input.inputSha256),
            )
        } catch (failure: PdfAigcInjectionException) {
            throw MistakePdfExportException(MistakePdfExportFailure.GENERATED_PDF_INVALID, failure)
        }
        if (labeled.size.toLong() > input.maxPdfBytes) {
            throw MistakePdfExportException(MistakePdfExportFailure.PDF_SIZE_LIMIT_EXCEEDED)
        }
        FileOutputStream(file).use { output -> output.write(labeled) }
    }
```

闭合点：`validatedPageCount`（`:134`）读的是注入后文件；`store.writeDigest`（`:135`）对注入后
字节算 sha；`publish` 的 `sha256`（`:314`）与 `verifyIntegrity()` 因此一致；`reopenVerified`
（`:151-163`）读到的也是注入后缓存。

### 3.5 打印部分页：缓冲后重注入（整份打印已天然保留）

`writeSubset` 当前把选中页重绘进新的 `PdfDocument`（`MistakePdfDelivery.kt:226-266`），新文档
没有 Info `/AIGC`。整份打印走 `copyWhole`（`:130-134`），原文件字节原样复制，**已含** `/AIGC`，
不需要改。

```diff
--- a/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt
+++ b/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt
@@ import java.io.BufferedInputStream
+import java.io.ByteArrayOutputStream
+import com.tingyun.smartmistakebook.core.export.pdf.PdfAigcInjectionException
+import com.tingyun.smartmistakebook.core.export.pdf.PdfAigcInjector
+import com.tingyun.smartmistakebook.core.export.pdf.PdfAigcMetadata
+import com.tingyun.smartmistakebook.core.export.pdf.PdfAigcReader
@@
                     if (!cancellationSignal.isCanceled) {
+                        val subset = ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
+                        val labeled = relabelSubset(prepared, subset)
                         FileOutputStream(destination.fileDescriptor).use { output ->
-                            document.writeTo(output)
+                            output.write(labeled)
                             output.flush()
                         }
                     }
@@
         } finally {
             source.close()
         }
     }
+
+    /** 子集重绘是**新文档**；来源 prepared 有 `/AIGC` 时按同一份 JSON 重新注入，否则保持不标。 */
+    private fun relabelSubset(prepared: PreparedMistakePdf, subset: ByteArray): ByteArray {
+        val aigc = PdfAigcReader.readLatestAigc(prepared.file.readBytes()) ?: return subset
+        return try {
+            PdfAigcInjector().inject(subset, PdfAigcMetadata.parse(aigc.json))
+        } catch (failure: PdfAigcInjectionException) {
+            throw MistakePdfExportException(MistakePdfExportFailure.GENERATED_PDF_INVALID, failure)
+        }
+    }
```

判据：子集打印失败时既有 catch（`MistakePdfDelivery.kt:158-164`）走
`onWriteFailed("无法复制 PDF")`，不会输出无标识 PDF。内存代价：子集 ≤ 选中页、≤ `maxPdfBytes`
（24 MiB 批量上限）；若评审要求零额外内存，可改为 `cacheDir` 临时文件 + `FileOutputStream`，
判据不变。

---

## 4. 变更闭合面（改一处，枚举每一处）

| 改动 | 牵动点 | 处置 |
|---|---|---|
| `MistakePdfExportInput.hasModelParsedText` | 资格层构造（`MistakePdfEligibility.kt:161`）、批量（`MistakePdfBatchEligibility.kt:80`）、卷面编辑（`MistakePdfPaperEdit.kt:219`）、草稿 DTO 双向（`MistakePaperDraftStore.kt:162/187/316`） | 全部显式赋值；生产 4 处 + 测试 helper（约 15 处）补 `false` 或真实值 |
| `mistake-export-v3 → v4` | 缓存目录名/`findExisting`、`PreparedMistakePdfToken`、持久化 token、恢复路径 | 旧目录不可达；无数据迁移；旧 token 的 `reopenVerified` 自然失败 |
| `mistake-batch-v3 → v4` | 批量卷面草稿 `sourceInputSha256`、编辑后指纹 | 旧批量草稿不可复用（草稿版本也在 §3.3 失效） |
| `mistake-paper-edit-v1 → v2` + `FORMAT_VERSION 1→2` | 已落盘卷面草稿 | 旧草稿 `toModelOrNull()` 返回 null（fail-closed） |
| `prepare` 注入 | `validatedPageCount`、`writeDigest`、`publish`、`verifyIntegrity`、`reopenVerified`、`copyWhole` 的摘要比对 | 摘要全部绑定注入后字节；`copyWhole` 比对 `prepared.sha256` 仍成立 |
| `writeSubset` 重注入 | 打印适配器单测、字节数断言 | 只影响部分页路径；整份路径不变 |
| `isAiGeneratedExportImage(null)` | 纯文本导出 | 只在“有图”时调用；否则 `null` 会被误判为 AI 图，导致全量误标（伪标识） |
| 注入后大小 | `input.maxPdfBytes` / 批量上限 | 注入后再次判限；超限 `PDF_SIZE_LIMIT_EXCEEDED`，不发布 |
| 指纹断言/金样 | 现有测试多用相对比较（`assertNotEquals`）或 `"1".repeat(64)` 占位；本会话 grep 未发现硬编码的 v3 指纹常量 | 不需要改数值；若评审发现硬编码，按 v4 重算 |

**DOCX 不在本接缝内**：`DocxMistakeRenderer` / `docProps/custom.xml` 的对称缺口是 KD-75 原文
另立的条目（R1 U9），本草案只处理 PDF `Info /AIGC`，不把 DOCX 的半成品混进来。

---

## 5. 判据（每条都可失败）

1. **完整性**：`prepared.verifyIntegrity() == true`，且 `sha256` 是**注入后**字节的摘要；
   `readRecordedDigest == sha256 == sha256File(file)`（`MistakePdfExporter.kt:64-72`）。
2. **平台接受**：注入后 `PdfRenderer` 打开成功，`pageCount` 与注入前相等；第 0 页渲染非白。
3. **语义读回**：`PdfAigcReader.readLatestAigc(labels)` 的 `Label=="1"`、
   `ContentProducer=="SmartMistakeBook"`、`ProduceID==input.inputSha256`；L3 用 host pypdf 读
   `/Info` 的 `/AIGC` 三字段一致（独立读者，不用自写 reader 自证）。
4. **负向**：纯用户内容（无 AI 图、无 `MODEL_DOCUMENT_PARSE`）的 prepared PDF
   `readLatestAigc == null`，且 `readInfoStrings` 无 `AIGC` 键。
5. **模型文本**：同一 blocks、仅 `blockEvidence` 出现 `MODEL_DOCUMENT_PARSE` 的文档必须打标
   （`PdfAigcContentPolicy.shouldLabel(false, true) == true`）。
6. **缓存失效**：旧 `mistake-export-v3` 目录不再被 `findExisting` 命中；同一新输入重复
   `prepare` 命中缓存时**不会**二次注入（`readLatestAigcKeyCount == 1`，字节不变）。
7. **打印**：整份打印字节与 prepared 文件逐字节一致（已有 `copyWhole` 摘要判据）；部分页打印
   输出也能读回 `/AIGC`，且页数等于选中页数。
8. **失败闭包**：注入器抛 typed error 时 `prepare` 抛
   `MistakePdfExportException(GENERATED_PDF_INVALID)`，`cache/mistake_pdf_exports` 下无发布目录、
   无 `.pending-` 残留。
9. **批量**：任一题含模型文本 → 批量 prepared 有 `/AIGC`；全部纯用户 → 无 `/AIGC`。
10. **不误标**：`QUESTION_DIAGRAM` 且无其它 AI 内容、纯用户文本、`MODEL_DOCUMENT_PARSE` 之外
    的 provenance（`USER_TRANSCRIPTION`/`USER_CORRECTION`/`LOCAL_OCR` 等）在**无 AI 图**时都不打标。

---

## 6. 测试计划（落位后）

| 层 | 用例 | 断言 |
|---|---|---|
| JVM（`core/export/src/test`） | `MistakePdfEligibilityTest` 增：模型文本 → `input.hasModelParsedText == true`；纯用户 → false | §5.5、§5.10 |
| JVM | `MistakePdfBatchEligibilityTest` 增：任一题模型文本 → 批量布尔 true | §5.9 |
| JVM | `MistakePaperDraftStoreTest` 增：v1 草稿被拒；v2 往返保留布尔 | §3.3 失效语义 |
| 设备（`core/export/src/androidTest`） | `preparedExportCarriesAigcAndStillOpensInPlatformRenderer`：真实 `MistakePdfExporter.prepare` → `verifyIntegrity` → `PdfRenderer` 页数/非白 → `PdfAigcReader` 读回 | §5.1–§5.3 |
| 设备 | `userOnlyExportHasNoAigcKey` | §5.4 |
| 设备 | `partialRangePrintKeepsAigc`（打印适配器 + 选中页） | §5.7 |
| host（pypdf） | 对设备落盘 prepared PDF 读 `/Info` 的 `/AIGC` | §5.3 L3 |

复跑命令（设备落位后；**禁止** `:app:connectedLocalFirstDebugAndroidTest`）：

```bash
adb shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.export.MistakePdfExporterInstrumentedTest#preparedExportCarriesAigcAndStillOpensInPlatformRenderer \
  com.tingyun.smartmistakebook.core.export.test/androidx.test.runner.AndroidJUnitRunner
```

---

## 7. 不做 / 未验证

- 未应用任何 hunk；`core/export` 仍对 `PdfAigc*` 零引用（本会话 `grep` 复核）。
- 未运行 Gradle / 编译 / 单测 / 设备；§3 的代码片段未经过编译器。
- 未验证 Android PDFium 对 `prepare` 产物增量段的接受度（R9 §10.2 #2，KD-75 U3）；这正是
  §6 设备用例要闭环的项。
- 未做 pypdf 独立读者；未验证打印子集的实际字节。
- 未改 `core/domain` / `feature/*` / `settings.gradle.kts`；§2.1 的移动与 §3 的 hunk 均待评审。

## 8. 来源（本会话实读）

- `docs/research/2026-11-03-instrumentation-pdf-verification.md` §7.6（本草案的 R9 输入）、
  §0、§2.6、§3.1、§5.2、§7.2–§7.4。
- `docs/known-defects.md:2894`（KD-75 Fix direction：v3→v4、writeSubset、判据 (a)+(b)）。
- `docs/research/2026-11-02-pdf-incremental-aigc.md` §2.4/§7.2（缓存与注入位置）。
- 源码：`core/export/.../MistakePdfExporter.kt`、`MistakePdfEligibility.kt`、
  `MistakePdfBatchEligibility.kt`、`MistakePdfPaperEdit.kt`、`MistakePaperDraftStore.kt`、
  `MistakePdfDelivery.kt`、`AiGeneratedImageCaption.kt`；`core/data/.../pdf/*`；
  `core/model/.../CapturedQuestionDocument.kt`；`core/export/build.gradle.kts`。
