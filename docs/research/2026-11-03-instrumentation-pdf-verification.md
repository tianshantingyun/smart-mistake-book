# R9：instrumentation 侧 PDF 生成/读取的可靠做法（KD-75 设备往返）

| 字段 | 值 |
|---|---|
| evidence-id | R9-2026-11-03 |
| 批次 | 41（计划 §1 R9；服务对象 = §2 WS2 items 11–17） |
| executed-at | 2026-11-02（本机时间；文档命名按计划批次 41） |
| head | `62522930bc8bae51be1523c954f39449e054b6af`（未做 git 写） |
| 执行者 | 研究子代理（只读：读源码 / 官方文档 / GitHub 元数据 / adb 只读探针） |
| 未执行 | 任何 Gradle 任务、任何 `am instrument`、任何 PDF 渲染/往返实跑、任何 git 写 |
| verdict | 结论可用；7 条运行时事实显式标 UNVERIFIED（§10.2） |

> 口径：本文件只写**本轮实读/实跑**得到的结论。官方契约以 `developer.android.google.cn`（同一文档站的镜像，`developer.android.com` 本轮 443 连接超时）为准；`source.android.com` 与 `android.googlesource.com` 本轮均不可达。社区来源一律标注，单一来源按“待验证”处理。

---

## 0. 结论摘要（可直接照做的 10 条）

1. **生成用 `PdfDocument`（API 19+），读回必须“先落成真实文件，再用 `ParcelFileDescriptor` + `PdfRenderer`（API 21+）”**。本项目 `minSdk = 23`（`app/build.gradle.kts:28`、`core/data/build.gradle.kts:18`），两侧都无版本门槛。仓内已有同形写法：`core/data/src/androidTest/.../VisualDrawOpsPdfPainterInstrumentedTest.kt:84-100`（`File.createTempFile` → `ParcelFileDescriptor.open(MODE_READ_ONLY)` → `PdfRenderer(...).use` → `openPage(0).use`）。
2. **不能把 `content://`、管道、或 APK 内压缩 asset 的 fd 直接交给 `PdfRenderer`**。官方：fd “must be seekable, i.e. its data being randomly accessed, e.g. pointing to a file”，否则抛 `IllegalArgumentException`；官方样例 `googlearchive/android-PdfRendererBasic` 的注释原文是 “Since PdfRenderer cannot handle the compressed asset file directly, we copy it into [a file]”，随后 `ParcelFileDescriptor.open(file, MODE_READ_ONLY)`。
3. **fd 所有权归 renderer**（官方：“takes ownership of the passed in file descriptor and is responsible for closing it”）。仓内 `ParcelFileDescriptor.open(...).use { PdfRenderer(it).use { … } }` 的双层 close 是安全的：`ParcelFileDescriptor.closeWithStatus` 首行 `if (mClosed) return;`（AOSP `core/java/android/os/ParcelFileDescriptor.java:846`）。**但** renderer 关闭后不得再读 `descriptor.fd`。
4. **页必须先 `close()` 再关 renderer，且同一时刻只开一页**。官方：`close()` 抛 `IllegalStateException` “if any page is opened and not closed”；“you can have only a single page opened at any given time”。仓内 `openPage(...).use` 已满足。
5. **位图必须 `ARGB_8888`**（官方 Page 文档），且 PDFium 不铺白底——断言前必须 `bitmap.eraseColor(Color.WHITE)`（仓内 `VisualDrawOpsPdfPainterInstrumentedTest.kt:111`、`PdfBatchImportPageRenderer.kt:64`）。渲染模式 `RENDER_MODE_FOR_DISPLAY` / `RENDER_MODE_FOR_PRINT` 只影响质量，**断言不得绑定精确像素值**。
6. **单位是点（1/72 英寸）**：`PdfDocument.PageInfo` 与 `PdfRenderer.Page.getWidth/getHeight` 同源；后者返回 `int`，A4 即 `595×842`（截断）。页尺寸断言用整型相等，不做浮点容差。
7. **API 级别差异集中在一个点：Android 15 起 PDF 被模块化**。官方 Android 15 特性页原文：“The PdfRenderer has been moved to a module that can be updated using Google Play system updates independent of the platform release, and we're supporting these changes back to Android 11 (API level 30) by creating a compatible pre-Android 15 version of the API surface, called `PdfRendererPreV`.” → 文本抽取/搜索/表单/密码（API 35）在旧设备上**不能假设可用**：`PdfRendererPreV` 标 “Added in API level 35 / Also in S Extensions 13”。本轮 4 台设备最高 API 34，且 API 34 模拟器 `ro.build.version.extensions.s` 为空（§1.3）→ 这类断言在本机矩阵上不可跑（UNVERIFIED）。
8. **临时文件**：写 `context.cacheDir` 可用（实测文件位 `-rw-------`）；但**证据文件要放 `filesDir`**，因为官方写明 “Android may delete these cache files to recover space”。外部目录不可当默认：API 23 模拟器上 `/sdcard/Android/data` 根本不存在（§5.1）。
9. **seccomp 不是 PDF 的主要风险面**。官方：API 26 起对所有 app 施加 seccomp 过滤器（Android 8.0 changes 原文）；实测 API 23 的 app 进程 `Seccomp: 0`、API 34 的同设备第三方 app 进程（`com.google.android.gms`）`Seccomp: 2 / Seccomp_filters: 1`。真正会杀掉 instrumentation 的是 **PDFium 对畸形输入的原生崩溃**（SIGSEGV/SIGABRT，社区单一来源，§6.3 标待验证）→ 畸形用例**一类一进程**跑，崩溃记“未判定/缺陷”，不得写成“抛了 IOException”。
10. **`PdfRenderer` 只证明“平台 PDFium 接受这份字节”，不证明 `/AIGC` 语义正确**；`PdfAigcReader` 自述“不是独立读者”（`core/data/src/main/kotlin/.../pdf/PdfAigcReader.kt:10-11`）。闭合 KD-75 的独立读者用 **host 侧 pypdf**（BSD-3，本机 6.16.2）或 **test-only 模块引 pdfbox-android**（Apache-2.0，但 2023-01 后未发版）。PyMuPDF 是 AGPL-3.0/商业双许可 → **只作本机校验工具，不进依赖**（§9）。

---

## 1. 任务、边界与既有事实

### 1.1 本项在批次 41 的位置

- 计划 §1 R9：为 **KD-75 设备往返用例**（§2 WS2 items 11–17）给出测试结构、断言与失败模式。
- 不在本项范围：写测试代码（WS2 实施）、改 `core/export`（热区，只出 patch 草案，item 17）、设备实跑（WS2 item 16）。
- KD-75 的验收判据原文（`docs/known-defects.md:2896`）要求：真实 `DeterministicMistakePdfRenderer` 产物 → `PreparedMistakePdf.verifyIntegrity()` → `PdfRenderer` 页数一致 → 读回 `Label/ContentProducer/ProduceID` → 负向（纯用户内容读不到 `/AIGC`）→ **独立读者至少一次**。

