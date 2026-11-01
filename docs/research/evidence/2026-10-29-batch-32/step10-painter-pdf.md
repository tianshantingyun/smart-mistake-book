# E-10 回归：painter 24 / PDF 16

| 字段 | 值 |
|---|---|
| evidence-id | E-10 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 四（防 schema 71 / 依赖变化引入回退） |
| gate-or-kd | KD-78 ③/④；KD-79 |
| verdict | PASS（24/24 + 16/16） |
| executed-at | 2026-10-29T11:28:57（painter）/ 11:29:04（PDF）+08:00 |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `de27b081942695474091500d0d7f4a53e8b70e5b3106f1c152f3e1ae2325f15c` |
| apk-version | （测试包空） |
| exit-code | 两次宿主 `EXIT=0`（非判据） |
| raw-log | `step10-painter.log`（345 行）/ `step11-pdf.log`（233 行）+ 两份 logcat |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

- painter `VisualDrawOpsPainterInstrumentedTest`：24/24（构建前复读 24），屏侧正确性归属本套件。
- PDF `VisualDrawOpsPdfPainterInstrumentedTest`：16/16（构建前复读 16）。
- PDF 正向路径必须被走到：logcat `java.io.IOException: simulated write failure` > 0。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
for pair in \
  "com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest|step10-painter" \
  "com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest|step11-pdf"; do
  cls=${pair%%|*}; name=${pair#*|}
  $A -s $S logcat -c
  $A -s $S shell am instrument -w -r -e class "$cls" \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
    | tee "build/device-track-batch32/$name.log"
  $A -s $S logcat -v threadtime -d > "build/device-track-batch32/$name-logcat.txt"
done
```

## 3. 结果摘要

| 套件 | tests | failures | errors | skipped | `STATUS_CODE: 0` |
|---|---|---|---|---|---|
| painter | 24 | 0 | 0 | 0 | 24 |
| PDF | 16 | 0 | 0 | 0 | 16 |

| 专项 | 值 | 判据 |
|---|---|---|
| `java.io.IOException: simulated write failure`（PDF logcat） | 230 | > 0（fail-open 路径被走到） |
| 装配级 paint flags 恒抛（painter / flags / isolation） | 0 / 0 / 0 | 0 |
| logcat 9 类签名 | 0 / 0 | 见 E-12 |

## 4. 关键输出（有界）

```text
Time: 0.472
OK (24 tests)

Time: 5.746
OK (16 tests)
```

## 5. 失败 / 跳过 / 异常

无。

## 6. 脱敏与去噪

- 删除：无（PDF logcat 20,576 行全量留 `build/`）。
- 保留：summary、逐用例状态码、专项计数。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

PASS：painter 24/24、PDF 16/16，无回退；PDF native 写失败路径确有 230 次命中。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step10-painter.log`（345 行 / 18,214 B）、`step11-pdf.log`（233 行 / 12,071 B）；logcat 126 行 / 20576 行。
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
