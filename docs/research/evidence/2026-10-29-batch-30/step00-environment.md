# 批次 30 设备环境（E-00）：设备再登记 / 构建产物身份 / dex 符号门

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-29-batch-30 |
| step | 0（设备再登记 + 构建 + 安装 + dex 门） |
| gate-or-kd | 判据文档 `docs/research/2026-10-29-batch-30-device-criteria.md` §5.0–§5.3 |
| verdict | **PASS**（设备五值登记；三包 + database 测试包重建、sha256 宿主=设备；dex 符号门两包 `DEX OK`） |
| executed-at | 2026-10-29T02:25:45+0800（设备时钟 UTC 18:25） |
| operator | 设备验证轨（subagent，批次 30） |
| head | `a806525eccd8918b2b7b6e1be3a7c02906858187` |
| worktree | dirty（他线 D-01/D-02/解析器在飞：`StudyDatabase.kt` version=70、`PdfWriteIntegrity.kt`/`VisualDrawOpsDuplicateKeys.kt` 等 `??`/M；本轨零写入） |
| device-serial | `emulator-5562`（test_device；**未触碰 `-5566`/`-5584`**） |
| apk | 见 §2 四行 |
| apk-version | 0.2.0-offline |
| exit-code | 构建两条 `EXIT=0`（`BUILD SUCCESSFUL`）；安装三条 `Success` |
| raw-log | `build/device-track-batch30/apk-host-sha256.txt`、`apk-device-sha256.txt`、`source-snapshot-before.txt`、`source-snapshot-after-build.txt` |
| record-ref | 批次 30 记录（后写） |
| sanitized | 是（无密钥/无学习者内容/无用户主目录；物理设备不适用） |

## 1. 设备五值（判据 §5.0）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A devices -l                         # emulator-5562/-5566/-5584 均 device 态
$A -s $S shell getprop ro.build.version.sdk        # 34
$A -s $S shell getprop ro.product.cpu.abi          # x86_64
$A -s $S shell getconf PAGE_SIZE                   # 4096
$A -s $S shell getprop ro.build.fingerprint        # google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys
$A -s $S shell cat /proc/uptime /proc/loadavg      # 14060.24 55188.92 / 0.00 0.02 0.00
```

- 设备已在批次 29 冷启动并长期在线，本轮 uptime 14060 s、1 分钟 load 0.00，**无需再等沉降**；
  与批次 29 同 serial / 同 API / 同 ABI / 同页大小，因此本批像素读数可与批次 29 **同设备**对照
  （仅限同一设备内对照，判据 §7.4-6）。
- `emulator-5566`（审计他线）、`emulator-5584`（kb_test_device，他线）未执行任何命令。

## 2. 构建与产物身份（判据 §5.1–§5.2）

```bash
./gradlew --console=plain \
  :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest          # BUILD SUCCESSFUL in 36s；EXIT=0
