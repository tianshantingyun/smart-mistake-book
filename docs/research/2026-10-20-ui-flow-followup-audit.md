# 现有 UI 续查：题面编辑、拆分续做与图片展示

日期：2026-10-20。承接[上一轮 UF-01～12](2026-10-20-ui-usability-friction-audit.md)及[UL-01～09](2026-10-20-existing-ui-logic-audit.md)。本轮新增 UF-13～19，均修现有操作，不增加业务功能、页面或新的 UI 工作包。执行进入[原 UI 计划第7节](../superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)，呈现规则进入[原设计第9节](../superpowers/specs/2026-10-19-ui-ux-redesign-design.md)。

## 1. 证据范围与去重

本轮沿源码追到界面、路由、仓库和数据库状态约束；HEAD 为 `a200f27bf6feb19137d485329967b126952f0950`，工作树包含其他任务改动。没有修改产品源码、运行产品测试或启动设备，不把静态分析写成设备复现。复核基线、12个源码文件指纹、本轮文档差异及验证结果保存在 `.jez/artifacts/ui-flow-followup-2026-10-20/`。

UL-01/09是异步读取完成后串到新目标；UF-18是共享图片组件在新读取完成前继续呈现旧位图，根因和修复位置不同。UF-13涉及录入结构化编辑，不重复 UF-06 的试卷编辑公式预览。UF-14～17是原拆分复核流的对象、状态和动作不一致，沿 UX-03/12 的既有工作细化，不重新开发拆分服务。

## 2. 问题、修法与验收

### UF-13：改选项时不能先清空，编辑中间态被当作不合法成品

