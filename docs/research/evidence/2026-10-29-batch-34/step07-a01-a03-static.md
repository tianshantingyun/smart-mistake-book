# E-07 A-01/A-02/A-03 静态确认（0 远程派发 / 广告开关未动 / 无新增设备用例）

| 字段 | 值 |
|---|---|
| evidence-id | E-07 |
| convention-version | 1 |
| batch | 2026-10-29-batch-34 |
| step | 三（A-01/A-02/A-03 相关新用例与广告面） |
| gate-or-kd | 4E.2 A-01/A-02/A-03；计划 §2「能力快照 / prompt / 广告文本零改动」 |
| verdict | PASS（静态核对；JVM 执行不在本轨权限内） |
| executed-at | 2026-10-29T19:03:00+08:00 |
| operator | 设备验证轨（子代理） |
| head | `db792fa873f4bc150f415b67d1c2e3b1bab6c995` |
| worktree | 721 条（241 `M` / 480 `??`） |
| device-serial | —（本步无设备执行） |
| exit-code | —（只读 grep/sed） |
| raw-log | — |
| record-ref | `docs/research/2026-10-29-batch-34-record.md` §4 |
| sanitized | 是（§5） |

## 1. 目的与判据

计划 §1 任务 4/5 要求：A-01 在 coordinator 落 `VisualExecutionEngine` 注入点但**不接生产开关**、
JS 路径 **0 远程派发**；A-02/A-03 的 `method=JS` / `program` 只由显式能力集合开启，**广告开关不动**。
本步只用只读源码核对设备面是否需要新用例，并确认生产装配仍关闭。

## 2. 命令（可复跑）

```bash
cd "D:/smart mistake book"
grep -n "visualEngine\|FigureGenerationMethod.JS ->\|private suspend fun generateLocalJs\|ledger.reserve" \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/DefaultFigureGenerationCoordinator.kt
grep -n "IMAGE_MODEL_ONLY" \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/ConfiguredCleanImageGenerator.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/AttachedImageGenerator.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorFigureGenerator.kt \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/FigurePromptAssembler.kt
grep -n "require(FigureGenerationMethod.JS !in capabilities.availableMethods)" \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/FigurePromptAssembler.kt
sed -n '120,135p;480,490p' core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelProtocol.kt
sed -n '780,805p' core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/DefaultFigureGenerationCoordinator.kt
# 设备侧是否有 A-01..A-03 的 androidTest 类
grep -rln "visualEngine\|generateLocalJs\|reservedRemoteDispatches\|figureMethods" \
  app/src/androidTest core/data/src/androidTest core/database/src/androidTest || echo "(no androidTest hits)"
```

## 3. 结果摘要

| 项 | 结果 | 判据 |
|---|---|---|
| 生产装配 `FigureGenerationCoordinationFactory.create` 的 `visualEngine`/`visualFrameInputs` | 默认 `null`（`:790`、`:792`、`:801-802`） | 生产不接引擎 |
| JS 分支 | `FigureGenerationMethod.JS -> generateLocalJs`（`:211`）；无引擎时在任何账本调用前回 `UNAVAILABLE`（`:252-257`） | 0 远程派发 |
| JS 路径是否调用 `ledger.reserve` | 无（`:245-246` 注释明写；`:248-315` 全段无 `ledger` 调用） | 0 远程派发 |
| IMAGE_MODEL 路径 | 仍走 `generateImageViaModel`（`:209`）→ `openBackend` → 账本 | 原路径不动 |
| 能力快照常量 `IMAGE_MODEL_ONLY` | `availableMethods = setOf(IMAGE_MODEL)`（`FigureGeneration.kt:161-168`） | JS 不广告 |
| 三处生产快照位 | `AttachedImageGenerator.kt:144`、`ConfiguredCleanImageGenerator.kt:95`、`TutorFigureGenerator.kt:372` 仍 `IMAGE_MODEL_ONLY` | JS 不广告 |
| prompt 装配默认 | `FigurePromptAssembler.kt:102` 默认 `IMAGE_MODEL_ONLY`；`:40`、`:108` `require(JS !in availableMethods)` | 装配期 fail-closed |
| 请求 schema `method.enum` 默认 | `OpenAiModelProtocol.kt:129-131` 默认 `setOf(IMAGE_MODEL)`；只有 `jsAdvertised` 时才 `add("JS")`（`:485-486`） | 生产请求体不变 |
| A-01/A-02/A-03 的 androidTest 类 | `(no androidTest hits)` | 无新增设备用例，本步不产生设备执行 |

相关 JVM 用例（**执行归单测门，本轨只允许 `assemble*`，未运行**）：
`FigureGenerationCoordinatorTest.kt:259`（JS 本地执行不预留）、`:317`/`:374`/`:402`
（`reservedRemoteDispatches == 0`）、`:323`（远程预算为 0 仍本地执行）、`:433`（引擎注入不影响 IMAGE_MODEL）；
`FigureGenerationPolicyTest.kt:188`（本地 JS 不被远程预算阻塞）；
`OpenAiNativeToolsProtocolTest.kt` 中 JS/`program` 双解析与 schema 用例。

## 4. 关键输出（有界）

```text
153:    private val visualEngine: VisualExecutionEngine? = null,
158:    private val visualFrameInputs: VisualFrameInputs? = null,
209:            FigureGenerationMethod.IMAGE_MODEL -> generateImageViaModel(request, proposal)
211:            FigureGenerationMethod.JS -> generateLocalJs(request, proposal)
252:        val engine = visualEngine ?: return outcome(   # 无引擎：UNAVAILABLE
790:        visualEngine: VisualExecutionEngine? = null,
792:        visualFrameInputs: VisualFrameInputs? = null,
801:        visualEngine = visualEngine,
```

```text
AttachedImageGenerator.kt:144:        val capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY
ConfiguredCleanImageGenerator.kt:95:    val capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY
TutorFigureGenerator.kt:372:           val capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY
FigurePromptAssembler.kt:102:          capabilities: FigureGenerationCapabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
FigurePromptAssembler.kt:40/108:       require(FigureGenerationMethod.JS !in capabilities.availableMethods)
```

```text
OpenAiModelProtocol.kt:129-131
        figureMethods: Set<FigureGenerationMethod> =
            setOf(com.tingyun.smartmistakebook.core.model.FigureGenerationMethod.IMAGE_MODEL),
OpenAiModelProtocol.kt:485-486
                                        add(JsonPrimitive("IMAGE_MODEL"))
                                        if (jsAdvertised) add(JsonPrimitive("JS"))
```

## 5. 脱敏与去噪

- 只读源码，无日志正文；无需脱敏。
- 扫描：`00-index.md` §6 → 0 命中。

## 6. 判定

**PASS（静态）**。A-01 的注入点已落、生产装配默认不注入引擎；JS 分支不触账本（0 远程派发）；
A-02/A-03 的 JS 声明只在显式能力集合下出现，三处生产快照 + prompt 默认 + 请求 schema 默认均未改，
广告开关未动。**未覆盖**：A-01/A-02/A-03 的 JVM 用例执行结果（单测门负责，本轨未运行）；无新增
androidTest 用例，故本步无设备产物。
