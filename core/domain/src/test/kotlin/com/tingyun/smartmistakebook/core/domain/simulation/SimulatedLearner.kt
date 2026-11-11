package com.tingyun.smartmistakebook.core.domain.simulation

import com.tingyun.smartmistakebook.core.domain.AlgorithmConstants
import com.tingyun.smartmistakebook.core.domain.CalibrationInput
import com.tingyun.smartmistakebook.core.domain.CalibrationReportBuilder
import com.tingyun.smartmistakebook.core.domain.ClearlyMasteredForSkipPolicy
import com.tingyun.smartmistakebook.core.domain.FsrsEvidenceRatingMapper
import com.tingyun.smartmistakebook.core.domain.FsrsMemoryUpdateModel
import com.tingyun.smartmistakebook.core.domain.FsrsRating
import com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.domain.LogDurationModel
import com.tingyun.smartmistakebook.core.domain.PlannedReasonCalibration
import com.tingyun.smartmistakebook.core.domain.ReviewCandidate
import com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2
import com.tingyun.smartmistakebook.core.domain.ReviewPlanningRequest
import com.tingyun.smartmistakebook.core.domain.ReviewSample
import com.tingyun.smartmistakebook.core.domain.ReviewScoringWeights
import com.tingyun.smartmistakebook.core.domain.SchedulingEvaluationHarness
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.IncrementalLearningEvent
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LearningModelVersion
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.StudyDayMath
import java.security.MessageDigest
import java.util.Locale
import java.util.Random

/**
 * 离线模拟学生（批次 49 / P1-6）。方法论与冻结口径见
 * `docs/research/2026-11-08-calibration-method.md` §3–§4；常量值本身不是校准结论，
 * **所有输出都只对"这个世界"成立（model-based）**，不得当作真库结论。
 *
 * 日循环与生产同序：
 * `snapshot → ReviewPlannerV2.plan → 逐题作答 → LearningProjector（内部 FsrsMemoryUpdateModel）
 * → review_log 行 → 次日`。
 *
 * 为什么走生产投影器而不是在这里重写一遍掌握度公式：知识点掌握态（β-二项 s/f、
 * 记忆卡、观察列表、calibrated 状态、CONFLICTED 判定）由
 * [LearningProjector.projectMastery] 一处定义；测试夹具再抄一份就会重演本仓反复关掉的
 * "同一规则两处权威、改一处另一处照旧跑"（`AlgorithmConstants` / `MasterySmoothing` 的
 * KDoc 都记着同型缺陷）。因此这里的"世界真值"只落在两处：① 注入投影器的
 * [FsrsMemoryUpdateModel] 参数集（[SimulationConfig.truthParameters]）；② 作答抽样。
 *
 * **故意错配（T4）**：[SimulationConfig.carelessRate] 让 `P(答对) = R × (1 − slip)`，
 * planner 的 FSRS 预测不知道这个 slip。若世界与 planner 的内部模型完全同构，任何权重/幅度
 * 都只能在 planner 自己的目标函数上"最优"，测量退化为自证（R1 §3.2）。
 *
 * **本 harness 明确不建模**（避免把"没测"读成"测过了"）：
 * - 考试日召回探针（R1 §3.0 的 20 次/KC 抽样）——那是 G6 的结局变量，属 P1-8 的臂；
 *   本类只输出"考试科覆盖"这类可从复习流直接读出的量；
 * - avoidance 信号（[ReviewCandidate.avoidance] 恒 false）——模拟里没有"切走/低分"行为通道，
 *   所以 AVOIDANCE_WEIGHT 在本世界里不可测；如实登记，不用假信号把它填满；
 * - 同日重复作答（每题每天至多一次）——排程计划本身不重复练习题，且同日重复在拟合口径里
 *   只训不评（`SchedulingEvaluation.kt:190-197`）。
 */
class SimulatedLearner(private val config: SimulationConfig) {

    private val truthParameters: DoubleArray = config.truthParameters.copyOf()
    private val truthDecay: Double = -truthParameters[20]
    private val world: World = buildWorld(config)

