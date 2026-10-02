package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.study.RoomBackedStudyExperienceRepository
import com.tingyun.smartmistakebook.core.database.dao.PROJECTION_ARCHIVE_TABLES
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.AttemptSubmittedResponse
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotJson
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **回退工具端到端演练（真 Room + 真 drainer）**：把 `docs/research/kernel-projection-rollback.md`
 * §4 的全流程走一遍，并把每一步交给本批新增的工具/接口。
 *
 * 主链路刻意不 mock 任何事务/DAO：
 *
 * 1. 写账本（`recordAttempt`）→ 投影（`RoomBackedStudyExperienceRepository.refresh()` 驱动
 *    真实 `StudyProjectionDrainer` 做增量提交）；
 * 2. 追加一条修正事件（`appendAttemptCorrection`）→ 再排空：drainer 判定
 *    `FULL_REPLAY_REQUIRED`，**先归档旧投影、再全量重放**（`commitFullReplay`）——归档行由
 *    生产代码自己产生，不是测试手写；
 * 3. `readLatestArchivedProjection`（§4-1）→ `restoreArchivedProjection`（§4-2 归档当前 +
 *    §4-3 schema 比对 + §4-4 单事务重建）→ 断言恢复出来的投影与归档时状态**逐位一致**；
 * 4. 再排空（§4-5）→ 断言投影重新前进到账本头、且**被恢复的那份又被归档**（回退本身可逆）。
 *
 * 拒绝面（每条一个用例 + 断言"拒绝不写一行"）：无归档 / 跨版本（含载荷与版本列不一致）/
 * schema_ddl 漂移 / 呈现态超前（目标早于既有终局揭示）/ checkpoint 超前账本头 /
 * 恢复时刻早于目标归档 / 归档 JSON 坏。
 */
@RunWith(AndroidJUnit4::class)
class ProjectionRollbackDrillInstrumentedTest {

    @Test
    fun theRealDrainerArchivesTheDisplacedProjectionAndRestoreReturnsItBitForBit() = runBlocking {
        withDrill { context, databaseName, store, repository ->
            // ---- 写账本 → 投影（真实增量提交） ----
            store.recordAttempt(
                attemptCommand(
                    submissionId = "rollback-submission-1",
                    attemptId = "rollback-attempt-1",
                    presentationId = "rollback-presentation-1",
                ),
            )
            repository.refresh()
            val committed = requireNotNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER)) {
                "首答排空后必须有投影"
            }
            assertEquals(1L, committed.snapshot.checkpoint.lastSequence)
            assertEquals(setOf(KNOWLEDGE_ID), committed.snapshot.knowledgeMasteryStates.keys)
            assertEquals("还没有发生重放，归档表应为空", 0, archiveCount(context, databaseName))

            // ---- 修正事件 → 下一次排空触发全量重放：drainer 先归档旧投影、再重放 ----
            store.appendAttemptCorrection(
                correctionCommand(
                    submissionId = "rollback-submission-1",
                    attemptId = "rollback-attempt-1",
                    correctionId = "rollback-correction-1",
                ),
            )
            repository.refresh()
            val afterReplay = requireNotNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
            assertEquals("重放后投影追到账本头", 2L, afterReplay.snapshot.checkpoint.lastSequence)
            assertEquals(
                "重放前的那份投影被 drainer 归档（顺序即机制）",
                1,
                archiveCount(context, databaseName),
            )
            assertNotEquals(
                "修正事件改变投影输出，归档的那份与重放后的不是同一份",
                committed.snapshot,
                afterReplay.snapshot,
            )

            // ---- §4 第 1 步：定位目标行（最近一份归档） ----
            val target = requireNotNull(store.readLatestArchivedProjection(PROJECTION, LEARNER))
            assertEquals(LearningProjector.VERSION, target.projectorVersion)
            assertEquals(
                "归档行按 '重放前那份' 逐位落库",
                committed.snapshot,
                LearnerSnapshotJson.decode(target.snapshotJson),
            )
            PROJECTION_ARCHIVE_TABLES.forEach { table ->
                assertTrue(
                    "schema_ddl 少了 $table 的建表 SQL：${target.schemaDdl.take(120)}",
                    target.schemaDdl.contains(table),
                )
            }

