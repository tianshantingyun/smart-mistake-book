# 批次 50 环后强门复跑（post-fix gate，2026-11-09）

> 角色：门禁执行员（本 ask 只复跑门禁并记录 exit；未改任何源码/测试/台账/阈值/门定义/金标）。
> 执行时点：2026-11-09 02:23–02:34 +0800。
> HEAD：`a87fc70d15c8ad726cee17422dbeef0b4977747d`（main）；共享脏树 `git status --porcelain -uall` = **5355**（复跑前后逐次实读均为 5355，见 `build/batch50-postfix-unit-*.log` 首尾行）。
> 串行纪律：Gradle 单客户端逐条执行（后台任务跑完再起下一条，无重叠）；未运行 `connected*AndroidTest`；未调用 adb；开工前 `ps -W`/`tasklist` 只有两个 `java.exe`，`Get-CimInstance` 实读二者均为 Gradle daemon（本仓 20932、他仓 `D:\smb-ui-forest-20261027` 37132），本仓 daemon 日志当时只有周期健康检查，未见构建 wrapper 进程。
> 说明：`build/batch50-strong-postfix.log` 盘上此前不存在（开工前 `ls -la build/*batch50* build/*50-*` 的输出只有 `build/b50-baseline-backup-sync.txt` 与 `build/b50-ledger/`，无任何 strong 日志），本轮为该文件首次生成；脚本 `build/run-batch50-strong-postfix.sh` 为新增（`build/` gitignored），任务集与 `build/run-batch49-strong-postfix.sh:14-22` 逐字一致（门定义不变）。

## 0. 结论与 exit