    /**
     * 跑满 [SimulationConfig.horizonDays] 个学习日并返回指标 + review_log + 末态快照。
     *
     * 纯函数式：同一实例重复调用结果逐位相同（无外部状态）；同一 config 两次构造亦然。
     */
    fun run(): SimulationRun {
        val durationModel = LogDurationModel().also { model ->
            config.durationSecondsBySubject.forEach { (subjectId, seconds) ->
                model.record(LEARNER_ID, subjectId, null, 0.0, seconds.toDouble())
            }
        }
        val planner = ReviewPlannerV2(durationModel = durationModel, scoring = config.scoringWeights)
        val projector = LearningProjector(memoryUpdateModel = FsrsMemoryUpdateModel(truthParameters))

        var snapshot = LearnerSnapshot.empty(LEARNER_ID, LearningProjector.VERSION)
        var nextSequence = 1L
        val reviewLog = mutableListOf<ReviewSample>()
        val lastReviewRowByUnit = mutableMapOf<String, ReviewSample>()
        val attempts = mutableListOf<SimulatedAttempt>()
        val predictionPairs = mutableListOf<CalibrationInput>()
        val reviewedDaysByKnowledgeNode = mutableMapOf<String, MutableSet<Int>>()
        val dailyWorkload = mutableListOf<DailyWorkload>()

        for (studyDay in 1..config.horizonDays) {
            val dayStart = BASE_EPOCH_MILLIS + studyDay * DAY_MILLIS
            val budgetSeconds = budgetSecondsFor(studyDay)
            val plan = planner.plan(
                ReviewPlanningRequest(
                    learnerSnapshot = snapshot,
                    candidates = candidatesFor(studyDay, snapshot),
                    localDayEpochDay = StudyDayMath.localEpochDayOf(dayStart, UTC_OFFSET_MINUTES),
                    timeZoneId = TIME_ZONE_ID,
                    timeBudgetSeconds = budgetSeconds,
                    planningAtEpochMillis = dayStart,
                    knowledgePrerequisites = world.prerequisites,
                ),
            )

            val events = mutableListOf<IncrementalLearningEvent>()
            var elapsedSeconds = 0
            plan.queueItems.forEach { item ->
                val question = world.questionsById.getValue(item.practiceUnitId)
                val attemptAt = dayStart + elapsedSeconds * MILLIS_PER_SECOND
                val durationSeconds = item.estimatedDurationSeconds
                val studyDayEpochDay = StudyDayMath.localEpochDayOf(attemptAt, UTC_OFFSET_MINUTES)
                // 作答前的题卡状态（`snapshot` 在当日批次投影前取值）——Brier/ECE 的预测
                // 必须来自"答题前"的状态，事后取值就是把结果泄漏进预测。
                val priorMemory = snapshot.problemMemoryStates[item.practiceUnitId]
                val predictedRetention = priorMemory?.let { memory ->
                    retentionAt(attemptAt, memory.lastReviewedAtEpochMillis, memory.stabilityDays, PRODUCT_DECAY)
                }
                val worldRetention = priorMemory?.let { memory ->
                    retentionAt(attemptAt, memory.lastReviewedAtEpochMillis, memory.stabilityDays, truthDecay)
                } ?: NEW_CARD_CORRECT_PROBABILITY
                val correct = answerDraw(studyDay, item.practiceUnitId) <
                    worldRetention * (1.0 - config.carelessRate)

                // 评级词表与生产一致（R1 §3.0）：错 → AGAIN；对但预测回忆低 → 折价正确 = HARD
                // （`FsrsEvidenceRatingMapper.schedulingRatingFor` 的 LOW_CONFIDENCE_CORRECT_CEILING
                // 正是"折扣过的独立正确"）；其余 → GOOD。EASY 在生产里结构性不可达，不模拟。
                val lowConfidenceCorrect = correct && worldRetention < LOW_CONFIDENCE_CORRECT_MAX_PROBABILITY
                val reason = if (correct) {
                    LearningEvidenceReason.INDEPENDENT_CORRECT
                } else {
                    LearningEvidenceReason.INDEPENDENT_INCORRECT
                }
                val weight = if (lowConfidenceCorrect) LOW_CONFIDENCE_CORRECT_WEIGHT else 1.0
                val sequence = nextSequence++
                events += attempt(
                    sequence = sequence,
                    question = question,
                    reason = reason,
                    weight = weight,
                    correct = correct,
                    attemptAt = attemptAt,
                    durationSeconds = durationSeconds,
                    studyDayEpochDay = studyDayEpochDay,
                    dayStart = dayStart,
                )

                // review_log 行：字段口径逐项对齐 `ReviewLogSink.record`（rating 是调度实际
                // 使用的档、state 按"无前条/前条 AGAIN/同日/跨日"派生、deltaTDays 是学习日差）。
                val previousRow = lastReviewRowByUnit[item.practiceUnitId]
                val deltaTDays = if (priorMemory == null) {
                    0.0
                } else {
                    val previousEpochDay = StudyDayMath.localEpochDayOf(
                        priorMemory.lastReviewedAtEpochMillis,
                        UTC_OFFSET_MINUTES,
                    )
                    (studyDayEpochDay - previousEpochDay).coerceAtLeast(0).toDouble()
                }
                val state = when {
                    previousRow == null -> ReviewSample.STATE_NEW
                    previousRow.rating == FsrsRating.AGAIN -> ReviewSample.STATE_RELEARNING
                    StudyDayMath.localEpochDayOf(
                        previousRow.reviewedAtEpochMillis,
                        UTC_OFFSET_MINUTES,
                    ) == studyDayEpochDay -> ReviewSample.STATE_LEARNING
                    else -> ReviewSample.STATE_REVIEW
                }
                val row = ReviewSample(
                    practiceUnitId = item.practiceUnitId,
                    reviewedAtEpochMillis = attemptAt,
                    rating = FsrsEvidenceRatingMapper.schedulingRatingFor(reason, weight),
                    durationMs = durationSeconds * MILLIS_PER_SECOND,
                    sourceKind = ReviewSample.ATTEMPT_KIND,
                    // 生产口径：排程原因快照 = 原因名排序后逗号连接
                    // （`StudyReviewPlannerService.kt:683`），per-reason 校准按该串分组。
                    plannedReason = item.reasons.map { it.name }.sorted().joinToString(","),
                    deltaTDays = deltaTDays,
                    state = state,
                )
                reviewLog += row
                lastReviewRowByUnit[item.practiceUnitId] = row

                predictedRetention?.let { probability ->
                    predictionPairs += CalibrationInput(
                        predictedScore = probability,
                        wasIndependentCorrect = correct,
                    )
                }
                attempts += SimulatedAttempt(
                    day = studyDay,
                    practiceUnitId = item.practiceUnitId,
                    subjectId = question.subjectId,
                    knowledgeNodeId = question.knowledgeNodeId,
                    plannedReason = row.plannedReason.orEmpty(),
                    durationSeconds = durationSeconds,
                    predictedRetention = predictedRetention,
                    correct = correct,
                )
                reviewedDaysByKnowledgeNode
                    .getOrPut(question.knowledgeNodeId) { mutableSetOf() }
                    .add(studyDay)
                elapsedSeconds += durationSeconds
            }

            if (events.isNotEmpty()) {
                snapshot = projector.project(snapshot, events, nextSequence - 1).snapshot
            }

            dailyWorkload += DailyWorkload(
                day = studyDay,
                budgetSeconds = budgetSeconds,
                plannedSeconds = plan.totalEstimatedDurationSeconds,
                answeredSeconds = elapsedSeconds,
                plannedItemCount = plan.queueItems.size,
            )
        }

        // 无数据不填数（R12 / T11）：`CalibrationReportBuilder` 在空输入上把 Brier/ECE 返回
        // 0.0——而 0.0 正是这两个指标的最优值，"没数据"会读成"完美校准"。因此空集走 null。
        val calibration = if (predictionPairs.isEmpty()) {
            null
        } else {
            CalibrationReportBuilder.build(
                modelVersion = LearningModelVersion(
                    modelId = FsrsMemoryUpdateModel.ALGORITHM_ID,
                    version = ReviewPlannerV2.VERSION,
                    algorithmHash = "offline-simulation",
                ),
                resolved = predictionPairs,
                totalPredictions = predictionPairs.size,
                generatedAtEpochMillis = horizonEndEpochMillis(),
            )
        }

        // 掌握数是**读侧**读数：用产品此刻的 E 判据在同一份末态上重算（21 天 ∧ 0.9，
        // `ClearlyMasteredForSkipPolicy.meetsMemoryCriterion`），不改阈值、不看存储 status
        // （status 是写入瞬间的真相，不会随时间衰减）。
        val masteredKnowledgeNodeCount = world.knowledgeNodes.count { node ->
            val state = snapshot.knowledgeMasteryStates[node.id] ?: return@count false
            ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(
                memoryStabilityDays = state.memoryStabilityDays,
                lastAttemptAtEpochMillis = state.lastAttemptAtEpochMillis,
                atEpochMillis = horizonEndEpochMillis(),
                decay = PRODUCT_DECAY,
            )
        }

        val perSubjectCoverage = world.subjectIds.map { subjectId ->
            val nodes = world.knowledgeNodes.filter { it.subjectId == subjectId }
            SubjectCoverage(
                subjectId = subjectId,
                knowledgeNodeCount = nodes.size,
                reviewedKnowledgeNodeCount = nodes.count { node ->
                    reviewedDaysByKnowledgeNode[node.id].orEmpty().isNotEmpty()
                },
            )
        }
        val examSubjectCoverage = world.exams.map { exam ->
            val nodes = world.knowledgeNodes.filter { it.subjectId == exam.subjectId }
            // 窗口与生产 ramp 同窗：`daysUntil in 0..examWindowDays` 且 ramp = 1 − d/window，
            // d = window 处 ramp = 0（无信号），故有效窗口是考前 window 天（含考试当天）。
            val firstCoveredDay = exam.day - config.examWindowDays + 1
            ExamCoverage(
                subjectId = exam.subjectId,
                examDay = exam.day,
                knowledgeNodeCount = nodes.size,
                reviewedKnowledgeNodeCountInWindow = nodes.count { node ->
                    reviewedDaysByKnowledgeNode[node.id].orEmpty()
                        .any { reviewedDay -> reviewedDay in firstCoveredDay..exam.day }
                },
            )
        }

        val metrics = SimulationMetrics(
            seed = config.seed,
            carelessRate = config.carelessRate,
            horizonDays = config.horizonDays,
            dailyBudgetSeconds = config.dailyBudgetSeconds,
            examCapacityExtraFraction = config.examCapacityExtraFraction,
            knowledgeNodeCount = world.knowledgeNodes.size,
            questionCount = world.questions.size,
            answeredItemCount = attempts.size,
            newCardAttemptCount = attempts.count { it.predictedRetention == null },
            scoredPredictionCount = predictionPairs.size,
            brierScore = calibration?.overallBrierScore,
            expectedCalibrationError = calibration?.expectedCalibrationError,
            logLoss = calibration?.overallLogLoss,
            maximumCalibrationDeviation = calibration?.maximumCalibrationDeviation,
            masteredKnowledgeNodeCount = masteredKnowledgeNodeCount,
            reviewedKnowledgeNodeCount = reviewedDaysByKnowledgeNode.size,
            perSubjectCoverage = perSubjectCoverage,
            examSubjectCoverage = examSubjectCoverage,
            perReasonRecall = SchedulingEvaluationHarness.calibratePlannedReasons(reviewLog),
            dailyWorkload = dailyWorkload,
            totalBudgetSeconds = dailyWorkload.sumOf { it.budgetSeconds.toLong() },
            totalAnsweredSeconds = dailyWorkload.sumOf { it.answeredSeconds.toLong() },
            overrunDays = dailyWorkload.count { it.answeredSeconds > it.budgetSeconds },
        )
        return SimulationRun(metrics = metrics, reviewLog = reviewLog, attempts = attempts, finalSnapshot = snapshot)
    }

