# 智能体稳定性加固与能力补齐（2026-11-18）

> 性质：本批在 `D:/smbk-agent-wt` 工作树上完成；外部资料只作为**机制对照**，不照搬代码。
> 本文件记录：外部来源核对结果、采纳/不采纳的理由、落地文件、验证证据、明确未做项。
> 所有"绿/通过"只对应本文第 5 节的命令与输出；未执行的面一律写 UNVERIFIED。

## 1. 本批解决的真实失败

| 失败形态 | 修复 |
|---|---|
| 工具结果超过 `TutorToolOutcome` 6,000 字符上限时整轮工具调用失败 | 完整结果写入本地工件（内容寻址、TTL/条数回收），模型只收有界预览 + `resultId`；新增 `READ_RESULT` 分页回读 |
| 8 轮工具历史把提示词推过 Provider 上下文窗口 | `TutorPromptBudget.compactedForTutorPrompt`：最新结果保完整、较早轮次降成带标记的短摘要（32k 字符预算） |
| 一次 429/503/连接重置直接落 `RETRYABLE_FAILURE`，学生必须手点重试 | `ModelProviderRetry`：429/5xx/连接类 `IOException` 在**尚未产生任何增量**时指数退避重发；`Retry-After` 优先并封顶 |
| 增量已流到屏幕后重发会把半截回答变成重复内容 | `DeltaPresence` 红线：思考/正文/工具调用任一增量到达后不再重发（与 OpenAI Agents SDK "unsafe emitted stream events veto retries" 同构） |
| 智能体看不到今天的复习计划，也只能加不能撤 | 新增 `REVIEW_PLAN_READ`（只读注入）与 `REMOVE_FROM_REVIEW_PLAN`（不可变 revision，`operation="remove"`） |
| 导出只能 PDF，学生说"要 Word"时模型无法满足 | `START_EXPORT.format`（pdf/docx），格式进入导出任务身份与执行器等价判定 |
| 智能体不能编考试日历、不能改知识点关联 | 新增 `ADD_EXAM`/`REMOVE_EXAM`/`RECLASSIFY_PROBLEM`，以及写侧唯一坐标来源 `KNOWLEDGE_CATALOG_READ` |

## 2. 外部成熟项目对照（已逐项核对一手源码）

| 来源（一手） | 机制 | 采纳 | 不采纳 / 差异 |
|---|---|---|---|
| OpenAI Agents SDK `run_internal/model_retry.py`（<https://github.com/openai/openai-agents-python/blob/main/src/agents/run_internal/model_retry.py>） | 重试由 `RetryPolicy` 驱动，**无默认状态码白名单、无默认重试次数**；退避默认 0.25s 起、2s 封顶、×2、抖动 0.875–1.125；`Retry-After` 优先于计算退避；**已发出的流事件否决重试** | 采用"增量到达即禁止重发"的否决规则与 Retry-After 优先级；退避形状取本地参数（500ms 起、8s 封顶、等抖动） | 不引入其 provider 错误链解析；不把默认改为"无策略即 0 次"——本应用的产品语义是"任务尽量自愈"，因此给出显式默认策略（3 次尝试） |
| Pydantic AI `usage.py`（<https://github.com/pydantic/pydantic-ai/blob/main/pydantic_ai_slim/pydantic_ai/usage.py>） | `UsageLimits(request_limit=50, tool_calls_limit=None, total_tokens_limit=None, ...)` | 采用"运行级预算显式化"的思路：本轮已有 `MAX_TOOL_ROUNDS=8`、每轮 `MAX_TOOL_CALLS_PER_ROUND=4`、声明上限 16；另加提示词历史字符预算 | 不引入 token 计数上限：当前 Provider 响应不返回 usage，本地按 token 估算是伪精确；改用字符/轮次/声明数三个可观测预算，避免"看起来有 token 门、实际没数" |
| LangChain `ToolMessage.artifact`（<https://github.com/langchain-ai/langchain/blob/master/libs/core/langchain_core/messages/tool.py>） | 完整工具输出与模型可见内容分离，artifact 不进模型 | 采用 `resultId` + 本地工件 + 预览信封（字段：`resultId`/`preview`/`totalChars`/`nextStep`） | 不做进程内 artifact 注入：工件必须跨进程重启可读，且要有 TTL/条数回收 |
| LangChain `trim_messages`（<https://github.com/langchain-ai/langchain/blob/master/libs/core/langchain_core/messages/utils.py>） | 按预算裁剪消息历史，保留系统/最新消息 | 采用"最新优先、旧轮降级为摘要并标记"的压缩 | 不裁剪用户原话/系统提示词：只压缩工具历史，学生文字与规则不丢 |
| MCP `CallToolResult.structuredContent` / `resource_link`（<https://modelcontextprotocol.io/specification/2025-06-18/server/tools>） | 工具结果可带结构化内容与资源链接 | 仅作为信封设计参考（结果 + 可回读引用） | 不实现 MCP wire；本应用协议是自有 OpenAI 兼容信封 |

