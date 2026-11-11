# 批次 51 · 设备全量证据（2026-11-10）

> 角色：设备工程师（设备全量轨，串行独占设备）。范围 = 主会话下发的 4 项：
> ①数据安全（迁移 1..75 / 备份 34 / trimmer / rowCounts / drill + 四方门）、
> ②3E 设备复验（每日累计口径）+ 新 UI（5b/5c/6B-3/KD-67）、
> ③2h soak / R8 release 冒烟 / release APK 安装冒烟、
> ④设备型号/API/APK sha/源快照记录。
>
> 口径：本目录每条结论都对应本会话实跑命令与原始日志（命令写在日志首行的 `cmd=`）。
> 设备：`emulator-5590`（`ui_motion_20261107`）与 `emulator-5592`（`ui_teaching_20261108`），
> 均 x86_64 / API 34 / Android 14 / PAGE_SIZE 4096 —— **模拟器，非真机**。
> 全部设备用例走裸 `adb install` + `am instrument`；**未跑**任何 `connected*AndroidTest` / Gradle device 任务。

## 0. 一屏结论

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 快照绑定（HEAD / 脏树 / APK sha / 设备） | 已绑定 | `00-snapshot.txt`、`02-device-meta.txt`、`03-apk-shas.txt` |
| 2 | 四方备份门 `check_backup_plan_sync.py` | **EXIT=0 / PASS**（digest `8567faae…`） | `07-check-backup-plan-sync.log` |
| 3 | 迁移 1..75（`FullMigrationMatrixInstrumentedTest` 20 例） | **OK (20 tests)**，Time 1,446.843s | `05-instrument-migration-matrix.log` |
| 4 | 备份 34（`BackupRestoreInstrumentedTest`） | **OK (34 tests)**，51.031s | `08-instrument-backup-34.log` |
| 5 | trimmer（`BackupSnapshotTrimmerInstrumentedTest` 10 例） | **OK (10 tests)**，209.429s | `09-instrument-trimmer.log` |
| 6 | rowCounts（`BackupRowCountsCoverageInstrumentedTest` 2 例） | **OK (2 tests)**，3.093s | `10-instrument-rowcounts.log` |
| 7 | drill mask 9/13/15（3 例） | **OK (3 tests)**，9.649s | `11-instrument-drill-masks.log` |
| 8 | drill corrupted（1 例） | **OK (1 test)**，1.329s | `12-instrument-drill-corrupted.log` |
| 9 | drill kill-stage（设计性进程死亡） | `shortMsg=Process crashed.`（**按设计**） | `13-instrument-drill-killstage.log` |
| 10 | drill restore-from-handoff（1 例） | **OK (1 test)**，2.412s | `14-instrument-drill-restore-handoff.log` |
| 11 | 3E 每日累计设备复验（4 例） | 首跑 **1 红**（夹具 weight 0.5 > 0.35 上限）→ 修夹具 → **OK (4 tests)**，6.376s | `20-…log`（红）、`26-…log`（绿）、`25-…build.log` |
| 12 | KD-67 第二道校准题真 Room e2e（3 例） | **OK (3 tests)**，1.962s | `21-instrument-kd67.log` |
| 13 | 6B-3 改卷 UI + 5c 错因面板（24 例） | **OK (24 tests)**，32.839s | `22-instrument-library-6b3-5c.log` |
| 14 | 5c 真库写入接缝 + JS 回放 painter（25 例） | **OK (25 tests)**，1.333s | `23-instrument-5c-realrepo-js-painter.log` |
| 15 | 5b 容量 / 新题预览 / AIGC 标签 / 能力屏（17 例） | **OK (17 tests)**，36.361s | `24-instrument-app-5b-aigc-capability.log` |
| 16 | R8 release 构建（minify+shrink，本地签名） | **BUILD SUCCESSFUL / EXIT=0**；APK sha `b6fbd877…`；mapping 140,191,204 B（R8 9.3.16） | `06-build-release-r8.log`、`03-apk-shas.txt` |
| 17 | release APK 安装冒烟 | 装成功；versionCode **101177** / 0.2.0-localfirst；冷启 1629ms；四 tab + 3 个深层页渲染；crash 0 / FATAL 0 / ANR 0 | `50-release-install.log`、`51-release-launch.log`、`52…58-*.png/xml` |
| 18 | 2h soak（release APK 101177） | **PASS**：7200s / 115 轮 / pid 未重启 / TOTAL PSS 62,691→108,819 KB / 0 新 crash / 0 新 dropbox / monkey 全 0 | `soak-start.txt`、`soak/soak-summary.txt`、`37-soak-console.log` |

