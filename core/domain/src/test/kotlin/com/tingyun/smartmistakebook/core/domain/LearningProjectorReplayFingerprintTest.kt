package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.AttemptCorrection
import com.tingyun.smartmistakebook.core.model.BindingChanged
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotJson
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.TutorAnswerExposureOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W4-3/S8：重放管道的事件指纹复用（跨 pass 去重）与**逐位等价**。
 *
 * 读边界（`loadLearningLedger`/`loadProjectionBatch`）已经用 SHA-256 校验过每一行载荷的规范
 * 指纹；重放过去会在投影 pass 里对同一批事件再算一遍。本测试钉三件事：
 *
 * 1. **等价**：`replay(ledger, canonicalFingerprints = 读边界值)` 与"投影器自己重算"的
 *    `replay(ledger)` 输出逐位相同（整个 [LearningProjectionResult] 数据类相等，含 applied
 *    记录里的指纹值）——包括 5 万事件级夹具；
 * 2. **机制**：给了读边界值就用它（不重算）——错值会原样落进记录，由提交侧
 *    `verifyAppliedEventWindows` 与账本行比对兜底，不在投影器里做第二次校验；
 * 3. **规模**：5 万事件（现有测试里最大的重放夹具）下两条路仍逐位一致。
 */
class LearningProjectorReplayFingerprintTest {

    private val projector = LearningProjector()

    @Test
    fun `replay reuses caller-validated fingerprints bit for bit on a mixed ledger`() {
        val ledger = mixedLedger(1_200)
        val recomputed = projector.replay(LEARNER_ID, ledger)
        val reused = projector.replay(
            learnerId = LEARNER_ID,
            ledger = ledger,
            canonicalFingerprints = ledger.validatedFingerprints(),
        )

        assertEquals(
            "读边界指纹复用必须与投影器重算逐位相同（整个结果，含 applied 记录里的指纹值）",
            recomputed,
            reused,
        )
        assertAppliedFingerprintsMatchCanonical(reused, ledger)
    }

    @Test
    fun `replay records the read-boundary fingerprints verbatim instead of recomputing`() {
        // 机制钉法：故意给一个"只有调用方知道"的值。投影器若仍自行重算，记录里就会是规范指纹，
        // 断言失败。这同时说明信任边界在哪：读边界验值，投影器照抄，提交侧（DB）比对账本行。
        val attempt = attempt(sequence = 1, ordinal = 1)
        val ledger = listOf(attempt, chatEvidence(sequence = 2))
        val readBoundaryValues = mapOf(
            attempt.ledgerEventId to "read-boundary-attempt-fingerprint",
            "chat-2" to "read-boundary-chat-fingerprint",
        )

        val result = projector.replay(
            learnerId = LEARNER_ID,
            ledger = ledger,
            canonicalFingerprints = readBoundaryValues,
        )

        val record = result.snapshot.appliedAttemptRecords.getValue(attempt.attemptId)
        assertEquals("read-boundary-attempt-fingerprint", record.canonicalFingerprint)
        assertNotEquals(
            "重算会得到另一个值——所以这条断言确实在钉'复用'而不是巧合",
            LearningLedgerFingerprint.attempt(attempt),
            record.canonicalFingerprint,
        )
        // 未给值的类型（chat evidence 不落 applied 记录表）与缺项事件仍走本 pass 内现算，
        // 不因缺项而丢记录。
        assertEquals(2L, result.snapshot.checkpoint.lastSequence)
    }

    @Test
    fun `replay recomputes once per pass when no read-boundary fingerprints are supplied`() {
        // 既有调用方（测试/其他入口）不传指纹时必须逐位保持旧行为：同一条 ledger 重放两次，
        // 结果一致，且 applied 记录里的值等于规范指纹。
        val ledger = mixedLedger(300)
        val first = projector.replay(LEARNER_ID, ledger)
        val second = projector.replay(LEARNER_ID, ledger)

        assertEquals(first, second)
        assertAppliedFingerprintsMatchCanonical(first, ledger)
    }

