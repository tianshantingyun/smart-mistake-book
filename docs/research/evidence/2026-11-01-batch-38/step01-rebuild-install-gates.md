# E-01 重建重装 + 源快照门（含 core/model）+ dex 符号门 + asset 门

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤一（重建重装硬门，先于一切设备命令） |
| gate-or-kd | 批次 38 设备硬门（判据沿批次 37 判据 §四，源快照集含 `core/model/src`） |
| verdict | PASS（三 APK 全链：构建→sha→安装→设备 sha 复核→dex 门→asset 门） |
| executed-at | 2026-11-01T04:00:00+08:00 起（窗口 A 04:00–04:09；窗口 B 04:26–04:33） |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b52f01cb67c1d0f88375bcc092a8f54d9a` |
| worktree | 786 条（仅记录） |
| device-serial | emulator-5562（模拟器，可保留） |
| device-avd / api / abi / page-size | test_device / 34 / x86_64 / 4096（E-00） |
| apk | 见 §3 三包 + §4 data 包（本机路径，`<repo>` = 仓库根） |
| apk-sha256 | 见 §3/§4 |
| apk-version | strictOffline debug（app）/ debug androidTest（三库模块） |
| exit-code | BUILD2_EXIT=0（窗口 A）；BUILD3_EXIT=0（窗口 B）；BUILD1_EXIT=1（见 §2 归因） |
| raw-log | `build/device-track-batch38/{build.log,build2.log,build3.log,src-sha-*.txt,apk-sha256.txt,dex-gate-*.txt,test-counts-before.txt}`（本机，不入仓；build*.log 各 ≤460 行） |
| record-ref | `docs/research/2026-11-01-batch-38-record.md`（设备节） |
| sanitized | 是（见本文件 §7） |

## 1. 目的与判据

证明本批设备结论绑定**当前树**（HEAD=`dcfdc5b5` + 工作树未提交态）上重建、安装、哈希核对一致的三份 debug APK（app 主包 / app androidTest / core:database androidTest / core:data androidTest），源快照窗口内源稳定，新增/迁移 `@Test` 符号都在对应测试包 dex 中（防旧包跑新门），71.json schema 资产在测试包内。命令原型 = 批次 37 判据 §四（`docs/research/2026-10-31-batch-37-device-criteria.md:342-451`），源快照 find 集 = `app/src core/model/src core/data/src core/database/src core/domain/src`（计划底线五模块，**含 core/model**）。

## 2. 构建一致性门与两次构建窗口

```bash
python tools/ci/check_r04_keep_rules.py ; echo "KEEP_GATE_EXIT=$?"
# → OK: app/proguard-rules.pro: 0 hits / OK: app/r04-test-keep.pro: 0 hits / OK: -dontobfuscate 0 hits in each of 2 → KEEP_GATE_EXIT=0
S=71 python - <<'PY' …   # 批次 34 §1.4 原脚本
# → SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90 / ONLY_PLAN=[] ONLY_JSON=[] DUP=[]
#   VIEWS_MATCH=True / PLAN_VERSION=71 JSON_VERSION=71 / PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138 / RESULT=PASS，EXIT=0
```

**窗口 A（app + database 两 APK）**：`src-sha-before.txt` = **1220 文件**（04:00 实取）。
第一次构建 `:app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest` → **BUILD_EXIT=1**：`:core:data:compileDebugAndroidTestKotlin` FAILED，唯一两错误（`build.log:381-382`）：

```text
e: …/core/data/visual/TutorVisualCommitRaceInstrumentedTest.kt:284:9 Unresolved reference 'TutorVisualIdentity'.
e: …/core/data/visual/TutorVisualRebuildRestoreInstrumentedTest.kt:229:51 Suspension functions can only be called within coroutine body.
```

归因：**本批 V-03/V-04 实施轨交付文件的编译缺陷**（两文件 untracked、02:50/02:51 落盘、不在本轨套件单；`TutorVisualIdentity` 实存于 core.database 并被 `BackupVisualReferenceInstrumentedTest.kt:27` 同样 import 使用——缺 import；`:229` 局部函数 `restoreByPortRead` 调 suspend DAO 未标 suspend）。**经主会话升级授权做最小机械修复**（只许两处：①race 测试加 import；②restore 测试局部函数加 `suspend`；不改断言/不改语义/不重排），修复 diff 原文：

```text
① TutorVisualCommitRaceInstrumentedTest.kt（import 块，第 11 行前插入）
+ import com.tingyun.smartmistakebook.core.database.TutorVisualIdentity
② TutorVisualRebuildRestoreInstrumentedTest.kt:228（局部函数签名加 suspend）
- fun restoreByPortRead(snapshotId: String, policy: VisualRestorePolicy): VisualRestoreOutcome {
+ suspend fun restoreByPortRead(snapshotId: String, policy: VisualRestorePolicy): VisualRestoreOutcome {
```

修复前后文件 sha256（窗口 diff 实测，构成审计链）：
`TutorVisualCommitRaceInstrumentedTest.kt` 8c90702c…→6a6ec7be…；`TutorVisualRebuildRestoreInstrumentedTest.kt` aecf847f…→fc9f45fa…（全值见 `build/device-track-batch38/src-snapshot-windowB.diff` 对照）。

- 窗口 A 实际完成构建 = 第二次（`:app` 两目标 + `:core:database:assembleDebugAndroidTest`）→ **BUILD2_EXIT=0**（`build2.log` 末 `BUILD SUCCESSFUL in 14s`）。
- 窗口 A 源快照：`diff src-sha-before.txt src-sha-after.txt` = **0 行**（SOURCE STABLE）。
- app 主 APK 未重建（Gradle up-to-date，02:46 产物、其输入集合与现树一致——Gradle up-to-date 判定即重建语义，sha 进 §3）。

**窗口 B（data APK）**：修复后取 `src-sha-before-windowB.txt` → `:core:data:assembleDebugAndroidTest` → **BUILD3_EXIT=0**（`build3.log` 末 `76 actionable tasks: 5 executed, 71 up-to-date`）→ `diff before/after`（窗口内）= **0 行**；相对窗口 A 的源差**恰好只有两个被修复文件**（`src-sha-after.txt` vs `src-sha-after-windowB.txt` diff = ±2 文件两行，见上 sha）。

## 3. 窗口 A 产物身份与安装核对（`adb install -r -t` 各 1 次 Success；设备端 sha256sum 逐字节一致）

| 产物 | 本机路径（`<repo>`=仓库根） | 字节 | sha256 | 设备核对 |
|---|---|---|---|---|
| app 主包 | `<repo>/app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | 199858962 | `94c82daab45da2fc0fe11f788c68915b84f18400f8ad027a1ae018b4764828c9` | `pm path com.tingyun.smartmistakebook.offline` → sha 一致 |
| app androidTest | `<repo>/app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | 1632598 | `33e1d6a24c5b785af7752e585d9408847dc7991dc1962b85349a0297d75161fa` | `.offline.test` → sha 一致 |
| database androidTest | `<repo>/core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` | 209357400 | `7baf8c4624e14ec682aa2c70583cc8f0765da8c29fc78c49d2a40434d13c35c1` | `.core.database.test` → sha 一致 |

## 4. 窗口 B 产物身份与安装核对

| 产物 | 本机路径 | 字节 | sha256 | 设备核对 |
|---|---|---|---|---|
| data androidTest | `<repo>/core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | （构建产物） | `e86fce38d62c59b274b0e46de4e310ed712b2d2e7fb6b923938ce1e1c409bcc0` | `pm path com.tingyun.smartmistakebook.core.data.test` → sha 一致 |

## 5. dex 符号门（need()，批次 34 §4.3 原型；全量命令与结果在 `build/device-track-batch38/dex-gate-{app,database,data}.txt`）

- **app 测试包（33e1d6a2…）**：19 符号全 OK——隔离套件类 + K6 两方法 + P-12③ 例 + V01App 套件 + flags 套件及两方法 + **新增 A-09 套件 3 方法 + P-09/P-10/P-04-05 三套件类与各 1 方法**。
- **database 测试包（7baf8c46…）**：21 符号全 OK（迁移矩阵/69-70/70-71/结构门 5 方法/提交 4 digest 方法）。首轮 1 个 `MISSING` 为**判据转录笔误**（batch-34 表 `CasUse` vs 现树方法名 `onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts`，`:102` 实读核对），以现树名复跑 DEX OK——非旧包（同轮其余 20 符号全中）。
- **data 测试包（e86fce38…）**：30 符号全 OK——备份/rowCounts 两方法/裁剪器/C4（含 **D-04 新增 3 方法** previewAssetRides…/repackedArchiveMissingPreviewAssetBytes…/createArchiveFails…）/仓库/parity n09-n13/painter/PDF 3 方法/本地序号/对账/引擎/A-10 三方法。

## 6. asset 门与构建前计数

- `data` 测试包内 `assets/…StudyDatabase/71.json` = **378961 B**（unzip -l 实读；database 测试包含全部 71 个 schema 资产 + 71.json，`unzip -l | grep -c` = 71）；app 测试包 assets = 0（预期）。
- 构建前 `grep -c '@Test'`（`test-counts-before.txt` 全 25 文件）：与判据表逐一相符，**唯一差 = C4 `BackupVisualReferenceInstrumentedTest` = 8（判据表 5 为批次 37 时点值；+3 = 本批 D-04 落点的预览 canonical 资产三例，同文件他轨 M +339 行）**——按批次 37 §3.2"以构建前复读为准、新增用例必须重跑"执行 8 例（E-13）。
- **V-01 降级变体（core/data `V01VisualChainInstrumentedTest.kt`，4 例）不进套件单**：其已知 2/4 夹具红**未修复**（`:238/:563/:595` 仍为 `measured.singleOrNull()` 形态、`:96-120` KDoc 降级登记原样）——本批任务项"V-01 降级变体**若修复**"条件不成立，如实登记不跑（批次 37 record §"不做"同口径：3 行级修复待主会话裁定）。

## 7. 脱敏与去噪

- 本文件不含密钥/凭据/邮箱/物理序列号/用户目录（模拟器 serial 与 `<repo>` 相对路径均合规）。
- 扫描：`00-index.md` §6 统一记录本目录 §4.4 扫描结果。
- 原始全量在 `build/`（`.gitignore:15` 忽略），本文件为唯一关闭判据载体。