    /**
     * 世界结构摘要（不含任何作答）：用于机检"留出 seed 不复用"与"换 seed 真的换世界"。
     * 浮点用 [java.lang.Double.toHexString] 进入摘要（R1 §3.6：十进制格式化会掩盖末位差异）。
     */
    fun worldFingerprint(): String = sha256(
        buildString {
            world.knowledgeNodes.forEach { node ->
                append("kc:").append(node.id).append(':').append(node.subjectId).append(';')
            }
            world.prerequisites.toSortedMap().forEach { (nodeId, prerequisites) ->
                append("prereq:").append(nodeId).append('<')
                    .append(prerequisites.sorted().joinToString(",")).append(';')
            }
            world.questions.forEach { question ->
                append("q:").append(question.practiceUnitId)
                    .append(':').append(question.knowledgeNodeId)
                    .append(':').append(question.itemFamilyId)
                    .append(':').append(question.sourceBundleId)
                    .append(':').append(java.lang.Double.toHexString(question.difficulty))
                    .append(';')
            }
            world.exams.sortedWith(compareBy(Exam::day, Exam::subjectId)).forEach { exam ->
                append("exam:").append(exam.subjectId).append(':').append(exam.day).append(';')
            }
        },
    )

    private fun candidatesFor(studyDay: Int, snapshot: LearnerSnapshot): List<ReviewCandidate> =
        world.questions.map { question ->
            val prior = snapshot.problemMemoryStates[question.practiceUnitId]
            ReviewCandidate(
                practiceUnitId = question.practiceUnitId,
                knowledgeNodeIds = setOf(question.knowledgeNodeId),
                itemFamilyId = question.itemFamilyId,
                sourceBundleId = question.sourceBundleId,
                subjectId = question.subjectId,
                difficulty = question.difficulty,
                estimatedDurationSeconds = config.durationSecondsBySubject.getValue(question.subjectId),
                examPriority = examPriorityFor(question.subjectId, studyDay),
                // 与生产同义：连错才叫"重复错题"（单次 AGAIN 由 RECENT_LAPSE 表达）。
                // 不建模这条会让 REPEATED_MISTAKE 权重在扫描里恒为零、响应面恒平。
                repeatMistakePriority = prior?.let { memory ->
                    if (memory.lapseCount >= REPEAT_MISTAKE_MIN_LAPSES) {
                        (memory.lapseCount / 3.0).coerceAtMost(1.0)
                    } else {
                        0.0
                    }
                } ?: 0.0,
                // 到期后才"可复习"：逾期越久，LONG_WAITING 的真实来源就是这个时刻。
                eligibleSinceEpochMillis = prior?.nextReviewAtEpochMillis,
                leech = prior?.isLeeched == true,
            )
        }

