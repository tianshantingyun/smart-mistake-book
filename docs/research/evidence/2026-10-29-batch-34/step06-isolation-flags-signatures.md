# E-06 隔离 13 / flags 2 / 12 类崩溃签名 / worker 生命周期

| 字段 | 值 |
|---|---|
| evidence-id | E-06 |
| convention-version | 1 |
| batch | 2026-10-29-batch-34 |
| step | 五（隔离 + flags + 崩溃签名 + worker 生命周期） |
| gate-or-kd | KD-78 ④ / 隔离与 flags 不回退 |
| verdict | PASS |
| executed-at | 2026-10-29T19:01:17+08:00 → 19:02:26+08:00 |
| operator | 设备验证轨（子代理） |
| head | `db792fa873f4bc150f415b67d1c2e3b1bab6c995` |
| worktree | 721 条（241 `M` / 480 `??`） |
| device-serial | `emulator-5562` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` |
| apk-sha256 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| exit-code | 0（两条 `am instrument`） |
| raw-log | `build/device-track-batch34/step14-isolation.log` / `step14-isolation-logcat.txt`（1,254 行）；`step15-flags*.log`（本机，不入仓） |
| record-ref | `docs/research/2026-10-29-batch-34-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

- 隔离 13：`VisualJsIsolationInstrumentedTest` = `OK (13 tests)`，0 失败/错误/跳过。
- flags 2：`VisualMeasureReplayPaintFlagsInstrumentedTest` = `OK (2 tests)`。
- 12 类崩溃签名在**全部 15 份 logcat** 中各为 0。
- worker 生命周期：spawn↔ready 按 pid 1:1；kill 全归因、无意外杀因；套件后无存活 worker。

## 2. 命令（可复跑）

```bash
cd "D:/smart mistake book"; export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562; D=build/device-track-batch34
run() { step="$1"; cls="$2"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner > "$D/$step.log" 2>&1
  $A -s "$S" logcat -v threadtime -d > "$D/$step-logcat.txt" 2>&1; }

run step14-isolation com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest
run step15-flags     com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest

# 签名扫描（12 类，含 0 也打印）
for f in "$D"/*-logcat.txt; do
  for s in "FATAL EXCEPTION" "ANR in " NoSuchMethodError NoSuchMethodException NoClassDefFoundError \
           ClassNotFoundException UnsatisfiedLinkError "CANNOT LINK" "failed to map segment" SIGSEGV \
           StartupException MlKitException; do grep -cF -- "$s" "$f"; done
done

# worker 生命周期
grep -c "Start proc .*:visual_js" "$D/step14-isolation-logcat.txt"                # 17
grep -c "warm-up finished" "$D/step14-isolation-logcat.txt"                       # 17
grep -ao "killing :visual_js worker: .*" "$D/step14-isolation-logcat.txt" | sort | uniq -c
$A -s "$S" shell ps -A -o PID,NAME | grep visual_js || echo "(none)"
```

## 3. 结果摘要

| 套件 | 构建前 `@Test` | 实际 summary | Time | failures/errors/skipped |
|---|---:|---|---:|---:|
| 隔离 | 13 | `OK (13 tests)` | 66.069 | 0 / 0 / 0 |
| flags | 2 | `OK (2 tests)` | 0.065 | 0 / 0 / 0 |

12 类崩溃签名（每格为该 logcat 的命中数）：

| logcat | FATAL | ANR | NoSuchMethod E/X | NoClassDef | ClassNotFound | UnsatisfiedLink | CANNOT LINK | map segment | SIGSEGV | Startup | MlKit |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| step01-backup | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step02-rowcounts | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step03-structure | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step04-trimmer | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step05-c4 | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step06-migration | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step07-69-70 | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step08-70-71 | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step09-commit | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step10-repo | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step11-parity | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step12-painter | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step13-pdf | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step14-isolation | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| step15-flags | 0 | 0 | 0/0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

## 4. 关键输出（有界）

```text
Time: 66.069
OK (13 tests)

Time: 0.065
OK (2 tests)
```

worker 生命周期（isolation）：

```text
Start proc .*:visual_js                     = 17
warm-up finished                            = 17
killing :visual_js worker: watchdog hard deadline              = 5
killing :visual_js worker: terminate(generation=…) accepted     = 3
killing :visual_js worker: the host unbound the visual_js worker = 7
# 服务主动杀因合计 5+3+7 = 15
# AMS 系统回收 "isolated not needed"                                = 12（不计杀因）
# 进程内无 "killing :visual_js worker: <其它原因>" 行                 = 0
# ps -A | grep visual_js 套件后                                        = (none)
```

**逐 pid 核对**（17 个 spawn 的 pid）：其中 15 个各有且仅有一次服务主动杀因；
剩余 2 个 pid（`17733`、`17918`）没有服务杀因行，但都出现在 AMS `Killing … isolated not needed`
列表中，属系统回收（批次 29 判据明确该项不计杀因、不算意外杀）。因此 17 个 worker 全部终止且
无未归类杀因。另一条 `Killing 16647:…offline … due to start instr` 是**主应用进程**（非 `:visual_js`
worker），不得计入 worker 杀因；批次 29 的四项求和公式在本轮不适用，故不强行凑 17。

spawn↔ready 按 pid 完全 1:1：

```text
spawn: 17593 17638 17663 17684 17708 17733 17757 17781 17800 17819 17842 17867 17890 17918 17949 17972 17998
ready: 17593 17638 17663 17684 17708 17733 17757 17781 17800 17819 17842 17867 17890 17918 17949 17972 17998
```

## 5. 失败 / 跳过 / 异常

无。两套件 0 失败 / 0 错误 / 0 跳过；12 类签名全 0；无未归类杀因；无存活 worker。

## 6. 脱敏与去噪

- 删除：逐用例进度点；logcat 中的系统噪声行、时间戳正文（只保留签名计数与生命周期行）。
- 保留：spawn/ready/杀因计数、逐 pid 集合、签名计数（含 0）、`OK (N tests)`。
- 扫描：`00-index.md` §6 → 0 命中。

## 7. 判定

**PASS**。隔离 13/13、flags 2/2 全绿；12 类崩溃签名在 15 份 logcat 全 0；worker spawn↔ready
按 pid 1:1；服务主动杀因 15 + AMS 系统回收 2 = 17，无未归类杀因；套件后无存活 worker。
