# E-06 一致门（原型）16 + painter 24 + PDF 16

| 字段 | 值 |
|---|---|
| evidence-id | E-06 |
| convention-version | 1 |
| batch | 2026-10-29-batch-33 |
| step | 判据三（回归不回退） |
| gate-or-kd | 一致门（**原型门，不得引用为产品/发版结论**）；painter / PDF |
| verdict | PASS |
| executed-at | 2026-10-29T15:01:26+08:00（parity）/ 15:01:28（painter）/ 15:01:35（PDF） |
| operator | 设备验证轨（子代理） |
| head | `6abd807fa73d9084ef629d54015bd4f725c94784` |
| worktree | dirty（仅记录） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `<repo>/core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `36f79b195e6c43d7841a85da3385887eb092518d1a0aa0f7462a619fb06f7627` |
| apk-version | （空） |
| exit-code | 0（三步） |
| raw-log | `step10-parity.log`（233 行）；`step11-painter.log`（345）；`step12-pdf.log`（233） |
| record-ref | `docs/research/2026-10-29-batch-33-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

证明本批改动（结构门修复、C4 新用例）没有让一致门与绘制/PDF 回归。一致门仍是**原型门**，
本文件只记录"设备上 16/16 通过"，不作产品门或发版结论。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
run() { step="$1"; cls="$2"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
    > "build/device-track-batch33/$step.log" 2>&1
  $A -s "$S" logcat -v threadtime -d > "build/device-track-batch33/$step-logcat.txt"; }

run step10-parity com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest
run step11-painter com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest
run step12-pdf com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest
```

- 退出码：三步均 0。

## 3. 结果摘要

| 套件 | 目标 | 实测 | 判定 |
|---|---:|---|---|
| `VisualDrawOpsParityInstrumentedTest` | 16 | `OK (16 tests)` | PASS（**原型门**） |
| `VisualDrawOpsPainterInstrumentedTest` | 24 | `OK (24 tests)` | PASS |
| `VisualDrawOpsPdfPainterInstrumentedTest` | 16 | `OK (16 tests)` | PASS |
| skipped / ignored | 0 | 0 | PASS |

一致门的负控（`n09…n13`）与 PDF 三个失败/只读目标方法已由 dex 门确认在包内（E-01），
16/24/16 的绿轮不是旧包（D 类）。

## 4. 关键输出（有界）

```text
step10-parity: OK (16 tests)
step11-painter: OK (24 tests)
step12-pdf:    OK (16 tests)
```

## 5. 失败 / 跳过 / 异常

无。跨设备像素比较不涉及（单设备内比较）。

## 6. 脱敏与去噪

- 删除：逐用例重复行。
- 保留：三条 summary。
- 扫描：0 命中。
- 二进制/截图：无。

## 7. 判定

PASS。16/24/16 全绿，均来自最终 data APK `36f79b19…`。**一致门不是产品门**；
不得把本文件 16/16 写成产品/发版结论。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch33/step10-parity*`、`step11-painter*`、`step12-pdf*`
- 生成命令：见 §2
- 行数/大小：233 / 345 / 233 行