            // ---- §4 第 2–4 步：回退（restore 内部先归档当前投影，再单事务重建） ----
            // 恢复时刻取"晚于目标归档"：生产里就是 `now`（drainer 用系统时钟归档），
            // 被换下的那份由此成为"最近一份"——连续回退依赖这个单调性。
            val restored = store.restoreArchivedProjection(
                projectionName = PROJECTION,
                learnerId = LEARNER,
                expectedProjectorVersion = LearningProjector.VERSION,
                restoredAtEpochMillis = target.archivedAtEpochMillis + 1,
            )
            assertEquals(
                "state_version 从当前头部递增，不复用旧值",
                afterReplay.stateVersion + 1,
                restored.stateVersion,
            )
            assertEquals(
                "恢复后的投影与归档时状态逐位一致",
                committed.snapshot,
                restored.snapshot,
            )
            assertEquals(
                "读口读回的与恢复返回的必须是同一份",
                restored,
                requireNotNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER)),
            )

            // ---- §4 第 2 步的落点：被换下的当前投影追加归档；既有归档行一字未改 ----
            assertEquals(
                "restore 追加了一行（被换下的当前投影），不是回写/删除",
                2,
                archiveCount(context, databaseName),
            )
            val appended = requireNotNull(store.readLatestArchivedProjection(PROJECTION, LEARNER))
            assertTrue("新追加的归档在目标行之后", appended.archiveId > target.archiveId)
            assertEquals(
                "追加的那份就是回退前的当前投影",
                afterReplay.snapshot,
                LearnerSnapshotJson.decode(appended.snapshotJson),
            )
            assertEquals(
                "目标归档行保持原样",
                ArchiveRow(
                    archiveId = target.archiveId,
                    archivedAtEpochMillis = target.archivedAtEpochMillis,
                    snapshotJson = target.snapshotJson,
                    projectorVersion = target.projectorVersion,
                    schemaDdl = target.schemaDdl,
                ),
                readArchiveRowById(context, databaseName, target.archiveId),
            )

            // ---- §4 第 5–6 步：重新排空 → 投影按当前算法重新前进，恢复的那份又被归档 ----
            repository.refresh()
            val afterRedrain = requireNotNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
            assertEquals(
                "重放是确定性的：同一账本重放两次得到同一份投影",
                afterReplay.snapshot,
                afterRedrain.snapshot,
            )
            assertEquals(
                "§4-6：投影回到 CURRENT（既不是 STALE 也不是 CATCHING_UP）",
                LearnerSnapshotFreshness.CURRENT,
                afterRedrain.snapshot.freshness,
            )
            assertEquals(
                "§4-6：投影状态回到 CURRENT",
                ProjectionStatus.CURRENT,
                afterRedrain.snapshot.projectionStatus,
            )
            assertEquals(3, archiveCount(context, databaseName))
            assertEquals(
                "回退本身可逆：被替换掉的恢复投影也在归档表里",
                committed.snapshot,
                LearnerSnapshotJson.decode(
                    requireNotNull(store.readLatestArchivedProjection(PROJECTION, LEARNER)).snapshotJson,
                ),
            )
        }
    }

    @Test
    fun restoringWithoutAnyArchiveIsRejectedAndWritesNothing() = runBlocking {
        withDrill { context, databaseName, store, _ ->
            val rejected = runCatching {
                store.restoreArchivedProjection(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    expectedProjectorVersion = LearningProjector.VERSION,
                    restoredAtEpochMillis = RESTORED_AT,
                )
            }.exceptionOrNull()

            assertTrue("无归档必须是显式拒绝：$rejected", rejected is ProjectionRestoreRejectedException)
            assertEquals(
                ProjectionRestoreRejection.NO_ARCHIVE,
                (rejected as ProjectionRestoreRejectedException).reason,
            )
            assertNull(
                "拒绝不得建立投影空壳",
                store.readCurrentLearnerSnapshot(PROJECTION, LEARNER),
            )
            assertEquals(0, archiveCount(context, databaseName))
        }
    }

    @Test
    fun restoringACrossVersionArchiveIsRejected() = runBlocking {
        withDrill { context, databaseName, store, _ ->
            // 真实归档写入路径落一行"旧二进制"归档：版本列与 JSON 载荷同为一个旧复合串。
            val legacySnapshot = LearnerSnapshot(
                learnerId = LEARNER,
                checkpoint = ProjectionCheckpoint(
                    lastSequence = 0,
                    projectorVersion = LEGACY_PROJECTOR_VERSION,
                    projectedAtEpochMillis = SEED_AT,
                ),
                generatedAtEpochMillis = SEED_AT,
            )
            store.archiveProjectionSnapshot(
                ProjectionArchiveRecord(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    snapshotJson = LearnerSnapshotJson.encode(legacySnapshot),
                    projectorVersion = LEGACY_PROJECTOR_VERSION,
                    archivedAtEpochMillis = ARCHIVED_AT,
                ),
            )

            val rejected = runCatching {
                store.restoreArchivedProjection(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    expectedProjectorVersion = LearningProjector.VERSION,
                    restoredAtEpochMillis = RESTORED_AT,
                )
            }.exceptionOrNull()

            assertTrue("跨版本必须被拒：$rejected", rejected is ProjectionRestoreRejectedException)
            assertEquals(
                ProjectionRestoreRejection.VERSION_MISMATCH,
                (rejected as ProjectionRestoreRejectedException).reason,
            )
            assertNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
            assertEquals("拒绝不得追加归档行", 1, archiveCount(context, databaseName))
        }
    }

    @Test
    fun anArchiveWhosePayloadCarriesAnotherVersionIsRejected() = runBlocking {
        withDrill { context, databaseName, store, _ ->
            // 版本列伪装成当前版本、载荷仍写着旧版本（外部改行/未来漂移才会出现的形状）：
            // 只信列会让一份别的版本算出来的状态写进来，所以载荷也必须被判定。
            val legacySnapshot = LearnerSnapshot(
                learnerId = LEARNER,
                checkpoint = ProjectionCheckpoint(
                    lastSequence = 0,
                    projectorVersion = LEGACY_PROJECTOR_VERSION,
                    projectedAtEpochMillis = SEED_AT,
                ),
                generatedAtEpochMillis = SEED_AT,
            )
            store.archiveProjectionSnapshot(
                ProjectionArchiveRecord(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    snapshotJson = LearnerSnapshotJson.encode(legacySnapshot),
                    projectorVersion = LearningProjector.VERSION,
                    archivedAtEpochMillis = ARCHIVED_AT,
                ),
            )

            val rejected = runCatching {
                store.restoreArchivedProjection(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    expectedProjectorVersion = LearningProjector.VERSION,
                    restoredAtEpochMillis = RESTORED_AT,
                )
            }.exceptionOrNull()

            assertTrue("载荷版本不一致必须被拒：$rejected", rejected is ProjectionRestoreRejectedException)
            assertEquals(
                ProjectionRestoreRejection.VERSION_MISMATCH,
                (rejected as ProjectionRestoreRejectedException).reason,
            )
            assertNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
            assertEquals(1, archiveCount(context, databaseName))
        }
    }

    @Test
    fun anArchiveWithDriftedSchemaDdlIsRejected() = runBlocking {
        withDrill { context, databaseName, store, repository ->
            store.recordAttempt(
                attemptCommand(
                    submissionId = "drift-submission-1",
                    attemptId = "drift-attempt-1",
                    presentationId = "drift-presentation-1",
                ),
            )
            repository.refresh()
            val projected = requireNotNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
            store.archiveProjectionSnapshot(
                ProjectionArchiveRecord(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    snapshotJson = LearnerSnapshotJson.encode(projected.snapshot),
                    projectorVersion = projected.snapshot.checkpoint.projectorVersion,
                    archivedAtEpochMillis = ARCHIVED_AT,
                ),
            )
            val target = requireNotNull(store.readLatestArchivedProjection(PROJECTION, LEARNER))
            // 漂移只能从外部灌进归档行：生产写侧永远现读真 DDL，这正是"schema_ddl 是库的事实"。
            driftSchemaDdl(context, databaseName, target.archiveId)

            val rejected = runCatching {
                store.restoreArchivedProjection(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    expectedProjectorVersion = LearningProjector.VERSION,
                    restoredAtEpochMillis = RESTORED_AT,
                )
            }.exceptionOrNull()

            assertTrue("schema 漂移必须被拒：$rejected", rejected is ProjectionRestoreRejectedException)
            assertEquals(
                ProjectionRestoreRejection.SCHEMA_MISMATCH,
                (rejected as ProjectionRestoreRejectedException).reason,
            )
            assertEquals(
                "拒绝不得改动当前投影",
                projected,
                store.readCurrentLearnerSnapshot(PROJECTION, LEARNER),
            )
            assertEquals(
                "拒绝不得追加归档行（漂移行也不许被顺手修好）",
                1,
                archiveCount(context, databaseName),
            )
            assertEquals(
                "被拒的归档行保持漂移原样",
                target.schemaDdl + SCHEMA_DRIFT_SUFFIX,
                requireNotNull(store.readLatestArchivedProjection(PROJECTION, LEARNER)).schemaDdl,
            )
        }
    }

    /**
     * P1（复核）：恢复目标早于既有呈现事实 → 显式拒绝。
     *
     * 可达态就是"操作者自建 save-point"：归档 C → 之后发生揭示（呈现行终局 = C+1）。
     * 增量排空把**未回滚的**呈现行按恢复后的 checkpoint 读成权威
     * （`PresentationProjectionState` 的"揭示序号 ≤ 水位"前置条件），没有这道门就会在
     * 下一次增量排空里以 `IllegalArgumentException` 卡死。工具不静默回滚呈现态
     * （没有历史版本可精确退到 C 当时的呈现态），所以在这里拒绝。
     */
    @Test
    fun aTargetEarlierThanAnExistingPresentationFactIsRejected() = runBlocking {
        withDrill { context, databaseName, store, repository ->
            store.recordAttempt(
                attemptCommand(
                    submissionId = "presentation-ahead-submission-1",
                    attemptId = "presentation-ahead-attempt-1",
                    presentationId = "presentation-ahead-presentation-1",
                ),
            )
            repository.refresh()
            val savePoint = requireNotNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
            assertEquals(1L, savePoint.snapshot.checkpoint.lastSequence)
            // 操作者自建 save-point（runbook §2 的"先给当前投影落一条归档"）：归档 C=1。
            store.archiveProjectionSnapshot(
                ProjectionArchiveRecord(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    snapshotJson = LearnerSnapshotJson.encode(savePoint.snapshot),
                    projectorVersion = savePoint.snapshot.checkpoint.projectorVersion,
                    archivedAtEpochMillis = ARCHIVED_AT,
                ),
            )

            // 揭示落在 C+1：呈现行终局 = 2（下一次**增量**排空的权威）。
            store.recordAnswerReveal(
                answerRevealCommand(
                    assessmentEventId = "presentation-ahead-reveal-1",
                    presentationId = "presentation-ahead-presentation-1",
                ),
            )
            repository.refresh()
            val afterReveal = requireNotNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
            assertEquals(2L, afterReveal.snapshot.checkpoint.lastSequence)

            val rejected = runCatching {
                store.restoreArchivedProjection(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    expectedProjectorVersion = LearningProjector.VERSION,
                    restoredAtEpochMillis = ARCHIVED_AT,
                )
            }.exceptionOrNull()

            assertTrue(
                "目标早于既有呈现事实必须被拒：$rejected",
                rejected is ProjectionRestoreRejectedException,
            )
            assertEquals(
                ProjectionRestoreRejection.PRESENTATION_AHEAD,
                (rejected as ProjectionRestoreRejectedException).reason,
            )
            assertEquals(
                "拒绝不得改动当前投影",
                afterReveal,
                store.readCurrentLearnerSnapshot(PROJECTION, LEARNER),
            )
            assertEquals("拒绝不得追加归档行", 1, archiveCount(context, databaseName))
        }
    }

    /** P2-A（复核）：归档 checkpoint 超前当前账本头 → 拒绝（否则下一次排空静默跳过事件）。 */
    @Test
    fun anArchiveWithACheckpointAheadOfTheLedgerIsRejected() = runBlocking {
        withDrill { context, databaseName, store, _ ->
            // 空账本（头 0）+ 一份"看起来正常"的 checkpoint=1 归档。
            store.archiveProjectionSnapshot(
                ProjectionArchiveRecord(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    snapshotJson = LearnerSnapshotJson.encode(
                        LearnerSnapshot(
                            learnerId = LEARNER,
                            checkpoint = ProjectionCheckpoint(
                                lastSequence = 1,
                                projectorVersion = LearningProjector.VERSION,
                                projectedAtEpochMillis = SEED_AT,
                            ),
                            generatedAtEpochMillis = SEED_AT,
                        ),
                    ),
                    projectorVersion = LearningProjector.VERSION,
                    archivedAtEpochMillis = ARCHIVED_AT,
                ),
            )

            val rejected = runCatching {
                store.restoreArchivedProjection(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    expectedProjectorVersion = LearningProjector.VERSION,
                    restoredAtEpochMillis = ARCHIVED_AT,
                )
            }.exceptionOrNull()

            assertTrue("checkpoint 超前账本头必须被拒：$rejected", rejected is ProjectionRestoreRejectedException)
            assertEquals(
                ProjectionRestoreRejection.CHECKPOINT_AHEAD,
                (rejected as ProjectionRestoreRejectedException).reason,
            )
            assertNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
            assertEquals("拒绝不得追加归档行", 1, archiveCount(context, databaseName))
        }
    }

    /** P2-B（复核）：恢复时刻早于目标归档时刻 → 拒绝（时间倒挂让"最近一份"原地不动）。 */
    @Test
    fun aRestoreTimeBeforeTheArchiveIsRejected() = runBlocking {
        withDrill { context, databaseName, store, _ ->
            store.archiveProjectionSnapshot(
                ProjectionArchiveRecord(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    snapshotJson = LearnerSnapshotJson.encode(savePointSnapshot()),
                    projectorVersion = LearningProjector.VERSION,
                    archivedAtEpochMillis = ARCHIVED_AT,
                ),
            )

            val rejected = runCatching {
                store.restoreArchivedProjection(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    expectedProjectorVersion = LearningProjector.VERSION,
                    restoredAtEpochMillis = ARCHIVED_AT - 1,
                )
            }.exceptionOrNull()

            assertTrue("时间倒挂必须被拒：$rejected", rejected is ProjectionRestoreRejectedException)
            assertEquals(
                ProjectionRestoreRejection.RESTORED_AT_IN_PAST,
                (rejected as ProjectionRestoreRejectedException).reason,
            )
            assertNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
            assertEquals("拒绝不得追加归档行", 1, archiveCount(context, databaseName))
        }
    }

    /** P2-G（复核）：归档 JSON 坏 → 机器可判的拒绝（原始解码异常只作 cause）。 */
    @Test
    fun aMalformedArchiveJsonIsRejected() = runBlocking {
        withDrill { context, databaseName, store, _ ->
            store.archiveProjectionSnapshot(
                ProjectionArchiveRecord(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    snapshotJson = "{\"learnerId\": \"$LEARNER\", \"checkpoint\": {",
                    projectorVersion = LearningProjector.VERSION,
                    archivedAtEpochMillis = ARCHIVED_AT,
                ),
            )

            val rejected = runCatching {
                store.restoreArchivedProjection(
                    projectionName = PROJECTION,
                    learnerId = LEARNER,
                    expectedProjectorVersion = LearningProjector.VERSION,
                    restoredAtEpochMillis = ARCHIVED_AT,
                )
            }.exceptionOrNull()

            assertTrue("坏 JSON 必须被拒：$rejected", rejected is ProjectionRestoreRejectedException)
            assertEquals(
                ProjectionRestoreRejection.MALFORMED_ARCHIVE,
                (rejected as ProjectionRestoreRejectedException).reason,
            )
            assertNull(store.readCurrentLearnerSnapshot(PROJECTION, LEARNER))
            assertEquals("拒绝不得追加归档行", 1, archiveCount(context, databaseName))
        }
    }

    // ---- 夹具 ----

    private suspend fun withDrill(
        block: suspend (
            Context,
            String,
            StudyDatabasePort,
            RoomBackedStudyExperienceRepository,
        ) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "projection-rollback-drill-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = store,
            applicationScope = applicationScope,
        )
        try {
            store.seedStudyFacts(drillSeed())
            store.saveAssessmentEvidenceSnapshot(evidenceSnapshot())
            repository.initialize()
            block(context, databaseName, store, repository)
        } finally {
            repository.close()
            store.close()
            applicationScope.cancel()
            context.deleteDatabase(databaseName)
        }
    }

    private fun attemptCommand(
        submissionId: String,
        attemptId: String,
        presentationId: String,
    ) = AttemptWriteCommand(
        learnerId = LEARNER,
        submissionId = submissionId,
        attemptId = attemptId,
        presentationId = presentationId,
        assessmentSnapshotId = SNAPSHOT_ID,
        submittedResponse = AttemptSubmittedResponse.Choice(
            choiceId = "B",
            choiceMarkdown = "同时比较驻点与端点",
            submittedAtEpochMillis = OCCURRED_AT,
        ),
        evidence = independentEvidence(),
        problemMemoryOutcome = ProblemMemoryOutcome.INDEPENDENT_RECALL,
        occurredAtEpochMillis = OCCURRED_AT,
        durationSeconds = 45,
        studyDay = studyDay(OCCURRED_AT),
    )

    private fun correctionCommand(
        submissionId: String,
        attemptId: String,
        correctionId: String,
    ) = AttemptCorrectionRecord(
        learnerId = LEARNER,
        submissionId = submissionId,
        correctionId = correctionId,
        attemptId = attemptId,
        replacementEvidence = negativeEvidence(),
        replacementMemoryOutcome = ProblemMemoryOutcome.RETRIEVAL_FAILURE,
        reasonMarkdown = "复核发现首次判分有误",
        occurredAtEpochMillis = OCCURRED_AT + 2_000,
    )

    private fun answerRevealCommand(
        assessmentEventId: String,
        presentationId: String,
    ) = AnswerRevealWriteCommand(
        learnerId = LEARNER,
        assessmentEventId = assessmentEventId,
        presentationId = presentationId,
        assessmentSnapshotId = SNAPSHOT_ID,
        contentMarkdown = "完整答案与推导",
        occurredAtEpochMillis = OCCURRED_AT + 3_000,
        studyDay = studyDay(OCCURRED_AT + 3_000),
    )

    /** "看起来正常"的空 save-point 快照（checkpoint 0 / 当前版本）：给纯拒绝用例当归档载荷。 */
    private fun savePointSnapshot() = LearnerSnapshot(
        learnerId = LEARNER,
        checkpoint = ProjectionCheckpoint(
            lastSequence = 0,
            projectorVersion = LearningProjector.VERSION,
            projectedAtEpochMillis = SEED_AT,
        ),
        generatedAtEpochMillis = SEED_AT,
    )

    private fun evidenceSnapshot() = AssessmentEvidenceSnapshot(
        snapshotId = SNAPSHOT_ID,
        assessmentItemId = "assessment-item-rollback",
        practiceUnitId = UNIT_ID,
        problemRevisionId = REVISION_ID,
        answerSpecId = "answer-spec-rollback",
        itemFamilyId = "family-rollback",
        sourceBundleId = "bundle-rollback",
        taxonomyVersion = TAXONOMY_VERSION,
        verification = AssessmentSnapshotVerification.VERIFIED,
        calibration = CalibrationSnapshot(
            support = CalibrationSupport.SUPPORTED,
            sourceId = "calibration-source",
            version = "calibration-v1",
            validFromEpochMillis = 0,
            validUntilEpochMillis = OCCURRED_AT + 100_000,
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
        capturedAtEpochMillis = OCCURRED_AT - 1_000,
    )

    private fun drillSeed() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = PROBLEM_ID,
                canonicalFingerprint = "problem-fingerprint-rollback",
                subject = "MATH",
                createdAtEpochMillis = SEED_AT,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = REVISION_ID,
                problemId = PROBLEM_ID,
                revisionNumber = 1,
                title = "闭区间最值",
                problemMarkdown = "下列哪一项是闭区间最值比较的正确做法？",
                answerSpecId = "answer-spec-rollback",
                answerSpecSnapshot = "B",
                answerVerificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
                sourceType = "TEST_LOCAL",
                sourceReference = null,
                contentFingerprint = "revision-fingerprint-rollback",
                createdAtEpochMillis = SEED_AT,
            ),
        ),
        practiceUnits = listOf(
            PracticeUnitSeedRecord(
                practiceUnitId = UNIT_ID,
                problemId = PROBLEM_ID,
                problemRevisionId = REVISION_ID,
                unitKey = "whole",
                unitKind = "WHOLE",
                title = "闭区间最值",
                promptMarkdown = "下列哪一项是闭区间最值比较的正确做法？",
                estimatedSeconds = 120,
                createdAtEpochMillis = SEED_AT,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-rollback",
                practiceUnitId = UNIT_ID,
                problemId = PROBLEM_ID,
                currentRevisionId = REVISION_ID,
                sourceKey = "test:rollback",
                acceptedAtEpochMillis = SEED_AT,
                updatedAtEpochMillis = SEED_AT,
            ),
        ),
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = KNOWLEDGE_ID,
                stableCode = "math.drill.closed-interval-extremum",
                subject = "MATH",
                displayName = "闭区间最值",
                parentKnowledgeNodeId = null,
                taxonomyVersion = TAXONOMY_VERSION,
                createdAtEpochMillis = SEED_AT,
            ),
        ),
        knowledgeBindings = listOf(
            KnowledgeBindingSeedRecord(
                bindingId = BINDING_ID,
                practiceUnitId = UNIT_ID,
                knowledgeNodeId = KNOWLEDGE_ID,
                basisRevisionId = REVISION_ID,
                strength = 1.0,
                sourceType = "VERIFIED",
                taxonomyVersion = TAXONOMY_VERSION,
                acceptedAtEpochMillis = SEED_AT,
            ),
        ),
    )

    private fun independentEvidence() = LearningEvidence(
        direction = LearningEvidenceDirection.POSITIVE,
        weight = 1.0,
        reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
    )

    private fun negativeEvidence() = LearningEvidence(
        direction = LearningEvidenceDirection.NEGATIVE,
        weight = 1.0,
        reason = LearningEvidenceReason.INDEPENDENT_INCORRECT,
    )

    private fun studyDay(atEpochMillis: Long): StudyDayContext {
        val zoned = Instant.ofEpochMilli(atEpochMillis).atZone(ZoneId.of(ZONE))
        return StudyDayContext(
            epochDay = zoned.toLocalDate().toEpochDay(),
            timeZoneId = ZONE,
            utcOffsetMinutes = zoned.offset.totalSeconds / 60,
        )
    }

    // ---- 直读探针（演练记录里的"归档表到底发生了什么"眼） ----

    private fun archiveCount(context: Context, databaseName: String): Int {
        val connection = AndroidSQLiteDriver()
            .open(context.getDatabasePath(databaseName).absolutePath)
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

    private fun readArchiveRowById(
        context: Context,
        databaseName: String,
        archiveId: Long,
    ): ArchiveRow {
        val connection = AndroidSQLiteDriver()
            .open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val statement = connection.prepare(
                "SELECT archive_id, archived_at_epoch_millis, snapshot_json, projector_version, " +
                    "schema_ddl FROM projection_archive WHERE archive_id = ?",
            )
            try {
                statement.bindLong(1, archiveId)
                assertTrue("归档行 $archiveId 必须还在", statement.step())
                return ArchiveRow(
                    archiveId = statement.getLong(0),
                    archivedAtEpochMillis = statement.getLong(1),
                    snapshotJson = requireNotNull(statement.getText(2)),
                    projectorVersion = requireNotNull(statement.getText(3)),
                    schemaDdl = requireNotNull(statement.getText(4)),
                )
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private fun driftSchemaDdl(context: Context, databaseName: String, archiveId: Long) {
        val connection = AndroidSQLiteDriver()
            .open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL(
                "UPDATE projection_archive SET schema_ddl = schema_ddl || '$SCHEMA_DRIFT_SUFFIX' " +
                    "WHERE archive_id = $archiveId",
            )
        } finally {
            connection.close()
        }
    }

    private data class ArchiveRow(
        val archiveId: Long,
        val archivedAtEpochMillis: Long,
        val snapshotJson: String,
        val projectorVersion: String,
        val schemaDdl: String,
    )

    private companion object {
        const val LEARNER = "learner:local"
        const val PROJECTION = "study-experience-v1"
        const val PROBLEM_ID = "problem:rollback"
        const val REVISION_ID = "revision:rollback"
        const val UNIT_ID = "practice:rollback:whole"
        const val KNOWLEDGE_ID = "knowledge:rollback:extremum"
        const val BINDING_ID = "binding:rollback"
        const val SNAPSHOT_ID = "evidence-snapshot:rollback"
        const val TAXONOMY_VERSION = "taxonomy-rollback-v1"
        const val ZONE = "Asia/Shanghai"
        const val SEED_AT = 1_768_010_000_000L
        const val OCCURRED_AT = 1_768_010_400_000L
        const val ARCHIVED_AT = 1_768_020_000_000L
        const val RESTORED_AT = 1_768_030_000_000L

        /** B4（KF-32）bump 之前的复合串：跨版本拒绝用例的"旧二进制"版本。 */
        const val LEGACY_PROJECTOR_VERSION =
            "learning-core-v11(projector-v11,evidence-v5,curve-v3,skip-v4,attribution-v3,ledger-v2)"

        const val SCHEMA_DRIFT_SUFFIX = " -- drill-drift"
    }
}
