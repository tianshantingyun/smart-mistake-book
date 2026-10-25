package com.tingyun.smartmistakebook.core.data.capture

import android.database.sqlite.SQLiteDatabase
import com.tingyun.smartmistakebook.core.data.readyKnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.data.model.FakeModelGateway
import com.tingyun.smartmistakebook.core.data.model.RoomModelTaskRepository
import com.tingyun.smartmistakebook.core.data.model.sha256Hex
import com.tingyun.smartmistakebook.core.data.visual.insertFigureGenerationAuditFixture
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskRecord
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskStatus
import com.tingyun.smartmistakebook.core.domain.CleanImageGenerator
import com.tingyun.smartmistakebook.core.domain.CleanImageRequest
import com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest
import com.tingyun.smartmistakebook.core.domain.CapturedProblemCommitSummary
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureInputSource
import com.tingyun.smartmistakebook.core.domain.CaptureTranscriptionReview
import com.tingyun.smartmistakebook.core.domain.CaptureWritingLayer
import com.tingyun.smartmistakebook.core.domain.FigureArtifactRef
import com.tingyun.smartmistakebook.core.model.ImagePipelineProblemKind
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
 * 4E WG-03 起：TEXT_ONLY 记 `NO_FIGURE` 终态（不伪报已产图）；生成请求已发出、结果未知的
 * 行停在 `AWAITING_VERIFICATION`，重启对账**不盲目补发**。
 *
 * 用例都用真库、真驱动（真迁移后的 schema）跑：
 * 1. **生成轮被杀 → 待核实，不补发**：作用域取消在生成轮（请求已可能发出）→ 行落
 *    `AWAITING_VERIFICATION`；新仓库启动对账不接手（0 行）、生成不再被调用、题面保留原图；
 * 2. **SUPERSEDED 不挂新修订**：题面已被新修订取代之后，晚到的净化结果标 `SUPERSEDED`、
 *    **一分钱都不再花**（生成轮不再被调用），旧修订也不挂图；
 * 3. **TEXT_ONLY → NO_FIGURE 终态**：分类判纯文字 → 行落 `NO_FIGURE`（不是 SUCCEEDED），
 *    不生成、不挂接、原题照常可保存。
 *
 * 既有 capture 仪器化（`CaptureWorkflowInstrumentedTest` 的三条重绘用例）同批回归，不回退。
 */
class CleanRedrawTaskInstrumentedTest : CaptureWorkflowTestBase() {

    /**
     * 4E WG-03：进程死在生成轮（已发出、结果未知）→ 行停在待核实；启动对账**不接手**、
     * **不补发**生成（外部调用不保证恰好一次，Provider 幂等/查询能力未验证前不赌重发）。
     * 分类轮是重放（模型台账终态即返回），派遣次数不变。
     */
    @Test
    fun interruptedGenerationStaysAwaitingVerificationAndIsNeverReSent() = runBlocking {
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = redrawRepository(
            scope = firstScope,
            // 生成轮卡住（慢模型调用）：请求已可能发出，这次尝试会停在"待核实"上。
            generate = { _ ->
                delay(30_000)
                canonicalFigureRef(cleanPngBytes())
            },
        )
        val committed = commitOneMistake(
            repository = first,
            requestId = "clean-redraw-resume-commit",
            title = "续跑重绘题",
        )

        val taskId = awaitTaskStatus(CleanRedrawTaskStatus.AWAITING_VERIFICATION)
        val interrupted = checkNotNull(database.readCleanRedrawTask(taskId))
        assertEquals(
            "生成请求已发出、结果未知：行停在待核实，不再是可续跑的 PENDING",
            CleanRedrawTaskStatus.AWAITING_VERIFICATION,
            interrupted.status,
        )
        assertEquals("这一行已经领取过一次尝试", 1, interrupted.attemptCount)
        val classifyBefore = awaitClassifySucceeded(committed.problemRevisionId)
        assertEquals(ModelTaskStatus.SUCCEEDED, classifyBefore.status)

        // ≈ 进程被杀：作用域取消，正在等的生成轮到此为止（行停在待核实）。
        firstScope.cancel()
        delay(100)

        // 重启：新仓库启动对账——待核实行**不被接手**（不盲目补发）。
        var generateCallsAfterRestart = 0
        val resumed = redrawRepository(scope = null, generate = { _ ->
            generateCallsAfterRestart += 1
            canonicalFigureRef(cleanPngBytes())
        })
        val processed = resumed.resumePendingCleanRedraws(staleBeforeEpochMillis = Long.MAX_VALUE)

        assertEquals("待核实行不许被对账接手（不盲目补发）", 0, processed)
        assertEquals("生成绝不补发第二次", 0, generateCallsAfterRestart)
        val row = checkNotNull(database.readCleanRedrawTask(taskId))
        assertEquals(
            "行仍停在待核实（用户看原图，不赌一次可能重复付费的重发）",
            CleanRedrawTaskStatus.AWAITING_VERIFICATION,
            row.status,
        )
        assertFalse("待核实没有产物：不许挂图", cleanImageAttached(committed.errorBookEntryId))
        val classifyAfter = checkNotNull(
            database.readModelTask("save-decision:${committed.problemRevisionId}"),
        )
        assertEquals(
            "分类轮派遣次数不变（本用例没有任何补发）",
            classifyBefore.attemptCount,
            classifyAfter.attemptCount,
        )
    }

