# 批次 50 基线核对（2026-11-08/09）：发版水平总攻——四门 + 产品面 + 发布

| 字段 | 值 |
|---|---|
| 角色 | 基线/协作核对员（只读；**未改任何源码/测试/台账/金标**，只新建本文件） |
| 计划 | `.zcode/plans/plan-2026-11-09-batch-50-release.md`（已完整读取，89 行；P0/R/G0–G4/产品面/发布/验收七段） |
| 核对时点 | 2026-11-08 13:19 +0800（本会话 `date` 实测；HEAD 提交时间 2026-11-08 07:43:59 +0800） |
| 上一批终裁 | `docs/research/2026-11-08-batch-49-record.md`（NOT ACCEPTED 维持；台账 900/22/278）——已读 |
| 本会话实跑范围 | 只读 `git`/`grep`/`sed`/`python -I`/`adb` 只读查询；**零 Gradle、零安装、零设备写入、零源码写入**（禁令照守） |
| verdict | **可开工，但 6 处必须先认账**：① 工作树 581 脏且**批次 49 的代码改动仍未提交**（至少 `ReviewPlannerV2` 权重 seam 与 `feature/review` 校准/冲突 UI；近 5 个提交全是 docs/evidence）；② `check_backup_plan_sync.py` **exit 2**，四方门脚本仍钉 73/92/closure4，其 pytest **6 failed**；③ AUD-08 v2 的 **acceptance 8 页单次复算已在批次 48 用掉**（5/8=0.6250），本批「acceptance 只跑一次」若复用同一 16/8 集即撞红线；④ **无 release keystore、四个 `RELEASE_*` 变量全缺**，versionCode 仍是硬编码 2；⑤ 设备池在核对期间由 2 台降为 **1 台**（5566 消失），且 `PAGE_SIZE=4096`；⑥ 热区 `feature/review` 载着**未提交的批次 49 校准/冲突 UI**（+87/+167 行），`core/database` 14 脏、`core/domain` 8 脏 |

---

## 0. 结论摘要（按开工顺序）

1. **先提交、再谈干净树发版**：`git status --porcelain=v1 | wc -l` → **581**（129 M + 452 ??；本文件落盘前的读数）；最近 5 个提交（`1c109282`/`82891e9d`/`e7422b79`/`9461a97d`/`a87fc70d`）`git show --stat` 全是 docs/evidence，**零源码**。当前树仍能读到批次 49 的未提交代码：`ReviewPlannerV2` 权重注入 seam（79 行，注释自标「批次 49 / P1-9」；`git grep ReviewScoringWeights HEAD` 零命中）与 `feature/review` 校准 UI（+87/+167 行，注释自标「批次 49 修复环」；`KnowledgeCalibrationDecision.kt` 不在 HEAD）。这与批次 49 记录 §9.4「已推送、本地树后续无未提交本批文件」的说法**以当前 git 状态为准**并不一致，需主会话认账。→ 发布阶段「干净树构建」前置不是提交一批文档，而是**先把在飞代码归属裁定清楚**。
2. **G0 四方门现在是红的且原因不是一处**：`python -I tools/ci/check_backup_plan_sync.py` → **TRUE_EXIT=2**（`ERROR 无法建立可比事实：设计件必须恰有一个 6c2-plan-v3 机器块，实际 0`）。根因三层：设计件 §9.11 的 `6c2-plan-v3:begin`（`:1665`）没有自己的 `end`，JSON 块在 `:2309` 被 `6c2-plan-v4:end`（`:2310`）收尾；脚本仍按 v3 标记解析（`tools/ci/check_backup_plan_sync.py:60`）并钉 `73.json`/92 表/closure 4（`:56`/`:267`/`:307-330`）；同脚本的 pytest **6 failed, 2 passed**。**但内容层四方已一致**：v6 机器块 `closure 6 / schema 75 / identity f9a3d1c9… / 99 表`（`:2317-2986`），空库全选摘要 `8567faae…` 与 `SmbkSelectionPlanTest.kt:649-650` 逐字相同，本会话按 `SmbkSelectionPlan.closureDigest` 算法独立复算**相等**。→ 本批要做的是「删/修 v3 残块 + 把门脚本与测试升到 v6/75/99/closure6」，不是改内容。
3. **AUD-08 的「只跑一次」额度已用**：`split_golden_v2.json` 的 `sampleRule` 已冻结 24 页 = dev 16 + acceptance 8；批次 48 记录 `:17`/`:58`/`:140` 明确 acceptance 以 `runs/acceptance-once-01` **只复算一次**（① 5/8=0.6250、③ 3/26=0.1154，跑后零调参），`docs/acceptance/verdicts.md:22` 亦记「未重跑（acceptance 单次复算红线）」。计划 §G3-22「acceptance 8 页只跑一次」若沿用同一 v2 8 页，就是**第二次看同一 holdout**，与既有冻结协议冲突。开工前必须二选一：**冻结新 holdout（v3）**，或**不重跑 acceptance、只交付 dev 与现有 5/8**。
4. **发布面从零开始**：仓库内 `.jks/.keystore` 仅 `.android/debug.keystore`（debug，非 release）；`git ls-files` 无任何签名文件；`keystore.properties` 不存在；`env` 中 `RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` **全部缺失**。`app/build.gradle.kts:33-34` 仍是 `versionCode = 2` / `versionName = "0.2.0"` 硬编码，无 `smbk.versionCode`/git 计数注入。
5. **设备面**：`adb devices -l` 本会话首次查询为 2 台（`emulator-5566`、`emulator-5590`），复跑时 `emulator-5566` 已消失，只剩 `emulator-5590`（AVD `ui_motion_20261107`，Android 14 / API 34 / x86_64 / `PAGE_SIZE=4096`）。**无真机**；arm64 16KB 运行时仍是 D 类 BLOCKED。
6. **协作面**：`feature/review` 与 `app`（提醒/视觉 JS）两条线仍在飞；`core/database` 14 脏、`core/domain` 8 脏、`docs/current` 7 脏、`knowledge-production` 14 脏。本批任何改动前必须按计划再跑 `git status --porcelain -- <目标目录>`，**不整文件抢占**。