## 1. 快照与绑定（`00-snapshot.txt`）

```
time_utc=2026-11-09T23:18:40Z local=2026-11-10 07:18:40 +0800
HEAD=8dd29e963975ff44703f638064322be0e7494f98
HEAD_subject=docs: 记 CV 切题算法删除与切题只走模型识别（批次50 纠偏；2 文件）
branch=## main...origin/main
dirty_porcelain_lines=683
dirty_uall_paths=5418
modified_count=201
untracked_count=5217
tracked_diff_sha256=9f8b705c9be2e4d880c26fe6fd4dc203446ffc17ed8cae491e533a1cc16756fe
```

**如实**：本轮设备读数构建自**脏工作树**（HEAD + 各线未提交改动），不是干净树产物；源码绑定以
**HEAD + 下列 APK sha** 为权威，`tracked_diff_sha256` 只记开工时点。开工前 `git status --porcelain --
<目标路径>` 已查；本轨只改了 2 个测试夹具字面量（§7），未碰任何生产代码、门脚本、台账、金标。

APK sha256（`03-apk-shas.txt`，本会话实际安装/构建的包）：

| 用途 | 文件 | sha256 |
|---|---|---|
| app debug（UI 设备用例目标） | `app-localFirst-debug.apk` | `38cb991cf680ce40874da2912c5adb08addcb4884fa1b3cf052b13bf07b1a8b6` |
| app androidTest（5b/能力屏/AIGC 标签） | `app-localFirst-debug-androidTest.apk` | `2e32e0856f531c2c88d2921629e0f2deca7958b026f745e29fd6516fdd2afcd9`（与批次 50 终版同 sha，源未变、任务 up-to-date） |
| core:data androidTest（**3E 首跑**，红） | `data-debug-androidTest.apk` | `2ae00f060a2f50fdc91218348b80999d44d9233867a28cc3ca2b62cc66069870` |
| core:data androidTest（**修夹具后**，3E 复跑 + 其余 core:data 用首版） | 同上 | `2516876f9bef6e9874ab37c1289920ed83a318c3e0e600f4640951917ad85e9c` |
| core:database androidTest（迁移矩阵） | `database-debug-androidTest.apk` | `5a8ca2b6afe3d2d2c9e321d6079887741ef1e7a14d0d2cbc589c993df71f82b2` |
| feature:library androidTest（6B-3/5c 面板） | `library-debug-androidTest.apk` | `63ffb00cdf1b6a8a347d63ce3a1923a6b28e1fdf45a741f6df6a3c28bfaa2d95` |
| core:export androidTest（构建产物，本轨未跑该模块用例） | `export-debug-androidTest.apk` | `35aaa658b9adab1b940a56ca0b639607529c92883ef428774c1638724a98b9ce` |
| release（冒烟 + soak） | `app-localFirst-release.apk` | `b6fbd87745c3ba3bc5fc9b63e7830019c9e99682bacc7432d495546ed72ed46f`（156,230,405 B，versionCode 101177） |

设备元数据（`02-device-meta.txt`）：

| 字段 | emulator-5590 | emulator-5592 |
|---|---|---|
| AVD | `ui_motion_20261107` | `ui_teaching_20261108` |
| sdk / release | 34 / 14 | 34 / 14 |
| abi / page size | x86_64 / 4096 | x86_64 / 4096 |
| model / fingerprint | `sdk_gphone64_x86_64` / `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` | 同左 |
| is_emulator | 1 | 1 |
| boot_id | `31ecd364-6b2a-4e52-9643-23e681696172` | `b24f1996-4c2c-4722-9c30-2f6208790c3a` |

## 2. 数据安全（迁移 / 备份 / trimmer / rowCounts / drill）

命令模板：`adb -s emulator-5590 shell am instrument -w -r -e class <class> <pkg>/androidx.test.runner.AndroidJUnitRunner`
（drill 另加 `-e smb_allow_data_reset true`；kill-stage 另加 `-e smb_d1_kill_stage true`）。
完整命令逐条写在每个日志首行的 `cmd=` / `runner=` / `args=`。

