<!-- 体积自查：约 120 行；R4 上限 400 行 / 65536 B -->
# E-40-01 重建重装 + 源快照门（含 core/model）+ dex 符号门 + asset 门

> 边界：本文件只绑定 **2026-11-02T10:13 构建窗**的四包 APK 与当时的源快照。执行中 HEAD 由 `a3f367df` 变为 `dee81ea7`（10:45:47），工作树 60 文件漂移；当前树已不能复现本文件所测的 73 状态，详见 `step07-drift-and-blockers.md`。

| 字段 | 值 |
|---|---|
| evidence-id | E-40-01 |
| convention-version | 1 |
| batch | 2026-11-02-batch-40 |
| step | 步骤一（重建重装硬门，先于一切设备套件） |
| gate-or-kd | 批次 40 设备判据 §45–§51 共通门 + 计划 §2 设备绿绑定源快照 |
| verdict | **PASS**（四包构建→源快照 diff=0→安装 4×Success→设备侧 sha 逐字节一致→asset 门→dex 门） |
| executed-at | 2026-11-02T10:11–10:22+08:00 |
| operator | 设备验证轨（批次 40 工作流子代理） |
| head | `a3f367dfaccd960f951a66468a1cdda2f0b21f47` |
| worktree | `git status --porcelain` = **653 条**（他线在飞；仅记录，不作归因） |
| device-serial | emulator-5584（AVD `kb_test_device`，API 34，x86_64，page-size 4096，boot_count=1，Awake） |
| apk | 见 §4 四包 |
| apk-sha256 | 见 §4 |
| apk-version | `0.2.0-offline`（strictOffline debug） |
| exit-code | 首次 BUILD_EXIT=1（WS2 测试编译缺参）；修复后 **BUILD_EXIT=0**；4×install Success |
| raw-log | `build/device-track-batch40/{env.txt,build.log,build-exit.txt,build2.log,build2-exit.txt,src-sha-before.txt,src-sha-after.txt,src-snapshot.diff,apk-sha256.txt,device-apk-sha256.txt,install.log,dex/*.symbols.txt}` |
| record-ref | `docs/research/2026-11-02-batch-40-record.md`（记录员落笔位） |
| sanitized | 是（无密钥/凭据/物理序列号；仅模拟器 serial） |

## 1. 目的与判据

证明本批设备结论绑定**当前树**（HEAD=`a3f367df` + 工作树未提交态，含 WS1 PDF 注入器与 WS2 schema 73 落码）上重建、安装、哈希核对一致的四份 debug APK；源快照窗口内源稳定；`73.json` 资产进入 data 测试包；新增符号（错因账本、72→73 迁移、PDF /AIGC、KD-71/KD-76/isolation 套件）都在对应 dex 中。

源快照 find 集 = `app/src core/model/src core/data/src core/database/src core/domain/src`（计划底线五模块，含 `core/model`）。

## 2. 构建前状态与一次归因修复（必须披露）

首次构建 `:core:database:compileDebugAndroidTestKotlin` **失败**（`build.log:380`）：

```
e: ErrorCauseLedgerInstrumentedTest.kt:172:17 No value passed for parameter 'errorBookEntries'.
```

- 归因：**本批 WS2 新测试**（`core/database/src/androidTest/.../ErrorCauseLedgerInstrumentedTest.kt`，untracked，mtime 07:41）未给 `StudySeedBundle.errorBookEntries` 传值；`StudySeedBundle` 该参数无默认值（`core/database/src/main/kotlin/.../StudyDatabaseRecords.kt:714`）。
- 处置：设备轨最小增量修复——在该 `StudySeedBundle(...)`（`practiceUnits = ...` 之后）补 `errorBookEntries = emptyList(),`。只补必填参数，不改任何断言、不删用例、不放宽门。
- 修复后文件 sha256 = `b166536e9ad871e628b4861311e92d17ed8a322c774f50222b769e45d0842ece`（`error-cause-test.sha256`）。该文件为 WS2 untracked 新文件，整文件纳入修改面并在此注明。
- 修复后 `./gradlew --console=plain :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest` → **BUILD SUCCESSFUL in 33s**（`build2.log`，`build2-exit.txt`=0）。

## 3. 源快照门

```bash
find app/src core/model/src core/data/src core/database/src core/domain/src \
  -type f -print0 | sort -z | xargs -0 sha256sum > build/device-track-batch40/src-sha-before.txt
# 构建后同命令 → src-sha-after.txt
diff src-sha-before.txt src-sha-after.txt > src-snapshot.diff
```

