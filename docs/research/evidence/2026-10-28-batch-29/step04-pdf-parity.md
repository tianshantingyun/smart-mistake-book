# E-04 PDF 回放 13 例 + 屏/PDF 一致性原型 10 例（PDF FAIL 1/13；一致性 PASS）

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-28-batch-29 |
| step | 四（T3 PDF 回放与 T4 一致性门原型） |
| gate-or-kd | T3 `VisualDrawOpsPdfPainter` 设备判据；T4 原型门（非产品门/非发版门） |
| verdict | **PDF FAIL**：`Tests run: 13, Failures: 1`（`pdfWriteFailureIsCoded`，`PdfDocument.writeTo` 吞 IOException → `PAINTER_PDF_IO` 不可达）；**一致性 PASS**：`OK (10 tests)` |
| executed-at | 2026-10-28T22:36:00+08:00 前后（设备 UTC 14:36） |
| operator | 设备验证轨（subagent，批次 29） |
| head | `3d9372e5` |
| worktree | dirty（T3/T4 新文件为 ??；T5 在飞） |
| device-serial | emulator-5562（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | core:data androidTest `4d152b5a…fa72cd81`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 两条命令宿主 `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch29/step04-pdf.log`/`-logcat.txt`、`step04-parity.log`/`-logcat.txt` |
| record-ref | 批次 29 记录（后写） |
| sanitized | 是 |

## 1. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
# T3 PDF 回放 13 例
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch29/step04-pdf.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch29/step04-pdf-logcat.txt
# T4 一致性原型 10 例
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch29/step04-parity.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch29/step04-parity-logcat.txt
```

## 2. T3 PDF 结果

```text
Time: 1.167
There was 1 failure:
1) pdfWriteFailureIsCoded(com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest)
java.lang.AssertionError: expected VisualDrawOpsPaintingException
	at ...VisualDrawOpsPdfPainterInstrumentedTest.paintingFailureOf(VisualDrawOpsPdfPainterInstrumentedTest.kt:217)
	at ...VisualDrawOpsPdfPainterInstrumentedTest.pdfWriteFailureIsCoded(VisualDrawOpsPdfPainterInstrumentedTest.kt:446)
Tests run: 13,  Failures: 1
INSTRUMENTATION_CODE: -1
```

通过 12/13：页尺寸 ceil、DPI 归一、原点左上、屏/PDF 同网格、alpha 0.5 跟踪、背景覆盖整页、
嵌套 clip、label 位置、hitRegions 不绘制、栅格确定性、ptPerDu/超页 fail-closed、
共享 painter 码族穿透（stroke-only/奇数 dash/坏 dash）全部绿。

### 2.1 根因 R2（C 类：产品缺陷；未放宽断言）

`pdfWriteFailureIsCoded` 让 `PdfDocument.writeTo` 写一个必抛 `IOException` 的 `OutputStream`，
期望 `PAINTER_PDF_IO`；实际 `write()` 正常返回，测试在 `paintingFailureOf` 里自抛
`AssertionError`（没有产品异常可捕获）。

logcat 给出了**实际发生但被吞掉**的异常（同一条 230 次，每次写 chunk 一次）：

```text
10-28 14:36:34.988  4936  4958 W System.err: java.io.IOException: simulated write failure
10-28 14:36:34.989  4936  4958 W System.err: 	at android.graphics.pdf.PdfDocument.nativeWriteTo(Native Method)
10-28 14:36:34.989  4936  4958 W System.err: 	at android.graphics.pdf.PdfDocument.writeTo(PdfDocument.java:175)
10-28 14:36:34.989  4936  4958 W System.err: 	at com.tingyun...VisualDrawOpsPdfPainterInstrumentedTest.kt:447
```

- 权威源：AOSP `libs/hwui/jni/CreateJavaOutputStreamAdaptor.cpp`（android-14.0.0_r1）
  `SkJavaOutputStream::write` 在 Java `OutputStream.write` 抛异常时执行
  `ExceptionCheck()` → `ExceptionDescribe()` → `ExceptionClear()` → `return false`。
  即异常被打印到 stderr 后**清除**，不会传回 `PdfDocument.writeTo` 的 Java 调用方。
- 后果：`VisualDrawOpsPdfPainter.kt:88-95` 的 `catch (io: IOException)` **不可达**；
  `PAINTER_PDF_IO` 永远发不出。写盘失败时 PDF 被**静默截断且调用方认为成功**——fail-open。
- 归因类别：**C（产品缺陷）**，非环境（同一设备上 12 例 PDF 用例绿，异常路径确定）。
- 修复建议（归修复环，本轨未改）：
  1. 用"记录失败标志的委托 OutputStream"包住调用方流：`write()` 在 rethrow 前置位 flag；
     `pdf.writeTo(wrapper)` 正常返回后检查 flag，置位则抛 `PAINTER_PDF_IO`；
  2. 或先 `writeTo(ByteArrayOutputStream)`（内存流不会失败），再由本类把字节写到 `out`，
     自写路径的 `IOException` 折 `PAINTER_PDF_IO`；
  3. 修复后同命令复跑，期望 `OK (13 tests)`；建议补"部分写后截断"负向证据。
- 同时建议修正 `docs/research/2026-10-28-pdf-replay-differences.md:294/306` 的
  "IOException 会逃出"结论，避免后续再按错误前提设计。

## 3. T4 一致性原型结果

```text
Time: 0.535
OK (10 tests)
INSTRUMENTATION_CODE: -1
```

10 例：`thePrototypeGateJudgesEveryPositiveFixture`（P01–P08 聚合）、
`backgroundNullIsObservedButNeverJudged`（P-BGNULL 只观察）、
`parityMetricsUseUnionForIouAndStayInsideErosionBounds`（度量自证）、
N01–N07（单像素位移/单通道偏移/丢 clip/label 位移/字形替换/alpha 幅度/丢 op 全部必须被抓到）。

- 判定：**PASS（原型门）**。KDoc 与运行期 banner 声明 `scope=PROTOTYPE`、非产品门、非 CI 门、
  非发版门；本证据**不得**被引用为产品/发版一致性结论。
- 该套件以 `ByteArrayOutputStream` 承接 PDF，不经过必失败流，故不受 R2 影响。

## 4. 判定

- T3 PDF 回放：**FAIL 1/13**（R2 产品缺陷；`PAINTER_PDF_IO` 不可达）。
- T4 一致性：**PASS 10/10**（原型门；阈值为原型容差）。
- 9 类签名 0（见 step06）；**未**改任何测试、未放宽容差、未删断言。
