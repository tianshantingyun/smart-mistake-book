# E-01 构建 → 安装 → dex/schema 资产门（硬门：必须先重建重装）

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-29-batch-34 |
| step | 一（重建重装 + dex 符号门） |
| gate-or-kd | 设备判据 §2（硬门）、§4.2/§4.3 |
| verdict | PASS |
| executed-at | 2026-10-29T18:42:48+08:00 → 18:44:40+08:00 |
| operator | 设备验证轨（子代理） |
| head | `db792fa873f4bc150f415b67d1c2e3b1bab6c995` |
| worktree | 721 条（241 `M` / 480 `??`；本轨零 git 写） |
| device-serial | `emulator-5562` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| exit-code | 0（构建）/ 0（四次 install） |
| raw-log | `build/device-track-batch34/src-sha-before.txt`、`src-sha-after.txt`（本机，不入仓） |
| record-ref | `docs/research/2026-10-29-batch-34-record.md` §4（记录员后写） |
| sanitized | 是（§6） |

## 1. 目的与判据

批次 33 的设备绿轮构建于 14:44–15:13，现树已有多个测试文件不同（仓库 11→12 例、提交/结构门/retention
文件改动），且本批新增 `BackupRowCountsCoverageInstrumentedTest`。**旧 APK 物理上不含新符号**，
因此本步必须先构建、再安装、再做符号门；任一门失败即停，不得拿旧包跑。

## 2. 命令（可复跑）

构建前一致性门：

```bash
cd "D:/smart mistake book"
export MSYS_NO_PATHCONV=1
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
P=$(sed -n 's/.*const val SCHEMA_VERSION: Int = \([0-9][0-9]*\).*/\1/p' \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt | head -1)
echo "V=$V P=$P"
test -f core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json
git ls-files --error-unmatch \
  core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json
grep -q "VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71" \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
```

源快照 + 构建 + 构建后 diff：

```bash
D=build/device-track-batch34; mkdir -p "$D"
find app/src core/data/src core/database/src -type f -print0 | sort -z \
  | xargs -0 sha256sum > "$D/src-sha-before.txt"

./gradlew --console=plain \
  :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest \
  :core:database:assembleDebugAndroidTest

find app/src core/data/src core/database/src -type f -print0 | sort -z \
  | xargs -0 sha256sum > "$D/src-sha-after.txt"
diff -u "$D/src-sha-before.txt" "$D/src-sha-after.txt" && echo "SOURCE STABLE"
```

安装与设备侧字节核对：

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
S=emulator-5562
$A -s "$S" install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s "$S" install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s "$S" install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s "$S" install -r -t core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
for p in com.tingyun.smartmistakebook.offline com.tingyun.smartmistakebook.offline.test \
         com.tingyun.smartmistakebook.core.data.test com.tingyun.smartmistakebook.core.database.test; do
  path=$($A -s "$S" shell pm path "$p" | tr -d '\r' | sed 's/^package://')
  $A -s "$S" shell sha256sum "$path"
done
```

schema 资产门 + dex 符号门：

```bash
APK=core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
unzip -l "$APK" | grep -E 'StudyDatabase/[0-9]+\.json$' | wc -l          # 71
unzip -p "$APK" "assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json" | sha256sum
unzip -p "$APK" 'classes*.dex' | grep -a -c "预检淘汰集必须包含在飞行 owner 的行"   # 1
# 其余符号见 §4 表
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| 构建前 `V` / `P` | `71` / `71` | 必须相等 |
| `71.json` 存在且被 git 跟踪 | 是 | 必须 |
| 迁移常量 `VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71` | 存在 | 必须 |
| 源快照文件数（before / after） | 864 / 864 | — |
| `diff src-sha-before src-sha-after` | 空（`SOURCE STABLE`） | 必须空 |
| 构建退出码 | `0`；`BUILD SUCCESSFUL in 42s`；`327 actionable tasks: 19 executed, 308 up-to-date` | 必须 0 |
| 四次 `install -r -t` | 均 `Success` | 必须全 Success |
| 设备侧 sha256 与宿主 | 4/4 逐字节一致 | 必须一致 |
| `71.json` asset 存在 / schema 数 | 是 / 71 | 必须 71 |
| dex 符号门（3 个测试 APK） | 全 `DEX OK` | 任一 MISSING 即停 |

## 4. 关键输出（有界）

构建前的门：

```text
V=71 P=71
71.json exists
core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json
71.json tracked
migration const present
```

构建尾部：

```text
> Task :core:database:assembleDebugAndroidTest
> Task :core:data:assembleDebugAndroidTest
> Task :app:assembleStrictOfflineDebug
> Task :app:assembleStrictOfflineDebugAndroidTest UP-TO-DATE
BUILD SUCCESSFUL in 42s
327 actionable tasks: 19 executed, 308 up-to-date
BUILD_EXIT=0
```

产物身份（宿主 `sha256sum` + `stat` + `aapt2 dump badging`）：

