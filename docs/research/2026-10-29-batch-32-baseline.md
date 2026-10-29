# 批次 32 基线核对（2026-10-29）

> 口径：**只读核对**。本轮零 Gradle、零仪器化执行、零 git 写、零产品代码改动。
> 所有"设备绿 / 套件通过 / 单测通过"结论均来自**仓内已有证据文件的复读**，不是本轮重跑；
> 凡本轮未执行的检查一律标 `UNVERIFIED`。
>
> 对应计划：`.zcode/plans/plan-2026-10-29-batch-32-parity-schema71.md` §0（基线核对）。

## 0. 与计划 §0 的不符处（先看这里）

计划 §0 的"复核遗留"有 **4 条已被现树否证**（代码/测试已在 HEAD 内，只是没上设备或从未回填 §0），
另有 3 条成立，2 处引用瑕疵。逐条见 §8。最要紧的一条：

| # | 计划 §0 原文 | 现树实测 | 判定 |
|---|---|---|---|
| D1 | "N09-P06 期望质心 `1.000`"（任务 A1 要删） | `VisualDrawOpsParityInstrumentedTest.kt:298-335` **已无** `assertEquals(1.0, centroid)`；断言改为 `verdict == F4_GEOMETRY`。旧的 `1.000` 等式只在 `build/device-track-batch31/step08-parity-logcat.txt:129`（上一次设备跑的旧源） | **源侧已改**，剩设备复跑 |
| D2 | "evict 只经手写候选验证，plan→command 未端到端" | 端到端用例**已在 HEAD**：`TutorVisualCommitRepositoryInstrumentedTest.kt:180` `commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand`（clean，commit `5fee43c5`） | **用例已落码、未上设备** |
| D3 | "pin 谓词无有分辨力用例" | 有且**设备已绿**：`TutorVisualStructureGatesInstrumentedTest.kt:156-212`（`snap-pinned-staging` 为 STAGING + 题面 pin）；E-09 step11 `OK (4 tests)` 含该例 | **计划陈述过期** |
| D4 | "snapshotId 复派生用的仍是调用方 digest（DB 层未校验 digest==sha256(json)）" | DB 层**已双向校验**：`RoomStudyDatabase.kt:2174-2179`（state/draw 两个 digest）+ `:2185`（snapshotId==派生值）；commit `24510dca` 即批次 31 | **计划陈述过期** |

> 说明：D2/D3/D4 的代码都是批次 31 的提交（`5fee43c5`/`24510dca`），已在 `0b4bdbbe` 祖先链上。
> 计划 §0 的"复核遗留"抄的是更早一轮复核稿，未随批次 31 落码回填。任务 5.7/5.8/5.9 的**代码面**
> 不能按 §0 字面重复施工；真正缺的是**设备执行证据**（见 §5、§8）。

---

## 1. Git 与 schema（工单第 1 项）

| 项 | 实测 | 命令/出处 |
|---|---|---|
| HEAD | `0b4bdbbea918ba6b8c8e1f3b190b8be502ec627a` | `git rev-parse HEAD` |
| origin/main 本地引用 | `59d4a03f92ab27314d25384a777f41829b3421a2` | `git rev-parse origin/main` |
| 关系 | HEAD 领先 origin/main **11** 提交，落后 **0**；`origin/main` 是 HEAD 的祖先 | `git rev-list --left-right --count origin/main...HEAD` → `0 11`；`git merge-base --is-ancestor origin/main HEAD` 成功 |
| 分支 | `main`（仅此一本地分支） | `git branch -a --contains HEAD` |
| 工作树计数 | **688** 行（`216 M` + `472 ??`） | `git status --porcelain \| wc -l`；`awk '{print substr($0,1,2)}' \| sort \| uniq -c` |
| DB schema（源码事实源） | **70** | `StudyDatabase.kt:139` `STUDY_DATABASE_VERSION = 70`；`:242` `version = STUDY_DATABASE_VERSION` |
| 请求 schema | **71**（计划 §1 任务 B；`71.json` 不存在） | `find . -name "71.json" -not -path "*/build/*"` → 空 |
| 设备运行时 DB 版本 | `UNVERIFIED` | 本轮只读、未 `adb shell` 打开任何应用私有库 |

- 计划 §0 的"推送遇 443 后台重试"与 `origin/main` 落后 11 提交一致：批次 31 只在本地，未落远端。
- `472 ??` 含被折叠的整目录（如 `core/markdown/`），不是字面文件数。

