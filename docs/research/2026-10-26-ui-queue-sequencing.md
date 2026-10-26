# 热区排队产品项盘点与顺序研究（2026-10-26）

> **性质：只研究不实现。** 本文只做仓内产品状态、依赖与热区核对，并给出建议顺序与一页设计骨架；不改任何生产代码、不跑 Gradle/设备、不做 git 写操作。
>
> **基线：** HEAD `5bc8bdf8`（= 批次 23 收口，`git rev-parse HEAD` 实读）；工作树快照取自 [`2026-10-26-batch-24-baseline.md`](2026-10-26-batch-24-baseline.md) §2（冻结于 2026-10-26 19:34:43，200 M + 469 ??），并用本会话逐文件 `git status --porcelain -- <path>` 抽查关键落点。基线明确记录树在核对后仍在变脏（`:5`），所以本文的脏态是**时点读数**，不是稳定终值。
>
> **口径：** “干净” = `git status --porcelain -- <path>` 无输出，不代表内容无缺陷；“热区” = 批次 24 计划 §0 的清单 + 本任务共享纪律点名的禁改面；“已完成”只在有代码/命令/设备/真实模型分栏证据时才写，文档状态与代码状态分开记。

## 0. 方法与边界

**读过的权威材料（本会话实读）：**

- 计划：[`.zcode/plans/plan-2026-10-26-batch-24-isolation-final-kd70.md`](../../.zcode/plans/plan-2026-10-26-batch-24-isolation-final-kd70.md)（T5 条件轨在 `:15`）。
- 总路线：[`docs/REFACTOR-MASTER-LINE.md`](../REFACTOR-MASTER-LINE.md) 的阶段 3D/3E/4C/5/5B/6/6B 行（`:66`、`:70`、`:72`、`:76`、`:77`、`:78`、`:79`）、§4 顺序刚性（`:84-103`）、§7.2 阶段 5 拆批（`:170-176`）、§12 排期（`:220-235`）。
- 决策台账：[`docs/agent-first-refactor-decisions-2026-09-23.md`](../agent-first-refactor-decisions-2026-09-23.md) 的 5c 范围 `:174`、5b/5c 拆批 `:2733`、6B-3 记录 `:3012/:3025/:3036`、G7 登记 `:3255/:3294/:3313/:3350-3351`。
- 需求定稿：[`docs/requirements-fused-final-2026-10-11.md`](../requirements-fused-final-2026-10-11.md) 的统一标注约束 `:73`、R5 的 F7–F18 `:108-120`、R8 的 H2/H3 `:165-167`、错因/E11 `:69-71/:135`。
- 新阶段 spec：[`docs/research/2026-10-11-new-stages-spec.md`](2026-10-11-new-stages-spec.md) 的 3E `:14-29`、5 `:182-187`、6 `:196-200`、6B `:99-122`。
- 合规研究：[`docs/research/2026-10-25-ai-content-labeling-compliance.md`](2026-10-25-ai-content-labeling-compliance.md)（下称 R3）§6 G1/G2 `:153-154`、§9.2 未落 `:213-218`；文案硬规则 [`docs/ai-content-disclosure-copy.md`](../ai-content-disclosure-copy.md) `:49-56`。
- 近期批次记录：[batch 20](2026-10-25-batch-20-record.md) `:90-91/:142`、[batch 21](2026-10-25-batch-21-record.md) `:107/:260-266`、[batch 22](2026-10-26-batch-22-record.md) `:13-14/:275-276`、[batch 23](2026-10-26-batch-23-record.md) `:14/:288`、[batch 24 基线](2026-10-26-batch-24-baseline.md) §2/§3/§8。
- 缺陷台账：[`docs/known-defects.md`](../known-defects.md) KD-64 `:2551`、KD-68 `:2637-2657`、KD-66 `:2573-2598`。

**本会话实际执行的检查：**

- `git status --porcelain -- <path>` 逐个抽查候选落点（结果见 §2、§3）。
- `grep -rn "AI 生成\|AI生成\|内容由AI\|由AI生成\|人工智能生成" --include=*.kt --include=*.xml feature/tutor/src/main core/ui/src/main app/src/main/res` → **0 命中**（G1/G2 未落）。
- `grep -rn "AWAITING_VERIFICATION" --include=*.kt feature core/domain/src/main` → **0 命中**（UI/域层没有独立态）。
- `grep -rn "AttachedImageCard\|GeneratedFigureCard" ...`、`grep -n` 若干行号（见各节）。
- `python -c "import json; ..."` 读取 `tools/split_golden/tables/split_golden_v1.json`：`pages=7`、`boxes=16`、`evidenceGrade="model-labeled, no human expert; two independent labelers agreed"`；旁车 sha `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f`。
- `WebFetch` 官方《人工智能生成合成内容标识办法》`https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm`：确认 2025-09-01 施行，第四条要求显式标识、第五条要求在文件元数据中添加隐式标识（下载/复制/导出功能须保留合规显式标识）。

**没做的：** 未跑 Gradle、JUnit、androidTest、设备用例；未编译；未 `git fetch`；未改任何生产文件；未做 git 写操作。因此本文所有“测试通过/设备通过”均**引用仓内既有记录**，不是本会话复跑结果。

**外部检索说明：** 七项均为仓内产品状态与依赖盘点，没有“需要一个开源实现”的选型缺口，所以没有触发 `gh`/GitHub 检索；唯一外部核验是上方法规原文。不从训练记忆补任何版本、数值或 API。

---

## 1. 结论摘要

### 1.1 七项一句话判定

| 项 | 一句话判定 | 建议档位 |
|---|---|---|
| **G1/G2 显式标识** | P0 合规义务，代码零命中；G2 只动 `core/ui` 一个文件，G1 动 `feature/tutor` 两个文件；无 3D/3E/PRD 依赖 | **第一档（可最先做）** |
| **AWAITING_VERIFICATION UI 态** | 数据层已把“结果未知”做成终态，但 UI 映射成 `Idle`（`ExplicitRedrawService.kt:431`），点重绘只得到原始 `Rejected("AWAITING_VERIFICATION")`；这是 4E WG-03/06 的收口缺口 | **第一档（小批收口）** |
| **5b 知识点复习** | **不是零起点**：入口/会话/队列/3E 全库扫描代码都已在；剩余是 KD-68 诚实预览、F8/F9/F18 节奏预算（需产品裁定）与真实数据验收；受 3D 硬门 | **第二档（先做 KD-68 一片，再谈节奏）** |
| **5c 讲题/错因** | 当前只有详情页“错因 / 备注”自由文本（`MistakeDetailRoute.kt:698`），无结构化错因、无自动写/聚类/撤销；受 D-1 自动落账门、AUD-11、schema 70（预计；当前 69） | **第四档（最后）** |
| **阶段 6 档案设置** | 已有薄弱点展示与学习掌握页，但行不可点、无全库盲区、无“未练习”区分；依赖 5b/3E 闭环与 3D | **第三档（5b 之后）** |
| **6B-3 app 注入** | 端口、提示词、ViewModel 都在，唯独 app 没注入；但 `core:data` 不能依赖 `core:export`/`feature:tutor`（`core/data/build.gradle.kts:169-171`），“注入”实际要先定新任务类型与提示词归属 | **第二档（先设计再实现）** |
| **AUD-08 切分金标门** | 门已定义、金标已冻结、脚本可复跑；但门值 0.5714 < 0.9，5/7 页无应用输出，且 `build/split-eval/app-split.json` 没有仓内生产者；验收目标是“门可复跑 + 自动入库继续关闭”，不是“门必须变绿” | **第一档（工具链可先做，不需热区）** |

### 1.2 三个必须纠正的现状认知

1. **5b 不是“未开工”。** 总路线阶段 5 行只写了 5a 的代码落地（`docs/REFACTOR-MASTER-LINE.md:76`），但代码里知识点复习入口 `ReviewRoute.kt:122`、会话 `KnowledgeReviewSessionScreen.kt:97`、队列 `KnowledgeReviewQueue.kt:131`、3E 第二档 `StudyReviewPlannerService.kt:721-828` 都已存在。5b 的真实剩余是“节奏/预算/诚实预览 + 设备验收”，不是重建知识点复习。
2. **6B-3 不是“把端口接上”那么小。** 端口 `TutorPaperEditAssistant.kt:25` 与提示词 `:35` 在 `feature:tutor`，而模型派发在 `core:data`；模块依赖图里 `core:data` 只依赖 `core:model/core:domain/core:database`，**不依赖 `core:export` 或 `feature:tutor`**（`core/data/build.gradle.kts:169-171`）。要注入真实模型，必须先裁定：新 `ModelTaskKind`/输入 DTO/提示词放在哪一层，或是否允许 app 侧直接调 gateway（后者会绕开统一 ledger/预算，不推荐）。
3. **AUD-08 的验收不是“把 0.5714 提到 0.9”。** 审计门要求“门说明、数据集与逐题裁定可复跑；切残不得自动入库；失败页可补拍/继续”（`docs/REFACTOR-MASTER-LINE.md:145`）。当前门为 false、门后自动化未启用（`docs/REFACTOR-MASTER-LINE.md:72`），这本身是安全状态；要补的是**可复跑的应用导出生产者**、**逐页裁定落盘**与**金标证据等级决定**，不是改阈值。

### 1.3 TL;DR 建议顺序

**现在就能做（不碰热区）：**

0. **AUD-08 工具链半边**：新增 `tools/split_golden/export_split_eval.py` + README，把设备 DB → `build/split-eval/app-split.json` 的步骤做成可复跑脚本；不碰 `feature/capture`。

**热区清空后（串行）：**

1. **G2**（`core/ui` 一个文件）→ **G1**（`feature/tutor` 两个文件 + 共享角标）。
2. **AWAITING_VERIFICATION UI 态**（`feature/library` + `core/domain` + `core/data`）。
3. **5b-1 / KD-68**（`feature/review` 一个文件 + 测试）：积压预览不再承诺不可调度项。
4. **AUD-08 收口**（`feature/capture` 失败页复核 + 设备重测 + 金标证据决定）。
5. **6B-3 注入**（先一页架构裁定，再 `core:model` + `core:data` + `feature:tutor` + `app`）。
6. **5b-2 / F8-F9-F18**（需产品裁定；`feature/review` + 设置 + `core:data`）。
7. **阶段 6 档案**（`app` + `feature/profile` + `core/domain/data` + `feature/review`）。
8. **5c 错因**（最后；schema 70 + 模型任务 + 全链）。

> 若 3D 硬门先收口，5b-2 与阶段 6 可以整体前移到第 4 步之后；否则它们只能做“受控开发/隔离测试”，真实数据启用继续挂起（`docs/REFACTOR-MASTER-LINE.md:101`）。

### 1.4 给批次 24 T5 的条件轨结论

计划 T5 的条件轨写了“feature/library 干净→AWAITING UI；tutor+ui 干净→G1/G2；否则登记”（`plan-2026-10-26-batch-24-isolation-final-kd70.md:15`）。按基线冻结时点：

| 条件 | 实测 | 本批可做？ |
|---|---|---|
| `feature/library` 干净 | **29 M + 11 ??**（基线 `:45`），`MistakeDetailRoute.kt` 为 M（本会话 `git status`） | **否**，登记 |
| `feature/tutor` + `core/ui` 干净 | tutor **37 M + 21 ??**、core/ui **11 M + 24 ??**（基线 `:45`） | **否**，登记 |
| `core/domain` 干净 | **4 M**（基线 `:56`），`MistakeDetailRepository.kt` 为 M | **否** |
| `core/data/.../visual` 干净 | 干净（基线 `:109`） | 与本七项无关 |
| `tools/split_golden` 干净 | 本会话 `git status --porcelain -- tools/split_golden` **空** | **是**，可做 AUD-08 工具半边 |
| `feature/profile` 干净 | **3 M**（基线 `:62`），`ProfileRoute.kt` 为 M | **否**（阶段 6 连“非声明热区”也脏） |

