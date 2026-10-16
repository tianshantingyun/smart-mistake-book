package com.tingyun.smartmistakebook.core.data.capture

import android.database.sqlite.SQLiteDatabase
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskRecord
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskStatus
import com.tingyun.smartmistakebook.core.domain.CleanImageGenerator
import com.tingyun.smartmistakebook.core.domain.CleanImageResult
import com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest
import com.tingyun.smartmistakebook.core.domain.CapturedProblemCommitSummary
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureInputSource
import com.tingyun.smartmistakebook.core.domain.CaptureTranscriptionReview
import com.tingyun.smartmistakebook.core.domain.CaptureWritingLayer
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-09（4D）净化作业的**设备侧**验收：任务持久行 + 故障续跑 + 晚到结果不挂新修订。
 *
 * 两条用例都用真库、真驱动（真迁移后的 schema 66）跑：
 * 1. **PENDING 行重启后被重试**：生成轮被"进程被杀"（作用域取消）打断 → 行留在 `PENDING`；
 *    新仓库启动对账接着做完，且**分类轮是重放不是重派**（同一 `requestId` 的模型任务仍是
 *    1 次派遣——"不能靠重放已提交操作静默再计费"）；
 * 2. **SUPERSEDED 不挂新修订**：题面已被新修订取代（合成 5B 修订编辑会产生的状态）之后，
 *    晚到的净化结果标 `SUPERSEDED`、**一分钱都不再花**（生成轮不再被调用），旧修订也不挂图。
 *
 * 既有 capture 仪器化（`CaptureWorkflowInstrumentedTest` 的三条重绘用例）同批回归，不回退。
 */
class CleanRedrawTaskInstrumentedTest : CaptureWorkflowTestBase() {

    @Test
    fun interruptedPendingCleanRedrawTaskIsRetriedOnResumeWithoutPayingTwice() = runBlocking {
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = redrawRepository(
            scope = firstScope,
            // 生成轮卡住（慢模型调用）：这次尝试会停在"已领取、还没产出"的 PENDING 上。
            generate = { _, _ ->
                delay(30_000)
                CleanImageResult(bytes = cleanPngBytes(), mimeType = "image/png")
            },
        )
        val committed = commitOneMistake(
            repository = first,
            requestId = "clean-redraw-resume-commit",
            title = "续跑重绘题",
        )

        val interrupted = awaitTask { it.attemptCount >= 1 }
        assertEquals(
            "进程被杀时行必须留在 PENDING（还没有终态）",
            CleanRedrawTaskStatus.PENDING,
            interrupted.status,
        )
        assertEquals("这一行已经领取过一次尝试", 1, interrupted.attemptCount)
        // 领取（attemptCount=1）发生在分类轮**之前**：领取一到就断言分类行会撞竞态——等它真落成 SUCCEEDED。
        val classifyBefore = awaitClassifySucceeded(committed.problemRevisionId)
        assertEquals(ModelTaskStatus.SUCCEEDED, classifyBefore.status)

        // ≈ 进程被杀：作用域取消，正在等的生成轮到此为止（行留在 PENDING）。
        firstScope.cancel()
        delay(100)

        // 重启：新仓库（新作用域/新资产库句柄）启动对账，接着做完。
        val resumed = redrawRepository(scope = null, generate = { _, _ ->
            CleanImageResult(bytes = cleanPngBytes(), mimeType = "image/png")
        })
        val processed = resumed.resumePendingCleanRedraws(staleBeforeEpochMillis = Long.MAX_VALUE)

        assertEquals("一行被接手", 1, processed)
        val row = checkNotNull(database.readCleanRedrawTask(interrupted.taskId))
        assertEquals(CleanRedrawTaskStatus.SUCCEEDED, row.status)
        assertEquals("第二次尝试落账", 2, row.attemptCount)
        assertTrue("干净图必须挂上", cleanImageAttached(committed.errorBookEntryId))

        val classifyAfter = checkNotNull(
            database.readModelTask("save-decision:${committed.problemRevisionId}"),
        )
        assertEquals(
            "分类轮是重放（终态即返回）不是重派：派遣次数不许从 1 变多——不能靠重放已提交操作静默再计费",
            classifyBefore.attemptCount,
            classifyAfter.attemptCount,
        )
    }

