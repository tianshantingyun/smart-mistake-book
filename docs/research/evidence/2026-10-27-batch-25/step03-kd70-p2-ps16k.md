# E-03 KD-70 P2 于 ps16k（16KB × x86_64）重跑 + 16KB 装载门复跑

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-27-batch-25 |
| step | 三（KD-70 P2 @ ps16k + 16KB 门） |
| gate-or-kd | KD-70（P2 判据不放宽重跑）/ 16KB 运行时装载门 |
| verdict | **PASS（20/20 + 3/3；P2 16KB 轴成立；arm64 轴 UNVERIFIED，见 §5）** |
| executed-at | 2026-10-27T03:34:00+08:00 |
| operator | 设备验证轨（subagent，批次 25） |
| head | 25ddb60273e32ebd4100e8deb9f965bb0dfd4476 |
| worktree | dirty（他线在飞；含 T2 的 warm-up 落层） |
| device-serial | emulator-5556 |
| device-avd | ps16k_x86_64 |
| device-api | 36 |
| device-abi | x86_64 |
| device-page-size | 16384 |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`（自含测试 APK） |
| apk-sha256 | `94df5c3931db09170dc8ac52db3a074c281bc2b8869bdba3ced724e08dea8590`（设备侧 `pm path`+`sha256sum` 一致） |
| apk-version | — |
| exit-code | 0（adb 进程退出码；判据是 summary + `PAGE_SIZE=` 行） |
| raw-log | `build/device-track-batch25/step03-kd70.log`（289 行）、`step03-16kb.log`（51 行）、`step03-ps16k-logcat.txt`（1,113 行）、`step03-install.log` |
| record-ref | 批次 25 记录（由记录员后写） |
| sanitized | 是（§6） |

## 1. 目的与判据

按批次 25 R4（`docs/research/2026-10-27-arm64-16kb-coverage.md` §3）在 **真实 16KB 页内核**
上重跑 KD-70 全量，判据与批次 24 E-02 **完全一致、不放宽**（R4 §3.3 逐字）：

1. 全类 20/20 绿（`QuickJsVisualExecutionEngineInstrumentedTest`，@Test 计数 20 实测一致）；
2. P2 `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`：`MEMORY_LIMIT` +
   诊断含 `attributed by memoryUsage` / `mallocLimit=16777216` / `PENDING_CALIBRATION`，
   且 `mallocSize != -1`（⇒ ≥15,099,495，≥90%；精确值不打印，维持 EXACT VALUE UNVERIFIED）；
3. `PAGE_SIZE=` 日志行 = 16384；
4. 同设备补 16KB 装载门三类（`NativeLibraryPageSizeInstrumentedTest`，4KB 设备会 skip，
   本设备必须 3/3 且 `runtimePageSizeIs16KB` 真断言 16384）；
5. 崩溃/linker 签名 0。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A -s emulator-5556 shell getconf PAGE_SIZE          # 16384（R4 §3.1 前检）
$A -s emulator-5556 shell getprop ro.build.version.sdk   # 36
$A -s emulator-5556 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s emulator-5556 logcat -c
$A -s emulator-5556 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5556 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.NativeLibraryPageSizeInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5556 logcat -v threadtime -d > build/device-track-batch25/step03-ps16k-logcat.txt
```

- 工作目录：仓库根；执行前该设备刚完成冷启动恢复（step00 §2.1），boot 完成后才安装
- 安装：`Success`；设备侧 APK sha256 与产物一致
- 测试 runner 包：`com.tingyun.smartmistakebook.core.data.test`（实际安装包名实测；
  R4 §3.2 命令块中的 `localfirst.test` 是文档笔误，未采用）

## 3. 结果摘要

| 指标 | KD-70 全量 | 16KB 门 | 判据 |
|---|---|---|---|
| tests run | 20 | 3 | 20 / 3 |
| failures | 0 | 0 | 0 |
| skipped | 0 | 0 | 0（本设备 16384，`assumeTrue` 不触发） |
| summary | `OK (20 tests)` | `OK (3 tests)` | — |
| `Time` | 7.451 s | 0.025 s | 记录值 |
| `PAGE_SIZE=` 行 | — | `16384` | 必须存在且 =16384 |
| 9 类签名命中 | 0（共用 logcat） | — | 期望 0 |

签名扫描（对 `step03-ps16k-logcat.txt`）：

```bash
for s in "FATAL EXCEPTION" "ANR in" "NoSuchMethodError" "NoClassDefFoundError" \
         "UnsatisfiedLinkError" "SIGSEGV" "CANNOT LINK" "failed to map segment" "unsupported RELRO"; do
  printf '%-24s %s\n' "$s" "$(grep -c "$s" build/device-track-batch25/step03-ps16k-logcat.txt)"
done
# 全部 0
```

