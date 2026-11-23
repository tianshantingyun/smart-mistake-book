package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import com.tingyun.smartmistakebook.core.domain.StudyReviewOverview
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 注入 `TutorAgentContext.reviewContextJson` 的只读事实：今天的复习计划/容量、每日复习设置、
 * 考试日历。它不塞进每轮提示词，只有模型调用 `REVIEW_PLAN_READ` 时才回读。
 *
 * 信息全部来自应用层已发布的快照与设置存储；模型不能借此读别的学生/别的科目，也不能写。
 * 每个列表都有上限，保证 JSON 在 16 KiB 内。
 */
internal suspend fun SmartMistakeBookApplication.tutorReviewContextJson(): String? {
    val review = studyRepository.snapshot.value.review
    val options = runCatching { schedulingSettingsStore.options.first() }.getOrNull()
    val exams = runCatching { schedulingSettingsStore.exams.first() }.getOrNull().orEmpty()
    return buildJsonObject {
        put("plan", reviewJson(review))
        options?.let { put("scheduling", schedulingJson(it)) }
        put("exams", JsonArray(exams.take(20).map { examJson(it) }))
        put("note", "这是本机真实只读事实；没有计划/容量时对应字段为空，不要编造。")
    }.toString()
}

private fun reviewJson(review: StudyReviewOverview) = buildJsonObject {
    review.planId?.let { put("planId", it) }
    put("scheduledCount", review.scheduledCount)
    put("estimatedSeconds", review.estimatedSeconds)
    put("reasons", JsonArray(review.reasons.map { JsonPrimitive(it.name) }))
    put("scheduledPracticeUnitIds", JsonArray(review.scheduledPracticeUnitIds.take(50).map(::JsonPrimitive)))
    review.activeSessionId?.let { put("activeSessionId", it) }
    put("currentOrdinal", review.currentOrdinal)
    put("completedToday", review.completedToday)
    put("completionStreakDays", review.completionStreakDays)
    put("intakeBacklogCount", review.intakeBacklogCount)
    put("intakeMedianEstimateSeconds", review.intakeMedianEstimateSeconds)
    put("intakeUnschedulableCount", review.intakeUnschedulableCount)
    review.capacity?.let { capacity ->
        put("capacity", buildJsonObject {
            put("dailyCapacity", capacity.dailyCapacity)
            put("maxDailyCapacity", capacity.maxDailyCapacity)
            put("totalGranted", capacity.totalGranted)
            put("examSubjectsInWindow", capacity.examSubjectsInWindow)
            put("examDeficitCount", capacity.examDeficitCount)
            put("examUnmetCount", capacity.examUnmetCount)
            put("rebalancePlanned", capacity.rebalancePlanned)
            put("rebalanceBacklogCount", capacity.rebalanceBacklogCount)
            put("rebalanceIneligibleCount", capacity.rebalanceIneligibleCount)
            put("rebalanceGranted", capacity.rebalanceGranted)
        })
    }
}

private fun schedulingJson(options: SchedulingOptions) = buildJsonObject {
    put("dailyReviewMinutes", options.dailyReviewMinutes)
    put("desiredRetention", options.desiredRetention)
}

private fun examJson(exam: ExamCalendarEntry) = buildJsonObject {
    put("entryId", exam.entryId)
    put("subject", exam.subject)
    put("examEpochDay", exam.examEpochDay)
    put("title", exam.title)
}
