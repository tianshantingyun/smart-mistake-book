# 批次 51 基线核对（2026-11-10）：收官总攻开工前事实面

| 字段 | 值 |
|---|---|
| 角色 | 基线/协作核对员（**只读**：未改源码/测试/台账/金标/门脚本；本文件是唯一新建文件） |
| 计划 | `.zcode/plans/plan-2026-11-10-batch-51-final.md`（已完整读取，117 行；P0 3 / R 3 / G1 8 / G2 3 / G3 5 / G4 5 / JS 3 / 数据安全与发布 6 / 验收复核 7 = 22 阶段 45 项） |
| 上一批终裁 | `docs/research/2026-11-09-batch-50-record.md`（四门未过：3D `PASS main / FAIL guardrail`；3E 条件性；MVA `NOT_EVALUABLE`；AUD-08 `FAIL`）——已读 |
| 核对时点 | 2026-11-10 00:10:59 +0800（`date "+%Y-%m-%d %H:%M:%S %z"` 实测） |
| 本会话实跑 | 只读 `git`/`grep`/`ls`/`python -I`/`adb devices`/`adb emu avd name`/PowerShell 进程查询；**零 Gradle、零安装、零设备写入、零 Provider 调用、零门重跑** |
| 判定 | **可开工，但有 6 处必须先认账**（见 §0）；其中 **C1（AUD-08 无「从未看过」的 acceptance 切片）** 与 **C5（MVA/切分门脚本未入库）** 会直接影响本批「完成定义」能否成立 |

---

## 0. 结论摘要（按开工顺序，均为本会话实测）

1. **C1 — AUD-08 的「新 acceptance 切片（从未看过）」在现有金标上不存在**。计划第 23 项要求「新 acceptance 切片（从未看过）只跑一次」，但金标 `split_golden_v2` 的 24 页 = dev 16 + acceptance 8；那 8 页已先后被批次 48（`acceptance-once-01`）与批次 50（`acceptance-b50-01`）消费，`SCOPE-CORRECTION.md §2` 已明写「held-out 属性不可恢复（本批是第二次观测）」。要满足「从未看过」，必须**新增页面并重新盲标**（等于做 v3 切片）或**把口径降级如实登记**；不能沿用 v2 的 8 页再跑一次并称 held-out。→ 需主会话在 G3 开工前裁定并写进证据，不得默认复用。
2. **C2 — 3D 旧抽样框已不可重建**。批次 50 `d1/REPORT.md` 修复环补记：抽样框 `build/kb-staging/overfull_nodes.csv` 被 D-2 原地重写，`sample_d1.py --check` 为 `match=false` / exit 2。计划第 11 项已要求「新抽样框快照 + 新 audit id」，**不得复用 `B50-D1-2026-11-08`**，也不得把旧 1600 样本当可重放证据。
3. **C3 — 3E 改成「每日累计」必须反转一条现存的绿测断言**。`StudyReviewPlanner3EScanInstrumentedTest.kt:383-388` 现在断言「同一天两次排计划累计 > 10」并注明「实现口径是『每次排计划 ≤10』」。任务 17/18 一旦改语义，这条断言必须改为「同日累计 ≤10」；**这是口径反转，不是放宽断言**——按 `git` 当前状态该文件已是 ` M`，改动前须按协作规则再查归属，避免与 3E 他线抢占。
4. **C5 — 计划点名的两个门入口没有入库**：`tools/figure_eval/mva_gate.py` 与 `tools/figure_eval/test_mva_gate.py`、`tools/figure_eval/judge_channel_map.json` 均为 `??`（未跟踪），`tools/figure_eval/figure_eval_gate.py` 与 `tools/split_golden/check_split_gate.py` 为 ` M`。→ 从 HEAD 干净树复算 MVA/AUD-08 门**做不到**；独立复核只能对工作树做，且必须在记录里说明工具的入库状态。
5. **四方备份一致性当前是绿的**：`python -I tools/ci/check_backup_plan_sync.py` → **exit 0 / PASS 四方一致（code / §9.14 机器块 / golden / 75.json）**，99 表 / 2 视图 / trimOrder 91 / masks 9,13,15 / digest `8567faae…`。DB schema 75 三处同源（代码常量、`75.json`、机器块）。
6. **工作树是「脏但可开工」**：`git status --porcelain` **619** 行（153 ` M` + 466 `??`），`-uall` 展开 **3846**（与台账报告的 `dirty_paths=3846` 逐字一致）。JS/UI 与 KB 热区都有在飞改动（§2 全清单）。**main 树当前无 gradlew 客户端进程**，但 ui-green-pages worktree 有一个 Gradle 客户端在跑（PID 43348），Gradle 阶段前必须再确认。

