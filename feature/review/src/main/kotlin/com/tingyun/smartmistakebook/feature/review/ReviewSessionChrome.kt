package com.tingyun.smartmistakebook.feature.review

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel

/** The three review modes share a reading position without changing their queue calculations. */
@Composable
internal fun ReviewSessionProgress(label: String, progress: Float) {
    WorkspacePanel(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = SmartColors.Jade,
            trackColor = SmartColors.Track,
        )
    }
}
