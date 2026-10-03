# 阶段 4B · 智能体生图与文档导出 完成记录 · 2026-10-03/04

> 依据：主线 4B 行（`docs/REFACTOR-MASTER-LINE.md`）、phasing 阶段 4B（依赖 2、4A）、
> 设计契约 `docs/research/2026-09-28-imagegen-and-export-design.md`（A1–A6 + B1–B6）、
> 阶段计划 `docs/research/2026-10-03-stage4b-plan.md`（提交 `87e97270`）、4A 完成记录
> （`docs/research/2026-10-03-stage4a-completion-record.md`，tip `2dccc574`）。
> **用户 2026-10-03 四项裁定**：① B3=模型只提议版式参数、可按用户要求给出候选题（本地留痕）、
> 学生在候选中勾选（**payload 不含题 id**）；② AI 标识**只做隐式元数据**（显式角标不做）；
> ③ 分发=**系统分享面板**（不引微信/QQ SDK）；④ **不新增应用内预览**（打印=系统打印对话框，自带预览 + 另存为 PDF）。
> 实施方式：多 Agent 协作；每批「实施 → 正式门 → 独立复核（新 Agent、只读、对抗性）→ 修复轮 → 提交」。
> **四批均已提交、未 push**（tip `c11193a8`）；本完成记录、主线 4B 行、台账 §3.18 由协调方核验后提交。

## 1. 交付与提交（四批）

| 批 | 提交 | 内容 | 版本面 |
|---|---|---|---|
| 批 1 | `7a44bf40` | **A1** 第 8 枚 `GENERATE_FIGURE` 全程接入（枚举/描述/追踪/门/授权/执行/协议两路由/Respond 名单 8 处触点 + `MAX_TOOL_DECLARATIONS` 7→8 + 3 处测试硬断言同步）；**A3** 图片门单源（`resolveImageCredential` 唯一实现 + `vault.resolve`）；**A5** image-mcp-server README 标注「仅开发用」；**A6** 大厅 `attachedImages` 死分支清理（wire key 移除 + 指纹 strip；Respond/Plan 降级为兼容字段） | 提示词 `tutor-plan-v16-figure-no-preview` / `tutor-respond-v23-figure-no-preview` / `tutor-lobby-v14-figure-no-preview`；零 schema；`LearningCoreVersions` 不动。**42 files changed** |
| 批 2 | `92764af2` | **A2** 生成图持久化/幂等（`figure-<sha256 hex>` 幂等 id 兼作规范资产 id 与文件名；canonical 行 + 引用注册——题重绘 `problem_revision_source_asset` role=CLEAN_IMAGE、会话图 `tutor_message_source_asset`；UI 从持久引用重建，旋转/进程重建不重复出网）；**A4** 三态（GENERATING/FAILED(重试走幂等键)/READY）与记账（次数/时间/模型/用途）；**隐式 AI 标识**（PNG eXIf / JPEG EXIF） | 提示词 `tutor-plan-v18-figure-conditional` / `tutor-respond-v25-figure-conditional` / `tutor-lobby-v16-figure-conditional`；零 schema。**38 files changed** |
| 批 3 | `d2317f5d` | **B1** `MistakePdfLayout`（参数模型 + `validate()` + `DEFAULT` + 进 `exportFingerprint`）；**B2** 渲染器参数化 + 练习卷（作答空白区、答案/解析独立成区、`includeSolution` 无数据源 `UNSUPPORTED_LAYOUT_FEATURE` fail-closed 跳过、双栏超半栏缩半栏）；**B4** 三链路回归（预览 0 页尺寸 / 打印字节=prepared 逐字节 / 部分页重渲染）+ 金样对拍机制（`PreparedPdfPrintWriter` 为逐字搬迁的测试 seam）；**B5** Typst 研究（结论：**不立项**）；**B6** 干净图入导出核实（零代码改动 + 补两用例） | 提示词不动；零 schema。**15 files changed** |
| 批 4 | `c11193a8` | **B3** `START_EXPORT` payload 白名单首次落地（键恰好 `{templateId, layout}`，缺字段/多字段/非枚举/越界参数拒）+ 确认卡（模板名与关键参数）+ 导出 sheet（预填模型提议值可改；候选题=本轮检索/工具结果本地留痕，学生勾选；可库内再搜）+ 后台导出接线（`ExportPdfWorker` 带 layout）+ skipped 单源（sheet / 完成通知同读 `unsupportedRequestedFeatures`） | 提示词 `tutor-lobby-v17-export-layout-proposal`（plan v18 / respond v25 不动）；零 schema；`MistakePdfLayout` **core:export → core:model** 迁移（R079）。**47 files changed（48 路径：3 A + 43 M + 1 R079 按新旧两名计）** |

