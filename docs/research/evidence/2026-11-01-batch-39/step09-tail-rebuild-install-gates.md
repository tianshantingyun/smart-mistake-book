# E-09 批次 39 收尾轮·重建重装与硬门

| 字段 | 值 |
|---|---|
| evidence-id | E-09 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39（收尾轮 2026-11-02） |
| step | 收尾轮·步一 |
| gate-or-kd | 源快照门 / APK 身份门 / dex 门 / asset 门 |
| verdict | PASS（窗 W2）；窗 W3 见 E-14 |
| executed-at | 2026-11-02T00:45:00+08:00 |
| operator | 设备验证轨（子代理） |
| head | `f10a1e4e` |
| worktree | 514 M/??（开窗时，仅记录） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096（`getconf PAGE_SIZE`；`ro.product.cpu.page.size` 为空） |
| apk | `<repo>/app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` 等四包 |
| apk-sha256 | app `fe9452aab64d6fcf9d840a0e5e5c542d43daed176da2bf5aa71370d766d69e3e`；appTest `3c222fca7560327ab7200523b5c1332ebfd6228277a0eb133c34338985f9bfaf`；data `f7e961470b99d6fe5641b798687cd67593ff4b6b7a5251ac4168f0e06699e3a2`；database `9ca011396fd4fc126b5b109250cbc3262ae322d93ec48cadfe6953eaf1dd8d97` |
| apk-version | `0.2.0-offline`（versionCode 2） |
| exit-code | keep=0 / schema=0 / build=0 / install=0 |
| raw-log | `build/device-track-batch39-tail/{keep-gate,schema71-gate,build}.log`、`apk-sha256.txt`、`device-apk-sha.txt`、`dex-gate.log` |
| record-ref | `docs/research/evidence/2026-11-01-batch-39/00-index.md` §11 |
| sanitized | 是（无密钥/主目录/邮箱） |

## 1. 一致门（构建前）

```bash
python tools/ci/check_r04_keep_rules.py
# OK: proguard-rules.pro / r04-test-keep.pro 各 0 hits；KEEP_GATE_EXIT=0
S=71 python - <<'PY' …（批次 34 §1.4 原脚本逐字）
# SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90
# ONLY_PLAN=[] ONLY_JSON=[] DUP=[] VIEWS_MATCH=True
# PLAN_VERSION=71 JSON_VERSION=71
# PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138
# RESULT=PASS / SCHEMA_GATE_EXIT=0
```

keep 三文件（`app/r04-test-keep.pro`、`app/proguard-rules.pro`、`app/build.gradle.kts`）`git status --porcelain` 计数 0（开窗实跑）。

## 2. 源快照（五模块：app/core:model/core:data/core:database/core:domain）

```bash
find app/src core/model/src core/data/src core/database/src core/domain/src -type f -print0 \
  | sort -z | xargs -0 sha256sum > build/device-track-batch39-tail/src-sha-before.txt
# 1232 行；文件 sha256 b8708cfd458fb1b0d95c4c8286c6a5da0f1636b8852570e4c734ef9c6a0146fb
```

- 窗 W1（00:45–00:55）：X-03 首跑红后修复夹具，W1 作废（见 E-10）。
- 窗 W2：`src-sha-before.txt`（00:55:37）→ `src-sha-after-restore.txt`（01:26）`diff` = **0 行**；X-03、isolation、T4 控制轮均落在此窗。
- 窗 W3（01:27 起）：他线在飞，`src-snapshot-all.diff` 非空（见 E-14）。

## 3. 构建（只 assemble，禁 connectedAndroidTest）

```bash
./gradlew --console=plain :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest
# BUILD SUCCESSFUL in 32s；BUILD_EXIT=0
```

构建后 `src-snapshot-build.diff` = 0 行。X-03 夹具修正与 KD-76 loadavg 修正后各重建一次（`build-x03fix.log`、`build-kd76fix.log`，exit 0），未运行任何 `connectedAndroidTest`。

## 4. 四包 sha 与设备侧字节核对

