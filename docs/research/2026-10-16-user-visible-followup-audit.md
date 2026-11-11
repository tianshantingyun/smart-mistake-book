# 已实现功能的用户可见缺口复查（2026-10-16）

本轮继续检查“功能已有入口、已有完成记录，但用户实际使用仍会遇到的问题”。收敛出 **11 项具体缺口**：3 项设备复现，4 项由执行探针与源码链共同确认，4 项由完整源码消费链确认。后两类不冒充设备端到端复现。本文全部问题仍待修复。

审查基线为 `fa265487d7d48ba1274e8533d4e417109ea8eefd` 加本轮开始时已有的共享工作区改动。已对照 known-defects、主线计划和近期补修记录，排除已修问题与同一机制的重复登记。UV-02 是 AUD-02 的另一处恢复身份缺口，UV-04 是 V-05 的重启交接残余；其余未找到精确对应的既有登记。UV 编号仅用于本报告，没有擅自重排主线计划。

## 优先处理项

| 编号 | 优先级 | 用户能看到的现象 | 证据等级 |
| --- | --- | --- | --- |
| UV-01 | P1 | 讲题时先请求提示，原题选项随后无法继续点击；提示失败也会发生 | 源码完整链 |
| UV-02 | P1 | 小测进程恢复后，旧选择或判决可能套到另一个知识点 | 生产 ViewModel 执行 + 源码链 |
| UV-03 | P1 | 批量分题成功，复核页的原卷与裁剪预览却失去源图 | 原始 SQL 执行 + 源码链 |
| UV-04 | P2 | 清空数据后按提示重新打开，仍报数据库连接池已关闭 | 删除分支设备复现；恢复分支源码链 |
| UV-05 | P2 | 备注提示保存成功，正文和再次编辑却仍是旧内容 | 源码完整链 |
| UV-06 | P2 | 批量页已入库或移除，仍显示“点此继续”，点开却说不存在 | 源码完整链 |
| UV-07 | P2 | 跳过最后一个知识点后，内容区空白，没有完成入口 | 生产 ViewModel 执行 + UI 源码链 |
| UV-08 | P2 | 大模型设置旋转屏幕后，未保存输入全部消失 | 设备复现 |
| UV-09 | P2 | 系统重新允许通知后，界面显示提醒开启，实际可能没有下一次闹钟 | 源码完整链 |
| UV-10 | P2 | PDF 关闭答案区，题面仍打印“已选”，影响重新练习 | 生产排版器执行 + 源码链 |
| UV-11 | P2 | 掌握页“去错题本看看”实际回到“我的” | 设备复现 |

P1 表示阻断主要学习/复核流程或混淆学习身份；P2 表示明确的操作、反馈、恢复或导航缺口。没有把代码风格、未承诺的新功能列成用户缺陷。

## 逐项定位与补齐方向

### UV-01：请求提示夺走当前诊断题的交互权

触发：进入带选项的讲题诊断轮，尚未作答时点击“我不确定，给我一点提示”，等待请求成功或失败，再尝试回答原题。自由对话仍可使用，但原选择题的客观作答入口被禁用。

[TutorSessionPanel.kt:910](<D:/smart mistake book/feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/components/TutorSessionPanel.kt:910>) 发起 RESPOND 而不写作答证据；[TutorConversationTimeline.kt:285](<D:/smart mistake book/feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationTimeline.kt:285>) 把这些请求（包括失败请求）追加为 Reply。随后 [TutorSessionPanel.kt:903](<D:/smart mistake book/feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/components/TutorSessionPanel.kt:903>) 的 `interactionEnabled = isTail && isCurrentTurn` 因原 Plan 不再是时间线尾项而变成 false。提示没有产生 TutorTurnResponse，自动续题需要的非空作答历史也不满足，没有找到恢复原选项的路径。

补齐方向：按“当前尚未完成的诊断轮”确定交互权，提示消息不应终结该轮。验证提示成功、失败、重试之后，原选择仍可提交一次，并且提示不会伪造选择证据。现有提示仪器测试只检查请求内容与没有写选择，没有检查提示之后还能作答。本项与 KD-36 的旧回调捕获问题不同。

### UV-02：会话恢复保留了位置，却没有保留题目身份

触发：小测队列 A/B/C，在某一项留下选择或判决；进程被回收后，掌握证据、日期或知识库变化使重新生成的队列重排/缩短。恢复时仍使用旧索引、选择和判决。

[SmartMistakeBookRoot.kt:545](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:545>) 在重建时重新规划当前队列；[KnowledgeReviewSessionViewModel.kt:89](<D:/smart mistake book/feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionViewModel.kt:89>) 将旧 currentIndex 直接用于新 plan，旧选择/判决也不核对知识点身份。题面恢复又使用新 currentEntry 的 KC 读取 `(sessionId, KC)`，可能显示另一知识点配旧判决，或提示原题无法恢复。此前持久化原题面的修复没有闭合“本次有序队列”的身份。

