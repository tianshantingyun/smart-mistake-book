# 批次 49 记录（2026-11-08）：掌握度 + 复习选题质量（A+B+C）

> 终态：**NOT ACCEPTED（验收不通过；发版不成立）**。台账 **PASS 900 / FAIL 22 / BLOCKED 278 / UNVERIFIED 0**（1200 行）；`acceptance_report` **exit 1**、`violations=[]`；`--require-complete` **exit 1**（`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED` 18 组）。与批次 48 的 898/23/279 相比：**F-055 FAIL→PASS、F-063 BLOCKED→PASS、F-057 维持 FAIL**；其余 verdict 未动。掌握阈值 **21 天 / 0.9 未改**；G6 **0/4** 与权重 **0/17** 臂均未过预注册门，`adopted_f=null`、`adopted=null`，**全部维持现值**；`ReviewPlannerV2.VERSION` 未 bump，三组金样未重生成。所有模拟结论 **model-based**（30 KC / 300 题 / 120 天合成世界），真库样本 **BLOCKED**。
> 计划：`.zcode/plans/plan-2026-11-08-mastery-review-quality.md`（18 项：P0 5 + P1 5 + P2 5 + P3 3；已完整读取）。
> 基线：`docs/research/2026-11-08-batch-49-baseline.md`（开工时 HEAD `ba7b5624`、595 脏路径；本记录时 HEAD `0515cfb0`）。
> 上一批终裁：`docs/research/2026-11-07-batch-48-record.md` §0/§9（NOT ACCEPTED；898/23/279）。
> 本批终裁（验收轨）：`docs/acceptance/adjudication.md` §「批次 49 报告重生成终裁」B49-0…B49-9。
> 本文件结构：§0 口径 → §1 18 项对账 → §2 各轨 → §3 门禁 exit → §4 设备 → §5 复核/修复 → §6 遗留 → §7 提交清单 → §8 回链。**本文件由本记录员收口；§1–§6 的事实逐条标出处，未运行项单列。**

---

## 0. 口径与声明

### 0.1 判定与证据口径

- **verdict 四值**：PASS=行判据在当前证据下成立；FAIL=判据不成立且缺陷已登记；BLOCKED=解除条件缺失（**不是通过**）；UNVERIFIED=本环境无法复核。本批未改任何阈值/判据/断言放宽；`F-057` 的“假 PASS”被回退为 FAIL（见 §5.1）。
- **model-based**：P1 的 G6/权重/掌握阈值读数全部来自 `SimulatedLearner` 的合成世界。对外表述必须带 `model-based`；不得当作真库或真机结论（`docs/research/2026-11-08-review-calibration-report.md:11、:324`）。
- **真库 BLOCKED**：本批零真库样本；真库 per-reason、G6 因果效应、真库 `fsrsBeatsBaseline`、参数可辨识性均为 **BLOCKED**（`calibration/run-manifest.json` `known_deviations`；报告 §8）。模拟侧上线门已补跑（885 样本、FSRS logLoss 0.1501 < baseline 0.3731、`beats=true`），但**不外推**到真库。
- **掌握阈值**：`MIN_STABILITY_DAYS=21.0`、`MIN_CURRENT_RETENTION=0.9` 未改（`AlgorithmConstants.kt:63/:66`；`git diff` 对该文件为空；sha256 `e695418f…cfd84` 与预注册 §1.1 基线一致）。
- **冲突 KC 默认仍可选**：`KnowledgeConflictCard` 的默认动作为 `CONTINUE_REVIEW`（「仍然复习」），卡不阻断取题/作答，0 必选（`KnowledgeConflictCard.kt:72-75`；`KnowledgeReviewSessionScreen.kt:240`）。
- **Gradle/设备纪律**：Gradle 只在门禁/实验阶段串行运行；**未运行** `connectedLocalFirstDebugAndroidTest`；设备一律经 `adb`（`C:/Android/Sdk/platform-tools/adb.exe`）。本记录员**本轮未跑任何 Gradle、instrumented 测试或 soak**（见 §0.3）。
- **热区归属**：`feature/review` 本批只碰开工时干净的 `KnowledgeReviewSessionScreen.kt`/`ViewModel.kt` 与新建文件；他线在飞的 `KnowledgeReviewSessionViewModelTest.kt`（SavedState 序列化改动）一字未改。`app` 本批只碰开工时干净的 `SchedulingSettingsScreen.kt`/`SecondaryScreens.kt`/`SmartMistakeBookRoot.kt`；`ReviewReminder*` 与 visualjs 六文件未改（`integration-check.md` §2）。`RoomStudyDatabase.kt` 的 16 行改动全部是 PR#13（T8）手工并集，**无本批内容、无新 DAO 方法**；`LearningDao.readReviewLogSamples` 仅改既有 SQL，签名未变（`integration-check.md` §2.3，本记录员复读 `LearningDao.kt:227-242`）。
- **证据落点**：`docs/research/evidence/2026-11-08-batch-49/`（271 项 `EVIDENCE.sha256`）与 `docs/acceptance/evidence/2026-11-08-batch-49/`（`REVIEWS.sha256` 923 项、`GATES.sha256` 10 项）。

### 0.2 台账基线迁移

| 量 | 批次 48 终态 | 批次 49 终态 | 变化 |
|---|---:|---:|---|
| PASS | 898 | **900** | +2（F-055、F-063） |
| FAIL | 23 | **22** | −1（F-055 转 PASS；F-057 维持 FAIL） |
| BLOCKED | 279 | **278** | −1（F-063 转 PASS） |
| UNVERIFIED | 0 | 0 | — |
| 合计 | 1200 | 1200 | 0 |
| ledger sha256 | `28e96a8d…76375` | **`264425f5…f6ba8`** | 本批冻结值（修复前 `d370ef8a…`） |

- 本记录员本轮 `awk` 复算台账：PASS 900 / FAIL 22 / BLOCKED 278（另 2 行表头、1 行分隔），与 `acceptance_report` 的 `counts` 一致。
- 域计数（本记录员按台账逐行复算；`00-index.md` §0 的 F 列写作 `58/0/32`，与台账不符，按台账更正为 `57/1/32`）：D0 100/0/0；A 65/0/25；B 88/0/2；C 88/0/2；D 82/0/8；E 81/0/9；F **57/1/32**；G 83/0/7；H 69/2/19；I 53/0/37；J 29/13/48；FIX 15/0/85；GATE 90/6/4。合计 PASS 900 / FAIL 22 / BLOCKED 278，与总表一致。

### 0.3 本记录员实跑 / 未跑（2026-11-08）

**实跑（本轮命令与读数）**：

- `git rev-parse HEAD` → `0515cfb087e0d12afbdff2c739c2993dce5adf99`（main）；`git status --porcelain=v1 | wc -l` → **1551**；同轮 `acceptance_report` 自记 `dirty_paths=4913`（工具内部 `-uall` 口径）。两者口径不同，本记录不混读。
- `python -I tools/ci/acceptance_init.py --check` → **exit 0**。
- `python -I tools/ci/acceptance_report.py --out build/b49-record/report.json` → **exit 1**；`state=FAIL`、`violations=[]`、`counts=900/22/278/0`、`pass_with_review=900`、`pass_with_invalid_review=0`、`pass_without_review=0`、`pass_with_row_specific_review=731`、`review_validation_errors=[]`。
- `python -I tools/ci/acceptance_report.py --require-complete --out build/b49-record/report-require-complete.json` → **exit 1**；`NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=22 BLOCKED=278)` + `REVIEW_CHECKS_SHARED`（18 组）。
- `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md --out build/b49-record/lint.json` → **exit 1**；`total=1200`、`violations=144`，全部 `EVIDENCE_REUSED`；**批次 49 新证据 0 条入告警**。
- `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md --out build/b49-record/trace.json` → **exit 0**；`total=1200`、`gaps=0`。
- runbook 覆盖（默认载体 `docs/acceptance/runbook.md`）→ **exit 1**、covered **88/278**；以 `docs/acceptance/blocked-runbook.md` 为载体（进程内替换常量，不改工具）→ **exit 0**、covered **278/278**、`missing=[]`。
- `sha256sum -c docs/research/evidence/2026-11-08-batch-49/EVIDENCE.sha256` → **exit 0**、**271/271 OK**；`REVIEWS.sha256` → **exit 0**、**923/923 OK**；`GATES.sha256`（在 `gate/` 目录内执行）→ **exit 0**、**10/10 OK**。
- `python -I -m pytest tools/tests/test_batch49_repair_invariants.py -q` → **4 passed**；`python -I -m pytest tools/tests/test_acceptance_lint.py -q` → **10 passed**。
- `adb devices -l` → `emulator-5566`、`emulator-5590` 两台在线（API 34 / x86_64）；本记录员只做只读查询。
- 解析 `targeted-jvm/*.xml`、`jvm/*.xml`、`p2-conflict/*.xml` 的 `tests/failures/errors/skipped`（读数见 §1/§2）。

