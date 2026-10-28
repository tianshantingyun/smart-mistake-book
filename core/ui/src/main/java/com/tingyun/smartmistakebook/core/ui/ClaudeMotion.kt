package com.tingyun.smartmistakebook.core.ui

// Ported from Caho1/open-claude-ui cds-theme.css / ClaudeMotionPresets.ts, c1f923ae (MIT).
// Original copyright and full license are bundled in assets/open-source/open-claude-ui-LICENSE.txt.
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.animateFloatAsState

/** Only consumed motion roles. Durations are source values; pixels and typography never animate. */
object ClaudeMotion {
    const val PressMillis = 150
    const val SnapMillis = 120
    const val RevealMillis = 200
    const val PageMillis = 180
    val Swift = CubicBezierEasing(.16f, 1f, .3f, 1f)
    val Expand = CubicBezierEasing(.19f, 1f, .22f, 1f)
    val Enter = CubicBezierEasing(.22f, 1f, .36f, 1f)
    val Out = CubicBezierEasing(.165f, .84f, .44f, 1f)
    val Snap = CubicBezierEasing(.32f, .72f, 0f, 1f)

    fun <T> spec(enabled: Boolean, duration: Int = SnapMillis): FiniteAnimationSpec<T> =
        if (enabled) tween(duration, easing = Swift) else snap()

    /** Keep total travel within 2dp, including wide buttons and entry cards. */
    internal fun pressedScale(longestEdgeDp: Float): Float =
        if (longestEdgeDp.isFinite() && longestEdgeDp > 0f) {
            (1f - 2f / longestEdgeDp).coerceIn(.96f, 1f)
        } else 1f
}

/** A caller can reduce motion further, but cannot re-enable animations disabled by the system. */
val LocalClaudeMotionEnabled = staticCompositionLocalOf { true }

@Composable
internal fun ClaudeMotionTheme(content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    fun readScale(): Float = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    var scale by remember(resolver) { mutableFloatStateOf(readScale()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = readScale() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        // Read after registration as well, so a scale change during setup cannot be missed.
        scale = readScale()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalClaudeMotionEnabled provides (LocalClaudeMotionEnabled.current && scale > 0f), content = content)
}

/** Measured disclosure. Does not own domain state or dispatch anything when an animation finishes. */
@Composable
fun ClaudeDisclosure(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val enabled = LocalClaudeMotionEnabled.current
    AnimatedVisibility(visible, modifier,
        enter = if (enabled) expandVertically(tween<IntSize>(ClaudeMotion.RevealMillis, easing = ClaudeMotion.Expand)) +
            fadeIn(tween(ClaudeMotion.PressMillis)) else EnterTransition.None,
        exit = if (enabled) shrinkVertically(tween<IntSize>(160, easing = ClaudeMotion.Expand)) +
            fadeOut(tween(ClaudeMotion.SnapMillis)) else ExitTransition.None,
        content = content)
}

/** One entrance for a modal instance; gesture transforms and viewport measurement stay independent. */
@Composable
fun Modifier.claudeModalEntrance(): Modifier {
    val enabled = LocalClaudeMotionEnabled.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val fraction by animateFloatAsState(if (entered || !enabled) 1f else 0f,
        animationSpec = if (enabled) tween(ClaudeMotion.RevealMillis, easing = ClaudeMotion.Out) else snap(),
        label = "cds-modal-entrance")
    return graphicsLayer { alpha = fraction; scaleX = .95f + .05f * fraction; scaleY = scaleX }
}
