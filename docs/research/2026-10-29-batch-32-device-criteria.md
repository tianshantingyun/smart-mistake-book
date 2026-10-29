# 批次 32 T1 判据与命令清单：一致门 / 迁移 1→71 / 备份 34/34 / 结构门 / schema 71 三段 / 串行命令与 dex 门 / 红例归因

> **性质**：判据与命令清单。本轨**只写判据，不执行设备**；设备执行归设备轨。
> **本会话实跑（只读）**：`git rev-parse`、`git status --porcelain`、`grep -c '@Test'`、`grep -n`、
> `python` 读 `70.json`、`unzip -l`、`stat`。**零 Gradle、零 adb / am instrument、零 git 写、零产品源码改动。**
> 文中所有 `OK (N tests)`、`FAIL`、`PASS` 都是**目标或历史证据的复读**，不是本轮结果。
>
> 输入：计划 `.zcode/plans/plan-2026-10-29-batch-32-parity-schema71.md`（全文）、
> 批次 32 基线 `docs/research/2026-10-29-batch-32-baseline.md`（全文）、
> 一致性度量纠正 `docs/research/2026-10-29-parity-metric-correction.md`（全文）、
> schema 生成链路 `docs/research/2026-10-29-schema-generation-flow.md`（全文）、
> 批次 31 设备判据 `docs/research/2026-10-29-batch-31-device-criteria.md`（全文）、
> 批次 31 证据 `docs/research/evidence/2026-10-29-batch-31/`（E-01/E-03/E-08/E-09/E-11/E-12）、
> 现树源码/测试实读。
>
> **快照时点**：2026-10-29 09:58 +0800，HEAD `0b4bdbbea918ba6b8c8e1f3b190b8be502ec627a`。
> **工作树正在被 T3/T4 改写**（§1.1）：本文件的计数与常量是**该时点复读值**，设备轨必须以
> **构建前最后一次复读**为准（§1.3），不得把本文件的数字当冻结常量。

---

## 0. 结论速览

| # | 判据 | 现树事实（本会话实测） | 设备目标 |
|---|---|---|---|
| A | 一致门 `VisualDrawOpsParityInstrumentedTest` | **16** `@Test`（`calibrated-r2`，含新增 N13） | `OK (16 tests)`；N09-P06/P07、N10、N11、N12 全部 `red=true` |
| B1 | 迁移矩阵 `FullMigrationMatrixInstrumentedTest` | **15** `@Test`；循环 `1..STUDY_DATABASE_VERSION`；常量已改 **71** | `OK (15 tests)`；覆盖 **1..71** |
| B2 | 69→70 套件 `TutorVisualSnapshotMigration_69_70InstrumentedTest` | **4** `@Test`（M，T3 在飞） | `OK (4 tests)`；v69 库升到 **71** |
| B3 | 70→71 专用套件 `TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest` | **3** `@Test`（批次 32 T3 已落，`??` 未跟踪） | `OK (3 tests)`（迁移/旧行保持/幂等、版本换新行、五列查重、唯一键拒绝） |
| C | 备份 `BackupRestoreInstrumentedTest` | **34** `@Test`（M，他线在飞） | `OK (34 tests)`；8 条旧红全绿；schema68→71 |
| D | 持久化结构门 `TutorVisualStructureGatesInstrumentedTest` | **5** `@Test`（批次 32 T5 +1：仅因 pinned 才保留的有分辨力用例） | `OK (5 tests)` |
| D+ | 提交 / 仓库 `TutorVisualCommitInstrumentedTest` / `TutorVisualCommitRepositoryInstrumentedTest` | **11 / 8** `@Test`（批次 32 T5：提交 +1 draw-digest 负向；仓库 +3 租约/陈旧读×2，evict 端到端为既有用例改强） | `OK (11 tests)` / `OK (8 tests)`，以复读为准 |
| S1 | schema 71 生成物 | 09:58 快照：`71.json` 不存在 / `SCHEMA_VERSION=70`；**>10:13 已生成 + 回填**（见 §0 接线后记） | 71.json 存在且 hash 与回填常量逐字一致（`714c0ecb…`） |
| S2 | 迁移矩阵 1→71 | 同 B1 | 同 B1 |
| S3 | 备份清单逐名 | 同 C + `SmbkSelectionPlan.rowCountKeys` 90 表 | 90 表逐名、无 missing/extra |

**三条硬结论**：

1. **批次 31 的设备 APK 与现树不同源，本批必须重建重装。** 证据不止“HEAD 不同”：现树 parity
   测试在 `08:34:32` 提交（`f7c5d4b7`）、结构门在 `08:33:55` 提交（`5fee43c5`），而批次 31 的
   APK 构建于 `06:28:19–06:28:20`（§1.4）。批次 31 的 APK/日志**不得**作为本批任何绿轮证据。
2. **schema 71 的生成与回填已完成（批次 32 集成接线后记，实读 >10:13），中间态已消除**：
   `71.json` 已生成（`database.version=71`、`identityHash=714c0ecb85ee5edf74f0013740148138`、
   90 entities / 2 views，mtime 10:13），`SmbkSelectionPlan.SCHEMA_VERSION=71` 且
   `IDENTITY_HASH` 与 `71.json` 逐字相同；生成的 `StudyDatabase_Impl.kt` 里
   `RoomOpenDelegate(71, "714c0ecb…", …)` 同值，迁移 DDL（`${TABLE_NAME}` 替换后）与
   `71.json` 的 `createSql` 逐字一致。**构建前仍必须按 §1.3 复读**（§7.1 构建前门失败即停）；
   09:58 的“中间态”表述只对该时点成立，不得再据此阻止重建。
3. **一致门仍是原型门**（`VisualDrawOpsParityInstrumentedTest.kt:100` `product-gate=no`）。
   16/16 绿只证明“PDF 路径相对屏路径未引入新偏差”，**不得**写成产品门 / 发版结论。

> **接线后记（2026-10-29 批次 32 集成，T3 生成 + T4 回填之后；实读 >10:13）**：
> §0/§1.1 里 `71.json 不存在 / SCHEMA_VERSION=70 / IDENTITY_HASH=11a8a701…` 只属于 09:58 快照，
> **现树已取代**：`core/database/schemas/…/71.json` 存在（`version 71` /
> `identityHash 714c0ecb85ee5edf74f0013740148138` / 90 entities / 2 views，378,961 B，
> mtime 2026-10-29 10:13，`??` 未跟踪——提交必须纳入）；`SmbkSelectionPlan` 两常量已回填
> `71` / `714c…`；`StudyDatabase_Impl.kt` 的 `RoomOpenDelegate(71, "714c…", …)` 与 `71.json`
> 同值。设备轨仍按 §1.3 在构建前复读，不把本文件当冻结常量。

---

## 1. 快照与计数冻结

### 1.1 当前在飞状态（2026-10-29 09:58 实测）

