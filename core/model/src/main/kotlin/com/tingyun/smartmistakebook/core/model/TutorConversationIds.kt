package com.tingyun.smartmistakebook.core.model

/**
 * 讲题会话的对话 id 约定：写侧（feature:tutor 建会话时）与读侧（讲题判定结算读模型判词时）
 * 必须来自同一处，否则结算会去读一个不存在的对话、静默拿不到判词。
 */
object TutorConversationIds {
    /** 讲题会话（拍照会话 / 错题讲题）的对话 id 前缀；读侧据此把会话 id 还原出来。 */
    const val CAPTURED_PREFIX = "tutor-conv:captured:"

    fun captured(sessionId: String): String = "$CAPTURED_PREFIX$sessionId"
}