**T5 本批判定：** 三个声明热区全部未空出，G1/G2 与 AWAITING 都应登记遗留，不得以“小改”越权；唯一可动的是 `tools/split_golden` 的 AUD-08 工具链。

---

## 2. 七项总表

> 价值 = 对学生/合规的直接可见收益；依赖 = 3D/3E/PRD 前置；风险 = 改动面/语义风险；热区 = 当前禁改面耦合。证据行号见 §3。

| 项 | 用户可见价值 | 依赖（3D/3E/PRD） | 最小落地范围（生产文件） | 验收判据 | 与热区他线的关系 | 当前状态（读码/记录） |
|---|---|---|---|---|---|---|
| **G1 讲题 AI 提示** | 学生知道讲解是 AI 生成，不是老师/教材；P0 合规 | 无 3D/3E/PRD；法规第四条 | `feature/tutor/TutorSurfaceMessages.kt`、`TutorConversationSurface.kt`、新增 `core/ui/AiGeneratedBadge.kt` | 持久回复与流式回复都显示“AI 生成”；学生消息/失败卡不显示；不可关闭；TalkBack 可读 | `feature/tutor` 37 M、`core/ui` 11 M；两文件均 M | 零代码（grep 0 命中）；R3 §9.2 open |
| **G2 App 内生成图角标** | 学生展开生成图时能看到“AI 生成”，不与原题图混淆 | 无 3D/3E/PRD；法规第四条 | `core/ui/AttachedImageCard.kt`（`AttachedImageCard` READY `:216`、`GeneratedFigureCard` READY `:345`） | READY 折叠行与展开图都有角标；GENERATING/FAILED 不加；原题图不误标 | `core/ui` 11 M + 24 ??；文件 M | 零代码（grep 0 命中） |
| **AWAITING UI 态** | 重绘结果未知时不再显示“可重绘”然后弹原始原因；给出“重新核实/放弃” | 无 3D/3E/PRD；4E WG-03/06 收口 | `core/domain/MistakeDetailRepository.kt`、`core/data/.../ExplicitRedrawService.kt`、`feature/library/MistakeDetailRoute.kt`（+ 可选 `CleanRedrawTaskContract.kt`） | 显示独立待核实态；不再泄漏 `AWAITING_VERIFICATION` 原始串；重新核实能据本地产物收敛；放弃显式且不自动重发；进程死亡后重开可见 | `feature/library` 29 M、`core/domain` 4 M、`core/data` 10 M；`ExplicitRedrawService.kt` 本身干净 | 数据层有状态（`CleanRedrawTaskContract.kt:42-48`），UI 映射为 Idle（`:431`） |
| **5b 知识点复习** | 每天有知识点卡片、预算封顶、可跳过、全库薄弱点能捞回 | 3E 代码+设备 KD-64 已闭；3D 硬门仍开；PRD-02/03 已设备过；F8/F9/F18 数值待裁定 | 5b-1：`feature/review/ReviewRoute.kt`；5b-2：`ReviewRoute.kt`、`KnowledgeReviewSessionScreen.kt`、`app/SchedulingSettingsScreen.kt`、`core/data/.../study/RoomBackedStudyExperienceRepository.kt`（预算透传已有） | 全库捞回、预算/名额封顶、跳过/无材料出口、跨会话再次作答；KD-68 不再承诺不可调度项；真实数据启用须 3D 全过 | `feature/review` 11 M；`ReviewRoute.kt`/`KnowledgeReviewSessionScreen.kt` 均 M；`app/Root` M | 入口/会话/队列/3E 已在；F8 代码默认 20 分钟且未见用户配置源（读码）、F18 未见生产实现（读码）、KD-68 open |
| **5c 讲题/错因** | 错因可自动标注、检索、聚类、撤销；学生看到“我总栽在计算/审题” | 3D/D-1 自动落账门；AUD-11；PRD-02；D-4 仅覆盖非门；schema 70（预计；当前 69） | 5c-1：`core/model` 新错因类型、`core/database` 新表/DAO/迁移、`core/domain` 仓储、`core/data` 实现、`feature/library` 详情/筛选；5c-2 再加 `ModelTaskKind`/适配器/提示词/确认卡 | 错因可检索/筛选/撤销；备注原文不出网；D-1 前只能建议+确认，不能自动写；错因不写掌握证据；迁移矩阵绿 | 几乎全部热区：`feature/library`/`feature/tutor`/`core/domain`/`core/database`/`core/data`/`app` | 只有自由文本备注（`MistakeDetailRoute.kt:698`）；无结构化错因代码 |
| **阶段 6 档案设置** | 全库画像：已练/未练/不支持分开；薄弱行可点进复习；不伪造统计 | AUD-12；阶段 5 闭环 + 3E；3D 用于真实复习动作 | `app/LearningMasteryScreen.kt`、`feature/profile/ProfileRoute.kt`、`app/SmartMistakeBookRoot.kt`/`Destinations.kt`、`core/domain/StudyExperienceRepository.kt`、`core/data/study/StudyExperienceMappers.kt`、`feature/review`（定向复习） | 无记录不伪造；未练/不支持有明确出口；薄弱行导航到该知识点复习；只读，不写事实；既有设置入口保留 | `app` 23 M（LearningMastery/Root/Destinations 均 M）、`feature/profile` 3 M、`core/domain` 4 M、`feature/review` 11 M | 已有薄弱点/掌握页；行不可点（`WeaknessRow` 无 onClick）；无“未练习”桶 |
| **6B-3 app 注入** | 学生一句话改卷面（删题/调序/改文字），不改错题本 | 4B/6B-1/6B-2 已完成；无 3D/3E；真实 Provider 质量 UNVERIFIED | `core/model` 新任务输入/输出 + `ModelTaskKind`、`core/data/OpenAiModelTaskAdapters.kt`/`ModelEgress.kt`/gateway 能力、`app` 适配器、`app/Root` 注入两处 TutorRoute；`feature/tutor` 仅已有端口 | 端口 null 时入口隐藏；注入后可见；白名单解析/原子 applier 不放松；失败保留草稿；每次改卷一次派发且走 ledger；真实模型 UNVERIFIED | `feature/tutor` 37 M（`TutorExportEditSheet.kt`/`TutorConversationViewModel.kt` M）、`app/Root` M；`core/model` 与适配器主文件干净 | 端口/提示词/UI 已在；app 未注入；`TutorRoute` 两处调用无 `paperEditAssistant` |
| **AUD-08 切分金标门** | 切残题不会悄悄入库；自动入库只在准确率门后；失败页可补拍 | AUD-03 已设备过；4C 链已在；无 3D/3E；真实模型/标注是重测前提 | `tools/split_golden/export_split_eval.py`（新）、`README.md`（新）、可选 `split_golden_v2.json`；`feature/capture` 失败页若缺出口才改 | validator PASS；measure 同输入逐字节一致；应用导出从设备 DB 可复跑；逐页裁定落盘；门 false 时自动入库继续关闭；切残不入库；失败页可补拍/继续 | `tools/split_golden` **干净**；`feature/capture` 15 M；`CaptureScreen.kt` M | 金标 7 页/16 框、sha `f9e92c47…`；门 0.5714 < 0.9；5/7 页无应用输出；`app-split.json` 无仓内生产者 |

---

## 3. 逐项盘点

### 3.1 G1/G2 显式标识

**读码/记录事实**

- R3 差距清单把 G1 定义为“讲题对话界面 AI 回复显式提示（常驻提示条或每条回复尾部标注）”，G2 定义为“生成图 App 内展示角标（`AttachedImageCard` READY 态）”，均标 P0（`docs/research/2026-10-25-ai-content-labeling-compliance.md:153-154`）。
- 本会话 `grep`：`feature/tutor/src/main`、`core/ui/src/main`、`app/src/main/res` 中“AI 生成/AI生成/内容由AI/由AI生成/人工智能生成”**0 命中**。R3 §9.2 也记录了同一结论（`:213-218`）。
- G3（导出图注）已落：`core/export/.../AiGeneratedImageCaption.kt:27`，PDF 三处 + DOCX 三处图注（R3 §9.1）。所以缺的只是 G1/G2 两个交互面。
- G1 的两个渲染路径：
  - 持久回复：`feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorSurfaceMessages.kt:291`（`TutorSurfaceAssistantReply`），正文在 `:318-324`。
  - 流式回复：`feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationSurface.kt:324`（`TutorLiveTurnBlock`），正文在 `:351`。
- G2 的两个图卡：
  - `core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/AttachedImageCard.kt:150`（`AttachedImageCard`），READY 在 `:216`。
  - `:303`（`GeneratedFigureCard`），READY 在 `:345`。
  - 二者都由 `AttachedImagesSection`（`:414`）/`GeneratedFiguresSection`（`:483`）供给 `TutorChatConversation.kt:322/:336` 与 `TutorGeneratedTurn.kt:122`。
- 法规原文（本会话 WebFetch）：《人工智能生成合成内容标识办法》第四条要求对生成合成内容添加显式标识，下载/复制/导出功能须保留合规显式标识；2025-09-01 施行。来源：`https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm`。

**用户可见价值**

- 学生知道这段讲解/这张图是 AI 生成，不是老师原话或教材原图；对 beta 分发是 P0 合规项。G2 还消除“原题图 vs 生成图”混淆。

**依赖**

- 无 3D/3E/PRD 前置。G1/G2 是纯 UI 文案层，R3 明确“不动判据”（`:153`）。
- G7 未成年人叠加（加粗/前置角标）随 G1/G2 一次到位（`docs/research/2026-10-25-beta-compliance-copy.md:4/:116`、决策台账 `:3350`）；G5 设置页“AI 生成内容说明”入口可同批也可后置（`docs/ai-content-disclosure-copy.md:67`），但它需要 `SecondaryScreens.kt`（M，热区）。

**最小落地范围**

- **G2 单独一批（最小）：** 只改 `core/ui/AttachedImageCard.kt`。在两张图卡的 READY 态（折叠行 + 展开图）加“AI 生成”角标；GENERATING/FAILED 不加（它们已有状态语义）。测试放 `core/ui/src/test` 或 `core/ui/src/androidTest`。
- **G1 单独一批：** 新增 `core/ui/AiGeneratedBadge.kt`（共享组件；新文件，但目录仍是热区），改 `TutorSurfaceMessages.kt` 与 `TutorConversationSurface.kt`。不改 `SmartMistakeBookRoot.kt`、不改 `SecondaryScreens.kt`。
- **不建议把 G1/G2 与 G5/G7 合成一大批**：G5 设置入口要动 `SecondaryScreens.kt`（M）与 Root（M），会把一个 P0 小改拖成跨热区大改。G1/G2 先落，G5/G7 后补。

**验收判据**

- 持久回复、流式回复都出现“AI 生成”；学生消息、思考卡、工具痕迹、失败卡不出现。
- 不可关闭、不可移除；TalkBack 读出“本内容由人工智能生成”一类完整语义，不只是装饰性文字。
- G2 只在 READY 态出现；GENERATING/FAILED 不出现；原题来源图（非这两张卡）不被误标。
- 代码/模块命令：`feature:tutor` 单元测试 + instrumented UI；`core/ui` 测试。设备：截图/语义树；真实模型不需要。
- **不得**以 G1/G2 落地声称“应用内已合规”：G5 文案入口、G7 未成年人提示、KD-75 PDF 隐式标识仍未齐（R3 §9.2；`docs/ai-content-disclosure-copy.md:8`）。

