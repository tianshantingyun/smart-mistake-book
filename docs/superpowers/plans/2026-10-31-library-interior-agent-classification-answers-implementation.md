# 错题本内页 A 方案与三项功能接线实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（明确要求分工时）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框跟踪进度。本次默认在当前工作区内联执行，不自动调度子代理。

**目标：** 交付阅读优先的错题详情与编辑界面，并接通智能体修改题图、全知识库关键词分类及可单独保存的详细答案。

**架构：** 保留现有结构化题面、版本冲突检查、模型任务仓储、统一生图协调器和后台导出。目录搜索采用完整字段分页读侧，人工跨科绑定按节点身份写入；修改建议先进入草稿；答案按精确题面准备，预览后与题目独立交付。

**技术栈：** Kotlin、Jetpack Compose、Coroutines/Flow、现有 Room 3 与 SQLite、WorkManager、既有模型网关、现有 PDF/Word 渲染器。JS 方法继续受既有隔离运行库专项准入约束。

---

## 1. 授权、基线和交付方式

正式设计：`docs/superpowers/specs/2026-10-31-library-interior-agent-classification-answers-design.md`。用户已确认 A 布局、跨学科关联且不改题目主学科、缺失答案由智能体补全后预览，以及本设计/计划两条新增路径。

仓库：`C:/Users/听云/.codex/worktrees/ui-green-pages/smart mistake book`。分支：`codex/ui-green-pages-2026-10-27`。起始提交：`c4a16e9c265673daa418bff1d2556dfe4591b4d1`。数据库版本：69。

三个子项目写在同一份已获路径授权的计划中，各自完成服务、界面和验证后可单独交付：

| 子项目 | 任务 | 完成边界 |
| --- | --- | --- |
| A · 全库分类 | T1–T3 | 真库中超过首批 256 项的节点可检索，跨科选择真实落库 |
| B · 智能体修改 | T4–T6 | 真正生成/编辑图资产，采用草稿并经版本保存后显示 |
| C · 详细答案 | T7–T10 | 正确复用或补全解答，PDF/Word 形成单独可验证的答案文件 |
| 共享收口 | T0、T11 | 接续已有修改，按 A 布局统一原生交互与验证 |

文档阶段已完成；用户已于实施启动弹窗授权计划列明的 42 个新源码/测试文件、3 个生成 schema 及实施证据目录。现按任务记录实际实施进度。所有进入网关或持久 JSON 的新 DTO 使用 kotlinx.serialization；默认空字段遵循旧指纹兼容规则。清单内已获准路径直接实施，不重复询问；清单外新增路径仍须弹窗取得同意。不得把两份文档的路径授权扩大为任意源码路径授权。修改已存在的文件在获准执行任务后按任务范围进行。

既有未提交文件必须保留：`core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/ClaudeDialog.kt`、`core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/WorkspaceSheet.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditRoute.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeExportFormatSheet.kt`。不能用 checkout/reset 清理它们，也不能在文档提交中夹带这些代码。

迁移按本基线顺序使用 69→70、70→71、71→72。若执行时版本已变化，先重新核对并修订本计划的迁移编号，再编写迁移；禁止覆盖同名迁移或跳过迁移链。

2026-11-01 执行调整：用户要求以完成改造为主，测试无需过度严谨。保留必要编译及少量关键流程检查，不再逐批扩展全面测试矩阵；设备、长时及完整视觉验收留到功能接齐后统一处理。未执行的测试项保留未勾选，不将编译通过写成原生流程已验证。

## 2. 文件组织与执行命令

文件在各任务的“文件”栏逐项列出；新增文件只承担列明职责。`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt`、`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt` 和根界面只增加必要注册/装配，新协议、数据库作业与业务规则放到独立文件。不能把新增整套功能都塞进这些大文件。

使用同源码的英文 Junction，串行运行 Gradle；不在共享检出目录中改文件。先运行以下 PowerShell 定义，之后各任务使用 `Invoke-SmbGradle`：

```powershell
$smbRepo = 'D:/smb-ui-forest-20261027'
$smbGradle = Join-Path $smbRepo 'gradlew.bat'
if (!(Test-Path -LiteralPath $smbGradle)) { throw '项目 Gradle Wrapper 不存在' }
function Invoke-SmbGradle {
    param([Parameter(Mandatory=$true)][string[]]$TaskArguments)
    & $smbGradle --project-dir $smbRepo --gradle-user-home 'D:/smart mistake book/.gradle' --no-configuration-cache @TaskArguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle 失败：$LASTEXITCODE" }
}
```

`gradle-wrapper.properties` 已固定 Gradle 9.6.1 及分发摘要。纯 JVM 的 `:core:model`、`:core:domain` 用 `:test`；Android 库用 `:testDebugUnitTest`，应用变体用 `:testLocalFirstDebugUnitTest`。

每个任务采用红灯→最小实现→绿灯→本地审查→提交。计划中的 Kotlin 块是新契约/测试的明确示例，不代表它们已经存在或已经编译。只暂存本任务文件；先核对 `git diff --cached --name-only`，禁止 `git add .`、跳过 hooks 或把其他任务改动混入。每个任务提供提交标题，实际提交前完整阅读暂存 diff。

## T0 · 接续当前界面基线

**文件：** 上述五个既有未提交文件；`core/ui/src/test/java/com/tingyun/smartmistakebook/core/ui/` 与 `feature/library/src/test/kotlin/com/tingyun/smartmistakebook/feature/library/` 的现有测试。此任务不增加为了匹配排版实现而编写的测试。

- [x] 阅读当前 diff，确认共享弹层、版本区、编辑区和格式面板的真实调用方；记录未完成接线。新的 AI/答案按钮暂不显示可用状态。
- [x] 运行既有相关测试和编译，失败时先确认原始失败；只修复这五个文件引入的最小编译/接口问题，不将旧业务问题包装成视觉改造。
- [x] 核对导出默认 PDF、取消不导出、格式辅助说明、历史版本只读和既有 testTag；保留原图自然比例。
- [x] 通过后提交界面基线，标题 `refactor(ui): 统一错题内页容器与操作层次`，新功能仍保持未完成。

```powershell
Invoke-SmbGradle -TaskArguments @(':core:ui:testDebugUnitTest', ':feature:library:testDebugUnitTest', ':feature:library:compileDebugKotlin', ':app:compileLocalFirstDebugKotlin')
```

## 子项目 A · 全库分类

### T1 · 完整知识目录查询、关键词与分页

**新增：**

- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/KnowledgeCatalog.kt`：查询、游标、命中、可选节点身份。
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeCatalogRepository.kt`：目录查询端口与本地词组提取。
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomKnowledgeCatalogRepository.kt`：数据库结果到领域目录的适配。
- `core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeCatalogKeywordsTest.kt`。
- `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomKnowledgeCatalogRepositoryTest.kt`（实施中发现清单遗漏，用户已在弹窗单独授权）。
- `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeCatalogSearchInstrumentedTest.kt`。

**修改：**

- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt`：新增分页目录读口，保持原讲题读口。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt`：完整字段参数化查询。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt`：查询实现与转发。