P2 用例设备耗时：logcat `TestRunner started 19:32:30.038` → `finished .072` = **34 ms**
（批次 24 在 4KB x86_64 上为 98 ms；同数量级，远离夹具 `cooperativeTimeoutMillis=10_000`，
`PHASE_TIMEOUT` 抢跑风险未出现）。

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: test=heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage
INSTRUMENTATION_STATUS_CODE: 1
...
INSTRUMENTATION_STATUS: test=heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage
INSTRUMENTATION_STATUS_CODE: 0
...
Time: 7.451

OK (20 tests)

INSTRUMENTATION_CODE: -1
```

```text
10-26 19:32:30.935  4186  4202 I System.out: PAGE_SIZE=16384
```

P2 通过形态（由通过断言约束，非日志打印；断言位置
`core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt` P2 用例体）：
`reason == MEMORY_LIMIT`、诊断含 `attributed by memoryUsage`/`mallocLimit=16777216`/
`PENDING_CALIBRATION`、`!diagnostics.contains("mallocSize=-1")`。⇒ `mallocSize ≥ 15,099,495`
（≥90% × 16 MiB）。**精确 `mallocSize` 未打印，维持 EXACT VALUE UNVERIFIED**（与批次 24 E-02 §7 同口径）。

## 5. 结论（按 R4 §4.2 降级句式逐条标注）

1. **KD-70 P2**：`heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage` 绿——
   reason=`MEMORY_LIMIT`，诊断含 `attributed by memoryUsage`/`mallocLimit=16777216`/
   `PENDING_CALIBRATION`，`mallocSize != -1`。**[PROVED on emulator-5556: x86_64 × 16KB × API 36]**
   （R4 §4.2 结论 B 句式）。KD-70 是否因此变动裁定由主会话按台账回链决定；本步不做关闭动作。
2. **16KB 装载门**：`NativeLibraryPageSizeInstrumentedTest` 3/3（`runtimePageSizeIs16KB`
   断言 16384 通过而非 skip），`libquickjs.so`/`libLiteRt.so`/`liblitert_jni.so` x86_64 切片由
   bionic linker 真实装载，linker/RELRO grep 零命中。**[PROVED on emulator-5556: x86_64 × 16KB × API 36]**
   （R4 §4.2 结论 A 句式）。与批次 24 E-04（同为 ps16k）一致，无回归。
3. **arm64 运行时轴 UNVERIFIED**（R4 §4.2 结论 C 句式）：本机（x86_64 Windows）emulator 对
   arm64 系统镜像 FATAL 硬拒载（2026-10-27 R4 实测，
   `docs/research/2026-10-27-arm64-16kb-coverage.md` §2.1 探针 4）；arm64 `.so` 仅有静态对齐证据，
   无任何运行时装载证据。**不得写"16KB 已验证"，只能写"16KB × x86_64 已验证；
   16KB × arm64 UNVERIFIED"。**
4. **真机子句 UNVERIFIED**（R4 §4.2 结论 D 句式）：契约 `visual-generation-contract.md:22`
   的"16 KB 真机加载"子句不受本步影响——ps16k 是模拟器，无 Play 安装链路/真机兼容模式判定。
5. **arm64 上 P2 耗时/OOM 行为 UNVERIFIED**（R4 §4.2 结论 E 句式）：本步 34 ms 是 x86_64 读数，
   不得外推。
6. T2 的 warm-up 落层在本 APK 内（服务 `onCreate` 一次性装载 + 单调 ready），本步引擎类
   20/20 绿与 warm-up 无冲突；KD-76 的设备判据归属 E-01（隔离类 T2 三例）记录，本步不重复声称。

## 6. 脱敏与去噪

- 删除：adb 安装进度行。
- 保留：P2 逐例状态、summary、`PAGE_SIZE=` 行、崩溃/linker 签名零计数、P2 起止时间戳。
- 扫描：见 `00-index.md` §6。
- 二进制/截图：无。

## 7. 判定

**PASS（20/20 + 3/3）**：KD-70 P2 判据在 16KB × x86_64 轴上成立（与批次 24 的 4KB × x86_64
合成双页宽矩阵），16KB 装载门复跑无回归；arm64 轴、真机子句、精确 mallocSize 均如实
UNVERIFIED（§5）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch25/step03-kd70.log`（289 行）、`step03-16kb.log`（51 行）、
  `step03-ps16k-logcat.txt`（1,113 行）、`step03-install.log`（2 行）
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