---

## 1. HEAD / 远端 / 工作树

| 项 | 实测（命令与输出） |
|---|---|
| HEAD | `git rev-parse HEAD` → `8dd29e963975ff44703f638064322be0e7494f98`；`git log --oneline -1` → `8dd29e96 docs: 记 CV 切题算法删除与切题只走模型识别（批次50 纠偏；2 文件）` |
| 分支 / 上游 | `git status -sb` 首行 → `## main...origin/main` |
| 远端一致性（本地跟踪） | `git rev-list --left-right --count main...origin/main` → `0	0`（无领先/落后） |
| 远端一致性（真实可达性） | `timeout 25 git ls-remote origin refs/heads/main` → **无输出、`Terminated`、exit 143**（25s 超时）→ **远端真实状态未验证**；`0 0` 只证明本地 `origin/main` 引用未被 fetch 落后，**不得**读成「远端 = 本地」。批次 50 基线同项亦为超时。 |
| 工作树计数 | `git status --porcelain \| wc -l` → **619**；状态码 `153  M` + `466 ??` |
| 展开计数 | `git status --porcelain -uall \| wc -l` → **3846**（未跟踪目录展开为单文件后；与 `acceptance_report.py` 的 `dirty_paths=3846` 相同） |
| diff 规模 | `git diff --stat` → `153 files changed, 3858 insertions(+), 1425 deletions(-)`（`--ignore-all-space` 仍 3811/1378 → 非纯换行差异；`core.autocrlf=true`、`core.filemode=false`） |
| ` M` 首层分布 | `core 70`、`docs 48`、`tools 12`、`app 11`、`feature 9`、`knowledge-production 1`、`.gitignore 1`、`dynamic_gui_architecture_plan.md 1` |
| `??` 首层分布 | `tools 42`、`core 24`、`docs 16`、`knowledge-production 13`、`feature 9`、`app 6`，其余 ~350 项为根目录散件（`.agent_*`、`.tmp_*`、`.audit_crops_*`、`Screenshot_*.png`、`x/`、`out/`、`output/`、`crops/`、`growth/`、`tmp*` 等） |
| 已注册 worktree | `git worktree list` → 主树 `8dd29e96 [main]` + `.codex/worktrees/plans-publication`（`32026050`）、`.codex/worktrees/ui-green-pages`（`ad64b8e4`）、`.codex/worktrees/verify-visible-fixes`（detached `a15aebb7`）、`D:/aud11-measure`（detached `a15aebb7`）、`.worktrees/kb-bench`（`0486a532`）、`.worktrees/kernel-readiness`（`de1ff0c7`） |
| 未注册目录 | `D:/smb-ui-forest-20261027` 是 **symlink**（`ls -d` 显示 `@`）→ `C:/Users/听云/.codex/worktrees/ui-green-pages/smart mistake book/`，即已注册 worktree `ad64b8e4` 的别名；**不是**第 8 个树 |

> 说明：466 个未跟踪项里大量是历史研究散件（crops/临时 dump）。它们不影响本批结论，但会让「干净树构建」与 `dirty_paths` 读数长期维持在数千量级——发布阶段（任务 37）必须用 clone 而非本树。

---

## 2. 热区快照（JS / UI / KB 在飞文件）

命令：`git status --porcelain -- app feature knowledge-production tools/kb_build tools/kb_coverage tools/kb_perf tools/figure_eval tools/split_golden` → 136 行；逐项如下。

### 2.1 KB 线（计划点名「不整文件抢占」）

| 状态 | 路径 |
|---|---|
| ` M` | `knowledge-production/wusan/materials.jsonl` |
| ` M` | `tools/kb_build/merge_candidate_verdicts.py`、`tools/kb_build/new_content.py` |
| `??` | `tools/kb_build/_check_p0018.py`、`tools/kb_build/merge_head_b26.py` |
| ` M` | `tools/kb_coverage/judge_ledger.py`、`tools/kb_coverage/materialize.py`、`tools/kb_coverage/office_extract.py` |

