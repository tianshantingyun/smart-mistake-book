# 批次 49 · 设备轨证据（2026-11-08）

> 角色：设备工程师。范围 = 本 ask：`assembleDebugAndroidTest` + `adb install -r -t` + `am instrument`；
> 四项设备面（2h soak、KD-67 确认卡 UI、校准页 UI、冲突页/重整理 UI、review_log 最近窗口 DAO 用例）
> 与 APK sha / 源快照绑定。
>
> 口径：下文每条结论都对应本会话实跑命令与日志文件；未跑项在 §7 单列。
> 日志均为原始重定向文件，未二次编辑。

## 0. 一屏结论

| 项 | 结果 | 证据 |
|---|---|---|
| APK 构建（app + app androidTest + core:database androidTest） | `BUILD SUCCESSFUL` | `01-build-red-phase.log`、`05-build-green-phase.log` |
| KD-67 确认卡 + 冲突卡 + 投影重整理（新增 8 例设备 UI） | **8/8 PASS**（修复后） | `06-instrument-green-phase.log` |
| 冲突卡「仍然复习 / 先做校准题」收起接线 | **红 2 例 → 修 1 行 → 绿 2 例** | `04-instrument-red-phase-2.log`、`06-instrument-green-phase.log` |
| 校准可见页 UI（既有 8 例） | **8/8 PASS** | `07-instrument-calibration-page.log` |
| review_log 最近窗口 DAO（点名用例） | **1/1 PASS** | `08-instrument-review-log-window.log` |
| review_log 所在类回归（4 例） | **4/4 PASS** | `09-instrument-review-log-class-regression.log` |
| 既有会话屏设备回归（4 例，修 1 行后） | **4/4 PASS** | `11-instrument-session-regression.log` |
| feature/review 单测回归（修 1 行后） | 101 tests / 0 failures | `10-feature-review-unit-regression.log` |
| 2h soak（F-055/F-057） | **完整 7200s，117 轮，0 新崩溃/0 重启**（PSS 增长见 §6） | `soak/soak-summary.txt`、`soak/soak-samples.csv` |
| APK sha 与源快照 | 已绑定 | `00-snapshot-and-device-meta.txt` |

## 1. 任务名事实：本工程没有 `assembleDebugAndroidTest`

计划要求 `assembleDebugAndroidTest`。本工程 `app` 有 `localFirst` / `strictOffline` 两个
flavor，该任务名在 `:app` 是**歧义**的，Gradle 直接拒绝：

```text
$ ./gradlew :app:assembleDebugAndroidTest --dry-run
Cannot locate tasks that match ':app:assembleDebugAndroidTest' as task 'assembleDebugAndroidTest'
is ambiguous in project ':app'. Candidates are: 'assembleLocalFirstDebugAndroidTest',
'assembleStrictOfflineDebugAndroidTest'.
EXIT=1
```

（`00-assembleDebugAndroidTest-task-name.log`）

因此实际执行的是**该任务在设备轨所用 flavor 下的全名**，与计划意图一致：

```text
$ ./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest \
    :core:database:assembleDebugAndroidTest --console=plain
BUILD SUCCESSFUL in 28s
293 actionable tasks: 8 executed, 285 up-to-date
```

（`01-build-red-phase.log`；`core:database` 无 flavor，`assembleDebugAndroidTest` 合法。）

## 2. 被验证的四个设备面与新增用例

### 2.1 新增文件（本 ask 唯一新增测试）

`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/Batch49ReviewCardsAndReprojectInstrumentedTest.kt`

