# 4E 双后端装配研究（批次 34，驱动 T3/T4）

> 输入：`.zcode/plans/plan-2026-10-29-batch-34-dual-backend-kd80.md`（已全文读）、
> 准入清单 A-01/A-02/A-03（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:189-191`）、
> 批次 34 只读基线（`docs/research/2026-10-29-batch-34-baseline.md`）。
> 本文只做研究/设计，**未改任何源码、未运行 Gradle、未上设备**；所有"现状"结论来自本轮实读源码与只读命令，
> 与设计有关的推断一律标"建议"，无法核实的标 **未验证**。

**一句话结论**：A-01 的最小切口是给 `DefaultFigureGenerationCoordinator` 加两个**默认 null** 的构造参数
（`visualEngine` + 宿主帧输入），新增 JS 分支且**完全不经过 `ledger.reserve`**；但 A-01 的"记 attempt"
半场被现状的 `(operation_id, attempt_ordinal)` 唯一索引与 `insertAttempt ABORT` 挡住，必须先解决本地尝试的序号分配，
否则本地尝试要么写不进、要么被静默丢弃。A-02/A-03 的关键不是解析层（解析层已经接受 `program`/`method=JS`），
而是 **`TutorToolCall` 构造器 + `toFigureGenerationProposal` + `RoomTutorToolRunner` 三处**：后两处现在会把 JS
静默改写成 IMAGE_MODEL。请求 schema 加 `JS` 对持久化指纹零影响，可无迁移回滚。

---

## 0. 现状核对（本轮实读，逐条给位置）

| 事实 | 证据 |
|---|---|
| 协调器构造参数只有 `ledger/openBackend/now/audit`，无引擎 | `core/data/.../visual/DefaultFigureGenerationCoordinator.kt:125-134` |
| 非 IMAGE_MODEL 直接 `UNAVAILABLE` | 同上 `:182-190`（"本波次没有可用的 JS 运行库，未执行"） |
| 生产装配点只有 app 一处，未传引擎 | `app/.../SmartMistakeBookApplication.kt:360-368`；工厂 `DefaultFigureGenerationCoordinator.kt:571-586` |
| policy 已能接受 JS（能力含 JS 且 program 合法） | `core/model/.../FigureGenerationPolicy.kt:179-182`、`:185-218` |
| policy 的预算门对**所有**方法生效（含 JS） | 同上 `:143-149`（`allowsOneRemoteDispatch()` 在 method 分支之前） |
| 工具环构造器仍拒绝 program / 非 IMAGE 方法 | `core/model/.../TutorToolLoop.kt:344-349` |
| `toFigureGenerationProposal` **丢弃 program** 且**无条件填图像载荷** | 同上 `:394-408`（`imageOperation = figureImageOperation ?: kind.defaultImageOperation()`、`imagePrompt = figureImagePrompt ?: figureDescription`） |
| 解析层已接受两路的 `program` | `core/data/.../OpenAiModelResponseParsers.kt:307`（生成白名单）、`:567`（工具调用白名单）、`:333`/`:622`（取值）；`OpenAiModelProtocol.kt:742`（原生 tool_calls 走同一 `toTutorToolCall`） |
| 原生 schema `method.enum` 只有 IMAGE_MODEL | `core/data/.../OpenAiModelProtocol.kt:443-461`，enum 在 `:453` |
| 工具执行分支完全不看 `figureMethod`，痕迹硬写 IMAGE_MODEL | `core/data/.../study/RoomTutorToolRunner.kt:234`、`:947-953`、`:1026-1041`、`:1060-1068` |
| 引擎端口/请求类型已存在（含 viewport/theme/seed/timeMs） | `core/domain/.../VisualExecutionEngine.kt:40-46`、`:56-84` |
| 引擎实现存在但无生产装配（L3 客户端在 app） | `app/.../visualjs/VisualJsClientEngine.kt:100`；`core/data/.../QuickJsVisualExecutionEngine.kt:141`（服务侧直连引擎） |
| attempt 表已为 JS 预留：`method` 可为 JS、`reservation_id` 可空 | `core/database/.../entity/FigureGenerationEntities.kt:90-99`、`:147-148` |
| `(operation_id, attempt_ordinal)` 唯一、插入 `ABORT` | `:123`；`core/database/.../dao/FigureGenerationDao.kt:19-20`、`:102-103` |
| 审计 operation 计数是**绝对覆盖**写 | `FigureGenerationDao.kt:62-100`（`SET ... = :usedRemoteDispatches`） |
| `beginOperation` 对已存在的行也返回 true（`insertOperation` 的返回值被丢弃） | `core/database/.../RoomStudyDatabase.kt:1319-1340` |
| 契约：JS 本地执行不占远程派发；换方法不换 operationId | `docs/current/visual-generation-contract.md:163`；同句 `docs/current/figure-generation-prompt-policy.md:101` |
| 契约：JS 仍不广告、无生产装配 | `docs/current/visual-generation-contract.md:125`（§5.1 表末行） |

---

## 1. 任务一：coordinator 注入 `VisualExecutionEngine` 的最小切口（A-01）

### 1.1 切口形态（建议）

```kotlin
class DefaultFigureGenerationCoordinator internal constructor(
    private val ledger: RemoteModelDispatchLedger,
    private val openBackend: suspend () -> FigureImageBackend?,
    private val now: () -> Long = System::currentTimeMillis,
    private val audit: FigureGenerationAuditRecorder = FigureGenerationAuditRecorder.Noop,
    // 新增，均默认 null：旧装配（含全部既有 JVM 用例）字节行为不变
    private val visualEngine: VisualExecutionEngine? = null,
    private val visualFrameInputs: VisualFrameInputs? = null,
)
```

`FigureGenerationCoordinationFactory.create(...)` 追加同名可选参数，**app 调用点一字不改**（默认 null）——
于是生产继续走 `UNAVAILABLE`，满足"不接生产广告开关"。

`generateImage` 的方法分支从"非 IMAGE 即不可用"改为：

```kotlin
return when (method) {
    FigureGenerationMethod.IMAGE_MODEL -> generateImage(request, proposal)   // 原路径一行不动
    FigureGenerationMethod.JS -> generateLocalJs(request, proposal)          // 新增私有分支
}
```

`generateLocalJs` 的顺序（建议）：

1. `val engine = visualEngine ?: return UNAVAILABLE("本波次没有可用的 JS 运行库，未执行")` —— **保留现文案**，
   既有负向用例（`FigureGenerationCoordinatorTest.kt:197-220`）改成正向后仍要有"无引擎 → UNAVAILABLE"对照。
2. `val frame = visualFrameInputs?.forGeneration(request, proposal) ?: return UNAVAILABLE(...)`。
3. 组 `VisualExecutionRequest(programId = 派生, sourceJs = proposal.program!!, sceneRevision = 1, stateJson = null, viewport/theme/seed/timeMs 来自 frame, explicitUserRetry = false)`。
   `program` 由 policy 保证非空（`FigureGenerationPolicy.kt:195-211`），这里 `requireNotNull` 只是收窄类型。
   `programId` 建议 `"visual:${operationId}:${sha256(program).take(16)}"`（契约 §8：源码改变即新 program，
   无需读库也能保证确定性）。
4. `audit.begin()`（PENDING）→ `markOperationRunning` → **本地尝试**（见 §4）→ `engine.execute`。
5. 结果映射：

| 引擎结果 | outcome.status | artifact | 诊断 |
|---|---|---|---|
| `Rendered` | GENERATED | `FigureArtifact.Program(purpose, programId, sourceJs)` | 无 |
| `Rejected(ENGINE_CLOSED)` | UNAVAILABLE | null | 有界 reason 文案 |
| 其它 `Rejected` | FAILED | null | `"${reason.name}: ${diagnostics}"` 截断到 `MAX_DIAGNOSTICS_CHARS` |
| 抛异常（端口承诺不抛，防御） | FAILED | null | 同 `boundedFailure` |

6. 取消：沿用 `generateWithBackend` 的 `NonCancellable` 收尾（`DefaultFigureGenerationCoordinator.kt:235-241`），
   attempt/operation 落 CANCELLED。

**绝不调用 `ledger.reserve`**。理由是结构性的：`RemoteModelDispatchRequest.configurationFingerprint` 非空
（`core/domain/.../RemoteModelDispatchLedger.kt:47`），JS 没有"端点/模型配置"，为它伪造一个指纹并把
`model_task_operation.dispatch_count` +1 正好是契约 `:163` 禁止的"本地执行占远程派发"。

### 1.2 两个新参数各自消灭的失败（§12.2 自检）

| 新增 | 消灭的具体失败 |
|---|---|
| `visualEngine` | 没有它，policy 能校验 JS、coordinator 却永远回 UNAVAILABLE——A-01 的"能执行"永远无法达成，且无法用 JVM 用例证明"0 远程派发" |
| `visualFrameInputs` | 没有它，coordinator 只能自己编 viewport/theme/seed；那会伪造一个不存在的布局环境，同一 program 两次执行结果不可复现，且违反契约 §3.2 "布局事务冻结这些值" |

（备选：把两者包成一个 `VisualBackend` 对象、只加一个参数。功能等价，API 面更小，但会多一个只有一种实现的
包装类型；建议 T3 二选一，不要两套都建。）

### 1.3 必须在同一刀里处理的 policy 语义（否则 JS 走不通）

`FigureGenerationPolicy.validateGenerate` 的预算门（`:143-149`）在方法确定之后仍对**所有**方法生效，而
`FigureGenerationBudget.allowsOneRemoteDispatch()` 只表达**远程**派发。余额 0 时，一个纯本地 JS 请求会被
`BUDGET_INSUFFICIENT` 拒掉——与契约 `:163` 相反。
建议：`if (method == FigureGenerationMethod.IMAGE_MODEL && !request.budget.allowsOneRemoteDispatch()) reject(...)`，
并补 JVM 对照（IMAGE_MODEL@0 拒 / JS@0 过）。`dispatchParent` 仍要求非空（操作身份与审计需要父操作），
本批不改。

### 1.4 牵动面枚举（§12.1，逐项给"改到 / 为何不改"）

| 被牵动处 | 处置 |
|---|---|
| `DefaultFigureGenerationCoordinator`（clean） | 改：加参数 + JS 分支；IMAGE_MODEL 分支零改动 |
| `FigureGenerationCoordinationFactory`（同文件，clean） | 改：透传两个可选参数 |
| app 装配 `SmartMistakeBookApplication.kt:360-368`（clean） | **不改**：默认 null 即维持 UNAVAILABLE |
| `core/domain/FigureGenerationCoordinator.kt:19-20`（端口 KDoc 写"实现应如实回 UNAVAILABLE"） | **禁改**（core/domain 热区、开工即脏 M）→ **停项登记**：JS 分支落地后该 KDoc 变旧，必须在热区解锁批同步，否则"文档说没接通、代码已接通" |
| `core/domain/VisualExecutionEngine.kt`（同理热区） | 不改：端口语义已够用（`close()` 不调用——引擎生命周期属 app 装配，coordinator 不拥有它） |
| `FigureGenerationOutcome` / `FigureArtifact.Program`（`core/model/.../FigureGeneration.kt:438-515`） | 不改：Program 已存在 |
| 审计 recorder（clean） | 改：新增本地尝试入口（§4） |
| `TutorToolFigureTrace`（`core/model/.../TutorToolTrace.kt:118-138`） | 不改类型；执行侧要能写 `method=JS`（§2.3） |
| 设备广告面（`TutorCapabilitySnapshot`、prompt、工具描述） | 不改（批次硬约束） |
| 测试 | 改/加：见 §1.5 |

### 1.5 验收用例（JVM，先红后绿）

`core/data/.../visual/FigureGenerationCoordinatorTest.kt`：

1. `aJavascriptProposalIsExecutedLocallyWithoutReserving`（把 `:197-220` 的负向翻正向）：fake 引擎回 `Rendered`；
   断言 `ledger.requests.isEmpty()`、`backendOpens == 0`、`channel.generateCalls == 0`、`status == GENERATED`、
   `artifact is FigureArtifact.Program`、尝试行 `method=JS`/`reservationId=null`、operation 计数 `0/0`。
2. `aJavascriptProposalWithoutAnEngineStaysUnavailable`（保留的负向）：不建 attempt、不碰账本。
3. `aRejectedJavascriptExecutionRecordsAFailedAttemptAndZeroDispatches`：`Rejected(INVALID_PROGRAM)` → FAILED、
   诊断有界、attempt FAILED、计数 `0/0`。
4. `anImageModelProposalIsUnaffectedByTheEngineInjection`：注入引擎后 IMAGE_MODEL 仍恰好 `reserve` 一次 + 通道一次。
5. `aJavascriptProposalAtZeroRemoteBudgetIsStillAllowed`（policy 对照，落 `core/model` 测试）。
6. `switchingMethodsOnTheSameOperationDoesNotZeroTheRemoteAuditCounts` —— **先红**（§4.3 的缺陷），
   修好计数覆盖后再绿。

---

## 2. 任务二：工具环接受 `method=JS` + `program` 的字段归属（A-02）

### 2.1 归属决定：program **只**在 proposal 上，不进 `FigureGenerationRequest`

- `FigureGenerationProposal.program` 已存在（`core/model/.../FigureGeneration.kt:382`），注释即"JS 方法载荷；
  IMAGE_MODEL 不得携带"。
- `FigureGenerationRequest`（`:299-363`）是**宿主冻结输入**，模型输出不该进；`program` 是模型输出，
  若同时塞进 request 就有两个载体、会漂移（policy 的 `validateJsProposal` 只读 proposal.program）。
- 结论：**request 不加 program 字段**。A-03 行里"program 独立字段"的正确落点是 proposal（wire 层字段名 `program`），
  不是 request。该行文字容易被读成"要加在 request 上"，T4 不要照字面加。
- "只进独立字段"的反面同样要钉住：`program` 不得从 `description`/`imagePrompt` 里二次提取，也不得把源码塞进
  description 蒙混过关（`description` 现有控制字符禁令 `TutorToolLoop.kt:338-342` 会挡多行源码，但单行 JS 文本
  无法与普通描述区分——所以 JS 一律要求 `program` 字段，缺失即拒，见 §2.3 `javascriptInTheDescriptionIsNotAProgram`）。
- 旧字段仍要保留：`kind` 与 `description` 目前是 `GENERATE_FIGURE` 的必填（`TutorToolLoop.kt:327-336`），
  JS 分支沿用它们表达用途/回退文案，不要顺手删掉。

### 2.2 必须同时改的三处（只改一处都等于没接通）

| 位置 | 现状 | 不改会发生的具体失败 |
|---|---|---|
| `TutorToolCall.init`（`TutorToolLoop.kt:344-349`） | 一律拒 `program`、拒非 IMAGE 方法 | 模型合法输出被整条拒（当前是刻意 fail-closed） |
| `toFigureGenerationProposal`（`:394-408`） | 丢 `program`；**无条件**填 `imageOperation`/`imagePrompt` | 即使构造器放行，proposal 也会带 `imagePrompt` → `JS_PAYLOAD_INVALID`（`FigureGenerationPolicy.kt:189-194`），JS 永远执行不了。**这是最容易漏的一处** |
| `RoomTutorToolRunner.generateFigure`（`:947-1041`） | 不看 `figureMethod`，恒走 `TutorFigureRequest`→图像生成器 | 构造器一放行，JS 调用会被**当成 IMAGE_MODEL 真出网**（双付费/方法错配），且痕迹写 IMAGE_MODEL |

建议的语义：

```kotlin
// TutorToolCall.init（GENERATE_FIGURE 分支内）
if (figureMethod == FigureGenerationMethod.JS) {
    require(figureProgram != null && figureProgram.isNotBlank()) { "JS must carry a complete program" }
    require(figureUsageUtf8Bytes(figureProgram) <= FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES) { ... }
    require(figureImageOperation == null && figureImagePrompt == null) { "JS must not carry image payloads" }
} else {
    require(figureProgram == null) { "The JS program carrier is only valid for method=JS" }
}
```

`toFigureGenerationProposal` 改为按 method 分支：JS → `program = figureProgram`，图像字段为 null；
IMAGE_MODEL/旧调用 → 现有形状不变。

`RoomTutorToolRunner` 在本批（JS 执行尚未接线到工具环）最小安全做法：`figureMethod == JS` 时
**fail-closed 返回 UNAVAILABLE/`ok=false` 且痕迹 `method=JS`**，不要落进图像生成器。等 coordinator 的 JS 分支
（§1）与工具环形参（A-01 的另一半）接线后再切正向。**注意该文件开工即脏（他线 M）**：要改就整文件纳入并注明，
或按"停项登记"先只改 `core/model` 三处里的两处（构造器 + proposal），把 runner 的 fail-closed 作为下一批硬前置。

### 2.3 两路同果的测试矩阵（A-02 验收）

`core/data/.../model/TutorFigureMethodDualParseTest.kt`：

| 用例 | 期望 |
|---|---|
| `jsMethodParsesIdenticallyOnBothRoutesWithItsProgram`（翻转现有 `jsMethodIsRejectedOnBothRoutes`） | 两路 `TutorToolCall` 相等；`figureMethod=JS`；`figureProgram` **逐字节**相等（含 `\r\n`、反斜杠、中文、emoji） |
| `aJsProposalCarriesTheProgramAndNoImagePayload` | `toFigureGenerationProposal().program == program`，`imageOperation/imagePrompt == null` |
| `aProgramOnImageModelIsRejectedOnBothRoutes` | 保留（现有负向） |
| `jsWithoutProgramIsRejectedOnBothRoutes` | 保留（现负向翻向后补回） |
| `aProgramOverSixtyFourKibibytesIsRejectedOnBothRoutes` | 64 KiB+1 B 拒 |
| `jsWithAnImagePromptIsRejectedOnBothRoutes` | 互斥载荷 |
| `aProgramOutsideGenerateFigureIsRejected` | 其它工具带 program |
| `javascriptInTheDescriptionIsNotAProgram` | JS 无 program 但把源码塞 description → `JS_PAYLOAD_INVALID`（走 policy，不走"猜文本"） |

负向不得放宽：`aProgramPayloadIsRejectedOnBothRoutes`（IMAGE_MODEL + program）与 `anUnknownNativeArgumentIsRejectedJustLikeRouteB`
必须原样保留。

---

## 3. 任务三：请求 schema 的 `method.enum` 加 JS —— 兼容性影响与回滚（A-03）

### 3.1 先厘清"请求 schema"有两层

1. **模型可见的工具声明 JSON**（`OpenAiModelProtocol.strictFunctionSchema` 生成的 `method.enum`，`OpenAiModelProtocol.kt:443-461`）。
   它每次请求现生成，不进库。
2. **持久化请求形状**（`ModelTaskRequest` + `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`，`core/model/.../ModelTasks.kt:546-547`）。
   A-03 **不需要**动第 2 层：input 里只有 `toolDeclarations`（`List<TutorToolName>`，如 `core/model/.../TutorLobbyTasks.kt:38`；
   它在旧指纹路径里还被专门抹平，见 `ModelTasks.kt:1048-1057`），渲染出来的工具声明体（含 `method.enum`）
   是每次请求现生成的字符串，不是序列化字段，也不进指纹。

### 3.2 兼容性结论（逐条给依据）

| 问题 | 结论 | 依据 |
|---|---|---|
| 加 JS 会不会让旧请求行指纹变化？ | **不会**。指纹只吃 typed input（`ModelTaskRequest.fingerprintPayload`）与逻辑操作 input；工具声明体不参与 | `ModelTasks.kt:790-798`、`:800-829`、`:854-870` |
| 要不要升 `CURRENT_SCHEMA_VERSION`？ | **不要**。没有新增/改动序列化字段 | `ModelTasks.kt:546-547`、`:832-846` |
| 旧模型（不会输出 JS）受影响吗？ | 不受影响；声明里多个枚举值只是可选集合变大 | `OpenAiModelResponseParsers.kt:598-625` 解析不变 |
| 新模型输出 `JS` 但生产能力只有 IMAGE_MODEL 会怎样？ | policy 拒 `METHOD_NOT_AVAILABLE` → `REJECTED`，不出网 | `FigureGenerationPolicy.kt:131-136` |
| DB 里有 `method='JS'` 的行能读回吗？ | 能：`method` 是 TEXT、无 CHECK 约束；`TutorToolFigureTrace.method` 是同一枚举 | `FigureGenerationEntities.kt:135`、`TutorToolTrace.kt:133` |
| 回滚要迁移吗？ | 不要。把 enum 值/参数删回即可；已写入的 `method='JS'` 行仍可读（枚举仍含 JS，解析层本来就接受） | 同上 |

### 3.3 与"零广告"硬约束的冲突与解法

A-03 行要求"schema 同时广告 IMAGE_MODEL 与 JS"，批次 34 硬约束要求"能力快照/prompt/广告文本零改动直到全门绿"。
二者只有在**把广告做成能力参数**时同时成立：

- 给 `OpenAiModelProtocol.requestBody(...)` / `strictFunctionSchema(...)` 加一个
  `figureMethods: Set<FigureGenerationMethod> = setOf(IMAGE_MODEL)` 参数（默认值即生产现状）；
- enum 由该参数生成，**生产调用点不传** → 线上仍只广告 IMAGE_MODEL（零广告改动）；
- JVM 用例显式传 `setOf(IMAGE_MODEL, JS)` 证明"schema 能同时广告两方法且 `program` 是独立字段"（A-03 的 JVM 判据）。

**残余待裁定（不阻塞 T4 落码，但必须登记）**：若主会话把 A-03 读成"生产请求体里必须同时出现两方法"，那与本批硬约束
直接冲突，任何实现都无法同时满足——按纪律应作为阻塞项上报，而不是悄悄改生产广告。本文按"能力参数化、生产默认关"处理。

另一个不要踩的坑：不要用可变全局（`object { var }`）当广告开关；它是隐藏状态，会让 JVM 用例互相污染。

---

## 4. 任务三（续）：attempt 记录与"0 远程派发"的可观测断言

### 4.1 现状写入路径与本地尝试的硬约束

- 一次尝试的 id 形如 `figure-attempt:{op}:{ordinal}:{method}`，ordinal 来自**预留**（`DefaultFigureGenerationCoordinator.kt:416-431`）。
- 表上 `(operation_id, attempt_ordinal)` **唯一**（`FigureGenerationEntities.kt:123`），插入 `ABORT`（`FigureGenerationDao.kt:102-103`），
  异常被 recorder 吞掉 → `beginAttempt` 返回 false → `attemptId=null`（`RoomFigureGenerationAuditRecorder.kt:116-123`）。
- 因此**本地尝试不能借用预留序号，也不能恒定用 0/-1**：同一 operation 的换方法/重试（契约 `:163` 明确"换方法不换 operationId"）
  必然撞序，结果是"审计静默丢失"，而生成照旧成功——正是 attempt 表存在的意义被抵消。

### 4.2 建议：由持久层分配本地序号（schema 不变）

新增一条**只服务本地方法**的写口（DB schema 仍是 71，无迁移）：

```kotlin
// core/data 端口（FigureGenerationAuditRecorder）
suspend fun beginLocalAttempt(
    operationId: String,
    method: FigureGenerationMethod,
    inputFingerprint: String,
    sourceSha256: String?,
    startedAtEpochMillis: Long,
): FigureGenerationAuditRecordedAttempt?   // (attemptId, attemptOrdinal)