| 项 | 实测 | 证据 |
|---|---|---|
| HEAD | `0b4bdbbe` | `git rev-parse --short HEAD` |
| `STUDY_DATABASE_VERSION` | **71**（`M`，T3 在飞） | `StudyDatabase.kt:139` |
| `SmbkSelectionPlan.SCHEMA_VERSION` | **70**（未回填，符合“先生成后回填”顺序） | `SmbkSelectionPlan.kt:40` |
| `SmbkSelectionPlan.IDENTITY_HASH` | `11a8a701bae767a8a3c5bc810e4743a4`（仍是 70 的值） | `SmbkSelectionPlan.kt:43` |
| 71.json | **不存在** | `find core/database/schemas -name "71.json"` → 空 |
| 导出 schema 数 | **70** 个 `*.json` | `ls ... \| grep -cE '^[0-9]+\.json$'` |
| 70.json | `version=70`、`identityHash=11a8a701…`、90 entities / 2 views | python 实读 |
| 迁移注册 | 最高 `VISUAL_SNAPSHOT_MIGRATION_69_70`；新增 `VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71` 已注册 | `StudyDatabase.kt:409,411` |
| 70→71 迁移实现 | **已落**（untracked `VisualSnapshotIdentityMigration_70_71.kt`）：DROP 旧三列索引 + CREATE 五列唯一索引；**无触发器 backstop**（文件内写明本批裁定不加） | 文件实读 |
| parity 阈值版本 | **`calibrated-r2`**（`M`，批次 32 T2） | `VisualDrawOpsParityMetrics.kt:262` |
| parity 数值 | `bbox<=1px`、`centroid<=0.75px`、`mass[0.93,1.07]`、`tv<=0.08`（数值沿用 r1） | `:270-274` |
| `PARITY_GATE` 日志串 | `thresholds=calibrated-r2`、`calibration=r2-criterion-corrected(numerics=r1)`、`blindSet=text-centroid B(-v,tau_c) per-fixture (direction-dependent)`、`displacement-exits=bestShift(main)+bboxDev(aux)`、`verification=only-from-suite-run-results`（现树实读；旧稿 `device-evidence=…` 已被代码取代） | `VisualDrawOpsParityInstrumentedTest.kt:100-108` |

> **在飞纪律**：T3/T4 仍在写。设备轨开跑前必须确认上述目标文件**不再变化**（构建前后 sha256 快照一致，
> §1.3 第 5 条）；否则判 D（构建早于源改动），不得把中间态 APK 当证据。
>
> **接线后记覆盖**：本表 `SmbkSelectionPlan.SCHEMA_VERSION` / `IDENTITY_HASH` / `71.json` /
> 导出 schema 数四行是 09:58 值；>10:13 后已改为 `71` / `714c0ecb85ee5edf74f0013740148138` /
> 存在（90 entities·2 views）/ 71 个，详见 §0 接线后记与 §1.3 的构建前复读。

### 1.2 套件计数（本会话 `grep -c '@Test'` 实跑，09:58）

| 套件（文件） | 计数 | 归属/状态 |
|---|---|---|
| `core/data/.../backup/BackupRestoreInstrumentedTest.kt` | **34** | `M`（他线在飞） |
| `core/database/.../TutorVisualSnapshotMigration_69_70InstrumentedTest.kt` | **4** | `M`（T3 在飞） |
| `core/database/.../FullMigrationMatrixInstrumentedTest.kt` | **15** | clean |
| `core/data/.../visual/VisualDrawOpsPainterInstrumentedTest.kt` | **24** | clean |
| `app/.../visualjs/VisualJsIsolationInstrumentedTest.kt` | **13** | clean |
| `app/.../visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt` | **2** | clean |
| `core/data/.../visual/VisualDrawOpsPdfPainterInstrumentedTest.kt` | **16** | clean |
| `core/data/.../visual/VisualDrawOpsParityInstrumentedTest.kt` | **16** | `M`（批次 32 T2；批次 31 为 15，新增 N13） |
| `core/database/.../TutorVisualStructureGatesInstrumentedTest.kt` | **5** | `M`（批次 32 T5 +1：仅因 pinned 才保留的 plan→command→SQL 用例） |
| `core/database/.../TutorVisualCommitInstrumentedTest.kt` | **11** | `M`（批次 32 T5 +1：draw-digest 负向；批次 31 设备跑 9） |
| `core/data/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt` | **8** | `M`（批次 32 T5 +3：租约 1 + 陈旧读 2；批次 31 设备跑 4） |
| `core/database/.../TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest.kt` | **3** | `??`（批次 32 T3 新增） |
| `core/data/.../backup/BackupSnapshotTrimmerInstrumentedTest.kt` | **9** | clean |
| `core/data/src/test/.../backup/SmbkSelectionPlanTest.kt`（JVM） | **12** | `M`（批次 32 T4），已指向 `71.json`（`SCHEMA_71_PATH`） |

### 1.3 计数冻结规则（设备轨强制）

1. 构建前对 §1.2 全部套件再跑一次 `grep -c '@Test'`，把结果写进证据头（`counts-before-build`）。
2. `am instrument` 的 summary 目标 = **最后一次复读的 N**（`OK (N tests)`）；不得预先写死本文件的 16/4/15/34。
3. `N_run < N_frozen` → **D（旧包/装错包）**：停，查 sha256 与 dex 门，重装重跑；不得把少例读成通过。
4. `N_run > N_frozen` → 构建后仍有落码：停，重新复读并重建；不得“多跑的几例不管”。
5. 构建前/后对 `app/src`、`core/data/src`、`core/database/src` 做 sha256 快照并 `diff`；不一致 = 构建早于源改动，判 **D**。
6. 70→71 专用套件的类名/方法名**以落码后 `grep` 实读为准**；本文件不预写未落码的名字（§3.3）。

### 1.4 批次 31 设备 APK 与现树不同源（硬证据）

| 证据 | 实测 |
|---|---|
| 批次 31 设备证据头 | E-01/E-09 记 `head=59d4a03f`（`step01-backup.md`、`step09-persistence-structure.md` 头表） |
| 现 HEAD | `0b4bdbbe`（批次 31 之后至少 2 个测试提交） |
| parity 测试提交 | `f7c5d4b7 2026-10-29 08:34:32`（`git log -1 --format='%ci'`） |
| 结构门提交 | `5fee43c5 2026-10-29 08:33:55` |
| APK 构建时间 | `06:28:19–06:28:20`（`stat -c '%y'` 三个 APK） |
| parity 源 mtime | `08:01:48`；结构门源 mtime `07:59:39` |
| APK 内陈旧资产 | `unzip -l database-debug-androidTest.apk` 含 `assets/.../70.json.NOT_EXPORTED.md`；该文件在现树 `find . -name "*NOT_EXPORTED*"` → **不存在** |

**结论**：批次 31 的 APK 早于 parity/结构门源改动，且带有现树已不存在的资产。**必须
`assemble*` 重建 + `install -r -t` 重装**；批次 31 的 `build/device-track-batch31/*` 只能作为
“旧轮读数出处”，不得作为本批绿轮证据。

### 1.5 热区与脏文件

- 热区（禁改）：`feature/tutor`、`feature/library`、`feature/review`、`feature/capture`、
  `feature/profile`、`core/ui` 全部、`core/markdown` 整目录、`core/export` 全部、`core/domain`、
  `core/data` 他线 M 与 knowledge/dense 面、`settings.gradle.kts`、`.gitignore`、`app` Root 与
  开工即脏 M、`docs/current` 其余 M、`docs/research` 他线未跟踪记录、`tools/kb`、`knowledge-production`。
- 本批目标文件开工即脏：`BackupRestoreInstrumentedTest.kt`（`M`）、`DeviceMigrationDrillInstrumentedTest.kt`
  （`M`）——属 core/data 他线在飞；改前须确认归属，变脏则整文件纳入并注明或停项登记。
- schema 71 单写者 = T3（生成与回填同轨）；T1 不写 schema 面代码。

---

## 2. 判据 A：一致门（parity）