**P1；源码直接依据。** [选项输入回调](../../feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureStructuredDocumentEditor.kt#L75)直接忽略空白输入；受控字段仍显示原值，清空后重写会被挡住。[工作区 editBlock](../../feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureWorkspacePolicy.kt#L144)又将每次输入直接送入 draft 校验，不通过就静默返回旧工作区。[draft 校验](../../core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/CapturedQuestionDocument.kt#L96)要求内容不被 sanitizer 改写，而[空选项会被替换](../../core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/StructuredContent.kt#L506)。只删掉空白判断仍不能解决这个问题。

改法：在现有编辑状态中按 draftId、basis、blockId、choiceId 保存原始输入缓冲，允许清空、组合输入和未写完的公式；显示的值不从最后合法成品反向覆盖。现有安全处理/校验用于生成合法候选和最终提交；有未通过校验的编辑时，原提交动作不得悄悄保存旧值，要就地指出原有校验问题并保留输入。缓冲服从现有草稿/返回策略，不能增加自动保存、吞掉已有草稿持久化或把无效内容写进正式题目；不放宽领域校验、不新增“应用修改”步骤。

验收：全选删空再输入、中文 IME 组合、粘贴替换、公式未闭合→补全、重建/返回与切换草稿；空白时字段不回弹，提交不冒报成功，补全后原命令得到新值。仅临时输入改变不触发模型或制造 revision。

### UF-14：进入指定任务，页面却可能换成另一份卷子

**P1；跨层源码依据。** [根路由传入明确 jobId](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt#L936)，但[复核页找不到它时退到 jobs.firstOrNull](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/SplitImportReviewRoute.kt#L80)。[活跃查询](../../core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/SplitImportDao.kt#L53)只返回 PREPARING/READY，按更新时间排序。指定 A 已完成/移出活跃集、同时仍有 B 时，原页面可无提示显示并操作 B；A 完成后也可能原地换成 B。

改法：显式目标与“无目标看最近任务”分开。传入 jobId 就保持这个对象，初次加载不显示假空态；必要时复用已有 readImport 查明该任务状态，呈现已有完成/不可用/失败信息及原返回，不自动换任务或创建新任务选择页。无 jobId 的既有最近任务行为保留。读取、点击、结果和恢复都校验相同 jobId，不能让 A 的事件操作 B。

验收：A/B 同在、A 已完成但 B 活跃、首个快照未到、A 消失/读取失败、重建和复核返回；带 A 的入口从不显示 B 的原图、题卡或按钮，不隐式更改导航目标。

### UF-15：还在准备就宣告切分完成，勾选却违反仓库条件

**P1；状态冲突已由源码确认。** [reviewableJob 同时接纳 PREPARING/READY](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/SplitImportReviewRoute.kt#L84)，[页头固定“切分完成”](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/SplitImportReviewRoute.kt#L111)，题卡和主按钮随之可用；但[updateSelected 明确只接受 READY](../../core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomSplitImportStore.kt#L65)。点击还没有异常/拒绝结果的局部反馈。

改法：由现有任务状态推导页头与可操作性。PREPARING 显示正在准备，保留可读已有结果；勾选严格按原仓库的 READY 条件启用。续做/收尾分别沿真实草稿和 UF-17 的原收尾条件，不因出现题卡就可操作；数据库的 complete 实际支持 PREPARING→COMPLETED，不能将它误写成仓库禁止。在途防重复；结果 false 或失败就地说明并保留当前任务/选择，取消异常按取消处理。不要放宽仓库条件，也不要在重组、返回或状态变化时重派模型任务。

验收：PREPARING→READY、状态临点击前改变、选择更新 false/异常、连续点击；界面与原仓库条件一致，失败不假装勾选成功，不把准备中说成没有结果或已经完成。

### UF-16：“录入所选”的数量混入已录入题，行箭头也没有对应动作

**P2；源码直接依据。** [页头计数](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/SplitImportReviewRoute.kt#L118)和[主按钮数量](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/SplitImportReviewRoute.kt#L185)只数 selected，实际续做仅查 selected + PENDING；已录入题仍可能计入，数量不代表还要处理多少道。[题卡](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/SplitImportReviewRoute.kt#L242)只有复选框回调，却给未录入行绘制右箭头，暗示整行能进入详情。

改法：从已有题台账分别派生“待录入已选数量/已录入数量/总题数”；主按钮表达逐题续做，例如“继续录入（2题待提交）”，不要暗示一次提交整批。已录入与未选保持不同状态，取消勾选不表达为撤销入库。没有行打开回调就移除进入箭头，选择行按现有勾选语义呈现，不为修箭头新增详情跳转或逐题快捷入口。勾选许可、已存回执和逐题次序不变。

验收：例如已录入3道、另选待提交2道时，主动作只表示2道；0选中、全部已录入、部分失败仍使用原收尾规则。图标、读屏和实际动作一致，不重复入库，不将浏览写成提交回执。

### UF-17：找不到下一份草稿，被直接当作任务完成

**P1；跨层源码依据。** [续做分支](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/SplitImportReviewRoute.kt#L195)用 firstNotNullOfOrNull 查草稿；结果 null 就 complete，包括“仍有选中 PENDING，但 splitDraftId 缺失”的状态。[complete 实现](../../core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomSplitImportStore.kt#L190)只转换任务状态，没有核验待提交题是否存在。因此“无法继续”和“已经完成”在此 UI 命令出口混为一谈，不只是按钮文案不清。

改法：先区分是否仍有选中 PENDING，按原题号次序确定下一题，再读它的草稿，不能用 firstNotNullOfOrNull 跳过前面缺草稿的题；尚有待提交而缺草稿时保留原任务，明确当前题尚不能继续，复用既有读取/恢复入口，不调用 complete 或把题标为 SAVED。只有原收尾条件确实成立才调用原收尾命令并处理结果；不新增任务状态、自动重建草稿或重派切分。UF-14保证收尾后也不会显示另一任务。

验收：READY 且选中 PENDING 的 draftId 为 null、部分有草稿/部分缺失、全部已提交、complete false/异常；缺草稿的题不被跳过收尾，错误可见，已经入库的真实回执仍保留。

### UF-18：切换图片后，新图加载期间可能仍显示旧图

**P1；本地源码与框架源码推断，待设备复现。** [BoundedLocalImage](../../core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/BoundedLocalImage.kt#L50)将 imageUri/sourceRegion 作为 produceState key，但未在输入身份改变时使旧 Available 失效。[录入页](../../feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureScreen.kt#L804)在同一位置替换所选来源图片。Compose [官方 produceState 源码](https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/ProduceState.kt)中状态由无 key 的 remember 持有，key 改变重启 producer，不自动重置已有值。因此同一组合位置加载 B 或另一个裁剪区域时，有继续显示 A 的窗口；不声称已测出闪现时长。

改法：图片状态携带并核验当前 uri + region（调用方有 asset/revision 身份时一并绑定），身份改变当次呈现即不能消费旧位图/AVAILABLE；不能只在异步加载稍后开始时清空。复用现有有界解码，等待时保留版面并显示局部加载状态，旧任务取消/迟到不改写新目标。展开状态按原交互保留，不新增缓存体系、远程读取或自动重绘。

验收：A快→B慢、A→不可读B、相同uri切region、A→B→C、重建；从目标变更的首帧起，旧图不搭配新图题、页码或裁剪说明。用受控慢读取与 Compose 测试核实，不能仅凭普通速度截图判通过。组件消费者按下节统一覆盖两种生图方法。

### UF-19：资源已就绪但解码失败时，展开只剩空白

**P2；源码直接依据。** [BoundedLocalImage](../../core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/BoundedLocalImage.kt#L60)可报告 UNAVAILABLE，但仅在 Available 时绘制 Image；[AttachedImageCard 的 Ready 分支](../../core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/AttachedImageCard.kt#L198)没有接收这个回调。资源解析 Ready 并不保证稍后的解码成功，权限变化、解码损坏或读期间文件失效时可留下空白；折叠状态也仍无失败提示。现有 Failed 分支不覆盖这条本地解码路径。

改法：将本地加载/解码状态接回当前图片卡，显示可理解的“图片暂时无法显示”和已有图题/文字摘要，避免展开时只写“已展开”而丢掉图题。资源事实与本次呈现失败分开，不伪写生成失败或修改产物台账；回调绑定 UF-18 的图片身份。复用已有失败动作许可，损坏/缺源不盲目重新生成，不为此新增常驻按钮。

验收：Ready + 可读图、Ready + 无法解码、权限/文件失效、故障后切下一张、收起/展开；摘要和失败在视觉及读屏中可知，原正文可读，无无限加载、空白冒报就绪或隐式外发。

## 3. 统一图片规则与实施次序

UF-18/19在共享消费层修复：原图、转写所用图、讲解图和导出预览按各自已有消费者检查；JS、IMAGE_MODEL 沿统一产物合同读取，不因用途硬绑方法。查看、切换、展开、恢复都不触发生图；显式生成/重新生成继续使用同一判定策略、协调器和预算台账，不在 UI 新建后端选择器。尚未上线的 JS 通道只写未来验收，不当作当前可运行能力。

先做 UF-14/15/17（对象/可执行状态/收尾），同文件合批；UF-16同时改表达。随后 UF-13（编辑缓冲与现有校验），UF-18/19（共享图片身份与可见失败）结合原 UL 竞态回归执行。对应 UI-04/08/11；五功能分区、四底部入口、原生表单、评分与发布门均保持原约束。

## 4. 覆盖与待验范围

| 现有路径 | 本轮补到的具体断点 | 延续已有任务/后续验证 |
|---|---|---|
| 录入→结构化修改→提交 | UF-13，中间态与合法候选混用 | UI-04；真实输入法/重建与原保存回归 |
| 批量/拍照拆分→复核→逐题录入→回到任务 | UF-14～17，对象/状态/数量/收尾 | UI-04/02；受控多任务、缺草稿与仓库拒绝 |
| 来源页切换、图片/裁剪区域替换 | UF-18，新身份的首帧不能用旧位图 | UI-08/11；受控慢读取和真实页面 |
| 讲解图展开与本地失败 | UF-19，消费失败可见 | UI-08/11、WG-06/07；现有通道先验，JS上线后补同合同 |
| 其他既有主路径 | 未新增独立缺陷编号 | 原UX/UL/UF任务继续有效，不能从本轮推断全部页面已验收 |

本轮完成的是源码审查与计划细化。七项实现、设备复现、前后视觉比较均未完成，不勾选实现任务；文档核验通过也不等于软件已经修好。