- 提示词实际值经逐提交 `git show <commit>:core/model/.../ModelEgress.kt` 与工作树 `ModelEgress.kt:53/63/79` 核对：**现行 plan `tutor-plan-v18-figure-conditional` / respond `tutor-respond-v25-figure-conditional` / lobby `tutor-lobby-v17-export-layout-proposal`**，与计划口径一致（批 3 不动提示词）。
- 四批合计 **142 文件次**（同一文件跨批重复计数；批 4 内 `MistakePdfLayout.kt` 为 R079 重命名）。

## 2. 退出门逐条（设计契约原文）

| 门 | 结论 | 证据 |
|---|---|---|
| **A1–A6、B1–B4、B6 全部用例绿** | ✅ | A/B 逐条落证见 §3；批级正式门与 4B 综合门数字见 §4；B5 为研究交付、不进本阶段门禁 |
| **真机走查一条完整链路：智能体生成一张图 → 图持久化 → 学生说「导出成两栏练习卷，不含答案」→ START_EXPORT 确认卡 → 参数化渲染 → 预览/打印字节一致** | ⚠️ **未能完整走通（UNVERIFIED）** | 模拟器**无模型凭据**：生图段不可达（无模型 → 无法在库内造题、无法触发生图）；已实测段与仪器化覆盖见 §4.3。**如实登记为 UNVERIFIED**，不得据本记录声称该门已通过 |
| **B5 研究交付（不进本阶段门禁）** | ✅ | `docs/research/typst-android-feasibility.md` 落盘；结论门判定 **不立项**（见 §3 B5、§7.3） |

## 3. A1–A6 / B1–B6 逐条落证