**文件/类**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt`，
类 `com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest`。

### 2.1 套件目标

- summary 必须是 **`OK (16 tests)`**（本快照；构建前按 §1.3 复读；若 T4 再加/删用例，以复读为准）。
- 0 failures / 0 errors / 0 skipped。
- `INSTRUMENTATION_CODE: -1` **不是判据**；宿主 shell `EXIT=0` **不是判据**。
- **禁用 `connectedAndroidTest`**；只允许 `assemble*` + `install -r -t` + `am instrument`。

### 2.2 负控必须红（本批标定许可证）

设备日志里每个 `PARITY_CONTROL ` 行必须 `red=true`。本快照负控：

| 负控 | 用例/行 | 必须成立 |
|---|---|---|
| N09-P06 | `n09OnePixelTextShiftIsCaught`（`:309`），P06 +1 px | `assertRed`；质心读数 = `1.2624767812258337`（容差 `1e-6`，`:902-903`）；判决 `F4_GEOMETRY` |
| N09-P07 | 同上，P07 +1 px | `assertRed`；质心读数 = `0.608282132823731`（容差 `0.02`，`:904-905`）；**质心必须盲**（`<= 0.75`）；判决 `F4_GEOMETRY`（红来自 bbox/bestShift） |
| N10 | `n10WholePageMisdrawIsCaught`（`:450`） | `assertRed`；`pdfBox == null`、`bboxDev == Int.MAX_VALUE`、`F4_GEOMETRY` |
| N11 | `n11ShapeWrongButPositionAndMassRightIsCaught`（`:466`） | `assertRed`；旋转前后 `bboxDev` 与 `massRatio` 逐位不变；判决红 |
| N12 | `n12WholeBlockShiftBeyondToleranceIsCaught`（`:500`） | `assertRed`；`centroidDeltaPx > 0.75`；判决红 |
| N13 | `n13CentroidBlindSpotIsCoveredByBboxOrShift`（`:368`） | 合成栅格：质心 `0.0`、墨量 `1.0`、`bboxDev > 1`、`bestShift != (0,0)` 且 gain `> 0.02`、判决 `F4_GEOMETRY`；`reasons` 必须点名 `text bboxDev=` 或 `text bestShift=` |

**N10/N11/N12 任一绿 = `PARITY_GATE_DESENSITIZED`，判 A（门失去分辨力），本轮标定作废；不得调阈值让它绿。**
N09-P06/P07 任一绿同理；N13 绿 = “质心不可靠时由 bbox/shift 兜底”不成立，同样作废。

### 2.3 套件级 logcat 判据

```bash
LOG=build/device-track-batch32/step08-parity-logcat.txt
RUN=build/device-track-batch32/step08-parity.log

grep -c "PARITY_GATE scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no" "$LOG"   # == 测试数（每例 @Before 一次；本快照 16）
grep -c "thresholds=calibrated-r2" "$LOG"                                                 # == 测试数（本快照 16）
grep -c "blindSet=text-centroid B(-v,tau_c) per-fixture (direction-dependent)" "$LOG"     # == 测试数（证明不是旧 (0,0.76) 标量窗口）
grep -c "displacement-exits=bestShift(main)+bboxDev(aux)" "$LOG"                          # == 测试数
grep -c "PARITY_CONTROL " "$LOG"                                                          # == 15（N01×4 + N02–N07×6 + N09×2 + N10 + N11 + N12；N08/N13 不打印该行）
grep "PARITY_CONTROL " "$LOG" | grep -vc "red=true"                                       # 0
grep -c "PARITY_GATE_DESENSITIZED" "$RUN"                                                 # 0
grep -c "PARITY_SYSTEMATIC_DRIFT"  "$RUN"                                                 # 0
grep -c "PARITY_DIAGNOSTIC_WRITTEN" "$LOG"                                                # 0（PNG 只在失败时写）
grep "PARITY_ENV" "$LOG" | tail -1                                                        # 含 replayTextPaintFlags=0
grep "PARITY_F5"  "$LOG" | grep -c "class=TEXT"                                           # 0（TEXT 无隔离带）
```

- `PARITY_CONTROL_DETAIL` 是附加明细（N09×2、N11、N12、N13），**不得**计进 `PARITY_CONTROL ` 控制数。
- 旧 APK 的判别串：`blindWindow=text-centroid(0,0.76)px`、`thresholds=calibrated-r1`。出现任一 = **D（旧包）**。
- `PARITY_ENV` 必须含 `replayTextPaintFlags=0`（AA 关）；`1283` = 环境漂移（先判 D/环境，再判 C）。

### 2.4 N09-P07 的已知风险（必须写进证据）

- P07 `d(+1) = 0.608282` 是**由 N12 的 +4 读数反解的预测**，批次 31 **从未执行**（N09 循环在 P06 中止）。
  本批是它第一次上设备。
- 若 N09-P07 的质心等式红但判决仍红：先按 §8 归因（B：期望/容差；A：夹具；D：跨设备/AA 状态），
  **不得**为了让它绿而放宽 `maxCentroidDeltaPx` 或把 P07 从用例里删掉。
- 若 N09-P06 的 `1e-6` 等式红：跨构建逐位相等**未验证**（纠正文档 §5-3），先判 D（换设备/AA 状态）或 B（容差），
  再判 C；不得把 1e-6 改成“随便一个数”。

### 2.5 原型门边界

- `product-gate=no` 行必须在；**不得**把 16/16 绿写成产品门、发版门、“PDF 可用”。
- 本门只证“PDF 路径相对屏路径未引入新偏差”；屏侧正确性归 painter 套件（24 例）。
- 不覆盖跨设备/跨 API/fontScale/其它字形；跨设备像素差判 **D**，不作产品缺陷。

---

## 3. 判据 B：迁移 1→71

### 3.1 迁移矩阵（`FullMigrationMatrixInstrumentedTest`）

**目标**：`OK (15 tests)`（构建前复读；若 T3 在该文件另加 70→71 专项，按复读计数）。

第 1 例是矩阵循环（`FullMigrationMatrixInstrumentedTest.kt:38`）：

```kotlin
for (version in 1..STUDY_DATABASE_VERSION) { ... }
```

当前常量 **71** ⇒ 必须覆盖 **1..71**（71 个版本点）。**证明覆盖范围的三条硬门**：

```bash
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
echo "V=$V"                                                   # 必须 71

unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -E 'StudyDatabase/[0-9]+\.json$' | wc -l             # 必须 == V（71）
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -q "StudyDatabase/$V.json" || echo "MISSING $V.json" # 不得输出 MISSING

git ls-files --error-unmatch \
  "core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/$V.json"  # 必须已跟踪
```

- 任一版本缺 schema/迁移，矩阵会在该版本中止 → **C（产品/迁移缺口）**；不得删该版本或缩小循环。
- 矩阵用每个版本的导出 `setupQueries` 建库再 `StudyDatabaseFactory.open`，因此它同时是
  **“JSON 哈希 ↔ Impl 哈希”的运行时交叉校验**（设计文档 §1.4/§3.5）：71.json 的哈希与生成代码不一致时，
  会在打开时报 `Room cannot verify the data integrity`。

### 3.2 69→70 套件（`TutorVisualSnapshotMigration_69_70InstrumentedTest`）

**目标**：`OK (4 tests)`（现树 4；T3 在飞，构建前复读）。

- 第 1 例从 v69 库打开后必须迁到 **`STUDY_DATABASE_VERSION`（现在 71）**；执行顺序：
  数据保全 → 外键负向 → 形状（`inspectV70Shape`）。四表建出且为空、旧行一行不丢、再次打开幂等。
- 第 3/4 例：迁移 DDL 常量与 PRAGMA 结构读数同源；负控能对错列序/非唯一/不存在索引判不等。
- **v71 分支**：v69 库要连续走 69→70→71。第 1 例的 v70 形状断言若仍期待旧三列唯一索引，
  会与 71 的 DROP+CREATE 冲突 → 先查是否旧包（D），再查迁移/断言（C/A）。
- 只跑 2 例 = 旧包（D）；只跑 4 例但 70→71 未生效 = C（迁移未注册/未执行）。

### 3.3 70→71 专用套件（本批必须新增，当前尚无）

- 计划任务 6 与设计文档 §2.8 要求带数据的 70→71 迁移验证；当前树只有迁移实现
  `VisualSnapshotIdentityMigration_70_71.kt`，**没有对应仪器化套件**（`find core -iname "*70*71*"` 只命中主源码）。
- 设备轨**不得**用“迁移矩阵 15 例绿”代替它：矩阵是空库，看不出“旧行还在/新键生效/身份负向”。
- 必须覆盖（设计 §2.8 M1–M10，去掉触发器项见 §5.4）：
  1. **M1** 用 70.json 建库、灌 1 行 v70 快照（三列键），打开后迁到 71：行还在、旧三列索引消失、新五列唯一索引存在。
  2. **M2（身份负向，计划任务 6）** 同 `(program,state,draw)`、新 `renderer/font` 再提交 → **新增一行**（COUNT=2），`snapshot_id` 与旧行不同。
  3. **M3** 同五列再插 → `UNIQUE constraint failed`（五列）。
  4. **M4** 故意不 DROP 旧索引 → Room `Migration didn't properly handle`（证明校验兜底真实存在）。
  5. **M9** 迁移失败注入 → 库仍 v70，旧索引/数据不变（回滚）。
  6. **M10** 71.json 的 `setupQueries` 哈希 vs 生成代码：改坏 JSON 哈希 → `Room cannot verify the data integrity`。