> 与计划 §不变量 5 的表述一致（`materials.jsonl` 与 `tools/kb_build/*` 已 M）。3D 改绑（任务 8）要动的是 **shipped pack** `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json`（当前 sha256 `10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670`，4,538,305 B，mtime 2026-11-04 13:30），与上述 KB 在飞文件是不同文件；`tools/kb_build/rebind_materials.py` 存在（4,863 B，2026-09-17）且**未被改动**。

### 2.2 JS / UI 线（计划点名「只做最小 hook」）

| 状态 | 路径 |
|---|---|
| ` M` | `app/src/main/kotlin/.../SmartMistakeBookApplication.kt`、`SmartMistakeBookRoot.kt`、`SecondaryScreens.kt`、`SchedulingSettingsScreen.kt`、`ReviewReminderCoordinator.kt` |
| ` M` | `app/src/test/.../ReviewReminderCoordinatorTest.kt`、`SavedMistakeSheetBytesTest.kt`、`visualjs/VisualContentMeasuringTest.kt` |
| ` M` | `app/src/androidTest/.../CapabilityScreenInstrumentedTest.kt`、`MistakeExportNotificationsInstrumentedTest.kt`、`ReviewReminderPlatformInstrumentedTest.kt` |
| `??` | `app/src/androidTest/.../visualjs/VisualJsClientFrameQueueDeviceInstrumentedTest.kt`、`VisualJsDeathUnconfirmedFailClosedInstrumentedTest.kt`、`VisualJsReconnectBudgetInstrumentedTest.kt`、`app/src/test/.../visualjs/VisualMeasureEnvironmentBudgetTest.kt`、`app/src/androidTest/.../AiContentLabelsInstrumentedTest.kt` |
| ` M` | `feature/review/.../KnowledgeReviewSessionScreen.kt`、`KnowledgeReviewSessionViewModel.kt`、`ReviewSessionScreen.kt`、`KnowledgeReviewSessionViewModelTest.kt` |
| ` M` | `feature/tutor/.../TutorGeneratedTurn.kt`、`TutorPaperEditApplier.kt`、`TutorSurfacePolicyTest.kt` |
| ` M` | `feature/library/build.gradle.kts`、`MistakeDetailRoute.kt` |
| `??` | `feature/review/.../KnowledgeCalibrationDecision.kt`、`feature/tutor/.../TutorPaperEditModelTextTest.kt`、`feature/tutor/.../TutorAiGeneratedLabelInstrumentedTest.kt` |
| `??` | `feature/capture/gradle.lockfile`、`feature/library/gradle.lockfile`、`feature/profile/gradle.lockfile`、`feature/review/gradle.lockfile`、`feature/tutor/gradle.lockfile`、`settings-gradle.lockfile` |
| `??` | **`feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/nul`**（Windows `nul` 重定向残留空文件；不得提交、不得当作源码） |

### 2.3 门脚本热区

| 状态 | 路径 | 影响 |
|---|---|---|
| ` M` | `tools/figure_eval/figure_eval_gate.py` | MVA 旧门（批次 50 记录：`critical_error_evidence` 标 `citable=false`） |
| ` M` | `tools/split_golden/check_split_gate.py`（34,568 B，sha256 `94f8386835b6…`） | AUD-08 判门入口；`SCOPE_REASONS_V2[1]` 已按批次 50 修复环改写 |
| `??` | `tools/figure_eval/mva_gate.py`（26,004 B，sha256 `d50567dc5b1f…`） | 计划 §验证命令点名的 MVA 入口；**未入库**，`argparse` 自述「只读夹具，不生成图」(`:521`) |
| `??` | `tools/figure_eval/judge_channel_map.json`（56,652 B）、`tools/figure_eval/test_mva_gate.py` | 判官通道映射 / 门脚本自测；**未入库** |

---

## 3. DB schema / 备份四方 / 机器块

### 3.1 schema = 75（三处同源，实测）