| 包 | 宿主 sha256 | 设备侧 sha256 | 一致 |
|---|---|---|---|
| `com.tingyun.smartmistakebook.offline` | `fe9452aab64d6fcf9d840a0e5e5c542d43daed176da2bf5aa71370d766d69e3e` | 同左 | 是 |
| `com.tingyun.smartmistakebook.offline.test` | `3c222fca7560327ab7200523b5c1332ebfd6228277a0eb133c34338985f9bfaf` | 同左 | 是 |
| `com.tingyun.smartmistakebook.core.data.test` | `f7e961470b99d6fe5641b798687cd67593ff4b6b7a5251ac4168f0e06699e3a2` | 同左 | 是 |
| `com.tingyun.smartmistakebook.core.database.test` | `9ca011396fd4fc126b5b109250cbc3262ae322d93ec48cadfe6953eaf1dd8d97` | 同左 | 是 |

四包 `adb install -r -t` 均 `Success`，无 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

## 5. asset 门与 dex 门

```text
ASSET_OK   （data APK 含 assets/…StudyDatabase/71.json）
71.json sha256 = 4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94
```

```bash
need() { apk=$1; shift; for s in "$@"; do unzip -p $apk 'classes*.dex' | grep -a -q "$s" || …; done; }
# DEX OK: app-strictOffline-debug-androidTest.apk
# DEX OK: data-debug-androidTest.apk
# 0 MISSING（含 KD-76 用例类 VisualJsConnectStageCalibrationInstrumentedTest）
```

## 6. 如实登记

- `WRITE` 记录：app APK 在控制轮还原后重打包 sha 变为 `fe9452aa…`（同源不同字节，增量打包非字节可复现）；此后所有 app 侧证据以 `fe9452aa…` 为准。
- `data-debug-androidTest.apk` 构建于 01:04。**推断（非直读构建输入）**：该包内部 `StudyDatabase=72` 而 `SmbkSelectionPlan=71`，依据是 E-12 三套件失败消息 `expected:<71> but was:<72>` 与 `快照 schema=72，裁剪器只支持 schema=71`；当时 `StudyDatabase.kt` 处于他线 transient 72 状态、01:57 后又被改回 71（mtime 01:57:29）。该推断不改变 E-12 的 B 类归因。X-03 不依赖 DB 版本，绿色不受影响。

## 7. 身份时点补记（2026-11-02 落盘员追加；不改上文既有行）

§4 表与头部 `apk-sha256` 记的是**收尾窗内的多组观察值**（不是 00:45 单一时点）；按 raw-log 拆开如下。表中"沿用"表示该时点未重装、设备侧值保持上一行：

| 观察组 | 触发 | app | appTest | data androidTest | database androidTest | 宿主/设备核对文件 |
|---|---|---|---|---|---|---|
| G1 首建首装 | `build.log`（00:46）/ `install.log` | `f97f2972…a6346bf` | `b4242b7d…1cda5d0` | `d1fc594d…e4c4904` | `9ca01139…1dd8d97` | `apk-sha256.txt:1-4` + `device-apk-sha.txt:1-4`（四包一致） |
| G2 X-03 修正重建 | `build-x03fix.log`（00:54） | 沿用 `f97f2972…` | `3c222fca…f9bfaf` | `f7e96147…e99e3a2` | 沿用 `9ca01139…` | `apk-sha256.txt:5-6` + `device-apk-sha.txt:5-6`（appTest/data 一致） |
| G3 控制轮 | `control-apk-sha256.txt`（01:12） | `4c1508c1…a442ec4` | `b4242b7d…`（相对 G2 的 `3c222fca…` 已换；设备侧同值） | 沿用 `f7e96147…` | 沿用 `9ca01139…` | `control-apk-sha256.txt` + `device-apk-sha-control.txt`（两包一致） |
| G4 还原 | `build-restore.log`（01:26） | `fe9452aa…d69e3e` | 沿用 `b4242b7d…` | 沿用 `f7e96147…` | 沿用 `9ca01139…` | `apk-sha256-restored.txt:1-2` + `device-apk-sha-restored.txt:1`（app 一致；appTest 设备侧仍为 G1 同值） |

