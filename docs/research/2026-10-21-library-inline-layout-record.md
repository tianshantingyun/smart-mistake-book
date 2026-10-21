# 错题本题图外显与排版实施记录

> 提交批注（2026-10-21）：用户要求提交推送且不重跑测试。下文“未提交/未推送”描述实现完成时的历史状态，实际提交与远端结果以Git历史为准。

日期：2026-10-21。范围：用户要求在错题本直接看题图，并改善现有页面排版。使用Product Design对实际页面做局部审查，随后修改原生Compose实现。代码及本轮定向验证完成，未提交推送；不代表整个UI改版或主应用验收完成。

## 观察与决定

本轮重新运行真实`LibraryRoute`，保存并打开改前截图，再在同设备、同题目、同主题下比较改后画面。原布局的巨型标题、两条整宽入口、常开筛选和整宽导出按钮占用了首屏主要空间；题图虽已接线，却被挤到下方。已有纸色/玉绿主题、搜索/收藏/筛选与后台成果入口继续沿用。

改为紧凑页头、搜索、结果工具栏、按需筛选、独立图文题卡。标题和摘要最多两行，移除重复学科圆标和进入箭头；普通学习状态用可读的次级文字，避免整张题卡同时争夺注意力。输入框使用最小高度，大字号可增长；原生按钮保留48dp触区，筛选标签可横向滚动，选中状态与展开状态有语义。

用户没有要求新增逐题选择。旧设计中的选择态安排已撤回；紧凑导出仍针对**当前筛选的全部匹配题目**及既有上限，页头PDF图标仍是导出成果永久入口，空库也保留。搜索无结果原来未进入空态分支，本轮补齐已有空态与清除恢复入口。

## 实现范围

| 文件 | 变化 |
|---|---|
| `feature/library/.../LibraryRoute.kt` | 紧凑布局、按需筛选、图文题卡、分页稳定ID、图片读取接线、搜索空态 |
| `feature/library/.../LibraryToolbar.kt` | 页头、结果工具栏和可撤销的现有条件摘要，专用于本页 |
| `feature/library/.../LibraryInlineImages.kt` | 只读本地来源图片、局部加载/失败、多图顺序、有界按比例显示 |
| `feature/library/.../LibraryModels.kt` | 仅UI内存中的图片版本键，绑定修订ID或条目更新时间，防同题修订后显示旧图 |
| `feature/library/.../LibraryInlineImagesInstrumentedTest.kt` | 6例图片呈现及交互回归 |
| `feature/library/.../LibraryLayoutInstrumentedTest.kt` | 4例首屏密度、筛选摘要、空搜索与窄屏大字号回归 |
| `feature/library/.../LibraryBatchExportEntryInstrumentedTest.kt` | 适配先展开筛选，保留原导出顺序与空库成果入口断言 |
| `feature/library/.../LibraryRepositoryInstrumentedTest.kt` | 保留原仓储回归，补排序/时间范围接线，共6例 |

根入口已经传入`MistakeDetailRepository`，本轮不新造数据通路。只有懒列表组成的题目读取详情来源；图片复用`BoundedLocalImage`本地解码，不访问网络或自动生成。现代题图按题面证据中的来源阅读顺序排列，旧题沿已有来源展示；已有净图优先。多图逐张展示，无图不伪造图片错误，损坏或缺失图保留题目文字及局部提示。图片与题干仍进入同一道题，收藏按钮只修改原收藏字段。

未修改其他在飞的复习、批量录入、模型任务、导出业务、数据库或共享ViewModel；共享图片组件沿上一批既有实现使用。本轮没有新依赖、Room迁移或模型调用。两种生图方式的统一判定规则保持原合同。

## 本轮验证证据

产物目录：`.jez/artifacts/library-layout-2026-10-21/`，内含HEAD/源文件基线、构建与设备日志、机器可读验证清单及截图。第一阶段题图接线证据另保存在`.jez/artifacts/library-inline-images-2026-10-21/`。

1. 修改排版前，新加4例UI回归在原布局均失败，原因分别对应未收起的筛选、缺少筛选切换入口和缺失搜索空态；见`red-device.log`。
2. 最终执行：`./gradlew.bat :feature:library:testDebugUnitTest :feature:library:assembleDebugAndroidTest :feature:library:lintDebug --console=plain`，`BUILD SUCCESSFUL`；JVM为52例、0失败/错误/跳过，lint为0 Error/Fatal、8 Warning，保留仓库既有警告与弃用提示。
3. 最终在独立`emulator-5562`、API34运行上述4个测试类，`OK (18 tests)`。覆盖内存/分页图片、现代多页顺序、同题修订换图的真实像素、损坏图、无图、收藏与开题分离、展开/收起/移除条件、排序/时间窗口、空搜索、分页全量数量和当前筛选导出顺序。
4. 默认视口360dp、fontScale1.0，首条题目正文起点约194.29dp，低于240dp目标；同题前后截图一起检查。另以320dp、fontScale1.5检查页头动作不重叠，长标题换行，滚动可达所有筛选标签。

截图为真实Compose页面在测试宿主内的渲染，题目及PNG为合成测试资料，不写入用户错题库。没有把这些截图写成整个应用、所有权限和系统状态的端到端证据。

- [改前](../../.jez/artifacts/library-layout-2026-10-21/before.png)
- [改后](../../.jez/artifacts/library-layout-2026-10-21/after.png)
- [窄屏大字号](../../.jez/artifacts/library-layout-2026-10-21/large-type.png)

## 仍需后续验证

完整主应用返回、进程恢复、TalkBack、其他设备与主题覆盖沿UI-11继续。分页/查询失败收敛与归档恢复回执沿UL-06/07继续；本轮保持已有入口，未宣称修复这两项。极长页面、单题大量图片与长列表的内存、帧耗、分页追加和滚动恢复尚无性能证据，不能以本地有界解码替代验收。新JS产物与其他未上线生图消费链沿原WG/UI-08计划验。
