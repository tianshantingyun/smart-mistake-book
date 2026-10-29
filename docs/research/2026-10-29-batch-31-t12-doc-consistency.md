# 批次 31 文档回改与 KD 状态核对（2026-10-29，台账维护轨 T5）

> **口径**：本轨只改 `docs/`，未改任何 Kotlin/JS/Gradle/schema 文件，未运行 Gradle，未执行任何 JVM/仪器化/设备用例，未做 git 写操作。所有"现树"数字都是本轮只读 `grep -c` / `wc -l` / `git status` / python 解析实测；**没有一条构建或设备结论**。
>
> **快照时点**：2026-10-29 **05:39 +0800**（`date` 实跑）。此时他线仍在写码：`TutorVisualSnapshotMigration_69_70InstrumentedTest.kt` mtime 05:32、`VisualDrawOpsParityMetrics.kt` / `VisualDrawOpsParityInstrumentedTest.kt` mtime 05:28。本文记录的是**该快照**；后续设备轨/代码轨应另起 Progress 回填，不得把本文数字当终点。
>
> **设备证据**：`docs/research/evidence/2026-10-29-batch-31/` 在本文快照时点（05:39）**实测不存在**（`ls` = `No such file or directory`）。因此本批所有设备绿轮（备份 34、69→70、迁移 1→70、painter 24、隔离 13、flags 2、PDF 16、parity 15）在本文件内一律标 **未出数**。
>
> **后续回填（2026-10-29 复核修复轮，只读）**：设备轨 06:20–06:54 已落盘该目录 **15 份**证据，上述套件均已实跑：备份 `OK (34 tests)`（E-01）、69→70 `OK (4 tests)`（E-02）、迁移矩阵 `OK (15 tests)`（E-03）、painter 24/24（E-04）、隔离 13/13（E-05）、flags 2/2（E-06）、PDF 16/16（E-07）、parity **FAIL 14/15**（E-08/E-08b，N09-P06 旧等式 A 类）。本句只取代"未出数/目录不存在"，不改变本文件作为 05:39 快照记录的性质。
>
> 计划对照：`.zcode/plans/plan-2026-10-29-batch-31-persistence-hardening.md:27-29`（任务 8/9）。
> 基线对照：`docs/research/2026-10-29-batch-31-baseline.md`（下称"基线"）。
> 前一批同类记录：`docs/research/2026-10-29-batch-30-t12-doc-consistency.md`。

---

## 1. 改动清单（逐文件 + 行号）

| 文件 | 行（改后） | 改动 | 依据 |
|---|---|---|---|
| `docs/known-defects.md` | `:2750` | KD-71 新增批次 31 Progress：本批无设备证据；本机 JUnit XML（`tests="3" failures="0"`，mtime 04:06:29）含 T7 新用例，"新用例未执行"的旧表述被否证；JVM 执行绿但命令/门禁归属 UNVERIFIED；维持 open | 附录 A.4 |
| `docs/known-defects.md` | `:2778` | KD-72 新增批次 31 Progress：窗口仍不满足（696 行 / staged 0 / 跟踪锁 0 / 盘上 14 / W1 6/6 脏 / W6 W7 未做）→ 零动作、维持 open | 附录 B.1 |
| `docs/known-defects.md` | `:2796` | KD-73 新增批次 31 Progress：全仓无扫描脚本/结果，批次 30 T7 自述仍 UNVERIFIED；无设备/R8 冒烟；维持 open | 附录 B.2 |
| `docs/known-defects.md` | `:2895` | KD-77 新增批次 31 Progress：本批未起 API 23 AVD、无证据目录；在线模拟器均 API 34；API 23 不得标绿；维持 open | 附录 C |
| `docs/known-defects.md` | `:2955` | KD-79 的 Reopen/关闭条件里"PDF 13 例"就地更正为**现树 16 例**（13 为批次 29 登记时点；批次 30 设备轨已按 16 例执行 `OK (16 tests)`） | 附录 A.1 |
| `docs/known-defects.md` | `:2973` | KD-80 新增批次 31 Progress：修复仍在树（70/90/21/CLOSURE 3、trimmer 跟随常量、测试改名+常量期望）；表清单与 `70.json` 90 实体双向差集为空；34/34 设备绿本批未跑；维持 open | 附录 B.3 |
| `docs/current/visual-generation-contract.md` | `:118` | 表头时点加"批次 31 T5 回改现树行数与 parity 例数" | 附录 A.1 |
| `docs/current/visual-generation-contract.md` | `:123` | `VisualDrawOpsPdfPainter.kt` **154 → 258 行**；`VisualDrawOpsPainter.kt` 现树 **334 行**、`VisualDrawOpsPainterPure.kt` 现树 **228 行**；parity **11 → 15 例**（+N08 与 `calibrated-r1` 的 N09–N12，只在树）；补 `calibrated-r1` 阈值（质心 0.75/墨量 0.93–1.07/TEXT F5 归零）与"设备未跑" | 附录 A.1/A.5 |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | `:609` | 附录 G 计数回改追加批次 31 段：批次 30 设备证据目录已存在；现树 24/15/13/16；批次 31 设备绿轮未出数 | 附录 A.1/C |
| `docs/research/2026-10-29-batch-30-t12-doc-consistency.md` | `:9` | 顶部加"批次 31 后续"指针：本文 parity 11 是批次 30 时点，批次 31 现树为 15（其余计数不变） | 附录 A.1 |
| `docs/research/2026-10-29-batch-31-t12-doc-consistency.md` | 本文件 | 新增本批文档回改记录与附录（命令/输出） | 任务 8/9 第三项 |