    @Test
    fun `full replay over fifty thousand events stays bit identical with reused fingerprints`() {
        val ledger = mixedLedger(50_000)

        val recomputeStartedAt = System.nanoTime()
        val recomputed = projector.replay(LEARNER_ID, ledger)
        val recomputeFinishedAt = System.nanoTime()

        val validated = ledger.validatedFingerprints()
        val reuseStartedAt = System.nanoTime()
        val reused = projector.replay(
            learnerId = LEARNER_ID,
            ledger = ledger,
            canonicalFingerprints = validated,
        )
        val reuseFinishedAt = System.nanoTime()

        val isolatedHashStartedAt = System.nanoTime()
        val isolatedHashes = ledger.validatedFingerprints()
        val isolatedHashFinishedAt = System.nanoTime()

        println(
            "W4-3/S8 replay 50k events: recompute=${(recomputeFinishedAt - recomputeStartedAt) / 1_000_000}ms " +
                "reuse=${(reuseFinishedAt - reuseStartedAt) / 1_000_000}ms " +
                "isolated-50k-fingerprints=${(isolatedHashFinishedAt - isolatedHashStartedAt) / 1_000_000}ms " +
                "validated=${validated.size} isolated=${isolatedHashes.size}",
        )

        assertEquals("5 万事件级夹具下两条路必须逐位相同", recomputed, reused)
        assertEquals(50_000L, reused.snapshot.checkpoint.lastSequence)
        assertEquals(50_000L, reused.snapshot.knownLedgerHeadSequence)
        assertTrue(
            "夹具必须真的覆盖全部五类账本事件",
            ledger.map { it::class }.toSet().size == 5,
        )
        assertAppliedFingerprintsMatchCanonical(reused, ledger)
    }

    /**
     * K2 批 1 记录项（无门）：升级规模数字——把混合账本重放成快照后，量"升级归档会发生的那份
     * JSON"的字节数与 encode 成本，以及全量重放的调用成本（读边界口径：与 drainer 的重放调用
     * 逐字同形——`canonicalFingerprints` 由读边界先算好，计时只包住 `replay(...)` 调用本身；
     * 读边界的 SHA-256 成本单独打印）。
     *
     * 口径说明：量的是"同一账本在 N 事件规模下重放出的投影"——升级时被归档的 displaced 快照
     * 是**上一个二进制**留下的同规模投影，JSON 形状同形、量级相同（数值随版本口径不同）。
     * 纯 JVM 桌面口径；真机另行登记（K2 记录）。
     */
    @Test
    fun `upgrade scale archive json bytes and encode cost at ten and fifty thousand events`() {
        listOf(10_000, 50_000).forEach { count ->
            val ledger = mixedLedger(count)
            val fingerprintsStartedAt = System.nanoTime()
            val readBoundaryFingerprints = ledger.validatedFingerprints()
            val fingerprintsMillis = (System.nanoTime() - fingerprintsStartedAt) / 1_000_000
            val replayStartedAt = System.nanoTime()
            val replayed = projector.replay(
                learnerId = LEARNER_ID,
                ledger = ledger,
                canonicalFingerprints = readBoundaryFingerprints,
            )
            val replayMillis = (System.nanoTime() - replayStartedAt) / 1_000_000
            val encodeStartedAt = System.nanoTime()
            val json = LearnerSnapshotJson.encode(replayed.snapshot)
            val encodeMillis = (System.nanoTime() - encodeStartedAt) / 1_000_000
            println(
                "K2 upgrade scale N=$count: archive-json-chars=${json.length} " +
                    "archive-json-utf8-bytes=${json.toByteArray(Charsets.UTF_8).size} " +
                    "encode=${encodeMillis}ms read-boundary-fingerprints=${fingerprintsMillis}ms " +
                    "full-replay=${replayMillis}ms",
            )
            assertTrue("归档 JSON 必须是真产物（非空）", json.isNotEmpty())
            assertEquals(
                "归档 JSON 必须能逐位解回（记录项的完整性锚点）",
                replayed.snapshot,
                LearnerSnapshotJson.decode(json),
            )
        }
    }

