# E-00 环境、设备与产物身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-27-batch-26 |
| step | 步零（环境） |
| gate-or-kd | —（信息性） |
| verdict | PASS（信息性） |
| executed-at | 2026-10-27T07:14:00+08:00 |
| operator | 设备验证轨（subagent，批次 26） |
| head | 405c83739cfa5385aa7652ef61f3488f953c66ce |
| worktree | dirty（688 条；他线在飞 + 批次 26 T1/T4/T5 未提交落层） |
| device-serial | emulator-5554（5556 只读核，未执行安装/测试） |
| sanitized | 是（§6；扫描见 00-index §6） |

## 1. 主机工具

```bash
C:/Android/Sdk/platform-tools/adb.exe version   # 1.0.41 / 37.0.1-15733141
java -version                                   # OpenJDK 21.0.11 LTS (Temurin 21.0.11+10)
./gradlew --version                             # wrapper 9.6.1
```

| 工具 | 路径/版本 | 备注 |
|---|---|---|
| adb | `C:/Android/Sdk/platform-tools/adb.exe`，1.0.41 / 37.0.1-15733141 | 只用 `install -r -t`、`am instrument`、`logcat`、`pm`、`am start -W`、`am force-stop`、`pidof`、`emu avd name`、`reboot` |
| JDK | OpenJDK 21.0.11 LTS | Gradle / keytool / apksigner 共用 |
| Gradle | wrapper 9.6.1 | 只跑 `assemble*` 与 `validateReleaseSigning`；**未跑 `connectedAndroidTest`**（禁项） |
| keytool | JDK 21 附带 | 本批一次性自签名 smoke key（§3） |

## 2. 设备（只读核）

四台模拟器在线（5554/5556/5562/5584）。**5562/5584 属他线，全程未在其上执行任何命令**。

| serial | AVD | API | ABI | `getconf PAGE_SIZE` | 用途 |
|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | 隔离门（13+2）/ KD-66 门 / 冷启动 ③ 尝试 / R8 release 冒烟 / 备份抽验 |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | **16384** | **只读核**（`emu avd name` + 三值；本批四步不需要 ps16k，未安装、未执行测试） |

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A -s emulator-5554 emu avd name            # test_device
$A -s emulator-5554 shell getprop ro.build.version.sdk      # 34
$A -s emulator-5554 shell getprop ro.product.cpu.abi        # x86_64
$A -s emulator-5554 shell getconf PAGE_SIZE                 # 4096
$A -s emulator-5554 shell uptime            # up 3:54, load 0.03（沉降态）
$A -s emulator-5556 emu avd name            # ps16k_x86_64
$A -s emulator-5556 shell getprop ro.build.version.sdk      # 36
$A -s emulator-5556 shell getconf PAGE_SIZE                 # 16384
emulator.exe -list-avds                     # test_device / ps16k_x86_64 / ps16k_arm64 / kb_test_device / audit_user_visible_20261016
```

- ps16k AVD 在盘只读核通过（`ps16k_x86_64.avd`/`.ini` 存在；本批任务书四步不含 KD-70/16KB，故 5556 未动）。
- 本批无设备环境故障：5554 全程可用，无 guest 楔死；唯一一次 `adb reboot` 是步骤三（E-03）的判据本体（冷启动），见 `step03-kd76-criteria.md`。
- 沉降纪律：步骤一/二/四/五均在沉降态设备执行（开工时 5554 已 up 3:54、load 0.03）；步骤三按判据要求**冷启动后不沉降**即跑（E-03 §2，红绿均按 R1 §4.5 归因树处理）。

## 3. 构建（只 assemble；release 用一次性本地 key）

```bash
./gradlew --console=plain :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest
# BUILD SUCCESSFUL in 36s；293 actionable tasks: 9 executed, 284 up-to-date；EXIT=0
```

```bash
./gradlew --console=plain :app:validateReleaseSigning
# EXIT=1；日志 build/device-track-batch26/validate-release-signing.log（18 行）
# > Release signing is missing required environment variables: RELEASE_KEY_ALIAS,
#   RELEASE_KEY_PASSWORD, RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD
```

```bash
keytool -genkeypair -keystore "D:/smart mistake book/build/device-track-batch26/release-smoke.jks" \
  -storepass <local-smoke> -keypass <local-smoke> -alias batch26-smoke \
  -keyalg RSA -keysize 2048 -validity 7 -dname "CN=Batch26 Local Smoke,O=Local,C=CN"
