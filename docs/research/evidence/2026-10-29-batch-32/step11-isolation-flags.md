# E-11 隔离 13 / flags 2 + worker 生命周期

| 字段 | 值 |
|---|---|
| evidence-id | E-11 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 四 / 五（判据 §7.6 与批次 31 E-10 口径） |
| gate-or-kd | KD-78 ④-P2/P3/P4 |
| verdict | PASS（13/13 + 2/2；17 spawn = 17 ready，全部退出，无残留） |
| executed-at | 2026-10-29T11:30:09（isolation）/ 11:30:12（flags）+08:00 |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` |
| apk-sha256 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| apk-version | （测试包空） |
| exit-code | 两次宿主 `EXIT=0`（非判据） |
| raw-log | `step12-isolation.log`（191 行）/ `step13-flags.log`（37 行）+ 两份 logcat |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

- 隔离 `VisualJsIsolationInstrumentedTest` 13/13；worker `spawn` 与 `ready` 1:1；无残留进程；`warm-up failed` = 0。
- flags `VisualMeasureReplayPaintFlagsInstrumentedTest` 2/2；装配级 paint flags 恒抛 = 0。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
for pair in \
  "com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest|step12-isolation" \
  "com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest|step13-flags"; do
  cls=${pair%%|*}; name=${pair#*|}
  $A -s $S logcat -c
  $A -s $S shell am instrument -w -r -e class "$cls" \
    com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
    | tee "build/device-track-batch32/$name.log"
  $A -s $S logcat -v threadtime -d > "build/device-track-batch32/$name-logcat.txt"
done

# worker 生命周期（isolation logcat）
grep -c 'Start proc .*visual_js.*VisualJsService' build/device-track-batch32/step12-isolation-logcat.txt   # 17
grep -c 'worker ready'                              build/device-track-batch32/step12-isolation-logcat.txt   # 17
# 17 个 spawn PID 全部有 "Process <pid> exited due to signal 9"（逐 pid 核对）
$A -s $S shell "ps -A -o PID,NAME | grep visual_js"   # 空
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| isolation tests / failures / skipped | 13 / 0 / 0 | 13/0/0 |
| flags tests / failures / skipped | 2 / 0 / 0 | 2/0/0 |
| worker spawn / ready | 17 / 17 | 1:1 |
| 17 个 spawn PID 退出证据 | 17/17（`Process <pid> exited due to signal 9`） | 全部 |
| app 侧 kill 原因行 | 16（4 `terminate` + 7 `host unbound` + 5 `watchdog hard deadline`） | 见 §5 |
| `warm-up failed` | 0 | 0 |
| 事后残留 `visual_js` 进程 | 空 | 空 |
| 装配级 paint flags 恒抛（isolation / flags / painter） | 0 / 0 / 0 | 0 |
| logcat 9 类签名（两份） | 0 / 0 | 见 E-12 |

## 4. 关键输出（有界）

```text
Time: 62.517
OK (13 tests)

Time: 0.023
OK (2 tests)
```

## 5. 失败 / 跳过 / 异常

无失败、无跳过、无 ANR。**说明**：spawn 17 与 app 侧 kill 行 16 相差 1——差的那一个是宿主 unbound 后由 ActivityManager
直接回收（`isolated not needed` / `Sending signal. PID: … SIG: 9`），PID 级退出证据 17/17 完整，残留检查为空。不判失败。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary、worker 生命周期计数、逐 pid 退出证据。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

PASS：隔离 13/13、flags 2/2；worker spawn↔ready 17/17、全部退出、无残留；装配级 paint flags 恒抛 0。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step12-isolation.log`（191 行 / 9,999 B）、`step13-flags.log`（37 行 / 1,734 B）；logcat 1,242 行 / 616 行。
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
