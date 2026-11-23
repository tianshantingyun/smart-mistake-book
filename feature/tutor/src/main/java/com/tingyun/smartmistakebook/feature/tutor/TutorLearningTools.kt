package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.model.TUTOR_LEARNING_CONTEXT_SEPARATOR
import com.tingyun.smartmistakebook.core.model.tutorLearningProposalRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

enum class TutorLearningTool(
    internal val label: String,
    internal val icon: ImageVector,
    internal val tag: String,
) {
    PAPER_ASSEMBLY("组卷", Icons.Outlined.Description, "paper_assembly"),
    LEARNING_MASTERY("查看薄弱点", Icons.Outlined.Insights, "learning_mastery"),
    REVIEW_PLANNING("复习规划", Icons.Outlined.CalendarMonth, "review_planning"),
}

data class TutorLearningToolAction(
    val tool: TutorLearningTool,
    val detail: String,
    /** 暂不可用时留空，菜单继续显示原因。 */
    val onOpen: (() -> Unit)?,
)

/** 由应用根据本机学习数据和导航入口装配。 */
val LocalTutorLearningTools = compositionLocalOf<List<TutorLearningToolAction>> { emptyList() }

data class TutorLearningToolDraft(
    val marker: String,
    val request: String,
    val context: String,
) {
    val label: String get() = marker.removeSurrounding("【", "】")

    fun messageFor(text: String): String =
        "$marker\n" + text.replace(TUTOR_LEARNING_CONTEXT_SEPARATOR, "\n学习工具数据\n") +
            TUTOR_LEARNING_CONTEXT_SEPARATOR + context

    companion object {
        fun fromMessage(message: String?): TutorLearningToolDraft? {
            val request = message?.let(::tutorLearningProposalRequest) ?: return null
            return TutorLearningToolDraft(
                marker = message.substringBefore('\n'),
                request = request,
                context = message.substringAfter(TUTOR_LEARNING_CONTEXT_SEPARATOR),
            )
        }
    }
}
