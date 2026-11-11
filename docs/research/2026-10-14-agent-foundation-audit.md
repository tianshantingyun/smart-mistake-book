# 导师 Agent 底层完成度审计（2026-10-14）

> **2026-10-18 绘图路线更新：** 所有生图入口统一判定，由模型按共享规则选择JS或图像模型；两者均为生图方式，正式统一接线待实施。当前约束见[教学图形契约](../current/visual-generation-contract.md)。本文既有日期、提交、测试、缺陷与旧方案作为历史事实保留，不代表新路线已实现。

> 以下正文保留修复前的审计基线与当时源码位置。用户随后授权小修：AGF-01/03/04 已修并通过回归；KD-35/36 的旧状态捕获已修，真实切片退出门仍待复验。当前实现与新验证见 [小修记录](<D:/smart mistake book/docs/research/2026-10-14-agent-foundation-small-fixes.md>)。AGF-02 和复习计划调整接线仍未完成。

## 判断

**基础已经搭实，执行闭环还没有完全收口。** 当前实现能在约束内调用工具、回喂结果、控制远程派发预算、限制本地写入并等待学生确认；但工具执行进度的持久恢复、原生动作交接、取消执行者的一致性和协议参数对齐仍有具体缺口。

完成度应按下面几层分别判断，不能把“任务状态落库”直接等同于“Agent 可断点续跑”，也不能把“确认卡有数据库”直接等同于“所有模型动作都能到达确认卡”。

| 层 | 已有实现 | 当前判断 |
|---|---|---|
| 意图、输入与输出契约 | typed input/output、意图授权矩阵、工具和动作白名单、参数校验 | 已有可用基础 |
| 工具循环 | 8 个领域工具、结果回喂、最多 5 轮工具、每轮最多 3 个工具调用、结果预算 | 正常路径已实现；恢复见 AGF-02 |
| 远程派发与出站授权 | 每轮授权、网关预检、资产读取、持久派发计数；同逻辑操作最多 6 次模型派发 | 约束已有实际实现；生图另有通道，不能将这个数外推为所有网络调用的总上限 |
| 本地数据写门 | 知识点代号/节点边界、证据门、配额与冷却；错题保存走 ask 档 | 有实际约束，不能由模型直接自由写数据库 |
| 确认与动作落地 | pending request 持久化、恢复与裁决；打开题目、收录/图片录入、导出有执行出口 | 已入队的卡片有闭环；原生动作入队前有断点，见 AGF-01；复习计划调整仍有未接线出口 |
| 状态、取消与恢复 | Room 状态机、版本比较交换、操作预算、协程/网络取消、等待执行者接管 | 状态恢复已有；执行进度与取消所有者仍不完整，见 AGF-02/03 |
| 协议适配 | 多种 wire protocol、原生 function 与 JSON 路由、截断失败分流 | 有实现与测试；仍有参数静默丢失，见 AGF-04 |
| 可观测与验证 | 实时工具痕迹、成功消息保存痕迹、JVM 与 Room 仪器化测试 | 覆盖面较广；失败/中断后的工具回执和跨层场景仍需补齐 |

## 四项源码确认的缺口

### AGF-01 · P1：原生本地动作可能到不了确认卡

**触发条件：** 大厅请求初始没有待确认动作；模型通过原生 function 调用提出 `START_EXPORT`、`SAVE_TO_NOTEBOOK` 等白名单动作；下一轮正常作答，但不再重复输出 `localActions`。

调用链现在是：

1. 原生解析器把动作放进 `TutorToolRequestsOutput.localActions`。
2. 仓库把动作并入临时的 `roundRequest.input.requestedLocalActions`。
3. 下一轮提示词告诉模型“已经提出、正在等学生确认……不要重复提出”。
4. 最终输出解析器只读取最后一轮 JSON 中的 `localActions`，没有接回前面累积的动作。
5. 挂卡代码读取外层原始 `request.input`，再拼上最终 `output.localActions`。

因此，模型遵守“不重复提出”时，原生动作可能既不在原始请求里，也不在最终输出里，确认卡就不会出现。只有动作的原生调用还可能没有工具执行痕迹；不能依靠 `NOTEBOOK_WRITE` 的 ask 痕迹兜住所有动作。

源码证据：

- [OpenAiModelProtocol.kt:728](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelProtocol.kt:728>)：解析原生动作。
- [RoomModelTaskRepository.kt:513](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt:513>)：动作仅并进临时请求。
- [OpenAiModelTaskAdapters.kt:520](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTaskAdapters.kt:520>)：提示词要求不重复提出。
- [OpenAiModelResponseParsers.kt:515](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelResponseParsers.kt:515>)：最终动作取自最后输出。
- [TutorLobbySendCommands.kt:561](<D:/smart mistake book/feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorLobbySendCommands.kt:561>)：挂卡读取原始请求。

**收口要求：** 由本地执行器持有累积动作的唯一事实，在成功或可恢复的交接点交给 pending request；保留参数、去重、权限和上下文校验。不要依赖模型在最后一轮重新复述一次动作。

