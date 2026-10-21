# 错题本视觉美化记录

> 提交批注（2026-10-21）：用户要求提交推送且不重跑测试。下文“未提交/未推送”描述实现完成时的历史状态，实际提交与远端结果以Git历史为准。

日期：2026-10-21。承接用户“自己去找一些技能，把这个美化一下”。采用本机已有`ui-polish`与`frontend-design`，直接修改Android Compose页面；未安装新的技能或前端依赖，未提交推送。范围是上一批错题本列表，其他在飞修改保持原状。

## 视觉决定与代码

选择清爽的题目手册方向，沿用纸色/玉绿主题与现有原生字体，不增加无数据依据的统计、装饰入口或动画。将浏览重点放在题图上，避免大标题、摘要和图像同时争夺注意力。

| 文件 | 本批变化 |
|---|---|
| `feature/library/.../LibraryQuestionCard.kt` | 页面专用题卡：图片在上，标题/独立收藏/摘要在下，科目路径与状态标签分开；减轻边框，整卡仍进入同一题 |
| `feature/library/.../LibraryRoute.kt` | 复用题卡，页边距收至16dp，搜索平时用浅色面、聚焦强调边界，空态录入按钮取消硬高度 |
| `feature/library/.../LibraryToolbar.kt` | 成果入口在常规宽度增加“文件”文字；窄屏/大字保留图标与完整语义；展开/已筛选按钮强调状态，已选条件使用原生可撤销Chip |
| `feature/library/.../LibraryInlineImages.kt` | 仅调整图片边距与圆角，保留本地读取、有界比例、修订身份、来源顺序和失败提示 |
| `feature/library/.../LibraryInlineImagesInstrumentedTest.kt` | 保留6例图片回归，增加两张题图与手动题混排、第二题导航身份回归及连续浏览截图 |

本批没有修改筛选、收藏、导出范围、图片生成方法或仓储写入规则。图片仍为已保存本地资料；未调用生图模型，也没有重新生成题图。页面专用组件不推广为整个应用的新设计系统。

## 当前验证

产物位于`.jez/artifacts/library-polish-2026-10-21/`，包含本批源码基线、构建/设备日志、截图与校验清单。先在独立API34模拟器运行原页面并打开改前截图，再对同题、同主题、同视口截图一起比较；另检查连续多题和320dp/1.5倍字。

- 执行`./gradlew.bat :feature:library:testDebugUnitTest :feature:library:assembleDebugAndroidTest :feature:library:lintDebug --console=plain`，构建成功；52例JVM单测无失败/错误/跳过，lint 0 Error/Fatal、8 Warning。
- 独立`emulator-5562`运行`LibraryLayoutInstrumentedTest`、`LibraryInlineImagesInstrumentedTest`、`LibraryBatchExportEntryInstrumentedTest`、`LibraryRepositoryInstrumentedTest`，19例通过。覆盖现有分页/搜索/筛选/排序/时间/导出合同、损坏/无图/修订换图、多页顺序、收藏不导航、图片和整卡导航身份。
- 默认首屏题卡顶部低于240dp。此处测试标签从上一批题内文字行移到整卡，测量口径不同，不将两个数字写成同口径的下降比例。

截图是实际Compose页面的测试宿主渲染，PNG和题目是本地合成测试资料，未写入用户错题库。展示连续题目用于判断密度，不编造用户已有题目数量。

- [同题改前](../../.jez/artifacts/library-polish-2026-10-21/before.png)
- [同题改后](../../.jez/artifacts/library-polish-2026-10-21/after.png)
- [连续题目](../../.jez/artifacts/library-polish-2026-10-21/gallery.png)
- [窄屏大字号](../../.jez/artifacts/library-polish-2026-10-21/large-type.png)

完整主应用导航、进程恢复、TalkBack、其他设备、极长题图/大量图片性能以及UL-06/07仍沿原计划待验。原生组件的焦点/选中语义与大字号画面检查不等于完整无障碍认证。

## 用户反馈补充：文字可读性

用户指出上版文字分辨度低、视觉对比度弱，本批用`accessibility`检查并调整同页4个生产文件。不是以名义对比度达标否定用户感受：上版摘要仅13sp、状态仅12sp，整体偏小偏细；本批同时提高实际字号、字重和字色。

| 元素 | 上版 | 本批 |
|---|---|---|
| 题名 | 16sp半粗 | 18sp半粗 |
| 摘要 | 13sp常规、InkSecondary灰字 | 15sp中等、Ink近黑字 |
| 科目/状态 | 12sp灰/绿字 | 14sp中等、Ink近黑字 |
| 页头/工具栏/搜索提示 | 灰字或绿字、中等字重 | 深色字，动作半粗，提示/输入中等字重 |
| 图片 | 卡内左右各12dp内缩 | 取消左右内缩，完整等比放宽显示 |

`SmartColors`共享主题保持原状；只调整本页消费的既有字色和排版令牌。没有改原图像素、解码规则、来源顺序、生成方法或回调合同。原图本身缺失的细节不由UI字色计算证明得到恢复。

证据目录：`.jez/artifacts/library-readability-2026-10-21/`。改前图沿用上一批已验证的同数据截图，改后重新渲染；`contrast.json`读取实际主题源色，以sRGB相对亮度计算不透明正常态文字/底色：题卡Ink/OnJade约15.158:1，掌握标签Ink/JadeSoft约12.138:1，工具栏与普通标签Ink/Paper约14.439:1。它不测图片内文字，也不是全应用无障碍认证。

本批仍执行模块单测、测试APK构建、lint与原19例设备回归，重点复验窄屏大字、收藏/导航分离、筛选/导出和题图呈现。机器校验清单、日志及截图保存在同目录，完整主应用与TalkBack等边界仍按前述待验项执行。

首次构建遇到既有在飞文件`core/data/.../ConfiguredCleanImageGenerator.kt`缺少`FigureDispatchParent`导入，同一缺失导致5条Kotlin错误。为恢复本页验证，仅插入该类型的一个import，保留该文件原有全部其他内容；修改前副本与单行插入断言留在证据目录。没有修改该模块的生图行为，也没有跳过失败任务继续宣称构建通过；重试结果另存`final-build.log`。

首轮设备用例18/19通过，剩余用例原先假定第三道手动题已被懒列表组合；字号增大后它移出了首屏。该用例改为通过真实列表滚动查找目标，再保留原有正文和导航身份断言，未修改产品布局去迎合旧卡片高度。最终构建与设备结果分别以`verified-build.log`、`verified-device.log`为准。

- [调整前](../../.jez/artifacts/library-readability-2026-10-21/before.png)
- [调整后连续题目](../../.jez/artifacts/library-readability-2026-10-21/after.png)
- [窄屏大字](../../.jez/artifacts/library-readability-2026-10-21/large-type.png)
