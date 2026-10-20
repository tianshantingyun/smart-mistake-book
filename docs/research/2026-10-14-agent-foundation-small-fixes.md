# Agent 底层小修记录（2026-10-14）

用户授权：审计发现的缺口若改动不大，直接修复。本批完成三个 Agent 缺口，并修复讲题重试、选择提交两处旧状态捕获。没有修改数据库 schema、不可变请求守卫、工具权限或派发预算。

## 实现与边界

| 项目 | 本批结果 | 保留的验收边界 |
|---|---|---|
| AGF-01 原生动作交接 | 工具环累积动作随最终大厅输出持久保存；挂卡读取该交接字段，保留参数并去重 | 仍由现有准入逻辑选出一张卡；工具中途断点恢复属于 AGF-02 |
| AGF-03 取消执行者 | 取得执行权后才登记 Job、清工具痕迹；学生停止在释放执行权前落 CANCELLED，等待者不重派发 | 本批验证同实例、同 requestId 的执行者和等待者；常规协程取消后的接管仍通过原测试 |
| AGF-04 / KD-30 | 原生路由读取 extendedResult，与 JSON 路由一致 | 不扩大工具授权或结果预算 |
| KD-35 重试旧 ID | 无状态命令随 Compose 重组绑定当前任务；重试使用观察到的尝试数，产生新 requestId | 真实服务商失败后、真实数据库中的原崩溃切片尚未重新走查；正式验收门保持 open |
| KD-36 提交旧空计划 | 同一修复使选择提交读取已完成计划，真正调用选择写入通道 | Compose 回归使用受控交互仓库；attempt_submission / review_log / review_session 的实际结算仍待复验 |

累积交接使用 TutorLobbyOutput.requestedLocalActions：默认空值兼容旧输出记录，空值不编码；它不是模型 wire 字段，模型每轮至多一个 localActions 的限制保留。Room 在成功终态前填入工具环累计值，现有确认卡权限和目标解析继续生效。

讲题页的 Plan、Respond、Interaction 命令类本身不持有状态。缓存这些命令对象却让输入闭包冻结在首次组合，造成“已观察任务但仍用 attempt=0”和“成功计划仍读成 null”。本批移除这三个构造处的 remember，保持原来的协程、仓库、回调及自动开轮判据。

主要实现：

- [TutorLobbyTasks.kt](<D:/smart mistake book/core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorLobbyTasks.kt>)：兼容旧记录的动作交接字段与边界校验。
- [RoomModelTaskRepository.kt](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt>)：终态动作交接、执行所有者登记和停止顺序。
- [OpenAiModelProtocol.kt](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelProtocol.kt>)：原生 extendedResult。
- [TutorLobbySendCommands.kt](<D:/smart mistake book/feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorLobbySendCommands.kt>)：累积动作进入确认卡准入。
- [TutorSessionPanel.kt](<D:/smart mistake book/feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/components/TutorSessionPanel.kt>)：命令绑定当前组合的输入。

## 回归证据

新增七项行为回归，均有修复前的预期失败证据：

1. 原生 MASTERY_READ 保留扩展结果申请：原实现解析成 false。
2. 原生 START_EXPORT 后最终回复不重复动作：原实现持久输出没有交接动作。
3. 工具轮重复动作去重交接：原实现交接列表为空。
4. 有等待者时公开 stop 取消真正执行者、不重新派发：原实现执行者没有被取消。
5. 累积动作实际进入 pending 卡和 AWAITING_CONSENT：去掉挂卡交接后为 0 张卡；恢复修复后通过。
6. 失败卡重试读取已观察的尝试：原实现两次 requestId 相同。
7. RUNNING 之后才完成的计划可提交选择：原实现选择写入调用为 0。

本轮最终 JVM 命令：

```powershell
.\gradlew.bat :core:model:test :core:domain:test :core:database:testDebugUnitTest :core:data:testDebugUnitTest :feature:tutor:testDebugUnitTest --rerun-tasks --console=plain
```

**BUILD SUCCESSFUL in 1m 57s；66 个任务全部执行；2,125 项，0 失败 / 错误 / 跳过。** 五模块分别为 418 / 566 / 182 / 749 / 210；这是相关模块总数，包含共享工作区其他已存在的测试。

Room / 工具循环：

```powershell
.\gradlew.bat :core:data:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.model.RoomModelTaskToolLoopInstrumentedTest,com.tingyun.smartmistakebook.core.data.model.ModelTaskRepositoryInstrumentedTest' --console=plain
```

**BUILD SUCCESSFUL in 44s；26 项通过，0 失败 / 跳过。** 使用真实 Android Room 和受控网关，核对终态持久输出、取消、预算与工具循环。

导师页面：

```powershell
.\gradlew.bat :feature:tutor:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.feature.tutor.CapturedTutorSessionInstrumentedTest,com.tingyun.smartmistakebook.feature.tutor.TutorConversationScreenInstrumentedTest,com.tingyun.smartmistakebook.feature.tutor.TutorCommandRecompositionInstrumentedTest' --console=plain
```

**BUILD SUCCESSFUL in 2m 4s；39 项通过，0 失败 / 跳过。** 在模拟器中执行真实 Compose 交互；模型和交互仓库为测试替身，没有访问真实服务商。

证据：[JVM 日志](<D:/smart mistake book/.artifacts/agent-foundation-fixes-2026-10-14/unit-verification.log>)、[JVM XML 汇总](<D:/smart mistake book/.artifacts/agent-foundation-fixes-2026-10-14/unit-results.json>)、[Room 日志](<D:/smart mistake book/.artifacts/agent-foundation-fixes-2026-10-14/runtime-green.log>)、[Compose 日志](<D:/smart mistake book/.artifacts/agent-foundation-fixes-2026-10-14/tutor-ui-verification.log>)。同目录保留红灯日志及通过后的设备 XML 副本。

## 剩余工作

- **AGF-02** 仍需独立设计工具执行检查点、调用回执和写工具幂等恢复，涉及持久化/迁移及中断语义；归入 AUD-14。本批最终动作交接不能替代中途检查点。
- **ADD_TO_REVIEW_PLAN** 仍是未接线的业务动作，沿已有阶段计划处理。
- **AUD-21/22** 已有上述根因修复和回归，保留真实服务商、真实复习账本切片验收门，不依据替身测试关闭。
- 本批没有执行全仓库 Lint、Release 或真实服务商走查。