---

## 1. HEAD / 远端 / 工作树

| 项 | 实测（本会话命令与输出） |
|---|---|
| HEAD | `git rev-parse HEAD` → `a87fc70d15c8ad726cee17422dbeef0b4977747d`；`git log -1` → `2026-11-08 07:43:59 +0800 docs: 批次49 主会话收口（双门实跑/标定维持现值/产品闭环/22 FAIL 构成/推送）（1 文件）` |
| 分支 / 上游 | `main`；`git status -sb` 首行 → `## main...origin/main` |
| 远端同步 | `git rev-list --left-right --count origin/main...HEAD` → `0	0`（相对本地跟踪引用无领先/落后）；`timeout 20 git ls-remote origin refs/heads/main` → **超时/fatal（exit 124，`expected flush after ref listing`）→ 远端可达性未验证**，不得把 0/0 读成「远端无新提交」 |
| 远端地址 | `origin https://github.com/tianshantingyun/smart-mistake-book.git`（fetch/push 同址） |
| 工作树计数 | `git status --porcelain=v1 \| wc -l` → **581**；状态码：`452 ??` + `129 M` |
| 首层分布 | `core` 75、`docs` 60、`tools` 50、`knowledge-production` 14、`feature` 12、`app` 12；其余为 `x`/`tmp*`/`crops`/`output`/`out`/`growth` 等散件 |
| 最近 5 提交内容 | 全部 docs/evidence：`a87fc70d` 1 文件、`9461a97d` 5 文件、`e7422b79` 922 文件（行级 JSON 重锚）、`82891e9d` 133 文件（证据）、`1c109282` 2 文件（基线/记录）→ **零源码提交** |
| 已注册 worktree | `git worktree list` → 主树 `a87fc70d [main]` + 6 个：`.codex/worktrees/plans-publication`（`32026050`）、`.codex/worktrees/ui-green-pages`（`ad64b8e4`）、`.codex/worktrees/verify-visible-fixes`（detached `a15aebb7`）、`D:/aud11-measure`（detached `a15aebb7`）、`.worktrees/kb-bench`（`0486a532`）、`.worktrees/kernel-readiness`（`de1ff0c7`） |
| 未注册目录 | `.worktrees/image-subsystem/`、`.worktrees/split-large-files/` 存在但**不在** `git worktree list`（未深查，按陈旧副本对待，不读不写） |

**归属判定（只针对已读 diff，不做全树归属）**：

- `core/domain/.../ReviewPlannerV2.kt` 的 79 行 diff 自述「批次 49 / P1-9；预注册 §5.1」→ **批次 49 未提交代码**；`git grep ReviewScoringWeights HEAD` 零命中，证明 HEAD 不含该 seam。
- `feature/review/...KnowledgeReviewSessionViewModel.kt` 的 +167 行与 `KnowledgeReviewSessionScreen.kt` 的 +87 行自述「批次 49 修复环」；`KnowledgeCalibrationDecision.kt` `git cat-file -e HEAD:…` → **exit 128（不在 HEAD）**。
- `core/database/.../RoomStudyDatabase.kt` 的 16 行 diff 自述「PR#13（T8）桥接…手工并集」→ 他线存量，与批次 49 基线 `:163` 的登记一致。
- → 计划 §硬约束「他线在飞文件不整文件抢占」在当前树上**同时**适用于批次 49 遗留与他线；两者都未提交，无法用「已提交/未提交」区分归属。