| 套件 | 读数 | 判读 |
|---|---|---|
| `FullMigrationMatrixInstrumentedTest`（20） | `Time: 1,446.843` / `OK (20 tests)` | 1..75 全链 + 4 个带数据迁移 + 各版本表创建断言全绿。**比批次 50 的 553s 慢**：本轮与 2h soak 的 monkey、release R8 构建并发，模拟器 I/O 等待高（`top` 实测 38% iow），非语义差异。 |
| `BackupRestoreInstrumentedTest`（34） | `Time: 51.031` / `OK (34 tests)` | 备份 34 全绿 |
| `BackupSnapshotTrimmerInstrumentedTest`（10） | `Time: 209.429` / `OK (10 tests)` | trimmer 全绿（含 schema 73 identity mismatch fail-closed 等） |
| `BackupRowCountsCoverageInstrumentedTest`（2） | `Time: 3.093` / `OK (2 tests)` | rowCounts 全绿 |
| `DeviceMigrationDrillInstrumentedTest` mask 15/9/13（3） | `Time: 9.649` / `OK (3 tests)` | 3 个 mask 换机往返全绿（6C-2 白名单 9/13/15 未改） |
| 同上 corrupted（1） | `Time: 1.329` / `OK (1 test)` | 损坏包在换库前停止、不留半库 |
| 同上 kill-stage（1） | `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`、`INSTRUMENTATION_CODE: 0` | **设计性进程死亡**（用例自身 `killProcess(myPid())`），不是失败；交接件已落盘 |
| 同上 restore-from-handoff（1） | `Time: 2.412` / `OK (1 test)` | 新进程读交接件恢复、mask=9 对拍通过 |

四方门（`07-check-backup-plan-sync.log`，`python -I tools/ci/check_backup_plan_sync.py`）：
末行 `PASS 四方一致（code / §9.14 机器块 / golden / 75.json）`，**EXIT=0**；
schemaVersion 75、identityHash `f9a3d1c9ea8ef83d7222721ad1015a51`、99 表 / 2 视图、
trimOrder 91、allowedMasks `[9, 13, 15]`、digest `8567faae0e90ad4b720eab9fb4ceda82ce90fdb14e7c8ec4dc8309a520fb286c`。

## 3. 3E 每日累计设备复验（含一处测试夹具红→绿）

**首跑（`20-instrument-3e-daily-cap.log`）**：`Tests run: 4, Failures: 1`，唯一红 =
`scanQuotaIsDailyCumulativeAcrossPlanCallsInTheSameStudyDay`：

```
java.lang.IllegalArgumentException: Chat evidence weight 0.5 exceeds the 0.35 cap
  at ChatEvidenceSubmitted.<init>(LearningState.kt:514)
  at ChatEvidenceDaoKt.toChatEvidenceModel(ChatEvidenceDao.kt:293)
  at ChatEvidenceDao.insertAsLedgerEvents(ChatEvidenceDao.kt:117)
```

根因（一手核对）：
- 上限单源 `core/model/.../LearningState.kt:532` → `MAX_CHAT_EVIDENCE_WEIGHT = 0.35`；
  生产答错写口 `KnowledgeQuizFeedbackWriter.kt:886` 走 `MasteryWriteGate.negativeWeight()` = 0.35
  （`MasteryWriteGate.kt:172,192`）——**生产路径不越界**。
- 红的是**测试夹具**：`StudyReviewPlanner3EScanInstrumentedTest.kt:700` 写 `weight = 0.5`，
  与生产写口和模型不变式都不一致；同一缺陷也存在于 JVM 夹具
  `RoomBackedStudyExperienceRepositoryTest.kt:740`。

处置（本轨最小改动，不改生产、不放宽断言）：两处夹具 `weight = 0.5` → `0.35`（附注释说明与
`MasteryWriteGate.negativeWeight()` 同值）。重建 `:core:data:assembleDebugAndroidTest`
（`25-build-data-androidtest-after-fix.log`，BUILD SUCCESSFUL 33s）→ 装新 test APK
（sha `2516876f…`）→ 复跑。

**复跑（`26-instrument-3e-rerun.log`）**：`Time: 6.376` / **OK (4 tests)**。4 例 =
全库捞回 10 个 / 时间预算先绑住 / **同日第二次排计划累计 ≤10（每日口径）** / 无考试日指纹一致。

口径说明：设备用例的每日累计断言（`StudyReviewPlanner3EScanInstrumentedTest.kt:336-393`）是
「第一次捞回 10 个并落当日 `KNOWLEDGE_QUIZ` 证据 → 第二次只能回放同一批，并集 ≤10」；
这是**收紧**后的每日口径（旧断言「同日累计 20 > 10」已被改写），未改门阈值。
JVM 同口径类本轮另行点跑，见 §8。

## 4. 新 UI 设备用例（5b / 5c / 6B-3 / KD-67）

