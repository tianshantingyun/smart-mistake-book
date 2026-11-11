package com.tingyun.smartmistakebook.visualjs

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.tingyun.smartmistakebook.BuildConfig
import com.tingyun.smartmistakebook.core.data.visual.JsFigureArtifactAdapter
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepository
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualPreviewLabel
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualPreviewRendererFactory
import com.tingyun.smartmistakebook.core.data.visual.VisualFrameInputs
import com.tingyun.smartmistakebook.core.data.visual.VisualFrameSnapshot
import com.tingyun.smartmistakebook.core.data.visual.VisualMeasuredLayoutArtifact
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** Owns the application engine and the real layout inputs used by static drawings. */
internal class VisualJsFigureHost(context: Context) : VisualFrameInputs {
    val engine = VisualJsClientEngine(context.applicationContext)
    private data class Layout(val viewport: VisualViewport, val fontScaleMilli: Int, val theme: String)
    @Volatile private var layout: Layout? = null
    @Volatile private var ready = false

    init {
        val configuration = context.resources.configuration
        val width = (configuration.screenWidthDp - 36).coerceIn(120, 720)
        updateFrame(width, width * 2 / 3, configuration.fontScale,
            if (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light")
    }

    suspend fun prepare(): Boolean {
        if (ready) return true
        val result = engine.execute(VisualExecutionRequest(
            programId = "host-readiness-v1", sourceJs = READINESS_PROGRAM, sceneRevision = 1,
            stateJson = "{}", viewport = VisualViewport(160, 120), timeMs = 0, seed = 0, theme = "light",
        ))
        ready = result is VisualExecutionResult.Rendered && result.drawCommandsJson != null &&
            result.contents.size == 1 && result.contents.single().measured.layoutJson != null
        return ready
    }

    fun close() { ready = false; engine.close() }

    fun updateFrame(widthDu: Int, heightDu: Int, fontScale: Float, theme: String) {
        if (widthDu <= 0 || heightDu <= 0) return
        layout = Layout(VisualViewport(widthDu.coerceAtMost(4_096), heightDu.coerceAtMost(4_096)),
            (fontScale * 1_000).roundToInt(), theme)
    }

    override fun available(): Boolean = ready && layout != null

    override fun forGeneration(request: FigureGenerationRequest, proposal: FigureGenerationProposal): VisualFrameSnapshot? {
        if (!ready) return null
        val current = layout ?: return null
        val seed = figureSha256Hex(request.operationId.toByteArray(Charsets.UTF_8)).take(13).toLong(16)
        return VisualFrameSnapshot(current.viewport, current.theme, seed, 0, current.fontScaleMilli)
    }

    fun artifactAdapter(context: Context, database: StudyDatabasePort) = JsFigureArtifactAdapter(
        snapshots = TutorVisualCommitRepository(database,
            freeSpaceBytes = { context.filesDir.usableSpace }),
        preview = TutorVisualPreviewRendererFactory.forProduction(context, database),
        labels = ::labels,
        runtimeVersion = BuildConfig.VISUAL_RUNTIME_VERSION,
    )

    private fun labels(frame: VisualFrameSnapshot, rendered: VisualExecutionResult.Rendered): List<TutorVisualPreviewLabel> =
        rendered.contents.map { content ->
            val metric = content.measured
            require(metric.fontScaleMilli == frame.fontScaleMilli) { "字体设置已变化，请重试绘图" }
            val artifact = Json.decodeFromString(VisualMeasuredLayoutArtifact.serializer(),
                requireNotNull(metric.layoutJson) { "图中文字布局缺失" })
            require(artifact.kind == content.declaration.kind && artifact.digest == metric.digest &&
                artifact.widthDu == metric.widthDu && artifact.heightDu == metric.heightDu &&
                artifact.baselineDu == metric.baselineDu && artifact.fontSizeSp == metric.fontSizeSp) {
                "图中文字布局与执行结果不一致"
            }
            TutorVisualPreviewLabel(metric.contentId, content.declaration.payload, artifact)
        }

    private companion object {
        const val READINESS_PROGRAM = """
            const visual = {
              prepare: function () { return [{kind: 'text', payload: 'ready'}]; },
              render: function (input, metrics) {
                return {schemaVersion: 1, sceneId: 'host-ready', revision: input.revision,
                  viewBox: {x: 0, y: 0, width: input.viewport.widthDu, height: input.viewport.heightDu},
                  paths: [], root: {type: 'group', children: [
                    {type: 'label', contentId: metrics[0].contentId, anchor: {x: 20, y: 30},
                     hAlign: 'left', vAlign: 'top', color: '#26354a'}]}, controls: [], hitRegions: []};
              }
            };
        """
    }
}

@Composable
internal fun Modifier.trackFigureViewport(host: VisualJsFigureHost?): Modifier {
    val density = LocalDensity.current
    val theme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) "dark" else "light"
    var widthPx by remember { mutableIntStateOf(0) }
    SideEffect {
        if (widthPx > 0) {
            val widthDu = (widthPx / density.density - 36).roundToInt().coerceIn(120, 720)
            host?.updateFrame(widthDu, widthDu * 2 / 3, density.fontScale, theme)
        }
    }
    return onSizeChanged { widthPx = it.width }
}
