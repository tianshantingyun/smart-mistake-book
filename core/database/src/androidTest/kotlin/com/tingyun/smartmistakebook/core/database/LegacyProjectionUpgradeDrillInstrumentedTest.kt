package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.study.RoomBackedStudyExperienceRepository
import com.tingyun.smartmistakebook.core.domain.LearningCoreVersions
import com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.AttemptCorrection
import com.tingyun.smartmistakebook.core.model.AttemptSubmittedResponse
import com.tingyun.smartmistakebook.core.model.BindingChanged
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotJson
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.TutorAnswerExposureOutcome
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **K2 批 1 · 存量库升级演练（v13 首开 = 先归档再重放）**——真 Room、真 drainer、raw SQL 播种。
 *
 * 既有覆盖里这条链路只被拆成三块、各缺一角：`ProjectionVersionGuardTest`（纯守卫、空账本）、
 * `ProjectionArchiveDrainerTest`（真 drainer 但账本恒空）、`ReplayFingerprintDrainerTest`
 * （旧版 + 真账本但 fake 端口 + 6 事件最小样例）；仪器化的 `ProjectionRollbackDrillInstrumentedTest`
 * 归档的是**当前版本**快照（触发源是修正事件），`FullMigrationMatrixInstrumentedTest` 用空库。
 * **不存在**「真 Room 库躺旧版投影 + 真账本 → 当前代码打开 → 先归档再重放」的演练——本用例补它。
 *
 * 库形态 = 上一台二进制（v11 复合串）留下的存量库**投影态**（库本身按当前 63 schema 建；schema
 * 迁移腿由 `FullMigrationMatrixInstrumentedTest` 覆盖，本用例只演练**投影版本**这条腿）：
 * - `learner_projection_snapshot` 旧版本头（checkpoint 追平账本头 5）+ 一张 v11 口径记忆卡；
 * - 5 条账本行（4 类：ATTEMPT ×2 / ANSWER_REVEAL_OUTCOME / ATTEMPT_CORRECTION /
 *   CHAT_EVIDENCE_SUBMITTED）+ 2 条 attempt 行 + 分配头 5；
 * - 两张呈现态行（每个 presentation 一行，终局揭示那个带 `terminal_event_sequence`）。
 *
 * 播种全部走 raw SQL（列集逐列对齐当前 `63.json`，外键开启、按依赖序插入；值为测试里构造的
 * 模型事件的规范指纹，读边界会逐行 SHA-256 复算——播种错一位就进 CONFLICT）。随后
 * `StudyDatabaseFactory.open` 打开、用 `RoomBackedStudyExperienceRepository.refresh()`
 * （既有 drill 的驱动方式）触发真实 drainer。断言四件事：
 * ① **先归档**：`projection_archive` 一行、版本=旧版、JSON 可解且逐位等于被替换的那一份；
 * ② 投影快照版本升到当前（`LearningCoreVersions.PROJECTION_COMPOSITE`）；
 * ③ **重放确定性**：再 drain 一次结果一致（幂等，且不再追加归档）；
 * ④ **用户数据零丢失**：账本行 / attempt 行 / 分配头与播种后一致。
 */
@RunWith(AndroidJUnit4::class)
class LegacyProjectionUpgradeDrillInstrumentedTest {

    @Test
    fun anExistingDatabaseWithAV11ProjectionIsArchivedBeforeTheLedgerIsReplayed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "legacy-projection-upgrade-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            // 当前 schema（63）建库后，用 raw SQL 把它变成"旧二进制留下的库"。
            // Room 是惰性打开：先读一次版本强制它把库文件建出来（close 本身不触发建库）。
            StudyDatabaseFactory.open(context, databaseName).use { it.readDatabaseVersion() }
            val ledger = upgradeLedger()
            seedLegacyDatabase(context, databaseName, ledger)
            assertEquals("播种的账本行数", 5, countRows(context, databaseName, "projection_outbox"))
            assertEquals("播种的 attempt 行数", 2, countRows(context, databaseName, "attempt_event"))
            assertEquals("播种的分配头", 5L, readLedgerHead(context, databaseName))

