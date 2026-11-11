package com.tingyun.smartmistakebook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.StudyProjectionIssue
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.SmartColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 投影专用状态卡的学生文案（批次 49 P2-13）。
 *
 * 为什么按 [StudyProjectionIssue.status] 翻译而不是直接显示异常的英文 message：学生要看到的是
 * "哪一类不一致"与"下一步做什么"，技术细节仍在 `failureMessage`/日志里。
 * 每个状态的句子必须**不同**——"冲突"（账本里两条互相矛盾的记录）与"缺口"（账本少一段）
 * 是两种不同的账本事实，合并成一句"数据出错了"等于没说。
 */
internal data class StudyProjectionIssueCopy(
    val title: String,
    val reason: String,
    val actionLabel: String,
    val consequence: String,
)

internal fun studyProjectionIssueCopy(issue: StudyProjectionIssue): StudyProjectionIssueCopy =
    when (issue.status) {
        ProjectionStatus.CONFLICTED -> StudyProjectionIssueCopy(
            title = "学习记录需要重新整理",
            reason = "学习记录里有两处互相矛盾的内容，投影暂时停在冲突处，学习数据无法继续更新。",
            actionLabel = "重新整理",
            consequence = "重新整理会用本机学习账本重新计算；不会删除任何作答记录。",
        )

        ProjectionStatus.WAITING_FOR_GAP -> StudyProjectionIssueCopy(
            title = "学习记录需要重新整理",
            reason = "学习记录缺少一段连续内容，投影停在缺口前，学习数据无法继续更新。",
            actionLabel = "重新整理",
            consequence = "重新整理会用本机学习账本重新计算，不会删除任何作答记录；缺口仍在时会如实保留这条提示。",
        )

        ProjectionStatus.CATCHING_UP -> StudyProjectionIssueCopy(
            title = "学习记录还在整理",
            reason = "投影还没追上最新记录，刚才的操作可能暂时看不到。",
            actionLabel = "重新整理",
            consequence = "可以先等一会；立即重新整理也不会丢记录。",
        )

        // CURRENT 不是一种"问题"（StudyProjectionIssue 构造即拒绝）。走到这里说明
        // 出现了一个未登记的新状态：如实说"有未识别的不一致"，不冒充已知原因。
        ProjectionStatus.CURRENT -> StudyProjectionIssueCopy(
            title = "学习记录需要重新整理",
            reason = "投影状态暂时无法识别。",
            actionLabel = "重新整理",
            consequence = "重新整理会用本机学习账本重新计算；不会删除任何作答记录。",
        )
    }

/**
 * 投影专用状态卡（替换通用错误行，批次 49 P2-13）。
 *
 * 三态可见（与既有写路径纪律同源）：空闲 → 整理中（按钮置忙）→ 成功（卡由快照清除消失）
 * 或失败（**必须说"没有改变"**，不得假成功）。唯一动作通过注入的 suspend lambda 走
 * `StudyExperienceRepository.reprojectAll()`，卡本身不持有仓库。
 */
@Composable
internal fun StudyProjectionIssueBanner(
    issue: StudyProjectionIssue,
    onReproject: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = studyProjectionIssueCopy(issue)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SmartColors.JadeSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("study_projection_issue"),
    ) {
        Text(
            text = copy.title,
            modifier = Modifier.testTag("study_projection_issue_title"),
            color = SmartColors.Ink,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = copy.reason,
            modifier = Modifier.testTag("study_projection_issue_reason"),
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        PrimaryActionButton(
            text = if (busy) "正在重新整理…" else copy.actionLabel,
            onClick = {
                if (busy) return@PrimaryActionButton
                busy = true
                failure = null
                scope.launch {
                    try {
                        onReproject()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failure = "重新整理没有完成，学习记录没有改变。可以换一会再试。"
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("study_projection_issue_reproject"),
            enabled = !busy,
            contentDescription = "用本机学习记录重新整理；不会删除任何作答",
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = copy.consequence,
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        failure?.let { message ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                modifier = Modifier.testTag("study_projection_issue_error"),
                color = SmartColors.ErrorText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
