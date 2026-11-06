# 批次 47 H 域 FAIL 处置单（H-018 / H-061 / H-062）

> 口径：不得洗绿。FAIL 只有凭新证据才转 PASS；BLOCKED/热区项保留原判并写明出口。
> 本批 H 域结果：**H-061 转 PASS（KD-84 关闭）**；H-018、H-062 保留 FAIL（KD-85 + DOCX 载体缺口，
> 修复落点跨热区/跨线，非本批可闭合）。

## H-061 · 部分页打印子集保留文档级 /AIGC — **已修复，FAIL→PASS（KD-84 关闭）**

- 根因：`PreparedPdfPrintWriter.writeSubset`（`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt:215` 起）
  把选中页经 `PdfRenderer` 逐页重绘进新 `PdfDocument` 再写出；文档级 Info 字典不随页内容复制，
  子集产物只剩 Skia `/Info`。整份打印走 `copyWhole` 字节复制，不受影响。
- 修复（KD-84 方案 ①，`core/export` 已批准 hunk，未触热区）：
  - `MistakePdfExporter.kt`：`PreparedMistakePdf` 新增 `internal val aigcMetadataJson: String?` ——
    读回 prepared 产物已写的同一份 `/AIGC` JSON；产物存在但读不出按 `GENERATED_PDF_INVALID` fail-closed。
  - `MistakePdfDelivery.kt`：`writeSubset` 先把子集缓冲成字节，再经新增 `relabelSubset(prepared, bytes)`
    用同一 `PdfAigcInjector` 重注入后一次写出；未打标的 prepared 原样写出（不制造反向假阳性）。
  - 设备用例 `wholePrintKeepsAigcWhilePartialPrintDropsTheDocumentLevelEntry` 改名为
    `wholePrintAndPartialPrintBothKeepTheDocumentLevelAigcEntry`，`assertNull(known gap)` 换成
    正向断言（`/AIGC` 非空、`Label=1`、`prepared.inputSha256 == produceId`、键数=1、页数=1）。
- 先红后绿：
  - 红：`ANDROID_SERIAL=emulator-5584 ./gradlew :core:export:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="…AigcInstrumentedTest#wholePrintAndPartialPrintBothKeepTheDocumentLevelAigcEntry" --offline` →
    `AssertionError: partial-page print must keep the document-level /AIGC`，**EXIT=1**（`h-061-red-b47.log`）。
  - 绿：同命令 → `Finished 1 tests`，**EXIT=0**（`h-061-green-b47.log`；junit `h-061-connected-green.xml` tests=1 failures=0）；
    同 APK `am instrument` 全类 `OK (7 tests)`。
- 独立读者（KD-84 关闭条件）：宿主 pypdf 6.16.2 读回 `print-prepared-labeled.pdf`（4 页）与
  `print-subset-relabeled.pdf`（1 页 = 所选页数），`/AIGC` 均在、`Label=1`、`ProduceID` 同为
  `9458cbe2cd1c35bb3d2e469917891037a222c8c7d20add1b303dac26d39b74e3`（= 设备 cache 目录名 = `input.inputSha256`）；
  **EXIT=0**（`h-061-pypdf-b47.log`）。
- 复跑命令与 exit：
  - `python -I tools/ci/h_compliance_gate.py --only H-061` → EXIT=0（`h-061-gate-b47.log`）
  - `python -I tools/ci/fix_relapse_gate.py --gate FIX-T04` → EXIT=0（门已按关闭态更新；证据缺失/实现回退即转红）
  - JVM：`./gradlew :core:export:testDebugUnitTest --offline` → 16 套 172 tests，0 failures 0 errors
- 残余边界：判据未扩大（KD-85 路径仍不打标）；`copyWhole` 字节路径不变；
  本批 Gradle 冷配置缓存下 `processDebug*Resources` 触发 `aapt2 9.3.0` 依赖验证失败
  （`gradle/verification-metadata.xml` 无该组件，属仓库级基建缺口，与本修复无关，未越权改）。