| 功能面 | 类（模块） | 读数 |
|---|---|---|
| KD-67 第二道校准题真 Room e2e | `KnowledgeCalibrationQuestionRoomE2eInstrumentedTest`（core:data） | `OK (3 tests)` |
| 6B-3 自然语言改卷入口/预览/采用-取消 + 5c 错因面板 | `NativePaperEditModelInstrumentedTest` + `NativePaperEditRouteInstrumentedTest` + `MistakeErrorCausePanelInstrumentedTest`（feature:library） | `OK (24 tests)` |
| 5c 真库写入接缝 + JS 回放 painter | `ErrorCauseReviewServiceInstrumentedTest` + `VisualDrawOpsPainterInstrumentedTest`（core:data） | `OK (25 tests)` |
| 5b 容量 / 新题预览 / AIGC 标签 / 能力屏 | `ReviewCapacitySectionInstrumentedTest` + `ReviewIntakeBacklogPreviewInstrumentedTest` + `AiContentLabelsInstrumentedTest` + `CapabilityScreenInstrumentedTest`（app） | `OK (17 tests)` |

说明：这些用例是**仪表化断言**（真实 app/Compose 树或真实 Room），不是人工走查；本轨未额外做
截图走查（批次 50 的 5b/5c/6B-3 截图证据仍在 `docs/research/evidence/2026-11-09-batch-50/`）。

## 5. release APK 安装冒烟（R8）

- 构建：`:app:assembleLocalFirstRelease -Psmbk.versionCode=101177`，`isMinifyEnabled=true` +
  `isShrinkResources=true`，签名取 `.tmp/release-signing/release-signing.env` 的本地 key
  （**值未打印/未落证据**）；`BUILD SUCCESSFUL`，APK `b6fbd877…`，`mapping.txt` 140,191,204 B
  / `# compiler: R8` / `9.3.16` / `min_api: 23`。
- 安装（`50-release-install.log`）：debug→release 签名不同，先 `uninstall` 再 `install` → `Success`；
  `versionCode=101177`、`versionName=0.2.0-localfirst`、`minSdk=23` / `targetSdk=36`。
- 启动（`51-release-launch.log`）：`am start -W` → `Status: ok`、`LaunchState: COLD`、
  `TotalTime: 1629`；9s 后 `logcat -b crash` **0 行**、全缓冲 `FATAL EXCEPTION` **0**、
  `ANR in com.tingyun` **0**，pid `11810`。
- 渲染：`52-release-home.png/xml` 四 tab 全渲染（复习空态「还没有可复习的题」/ 智能体「请先配置并
  测试模型」/ 错题本「暂无错题」/ 我的「学习掌握」）；另有 3 个深层页 R8 冒烟：
  `56-release-scheduling`（复习安排：每日时长/记忆目标/考试日历）、
  `57-release-privacy`（数据与隐私：本机数据清单/智能服务/隐私政策入口）、
  `58-release-storage`（存储、备份与导出：备份内容勾选/本机存储）；三页均渲染、tap/back 后
  crash 0 / FATAL 0 / pid 不变。
- **如实**：release 首次启动自动铺开知识库，DB 实测 `399,163,392 B`（应用内「学习记录 380.7 MiB」），
  即 soak 跑在**首启已播种**的库上，不是空库（批次 50 记录为「空库」，本轮不同，已按实际登记）。

## 6. 2h soak（release APK 101177）—— **PASS**

起跑绑定 `soak-start.txt`：`duration_target_s=7200`、APK `b6fbd877…`、versionCode 101177、
device `emulator-5592`、boot_id `b24f1996…`；harness = `35-soak-script.sh`（沿用批次 50 判据：
未达时长 / 新 crash 行 / 新 dropbox / monkey 非零 → FAIL）。

`soak/soak-summary.txt`（脚本自算，`EXIT=0`；控制台 `37-soak-console.log` 末行 `SOAK_EXIT=0`）：

```
duration_target_s=7200
cycles=115
pss_samples=115 pss_min_kb=62369 pss_max_kb=108584
total_pss_min_kb=62691 total_pss_max_kb=108819
dropbox_first=0 dropbox_last=0
crash_lines_first=0 crash_lines_last=0
crash_delta=0 dropbox_delta=0 monkey_rc_nonzero=0
completed=1
OUTCOME=PASS
FAIL_REASONS=none
EXIT=0
```

- 115 轮 monkey（`soak/soak-monkey-1..115.log`，每轮 `Events injected: 300` + `Monkey finished`，
  rc 全 0）；首样本 `23:45:23Z`、末样本 `01:44:24Z`（elapsed 7,144s，循环在 ≥7,200s 时置
  `completed=1` 退出）；pid 全程 `13400` **未重启**。