- [x] 写关键词和真库分页失败用例：300 个已审节点中第 299 个仅在尾部别名命中；教学段落命中；同名跨科节点；空查询多页不重不漏；退役/未审核/不可展示材料不进入结果；知识库更新后旧游标失效。
- [x] 运行领域测试确认新提取函数尚不存在，或断言仍受 256 项截断而失败。
- [x] 实现下面契约。`catalogRevision` 采用仓储初始化时的一次会话代号与 Room 节点、材料、材料绑定/生命周期表失效序号构成的 generation；只在这些表变化或库重开时推进。该值在未变更期间稳定，不逐键重算全库摘要，也不每次查询生成新随机数。游标在仓储内编码并校验查询摘要和 generation。每页采用一致读；这不是跨写事务的长期快照，失效事件到达后丢弃旧页并重查，最终选择写入时再核对节点有效性。

```kotlin
data class KnowledgeNodeSelection(val nodeId: String, val taxonomyVersion: String)
data class CatalogCursor(val querySha256: String, val catalogRevision: String, val lastSortKey: String)
data class CatalogQuery(val text: String, val cursor: CatalogCursor? = null, val pageSize: Int = 32)
data class CatalogHit(
    val node: KnowledgeNodeSelection, val subject: String, val chapterPath: List<String>,
    val name: String, val snippet: String, val keywords: List<String>,
)
data class CatalogPage(val hits: List<CatalogHit>, val next: CatalogCursor?, val catalogRevision: String)
interface KnowledgeCatalogRepository { suspend fun search(query: CatalogQuery): CatalogPage }
fun extractCatalogKeywords(sentence: String, approvedPhrases: List<String>): List<String>
```

```kotlin
@Test fun sentenceKeepsMeaningfulPhrases() {
    assertEquals(listOf("二次函数", "最小值"), extractCatalogKeywords(
        "我想查找二次函数的最小值", listOf("二次函数", "最小值"),
    ))
}
```

查询搜索名称、完整别名、知识描述和允许展示教学段落。以已审可见词组词典提取整句里的最长有意义词组；去重并按句中位置排列，不展示内部 n-gram。词典无命中时保留规范化输入作字面查询。多个词组按 OR 找候选、匹配词组数优先，再按学科/路径/节点 id 稳定排序；显示真实片段及完整路径。完整字段匹配决定集合，现有有损索引最多辅助排序，不作为候选白名单。SQLite 的 `%`、`_`、反斜线转义，值只通过绑定参数传入。空查询按目录顺序分页。

- [x] 在后台查询并验证边界；不得 `readSubjectKnowledgeNodes(..., 256)` 后在 UI 里过滤，也不得每键扫描加载全库到主线程。新增分页端口不得修改既有讲题召回排序或 KD-24 的索引版本。用 10,000 节点/50,000 可展示段落记录设备耗时；若查询连续造成可见卡顿，先修复本读侧查询再交付，不通过改回截断候选规避。
- [x] 领域和设备测试通过后提交 `feat(library): 支持全库关键词目录检索`。

```powershell
Invoke-SmbGradle -TaskArguments @(':core:domain:test', ':core:database:testDebugUnitTest', ':core:data:testDebugUnitTest', ':core:database:assembleDebugAndroidTest')
```

### T2 · 按节点身份写入人工跨学科分类

**修改：**

- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeOrganizationRepository.kt`。
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeOrganizationRepository.kt`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomProblemOrganizationStore.kt`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/DraftPort.kt`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabaseRecords.kt`。
- `core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeOrganizationRepositoryTest.kt`。
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/mistake/MistakeOrganizationRepositoryInstrumentedTest.kt`。

- [x] 真库失败用例：数学题绑定物理节点后，节点 id/真实学科保留，题目仍为数学；两个同名不同 id 节点均保留；重复请求幂等；退役、taxonomy 不符和题目版本变化拒绝且不部分写入。
- [x] 在 `ProblemOrganizationSelection` 增加 `knowledgeSelections: Set<KnowledgeNodeSelection> = emptySet()`，旧字段仍按原语义处理。新选择以节点 id 解析，不经 `displayName.lowercase()` 去重或降格为本学科自由标签。
- [x] 写入事务逐一读取并验证已审、有效的 node id/version，沿用现有分类/知识绑定写口。跨科节点的章节路径保留真实来源。人工路径允许跨科；自动分类、讲题数据披露边界、节点掌握证据的所属学科均不随之扩大。沿用既有纠正分类确认，不把在 picker 点选等同于数据库提交。

```kotlin
// 新字段同时进入命令内容身份；不同节点不能因重名而得到同一确认指纹。
val a = KnowledgeNodeSelection("math-scalar", "math-v1")
val b = KnowledgeNodeSelection("physics-work", "physics-v1")
assertEquals(2, setOf(a, b).size)
// 真库用例还必须分别断言 problem.subject 与两条 knowledge_node_id 绑定。
```

- [x] 跑现有分类协议/确认/证据隔离回归和新增设备用例；检查重开页面仍保留跨科关联，而旧模型建议不能覆盖人工纠正。
- [x] 提交 `feat(library): 保留节点身份的跨学科分类`。

```powershell
Invoke-SmbGradle -TaskArguments @(':core:domain:test', ':core:database:testDebugUnitTest', ':core:data:testDebugUnitTest', ':core:data:assembleDebugAndroidTest')
```

### T3 · 全库分类原生交互与真实仓储装配

**新增：**

- `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/KnowledgeCatalogViewModel.kt`：查询、分页、关键词与选择状态。
- `feature/library/src/test/kotlin/com/tingyun/smartmistakebook/feature/library/KnowledgeCatalogViewModelTest.kt`。

**修改：** `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/` 下的 `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeOrganizationTreePicker.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeOrganizationState.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeOrganizationSection.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeOrganizationCorrectionEditor.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt`；`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt`、`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt`；现有 `feature/library/src/test/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeOrganizationTreePickerTest.kt` 与 `feature/library/src/androidTest/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeOrganizationInstrumentedTest.kt`。

- [x] VM 失败用例：快速输入 A/B 后只接受 B；上一页查询完成晚到不覆盖新查询；相同 query 加载下一页只请求一次；切换目录版本清空游标但保留已选稳定节点；空态与错误态可以返回选择列表。
- [x] 将输入接到 `debounce(250)`、`distinctUntilChanged()`、`flatMapLatest`，数据库工作不在主线程；结果携带查询身份，合并页按 node id 去重。新 VM 依赖 T1 端口，不依赖全局 Application 或数据库具体类。