**与热区他线的关系**

- G1 的 `TutorSurfaceMessages.kt`、`TutorConversationSurface.kt` 均为 **M**（本会话 `git status`）；`feature/tutor` 整体 37 M + 21 ??（基线 `:45`）。
- G2 的 `AttachedImageCard.kt` 为 **M**；`core/ui` 11 M + 24 ??（基线 `:45`）。
- 两处都是声明热区，**T5 本批不能动**；必须等对应线清空。G2 只依赖 core/ui，若 core/ui 先清空，可比 G1 先做。

---

### 3.2 AWAITING_VERIFICATION UI 态

**读码/记录事实**

- 数据层已有终态：`core/database/.../CleanRedrawTaskContract.kt:42-48` 定义 `AWAITING_VERIFICATION`，并列入 `TERMINAL`；注释明确“生成请求已发出、结果未知（进程死在生成轮）”“不盲目补发”（`:30-40`）。
- `ExplicitRedrawService.request()` 对 AWAITING 分两支：本进程执行者还在跑 → `Accepted`；执行者不在 → `Rejected("AWAITING_VERIFICATION")`（`core/data/.../ExplicitRedrawService.kt:261-268`）。注释直接点名“该路径与独立 UI 态需要 core/domain 与 feature/library 的类型/文案改动（他线热区），登记为遗留”（`:260-262`）。
- 但 `toExplicitRedrawState()` 的 `when` 没有 AWAITING 分支，落到 `else -> ExplicitRedrawState.Idle`（`:431`）。所以详情页看到的是“没有重绘过”，按钮仍显示“重绘干净题面”；点击后弹 `重绘请求未被接受（AWAITING_VERIFICATION）`（`feature/library/.../MistakeDetailRoute.kt:654-672`）。
- 域层 `ExplicitRedrawPhase` 只有 IDLE/RUNNING/FAILED/SUCCEEDED/SUPERSEDED（`core/domain/.../MistakeDetailRepository.kt:428-442`），`ExplicitRedrawState` 只有 phase/taskId/attemptCount/failureReason/canRetry/updatedAt（`:459-485`）。
- 批次 20 记录把“独立 UI 态 + 重新核实/放弃路径”登记为遗留（`docs/research/2026-10-25-batch-20-record.md:142`）；批次 21/22 继续登记（batch 21 `:263`、batch 22 `:276`）。

**用户可见价值**

- 现在的行为是“按钮看起来还能点 → 点了被拒 → 显示内部枚举名”。改成独立态后，学生看到的是“上次重绘结果未确认，原图仍可用”，并有明确的两条出口：重新核实、放弃这次重绘。这是 4E WG-03/06 从“数据正确”到“用户可理解”的最后一米。

**依赖**

- 无 3D/3E/PRD 前置。依赖 4E WG-03（AWAITING 终态）与 WG-06（显式重绘）已落的数据层。
- 需要产品裁定：“放弃”后是否允许以新身份重新发起（见 §6），以及“重新核实”的本地证据来源（修订的 CLEAN_IMAGE 引用 / 审计 ledger 产物）。

**最小落地范围**

- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`：`ExplicitRedrawPhase` 增加 `AWAITING_VERIFICATION`（`:428`）；`ExplicitRedrawState` 增加 `canRecheck`/`canAbandon`（`:459`）；`ExplicitRedrawRequestResult` 增加“重新核实/放弃”的结果类型（`:492` 起）。
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/ExplicitRedrawService.kt`：`toExplicitRedrawState()` 增加 AWAITING 分支（`:399-433`，替换 `else -> Idle`）；新增 `recheck(taskId)` 与 `abandon(taskId)`（只读本地证据 + 显式终态，不自动重发）。
- `core/database/.../CleanRedrawTaskContract.kt`：若“放弃”需要独立终态，增加 `ABANDONED` 并加入 `TERMINAL`（`:42-48`）。这只是字符串列取值，**不加 schema**。
- `feature/library/.../MistakeDetailRoute.kt`：状态行与两个动作（`:534-621`、文案 `:628-672`）。
- 测试：`core/data/src/test/.../ExplicitRedrawServiceTest.kt`（JVM）+ `feature/library/src/androidTest/.../MistakeDetailExplicitRedrawInstrumentedTest.kt`（设备）。
- 不改 Root/Destinations；不改 schema。

**验收判据**

- 进程死在生成轮后重开详情页：显示独立“结果未确认”态，不显示“重绘干净题面”这个会必然失败的按钮。
- “重新核实”：只读本地（修订视觉引用 / ledger attempt 产物），有产物 → 收敛 SUCCEEDED；无产物 → 仍显示未确认，不派发、不扣费。
- “放弃”：显式终态；不自动重发；若产品允许再发起，必须使用新操作身份，不能复用同一 taskId 造成“已终态不再受理”。
- 任何路径都不把 `AWAITING_VERIFICATION` 原始串直接展示给学生；日志/审计可保留。
- JVM 覆盖状态映射/幂等；设备覆盖“杀进程 → 重开 → 重新核实/放弃”。

**与热区他线的关系**

- `core/domain/MistakeDetailRepository.kt` **M**（基线 `:69`，本会话 `git status`）。
- `feature/library/MistakeDetailRoute.kt` **M**；`feature/library` 29 M + 11 ??（基线 `:45`）。
- `core/data/.../ExplicitRedrawService.kt` **干净**（本会话 `git status`），但 `core/data` 整体 10 M + 2 ??（基线 `:57`）；提交必须按路径白名单。
- 结论：**三处中两处热区**，T5 本批不能做；等 library + domain 清空。

---

### 3.3 5b 知识点复习

**读码/记录事实（重点：不是零起点）**

- 入口：`feature/review/.../ReviewRoute.kt:122-135` 有“知识点复习”第二入口，显隐由 `knowledgeReviewEntryVisible`（`:341`）控制；`review_start_knowledge_review` testTag 在 `:126`。
- 会话：`feature/review/.../KnowledgeReviewSessionScreen.kt:97`；无材料出口 `:245-250`，跳过出口 `:692`，结束页 `:195`。
- 队列：`core/domain/.../KnowledgeReviewQueue.kt:131-143` 定义两个范围档；第二档先按 `FULL_LIBRARY_SCAN_DAILY_CAP`（10，`KnowledgeReviewFullLibraryScan.kt:30`）取前 N，再与第一档并集，最后时间预算取队（`:160-181`）。
- 3E 全库扫描：`core/data/.../StudyReviewPlannerService.kt:721-730`（范围）、`:772-828`（第二档查询、cap、并集）。
- 预算：`RoomBackedStudyExperienceRepository.kt:1113` 固定 `DEFAULT_REVIEW_TIME_BUDGET_SECONDS = 20 * 60`，没有设置页透传（构造参数 `:98` 可注入，但生产没有用户配置源）。
- F16 重教：题级复习已有 `ReTeachOpening` / `ReTeachOpeningCard`（`feature/review/.../ReviewSessionScreen.kt:845`、`:1008-1015`）；**现有知识点复习会话中未见 leech/重教路径**（读码结论，不是“绝不存在”），5b 是否要另一套重教需产品裁定。
- KD-68：`ReviewRoute.kt:522-558` 仍用 `NewIntroductionPolicy.preview` + 固定 900s，文案“每天约 X 题，约 M 天安排完”；`StudyReviewOverview.intakeUnschedulableCount` 数据侧已落（`StudyExperienceRepository.kt:86-98`），`StudySnapshotBuilder.kt:91/:130-132` 已派生，但 UI 零消费（KD-68 `:2637-2657`；批次 23 T3 数据侧记录 `:2637-2652`）。
- 3E 设备门：KD-64 已于批次 21 关闭，设备 `StudyReviewPlanner3EScanInstrumentedTest` `OK (3 tests)`（`docs/known-defects.md:2551`；批次 21 记录 `:107`）。

**用户可见价值**

- 错题复习之外的第二条复习路径：按知识点出选择题、判答、回写掌握度；3E 让“今天错题没碰到的弱知识点”也能被捞回。F8/F9/F18 决定学生每天被安排多少、积压/休假后怎么追赶、周末能不能集中清空。

**依赖**

- **3E**：代码与 KD-64 设备门已闭；剩余是 5b 对队列/预算的验收，不是重新做 3E。
- **3D**：真实数据启用硬门仍未收口（D-0/D-0b/D-1/D-2/D-3/D-5 同包全过才开放新增学习写入，`docs/REFACTOR-MASTER-LINE.md:101`）。D-1 只是点估计过门，D-2/D-3/D-5 未共同收口（`:66`）。
- **PRD-02/03**：答案键纠错与原题修订已设备过（PRD-02 24/0、PRD-03 5/0+2/0，`docs/REFACTOR-MASTER-LINE.md:226`），不再是 5b 的阻塞项。
- **F8/F9/F18 数值/形态**：需求只给了方向（`:114-120`），没有冻结值；不得自行取值。

**最小落地范围（建议拆两片）**

- **5b-1（一片，先做）：** 只改 `feature/review/ReviewRoute.kt` 的 `IntakeBacklogPreview`（`:522-558`）：
  - 从 `overview.intakeUnschedulableCount` 取不可调度数；
  - 当 >0 时，文案改为“其中 Y 道超出每日预算，需专项会话”，并**不再**对全部积压承诺“约 M 天安排完”；
  - 当 =0 时保留现有文案（旧金样逐位不变）。
  - 测试：`app/src/androidTest/.../ReviewIntakeBacklogPreviewInstrumentedTest.kt`（当前只测 45 道/15 天，需加“不可调度”例）。
- **5b-2（产品裁定后再做）：** F8 预算设置（`app/SchedulingSettingsScreen.kt` + DataStore + `RoomBackedStudyExperienceRepository` 构造透传）、F9 追赶开关（`NewIntroductionPolicy` 已有考试 catch-up，不是休假/积压 catch-up）、F18 周末模式（`ReviewRoute`/planner）、F17 名额口径（当前“每次排计划 ≤10”，KD-46；若要真每日累计需持久账本/schema，属另一设计）。
- 不建议在 5b-2 里改 `KnowledgeReviewSessionScreen` 的主体；它已能完成“出题→作答→判答→回写→下一知识点”。

**验收判据**

- 5b-1：不可调度项不再进入“约 M 天安排完”的承诺；`ReviewIntakeBacklogPreviewInstrumentedTest` 覆盖“全超预算显示为不可调度/专项会话”；旧的无不可调度例逐字不变。
- 5b 整体：弱掌握点离开今日错题并集仍可被捞回（代码已具备，需设备/真实数据证据）；时间/名额双封顶；跳过不伪造作答；无材料有明确出口；跨会话再次作答不丢；真实数据启用须 3D 全过。
- 设备：`StudyReviewPlanner3EScanInstrumentedTest`（core/data）+ `KnowledgeReviewSessionInstrumentedTest`（app）+ `ReviewIntakeBacklogPreviewInstrumentedTest`；真实模型质量 UNVERIFIED。

**与热区他线的关系**

- `feature/review/ReviewRoute.kt`、`KnowledgeReviewSessionScreen.kt` 均 **M**；`feature/review` 11 M + 1 ??（基线 `:45`）。
- `app/SmartMistakeBookRoot.kt`/`Destinations.kt` **M**；`app/SchedulingSettingsScreen.kt` **M**。
- `core/data/.../StudyReviewPlannerService.kt`、`StudySnapshotBuilder.kt`、`StudyExperienceMappers.kt` **干净**（本会话 `git status`），但 `core/data` 模块整体脏。
- 结论：5b-1 只碰 `feature/review` 一个热区文件，是最小可做片；5b-2 跨 `app`/`feature/review`/`core/data`，必须等更宽的清空。