---

## 2. DB schema / 备份清单 / 机器块 / 四方门

### 2.1 schema = 75（单源常量）

```text
$ sed -n '140,145p' core/database/src/main/kotlin/.../StudyDatabase.kt
internal const val STUDY_DATABASE_VERSION = 75        # :143
$ sed -n '250,256p' …/StudyDatabase.kt
    version = STUDY_DATABASE_VERSION,                # :254
$ ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/ | wc -l
75
$ python -I  # 解析 75.json
version 75 / identityHash f9a3d1c9ea8ef83d7222721ad1015a51 / entities 99 / views 2
```

### 2.2 备份清单（代码单源）

| 项 | 值 | 出处 |
|---|---|---|
| `SCHEMA_VERSION` | 75 | `core/data/src/main/kotlin/.../backup/SmbkSelectionPlan.kt:42` |
| `CLOSURE_VERSION` | 6 | 同上 `:52` |
| `CLOSURE_DIGEST_PREFIX` | `smbk-selection-v6` | 同上 `:55` |
| `allTables` | **99**（`require(allTables.size == 99)`） | 同上 `:203-207`、`:576` |
| 类别表数 | ISSUE_BOOK 35 + MASTERY 40 + SESSIONS 7 + IMAGES 1 = 83；+ always 7 + derived 2 + split 7 = 99 | v6 机器块解析（见 §2.3） |
| `allowedMasks` | `{9, 13, 15}`；0/8 拒绝 | 同上 `:61`、`:597` |
| `trimOrder` / `views` | 91 / 2（`library_catalog`、`knowledge_question_lattice`） | 同上；`views` `:67-69` |
| 可读 closure | 4 / 5 / 6（v5/v6 均在 `ClosureRuleTables.of`） | `ClosureRuleTables.kt:104-108` |

### 2.3 机器块状态（设计件）

- `docs/research/2026-10-17-6c2-content-selection-design.md:1665` 有 `<!-- 6c2-plan-v3:begin -->`，但**没有 `6c2-plan-v3:end`**；其后 JSON 在 `:2309` 以三反引号围栏收尾，`:2310` 是 `<!-- 6c2-plan-v4:end -->`。
- `:2317` 起有 `<!-- 6c2-plan-v6:begin -->`，`:2986` 有匹配的 `6c2-plan-v6:end`；块内容实测：`closureVersion 6 / schemaVersion 75 / identityHash f9a3d1c9… / 99 表（unique 99）/ trimOrder 91`。
- v6 块空库全选摘要 `8567faae0e90ad4b720eab9fb4ceda82ce90fdb14e7c8ec4dc8309a520fb286c`；本会话用 `SmbkSelectionPlan.closureDigest` 同算法独立复算 → **逐字相等**；`SmbkSelectionPlanTest.kt:649-650` 的 `GOLDEN_EMPTY_FULL_DIGEST` 也是该值；该测试的 `MACHINE_BLOCK` 正则已是 `6c2-plan-v6`（`:652-655`）。
- v3 残块内容仍是 `closure 5 / schema 73 / identity 1d086050… / 92 表`（解析值），与当前代码/75.json 不一致，仅作历史。

### 2.4 四方门 `check_backup_plan_sync.py`（本会话实跑）

```text
$ python -I tools/ci/check_backup_plan_sync.py > build/b50-baseline-backup-sync.txt 2>&1
TRUE_EXIT=2
ERROR 无法建立可比事实：设计件必须恰有一个 6c2-plan-v3 机器块，实际 0
```

```text
$ python -I -m pytest tools/tests/test_backup_plan_sync.py -q
6 failed, 2 passed in 0.40s
# 失败含 test_repository_is_currently_synced / test_machine_block_drift_is_detected
# / test_identity_hash_drift_exits_one / test_v4_*（3 例）
```

**脚本/测试与现状的钉值差**（G0-8/9 必须一起处理，计划文字只提「设计件机器块修复」）：