```kotlin
val pages = queryText.debounce(250).distinctUntilChanged().flatMapLatest { text ->
    flow { emit(catalog.search(CatalogQuery(text = text))) }
}
// 下一页必须沿用当前 CatalogPage.next；过期 cursor 返回“目录已更新”并重新查询。
```

- [x] 原生底部面板展示全库范围、当前题目学科、关键词模块、学科/章节分组、匹配段落及已选状态。关键词负责筛选，结果负责选择；重复已选项不可再次加入。保留键盘关闭、独立内容滚动与固定「完成」操作。
- [x] 在应用中注入 `RoomKnowledgeCatalogRepository`，将选中节点送回已有纠正草稿，再由已有确认命令提交。用真库验证搜索首批目录之外节点和跨科重开结果；用例继续保留兼容的现有 testTag。
- [x] 提交 `feat(ui): 接通全知识库分类选择面板`。

```powershell
Invoke-SmbGradle -TaskArguments @(':feature:library:testDebugUnitTest', ':feature:library:assembleDebugAndroidTest', ':app:assembleLocalFirstDebug')
```

## 子项目 B · 智能体修改

### T4 · 结构化修改协议与模型任务注册

**新增：**

- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/MistakeEditTasks.kt`：基准、请求、白名单修改建议。
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeEditRepository.kt`：修改请求、作业观察、采用检查端口。
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiMistakeEditProtocol.kt`：提示词、请求和严格结果解析。
- `core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/MistakeEditTasksTest.kt`。
- `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/MistakeEditProtocolTest.kt`。

**修改：** `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt`、`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelEgress.kt`、`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/CapturedQuestionDocument.kt`；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTaskAdapters.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiCompatibleModelGateway.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/ModelCapabilityTester.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt`；`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ModelConfigurationStore.kt` 与 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/DataStoreModelConfigurationStore.kt` 的新增任务角色映射。

- [x] 写失败用例：同一要求键相同，换要求或源图键不同；模型新增题目/块 id、任意 URI、额外字段、超长指令拒绝；旧序列化与空字段指纹完全不变；停止和缺能力返回既有准确失败状态。
- [x] 声明精确基准和 host 分配身份。请求携带当前结构化草稿与选中目标；图建议只允许 `FigureGenerationProposal`，不可将源码塞入正文。解析器按现有 id 白名单校验所有修改。

```kotlin
data class EditBasis(val entryId: String, val revisionId: String, val draftSha256: String)
data class StoredFigureReference(
    val blockId: String, val assetId: String, val contentSha256: String, val role: VisualRole,
)
// 新建在 CapturedQuestionDocument 上的已采用图引用字段默认空且 EncodeDefault(NEVER)。
// 图资产必须由宿主验证；此类型不携带模型任意 URI。
fun editOperationKey(basis: EditBasis, targetBlockId: String, sourceSha256: String?, instruction: String): String
```

`editOperationKey` 使用 UTF-8 长度前缀编码后的 SHA-256，指令仅做 Unicode/换行和首尾规范化，不裁掉有意义内容。相同要求重试保留逻辑 operation id，不能沿用不含指令的显式净化指纹。

```kotlin
@Test fun differentInstructionsDoNotReuseCleanRedraw() {
    val basis = EditBasis("entry-1", "rev-2", "a".repeat(64))
    val first = editOperationKey(basis, "figure-1", "b".repeat(64), "标出顶点")
    val second = editOperationKey(basis, "figure-1", "b".repeat(64), "标出对称轴")
    assertNotEquals(first, second)
    assertEquals(first, editOperationKey(basis, "figure-1", "b".repeat(64), "标出顶点"))
}
```

- [x] 注册 `ModelTaskKind.MISTAKE_EDIT`、`MistakeEditInput` 和对应 typed output，补齐 sealed 分支、数据库任务合法形状、能力探测、设置路由、备份读回和 ModelEgressPolicy。外发开关传真实值，不绕过现有策略；旧 Provider 快照未验证新任务时明确提示配置/测试能力，不静默宣称支持。
接线核验补充的现有文件：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/KnowledgeQuizTasks.kt`（任务完成校验）、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelProtocol.kt`（图片编码）、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/wire/ModelProbeSpec.kt`（合成探测）、`core/data/src/debug/kotlin/com/tingyun/smartmistakebook/core/data/model/FakeModelGateway.kt`（演示分支）、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt` 与 `docs/research/2026-10-17-6c2-content-selection-design.md`（备份类别及同源机器块）、`app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt`（模型设置能力说明）。对应既有网关、能力测试、备份契约和原生配置测试同步扩充。

当前代码使用统一模型配置，未设独立的任务角色路由；新编辑能力写入现有能力验证和同一网关，不新增模型配置入口。沿用同一次合成结构化探测，额外用真实编辑解析器核对合成 patch；旧验证缺少新能力位时保持不可用。

模型明确返回 `NO_CHANGE`、`NEED_SOURCE` 或 `UNAVAILABLE` 时保留其原因且不产生可采用差异；不得把这种合法结果误报成空修改成功。只有明确的修改建议可以进入采用流程。

- [x] MockWebServer 覆盖请求携带正确原图、异常/截断结果、取消与零能力零 HTTP；旧请求 golden 指纹回归通过后提交 `feat(model): 增加可审阅的智能体修改协议`。

```powershell
Invoke-SmbGradle -TaskArguments @(':core:model:test', ':core:domain:test', ':core:data:testDebugUnitTest', ':core:database:testDebugUnitTest')
```

### T5 · 可恢复修改作业、图资产与版本保存

**新增：**

- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeEditRepository.kt`：模型建议、绘图与作业状态编排。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/MistakeEditMigration_69_70.kt`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/MistakeEditJobEntity.kt`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/MistakeEditJobDao.kt`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/MistakeEditJobPort.kt`。
- `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/mistake/MistakeEditWorkflowTest.kt`。
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/mistake/MistakeAgentEditInstrumentedTest.kt`。

**修改：** `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabasePort.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/MistakeRevisionWritePort.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/MistakeDetailDao.kt`；`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeDetailRepository.kt`；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/DefaultFigureGenerationCoordinator.kt` 的来源/审计关联；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt` 的新表清单与恢复验证。

- [ ] 写失败用例：作业重开恢复；同一次点击不重复出网；不同指令不误复用；取消/晚到结果不采用；图和文字一起采用；保存冲突后原图/原题不变；新版本只挂经验证资产，重试不重复挂图。
- [x] 迁移 69→70 新建 `mistake_edit_job`，主键 operation id，保存 entry/revision/draft/target/source/instruction 摘要、模型 request id、请求与建议 JSON、状态和时间。状态集合与 T4 领域类型一致：`QUEUED/REQUESTING/GENERATING_FIGURES/READY/NO_CHANGE/NEED_SOURCE/UNAVAILABLE/ADOPTED/FAILED/CANCELLED/SUPERSEDED`，状态推进用数据库条件更新；作业存在不等于已采用，非修改结果不可采用。使用应用级作用域收集模型执行，离开页面不等于用户取消。启动恢复只调度已授权的未完成作业，READY 和非修改结果不重新生成；T6 应用装配注册恢复入口。生成资产写入既有资产存储，不创建 UI 临时 URI 权威。
- [x] 经 `ModelTaskRepository` 发起修改任务，绘图统一调用 `FigureGenerationCoordinator.generate(request, proposal)`。合法 JS proposal 在当前未装配状态返回不可用；不得直接调用 QuickJS、在主进程执行脚本、默认改为远程生图或重复建一套后端选择器。引用 `docs/current/figure-generation-prompt-policy.md` 与 `docs/current/visual-generation-contract.md`；仅在既有视觉专项通过共享场景验证、原生渲染及隔离引擎装配后，统一开放 JS 能力。

```kotlin
fun isEditBasisCurrent(proposed: EditBasis, current: EditBasis): Boolean = proposed == current
@Test fun lateProposalCannotReplaceCurrentDraft() {
    val proposed = EditBasis("entry-1", "rev-2", "a".repeat(64))
    val current = proposed.copy(draftSha256 = "b".repeat(64))
    assertFalse(isEditBasisCurrent(proposed, current))
}
```

- [x] 新 `StoredFigureReference` 按 `blockId` 解析，新增修订命令带已采用资产选择；事务复核资产存在、SHA、目标块、角色及该作业的来源与准入记录。不能仅因构造出一个 DTO 就允许挂入任意既有资产。题面、文字证据、图引用指纹同时更新；保持原始拍照证据只读，不能复制旧版生成图冒充新图。详情、结构化渲染和导出解析同一引用。
- [ ] 恢复先核对 canonical 请求、持久资产和题面身份，损坏建议标失败；进程重启不自动重跑已付费成功的生成。无在飞依赖的弃用资产交给既有清理机制；不得删除历史题目的有效资产。迁移使用 Room 导出 schema 70，禁止手写 schema JSON。
- [ ] 通过事务/迁移/备份回归后提交 `feat(library): 接通可恢复的题图修改与修订保存`。

```powershell
Invoke-SmbGradle -TaskArguments @(':core:data:testDebugUnitTest', ':core:database:testDebugUnitTest', ':core:data:assembleDebugAndroidTest', ':core:database:assembleDebugAndroidTest')
```

### T6 · 编辑页真实智能体入口、对比与采用

**新增：**

- `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeAgentEditSheet.kt`：范围、指令、任务进度、前后对比和采用按钮。
- `feature/library/src/test/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeAgentEditStateTest.kt`。
- `feature/library/src/androidTest/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeAgentEditInstrumentedTest.kt`。

**修改：** `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditViewModel.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditRoute.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt`；`core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/StructuredContentRenderer.kt`；`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt`、`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt`。

- [ ] 失败用例覆盖：采用前草稿未变；关闭建议仍在编辑页；采用标脏但未改正式版本；保存后重开显示新图；手动输入与晚到建议冲突不丢草稿；多图选择只修改指定块。
- [x] VM 增加 `requestAgentEdit(targetBlockId, instruction)`、`adoptAgentEdit(operationId)`、`cancelAgentEdit(operationId)`，采用前重新比较 `EditBasis`，对建议逐项合法性校验成功后一次更新当前草稿。已有 `save()` 仍是唯一题目修订提交动作。

```kotlin
// 函数定义属于 MistakeRevisionEditViewModel；采用入口不能调用 reviseProblem。
fun adoptAgentEdit(operationId: String) {
    // 读取仓储的已验证建议，并比较当前 revisionId/draftSha256；不接受 UI 传入建议正文。
    viewModelScope.launch { adoptPreparedSuggestion(operationId) }
}
// adoptPreparedSuggestion 是同一 VM 的私有 suspend 方法：读取 T5 的持久建议，
// 通过后更新草稿/dirty；冲突时只更新 conflict，不触碰现有 title、blocks 或图资产选择。
```

- [x] 原生入口使用蓝色弱强调，不替换手动表单。明确当前修改的是题面草稿而非导出副本。进度从持久作业观察，按钮只在合法状态可用；失败提供原有设置/重试/返回编辑出口，不伪造成功动效。
- [x] 应用级装配真实 `RoomMistakeEditRepository`、模型仓储和统一协调器。缺模型/图能力显示准确不可用状态；真库加受控 Provider 测试打通提出建议→真实资产→采用→保存→详情/导出，不能只用 fixture 证明生产接线。
- [ ] 通过后提交 `feat(ui): 在题面编辑中接通智能体代修改`。

```powershell
Invoke-SmbGradle -TaskArguments @(':feature:library:testDebugUnitTest', ':core:ui:testDebugUnitTest', ':feature:library:assembleDebugAndroidTest', ':app:assembleLocalFirstDebug', ':app:assembleStrictOfflineDebug')
```

## 子项目 C · 详细答案

### T7 · 结构化详解文档、复用判据与模型协议

**新增：**

- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/DetailedSolution.kt`：逐题答案、基准、来源、核对状态和请求类型。
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/DetailedSolutionRepository.kt`：准备/观察、复用判据。
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiDetailedSolutionProtocol.kt`：结构化解答生成与严格解析。
- `core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/DetailedSolutionTest.kt`。
- `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/DetailedSolutionProtocolTest.kt`。

