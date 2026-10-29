# E-04 painter 24 例：`VisualDrawOpsPainterInstrumentedTest`（PASS 24/24）

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 二（八套件第 4；判据 §2.4） |
| gate-or-kd | 屏侧 painter 功能门；KD-78 ③/④ 像素与 flags 钉子 |
| verdict | **PASS**：`OK (24 tests)`；5 条关键判据逐例 `STATUS_CODE=0` |
| executed-at | 2026-10-29T06:38:18–06:38:23+0800 |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `34151d4d9571daffc27e6a370172afb6775b7e730742dc3484ffd5fdb2e7b952`（设备侧一致） |
| apk-version | （测试包 versionName 空） |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch31/step04-painter.log`（345 行 / 18214 B）/ `-logcat.txt`（123 行 / 18584 B） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §2.4：目标 `OK (24 tests)`；5 条关键用例必须绿（八位色 alpha 相乘、回放 textPaintFlags 钉死、
奇数 dash 门序、`drawRendered` 与 validatedDocument 逐位相等、坏命令 fail-closed）；套件级
`measure paint flags .* do not match the environment fingerprint` = 0。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step04-painter.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step04-painter-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 24 | 期望 24 |
| failures / errors / skipped | 0 / 0 / 0 | 期望 0 |
| `STATUS_CODE: 0` | 24 | — |
| 装配级恒抛（paint flags 不匹配） | 0 | 期望 0 |
| 9 类签名 | 0（E-11） | 期望 0 |

## 4. 关键输出（有界）

```text
Time: 0.406

OK (24 tests)

INSTRUMENTATION_CODE: -1
```

关键用例逐条（构建前复读的源码行号见判据 §2.4）：

```text
anEightDigitColorAlphaMultipliesWithTheOpAlphaInsteadOfBeingOverwritten                     codes=['1','0']
theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint             codes=['1','0']
aStrokeOnlyPathWithAnOddDashReportsTheSameFirstGateAsTheGenerators                         codes=['1','0']
drawRenderedReplaysTheFrozenCommandSequence                                               codes=['1','0']
drawRenderedFailsClosedOnMissingOrInvalidCommands                                         codes=['1','0']
```

```text
grep -c "measure paint flags .* do not match the environment fingerprint" step04-painter-logcat.txt
0
```

## 5. 失败 / 跳过 / 异常

无。KD-78 ③ 的像素正例（`#ff000080` 中心 `0xFFFF7F7F`）与 ④ 的回放 flags=0 钉子均在本套件内通过；
**逻辑负例**（把常量改回 1283 应红）不在本轨执行面。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary 与 5 条关键用例状态码、装配级计数。
- 扫描：R4 §4.4 → 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS。批次 29/30 的 1283 形态未复现；painter 24 例在本包本设备全绿。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step04-painter.log` / `-logcat.txt`
- 行数/大小：345 行 / 18214 B；logcat 123 行 / 18584 B
- 说明：`build/` 被 `.gitignore:15` 忽略。
