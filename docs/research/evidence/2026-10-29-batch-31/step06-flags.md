# E-06 flags 跨侧 2 例：`VisualMeasureReplayPaintFlagsInstrumentedTest`（PASS 2/2）

| 字段 | 值 |
|---|---|
| evidence-id | E-06 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 二（八套件第 6；判据 §2.6） |
| gate-or-kd | KD-78 ④-P2/N1/N2（flags=0 同源 + 坏指纹 fail-closed） |
| verdict | **PASS**：`OK (2 tests)`；负例（坏指纹 `paintFlags=1` 被拒）绿 |
| executed-at | 2026-10-29T06:39:36–06:39:41+0800 |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` |
| apk-sha256 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19`（设备侧一致） |
| apk-version | （测试包 versionName 空） |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch31/step06-flags.log`（37 行 / 1734 B）/ `-logcat.txt`（619 行 / 102646 B） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §2.6：`OK (2 tests)`；
第 1 例：度量 Paint flags=0、`environment.paintFlags=0`、指纹以 `|paintFlags=0` 结尾、`Paint().flags != 0`；
第 2 例（负例）：坏指纹 `paintFlags=1` 被拒且消息含 `paintFlags=1`——它绿 = 坏指纹被拒，
它红 = KD-78 ④ 关闭不成立。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step06-flags.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step06-flags-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 2 | 期望 2（**这是正常目标，不是旧包信号**） |
| failures / errors / skipped | 0 / 0 / 0 | 期望 0 |
| `STATUS_CODE: 0` | 2 | — |
| paint flags 不匹配 | 0 | 期望 0 |
| 9 类签名 | 0（E-11） | 期望 0 |

## 4. 关键输出（有界）

```text
Time: 0.021

OK (2 tests)

INSTRUMENTATION_CODE: -1
```

```text
theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags      codes=['1','0']
aFingerprintThatDisagreesWithTheActualPaintFailsClosed                codes=['1','0']
```

同轮隔离 logcat 的指纹行（`PARITY_ENV` 之外的 flags 侧证据）：

```text
grep -c "measure paint flags .* do not match the environment fingerprint" \
  step05-isolation-logcat.txt step06-flags-logcat.txt step04-painter-logcat.txt
0 / 0 / 0
```

## 5. 失败 / 跳过 / 异常

无。负例通过 = 坏指纹 `paintFlags=1` 确实被 fail-closed 拒绝，未出现"坏指纹被放过"。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary、两例状态码、装配级计数。
- 扫描：R4 §4.4 → 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS。KD-78 ④ 的跨侧 flags 面在本包本设备成立；KD-78 ④ 的合并裁决仍以 E-04/E-05/E-07/E-11 一并成立为准。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step06-flags.log` / `-logcat.txt`
- 行数/大小：37 行 / 1734 B；logcat 619 行 / 102646 B
- 说明：`build/` 被 `.gitignore:15` 忽略。