---

### 3.4 5c 讲题/错因

**读码/记录事实**

- 当前只有详情页自由文本“错因 / 备注”：`feature/library/.../MistakeDetailRoute.kt:698`、保存动作 `:751`、空态 `:760`；域模型 `MistakeDetailState.userNote`（`core/domain/.../MistakeDetailRepository.kt:186`）。决策台账明确“备注不砍”（`:464`）。
- 没有结构化错因：`core/domain`/`core/data` 没有 `MistakeCause`/错因表/分类枚举；`grep "错因"` 只命中 UI 文案与提示词禁写（`OpenAiProblemOrganizationProtocol.kt:95/:100/:125` 明确“不要把错因当分类/不推断具体错误原因”）。
- 需求现状：错题级错因“❌/🔌 三条合并”，错因聚类“facet 可支撑、无视图”，撤销/驳回“❓”（`docs/requirements-fused-final-2026-10-11.md:69-71`）；E11 历史检索“会话有账本、检索 UI 待核”（`:135`）。
- 统一约束：所有标注层（知识点、错因、标签、题型、题源）由智能体自动完成、对智能体可见可检索；**D-1 过门 = 自动落账许可门**，过门前人工确认只是过渡态（`:73`）。
- 总路线 5c 范围：错因自动写/检索/聚类/撤销、E11 历史检索、多解/解析复用/订正摘要、M2 与只在需要时询问备注；备注原文不出网；自动化许可继续按 D-1 及裁定 39（`docs/REFACTOR-MASTER-LINE.md:174`）。
- 5c 与 5a/5b 同受原 3D 硬门；承接 AUD-11 粒度/答案键反例（`docs/REFACTOR-MASTER-LINE.md:76`、§7.2 `:173-174`）。

**用户可见价值**

- 学生能看到“我总栽在计算/审题”这类可行动的错因分布；错因可检索、可撤销；讲题历史可回溯。错因是“错题本→讲题→判定→档案”闭环中仍缺的一块语义层（不是唯一缺项）。

**依赖**

- **3D / D-1**：自动写错因在 D-1 过门前只能“模型建议 + 学生确认”，不能自动落账（需求 `:73`）。3D 整体未收口，所以 5c 真实启用必须挂起。
- **AUD-11**：判词粒度/答案键反例；错因不能拿“懂了”当“做对了”（`docs/REFACTOR-MASTER-LINE.md:148`）。
- **PRD-02**：答案键纠错/撤销/重放（已设备过，但 5c 要复用其证据语义）。
- **D-4**：前置补救覆盖，非硬门（`docs/REFACTOR-MASTER-LINE.md:97`）。
- **schema 70（预计；当前 69，开工前按台账占号）**：新增错因表/列，必须在阶段 7 迁移链重置之前完成；不能绕过迁移矩阵。

**最小落地范围（建议再拆两片）**

- **5c-1（本地、无模型）：** 结构化错因词表 + 题级标签的本地写/读/检索/撤销：
  - `core/model` 新 `MistakeCause`（词表值需产品裁定，不擅自定）；
  - `core/database` 新表/DAO/迁移（`StudyDatabase.kt`、schema 70（预计）、`MistakeDetailDao` 族）；
  - `core/domain/MistakeDetailRepository.kt` 增加读/写/撤销命令；
  - `core/data/.../mistake/RoomMistakeDetailRepository.kt` 实现；
  - `feature/library/MistakeDetailRoute.kt`（详情选择/撤销）+ `LibraryRoute`/`LibraryToolbar`（筛选）；
  - 备注原文不进网络；错因不写掌握证据。
- **5c-2（模型建议/自动写）：** 新 `ModelTaskKind` + 输入/输出 + 提示词 + egress 披露 + `feature/library` 确认卡 + 审计/撤销；D-1 前只建议、不自动写。
- 不建议在 5c-1 就改 `feature/tutor` 的讲题链；多解/订正摘要属 5c-3，可后置。

**验收判据**

- 错因可检索/筛选/聚类（至少有 facet/列表视图）；可撤销/驳回；撤销后不残留“已写”事实。
- 备注原文不出网（grep 无 egress 路径）；错因标签不写 `learner_*` 掌握证据；D-1 前自动写开关必须默认关闭，模型建议必须经学生确认。
- 迁移矩阵、备份/恢复、撤销幂等、跨会话读取均有测试；真实模型质量 UNVERIFIED；3D 未过时不得开放真实数据自动写。

**与热区他线的关系**

- 几乎全覆盖：`feature/library`（29 M）、`feature/tutor`（37 M）、`core/domain`（4 M）、`core/database`（8 M）、`core/data`（10 M）、`app`（23 M）。
- 需要一个新 schema（70），而阶段 7 迁移链重置必须等所有 schema 改完（`docs/REFACTOR-MASTER-LINE.md:99`）。这是它必须排最后的硬理由之一。

---

### 3.5 阶段 6 档案与设置

**读码/记录事实**

- 现有“我的”页：`feature/profile/ProfileRoute.kt:91-100` 展示薄弱点与设置区；`WeaknessSection` `:252-282`，`WeaknessRow` `:285-349` **没有 onClick**（只能看，不能点进复习）。
- 学习掌握页：`app/LearningMasteryScreen.kt:50-110` 只把 `overview.weaknesses + strengths` 去重后展示（`:60`），按科目分组（`:98-108`）；空态区分“准备中/无记录”（`:124-133`）。没有“未练习/盲区”桶，也没有全库支持范围。
- 数据：`StudyProfileOverview` 只有 `hasLearningEvidence`/`recordedAttemptCount`/`newlyMasteredCount`/`weaknesses`/`strengths`/`projectionIsCurrent`（`core/domain/.../StudyExperienceRepository.kt:190-198`）；映射 `StudyExperienceMappers.kt:135-204` 只从 `knowledgeMasteryStates` 派生，未练节点没有条目。
- 已有的 `StudyKnowledgeCoverageOverview`（`core/domain/.../StudyKnowledgeCoverage.kt:49-82`）是**知识包内容覆盖**（审阅科目/主题/原子知识/来源），不是“学生练过/没练过”的画像；不能拿它冒充学情。
- 需求 R8：H2“全库/盲区画像未完成（AUD-12）”、H3“已有弱点展示；行不可点进复习，全库盲区尚未纳入（AUD-12）”；H9 雷达/H11 周报/I2 进度环均未开工（`docs/requirements-fused-final-2026-10-11.md:165-170`）。
- 总路线阶段 6：AUD-12 扩全量画像与薄弱点复习出口；R8 雷达/周报/进度/内核自检；只读档案与设置总闸（`docs/REFACTOR-MASTER-LINE.md:78`）；审计验收“无学习事实不伪造掌握；可解释支持范围；只读行导航到对应复习，不直接改事实”（`:149`）。
- 新阶段 spec 明确改动点：`app/LearningMasteryScreen`、根导航、`StudyExperienceMappers`；无材料/不支持科目给明确出口；四科外降级（`docs/research/2026-10-11-new-stages-spec.md:196-200`）。

**用户可见价值**

- 学生能看到“我练过哪些、哪些完全没练、哪些科目暂不支持掌握分析”，而不是只看到已有掌握状态；薄弱行能直接进入该知识点的复习，不用自己再找。

**依赖**

- **AUD-12**；阶段 5 闭环（5a/5b）+ 3E 扫描（`:149`）；真实复习动作受 3D 硬门。
- 只读画像本身可以在 5a 已落地后先做；但“点进行动”依赖定向复习入口（见 §5.5 的设计裁定），以及 3D 对真实写入的许可。

**最小落地范围**

- `app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt`（M）：全库/未练/不支持三桶；保留现有摘要与科目分组。
- `feature/profile/src/main/java/.../ProfileRoute.kt`（M）：`WeaknessSection` 行加 `onClick`；`WeaknessRow` 加 `Modifier.clickable` + 语义；设置区 `SettingsSection`（`:351`）保持，不重建。
- `core/domain/.../StudyExperienceRepository.kt`（M）：`StudyProfileOverview` 增加只读的 `unseenKnowledgeCountBySubject` / `unsupportedSubjects` 或 `knowledgeCoverage` 读面；不要伪造 mastery。
- `core/data/.../study/StudyExperienceMappers.kt`（干净）：从知识目录/可用性派生未练/不支持；保持现有 weaknesses/strengths 逐位不变。
- `app/SmartMistakeBookRoot.kt`/`SmartMistakeBookDestinations.kt`（M）+ `SmartMistakeBookRoutes.kt`（干净）：弱点行 → 知识点复习的目标路由/参数。
- `feature/review`（M）：若采用“按节点定向计划”，可能需要 `currentKnowledgeReviewPlan` 的节点参数或新 API；若只导航到今日计划，则不改。
- 测试：`feature/profile` 已有 `LearningMasterySubtitleTest`（M）可扩；`app` instrumented 覆盖点进行；mapper JVM 覆盖未练/不支持/四科外。

**验收判据**

- 有记录/无记录/目录更新或退役/知识点无绑定题/四科外降级/返回上下文均有明确呈现（新阶段 spec `:200`）。
- 未练节点显示“未练习”，不显示 0% 掌握或伪造趋势；不支持科目显示“暂未支持掌握分析”一类明确文案（D-Q6-2 文案尚未落地，属本项的一部分）。
- 薄弱行可导航到该知识点的既有复习入口；无材料/无绑定题有明确出口；档案只读，不直接写评分/掌握。
- 既有保持率/考试/提醒设置保留（`:200`）；不把 H9/H11/I2 一起塞进本项。

**与热区他线的关系**

- `feature/profile/ProfileRoute.kt` **M**（3 M，基线 `:62`；本会话 `git status`）；注意 `feature/profile` 不在共享纪律声明的四模块热区里，但**确实脏**，不能按“干净”处理。
- `app/LearningMasteryScreen.kt`、`Root.kt`、`Destinations.kt` 均 **M**；`app` 23 M（基线 `:63`）。
- `core/domain/StudyExperienceRepository.kt` **M**；`core/data/StudyExperienceMappers.kt` 干净。
- `feature/review` 11 M；若做定向复习，必须再进热区。
- 结论：阶段 6 不是“app 一个文件”的活，至少跨 app + profile + core/domain + review；必须等这些面清空。

---

### 3.6 6B-3 app 注入

**读码/记录事实**