| 位置 | 现钉 | 当前事实 |
|---|---|---|
| `tools/ci/check_backup_plan_sync.py:56` | `SCHEMA_PATH = …/73.json` | schema 75 |
| 同 `:60` | `MACHINE_BLOCK` 只认 `6c2-plan-v3` | 现行块是 v6 |
| 同 `:267` | `len(allTables) == 92 == len(schema_tables)` | 99 |
| 同 `:302` | `block["digest"]["goldenEmptyFull"]["sha256"]` | v6 块键名是 `emptyFullSelectionGolden` |
| 同 `:307-330` | closure 4 冻结表 + v4 夹具 | 可保留（历史兼容），但版本三元组须按 75/closure6 校验 |
| `tools/tests/test_backup_plan_sync.py:87` | 断言设计件含 `1d086050…` | 现行 identity `f9a3d1c9…` |

> **边界**：脚本只证明静态四方同源，不证明运行时 schema/迁移/设备备份行为（脚本自述 `:31`）。本基线**未改**脚本、测试或设计件。

### 2.5 备份设备用例的静态矛盾（G0-10 直接相关）

5 个 backup androidTest 为 `M`（`BackupRestoreInstrumentedTest`、`BackupRowCountsCoverageInstrumentedTest`、`BackupSelectionRestoreInstrumentedTest`、`BackupVisualReferenceInstrumentedTest`、`DeviceMigrationDrillInstrumentedTest`）。其中 `BackupRowCountsCoverageInstrumentedTest.kt`：

- `SCHEMA_ASSET = "…/73.json"`（`:570`），但断言 `73.json 的 database.version 必须等于 SmbkSelectionPlan.SCHEMA_VERSION`（`:90-93`，后者=75）与 `assertEquals("73.json entities 必须恰有 92 项", 92, …)`（`:83`）。
- → 若现在上设备，该用例**静态必红**（73≠75、92≠99）；这是 G0-10 需要把 asset 与 92 文案一并升到 75/99 的直接证据，**不是**设备实测结论（本基线未跑）。

---

## 3. 热区快照（M/?? 清单 + mtime）

### 3.1 app visualjs（JS/UI 他线）

```text
 M app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuringTest.kt
?? app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientFrameQueueDeviceInstrumentedTest.kt
?? app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsDeathUnconfirmedFailClosedInstrumentedTest.kt
?? app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsReconnectBudgetInstrumentedTest.kt
?? app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/VisualMeasureEnvironmentBudgetTest.kt
```

| 文件 | mtime（`stat -c '%y'`） |
|---|---|
| `VisualContentMeasuringTest.kt` | 2026-11-07 05:18:20 |
| `VisualJsClientFrameQueueDeviceInstrumentedTest.kt` | 2026-11-04 05:24:59 |
| `VisualJsDeathUnconfirmedFailClosedInstrumentedTest.kt` | 2026-11-01 02:28:26 |
| `VisualJsReconnectBudgetInstrumentedTest.kt` | 2026-11-01 02:22:33 |
| `VisualMeasureEnvironmentBudgetTest.kt` | 2026-11-01 02:37:01 |

- `app/src/main/kotlin/.../visualjs/` **干净**（`Kd76ReportVerdict.kt`、`VisualContentMeasuring.kt`、`VisualJsClientEngine.kt`、`VisualJsFrameQueue.kt`、`VisualJsProtocol.kt`、`VisualJsService.kt` 均无 status 条目）。

### 3.2 核心 visual（`core/data`）

```text
 M core/data/src/androidTest/kotlin/.../core/data/visual/StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt
 M core/data/src/androidTest/kotlin/.../core/data/visual/TutorVisualRebuildRestoreInstrumentedTest.kt
 M core/data/src/test/kotlin/.../core/data/visual/FigureGenerationAuditRecorderTest.kt
```

| 文件 | mtime |
|---|---|
| `StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt` | 2026-11-07 05:18:20 |
| `TutorVisualRebuildRestoreInstrumentedTest.kt` | 2026-11-07 05:18:20 |
| `FigureGenerationAuditRecorderTest.kt` | 2026-11-07 05:18:20 |

- `core/data/src/main/kotlin/.../core/data/visual/` **干净**（图/JS 引擎生产码无 status 条目）。

### 3.3 feature/review（本批 5b/5c/6B-3 落点，载批次 49 未提交 UI）

```text
 M …/feature/review/KnowledgeReviewSessionScreen.kt          (+87)
 M …/feature/review/KnowledgeReviewSessionViewModel.kt       (+167)
 M …/feature/review/KnowledgeReviewSessionViewModelTest.kt   (17 行改动)
?? …/feature/review/KnowledgeCalibrationDecision.kt           (新文件，不在 HEAD)
?? feature/review/gradle.lockfile
```

