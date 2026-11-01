# E-00 环境与设备身份（批次 31 设备轨）

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 步 0（设备再登记）+ 步 1（构建）+ 步 2–3（安装 / dex 符号门 / schema 资产门） |
| gate-or-kd | 设备判据 §4.0–§4.3；KD-80 关闭门的产物身份前提 |
| verdict | PASS（信息性 + 产物身份；不参与功能门判定） |
| executed-at | 2026-10-29T06:26:20–06:30:41+0800（logcat 为设备 UTC，本机实测设备约 +2 s 偏差） |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f92ab27314d25384a777f41829b3421a2` |
| worktree | dirty（他线在飞；本轨零写入主源/测试/热区） |
| device-serial | `emulator-5562`（模拟器，保留） |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | `x86_64` |
| device-page-size | 4096 |
| apk | 见 §3（四包，仓库相对路径） |
| apk-sha256 | 见 §3 |
| apk-version | `0.2.0-offline`（app）/ 测试包 versionName 空 |
| exit-code | 构建 `EXIT=0`；四条 install 均 `RC=0`；dex 门 `EXIT=0` |
| raw-log | `build/device-track-batch31/step00-build.log`（425 行 / 27004 B）· `step02-install.log` · `step02-install-verify.log` · `step03-dex-gate.log` · `src-snapshot-before.txt` / `-after.txt` |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（记录员后写） |
| sanitized | 是（§6；本文件 §4.4 扫描 0 命中；目录级唯一命中是 `00-index.md` §6 自身的正则文本，非真实路径） |

## 1. 目的与判据

固定"在哪个设备、跑哪一份 APK"：设备判据 §4.0 要求 device 态 + 五值进证据头；
§4.1 要求构建 `EXIT=0` 且构建前后源文件 sha256 快照一致；§4.3 要求三个测试包 dex 符号门全
`DEX OK`，且矩阵资产门 `assets` 中 schema 数 == `STUDY_DATABASE_VERSION`。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562

# 设备登记
$A devices -l
$A -s $S shell getprop ro.build.version.sdk
$A -s $S shell getprop ro.product.cpu.abi
$A -s $S shell getconf PAGE_SIZE
$A -s $S shell getprop ro.build.fingerprint
$A -s $S shell cat /proc/uptime /proc/loadavg
$A -s $S emu avd name

# 构建（只 assemble*，禁 connectedAndroidTest）
./gradlew --console=plain \
  :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest

# 构建前后源快照（构建前写 before，构建后写 after）
for d in app/src core/data/src core/database/src; do find "$d" -type f | sort | xargs sha256sum; done > build/device-track-batch31/src-snapshot-before.txt
# 构建完成后再执行同一条命令，输出重定向到 build/device-track-batch31/src-snapshot-after.txt

# 安装
$A -s $S install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
# …另三包同格式

# 设备侧哈希核对（依次替换包名为 .offline / .offline.test /
# com.tingyun.smartmistakebook.core.data.test / com.tingyun.smartmistakebook.core.database.test）
$A -s $S shell sha256sum "$($A -s $S shell pm path com.tingyun.smartmistakebook.offline | tr -d '\r' | sed 's/^package://')"

# dex 符号门 / schema 资产门
bash build/device-track-batch31/dex-gate.sh
```

退出码：构建 `EXIT=0`（`BUILD SUCCESSFUL in 37s`，327 tasks / 17 executed）；install 四包 `RC=0`；
dex 门 `EXIT=0`；设备侧 sha256 四包与宿主逐字节相同。

## 3. 设备与产物身份

| 项 | 值 |
|---|---|
| `adb version` | 1.0.41 / 37.0.1-15733141 |
| 在线设备 | `emulator-5562`(test_device)、`emulator-5566`(他线)、`emulator-5584`(他线，未触碰) |
| API / ABI / PAGE_SIZE | 34 / `x86_64` / 4096 |
| fingerprint | `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` |
| 冷启动沉降 | uptime 28479.31 s；loadavg 0.02 0.02 0.00 |
| `ro.product.cpu.page.size` | `<empty>`（R4 §8.3：空值原样记录，不判失败） |
| `ro.product.page.size` | `<empty>` |
| 构建前源快照 | 859 个文件，清单 sha256 `74141d3226069385562d5f69717442ec10f174278f45fd442b05886878155e42` |
| 构建后源快照 | 同一 sha256（`diff` 空）→ 构建未早于源改动 |