- 端口已存在：`feature/tutor/.../TutorPaperEditAssistant.kt:25`（`commandsFor(paper, instruction): String`）；提示词与白名单声明在 `:35` 起，`MAX_INSTRUCTION_CHARS=2_000`（`:37`）、`MAX_PROMPT_CHARS=12_000`。
- UI 已存在：`feature/tutor/.../TutorExportEditSheet.kt:63` 有 `modelEditAvailable`；`:120` 起是“用一句话改卷面”区，null 端口时整块不渲染。
- ViewModel 已存在：`TutorConversationViewModel.kt:131` 接收 `paperEditAssistant`，`:612` 起走“调用端口 → `TutorPaperEditApplier` 严格解析 → 原子应用”；失败保留草稿。
- 工厂/路由已留参数：`TutorConversationViewModelFactory.kt:40`、`TutorLobbyRoute.kt:105/:126`、`TutorRoute.kt:112/:138`。
- **app 未注入**：`SmartMistakeBookRoot.kt:562` 与 `:652` 两处 `TutorRoute(...)` 都没有 `paperEditAssistant` 参数（本会话 `grep`/读段），所以生产入口永远 `modelEditAvailable=false`。
- 模块边界：`core/data/build.gradle.kts:169-171` 只依赖 `core:model/core:domain/core:database`，**不依赖 `core:export` 或 `feature:tutor`**；`feature:tutor/build.gradle.kts:29-33` 依赖 `core:export`；`app/build.gradle.kts:157-168` 依赖 feature 与 core/export。因此 `core:data` 的模型适配器无法直接引用 `MistakePdfPaperEdit` 或 `TutorPaperEditPrompt`。
- 现有任务类型：`core/model/.../ModelTasks.kt:12-25` 没有“改卷”任务；适配器 prompt 分发 `OpenAiModelTaskAdapters.kt:44`；egress 披露 `ModelEgress.kt:88-105`；provider 能力集 `OpenAiCompatibleModelGateway.kt:552-560`。
- **已有可复用层：** 白名单命令模型/解析器已在 `core/model`（`TutorPaperEditApplier.kt:5-6` import `PaperEditCommand`/`PaperEditCommandParser`），不在 feature；所以新任务只需把“有界卷面文本 + 指令”送进 `core:data`，命令 JSON 解析仍复用既有 `PaperEditCommandParser`。真正缺的是“卷面 DTO → 文本”的桥（`MistakePdfPaperEdit` 在 `core:export`）与提示词模板归属。
- 总路线 6B 行：6B-2 原生编辑入口已完成（批次 16 设备 8/8）；6B-3 白名单/解析/原子 applier JVM 通过，**模型端口未在 app 注入，真实模型 UNVERIFIED**（`docs/REFACTOR-MASTER-LINE.md:79`）。决策台账也记录“6B-3 仍未注入真实模型端口”（`:3036`）、“下一批：6B-3 app 注入”（`:3025`）。

**用户可见价值**

- 学生不用逐题点按钮，用一句话就能删题/调序/改文字/改卷面备注；只改导出副本，不影响错题本。对高频组卷/打印的学生是明显省力。

**依赖**

- 4B/6B-1（docx 导出）与 6B-2（原生编辑）已完成，不依赖 3D/3E。
- 依赖真实 Provider 才能有用户可见价值；当前无凭据，真实质量 UNVERIFIED。
- **架构前置（未完成）：** 新任务类型/提示词/文档 DTO 的归属裁定。推荐方案见 §5.6。

**最小落地范围（按推荐方案 A）**

- `core/model/.../ModelTasks.kt`：新增 `ModelTaskKind.PAPER_EDIT`（`:12-25`）。
- 新 `core/model/.../PaperEditTasks.kt`：`PaperEditInput`（指令 + 有界卷面文本/摘要 + 版式摘要）与 `PaperEditOutput`（原始命令 JSON 或结构化命令列表）；输入需 strip 空载体、加 4 用例（项目硬纪律）。
- `core/data/.../model/OpenAiModelTaskAdapters.kt`：prompt 分发（`:44`）与 parse 分支；提示词模板放 `core/model`（不能放 feature）；`PaperEditOutput` 解析后仍交给 feature 的 `TutorPaperEditApplier` 严格校验。
- `core/model/.../ModelEgress.kt`（`:88-105`）与 `OpenAiCompatibleModelGateway.kt:552-560`：新增 kind 的披露/能力声明；必须决定它归 `TUTORING` 还是新 purpose（新 purpose 会扩 manifest 语义，风险更高）。
- 新 `app/src/main/kotlin/com/tingyun/smartmistakebook/PaperEditModelAssistant.kt`：实现 `TutorPaperEditAssistant`，通过 `ModelTaskRepository.execute` 派发一次；不得直接调 `ModelGateway` 绕过 ledger。
- `app/SmartMistakeBookRoot.kt` 两处 `TutorRoute` 增加 `paperEditAssistant`（`:594` 与 `:682` 附近）。
- 测试：`core/model` 契约测试；`core/data` MockWebServer/适配器测试；`feature/tutor` JVM（已有 applier/parser）；`app` instrumented 用 fake 端口验证“有端口显示、无端口隐藏”。
- 不建议改 `TutorExportEditSheet` 的 UI 结构；它已能显示/隐藏。

**验收判据**

- 未注入 → 编辑态不显示“用一句话改卷面”（与 PRD-06“不广告未接线能力”一致）；注入 → 显示，且一次点击只发一次逻辑派发。
- 模型输出代码块/解释/截图 URL/越界 entryId 一律整批拒绝，草稿保持原样（已有 `TutorPaperEditApplier` 契约）。
- 失败/超时保留上一有效卷面；不写回 `error_book_entry`；不改图。
- fake 端口 + MockWebServer 可验；真实 Provider 质量/成功率 UNVERIFIED，不得写成已验收。
- 预算：每次改卷是一次独立逻辑操作；不得偷用讲题终答预算或绕过 6 次派发上限。

**与热区他线的关系**

- `feature/tutor/TutorExportEditSheet.kt`、`TutorConversationViewModel.kt` **M**；`feature/tutor` 37 M + 21 ??。
- `app/SmartMistakeBookRoot.kt` **M**；`app` 23 M。
- `core/model/ModelTasks.kt`、`ModelEgress.kt`、`core/data/OpenAiModelTaskAdapters.kt` **干净**（本会话 `git status`）；但 `core/data` 模块整体脏，且 `core/data` 的 `OpenAiNativeToolsProtocolTest.kt`/`TutorToolRequestDualParseTest.kt` 为 M（他线）。
- 结论：6B-3 可以拆成“core/model + core/data 的干净半边”先做，但真正用户可见必须等 `feature/tutor` + `app/Root` 清空。新 kind 会牵动多处枚举/披露，风险高于 G1/G2/AWAITING。

---

### 3.7 AUD-08 切分金标门

**读码/记录事实**

- 金标 v1：`tools/split_golden/tables/split_golden_v1.json`，7 页/16 框；`evidenceGrade = "model-labeled, no human expert; two independent labelers agreed"`（本会话 `python` 实读）；旁车 sha `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f`。
- 校验器：`tools/split_golden/validate_split_golden.py:1-50`，C1–C6 机械校验；`MIN_PAGES=7`（`:61`）。
- 度量脚本：`tools/split_golden/measure_split_accuracy.py:1-40`，门 `① countExactRate ≥ 0.9`、`③ fragmentRate ≤ 0.05`；② IoU 只度量不设门；输入默认 `build/split-eval/app-split.json`（`DEFAULT_APP`，`:53`），应用导出缺失 exit 3。
- 口径 v2：单题金标页“不拆分即正确”；修正后 `countExactRate = 4/7 = 0.5714`，`fragmentRate = 0/16`，5/7 页无应用输出（`docs/research/2026-10-14-4c-stage1-record.md:102-130`、`:220-230`；总路线 `:72`）。
- 应用导出：`build/split-eval/app-split.json` 是设备 DB 快照（7 页 8 框，1482 B），但仓内**没有生产者脚本**；`tools/split_golden` 只引用该路径（`measure_split_accuracy.py:34/:53`），4C 记录也只描述“应用输出确取自设备 DB”（`:102`）。
- 自动入库：`grep "autoIngest|自动入库|accuracyGate|splitGate|gateEnabled"` 于 `feature/ core/ app/src/main` 只命中一条注释（`feature/capture/.../CaptureAcquisitionCommands.kt:161`），没有门控开关/自动入库实现；当前仍需用户逐题确认（4C 记录 §4）。
- 失败页出口已存在：`CaptureModelTaskCard.kt:116-124`“题目拍摄不完整，请补拍后续页面…继续补拍本题”；`CapturePendingCaptures.kt:123-124`“重新拍摄”。
- 审计验收：门说明、数据集与逐题裁定可复跑；切残不得自动入库；失败页可补拍/继续（`docs/REFACTOR-MASTER-LINE.md:145`）。门后自动化未启用（`:72`）。

**用户可见价值**

- 现在没有“自动入库”，所以直接影响是间接的：它保证未来开启自动入库时，切残题不会静默进库；失败页可补拍。对 beta 的价值是“安全门”而非新功能。

**依赖**

- AUD-03 已修法落地并设备 3/3（`docs/REFACTOR-MASTER-LINE.md:140`），不阻塞。
- 4C 链已在；无 3D/3E 依赖。
- 重测需要真实模型/设备 DB 与标注；真实 Provider 质量 UNVERIFIED。金标当前是模型标注、无人工专家，这是证据等级问题，不是代码问题。

**最小落地范围**

- **工具半边（现在可做，干净）：**
  - 新 `tools/split_golden/export_split_eval.py`：从设备 DB（`split_import_question`/草稿/页表）导出 `build/split-eval/app-split.json`，带 schema 校验、sha、页序、重复文件拒绝；只读、不联网。
  - 新 `tools/split_golden/README.md`：记录设备命令、DB 查询、导出格式、与 `measure_split_accuracy.py` 的对接、sha 对照。
  - 可选：`measure_split_accuracy.py` 增加 `--json` 逐页裁定输出（不改门值/口径）。
- **数据半边（需产品/研究裁定）：**
  - 是否扩充 `split_golden_v2.json`（新文件，v1 不改）并从“模型双标注”升级为“人工专家双标注”；若不做，必须在门说明里保留 `model-labeled` 证据等级，不能写成人工金标。
  - 是否用当前真实 Provider 重跑 7 页/新页，拿到新的 `app-split.json`；若没有稳定 Provider，AUD-08 只能保持“门 false + 自动化关闭”。
- **失败页：** 现有出口已存在；只需在设备/UI 测试中证明“切分失败页可补拍/继续”，无需重建。若发现缺口，才改 `feature/capture`（M）。

**验收判据**

- `python tools/split_golden/validate_split_golden.py` exit 0（本会话未跑，4C 记录曾 PASS）。
- `python tools/split_golden/measure_split_accuracy.py` 同输入逐字节一致；应用导出缺失 exit 3；门 false 时明确打印“门后自动化不启用”。
- 逐页裁定表可复跑并落盘；金标 sha、应用导出 sha、设备/DB 版本、命令、exit code 齐全。
- 切残不入库：自动入库路径不存在或默认关闭；逐题确认仍是唯一入库动作；`isUsableSplitRegionSet()`（`CaptureWorkflowRepository.kt:439`）仍拒绝 1 块/13+ 块和重复区域。
- 失败页可补拍/继续（`CaptureModelTaskCard.kt:116-124`）。
- 不得为了“门变绿”改阈值/口径/金标分母；若要改，必须先裁定并留旧值对照。

**与热区他线的关系**

- `tools/split_golden` **干净**（本会话 `git status` 空）→ 工具半边**不需要热区清空**，这是七项里唯一可立即推进的。
- `feature/capture` 15 M + 1 ??；`CaptureScreen.kt` M。失败页复核/设备重测才需要等 capture 线。
- 设备/真实模型：当前无凭据，重测只能 UNVERIFIED 或引用既有记录。

---

## 4. 热区清空后的建议顺序

### 4.1 排序依据（价值 / 依赖 / 风险）

> 评分 5 = 最好/最高，1 = 最差/最低；“依赖就绪”5 = 现在就能做，1 = 多重硬门未过。

| 项 | 用户价值 | 依赖就绪 | 风险/改动面 | 热区耦合 | 综合建议 |
|---|---:|---:|---:|---|---|
| G2 App 内图角标 | 5（P0 合规） | 5（无前置） | 5（1 文件 + 测试） | core/ui | **第 1** |
| G1 讲题 AI 提示 | 5（P0 合规） | 5（无前置） | 4（2 文件 + 共享件） | feature/tutor + core/ui | **第 2** |
| AWAITING UI | 4（消除可见死路） | 5（无前置） | 3（3–4 文件，状态语义） | library/domain/data | **第 3** |
| 5b-1 / KD-68 | 3（诚实，不新增能力） | 5（数据侧已落） | 5（1 文件 + 测试） | feature/review | **第 4** |
| AUD-08 工具+收口 | 3（安全门/未来自动化） | 4（工具干净；设备/模型待） | 4（工具/度量，不改产品逻辑） | tools + capture | **第 5（工具可提前到第 0）** |
| 6B-3 注入 | 3（组卷省力） | 3（架构裁定 + 真实 Provider） | 2（新任务类型/披露/多模块） | tutor + app + core/model | **第 6** |
| 5b-2 F8/F9/F18 | 4（每日节奏） | 2（3D + 产品值未裁） | 2（设置/planner/可能 schema） | review + app | **第 7** |
| 阶段 6 档案 | 4（全库画像/行动） | 2（5b/3E + 3D + 定向复习） | 3（app/profile/domain/review） | 多模块 | **第 8** |
| 5c 错因 | 4（个性化诊断） | 1（D-1/AUD-11/schema 70） | 1（几乎全模块） | 几乎全部 | **第 9** |