**验收钉子：** 原生动作单独调用、动作与读工具混合调用、最后输出动作数组为空、重复提议、动作后中断恢复；均核对实际 pending 行与确认后的执行出口。

### AGF-02 · P1：有持久任务状态，但没有完整的工具执行检查点

每次进入工具循环，`roundRequest`、`toolRoundsUsed` 和 `toolRoundResults` 都从初始请求重新建立。工具结果、动态披露的知识点代号和新提出的动作随后只进入内存中的下一轮请求。状态更新 SQL 不保存这些执行进度；重试还会清空内存工具痕迹。

**后果：** 工具执行后进程被杀，或后续模型派发失败再重试时，不能精确恢复此前那一轮的工具结果、代号映射和待确认动作。已被写门接受的掌握证据/咨询记录可以保留，但模型上下文和可见回执可能丢失；恢复还可能重新调用工具，继续消耗剩余派发预算。

这不代表所有事实都会重复落库。现有稳定 ID、配额和写门能够限制部分重复；缺的是每次工具调用的持久回执与恢复语义。

源码证据：

- [RoomModelTaskRepository.kt:279](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt:279>)：循环进度从初始请求重建。
- [RoomModelTaskRepository.kt:389](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt:389>)：收到工具申请后先回到 QUEUED，没有同时记录待执行调用。
- [RoomModelTaskRepository.kt:486](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt:486>)：结果、痕迹与下一轮输入累积在内存。
- [ModelTaskTransactionDao.kt:110](<D:/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ModelTaskTransactionDao.kt:110>)：状态更新没有工具进度字段。
- [TutorLobbySendCommands.kt:540](<D:/smart mistake book/feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorLobbySendCommands.kt:540>)：成功收尾时才把工具痕迹存入助手消息。

**收口要求：** 将不可变原始请求与可变执行检查点分开；保存轮次、调用身份、执行状态、工具回执、代号映射和累积动作。明确各写工具的幂等范围，恢复时重建已执行结果，而不是把已接受的学习事实一概回滚。继续沿用现有操作预算，不因恢复重置。

**验收钉子：** 至少在“收到调用未执行”“写工具已完成但回执未交接”“第二次模型派发前”“最终回复前”四个位置中断并重建仓库；核对事实、工具调用次数、代号、动作、痕迹与剩余预算。

### AGF-03 · P2：重复收集同一请求时，停止可能取消等待者

限定场景是同一个 repository 中，同一个 requestId 存在两个执行流收集者：A 已获得操作执行权，B 等待 A 完成。

`execute()` 在抢占执行权之前就把当前 Job 写入 `activeExecutions[requestId]`，B 因此会覆盖 A 的取消登记。随后 `cancel(requestId)` 查到 B，只取消并等待 B，然后把任务写成 CANCELLED。真正执行网络/工具工作的 A 不一定收到这次取消，后续状态写入可能由比较交换挡住，但正在进行的工作并没有因此得到直接取消。

源码证据：[RoomModelTaskRepository.kt:153](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt:153>)、[取消入口:176](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt:176>)、[执行权等待:230](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt:230>)。

**收口要求：** 取消登记与真正的执行所有者使用同一个身份，等待者不替换所有者 Job；所有者交接时再原子更新。

**验收钉子：** A 正在派发，B 开始等待，调用公开 cancel API；验证 A 的网络/工具确实退出、B 正确结束、CANCELLED 后无新副作用。现有“直接取消 A 后等待者接管”的测试不能替代这个场景。本轮未在设备上复现此竞态。

### AGF-04 · P2：原生路由丢失 `MASTERY_READ.extendedResult`

原生 function schema 声明了 `extendedResult`，JSON 路由也解析它，但原生调用构造 `TutorToolCall` 时没有赋值，最终采用默认 `false`。同一模型请求在两种路由下得到不同结果预算，扩展阅读会在原生路由静默失效。

源码证据：[OpenAiModelProtocol.kt:696](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelProtocol.kt:696>)、[JSON 解析:482](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelResponseParsers.kt:482>)、[字段默认值:163](<D:/smart mistake book/core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolLoop.kt:163>)。

这是既有 **KD-30 / AUD-14**，本次复核仍然存在。近期截断处理的修复不能代表这一项也已完成。

**收口要求与验收：** 两种路由形成相同的工具契约；逐项对拍参数、默认值、未知键、意图授权和预算。针对 `extendedResult=true` 核对解析值及实际回喂容量。

## 产品接线与既有实际走查

`ADD_TO_REVIEW_PLAN` 已在动作白名单中，但执行仍会落到 [NOT_WIRED_YET](<D:/smart mistake book/feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorPendingRequestCommands.kt:215>)。它能表达提议并如实反馈，尚未形成“同意后实际调整复习计划”的完整能力，计划状态应保留这个区别。

