# 批次 51 / JS 产品化（计划任务 30–32）：MVA 未过 → JS 保持关闭、math 显式降级复验、KD 登记（2026-11-10）

> **性质**：批次 51 计划「JS 产品化」子线（任务 30/31/32）收口。
> **门阈值零改动、断言零放宽、生产能力广告零改动、无新增依赖、未提交。**
> HEAD `8dd29e963975ff44703f638064322be0e7494f98`（开工/收口 `git rev-parse HEAD` 实读；
> 工作树有他线 683 条改动，本子线只读其目标文件、只追加证据与 KD 台账）。
> 证据根：本目录。JVM 串行执行（`--no-daemon --no-parallel`）；**未跑设备**（见 §6）。

## 0. 结论（先给门判）

| 项 | 结论 | 依据 |
|---|---|---|
| MVA 门 | **FAIL**（JS 臂比率门；IMG 臂 BLOCKED） | `docs/research/evidence/2026-11-10-batch-51/mva-gate/README.md` §6/§7 |
| 生产 `visualEngine` 注入 | **未注入**（三参装配 + 工厂两参默认 null） | §1（现树实读） |
| `availableMethods` 广告 JS | **未广告**（6 处 `IMAGE_MODEL_ONLY` + 默认参数同值） | §1（现树实读 + 7 例 JVM） |
| math 标签 | **维持显式产品降级**：painter 诊断含 `explicitly degraded`；度量期 math 失败降级为**原文 text**（`degraded=true`，不丢字） | §2 |
| KD-78 残留 | 门/兜底全在树；**产品样例的 KD-78 ① 残留**已由 G4 线在批次 51 修复（先红后绿）；KD-78 本体维持 closed | §3 |
| KD-76 / KD-71 / KD-73 / KD-82 | 均**保持 open**（本批无新关闭证据；KD-73 静态半边本轮实跑绿；KD-82 JVM 7/0） | §4 |
| 定向 JVM 单测 | **147 tests / 0 failures / 0 errors / 0 skipped**，两次 Gradle 调用均 EXIT=0 | §5 |

**用户裁定映射**：MVA 未过 ⇒ 不注入、不广告、KD 表登记「MVA 未过」；math 只走显式降级
（不实现第三套盒几何）；切题只走模型识别（本子线零触碰）；无知识库浏览入口 / 无青少年模式
（本子线零触碰）；6C-2 备份 mask 9/13/15 未触碰。
**本子线代码改动 = 0**（依据"新增必先指认它消灭的失败"：MVA 未过时没有任何产品失败需要
靠改代码消除；改动只落在 KD 台账与本证据目录）。

## 1. 生产装配与能力广告：现树未变（MVA 未过，保持关闭）

### 1.1 MVA 读数（引用同批证据，不重算）

`docs/research/evidence/2026-11-10-batch-51/mva-gate/README.md` §6/§7 的原始读数：

- 首次合格 **32/120 = 0.2667**（阈值 ≥0.90，FAIL）；预算内最终同为 0.2667（≥0.95，FAIL）；
- 每组 20 题：flow 2、handwriting 0、mechanics 0、natural 14、proportion 1、source 15
  → **6/6 组 FAIL**；
- RENDERED **95/120 = 0.7917** 是首次门的上界（阈值需 108 题）——**与未判事实无关**；
- 端到端 p95 **72.9551 s**（作者模型 elapsed_ms + 设备渲染延迟；机器可读门已按请求→首图重算为
  `met=false`；渲染侧 35.15 ms 只作并列读数）> 30 s；IMG 臂无 provider → **BLOCKED**。

阈值 **90/95/90/0**、p95 **30 s / 120 s**、分母 **120** 均未改；同批 README §8 的
"120 集已转回归集、本读数不能作启用 PASS 依据"一并引用——本读数既不能作启用依据，
也**不因不可评而降级为 NOT_EVALUABLE**：上界 95/120 已把首次/最终/分组三门压在阈值之下
（同文 §7.1）。

**判定：MVA = FAIL。** 按用户裁定，JS 不注入、能力不广告。

### 1.2 装配现树（本会话实读）

