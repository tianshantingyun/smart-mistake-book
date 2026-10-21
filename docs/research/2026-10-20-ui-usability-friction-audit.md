# 现有页面使用阻力审查：输入、选择、阅读与操作反馈

日期：2026-10-20。用户要求：继续找出现有 UI 设计不合理、用起来不顺心的地方，将具体改法写入计划。延续上一轮边界：改善现有功能的呈现和操作，不扩业务功能。本文承接 UX-01～14、UL-01～09，使用 UF 编号标注具体使用阻力；不把同主题拆成新的功能阶段，也不重复统计旧漏洞。

## 1. 本轮依据与限制

- 当前源码工作树 HEAD 为 `a200f27bf6feb19137d485329967b126952f0950`，含其他任务改动。本轮未改产品源码。相关源码指纹、包信息、截图、UI树与文档验证保存在 `.jez/artifacts/ui-friction-2026-10-20/`。
- 起初无运行设备；本轮以无窗口方式启动专用 `audit_user_visible_20261016`，串号 `emulator-5562`，API34、1080×2400、420dpi。该模拟器没有主应用，本轮安装已有 localFirst debug APK，版本 `0.2.0-localfirst` / code 2，SHA-256 `71c08532505aa22ec920286934695024f9c900f4de9f0f59dcf0908ce0acf748`。**没有重建APK，也没有建立该包与当前HEAD的完整映射**；包画面和源码依据分别注明。
- 实际查看复习空态、我的、复习排程、存储页、模型设置及其滚动后状态、学习掌握空态。未输入密钥、点击连接测试、发送模型请求、提交作答、创建备份、恢复或删除数据。首次安装/启动会产生正常初始化数据，不声称审查完全零本地写入。
- 模拟器时区是 GMT，font_scale 设置返回 null；截图中的时钟/问候不作为时区缺陷，未验证大字。模型设置设置了 FLAG_SECURE；标准截屏得到0字节，本轮只采用其UI树和源码，**不把空截图算视觉证据，也不绕过安全窗口**。
- 有数据的选题、导出编辑/交付和学习档案长列表只做源码审查，需真实内容/可控替身及设备画面再验。没有点击取消系统相册来复现UF-11。以下未观察的结果不写成设备已复现。

可读画面：[复习空态](../../.jez/artifacts/ui-friction-2026-10-20/initial.png)、[我的](../../.jez/artifacts/ui-friction-2026-10-20/profile.png)、[排程与考试输入](../../.jez/artifacts/ui-friction-2026-10-20/scheduling.png)、[存储操作](../../.jez/artifacts/ui-friction-2026-10-20/storage.png)、[学习掌握空态](../../.jez/artifacts/ui-friction-2026-10-20/mastery-empty.png)。模型设置前后UI树分别为[进入时](../../.jez/artifacts/ui-friction-2026-10-20/model-settings.xml)与[向下滚动后](../../.jez/artifacts/ui-friction-2026-10-20/model-settings-scrolled.xml)。

新包已提供“录入第一道题”的复习空态动作；UX-01中“没有下一步”的旧观察不能继续当作当前包事实，剩余层级问题仍沿原计划处理。通用字号/对比度、四底部入口、JS/图像模型策略与九项UL漏洞不在本清单重复立项。

## 2. 具体问题与改法

### UF-01：数据库总占用被叫作“学习记录”