**未跑（不得读成通过）**：任何 Gradle 任务（单测/强门/assemble）、任何 `am instrument`/设备 UI、2h soak、标定实验、`connectedLocalFirstDebugAndroidTest`。下文 Gradle/设备/soak 数字均标注为**归档值（读自证据文档/日志/XML）**。

### 0.4 本记录的证据边界

- 本记录是**收口记录**，不是第二主体复核：`922` 份行级复核全部为 `mechanical-recompute`、`independent_agent=false`（`00-index.md` §3；本记录员抽样确认 C-053/D-019/H-071/I-024 的 `run_id`/`ledger_batch=49`）。
- 修复环 prose 的“中项 7 类”与“7 条 GATE”是计数笔误：逐行表为 **1 高 + 6 中**；22 FAIL 中 GATE 为 **6 条**（`adjudication.md` B49-0/B49-2.3）。本记录以逐行表为准。
- `00-index.md` 头部“HEAD `ba7b5624` / 门禁 4898”是**索引冻结时点**叙述；终裁报告头为 `head=0515cfb0…`、`dirty=4908`（`adjudication.md` B49-9 证据口径 1）。本记录采用报告 JSON 与本轮 `git` 读数，并保留两处时点差异。
- 本记录员复核发现 `00-index.md` §0 的 F 域计数写作 `58/0/32`，按台账逐行复算为 `57/1/32`（F-057 是 FAIL）；总数 900/22/278 不受影响。已按台账更正（§0.2），不改任何 verdict。

---

## 1. 18 项对账

来源：计划 `.zcode/plans/plan-2026-11-08-mastery-review-quality.md`（P0 1–5、P1 6–10、P2 11–15、P3 16–18）。✓=完成并有证据在盘；✓*=完成但带如实保留/部分达成；✗=未执行。

| # | 项 | 结果 | 证据 |
|---:|---|---|---|
| 1 | P0 预算来源统一（preview/intake/plan 全走 `dailyReviewBudgetSeconds()`；`CONSTRUCTOR_ONLY` 仅夹具；store 胜出/null 回退两例） | ✓ | `StudyReviewPlannerService.kt:60/:117/:328/:383/:482`；`StudyReviewPlannerExamWindowTest.kt:467/:486`；ExamWindow `8/0`、CapacityPreview `4/0`（`targeted-jvm/01-*.xml`、`02-*.xml`） |
| 2 | P0 `review_log` 最近窗口（内层 DESC LIMIT、外层 ASC；fake `takeLast(limit)`；3 行 limit=2 断言） | ✓ | `LearningDao.kt:227-242`（签名未变）；`MasterySchedulingMigrationInstrumentedTest.kt:126-158`；设备红 `expected:<[2000, 3000]> but was:<[1000, 2000]>` → 绿 `4/4`（`review-log-window/b49-review-window-*.xml`、`.device.json`） |
| 3 | P0 校准口径过滤 `MODEL_JUDGED`/`REVEAL` | ✓ | `SchedulingEvaluation.kt:319` 过 `fittableReviewSamples`；`PlannedReasonCalibrationTest.kt:49` 新例；`4/0`（`targeted-jvm/04-*.xml`） |
| 4 | P0 fake `plannedReason` + `acceptance_lint` 检查列前缀 + E-061 文本 | ✓ | `RoomBackedStudyExperienceRepositoryTest.kt:3476`（`takeLast`）、`:3489`（`plannedReason`）；`acceptance_lint.py:110-116`；前缀样例 `3 violations / 1 non-violation`（`lint-prefix/prefix-sample.lint.json`）；真台账 144 不变、`UNVERIFIED_AS_PASS=0`；E-061 已改（`ledger.md:552`） |
| 5 | 25 份失效复核按当前 sha 重生成 | ✓ | `00-index.md` §2：C-053、D-019…026/036/044/080、H-009…071、I-024；本记录员抽验 C-053/D-019/H-071/I-024：`ledger_batch=49`、`review_kind=mechanical-recompute`、`run_id=…batch49-repair-line-review-*` |
| 6 | P1 新建 `SimulatedLearner`（30 KC/5 科/先修链 + 300 题；真值 FSRS + Bernoulli；逐日 planner→作答→记忆更新→review_log→指标） | ✓ | `core/domain/src/test/.../simulation/{SimulatedLearner,SimulatedLearnerTest,SimulationDeterminismContractTest,ReviewCalibrationExperimentTest}.kt`；`SimulatedLearnerTest` `6/0`（`jvm/TEST-…SimulatedLearnerTest.xml`）；同 seed 摘要 `3d64a84e…` 两轮一致（`p1-6-simulated-learner.md` §4） |
| 7 | P1 预注册（G6 {0,0.15,0.25,0.35,0.5}；权重坐标；目标/选择规则；10 训练 + 10 留出 seed；阈值只测不改） | ✓ | `2026-11-08-review-calibration-prereg.md` sha256 `042776e8…96b843`；与 `run-manifest.json.prereg_sha256`、报告 JSON `meta.prereg_sha256` 三方一致 |
| 8 | P1 报告 + JSON（留出集结果；选值理由） | ✓ | `2026-11-08-review-calibration-report.md`、`.json`；留出执行 id `batch49-holdout-20261107T1455Z`；G6/权重/阈值读数与 `decision` 块 |
| 9 | P1 行为变更（仅当预注册门通过） | ✓（N/A：未过门） | G6 `0/4`、权重 `0/17`；`g6-decisions.json` `adopted_f=null`、`verdict=KEEP_0.50…`；`weight-decisions.json` `passing=0`、`adopted=null`、`verdict=KEEP_ALL_CURRENT_WEIGHTS`；报告 JSON `constants_changed=false`、`version_bump=false`、`golden_regenerated=false` |
| 10 | P1 上线门 `evaluateSchedulingModels`（模拟+真库断言 `fsrsBeatsBaseline`） | ✓* 模拟侧 PASS；真库 **BLOCKED** | `SchedulingEvaluationOnSimulationTest` `1/0`；885 样本、FSRS 0.1501 < baseline 0.3731、`beats=true`（`repair/p1-10-evaluate-scheduling-models.md`）；真库零样本不填数 |
| 11 | P2 KD-67：`AdaptiveClarificationConsumer` + 确认卡 + 会话装配 + `KnowledgeQuizItemStore.record` 核验状态 + upgrader 生产调用 | ✓* 实现与 JVM/设备 UI 成立；真 Room e2e BLOCKED | `KnowledgeClarificationPresenter.kt`、`KnowledgeClarificationCard.kt`、`KnowledgeCalibrationDecision.kt`；`KnowledgeClarificationPresenterTest` `14/0`；`KnowledgeQuizItemStoreVerificationStatusTest` `2/0`；`KnowledgeQuizItemVerificationUpgraderTest` `15/0`；同批设备 UI 类整体绿 `8/8`（修复环后 9/9）；其中设备红→绿 2 例属冲突卡接线（P2-14，见 #14） |
| 12 | P2 校准可见页（21 维、最近拟合、per-reason、来源、G6/版本、立即重拟合/重置参数） | ✓* 页面/JVM/设备成立；真库读数 BLOCKED | `SchedulingCalibrationView.kt`、`SchedulingCalibrationSection.kt`；`SchedulingCalibrationViewBuilderTest` `7/0`、`SchedulingCalibrationCopyTest` `7/0`、`StudySchedulingCalibrationTest` `8/0`；设备 `8/8`；DataStore `4/4` |
| 13 | P2 投影冲突处置（`CONFLICTED` 专用状态 + 原因 + 「重新整理」；`reprojectAll()` 全量重放，不改 DAO） | ✓ | `StudyProjectionDrainer.kt:254` `reproject()`；`RoomBackedStudyExperienceRepository.kt:955` `reprojectAll()`；`StudyProjectionIssueBanner.kt`；`ProjectionConflictReprojectTest` `8/0`；设备投影卡用例绿 |
| 14 | P2 知识点冲突（冲突卡「先做校准题 / 仍然复习」；默认保持可选） | ✓* 卡与动作成立；第二道校准题未实现 | `KnowledgeConflictCard.kt:35-36/:72-75/:111/:125`；P2 证据 `KnowledgeConflictCardTest` `5/0`、`KnowledgeConflictSessionStateTest` `6/0`（post-fix 复跑 `6/0`、`10/0`）；`CALIBRATION_CHECK` 单独不弹卡；默认 `CONTINUE_REVIEW` |
| 15 | P2 验收行：11–14 各补 E/F/A 行 + 设备证据（含无障碍/截图） | ✓* 12 行原位升级；a11y 对比度 UNVERIFIED | `00-index.md` §1（A-064/E-009/F-045；A-019/E-080/F-055；A-067/E-044/F-057；A-021/E-081/F-063）；四屏 PNG + a11y 语义树；`a11y_audit` STRUCTURAL=PASS、CONTRAST=UNVERIFIED；`12-batch49-repair-artifacts.device.json` 17/17 |
| 16 | P3 定向/全量/强门；2h soak F-055/F-057；UI 设备用例 | ✓* 定向点名全绿、强门 0、soak 完成；**12 模块单测门 exit 1（9 红 HEAD 既存）**；F-057 维持 FAIL | `p0-closure.md` §2（ExamWindow 8/0、CapacityPreview 4/0、LongRange 2/0、PlannedReason 4/0）；`unit-gate.md`（4161/9 红/2 skip，exit 1）；`strong-gate.md`（7 任务 exit 0）；`postfix-gate.md`（强门 0；5 模块 2970/10 红）；`device/README.md` §6 |
| 17 | P3 `acceptance_report --require-complete`；算法相关 FAIL/BLOCKED 清零或说明 | ✓* 以“说明”收口；未清零 | 本轮 `require-complete` exit 1；22 FAIL = F-057 1 + H-018/H-062 2 + J 13 + GATE 6；278 BLOCKED；`adjudication.md` B49-3 逐组说明 |
| 18 | P3 25 份复核重生成；台账/终裁更新 | ✓ | 25 份重生成（§1 #5）；`ledger.md` sha `264425f5…`；`verdicts.md`、`domain-index.md` 已重生成；`adjudication.md` B49 终裁（签字块） |