| 来源 | 位置 / 读数 |
|---|---|
| Room 常量 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:142` → `internal const val STUDY_DATABASE_VERSION = 75`；`:254` → `version = STUDY_DATABASE_VERSION,` |
| 导出物 | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/*.json` → **75 个**；`75.json` 423,942 B（2026-11-07 05:20） |
| 备份清单常量 | `core/data/.../backup/SmbkSelectionPlan.kt:42` → `SCHEMA_VERSION = 75`；`:49` → `IDENTITY_HASH = "f9a3d1c9ea8ef83d7222721ad1015a51"`；`:52` → `CLOSURE_VERSION = 6`；`:61` → `allowedMasks = setOf(9, 13, 15)` |
| 设计件机器块 | `docs/research/2026-10-17-6c2-content-selection-design.md:2318`（`6c2-plan-v3:begin`）… `:2991`（end）；`:2322` `"schemaVersion": 75`；`:2323` `"identityHash": "f9a3d1c9…"`；`:2328` note「99 表 / 2 视图 / closure 6」 |
| 断言 | `SmbkSelectionPlan.kt:576` `require(allTables.size == 99)`；`:585` `require(trimOrder.size == 91)`；`:592` `require(softReferences.size == 24)`；`:597` `allowedMasks == setOf(9,13,15)` |

**请勿被注释误导（存量漂移，非本批改动）**：同一文件里 `:202` 写「97 张表（类别 78 + ALWAYS 7 + DERIVED 2 + SPLIT 7）」、`:210` 写「89 项裁剪顺序」、`:324` 写「21 条软引用」；与代码断言（99 / 91 / 24）和机器块（99 / 91）都不一致，**是注释旧了，代码与机器块是对的**。任何阶段不要按注释数字推算，也不要顺手改注释（超出本批范围，改则需单独登记）。

### 3.2 四方门（计划 §验证命令第 2 条，本会话实跑）

```text
$ python -I tools/ci/check_backup_plan_sync.py
  OK   closureVersion: code==block：code=6 block=6
  OK   schemaVersion: code==block==75.json：code=75 block=75 schema=75
  OK   identityHash: code==block==75.json：code=f9a3d1c9ea8ef83d7222721ad1015a51 …
  OK   table list: code==block==75.json：code=99 block=99 schema=99; code-only=[] schema-only=[]
  OK   trimOrder: code==block：code=91 block=91
  OK   allowedMasks == block maskPolicy.closedMasks：code=[9, 13, 15] block=[15, 13, 9]
  OK   digest: recomputed==test golden==block golden：8567faae0e90ad4b720eab9fb4ceda82ce90fdb14e7c8ec4dc8309a520fb286c
  OK   v4: 91 keys and delta == {+error_cause+interior, -material_feature} …
  OK   v4 fixture: bytes == golden == sidecar：3 夹具字节与 sidecar/golden 逐位一致
PASS 四方一致（code / §9.14 机器块 / golden / 75.json）
EXIT=0
```

→ **exit 0**。这是本会话被点名要求实跑的门，已跑；输出行全部为 `  OK  …`（无 `ERROR`/`FAIL` 行），末行 `PASS 四方一致（code / §9.14 机器块 / golden / 75.json）`。

### 3.3 6C-2 备份 mask（用户裁定，不得改）

`SmbkSelectionPlan.kt:60-64`：`allowedMasks = {9, 13, 15}`、`rejectedMasks = (0..15) - allowedMasks`；`FULL_MASK = 15`；`:34` 注释「0、8 与 1–7、10–12、14 一律拒绝，不静默补齐」。四方门已核 `allowedMasks` 与机器块 `maskPolicy.closedMasks` 一致。

---

## 4. 设备 / AVD / 凭据可用性（不打印任何值）

| 项 | 实测 |
|---|---|
| adb 位置 | `adb` 不在 PATH；`$ANDROID_HOME=C:\Android\Sdk`，可执行文件 `C:\Android\Sdk/platform-tools/adb.exe`（8,273,560 B） |
| 在线设备 | `adb devices -l` → **2 台，均 emulator，无真机**：`emulator-5590`（`sdk_gphone64_x86_64`，transport 4）、`emulator-5592`（`sdk_gphone64_x86_64`，transport 2） |
| AVD 归属 | `adb -s emulator-5590 emu avd name` → `ui_motion_20261107`，`ro.build.version.sdk` → **34**；`emulator-5592` → `ui_teaching_20261108`，**34** |
| AVD 池 | `~/.android/avd/*.ini` → **11 个**：`api23_smoke`、`api23_smoke32`、`api23_smoke64`、`audit_user_visible_20261016`、`kb_test_device`、`kb_test_device_iso`、`ps16k_arm64`、`ps16k_x86_64`、`test_device`、`ui_motion_20261107`、`ui_teaching_20261108` |
| 16KB | `ps16k_arm64` / `ps16k_x86_64` 仅 AVD 定义存在；**本会话未启动、未验证 16KB 运行时**（批次 50 记录为 D 类 BLOCKED） |
| `local.properties` 键名（值已掩码，未打印） | `sdk.dir`、`SMB_REAL_PROVIDER_BASE_URL`、`SMB_REAL_PROVIDER_KEY`、`SMB_REAL_PROVIDER_MODEL`；注释标明「DeepSeek real-provider smoke (2026-11-06, user-provided; NEVER commit)」 |
| Provider 结论 | 只有 1 组 **文本/对话** provider 键。**没有任何图像生成 provider 键** → MVA IMG 臂「无 provider 保持 BLOCKED」与计划一致，本批不要试图用现有键冒充图像后端。 |