## 2. 热区快照 + core/data、core/database 开工即脏清单（工单第 2 项）

### 2.1 热区清单口径

批次 32 计划同样**没有枚举热区**：
`grep -n "热区\|HOT_ZONE" .zcode/plans/plan-2026-10-29-batch-32-parity-schema71.md` 只命中 `:33` 的"热区禁改"一句。
本节沿用批次 30/31 基线的 12 项清单（`docs/research/2026-10-29-batch-31-baseline.md:34-46`），
**这是沿用口径，不是本批计划原文**。

### 2.2 快照（total = M + ??）

| 热区项 | 本批实测 | 批次 31 基线值 | 判定 |
|---|---|---|---|
| feature/tutor | **59**（37 M + 22 ??） | 59（37+22） | 未变 |
| feature/library | **43**（30 M + 13 ??） | 43（30+13） | 未变 |
| feature/capture | **16**（15 M + 1 ??） | 16 | 未变 |
| feature/review | **12**（11 M + 1 ??） | 12 | 未变 |
| feature/profile | **4**（3 M + 1 ??） | 4 | 未变 |
| core/ui | **35**（11 M + 24 ??） | 35 | 未变 |
| core/markdown | **1**（整目录 `??`） | 1 | 未变 |
| core/export | **6**（5 M + 1 ??） | 6 | 未变 |
| core/domain | **5**（4 M + 1 ??） | 5 | 未变 |
| settings.gradle.kts | **1 M** | 1 M | 未变 |
| `.gitignore` | **1 M** | 1 M | 未变 |
| `SmartMistakeBookRoot.kt` | **1 M** | 1 M | 未变 |
| `SecondaryScreens.kt` | **1 M** | 1 M | 未变 |

命令：`for p in …; do git status --porcelain -- "$p" | wc -l; done`（逐项另计 M/??）。
**结论：热区自批次 31 收口后零漂移，仍全部为脏（他线在飞）。**

计划清单外同脏：core/data **14**、core/database **10**、app/src **28**、docs **61**、tools **21**。

### 2.3 core/data 开工即脏（14 = 13 M + 1 ??）

命令：`git status --porcelain -- core/data core/database`（原样 24 行）。
`M`（13）：
1. `core/data/src/androidTest/.../backup/BackupRestoreInstrumentedTest.kt`
2. `core/data/src/androidTest/.../backup/DeviceMigrationDrillInstrumentedTest.kt`
3. `core/data/src/androidTest/.../capture/BatchImportRepositoryInstrumentedTest.kt`
4. `core/data/src/androidTest/.../capture/SplitImportCommitReceiptInstrumentedTest.kt`
5. `core/data/src/androidTest/.../capture/SplitImportReceiptReconciliationInstrumentedTest.kt`
6. `core/data/src/androidTest/.../model/ModelTaskRepositoryInstrumentedTest.kt`
7. `core/data/src/androidTest/.../study/StudySubmissionWithoutFixtureInstrumentedTest.kt`
8. `core/data/src/main/.../mistake/RoomMistakeDetailRepository.kt`
9. `core/data/src/main/.../study/RoomTutorToolRunner.kt`
10. `core/data/src/test/.../knowledge/GoldenRetrievalJvmTest.kt`
11. `core/data/src/test/.../model/OpenAiNativeToolsProtocolTest.kt`
12. `core/data/src/test/.../model/TutorToolRequestDualParseTest.kt`
13. `core/data/src/test/.../visual/VisualDrawOpsParserTest.kt`

`??`（1）：`core/data/gradle.lockfile`（未跟踪锁，**不得入库**，KD-72）。

### 2.4 core/database 开工即脏（10 = 9 M + 1 ??）

`M`（9）：
1. `core/database/src/androidTest/.../LibraryFtsCountPathInstrumentedTest.kt`
2. `core/database/src/androidTest/.../LibraryFtsCountPathScale50kInstrumentedTest.kt`
3. `core/database/src/androidTest/.../LibraryFtsRankingPathInstrumentedTest.kt`
4. `core/database/src/main/.../RoomBatchImportStore.kt`
5. `core/database/src/main/.../TutorConversationDatabaseContract.kt`
6. `core/database/src/main/.../dao/BatchImportDao.kt`
7. `core/database/src/main/.../dao/PendingCaptureDao.kt`
8. `core/database/src/main/.../dao/TutorConversationDao.kt`
9. `core/database/src/main/.../port/CleanRedrawTaskPort.kt`