**对账结论**：18/18 项执行；其中 **#10、#11、#12、#14、#15、#16、#17** 带如实保留（真库 BLOCKED、真 Room e2e BLOCKED、第二道题未实现、a11y 对比度 UNVERIFIED、单测门 exit 1、验收未收口），**0 项洗绿、0 项未执行**。计划 §验证中“定向 0 红（含 5 条原红点名绿）”达成；“模拟同 seed 逐位一致”达成；“12 模块单测门 + 强门 exit=0”**未达成**（强门 0，单测门 1）；“设备 2h soak/确认卡/校准页/冲突页/重整理”达成；“`acceptance_report` 新统计”达成；版本变更前后金样差异 **N/A**（未采纳任何变更，无需重生成）。

---

## 2. 各轨

| 轨 | 范围 | 终态 | 关键产物 | 如实缺口 |
|---|---|---|---|---|
| P0 断链修复（1–5） | 预算同源、review_log 最近窗口、校准口径、fake/lint/E-061、25 份复核 | 完成；5 条原红点名当前全绿（未重演红态） | `targeted-jvm/` 4 XML；`review-log-window/`；`lint-prefix/`；25 份行级 JSON | 5 条原红的“先红”只有基线静态推断，未在本批回退 PR#13 语义重演；`reviewLogSamples` fake 的 `takeLast` 无独立 JVM 断言（设备真 DAO 覆盖生产路径） |
| P1 模拟与标定（6–10） | 模拟世界、预注册、报告、门裁决、上线门 | 完成；**G6 0/4、权重 0/17 → 维持现值**；模拟上线门 PASS、真库 BLOCKED | `simulation/` 4 文件；预注册 + 报告 md/json；`calibration/`（含 `cross-process-rerun/`、`report-recompute/`）；`repair/p1-10-*` | 全部 **model-based**；真库/真机无样本；A 线 FSRS 拟合 NOT RUN；`avoidance` 不可测；参数可辨识性 `WORLD_TOO_SMALL_FOR_IDENTIFIABILITY` |
| P2-11 KD-67（11） | 澄清消费者、确认卡、核验写路径、生产装配 | 实现完成；JVM 14/0；设备 UI 8/8（修复后 9/9） | `KnowledgeClarificationPresenter.kt`、`KnowledgeClarificationCard.kt`、`KnowledgeCalibrationDecision.kt`；`KnowledgeQuizItemStore`/`Upgrader`；`SmartMistakeBookRoot` 接线 | 真 Room 端到端（账本写→CAS→下次装配 `contentVerified=true`）未跑；`IndependentVerification` 生产不可达；错题行候选仍 `contentVerified=false`；生产调用未传 `userRequestedQuestion=true`（F5） |
| P2-12 校准页（12） | 21 维、来源、最近拟合、per-reason、G6/版本、两个动作 | 完成；JVM 7/0 + 7/0 + 8/0；设备 8/8；DataStore 4/4 | `SchedulingCalibrationView.kt`、`SchedulingCalibrationSection.kt`、`SchedulingSettingsStore` 扩展、`SecondaryScreens`/`Root` 接线 | 真库读数 BLOCKED；对比度 UNVERIFIED；大字号未测；既有 `CapabilityScreenInstrumentedTest` 2 红在 HEAD 等价构建上同样存在（非本线引入）；`retentionFromSlider` 恢复为范围外修复（已登记） |
| P2-13/14 冲突处置（13–14） | 投影 `CONFLICTED`/`reprojectAll()`、知识点冲突卡 | 完成；JVM 8/0 + 5/0 + 6/0 + 23/0 + 3/0；设备用例绿；F-063 PASS | `StudyProjectionDrainer`、`StudySnapshotBuilder`、`StudyProjectionIssueBanner`、`KnowledgeConflictCard`、会话 VM 状态机 | 冲突队列真实来源未在设备跑（JVM 覆盖）；「先做校准题」第二道题未实现（候选池/单槽持久化限制）；对比度 UNVERIFIED |
| P2-15 验收行（15） | 12 行原位升级 + 设备/无障碍/截图 | 完成；A-019/A-021/A-064/A-067/E-009/E-044/E-080/E-081/F-045 PASS；F-055/F-063 转 PASS；F-057 维持 FAIL | `00-index.md` §1；`device/screenshots/` 4 PNG；`device/a11y/` 4 语义树；`12-batch49-repair-artifacts.device.json` | 12 行是机械复核 + JVM/设备 UI 证据，不等于完整产品闭环；a11y 仅结构检查 |
| P3 门禁/soak/台账（16–18） | 12 模块单测、强门、2h soak、验收报告、复核重生成 | 单测门 exit 1（9 红 HEAD）；强门 0；soak 7200s/117 轮；验收 exit 1；台账 900/22/278 | `unit-gate.md`、`strong-gate.md`、`postfix-gate.md`、`device/soak/`、`gate/`、`REVIEWS.sha256` | 单测门判据未达成；22 FAIL/278 BLOCKED 未清；F-057 未命中超限路径；PSS +49 MB 未归因 |
| 修复环 | F-057 假 PASS 回退、soak 退出码、设备绑定、决策消费、哈希漂移、P1-10、a11y | 高 1 + 中 6 已修/登记；4 个阻塞保留 | `repair/README.md`、`repair/red-green.log.md`、`repair/unit-gate-and-kd67-blockers.md`、`postfix-gate.md` | 12 模块单测门、acceptance 终裁门、KD-67 真 Room e2e、第二道校准题仍阻塞 |
| 集成静态检查 | 改动枚举、包名/import/依赖/资源、热区归属、提交清单 | 完成；静态检查通过；发现 1 个应剔除文件 + 若干登记项 | `integration-check.md`、`commit-manifest-add.txt` | 未跑 Gradle/设备/门禁；manifest 已随修复环/验收轨变旧（§7） |

### 2.1 P1 标定结果要点（model-based）

- **G6 幅度**：4 臂全部未过门；`f=0.15/0.25` 为 `UNDERPOWERED`（等时合格 world n=6<8），`f=0.35` 中位 Δ +0.5 KC < 1 KC，`f=0.50` 符号一致 5/8<8/10 且为网格端点。`no_equal_time_cells=22`（G6）。
- **权重**：17 臂全部未过门；`passing=0`；最好臂 `W/due/x1.25` 中位 +2.5 KC 但符号 6/8、护栏不过且为端点；`W/weak/x1.25` 7/10 同号仍不足。`avoidance` 不可测。
- **机制发现**：`MAX_BEAM_STEPS=20`（`ReviewPlannerV2.kt:192`，常量 `:1133`）使每日选题步数封顶；所有臂每个 world 的 `max_daily_items` 中位恰为 20，典型 world 22–31 天顶格——G6 扩容不转化为更多选题步数，`f` 效应被机械压平（报告 §6.1）。
- **护栏结构性不可达**：G2（非考试科不挨饿）在 6 KC/科的离散世界里等价于“一个 KC 都不能少”，所有 21 臂至少违反一条 G2/G3；报告 §6.2 明确“这不是权重没用，是门的设计把可裁决区间压成空集”，并明确**本轮不得事后改规则**。
- **掌握阈值读数**：T0(21,0.90)/T1(14,0.90)/T2(21,0.85) 三组只读；`change_proposed=false`；分母仅 1–2 个 KC，保持率会被单点翻转；不得当作阈值安全证明（报告 §3.3、§8）。
- **变更纪律兑现**：本批未采纳任何非默认 G6/权重值，预注册 §11 的 bundle（VERSION bump + 三组金样重生成 + 集成回归）**未触发**。`ReviewPlannerV2.kt` 的字节变化来自注入 seam，`ReviewScoringWeightsSeamTest` 3/0 与金样 `2/0`（`golden selection sequences stay bit identical`）证明默认行为未变；若后续把该 seam 视为行为变更，必须按硬约束一次性完成 bundle——本批不半途启动。

