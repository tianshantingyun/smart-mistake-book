package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy

/**
 * 生产生图入口（讲题工具/历史配图重生成/保存净化）共用的预算声明（KD-60）。
 *
 * 消灭的具体失败：三个入口各自写 `remainingRemoteDispatches = MAX_DISPATCHES`，
 * 把“同一逻辑操作最多 6 次派发”的上限当成“当前还剩 6 次”的余量；长工具环先烧掉几轮后，
 * 配图会被账本判 `BUDGET_EXHAUSTED`，而提示词/诊断里看不出真实原因。
 *
 * 这里只声明两件真实的事：
 * - 上限 = 既有 durable 策略（图像与工具轮共享，不另加额度）；
 * - 余量 = **未预读**（null）：由 `RoomFigureDispatchLedger` 在预留时判定，拒绝时回
 *   真实已派发次数。声明面不得用常量冒充余额。
 */
internal fun sharedOperationFigureBudget(
    reserveChatFinalAnswer: Boolean,
): FigureGenerationBudget = FigureGenerationBudget(
    operationDispatchLimit = ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES,
    remainingRemoteDispatches = null,
    reserveChatFinalAnswer = reserveChatFinalAnswer,
    sharesOperationBudgetWithToolRounds = true,
)
