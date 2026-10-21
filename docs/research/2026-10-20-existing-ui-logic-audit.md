# 现有 UI 逻辑漏洞补充审查

日期：2026-10-20。用户范围：继续寻找现有 UI 的逻辑漏洞，写入修复计划，不增加新的业务逻辑或功能。本轮只修改文档。

本次沿 Compose 事件、ViewModel 状态和现有领域调用作静态核对，工作树 HEAD 为 `a200f27bf6feb19137d485329967b126952f0950`，包含其他任务的未提交改动。源码指纹和本轮文档校验记录位于 `.jez/artifacts/ui-logic-audit-2026-10-20/`。以下是源码链路确认的缺口，**尚未在设备上复现，也未运行产品测试**；不把旧截图作为本轮漏洞的复现证据。

## 1. 修复范围与实施归属

全部任务并入[原 UI 计划第 5 节](../superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)，沿用 UI-00～11，不另开功能阶段。UL 是漏洞编号，不是新能力编号。

| 编号 | 优先级 | 现有行为漏洞 | 归属 |
|---|---|---|---|
| UL-01 | P1 | 附件题异步读取无目标校验，旧结果可覆盖后选的新题或重新出现 | UI-07；PRD-08 的竞态回归 |
| UL-02 | P1 | 编辑器原地重建再次载入基线，覆盖尚未保存的编辑 | UI-05/11；PRD-03 |
| UL-03 | P1 | 保存后再修改，“完成”仍可绕过未保存检查并显示成功提示 | UI-05；PRD-03 |
| UL-04 | P1 | 正在保存时仍可确认“放弃”，但已发出的保存没有取消 | UI-02/05；PRD-03 |
| UL-05 | P1 | 答案卷的有效含答案状态与“不含答案区”控件不一致 | UI-09；PRD-04 |
| UL-06 | P1 | 列表元数据异常和分页错误没有转成可见、可恢复的页面状态 | UI-03；PRD-06/11 |
| UL-07 | P2 | 归档列表的恢复忽略返回值和异常，没有在途或失败反馈 | UI-03；现有归档维护 |
| UL-08 | P2 | 归档弹窗允许在途重复确认，失败信息却显示在弹窗背后 | UI-05；现有归档维护 |
| UL-09 | P1 | 导出编辑稿读取只校验版式，可能回填过期的选题或另一轮弹层 | UI-09；PRD-04 |

修复只调整现有动作的目标绑定、生命周期、提交互斥和状态展示；不新增页面、导航入口、筛选维度、评分规则、模型调用、Room 表或恢复引擎。现有确认弹窗与错误/重试组件可就地复用，不增加常规操作的确认步骤。

## 2. 触发链、最小修法与回归条件

### UL-01：附件题的旧读取覆盖新选择

