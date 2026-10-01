package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.IndependentCorrectObservation
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.MasteryStatus

/**
 * Versioned policy for determining when a knowledge component is clearly mastered.
 *
 * W3-2（台账「裁决 13 · 修订」，2026-09-30）：判据 = **知识点自己的记忆状态**——同一套 FSRS
 * 更新吃该知识点的作答流（`KnowledgeMasteryState.memoryStabilityDays` /
 * `lastAttemptAtEpochMillis`）。掌握 ⇔ 记忆稳定度 ≥ [minimumStabilityDays]（Anki mature 口径，
 * 21 天）∧ **当前**召回概率 ≥ [minimumCurrentRetention]（0.9，与目标保留率同源）。
 * ε/θ 硬门退场：β-二项 p̂ + Wilson 区间降为展示层（KF-20 的"区间 = 透明度"）。
 */
data class MasteryDecisionPolicy(
    val policyVersion: String,
    val minimumStabilityDays: Double,
    val minimumCurrentRetention: Double,
    /**
     * 判定"当前召回概率"用的 FSRS 衰减。E 判据在**读取时**用 `now` 重算召回概率，需要参数
     * 上下文：有上下文的调用方（投影器）传自己的 `-w20`；无上下文的调用方在 [companion] 的
     * DEFAULT 里**显式**取默认（W2-1 纪律：默认值必须可见地声明）。规划侧接个性化参数随
     * Wave 3 批次 3（规划侧个性化）落地。
     */
    val decay: Double,
    /** 独立正确观察的**题目族**广度门槛：只服务 CONFLICTED 的恢复路径。 */
    val minimumItemFamilies: Int,
    /** 独立正确观察的**学习日**广度门槛：只服务 CONFLICTED 的恢复路径。 */
    val minimumStudyDays: Int,
) {
    init {
        require(minimumStabilityDays > 0.0) { "Minimum stability days must be positive" }
        require(minimumCurrentRetention in 0.0..1.0) {
            "Minimum current retention must be a probability"
        }
        require(decay < 0.0) { "FSRS decay must be negative" }
        require(minimumItemFamilies > 0) { "Minimum families must be positive" }
        require(minimumStudyDays > 0) { "Minimum study days must be positive" }
    }

    companion object {
        val DEFAULT = MasteryDecisionPolicy(
            policyVersion = "mastery-v2",
            minimumStabilityDays = AlgorithmConstants.Mastery.MIN_STABILITY_DAYS,
            minimumCurrentRetention = AlgorithmConstants.Mastery.MIN_CURRENT_RETENTION,
            // 无个性化参数上下文时的显式回退（默认衰减）；有上下文者必须传自己的 decay。
            decay = -FsrsScheduleMath.DEFAULT_PARAMETERS[20],
            minimumItemFamilies = AlgorithmConstants.Mastery.MIN_ITEM_FAMILIES,
            minimumStudyDays = AlgorithmConstants.Mastery.MIN_STUDY_DAYS,
        )
    }
}

/** Shared, time-aware threshold contract used by projection and adaptive teaching decisions. */
object ClearlyMasteredForSkipPolicy {
    const val VERSION = LearningCoreVersions.SKIP_POLICY

    /** CONFLICTED 恢复路径的直接证据权重门槛（原判据常量，语义不变）。 */
    const val EVIDENCE_MASS = AlgorithmConstants.Mastery.MIN_EVIDENCE_MASS
    const val REQUIRED_FAMILIES = AlgorithmConstants.Mastery.MIN_ITEM_FAMILIES
    const val REQUIRED_STUDY_DAYS = AlgorithmConstants.Mastery.MIN_STUDY_DAYS
    const val MAX_EVIDENCE_AGE_MILLIS =
        AlgorithmConstants.Mastery.MAX_EVIDENCE_AGE_DAYS * AlgorithmConstants.DAY_MILLIS

    private const val TIME_EPSILON_MILLIS = 1L
    private const val VALUE_EPSILON = 1e-9

    /**
     * E 判据核心（纯函数，投影与读取共用这一处定义）：稳定度 ≥ 门槛 ∧ **当前**召回概率 ≥ 门槛。
     * 当前召回概率由 `atEpochMillis − lastAttemptAtEpochMillis` 与稳定度重算——"掌握是活的"：
     * 久不复习 → 概率跌破 → 自动降档；复习一次 → 立刻恢复；又错新题 → 记忆卡 lapse 掉档。
     */
    fun meetsMemoryCriterion(
        memoryStabilityDays: Double?,
        lastAttemptAtEpochMillis: Long?,
        atEpochMillis: Long,
        decay: Double,
        policy: MasteryDecisionPolicy = MasteryDecisionPolicy.DEFAULT,
    ): Boolean {
        if (memoryStabilityDays == null || lastAttemptAtEpochMillis == null) return false
        if (memoryStabilityDays + VALUE_EPSILON < policy.minimumStabilityDays) return false
        if (atEpochMillis + TIME_EPSILON_MILLIS < lastAttemptAtEpochMillis) return false
        val elapsedDays = (atEpochMillis - lastAttemptAtEpochMillis) / ONE_DAY_MILLIS
        val retention = FsrsScheduleMath.retention(elapsedDays, memoryStabilityDays, decay)
        return retention + VALUE_EPSILON >= policy.minimumCurrentRetention
    }

    fun isSatisfied(
        state: KnowledgeMasteryState,
        atEpochMillis: Long,
        policy: MasteryDecisionPolicy = MasteryDecisionPolicy.DEFAULT,
    ): Boolean {
        require(atEpochMillis >= 0) { "Mastery decision time must not be negative" }
        return state.status == MasteryStatus.MASTERED &&
            meetsMemoryCriterion(
                memoryStabilityDays = state.memoryStabilityDays,
                lastAttemptAtEpochMillis = state.lastAttemptAtEpochMillis,
                atEpochMillis = atEpochMillis,
                decay = policy.decay,
                policy = policy,
            )
    }

    internal fun hasIndependentBreadth(
        observations: List<IndependentCorrectObservation>,
        lastIndependentErrorAtEpochMillis: Long?,
        lastIndependentErrorSequence: Long?,
        atEpochMillis: Long,
        policy: MasteryDecisionPolicy = MasteryDecisionPolicy.DEFAULT,
    ): Boolean {
        val valid = validIndependentObservations(
            observations,
            lastIndependentErrorAtEpochMillis,
            lastIndependentErrorSequence,
            atEpochMillis,
        )
        return valid.map(IndependentCorrectObservation::itemFamilyId).distinct().size >= policy.minimumItemFamilies &&
            valid.map(IndependentCorrectObservation::studyDayEpochDay).distinct().size >= policy.minimumStudyDays
    }

    internal fun validIndependentObservations(
        observations: List<IndependentCorrectObservation>,
        lastIndependentErrorAtEpochMillis: Long?,
        lastIndependentErrorSequence: Long?,
        atEpochMillis: Long,
    ): List<IndependentCorrectObservation> = observations.filter {
        val afterError = when {
            lastIndependentErrorSequence != null && it.eventSequence > 0 ->
                it.eventSequence > lastIndependentErrorSequence
            lastIndependentErrorAtEpochMillis != null ->
                it.occurredAtEpochMillis > lastIndependentErrorAtEpochMillis
            else -> true
        }
        val fresh = atEpochMillis >= it.occurredAtEpochMillis &&
            atEpochMillis - it.occurredAtEpochMillis <= MAX_EVIDENCE_AGE_MILLIS
        afterError && fresh && it.isStudyDayTrusted
    }

    private const val ONE_DAY_MILLIS = 86_400_000.0
}
