package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.domain.MasteryWriteGate
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection
import com.tingyun.smartmistakebook.core.model.TutorUnderstandingTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AUD-11 ①②（真 Room + 真写入口 `KnowledgeEvidenceWriter`）：**冷却限 MODEL_CHAT** 与
 * **落库去向可读**。
 *
 * 三层验收各自钉一面（对应报告 §2.2 的实测反例）：
 * 1. 模型写之后 1h 的小测写**放行且落库**（改前：`SAME_KC_IN_COOLDOWN`，当晚小测证据 0/3）；
 * 2. 反向亦然：小测写的 accepted 行不再是模型写的冷却起点（改前它挡掉模型的下一笔）；
 * 3. 模型通道自己的 12h 冷却**保持**（含严格 `<` 边界：12h−1ms 拒、正好 12h 放行）；
 * 4. `accepted 被 IGNORE` 可读且自纠：正常重放 = REPLAYED；身份粘性（既有行是被拒观察行）
 *    = 保留观察行、合法写换新身份入账并报 LEDGERED_AFTER_REJECTED_IDENTITY（KD-43(b)）；
 * 5. 校准分桶带上 `source_kind` 维（真 SQL 的 GROUP BY，写入值与查询值必须逐字一致）。
 *
 * 写通道请求形状对齐生产：讲题通道照 `RoomTutorToolRunner.masteryUpdate`（方向/理解档/置信/
 * 锚定等级/证据锚条数），测验通道照 `KnowledgeQuizFeedbackWriter.submit`（置信 1.0 =
 * `OBJECTIVE_ANSWER_CONFIDENCE`、`anchor_class=CONFIRMED`、`hasObjectiveSupport`）。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeQuizCooldownChannelScopeInstrumentedTest {

    @Test
    fun aQuizWriteLandsEvenWhenAModelWriteJustLandedOnTheSameKc() = runBlocking {
        withRealDatabase { database ->
            val writer = KnowledgeEvidenceWriter(database)
            assertTrue(writer.write(modelRequest("req-model-1", occurredAtEpochMillis = T0)) is
                KnowledgeEvidenceWriteOutcome.Accepted)

            // 1h 后同一 KC 的客观作答（真实作答节律：一晚讲题后当晚做题）。
            val quiz = writer.write(quizRequest("req-quiz-1", occurredAtEpochMillis = T0 + ONE_HOUR_MILLIS))

            assertEquals(
                "客观作答通道不取模型通道的冷却年龄",
                KnowledgeEvidenceLedgerStatus.LEDGERED,
                acceptedStatus(quiz),
            )
            val rows = database.readChatEvidenceByLearner(LEARNER_ID)
            assertEquals("两笔都该落库（模型 + 小测）", 2, rows.size)
            assertTrue("没有一笔该带拒因", rows.all { it.rejected_reason == null })
            assertEquals(
                setOf("MODEL_CHAT", "KNOWLEDGE_QUIZ"),
                rows.map { it.source_kind }.toSet(),
            )
            assertEquals(
                "两笔各自进账本（不同通道、各自分配序列）",
                2,
                chatEvents(database).size,
            )
        }
    }

    @Test
    fun aModelWriteLandsEvenWhenAQuizWriteJustLandedOnTheSameKc() = runBlocking {
        withRealDatabase { database ->
            val writer = KnowledgeEvidenceWriter(database)
            assertTrue(writer.write(quizRequest("req-quiz-2", occurredAtEpochMillis = T0)) is
                KnowledgeEvidenceWriteOutcome.Accepted)

            val model = writer.write(modelRequest("req-model-2", occurredAtEpochMillis = T0 + ONE_HOUR_MILLIS))

            assertEquals(
                "反向亦然：别的通道的 accepted 行不是本通道的冷却起点",
                KnowledgeEvidenceLedgerStatus.LEDGERED,
                acceptedStatus(model),
            )
            val rows = database.readChatEvidenceByLearner(LEARNER_ID)
            assertEquals(2, rows.size)
            assertTrue(rows.all { it.rejected_reason == null })
        }
    }

    @Test
    fun theModelCooldownStillRejectsASecondModelWriteInsideTheWindow() = runBlocking {
        withRealDatabase { database ->
            val writer = KnowledgeEvidenceWriter(database)
            writer.write(modelRequest("req-model-3a", occurredAtEpochMillis = T0))

            val second = writer.write(modelRequest("req-model-3b", occurredAtEpochMillis = T0 + ONE_HOUR_MILLIS))

            assertEquals(
                "模型通道的 12h 自我重复冷却必须保持",
                MasteryWriteGate.RejectReason.SAME_KC_IN_COOLDOWN,
                (second as KnowledgeEvidenceWriteOutcome.Rejected).reason,
            )
            val rows = database.readChatEvidenceByLearner(LEARNER_ID)
            assertEquals("被拒 ≠ 删除：观察行落库", 2, rows.size)
            assertEquals(
                MasteryWriteGate.RejectReason.SAME_KC_IN_COOLDOWN.name,
                rows.single { it.evidence_id == modelEvidenceId("req-model-3b") }.rejected_reason,
            )
            assertEquals("被拒行不进账本", 1, chatEvents(database).size)
        }
    }

    @Test
    fun theModelCooldownBoundaryStaysStrictlyBelowTwelveHours() = runBlocking {
        withRealDatabase { database ->
            val writer = KnowledgeEvidenceWriter(database)
            writer.write(modelRequest("req-model-4a", occurredAtEpochMillis = T0))

            val justInside = writer.write(
                modelRequest("req-model-4b", occurredAtEpochMillis = T0 + TWELVE_HOURS_MILLIS - 1),
            )
            assertEquals(
                "严格 <：差 1ms 仍拒",
                MasteryWriteGate.RejectReason.SAME_KC_IN_COOLDOWN,
                (justInside as KnowledgeEvidenceWriteOutcome.Rejected).reason,
            )

            val exactlyAtBoundary = writer.write(
                modelRequest("req-model-4c", occurredAtEpochMillis = T0 + TWELVE_HOURS_MILLIS),
            )
            assertEquals(
                "正好 12h 放行（边界是严格小于）",
                KnowledgeEvidenceLedgerStatus.LEDGERED,
                acceptedStatus(exactlyAtBoundary),
            )
        }
    }

    @Test
    fun aWriteBlockedByARejectedRowIsCompensatedUnderAFreshIdentityWithoutTouchingTheObservation() =
        runBlocking {
            withRealDatabase { database ->
                val writer = KnowledgeEvidenceWriter(database)
                // 身份粘性（AUD-11 ③ 附带测到、登记 KD-43(b)）：同一身份先被拒（置信不足），
                // 条件变化后同一身份的合法写通过门。裁定：观察行保留，合法写换新身份入账。
                val rejected = writer.write(
                    modelRequest("req-sticky", occurredAtEpochMillis = T0, evidenceConfidence = 0.5),
                )
                assertEquals(
                    MasteryWriteGate.RejectReason.EVIDENCE_BELOW_CONFIDENCE,
                    (rejected as KnowledgeEvidenceWriteOutcome.Rejected).reason,
                )

                val accepted = writer.write(modelRequest("req-sticky", occurredAtEpochMillis = T0 + 1))

                val outcome = accepted as KnowledgeEvidenceWriteOutcome.Accepted
                assertEquals(
                    "回执标记粘性：换了新身份并且已经入账",
                    KnowledgeEvidenceLedgerStatus.LEDGERED_AFTER_REJECTED_IDENTITY,
                    outcome.ledgerStatus,
                )
                val rows = database.readChatEvidenceByLearner(LEARNER_ID)
                val observation = rows.single { it.evidence_id == modelEvidenceId("req-sticky") }
                assertTrue("既有被拒行不被改写（IGNORE 语义）", observation.isRejected)
                val compensatedId = knowledgeEvidenceIdAfterRejectedIdentity(
                    modelEvidenceId("req-sticky"),
                    NODE_ID,
                )
                val compensated = rows.single { it.evidence_id == compensatedId }
                assertNull("新身份落的是正常证据", compensated.rejected_reason)
                assertEquals(
                    "账本里正好有这一笔补偿事件",
                    listOf(compensatedId),
                    chatEvents(database).map { it.evidenceId },
                )

                // 同一请求重试：补偿身份已存在 → 重放，不再多一笔。
                // 时间推到模型通道 12h 冷却之外，否则这一笔会先被冷却挡成 Rejected。
                val replay = writer.write(
                    modelRequest(
                        "req-sticky",
                        occurredAtEpochMillis = T0 + TWELVE_HOURS_MILLIS + 1,
                    ),
                )
                assertEquals(
                    KnowledgeEvidenceLedgerStatus.REPLAYED_AFTER_REJECTED_IDENTITY,
                    (replay as KnowledgeEvidenceWriteOutcome.Accepted).ledgerStatus,
                )
                assertEquals("重放不推进序列、不写第二条 outbox", 1, chatEvents(database).size)
                assertEquals(2, database.readChatEvidenceByLearner(LEARNER_ID).size)
            }
        }

    @Test
    fun anAcceptedReplayIsReportedAsReplayedWithoutASecondLedgerEvent() = runBlocking {
        withRealDatabase { database ->
            val writer = KnowledgeEvidenceWriter(database)
            writer.write(quizRequest("req-replay", occurredAtEpochMillis = T0))

            val replay = writer.write(quizRequest("req-replay", occurredAtEpochMillis = T0 + 1_000))

            assertEquals(
                "同身份重放 = 重试（AUD-01），如实报 REPLAYED 而不是丢写",
                KnowledgeEvidenceLedgerStatus.REPLAYED,
                acceptedStatus(replay),
            )
            assertEquals(1, database.readChatEvidenceByLearner(LEARNER_ID).size)
            assertEquals("重放不推进序列、不写第二条 outbox", 1, chatEvents(database).size)
        }
    }

    @Test
    fun theCalibrationBucketsCarryTheChannelDimension() = runBlocking {
        withRealDatabase { database ->
            val writer = KnowledgeEvidenceWriter(database)
            writer.write(modelRequest("req-cal-model", occurredAtEpochMillis = T0))
            writer.write(quizRequest("req-cal-quiz", occurredAtEpochMillis = T0))
            writer.write(modelRequest("req-cal-model-2", occurredAtEpochMillis = T0 + 1_000))
            // 未锚定节点：拒因与模型通道那条不同，用来证明分桶读得出"哪条通道被哪个门挡"。
            writer.write(modelRequest("req-cal-model-3", nodeId = "kc-not-in-library", occurredAtEpochMillis = T0))

            val rejected = database.countRejectedChatEvidenceByReason(LEARNER_ID)
                .map { Triple(it.sourceKind, it.reason, it.count) }
                .toSet()

            assertEquals(
                setOf(
                    Triple(
                        StudyDbValue.ChatEvidenceSourceKind.MODEL_CHAT,
                        MasteryWriteGate.RejectReason.SAME_KC_IN_COOLDOWN.name,
                        1,
                    ),
                    Triple(
                        StudyDbValue.ChatEvidenceSourceKind.MODEL_CHAT,
                        MasteryWriteGate.RejectReason.KNOWLEDGE_NODE_NOT_ANCHORED.name,
                        1,
                    ),
                ),
                rejected,
            )

            val acceptedPerHour = database.countAcceptedChatEvidencePerHour(LEARNER_ID, sinceEpochMillis = 0)
                .associate { it.sourceKind to it.count }
            assertEquals(
                mapOf(
                    StudyDbValue.ChatEvidenceSourceKind.MODEL_CHAT to 1,
                    StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ to 1,
                ),
                acceptedPerHour,
            )
            assertNull(
                "窗口之外没有 accepted 行可统计（该查询只数窗口内的 accepted 行）",
                database.countAcceptedChatEvidencePerHour(LEARNER_ID, sinceEpochMillis = Long.MAX_VALUE).firstOrNull(),
            )
        }
    }

    private fun acceptedStatus(outcome: KnowledgeEvidenceWriteOutcome): KnowledgeEvidenceLedgerStatus =
        (outcome as KnowledgeEvidenceWriteOutcome.Accepted).ledgerStatus

    private suspend fun chatEvents(database: StudyDatabasePort): List<ChatEvidenceSubmitted> =
        database.loadProjectionBatch(
            projectionName = "study-experience-v1",
            learnerId = LEARNER_ID,
            limit = 100,
        ).events.map { it.event }.filterIsInstance<ChatEvidenceSubmitted>()

    private suspend fun withRealDatabase(block: suspend (StudyDatabasePort) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "quiz-cooldown-scope-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        try {
            seedStudyFacts(context, database, databaseName, quizSeed())
            block(database)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun quizSeed() = StudySeedBundle(
        problems = emptyList(),
        revisions = emptyList(),
        practiceUnits = emptyList(),
        errorBookEntries = emptyList(),
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = NODE_ID,
                stableCode = "$TAXONOMY_VERSION:math:atomic:monotonicity",
                subject = "MATH",
                displayName = "判断函数单调性",
                parentKnowledgeNodeId = null,
                taxonomyVersion = TAXONOMY_VERSION,
                createdAtEpochMillis = 1_000,
                canonicalName = "判断函数单调性",
                nodeKind = KnowledgeNodeKind.REASONING.name,
                granularity = KnowledgeNodeGranularity.ATOMIC.name,
                verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
            ),
        ),
    )

    private fun modelEvidenceId(requestId: String) = "chat-ev:$requestId:mastery_update:$NODE_ID"

    private fun modelRequest(
        requestId: String,
        occurredAtEpochMillis: Long,
        nodeId: String = NODE_ID,
        evidenceConfidence: Double = 0.9,
    ) = KnowledgeEvidenceWriteRequest(
        learnerId = LEARNER_ID,
        channel = KnowledgeEvidenceChannel.MODEL_CHAT,
        namespace = requestId,
        discriminator = "mastery_update",
        conversationId = "tutor-session:$requestId",
        knowledgeNodeId = nodeId,
        direction = TutorEvidenceDirection.NEGATIVE,
        understanding = TutorUnderstandingTier.CONFIDENT,
        evidenceConfidence = evidenceConfidence,
        reasonMarkdown = "学生这一步没做出来",
        anchorClass = MasteryWriteGate.ANCHOR_CLASS_CONFIRMED,
        occurredAtEpochMillis = occurredAtEpochMillis,
    )

    private fun quizRequest(
        requestId: String,
        occurredAtEpochMillis: Long,
    ) = KnowledgeEvidenceWriteRequest(
        learnerId = LEARNER_ID,
        channel = KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ,
        namespace = requestId,
        conversationId = "knowledge-quiz-review:$SESSION_ID",
        knowledgeNodeId = NODE_ID,
        direction = TutorEvidenceDirection.POSITIVE,
        understanding = TutorUnderstandingTier.CONFIDENT,
        evidenceConfidence = OBJECTIVE_ANSWER_CONFIDENCE,
        reasonMarkdown = "知识点复习作答（答对）",
        anchorClass = MasteryWriteGate.ANCHOR_CLASS_CONFIRMED,
        hasObjectiveSupport = true,
        occurredAtEpochMillis = occurredAtEpochMillis,
    )

    private companion object {
        const val LEARNER_ID = "learner:local"
        const val NODE_ID = "kc-monotonicity"
        const val TAXONOMY_VERSION = "cn-highschool-m1-v1"
        const val SESSION_ID = 1_700_000_000_000L
        const val T0 = 1_700_000_000_000L
        const val ONE_HOUR_MILLIS = 60L * 60 * 1_000
        const val TWELVE_HOURS_MILLIS = 12L * 60 * 60 * 1_000
    }
}
