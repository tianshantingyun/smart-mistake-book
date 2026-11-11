# 批次 49 · P2-12 校准可见页：实现与证据

> 角色：app / 设置页工程师。范围 = 计划 §P2-12「校准可见页」一项。
> 本文件只登记**本会话实际执行**的命令与读数；未跑项在 §6 如实列出，不当作已通过。

## 0. 一屏结论

| 项 | 结果 | 证据出处 |
|---|---|---|
| 21 维参数 + 来源（本地拟合/默认/导入） | 上屏；+7 例 JVM 口径用例 | `SchedulingCalibrationViewBuilderTest`、`SchedulingCalibrationCopyTest`、设备用例（§4） |
| 最近拟合时间/样本/验证 logLoss vs 基线 | 持久化到 DataStore；跨进程读回有设备用例 | `SchedulingSettingsStoreInstrumentedTest`（设备 4/4） |
| per-reason 校准表 | 上屏，复用 `calibratePlannedReasons`（MODEL_JUDGED/REVEAL 不进） | `SchedulingCalibrationViewBuilderTest`、设备用例 |
| 来源校准 | 复用既有 `SourceCalibrationSection`（能力页同一页已展示，未重复实现） | `SecondaryScreens.kt:507-511`（本次未改） |
| G6 当前幅度与版本 | `+50%` / `review-planner-v4`，读单源常量；附 model-based 说明 | 设备用例 `scheduling_calibration_g6` |
| 「立即重拟合」「重置参数」 | 复用 `optimizeSchedulingParameters` / `setOptimizedParameters(null)`；点击链路有设备用例 | 设备用例 4/5 |
| 入口 | 能力页内新增面板，无新导航 | 设备用例 `theCapabilityPageHostsTheCalibrationSection` |
| DAO | **零新增**（`git status`：`RoomStudyDatabase.kt` 的改动来自他线，本线未触碰） | §5 |

## 1. 改动文件

**新增**

| 文件 | 作用 |
|---|---|
| `core/domain/src/main/.../SchedulingCalibrationView.kt` | 读模型 `SchedulingCalibrationView` / `FsrsParameterFit` + 纯函数 `SchedulingCalibrationViewBuilder` |
| `app/src/main/.../SchedulingCalibrationSection.kt` | Compose 面板 + 文案 helper（可 JVM 测） |
| `core/domain/src/test/.../SchedulingCalibrationViewBuilderTest.kt` | 7 例：来源判定、同切分损失比较、21 维、per-reason 口径、空账本 |
| `app/src/test/.../SchedulingCalibrationCopyTest.kt` | 7 例：点号小数、来源文案、拟合记录文案、缺值不冒充 0、200 门文案 |
| `app/src/androidTest/.../SchedulingCalibrationSectionInstrumentedTest.kt` | 8 例 Compose 语义断言（含能力页入口） |
| `core/data/src/androidTest/.../settings/SchedulingSettingsStoreInstrumentedTest.kt` | 4 例 DataStore 往返/清除语义 |

**修改（外科式 hunk，见 §5 共享树登记）**

| 文件 | 改动 |
|---|---|
| `core/domain/.../StudyExperienceRepository.kt` | `StudyExperienceRepository.schedulingCalibrationView()`（默认 null）；`SchedulingSettingsStore.parameterFit` + `recordParameterFit`（默认 null/no-op） |
| `core/data/.../StudySchedulingCalibration.kt` | `schedulingCalibrationView()` 一次装配；采纳成功后写 `FsrsParameterFit` |
| `core/data/.../settings/DataStoreSchedulingSettingsStore.kt` | 四个偏好键读写；`setOptimizedParameters` 任何写入都清旧记录 |
| `core/data/.../RoomBackedStudyExperienceRepository.kt` | 一行委托 override |
| `app/.../SecondaryScreens.kt` | `CapabilityScreen` 三个可选参数 + 面板挂载 |
| `app/.../SmartMistakeBookRoot.kt` | 能力页调用点接线（3 个参数） |
| `app/.../SchedulingSettingsScreen.kt` | **范围外修复**：恢复被 `ea4348f9` 静默回退的 `retentionFromSlider`（F-055/F-057 崩溃门，见 §5.2） |
| `core/data/src/test/.../StudySchedulingCalibrationTest.kt` | fake store 实现新接口；+4 例（记录时点/拒绝不动记录/页面视图/导入来源） |