- PSS 62,369 → 108,584 KB（末次 102,157）；TOTAL PSS 62,691 → 108,819 KB（末次 102,384）；
  **只记录、不设门**（与批次 50 同口径：PASS 仅等价于「7200s 内无新增 crash/dropbox、monkey 全 0」，
  不等于内存/电池稳定）。
- `crash_lines` 首尾 0 / 0，`dropbox_hits` 首尾 0 / 0（本设备 dropbox 基线为 0，批次 50 为 2）。
- **口径如实**：本设备为排查 release 首启播种数据执行过 `adb root`（adbd 以 root 运行），因此
  `smaps_rollup` 的 `Pss:` 这次读得到（批次 50 该设备为 `pss_samples=0`）；只影响采样可读性，
  不改变 soak 判据。soak 跑在 release **首启已播种**的库上（DB 399,163,392 B，见 §5）。


## 7. 本轨改动文件（2 处测试夹具，最小改动）

| 文件 | 行 | 改动 | 理由 |
|---|---|---|---|
| `core/data/src/androidTest/.../study/StudyReviewPlanner3EScanInstrumentedTest.kt` | 700 | `weight = 0.5` → `0.35`（+2 行注释） | 夹具违反 `ChatEvidenceSubmitted` 0.35 上限，设备用例必红；生产写口同值 |
| `core/data/src/test/.../study/RoomBackedStudyExperienceRepositoryTest.kt` | 740 | `weight = 0.5` → `0.35`（+2 行注释） | 同一缺陷的 JVM 副本 |

未改：生产代码、门阈值、备份 mask、金标、台账、门脚本。两个文件在开工时均为 ` M`（3E 线在飞），
本轨只改上述字面量，未整文件回写；若 3E 线后续整文件回写，需重新应用这两处。

## 8. 未跑 / 未验证（如实）

- **真机 / arm64 / 16KB 运行时**：未跑（无真机；两台均 x86_64 / PAGE_SIZE 4096 模拟器）。
- **干净树复算**：本轨全部读数来自脏工作树 + 上表 APK sha；干净树复跑未做。
- **JVM 全量单测门 / lint / 强门**：不在本轨范围；本轨只点跑 3E 同口径 JVM 类一次（见 §8 末尾）。
- **AIGC 导出模块（core:export）用例**：本轨构建了 test APK 但未运行该类（批次 50 已归档 32 例；
  本批主会话是否重跑不在本轨四项内）。
- **`connectedLocalFirstDebugAndroidTest` / 任何 connected Gradle 设备任务**：未跑（计划禁令）。
- **Play Console / 备案 / 生产签名 / 图像 provider**：外部阻塞，本轨未触碰。
- **soak 的 ANR/内存/电池门**：脚本只采 crash/dropbox/monkey/duration/PSS（PSS 只记录不比较），
  soak PASS 不等于内存/电池稳定（与批次 50 同口径）。
- JVM 3E 同口径类（`RoomBackedStudyExperienceRepositoryTest`，40 例）点跑结果：
  `27-jvm-3e-room-backed-test.log` + `core/data/build/test-results/testDebugUnitTest/TEST-…RoomBacked….xml`。
  **40 例 / 1 红**：3E 相关 11 例全部 pass（含 `scanQuotaIsDailyCumulativeAcrossPlanCallsInTheSameStudyDay`、
  `scanQuotaResetsAtTheStudyDayBoundary`、`scanQuotaSpendsOnlyTheRemainingSlotsWithinTheSameDay`、
  `scanQuotaDoesNotChargeTodaysFirstTierReviews`、`scanQuotaFailsClosedWhenTheEvidenceReadFails`、
  `scanQuotaCountsRejectedObservationRowsAsSpent`）；唯一红 = **`profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes`**
  （`expected:<[knowledge:m1:math.read-monotonicity-from-graph]> but was:<null>`，断言在
  `RoomBackedStudyExperienceRepositoryTest.kt:1969` 的 `profile.knowledgePrerequisites[...]`）。
  该用例不使用本轨改动的 `knowledgeQuizEvidenceRow` 夹具（grep 仅在 863/911/926/976/1025/1073 行使用），
  **与本轨改动无因果关系**；单独点跑该用例仍确定性失败（`28-jvm-profile-test-isolated.log`：
  `tests=1 failures=1`，同一断言），排除测试顺序/并发偶发。它是主会话单测门的红，按实登记，本轨未修
  （超出设备范围）。