- 类名/方法名以落码后 `grep` 实读为准，写入 dex 门（§7.4）；本文件**不预写**未落码的名字。
- 触发器 M5–M8 **不在本批验收**：迁移文件明确写“本批不加触发器 backstop”（§5.4），
  设备判据不得声称 DB 级触发器已生效。

### 3.4 schema 资产门（与 §3.1 同一条，单列强调）

矩阵按 **androidTest assets** 建库；`core/data/build.gradle.kts:36` 把 `core/database/schemas`
挂到 `androidTest` assets。`71.json` 必须同时满足：文件存在、`database.version == 71`、在 APK assets 内、
被 `git ls-files` 跟踪。四缺一 → 停。

---

## 4. 判据 C：备份 34/34

**文件/类**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt`，
类 `com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest`。

### 4.1 套件目标

- summary **`OK (34 tests)`**（构建前复读）；0 failures / 0 errors / 0 skipped。
- 每例 `INSTRUMENTATION_STATUS_CODE: 0`。

### 4.2 批次 30 的 8 条红例必须全绿（本会话逐名确认在现树）

| # | 用例 |
|---|---|
| 1 | `fullRestoreLeavesDerivedSearchIndexEmptyAndMarksItForRebuild` |
| 2 | `backupClosureIncludesGenerationAttemptOutputs` |
| 3 | `settingsSurviveBackupAndRestoreRoundTrip` |
| 4 | `duplicateConcurrentRestoreRequestsAreRejectedIdempotently` |
| 5 | `schema68ArchiveRestoresAndMigratesToCurrentVersion`（`:488`） |
| 6 | `backupCreatedWhileACaptureIsRegisteredKeepsTheArchiveReferenceClosed` |
| 7 | `backupDeleteAndRestoreRoundTripPreservesCatalog` |
| 8 | `settingsWriteFailureIsReportedAsPartialWithoutRollingBackTheRestoredDatabase` |

### 4.3 schema 68 → 71（版本接缝）

- `schema68ArchiveRestoresAndMigratesToCurrentVersion` 断言恢复后 `readDatabaseVersion() ==
  SmbkSelectionPlan.SCHEMA_VERSION`（`:590`）。71 回填后，它必须读 **71**，不是字面量 70/69。
- 回执里的版本仍是包自己的 **68**（`:577-581`）；不得把回执版本改成 71。
- 旧（≤70）`.smbk` 快照在 71 裁剪器下**不可裁剪**是既有 fail-closed 的延续（设计 §3.4 U9），
  本批**不**要求兼容；不得为此放宽 `BackupSnapshotTrimmer` 的版本门。

### 4.4 套件级附加判据

```bash
grep -c "BackupSnapshotTrimException" build/device-track-batch32/step01-backup.log        # 0
grep -c "expected:<70>"               build/device-track-batch32/step01-backup.log        # 0
grep -c "expected:<71>"               build/device-track-batch32/step01-backup.log        # 0（期望应读常量，不打印字面量）
grep -c "裁剪器只支持 schema="         build/device-track-batch32/step01-backup-logcat.txt # 0
```

- 若出现 `快照 schema=71，裁剪器只支持 schema=70`：先查 `SmbkSelectionPlan.SCHEMA_VERSION`（`:40`）
  与 `BackupSnapshotTrimmer` 版本门（`:267-284`）。新包仍 70 → **C（回填未完成）**；旧包 → **D**。
- 若出现 `expected:<70> but was:<71>`：装的是旧测试包，判 **D**。

### 4.5 备份清单逐名（与 §6 Stage 3 同一判据）

- **归档侧**：v3 manifest 的 `selection.rowCounts` 必须**恰好覆盖 90 张表**，missing/extra 逐名报出
  （`SmbkArchiveCodec.kt:464-468`；`SmbkSelectionPlan.rowCountKeys = allTables.toSet()`，`:191`）。
- **恢复侧**：manifest `rowCounts` 的 90 个键与包内库 `COUNT(*)` **逐表相等**（`AndroidBackupRepository.kt:1224-1226`）。
- **选择计划侧**：`SmbkSelectionPlan` 的 90 表 / 2 视图 / 82 裁剪项 / 5 条闭包 / 21 条软引用
  （`SmbkSelectionPlan.kt:538-554`）与 71.json 逐名一致（§6 Stage 3 脚本）。
- 34/34 绿 + 表清单 PASS **仍不足以**证明 KD-80 80-C4（视觉四表引用关系保持）——
  现 34 例无 `tutor_visual` 行/引用断言（批次 32 基线 §3.4）。本批若要关闭 80-C4，必须补断言；
  否则如实写 `80-C4 UNVERIFIED`。

### 4.6 裁剪器 9 例（KD-80 80-C3 设备半边）

`BackupSnapshotTrimmerInstrumentedTest` **9** `@Test`（本会话复读），目标 `OK (9 tests)`。
JVM 半边 `SmbkSelectionPlanTest` **12** 例，目标 12/12；`SCHEMA_70_PATH` 已由 T4 改为
`SCHEMA_71_PATH`（现树 `:610-611`，指向 `71.json`），JVM 面待执行（本批未跑 Gradle）。

---

## 5. 判据 D：持久化结构门

### 5.1 结构门（`TutorVisualStructureGatesInstrumentedTest`）

**目标**：`OK (4 tests)`。4 条方法（本会话实读）：

| 用例 | 必须成立 |
|---|---|
| `messageLinkRejectsForeignKeyProgramAndStagingSnapshots` | 跨 program / STAGING 的 link 被拒（`ImmutablePayloadConflictException` 分流） |
| `revisionPinRejectsStagingAndForeignKeyProgramButAcceptsSameProgramLive` | 题面 pin 拒 STAGING/跨 program，接受同 program LIVE |
| `evictionDeletesOnlyUnpinnedStagingIntermediates` | 只删未 pin 的 STAGING 中间态；pinned/被引用行保留 |
| `commitTransactionExecutesDemoteAndEvict` | demote/evict 在提交事务内真的执行 |

- 本批**不新增触发器**（§5.4），这 4 条仍是 DAO 层唯一执行面；不得因为“计划写了触发器”而把这 4 条降级为辅助。
- 若 v71 迁移改了唯一索引，本套件的 link/pin/CAS 用例必须仍绿；红 → 先 D（旧包）再 C（迁移/DAO）。

### 5.2 提交 / 仓库两套（本批必须一起跑）

- `TutorVisualCommitInstrumentedTest` **10** `@Test`（批次 31 设备跑 9；新增
  `snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`，`:124`）。
- `TutorVisualCommitRepositoryInstrumentedTest` **5** `@Test`（批次 31 设备跑 4；新增
  `snapshotOverSourceBudgetIsRejectedBeforeAnyWrite`，`:229`）。
- 关键新增必须绿：`snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`（digest==sha256(json)
  的负向）、`snapshotOverSourceBudgetIsRejectedBeforeAnyWrite`（源码超 64 KiB 写库前拒绝）、
  `commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand`（plan→command 端到端）、
  `stageThenCommitStillDemotesThePreviousLatestCheckpoint`。
- 这 4 条**从未上过设备**（批次 31 只跑 9/4）；本批是首次执行，红例按 §8 归因，不得删用例。

### 5.3 预算 / evict / pin 三处证据（计划验收）

| 面 | 现有证据 | 缺口（如实登记） |
|---|---|---|
| 预算 | `snapshotOverSourceBudgetIsRejectedBeforeAnyWrite`（单源码 64 KiB 上限）+ E-08 的 per-program/global 陈旧读用例 | **设备轨后记（2026-10-29 11:19–11:35）**：本行原写"预算门移入事务**未落**"是 09:58 快照，现树已落码——`RoomStudyDatabase.enforceVisualCheckpointBudget` 在 `commitTutorTurnWithVisual` 的 demote/evict 之后重读 `programCheckpointStoredBytes`/`totalCheckpointStoredBytes`（现树 `:2162-2184`）；`stageTutorVisualSnapshot` 同样重判，复核修复轮又把 plan 的淘汰集带进 stage 事务（此前只写 `incomingKind`）。E-08 实跑 `OK (11 tests)`/`OK (8 tests)` |
| evict | `commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand`（端到端）+ 结构门 4 条 | **设备轨后记**：STAGING 租约条件**已加**——`evictUnpinnedCheckpoints` 带 `owner_request_id IS NULL OR = 本次 owner OR created_at <= 租约截止`（现树 `TutorVisualDao.kt:208-235`），E-08 的 `freshForeignStagingLeaseIsNotEvictedByAnotherRequest` 绿；E-07 该例的红因是测试自身读已删行（E-13），复核修复轮已改读未入候选的行，绿轮待重跑 |
| pin | 结构门 `evictionDeletesOnlyUnpinnedStagingIntermediates` + JVM `VisualCheckpointRetentionTest` | 设备面已有；红因是测试自身矛盾（E-13），非 pin 谓词失效；JVM 例本批未执行 |

### 5.4 计划与现树的两处偏差（必须由主会话裁定，不得静默接受）

1. **触发器 backstop 未落**：计划任务 4 写“触发器 backstop”；现树迁移文件明确“本批不加”，
   理由是会改既有异常契约、会删一条有分辨力用例、fresh install 不会自动建。→ 设备判据**不得**
   声称 DB 级触发器生效；要么主会话正式修改计划，要么另批补。
2. ~~**预算门未入事务 / evict 租约未加**：计划任务 7/9 的目标在现树未见落码。~~ **本项已被
   设备轨后的现树取代（09:58 快照写“未见落码”不成立）**：预算重判在 `RoomStudyDatabase`
   事务内（`:2162-2184`），evict SQL 带租约谓词（`TutorVisualDao.kt:208-235`），E-08 两套件
   `OK (11 tests)`/`OK (8 tests)`。仍属偏差的只有第 1 项（触发器 backstop）；本批不得声称
   DB 级触发器生效。

---

## 6. schema 71 三段验收

> 顺序硬约束（设计 §3.1）：**代码落齐 → 生成 71.json + Impl → 提取并交叉校验 hash → 回填常量与测试字面量
> → 跑门**。违反顺序会产生“71 常量 + 无 71.json”的中间态；当前树正处于该中间态（§1.1），
> **Stage 1 通过前不得构建/安装**。

### 6.1 Stage 1：生成物存在且 identityHash 与回填常量一致

**生成命令（由 schema 单写者 T3 执行，不是设备轨）**：

```bash
./gradlew :core:database:kspDebugKotlin
# 若增量/缓存可疑：
# ./gradlew :core:database:kspDebugKotlin --rerun-tasks
```

判定标准是**生成物存在**，不是任务 exit=0（`copyRoomSchemas` 可能 SKIPPED）。

**A. 71.json（首选，人可读）**

```bash
DB=core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase
test -f "$DB/71.json" || { echo "71.json missing"; exit 1; }

