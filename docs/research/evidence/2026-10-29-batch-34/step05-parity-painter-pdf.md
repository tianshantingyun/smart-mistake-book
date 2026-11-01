# E-05 一致门 16 / painter 24 / PDF 16

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-29-batch-34 |
| step | 四（parity / painter / PDF 不回退） |
| gate-or-kd | 一致门（**原型门，非产品门**）/ KD-78 painter / PDF 写盘 |
| verdict | PASS |
| executed-at | 2026-10-29T19:00:52+08:00 → 19:01:13+08:00 |
| operator | 设备验证轨（子代理） |
| head | `db792fa873f4bc150f415b67d1c2e3b1bab6c995` |
| worktree | 721 条（241 `M` / 480 `??`） |
| device-serial | `emulator-5562` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `53e69f485a4bb4fe3d5c7bdff53ebbfb50008011376df1cbc133e4a946397caf` |
| exit-code | 0（三条 `am instrument`） |
| raw-log | `build/device-track-batch34/step11-parity.log`、`step12-painter.log`、`step13-pdf.log`（本机，不入仓） |
| record-ref | `docs/research/2026-10-29-batch-34-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

- parity 16：`VisualDrawOpsParityInstrumentedTest` = `OK (16 tests)`。这是**原型一致门**，
  不得写成产品门或发版结论。
- painter 24：`VisualDrawOpsPainterInstrumentedTest` = `OK (24 tests)`（现树 24 例，含 T5/复核轮新增）。
- PDF 16：`VisualDrawOpsPdfPainterInstrumentedTest` = `OK (16 tests)`。

## 2. 命令（可复跑）

```bash
cd "D:/smart mistake book"; export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562; D=build/device-track-batch34
run() { step="$1"; cls="$2"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner > "$D/$step.log" 2>&1
  $A -s "$S" logcat -v threadtime -d > "$D/$step-logcat.txt" 2>&1; }

run step11-parity com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest
run step12-painter com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest
run step13-pdf com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest
```

## 3. 结果摘要

| 套件 | 构建前 `@Test` | 实际 summary | Time | failures/errors/skipped |
|---|---:|---|---:|---:|
| 一致门 | 16 | `OK (16 tests)` | 2.051 | 0 / 0 / 0 |
| painter | 24 | `OK (24 tests)` | 0.605 | 0 / 0 / 0 |
| PDF | 16 | `OK (16 tests)` | 7.719 | 0 / 0 / 0 |

设备报告数逐项等于构建前源计数（16 / 24 / 16）。

## 4. 关键输出（有界）

```text
Time: 2.051
OK (16 tests)

Time: 0.605
OK (24 tests)

Time: 7.719
OK (16 tests)
```

painter 的 9 类崩溃签名与像素断言均在设备上执行（该套件全绿，未出现无码崩溃）；
本步 logcat 12 类签名 = 0（见 E-06 §3）。

## 5. 失败 / 跳过 / 异常

无。三条套件均 0 失败 / 0 错误 / 0 跳过。

## 6. 脱敏与去噪

- 删除：逐用例进度点；PDF logcat 的全量正文（20,066 行留在 `build/`，本文件只留计数）。
- 保留：Time、`OK (N tests)`、计数对照、签名计数。
- 扫描：`00-index.md` §6 → 0 命中。

## 7. 判定

**PASS**。parity 16 / painter 24 / PDF 16 在重建后的 data 测试包上全部绿；一致门仍按原型门口径记录，
不据此作产品门结论。
