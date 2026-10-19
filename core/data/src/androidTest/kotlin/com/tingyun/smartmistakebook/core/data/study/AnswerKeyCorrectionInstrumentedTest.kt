package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T2/PRD-02（KD-43）：**真 Room + 生产写路径**的答案键纠正/撤销/重放。
 *
 * 消灭的失败（AUD-11 §3.1/§3.3）：错键判错后没有补偿、替代或版本化落点，重放会确定性
 * 复现错判；证据行里也没有题面/键版本，无法定位受影响的行。
 *
 * 覆盖：错键判错→改键、正确→撤销、改两次/撤销更正、重复请求、后续已有新作答、同 KC
 * 其他题/其他 KC 不受误伤、纠错中被杀、旧键迟到、键信任门（未核验键只留观察行）、
 * 已作答后报告键问题（无账本目标时回落争议观察）、第二次报告仍指向原作答。
 * "改绑后重放"与"旧包恢复"分别由 `BindingChangedReplayDrainerTest` 与
 * `FullMigrationMatrixInstrumentedTest` 的 67→68 用例承担（此处不重复伪造）。
 */
@RunWith(AndroidJUnit4::class)
class AnswerKeyCorrectionInstrumentedTest {

    @Test
    fun aWrongKeyVerdictIsCorrectedAndReplayedAtTheOriginalAnswerPosition() = runBlocking {
        withFixture { database, repository, fixture ->
            val wrong = fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "B")
            assertFalse(wrong.isCorrect)
            assertTrue("独立核验过的键走现有规则", wrong.evidenceRecorded)
            repository.refresh()
            val target = database.readChatEvidenceByLearner(LEARNER_ID)
                .single { it.source_kind == "KNOWLEDGE_QUIZ" }

            val receipt = fixture.correctionWriter().submit(
                AnswerKeyCorrectionCommand(
                    requestId = "fix-key-1",
                    knowledgeNodeId = NODE,
                    conversationId = fixture.conversationId,
                    targetEvidenceId = target.evidence_id,
                    itemId = ITEM_ID,
                    selectedChoiceId = "B",
                    newCorrectChoiceId = "B",
                    reasonMarkdown = "答案键应为 B",
                    occurredAtEpochMillis = T0 + 10_000,
                ),
            )
            assertEquals(AnswerKeyCorrectionStatus.RECORDED, receipt.status)
            assertNotNull("键版本 = 本次纠正在账本里的序列", receipt.keyVersion)
            assertTrue("回执声明重放待办", receipt.replayPending)
            assertEquals(setOf(NODE), receipt.affectedKnowledgeNodeIds)

            repository.refresh()
            val corrected = fixture.mastery(NODE)
            assertEquals(0.15, corrected.successWeight, 1e-9)
            assertEquals(0.0, corrected.failureWeight, 1e-9)
            assertEquals(
                "更正挂回原作答时间",
                T0,
                corrected.lastEvidenceAtEpochMillis ?: -1L,
            )

            // 增量 vs 全量逐位一致：drain 的持久化快照 == 从空快照重放整本账本。
            val ledger = database.loadLearningLedger(LEARNER_ID)
            val replayed = LearningProjector().replay(
                learnerId = LEARNER_ID,
                ledger = ledger.validPrefix.map { it.event },
                canonicalFingerprints = ledger.validPrefix.associate {
                    it.event.ledgerEventId to it.canonicalFingerprint
                },
            )
            val persisted = database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID)!!
            assertEquals(replayed.snapshot.knowledgeMasteryStates, persisted.snapshot.knowledgeMasteryStates)
            assertEquals(replayed.snapshot.problemMemoryStates, persisted.snapshot.problemMemoryStates)
            assertTrue(replayed.appliedEvidenceCorrectionIds.isNotEmpty())

            // 读给会话的题面带最新有效替代键（快照行本身不改写）：UI 的本地判题与写入口
            // 因此不会分叉。
            assertEquals(
                "B",
                repository.readPersistedKnowledgeQuizItem(SESSION_ID, NODE)?.correctChoiceId,
            )