> 密钥纪律：本文件只记录**键名**；`local.properties` 值与任何 token 均未出现在本文件、命令输出或结果里。

### 4.1 Gradle 并发面（计划红线：不得与其他 agent 并发）

| PID | 类型 | gradle user home | 读数 |
|---|---|---|---|
| 56488 | `GradleDaemon` | 默认（`~/.gradle`） | 内存 3,111,144 K |
| 44808 | `GradleDaemon` | 默认（`~/.gradle`） | 内存 2,368,956 K |
| 43348 | Gradle 客户端（`gradlew`，非 daemon） | `D:\smb-ui-forest-20261027\.gradle` | 启动 2026/11/10 00:06:20；CPU 00:10:56 与 00:10:59 两次采样均 `4.3125`（持平） |

- 43348 的 `-Dgradle.user.home` 指向 `D:\smb-ui-forest-20261027\.gradle`，该路径是 ui-green-pages worktree 的 symlink → **这是另一条线（UI 绿页）在跑/挂起的 Gradle 客户端**，与 main 树不是同一 `gradle.user.home`。
- 本会话**未发现** main 树的 gradlew 客户端进程 → main 树当前无本树构建在跑。
- 两个默认 home 的 `GradleDaemon` 归属未探针（可能是本树历史 daemon 常驻），**不能**据此断言「空闲」。→ 各阶段起 Gradle 前按计划再做一次 `tasklist`/`Get-Process` 检查；发现他线在跑就等，不并发。

---

## 5. 批次 50 四门证据路径与终态读数

主索引：`docs/research/evidence/2026-11-09-batch-50/EVIDENCE.sha256`（**673 行**；分组 `device 183`、`split-gate 143`、`d1 112`、`aigc 38`、`release 37`、`privacy 25`、`d3 23`、`3e 19`、`d2 14`、`js 13`、`5b 12`、`r4-binding-audit 9`、`g0 8`、`mva 7`、`6b-3 7`、`kd67 6`、`5c 5`）。四门文件均已登记在册（各命中 1 次）。

### 5.1 3D 语义绑定审计（D-1）

| 项 | 值（来源） |
|---|---|
| 证据路径 | `docs/research/evidence/2026-11-09-batch-50/d1/`：`stats.json`、`REPORT.md`、`errors.csv`（44 行 = 表头 + **43** 条）、`FRAME-DRIFT.md`、`frame-drift.json`、`sample.jsonl`、`judgments.csv`、`check_raw.py`、`recompute.py` |
| audit_id | `B50-D1-2026-11-08`（`stats.json`） |
| 样本 | 1600 = overfull 1300 + normal 300（`stats.json#sample`） |
| 判定 | correct 1557 / REBIND 39 / NONE 4 → **43 错**（`stats.json#judgments`）；`errors.csv` 43 行数据（`csv.DictReader` 实计），层分布 `normal 22` / `overfull 21` |
| 主判据 | `lower_95 = 0.975691305961763` ≥ 0.95 → `estimate.pass = true` |
| 护栏 | overfull `0.9770088445` PASS；**normal `0.8979148347511259` < 0.90 FAIL**；`per_layer_guardrail_pass = false` |
| 总判定 | `overall_pass = false`；`verdict = "PASS main / FAIL guardrail"`（`stats.json`） |
| 口径注意 | `REPORT.md:20-24` 修复环补记：抽样框被 D-2 原地重写，`sample_d1.py --check` 现为 `match=false`/exit 2；主判据重算 0.9756882（仍 PASS），层归属 0 条不一致。**旧样本不可重放**。标注性质 = model-labeled（非人工专家）。 |

