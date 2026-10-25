package com.tingyun.smartmistakebook.core.domain

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * G5-A 预测生产端口（R5 提案 §3-A 的"本地双端口"之一；实现规格
 * `docs/research/2026-10-25-elo-predictor-spec.md` §4.1，下称 R4）。
 *
 * 它填的是 R5 提案 §1.2② 指认的缺口：选择器的走廊判据需要
 * [PredictedCorrectnessInterval]，而此前生产侧没有任何代码为候选产出这个区间——
 * 没有本端口，`AdaptiveQuestionSelector` 恒 fail-closed 空转。
 *
 * 对 R5 §3-A 草图的语义映射（裁定 1：选项 A，Elo 状态**惰性重放派生（零新表）**）：
 * - 草图 `update(answer)` → [replay]：不做增量写回，状态永远是作答流前缀的纯函数
 *   （同前缀 → 逐位同状态，符合"固定账本重放逐位不变"纪律）；
 * - 草图 `predict(candidate)` → [predictInterval]。
 *
 * 边界（钉住，R4 §0.6 / 任务项六）：**选择决策不经模型**——实现是纯本地数学，
 * 无模型调用、无网络、无提示词、无请求 schema；`AdaptiveQuestionSelector` 的边界
 * 注释（"model wording may consume this decision but cannot replace it"）在预测侧
 * 同样成立。
 */
interface AdaptiveCorrectnessPredictor {
    /**
     * 作答流（[EloAnswerRecord.eventSequence] 严格升序）的纯折叠。
     * 同一前缀逐位得同一状态；空列表 → [EloLearnerState.INITIAL]。
     */
    fun replay(events: List<EloAnswerRecord>): EloLearnerState

    /**
     * 候选的预测区间：点预测 + 半宽 h(n)（n = 该节点已计作答数）。n=0 时区间全宽，
     * 走廊判据必拒——冷启动 fail-closed 是设计默认（R4 §4.4）。
     */
    fun predictInterval(
        state: EloLearnerState,
        knowledgeNodeId: String,
        optionsCount: Int?,
        decisionAtEpochMillis: Long,
    ): PredictedCorrectnessInterval
}

/**
 * Elo 折叠的全部可调数值——**逐字段 PENDING_CALIBRATION**（主会话裁定 1）：
 * [PROSO_PRODUCTION_DEFAULTS] 是别家（proso-apps）的生产值快照，**未在本项目数据
 * 分布上验证**（R4 §5.1；Wave X 校准线落真实效果）。消费方不得把这些值当作"已校准"：
 * Wave X 校准改值时，`LearningCoreVersions.PREDICTOR_ELO` 必须同步 bump。
 *
 * @param eloAlpha α(n) 分子。proso `elo_alpha=0.8`（prediction.py:166）。PENDING_CALIBRATION
 * @param eloDynamicAlpha α(n) 阻尼常数（α(n) = eloAlpha/(1+eloDynamicAlpha·n)）。
 *   proso `elo_dynamic_alpha=0.05`（:166）。PENDING_CALIBRATION
 * @param pfaeGood 答对时 (用户,题) 局部技能步长。proso `pfae_good=3.4`（:166）。PENDING_CALIBRATION
 * @param pfaeBad 答错时局部技能步长（非对称：只小幅拉低，防一次答错抹掉多次答对）。
 *   proso `pfae_bad=0.3`（:166）。PENDING_CALIBRATION
 * @param timeShiftSeconds 重答分支的时间回升项分子（秒）。proso `time_shift=80.0`（:166）。
 *   PENDING_CALIBRATION
 * @param intervalHalfWidthInitial 区间半宽 h(n) 的分子（h(0) = 全宽 0.5，R4 §4.4——
 *   本移植的必要补全，不是 proso 数学的一部分：proso 输出点值，本项目契约要区间）。
 *   PENDING_CALIBRATION
 * @param predictionValidityMillis 预测区间有效期（validUntil = 决策时刻 + 此值）。
 *   R4 §4.1 取 24h。PENDING_CALIBRATION
 */
