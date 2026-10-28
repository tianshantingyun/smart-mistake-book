# 批次 30 设备判据分册：KD-79 PDF 写成功判定（T5）

> **本文件是判据，不是证据**：本轨（T5）零 Gradle、零编译、零设备、零 git 写；下述所有"应绿/应红"
> 均未被本轨执行（UNVERIFIED）。设备执行归设备轨，KD-79 的 open/close 由主会话按设备证据裁定——
> **本文件不宣布关闭**（沿用 `docs/known-defects.md:2921` 的 Reopen/关闭条件）。
>
> 落点边界：T1 的批次 30 总判据清单应**引用本文件**而非复制本表；T12 负责把 KD-79 台账文字与
> 计数对齐（本轨不改 `docs/known-defects.md`、不改 `docs/current/*`）。本文件独立成册的理由是
> 并行写冲突：批次 30 的 `2026-10-28-batch-29-device-criteria.md` 正被 T12 计数回改、T1 另产总清单。

---

## 1. KD-79 关闭判据原文与逐条映射

台账原文（`docs/known-defects.md:2913` 标题、`:2921`）：

> `PDF 写盘失败 fail-open：PdfDocument.writeTo 在 API 34 吞掉 OutputStream 异常，PAINTER_PDF_IO 不可达，调用方把截断 PDF 当成功`
>
> **Reopen/关闭条件**：设备轨在 API 34 上复跑 PDF 13 例（`pdfWriteFailureIsCoded` 绿 = `PAINTER_PDF_IO`
> 可达）且 logcat 仍出现 native 打印的模拟 IOException（证明确实走了 fail-open 路径而修复把它转成
> 有码异常）；若未来该 painter 进入生产装配，另需"部分写后进程被杀/流关闭"的恢复语义裁定。

| # | 关闭条件 | 批次 30 的载体（现树） | 通过判据 | 本轨状态 |
|---|---|---|---|---|
| C1 | API 34 复跑 PDF 套件，`pdfWriteFailureIsCoded` 绿（`PAINTER_PDF_IO` 可达） | `VisualDrawOpsPdfPainterInstrumentedTest`（**现树 16 例**，原 13 例全部保留） | 摘要行 **`OK (16 tests)`**、0 failures/0 errors/0 skipped | **未运行**（UNVERIFIED） |
| C2 | logcat 仍出现 native 打印的模拟 IOException（证明走"native 吞异常"路径） | 两个失败夹具：`FailingOutputStream` → `simulated write failure`；`MidStreamFailingOutputStream` → `simulated mid-stream write failure` | logcat 中出现对应字符串（native `ExceptionDescribe()` 打到 `System.err`），且该轮 `OK (16 tests)` | **未运行**（UNVERIFIED） |
| C3 | （批次 30 新增的必要条件，**不替代** C1/C2）文件目标完整判据：长度/尾标回读 + 页数回读 + 发布 | `pdfFileWriteRoundTripsThroughReadback`（P-PDF-01） | 目标存在且非空、尾窗含 `%%EOF`、`PdfRenderer` 页数 1、`48×32` pt、无 `.pending-` 残留 | **未运行**（UNVERIFIED） |
| C4 | （批次 30 新增的负向）只读目标折成有码失败 | `pdfReadOnlyTargetFailsClosedBeforeAnyWrite`（N-PDF-06 落地形态） | `PAINTER_PDF_IO`、消息含 `not writable`、目标不存在 | **未运行**（UNVERIFIED） |
| C5 | （批次 30 新增的负向）流中途抛错保留**首个**失败文本 | `pdfWriteFailureMidStreamKeepsTheFirstFailureText`（N-PDF-02） | `PAINTER_PDF_IO`、消息含 `simulated mid-stream write failure`、失败前已接受字节 > 0 | **未运行**（UNVERIFIED） |

**计数口径**：C1 原文写"13 例"，是批次 29 的时点值。现树 16 = 原 13 + 本轨新增 3（未删除、未改写任何
既有断言、未放宽容差），故关闭判据的套件计数**重基线为 `OK (16 tests)`**——这是计数漂移，不是放宽；
若设备仍报 `OK (13 tests)`，说明装了旧 APK（见 §4 dex 符号门），不得判绿。

---

