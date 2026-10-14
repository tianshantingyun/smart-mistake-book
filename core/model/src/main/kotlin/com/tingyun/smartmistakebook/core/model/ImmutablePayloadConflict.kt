package com.tingyun.smartmistakebook.core.model

/**
 * 写入路径的「同标识、不同载荷」冲突：**这个标识已经存在一行，而这一次带来的内容不一样**。
 *
 * 为什么要有这个标记（而不是让调用方去认那个异常类）：冲突的异常类型住在 `core:database`
 * （`ImmutablePayloadConflictException`），而界面层（`feature:*`）按依赖方向看不到那个模块
 * （只有 `app` 与 `core:data` 依赖它）。两条路都不能走：
 * - 按**类名字符串**认——release/internal/benchmark 变体开着 R8，类名会被改写，这个判断会在
 *   发布包里静默失效（本地测试照样绿），兜底就等于没有；
 * - 干脆吞掉整族 `IllegalStateException`——`kotlinx.coroutines.CancellationException` 正是它的
 *   子类，会把协程取消一起吞掉；`LearningLedgerIntegrityException` 这类正直性缺陷也会被伪装成
 *   "可重试的失败"，把真正的数据问题藏起来。
 *
 * 所以把"它是什么"变成一个跨层可见的**类型**：`core:database` 的冲突异常实现它，
 * 调用方按 `is ImmutablePayloadConflict` 精确判定。这既不受 R8 影响，也不会放宽到别的族成员。
 * 新增门（第 12 条）：它消灭的具体失败 = KD-35——讲题失败卡的「重试」在"同标识、不同载荷"
 * 上报出的冲突无人接住，异常直达主线程把进程打崩（`docs/known-defects.md`）。
 *
 * 语义边界：认出它**不等于**可以忽略它。调用方要么按"这一次没写成、请重试"如实报错，
 * 要么把它换成一个新的标识重来；**不许静默吞掉**——那与"学生的操作悄悄消失"是同一种失败。
 */
interface ImmutablePayloadConflict
