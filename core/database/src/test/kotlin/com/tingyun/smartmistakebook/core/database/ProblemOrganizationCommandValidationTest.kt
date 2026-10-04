package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.model.BindingAcceptanceSource
import com.tingyun.smartmistakebook.core.model.ClassificationDimension
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.SubjectKind
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 确认命令的纯函数不变量（`RoomProblemOrganizationStore.validateCommand`）。
 *
 * 消灭的失败：KF-07 删除 topic 兜底全绑后，无原子对的确认命令 `knowledgeBindings` 为空，而 DB 层
 * 曾要求"至少一条绑定"——组织写入被 `IllegalArgumentException` 整单拒掉。该约束只在真库/仪器化
 * 路径上存在，JVM 侧的 fake port 不跑 `validateCommand`，所以回归直到仪器化门才暴露；本测试直接
 * 对纯函数钉住"零绑定可接受 + 混绑仍拒绝"。
 */
class ProblemOrganizationCommandValidationTest {

    @Test
    fun zeroBindingCommandIsAcceptedAsHonestlyUnclassified() {
        // KF-07：没有可验证的原子节点 → 空绑定是合法形态；pseudo:<SUBJECT> 占位由下游
        // ensurePseudoKnowledgeBinding 物化，不进本命令。
        validateCommand(command(knowledgeBindings = emptyList()))
    }

    @Test
    fun groundedAtomicOnlyCommandWithoutVisibleTopicBindingIsAccepted() {
        // 正常路径之一：只绑 grounded 原子节点（可见 topic 节点照常物化但不被绑定）。
        validateCommand(
            command(
                knowledgeBindings = listOf(
                    binding(
                        bindingId = "knowledge-binding:atomic-1",
                        knowledgeNodeId = ATOMIC_NODE_ID,
                    ),
                ),
            ),
        )
    }

    @Test
    fun mixingVisibleTopicAndGroundedAtomicBindingsIsRejected() {
        val failure = runCatching {
            validateCommand(
                command(
                    knowledgeBindings = listOf(
                        binding(
                            bindingId = "knowledge-binding:topic",
                            knowledgeNodeId = TOPIC_NODE_ID,
                        ),
                        binding(
                            bindingId = "knowledge-binding:atomic-2",
                            knowledgeNodeId = ATOMIC_NODE_ID,
                        ),
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue("混绑必须是 IllegalArgumentException，实际：$failure", failure is IllegalArgumentException)
        assertTrue(
            "失败必须来自「可见 topic 与 grounded 原子混绑」这条检查，实际：${failure?.message}",
            failure?.message?.contains("mixture") == true,
        )
    }

    private fun command(
        knowledgeBindings: List<KnowledgeBindingSeedRecord>,
    ) = ConfirmProblemOrganizationCommand(
        commandId = "organization-apply:test",
        payloadFingerprint = "a".repeat(64),
        problemId = PROBLEM_ID,
        problemRevisionId = REVISION_ID,
        practiceUnitId = PRACTICE_UNIT_ID,
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = TOPIC_NODE_ID,
                stableCode = KNOWLEDGE_LABEL_ID,
                subject = SubjectKind.MATH.name,
                displayName = "二次函数最值",
                parentKnowledgeNodeId = null,
                taxonomyVersion = "organization-v1",
                createdAtEpochMillis = ACCEPTED_AT,
                canonicalName = "二次函数最值",
                nodeKind = KnowledgeNodeKind.TOPIC.name,
                granularity = KnowledgeNodeGranularity.TOPIC.name,
                verificationStatus = KnowledgeNodeVerificationStatus.MODEL_CANDIDATE.name,
            ),
        ),
        knowledgeBindings = knowledgeBindings,
        classifications = listOf(
            classification(
                bindingId = "classification:chapter",
                dimension = ClassificationDimension.CHAPTER,
                labelId = "math:chapter:functions",
                displayName = "函数",
            ),
            classification(
                bindingId = "classification:knowledge",
                dimension = ClassificationDimension.KNOWLEDGE,
                labelId = KNOWLEDGE_LABEL_ID,
                displayName = "二次函数最值",
            ),
        ),
        relations = emptyList(),
        acceptedAtEpochMillis = ACCEPTED_AT,
    )

    private fun classification(
        bindingId: String,
        dimension: ClassificationDimension,
        labelId: String,
        displayName: String,
    ) = ProblemClassificationBindingRecord(
        bindingId = bindingId,
        problemId = PROBLEM_ID,
        basisRevisionId = REVISION_ID,
        dimension = dimension.name,
        labelId = labelId,
        displayName = displayName,
        taxonomyVersion = "local-policy-v1",
        acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED.name,
        acceptedAtEpochMillis = ACCEPTED_AT,
    )

    private fun binding(
        bindingId: String,
        knowledgeNodeId: String,
    ) = KnowledgeBindingSeedRecord(
        bindingId = bindingId,
        practiceUnitId = PRACTICE_UNIT_ID,
        knowledgeNodeId = knowledgeNodeId,
        basisRevisionId = REVISION_ID,
        strength = 1.0,
        sourceType = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED.name,
        taxonomyVersion = "local-policy-v1",
        acceptedAtEpochMillis = ACCEPTED_AT,
    )

    private companion object {
        const val PROBLEM_ID = "problem-1"
        const val REVISION_ID = "revision-1"
        const val PRACTICE_UNIT_ID = "practice-1"
        const val TOPIC_NODE_ID = "knowledge:topic-function-extrema"
        const val ATOMIC_NODE_ID = "math-atomic-core-operation"
        const val KNOWLEDGE_LABEL_ID = "math:knowledge:function-extrema"
        const val ACCEPTED_AT = 2_000L
    }
}