// core/database 端口（FigureGenerationRecorderPort）+ RoomStudyDatabase 实现
suspend fun beginLocalFigureGenerationAttempt(command: BeginLocalFigureGenerationAttemptCommand): FigureGenerationAttemptIdentity? = null
```

实现语义：`next = COALESCE(MAX(attempt_ordinal),0)+1 WHERE operation_id=?`，`attemptId = "figure-attempt:$op:$next:$method"`，
`reservation_id = NULL`，`status = RUNNING`，然后 `insertAttempt`。**两步必须在同一事务里**（否则并发/重试会撞唯一索引）。
事务形态有本仓先例可照抄：DAO 的 `@Transaction` 具体方法（`core/database/.../dao/AgentPendingRequestDao.kt:133-135`，
`internal abstract class`；`core/database/.../dao/KnowledgeGroundingDao.kt:207`，`internal interface`）。
`FigureGenerationDao` 现为 `internal interface`（`FigureGenerationDao.kt:24`），可加同形 `@Transaction` 具体方法；
`withTransaction` 在 `core/database/src/main` **0 命中**（`grep -rn withTransaction --include=*.kt core/database/src/main` → exit=1），
不要新引入那种风味。**仍需编译验证**的是：新增方法在 Room 生成代码后确实包住两条语句（本轮未运行 Gradle）。

若 T4 判断 `core/database` 不可动（该模块开工即脏 M 9 个文件），**诚实的降级**是：本批只落 §1 的引擎注入 + JS 分支 + 0 派发，
把"记 attempt"半边标为阻塞/停项，不要用恒定序号假装记上（会让第二次本地尝试静默丢行）。

### 4.3 顺带发现的审计缺陷（必须先登记，T3 用红测钉住）

1. **计数被覆盖成 0**：`finishOperation` 是绝对覆盖（`FigureGenerationDao.kt:62-100`），而每次 `generate()` 的状态对象从
   `reserved=0, used=0` 起（`DefaultFigureGenerationCoordinator.kt:405-406`）。同一 operation 先 IMAGE_MODEL（used=1）后 JS（0/0），
   第二次会把 `used_remote_dispatches` 冲成 0。
2. **`beginOperation` 对已存在行也返回 true**（`RoomStudyDatabase.kt:1319-1340`：`insertOperation` 返回值被丢弃，恒 `return true`），
   于是第二次调用会走 `finishOperation` 覆盖——缺陷 1 必然发生，而不是理论上的竞态。

建议修法（二选一，需 A-10 轨道确认口径）：
- (a) 计数查询改成 `used_remote_dispatches = used_remote_dispatches + :used`（真正的"累计"，符合契约 §8"实际费用计数"）；
- (b) operation 行改为"仅由最后一次尝试写"，并新增逐操作累计视图/查询。**不建议**：要改 schema 语义。

本批最小安全动作：**不改 DAO**，先落 `switchingMethodsOnTheSameOperationDoesNotZeroTheRemoteAuditCounts` 红测 +
在台账登记。JS 分支自身必须保证：JS-only 的 operation 写 `0/0`（这是"0 远程派发"的正向证据）。

### 4.4 "0 远程派发"四层可观测断言

| 层 | 断言 | 载体（可复用） |
|---|---|---|
| 协调器（fake） | `ledger.requests.isEmpty()`；`openBackend` 计数 0；`channel.generateCalls == 0` | `FigureGenerationCoordinatorTest.kt:67-81`、`:111-125` 同型 |
| HTTP | `MockWebServer.requestCount == 0`，并配**非空转对照**（同夹具 IMAGE_MODEL 臂 = 1） | `FigureHttpTransportBypassObservationTest.kt:96-143` 的既有手法 |
| 审计 | 终态 operation `reserved=0 / used=0`；attempt `method=JS`、`reservation_id=NULL` | `FigureGenerationAuditRecorderTest.kt:55-110` 的 Recording 替身 |
| 设备（A-09，后续批） | `model_task_operation.dispatch_count` 前后不变；`figure_generation_operation.used_remote_dispatches=0`；`figure_generation_attempt` 多一行 `method='JS'`；对照臂 IMAGE_MODEL 计数 +1 | 需新建 `VisualJsAdvertiseThenExecuteInstrumentedTest`（清单 A-09 建议名） |

第 2 层与第 4 层的"对照臂"是刻意的：只断言 0 而不证明观测器会计数，等于允许"观测失灵"的假绿
（`FigureHttpTransportBypassObservationTest.kt:63-68` 的 KDoc 已把这条写成纪律）。

---

## 5. 任务四：GitHub 多后端执行抽象参考评分

评分维度：**许可明确性 / 维护活跃度 / 可迁移性（同栈或模式可直接借鉴）/ 是否值得引入代码**。全部元数据为本轮
`gh api` 实取（2026-10-29），代码类事实取文件原文。

| 项目 | 许可（核实方式） | 活跃度 | 抽象形态（原文事实） | 可迁移性 | 结论 |
|---|---|---|---|---|---|
| **bazelbuild/bazel**（26,011★，Java） | Apache-2.0（`gh api repos/bazelbuild/bazel/license` → `LICENSE` / Apache-2.0） | pushed 2026-10-29 | `SpawnStrategy`：`canExec(spawn)` 能力判定 + `exec()` 返回 `ImmutableList<SpawnResult>`——KDoc 明写"first entry 是成功执行，列表可含失败重试条目"；`DynamicSpawnStrategy` 同时跑 local+remote 取先完成者（[SpawnStrategy.java](https://github.com/bazelbuild/bazel/blob/d79858980aeac1cd9592adacaef3f63d0a62aa0c/src/main/java/com/google/devtools/build/lib/actions/SpawnStrategy.java)、[DynamicSpawnStrategy.java](https://github.com/bazelbuild/bazel/blob/d79858980aeac1cd9592adacaef3f63d0a62aa0c/src/main/java/com/google/devtools/build/lib/dynamic/DynamicSpawnStrategy.java)） | **高**：同 JVM、接口形状与我们的 `FigureGenerationCoordinator`（canExec≈policy、exec≈generate、attempt 列表≈attempt 表）一一对应 | **不引代码，只借形状**。评分 4.5/5 |
| **microsoft/onnxruntime**（22,186★，C++） | MIT（`gh api repos/microsoft/onnxruntime/license` → `LICENSE`） | pushed 2026-10-29 | Execution Provider 按**优先级列表**注册；节点用 `GetCapability()` 分派给最高优先且能处理的 provider，否则下探，最终 CPU（[官方文档](https://onnxruntime.ai/docs/execution-providers/)：`"The list of providers is ordered by Priority."`、`"execute a node using CUDAExecutionProvider if capable, otherwise execute using CPUExecutionProvider."`） | **中**：我们是"整方法二选一"，不需要子图切分；但"优先级 + 能力回退"的规则可借鉴 | 不引代码。评分 3/5 |
| **gfx-rs/wgpu**（18,412★，Rust） | **两来源不一致**：`gh api repos/gfx-rs/wgpu/license` → `LICENSE.APACHE`/Apache-2.0；docs.rs 写 `MIT OR Apache-2.0` → 按**单一来源待验证**处理 | pushed 2026-10-27 | `Backends` 位标志（VULKAN/GL/METAL/DX12/BROWSER_WEBGPU），`from_env` 从 `WGPU_BACKEND` 读、`from_comma_list` 解析别名；`NOOP` 显式存在（[Backends 文档](https://docs.rs/wgpu-types/latest/wgpu_types/struct.Backends.html)） | **中低**：显式后端选择 + 环境覆盖的形态可借，fallback 语义该页未写 → **未验证** | 不引代码。评分 2.5/5 |
| **FFmpeg**（66,010★，C） | LGPL-2.1+，可选部件 GPL-2+（[ffmpeg.org/legal.html](https://ffmpeg.org/legal.html)）；GitHub 元数据 `license=NOASSERTION` 与官方页不一致 → 以官方页为准 | pushed 2026-10-29 | 内建 hwaccel：能硬件解码则"delegate all significant processing"，否则"still be decoded in software automatically"；**外部 wrapper 无回退**（[HWAccelIntro](https://trac.ffmpeg.org/wiki/HWAccelIntro)） | **低**：解码域特化，许可也最复杂 | 不引代码。评分 1.5/5 |
| **dokar3/quickjs-kt**（155★，C） | Apache-2.0（`gh api repos/dokar3/quickjs-kt/license` → `LICENSE.txt`，sha `6929566a…`；README §License 同） | pushed 2026-10-28；tag 到 `v1.0.15` | 我们**已经在用**的运行时（契约钉 commit `a081755b…`）；社区小、issue 29 | 不是抽象参考，是**依赖面事实**：`LICENSE.txt` 是 Apache-2.0；契约"保留 MIT/QuickJS 许可证"指的是 QuickJS 本体（**本轮未核 QuickJS 本体许可**） | 维持现状，勿因"star 少"换库。评分 N/A（依赖） |

**采纳决定**：不引入任何外部依赖/代码。`VisualExecutionEngine` 端口已经是正确的策略接口；只需按 Bazel 的两条经验
把语义钉死：① 后端选择来自**能力判定**（policy），不是入口；② 一次执行 = 一条 attempt 记录（成功与否都在列表里）。

---

## 6. 未验证项

1. **真实设备/真实模型行为**：本轮零 Gradle、零设备。所有"会绿"均为设计预期，不是执行结果。
2. **本地序号分配的事务生成**：`@Transaction` DAO 具体方法在本仓有先例（`AgentPendingRequestDao.kt:133-135`、
   `KnowledgeGroundingDao.kt:207`），但新方法"读 max + 插入"是否被 Room 生成的一层事务包住，**未编译验证**（本轮零 Gradle）。
3. **`VisualJsClientEngine` 生产装配可行性**：L3 客户端仍无生产构造点（契约 §5.1 `:125`）；`app` 侧 `AndroidVisualExecutionEngine`
   仍不存在（契约 §5.1 `:120`）。T3 的 JVM 用例只能用 `FakeVisualExecutionEngine`，**不能**据此声称"设备可执行"。
4. **wgpu 的 fallback 语义**：docs.rs 页未覆盖，未从官方文档核实。
5. **QuickJS 本体许可（MIT）**：本轮只核实了 wrapper 仓库（Apache-2.0），未取 QuickJS 上游许可原文。
6. **设备侧 attempt/计数断言**（A-09 四层里的第 4 层）未落任何代码或证据。

## 7. 待裁定 / 停项登记

1. **A-03 生产广告口径**：本批按"能力参数化、生产默认 IMAGE_MODEL-only"实现；若要求生产请求体立即同时广告两方法，
   与批次硬约束冲突 → 阻塞项。
2. **`core/domain/FigureGenerationCoordinator.kt:19-20` 的 KDoc**：JS 分支落地后即过时，但 core/domain 是热区禁改 → 停项登记，
   热区解锁批同步。
3. **`RoomTutorToolRunner.kt`（他线 M）**：JS fail-closed 分支要么整文件纳入并注明，要么停项登记（见 §2.2）。
4. **operation 审计计数覆盖缺陷**（§4.3）：建议归 A-10（每逻辑操作预算）轨道，本批只落红测 + 登记。
5. **本地 attempt 序号分配**：若 `core/database` 被视为不可动，A-01 的"记 attempt"半边按阻塞上报（§4.2 末段）。

## 8. 来源

- 本地源码（本轮实读，行号见正文）：`core/data/.../visual/DefaultFigureGenerationCoordinator.kt`、
  `core/data/.../visual/FigureGenerationAuditRecorder.kt`、`core/data/.../visual/RoomFigureGenerationAuditRecorder.kt`、
  `core/data/.../model/OpenAiModelProtocol.kt`、`OpenAiModelResponseParsers.kt`、`TutorFigureMethodDualParseTest.kt`、
  `core/data/.../study/RoomTutorToolRunner.kt`、`core/model/.../TutorToolLoop.kt`、`FigureGeneration.kt`、
  `FigureGenerationPolicy.kt`、`ModelTasks.kt`、`core/domain/.../VisualExecutionEngine.kt`、
  `FigureGenerationCoordinator.kt`、`RemoteModelDispatchLedger.kt`、`core/database/.../FigureGenerationEntities.kt`、
  `FigureGenerationDao.kt`、`RoomStudyDatabase.kt`、`app/.../SmartMistakeBookApplication.kt`、
  `docs/current/visual-generation-contract.md`、`docs/current/figure-generation-prompt-policy.md`。
- GitHub（`gh api`，2026-10-29 实取）：
  - https://github.com/bazelbuild/bazel/blob/d79858980aeac1cd9592adacaef3f63d0a62aa0c/src/main/java/com/google/devtools/build/lib/actions/SpawnStrategy.java
  - https://github.com/bazelbuild/bazel/blob/d79858980aeac1cd9592adacaef3f63d0a62aa0c/src/main/java/com/google/devtools/build/lib/dynamic/DynamicSpawnStrategy.java
  - https://onnxruntime.ai/docs/execution-providers/
  - https://docs.rs/wgpu-types/latest/wgpu_types/struct.Backends.html
  - https://ffmpeg.org/legal.html 、https://trac.ffmpeg.org/wiki/HWAccelIntro
  - https://github.com/dokar3/quickjs-kt （`LICENSE.txt` sha `6929566ac66cd4484861ad3e73b5977fef965ec9`，tag `v1.0.15`）