`??`（1）：`core/database/gradle.lockfile`（未跟踪锁，不得入库）。

> **与批次 31 基线的差**：批次 31 记 11 条（10 M + 1 ??），多出的
> `core/database/src/test/.../KernelWave0SchemaContractTest.kt` 现已 clean —— 它随
> commit `c7a6c107`（69→70 断言结构读数化）入 HEAD，不是他线新写入。

### 2.5 本批目标文件开工状态

- schema 面文件**全部干净**：`core/database/schemas/`、`StudyDatabase.kt`、
  `dao/TutorVisualDao.kt`、`TutorVisualIdentity.kt`、`RoomStudyDatabase.kt`、
  `core/data/src/main/.../backup/{SmbkSelectionPlan,BackupSnapshotTrimmer}.kt`、
  `core/data/src/main/.../visual/`（整包）——`git status --porcelain` 零输出。
- parity 面文件**全部干净**：`VisualDrawOpsParityMetrics.kt`、
  `VisualDrawOpsParityInstrumentedTest.kt`。
- 唯二"本批要碰但已脏"的：`BackupRestoreInstrumentedTest.kt`（M，批次 30/31 修复未提交）、
  `DeviceMigrationDrillInstrumentedTest.kt`（M）——都属 core/data 他线在飞，改前须确认归属。

## 3. 一致门（parity）现状（工单第 3 项）

### 3.1 `centroidDeltaPx` 的实现（度量定义）

`VisualDrawOpsParityMetrics.kt:808-810`：

```kotlin
val centroidDelta = if (screenMass > 0.0 && pdfMass > 0.0) {
    hypot(pdfCentroidX / pdfMass - screenCentroidX / screenMass,
          pdfCentroidY / pdfMass - screenCentroidY / screenMass)
} else Double.NaN
```

- 这是**屏栅格质心与 PDF 栅格质心之间的欧氏距离**（跨栅格），不是同侧平移前后的质心差。
- 平移 PDF 侧 t 后读数 = `|v + t|`（v = 基线屏↔PDF 质心偏移），**方向相关**。
- KDoc 已按此重写：`VisualDrawOpsParityMetrics.kt:20-39`（"跨栅格欧氏距离…方向相关"）、
  `VisualDrawOpsParityInstrumentedTest.kt:39-48`（文件头）、`:299-314`（N09 用例注释）。

### 3.2 N09 断言行

- 代码：`VisualDrawOpsParityInstrumentedTest.kt:298` `fun n09OnePixelTextShiftIsCaught()`。
- 现断言：`:319` `assertRed(...)` + `:322-326` `assertEquals(F4_GEOMETRY, judgment.decision.verdict)`；
  质心/bbox/bestShift 仅进 `Log.i`（`:329-334`）。
- **旧的 `assertEquals(1.0, text.centroidDeltaPx)` 已不存在**（`git diff --quiet HEAD -- <visual/>` → exit 0，
  即工作树与 HEAD 同源）。
- 旧断言仍能在设备日志里看到：`build/device-track-batch31/step08-parity-logcat.txt:129`
  `expected:<1.0> but was:<1.2624767812258337>`；`step08/.log` 尾部 `Tests run: 15, Failures: 1`；
  `step08b-parity-rerun.log:236-237` 同值重跑仍 FAIL。
  → 上一次设备跑的源**早于** commit `f7c5d4b7`（2026-10-29 08:34:32，该 commit 已含修正）。

### 3.3 阈值与版本

- `VisualDrawOpsParityMetrics.kt:242` `const val THRESHOLD_VERSION = "calibrated-r1"`。
- `:248-256` `TEXT_THRESHOLDS`：`maxBboxDeviationPx=1`、`maxCentroidDeltaPx=0.75`、
  `minMassRatio=0.93`、`maxMassRatio=1.07`、`maxProfileTv=0.08`、`reportedIouLimit=0.99`。
- 判决读同一份数值：`:684`（质心）、`:675`（bbox）、`:692-697`（bestShift）。

### 3.4 设备读数出处（复读，非本轮重跑）