## 2. 设备套件 16 例逐例判据

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterInstrumentedTest.kt`。
前 13 例的判据与批次 29 T1 一致（`docs/research/2026-10-28-batch-29-device-criteria.md` §1/§3 同源口径：
页尺寸 ceil、DPI 归一、背景覆盖 ceil 余条、clip 根空间、label 投影位置、alpha 量化、hitRegions 不绘制、
栅格确定性、页几何两例、`pdfWriteFailureIsCoded`、回放码族穿透），**本轨未改其夹具与断言**。

| # | 用例 | 设备必须成立 | 来源 |
|---|---|---|---|
| 1–8 | `pdfPageSizeUsesCeilOfViewBoxTimesPtPerDu` / `pdfFillMatchesTheScreenOnTheNormalizedPixelGrid` / `pdfAndScreenShareTheSameGridAtAnyPageScale` / `pdfBackgroundCoversTheRoundedUpPage` / `pdfNestedGroupClipCutsInRootSpace` / `pdfGroupLabelLeavesInkAtTheProjectedRootSpacePosition` / `pdfHalfAlphaTracksTheScreenQuantizedAlpha` / `pdfDoesNotPaintHitRegions` | 各用例原断言全部成立（回归，不退化） | 批次 29 现树 |
| 9 | `pdfRasterIsDeterministicAcrossTwoIndependentBuilds` | 两次独立构建的 PDF 回读栅格逐位相等；同一字节回读两遍相等 | 批次 29 现树 |
| 10–11 | `pdfUnusablePtPerDuFailsClosed` / `pdfOversizePageFailsClosed` | `PAINTER_PDF_PAGE_BOUNDS` | 批次 29 现树 |
| 12 | `pdfWriteFailureIsCoded` | 首写即抛 → `PAINTER_PDF_IO`（D1；logcat 有 native 打印） | 批次 29 现树（KD-79 原判据） |
| 13 | `pdfReplayFailuresKeepTheScreenPainterCodeFamily` | `PAINTER_STROKE_WITHOUT_FILL` / `PAINTER_DASH_ODD` 且消息含 `ops[0]` | 批次 29 现树 |
| **14** | `pdfWriteFailureMidStreamKeepsTheFirstFailureText` | `PAINTER_PDF_IO`；消息含 `simulated mid-stream write failure`；夹具 `acceptedBytes > 0`（证明失败发生在首块之后）；**首失败不被后续重试覆盖** | **批次 30 T5 新增（N-PDF-02）** |
| **15** | `pdfFileWriteRoundTripsThroughReadback` | `target` 是文件且非空；尾窗含 `%%EOF`；`PdfRenderer` 页数 1、页尺寸 `48×32` pt；cacheDir 无 `*.pending-<uuid>` 残留 | **批次 30 T5 新增（P-PDF-01 / D3）** |
| **16** | `pdfReadOnlyTargetFailsClosedBeforeAnyWrite` | 目录 `chmod 0555` 成功；`writeFile` 抛 `PAINTER_PDF_IO` 且消息含 `not writable`；目标文件不存在（无半成品发布） | **批次 30 T5 新增（N-PDF-06）** |

**第 14 例的"中途"证明方式**：`MidStreamFailingOutputStream(chunksBeforeFailure = 1)` 先放行一块、其后
每块抛 `IOException`；用例同时断言 `acceptedBytes > 0`。若失败发生在首块，`acceptedBytes` 会是 0，
本断言必红——这是"中点抛"与"首写抛"的区分门，不是形式断言。

---

## 3. JVM 门（纯层判定语义，不算设备证据）

文件：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/PdfWriteIntegrityTest.kt`
（**16 例**，`grep -c "@Test"` 实测；本轨未运行）。被测文件 `PdfWriteIntegrity.kt` **零 android 导入**，
故判定语义可在无 Robolectric 的 JVM 门里钉死。