    /** 与 `StudyReviewPlannerService.examPriorityOf` 同一形状：科目坡道 = 1 − daysUntil/window。 */
    private fun examPriorityFor(subjectId: String, studyDay: Int): Double {
        var best = 0.0
        world.exams.forEach { exam ->
            if (exam.subjectId != subjectId) return@forEach
            val daysUntil = exam.day - studyDay
            if (daysUntil in 0..config.examWindowDays) {
                best = maxOf(best, 1.0 - daysUntil.toDouble() / config.examWindowDays)
            }
        }
        return best.coerceIn(0.0, 1.0)
    }

    /**
     * 与 `StudyReviewPlannerService.examCapacityBudgetSeconds` 同一形状：全天一口预算，
     * 多科并行取各科 ramp 的最大值，`f=0` 时**逐位返回基础预算**（不做任何算术）。
     */
    private fun budgetSecondsFor(studyDay: Int): Int {
        if (config.examCapacityExtraFraction <= 0.0) return config.dailyBudgetSeconds
        val ramp = world.exams.maxOfOrNull { exam ->
            val daysUntil = exam.day - studyDay
            if (daysUntil in 0..config.examWindowDays) {
                1.0 - daysUntil.toDouble() / config.examWindowDays
            } else {
                0.0
            }
        } ?: 0.0
        if (ramp <= 0.0) return config.dailyBudgetSeconds
        val extraSeconds = (
            config.dailyBudgetSeconds * config.examCapacityExtraFraction * ramp
            ).toInt()
        return config.dailyBudgetSeconds + extraSeconds
    }

    /**
     * 单次作答的噪声抽样。种子只由 `(world seed, 学习日, 题 id)` 决定，**不依赖当日选了哪些题、
     * 也不依赖抽签顺序**——否则换一个权重臂就会打乱后续所有抽样，"配对世界"退化成
     * "同一批 seed 的两次独立实验"，臂间差异被抽样噪声污染（R1 R3）。
     */
    private fun answerDraw(studyDay: Int, practiceUnitId: String): Double {
        val attemptSeed = config.seed * 1_000_003L +
            studyDay * 31_337L +
            practiceUnitId.hashCode().toLong()
        return Random(attemptSeed).nextDouble()
    }

