package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationSource
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationSourceVerifier
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationUpgradeRejection
import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.EvidenceCorrection
import com.tingyun.smartmistakebook.core.model.EvidenceCorrectionKind
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G5-A 核验写方 API（KD-67，批次 22 T4）：UNKNOWN → VERIFIED/USER_ASSERTED 的显式升级。
 *
 * 消灭的失败：
 * - 唯一生产题面写入方固定 UNKNOWN，且没有任何显式升级口——G5-A 的候选即使接线也
 *   永远到不了 ASK（KD-67 死端）；
 * - 若用整行覆盖"顺手"升级，题面内容/揭示态/时间戳会被重写，"首见即真"与 KD-41 修复口的
 *   边界一起失守。本用例用一个只实现单列 CAS 的假写端口，钉住"除 verificationStatus
 *   外每一列逐字段不变"；
 * - 若用户确认来源缺失/来自别人，`USER_ASSERTED` 会变成无来源的信任标签。本用例钉住
 *   PRD-02 的来源语义与"不写库"。
 *
 * 说明：写端口现在走**生产 adapter** [StudyDatabaseAssessmentItemVerificationStatusWriter]
 *（批次 23 T2 接线）落到 `StudyDatabasePort.compareAndSetAssessmentItemVerificationStatus`；
 * 假库镜像真 DAO 的单列 CAS 语义（行不存在/当前状态不符 → 0，写入 → 1）。Room 端到端
 *（真实 SQL/列不变）由 `:core:database` 的 `AssessmentItemVerificationStatusInstrumentedTest`
 * 覆盖，本文件证明的是 API + 端口契约 + 生产 adapter 委托。
 */
class KnowledgeQuizItemVerificationUpgraderTest {

    private val learnerId = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID
    private val sessionId = 5_000L
    private val nodeId = "kc-a"

    private fun snapshotId() = knowledgeQuizItemSnapshotId(learnerId, sessionId, nodeId)

    private fun row(
        verificationStatus: String = StudyDbValue.VerificationStatus.UNKNOWN,
        optionsSnapshot: String = DEFAULT_OPTIONS,
    ) = AssessmentItemSnapshotSeedRecord(
        assessmentItemSnapshotId = snapshotId(),
        itemRevision = 1,
        practiceUnitId = null,
        problemRevisionId = null,
        tutorContentSnapshotId = snapshotId(),
        promptMarkdown = "考察 $nodeId 的题面",
        optionsSnapshot = optionsSnapshot,
        answerSpecSnapshot =
            """{"schema":"knowledge-quiz-item.v1","itemId":"knowledge-quiz:item:$nodeId",""" +
            """"correctChoiceId":"A","knowledgeNodeIds":["$nodeId"]}""",
        verificationStatus = verificationStatus,
        assessmentEligibility = StudyDbValue.AssessmentEligibility.SESSION_ONLY,
        scoringMode = StudyDbValue.ScoringMode.AUTO_VERIFIED,
        learnerSnapshotVersion = "knowledge-quiz-review-v1",
        projectionCheckpoint = 0L,
        hintLevelAtPresentation = 0,
        answerRevealState = "HIDDEN",
        createdAtEpochMillis = 11_000L,
    )

    private fun userAssertion(
        confirmedBy: String = learnerId,
        confirmationId: String = "key-correction:req-1:evidence-1",
    ) = AnswerKeyVerificationSource.UserAssertion(
        confirmationId = confirmationId,
        confirmedByLearnerId = confirmedBy,
        confirmedAtEpochMillis = 12_000L,
    )

    private fun independentVerification(
        verificationId: String = "manual-review:item-1",
    ) = AnswerKeyVerificationSource.IndependentVerification(
        verificationId = verificationId,
        verifiedAtEpochMillis = 12_000L,
    )

