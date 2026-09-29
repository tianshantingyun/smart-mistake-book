package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.TutorConversation
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationStatus
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkMuted
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.Paper
import com.tingyun.smartmistakebook.core.ui.PaperDivider

@Composable
fun TutorHistoryRoute(
    conversations: TutorConversationRepository,
    onOpenTextConversation: (String) -> Unit,
    onOpenCapturedSession: (String) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只列智能体栏（本会话区）的会话：复习栏两个入口各有一个区，历史互不相串（D-Q1F/M10）。
    val recent by conversations.observeRecent(100, TutorConversationAreas.AGENT)
        .collectAsState(initial = emptyList())
    val visible = tutorHistoryConversations(recent)
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .testTag("tutor_history_screen"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("tutor_history_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回讲题",
                    tint = Ink,
                )
            }
            Text(
                text = "讲题历史",
                modifier = Modifier.weight(1f),
                color = Ink,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        PaperDivider()
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "还没有讲题记录",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "从拍题或选择一道错题开始",
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = visible,
                    key = TutorConversation::conversationId,
                ) { conversation ->
                    TutorHistoryRow(
                        conversation = conversation,
                        onClick = {
                            when (conversation.anchorKind) {
                                TutorConversationAnchorKind.TEXT_ONLY ->
                                    onOpenTextConversation(conversation.conversationId)
                                TutorConversationAnchorKind.EPHEMERAL_DRAFT,
                                TutorConversationAnchorKind.PROBLEM_REVISION,
                                -> onOpenCapturedSession(
                                    conversation.anchorId ?: conversation.conversationId,
                                )
                            }
                        },
                        onArchive = {
                            if (conversation.status != TutorConversationStatus.ARCHIVED) {
                                onArchive(conversation.conversationId)
                            }
                        },
                        onDelete = { deleteTarget = conversation.conversationId },
                    )
                }
            }
        }
    }
    deleteTarget?.let { conversationId ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条讲题记录？") },
            text = { Text("只会删除这条对话，题目、修订和错题本内容会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDelete(conversationId)
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun TutorHistoryRow(
    conversation: TutorConversation,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
            .testTag("tutor_history_row_${conversation.conversationId}"),
        color = Paper,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
            ) {
                Text(
                    text = tutorConversationTitle(conversation),
                    color = Ink,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
                Text(
                    text = historySubtitle(conversation),
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (conversation.status != TutorConversationStatus.ARCHIVED) {
                IconButton(
                    onClick = onArchive,
                    modifier = Modifier.testTag("tutor_history_archive_${conversation.conversationId}"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = "归档这个讲题记录",
                        tint = InkSecondary,
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("tutor_history_delete_${conversation.conversationId}"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除这条讲题记录",
                    tint = InkSecondary,
                )
            }
        }
    }
}

/**
 * 历史列表的内容（K1b）：**只列有消息的会话**。
 *
 * 写侧已经不再为"进入页面"建行（`TutorSessionViewModel` 不建、会话行由第一条消息按需创建），
 * 但旧安装的存量行、以及任何写侧漏网的行仍可能没有一条消息；列表是这条规则对学生的唯一出口，
 * 所以在这里再挡一次。判定用真实消息行数（`messageCount`）而不是 `last_turn_ordinal`——
 * 后者是序号语义，讲题会话的序号有空洞会多报。
 */
internal fun tutorHistoryConversations(
    conversations: List<TutorConversation>,
): List<TutorConversation> = conversations.filter { conversation ->
    conversation.messageCount > 0
}

/**
 * 会话标题（K1c/B7）：**首条消息截取**（同一份原文按 [TUTOR_CONVERSATION_TITLE_MAX_CHARS] 截断）。
 *
 * 写侧不再把题名塞进 `title`（B7），所以标题的第一来源是首条消息、**不是** `title` 列；
 * `title` 只作为旧行的兜底（升级前写下的行带着题名），两者都没有才回落到入口标签。
 * 时间由列表行给出（[historySubtitle]）——这就是"首条消息截取 + 时间"里的那一半。
 */
internal fun tutorConversationTitle(conversation: TutorConversation): String =
    tutorConversationTitleOf(conversation.firstMessageBodyMarkdown)
        ?: conversation.title?.takeIf(String::isNotBlank)
        ?: conversationAnchorLabel(conversation)

private fun conversationAnchorLabel(conversation: TutorConversation): String = when (
    conversation.anchorKind
) {
    TutorConversationAnchorKind.TEXT_ONLY -> "文字讲题"
    TutorConversationAnchorKind.EPHEMERAL_DRAFT -> "拍题讲题"
    TutorConversationAnchorKind.PROBLEM_REVISION -> "错题讲题"
}

private fun historySubtitle(conversation: TutorConversation): String {
    val status = when (conversation.status) {
        TutorConversationStatus.ACTIVE -> "进行中"
        TutorConversationStatus.PAUSED -> "等你继续"
        TutorConversationStatus.COMPLETED -> "已结束"
        TutorConversationStatus.ARCHIVED -> "已归档"
    }
    // 用真实消息行数，而不是 last_turn_ordinal 序号（讲题会话的序号有空洞会多报）。
    // 时间与标题配对出现（K1c 的"首条消息截取 + 时间"）：两条会话的开头一样时，只有
    // 时间能把它们分开。
    return "$status · 共 ${conversation.messageCount} 条消息 · ${historyTimestamp(conversation)}"
}

/** 会话最近一次更新的本地时间（列表时区 = 设备时区，读时算，不落库）。 */
private fun historyTimestamp(conversation: TutorConversation): String =
    java.time.Instant.ofEpochMilli(conversation.updatedAtEpochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(
            java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm")
                .withLocale(java.util.Locale.getDefault()),
        )