    private fun retentionAt(
        attemptAtEpochMillis: Long,
        lastReviewedAtEpochMillis: Long,
        stabilityDays: Double,
        decay: Double,
    ): Double {
        // 与 `ForgettingCurve.estimateAt` 同口径：墙钟时长按整日向下取整。
        val elapsedDays = (attemptAtEpochMillis - lastReviewedAtEpochMillis)
            .coerceAtLeast(0) / DAY_MILLIS
        return FsrsScheduleMath.retention(elapsedDays.toDouble(), stabilityDays, decay)
    }

    private fun attempt(
        sequence: Long,
        question: QuestionSpec,
        reason: LearningEvidenceReason,
        weight: Double,
        correct: Boolean,
        attemptAt: Long,
        durationSeconds: Int,
        studyDayEpochDay: Long,
        dayStart: Long,
    ): Attempt = Attempt(
        attemptId = "attempt-$sequence",
        presentationId = "presentation-$sequence",
        responseOrdinal = 1,
        assessmentSnapshot = AssessmentEvidenceSnapshot(
            snapshotId = "snapshot-$sequence",
            assessmentItemId = "assessment-item-${question.practiceUnitId}",
            practiceUnitId = question.practiceUnitId,
            problemRevisionId = "revision-${question.practiceUnitId}",
            answerSpecId = "answer-spec-v1",
            itemFamilyId = question.itemFamilyId,
            sourceBundleId = question.sourceBundleId,
            taxonomyVersion = "taxonomy-v1",
            verification = AssessmentSnapshotVerification.VERIFIED,
            calibration = CalibrationSnapshot(
                support = CalibrationSupport.SUPPORTED,
                sourceId = "simulation-calibration-source",
                version = "simulation-calibration-v1",
                validFromEpochMillis = 0,
                // 窗口覆盖整个 horizon：过期的校准窗口会把每次作答都变成"未受支持观察"，
                // 于是 CALIBRATION_CHECK 恒在、弱点信号恒定——那是夹具假象，不是学生行为。
                validUntilEpochMillis = horizonEndEpochMillis() + DAY_MILLIS,
            ),
            attributions = listOf(
                KnowledgeEvidenceAttribution(
                    bindingId = "binding-${question.practiceUnitId}",
                    knowledgeNodeId = question.knowledgeNodeId,
                    weight = 1.0,
                    basisRevisionId = "revision-${question.practiceUnitId}",
                    taxonomyVersion = "taxonomy-v1",
                    role = EvidenceAttributionRole.PRIMARY,
                    certainty = EvidenceAttributionCertainty.DIRECT,
                ),
            ),
            capturedAtEpochMillis = dayStart,
        ),
        evidence = LearningEvidence(
            direction = if (correct) {
                LearningEvidenceDirection.POSITIVE
            } else {
                LearningEvidenceDirection.NEGATIVE
            },
            weight = weight,
            reason = reason,
        ),
        problemMemoryOutcome = if (correct) {
            ProblemMemoryOutcome.INDEPENDENT_RECALL
        } else {
            ProblemMemoryOutcome.RETRIEVAL_FAILURE
        },
        occurredAtEpochMillis = attemptAt,
        durationSeconds = durationSeconds,
        studyDay = StudyDayContext(
            epochDay = studyDayEpochDay,
            timeZoneId = TIME_ZONE_ID,
            utcOffsetMinutes = UTC_OFFSET_MINUTES,
        ),
        eventSequence = sequence,
    )

    private fun horizonEndEpochMillis(): Long =
        BASE_EPOCH_MILLIS + (config.horizonDays + 1) * DAY_MILLIS

    private companion object {
        const val LEARNER_ID = "simulation-learner"
        const val TIME_ZONE_ID = "UTC"
        const val UTC_OFFSET_MINUTES = 0
        const val MILLIS_PER_SECOND = 1_000L

        /** 冷启动：新题没有先验 R，取无信息先验 0.5（不计入 Brier/ECE）。 */
        const val NEW_CARD_CORRECT_PROBABILITY = 0.5

        /** R1 §3.0 的评级口径：对且预测回忆 < 0.55 → HARD。 */
        const val LOW_CONFIDENCE_CORRECT_MAX_PROBABILITY = 0.55

        /**
         * 折价正确档：`FsrsEvidenceRatingMapper.schedulingRatingFor` 在
         * `weight < LOW_CONFIDENCE_CORRECT_CEILING (0.85)` 时把独立正确判为 HARD。
         */
        const val LOW_CONFIDENCE_CORRECT_WEIGHT = 0.7

        /** 连续两次遗忘才算"重复错题"（单次由 `ReviewReason.RECENT_LAPSE` 表达）。 */
        const val REPEAT_MISTAKE_MIN_LAPSES = 2

        /**
         * 产品侧 FSRS 衰减（Brier/ECE 的预测与掌握读数都用它）：世界真值可以不同，
         * 但"产品会不会判对/判掌握"只能用产品自己的参数回答。
         */
        val PRODUCT_DECAY: Double = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]

