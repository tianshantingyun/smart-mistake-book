# 用户可感知缺陷补修（2026-10-15）

承接 [本轮审计 V-01～V-06](2026-10-15-user-visible-completed-feature-audit.md)。本批修复这 5 个新确认问题和 1 个恢复风险；导出链同时补上了影响本批正确性的 AUD-06 缓存输入缺失。业务实现未更改学习事实生成规则、判分规则、模型授权或数据库结构。

**设备数据事件。** 自动化设备测试结束后，被测应用与原私有数据目录被测试框架移除。本次执行前未先备份，是执行疏漏；重新安装后未找到能完整恢复原题目、历史和配置的备份。已向用户明确说明，用户选择“没有备份，保留重新安装状态”。设备按该选择保留新版新安装状态；没有用合成数据冒充原来的记录。新增 [设备测试数据保护说明](../testing/device-state-safety.md)。此事不改变下面的代码 / 测试结果，但不能据测试成功宣称模拟器原数据完整保留。

## 行为与改动

| 编号 | 修改后的行为 | 实现与验证 |
|---|---|---|
| V-01 | 搜索无结果时保留搜索框与清除出口，不再显示成空题库 | LibraryRoute 分开使用全库数量和当前结果数量；设备测试实际执行输入无结果、清除、恢复结果 |
| V-02 | 分页失效通知贯通到界面，保留总量信息；数据变化后刷新数量和分类，旧查询不能覆盖新查询 | database / data 的 MappingPagingSource 与搜索 RefreshingPagingSource 传递失效；LibraryRoute 响应 catalog 变化；ViewModel 取消旧元数据请求并核对查询身份 |
| V-02 附带问题 | 生产筛选项可以选中，导出数量描述完整候选结果 | 修正生产路径误用空内存 catalog 校验选项；按仓库当前结果总量显示导出数，分页禁用占位项；设备测试覆盖 80 个候选、仅加载一行的情况 |
| V-03 | 批量 PDF 每题保留自己的净化图与备注，自定义块序不跨题重排备注 | 增加 Image / Note 导出块，按题分组排版；位图逐张解码、绘制后回收；设备将两张不同颜色图片写入同一份 PDF 并检查两种颜色像素 |
| V-04 | 原题复习读取精确版本的正式题面，可展开全部原图；缺图有提示 | app 注入 MistakeDetailRepository.observeExact；复习页核对 entry / problem / revision 身份，复用结构化渲染与本地采样图片组件；设备覆盖两页图、旧版本不串图、缺图 |
| V-05 | 旋转 / Activity 重建不丢正在执行的操作；进程状态恢复不会虚构一个正在执行的任务 | StorageOperationsViewModel 持有状态和操作作用域；重建沿用 ViewModel，恢复运行标记时显示中断并解除 busy；执行中禁止离开此页；设备覆盖 Activity 重建与受控 SavedStateHandle 恢复 |
| V-06 | 答案卷保留答案区；没有答案的题逐题注明，不会静默消失 | 分页器按题号列出缺失，记录 NO_ANSWER_AVAILABLE；导出设置和完成通知说明仅附已有答案；保留既有“已有已选项文本”的数据语义，不自动生成答案 |
| AUD-06 | 修改可见备注或净化图内容后生成新缓存；未勾选备注时，修改备注不影响缓存 | 指纹纳入实际渲染的备注、图片 URI 和资产字节摘要；升级缓存版本使旧的缺内容产物失效；单元测试覆盖可见 / 隐藏备注与同 URI 不同图片摘要 |

图片设备测试还发现并修复了旧 URI 解码问题：真实资产会使用 `file:/...`，原逻辑只去除 `file://`。现在通过标准 File URI 读取路径，支持标准转义。长图按可打印高度等比缩放，避免一张长图使整份导出失败。

最后的 Lint 检查拦住了导出成果页已有的 5 处 API 26 日期调用。已在 MistakeExportHubRoute 使用兼容 API 23 的格式化方式，保留 `MM-dd HH:mm`，并按当前系统时区显示。新回归先复现了切换时区后仍显示旧时区的问题，再验证修复；没有提高最低版本或抑制 Lint。

## 主要文件

- 错题本：[LibraryRoute](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryRoute.kt)、[LibraryViewModel](../../feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryViewModel.kt)、[database 分页包装](../../core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/MappingPagingSource.kt)、[data 分页包装](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/library/MappingPagingSource.kt)、[搜索包装](../../core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomLibrarySearchStore.kt)。
- 导出：[单题输入与指纹](../../core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt)、[批量输入](../../core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfBatchEligibility.kt)、[分页器](../../core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt)、[渲染器](../../core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DeterministicMistakePdfRenderer.kt)、[导出设置提示](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt)、[通知提示](../../app/src/main/kotlin/com/tingyun/smartmistakebook/ExportPdfWorker.kt)。
- 原题复习：[题面与原图组件](../../feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/CapturedReviewQuestionContent.kt)、[复习屏幕](../../feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/CapturedReviewSessionScreen.kt)、[精确版本接线](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookDestinations.kt)、[仓库注入](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt)。
- 存储：[生命周期状态](../../app/src/main/kotlin/com/tingyun/smartmistakebook/StorageOperationsViewModel.kt)、[页面接线](../../app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt)。

## 验证记录

全部材料在 `.artifacts/user-visible-fixes-2026-10-15/`，红灯记录与最终通过结果分开保留。

### 单元测试与组装

```powershell
.\gradlew.bat :core:model:test :core:domain:test :core:database:testDebugUnitTest :core:data:testDebugUnitTest :core:export:testDebugUnitTest :feature:library:testDebugUnitTest :feature:review:testDebugUnitTest :feature:tutor:testDebugUnitTest :app:testLocalFirstDebugUnitTest :app:testStrictOfflineDebugUnitTest :app:assembleLocalFirstDebug :app:assembleStrictOfflineDebug --rerun-tasks --console=plain
```

