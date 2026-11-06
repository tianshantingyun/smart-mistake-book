package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import android.graphics.Bitmap
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
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D-04 预览渲染器的**设备向量**（批次 39；R1 `docs/research/2026-11-01-preview-wiring.md`
 * §3 的 N5/N6/N7 判据 + §2.3 取消重抛语义）。测试位 = core:data androidTest（同模块
 * internal 直达，零 seam；全程形归 app V-01 链套件）。
 *
 * 判据映射（与 JVM 半边 `TutorVisualPreviewRendererTest` 分工：两门独立判、不得合并，
 * `VisualLayoutBudgetTest.kt:42-50` "防发现等价"的设备镜像）：
 * - **N5**（du 域**累计**门，内容预算语义）：viewport 4096² du → 预览 du 面积 = 4× 预算 →
 *   `LAYOUT_PIXEL_BUDGET` 前缀 + 零写入口 + 快照照常提交（LIVE 且 preview_asset_id NULL，
 *   防漂移钉：静默缩采样会让码断言与 null 断言同时红）；
 * - **N6**（px×B/px **单次分配**门，内存事实语义）：2050²×4 = 16,810,000 B > 16 MiB →
 *   门直调拒绝（逐字对齐 JVM 半边）+ 渲染器注入 pxPerDu 越过策略证明"门接在分配**前**"；
 * - **N7**（"文字与预览合计"语义）：2048² 预览独判**恰达不拒**（"≤"的设备钉，真渲染+
 *   persist+提交全链）→ 追加 1 du² 合成度量条目合计越界 → 拒且只拒预览（快照照常提交）；
 * - **取消重抛**：persist 被取消 → `CancellationException` 原样穿透，不折
 *   [TutorVisualPreviewOutcome.Refused]（取消不是超限，吞掉会把取消伪装成"预览不可用"）。
 *
 * 先红后绿：渲染器未落时本套件编译不过即红；本环境零 Gradle/零设备纪律，执行状态
 * **UNVERIFIED**（归门禁串行 + 设备轨）。方法名无空格（D8 SimpleName 约束）。
 *
 * **R-04 登记**（R1 §2.7）：生产 commit 接通前渲染器只被 androidTest 引用；本套件用例
 * 不得进 R-04 release 轨清单，keep 义务与 `VisualChainTestSeams` 一并登记
 * （`app/r04-test-keep.pro`，R-04 批落地）。
 */
