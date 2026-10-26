# E-08 12 类崩溃签名全量重扫（17 个 logcat）

| 字段 | 值 |
|---|---|
| evidence-id | E-08 |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | 证据落盘复核（KD-74 C1②） |
| gate-or-kd | KD-74 |
| verdict | PASS（覆盖 17/17；2 条命中均非被测应用） |
| executed-at | 2026-10-26T16:59:00+08:00 |
| operator | 证据落盘员（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞；本轨只写 docs 证据与本机 `build/` 原始输出） |
| device-serial | emulator-5554 / emulator-5556（日志来源） |
| device-avd | test_device / ps16k_x86_64 |
| device-api | 34 / 36 |
| device-abi | x86_64 / x86_64 |
| device-page-size | 4096 / 16384 |
| apk | 见 E-00 §3（debug、internal、data/database androidTest） |
| apk-sha256 | 见 E-00 §3（本轮重算一致，见 `00-index.md` §2） |
| exit-code | 0 |
| raw-log | `build/device-track-batch23/step08-signature-rescan.txt`（19 行）＋ 17 个 `*logcat*.txt`（15,313 行） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4（记录尚未生成，见 §5） |
| sanitized | 是（§6；0 命中） |

## 1. 目的与判据

批次随附的 `build/device-track-batch23/signature-matrix.txt` 只覆盖 **13** 个 logcat，未覆盖 `step01d`、`step01e`、`step06d`、`step06e`。KD-74 的 Fix direction 要求"logcat 的 FATAL/ANR/NoSuchMethod/NoClassDef 全量 grep"，因此本步对 `build/device-track-batch23/` 下全部 **17** 个 logcat 重跑 R4 §8.2 的 12 类签名，0 也打印；任何命中必须给出 ±上下文与归属，禁止静默删除。

## 2. 命令（可复跑）

```bash
cd "D:/smart mistake book"                  # 仓库根（本机路径；换机改为自己的仓库根）
LOG=build/device-track-batch23/step01d-logcat.txt   # 外层对每个 *logcat*.txt 重复本循环
while IFS= read -r s; do
  printf '%-28s %s\n' "$s" "$(grep -cF -- "$s" "$LOG" 2>/dev/null | tr -d '\r')"
done <<'EOF' > build/device-track-batch23/step08-signature-rescan.txt
FATAL EXCEPTION
ANR in 
NoSuchMethodError
NoSuchMethodException
NoClassDefFoundError
ClassNotFoundException
UnsatisfiedLinkError
CANNOT LINK
failed to map segment
SIGSEGV
StartupException
MlKitException
EOF
# 外层对 build/device-track-batch23/*logcat*.txt 循环，逐文件输出一行 12 列；
# 再对命中行取上下文：
grep -nF -B4 -A6 -- "ANR in " build/device-track-batch23/step01d-logcat.txt
grep -nF -B6 -A8 -- "UnsatisfiedLinkError" build/device-track-batch23/step01d-logcat.txt
```

- 工作目录：仓库根
- 退出码：0
- 文件数：`ls build/device-track-batch23/*logcat*.txt | wc -l` → `17`

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| logcat 文件 | 17 | 17/17 覆盖（旧矩阵 13/17） |
| 签名 × 文件 | 204 | 全部执行 |
| 总命中 | 2 | 均位于 `step01d-logcat.txt` |
| 被测应用归因命中 | 0 | `FATAL EXCEPTION`/`NoSuchMethod*`/`NoClassDef*`/`ClassNotFound*`/`CANNOT LINK`/`failed to map segment`/`SIGSEGV`/`StartupException`/`MlKitException` 全 0 |
| 旧矩阵未覆盖文件 | 4 | step01d / step01e / step06d / step06e 本轮补齐 |

逐文件计数（12 列顺序：FATAL / ANR / NSME / NSCE / NCDE / CNFE / ULE / CANTLINK / MAPSEG / SIGSEGV / STARTUP / MLKIT）：

