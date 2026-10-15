package com.tingyun.smartmistakebook.core.model

/**
 * 模型文本超出**格式预算**（KD-38）。
 *
 * 与"形状本身不认识"（其余 [IllegalArgumentException] 抛点，如未知键、枚举不识别、危险控制字符）
 * 分开，是因为两者的**重发判决不同**：
 * - 这里是"这一次输出的文本超过了本轮声明的长度预算"——抽样问题，重发可能换一份更短的输出，
 *   所以网关把它归到**可重试**的 `INCOMPLETE_OUTPUT`；
 * - 那里是"这条输出没有按契约说话"——同一条请求重发多半逐字复现，归不可重试的 `INVALID_RESPONSE`。
 *
 * 抛点只有 [String.requireSafeModelText] 的超预算分支：解析期能按预算收敛的**短标签**字段
 * （动作标签 / 知识点标签 / 锚点词）已在解析器里截断，走到这里的是正文级文本（`messageMarkdown`、
 * `solutionMarkdown`、`openingMarkdown`…）——它们不能靠截断收敛，只能让这一轮重来。
 *
 * 该函数也用于**本地构造**的文本（id、清单字段…），那些抛点发生在请求构造期、不会经过网关的
 * 响应解析出口；网关侧只在响应解析路径上把本类型映射成可重试失败。
 */
class ModelTextBudgetExceededException(message: String) : IllegalArgumentException(message)