    /**
     * KF-32 的核心语义（正是 bump 的理由）：重放期归属按**当前绑定**重派生——同一账本在改绑后
     * 重放，历史证据挂到新节点上，旧节点因为"重放从空表累加 + 差集删除"而**归零**。
     */
    @Test
    fun `replay re-derives historical attributions from the current bindings after a rebind`() {
        val unitId = "unit-1"
        val attempt = attempt(sequence = 1, ordinal = 1)
        val ledger = listOf(attempt, bindingChange(sequence = 2, previous = "kc-1", new = "kc-2"))
        val oldBindings = mapOf(unitId to listOf(binding("binding-old", unitId, "kc-1")))
        val newBindings = mapOf(unitId to listOf(binding("binding-new", unitId, "kc-2")))

        val before = projector.replay(
            learnerId = LEARNER_ID,
            ledger = ledger,
            currentBindingsByPracticeUnit = oldBindings,
        )
        assertEquals(
            "改绑前：证据按当时绑定落在旧节点",
            listOf("kc-1"),
            before.snapshot.knowledgeMasteryStates.keys.toList(),
        )

        val after = projector.replay(
            learnerId = LEARNER_ID,
            ledger = ledger,
            currentBindingsByPracticeUnit = newBindings,
        )

        assertEquals(
            "改绑后：历史证据重挂新节点，旧节点归零（重放从空表累加，旧节点不再被创建）",
            listOf("kc-2"),
            after.snapshot.knowledgeMasteryStates.keys.toList(),
        )
        val newState = after.snapshot.knowledgeMasteryStates.getValue("kc-2")
        assertEquals("重派生后的证据权重按绑定数均分（单绑定 = 1.0）", 1.0, newState.evidenceMass, 1e-9)
        assertEquals(
            "重派生归属的 bindingId 是当前绑定（不是写时快照的 binding-attempt-1）",
            "binding-new",
            newState.independentCorrectObservations.single().bindingId,
        )
        val replayedAgain = projector.replay(
            learnerId = LEARNER_ID,
            ledger = ledger,
            currentBindingsByPracticeUnit = newBindings,
        )
        assertEquals("重放幂等：同一账本 + 同一当前绑定 → 逐位相同", after, replayedAgain)
    }

    /**
     * 合并 × 改绑组合：先把历史证据合并到 successor 上，再改绑到 successor——两条机制必须叠加
     * 而不是互相覆盖（改绑派生出的新节点仍走同一 successors 链）。
     */
    @Test
    fun `a rebind onto a merged successor lands the historical evidence on the successor`() {
        val unitId = "unit-1"
        val attempt = attempt(sequence = 1, ordinal = 1)
        val ledger = listOf(attempt, bindingChange(sequence = 2, previous = "kc-1", new = "kc-merged"))
        val successors = KnowledgeNodeSuccessors(mapOf("kc-1" to "kc-merged", "kc-merged" to "kc-final"))

        val result = projector.replay(
            learnerId = LEARNER_ID,
            ledger = ledger,
            knowledgeNodeSuccessors = successors,
            currentBindingsByPracticeUnit = mapOf(
                unitId to listOf(binding("binding-merged", unitId, "kc-merged")),
            ),
        )

        assertEquals(
            "合并链继续生效：重派生出的 kc-merged 经 successors 落到 kc-final",
            listOf("kc-final"),
            result.snapshot.knowledgeMasteryStates.keys.toList(),
        )
    }

    /**
     * 缺项退回：题不在当前绑定映射里（或映射为空）时，重放读写时快照——既有调用方与无绑定题
     * 的行为逐位不变。
     */
    @Test
    fun `replay falls back to the write-time snapshot when no current bindings are supplied`() {
        val unitId = "unit-1"
        val attempt = attempt(sequence = 1, ordinal = 1)
        val ledger = listOf(attempt, bindingChange(sequence = 2, previous = "kc-1", new = "kc-2"))

        val withMap = projector.replay(
            learnerId = LEARNER_ID,
            ledger = ledger,
            currentBindingsByPracticeUnit = mapOf(
                unitId to listOf(binding("binding-old", unitId, "kc-1")),
            ),
        )
        val withoutMap = projector.replay(learnerId = LEARNER_ID, ledger = ledger)

        assertEquals(
            "无当前绑定时退回写时快照（夹具的归属节点）",
            listOf("kc-1"),
            withoutMap.snapshot.knowledgeMasteryStates.keys.toList(),
        )
        assertEquals(
            "写时快照的 bindingId 仍是旧绑定（未被重派生改写）",
            "binding-attempt-1",
            withoutMap.snapshot.knowledgeMasteryStates.getValue("kc-1")
                .independentCorrectObservations.single().bindingId,
        )
        assertEquals(
            "缺项退回的是写时快照本身（绑定 id 与重派生路径不同，其余投影数值同形）",
            "binding-old",
            withMap.snapshot.knowledgeMasteryStates.getValue("kc-1")
                .independentCorrectObservations.single().bindingId,
        )
        assertEquals(
            "两条路只差归因来源，掌握度数值逐位相同",
            withoutMap.snapshot.knowledgeMasteryStates.getValue("kc-1").copy(
                independentCorrectObservations = withMap.snapshot.knowledgeMasteryStates
                    .getValue("kc-1").independentCorrectObservations,
            ),
            withMap.snapshot.knowledgeMasteryStates.getValue("kc-1"),
        )
    }

