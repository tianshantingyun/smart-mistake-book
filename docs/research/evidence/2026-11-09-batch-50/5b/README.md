# 5b 容量 UI（批次 50）证据

日期：2026-11-08（本会话实跑；计划 `.zcode/plans/plan-2026-11-09-batch-50-release.md`，产品面 item 29）
范围：`ReviewCapacityPreview` 挂进快照/复习页（今日容量、考试缺口、欠账、逐科 granted/unmet、
`intakeUnschedulableCount`），KD-68 一并关闭；**不改调度器语义**（未改
`ReviewPlannerV2` / `ReviewCapacityScheduler` / `ExamQuotaPlanner` / `MissedDayRebalancePolicy` /
`ReviewCapacityPolicy`，见 §5）。

## 1. 落点

| 文件 | 变更 |
|---|---|
| `core/domain/.../StudyReviewCapacity.kt` | **新增**：`StudyReviewCapacity` / `StudySubjectCapacity`（core/data 预览的 domain 镜像；feature 不能依赖 core:data） |
| `core/domain/.../StudyExperienceRepository.kt` | `StudyReviewOverview` 增 `capacity: StudyReviewCapacity? = null`（null = 没有容量事实） |
| `core/data/.../study/StudyExperienceMappers.kt` | `ReviewCapacityPreview.toStudyReviewCapacity()` 镜像 + `toOverview(capacity=)` 透传 |
| `core/data/.../study/StudySnapshotBuilder.kt` | 每次快照经 `StudyReviewPlannerService.reviewCapacityPreview` 派生容量事实（只读） |
| `core/data/.../study/StudyReviewPlannerService.kt` | `reviewCapacityPreview` 增可选 `precomputedIntakeUnschedulableCount`：快照已算过的 intake 计数不再重复读库/重复派生 |
| `feature/review/.../ReviewCapacityCopy.kt` | **新增**：容量区块与新题预览的纯文案函数（JVM 可测） |
| `feature/review/.../ReviewRoute.kt` | 渲染"复习容量"区块；新题预览消费 `intakeUnschedulableCount`（KD-68 改口） |
| `core/data/src/test/.../StudySnapshotCapacityPreviewTest.kt` | **新增**：快照接线 + 诚实空态 + 逐字段镜像（3 例） |
| `feature/review/src/test/.../ReviewCapacityCopyTest.kt` | **新增**：容量/欠账/逐科文案 + KD-68 拆分（10 例） |
| `app/src/androidTest/.../ReviewIntakeBacklogPreviewInstrumentedTest.kt` | 重写断言到现行文案 + **全超预算用例**（KD-68 关闭条件） |
| `app/src/androidTest/.../ReviewCapacitySectionInstrumentedTest.kt` | **新增**：容量区块渲染 / 无数据空态 / 空库不显示（3 例） |

## 2. 先红后绿（JVM）

红（实现前，缺符号；`red-compile.log`）：

```bash
./gradlew --offline --console=plain :core:data:compileDebugUnitTestKotlin :feature:review:compileDebugUnitTestKotlin
# exit=1
# e: .../ReviewCapacityCopyTest.kt:3:49 Unresolved reference 'StudyReviewCapacity'.
# e: .../StudySnapshotCapacityPreviewTest.kt:60:50 Unresolved reference 'capacity'.
```

绿（`jvm-green.log`）：

```bash
./gradlew --offline --console=plain \
  :core:data:cleanTestDebugUnitTest :core:domain:cleanTestDebugUnitTest :feature:review:cleanTestDebugUnitTest \
  :core:data:testDebugUnitTest \
    --tests "...StudySnapshotCapacityPreviewTest" \
    --tests "...ReviewCapacityPreviewReaderTest" \
    --tests "...StudyReviewPlannerServiceCapacityPreviewTest" \
    --tests "...StudySnapshotIntakeUnschedulableTest" \
    --tests "...StudyExperienceMappersIntakeTest" \
    --tests "...ReviewCapacitySchedulerTest" \
    --tests "...ExamQuotaPlannerTest" \
    --tests "...MissedDayRebalancePolicyTest" \
  :core:domain:testDebugUnitTest :feature:review:testDebugUnitTest
# exit=0, BUILD SUCCESSFUL
```