        /**
         * 夹具时间原点：2026-01-05 09:00 UTC（第 1 个学习日的计划时刻）。远离纪元，
         * `StudyDayMath` 的 04:00 学习日界下日序恒为正。
         */
        val BASE_EPOCH_MILLIS: Long = 1_767_603_600_000L
        val DAY_MILLIS: Long = AlgorithmConstants.DAY_MILLIS
    }
}

/** 规范化摘要的单源（世界指纹与指标摘要共用同一实现）。 */
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }

/**
 * 模拟学生的世界参数（`docs/research/2026-11-08-calibration-method.md` §3.0）。
 *
 * [seed] 同时决定世界结构（先修图/科目/难度）与噪声抽样；[carelessRate] 是**唯一**的
 * 世界错配项（`P(答对) = R × (1 − slip)`）。预注册（P1-7）冻结的网格与 seed 列表
 * 通过复制本 config 表达，不改本类默认值。
 */
data class SimulationConfig(
    val seed: Long,
    val carelessRate: Double = 0.05,
    val horizonDays: Int = 120,
    val subjectCount: Int = 5,
    val knowledgeNodesPerSubject: Int = 6,
    val questionsPerKnowledgeNode: Int = 10,
    /** 与设置默认同源：`dailyReviewMinutes = 20` → 1200s（`StudyExperienceRepository.kt:736-739`）。 */
    val dailyBudgetSeconds: Int = 1_200,
    /** G6 考前扩容幅度 `f`；0.0 = 无扩容（默认臂，窗口外逐位不变）。 */
    val examCapacityExtraFraction: Double = 0.0,
    val examWindowDays: Int = 14,
    val examDayBySubject: Map<String, Int> = DEFAULT_EXAM_DAY_BY_SUBJECT,
    val durationSecondsBySubject: Map<String, Int> = DEFAULT_DURATION_SECONDS_BY_SUBJECT,
    /** 世界真值 FSRS 参数（注入投影器）；默认 = 产品参数中心。 */
    val truthParameters: DoubleArray = FsrsScheduleMath.DEFAULT_PARAMETERS,
    /**
     * 排程打分权重（批次 49 P1-8 权重坐标扫描的注入点，预注册 §5.2 的 17 个臂）。
     * 默认 = 产品现状 [ReviewScoringWeights.DEFAULT]，故不传时世界逐位不变。
     */
    val scoringWeights: ReviewScoringWeights = ReviewScoringWeights.DEFAULT,
) {
    init {
        require(horizonDays > 0) { "Simulation horizon must be positive" }
        require(carelessRate in 0.0..0.5) { "Careless rate must lie within 0.0..0.5" }
        require(subjectCount > 0) { "Subject count must be positive" }
        require(knowledgeNodesPerSubject > 0) { "Knowledge nodes per subject must be positive" }
        require(questionsPerKnowledgeNode > 0) { "Questions per knowledge node must be positive" }
        require(dailyBudgetSeconds > 0) { "Daily review budget must be positive" }
        require(examCapacityExtraFraction in 0.0..1.0) {
            "Exam capacity extra fraction must lie within 0.0..1.0"
        }
        require(examWindowDays >= 1) { "Exam window must cover at least one day" }
        val subjectIds = (0 until subjectCount).map { subjectId(it) }.toSet()
        require(examDayBySubject.keys.all(subjectIds::contains)) {
            "Every exam subject must be one of $subjectIds"
        }
        require(examDayBySubject.values.all { it in 1..horizonDays }) {
            "Every exam day must fall inside the $horizonDays-day horizon"
        }
        require(durationSecondsBySubject.keys.containsAll(subjectIds)) {
            "A duration profile is required for every subject in $subjectIds"
        }
        require(durationSecondsBySubject.values.all { it > 0 }) {
            "Subject durations must be positive"
        }
        FsrsScheduleMath.requireValid(truthParameters)
    }

    companion object {
        const val DEFAULT_SUBJECT_COUNT = 5
        const val DEFAULT_KNOWLEDGE_NODES_PER_SUBJECT = 6
        const val DEFAULT_QUESTIONS_PER_KNOWLEDGE_NODE = 10

        /** 5 科各 1 场、故意错开（R1 §3.0），才能走到"多科并行取最大 ramp"的分支。 */
        val DEFAULT_EXAM_DAY_BY_SUBJECT: Map<String, Int> = linkedMapOf(
            "subject-0" to 108,
            "subject-1" to 112,
            "subject-2" to 116,
            "subject-3" to 118,
            "subject-4" to 120,
        )

        /** 科目级作答时长画像（秒）。写在夹具里是为了让"日工作量/预算"不是恒等式。 */
        val DEFAULT_DURATION_SECONDS_BY_SUBJECT: Map<String, Int> = linkedMapOf(
            "subject-0" to 45,
            "subject-1" to 55,
            "subject-2" to 65,
            "subject-3" to 75,
            "subject-4" to 85,
        )

        fun subjectId(index: Int): String = "subject-$index"
    }
}

/** 一次模拟运行的全部产出。 */
data class SimulationRun(
    val metrics: SimulationMetrics,
    /**
     * 内存 review_log（生产行形状：rating / sourceKind=ATTEMPT / plannedReason / state /
     * deltaTDays / durationMs）。可直接喂 `FsrsParameterOptimizer` / `evaluateSchedulingModels`。
     */
    val reviewLog: List<ReviewSample>,
    val attempts: List<SimulatedAttempt>,
    val finalSnapshot: LearnerSnapshot,
)

