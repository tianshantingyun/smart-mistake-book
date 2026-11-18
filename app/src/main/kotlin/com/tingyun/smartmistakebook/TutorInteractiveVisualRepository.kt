package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.data.visual.TutorVisualFramePng
import com.tingyun.smartmistakebook.core.data.visual.TutorVisualFrameRenderer
import com.tingyun.smartmistakebook.core.data.visual.VisualFrameSnapshot
import com.tingyun.smartmistakebook.visualjs.visualPreviewLabels
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualFrame
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualFrameResult
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualPort
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualReduceResult
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualScene
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionMode
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.TutorInteractiveSceneBudget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 4E.2 交互图的 app 侧实现：读链接/程序/快照，`reduce` 走隔离引擎的 PROGRAM 模式，
 * 重画复用静态预览同一套 DrawOps 校验、4 MP 门与 painter，只是不落资产。
 */
internal class TutorInteractiveVisualRepository(
    private val database: StudyDatabasePort,
    private val engine: VisualExecutionEngine,
    private val frameRenderer: TutorVisualFrameRenderer,
    private val clock: () -> Long = System::currentTimeMillis,
) : TutorInteractiveVisualPort {

    override suspend fun read(
        messageId: String,
        ordinal: Int,
        programId: String?,
        snapshotId: String?,
    ): TutorInteractiveVisualScene? {
        var link = database.readTutorMessageVisual(messageId, ordinal)
        if (link == null && programId != null && snapshotId != null) {
            link = database.linkTutorMessageVisualIfLive(
                messageId = messageId,
                ordinal = ordinal,
                programId = programId,
                snapshotId = snapshotId,
                uiStateJson = null,
                formDigest = null,
                nowEpochMillis = clock(),
            )
        }
        link ?: return null
        val snapshot = database.readTutorVisualSnapshot(link.currentSnapshotId) ?: return null
        val program = database.readTutorVisualProgram(link.programId) ?: return null
        val revision = runCatching {
            Json.parseToJsonElement(snapshot.sceneJson).jsonObject["revision"]!!.jsonPrimitive.intOrNull
        }.getOrNull() ?: link.revision
        val draftState = runCatching {
            link.uiStateJson?.let { draft ->
                (Json.parseToJsonElement(draft) as? JsonObject)?.get("state")?.takeIf { it is JsonObject }?.toString()
            }
        }.getOrNull()
        return TutorInteractiveVisualScene(
            messageId = messageId,
            ordinal = ordinal,
            revision = link.revision,
            programId = link.programId,
            sourceJs = program.sourceJs,
            initialStateJson = program.initialStateJson,
            stateJson = draftState ?: snapshot.stateJson,
            sceneJson = snapshot.sceneJson,
            drawCommandsJson = snapshot.drawCommandsJson,
            viewportWidthDu = snapshot.viewportWidthDu,
            viewportHeightDu = snapshot.viewportHeightDu,
            fontScaleMilli = snapshot.fontScaleMilli,
            theme = snapshot.theme,
            timeMs = snapshot.timeMs,
            seed = snapshot.seed,
            sceneRevision = revision.coerceAtLeast(1),
            uiStateJson = link.uiStateJson,
            formDigest = link.formDigest,
        )
    }

    override suspend fun render(
        scene: TutorInteractiveVisualScene,
        stateJson: String,
    ): TutorInteractiveVisualFrameResult = renderState(scene, stateJson)

    override suspend fun reduce(
        scene: TutorInteractiveVisualScene,
        eventJson: String,
    ): TutorInteractiveVisualReduceResult {
        val event = runCatching { Json.parseToJsonElement(eventJson) as? JsonObject }.getOrNull()
            ?: return TutorInteractiveVisualReduceResult.Rejected("事件不是 JSON 对象")
        val state = runCatching { Json.parseToJsonElement(scene.stateJson) as? JsonObject }.getOrNull()
            ?: return TutorInteractiveVisualReduceResult.Rejected("当前状态不是 JSON 对象")
        val combined = buildJsonObject {
            put("state", state)
            put("event", event)
        }.toString()
        if (combined.length > TutorInteractiveSceneBudget.MAX_STATE_JSON_CHARS * 2) {
            return TutorInteractiveVisualReduceResult.Rejected("状态或事件超出交互预算")
        }
        // reduce 在 PROGRAM 模式下执行：新上下文、无 SDK、无时钟；sourceJs 末尾追加调用点，
        // 程序没有 reduce 时 fail-closed（不把旧状态当成"没变化"）。
        val wrapped = scene.sourceJs + "\n; if (typeof visual === 'undefined' || " +
            "typeof visual.reduce !== 'function') { throw new Error('MISSING_REDUCE'); } " +
            "return visual.reduce(input.state.state, input.state.event);"
        val result = engine.execute(
            com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest(
                programId = scene.programId,
                sourceJs = wrapped,
                sceneRevision = scene.sceneRevision,
                stateJson = combined,
                viewport = VisualViewport(scene.viewportWidthDu, scene.viewportHeightDu),
                timeMs = clock(),
                seed = scene.seed,
                theme = scene.theme,
                executionMode = VisualExecutionMode.PROGRAM,
            ),
        )
        val value = when (result) {
            is VisualExecutionResult.Computed -> result.valueJson
            is VisualExecutionResult.Rendered -> return TutorInteractiveVisualReduceResult.Rejected(
                "reduce 返回了场景而不是状态",
            )
            is VisualExecutionResult.Rejected -> return TutorInteractiveVisualReduceResult.Rejected(
                "reduce 未完成（${result.reason}）：${result.diagnostics.take(160)}",
            )
        }
        val nextState = runCatching { Json.parseToJsonElement(value) as? JsonObject }.getOrNull()
            ?: return TutorInteractiveVisualReduceResult.Rejected("reduce 没有返回 StateV1 对象")
        val nextStateJson = nextState.toString()
        if (nextStateJson.length > TutorInteractiveSceneBudget.MAX_STATE_JSON_CHARS) {
            return TutorInteractiveVisualReduceResult.Rejected("reduce 返回的状态超出交互预算")
        }
        return when (val frame = renderState(scene, nextStateJson)) {
            is TutorInteractiveVisualFrameResult.Rendered -> TutorInteractiveVisualReduceResult.Updated(frame.frame)
            is TutorInteractiveVisualFrameResult.Rejected -> TutorInteractiveVisualReduceResult.Rejected(frame.diagnostic)
        }
    }

    override suspend fun saveDraft(
        messageId: String,
        ordinal: Int,
        expectedRevision: Int,
        uiStateJson: String?,
        formDigest: String?,
    ): Boolean = database.updateTutorMessageVisualDraft(
        messageId = messageId,
        ordinal = ordinal,
        expectedRevision = expectedRevision,
        uiStateJson = uiStateJson,
        formDigest = formDigest,
        updatedAtEpochMillis = clock(),
    )

    private suspend fun renderState(
        scene: TutorInteractiveVisualScene,
        stateJson: String,
    ): TutorInteractiveVisualFrameResult {
        val request = com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest(
            programId = scene.programId,
            sourceJs = scene.sourceJs,
            sceneRevision = scene.sceneRevision,
            stateJson = stateJson,
            viewport = VisualViewport(scene.viewportWidthDu, scene.viewportHeightDu),
            timeMs = scene.timeMs,
            seed = scene.seed,
            theme = scene.theme,
        )
        val rendered = when (val result = engine.execute(request)) {
            is VisualExecutionResult.Rendered -> result
            is VisualExecutionResult.Computed -> return TutorInteractiveVisualFrameResult.Rejected(
                "场景渲染返回了程序值而不是场景",
            )
            is VisualExecutionResult.Rejected -> return TutorInteractiveVisualFrameResult.Rejected(
                "渲染未完成（${result.reason}）：${result.diagnostics.take(160)}",
            )
        }
        val drawCommands = rendered.drawCommandsJson
            ?: return TutorInteractiveVisualFrameResult.Rejected("渲染结果没有可绘制的 DrawOps")
        val frame = VisualFrameSnapshot(
            viewport = VisualViewport(scene.viewportWidthDu, scene.viewportHeightDu),
            theme = scene.theme,
            seed = scene.seed,
            timeMs = scene.timeMs,
            fontScaleMilli = scene.fontScaleMilli,
        )
        val labels = runCatching { visualPreviewLabels(frame, rendered) }.getOrElse { failure ->
            return TutorInteractiveVisualFrameResult.Rejected(
                "标签布局不可用：${failure.message?.take(120) ?: failure::class.simpleName}",
            )
        }
        val png = com.tingyun.smartmistakebook.core.data.visual.TutorVisualPreviewRequest(
            rendered = rendered,
            viewportWidthDu = scene.viewportWidthDu,
            viewportHeightDu = scene.viewportHeightDu,
            fontScaleMilli = scene.fontScaleMilli,
            createdAtEpochMillis = clock(),
            labels = labels,
            measuredEntries = rendered.contents.map { it.measured },
            preferredPxPerDu = 3f,
        )
        return when (val painted = frameRenderer.renderPng(png)) {
            is TutorVisualFramePng.Rendered -> TutorInteractiveVisualFrameResult.Rendered(
                TutorInteractiveVisualFrame(
                    stateJson = stateJson,
                    sceneJson = rendered.sceneJson,
                    drawCommandsJson = drawCommands,
                    pngBytes = painted.bytes,
                ),
            )
            is TutorVisualFramePng.Refused -> TutorInteractiveVisualFrameResult.Rejected(
                "${painted.code}: ${painted.diagnostic.take(160)}",
            )
        }
    }
}
