package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.data.model.FigurePromptAssembler
import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CommitTutorTurnWithVisualCommand
import com.tingyun.smartmistakebook.core.database.TutorVisualCheckpointSummary
import com.tingyun.smartmistakebook.core.database.TutorVisualTurnCommitRecord
import com.tingyun.smartmistakebook.core.database.port.TutorVisualPort
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-04 预览**生产注入点**的 JVM 面（批次 42 / WS7）。
 *
 * 判据映射（计划 `.zcode/plans/plan-2026-11-04-batch-42.md` §2 WS7 项 56–60）：
 * - 56 默认关闭：不传开关时与现有生产逐位相同——**零渲染调用**，快照（含调用方已有的
 *   `previewAssetId`）原样返回；
 * - 57 开关打开端到端（JVM 半边）：开关 ON → 渲染端口 → `previewAssetId` 注入 →
 *   **真实仓储**的 commit 命令携带该 id（渲染端口与资产写侧用假件替代——真实
 *   Bitmap/Canvas 半边在 `TutorVisualPreviewAssemblyInstrumentedTest`）；
 * - 58 失败降级：拒绝/抛异常/OOM 都折 `previewAssetId = null` + 有界诊断，不继承旧 id、
 *   不阻断提交；取消重抛；
 * - 59 广告面零改动：开关 ON 时生产能力常量仍只含 IMAGE_MODEL、提示词不出现 JS。
 *
 * 先红后绿：本文件在 [TutorVisualPreviewAssembly] 落盘前引用即编译红（符号缺失）；
 * 本环境按批次纪律**不运行 Gradle**，执行归门禁阶段（结果在本轮记 UNVERIFIED）。
 */
class TutorVisualPreviewAssemblyTest {

    // ------------------------------------------------------------ 夹具

    /** 合法 DrawOps V1 文本（解析器接受；预览管线之外的断言不依赖它的内容）。 */
    private val drawCommandsText =
        """{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":360,"h":240},""" +
            """"background":"#ffffff","ops":[],"hitRegions":[],""" +
            """"stats":{"nodes":0,"pathCommands":0,"labels":0,"ops":0}}"""

    /** 可编程渲染端口：记录调用次数，便于断言"关闭时不触碰渲染"。 */
    private class ScriptedRenderer(
        private val respond: suspend () -> TutorVisualPreviewOutcome,
    ) : TutorVisualPreviewRenderer {
        var calls: Int = 0
            private set

        override suspend fun renderPreview(request: TutorVisualPreviewRequest): TutorVisualPreviewOutcome {
            calls += 1
            return respond()
        }
    }

    private fun request(
        viewportWidthDu: Int = 360,
        viewportHeightDu: Int = 240,
        fontScaleMilli: Int = 1_000,
    ): TutorVisualPreviewRequest = TutorVisualPreviewRequest(
        rendered = VisualExecutionResult.Rendered(
            sceneJson = """{"scene":true}""",
            sceneSchemaVersion = 1,
            contentIds = emptySet(),
            drawCommandsJson = drawCommandsText,
        ),
        viewportWidthDu = viewportWidthDu,
        viewportHeightDu = viewportHeightDu,
        fontScaleMilli = fontScaleMilli,
        createdAtEpochMillis = 9_000,
    )

    private fun snapshotInput(
        viewportWidthDu: Int = 360,
        viewportHeightDu: Int = 240,
        fontScaleMilli: Int = 1_000,
        previewAssetId: String? = null,
    ): TutorVisualSnapshotInput = TutorVisualSnapshotInput(
        stateJson = "{}",
        sceneSchemaVersion = 1,
        sceneJson = """{"scene":true}""",
        renderSchemaVersion = 1,
        drawCommandsJson = drawCommandsText,
        contentIds = emptySet(),
        viewportWidthDu = viewportWidthDu,
        viewportHeightDu = viewportHeightDu,
        fontScaleMilli = fontScaleMilli,
        theme = "LIGHT",
        timeMs = 0,
        seed = 0,
        runtimeVersion = "quickjs-test",
        compilerVersion = "visual-compiler-v1",
        rendererVersion = "visual-renderer-v1",
        fontManifestVersion = "font-manifest-v1",
        questionVerificationStatus = "NOT_CHECKED",
        previewAssetId = previewAssetId,
    )

