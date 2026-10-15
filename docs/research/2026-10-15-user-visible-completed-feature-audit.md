# 已交付流程中的用户可感知缺陷复核（2026-10-15）

> 补修进展（2026-10-15）：V-01～V-06 已实施补修，并补上 AUD-06 的缓存输入缺口。具体范围、回归结果与未验证边界见 [修复记录](2026-10-15-user-visible-fixes.md)。下文保留审计时的证据和结论，作为历史基线。

本轮继续检查“已经提供给用户使用，但仍有明显缺陷”的功能。结论为 **5 个可确认的新问题，另有 1 个待设备复现的恢复风险**；同时复核了 7 个已有登记的未收口问题。编号 V-01～V-06 仅用于本报告，不替代主线 AUD 编号。

本轮没有修改业务代码、测试源码或原有计划台账。新增本报告与 `.artifacts/user-visible-audit-2026-10-15/` 下的核验材料。共享工作区中的其他改动不属于本次审计交付。

## 1. 基线、范围与证据口径

- HEAD：`a15aebb715ca8bcb3c4fed599652fbbf8c146e86`，叠加当前共享工作区改动；不能将结果仅归因于该提交。
- 本轮重新构建并覆盖安装 `localFirstDebug`，保留原有应用数据。APK SHA-256：`DEF0D2CDFE3CC039688E95CB219614781A4EBD12C7A490B9DEFDD14BBC3D4286`。
- 设备：`emulator-5554`，Android 14 / API 34。核验源码的摘要和工作区状态见 `baseline.json`、`working-tree.txt`。
- **设备复现**表示本轮真实操作安装包并保存 UI 树；**生产代码探针**表示调用本轮编译出的实际生产类，使用受控输入；**源码确认**不等于设备复现；恢复风险单独列出。
- P1：主流程卡住、应有题面或已保存内容缺失，优先补修。P2：重要但有绕行方式的显示、交付或恢复问题。

| 已提供的流程 | 本轮核查结果 | 证据边界 |
|---|---|---|
| 拍照 / 整卷切分 / 确认录入 | 多题提前标记已录入的问题仍在；未把尚未完成的 4C 自动化质量当作新缺陷 | 当前入口与导航源码 |
| 错题本搜索 / 筛选 / 分页 / 归档后的刷新 | 新增 V-01、V-02 | 搜索设备复现；分页生产代码探针 |
| 题目详情 / 备注 / 历史版本 | 核对备注进入导出的交接；未新增历史版本缺陷 | 源码与既有模块测试 |
| 讲题 / 重试 / 选择提交 / 判定结算 | KD-35/36 已修，不重复报；KD-37/38 仍未收口 | 当前源码与已有设备记录分开引用 |
| 原题复习 / 知识点小测 | 新增 V-04；AUD-01/02 仍在 | 原题复习设备复现；小测源码复核 |
| 单题 / 批量 PDF / 版式 / 答案区 | 新增 V-03、V-06；AUD-06 仍在 | 实际导出输入和分页器探针 |
| 备份 / 校验 / 恢复 / 页面重建 | AUD-05 仍在；新增 V-05 风险 | 源码复核，未操作真实恢复或删除 |
| 提醒 / 设置 / 学情入口 | 核查刷新、提醒协调与既有测试，本轮未形成新的高置信问题 | 双构建类型单元测试；不代表全机型验收 |

## 2. 新增的 5 个可确认问题

### V-01 · P1 / 高置信：搜索无结果会把搜索框和清除入口一起藏掉

**触发与后果。** 错题本原本有题，输入不匹配的词后，页面变成“还没有错题 / 录入第一道错题”。搜索框、筛选和清除入口消失。学生容易以为题目丢了，也无法在当前页改词。切到其他栏目再回来仍可保留这一状态。