/** 一次作答的模拟层事实（review_log 行之外，指标需要题/科/KC 维度）。 */
data class SimulatedAttempt(
    val day: Int,
    val practiceUnitId: String,
    val subjectId: String,
    val knowledgeNodeId: String,
    val plannedReason: String,
    val durationSeconds: Int,
    /** null = 新卡（没有先验 R），不计入 Brier/ECE。 */
    val predictedRetention: Double?,
    val correct: Boolean,
)

/** 单日工作量：计划时长 vs 预算 vs 实际作答时长（模拟不丢题，后两者相等）。 */
data class DailyWorkload(
    val day: Int,
    val budgetSeconds: Int,
    val plannedSeconds: Int,
    val answeredSeconds: Int,
    val plannedItemCount: Int,
) {
    val utilization: Double
        get() = if (budgetSeconds == 0) 0.0 else answeredSeconds.toDouble() / budgetSeconds
}

/** 整段 horizon 内某科的 KC 覆盖（至少复习过一次的 KC 占比）。 */
data class SubjectCoverage(
    val subjectId: String,
    val knowledgeNodeCount: Int,
    val reviewedKnowledgeNodeCount: Int,
) {
    val reviewedShare: Double
        get() = if (knowledgeNodeCount == 0) 0.0 else reviewedKnowledgeNodeCount.toDouble() / knowledgeNodeCount
}

/** 考前窗口（生产 ramp 同窗）内该科 KC 覆盖。 */
data class ExamCoverage(
    val subjectId: String,
    val examDay: Int,
    val knowledgeNodeCount: Int,
    val reviewedKnowledgeNodeCountInWindow: Int,
) {
    val reviewedShare: Double
        get() = if (knowledgeNodeCount == 0) 0.0 else reviewedKnowledgeNodeCountInWindow.toDouble() / knowledgeNodeCount
}

/**
 * 模拟指标（A 线 Brier/ECE + 工作量 + 覆盖 + 掌握数 + per-reason recall）。
 *
 * 浮点缺样本时一律 `null`（R12/T11）：`CalibrationReportBuilder` 在空输入上返回 0.0，
 * 而 0.0 是 Brier/ECE 的最优值——用 0.0 冒充"无数据"等于把 BLOCKED 读成满分。
 */
data class SimulationMetrics(
    val seed: Long,
    val carelessRate: Double,
    val horizonDays: Int,
    val dailyBudgetSeconds: Int,
    val examCapacityExtraFraction: Double,
    val knowledgeNodeCount: Int,
    val questionCount: Int,
    val answeredItemCount: Int,
    val newCardAttemptCount: Int,
    val scoredPredictionCount: Int,
    val brierScore: Double?,
    val expectedCalibrationError: Double?,
    val logLoss: Double?,
    val maximumCalibrationDeviation: Double?,
    val masteredKnowledgeNodeCount: Int,
    val reviewedKnowledgeNodeCount: Int,
    val perSubjectCoverage: List<SubjectCoverage>,
    val examSubjectCoverage: List<ExamCoverage>,
    val perReasonRecall: List<PlannedReasonCalibration>,
    val dailyWorkload: List<DailyWorkload>,
    val totalBudgetSeconds: Long,
    val totalAnsweredSeconds: Long,
    val overrunDays: Int,
) {
    /**
     * 规范化摘要（R1 §3.6）：浮点走 `Double.toHexString`，列表按固定顺序，SHA-256 收口。
     * P1-8 的报告把这条 hex 写进 JSON 的 `digest`，任何末位漂移都会变红。
     */
    fun digest(): String {
        val canonical = buildString {
            append("seed=").append(seed)
            append(";careless=").append(hex(carelessRate))
            append(";horizon=").append(horizonDays)
            append(";budget=").append(dailyBudgetSeconds)
            append(";examExtra=").append(hex(examCapacityExtraFraction))
            append(";kc=").append(knowledgeNodeCount)
            append(";questions=").append(questionCount)
            append(";answered=").append(answeredItemCount)
            append(";newCard=").append(newCardAttemptCount)
            append(";scored=").append(scoredPredictionCount)
            append(";brier=").append(hex(brierScore))
            append(";ece=").append(hex(expectedCalibrationError))
            append(";logLoss=").append(hex(logLoss))
            append(";maxDeviation=").append(hex(maximumCalibrationDeviation))
            append(";mastered=").append(masteredKnowledgeNodeCount)
            append(";reviewedKc=").append(reviewedKnowledgeNodeCount)
            append(";totalBudget=").append(totalBudgetSeconds)
            append(";totalAnswered=").append(totalAnsweredSeconds)
            append(";overrunDays=").append(overrunDays)
            dailyWorkload.forEach { day ->
                append(";day:").append(day.day)
                    .append(',').append(day.budgetSeconds)
                    .append(',').append(day.plannedSeconds)
                    .append(',').append(day.answeredSeconds)
                    .append(',').append(day.plannedItemCount)
            }
            perSubjectCoverage.forEach { coverage ->
                append(";subject:").append(coverage.subjectId)
                    .append(',').append(coverage.knowledgeNodeCount)
                    .append(',').append(coverage.reviewedKnowledgeNodeCount)
            }
            examSubjectCoverage.forEach { coverage ->
                append(";exam:").append(coverage.subjectId)
                    .append(',').append(coverage.examDay)
                    .append(',').append(coverage.knowledgeNodeCount)
                    .append(',').append(coverage.reviewedKnowledgeNodeCountInWindow)
            }
            perReasonRecall.sortedBy { it.plannedReason }.forEach { calibration ->
                append(";reason:").append(calibration.plannedReason)
                    .append(',').append(calibration.sampleCount)
                    .append(',').append(hex(calibration.realizedRecallRate))
                    .append(',').append(hex(calibration.overallRecallRate))
            }
        }
        return sha256(canonical)
    }

    private fun hex(value: Double?): String =
        value?.let(java.lang.Double::toHexString) ?: "null"
}