仓库最近的 [薄纵向切片走查:469](<D:/smart mistake book/docs/research/2026-10-11-thin-vertical-slice-walkthrough.md:469>) 还记录了：讲题模型输出失败后，失败卡重试触发 `ImmutablePayloadConflictException` 崩溃；换 composer 重试后能出题，但提交选项后锁死且没有产生判定持久记录。

本轮回读仍确认两个相关边界：

- [TutorPlanCommands.kt:55](<D:/smart mistake book/feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorPlanCommands.kt:55>) 重新构造带当前时间的请求，launch/collect 没有在此捕获创建请求的异常。
- [ModelTaskTransactionDao.kt:363](<D:/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ModelTaskTransactionDao.kt:363>) 对 Plan 的不可变请求同时比较完整 requestSnapshot 和创建时间；发生同 ID 不同载荷时仍由创建路径抛异常。

**这里引用的是仓库已有实际走查，本轮没有再次调用真实服务商复现崩溃或提交锁死。** 应优先核对重试入口是否重用持久请求、是否正确建立新的尝试身份，以及创建失败能否进入可见失败通道。不可通过删除不可变性守卫来掩盖调用端契约不一致。选项提交还需核对任务、消息、判定事实与 UI 状态的交接。

## 验证证据

本次为本地源码审查与现有测试执行，没有新增实现代码。

### JVM

执行命令：

```powershell
.\gradlew.bat :core:model:test :core:domain:test :core:database:testDebugUnitTest :core:data:testDebugUnitTest :feature:tutor:testDebugUnitTest --rerun-tasks --console=plain
```

2026-10-14 本次结果：**BUILD SUCCESSFUL in 3m 19s；66 个任务全部执行；2,120 项测试，0 失败、0 错误、0 跳过。** 数量来自本次各模块的 JUnit XML，不把其他模块或历史运行计入。

| 模块 | 测试数 | 失败 / 错误 / 跳过 |
|---|---:|---|
| core:model | 416 | 0 / 0 / 0 |
| core:domain | 566 | 0 / 0 / 0 |
| core:database | 182 | 0 / 0 / 0 |
| core:data | 747 | 0 / 0 / 0 |
| feature:tutor | 209 | 0 / 0 / 0 |

这是五个相关模块的测试总数，并非 2,120 项都针对 Agent。对照日志：[JVM 构建输出](<D:/smart mistake book/.artifacts/agent-foundation-audit-2026-10-14/agent-unit-verification.log>)、[XML 汇总](<D:/smart mistake book/.artifacts/agent-foundation-audit-2026-10-14/test-results.json>)。

### Android Room / 工具循环

执行命令：

```powershell
.\gradlew.bat :core:data:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.model.RoomModelTaskToolLoopInstrumentedTest,com.tingyun.smartmistakebook.core.data.model.ModelTaskRepositoryInstrumentedTest' --console=plain
```

2026-10-14 重跑结果：**BUILD SUCCESSFUL in 52s；23/23，0 失败、0 跳过**。其中工具循环类 9 项，任务仓库类 14 项。使用 Android 模拟器上的真实 Room 与本地 scripted/fake gateway，验证正常循环、结果预算、状态持久化、远程计数、并发所有权等现有场景；不等于真实服务商响应质量通过。

第一次尝试曾被 `BatchImportRepositoryInstrumentedTest` 的两个未解析引用阻塞，日志留在 2026-10-13 的证据目录。共享工作区随后修正该测试，本次重跑已越过编译并实际执行 23 项；最终设备结论以上述重跑为准。

对照日志：[Android 构建与执行输出](<D:/smart mistake book/.artifacts/agent-foundation-audit-2026-10-14/agent-device-verification.log>)、[设备 JUnit XML 副本](<D:/smart mistake book/.artifacts/agent-foundation-audit-2026-10-14/device-test-results.xml>)。本轮没有执行真实服务商探测、全仓库 Lint/Release 或新增缺口的故障注入测试。上述四项缺口依据现有源码路径确认；测试通过不能证明未覆盖的交接与恢复场景通过。

## 建议执行顺序与完成门

1. **先修用户流程断点：** 原生动作实际挂卡；沿既有走查定位讲题重试崩溃、提交锁死，并用真实持久状态核验。
2. **再补执行恢复：** 独立工具检查点与调用回执；对读、学习事实写入、咨询写入、待确认动作分别规定恢复和幂等语义。
3. **统一取消所有者与协议契约：** 覆盖等待者替换、接管、停止和 Route A/B 参数对拍；KD-30 归回 AUD-14。
4. **对齐能力与计划状态：** 复习调整尚未接线的动作保留明确边界；给上述跨层场景建立完成门，成功、失败、停止、重启、重试都检查实际持久结果。

**达到“底层完善”的关键证据，是同一个操作在成功、失败和中断后，都能解释并恢复它已经做过什么、还等待什么，以及下一步是否可以安全继续。** 当前实现的工具约束和数据写门值得保留，接下来最有价值的是把这些交接与恢复接完整。
