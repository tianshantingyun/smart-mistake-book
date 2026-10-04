package com.tingyun.smartmistakebook.core.data.mistake

import com.tingyun.smartmistakebook.core.database.ConfirmProblemOrganizationCommand
import com.tingyun.smartmistakebook.core.database.KnowledgeBindingSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemClassificationBindingRecord
import com.tingyun.smartmistakebook.core.model.AtomicKnowledgeSuggestion
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshotCodec
import com.tingyun.smartmistakebook.core.model.ClassificationDimension
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.KnowledgeBaseNodeContext
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.ProblemClassificationSuggestion
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationInput
import com.tingyun.smartmistakebook.core.model.ProblemStepKnowledgeAttribution
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.SubjectKind
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KF-29 抽样快照构造的 JVM 钉死：绑定点名/理由/步骤证据进快照，题面超预算时如实截断，
 * 没有绑定就没有样本。
 *
 * 消灭的失败：复核屏只能看到节点 id 和最终绑定行——"当时模型给的理由、步骤依据、用的是哪个
 * 模型版本"无从复核；题面被截断却不标记，审阅者以为看到了全文。
 */
class BindingAuditSampleBuilderTest {

    @Test
    fun `snapshot carries bindings rationale evidence and model version`() {
        val command = buildBindingAuditSampleCommand(
            input = input(),
            command = command(),
            modelVersion = "model-x-1",
            providerId = "provider:test",
            classifications = listOf(
                suggestion(ClassificationDimension.CHAPTER, "函数", "章节层级匹配。"),
                suggestion(ClassificationDimension.KNOWLEDGE, "函数最值", "考点是函数最值。"),
            ),
            atomicKnowledge = listOf(atom()),
            stepAttributions = listOf(
                ProblemStepKnowledgeAttribution(
                    stepOrdinal = 1,
                    stepSummaryMarkdown = "比较端点与驻点。",
                    atomicReferenceIds = listOf("atom-1"),
                ),
            ),
            sampledAtEpochMillis = utcMillis("2026-10-04"),
        )

        assertNotNull("有绑定时必须构造出抽样命令", command)
        val record = command!!
        assertEquals(PRACTICE, record.practiceUnitId)
        assertTrue(
            "样本 id 必须带配额前缀（每科每周首 N 条的计数依据）",
            record.sampleId.startsWith("${record.quotaPrefix}:"),
        )
        assertTrue("配额键含科与 ISO 周", record.quotaPrefix == "binding-audit:MATH:2026-W40")

        val snapshot = BindingAuditSnapshotCodec.decode(record.bindingSnapshotJson)!!
        assertEquals("MATH", snapshot.subject)
        assertEquals("model-x-1", snapshot.modelVersion)
        assertEquals("provider:test", snapshot.providerId)
        assertEquals("LOCAL_POLICY_ACCEPTED", snapshot.acceptanceSource)
        assertEquals(utcMillis("2026-10-04"), snapshot.acceptedAtEpochMillis)
        assertEquals("题面必须进快照（原文依据）", "题面正文", snapshot.questionMarkdown)
        assertFalse(snapshot.questionTruncated)
        val binding = snapshot.bindings.single()
        assertEquals(ATOMIC_NODE, binding.knowledgeNodeId)
        assertEquals("绑定点用候选菜单里的可读名，而不是节点 id", "确定函数最值", binding.displayName)
        assertEquals("ATOMIC", binding.granularity)
        assertEquals(0.75, binding.strength, 0.0)
        assertEquals(
            "分类理由必须进快照",
            "考点是函数最值。",
            snapshot.classifications.single { it.dimension == "KNOWLEDGE" }.rationaleMarkdown,
        )
        assertEquals(
            "步骤证据必须挂到已绑定节点",
            listOf(ATOMIC_NODE),
            snapshot.stepEvidence.single().knowledgeNodeIds,
        )
    }