> **整文件纳入**：`docs/current/visual-generation-contract.md` 与 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` 在本轨开工时已是 ` M`（批次 30 T12 未提交改动），按纪律**整文件纳入**，不得只挑本轨片段提交。`docs/known-defects.md` 开工时干净（`git status --porcelain` 无输出），本轨改动后变 ` M`。本文件为 `??` 新文件。

---

## 2. KD 状态裁决（按本批证据，不虚报）

| KD | 本批证据（截至 05:39） | 裁决 |
|---|---|---|
| **KD-71**（close/execute 竞态） | 无 `docs/research/evidence/2026-10-29-batch-31/`；无设备复跑。**新**：本机 JUnit XML 显示 `QuickJsVisualExecutionEngineJvmTest` 3 例 0 失败（含 T7 新用例），mtime 04:06:29；XML 在 `build/`（gitignored），无命令行/门禁日志 | **维持 open**（设备侧两条并发用例本批未重跑；JVM 执行绿不等于 Reopen condition 满足） |
| **KD-72**（dependency locking） | 696 行（221 M + 475 ??）、staged 0、跟踪锁 0、盘上 14、`:benchmark` 无锁、W1 6/6 脏、W6/W7 未做 | **维持 open**（干净树窗口仍不满足，零动作） |
| **KD-73**（stdlib 2.3.21 全图） | 全仓无扫描脚本/结果；无设备/R8 冒烟 | **维持 open**（批次 30 T7 自述仍 UNVERIFIED） |
| **KD-77**（API 23） | 未起 API 23 AVD；在线三台均 API 34/x86_64/4096；旧冒烟 logcat 未落仓 | **维持 open**（API 23 项不得标绿） |
| **KD-80**（备份清单 70） | 修复在树（70/90/21/CLOSURE 3 + trimmer 跟随 + 测试改名/常量期望）；`BackupRestoreInstrumentedTest` 34 例、` M`；表名与 70.json 90 实体双向差集空；**34/34 设备绿未跑**；34 例对"视觉四表引用保持"零覆盖（`grep tutor_visual` = 0） | **维持 open**（关闭条件未满足） |

**未出数一律标未出数**：本批没有任何一条 KD 被写成"已关闭"。

---

## 3. 过期句处置（基线点名 + 本轮扫描）

| 过期句 | 位置 | 处置 |
|---|---|---|
| "PDF 13 例" | `known-defects.md` KD-79 Reopen/关闭条件（`:2955`） | **就地更正**为现树 16 例（保留 13 的历史含义） |
| "两文件无本批改动 / 本批没有证据目录" | `known-defects.md` KD-71 批次 30 T12 块 | 该块已有"复核修复轮更正"；本轮新增批次 31 块再次标明它是**批次 30 T12 时点**表述，并按补记惯例保留原文 |
| "证据目录不存在" | KD-76（`:2869` 附近）、KD-78（`:2916` 附近）的批次 29 T6 块 | 均被后续设备轨块显式"取代"；按补记惯例不回改。本轮不在这些历史块上重复注记 |
| "批次 30 修复后设备复跑未出数（目录不存在）" | `2026-10-26-batch-24-4e2-admission-checklist.md:609` | **追加批次 31 回改段**：批次 30 设备证据已落盘；现树计数刷新 |
| "仪器化 13 例 / PDF 104 行" | 契约 §5.1 | 已在批次 30 T12 改到 154/16；本轮再改到 **258 行**并补 parity 15 与 `calibrated-r1` |

---

## 4. 未验证项（如实）

- **设备面全部未出数**：备份 34/34、69→70（现树 4 例，T3 在飞）、迁移 1→70、painter 24、隔离 13、flags 2、PDF 16、parity 15 的绿轮本批都**没有执行证据**；`grep -c "@Test"` 只是源计数。
- **KD-71 JVM XML**：是 `build/` 下的本地产物，不是仓内证据；无法从仓内确定它由哪条命令/门禁产生。
- **KD-73**：批次 30 T7 的 10.3 万 class 扫描仍无脚本/日志/结果落仓，不可独立复跑。
- **KD-77**：API 23 镜像在盘不等于运行时已验证；本批未起 AVD。
- **parity `calibrated-r1`**：代码与负控 N09–N12 在树（T4，05:28），设备绿轮未跑；本轨**不**把它写成"阈值已验证"。
- **T3 在飞**：69→70 套件现树 **4 例**（`grep -c "@Test"`），mtime 05:32；本轨只记录该计数，不评审其断言质量。

---

## 附录 A：计数与现树（命令 + 输出）

### A.1 `@Test` 计数与文件行数

命令：

```bash
cd "D:/smart mistake book"
for f in \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterInstrumentedTest.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterPureTest.kt \
  app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt \
  app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt \
  core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualSnapshotMigration_69_70InstrumentedTest.kt \
  core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/FullMigrationMatrixInstrumentedTest.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/PdfWriteIntegrityTest.kt; do
  echo "$(grep -c '@Test' "$f") $f"