交叉核对说明：上表四条 GitHub 源码均在本轮用原始文件核对（`UsageLimits` 字段、`ToolMessage.artifact` 文档串、retry 常量与 veto 语义）；单来源项不当作"行业标准"，只作为机制出处。

## 3. 落地清单（按文件）

### 3.1 核心预算与工件
- `core/model/src/main/kotlin/.../TutorPromptBudget.kt`（新）：提示词工具历史压缩。
- `core/domain/src/main/kotlin/.../TutorToolResultStore.kt`（新）：工件读写/裁剪端口。
- `app/src/main/kotlin/.../TutorToolResultStore.kt`（新）：`noBackupFilesDir/tutor-tool-results`，`r-`+SHA-256 内容寻址，≤256 KiB，临时文件提交，TTL 24h，保留最新 64 条，单次读取 ≤3,500 字符。
- `core/data/.../RoomTutorToolRunner.kt`：`boundedToolData` 超 6k 写工件；`READ_RESULT` 分页；工件不可用时退回有效 JSON 预览（绝不返回半截 JSON）。
- `core/data/.../OpenAiModelTaskAdapters.kt`：工具历史渲染走 `compactedForTutorPrompt`。

### 3.2 Provider 重试
- `core/data/.../ModelProviderRetry.kt`（新）：状态码判定、退避/抖动、`DeltaPresence`。
- `core/data/.../OpenAiCompatibleModelGateway.kt`：`postWithProviderRetry` 包裹流式/非流式发送；授权异常与解析异常不在这一层重试。
- `core/data/.../OpenAiModelTransport.kt`：`ModelHttpResponse.retryAfterMillis`，解析 `Retry-After` 的 delta-seconds 形式。

### 3.3 智能体能力面
- 工具：`TutorToolName.READ_RESULT`、`REVIEW_PLAN_READ`、`KNOWLEDGE_CATALOG_READ`（15 枚声明，仍 ≤16）。
- 动作：`REMOVE_FROM_REVIEW_PLAN`、`ADD_EXAM`、`REMOVE_EXAM`、`RECLASSIFY_PROBLEM`；`START_EXPORT` 新增 `format`。
- `core/model/.../TutorLocalAction.kt`、`Capabilities.kt`、`TutorToolDescriptions.kt`、`TutorToolTrace.kt`、`TutorToolLoop.kt`。
- `core/domain/.../TutorToolGate.kt`、`TutorPermissionPolicy.kt`、`TutorLocalActionExecutor.kt`、`StudyExperienceRepository.kt`（`removeFromReviewPlan` 默认拒绝）。
- `core/data/.../RoomBackedStudyExperienceRepository.kt`：移除计划项时生成新不可变 revision（新指纹、重排 ordinal），不原地改历史行。
- `app/.../TutorReviewContext.kt`（新）：计划/容量/排程/考试的有界只读 JSON。
- `app/.../TutorAgentContextRepository.kt`：合并 `reviewContext`，`knownSubjects` 过滤空白。
- `app/.../TutorApplicationActions.kt`：考试增删（按稳定 `entryId` upsert，重放不堆行）；重分类走 `correctConfirmedOrganization`（目录重新校验，板块/手动分类保留）。
- `app/.../SmartMistakeBookApplication.kt`：`catalogSearch` 接线到 `knowledgeCatalogRepository`。

## 4. 明确未做（不是"已完成"）

1. **4E.2 交互面（`reduce`、原生 controls、`NativeTutorFormV1`、`ui_state_json` 草稿）未实现。**
   原因：当前树里 `SceneV1` 编译/持久化存在，但 `core/ui` 没有任何 RenderScene 消费者（无
   `VisualSceneCard`/`VisualControls`），feature/tutor 对 `visualId` 零引用；把 `controls`/`reduce`
   放进编译器只会让模型"能写但没有 UI 能执行"——按契约 §3.2 "未启用的控件/事件/模式解析与执行
   同时拒绝"以及"不得广告必然不可用的能力"，本批不打开这个面。要做必须连同原生绘制/控件/状态
   草稿/提交协议一起成批落地并有设备证据。