**排序逻辑：**

- 前四项都是“低风险、依赖已就绪、直接可见”，先做能立即降低 beta 风险并清掉已知死路。
- AUD-08 工具半边可以在热区清空前做；产品收口排在第 5，是因为它需要设备/模型，且不改用户功能。
- 6B-3 排在 5b-2 前，不是因为它价值更高，而是因为它不依赖 3D/3E；5b-2 要等 3D 与产品裁定。若 3D 先收口，5b-2 应前移到 6B-3 前。
- 阶段 6 依赖 5b/3E 的闭环与定向复习；5c 依赖 D-1 与 schema 70，且几乎覆盖所有热区，必须最后。

### 4.2 推荐波次（热区全部清空后，串行）

| 波次 | 内容 | 为什么现在 | 退出门 |
|---|---|---|---|
| **0（现在可做）** | AUD-08 工具链：`export_split_eval.py` + README（+ 可选 `--json`） | `tools/split_golden` 干净；不碰产品代码 | validator PASS；measure 可复跑；文档命令/exit/sha |
| **1** | G2 → G1 | P0、最小、无前置；G2 只 core/ui，G1 只 tutor+ui | 角标在持久/流式/图卡；不在学生/失败卡；TalkBack；不得声称全合规 |
| **2** | AWAITING UI 态 | 数据层已落，UI 映射是明显 dead-end | 独立态；重新核实/放弃；不自动重发；设备杀进程场景 |
| **3** | 5b-1 / KD-68 | 一文件修复，直接消除“约 M 天安排完”的误导 | `ReviewIntakeBacklogPreviewInstrumentedTest` 覆盖不可调度；旧例逐字不变 |
| **4** | AUD-08 收口 | 工具已就绪；需要设备/标注决定 | 门 false 保持；自动入库关闭；失败页可补拍；逐页裁定落盘 |
| **5** | 6B-3 注入（先一页架构裁定） | 独立于 3D/3E；有干净 core/model/core/data 半边 | fake 端口可见/隐藏；白名单/原子；真实 Provider UNVERIFIED |
| **6** | 5b-2 F8/F9/F18 | 需产品裁定；3D 未过只能受控开发/隔离测试 | 预算/名额/追赶/周末模式各自金样；真实启用须 3D |
| **7** | 阶段 6 档案 | 依赖 5b/3E 闭环与定向复习 | 未练/不支持/四科外；行可点；只读；不伪造 |
| **8** | 5c 错因 | D-1 自动落账门 + schema 70；最后做 | 本地标签/检索/撤销；D-1 前只建议；迁移矩阵；备注不出网 |

### 4.3 条件分支

- **若只有 `core/ui` 清空：** 先做 G2；G1 等 tutor。
- **若只有 `feature/library` + `core/domain` 清空：** 先做 AWAITING；G1/G2 等各自热区。
- **若 `feature/review` 先清空：** 5b-1 可以紧跟 AWAITING，不需要等 3D（它只改文案/预览，不改真实学习写入）。
- **若 3D 先收口：** 5b-2 前移到 6B-3 之前；阶段 6 也可与 5b-2 同批（都在 review/app/profile）。
- **若真实 Provider 一直不可用：** 6B-3 只能做到“fake 端口可见 + 真实质量 UNVERIFIED”；AUD-08 只能保持门 false；5c 的模型建议只能做本地隔离测试。不要把 UNVERIFIED 写成完成。

### 4.4 不建议的排法

- **不要把 G1/G2 与 G5/G7 合成一批。** G5 要动 `SecondaryScreens.kt`（M）与 Root（M），G7 还要设置页/时长提醒；会把 P0 小改拖成跨热区大改（`docs/ai-content-disclosure-copy.md:62-67`）。
- **不要先做 5c。** 它需要 schema 70、D-1、AUD-11，且几乎覆盖全部热区；先做会把整个工作树再拖脏一遍。
- **不要为了 AUD-08 门变绿改阈值/金标。** 脚本明确“阈值不动”，改口径必须另立预注册与旧值对照（`measure_split_accuracy.py:1-40`）。
- **不要把 5b-1 当成 5b 完成。** KD-68 只是诚实预览；F8/F9/F18 与真实数据验收仍在。

---

## 5. 每项一页设计骨架

> 以下只给设计骨架，不实现；每页包含“入口/状态/数据/交互/失败/验收/不做”。所有新数值/词表均标“需裁定”，不擅自取值。

### 5.1 G1/G2 显式标识（一页）

**目标：** 在讲题回复与 App 内生成图旁给出不可关闭的“AI 生成”显式标识，满足《标识办法》第四条交互界面义务；不改变任何判据。

**入口/位置：**

- G1：持久回复 `TutorSurfaceAssistantReply` + 流式 `TutorLiveTurnBlock`；每条助手正文尾部（或正文上方）一个小角标，避免顶部横幅滚出视野。
- G2：`AttachedImageCard` 与 `GeneratedFigureCard` 的 READY 态；折叠行标题旁 + 展开图角落。
- 共享组件：`AiGeneratedBadge(text = "AI 生成", emphasized = false, onClick = 显示一句话说明)`；G7 未成年人场景用 `emphasized = true`（加粗/前置）。

**状态：**

- 只对“AI 生成内容”显示；学生消息、思考卡、工具痕迹、失败卡、状态行不显示。
- G2 只在 READY；GENERATING/FAILED 已有状态语义，不重复标。
- 不可关闭、不可移除；无设置开关（`docs/ai-content-disclosure-copy.md:52`）。

**数据：**

- 不新增模型字段/数据库列；G1 的“是否 AI”由渲染路径本身决定（助手回复恒为 AI）；G2 由卡类型决定（这两张卡都是生成图）。若未来有非 AI 图卡，增加显式参数默认 false，避免误标。

**交互/无障碍：**

- 点击/长按角标弹出 §1 一句话版全文（`docs/ai-content-disclosure-copy.md:10-12`、`:55`）。
- TalkBack 读“AI 生成，本内容由人工智能生成”；角标本身不是装饰性无语义 Text。

**失败/边界：**

- 流式期间正文为空：角标可先不显示，正文出现即显示；不把空状态标成 AI 内容。
- 历史消息/旧数据：不需要迁移；渲染层统一添加。
- 导出链：G3 已做，不在本页；PDF 隐式标识 KD-75 另条。

**验收：**

- 新增/修改的 UI 测试断言角标存在/不存在；设备截图；TalkBack 走查。
- 不得声称“应用内已合规”：G5/G7/KD-75 未齐。

**不做：**

- 不做图片内水印（5% 规格待验证，R3 §7 V1）；不改模型提示词；不改导出链；不做设置开关。

---

### 5.2 AWAITING_VERIFICATION UI 态（一页）

**目标：** 把 `clean_redraw_task` 的“结果未知”终态映射成学生可理解的独立状态，并提供本地核实/显式放弃，不自动重发、不重复付费。

**入口/状态：**

- 详情页显式重绘区（`MistakeDetailRoute.kt:534`）。
- 新 `ExplicitRedrawPhase.AWAITING_VERIFICATION`；`ExplicitRedrawState` 增加 `canRecheck`/`canAbandon`。
- 文案：“上次重绘结果未确认（生成请求已发出，进程中断）。原图仍可用。”；两个动作：“重新核实”“放弃这次重绘”。

**数据/语义：**

- `toExplicitRedrawState()` 增加 AWAITING 分支，不再落 `Idle`。
- `recheck(taskId)`：只读本地证据——当前修订是否有 `CLEAN_IMAGE` 视觉引用且其 operation/attempt 与 `operationId` 对上；有 → CAS 写 SUCCEEDED；无 → 保持 AWAITING，提示“仍无法确认”，不派发。
- `abandon(taskId)`：写显式终态（建议 `ABANDONED`；加入 `TERMINAL`）；不删除原图，不改题面；不自动重发。
- 若允许“放弃后重新发起”：必须用新操作身份（new taskId/operationId），不能复用同 taskId；这是需裁定的语义（见 §6）。

**交互：**

- AWAITING 态下隐藏“重绘干净题面”按钮，只给两个动作；避免“点了必然失败”。
- “重新核实”进行中禁用；结果用一行状态文本，不弹内部枚举名。
- “放弃”二次确认（不可逆终态）；确认后显示“已放弃这次重绘，原图保留”。

**失败/边界：**

- 无本地产物 + 无执行者：只能放弃或保持待核实；不得静默补发。
- 进程再次死亡：状态仍在；重开详情页可再次核实。
- 旧数据（无 AWAITING 行）：行为不变。

**验收：**

- JVM：映射、幂等、recheck 有/无产物、abandon 终态；设备：杀进程 → 重开 → 核实/放弃；UI 测试：不出现 `AWAITING_VERIFICATION` 原始串。
- 不新增 schema；若加 ABANDONED，仅字符串取值。

**不做：**

- 不做自动重试、不做 Provider 幂等查询、不改 `CleanRedrawTaskRunner` 的领取/对账规则（仍不接手 AWAITING）。

---

### 5.3 5b 知识点复习（一页）

**目标：** 在已有知识点复习入口/会话/3E 队列之上，补齐“每天安排多少、积压怎么诚实呈现、休假/周末怎么选”的节奏层；不重建已有链。

**入口/状态：**

- 入口：`ReviewRoute` 第二卡（已有）。
- 会话：`KnowledgeReviewSessionScreen`（已有）；取题/恢复/提交/跳过/无材料出口（已有）。
- 5b-1：积压预览 `IntakeBacklogPreview` 增加 `intakeUnschedulableCount` 分支。
- 5b-2：设置页“复习节奏”区（F8 预算、F9 追赶、F18 周末模式）；知识点会话沿用同一预算。

**数据：**

- 5b-1：只读 `StudyReviewOverview.intakeUnschedulableCount`（已存在）；不改 planner。
- 5b-2：`reviewTimeBudgetSeconds` 目前是构造参数默认 20 分钟（`RoomBackedStudyExperienceRepository.kt:1113`）；需要 DataStore 设置源与范围/档位（需裁定）。F9/F18 若改 planner，必须先写金样；F17 若要“真每日累计”，需要持久账本/schema，另立设计。
- 不伪造掌握：跳过不写证据；无材料明确提示。

**交互：**

- 5b-1 文案：
  - 不可调度 = 0：保留“还有 N 道新题待学：每天约 X 题，约 M 天安排完”。
  - 不可调度 > 0：“还有 N 道新题待学；其中 Y 道超出每日预算，需专项会话安排”，不再给全量天数承诺。
- 5b-2 设置：预算选择（档位/自定义，需裁定）；追赶开关（默认关，需裁定）；周末模式（默认关，需裁定）；保存后下一次计划生效，不重排今日已开始会话。
- 会话：跳过/无材料/失败出口保持；跨会话再次作答不丢。

**失败/边界：**