| # | 用例 | 钉住的事实 | 消灭的具体失败 |
|---|---|---|---|
| 1 | `clarificationCardConfirmsTheDisplayedQuestionOnlyAfterAnswering` | 确认卡上屏；未作答时确认**不可点**且文案/无障碍描述正确；作答后可点；确认只带屏幕上这道题的 id | 确认卡只有 JVM 状态机测试，屏幕是否真挂上、按钮是否真按"已作答"放行，设备上从未验证 |
| 2 | `clarificationCardWithoutTheKeyReasonOffersNoConfirmButton` | 非"键未核验"原因下不出现确认按钮，只给换题/取消 | 所有原因共用一张卡会让学生在"非答案问题"上点一个永远无效的确认 |
| 3 | `conflictedKnowledgeNodeShowsTheOptionalCardAndStillLoadsTheQuiz` | 冲突卡上屏、两动作可点、题面照常加载、可作答 | 卡是否阻断取题/作答只在 JVM 断言，设备未验证 |
| 4 | `continueReviewDismissesTheConflictCard` | 点「仍然复习」后卡**必须消失** | **本次抓到真断链**：屏幕只按 `entry.isConflicted` 算模型，从不读 `viewModel.showConflictCard`，学生点完动作卡不消失 |
| 5 | `startCalibrationDismissesTheCardAndMarksThisRoundAsCalibration` | 点「先做校准题」后卡消失、显示"本次按校准题处理"、回调收到当前节点 | 同上（第二动作同样点了没反应） |
| 6 | `projectionConflictBannerReprojectsThroughTheInjectedAction` | 冲突投影卡上屏；「重新整理」走注入的 `reproject`；按钮无障碍描述 | 「重新整理」只有仓库层 JVM 用例，设备上没有一条"学生点了真的会调"的证据 |
| 7 | `projectionConflictBannerSaysNothingChangedWhenReprojectFails` | 重整理失败必须说"没有改变" | 失败静默/假成功会把"没修好"读成"已修好" |
| 8 | `projectionGapBannerExplainsTheMissingSegmentDistinctly` | 缺口与冲突原因句不同 | 两种账本事实共用一句"数据出错了"等于没说 |

设计取舍：用例 1/3/4/5 渲染的是**真实** `KnowledgeReviewSessionScreen`（含真实
`KnowledgeReviewSessionViewModel` 与 `LaunchedEffect` 取题），不是孤立 Composable；
状态由公开参数 `clarificationState` 与队列项 `reasons` 注入，与生产接线同一入口。

### 2.2 红 → 绿（同 APK 绑定）

1. **红**（修复前 app APK sha `ace60e07187822e2ff45bb81936d5b47c4d2003b815d5cbb57eceabba9175b55`）：
   `04-instrument-red-phase-2.log` → `Tests run: 8, Failures: 2`，恰好是用例 4、5：
   `Did not expect any node but found '1' node that satisfies: (TestTag = 'knowledge_conflict_card')`。
   其余 6 例已绿（证明红只来自接线缺陷，不是夹具问题）。
2. **修复**（1 行，`feature/review/.../KnowledgeReviewSessionScreen.kt:234`）：

   ```kotlin
   model = if (viewModel.showConflictCard) knowledgeConflictCardModel(entry) else null,
   ```

   依据是既有契约而非新语义：`KnowledgeConflictSessionStateTest` 已断言
   `dismissConflictCard()` / `startCalibration()` 后 `showConflictCard == false`
   （`KnowledgeConflictSessionStateTest.kt:84,96`），屏幕此前没有消费这个状态。
3. **绿**（修复后 app APK sha `ebb3aa340f071bb309edb1420220a9530e9734c1f34993fa91d5b8abd2244cee`，
   test APK `8629a05dbc7883c33ea9018746cc750d113fba8ac9e5d717a622660aead4d6ea`）：
   `06-instrument-green-phase.log` → `OK (8 tests)`，`Time: 14.753`。

> 第一次红跑（`02-instrument-red-phase.log`，8 run / 4 failures）里另有 2 例是**测试自身**未滚动/
> 未选答案导致的假红；已修正测试（加 `performScrollTo`、先选项再断言提交可用），
> 以 `04` 作为红相证据。没有放宽任何断言语义。
>
> 其余 6 例（KD-67 卡两例、冲突卡可用性、投影重整理三例）首跑即绿：被测实现已由
> P2-11/12/13 轨在树内落地（各自有 JVM 红→绿记录），本 ask 的设备红相只来自用例 4、5
> 抓到的那处接线缺陷——不把"首跑绿"冒充"测试先红"。

### 2.3 校准页 UI（既有用例，本 ask 只负责设备执行）

```text
$ adb -s emulator-5566 shell am instrument -w \
    -e class com.tingyun.smartmistakebook.SchedulingCalibrationSectionInstrumentedTest \
    com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
OK (8 tests)   Time: 15.921
```

覆盖 21 维参数、来源（本地拟合/导入/默认）、最近拟合与验证损失 vs 基线、per-reason 表、
G6 幅度与排期器版本、两个动作回调、能力页入口（`theCapabilityPageHostsTheCalibrationSection`）。
日志：`07-instrument-calibration-page.log`。