    private fun binding(
        bindingId: String,
        practiceUnitId: String,
        knowledgeNodeId: String,
    ) = PracticeUnitBindingFacts(
        bindingId = bindingId,
        practiceUnitId = practiceUnitId,
        knowledgeNodeId = knowledgeNodeId,
        taxonomyVersion = "taxonomy-v1",
    )

    private fun bindingChange(
        sequence: Long,
        previous: String,
        new: String,
    ): BindingChanged = BindingChanged(
        bindingChangeId = "binding-change-$sequence",
        practiceUnitId = "unit-1",
        previousKnowledgeNodeIds = listOf(previous),
        newKnowledgeNodeIds = listOf(new),
        occurredAtEpochMillis = occurredAt(sequence),
        eventSequence = sequence,
    )

    private fun assertAppliedFingerprintsMatchCanonical(
        result: LearningProjectionResult,
        ledger: List<LearningLedgerEvent>,
    ) {        val canonical = ledger.associate { it.ledgerEventId to LearningLedgerFingerprint.event(it) }
        result.snapshot.appliedAttemptRecords.values.forEach { record ->
            assertEquals(canonical.getValue(record.attemptId), record.canonicalFingerprint)
        }
        result.snapshot.appliedAnswerRevealRecords.values.forEach { record ->
            assertEquals(canonical.getValue(record.outcomeId), record.canonicalFingerprint)
        }
        result.snapshot.appliedTutorAnswerExposureRecords.values.forEach { record ->
            assertEquals(canonical.getValue(record.outcomeId), record.canonicalFingerprint)
        }
        result.snapshot.appliedCorrectionRecords.values.forEach { record ->
            assertEquals(canonical.getValue(record.correctionId), record.canonicalFingerprint)
        }
    }

    private fun List<LearningLedgerEvent>.validatedFingerprints(): Map<String, String> =
        associate { it.ledgerEventId to LearningLedgerFingerprint.event(it) }

    /**
     * 确定性混合账本：`count` 条、序列 1..count 连续、id 全局唯一。
     * 每 10 条一条看答案、每 25 条一条聊天证据、每 50 条一次讲师答案曝光，其余为作答；
     * 每第 97 次作答后跟一条修正（修正只能被全量重放消费，正是本测试的目标路径）。
     */
    private fun mixedLedger(count: Int): List<LearningLedgerEvent> {
        val events = ArrayList<LearningLedgerEvent>(count)
        var sequence = 1L
        var attemptOrdinal = 0L
        while (events.size < count) {
            val event: LearningLedgerEvent = when {
                sequence % 50L == 0L -> tutorExposure(sequence)
                sequence % 25L == 0L -> chatEvidence(sequence)
                sequence % 10L == 0L -> answerReveal(sequence)
                else -> {
                    attemptOrdinal++
                    attempt(sequence, attemptOrdinal)
                }
            }
            events += event
            sequence++
            if (event is Attempt && attemptOrdinal % 97L == 0L && events.size < count) {
                events += correction(sequence, event)
                sequence++
            }
        }
        return events
    }

    private fun attempt(sequence: Long, ordinal: Long): Attempt {
        val positive = ordinal % 3L != 0L
        return Attempt(
            attemptId = "attempt-$sequence",
            presentationId = "presentation-$sequence",
            responseOrdinal = 1,
            assessmentSnapshot = snapshot(
                id = "attempt-$sequence",
                practiceUnitId = "unit-${sequence % 997}",
                knowledgeNodeId = "kc-${sequence % 257}",
            ),
            evidence = LearningEvidence(
                direction = if (positive) LearningEvidenceDirection.POSITIVE else LearningEvidenceDirection.NEGATIVE,
                weight = 1.0,
                reason = if (positive) {
                    LearningEvidenceReason.INDEPENDENT_CORRECT
                } else {
                    LearningEvidenceReason.INDEPENDENT_INCORRECT
                },
            ),
            problemMemoryOutcome = if (positive) {
                ProblemMemoryOutcome.INDEPENDENT_RECALL
            } else {
                ProblemMemoryOutcome.RETRIEVAL_FAILURE
            },
            occurredAtEpochMillis = occurredAt(sequence),
            durationSeconds = 60,
            studyDay = studyDay(sequence),
            eventSequence = sequence,
        )
    }