    @Test
    fun aLateCleanRedrawResultIsSupersededInsteadOfAttachingToANewerRevision() = runBlocking {
        var generateCalls = 0
        val repositoryWithFailingRedraw = redrawRepository(scope = null, generate = { _, _ ->
            generateCalls += 1
            null // 生成失败：行落 FAILED（attempt 1），题面还没有干净图
        })
        val committed = commitOneMistake(
            repository = repositoryWithFailingRedraw,
            requestId = "clean-redraw-superseded-commit",
            title = "被取代的重绘题",
        )
        val failed = awaitTask { it.status == CleanRedrawTaskStatus.FAILED }
        assertEquals(CleanRedrawTaskStatus.FAILED, failed.status)
        assertEquals(1, generateCalls)
        assertFalse("失败那一轮没有挂图", cleanImageAttached(committed.errorBookEntryId))

        // 题面被新修订取代（5B 的修订编辑路径将来会真实产生这个状态：同步把条目的当前修订指过去）。
        synthesizeNewerRevision(committed)
        generateCalls = 0

        val processed = repositoryWithFailingRedraw.resumePendingCleanRedraws(
            staleBeforeEpochMillis = Long.MAX_VALUE,
        )

        assertEquals("一行被接手（它确实该有个终态，而不是永远 FAILED 重试）", 1, processed)
        val row = checkNotNull(database.readCleanRedrawTask(failed.taskId))
        assertEquals(CleanRedrawTaskStatus.SUPERSEDED, row.status)
        assertEquals("目标已不是当前题面：付费前就该拦下，一分钱不花", 0, generateCalls)
        assertFalse(
            "晚到的净化结果不许挂到新题面上",
            cleanImageAttached(committed.errorBookEntryId),
        )
        assertEquals(
            "旧修订也不许被挂（SUPERSEDED 的语义是「不挂」，不是「改挂别处」）",
            0L,
            cleanImageLinkCount(committed.problemRevisionId),
        )
    }

    // ---- fixtures ----

    private fun redrawRepository(
        scope: CoroutineScope?,
        generate: suspend (ByteArray, String) -> CleanImageResult?,
    ): RoomCaptureWorkflowRepository = RoomCaptureWorkflowRepository(
        database,
        AndroidCanonicalAssetVault(context),
        noTextRecognizer(),
        cleanRedraw = CleanImageGenerator(generate),
        cleanRedrawScope = scope,
        modelTasks = withFigureClassifyModelTasks(),
        captureEgressAllowed = { true },
    )

    private suspend fun commitOneMistake(
        repository: RoomCaptureWorkflowRepository,
        requestId: String,
        title: String,
    ): CapturedProblemCommitSummary {
        val source = createPng(width = 96, height = 128)
        val draft = repository.importDraft(
            CaptureDraftImportRequest(
                requestId = "$requestId-import",
                localUri = privateUri(source).toString(),
                source = CaptureInputSource.CAMERA,
                origin = CaptureEntryOrigin.LIBRARY,
                occurredAtEpochMillis = 80_000,
            ),
        )
        val request = ConfirmCapturedProblemRequest(
            requestId = requestId,
            draftId = draft.draftId,
            expectedRevisionNumber = draft.revisionNumber,
            subject = "MATH",
            title = title,
            transcription = "求函数 f(x)=x² 的单调区间。",
            writingLayer = CaptureWritingLayer.PRINTED,
            transcriptionReview = CaptureTranscriptionReview.MANUAL_ENTRY,
            occurredAtEpochMillis = 81_000,
        )
        val committed = repository.confirmAndCommit(request)
        assertTrue("夹具提交必须真的落库（否则没有作业可续）", committed.created)
        return committed
    }