### 1.2 仓内现状（本会话实读，均为当前树）

| 位置 | 事实 |
|---|---|
| `core/data/src/androidTest/.../visual/VisualDrawOpsPdfPainterInstrumentedTest.kt:84-100` | 设备侧“落文件 → `MODE_READ_ONLY` → `PdfRenderer` → `openPage().use`”的既有范式；`:103-126` 栅格化后逐像素比较 |
| `core/data/src/androidTest/.../visual/VisualDrawOpsPdfPainterInstrumentedTest.kt:478` | 已有“真文件目标 → 长度/尾标回读 + `PdfRenderer` 页数回读 + `renameTo` 发布”的用例（P-PDF-01） |
| `app/src/androidTest/.../SharedCaptureTestFiles.kt:62-88` | 设备夹具生成器 `pdfBytes(pageCount)`：`PdfDocument` + `PageInfo.Builder(595, 842, n)` + `writeTo` + `close` |
| `core/data/src/main/.../capture/PdfBatchImportPageRenderer.kt:54-87` | 生产读取链：`ParcelFileDescriptor.open(privatePdf, MODE_READ_ONLY)` → `PdfRenderer` → 逐页 `openPage().use` → `eraseColor(WHITE)` → `render(..., null, null, RENDER_MODE_FOR_DISPLAY)` |
| `core/data/src/androidTest/.../capture/BatchImportSourceRangeInstrumentedTest.kt:142-166` | 既有 fail-closed 断言范式：畸形字节断言 `IOException \|\| IllegalArgumentException`，**不绑定 message**；并断言无 staging 残留 |
| `core/data/src/main/.../pdf/PdfAigcReader.kt:10-11` | 自述“**不是独立读者**”，不能自证 |
| `core/data/src/main/.../pdf/PdfAigcReader.kt:100-113` | `readStructuralPageCount` 只做结构检查，“真实打开/页数归设备轨 `PdfRenderer`（本批未跑）” |
| `core/data/src/main/.../pdf/PdfAigcErrorCode.kt:13-56` | 失败码可作断言枚举（`NOT_PDF_HEADER`、`STARTXREF_MISSING`、`XREF_TABLE_MALFORMED`、`ENCRYPTED`、`LINEARIZED` 等） |
| `core/data/src/test/.../pdf/PdfTestFixtures.kt:15` | xref 流/表夹具构造器是 `internal object`，位于 **`src/test`**（JVM），androidTest 与它不同源 → 设备侧需要最小镜像（见 §7.2 注） |
| `core/data/src/main/.../pdf/PdfAigcContentPolicy.kt:31-38` | 打标判据 `shouldLabel(hasAiGeneratedImage, hasModelParsedText)`，`MODEL_DOCUMENT_PARSE` 出现即打标 |
| `core/export/src/androidTest/.../MistakePdfExporterInstrumentedTest.kt:48,90,134,152,179` | 已有 5 个 `@Test` 覆盖 prepare/preview/integrity，**但 `grep -rn PdfAigc core/export/` 零命中**（本轮实跑） |
| `core/export/build.gradle.kts:29-30` | `core/export` androidTest 只引 `androidx.test.ext.junit` / `runner`；`core/export/src/androidTest/` 下只有 `kotlin/`，无 assets |
| `core/data/build.gradle.kts:35-36` | core:data 的 androidTest assets 已挂 `core/database/schemas`（加 PDF 夹具会与 schema 资产混放，见 §7.3） |

**既有设备证据（他轮，非本轮实跑）**：`docs/research/evidence/2026-10-29-batch-34/step05-parity-painter-pdf.md` 记录 `VisualDrawOpsPdfPainterInstrumentedTest` = `OK (16 tests)`（`:31`、`:55`），设备 `emulator-5562` / API 34（`:15-16`），APK sha256 `53e69f48…97caf`（`:20`），exit-code 0（`:21`）。这证明**同一范式在 API 34 x86_64 上跑通过一次**；不构成本批任何设备的绿。

### 1.3 本轮实测的设备与工具矩阵

```text
$ "C:/Android/Sdk/platform-tools/adb.exe" devices -l
emulator-5562  device product:sdk_gphone64_x86_64  model:sdk_gphone64_x86_64
emulator-5566  device product:sdk_gphone64_x86_64  model:sdk_gphone64_x86_64
emulator-5584  device product:sdk_gphone64_x86_64  model:sdk_gphone64_x86_64
emulator-5596  device product:sdk_phone_x86_64     model:Android_SDK_built_for_x86_64
$ … -s <serial> shell getprop ro.build.version.sdk
5562=34  5566=34  5584=34  5596=23
$ … -s emulator-5562 shell getprop ro.build.version.extensions.s
(空)                      # 无 S SDK Extension → PdfRendererPreV 不可用
```

| 项 | 实测值 |
|---|---|
| 设备 API 分布 | 34×3、23×1（`minSdk` 边界设备存在） |
| API 34 已装测试 APK | `com.tingyun.smartmistakebook.core.data.test`、`…core.export.test`（`pm list packages`） |
| 普通 app 进程 seccomp | API 23（本项目 app，pid 7932）：`Seccomp: 0`；API 34（`com.google.android.gms`，pid 14563/14622）：`Seccomp: 2`、`Seccomp_filters: 1`（`/proc/<pid>/status`；API 34 上本项目 app 未运行，故取同设备另一 u0_a 进程） |
| host Python | 3.13.14；`pypdf 6.16.2`；`PyMuPDF 1.28.2`（`fitz` 已弃用，应 `import pymupdf`） |
| host `qpdf` | 未安装（`where qpdf` 无结果） |

---

## 2. 官方契约（一手）

来源均为 `developer.android.google.cn` 镜像（与 `developer.android.com` 同文；本轮 `.com` 443 超时）。以下引文为页面正文原文。

### 2.1 `PdfDocument`（API 19+）

- `startPage(PageInfo)` 返回空白页；`finishPage(page)` 必须调用；**未 finish 时不得 `startPage`、不得 `writeTo`、不得 `close`**。
- `writeTo(OutputStream)`：“You can call this method multiple times.”；“Do not call this method after `close()`. Also do not call this method if a page … is not finished”。
- `close()` 后“none of its methods should be called”。
- “This class is not thread safe”，同一时刻只能写一页。
- `PageInfo` 的宽高单位：“PostScript points (1/72th of an inch)”。
- **未文档化**：`writeTo` 是否关闭传入的 stream。→ 测试一律 `FileOutputStream(...).use { doc.writeTo(it) }`，不依赖该行为。