该轮 `BUILD SUCCESSFUL in 3m 16s`，293 个任务实际执行；2,396 项测试，失败 / 错误 / 跳过均为 0。覆盖当前共享工作区的集成状态，不能把全部用例都算作本批新增。

### 设备回归

在 Android 14 / API 34 模拟器上顺序执行，每组使用各自的 class 过滤：

| 任务 | 测试类 | 结果 |
|---|---|---|
| `:feature:library:connectedDebugAndroidTest` | LibraryRepositoryInstrumentedTest + MistakeExportHubInstrumentedTest | 10 / 10；包括无结果可清除、仓库接口夹具筛选、数量刷新、完整导出计数、迟到查询不覆盖，以及导出成果页原有 5 项回归 |
| `:core:export:connectedDebugAndroidTest` | MistakePdfCleanImageExportInstrumentedTest + MistakePdfExporterInstrumentedTest | 14 / 14；包括批量两色图片的实际 PDF 像素检查 |
| `:app:connectedLocalFirstDebugAndroidTest` | CapturedReviewDocumentInstrumentedTest + StorageOperationLifecycleInstrumentedTest | 5 / 5；包括正式题面、两页原图、错误版本隔离、缺图、Activity 重建、受控进程状态恢复 |

主体补修后先通过 24 / 24；日期兼容补修后复验 library / app，并增加导出成果页 5 项检查，最终覆盖 **29 / 29**。最终 XML 副本在 `device-results/`，汇总在 `device-results.json`。

### 回归敏感性

- `paging-red.log`：数据库分页包装两项用例在修改前失败，分别暴露失效通知和页计数丢失。
- `library-red.log`：原有实现不能满足新增的搜索、筛选、数量刷新、导出计数检查；修后通过，随后增加迟到查询检查。
- `export-red.log`：批量备注与无答案提示用例在修复前失败。旧测试中“无答案时完全不显示答案区”的预期，已按本次明确的缺失提示行为更新。
- `images-device-red.log`：真实 File URI 的批量图片暴露路径解析错误；修复后像素检查通过。
- `tall-image-red.log`：长图原本触发 PAGE_LIMIT_EXCEEDED；修复后等比缩放，并通过模块回归。

### 最终核验补记

日期兼容补修后，重新执行 `:feature:library:testDebugUnitTest`、两个 app 单测变体、两种 Debug 组装，以及 `:core:export:lintDebug :feature:library:lintDebug :feature:review:lintDebug :app:lintLocalFirstDebug`。`BUILD SUCCESSFUL in 1m 39s`；最终 XML 汇总 **2,397 项单元测试，0 失败 / 错误 / 跳过**，四份受影响的 Lint 报告 **0 错误**，保留已有警告。日志为 `final-lint-build.log`，汇总为 `unit-results.json`、`lint-results.json`。

`export-time-red.log` 保留了时区切换回归的原始失败。日期补修后的设备复验记录在 `post-format-library-device.log`、`post-format-app-device.log`。

### 提交前独立核验

提交前从 `a15aebb7` 隔离出暂存版本，补齐了基线遗漏的 `KnowledgeMaterialContentAttribute`、`TutorLobbyOutput.requestedLocalActions` 及其动作交接，以及 `TutorPlanSink.setPlanStartError` 回调接线。`TutorSessionPanel` 仅纳入这一行必要接线，其余未提交改动保留在原工作区。

按 Git 对象原始字节校验全部 2,249 个文件后，独立运行上述十个单测范围、两种 Debug 组装和四个 Lint 范围。最终 `BUILD SUCCESSFUL in 1m 40s`：**2,395 项单测，2,394 通过、1 跳过、0 失败 / 错误**；跳过的 `StagingPackRetrievalBenchmarkTest` 缺少 `build/kb-staging/moe-2025-four-subjects-v1.json` 夹具，未把该基准算作通过。两个 APK 存在且已记录 SHA-256，四个 Lint 范围均为 0 错误，保留已有警告。

提交版本的日志为 `pre-push-raw-snapshot-verification.log`，汇总为 `pre-push-unit-results.json`、`pre-push-skipped-tests.json`、`pre-push-lint-results.json`、`pre-push-apks.json`。这组结果对应独立提交内容；前述 2,397 项是补修阶段共享工作区的结果。设备测试未重跑，模拟器继续保留用户选择的新安装状态。

独立导出时发现默认 CRLF 转换会改变冻结词表及 TSV 夹具字节；本次验证恢复为 Git 原始对象字节后通过。冻结资产的跨平台检出保护已归入 AUD-15，仍需单独处理。

## 保留边界

- V-05 已验证 Activity 重建和保存状态恢复的状态机；没有对真实大备份的恢复过程做强杀进程实验。数据库 / 文件回滚仍依赖既有恢复机制。本批没有将备份改为可跨进程自动续跑。
- 没有重评真实模型的图形转写、公式质量或讲题成功率；原图入口提供核对条件，不将模型图注当成原图等价物。
- 原审计中的 AUD-01/02/03/05、KD-37/38 继续保留；本批不把它们算作已修复。AUD-06 的输入指纹缺口已补修，真实用户操作全链仍应按主线四层证据继续积累。
- 未执行发布或数据库迁移；提交范围包含本批补修及独立构建所需的基线类型 / 接线补齐，保留共享工作区原有的其他改动。

Completion Gate PASSED（本批代码补修）：验证范围与未实测边界如上；模拟器数据损失已单独披露，用户明确选择保留重新安装状态。