    @Test
    fun `a confirm without bindings produces no sample`() {
        assertNull(
            "没有写入任何绑定的确认（例如被用户纠正保护的自动重跑）不该产生抽样",
            buildBindingAuditSampleCommand(
                input = input(),
                command = command().copy(knowledgeBindings = emptyList()),
                modelVersion = null,
                providerId = null,
                classifications = emptyList(),
                atomicKnowledge = emptyList(),
                stepAttributions = emptyList(),
                sampledAtEpochMillis = utcMillis("2026-10-04"),
            ),
        )
    }

    @Test
    fun `an over budget question is truncated and flagged`() {
        val longQuestion = "长".repeat(BindingAuditSnapshotCodec.MAX_QUESTION_CHARS + 500)
        val command = buildBindingAuditSampleCommand(
            input = input().copy(questionDocument = question(longQuestion)),
            command = command(),
            modelVersion = null,
            providerId = null,
            classifications = listOf(suggestion(ClassificationDimension.KNOWLEDGE, "函数最值", "理由。")),
            atomicKnowledge = listOf(atom()),
            stepAttributions = emptyList(),
            sampledAtEpochMillis = utcMillis("2026-10-04"),
        )!!

        val snapshot = BindingAuditSnapshotCodec.decode(command.bindingSnapshotJson)!!
        assertTrue("题面超预算必须标记截断（不许让审阅者以为看到了全文）", snapshot.questionTruncated)
        assertEquals(
            BindingAuditSnapshotCodec.MAX_QUESTION_CHARS,
            snapshot.questionMarkdown.length,
        )
    }

    @Test
    fun `binding names fall back to node ids instead of inventing names`() {
        val command = buildBindingAuditSampleCommand(
            input = input().copy(knowledgeBaseNodes = emptyList()),
            command = command().copy(knowledgeNodes = emptyList()),
            modelVersion = null,
            providerId = null,
            classifications = emptyList(),
            atomicKnowledge = emptyList(),
            stepAttributions = emptyList(),
            sampledAtEpochMillis = utcMillis("2026-10-04"),
        )!!

        val snapshot = BindingAuditSnapshotCodec.decode(command.bindingSnapshotJson)!!
        assertEquals(
            "两处都没有名字时回退为节点 id（不许编造）",
            ATOMIC_NODE,
            snapshot.bindings.single().displayName,
        )
    }

    /**
     * F4 修复轮：**构造期失败**（如快照超预算导致 encode 抛错）必须被吞掉且不落样本——
     * 绑定写入已经成功，抽样不许把它谎报成失败。这里直接注入"构造即抛"的样本源，
     * 钉住仓库实际走的那条 best-effort 分支。
     */
    @Test
    fun `a construction failure is swallowed and leaves no sample`() = runBlocking {
        var recorded = false
        recordBindingAuditSampleBestEffort(
            sample = { error("binding audit snapshot exceeds its encoded budget") },
            record = {
                recorded = true
                true
            },
        )

        assertFalse("构造失败后不许落样本", recorded)
    }

    /** 落库期失败的同一保证（与构造失败分开钉：两条分支各自可失败）。 */
    @Test
    fun `a record failure is swallowed after the organization write succeeded`() = runBlocking {
        val command = buildBindingAuditSampleCommand(
            input = input(),
            command = command(),
            modelVersion = "model-x-1",
            providerId = null,
            classifications = listOf(suggestion(ClassificationDimension.KNOWLEDGE, "函数最值", "理由。")),
            atomicKnowledge = listOf(atom()),
            stepAttributions = emptyList(),
            sampledAtEpochMillis = utcMillis("2026-10-04"),
        )!!
        var attempted = false
        recordBindingAuditSampleBestEffort(
            sample = { command },
            record = {
                attempted = true
                error("database is unavailable")
            },
        )

        assertTrue("确实尝试过落库（不是静默跳过）", attempted)
    }

    /** 没有可审计的绑定（null 样本）是正常 no-op，不是失败。 */
    @Test
    fun `a null sample is a no-op`() = runBlocking {
        var recorded = false
        recordBindingAuditSampleBestEffort(
            sample = { null },
            record = {
                recorded = true
                true
            },
        )

        assertFalse(recorded)
    }

