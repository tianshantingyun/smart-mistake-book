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

    /**
     * KF-30：`accepted_confidence` 的静态不变量——NULL（未知）与 0..1 的真值合法；
     * 越界/非有限值（写入侧手改或坏数据）必须在落库前被拒。
     */
    @Test
    fun anOutOfRangeAcceptedConfidenceIsRejectedWhileUnknownAndValidScoresPass() {
        fun withConfidence(confidence: Double?): ConfirmProblemOrganizationCommand {
            val base = command(knowledgeBindings = emptyList())
            return base.copy(
                classifications = base.classifications.map {
                    it.copy(acceptedConfidence = confidence)
                },
            )
        }

        validateCommand(withConfidence(null))
        validateCommand(withConfidence(0.0))
        validateCommand(withConfidence(1.0))

        listOf(1.5, -0.01, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            val failure = runCatching { validateCommand(withConfidence(invalid)) }.exceptionOrNull()
            assertTrue(
                "非法置信度 $invalid 必须被拒，实际：$failure",
                failure is IllegalArgumentException,
            )
        }
    }

    /**
     * K5-B（schema 65）：候选集的静态不变量——完整、合法、同轮、同权威；**被拒候选合法**
     * （`accepted = false` 的行恰恰是本项要落的），但 `accepted = true` 的候选必须指向本命令
     * 真实写入的标签——否则"被接受"的标记从源头就是假的（校准审计的分子会混进没落地的标签）。
     */
    @Test
    fun classificationCandidatesMustBeCompleteAndAcceptedOnesMustPointAtWrittenLabels() {
        fun withCandidates(
            candidates: List<ProblemClassificationCandidateRecord>,
        ): ConfirmProblemOrganizationCommand =
            command(knowledgeBindings = emptyList()).copy(classificationCandidates = candidates)

        val rejected = candidate(
            candidateId = "classification-candidate:rejected",
            dimension = ClassificationDimension.CHAPTER,
            labelId = "math:chapter:geometry",
            displayName = "几何",
            confidence = 0.5,
            accepted = false,
        )
        val accepted = candidate(
            candidateId = "classification-candidate:accepted",
            dimension = ClassificationDimension.CHAPTER,
            labelId = "math:chapter:functions",
            displayName = "函数",
            confidence = 0.9,
            accepted = true,
        )
        // 被拒候选合法（含低于接受门的分数）；被接受候选必须命中本命令写入的标签。
        validateCommand(withCandidates(listOf(rejected, accepted)))
        validateCommand(withCandidates(emptyList()))

        val wrongLabel = accepted.copy(
            candidateId = "classification-candidate:ghost",
            labelId = "math:chapter:ghost",
        )
        assertRejected(
            withCandidates(listOf(wrongLabel)),
            "accepted 标记不许指向没写进本命令的标签",
        )
        assertRejected(
            withCandidates(listOf(rejected.copy(candidateId = "  "))),
            "候选 id 不许空白",
        )
        assertRejected(
            withCandidates(listOf(rejected.copy(confidence = 1.5))),
            "候选分数必须是概率",
        )
        assertRejected(
            withCandidates(listOf(rejected.copy(basisRevisionId = "revision-other"))),
            "候选必须属于本次确认的修订",
        )
        assertRejected(
            withCandidates(listOf(rejected.copy(acceptedAtEpochMillis = ACCEPTED_AT + 1))),
            "候选必须属于本轮（acceptedAt 一致）",
        )
        assertRejected(
            withCandidates(
                listOf(rejected, rejected.copy(acceptanceSource = "USER_CORRECTED")),
            ),
            "同一命令的候选必须共享本轮接受权威",
        )
    }

    private fun assertRejected(command: ConfirmProblemOrganizationCommand, why: String) {
        val failure = runCatching { validateCommand(command) }.exceptionOrNull()
        assertTrue("$why，实际：$failure", failure is IllegalArgumentException)
    }

    private fun candidate(
        candidateId: String,
        dimension: ClassificationDimension,
        labelId: String,
        displayName: String,
        confidence: Double,
        accepted: Boolean,
    ) = ProblemClassificationCandidateRecord(
        candidateId = candidateId,
        problemId = PROBLEM_ID,
        basisRevisionId = REVISION_ID,
        dimension = dimension.name,
        labelId = labelId,
        displayName = displayName,
        confidence = confidence,
        accepted = accepted,
        acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED.name,
        acceptedAtEpochMillis = ACCEPTED_AT,
    )

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