            val store = StudyDatabaseFactory.open(context, databaseName)
            val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repository = RoomBackedStudyExperienceRepository(
                database = store,
                applicationScope = applicationScope,
            )
            try {
                // 排空前：旧版本头 + 旧口径记忆卡（"被替换的那一份"），归档表为空。
                val displaced = requireNotNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER)) {
                    "播种的存量库必须有投影头"
                }
                assertEquals(OLD_VERSION, displaced.snapshot.checkpoint.projectorVersion)
                assertEquals(5L, displaced.snapshot.checkpoint.lastSequence)
                assertTrue(
                    "播种的快照必须有真实结构（一张 v11 记忆卡）：${displaced.snapshot.problemMemoryStates.keys}",
                    displaced.snapshot.problemMemoryStates.containsKey(UNIT_ID),
                )
                assertEquals("还没重放，归档表应为空", 0, archiveCount(context, databaseName))

                // ---- v13 首开：版本不匹配 → 先归档 → 全量重放（真实 drainer） ----
                repository.refresh()

                val afterUpgrade = requireNotNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
                // ① 先归档：一行、版本=旧版、JSON 可解且逐位等于被替换的那一份
                assertEquals("重放必须先产生一条归档行", 1, archiveCount(context, databaseName))
                val archived = readLatestArchiveRow(context, databaseName)
                assertEquals(PROJECTION, archived.projectionName)
                assertEquals(LEARNER, archived.learnerId)
                assertEquals("归档行必须点名被替换快照的旧版本", OLD_VERSION, archived.projectorVersion)
                assertEquals(
                    "归档的必须是被替换的那一份（JSON 往返逐位一致）",
                    displaced.snapshot,
                    LearnerSnapshotJson.decode(archived.snapshotJson),
                )
                assertNotEquals(
                    "归档的不是重放后的新值（否则'改数值可回退'落空）",
                    afterUpgrade.snapshot,
                    LearnerSnapshotJson.decode(archived.snapshotJson),
                )
                assertTrue(
                    "归档行必须带可回退用的投影表 DDL：${archived.schemaDdl.take(80)}",
                    archived.schemaDdl.contains("learner_projection_snapshot"),
                )
                // ② 投影快照版本升到当前
                assertEquals(
                    LearningCoreVersions.PROJECTION_COMPOSITE,
                    afterUpgrade.snapshot.checkpoint.projectorVersion,
                )
                assertNotEquals(OLD_VERSION, afterUpgrade.snapshot.checkpoint.projectorVersion)
                assertEquals("重放把投影追平账本头（checkpoint 5）", 5L, afterUpgrade.snapshot.checkpoint.lastSequence)
                assertEquals(
                    "全量重放的审计窗口必须覆盖两条 attempt",
                    2,
                    afterUpgrade.snapshot.appliedAttemptRecords.size,
                )
                // ③ 重放确定性：再 drain 一次结果一致（幂等），且不再追加归档
                repository.refresh()
                assertEquals(
                    "同一账本重放两次必须得到同一份投影（幂等）",
                    afterUpgrade,
                    requireNotNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER)),
                )
                assertEquals(
                    "第二次排空版本已一致，不追加归档行",
                    1,
                    archiveCount(context, databaseName),
                )
                // ④ 用户数据零丢失：账本行 / attempt 行 / 分配头与播种后一致
                assertEquals(
                    "账本行数不许变",
                    5,
                    countRows(context, databaseName, "projection_outbox"),
                )
                assertEquals(
                    "attempt 行数不许变",
                    2,
                    countRows(context, databaseName, "attempt_event"),
                )
                assertEquals(
                    "学习序列分配头不许变",
                    5L,
                    readLedgerHead(context, databaseName),
                )
            } finally {
                repository.close()
                store.close()
                applicationScope.cancel()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    // ---- raw SQL 播种（列集逐列对齐 core/database/schemas/63.json） ----

    private fun seedLegacyDatabase(
        context: Context,
        databaseName: String,
        ledger: UpgradeLedger,
    ) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
            // 题 / 修订 / 题卡 / 知识点 / 绑定：证据快照与归因的外键父行。
            connection.insert(
                INSERT_PROBLEM,
                PROBLEM_ID, "fp-upgrade-problem", "MATH", SEED_AT, null,
            )
            connection.insert(
                INSERT_REVISION,
                REVISION_ID, PROBLEM_ID, 1, "升级演练题", "题面",
                null, ANSWER_SPEC_ID, "B", "VERIFIED", "TEST_LOCAL", null,
                "fp-upgrade-revision", SEED_AT,
            )
            connection.insert(
                INSERT_PRACTICE_UNIT,
                UNIT_ID, PROBLEM_ID, REVISION_ID, "whole", "WHOLE_PROBLEM",
                "升级演练题", "题面", 120, SEED_AT,
            )
            connection.insert(
                INSERT_KNOWLEDGE_NODE,
                KNOWLEDGE_ID, "upgrade.drill.kc", "MATH", "升级演练知识点", "",
                "TOPIC", "TOPIC", "", null, "MODEL_CANDIDATE", null,
                TAXONOMY_VERSION, SEED_AT, "ACTIVE", null,
            )
            connection.insert(
                INSERT_BINDING,
                BINDING_ID, UNIT_ID, KNOWLEDGE_ID, REVISION_ID,
                1.0, "VERIFIED", TAXONOMY_VERSION, SEED_AT,
            )
            // 旧二进制留下的投影头 + 一张 v11 口径记忆卡（被归档的那一份）。
            connection.insert(
                INSERT_PROJECTION_HEADER,
                PROJECTION, LEARNER, 1L, 5L, 5L, OLD_VERSION,
                SEED_AT + 300_000L, SEED_AT + 300_000L, null, "CURRENT", "CURRENT",
            )
            connection.insert(
                INSERT_MEMORY_STATE,
                PROJECTION, LEARNER, UNIT_ID, 12.5, 7.0,
                SEED_AT + 60_000L, SEED_AT + 86_400_000L,
                1L, 0L, 0L, 0L, null, 0L, null,
                OLD_VERSION, 1L, "INDEPENDENT_CORRECT", "POSITIVE", 0L, 0L,
            )
            // 证据快照 + 归因（attempt/reveal 的 FK 父行，也是规范指纹的一部分）。
            listOf(ledger.attemptA, ledger.attemptB).forEach { attempt ->
                val snapshot = attempt.assessmentSnapshot
                connection.insert(
                    INSERT_EVIDENCE_SNAPSHOT,
                    snapshot.snapshotId, snapshot.assessmentItemId, snapshot.practiceUnitId,
                    snapshot.problemRevisionId, snapshot.answerSpecId, snapshot.itemFamilyId,
                    snapshot.sourceBundleId, snapshot.taxonomyVersion,
                    snapshot.verification.name, snapshot.calibration.support.name,
                    snapshot.calibration.sourceId, snapshot.calibration.version,
                    snapshot.calibration.validFromEpochMillis,
                    snapshot.calibration.validUntilEpochMillis,
                    snapshot.capturedAtEpochMillis,
                )
                snapshot.attributions.forEach { attribution ->
                    connection.insert(
                        INSERT_ATTRIBUTION,
                        snapshot.snapshotId, attribution.bindingId, snapshot.practiceUnitId,
                        attribution.knowledgeNodeId, attribution.weight,
                        attribution.basisRevisionId, attribution.taxonomyVersion,
                        attribution.role.name, attribution.certainty.name,
                    )
                }
            }
            // attempt 行（载荷列逐列 = 模型事件的实体映射回写）。
            connection.insert(INSERT_SUBMISSION, ledger.attemptA.submissionId(), LEARNER, "payload:upgrade:1")
            connection.insert(INSERT_SUBMISSION, ledger.attemptB.submissionId(), LEARNER, "payload:upgrade:2")
            connection.insert(INSERT_ATTEMPT_EVENT, *ledger.attemptA.toRow())
            connection.insert(INSERT_ATTEMPT_EVENT, *ledger.attemptB.toRow())
            // 修正行。
            connection.insert(
                INSERT_CORRECTION,
                ledger.correction.correctionId, LEARNER, ledger.attemptB.submissionId(),
                ledger.correction.attemptId, ledger.correction.eventSequence,
                LearningLedgerFingerprint.correction(ledger.correction),
                ledger.correction.replacementEvidence.direction.name,
                ledger.correction.replacementEvidence.weight,
                ledger.correction.replacementEvidence.reason.name,
                ledger.correction.replacementMemoryOutcome.name,
                ledger.correction.reasonMarkdown, ledger.correction.occurredAtEpochMillis,
            )
            // 揭示事件 + 结局行（同一 presentation 的终局）。
            val reveal = ledger.revealA
            connection.insert(
                INSERT_REVEAL_EVENT,
                REVEAL_EVENT_ID, LEARNER, reveal.outcomeId, reveal.presentationId,
                reveal.assessmentSnapshot.snapshotId, "完整答案与推导",
                reveal.occurredAtEpochMillis, reveal.studyDay.epochDay,
                reveal.studyDay.timeZoneId, reveal.studyDay.utcOffsetMinutes,
            )
            connection.insert(
                INSERT_REVEAL_OUTCOME,
                reveal.outcomeId, LEARNER, REVEAL_EVENT_ID, reveal.presentationId,
                reveal.assessmentSnapshot.snapshotId, reveal.eventSequence,
                LearningLedgerFingerprint.answerReveal(reveal),
                reveal.occurredAtEpochMillis, reveal.studyDay.epochDay,
                reveal.studyDay.timeZoneId, reveal.studyDay.utcOffsetMinutes,
            )
            // 呈现行 + 呈现投影态（全量重放提交侧会逐行比对，必须与重放输出同形）。
            connection.insert(
                INSERT_PRESENTATION,
                LEARNER, ledger.attemptA.presentationId, ledger.attemptA.assessmentSnapshot.snapshotId,
                1L, 1L, 1L, SEED_AT + 60_000L,
            )
            connection.insert(
                INSERT_PRESENTATION,
                LEARNER, ledger.attemptB.presentationId, ledger.attemptB.assessmentSnapshot.snapshotId,
                1L, 0L, 1L, SEED_AT + 180_000L,
            )
            connection.insert(
                INSERT_PRESENTATION_STATE,
                PROJECTION, LEARNER, ledger.attemptA.presentationId,
                reveal.outcomeId, ProblemMemoryOutcome.ANSWER_REVEALED.name, reveal.eventSequence,
                1L, reveal.eventSequence, 1L, 2L,
            )
            connection.insert(
                INSERT_PRESENTATION_STATE,
                PROJECTION, LEARNER, ledger.attemptB.presentationId,
                null, null, null, 1L, ledger.attemptB.eventSequence, 1L, 1L,
            )
            // 模型证据行（无外键父行）。
            connection.insert(
                INSERT_CHAT_EVIDENCE,
                ledger.chat.evidenceId, LEARNER, ledger.chat.conversationId,
                ledger.chat.knowledgeNodeId, ledger.chat.direction.name, ledger.chat.weight,
                ledger.chat.reasonMarkdown, ledger.chat.confidence, "MODEL_CHAT",
                ledger.chat.occurredAtEpochMillis, null, null, null,
            )
            // 账本 outbox（身份三连 = 事件 id / 序号 / 规范指纹）+ 分配头。
            ledger.events.forEach { event ->
                connection.insert(
                    INSERT_OUTBOX,
                    "learning-outbox:$LEARNER:${eventKindOf(event)}:${event.ledgerEventId}",
                    LEARNER, event.eventSequence, eventKindOf(event), event.ledgerEventId,
                    LearningLedgerFingerprint.event(event), "PENDING", event.occurredAtEpochMillis,
                )
            }
            connection.insert(INSERT_LEARNING_SEQUENCE, LEARNER, 5L)
        } finally {
            connection.close()
        }
    }

    // ---- 夹具：v11 存量库的账本（4 类 5 条）+ 事件行映射 ----

    private fun upgradeLedger(): UpgradeLedger {
        val occurredA = SEED_AT + 60_000L
        val occurredB = SEED_AT + 180_000L
        val attemptA = Attempt(
            attemptId = ATTEMPT_A,
            presentationId = PRESENTATION_A,
            responseOrdinal = 1,
            assessmentSnapshot = evidenceSnapshot(SNAPSHOT_A, "item:upgrade:1", "family:upgrade:1"),
            evidence = LearningEvidence(
                direction = LearningEvidenceDirection.POSITIVE,
                weight = 1.0,
                reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
            ),
            problemMemoryOutcome = ProblemMemoryOutcome.INDEPENDENT_RECALL,
            occurredAtEpochMillis = occurredA,
            durationSeconds = 45,
            studyDay = studyDay(occurredA),
            eventSequence = 1,
            submittedResponse = AttemptSubmittedResponse.Choice(
                choiceId = "B",
                choiceMarkdown = "同时比较驻点与端点",
                submittedAtEpochMillis = occurredA,
            ),
        )
        val revealA = AnswerRevealOutcome(
            outcomeId = REVEAL_OUTCOME_ID,
            presentationId = PRESENTATION_A,
            assessmentSnapshot = attemptA.assessmentSnapshot,
            occurredAtEpochMillis = SEED_AT + 120_000L,
            studyDay = studyDay(SEED_AT + 120_000L),
            eventSequence = 2,
        )
        val attemptB = Attempt(
            attemptId = ATTEMPT_B,
            presentationId = PRESENTATION_B,
            responseOrdinal = 1,
            assessmentSnapshot = evidenceSnapshot(SNAPSHOT_B, "item:upgrade:2", "family:upgrade:2"),
            evidence = LearningEvidence(
                direction = LearningEvidenceDirection.POSITIVE,
                weight = 1.0,
                reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
            ),
            problemMemoryOutcome = ProblemMemoryOutcome.INDEPENDENT_RECALL,
            occurredAtEpochMillis = occurredB,
            durationSeconds = 60,
            studyDay = studyDay(occurredB),
            eventSequence = 3,
            submittedResponse = AttemptSubmittedResponse.Choice(
                choiceId = "A",
                choiceMarkdown = "只比较端点",
                submittedAtEpochMillis = occurredB,
            ),
        )
        val correction = AttemptCorrection(
            correctionId = CORRECTION_ID,
            attemptId = ATTEMPT_B,
            replacementEvidence = LearningEvidence(
                direction = LearningEvidenceDirection.NEGATIVE,
                weight = 1.0,
                reason = LearningEvidenceReason.INDEPENDENT_INCORRECT,
            ),
            replacementMemoryOutcome = ProblemMemoryOutcome.RETRIEVAL_FAILURE,
            reasonMarkdown = "复核发现首次判分有误",
            occurredAtEpochMillis = SEED_AT + 240_000L,
            eventSequence = 4,
        )
        val chat = ChatEvidenceSubmitted(
            evidenceId = CHAT_EVIDENCE_ID,
            conversationId = "conversation:upgrade",
            knowledgeNodeId = KNOWLEDGE_ID,
            direction = LearningEvidenceDirection.POSITIVE,
            weight = ChatEvidenceSubmitted.POSITIVE_WEIGHT,
            reasonMarkdown = "模型判断：该生已掌握。",
            confidence = 0.9,
            occurredAtEpochMillis = SEED_AT + 300_000L,
            eventSequence = 5,
        )
        return UpgradeLedger(
            attemptA = attemptA,
            revealA = revealA,
            attemptB = attemptB,
            correction = correction,
            chat = chat,
        )
    }

    private fun evidenceSnapshot(
        snapshotId: String,
        assessmentItemId: String,
        itemFamilyId: String,
    ) = AssessmentEvidenceSnapshot(
        snapshotId = snapshotId,
        assessmentItemId = assessmentItemId,
        practiceUnitId = UNIT_ID,
        problemRevisionId = REVISION_ID,
        answerSpecId = ANSWER_SPEC_ID,
        itemFamilyId = itemFamilyId,
        sourceBundleId = "bundle:upgrade",
        taxonomyVersion = TAXONOMY_VERSION,
        verification = AssessmentSnapshotVerification.VERIFIED,
        calibration = CalibrationSnapshot(
            support = CalibrationSupport.SUPPORTED,
            sourceId = "calibration:upgrade",
            version = "calibration-v1",
            validFromEpochMillis = 0,
            validUntilEpochMillis = CALIBRATION_VALID_UNTIL,
        ),
        attributions = listOf(
            KnowledgeEvidenceAttribution(
                bindingId = BINDING_ID,
                knowledgeNodeId = KNOWLEDGE_ID,
                weight = 1.0,
                basisRevisionId = REVISION_ID,
                taxonomyVersion = TAXONOMY_VERSION,
                role = EvidenceAttributionRole.PRIMARY,
                certainty = EvidenceAttributionCertainty.DIRECT,
            ),
        ),
        capturedAtEpochMillis = SEED_AT,
    )

    private fun studyDay(atEpochMillis: Long): StudyDayContext {
        val zoned = Instant.ofEpochMilli(atEpochMillis).atZone(ZoneId.of(ZONE))
        return StudyDayContext(
            epochDay = zoned.toLocalDate().toEpochDay(),
            timeZoneId = ZONE,
            utcOffsetMinutes = zoned.offset.totalSeconds / 60,
        )
    }

    private fun Attempt.submissionId(): String = when (attemptId) {
        ATTEMPT_A -> SUBMISSION_A
        ATTEMPT_B -> SUBMISSION_B
        else -> error("Unexpected attempt id $attemptId")
    }

    /** `attempt_event` 的 25 列绑定值（列序与 `INSERT_ATTEMPT_EVENT` 一一对应）。 */
    private fun Attempt.toRow(): Array<Any?> = arrayOf(
        attemptId,
        LEARNER,
        submissionId(),
        eventSequence,
        LearningLedgerFingerprint.attempt(this),
        presentationId,
        responseOrdinal,
        assessmentSnapshot.snapshotId,
        (submittedResponse as AttemptSubmittedResponse.Choice).choiceId,
        (submittedResponse as AttemptSubmittedResponse.Choice).choiceMarkdown,
        (submittedResponse as AttemptSubmittedResponse.Choice).submittedAtEpochMillis,
        evidence.direction.name,
        evidence.weight,
        evidence.reason.name,
        problemMemoryOutcome.name,
        occurredAtEpochMillis,
        durationSeconds,
        studyDay.epochDay,
        studyDay.timeZoneId,
        studyDay.utcOffsetMinutes,
        0L, // hint_count
        0L, // revealed_before_answer
        null, // error_type
        null, // error_type_confidence
        0L, // low_confidence_correct
    )

    private fun eventKindOf(event: LearningLedgerEvent): String = when (event) {
        is Attempt -> "ATTEMPT"
        is AnswerRevealOutcome -> "ANSWER_REVEAL_OUTCOME"
        is AttemptCorrection -> "ATTEMPT_CORRECTION"
        is ChatEvidenceSubmitted -> "CHAT_EVIDENCE_SUBMITTED"
        is TutorAnswerExposureOutcome -> "TUTOR_ANSWER_EXPOSURE_OUTCOME"
        is BindingChanged -> "BINDING_CHANGED"
    }

    private class UpgradeLedger(
        val attemptA: Attempt,
        val revealA: AnswerRevealOutcome,
        val attemptB: Attempt,
        val correction: AttemptCorrection,
        val chat: ChatEvidenceSubmitted,
    ) {
        val events: List<LearningLedgerEvent>
            get() = listOf(attemptA, revealA, attemptB, correction, chat)
    }

    // ---- 直读探针（演练记录里的"库里到底发生了什么"眼） ----

    private fun archiveCount(context: Context, databaseName: String): Int {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val statement = connection.prepare("SELECT COUNT(*) FROM projection_archive")
            try {
                assertTrue(statement.step())
                return statement.getLong(0).toInt()
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private fun countRows(context: Context, databaseName: String, table: String): Int {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val statement = connection.prepare("SELECT COUNT(*) FROM `$table`")
            try {
                assertTrue(statement.step())
                return statement.getLong(0).toInt()
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private fun readLedgerHead(context: Context, databaseName: String): Long {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val statement = connection.prepare(
                "SELECT last_allocated_sequence FROM learning_sequence WHERE learner_id = ?",
            )
            try {
                statement.bindText(1, LEARNER)
                assertTrue("分配头行必须还在", statement.step())
                return statement.getLong(0)
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private fun readLatestArchiveRow(context: Context, databaseName: String): ArchiveRow {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val statement = connection.prepare(
                "SELECT projection_name, learner_id, archived_at_epoch_millis, snapshot_json, " +
                    "projector_version, schema_ddl FROM projection_archive ORDER BY archive_id DESC LIMIT 1",
            )
            try {
                assertTrue("归档表必须有那一行", statement.step())
                return ArchiveRow(
                    projectionName = requireNotNull(statement.getText(0)),
                    learnerId = requireNotNull(statement.getText(1)),
                    archivedAtEpochMillis = statement.getLong(2),
                    snapshotJson = requireNotNull(statement.getText(3)),
                    projectorVersion = requireNotNull(statement.getText(4)),
                    schemaDdl = requireNotNull(statement.getText(5)),
                )
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private data class ArchiveRow(
        val projectionName: String,
        val learnerId: String,
        val archivedAtEpochMillis: Long,
        val snapshotJson: String,
        val projectorVersion: String,
        val schemaDdl: String,
    )

    private fun SQLiteConnection.insert(sql: String, vararg binds: Any?) {
        val statement = prepare(sql)
        try {
            binds.forEachIndexed { index, value ->
                val position = index + 1
                when (value) {
                    null -> statement.bindNull(position)
                    is String -> statement.bindText(position, value)
                    is Long -> statement.bindLong(position, value)
                    is Int -> statement.bindLong(position, value.toLong())
                    is Double -> statement.bindDouble(position, value)
                    is Boolean -> statement.bindBoolean(position, value)
                    else -> error("Unsupported bind value: $value")
                }
            }
            // INSERT 没有结果行：step() 执行语句并返回 false（出错由驱动抛 SQLiteException）。
            statement.step()
        } finally {
            statement.close()
        }
    }

    private companion object {
        const val LEARNER = "learner:local"
        const val PROJECTION = "study-experience-v1"
        const val PROBLEM_ID = "problem:upgrade"
        const val REVISION_ID = "revision:upgrade"
        const val UNIT_ID = "practice:upgrade:whole"
        const val KNOWLEDGE_ID = "knowledge:upgrade:extremum"
        const val BINDING_ID = "binding:upgrade"
        const val ANSWER_SPEC_ID = "answer-spec:upgrade"
        const val SNAPSHOT_A = "evidence:upgrade:1"
        const val SNAPSHOT_B = "evidence:upgrade:2"
        const val SUBMISSION_A = "submission:upgrade:1"
        const val SUBMISSION_B = "submission:upgrade:2"
        const val ATTEMPT_A = "attempt:upgrade:1"
        const val ATTEMPT_B = "attempt:upgrade:2"
        const val PRESENTATION_A = "presentation:upgrade:1"
        const val PRESENTATION_B = "presentation:upgrade:2"
        const val CORRECTION_ID = "correction:upgrade:1"
        const val REVEAL_EVENT_ID = "reveal-event:upgrade:1"
        const val REVEAL_OUTCOME_ID = "reveal-outcome:upgrade:1"
        const val CHAT_EVIDENCE_ID = "chat-evidence:upgrade:1"
        const val TAXONOMY_VERSION = "taxonomy:upgrade:v1"
        const val ZONE = "Asia/Shanghai"
        const val SEED_AT = 1_768_200_000_000L
        const val CALIBRATION_VALID_UNTIL = SEED_AT + 10_000_000L

        /**
         * v11 时代真实复合串（与 `ProjectionRollbackDrillInstrumentedTest` 的
         * `LEGACY_PROJECTOR_VERSION` 同一串）：v13 首开要归档的就是它算出来的投影。
         */
        const val OLD_VERSION =
            "learning-core-v11(projector-v11,evidence-v5,curve-v3,skip-v4,attribution-v3,ledger-v2)"
    }
}

private val INSERT_PROBLEM = """
    INSERT INTO `problem` (`problem_id`, `canonical_fingerprint`, `subject`,
        `created_at_epoch_millis`, `archived_at_epoch_millis`) VALUES (?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_REVISION = """
    INSERT INTO `problem_revision` (`revision_id`, `problem_id`, `revision_number`, `title`,
        `problem_markdown`, `question_document_snapshot`, `answer_spec_id`, `answer_spec_snapshot`,
        `answer_verification_status`, `source_type`, `source_reference`, `content_fingerprint`,
        `created_at_epoch_millis`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_PRACTICE_UNIT = """
    INSERT INTO `practice_unit` (`practice_unit_id`, `problem_id`, `problem_revision_id`, `unit_key`,
        `unit_kind`, `title`, `prompt_markdown`, `estimated_seconds`, `created_at_epoch_millis`)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_KNOWLEDGE_NODE = """
    INSERT INTO `knowledge_node` (`knowledge_node_id`, `stable_code`, `subject`, `display_name`,
        `canonical_name`, `node_kind`, `granularity`, `aliases_text`, `boundary_markdown`,
        `verification_status`, `parent_knowledge_node_id`, `taxonomy_version`,
        `created_at_epoch_millis`, `status`, `superseded_by`)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_BINDING = """
    INSERT INTO `practice_unit_knowledge_binding` (`binding_id`, `practice_unit_id`,
        `knowledge_node_id`, `basis_revision_id`, `strength`, `source_type`, `taxonomy_version`,
        `accepted_at_epoch_millis`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_PROJECTION_HEADER = """
    INSERT INTO `learner_projection_snapshot` (`projection_name`, `learner_id`, `state_version`,
        `checkpoint_sequence`, `known_ledger_head_sequence`, `projector_version`,
        `projected_at_epoch_millis`, `generated_at_epoch_millis`, `correction_watermark_epoch_millis`,
        `freshness`, `projection_status`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_MEMORY_STATE = """
    INSERT INTO `learner_problem_memory_state` (`projection_name`, `learner_id`, `practice_unit_id`,
        `stability_days`, `difficulty`, `last_reviewed_at_epoch_millis`,
        `next_review_at_epoch_millis`, `independent_correct_count`, `assisted_correct_count`,
        `lapse_count`, `answer_reveal_count`, `last_lapse_at_epoch_millis`, `clock_anomaly_count`,
        `last_clock_anomaly_at_epoch_millis`, `projector_version`, `checkpoint_sequence`,
        `last_evidence_reason`, `last_evidence_direction`, `consecutive_cross_day_success`,
        `consecutive_cross_day_again`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_EVIDENCE_SNAPSHOT = """
    INSERT INTO `assessment_evidence_snapshot` (`snapshot_id`, `assessment_item_id`,
        `practice_unit_id`, `problem_revision_id`, `answer_spec_id`, `item_family_id`,
        `source_bundle_id`, `taxonomy_version`, `verification`, `calibration_support`,
        `calibration_source_id`, `calibration_version`, `calibration_valid_from_epoch_millis`,
        `calibration_valid_until_epoch_millis`, `captured_at_epoch_millis`)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_ATTRIBUTION = """
    INSERT INTO `assessment_evidence_attribution` (`snapshot_id`, `binding_id`, `practice_unit_id`,
        `knowledge_node_id`, `weight`, `basis_revision_id`, `taxonomy_version`, `role`, `certainty`)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_SUBMISSION = """
    INSERT INTO `attempt_submission` (`submission_id`, `learner_id`, `payload_fingerprint`)
    VALUES (?, ?, ?)
""".trimIndent()

private val INSERT_ATTEMPT_EVENT = """
    INSERT INTO `attempt_event` (`attempt_id`, `learner_id`, `submission_id`, `event_sequence`,
        `canonical_fingerprint`, `presentation_id`, `response_ordinal`, `assessment_snapshot_id`,
        `submitted_choice_id`, `submitted_choice_markdown`, `response_submitted_at_epoch_millis`,
        `evidence_direction`, `evidence_weight`, `evidence_reason`, `problem_memory_outcome`,
        `occurred_at_epoch_millis`, `duration_seconds`, `study_day_epoch_day`,
        `study_day_time_zone_id`, `study_day_utc_offset_minutes`, `hint_count`,
        `revealed_before_answer`, `error_type`, `error_type_confidence`, `low_confidence_correct`)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_CORRECTION = """
    INSERT INTO `attempt_correction` (`correction_id`, `learner_id`, `submission_id`, `attempt_id`,
        `event_sequence`, `canonical_fingerprint`, `replacement_evidence_direction`,
        `replacement_evidence_weight`, `replacement_evidence_reason`, `replacement_memory_outcome`,
        `reason_markdown`, `occurred_at_epoch_millis`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_REVEAL_EVENT = """
    INSERT INTO `assessment_answer_reveal_event` (`assessment_event_id`, `learner_id`, `outcome_id`,
        `presentation_id`, `assessment_snapshot_id`, `content_markdown`, `occurred_at_epoch_millis`,
        `study_day_epoch_day`, `study_day_time_zone_id`, `study_day_utc_offset_minutes`)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_REVEAL_OUTCOME = """
    INSERT INTO `answer_reveal_outcome` (`outcome_id`, `learner_id`, `assessment_event_id`,
        `presentation_id`, `assessment_snapshot_id`, `event_sequence`, `canonical_fingerprint`,
        `occurred_at_epoch_millis`, `study_day_epoch_day`, `study_day_time_zone_id`,
        `study_day_utc_offset_minutes`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_PRESENTATION = """
    INSERT INTO `assessment_presentation` (`learner_id`, `presentation_id`,
        `assessment_snapshot_id`, `last_response_ordinal`, `terminal`, `state_version`,
        `updated_at_epoch_millis`) VALUES (?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_PRESENTATION_STATE = """
    INSERT INTO `presentation_projection_state` (`projection_name`, `learner_id`, `presentation_id`,
        `terminal_outcome_id`, `terminal_outcome`, `terminal_event_sequence`, `memory_projected`,
        `memory_projection_sequence`, `last_response_ordinal`, `state_version`)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_CHAT_EVIDENCE = """
    INSERT INTO `learner_chat_evidence` (`evidence_id`, `learner_id`, `conversation_id`,
        `knowledge_node_id`, `direction`, `weight`, `reason_markdown`, `confidence`, `source_kind`,
        `created_at_epoch_millis`, `rejected_reason`, `rejected_at_epoch_millis`, `anchor_class`)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_OUTBOX = """
    INSERT INTO `projection_outbox` (`outbox_id`, `learner_id`, `outbox_sequence`, `event_kind`,
        `event_id`, `canonical_fingerprint`, `status`, `created_at_epoch_millis`)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_LEARNING_SEQUENCE = """
    INSERT INTO `learning_sequence` (`learner_id`, `last_allocated_sequence`) VALUES (?, ?)
""".trimIndent()