### 2.2 `PdfRenderer`（API 21+）

- 构造：`PdfRenderer(ParcelFileDescriptor)`；文档原文：

  > “The provided file descriptor must be seekable, i.e. its data being randomly accessed, e.g. pointing to a file.”
  > “This class takes ownership of the passed in file descriptor and is responsible for closing it when the renderer is closed.”
  > “If the file is from an untrusted source it is recommended to run the renderer in a separate, isolated process with minimal permissions to limit the impact of security exploits.”
  > “The constructor should be instantiated on the … WorkerThread as it can be long-running while loading the document.”

- 异常：`IOException`（读文件出错）、`IllegalArgumentException`（fd 不可 seek）、`NullPointerException`、`SecurityException`（需要密码 / 不支持的加密方案）。
- `close()`：`IllegalStateException` “if any page is opened and not closed”。
- `pageCount`：`IllegalStateException`（已关闭）；`openPage(index)`：越界 `IllegalArgumentException`。
- 线程：“thread safe … however only one thread will be executed at a time”。
- **文档没有**“fd 必须只读”的表述；仓内统一用 `MODE_READ_ONLY` 是工程约定（`PdfBatchImportPageRenderer.kt:54`、`VisualDrawOpsPdfPainterInstrumentedTest.kt:94`），不要把它写成平台要求。

### 2.3 API 35 的 `PdfRenderer(ParcelFileDescriptor, LoadParams)`

- `LoadParams`（API 35 / S Ext 13）目前只文档化 `getPassword()`；“Returns null if not set”。
- 同一构造器额外要求 `LoadParams` 非 null；`SecurityException` 覆盖“密码错误/方案不支持”。

### 2.4 `PdfRenderer.Page`

