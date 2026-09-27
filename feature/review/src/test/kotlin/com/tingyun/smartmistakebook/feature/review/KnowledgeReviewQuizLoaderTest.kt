package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueEntry
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.TutorTeachingReferenceRepository
import com.tingyun.smartmistakebook.core.model.KnowledgeQuizChoice
import com.tingyun.smartmistakebook.core.model.KnowledgeQuizOutput
import com.tingyun.smartmistakebook.core.model.KnowledgeTeachingMaterialType
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.TutorTeachingReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnowledgeReviewQuizLoaderTest {

    private val entry = KnowledgeReviewQueueEntry(
        knowledgeNodeId = "kc-monotonicity",
        subject = "MATH",
        displayName = "函数单调性",
        masteryScore = null,
        lastEvidenceAtEpochMillis = null,
    )

    private fun material() = TutorTeachingReference(
        materialId = "material:monotonicity",
        subject = "MATH",
        materialType = KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION,
        title = "函数单调性讲解",
        summaryMarkdown = "概要",
        applicabilityMarkdown = "适用",
        contentMarkdown = "单调性判定正文",
        boundaryMarkdown = "只覆盖单调性判定",
        knowledgeNodeIds = listOf("kc-monotonicity"),
    )

    private fun externalProvider() = ProviderCapabilitySnapshot(
        providerId = "provider:test",
        providerDisplayName = "测试模型",
        modelId = "model:test",
        supportedTasks = setOf(ModelTaskKind.KNOWLEDGE_QUIZ),
        supportsImageInput = false,
        supportsStructuredOutput = true,
        supportsStreaming = true,
        executionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
        providerConfigurationVersion = "cfg-v1",
    )

    private fun quizOutput() = KnowledgeQuizOutput(
        questionMarkdown = "函数单调性的判定？",
        choices = listOf(
            KnowledgeQuizChoice("A", "看导数符号"),
            KnowledgeQuizChoice("B", "看函数值大小"),
        ),
        correctChoiceId = "A",
        modelVersion = "fake/quiz-v1",
    )

    private class LoaderFakeModelTasks(
        private val provider: ProviderCapabilitySnapshot,
        private val output: KnowledgeQuizOutput?,
    ) : ModelTaskRepository {
        var executedRequest: ModelTaskRequest? = null
            private set

        override suspend fun capabilities(): ProviderCapabilitySnapshot = provider

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> {
            executedRequest = request
            val succeeded = output != null
            if (!succeeded) return flowOf(
                snapshot(request, ModelTaskStatus.PERMANENT_FAILURE, output = null),
            )
            return flowOf(
                snapshot(request, ModelTaskStatus.SUCCEEDED, output = output),
            )
        }

        private fun snapshot(
            request: ModelTaskRequest,
            status: ModelTaskStatus,
            output: KnowledgeQuizOutput?,
        ) = ModelTaskSnapshot(
            taskId = "task-${request.requestId}",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = status,
            stateVersion = 3,
            stage = ModelTaskStage.COMPLETE,
            userMessage = if (status == ModelTaskStatus.SUCCEEDED) "已完成" else "失败",
            attemptCount = 1,
            output = output,
            failure = if (status == ModelTaskStatus.SUCCEEDED) null else {
                com.tingyun.smartmistakebook.core.model.ModelTaskFailure(
                    code = com.tingyun.smartmistakebook.core.model.ModelFailureCode.UNKNOWN,
                    message = "模拟失败",
                    retryable = false,
                )
            },
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 200,
        )
    }

    private class LoaderFakeReferences(
        private val references: List<TutorTeachingReference>,
    ) : TutorTeachingReferenceRepository {
        override suspend fun referencesFor(
            subject: String,
            knowledgeNodeIds: Set<String>,
        ): List<TutorTeachingReference> =
            references.filter { it.subject == subject && it.knowledgeNodeIds.any(knowledgeNodeIds::contains) }
    }

    @Test
    fun resolvedMaterialLeadsToASucceededAssessmentItem() = runTest {
        val modelTasks = LoaderFakeModelTasks(externalProvider(), quizOutput())
        val loader = KnowledgeReviewQuizLoader(
            modelTasks = modelTasks,
            references = LoaderFakeReferences(listOf(material())),
            knowledgeBaseAvailability = readyAvailability(),
        )

        val loaded = loader.loadQuiz(entry)
        val item = (loaded as KnowledgeQuizLoadResult.Ready).item

        assertEquals("kc-monotonicity", item?.knowledgeNodeIds?.single())
        assertEquals("A", item?.correctChoiceId)
        // 请求确实带上了该节点的材料。
        val input = modelTasks.executedRequest?.input as com.tingyun.smartmistakebook.core.model.KnowledgeQuizInput
        assertEquals("函数单调性讲解", input.materialTitle)
    }

    /**
     * 消灭的失败（D-Q3，消费点④）：改前所有原因都挤成一个 null——学生只看到"这道知识点
     * 暂时没有生成出来"，分不清"没有材料"和"内容还没准备好"。现在各归各的因。
     */
    @Test
    fun noMaterialForTheNodeIsReportedAsNoMaterial() = runTest {
        val loader = KnowledgeReviewQuizLoader(
            modelTasks = LoaderFakeModelTasks(externalProvider(), quizOutput()),
            references = LoaderFakeReferences(emptyList()),
            knowledgeBaseAvailability = readyAvailability(),
        )
        assertEquals(KnowledgeQuizLoadResult.NoMaterial, loader.loadQuiz(entry))
    }

    @Test
    fun knowledgeBasePreparingIsNotReportedAsMissingMaterial() = runTest {
        val modelTasks = LoaderFakeModelTasks(externalProvider(), quizOutput())
        val loader = KnowledgeReviewQuizLoader(
            modelTasks = modelTasks,
            references = LoaderFakeReferences(listOf(material())),
            knowledgeBaseAvailability = MutableStateFlow(KnowledgeBaseAvailability.Preparing),
        )

        assertEquals(KnowledgeQuizLoadResult.KnowledgePreparing, loader.loadQuiz(entry))
        // 连模型都没问：内容没就位时不该派发一个注定没有材料的请求。
        assertNull("未就绪不得发起任务", modelTasks.executedRequest)
    }

    @Test
    fun knowledgeBaseUnavailableIsAlsoReportedAsPreparing() = runTest {
        val loader = KnowledgeReviewQuizLoader(
            modelTasks = LoaderFakeModelTasks(externalProvider(), quizOutput()),
            references = LoaderFakeReferences(listOf(material())),
            knowledgeBaseAvailability = MutableStateFlow(
                KnowledgeBaseAvailability.Unavailable("startup:knowledge:1"),
            ),
        )

        assertEquals(KnowledgeQuizLoadResult.KnowledgePreparing, loader.loadQuiz(entry))
    }

    @Test
    fun providerWithoutQuizSupportIsReportedAsUnavailable() = runTest {
        val provider = externalProvider().copy(supportedTasks = emptySet())
        val loader = KnowledgeReviewQuizLoader(
            modelTasks = LoaderFakeModelTasks(provider, quizOutput()),
            references = LoaderFakeReferences(listOf(material())),
            knowledgeBaseAvailability = readyAvailability(),
        )
        assertEquals(KnowledgeQuizLoadResult.Unavailable, loader.loadQuiz(entry))
    }

    @Test
    fun failedModelTaskIsReportedAsUnavailable() = runTest {
        val loader = KnowledgeReviewQuizLoader(
            modelTasks = LoaderFakeModelTasks(externalProvider(), output = null),
            references = LoaderFakeReferences(listOf(material())),
            knowledgeBaseAvailability = readyAvailability(),
        )
        assertEquals(KnowledgeQuizLoadResult.Unavailable, loader.loadQuiz(entry))
    }

    private fun readyAvailability() =
        MutableStateFlow(KnowledgeBaseAvailability.Ready)
}