    private fun programInput(): TutorVisualProgramInput = TutorVisualProgramInput(
        learnerId = "learner-ws7",
        role = "QUESTION_DIAGRAM",
        sourceJs = "draw();",
        initialStateJson = "{}",
        basisFingerprint = "basis-ws7",
        apiVersion = 1,
        sdkVersion = "visual-sdk-v1",
    )

    private fun assistantCommand(): AppendTutorAssistantMessageDatabaseCommand =
        AppendTutorAssistantMessageDatabaseCommand(
            conversationId = "conversation-ws7",
            messageId = "message-ws7",
            replyToMessageId = null,
            bodyMarkdown = "图在下面。",
            logicalOperationId = "operation-ws7",
            status = "SUCCEEDED",
            createdAtEpochMillis = 1_000,
            completedAtEpochMillis = 1_000,
            errorCode = null,
        )

    // ------------------------------------------------------------ 项 56：默认关闭

    @Test
    fun `the injection point is off by default and never touches the renderer`() = runBlocking {
        val renderer = ScriptedRenderer { error("the renderer must not run while the switch is off") }
        // 不传开关 = 默认态；构造签名只允许省略第二个实参。
        val assembly = TutorVisualPreviewAssembly(renderer)
        val callerSnapshot = snapshotInput(previewAssetId = "caller-owned-asset")

        val result = assembly.inject(request(), callerSnapshot)

        assertFalse("默认必须是关闭态（MVA 未过前生产唯一合法态）", assembly.isEnabled)
        assertEquals("关闭时快照输入必须逐位不变（含调用方已有的 id）", callerSnapshot, result.snapshot)
        assertEquals("caller-owned-asset", result.snapshot.previewAssetId)
        assertNull("关闭时不该产生任何诊断", result.diagnostic)
        assertEquals("关闭时渲染器必须零调用（不分配位图、不写 vault）", 0, renderer.calls)
    }

    // ------------------------------------------------------------ 项 57：开关打开（JVM 半边）

    @Test
    fun `an enabled switch injects the accepted preview id into the snapshot`() = runBlocking {
        val assembly = TutorVisualPreviewAssembly(
            renderer = ScriptedRenderer { TutorVisualPreviewOutcome.Accepted("preview-asset-1") },
            injection = TutorVisualPreviewInjection.ENABLED,
        )

        val result = assembly.inject(request(), snapshotInput())

        assertTrue(assembly.isEnabled)
        assertEquals("preview-asset-1", result.snapshot.previewAssetId)
        assertNull("成功路径不需要诊断", result.diagnostic)
    }

    @Test
    fun `the injected preview id reaches the real commit command`() = runBlocking {
        val port = CapturingVisualPort()
        val repository = TutorVisualCommitRepository(
            database = port,
            freeSpaceBytes = { Long.MAX_VALUE },
            clock = { 9_000 },
        )
        val assembly = TutorVisualPreviewAssembly(
            renderer = ScriptedRenderer { TutorVisualPreviewOutcome.Accepted("preview-asset-e2e") },
            injection = TutorVisualPreviewInjection.ENABLED,
        )

        val prepared = assembly.inject(request(), snapshotInput())
        val failure = runCatching {
            repository.commitAssistantTurnWithVisual(
                message = assistantCommand(),
                program = programInput(),
                snapshot = prepared.snapshot,
                ownerRequestId = "request-ws7",
            )
        }

        // 捕获端口在记录命令后主动终止，避免在 JVM 上伪造三张返回记录（与断言无关）。
        assertTrue(
            "the capturing port must stop the commit after recording: $failure",
            failure.exceptionOrNull() is StopAfterCapture,
        )
        val command = requireNotNull(port.committed) { "the commit command must reach the port" }
        assertEquals(
            "开关打开时 previewAssetId 必须随快照进入真实 commit 命令",
            "preview-asset-e2e",
            command.snapshot.previewAssetId,
        )
    }