- 3D 未过：真实数据启用关闭；只能隔离测试/受控开发。
- 全超预算：不崩溃（KD-64 已修），显示“需专项会话”；不得显示“约 M 天”。
- 知识内容未就绪：已有“准备中”门（Root `:805/:812`），保留。
- 无绑定题/无材料：跳过或明确出口，不伪造题目。

**验收：**

- 5b-1：`ReviewIntakeBacklogPreviewInstrumentedTest` 新增不可调度例；旧例逐字不变。
- 5b-2：planner JVM 金样（预算/追赶/周末各自开/关）；设置 instrumented；设备有数据全链；真实模型质量 UNVERIFIED。
- 3E：`StudyReviewPlanner3EScanInstrumentedTest` 仍 3/3；KD-64 不回退。

**不做：**

- 不重建 `KnowledgeReviewQueue`/`KnowledgeReviewSessionScreen` 主体；不改 3E 范围；不把 F18 做成默认节奏；不新造掌握证据。

---

### 5.4 5c 讲题/错因（一页）

**目标：** 把“错因”从自由文本升级为可检索、可聚类、可撤销的结构化标注；自动写只在 D-1 过门后开放，过门前只做建议+确认。

**入口/状态：**

- 5c-1：详情页“错因”区（替换/并列现有“错因 / 备注”自由文本）；列表筛选 facet；讲题历史检索（E11）后置。
- 5c-2：模型建议卡（来自讲题/判题证据），学生确认后写；D-1 过门后才可自动落账。

**数据：**

- 新错因词表（需产品裁定；需求只给了“计算/审题”示例，不是冻结枚举）。
- 新表/列（schema 70（预计；当前 69））：题级错因、来源（用户/模型）、置信/证据引用、撤销状态、时间；备注原文不出网。
- 错因不写 `learner_*` 掌握证据；不改变 `ReviewPlanner` 的判据；撤销走显式状态，不物理删除历史。

**交互：**

- 详情：选择/多选错因 + 保存；撤销/驳回；显示来源与时间。
- 列表：按错因筛选/聚类计数（“我总栽在计算/审题”）；不伪造统计，样本不足显示“样本少”。
- 模型建议：显示“AI 建议”与依据；学生确认才写；可一键驳回。

**失败/边界：**

- D-1 未过：自动写默认关闭；模型建议不得直接落库；UI 不得写“AI 已判定”。
- 无证据：不强行写（需求 C10 “找不到证据不强行”）。
- 备注：保持私有，不进入任何模型输入/导出；`userNote` 路径不变。
- 迁移：schema 70（预计）非破坏迁移；备份/恢复/迁移矩阵；阶段 7 迁移链重置最后做。

**验收：**

- 本地标签 CRUD/检索/撤销/幂等；DB 迁移矩阵；备份恢复；UI 筛选；设备有数据。
- 自动写：D-1 过门前的“建议+确认”测试；过门后的自动写需另立开关与审计；真实模型质量 UNVERIFIED。
- 备注不出网：grep/egress 测试。

**不做：**

- 不在 5c-1 做多解/订正摘要/讲题历史全文检索（5c-3）；不把错因写进掌握度；不删旧备注。

---

### 5.5 阶段 6 档案与设置（一页）

**目标：** 把已有学情页扩成“全库、只读、可行动”的档案：已练/未练/不支持分开，薄弱行可进对应复习；不伪造统计。

**入口/状态：**

- “我的”页：薄弱点行可点；设置区保留（不重建）。
- 学习掌握页：三桶——已练（weaknesses/strengths）、未练（知识目录中有、无学习事实）、不支持（四科外/知识内容未就绪）。
- 设置总闸：沿用 `SettingsSection`（`ProfileRoute.kt:351`）；G5 “AI 生成内容说明”入口可在此后补。

**数据：**

- `StudyProfileOverview` 增加只读计数/条目：`unseenKnowledgeCountBySubject`、`unsupportedSubjects`、`knowledgeBaseAvailability`（或复用 `StudyKnowledgeCoverageOverview` 的支持范围，但不把它当学情）。
- `StudyExperienceMappers` 从知识目录/可用性派生；无 mastery 的节点不填 0 分，显示“未练习”。
- 不新增写入路径；档案只读。

**交互：**

- 未练行：可点“开始这个知识点的复习” → 定向复习（见下）。
- 薄弱行：点进该知识点的复习；若无材料/无绑定题，显示“暂无可出题材料”并返回。
- 不支持科目：显示“暂未支持掌握分析”（D-Q6-2 文案）；不显示 0%。
- 空态：无学习事实 → “还没有学习记录”；知识内容未就绪 → “掌握分析还在准备中”（已有，保留）。

**定向复习的裁定点：**

- 方案 A（推荐）：新增 `currentKnowledgeReviewPlanForNode(nodeId)` 或路由参数，生成只含该节点的计划；优点：行为确定；缺点：需要 domain API + feature/review + Root。
- 方案 B：导航到今日计划并提示“该知识点已加入/未加入”；优点：改动小；缺点：不能保证点到即复习。
- 本页不擅自选；需产品裁定。

**失败/边界：**

- 3D 未过：只读画像可显示；真实复习动作仍受门控。
- 知识包更新/退役：节点消失时显示“知识点已更新”，不伪造。
- 四科外：明确“暂未支持”；不把五科混算。
- 设置项保留：保持率/考试/提醒/备份入口不搬走、不改语义。

**验收：**

- mapper JVM：有/无记录、未练、四科外、目录退役；profile UI instrumented：行可点、空态、返回；设备走查。
- 不伪造：未练不显示 0% 掌握；无样本不显示趋势；只读不写。

**不做：**

- 不在本项做 H9 雷达/H11 周报/I2 进度环（未开工、无数据底座）；不重建设置页；不把 `StudyKnowledgeCoverageOverview` 当学情。

---

### 5.6 6B-3 app 注入（一页）

**目标：** 让“用一句话改卷面”在真实模型可用时出现，且仍走统一模型派发/预算/审计；不反写错题本，不改图。

**三种方案（需先裁定）：**

- **A（推荐）：** 新 `ModelTaskKind.PAPER_EDIT` + `core/model` 的 `PaperEditInput/Output`；输入只带“有界卷面文本 + 指令 + 版式摘要”，提示词模板放 `core/model`；`core:data` 适配器消费；app 适配器实现 `TutorPaperEditAssistant`，通过 `ModelTaskRepository.execute` 派发。命令词表/解析器已在 `core/model`（`PaperEditCommand`/`PaperEditCommandParser`），不需要再建一套。
- **B：** 复用 `TUTOR_LOBBY` 的输入/输出。**不推荐**：污染大厅语义/披露，且输出解析要分叉。
- **C：** app 直接调 `ModelGateway`，绕过 `ModelTaskRepository`。**不推荐**：绕开 ledger/预算/审计，违反统一派发纪律。

**入口/状态：**

- 编辑 sheet：`modelEditAvailable` 由端口是否注入决定；未注入不显示（已有）。
- 注入后显示“用一句话改卷面”+ 输入框 + 提示“AI 仅能移出/加回/调序/改文字；不能改图，不影响错题本”（已有文案）。
- 进行中禁用输入/按钮；成功显示应用了几条命令；失败保留草稿。

**数据/协议：**

- 输入：指令 ≤2,000 字；卷面文本 ≤12,000 字（现有 `MAX_PROMPT_CHARS`）；不含图片 URI/资产 id/备注以外的本地路径。
- 输出：白名单七命令 JSON；`TutorPaperEditApplier` 严格解析/原子应用；非法/越界整批拒绝。
- 披露：新 kind 的 `ModelEgress` 类别必须明确（建议 text-only 文档副本，不含题面图）；provider 能力集只在支持 structured output 时广告。
- 预算：一次改卷 = 一次逻辑派发；不得复用讲题终答预算；失败可重试但同一操作计数。

**失败/边界：**

- 模型返回代码块/解释/截图 URL → 整批拒绝，草稿原样。
- 超时/取消 → 保留上一有效卷面。
- 真实 Provider 不可用 → 入口隐藏或失败卡；不广告。
- 不写 `error_book_entry`；不生成新图；不改原题。

**验收：**

- 未注入：UI 无“AI 改卷”区；注入 fake：显示并可用；MockWebServer：一次派发、白名单解析、越界拒绝；设备：编辑→改卷→重渲染→分享。
- 真实 Provider：UNVERIFIED；不得写成已验收。

**不做：**

- 不做 DOCX 往返解析；不做图片生成/替换；不做自由文本命令；不绕过 `TutorPaperEditApplier`。

---

### 5.7 AUD-08 切分金标门（一页）

**目标：** 让切分准确率门可复跑、可审计，并在门未过时保持“自动入库关闭”；不把门值/口径当调参对象。

**入口/状态：**

- 工具：`validate_split_golden.py`、`measure_split_accuracy.py`、新 `export_split_eval.py`。
- 数据：金标 v1（7 页/16 框，sha `f9e92c47…`）+ 应用导出（`build/split-eval/app-split.json`）。
- 门：① ≥0.9、③ ≤0.05；② 只度量。门 false 时打印“门后自动化不启用”。

**数据流：**

1. 设备录入/切分产生 `split_import_question` 等行；
2. `export_split_eval.py` 从设备 DB 快照导出 `app-split.json`（页名/框/题数/来源），带 sha 与 schema 校验；
3. `measure_split_accuracy.py --golden ... --app ...` 输出逐页表 + 指标；
4. 结果落盘（研究记录/证据目录），不写回产品。

**交互/产品：**

- 当前没有自动入库；保持逐题确认（`CaptureModelTaskCard`/SplitImportReviewRoute）。
- 失败页可补拍/继续（已有 `继续补拍本题`）。
- 若未来开门后自动化：必须在门值 + 真机 + 逐题裁定全部满足后另立 PRD/裁定；本页不实现。

**失败/边界：**

- 应用导出缺失 → exit 3，不猜；
- 金标/应用页名不匹配 → 只对可比页计分，分母写死并打印；
- 单题页“不拆分即正确”口径不变；
- 不因门 false 改阈值/分母/金标；不把 0.5714 说成“接近通过”。

**验收：**

- validator PASS；measure 同输入逐字节一致；`export_split_eval.py` 在设备 DB 上可复跑并产出同 sha 的 JSON；逐页裁定落盘；门 false + 自动入库关闭；失败页设备测试。
- 金标证据等级：若仍为模型标注，必须保留 `model-labeled` 字样，不能写成人工金标；若要升级，另建 v2 + 人工双标注 + sha。

**不做：**

- 不做 4C③ 门后自动化；不改切分提示词/模型；不为了数字好看重采样金标；不改 `isUsableSplitRegionSet` 的 2–12 块红线。

---

## 6. 未验证、风险与需裁定

### 6.1 需产品/主会话裁定（不得自行取值）

