package com.tingyun.smartmistakebook.core.ui

// Copy feedback timing from CodeBlock.tsx (Caho1/open-claude-ui c1f923ae, MIT).
import android.content.ClipData
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ClaudeCopyButton(text: String, modifier: Modifier = Modifier, label: String = "复制",
    contentColor: Color = InkSecondary, errorColor: Color = ErrorText,
    successColor: Color = JadeActive) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var feedback by remember(text) { mutableStateOf<String?>(null) }
    var fading by remember(text) { mutableStateOf(false) }
    var copyJob by remember { mutableStateOf<Job?>(null) }
    val motion = LocalClaudeMotionEnabled.current
    val feedbackAlpha by animateFloatAsState(if (fading && motion) 0f else 1f,
        animationSpec = if (motion) tween(300) else snap(), label = "cds-copy-decay")
    ClaudeTextButton(onClick = {
        copyJob?.cancel()
        copyJob = scope.launch {
            fading = false
            feedback = try {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
                "已复制"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                "复制失败"
            }
            delay(2200)
            fading = true
            delay(300)
            feedback = null
            fading = false
        }
    }, modifier = modifier) {
        val tint = when (feedback) {
            "复制失败" -> errorColor
            "已复制" -> successColor
            else -> contentColor
        }
        if (feedback == "已复制") Icon(Icons.Outlined.Check, contentDescription = null,
            tint = tint, modifier = Modifier.size(14.dp).alpha(feedbackAlpha))
        Text(feedback ?: label, style = ClaudeLayout.ControlText,
            modifier = Modifier.alpha(feedbackAlpha), color = tint)
    }
}