```text
step01-logcat.txt                          0 0 0 0 0 0 0 0 0 0 0 0
step01b-logcat.txt                         0 0 0 0 0 0 0 0 0 0 0 0
step01c-logcat.txt                         0 0 0 0 0 0 0 0 0 0 0 0
step01d-logcat.txt                         0 1 0 0 0 0 1 0 0 0 0 0
step01e-logcat.txt                         0 0 0 0 0 0 0 0 0 0 0 0
step02-quickjs-oom-logcat.txt              0 0 0 0 0 0 0 0 0 0 0 0
step03-page-size-16k-logcat.txt            0 0 0 0 0 0 0 0 0 0 0 0
step04-coldstart1-logcat.txt               0 0 0 0 0 0 0 0 0 0 0 0
step04-coldstart2-logcat.txt               0 0 0 0 0 0 0 0 0 0 0 0
step04-ocr-logcat.txt                      0 0 0 0 0 0 0 0 0 0 0 0
step05-backup-restore-logcat.txt           0 0 0 0 0 0 0 0 0 0 0 0
step06-device-migration-drill-logcat.txt   0 0 0 0 0 0 0 0 0 0 0 0
step06b-device-migration-drill-optin-logcat.txt 0 0 0 0 0 0 0 0 0 0 0 0
step06c-migration-matrix-sample-logcat.txt 0 0 0 0 0 0 0 0 0 0 0 0
step06d-full-migration-matrix-logcat.txt   0 0 0 0 0 0 0 0 0 0 0 0
step06e-kill-stage-logcat.txt              0 0 0 0 0 0 0 0 0 0 0 0
step07-kd67-cas-logcat.txt                 0 0 0 0 0 0 0 0 0 0 0 0
TOTAL_HITS_ALL_FILES=2
```

## 4. 关键输出（有界）

命中 1（`step01d-logcat.txt:390`）：

```text
08:47:10.256   556  2943 E ActivityManager: ANR in com.google.android.gms.persistent
08:47:10.256   556  2943 E ActivityManager: PID: 2538
08:47:10.256   556  2943 E ActivityManager: Reason: Broadcast of Intent { act=android.intent.action.SIM_STATE_CHANGED ... }
```

命中 2（`step01d-logcat.txt:1700`）：

```text
08:47:18.885  3289  3325 W NativeCrashHandlerImpl: java.lang.UnsatisfiedLinkError: dlopen failed:
  library "libnative_crash_handler_jni.so" not found
  at com.google.android.libraries.performance.primes.metrics.crash.NativeCrashHandlerImpl.b(PG:2)
```

归属：两条都发生在 `adb reboot` 后的系统启动阶段（08:47:10 / 08:47:18），**早于**该轮测试开始（`08:47:39.406 TestRunner: started: ...`），PID 2538 是 GMS、PID 3289 是启用 Primes 的第三方进程；被测应用/测试进程不是这两个 PID。两条均为系统噪声，不构成被测应用的崩溃签名。

## 5. 失败 / 跳过 / 异常

- 无失败、无跳过。
- **更正**：旧 `signature-matrix.txt` 的"13 个文件全 0"不能外推为"全部步骤全 0"；本步把覆盖限定为 17/17，并把 `step01d` 的 2 条系统命中如实保留。
- `step06e-kill-stage-logcat.txt` 的 12 类 Java 签名为 0 与设计不矛盾：该用例是 native kill，崩溃记录在 `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`（E-06 §9.2），不是 logcat Java 异常。

## 6. 脱敏与去噪

- 删除：无（只摘取命中 ± 上下文与计数）。
- 保留：两条命中、`TOTAL_HITS_ALL_FILES=2`、逐文件 0 计数。
- 扫描：R4 §4.4 四条命令 → secrets 0 / credential assignments 0 / emails 0 / home paths 仅正则自匹配（结果见 `00-index.md` §6）。
- 二进制/截图：无。

## 7. 判定

**PASS（覆盖 17/17；2 条命中均非被测应用）**。12×17=204 格全部执行；0 个被测应用归因的崩溃签名。2 条命中保留在案且给出上下文，未用"无异常"代替计数。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step08-signature-rescan.txt`（19 行）与 17 个 `*logcat*.txt`（15,313 行）
- 生成命令：§2
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。

## 9. 追加：设备指纹在线复核（2026-10-26T17:06+08:00）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
MSYS_NO_PATHCONV=1 $A devices
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell getconf PAGE_SIZE                       # 4096
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell getprop ro.build.version.sdk            # 34
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell getprop ro.product.cpu.abi              # x86_64
MSYS_NO_PATHCONV=1 $A -s emulator-5554 emu avd name                                  # test_device
MSYS_NO_PATHCONV=1 $A -s emulator-5556 shell getconf PAGE_SIZE                       # 16384
MSYS_NO_PATHCONV=1 $A -s emulator-5556 shell getprop ro.build.version.sdk            # 36
MSYS_NO_PATHCONV=1 $A -s emulator-5556 shell getprop ro.product.cpu.abi              # x86_64
MSYS_NO_PATHCONV=1 $A -s emulator-5556 shell getprop ro.boot.hardware.cpu.pagesize   # 16384
MSYS_NO_PATHCONV=1 $A -s emulator-5556 shell getprop ro.product.cpu.page.size        # <empty>
MSYS_NO_PATHCONV=1 $A -s emulator-5556 emu avd name                                  # ps16k_x86_64
```

结果与 E-00 §2 逐项一致；`adb devices` 同时列出 `emulator-5562`/`emulator-5584`，本轮未在其上执行任何命令。设备仍在线的复核不替代 E-00/E-03 的设备执行证据。
