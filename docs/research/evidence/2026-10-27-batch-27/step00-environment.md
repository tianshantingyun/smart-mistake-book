# E-00 环境、构建与产物身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-27-batch-27 |
| step | 步零（环境） |
| gate-or-kd | —（信息性） |
| verdict | PASS（信息性） |
| executed-at | 2026-10-28T02:55:00+08:00（设备只读核）～03:45:00+08:00（恢复核验） |
| operator | 设备验证轨（subagent，批次 27） |
| head | caa0726a02f1862371a3b2770c8c29663ba7fa02 |
| worktree | dirty（698 条；他线在飞 + 批次 27 T1–T6 落层；本轨零源码改动） |
| device-serial | emulator-5554（5556 只读核；5584 他线未触碰） |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| sanitized | 是（§7；扫描见 00-index §6） |

## 1. 主机工具

```bash
C:/Android/Sdk/platform-tools/adb.exe version   # 1.0.41 / 37.0.1-15733141
./gradlew --version                             # Gradle wrapper 9.6.1（同批次 26）
C:/Android/Sdk/build-tools/37.0.0/dexdump.exe   # R8 类名核对用（本批新增主机工具用途）
C:/Android/Sdk/build-tools/37.0.0/apksigner.bat verify --print-certs <apk>  # 证书身份
```

| 工具 | 用途 | 备注 |
|---|---|---|
| adb | install / am instrument / logcat / pm / ps / dumpsys / pidof / run-as / root·unroot | **未跑 `connectedAndroidTest`**（禁项）；`adb root/unroot` 一次（step03 root 通道尝试，已恢复 shell） |
| Gradle | 只跑 `assemble*`（:app debug+androidTest、:core:data androidTest、:app strictOffline release） | 无 connected 任务、无 test 任务 |
| keytool | 一次性本地 release smoke key | `CN=Batch27 Local Smoke`，7 天，`build/` 内不入仓 |
| aapt2/dexdump/apksigner | 合并清单 provider 审计 / R8 类名审计 / 证书身份 | 主机侧只读 |

## 2. 设备（开工只读核）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A -s emulator-5554 emu avd name            # test_device
$A -s emulator-5554 shell getprop ro.build.version.sdk      # 34
$A -s emulator-5554 shell getprop ro.product.cpu.abi        # x86_64
$A -s emulator-5554 shell getconf PAGE_SIZE                 # 4096
$A -s emulator-5554 shell uptime            # 19:07:22 up 19:44, load 0.17/0.12/0.05（沉降态）
$A -s emulator-5556 emu avd name            # ps16k_x86_64（只读核）
$A -s emulator-5556 shell getprop ro.build.version.sdk      # 36
$A -s emulator-5556 shell getconf PAGE_SIZE                 # 16384
```

设备时钟为 UTC；主机时钟 +08:00（=设备 +8h）。本证据 `executed-at` 用主机时钟，logcat
原文引 UTC 值。批次无设备环境故障；5554 全程可用。

## 3. 构建（只 assemble；release 用一次性本地 key）

```bash
./gradlew --console=plain :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest
# BUILD SUCCESSFUL in 43s；293 actionable tasks: 15 executed, 278 up-to-date；EXIT=0

keytool -genkeypair -keystore "build/device-track-batch27/release-smoke-b27.jks" \
  -storepass <local-smoke-b27> -keypass <local-smoke-b27> -alias batch27-smoke \
  -keyalg RSA -keysize 2048 -validity 7 -dname "CN=Batch27 Local Smoke,O=Local,C=CN"
# EXIT=0