2. **题目删除/合并/拆分未新增。** 领域层只有归档/恢复与卷面编辑（`splitDraft` 属录入草稿），
   没有库级删除/合并 API；绕过领域自造删除会破坏备份/导出/复习引用的完整性，故不做。
3. **真实第三方 Provider 端到端未验证。** 设备上没有可用模型凭据；重试/工具环的模型侧行为
   仍是 UNVERIFIED。
4. **API 23 / arm64 / 16 KB 页面未在本批复验。**

## 5. 验证证据（本轮真实执行）

### 5.1 单元门（强制重跑，非缓存）

命令：

```bash
./gradlew :core:model:cleanTest :core:model:test \
  :core:domain:cleanTest :core:domain:test \
  :core:data:cleanTestDebugUnitTest :core:data:testDebugUnitTest \
  :core:database:cleanTestDebugUnitTest :core:database:testDebugUnitTest \
  :core:export:cleanTestDebugUnitTest :core:export:testDebugUnitTest \
  :feature:review:cleanTestDebugUnitTest :feature:review:testDebugUnitTest \
  :feature:library:cleanTestDebugUnitTest :feature:library:testDebugUnitTest \
  :feature:capture:cleanTestDebugUnitTest :feature:capture:testDebugUnitTest \
  :feature:tutor:cleanTestDebugUnitTest :feature:tutor:testDebugUnitTest \
  --continue
```

结果：`BUILD SUCCESSFUL in 1m 38s`；XML 汇总 **4,294 tests / 0 failures / 0 errors**：

| 模块 | 例数 |
|---|---|
| core:model | 674 |
| core:domain | 759 |
| core:data | 1,786 |
| core:database | 248 |
| core:export | 183 |
| feature:review | 116 |
| feature:library | 111 |
| feature:capture | 149 |
| feature:tutor | 268 |

### 5.2 编译/组装强门

```bash
./gradlew :core:data:compileDebugAndroidTestKotlin :core:database:compileDebugAndroidTestKotlin \
  :feature:library:compileDebugAndroidTestKotlin :feature:capture:compileDebugAndroidTestKotlin \
  :feature:tutor:compileDebugAndroidTestKotlin :app:compileLocalFirstDebugAndroidTestKotlin \
  :app:assembleLocalFirstDebug
```

结果：`BUILD SUCCESSFUL in 20s`。

### 5.3 设备轨（x86_64 / API 34 模拟器 emulator-5554）

先卸载设备上的 release 变体（`com.tingyun.smartmistakebook.localfirst`，versionCode 101429），
安装本轮 debug 与 androidTest APK，然后：

| 用例 | 命令类 | 结果 |
|---|---|---|
| 考试动作真机落库（ADD_EXAM→DataStore→REMOVE_EXAM） | `am instrument -e class ...TutorExamActionsInstrumentedTest` | `OK (1 test)`，Time 0.28s |
| 工具结果工件（内容寻址/分页/缺省与坏 id） | `...TutorToolResultStoreInstrumentedTest` | `OK (1 test)`，Time 0.043s |
| 生产装配 + 隔离 QuickJS + `agent.callTool` 工具环 | `...TutorAgentJsToolInstrumentedTest` | `OK (1 test)`，Time 1.615s |
| 启动冒烟 | `am start -W` + logcat | `Displayed ... +17s819ms`（冷启动，未 `-W` 超时判定）；logcat 无 `FATAL EXCEPTION` / `UninitializedPropertyAccess` / `Missing ... Port` / `SQLiteConstraintException`；`VisualJsService` warm-up `ready for requests` |

### 5.4 未覆盖

- 新增 `RECLASSIFY_PROBLEM` 只在 JVM 准入/工具面测试覆盖；设备没有可用的目录+题目夹具，
  **设备未执行**。
- `ModelProviderRetry` 的 HTTP 层 `Retry-After` 解析只有网关单测（构造 `ModelHttpResponse`），
  真实 OkHttp 响应头路径未单独插桩。
- 真实 Provider 的 429/5xx 自愈只能靠未来真机记录确认。

## 6. 风险

- 重试会增加一次请求的潜在费用（最多 3 次尝试）；增量到达即停的规则避免了重复内容，但
  "没有增量、上游其实已计费"的形态无法本地识别。
- 工件目录与 `noBackupFilesDir` 同生命周期；备份不包含工件（可接受：工件是工具结果的缓存，
  不是用户数据）。
- `KNOWLEDGE_CATALOG_READ` 把原始 `nodeId` 暴露给模型（写侧需要精确坐标）。写侧仍由目录重新
  校验，编造身份无法落库；这是与 `KNOWLEDGE_READ` 会话代号通道刻意的分工，已在 KDoc 写明。