### 2.2 先红后绿证据强度（如实）

| 面 | 红相 | 绿相 | 评价 |
|---|---|---|---|
| P0-2 review_log 窗口 | 真设备：旧 SQL 返回 `[1000,2000]`，`failures=1`（`b49-review-window-red-old-sql.xml`） | 修复后 `4/4`（`b49-review-window-green-final.xml`） | **强**：同一用例、同一设备、同数据 |
| P0-1 预算 / P0-3 校准口径 | 未捕获失败日志（修复随测试同批落地） | ExamWindow 8/0、CapacityPreview 4/0、PlannedReason 4/0 | **中**：只有绿；基线 §4 的红态是静态推断，未重演 |
| P1 模拟器 | 编译红：`Unresolved reference 'metrics'` / 类型推断失败 | 6/0 | **弱-中**：红是“类不存在”，行为失败由 6 条断言各自钉住 |
| P2-11 presenter | 编译红：`Unresolved reference 'KnowledgeClarificationPresenter'` | 14/0 | **中**：编译红，非行为红 |
| P2-12 校准页 builder | 首版“只实现能编译路径”4/7 红 | 7/0 | **强**：行为红 |
| P2-12 copy test | 仅编译红（`Unresolved reference 'retentionFromSlider'`，被既有编译断裂挡在更早一层） | 7/0 | **弱**：如实登记无行为红 |
| P2-13 冲突 | `calibrationIntentRoutesThroughTheCalibrationCorridor` 1/7 红 → 显式 `userRequestedQuestion=true` 后 7/7；`StudyProjectionIssueCopyTest` 1 红 → 改文案 3/3 | 8/0、3/0 | **强**（前者修正参数而非放宽断言） |
| P2-14 冲突卡 | 设备用例 4、5 红（共 8 例）：点「仍然复习」/「先做校准题」后卡不消失；1 行 `showConflictCard` 接线修复 | 8/8（修复后整类 9/9） | **强**：抓到真实断链 |
| 修复环不变量 | 旧夹具 4 failed（`repair/red-before-replay.txt`） | 新树 4 passed（本轮复跑） | **强**（Python 不变量） |
| 修复环文案 | 旧文案「校准题只多问一道」→ `KnowledgeConflictCardTest` FAILED | 恢复真实后果文案 6/0 | **强** |

> 未捕获行为红的项目已在证据文档中如实登记（`kd67-clarification-closure.md` §3.1、`p2-conflict-handling.md` §3.3、`calibration-page/README.md` §3.2），不以编译红冒充行为红。

---

## 3. 门禁 exit

**归档值**=执行件当轮实跑并落在证据文档/日志中的读数；**本轮复跑**=本记录员 2026-11-08 在当前树上实际执行（输出到 gitignored `build/b49-record/`）。未跑项单列。