1. **AWAITING 的“放弃”语义**：放弃后是否允许以新身份重新发起？若允许，新 taskId/operationId 如何生成、如何防止重复付费？若不允许，是否只能通过题面修订获得新身份？
2. **AWAITING 的“重新核实”证据源**：只查修订的 `CLEAN_IMAGE` 引用，还是也查 ledger attempt 产物？两者不一致时以谁为准？
3. **5b F8 预算形态**：档位/自定义、按日还是按会话、默认 20 分钟是否保留；需求要求“重构自定义类型，使其具有合理性”，没有冻结值（`docs/requirements-fused-final-2026-10-11.md:114`）。
4. **5b F9 追赶**：休假/积压后的追赶规则（天数、上限、是否影响新题引入）——现有的是考试 catch-up，不是休假 catch-up（`NewIntroductionPolicy.kt:105-140`）。
5. **5b F18 周末模式**：默认关、可选模式的具体行为；需求明确“模式不固定，可以选择，不做默认节奏”（`:119`）。
6. **5b F17 名额口径**：保留“每次排计划 ≤10”（KD-46 已有登记）还是做真每日累计（需持久账本/schema）。
7. **5c 错因词表**：分类维度/标签集/是否多选/是否允许自由文本；需求只给“计算/审题”示例，不是冻结枚举（`:69-70`）。
8. **5c 自动写许可**：D-1 过门后是否全自动，还是仍保留逐条确认；裁定 39 的触发点（`docs/agent-first-refactor-decisions-2026-09-23.md:2687`）。
9. **6B-3 任务归属**：方案 A/B/C（§5.6）；新 kind 的 egress purpose 与 provider 能力广告；提示词放 `core/model` 还是别处。
10. **阶段 6 定向复习**：方案 A（按节点新计划）还是 B（导航今日计划）；四科外“暂未支持掌握分析”文案的最终措辞（D-Q6-2 尚未落地）。
11. **AUD-08 金标证据等级**：接受“模型标注、无人工专家”并保留字样，还是投入人工专家双标注做 v2；是否用真实 Provider 重跑。
12. **G1/G2 形态**：每条回复尾部角标 vs 顶部常驻条；G7 未成年人加粗/前置的最终视觉；G5 设置入口是否同批（需 SecondaryScreens 干净）。

### 6.2 未验证 / 不能声称

- **3D 未收口**：D-0/D-0b/D-1/D-2/D-3/D-5 未同包全过；D-1 只是点估计 95.15%，不是总体下界（`docs/REFACTOR-MASTER-LINE.md:66`、需求 `:80`）。5b/5c/阶段 6 的真实数据启用均 UNVERIFIED。
- **真实 Provider 无凭据**：6B-3、5c 模型建议、AUD-08 重测、G1/G2 模型质量均无法在本会话验证。
- **5b 设备/真实数据**：KD-64 设备 3/3 是既有记录，本会话未复跑；5b 全链（真实库 + 真实模型）未验证。
- **5c 零代码**：本会话 grep 未发现结构化错因实现；这是读码结论，不等于“绝对不存在”，但当前树无生产者/表/DAO。
- **AUD-08 应用导出**：`build/split-eval/app-split.json` 是历史工件，本会话未在设备上重跑；`export_split_eval.py` 尚不存在。
- **G1/G2 零代码**：本会话 grep 0 命中，但未穷举所有资源文件/动态字符串；R3 §9.2 的独立复核结论一致。
- **热区脏态**：批次 24 基线是 19:34 冻结值，树在核对后仍变（`:5`、`:8`）；开工前必须重新核 HEAD/porcelain，不能照抄本文数字。

### 6.3 主要风险

| 项 | 风险 | 缓解 |
|---|---|---|
| G1/G2 | 只标持久回复、漏流式；或误标原题图 | 两个渲染路径都测；G2 只标两张生成图卡；grep/截图双验 |
| AWAITING | “放弃”后复用 taskId 导致永久不再受理；或重新核实触发重复付费 | 新身份/终态裁定；recheck 只读本地；不自动派发 |
| 5b | UI 继续承诺不可调度项；F8/F9/F18 自行取值 | 5b-1 先修 KD-68；F8/F9/F18 等裁定；金样逐位不变 |
| 5c | D-1 前自动写；备注出网；schema 迁移破坏 | 默认建议+确认；备注不进模型；迁移矩阵/备份恢复 |
| 阶段 6 | 未练显示 0% 掌握；四科外伪造统计 | 未练单独桶；不支持明确文案；不写事实 |
| 6B-3 | 绕开 ledger；新 kind 污染披露；模型输出越界 | 方案 A；统一 `ModelTaskRepository`；白名单/原子 applier |
| AUD-08 | 为门变绿改阈值/金标；把 0.5714 说成接近通过 | 阈值/口径不动；门 false 如实；自动入库继续关闭 |

---

## 7. 来源与可复跑命令

### 7.1 仓内来源（本会话实读/实查）

- 计划：`.zcode/plans/plan-2026-10-26-batch-24-isolation-final-kd70.md:15`（T5 条件轨）。
- 总路线：`docs/REFACTOR-MASTER-LINE.md:66`（3D）、`:70`（3E）、`:72`（4C/AUD-08）、`:76`（5）、`:77`（5B）、`:78`（6）、`:79`（6B）、`:84-103`（顺序）、`:145`（AUD-08 验收）、`:149`（AUD-12）、`:170-176`（5a/b/c）、`:226`（PRD-02/03）、`:231`（G1/G2/G3）。
- 决策台账：`docs/agent-first-refactor-decisions-2026-09-23.md:174`（5c）、`:464`（备注不砍）、`:2687`（裁定 39）、`:2733`（5 拆批）、`:3012/:3025/:3036`（6B-3）、`:3255/:3294/:3313/:3350-3351`（G1/G2/G7）。
- 需求定稿：`docs/requirements-fused-final-2026-10-11.md:73`（自动标注/D-1）、`:69-71`（错因）、`:80`（3D）、`:108-120`（R5/F7-F18）、`:135`（E11）、`:165-170`（R8/H2/H3）。
- 新阶段 spec：`docs/research/2026-10-11-new-stages-spec.md:14-29`（3E）、`:99-122`（6B）、`:182-187`（5）、`:196-200`（6）。
- R3 合规：`docs/research/2026-10-25-ai-content-labeling-compliance.md:153-154`（G1/G2）、`:162`（T7 最小面）、`:213-218`（未落/不得称合规）。
- 文案：`docs/ai-content-disclosure-copy.md:10-12`（一句话）、`:49-56`（硬规则）、`:62-67`（嵌入点/热区遗留）。
- 批次记录：batch 20 `:90-91/:142`；batch 21 `:107/:260-266`；batch 22 `:13-14/:275-276`；batch 23 `:14/:288`；batch 24 基线 `:5/:26-27/:45-63/:69-95/:101-118`。
- 缺陷：`docs/known-defects.md:2551`（KD-64）、`:2573-2598`（KD-66）、`:2637-2657`（KD-68）。
- 代码：
  - G1/G2：`feature/tutor/.../TutorSurfaceMessages.kt:291/:318-324`、`TutorConversationSurface.kt:324/:351`、`core/ui/.../AttachedImageCard.kt:150/:216/:303/:345/:414/:483`。
  - AWAITING：`core/database/.../CleanRedrawTaskContract.kt:30-48`、`core/data/.../ExplicitRedrawService.kt:261-268/:399-433/:431`、`core/domain/.../MistakeDetailRepository.kt:428-442/:459-485`、`feature/library/.../MistakeDetailRoute.kt:534-621/:628-672`。
  - 5b：`feature/review/.../ReviewRoute.kt:122-135/:341/:522-558`、`KnowledgeReviewSessionScreen.kt:97/:195/:245-250/:692`、`core/domain/.../KnowledgeReviewQueue.kt:131-143/:160-181`、`KnowledgeReviewFullLibraryScan.kt:30`、`core/data/.../StudyReviewPlannerService.kt:721-730/:772-828`、`RoomBackedStudyExperienceRepository.kt:1113`、`StudyExperienceRepository.kt:86-98`、`StudySnapshotBuilder.kt:91/:130-132`、`NewIntroductionPolicy.kt:90-96/:205/:383`。
  - 5c：`feature/library/.../MistakeDetailRoute.kt:698`、`core/domain/.../MistakeDetailRepository.kt:186`、`core/data/.../model/OpenAiProblemOrganizationProtocol.kt:95/:100/:125`。
  - 阶段 6：`feature/profile/.../ProfileRoute.kt:91-100/:252-282/:285/:351`、`app/.../LearningMasteryScreen.kt:50-110/:124-133`、`core/domain/.../StudyExperienceRepository.kt:190-198`、`StudyExperienceMappers.kt:135-204`、`StudyKnowledgeCoverage.kt:49-82`。
  - 6B-3：`feature/tutor/.../TutorPaperEditAssistant.kt:25/:35/:37`、`TutorExportEditSheet.kt:63/:120`、`TutorConversationViewModel.kt:131/:612`、`TutorConversationViewModelFactory.kt:40`、`TutorLobbyRoute.kt:105/:126`、`TutorRoute.kt:112/:138`、`app/.../SmartMistakeBookRoot.kt:562/:594/:652/:682`、`core/model/.../ModelTasks.kt:12-25`、`core/model/.../PaperEditCommand.kt:27/:118`、`core/data/.../model/OpenAiModelTaskAdapters.kt:44`、`core/model/.../ModelEgress.kt:88-105`、`core/data/.../model/OpenAiCompatibleModelGateway.kt:552-560`、`core/data/build.gradle.kts:169-171`。
  - AUD-08：`tools/split_golden/validate_split_golden.py:1-50`、`measure_split_accuracy.py:1-40/:53`、`tables/split_golden_v1.json`、`tables/split_golden_v1.json.sha256`、`core/domain/.../CaptureWorkflowRepository.kt:439`、`core/data/.../BatchSplitRecognizer.kt:86`、`feature/capture/.../CaptureModelTaskCard.kt:116-124`、`CapturePendingCaptures.kt:123-124`、`CaptureAcquisitionCommands.kt:161`。
  - 热区：`docs/research/2026-10-26-batch-24-baseline.md:26-27/:45-63/:69-95/:101-118`。

### 7.2 本会话执行过的命令（可复跑）

```bash
git status --porcelain -- <每个候选落点>        # 见 §2/§3 各节
grep -rn "AI 生成\|AI生成\|内容由AI\|由AI生成\|人工智能生成" --include=*.kt --include=*.xml \
  feature/tutor/src/main core/ui/src/main app/src/main/res | wc -l          # 0
grep -rn "AWAITING_VERIFICATION" --include=*.kt feature core/domain/src/main | wc -l   # 0
grep -rn "autoIngest\|自动入库\|accuracyGate\|splitGate\|gateEnabled" \
  --include=*.kt feature core app/src/main | wc -l                          # 1（仅注释）
python -c "import json;d=json.load(open('tools/split_golden/tables/split_golden_v1.json',encoding='utf-8'));print(len(d['pages']),sum(p['questionCount'] for p in d['pages']),d['evidenceGrade'])"  # 7 16 model-labeled...
```

### 7.3 外部来源

- 《人工智能生成合成内容标识办法》官方全文（本会话 WebFetch 核对第四条/第五条/施行日期）：<https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm>
- 仓内 R3 另引 TC260-PG-20258A / GB 45438-2025 / 网信办解读等；本会话未重开，沿用 R3 §8 的来源清单（`docs/research/2026-10-25-ai-content-labeling-compliance.md:185-197`），不把未重开的条文当本会话新证据。

---

## 8. 给下一批执行人的最小行动卡

1. **开工前**：重新跑 `git status --porcelain` 与 `git rev-parse HEAD`；不要用本文的脏态数字当终值。
2. **若 T5 还要走条件轨**：只认“目标文件 porcelain 为空”；G2 看 `core/ui/AttachedImageCard.kt`，G1 看 `TutorSurfaceMessages.kt`/`TutorConversationSurface.kt`，AWAITING 看 `MistakeDetailRoute.kt`/`MistakeDetailRepository.kt`/`ExplicitRedrawService.kt`。任一为 M 就登记，不越权。
3. **若只能做一件**：做 G2（一文件、P0、无前置）。
4. **若能做两件且 core/ui 与 tutor 同时清空**：G2 与 G1 同批，但共享角标组件先落 `core/ui`，避免两套文案。
5. **若想先动不碰热区的**：做 AUD-08 的 `export_split_eval.py` + README；门值/阈值/金标一个字不改。
6. **不要**在没有裁定前做 5c、6B-3 新任务类型、F8/F9/F18 数值、AWAITING 放弃语义。
