# 错题本布局与视觉模块收敛

日期：2026-10-26。用户反馈当前界面不够紧凑、成熟，并进一步指出“错题页里面的视觉模块性还不是很强”。本轮以实际错题本、复习、讲题及“我的”页面截图核对，修正错题本列表和详情的视觉分区，沿用已批准的青绿配色、Claude参考方向及现有原生组件。

后续修订：用户查看本批详情效果后明确要求不再缩略，详情图片改为直接全宽呈现；默认收起/缩略高度及相应49项历史验证不能作为新规则的当前结果。最新规则与证据见[详情图片直接显示](2026-10-26-detail-images-direct-display.md)。

## 问题与处理

1. 卡片先显示大幅图片，题名和状态落在后面，浏览时缺少身份信息。改为题名/收藏、分类/状态、题图的顺序；题图继续直接呈现。
2. 图片末尾再放来源和放大操作，卡片被分成上下松散两段。将来源和放大放在图头一行，多页页码并入来源说明；统一内边距，收紧图片和卡片内容的关系。
3. 图片已经给出题目，下面仍重复文字摘要。图片可用时不重复显示；手动题、无图或首图不可用时保留摘要，不以版面紧凑为由丢失后备内容。
4. 页面标题与其他文字权重相近，导出入口使用PDF图标却实际包含多种格式。加强页题层级，改为通用文件夹图标；缩小收藏与状态图标的视觉尺寸，保留原触区和文字可读性。
5. 实际空列表仍可能露出导出，还会在无筛选时提示清除筛选。以分页仓储的无筛选计数确定空库，只有结果大于0才显示导出；已有搜索/筛选条件时保留清除和调整入口。
6. 列表题名、元信息与图片没有清楚边界。将题名、分类、来源、掌握状态与收藏/放大放在紧凑信息头，使用浅底和细分隔线；图片占满卡片正文宽度。普通字号采用两层信息，不再多占一整行显示来源。无图、图片加载和失败时仍有文字内容；正常图片不重复摘要。
7. 详情的题面、原图、讲解图和备注主要靠标题与空白区分。引入只负责呈现的`MistakeSectionCard`，统一浅底标题、内容底色、1dp细边界和16dp模块间距；题面编辑/导出归入题面模块，备注编辑归入备注标题。原图和讲解图维持默认收起，使用已有`ClaudeDisclosure`展开动效；大字/窄屏标题与操作分行，触区不少于48dp。原有内容顺序、资产角色选择、历史版本只读及备注保存合同保持。
8. 讲题输入区的阻断原因和设置动作显得松散。放入同一中性浅底区域，普通宽度同排、大字/窄屏分行；不改变可发送条件、附件或设置回调。

不改原始图片、不自动裁掉图中内容、不发起生图，不新增业务功能或依赖。JS/IMAGE_MODEL统一判定和产物合同不受影响。其他页面继续沿既有计划检查，不据此宣称全应用已达到设计验收。

## 实现范围

- 8个生产文件：错题卡、图头与后备内容、列表空态/导出可用性、页头、详情模块和共享讲题输入说明。
- 5个测试文件：空态判定、列表布局/图片、大字号输入说明、详情分区与模块实际绘制。
- 修改前原始字节、13文件摘要及本轮差异保存在`.jez/artifacts/library-layout-2026-10-26/`。不以整个共享工作区diff冒充本轮修改。
- 静态核对确认图片选择/来源排序、讲题可用性策略、备注编辑/保存/失败处理保持原样；既有测试锚点保留，只新增题面模块锚点。

## 验证