| 读数 | 数值 | 出处（文件:行） |
|---|---|---|
| P06 基线 | `bboxDev=1 centroid=0.32` | `build/device-track-batch30/step05-parity-logcat.txt:120` |
| P07 基线（噪声 0.518） | `bboxDev=1 centroid=0.52` | 同上 `:122` |
| P06 +1 px（N09） | `centroid=1.2624767812258337`、`bboxDev=2` | `build/device-track-batch31/step08-parity-logcat.txt:129`、`:126` |
| P07 +4 px（N12） | `centroid=3.56014654994809`、`bboxDev=5` | 同上 `:186` |
| N11 旋转 | `centroid=1.1665452698556462` | 同上 `:241-242` |
| N09-P07 | **未执行**（N09 循环在 P06 断言处中止） | `step08-parity.log` 仅 1 条 failure |

> 引用瑕疵（见 §8 D8）：`TextThresholds` KDoc（`VisualDrawOpsParityMetrics.kt:188`）把
> "N11 旋转 1.1665"与 0.518 一起挂在 `batch30 step05:119-122` 上，但该文件 **N11 命中数 = 0**
> （`grep -c "N11" …` → 0）；1.1665 实际在 batch31 `step08:241`。

## 4. schema 面（工单第 4 项）

| 项 | 实测 | 出处 |
|---|---|---|
| `StudyDatabase` 版本 | **70** | `StudyDatabase.kt:139`、`:242` |
| `schemas` 最高版本 | **70.json**（59–70 连续，共 12 个） | `find core/database/schemas/.../StudyDatabase -name "*.json"` |
| `70.json` version | **70** | python `json.load(...)['database']['version']` |
| `70.json` identityHash | **`11a8a701bae767a8a3c5bc810e4743a4`** | 同上 |
| `70.json` entities / views | **90** / **2** | 同上 |
| `SmbkSelectionPlan.SCHEMA_VERSION` | **70** | `SmbkSelectionPlan.kt:40` |
| `SmbkSelectionPlan.IDENTITY_HASH` | **`11a8a701bae767a8a3c5bc810e4743a4`**（与 70.json 逐字同） | `SmbkSelectionPlan.kt:43` |
| `CLOSURE_VERSION` | **3** | `:46` |
| 表/视图/顺序/闭包/软引用 | 90 表、2 视图、`trimOrder` 82、闭包 5 边、软引用 21 条、`allowedMasks={9,13,15}` | `:536-560` 的 `require` 全链；`:186-191` |
| 备份裁剪器版本门 | `database.version != SmbkSelectionPlan.SCHEMA_VERSION` → fail；`room_master_table.identity_hash != IDENTITY_HASH` → fail；清单缺表 → fail | `BackupSnapshotTrimmer.kt:266-284` |
| 71.json | **不存在** | `find . -name "71.json"`（排除 build）→ 空 |

- 迁移注册表最高到 `VISUAL_SNAPSHOT_MIGRATION_69_70`（`StudyDatabase.kt:409`），**无 70→71**。
- 与计划 §1 任务 4/5 的关系：本批需要新增 `70→71` 迁移、`71.json`、常量回填；当前全部未开工。

## 5. 持久化现状（工单第 5 项）

### 5.1 预算门位置：**事务外**（计划陈述成立）

- 判定在仓储层、任何 DB 写之前：`TutorVisualCommitRepository.kt:346` `planCheckpoint`，
  由 `:200`（stage）与 `:242`（commit）调用；`:170` 用 `checkpointPlanLock = Mutex()` 做**进程内**串行。
- 事务边界在 DB 层：`RoomStudyDatabase.kt:1970-1972`
  `commitTutorTurnWithVisual(...) = database.withWriteTransaction { ... }`。
- 事务内**没有**重读已用字节：`commitTutorTurnWithVisual` 内只有 ensure program/snapshot、
  append message、promote、link、demote（`:2031-2034`）、evict（`:2035-2039`）。
- 后果：多进程/绕过 Mutex 的并发可各自通过预算判定（"双过"）；Mutex 只在同进程同 repository 实例内生效。

### 5.2 evict SQL 谓词：**校验 pin/kind/status，不校验 STAGING 租约**（成立）

`TutorVisualDao.kt:168-186`：

```sql
DELETE FROM tutor_visual_snapshot
WHERE snapshot_id IN (:snapshotIds)
  AND (checkpoint_kind IS NULL OR checkpoint_kind = :latestKind)
  AND status = :stagingStatus
  AND NOT EXISTS (… tutor_message_visual.current_snapshot_id …)
  AND NOT EXISTS (… problem_revision_visual.snapshot_id …)
```