## H-062 · 模型改卷文本带 /AIGC — **保留 FAIL（KD-85 open；热区阻塞）**

- 根因（读码，`docs/known-defects.md` KD-85）：`MistakePdfPaperEdit.toDraft()`
  （`core/export/.../MistakePdfPaperEdit.kt:233`）固定写
  `hasModelParsedText = sourceInput.hasModelParsedText`；自然语言改卷的模型文本
  由 `feature/tutor/.../TutorPaperEditApplier.kt` 写入 `MistakePdfPaperEdit`，
  来源纯用户时判据为假 → 模型改写文本导出 PDF 无 `/AIGC`。
- 为什么本批不修：KD-85 方案 ① 的置位点在 `TutorPaperEditApplier.apply`（`feature/tutor`，热区禁改）；
  方案 ② 需用户把"模型改卷不计 AIGC"写入不可变清单。替代修法（`toDraft` 见编辑即置位）被 KD-85 明令禁止
  （会把用户手工编辑误标为 AI）。
- 复跑命令与 exit：`python -I tools/ci/h_compliance_gate.py --only H-062` → **EXIT=1**（`h-062-b47.log`）；
  `python -I tools/ci/fix_relapse_gate.py --gate FIX-T05` → EXIT=0（门锁 open 现状，实现回退即转红）。
- 出口（runbook FIX-T05）：主会话授权热区后按 KD-85 方案 ① 落 `modelTextApplied` 置位 +
  `sourceInput.hasModelParsedText || modelTextApplied`，再按其关闭条件做设备读回两向用例。
- 防漂移：`h_compliance_gate --only H-062` 现要求 KD-85 关闭标记与置位落码同时成立才转绿。

## H-018 · 全部交付格式与打印子集都有文件级隐式标识 — **保留 FAIL（剩余 DOCX 载体 + KD-85）**

- 根因（三分量）：
  1. KD-84 部分页丢标 —— 本批已修（见 H-061，KD-84 已 closed）；
  2. KD-85 模型改卷文本漏标 —— 热区阻塞（见 H-062）；
  3. DOCX 无文档级 /AIGC —— `DocxMistakeRenderer`/`MinimalDocxWriter` 只写
     `[Content_Types].xml` + `_rels/.rels` + `word/document.xml`（+图片部件），无 `docProps/*` 部件，
     文档级隐式标识无载体（runbook FIX-T07；`h-018-b47.log` EXIT=1 中的剩余缺口即 2+3）。
- 为什么本批不修 DOCX：载体选型（`docProps/custom.xml` vs `core.xml` keywords）与
  GB 45438-2025 对文档载体的对应关系是跨线设计裁定，且 H-018 因 KD-85 无法在本批转绿，
  先修不改变该行判词；留 FIX-T07 出口（"仅在代码里出现字面量不算关闭"）。
- 复跑命令与 exit：`python -I tools/ci/h_compliance_gate.py --only H-018` → **EXIT=1**（`h-018-b47.log`）。
- 出口：KD-85 热区授权（FIX-T05）+ DOCX 载体落地并独立读回（FIX-T07）后，本行重跑门转绿。

## 证据清单（本目录）

| 文件 | 内容 |
|---|---|
| `h-061-red-b47.log` | 修复前设备红轮（EXIT=1） |
| `h-061-green-b47.log` | 修复后设备绿轮 + `am instrument` 全类 + logcat 摘录（EXIT=0） |
| `h-061-connected-green.xml` | 绿轮 junit（tests=1 failures=0） |
| `h-061-gate-b47.log` | `--only H-061` PASS（EXIT=0） |
| `h-061-pypdf-b47.log` | 宿主 pypdf 独立读回 prepared/subset（EXIT=0） |
| `print-prepared-labeled.pdf` / `print-subset-relabeled.pdf` | 设备产物字节（63,228 B / 18,029 B） |
| `h-018-b47.log` / `h-062-b47.log` | 保留 FAIL 两行的门禁复跑（EXIT=1） |