| 组 | 用例 | 钉住的语义 / 消灭的失败 |
|---|---|---|
| D1 | `theTrackingStreamRecordsTheFirstFailureAndRethrowsEveryAttempt` | 首失败不随后续重试覆盖；失败块不计入接受字节；每一块都重抛（native 语义不变） |
| D1 | `anErrorTypedWriteFailureIsRecordedToo` | `Throwable` 捕获面（N-PDF-09：原 `catch (Exception)` 漏 `Error`，native 的 `ExceptionCheck` 对任何 `Throwable` 都清） |
| D1 | `theFirstFailureTextFallsBackToTheClassNameWhenThereIsNoMessage` | 无 message 的失败仍有可归因文本 |
| D1 | `aDiskFullStyleFailureKeepsItsErrnoText` | 计划 §1.6 的"磁盘满模拟"（N-PDF-05）：`IOException` 的 errno 文本原样进有码消息（真 ENOSPC 不可行，见 §6-3） |
| D2 | `aStreamThatProducedNoBytesFailsClosed` | 零字节成功（native 在调用 Java `write` 前失败） |
| D2 | `aPayloadWithoutTheTrailerFailsClosed` | 尾部静默截断（`%%EOF` 未写出） |
| D2 | `aPayloadEndingWithTheTrailerPasses` | 正例：不误伤正常产物 |
| D2 | `theTrailerIsMatchedAcrossChunkBoundaries` | 尾标被 4096 B 分块切开仍可判 |
| D2 | `aTrailerThatHasScrolledOutOfTheTailWindowFailsClosed` | 尾窗有界（最后 1024 B），不拿全文做假阳性 |
| D2 | `aSingleByteTrailerSplitIntoPerByteWritesIsStillMatched` | `write(int)` 同语义（惰性防御，native 不走） |
| D3 字节面 | `aCompleteFilePassesTheByteLevelReadback` | 正例 |
| D3 字节面 | `aFileShorterThanTheAcceptedBytesFailsClosed` | **短写**：接受字节 > 落盘长度 |
| D3 字节面 | `aFileLargerThanTheAcceptedBytesFailsClosed` | 长度不符（双向） |
| D3 字节面 | `aFileWithoutTheTrailerFailsClosedEvenWhenTheLengthMatches` | 长度对但尾标缺失（静默截断后补齐长度的形态） |
| D3 字节面 | `aMissingFileFailsClosed` | 目标未落盘 |
| D3 字节面 | `aZeroAcceptedByteCountFailsClosed` | 零字节 |

---

## 4. 设备轨串行命令（只跑本套件，替换 `$S`）

> 纪律不变：**禁 `connectedAndroidTest`**；只 `assemble*` + `adb install -r -t` + `am instrument`；
> Gradle 环境由设备轨负责（本轨未执行）。

```bash
# A. 构建（core:data 的 androidTest 包 = 本判据载体）
./gradlew --console=plain :core:data:assembleDebugAndroidTest
sha256sum core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk

# B. 安装
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s "$S" install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s "$S" shell getprop ro.build.version.sdk          # 必须 34（KD-79 的条件是 API 34）
$A -s "$S" shell sha256sum "$($A -s "$S" shell pm path com.tingyun.smartmistakebook.core.data.test | tr -d '\r' | sed 's/^package://')"

# C. dex 符号门（防旧包：旧 13 例包不含新增三例的方法名）
unzip -p core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk 'classes*.dex' \
  | grep -a -o -E 'pdfWriteFailureIsCoded|pdfWriteFailureMidStreamKeepsTheFirstFailureText|pdfFileWriteRoundTripsThroughReadback|pdfReadOnlyTargetFailsClosedBeforeAnyWrite' \
  | sort -u
# 通过判据：四个方法名全部出现（缺任一 = 装了旧包，判环境类，不判产品）

# D. 执行（先清 logcat）
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch30/step05-pdf.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch30/step05-pdf-logcat.txt

# E. 判据提取
grep -E "OK \(|Tests run:|Time:" build/device-track-batch30/step05-pdf.log | tail -3   # 期望 OK (16 tests)
grep -c "simulated write failure"            build/device-track-batch30/step05-pdf-logcat.txt  # >0（C2）
grep -c "simulated mid-stream write failure" build/device-track-batch30/step05-pdf-logcat.txt  # >0（C2 / 第 14 例）
grep -cE "FATAL EXCEPTION|NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK" \
  build/device-track-batch30/step05-pdf-logcat.txt                                        # 期望 0
```

**通过判据（全部成立才算本套件 PASS）**：

1. 摘要行 **`OK (16 tests)`**，0 failures / 0 errors / 0 skipped；
2. logcat 同时出现两种模拟失败字符串（证明两次失败都真的走 native 吞异常路径，而不是异常逃逸到
   Java——后者会让修复"看起来"生效但机制不同）；
3. logcat 崩溃签名 0；
4. `pdfFileWriteRoundTripsThroughReadback` 绿（D3 全链：sync + 长度/尾标 + 页数 + rename）；
5. `pdfReadOnlyTargetFailsClosedBeforeAnyWrite` 绿（chmod 生效是夹具前提，见 §5）。