本轮运行当前编译出的生产 ViewModel，使用同一 SavedStateHandle、改变恢复时队列：`kc-fresh / selected=B` 变为 `kc-confl / selected=B`。这是条件明确的状态复现，尚未在带真实学习数据的应用中执行进程回收重排。[执行记录](<D:/smart mistake book/.artifacts/user-visible-audit-2026-10-16/review-state-probe.log>)。

补齐方向：持久化会话的有序 KC 列表及当前题目身份，按该会话恢复；选择、题面、判决必须绑定同一身份。补测队列重排、缩短、已掌握项退出候选后的恢复，不能只用完全相同的固定 plan 测进程恢复。

### UV-03：分题已成功，待复核页面的源图却被清理

触发：整卷/批量导入，模型成功切分，批量页进入 READY/SETTLED 后打开分题复核。子题的规范资产已经保存，但复核页的原卷和裁剪预览仍引用 staging URI。

[RoomBatchImportRepository.kt:420](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomBatchImportRepository.kt:420>) 将原 page.sourceUri 送给分题识别，成功后置 SETTLED 并清理 staging；[BatchSplitRecognizer.kt:101](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/BatchSplitRecognizer.kt:101>) 把该 URI 写入分题任务。[BatchImportDao.kt:258](<D:/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/BatchImportDao.kt:258>) 的保留查询只考虑 batch page，不考虑待复核 split job。[SplitImportReviewRoute.kt:155](<D:/smart mistake book/feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/SplitImportReviewRoute.kt:155>) 继续直接展示它，没有规范资产回退。

本轮提取生产 DAO 原始查询，在内存 SQLite 中放入同源的 READY 分题任务与 READY/SETTLED 批量页，得到 `active_ready_split_jobs=1`、`retained_count=0`。[SQL 与输出](<D:/smart mistake book/.artifacts/user-visible-audit-2026-10-16/staging-retention-probe.json>)。这验证删除判据，不等同于真实模型/Android 图像复核端到端验证，也不表示子题规范资产全部丢失。

补齐方向：分题复核任务改为引用受管理的规范源资产，或把仍需复核的任务加入引用保留判据；最后一位消费者结束后再清理。现有测试仅验证子草稿可打开，需增加批量完成清理之后真实打开 reviewJob.sourceUri 的断言。

### UV-04：恢复/删除后的“请重新打开应用”缺少真正重启交接

[AndroidBackupRepository.kt:328](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt:328>) 恢复时关闭 Room，删除全部数据也在同文件 `:497` 关闭数据库。[SecondaryScreens.kt:726](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt:726>) 仅显示重启文字，随后清掉 busy，允许继续操作；现有仓库仍绑定已关闭数据库。根页 Back 在 [SmartMistakeBookRoot.kt:240](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:240>) 只是把任务放后台，正常再次打开不保证新建进程。

在本轮新建的空白审计模拟器中完成“删除全部数据”，看到“请重新打开应用”，随后 Home 并重新启动入口；PID 仍为 4515。页面出现“本机学习数据暂时无法更新”，再点“清理孤立题图”得到 **`Error code: 21, message: Connection pool is closed`**。真实进程 force-stop 后再启动才能恢复正常启动页面。[错误截图](<D:/smart mistake book/.artifacts/user-visible-audit-2026-10-16/closed-operation.png>)、[界面树](<D:/smart mistake book/.artifacts/user-visible-audit-2026-10-16/ui-closed-operation.xml>)。

设备只复现了删除分支，未执行完整备份恢复。恢复分支的相同交接缺口由源码确认。补齐方向：完成后进入明确的进程级重启状态，停止使用旧仓库并提供有效的重新初始化/重启交接；补测真实关闭数据库之后的后续 UI，不能只验证 busy 复位或另开一个测试数据库。

### UV-05：备注写入成功，详情页没有读回新值

触发：详情页保存新增/修改的备注，留在原页观察正文，再次点击编辑。用户可能看到“已保存备注”，却仍看到旧内容；二次编辑还会把旧值填回输入框，再次保存可覆盖刚保存的新值。

[RoomMistakeDetailRepository.kt:109](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeDetailRepository.kt:109>) 的 observe/observeExact 实际是一轮读取后结束；[MistakeDetailRoute.kt:115](<D:/smart mistake book/feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt:115>) 不会在备注保存后重建读取流。同文件 `:454` 再次编辑使用旧 note，`:480` 保存成功只收起编辑和显示消息。

补齐方向：让详情真正观察记录变化，或保存成功后可靠刷新/更新当前详情状态。补测“保存后的正文 + 第二次打开编辑”，现有测试仅断言写口收到了备注。退出详情重新进入可以读到新值，不消除原页的错误反馈。