| 门 | 命令 | exit | 摘要 |
|---|---|---:|---|
| 强门（post-fix） | `bash build/run-batch50-strong-postfix.sh` | **0** | 7/7 任务 exit=0；`GRADLE_EXIT=0`；`^e: ` = 0；无 FAILED 任务 |
| 修复环代码足迹定向单测 | 见 §2.1 | **0** | 5 个 pytest 文件 **145 passed**；d1 三脚本实跑（其中 `sample_d1.py --check` 为**预期红 exit 2**） |
| 本批 Kotlin 改动模块定向单测（扩展口径） | 见 §2.2 | **1** | 10 模块 **4294 例 / 11 红 / 0 error / 3 skip**；11 红逐名与本批/批次49 已登记既有红一致（§2.3） |
| `acceptance_report` | `python -I tools/ci/acceptance_report.py --out build/b50-acceptance/report-postfix.json` | **1** | `state=FAIL`；counts **933/13/254/0**；`violations=[]`；933/933 PASS 行有效复核 |
| `acceptance_report --require-complete` | 同脚本 `--require-complete` | **1** | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=13 BLOCKED=254)` + `REVIEW_CHECKS_SHARED`（20 组） |

**不得读成**：强门 exit=0 只证明 androidTest 源码编译与打包，不执行任何测试；单测 exit=1 与验收 exit=1 是真实未收口（11 红均为既有；台账仍有 13 FAIL/254 BLOCKED），不是本次复跑的机械故障。

## 1. 强门（post-fix）：PASS，exit=0

命令（逐字）：

```bash
cd "D:/smart mistake book"
bash build/run-batch50-strong-postfix.sh
# SCRIPT_EXIT=0
```

- 日志：`build/batch50-strong-postfix.log`（**1129 行**，sha256 `453584f46e3b6e039c0ce0ab0242d2fda7bba68fc8121f821516ea1baea6e1ef`），起止 `02:23:43 → 02:25:36 +0800`，HEAD/脏树数/jdk 21.0.11 见日志头 `:1-4`。
- 脚本：`build/run-batch50-strong-postfix.sh`（sha256 `b974d8eb304e8933ecf431c8904234bde481aa436984263a71a27595168763bb`）。
- 逐任务（日志行号为 `build/batch50-strong-postfix.log` 行号）：

| # | 任务 | exit | 任务态 | BUILD 行 |
|---|---|---:|---|---|
| 1 | `:core:data:compileDebugAndroidTestKotlin --rerun` | 0 | 执行（`:62`，无 UP-TO-DATE） | `:75` `BUILD SUCCESSFUL in 49s` |
| 2 | `:core:database:compileDebugAndroidTestKotlin --rerun` | 0 | 执行（`:138`） | `:192` `BUILD SUCCESSFUL in 20s` |
| 3 | `:core:ui:compileDebugAndroidTestKotlin --rerun` | 0 | `NO-SOURCE`（`:250`） | `:260` `BUILD SUCCESSFUL in 4s` |
| 4 | `:core:export:compileDebugAndroidTestKotlin --rerun` | 0 | 执行（`:308`） | `:319` `BUILD SUCCESSFUL in 5s` |
| 5 | `:app:compileLocalFirstDebugAndroidTestKotlin --rerun` | 0 | 执行（`:546`） | `:563` `BUILD SUCCESSFUL in 13s` |
| 6 | `:app:compileStrictOfflineDebugAndroidTestKotlin --rerun` | 0 | 执行（`:790`） | `:807` `BUILD SUCCESSFUL in 10s` |
| 7 | `:app:assembleLocalFirstDebug --continue` | 0 | `:app:assembleLocalFirstDebug UP-TO-DATE`（`:1114`）、`packageLocalFirstDebug UP-TO-DATE`（`:1112`） | `:1124` `BUILD SUCCESSFUL in 3s` |

- `grep -c "^e: " build/batch50-strong-postfix.log` = **0**；`grep -cE '^> Task .* FAILED'` = **0**；末行 `GRADLE_EXIT=0`（`:1129`）。
- APK：`app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk`，190 694 962 B，mtime `11-08 20:51`，sha256 `43f1fe46651614f081c0d9edb8a4bc0b33988905822868e857b24e25c34a3e8e`。本次 assemble 为 UP-TO-DATE，即该 APK 与批次 50 设备轨绑定的 `43f1fe46…` 为同一构建；**未重新打包**。
- **不证明**：不执行任何测试；`core:ui` = NO-SOURCE；不证明运行时/设备行为。

## 2. 定向单测

### 2.1 修复环代码足迹（主口径）

依据：`docs/research/evidence/2026-11-09-batch-50/repair/REPAIR-RING.md:75-91` 的改动清单只含 Python 工具与文档（`tools/split_golden/check_split_gate.py`、`tools/figure_eval/figure_eval_gate.py`、`tools/tests/test_batch50_repair_invariants.py`、`docs/acceptance/evidence/2026-11-09-batch-50/make_verdicts_report.py`、`d1/frame_drift_check.py`）；另以 mtime 扫描 `app core feature tools docs` 下 01:45–02:30 的 `*.kt`，**0 个命中**。故修复环本身没有 Kotlin 模块可跑。

```bash
cd "D:/smart mistake book"
python -I -X utf8 -m pytest \
  tools/tests/test_batch50_repair_invariants.py tools/tests/test_split_gate.py \
  tools/tests/test_acceptance_report.py \
  tools/figure_eval/test_figure_eval_gate.py tools/figure_eval/test_mva_gate.py -q