| 条目 | 落点 / 实现 | 证据 |
|---|---|---|
| **A1** 第 8 枚 GENERATE_FIGURE | `TutorToolName.GENERATE_FIGURE`（`core/model/.../TutorToolLoop.kt:32`）；`MAX_TOOL_DECLARATIONS = 8`（同文件 `:650`）；参数 `{kind: REDRAW_PROBLEM\|GENERATE_PROCESS, description ≤200}`，REDRAW 源图自动取自当前题规范资产（绝不接受模型供图），结果只回 id+状态；两路由 schema/解析同源；执行器把生成器/clean-redraw 穿 app→repository→runner 三层注入 | 批 1（`7a44bf40`）；计划点名的 3 处硬断言 7→8 随批更新（`TutorModelTaskPolicyTest` / `RoomModelTaskToolLoopInstrumentedTest` / `TutorToolCarrierValidationTest`，均在本批触及文件内）；批 1 正式门 core:data 工具环仪器化 **12/0/0** |
| **A2** 生成图持久化/幂等 | 幂等 id `figure-<sha256 hex>`（`core/data/.../TutorFigureGenerator.kt:117,153`，兼作规范资产 id 与文件名）；`SourceAssetType.GENERATED_FIGURE` 代码值（`core/database/.../StudyDbValue.kt:50`，TEXT 常量、零 schema）；生成成功注册 canonical 行 + 引用（题重绘 role=CLEAN_IMAGE / 会话图 `tutor_message_source_asset`）；UI 从持久引用重建 | 批 2（`92764af2`）；测试面 `AttachedImageGeneratorTest`、`TutorAssistantMessageFigureLinkInstrumentedTest`、`TutorTurnMessagesTest`；批 2 正式门 DB 全套 **210/0/0**、core:data 定向 **14/0/0**、feature:tutor 全套 **64/0/0**、app 三屏 **5/0/0**；幂等/引用缺口边界见 §7.2 |
| **A3** 门单源 | `ConfiguredCleanImageGenerator` 删内联门→改调 `resolveImageCredential`（`core/data/.../capture/ConfiguredCleanImageGenerator.kt:31,48-51`，KDoc 明说唯一实现）；`savedMistakeSheetBytes` 改 `vault.resolve`（`AttachedImageGeneratorFactory.kt:55,69`） | 批 1；新增 `ImageCredentialGateSingleSourceTest`（「改 Gate 一处、两链都变」的变异测试）；`ConfiguredTutorFigureGeneratorTest` / `SavedMistakeSheetBytesTest` 随迁 |
| **A4** 三态与记账 + 隐式 AI 标识 | `AttachedImageCard` 三态 GENERATING/FAILED（重试走幂等键，不重复付费）/READY；重绘静默失败→「图重绘未完成，已保留原图」；成功→「本次生成已计入额度」；记账=次数/时间/模型/用途（不猜金额）；生成图文件写 属性+提供者+编号（PNG eXIf / JPEG EXIF，`core/data/.../capture/GeneratedFigureMetadata.kt`） | 批 2；`AttachedImageCardTest`、`GeneratedFigureMetadataInstrumentedTest`；批 2 门同上；隐式标识落点/不随 PDF 边界见 §7.2；真机可读回/解码在综合门登记（§7.2、§7.6） |
| **A5** MCP server 处置 | 保留目录、README 首行标注（`tools/image-mcp-server/README.md:3`）：「仅开发用；无 SSRF / 凭证防护；已被 App 内 `GENERATE_FIGURE` 工具取代」（不删目录） | 批 1；首行已核对 |
| **A6** 大厅死分支清理 | 大厅 `attachedImages` 从 `TUTOR_LOBBY_WIRE_KEYS` 移除 + 指纹 strip；`TutorLobbyTasks.attachedImages` 注释为遗留解码载体、不再由任何解析器写入；Respond/Plan 降级为兼容字段（仍解析仍渲染） | 批 1；`TutorLobbyTasksTest`（未知键拒 + 旧行兼容）；批 1 门 |
| **B1** `MistakePdfLayout` | `core/model/.../MistakePdfLayout.kt`（批 4 由 core:export 迁入，R079）：`templateId`（compact/practice_sheet/with_answers）、`marginPt 24..72`、`fontScale 1..3`、`columnCount 1\|2`、`blockOrder` 白名单、`imageScale 0.5..1.0`、`includeAnswer/Solution/Note` 默认 false + `validate()` + `DEFAULT`；进 `MistakePdfExportInput` 与 `exportFingerprint` 全字段 | 批 3 落地、批 4 迁移；`MistakePdfLayoutTest`、`MistakePdfEligibilityTest`（换 layout 必换指纹）；批 3 门 core:export **19/0/0** |
| **B2** 参数化 / 练习卷 | 常数注入 + `PdfLineStyle` 按档缩放；作答空白区（选择 1 行高×≤6；段落 `max(3, 字数/25)` 行高细线框，`MistakePdfPagePlanner.kt:238,579`）；答案/解析**独立成区**（用户 2026-10-01 裁定：题目区在前、答案/解析区在后）；`includeSolution` 无数据源→`UNSUPPORTED_LAYOUT_FEATURE` fail-closed 跳过（`unsupportedRequestedFeatures` 单源，planner 与结果通知同读）；`columnCount=2` 且图超半栏→缩半栏 | 批 3；`MistakePdfPagePlannerLayoutTest`、`MistakePdfLayoutTemplatesInstrumentedTest`（模板字节互异 + 空白区断言）；批 3 门 |
| **B3** START_EXPORT / 导出 sheet | payload **键恰好** `{templateId, layout}`（缺字段拒、多字段拒；templateId 逐字枚举；`MistakePdfLayout.validate()`；`AgentPendingRequest.kt:209-213`）；确认卡展示模板名与关键参数；确认后打开导出 sheet（版式表单预填模型提议值可改；候选题=本地留痕默认列出、学生勾选、可库内再搜；**payload 不含题 id**）；确认→后台导出（`ExportPdfWorker` 带 layout）→「导出成果」hub；skipped 单源 | 批 4；`AgentPendingRequestTest`、`TutorLocalActionParserTest` / `TutorLocalActionAdmissionTest` / `TutorLocalActionAdvertisementParityTest` / `TutorLocalActionExecutionTest`、`TutorPendingRequestCommandsTest`、`TutorExportSheetTest`、`ExportPdfWorkerTest`、`MistakeExportJobRunnerTest`、`RoomAgentPendingRequestRepositoryTest`；批 4 正式门 app 五类 **9/0/0**；旧形状读回边界见 §7.4 |
| **B4** 三链路回归与金样 | `PreparedPdfPrintWriter`（打印字节层核心抽为测试 seam，逐字搬迁、行为不变）；回归 3 例：预览第 0 页尺寸/aspect、**整份打印字节 = prepared 文件字节逐字节**、部分页仅所请求页重渲染；金样对拍 `MistakePdfPlannerGoldenTest`（确定性渲染 sha256 摘要） | 批 3；`MistakePdfDeliveryRegressionInstrumentedTest`、`MistakePdfPlannerGoldenTest`、`MistakePdfPagePlannerTest`；批 3 门 core:export **19/0/0**、feature:library 导出 **9/0/0**、app 导出类 **8/0/0**；金样单机登记口径见 §7.3 |
| **B5** Typst 研究 | `docs/research/typst-android-feasibility.md` 落盘；结论门判定**不立项**：许可 Apache-2.0 通过；体积门 **不满足**（java-typst 2.0.0 = 15.9MB 已超 / 2.2.0 = 34.3MB、typst.ts wasm = 27.0MB）；java-typst 需 minSdk 26 与项目 23 冲突；编译性能无可信基准 → 维持参数化模板 + Canvas MathBox；复查条件 3 条（≤15MB 含中文渲染字体 / minSdk ≤23 且单题 ≤数百 ms / 用户明确接受抬 minSdk+体积） | 批 3；批 3 独立复核经网络复核关键数字为真；不进本阶段门禁 |
| **B6** 干净图入导出 | **零代码改动**：cleanImage 引用表/role 一致性经核实（写侧 `ProblemDraftTransactionDao` / 两入口；读侧 `MistakeDetailDao → RoomMistakeDetailRepository → MistakePdfEligibility`）；A2 重绘资产（role=CLEAN_IMAGE）自然进入现有 cleanImage 逻辑；无重绘图回退现状（结构化图块渲染，原图不导出） | 批 3；`MistakePdfCleanImageExportInstrumentedTest`（补「有重绘图导出含图 / 无重绘图按现状」两用例 + 真机像素带用例：真实 PNG → CLEAN_IMAGE → PDF 真画出）；原计划未分派 → 并入批 3 闭合退出门 |

