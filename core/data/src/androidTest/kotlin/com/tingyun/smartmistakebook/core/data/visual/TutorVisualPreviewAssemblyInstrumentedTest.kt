package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.TutorVisualStatus
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D-04 预览**生产注入点**的设备面（批次 42 / WS7 项 57/58）。
 *
 * 三例都走**真实**生产渲染链（`TutorVisualPreviewRendererFactory.forProduction`：真
 * Canvas/Bitmap → PNG → canonical vault → 资产行登记）与**真实**仓储
 * （`TutorVisualCommitRepository`）：注入点开关 ON/OFF/拒绝三种形态下，快照行的
 * `preview_asset_id` 分别是"注入的 id"/NULL/NULL，且提交本身都不失败。
 *
 * JVM 半边（[TutorVisualPreviewAssemblyTest]）已覆盖开关默认态、注入/降级/取消重抛与
 * JVM 端到端命令；本套件只补"真实位图/写盘/外键"这一段，两者分界同 R1 §3.4。
 *
 * 本轨不运行 Gradle；设备侧按批次纪律用 `assemble.*AndroidTest` + `adb install -r -t` +
 * `am instrument -e class …TutorVisualPreviewAssemblyInstrumentedTest`（禁用
 * `connectedAndroidTest`）。执行状态在本轮为 **UNVERIFIED**。
 */
@RunWith(AndroidJUnit4::class)
class TutorVisualPreviewAssemblyInstrumentedTest {