done

wc -l \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterPure.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainter.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterPure.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityMetrics.kt
```

输出（2026-10-29 05:39 前后）：

```
24 VisualDrawOpsPainterInstrumentedTest.kt
15 VisualDrawOpsParityInstrumentedTest.kt
16 VisualDrawOpsPdfPainterInstrumentedTest.kt
13 VisualDrawOpsPainterPureTest.kt
13 VisualJsIsolationInstrumentedTest.kt
2  VisualMeasureReplayPaintFlagsInstrumentedTest.kt
34 BackupRestoreInstrumentedTest.kt
4  TutorVisualSnapshotMigration_69_70InstrumentedTest.kt
15 FullMigrationMatrixInstrumentedTest.kt
16 PdfWriteIntegrityTest.kt

258 VisualDrawOpsPdfPainter.kt
92  VisualDrawOpsPdfPainterPure.kt
334 VisualDrawOpsPainter.kt
228 VisualDrawOpsPainterPure.kt
967 VisualDrawOpsParityMetrics.kt
```

> 说明：69→70 套件在基线时是 2 例（基线 §3.4），05:32 被 T3 改为 4 例（新增 `indexExpectationsStayInSyncWithTheMigrationDdlConstants` 与负控 `indexShapeAssertionsCanFailOnWrongColumnOrderAndNonUniqueIndex`）。本轨按现树记 4。

### A.2 schema 与请求 schema

```bash
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
git ls-files core/database/schemas | wc -l
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase | wc -l
```

输出：

```
139:internal const val STUDY_DATABASE_VERSION = 70
70
70
```

（`71.json` 不存在：`ls …/71.json` = `No such file or directory`。任务 6 的 schema 71 在本快照尚未落树。）

### A.3 `70.json`

```bash
python - <<'PY'
import json
d=json.load(open('core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json',encoding='utf-8'))
print('version',d['database']['version'])
print('entities',len(d['database']['entities']))
print('identityHash',d['database']['identityHash'])
print('views',[v['viewName'] for v in d['database']['views']])
PY
```

输出：

```
version 70
entities 90
identityHash 11a8a701bae767a8a3c5bc810e4743a4
views ['library_catalog', 'knowledge_question_lattice']
```

### A.4 KD-71 本机 JUnit XML

```bash
ls -l --time-style=full-iso core/data/build/test-results/testDebugUnitTest/TEST-com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineJvmTest.xml
head -2 <同上文件>
grep -o 'testcase name="[^"]*"' <同上文件>
```

输出（摘）：

```
-rw-r--r-- … 981 2026-10-29 04:06:29.203607600 +0800 …QuickJsVisualExecutionEngineJvmTest.xml
<testsuite … tests="3" skipped="0" failures="0" errors="0" timestamp="2026-10-28T20:06:28.670Z" …>
testcase name="close landing between the closed precheck and registration still rejects the execution"
testcase name="oversized source and state are rejected before any engine allocation"
testcase name="execute after close is rejected without touching the engine library"
```

> 该 XML 在 `build/`（gitignored），只作"本机执行过、绿"的旁证，不升级为仓内证据。

### A.5 parity `calibrated-r1`（T4 在飞落码）

```bash
grep -n "THRESHOLD_VERSION = \|maxCentroidDeltaPx = \|minMassRatio = \|maxMassRatio = \|if (fixtureClass == ParityFixtureClass.TEXT) return false" \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityMetrics.kt
git diff --stat -- …/VisualDrawOpsParityMetrics.kt …/VisualDrawOpsParityInstrumentedTest.kt
```

输出：

```
234:    const val THRESHOLD_VERSION = "calibrated-r1"
243:        maxCentroidDeltaPx = 0.75,
244:        minMassRatio = 0.93,
245:        maxMassRatio = 1.07,
727:        if (fixtureClass == ParityFixtureClass.TEXT) return false

 …/VisualDrawOpsParityInstrumentedTest.kt | 149 ++++++++++++++++-
 …/VisualDrawOpsParityMetrics.kt          | 182 +++++++++++++++++----
 2 files changed, 289 insertions(+), 42 deletions(-)