data class EloHyperparameters(
    val eloAlpha: Double,
    val eloDynamicAlpha: Double,
    val pfaeGood: Double,
    val pfaeBad: Double,
    val timeShiftSeconds: Double,
    val intervalHalfWidthInitial: Double,
    val predictionValidityMillis: Long,
) {
    companion object {
        /**
         * proso 生产默认值快照（出处：`proso/models/prediction.py:164-236` 默认参数 +
         * `testproject/proso_config.yaml:4-5` 生产配置未覆盖任何参数；MIT，本会话
         * `gh api` 实拉核验）。**别家生产值，未在本项目数据上校准**——见类 KDoc。
         */
        val PROSO_PRODUCTION_DEFAULTS = EloHyperparameters(
            eloAlpha = 0.8,
            eloDynamicAlpha = 0.05,
            pfaeGood = 3.4,
            pfaeBad = 0.3,
            timeShiftSeconds = 80.0,
            intervalHalfWidthInitial = 0.5,
            predictionValidityMillis = 24L * 60 * 60 * 1000,
        )
    }
}

/**
 * 一条**已解析有效**的作答（core/data 装配器从台账前缀解析产出；core/domain 不碰
 * 台账类型）。"已解析"指 VOID/REPLACE 修正语义已在装配侧折叠完（与掌握度投影同语义，
 * `LearningProjector.resolveEvidenceEffect`）——被排除的证据不进本列表，被替代的证据
 * 带原方向位与原时间、按新对错出现。
 *
 * @param optionsCount 题面选项数（猜测修正 guess=1/n 用）；null = 快照读不回 → 该事件
 *   guess=0、修正关闭（proso 自身的降级路径，prediction.py:356-359）。逐事件降级，
 *   不全局失效。
 */
data class EloAnswerRecord(
    val evidenceId: String,
    val knowledgeNodeId: String,
    val isCorrect: Boolean,
    val optionsCount: Int?,
    val occurredAtEpochMillis: Long,
    val eventSequence: Long,
) {
    init {
        require(evidenceId.isNotBlank()) { "Elo answer evidence id must not be blank" }
        require(knowledgeNodeId.isNotBlank()) { "Elo answer knowledge node id must not be blank" }
        require(occurredAtEpochMillis >= 0) { "Elo answer time must not be negative" }
        require(eventSequence > 0) { "Elo answer sequence must be positive" }
        require(optionsCount == null || optionsCount > 0) {
            "Elo answer option count must be positive when present"
        }
    }
}

/**
 * 折叠产物：单学习者的 Elo 状态（R4 §4.1）。
 *
 * 状态 = 作答流前缀的纯函数，无隐藏写入通道；键控对齐 proso 三状态
 * （prior_skill / difficulty / current_skill，prediction.py:173-184）外加
 * 重放与区间派生需要的时间与计数面。
 *
 * @param currentSkillByNode 缺键 = 该 (学习者,节点) 无历史（use_prior 分支）。
 * @param lastAnswerAtByNode 缺键或 ≤0 = 无有效上次时间（预测走先验分支）。
 * @param answeredCountByNode 区间半宽 h(n) 的 n（全部已计作答，含首答）。
 * @param firstAnswerCountByUser proso `user_first_answers`（:180）：该学习者答过的
 *   不同节点数（首答门 α 的用户侧 n）。
 * @param firstAnswerCountByNode proso `item_first_answers`（:181）：该节点被首答的
 *   次数（单学习者设备上恒 ≤1；α 的题目侧 n）。
 */
