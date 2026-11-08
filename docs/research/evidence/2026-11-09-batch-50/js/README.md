# 批次 50 / JS 线：math 显式降级、KD-78 ①②/④ 核销、生产装配核查（2026-11-09）

> **性质**：批次 50 计划任务 31（`JS 生产装配：visualEngine 注入、capability 广告、math 重放、KD-78 ①②`）
> 的 JS 子线收口。**门阈值零改动、断言零放宽、生产能力广告零改动、未提交。**
> HEAD `a87fc70d15c8ad726cee17422dbeef0b4977747d`（开工 `git rev-parse HEAD` 实读）。
> 设备 `emulator-5590`（API 34 / x86_64 / PAGE_SIZE 4096 / boot_id
> `31ecd364-6b2a-4e52-9643-23e681696172`）；**裸 `adb install` + `am instrument`，未跑
> `connectedLocalFirstDebugAndroidTest`**。

## 0. 结论（先给门判）

| 项 | 结论 | 证据 |
|---|---|---|
| math 盒回放 | **显式产品降级**（不是"本批未做"）：保持 `PAINTER_LABEL_MATH` fail-closed，诊断点明 degrade 语义；设备先红后绿 | §1、`red-math-degrade.log` / `green-math-degrade.log` |
| KD-78 ① stroke-only | **核销**：生成侧两道门同码拒绝 + Node/JVM 负向矩阵 + 回放兜底设备绿 | §2 |
| KD-78 ② dash 形状 | **核销**：同上（偶数且 ≥2，含空数组） | §2 |
| KD-78 ④ flags 指纹 | **维持关闭**：painter 24/24（含真实回放 Paint flags=0）+ 跨侧 flags 2/2 | §3 |
| KD-78 整条 | **关闭（①②③④ 全满足）** | §2/§3 + 批次 29/30 既有 ③ 设备像素证据 |
| visualEngine 生产注入 | **未注入（门未过）**：MVA FAIL + KD-76 open + 无宿主帧源；3 参装配保持 JS 恒 UNAVAILABLE | §4 |
| capability 广告 | **保持不广告**：生产 6 处能力集合全 `IMAGE_MODEL_ONLY`；引用 MVA 证据 §7 | §4 |
| KD-76 / KD-71 / KD-73 / KD-82 | 按现证据登记，**均保持 open** | §5 |

## 1. math 内容重放：显式降级（先红后绿）

### 1.1 裁定依据（为什么不是实现）

三件事实同时成立（本轮实读）：

1. **artifact 不携可画内容**：`VisualMeasuredLayoutArtifact` 对未降级 math 只携
   width/height/baseline，`lines == null`（`VisualMeasuredLayoutArtifact.kt:44-46` 的
   init 不变式；`:32` 消费表）。`VisualLabelContent.Math` 的 `box` 在生产路径**没有来源**。
2. **几何唯一来源约束**：契约 `docs/current/visual-generation-contract.md:131` 明令
   "不得继续增加第三套公式/坐标绘制逻辑"；盒几何已有 core/ui Compose lowering 与
   core/export `CanvasMathBoxRenderer` 两套镜像。
3. **生产回放不会构造该 DTO**：预览在折算前即以同码拒绝
   （`TutorVisualPreviewRenderer.kt:297-309`）；契约降级的 math 已是 text 布局
   （`VisualContentMeasuring.kt:459-466`，`degraded=true`），按普通 label 回放。

因此按任务书"实现**或明确降级**"选后者，并把"暂时未写"改成可审计的产品语义。

### 1.2 先红

`VisualDrawOpsPainterInstrumentedTest.aMathLabelFailsClosedThisBatch` 先加断言
（`VisualDrawOpsPainterInstrumentedTest.kt:477-480`）：诊断必须含 `explicitly degraded`。

```text
$ adb -s emulator-5590 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$ adb -s emulator-5590 shell am instrument -w -r \
    -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest#aMathLabelFailsClosedThisBatch \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
Tests run: 1,  Failures: 1
java.lang.AssertionError: math degrade diagnostic must name the explicit degrade, got:
  PAINTER_LABEL_MATH: ops[0]: math box replay is not implemented this batch (fail-closed, R1 §3.6)
```

原始输出：`red-math-degrade.log`。

### 1.3 绿的落码

