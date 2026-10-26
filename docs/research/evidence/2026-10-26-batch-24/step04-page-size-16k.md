# E-04 16KB 页运行时装载（NativeLibraryPageSizeInstrumentedTest）

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-26-batch-24 |
| step | 四（16KB 抽验） |
| gate-or-kd | 16KB 运行时门 / KD-74 关联证据 |
| verdict | PASS（3/3，真实 16384 页内核） |
| executed-at | 2026-10-26T21:45:30+08:00 |
| operator | 设备验证轨（subagent，批次 24） |
| head | 5bc8bdf868e1296049ab901cf1da69e136062006 |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5556 |
| device-avd | ps16k_x86_64 |
| device-api | 36 |
| device-abi | x86_64 |
| device-page-size | 16384 |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`（自含测试 APK） |
| apk-sha256 | `8ff654e95686ea5ef89a73cc797f6ae7688c720877b01a50700dff6d5d605281` |
| apk-version | — |
| exit-code | 0（adb 进程退出码；判据是 summary + PAGE_SIZE 行） |
| raw-log | `build/device-track-batch24/step04-16kb.log`（51 行）、`step04-16kb-logcat.txt`（300 行） |
| record-ref | `docs/research/2026-10-26-batch-24-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

在**真实 16KB 页内核**上让动态链接器实际装载 `libquickjs.so`、`libLiteRt.so`、`liblitert_jni.so`，
并要求 `runtimePageSizeIs16KB` 精确断言 `16384`。4KB 设备会 skip，不能作为 16KB 证据；
本步必须在 `ps16k_x86_64` 上执行。

本步只回答**运行时装载**；静态 ELF 页对齐沿用批次 23 E-03（`zipalign` + Python 解析）与 E-09
（官方 LLVM `llvm-readobj --program-headers`），本批不重复。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A -s emulator-5556 shell getconf PAGE_SIZE          # 16384
$A -s emulator-5556 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s emulator-5556 logcat -c
$A -s emulator-5556 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.NativeLibraryPageSizeInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5556 logcat -v threadtime -d > build/device-track-batch24/step04-16kb-logcat.txt
```

- 工作目录：仓库根
- 安装：`Success`，退出码 0
- 设备侧 APK 复核：`pm path` + `sha256sum` = `8ff654e9…5281`，与产物一致

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 3 | 期望 3 |
| failures | 0 | 期望 0 |
| skipped | 0 | 期望 0（本设备是 16384，不触发 `assumeTrue`） |
| `OK (3 tests)` | 是 | 是 |
| `PAGE_SIZE=` 打印行 | `16384` | 必须存在且等于 16384 |
| `INSTRUMENTATION_CODE` | -1 | 非判据（adb 侧 exit=0） |
| 7 类崩溃签名命中 | 0 | 期望 0 |

逐例状态（`1 → 0`）：

```text
quickJsNativeLibraryLoadsOnTheDevicePageSize      => 1, 0
runtimePageSizeIs16KB                             => 1, 0
litertNativeLibrariesLoadOnTheDevicePageSize      => 1, 0
```

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: numtests=3
...
Time: 0.039

OK (3 tests)

INSTRUMENTATION_CODE: -1
```

```text
10-26 13:45:30.655  5363  5379 I System.out: PAGE_SIZE=16384
```

（设备时钟与主机不同步，`logcat` 时间戳按设备本地时间；判定以 `PAGE_SIZE=` 行与 summary 为准。）

## 5. 失败 / 跳过 / 异常

无失败、无跳过。`runtimePageSizeIs16KB` 的 `assumeTrue` 未触发，说明内核页大小确实是 16384；
三个 `System.loadLibrary` 均未抛 `UnsatisfiedLinkError` / `dlopen failed`。

## 6. 脱敏与去噪

- 删除：安装进度行。
- 保留：`PAGE_SIZE=` 行、逐例状态、summary、crash 签名零计数。
- 扫描：见 `00-index.md` §6（0 命中）。
- 二进制/截图：无。

## 7. 判定

**PASS（3/3）**：在 API 36 / x86_64 / `PAGE_SIZE=16384` 的 `ps16k_x86_64` 上，QuickJS 与 LiteRT
原生库真实装载成功，页大小断言精确成立。

未覆盖：

1. **arm64 16KB 运行时**仍未执行（ps16k 是 x86_64 AVD）；此项沿用批次 23 缺口，不能由本步替代。
2. 静态 ELF 页对齐本批未重跑（批次 23 E-03/E-09 已覆盖，结论不因本步改变）。
3. 只做 `System.loadLibrary`，不构造 `Interpreter`、不跑推理；本步判据是装载器拒绝面，不是模型语义。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch24/step04-16kb.log`（51 行 / 约 4 KiB）、`step04-16kb-logcat.txt`（300 行 / 约 30 KiB）
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