    @Test
    fun `an enabled real renderer degrades end to end at the commit command`() = runBlocking {
        // 真实渲染器（非假件）的拒绝路径不需要 Android Bitmap（准入先于分配），因此 JVM
        // 可以跑通"装配 → 真实渲染器拒绝 → 真实仓储命令"的完整降级链。
        val writeAttempts = intArrayOf(0)
        val realRenderer = TutorVisualPreviewRendererFactory.create(
            TutorVisualPreviewAssetPersister(
                persistPngBytes = { _, _, _ ->
                    writeAttempts[0] += 1
                    error("a refused preview must never reach the vault write port")
                },
                registerRow = { error("a refused preview must never register an asset row") },
            ),
        )
        val port = CapturingVisualPort()
        val repository = TutorVisualCommitRepository(
            database = port,
            freeSpaceBytes = { Long.MAX_VALUE },
            clock = { 9_000 },
        )
        val assembly = TutorVisualPreviewAssembly(realRenderer, TutorVisualPreviewInjection.ENABLED)

        val prepared = assembly.inject(
            request(viewportWidthDu = 4_096, viewportHeightDu = 4_096),
            snapshotInput(viewportWidthDu = 4_096, viewportHeightDu = 4_096),
        )

        assertNull("真实渲染器拒绝后必须降级为 null", prepared.snapshot.previewAssetId)
        assertTrue(
            "真实渲染器的拒绝码必须原样带出，got: ${prepared.diagnostic}",
            prepared.diagnostic!!.startsWith(VisualLayoutBudget.LAYOUT_PIXEL_BUDGET),
        )
        assertEquals("拒绝必须发生在任何 vault 写入口之前", 0, writeAttempts[0])

        val failure = runCatching {
            repository.commitAssistantTurnWithVisual(
                message = assistantCommand(),
                program = programInput(),
                snapshot = prepared.snapshot,
                ownerRequestId = "request-ws7-real-refusal",
            )
        }
        assertTrue(
            "the capturing port must stop the commit after recording: $failure",
            failure.exceptionOrNull() is StopAfterCapture,
        )
        assertNull(
            "端到端降级：提交命令里的 previewAssetId 必须是 null（快照照常提交）",
            port.committed!!.snapshot.previewAssetId,
        )
    }

    // ------------------------------------------------------------ 项 58：失败降级

    @Test
    fun `a refused preview degrades to a null id and never keeps a stale one`() = runBlocking {
        val assembly = TutorVisualPreviewAssembly(
            renderer = ScriptedRenderer {
                TutorVisualPreviewOutcome.Refused(
                    code = VisualLayoutBudget.LAYOUT_PIXEL_BUDGET,
                    diagnostic = "${VisualLayoutBudget.LAYOUT_PIXEL_BUDGET}: preview too large",
                )
            },
            injection = TutorVisualPreviewInjection.ENABLED,
        )

        val result = assembly.inject(request(), snapshotInput(previewAssetId = "stale-asset"))

        assertNull("拒绝必须显式置 null，不得沿用旧 id（显示缓存不能引用无关资产）", result.snapshot.previewAssetId)
        assertTrue(
            "诊断必须以拒绝码开头，got: ${result.diagnostic}",
            result.diagnostic!!.startsWith(VisualLayoutBudget.LAYOUT_PIXEL_BUDGET),
        )
    }

    @Test
    fun `a throwing renderer degrades to null instead of failing the commit`() = runBlocking {
        val assembly = TutorVisualPreviewAssembly(
            renderer = ScriptedRenderer { throw IllegalStateException("vault unavailable") },
            injection = TutorVisualPreviewInjection.ENABLED,
        )

        val result = assembly.inject(request(), snapshotInput())

        assertNull(result.snapshot.previewAssetId)
        assertTrue(
            "装配边界的兜底失败必须可分流，got: ${result.diagnostic}",
            result.diagnostic!!.startsWith(PREVIEW_INJECTION_FAILED),
        )
        assertTrue(
            "诊断必须有界（≤ ${FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS} 字符）",
            result.diagnostic!!.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS,
        )
    }

    @Test
    fun `an out-of-memory backstop degrades to null instead of killing the process`() = runBlocking {
        val assembly = TutorVisualPreviewAssembly(
            renderer = ScriptedRenderer { throw OutOfMemoryError("simulated allocation failure") },
            injection = TutorVisualPreviewInjection.ENABLED,
        )

        val result = assembly.inject(request(), snapshotInput())

        assertNull("OOM 兜底也必须折 previewAssetId=null（门在前、兜底在后）", result.snapshot.previewAssetId)
        assertTrue(
            "OOM 兜底必须带可分流码，got: ${result.diagnostic}",
            result.diagnostic!!.startsWith(PREVIEW_INJECTION_FAILED),
        )
    }

