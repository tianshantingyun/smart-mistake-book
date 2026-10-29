# 能力快照驱动的工具描述与 prompt 注入研究（批次 35，驱动 T4/T5）

> 输入：计划 `.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md`（已全文读）、
> 准入清单 A-04/A-05/A-06/A-08/A-13 行原文（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:192-201`，
> 下称**清单**）、批次 35 基线（`docs/research/2026-10-30-batch-35-baseline.md`）、
> 批次 34 双后端装配研究（`docs/research/2026-10-29-dual-backend-wiring.md`）。
> 本文只做研究/设计，**未改任何源码、未运行 Gradle、未上设备、未做 git 写**；
> 所有"现状"结论来自本轮实读源码与实跑命令（命令与输出随条记录），设计推断一律标"建议"，
> 无法核实的标 **未验证**。

**一句话结论**：A-04/A-06 的最小切口与批次 34 的 A-03 完全同构——**能力参数化、生产默认逐字节不变**
（`figureMethods` 缺省 `IMAGE_MODEL` 已在 `OpenAiModelProtocol.kt:378-379` 建立先例）；A-05 的"双就绪位"
是**方法级广告位**（工具广告 = 任一方法就绪；JS 广告 = 引擎 ready ∧ 度量 ready），就绪位是每轮重算的
**装配态快照**而非跨轮状态机，执行失败经既有 outcome/熔断通道传播、不回写广告位，唯一降位路径是装配级
死亡；A-08 的入口侧约束裁定为 **program 摘要是权威、显式 basis 必须过复核**，落点在 policy（新增
`BASIS_FINGERPRINT_MISMATCH` 拒绝码）而不是 coordinator；A-13 的 grep 门脚本化是 tools/ci 的独立小脚本
（本轮实跑复核恰 3 处构造）。GitHub 参考共 4 项（MCP 规范 / openai-agents-python / LSP 动态注册 /
批次 34 已评的 Bazel 交叉引用），均不引代码，只借形状。

---

## 0. 本轮实读核对基线（全部为本会话实跑/实读）

| 事实 | 证据（本轮） |
|---|---|
| A-13 基线仍成立：`FigureGenerationRequest(` 在 `core/data/src/main` 恰 3 处 | `grep -rn "FigureGenerationRequest(" core/data/src/main --include="*.kt"` → `ConfiguredCleanImageGenerator.kt:100`、`AttachedImageGenerator.kt:155`、`TutorFigureGenerator.kt:377`（3 文件 3 处，与清单 `:381` 附录基线逐字一致） |
| 三入口都**显式**传 IMAGE 语义的 basis | `TutorFigureGenerator.kt:393-397`、`AttachedImageGenerator.kt:183-187`、`ConfiguredCleanImageGenerator.kt:128-132`（实读，均为 `basisFingerprint = figureBasisFingerprint(operationId, sourceSha, imagePrompt)`） |
| coordinator 对显式 basis **无条件采信** | `DefaultFigureGenerationCoordinator.kt:695` `basisFingerprint = basisFingerprint ?: when (proposal.method) {…}`（实读） |
| JS 的 basis 派生已落（批次 34） | `FigureGeneration.kt:646-664` `figureJsBasisFingerprint`（program 字节哈希进规范化串）；coordinator 分支 `:698-708`；用例 `FigureGenerationCoordinatorTest.kt:463`（清单 v3 补记 5 转录，本轮未跑） |
| 生产可用方法集合恒 `IMAGE_MODEL_ONLY` | `FigureGeneration.kt:160-167`（`IMAGE_MODEL_ONLY` 常量）；三入口均硬编码 `val capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY`（`TutorFigureGenerator.kt:372`、`AttachedImageGenerator.kt:144`、`ConfiguredCleanImageGenerator.kt:95`） |
| prompt 装配器对 JS fail-closed | `FigurePromptAssembler.kt:40-43`（`assemble`）、`:108-111`（`assembleForTutorTool`）两处 `require(JS !in capabilities.availableMethods)`（实读） |
| 工具描述是静态 kind 口径 | `TutorToolDescriptions.kt:56-68`（长版，含"本地未配置可用的图像模型时也不会生成"）、`:89-93`（短版）（实读） |
| 工具描述消费点 2 处 | Route B 提示词 `OpenAiModelTaskAdapters.kt:658`；原生 schema `OpenAiModelProtocol.kt:268`（实读） |
| 能力快照就绪位是单一 Boolean | `Capabilities.kt:70-78` `withDependencies(imageBackendReady, knowledgeMaterialReady)`；生产读 `RoomModelTaskRepository.kt:959` `imageBackendReady = figureGenerator != null && declaredProvider.supportsImageInput`（实读） |
| A-03 的能力参数化先例已落 | `OpenAiModelProtocol.kt:378-379` `figureMethods: Set<…> = setOf(…IMAGE_MODEL)`；`jsAdvertised` 仅当集合含 JS（`:388-389`，本轮实读） |
| 工具环契约层已接受 method=JS + program | `TutorToolLoop.kt:200-210`（figureMethod 字段 KDoc）、`:355-390`（init 的 JS 分支：program 必带、与图像载荷互斥，本轮实读）；`toFigureGenerationProposal` 按 method 分支（清单 v3 补记 4/6 登记） |
| 执行器第二道门在树 | `RoomTutorToolRunner.kt:949-962`：`call.figureMethod == JS` → `ok=false` + `FIGURE_METHOD_UNAVAILABLE`（实读；该文件已提交、本轮 `git status` 干净） |
| JS 引擎/帧输入注入点在树、生产未接 | `DefaultFigureGenerationCoordinator.kt:153/:158`（构造参数默认 null）、`:211`（JS 分支）、`:248-277`（`generateLocalJs`：无引擎 → UNAVAILABLE）、`:790-806`（factory 透传）；生产装配 `SmartMistakeBookApplication.kt:364` 未传引擎（实读） |
| 契约：交互面（reduce/controls/form）未启用时**不得**在能力上下文广告 | `docs/current/visual-generation-contract.md:58`（"未启用的控件/事件/模式在模型能力上下文中不广告，解析与执行也同时拒绝"）、`:94`（SceneV1 的 `controls[]`/`form?`）、`:139`（"4E.1 不广告 form"）（实读） |
| 静态程序形状与 SDK 签名 | 契约 `:47`（`const visual = { prepare(input), render(input, metrics), reduce(state, event) }`；静态程序可省略 reduce，`:58`）；`visual-sdk.js:77-281` 的 `visualSdk` 公开原语恰 6 个：`circle/arc/arrow/axis/polyline/random`（本轮 awk 实提），`visual-sdk.js:281` `globalThis.visualSdk = ObjectFreeze(visualSdk)` |
| M-04：度量失败当前折 `INVALID_PROGRAM` | 清单 `:158` 行原文"诊断语义：度量未接线不能折成'程序错'…**未落**：当前折 `INVALID_PROGRAM`"；佐证：`VisualJsFailureClassification.kt:126-141`（折回 KDoc/映射/前缀，实读）——UNATTRIBUTED 折回落 `INVALID_PROGRAM`、仅诊断前缀 `[UNATTRIBUTED]` 区分 |
| 设计目标文件全部不在热区 | `git status --porcelain` 逐文件实跑：`Capabilities.kt`、`TutorToolDescriptions.kt`、`FigureGenerationPolicy.kt`、`FigureGeneration.kt`、`TutorToolLoop.kt`（core/model）与 `FigurePromptAssembler.kt`、`OpenAiModelTaskAdapters.kt`、`OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt`、`DefaultFigureGenerationCoordinator.kt`（core/data）**全部 CLEAN**；`VisualFrameInputs.kt` 为 untracked 新文件（??） |

---

## 1. 五行清单原文转录（判据即验收口径）

- **A-04**（清单 `:192`）："Prompt assembler 按真实能力注入 schema/SDK 签名/最小样例；去掉 JS fail-closed"；
  现状"未落：装配期直接 `require(JS !in availableMethods)`"；判据："JS 静态能力 prompt 测试：含
  schema/SDK/样例、不含 reduce/form/controls（JVM）"。
- **A-05**（清单 `:193`）："`TutorCapabilitySnapshot` 方法级就绪：JS runtime ready 时 `GENERATE_FIGURE`
  可广告，不依赖 image backend"；现状"未落：`imageBackendReady` 只看图像后端"；判据："JVM 双就绪矩阵 +
  设备广告-执行一致（JVM/设备）"。
- **A-06**（清单 `:194`）："`TutorToolDescriptions` 动态描述 method/program，不再写'未配置图像模型就不会
  生成'"；现状"未落：长短描述仍是 kind 口径"；判据："JVM 描述快照测试（JVM）"。
- **A-08**（清单 `:196`）：basis 指纹纳入 program 摘要（或入口显式 basis）；"已落（批次 34，v3 补记 5）"，
  JVM 未跑；**计划任务 12 的增量是入口侧约束**（宿主显式传 basis 时以 program 摘要复核），该半边清单行内
  未见落点（基线核对 §四 已登记）。
- **A-13**（清单 `:201`）："全入口同管线……三个 `FigureGenerationRequest(` 构造点全部经同一
  `FigureGenerationCoordinator` 端口"；现状"部分落：三入口均走 coordinator 建链，但无逐入口断言行，
  架构 grep 门未立"；判据 ①："主机 grep 门：`grep -rn "FigureGenerationRequest(" core/data/src/main`
  → 恰 3 处，出现第 4 处即失败"。

---

## 2. 任务一：能力快照派生工具描述与 prompt 注入的最小实现设计（A-04 + A-06）

### 2.1 设计原则：与 A-03 同构的"能力参数化、生产默认逐字节不变"

批次 34 已在请求 schema 层建立了这一形态：`figureMethods` 带默认值 `setOf(IMAGE_MODEL)`，生产调用点
不传 → 输出与之前逐字节相同（`OpenAiModelProtocol.kt:378-389`）。A-04/A-06 沿同一形态落到 prompt 装配与
工具描述两层。**三个生产默认不变的证据形态**（先红后绿的钉子）：

1. **A-04 默认快照测试**：同一 `(purpose, capabilities=IMAGE_MODEL_ONLY, budget)` 输入下，
   `assemble`/`assembleForTutorTool` 的 `body` 与现实现产出**逐字节相同**（先写对照测试钉住现文本，
   再动实现——这是"广告开关零改动"硬约束的 JVM 证据，与 `FigurePromptParityTest` 现有用例并排）。
2. **A-06 默认快照测试**：默认参数下 `GENERATE_FIGURE.purposeDescription()` /
   `nativePurposeDescription()` 返回值与现文本**逐字节相同**（把现文本抄进期望值；不是断言"包含某词"，
   而是全等）。
3. **负向不得放宽**：`FigurePromptParityTest.jsIsNotAdvertisedWhenItIsNotAvailable`（`FigurePromptParityTest.kt:45-56`
   ：body 不含 "JS"/"QuickJS"/"sourceJs"/"program"，断言在 `:48-55`）与 `advertisingJsIsRefusedInThisWave`（`:57-78`）
   的命运必须在实现前裁定（见 2.3 红线）。

### 2.2 A-04 的最小切口（`FigurePromptAssembler`，core/data，文件 CLEAN）

现状两处 `require(JS !in availableMethods)`（`:40-43`、`:108-111`）+ 四段 IMAGE-only 硬编码：
方法标签 `promptLabel`（`:191-194`，已支持 JS）、能力段 `methodCapabilities`（`:209-222`，只渲染 IMAGE），
输出段 `:73`（"选择 IMAGE_MODEL 就返回…"）、预算段 `:70` 与 `:151`（"本波次只有图像模型方法"/"图像生成/
编辑各占一次远程派发"）。

**建议的形态**（每个新增分支都指认消灭的失败）：

| 切口 | 内容 | 消灭的具体失败 |
|---|---|---|
| 删两处 `require(JS !in …)`，改为 `require(JS !in … || jsStaticContractRenderable)` 语义的**能力分支** | `availableMethods` 含 JS 时渲染"JS 静态段"；不含 JS 时正文与现状逐字节相同 | 不删则 A-04 永远无法达成（"去掉 JS fail-closed"是清单行明文）；不改分支则 JS 广告时 prompt 仍写"本波次只有图像模型方法"→ 广告-正文漂移，模型被两处矛盾文本夹住 |
| 能力段 `methodCapabilities` 加 JS 分支 | "JS：本机结构化程序绘图（静态后端：`prepare(input)` + `render(input, metrics)`）；参数交互=不支持"（位取自 `capabilities.supportsParametricInteraction`，与 IMAGE 行同源） | 没有 JS 能力行时"方法与能力"段列了 JS 却没有它的操作/位描述——模型只能猜 |
| **JS 静态段（新增）**：程序形状 + SDK 签名 + 最小样例 | 程序形状：`const visual = { prepare(input), render(input, metrics) }`（静态程序可省略 reduce——契约 `:47`/`:58`）；SDK 签名：`visualSdk.circle(cx,cy,r)`、`arc(cx,cy,r,a0,a1)`、`arrow(x1,y1,x2,y2,o)`、`axis(o)`、`polyline(points)`、`random()`（`visual-sdk.js:77-281` 实提的恰 6 个公开原语）；最小样例：一段 ≤15 行的画轴+圆示例（从 SDK 原语直取，不含任何交互调用） | 判据明文要求"含 schema/SDK/样例"；没有这段，模型产出的 program 拿不到可执行的形状契约，只能凭 SHARED_RULES 里的一句偏好文字猜 SceneV1/SDK——生成的源码必然大量 `INVALID_PROGRAM` |
| **交互面禁令（判据的"不含 reduce/form/controls"）** | JS 静态段与样例**不得出现** `reduce`、`form`、`controls` 三个字面量；能力段对 JS 只声明静态后端 | 契约 `:58`"未启用的控件/事件/模式在模型能力上下文中不广告，解析与执行也同时拒绝"——广告了 reduce/controls/form 就等于广告 4E.2 交互面，模型会产出宿主拒绝执行的交互程序（广告-执行漂移的 JS 版） |
| 输出段/预算段按方法分支 | JS 可用时输出段补"选择 JS 就返回 method=JS 与独立 program 字段（不得把程序塞进 description）"；预算段补"结构化程序在本机执行，不占远程派发"（契约 `:163`） | 预算段硬写"图像生成/编辑各占一次远程派发"会让模型把 JS 选择也当成远程付费决策，扭曲方法偏好；与 policy 的"预算门只对 IMAGE_MODEL 生效"（`FigureGenerationPolicy.kt:144-156`）矛盾 |

**SDK 签名的注入来源裁定（建议）**：签名清单从 `visual-sdk.js` 的 `visualSdk` 成员**派生**（同源）而不是
在 Kotlin 里再抄一份——但 V1 的最小实现允许把 6 个签名写成 Kotlin 常量并加一条"与 visual-sdk.js 逐名一致"
的单测（对拍 `visual-sdk.js` 文本里 6 个 `xxx: function` 成员名）；带资源读取的运行时派生放到批次 34 研究已
指出的 L3 装配批。理由：prompt 是版本化正文（`FigureGenerationPolicyVersion`），随资产静默漂移反而破坏
"改动必回写契约"的纪律；测试对拍已足够消灭"两处清单漂移"这个具体失败。

**测试矩阵（JVM，先红后绿，落 `core/data/src/test`）**：

| 用例 | 断言 |
|---|---|
| `imageModelOnlyBodyIsByteIdenticalToThePreWaveBody` | IMAGE_MODEL_ONLY 输入 → body 与现实现快照全等（先落快照再改代码） |
| `aJavascriptCapabilityInjectsProgramShapeSdkSignatureAndSample` | JS+IMAGE 输入 → body 含 `prepare(`、`render(`、`visualSdk.circle`、`visualSdk.axis` 与样例标记 |
| `aJavascriptCapabilityNeverMentionsTheInteractiveSurface` | body 不含 `reduce`/`form`/`controls`（判据原文）；`supportsParametricInteraction=true` 时 JS 行如实写"不支持"或按位声明，但**样例/签名段**仍不得出现交互调用 |
| `aJavascriptOnlyCapabilityStillAssembles` | 只有 JS（无 IMAGE）→ 组装成功且预算段不出现"图像生成/编辑各占一次远程派发"（`FigureGenerationCapabilities` 的 init 已禁止 IMAGE 无操作，JS-only 是合法集合） |
| 既有 `advertisingJsIsRefusedInThisWave` 的改向 | 翻转为正向用例（JS 可用时组装成功）+ **保留一条负向**：`availableMethods` 含 JS 且 `supportsParametricInteraction=false` 时，能力段对 JS 的"参数交互"行如实写不支持（防"删 require 顺手删掉全部 JS 校验"） |

### 2.3 两条红线（§10/§12 自检）

1. **"不含 JS" parity 断言（`FigurePromptParityTest.kt:45-56`）不放宽、不改写**：它守的是"未接通时不
   广告"，JS 接通后该用例输入仍是 IMAGE_MODEL_ONLY，断言原样成立。唯一要改的是
   `advertisingJsIsRefusedInThisWave`（它断言的是"含 JS 即抛"，A-04 落地后这是被消灭的失败本身）——
   按纪律这不是"删断言变绿"，而是判据行明文要求的语义翻转；翻转后必须有上面的正向+负向替代。
2. **A-04~A-06 只落代码不接通**：本批所有新增分支的触发条件是"调用方显式传含 JS 的能力集合"；生产
   三个入口硬编码 `IMAGE_MODEL_ONLY`（`:372`/`:144`/`:95` 实读）、`availableMethods` 不加 JS（清单 §0
   勾选规则 5）。接通是"全门绿后最后一步"。

### 2.4 A-06 的最小切口（`TutorToolDescriptions`，core/model，文件 CLEAN）

**建议形态**：两个描述函数加**带默认值的能力参数**，生成 side 逐字透传：

```kotlin
// TutorToolDescriptions.kt（core/model）
fun TutorToolName.purposeDescription(
    figureMethods: Set<FigureGenerationMethod> = setOf(FigureGenerationMethod.IMAGE_MODEL),
): String
fun TutorToolName.nativePurposeDescription(
    figureMethods: Set<FigureGenerationMethod> = setOf(FigureGenerationMethod.IMAGE_MODEL),
): String
```

- `GENERATE_FIGURE` 分支内按 `figureMethods` 渲染：默认（生产）→ **现文本逐字节返回**；含 JS →
  长版把"没有当前题（如大厅轮次）时不会生成；本地未配置可用的图像模型时也不会生成（返回不可用，
  不要反复申请）"一句替换为按方法分别表述："method=JS 时在 program 字段给出完整结构化程序
  （不要把程序写进 description）；本机未接通的方法不得输出其载荷；返回不可用时不要反复申请"；
  短版同口径收紧。非 GENERATE_FIGURE 分支**不读该参数**（工具其它七枚与方法面无关，参数对它们是
  死参数——不产生行为分叉）。
- 消费点 2 处（`OpenAiModelTaskAdapters.kt:658`、`OpenAiModelProtocol.kt:268`）**本批不传参** →
  生产广告文本零改动；A-03 已参数化的 `strictFunctionSchema` 后续接通时由同一集合喂两处（描述与
  schema 枚举同源，消灭"schema 广告了 JS、描述还说只有图像模型"的漂移）。
- 判据落地：`TutorToolDescriptionsSnapshotTest`（JVM）——① 默认参数 × 8 工具 = 现文本全等快照；
  ② 含 JS × GENERATE_FIGURE = 动态句（含 `method=JS`、`program`，不含"未配置图像模型就不会生成"
  的旧句）；③ 其余 7 工具在含 JS 集合下与默认逐字节相同（证明参数对它们无行为）。

**备选（不采纳）**：把描述改成接收完整 `TutorCapabilitySnapshot`。拒绝理由：描述层只需要"方法集合"
一个事实；传整个快照会让 `core:model` 的纯函数依赖快照形状，测试面膨胀而无对应失败（§12.2：指认不出
它消灭哪个具体失败）。

---

## 3. 任务二：双就绪位（引擎 ready + 度量 ready）的状态机与失败传播（A-05）

### 3.1 现状与"双就绪位"的精确语义

- 现状工具级就绪：`withDependencies(imageBackendReady, knowledgeMaterialReady)`（`Capabilities.kt:70-78`）
  —— `imageBackendReady=false` 时整个 `GENERATE_FIGURE` 从广告面消失。生产读
  `figureGenerator != null && declaredProvider.supportsImageInput`（`RoomModelTaskRepository.kt:959`）。
- A-05 要求**方法级**：JS runtime ready 时 `GENERATE_FIGURE` 可广告、不依赖 image backend。
- 本任务把"JS runtime ready"展开为**双就绪位**：
  - **引擎 ready**：`VisualExecutionEngine` 生产实现已装配且未 close（app 侧 L3 客户端/引擎装配，契约
    §5.1 仍缺 `AndroidVisualExecutionEngine` 生产构造点——批次 34 研究 §6.3 已登记 UNVERIFIED）；
  - **度量 ready**：事务第 2 步的 `VisualContentMeasurer` 有真实实现可注入（契约 `VisualExecutionEngine.kt:162-164`
    seam；"真实度量是批次二 core:visual 的工作"，当前 core:data 的度量落点见清单 S-06 行"真实度量与
    屏/PDF painter 已在 core:data visual 过渡落点落码"）。
- **为什么两个都必须 ready 才广告**（每个位各消灭一个失败）：只有引擎没有度量 → 每个事务在"prepare
  返回度量批次"一步失败——广告它就是快照 KDoc 明文禁止的"广告一个必然不可用的工具"（`Capabilities.kt:36-37`）；
  只有度量没有引擎 → coordinator `generateLocalJs` 第一行就 UNAVAILABLE（`DefaultFigureGenerationCoordinator.kt:252-257`），
  同罪。**这同时是 M-04 的预防层**：度量没接线时连执行都不该发生，而不是执行后把失败折成 `INVALID_PROGRAM`
  回喂模型让它反复修 program（清单 `:158` 行的失败原文）。

### 3.2 状态机：**每轮重算的装配态快照，不是跨轮可变状态**

```
输入位（装配态，每轮派遣前重算，模型不可影响）：
  imageBackendReady   ∈ {0,1}   （既有位）
  jsEngineReady       ∈ {0,1}   （新增：引擎已装配且未 close）
  jsMeasurementReady  ∈ {0,1}   （新增：真实 measurer 已装配）
  knowledgeMaterialReady ∈ {0,1}（既有位）

派生输出（纯函数，无可变状态）：
  figureMethodsAdvertised = { IMAGE_MODEL if imageBackendReady } ∪ { JS if jsEngineReady ∧ jsMeasurementReady }
  GENERATE_FIGURE advertised ⇔ figureMethodsAdvertised ≠ ∅          （任一方法就绪即广告工具）
  KNOWLEDGE_READ advertised ⇔ knowledgeMaterialReady                （既有语义不变）
```

**状态空间是 8 态的纯函数表**（JVM 矩阵用例逐行钉），不是带转移规则的生命周期机——这是刻意裁定：
`TutorCapabilitySnapshot` 的 KDoc（`Capabilities.kt:44-46`）已规定消费者"都从 [forRound] 读，禁止各自抄
一份名单"；把"就绪"做成有状态的机（ready/degraded/cooldown…）会出现第二份健康度事实，与 coordinator
的 outcome/熔断通道打架。**降位只有一条路径**：装配级死亡（引擎 close、进程死亡未重建、measurer 被摘除）
→ 下一轮 `toolDeclarationsFor` 重算时 JS 从广告面消失。执行期失败**不**降位（见 3.3）。

### 3.3 失败传播表（执行期失败去哪里、广告位动不动）

| 执行期事件 | 端口/现状载体 | 传播结果 | 广告位 |
|---|---|---|---|
| 引擎已 close | `VisualExecutionRejection.ENGINE_CLOSED`（`VisualExecutionEngine.kt:311`） | coordinator 现状映射 UNAVAILABLE（`DefaultFigureGenerationCoordinator.kt:244` 注释明写"ENGINE_CLOSED 是'本机没有引擎'，如实回 UNAVAILABLE"） | **是装配级死亡的信号**：装配方收到 close 事实后把 `jsEngineReady=false`，下一轮重算即消失 JS（不是 coordinator 改快照——快照是装配态，coordinator 只有 outcome） |
| 程序错误 | `INVALID_PROGRAM`/`SANDBOX_VIOLATION`/… | FAILED + 有界诊断回喂（既有） | **不动**。程序错是"这份 program 错"，不是"方法坏了"；降位会让一次坏程序永久杀死方法广告（不可审计的隐性降级） |
| 熔断（硬终止后同 hash） | `PROGRAM_FUSED`（契约 §4:70；P-12） | per-programHash 拒自动重发、显式重试解熔断（既有） | **不动**：熔断是 program 级，不是方法级（源码变更即新 hash 新 program） |
| **度量未接线/度量失败** | 当前折 `INVALID_PROGRAM`（清单 M-04 行；`VisualJsFailureClassification.kt:126-141` 折回映射佐证） | **M-04 裁定（本任务承接）**：新增可区分分类（如 `MEASUREMENT_UNAVAILABLE` 的独立诊断前缀/类别），断言"measurement unavailable"不落 `INVALID_PROGRAM` 文案（判据原文） | 广告前的预防是主门（3.1 双位）；执行后的可区分分类是兜底证据 |
| 无宿主帧 | `visualFrameInputs.forGeneration → null` | UNAVAILABLE（`:270-277` 现状） | 帧输入缺位与度量同属"装配半边缺"，装配方应把它折进 `jsEngineReady` 的装配判据（引擎装配点同时给帧提供方）；coordinator 侧现状已如实 UNAVAILABLE |
| 忙/硬期限 | `BUSY`/`HARD_DEADLINE_EXCEEDED` | 有界诊断，宿主改调度（`VisualExecutionEngine.kt:283`） | **不动**（编排侧瞬态） |

**失败传播的两个纪律**：① coordinator 不持有/不修改快照（outcome 单向流），广告位的降级发生在**下一轮
`toolDeclarationsFor` 重算**，天然幂等、无需通知通道；② 任何降位必须能回答"装配态哪一位从 1 变 0"——
不允许出现"连续失败 N 次后冷却"这类无装配事实对应的规则（那是新增第二份健康度状态，指认不出消灭的失败，
§12.2 砍掉）。

### 3.4 最小切口与热区核对

| 位置 | 改动 | 消灭的失败 |
|---|---|---|
| `Capabilities.kt`（CLEAN） | `withDependencies` 增加双位：`jsEngineReady: Boolean = false, jsMeasurementReady: Boolean = false`（默认 false = 生产现状字节不变）；派生：`GENERATE_FIGURE` 在 `imageBackendReady \|\| (jsEngineReady && jsMeasurementReady)` 时保留。**保留旧参数签名**（或委托旧签名→新签名），既有 `TutorCapabilitySnapshotTest.imageBackendAndKnowledgeMaterialReadinessGateTheirTools`（`:76-101`）不放宽 | "JS runtime ready 但图像后端没配 → 整工具消失"与"图像后端就绪但 JS 半边缺 → 广告了不可执行方法"两个方向的方法级漂移；默认参数保证生产（只传旧两位）行为逐位不变 |
| `RoomModelTaskRepository.kt`（实跑 `git status` CLEAN） | `toolDeclarationsFor` 的 `withDependencies` 调用（`:958-961`）透传双位；位的**生产来源**留默认 false（=现状），真实装配值等 L3 引擎装配批接线 | 不接装配值则广告面零变化（硬约束）；接口先立，接线是纯装配动作 |
| 测试（core/model JVM） | 8 态矩阵：`{image, engine, metric}` × {0,1}³ → 广告工具/方法集合逐行断言；含"engine=1,metric=0 → JS 不广告但 IMAGE 就绪时工具仍在"（A-05 行的判据核心：不依赖 image backend） | 单一位缺半边的假广告/假消失 |

**备选（不采纳）**：把双位做成 `data class FigureMethodReadiness` 独立类型。拒绝理由：两个 Boolean 的
类型化包装在 `withDependencies` 这个唯一消费点没有第二读者，包装类型不消灭任何新失败（§12.2）；
若后续出现第三位再收拢。

---

## 4. 任务三：basis 指纹的入口侧约束（A-08 增量）

### 4.1 风险的精确定义（本轮实读）

- 三入口显式传 **IMAGE 语义** basis：`figureBasisFingerprint(operationId, sourceSha, imagePrompt)`
  （`TutorFigureGenerator.kt:393-397`、`AttachedImageGenerator.kt:183-187`、`ConfiguredCleanImageGenerator.kt:128-132`）。
- coordinator：`basisFingerprint ?: when (proposal.method) {…}`（`:695-709`）——**显式值无条件优先**，
  JS 分支只在显式值为 null 时生效。
- 因此：若一个 JS proposal 在带显式 IMAGE 语义 basis 的 request 上到达 coordinator，两份**不同**的
  program 会共享同一 `input_fingerprint`——这正是 A-08 判据"两个不同 program 不得同指纹"要消灭的失败，
  从入口侧复活。
- **今日可达性**：不可达。三入口只构造 `imageModelProposal`（IMAGE 方法）；工具环 JS 调用被
  `RoomTutorToolRunner.kt:949-962` 第二道门 fail-closed。但 `FigureGenerationCoordinator` 是公开端口，
  A-02 接线（工具环 JS → coordinator）落地那一刻，"request 显式 basis（IMAGE 语义）+ proposal.method=JS"
  就是默认形态——**约束必须在接线前立**，否则 A-02 一接通就写脏审计。

### 4.2 裁决：program 摘要是权威；显式 basis 必须过**方法一致**复核；冲突 fail-closed

规则（建议，落 policy）：

```
validateGenerate 内，method 与载荷校验之后追加：
  request.basisFingerprint != null 时：
    expected = when (proposal.method) {
        JS          -> figureJsBasisFingerprint(request.operationId, request.source?.contentSha256, requireNotNull(proposal.program))
        IMAGE_MODEL -> figureBasisFingerprint(request.operationId, request.source?.contentSha256, proposal.imagePrompt)
    }
    request.basisFingerprint == expected，否则 REJECTED(BASIS_FINGERPRINT_MISMATCH)
```

三条理由（每条都对着一个被否决的替代方案）：

1. **否决"显式值静默覆盖推导"**（现状语义）：audit 的 `input_fingerprint` 存在的意义就是回答"这次尝试的
   输入变过没有"（`FigureGeneration.kt:604-614` KDoc 原文）；冲突时采信显式值等于把宿主 bug 写进审计证据，
   事后无法区分"输入真的变了"与"宿主算错了指纹"。
2. **否决"冲突时 coordinator 静默重算"**：重算会掩盖宿主 bug 且制造两种读法（表里的值 ≠ 宿主声明的值）；
   唯一比"写脏"更好的是"第一次就炸"。
3. **否决"只查 JS、不查 IMAGE"**：IMAGE 复核恒过（三入口传的恰好就是该推导，本轮逐一实读核对），
   却把"入口的 basis 语义 = coordinator 推导语义"变成被钉住的恒等式而不是巧合；出现第 4 个入口
   （A-13 门拦截后人工放行的那种）算错 basis 时，IMAGE 侧同样在第一次就拒。

**落点裁定：`FigureGenerationPolicy`（core/model，CLEAN）而不是 coordinator**：

- policy 的输入恰是 `(request, proposal)`（`FigureGenerationPolicy.kt:37-40`），与 `SOURCE_EVIDENCE_MISMATCH`
  （"声明的 sha 与实际字节不一致"，`:282-287`）同构——"声明的输入身份与实际载荷不一致"是同一类校验；
- 三入口都过同一 policy（A-13 的"全入口同管线"），放 coordinator 反而要另写入口无关的校验路径；
- coordinator 零改动 → 批次 34 的 6 个 coordinator 用例零回滚风险。
- 新拒绝码 `BASIS_FINGERPRINT_MISMATCH`（`FigureGenerationRejectionCode` 枚举追加，有界可审计）。

**先红后绿**：① `anImageModelProposalWithAMatchingExplicitBasisIsAccepted`（三入口现值复核恒过的正向钉子）；
② `aJsProposalUnderAnImageSemanticsExplicitBasisIsRejected`（两个不同 program + 同一显式 basis → 第二个 REJECTED，
指纹不等——A-08 判据的入口侧形态）；③ `aJsProposalWithTheProgramDigestBasisIsAccepted`（工具环未来形态：
显式值 = program 摘要 → 过）；④ 既有 `FigureGenerationCoordinatorTest.distinctJavascriptProgramsGetDistinctBasisFingerprints`
（`FigureGenerationCoordinatorTest.kt:463`，清单 v3 补记 5 登记）不放宽，本轮未跑（UNVERIFIED）。

### 4.3 A-13 grep 门脚本化（计划任务 12 后半）

- 本轮实跑复核：`grep -rn "FigureGenerationRequest(" core/data/src/main --include="*.kt"` → 恰 3 文件
  3 处，与清单 `:381` 附录基线逐字一致。
- **脚本设计（建议）**：`tools/ci/check_figure_entry_gates.py`（对齐 `tools/tests/test_split_gate.py` 的
  现有风格，可被同一 pytest 收口）：
  - 门 1：对 `core/data/src/main` 全量 grep `FigureGenerationRequest(` → 期望命中集合与**硬编码白名单**
    精确相等（`ConfiguredCleanImageGenerator.kt`、`AttachedImageGenerator.kt`、`TutorFigureGenerator.kt`），
    多/少任一处 exit≠0 并打印差异（不写"≤3"这种宽松口径——第 4 处出现即失败是判据原文）；
  - 门 2（同脚本）：三文件各含 `basisFingerprint = figure` 显式传值（入口侧约束的静态前哨）；
  - 输出 `PASS/FAIL` + 命中清单（对齐批次 34 设备证据的静态门输出习惯）。
- **幽灵门教训**（基线核对 §三.6：`app/r04-test-keep.pro:13-14` 声称的门在 tools/ 不存在）：本脚本必须
  真实可运行并被验收命令引用；`.pro` 注释若声称同类门，同批按计划任务 15 二选一（实现或改注释）。

---

## 5. 任务四：GitHub 参考（能力协商 / 工具描述派生）评分

评分维度沿用批次 34 研究 §5：许可明确性 / 维护活跃度 / 可迁移性 / 是否值得引入代码。
全部元数据为本轮 `gh api` 实取（2026-10-30）；许可与关键代码事实取文件/规范原文。

| 项目 | 许可（核实方式） | 活跃度 | 形态（原文事实） | 可迁移性 | 结论 |
|---|---|---|---|---|---|
| **modelcontextprotocol/modelcontextprotocol**（规范仓，9,562★，TypeScript） | 许可证**过渡中**：MIT → Apache-2.0（`gh api …/contents/LICENSE` 原文："All new code and specification contributions to the project are licensed under Apache-2.0"；GitHub 元数据 `NOASSERTION` 与之一致，以 LICENSE 文件为准） | pushed 2026-10-29 | 能力协商三段式：① 初始化时声明能力（"Servers that support tools **MUST** declare the `tools` capability"，`{"tools":{"listChanged":true}}`）；② 客户端 `tools/list` 发现（含 name/description/inputSchema）；③ 列表变化时服务端发 `notifications/tools/list_changed`、客户端**重拉** `tools/list`（[官方 Tools 页](https://modelcontextprotocol.io/specification/2025-06-18/server/tools) 原文："When the list of available tools changes, servers that declared the listChanged capability **SHOULD** send a notification"） | **高**：与 A-05 的"就绪位是装配态声明、广告面每轮重算"同构——MCP 用"声明能力→发现→变更通知→重发现"替代"客户端持有一份会过期的工具清单"，正是 3.2"快照不是状态机"裁定的独立旁证；`listChanged` 不要求增量协议，只要求"下次 list 生效"，与"降位发生在下一轮 toolDeclarationsFor"逐点对应 | **不引代码，只借形状**。评分 4.5/5 |
| **openai/openai-agents-python**（30,375★，Python） | MIT（`gh api repos/openai/openai-agents-python` → `license.spdx_id=MIT`） | pushed 2026-10-27 | 每轮动态工具门：`FunctionTool.is_enabled: bool \| Callable[[RunContextWrapper, AgentBase], MaybeAwaitable[bool]]`，原注："You can use this to dynamically enable/disable a tool based on your context/state"（`src/agents/tool.py` 实提）；schema/描述由函数签名与 docstring **单一来源派生** | **高**：`is_enabled` 的"bool 或按上下文求值"双形态，就是 `withDependencies` 双位的 API 形态；"描述与 schema 同源派生"就是 A-06"描述与 A-03 schema 枚举喂同一集合"的先例 | **不引代码，只借形状**。评分 4/5 |
| **microsoft/vscode-languageserver-node**（LSP 实现，1,796★，TypeScript） | MIT（`protocol/src/common/protocol.ts` 文件头原文 "Licensed under the MIT License"，本轮实读） | pushed 2026-10-28 | 动态能力注册：`Registration { id, method, registerOptions }` + `client/registerCapability`/`unregisterCapability`（`protocol.ts` 实提）——静态握手（initialize 的 capabilities）之外的**运行期增补/撤回** | **中**：比 MCP 重（有 id 化的注册/注销往返）；本项目"每轮重算"没有协议往返可省，引入 id 机制只增加状态。仅作"动态注册不是唯一形态"的反例参考 | **不引代码**。评分 2.5/5 |
| **bazelbuild/bazel**（批次 34 已评，Apache-2.0，cross-ref） | 同批次 34 研究 §5（本轮未重取） | — | `SpawnStrategy.canExec(spawn)` 能力判定 + `exec()`——"后端选择来自能力判定，不是入口"（批次 34 §5 采纳决定原文） | 交叉引用：A-05 的方法级广告与 policy 的 `availableMethods` 门合起来就是 canExec 的广告半边 | 维持批次 34 裁定：不引代码，借两条经验。评分 4.5/5（原评分） |

**采纳决定**：不引入任何外部依赖。A-04/A-05/A-06 的形态在三个独立先例（MCP 的声明-发现-重发现、
openai-agents 的 per-run is_enabled、A-03 已落的默认参数化）上收敛到同一形状：**能力集合是唯一事实，
描述/枚举/就绪位都是它的纯函数派生**。没有任何一个先例支持"把就绪做成有状态机"或"描述与 schema 各维护
一份"——这加固了 2.3/3.2 的两条裁定。

---

## 6. 待裁定 / 停项登记

1. **A-04 判据里"schema"一词的口径**：清单判据"含 schema/SDK/样例"中，`schema` 按"program 形状
   （`const visual = { prepare, render }`）+ SceneV1 场景元素的最小说明"执行（本文 2.2 表第 3 行）。
   若主会话读成"prompt 里注入完整 SceneV1 JSON Schema"，正文预算（`MAX_BODY_CHARS = 12_000`，
   `FigureGeneration.kt:428`）需要先核算，且与"最小样例"重复——建议按"程序形状签名"口径执行，不阻塞落码。
2. **A-05 双位的生产装配值来源**：`jsEngineReady`/`jsMeasurementReady` 的真实装配值依赖 L3 引擎生产装配
   （契约 §5.1 `AndroidVisualExecutionEngine` 仍不存在生产构造点——批次 34 研究 §6.3 登记 UNVERIFIED）。
   本批接口+默认 false 落地后，**接线动作与"全门绿后接通 availableMethods"必须同批裁定**，不允许
   "双位接真值但 availableMethods 仍拒绝 JS"悬置超过一个批次（会造出"接口说可以、policy 说不行"的第二处矛盾）。
3. **M-04 的分类载体**：3.3 表把"度量未接线/度量失败不折 INVALID_PROGRAM"归入本批执行路径；
   具体载体（`VisualJsFailureKind` 增枚举 vs 仅诊断前缀约定）按 P-11 的既有先例（"UNATTRIBUTED 折回
   `INVALID_PROGRAM`、诊断前缀区分"，`VisualJsFailureClassification.kt:126-141` 实读）建议用**前缀/类别**
   而非新增端口枚举，需 T4 落码时按判据原文（"负向用例断言 measurement unavailable 单独分类"）定谳。
4. **`advertisingJsIsRefusedInThisWave` 的语义翻转**（2.3 红线 2）：这是改既有测试语义（判据行明文要求），
   不属"放宽断言"红线，但按"不许放宽断言"的共享纪律仍需在台账显式登记翻转理由与替代负向。
5. **停项登记（沿批次 34 §7）**：`core/domain/FigureGenerationCoordinator.kt:19-20` KDoc 过时问题不属本
   五行，维持既有停项；本文所有设计目标文件实跑 CLEAN，无新增整文件纳入注记需求。

---

## 7. 未验证项

1. **全部 JVM/设备用例未运行**：本文零 Gradle（共享纪律），2.2/3.4/4.2 的用例表是**待执行定义**，
   不是执行结果；A-08 既有用例 `FigureGenerationCoordinatorTest.kt:463` 本轮也未跑（清单 v3 补记 5 口径沿用）。
2. **`visual-sdk.js` 签名清单的完备性**：本轮只实提了 `visualSdk` 的 6 个公开原语（`circle/arc/arrow/
   axis/polyline/random`，awk 于 `visual-sdk.js:77-281`）；bootstrap 注入的其它全局（`__ktRandom` 绑定、
   `Date`/`Math.random` 封闭）是否也属"SDK 签名"应注入 prompt 的面，**未与 T4 对齐**——建议按"模型可见的
   可调用面 = visualSdk + 程序形状"口径执行（bootstrap 是引擎内部纪律，不是模型 API）。
3. **`supportsParametricInteraction` 位在 JS 静态后端下的真实值**：`FigureGeneration.kt:151-152` 注释明说
   "JS 可以是静态后端（无交互）"，但 4E.2 交互开启后该位的装配值未定（本文 2.2 表按位如实渲染，不预设值）。
4. **MCP 规范许可的最终态**：LICENSE 文件自述"undergoing a licensing transition"（MIT → Apache-2.0），
   规范正文条款的最终归属以完成迁移后的 LICENSE 为准；本文按"新贡献即 Apache-2.0"引用（单来源：仓库
   LICENSE 文件本身；未见矛盾来源）。
5. **openai-agents-python 的 `is_enabled` 运行期语义细节**（每轮求值时机、禁用工具是否对模型隐藏而非仅拒执行）
   只读了 `tool.py` 的字段文档，未读其 run loop 实现——本文只引用其 API 形态（bool | Callable），
   不引用其运行期语义。
6. **LangChain 动态工具**：本轮未取（gh code search 首轮零命中后按"每缺口最多 3 轮"纪律停止，改以
   openai-agents-python 覆盖"派生/动态门"维度）；若 T4 需要更多先例可补一轮检索。

## 8. 来源

- 本地源码（本轮实读，行号见正文）：`core/data/.../model/FigurePromptAssembler.kt`、`OpenAiModelProtocol.kt`、
  `OpenAiModelTaskAdapters.kt`、`TutorFigureGenerator.kt`、`AttachedImageGenerator.kt`、
  `RoomModelTaskRepository.kt`、`RoomTutorToolRunner.kt`；`core/data/.../visual/DefaultFigureGenerationCoordinator.kt`、
  `VisualJsAssets.kt`、`VisualFrameInputs.kt`、`VisualJsFailureClassification.kt`；`core/data/src/main/resources/visual/visual-sdk.js`；
  `core/model/.../Capabilities.kt`、`TutorToolDescriptions.kt`、`FigureGeneration.kt`、`FigureGenerationPolicy.kt`、
  `TutorToolLoop.kt`；`core/domain/.../VisualExecutionEngine.kt`；`app/.../SmartMistakeBookApplication.kt`；
  测试 `TutorCapabilitySnapshotTest.kt`、`FigurePromptParityTest.kt`；`docs/current/visual-generation-contract.md`。
- 文档（本轮实读）：`.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md`；
  `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（`:192-201`、`:381`）；
  `docs/research/2026-10-29-dual-backend-wiring.md`；`docs/research/2026-10-30-batch-35-baseline.md`。
- GitHub / 官方（本轮 `gh api` / WebFetch 实取，2026-10-30）：
  - https://modelcontextprotocol.io/specification/2025-06-18/server/tools （tools 能力声明、tools/list、
    `notifications/tools/list_changed` 原文）
  - https://github.com/modelcontextprotocol/modelcontextprotocol （`gh api` 元数据 9,562★ / pushed 2026-10-29；
    `contents/LICENSE` 过渡声明原文）
  - https://github.com/openai/openai-agents-python （`gh api` 元数据 MIT / 30,375★ / pushed 2026-10-27；
    `contents/src/agents/tool.py` 的 `FunctionTool.is_enabled` 原文）
  - https://github.com/microsoft/vscode-languageserver-node （`gh api` 元数据 MIT / 1,796★ / pushed 2026-10-28；
    `contents/protocol/src/common/protocol.ts` 的 `Registration` 与 MIT 头原文）
  - https://github.com/bazelbuild/bazel （cross-ref，元数据沿批次 34 研究 §5，本轮未重取）
- 本轮实跑命令清单（可重跑）：见 §0 表"证据"列；其中关键三条——
  `grep -rn "FigureGenerationRequest(" core/data/src/main --include="*.kt"`（恰 3 处）、
  `awk 'NR>=77 && NR<=281' core/data/src/main/resources/visual/visual-sdk.js | grep -E "^\s+[a-zA-Z_]+:"`
  （visualSdk 6 原语）、十文件逐一 `git status --porcelain`（全部 CLEAN）。
