# E-07 PDF 16 例：`VisualDrawOpsPdfPainterInstrumentedTest`（PASS 16/16）

| 字段 | 值 |
|---|---|
| evidence-id | E-07 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 二（八套件第 7；判据 §2.7、§5.4） |
| gate-or-kd | KD-79 79-P1/P2/P3/N2（`PAINTER_PDF_IO` 可达 + 写盘完整性） |
| verdict | **PASS**：`OK (16 tests)`；5 条关键用例绿；logcat native `java.io.IOException: simulated write failure` = 230 > 0 |
| executed-at | 2026-10-29T06:39:49–06:39:54+0800 |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `34151d4d9571daffc27e6a370172afb6775b7e730742dc3484ffd5fdb2e7b952`（设备侧一致） |
| apk-version | （测试包 versionName 空） |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch31/step07-pdf.log`（233 行 / 12071 B）/ `-logcat.txt`（20576 行 / 2712169 B） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §2.7：`OK (16 tests)`；5 条关键用例（`pdfWriteFailureIsCoded`、mid-stream 首失败文本、
写盘回读、只读目标 fail-closed、回放错误码族）必须绿；套件级
`java.io.IOException: simulated write failure` **> 0**，证明 native 吞异常路径确实被走到。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step07-pdf.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step07-pdf-logcat.txt

grep -c "java.io.IOException: simulated write failure" build/device-track-batch31/step07-pdf-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 16 | 期望 16（批次 29 的 13 例已由 T5 扩到 16） |
| failures / errors / skipped | 0 / 0 / 0 | 期望 0 |
| `STATUS_CODE: 0` | 16 | — |
| native 模拟写失败 | **230** | 期望 > 0 |
| 9 类签名 | 0（E-11） | 期望 0 |

## 4. 关键输出（有界）

```text
Time: 5.309

OK (16 tests)

INSTRUMENTATION_CODE: -1
```

```text
pdfWriteFailureIsCoded                                codes=['1','0']
pdfWriteFailureMidStreamKeepsTheFirstFailureText      codes=['1','0']
pdfFileWriteRoundTripsThroughReadback                 codes=['1','0']
pdfReadOnlyTargetFailsClosedBeforeAnyWrite            codes=['1','0']
pdfReplayFailuresKeepTheScreenPainterCodeFamily       codes=['1','0']
```

```text
grep -c "java.io.IOException: simulated write failure" step07-pdf-logcat.txt
230
```

## 5. 失败 / 跳过 / 异常

无。**仍无设备用例**（判据 §2.7 明列，不因 16 绿而改变）：ENOSPC、静默短写、页数回读不符、
`renameTo` 失败 → 这四项 KD-79 残留仍是 **UNVERIFIED**，16 绿不得写成 D3 全路径完成。

## 6. 脱敏与去噪

- 删除：logcat 全量 20576 行不入仓，只留计数（本文件）。
- 保留：summary、5 例状态码、native 异常计数（含 0 的项另见 E-11）。
- 扫描：R4 §4.4 → 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS：`PAINTER_PDF_IO` 可达（79-P1）、mid-stream 首失败文本保留（79-N2）、写盘回读（79-P3）、
只读目标 fail-closed 均成立；native 230 条异常证明修复把 fail-open 转成了有码异常。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step07-pdf.log` / `-logcat.txt`
- 行数/大小：233 行 / 12071 B；logcat 20576 行 / 2712169 B
- 说明：`build/` 被 `.gitignore:15` 忽略。