HASH=$(python - "$DB/71.json" <<'PY'
import json,sys
d=json.load(open(sys.argv[1],encoding="utf-8"))["database"]
assert d["version"]==71, d["version"]
h=d["identityHash"]
assert len(h)==32 and all(c in "0123456789abcdef" for c in h), h
assert h!="00000000000000000000000000000000", "placeholder hash"
assert any(f"'{h}'" in q for q in d["setupQueries"]), "setupQueries hash mismatch"
print(h)
PY
)
echo "identityHash=$HASH"
python - "$DB/71.json" <<'PY'
import json,sys
d=json.load(open(sys.argv[1],encoding="utf-8"))["database"]
print("entities",len(d["entities"]),"views",len(d["views"]))
PY
# 期望：90 entities / 2 views（v71 只改索引，不加表/视图；设计 C10）
```

**B. 生成的 Impl（与 A 同源，必须逐字相等）**

```bash
IMPL=core/database/build/generated/ksp/debug/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase_Impl.kt
grep -q "RoomOpenDelegate(71, \"$HASH\"" "$IMPL" || { echo "Impl hash != 71.json hash"; exit 1; }
```

**C. 回填常量（只改这些；结构数字不变）**

```bash
grep -n "SCHEMA_VERSION: Int = 71" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt
grep -n "IDENTITY_HASH: String = \"$HASH\"" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt
grep -n "CLOSURE_VERSION: Int = 3" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt
grep -n "allTables.size == 90\|views.size == 2\|trimOrder.size == 82\|expectedClosure.size == 5\|softReferences.size == 21" \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt
```

- `SCHEMA_VERSION=71`、`IDENTITY_HASH=$HASH`；`CLOSURE_VERSION=3`、90/2/82/5/21 **不变**。
- 测试字面量：`KernelWave0SchemaContractTest` 的 `71`（现树已改，`:53` 附近）、
  `SmbkSelectionPlanTest` 的 `SCHEMA_70_PATH` → 71 路径（T4 已改为 `SCHEMA_71_PATH`）、
  `TutorVisualSchemaContractTest` 保留 v70 冻结断言并另加 v71 断言。
- **不得**从 70.json 反推 hash（设计 §1.3：192 组组合全部算不出）；**不得**用 `legacyIdentityHash` 冒充；
  **不得**手写占位。

**D. 跟踪与注册**

```bash
git ls-files --error-unmatch "$DB/71.json"                          # 必须已跟踪
grep -n "VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71" \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt   # 必须注册
```

**Stage 1 通过条件**：A/B/C/D 全过。任一不一致 → 停项，按设计 §3.6 回滚或重生成；**不得**改 JSON 去迁就常量。

### 6.2 Stage 2：迁移矩阵 1→71

同 §3.1：`OK (15 tests)`、V=71、assets 71 个 schema、`71.json` 已跟踪、循环 1..71。
矩阵同时证明 **71.json 的 setupQueries 哈希与生成代码同源**（打开即校验）。

### 6.3 Stage 3：备份清单逐名

**脚本（S=71；在仓库根执行）**：

```bash
S=71 python - <<'PY'
import json, os, re, sys
v = os.environ.get("S", "71")
plan_path = "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt"
schema_path = f"core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/{v}.json"
if not os.path.exists(schema_path):
    print(f"SCHEMA={v} MISSING {schema_path}"); sys.exit(2)
src = open(plan_path, encoding="utf-8").read()
def block(a, b): return src[src.index(a):src.index(b)]
views = re.findall(r'"([a-z0-9_]+)"', block("val views", "val categoryTables"))
tables = []
for a, b in [("val categoryTables", "val alwaysTables"), ("val alwaysTables", "val derivedTables"),
             ("val derivedTables", "val splitTables"), ("val splitTables", "val rebuildTables")]:
    tables += re.findall(r'"([a-z0-9_]+)"', block(a, b))