./gradlew --console=plain :core:database:assembleDebugAndroidTest   # BUILD SUCCESSFUL in 20s；EXIT=0
```

| 产物 | 字节 | sha256（宿主；设备侧 `pm path`+`sha256sum` 逐字节一致） |
|---|---|---|
| `app-strictOffline-debug.apk` | 189,509,308 | `1429ee0e41a20c269825172bc16cf5cf006e1525ff413748f2219fa946b04ad3` |
| `app-strictOffline-debug-androidTest.apk` | 1,586,361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| `data-debug-androidTest.apk` | 197,750,758 | `cb88851b828115f280061eb1b256cfa2f04111047576a8917ab62617ae4b1951` |
| `database-debug-androidTest.apk` | 196,810,446 | `c07669639e269eee7c64bfb2264aa44890cee56e9f7d172a455c3b74fa38d49b` |

- 三条 `adb install -r -t`（app / app-test / data-test）均 `Success`；database 测试包第四条亦 `Success`。
- **三包均非批次 29 旧包**：旧 sha256 为 `05a5b8bd…` / `229cd58e…` / `4d152b5a…`（判据 §1.2），
  本批全部不同；APK 文件时间 2026-10-29 02:26–02:27，晚于最后一次源改动（01:17:45）。
- **构建早于源改动的陷阱已排除**：构建前后对 `app/src`、`core/data/src`、`core/database/src`
  共 779 个文件做 sha256 快照，`diff` 为空（`SOURCE-UNCHANGED-DURING-BUILD`）。

## 3. dex 符号门（判据 §5.3，防旧包跑新门）

```bash
need() { apk="$1"; shift; for s in "$@"; do
  unzip -p "$apk" 'classes*.dex' | grep -a -q "$s" || { echo "MISSING [$s] in $apk"; return 1; }
done; echo "DEX OK: $apk"; }
```

```text
DEX OK: app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
DEX OK: core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
```

- app 测试包命中：`VisualJsIsolationInstrumentedTest`、`VisualMeasureReplayPaintFlagsInstrumentedTest`、
  `theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags`、
  `aFingerprintThatDisagreesWithTheActualPaintFailsClosed`、
  `anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled`。
- core:data 测试包命中：painter/PDF/parity 三套件类名 + `drawRenderedReplaysTheFrozenCommandSequence`、
  `drawRenderedFailsClosedOnMissingOrInvalidCommands`、
  `aStrokeOnlyPathWithAnOddDashReportsTheSameFirstGateAsTheGenerators`、
  `theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint`、
  `n08UniformInteriorColorCastStaysRedInsteadOfBeingDowngradedToF5`、
  `pdfWriteFailureMidStreamKeepsTheFirstFailureText`、`pdfFileWriteRoundTripsThroughReadback`、
  `pdfReadOnlyTargetFailsClosedBeforeAnyWrite`、`PdfWriteFailureTrackingStream`、`PdfWriteTelemetry`。
- 判据 §5.3 的"旧核心包会被拒绝"未在本轮重演（旧包已被覆盖重建），该条按判据 §1.2 的既有实测引用。

## 4. 现树计数（判据 §1.1，`grep -c '@Test'` 实测）

| 套件 | 计数 |
|---|---|
| `VisualDrawOpsPainterInstrumentedTest` | 24 |
| `VisualJsIsolationInstrumentedTest` | 13 |
| `VisualMeasureReplayPaintFlagsInstrumentedTest` | 2 |
| `VisualDrawOpsPdfPainterInstrumentedTest` | 16 |
| `VisualDrawOpsParityInstrumentedTest` | 11 |
| `BackupRestoreInstrumentedTest`（批次 30 未变） | 34 |
| `FullMigrationMatrixInstrumentedTest` | 15 |
| `TutorVisualSnapshotMigration_69_70InstrumentedTest` | 2 |
| `TutorVisualCommitInstrumentedTest` | 8 |
| `TutorVisualCommitRepositoryInstrumentedTest` | 3 |

## 5. 未验证项

- **UNVERIFIED**：`okio`-级别/16 KB 页、arm64/真机、生产签名、真实 Provider —— 不在本步面。
- 本文件只覆盖构建/安装/符号门；套件结果见 `step01`–`step07`。

## 6. 主机工具（R4 §5.2 补登，2026-10-29 本轨实跑）

| 工具 | 版本/路径 | 备注 |
|---|---|---|
| adb | `Android Debug Bridge version 1.0.41` / `Version 37.0.1-15733141` | `C:/Android/Sdk/platform-tools/adb.exe` |
| sha256sum | `GNU coreutils 8.32` | APK 身份与源快照 |
| zipalign | build-tools `36.0.0`、`37.0.0` 均在 | 本批**未做** 16KB ZIP 校验（不在套件范围） |
| llvm-readelf | **不存在**（`command -v llvm-readelf` 无输出） | ELF/16KB 面不在本批范围，不作判据（R4 §8.3） |

## 7. 脱敏与去噪（R4 §4.4）

- 扫描：目录级四条正则各 **0 命中**（命令与结果见 `00-index.md` §8）；补充命中只有长小数与跨轨文件的盘符绝对路径，均非秘密/PII。
- 去噪：只保留构建 summary、APK sha256、dex 门两行；未删除任何 `FATAL`/`ANR`/`chatty` 行（本文件无此类行）。
- 二进制/截图：无。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch30/`（`.gitignore:15` 忽略）
- 文件：`apk-host-sha256.txt`、`apk-device-sha256.txt`、`source-snapshot-before.txt`、`source-snapshot-after-build.txt`、`source-snapshot-end.txt`
- 说明：只作本机复查，**关闭判据只认本 `.md`**。
