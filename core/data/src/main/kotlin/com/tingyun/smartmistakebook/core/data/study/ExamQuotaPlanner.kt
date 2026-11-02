package com.tingyun.smartmistakebook.core.data.study

/**
 * 考试截止日每日配额（批次 40 WS3 item 19）。
 *
 * **消灭的失败**：spec §2.17 写了"考试前把 R 压到 r*_exam"，实现却只有固定的
 * `EARLY_REVIEW_MAX_RETRIEVABILITY = 0.8` 提前闸；考前 14 天与考前 1 天同权，
 * 题多时缺口题排不进去，学生也不知道今天欠了多少。本纯函数把缺口题数、
 * 剩余学习日与单日容量折成 `ceil(缺口 / 剩余天)`，并把超出容量的部分报成
 * [ExamQuotaPlan.unmetCount]，不产生"假配额"。
 *
 * 只读规划：输出不影响 `ReviewPlannerV2.VERSION` 或任何既有计划；真正激活
 * （把 picked 并入默认候选集）需要 bump core/domain 的排程版本，本批登记阻塞。
 *
 * 窗口：`daysUntilExam ∈ 1..14`；考试当天（0）与已过（<0）都返回
 * [ExamQuotaPlan.none]（考试当天再加压没有意义，且避免除零）。
 */
object ExamQuotaPlanner {

    /**
     * @param facts 只应包含**该考试科目**的候选（跨科串味已在调用方按科目过滤；
     *   见 `ReviewCapacityPreviewReader`）。
     * @param studyDayWeights F18 的逐日权重（长度必须等于剩余天数）；null = 每天等权。
     * @param quotaCap 多科目共享当日容量时的本科目名额上限（null = 只受 `budgetSeconds`
     *   折算的单日容量约束）。**消灭的具体失败**：两个在窗科目各自拿整份
     *   `budgetSeconds` 时，当日考试配额会被翻倍；调用方
     *   （`ReviewCapacityScheduler`）必须把同一份单日容量分配到各科，再从这里收口。
     */
    fun plan(
        facts: List<ReviewCapacityFacts>,
        examEpochDay: Long,
        todayEpochDay: Long,
        baseRetention: Double,
        budgetSeconds: Int,
        p50DurationSeconds: Int,
        studyDayWeights: List<Int>? = null,
        quotaCap: Int? = null,
    ): ExamQuotaPlan {
        require(examEpochDay >= 0 && todayEpochDay >= 0) { "Epoch days must not be negative" }
        require(baseRetention in 0.7..ReviewCapacityPolicy.MAX_EXAM_TARGET_RETENTION) {
            "Base retention must be in 0.7..${ReviewCapacityPolicy.MAX_EXAM_TARGET_RETENTION}"
        }
        require(budgetSeconds > 0) { "Time budget must be positive" }
        require(p50DurationSeconds > 0) { "Median duration must be positive" }
        require(quotaCap == null || quotaCap >= 0) { "Quota cap must not be negative" }
        val daysUntilExam = (examEpochDay - todayEpochDay)
        if (daysUntilExam < 1 || daysUntilExam > ReviewCapacityPolicy.EXAM_WINDOW_DAYS) {
            return ExamQuotaPlan.none(baseRetention)
        }
        val windowDays = daysUntilExam.toInt()
        val weights = studyDayWeights?.also { supplied ->
            require(supplied.size == windowDays) {
                "Study-day weights must cover exactly $windowDays days"
            }
            require(supplied.all { it >= 0 }) { "Study-day weights must not be negative" }
            require(supplied.sum() > 0) { "At least one study day is required" }
        }
        val target = ReviewCapacityPolicy.examTargetRetention(
            baseRetention = baseRetention,
            daysUntilExam = windowDays,
        )
        val capacity = ReviewCapacityPolicy.capacityPerDay(budgetSeconds, p50DurationSeconds)
        val studyDaysLeft = weights?.count { it > 0 } ?: windowDays

        val deficits = facts
            .mapNotNull { fact ->
                val atExam = fact.retrievabilityAtExam ?: return@mapNotNull null
                val deficit = ReviewCapacityPolicy.examDeficit(atExam, target)
                if (deficit <= 0.0) null else ExamDeficit(fact = fact, deficit = deficit)
            }
            .sortedWith(
                compareByDescending<ExamDeficit> { it.deficit }
                    .thenBy { it.fact.retrievabilityAtExam ?: 1.0 }
                    .thenBy { it.fact.nextReviewAtEpochMillis }
                    .thenBy { it.fact.practiceUnitId },
            )
        val quota = ReviewCapacityPolicy.dailyQuota(
            remaining = deficits.size,
            studyDaysLeft = studyDaysLeft,
            capPerDay = capacity,
        ).let { uncapped -> quotaCap?.let { minOf(uncapped, it) } ?: uncapped }
        val picked = deficits.take(quota).map(ExamDeficit::fact)
        return ExamQuotaPlan(
            inWindow = true,
            daysUntilExam = windowDays,
            targetRetentionAtExam = target,
            capacityPerDay = capacity,
            studyDaysLeft = studyDaysLeft,
            quota = quota,
            picked = picked,
            deficitCount = deficits.size,
            unmetCount = deficits.size - picked.size,
        )
    }
}

/**
 * 考试配额的只读结果。`deficitCount = picked.size + unmetCount` 恒成立。
 * [ExamQuotaPlan.none] 表示窗口外/考试当天：零输出，不改变任何既有计划。
 */
data class ExamQuotaPlan(
    val inWindow: Boolean,
    val daysUntilExam: Int,
    val targetRetentionAtExam: Double,
    val capacityPerDay: Int,
    val studyDaysLeft: Int,
    val quota: Int,
    val picked: List<ReviewCapacityFacts>,
    val deficitCount: Int,
    val unmetCount: Int,
) {
    init {
        require(daysUntilExam >= 0) { "Days until exam must not be negative" }
        require(targetRetentionAtExam.isFinite() && targetRetentionAtExam in 0.0..1.0) {
            "Target retention must be finite and in 0..1"
        }
        require(capacityPerDay >= 0) { "Daily capacity must not be negative" }
        require(studyDaysLeft >= 0) { "Study days left must not be negative" }
        require(quota >= 0) { "Quota must not be negative" }
        require(deficitCount >= 0) { "Deficit count must not be negative" }
        require(unmetCount >= 0) { "Unmet count must not be negative" }
        require(picked.size <= quota) {
            "Picked items must not exceed the granted quota"
        }
        require(picked.size + unmetCount == deficitCount) {
            "Picked items and unmet count must account for every deficit"
        }
        require(picked.map(ReviewCapacityFacts::practiceUnitId).distinct().size == picked.size) {
            "Picked practice units must be unique"
        }
        require(!inWindow || daysUntilExam in 1..ReviewCapacityPolicy.EXAM_WINDOW_DAYS) {
            "An in-window plan must be inside the exam ramp window"
        }
    }

    companion object {
        /** 窗口外/考试当天：零输出（逐位不变）。 */
        fun none(baseRetention: Double = ReviewCapacityPolicy.BASE_TARGET_RETENTION) =
            ExamQuotaPlan(
                inWindow = false,
                daysUntilExam = 0,
                targetRetentionAtExam = baseRetention,
                capacityPerDay = 0,
                studyDaysLeft = 0,
                quota = 0,
                picked = emptyList(),
                deficitCount = 0,
                unmetCount = 0,
            )
    }
}

private data class ExamDeficit(
    val fact: ReviewCapacityFacts,
    val deficit: Double,
)
