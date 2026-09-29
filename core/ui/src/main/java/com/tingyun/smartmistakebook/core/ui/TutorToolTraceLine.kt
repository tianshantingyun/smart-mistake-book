package com.tingyun.smartmistakebook.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.model.TutorToolTraceDisplay
import com.tingyun.smartmistakebook.core.model.decodeTutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.tutorToolTraceDisplay

/**
 * 「它刚才查阅了什么」的那一行：**加粗灰色小字内联**，点开看明细（含被拒理由）。
 *
 * 形态是裁定过的（台账 K2c 改判）：**不是工具卡**——用户原话"Codex 使用的并不是工具卡，而是
 * 加粗的灰色小字"。所以这里只有一行小字：折叠态一句话（「已查阅 · 2 项」），展开后逐条列出
 * 查了什么、拿到几条、没执行的是为什么。它长在会话流里（正文上方），与思考卡是两件东西：
 * 思考是模型的推理，查阅是它对外做的事，混在一张卡里学生分不出"它在想"和"它去查了"。
 *
 * 传 null / 空串（这一轮没查过、或旧行没有痕迹）时**什么都不渲染**：不留空气泡、不留空行。
 * 明细不可展开时（没有条目）也不给箭头——不给点了没反应的入口。
 */
@Composable
fun TutorToolTraceLine(
    display: TutorToolTraceDisplay?,
    modifier: Modifier = Modifier,
    testTag: String = "tutor_tool_trace",
) {
    val shown = display ?: return
    // 展开状态跨重建保留（与思考卡同一手法）：转屏不该把学生刚点开的明细收回去。
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandable = shown.hasDetails
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = expandable,
                    role = Role.Button,
                    onClickLabel = if (expanded) "收起查阅明细" else "展开查阅明细",
                ) { expanded = !expanded }
                .semantics {
                    if (expandable) {
                        stateDescription = if (expanded) "明细已展开" else "明细已收起"
                    }
                }
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = shown.headline,
                color = InkSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            if (expandable) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = InkSecondary,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(14.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && expandable,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                shown.rows.forEach { row ->
                    Text(
                        text = row.text,
                        color = InkMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    row.detail?.let { detail ->
                        Text(
                            text = detail,
                            color = InkMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                shown.omittedNote?.let { note ->
                    Text(
                        text = note,
                        color = InkMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/**
 * 痕迹 JSON → 渲染模型（组合里的一次性解码）。传 null / 空白 / 解不开 / 空痕迹时返回 null，
 * 界面据此整行不渲染。
 */
@Composable
fun rememberTutorToolTraceDisplay(toolTraceJson: String?): TutorToolTraceDisplay? =
    remember(toolTraceJson) {
        decodeTutorTurnToolTrace(toolTraceJson)?.let(::tutorToolTraceDisplay)
    }
