package com.tingyun.smartmistakebook

import android.net.Uri
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey

/**
 * 路由表与它唯一的本地形态转换（路径参数的编码/解码）。
 *
 * 从 `SmartMistakeBookRoot.kt` 拆出（2026-09-14）：那个文件因并行会话的改动超过了 1000 行
 * 硬限，规模门按处方要求拆块。同一包内移动，**所有调用点无需改 import**；
 * `RootDestination` 与 `rootDestinations` 留在 Root——它们是 file-private，移出来会破坏可见性。
 *
 * 讲题只有**一个页面**（智能体），`Tutor` / `CapturedTutorSession` / `MistakeTutor` /
 * `TutorTextConversation` 是它的四种"这一轮带什么"的形态：不带附件（无题轮/大厅）、一次已
 * 拍好的会话、一道错题（错题详情 / 复习判题 / 判题复核 / 页内选题共用）、一条历史文字会话。
 * 四条都渲染同一个页面（同一个标题栏），底栏都映射到 `Tutor`（见 `bottomBarRouteFor`）。
 */
internal object Routes {
    const val Review = "review"
    const val Tutor = "tutor"
    const val Library = "library"
    const val Profile = "profile"
    const val ReviewSession = "review/session"
    const val KnowledgeReviewSession = "review/knowledge-session"
    /**
     * 录入（L6）——错题本、智能体等入口**同指的唯一入口**。改前是 `capture/tutor` 与
     * `capture/library` 两条并列路由（同一屏两个意图），用户的裁定是"不管批量目录还是单个体，
     * 都是录入"：现在入口只有一个，来源语义（进入讲题 / 存入错题本）交给 [CaptureOriginArgument]，
     * 方式选择（拍照 / 相册 / 整卷/PDF）由录入屏内部承担。
     */
    const val Capture = "capture/entry/{origin}"
    const val CaptureOriginArgument = "origin"
    /** 整卷/PDF/多张照片录入（L6 起是录入流内部的一步，不再是错题本栏的并列入口）。 */
    const val BatchImport = "capture/batch"
    /**
     * 「导出成果」（L7）：后台导出完成后的应用内入口，也是通知的落点。
     * 改前 `library/export` 是批量导出的前台预览页；导出后台化后它连同单题导出页一起退场，
     * 导出动作改为"入队后即可离开"。
     */
    const val ExportResults = "library/exports"
    const val CaptureResume = "capture/resume/{draftId}"
    const val SplitReview = "capture/split-review?jobId={jobId}"
    const val CapturedTutorSession = "tutor/captured/{sessionId}"
    const val TutorHistory = "tutor/history"
    const val TutorTextConversation = "tutor/lobby/{conversationId}"
    const val MistakeDetail = "mistake/{itemId}"
    const val MistakeTutor = "mistake/tutor/{entryId}/{problemId}/{problemRevisionId}"
    const val Capability = "settings/capability"
    const val LearningMastery = "profile/learning-mastery"
    const val Privacy = "settings/privacy"
    const val Reminder = "settings/reminder"
    const val Scheduling = "settings/scheduling"
    const val Storage = "settings/storage"

    fun mistakeDetail(itemId: String): String = "mistake/${Uri.encode(itemId)}"

    fun mistakeTutor(key: MistakeRevisionKey): String = listOf(
        "mistake",
        "tutor",
        encodeRevisionArgument(key.entryId),
        encodeRevisionArgument(key.problemId),
        encodeRevisionArgument(key.problemRevisionId),
    ).joinToString("/")

    // Navigation decodes a path argument once; keep one encoded layer for the explicit boundary decode.
    private fun encodeRevisionArgument(value: String): String = Uri.encode(Uri.encode(value))

    /**
     * 错题讲题路由（`mistake/tutor/…`）三段路径参数的边界解码。L7 起导出不再走带 key 的路由
     * （导出改为入队后台任务），这条解码只剩讲题一个消费者。
     */
    fun decodeMistakeKey(
        entryId: String?,
        problemId: String?,
        problemRevisionId: String?,
    ): MistakeRevisionKey? = runCatching {
        MistakeRevisionKey(
            entryId = Uri.decode(entryId.orEmpty()),
            problemId = Uri.decode(problemId.orEmpty()),
            problemRevisionId = Uri.decode(problemRevisionId.orEmpty()),
        )
    }.getOrNull()

    fun capturedTutorSession(sessionId: String): String = "tutor/captured/${Uri.encode(sessionId)}"

    fun tutorTextConversation(conversationId: String): String =
        "tutor/lobby/${Uri.encode(conversationId)}"

    fun capture(origin: CaptureEntryOrigin): String =
        "capture/entry/${origin.name.lowercase()}"

    /**
     * 录入路由的来源参数解码。空白 = 没有给出（框架恢复等场景），按"存入错题本"处理；
     * 给了但不是已知来源 = 接线错误，直接抛——静默降级会把讲题拍的照片存进错题本。
     */
    fun captureEntryOrigin(raw: String?): CaptureEntryOrigin =
        raw?.takeIf { it.isNotBlank() }
            ?.let { CaptureEntryOrigin.valueOf(it.uppercase()) }
            ?: CaptureEntryOrigin.LIBRARY

    fun captureResume(draftId: String): String = "capture/resume/${Uri.encode(draftId)}"

    fun splitReview(jobId: String?): String =
        if (jobId.isNullOrBlank()) {
            "capture/split-review"
        } else {
            "capture/split-review?jobId=${Uri.encode(jobId)}"
        }
}