RELEASE_KEY_ALIAS=batch27-smoke RELEASE_KEY_PASSWORD=<local-smoke-b27> \
RELEASE_STORE_FILE="D:/smart mistake book/build/device-track-batch27/release-smoke-b27.jks" \
RELEASE_STORE_PASSWORD=<local-smoke-b27> \
./gradlew --console=plain :app:assembleStrictOfflineRelease
# BUILD SUCCESSFUL in 2m 57s；412 actionable tasks: 63 executed, 349 up-to-date；EXIT=0
```

签名前置（同批次 24/25/26 口径）：本机无生产 `RELEASE_*` 变量，release 以一次性本地 key
构建，验证 R8/资源收缩/打包/启动路径，**不验证生产签名与发布链**；生产 release 签名
UNVERIFIED。本批未运行 `validateReleaseSigning`（批次 26 已存原始证据，且本批未变更签名
逻辑——`app/build.gradle.kts` 本轨零改动）。

**测试 APK 产物级核验**（防"旧包跑新门"误读）：

```bash
python zipfile 实测 app-strictOffline-debug-androidTest.apk（dex 字符串池）：
  classes3.dex: 'VisualJsIsolationInstrumentedTest' ×106、
                'aHungWorkerReleasesTheCallerWithinHardPlus500ms' ×4、
                'VisualJsHandshakeRaceInstrumentedTest' ×42、
                'workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater' ×4
```

主 APK（app debug）为批次 27 新源构建（哈希 ≠ 批次 26）；androidTest APK 与批次 26 逐字节
一致——本批 app androidTest 源零改动（隔离套件 13 例与批次 26 E-01 同集），主 APK 内引擎/
服务改动即被测对象。

## 4. 产物身份

| 产物 | 本机路径 | sha256 | 大小 |
|---|---|---|---|
| app debug APK | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | `43adac58c0b6c28e46bef7cc99ebc01b3fd59781f01e5da54581807330940a58` | 189,399,345 B |
| app debug androidTest APK | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | `cde7e27b9a6c0ab90db06f05e6f62465c729e220593b262980f374cc9becb293` | 1,585,488 B（=批次 26，逐字节一致） |
| core:data androidTest APK | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | `64264e505f16171f86933c98dccb288469f12a15b32268db9ecc2411a78d8fd8` | 197,639,024 B |
| app release APK（一次性本地 key） | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk` | `950b7eee31c46561d33a34cbd202c7b01c0b753e308adb830c02fead2d26c7cf` | 155,732,032 B |

release 证书（`apksigner verify --print-certs`）：`V2 Signer: certificate DN: CN=Batch27
Local Smoke, O=Local, C=CN`，SHA-256 `1ac911057df9f6fb47b2a48684ad86344bf4c1b74676ab48eee2f
5c2e5a1beea`。**一次性本轨 key，不是生产发布证书。**

## 5. 安装与设备侧身份（每步 `pm path`+`sha256sum` 逐字节一致）

- debug 态首装（隔离套件/P-08/A-11/采样/备份全部在此态执行）：三包 `install -r -t` 均
  `Success`；app `43adac58…`、test `cde7e27b…`、data-test `64264e50…` 设备侧与上表一致。
- release 舞步（step03）：先 `uninstall` 两包再装 release（`950b7eee…` 设备侧一致）+
  重签 test 包；舞步后**恢复 debug 三件套**（三个 install 均 `Success`），恢复后设备侧
  sha256 复核 = `43adac58c0b6c28e` / `cde7e27b9a6c0ab9` / `64264e505f16171f`（前 16 位，
  与上表逐字节一致）；`adb unroot` 后 `id` = `uid=2000(shell)`。
- 收尾设备态：沉降中（19:44:50 up 20:21，load 0.63/0.73/0.54 回落）。

## 6. 数据保护

- 设备用途：专用测试模拟器；无真实学习者数据；备份抽验为合成夹具。
- 破坏性操作：release 舞步的两次 `adb uninstall`（卸载对象=本轨自己刚装的 debug 包，卸载后
  立即按同产物恢复；应用数据为合成夹具，无用户数据）；`adb root`/`unroot`（adbd 重启，
  不动 userdata）。`adb reboot` 本批 **0 次**。

## 7. 判定

PASS（信息性）。设备身份、四产物哈希、构建 exit 码、测试 APK 内含套件的产物级核验、
release 证书指纹均已固定；`connectedAndroidTest` 零次；git 写操作零次；产品/测试源代码
零改动（本轨只新增本证据目录与 `build/` 原始日志）。
