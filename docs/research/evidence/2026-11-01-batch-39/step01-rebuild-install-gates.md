# E-01 重建重装 + 源快照门（含 core/model）+ dex 符号门 + asset 门

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39 |
| step | 步骤一（重建重装硬门，先于一切套件命令） |
| gate-or-kd | 批次 39 设备硬门（判据沿批次 37 判据 §四 `2026-10-31-batch-37-device-criteria.md:342-451` + 批 38 E-01 实证） |
| verdict | PASS（四包全链：一致性门→源快照→构建→快照 diff=0→sha→安装 4×Success→设备 sha 逐字节一致→asset 门→dex 门） |
| executed-at | 2026-11-01T10:16–10:33+08:00 |
| operator | 设备验证轨（批次 39 工作流子代理） |
| head | `a0641bb7392f380b5716afe95dd3c371dd0c7670` |
| worktree | 737 条（仅记录） |
| device-serial | emulator-5562（模拟器，可保留） |
| device-avd / api / abi / page-size | test_device / 34 / x86_64 / 4096（E-00） |
| apk | 见 §4 四包（本机路径，`<repo>` = 仓库根） |
| apk-sha256 | 见 §4 |
| apk-version | strictOffline debug（app）/ debug androidTest（三库模块） |
| exit-code | BUILD_EXIT=0；4×install exit=0；全部 need() 无 MISSING |
| raw-log | `build/device-track-batch39/{build.log,build-exit.txt,src-sha-before.txt,src-sha-after.txt,src-snapshot.diff,apk-sha256.txt}`（本机，不入仓；build.log 419 行） |
| record-ref | `docs/research/2026-11-01-batch-39-record.md`（记录员落笔位） |
| sanitized | 是（见本文件 §8） |

## 1. 目的与判据

证明本批设备结论绑定**当前树**（HEAD=`a0641bb7` + 工作树未提交态，含在飞 T2/T3/T4 落码）上重建、安装、哈希核对一致的四份 debug APK（app 主包 / app androidTest / core:data androidTest / core:database androidTest），源快照窗口内源稳定，新增 `@Test` 符号（N 向量 4 / X-02 6 / X-03 1 / A-10 协调器级 1 / V-01 ④ 1 + 既有批 38 全集）都在对应测试包 dex 中，71.json schema 资产在测试包内。源快照 find 集 = `app/src core/model/src core/data/src core/database/src core/domain/src`（计划底线五模块，含 core/model）。

## 2. 构建前一致性门（2026-11-01T10:17+08:00 实跑）

```bash
python tools/ci/check_r04_keep_rules.py ; echo "KEEP_GATE_EXIT=$?"
# → OK: app/proguard-rules.pro: 0 hits / OK: app/r04-test-keep.pro: 0 hits / OK: -dontobfuscate 0 hits in each of 2
#   KEEP_GATE_EXIT=0
S=71 python -I - <<'PY' …   # 批次 34 §1.4 原脚本（逐字）
# → SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90 / ONLY_PLAN=[] ONLY_JSON=[] DUP=[]
#   VIEWS_MATCH=True / PLAN_VERSION=71 JSON_VERSION=71 / PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138
#   RESULT=PASS  EXIT=0
```