依据：[读取并回填附件](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationViewModel.kt#L653)、[清除附件](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationViewModel.kt#L672)、[恢复附件读取](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationViewModel.kt#L762)。每次选择都新开 `viewModelScope.launch`；读取结束后直接回填，未核对是否仍是本次选择。清除/派发附件也没有使先前未结束的读取失效，恢复读取同样没有结束时的目标校验。

触发：选 A（慢读）→选 B（快读）→B 已显示→A 晚到，附件变成 A；或 B 已被移除后 A 晚到，附件重新出现。已有发送后的清空规则不能阻止旧读晚到。

最小修复：为现有附件选择/恢复读取保留当前请求身份，取消或使旧请求失效，结果和错误只允许回填仍有效的目标。移除、派发和切换当前会话沿同一失效边界处理；取消作为取消传播。保留“实际派发成功后才清附件”的原规则，不改为选择后自动发送，不新增附件管理页。

回归：可控延迟的 A/B 反序完成；B 移除后 A 完成；旧读失败晚于新读成功；读取期间当前回合已经派发；取消读取。最终待发附件只能来自仍有效的最后一次选择，旧读不复活附件、不覆盖新错误/新结果，选择/移除本身无模型派发。

### UL-02：旋转后重新载入覆盖未保存编辑

依据：[编辑页 LaunchedEffect](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditRoute.kt#L68)、[onEditorOpened](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditViewModel.kt#L103)、[loadBaseline 覆盖字段](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditViewModel.kt#L316)。ViewModel 能跨配置变化保留，但组合重建仍执行 `onEditorOpened()`；`openedOnce` 为 true 时又载入基线，异步读完用存量题面覆盖 title/blocks。

触发：修改题干但未保存→旋转导致 Activity/组合重建→旧 ViewModel 再次收到打开事件→读取完成后输入回退。这里不把普通 recomposition 误说成一定触发，也不声称已覆盖进程死亡恢复。

最小修复：区分同次编辑的组合重建与明确结束后重新进入。保留同次会话的草稿/冲突/保存状态；用户确实放弃或完成后再进才按既有规则载入当前版。不新增自动保存、数据库草稿系统或编辑入口。

回归：有未保存题干/公式/选项时重建；有冲突提示时重建；保存后完成再进；明确放弃后再进。前两者保留当前稿，后两者读取当前基线；不能为了保稿恢复已经明确放弃的旧稿。

### UL-03：“完成”使用上一次保存状态，绕过当前未保存检查

依据：[输入变更](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditViewModel.kt#L111)、[差异计算只更新 diff/dirty](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditViewModel.kt#L395)、[完成按钮](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditRoute.kt#L351)、[返回详情的保存提示](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt#L118)。保存过一次后 `savedRevisionNumber` 保留；再次输入只恢复 dirty，完成按钮仍直接 `onDone`，没有经过 `leave()` 的未保存检查。

触发：修改→保存成功→再次修改→点“完成，返回详情”。第二次改动没有保存，却离开编辑器并显示“已保存题面修改”的提示。

最小修复：完成只能确认与当前内容一致的保存结果；当前 dirty/saving 时不能走成功出口。复用现有离开/继续编辑语义，成功提示依真实已保存内容显示。不增加自动补存、不把完成按钮变成另一套保存命令。

回归：保存 A 后修改 B，完成不能静默离开或报 B 已保存；明确放弃 B 后显示 A；保存 B 后完成显示 B；无改动完成沿原成功路径。针对编辑回执与详情提示一起验收。

### UL-04：“放弃修改”不能表示取消已开始的保存

依据：[leave 未检查 saving](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditRoute.kt#L70)、[放弃按钮只调用 onBack](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditRoute.kt#L365)、[ViewModel 保存协程](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditViewModel.kt#L190)。保存中虽然禁用输入/保存按钮，返回及既有放弃弹窗没有同样约束。离开内部编辑页不等于销毁宿主导航项的 ViewModel，也没有撤销已发出的 reviseProblem。

触发：用延迟仓储保持保存在途→系统/页头返回→确认“放弃修改”→保存随后成功。界面让学生理解为没有保存，而题目可能已经换版。

最小修复：沿已存在的 saving 状态约束返回、完成和放弃弹窗，提交期间不能承诺“放弃即不写入”；保存结束再按真实结果决定能否离开。保存开始前的明确放弃保持原语义，不新增保存回滚、全局导航锁或撤销业务。

回归：延迟成功/失败时点页头返回、系统返回、既有放弃确认；在途不能走虚假取消出口，失败保稿，成功只确认实际版本；连续点击不会再发保存命令。

### UL-05：答案卷显示“不含答案区”

依据：[版式摘要按有效值判定](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt#L161)、[模板切换](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt#L359)、[附加内容控件只读原始 includeAnswer](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt#L462)、[状态接收原值](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationViewModel.kt#L379)、[答案卷既有契约](../../core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/MistakePdfLayout.kt#L58)。`with_answers` 等效启用答案区；切模板只改 templateId，ViewModel也未归一化两者。因而 includeAnswer=false 时选择答案卷，摘要说含答案、控件却说不含答案，点击关闭也不能覆盖模板语义。

最小修复：控件、摘要和实际导出的有效状态同源；答案卷强制包含的既有含义必须在现有控件上如实表现，不能让无效的关闭操作看起来成功。保留默认不带答案及三个模板的既有语义；不新增答案模式、不改变领域规则来迁就矛盾显示。与模板绑定的控件状态明确区分，不用低对比无说明禁用掩盖原因。

回归：三个模板×includeAnswer true/false，选择模板→开关→切回原模板；有答案/缺答案两种数据；摘要、控件、传出 layout 和最终产物一致。缺答案如实说明，不把生成空答案区当成功。

### UL-06：列表读取失败没有可见状态

依据：[元数据任务](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryViewModel.kt#L319)、[分页渲染](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryRoute.kt#L313)。totalCount/facets 调用没有错误收敛；列表直接遍历 Paging 项目，没有消费 refresh/append 的 LoadState.Error 和 retry。已有旧查询结果校验与取消检查是正确保护，不删除它们。

触发：元数据或分页数据源抛错。失败无法转成对应的可见错误和重试；首载、已有内容、翻页三种情况没有明确区分。未实测崩溃，不能把此静态缺口记成必现崩溃。

最小修复：用现有页级/局部状态接住异常，保留 CancellationException；首载失败、分页失败就地展示，已有题保持可读，重试原查询，保留搜索/筛选。不把异常结果伪装空库，不新增网络依赖、缓存层或搜索模式。

回归：totalCount/facets/refresh/append 分别失败再恢复；旧查询失败晚到；切条件与取消。无永久等待或未捕获普通失败，翻页失败不抹掉旧题，重试不改筛选和查询顺序。

### UL-07：列表恢复题目没有失败回执

依据：[恢复回调忽略结果](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryRoute.kt#L230)、[恢复行](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryRoute.kt#L733)。UI launch 中直接调用 restoreEntry，返回 false 无反馈，异常未捕获；按钮也没有当前条目的在途状态。详情页的恢复已有错误/在途处理，可复用同语义。

最小修复：消费真实返回值，错误与重试留在该条目附近，当前条目在途防重入；成功由既有观察流更新。只恢复既有归档题，不新增回收站、恢复类型或恢复后的自动练习。

回归：成功、false、异常、连续点击、离开/返回；每次只发有效的恢复动作，失败可见，原题与学习记录不因 UI 回执修复而改变。

### UL-08：归档弹窗重复确认，错误被遮挡

依据：[归档动作和确认弹窗](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt#L656)。外层按钮用 !archiving，但确认按钮没有在途禁用或回调防重入；失败只写到弹窗之外的 error，弹窗仍开着。

触发：弹窗点确认，延迟返回期间重复点击；仓储返回 false/抛错后，错误文字位于模态背后，学生仍面对同一确认弹窗。不能据此宣称底层一定重复归档成功，底层幂等保护继续保留。

最小修复：沿 archiving 给确认事件加互斥，在当前可见弹窗内呈现失败/可重试状态；在途不能用“取消”暗示已撤销事务。取消发生在提交前不调用仓储，成功只离开一次。复用原确认弹窗，不叠加第二层确认。

回归：false/异常在弹窗内可读；双击只派发一次；失败后一次重试；提交前取消零写入；成功回执只触发一次返回且不影响其他页面。

### UL-09：导出编辑稿读取回填过期的选题

依据：[编辑稿准备与回填](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationViewModel.kt#L428)、[仍可修改候选题](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt#L514)、[读取期间可关闭](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt#L578)。读取捕获 entryIds 与 sheet.layout；结束时只拒绝 current=null 或 layout 改变，没有核对当前选中题身份或是否还是原来的导出弹层。

触发：勾 A→编辑卷面读 A 中→改勾 B→A 的稿件晚到，直接显示 A 的编辑稿；或关闭旧弹层、打开同版式的新弹层，旧结果进入新弹层。这里已有版式变更保护是正确的，缺口在其余目标身份。

最小修复：沿现有读取状态绑定本次弹层/读取请求和冻结选题身份；选择改变、关闭或新一轮打开使过期读失效，错误也只回填有效请求。复用现有“请重新点编辑卷面”提示，不自动增加读取、导出或生图，不创建第二套卷面模型。旧回调不能清掉新请求的在途状态。

回归：读 A 时改勾 B；旧弹层读 A→关闭→同版式新弹层读 B，反序完成；旧失败晚到；单纯改版式仍沿原保护。显示/编辑/提交的题与当前有效选题一致，过期结果不触发导航或文件生成。

## 3. 已有正确处理与排除项

- LibraryViewModel 元数据已有取消与查询身份校验；问题是错误收敛及分页状态，不把它误报为所有旧查询都能覆盖新查询。
- 原题修订已有 save 防重入、CAS 冲突和保留输入的 reload；本次修复打开/离开/完成的 UI 生命周期，不重建 reviseProblem 或扩可编辑字段。
- 原生 PDF/DOCX 格式选择、原题编辑及配置清除确认已有在飞实现。源码存在不代表验收通过，也不能继续按“完全缺少入口”重复实现。
- 模型设置的已保存配置版本检查、保存/清除期间返回保护已有处理，不列作本批新缺陷。
- 不把新复习设计、导出新增模式、模型生图新能力、小屏视觉重排混入这批修复；既有 JS/IMAGE_MODEL 统一规则、ASK/AUTO_VISIBLE 权限与学习启用门不变。

## 4. 后续验收与本轮证据界限

先修会选错题/丢稿/虚报保存/意外带答案的 UL-01～05、UL-09，再修列表失败闭环 UL-06～08；同文件单写者，编辑器三项合批，分别保留回归情景。复用现有同包 ViewModel/Compose 测试和仓储替身；需要等待的操作用可控 suspend/CompletableDeferred，避免靠固定 sleep 猜竞态。

修复验收分别记录源码/定向测试/设备操作证据，未覆盖保持 UNVERIFIED。本轮只验证文档、链接、任务映射和所引源码存在/指纹，未实施以上修复，未执行 Gradle 或 Android 复现，所有实施任务保持未勾选。