- `render(Bitmap, Rect, Matrix, int)`：目标位图 “must be `ARGB_8888`”；`destClip`/`transform` 可为 null（null 时自动适配整页）；矩阵必须 affine；`transform` 语义是“page coordinates in points (1/72") → bitmap pixels”。
- `getWidth()/getHeight()`：“in points (1/72")”，返回 `int`，不保证各页等宽。
- API 35 新增 12 个方法，其中与本项相关的：`getTextContents()`、`searchText(String)`、`selectContent(...)`、`getImageContents()`、`getLinkContents()`、`getGotoLinks()`、`render(Bitmap, Rect, Matrix, RenderParams)`（该重载“will take care of closing the bitmap”）。
- 因此：**文本级断言只能出现在 API ≥ 35（或 S Ext 13）的设备上**，且不能作为 KD-75 的主判据。

### 2.5 `PdfRendererPreV` 与模块化

- `PdfRendererPreV`：“Added in API level 35”、“Also in S Extensions 13”；构造函数同样要求 seekable fd、接管 fd；文档同样建议“对不可信文件用独立、最小权限进程”。
- Android 15 特性页：PdfRenderer 已移入可通过 Google Play 系统更新独立升级的模块，并以 `PdfRendererPreV` 把新 API 面回放到 Android 11（API 30）。
- 推论：**同一 API 级别不同设备的 PDF 行为可能不同**（模块版本随 Play 系统更新漂移）。证据落盘必须记录设备 API + 系统补丁级别，不能只写“API 34”。

### 2.6 API 级别矩阵（本项相关）

| 能力 | 最低 API | 本机矩阵可用性 |
|---|---|---|
| `PdfDocument` 生成 | 19 | 全可用（minSdk 23） |
| `PdfRenderer` 读/渲染 | 21 | 全可用 |
| `PdfRenderer` + `LoadParams`（密码） | 35 / S Ext 13 | **不可用**（最高 API 34，无 S Ext） |
| `Page.getTextContents/searchText/selectContent` | 35 / S Ext 13 | **不可用** |
| `PdfRendererPreV` | 35 / S Ext 13 | **不可用** |
| seccomp 过滤器 | 26（对所有 app） | API 34 有、API 23 无 |

---

## 3. 生成侧（`PdfDocument`）可靠做法

### 3.1 顺序与异常

必须遵守：`startPage` → 绘制 → `finishPage` →（可多页）→ `writeTo` → `close`。

两个真实失败形状：

- `writeTo` 时仍有未 finish 的页 → `IllegalStateException`（官方“do not call this method if a page … is not finished”）。
- `close()` 时仍有未 finish 的页 → 同样违反契约。**用 `try/finally` 直接 `close()` 会掩盖原始异常**：仓内 `SharedCaptureTestFiles.kt:62-88` 的 `finally { document.close() }` 在 `writeTo` 抛错时会再抛一次，测试看到的是掩盖后的异常。新用例应写成“先 `runCatching` 记原始异常，再 `close`”，或在 `writeTo` 成功后单独 close（与 `PdfWriteIntegrity` 里“先 finishPage 再 close 防掩盖”的既有裁定一致）。

### 3.2 单位与取整

- `PageInfo.Builder(width, height, pageNumber)` 单位 = 点；仓内统一 A4 `595×842`（`SharedCaptureTestFiles.kt:68`）。
- `Page.getWidth/getHeight` 返回 `int`，A4 实测为 `595/842`（`VisualDrawOpsPdfPainterInstrumentedTest.kt:91-99` 已按整型断言）。
- **不要**把 `PdfDocument` 的页尺寸与 `PdfRenderer` 的页尺寸做浮点比较；两侧都取整型相等。

### 3.3 输出字节形态（对注入器分支的影响）

`PdfAigcInjector` 支持经典 xref 表与 xref 流（`PdfAigcErrorCode.kt` 里 `XREF_STREAM_*` 一族），并且有“原文件没有 Info → 新建 Info”的分支（`INFO_MISSING_ALLOWED` 不列为错误）。但**Skia（`PdfDocument`）产出的 PDF 到底是“有 Info/无 Info”“经典表/xref 流”本轮未验证**：

- 无法用 JVM 复现（`android.graphics.pdf` 是设备侧实现，仓内 JVM 夹具是自写字节，不代表 Skia 输出）；
- 无法用 host 复现（没有 Skia PDF 后端）。

→ **WS2 首次设备跑必须记录实际分支**（`readInfoStrings()` 是否为空、`PdfCrossReferenceIndex` 解析到表型还是流型），否则“注入成功”可能只覆盖了 4 个分支中的 1 个。这条标 **UNVERIFIED**（§10.2 第 1 条）。

### 3.4 设备夹具骨架（与仓内既有写法同形）

```kotlin
// 仅示意；落码归 WS2。
private fun androidPdfBytes(pages: Int, text: String = "fixture"): ByteArray {
    val document = PdfDocument()
    var failure: Throwable? = null
    return try {
        ByteArrayOutputStream().use { output ->
            repeat(pages) { index ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(595, 842, index + 1).create(),
                )
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(text, 48f, 96f, Paint().apply { textSize = 28f })
                document.finishPage(page)
            }
            document.writeTo(output)
            output.toByteArray()
        }
    } catch (t: Throwable) {
        failure = t
        throw t
    } finally {
        runCatching { document.close() }.onFailure { if (failure == null) throw it }
    }
}
```

---

## 4. 读取侧（`PdfRenderer`）可靠做法

### 4.1 必须先落成文件

官方要求 seekable；官方样例 `googlearchive/android-PdfRendererBasic` 的 `PdfRendererBasicFragment.java:154-166`：

```java
// Since PdfRenderer cannot handle the compressed asset file directly, we copy it into
// the application's cache directory...
mFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
mPdfRenderer = new PdfRenderer(mFileDescriptor);
```

同一个事实在 `AssetManager.openFd` 文档里：“Open an **uncompressed** asset by mmapping it”。→ 三条路，按推荐序：

1. **字节 → `cacheDir` 临时文件 → `ParcelFileDescriptor.open(..., MODE_READ_ONLY)`**（推荐；仓内既有范式）。
2. 需要放 assets：必须 `noCompress`（AAPT 不压缩）才能 `openFd`；会增大 APK 且改变打包行为，仅当“必须携带二进制夹具”时才用。
3. `content://` / `openInputStream` / 管道：**不可**直接给 `PdfRenderer`（非 seekable → `IllegalArgumentException`）；必须先复制到文件（生产链 `PdfBatchImportPageRenderer.kt:36-53` 正是这么做的）。

### 4.2 fd 所有权与 close 顺序

- renderer 接管 fd 并负责关闭（官方）。仓内 `ParcelFileDescriptor.open(...).use { PdfRenderer(it).use { … } }` 的双 close 安全：`closeWithStatus` 幂等（`ParcelFileDescriptor.java:845-847`）。
- **但**不要保存 `descriptor` 并在 renderer 关闭后使用（`descriptor.fd` 已无效；某些实现下 fd 号可能被复用）。
- 正确顺序（官方样例 `:174-183`）：`page.close()` → `renderer.close()` → `descriptor.close()`。

### 4.3 页生命周期

- 同一时刻只开一页（官方）。
- 忘记 `page.close()` → `renderer.close()` 抛 `IllegalStateException`，**原始断言会被这条异常盖掉**。仓内 `.use` 已规避。
- 关闭后再用 page 的任何方法 → `IllegalStateException`（官方“If the document/page is closed before invocation”）。

### 4.4 位图与渲染模式

- 必须 `Bitmap.Config.ARGB_8888`；否则行为未文档化（不要写“一定抛某异常”）。
- 先 `eraseColor(WHITE)`，再 `render`；PDFium 不保证铺底。
- `destClip=null` + `transform=null` = 自动适配整页（官方），最省事且跨设备稳定。
- 断言用“非白像素数 > 阈值”“两次独立渲染逐位相等”这类**结构性**判据，不用精确像素值（`VisualDrawOpsParityInstrumentedTest.kt:575-580` 的“同一 PDF 两次 `PdfRenderer` 栅格逐位相等”就是这种口径）。

### 4.5 增量更新 PDF 的设备接受度（KD-75 U3）如何闭环

`PdfAigcInjector` 在文件尾追加 xref 段 + 重定义 Info 对象（R6 `docs/research/2026-11-02-pdf-incremental-aigc.md` §2、§7.2）。Android PDFium 是否接受该追加段，**本轮无设备证据**（`known-defects.md:2900` E-40-02a 判 BLOCKED）。闭环判据必须是**可失败**的：

```text
输入：PdfDocument 产物（N 页）→ inject(/AIGC) → 写 filesDir/roundtrip.pdf
断言 A：PdfRenderer(descriptor).pageCount == N            （PDFium 接受追加段）
断言 B：renderer.openPage(0).use { render(...) } 非白像素 > 0（可渲染）
断言 C：PdfAigcReader.readLatestAigc(bytes) != null        （自洽读回，非独立）
断言 D：host pypdf 读 /AIGC 三字段一致                      （独立读者，§7.1 L3）
```

**只过 A/B 不过 D** 时，结论只能是“平台接受、语义未证”，不得关闭 KD-75。

### 4.6 仓内生产写法（作为对照，不新增依赖）

`PdfBatchImportPageRenderer.kt:54-87` 是本项目唯一的生产侧 `PdfRenderer` 使用点，已包含：seekable 文件、只读 fd、逐页 `use`、白底、按长边缩放、渲染字节上限。新用例应复用同一形状，避免出现第二套读取范式。

---

## 5. 临时文件、目录与权限

### 5.1 三个目录的选择

| 目录 | 实测/文档事实 | 用途建议 |
|---|---|---|
| `context.cacheDir` | 实测 `drwxrws--x`（2771，setgid）；文件 `-rw-------`；官方“Android may delete these cache files to recover space” | 渲染中间物、一次性 PDF 字节 |
| `context.filesDir` | 实测 `drwxrwx--x`（0771）；文件 `-rw-------`（0600） | **证据产物**（往返 PDF、读回 JSON） |
| `getExternalFilesDir(null)` | API 23 模拟器上 `/sdcard/Android/data` 不存在（`ls` 报 No such file or directory）；API 34 模拟器存在且 adb 可列 | 需要 host 直接拉取时用；**必须判 null** |

（实测命令：`adb -s emulator-5562 shell 'run-as com.tingyun.smartmistakebook.core.data.test sh -c "ls -ld . cache files"'`，输出 `. = drwx------`、`cache = drwxrws--x`、`files = drwxrwx--x`；`find files -type f | xargs ls -l` → `-rw-------`。API 23 设备 `run-as com.tingyun.smartmistakebook.offline ls -la files` → `drwx------` + `-rw-------`。）

**注意**：不要断言“files 目录是 0700”——实测是 0771（Android 11+ 备份/共享组位）。可断言的稳定性质是：文件属主 = 应用 uid、`st_mode & 0o007 == 0`（others 无任何权限）。

### 5.2 证据落盘与拉取

- 库模块（`core:data` / `core:export`）的 androidTest 是**独立安装的测试 APK**（实测 `com.tingyun.smartmistakebook.core.data.test` 有自己的 uid 与数据目录，`run-as` rc=0），不是被测 app 的数据目录。证据路径要按“测试 APK 的 filesDir”写。
- 拉取：`adb exec-out run-as <testPkg> cat files/<name> > out.pdf`（要求 debuggable 构建；本轮在 API 23 与 API 34 的 debug 包上 `run-as` 均 rc=0）。
- **不要依赖** `adb shell ls /data/data/<pkg>`：本轮 API 23 模拟器可列，是因为模拟器属性；生产设备不可列，写成用例会假绿。
- 外部目录拉取（API 34 模拟器可列 `/sdcard/Android/data`）跨设备不稳定，只作可选通道。

### 5.3 `FileProvider` 何时需要

- **测试内读回不需要**：同进程、同 uid、`ParcelFileDescriptor.open(file, …)` 即可。
- 需要的是“把文件交给别的应用/系统组件”（分享、打印、外部阅读器）。生产链已由 `MistakePdfDelivery` 处理；**不要在测试里引入 FileProvider** 来“模拟分享”，那会测到另一条链路。

### 5.4 不该做的

- 用 `MediaStore` 插入 PDF 再读回（引入 scoped storage 变量，与本项判据无关）。
- 在 `@Before` 里 `clearPackageData` / 删整个 `filesDir`（会误删他例产物；只清理本用例命名空间）。
- 把 PDF 写进 `androidTest/assets` 再在运行时读（压缩 → 非 seekable → 直接踩 §4.1 的坑）。

---

## 6. seccomp 与进程隔离

### 6.1 官方口径

Android 8.0 changes 原文：

> “Android 8.0 (API level 26) applies a Secure Computing (SECCOMP) filter to all apps. The list of allowed syscalls is restricted to those exposed through bionic.”

### 6.2 本轮实测

| 设备 | `/proc/<pid>/status` |
|---|---|
| API 23（app `com.tingyun.smartmistakebook.offline`，pid 7932） | `Seccomp: 0` |
| API 34（`com.google.android.gms`，pid 14563/14622） | `Seccomp: 2`、`Seccomp_filters: 1` |

→ 结论：**API 23 设备不能用来证明“seccomp 不干扰 PDF”**；过滤器在 26+ 才存在，且 `am instrument` 进程继承被测 uid 的过滤器。

### 6.3 真实风险面是 PDFium 原生崩溃，不是 seccomp

- PDFium 只用 bionic 暴露的常规系统调用（`openat/mmap/read/fstat/futex`），没有已知“PDF 渲染需要被 seccomp 拒绝的 syscall”的证据。本轮**未**在 API 26+ 设备上抓到任何 SIGSYS；不做“已排除”的结论，只记“未见”。
- 已知更实际的风险：旧 Android 上 PDFium 对畸形 PDF 会**原生崩溃**（`FPDF_CloseDocument` 等），社区报告覆盖 Android 5–8（StackOverflow 64078319，**单一来源，待验证**）。这类崩溃的表现是 instrumentation 进程死亡（`INSTRUMENTATION_RESULT: shortMsg=Process crashed.`），**不是** `IOException`。→ 畸形用例必须“一类一进程”，且把“进程崩溃”单独归类，不能与“解析器拒绝”混为一谈。
- 官方对不可信 PDF 的建议是**独立进程 + 最小权限**（`PdfRenderer` 与 `PdfRendererPreV` 文档都写了）。本项目的畸形用例来源是自造字节（可信），因此本进程跑可接受；**若将来接入用户上传 PDF 的 fuzz/回归，必须另起进程**。

### 6.4 对测试结构的直接推论

1. 畸形四类**各自一个 `@Test`，但按类分次 `am instrument`**（`-e class ...`），一次崩溃只损失一个类。
2. 断言口径：`IOException || IllegalArgumentException`（沿用 `BatchImportSourceRangeInstrumentedTest.kt:160-163`），不绑定 message。
3. 崩溃/超时 → 判“未判定（崩溃签名待归因）”，写进证据；**不得**改写成“fail-closed 通过”。

---

## 7. 测试结构（KD-75 设备往返）

### 7.1 三层判据（缺一层就不能关单）

| 层 | 位置 | 证明什么 | 本轮状态 |
|---|---|---|---|
| L1 设备结构往返 | `core/data/src/androidTest/.../pdf/PdfAigcDeviceRoundTripInstrumentedTest` | 平台 PDFium 接受注入后的字节、页数/渲染不变、自洽读回、幂等 | 待 WS2 落码 |
| L2 真实导出链 | `core/export/src/androidTest`（**patch 草案**，热区禁改） | 真实 `MistakePdfExporter` 产物 + 注入 + 完整性 + 渲染 | 待 patch 评审 |
| L3 独立读者 | host（pypdf / PyMuPDF）或 test-only 模块（pdfbox-android） | `/AIGC` 语义正确、增量段被非 PDFium 解析器接受 | 待 WS2 item 16 实跑 |

### 7.2 用例清单（名称 / 夹具 / 断言 / API 门）

> 命名沿用仓内英文句式（如 `preparedPdfOpensPreviewsAndReusesSamePathAndDigest`）。全部无 API 35 依赖。

| # | 用例名 | 夹具 | 断言（可失败） | 对应 WS2 |
|---|---|---|---|---|
| 1 | `injectedPdfOpensInPlatformRendererWithUnchangedPageCount` | `androidPdfBytes(pages=2)` → inject → `filesDir` | `pageCount == 2`；`page(0).width==595 && height==842`；`page(1)` 同（“Skia 写出的页尺寸 = PageInfo 点值”这一等式由 E-05 的 16 例在 API 34 间接支持；换 API 级别首跑要复核） | 11 |
| 2 | `injectedPdfRendersNonBlankAndDeterministicPixels` | 同上 | 白底 + `render(null,null,DISPLAY)` → 非白像素 > 500；同字节两次打开逐位相等 | 11 |
| 3 | `secondInjectionKeepsSingleInfoObjectAndOneAigcKey` | 同 #1 + 再注入一次 | `readLatestAigcKeyCount == 1`；`readLatestAigc().infoObjectNumber` 两次相同；`readInfoStrings()` 的 Title/Author 逐字不变；输出以输入字节为前缀 | 13 |
| 4 | `streamCrossReferencePdfSurvivesInjectionAndPlatformRender` | 设备内构造 xref 流型夹具（镜像 JVM `PdfTestFixtures.streamPdf` 的最小版）→ inject | 注入不抛；`PdfRenderer` 打开成功且页数一致；`readLatestAigc != null` | 12 |
| 5 | `malformedHeaderIsRejectedWithoutPublishingAnyFile` | `"not-a-pdf"` / 截断头 | `PdfAigcInjectionException.code == NOT_PDF_HEADER`（或 `HEADER_TRUNCATED`）；`filesDir` 下无新文件；无 `.partial` 残留 | 14 |
| 6 | `missingStartXrefIsRejectedWithoutPublishingAnyFile` | 去掉尾部 `startxref` | `code == STARTXREF_MISSING`；无文件残留 | 14 |
| 7 | `corruptedXrefTableIsRejectedWithoutPublishingAnyFile` | xref 条目偏移写成 `X…` | `code ∈ {XREF_TABLE_MALFORMED, STARTXREF_OUT_OF_RANGE}`；无文件残留 | 14 |
| 8 | `encryptedPdfIsRejectedWithoutPublishingAnyFile` | 带 `/Encrypt` 的最小夹具 | `code == ENCRYPTED`；无文件残留 | 14 |
| 9 | `platformRendererRejectsTheSameMalformedBytes` | 同 #5–#8 的字节，直接给 `PdfRenderer` | `IOException \|\| IllegalArgumentException`（不绑 message）；**单独 `am instrument` 调用** | 14 |
| 10 | `labelledExportReadsBackLabelAndUnlabelledExportHasNoAigcKey` | 两份夹具：`shouldLabel=true/false` | 打标份 `readLatestAigc().label ∈ {"1","2","3"}` 且三字段非空；未打标份 `readLatestAigc == null` 且 `readInfoStrings()` 无 `AIGC` | 15 |
| 11 | `modelParsedTextPolicyForcesLabelling` | `PdfAigcContentPolicy.shouldLabel(false, true)` | `true`；`shouldLabel(false,false)==false`；`hasModelParsedText(["MODEL_DOCUMENT_PARSE"])==true`（纯判据，放 JVM 更省设备时间） | 15 |

夹具生成方式：**代码内生成**（§3.4），不新增二进制资产。

> 注（#4 流型 xref）：`PdfTestFixtures` 是 `core/data/src/test` 的 `internal object`（`:15`），androidTest 看不到。设备侧必须写一个**最小**的 xref 流构造器（约 40 行字节拼接，沿用 R6 §2.5 的 `/W`、`/Index` 规则），这是为消除“设备用例无法构造流型输入”这一具体失败而新增的最小代码；不得把 JVM 夹具文件整体复制进 androidTest 造成双源漂移。

### 7.3 资源放置

| 方案 | 评价 |
|---|---|
| 代码内生成 PDF（推荐） | 无资产、无 sha 漂移；与 `SharedCaptureTestFiles.kt:62-88` 同源；畸形字节用 `ByteArray` 字面量 + 显式构造步骤注释 |
| `core/data/src/androidTest/assets/pdf/*.pdf` | 需要“构建期同步 + 运行时 sha256”两道门（照 `core/data/build.gradle.kts:35-36` 的 schema 资产与 `golden/` 的镜像做法）；且压缩资产**不能** `openFd`，必须复制到文件。**默认不做** |
| 放 `core/database/schemas` 已挂的 assets 目录 | 混入 schema 资产，禁止 |

结论：**不加二进制资产**。若未来确需第三方产出的 PDF（如真实 Word 导出的畸形件），走“独立 `assets/pdf/` + 构建期 sha256 + 运行时断言”三件套，并在记录里登记来源与 sha。

### 7.4 断言口径细则（正/反例）

**可以断**：

- `pageCount`、`page.width/height`（整型点）。
- 非白像素计数下限（阈值取 `>500` 这类宽松值）、两次渲染逐位相等。
- `PdfAigcReader` 的返回值：`label ∈ {1,2,3}`、三字段非空、Info 原键逐字保留、`readLatestAigcKeyCount == 1`（键集/标签不合法时 reader 自己抛 `AIGC_SCHEMA_INVALID`，用例只需断言“不抛 + 值正确”）
- 失败码枚举（`PdfAigcErrorCode`），不解析 message。
- 产物文件“不存在 / 无 `.partial` 残留 / `st_mode & 0o007 == 0`”。

**不可以断**：

- 异常 message 文本（各 API/模块版本不同；仓内既有测试已明确规避，`BatchImportSourceRangeInstrumentedTest.kt:158-163`）。
- 精确像素值或精确非白像素数（PDFium 抗锯齿随模块版本漂移）。
- `Page.getTextContents()`（API 35+，本机不可用）。
- “`PdfRenderer` 一定抛 `IOException`”（旧版本可能抛 `IllegalArgumentException`，甚至崩溃）。
- “`filesDir` 权限是 0700”（实测 0771）。

### 7.5 复跑命令与证据字段

沿用 E-05 的范式（`docs/research/evidence/2026-10-29-batch-34/step05-parity-painter-pdf.md:36-44`）：

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=<serial>; D=build/device-track-batch41
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r -e class \
  com.tingyun.smartmistakebook.core.data.pdf.PdfAigcDeviceRoundTripInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > "$D/pdf-roundtrip.log" 2>&1
$A -s "$S" logcat -v threadtime -d > "$D/pdf-roundtrip-logcat.txt" 2>&1
```

证据文件必须包含：`head`（源快照）、`git status --porcelain | wc -l`、`device-serial`、`device-api`、`device-abi`、`device-page-size`、`apk-sha256`（测试 APK）、`exit-code`、`OK (n tests)` 原文、失败用例名。**禁止**用 `:app:connectedLocalFirstDebugAndroidTest`。

> 判定注意（本项未实测，属工程常识，落证据时必须核）：`adb shell am instrument` 的 shell exit code 与“用例是否全绿”不一一对应（用例失败/崩溃时仍可能是 0）。判据必须解析日志里的 `OK (n tests)` 与 `FAILURES!!!` 段，exit-code 只作辅助。E-05 同时记录了 `exit-code 0` 与 `OK (16 tests)`（`:21`、`:55`），是正确口径的样例。

L3 独立读者（host）：

```bash
"$A" -s "$S" exec-out run-as com.tingyun.smartmistakebook.core.data.test \
  cat files/pdf-roundtrip/injected.pdf > build/device-track-batch41/injected.pdf
python -I -c "
import pypdf, sys
r = pypdf.PdfReader(sys.argv[1])
info = r.trailer.get('/Info')
print('pages=', len(r.pages))
print('aigc=', None if info is None else info.get_object().get('/AIGC'))
" build/device-track-batch41/injected.pdf
```

（`python -I` 隔离用户 site；pypdf 6.16.2 本机已装。取 `/AIGC` 用 `trailer['/Info']` 而不是 `reader.metadata`，避免自定义键在 `DocumentInformation` 归一化时被漏读；若该键读不到，再退回 `reader.metadata.get('/AIGC')` 交叉核对。PyMuPDF 仅作第二读者，勿进依赖。）

### 7.6 `core/export` 产品接缝 patch 草案（item 17，只出草案）

**现状**：`grep -rn PdfAigc core/export/` 零命中（本轮实跑）；`core/export/src/androidTest` 只有 `kotlin/`，无 assets；androidTest 依赖只有 junit/runner（`core/export/build.gradle.kts:29-30`）。

**草案形状**（不改热区，评审后再落）：

1. 在 `MistakePdfExporter` 的“prepare 完成 → 发布”之间插入一次纯字节步骤：
   `bytes = PdfAigcInjector.inject(bytes, metadata)`，`metadata` 由 `PdfAigcContentPolicy.shouldLabel(hasAiImage, hasModelParsedText)` 决定是否构造；
2. `PreparedMistakePdf.sha256` / `verifyIntegrity()` 改在**注入后**字节上计算（否则完整性断言与发布物不一致）；
3. 缓存指纹 `mistake-export-v3` → `v4`（`MistakePdfEligibility.kt:186`，见 `known-defects.md:2894`）；
4. 打印子集 `writeSubset` 重绘会丢元数据 → 缓冲后注入或显式登记；
5. `core/export` androidTest 增 1 例 `preparedExportCarriesAigcAndStillOpensInPlatformRenderer`，断言同 #1/#10 但输入换成真实 prepared PDF。

判据：注入后 `verifyIntegrity()` 为真、`PdfRenderer` 页数不变、`/AIGC` 可读、未打标产物读不到 `/AIGC`、旧缓存不再命中。

---

## 8. 失败模式表

| # | 症状 | 根因 | 判据/处置 |
|---|---|---|---|
| 1 | `IllegalArgumentException`（构造 `PdfRenderer`） | fd 非 seekable（pipe / 压缩 asset / `openInputStream`） | 先落文件；断言不绑 message |
| 2 | `IOException`（构造） | 字节不是合法 PDF，或文件被并发改写 | 区分“输入坏”与“写入未 flush”：写文件后先 `FileOutputStream.use` 关闭再打开 fd |
| 3 | `IllegalStateException`（`renderer.close()`） | 有 page 未 close | `openPage().use` |
| 4 | `IllegalStateException`（第二次 `openPage`） | 同时开两页 | 官方“only a single page opened at any given time” |
| 5 | `SecurityException`（构造） | 需要密码 / 不支持的加密方案（API < 35 无法解密） | 本项目产物不含 `/Encrypt`；遇到即 fail-closed |
| 6 | `IllegalStateException`（`writeTo`） | 有页未 `finishPage` | 顺序错误，属实现缺陷 |
| 7 | 渲染全白 | 忘记 `eraseColor(WHITE)` 之外的“内容未绘制”/变换错 | 断言非白像素下限；`transform=null` 走自动适配 |
| 8 | 两次渲染像素不同 | 断言了抗锯齿/字体细节 | 只断“同字节同设备两次相等”，不断跨设备 |
| 9 | 进程崩溃（`shortMsg=Process crashed`） | PDFium 对畸形输入原生崩溃（旧版本更易） | 一类一进程；记“未判定/缺陷”，不得写成 IOException |
| 10 | 测试间互相污染 | 共用固定文件名 / 清理整个目录 | 每例独立子目录 + 只清自己命名空间 |
| 11 | 证据文件消失 | 放 `cacheDir` 被系统回收 | 证据放 `filesDir` |
| 12 | `NullPointerException`（外部目录） | `getExternalFilesDir(null)` 返回 null（API 23 模拟器无 SD） | 先判 null，不可用时跳过并记原因 |
| 13 | host 拉取失败 | 非 debuggable 构建 / 路径写成 app 包名 | `run-as <测试APK包名>`；只对 debug 构建 |
| 14 | 注入后页数变化 | 追加段写坏（`/Size`、`startxref` 偏移错） | `pageCount` 必须与注入前相等；不等即缺陷 |
| 15 | 注入“成功”但独立读者读不到 `/AIGC` | 只过 PDFium 自洽，未过独立读者；或读者只读首个 `%%EOF` | L3 必须用跟随 `/Prev` 链的读者（pypdf），并断言读到且仅读到一份 `/AIGC` |
| 16 | 同一 API 级别设备行为不同 | PDF 已模块化，随 Play 系统更新漂移（Android 15 起） | 证据记录 API + 补丁级别；跨设备差异不归因为“随机” |

---

## 9. 选型与许可证（采纳/拒绝理由）

| 候选 | 事实（本轮核实） | 结论 |
|---|---|---|
| `android.graphics.pdf.PdfRenderer`（平台） | API 21+；官方 PDFium 后端；本仓既有设备绿（E-05） | **采纳**为 L1 主判据；但它不是“独立读者”（同一平台实现） |
| pypdf 6.16.2（host） | `LICENSE` 实读为 BSD 3-clause（`gh api …/contents/LICENSE`）；repo 10.3k star，2026-11-01 有推送 | **采纳**为 L3 独立读者首选（零依赖进 APK） |
| PyMuPDF 1.28.2（host） | 元数据 `Dual Licensed - GNU AFFERO GPL 3.0 or Artifex Commercial License` | **只作本机第二读者**；AGPL 不进任何分发物/依赖 |
| pdfbox-android 2.0.27.0 | Apache-2.0；repo 1197 star；最新 release 2023-01-02，最后推送 2024-03-18；README 要求先 `PDFBoxResourceLoader.init(context)`；README 称“Requires API 19 or greater for full functionality” | **可选**（仅当需要“APK 内独立读者”）；代价：引入 BouncyCastle 三件套、测试 APK 变大、维护已停 → 不设为默认 |
| `androidx.pdf:pdf-viewer` 1.0.0-beta01 | Apache-2.0（androidx 仓库）；`pdf/pdf-viewer/build.gradle` minSdk 28；模块为 viewer UI + sandboxed document service | **拒绝**作独立读者：它仍走平台渲染路径，且是 beta UI 组件 |
| iText / iText7 | AGPL | **拒绝**（许可证） |
| qpdf（host） | 本机未安装 | 未采用；若需结构级校验可另装（本轮未验证） |

---

## 10. 本轮执行与未执行

### 10.1 实跑命令（可复跑）

| 目的 | 命令 | 结果 |
|---|---|---|
| 设备与 API | `adb devices -l`；`adb -s <s> shell getprop ro.build.version.sdk` | 4 台在线；34/34/34/23 |
| SDK 扩展 | `adb -s emulator-5562 shell getprop ro.build.version.extensions.s` | 空 → `PdfRendererPreV` 不可用 |
| seccomp | `adb -s <s> shell 'cat /proc/<pid>/status \| grep -i seccomp'` | API 23 `Seccomp: 0`；API 34 `Seccomp: 2` / `Seccomp_filters: 1` |
| 目录权限 | `adb -s emulator-5562 shell 'run-as com.tingyun.smartmistakebook.core.data.test sh -c "ls -ld . cache files"'` | `. 0700`、`cache 2771`、`files 0771`；文件 `-rw-------` |
| run-as 可用性 | `adb -s <s> shell 'run-as <pkg> ls files'` | API 23 与 API 34 均 rc=0（debug 包） |
| 外部目录 | `adb -s emulator-5596 shell 'ls -d /sdcard/Android/data'` | No such file or directory（API 23）；API 34 存在 |
| host 工具 | `python -c "import pypdf, pymupdf"`；`where qpdf` | pypdf 6.16.2 / PyMuPDF 1.28.2；qpdf 未装 |
| 官方契约 | `curl -sSL https://developer.android.google.cn/reference/android/graphics/pdf/{PdfRenderer,PdfRenderer.Page,PdfDocument,PdfDocument.PageInfo,PdfRendererPreV,LoadParams}` | 200；正文已引 |
| 模块化 | `…/about/versions/15/features`、`…/about/versions/oreo/android-8.0-changes` | 200；引文见 §2.5、§6.1 |
| AOSP PFD | `raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/os/ParcelFileDescriptor.java` | 200；`:845-847` 幂等 close |
| 样例 | `raw.githubusercontent.com/googlearchive/android-PdfRendererBasic/master/Application/src/main/java/.../PdfRendererBasicFragment.java` | 200；`:154-166`、`:174-183` |
| 库元数据 | `gh api repos/{TomRoush/PdfBox-Android, androidx/androidx, py-pdf/pypdf, googlearchive/android-PdfRendererBasic}` | 见 §9 |

### 10.2 UNVERIFIED（不得当通过）

1. **Skia（`PdfDocument`）产物的 Info/xref 形态**：是否有 Info、是经典表还是 xref 流——无设备运行、无 Skia 后端可复现。影响注入器分支覆盖。
2. **Android PDFium 对追加增量段的接受度（KD-75 U3）**：无设备往返实跑；`known-defects.md:2900` E-40-02a 仍 BLOCKED。
3. **`PdfRendererPreV` / `Page.getTextContents` 的真实行为**：本机最高 API 34 且无 S Ext 13，类不可用。
4. **畸形 PDF 在 API 23/26 上的崩溃率**：只有社区单一来源（SO 64078319），未在设备上复现。
5. **`noCompress` 后 `openFd` 直读**：未构建验证（本轮零 Gradle）。
6. **`adb exec-out run-as … cat` 端到端拉取 PDF**：`run-as` rc=0 已验证，但未拉过真实 PDF 产物。
7. 任何 Gradle 编译/单测门/强门/设备绿：本项零 Gradle、零 `am instrument`。

---

## 11. 来源

**官方（一手，`developer.android.google.cn` 镜像）**

- `PdfRenderer`：https://developer.android.google.cn/reference/android/graphics/pdf/PdfRenderer
- `PdfRenderer.Page`：https://developer.android.google.cn/reference/android/graphics/pdf/PdfRenderer.Page
- `PdfRendererPreV`：https://developer.android.google.cn/reference/android/graphics/pdf/PdfRendererPreV
- `PdfDocument`：https://developer.android.google.cn/reference/android/graphics/pdf/PdfDocument
- `PdfDocument.PageInfo`：https://developer.android.google.cn/reference/android/graphics/pdf/PdfDocument.PageInfo
- `LoadParams`：https://developer.android.google.cn/reference/android/graphics/pdf/LoadParams
- `android.graphics.pdf` 包：https://developer.android.google.cn/reference/android/graphics/pdf/package-summary
- `AssetManager.openFd`：https://developer.android.google.cn/reference/android/content/res/AssetManager
- Android 15 特性（PDF 模块化 / `PdfRendererPreV`）：https://developer.android.google.cn/about/versions/15/features
- Android 8.0 变更（seccomp 对所有 app）：https://developer.android.google.cn/about/versions/oreo/android-8.0-changes
- 应用专属存储（内部存储私有 / cache 可被回收）：https://developer.android.google.cn/training/data-storage/app-specific
- Instrumented tests（`Instrumentation` 提供 app context）：https://developer.android.google.cn/training/testing/instrumented-tests

**AOSP / 样例**

- `ParcelFileDescriptor.java`（`closeWithStatus` 幂等，`:845-847`）：https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/os/ParcelFileDescriptor.java
- 官方样例 `googlearchive/android-PdfRendererBasic`（Apache-2.0，archived，最后推送 2019-09-10）：https://github.com/googlearchive/android-PdfRendererBasic — `PdfRendererBasicFragment.java:154-166`（复制压缩资产 → 文件 → 只读 fd）、`:174-183`（page → renderer → descriptor 关闭顺序）

**库/许可证**

- pypdf（BSD-3，6.16.2）：https://github.com/py-pdf/pypdf
- PyMuPDF（AGPL-3.0/商业双许可，1.28.2）：PyPI 元数据（本机 `importlib.metadata`）
- PdfBox-Android（Apache-2.0，v2.0.27.0 / 2023-01-02，最后推送 2024-03-18）：https://github.com/TomRoush/PdfBox-Android
- androidx.pdf（Apache-2.0，`pdf-viewer` 1.0.0-beta01，minSdk 28）：https://github.com/androidx/androidx/tree/androidx-main/pdf

**仓内（本会话实读）**

- `docs/known-defects.md:2879-2902`（KD-75 症状/判据/Progress/回链）
- `docs/research/2026-11-02-pdf-incremental-aigc.md`（R6：增量段规范、失败码分类、测试矩阵）
- `docs/research/2026-11-02-batch-40-record.md:281-340`（§8 #5/#21、§9.1 #1）
- `docs/research/evidence/2026-10-29-batch-34/step05-parity-painter-pdf.md:15-21,28-31,36-44,53-63`（PDF 16 例设备绿与命令）
- 源码：`core/data/.../pdf/PdfAigcReader.kt`、`PdfAigcErrorCode.kt`、`PdfAigcContentPolicy.kt`、`core/data/.../capture/PdfBatchImportPageRenderer.kt`、`core/data/src/androidTest/.../visual/VisualDrawOpsPdfPainterInstrumentedTest.kt`、`core/data/src/androidTest/.../capture/BatchImportSourceRangeInstrumentedTest.kt`、`app/src/androidTest/.../SharedCaptureTestFiles.kt`、`core/export/src/androidTest/.../MistakePdfExporterInstrumentedTest.kt`、`core/data/build.gradle.kts`、`core/export/build.gradle.kts`、`app/build.gradle.kts`

**社区（单一来源，待验证）**

- StackOverflow 64078319（旧 Android 上 PDFium 崩溃，Android 5–8）：https://stackoverflow.com/questions/64078319/ — 仅用于“风险存在”，不作判据。
