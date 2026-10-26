package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G5-A / KD-67 的生产落库半边（core/database，真 Room + 真 SQLite）。
 *
 * 钉住四件事：
 * 1. `compareAndSetAssessmentItemVerificationStatus` 只改 `verification_status` 一列，
 *    题干/选项/键载荷/揭示态/时间戳逐字段不变；
 * 2. 返回**真实受影响行数**：写入 = 1，过期 expected = 0；
 * 3. 行不存在时 0 行且**不新增行**（不是 upsert）；
 * 4. 状态对收紧：只有 `UNKNOWN → VERIFIED/USER_ASSERTED` 可执行，其余在写库前抛契约异常。
 *
 * 设备轨命令（本轨不跑 Gradle，结果 UNVERIFIED）：
 * `D:/smart mistake book/gradlew.bat -p "D:/smart mistake book"
 *  :core:database:connectedDebugAndroidTest
 *  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.AssessmentItemVerificationStatusInstrumentedTest
 *  --console=plain`
 */
@RunWith(AndroidJUnit4::class)
class AssessmentItemVerificationStatusInstrumentedTest {
    private lateinit var store: RoomStudyDatabase

    @Before
    fun setUp() = runBlocking {
        store = StudyDatabaseFactory.openInMemory(
            ApplicationProvider.getApplicationContext<Context>(),
        )
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun casUpgradesOnlyTheVerificationStatusColumn() = runBlocking {
        val before = seedSnapshot()

        val rows = store.compareAndSetAssessmentItemVerificationStatus(
            assessmentItemSnapshotId = SNAPSHOT_ID,
            expectedCurrentStatus = StudyDbValue.VerificationStatus.UNKNOWN,
            newVerificationStatus = StudyDbValue.VerificationStatus.USER_ASSERTED,
        )

        assertEquals("受影响行数必须如实返回", 1, rows)
        assertEquals(
            "CAS 只写 verification_status，其余列必须逐字段不变",
            before.copy(verificationStatus = StudyDbValue.VerificationStatus.USER_ASSERTED),
            store.findAssessmentItemSnapshot(SNAPSHOT_ID),
        )
    }

    @Test
    fun aStaleExpectedStatusIsANoOp() = runBlocking {
        val before = seedSnapshot()

        assertEquals(
            1,
            store.compareAndSetAssessmentItemVerificationStatus(
                SNAPSHOT_ID,
                StudyDbValue.VerificationStatus.UNKNOWN,
                StudyDbValue.VerificationStatus.VERIFIED,
            ),
        )
        assertEquals(
            "过期 expected 必须返回 0 行",
            0,
            store.compareAndSetAssessmentItemVerificationStatus(
                SNAPSHOT_ID,
                StudyDbValue.VerificationStatus.UNKNOWN,
                StudyDbValue.VerificationStatus.USER_ASSERTED,
            ),
        )
        assertEquals(
            before.copy(verificationStatus = StudyDbValue.VerificationStatus.VERIFIED),
            store.findAssessmentItemSnapshot(SNAPSHOT_ID),
        )
    }

    @Test
    fun aMissingRowIsNeverCreated() = runBlocking {
        assertEquals(
            0,
            store.compareAndSetAssessmentItemVerificationStatus(
                "snapshot-missing",
                StudyDbValue.VerificationStatus.UNKNOWN,
                StudyDbValue.VerificationStatus.VERIFIED,
            ),
        )
        assertNull(store.findAssessmentItemSnapshot("snapshot-missing"))
    }

    @Test
    fun onlyUnknownCanBeUpgradedThroughTheCasPort() = runBlocking {
        seedSnapshot(StudyDbValue.VerificationStatus.VERIFIED)

        val swapVerifiedSources = runCatching {
            store.compareAndSetAssessmentItemVerificationStatus(
                SNAPSHOT_ID,
                StudyDbValue.VerificationStatus.VERIFIED,
                StudyDbValue.VerificationStatus.USER_ASSERTED,
            )
        }.exceptionOrNull()
        assertTrue(
            "已核验状态之间不得互换",
            swapVerifiedSources is DatabaseContractViolationException,
        )

        val downgrade = runCatching {
            store.compareAndSetAssessmentItemVerificationStatus(
                SNAPSHOT_ID,
                StudyDbValue.VerificationStatus.UNKNOWN,
                StudyDbValue.VerificationStatus.UNKNOWN,
            )
        }.exceptionOrNull()
        assertTrue(
            "不得把状态写回 UNKNOWN",
            downgrade is DatabaseContractViolationException,
        )

        assertEquals(
            "被拒的迁移不得写库",
            StudyDbValue.VerificationStatus.VERIFIED,
            store.findAssessmentItemSnapshot(SNAPSHOT_ID)?.verificationStatus,
        )
    }

    @Test
    fun concurrentCasWritersHaveExactlyOneWinner() = runBlocking {
        seedSnapshot()

        val results = coroutineScope {
            List(2) {
                async(Dispatchers.IO) {
                    store.compareAndSetAssessmentItemVerificationStatus(
                        SNAPSHOT_ID,
                        StudyDbValue.VerificationStatus.UNKNOWN,
                        StudyDbValue.VerificationStatus.USER_ASSERTED,
                    )
                }
            }.awaitAll()
        }

        assertEquals("并发同 id 两次必须恰一次写入", listOf(0, 1), results.sorted())
        assertEquals(
            StudyDbValue.VerificationStatus.USER_ASSERTED,
            store.findAssessmentItemSnapshot(SNAPSHOT_ID)?.verificationStatus,
        )
    }

    private suspend fun seedSnapshot(
        verificationStatus: String = StudyDbValue.VerificationStatus.UNKNOWN,
    ): AssessmentItemSnapshotSeedRecord {
        val record = AssessmentItemSnapshotSeedRecord(
            assessmentItemSnapshotId = SNAPSHOT_ID,
            itemRevision = 1,
            practiceUnitId = null,
            problemRevisionId = null,
            tutorContentSnapshotId = SNAPSHOT_ID,
            promptMarkdown = "考察 kc-a 的题面",
            optionsSnapshot =
                """[{"id":"A","markdown":"正确项"},{"id":"B","markdown":"干扰项"}]""",
            answerSpecSnapshot =
                """{"schema":"knowledge-quiz-item.v1","itemId":"knowledge-quiz:item:kc-a",""" +
                """"correctChoiceId":"A","knowledgeNodeIds":["kc-a"]}""",
            verificationStatus = verificationStatus,
            assessmentEligibility = StudyDbValue.AssessmentEligibility.SESSION_ONLY,
            scoringMode = StudyDbValue.ScoringMode.AUTO_VERIFIED,
            learnerSnapshotVersion = "knowledge-quiz-review-v1",
            projectionCheckpoint = 0L,
            hintLevelAtPresentation = 0,
            answerRevealState = "HIDDEN",
            createdAtEpochMillis = 11_000L,
        )
        store.saveAssessmentItemSnapshot(record)
        return record
    }

    private companion object {
        const val SNAPSHOT_ID = "assessment-item-snapshot:knowledge-quiz:learner:1:5:kc-a"
    }
}