| 事实 | 证据 |
|---|---|
| 生产装配仍只传三参 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt:387-391`（`FigureGenerationCoordinationFactory.create(database, configurationStore, networkRequestsAllowed)`） |
| 工厂的 JS 两个参数默认 null | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/DefaultFigureGenerationCoordinator.kt:886-891`（`visualEngine: VisualExecutionEngine? = null`、`visualFrameInputs: VisualFrameInputs? = null`） |
| 协调器无引擎即 UNAVAILABLE | 同文件 `create` 直接把 null 传入 `DefaultFigureGenerationCoordinator`（`:892-903`） |

### 1.3 能力广告 6 处（全部 `IMAGE_MODEL_ONLY`）

```text
core/data/.../capture/ConfiguredCleanImageGenerator.kt:95
core/data/.../export/RoomDetailedSolutionRepository.kt:256
core/data/.../mistake/RoomMistakeEditRepository.kt:258
core/data/.../model/FigurePromptAssembler.kt:124   （默认参数）
core/data/.../model/AttachedImageGenerator.kt:144
core/data/.../model/TutorFigureGenerator.kt:372
```

`FigurePromptAssembler.kt:19-23` 仍写明「`availableMethods` 接通是『全门绿后』最后一步」；
`docs/current/figure-generation-prompt-policy.md:84` 仍写「未接 JS 运行库前 availableMethods
不含 JS」。**能力快照无需更新**（MVA 未过，更新成"含 JS"才是伪造）。

JVM 钉住广告面：`FigurePromptCapabilityInjectionTest` **7/0/0/0**（`:49`/`:60` 两例断言
`IMAGE_MODEL_ONLY` 正文与波前逐字节相同；`:69`/`:107` 两例覆盖 JS 能力注入与"不含交互面"）。
证据：`junit/TEST-...FigurePromptCapabilityInjectionTest.xml`。

## 2. math 标签：维持显式降级、不静默丢字（现树复验）

### 2.1 三处语义（本会话实读）

| 面 | 现树行为 | 锚点 |
|---|---|---|
| 回放（painter） | `VisualLabelContent.Math` → `PAINTER_LABEL_MATH`，诊断逐字含 **`explicitly degraded`**，并说明 artifact 不携 MathBox/原文、不得新增第三套几何走查 | `core/data/.../visual/VisualDrawOpsPainter.kt:194-202` |
| 回放 KDoc | `PAINTER_LABEL_MATH` 与 `VisualLabelContent.Math` 均写"V1 显式产品降级"及解锁前置（携盒或原文+环境、与 core/ui + core/export 逐点对齐） | `VisualDrawOpsPainterPure.kt:53-62`、`:223-231`；`VisualMeasuredLayoutArtifact.kt:32` |
| 预览 | 同码继承 fail-closed，折算前拒绝（不把 Math DTO 交给 painter） | `TutorVisualPreviewRenderer.kt:297-309` |
| 度量 | math lowering 失败 → **用 lowering 透传的原文**走 text 度量，`kind` 保持 MATH、`degraded=true`（单一事实来源，度量文本 = 未来回放文本） | `app/.../visualjs/VisualContentMeasuring.kt:455-466` |

**"不得静默丢字"的判据**：纯 math 盒（未降级 artifact）被**有码拒绝**（不是静默跳过）；
可恢复 math 在度量期降级为**原文 text**（不是占位框、不是空串）。JVM 用例
`an over-budget math payload degrades to the text path and the digest records it`
（`VisualContentMeasuringTest.kt:223`）本轮绿；`TutorVisualPreviewRendererTest` 的 math 边界例
（`:190`）本轮绿。

### 2.2 本批没有实现 math 盒回放（有意结论）

解锁需先扩 `VisualMeasuredLayoutArtifact` 形状并与 core/ui、core/export 两侧几何逐点对齐；
本批无该授权，维持显式降级（与批次 50 裁定一致）。**未新增任何盒/坐标走查。**

## 3. KD-78 残留复验（本体 closed，不重开）

