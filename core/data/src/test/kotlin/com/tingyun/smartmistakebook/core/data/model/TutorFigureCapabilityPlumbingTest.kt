package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.study.FakeStudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.ModelGateway
import com.tingyun.smartmistakebook.core.domain.TUTOR_TOOL_DECLARATIONS
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelGatewayEvent
import com.tingyun.smartmistakebook.core.model.ModelGatewayExecution
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.withFigureGenerationCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JS 接入智能体的**生产链路**回归：coordinator 报出的真实生图能力必须能到达
 * `input.figureGenerationCapabilities`，否则工具 schema 的 `method.enum`、提示词方法段、
 * 轮次层与执行器的 JS 就绪门全部退回 IMAGE_MODEL——引擎明明在，模型却调不到。
 *
 * 本用例钉住两件事实：
 * 1. 折叠前（生产旧行为）JS 不在工具广告面；
 * 2. 折叠后 GENERATE_FIGURE 进广告面，wire schema 同时出现 `"JS"` 枚举与独立 `"program"` 字段。
 */
class TutorFigureCapabilityPlumbingTest {

    private val jsOnly = FigureGenerationCapabilities(
        availableMethods = setOf(FigureGenerationMethod.JS),
    )

    @Test
    fun foldedJsCapabilitiesPutGenerateFigureOnTheToolSurface() {
        val repository = repository(figureCapabilities = jsOnly)
        val provider = provider(figureCapabilities = jsOnly)

        val unfolded = planInput()
        assertFalse(
            "没有折叠能力快照时不得凭空广告 JS",
            TutorToolName.GENERATE_FIGURE in repository.toolDeclarationsFor(unfolded, provider),
        )

        val folded = unfolded.withFigureGenerationCapabilities(jsOnly)
        assertTrue(
            "折叠 coordinator 的 JS 能力后 GENERATE_FIGURE 必须进入广告面",
            TutorToolName.GENERATE_FIGURE in repository.toolDeclarationsFor(folded, provider),
        )
    }

    @Test
    fun foldedJsCapabilitiesReachTheWireSchemaAsMethodAndProgram() {
        val repository = repository(figureCapabilities = jsOnly)
        val provider = provider(figureCapabilities = jsOnly)
        val folded = planInput().withFigureGenerationCapabilities(jsOnly)
        val declared = repository.toolDeclarationsFor(folded, provider)
        assertTrue(TutorToolName.GENERATE_FIGURE in declared)

        val roundInput = (folded as TutorPlanInput).copy(toolDeclarations = declared.toList())
        val body = OpenAiModelProtocol.requestBody(
            modelId = "deepseek-v4-flash",
            input = roundInput,
            images = emptyList(),
            enableNativeTools = true,
        )

        assertTrue(
            "JS 能力折叠后 wire schema 必须广告 method=JS",
            body.contains("\"JS\""),
        )
        assertTrue(
            "JS 能力折叠后 wire schema 必须包含独立 program 字段",
            body.contains("\"program\""),
        )
        assertTrue(
            "JS 能力折叠后提示词必须带上结构化程序契约",
            body.contains("结构化程序"),
        )
    }

    @Test
    fun imageOnlyCapabilitiesKeepThePreviousWireSchema() {
        val imageOnly = FigureGenerationCapabilities.IMAGE_MODEL_ONLY
        val repository = repository(figureCapabilities = imageOnly)
        val provider = provider(figureCapabilities = imageOnly)
        val folded = planInput().withFigureGenerationCapabilities(imageOnly)
        val declared = repository.toolDeclarationsFor(folded, provider)

        val roundInput = (folded as TutorPlanInput).copy(toolDeclarations = declared.toList())
        val body = OpenAiModelProtocol.requestBody(
            modelId = "deepseek-v4-flash",
            input = roundInput,
            images = emptyList(),
            enableNativeTools = true,
        )

        assertTrue(body.contains("\"IMAGE_MODEL\""))
        assertFalse("只有图像模型时不得广告 JS", body.contains("\"JS\""))
        assertFalse("只有图像模型时不得出现 program 字段", body.contains("\"program\""))
    }

    @Test
    fun foldedLocalCapabilitiesDoNotCountAsRemoteIdentityDrift() {
        // 派遣前折入本机生图能力后，网关 Started 事件仍只带远程快照；身份比较必须忽略
        // 本地能力列，否则每一条讲题都会在开始后立刻被判成"模型身份变化"。
        val remote = provider(figureCapabilities = null)
        val folded = remote.copy(figureGenerationCapabilities = jsOnly)
        assertTrue(folded.sameRemoteIdentityAs(remote))
        assertTrue(remote.sameRemoteIdentityAs(folded))

        val anotherModel = folded.copy(modelId = "another-model")
        assertFalse(folded.sameRemoteIdentityAs(anotherModel))
    }

    private fun repository(
        figureCapabilities: FigureGenerationCapabilities,
    ): RoomModelTaskRepository = RoomModelTaskRepository(
        database = FakeStudyDatabasePort(),
        gateway = object : ModelGateway {
            override suspend fun capabilities(): ProviderCapabilitySnapshot = provider(figureCapabilities)

            override fun execute(execution: ModelGatewayExecution): Flow<ModelGatewayEvent> =
                emptyFlow()
        },
        knowledgeBaseAvailability = MutableStateFlow(KnowledgeBaseAvailability.Ready),
        figureGenerator = object : TutorFigureGenerator {
            override suspend fun generate(request: TutorFigureRequest): TutorFigureResult =
                error("tool-surface tests never execute a figure")

            override fun supports(method: FigureGenerationMethod): Boolean = true
        },
        figureCapabilities = { figureCapabilities },
    )

    private fun provider(
        figureCapabilities: FigureGenerationCapabilities?,
    ): ProviderCapabilitySnapshot = ProviderCapabilitySnapshot(
        providerId = "test-provider",
        providerDisplayName = "Test Provider",
        modelId = "deepseek-v4-flash",
        supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN),
        // 非视觉模型：旧行为下这里会让 GENERATE_FIGURE 整体消失，JS 折叠是唯一入口。
        supportsImageInput = false,
        supportsStructuredOutput = true,
        supportsStreaming = true,
        executionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
        figureGenerationCapabilities = figureCapabilities,
    )

    private fun planInput(): TutorPlanInput = TutorPlanInput(
        sessionId = "session-1",
        draftRevisionNumber = 1,
        subject = "数学",
        questionDocument = QuestionDocument(
            id = "doc-1",
            title = "测试题目",
            blocks = listOf(ContentBlock.Paragraph(id = "p-1", markdown = "1 + 1 = ?")),
        ),
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
        reviewedTeachingReferences = emptyList(),
        questionLearningEvidence = null,
        cycleOrdinal = 1,
        priorConversationMemory = null,
        priorCycleStudentMessages = emptyList(),
        turnOrdinal = 1,
        priorTurns = emptyList(),
        toolDeclarations = TUTOR_TOOL_DECLARATIONS.toList(),
    )
}
