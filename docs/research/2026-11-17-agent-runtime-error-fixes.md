# 智能体运行报错定位与修复（2026-11-17）

## 现场证据

- 真机 logcat（API 34 emulator-5554，debug 包）每次启动都有：
  `Uncaught applicationScope coroutine failure: java.lang.UnsupportedOperationException:
  Mistake edit persistence is not installed`，栈顶
  `RoomStudyDatabase.readPendingMistakeEditJobs` → `RoomMistakeEditRepository.resumePendingJobs`
  → `SmartMistakeBookApplication.onCreate`。
- `RoomStudyDatabase` 缺失 `MistakeEditJobPort` / `ExportRecordPort` 组级 / `readKnowledgeCatalogPhrases`
  / `observeMistakeSourceAssets` 的 override，端口回落到 `unsupported()`/空默认。
  历史实现仍在 `86801da8`，是在后续 UI 整合合并中丢失的。

## 根因与修复

1. **持久修改作业端口丢失**：恢复 `MistakeEditJobPort` 全部 9 个方法（真实 Room + 状态 CAS +
   basis 校验 + 图片 authority），并给启动恢复与后台作业协程加有界异常处理。
   影响：启动噪声与智能体 `EDIT_PROBLEM` 必败。
2. **导出记录组端口丢失**：恢复 `createMistakeExportGroup` / `read` / `observe` /
   `confirmMistakeAnswerPreview` / `startGroupedMistakeExport`。
   影响：`START_EXPORT` / 详细答案导出在建组第一步抛异常。
3. **知识目录 phrases / 题源观察口丢失**：恢复 `readKnowledgeCatalogPhrases` 与
   `observeMistakeSourceAssets`，避免分类/读面静默空。
4. **JS 宿主嵌套事实丢失**：`RUN_JAVASCRIPT` 内部 `NOTEBOOK_READ` 的条目、`READ_FIGURE` 的 grant、
   `GENERATE_FIGURE` 的图 id、`APP_CONTEXT` 的科目现在进入运行上下文并回传顶层；
   `callAction` 的 `entryId` 只在 knownEntryIds 内绑定目标。修复"刚检索到的题不能收藏/加入计划"。
5. **工具结果 6,000 字符硬上限**：`SELECT_QUESTION` / `APP_CONTEXT` 的完整题面很容易超限，
   旧实现抛 `IllegalArgumentException` 后被折成"查询没有完成"。现在超限显式省略 `document`
   （标记 `documentOmitted`），APP_CONTEXT 超限压成计数/截断列表；不再构造非法/半截 JSON。
6. **TC 解析容错**：rationale 截断到本地 200 字、terms 去空白/控制符/截断/去重、confidence
   与 intent.confidence 夹到 0..1、枚举大小写不敏感、本地动作参数 trim、重复请求按语义去重；
   减少"形状微差 → 整轮 INVALID_RESPONSE"。
7. **工具/动作广告与目标**：JS 引擎按 `visualEngineReady`（隔离服务 warm-up）广告；
   顶层动作附图改读与 JS 同一份 helper（大厅来源图+上下文图 / 讲题学生图）；
   `START_EXPORT` 无 entryIds 时回退当前题；已知条目/图/科目截断改 `takeLast` 保留最新事实。
8. **取消传播与 fail-closed**：新增的 runCatching 改为显式 catch 后重抛
   `CancellationException`；动作可用集探针失败时不再回退成"全部动作"，单个动作探针失败只裁该动作。
9. **诊断**：工具执行失败记录工具名+异常类；模型任务未知异常记录异常类（不记内容）；
   JS 未知工具/动作/非法 state 返回可操作错误码。

## 验证

- 单测门全绿：`:core:model :core:domain :core:data :core:database :core:export
  :feature:review :feature:library :feature:capture :feature:tutor`（`--continue`，2m5s）。
- 新增/更新：`TutorAgentToolWiringTest`（嵌套 NOTEBOOK_READ→action、嵌套 READ_FIGURE、大题面/
  大 APP_CONTEXT）、`TutorJavascriptRunnerTest`、`OpenAiNativeToolsProtocolTest`（归一化）、
  `TutorCapabilitySnapshotTest`（JS/读图就绪门）、`ProblemRevisionEditDatabaseInstrumentedTest`
  （真实 Room：创建/幂等/latest/待恢复/CAS/verify）与 `ExportRecordPortInstrumentedTest`。
- 真机：`ProblemRevisionEditDatabaseInstrumentedTest` 10/10、`StudyDatabaseInstrumentedTest`
  31/31、`ExportRecordPortInstrumentedTest` 1/1、`TutorAgentJsToolInstrumentedTest` 1/1、
  `VisualJsProgramInstrumentedTest` 6/6；安装后启动 logcat 不再有
  `Mistake edit persistence is not installed` / 未捕获协程失败。

## 未验证

- 真实第三方 Provider 的端到端工具环（本机无凭据）：归 UNVERIFIED。
- `ExportRecordPort.createMistakeExportGroup` 的真实建组走完整导出链（需先冻结答案 manifest）：
  由导出设备链覆盖；本批只证明 override 已安装且读/观察口可用。
