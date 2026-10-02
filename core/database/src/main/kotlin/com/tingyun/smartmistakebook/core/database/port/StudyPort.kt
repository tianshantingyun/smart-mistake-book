package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.model.TeachingAdvisoryRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * Read-only port for the learning ledger head sequence.
 */
interface LearningLedgerPort {
    fun observeLearningLedgerHead(learnerId: String): Flow<Long> = flowOf(0L)
}

/**
 * Port for the student-model prediction audit loop (PR-07).
 */
interface PredictionAuditPort {
    /** Persist shadow predictions produced during review planning (audit PR-07). */
    suspend fun recordStudentModelPredictions(predictions: List<StudentModelPredictionRecord>) = Unit

    /**
     * Resolve every unresolved prediction for [practiceUnitId] whose window
     * contains [observedAtEpochMillis] with the real attempt outcome.
     * @return number of predictions resolved.
     */
    suspend fun resolveStudentModelPredictions(
        practiceUnitId: String,
        wasIndependentCorrect: Boolean,
        observedAtEpochMillis: Long,
        responseLatencyMs: Long? = null,
        hintCount: Int = 0,
    ): Int = 0

    /** Resolved prediction/outcome pairs for offline calibration. */
    suspend fun readResolvedStudentModelPredictions(
        modelId: String,
        modelVersion: String,
    ): List<ResolvedStudentModelPredictionRecord> = emptyList()

    /** Last observed latency (ms) for a practice unit, used as HLR latency feature. */
    suspend fun findLastPredictionLatencyMs(practiceUnitId: String): Long? = null
}

/** Port-level prediction record for the student-model audit loop (PR-07). */
data class StudentModelPredictionRecord(
    val predictionId: String,
    val modelId: String,
    val modelVersion: String,
    val algorithmHash: String,
    val practiceUnitId: String,
    val knowledgeNodeId: String?,
    val featureFingerprint: String,
    val predictedScore: Double,
    val conservativeScore: Double,
    val predictionWindowStartEpochMillis: Long,
    val predictionWindowEndEpochMillis: Long,
    val predictedAtEpochMillis: Long,
)

/** Resolved prediction with its real outcome, ready for calibration. */
data class ResolvedStudentModelPredictionRecord(
    val predictionId: String,
    val modelId: String,
    val modelVersion: String,
    val algorithmHash: String,
    val predictedScore: Double,
    val conservativeScore: Double,
    val wasIndependentCorrect: Boolean,
    val observedAtEpochMillis: Long,
)

/**
 * D-M M1 功能内核：一个 practice unit 的题目事实（提交/揭示路径的元数据来源）。
 *
 * fixture 目录门删除后，机器可判题的作答提交与答案揭示所需的事实只能来自库内：
 * practice unit 记录（本题）＋ problem revision（题面/答案规格/来源）＋ problem（科目）。
 * 没有答案规格或没有结构化选项的题不构成机器可判题——派生方返回 null，界面回到
 * 讲题判定通道（与 release 行为一致）。
 */
data class PracticeUnitAssessmentRecord(
    val practiceUnitId: String,
    val problemId: String,
    val problemRevisionId: String,
    val subject: String,
    val unitTitle: String,
    val promptMarkdown: String,
    /** CapturedQuestionDocument 编码快照；null = 该 revision 没有结构化题面。 */
    val questionDocumentSnapshot: String?,
    val answerSpecId: String?,
    val answerSpecSnapshot: String?,
    val answerVerificationStatus: String,
    val sourceType: String,
    val sourceReference: String?,
    /** revision 的创建时刻：派生证据快照的确定性 capturedAt（快照内容不可随时间漂移）。 */
    val revisionCreatedAtEpochMillis: Long,
)

/**
 * 提交路径的题目事实读面（D-M M1）：按 practice unit 读题面与答案规格。
 * 无 fixture 的构建里这是机器可判题的唯一目录来源。
 */
interface PracticeUnitAssessmentReadPort {
    suspend fun readPracticeUnitAssessment(
        practiceUnitId: String,
    ): PracticeUnitAssessmentRecord?
}

/** Port-level record of one accepted practice-unit/knowledge binding. */
data class PracticeUnitKnowledgeBindingRecord(
    val bindingId: String,
    val practiceUnitId: String,
    val knowledgeNodeId: String,
    val basisRevisionId: String,
    val taxonomyVersion: String,
    val acceptedAtEpochMillis: Long,
)


/**
 * The model's own write surface inside the mastery database: advisory rows
 * are the ONLY rows an LLM may author. Projection-owned state
 * (learner_*_state) is exclusive to LearningProjector, so full replay never
 * erases model judgment and evidence never absorbs it.
 */
interface MasteryAdvisoryPort {
    /** Idempotent per (learner, source id, kind) - replays never duplicate. */
    suspend fun recordTeachingAdvisories(entries: List<TeachingAdvisoryRecord>) = Unit

    fun observeTeachingAdvisories(
        learnerId: String,
        practiceUnitId: String?,
    ): Flow<List<TeachingAdvisoryRecord>> = flowOf(emptyList())

    /**
     * 批量读一组题的咨询行（S16，W4-1 组装批量化）：一次查询取代逐题 N+1。
     *
     * 单题语义与 [observeTeachingAdvisories] 相同（每题只取最新 50 行、行内
     * `created_at` 降序）；Room 实现用一条 `IN (...)` 查询 + 分组后截断复刻该口径。
     * 默认实现退回逐题读，保证非 Room 实现（测试夹具）语义不变。
     */
    suspend fun readTeachingAdvisoriesForUnits(
        learnerId: String,
        practiceUnitIds: Collection<String>,
    ): List<TeachingAdvisoryRecord> = practiceUnitIds.flatMap { practiceUnitId ->
        observeTeachingAdvisories(learnerId, practiceUnitId).first()
    }
}