**修改：** `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt`、`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelEgress.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTaskAdapters.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiCompatibleModelGateway.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/ModelCapabilityTester.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ModelConfigurationStore.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/DataStoreModelConfigurationStore.kt`；新增任务种类 `MISTAKE_SOLUTION`，不借用带学习证据写入的 `TUTOR_EVALUATE`。

- [ ] 失败用例：旧 revision、修改后的条件、缺最终答案、缺步骤、任意远程图 URI、超预算文档拒绝；相同题面完整文档可复用；来源声明/正确性声明不能由模型自行升级；旧 JSON golden 不变。
- [ ] 定义并严格解析以下文档。`steps` 是现有 `QuestionDocument`，仅允许可展示的解题内容，经结构化 sanitizer；`StoredFigureReference` 复用 T4 的资产身份。来源、basis 与核对状态由宿主依据请求和已保存事实填充，wire 输出不允许自行指定这些宿主字段。

```kotlin
data class SolutionBasis(val revisionId: String, val questionSha256: String)
enum class SolutionOrigin { EXISTING, MODEL }
enum class SolutionVerification { UNVERIFIED, VERIFIED }
data class DetailedSolutionDocument(
    val basis: SolutionBasis, val finalAnswerMarkdown: String, val steps: QuestionDocument,
    val figures: List<StoredFigureReference>, val origin: SolutionOrigin,
    val verification: SolutionVerification,
)
fun canReuseSolution(document: DetailedSolutionDocument, target: SolutionBasis): Boolean =
    document.basis == target && document.finalAnswerMarkdown.isNotBlank() && document.steps.blocks.isNotEmpty()
```