    /**
     * 等台账里的那一行出现并满足 [match]（这里每次只有一行；按"可续跑"读口拿，所以已终态
     * 的 SUCCEEDED/SUPERSEDED 不在读口里——两个用例要等的都是可续跑态或刚领取的态）。
     */
    private suspend fun awaitTask(
        match: (CleanRedrawTaskRecord) -> Boolean,
    ): CleanRedrawTaskRecord {
        var found: CleanRedrawTaskRecord? = null
        withTimeout(15_000) {
            while (found == null) {
                found = database.readResumableCleanRedrawTasks(
                    maxAttempts = MAX_CLEAN_REDRAW_ATTEMPTS,
                    staleBeforeEpochMillis = Long.MAX_VALUE,
                ).firstOrNull(match)
                if (found == null) delay(25)
            }
        }
        return checkNotNull(found)
    }

    private fun cleanPngBytes(): ByteArray = createPng(width = 24, height = 24).readBytes()

    /** 等分类轮的模型任务行落成 SUCCEEDED（它晚于重绘行的领取、早于生成轮）。 */
    private suspend fun awaitClassifySucceeded(revisionId: String): ModelTaskSnapshot {
        var row: ModelTaskSnapshot? = null
        withTimeout(15_000) {
            while (row?.status != ModelTaskStatus.SUCCEEDED) {
                row = database.readModelTask("save-decision:$revisionId")
                if (row?.status != ModelTaskStatus.SUCCEEDED) delay(25)
            }
        }
        return checkNotNull(row)
    }

    private suspend fun cleanImageAttached(entryId: String): Boolean =
        checkNotNull(database.readMistakeDetail(entryId))
            .sourceAssets
            .any { it.role == "CLEAN_IMAGE" }

    /**
     * 合成"题面已被新修订取代"：插一条更高版本号的修订行，并把错题条目的当前修订指过去
     * （`error_book_entry.current_revision_id` 是"书里现在显示哪一版"的权威列）。
     * 走裸连接：这是**测试专用**的状态合成，生产代码里没有"改条目当前修订"的写口。
     */
    private fun synthesizeNewerRevision(committed: CapturedProblemCommitSummary) {
        withRawDatabase { database ->
            database.execSQL(
                """
                INSERT INTO `problem_revision` (
                    `revision_id`, `problem_id`, `revision_number`, `title`, `problem_markdown`,
                    `question_document_snapshot`, `answer_spec_id`, `answer_spec_snapshot`,
                    `answer_verification_status`, `source_type`, `source_reference`,
                    `content_fingerprint`, `created_at_epoch_millis`
                )
                SELECT '$SUPERSEDING_REVISION_ID', `problem_id`, `revision_number` + 1,
                    '新题面', `problem_markdown`, `question_document_snapshot`, NULL, NULL,
                    `answer_verification_status`, `source_type`, NULL,
                    '$SUPERSEDING_REVISION_ID-fingerprint', `created_at_epoch_millis` + 1
                FROM `problem_revision` WHERE `revision_id` = ?
                """.trimIndent(),
                arrayOf(committed.problemRevisionId),
            )
            database.execSQL(
                "UPDATE `error_book_entry` SET `current_revision_id` = ? WHERE `entry_id` = ?",
                arrayOf(SUPERSEDING_REVISION_ID, committed.errorBookEntryId),
            )
        }
    }

    private fun cleanImageLinkCount(revisionId: String): Long {
        var count = 0L
        withRawDatabase { database ->
            database.rawQuery(
                "SELECT COUNT(*) FROM `problem_revision_source_asset` " +
                    "WHERE `problem_revision_id` = ? AND `role` = 'CLEAN_IMAGE'",
                arrayOf(revisionId),
            ).use { cursor ->
                if (cursor.moveToFirst()) count = cursor.getLong(0)
            }
        }
        return count
    }

    private fun withRawDatabase(block: (SQLiteDatabase) -> Unit) {
        val path = File(context.getDatabasePath(databaseName).absolutePath)
        val raw = SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            block(raw)
        } finally {
            raw.close()
        }
    }

    private companion object {
        const val SUPERSEDING_REVISION_ID = "revision-clean-redraw-superseding"
    }
}
