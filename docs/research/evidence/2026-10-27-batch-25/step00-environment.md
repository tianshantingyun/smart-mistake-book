# E-00 环境、设备与产物身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-27-batch-25 |
| step | 步零（环境） |
| gate-or-kd | —（信息性） |
| verdict | PASS（信息性；含一次设备环境故障与恢复记录，见 §2.1） |
| executed-at | 2026-10-27T03:25:00+08:00 |
| operator | 设备验证轨（subagent，批次 25） |
| head | 25ddb60273e32ebd4100e8deb9f965bb0dfd4476 |
| worktree | dirty（他线在飞；本轨只新增本目录与 `build/device-track-batch25/` 原始日志） |
| device-serial | emulator-5554 / emulator-5556 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 主机工具

```bash
C:/Android/Sdk/platform-tools/adb.exe version
java -version
./gradlew --version
```

| 工具 | 路径/版本 | 备注 |
|---|---|---|
| adb | `C:/Android/Sdk/platform-tools/adb.exe`，1.0.41 / 37.0.1-15733141 | 只用 `install -r -t`、`am instrument`、`logcat`、`pm`、`emu kill`（恢复） |
| JDK | OpenJDK 21.0.11 LTS | Gradle / keytool / apksigner 共用 |
| Gradle | wrapper 9.6.1 | 只跑 `assemble*` 与 `validateReleaseSigning`，未跑 `connectedAndroidTest` |
| keytool | JDK 21 附带 | 本批一次性自签名 smoke key（§3） |

## 2. 设备

开工时四台模拟器在线（5554/5556/5562/5584）。本轨只使用其中两台；**5562/5584 属他线，全程未在其上执行任何命令**。

| serial | AVD | API | ABI | `getconf PAGE_SIZE` | 用途 |
|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | 隔离门（含 T1/T2/T3 新用例）/ R8 release 冒烟 / 备份抽验 |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | **16384** | KD-70 P2 重跑 + 16KB 装载门 |

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 emu avd name            # test_device
$A -s emulator-5554 shell getprop ro.build.version.sdk      # 34
$A -s emulator-5554 shell getprop ro.product.cpu.abi        # x86_64
$A -s emulator-5554 shell getconf PAGE_SIZE                 # 4096
$A -s emulator-5556 emu avd name            # ps16k_x86_64
$A -s emulator-5556 shell getprop ro.build.version.sdk      # 36
$A -s emulator-5556 shell getconf PAGE_SIZE                 # 16384
```

### 2.1 设备环境故障与恢复（如实记录；两台目标机同时受累）

开工只读核通过后、首次 `install` 时，两台目标模拟器**先后出现同一类 guest 故障**：`pm`/`cmd` 返回
`Can't find service: package`（5554 于 03:05 首次命中，5556 于 03:18 命中）。

诊断证据（`build/device-track-batch25/` 原始日志外的实机输出，判定行摘录）：

- 5554 crash buffer：`*** FATAL EXCEPTION IN SYSTEM PROCESS: main:
  java.lang.RuntimeException: failed to set system property`（`SystemServer.run(SystemServer.java:779)`，
  设备时 18:34:14）；events `boot_progress_start` 连续 5 次（设备时 18:26/18:35/18:51/19:01/19:14）
  ——**system_server 反复重启 ≥5 次**。
- 5556 同类：`com.android.phone` 报 `DeadSystemException`（设备时 18:53），crash_dump helper
  失败每约 5.5 min 一条，`boot_progress_start` 19:01/19:07/19:12/19:18 连续 4 次。
- 客户内 `adb reboot` 返回 `Success` 但 guest `uptime` 未复位（10:36 连续）→ **init/property 层
  楔死，客户内重启无效**（与 `failed to set system property` 致命错同源）。
- 宿主资源同时段健康（64 GB 总内存 / 10.7 GB 空闲，CPU 25%）→ **非宿主资源饥饿**；
  楔死根因未深挖（两台 guest 长时间在飞（≥10.5 h uptime）+ 多轨共用宿主），按环境故障处置。

恢复动作（宿主侧，逐台执行）：

```bash
$A -s emulator-5554 emu kill        # OK: killing emulator, bye bye
emulator.exe -avd test_device   -port 5554 -no-snapshot -no-boot-anim -gpu auto   # 冷启动
$A -s emulator-5556 emu kill
emulator.exe -avd ps16k_x86_64  -port 5556 -no-snapshot -no-boot-anim -gpu auto
```

冷启动后复核（两台均 `up 1 min`、`pm path android` 正常返回、身份三值与上表一致）。
按本批环境约束"冷启动后先沉降再跑"：5554 首次安装前沉降 **150 s**；5556 首次安装前同样
等待 boot 完成后再执行。`-no-snapshot` 只丢弃 quickboot 快照（易失状态），userdata 持久分区保留；
此前批次/他线安装的包不在本轨处置范围（本轨只覆盖安装自己点名的产物，见 §3/各 step）。
emulator-5554/5556 的冷启动控制台日志：`build/device-track-batch25/emulator-5554-coldboot.log`、
`emulator-5556-coldboot.log`（本机原始日志，不入仓）。