正式 `canReuseSolution` 还须验证文档/资产完整性与未撤销状态，不能仅凭上述最小内容判断。只有机器答案没有解题步骤时返回不可复用。已有结构化解答按其精确题面身份适配；普通聊天或笔记不直接适配。

```kotlin
@Test fun changedQuestionCannotReuseOldSolution() {
    val basis = SolutionBasis("rev-2", "a".repeat(64))
    val document = DetailedSolutionDocument(basis, "1", QuestionDocument("steps", blocks =
        listOf(ContentBlock.Paragraph("step-1", "配方得到平方项加一。"))),
        emptyList(), SolutionOrigin.EXISTING, SolutionVerification.UNVERIFIED)
    assertTrue(canReuseSolution(document, basis))
    assertFalse(canReuseSolution(document, basis.copy(questionSha256 = "b".repeat(64))))
}
```

- [ ] 生成提示词请求面向用户的步骤、最终答案、必要公式和配图 proposal，不索取内部隐式思维链。不产生学习掌握证据。配置核对能力可返回独立核对事实；无事实时保留 `UNVERIFIED`，预览勾选不能改它。
- [ ] 新任务覆盖序列化、Provider 能力、停止、恢复、预算和外发授权；完整输出才准备答案配图，图仍走统一协调器。截断/格式错误不形成可导出成功文档。
- [ ] 测试通过后提交 `feat(model): 增加面向导出的结构化详细解答`。

```powershell
Invoke-SmbGradle -TaskArguments @(':core:model:test', ':core:domain:test', ':core:data:testDebugUnitTest', ':core:database:testDebugUnitTest')
```

### T8 · 冻结题面、复用与补全的持久准备流程

**新增：**

- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/export/RoomDetailedSolutionRepository.kt`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/DetailedSolutionMigration_70_71.kt`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/DetailedSolutionEntity.kt`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/DetailedSolutionDao.kt`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/DetailedSolutionPort.kt`。
- `app/src/main/kotlin/com/tingyun/smartmistakebook/PrepareDetailedSolutionsWorker.kt`。
- `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/export/DetailedSolutionPreparationTest.kt`。
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/export/DetailedSolutionPreparationInstrumentedTest.kt`。

**修改：** T7 新建的 `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/DetailedSolution.kt`（冻结 manifest DTO，仅引用 core:model 类型）、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePaperDraftStore.kt`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt`；`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabasePort.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt`；备份清单/恢复验证与 `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt`。

- [ ] 编写持久失败用例：缺答案仅补缺项；已有有效项零模型请求；后台重开不重复付费；修改条件失效而只重排仍可复用逐题解答；预览所见与稍后 worker 读取一致；历史版本不偷读当前答案。
- [ ] 按点击「准备答案并预览」时的状态保存不可变卷面，并保留每个导出题号到精确 revision 和最终导出内容的映射。`sourceInputSha256` 不能替代改写后 `inputSha256`；为每题计算最终题干、条件、选项与图事实的 `questionSha256`，不把备注、页边距和题序混入逐题语义身份。

```kotlin
data class FrozenExportQuestion(
    val ordinal: Int, val entryId: String, val basis: SolutionBasis,
    val document: QuestionDocument, val figures: List<StoredFigureReference>,
)
data class FrozenAnswerManifest(
    val paperDraftId: String, val inputSha256: String, val layout: MistakePdfLayout,
    val questions: List<FrozenExportQuestion>,
)
// manifest 含最终题面与图资产身份，并与已校验的 paper draft 摘要绑定；
// 私有文件缓存丢失后可重建，不拿当前题面代替已冻结内容。
// 实现以 canonical 编码进行 SHA 校验，不用字符串拼接 title 当身份。
```

- [ ] 迁移 70→71 在已列出的 entity/DAO/port 文件中建立 `detailed_solution_manifest` 与 `detailed_solution`。前表保存完整冻结 manifest JSON（含最终题面/题序/版式/资产身份）、摘要、创建时间与准备状态；不只存短期缓存路径。后表按 `revisionId + questionSha256 + 解答协议版本` 唯一。记录模型 request id、请求/文档 JSON、内容摘要、来源、核对事实、状态和时间。状态为 `QUEUED/RUNNING/READY/CANCELLED/FAILED/STALE`；文档只有完整且摘要/图资产验证通过才进入 READY。
- [ ] `PrepareDetailedSolutionsWorker` 的 WorkManager Data 只放 manifest 标识，不塞题面、图片或答案全文；从数据库读取并校验冻结 manifest，先复用，后补缺。活动 manifest 和被引用资产不能被普通缓存 TTL 清理；旧 paper draft 文件只是可重建缓存。增加“删除草稿缓存后进程重开仍能准备/交付相同卷面”的设备用例，备份/还原保留完整 manifest 内容。所有模型调用经过已有仓储与预算；手动停止立即持久化，重试不重新生成已完成项。源库当前版本向前推进时，已冻结卷面仍是明确的第 N 版，预览显示版次；卷面草稿自身被改写则使对应 manifest 和预览确认失效，不自动换成新版本。
- [ ] 准备状态逐题可见。任一题仍缺完整答案时，不能把不完整答案卷标成完整成果；题目文件仍可独立保存，答案可单独重试。重排/删除只重建整卷和预览身份，不重新付费生成未变题面的解答。
- [ ] 核对备份新表、还原完整性、资产引用及进程重启；Room 生成 `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json`（生成路径纳入本批许可）后提交 `feat(export): 持久准备和复用精确题面的详细答案`。

```powershell
Invoke-SmbGradle -TaskArguments @(':core:data:testDebugUnitTest', ':core:database:testDebugUnitTest', ':core:export:testDebugUnitTest', ':core:data:assembleDebugAndroidTest', ':app:compileLocalFirstDebugKotlin')
```

### T9 · PDF 与 Word 的完整答案文件渲染

**新增：**

- `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DetailedAnswerExport.kt`：冻结答案输入、块映射与身份。
- `core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/DetailedAnswerExportTest.kt`。
- `core/export/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/export/DetailedAnswerExportInstrumentedTest.kt`。