### 2.4 review_log 最近窗口 DAO（核心 P0-2）

```text
$ adb -s emulator-5566 shell am instrument -w \
    -e class "com.tingyun.smartmistakebook.core.database.MasterySchedulingMigrationInstrumentedTest#recentReviewLogWindowKeepsTheNewestRowsInAscendingOrder" \
    com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
OK (1 test)   Time: 0.517
```

同类全量回归：`OK (4 tests)`（`09-instrument-review-log-class-regression.log`）。
用例本体在 `MasterySchedulingMigrationInstrumentedTest.kt:126-158`：3 行 + `limit=2` 必须返回
**最新两行**且**升序**（内层 DESC 取窗、外层 ASC 返回）。

## 3. 构建与安装（逐条命令）

```text
$ ./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest \
    :core:database:assembleDebugAndroidTest --console=plain          # 01 / 05
$ adb -s emulator-5566 install -r -t app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk
$ adb -s emulator-5566 install -r -t app/build/outputs/apk/androidTest/localFirst/debug/app-localFirst-debug-androidTest.apk
$ adb -s emulator-5566 install -r -t core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
$ adb -s emulator-5590 install -r -t app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk   # soak
```

三次安装均为 `Performing Streamed Install / Success`。测试 APK 包名
`com.tingyun.smartmistakebook.localfirst.test`，runner `androidx.test.runner.AndroidJUnitRunner`。
**未使用** `connectedLocalFirstDebugAndroidTest`（硬约束）。

## 4. APK sha 与源快照（绑定）

`00-snapshot-and-device-meta.txt` 原文摘要：

| 项 | 值 |
|---|---|
| HEAD | `ba7b5624ea0c2bc3f09dcee247f5852e4f428122`（main） |
| 脏文件数 | 643（共享树；含本轮全部轨道未提交改动） |
| `git diff` sha256 | `1546825ad7495090a18b0e65913b79ed3b8a6f73da4bfa904f749578439ac03f` |
| `git status --porcelain` sha256 | `7ff5c72b5e762b27d14c7e872c03c51003f17b2b54c8bebfac0122ad47ab16cd` |
| app APK（最终，绿相与 soak） | `ebb3aa340f071bb309edb1420220a9530e9734c1f34993fa91d5b8abd2244cee`（190,611,130 B） |
| app APK（红相，修复前） | `ace60e07187822e2ff45bb81936d5b47c4d2003b815d5cbb57eceabba9175b55` |
| app androidTest APK | `8629a05dbc7883c33ea9018746cc750d113fba8ac9e5d717a622660aead4d6ea`（1,867,500 B） |
| core:database androidTest APK | `2c3c1913a1885aacbc0f56554d7108c3f2ef9995e3a705016da2fb620e59f926`（198,429,168 B） |

设备：`emulator-5566` / `emulator-5590`，`sdk_gphone64_x86_64`，API 34，
fingerprint `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`，
安装包 `versionName=0.2.0-localfirst`、`versionCode=2`。

**设备侧回读**（不是只信任本地文件）：两台设备 `pm path` → `adb pull base.apk` → `sha256sum`
均为 `ebb3aa340f071bb309edb1420220a9530e9734c1f34993fa91d5b8abd2244cee`，与构建产物一致。
因此 §0/§2 的绿相与 §6 soak 都钉在同一个 APK 字节上。

> 说明：APK 是从**当前脏树**构建的，不是 HEAD。sha + `git diff` 摘要共同构成源快照；
> 重跑需在同一工作树状态。构建后本 ask 只改了两处（新增测试文件、1 行屏幕接线），
> 该改动已包含在最终 APK sha 中。

## 5. 本 ask 改动的文件

| 文件 | 改动 | 归属/风险 |
|---|---|---|
| `app/src/androidTest/.../Batch49ReviewCardsAndReprojectInstrumentedTest.kt` | **新增** 8 例设备 UI | 新文件，无冲突 |
| `feature/review/src/main/.../KnowledgeReviewSessionScreen.kt:234` | 1 行：冲突卡模型按 `viewModel.showConflictCard` 决定；附 4 行注释 | 他线（P2-14）文件；只插入该表达式，未动其它行 |

未改：掌握阈值、G6/权重、`ReviewPlannerV2.VERSION`、金样、台账、`RoomStudyDatabase.kt`/DAO。