db = json.load(open(schema_path, encoding="utf-8"))["database"]
json_tables = [e["tableName"] for e in db["entities"]]
json_views = [x["viewName"] for x in db["views"]]
plan_ver = int(re.search(r'const val SCHEMA_VERSION: Int = (\d+)', src).group(1))
plan_hash = re.search(r'const val IDENTITY_HASH: String = "([0-9a-f]+)"', src).group(1)
only_plan = sorted(set(tables) - set(json_tables)); only_json = sorted(set(json_tables) - set(tables))
dups = sorted({t for t in tables if tables.count(t) > 1})
print(f"SCHEMA={v} PLAN_TABLES={len(tables)} UNIQUE={len(set(tables))} JSON_TABLES={len(json_tables)}")
print(f"ONLY_PLAN={only_plan}"); print(f"ONLY_JSON={only_json}"); print(f"DUP={dups}")
print(f"VIEWS_MATCH={set(views) == set(json_views)}")
print(f"PLAN_VERSION={plan_ver} JSON_VERSION={db['version']}")
print(f"PLAN_HASH={plan_hash} JSON_HASH={db['identityHash']}")
ok = (len(tables) == len(set(tables)) == len(json_tables) and not only_plan and not only_json
      and set(views) == set(json_views) and plan_ver == db["version"] == int(v) and plan_hash == db["identityHash"])
print("RESULT=" + ("PASS" if ok else "FAIL")); sys.exit(0 if ok else 1)
PY
echo "EXIT=$?"
```

**通过**：`RESULT=PASS`、`EXIT=0`；90 表 / 2 视图、双向差集空、无重复、version/hash 同源。
若 `EXIT=2`（71.json 缺失）：**不得**拿 S=70 的结果关闭 71。

**设备侧同一条判据**：备份 34/34 内的归档/恢复用例 + §4.5 的 `rowCounts` 90 键逐名对拍。

---

## 7. 设备轨串行命令与 dex 符号门

> 严格串行，一条完成再下一条。`export MSYS_NO_PATHCONV=1` 全程生效。Gradle 只允许 `assemble*`；
> **禁 `connectedAndroidTest`**。设备执行归设备轨；本节只给命令与判据。

### 7.0 步 0：设备再登记

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A devices -l
# 选定 $S=emulator-5562（不使用 -5566/-5584）
$A -s "$S" shell getprop ro.build.version.sdk
$A -s "$S" shell getprop ro.product.cpu.abi
$A -s "$S" shell getconf PAGE_SIZE
$A -s "$S" shell getprop ro.build.fingerprint
```

**通过**：`device` 态、非 offline/unauthorized；serial/API/ABI/PAGE_SIZE/fingerprint 五值进证据头。
优先 API 34 / x86_64 / 4 KB；换设备则注明**不能跨设备比较像素**。批次 32 基线只读实测三台均 API 34 / x86_64 / 4096，
无 API 23（KD-77 仍 open）。

### 7.1 步 1：构建前一致性门（schema 71 中间态禁止构建）

```bash
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
P=$(sed -n 's/.*const val SCHEMA_VERSION: Int = \([0-9][0-9]*\).*/\1/p' \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt | head -1)
echo "V=$V P=$P"                                   # 必须相等（71/71）
test -f core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json \
  || { echo "STOP: 71.json missing"; exit 1; }
```

任一不等或 71.json 缺失 → **停**，等 T3 完成 Stage 1（§6.1）；不得用中间态构建。

### 7.2 步 2：构建（EXIT=0）

```bash
./gradlew --console=plain \
  :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest \
  :core:database:assembleDebugAndroidTest
```

构建后立即记录四包字节/sha256：

```bash
sha256sum app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
sha256sum app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
sha256sum core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
sha256sum core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
```

**构建早于源改动的陷阱**：构建前后对 `app/src`、`core/data/src`、`core/database/src` 做 sha256 快照并 `diff`；
不一致 = **D**，重新构建。T3/T4 在设备轨执行期间继续写文件时，必须等稳定后重建。

### 7.3 步 3：安装（`adb install -r -t`）

包名（`output-metadata.json` 实测）：

| APK | 包名 |
|---|---|
| app | `com.tingyun.smartmistakebook.offline` |
| app test | `com.tingyun.smartmistakebook.offline.test` |
| core:data test | `com.tingyun.smartmistakebook.core.data.test` |
| core:database test | `com.tingyun.smartmistakebook.core.database.test` |

```bash
$A -s "$S" install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s "$S" install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s "$S" install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s "$S" install -r -t core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
```

**通过**：四条均 `Success`；设备侧 `pm path` + `sha256sum` 与宿主逐字节一致：

```bash
for p in com.tingyun.smartmistakebook.offline \
         com.tingyun.smartmistakebook.offline.test \
         com.tingyun.smartmistakebook.core.data.test \
         com.tingyun.smartmistakebook.core.database.test; do
  path=$($A -s "$S" shell pm path "$p" | tr -d '\r' | sed 's/^package://')
  $A -s "$S" shell sha256sum "$path"
done
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` → 先 `adb uninstall <精确包名>` 再装，舞步记入证据。

### 7.4 步 4：dex 符号门（防旧包跑新门）

```bash
set -e
need() {
  apk="$1"; shift
  for s in "$@"; do
    unzip -p "$apk" 'classes*.dex' | grep -a -q "$s" \
      || { echo "MISSING [$s] in $apk"; return 1; }
  done
  echo "DEX OK: $apk"
}

# app 测试包：隔离 13 + flags 2
need app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk \
  VisualJsIsolationInstrumentedTest \
  VisualMeasureReplayPaintFlagsInstrumentedTest \
  theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags \
  aFingerprintThatDisagreesWithTheActualPaintFailsClosed

# core:data 测试包：备份 34 + painter 24 + PDF 16 + parity 16 + 仓库 5
need core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
  BackupRestoreInstrumentedTest \
  schema68ArchiveRestoresAndMigratesToCurrentVersion \
  VisualDrawOpsPainterInstrumentedTest \
  VisualDrawOpsPdfPainterInstrumentedTest \
  VisualDrawOpsParityInstrumentedTest \
  n09OnePixelTextShiftIsCaught \
  n10WholePageMisdrawIsCaught \
  n11ShapeWrongButPositionAndMassRightIsCaught \
  n12WholeBlockShiftBeyondToleranceIsCaught \
  n13CentroidBlindSpotIsCoveredByBboxOrShift \
  theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint \
  pdfWriteFailureMidStreamKeepsTheFirstFailureText \
  pdfFileWriteRoundTripsThroughReadback \
  pdfReadOnlyTargetFailsClosedBeforeAnyWrite \
  PdfWriteFailureTrackingStream PdfWriteTelemetry \
  TutorVisualCommitRepositoryInstrumentedTest \
  snapshotOverSourceBudgetIsRejectedBeforeAnyWrite \
  commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand \
  stageThenCommitStillDemotesThePreviousLatestCheckpoint

# core:database 测试包：69→70 4 + 迁移矩阵 15 + 结构门 4 + 提交 10 + 70→71（落码后补）
need core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  FullMigrationMatrixInstrumentedTest \
  everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback \
  TutorVisualSnapshotMigration_69_70InstrumentedTest \
  visualTablesAreCreatedEmptyWithoutTouchingV69RowsAndIdempotent \
  onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts \
  indexExpectationsStayInSyncWithTheMigrationDdlConstants \
  indexShapeAssertionsCanFailOnWrongColumnOrderAndNonUniqueIndex \
  TutorVisualStructureGatesInstrumentedTest \
  messageLinkRejectsForeignKeyProgramAndStagingSnapshots \
  revisionPinRejectsStagingAndForeignKeyProgramButAcceptsSameProgramLive \
  evictionDeletesOnlyUnpinnedStagingIntermediates \
  commitTransactionExecutesDemoteAndEvict \
  TutorVisualCommitInstrumentedTest \
  snapshotIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite \
  snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite
```