```

> 标定依据见 `docs/research/2026-10-29-parity-calibration.md`；**本轨零设备执行**，不判定阈值已通过。

---

## 附录 B：KD-72/73/80 的命令与输出

### B.1 KD-72 锁窗口

```bash
git status --porcelain | wc -l
git status --porcelain | awk '$1=="M"{m++} $1=="??"{u++} END{print "M="m" ??="u}'
git diff --cached --name-only | wc -l
git ls-files '*lockfile' | wc -l
find . -maxdepth 3 -name "*.lockfile" -not -path "./.worktrees/*" | wc -l
ls benchmark/gradle.lockfile
git status --porcelain -- settings.gradle.kts core/ui/build.gradle.kts feature/tutor/build.gradle.kts core/export/build.gradle.kts .gitignore core/markdown/build.gradle.kts
```

输出（摘）：

```
696
M=221 ??=475
0
0
14
ls: cannot access 'benchmark/gradle.lockfile': No such file or directory
 M settings.gradle.kts
 M core/ui/build.gradle.kts
 M feature/tutor/build.gradle.kts
 M core/export/build.gradle.kts
 M .gitignore
?? core/markdown/build.gradle.kts
```

### B.2 KD-73 扫描产物

```bash
find . -maxdepth 3 \( -iname "*stdlib*scan*" -o -iname "*kd73*" -o -iname "*class-ref*" \) -not -path "./.git/*"
ls tmp/ | grep -i "kd73\|stdlib\|scan"
```

输出：**空**（无 KD-73 扫描脚本/结果）。

### B.3 KD-80 备份清单

```bash
grep -n "SCHEMA_VERSION\|IDENTITY_HASH\|allTables.size\|softReferences.size\|CLOSURE_VERSION" \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt
sed -n '265,285p' core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmer.kt
grep -n "schema68ArchiveRestoresAndMigratesToCurrentVersion\|SmbkSelectionPlan.SCHEMA_VERSION" \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt
git status --porcelain -- …/BackupRestoreInstrumentedTest.kt
python - <<'PY'
import json,re,io
src=io.open('core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt',encoding='utf-8').read()
names=set(re.findall(r'"([a-z][a-z0-9_]+)"',src))
d=json.load(open('core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json',encoding='utf-8'))
ents={e['tableName'] for e in d['database']['entities']}
inter=names & ents
print('src quoted identifiers that match entities:',len(inter))
print('entities:',len(ents))
print('entity tables absent from src quoted set:',sorted(ents-inter))
PY
```

输出（摘）：

```
40:    const val SCHEMA_VERSION: Int = 70
43:    const val IDENTITY_HASH: String = "11a8a701bae767a8a3c5bc810e4743a4"
46:    const val CLOSURE_VERSION: Int = 3
538:        require(allTables.size == 90) { "schema 70 应有 90 张表，实际 ${allTables.size}" }
554:        require(softReferences.size == 21) { "软引用应为 21 条" }