## 2. 设计要点（每个新增都指认它消灭的失败）

| 新增 | 消灭的具体失败 |
|---|---|
| `FsrsParameterFit` + DataStore 三键 | 只存 21 维参数时，进程重启后"最近拟合时间/样本数"不可恢复，页面只能显示无时点的样本数 |
| 来源枚举 `LOCAL_FIT/DEFAULT/IMPORTED` | 备份恢复或更早版本写入的参数没有本地拟合记录；若一律标"本地拟合"，"最近拟合时间"会指向不存在的本地事件 |
| `SchedulingCalibrationViewBuilder` 一次装配 | 分开读六个口会让"参数"与"它的验证损失"来自两个时点，"vs 基线"不再是同一协议的比较 |
| 两个损失都用 `FsrsParameterOptimizer.validationLogLoss` | 用不同切分/不同样本集算当前与基线时，数字不可相减 |
| 缺值显示 `—` | 用 `0.0` 顶替损失会读成"完美预测"，比空白更误导 |
| `setOptimizedParameters` 清旧记录 | 重置/备份恢复后旧时点贴到新参数上，比没有记录更误导 |
| 面板留在能力页 | 新建导航体系会与现有 `Routes.Capability` 重复；能力页已是"学习预测效果 + 来源校准"的校准页事实主体 |

## 3. 定向 JVM（红→绿）

### 3.1 domain：先红

```text
$ ./gradlew :core:domain:test --tests "...SchedulingCalibrationViewBuilderTest" --console=plain
> Task :core:domain:compileTestKotlin FAILED      # 01-red-domain.log（新 API 不存在）
$ ./gradlew :core:domain:test --tests "...SchedulingCalibrationViewBuilderTest" --console=plain
7 tests completed, 4 failed                      # 02-red-behavioral.log
  · nothing fitted yet reports the default parameters and an empty fit record
  · a locally fitted set is attributed to the local fit with its own record
  · parameters without a local fit record are reported as imported not as a local fit
  · current and baseline validation losses are computed on the same split
```

首版 builder 故意只实现"能编译"的路径（来源恒 DEFAULT、基线恒 null），4 例按预期红；补齐来源判定与同切分基线后转绿。

### 3.2 绿

| 命令 | 结果 | 日志 |
|---|---|---|
| `./gradlew :core:domain:test --tests "...SchedulingCalibrationViewBuilderTest"` | `tests="7" failures="0"` | `03-green-domain.log` |
| `./gradlew :core:data:testDebugUnitTest --tests "...StudySchedulingCalibrationTest"` | `tests="8" failures="0"`（含 4 新例） | `10-data-test.log` |
| `./gradlew :app:testLocalFirstDebugUnitTest --tests "...SchedulingCalibrationCopyTest" --tests "...SchedulingRetentionSliderValueTest"` | 7 + 4 = 11 例，0 失败 | `05-app-copy-test.log` |
| `./gradlew :core:domain:test` 5 个类（见下） | 40 例 0 失败 | `13-domain-regression.log` |

```text
SchedulingCalibrationViewBuilderTest   tests=7   failures=0
SchedulingEvaluationHarnessTest        tests=24  failures=0
PlannedReasonCalibrationTest           tests=4   failures=0
ReviewPlannerScaleBenchmarkTest        tests=2   failures=0   # 三组金样逐位一致 → 排程行为未动
ReviewScoringWeightsSeamTest           tests=3   failures=0
```

说明：`SchedulingCalibrationCopyTest` 首次可编译时即绿（helper 与用例同批落地，未取得行为红）；
它的红证据只有编译前的 `Unresolved reference`（被 §5.2 的既有编译断裂挡在更早一层），如实登记。

## 4. 设备实跑（adb，未用 connected* 任务）

```text
$ adb -s emulator-5566 install -r -t app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk          # Success
$ adb -s emulator-5566 install -r -t app/build/outputs/apk/androidTest/localFirst/debug/app-localFirst-debug-androidTest.apk  # Success
$ adb -s emulator-5566 shell am instrument -w -e class \
    com.tingyun.smartmistakebook.SchedulingCalibrationSectionInstrumentedTest \
    com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
OK (8 tests)        # 09-device-run.txt

$ adb -s emulator-5566 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk   # Success
$ adb -s emulator-5566 shell am instrument -w -e class \
    com.tingyun.smartmistakebook.core.data.settings.SchedulingSettingsStoreInstrumentedTest \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
OK (4 tests)        # 12-data-device-run.txt
```