data class EloLearnerState(
    val priorSkill: Double,
    val difficultyByNode: Map<String, Double>,
    val currentSkillByNode: Map<String, Double>,
    val lastAnswerAtByNode: Map<String, Long>,
    val answeredCountByNode: Map<String, Int>,
    val firstAnswerCountByUser: Int,
    val firstAnswerCountByNode: Map<String, Int>,
) {
    companion object {
        /** 全空状态：prior 0.0、difficulty 0.0（缺键视为 0，prediction.py:175-176）。 */
        val INITIAL = EloLearnerState(
            priorSkill = 0.0,
            difficultyByNode = emptyMap(),
            currentSkillByNode = emptyMap(),
            lastAnswerAtByNode = emptyMap(),
            answeredCountByNode = emptyMap(),
            firstAnswerCountByUser = 0,
            firstAnswerCountByNode = emptyMap(),
        )
    }
}

/**
 * proso-apps `PriorCurrentPredictiveModel` 的 Kotlin 移植（MIT；出处
 * https://github.com/adaptive-learning/proso-apps `proso/models/prediction.py:164-236,353-364`，
 * 本会话 `gh api` 实拉全文逐行核对；移植口径 = R4 §1 的公式精读）。
 *
 * 纯函数：无 IO、无时钟读取、无全局态、无模型调用。约 60 行标量 double 运算，
 * 单遍折叠 O(E)（每事件 1 次 exp + 常数次哈希操作；R4 §3.2）。
 *
 * 与 proso 源码的两处**显式偏差**（均为 R4 规格规定，均已登记算法台账）：
 * 1. **缺/坏上次时间的降级**：proso 对 falsy last_time 取 `seconds_ago = 315460000`
 *    （≈十年，prediction.py:197）→ 回升项 ≈0；本移植按 R4 §2.2.4/§4.5 取更保守的
 *    **先验分支**（不猜插值）。时间倒挂（occurredAt < lastAnswerAt）同理走先验分支，
 *    而非 proso 的 `max(seconds_ago, 0.001)` 巨幅回升。
 * 2. **首答 α 的两个计数**：R4 §4.1 规则 3 草拟"合并为一个 n（数学等价）"——主源
 *    实测是**两个独立计数**分别作用 prior 与 difficulty（prediction.py:229-236：
 *    `alpha_fun(data['user_first_answers'])` 与 `alpha_fun(data['item_first_answers'])`）。
 *    单学习者设备上 item 计数在首答时恒为 0，而 user 计数随已答节点数增长，两者只在
 *    第一个节点相等——"数学等价"前提不成立。本移植按主源的两计数形态实现
 *    （[EloLearnerState] 同时携带两个计数，与 R4 §4.1 的状态数据类一致）。
 *
 * 不移植（R4 §1.9）：`AlwaysLearningPredictiveModel` 的层级父题传播、
 * `ShiftedPredictiveModel` 的预测偏移——均非 proso 生产配置所启用，本项目亦无对应面。
 */