    /** 回执存在性端口：只有夹具里真实发出的两个 id 被视为存在（镜像未来 adapter）。 */
    private fun sourceVerifier() = AnswerKeyVerificationSourceVerifier { source, _, _ ->
        when (source) {
            is AnswerKeyVerificationSource.UserAssertion ->
                source.confirmationId == "key-correction:req-1:evidence-1"

            is AnswerKeyVerificationSource.IndependentVerification ->
                source.verificationId == "manual-review:item-1"
        }
    }

    /**
     * 生产 adapter + 计数装饰：既走真实 [StudyDatabaseAssessmentItemVerificationStatusWriter]
     * → 假库 CAS 口，又保留"拒绝路径不调用写口"的计数断言。
     */
    private class CountingWriter(
        database: FakeStudyDatabasePort,
    ) : AssessmentItemVerificationStatusWriter {
        private val delegate = StudyDatabaseAssessmentItemVerificationStatusWriter(database)
        var calls: Int = 0
            private set
        var lastExpected: String? = null
            private set
        var lastNew: String? = null
            private set

        override suspend fun compareAndSetVerificationStatus(
            assessmentItemSnapshotId: String,
            expectedCurrentStatus: String,
            newVerificationStatus: String,
        ): Boolean {
            calls += 1
            lastExpected = expectedCurrentStatus
            lastNew = newVerificationStatus
            return delegate.compareAndSetVerificationStatus(
                assessmentItemSnapshotId = assessmentItemSnapshotId,
                expectedCurrentStatus = expectedCurrentStatus,
                newVerificationStatus = newVerificationStatus,
            )
        }
    }

    private fun upgrader(
        database: FakeStudyDatabasePort,
        writer: CountingWriter,
        verifier: AnswerKeyVerificationSourceVerifier = sourceVerifier(),
    ) = KnowledgeQuizItemVerificationUpgrader(database, learnerId, writer, verifier)

    @Test
    fun `user asserted upgrade writes only the verification status column`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        val before = row().also { database.assessmentItemSnapshots[id] = it }
        val writer = CountingWriter(database)
        val upgrader = upgrader(database, writer)

        val outcome = upgrader.upgrade(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            targetVerification = AnswerKeyVerification.USER_ASSERTED,
            source = userAssertion(),
        )