## 4. 门（批级正式门 + 综合门 + 设备级走查）

### 4.1 批级正式门（均为修复后状态重跑；时间戳 2026-10-03/04 UTC）

| 批 | 门 | 结果 |
|---|---|---|
| 批 1 | 全量 JVM `test --continue --rerun-tasks`（243 任务） | ✓ |
| 批 1 | core:data 工具环仪器化 | **12/0/0** |
| 批 1 | app 三屏仪器化 | **5/0/0** |
| 批 2 | 全量 JVM | ✓ |
| 批 2 | core:database 仪器化全套 | **210/0/0** |
| 批 2 | core:data 定向仪器化 | **14/0/0** |
| 批 2 | feature:tutor 仪器化全套 | **64/0/0** |
| 批 2 | app 三屏仪器化 | **5/0/0** |
| 批 3 | 全量 JVM | ✓ |
| 批 3 | core:export 仪器化 | **19/0/0** |
| 批 3 | feature:library 导出类仪器化 | **9/0/0** |
| 批 3 | app 导出类仪器化 | **8/0/0** |
| 批 4 | 全量 JVM | ✓ |
| 批 4 | core:data 定向仪器化 | **12/0/0** |
| 批 4 | feature:tutor 仪器化 | **64/0/0** |
| 批 4 | feature:library 仪器化 | **9/0/0** |
| 批 4 | app 五类仪器化 | **9/0/0** |

### 4.2 4B 综合门（2026-10-04 凌晨）

| 门 | 结果 |
|---|---|
| 全量 JVM | **2344 / 0 / 0**（模块分解：app 95、core:data 648、core:database 129、core:domain 558、core:export 63、core:model 410、core:ui 38、feature:capture 110、feature:library 39、feature:profile 6、feature:review 39、feature:tutor 209） |
| core:database 仪器化全套 | **210 / 0 / 0**（时间戳 `2026-10-03T23:22:51Z`） |
| app 全套仪器化 | **53 / 0 / 0**（时间戳 `2026-10-03T23:24:59Z`） |
| R8 冒烟 | `:app:assembleLocalFirstRelease`（带 `RELEASE_*` 签名环境变量）✓ → adb install ✓ → monkey 启动（包名 **`com.tingyun.smartmistakebook.localfirst`**，注意 flavor 后缀）✓ → PID **20321** → logcat 94 行、`FATAL` / `ClassNotFound` / `NoSuchMethod` / `NoClassDefFound` 计数 **0** → 截图 = 首页（复习/智能体/错题本/我的） |