    @Test
    fun `cancellation is rethrown and never disguised as a preview refusal`() = runBlocking {
        val assembly = TutorVisualPreviewAssembly(
            renderer = ScriptedRenderer { throw CancellationException("cancelled by the caller") },
            injection = TutorVisualPreviewInjection.ENABLED,
        )

        val failure = runCatching { assembly.inject(request(), snapshotInput()) }

        assertTrue(
            "取消不是超限：必须原样重抛，不能折成 previewAssetId=null 的'预览不可用'",
            failure.exceptionOrNull() is CancellationException,
        )
    }

    @Test
    fun `an enabled preview may only use the snapshot's frozen frame`() = runBlocking {
        val renderer = ScriptedRenderer { TutorVisualPreviewOutcome.Accepted("never-reached") }
        val assembly = TutorVisualPreviewAssembly(renderer, TutorVisualPreviewInjection.ENABLED)

        val viewportFailure = runCatching {
            assembly.inject(request(viewportWidthDu = 361), snapshotInput())
        }
        val fontScaleFailure = runCatching {
            assembly.inject(request(fontScaleMilli = 1_500), snapshotInput())
        }

        assertTrue(
            "视口漂移必须被拒（显示缓存与快照同帧），got: $viewportFailure",
            viewportFailure.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            "fontScale 漂移必须被拒，got: $fontScaleFailure",
            fontScaleFailure.exceptionOrNull() is IllegalArgumentException,
        )
        assertEquals("帧校验必须先于渲染（零渲染调用）", 0, renderer.calls)
    }

    // ------------------------------------------------------------ 项 59：广告面零改动

    @Test
    fun `the preview switch never advertises JS or changes available methods`() {
        // 开关打开只是预览侧的显式态；生产能力常量与提示词广告面都不得随之变化。
        val assembly = TutorVisualPreviewAssembly(
            renderer = ScriptedRenderer { TutorVisualPreviewOutcome.Accepted("unused") },
            injection = TutorVisualPreviewInjection.ENABLED,
        )
        assertTrue(assembly.isEnabled)

        // 开关只有"关/开"两态：不得长出一个"顺带广告"的第三态（广告面唯一来源仍是能力声明）。
        assertEquals(
            setOf(TutorVisualPreviewInjection.DISABLED, TutorVisualPreviewInjection.ENABLED),
            TutorVisualPreviewInjection.entries.toSet(),
        )

        assertEquals(
            "生产能力常量必须仍是 IMAGE_MODEL_ONLY（开关不放广告面）",
            setOf(FigureGenerationMethod.IMAGE_MODEL),
            FigureGenerationCapabilities.IMAGE_MODEL_ONLY.availableMethods,
        )
        assertTrue(FigureGenerationCapabilities.IMAGE_MODEL_ONLY.imageModelOnly)

        val prompt = FigurePromptAssembler.assemble(
            purpose = FigureGenerationPurpose.EXPLANATION,
            capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
            budget = FigureGenerationBudget(),
        )
        assertEquals(listOf(FigureGenerationMethod.IMAGE_MODEL), prompt.availableMethods)
        assertFalse("开关打开不得让提示词广告 JS：${prompt.body}", prompt.body.contains("QuickJS"))
        assertFalse("开关打开不得注入 sourceJs：${prompt.body}", prompt.body.contains("sourceJs"))
    }

    // ------------------------------------------------------------ 端口/哨兵

    /** 记录 commit 命令后终止；让 JVM 端到端不需要伪造三张返回记录。 */
    private object StopAfterCapture : RuntimeException("stop after capturing the commit command")

    private class CapturingVisualPort : TutorVisualPort {
        var committed: CommitTutorTurnWithVisualCommand? = null
            private set

        override suspend fun commitTutorTurnWithVisual(
            command: CommitTutorTurnWithVisualCommand,
        ): TutorVisualTurnCommitRecord {
            committed = command
            throw StopAfterCapture
        }

        override suspend fun readVisualCheckpointSummaries(
            programId: String,
        ): List<TutorVisualCheckpointSummary> = emptyList()

        override suspend fun readGlobalVisualCheckpointStoredBytes(): Long = 0L
    }
}