        assertEquals(
            KnowledgeQuizItemVerificationUpgradeOutcome.Upgraded(
                assessmentItemSnapshotId = id,
                verificationStatus = StudyDbValue.VerificationStatus.USER_ASSERTED,
                source = userAssertion(),
            ),
            outcome,
        )
        assertEquals("只允许一次单列 CAS", 1, writer.calls)
        assertEquals(StudyDbValue.VerificationStatus.UNKNOWN, writer.lastExpected)
        assertEquals(StudyDbValue.VerificationStatus.USER_ASSERTED, writer.lastNew)
        val after = database.assessmentItemSnapshots.getValue(id)
        assertEquals(
            "升级只写 verificationStatus，其余列必须逐字段不变",
            before.copy(verificationStatus = StudyDbValue.VerificationStatus.USER_ASSERTED),
            after,
        )
        // 读面（装配器与反馈门共用）立刻看到新状态。
        assertEquals(
            StudyDbValue.VerificationStatus.USER_ASSERTED,
            KnowledgeQuizItemStore(database, learnerId).readSnapshot(sessionId, nodeId)?.verificationStatus,
        )
    }

    @Test
    fun `independent verification upgrades an unknown row to verified`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        val before = row().also { database.assessmentItemSnapshots[id] = it }
        val writer = CountingWriter(database)

        val outcome = upgrader(database, writer).upgrade(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            targetVerification = AnswerKeyVerification.VERIFIED,
            source = independentVerification(),
        )

        assertEquals(
            KnowledgeQuizItemVerificationUpgradeOutcome.Upgraded(
                assessmentItemSnapshotId = id,
                verificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
                source = independentVerification(),
            ),
            outcome,
        )
        assertEquals(1, writer.calls)
        assertEquals(StudyDbValue.VerificationStatus.UNKNOWN, writer.lastExpected)
        assertEquals(StudyDbValue.VerificationStatus.VERIFIED, writer.lastNew)
        assertEquals(
            before.copy(verificationStatus = StudyDbValue.VerificationStatus.VERIFIED),
            database.assessmentItemSnapshots.getValue(id),
        )
    }

    @Test
    fun `user asserted without an explicit user confirmation is rejected and writes nothing`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        val before = row().also { database.assessmentItemSnapshots[id] = it }
        val writer = CountingWriter(database)

        val outcome = upgrader(database, writer).upgrade(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            targetVerification = AnswerKeyVerification.USER_ASSERTED,
            source = independentVerification(),
        )

        assertEquals(
            KnowledgeQuizItemVerificationUpgradeOutcome.Rejected(
                AnswerKeyVerificationUpgradeRejection.SOURCE_DOES_NOT_MATCH_TARGET,
            ),
            outcome,
        )
        assertEquals("拒绝路径不得写库", 0, writer.calls)
        assertEquals(before, database.assessmentItemSnapshots.getValue(id))
    }

    @Test
    fun `a confirmation from another learner is rejected and writes nothing`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        val before = row().also { database.assessmentItemSnapshots[id] = it }
        val writer = CountingWriter(database)

        val outcome = upgrader(database, writer).upgrade(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            targetVerification = AnswerKeyVerification.USER_ASSERTED,
            source = userAssertion(confirmedBy = "learner:other"),
        )

        assertEquals(
            KnowledgeQuizItemVerificationUpgradeOutcome.Rejected(
                AnswerKeyVerificationUpgradeRejection.USER_CONFIRMATION_LEARNER_MISMATCH,
            ),
            outcome,
        )
        assertEquals(0, writer.calls)
        assertEquals(before, database.assessmentItemSnapshots.getValue(id))
    }

    @Test
    fun `verified rows are idempotent at the same target and never rewritten for another target`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        database.assessmentItemSnapshots[id] = row(
            verificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
        )
        val writer = CountingWriter(database)
        val upgrader = upgrader(database, writer)

        // 重试同一个目标：幂等成功，不写库。
        assertEquals(
            KnowledgeQuizItemVerificationUpgradeOutcome.AlreadyUpgraded(
                assessmentItemSnapshotId = id,
                verificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
            ),
            upgrader.upgrade(
                sessionId = sessionId,
                knowledgeNodeId = nodeId,
                targetVerification = AnswerKeyVerification.VERIFIED,
                source = independentVerification(),
            ),
        )
        assertEquals(0, writer.calls)

        // 已核验行不得被另一个目标改写（包括 USER_ASSERTED → VERIFIED 的二次升级）。
        assertEquals(
            KnowledgeQuizItemVerificationUpgradeOutcome.Rejected(
                AnswerKeyVerificationUpgradeRejection.CURRENT_IS_NOT_UNVERIFIED,
            ),
            upgrader.upgrade(
                sessionId = sessionId,
                knowledgeNodeId = nodeId,
                targetVerification = AnswerKeyVerification.USER_ASSERTED,
                source = userAssertion(),
            ),
        )
        assertEquals(0, writer.calls)
        assertEquals(
            StudyDbValue.VerificationStatus.VERIFIED,
            database.assessmentItemSnapshots.getValue(id).verificationStatus,
        )
    }

    @Test
    fun `a missing snapshot is reported instead of creating a row`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val writer = CountingWriter(database)

        val outcome = upgrader(database, writer).upgrade(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            targetVerification = AnswerKeyVerification.USER_ASSERTED,
            source = userAssertion(),
        )

        assertEquals(KnowledgeQuizItemVerificationUpgradeOutcome.SnapshotNotFound, outcome)
        assertEquals(0, writer.calls)
        assertTrue(database.assessmentItemSnapshots.isEmpty())
    }

    @Test
    fun `an unreadable snapshot is not upgraded`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        val before = row(optionsSnapshot = "[not-json").also { database.assessmentItemSnapshots[id] = it }
        val writer = CountingWriter(database)

        val outcome = upgrader(database, writer).upgrade(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            targetVerification = AnswerKeyVerification.USER_ASSERTED,
            source = userAssertion(),
        )

        assertEquals(KnowledgeQuizItemVerificationUpgradeOutcome.SnapshotUnreadable, outcome)
        assertEquals("坏题面不得被标成已核验", 0, writer.calls)
        assertEquals(before, database.assessmentItemSnapshots.getValue(id))
    }

    @Test
    fun `a confirmation id with no receipt is rejected and writes nothing`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        val before = row().also { database.assessmentItemSnapshots[id] = it }
        val writer = CountingWriter(database)

        // 复核发现 #13：只校验"非空"时，这条自造 id 会把 UNKNOWN 写成 USER_ASSERTED。
        val outcome = upgrader(database, writer).upgrade(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            targetVerification = AnswerKeyVerification.USER_ASSERTED,
            source = userAssertion(confirmationId = "forged:never-issued"),
        )

        assertEquals(
            KnowledgeQuizItemVerificationUpgradeOutcome.Rejected(
                AnswerKeyVerificationUpgradeRejection.SOURCE_NOT_VERIFIED,
            ),
            outcome,
        )
        assertEquals("无回执不得写库", 0, writer.calls)
        assertEquals(before, database.assessmentItemSnapshots.getValue(id))
    }

    @Test
    fun `an already upgraded row with a forged receipt is not reported as idempotent`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        database.assessmentItemSnapshots[id] = row(
            verificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
        )
        val writer = CountingWriter(database)

        // 幂等出口以前在来源校验之前直接返回 AlreadyUpgraded，UI 会把"无回执重试"
        // 显示成"已确认"。现在同样必须先证明回执存在。
        val outcome = upgrader(database, writer).upgrade(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            targetVerification = AnswerKeyVerification.VERIFIED,
            source = independentVerification(verificationId = "forged:never-issued"),
        )

        assertEquals(
            KnowledgeQuizItemVerificationUpgradeOutcome.Rejected(
                AnswerKeyVerificationUpgradeRejection.SOURCE_NOT_VERIFIED,
            ),
            outcome,
        )
        assertEquals(0, writer.calls)
    }

    @Test
    fun `the production writer maps the raw CAS row count and never creates a row`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        database.assessmentItemSnapshots[id] = row()
        val writer = StudyDatabaseAssessmentItemVerificationStatusWriter(database)

        assertEquals(
            1,
            database.compareAndSetAssessmentItemVerificationStatus(
                id,
                StudyDbValue.VerificationStatus.UNKNOWN,
                StudyDbValue.VerificationStatus.USER_ASSERTED,
            ),
        )
        assertFalse(
            "第二次 CAS 的 expected 已过期，必须如实返回 false",
            writer.compareAndSetVerificationStatus(
                id,
                StudyDbValue.VerificationStatus.UNKNOWN,
                StudyDbValue.VerificationStatus.USER_ASSERTED,
            ),
        )
        assertEquals(
            StudyDbValue.VerificationStatus.USER_ASSERTED,
            database.assessmentItemSnapshots.getValue(id).verificationStatus,
        )
        assertEquals(
            "行不存在必须如实返回 0 行，不得补一行",
            0,
            database.compareAndSetAssessmentItemVerificationStatus(
                "snapshot-missing",
                StudyDbValue.VerificationStatus.UNKNOWN,
                StudyDbValue.VerificationStatus.VERIFIED,
            ),
        )
        assertFalse(database.assessmentItemSnapshots.containsKey("snapshot-missing"))
    }

    /**
     * KD-67 并发半边：两个调用方都读到 `UNKNOWN` 后才各自尝试 CAS——恰一个生效。
     * 屏障放在 sourceVerifier 里，保证两条路径都完成"读行 → 验来源"之后才进写口；
     * 输家 CAS 返回 0 行，重读发现已是本次目标，按幂等出口回报 [AlreadyUpgraded]。
     */
    @Test
    fun `two concurrent upgrades of the same unknown row have exactly one winner`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        database.assessmentItemSnapshots[id] = row()
        val writer = CountingWriter(database)
        val arrivals = AtomicInteger(0)
        val bothVerified = CompletableDeferred<Unit>()
        val barrierVerifier = AnswerKeyVerificationSourceVerifier { _, _, _ ->
            if (arrivals.incrementAndGet() == 2) bothVerified.complete(Unit)
            bothVerified.await()
            true
        }

        val outcomes = coroutineScope {
            List(2) {
                async {
                    KnowledgeQuizItemVerificationUpgrader(
                        database,
                        learnerId,
                        writer,
                        barrierVerifier,
                    ).upgrade(
                        sessionId = sessionId,
                        knowledgeNodeId = nodeId,
                        targetVerification = AnswerKeyVerification.USER_ASSERTED,
                        source = userAssertion(),
                    )
                }
            }.awaitAll()
        }

        assertEquals(
            "并发同 id 两次必须恰一次生效",
            1,
            outcomes.count { it is KnowledgeQuizItemVerificationUpgradeOutcome.Upgraded },
        )
        assertEquals(
            "输家按重读结果走幂等出口",
            1,
            outcomes.count {
                it == KnowledgeQuizItemVerificationUpgradeOutcome.AlreadyUpgraded(
                    assessmentItemSnapshotId = id,
                    verificationStatus = StudyDbValue.VerificationStatus.USER_ASSERTED,
                )
            },
        )
        assertEquals("两条路径都应完成来源校验后才尝试 CAS", 2, writer.calls)
        assertEquals(
            StudyDbValue.VerificationStatus.USER_ASSERTED,
            database.assessmentItemSnapshots.getValue(id).verificationStatus,
        )
    }

    private companion object {
        const val DEFAULT_OPTIONS =
            """[{"id":"A","markdown":"正确项"},{"id":"B","markdown":"干扰项"}]"""
    }

    /**
     * KD-67 生产回执校验（批次 49）：`UserAssertion.confirmationId` 必须指向账本里一条真实的
     * **用户断言当前答案**的记录，而不是任意非空字符串。
     *
     * 消灭的失败：端口有契约没有生产实现时，确认流只有两条路——把自造 id 当回执（升级口
     * 被绕过），或在生产里永远 `SOURCE_NOT_VERIFIED`（学生点了确认、状态却没变）。
     */
    @Test
    fun `the ledger verifier accepts the learners own asserted key receipt`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        database.assessmentItemSnapshots[id] = row()
        val assertionId = "knowledge-quiz:review:5000:item:kc-a:A:key-fix:A"
        database.projectionLedger = listOf(
            ledgerEvent(
                ChatEvidenceSubmitted(
                    evidenceId = assertionId,
                    conversationId = "knowledge-quiz-review:5000",
                    knowledgeNodeId = nodeId,
                    direction = LearningEvidenceDirection.POSITIVE,
                    weight = 0.18,
                    reasonMarkdown = knowledgeQuizKeyCorrectionReasonPrefix(
                        itemId = "knowledge-quiz:item:$nodeId",
                        newCorrectChoiceId = "A",
                    ) + "学生确认答案",
                    confidence = 0.95,
                    occurredAtEpochMillis = 12_000L,
                    eventSequence = 1L,
                ),
            ),
        )
        val verifier = LedgerAnswerKeyVerificationSourceVerifier(database, learnerId)

        assertTrue(
            verifier.verify(userAssertion(confirmationId = assertionId), learnerId, id),
        )
        assertFalse(
            "同一学习者但没有这条回执的自造 id 不得通过",
            verifier.verify(userAssertion(confirmationId = "forged:never-issued"), learnerId, id),
        )
    }

    @Test
    fun `the ledger verifier rejects a receipt asserting a different key`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        database.assessmentItemSnapshots[id] = row()
        // 学生把答案改成了 B；这条记录断言的是 B，不是题面当前的 A。
        val assertionId = "knowledge-quiz:review:5000:item:kc-a:B:key-fix:B"
        database.projectionLedger = listOf(
            ledgerEvent(
                ChatEvidenceSubmitted(
                    evidenceId = assertionId,
                    conversationId = "knowledge-quiz-review:5000",
                    knowledgeNodeId = nodeId,
                    direction = LearningEvidenceDirection.NEGATIVE,
                    weight = 0.35,
                    reasonMarkdown = knowledgeQuizKeyCorrectionReasonPrefix(
                        itemId = "knowledge-quiz:item:$nodeId",
                        newCorrectChoiceId = "B",
                    ) + "学生改正答案键",
                    confidence = 0.95,
                    occurredAtEpochMillis = 12_000L,
                    eventSequence = 1L,
                ),
            ),
        )
        val verifier = LedgerAnswerKeyVerificationSourceVerifier(database, learnerId)

        assertFalse(
            "断言了另一个键的记录不是'确认当前答案'",
            verifier.verify(userAssertion(confirmationId = assertionId), learnerId, id),
        )
    }

    @Test
    fun `the ledger verifier rejects an assertion the learner later superseded`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val id = snapshotId()
        database.assessmentItemSnapshots[id] = row()
        val assertionId = "knowledge-quiz:review:5000:item:kc-a:A:key-fix:A"
        database.projectionLedger = listOf(
            ledgerEvent(
                ChatEvidenceSubmitted(
                    evidenceId = assertionId,
                    conversationId = "knowledge-quiz-review:5000",
                    knowledgeNodeId = nodeId,
                    direction = LearningEvidenceDirection.POSITIVE,
                    weight = 0.18,
                    reasonMarkdown = knowledgeQuizKeyCorrectionReasonPrefix(
                        itemId = "knowledge-quiz:item:$nodeId",
                        newCorrectChoiceId = "A",
                    ) + "学生确认答案",
                    confidence = 0.95,
                    occurredAtEpochMillis = 12_000L,
                    eventSequence = 1L,
                ),
            ),
            ledgerEvent(
                EvidenceCorrection(
                    correctionId = "key-void:req-9:$assertionId",
                    targetEvidenceId = assertionId,
                    kind = EvidenceCorrectionKind.VOID,
                    replacementDirection = LearningEvidenceDirection.NONE,
                    replacementWeight = 0.0,
                    reasonMarkdown = "撤销确认",
                    occurredAtEpochMillis = 13_000L,
                    eventSequence = 2L,
                ),
            ),
        )
        val verifier = LedgerAnswerKeyVerificationSourceVerifier(database, learnerId)

        assertFalse(
            "被撤销的断言 = 学生收回了确认，不得再作为来源",
            verifier.verify(userAssertion(confirmationId = assertionId), learnerId, id),
        )
    }

    @Test
    fun `the ledger verifier refuses independent verification because no production record source exists`() =
        runBlocking {
            val database = FakeStudyDatabasePort()
            val id = snapshotId()
            database.assessmentItemSnapshots[id] = row()

            val verifier = LedgerAnswerKeyVerificationSourceVerifier(database, learnerId)

            assertFalse(
                "没有独立核验记录库时宁可不可达，也不拿自造 id 冒充",
                verifier.verify(independentVerification(), learnerId, id),
            )
        }

    private fun ledgerEvent(event: com.tingyun.smartmistakebook.core.model.LearningLedgerEvent) =
        com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent(
            event = event,
            canonicalFingerprint = "fingerprint:${event.ledgerEventId}",
        )
}
