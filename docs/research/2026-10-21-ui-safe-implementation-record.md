# UI 无冲突优先批实施记录：UF-08、UF-18、UF-19

> 提交批注（2026-10-21）：用户要求提交推送且不重跑测试。下文“未提交/未推送”描述实现完成时的历史状态，实际提交与远端结果以Git历史为准。

日期：2026-10-21。用户授权：从已写好的 UI 改进中选择与现有工作没有冲突的项实施。原依据为[UI计划第6、7节](../superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)及[设计8.4、9.3节](../superpowers/specs/2026-10-19-ui-ux-redesign-design.md)。本批仅实现以下三项，不代表整套 UI 改版、UL修复或WG接线完成。

## 1. 工作边界与冲突检查

- 开始时 HEAD 为 `5bc83136`。用 `git status` 和最近批次占用记录核对，三个生产文件均干净：`BoundedLocalImage.kt`、`AttachedImageCard.kt`、`TutorMistakePicker.kt`。本会话只有root活跃；现有其他会话状态和文件快照仅用于规避冲突，未向其他会话发消息。
- 避开已有脏的复习、批量导入、数据/模型、导出排版及其测试文件；批次16记录占用的导出Sheet/会话VM也不接手。未改构建依赖、业务合同、Room结构、生成策略、预算台账或正式题目内容。
- 原有 `emulator-5554` 不操作。本批启动独立 `audit_user_visible_20261016` / `emulator-5562`，仅安装/运行feature:tutor的组件测试APK；不安装主应用新包、不提交学习记录、不发送模型请求。测试PNG、损坏文件和可控慢读取都位于测试包的数据区。
- 本轮快照、日志、APK指纹、截图和验证结果保存于 `.jez/artifacts/ui-safe-implementation-2026-10-21/`。没有提交或推送；其他在飞源码保持原工作树内容。

## 2. 已实现行为

| 原任务 | 实现与源码 | 当前完成边界 |
|---|---|---|
| UF-08 | [TutorMistakePicker](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorMistakePicker.kt)：非空query显示可读屏的“清空搜索”，一次恢复原候选；现有章节/知识点摘要自然换行 | 清空、候选恢复、点选/取消回调、保存状态恢复、长标签1.5倍字号已验；不改变筛选、顺序和附件绑定，UL-01另待 |
| UF-18 | [BoundedLocalImage](../../core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/BoundedLocalImage.kt)：以uri/region隔离producer的State；换源立即不消费旧位图，沿原采样解码；加载占位保留最近图像尺寸并受当前高度上限约束 | 慢图切换、同uri换裁剪区、下方内容位置、迟到读取与真实像素已验；未上线JS/WG消费端仍待原门 |
| UF-19 | [AttachedImageCard](../../core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/AttachedImageCard.kt)：本地解码失败进入当前卡的可见失败态，图题/已有摘要保留；失败不授予重生权限；显式收起再展开允许重新读同一URI | 损坏图、收起/展开、同URI修复、换URI恢复、保存状态恢复已验；不改生成台账、不自动重生，现有生成回调断言为0 |

图片变更落在共享消费层，JS与IMAGE_MODEL继续使用同一产物消费规则；本批没有实现新JS通道，也没有新增按用途选后端的逻辑。原图/转写/讲解/导出仍由现有调用方消费组件，完整应用链和未上线通道要继续沿UI-08/WG逐入口验收。

## 3. 缺陷复现、修复与独立复核

先写真实Compose组件回归，编译测试包后在原生产源码上运行：6例中5例按预期失败，分别为旧URI图片残留、旧裁剪残留、解码失败无提示、没有清空入口、标签单行裁切。迟到取消的既有行为原已通过，不把它说成新增缺陷。

初次修复后发现标签测试读的是合并后的可点击行，而非标签文本的布局结果，改为unmerged文字节点；这属于测试定位修正。另用下方文字位置做新回归，证明只隐藏旧图仍会从64px高度塌到0；补有界占位后通过。

一次独立只读复核提出两项，均已处理：

1. 新失败态不能锁死同URI的本地恢复。新增“原URI文件修好后显式重开”的回归，先观察超时失败，再在显式重开时仅重置本地解码状态；不调用生图。
2. 迟到用例只看描述无法证明位图正确。改用红色B/绿色C真实PNG，等待B两次读取的文件描述符关闭，再检查C的实际中心像素为绿色。生产取消机制保持原实现。

## 4. 本轮运行证据

命令在仓库根目录执行，`GRADLE_USER_HOME`定向仓库现有`.gradle`缓存：

```powershell
.\gradlew.bat :core:ui:testDebugUnitTest :feature:tutor:testDebugUnitTest :feature:tutor:assembleDebugAndroidTest :core:ui:lintDebug :feature:tutor:lintDebug --console=plain
```

结果：`BUILD SUCCESSFUL`，退出0；core:ui **41例**、feature:tutor **246例**单测通过，合计**287例**，失败/错误/跳过均0。两模块lint无Error/Fatal，分别1/16条Warning，未落在本轮三个生产文件；编译仍有Compose测试API、剪贴板及Gradle弃用提示，不宣称零警告。

独立模拟器API34、420dpi。对同一测试包在840×2100（320dp宽）和945×2100（360dp宽）分别运行：

```powershell
adb -s emulator-5562 shell am instrument -w -r -e uiEvidence true -e class com.tingyun.smartmistakebook.feature.tutor.LocalImageUiInstrumentedTest,com.tingyun.smartmistakebook.feature.tutor.TutorMistakePickerUiInstrumentedTest com.tingyun.smartmistakebook.feature.tutor.test/androidx.test.runner.AndroidJUnitRunner
```

两次均为 **`OK (7 tests)`**：5例图片回归、2例选题器回归；长标签场景在组件中设置fontScale=1.5。恢复测试使用Compose的StateRestorationTester，不能等同完整主应用进程重启/横竖屏走查。

截图为真实组件测试宿主（MaterialTheme及合成内容），不是完整主应用画面或用户题库：[320dp长标签](../../.jez/artifacts/ui-safe-implementation-2026-10-21/screenshots-320dp/picker-long-label-font150.png)、[320dp图片失败](../../.jez/artifacts/ui-safe-implementation-2026-10-21/screenshots-320dp/image-local-failure.png)、[360dp清空后候选](../../.jez/artifacts/ui-safe-implementation-2026-10-21/screenshots-360dp/picker-cleared.png)。字体/间距/品牌视觉的全局验收不据此关闭。

## 5. 仍保留在原计划的工作

本批仅关闭三项的代码和定向组件回归；UI-04/07/08/11的大任务、UL-01等竞态、其他UF项保持未完成。整应用数据/导航/真实图产物消费、其他Android版本、横屏/平板、TalkBack人工操作、生成式JS接线和最终视觉对照未在本轮运行。不要用本批结果覆盖PRD/WG或发布门。
