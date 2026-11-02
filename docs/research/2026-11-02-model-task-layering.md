# 6B-3 新模型任务类型的分层研究（批次 40 / R4，驱动 WS4 六项）

- 日期：2026-11-02
- 研究线：R4「6B-3 任务分层」（计划 `.zcode/plans/plan-2026-11-02-batch-40-5x.md:86`）
- 服务对象：WS4 六项（`:43-49`）：23 归属裁定、24 DTO + 请求 schema 19、25 gateway + 预算/ledger、26 提示词模板、27 响应解析与失败分类、28 export/tutor 注入接缝
- 方法：读仓内一手源码与契约文档；`gh api` 取仓库元数据与 README；官方文档站取行为说明。**未运行 Gradle、未跑测试、未做 git 写操作**（共享纪律：门禁由脚本串行执行）。
- 结论性质：本文是**设计裁定与牵动面清单**，不是实现完成声明；所有"编译期会强制"的判断属语言语义推断（见 §8）。

---

## 0. 结论摘要

| # | 议题 | 裁定 | 依据 |
|---|---|---|---|
| 1 | 新 `ModelTaskKind` 放哪层 | **`core/model`**，与 `ModelTaskInput`/`ModelTaskOutput` 同模块同包 | `sealed interface` 的子类型只能声明在同一模块（`core/model/.../ModelTasks.kt:159-160`、`:560-561`）；`core/model` 只依赖 kotlinx-serialization（`core/model/build.gradle.kts:29`）；`core:data` 单向依赖 `core:model`（`core/data/build.gradle.kts:169-171`） |
| 2 | DTO 与提示词的归属 | **输入/输出 DTO 在 `core/model`；wire 提示词与响应解析在 `core/data/model`** | 同上；提示词现状即 `core/data/.../model/OpenAiModelTaskAdapters.kt:65-110` 的按 input 类型分派 |
| 3 | 请求 schema 18→19 | **要 bump，但只是"版本声明 + 一条输入-版本守卫"，不是字段级迁移**；不需要新增任何 `withoutEmpty*` 指纹抹平 | `CURRENT_SCHEMA_VERSION = 18`（`ModelTasks.kt:546-547`）；schema 18 只来自 `TutorVisualContext` 的 `@EncodeDefault(NEVER)`（`:540-546`）；新 kind 是新增子类型，不改变旧行编码（推断，见 §8） |
| 4 | 预算/ledger 接入 | **零新增 schema、零新增 DDL**：账本按 `operation_fingerprint` 与 `dispatch_count` 走，kind 只作为指纹输入之一 | `ModelTaskLogicalOperationFingerprint.of` 把 `input.kind.name` 拼进 payload（`ModelTasks.kt:809-829`）；预留/上限在 `ModelTaskTransactionDao.kt:244-...`、`:325-...`、`:93-108` |
| 5 | 与 FIGURE_GENERATION / ADVISORY 的复用面 | **两者都不是 `ModelTaskKind`**：前者是工具 + `figure_generation_*` 审计表 + 共享账本，后者是工具 `ADVISORY_READ/WRITE`。可复用账本/预算/失败分类，**不可复用为任务类型**，也**不应**把改卷塞进 TUTOR_RESPOND 的工具环 | `TutorToolName` 枚举（`core/model/.../TutorToolLoop.kt:12-33`）；契约明令"不得新增视觉任务类型"（`docs/current/model-task-contracts.md:53`）；共享预算声明（`core/data/.../model/FigureGenerationBudgets.kt:12-22`） |
| 6 | GitHub 参考 | **不引入任何外部代码或依赖**；只采纳 4 个模式（类型化函数定义、校验-重试分类、fail-closed 预算、幂等预留）。Kotlin/Android 生态没有可信的同类编排库 | §6 评分表；`gh search repos "llm kotlin"` 结果全为 0–1 star（命令见 §9） |
| 7 | 注入接缝 | 本期只落 **core 侧任务链 + 中立 DTO + 映射契约**；`feature/tutor` 的 `TutorPaperEditAssistant` 端口与其提示词**保持不动**（热区），app 装配登记为热区欠账 | 端口与提示词在 `feature/tutor/.../TutorPaperEditAssistant.kt:17-35`；其 KDoc 自述"本批不拥有新的 ModelTaskKind"（同文件 `:20-25`） |

---

## 1. 现状事实（本次读到的代码面）

### 1.1 `ModelTaskKind` 与它的闭合面

