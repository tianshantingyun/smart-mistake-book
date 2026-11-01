package com.tingyun.smartmistakebook.feature.capture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.StructuredContentRenderer

@Composable
internal fun CaptureDocumentPreviewCard(
    capturedDocument: CapturedQuestionDocument,
    modifier: Modifier = Modifier,
) {
    val layerSummary = capturedDocument.blockEvidence
        .groupingBy { it.writingLayer }
        .eachCount()
        .entries
        .sortedBy { it.key.ordinal }
        .joinToString(" · ") { (layer, count) -> "${layer.label()} $count" }

    CapturePanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("capture_document_preview"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(23.dp),
                tint = JadeActive,
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    text = "题面已准备好",
                    color = Ink,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${capturedDocument.document.blocks.size} 处题面内容" +
                        layerSummary.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        StructuredContentRenderer(
            document = capturedDocument.document,
            choicesEnabled = false,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "保存时会同时保留原图和这份题面。",
            modifier = Modifier.padding(top = 12.dp),
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun WritingLayer.label(): String = when (this) {
    WritingLayer.PRINTED -> "印刷"
    WritingLayer.HANDWRITTEN -> "手写"
    WritingLayer.MIXED -> "混合"
    WritingLayer.DIAGRAM -> "图形"
    WritingLayer.UNKNOWN -> "待确认"
}