### UV-06：批量任务列表仍把已完成草稿当作可继续项

触发：批量页确认入库成功后返回批次，再点同页；或移除该页草稿后返回批次。列表仍显示“已保存，点此继续”，点击却进入“这道题已处理或不存在”。

[BatchImportRoute.kt:455](<D:/smart mistake book/feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/BatchImportRoute.kt:455>) 只依据 draftId 非空允许点击，`:548` 为 READY 固定显示继续文案；[RoomBatchImportRepository.kt:640](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomBatchImportRepository.kt:640>) 暴露旧 resultDraftId，不检查草稿状态。提交/移除将草稿置为 COMMITTED/ABANDONED，却不改变批量页；恢复查询只接受 EDITING。

补齐方向：批量页与草稿最终状态关联，已入库导航详情，已移除明确标注，只有可恢复草稿提供继续入口。增加“真实提交成功→返回原批次→再次点击”的组合验证。这与 KD-39 的提交回执窗口不同，本项在提交全部成功后也成立。

### UV-07：跳过末项没有会话结束状态

触发：最后一个知识点出题失败或无材料时点击跳过。题面与重试按钮消失，进度停在末项，既没有结束说明也没有完成按钮；页头返回仍可用。

[KnowledgeReviewSessionViewModel.kt:329](<D:/smart mistake book/feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionViewModel.kt:329>) 跳过后递增索引并清空题面/判决；[KnowledgeReviewSessionScreen.kt:79](<D:/smart mistake book/feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionScreen.kt:79>) 对 null currentEntry 没有结束 UI，完成按钮仅在存在 submittedChoice 时渲染。

同一生产 ViewModel 探针输出 `entry=null; can_continue=false; submitted_choice=null; completed=false`，UI 缺口由消费分支确认，尚未做设备末项跳过。[执行记录](<D:/smart mistake book/.artifacts/user-visible-audit-2026-10-16/review-state-probe.log>)。补齐方向：显式建模完成/全跳过状态并提供结束入口；验证最后一项跳过和全部跳过。现有单测只检查 null 字段，因此没有检查用户出口。

### UV-08：模型配置输入在屏幕旋转后丢失

[SecondaryScreens.kt:127](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt:127>) 用普通 remember 保存 provider、URL、model、protocol、API Key 等临时字段，Activity 重建后丢失，再按持久配置覆盖。首次配置会恢复空表单。

设备复现：输入非敏感服务商名 `audit_provider_20261016`，未保存，旋转到横屏再回竖屏；四个 EditText 从 `[audit_provider_20261016, 空, 空, 空]` 变为全空。[前后字段记录](<D:/smart mistake book/.artifacts/user-visible-audit-2026-10-16/form-rotation-evidence.json>)，原始 XML 同目录 `ui-form-before.xml` / `ui-form-after.xml`。未输入真实密钥、未调用模型。截图采集未成功，保留 XML 证据，不把空文件作为截图。

补齐方向：用适当的页面状态持有者保留未保存输入，敏感字段按既有密钥安全边界处理；验证配置变更期间保留表单和保存状态。无需为了旋转保留而把明文密钥随意写入持久状态。

### UV-09：通知频道重新允许后，提醒排程不一定恢复

触发：开启提醒，在系统关闭复习通知频道，等闹钟到点；随后在系统重开频道，再回应用并新进入提醒页。偏好仍为 enabled，但此前到点处理取消了闹钟且没有排下一次。

[ReviewReminderCoordinator.kt:43](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderCoordinator.kt:43>) 在不能投递时 cancel 并直接 return；[ReviewReminderPlatform.kt:114](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderPlatform.kt:114>) 确实检查频道关闭状态。重新开频道不产生偏好变化；[ReviewReminderScreen.kt:64](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderScreen.kt:64>) 新建时已读到 true，`:121` 只有 false→true 边沿才刷新，因而可能漏掉重排。普通 Activity resume 也没有其他刷新路径。

补齐方向：在合适的恢复入口对已开启提醒做幂等校准，或在无法投递时保留后续调度策略。补测关闭频道到点后重开、再新进入设置的路径。若原提醒页留在栈里感知到 false→true，或进程真正重启、主动改时间/开关，则可能恢复；不是不可逆永久失效。本轮现有协调器 6 项单测通过，但未设备测试这一情景。

### UV-10：隐藏答案区没有隐藏题面里的作答痕迹

[MistakePdfPagePlanner.kt:183](<D:/smart mistake book/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt:183>) 对每个 ChoiceGroup 无条件输出 selected 对应的“已选”，不受 includeAnswer 控制。产品配置将答案/解析安排为题后独立区域，但关闭该区仍在题面暴露已有选择。