**以下情形不得判绿**：只跑 13 例（旧包）；`Tests run:` 有 Failure/Error/Skipped；把 `INSTRUMENTATION_CODE`
或 shell 退出码当判据（批次 29 已裁定两者都不是判据）；logcat 无 native 打印却报绿。

---

## 5. 夹具/环境失败的归因（不得算产品红）

- **只读目录 fixture**：`File.setWritable(false, false)` 在不支持 chmod 的存储卷上会返回 false 或跳变；
  用例已把该调用写成断言，若红先按"环境/夹具（D/A 类）"归因，再换 `context.cacheDir` 下的目录重试；
  不得把"chmod 不生效"当 `PAINTER_PDF_IO` 未实现。
- **为什么是只读目录而不是只读文件**：`writeFile` 走"同目录临时件 + `renameTo`"，POSIX 下在可写目录
  内 rename 可以替换一个 0444 的目标文件，因此 **0444 文件不是本入口的失败面**；R2 的 N-PDF-06 原文是
  "直接 `FileOutputStream(file)`" 形态（无临时件），本轨的等价失败面是"目标目录不可写"。这是形态差异，
  已在 `writeFile` KDoc 与用例注释写明，**不得**据此认为只读文件被漏测成绿。
- **`%%EOF` 尾标**：判据只主张"尾窗内出现"，不主张精确结尾（R2 §3.2 前提只核了 android-14.0.0_r1）。
  若真机/新版本 SkPDF 改尾标格式导致正例红，先按"夹具/平台漂移（B 类）"取证，**不得**删除尾标断言
  来变绿；正确处置是更新 `PDF_TRAILER_MARKER` 并保留判据强度。
- **页数回读**：`PdfRenderer` 只吃可 seek 的 fd；本用例写的是真文件，不是流。

---

## 6. 已知残留与未验证（不得读成覆盖）

1. **中段静默丢块**（不抛、尾标完整、页数正确）：D1/D2/D3 全不可见（R2 §2.2/§3.5）；只有批次 29 T4 的
   栅格一致性门（**原型门，非产品/发版判据**）可能暴露。本轨**没有**为它写"会绿"的用例。
2. **委托流静默短写**：纯流目标上不可观测（跟踪流只看"交出去的字节是否被正常返回"）。它只在文件
   目标层由"文件长度 == 接受字节"抓到——本轨的短写负向（§3 第 11 例）钉的是这一层，**不是**流层。
3. **真 ENOSPC / 真 fsync 失败**：无法在无 root 设备上稳定制造（R2 §6 N-PDF-05/N-PDF-10）；负向用的是
   `IOException("ENOSPC")` 类与只读目录，不覆盖内核 partial write/errno 路径。
4. **PDFium 对截断 PDF 的修复式加载边界**（R2 N-PDF-07）：未取基线，故本轨**不写**"截断后必抛/必页数不符"
   的设备断言；需先在 API 34 上取一次基线再定断言（禁止按猜测定）。
5. **本轨零执行**：`PdfWriteIntegrityTest`（16 例）与 `VisualDrawOpsPdfPainterInstrumentedTest`（16 例）
   均**未运行**；全部绿轮 UNVERIFIED。KD-79 维持 open，直到设备证据落盘。

---

## 7. 落点与引用（本批 T5 改动清单）

| 文件 | 作用 |
|---|---|
| `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/PdfWriteIntegrity.kt` | 新增纯层：跟踪流（`Throwable` 捕获）、遥测（接受字节 + 1024 B 尾窗）、`pdfWriteFailureReason`（D1∧D2）、`pdfFileFailureReason`（长度+尾标） |
| `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt` | `write()` 接入 D1∧D2；新增 `writeFile()`（D1∧D2∧D3：同目录临时件 → flush+`fd.sync` → 长度/尾标 → `PdfRenderer` 页数 → `renameTo`），失败折 `PAINTER_PDF_IO` |
| `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/PdfWriteIntegrityTest.kt` | 新增 JVM 16 例（判定语义） |
| `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterInstrumentedTest.kt` | 13 → **16** 例（新增第 14/15/16 例，前 13 例未改） |

**为什么不复用 `core:export`**：`MistakePdfExporter.prepare/validatedPageCount/publish` 的
`validatedPageCount`/`publish`/`PreparedPdfStore` 均 private，`core:data` 不依赖 `core:export`
（`core/data/build.gradle.kts` 只有 `core:model/domain/database`），且 `core/export` 是本批禁改热区。
故按同一序列在本包复制（R2 §3.6 + 研究复核高危⑥的保守分支），不新增依赖、不改热区。
