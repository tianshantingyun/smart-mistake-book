package com.tingyun.smartmistakebook.core.data.mistake

import com.tingyun.smartmistakebook.core.database.ConfirmProblemOrganizationCommand
import com.tingyun.smartmistakebook.core.database.RecordBindingAuditSampleCommand
import com.tingyun.smartmistakebook.core.model.AtomicKnowledgeSuggestion
import com.tingyun.smartmistakebook.core.model.BindingAuditSampling
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshot
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshotBinding
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshotClassification
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshotCodec
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshotStep
import com.tingyun.smartmistakebook.core.model.ProblemClassificationSuggestion
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationInput
import com.tingyun.smartmistakebook.core.model.ProblemStepKnowledgeAttribution
import com.tingyun.smartmistakebook.core.model.QuestionDocumentMarkdownProjection
import java.util.Locale

/** 快照里步骤证据的条数上限（有界审计辅助，不是完整归档）。 */
private const val MAX_BINDING_AUDIT_STEP_EVIDENCE = 24

/**
 * KF-29：从一次已确认的组织写入构造抽样命令（纯函数，JVM 可测）。
 *
 * **抽样规则**：每科每周**首 N 条新绑定**（默认 5，`BindingAuditSampling`）——本函数不做配额
 * 判定，只构造内容寻址的样本命令；配额计数与插入在同一写事务里（`BindingAuditSampleDao.record`），
 * 并发下恰好封顶。
 *
 * 返回 null 的两种情形都**不产生抽样**且都不算错误：
 * - 本次确认没有写任何知识点绑定（例如被用户纠正保护挡下的自动重跑）；
 * - 命令里没有分类行（拿不到权威来源），此时没有可审计的绑定语义。
 *
 * 快照里的绑定点名优先取模型当时的候选菜单（[ProblemOrganizationInput.knowledgeBaseNodes]），
 * 其次是本次新建的 topic 节点；两处都没有时**回退为节点 id**——复核屏宁可显示 id，也不许
 * 编造一个名字。
 */
internal fun buildBindingAuditSampleCommand(
    input: ProblemOrganizationInput,
    command: ConfirmProblemOrganizationCommand,
    modelVersion: String?,
    providerId: String?,
    classifications: List<ProblemClassificationSuggestion>,
    atomicKnowledge: List<AtomicKnowledgeSuggestion>,
    stepAttributions: List<ProblemStepKnowledgeAttribution>,
    sampledAtEpochMillis: Long,
): RecordBindingAuditSampleCommand? {
    if (command.knowledgeBindings.isEmpty()) return null
    val acceptanceSource = command.classifications.firstOrNull()?.acceptanceSource ?: return null
    val quotaPrefix = BindingAuditSampling.quotaPrefix(input.subject.name, sampledAtEpochMillis)
    val sampleId = BindingAuditSampling.sampleId(
        quotaPrefix = quotaPrefix,
        practiceUnitId = input.practiceUnitId,
        problemRevisionId = input.problemRevisionId,
        bindingIds = command.knowledgeBindings.map { binding -> binding.bindingId },
    )
    val nodeContexts = input.knowledgeBaseNodes.associateBy { context -> context.knowledgeNodeId }
    val topicNodes = command.knowledgeNodes.associateBy { node -> node.knowledgeNodeId }
    val rationaleByClassification = classifications.associateBy { suggestion ->
        suggestion.dimension.name to suggestion.displayName.normalizedAuditName()
    }
    val atomByReferenceId = atomicKnowledge.associateBy { atom -> atom.referenceId }
    val boundNodeIds = command.knowledgeBindings.mapTo(linkedSetOf()) { binding ->
        binding.knowledgeNodeId
    }
    val projectedQuestion = QuestionDocumentMarkdownProjection.project(input.questionDocument)
    val questionTruncated = projectedQuestion.length > BindingAuditSnapshotCodec.MAX_QUESTION_CHARS
    val snapshot = BindingAuditSnapshot(
        subject = input.subject.name,
        modelVersion = modelVersion,
        providerId = providerId,
        acceptanceSource = acceptanceSource,
        acceptedAtEpochMillis = sampledAtEpochMillis,
        problemId = input.problemId,
        problemRevisionId = input.problemRevisionId,
        practiceUnitId = input.practiceUnitId,
        questionMarkdown = if (questionTruncated) {
            projectedQuestion.take(BindingAuditSnapshotCodec.MAX_QUESTION_CHARS)
        } else {
            projectedQuestion
        },
        questionTruncated = questionTruncated,
        bindings = command.knowledgeBindings.map { binding ->
            val context = nodeContexts[binding.knowledgeNodeId]
            val topic = topicNodes[binding.knowledgeNodeId]
            BindingAuditSnapshotBinding(
                knowledgeNodeId = binding.knowledgeNodeId,
                displayName = context?.canonicalName
                    ?: topic?.displayName
                    ?: binding.knowledgeNodeId,
                granularity = context?.granularity?.name ?: topic?.granularity ?: "TOPIC",
                strength = binding.strength,
                taxonomyVersion = binding.taxonomyVersion,
            )
        },
        classifications = command.classifications.map { record ->
            val suggestion = rationaleByClassification[
                record.dimension to record.displayName.normalizedAuditName()
            ]
            BindingAuditSnapshotClassification(
                dimension = record.dimension,
                labelId = record.labelId,
                displayName = record.displayName,
                rationaleMarkdown = suggestion?.rationaleMarkdown,
            )
        },
        stepEvidence = stepAttributions.mapNotNull { step ->
            val nodeIds = step.atomicReferenceIds
                .mapNotNull { referenceId -> atomByReferenceId[referenceId]?.matchedKnowledgeNodeId }
                .filter { nodeId -> nodeId in boundNodeIds }
                .distinct()
            if (nodeIds.isEmpty()) {
                null
            } else {
                BindingAuditSnapshotStep(
                    stepOrdinal = step.stepOrdinal,
                    stepSummaryMarkdown = step.stepSummaryMarkdown,
                    knowledgeNodeIds = nodeIds,
                )
            }
        }.take(MAX_BINDING_AUDIT_STEP_EVIDENCE),
    )
    return RecordBindingAuditSampleCommand(
        sampleId = sampleId,
        quotaPrefix = quotaPrefix,
        practiceUnitId = input.practiceUnitId,
        bindingSnapshotJson = BindingAuditSnapshotCodec.encode(snapshot),
    )
}

/** 与 `mergeAutomaticClassifications` 同一套分类名归一（空白折叠 + 小写）。 */
private fun String.normalizedAuditName(): String =
    trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