| APK | 字节 | sha256 | versionName | mtime |
|---|---:|---|---|---|
| `app-strictOffline-debug.apk` | 189,547,386 | `ead9d4a43c99e2345abb4dc1709005c42225e66e1467169d043bd599def60cad` | `0.2.0-offline` | 18:43:40 |
| `app-strictOffline-debug-androidTest.apk` | 1,586,361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` | （空） | 02:27:04 |
| `data-debug-androidTest.apk` | 197,787,221 | `53e69f485a4bb4fe3d5c7bdff53ebbfb50008011376df1cbc133e4a946397caf` | （空） | 18:43:38 |
| `database-debug-androidTest.apk` | 196,846,909 | `f75d6fc8b036942a8408b21dff1b6626bc1882e16bd399a48fdb87e70e27b116` | （空） | 18:43:38 |

**app 测试 APK 的 `UP-TO-DATE` 说明（诚实登记）**：该包 mtime 为 02:27:04、sha 与批次 33
`00-index.md` §2 的 `2dd22651…` 完全相同，Gradle 报告 `packageStrictOfflineDebugAndroidTest UP-TO-DATE`。
本步用 `find app/src/androidTest app/src/main -type f -newermt "2026-10-29 02:27:04"` 实跑，**输出为空**，
即 app 测试源与主源自该包生成后未改动；`compileStrictOfflineDebugAndroidTestKotlin` 本轮执行但输出内容
未变，故打包任务判定 up-to-date。结论：包内容对应当前源；仍执行了 `install -r -t`（重装成功）。
`data` / `database` 两个测试包（本批新增/补强符号所在）为 18:43 重新打包，sha 与批次 33 不同。

设备侧 `pm path` + `sha256sum`：

| 包 | 设备 sha256 | 与宿主 |
|---|---|---|
| `com.tingyun.smartmistakebook.offline` | `ead9d4a4…def60cad` | 一致 |
| `com.tingyun.smartmistakebook.offline.test` | `2dd22651…9d950d19` | 一致 |
| `com.tingyun.smartmistakebook.core.data.test` | `53e69f48…e946397caf` | 一致 |
| `com.tingyun.smartmistakebook.core.database.test` | `f75d6fc8…e27b116` | 一致 |

schema 资产门：

```text
71                                   # data 测试 APK 内 StudyDatabase/*.json 数量
assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json   378961 B
4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94  (APK 内 asset)
4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94  core/database/schemas/.../71.json
```

dex 符号门（命令见 §2；`classes*.dex` 内逐符号 grep）：

```text
DEX OK: app-strictOffline-debug-androidTest.apk
DEX OK: data-debug-androidTest.apk
DEX OK: database-debug-androidTest.apk
预检淘汰集必须包含在飞行 owner 的行 = 1
```

覆盖符号：app 包 `VisualJsIsolationInstrumentedTest` / `VisualMeasureReplayPaintFlagsInstrumentedTest` /
两例 flags 方法名；data 包 `BackupRestoreInstrumentedTest`、`schema68ArchiveRestoresAndMigratesToCurrentVersion`、
`BackupRowCountsCoverageInstrumentedTest` + 两个新方法名、`BackupSnapshotTrimmerInstrumentedTest`、
`BackupVisualReferenceInstrumentedTest` + 5 个方法名、painter/pdf/parity、仓库 12 例（含
`stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite`）与 plan 淘汰集中文字符串；
database 包迁移矩阵/69-70/70-71/结构门 5 例/提交 12 例（含 `sceneDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`）。

## 5. 失败 / 跳过 / 异常

无。构建 0 失败；四次安装 0 失败；dex/schema 门 0 MISSING。

## 6. 脱敏与去噪

- 删除：Gradle 输出的 deprecation warning 与无关任务行；保留 `BUILD SUCCESSFUL`/任务计数/退出码。
- 保留：全部 sha256、字节数、versionName、设备身份。
- 未保留：本机临时日志目录（已省略，非证据）。
- 扫描：见 `00-index.md` §6 的 §4.4 四条正则 → 0 命中。

## 7. 判定

**PASS**。硬门成立：构建前 schema/常量一致、构建 `exit=0` 且源快照前后相同、四包安装成功且设备侧
sha 与宿主逐字节一致、测试 APK 内含 `71.json`（sha 与仓库权威源相同）、三个测试 APK 均含本批新增/
补强符号。未覆盖项：app 测试 APK 为内容等价的 up-to-date 包（§4 已说明，非旧源）。

## 8. 复核更正（2026-10-29 复核修复轮）：源快照 find 集合缺 `core/model/src`

§2/§3 的 `find app/src core/data/src core/database/src …` **没有包含 `core/model/src`**，而本批
A-02/A-03 与 `FigureGenerationPolicy` 的改动都在 model 模块，且会编进 app APK / data 测试 APK。
因此"864 文件前后相同"只覆盖 app/data/database 三个源集，**不能**据此声称 model 模块在构建期间
未漂移；A-01~A-03 的"同一源/同一 APK"证据对 model 模块不成立。

复核时实跑的事后核对（只证明"构建后没有新改动"，**不是构建时门**）：

```text
$ grep -c "core/model" build/device-track-batch34/src-sha-after.txt
0
$ wc -l build/device-track-batch34/src-sha-after.txt
864 build/device-track-batch34/src-sha-after.txt
$ find core/model/src -type f -newermt "2026-10-29 18:43:40" -print | wc -l
0
$ find app/src core/model/src core/data/src core/database/src -type f | wc -l
973
```

结论：本批**恰好**没有在构建后改动 model 源，但上述门本身不覆盖它。后续构建门必须把 find 集合
扩为 `app/src core/model/src core/data/src core/database/src`（判据文档 §2 已同步改写）；若要求
严格口径，需在扩集合后重建并重取四包 sha。