- 没有 `owner_request_id` / `created_at_epoch_millis` 条件 → 会删掉另一个仍在飞请求刚 stage 的 STAGING 行
  （批次 31 记录 §5 #8 已登记，未修）。
- 启动对账 `deleteStaleStagingSnapshots`（`:193-214`）**有**租约（`created_at <= staleBefore`），
  与 evict 不同口径。

### 5.3 pin 用例：**已有有分辨力用例，且设备已绿**

- 纯策略 JVM：`VisualCheckpointRetentionTest.kt:125` `a pinned intermediate is retained and not evicted`
  （pinned=true 为可淘汰形状，只因 pin 才留下）；另 `:144` 旧 LATEST 被 pin 只降级不删。共 17 例。
- DB/SQL 结构门（设备）：`TutorVisualStructureGatesInstrumentedTest.kt:156-212`
  `evictionDeletesOnlyUnpinnedStagingIntermediates` —— 显式构造 `snap-pinned-staging`（STAGING + 题面 pin）与
  `snap-linked-staging`（STAGING + 消息指针），并断言 pinned 读侧 1/1/0。
- 设备证据：E-09（`docs/research/evidence/2026-10-29-batch-31/step09-persistence-structure.md`）
  `OK (4 tests)`，逐例含 `evictionDeletesOnlyUnpinnedStagingIntermediates codes=['1','0']`。
- plan→command 端到端（设备**未跑**）：`TutorVisualCommitRepositoryInstrumentedTest.kt:180`
  `commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand`；该套件设备只跑了 4 例
  （文件现有 5 个 `@Test`），记录 §4.4 明说"新增 2 例未跑"。

### 5.4 snapshotId 复派生入参 + digest 校验

- 身份派生：`TutorVisualIdentity.kt:71-81`
  `deriveSnapshotId(programId, stateSha256, drawCommandsSha256)` = `sha256(programId + stateSha256 + drawCommandsSha256)`，
  取前 32 hex 加前缀 `visual-snapshot-`。**不含 renderer/font 版本**（计划任务 4 要并入）。
- 唯一键同源：`(program_id, state_sha256, draw_commands_sha256)`（Kdoc `:39-42`）。
- DB 层校验（HEAD 内）：`RoomStudyDatabase.kt:2174-2179`
  `require(stateSha256 == sha256Hex(stateJson))`、`require(drawCommandsSha256 == sha256Hex(drawCommandsJson))`；
  `:2185-2187` `require(snapshotId == derivedSnapshotId)`。
  → 计划 §0 的"未校验"**不成立**（见 §0 D4）。

## 6. AUD-08：`export_split_eval.py` 的快照 sha 是否含 `-wal`（工单第 6 项）

**不含。** 证据：

- `tools/split_golden/export_split_eval.py:72-73` `_sha256(path) = hashlib.sha256(path.read_bytes())`；
  `:221-222` `"db": {"file": db_path.name, "sha256": _sha256(db_path), "bytes": db_path.stat().st_size}`。
  只读主库文件字节。
- 全文 `grep -in "wal"` → **0 命中**（没有 `-wal`/`-shm` 参与任何计算）。
- 只读打开：`:76-79` `sqlite3.connect("file:…?mode=ro", uri=True)`。
- 已登记的已知限制：`tools/split_golden/README.md:86-89` 原文"已知限制（2026-10-29 实测，未修）：
  `source.db.sha256` 只覆盖主库字节，**不覆盖 WAL**……同一主库 + 不同 WAL 会得到不同导出却报同一 sha
  （受控实验：主库 sha 同为 `946b896c…`，带 WAL 1 页 2 框 / 无 WAL 0 页）"。
- 计划任务 10 的"把 `-wal` 纳入快照 sha 或登记为已知限制并加检测"：**登记已完成，检测/纳入未做**。

## 7. KD 原文与状态 + 设备在线（工单第 7 项）

出处统一为 `docs/known-defects.md`（KD-80 亦见 `docs/agent-first-refactor-decisions-2026-09-23.md:3532`）。

