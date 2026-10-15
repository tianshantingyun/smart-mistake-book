package com.tingyun.smartmistakebook.core.database.dao

import com.tingyun.smartmistakebook.core.database.entity.AssessmentEvidenceAttributionEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentEvidenceSnapshotEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-37：快照身份的**时间戳容忍**判定（`AttemptTransactionDao.saveEvidenceSnapshot` 的守卫）。
 *
 * 为什么身份判定值得单独钉住：这条比较此前是**整实体相等**，而「重新判定」每次都用新的
 * `capturedAtEpochMillis` 重建同一个 `snapshotId` 的快照——于是同一份快照被判成"载荷被改写"，
 * 在快照写入处就抛冲突（早于 `recordAttempt` 的幂等回放），第一次因 advance 失败而回滚的行
 * 再也写不进去。下面两条方向都要钉死：只差时间戳 → **同一张**；身份/内容/归属真变 → **仍是另一张**。
 *
 * 两侧的归属行都按 `binding_id` 排序（`AssessmentEvidenceSnapshot.toAttributionEntities()` 与
 * `findAttributions` 的 ORDER BY 同一口径），所以这里用已排序的列表比较。
 */
class AssessmentEvidenceSnapshotIdentityTest {

    @Test
    fun theSameSnapshotRecomputedAtAnotherInstantIsStillTheSameSnapshot() {
        val stored = snapshot(capturedAtEpochMillis = CAPTURED_AT)
        val retry = snapshot(capturedAtEpochMillis = CAPTURED_AT + 60_000)

        assertTrue(
            "时间戳不是快照身份的一部分：重试带新时间戳必须判成同一张，否则「重新判定」永远死在这里",
            sameEvidenceSnapshotIdentity(
                existing = stored,
                existingAttributions = listOf(attribution()),
                candidate = retry,
                candidateAttributions = listOf(attribution()),
            ),
        )
    }

    @Test
    fun anotherSnapshotIdIsAnotherSnapshot() {
        assertFalse(
            sameEvidenceSnapshotIdentity(
                existing = snapshot(),
                existingAttributions = listOf(attribution()),
                candidate = snapshot(snapshotId = "snapshot-other"),
                candidateAttributions = listOf(attribution()),
            ),
        )
    }

    @Test
    fun contentThatIsNotTheCaptureTimeStillHasToMatch() {
        val changedColumns = listOf(
            snapshot(assessmentItemId = "assessment-item-other"),
            snapshot(answerSpecId = "answer-spec-other"),
            snapshot(itemFamilyId = "family-other"),
            snapshot(sourceBundleId = "bundle-other"),
            snapshot(taxonomyVersion = "taxonomy-other"),
            snapshot(verification = "USER_ASSERTED"),
            snapshot(calibrationVersion = "calibration-other"),
            snapshot(calibrationValidUntilEpochMillis = CAPTURED_AT + 1),
            snapshot(practiceUnitId = "unit-other"),
        )

        changedColumns.forEach { candidate ->
            assertFalse(
                "除采集时刻外的任何一列不同都仍是另一份载荷：$candidate",
                sameEvidenceSnapshotIdentity(
                    existing = snapshot(),
                    existingAttributions = listOf(attribution()),
                    candidate = candidate,
                    candidateAttributions = listOf(attribution()),
                ),
            )
        }
    }

    @Test
    fun attributionsArePartOfTheSnapshotIdentity() {
        assertFalse(
            sameEvidenceSnapshotIdentity(
                existing = snapshot(),
                existingAttributions = listOf(attribution()),
                candidate = snapshot(),
                candidateAttributions = listOf(attribution(weight = 0.5)),
            ),
        )
        assertFalse(
            sameEvidenceSnapshotIdentity(
                existing = snapshot(),
                existingAttributions = listOf(attribution()),
                candidate = snapshot(),
                candidateAttributions = listOf(attribution(bindingId = "binding-other")),
            ),
        )
        assertFalse(
            "归属行数量变化同样是另一份载荷",
            sameEvidenceSnapshotIdentity(
                existing = snapshot(),
                existingAttributions = listOf(attribution()),
                candidate = snapshot(),
                candidateAttributions = emptyList(),
            ),
        )
    }

    private fun snapshot(
        snapshotId: String = SNAPSHOT_ID,
        assessmentItemId: String = "assessment-item-1",
        practiceUnitId: String = "unit-1",
        answerSpecId: String = "answer-spec-1",
        itemFamilyId: String = "family-1",
        sourceBundleId: String? = "bundle-1",
        taxonomyVersion: String = "taxonomy-v1",
        verification: String = "VERIFIED",
        calibrationVersion: String = "calibration-v1",
        calibrationValidUntilEpochMillis: Long = CAPTURED_AT + 100_000,
        capturedAtEpochMillis: Long = CAPTURED_AT,
    ) = AssessmentEvidenceSnapshotEntity(
        snapshotId = snapshotId,
        assessmentItemId = assessmentItemId,
        practiceUnitId = practiceUnitId,
        problemRevisionId = "revision-1",
        answerSpecId = answerSpecId,
        itemFamilyId = itemFamilyId,
        sourceBundleId = sourceBundleId,
        taxonomyVersion = taxonomyVersion,
        verification = verification,
        calibrationSupport = "SUPPORTED",
        calibrationSourceId = "calibration-source",
        calibrationVersion = calibrationVersion,
        calibrationValidFromEpochMillis = 0,
        calibrationValidUntilEpochMillis = calibrationValidUntilEpochMillis,
        capturedAtEpochMillis = capturedAtEpochMillis,
    )

    private fun attribution(
        bindingId: String = "binding-1",
        weight: Double = 1.0,
    ) = AssessmentEvidenceAttributionEntity(
        snapshotId = SNAPSHOT_ID,
        bindingId = bindingId,
        practiceUnitId = "unit-1",
        knowledgeNodeId = "knowledge-1",
        weight = weight,
        basisRevisionId = "revision-1",
        taxonomyVersion = "taxonomy-v1",
        role = "PRIMARY",
        certainty = "DIRECT",
    )

    private companion object {
        const val SNAPSHOT_ID = "snapshot-1"
        const val CAPTURED_AT = 1_700_000_000_000L
    }
}