### 5.2 3E 全库薄弱点扫描 / 队列 / 预算

| 项 | 值（来源） |
|---|---|
| 证据路径 | `docs/research/evidence/2026-11-09-batch-50/3e/`：`summary.json`、`README.md`、`jvm/`（4 份 JUnit XML）、`logs/`（01–09 + apk-sha）、`00-snapshot.txt` |
| 判定 | `verdict = "PASS_WITH_FLAGGED_LIMITATION"`（**条件性，不是无条件 PASS**）；`thresholds_changed = false`；`cap_value = 10` |
| 名额来源 | `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewFullLibraryScan.kt:30` → `const val FULL_LIBRARY_SCAN_DAILY_CAP: Int = 10`（该常量名含 DAILY，但同文件 `:20-28` 注释与 `:567` 文档均写「每次排计划 ≤10」——KD-46 的口径缺口就在此） |
| 设备 | `emulator-5590` / `ui_motion_20261107` / sdk 34 / x86_64；命令 `am instrument … StudyReviewPlanner3EScanInstrumentedTest`；`green_phase` = `OK (4 tests)` / 3.668s；`test_apk_sha256 = dc0abe9d…` |
| 已知限制 | 第一档（今日队列）先占满知识点槽位时，第二档贡献为 0（README §5 R1，产品判断，未改语义） |

### 5.3 MVA（JS / IMG 两臂）

| 项 | 值（来源） |
|---|---|
| 证据路径 | `docs/research/evidence/2026-11-09-batch-50/mva/`：`README.md`、`mva_preflight.json`、`preflight-stdout.txt`、`arm-fake/`、`arm-js/` |
| 门态 | **`FAIL` / `NOT_EVALUABLE`**；四道比率门在 120 分母上**0 可评样本**，`first_pass_rate = null`（`README.md:15`、`:21`） |
| JS 臂 | `js_backend_status = NOT_AVAILABLE`、`quality_gates = NOT_EVALUABLE`、`dispatches_used = 0`（`README.md:133`） |
| 旧门恒真 | `figure_eval_gate.py:980` 注释「不可引用为门证据」，`machinery_check.critical_error_evidence` 已标 `citable=false`（批次 50 记录 §修复环） |
| 与计划关系 | 本批 G4 要把它从「0 可评」变成「真实 JS 样本 + 事实核验 + VLM judge」；IMG 臂无 provider 维持 BLOCKED（§4 已核：无图像键） |

### 5.4 AUD-08 切分门

| 项 | 值（来源） |
|---|---|
| 证据路径 | `docs/research/evidence/2026-11-09-batch-50/split-gate/`：`gate-report-b50.json`、`gate-evidence-b50.md`、`README.md`、`SCOPE-CORRECTION.md`、`acceptance-protocol.md`、`MANIFEST-SHA256.txt`、`metrics/`、`perPage/`（内嵌）、`tables/`、`rulings/`、`exports/`、`runs/` |
| 门态 | `gate.state = "FAIL"`、`gate.passed = false`；`count`（①）`7/24 = 0.2916666667` < 0.9（`met=false`）；`fragment`（③）`1/78 = 0.0128205` ≤ 0.05（`met=true`）；`iou`（②）`27/78 = 0.34615`（informational） |
| 硬红线 | `hardRedLines.fragmentedQuestions = {required: 0, satisfied: false, value: 1}` → **切残 1（未过）**。注意与 ③ 区分：③ 是「碎片率」，硬红线是「切残题数」，两者不同。 |
| runs 目录 | `acceptance-b50-01/`、`dev-hint-01/`、`dev-hint-prompt-02/`、`dev-red-01/` → acceptance **只有 1 个 run**（口径「本批只跑一次」成立）。 |
| held-out | `SCOPE-CORRECTION.md §2`：同一 8 页已在批次 48 `acceptance-once-01`（5/8=0.6250、13/26=0.50、3/26=0.1154）消费；**held-out 不可恢复**，本批是第二次观测。 |
| 金标 sha | `03b1eeccbbde19e5ab2e67d5e7de9bf1c1f2b1d8124c3e4b1e6cfe2091e18e54`；应用导出 `0d9f375988e8494606f434ade0a8413a5bfe8be7fb85fa93f7d6c7d62bae54a0`；证据等级 `G1`、`notGeneralizable=true`、单模型盲标 |
| 自动化 | `automation.enabled = false`（裁定 37 / 4C③：门不过不允许自动入库） |