- **生成门**：`core/data/src/main/resources/visual/visual-compiler.js:892-897`
  （`PAINTER_STROKE_WITHOUT_FILL`）与 `:483-492`（`PAINTER_DASH_ODD`，偶数且 ≥2）；
  原生解析器 `VisualDrawOpsParser.kt:442-454` / `:559-567` 同码拒绝。
- **回放兜底**：`VisualDrawOpsPainter.kt:151-162` 先 dash 形状、后 stroke-only，与生成门同序。
- **本轮实跑**：`node tools/visual_compiler/run_tests.mjs` → `94 passed, 0 failed`（EXIT=0，
  `logs/node-compiler.log`）；JVM `VisualDrawOpsValidatorTest` 35、`VisualDrawOpsParserTest` 26、
  `VisualDrawOpsPainterPureTest` 13，全 0 失败（§5）。
- **KD-78 ① 的产品样例残留（批次 51 新修复）**：`JS_STATIC_SAMPLE` 原为
  `stroke: '#26354a'`（字符串）+ path 无 `fill`，交真实编译器报
  `SCENE_FIELD_TYPE: ...stroke must be an object`（`mva-gate/README.md` §2 的红轮）。
  G4 线已改为 `fill` + `stroke: { width }` 对象 + label 必填四字段
  （`FigurePromptAssembler.kt:256-294`），红→绿：`test_prompt_sample_compiles.py` 1 passed、
  探针 `{"compiles":true}`。**这是提示词样例的残留，不是 KD-78 门本身**；门与兜底未改。
- **math 降级条**（KD-78 批次 50 关闭时并入的契约 §5 第六条）本批复验见 §2。

## 4. KD-76 / KD-71 / KD-73 / KD-82：按证据登记（均 open）

| KD | 本批动作 | 本批证据 | 状态 |
|---|---|---|---|
| KD-76 | 只读复核门与装配；**未跑设备校准** | 门 `READY_TIMEOUT_MILLIS = 10_000L`（`VisualJsClientEngine.kt:1325`）；报表判词常量/20 样本闸门 JVM 8/0（`Kd76ReportVerdictTest`）；MVA FAIL + 三参装配=广告门不变 | **open**（剩余缺口：产品进程/UI 首请求、C4 毫秒分布、29.5 s 链四段、低内存、arm64/真机） |
| KD-71 | 只读；零改动零新执行证据 | 最近设备证据仍是批次 40 `3×OK (27 tests)`；G4 变异/回退红向敏感性未做 | **open**（建议条件式关闭不变） |
| KD-73 | **门脚本实跑** | `check_stdlib_compat.py` → `OK: 0 unresolved stdlib member references across 23859 unique kotlin/* refs (389 artifacts; 8 coordinates unscanned)`，**EXIT=0**；`check_dependency_pins.py` → `resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`，**EXIT=0**（`logs/`） | **open**（设备/R8 冒烟无 stdlib 归因崩溃未做） |
| KD-82 | 常量复核 + JVM | `OpenAiModelProtocol.kt:562`（`MAX_IMAGE_PROMPT_UTF8_BYTES / 4`）、`:600`（`MAX_PROGRAM_UTF8_BYTES / 4`）；`ProgramSchemaMaxLengthAlignmentTest` **7/0/0/0** | **open（known limitation）**（差广告解冻批对 imagePrompt 半边的产品裁定） |

## 5. 定向单测（visualjs）与 exit

本子线的两次 Gradle 调用**串行**执行（先 core:data、后 app；均 `--no-daemon --no-parallel`，
第二次在第一次退出后才启动）：

```text
$ ./gradlew --no-daemon --no-parallel --console=plain :core:data:testDebugUnitTest \
    --tests "*VisualDrawOpsValidatorTest" --tests "*VisualDrawOpsParserTest" \
    --tests "*VisualDrawOpsPainterPureTest" --tests "*TutorVisualPreviewRendererTest" \
    --tests "*ProgramSchemaMaxLengthAlignmentTest" --tests "*FigurePromptCapabilityInjectionTest"
BUILD SUCCESSFUL in 14s        # EXIT=0（logs/gradle-core-visualjs.log）

$ ./gradlew --no-daemon --no-parallel --console=plain :app:testLocalFirstDebugUnitTest \
    --tests "com.tingyun.smartmistakebook.visualjs.*"
BUILD SUCCESSFUL in 55s        # EXIT=0（logs/gradle-app-visualjs.log）
```