## 3. 构建（只 assemble；release 用一次性本地 key）

```bash
./gradlew --console=plain :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest
# BUILD SUCCESSFUL in 45s；293 actionable tasks: 15 executed, 278 up-to-date；exit=0
```

```bash
./gradlew --console=plain :app:validateReleaseSigning
# exit=1；日志 build/device-track-batch25/validate-release-signing.log
# > Release signing is missing required environment variables: RELEASE_KEY_ALIAS,
#   RELEASE_KEY_PASSWORD, RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD
```

```bash
keytool -genkeypair -keystore "D:/smart mistake book/build/device-track-batch25/release-smoke.jks" \
  -storepass <local-smoke> -keypass <local-smoke> -alias batch25-smoke \
  -keyalg RSA -keysize 2048 -validity 7 -dname "CN=Batch25 Local Smoke,O=Local,C=CN"
# exit=0（一次性本地 key；仅本地、被 .gitignore 忽略、不入仓）

RELEASE_KEY_ALIAS=batch25-smoke RELEASE_KEY_PASSWORD=<local-smoke> \
RELEASE_STORE_FILE="D:/smart mistake book/build/device-track-batch25/release-smoke.jks" \
RELEASE_STORE_PASSWORD=<local-smoke> \
./gradlew --console=plain :app:assembleStrictOfflineRelease
# BUILD SUCCESSFUL in 3m 11s；412 actionable tasks: 68 executed, 344 up-to-date；exit=0
```

**release 签名前置（与批次 24 同口径）.** 本机环境**没有**生产 `RELEASE_*` 变量；
`validateReleaseSigning` exit=1 是原始证据。因此本批 release 步证明的是 release 变体
（R8 minify + shrinkResources）的打包与冷启动，**不证明生产签名/证书轮换**。生产 release 签名
仍 UNVERIFIED。

## 4. 产物身份

| 产物 | 本机路径 | 字节 | sha256 | versionName |
|---|---|---|---|---|
| app debug APK | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | 见 build | `1af0f99af2e2bf3182f4838357ad2e5a93568ac4a33439524ffbde6214e7b737` | 0.2.0-offline |
| app debug androidTest APK | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | 见 build | `6623b0acebb3cca4bcf891051d0f5a41641d3d7e6c45750fcbac2d5302ec600c` | — |
| core:data androidTest APK | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | 见 build | `94df5c3931db09170dc8ac52db3a074c281bc2b8869bdba3ced724e08dea8590` | — |
| app release APK（一次性本地 key） | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk` | 155,699,091 | `824cd5b2e93eecbaeb6bf8695e780ba37c674cc99f0a8a76790d4b03bfb3735f` | 0.2.0-offline |

（debug 三件套的字节数未在开工表重复罗列，以 sha256 为准；设备侧 `pm path`+`sha256sum` 复核见各 step。）

测试 APK 内含 `lib/x86_64/libquickjs.so` 等 4 ABI × 7 库（python zipfile 实测，含 arm64-v8a/
armeabi-v7a/x86/x86_64），ps16k 上的 x86_64 切片装载链路前置成立。

## 5. 数据保护

- 设备用途：专用测试模拟器（`test_device`、`ps16k_x86_64`）。
- 真实学习者数据：否。所有夹具为合成内容。
- 破坏性操作：
  1. §2.1 的 `emu kill` + 冷启动：丢弃两台 guest 的 quickboot 快照（易失内存态），保留 userdata；
     两台为本轨专用测试机，无用户数据。
  2. release 步因签名不同，先 `adb uninstall com.tingyun.smartmistakebook.offline` 再装 release
     （该包为本批刚装的 debug 包，非用户数据）；步骤结束后重装 debug 包（见 `step04` §7）。
- 测试后检查：见各 step 判定节。

## 6. 脱敏与去噪

- 删除：Gradle 进度行与 ANSI 转义只留在 `build/` 原始日志；证据文件只摘判定行。
- 保留：命令、serial（模拟器）、AVD、API、ABI、页大小、APK 哈希、退出码、故障诊断判定行。
- 物理设备 serial、用户名、主目录、密钥/口令一律未写入本目录；一次性 keystore 的口令在本目录
  一律写作 `<local-smoke>`，不落明文。
- 扫描：见 `00-index.md` §6（本目录写盘后统一跑）。

二进制/截图：无。

## 7. 判定

PASS（信息性）。两台目标设备身份、四个 APK 的哈希、构建 exit 码与签名前置失败证据均已固定；
设备环境故障（§2.1）已按"宿主侧冷启动"恢复并在每个 step 的执行前复核身份。恢复动作造成的
guest 快照丢弃只影响易失状态，不构成本批任何判据的削弱；各 step 判据均在恢复后的设备上实测。