设备用例覆盖（8）：
1. 21 维 `w0..w20` 全部在屏且下标连续、拟合记录/验证损失/样本数/理由表/G6/版本文案；
2. 导入参数不冒充本地拟合；
3. 默认参数时"重置参数"置灰；
4. 点「立即重拟合」→ 回调恰好一次 → 未采纳文案 → 重新装配（provider ≥2 次）；
5. 点「重置参数」→ 回调一次 → "下次启动生效"文案；
6. provider 抛错 → 错误文案，不显示空参数；
7. provider 返回 null → "此版本暂不提供"，不显示空参数；
8. `CapabilityScreen` 内面板可达（入口在能力页，无新导航）。

首轮设备跑曾 1 红（`assertTextContains` 默认 `substring=false`，与"部分文本"意图不符；以及首例未传动作回调导致按钮不存在）。两处均为**用例写法**修正，断言点未放宽：改 `substring = true` 仍断言同一短语，首例补传回调后仍断言按钮存在且可用。

### 4.1 既有 `CapabilityScreenInstrumentedTest` 的 2 红：证明与本线无关

本线同时跑了同页的既有类，读数 **4/6 通过、2 红**（`14-existing-capability-device.txt`）：

```text
Error in syntheticTestRunsOnlyAfterTapAndShowsTheByokBoundary: performScrollTo() failed
  Expected ... contains '测试通过：图片读取和讲解都可用。'
Error in testResultIsRemovedAsSoonAsTheSavedFormIsEdited: 同上
```

为排除是本线引入，做了**等价回退对照**：临时撤销本线在 `SecondaryScreens.kt` / `SmartMistakeBookRoot.kt` 的全部 hunk（连同临时移除新增的 capability 页用例），重建双 APK 后重跑同一类：

```text
$ ./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest   # BUILD SUCCESSFUL
$ adb ... am instrument -e class ...CapabilityScreenInstrumentedTest ...
Tests run: 6,  Failures: 2        # 16-head-equivalent-capability-run.txt（与带本线改动时逐项相同）
```

结论：**同一类在 HEAD 等价构建上同样 2 红**，非本线引入。源码根因（读自当前 HEAD）：`SecondaryScreens.kt:598`
把 `verification.editCapabilityDescription()` 拼在同一句后面，测试夹具的
`ModelCapabilityVerification` 未给 `supportsMistakeEdit`（`ModelConfigurationStore.kt:57` 默认 false），
于是实际文案带后缀，而用例用 `onNodeWithText(...)`（默认 `substring=false`）做**精确匹配**。
本线未修复它（不在授权面内），登记为**既有 2 红**，请主会话决定是否补正则/`substring=true` 或带后缀断言。
回退用于对照后已**全部还原**并重建：最终 `SchedulingCalibrationSectionInstrumentedTest` = `OK (8 tests)`（`09-device-run.txt`）。


## 5. 共享树与范围外处置

### 5.1 他线在飞文件（只加外科 hunk，不整文件抢占）

开工时 `git status` 显示以下文件已有他线未提交改动；本线只在**远离其 hunk** 的位置加行，并在同一工作树上重新编译通过：

| 文件 | 他线未提交内容 | 本线新增位置 |
|---|---|---|
| `app/.../SmartMistakeBookRoot.kt` | KD-67 确认卡/升级接线（+278 行） | `CapabilityScreen(...)` 调用点（3 个参数） |
| `core/domain/.../StudyExperienceRepository.kt` | KD-67 选择装配口（+19 行 @583） | `SchedulingSettingsStore`（~801）/`plannedReasonCalibrations` 后 |
| `core/data/.../RoomBackedStudyExperienceRepository.kt` | KD-67 `selectKnowledgeReviewQuestion`（+27 行 @914） | `plannedReasonCalibrations` override 后（一行委托） |

未触碰：`core/database/.../RoomStudyDatabase.kt`、`SchedulingEvaluation.kt`、`ReviewPlannerV2.kt`、`AlgorithmConstants.kt`（后两者由 P1-9 裁决为"维持现值"，本线只读展示）。

### 5.2 范围外修复（必须登记）

