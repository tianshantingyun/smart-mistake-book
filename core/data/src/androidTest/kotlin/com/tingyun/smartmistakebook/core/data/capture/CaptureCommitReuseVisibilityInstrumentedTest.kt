package com.tingyun.smartmistakebook.core.data.capture

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureInputSource
import com.tingyun.smartmistakebook.core.domain.CaptureTranscriptionReview
import com.tingyun.smartmistakebook.core.domain.CaptureWritingLayer
import com.tingyun.smartmistakebook.core.domain.CapturedProblemCommitSummary
import com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4C·B5 题级去重可见化（`RoomCaptureWorkflowRepository` 这一层）。
 *
 * 题级去重本来就发生在提交事务里（`ProblemDraftTransactionDao.resolveCommittedProblemIds`
 * 按规范指纹或「同科 + 同题面」找既有题行），但此前提交摘要只回执 `created`——复用与新建
 * 在摘要上长得一样。这一组用例钉住新加的那条指示 [CapturedProblemCommitSummary.reusedExistingProblem]
 * **如实**，并同时钉住它没有改变去重判据本身：
 *
 * - 同题面第二次提交（另一份草稿、另一笔请求）→ 复用既有题行、`problem` 表不增、指示为真；
 * - 同一笔提交重放 → `created = false`，指示同样为真（这次一行也没写）；
 * - 不同题面 → 新建、`problem` 表 +1、指示为假。
 *
 * `created` 的语义**没有动**：它只回答"这次调用做了写入、还是幂等重放"。所以"同题面二次提交"
 * 有两种读法，这里两种都钉住——同一份草稿重放 → `created = false`；换一份草稿提交同一题面
 * → `created = true` 且 [CapturedProblemCommitSummary.reusedExistingProblem] 为真（此前这一笔
 * 在摘要上与新建完全分不开，正是本轨要消灭的那种静默）。
 */
@RunWith(AndroidJUnit4::class)
class CaptureCommitReuseVisibilityInstrumentedTest : CaptureWorkflowTestBase() {

    @Test
    fun sameProblemTextFromASecondDraftReusesTheStoredProblemAndReportsIt() = runBlocking {
        // Room 惰性打开：先走一次读口让库文件真正落盘，裸连接才打得开（否则首条断言就撞
        // SQLiteCantOpenDatabaseException，设备实测 3/3 确定性红——这是测试自身的时序，不是产品面）。
        assertEquals("起点：库里还没有错题", 0, database.countMistakes())
        assertEquals("起点：库里还没有题行", 0, problemRowCount())

        val first = commitDraft(
            requestId = "reuse-first",
            text = PROBLEM_TEXT,
            occurredAtEpochMillis = 1_000,
        )
        assertTrue("第一笔是这次刚写进去的", first.summary.created)
        assertFalse("第一笔是新建入库，不是复用", first.summary.reusedExistingProblem)
        assertEquals(1, problemRowCount())
        assertEquals(1, database.countMistakes())

        val second = commitDraft(
            requestId = "reuse-second",
            text = PROBLEM_TEXT,
            occurredAtEpochMillis = 5_000,
        )

        assertTrue("另一份草稿的第一笔仍然是刚提交的（created 的语义没动）", second.summary.created)
        assertTrue("同题面命中既有题行：摘要如实报复用", second.summary.reusedExistingProblem)
        assertEquals("复用的是同一行题", first.summary.problemId, second.summary.problemId)
        assertEquals(first.summary.problemRevisionId, second.summary.problemRevisionId)
        assertEquals("库中 problem 行不增", 1, problemRowCount())
        assertEquals(1, database.countMistakes())
    }

    @Test
    fun replayedCommitKeepsCreatedFalseAndStillReportsReuse() = runBlocking {
        val first = commitDraft(
            requestId = "replay-first",
            text = PROBLEM_TEXT,
            occurredAtEpochMillis = 7_000,
        )
        assertEquals(1, problemRowCount())

        val replay = repository.confirmAndCommit(first.request)

        assertFalse("重放：这次调用没有写入", replay.created)
        assertTrue("重放同样没有写出新题行", replay.reusedExistingProblem)
        assertEquals("库中 problem 行不增", 1, problemRowCount())
        assertEquals(1, database.countMistakes())
    }

    @Test
    fun differentProblemTextCreatesASecondProblemRowAndSaysNew() = runBlocking {
        val first = commitDraft(
            requestId = "distinct-first",
            text = PROBLEM_TEXT,
            occurredAtEpochMillis = 9_000,
        )
        val second = commitDraft(
            requestId = "distinct-second",
            text = OTHER_PROBLEM_TEXT,
            occurredAtEpochMillis = 11_000,
        )

        assertTrue(second.summary.created)
        assertFalse("不同题面：新建入库", second.summary.reusedExistingProblem)
        assertNotEquals(first.summary.problemId, second.summary.problemId)
        assertEquals("新建一道就多一行题", 2, problemRowCount())
        assertEquals(2, database.countMistakes())
    }

    private suspend fun commitDraft(
        requestId: String,
        text: String,
        occurredAtEpochMillis: Long,
    ): CommittedDraft {
        val source = createPng(width = 96, height = 128)
        val imported = repository.importDraft(
            CaptureDraftImportRequest(
                requestId = "import-$requestId",
                localUri = privateUri(source).toString(),
                source = CaptureInputSource.PHOTO_PICKER,
                origin = CaptureEntryOrigin.LIBRARY,
                occurredAtEpochMillis = occurredAtEpochMillis,
            ),
        )
        // 题面与标题逐字相同：题级去重判的是「同科 + 同一份题面」（含文档标题），
        // 所以两者的差异只允许在草稿身份与时间上（否则测的就不是去重了）。
        val request = ConfirmCapturedProblemRequest(
            requestId = requestId,
            draftId = imported.draftId,
            expectedRevisionNumber = imported.revisionNumber,
            subject = PROBLEM_SUBJECT,
            title = PROBLEM_TITLE,
            transcription = text,
            writingLayer = CaptureWritingLayer.PRINTED,
            transcriptionReview = CaptureTranscriptionReview.MANUAL_ENTRY,
            occurredAtEpochMillis = occurredAtEpochMillis + 500,
        )
        return CommittedDraft(request = request, summary = repository.confirmAndCommit(request))
    }

    /**
     * `problem` 表的**行数**（不是错题条目数）：题级去重说的"库里不增"就是这张表。
     * 库里没有暴露这张表的读口，这里另开一条本进程自己的连接按 SQL 数——
     * 只是断言用，与生产读路径无关（先例：`CleanRedrawTaskInstrumentedTest` 的 raw 查询）。
     */
    private fun problemRowCount(): Int {
        var count = 0
        val path = File(context.getDatabasePath(databaseName).absolutePath)
        val raw = SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            raw.rawQuery("SELECT COUNT(*) FROM `problem`", null).use { cursor ->
                if (cursor.moveToFirst()) count = cursor.getInt(0)
            }
        } finally {
            raw.close()
        }
        return count
    }

    private data class CommittedDraft(
        val request: ConfirmCapturedProblemRequest,
        val summary: CapturedProblemCommitSummary,
    )

    private companion object {
        const val PROBLEM_SUBJECT = "MATH"
        const val PROBLEM_TITLE = "同一道函数题"
        const val PROBLEM_TEXT = "已知函数 f(x)=x^2-2x，求它的单调区间。"
        const val OTHER_PROBLEM_TEXT = "已知函数 g(x)=x^3+3x，判断它的奇偶性。"
    }
}