JUnit 汇总（XML 原件在 `junit/`，机器汇总 `test-results.json`）：

| 套件 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| FigurePromptCapabilityInjectionTest | 7 | 0 | 0 | 0 |
| ProgramSchemaMaxLengthAlignmentTest | 7 | 0 | 0 | 0 |
| TutorVisualPreviewRendererTest | 9 | 0 | 0 | 0 |
| VisualDrawOpsPainterPureTest | 13 | 0 | 0 | 0 |
| VisualDrawOpsParserTest | 26 | 0 | 0 | 0 |
| VisualDrawOpsValidatorTest | 35 | 0 | 0 | 0 |
| ConnectStageTimestampsTest | 2 | 0 | 0 | 0 |
| Kd76ReportVerdictTest | 8 | 0 | 0 | 0 |
| VisualContentMeasuringTest | 16 | 0 | 0 | 0 |
| VisualJsFrameQueueTest | 5 | 0 | 0 | 0 |
| VisualJsResultFrameIdentityTest | 5 | 0 | 0 | 0 |
| VisualMeasureEnvironmentBudgetTest | 5 | 0 | 0 | 0 |
| VisualMeasuredLayoutInkBoundsTest | 9 | 0 | 0 | 0 |
| **合计** | **147** | **0** | **0** | **0** |

说明：`core:data` 的编译任务显示 `UP-TO-DATE`（Gradle 判定输入未变；上一次 core:data 构建的
`build/test-results` 目录时间戳为 06:41）；`app` 的 `compileLocalFirstDebugKotlin` 与
`compileLocalFirstDebugUnitTestKotlin` 本轮**重新执行**并成功（后者含他线在飞的
`visualjs/VisualContentMeasuringTest.kt` 增量，该文件本子线未改、16/0 绿）。

## 6. 未做 / 未验证（如实）

1. **未跑设备**：math `PAINTER_LABEL_MATH` 的最近设备证据是批次 50 `VisualDrawOpsPainterInstrumentedTest`
   `OK (1 test)`（`docs/research/evidence/2026-11-09-batch-50/js/green-math-degrade.log`）；
   本批未重装 APK、未跑 `am instrument` → 本批设备面 **UNVERIFIED**。
2. **KD-76 未跑校准**（主机/设备 n≥20 冷启动样本）；**KD-71 G4 未做**；**KD-73 设备/R8 半边未做**；
   **KD-82 产品裁定缺失**。
3. 未跑 arm64/真机；未跑 soak/release/全模块单测（属批次 51 其他子线）。
4. `VisualContentMeasuringTest` 的增量来自 JS/UI 他线（`git status` 实读为 M），本子线**未改**该文件；
   其 16/0 只证明"当前树含该增量时通过"，不替该线的改动背书。
5. 本目录是单轮实跑，不是统计样本。

## 7. 可复跑命令

```bash
# 静态门（无 Gradle）
python -I tools/ci/check_stdlib_compat.py
python -I tools/ci/check_dependency_pins.py
node tools/visual_compiler/run_tests.mjs

# JVM（串行；同一条命令内的 --tests 过滤即本目录 6 套件）
./gradlew --no-daemon --no-parallel --console=plain :core:data:testDebugUnitTest \
  --tests "*VisualDrawOpsValidatorTest" --tests "*VisualDrawOpsParserTest" \
  --tests "*VisualDrawOpsPainterPureTest" --tests "*TutorVisualPreviewRendererTest" \
  --tests "*ProgramSchemaMaxLengthAlignmentTest" --tests "*FigurePromptCapabilityInjectionTest"
./gradlew --no-daemon --no-parallel --console=plain :app:testLocalFirstDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.visualjs.*"

# 装配/广告静态复核
git grep -n "FigureGenerationCoordinationFactory.create(" -- app/src/main/kotlin
git grep -n "FigureGenerationCapabilities.IMAGE_MODEL_ONLY" -- core/data/src/main/kotlin
```
