package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.entity.ProblemClassificationBindingEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KF-30：`acceptedConfidence` 与 `acceptedAtEpochMillis` 一样，是**重确认会刷新的记录值**，
 * 不纳入分类行"同一接受事实"（`sameAcceptedFact`）的身份比较。
 *
 * 消灭的失败：把分数纳入身份比较后，一次"重复确认 + 真实分数刷新"（标签身份没变）会在
 * `insertClassificationBindings` 返回 -1 的防御分支被判成 `ImmutablePayloadConflictException`
 * ——"重复确认"意外变成"载荷冲突"。真正的载荷差异（标签 id、维度、权威来源）仍然判冲突。
 */
class ClassificationAcceptedFactIdentityTest {

    @Test
    fun refreshingTheRecordedConfidenceOrTimestampIsNotAConflictingFact() {
        val stored = bindingEntity(acceptedConfidence = null, acceptedAtEpochMillis = 2_000)
        val refreshed = bindingEntity(acceptedConfidence = 0.91, acceptedAtEpochMillis = 3_000)

        assertTrue(
            "刷新真实分数/时间戳只是更新记录值，标签身份没变就不是另一个事实",
            stored.sameAcceptedFact(refreshed),
        )
        assertTrue(
            "反方向同理（判据对称）",
            refreshed.sameAcceptedFact(stored),
        )
    }

    @Test
    fun aDifferentAcceptedLabelOrAuthorityIsAConflictingFact() {
        val stored = bindingEntity()

        assertFalse(
            "换了标签就是另一个事实（同名 bindingId 也不许静默保留旧行）",
            stored.sameAcceptedFact(bindingEntity(labelId = "math:knowledge:other")),
        )
        assertFalse(
            "换了维度就是另一个事实",
            stored.sameAcceptedFact(bindingEntity(dimension = "CHAPTER")),
        )
        assertFalse(
            "换了权威来源就是另一个事实",
            stored.sameAcceptedFact(bindingEntity(acceptanceSource = "USER_CORRECTED")),
        )
    }

    private fun bindingEntity(
        labelId: String = "math:knowledge:function-extrema",
        dimension: String = "KNOWLEDGE",
        acceptanceSource: String = "LOCAL_POLICY_ACCEPTED",
        acceptedAtEpochMillis: Long = 2_000,
        acceptedConfidence: Double? = null,
    ) = ProblemClassificationBindingEntity(
        bindingId = "classification-test",
        problemId = "problem-1",
        basisRevisionId = "revision-1",
        dimension = dimension,
        labelId = labelId,
        displayName = "函数最值",
        taxonomyVersion = "local-policy-v1",
        acceptanceSource = acceptanceSource,
        acceptedAtEpochMillis = acceptedAtEpochMillis,
        acceptedConfidence = acceptedConfidence,
    )
}