DB schema 硬约束复读：`STUDY_DATABASE_VERSION = 71`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:138` 实读）。

## 3. 源快照与构建（10:18–10:33 实跑）

- `src-sha-before.txt` = **1227 文件**（10:18 实取；批 38 同集为 1220，+7 与在飞新文件一致）。
- 构建：`./gradlew --console=plain :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest` → **BUILD_EXIT=0**（`build.log` 末 `BUILD SUCCESSFUL in 16s`，`327 actionable tasks: 9 executed, 318 up-to-date`——Gradle up-to-date 判定即重建语义，沿批 38 E-01 §2 先例；输入集合 = 现树）。
- `diff src-sha-before.txt src-sha-after.txt` = **0 行**（SOURCE STABLE；`src-snapshot.diff` 在 raw-log）。

## 4. 四包身份与安装核对（`adb install -r -t` 各 1 次 Success；设备端 sha256sum 逐字节一致）

| 产物 | 本机路径（`<repo>`=仓库根） | 字节 | sha256 | 设备核对 |
|---|---|---|---|---|
| app 主包 | `<repo>/app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | 199858962 | `c149a2c34ca0a235b0e76044a4fba6db1b1767461dd17b872854b9b7fb76e231` | `pm path com.tingyun.smartmistakebook.offline` → sha 一致 |
| app androidTest | `<repo>/app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | 1638831 | `e20d923a8e5d76190ac24f2c7e7d82a55448bba6944520c59683e9e41178b17d` | `.offline.test` → sha 一致 |
| data androidTest | `<repo>/core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | 194190253 | `5b8ba39c83b625f3880210f5df2f249d9728e61a96a32a827f72b977101153c3` | `.core.data.test` → sha 一致 |
| database androidTest | `<repo>/core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` | 209357400 | `c5fae932c013859e5bb259c6520a6c788a769f4c4784b19d53dbf485728bc1a4` | `.core.database.test` → sha 一致 |

无 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，未触发 uninstall 舞步。

## 5. schema asset 门

`data` 测试包内 `assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json` = **378961 B**（unzip -l 实读）；schema 资产计数 `unzip -l | grep -c '…StudyDatabase/'` = **71**；71.json sha256 = `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94`。

## 6. dex 符号门（need()，批次 34 §4.3 原型逐字；命令与全结果在会话输出，摘要如下）

- **app 测试包（e20d923a…）**：16 符号全 OK——隔离套件类 + K6 两方法 + P-12③ 例 + **V-01 套件 4 方法（含新 ④ `theRestoredChainWritesAPdfThroughTheTestSeamAndKeepsThePreviewReference`）** + A-09/P-09/P-10/P-04-05 四套件类 + flags 套件两方法。
- **data 测试包（5b8ba39c…）**：41 符号全 OK——备份组（含 C4 8 方法含 D-04 三新例 `previewAssetRidesWithProgramAndSnapshotThroughArchiveAndRestore`/`repackedArchiveMissingPreviewAssetBytesIsRejectedBeforeSwap`/`createArchiveFailsWhenReferencedPreviewBytesAreMissing`）+ rowCounts 两方法 + 仓库 + parity/painter/PDF + 本地序号 + 对账 + 引擎 + A-10 三方法 + **新增四套件类与 12 方法**（N 向量 4：`fullCanvasPreviewRefusal…`/`theByteGateRefuses…`/`aPreviewThatFitsAlone…`/`aCancelledPreviewPersistIsRethrownAndNeverFoldedIntoARefusal`；X-02 6；X-03 1：`aPersistedProgramRelayoutsInteractivelyOfflineWithZeroRemoteDispatchWitnesses`；A-10 协调器级 1：`aThirdFinalFigureAcrossJsAndImageMethodsIsRejectedByTheCoordinatorOverTheRealLedger`）。
- **database 测试包（c5fae932…）**：10 符号全 OK——结构门 5 方法 + 矩阵/69-70/70-71/提交套件 + digest 方法 `onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts`。
- 任一 `MISSING` = 0（全部 `DEX OK`）。

## 7. 与判据的对照

- 判据 §六顺序（登记→一致门→快照 before→构建→快照 after diff→sha/安装→asset 门→dex 门→套件）逐步执行，未颠倒。
- 批 38 底册 24 套件 @Test 计数本会话开工复读**全部相符**（15/3/1/1/1/2/4·app + 34/2/9/8/12/16/24/16/1/6/27/3·data + 5/15/4/3/12·database），V-01 app 套件按落码 3→4 进套件单。
- 批 38 已编译未执行 3 套件（`TutorVisualCommitRace` 3 / `TutorVisualRebuildRestore` 3 / database `TutorVisualSavedProblemRetention` 2）按判据归 V-03/V-04 轨套件单，本轨不执行（同批 38 E-01 §6 边界口径）。

## 8. 脱敏与去噪

- 本文件不含密钥/凭据/邮箱/物理序列号/用户目录（模拟器 serial 与 `<repo>` 相对路径合规）。
- 原始全量在 `build/device-track-batch39/`（`.gitignore:15` 忽略），本文件为唯一关闭判据载体。