# TARGETED_PYTEST_FINAL_EXIT=0 → 145 passed in 7.13s
```

| 套件 | 命令（同一行） | exit | 读数 | 日志 |
|---|---|---:|---|---|
| 修复不变量 + 切分门 | `pytest tools/tests/test_batch50_repair_invariants.py tools/tests/test_split_gate.py -q` | 0 | 71 passed | `build/batch50-postfix-pytest-repair.log` |
| Figure/MVA 门 | `pytest tools/figure_eval/test_figure_eval_gate.py tools/figure_eval/test_mva_gate.py -q` | 0 | 46 passed | `build/batch50-postfix-pytest-figure.log` |
| acceptance 报告工具 | `pytest tools/tests/test_acceptance_report.py -q` | 0 | 28 passed | `build/batch50-postfix-pytest-acceptance-report.log` |
| **合并（最终盘上状态）** | 上述 5 文件 | **0** | **145 passed** | `build/batch50-postfix-pytest-targeted-final.log`（sha256 `f699c6b7230d9714b639c50a2b8732c2441f49482e688438371f516f6cec4980`） |

修复环证据脚本实跑（`d1/frame_drift_check.py` 会按设计重写 `frame-drift.json`；合并 pytest 在其**之后**重跑，断言的是最终盘上状态）：

| 命令 | exit | 关键读数 | 日志 |
|---|---:|---|---|
| `cd docs/research/evidence/2026-11-09-batch-50/d1 && python -I -X utf8 frame_drift_check.py` | **0** | `conclusion_unchanged=true`；`replay.exit=2`；`sample_layer_mismatches=0`；`resample_different_keys=879`；`main_lower=0.975688200072439`（PASS）；`guardrail_nofpc_lower_normal=0.8979148347511259`（FAIL） | `build/batch50-postfix-frame-drift.log` |
| `cd …/d1 && python -I -X utf8 recompute.py` | **0** | `all_match=true` | `build/batch50-postfix-recompute.log` |
| `cd …/d1 && python -I -X utf8 sample_d1.py --check` | **2** | `{"match": false, "expected": 1600, "actual": 1600}`——**预期红**（抽样框已被 D-2 覆盖），不是通过 | `build/batch50-postfix-sample-check.log` |

未运行：`make_verdicts_report.py`（默认直接写 `docs/acceptance/verdicts.md` / `domain-index.md`，本轮不重生成终端文档）。

### 2.2 本批 Kotlin 改动模块（扩展口径，非修复环足迹）

修复环未改 Kotlin；为给出本批最终 Kotlin 单测读数，按本批改动模块（`core/model`、`core/domain`、`core/database`、`core/data`、`core/export`、`core/ui`、`feature/library`、`feature/review`、`feature/tutor`、`app`）跑 `testDebugUnitTest --continue --rerun`：

```bash
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew :core:model:testDebugUnitTest :core:domain:testDebugUnitTest \
  :core:database:testDebugUnitTest :core:data:testDebugUnitTest :core:export:testDebugUnitTest \
  --continue --rerun --console=plain   # GRADLE_EXIT_A=1（:core:database、:core:data 红）
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew :core:ui:testDebugUnitTest :feature:library:testDebugUnitTest \
  :feature:review:testDebugUnitTest :feature:tutor:testDebugUnitTest :app:testLocalFirstDebugUnitTest \
  --continue --rerun --console=plain   # GRADLE_EXIT_B=1（:feature:tutor、:app 红）