**修改：** `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/MistakeExportFormat.kt`（共享产物角色）、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DeterministicMistakePdfRenderer.kt`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeDocxExporter.kt`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt`。

- [ ] 先写失败用例：三步文字/长公式/多页答案实际存在于文件；答案文件不重复题面全文；题号与 manifest 一致；图角色正确；正文/格式/版式任一变更使答案缓存键变化；原题目输出 golden 不变。
- [ ] 新输入将 `FrozenAnswerManifest` 与按其基准排序的 `DetailedSolutionDocument` 列表合并，校验无缺项/重复/错版。映射成共享原生排版的标题、题号、段落、公式和允许图块，题目与答案使用不同产物角色。

```kotlin
// 此枚举放在 core:model/MistakeExportFormat.kt，domain/database 不依赖 core:export。
enum class MistakeExportArtifactRole { QUESTIONS, DETAILED_ANSWERS }
data class DetailedAnswerExportInput(
    val manifest: FrozenAnswerManifest,
    val documents: List<DetailedSolutionDocument>,
    val format: MistakeExportFormat,
    val layout: MistakePdfLayout,
)
// 新入口 prepareDetailedAnswers(input) 分别置于 MistakePdfExporter/MistakeDocxExporter；
// 复用现有字形、分页、文件发布与 reopenVerified，不用 includeSolution 开关冒充文字解析。
```

- [ ] 题目旧入口继续沿用原指纹；新答案入口以版本化 canonical 编码包含 `DETAILED_ANSWERS`、manifest SHA、答案文档/图 SHA、格式及版式。图片不从用户任意 URI 读取，只解析已授权资产并复核摘要。
- [ ] PDF 完整分页显示步骤和公式，保持题号；Word 保留现有“图形用文字说明”的真实限制，同时完整保留步骤、最终答案与公式。复用 shared block 逻辑时增加产物上下文，避免题目区域的角色过滤把答案讲解图/文字静默丢弃；不得让答案图进入题目卷。
- [ ] 真设备渲染并重开 PDF，逐页检查；解压 DOCX 检查文档 XML 含全部步骤/题号，验证损坏文件不进入成功状态。通过后提交 `feat(export): 独立渲染完整详细答案文档`。

```powershell
Invoke-SmbGradle -TaskArguments @(':core:export:testDebugUnitTest', ':core:export:assembleDebugAndroidTest')
```

### T10 · 双文件导出、预览确认、部分成功与全部入口

**新增：**

- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/MistakeExportGroupMigration_71_72.kt`。
- `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/DetailedAnswerPreviewSheet.kt`。
- `app/src/test/kotlin/com/tingyun/smartmistakebook/DetailedAnswerExportWiringTest.kt`。
- `feature/library/src/androidTest/kotlin/com/tingyun/smartmistakebook/feature/library/DetailedAnswerPreviewInstrumentedTest.kt`。

**修改：**

- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeExportRecord.kt`；`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/MistakeExportRecordEntity.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/MistakeExportRecordDao.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/ExportRecordPort.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/MistakeExportRecordContract.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabaseMappings.kt`；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/export/RoomMistakeExportRepository.kt`。
- `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportJobRunner.kt`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportFormatRouting.kt`；`app/src/main/kotlin/com/tingyun/smartmistakebook/ExportPdfWorker.kt`、`app/src/main/kotlin/com/tingyun/smartmistakebook/MistakeExportNotifications.kt`、`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt`、`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt`。
- `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeExportFormatSheet.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeExportHubRoute.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakePaperEditRoute.kt`、`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt`。
- `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt`、`feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportEditSheet.kt`、`feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationViewModel.kt`、`feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorLobbyRoute.kt`；现有格式/导出/worker 回归用例。
- 备份表与迁移矩阵：增加新 group 表及新列的读回/还原检查；group entity 和事务写口分别并入本任务列出的 MistakeExportRecordEntity.kt / MistakeExportRecordDao.kt，不另建无清单文件。

- [ ] 失败用例先覆盖：默认不带答案且不调用模型；点击准备才派发；预览未确认不可导出答案；确认只对应该份 manifest+答案 SHA；返回/取消不发起导出；单题、批量、历史、编辑后卷面、讲题导出均透传选择。
- [ ] 迁移 71→72 新建 `mistake_export_group`，存 T8 manifest id/摘要、题目/答案 export id、是否请求答案及 preview 确认 SHA；现有 export record 增加可空 group id 与默认 `QUESTIONS` 的产物角色。旧记录不改文件身份或成功状态，旧 `SINGLE/BATCH` 继续描述选题方式，不拿它兼任文件角色。Room 生成 `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/72.json`，该生成路径纳入本批许可。
- [ ] 在 `MistakeExportJobRequest` 新增 `DetailedAnswers` 分支，携带 group id、manifest SHA、答案集合 SHA 和格式。worker 的序列化、反序列化、通知、记录、恢复以及所有穷尽分支同时覆盖。旧 `Single/Batch/Edited` 请求格式与行为保持兼容；已选择答案的组一律读 T8 数据库冻结 manifest；题目侧缓存草稿丢失时从该 manifest 重建并校验，不在后台按 entry id 重新选当前版本。

```kotlin
data class AnswerPreviewApproval(
    val manifestSha256: String, val solutionsSha256: String, val format: MistakeExportFormat,
)
fun approvalMatches(
    approval: AnswerPreviewApproval?, manifestSha256: String, solutionsSha256: String, format: MistakeExportFormat,
): Boolean = approval == AnswerPreviewApproval(manifestSha256, solutionsSha256, format)
@Test fun changedSolutionNeedsAnotherPreview() {
    assertFalse(approvalMatches(AnswerPreviewApproval("m1", "s1", MistakeExportFormat.PDF), "m1", "s2", MistakeExportFormat.PDF))
}
```

- [ ] UI 的「另存一份详细答案」默认不选。准备后预览完整步骤/公式/来源和核对状态，确认保存上述摘要对，不能把 `verification` 改为 VERIFIED。文件格式或版式变化无需重新生成同题解答，但预览确认必须绑定当前格式与卷面摘要，重新显示格式限制和交付清单后确认。
- [ ] 题目和答案各自独立入队、计状态与校验文件。答案未准备好/失败时，提供“保存题目”“重试答案”；不隐式交付缺题号的半份答案。成功项不重新入队或再次生成。Hub、通知、保存与分享逐文件显示格式与角色；不以一条总“成功”遮盖另一份失败。两次系统文件保存动作分别遵循既有 SAF，不新增 ZIP/上传/公开链接。
- [ ] 增加恢复/文件过期/文件损坏/部分成功真库设备用例；配对重开两份文件核对题号与冻结版本。通过后提交 `feat(export): 接通详细答案预览与独立文件交付`。

```powershell
Invoke-SmbGradle -TaskArguments @(':core:export:testDebugUnitTest', ':core:data:testDebugUnitTest', ':core:database:testDebugUnitTest', ':feature:library:testDebugUnitTest', ':feature:tutor:testDebugUnitTest', ':app:testLocalFirstDebugUnitTest', ':app:assembleLocalFirstDebug', ':app:assembleStrictOfflineDebug')
```

## T11 · A 布局、原生交互与最终验收

**修改：** T0/T3/T6/T10 已列明的共享容器、详情、编辑、分类、导出及其直接消费者。复用现有设备测试中的完整原图、root 导航、触控目标和字体缩放检查；不新增仅钉死颜色/圆角数值的测试。

- [ ] 详情按题面→完整原图→笔记/分类组织，版本信息降低层级但可查看；主要讲解操作与编辑/导出有清楚主次。历史版本与当前版本的读写权限不变。
- [ ] 核查每个弹层关闭/返回只退出当前层；智能体建议关闭不离开编辑页、答案预览返回不丢格式与选择、分类点选不越过既有纠正确认。所有按钮状态来自真实业务状态，不来自动画计时器。
- [ ] 使用既有 `WorkspaceSheet`、`ClaudeDialog`、主题和字体，48 dp 触控目标、清楚的输入标签与焦点、可读对比度，文字随系统字体缩放；图片保持比例，不缩成用户已否定的详情缩略图。
- [ ] 过渡使用 160–220 ms 淡入/位移，遵循系统减少动态效果；键盘弹出后内容可滚动、确认操作可达；长知识路径、长答案与多图不会撑破屏幕或相互遮挡。处理中不用无意义常驻闪烁。
- [ ] 对最终源码运行下列范围检查，结果与 APK/SHA、源码版本相互对应。专用设备只使用本任务 AVD `audit_full_ui_forest_20261028` 的 `emulator-5566`，先检查设备身份与已有驱动锁；不操作其他任务的模拟器，不关闭他人设备。新增证据路径与新测试文件一样需提前弹窗授权。

```powershell
Invoke-SmbGradle -TaskArguments @(':core:model:test', ':core:domain:test', ':core:database:testDebugUnitTest', ':core:data:testDebugUnitTest', ':core:export:testDebugUnitTest', ':core:ui:testDebugUnitTest', ':feature:library:testDebugUnitTest', ':feature:tutor:testDebugUnitTest', ':app:testLocalFirstDebugUnitTest', ':core:database:lintDebug', ':core:data:lintDebug', ':core:export:lintDebug', ':core:ui:lintDebug', ':feature:library:lintDebug', ':feature:tutor:lintDebug', ':app:lintLocalFirstDebug', ':app:assembleLocalFirstDebug', ':app:assembleStrictOfflineDebug', ':app:assembleLocalFirstDebugAndroidTest', ':feature:library:assembleDebugAndroidTest', ':core:data:assembleDebugAndroidTest', ':core:database:assembleDebugAndroidTest', ':core:export:assembleDebugAndroidTest')
```

设备用例至少包括新增的目录搜索、跨科写入、真实改图修订、详解准备、PDF/Word 答案、预览/双文件，以及既有完整原图和导出取消回归。执行类名均采用各任务中新建文件的包名+类名；用 `adb -s emulator-5566 shell am instrument -w -e class` 点名，包名读取本轮 APK 的 instrumentation 信息，不猜测变体 applicationId。不重复累计复跑用例计数。

- [ ] 在 360/390 dp、1.6 字体比例、键盘和减少动态效果条件下检查原生截图/交互。真实 Provider 与设备 JS 准入未执行时，分别列为未验证，不以假服务或浏览器截图替代。当前方案选择 JS 但运行库未准入时须显示不可用，不能宣称两种绘图方法已全部完成。
- [ ] 逐条对照下一节覆盖矩阵，仅将具备本轮证据的任务勾选；汇报每个子项目完成/未完成、迁移/备份结果与剩余风险。最终提交 `feat(ui): 完成错题本阅读优先内页与交互整合`；是否推送遵循用户届时指令。

## 3. 需求覆盖与发布门槛

| 必须满足的行为 | 负责任务 | 必须出现的证据 |
| --- | --- | --- |
| A 布局与完整原图 | T0、T11 | 最终 APK 原生截图与完整图回归 |
| 文字/公式/图像的智能体入口 | T4–T6 | 真实请求、类型化结果、保存后重开 |
| 图像不是只改 alt 说明 | T5、T6 | 资产 SHA 改变且新版本引用正确 |
| JS/图像模型同规则判定 | T4、T5 | 共享 policy、显式 method、不可用与零旁路测试；JS 发布另需既有专项准入 |
| 同一操作重试与不同指令隔离 | T4、T5 | 相同 operation 幂等、不同指令不同键 |
| 全库与长别名/句子/教学段落可搜索 | T1、T3 | 超过 256 条真库、尾部字段与多页测试 |
| 关键词模块、直接选择、跨科不改主学科 | T1–T3 | 目录界面、真库绑定与重开断言 |
| 先复用，有缺失才生成 | T7、T8 | 有效项零调用，缺失项有持久任务 |
| 导出改题后不沿用旧答案 | T7、T8 | 最终题面语义摘要和不复用测试 |
| 重排不浪费生成 | T8 | 逐题复用而整卷/预览身份改变 |
| 预览不是正确性验证 | T7、T10 | 预览后仍保留原核对状态，无掌握写入 |
| PDF/Word 都有完整文字详解 | T9 | 真文件逐页/XML 检查与损坏重开拒绝 |
| 两份文件、部分成功、独立重试 | T10 | 各自状态/校验/保存及中断恢复 |
| 单题/批量/历史/卷面副本/讲题入口一致 | T8、T10 | 逐入口参数及最终文件对应测试 |
| 迁移/备份/还原保留旧事实 | T5、T8、T10 | 69→72 及旧版本矩阵、schema、资产引用验证 |
| 正式 UI 可用且动画克制 | T11 | 窄屏、大字体、键盘、返回与减少动画记录 |

未完成任何一行，不将三个子项目一起宣称完成。设计原型中的固定数据、16 项浏览器交互检查、旧提交的单元/设备测试计数均不是未来生产实现的通过证据。

## 4. 文档自检与执行交接

执行计划时逐项勾选，任务完成须有本轮实际命令输出和文件证据。纯视觉调整复用已有检查；新增业务协议/版本写入/持久作业/导出文件必须运行本计划列出的有意义测试。

本计划当前状态：**实施中；T0、T1、T2、T3、T4 已验证，下一任务为 T5；其余任务未完成**。推荐使用 executing-plans 在当前工作区按 T0→T1–T3→T4–T6→T7–T10→T11 内联执行，分段审阅。用户明确选择分工时才切换子代理驱动；不能因为计划头部列出该选项就擅自启动代理。


### T0 本轮证据

2026-10-31：`core:ui:testDebugUnitTest`、`feature:library:testDebugUnitTest`、`feature:library:compileDebugKotlin`、`app:compileLocalFirstDebugKotlin` 一次构建通过，`BUILD SUCCESSFUL in 59s`。测试 XML 的统计与本轮命令摘要位于 `.jez/artifacts/library-interior-implementation-2026-10-31/t0-verification.json`。已有编译告警仍保留；此记录不代表原生视觉验收或三个新增功能完成。


### T1 初始阶段记录（由下面完成记录取代）

已新增计划列明的 KnowledgeCatalog.kt、KnowledgeCatalogRepository.kt 和 KnowledgeCatalogKeywordsTest.kt。关键词 5 项红灯记录确认缺失函数，随后绿灯通过；core:model 与 core:domain 全量回归通过，统计见同一实施证据目录的 progress.json。这是初始阶段的范围记录；目录查询的最终结果见下面完成记录，分类原生 UI 由 T3 负责。


### T1 完成记录

2026-10-31：目录查询与应用级仓储已实现，真库设备用例 7 项、仓储用例 5 项、关键词用例 5 项通过。最终数据库测试包与数据库/数据层回归构建为 `BUILD SUCCESSFUL in 14s`。core:model 511、core:domain 713、core:database 211 项均无失败/错误；core:data 1167 项中 1 项 staging benchmark 跳过，其他无失败/错误，不将跳过项计为通过。

10,000 节点 / 50,000 教学段落的全字段查询正确找到最后一条材料，两轮耗时为 719 ms 和 512 ms（本机专用 API 34 模拟器，不外推为所有设备性能）。分页 keyset 按匹配数、学科、名称、节点 id 排序；目录路径在每页批量追溯用于界面分组，避免对全库递归生成路径。目录变化后游标失效，输入取消不会污染后续查询。

本任务还补齐了已有消息附图读取 DTO 的角色/基准版本字段，并同步已有 FTS 测试中漏掉的收藏条件 SQL 副本与绑定；保留全部断言和旧 JOIN 对照，不改生产收藏过滤。真实目录查询不使用这些 FTS 副本或旧召回索引。

证据位于 `.jez/artifacts/library-interior-implementation-2026-10-31/t1-verification.json` 及同目录构建/设备日志；已归档实际测试 APK 和 SHA。分类界面尚未接入此仓储，不能将 T1 等同于 T1–T3 全部完成。

#### T5 / T11 必须关闭的已知失败

额外运行旧 `TutorSessionVisualRoleDatabaseInstrumentedTest` 时 4 项中 2 项失败：`savingCopiesAssistantFigureRolesFromTheToolTrace` 的角色仍为 null，`deletingTheTutorConversationKeepsTheCommittedRevisionLinks` 缺少已生成题面图。原失败保留在 `t1-native-catalog.log`。T5 接线时必须调查写入/提交链，并以可信生成结果和规范资产身份写入角色；不得仅信任模型自报 toolTrace 的角色字符串，也不得给未知旧图乱填角色。修复后重跑这两项，T11 最终验收不得遗漏。


### T2 完成记录（2026-11-01）

- 人工目录选择以 node id + taxonomy 保存，跨科同名节点保留各自身份；章节来自真实父节点，题目主学科不改。旧模型建议不能覆盖人工纠正。
- 事务内重新核对节点有效状态、审核、taxonomy、章节与当前在册版本；目录变化或题目换版整笔拒绝。重复命令保持幂等。
- 32 项选择预算包含旧字段与目录节点；目录自动派生的章节标签不占第二份选择名额，持久分类最多 64 行；旧自动路径原限制保留。
- `:core:domain:test :core:database:testDebugUnitTest :core:data:testDebugUnitTest :core:data:assembleDebugAndroidTest` 成功；分类真库最终 **16/16** 通过，覆盖跨科、同名、重开、幂等、目录退役/待审/版本不符、事务中目录变化和真实题目换版。
- 测试包暴露的两处旧拆分夹具空类型编译缺口仅补 `requireNotNull`；新分类夹具补合法已确认题面，未放宽正式读取校验。
- 证据：`.jez/artifacts/library-interior-implementation-2026-10-31/t2-verification.json`。T3 原生面板接线正在进行；T5/T11 两项图像角色旧失败仍未关闭。


### T3 完成记录（2026-11-01）

- 原生全库面板接到应用级真实目录仓储，输入防抖与查询身份隔离；目录变化重置游标，已选身份保留；翻页去重并拒绝晚到旧页。检索结果按真实学科/章节分组，新查询回到顶部。
- 面板展示句子/关键词检索、关键词模块、匹配片段和已选项；底部完成固定，关闭丢弃本次面板选择。完成返回纠正草稿，保存才提交；无在线分类任务时同样可手动关联。重开读回真实 node id、taxonomy 和学科，不降格成同名字符串。
- `:feature:library:testDebugUnitTest :feature:library:assembleDebugAndroidTest :app:assembleLocalFirstDebug` 成功；最后补充的身份保护回归后错题本 JVM **80/80**，目录 VM **6/6**（计入 80），数据层整理相关回归 **36/36**。
- 原生 `MistakeOrganizationInstrumentedTest` 最终 **18/18**。新增真实 Room 场景在 300+ 节点中搜索第 299 项及物理同名项，完成后无写入、保存后精确两条关联，重开学科正确，题目仍为数学。另验面板关闭丢弃选择和旧交互兼容。
- 已实际查看设备截图 `t3-catalog-picker-verified.png`；整机深色/大字体/无障碍与全部内页验收仍归 T11，不以该截图替代全量验收。
- 证据：`.jez/artifacts/library-interior-implementation-2026-10-31/t3-verification.json`。下一任务 T4；全量实施仍未完成。


### T4 完成记录（2026-11-01）

- 注册 `MISTAKE_EDIT` 与严格的宿主别名修改协议；基准绑定 entry/revision/draft SHA，逻辑键使用 UTF-8 长度前缀并包含修改要求及源图 SHA。模型不能新增题目、块或选项身份，不能更改宿主控制的选项交互标记。
- 新已采用图引用在空值时不序列化；全量模型 golden/旧序列化回归通过。图建议只使用统一 `FigureGenerationProposal`，JS 源码不能进入普通文本；实际执行与资产准入留给 T5。
- `NO_CHANGE/NEED_SOURCE/UNAVAILABLE` 保留原因且没有可采用差异；T5 状态词汇与领域类型同步。普通数学不等式可正常保留，图片链接、URI 和可执行标签拒绝。
- 合成编辑 patch 纳入现有结构化探测且复用实际解析器，旧能力记录默认不可用；能力位持久化、重开和换密钥失效通过原生测试。设置页区分讲解/图片识别/智能修改能力。
- 原图经过同一受限资产与请求编码链，出网前核对冻结 SHA/尺寸/体积；MockWebServer 验真实图片请求、额外字段拒绝、截断状态以及缺能力和取消均零 HTTP。新任务归入 ISSUE_BOOK 备份类别，并同步备份机器块及选择性备份回归。
- 最终命令 `:core:model:test :core:domain:test :core:database:testDebugUnitTest :core:data:testDebugUnitTest :core:data:assembleDebugAndroidTest :app:compileLocalFirstDebugKotlin` 成功：模型 **519**、领域 **714**、数据库 **211** 全通过；数据层 **1177** 无失败/错误，其中 **1 项既有 staging 基准跳过**。原生配置 **2/2** 通过。新模型专测 8 项、新协议 5 项计入上述总数。
- 证据：`.jez/artifacts/library-interior-implementation-2026-10-31/t4-verification.json`，测试 APK 已归档。下一任务为 T5；智能体编辑 UI/持久作业/详细答案仍未完成，两项旧图像角色失败仍待 T5/T11 关闭。
