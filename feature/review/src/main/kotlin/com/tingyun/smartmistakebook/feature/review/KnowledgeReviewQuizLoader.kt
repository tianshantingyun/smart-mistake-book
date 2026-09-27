package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueEntry
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.TutorTeachingReferenceRepository
import com.tingyun.smartmistakebook.core.domain.isReady
import com.tingyun.smartmistakebook.core.model.KnowledgeQuizOutput
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.toTutorAssessmentItem
import kotlinx.coroutines.flow.StateFlow

/**
 * 一次取题的结果（D-Q3：首装后台化）。
 *
 * 改前这里只有一个 `null`：provider 没有、材料没有、内容还在准备、模型这一步没成功——
 * 四种原因挤成同一个"出题失败"，学生看到的是同一句"暂时没有生成出来"，无从判断是
 * 该等一会、还是该跳过这道。四个分支各自对应一条**不同的话与出路**：
 * - [Ready]：可以作答；
 * - [KnowledgePreparing]：知识内容还在后台就位，等一会重试就好；
 * - [NoMaterial]：这个知识点真的没有讲解材料（合法空集），重试没意义，跳过它；
 * - [Unavailable]：其余不可用（模型未配置/不支持、请求构造不出、任务没成功）。
 */
sealed interface KnowledgeQuizLoadResult {
    data class Ready(val item: TutorAssessmentItem) : KnowledgeQuizLoadResult
    data object KnowledgePreparing : KnowledgeQuizLoadResult
    data object NoMaterial : KnowledgeQuizLoadResult
    data object Unavailable : KnowledgeQuizLoadResult
}

/**
 * 为知识点复习计划中的一个队列项现场出题（spec dual-review-entry §3.3/§3.4）。
 *
 * 一次出题 = 解析该知识点讲解材料 → 构造 KNOWLEDGE_QUIZ 请求（带 egress least-disclosure
 * manifest）→ 跑模型任务到终态 → 成功时把输出映射成可判答的 [TutorAssessmentItem]。
 * 任何一步不可用都返回对应的 [KnowledgeQuizLoadResult]（不再是一个没有话可说的 null）。
 *
 * 消灭的失败：知识点复习的"取题"逻辑散在 Composable 无法测试、各节点材料解析标准不一；
 * 以及 D-Q3 点名的"知识内容还在准备"被说成"没有材料"。
 */
class KnowledgeReviewQuizLoader(
    private val modelTasks: ModelTaskRepository,
    private val references: TutorTeachingReferenceRepository,
    /**
     * 知识能力就绪位（D-Q3），必需参数：没有它，"内容还没就位"就会退回成
     * "这个知识点没有讲解材料"的假话。
     */
    private val knowledgeBaseAvailability: StateFlow<KnowledgeBaseAvailability>,
) {
    suspend fun loadQuiz(entry: KnowledgeReviewQueueEntry): KnowledgeQuizLoadResult {
        if (!knowledgeBaseAvailability.value.isReady) {
            return KnowledgeQuizLoadResult.KnowledgePreparing
        }
        val provider = runCatching { modelTasks.capabilities() }.getOrNull()
            ?: return KnowledgeQuizLoadResult.Unavailable
        if (!provider.supports(com.tingyun.smartmistakebook.core.model.ModelTaskKind.KNOWLEDGE_QUIZ)) {
            return KnowledgeQuizLoadResult.Unavailable
        }
        val teachingReferences = references.referencesFor(
            subject = entry.subject,
            knowledgeNodeIds = setOf(entry.knowledgeNodeId),
        )
        val material = teachingReferences.firstOrNull { reference ->
            entry.knowledgeNodeId in reference.knowledgeNodeIds
        } ?: return KnowledgeQuizLoadResult.NoMaterial
        if (provider.executionLocation == ModelExecutionLocation.UNAVAILABLE) {
            return KnowledgeQuizLoadResult.Unavailable
        }

        val requestId = "knowledge-quiz:review:${entry.knowledgeNodeId}:${java.util.UUID.randomUUID()}"
        val occurredAt = System.currentTimeMillis()
        val request = buildKnowledgeQuizRequest(
            provider = provider,
            requestId = requestId,
            entry = entry,
            reference = material,
            occurredAtEpochMillis = occurredAt,
        ) ?: return KnowledgeQuizLoadResult.Unavailable

        var succeededOutput: KnowledgeQuizOutput? = null
        modelTasks.execute(request).collect { snapshot ->
            if (snapshot.status == ModelTaskStatus.SUCCEEDED) {
                succeededOutput = snapshot.output as? KnowledgeQuizOutput
            }
        }
        val output = succeededOutput ?: return KnowledgeQuizLoadResult.Unavailable
        return KnowledgeQuizLoadResult.Ready(
            output.toTutorAssessmentItem(
                knowledgeNodeId = entry.knowledgeNodeId,
                itemId = "knowledge-quiz:item:${entry.knowledgeNodeId}",
            ),
        )
    }
}