| KD | 标题（原文摘要） | 登记 | 当前状态 | 最新 Progress 要点 |
|---|---|---|---|---|
| **KD-71** | `QuickJsVisualExecutionEngine` close/execute 竞态：closed 检查与 `currentTransaction.set` 之间的窗口（低） | 2026-10-26 批次 21 | **open** | 修复已落（单锁状态机 + 有界等待）；批次 23 设备 20 例、两条用例 `STATUS_CODE 0`；"是否关闭待主会话裁定"（`:2727-2746`） |
| **KD-72** | dependency locking 已配置但仓库无已跟踪锁文件；DEFAULT 下锁门未生效，已生成锁与 HEAD 不一致（中） | 2026-10-26 | **open** | 13 个未跟踪锁（盘上 14 个）；`git ls-files '*gradle.lockfile'` = 0；`:benchmark` 缺锁；不得原样 `git add`（`:2754-2770`） |
| **KD-73** | 运行时 kotlin-stdlib 强制降回 2.3.21：字节码/API 兼容证据只覆盖 quickjs AAR，不是全解析图（中） | 2026-10-26 | **open** | 批次 31 无全解析图扫描产物；`check_dependency_pins.py` 只校版本不校 API（`:2782-2798`） |
| **KD-77** | API 23 运行时未验证：本机无 android-23 镜像，未执行 v21 APK 冒烟（中） | 2026-10-27 批次 24 | **open** | 批次 31 设备轨 15 份证据**全部 API 34**，无 API 23；关闭需修复后 APK 装机 + 冷启动 + 最小冒烟并落盘（`:2883-2905`） |
| **KD-80** | schema 70 落地后备份机器清单一度仍停在 69：v70 库备份/恢复 fail-closed（高） | 2026-10-29 批次 30 | **open（80-C1/C2 已核销；C3 PARTIAL、C4 零断言）** | 批次 31 设备 `BackupRestoreInstrumentedTest` `OK (34 tests)`；`70.json` 90 实体/2 视图/双向差集空；C4 对视觉四表引用**零断言**（`:2967-2985`） |

**设备在线（只读）**：`C:/Android/Sdk/platform-tools/adb.exe devices -l`

```
emulator-5562  device product:sdk_gphone64_x86_64 …
emulator-5566  device product:sdk_gphone64_x86_64 …
emulator-5584  device product:sdk_gphone64_x86_64 …
```

| serial | `ro.build.version.sdk` | `getconf PAGE_SIZE` | `ro.product.cpu.abi` |
|---|---|---|---|
| emulator-5562 | 34 | 4096 | x86_64 |
| emulator-5566 | 34 | 4096 | x86_64 |
| emulator-5584 | 34 | 4096 | x86_64 |

三台均为 API 34 / x86_64 / 4 KB 页；**无 API 23**（与 KD-77 open 一致）。

## 8. 与计划 §0 的逐条对照（含不符处与未验证项）

| # | §0 陈述 | 本轮判定 | 证据 / 原因 |
|---|---|---|---|
| 1 | 设备绿：备份 34/34、69→70 4/4、迁移 1→70 15/15、painter 24/24、隔离 13/13、flags 2/2、PDF 16/16、结构门 4/4、提交 9/9、仓库 4/4 | **复读一致，非本轮重跑** | E-01/E-04/E-09 等证据文件；`TutorVisualCommitInstrumentedTest` 现有 10 `@Test`（设备 9）、`TutorVisualCommitRepositoryInstrumentedTest` 现有 5（设备 4）、`VisualDrawOpsPainterInstrumentedTest` 24 `@Test` |
| 2 | 一致门 FAIL 14/15，N09-P06 期望 1.000、实测 1.2625 | FAIL 事实成立（旧源）；**源码断言已改** | `step08-parity.log` `Tests run: 15, Failures: 1`；现树 `VisualDrawOpsParityInstrumentedTest.kt:298-335` |
| 3 | `calibrated-r1` 仍是 UNVERIFIED | **成立** | 无绿轮证据；旧源套件红 |
| 4 | schema 71 推迟，本批"先生成、后回填、再验证" | **成立** | 无 `71.json`；版本 70；迁移最高 69→70 |
| 5 | 预算门在事务外（并发可双过） | **成立** | `TutorVisualCommitRepository.kt:346` 在 `RoomStudyDatabase.kt:1971` 事务之前；事务内无字节重读 |
| 6 | evict 只经手写候选验证，plan→command 未端到端 | **不成立（陈述过期）** | 端到端用例在 `TutorVisualCommitRepositoryInstrumentedTest.kt:180`，但**未上设备** → 应改写为"已落码、待设备" |
| 7 | pin 谓词无有分辨力用例 | **不成立（陈述过期）** | `TutorVisualStructureGatesInstrumentedTest.kt:156-212` 有 STAGING+pin 行；E-09 设备绿 |
| 8 | snapshotId 复派生用调用方 digest，DB 层未校验 digest==sha256(json) | **不成立（陈述过期）** | `RoomStudyDatabase.kt:2174-2187` 双向校验，HEAD 内 |
| 9 | evict SQL 不校验 STAGING 租约 | **成立** | `TutorVisualDao.kt:168-186` 无 owner/created_at |
| 10 | AUD-08 `source.db.sha256` 不含 WAL | **成立** | `export_split_eval.py:221`；README `:86-89` |
| 11 | 台账反向虚报需回改 | **未核对（本轮不审台账）** | 需任务 D 单独比对；本轮只记录 |
| 12 | 计划 §2"设备 APK 与现树不同源，本批必须重建重装" | **成立且更严重** | E-09 记 `head 59d4a03f`（worktree dirty，零写入）；现 HEAD `0b4bdbbe`，且批次 31 后又有修正 |