- `VisualDrawOpsPainter.kt:194-202`：诊断改为
  `math box replay is explicitly degraded in V1 — the frozen layout artifact carries no
  MathBox/payload, so there is no paintable lowering; fail-closed by contract (no third
  geometry walker; see VisualMeasuredLayoutArtifact / core/ui + core/export single source)`。
- `VisualDrawOpsPainterPure.kt:53-62`：`PAINTER_LABEL_MATH` KDoc 改写为显式降级 + 解锁前置。
- `VisualDrawOpsPainterPure.kt:223-231`：`VisualLabelContent.Math` KDoc 点名生产路径不构造。
- `VisualMeasuredLayoutArtifact.kt:32`：消费表行改为"V1 显式产品降级 + 解锁前置"。
- `docs/current/visual-generation-contract.md` §5 新增第六条（math 盒回放显式降级）。

```text
$ adb -s emulator-5590 shell am instrument -w -r -e class ...#aMathLabelFailsClosedThisBatch ...
Time: 0.057
OK (1 test)
```

原始输出：`green-math-degrade.log`。

## 2. KD-78 ①② 核销（生成门 + 负向矩阵 + 回放兜底）

### 2.1 两道生成门（现树实读）

| 门 | 编译器（JS） | 原生解析器 |
|---|---|---|
| ① stroke-only | `core/data/src/main/resources/visual/visual-compiler.js:892-897` `fail('PAINTER_STROKE_WITHOUT_FILL', …)` | `VisualDrawOpsParser.kt:442-454` 同码拒绝 |
| ② dash 形状 | `visual-compiler.js:483-492` `dash.length < 2 || dash.length % 2 !== 0` → `PAINTER_DASH_ODD` | `VisualDrawOpsParser.kt:559-567` 同判据同码 |

回放侧兜底保留（批次 29 裁定）：`VisualDrawOpsPainter.kt:151-162` 先 dash 形状后
stroke-only，与生成侧同码同序。

### 2.2 负向矩阵实跑

```text
$ node tools/visual_compiler/run_tests.mjs
94 passed, 0 failed          # EXIT=0（含 run_tests.mjs:436-451 的 stroke-only/奇数/单项/空数组
                             # 负例与 :455-461 的 [2,2,4,4] 负控）；完整输出 node-compiler.log
```

```text
$ ./gradlew.bat :core:data:testDebugUnitTest --offline --no-parallel --console=plain \
    --tests "*VisualDrawOpsValidatorTest" --tests "*VisualDrawOpsParserTest" --tests "*VisualDrawOpsPainterPureTest"
BUILD SUCCESSFUL
```

JUnit XML（本轮实跑，`2026-11-08T11:2xZ`）：

| 套件 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `VisualDrawOpsValidatorTest` | 35 | 0 | 0 | 0 |
| `VisualDrawOpsParserTest` | 26 | 0 | 0 | 0 |
| `VisualDrawOpsPainterPureTest` | 13 | 0 | 0 | 0 |

XML 原文已复制到本目录。

### 2.3 回放兜底设备证据

```text
$ adb -s emulator-5590 shell am instrument -w -r \
    -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
Time: 0.317
OK (24 tests)
```

24 例含：`aStrokeOnlyPathStillFailsClosedInTheReplayFallback`（`:363`）、
`aStrokeOnlyPathWithAnOddDashReportsTheSameFirstGateAsTheGenerators`（`:428`，期望
`PAINTER_DASH_ODD`）、8 位色 alpha 像素正例（`:253`）、真实回放 Paint flags（`:264`）。
原始输出：`painter-suite.log`；APK sha256 `43507ed8…`（`data-debug-androidTest.apk`）。

**KD-78 ① ② 判定：核销。** 契约条文（§5 四条）+ 两道生成门同码同序 + Node/JVM 负向矩阵
+ 回放兜底设备绿全部成立；无未满足项。设备为 x86_64/API 34 模拟器口径（arm64/真机未跑，
不属本条判据）。

## 3. KD-78 ④：flags 指纹跨侧复核（现树设备实跑）

```text
$ ./gradlew.bat :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest ...
$ adb -s emulator-5590 install -r -t app-localFirst-debug.apk
$ adb -s emulator-5590 install -r -t app-localFirst-debug-androidTest.apk
$ adb -s emulator-5590 shell am instrument -w -r \
    -e class com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest \
    com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
Time: 0.028
OK (2 tests)
```