- 每一组内**被 `device-apk-sha*.txt` 覆盖到的包**均逐字节一致；未覆盖的包不声称设备侧核对（如上表"沿用"）。
- 本 ask 复核：`nl -ba apk-sha256.txt`、`nl -ba device-apk-sha.txt`、`nl -ba apk-sha256-restored.txt`、`nl -ba device-apk-sha-restored.txt`、`nl -ba control-apk-sha256.txt`、`nl -ba device-apk-sha-control.txt` 实读；表中哈希为原文件行内值。
- 影响面：E-10（X-03）绑定 data `f7e96147…` / appTest `3c222fca…`（G2）；E-11 **基线窗**绑定 app `f97f2972…`（G1） / appTest `3c222fca…`（G2，00:55 安装）、**控制窗**绑定 app `4c1508c1…`（G3）/ appTest `b4242b7d…`（G3 设备侧记录）；E-12 绑定 app `fe9452aa…`（G4）；E-13 绑定 app `fe9452aa…` / appTest `3c222fca…`。**更正（第 2 轮复核）**：原句把 E-11 基线写成 G4/G1 的 `fe9452aa…`/`b4242b7d…`，与窗时序矛盾——`fe9452aa…` 是 01:26 构建、01:27 安装的还原包，而基线窗跑在 01:06–01:09。G1/G2 的记录在窗**前**（00:46/00:55），基线窗内没有再次核对设备 sha；因此 E-11 的基线绑定是**时序绑定**，不是窗内实测，原句"各结论均落在有宿主/设备核对文件的组内"对基线窗不成立。
- `raw-log` 字段的 `device-apk-sha.txt` 应理解为 `device-apk-sha*.txt`（含 `-control`/`-restored`），本条补记消除"按 §4 的单一 `device-apk-sha.txt` 找不到 app `fe9452aa`"的复核障碍。

## 8. schema 71/72 冲突登记（第 2 轮复核追加；零 Gradle）

- **门锁与现树的差异**：本批 §1 的 `S=71` 一致门在 00:45 实跑 PASS（`schema71-gate.log`：`PLAN_VERSION=71 JSON_VERSION=71`、`PLAN_HASH=JSON_HASH=714c0ecb…`）。但它校验的是 `71.json`（文件 sha256 `4a13d9d0…`）与 `SmbkSelectionPlan.kt` 的 71 常量，**不是** 00:54/01:04/03:04 实际装机 APK 内嵌的 schema。
- **现树已是 72**（本复核轮实读）：`core/database/.../StudyDatabase.kt:140` `internal const val STUDY_DATABASE_VERSION = 72`；`core/database/schemas/.../72.json` 存在（未跟踪，mtime 2026-11-02 02:53，sha256 `a49452df…`）。这是他线在飞的 schema-72 工作，本轨未改。
- **S=72 门本复核轮实跑（只读 Python，非 Gradle）结果 FAIL**：

```text
SCHEMA=72 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=91
ONLY_PLAN=[]
ONLY_JSON=['knowledge_search_material_feature']
DUP=[]
VIEWS_MATCH=True
PLAN_VERSION=71 JSON_VERSION=72
PLAN_HASH=714c0ecb85ee5edf74f0013740148138 JSON_HASH=aa5e8e8e6536e0c1f3adcda6867814cb
RESULT=FAIL
EXIT=1
```

- 该结果与集成轨转述的"JSON_TABLES=94 / ONLY_JSON=4 / HASH 3570ca22…"**不同**：现树 `72.json` 在 02:53 后已再次变化（本次读到 91 表、hash `aa5e8e8e…`）。因此"具体 FAIL 明细"本身也是时间点值；能稳定复现的只有"`S=72` 门 FAIL"。
- **结论**：§5 的 `ASSET_OK（71.json 存在）` 与 §1 的 `S=71 PASS` 都**不能证明运行时 schema 是 71**；现树重建必须先由主会话/他线裁定 71/72。任何"按现树重建"都不得把 §1 当有效门。