| APK | 字节 | sha256（宿主＝设备） |
|---|---|---|
| `app-strictOffline-debug.apk` | 189525382 | `a3973a7a584e425ebaa1dc1e07f0cb96fb799fc80b2fe5111963691434ea42cc` |
| `app-strictOffline-debug-androidTest.apk` | 1586361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| `data-debug-androidTest.apk` | 197763509 | `34151d4d9571daffc27e6a370172afb6775b7e730742dc3484ffd5fdb2e7b952` |
| `database-debug-androidTest.apk` | 196823330 | `4e54f0de476712add5bc839dec16a419b3927a4fdfc27bf41beca4e290c7da2d` |

包名（`output-metadata.json` + `pm list instrumentation` 实测）：
`com.tingyun.smartmistakebook.offline` / `.offline.test` /
`com.tingyun.smartmistakebook.core.data.test` / `com.tingyun.smartmistakebook.core.database.test`，
四者 runner 均为 `androidx.test.runner.AndroidJUnitRunner`。

**与批次 30 的差异**：app 包 `a3973a7a…`（批 30 为 `1429ee0e…`）、data 测试包 `34151d4d…`
（批 30 为 `cb88851b…`）、database 测试包 `4e54f0de…`（批 30 为 `c0766963…`）；app 测试包
`2dd22651…` 与批 30 相同（该源集本轮无改动，`app/src/androidTest` 未落在本批 T3/T4 落码面）。

## 4. 关键输出（有界）

```text
$A devices -l
emulator-5562  device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa
emulator-5566  device ...        # 他线
emulator-5584  device ...        # 他线，未触碰

ro.build.version.sdk=34
ro.product.cpu.abi=x86_64
PAGE_SIZE=4096
ro.build.fingerprint=google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys
28479.31 111311.10
0.02 0.02 0.00 1/1759 8694

> Task :app:assembleStrictOfflineDebug
BUILD SUCCESSFUL in 37s
327 actionable tasks: 17 executed, 310 up-to-date

== install app-strictOffline-debug.apk        → Performing Streamed Install / Success / RC=0
== install app-strictOffline-debug-androidTest.apk → Success / RC=0
== install data-debug-androidTest.apk        → Success / RC=0
== install database-debug-androidTest.apk    → Success / RC=0

DEX OK: app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
DEX OK: core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
DEX OK: core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
```

dex 符号门收录 = 判据 §4.3 的三段清单（本轨对 `dex-gate.sh` 复核计数：app **5** 个符号 / data **19** 个符号 / database **14** 个符号），
含本批新增 `TutorVisualStructureGatesInstrumentedTest` 四方法与 `snapshotIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite`、
`stageThenCommitStillDemotesThePreviousLatestCheckpoint`、`n09OnePixelTextShiftIsCaught`–`n12WholeBlockShiftBeyondToleranceIsCaught`。

**schema 资产门**（矩阵按 assets 建库）：

```text
V=70
schemas_in_apk=70
HAS_70.json=YES
assets/…StudyDatabase/69.json, 70.json 均在包内
```

**构建前计数复读**（判据 §1.3;`grep -c '@Test'`）：34 / 4 / 15 / 24 / 13 / 2 / 16 / 15；
补充套件 9 / 4 / 4；trimmer 9。与判据文档快照逐项相同，无 in-flight 漂移。

## 5. 失败 / 跳过 / 异常

无。四包 `Success`；无 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`；未执行 `adb root`；
未执行 `connectedAndroidTest`；Gradle 只跑四条 assemble 任务。

## 6. 脱敏与去噪

- 删除：重复进度行（`Performing Streamed Install` 之外无）；未删任何 `FATAL`/`ANR`/`chatty` 行（本文件无此类行）。
- 保留：设备 serial/AVD/API/ABI/PAGE_SIZE/fingerprint、APK 字节与 sha256、退出码。
- 扫描（R4 §4.4，对本批证据目录）：

```text
secrets 0 hits / credential assignments 0 hits / emails 0 hits
home paths：本文件 0；目录级唯一命中在 00-index.md §6 自身的正则文本（非真实主目录）
```

- 二进制/截图：无。APK 本体不入仓，只记字节与 sha256。

## 7. 判定

PASS（信息性）：设备为 `emulator-5562` / API 34 / `x86_64` / 4096，四包身份与宿主逐字节一致，
源快照构建前后一致，dex 与 schema 资产门通过。**未覆盖**：真机 / arm64 / 16 KB 页 / 生产签名
（判据 §7 明列不在本判据面）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/{step00-build.log,step02-install.log,step02-install-verify.log,step03-dex-gate.log,src-snapshot-before.txt,src-snapshot-after.txt}`
- 行数/大小：构建 425 行 / 27004 B；快照各 859 行
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