- **70→71 套件**：T3 落码后，用 `grep -n "class .*70_71.*InstrumentedTest\|fun .*" <新文件>` 取真实类名/方法名，
  追加到 database dex 门。**不得**把本文件预写的占位名塞进门里。
- 通过：三个 `DEX OK`；任一 `MISSING` = 旧包/漏装，**停**，重建重装，不得继续跑。

**schema 资产门**（同 §3.1/§3.4）：

```bash
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
echo "V=$V"
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -E 'StudyDatabase/[0-9]+\.json$' | wc -l   # == V
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -q "StudyDatabase/$V.json" || { echo "MISSING $V.json"; exit 1; }
```

### 7.5 步 5–N：套件串行（每套件前清 logcat、后落 raw log）

```bash
mkdir -p build/device-track-batch32

# 1) 备份 34
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step01-backup.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch32/step01-backup-logcat.txt

# 2) 69→70（v69 → 71）
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotMigration_69_70InstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step02-migration-69-70.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch32/step02-migration-69-70-logcat.txt

# 3) 迁移矩阵 1→71
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step03-migration-matrix.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch32/step03-migration-matrix-logcat.txt

# 4) 70→71 专用（类名以落码复读为准；先导出变量）
# MIG71=com.tingyun.smartmistakebook.core.database.<实际类名>
# $A -s "$S" logcat -c
# $A -s "$S" shell am instrument -w -r -e class "$MIG71" \
#   com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
#   | tee build/device-track-batch32/step04-migration-70-71.log

# 5) 结构门 4
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step05-structure-gates.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch32/step05-structure-gates-logcat.txt

# 6) 提交 10
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualCommitInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step06-visual-commit.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch32/step06-visual-commit-logcat.txt

# 7) 仓库 5
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepositoryInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step07-visual-repo.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch32/step07-visual-repo-logcat.txt

# 8) 一致门 16（原型门）
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step08-parity.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch32/step08-parity-logcat.txt

# 9) 裁剪器 9（KD-80 80-C3 设备半边）
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupSnapshotTrimmerInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step09-trimmer.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch32/step09-trimmer-logcat.txt
```

批次 31 的 painter 24 / 隔离 13 / flags 2 / PDF 16 仍建议同轮回归（防 schema 71 或依赖变化引入回退），
命令同批次 31 判据 §4.4，仅把输出目录改为 `build/device-track-batch32/`。

逐步汇总：

```bash
for f in step01-backup step02-migration-69-70 step03-migration-matrix step04-migration-70-71 \
         step05-structure-gates step06-visual-commit step07-visual-repo step08-parity step09-trimmer; do
  echo "== $f"; grep -E "OK \(|Tests run:|Failures:|Error|Skipped|Time:" \
    "build/device-track-batch32/$f.log" | tail -4
done
```

任一 `Failures: [1-9]`、`Error`、`Skipped`、或 summary 非目标字符串 → **停在该步**，按 §8 归因，
不得“先跑完再说”。

### 7.6 签名扫描（全部 logcat）

```bash
for f in build/device-track-batch32/*-logcat.txt; do
  printf "%s: " "$(basename "$f")"
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f"
done   # 期望全 0
```

---

## 8. 红例归因规则（先测试层，后产品层）

### 8.1 绝对禁止

- 删除/弱化断言、放大 delta/容差、缩小扫描范围、`@Ignore`/`Assume` 跳过、用 `-e class` 过滤红例、
  把 `OK (N tests)` 的 N 改小、把失败改写成“已知问题”后仍声称通过。
- **先改测试让它变绿、再回头找原因**。
- 以“批次 31 曾 34/34、24/24、13/13 绿”或“另一台设备绿”替代当前树/当前 APK 的证据。
- 把一致门（prototype）的红/绿写成产品门或发版结论。
- 为 N09/N10/N11/N12/N13 变绿直接调阈值，而不引用设备读数与归因证据。
- 用“迁移矩阵 15 例绿”代替 70→71 专用套件；用“表在 90 张清单里”代替“引用关系保持”。

### 8.2 强制顺序

1. **抓原样**：完整失败行、断言消息、stack（项目帧 + 框架帧首行）、`Tests run:` 行；归因前不删 log。
2. **定位第一项目帧**：哪个文件哪一行。
3. **先测试层**：
   - **A（夹具/门逻辑缺陷）**：失败在测试辅助代码、夹具、或原型门自身判定逻辑；产品路径未被
     执行到，或门的分辨力断言写错。处置 = 改夹具/门逻辑，产品源零改动；**不得借机放宽阈值**。
   - **B（测试期望漂移）**：实际值与权威源（常量/契约/编译器/探针）一致，测试期望写错。
     处置 = **改成权威值**（不是改成实测值），保留 delta 0；补权威源 `path:line`。
4. **再环境层 D（设备/旧包）**：设备 offline、装错旧包、API/ABI/PAGE_SIZE 不符、字体差异导致
   跨设备像素比较、构建早于源改动。处置 = 重登记/重建重装/核 sha256 与 dex 门；
   **跨设备像素差异不作产品缺陷**，只在单设备内比较像素。
5. **最后产品层 C**：测试期望符合权威源，产品输出/行为错。处置 = 修产品；保留/加强测试；
   若为已知缺口登记 KD，**不得删测试或放宽**。
6. **先红后绿闭环**：红 log → 归因证据 → 改动清单（精确到文件/行/牵动面）→ 重建重装（新 sha256）
   → 同命令复跑绿 log。未复跑 = UNVERIFIED。

> 口诀：**A/B 先行，D 排除，最后才 C**。

### 8.3 批次 32 专用陷阱

| 现象 | 先判 | 处置 |
|---|---|---|
| 构建前 `V=71` 但 `71.json` 缺失 / `SCHEMA_VERSION=70` | **停**（中间态） | 等 T3 完成 Stage 1；不得构建 |
| 迁移矩阵在 v71 红、`context.assets.open` 抛 FileNotFound | **D**（assets 缺 71）→ C | 查 §3.4 资产门；重建 |
| 迁移矩阵红 `Migration didn't properly handle` | **C**（迁移 DDL 与实体索引不一致） | 对照 71.json 的 `createSql` 修迁移；先排除 D |
| 打开库报 `Room cannot verify the data integrity` | **C**（71.json 哈希 ≠ Impl 哈希）或 D（旧 asset） | 按 §6.1 A/B 重取；**不得改 JSON** |
| 备份红 `快照 schema=71，裁剪器只支持 schema=70` | 先 D → 再 C | 新包仍 70 = 回填未完成；查 `SmbkSelectionPlan:40` |
| 备份红 `expected:<70> but was:<71>` | **D**（旧测试包） | dex 门查 `schema68ArchiveRestoresAndMigratesToCurrentVersion`；重装 |
| 备份 34 中 `schema68ArchiveRestoresAndMigratesToCurrentVersion` 红 | **C**（迁移未注册/恢复未落到实际库） | 查 `StudyDatabase.kt:411` 与恢复路径 |
| 69→70 只跑 2 例 | **D** | 旧包；现树 4 例，重建 |
| 69→70 红 `Migration didn't properly handle` / 索引集合多一个 | **C**（未 DROP 旧索引） | 查 `VisualSnapshotIdentityMigration_70_71.kt` 的 DROP；不得只 CREATE |
| parity 15 绿但现树 16 | **D** | 旧包；N13 未跑不能作为 `calibrated-r2` 证据 |
| parity 出现 `thresholds=calibrated-r1` 或 `blindWindow=text-centroid(0,0.76)px` | **D** | 旧包；重建 |
| N10/N11/N12/N13 任一绿 | **A**（门失去分辨力） | 不得调阈值；`PARITY_GATE_DESENSITIZED` 即证据；标定作废 |
| N09-P07 质心等式红但判决红 | B/A/D（容差/夹具/环境） | 不得删 P07 或放宽 0.75；按 §2.4 记录 |
| 结构门 4 例红 | 先 D → C | 查 v71 索引/DAO EXISTS/CAS 是否被迁移牵动 |
| 提交 10 / 仓库 5 中新增用例缺失 | **D** | dex 门查 `snapshotDigest…` / `snapshotOverSourceBudget…`；重建 |
| 预算/evict 新用例红 | 先 A（夹具）→ C（预算门/租约的**接线**） | **设备轨后记**：任务 7/9 已落码（事务内重判 `RoomStudyDatabase:2162-2184`、租约 `TutorVisualDao.kt:208-235`，§5.3/§5.4-2），E-08 两套件绿；红例按 A/C 的实现接线归因，不得再按"未落"登记，也不得删用例 |
| 跨设备像素差 | **D** | 只在同设备内比较；不得判产品缺陷 |