| 文件 | mtime |
|---|---|
| `KnowledgeReviewSessionScreen.kt` | 2026-11-08 05:57:57 |
| `KnowledgeReviewSessionViewModel.kt` | 2026-11-08 05:57:44 |
| `KnowledgeReviewSessionViewModelTest.kt` | 2026-11-07 05:18:20 |
| `KnowledgeCalibrationDecision.kt` | 2026-11-08 05:57:30 |

- 内容已读：ViewModel diff 含「批次 49 修复环」校准决策/冲突卡；`KnowledgeCalibrationDecision.kt` 是校准选择结果类型。**不是**本批新写，但会与本批 5b/5c/6B-3/KD-67 直接同文件冲突。

### 3.4 feature/tutor

```text
 M …/feature/tutor/…/TutorSurfacePolicyTest.kt   (6 行)
?? feature/tutor/gradle.lockfile
?? feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/nul   (0 B, mtime 2026-09-29 09:57)
```

### 3.5 app Root / SecondaryScreens（本批装配点）

| 文件 | status | mtime |
|---|---|---|
| `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt` | **干净**（`git status` 空） | 2026-11-08 05:58:14 |
| `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt` | **干净** | 2026-11-08 00:20:04 |

- 同模块其余 M 为他线：`ReviewReminderCoordinator.kt`（6 行）、`SchedulingSettingsScreen.kt`（23 行）、`ReviewReminderCoordinatorTest.kt`、`SavedMistakeSheetBytesTest.kt`、两个 `ReviewReminder*InstrumentedTest.kt`。

### 3.6 其他会被本批碰到的脏区

| 区域 | 计数 | 代表性文件 |
|---|---|---|
| `core/database` | 14 | `RoomStudyDatabase.kt`（PR#13 桥接 16 行）、`KnowledgeSearchIndex{Build,Completeness}.kt`、`StudyDatabasePort.kt`、`TendingCaptureDao` 等 |
| `core/domain` | 8（6 M + 2 ??） | `ReviewPlannerV2.kt`（批次 49 权重 seam 79 行）、`ReviewPlanner.kt`、`AdaptiveSelectionConsumption.kt`、`KnowledgeReviewQueue.kt`、`TutorJudgedReview.kt`、`MistakeOrganizationRepositoryTest.kt`；?? `ReviewScoringWeightsSeamTest.kt`、`gradle.lockfile` |
| `docs/current` | 7 | `release-gates.md`、`data-schema.md`、`architecture-contract.md` 等 |
| `knowledge-production` | 14 | `wusan/materials.jsonl` + 13 个 `_tmp_*`/候选 CSV |
| `core/data/.../backup`（生产码） | **0** | 可安全做最小 hunk（G0 清单单源） |
| 未跟踪 lockfile | **14**（已跟踪 0） | `app`、`core/{data,database,domain,export,markdown,model,ui}`、`feature/{capture,library,profile,review,tutor}`、`settings-gradle.lockfile` |

---

## 4. 验收台账（22 FAIL / 278 BLOCKED）与四门现状

### 4.1 计数（本会话实跑）

```text
$ awk -F'|' 'NF>6 {gsub(/ /,"",$(NF-1)); print $(NF-1)}' docs/acceptance/ledger.md | sort | uniq -c
    900 PASS
    278 BLOCKED
     22 FAIL
      2 verdict
      1 ---
```

- FAIL 22 行 ID：`F-057 H-018 H-062 J-001 J-002 J-020 J-021 J-024 J-025 J-027 J-033 J-034 J-041 J-048 J-051 J-052 GATE-032 GATE-038 GATE-042 GATE-091 GATE-092 GATE-099`。
- 域分布：FAIL = J 13 / GATE 6 / H 2 / F 1；BLOCKED = FIX 85 / J 48 / I 37 / F 32 / A 25 / H 19 / E 9 / D 8 / GATE 4 / G 7 / C 2 / B 2。
- 与批次 49 终裁值逐值一致（`docs/acceptance/verdicts.md:12` → 900/22/278/0）→ 本基线未改任何 verdict。

### 4.2 四门基线

