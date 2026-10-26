# E-03 16KB 页大小与 native 装载（ps16k）

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | 16KB 设备门 |
| gate-or-kd | 16KB 页适配 / KD-74（16KB 类证据） |
| verdict | PASS（运行时）；官方 ELF 工具项 UNVERIFIED |
| executed-at | 2026-10-26T16:18:00+08:00 |
| operator | 设备验证轨（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞；本轨未改产品源） |
| device-serial | emulator-5556 |
| device-avd | ps16k_x86_64 |
| device-api | 36 |
| device-abi | x86_64 |
| device-page-size | 16384 |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`（自含测试 APK） |
| apk-sha256 | `5ef6ff9b46165517c0196f6b2926e8a07b64cf1fcfa8e62c5c82eb6901e72e75` |
| apk-version | — |
| exit-code | 0（adb 进程退出码；非判据） |
| raw-log | `build/device-track-batch23/step03-page-size-16k.log`（52 行）、`step03-page-size-16k-logcat.txt`（306 行）、`step03-page-size-device.txt`、`step03-zipalign-data-test-apk.txt`、`step03-elf-alignment.txt` |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

在真实 16 KB 页内核上让动态链接器实际 mmap `libquickjs.so` 与 LiteRT 两个 `.so`，并断言运行时页大小精确为 16384：

- `runtimePageSizeIs16KB`：`Os.sysconf(_SC_PAGESIZE) == 16384`（4 KB 设备会 `assumeTrue` 跳过，本设备不得跳过）；
- `quickJsNativeLibraryLoadsOnTheDevicePageSize`、`litertNativeLibrariesLoadOnTheDevicePageSize`：`System.loadLibrary` 成功。

补充（静态）：`zipalign -c -P 16 -v 4` 与官方 ELF 判据（LOAD `p_align >= 0x4000`；`GNU_RELRO` 终点 16 KB 对齐，或按官方豁免"RELRO 独占 LOAD 段且余数页内无可写段"）。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5556 shell getconf PAGE_SIZE
$A -s emulator-5556 shell getprop ro.product.cpu.page.size
$A -s emulator-5556 shell getprop ro.product.page_size
$A -s emulator-5556 shell getprop ro.boot.hardware.cpu.pagesize
$A -s emulator-5556 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s emulator-5556 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5556 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.NativeLibraryPageSizeInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
C:/Android/Sdk/build-tools/37.0.0/zipalign.exe -c -P 16 -v 4 \
  core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 3 | 3 |
| failures | 0 | 0 |
| skipped | 0 | 必须 0（16 KB 设备不得 skip） |
| `getconf PAGE_SIZE` | 16384 | 期望 16384 |
| `PAGE_SIZE=` 打印行 | `PAGE_SIZE=16384` | 必须存在（防 skip 掩盖） |
| `zipalign -c -P 16 -v 4` | exit 0，`Verification successful` | 期望 0 |
| 12 类日志签名命中 | 0 | 期望 0 |

`getprop` 原值（空值保留）：`ro.product.cpu.page.size=<empty>`、`ro.product.page_size=<empty>`、`ro.boot.hardware.cpu.pagesize=16384`。

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: test=quickJsNativeLibraryLoadsOnTheDevicePageSize
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: test=runtimePageSizeIs16KB
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: test=litertNativeLibrariesLoadOnTheDevicePageSize
INSTRUMENTATION_STATUS_CODE: 0
OK (3 tests)
INSTRUMENTATION_CODE: -1
```

logcat（`step03-page-size-16k-logcat.txt:279`）：

```text
10-26 08:17:21.396  3357  3373 I System.out: PAGE_SIZE=16384
```

## 5. 静态对齐（补充）

### 5.1 ZIP 层

```text
zipalign -c -P 16 -v 4 data-debug-androidTest.apk
Verification successful
ZIPALIGN_EXIT=0
```

### 5.2 ELF 层

**官方工具缺失**：`llvm-readelf` 与 NDK 均不存在（E-00 §1），因此"用官方 `llvm-readelf -Wl` 逐段判读"这一子项记为 **UNVERIFIED**，不以 zipalign/设备能装代替。

本轨另用只读 Python 解析 ELF 程序头（不写文件、不改产物），判据与官方一致：LOAD `p_align >= 0x4000` 且 offset/vaddr 同余；RELRO 终点 16 KB 整倍数，或余数页内无可写 LOAD 段（官方豁免，依据 `docs/research/2026-10-25-16kb-arm64-api23-build.md:12,59,115,129`）。

生产 App APK（`app-strictOffline-debug.apk`，仅 arm64-v8a + x86_64）14 个 `.so` 全部 PASS；其中 `libquickjs.so` x86_64 `RELRO end=0xcf000 mod=0x3000`、`libLiteRt.so` x86_64 `RELRO end=0x703000 mod=0x3000`，余数页内均无可写 LOAD 段 → 按官方豁免合规。测试 APK 的 x86_64/arm64 切片也全部 PASS；测试 APK 里另含 32 位切片，`armeabi-v7a`/`x86` 的 `libmlkit_google_ocr_pipeline.so` `LOAD align=0x1000` FAIL，但生产 App 已用 `abiFilters` 裁掉这两个 ABI（`app/build.gradle.kts:44-45`），不进入发布包。

完整逐库结果：`build/device-track-batch23/step03-elf-alignment.txt`。

## 6. 脱敏与去噪

同 E-01。证据目录统一扫描见 `00-index.md` §6。

## 7. 判定

**运行时 PASS**：真实 16 KB 内核上 3/3 通过且 `PAGE_SIZE=16384` 有打印，`libquickjs.so` 与 `libLiteRt.so`/`liblitert_jni.so` 实际装载成功；ZIP 16 KB 对齐 exit 0；生产 APK 的 64 位 `.so` 按官方判据（含豁免）全 PASS。

**未覆盖**：官方 `llvm-readelf` 判读（工具缺失）；arm64 设备运行时（ps16k AVD 是 x86_64，本批无法证明 arm64 16 KB 运行面）；32 位 ML Kit `.so` 不合规但已被 ABI 裁剪，若将来放开 ABI 会重新引入。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step03-*`
- 行数/大小：am instrument 52 行 / 2.4 KiB；其余见文件
- 生成命令：见 §2