# 补跑：--rerun 对别名/UP-TO-DATE 未生效的两处
./gradlew :core:model:test --rerun-tasks --console=plain          # GRADLE_EXIT_D=0
./gradlew :feature:review:testDebugUnitTest --rerun --console=plain  # GRADLE_EXIT_C=0
```

| 模块 | tests | fail | err | skip | 结论 | XML 新鲜度 |
|---|---:|---:|---:|---:|---|---|
| `core:model`（`test --rerun-tasks`） | 675 | 0 | 0 | 0 | 绿 | 02:33:27（70 文件全新鲜） |
| `core:domain` | 742 | 0 | 0 | 1 | 绿（1 skip 为既有 `ReviewCalibrationExperimentTest`） | 02:29:29 |
| `core:database` | 248 | **4** | 0 | 0 | 红（§2.3 #5–8） | 02:29:39 |
| `core:data` | 1707 | **4** | 0 | 2 | 红（§2.3 #1–4） | 02:30:55 |
| `core:export` | 178 | 0 | 0 | 0 | 绿 | 02:28:57 |
| `core:ui` | 56 | 0 | 0 | 0 | 绿 | 02:31:42 |
| `feature:library` | 108 | 0 | 0 | 0 | 绿 | 02:31:43 |
| `feature:review`（`--rerun` 补跑） | 116 | 0 | 0 | 0 | 绿 | 02:32:50 |
| `feature:tutor` | 266 | **1** | 0 | 0 | 红（§2.3 #9） | 02:31:44 |
| `app:testLocalFirstDebugUnitTest` | 198 | **2** | 0 | 0 | 红（§2.3 #10–11） | 02:31:44 |
| **合计** | **4294** | **11** | **0** | **3** | **exit=1** | — |

- 日志：`build/batch50-postfix-unit-a.log`（203 行，PRE/POST dirty 5355）、`build/batch50-postfix-unit-b.log`（324 行，PRE/POST 5355）、`build/batch50-postfix-unit-model-review.log`（91 行）、`build/batch50-postfix-unit-model.log`（43 行）。四份日志 `grep -c "^e: "` 均为 **0**（测试源码编译通过，红是断言）。
- 纪律说明：`:core:model:testDebugUnitTest`（纯 JVM 别名）与 `:feature:review:testDebugUnitTest` 在首轮分别呈 UP-TO-DATE（`--rerun` 未穿透别名/未触发），已分别用 `:core:model:test --rerun-tasks` 与 `:feature:review:testDebugUnitTest --rerun` 真实重跑并以 XML mtime 复核；**未把 UP-TO-DATE 读数当本轮执行结果**。

### 2.3 11 红逐名与归因（均为已登记既有红；本 ask 未在干净 HEAD 回退树重演）

| # | 模块 | 用例 | 既有归因（当前记录/基线逐名同组） |
|---|---|---|---|
| 1–3 | `core:data` | `OpenAiCompatibleModelGatewayTest.unverifiedEditCapabilityAndCancelledEditMakeZeroHttpCalls` / `changedEditSourceFingerprintIsRejectedBeforeHttp` / `mistakeEditMockServerReceivesOriginalImageAndRejectsInvalidOrTruncatedOutput`（`ModelEgressAuthorizationException`，`OpenAiCompatibleModelGatewayTest.kt:124/153/166`） | 批 49 `unit-gate.md:85-87`；批次 50 切分 CV 节已实跑同一 3 条（`record:178`）；测试文件 `git status` 空 |
| 4 | `core:data` | `RoomBackedStudyExperienceRepositoryTest.profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes`（`AssertionError`，`:1423`） | 批次 49 同名；批次 50 `record:60-61,149,593` 逐名同组；测试文件 `git status` 空 |
| 5 | `core:database` | `KernelWave0SchemaContractTest.the study database version matches the latest exported schema` | `record:55-57` 同一组（schema 头 vs 75）；测试文件 `git status` 空 |
| 6–7 | `core:database` | `LibraryFtsCountPathQueryCopyContractTest.crossJoinSqlCopiesMatchTheDaoQueries` / `scale50kCrossJoinSqlCopiesMatchTheDaoQueries` | 同上（FTS 冻结 SQL 副本漂移）；测试文件 `git status` 空 |
| 8 | `core:database` | `LibraryFtsRankingQueryContractTest.rankingAndFilterLiteralsMatchTheFrozenReferenceCopy` | 同上；测试文件 `git status` 空 |
| 9 | `feature:tutor` | `TutorExportSheetTest.theWordFormatNoticeStatesItsRealLimitsAndPdfHasNone` | `record:634-635` 同一类（`TutorExportSheetTest`，HEAD 文案 vs 用例）；测试与 `TutorExportSheet.kt` 均 `git status` 空 |
| 10 | `app` | `StorageRestoreFeedbackTest.genericRestoreFailureDoesNotEchoTheRawExceptionMessage` | `record:705-706` 同名；测试文件干净；`SecondaryScreens.kt` 本轮 diff = **66 insertions / 0 deletions**，`grep -E "恢复失败\|failure.message\|storageRestoreFailureMessage"` 对 diff **0 命中**（未触及该函数） |
| 11 | `app` | `TutorSettlementNoticeTest.aVerdictlessTutorVisitSaysThereIsNoVerdictAndOffersRetry` | `record:705-706` 同名；测试与 `SmartMistakeBookDestinations.kt` 均 `git status` 空（HEAD 字节） |

**归因边界**：以上是"逐名与本批既有记录/批次49 基线一致 + 相关测试文件干净（第 10 条另证 diff 不触及）"，不是干净 HEAD 回退树重演；共享树 5355 条脏改动无法安全回退，故不记"HEAD 实证"。**本批新增面未见新红**（新增用例所在模块：`core:model` 675/0、`feature:library` 108/0、`feature:review` 116/0；`core:data`/`app` 的红均为上表既有项）。

## 3. `acceptance_report`：exit=1（真实未收口）

```bash
python -I tools/ci/acceptance_report.py --out build/b50-acceptance/report-postfix.json
# REPORT_EXIT=1
python -I tools/ci/acceptance_report.py --require-complete \
  --out build/b50-acceptance/report-require-complete-postfix.json