---

## 6. 台账当前统计（`acceptance_report` 最近读数）

命令：`python -I tools/ci/acceptance_report.py`（**只读，未加 `--out`**，不落文件）→ **exit 1**，`ok=false`。

| 字段 | 值 |
|---|---|
| ledger | `docs/acceptance/ledger.md` |
| ledger sha256 | `9044a7794dda196c6fc22c8e989a33099482ef26399e13ffbcbe28c0f045cb97` |
| `head` | `8dd29e963975ff44703f638064322be0e7494f98`（与 git HEAD 一致） |
| `dirty_paths` | **3846**（= `git status --porcelain -uall \| wc -l`） |
| `total` | **1200** |
| `counts` | **PASS 933 / FAIL 13 / BLOCKED 254 / UNVERIFIED 0** |
| `state` / `acceptance_complete` | `FAIL` / `false` |
| 行级复核 | `pass_with_two_kinds=933`、`pass_with_review=933`、`pass_without_review=0`、`pass_with_invalid_review=0`、`pass_with_row_specific_review=655` |
| 复核质量 | `review_validation_errors = []`（0）；`review_shared_check_groups = 20` 组（即 `warnings` 20 条，全部是「同一组非 ledger_row checks 被 5–6 行 PASS 共用，行级判别力不成立」） |
| 证据批次分布 | `44:1206, 45:402, 46:62, 47:23, 48:3, 49:24, 50:392, unlabeled:384` |
| 判读 | 933 PASS 是**行级验收面**，不是四门门值；`--require-complete` 仍会 exit 1（254 BLOCKED + 13 FAIL）。本批不得把台账升级读成门通过。 |

---

## 7. 冲突 / 风险登记（开工前认账，不擅自处理）

| # | 事项 | 一手依据 | 对本批的要求 |
|---|---|---|---|
| **C1** | **AUD-08「从未看过」的 acceptance 切片不存在** | `SCOPE-CORRECTION.md §2`；`split-golden/gate-report-b50.json#scope.appliesTo.goldenSha256=03b1eecc…`；金标 24 页 = dev 16 + acceptance 8 | G3 任务 23 开工前必须二选一并在证据里写明：**(a)** 新增页面 + 重新盲标成新切片（等于 v3，需登记标注性质与成本）；**(b)** 口径降级「第二次观测、held-out 不可恢复」，门值不外推。不得默认复用 v2 8 页称「从未看过」。 |
| **C2** | **3D 旧抽样框不可重建** | `d1/REPORT.md:20-24`（`sample_d1.py --check` → `match=false`/exit 2） | 任务 11 必须新快照 + 新 audit id；旧 `B50-D1-2026-11-08` 的 1600 样本只能作历史读数，不能作复算依据。 |
| **C3** | **3E 语义反转要改现存断言** | `StudyReviewPlanner3EScanInstrumentedTest.kt:383-388`（断言同日两次累计 >10）；`KnowledgeReviewQueueTest.kt:23`（注释「每次排计划」）；`StudyReviewPlannerService.kt:823/928` | 任务 17/18 改「每日累计 ≤10」时，把该断言**反向**为「同日累计 ≤10」并加跨日重置用例；这是口径变更需在记录中显式声明，不是放宽断言。文件当前 ` M` → 改动前查归属。 |
| **C4** | **`SmbkSelectionPlan` 注释与代码断言漂移** | `:202`（97/78）、`:210`（89）、`:324`（21）vs `:576`（99）、`:585`（91）、`:592`（24）；四方门 `trimOrder 91` PASS | 以代码/机器块/`75.json` 为准；**不要**按注释推算，也不要顺手改注释（超出本批范围）。 |
| **C5** | **门脚本未入库** | `git status --porcelain -- tools/figure_eval tools/split_golden` → `?? mva_gate.py`、`?? judge_channel_map.json`、`?? test_mva_gate.py`；` M figure_eval_gate.py`、` M check_split_gate.py` | 独立复核与「干净树复现」只能标注为「工作树工具、未入库」；若要把门值写成可复算，任务 43/44 需先把工具归属/入库裁定清楚（本阶段不动）。 |
| **C6** | **他线 Gradle 活动** | §4.1：PID 43348 客户端运行于 ui-green-pages worktree（`gradle.user.home=D:\smb-ui-forest-20261027\.gradle`，00:06:20 启动，CPU 持平） | 每阶段起 Gradle 前重查进程；**不并发**。main 树本会话无客户端。 |
| **C7** | **KB 在飞文件** | §2.1：`materials.jsonl`、`tools/kb_build/{merge_candidate_verdicts,new_content}.py`、`tools/kb_coverage/{judge_ledger,materialize,office_extract}.py` 均 ` M`；另有 2 个 `??` | 3D 只改 shipped pack + staging，不整文件覆盖上述文件；冲突即停项登记。 |
| **C8** | **热区残留物** | `feature/tutor/src/main/java/.../nul`（`??`）；6 个 `gradle.lockfile`（`??`）；根目录 ~350 散件 | 不提交 `nul`；不把散件混入批次证据；发布用 clone。 |