### 8.1 引用/文档瑕疵（不影响判定，编译期不报）

| # | 位置 | 问题 |
|---|---|---|
| D8a | `VisualDrawOpsParityMetrics.kt:188`（`TextThresholds` KDoc） | 把"N11 旋转 1.1665"挂在 `batch30 step05:119-122`；该文件 `grep -c N11` = 0，1.1665 实际在 batch31 `step08:241-242` |
| D8b | `VisualDrawOpsParityInstrumentedTest.kt:48` | 文件头写"painter 套件（设备 19 例 + JVM 10 例）"；现树 `VisualDrawOpsPainterInstrumentedTest` = 24 `@Test`，E-04 也是 `OK (24 tests)` → "19" 是旧数 |
| D8c | 证据 `00-index.md:150` / `step08-parity.md:93` 引 `VisualDrawOpsParityMetrics.kt:798-802` | 实际 `hypot` 在 `:808-810`（行号漂移 6–10 行） |

## 9. 本轮未执行 / UNVERIFIED 清单

- 未运行 Gradle（assemble / unit test / lint），未重跑任何仪器化套件；
  §1/§7 的"设备绿"全部是**复读证据文件**。
- 未打开设备上的应用私有 DB，运行时 `PRAGMA user_version` / `room_master_table.identity_hash`
  的实际值 `UNVERIFIED`（只核了源码常量与 `70.json`）。
- KD-80 C3 的 JVM 半边、C4 的四表引用断言：沿用批次 31 结论（PARTIAL / UNVERIFIED），本轮未补。
- 计划 §0 "台账反向虚报"：本轮未逐条审 `docs/agent-first-refactor-decisions-2026-09-23.md` 的计数，
  仅定位到 KD 裁决行（`:3505/:3510/:3530/:3532`）。

## 10. 对批次 32 执行的直接影响

1. **任务 A1 的源码编辑已完成**（`f7c5d4b7`）；不要重复"删除 1.000 断言"。真正剩余：
   设备复跑取 `OK (15 tests)`（须用**现树**重建 APK，见 §8 #12），并让 N09-P07 真正执行。
2. **任务 C7/C8 不能按 §0 字面重复施工**：plan→command、pin 有分辨力、digest 校验三处在 HEAD 已具备；
   缺的是**设备执行**与**预算门入事务**（§5.1）两项。任务 8 的"补 pin 用例"应改成"把已有用例
   在现树 APK 上跑出绿并落盘"。
3. **任务 C9 的 evict 租约条件仍缺**（§5.2），成立、需落码。
4. **任务 B（schema 71）**：本批唯一 schema 写者，当前 70→71 全未开工，且要同步
   `SmbkSelectionPlan` 常量（表数/软引用/CLOSURE/identityHash）与 `BackupSnapshotTrimmer` 版本门。
5. **任务 D10 的 AUD-08**：WAL 已登记为已知限制；若选"纳入 sha"要同时改受控实验与 README 冻结纪律。
6. 热区零漂移，执行轨仍须以 §2 的 12 项为禁改边界（计划本身未枚举热区）。