    @Test
    fun switchOnRendersThroughTheProductionChainAndCommitsThePreviewId() = runBlocking {
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val renderer = TutorVisualPreviewRendererFactory.forProduction(testContext(), store)
            val assembly = TutorVisualPreviewAssembly(
                renderer = renderer,
                injection = TutorVisualPreviewInjection.ENABLED,
            )
            val snapshot = snapshotInput(viewportWidthDu = 360, viewportHeightDu = 240)

            val prepared = assembly.inject(request(360, 240), snapshot)

            val previewAssetId = requireNotNull(prepared.snapshot.previewAssetId) {
                "the switch is ON and a 360x240 preview must be accepted, diagnostic=${prepared.diagnostic}"
            }
            assertNull("成功注入不需要诊断", prepared.diagnostic)

            val committed = repository(store).commitAssistantTurnWithVisual(
                message = assistantCommand("message-ws7-on"),
                program = programInput(),
                snapshot = prepared.snapshot,
                ownerRequestId = "request-ws7-on",
            )

            val written = store.readTutorVisualSnapshot(committed.snapshot.snapshotId)
            requireNotNull(written) { "the committed snapshot row must be readable" }
            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            assertEquals(
                "设备端真实链路：提交行必须携带注入的预览资产 id",
                previewAssetId,
                written.previewAssetId,
            )
            val asset = store.readCanonicalSourceAsset(previewAssetId)
            requireNotNull(asset) { "the preview asset row must exist (RESTRICT FK is satisfied)" }
            assertEquals(
                StudyDbValue.SourceAssetType.LOCAL_VISUAL_PREVIEW,
                asset.sourceType,
            )
            assertTrue("预览 PNG 字节必须已落 canonical vault", asset.byteSize > 0L)
        }
    }

    @Test
    fun switchOffCommitsWithoutAPreviewOrAnyPreviewAssetWrite() = runBlocking {
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val renderer = TutorVisualPreviewRendererFactory.forProduction(testContext(), store)
            // 不传开关 = 默认关闭；生产装配的形状必须与现有生产逐位相同。
            val assembly = TutorVisualPreviewAssembly(renderer)
            assertFalse(assembly.isEnabled)

            val prepared = assembly.inject(
                request(360, 240),
                snapshotInput(viewportWidthDu = 360, viewportHeightDu = 240),
            )
            assertNull("关闭态必须保持 previewAssetId=null（现有默认不变）", prepared.snapshot.previewAssetId)
            assertNull(prepared.diagnostic)

            val committed = repository(store).commitAssistantTurnWithVisual(
                message = assistantCommand("message-ws7-off"),
                program = programInput(),
                snapshot = prepared.snapshot,
                ownerRequestId = "request-ws7-off",
            )

            val written = requireNotNull(store.readTutorVisualSnapshot(committed.snapshot.snapshotId))
            assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
            assertNull("关闭态提交行不得出现预览引用", written.previewAssetId)
            assertNoPreviewAssetWasWritten(store)
        }
    }

    @Test
    fun aRefusedPreviewDegradesToANullPreviewWithoutFailingTheCommit() = runBlocking {
        withDatabase { store ->
            store.createTutorConversation(conversationCommand())
            val renderer = TutorVisualPreviewRendererFactory.forProduction(testContext(), store)
            val assembly = TutorVisualPreviewAssembly(renderer, TutorVisualPreviewInjection.ENABLED)

            // 4096x4096 du = 16,777,216 du² > 4 MP 准入：真实渲染器在分配位图前拒绝。
            val prepared = assembly.inject(
                request(4_096, 4_096),
                snapshotInput(viewportWidthDu = 4_096, viewportHeightDu = 4_096),
            )

            assertNull("拒绝必须降级为 previewAssetId=null", prepared.snapshot.previewAssetId)
            val diagnostic = requireNotNull(prepared.diagnostic) { "a refusal must carry a bounded diagnostic" }
            assertTrue(
                "拒绝必须带 LAYOUT_PIXEL_BUDGET 码，got: $diagnostic",
                diagnostic.startsWith(VisualLayoutBudget.LAYOUT_PIXEL_BUDGET),
            )

            val committed = repository(store).commitAssistantTurnWithVisual(
                message = assistantCommand("message-ws7-refused"),
                program = programInput(),
                snapshot = prepared.snapshot,
                ownerRequestId = "request-ws7-refused",
            )

            val written = requireNotNull(store.readTutorVisualSnapshot(committed.snapshot.snapshotId))
            assertEquals("快照提交不得因预览失败而失败", TutorVisualStatus.LIVE, committed.snapshot.status)
            assertNull(written.previewAssetId)
            assertNoPreviewAssetWasWritten(store)
        }
    }

    // ------------------------------------------------------------ 夹具

    private fun testContext(): Context = ApplicationProvider.getApplicationContext()

    private suspend fun assertNoPreviewAssetWasWritten(store: StudyDatabasePort) {
        val referenced = store.readReferencedCanonicalAssets()
        val unreferenced = store.readUnreferencedCanonicalAssets()
        val previews = (referenced + unreferenced).filter { asset: CanonicalSourceAssetRecord ->
            asset.sourceType == StudyDbValue.SourceAssetType.LOCAL_VISUAL_PREVIEW
        }
        assertTrue("不得写入任何预览资产行，got: ${previews.map { it.sourceAssetId }}", previews.isEmpty())
    }

    private fun repository(store: StudyDatabasePort) = TutorVisualCommitRepository(
        database = store,
        freeSpaceBytes = { Long.MAX_VALUE },
        clock = { 9_000 },
    )

    private fun request(viewportWidthDu: Int, viewportHeightDu: Int): TutorVisualPreviewRequest =
        TutorVisualPreviewRequest(
            rendered = VisualExecutionResult.Rendered(
                sceneJson = """{"scene":true}""",
                sceneSchemaVersion = 1,
                contentIds = emptySet(),
                drawCommandsJson = DRAW_COMMANDS_TEXT,
            ),
            viewportWidthDu = viewportWidthDu,
            viewportHeightDu = viewportHeightDu,
            fontScaleMilli = 1_000,
            createdAtEpochMillis = 9_000,
        )

    private fun snapshotInput(viewportWidthDu: Int, viewportHeightDu: Int) = TutorVisualSnapshotInput(
        stateJson = "{}",
        sceneSchemaVersion = 1,
        sceneJson = """{"scene":true}""",
        renderSchemaVersion = 1,
        drawCommandsJson = DRAW_COMMANDS_TEXT,
        contentIds = emptySet(),
        viewportWidthDu = viewportWidthDu,
        viewportHeightDu = viewportHeightDu,
        fontScaleMilli = 1_000,
        theme = "LIGHT",
        timeMs = 0,
        seed = 0,
        runtimeVersion = "quickjs-test",
        compilerVersion = "visual-compiler-v1",
        rendererVersion = "visual-renderer-v1",
        fontManifestVersion = "font-manifest-v1",
        questionVerificationStatus = "NOT_CHECKED",
        previewAssetId = null,
    )

    private fun programInput() = TutorVisualProgramInput(
        learnerId = "learner-ws7",
        role = "QUESTION_DIAGRAM",
        sourceJs = "draw();",
        initialStateJson = "{}",
        basisFingerprint = "basis-ws7",
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
    )

    private fun assistantCommand(messageId: String) = AppendTutorAssistantMessageDatabaseCommand(
        conversationId = CONVERSATION_ID,
        messageId = messageId,
        replyToMessageId = null,
        bodyMarkdown = "图在下面。",
        logicalOperationId = "operation-$messageId",
        status = "SUCCEEDED",
        createdAtEpochMillis = 1_000,
        completedAtEpochMillis = 1_000,
        errorCode = null,
    )

    private fun conversationCommand() = CreateTutorConversationDatabaseCommand(
        conversationId = CONVERSATION_ID,
        anchorKind = "TEXT_ONLY",
        anchorId = null,
        anchorRevisionId = null,
        title = null,
        createdAtEpochMillis = 1_000,
    )

    private suspend fun withDatabase(block: suspend (StudyDatabasePort) -> Unit) {
        val context = testContext()
        val databaseName = "batch42-ws7-preview-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            block(store)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    private companion object {
        const val CONVERSATION_ID = "conversation-batch42-ws7"

        /** 合法 DrawOps V1（空 ops，背景填充）；解析器与 painter 的回放入口都接受。 */
        const val DRAW_COMMANDS_TEXT =
            """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":360,"h":240},""" +
                """"background":"#ffffff","ops":[],"hitRegions":[],""" +
                """"stats":{"nodes":0,"pathCommands":0,"labels":0,"ops":0}}"""
    }
}