- [x] 源码复核、432项相关单元测试（library 73、tutor 258、app 101）、两模块lint和localFirst/strictOffline主包及相关测试包构建；`module-final-build.log`为BUILD SUCCESSFUL，1m55s。lint为0错误，保留library 8/tutor 17项警告。
- [x] 题名在图片上方、图头操作同排、正常图片不重复摘要、图片失败/手动题保留摘要；元信息加载时保留摘要，不出现只剩加载提示的空卡片。
- [x] 0结果不显示导出、空库与筛选空结果区别、大字/窄屏及长图离屏读取；沿原视口解码合同，不把本轮功能回归当性能合格。
- [x] 模块标题/内容实际底色有区别，折叠内容不提前出现，大字操作留在边界内；详情分区、角色隔离、展开恢复、备注保存/取消与历史只读定向回归。
- [x] 当前源码构建的APK在独立`emulator-5562`上12类49项定向回归全部通过，无遗漏/跳过；主应用及三个测试包的安装后SHA256与产物相符，副本和方法清单见`final-device-summary.json`。strictOffline完成构建，未另作设备全流程验收。
- [x] 8张最终原始PNG、尺寸和摘要留证，已查看默认列表/详情、大字号列表、实际主应用空库和讲题输入画面；现有计划与进度记录已同步。未提交推送。

验证命令使用仓库Gradle包装器和串行执行：

```text
./gradlew.bat :feature:library:testDebugUnitTest :feature:tutor:testDebugUnitTest :app:testLocalFirstDebugUnitTest :feature:library:lintDebug :feature:tutor:lintDebug :feature:library:assembleDebugAndroidTest :feature:tutor:assembleDebugAndroidTest :app:assembleLocalFirstDebug :app:assembleStrictOfflineDebug :app:assembleLocalFirstDebugAndroidTest -Pksp.incremental=false --continue --console=plain --no-daemon --no-parallel --max-workers=1
python -X utf8 .jez/artifacts/library-layout-2026-10-26/run_layout_suites.py --select NativeUiAdoptionInstrumentedTest,MistakeSectionCardInstrumentedTest,LibraryStatePresentationInstrumentedTest,LibraryLayoutInstrumentedTest,LibraryAdaptiveActionsInstrumentedTest,LibraryInlineImagesInstrumentedTest,LibraryPendingTasksEntryInstrumentedTest,MistakeDetailRoleSectionsInstrumentedTest,MistakeNoteAndArchiveInstrumentedTest,MistakeRelationAndRevisionInstrumentedTest,TutorComposerNoticeLayoutInstrumentedTest,TutorComposerInteractionInstrumentedTest
```

本轮最终证据为`module-final-build.log`、`unit-and-lint-summary.json`、`module-device.log`、`final-device-summary.json`、`static-checks.json`及`screenshots.json`。初期31项绿例和旧图另存为前模块版本，不能替代当前49项结果。生成差异文件时曾因Windows换行写法导致反向检查失败，修正证据脚本的换行处理后，当前`task-only.patch`反向只检查通过，未对源码执行反向应用。

## 页面预览

- [默认列表：测试夹具题目，实际列表组件](../../.jez/artifacts/library-layout-2026-10-26/screenshots/library-gallery.png)
- [详情模块：测试夹具题目，实际详情组件](../../.jez/artifacts/library-layout-2026-10-26/screenshots/mistake-detail-modules.png)
- [窄屏大字号：组件压力场景](../../.jez/artifacts/library-layout-2026-10-26/screenshots/library-layout-large-type.png)
- [实际主应用错题本：当前空库状态](../../.jez/artifacts/library-layout-2026-10-26/screenshots/main-library.png)

## 验收边界

组件截图使用明确的合成题目；实际MainActivity截图单独标注，不能将测试夹具当用户数据。额外尝试通过`uiautomator dump`等待实际列表稳定时，未生成指定XML，后续读取失败；保留`actual-library-capture.log`，不计成功，也没有据此修改设备设置、清空应用数据或绕过受保护页面。

原性能、release/profileable实体机、目标学生走查、人工朗读和完整发布流程门继续开放。此前domain的两项点预测金样失败不属于本轮验证结果，未关闭。构建/定向回归只证明本轮范围，不等于全软件已通过验收。
