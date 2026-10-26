# E-00 环境、设备与产物身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-26-batch-24 |
| step | 步零（环境） |
| gate-or-kd | —（信息性） |
| verdict | PASS（信息性，不参与设备门判定） |
| executed-at | 2026-10-26T21:31:00+08:00 |
| operator | 设备验证轨（subagent，批次 24） |
| head | 5bc8bdf868e1296049ab901cf1da69e136062006 |
| worktree | dirty（219 M + 480 ??；他线在飞，本轨只新增本目录） |
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
| adb | `C:/Android/Sdk/platform-tools/adb.exe`，1.0.41 / 37.0.1-15733141 | 只用 `install -r -t`、`am instrument`、`logcat`、`pm` |
| JDK | OpenJDK 21.0.11 LTS | Gradle 与 `apksigner` 共用 |
| Gradle | wrapper 9.6.1 | 只跑 `assemble*`，未跑 `connectedAndroidTest` |
| apksigner | `C:/Android/Sdk/build-tools/37.0.0/apksigner.bat` | 本批只用于 release 冒烟包的 V2 证书打印 |

本批未使用 `llvm-readobj` / `llvm-readelf` / `zipalign`：16KB 步只做**运行时装载**，静态 ELF 对齐沿用批次 23 E-03/E-09（不重复声明）。

## 2. 设备

| serial | AVD | API | ABI | `getconf PAGE_SIZE` | `ro.product.cpu.page.size` | 用途 |
|---|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | `<empty>` | 隔离门 / KD-70 / release 冒烟 / 备份迁移 |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | **16384** | `<empty>` | 16KB 运行时门 |

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

`adb devices -l` 另列 1 台物理设备与 `emulator-5562` / `emulator-5584`（他线在飞）；本轨**未**在其上安装或执行任何命令，物理 serial 按规范 §4.1 不落盘。开工时两台目标模拟器已启动（uptime 4h41m / 5h21m，load < 0.3），未重启、未冷启；批次 23 的"冷启动后先沉降 2 min"约束只在其冷启场景适用。

## 3. 构建（只 assemble）

```bash
./gradlew --console=plain :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest
# BUILD SUCCESSFUL in 1m 11s；327 actionable tasks: 15 executed, 312 up-to-date
```

```bash
RELEASE_KEY_ALIAS=batch24-smoke RELEASE_KEY_PASSWORD=batch24-smoke \
RELEASE_STORE_FILE="D:/smart mistake book/build/device-track-batch24/release-smoke.jks" \
RELEASE_STORE_PASSWORD=batch24-smoke \
./gradlew --console=plain :app:assembleStrictOfflineRelease
# BUILD SUCCESSFUL in 7m 8s；412 actionable tasks: 105 executed, 307 up-to-date
```

**release 签名前置（重要且如实）.** 本机环境**没有**生产 `RELEASE_*` 变量；`./gradlew :app:validateReleaseSigning` 在 2 s 内失败，明确列出四个缺失变量（原始日志 `build/device-track-batch24/validate-release-signing.log:22`）。为了执行任务书点名的 release 冒烟，本轨在 `build/device-track-batch24/` 下生成一次性自签名 key（CN=`Batch24 Local Smoke`，仅本地、被 `.gitignore` 忽略、不入仓），通过环境变量注入后构建。**因此本步证明的是 release 变体（R8 minify + shrinkResources）的打包与冷启动，不证明生产签名/证书轮换。** 生产 release 签名仍 UNVERIFIED。

## 4. 产物身份

| 产物 | 本机路径 | 字节 | sha256 | versionName |
|---|---|---|---|---|
| app debug APK（E-01 用，21:31 构建） | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | 189,363,367 | `53c3a453219f10352535f4b2e8d4e3b7e4f1aa4dfadd31b089d90a8328026eba` | 0.2.0-offline |
| app debug APK（E-01b 用，21:37 他线重建件；最终安装件） | 同上路径（已被重建覆盖） | 189,363,334 | `782cebd66eb12c8656ccc5fc518600cdf63ddd8572f7a6908d143e31930173c6` | 0.2.0-offline |
| app debug androidTest APK | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | 1,564,771 | `343b637af805798ac1dfadfe8f2ebd0c2a59f5aec390c9b6a3e8f35760e08c0e` | — |
| app release APK（本轨一次性签名） | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk` | 155,699,070 | `597129283c4d83c3a320bf3dbe963a1dce32297b5a78caec448b618943c180a7` | 0.2.0-offline |
| core:data androidTest APK | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | 193,441,519 | `8ff654e95686ea5ef89a73cc797f6ae7688c720877b01a50700dff6d5d605281` | — |
| core:database androidTest APK | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` | 196,665,032 | `1196dd93abdd781f23fb92b42eee1c876283fcb0b852ff1fb5d98a0e3b33dba6` | — |
| release mapping.txt（不提交本体） | `app/build/outputs/mapping/strictOfflineRelease/mapping.txt` | 123,742,810 | `10f6d4cfe2bd8386ef1c88df706ba45b91b7c0a327221f482e78191e2eac7671` | — |

设备侧复核（`pm path` + `sha256sum`）：

- `emulator-5554` 上 `com.tingyun.smartmistakebook.offline` 的 `base.apk`：E-01 时为 `53c3a453…6eba`，E-01b 与最终安装件为 `782cebd6…73c6`（21:37 他线重建），release 步为 `59712928…80a7`，均与当时盘上产物逐字节一致；
- `com.tingyun.smartmistakebook.offline.test` = `343b637a…0c0e`，与产物一致；
- `emulator-5556` 上 `com.tingyun.smartmistakebook.core.data.test` = `8ff654e9…5281`，与产物一致。

release 包签名：`apksigner verify --print-certs` → `V2 Signer: certificate DN: CN=Batch24 Local Smoke, O=Local, C=CN`，证书 SHA-256 `b1dfab60055f53111fcc8188b31825980b54765aca5c6f95cfa4ab91906393fd`。**这是本轨一次性 key，不是生产发布证书。**

## 5. 数据保护

- 设备用途：专用测试模拟器（`test_device`、`ps16k_x86_64`）。
- 真实学习者数据：否。所有夹具为合成内容；未读取、导出任何真实学习者数据。
- 破坏性操作：
  1. release 步因签名不同，先 `adb uninstall com.tingyun.smartmistakebook.offline` 再安装 release；该包是本批刚安装的 debug 包，非用户数据。步骤结束后已重装 debug 包（§6）。
  2. 迁移演练使用 `-e smb_allow_data_reset true` 显式 opt-in，只清测试 APK `com.tingyun.smartmistakebook.core.data.test` 的数据目录。
- 测试后检查：两个测试 APK 仍安装；debug 主包已恢复安装（见 `step03-r8-release-smoke.md` §8）。

## 6. 脱敏与去噪

- 删除：Gradle 进度行与 ANSI 转义只留在 `build/` 原始日志；证据文件只摘判定行。
- 保留：命令、serial（模拟器）、AVD、API、ABI、页大小、APK 哈希、退出码。
- 物理设备 serial、用户名、主目录、密钥/口令一律未写入本目录；一次性 keystore 的路径只出现 `build/device-track-batch24/`（仓库内相对路径），口令不落盘。
- 扫描：见 `00-index.md` §6（本目录写盘后统一跑，0 命中）。

二进制/截图：无。

## 7. 判定

PASS（信息性）。两台目标设备身份、五个 APK 与 release mapping 的哈希、设备侧哈希与 release 证书均已固定；release 使用一次性本地 key 的限制已在 §3 写明，不用它冒充生产签名验证。