当前 12 个 kind（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:12-25`）。新增一个值会同时触发：

| 闭合面 | 位置 | 机制 |
|---|---|---|
| 输入/输出类型分派 | `ModelTasks.kt:159-160`、`:560-561` | `sealed interface`：新子类型必须在同模块；`core:data` 无法扩展 |
| 提示词策略版本 | `ModelEgress.kt:88-103` | `currentFor(kind)` 是穷尽 `when`，新增枚举值不更新即编译失败 |
| 完成校验 | `core/model/.../KnowledgeQuizTasks.kt:122-183` | 对 `request.input` 的穷尽 `when` |
| wire 适配 | `core/data/.../model/OpenAiModelTaskAdapters.kt:65-110` | `prompt()` / `parse()` 两个穷尽 `when` |
| 提供方能力声明 | `core/data/.../model/OpenAiCompatibleModelGateway.kt:547-585` | `supportedTasks` 是**手写 buildSet**，不加则该 kind 永不被 `provider.supports(kind)` 认可 |
| 备份/恢复 kind 归类 | `core/data/.../backup/SmbkSelectionPlan.kt:514-527` | 恢复侧对未知 `task_kind` **fail-closed 拒包**（`RestoreSoftReferenceRecheck.kt:16-25`） |
| 测试硬编码 | `core/data/src/test/.../SmbkSelectionPlanTest.kt:257`；`core/model/src/test/.../TutorVisualContextFingerprintTest.kt:62-66` | 断言"12 个已知 task_kind"与"schema 是 18" |

后两条是**静默面**（不更新不会编译失败，但会在恢复或测试时炸）：kind 归类缺失 → 备份恢复拒收；计数断言缺失 → 无人发现映射漏项。

### 1.2 请求 schema 18 到底是什么

`ModelTaskRequest.schemaVersion` 是**请求逻辑形状的版本**，当前 = `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`（`ModelTasks.kt:540-547`）。它的作用只有两个，都能在代码里指到：

1. **旧行读回时的指纹复原**：`fingerprintPayload()` 对 `schemaVersion == 1` 走 legacy 编码，对其余版本按"该版本当年没有哪些键"逐项 strip（`ModelTasks.kt:854-996`，每处都有 `schemaVersion < X_SCHEMA_VERSION` 门控）。
2. **构造期守卫**：`ModelTaskRequest.init` 用 `schemaVersion >= X` 阻止旧版本承载新字段（`ModelTasks.kt:424-480`，共 12 条）。

两个指纹是分开的：请求指纹 `ModelTaskFingerprint`（含 schemaVersion/requestId/时间，`ModelTasks.kt:788-792`、`:854`）与逻辑操作指纹 `ModelTaskLogicalOperationFingerprint`（只有 `kind.name` + 输入 JSON，`ModelTasks.kt:802-830`）。

另有一条**独立的**版本轴：`ModelEgressManifest.schemaVersion` 当前 = 7（`ModelEgress.kt:356-357`），它管出网披露清单的词汇表与分支可用性，**不是**请求 schema。历史上 `KNOWLEDGE_QUIZ` 就是在 `schemaVersion >= 5` 的清单门控下进场的（`ModelEgress.kt:304-311`）——说明"加 kind"与"清单 schema"曾经耦合过，但耦合点是**清单自己的版本**，不是请求版本。

### 1.3 预算/账本的真实形态

- 上限是常量 6 次/逻辑操作（`ModelTasks.kt:60-67`）。
- 权威在 `model_task_operation.dispatch_count`：`reserveRemoteDispatch` 用 `UPDATE ... WHERE dispatch_count < :maxDispatches` 做原子自增（`ModelTaskTransactionDao.kt:93-108`、`:244-...`）；任务行不存在时 fail-closed 返回 `applied=false`（`:325-...` 的 `reserveOperationRemoteDispatch`，语义见 `:318-323` 注释）。
- 逻辑操作身份 = `kind.name + 编码后的输入`（`ModelTasks.kt:809-829`）。因此**新 kind 天然获得自己的 6 次额度**，不与讲题轮共享；这正是"塞进 TUTOR_RESPOND 工具环"与"独立 kind"的实质差别。
- 图像派发复用同一账本，通过 `FigureDispatchParent.ModelTask(requestId, reserveChatFinalAnswer)` 挂到父操作上（`core/model/.../FigureGeneration.kt:260-291`）；预算声明把"上限"与"余量"分开，`null` = 未预读、交给账本判定（`FigureGeneration.kt:174-216`）。

### 1.4 `FIGURE_GENERATION` 与 `ADVISORY` 的真实身份

- `FIGURE_GENERATION` **不是** `ModelTaskKind`。它是：
  - `TutorToolName.GENERATE_FIGURE` 工具（`core/model/.../TutorToolLoop.kt:27-32`）；
  - `figure_generation_operation` / `figure_generation_attempt` 两张审计表（`core/database/.../FigureGenerationMigration_68_69.kt:35-47`）；
  - 共享 coordinator/policy/ledger（`core/data/.../model/FigureGenerationBudgets.kt:12-22`）；
  - 契约明文："**不得新增视觉模型任务类型**"（`docs/current/model-task-contracts.md:53`）。
- `ADVISORY` 同样不是 kind：`ADVISORY_READ` / `ADVISORY_WRITE` 是讲题工具环里的两枚读/写工具（`TutorToolLoop.kt:19-26`；实现 `core/data/.../study/RoomTutorToolRunner.kt:1373-1600` 一带），它们**寄生在** `TUTOR_PLAN`/`TUTOR_RESPOND` 的既有任务里。

这就是仓库已经做过的两次"不加 kind"的裁定。6B-3 要加 kind，必须说清它为什么不同（§2.4）。

---

## 2. 议题一：新 kind 的归属层与请求 schema 19

### 2.1 裁定：`core/model`

三条独立证据：

1. **语言级**：`ModelTaskInput` / `ModelTaskOutput` 是 `sealed interface`（`ModelTasks.kt:159-160`、`:560-561`）。Kotlin 不允许跨模块扩展 sealed 子类型，所以新输入/输出类型**只能**写在 `core/model` 同一包。这不是风格选择，是编译约束。
2. **依赖方向**：`core:model` 是叶子，只有 kotlinx-serialization（`core/model/build.gradle.kts:9,29`）；`core:data -> core:domain + core:model + core:database`（`core/data/build.gradle.kts:169-171`）；架构契约写死"`feature:*` 不得依赖具体 `core:data` 类、模型解析器不得写库"（`docs/current/architecture-contract.md:6-16`）。
3. **既有先例**：6B-3 的命令白名单/解析器已经在 `core:model`，文件头写明了理由（`core/model/.../PaperEditCommand.kt:21-25`：纯 JVM、已有 JSON 依赖、feature 没有直接声明 JSON 依赖）。

命名建议：`PAPER_EDIT`（枚举常量）。**这个名字是持久契约**——它进 `model_task.task_kind`、`model_task_operation.task_kind`、备份包、以及逻辑操作指纹（`ModelTasks.kt:811`）。改名会让写入/迁移路径的完整性自检抛 `LearningLedgerIntegrityException`：`verifyIntegrity` 同时核对 `request.input.kind.name != taskKind` 与 `operationFingerprint != ModelTaskLogicalOperationFingerprint.of(request)`（`core/database/.../dao/ModelTaskTransactionDao.kt:492-503`）；读路径 `toSnapshot` 为性能刻意跳过这两项（同文件 `:472-477`），所以脏数据不会立刻崩，而是**在下一次 transition/reserve 或迁移时**才炸——这比立刻崩更难查。因此必须有一条 golden 测试钉住 `ModelTaskKind.PAPER_EDIT.name == "PAPER_EDIT"` 与 wire `@SerialName`。

### 2.2 牵动面清单（改一处先枚举）

| # | 对象 | 改法 | 不改的后果 |
|---|---|---|---|
| 1 | `ModelTaskKind` 枚举 | 加 `PAPER_EDIT` | — |
| 2 | 新 `PaperEditInput : ModelTaskInput` | 新类型 + `kind`/`subjectId` + `isAgentConsentEligible = true` + `requestsImageBytes = false` | 默认 `false` 会强制走清单路径，而清单没有对应 purpose（§2.3） |
| 3 | `ModelPromptPolicyVersions.currentFor` | 加 `PAPER_EDIT -> "paper-edit-v1-whitelist-commands"` | 穷尽 `when` 编译失败；且清单路径 `currentFor` 返回 null 会拒（`ModelEgress.kt:671-674`） |
| 4 | `ModelTaskCompletionValidator.validate` | 加输出类型分支 | 穷尽 `when` 编译失败 |
| 5 | `OpenAiModelTaskAdapters.prompt/parse` | 加分支 | 穷尽 `when` 编译失败 |
| 6 | `OpenAiCompatibleModelGateway.toCapabilities().supportedTasks` | 结构化输出通过时 `add(PAPER_EDIT)` | **静默**：全局同意路径 `provider.supports(kind)` 恒 false，任务永远 `EGRESS_AUTHORIZATION_REQUIRED`（`ModelEgress.kt:567-571`、`:593-597`） |
| 7 | `ModelTaskRequest.init` | 加 `schemaVersion >= PAPER_EDIT_SCHEMA_VERSION \|\| input !is PaperEditInput` | **静默**：schema 18 的请求可带新 kind，破坏"版本=形状"不变量 |
| 8 | `SmbkSelectionPlan.modelTaskKindCategory` | 加 `"PAPER_EDIT" to <类别>` | **静默且致命**：备份恢复把该 kind 判为"映射外"直接拒包（`RestoreSoftReferenceRecheck.kt:68-80`） |
| 9 | `SmbkSelectionPlanTest` | 12 → 13 | 单测红，但红在别处，容易误判成本批回归 |
| 10 | `TutorVisualContextFingerprintTest.theRequestSchemaIsEighteen` | 18 → 19 | 单测红。**注**：这是版本钉子的重钉，不是放宽断言 |
| 11 | `ModelTaskInput.isAgentConsentEligible` 的 KDoc | 把改卷轮次写进"哪些轮次可在全局同意下发"的清单（`ModelTasks.kt:164-172`） | 文档与行为不一致，安全审查失去唯一清单 |
| 12 | `ModelEgressManifest` 的三条 purpose 分支 | **不改**（见 §2.3） | 改了就要 egress schema 8，收益为零 |

### 2.3 请求 schema 19：要 bump，但最小化

**为什么 bump**（按真实性排序，第一条是"不要拿错理由"）：

1. **它不改变跨版本读回行为**。旧构建读到 `PAPER_EDIT` 行时，失败发生在 `ModelTaskInput` 的 discriminator 解码——未知 `type` 直接抛序列化异常，**早于** `ModelTaskRequest.init` 的 `schemaVersion in 1..18` 守卫（`ModelTaskCodec.decodeRequest` 是一次性 `decodeFromString`，`ModelTasks.kt:746-766`）。也就是说 bump 与否，旧构建都是 fail-closed，**不能**把"更友好的拒绝"当作理由。
2. **真正的收益是内部不变量**：仓库把 `schemaVersion` 当作"这一版请求允许出现哪些形状"的唯一声明（12 条构造期守卫，`ModelTasks.kt:424-480`；每个常量都带"哪一版引入什么"的注释，`:487-547`）。新增一个输入子类型而不 bump，会让"schema 18 的请求不可能带改卷输入"这条不变量失去可检验的锚点。
3. **计划与准入清单已把它写成批次验收**（`.zcode/plans/plan-2026-11-02-batch-40-5x.md:45`），且这是 6B-3 的历史待办（批次 17/18 记录均把它列为"请求 schema 19"未做项）。

**为什么"最小化"**：

- **不需要**任何 `withoutEmpty*` 指纹抹平。所有 strip 都是 `input is XxxInput` 类型守卫（`ModelTasks.kt:1009-1117`），旧行不可能是 `PaperEditInput`，所以请求指纹与逻辑操作指纹对旧行逐字节稳定。这是与 schema 17/18 的**本质差别**：那两次是给**已有类型加字段**，才需要按版本抹键。
- **不需要**新 DDL、不需要迁移、不需要 KSP 清单表数变化。`model_task_operation.task_kind` 是 `TEXT`（KSP 生成物 `core/database/build/generated/ksp/debug/.../StudyDatabase_Impl.kt:495`），没有 CHECK 约束。
- **需要**一条构造期守卫（上表 #7）和一条"schema 18 + PaperEditInput 必抛"的用例。

**是否新增 egress purpose？不。** `ModelEgressManifest` 的 purpose 只有三个，且 `authorize` 在 init 里要求 `CAPTURE_TO_DOCUMENT` 的 kind 集合恰为 `{CAPTURE_ASSESS, CAPTURE_PARSE}`、`CLASSIFICATION` 恰为 `{PROBLEM_CLASSIFY}`（`ModelEgress.kt:280-303`、`:343-352`），`TUTORING` 的 `when` 只认 4 个 kind（`:318-329`）。把改卷塞进任何一支都是语义错配；新开第四支要连带 egress schema 8 + 新披露集 + 一批测试。而生产里**根本没有为它构造清单的调用点**——契约层已经把"生产造不出来的清单分支"删过一轮，理由写在 `ModelEgress.kt:744-749`。沿用同一条裁定：**走全局同意（`isAgentConsentEligible = true`），不造清单路径**。

### 2.4 为什么必须是新 kind，而不是复用 TUTOR_RESPOND 工具环

反方论点成立的地方：`ADVISORY_*` / `GENERATE_FIGURE` 都是"不加 kind"的成功先例。它们共同的前提是——**它们发生在一次讲题轮次内**，有父操作、有会话、有轮次序号。

改卷不满足这个前提，四条差异都是结构性的：

| 维度 | 讲题工具环 | 改卷 |
|---|---|---|
| 触发点 | 会话内下一轮 | 导出 sheet 的独立动作（`TutorConversationViewModel.kt:612-620` 一带） |
| 父操作 | 有（`requestId`，`FigureDispatchParent.ModelTask`） | 无；它本身就是根操作 |
| 输入事实 | 会话 + 题面 + 证据 | 一份**卷面副本**（`MistakePdfPaperEdit`） |
| 预算语义 | 与图像共享 6 次，且必须给终答留 1 次（`FigureGeneration.kt:183-184`、`ModelTaskTransactionDao.kt:344`） | 独立 6 次；没有"终答"概念，**不应**预留 |

如果塞进 `TUTOR_RESPOND`：改卷会烧掉讲题轮的派发额度并占用终答保留位；卷面全文会进入讲题轮的请求指纹（换一份卷面 = 换一个逻辑操作，但讲题轮的 `requestId` 没变，会撞不可变载荷冲突）；而且导出 sheet 往往**没有会话上下文**，构造不出合法的 Respond 输入。这四条里任意一条都是真失败，不是"更干净"的偏好。

---

## 3. 议题二：DTO 与提示词归属

### 3.1 硬约束：DTO 不能直接复用 `MistakePdfPaperEdit`

`MistakePdfPaperEdit` 在 `core:export`（`core/export/src/main/kotlin/.../MistakePdfPaperEdit.kt:26`）。依赖图是 `core:export -> core:model`（`core/export/build.gradle.kts:23`），而 `core:data` 只看得到 `core:model/domain/database`（`core/data/build.gradle.kts:169-171`）。所以：

- `core:model` 无法引用 `MistakePdfPaperEdit`（会反向依赖，且 core:model 是叶子）；
- `core:data` 也无法引用它；
- **能同时看到两边的只有 `feature/tutor` 或 `app`**——都是热区。

结论：core 侧必须定义一个**中立、可序列化、有界**的卷面快照 DTO；`MistakePdfPaperEdit -> 快照` 的映射属于热区接缝，本批只出契约与登记，不改 feature。

### 3.2 建议的输入 DTO 形状（`core/model`）

```
PaperEditInput(
  paperId: String,                 // subjectId
  revision: Long,                   // 宿主侧卷面版本，用于 CAS 丢弃过期结果
  instruction: String,              // ≤ 2000 字，与学生输入框同限
  questions: List<PaperEditQuestionSnapshot>,  // entryId, title, blocks[], note?
  removedQuestions: List<PaperEditRemovedQuestion>, // entryId, title（restore 的候选）
  includeNote: Boolean,             // 版式是否含备注区
)
```

字段来源与预算都可以从既有实现抄口径，避免新造一套：`TutorPaperEditPrompt.build` 已经在用同样的信息集（题序/entryId/标题/块 id+类型+文字/选项 id+文字/已删来源题；`feature/tutor/.../TutorPaperEditAssistant.kt:44-125`），解析器上限也是现成的（`PaperEditCommand.kt:118-134`：payload 32 768、24 条命令、id 160、title 400、note 2000、text 8000、offset 1000）。

三个必须显式写进 init 的判据（每一条都对应一个真实失败）：

1. 块 id 在**同一题内**唯一、题 entryId 全局唯一 —— 否则模型给的 `blockId` 会命中错块，applier 只能整批拒。
2. 快照总字节 ≤ 一个显式上限（建议复用 `MAX_PROMPT_CHARS = 12_000` 的口径）——否则请求体在 `ModelTaskCodec.MAX_ENCODED_CHARS`（512 000，`ModelTasks.kt:734`）之前就先被撑爆，失败点落在离原因很远的地方。
3. `instruction.isNotBlank()` 且 ≤ 2000 字 —— 与学生输入框同一条预算，避免"UI 允许、模型层拒"。

### 3.3 提示词：`core/data/model`，但要处理"第二份提示词"

现状：**提示词已经在 `feature/tutor`**——`TutorPaperEditPrompt`（`feature/tutor/.../TutorPaperEditAssistant.kt:35-125`），且其 KDoc 自述"生产适配器的提示词必须用它"（`:17`）。计划要求新提示词落 `core/data model`（`.zcode/plans/plan-2026-11-02-batch-40-5x.md:47`）。

这就是一处**真实的双源风险**：两份提示词各自声明七命令白名单，任一份改了而另一份没改，就会出现"模型按 A 写、解析器按 B 拒"或反过来。处理办法（本批可做、且不动热区）：

- 在 `core/data/.../model/` 新建 `PaperEditPrompt`，**从 `PaperEditCommand.TYPES` 生成命令清单**（`PaperEditCommand.kt:88-97` 已经是"提示词与解析器共用"的声明，注释原话）；
- 新增一条 `core/model` 测试，把解析器当**唯一 oracle**：对每个 `TYPES` 项构造最小合法命令必须被接受、加一个白名单外键必须被拒。这样提示词侧的类型表无法静默漂移；
- `feature/tutor` 那份提示词**不动**，作为"当前唯一在用的入口"登记为热区欠账（它只在 `paperEditAssistant != null` 时可达，而生产 app 未注入，见 §7）。等 feature 热区释放后，把 feature 端口改为转调 core/data 的提示词，删掉重复。

**不做的**：不把提示词搬进 `core:model`。理由：提示词是 wire 事实（和 `OpenAiModelTaskAdapters` 里那一堆 prompt 一致），而 `core:model` 目前只有 DTO/校验；把提示词塞进去会让"模型层纯数据"的边界失效，且 `ModelPromptPolicyVersions` 已经承担了"版本在 core、文本在 data"的分工（`ModelEgress.kt:40-104` 只有版本串，文本在 `OpenAiModelTaskAdapters`）。

### 3.4 输出 DTO 与"两段解析"

`PaperEditCommand` 没有 `@Serializable`（`PaperEditCommand.kt:27-99`），所以输出 DTO **不能**直接内嵌命令列表去序列化。建议：

```
PaperEditOutput(val commandsJson: String, val modelVersion: String) : ModelTaskOutput
```

- `commandsJson` 存**经解析器验证过**的原始命令数组文本（有界）；
- gateway 侧 `parse()` 调用**同一个** `PaperEditCommandParser.parse`（core:model 的公开 API），`Rejected` → `InvalidModelResponseException` → 既有失败分类面归 `INVALID_RESPONSE`（`OpenAiCompatibleModelGateway.kt:466-479`）；
- applier 侧仍按 `commandsJson` 走原解析器（feature 端口返回 `String` 的签名不变），保证两侧**同一套形状判据**。

这条设计消灭的具体失败：**gateway 判 SUCCEEDED、applier 判非法**——即任务在账本里成功、学生看到的却是"改卷失败"，且失败原因在另一层、无法从任务行追。若只做 gateway 裸解析（不校验形状），这个分叉是必然出现的。

预算/失败分类的映射（复用既有出口，不新造枚举）：

| 情形 | 归类 | 依据 |
|---|---|---|
| 输出 JSON 超 `MAX_PAYLOAD_CHARS` | `OUTPUT_BUDGET_EXCEEDED`（可重试） | 与 KD-38 同出口（`OpenAiCompatibleModelGateway.kt:454-465`） |
| 未知键 / 类型不符 / 枚举外 type | `INVALID_RESPONSE`（不可重试） | 形状错误重发逐字复现（`ModelTextBudget.kt:5-10` 的判据说明） |
| 上游 `finish_reason=length` 截断 | `INCOMPLETE_OUTPUT`（可重试） | 截断分流在解析前（`OpenAiCompatibleModelGateway.kt:443-453`） |
| 401/403、429、5xx | `AUTHENTICATION_FAILED` / `RATE_LIMITED` / `SERVICE_UNAVAILABLE` | `:406-428` |

非流式：`usesOpenAiSse()` 只认三个 tutor input 类型（`OpenAiCompatibleModelGateway.kt:926-927`），新类型默认非流式。**不要**给改卷开流式：一次改卷是一整份 JSON，半截流没有消费方。

---

## 4. 议题三：预算/ledger 接入

### 4.1 免费继承的部分

只要请求走 `ModelTaskRepository.execute(request)`（`core/domain/.../ModelGateway.kt:25-46`），下面全部自动成立，**零改动**：

| 能力 | 机制 | 位置 |
|---|---|---|
| 逻辑操作身份 | `kind.name + 输入 JSON` 的 SHA-256 | `ModelTasks.kt:802-830` |
| 6 次派发上限 | `dispatch_count < MAX_DISPATCHES` 的原子 UPDATE | `ModelTaskTransactionDao.kt:93-108` |
| 预留先于出网 | `reserveRemoteDispatch` 成功后任务才进 RUNNING | `ModelTaskTransactionDao.kt:244-...` |
| 幂等/重放 | 不可变请求载荷冲突检测 + 事件行 | `ModelTaskTransactionDao.kt:172-238` |
| 旧行完整性 | `verifyIntegrity` 重算请求指纹 + 操作指纹 | `ModelTaskTransactionDao.kt:485-503` |

### 4.2 需要显式决定的三件事

1. **`reserveChatFinalAnswer` 不适用**。该位只对讲题父操作有意义（`FigureGeneration.kt:183-184`、`ModelTaskTransactionDao.kt:344`）。改卷不是对话轮，不应预留终答位；也不该被图像派发挂成父操作。
2. **不能共享讲题轮的额度**。这是"独立 kind"的直接收益：同一学生会话里改卷 5 次不会让讲题轮只剩 1 次。反过来，如果未来要把改卷做成讲题工具，必须显式声明共享——那是一次产品裁定，不是实现细节。
3. **kind 名进指纹 = 改名即断代**。`operationPayload` 第一行就 `append(input.kind.name)`（`ModelTasks.kt:811`）。因此 `PAPER_EDIT` 这个串必须被 golden 测试钉住；否则某次"命名统一"的重构会让历史改卷行的逻辑操作指纹失配——读路径不会立刻报错（`toSnapshot` 跳过校验），而是在下一次 transition/reserve 或迁移时抛 `LearningLedgerIntegrityException`（§2.1）。

### 4.3 不需要的东西

- 不需要新的预算表/新列：`model_task_operation` 的 `subject_id + task_kind` 索引已经覆盖新 kind 的查询（KSP 生成物 `StudyDatabase_Impl.kt:496`）。
- 不需要"用户级日配额"：仓内没有这个机制，litellm 那种 key/team budget（§6）是服务端多租户概念，与单机 Room 账本不同层；引入会新增一个无人消费的余额字段（违反"新增必须指认消灭的失败"）。

---

## 5. 议题四：与 FIGURE_GENERATION / ADVISORY 的复用面

| 面 | 能否复用 | 说明 |
|---|---|---|
| 任务类型 | **否** | 两者都不是 kind（§1.4）；契约明令不加视觉 kind（`docs/current/model-task-contracts.md:53`） |
| 派发账本 | **是（自动）** | 同一张 `model_task_operation` 表、同一 `MAX_DISPATCHES`；无需 `FigureDispatchParent` |
| 预算声明对象 | **否** | `FigureGenerationBudget` 带 `reserveChatFinalAnswer` / `sharesOperationBudgetWithToolRounds` 两个讲题语义（`FigureGeneration.kt:186-216`），改卷用不上；照抄会引入两个恒为 false 的字段 |
| 严格解析 | **是（模式）** | `requireOnlyKeys` 白名单拒绝（`OpenAiModelResponseParsing.kt:44-46`）；改卷已有同级解析器（`PaperEditCommand.kt:159-207`） |
| 失败分类 | **是** | `ModelFailureCode` + 网关的 401/429/5xx/截断/越界分流（`OpenAiCompatibleModelGateway.kt:406-479`） |
| 诊断捕获 | **是（自动）** | `reportModelCallFailure` / `ModelCallFailureDiagnostics` 按 `execution` 记录，kind 只是字段（`ModelCallFailureDiagnostics.kt:29-45`） |
| 审计表 | **否** | `figure_generation_*` 是图产物审计，改卷没有产物字节 |
| 工具环 / 工具声明 | **否（反模式）** | 塞进 `TUTOR_RESPOND` 会烧讲题额度、占终答位、把卷面写进讲题指纹（§2.4） |

一句话：**复用"账本 + 失败分类 + 白名单解析"这三样与 kind 无关的基础设施；不复用任何带讲题/图像语义的类型。** 连带地，`FigureGenerationCapabilities.availableMethods`（广告面）**零改动**：改卷不是图方法，把它写进 `availableMethods` 等于广告一个不存在的后端。

---

## 6. 议题五：GitHub 参考评分

### 6.1 评分口径

四项各 1–5 分，加权：结构贴合 ×0.35、可复用成本 ×0.25、维护度 ×0.25、预算/失败分类可借鉴 ×0.15。可复用成本 1–5（**5 = 可直接进 Android/Kotlin 且不破坏既有边界，1 = 完全不可移植**）。

元数据均来自 `gh api repos/<owner>/<repo>`（2026-11-02 实测，命令见 §9）。

### 6.2 评分表

| 仓库 | License | Stars | 最近 push | 结构贴合 | 可复用成本 | 维护度 | 预算/分类 | 加权 | 裁定 |
|---|---|---|---|---|---|---|---|---|---|
| `BoundaryML/baml` | Apache-2.0 | 9 468 | 2026-10-31 | 5 | 2 | 4 | 2 | **3.55** | 只借"类型化函数 = 输入类型 + 输出类型 + prompt + client"这一形状；运行时要 native 二进制 + 代码生成，Android 不可行 |
| `BerriAI/litellm` | MIT（`enterprise/` 子目录另有许可） | 62 498 | 2026-11-01 | 4 | 1 | 5 | 5 | **3.65** | 只借"预算在数据库里强制、余额不足 fail-closed"；它是 Python 网关服务，与本机 Room 账本不同层 |
| `567-labs/instructor` | MIT | 14 088 | 2026-10-26 | 4 | 1 | 5 | 4 | **3.50** | 只借"校验失败 → 带上下文重试 → 重试耗尽抛带 attempts 的异常"；Python |
| `temporalio/temporal` | MIT | 24 119 | 2026-11-01 | 3 | 1 | 5 | 4 | **3.15** | 只借"幂等键 + 重试策略"概念；引入服务端完全不现实 |
| `openai/openai-agents-python` | MIT | 30 438 | 2026-11-01 | 4 | 1 | 5 | 2 | **3.20** | "agents as tools / handoffs"可解释工具环，但不适用于单机任务分层 |
| `langchain4j/langchain4j` | Apache-2.0 | 13 337 | 2026-10-30 | 4 | 2 | 5 | 3 | **3.60** | 官方文档写"最低 JDK 17"（§9），未声明 Android 支持；体量大 |
| `aallam/openai-kotlin` | MIT | 1 850 | 2026-10-31 | 2 | 4 | 4 | 1 | **2.85** | 纯客户端；**不能**用——会绕过现有 transport 的 SSRF/私网拒绝与字节预算（`OpenAiModelTransport.kt:77-80`、`:176`、`:228`） |
| `simonw/llm` | Apache-2.0 | 12 687 | 2026-10-30 | 3 | 1 | 4 | 2 | **2.60** | 极简任务链参考，与我们的账本/CAS 无关 |
| `dottxt-ai/outlines` | Apache-2.0 | 15 991 | 2026-09-21 | 2 | 1 | 4 | 2 | **2.25** | 约束解码需要控制推理端；我们只能"解析后拒" |
| `stanfordnlp/dspy` | MIT | 38 878 | 2026-11-01 | 1 | 1 | 5 | 1 | **2.00** | 关注 prompt 自动优化，与分层无关 |

### 6.3 结论

1. **不引入任何代码或依赖**。最高分 3.65（litellm）与 3.60（langchain4j）都来自"概念贴合 + 维护好"，但可复用成本评 1/2：一个是 Python 服务端，一个最低 JDK 17 且未声明 Android 支持。`core/data` 现在连 `core:export` 都不依赖，加一个 JVM LLM 框架会同时破坏依赖方向与包体预算。
2. **采纳 4 个模式**（不引入代码）：
   - BAML：类型化函数 = 一个输入类型 + 一个输出类型 + 一个 prompt + 一个 client 绑定 → 对应我们的 `kind + Input + Output + promptPolicyVersion + supportedTasks`；
   - Instructor：校验失败是**可分类**的（重试次数、失败原因、耗尽异常带 attempts）→ 对应 `INVALID_RESPONSE`（不可重试）vs `INCOMPLETE_OUTPUT`（可重试）的分流；
   - LiteLLM：预算必须**在持久层强制**、余额读不到就拒绝（`fail_closed_budget_enforcement`）→ 对应 `dispatch_count < max` 的原子 UPDATE 与 `remainingRemoteDispatches = null` 交给账本判定；
   - Temporal：同一逻辑操作的重试必须复用**同一身份**（幂等键），换身份即换预算 → 对应 `operation_fingerprint` 与"用户显式重试复用同一 operationId"（`FigureGeneration.kt:330-339`）。
3. **Kotlin/Android 没有可信同类库**：`gh search repos "llm kotlin"` 返回 10 条，最高 0–1 star（命令与输出见 §9），全部不满足维护度门槛。自研任务是**有依据的**，不是惯性。

### 6.4 来源可靠性登记

| 声明 | 来源 | 等级 |
|---|---|---|
| 各仓库 license / stars / push 时间 | `gh api repos/...`（GitHub REST） | 单一来源（GitHub 官方 API），2026-11-02 实测 |
| litellm 预算在 DB 强制、`fail_closed_budget_enforcement` | 官方文档 `docs.litellm.ai/docs/proxy/users` | 官方文档单源 |
| litellm 路由/回退/`num_retries`/`rpd`/`tpd` | 官方文档 `docs.litellm.ai/docs/routing` | 官方文档单源 |
| instructor `max_retries` / `InstructorRetryException.n_attempts` | 官方文档 `python.useinstructor.com/concepts/retrying/` | 官方文档单源 |
| BAML 类型化函数/生成客户端 | 官方文档 `docs.boundaryml.com/guide/introduction/what-is-baml` | 官方文档单源 |
| **BAML 是否支持 Java/Kotlin** | README 说"TS, Py, Go, C#, Java, etc."；同仓 docs 介绍页只列 Python/TypeScript/Go/Ruby；仓库 `languages/` 目录只有 `csharp`、`rust` | **两处同仓材料互相矛盾 → 待验证**，不据此做任何决定 |
| langchain4j 最低 JDK 17 / 未提 Android | 官方文档 `docs.langchain4j.dev/get-started` | 官方文档单源；"未提 Android"是**材料缺失**，不等于不支持 |
| litellm license | `LICENSE` 文件正文（MIT + `enterprise/` 例外）与 API 的 `NOASSERTION` | 两材料一致解释（API 未识别复合许可），按 MIT + 子目录例外处理 |

---

## 7. WS4 六项：落点与验收锚

| 计划项 | 落点 | 验收锚（本批可写） | 热区/依赖 |
|---|---|---|---|
| 23 归属裁定 | 本文 §2 | 文档 + `ModelTaskKind.PAPER_EDIT` 落 core/model | 无 |
| 24 DTO + schema 19 | `core/model`：`PaperEditInput/Output`、`PAPER_EDIT_SCHEMA_VERSION = 19`、`CURRENT_SCHEMA_VERSION = 19`、构造期守卫 | ① schema 18 + PaperEditInput 抛；② 旧 v17/v18 golden 逐字节与双指纹不变；③ kind 名 golden | 改 `TutorVisualContextFingerprintTest:62-66` 的版本钉子（重钉，非放宽） |
| 25 gateway + 预算/ledger | `core/data/model`：adapter 两处 `when`、`supportedTasks`、capability 无需改（探测与 kind 无关，`ModelCapabilityTester.kt:66-124`） | ① 配置了 provider 时 `supports(PAPER_EDIT)` 为真；② 6 次后 `budgetExhausted`；③ 取消不耗预算 | 无 DDL、无迁移 |
| 26 提示词模板 + 版本号 | `core/data/model/PaperEditPrompt` + `ModelPromptPolicyVersions.PAPER_EDIT = "paper-edit-v1-whitelist-commands"` | ① 提示词含全部 7 个 type 且不含图片字段；② 版本串进清单校验（`ModelEgress.kt:671-674`） | `feature/tutor` 的 `TutorPaperEditPrompt` 保持不动，登记为重复源欠账 |
| 27 响应解析与失败分类 | adapter 的 `parse()` 调 `PaperEditCommandParser`；失败映射沿用网关出口 | ① 未知键 → `INVALID_RESPONSE`；② 超预算 → `OUTPUT_BUDGET_EXCEEDED`；③ 截断 → `INCOMPLETE_OUTPUT`；④ 成功结果的 `commandsJson` 能被 applier 原样接受 | 与 24 共用 golden |
| 28 export/tutor 注入接缝 | **只出契约**：`MistakePdfPaperEdit -> PaperEditInput` 的字段映射表 + `PaperEditOutput.commandsJson -> TutorPaperEditAssistant` 的返回契约；不在本批改 feature | 映射表逐字段对齐（题序/entryId/标题/块/选项/备注/已删题）；app 装配登记为热区欠账 | **热区**：`feature/tutor`、`app` 不动 |

**跨线依赖（必须先裁后写）**：`SmbkSelectionPlan.modelTaskKindCategory` 与 `73.json` 的机器块由 WS2 单写者负责。`PAPER_EDIT` 的**类别归属**（建议 `SESSIONS`：改卷是会话侧动作，卷面可从题重新导出，不属于学生的源事实）必须在 WS2 冻结 `73.json` 之前定下来，否则机器块与代码不一致，`SmbkSelectionPlanTest` 的字段级对拍会红（`SmbkSelectionPlanTest.kt:296-330`）。

---

## 8. 未验证 / 未取到

- **未运行任何编译或测试**（共享纪律：不跑 Gradle）。因此"穷尽 `when` 会编译失败""sealed 不能跨模块扩展"属**语言语义推断**，不是本次实测结论。可复核方式：加枚举值后跑 `:core:model:testDebugUnitTest`，看是否出现 `'when' expression must be exhaustive` 与 `sealed` 子类型错误。
- **未验证 `PAPER_EDIT` 在恢复链的端到端行为**（需要 schema 73 迁移矩阵 + 备份 34 例，归 WS2/WS8）。
- **未验证真实 Provider 对改卷请求的质量**（无凭据；与 6B-3 历史结论一致：真实模型质量 UNVERIFIED）。
- **BAML 的 Java/Kotlin 支持**：两处同仓材料矛盾，标记待验证（不影响本批结论）。
- **langchain4j 的 Android 兼容性**：官方 get-started 未提 Android，材料缺失，未做实测。
- **`PaperEditCommand` 的 `MAX_MOVE_OFFSET` 等预算**是否适合"多题整卷重排"：未做实测，沿用现值并在 24 的 DTO 校验里保持一致。
- **检索范围**：全部代码结论来自主工作树；`.worktrees/*` 下他线检出（`image-subsystem`、`kb-bench` 等）未纳入基线，其中的 `ModelTaskKind` 命中不作依据。

---

## 9. 来源与执行记录

### 9.1 仓内一手来源（路径:行）

- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:12-25`（kind 枚举）、`:60-67`（6 次上限）、`:159-183`（sealed input + 两个计算属性）、`:396-485`（请求与 12 条版本守卫）、`:487-549`（版本常量，18 在 546-547）、`:624-667`（快照构造与请求指纹校验）、`:788-830`（两个指纹）、`:854-996`（旧行指纹复原）、`:1009-1117`（各 strip 的类型守卫）
- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelEgress.kt:40-104`（提示词策略版本 + `currentFor`）、`:224-284`（清单 init 与 purpose 白名单）、`:304-352`（三类 purpose 的 kind 约束）、`:356-364`（清单 schema 7）、`:471-479`（版本化词汇表）、`:567-612`（authorize/同意路径）、`:660-750`（`requireAuthorizes` 与 `when(input)`，含"删过一轮不可达分支"的注释在 744-749）
- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/KnowledgeQuizTasks.kt:122-190`（完成校验穷尽 when）
- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/PaperEditCommand.kt:11-26`（放 core:model 的理由）、`:27-99`（七命令，无 `@Serializable`）、`:118-134`（预算）、`:159-207`（严格解析 + 每 type 键集）、`:328-341`（键提示）
- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolLoop.kt:12-33`（工具枚举：ADVISORY_READ/WRITE、GENERATE_FIGURE）
- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/FigureGeneration.kt:174-216`（预算声明面）、`:260-291`（`FigureDispatchParent`）、`:293-339`（请求/重试语义）
- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTextBudget.kt:1-18`（超预算 vs 形状错误的可重试判据）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTaskAdapters.kt:65-110`（prompt/parse 穷尽 when）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiCompatibleModelGateway.kt:401-480`（失败分类）、`:547-585`（`supportedTasks` 手写集合）、`:926-927`（SSE 仅三个 tutor input）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelResponseParsing.kt:44-46`（`requireOnlyKeys`）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelResponseParsers.kt:300-321`（图 proposal 白名单）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTransport.kt:77-80`（私网/环回/链路本地拒绝）、`:176`、`:228`（FixedDns）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/ModelCallFailureDiagnostics.kt:29-45`（诊断缝，默认关）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/FigureGenerationBudgets.kt:12-22`（共享预算声明）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt:43-57`（schema 72 / identityHash / closure v4）、`:514-527`（kind → 类别）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/RestoreSoftReferenceRecheck.kt:16-25`、`:68-80`（未知 kind fail-closed）
- `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlanTest.kt:257`（12 个已知 kind）、`:296-330`（机器块字段级对拍）
- `core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/TutorVisualContextFingerprintTest.kt:62-66`（schema=18 钉子）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ModelTaskTransactionDao.kt:84`、`:93-108`、`:172-238`、`:244-...`、`:318-323`、`:325-...`、`:344`（预留/上限/终答位）
- `core/database/build/generated/ksp/debug/kotlin/.../StudyDatabase_Impl.kt:495-496`（`model_task_operation` 是 TEXT，无 CHECK）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ModelGateway.kt:14-46`（网关/仓库端口）
- `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt:26-30`、`:349-357`（卷面形状）
- `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorPaperEditAssistant.kt:17-35`、`:44-125`（端口与现存提示词，**只读，热区**）；`TutorConversationViewModel.kt:612-620`（未注入时的可耻失败分支）
- `docs/current/architecture-contract.md:3-16`（依赖方向与三条禁令）
- `docs/current/model-task-contracts.md:3-20`（每个 kind 的机器可检契约）、`:51-53`（统一图策略与"不得新增视觉 kind"）
- `core/model/build.gradle.kts:9,29`；`core/data/build.gradle.kts:169-171`；`core/export/build.gradle.kts:23`
- `.zcode/plans/plan-2026-11-02-batch-40-5x.md:43-49`、`:86`

### 9.2 外部来源（URL）

- https://github.com/BoundaryML/baml （Apache-2.0，9 468 stars，push 2026-10-31）
- https://docs.boundaryml.com/guide/introduction/what-is-baml （function/class/client/生成客户端；语言列表：Python/TS/Go/Ruby）
- https://github.com/BerriAI/litellm ；https://github.com/BerriAI/litellm/blob/main/LICENSE （MIT + `enterprise/` 子目录例外）
- https://docs.litellm.ai/docs/routing （Router：load balancing、fallbacks、`num_retries`、`retry_after`、`rpd`/`tpd`）
- https://docs.litellm.ai/docs/proxy/users （Key/User/Team 预算，"enforced against spend read from the database"，`fail_closed_budget_enforcement`）
- https://github.com/567-labs/instructor ；https://python.useinstructor.com/concepts/retrying/ （`max_retries`、`InstructorRetryException.n_attempts`）
- https://github.com/temporalio/temporal ；https://github.com/openai/openai-agents-python
- https://github.com/langchain4j/langchain4j ；https://docs.langchain4j.dev/get-started （最低 JDK 17；未提 Android）
- https://github.com/aallam/openai-kotlin ；https://github.com/simonw/llm ；https://github.com/dottxt-ai/outlines ；https://github.com/stanfordnlp/dspy

### 9.3 执行过的检查命令

```bash
# 元数据（10 个候选仓库，逐个）
gh api "repos/<owner>/<repo>" --jq '[.full_name, .stargazers_count, (.license.spdx_id // "NONE"), .pushed_at, (.archived|tostring), .open_issues_count] | @tsv'

# README / LICENSE
gh api repos/BerriAI/litellm/contents/LICENSE --jq .content | base64 -d | head -20
gh api "repos/<owner>/<repo>/readme" --jq .content | base64 -d | head -40

# Kotlin/Android 同类库检索（结论：无可采纳）
gh search repos "llm kotlin" --limit 10 --json fullName,stargazersCount,license,pushedAt
gh search repos "llm kotlin android" --limit 8 --json fullName,stargazersCount,license,pushedAt
```

**未执行**：Gradle 编译/测试、设备轨、git 写操作（按共享纪律）。

---

## 10. WS4 落码记录（计划项 23–28）

本节是 §7 的**实现对照**：哪些已经落进代码、哪些仍只能出契约。**未跑 Gradle**，所以
"编译通过 / 测试通过"一律不是本节的声明。

### 10.1 项 23：归属裁定（已落码）

- `ModelTaskKind.PAPER_EDIT` 追加在枚举末尾（不改旧 ordinal）；名字是持久契约。
- 新 DTO 落 `core/model/.../PaperEditTasks.kt`：`PaperEditInput/Output`、
  `PaperEditQuestionSnapshot`、`PaperEditBlockSnapshot`、`PaperEditChoiceSnapshot`、
  `PaperEditRemovedQuestion`、`PaperEditBlockKind`。
- 提示词落 `core/data/.../model/PaperEditPrompt.kt`；版本串
  `ModelPromptPolicyVersions.PAPER_EDIT = "paper-edit-v1-whitelist-commands"`。

### 10.2 项 24：schema 19（已落码）

- `ModelTaskRequest.PAPER_EDIT_SCHEMA_VERSION = 19`，`CURRENT_SCHEMA_VERSION = 19`。
- 构造期守卫：`schemaVersion >= 19 || input !is PaperEditInput`。
- **无字段级迁移**：新 kind 是新 sealed 子类型，旧输入编码逐字节不变；没有任何新的
  `withoutEmpty*` strip。
- 版本钉子 `TutorVisualContextFingerprintTest` 从 18 重钉到 19（重钉不是放宽；
  `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION` 仍是 18）。

### 10.3 项 25：gateway / 预算 / ledger（已落码）

- `OpenAiCompatibleModelGateway.toCapabilities()` 在结构化输出通过时 `add(PAPER_EDIT)`。
  这是 R4 高危复核点：缺这一行，全局同意路径 `provider.supports(kind)` 恒 false，
  改卷永远 `EGRESS_AUTHORIZATION_REQUIRED`。
- 纯文本轮次的进度阶段：改卷归 `PREPARING` / "正在读取当前卷面"，不再错误显示"读取题图"。
- 预算/ledger **零 DDL、零新表**：`ModelTaskRepository.execute` 已有的
  `dispatch_count < MAX_DISPATCHES` 原子预留、`operation_fingerprint = kind.name + 输入 JSON`
  自动覆盖新 kind；`reserveChatFinalAnswer` 不适用（改卷不是对话轮）。
- 测试：kind/wire 名字 golden、逻辑操作指纹稳定性、schema 18 守卫、
  gateway 能力广告与同意路径端到端（JVM）。

### 10.4 项 26：提示词（已落码）

- 白名单从 `PaperEditCommand.TYPES` 生成；`PaperEditPrompt` 的对象初始化 `check`
  集合相等，新增/删除命令必须同时更新提示词。
- `includeNote=false` 时不广告 `{"type":"set_note"...}` 模板（仍保留一句"不要输出 set_note"）。
- 提示词只含题序 / entryId / 标题 / 块 id+类型+文字 / 选项 / 已删来源题；不含 URI、资产 id、
  base64、本地路径。
- `feature/tutor` 的 `TutorPaperEditPrompt` **未动**（热区）。它是第二份提示词，
  登记为热区欠账：热区释放后应把 feature 端口改为转调 `core/data` 的 `PaperEditPrompt`，
  删掉重复。`MAX_INSTRUCTION_CHARS` 的 2 000 也是同一重复源（core/model 的
  `PaperEditInput.MAX_INSTRUCTION_CHARS` 与 feature 的 `TutorPaperEditPrompt` 各一份）。

### 10.5 项 27：响应解析与失败分类（已落码）

- `OpenAiModelTaskAdapters.parse`：`PaperEditInput` 分支先把 `payload.toString()` 交给
  **同一个** `PaperEditCommandParser`；`Rejected` → `InvalidModelResponseException`
  → 网关 `INVALID_RESPONSE`（不可重试）。
- 超过 `PaperEditCommandParser.MAX_PAYLOAD_CHARS` → `ModelTextBudgetExceededException`
  → 网关 `OUTPUT_BUDGET_EXCEEDED` / `INCOMPLETE_OUTPUT`（可重试，KD-38 同出口）。
- `stop_reason/finish_reason = length/content_filter` 在适配器之前分流为
  `INCOMPLETE_OUTPUT`（既有协议路径，新 kind 复用）。
- `PaperEditOutput` 构造期再跑一次白名单解析器：落库读回/任务成功前都会被同一形状门拦住，
  消灭"gateway 判 SUCCEEDED、applier 判非法"的分叉。
- `ModelTaskCompletionValidator` 只核对输入/输出类型；形状判据不在第二处复制。

### 10.6 项 28：export / tutor 注入接缝（只出契约，热区未动）

映射表（`MistakePdfPaperEdit -> PaperEditInput`）：

| `PaperEditInput` 字段 | 来源 | 说明 |
|---|---|---|
| `paperId` | 宿主分配（导出作业/卷面草稿 id） | `MistakePdfPaperEdit` 自身没有纸面 id；不能拿 `questions[0].entryId` 冒充 |
| `revision` | 宿主侧卷面版本（CAS） | 丢弃过期模型结果；不由模型产生 |
| `instruction` | 学生输入框 | ≤ 2 000 字 |
| `questions[].entryId/title` | `paper.questions` 按当前顺序 | — |
| `questions[].note` | `question.note`，仅 `layout.includeNote` | 不含备注区时传 null |
| `Paragraph` 块 | `blockId=id, kind=PARAGRAPH, text=text` | — |
| `SectionHeading` 块 | `blockId=id, kind=SECTION_HEADING, text=text` | — |
| `Formula` 块 | `blockId=id, kind=FORMULA, text=latex` | `alternativeText/display` 不进模型：不可通过白名单命令编辑 |
| `ChoiceGroup` 块 | `blockId=id, kind=CHOICE_GROUP, text=prompt, choices=[{id,text}]` | `selected/enabled` 不进模型 |
| `Figure` 块 | `blockId=id, kind=FIGURE` | `title/alternativeText/schema` 省略；图只读 |
| `Image` 块 | `blockId=id, kind=IMAGE` | `localUri/expectedContentSha256/expectedByteSize/role/title` 全部丢弃——这正是快照存在的理由 |
| `Note` 块 | `blockId=id, kind=NOTE` | 备注文字不进块；改备注只能 `set_note` |
| `questions[].subtitle`、`cleanImage*`、`questionImageRole`、`solutionImages` | **不映射** | 图片/解析区引用只留在本地卷面 |
| `removedQuestions[]` | `paper.removedQuestions` | `entryId + title` |
| `includeNote` | `paper.layout.includeNote` | — |

返回契约（`PaperEditOutput.commandsJson -> TutorPaperEditAssistant`）：

1. 装配层把 `commandsJson` **原样**返回给 `TutorPaperEditAssistant.commandsFor`（签名
   `suspend fun commandsFor(paper, instruction): String` 不变）；
2. `TutorPaperEditApplier` 用 `PaperEditCommandParser` 再解析一次——与网关是**同一把**
   白名单（网关保证形状，applier 保证 id 在当前卷面内）；
3. `revision` 必须由装配层在应用前核对：卷面已变则丢弃结果并要求重新发起，而不是把
   旧 revision 的命令应用到新卷面；
4. `PAPER_EDIT` 请求必须 `agentConsentGranted = <应用层全局开关实值>` 且
   `egressManifest = null`（生产不构造改卷清单）。

**热区欠账（本批不改）**：

- `feature/tutor`：`TutorPaperEditAssistant` 的生产实现注入（把模型任务接到端口）、
  删除 `TutorPaperEditPrompt` 重复源；
- `app`：`SmartMistakeBookApplication` 的装配点；
- `docs/current/model-task-contracts.md`：补 `PAPER_EDIT` 的机器可检契约（docs/current 热区）；
- `SmbkSelectionPlan` 的 `73.json` 机器块：由 WS2 单写者重建。WS4 落码期间这两个文件被
  WS2 并发改写为 schema 73 / 93 表 / CLOSURE 5，WS4 的 `"PAPER_EDIT": "SESSIONS"` 一行
  叠加在其中（见结果里的整文件纳入登记）。落码后用脚本对拍：机器块 kind map 与代码 map
  相等、13 项、`PAPER_EDIT=SESSIONS`（2026-11-02 本会话实跑）。WS2 重建 73.json 时仍以
  代码 map 为准；若覆盖，必须保留这一行。
  **2026-11-02 复核轮裁定更正**：WS2 已按 rebase 到 71 落 **92 表** 并占 **73**；
  UI 线（`codex/ui-green-pages`）的 97 表 `paper_review*` 快照在合并时**改号到 74**，
  不得再与本批的 73 并存。

### 10.7 未验证（本条不是完成声明）

- 未运行 Gradle / 未编译 / 未跑测试：`sealed` 穷尽 `when`、`@SerialName` 枚举、schema 19
  的守卫与新增用例的编译期正确性，全部是**读码推断**。
- 未在设备上验证 `PAPER_EDIT` 的迁移/备份恢复（依赖 WS2 的 schema 73 与 WS8 的 34 例）；
  当前只保证 `SmbkSelectionPlan.modelTaskKindCategory` 不再把该 kind 判为映射外。
- 未验证真实 Provider 对改卷请求的输出质量（无凭据）。