### 8.4 归因记录最低字段

红例名 + 行号 + 失败消息 + 第一项目帧 + 四源核对结果（逐源 `path:line`）+ 类别（A/B/C/D）+
处置（改哪个文件哪几行/或不改的理由）+ 重建 sha256 + 绿轮 summary。缺任一字段 = 归因不成立。

---

## 9. 本文件边界与未验证项

- **已执行（设备轨 2026-10-29 11:19–11:35，不是本文件轮次）**：E-02 一致门 `OK (16 tests)`
  （15 负控红，N09-P07 实测 `0.608282132824767`）；E-03 69→70 `OK (4 tests)`；E-04 70→71
  `OK (3 tests)`；E-05 1→71 `OK (15 tests)`；E-06 备份 `OK (34 tests)`；E-08 提交/仓库
  `OK (11 tests)`/`OK (8 tests)`；E-09 裁剪器 `OK (9 tests)`；E-10 painter/PDF `OK (24)`/`OK (16)`；
  E-11 隔离/flags `OK (13)`/`OK (2)`。逐项回链 `docs/research/evidence/2026-10-29-batch-32/00-index.md` §3。
- **UNVERIFIED（仍未执行）**：JVM 单测（`SmbkSelectionPlanTest` 12、`VisualCheckpointRetentionTest` 17、
  `TutorVisualIdentityTest` 6、schema contract 例）——本批无任何 `testDebugUnitTest` 记录；
  结构门 E-07 仍 4/5（红例已修、绿轮未重跑）；触发器 backstop 未落（§5.4 第 1 项）。
- **已核（只读）**：§1.1/§1.2 的常量与计数、§1.4 的 APK/源 mtime、§4.2 的 8 条用例名、§5.1/§5.2 的方法名、
  `70.json` 的 70/`11a8a701…`/90/2、迁移注册最高 70→71。
- **在飞状态更新**：T3（schema 71）与 T4（parity r2/N13）已交付；本文件计数仍是 09:58 快照，
  设备轨构建前已按 §1.3 复读执行（计数漂移见 `00-index.md` §5-6）。
- **不在本判据面**：编译器/引擎/Node 台、R8 往返、arm64/真机/16 KB 页、生产签名、
  真实 Provider（无凭据 → 模型质量 UNVERIFIED）；一致门仍是原型门。
- **设备依赖**：批次 32 基线只读实测 `emulator-5562/5566/5584` 在线（均 API 34 / x86_64 / 4096）；
  若掉线，先按 §7.0 重新登记。`-5566/-5584` 属他线，禁碰。
- **本文件自身是新增 `??` 产物**；未做 git 写操作，未改任何热区文件。

---

## 10. 来源

**仓内（本会话实读/实跑）**

- 计划：`.zcode/plans/plan-2026-10-29-batch-32-parity-schema71.md:5-9,14-25,33-40`
- 基线：`docs/research/2026-10-29-batch-32-baseline.md`（全文）
- 纠正：`docs/research/2026-10-29-parity-metric-correction.md:17-50,131-150,232-256,260-363,398-415`
- schema 生成：`docs/research/2026-10-29-schema-generation-flow.md:15-29,32-64,89-153,186-230,396-558`
- 批次 31 判据：`docs/research/2026-10-29-batch-31-device-criteria.md:104-343,346-466,744-867`
- 批次 31 证据：`docs/research/evidence/2026-10-29-batch-31/step01-backup.md:1-70`、
  `step03-migration-matrix.md:1-50`、`step09-persistence-structure.md:1-120`、`00-index.md:19,64,73,134-136`
- 源码/测试（本会话实读）：
  - `core/database/src/main/kotlin/.../StudyDatabase.kt:139,340-411`
  - `core/database/src/main/kotlin/.../VisualSnapshotIdentityMigration_70_71.kt`（全文）
  - `core/database/src/main/kotlin/.../entity/TutorVisualEntities.kt`（在飞）
  - `core/data/src/main/kotlin/.../backup/SmbkSelectionPlan.kt:40,43,46,64,187-191,537-554,637-639`
  - `core/data/src/main/kotlin/.../backup/BackupSnapshotTrimmer.kt:267-284`
  - `core/data/src/main/kotlin/.../backup/SmbkArchiveCodec.kt:39-115,464-468`
  - `core/data/src/main/kotlin/.../backup/AndroidBackupRepository.kt:1215-1245`
  - `core/data/src/main/kotlin/.../visual/TutorVisualCommitRepository.kt:180-193,401-404,438-445`
  - `core/data/src/androidTest/.../visual/VisualDrawOpsParityInstrumentedTest.kt:92-108,307-447,450-520,874-903`
  - `core/data/src/androidTest/.../visual/VisualDrawOpsParityMetrics.kt:262,270-274`
  - `core/data/src/androidTest/.../backup/BackupRestoreInstrumentedTest.kt:488,577-590`
  - `core/database/src/androidTest/.../FullMigrationMatrixInstrumentedTest.kt:38`
  - `core/database/src/androidTest/.../TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:58,237-238,966-970`
  - `core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt:41,156-212`
  - `core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt:35,124`
  - `core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt:34,180,229`
  - `core/database/src/test/.../KernelWave0SchemaContractTest.kt:33-56`
  - `core/data/src/test/.../backup/SmbkSelectionPlanTest.kt:45-47,565,610-611`
- 构建配置：`core/data/build.gradle.kts:34-38`（androidTest assets）；`app/build.gradle.kts:20,47`；
  `core/data/build/outputs/apk/androidTest/debug/output-metadata.json`；
  `core/database/build/outputs/apk/androidTest/debug/output-metadata.json`；
  `app/build/outputs/apk/androidTest/strictOffline/debug/output-metadata.json`
- 实跑命令（可重跑）：
  - `git rev-parse --short HEAD` → `0b4bdbbe`
  - `grep -c '@Test' <12 个文件>` → 34/4/15/24/13/2/16/16/4/10/5/9
  - `python` 读 `70.json` → `version 70 / 11a8a701… / 90 / 2`
  - `ls ... | grep -cE '^[0-9]+\.json$'` → 70；`find ... -name 71.json` → 空
  - `stat -c '%y'` 三 APK → 06:28:19–06:28:20；parity 源 08:01:48；结构门源 07:59:39
  - `git log -1 --format='%ci' f7c5d4b7` → 08:34:32；`5fee43c5` → 08:33:55
  - `unzip -l database-debug-androidTest.apk` → 70 个 `StudyDatabase/[0-9]+.json`；含陈旧 `70.json.NOT_EXPORTED.md`
  - `git status --porcelain -- core/database core/data` → 在飞清单（§1.1）