**证据：实际包+源码。** 无可复习题/学习记录的审查状态，存储页显示“学习记录 388.9 MB”。[SecondaryScreens.kt](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt#L811)直接将 `databaseBytes` 命名为学习记录，混淆了数据库文件总量与学生的作答内容。

改法：改为“应用数据库”，用一句话解释包含本机题目、记录等实际数据；内置内容若也在该库内如实说明。不在没有分项统计时编造“你的记录占多少”。原图、可清理空间继续读已有Inventory，显示值与备份包含范围一致。**不改备份内容、不新加占用分析器。**

验收：空库/有题/有学习记录均不把全部数据库量说成作答量；文案与实际统计字段相符，原有字节数不变。

### UF-02：清理、备份和全部删除挤在同一操作组

**证据：实际包+源码。** 存储页“创建备份、校验、恢复、删除全部、清理孤立题图”连续排列；[删除与清理按钮](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt#L865)外观近似，学生为清理空间容易先扫到“删除全部数据”。

改法：同一页按“备份与恢复 / 空间维护 / 危险操作”分组；保留创建备份主动作，把全部删除放到页面末尾独立区，文字说明会影响什么。仍使用原有一次确认，不加新确认、不真删归档题；“清理孤立题图”改用可理解的说明，不能暗示会删除仍被题目引用的图片。

验收：能直接区分“释放可清理空间”与“清空全部内容”，各原动作仍可达；布局调整不改变删除/恢复/GC调用、保护和范围。

### UF-03：模型配置的空白密钥与禁用按钮让人猜原因

**证据：源码；首装表单UI树已看，已保存配置场景待复现。** [保存成功会清空输入密钥](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt#L294)，[保存/测试的启用条件](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt#L313)不同，但字段未说明已保存的密钥不会回显；测试因未配置、未保存改动或操作在途不可用时，没有逐项就地说明。连接配置下方还接了校准诊断，主任务与专业信息混排。

改法：按已有configuration/operation事实显示“尚未配置 / 已保存在本机 / 有改动待保存”；在密钥旁说明不回显、当前保存命令需要重新填写时明确提示。禁用保存/测试给当前首要原因，操作结果紧贴原按钮；高级诊断同页折叠，连接表单优先。保持主动测试和当前密钥保存合同，**不读回旧密钥、不让留空覆盖密钥、不自动测试，也不新增连接管理功能**。

验收：首装、已配置、改字段未填密钥、测试中、失败时都能从当前页知道下一步；状态提示不得把“已保存”说成“连接已验证”。

### UF-04：输入考试日期却要求先计算“还有几天”

**证据：实际包+源码。** [考试输入](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SchedulingSettingsScreen.kt#L165)只给天数，随后[转换成examEpochDay](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SchedulingSettingsScreen.kt#L181)。学生通常知道考试日期，当前设计把日期计算交给学生。

改法：把同一个考试日期字段呈现为可直接选/输入的日期，距离天数只作辅助文字；保持唯一日期事实源和已有添加命令。按项目现有依赖选择平台日期控件，不为此升级整套UI框架。不新做日历管理、考试预测或提醒规则；选择日期不自动添加考试。

验收：选定日期、取消、重新打开、今天/未来日期及时区边界按既有允许范围处理，写入的仍是同一天的examEpochDay；关闭选择器不改原字段。科目范围与原调度逻辑不变。

### UF-05：栏数、字号的五个选项挤成一行

**证据：源码，窄屏实际画面待验。** [栏数与字号](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt#L394)把单栏、双栏和三档字号放进一个无换行Row，其他参数也存在长标签并排。在小屏/大字下，选择成本和溢出风险高。

改法：栏数与字号分成独立选择组，按可用空间换行；缩放/边距把数值和增减动作成组，单独标清。常用参数先展示，已有低频参数可同页展开；不得靠缩字、小触区或隐藏某个取值挤下。所有控件仍更新原layout字段，不改变默认值、允许范围和模板含义。

验收：320/360dp和大字下所有原选项可达、完整可读、选中态清楚；每组只控制原对应字段；答案卷状态同时沿UL-05验证。

### UF-06：长卷面编辑既难核对公式，也难找到错误字段

**证据：源码，长内容设备场景待验。** [公式编辑仅显示LaTeX输入](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportEditSheet.kt#L271)，[必填为空只给整体提示](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportEditSheet.kt#L162)。几十个字段展开时，学生难知道改后的式子长什么样、哪一题阻止导出。

改法：复用现有数学渲染器在对应字段旁给预览；沿现有必填/合法性规则标出题号和字段，提交失败时定位首个当前错误，保留输入。长编辑按原UI-09全页设计落地，主动作可达，不再另列“做一个编辑器”的任务。只改表达/焦点/布局，**不新增必填项、校验阈值、自动修公式、自动保存或模型调用**。

验收：多题长文、空题干、空选项、长/不完整公式、IME打开时能找到并修正原错误；预览失败不丢原式，不标成保存/内容正确；定位不得被旧异步结果带到另一题。

### UF-07：保存/分享结果写在所有导出文件的最后

**证据：源码，有文件列表设备场景待验。** [交付反馈](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeExportHubRoute.kt#L312)在全部records之后统一显示。操作上方文件时，成功/失败可能在屏幕之外，也缺少明确的目标文件语境。

改法：让在途、结果和重试贴近本次操作的既有文件行，显示对应文件名/格式；需要屏幕阅读器可感知的轻量播报。沿用原交付回执，保存/分享/打印状态分清，不自动滚走正在看的内容，不把打开分享器说成分享成功。**不新增交付队列、历史表或后台生成任务。**

验收：多文件滚动列表、保存取消/失败、分享器打开失败时，当前行就能知道结果；不能把A文件的结果显示在B行，单个动作不重复生成或重复交付。

### UF-08：选题搜索缺少方便的清空动作，长标签难辨认

**证据：源码，有候选题场景待验。** [选题搜索框](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorMistakePicker.kt#L59)没有清空动作；[行内容](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorMistakePicker.kt#L123)将元信息限制为一行，长标题与标签容易难以区分。

改法：复用错题本已有的清空搜索表现，清空只调用原query更新；按空间让现有标题/科目/章节标签合理换行、完整读屏。空库与无搜索结果分别沿已有原因表达；保留原匹配范围、即时过滤和点选即返回语义。不加新搜索模式、推荐排序、确认选择页或题目预览服务。

验收：长查询一动作清空，清空后恢复原完整候选；同名/长标签能读到区别；选定/取消回到原输入上下文；附件异步身份另沿UL-01。

### UF-09：学习档案将每科每个知识点全部展开

**证据：源码，当前实际包只看了空态，长档案待数据验证。** [全量分组渲染](../../app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt#L319)将各科各主题下的全部知识点依次展开。内容多时，找到后面的科目要经过大量长文，缺乏浏览层级。

改法：保留现有科目/主题顺序与数量，科目摘要可直接看到，主题明细可原地展开/收起，并保持本次浏览的展开和位置。所有现有明细仍可访问，不用take截断，不加筛选、掌握编辑或证据深钻。默认展开规则只用于呈现，不依据猜测自动切换/排序，也不造新的学习数据。

验收：少量/大量真实明细均可到达，折叠数量与原数据相符；展开/收起不触发评分、模型或写库；现有读屏证据/区间说明保留。

### UF-10：批量照片选择的“取消”被当成选错

**证据：源码，本轮未打开相册复现。** [系统选择回调](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/BatchImportRoute.kt#L110)把selected.size<2统一提示“至少选择2张”，其中也包括空选择/取消。学生只是返回，却看见错误。

改法：空结果按取消回原处，保留原任务/提示；单张仍按当前批量规则说明应走普通录入，多张沿原导入。只修回调分支与反馈，不降低最小页数、不自动跳转、不默默创建单页任务。

验收：取消零新任务且无误报；单张与合法多张结果分别保持原规则，重复返回不触发导入。

### UF-11：试卷副本的“删除”“返回选题”没有说清影响

**证据：源码，设备编辑场景待验。** [移除按钮叫“删除”](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportEditSheet.kt#L242)、[已删除列表](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportEditSheet.kt#L144)、[返回选题](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportEditSheet.kt#L180)与卷面草稿语义不够贴近，学生会担心删掉错题本原题，也容易误解返回是否保留本次修改。

改法：统一“从本卷移除 / 本卷已移除，可加回”，退出动作旁如实说明现有回调会放弃什么；保留顶层“只改本卷”的提示，但不依赖用户记住已滚走的说明。复用原移除、加回和返回，不改成自动保存或再加一层确认，不改变原题。

验收：移除/加回仍只影响本卷，返回/关闭与说明一致；原题正文/顺序/归档不受影响，来源和副本身份始终可辨。

### UF-12：二级页面的返回跟正文一起滚走

**证据：模型设置UI树前后对照+源码。** 向下滚动设置表单后，UI树不再有“返回”，系统返回仍能退出；[RootPageColumn](../../core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Components.kt#L329)把其全部内容放在verticalScroll，多个二级页又把SecondaryHeader作为首个正文元素。

改法：优先在长设置、导出成果和编辑页将现有标题/返回放在滚动内容之外；复用现有页头与路由，不新增导航层。正文独立滚动，页头/键盘/inset各有一个空间拥有者；不要把RootPageColumn所有子内容一律固定。保留UL-04等保存期间的返回约束，不用固定页头绕过提交保护。

验收：滚到页面末尾、IME打开和大字时，页面内返回可达；系统返回/页头返回遵守同一原规则，返回目标和草稿不变；无重复页头或重复inset。

## 3. 设计取舍与实施顺序

先做文案/状态/选择组等小改动：UF-01/02/03/08/10/11；再做跨布局调整：UF-04/05/06/07/09/12。先修相关UL的目标、保存、版本和回执问题，再验呈现，不拿界面优化掩盖逻辑漏洞。多个任务共享SecondaryScreens/TutorExportEditSheet时按同文件合批，避免相互覆盖。

页面结构与验收进入[UI计划第6节](../superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)，具体样式/交互承接[设计第8节](../superpowers/specs/2026-10-19-ui-ux-redesign-design.md)。这些工作都只服务现有功能，不以新模型能力或3D门作为纯呈现修复前提。

参考平台模式：[Android日期选择](https://developer.android.com/develop/ui/compose/components/datepickers)提供选择/输入日期的现成方式；[Flow布局](https://developer.android.com/develop/ui/compose/layouts/flow)用于按空间换行；[Scaffold](https://developer.android.com/develop/ui/compose/components/scaffold)用于分配页头、正文与底部区域。这里引用交互/布局模式，不强制采用某个新版本API或新增依赖，实际实现须适配本仓库版本。

## 4. 完成定义

所有UF任务本轮均未实现。源码项需定向测试及实际长内容画面，已观察页面需同数据/同窗口的改前改后对照；空态截图不能替代非空、键盘、大字或跨版本验证。保留原领域校验、权限、默认值、后台任务和JS/IMAGE_MODEL统一策略；不能靠删能力、缩字、强制确认、自动外发或虚构统计满足“更简洁”。
