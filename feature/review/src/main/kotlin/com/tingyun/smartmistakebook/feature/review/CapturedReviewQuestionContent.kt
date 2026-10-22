package com.tingyun.smartmistakebook.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.ui.BoundedLocalImage
import com.tingyun.smartmistakebook.core.ui.LocalImageLoadState
import com.tingyun.smartmistakebook.core.ui.SafeMarkdownText
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.StructuredContentRenderer

@Composable
internal fun CapturedReviewQuestionContent(entry: StudyCatalogEntry, state: MistakeDetailState?) {
    val ready = (state as? MistakeDetailState.Ready)?.takeIf {
        it.detail.identity.errorBookEntryId == entry.entryId &&
            it.detail.identity.problemId == entry.problemId &&
            it.detail.identity.problemRevisionId == entry.problemRevisionId
    }
    if (ready != null) {
        StructuredContentRenderer(document = ready.questionDocument.document.copy(title = null), choicesEnabled = false)
    } else {
        SafeMarkdownText(entry.problemMarkdown, MaterialTheme.typography.bodyLarge, color = SmartColors.Ink)
    }
    val source = ready?.detail?.source as? MistakeSourceSet.Present
    // "查看原图"只收题目区资产：干净题面（CLEAN_IMAGE）不是原图，讲解图（TUTOR_EXPLANATION）
    // 更不是——T2a 起 revision 会持久化讲解链接，这个读口必须同步按角色过滤（WG-06）。
    val assets = source?.assets.orEmpty().filter {
        it.role != VisualRole.CLEAN_IMAGE.name && it.role != VisualRole.TUTOR_EXPLANATION.name
    }
    var showOriginal by rememberSaveable(entry.entryId, entry.problemRevisionId) { mutableStateOf(false) }
    if (assets.isNotEmpty()) {
        TextButton(onClick = { showOriginal = true }, modifier = Modifier.testTag("captured_review_source_open")) {
            Text("查看原图（${assets.size} 页）")
        }
    } else if (ready != null) {
        Text("这道题的原图暂不可用。", color = SmartColors.InkSecondary)
    }
    if (showOriginal) {
        Dialog(onDismissRequest = { showOriginal = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = SmartColors.Paper) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    TextButton(onClick = { showOriginal = false }) { Text("关闭原图") }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        assets.forEachIndexed { index, asset ->
                            val uri = (asset.location as? MistakeSourceLocation.Available)?.localUri
                            var failed by remember(uri) { mutableStateOf(uri == null) }
                            Text("原图 ${index + 1} / ${assets.size}")
                            if (uri != null) BoundedLocalImage(uri, "复习原图 ${index + 1}", expanded = true,
                                modifier = Modifier.fillMaxWidth(), onLoadStateChange = {
                                    failed = it == LocalImageLoadState.UNAVAILABLE
                                })
                            if (failed) Text("这一页原图无法读取，其他内容仍可查看。", color = SmartColors.ErrorText)
                        }
                    }
                }
            }
        }
    }
}
