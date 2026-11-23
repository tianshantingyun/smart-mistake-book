package com.tingyun.smartmistakebook

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.visual.VisualFrameSnapshot
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualFrameResult
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualReduceResult
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationDecision
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationSourceKind
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.TutorControlKind
import com.tingyun.smartmistakebook.core.model.parseTutorInteractiveSpec
import com.tingyun.smartmistakebook.visualjs.VisualJsFigureHost
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4E.2 全链设备用例：真实隔离 QuickJS 生成带 controls 的 SceneV1 → 真实快照/预览落库 →
 * 消息引用补建 → 交互端口渲染 → reduce 改状态重画 → 草稿 CAS 落库与读回。
 */
@RunWith(AndroidJUnit4::class)
class TutorInteractiveVisualInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<SmartMistakeBookApplication>()
    private val databaseName = "interactive-visual-${System.nanoTime()}.db"
    private var host: VisualJsFigureHost? = null
    private var database: com.tingyun.smartmistakebook.core.database.StudyDatabasePort? = null

    @After
    fun tearDown() {
        // 宿主属于应用进程（后续用例还要用），这里只关本用例的库。
        database?.close()
        context.deleteDatabase(databaseName)
    }

    private fun program() = """
        const visual = {
          prepare: function () { return []; },
          render: function (input) {
            const k = Number(input.state.k === undefined ? 1 : input.state.k);
            const v = input.viewport;
            return {
              schemaVersion: 1, sceneId: 'interactive-scene', revision: input.revision,
              viewBox: {x: 0, y: 0, width: v.widthDu, height: v.heightDu},
              paths: [{id: 'p', commands: [['M', 20, 20], ['L', v.widthDu - 20, v.heightDu - 20 - k * 10]]}],
              root: {type: 'path', pathId: 'p', fill: '#26354a', stroke: {width: 2}},
              controls: [{id: 'k', kind: 'range', label: '斜率', value: k, min: -5, max: 5, step: 0.5}],
              form: {formId: 'check-1', title: '检查一下', rationaleLabel: '理由',
                items: [{id: 'q1', kind: 'radioGroup', prompt: 'k>0 时图像怎么变？',
                  options: [{value: 'up', label: '上移'}, {value: 'down', label: '下移'}]}]},
              hitRegions: []
            };
          },
          reduce: function (state, event) {
            return Object.assign({}, state, {k: Number(event.value)});
          }
        };
    """.trimIndent()

    @Test
    fun interactiveSceneRoundTripsThroughTheRealEngineAndDraftStore() = runBlocking {
        // 复用应用已装配的宿主：另建一个 client 会与同一隔离服务争用绑定，prepare 会假红。
        val jsHost = requireNotNull(context.visualJsFigureHost) { "The app host must be assembled" }
        host = jsHost
        assertTrue("The isolated worker must be ready", jsHost.prepare())
        val store = StudyDatabaseFactory.open(context, databaseName)
        database = store
        store.createTutorConversation(
            CreateTutorConversationDatabaseCommand(
                conversationId = CONVERSATION_ID,
                anchorKind = "TEXT_ONLY",
                anchorId = null,
                anchorRevisionId = null,
                title = null,
                createdAtEpochMillis = 1_000,
            ),
        )
        store.appendTutorAssistantMessage(
            AppendTutorAssistantMessageDatabaseCommand(
                conversationId = CONVERSATION_ID,
                messageId = MESSAGE_ID,
                ordinal = null,
                replyToMessageId = null,
                bodyMarkdown = "图在下面。",
                thinkingMarkdown = null,
                toolTraceJson = null,
                sourceImageAssetIds = emptyList(),
                logicalOperationId = "operation-$MESSAGE_ID",
                status = "SUCCEEDED",
                createdAtEpochMillis = 1_000,
                completedAtEpochMillis = 2_000,
                errorCode = null,
            ),
        )

        val source = program()
        val viewport = VisualViewport(320, 240)
        val renderResult = jsHost.engine.execute(
            VisualExecutionRequest(
                programId = "interactive-program-1",
                sourceJs = source,
                sceneRevision = 1,
                stateJson = """{"k":1}""",
                viewport = viewport,
                timeMs = 0,
                seed = 7,
                theme = "light",
            ),
        )
        val rendered = when (renderResult) {
            is VisualExecutionResult.Rendered -> renderResult
            is VisualExecutionResult.Rejected -> error(
                "render rejected: ${renderResult.reason}: ${renderResult.diagnostics}",
            )
            is VisualExecutionResult.Computed -> error("render returned a computed value")
        }
        assertTrue(rendered.sceneJson, rendered.sceneJson.contains("\"controls\""))

        val artifact = jsHost.artifactAdapter(context, store).publish(
            request = FigureGenerationRequest(
                operationId = "interactive-operation-1",
                purpose = FigureGenerationPurpose.EXPLANATION,
                target = FigureGenerationTarget(fidelity = FigureFidelity.ILLUSTRATIVE),
                capabilities = FigureGenerationCapabilities(
                    availableMethods = setOf(FigureGenerationMethod.JS),
                ),
                budget = FigureGenerationBudget(),
                dispatchParent = FigureDispatchParent.ModelTask(
                    requestId = "interactive-parent-1",
                    reserveChatFinalAnswer = true,
                ),
                source = null,
                constraints = emptyList(),
                sourceKind = FigureGenerationSourceKind.TUTOR_MESSAGE,
                sourceId = MESSAGE_ID,
                basisFingerprint = null,
                explicitUserRetry = false,
            ),
            proposal = FigureGenerationProposal(
                decision = FigureGenerationDecision.GENERATE,
                purpose = FigureGenerationPurpose.EXPLANATION,
                method = FigureGenerationMethod.JS,
                reason = "可交互斜率图",
                fidelity = FigureFidelity.ILLUSTRATIVE,
                program = source,
                initialStateJson = """{"k":1}""",
            ),
            frame = VisualFrameSnapshot(viewport, "light", 7, 0, 1_000),
            rendered = rendered,
        )

        val repository = TutorInteractiveVisualRepository(store, jsHost.engine, jsHost.frameRenderer())
        val scene = repository.read(
            MESSAGE_ID,
            0,
            artifact.programId,
            artifact.snapshotId,
        )
        assertNotNull("The trace-supplied identity must link the message to its LIVE snapshot", scene)
        val spec = parseTutorInteractiveSpec(requireNotNull(scene).sceneJson)
        assertEquals(1, spec.controls.size)
        assertEquals(TutorControlKind.RANGE, spec.controls.single().kind)
        assertEquals("check-1", requireNotNull(spec.form).formId)
        assertEquals(1, spec.form!!.items.size)

        val first = repository.render(scene, scene.stateJson)
        assertTrue(first is TutorInteractiveVisualFrameResult.Rendered)
        assertTrue((first as TutorInteractiveVisualFrameResult.Rendered).frame.pngBytes.isNotEmpty())

        val reduced = repository.reduce(
            scene.copy(stateJson = first.frame.stateJson),
            """{"kind":"change","controlId":"k","value":3.5,"sequence":1}""",
        )
        assertTrue(reduced is TutorInteractiveVisualReduceResult.Updated)
        val updated = (reduced as TutorInteractiveVisualReduceResult.Updated).frame
        assertTrue(updated.stateJson, updated.stateJson.contains("3.5"))
        assertTrue(updated.sceneJson, updated.sceneJson.contains("3.5"))

        val draft = """{"state":${updated.stateJson},"form":{"answers":{"q1":"a"},"rationale":"理由"}}"""
        assertTrue(
            repository.saveDraft(
                MESSAGE_ID,
                0,
                scene.revision,
                draft,
                "digest-1",
            ),
        )
        val reread = repository.read(MESSAGE_ID, 0)
        assertEquals(draft, reread?.uiStateJson)
        assertEquals("digest-1", reread?.formDigest)
    }

    private companion object {
        const val CONVERSATION_ID = "interactive-conversation"
        const val MESSAGE_ID = "interactive-message"
    }
}
