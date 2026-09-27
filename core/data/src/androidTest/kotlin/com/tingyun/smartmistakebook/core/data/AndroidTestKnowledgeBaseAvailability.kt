package com.tingyun.smartmistakebook.core.data

import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * androidTest 的就绪位默认值（D-Q3）。
 *
 * 这些用例钉的是模型任务仓储 / 批量录入 / 归类落库本身，不是知识内容的就绪门，
 * 所以按"已就绪"传入：KNOWLEDGE_READ 走它原本的检索路径，行为与门引入前一致。
 * 就绪门本身的行为由 core:data 的单元用例钉住（未就绪 → 如实说"准备中"）。
 */
internal fun readyKnowledgeBaseAvailability(): StateFlow<KnowledgeBaseAvailability> =
    MutableStateFlow(KnowledgeBaseAvailability.Ready)