### 4.3 设备级走查（实测记录，如实写）

- **已实测**：R8 release 装机启动到首页；错题本空态；录入方式选择（拍照/相册/整卷-PDF）；系统照片选择器选图；图片导入 + 本机 OCR 成功；无模型凭据时界面显示 fail-closed 块「配置好模型后，拍照题图才会交给模型整理」（debug 构建）。
- **不可达（模拟器无模型凭据 / 无种子数据）**：转写→保存→导出→系统打印对话框（另存为 PDF）→分享面板（微信接收 PDF 未文档化）→通知点击→SAF 保存。该段由仪器化覆盖：core:export **19 例**（含打印字节=prepared 逐字节、预览 0 页、干净图真画进 PDF）、app 导出类（delivery/background/notifications/driver）、feature:library 导出 **9 例**。
- **结论**：4B 退出门中「生图→导出→打印真机走查」一条**未能完整走通**（缺模型凭据），以仪器化证据 + 本段实测覆盖，登记为 **UNVERIFIED**（§2、§7.6）。

## 5. 独立复核（逐批；每批一个新 Agent、只读对抗性）

| 批 | 结论 | 处置 |
|---|---|---|
| 批 1 | approve-with-notes | 1 条 P1「假渲染承诺」+ F2/F5/F6 修复；F3/F4/F7 登记（其中 **F4/F7 批 2 已做**：图可见后加「优先用 GENERATE_FIGURE」偏好句、sourceType 白名单加 `GENERATED_FIGURE`） |
| 批 2 | approve-with-notes | 1 条 P1「标识随导出/分享保留」收敛为登记（见 §7.2 F1-1）+ 9 条 P2 全处理；**并发双付：单进程不可达**（模型任务全局串行），**多实例残余登记**（见 §7.2） |
| 批 3 | approve-with-notes | 无 P0/P1；6 条 P2 全处理；金样摘要经独立复算一致；B5 关键数字经网络复核为真 |
| 批 4 | **block** | P1×2：①旧形状 `START_EXPORT` 行读回抛异常；②导出 sheet「再选」静默无效 → 修复 → **定向复验 approve-with-notes**（两 P1 闭合、均有回归用例；残留 N1–N5 见 §7.5） |

## 6. 用户四项裁定与实现一致性

| # | 裁定（2026-10-03） | 实现 | 一致性 |
|---|---|---|---|
| ① | B3=模型只提议版式参数 + 可按用户要求给出候选题（本地留痕），学生在候选中勾选；payload 不含题 id | payload **键恰好** `{templateId, layout}`（无题 id 字段，缺/多字段均拒，`AgentPendingRequest.kt:209-213`）；导出 sheet 候选题=本轮本地留痕、学生勾选、可库内再搜 | ✅ |
| ② | AI 标识只做隐式元数据（显式角标不做） | 生成图文件写 PNG eXIf / JPEG EXIF（批 2）；显式标识未落，登记「对外分发的 PDF 目前不含任何 AI 标识」于 §7.2 F1-1 | ✅（如实登记边界） |
| ③ | 分发=系统分享面板（不引微信/QQ SDK） | `MistakePdfDelivery.kt:398` 走 `Intent.ACTION_SEND`（系统面板）；构建清单核对：无 tencent/weixin/wechat/QQ 依赖 | ✅ |
| ④ | 不新增应用内预览；打印=系统打印对话框（另存为 PDF） | 4A 退场的 `MistakePdfPreview` 本阶段未恢复（生产消费者仍为零，仅 core:export 内部与仪器化用例）；打印交付链复用 `MistakePdfDelivery`（系统打印） | ✅ |

## 7. 遗留与 UNVERIFIED（边界清单逐条；本记录为最终去向）

> 本节逐条转写 4B 边界与登记清单（批 1–批 4 + 复核残留 N1–N5 + 综合门与走查）。
> 明确标 **UNVERIFIED** 的条目：退出门真机走查（§2）、批 2 真机未验证组（§7.2 末条）与 §7.6、
> 提示词 v17 对真实 provider 行为（§7.4）、B5 的 APK 增量/中文字体/NDK/编译性能（研究报告 §4）、
> 微信接收 PDF（§7.6）。

