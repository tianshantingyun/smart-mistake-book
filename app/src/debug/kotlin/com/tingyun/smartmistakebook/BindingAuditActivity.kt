package com.tingyun.smartmistakebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.database.BINDING_AUDIT_STATUS_PENDING
import com.tingyun.smartmistakebook.core.database.BINDING_AUDIT_VERDICT_AMBIGUOUS
import com.tingyun.smartmistakebook.core.database.BINDING_AUDIT_VERDICT_CORRECT
import com.tingyun.smartmistakebook.core.database.BINDING_AUDIT_VERDICT_WRONG
import com.tingyun.smartmistakebook.core.database.BindingAuditAggregator
import com.tingyun.smartmistakebook.core.database.BindingAuditErrorRate
import com.tingyun.smartmistakebook.core.database.BindingAuditSampleRow
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshot
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshotCodec
import com.tingyun.smartmistakebook.core.ui.SafeMarkdownText
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * KF-29 绑定复核屏（**debug 构建专用**，不进学生面）。
 *
 * 入口（adb 驱动）：
 * ```
 * adb shell am start -n com.tingyun.smartmistakebook.localfirst/com.tingyun.smartmistakebook.BindingAuditActivity
 * ```
 *
 * **导出是 adb 驱动所必需，边界靠本清单所属的 debug 源集，而不是 `exported` 标志**
 * （与 `TestSeedModelConfigReceiver` 同一条理由）：非导出的组件 `adb shell am start` 会被
 * `SecurityException: not exported from uid` 拒绝，QA 根本进不来——这与"不进学生面"无关，
 * release 变体不合并本清单，学生面看不到这个 Activity。
 *
 * 它消灭的失败：抽样入队之后**没有地方能看**——审阅者拿不到"题面 + 绑定点 + 原文依据"，
 * 一键判定也就无从谈起。本屏把一条样本的全部复核现场摆在一屏里，判定写回同一行
 * （PENDING→REVIEWED，`BindingAuditPort.reviewBindingAuditSample`）。
 */
class BindingAuditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartMistakeBookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BindingAuditScreen()
                }
            }
        }
    }
}

@Composable
private fun BindingAuditScreen() {
    val context = LocalContext.current
    val port = remember {
        (context.applicationContext as SmartMistakeBookApplication).studyDatabase
    }
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<List<BindingAuditSampleRow>>(emptyList()) }
    var all by remember { mutableStateOf<List<BindingAuditSampleRow>>(emptyList()) }
    var reviewError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        pending = port.readBindingAuditSamples(BINDING_AUDIT_STATUS_PENDING)
        all = port.readBindingAuditSamples(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "绑定复核（debug 专用）",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "待复核 ${pending.size} 条 · 全部 ${all.size} 条",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )

        val aggregation = BindingAuditAggregator.aggregate(all)
        AggregationSummary(
            groups = aggregation.groups,
            unreadableReviewedCount = aggregation.unreadableReviewedCount,
        )

        reviewError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (pending.isEmpty()) {
            Text(
                text = "暂无待复核样本。抽样在组织写入（自动接受/用户确认/离线纠正）时按" +
                    "每科每周首 5 条新绑定入队。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        pending.forEach { row ->
            SampleCard(
                row = row,
                onVerdict = { verdict ->
                    scope.launch {
                        runCatching {
                            port.reviewBindingAuditSample(
                                sampleId = row.sampleId,
                                verdict = verdict,
                                reviewedAtEpochMillis = System.currentTimeMillis(),
                            )
                        }.onFailure { failure ->
                            reviewError = "落判失败：${failure.message ?: failure::class.simpleName}"
                        }.onSuccess {
                            reviewError = null
                            refreshKey += 1
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun AggregationSummary(
    groups: List<BindingAuditErrorRate>,
    unreadableReviewedCount: Int,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = "已复核错误率（口径：WRONG / (CORRECT + WRONG)，AMBIGUOUS 不计入）",
            style = MaterialTheme.typography.titleSmall,
        )
        if (groups.isEmpty()) {
            Text(
                text = "还没有已复核样本，错误率不适用（不是 0）。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        groups.forEach { group ->
            val rate = group.errorRate?.let { value ->
                String.format(Locale.ROOT, "%.2f", value)
            } ?: "—"
            Text(
                text = "${group.subject} / ${group.modelVersion}：错误率 $rate" +
                    "（复核 ${group.reviewedCount}，错 ${group.wrongCount}，" +
                    "对 ${group.correctCount}，难判 ${group.ambiguousCount}）",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (unreadableReviewedCount > 0) {
            Text(
                text = "已复核但快照/判定读不出来的行：$unreadableReviewedCount（未计入任何分组）",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SampleCard(
    row: BindingAuditSampleRow,
    onVerdict: (String) -> Unit,
) {
    val snapshot = BindingAuditSnapshotCodec.decode(row.bindingSnapshotJson)
    Column(modifier = Modifier.padding(top = 20.dp)) {
        HorizontalDivider()
        Text(
            text = "样本 ${row.sampleId}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (snapshot == null) {
            Text(
                text = "快照无法解码（复核前请检查写入链路）：${row.bindingSnapshotJson.take(200)}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            SnapshotBody(snapshot)
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { onVerdict(BINDING_AUDIT_VERDICT_CORRECT) }) { Text("正确") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onVerdict(BINDING_AUDIT_VERDICT_WRONG) }) { Text("错误") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onVerdict(BINDING_AUDIT_VERDICT_AMBIGUOUS) }) {
                Text("难以判断")
            }
        }
    }
}

@Composable
private fun SnapshotBody(snapshot: BindingAuditSnapshot) {
    Text(
        text = "科 ${snapshot.subject} · 模型 ${snapshot.modelVersion ?: "—"} · " +
            "来源 ${snapshot.acceptanceSource} · 确认于 ${formatTime(snapshot.acceptedAtEpochMillis)}",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        text = "题面" + if (snapshot.questionTruncated) "（超预算已截断）" else "",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    SafeMarkdownText(
        markdown = snapshot.questionMarkdown.ifBlank { "（题面为空）" },
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = "绑定点",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    snapshot.bindings.forEach { binding ->
        Text(
            text = "· ${binding.displayName}（${binding.granularity}，" +
                "强度 ${"%.2f".format(Locale.ROOT, binding.strength)}，${binding.knowledgeNodeId}）",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Text(
        text = "原文依据（分类理由 / 步骤对应）",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    snapshot.classifications.forEach { classification ->
        Text(
            text = "· [${classification.dimension}] ${classification.displayName}：" +
                (classification.rationaleMarkdown ?: "（无理由记录）"),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    snapshot.stepEvidence.forEach { step ->
        Text(
            text = "· 步骤 ${step.stepOrdinal}：${step.stepSummaryMarkdown}" +
                " → ${step.knowledgeNodeIds.joinToString("、")}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Spacer(Modifier.height(4.dp))
}

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(epochMillis))