| 门 | 当前基线 | 出处 |
|---|---|---|
| **3D D-1**（KC 绑定精确率 ≥0.95，Wilson 单侧下界） | **未通过**：只有抽样点估计 ≈89.4%，无任何 ≥0.95 下界结论；仓内两份记录互相矛盾（95.15% 点估计 vs 89.4% 抽样） | `docs/research/2026-11-02-error-taxonomy.md:411`、`:418`；`docs/research/2026-11-02-batch-40-baseline.md:96` |
| **3D D-3**（召回保持） | 设备融合主集 **117/129 = 0.9069767**；MRR 0.7323；词面 102/129 | `docs/kb-acceptance-2026-10-17.md:574`、`:627` |
| **3D D-2**（结构审计） | 本会话未找到单一 D-2 门；`tools/kb_build/scan_duplicate_nodes.py`、`overfull_nodes.py`、`tools/kb_coverage/binding_precision.py`、`dedupe_near_dup_materials.py` 是现成构件（本批需组装成逐项清单） | 本会话 `grep -rln "近重复\|超配"` 命中 |
| **3E**（队列/预算） | 旧 KD 已修（KD-46 fixed 2026-10-18；KD-64 fixed 2026-10-26，设备 3/3）；**本批所需的「全库扫描捞回 / F8·F17 封顶 / 每日·单计划口径」设备证据本会话未找到**（androidTest 文件名检索未命中 review 队列容量用例） | `docs/known-defects.md:2055`、`:2563`；需求口径 `docs/requirements-fused-final-2026-10-11.md:114-118` |
| **AUD-08** | v2 24 页（dev16/acceptance8）：最终门 **① 10/24=0.4167 <0.9**、③ 3/78=0.0385≤0.05、**切残 3≠0**；acceptance 单次已跑 **① 5/8=0.6250、③ 3/26=0.1154** | `docs/research/2026-11-07-batch-48-record.md:100`、`:140`、`:144`；`docs/acceptance/verdicts.md:22` |
| **MVA** | 机制已在树且**干净**：盲测 120 / 开发 24（冻结 2026-10-25）、`freeze_manifest.json`、`budget.json`、门常量（≥90/95/90/0，p95 30s/120s）；`reports/` 只有 **fake** 自检（2026-10-26），README 自述「真实 Provider 质量 UNVERIFIED」。未找到 VLM-as-judge 实现（`tools` 深度 3 `*judge*` 仅知识线工具） | `tools/figure_eval/README.md:5`、`:10-18`；`tasks_blind_120.json`/`tasks_dev_24.json` 解析 |

### 4.3 AUD-08 红线（必须开工前裁定）

- 冻结集：`tools/split_golden/tables/split_golden_v2.json` → `pages` 24、`evidenceGradeLevel = G1`、`sampleRule` 明写「前16=dev 后8=acceptance」。
- 批次 48 已把 acceptance 8 页以 `runs/acceptance-once-01` **只复算一次**，并在记录中写出 5/8=0.6250 与 3/26=0.1154（`2026-11-07-batch-48-record.md:58`、`:140`），且 `verdicts.md:22` 记「未重跑（acceptance 单次复算红线）」。
- 计划 §G3-21/22 写「dev 16 页调参（先红后绿）」+「acceptance 8 页只跑一次」。**若仍是这 24 页/8 页**，第二次 acceptance 会违反同一冻结协议（同一 holdout 二次曝光）。
- **需要主会话裁定**：A) 冻结新 holdout（如 v3 24 页，重新抽样/标注）；或 B) 本批不重跑 acceptance，门值沿用 5/8 并如实 FAIL。**不得**在不说明的情况下把第二次跑当「第一次」。

---

## 5. 设备 / AVD（只读查询）

```text
$ C:/Android/Sdk/platform-tools/adb.exe devices -l     # 首次
emulator-5566  device  product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:1
emulator-5590  device  product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:2

$ … adb.exe devices -l                                 # 复跑（核对后段）
emulator-5590  device  product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:1
# emulator-5566 已不在列表（非本会话操作；无安装/停止动作）
```

| 设备 | 实测 | 包 |
|---|---|---|
| `emulator-5590`（AVD `ui_motion_20261107`） | API 34 / Android 14 / x86_64 / `PAGE_SIZE=4096` | `com.tingyun.smartmistakebook.localfirst`、`.localfirst.test`（2 个） |
| `emulator-5566` | 首次在线、复跑消失；本会话未取得其 getprop | — |

- AVD 清单（`emulator.exe -list-avds`）：`api23_smoke`、`api23_smoke32`、`api23_smoke64`、`audit_user_visible_20261016`、`kb_test_device`、`kb_test_device_iso`、`ps16k_arm64`、`ps16k_x86_64`、`test_device`、`ui_motion_20261107`。
- 工具：`apksigner.bat`、`zipalign.exe` 在 `C:/Android/Sdk/build-tools/37.0.0/`；`bundletool-1.18.3.jar` 只是库 jar（非 fat jar）→ G-REL-17 仍 BLOCKED（`docs/research/2026-11-05-release-gates.md:46`）。
- **无真机**；两台均为 x86_64 模拟器。2h soak 只剩 1 台可用（若 5566 不回来，需串行安排）。
- 本基线**未安装/未启动**任何 app，只做 `pm list packages`/`getprop`/`getconf` 只读查询。