### 7.1 批 1 边界

- **F3 图形题分类无持久化数据源**：A1 前置约束「重绘只限图形题（WITH_FIGURE）」在工具环**无本地判据**——`ImagePipelineProblemKind.WITH_FIGURE` 只在采集链瞬时分类、未持久化（`ProblemDraftRecord` 无字段），4B 零 schema 无法承载 → 登记为边界：工具环 REDRAW 只要求「当前题有源资产」；采集链的自动重绘仍只对 WITH_FIGURE 生效。
- **F4**（批 1 登记，批 2 已做）：图可见后加「优先用 GENERATE_FIGURE」偏好句。
- **F5**（批 1 修复）：saved-sheet 链完整性判据抽纯函数 + JVM 负向用例；整链（Context/vault 定位文件那半）负向仍只有仪器化覆盖。
- **F7**（批 1 登记，批 2 已做）：sourceType 白名单加 `GENERATED_FIGURE`。

### 7.2 批 2 边界

- **F1-1（P1 收敛为登记）——PDF 不携带图像级隐式标识**：隐式 AI 标识写在生成图**文件字节**里；文件原样复制/备份时仍在；**经 App 的 PDF 导出会重绘，标识不随 PDF 走**。加上「显式标识未落（办法第 4 条）」——即：**对外分发的 PDF 目前不含任何 AI 标识**（隐式不随 PDF、显式未做）。**对外分发前重开。**
- **F2-1（P2）——兼容链 GC 后可能重复付费**：兼容 `attachedImages` 链不建消息引用 → 孤儿回收（30 分钟宽限 + 用户触发存储清理）后重看旧回复可能重新生成（重复付费）；主路径（GENERATE_FIGURE）不受影响；根治需把 `messageId` 穿进解析器（成本大，未做）。
- **F2-2（P2）——PROCESS 两链幂等键不同**：工具链提示词含当前题上下文、兼容链不含 → 同一轮同时出 `attachedImages` 与 `GENERATE_FIGURE` 时 PROCESS 可能双付（REDRAW 两链同键）。
- **F2-6（P2）——`figure-<hex>` 契约例外**：`GENERATED_FIGURE` 记录的 `relativePath` 不满足 `validateSourceAsset` 的 `source-assets/<contentSha256>.<ext>` 不变量（`figure-<hex>` 身份）；只经 `registerCanonicalSourceAsset` / `attachCleanRedrawAsset` 落库，当前**无生产路径**送进 `validateSourceAsset`。
- **并发面**：同一幂等键两路并发 miss → 双付是否可达；复核结论**单进程不可达**（模型任务全局串行），**多实例（多进程）残余登记**。
- **真机未验证（转综合门）**：PNG eXIf 写/读与可解码性（新增 decode 断言后仍需设备跑）、旋转/进程重建不重复出网（设备级走查）、真实分享后元数据仍在——综合门时因无模型凭据，生图段不可达，见 §7.6。

### 7.3 批 3 登记

- **金样为「本实现单机登记」**：4A 无改前逐位基线、本轮不许检出旧提交 → DEFAULT 等价证据 = 逐项结构断言 + 常量读码；摘要值因 P2-1 修复重登记（**DEFAULT `b2cb5e06…`、练习卷 `0a8dfa0d…`**）；跨环境（CI 字体/系统差异）不稳定时按计划退化方案并如实标注。
- **B5 结论：不立项**（许可 Apache-2.0 放行；体积门不满足——java-typst 15.9/34.3MB、typst.ts wasm 27MB、minSdk 26 与项目 23 冲突；性能无可信基准）→ 维持参数化模板 + MathBox。APK 增量未实测（产物原大小口径）；NDK 路线为单一来源估计；中文字体自备为假设（均见研究报告 §4）。
- **B6**：cleanImage 引用表/role 一致性经核实（写侧 `ProblemDraftTransactionDao` / 两入口，读侧 `MistakeDetailDao → RoomMistakeDetailRepository → MistakePdfEligibility`），无代码改动；补投影两用例 + 真机像素带用例（真实 PNG → CLEAN_IMAGE → PDF 真画出）。
- **三链路回归仪器化 3 例**（预览 0 页尺寸、整份打印字节=prepared 逐字节、部分页仅所请求页）+ 打印字节层核心抽为 `PreparedPdfPrintWriter`（逐字搬迁、行为不变的测试 seam）。