XML 统计（本会话）：`core/data` **39 tests / 0 failures / 0 errors / 0 skipped**（8 个定向类）；
`feature/review` **116 / 0 / 0 / 0**（整模块）；`core/domain` **742 / 0 / 0 / 1 skipped**
（整模块，`test` 任务；唯一 skip 是既有的 `simulation.ReviewCalibrationExperimentTest.run the frozen calibration matrix`，
与本批无关）。新增类逐例：`StudySnapshotCapacityPreviewTest` 3/3、`ReviewCapacityCopyTest` 10/10。

> 说明：红→绿的第一次绿跑里，`ReviewCapacityCopyTest` 有 2 例按 **NEW_INTRODUCE_SHARE=1/3**
> 修正期望值（原期望误按 1/4 手算：400 s 切片 → 4 题/日，43/4 → 11 天、45/4 → 12 天）。
> 这是测试作者算错，不是放宽断言：断言仍是逐字相等。
> `StudySnapshotCapacityPreviewTest` 的"无考试→null"一例先在代码上**真红过**：当时用
> `preview == EMPTY` 判空，而 `intakeUnschedulableCount=1` 让预览不等于 EMPTY，暴露了
> 判据错误；改为读取器自身的两道前置条件（窗口内无考试 + 再平衡未规划）后转绿。

## 3. 设备实跑（emulator-5590）

不用 `connectedLocalFirstDebugAndroidTest`（计划禁令）；用 `strictOffline` debug 变体 +
裸 `am instrument`，由 `tools/ci/am_instrument_recorder.py` 逐例落 XML 并给出 `EXIT`。

```bash
./gradlew --offline --console=plain :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest   # exit=0（assemble.log）
adb -s emulator-5590 install -r app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk              # Success
adb -s emulator-5590 install -r app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk  # Success
python -I tools/ci/am_instrument_recorder.py --serial emulator-5590 \
  --package com.tingyun.smartmistakebook.offline.test \
  --class com.tingyun.smartmistakebook.ReviewIntakeBacklogPreviewInstrumentedTest \
  --apk-sha 518e900eca73518baee816eaa27e7a1fea632121638e8c46a9a0d8932f946805 \
  --test-apk-sha c5d99a80686b5435ca90398684a45711f7624b078be7309178dc4ecbab255b3f \
  --label 5b-intake --out-dir docs/research/evidence/2026-11-09-batch-50/5b
# tests=4 evaluated=4 failures=0 errors=0 skipped=0 → PASS
```

```bash
python -I tools/ci/am_instrument_recorder.py --serial emulator-5590 \
  --package com.tingyun.smartmistakebook.offline.test \
  --class com.tingyun.smartmistakebook.ReviewCapacitySectionInstrumentedTest \
  --apk-sha 518e900eca73518baee816eaa27e7a1fea632121638e8c46a9a0d8932f946805 \
  --test-apk-sha c5d99a80686b5435ca90398684a45711f7624b078be7309178dc4ecbab255b3f \
  --label 5b-capacity-section --out-dir docs/research/evidence/2026-11-09-batch-50/5b
# tests=3 evaluated=3 failures=0 errors=0 skipped=0 → PASS
```

设备绑定：`boot_id=31ecd364-6b2a-4e52-9643-23e681696172`、app APK sha256 `518e900e…`、
test APK sha256 `c5d99a80…`（两份 json/xml/summary 均为本次实测产物）。
安装形态：`strictOffline` debug（applicationId `com.tingyun.smartmistakebook.offline`），
与在用的 `localfirst` 包**不同 app id**，未覆盖他线装置。跑完已 `adb uninstall` 两个
`.offline` 包，装置恢复为本会话开始时的包集合（`localfirst` + 三个既有 test 包）。

## 4. KD-68 关闭依据

- 复习页现有消费者：`ReviewRoute` 把 `overview.intakeUnschedulableCount` 传给新题预览；
  可调度题数 = 总数 − unschedulable，只对可调度部分给"每天约 X 题，约 M 天安排完"。
- 不可调度部分单列："另有 N 道题单次超过整日预算，需专项会话，不会进入每日计划"
  （testTag `review_intake_unschedulable`）。