private data class KnowledgeNodeSpec(
    val id: String,
    val subjectId: String,
    val subjectIndex: Int,
    val indexInSubject: Int,
)

private data class QuestionSpec(
    val practiceUnitId: String,
    val knowledgeNodeId: String,
    val subjectId: String,
    val itemFamilyId: String,
    val sourceBundleId: String,
    val difficulty: Double,
)

private data class Exam(val subjectId: String, val day: Int)

/** 每个知识点的题族数：≥2 才能满足 CONFLICTED 恢复的题目族广度门。 */
private const val FAMILY_COUNT_PER_KNOWLEDGE_NODE = 2

private class World(
    val subjectIds: List<String>,
    val knowledgeNodes: List<KnowledgeNodeSpec>,
    val questions: List<QuestionSpec>,
    val prerequisites: Map<String, Set<String>>,
    val exams: List<Exam>,
) {
    val questionsById: Map<String, QuestionSpec> = questions.associateBy { it.practiceUnitId }
}

/**
 * 世界结构（只吃 [SimulationConfig.seed]）：
 * - 5 科 × 6 KC = 30，`kc-<subject>-<index>`；
 * - 先修链：每科第 1 个 KC 无先修，其余各 1 条（指向前一个），偶数位再加 1 条（指向前两个）
 *   —— 每 KC 1–2 条边（R1 §3.0）；
 * - 300 题，每 KC 10 道题、**2 个题族**（`family-<subject>-<index>-<0|1>`）：一个 KC 只给一个
 *   题族会让 CONFLICTED 的恢复判据（`ClearlyMasteredForSkipPolicy.REQUIRED_FAMILIES = 2`）
 *   结构性不可满足，于是任何一次"已掌握后遗忘"都永久留在冲突态，把队列灌满 CALIBRATION_CHECK
 *   的假信号——这是夹具缺陷，不是学生行为。留 2 个族也让"同族不得相邻"这条硬约束有约束面。
 * - 同科同来源（`source-<subject>`），让 SOURCE_PENALTY 在"整场都在同一科"时真实生效；
 * - 难度 `1 + 9·U(0,1)`；题目顺序按 world seed 打乱——planner 的平局排序沿用输入顺序，
 *   若输入恒按科目聚簇，平局就会系统性地饿死后几个科目（夹具伪影）。
 */
private fun buildWorld(config: SimulationConfig): World {
    val random = Random(config.seed)
    val subjectIds = (0 until config.subjectCount).map(SimulationConfig::subjectId)
    val knowledgeNodes = subjectIds.mapIndexed { subjectIndex, subjectId ->
        (0 until config.knowledgeNodesPerSubject).map { indexInSubject ->
            KnowledgeNodeSpec(
                id = "kc-$subjectIndex-$indexInSubject",
                subjectId = subjectId,
                subjectIndex = subjectIndex,
                indexInSubject = indexInSubject,
            )
        }
    }.flatten()
    val prerequisites = buildMap {
        knowledgeNodes.forEach { node ->
            if (node.indexInSubject == 0) return@forEach
            val prerequisites = linkedSetOf("kc-${node.subjectIndex}-${node.indexInSubject - 1}")
            if (node.indexInSubject >= 2 && node.indexInSubject % 2 == 0) {
                prerequisites += "kc-${node.subjectIndex}-${node.indexInSubject - 2}"
            }
            put(node.id, prerequisites)
        }
    }
    val questions = knowledgeNodes.flatMap { node ->
        (0 until config.questionsPerKnowledgeNode).map { variant ->
            QuestionSpec(
                practiceUnitId = "unit-${node.subjectIndex}-${node.indexInSubject}-$variant",
                knowledgeNodeId = node.id,
                subjectId = node.subjectId,
                itemFamilyId = "family-${node.subjectIndex}-${node.indexInSubject}-${variant % FAMILY_COUNT_PER_KNOWLEDGE_NODE}",
                sourceBundleId = "source-${node.subjectIndex}",
                difficulty = 1.0 + 9.0 * random.nextDouble(),
            )
        }
    }.toMutableList()
    // Fisher–Yates（`java.util.Collections.shuffle` 吃 `java.util.Random`，确定性由 seed 保证）。
    java.util.Collections.shuffle(questions, random)
    val exams = config.examDayBySubject.entries
        .map { (subjectId, day) -> Exam(subjectId, day) }
        .sortedWith(compareBy(Exam::day, Exam::subjectId))
    return World(subjectIds, knowledgeNodes, questions, prerequisites, exams)
}