---

## 6. 发布面基线（签名 / versionCode / 16KB / AAB / Play / 备案）

### 6.1 本地 release keystore 与签名变量：**均不存在**

| 检查 | 结果 |
|---|---|
| 仓库内 `*.jks`/`*.keystore`/`*.p12`（深度 4） | 仅 `./.android/debug.keystore`（**debug**，被 `.gitignore` 的 `.android/` 忽略） |
| `git ls-files` 签名文件 | `0` |
| `keystore.properties` | 不存在（`.gitignore:20` 已列） |
| `C:/Users/听云/.android/` | 只有 `debug.keystore`（2026-07-25） |
| `env` 中 `RELEASE_*` | **4 个全缺**（`RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`） |
| Gradle 注入点 | `app/build.gradle.kts:19-24` 空壳 `signingConfigs.create("release")`；`:127-145` 四变量齐备才注入；`:148-158` `validateReleaseSigning` 缺一即抛 |
| 既有结论 | 批次 43 release 门 G-REL-08「缺省 exit 1 并列出四名」PASS（`docs/research/2026-11-05-release-gates.md:37`）；G-REL-09 仍 **FAIL**（一次性证书 `CN=WS43 Ephemeral Local Only, OU=NotForRelease…`；`:38`）；G-REL-11 为 NOT_RUN（门未实现、无生产钥匙；`:40`） |

- 说明：`local.properties`（gitignored，`git check-ignore` exit 0）存有 `SMB_REAL_PROVIDER_*` 三键（**值已脱敏，不在此记录**）；与本批签名无关，但发布前的秘密扫描（G-REL-29）应把它列入扫描面。

### 6.2 versionCode / versionName

- `app/build.gradle.kts:33-34` → `versionCode = 2`、`versionName = "0.2.0"`，静态硬编码；`grep -rn "smbk.versionCode\|versionCodeOverride\|git count" app/build.gradle.kts buildSrc` **零命中** → 计划的「versionCode 由构建注入（git 计数/显式参数）」**尚未实现**。

### 6.3 16KB / ABI / AAB（沿用批次 43/44 结论，本会话未重跑）

- 静态面曾 PASS：`zipalign -c -P 16` exit 0、14/14 `.so` ELF `p_align=0x4000`、仅 arm64-v8a + x86_64（G-REL-12/13/14，`docs/research/2026-11-05-release-gates.md:41-43`）。
- 运行时：x86_64/16KB AVD（`ps16k_x86_64`）曾 PASS（G-REL-15，`:44`）；arm64 在 x86_64 宿主 **BLOCKED**（G-REL-16，`:45`）；AAB 页对齐 **BLOCKED**（无 bundletool-all，`:46`）。
- 本批要用 `unzip+readelf/objdump` 校验 AAB 16KB（计划 §R-7），前置是拿到 bundletool-all 或改用等价解析；本基线**未验证**该路径。

### 6.4 Play / 备案（外部项，全部 BLOCKED，不得伪造）

| 项 | 台账状态 | 证据 |
|---|---|---|
| Play mapping 上传 | `J-017` BLOCKED | `docs/acceptance/ledger.md:958` |
| 商店素材（图标/特色图/截图/描述） | `J-073`–`J-076` BLOCKED | `:1014-1017` |
| 隐私政策 URL / data safety 表单 | `J-077`/`J-078` BLOCKED；`H-036` BLOCKED | `:1018-1019`、`:797` |
| AIGC 分发表单 / 内容分级 | `J-079`/`J-080` BLOCKED | `:1020-1021` |
| 封闭测试轨道/账号/反馈/轮次/vc 上传/签名指纹/16KB 警告 | `J-082`–`J-090` BLOCKED | `:1023-1031` |
| 模型名称/备案号/上线编号回填 | `H-053` BLOCKED；文案仍是占位符 | `:814`；`docs/ai-content-disclosure-copy.md:49`、`:58` |
| App 备案（工信部）/ 生成式 AI 属地登记 | 材料清单第 2/6/7 项「适用」，外部未产出 | `docs/research/2026-10-25-store-launch-materials-checklist.md:12`、`:16-17` |

- 现成草案：`docs/current/privacy-and-data-safety.md`（Play Data safety answers 段）与 `docs/ai-content-disclosure-copy.md` 存在；**表单未提交**、备案号未取得 → 本批只能做本地材料/占位，状态维持 BLOCKED。

---

## 7. 未验证 / 缺口 / 开工前清单