- 全超预算：不出现任何"每天约 / 约 M 天"（设备用例
  `anAllOversizedBacklogPromisesNoDailyIntroduction` 断言 `每天约` 不存在）。
- 口径同源：`StudyReviewPlannerService.intakeUnschedulableCount` 与
  `NewIntroductionPolicy.previewSchedulability` 的 unschedulable 桶是同一谓词
  （`true duration > 整日预算`；见 `StudyReviewPlannerService.kt:296-315` 的 KDoc）。
  因快照只带 count/median、不带逐题时长，UI 侧对**可调度余量**仍用
  `NewIntroductionPolicy.preview` 的粗略算术（这是既有 API；见 §6 残余②）。

## 5. 未改调度器语义（可复核）

```bash
git status --porcelain -- \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityScheduler.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ExamQuotaPlanner.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/MissedDayRebalancePolicy.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityPolicy.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityPreviewReader.kt
# 输出为空（exit=0）→ 五个容量策略文件相对 HEAD 零改动
```

本轨实际改动的 6 个主/测试文件见 §1；其中没有排程/评分/配额的改动，唯一进入排程服务的
改动是 `reviewCapacityPreview` 的**可选**参数（默认 null，既有调用逐字不变；
`StudyReviewPlannerServiceCapacityPreviewTest` 4 例全绿）。快照新增的是只读派生，
不写计划、不改 `createReviewPlan` 的引入集合。

如实边界：`core/domain/ReviewPlanner.kt` 与 `ReviewPlannerV2.kt` 在本会话**开始时**即为 `M`
（他线在飞，初始 `git status` 可见），不是本轨改动；本轨未触碰这两个文件，也不宣称它们
的内容未变。

## 6. 残余 / 未验证（如实）

1. **`rebalanceEnabled` 生产恒 false**：`SchedulingSettingsStore` 没有该开关，快照预览走默认
   `false`，所以生产读面上"积压追赶：本轮未规划"是常态；planned 分支只有用例覆盖
   （本轨按计划"不改调度器语义"未接线开关，也未伪造规划）。
2. **欠账数的数据口径（读码发现，登记而非改语义）**：`ReviewCapacityPreview.rebalanceBacklogCount`
   在读取器里是 `facts.size`（**有记忆状态的题数**，即再平衡策略的作用域），不是"逾期题数"；
   `MissedDayRebalancePolicy` 用它算"剩余负载 ÷ 剩余学习日"，再用 `elapsedFraction ≥ 0.85`
   筛出可补题。UI 文案沿用读取器 KDoc 的"积压"一词。生产 rebalance 恒未规划，这行只会显示
   "本轮未规划"；若将来启用，数据侧应补"逾期/可补"真读数，不应把 `facts.size` 直接叫欠账。
3. **估算仍是算术而非逐日模拟**：快照只带积压 count/median；`previewSchedulability` 需要
   逐题时长。UI 已把 unschedulable 从估算中剔除，但"约 M 天"仍是 `preview` 的粗略算术，
   不是逐日模拟（KD-68 的关闭条件是"不再承诺不可调度项的日引入"，该条件已满足）。
4. **快照构建成本**：每次快照多一次 `readExams()` + 一次预算读取（纯数学，无写）；
   `precomputedIntakeUnschedulableCount` 避免了 intake 的二次读库。**未做基准**。
5. **未跑**：`:core:data`/`:core:domain` 全量之外的模块、lint、release/R8、2h soak、
   全设备矩阵；`connected*AndroidTest` 一律未跑（计划禁令）。
6. **未产出**：真机（仅 emulator）、截图、带真实考试数据的端到端页面截图（设备用例为
   Compose 夹具渲染，非播种真实库）。带考试的真实库快照接线由 JVM 用例
   （`StudySnapshotCapacityPreviewTest`）覆盖，未在设备上种入考试日历复跑。
7. **他线状态**：`RoomBackedStudyExperienceRepository.kt`、`SmartMistakeBookRoot.kt`、
   `KnowledgeReviewSession*` 等在本会话开始时即为 `M`（他线在飞），本轨未整文件抢占，
   未改动它们；`ReviewRoute` 与快照/映射文件在改动前为 clean。
