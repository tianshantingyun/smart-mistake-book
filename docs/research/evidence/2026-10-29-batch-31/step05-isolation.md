# E-05 隔离 13 例：`VisualJsIsolationInstrumentedTest`（PASS 13/13，spawn↔ready 17↔17）

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 二（八套件第 5；判据 §2.5、§5.3） |
| gate-or-kd | KD-78 ④-P3/P4；worker 生命周期收口 |
| verdict | **PASS**：`OK (13 tests)`；spawn 17 = ready 17 且 **pid 集合逐值相同**；`warm-up failed`=0；kill 四类 5+4+7+1=17；无残留 worker |
| executed-at | 2026-10-29T06:38:31–06:39:32+0800 |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` |
| apk-sha256 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19`（设备侧一致） |
| apk-version | （测试包 versionName 空） |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch31/step05-isolation.log`（191 行 / 9999 B）/ `-logcat.txt`（1231 行 / 190163 B） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §2.5：`OK (13 tests)`；`Start proc .*:visual_js` 与 `visual_js warm-up finished` 按 pid 1:1；
`warm-up failed`=0；kill 四类之和 = spawn；事后无 `visual_js` 残留进程；9 类签名 0；无 `death was not confirmed`。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step05-isolation.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step05-isolation-logcat.txt

L=build/device-track-batch31/step05-isolation-logcat.txt
grep "Start proc .*:visual_js" $L | sed -n 's/.*Start proc \([0-9]*\):.*/\1/p' | sort -n
grep "visual_js warm-up finished" $L | awk '{print $3}' | sort -n
$A -s $S shell ps -A -o PID,NAME | grep visual_js
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 13 | 期望 13 |
| failures / errors / skipped | 0 / 0 / 0 | 期望 0 |
| spawn | 17 | — |
| warm-up finished | 17 | = spawn |
| spawn↔ready pid 1:1 | `diff` 为空（17 个 pid 逐值相同） | PASS |
| `visual_js warm-up failed` | 0 | 期望 0 |
| `hard deadline (1500 ms) for request` | 5 | — |
| `terminate(generation` | 4 | — |
| `the host unbound the visual_js worker` | 7 | — |
| `due to start instr` | 1 | — |
| 四类之和 | **17** = spawn | PASS |
| 未分类杀因 | 无 | PASS |
| 事后残留 `ps \| grep visual_js` | 空 | PASS |
| 9 类签名 / `death was not confirmed` | 0 / 0 | 期望 0 |

## 4. 关键输出（有界）

```text
Time: 60.779

OK (13 tests)

INSTRUMENTATION_CODE: -1
```

pid 集合（spawn 与 warm-up 两行逐值相同）：

```text
9193 9243 9267 9286 9311 9336 9360 9383 9403 9422 9446 9470 9493 9518 9547 9570 9594
PID_SETS_IDENTICAL=YES (n=17)
died_lines=5        # AMS "has died: vis FGS"，是四类 kill 的后果行，不是独立杀因
```

## 5. 失败 / 跳过 / 异常

无。批次 28/29 的 "spawn 14 / ready 0 / warm-up failed 12" 形态未复现。

## 6. 脱敏与去噪

- 删除：无（未过滤 logcat；本文件只摘计数与 pid 集）。
- 保留：spawn/ready/kill 计数、pid 集合、残留检查。
- 扫描：R4 §4.4 → 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS。判据 §2.5 第 1–5 项全部成立；助手进程生命周期无泄漏。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step05-isolation.log` / `-logcat.txt`
- 行数/大小：191 行 / 9999 B；logcat 1231 行 / 190163 B
- 说明：`build/` 被 `.gitignore:15` 忽略。