## 6. 2h soak（F-055 / F-057）

- 命令：`bash soak/f-soak.sh emulator-5590 7200 soak`（脚本随证据存档）
- 启动：2026-11-07T18:04Z（本地 02:04），目标 7200s；包 `com.tingyun.smartmistakebook.localfirst`，
  最终 APK sha `ebb3aa34…`。
- 采样：`soak/soak-samples.csv`（每轮 monkey 300 events + `dumpsys meminfo` + dropbox + crash 行数）。
- **结果（`soak/soak-summary.txt` + CSV 原文）**：

  ```
  duration_target_s=7200
  cycles=117                       # 117×300 = 35,100 个 monkey 事件
  total_pss_min_kb=176669 total_pss_max_kb=242118   # 末轮 225,466
  dropbox_first=2  dropbox_last=2                    # 无新增
  crash_lines_first=19 crash_lines_last=19           # 无新增
  EXIT=0
  ```

  逐行复核（本会话 python 读 CSV）：117 轮**同一 pid `28350`**（进程从未重启），
  `monkey_rc != 0` 的轮数为 **0**，首轮 elapsed=6s、末轮 7188s（随后到达 7200s 退出）。
  结束时刻复查设备侧 `logcat -b crash` 行数 19、dropbox 命中 2，与首轮相同。

- **判定（只陈述事实，不替验收台账改 verdict）**：本轮 2h 完整跑完，崩溃面
  （新 FATAL / 进程重启 / monkey 异常退出）**没有复现**；F-055（长时 2h 基线）与
  F-057（2h 内至少 1 次 FATAL，超限路径）在批次 44 的崩溃证据在本轮不成立。
  → 需要验收轨按新证据重判；本文件不写 PASS/FAIL。
- **残余风险**：总 PSS 从 176,669 kB 升到 242,118 kB（末轮 225,466 kB，约 +49 MB）。
  这**不是**"已证明内存泄漏"，但若 F-050 的 2h 内存判据要求平稳，需要单列归因；
  本轮不据此宣称内存无问题。

> 判定口径：2h 内 `dropbox` 命中/`logcat -b crash` 行数增量表示新崩溃；`monkey_rc != 0` 表示
> monkey 因异常退出。只有两者都无增量才可称"2h 无 FATAL"。PSS 列在 API 34 的
> `smaps_rollup` 上取不到（脚本 `grep '^Pss:'` 为空，`pss_samples=0`），总量 PSS 来自
> `dumpsys meminfo TOTAL PSS`。

## 7. 未验证 / 未覆盖（如实登记）

1. **KD-67 的 app 级接线未在设备上跑**：本 ask 的设备用例从 `KnowledgeReviewSessionScreen` 的
   公开入口注入 `clarificationState`，验证"卡 → 按钮 → 回调"；`SmartMistakeBookRoot` 里
   `selectKnowledgeConsume` → presenter → 状态流的真实链路没有设备夹具（需要真实选择器/账本数据）。
2. **冲突队列的真实来源未在设备上跑**：用例用 `reasons = {CONFLICTED_KNOWLEDGE}` 构造队列项；
   planner 打分 → reasons → 队列的设备级链路未跑（JVM 侧有）。
3. **2h soak 只覆盖 x86_64 模拟器**（`emulator-5590`，API 34）；不外推到 arm64/真机。
   本轮 PSS 有约 +49 MB 增长，未做归因（见 §6 残余风险）。
4. **截图/无障碍专项（修复环已补）**：`screenshots/` 落四张 PNG（冲突卡/确认卡/校准页/投影冲突卡），
   `a11y/batch49/` 存 UiAutomation 语义树；`tools/ci/a11y_audit.py audit` 四屏 STRUCTURAL=PASS，
   CONTRAST=UNVERIFIED（无颜色输入）。绑定见 `12-batch49-repair-artifacts.device.json`
   （APK `e19f1b22…`、test APK `0252bc24…`、17/17 绿）。注意：`uiautomator dump` 子命令在
   仪器化进程中返回空，已改为直读 `UiAutomation.rootInActiveWindow`。
5. `-e log true` 的尝试（逐用例名）在本 runner 上返回 `Time: 0.016 / OK (8 tests)`——
   **未真正执行**，已删除该日志，不作为证据。