# REQUIRE_COMPLETE_EXIT=1
```

| 项 | 值 |
|---|---|
| ledger | `docs/acceptance/ledger.md`，sha256 `9044a7794dda196c6fc22c8e989a33099482ef26399e13ffbcbe28c0f045cb97`（与 `record:822` 冻结值一致；mtime 01:05，本轮未改） |
| counts | PASS **933** / FAIL **13** / BLOCKED **254** / UNVERIFIED **0**（total 1200） |
| state / ok | `FAIL` / `false` |
| `violations`（标准报告） | **[]**（证据链本身无违规） |
| 复核 | `pass_with_review=933`、`pass_without_review=0`、`pass_with_invalid_review=0`、`pass_with_row_specific_review=655`、`review_validation_errors=[]` |
| `--require-complete` violations | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=13 BLOCKED=254)`；`REVIEW_CHECKS_SHARED`（20 组非 ledger_row checks 被 ≥5 行共用） |
| head / dirty | `a87fc70d…` / 5355 |

- 输出：`build/b50-acceptance/report-postfix.json`（sha256 `1a513d88f8d3dfe0…`，219 行）、`report-require-complete-postfix.json`（sha256 `8b56332b5460f848…`，230 行）、同目录 `.out` 两份。
- `docs/acceptance/acceptance-report.json`（01:06）与 `verdicts.md` 未被本轮覆盖。

## 4. 未运行 / 残余（不得读成通过）

1. **设备与 2h soak 未在本 ask 复跑**：未用 adb、未安装 APK、未跑任何 instrumented/soak；设备证据仍以 `device/` 既有记录（emulator-5590，x86_64/API 34，非真机）为准。
2. **干净 HEAD 重演未做**：11 红按"逐名既有 + 相关文件干净"归因（§2.3），不是回退树实证。3D D-1 仍 FAIL（normal 护栏 0.8979<0.90；主判据 0.9757 未过 κ 前置，只是 model-labeled 暂定值）；3E 条件性通过（第二档无预留份额）；MVA 不可评；AUD-08 FAIL——四门结论不因本轮复跑改变。
3. **`acceptance_lint` / `trace_requirements` / `acceptance_init` / `d0_self_acceptance` 未在本 ask 重跑**（不在点名范围）；台账/复核/哈希清单未重生成。
4. **`make_verdicts_report.py` 未重生成**（会写 `verdicts.md`/`domain-index.md`）；`acceptance_report` 的 `dirty_paths=5355` 说明树仍是共享脏树。
5. **`core:model` 本轮实测 675 例**（记录/切分 CV 节为 674，时点差异未追因）；`feature:review` 首轮 UP-TO-DATE 已用 `--rerun` 补跑，本节读数取补跑 XML。
6. **强门不执行测试**：`core:ui` NO-SOURCE；`assembleLocalFirstDebug`/`packageLocalFirstDebug` UP-TO-DATE，APK 未重建（sha 与设备轨一致）。

## 5. 复跑命令

```bash
cd "D:/smart mistake book"
bash build/run-batch50-strong-postfix.sh; echo "SCRIPT_EXIT=$?"

python -I -X utf8 -m pytest \
  tools/tests/test_batch50_repair_invariants.py tools/tests/test_split_gate.py \
  tools/tests/test_acceptance_report.py \
  tools/figure_eval/test_figure_eval_gate.py tools/figure_eval/test_mva_gate.py -q; echo "PYTEST_EXIT=$?"

GRADLE_USER_HOME="$PWD/.gradle" ./gradlew :core:model:testDebugUnitTest :core:domain:testDebugUnitTest \
  :core:database:testDebugUnitTest :core:data:testDebugUnitTest :core:export:testDebugUnitTest \
  --continue --rerun --console=plain; echo "GRADLE_EXIT_A=$?"
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew :core:ui:testDebugUnitTest :feature:library:testDebugUnitTest \
  :feature:review:testDebugUnitTest :feature:tutor:testDebugUnitTest :app:testLocalFirstDebugUnitTest \
  --continue --rerun --console=plain; echo "GRADLE_EXIT_B=$?"
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew :core:model:test --rerun-tasks --console=plain; echo "GRADLE_EXIT_D=$?"

python -I tools/ci/acceptance_report.py --out build/b50-acceptance/report-postfix.json; echo "REPORT_EXIT=$?"
python -I tools/ci/acceptance_report.py --require-complete \
  --out build/b50-acceptance/report-require-complete-postfix.json; echo "REQUIRE_COMPLETE_EXIT=$?"
```
