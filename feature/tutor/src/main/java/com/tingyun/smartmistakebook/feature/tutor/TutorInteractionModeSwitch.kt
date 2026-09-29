package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.model.TutorInteractionMode
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip

/**
 * 会话内的交互模式切换（D-Q9）：一行小字说清现在是哪种，右边一个可点的出口换过去。
 *
 * **界面不出现内部词**（模式名、脚手架、L0–L4 一律不上屏）：标签取自
 * [TutorInteractionMode.displayLabel]（"直接回答" / "带着我一步步来"），出口取自
 * [TutorInteractionMode.switchActionLabel]，说明取自 [TutorInteractionMode.displayDescription]
 * ——三句话都只有 `core:model` 那一处出处，改文案不会漂。
 *
 * 为什么长在标题栏下面而不是输入框附近：它是**这条会话的**属性（随会话行落库），
 * 不是这一句话的属性；学生要能随时看到现在处在哪种交互形态里。
 */
@Composable
internal fun TutorInteractionModeSwitch(
    mode: TutorInteractionMode,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (mode) {
                TutorInteractionMode.NORMAL -> Icons.Outlined.Forum
                TutorInteractionMode.GUIDED -> Icons.Outlined.School
            },
            contentDescription = mode.displayDescription,
            tint = JadeActive,
            modifier = Modifier.padding(end = 2.dp),
        )
        Text(
            text = mode.displayLabel,
            modifier = Modifier
                .weight(1f)
                .testTag(TUTOR_MODE_LABEL_TAG),
            color = InkSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        OutlineActionChip(
            text = mode.switchActionLabel,
            onClick = onSwitch,
            modifier = Modifier.testTag(TUTOR_MODE_SWITCH_TAG),
            contentDescription = mode.displayDescription,
        )
    }
}

internal const val TUTOR_MODE_SWITCH_TAG = "tutor_mode_switch"
internal const val TUTOR_MODE_LABEL_TAG = "tutor_mode_label"