# EXIT=0（一次性本地 key；仅本地、被 .gitignore 忽略、不入仓）

RELEASE_KEY_ALIAS=batch26-smoke RELEASE_KEY_PASSWORD=<local-smoke> \
RELEASE_STORE_FILE="D:/smart mistake book/build/device-track-batch26/release-smoke.jks" \
RELEASE_STORE_PASSWORD=<local-smoke> \
./gradlew --console=plain :app:assembleStrictOfflineRelease
# BUILD SUCCESSFUL in 2m 1s；412 actionable tasks: 11 executed, 401 up-to-date；EXIT=0
```

**release 签名前置（与批次 24/25 同口径）**：本机无生产 `RELEASE_*` 变量；`validateReleaseSigning`
EXIT=1 是原始证据。本批 release 步证明 release 变体（R8 minify + shrinkResources）的打包与
冷启动，**不证明生产签名/证书轮换**；生产 release 签名 UNVERIFIED。

**测试 APK 内含新门用例的产物级核验**（本批新增，防"旧包跑新门"误读）：

```python
# python zipfile 实测 app-strictOffline-debug-androidTest.apk（dex 未压缩字符串池）
# classes3.dex: 'aHungWorkerReleasesTheCallerWithinHardPlus500ms' ×4、
#               'VisualJsHandshakeRaceInstrumentedTest' ×42；其余 dex 命中 0
```

## 4. 产物身份

| 产物 | 本机路径 | sha256 | versionName |
|---|---|---|---|
| app debug APK | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk`（189,366,584 B） | `758c0caf217f8bd3a3aaeba1c686b69c83947edce1b07fa115e9ffb8c58406c9` | 0.2.0-offline |
| app debug androidTest APK | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk`（1,585,488 B） | `cde7e27b9a6c0ab90db06f05e6f62465c729e220593b262980f374cc9becb293` | — |
| core:data androidTest APK | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | `94df5c3931db09170dc8ac52db3a074c281bc2b8869bdba3ced724e08dea8590`（与批次 25 逐字节一致，up-to-date 未重编） | — |
| app release APK（一次性本地 key） | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk` | `a4c1145145eb06e4e60281db229c7e0511106f6404c70db156a65b7e0ba2f432` | 0.2.0-offline |

设备侧 `pm path` + `sha256sum` 每步复核，与上表逐字节一致（各 step §2/§3：app `758c0caf…`、
test `cde7e27b…`、release `a4c11451…`）。

## 5. 数据保护

- 设备用途：专用测试模拟器（`test_device`；`ps16k_x86_64` 仅只读核）。
- 真实学习者数据：否。所有夹具为合成内容。
- 破坏性操作：
  1. E-03 的 `adb reboot`（判据本体）：丢弃易失内存态，userdata 持久分区保留；该机为本轨
     专用测试机，无用户数据。
  2. E-04 因签名不同先 `adb uninstall` 本批刚装的 debug 包再装 release；步骤结束后重装
     debug 三件套（三个 `install` 均 `Success`，见 `step04` §6）。
- 测试后检查：见各 step 判定节。

## 6. 脱敏与去噪

- 删除：Gradle 进度行与 ANSI 转义只留在 `build/device-track-batch26/` 原始日志；证据文件只摘判定行。
- 保留：命令、serial（模拟器）、AVD、API、ABI、页大小、APK 哈希、退出码、崩溃签名零计数。
- 物理设备 serial、用户名、主目录、密钥/口令一律未写入本目录；一次性 keystore 口令在本目录
  一律写作 `<local-smoke>`，不落明文（keystore 本体在 `build/`，不入仓）。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 7. 判定

PASS（信息性）。设备身份、四个 APK 哈希、构建 exit 码、签名前置失败证据、测试 APK 内含
新门用例的产物级核验均已固定；`connectedAndroidTest` 零次；git 写操作零次；产品/测试源代码
零改动（本轨只新增本证据目录与 `build/` 原始日志）。