`SchedulingRetentionSliderValueTest`（HEAD 内既有用例）引用 `retentionFromSlider`，但该函数在
`ea4348f9`（"收口全局界面与精简文案"）整文件重写后消失，滑杆回调退回裸 `value.toDouble()`：

- 后果 1：`:app:testLocalFirstDebugUnitTest` 在 HEAD 即编译失败（与本线无关的既有断裂）；
- 后果 2：F-055/F-057 的端点崩溃（0.97f→0.9700000286102295 越出 `SchedulingOptions` 的 0.7..0.97 band）在树上复活。

按 `587098f1` 原样恢复 `retentionFromSlider` + `coerceIn(0.7, 0.97)`（`SchedulingSettingsScreen.kt:36-56`、`:128`）。
证据：`git show 587098f1:app/.../SchedulingSettingsScreen.kt` 含该函数；`git log -- <file>` 最近一次为 `ea4348f9`。
**该改动是既有修复的恢复，不是本线产品决策**；不由本线决定是否保留，请主会话复核。

### 5.3 明文密钥（只报位置）

`local.properties` 含 `SMB_REAL_PROVIDER_*` 明文值（本次未读取其用途、未外传）。
该文件已被 `.gitignore:14` 忽略且 `git ls-files` 为空（未入库）。建议：轮换该 key 并改从环境变量注入。

## 6. 未验证 / 未运行（不当作已通过）

| 项 | 状态 | 说明 |
|---|---|---|
| 12 模块单测门 / 强门 / `acceptance_report --require-complete` | **未运行** | 计划 P3 范围；本线只跑定向类 |
| 真机（非模拟器）与 TalkBack 实测 | **未运行** | 只在 `emulator-5566`（API 34 x86_64）跑 |
| 真库（真实学习者数据）上的参数/损失读数 | **BLOCKED** | 本批零真库样本；页面在真库上只保证口径与空态，不产生真库结论 |
| 端到端设备截图（从首页进能力页） | **未运行** | 入口接线由 `CapabilityScreen` Compose 用例 + 编译覆盖；未走完整导航 |
| 既有 `CapabilityScreenInstrumentedTest` 的 2 红 | **既有红，本线未修** | 见 §4.1：HEAD 等价构建上同样 2 红；根因是精确匹配 vs 带后缀文案 |
| 全量 `:app:testLocalFirstDebugUnitTest` | **未运行** | 只跑本线 2 个类；其余既有用例状态未知 |
| 他线在飞测试类（`StudyReviewPlannerExamWindowTest` 等） | **未运行** | 属他线改动面，本线不代跑、不判红绿 |
| G6/权重是否应变更 | **不在本线** | P1-9 已裁决维持现值；页面只显示当前值并标注 model-based |

## 7. 复跑命令

```text
./gradlew :core:domain:test --tests "com.tingyun.smartmistakebook.core.domain.SchedulingCalibrationViewBuilderTest" \
    --tests "com.tingyun.smartmistakebook.core.domain.SchedulingEvaluationHarnessTest" \
    --tests "com.tingyun.smartmistakebook.core.domain.PlannedReasonCalibrationTest" \
    --tests "com.tingyun.smartmistakebook.core.domain.ReviewPlannerScaleBenchmarkTest" \
    --tests "com.tingyun.smartmistakebook.core.domain.ReviewScoringWeightsSeamTest" --console=plain

./gradlew :core:data:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.data.study.StudySchedulingCalibrationTest" --console=plain

./gradlew :app:testLocalFirstDebugUnitTest --tests "com.tingyun.smartmistakebook.SchedulingCalibrationCopyTest" \
    --tests "com.tingyun.smartmistakebook.SchedulingRetentionSliderValueTest" --console=plain

./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest :core:data:assembleDebugAndroidTest --console=plain
adb -s emulator-5566 install -r -t app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk
adb -s emulator-5566 install -r -t app/build/outputs/apk/androidTest/localFirst/debug/app-localFirst-debug-androidTest.apk
adb -s emulator-5566 shell am instrument -w -e class com.tingyun.smartmistakebook.SchedulingCalibrationSectionInstrumentedTest \
    com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5566 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
adb -s emulator-5566 shell am instrument -w -e class com.tingyun.smartmistakebook.core.data.settings.SchedulingSettingsStoreInstrumentedTest \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
```