- 文件数 **1277**；`src-sha-before.txt` sha256 = `6acde9b1ab0867cdb9737205940e03bd35e77cc1a2789faf54aaf0f7a9ef00f8`
- `src-snapshot.diff` = **0 行**（SOURCE STABLE）
- 口径说明：before 快照在 §2 修复**之后**取；修复前快照（1277 文件，sha `8dfdc835…`）只作过程记录，不作设备绿绑定。

## 4. 四包身份与安装核对

| 产物 | 宿主路径（`<repo>`=仓库根） | 字节 | sha256（宿主=设备） |
|---|---|---|---|
| app 主包 | `<repo>/app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | 185746540 | `1acb9907558a8a50254f7f52f4deedcb874a521b032f6afa39d1630a91835983` |
| app androidTest | `<repo>/app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | 1668087 | `565fc85b1edadc79e6497261976acbf8e03e1f11326fa4a6e6de9abb9a357c7b` |
| data androidTest | `<repo>/core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | 196071631 | `34e3435d9fa5ef0b7f18dc0b02cd229d35c9913e2565174c5b619c77bf103179` |
| database androidTest | `<repo>/core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` | 209357634 | `f12ef06abfb523c79b40b7e2170f006ed9d462cbb2d3cbba0ff8f6d059668f92` |

- 安装：`adb -s emulator-5584 install -r -t <apk>` ×4，日志 4×`Success`（`install.log`）。
- 设备侧复核：`pm path` + `sha256sum /data/app/.../base.apk`，四值逐字节等于宿主（`device-apk-sha256.txt`）。
- 包名：`com.tingyun.smartmistakebook.offline` / `.offline.test` / `com.tingyun.smartmistakebook.core.data.test` / `com.tingyun.smartmistakebook.core.database.test`。

## 5. schema asset 门（S=73）

- data 测试包内 `assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/73.json` = **396419 B**；schema 资产计数 **73**（`7.json`…`73.json`）。
- `73.json` sha256 = `27c5c6a45a09b178be66d3dcf2b0c4cdc96216c532dcbcebdafce0f187f82c73`。
- 静态解析：`database.version=73`、`identityHash=aa9f0313fcc417505471e3ab15ae0932`、entities=**93**、views=2；`SmbkSelectionPlan.SCHEMA_VERSION=73`/`IDENTITY_HASH` 同值、`CLOSURE_VERSION=5`、前缀 `smbk-selection-v5`。72→73 新增表恰为 `error_cause_binding`、`error_cause_observation`（93=91+2，解释了与基线 §4 预期 92 的偏差）。

## 6. dex 符号门（`dexdump` 逐 dex 全量导出后 `grep -cF`）

- **app 测试包**（5 dex）：isolation / calibration / V-01 / A-09 套件类各 1；KD-71 相关方法 `theWatchdogKills…`=4、`aHungWorker…`=2、`aSecondConcurrent…`=4、`theClientRebuildsAtMostOnce…`=2。
- **data 测试包**（13 dex）：backup 五类各 1；engine/parity/painter/pdf/x03 类各 1；`closeDuringAnInFlight…`=5、`concurrentCloseAndExecute…`=4。
- **database 测试包**（7 dex）：matrix / errorcause / gates / 69-70 / 70-71 / commit 类各 1；`errorCauseTablesAreCreatedEmptyWhileV72RowsSurvive`=2、`appendAcceptRetractAndSupersedeKeepOneActiveRow`=2。
- **app 主包**（10 dex）：`PdfAigcInjector` / `PdfAigcReader` / `PdfAigcContentPolicy` / `PdfAigcMetadata` 各 1。
- 任一 `MISSING` = 0。

## 7. 与全局验收的关系（不得混淆）

- 本文件只证明**设备输入**（当前树四包）成立，不代表双门通过。
- 当前单测门仍红：本会话扫描 `*/build/test-results/**/*.xml`（533 个，排除 `.worktrees/.artifacts/.jez`）仅 1 个失败套件——`core/database` 的 `MaterialFeatureScaleTest`：`v26 材料数 expected:<65021> but was:<64839>`（KB L2 他线 B 类，非 WS1/WS2 设备面）。强门据此未跑。
- `SmbkSelectionPlanTest` 设备外 JVM 结果：tests=12 / failures=0（09:38 结果 XML）。

## 8. 未验证 / 边界

- 本轮未跑任何 `connectedAndroidTest`；全部逐套件 `am instrument -e class`。
- 设备为模拟器（API 34 / x86_64），不外推到真机。
- §2 修复仅解阻塞，不构成 WS2 代码质量结论；WS2 单写者 diff 仍含他线 71→72 叠层，无法单独归因。