| 门 | 命令（逐字或出处） | exit | 关键值 | 轮次 |
|---|---|---:|---|---|
| 结构自测 | `python -I tools/ci/acceptance_init.py --check` | **0** | 无输出 | 本轮复跑 |
| 验收报告 | `python -I tools/ci/acceptance_report.py --out build/b49-record/report.json` | **1** | 900/22/278/0；`violations=[]`；`pass_with_review=900`、invalid=0、without=0、row_specific=731；head `0515cfb0`、dirty 4913 | 本轮复跑 |
| 终裁门 | `python -I tools/ci/acceptance_report.py --require-complete --out build/b49-record/report-require-complete.json` | **1** | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=22 BLOCKED=278)` + `REVIEW_CHECKS_SHARED` 18 组 | 本轮复跑 |
| lint | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md --out build/b49-record/lint.json` | **1** | total 1200；violations 144，全部 `EVIDENCE_REUSED`（批次 44–48 既有；批次 49 新证据 0 条） | 本轮复跑 |
| lint 诊断 | 同上 `--warn-only`（归档） | **0** | violations 0、warnings 144 | 归档（`gate/lint-warn-only.json`） |
| 追溯 | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md --out build/b49-record/trace.json` | **0** | total 1200、gaps 0 | 本轮复跑 |
| runbook 覆盖（默认载体） | `python -I tools/ci/check_blocked_runbook_coverage.py --out build/b49-record/coverage-runbook.json` | **1** | 88/278（工具-计划载体错位，批次 47 §6.4 已登记） | 本轮复跑 |
| runbook 覆盖（计划载体） | 同脚本进程内替换 `RUNBOOK=docs/acceptance/blocked-runbook.md` | **0** | **278/278**、missing=[] | 本轮复跑 |
| 研究证据哈希 | `sha256sum -c docs/research/evidence/2026-11-08-batch-49/EVIDENCE.sha256` | **0** | **271/271 OK** | 本轮复跑 |
| 行级复核哈希 | `sha256sum -c docs/acceptance/evidence/2026-11-08-batch-49/REVIEWS.sha256` | **0** | **923/923 OK**（922 行 JSON + schema） | 本轮复跑 |
| 门禁证据哈希 | 在 `gate/` 内 `sha256sum -c GATES.sha256` | **0** | **10/10 OK** | 本轮复跑 |
| 修复环不变量 | `python -I -m pytest tools/tests/test_batch49_repair_invariants.py -q` | **0** | 4 passed（旧夹具 4 failed，见 `repair/red-before-replay.txt`） | 本轮复跑 |
| lint 工具测试 | `python -I -m pytest tools/tests/test_acceptance_lint.py -q` | **0** | 10 passed（但**前缀规则无新增用例**，见 §6） | 本轮复跑 |
| 12 模块单测门 | `./gradlew :core:model:…:feature:profile:testDebugUnitTest --continue --console=plain` | **1** | 4161 tests / **9 fail** / 0 err / 2 skip；9 红均 HEAD 既存他线语义 | 归档（`unit-gate.md`） |
| 单测门（修复环复跑） | 同一 12 模块命令 | **1** | 4165 tests / **9 fail** / 2 skip；`feature:review` 106/0、`core:domain` 741/0 | 归档（`repair/unit-gate-after.log`） |
| 修复足迹 5 模块 | `:core:domain :core:data :feature:review :app:testLocalFirstDebugUnitTest` + `:core:database` | **1** | 2970 tests / **10 fail** / 2 skip；10 红均 HEAD 既存（含 app 2 红） | 归档（`postfix-gate.md` §2） |
| 强门（批内） | `bash build/run-batch49-strong.sh` | **0** | 7/7 任务 exit 0；仅编译/打包 | 归档（`strong-gate.md`） |
| 强门（post-fix） | `bash build/run-batch49-strong-postfix.sh` | **0** | 7/7 任务 exit 0；`^e:` 0 | 归档（`postfix-gate.md` §1） |
| 定向 JVM（P0 四类） | `:core:data:testDebugUnitTest --tests …`（3 类）+ `:core:domain:test --tests …` | **0/0/0/0** | ExamWindow 8/0、CapacityPreview 4/0、LongRange 2/0、PlannedReason 4/0 | 归档（`p0-closure.md` §2；XML 已复读） |
| P1 模拟/上线门 | `:core:domain:test --tests …simulation.*` 等 | **0** | SimulatedLearner 6/0；`SchedulingEvaluationOnSimulationTest` 1/0 | 归档（`p1-6-*`、`repair/p1-10-*`） |
| P2 JVM | 各定向类（见 §1 #11–#14） | **0** | presenter 14/0；conflict 8/0/5/0/6/0/23/0/3/0；calibration 7/0/7/0/8/0 | 归档（`p2-conflict/*.xml`、`jvm/*.xml`） |
| 设备构建 | `:app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest :core:database:assembleDebugAndroidTest` | **0** | `BUILD SUCCESSFUL in 28s`；`:app:assembleDebugAndroidTest` 因 flavor 歧义不可用（如实登记） | 归档（`device/01-build-red-phase.log`） |
| 设备 UI（主绿） | `am instrument -e class Batch49ReviewCardsAndReprojectInstrumentedTest …` | **0** | `OK (8 tests)`（修复后 9/9，APK `e19f1b22…`） | 归档（`device/06-*`、`device/12-*`） |
| 设备校准页 | `am instrument -e class SchedulingCalibrationSectionInstrumentedTest …` | **0** | `OK (8 tests)`；DataStore `OK (4 tests)` | 归档（`device/07-*`、`calibration-page/09-*`） |
| 设备 review_log 窗口 | `am instrument -e class …MasterySchedulingMigrationInstrumentedTest#recentReviewLogWindow…` | **0** | 点名 1/1；同类 4/4 | 归档（`device/08/09-*`） |
| 2h soak | `bash soak/f-soak.sh emulator-5590 7200 soak` | **无有效 exit 证据**（旧脚本字面量 `EXIT=0`） | 7200s、117 轮、同 pid 28350、0 新崩溃/重启；完成性由 CSV 判 | 归档（`device/soak/`；`EXIT-CAPTURE-CAVEAT.md`） |
| **未跑（本记录员）** | 任何 Gradle / `am instrument` / soak / `connectedLocalFirstDebugAndroidTest` | — | — | 如实 |

**门禁结论**：`acceptance_report` 与 `--require-complete` 的 exit 1 是**批次级未收口**（22 FAIL/278 BLOCKED），不是本次证据链违规（`violations=[]`、`pass_with_invalid_review=0`）；12 模块单测门 exit 1 是真实未达成，9 红全部为 HEAD 既存他线语义，但本批**不把“归因他线”改记为通过**。强门 exit 0 只证明编译/打包，不执行测试。

---

## 4. 设备

### 4.1 设备与环境（归档 + 本记录员只读复核）

| 项 | 值 | 出处 |
|---|---|---|
| 设备 | `emulator-5566`、`emulator-5590`；`sdk_gphone64_x86_64`；API 34；x86_64 | `device/00-snapshot-and-device-meta.txt`；本轮 `adb devices -l` 两台均在线 |
| app APK（主设备绿跑/soak） | sha256 `ebb3aa340f071bb309edb1420220a9530e9734c1f34993fa91d5b8abd2244cee`（190,611,130 B） | `device/README.md` §4；设备侧 `pm path`+`adb pull` 回读一致 |
| app APK（修复环） | sha256 `e19f1b22c58299db2ffb3be0df7284288d91a0ce7e42f0e9eb95eb06fd5fce51` | `device/12-batch49-repair-artifacts.device.json`；`postfix-gate.md` §1 |
| test APK（主） | sha256 `8629a05dbc7883c33ea9018746cc750d113fba8ac9e5d717a622660aead4d6ea` | `device/README.md` §4 |
| test APK（修复环） | sha256 `0252bc246948aead52f04821d97eb8656bbf470615f8f112731d74209d471609` | `device/12-batch49-repair-artifacts.device.json` |
| core:database test APK | sha256 `2c3c1913a1885aacbc0f56554d7108c3f2ef9995e3a705016da2fb620e59f926` | `device/README.md` §4 |
| 构建/安装 | 三次 `install -r -t` 均 `Success`；**未用** `connectedLocalFirstDebugAndroidTest` | `device/README.md` §3 |

### 4.2 设备结果

| 面 | 结果 | 证据 |
|---|---|---|
| 确认卡/冲突卡/投影重整理（新增 8 例） | 红 `Tests run: 8, Failures: 2`（用例 4、5：点动作后 `knowledge_conflict_card` 仍在）→ 1 行 `viewModel.showConflictCard` 接线 → 绿 `OK (8 tests)`；修复环后 `OK (9 tests)` | `device/04-instrument-red-phase-2.log`、`06-instrument-green-phase.log`、`12-batch49-repair-artifacts.device.json` |
| 校准可见页 | `OK (8 tests)`；随后修复环 8/8（APK `e19f1b22…`） | `device/07-instrument-calibration-page.log`；`12-*.device.json` |
| DataStore 偏好往返 | `OK (4 tests)` | `calibration-page/12-data-device-run.txt` |
| review_log 最近窗口 | 点名 `OK (1 test)`；同类 `OK (4 tests)` | `device/08/09-*.log`；`review-log-window/*.device.json` |
| 既有会话屏回归 | 4/4（修 1 行后） | `device/11-instrument-session-regression.log` |
| feature/review 单测回归 | 101/0（修复环后 106/0） | `device/10-feature-review-unit-regression.log`、`postfix-gate.md` |
| 2h soak | 7200s、117 轮（35,100 monkey events）、**同一 pid 28350**、`monkey_rc!=0` 0 轮、`crash_lines` 19→19、`dropbox` 2→2 | `device/soak/soak-summary.txt`、`soak-samples.csv`；`device/README.md` §6 |
| PSS | total min 176,669 / max 242,118 kB；末轮 225,466（约 +49 MB，未归因） | 同上 |
| 截图/无障碍 | 4 屏 PNG + UiAutomation 语义树；`a11y_audit` **STRUCTURAL=PASS**、**CONTRAST=UNVERIFIED**（无颜色输入） | `device/screenshots/`、`device/a11y/`；`repair/README.md` §1 #7 |

### 4.3 设备边界（如实）

1. **KD-67 app 级真实链路未跑**：设备用例从 `KnowledgeReviewSessionScreen` 公开入口注入 `clarificationState`，未走 `SmartMistakeBookRoot` 的真实 selector→presenter→状态流与真 Room 确认/升级（`repair/unit-gate-and-kd67-blockers.md` §3）。
2. **冲突队列真实来源未在设备跑**：用例用 `reasons={CONFLICTED_KNOWLEDGE}` 构造；planner 打分→reasons→队列的设备级链路未跑（JVM 有覆盖）。
3. **设备只覆盖 x86_64 模拟器 API 34**；不外推 arm64/真机。
4. **soak 退出码证据缺口**：历史 `soak.cmd.log` 的 `EXIT=0` 是旧脚本无条件字面量（修复前 `f-soak.sh:56`），**不作退出证明**；F-055/F-063 的 `cmd:` 已改 `file:`，完成性由 CSV 逐轮数据判（`device/soak/EXIT-CAPTURE-CAVEAT.md`）。修复后的 `f-soak.sh` 未再做一次 7200s 实跑，真实 `EXIT` 证据留待下次 soak。
5. **两组 APK 不可混读**：主绿跑/soak 用 `ebb3aa34…`；修复环截图/a11y/17 例用 `e19f1b22…`。

---

## 5. 复核/修复

### 5.1 修复环 findings 与处置（高 1 + 中 6）

| # | 级别 | finding | 处置 | 复证 |
|---|---|---|---|---|
| 1 | **高** | F-057 判据被反向改写后记 PASS（“未命中超限路径”被当成通过） | **回退为 FAIL**：恢复 HEAD 正向判据（2h 内至少 1 次 FATAL/超限路径命中）；证据恢复单测 XML；922 份复核按新 sha 重绑；计数 901/21→900/22 | `ledger.md:638`、`rows/F-057.json`、`verdicts.md:12`；`repair/README.md` §1 #1；本记录员读 `rows/F-057.json` `verdict=FAIL`、`run_id=…batch49-repair-f057` |
| 2 | 中 | `f-soak.sh` 无条件写 `EXIT=0`；F-055/057/063 的 `cmd_exit` 是空证据 | 脚本改为按 run 结果计算 `OUTCOME`/`EXIT` 并以真实状态退出；三行 `cmd:`→`file:`；新增 caveat | `device/soak/f-soak.sh`、`EXIT-CAPTURE-CAVEAT.md`；`repair/red-green.log.md` §2（旧夹具 4 failed → 新树 4 passed，本轮复跑） |
| 3 | 中 | 06/07 侧车声称 serial/APK 由 instrument 日志逐字转写 | 改为 `binding_provenance`：counts 来自日志；serial/APK 来自 `00-snapshot` 构建/安装记录与时间线；`source_log_sha256` 只绑定 counts | `device/06-*.device.json`、`07-*.device.json`；`00-index.md` §1 证据补件说明 |
| 4 | 中 | 「先做校准题」不兑现、决策被丢弃、文案承诺多问一道 | Root 返回决策并由会话状态机持久化/显示（ASK_CALIBRATION/NO_SAFE/失败三种文案）；卡片删除「多问一道」承诺；JVM 5 例 + 设备 2 例 | `KnowledgeCalibrationDecision.kt`、`KnowledgeReviewSessionViewModel.kt`、`KnowledgeReviewSessionScreen.kt`、`SmartMistakeBookRoot.kt:1405`；`repair/red-green.log.md` §1/§3 |
| 5 | 中 | `StudyReviewPlannerService.kt` / driver 哈希漂移 | manifest/报告 JSON 增加 `post_report_drift`；报告 §1.2/§8/§10.3 与 p1-9 文档更正；最终 driver 重跑 9 份产物逐位相同 | `calibration/run-manifest.json`、`2026-11-08-review-calibration-report.json`、`repair/calibration-driver-drift.md`；终裁 `cmp` 9/9 IDENTICAL |
| 6 | 中 | P1-10 `evaluateSchedulingModels` 未跑 | 新增 `SchedulingEvaluationOnSimulationTest`（10 dev world 合并），生产评估器断言 `fsrsBeatsBaseline`：885 样本、0.1501 < 0.3731、`beats=true` | `repair/p1-10-evaluate-scheduling-models.md`、`repair/TEST-…SchedulingEvaluationOnSimulationTest.xml` |
| 7 | 中 | P2-15 缺无障碍/截图 | 四屏 PNG + UiAutomation 语义树；`a11y_audit` STRUCTURAL=PASS、CONTRAST=UNVERIFIED；设备 17/17 绿（新 APK 绑定） | `device/screenshots/`、`device/a11y/`、`device/12-batch49-repair-artifacts.device.json` |

> 修复环 prose 写“中项 7 类”；逐行表为 1 高 + 6 中。以表为准（`adjudication.md` B49-2.3）。

### 5.2 行级复核链与 25 份重生成

- **922 份** `docs/acceptance/verdicts/rows/*.json` 全部绑当前台账 sha `264425f5…`；`REVIEWS.sha256` 923/923 通过；900 个 PASS 行 `pass_without_review=0`、`pass_with_invalid_review=0`、`review_validation_errors=[]`。
- **25 份失效复核**按当前 sha 重生成：C-053、D-019…026/036/044/080、H-009/H-014/H-015/H-021/H-026/H-027/H-044/H-052/H-057/H-066/H-070/H-071、I-024；只重算 `ledger_sha256`/`ledger_row`/`evidence_sha256`，**未改 verdict/证据路径/语义判据**（`00-index.md` §2）。
- **复核质量残余**：34 份复核的 `basis` 文本仍写修复前台账 sha `d370ef8a…`（25 份重生成 + 9 份本批行 A-019/A-021/A-064/A-067/E-009/E-044/E-080/E-081/F-045）；结构化字段已全部重绑，`acceptance_report` 0 校验错误，但审计叙述与当前台账不一致（`adjudication.md` B49-2.6）。
- **shared checks**：18 组 ≥5 行共用（119 行），另 19 组 2–4 行（50 行）；`pass_with_row_specific_review=731/900`。18 组与批次 48 同源（D0/GATE 门面），本批未新增。

### 5.3 集成静态检查发现（当前状态）

| # | 级别 | 发现 | 当前状态 |
|---|---|---|---|
| F1 | 中（提交前必处理） | `docs/2026-11-08-review-calibration-report.json` 是 GBK 旧副本（37,435 B），UTF-8 解码失败；与 `docs/research/...-report.json` 内容不同 | **仍存在**（本轮实测 `utf-8` 解码失败：`invalid start byte`）；不得提交，建议删除或重编码 |
| F2 | 中（证据↔树漂移） | `StudyReviewPlannerService.kt` 报告后被 P2-14 改 1 行（`reasons = scored.reasons`），与 manifest 不符 | 已登记 `post_report_drift`；G6/权重/金样只依赖 `ReviewPlannerV2.kt`，裁决不受影响 |
| F3 | 信息（已缓解） | 证据树夹带 `.mimosa/**` hook 状态文件 | `.gitignore:29` 已忽略；`git add --dry-run` 0 命中；manifest 有 `:(exclude)` 双保险 |
| F4 | 低 | `acceptance_lint` 新前缀规则无自动化回归用例（`test_acceptance_lint.py` 仅改 3 行编码参数） | **仍开放**；本记录员跑 10/10 绿，但该文件无前缀规则用例 |
| F5 | 低（跨线观察） | KD-67 生产调用 `selectKnowledgeReviewQuestion` 不传 `userRequestedQuestion=true`，选择器先返回 `QUESTION_NOT_REQUESTED`；确认卡仍因 `noCandidateDecision` 出现，但 ASK 分支永不执行 | 已登记，未修（属 KD-67 线裁定） |
| F6 | 低（证据过时） | `kd67-clarification-closure.md:155` 记录 app 单测编译失败（`retentionFromSlider`），当前树已由 P2-12 恢复 | 静态看已不成立；本记录未重跑该编译 |
| F7 | 低 | 证据树缺整体 `DIR-SHA256.txt`（批次 44/48 惯例） | 现有 `EVIDENCE.sha256` 271 项 + `REVIEWS.sha256` 923 项 + `GATES.sha256` 10 项；整体目录清单仍缺 |
| F8 | 信息（证据过时） | `p1-9-keep-current-values.md:163` 称全树构建因 `RoutingEvalDeviceExportInstrumentedTest.kt:73` 语法错误 FAIL；该文件当前不存在 | 结论不可复现；以当前门禁为准 |
| F9 | 信息 | `core:data` 全量 1690 tests / 4 failed（P2 证据） | 与 12 模块门同源 4 红；未清零 |
| F10 | 信息（既有惯例） | 证据树当前 **153 个 `.log`**（集成检查时点 22，后续轨道新增）被 `.gitignore:31` 的 `*.log` 忽略，原始日志不入库 | **仍开放**；复核者拿不到原始日志，只能读摘要/XML；`EVIDENCE.sha256` 仍覆盖这些文件 |

### 5.4 复核边界（如实）

1. 922 份行级复核是同会话脚本的**机械复算**，不是第二人/第二模型语义复核（`independent_agent=false`）。
2. 本记录员未重跑 Gradle/设备/soak；§3/§4 的归档值来自证据文档、日志与 XML，不是本轮实测。
3. 金标/人工语义复核不在本批范围；AUD-08 切分门仍 FAIL（① 10/24=0.4167<0.9、切残 3≠0），不属复习/掌握算法链但同为 FAIL。
4. 证据树 `.log` 不入库、`.mimosa` 运行态文件混入（已忽略）→ 复现者需按 `EVIDENCE.sha256`/`REVIEWS.sha256`/`GATES.sha256` 与文档内命令复跑。

---

## 6. 遗留（全部如实，含 BLOCKED/UNVERIFIED）

1. **验收未通过**：`acceptance_report --require-complete` exit 1；22 FAIL/278 BLOCKED 未消除；不得签“批次 49 验收通过/发版就绪”。
2. **12 模块单测门 exit 1**：4165 例 / 9 红（`core:database` 4、`core:data` 4、`feature:tutor` 1），全部 HEAD 既存他线语义；修复环 5 模块复跑 2970/10 红（新增 app 2 红）。本批面 `feature:review` 106/0、`core:domain` 741/0；门判据未达成。
3. **F-057 维持 FAIL**：2h soak 117 轮未命中“超限路径”，不能把未命中记成通过；批次 47 根因修复（`587098f1`）不等于超限路径验证完成。
4. **F-056 BLOCKED / PSS +49 MB 未归因**：total PSS 176,669→242,118 kB（末轮 225,466），不据此宣称内存无问题。
5. **KD-67 真 Room e2e BLOCKED**：真实 selector→presenter→状态流 + 真 Room 确认/升级未跑；`IndependentVerification` 生产不可达；错题行候选仍 `contentVerified=false`。
6. **第二道校准题未实现**：候选池只有本会话已落库的 FOUNDATION 题，`ASK_CALIBRATION` 无 CALIBRATION 候选题面；`(会话,知识点)` 单槽持久化不允许第二道题；超出本批热区与“不新增 DAO”约束。
7. **冲突 planner→队列设备链未跑**：设备用例用构造的 `reasons`；JVM 有覆盖。
8. **真库样本 BLOCKED**：真库 per-reason/G6 因果、真库 `fsrsBeatsBaseline`、参数可辨识性均零样本；所有标定数字 **model-based**。
9. **arm64/真机未覆盖**：设备证据仅 x86_64 模拟器 API 34；TalkBack/IME/对比度未做人工签字；a11y `CONTRAST=UNVERIFIED`。
10. **G6/权重门结构性不可达**：21/21 臂至少违反 G2/G3；`MAX_BEAM_STEPS=20` 封顶压平 G6 效应；下一轮需新预注册 + 扩网格 + 提功效，不能在旧 holdout 上重裁。
11. **holdout 二次执行与 CSV 精度**：`cross-process-rerun/` 用 holdout seed 901 重跑（digest 全等、无臂过门、未用于调参）；`holdout_run_id` 两次相同不能证明单次执行；CSV 6 位小数导致 2 个臂符号计数差 1（均远低于门 8）。已登记，旧 holdout 是否退役由下批预注册定。
12. **复核叙述残余**：34 份 `basis` 仍引用旧台账 sha `d370ef8a…`；shared checks 18 组（119 行）+ 19 组（50 行）；731/900 行级特有。
13. **lint 144 `EVIDENCE_REUSED`**：全部批次 44–48 既有复用，批次 49 新证据 0 条；未清零。
14. **`d0_self_acceptance --group all` 本批未跑**（`verdicts.md` §0）。
15. **`docs/2026-11-08-review-calibration-report.json` GBK 非法 UTF-8 旧副本仍在**；不得提交（integration F1）。
16. **证据 `.log` 不入库**：当前 153 个 `.log` 被 `*.log` 忽略（集成检查时点 22，后续轨道新增）；若验收要求原始日志入库，需 owner 决定 `git add -f` 或改 `.gitignore`（integration F10）。
17. **提交面未落地**：截至本记录，批次 49 全部改动仍在工作树（HEAD `0515cfb0`，无批次 49 提交）；`commit-manifest-add.txt`（57 行 → 展开 126 路径）是**集成检查时点快照**，已缺后续新增文件（见 §7）。
18. **范围外修复需主会话复核**：`SchedulingSettingsScreen.kt` 的 `retentionFromSlider` 恢复是恢复 `587098f1` 既有修复，不是产品决策；`calibration-page/README.md` §5.2 已登记，需主会话确认保留。

---

## 7. 提交清单

**范围声明**：以下仅为批次 49 产物。工作树另有他线改动（知识线 `core/database`/`core/model`/kb 工具、ReviewReminder/visualjs、`feature/library/build.gradle.kts`、`.gitignore` 等）**不在本清单**；提交时严禁 `git add -A` / `git add .`。`local.properties`（gitignored）、`build/b49-*`（gitignored）、14 个未跟踪 `gradle.lockfile`（KD-72：生成于他线未提交构建改动，不得原样入库）均不入库。

> **状态时点**：HEAD `0515cfb087e0d12afbdff2c739c2993dce5adf99`（main），批次 49 **尚未提交**；下表 `M`=已跟踪修改，`??`=未跟踪新增。`docs/research/evidence/2026-11-08-batch-49/commit-manifest-add.txt` 是集成检查时点的机器清单（57 行 / 展开 126 路径），**已变旧**：至少缺 `app/src/androidTest/.../Batch49ReviewCardsAndReprojectInstrumentedTest.kt`、`feature/review/.../KnowledgeCalibrationDecision.kt`，以及验收轨的行级 JSON/报告/证据目录与 `2026-11-08-batch-49-record.md`。提交时以显式路径重列，不以该 manifest 为唯一依据。

| 块 | 路径 | 状态 | 说明 |
|---|---|---|---|
| 1 P0 断链 | `core/database/.../dao/LearningDao.kt`；`core/data/.../study/StudyReviewPlannerService.kt`；`core/data/src/test/.../study/{StudyReviewPlannerExamWindowTest,StudyReviewPlannerServiceCapacityPreviewTest,RoomBackedStudyExperienceRepositoryTest}.kt`；`core/domain/.../SchedulingEvaluation.kt`；`core/domain/src/test/.../PlannedReasonCalibrationTest.kt`；`core/database/src/androidTest/.../MasterySchedulingMigrationInstrumentedTest.kt`；`tools/ci/acceptance_lint.py` | M | 预算同源、最近窗口、校准口径、fake plannedReason、lint 前缀；无新 DAO |
| 2 P1 模拟/标定 | `core/domain/src/test/.../simulation/{SimulatedLearner,SimulatedLearnerTest,SimulationDeterminismContractTest,ReviewCalibrationExperimentTest}.kt`（??）；`core/domain/src/test/.../ReviewScoringWeightsSeamTest.kt`（??）；`core/domain/.../{ReviewPlannerV2,ReviewPlanner}.kt`（M，seam 字节改动） | ??/M | `AlgorithmConstants.kt` **不在清单**（零 diff）；VERSION 未 bump、金样未重生成 |
| 3 P1 文档/证据 | `docs/research/2026-11-08-{batch-49-baseline,calibration-method,clarification-ux,review-calibration-prereg,review-calibration-report}.md`、`2026-11-08-review-calibration-report.json`（??）；`docs/research/evidence/2026-11-08-batch-49/**`（??，271 项 EVIDENCE；**除 `device/**` 外**，设备证据见块 9） | ?? | 预注册 sha `042776e8…`；证据 `.log`/`.mimosa` 不入库（§6 第 16 条） |
| 4 P2-11 KD-67 | `feature/review/.../{KnowledgeClarificationPresenter,KnowledgeClarificationCard,KnowledgeCalibrationDecision}.kt`（??）、`KnowledgeReviewSessionScreen.kt`/`ViewModel.kt`（M）；`core/data/.../study/{KnowledgeQuizItemStore,KnowledgeQuizItemVerificationUpgrader,RoomBackedStudyExperienceRepository}.kt`（M）；`core/domain/.../StudyExperienceRepository.kt`（M）；`app/.../SmartMistakeBookRoot.kt`（M）；`feature/review/src/test/.../KnowledgeClarificationPresenterTest.kt`（??）；`core/data/src/test/.../{KnowledgeQuizItemStoreVerificationStatusTest,KnowledgeQuizItemVerificationUpgraderTest}.kt`（??/M） | ??/M | 确认卡/核验写路径/生产装配；真 Room e2e 仍 BLOCKED |
| 5 P2-12 校准页 | `core/domain/.../SchedulingCalibrationView.kt`（??）；`app/.../SchedulingCalibrationSection.kt`（??）；`core/data/.../study/StudySchedulingCalibration.kt`（M）、`core/data/.../settings/DataStoreSchedulingSettingsStore.kt`（M）、`core/data/.../study/RoomBackedStudyExperienceRepository.kt`（M）；`app/.../{SecondaryScreens,SchedulingSettingsScreen}.kt`（M）；测试 `SchedulingCalibrationViewBuilderTest`/`SchedulingCalibrationCopyTest`/`StudySchedulingCalibrationTest`/`SchedulingSettingsStoreInstrumentedTest`/`SchedulingCalibrationSectionInstrumentedTest`（??/M） | ??/M | 21 维/来源/拟合/G6/动作；含范围外 `retentionFromSlider` 恢复（§6 第 18 条） |
| 6 P2-13/14 冲突 | `core/domain/.../KnowledgeReviewQueue.kt`（M）；`core/data/.../study/{StudyProjectionDrainer,StudySnapshotBuilder,RoomBackedStudyExperienceRepository}.kt`（M）；`feature/review/.../{KnowledgeConflictCard,KnowledgeReviewSessionScreen,KnowledgeReviewSessionViewModel}.kt`（??/M）；`app/.../StudyProjectionIssueBanner.kt`（??）、`SmartMistakeBookRoot.kt`（M）；`core/data/src/test/.../ProjectionConflictReprojectTest.kt`（??）；`feature/review/src/test/.../{KnowledgeConflictCardTest,KnowledgeConflictSessionStateTest}.kt`（??）；`app/src/test/.../StudyProjectionIssueCopyTest.kt`（??） | ??/M | CONFLICTED 专用状态 + `reprojectAll()`；零新增 DAO |
| 7 验收面 | `docs/acceptance/ledger.md`（M）；`docs/acceptance/verdicts/rows/**`（922 M）；`docs/acceptance/{acceptance-report.json,verdicts.md,domain-index.md,adjudication.md}`（M） | M | 台账 sha `264425f5…`；REVIEWS 923/923；终裁 B49 已写入 `adjudication.md`。**`blocked-runbook.md` 的 M 是批次 48 更新（diff 实证），不并入本批**，由批次 48 owner 决定 |
| 8 验收证据 | `docs/acceptance/evidence/2026-11-08-batch-49/**`（??；`GATES.sha256` 10 项、`gate/` 10 文件、`make_verdicts_report.py`） | ?? | 门禁归档；.log 视 `gate/*.txt` 摘要 |
| 9 设备证据 | `docs/research/evidence/2026-11-08-batch-49/device/**`（??；含 `soak/` 117 份 monkey 日志、CSV、4 PNG、a11y） | ?? | **当前 153 个 `.log` 受 `*.log` 忽略**（含 117 份 `soak-monkey-*.log` 与各轨构建/测试日志）；需 owner 决定是否 `git add -f`。`EVIDENCE.sha256` 覆盖这些文件 |
| 10 记录 | `docs/research/2026-11-08-batch-49-record.md` | ??（本文件） | 收口记录 |

- **建议提交顺序**：块 1–2（代码/测试）→ 块 4–6（产品闭环）→ 块 8（验收证据）→ 块 7（验收面，绑证据 sha）→ 块 3/9（研究/设备证据）→ 块 10（记录）。
- **禁止**：`git add -A`；14 个 `gradle.lockfile`；`docs/2026-11-08-review-calibration-report.json`（GBK 非法）；他线 `ReviewReminder*`/visualjs/知识线文件；`docs/acceptance/evidence/2026-11-07-batch-48/`（批次 48 owner 决定）。
- **提交前复核**：重跑 §3 三份哈希清单 `--verify`（当前 271/923/10 全 0）、`acceptance_report --require-complete`（当前 exit 1，预期仍 1）、`pytest tools/tests/test_batch49_repair_invariants.py`（4 passed）。提交不改变终裁：**NOT ACCEPTED 维持**。

---

## 8. 回链

| 引用 | 路径 | 用途/关键值 |
|---|---|---|
| 计划 | `.zcode/plans/plan-2026-11-08-mastery-review-quality.md` | 18 项任务与硬约束；本记录 §1 对账来源 |
| 基线 | `docs/research/2026-11-08-batch-49-baseline.md` | 开工时 HEAD `ba7b5624`、595 脏路径、5 条原红点名、热区他线清单 |
| 研究总索引 | `docs/research/evidence/2026-11-08-batch-49/00-index.md` | 12 行验收、25 份复核、门禁、残余 §6 |
| 证据哈希 | `docs/research/evidence/2026-11-08-batch-49/EVIDENCE.sha256` | 271 项，本轮 `-c` 271/271 OK |
| P0 收尾 | `docs/research/evidence/2026-11-08-batch-49/p0-closure.md` | 预算/窗口/校准口径/前缀/5 条原红 |
| 预注册 | `docs/research/2026-11-08-review-calibration-prereg.md` | sha `042776e8…96b843`；§4.3/§5.4 门、§11 bundle |
| 方法 | `docs/research/2026-11-08-calibration-method.md` | R1 方法论；模拟世界/指标/门 |
| 标定报告 | `docs/research/2026-11-08-review-calibration-report.md`、`.json` | G6 0/4、权重 0/17、阈值读数、`decision` 块、`post_report_drift` |
| 标定证据 | `docs/research/evidence/2026-11-08-batch-49/calibration/**` | `g6-decisions.json`、`weight-decisions.json`、`determinism.json`、`cross-process-rerun/`、`report-recompute/` |
| 模拟器 | `docs/research/evidence/2026-11-08-batch-49/p1-6-simulated-learner.md` | 6 例、同 seed 摘要 `3d64a84e…`、29/30 覆盖 |
| 行为裁决 | `docs/research/evidence/2026-11-08-batch-49/p1-9-keep-current-values.md` | 维持 0.5/权重/VERSION/金样；常量哈希 |
| KD-67 | `docs/research/evidence/2026-11-08-batch-49/kd67-clarification-closure.md` | presenter/card/store/upgrader/Root；残余 §5 |
| 校准页 | `docs/research/evidence/2026-11-08-batch-49/calibration-page/README.md` | 7/7、8/8、4/4、范围外修复 §5.2 |
| 冲突处置 | `docs/research/evidence/2026-11-08-batch-49/p2-conflict-handling.md` | `CONFLICTED`/`reprojectAll()`/冲突卡；残余 §6 |
| 设备 | `docs/research/evidence/2026-11-08-batch-49/device/README.md` + `device/soak/EXIT-CAPTURE-CAVEAT.md` | 8/8、9/9、7200s/117 轮、PSS、退出码 caveat |
| 单测门 | `docs/research/evidence/2026-11-08-batch-49/unit-gate.md` | 12 模块 4161/9 红/exit 1；逐条归因 |
| 强门 | `docs/research/evidence/2026-11-08-batch-49/strong-gate.md` | 7 任务 exit 0；只编译不执行 |
| 环后门禁 | `docs/research/evidence/2026-11-08-batch-49/postfix-gate.md` | 强门 0；5 模块 2970/10 红；acceptance exit 1 |
| 修复环 | `docs/research/evidence/2026-11-08-batch-49/repair/README.md`、`unit-gate-and-kd67-blockers.md`、`red-green.log.md`、`p1-10-evaluate-scheduling-models.md`、`calibration-driver-drift.md` | 高/中处置、4 阻塞、红绿记录、P1-10、漂移复跑 |
| 集成检查 | `docs/research/evidence/2026-11-08-batch-49/integration-check.md`、`commit-manifest-add.txt` | 改动枚举、热区归属、F1–F10、126 路径快照 |
| 台账 | `docs/acceptance/ledger.md` | 1200 行、900/22/278/0、sha `264425f5…f6ba8` |
| 验收报告 | `docs/acceptance/acceptance-report.json`、`docs/acceptance/evidence/2026-11-08-batch-49/gate/acceptance-report.json` | sha `5db05770…55db`；exit 1、violations=[] |
| 行级复核 | `docs/acceptance/verdicts/rows/*.json`、`REVIEWS.sha256` | 922 份；923/923 OK；25 份重生成 |
| 终裁 | `docs/acceptance/adjudication.md` §「批次 49 报告重生成终裁」 | NOT ACCEPTED；B49-0…B49-9；签/不签范围 |
| 报告投影 | `docs/acceptance/verdicts.md`、`docs/acceptance/domain-index.md` | 1200 行；计数 900/22/278；sha `8e3c84cf…`、`3d451976…` |
| 门禁证据哈希 | `docs/acceptance/evidence/2026-11-08-batch-49/GATES.sha256` | 10 项；10/10 OK |
| 上一批 | `docs/research/2026-11-07-batch-48-record.md` | 898/23/279；F-055/F-057/F-063 前态；NOT ACCEPTED |

## 9. 主会话收口（2026-11-08）

### 9.1 主会话实跑门禁

| 门 | 结果 |
|---|---|
| 单测门（12 模块 `--continue`） | **exit=1**；2769 tests / 9 fail；全部为 HEAD 既存他线语义（KernelWave0 schema75、LibraryFts×3、Gateway×3、profileStrengths、TutorExportSheet）；本批 P0 五条原红已全绿 |
| 强门（两 flavor androidTest 强制重编 + assemble） | **exit=0**（41s） |
| 定向（模拟器/校准视图/ExamWindow/CapacityPreview/PlannedReason） | **exit=0** |
| 验收报告 | PASS 900 / FAIL 22 / BLOCKED 278 / UNVERIFIED 0；`pass_with_review=900/900`；exit=1 |

**22 FAIL 构成**：J 域 16（签名/AAB/版本/干净树）、H-018/H-062（DOCX/改卷文本 AIGC）、F-057（2h 超限路径未命中，缺陷单维持）、GATE-032/038/042/091/092/099（终裁门/CI/切分门）。外部与切分算法项，非本轮算法质量项。

### 9.2 标定裁决（关键）

- **G6 0/4、权重 0/17 全部未过预注册门 → 维持现值**；`EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION=0.5`、`ReviewScoring` 常量未改，`ReviewPlannerV2.VERSION` 未 bump，金样未变。
- 机制发现：`MAX_BEAM_STEPS=20` 使每日选题步数封顶，G6 扩容不转化为更多步数，f 效应被机械压平；护栏 G2/G3 在 6 KC/科的离散世界里结构性不可达——报告明确“不是权重没用，是可裁决区间为空”，且未事后改规则。
- 掌握阈值 21/0.9 只测量（T0/T1/T2 读数），`change_proposed=false`，未改。
- 全部结论标 **model-based**；真库/真机样本不足维持 BLOCKED。

### 9.3 产品闭环与 P0 实得

- **P0 断链**：预算同源（preview/intake/plan + 两新例）、review_log 最近窗口（设备红→绿 4/4）、per-reason 过滤、fake plannedReason、lint 检查列前缀、E-061 更正。
- **P2**：KD-67 确认卡/presenter/核验写路径（JVM 14/0、设备 8/8→9/9）、校准可见页（JVM 7/7/8、设备 8/8）、投影冲突专用状态 + `reprojectAll()` + 知识点冲突卡（JVM 8/0/5/0/6/0/23/0/3/0、设备 8/8）。
- 25 份失效复核按当前 sha 重生成；`pass_with_invalid_review=0`。

### 9.4 提交与推送

P0 修复 → 模拟器/预注册/报告/校准视图 → 数据层校准与测试 → KD-67/冲突数据层 → feature/review UI → app 校准页/横幅 → Root 接线/设备用例 → 验收台账 → 922 份行级 JSON → 证据 → 基线/记录；**已推送远端 `1c109282`**（本地树后续无未提交本批文件）。

### 9.5 剩余

- 外部：生产签名/AAB/versionCode/干净树（J 域 16）、DOCX 与改卷文本 AIGC（H-018/H-062）、切分门 0.5714（AUD-08 需 CV+LLM 重做）。
- 算法侧：`MAX_BEAM_STEPS=20` 与护栏不可达是**设计级**发现，是否改属产品裁定；真库标定需 ≥400 可拟合样本；KD-67 真 Room 端到端与“第二道校准题”仍 BLOCKED。