class EloCorrectnessPredictor(
    private val hyper: EloHyperparameters = EloHyperparameters.PROSO_PRODUCTION_DEFAULTS,
) : AdaptiveCorrectnessPredictor {

    /** 预测身份：随区间进入 `PredictedCorrectnessInterval.modelVersion`。 */
    val modelVersion: String = LearningCoreVersions.PREDICTOR_ELO

    override fun replay(events: List<EloAnswerRecord>): EloLearnerState {
        // 台账序即折叠序：乱序输入按 fail-closed 拒绝（不排序、不猜顺序——排序是装配侧
        // 的职责，见 AdaptiveSelectionAssembler；这里只折叠）。
        require(
            events.zipWithNext().all { (current, next) -> current.eventSequence < next.eventSequence },
        ) { "Elo replay requires records in strictly ascending eventSequence order" }

        var priorSkill = EloLearnerState.INITIAL.priorSkill
        val difficultyByNode = HashMap<String, Double>()
        val currentSkillByNode = HashMap<String, Double>()
        val lastAnswerAtByNode = HashMap<String, Long>()
        val answeredCountByNode = HashMap<String, Int>()
        var firstAnswerCountByUser = 0
        val firstAnswerCountByNode = HashMap<String, Int>()

        for (record in events) {
            val node = record.knowledgeNodeId
            val storedSkill = currentSkillByNode[node]
            val lastAnswerAt = lastAnswerAtByNode[node]
            val skill = skillAt(
                priorSkill = priorSkill,
                difficulty = difficultyByNode[node],
                storedSkill = storedSkill,
                lastAnswerAt = lastAnswerAt,
                atEpochMillis = record.occurredAtEpochMillis,
            )
            val prediction = guessAdjusted(guessFor(record.optionsCount), skill)
            val result = if (record.isCorrect) 1.0 else 0.0
            val residual = result - prediction

            // 局部技能：每次作答都更新（prediction.py:217-225）；首答基线 = 先验分支技能
            // （update_phase：current_skill is None → current_skill = prior_skill − difficulty）。
            val baseSkill = storedSkill ?: (priorSkill - (difficultyByNode[node] ?: 0.0))
            currentSkillByNode[node] =
                baseSkill + (if (record.isCorrect) hyper.pfaeGood else hyper.pfaeBad) * residual

            if (storedSkill == null) {
                // 首答门（prediction.py:227-236）：动态步长 α(n) 只在 (用户,题) 首答时更新
                // 全局两项；两个计数在 prepare 期读取（pre-increment），更新后各自 +1。
                // 难度推进方向：答对比预测好（残差>0）→ 难度下降。
                val priorAlpha = hyper.eloAlpha / (1.0 + hyper.eloDynamicAlpha * firstAnswerCountByUser)
                val difficultyAlpha =
                    hyper.eloAlpha / (1.0 + hyper.eloDynamicAlpha * (firstAnswerCountByNode[node] ?: 0))
                priorSkill += priorAlpha * residual
                difficultyByNode[node] = (difficultyByNode[node] ?: 0.0) - difficultyAlpha * residual
                firstAnswerCountByUser += 1
                firstAnswerCountByNode[node] = (firstAnswerCountByNode[node] ?: 0) + 1
            }
            // 上次时间/作答数照写（含 occurredAt=0 的异常事件：写 0 后后续事件按
            // "无有效 last_time" 走先验分支——降级矩阵 R4 §4.5 最后一行）。
            lastAnswerAtByNode[node] = record.occurredAtEpochMillis
            answeredCountByNode[node] = (answeredCountByNode[node] ?: 0) + 1
        }

        return EloLearnerState(
            priorSkill = priorSkill,
            difficultyByNode = difficultyByNode,
            currentSkillByNode = currentSkillByNode,
            lastAnswerAtByNode = lastAnswerAtByNode,
            answeredCountByNode = answeredCountByNode,
            firstAnswerCountByUser = firstAnswerCountByUser,
            firstAnswerCountByNode = firstAnswerCountByNode,
        )
    }

    /**
     * 点预测 p = guess + (1−guess)·sigmoid(skill)（prediction.py:353-364）。
     * 全精度返回——跨引擎 ulp 钉值只发生在 [predictInterval] 的区间构造（R4 §3.5）。
     */
    fun predictCorrectness(
        state: EloLearnerState,
        knowledgeNodeId: String,
        optionsCount: Int?,
        atEpochMillis: Long,
    ): Double {
        val skill = skillAt(
            priorSkill = state.priorSkill,
            difficulty = state.difficultyByNode[knowledgeNodeId],
            storedSkill = state.currentSkillByNode[knowledgeNodeId],
            lastAnswerAt = state.lastAnswerAtByNode[knowledgeNodeId],
            atEpochMillis = atEpochMillis,
        )
        return guessAdjusted(guessFor(optionsCount), skill)
    }

    override fun predictInterval(
        state: EloLearnerState,
        knowledgeNodeId: String,
        optionsCount: Int?,
        decisionAtEpochMillis: Long,
    ): PredictedCorrectnessInterval {
        val point = predictCorrectness(state, knowledgeNodeId, optionsCount, decisionAtEpochMillis)
        val answeredCount = state.answeredCountByNode[knowledgeNodeId] ?: 0
        // h(n) = clamp(h0/√(1+n), 0.05, 0.5)（R4 §4.4）。它消灭两个具体失败：
        // ① 点值无法实例化既有区间契约（PredictedCorrectnessInterval 不接受点值）；
        // ② 冷启动裸点值误入走廊——n=0 时 h=0.5，区间宽 1.0，走廊（宽 0.30/0.40）必拒。
        // 走廊打开节奏（h 何时 ≤ 0.15）= 校准手柄，h0 与 0.05/0.5 都 PENDING_CALIBRATION。
        val halfWidth =
            (hyper.intervalHalfWidthInitial / sqrt(1.0 + answeredCount)).coerceIn(MIN_HALF_WIDTH, MAX_HALF_WIDTH)
        // roundTo(6)：跨引擎 exp 末位差（≤1 ulp）钉死在走廊比较之外——只钉区间构造，
        // 折叠内部保持全精度（R4 §3.5）。
        val lower = (point - halfWidth).roundTo(6).coerceIn(0.0, 1.0)
        val upper = (point + halfWidth).roundTo(6).coerceIn(0.0, 1.0)
        return PredictedCorrectnessInterval(
            lowerBound = lower,
            upperBound = upper,
            modelVersion = modelVersion,
            generatedAtEpochMillis = decisionAtEpochMillis,
            validUntilEpochMillis = decisionAtEpochMillis + hyper.predictionValidityMillis,
        )
    }

    /**
     * 预测技能（prediction.py:194-199 的两分支 + R4 §2.2.4 降级）：
     * - (用户,节点) 无历史 → 先验分支 `prior − difficulty`；
     * - 有历史且上次时间有效、决策时间不早于它 → 重答分支
     *   `current_skill + time_shift/max(seconds_ago, 0.001)`；
     * - 有历史但上次时间缺失/≤0/时间倒挂 → 先验分支（R4 的保守降级，见类 KDoc 偏差 1）。
     *
     * replay 与 [predictCorrectness] 共用本函数：两处分支判据单源，不漂移。
     */
    private fun skillAt(
        priorSkill: Double,
        difficulty: Double?,
        storedSkill: Double?,
        lastAnswerAt: Long?,
        atEpochMillis: Long,
    ): Double {
        val reAnswer = storedSkill != null &&
            lastAnswerAt != null && lastAnswerAt > 0 &&
            atEpochMillis > 0 &&
            atEpochMillis >= lastAnswerAt
        return if (reAnswer) {
            val secondsAgo = (atEpochMillis - requireNotNull(lastAnswerAt)) / 1000.0
            storedSkill + hyper.timeShiftSeconds / maxOf(secondsAgo, MIN_SECONDS_AGO)
        } else {
            priorSkill - (difficulty ?: 0.0)
        }
    }

    /** 机会水平修正：guess = 1/选项数；null 或 ≤0 → 0（修正关闭，proso :356-359）。 */
    private fun guessFor(optionsCount: Int?): Double =
        optionsCount?.takeIf { it > 0 }?.let { 1.0 / it } ?: 0.0

    private fun guessAdjusted(guess: Double, skill: Double): Double =
        guess + (1.0 - guess) * sigmoid(skill)

    private fun Double.roundTo(decimalPlaces: Int): Double {
        val scale = 10.0.pow(decimalPlaces)
        return round(this * scale) / scale
    }

    private companion object {
        /** proso 的秒距下限（prediction.py:199 `max(seconds_ago, 0.001)`）。 */
        const val MIN_SECONDS_AGO = 0.001

        /** h(n) 夹逼界（R4 §4.4 规则字面值；与 h0 一样属校准手柄）。 */
        const val MIN_HALF_WIDTH = 0.05
        const val MAX_HALF_WIDTH = 0.5

        private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))
    }
}