APK：app `b4c20120…`、androidTest `35dcf6d1…`。同轮 app JVM 度量/指纹套件：
`VisualContentMeasuringTest` 16/0、`VisualMeasureEnvironmentBudgetTest` 5/0、
`VisualMeasuredLayoutInkBoundsTest` 9/0（XML 原文在本目录）。painter 侧
`theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint` 在
24/24 内绿。**④ 维持关闭（批次 30 裁定 + 本轮现树复验）。**

## 4. 生产装配核查：visualEngine 未注入、JS 不广告（门未过）

### 4.1 门状态

- **MVA 门 FAIL（不可评）**：`docs/research/evidence/2026-11-09-batch-50/mva/README.md`
  §0/§5/§7（**P7 = 该文 §7「JS 不广告（核验，非声明）」**）；四道比率门 0 可评样本，
  JS 臂 `NOT_AVAILABLE`。
- **KD-76 open（高）**：冷启动首个 execute 误杀缺口未关闭（见 §5）。
- **广告门规**：`availableMethods` 含 JS 是"全门绿后"最后一步
  （`FigurePromptAssembler.kt:19-23`）；MVA 未过 → 本批不接通。

### 4.2 生产装配现状（本轮实读）

| 事实 | 证据 |
|---|---|
| 生产装配只传三参 | `SmartMistakeBookApplication.kt:386-392`：`FigureGenerationCoordinationFactory.create(database, configurationStore, networkRequestsAllowed)` |
| 工厂的 `visualEngine`/`visualFrameInputs` 默认 null | `DefaultFigureGenerationCoordinator.kt:886-903`（`create` 签名与默认值） |
| 协调器无引擎即回 UNAVAILABLE | `DefaultFigureGenerationCoordinator.kt:274-280`（`generateLocalJs` 首段） |
| 引擎类自述无生产装配点 | `VisualJsClientEngine.kt:92-93`（"本批不广告 JS：本类没有生产装配点"） |

### 4.3 能力广告（保持关闭）

```text
$ git grep -n "FigureGenerationCapabilities.IMAGE_MODEL_ONLY" -- 'core/data/src/main/kotlin'
ConfiguredCleanImageGenerator.kt:95
RoomDetailedSolutionRepository.kt:256
RoomMistakeEditRepository.kt:258
AttachedImageGenerator.kt:144
FigurePromptAssembler.kt:124（默认参数）
TutorFigureGenerator.kt:372
```

6 处全部 `IMAGE_MODEL_ONLY`（MVA README §7 列了其中 4 处；另 2 处本轮实读补齐，结论不变）。
**JS 不广告成立。** `FigurePromptAssembler.kt:19-23` 明写"`availableMethods` 接通是
'全门绿后'最后一步"。

### 4.4 为何本批不注入（指认它消灭的失败）

在当前门状态下注入引擎 + 编造宿主帧源，会把"能力误广告 ⇒ 本地执行未验收路径"从
**结构性不可达**变成**可达**：现状任何入口误传 JS 能力时，协调器因 `visualEngine=null`
仍回 UNAVAILABLE（fail-safe）；注入后则会真的进引擎。两种做法都不改变正常产品行为，
但后者在 MVA FAIL/KD-76 open 时扩大了暴露面，因此**不注入**（与批次 43 证据
`ws2-kd76-a6.md` §0-6 的 BLOCKED 判据一致：产品进程首请求必须同时改 Application 与
翻转广告面，而广告面须 MVA 通过）。

## 5. KD-76 / KD-71 / KD-73 / KD-82 登记依据

### KD-76（保持 open）

- 现树：`READY_TIMEOUT_MILLIS = 10_000L`（`VisualJsClientEngine.kt:1325`）；校准用例门
  `READY_TIMEOUT_MILLIS / 2 == 5_000L`（`VisualJsConnectStageCalibrationInstrumentedTest.kt:683`）。
- 最新证据（转录，非本轮实跑）：批次 43 A6 两批各 n=19、各一轮产品可见
  `HARD_DEADLINE_EXCEEDED` → **NOT EVALUABLE**（`docs/research/evidence/2026-11-05-batch-43/ws2-kd76-a6.md` §0/§5）；
  批次 45 的 10+10 形状被三个工具各自 exit 2 → **UNVERIFIED**
  （`docs/research/evidence/2026-11-06-batch-45/ws7/00-index.md` §1）。