    private fun answerReveal(sequence: Long): AnswerRevealOutcome = AnswerRevealOutcome(
        outcomeId = "reveal-$sequence",
        presentationId = "presentation-reveal-$sequence",
        assessmentSnapshot = snapshot(
            id = "reveal-$sequence",
            practiceUnitId = "unit-${sequence % 997}",
            knowledgeNodeId = "kc-${sequence % 257}",
        ),
        occurredAtEpochMillis = occurredAt(sequence),
        studyDay = studyDay(sequence),
        eventSequence = sequence,
    )

    private fun tutorExposure(sequence: Long): TutorAnswerExposureOutcome = TutorAnswerExposureOutcome(
        outcomeId = "exposure-$sequence",
        exposureId = "exposure-fact-$sequence",
        sessionId = "session-$sequence",
        questionDocumentId = "question-document-$sequence",
        questionRevisionNumber = 1,
        cycleOrdinal = 1,
        turnOrdinal = 1,
        problemRevisionId = "revision-1",
        practiceUnitId = "unit-${sequence % 997}",
        occurredAtEpochMillis = occurredAt(sequence),
        eventSequence = sequence,
    )

    private fun chatEvidence(sequence: Long): ChatEvidenceSubmitted {
        val positive = sequence % 2L == 0L
        return ChatEvidenceSubmitted(
            evidenceId = "chat-$sequence",
            conversationId = "conversation-replay",
            knowledgeNodeId = "kc-${sequence % 257}",
            direction = if (positive) LearningEvidenceDirection.POSITIVE else LearningEvidenceDirection.NEGATIVE,
            weight = if (positive) {
                ChatEvidenceSubmitted.POSITIVE_WEIGHT
            } else {
                ChatEvidenceSubmitted.NEGATIVE_WEIGHT
            },
            reasonMarkdown = "模型判断：第 $sequence 条。",
            confidence = 0.9,
            occurredAtEpochMillis = occurredAt(sequence),
            eventSequence = sequence,
        )
    }

    private fun correction(sequence: Long, attempt: Attempt): AttemptCorrection = AttemptCorrection(
        correctionId = "correction-$sequence",
        attemptId = attempt.attemptId,
        replacementEvidence = LearningEvidence(
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 1.0,
            reason = LearningEvidenceReason.INDEPENDENT_INCORRECT,
        ),
        replacementMemoryOutcome = ProblemMemoryOutcome.RETRIEVAL_FAILURE,
        reasonMarkdown = "修正为独立答错。",
        occurredAtEpochMillis = occurredAt(sequence),
        eventSequence = sequence,
    )

    private fun snapshot(
        id: String,
        practiceUnitId: String,
        knowledgeNodeId: String,
    ): AssessmentEvidenceSnapshot = AssessmentEvidenceSnapshot(
        snapshotId = "snapshot-$id",
        assessmentItemId = "assessment-$id",
        practiceUnitId = practiceUnitId,
        problemRevisionId = "revision-1",
        answerSpecId = "answer-1",
        itemFamilyId = "family-$id",
        sourceBundleId = "bundle-replay",
        taxonomyVersion = "taxonomy-v1",
        verification = AssessmentSnapshotVerification.VERIFIED,
        calibration = CalibrationSnapshot(
            support = CalibrationSupport.SUPPORTED,
            sourceId = "calibration-source",
            version = "calibration-v1",
            validFromEpochMillis = 0,
            validUntilEpochMillis = Long.MAX_VALUE,
        ),
        attributions = listOf(
            KnowledgeEvidenceAttribution(
                bindingId = "binding-$id",
                knowledgeNodeId = knowledgeNodeId,
                weight = 1.0,
                basisRevisionId = "revision-1",
                taxonomyVersion = "taxonomy-v1",
                role = EvidenceAttributionRole.PRIMARY,
                certainty = EvidenceAttributionCertainty.DIRECT,
            ),
        ),
        capturedAtEpochMillis = 0,
    )

    private fun occurredAt(sequence: Long): Long = REPLAY_BASE_EPOCH_MILLIS + sequence * 60_000L

    private fun studyDay(sequence: Long): StudyDayContext = StudyDayContext(
        epochDay = 20_000L + sequence / 1_440L,
        timeZoneId = "Asia/Shanghai",
        utcOffsetMinutes = 480,
    )

    private companion object {
        const val LEARNER_ID = "learner:replay-fingerprint"

        /** 夹具时间基：2026-01-01T00:00:00Z 之后，序列每 +1 前进一分钟。 */
        const val REPLAY_BASE_EPOCH_MILLIS = 1_767_225_600_000L
    }
}