    /**
     * 4E WG-03：TEXT_ONLY 记 `NO_FIGURE` 终态。分类判纯文字 → 行落 NO_FIGURE（不是
     * SUCCEEDED——不伪报已产图），不生成、不挂接；**原题照常保存**（无凭据/无需生成都
     * 不影响提交）。
     */
    @Test
    fun textOnlyCommitRecordsNoFigureTerminalWithoutAttaching() = runBlocking {
        var generateCalls = 0
        val repository = redrawRepository(
            scope = null,
            classifyKind = ImagePipelineProblemKind.TEXT_ONLY,
            generate = { _ ->
                generateCalls += 1
                canonicalFigureRef(cleanPngBytes())
            },
        )

        val committed = commitOneMistake(
            repository = repository,
            requestId = "clean-redraw-nofigure-commit",
            title = "纯文字题",
        )

        assertTrue("无需生成不影响提交：原题必须真的落库", committed.created)
        val taskId = awaitTaskStatus(CleanRedrawTaskStatus.NO_FIGURE)
        val row = checkNotNull(database.readCleanRedrawTask(taskId))
        assertEquals(
            "TEXT_ONLY 落 NO_FIGURE 终态，不伪报已产图",
            CleanRedrawTaskStatus.NO_FIGURE,
            row.status,
        )
        assertEquals("纯文字不生成", 0, generateCalls)
        assertFalse("纯文字不挂图", cleanImageAttached(committed.errorBookEntryId))
    }

    @Test
    fun aLateCleanRedrawResultIsSupersededInsteadOfAttachingToANewerRevision() = runBlocking {
        var generateCalls = 0
        val repositoryWithFailingRedraw = redrawRepository(scope = null, generate = { _ ->
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

    /**
     * F2（T1b 缺口）：保存期净化（不是详情页显式重绘）成功挂接后也必须回填
     * `figure_generation_attempt.output_asset_id`。此前 `RoomCaptureWorkflowRepository`
     * 构造 runner 时没注入 `onAttached`，保存期链的产物在审计里永远没有 output。
     */
    @Test
    fun saveTimeCleanRedrawBackfillsTheAttemptOutputAsset() = runBlocking {
        val attemptId = "figure-attempt:clean-redraw-op:save-time:1:IMAGE_MODEL"
        insertFigureGenerationAuditFixture(
            context = context,
            database = database,
            databaseName = databaseName,
            operationId = "clean-redraw-op:save-time",
            attemptId = attemptId,
            operationStatus = "RUNNING",
            attemptStatus = "RUNNING",
            outputAssetId = null,
            createdAtEpochMillis = 1_000,
            updatedAtEpochMillis = 1_000,
            startedAtEpochMillis = 1_000,
        )
        val generated = cleanPngBytes()
        val repository = redrawRepository(
            scope = null,
            generate = { canonicalFigureRef(generated, attemptId) },
        )

        commitOneMistake(
            repository = repository,
            requestId = "clean-redraw-backfill-commit",
            title = "保存期回填审计题",
        )

        val outputAssetId = SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.rawQuery(
                "SELECT output_asset_id FROM figure_generation_attempt WHERE attempt_id = ?",
                arrayOf(attemptId),
            ).use { cursor ->
                assertTrue("审计行必须存在", cursor.moveToFirst())
                if (cursor.isNull(0)) null else cursor.getString(0)
            }
        }
        assertEquals(
            "保存期净化成功后必须回填产物资产 id（与 attach 的幂等键同一口径）",
            "clean-" + sha256Hex(generated),
            outputAssetId,
        )
    }

    // ---- fixtures ----

    private fun redrawRepository(
        scope: CoroutineScope?,
        generate: suspend (CleanImageRequest) -> FigureArtifactRef?,
        classifyKind: ImagePipelineProblemKind = ImagePipelineProblemKind.WITH_FIGURE,
    ): RoomCaptureWorkflowRepository = RoomCaptureWorkflowRepository(
        database,
        AndroidCanonicalAssetVault(context),
        noTextRecognizer(),
        cleanRedraw = CleanImageGenerator(generate),
        cleanRedrawScope = scope,
        modelTasks = RoomModelTaskRepository(
            database = database,
            gateway = FakeModelGateway(
                stepDelayMillis = 0,
                classifyProblemKind = classifyKind,
            ),
            knowledgeBaseAvailability = readyKnowledgeBaseAvailability(),
        ),
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

    /**
     * 等台账里出现指定状态的行，返回其 task_id。走**裸连接**读：待核实/NO_FIGURE 都不在
     * "可续跑"读口里（对账不该看见它们），经 readResumable 轮询永远等不到。
     */
    private suspend fun awaitTaskStatus(status: String): String {
        var taskId: String? = null
        withTimeout(15_000) {
            while (taskId == null) {
                taskId = withRawDatabaseQuery { db ->
                    db.rawQuery(
                        "SELECT task_id FROM clean_redraw_task WHERE status = ? LIMIT 1",
                        arrayOf(status),
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }
                if (taskId == null) delay(25)
            }
        }
        return checkNotNull(taskId)
    }

    /** [withRawDatabase] 的取值版（读一条查询结果）。 */
    private fun <T> withRawDatabaseQuery(block: (SQLiteDatabase) -> T): T {
        val path = File(context.getDatabasePath(databaseName).absolutePath)
        val raw = SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            return block(raw)
        } finally {
            raw.close()
        }
    }

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