**本轮证据。** 更新前安装包观察到一次；本轮新构建覆盖安装后，以 `zzz` 再次复现。进入前 UI 显示 `1 道题`；进入后只剩空库文案、录入和导出成果。更新前的切栏往返也保存了 UI 树。最后重建 Activity 导航后确认原题仍在，已将页面恢复到有搜索框和 `1 道题` 的状态。

**原因位置。**

- [LibraryRoute.kt:94](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryRoute.kt#L94)：把带当前搜索 / 筛选条件的 `uiState.totalCount` 当成全库题数。
- [LibraryModels.kt:194](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryModels.kt#L194)：`totalMistakeCount == 0` 优先判为空库。
- [LibraryRoute.kt:153](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryRoute.kt#L153)、`:192`：空库隐藏搜索筛选，且 `canClear=false`。

**最小修复方向。** 全库数量与当前结果数量分开；有搜索 / 筛选时保留编辑和清除出口。使用生产仓库验证零结果，不能只给纯函数传两个正确数字。

材料：`ui-library-new-apk.xml`、`ui-search-zzz.xml`、`search-empty-current-build.png`、`ui-search-return.xml`、`ui-cleanup.xml`。

### V-02 · P1 / 高置信：分页包装丢失刷新通知，新增或归档后列表可能仍显示旧内容

**触发与后果。** 保持错题本的筛选条件不变，录入新题、归档或恢复旧题后返回。数据库即使已变化，页面当前分页对象也收不到失效信号，可能继续显示旧列表。题数和筛选分组也只在主动查询时计算，没有订阅变化。

**生产代码探针。** 使用真实的 database、data 两层 `MappingPagingSource` 包装一个受控分页源：底层 `invalidate()` 后 `raw.invalid=true`，两层包装仍为 `false`。底层页声明剩余 99 项，包装后的 `itemsAfter=-2147483648`（`COUNT_UNDEFINED`）。

**原因位置。**

- [core/database/MappingPagingSource.kt:6](../../core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/MappingPagingSource.kt#L6) 与 [core/data/MappingPagingSource.kt:6](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/library/MappingPagingSource.kt#L6)：未转交 delegate 的失效通知，重建 Page 时丢掉 `itemsBefore/itemsAfter`。
- [RoomLibrarySearchStore.kt:572](../../core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomLibrarySearchStore.kt#L572)：搜索路径的 `RefreshingPagingSource` 同样没有失效转交。
- [LibraryViewModel.kt:112](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryViewModel.kt#L112)、`:260`：同一仓库再次绑定直接返回，计数与 facets 为一次性读取；查询间还没有取消旧任务或校验结果所属查询，存在旧结果覆盖新状态的附加风险。

**另一个用户可见后果。** `LibraryRoute.kt:126/240` 用分页 `itemCount` 写“导出当前 N 道”，实际收集导出项则按查询拉全量（`LibraryViewModel.kt:297`）。丢失总量信息后，分页只载入一部分时，按钮数目可能小于实际导出数；实际超过上限时，也可能先显示较小的 N，点击后却告知超过上限。初始加载配置是 60 条，具体在屏条数还受预取影响，不能固定声称永远只显示 60。

**最小修复方向。** 贯通包装层失效通知与页计数；列表、题数、facets 使用相同查询版本并响应数据变化；导出文案使用实际候选总量。验收需覆盖当前页面保持筛选、数据变化后自动更新，及超过一页的真实仓库场景。

材料：`PagingAuditProbe.java`、`paging-probe.log`。未在设备上执行新增 / 归档实验，未修改现有题目来制造样本。

### V-03 · P1 / 高置信：批量导出漏掉每题的备注和净化图

**触发与后果。** 学生给题目写了备注、已有净化图，在智能体导出设置里选择“含备注区”后导出。单题输入能携带这些内容，但走批量合并后就没有了。批量路径即使只选一题也会触发。对于依赖净化图补充的图题，导出的题面可能不完整。

**生产代码探针输出。**

```text
single.note=NOTE_ONE
single.cleanImage=file:///audit-clean.png
batch.note=null
batch.cleanImage=null
batch.renderedNote=false
```

探针使用相同的正式题面和合法版式，直接调用实际 eligibility / batch eligibility / page planner；没有复制它们的实现。图像核验在输入交接层，未生成像素对照 PDF。

**原因位置。** [MistakePdfBatchEligibility.kt:65](../../core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfBatchEligibility.kt#L65) 只合并 `input.blocks`，`:92` 重建总输入时没有承接每题 `userNote` 和 `cleanImageLocalUri`。对应的单题字段在 [MistakePdfEligibility.kt:152](../../core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt#L152)。应用批量入队在 `SmartMistakeBookApplication.kt:568`。

**最小修复方向。** 批量输入保留“每题题面 + 图片 + 备注”的归属，分页器逐题消费。仅把某一题的图或备注塞进总输入字段，无法解决多题的归属问题。

材料：`ExportAuditProbe.java`、`export-probe.log`。现有净化图像素测试只测单题，不能证明批量链路完成。

### V-04 · P1 / 高置信：图题进入原题复习后，只能读图注，无法打开原图

**触发与后果。** 录入化学装置、几何或结构式题，进入今日复习“复做你保存的原题”。此页只渲染文字，没有原图、放大入口或结构化图形。题干要求“装置如下”“已知结构式”时，学生无法核对必要图形；图注不足以替代所有视觉条件。

**设备证据。** 本轮新包打开已有 DCDMH 制备与含量测定题。页面显示甲乙丙丁装置的文字描述，及“原图以化学结构式表示，难以可靠重建为 LaTeX，故保留为图注”；页面没有读取原图的控件。题目仍要求按原装置作答。

同一页面截图还可见标题重复、正文直接露出 `# 16.`，以及摩尔质量 / 温度里的 `\mathrmg`、`circ\mathrmC` 等残留。该样本的阅读退化已观察到；本轮未进一步区分残留究竟来自模型转写还是文字降级转换，不将它外推为所有公式都无法渲染。

**原因位置。** [CapturedReviewSessionScreen.kt:128](../../feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/CapturedReviewSessionScreen.kt#L128) 只调用 `SafeMarkdownText(entry.problemMarkdown)`，整个屏幕没有资产读取 / 图片组件。上游 [CapturedQuestionDocument.kt:249](../../core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/CapturedQuestionDocument.kt#L249) 的图形 Markdown 投影本身也只有替代文字。

**最小修复方向。** 用精确版本的正式题面与原图资产展示复习题，至少提供原图展开 / 放大；模型不能重建的图形必须有原图兜底。保留没有模型时不伪造判定的既有产品裁定。

材料：`ui-review-loaded.xml`、`review-figure.png`。本轮只进入复习，没有提交答案、调用真实模型或制造学习事实。

### V-06 · P2 / 高置信：选择“答案卷 / 含答案区”后可以得到无答案且无提示的文件

**触发与后果。** 导出没有已选项答案的数据，例如普通拍照录入题，选择“答案卷 / 含答案区”。文件生成成功，却没有答案区，入口和结果也不说明哪些题缺答案。

**生产代码探针输出。** 对合法的正式段落题面指定 `with_answers` 与 `includeAnswer=true` 后，实际分页器返回：

```text
answerTemplate.answerHeading=false
answerTemplate.skippedNoticeCount=0
```

**原因位置。** [MistakePdfPagePlanner.kt:288](../../core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt#L288) 只从 ChoiceGroup 的 `selected` 项取内容，无可用项时 `:293` 直接返回。[MistakePdfLayout.kt:124](../../core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/MistakePdfLayout.kt#L124) 的跳过提示仅登记解析区；`TutorExportSheet.kt:294/378` 仍展示“答案卷 / 含答案区”。

**最小修复方向。** 在导出前按实际内容显示答案可用情况，必要时禁用选项或明确缺失题数；产物结果同步提示。不要求为了满足勾选而自动编造答案，也不在这轮更改“已有已选项”的既有数据语义。

材料：`ExportAuditProbe.java`、`export-probe.log`。未操作真实分享或打印。

## 3. 新增的恢复风险（与已复现问题分开）

### V-05 · P2 / 中置信：存储页可能恢复成永久“正在处理”

**触发条件。** 正在创建 / 校验 / 恢复较大的备份，Activity 已保存状态，随后进程被系统回收；再次恢复该页面。

**源码证据。** [SecondaryScreens.kt:638](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt#L638) 用 `rememberSaveable` 保存 `operationBusy`。实际操作由该页面 `rememberCoroutineScope` 启动（例如 `:661`、`:689`、`:718`），不是持久任务，页面恢复时没有任务身份、查询或重新协调逻辑。若恢复出 `true`，原协程已经不存在，原来的 `finally` 也不会在新进程执行；`:831/839/853` 等按钮全部被禁用。

**用户后果。** 该页面可能一直显示“正在处理”，无法继续备份、校验或恢复，需要离开并重进页面来重新建立状态。不能把它当成后台操作仍在执行。

**最小修复方向。** 短操作按页面重建复位并提示中断；需续跑的长操作保存任务身份并由持久执行者协调。恢复操作还应统一处理数据库关闭后必须重启的状态。

**未验证。** 本轮未对正在备份 / 恢复的应用杀进程；不把静态状态机推断写成设备复现。必须以真实大备份的“开始 → 后台保存状态 → 回收进程 → 回到同页”实验确认。

## 4. 已登记、当前仍未收口的 7 项

这些不是本轮新增，不能重复计算为新发现；已回读当前关键实现。

| 既有编号 | 学生会感知到的后果 | 当前复核证据 / 边界 |
|---|---|---|
| AUD-01 | 同一知识点隔日再次小测、再次选同一选项，可能不能提交，反复重试无效 | `KnowledgeReviewQuizLoader.kt:92` 仍只按 KC 生成 itemId；`KnowledgeReviewSessionScreen.kt:260` 仍由 item + choice 派生提交身份；`ChatEvidenceDao.kt:68` 仍重复构造 outbox。当前源码确认，未在设备上推进时钟复现第二次小测 |
| AUD-02 | 小测选好答案后中断，恢复时可能换了题，却保留旧选择，还会再发一次生成请求 | `KnowledgeReviewSessionViewModel.kt:72/78/273`：题面只在内存，选择保留，恢复回 IDLE；`KnowledgeReviewQuizLoader.kt:72` 每次新 UUID，不读回旧出题任务。当前源码确认 |
| AUD-03 | 勾选多题后只进入第一题保存，其余却已被标为“已录入” | `SplitImportReviewRoute.kt:201/207` 先批量标 SAVED 再只打开 firstDraft；`SmartMistakeBookRoot.kt:729` 保存后去详情，没有逐题续接。当前源码确认 |
| AUD-05 | 并发录入 / 生图时，备份可能少题图，却通过文件清单校验 | `AndroidBackupRepository.kt:103/113` 仍先列资产后快照 DB，题数在 `:119` 读实时库；现有校验没有 DB 引用到资产的完整性闭合。当前源码确认，未执行破坏性恢复或声称已观察到丢图 |
| AUD-06 | 修改备注后重新导出，仍可能拿到旧备注版本；净化图变化同类 | 本轮实际生产代码探针确认 `NOTE_ONE → NOTE_TWO` 时 `inputSha256` 不变；`MistakePdfExporter.kt:120` 优先找已有缓存。图像变更分支本轮仅源码复核 |
| KD-37 / AUD-23 | 判定结算部分落库后失败，再点“重新判定”可能永远失败 | `TutorJudgedReviewSettler.kt:127/140/152/153` 仍是稳定快照 ID + 新时间戳、两段事务。保留原登记的条件性、源码级证据，不称每次结算都会失败 |
| KD-38 / AUD-24 | 讲题动作标签超过 32 字符时，可能整轮 INVALID_RESPONSE，需要重新尝试 | 当前 `TutorTasks.kt` 预算抛错机制仍在；真实模型触发频率仍未测，不能沿用历史“连续四次失败”推成该原因的频率 |

原登记详见 [主线 §7](../REFACTOR-MASTER-LINE.md) 和 [known-defects](../known-defects.md)。KD-35 重试崩溃、KD-36 旧选择提交闭包 / 结算不可见的既有修复不作为新增发现；新增结算失败恢复缺口仍按 KD-37 处理。

## 5. 本轮验证及其限度

### 模块测试与组装

```powershell
.\gradlew.bat :feature:library:testDebugUnitTest :feature:review:testDebugUnitTest :core:export:testDebugUnitTest :app:testLocalFirstDebugUnitTest :app:testStrictOfflineDebugUnitTest :app:assembleLocalFirstDebug --rerun-tasks --console=plain
```

`BUILD SUCCESSFUL in 2m 15s`，254 个任务实际执行。读取本轮 XML 结果：

| 模块 / 变体 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| feature:library | 39 | 0 | 0 | 0 |
| feature:review | 39 | 0 | 0 | 0 |
| core:export | 63 | 0 | 0 | 0 |
| app localFirstDebug | 51 | 0 | 0 | 0 |
| app strictOfflineDebug | 52 | 0 | 0 | 0 |
| 合计 | **244** | **0** | **0** | **0** |

首次命令使用了歧义任务名 `:app:testDebugUnitTest`，Gradle 拒绝执行；已改用两个明确变体重跑，不把首次失败当成产品缺陷。

### 探针与设备核验

- `ExportAuditProbe.java` 通过 `javac` 编译并调用当前生产导出类；确认批量丢字段、无答案静默跳过、备注变化缓存指纹不变。文本宽度使用受控测量，不替代 Android PDF 像素验收。
- `PagingAuditProbe.java` 通过 `javac` 编译并调用实际两层包装；确认失效与页计数信息丢失。受控底层源，不等同于完成 CRUD 的设备端到端测试。
- 当前 APK 在模拟器复现 V-01、观察 V-04；保存 UI XML 和截图。测试后恢复正常错题本页面，确认原有 1 道题仍可见。进入复习创建了正常的进行中会话，未作答或写入学习判定。
- 244 项测试全绿不能为上述缺口背书。例如 Library 的纯函数测试正确地区分空库 / 搜索空结果，但真实仓库入口传错全库数量；批量导出入口 UI 测试使用内存 entries，未覆盖生产分页；净化图像素测试只测单题；小测 UI 注入固定题面，未覆盖实际 loader 的进程恢复。

材料统一位于 `.artifacts/user-visible-audit-2026-10-15/`：`unit-verification.log`、`unit-results.json`、`export-probe.log`、`paging-probe.log`、`baseline.json`、探针源码、UI 树和截图。探针运行时 classpath 由 `audit-classpath.gradle` 从本轮 Gradle 测试任务读取。

### 未验范围与建议顺序

本轮覆盖主要已提供入口的实现交接，不是全设备、全服务商的穷举验收。没有做真实云模型质量重评、API 23 / 低内存设备全链、进程回收恢复实验、并发备份恢复、真实分享和打印，也没有重新跑全仓 Lint、工具脚本或发布门。

建议先修 **V-01 + V-02**（正常使用错题本就能遇到），接着修 **AUD-01/02/03、V-04、V-03、AUD-05/06、KD-37**（题目、记录和交付完整性），然后处理 **V-06、KD-38** 并复现 **V-05**。这些已有链路的补修不应等待新的功能阶段。

**Completion Gate PASSED（本轮审计交付）**：报告、当前构建 / 测试输出、生产代码探针与设备材料已落盘。各缺陷的修复验收仍待执行，风险项明确保留未验证状态。