### 7.4 批 4 登记

- **进程死亡 sheet 有意不持久化（决策，非技术限制）**：恢复「已 ACCEPTED 卡的 sheet」需要额外「应打开」标记，不在本批范围；进程死亡后学生需**重新发起导出**。
- **hub 无跳过说明（N4）**：hub 行不显示「解析区已跳过」说明（导出记录表无列可存，零 schema）；sheet 入口 + 完成通知两处已如实说明；hub 行未接（登记）。
- **提示词 v17（START_EXPORT 参数声明）对真实 provider 的行为未验证**（需真实模型调用）。
- 导出 sheet 的 Compose 交互只有 JVM 纯逻辑测试；设备级走查在综合门。
- **P1-a 修复后**：旧形状 `START_EXPORT` 行走「读回宽容 + 卡降级 + decide 用 DEFAULT」路径（写口仍严格）。

### 7.5 批 4 复核复验残留（P2 登记，不阻塞）

- **N1**：非 `{}` 的坏形状旧行（需 DB 损坏才可达）卡片预告与点击结果措辞不一致（卡说「确认后打开导出设置」，decide 如实拒绝）——非死路，登记。
- **N2**：P1-b 的 VM/Composable 接线无测试（纯函数用例 + 代码阅读），与「Compose 交互仅编译 + JVM 纯逻辑」口径一致。
- **N3**：worker decode 失败新分支无直接用例（复用 4A `giveUpOrRetry`；worker 编排历来由仪器化覆盖）。
- **N4**：hub 仍不显示「解析区已跳过」（仅完成通知带）；导出记录表无说明列（零 schema）——完成记录/台账必落一行（本记录 §7.4 已落）。
- **N5**：旧形状 `{}` 由实施者 `git show d2317f5d` 核实；宽容范围是任意合法 JSON 对象，结论不依赖该核实。

### 7.6 综合门与走查登记

- **B6（干净图块进导出）原计划未分派** → 并入批 3（导出侧、零代码改动、补「有重绘图导出含图 / 无重绘图按现状」两用例），闭合退出门。
- **批 4 综合门真机走查**：模拟器无模型凭据 → 「生成图」段不可达，按 4A 口径如实登记；其余段（持久化/导出/打印/分享）已尽量实测（§4.3）。
- **微信接收 PDF 的系统分享面板行为未文档化** → 真机走查实测记录：因走查不可达，本条仍为 UNVERIFIED。

### 7.7 版本与迁移登记

- **零 schema**：`STUDY_DATABASE_VERSION` 保持 **61**（`core/database/.../StudyDatabase.kt:125`）；无表/列改动。
- **`LearningCoreVersions` 不动**：复合串维持 `learning-core-v12`。
- **提示词 bump 自记**（即台账 §3.18 行）：批 1 plan v16 / respond v23 / lobby v14（`-figure-no-preview`）→ 批 2 plan v18 / respond v25 / lobby v16（`-figure-conditional`）→ 批 4 lobby v17（`-export-layout-proposal`）；批 3 不动。实际值已逐提交核对（§1）。

## 8. 剩余风险

- **退出门「生图→导出→打印真机走查」未完整闭环（UNVERIFIED）**：模拟器无模型凭据，生图段不可达；真实通知点击 / SAF 保存 / 分享面板（含微信接收 PDF）承接 4A 遗留仍未人工闭环——发布前建议在有模型/有数据的真机补一次整屏走查。
- **对外分发的 PDF 无 AI 标识**（隐式不随 PDF 重绘 + 显式标识未做）——合规项，对外分发前必须重开（§7.2 F1-1）。
- **兼容链重复付费残余**：F2-1（GC 后重看旧回复）/ F2-2（PROCESS 两链键不同）/ 多实例并发残余（§7.2）——主路径不受影响，根治未做。
- **金样为单机登记**：无可比对的改前逐位基线；跨环境确定性未验证（§7.3）。
- **提示词 v17 对真实 provider 行为未验证**（§7.4）。
- **hub 行缺「解析区已跳过」说明**（零 schema 无列可存，N4）。
- **B5 二期复查条件未满足**：Typst 路线在体积/minSdk/性能三门均未过，维持现状；若将来满足复查条件需重新出研究。
- **Mimosa / 安全边界**：本阶段不宣称任何安全结论（计划 §6 口径不变）。