- 本批未跑 KD-76 校准；painter/flags 设备绿不是 KD-76 证据。

### KD-71（保持 open）

- 最新设备证据：批次 40 `QuickJsVisualExecutionEngineInstrumentedTest` **3×`OK (27 tests)`**，
  G1 源绑定成立；**G4（变异/回退红向敏感性）未做**
  （`docs/research/evidence/2026-11-02-batch-40/step06-kd71-closeout.md`）。
- 本批零改动、零新证据；条件式关闭文案边界不变（模拟器口径 / G4 未做 / #240 上游 open /
  close 返回不代表无 JS 在执行）。

### KD-73（保持 open）

- 本轮实跑静态门：`python -I tools/ci/check_stdlib_compat.py` →
  `OK: 0 unresolved stdlib member references across 23859 unique kotlin/* refs (389 artifacts; 8 coordinates unscanned)`、
  **EXIT=0**；`python -I tools/ci/check_dependency_pins.py` → exit 0（quickjs 1.0.15 / stdlib 2.3.21）。
- 数值与批次 36/40 的 23,847/329/0 不同：本轮解析图来自现树未跟踪锁（脚本自打印 NOTE，
  图随工作树变化），**以本轮实读为准**。
- Reopen 的另一半"设备/R8 冒烟无 stdlib 归因崩溃"本批未做 → **保持 open**。

### KD-82（保持 open，known limitation）

- 两个广告面均已按 Fix direction ① 收紧：`imagePrompt` `MAX_IMAGE_PROMPT_UTF8_BYTES / 4`
  = 1024 码点（`OpenAiModelProtocol.kt:562`）、`program` `MAX_PROGRAM_UTF8_BYTES / 4`
  = 16384（`:600`）；本地字节门零改动。
- JVM 本轮实跑 `ProgramSchemaMaxLengthAlignmentTest` **7/0/0/0**（XML 在本目录）。
- 仍缺"广告解冻批"对 imagePrompt 半边的产品裁定（KD-82 关闭条件原文）→ **保持 open**；
  禁令"不得把现 schema 写成与本地门同一口径"继续有效。

## 6. 未做 / 未验证（如实）

1. **未实现 math 盒回放**：本批裁定为显式降级（§1.1）；盒回放需先扩 artifact 形状并与
   core/ui、core/export 几何逐点对齐。
2. **未注入生产 visualEngine**、未构造生产 `VisualFrameInputs`；JS 生产路径仍恒 UNAVAILABLE
   （这是本批的有意结论，不是遗漏）。
3. **未跑 arm64/真机**：全部设备数字为 `emulator-5590` x86_64/API 34 模拟器口径。
4. **未跑 KD-76/71/73/82 的关闭门**（校准/变异敏感性/R8 冒烟/广告解冻裁定）；只做登记。
5. **未跑 full soak / 全模块单测 / release 构建**（属批次 50 其他子线）。
6. 本目录日志为单轮实跑；不是统计样本。

## 7. 可复跑命令

```bash
# JVM（串行）
./gradlew.bat :core:data:testDebugUnitTest --offline --no-parallel --console=plain \
  --tests "*VisualDrawOpsValidatorTest" --tests "*VisualDrawOpsParserTest" --tests "*VisualDrawOpsPainterPureTest"
./gradlew.bat :core:data:testDebugUnitTest --offline --no-parallel --console=plain \
  --tests "*ProgramSchemaMaxLengthAlignmentTest"
./gradlew.bat :app:testLocalFirstDebugUnitTest --offline --no-parallel --console=plain \
  --tests "*VisualContentMeasuringTest" --tests "*VisualMeasureEnvironmentBudgetTest" --tests "*VisualMeasuredLayoutInkBoundsTest"

# Node
node tools/visual_compiler/run_tests.mjs

# 设备（裸 adb；禁 connected*AndroidTest）
./gradlew.bat :core:data:assembleDebugAndroidTest :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest
adb -s emulator-5590 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
adb -s emulator-5590 shell am instrument -w -r -e class \
  com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5590 install -r -t app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk
adb -s emulator-5590 install -r -t app/build/outputs/apk/androidTest/localFirst/debug/app-localFirst-debug-androidTest.apk
adb -s emulator-5590 shell am instrument -w -r -e class \
  com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest \
  com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner

# 静态门
python -I tools/ci/check_stdlib_compat.py
python -I tools/ci/check_dependency_pins.py
```