1. **NOT RUN（本 ask 明确排除）**：任何 Gradle 编译/单测/androidTest/设备执行；`acceptance_report.py`；`acceptance_lint.py`；`check_lockfiles.py`；`check_release_manifest.py`；`figure_eval_gate.py`；`check_split_gate.py`。→ 上述每一项都**没有本会话证据**，不得引用为通过。
2. **工作树不干净**：581 路径；发布前必须先把批次 49 代码（`ReviewPlannerV2`、`feature/review` 校准 UI 等）与各他线归属裁定并提交/排除；`git add -A` 会带入 14 个未跟踪 lockfile + 他线文件（批次 49 记录 §7 的禁令仍有效）。
3. **G0-8/9 的真实工作量**：计划只写「设计件机器块修复 + 四方一致」。实测还包含 **`check_backup_plan_sync.py` 与其 pytest 的钉值升级**（73→75、92→99、v3→v6 标记、`goldenEmptyFull`→`emptyFullSelectionGolden`、identity `1d086050`→`f9a3d1c9`）与 **5 个 backup androidTest 的 73/92 文案与 asset 升级**。漏掉任一项，门仍 exit 2 或设备用例仍必红。
4. **AUD-08 acceptance 二次曝光风险**（§4.3）：需要主会话在开工前给出 A/B 裁定；本基线不替该裁定背书。
5. **门阈值不可改**：本基线只读，未改任何阈值；计划列出的 0.95/0.9/MVA 90-95-90-0 仍按原值执行。
6. **3D 标注纪律**：`split_golden_v2.json` 的 `evidenceGradeLevel = G1`、`evidenceGrade` 明写「model-labeled, no human expert; single model … grade capped at G1」——本批 3D 新增标注必须同样如实标 model-labeled，不得升级为人工。
7. **设备不稳定**：5566 在核对期间消失；若本批依赖两台并行（soak + 功能），开工时须重新 `adb devices -l` 并登记，不得引用本基线的首查结果。
8. **证据落点尚不存在**：`docs/research/evidence/2026-11-09-batch-50/` 与 `docs/research/2026-11-09-batch-50-record.md` 均不存在（`ls` 报 `No such file or directory`）；本文件是本批唯一已落盘产物。
9. **未做全树归属**：本基线只对 §3 列出的热区与 §0/§7 点名的文件读了 `git status`/diff；581 路径中其余（tools 50、docs 60 等）未逐文件归属，开工前按阶段再跑 `git status --porcelain -- <目标目录>`。
10. **本地 release key 的「非 Play 生产身份」**：当前连本地 release key 都没有；本批若新建，必须在记录里写明指纹/DN 与「非 Play 生产」标注，并对齐 `RELEASE.md:20-28` 的四变量契约。

---

## 8. 本会话实跑命令（可重跑）

```bash
# HEAD / 远端 / 工作树
git rev-parse HEAD
git log -1 --format='%H%n%ci%n%s'
git rev-list --left-right --count origin/main...HEAD
timeout 20 git ls-remote origin refs/heads/main   # 本会话超时（exit 124）→ 远端可达性未验证
git status --porcelain=v1 | wc -l
git status --porcelain=v1 | cut -c1-2 | sort | uniq -c
git worktree list

# schema / 备份清单
sed -n '140,160p;250,256p' core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/ | wc -l
python -I   # 解析 75.json / v6 机器块 / 独立复算 closureDigest（见 §2.2/§2.3）

# 四方门（本会话读数 exit 2）
python -I tools/ci/check_backup_plan_sync.py
python -I -m pytest tools/tests/test_backup_plan_sync.py -q

# 台账
awk -F'|' 'NF>6 {gsub(/ /,"",$(NF-1)); print $(NF-1)}' docs/acceptance/ledger.md | sort | uniq -c

# 设备（只读）
C:/Android/Sdk/platform-tools/adb.exe devices -l
C:/Android/Sdk/platform-tools/adb.exe -s emulator-5590 shell getprop ro.build.version.sdk
C:/Android/Sdk/platform-tools/adb.exe -s emulator-5590 shell getconf PAGE_SIZE
C:/Android/Sdk/emulator/emulator.exe -list-avds

# 签名面
find . -maxdepth 4 \( -name '*.jks' -o -name '*.keystore' -o -name '*.p12' \)
git ls-files | grep -iE '\.jks$|\.keystore$'
env | grep -iE 'RELEASE|KEYSTORE|SIGNING'   # 4 个 RELEASE_* 均无
```

**证据边界**：本基线所有「现状」均来自上表命令/文件阅读；所有「设备执行/门禁执行」类结论一律标 NOT RUN，不替代批次 50 的门禁证据。
