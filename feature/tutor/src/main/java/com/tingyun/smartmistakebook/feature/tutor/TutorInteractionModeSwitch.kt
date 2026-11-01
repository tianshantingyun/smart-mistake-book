package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.tingyun.smartmistakebook.core.ui.ClaudeLayout
import com.tingyun.smartmistakebook.core.ui.claudeInteractiveRow
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.ContentSurface
import com.tingyun.smartmistakebook.core.ui.ClaudeDropdownMenu as DropdownMenu
import com.tingyun.smartmistakebook.core.ui.ClaudeDropdownMenuItem as DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.tingyun.smartmistakebook.core.ui.InlineActionButton

/**
 * 会话内的交互模式切换（D-Q9）：一行小字说清现在是哪种，右边一个可点的出口换过去。
 *
 * **界面不出现内部词**（模式名、脚手架、L0–L4 一律不上屏）：标签取自
 * [TutorInteractionMode.displayLabel]（"直接回答" / "带着我一步步来"），出口取自
 * [TutorInteractionMode.switchActionLabel]，说明取自 [TutorInteractionMode.displayDescription]
 * ——三句话都只有 `core:model` 那一处出处，改文案不会漂。
 *
 * 采用上游输入工具栏的紧凑选择器外形，仍切换原有的会话属性。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun TutorInteractionModeSwitch(
    mode: TutorInteractionMode,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
    Row(
        modifier = modifier
            .widthIn(max = 180.dp)
            .heightIn(min = 48.dp)
            .claudeInteractiveRow(onClick = { expanded = true }, onClickLabel = "选择回答方式")
            .padding(horizontal = 8.dp)
            .testTag(TUTOR_MODE_SWITCH_TAG)
            .semantics(mergeDescendants = true) { contentDescription = mode.displayDescription },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (mode) {
                TutorInteractionMode.NORMAL -> Icons.Outlined.Forum
                TutorInteractionMode.GUIDED -> Icons.Outlined.School
            },
            contentDescription = null,
            tint = InkSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = mode.displayLabel,
            modifier = Modifier
                .weight(1f, fill = false).testTag(TUTOR_MODE_LABEL_TAG),
            color = InkSecondary,
            style = ClaudeLayout.ControlText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(14.dp), tint = InkSecondary)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
        containerColor = ContentSurface, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Ink.copy(alpha = .1f))) {
        TutorInteractionMode.entries.forEach { option ->
            DropdownMenuItem(text = { Text(option.displayLabel, style = ClaudeLayout.ControlText, color = Ink) },
                onClick = { expanded = false; if (option != mode) onSwitch() },
                modifier = Modifier.testTag("tutor_mode_option_${option.name.lowercase()}"),
                trailingIcon = { if (option == mode) Text("✓", color = JadeActive) })
        }
    }
    }
}

internal const val TUTOR_MODE_SWITCH_TAG = "tutor_mode_switch"
internal const val TUTOR_MODE_LABEL_TAG = "tutor_mode_label"