            // 后续已有新作答：同一题面（本夹具固定 item id，模拟“恢复同一题面”）按**更正后的键**
            // （B）作答可以正常计分；生产上新生成的题面会得到新的 item id，不继承旧题面的键更正。
            // 题面快照本身不改写，新键来自账本里的更正事件。
            val followUp = fixture.submitAnswer(
                correctChoiceId = "B",
                selectedChoiceId = "B",
                sessionId = SESSION_ID + 1,
            )
            assertTrue("更正后的键可继续按现有规则计分", followUp.evidenceRecorded)
            repository.refresh()
            assertEquals(
                "历史替代 0.15 + 后续作答 0.15",
                0.30,
                fixture.mastery(NODE).successWeight,
                1e-9,
            )
        }
    }

    @Test
    fun voidingACorrectAnswerReturnsTheKnowledgeNodeToNoNumericEvidence() = runBlocking {
        withFixture { database, repository, fixture ->
            val correct = fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "A")
            assertTrue(correct.isCorrect)
            assertTrue(correct.evidenceRecorded)
            repository.refresh()
            assertEquals(0.15, fixture.mastery(NODE).successWeight, 1e-9)
            val target = database.readChatEvidenceByLearner(LEARNER_ID)
                .single { it.source_kind == "KNOWLEDGE_QUIZ" }

            val receipt = fixture.correctionWriter().submit(
                AnswerKeyCorrectionCommand(
                    requestId = "void-key-1",
                    knowledgeNodeId = NODE,
                    conversationId = fixture.conversationId,
                    targetEvidenceId = target.evidence_id,
                    reasonMarkdown = "这道题其实没有标准答案",
                    occurredAtEpochMillis = T0 + 10_000,
                    voidTarget = true,
                ),
            )
            assertEquals(AnswerKeyCorrectionStatus.RECORDED, receipt.status)

            repository.refresh()
            assertTrue(
                "撤销 = 从有效证据集合排除，不是零权重继续推进",
                database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID)!!
                    .snapshot.knowledgeMasteryStates[NODE] == null,
            )
            assertNull(
                "原证据行一字不改：仍在库里",
                database.readChatEvidenceByLearner(LEARNER_ID)
                    .single { it.evidence_id == target.evidence_id }
                    .rejected_reason,
            )
        }
    }

    @Test
    fun correctingTwiceAndUndoingTheCorrectionFollowLedgerOrder() = runBlocking {
        withFixture { database, repository, fixture ->
            fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "B")
            repository.refresh()
            val target = database.readChatEvidenceByLearner(LEARNER_ID)
                .single { it.source_kind == "KNOWLEDGE_QUIZ" }
            val writer = fixture.correctionWriter()

            val first = writer.submit(
                fixture.correctionCommand(
                    requestId = "fix-key-first",
                    target = target.evidence_id,
                    replacementCorrect = true,
                    at = T0 + 10_000,
                ),
            )
            val second = writer.submit(
                fixture.correctionCommand(
                    requestId = "fix-key-second",
                    target = first.correctionId,
                    replacementCorrect = false,
                    at = T0 + 20_000,
                ),
            )
            repository.refresh()
            assertEquals(0.0, fixture.mastery(NODE).successWeight, 1e-9)
            assertEquals(0.35, fixture.mastery(NODE).failureWeight, 1e-9)

            // 撤销更正：指向"第二次更正"的 VOID = 第二次更正不再生效，回退到第一次更正。
            val undo = writer.submit(
                AnswerKeyCorrectionCommand(
                    requestId = "undo-second-fix",
                    knowledgeNodeId = NODE,
                    conversationId = fixture.conversationId,
                    targetEvidenceId = second.correctionId,
                    reasonMarkdown = "第二次改错了，撤回",
                    occurredAtEpochMillis = T0 + 30_000,
                    voidTarget = true,
                ),
            )
            assertEquals(AnswerKeyCorrectionStatus.RECORDED, undo.status)
            repository.refresh()
            assertEquals("回退到第一次更正（POSITIVE）", 0.15, fixture.mastery(NODE).successWeight, 1e-9)
            assertEquals(0.0, fixture.mastery(NODE).failureWeight, 1e-9)
        }
    }

    @Test
    fun aDuplicateCorrectionRequestReturnsTheSameReceiptWithoutASecondLedgerEvent() = runBlocking {
        withFixture { database, repository, fixture ->
            fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "B")
            repository.refresh()
            val target = database.readChatEvidenceByLearner(LEARNER_ID)
                .single { it.source_kind == "KNOWLEDGE_QUIZ" }
            val command = fixture.correctionCommand(
                requestId = "fix-key-once",
                target = target.evidence_id,
                replacementCorrect = true,
                at = T0 + 10_000,
            )
            val writer = fixture.correctionWriter()

            val first = writer.submit(command)
            val ledgerAfterFirst = database.loadLearningLedger(LEARNER_ID)
            val replay = writer.submit(command)
            val ledgerAfterReplay = database.loadLearningLedger(LEARNER_ID)

            assertEquals(first.correctionId, replay.correctionId)
            assertEquals(AnswerKeyCorrectionStatus.REPLAYED, replay.status)
            assertEquals(
                "重复请求不得追加第二笔更正事件",
                ledgerAfterFirst.validPrefix.size,
                ledgerAfterReplay.validPrefix.size,
            )
            assertEquals(
                1,
                database.readChatEvidenceByLearner(LEARNER_ID)
                    .count { it.corrects_evidence_id == target.evidence_id },
            )
        }
    }

    @Test
    fun aCorrectionDoesNotHarmOtherAnswersOnTheSameNodeOrAnotherNode() = runBlocking {
        withFixture { database, repository, fixture ->
            fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "B", sessionId = SESSION_ID)
            fixture.submitAnswer(
                correctChoiceId = "A",
                selectedChoiceId = "A",
                sessionId = SESSION_ID + 1,
                nodeId = NODE,
            )
            fixture.submitAnswer(
                correctChoiceId = "A",
                selectedChoiceId = "A",
                sessionId = SESSION_ID + 2,
                nodeId = OTHER_NODE,
            )
            repository.refresh()
            val target = database.readChatEvidenceByLearner(LEARNER_ID)
                .first { it.knowledge_node_id == NODE && it.direction == "NEGATIVE" }
            val otherNodeBefore = fixture.mastery(OTHER_NODE)

            fixture.correctionWriter().submit(
                fixture.correctionCommand(
                    requestId = "fix-key-isolated",
                    target = target.evidence_id,
                    replacementCorrect = true,
                    at = T0 + 10_000,
                ),
            )
            repository.refresh()

            assertEquals(
                "同 KC 的另一笔正确作答仍在：两笔 POSITIVE 0.15 累加",
                0.30,
                fixture.mastery(NODE).successWeight,
                1e-9,
            )
            assertEquals("其他 KC 不受误伤", otherNodeBefore, fixture.mastery(OTHER_NODE))
        }
    }

    @Test
    fun aCorrectionCommittedBeforeDrainIsReplayedAfterAProcessKill() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "answer-key-correction-kill-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        var database: StudyDatabasePort? = null
        var scope: CoroutineScope? = null
        try {
            database = StudyDatabaseFactory.open(context, databaseName)
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            var repository = RoomBackedStudyExperienceRepository(
                database = database,
                applicationScope = scope,
            )
            seed(context, database, databaseName)
            val fixture = Fixture(database, repository)
            // 这道题要进入账本才能测“纠正事件活过杀进程”；未核验键只落 rejected
            // 观察行，纠错没有账本目标。先把题面键标为已核验。
            fixture.markKeyVerified()
            fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "B")
            repository.refresh()
            val target = database.readChatEvidenceByLearner(LEARNER_ID)
                .single { it.source_kind == "KNOWLEDGE_QUIZ" }
            fixture.correctionWriter().submit(
                fixture.correctionCommand(
                    requestId = "fix-key-before-kill",
                    target = target.evidence_id,
                    replacementCorrect = true,
                    at = T0 + 10_000,
                ),
            )
            // 杀掉"进程"：没有 drain，直接关库。
            repository.close()
            scope.cancel()
            database.close()
            database = null
            scope = null

            // 重开：未完成的重放由 drainer 看到更正事件后整本重放。
            val reopenedDatabase = StudyDatabaseFactory.open(context, databaseName)
            val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            database = reopenedDatabase
            scope = reopenedScope
            repository = RoomBackedStudyExperienceRepository(
                database = reopenedDatabase,
                applicationScope = reopenedScope,
            )
            repository.refresh()
            // refresh 触发的 drain 是异步的：进程刚重建时先等到投影真正追上，
            // 不能把“尚未提交”误判成“更正丢失”。
            val correctedSnapshot = withTimeout(15_000L) {
                var snapshot = reopenedDatabase
                    .readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID)
                while (snapshot == null) {
                    delay(50L)
                    snapshot = reopenedDatabase
                        .readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID)
                }
                checkNotNull(snapshot)
            }
            val corrected = correctedSnapshot.snapshot.knowledgeMasteryStates.getValue(NODE)
            assertEquals(0.15, corrected.successWeight, 1e-9)
            assertEquals(0.0, corrected.failureWeight, 1e-9)
            assertTrue(
                "更正事件与回执一起活过了进程被杀",
                reopenedDatabase.loadLearningLedger(LEARNER_ID).validPrefix.any {
                    it.event.ledgerEventId.startsWith("key-correction:fix-key-before-kill:")
                } ||
                    reopenedDatabase.readChatEvidenceByLearner(LEARNER_ID).any {
                        it.corrects_evidence_id == target.evidence_id
                    },
            )
        } finally {
            scope?.cancel()
            database?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun aLateVerdictAgainstTheOldKeyIsRejectedAfterTheKeyWasCorrected() = runBlocking {
        withFixture { database, repository, fixture ->
            val wrong = fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "B")
            assertTrue(wrong.evidenceRecorded)
            repository.refresh()
            val target = database.readChatEvidenceByLearner(LEARNER_ID)
                .single { it.source_kind == "KNOWLEDGE_QUIZ" }
            fixture.correctionWriter().submit(
                fixture.correctionCommand(
                    requestId = "fix-key-late",
                    target = target.evidence_id,
                    replacementCorrect = true,
                    at = T0 + 10_000,
                ),
            )
            repository.refresh()

            // 旧键判分迟到：同一身份、仍用旧键 A 提交。
            val late = fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "B")
            assertFalse("旧键迟到不得再生成新事实", late.evidenceRecorded)
            assertEquals("ANSWER_KEY_VERSION_STALE", late.rejectedReason)
            assertEquals(
                "不得凭迟到判分追加账本事实",
                2,
                database.loadLearningLedger(LEARNER_ID).validPrefix.size,
            )
            assertEquals(0.15, fixture.mastery(NODE).successWeight, 1e-9)
        }
    }

    @Test
    fun anUnverifiedGeneratedKeyKeepsTheRealChoiceAsAnObservationWithoutNumericEvidence() = runBlocking {
        withFixture(markKeyVerified = false) { database, repository, fixture ->
            val result = fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "A")

            assertTrue("会话反馈仍然给出真实对错", result.isCorrect)
            assertFalse("未核验键不得新增数值掌握证据", result.evidenceRecorded)
            assertEquals("ANSWER_KEY_UNVERIFIED", result.rejectedReason)
            repository.refresh()
            assertTrue(
                "只有观察行，没有 KC 掌握态",
                database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID)
                    ?.snapshot
                    ?.knowledgeMasteryStates
                    .orEmpty()
                    .isEmpty(),
            )
            val row = database.readChatEvidenceByLearner(LEARNER_ID).single()
            assertEquals("ANSWER_KEY_UNVERIFIED", row.rejected_reason)
            assertEquals(0.0, row.weight, 1e-9)
            assertTrue("真实选择留在理由里", row.reason_markdown.contains("A"))
        }
    }

    @Test
    fun aNeutralPreAnswerDisputePausesTheKeyWithoutLeakingOrScoringIt() = runBlocking {
        withFixture { database, repository, fixture ->
            val command = AnswerKeyCorrectionCommand(
                requestId = "dispute-before-answer",
                knowledgeNodeId = NODE,
                conversationId = "knowledge-quiz-review:dispute",
                itemId = ITEM_ID,
                voidTarget = true,
                reasonMarkdown = "选项表述有歧义",
                occurredAtEpochMillis = T0,
            )
            val dispute = fixture.correctionWriter().submit(command)
            assertEquals(AnswerKeyCorrectionStatus.RECORDED, dispute.status)
            assertEquals("ANSWER_KEY_DISPUTED", dispute.reason)
            assertFalse("预报告不落账本", dispute.replayPending)

            val replay = fixture.correctionWriter().submit(command)
            assertEquals("重复预报告按身份幂等", AnswerKeyCorrectionStatus.REPLAYED, replay.status)
            assertEquals(
                1,
                database.readChatEvidenceByLearner(LEARNER_ID)
                    .count { it.evidence_id == "key-dispute:dispute-before-answer" },
            )

            val result = fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "A")
            assertTrue(result.isCorrect)
            assertFalse("争议键暂停计分", result.evidenceRecorded)
            assertEquals("ANSWER_KEY_DISPUTED", result.rejectedReason)
            repository.refresh()
            assertTrue(
                "争议键不生成数值掌握证据",
                database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID)
                    ?.snapshot
                    ?.knowledgeMasteryStates
                    .orEmpty()
                    .isEmpty(),
            )
        }
    }

    @Test
    fun aPostAnswerIssueOnAnUnverifiedKeyIsRecordedAsDisputeInsteadOfDropped() = runBlocking {
        withFixture(markKeyVerified = false) { database, repository, fixture ->
            val result = fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "A")
            assertFalse(result.evidenceRecorded)
            assertEquals("ANSWER_KEY_UNVERIFIED", result.rejectedReason)

            // UI 在“已作答”状态只会把 selectedChoiceId 一起传来；该作答没有账本证据，
            // 但反馈仍然必须被记下：回落成争议观察行，而不是 ANSWER_KEY_TARGET_NOT_FOUND。
            val receipt = fixture.correctionWriter().submit(
                AnswerKeyCorrectionCommand(
                    requestId = "post-answer-issue-unverified",
                    knowledgeNodeId = NODE,
                    conversationId = fixture.conversationId,
                    itemId = ITEM_ID,
                    selectedChoiceId = "A",
                    voidTarget = true,
                    reasonMarkdown = "答案键好像不对",
                    occurredAtEpochMillis = T0 + 10_000,
                ),
            )
            assertEquals(AnswerKeyCorrectionStatus.RECORDED, receipt.status)
            assertEquals(REJECT_ANSWER_KEY_DISPUTED, receipt.reason)
            assertFalse("争议观察不是账本更正", receipt.replayPending)

            val rows = database.readChatEvidenceByLearner(LEARNER_ID)
            assertTrue(
                "反馈必须留下争议观察行",
                rows.any { it.evidence_id.startsWith("key-dispute:") },
            )
            val observation = rows.single { it.source_kind == "KNOWLEDGE_QUIZ" }
            assertEquals(
                "原观察行一字不改（仍是 UNVERIFIED 的 rejected 行）",
                "ANSWER_KEY_UNVERIFIED",
                observation.rejected_reason,
            )
            assertTrue(
                "未核验键的反馈不得伪造账本事实",
                database.loadLearningLedger(LEARNER_ID).validPrefix.isEmpty(),
            )
        }
    }

    @Test
    fun aSecondIssueReportTargetsTheOriginalAnswerInsteadOfTheFirstVoid() = runBlocking {
        withFixture { database, repository, fixture ->
            fixture.submitAnswer(correctChoiceId = "A", selectedChoiceId = "B")
            repository.refresh()
            val target = database.readChatEvidenceByLearner(LEARNER_ID)
                .single { it.source_kind == "KNOWLEDGE_QUIZ" }
            val writer = fixture.correctionWriter()
            fun report(requestId: String, reason: String) = AnswerKeyCorrectionCommand(
                requestId = requestId,
                knowledgeNodeId = NODE,
                conversationId = fixture.conversationId,
                itemId = ITEM_ID,
                selectedChoiceId = "B",
                voidTarget = true,
                reasonMarkdown = reason,
                occurredAtEpochMillis = T0 + 10_000,
            )

            val first = writer.submit(report("issue-first", "选项 B 其实也对"))
            // 第二次换理由：requestId 变（同一意图的新一笔），但目标仍必须是**原始作答**。
            // 若隐式定位把第一条 KEY_VOID 当成目标，第一条撤销会失效、原错误键重新计分。
            val second = writer.submit(report("issue-second", "题目没有正确答案"))

            assertEquals(AnswerKeyCorrectionStatus.RECORDED, first.status)
            assertEquals(AnswerKeyCorrectionStatus.RECORDED, second.status)
            assertEquals(
                "第二次报告必须指向原作答，而不是第一条撤销行",
                target.evidence_id,
                second.targetEvidenceId,
            )
            repository.refresh()
            assertNull(
                "两次撤销后原错误键仍必须被排除，不能回到掌握度",
                database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID)!!
                    .snapshot.knowledgeMasteryStates[NODE],
            )
            assertEquals(
                "两次报告各留一条争议观察行",
                2,
                database.readChatEvidenceByLearner(LEARNER_ID)
                    .count { it.rejected_reason == REJECT_ANSWER_KEY_DISPUTED },
            )
        }
    }

    // ---- fixture ----

    private class Fixture(
        val database: StudyDatabasePort,
        val repository: RoomBackedStudyExperienceRepository,
    ) {
        val conversationId = "knowledge-quiz-review:$SESSION_ID"

        fun correctionWriter() = AnswerKeyCorrectionWriter(database, LEARNER_ID)
    }

    private suspend fun withFixture(
        markKeyVerified: Boolean = true,
        block: suspend (StudyDatabasePort, RoomBackedStudyExperienceRepository, Fixture) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "answer-key-correction-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
        )
        try {
            seed(context, database, databaseName)
            val fixture = Fixture(database, repository)
            if (markKeyVerified) fixture.markKeyVerified()
            block(database, repository, fixture)
        } finally {
            repository.close()
            applicationScope.cancel()
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun seed(
        context: Context,
        database: StudyDatabasePort,
        databaseName: String,
    ) {
        seedStudyFacts(
            context = context,
            database = database,
            databaseName = databaseName,
            bundle = StudySeedBundle(
                problems = emptyList(),
                revisions = emptyList(),
                practiceUnits = emptyList(),
                errorBookEntries = emptyList(),
                knowledgeNodes = listOf(NODE, OTHER_NODE).map { nodeId ->
                    KnowledgeNodeSeedRecord(
                        knowledgeNodeId = nodeId,
                        stableCode = "$TAXONOMY_VERSION:math:atomic:$nodeId",
                        subject = "MATH",
                        displayName = nodeId,
                        parentKnowledgeNodeId = null,
                        taxonomyVersion = TAXONOMY_VERSION,
                        createdAtEpochMillis = 1_000,
                        canonicalName = nodeId,
                        nodeKind = KnowledgeNodeKind.REASONING.name,
                        granularity = KnowledgeNodeGranularity.ATOMIC.name,
                        verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
                    )
                },
            ),
        )
    }

    private suspend fun Fixture.markKeyVerified() {
        persistItem(SESSION_ID, NODE)
        persistItem(SESSION_ID + 1, NODE)
        persistItem(SESSION_ID + 2, OTHER_NODE)
        (listOf(SESSION_ID to NODE, SESSION_ID + 1 to NODE, SESSION_ID + 2 to OTHER_NODE)).forEach { (session, node) ->
            val snapshotId = knowledgeQuizItemSnapshotId(LEARNER_ID, session, node)
            val record = database.findAssessmentItemSnapshot(snapshotId)
                ?: error("Missing quiz item snapshot $snapshotId")
            database.overwriteAssessmentItemSnapshot(record.copy(verificationStatus = "VERIFIED"))
        }
    }

    private suspend fun Fixture.persistItem(sessionId: Long, nodeId: String) {
        val item = TutorAssessmentItem(
            id = ITEM_ID,
            stemMarkdown = "题干",
            choices = listOf(
                TutorChoice(id = "A", markdown = "选项 A"),
                TutorChoice(id = "B", markdown = "选项 B"),
            ),
            correctChoiceId = "A",
            knowledgeNodeIds = setOf(nodeId),
        )
        repository.persistKnowledgeQuizItem(sessionId = sessionId, knowledgeNodeId = nodeId, item = item)
    }

    private fun Fixture.correctionCommand(
        requestId: String,
        target: String,
        replacementCorrect: Boolean,
        at: Long,
    ) = AnswerKeyCorrectionCommand(
        requestId = requestId,
        knowledgeNodeId = NODE,
        conversationId = conversationId,
        targetEvidenceId = target,
        itemId = ITEM_ID,
        selectedChoiceId = "B",
        newCorrectChoiceId = if (replacementCorrect) "B" else "A",
        replacementCorrect = replacementCorrect,
        reasonMarkdown = "答案键更正",
        occurredAtEpochMillis = at,
    )

    private suspend fun Fixture.submitAnswer(
        correctChoiceId: String,
        selectedChoiceId: String,
        sessionId: Long = SESSION_ID,
        nodeId: String = NODE,
    ) = repository.submitKnowledgeQuizFeedback(
        requestId = "knowledge-quiz:review:$sessionId:$ITEM_ID:$selectedChoiceId",
        knowledgeNodeId = nodeId,
        correctChoiceId = correctChoiceId,
        selectedChoiceId = selectedChoiceId,
        occurredAtEpochMillis = T0,
        conversationId = "knowledge-quiz-review:$sessionId",
    )

    private suspend fun Fixture.mastery(nodeId: String) = database
        .readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID)!!
        .snapshot.knowledgeMasteryStates.getValue(nodeId)

    private companion object {
        const val LEARNER_ID = "learner:local"
        const val NODE = "kc-monotonicity"
        const val OTHER_NODE = "kc-continuity"
        const val ITEM_ID = "knowledge-quiz:item:monotonicity"
        const val SESSION_ID = 1_700_000_000_000L
        const val T0 = 1_700_000_001_000L
        const val TAXONOMY_VERSION = "cn-highschool-m1-v1"
    }
}