---

## 8. 本会话实跑清单（可复跑）

| # | 命令 | 退出码 / 关键输出 | 性质 |
|---|---|---|---|
| 1 | `git rev-parse HEAD` / `git rev-list --left-right --count main...origin/main` | `8dd29e96…` / `0	0` | 只读 |
| 2 | `timeout 25 git ls-remote origin refs/heads/main` | **143（Terminated，无输出）** | 只读；远端真实状态未验证 |
| 3 | `git status --porcelain \| wc -l` / `… -uall \| wc -l` | `619` / `3846`（153 M + 466 ??） | 只读 |
| 4 | `git diff --stat` | `153 files changed, 3858 insertions(+), 1425 deletions(-)` | 只读 |
| 5 | `python -I tools/ci/check_backup_plan_sync.py` | **EXIT=0**；`PASS 四方一致`；digest `8567faae…` | **计划点名，已实跑** |
| 6 | `python -I tools/ci/acceptance_report.py` | **EXIT=1**；`ok=false`；PASS 933 / FAIL 13 / BLOCKED 254 / UNVERIFIED 0 | 只读统计（未加 `--out`） |
| 7 | `C:\Android\Sdk\platform-tools\adb.exe devices -l` | 2 台 emulator（5590 / 5592） | 只读 |
| 8 | `adb -s <serial> emu avd name` + `getprop ro.build.version.sdk` | `ui_motion_20261107`/34、`ui_teaching_20261108`/34 | 只读 |
| 9 | `sed -E 's/=.*/=<MASKED>/' local.properties` | 4 个键名，值已掩码 | 只读，无密钥外泄 |
| 10 | `Get-CimInstance Win32_Process -Filter "name='java.exe'"` | 2×GradleDaemon（默认 home）+ 1×gradlew 客户端（他线 home） | 只读 |

**未跑（不得读成通过）**：任何 Gradle 任务 / 单元测试 / `am instrument` / 设备 UI / 安装 / 2h soak / R8 / release 构建 / 真实 Provider 调用 / VLM judge / `check_split_gate.py` / `mva_gate.py` / 四门任何重跑。本文件所有门值均为**读自批次 50 已归档证据与台账报告**，不是本会话复算。

> 读取方法备注：本机 Git Bash 下用 `cat` 看含中文的 JSON 会因 console codepage 显示为乱码（文件本身是合法 UTF-8，`3e/summary.json` 已用 `python -I` 复核 `全库薄弱点` 命中 1 次、`utf-8` 解码成功）。核对数字请用 `python -I -c` 解析，不要用 `cat` 下结论。

---

## 9. 给主会话的开场建议（不越权，仅排序）

1. **先裁 C1**（AUD-08 切片口径）：它是唯一会同时影响任务 22/23/24 与「完成定义」的前置；不定口径就跑 acceptance，等于把第二次观测记成 held-out。
2. **再确认 C6**（他线 Gradle）与 C7（KB 在飞），然后按计划 P0→R 走；3D 的 shipped pack 现 sha `10ede1bf…` 已记，改前/改后可比。
3. **3E（C3）与 3D（C2）互不依赖**，可先后串行；3E 先做 JVM 红测（同日两次累计 ≤10、跨日重置、预算先绑住不多取），再上设备。
4. G3 的提示词/口径工作（任务 20/21）可以在 C1 裁定前先做——它们不消费 acceptance。
5. 本阶段**零 Gradle、零设备写入**；下阶段起跑前按 §4.1 再查一次进程。