本轮执行当前生产排版器，compact 与 practice_sheet 都使用 `includeAnswer=false`，结果均为 `selected_choice_visible=true; answer_heading_present=false`。[输出](<D:/smart mistake book/.artifacts/user-visible-audit-2026-10-16/pdf-answer-visibility-probe.log>)。这是排版计划执行证据，尚未逐页渲染最终 PDF；既有选择可能是学生错误选择，不能一概称为“泄露正确答案”。

补齐方向：明确练习导出是否保留作答痕迹，题目区默认不打印交互状态，需要回顾时放在显式选择的答案/作答记录区域；验证关闭答案区时各模板不带 selected 痕迹。现有 golden 反而固定了旧输出，单测通过不能证明符合重新练习语义。

### UV-11：空态按钮目的地与文案不一致

设备复现路径：我的 → 查看全部知识点 → 空态“去错题本看看” → 实际返回“我的”，没有打开错题本。[空态界面树](<D:/smart mistake book/.artifacts/user-visible-audit-2026-10-16/ui-mastery-empty.xml>)、[点击后目的地](<D:/smart mistake book/.artifacts/user-visible-audit-2026-10-16/mastery-navigation-evidence.json>)。

[LearningMasteryScreen.kt:73](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt:73>) 将该按钮接到 onBack；[SmartMistakeBookRoot.kt:865](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:865>) 注入的只是 popBackStack。补齐方向：单独提供去错题本的导航回调，并验证真实目的地。本项是现有按钮接错，不重复计入 AUD-12 的全库画像/关联错题导航扩展。

## 本轮覆盖、验证和边界

| 流程 | 本轮检查内容 | 证据边界 |
| --- | --- | --- |
| 录入、批量、分题、错题详情 | 草稿恢复/移除、批量页与分题生命周期、源图引用、列表、备注、归档 | 源码消费链；staging 保留原始 SQL 执行 |
| 讲题与知识点复习 | 诊断选择、提示、任务时间线、题面恢复、队列身份、跳过与完成 | 源码消费链；生产 ViewModel 两种状态探针 |
| 设置、存储、提醒、启动 | 配置重建、删除/恢复交接、通知权限与调度、启动后的旧句柄 | 独立模拟器复现配置旋转、删除后继续操作；恢复与提醒情景只读码 |
| 导出与学习掌握 | 答案开关/练习模板、掌握页空态与导航 | 生产排版器执行；空态按钮设备复现 |

本轮执行：

```text
.\gradlew.bat :app:assembleLocalFirstDebug :core:export:testDebugUnitTest :feature:library:testDebugUnitTest --console=plain
BUILD SUCCESSFUL in 1m 19s

.\gradlew.bat -I .artifacts/user-visible-audit-2026-10-16/audit-classpath.init.gradle :feature:review:testDebugUnitTest :app:testLocalFirstDebugUnitTest --tests com.tingyun.smartmistakebook.ReviewReminderCoordinatorTest :feature:review:writeAuditRuntimeClasspath --no-configuration-cache --console=plain
BUILD SUCCESSFUL in 7s
```

读取当前运行后的 JUnit XML：export 73、library 40、review 42、提醒协调器 6，共 **161 项，0 failure / 0 error / 0 skip**。app 仅运行指定协调器测试，不能据此宣称全 app 测试通过。[计数及套件](<D:/smart mistake book/.artifacts/user-visible-audit-2026-10-16/module-check-results.json>)；Gradle 两份完整日志保存在同目录。另用临时 Gradle init script 输出运行 classpath，Java 源文件模式执行生产类探针，Python 执行原始 SQL；这些工具脚本仅位于忽略的审计工件目录，没有修改项目构建配置或测试源码。

设备使用本轮新建的 `audit_user_visible_20261016`，serial `emulator-5556`，安装本轮 APK，SHA-256 为 `CCB6B270F4FCBACBCBB697190268F67DAF90E3FE0C9BCC1BA80910471345C305`。应用没有原始学习数据，只含首装资源。删除测试前核对了专用 AVD 名称；没有对原模拟器 emulator-5554 执行安装、卸载、清库或其他变更。没有运行 connectedAndroidTest。

以下不计入 11 项：小测答错缺少纠正解析（产品契约仍需明确）；模型配置在提交中取消可能失效（时序未复现）；排程设置写失败丢输入（未注入存储失败）。KD-39/41/42、AUD-11/12/13 等既有缺口不重复报新项。真实模型讲解/识图质量、真实相机/文件选择器、真实 PDF 切分视觉质量、带真实数据的进程回收与完整备份恢复，本轮均未做端到端验收。

审计交付 **Completion Gate PASSED**：有当前源码、当前构建/测试输出和分级复现证据。本门只表示本轮审计交付完成，不表示产品无缺陷或上述问题已修复。本轮只新增本报告及忽略的审计工件，未改业务源码、测试、配置或主线计划，未提交推送。