if (database.version != SmbkSelectionPlan.SCHEMA_VERSION) { … }
if (identityHash != SmbkSelectionPlan.IDENTITY_HASH) { … }

488:    fun schema68ArchiveRestoresAndMigratesToCurrentVersion() = runBlocking {
590:                    SmbkSelectionPlan.SCHEMA_VERSION,

 M core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt

src quoted identifiers that match entities: 90
entities: 90
entity tables absent from src quoted set: []
```

> 该比对只证明"源码里出现的表名集合与 70.json 的 90 个实体名双向差集为空"，不等于设备 34/34 绿。

**第四项覆盖缺口（本轨复跑）**：

```bash
grep -rln "tutor_visual" core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/ | wc -l
```

输出：

```
0
```

> 即 `BackupRestoreInstrumentedTest` 34 例不覆盖"备份/恢复后视觉四表引用关系保持"；KD-80 第四条关闭条件不能由 34/34 单独满足。与 `docs/research/2026-10-29-batch-31-device-criteria.md` §0-3/§3.4 一致。

---

## 附录 C：批次 31 证据/环境盘点

```bash
ls -d docs/research/evidence/2026-10-29-batch-31
ls docs/research/evidence/
find docs/research -maxdepth 2 -iname "*api23*" -o -iname "*api-23*"
```

输出：

```
ls: cannot access 'docs/research/evidence/2026-10-29-batch-31': No such file or directory
2026-10-26-batch-23 … 2026-10-29-batch-30   （10 个目录，无 batch-31）
docs/research/2026-10-25-16kb-arm64-api23-build.md   （仅旧研究，无 API 23 冒烟证据）
```

在线设备（基线 §7.2 只读查询，本轨未重跑 adb）：`emulator-5562 / 5566 / 5584`，均 **API 34 / x86_64 / PAGE_SIZE 4096**；无 API 23 设备。

---

## 附录 D：本轨改动后文件状态

```bash
git status --porcelain -- docs/known-defects.md docs/current/visual-generation-contract.md \
  docs/research/2026-10-26-batch-24-4e2-admission-checklist.md \
  docs/research/2026-10-29-batch-31-t12-doc-consistency.md
```

预期：`docs/current/visual-generation-contract.md` 与 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` 开工即 ` M`（整文件纳入）；`docs/known-defects.md` 与 `docs/research/2026-10-29-batch-30-t12-doc-consistency.md` 由干净变 ` M`；本文件 `??`。**本轨未执行 `git add`/`commit`/`push`。**

---

## 附录 E：与前一批记录的差异

- 批次 30 T12 记录把 parity 记为 **11 例**、PDF 记为 **16 例**、painter 记为 **24 例**；本批 parity 因 T4 的 `calibrated-r1` 新增 N09–N12 变为 **15 例**，其余不变。
- 批次 30 T12 记录写"批次 30 设备证据目录不存在"；该目录其后已落盘 11 份，本批按现树更正（见 4e2 附录 G 回改段）。
- 批次 30 T12 记录写 KD-71 新 JVM 用例"未执行"；本批发现 `build/` 下 04:06 的 JUnit XML（3/0），按本机旁证更正为"已执行、非仓内证据"。
