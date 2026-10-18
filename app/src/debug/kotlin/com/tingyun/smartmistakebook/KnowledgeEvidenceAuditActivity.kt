package com.tingyun.smartmistakebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.data.study.KnowledgeQuizEvidenceAudit
import com.tingyun.smartmistakebook.core.data.study.KnowledgeQuizEvidenceAuditRow
import com.tingyun.smartmistakebook.core.data.study.RoomBackedStudyExperienceRepository
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme

/**
 * KD-48 知识测验证据审计屏（**debug 构建专用**，只读）。
 *
 * 消灭的失败：`KnowledgeQuizEvidenceAudit` 此前只有测试调用——AUD-11 的"证据行 ↔ 题面快照"
 * 对账结果在产品里没有任何消费面，错键落库后学生/开发都看不见"哪笔证据配的是哪道题"。
 * 本屏消费 [KnowledgeQuizEvidenceAudit.scanRows]，逐条列出对上（题面行 id）、坏行（读不回）
 * 与未对上（各自留 null，不猜）。
 *
 * **只读**：不写库、不补写、不纠正错键——纠正/撤销/重放语义按裁定留 5B（KD-43）。
 *
 * 入口（adb 驱动，与 `BindingAuditActivity` 同一条调试边界）：
 * ```
 * adb shell am start -n com.tingyun.smartmistakebook.localfirst/com.tingyun.smartmistakebook.KnowledgeEvidenceAuditActivity
 * ```
 * 导出是 adb 驱动所必需（非导出组件会被 `SecurityException: not exported from uid` 拒绝）；
 * 边界靠本清单所属的 debug 源集，而不是 `exported` 标志——release 变体不合并本清单。
 */
class KnowledgeEvidenceAuditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartMistakeBookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KnowledgeEvidenceAuditScreen()
                }
            }
        }
    }
}

@Composable
private fun KnowledgeEvidenceAuditScreen() {
    val context = LocalContext.current
    val database = remember(context) {
        (context.applicationContext as SmartMistakeBookApplication).studyDatabase
    }
    var rows by remember { mutableStateOf<List<KnowledgeQuizEvidenceAuditRow>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        failure = null
        runCatching {
            KnowledgeQuizEvidenceAudit(
                database = database,
                learnerId = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID,
            ).scanRows()
        }.onSuccess { loaded ->
            rows = loaded
        }.onFailure { error ->
            failure = error.message ?: error::class.java.simpleName
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "知识测验证据审计（debug 专用）",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "只读：不会写入或纠正任何证据行；纠正/撤销语义留 5B（KD-43）。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (loading) {
            Text(
                text = "正在读取…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        failure?.let { message ->
            Text(
                text = "读取失败：$message",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        val linked = rows.count { it.itemSnapshotId != null && !it.itemUnreadable }
        val unreadable = rows.count { it.itemUnreadable }
        val unmatched = rows.size - linked - unreadable
        Text(
            text = "共 ${rows.size} 条 · 对上 $linked 条 · 坏行 $unreadable 条 · 未对上 $unmatched 条",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        OutlinedButton(
            onClick = { refreshKey += 1 },
            enabled = !loading,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("刷新")
        }

        if (!loading && failure == null && rows.isEmpty()) {
            Text(
                text = "没有知识点测验证据行（本口只看 KNOWLEDGE_QUIZ 通道；讲题通道没有题面快照）。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        rows.forEach { row ->
            EvidenceAuditRow(row)
        }
    }
}

@Composable
private fun EvidenceAuditRow(row: KnowledgeQuizEvidenceAuditRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        HorizontalDivider()
        Text(
            text = "证据 ${row.evidenceId}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "知识点 ${row.knowledgeNodeId} · 会话 ${row.sessionId ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "请求 ${row.requestId ?: "不是本通道形状"}",
            style = MaterialTheme.typography.bodySmall,
        )
        when {
            row.itemUnreadable -> Text(
                text = "题面 ${row.itemSnapshotId}：读不回（坏行/键版本不认识）",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            row.itemSnapshotId != null -> Text(
                text = "题面 ${row.itemSnapshotId}：对上",
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Text(
                text = "题面：未对上（会话标识不识别，或题面行已清理）",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