    private fun input() = ProblemOrganizationInput(
        problemId = PROBLEM,
        problemRevisionId = REVISION,
        practiceUnitId = PRACTICE,
        subject = SubjectKind.MATH,
        questionDocument = question("题面正文"),
        relevantLearningEvidence = emptyList(),
        relationCandidates = emptyList(),
        knowledgeBaseNodes = listOf(
            KnowledgeBaseNodeContext(
                knowledgeNodeId = ATOMIC_NODE,
                subject = SubjectKind.MATH,
                canonicalName = "确定函数最值",
                aliases = emptyList(),
                kind = KnowledgeNodeKind.PROCEDURE,
                granularity = KnowledgeNodeGranularity.ATOMIC,
                parentCanonicalName = "函数最值",
                taxonomyVersion = "organization-v1",
                verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED,
            ),
        ),
    )

    private fun command() = ConfirmProblemOrganizationCommand(
        commandId = "organization-apply:test",
        payloadFingerprint = "a".repeat(64),
        problemId = PROBLEM,
        problemRevisionId = REVISION,
        practiceUnitId = PRACTICE,
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = "knowledge:topic-1",
                stableCode = "math:knowledge:topic",
                subject = "MATH",
                displayName = "函数最值",
                parentKnowledgeNodeId = null,
                taxonomyVersion = "organization-v1",
                createdAtEpochMillis = utcMillis("2026-10-04"),
            ),
        ),
        knowledgeBindings = listOf(
            KnowledgeBindingSeedRecord(
                bindingId = "knowledge-binding:atomic-1",
                practiceUnitId = PRACTICE,
                knowledgeNodeId = ATOMIC_NODE,
                basisRevisionId = REVISION,
                strength = 0.75,
                sourceType = "LOCAL_POLICY_ACCEPTED",
                taxonomyVersion = "organization-v1",
                acceptedAtEpochMillis = utcMillis("2026-10-04"),
            ),
        ),
        classifications = listOf(
            classification(ClassificationDimension.KNOWLEDGE, "函数最值", "math:knowledge:topic"),
        ),
        relations = emptyList(),
        acceptedAtEpochMillis = utcMillis("2026-10-04"),
    )

    private fun classification(
        dimension: ClassificationDimension,
        displayName: String,
        labelId: String,
    ) = ProblemClassificationBindingRecord(
        bindingId = "classification:$labelId",
        problemId = PROBLEM,
        basisRevisionId = REVISION,
        dimension = dimension.name,
        labelId = labelId,
        displayName = displayName,
        taxonomyVersion = "organization-v1",
        acceptanceSource = "LOCAL_POLICY_ACCEPTED",
        acceptedAtEpochMillis = utcMillis("2026-10-04"),
    )

    private fun suggestion(
        dimension: ClassificationDimension,
        displayName: String,
        rationale: String,
    ) = ProblemClassificationSuggestion(
        dimension = dimension,
        displayName = displayName,
        rationaleMarkdown = rationale,
        confidence = 0.9,
    )

    private fun atom() = AtomicKnowledgeSuggestion(
        referenceId = "atom-1",
        canonicalName = "确定函数最值的候选位置",
        aliases = emptyList(),
        kind = KnowledgeNodeKind.PROCEDURE,
        parentKnowledgeDisplayName = "函数最值",
        matchedKnowledgeNodeId = ATOMIC_NODE,
        prerequisiteReferenceIds = emptyList(),
        observableOutcomeMarkdown = "能确定端点与驻点并比较函数值。",
        boundaryMarkdown = "不包含导数公式的机械计算。",
        confidence = 0.91,
    )

    private fun question(text: String) = QuestionDocument(
        id = "question-1",
        blocks = listOf(ContentBlock.Paragraph("block-1", text)),
    )

    private fun utcMillis(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private companion object {
        const val PROBLEM = "problem-1"
        const val REVISION = "revision-1"
        const val PRACTICE = "practice-1"
        const val ATOMIC_NODE = "knowledge:atomic-1"
    }
}
