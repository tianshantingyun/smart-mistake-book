package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.NewIntroductionPolicy
import com.tingyun.smartmistakebook.core.domain.StudyReviewCapacity
import com.tingyun.smartmistakebook.core.domain.StudySubjectCapacity
import com.tingyun.smartmistakebook.core.ui.studentSubjectLabel

/**
 * 复习页"复习容量"区块的文案模型（5b 容量 UI / KD-68 同族）。
 *
 * 纯函数：数字全部来自快照上的 [StudyReviewCapacity]（由排程服务派生），这里只做
 * 人类可读的措辞，不重算容量、不猜预算。null = 没有容量事实，界面走
 * [ReviewCapacityCopyText.EMPTY_BODY] 的如实空态——绝不显示"今日容量 0 题"。
 */
internal data class ReviewCapacityCopy(
    val capacityLine: String,
    val examLine: String,
    val subjectLines: List<String>,
    val rebalanceLine: String,
)

internal object ReviewCapacityCopyText {
    const val TITLE = "复习容量"

    /**
     * 没有容量事实时的空态：说清"为什么没有"（没有临近考试），并说明到期的复习照常，
     * 不出现任何编造的容量数字。
     */
    const val EMPTY_BODY = "今天没有临近的考试，暂无容量读数；到期的复习照常安排。"
}

/** null = 本轮没有容量事实（窗口内无考试且积压追赶未规划）。 */
internal fun reviewCapacityCopy(capacity: StudyReviewCapacity?): ReviewCapacityCopy? {
    if (capacity == null) return null
    return ReviewCapacityCopy(
        capacityLine = capacityLine(capacity),
        examLine = examLine(capacity),
        subjectLines = capacity.subjects
            .filter { it.demand > 0 }
            .map(::subjectLine),
        rebalanceLine = rebalanceLine(capacity),
    )
}

private fun capacityLine(capacity: StudyReviewCapacity): String =
    if (capacity.maxDailyCapacity > capacity.dailyCapacity) {
        "今日容量 ${capacity.dailyCapacity} 题（最多 ${capacity.maxDailyCapacity} 题）"
    } else {
        "今日容量 ${capacity.dailyCapacity} 题"
    }

private fun examLine(capacity: StudyReviewCapacity): String = when {
    capacity.examSubjectsInWindow == 0 -> "窗口内没有考试"
    capacity.examDeficitCount == 0 -> "考试科目没有缺口"
    capacity.examUnmetCount == 0 -> "考试缺口 ${capacity.examDeficitCount} 题，今天都能安排"
    else -> "考试缺口 ${capacity.examDeficitCount} 题，今天排不下 ${capacity.examUnmetCount} 题"
}

private fun subjectLine(subject: StudySubjectCapacity): String = buildString {
    append("${subject.subjectId.studentSubjectLabel()}：名额 ${subject.granted} / 应排 ${subject.demand} 题")
    if (subject.unmet > 0) append("，未满足 ${subject.unmet} 题")
    if (!subject.feasibleBeforeExam) append("，考前做不完")
}

/**
 * 积压追赶（due-review）通道。未规划时**不报积压数**——那时的 0 不是"没有欠账"，
 * 只表示这条通道没有参与本轮规划（F12 显式态）。
 */
private fun rebalanceLine(capacity: StudyReviewCapacity): String {
    if (!capacity.rebalancePlanned) return "积压追赶：本轮未规划"
    return buildString {
        append("积压追赶：积压 ${capacity.rebalanceBacklogCount} 题，今天可安排 ${capacity.rebalanceGranted} 题")
        if (capacity.rebalanceIneligibleCount > 0) {
            append("，另有 ${capacity.rebalanceIneligibleCount} 题还没进入安全提前窗")
        }
    }
}

/**
 * 新题积压的覆盖预览文案（KD-68）。
 *
 * **消灭的失败**：旧文案把积压总数直接喂给 `NewIntroductionPolicy.preview` 的粗略算术，
 * 于是真实时长超过整日预算、每日引入永远排不进的新题也被算进"每天约 X 题 / 约 M 天安排完"。
 * 现在可调度部分（总数减去快照上的 intakeUnschedulableCount）才进入覆盖估算，
 * 不可调度部分单列成"需专项会话，不会进入每日计划"；全部不可调度时不给出任何每日承诺。
 *
 * [backlogCount] 为 0 → null（没有预览可显示，与既有空态一致）。
 */
internal data class IntakeBacklogCopy(
    val headline: String,
    val schedulableLine: String?,
    val unschedulableLine: String?,
)

internal fun intakeBacklogCopy(
    backlogCount: Int,
    unschedulableCount: Int,
    medianEstimateSeconds: Int,
    dailyBudgetSeconds: Int,
): IntakeBacklogCopy? {
    require(backlogCount >= 0) { "Intake backlog must not be negative" }
    require(unschedulableCount in 0..backlogCount) {
        "Unschedulable intake must be a subset of the backlog"
    }
    require(dailyBudgetSeconds > 0) { "Daily budget must be positive" }
    if (backlogCount == 0) return null

    val schedulableCount = backlogCount - unschedulableCount
    val estimateSeconds = if (medianEstimateSeconds > 0) medianEstimateSeconds else 60
    val schedulablePreview = schedulableCount.takeIf { it > 0 }?.let { count ->
        NewIntroductionPolicy.preview(
            backlogCount = count,
            typicalItemSeconds = estimateSeconds,
            timeBudgetSeconds = dailyBudgetSeconds,
        )
    }
    val schedulableLine = schedulablePreview?.let { preview ->
        if (unschedulableCount == 0) {
            "每天约 ${preview.perDayItems} 题，约 ${preview.daysToCover} 天安排完"
        } else {
            "可每日引入的 $schedulableCount 题：每天约 ${preview.perDayItems} 题，" +
                "约 ${preview.daysToCover} 天安排完"
        }
    }
    return IntakeBacklogCopy(
        headline = "$backlogCount 题待学",
        schedulableLine = schedulableLine,
        unschedulableLine = unschedulableCount.takeIf { it > 0 }?.let { count ->
            "另有 $count 道题单次超过整日预算，需专项会话，不会进入每日计划"
        },
    )
}