@RunWith(AndroidJUnit4::class)
class TutorVisualPreviewRendererInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val databases = mutableListOf<StudyDatabasePort>()
    private val databaseNames = mutableListOf<String>()

    @After
    fun tearDown() {
        databases.forEach { runCatching { it.close() } }
        databases.clear()
        databaseNames.forEach { name -> runCatching { context.deleteDatabase(name) } }
        databaseNames.clear()
    }

    // ------------------------------------------------------------ N5：满幅预览拒绝（du 累计口径）

    @Test
    fun fullCanvasPreviewRefusalLeavesTheSnapshotCommittableWithoutAPreviewReference() =
        runBlocking {
            withDatabase { store ->
                val recorder = PersistRecorder()
                val renderer = TutorVisualPreviewRendererFactory.create(
                    recordingPersister(recorder, store),
                )

                val outcome = renderer.renderPreview(
                    // drawCommands 恒不解析（准入先于解析）——任意非空文本即可。
                    request(drawCommandsJson = "...", viewportWidthDu = 4096, viewportHeightDu = 4096),
                )

                // ① 拒绝码前缀（du 累计门，不是字节门——N5/N6 分界）。
                val refused = refusedOf(outcome)
                assertEquals(VisualLayoutBudget.LAYOUT_PIXEL_BUDGET, refused.code)
                // ② 零写入口（拒绝发生在 createBitmap/persist 之前——无 OOM 是它的伴生证据）。
                assertEquals("拒绝必须发生在任何 vault 写入口之前", 0, recorder.writeAttempts)
                // ③ 门后进程仍可完成一次小分配（无隐性内存污染）。
                allocationProbe()

                // ④ 快照照常提交：LIVE 且 preview_asset_id IS NULL（防"静默缩采样"漂移钉）。
                val repository = TutorVisualCommitRepository(
                    database = store,
                    freeSpaceBytes = { Long.MAX_VALUE },
                    clock = { 9_000 },
                )
                val committed = repository.commitAssistantTurnWithVisual(
                    message = assistantCommand(MESSAGE_ID),
                    program = programInput(),
                    snapshot = snapshotInput(
                        drawCommandsJson = validDocumentText(4096, 4096),
                        viewportWidthDu = 4096,
                        viewportHeightDu = 4096,
                        previewAssetId = null,
                    ),
                    ownerRequestId = OWNER_ID,
                )
                assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
                val row = requireNotNull(store.readTutorVisualSnapshot(committed.snapshot.snapshotId))
                assertNull("预览缺席不影响快照本体", row.previewAssetId)
            }
        }

    // ------------------------------------------------------------ N6：createBitmap 分配前拒绝（字节口径）

    @Test
    fun theByteGateRefusesAnOverBudgetAllocationBeforeCreationAndTheRendererWiresItBeforeAllocating() {
        // 形 1（门直调，R1 §3.2 认可形态）：2050×2050×4 = 16,810,000 B > 16 MiB——
        // 逐字对齐 JVM 半边（VisualLayoutBudgetTest `the exact 16 MiB…`）。
        try {
            VisualLayoutBudget.requireBitmapBytesWithinBudget(
                2050,
                2050,
                VisualLayoutBudget.ARGB_8888_BYTES_PER_PIXEL,
            )
            fail("a 16,810,000-byte allocation must be refused before creation")
        } catch (expected: IllegalArgumentException) {
            assertTrue(
                "the refusal must carry the BITMAP_BYTE_BUDGET code, got: ${expected.message}",
                expected.message.orEmpty().startsWith(VisualLayoutBudget.BITMAP_BYTE_BUDGET),
            )
        }
        allocationProbe()

        // 形 2（渲染器接线顺序证明）：生产策略上限 1600 px 不可能自然产生越门请求，
        // 注入 pxPerDu = 2050/2048 越过策略 → 门在分配**前**拒绝。若门被挪到分配后/
        // 删掉，本例红（分配成功 → draw → persist → Accepted），这就是它消灭的失败。
        runBlocking {
            withDatabase { store ->
                val recorder = PersistRecorder()
                val impl = TutorVisualPreviewRendererFactory.create(
                    recordingPersister(recorder, store),
                ) as TutorVisualPreviewRendererImpl
                val outcome = impl.renderPreview(
                    request(
                        drawCommandsJson = validDocumentText(2048, 2048),
                        viewportWidthDu = 2048,
                        viewportHeightDu = 2048,
                    ),
                    pxPerDuOverride = 2050f / 2048f, // ceil(2048 × 2050/2048) = 2050 → 16,810,000 B
                )
                val refused = refusedOf(outcome)
                assertEquals(VisualLayoutBudget.BITMAP_BYTE_BUDGET, refused.code)
                assertEquals("越门请求必须在任何写入口之前被拒", 0, recorder.writeAttempts)
                allocationProbe()
            }
        }
    }

    // ------------------------------------------------------------ N7：合计语义（恰达不拒 + 越界只拒预览）

    @Test
    fun aPreviewThatFitsAloneIsAcceptedAndCommittableWhileACombinedOverflowRefusesOnlyThePreview() =
        runBlocking {
            withDatabase { store ->
                val recorder = PersistRecorder()
                val renderer = TutorVisualPreviewRendererFactory.create(
                    recordingPersister(recorder, store),
                )

                // 对照半例：2048² du 预览独判 = 4,194,304 **恰达不拒**（"≤"语义的设备钉）
                // → 真渲染（1600² px）→ persist → Accepted → previewAssetId 落库（RESTRICT
                // FK 建立引用）。
                val accepted = renderer.renderPreview(
                    request(
                        drawCommandsJson = validDocumentText(2048, 2048),
                        viewportWidthDu = 2048,
                        viewportHeightDu = 2048,
                    ),
                )
                val previewAssetId = when (accepted) {
                    is TutorVisualPreviewOutcome.Accepted -> accepted.previewAssetId
                    is TutorVisualPreviewOutcome.Refused ->
                        error("the exact-4MP preview alone must be admitted, got $accepted")
                }
                assertEquals("恰达渲染恰好落一次写", 1, recorder.writeAttempts)
                assertEquals(
                    "source_type 必须来自共享常量（单一来源）",
                    StudyDbValue.SourceAssetType.LOCAL_VISUAL_PREVIEW,
                    recorder.recordedSourceType,
                )

                val repository = TutorVisualCommitRepository(
                    database = store,
                    freeSpaceBytes = { Long.MAX_VALUE },
                    clock = { 9_000 },
                )
                val committed = repository.commitAssistantTurnWithVisual(
                    message = assistantCommand("$MESSAGE_ID-accepted"),
                    program = programInput(),
                    snapshot = snapshotInput(
                        drawCommandsJson = validDocumentText(2048, 2048),
                        viewportWidthDu = 2048,
                        viewportHeightDu = 2048,
                        previewAssetId = previewAssetId,
                    ),
                    ownerRequestId = OWNER_ID,
                )
                assertEquals(TutorVisualStatus.LIVE, committed.snapshot.status)
                val acceptedRow =
                    requireNotNull(store.readTutorVisualSnapshot(committed.snapshot.snapshotId))
                assertEquals("预览引用随快照落库", previewAssetId, acceptedRow.previewAssetId)

                // 拒绝半例：同 viewport + 1 du² 合成度量条目 → 合计 4,194,305 > 4 MP → 拒，
                // 且只拒预览（快照照常提交，同 N5 断言④）。
                val refused = refusedOf(
                    renderer.renderPreview(
                        request(
                            drawCommandsJson = validDocumentText(2048, 2048),
                            viewportWidthDu = 2048,
                            viewportHeightDu = 2048,
                            measuredEntries = listOf(measured(1f, 1f)),
                        ),
                    ),
                )
                assertEquals(VisualLayoutBudget.LAYOUT_PIXEL_BUDGET, refused.code)
                assertEquals("拒绝半例不得产生第二次写", 1, recorder.writeAttempts)

                val degradedCommit = repository.commitAssistantTurnWithVisual(
                    message = assistantCommand("$MESSAGE_ID-refused"),
                    program = programInput(),
                    snapshot = snapshotInput(
                        drawCommandsJson = validDocumentText(2048, 2048) + " ",
                        viewportWidthDu = 2048,
                        viewportHeightDu = 2048,
                        previewAssetId = null,
                    ),
                    ownerRequestId = "$OWNER_ID-2",
                )
                assertEquals(TutorVisualStatus.LIVE, degradedCommit.snapshot.status)
                val degradedRow =
                    requireNotNull(store.readTutorVisualSnapshot(degradedCommit.snapshot.snapshotId))
                assertNull("合计越界只拒预览，快照本体不带引用", degradedRow.previewAssetId)
            }
        }

    // ------------------------------------------------------------ 取消重抛（有界降级不吞取消）

    @Test
    fun aCancelledPreviewPersistIsRethrownAndNeverFoldedIntoARefusal() = runBlocking {
        withDatabase { store ->
            val cancelled = TutorVisualPreviewAssetPersister(
                persistPngBytes = { _, _, _ ->
                    throw CancellationException("preview persist cancelled")
                },
                registerRow = { },
            )
            val renderer = TutorVisualPreviewRendererFactory.create(cancelled)

            val outcome = runCatching {
                renderer.renderPreview(
                    request(drawCommandsJson = validDocumentText(360, 240), viewportWidthDu = 360, viewportHeightDu = 240),
                )
            }

            val thrown = outcome.exceptionOrNull()
            assertTrue(
                "取消必须原样穿透而不是折成 Refused：$thrown",
                thrown is CancellationException,
            )
            assertEquals("preview persist cancelled", thrown?.message)
            // 对照面：同样的取消若被错误折叠，outcome 会是 success(Refused)——上面的
            // assertTrue 已红。这里再钉"不是静默成功"的形状。
            assertTrue(outcome.isFailure)
            // 库可用性不受取消影响。
            assertNull(store.readTutorVisualSnapshot("no-such-snapshot"))
        }
    }

    // ------------------------------------------------------------ 夹具

    /** persist 写入口观测器 + 注入式写侧（登记行走真实 store，供 RESTRICT FK 建立引用）。 */
    private class PersistRecorder {
        var writeAttempts = 0
        var recordedSourceType: String? = null
        val registered = mutableListOf<CanonicalSourceAssetRecord>()
    }

    private fun recordingPersister(
        recorder: PersistRecorder,
        store: StudyDatabasePort,
    ): TutorVisualPreviewAssetPersister = TutorVisualPreviewAssetPersister(
        persistPngBytes = { _, sourceType, createdAtEpochMillis ->
            recorder.writeAttempts += 1
            recorder.recordedSourceType = sourceType
            canonicalRecord(sourceType, createdAtEpochMillis, recorder.registered.size)
        },
        registerRow = { record ->
            recorder.registered += record
            store.registerCanonicalSourceAsset(record)
        },
    )

    private fun canonicalRecord(
        sourceType: String,
        createdAtEpochMillis: Long,
        sequence: Int,
    ): CanonicalSourceAssetRecord {
        val hex = sequence.toString(16).padStart(2, '0')
        return CanonicalSourceAssetRecord(
            sourceAssetId = "asset-preview-n7-$hex-0000000000000000000000000000",
            contentSha256 = "d04f3ed7038cd1d34e4b0d3f3d3f2f21${hex}000000000000000000000000000000".take(64),
            relativePath = "source-assets/preview-n7-$hex.png",
            mimeType = "image/png",
            byteSize = 128L,
            width = 1600,
            height = 1600,
            sourceType = sourceType,
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }

    private fun request(
        drawCommandsJson: String,
        viewportWidthDu: Int,
        viewportHeightDu: Int,
        labels: List<TutorVisualPreviewLabel> = emptyList(),
        measuredEntries: List<VisualMeasuredContent>? = null,
    ): TutorVisualPreviewRequest = TutorVisualPreviewRequest(
        rendered = VisualExecutionResult.Rendered(
            sceneJson = """{"scene":true}""",
            sceneSchemaVersion = 1,
            contentIds = labels.map { it.contentId }.toSet(),
            drawCommandsJson = drawCommandsJson,
        ),
        viewportWidthDu = viewportWidthDu,
        viewportHeightDu = viewportHeightDu,
        fontScaleMilli = 1_000,
        createdAtEpochMillis = 9_000,
        labels = labels,
        measuredEntries = measuredEntries,
    )

    /** 最小合法 DrawOps V1 文档（空 ops + 不透明白底；解析器/校验器的生成门全过）。 */
    private fun validDocumentText(widthDu: Int, heightDu: Int): String =
        """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":$widthDu,"h":$heightDu},""" +
            """"background":"#ffffff","ops":[],"hitRegions":[],""" +
            """"stats":{"nodes":0,"pathCommands":0,"labels":0,"ops":0}}"""

    private fun measured(widthDu: Float, heightDu: Float) = VisualMeasuredContent(
        contentId = "c-x",
        widthDu = widthDu,
        heightDu = heightDu,
        baselineDu = 0f,
        fontSizeSp = 16f,
        digest = "d",
    )

    private fun refusedOf(outcome: TutorVisualPreviewOutcome): TutorVisualPreviewOutcome.Refused =
        outcome as? TutorVisualPreviewOutcome.Refused
            ?: error("expected a refusal, got $outcome")

    /** N6 断言位③/N5 断言③：拒绝后进程仍可完成一次小分配（无隐性内存污染）。 */
    private fun allocationProbe() {
        val probe = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            probe.eraseColor(android.graphics.Color.WHITE)
            assertEquals(android.graphics.Color.WHITE, probe.getPixel(4, 4))
        } finally {
            probe.recycle()
        }
    }

    private suspend fun withDatabase(block: suspend (StudyDatabasePort) -> Unit) {
        val databaseName = "preview-renderer-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        databases.add(store)
        databaseNames.add(databaseName)
        // 消息行的 FK 需要会话行（与 TutorVisualCommitRepositoryInstrumentedTest.withDatabase
        // 的夹具形状一致：建库后先建会话再提交）。
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
        block(store)
    }

    private fun programInput() = TutorVisualProgramInput(
        learnerId = "learner-preview-n7",
        role = "QUESTION_DIAGRAM",
        sourceJs = "draw();",
        initialStateJson = "{}",
        basisFingerprint = "basis-preview-n7",
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
    )

    private fun snapshotInput(
        drawCommandsJson: String,
        viewportWidthDu: Int,
        viewportHeightDu: Int,
        previewAssetId: String?,
    ) = TutorVisualSnapshotInput(
        stateJson = "{}",
        sceneSchemaVersion = 1,
        sceneJson = """{"scene":true}""",
        renderSchemaVersion = 1,
        drawCommandsJson = drawCommandsJson,
        contentIds = emptySet(),
        viewportWidthDu = viewportWidthDu,
        viewportHeightDu = viewportHeightDu,
        fontScaleMilli = 1_000,
        theme = "light",
        timeMs = 0,
        seed = 0,
        runtimeVersion = "quickjs-test",
        compilerVersion = "visual-compiler-v1",
        rendererVersion = "visual-renderer-v1",
        fontManifestVersion = "font-manifest-v1",
        questionVerificationStatus = "NOT_CHECKED",
        previewAssetId = previewAssetId,
    )

    private fun assistantCommand(messageId: String) = AppendTutorAssistantMessageDatabaseCommand(
        conversationId = CONVERSATION_ID,
        messageId = messageId,
        ordinal = null,
        replyToMessageId = null,
        bodyMarkdown = "图在下面。",
        thinkingMarkdown = null,
        toolTraceJson = """[{"tool":"plot","status":"OK"}]""",
        sourceImageAssetIds = emptyList(),
        logicalOperationId = "operation-$messageId",
        status = "SUCCEEDED",
        createdAtEpochMillis = 1_000,
        completedAtEpochMillis = 1_000,
        errorCode = null,
    )

    private companion object {
        const val CONVERSATION_ID = "conversation-preview-renderer"
        const val MESSAGE_ID = "message-preview-renderer"
        const val OWNER_ID = "owner-preview-renderer"
    }
}
