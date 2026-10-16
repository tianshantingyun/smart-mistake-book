package com.tingyun.smartmistakebook.core.data.capture

import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.ClaimCleanRedrawTaskAttemptCommand
import com.tingyun.smartmistakebook.core.database.CleanRedrawFailureCode
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskRecord
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskStatus
import com.tingyun.smartmistakebook.core.database.CreateCleanRedrawTaskCommand
import com.tingyun.smartmistakebook.core.database.FinishCleanRedrawTaskCommand
import com.tingyun.smartmistakebook.core.database.cleanRedrawTaskId
import com.tingyun.smartmistakebook.core.database.port.CleanRedrawTaskPort
import com.tingyun.smartmistakebook.core.domain.CleanImageResult
import com.tingyun.smartmistakebook.core.model.ImagePipelineClassifyInput
import com.tingyun.smartmistakebook.core.model.ImagePipelineProblemKind
import com.tingyun.smartmistakebook.core.model.ModelTaskLogicalOperationFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-09 净化作业的 JVM 单测（依赖缝见 [CleanRedrawTaskRunner] 的 KDoc）：
 *
 * - **指纹含 sha**：分类请求带源图字节 sha，作业行的指纹就是它的指纹（同一张图的判据按字节成立）；
 * - **同指纹已成功不重复付费**：成功过的作业再次触发/对账时不跑分类、不跑生成；
 * - **失败落码**：分类失败 / 生成失败 / 源图不可读 / 挂接失败各有码，不再吞成 false/null；
 * - **续跑**：被杀留下的 `PENDING` 行重启后被接手，且**重建的分类请求与原来那一轮逐位相同**
 *   （否则模型台账会把它当成另一轮，既接不上又可能再付一次钱）；
 * - **边界**：TEXT_ONLY 不动作、目标已不是当前题面在付费前就被拦、尝试次数耗尽不再跑、
 *   出网门关着一行也不动、晚于租约截止的行不被抢走。
 */
class CleanRedrawTaskRunnerTest {

    @Test
    fun `the classify request carries the source image byte sha`() = runBlocking {
        val harness = harness()

        val outcome = harness.runner.run(harness.identity())

        assertEquals(CleanRedrawTaskOutcome.SUCCEEDED, outcome)
        val request = harness.classifyRequests.single()
        assertEquals(
            "分类请求必须带源图字节 sha（行为 ①）",
            SHA_A,
            (request.input as ImagePipelineClassifyInput).sourceImageSha256,
        )
        val row = harness.taskRow()
        assertEquals(
            "作业行的指纹就是这一轮分类请求的逻辑操作指纹（内容身份，不含时刻/同意位）",
            ModelTaskLogicalOperationFingerprint.of(request),
            row.requestFingerprint,
        )
        assertEquals(SHA_A, row.sourceImageSha256)
        assertEquals(harness.identity().revisionId, row.revisionId)
        assertEquals(harness.identity().subjectId, row.subjectId)
        assertEquals(CleanRedrawTaskStatus.SUCCEEDED, row.status)
        assertEquals(1, harness.attachCalls.size)
    }

    @Test
    fun `a task that already succeeded is not paid for again`() = runBlocking {
        val harness = harness()
        harness.runner.run(harness.identity())
        assertEquals(CleanRedrawTaskStatus.SUCCEEDED, harness.taskRow().status)
        val paidRounds = harness.classifyCalls + harness.generateCalls

        val second = harness.runner.run(harness.identity())

        assertEquals(CleanRedrawTaskOutcome.ALREADY_SUCCEEDED, second)
        assertEquals(
            "同一作业再触发一次不许再跑分类/生成（不重复付费）",
            paidRounds,
            harness.classifyCalls + harness.generateCalls,
        )
        assertEquals(1, harness.attachCalls.size)
    }

    @Test
    fun `a request fingerprint that already succeeded elsewhere skips the paid round`() = runBlocking {
        val harness = harness()
        harness.runner.run(harness.identity())
        val succeeded = harness.taskRow()
        // 合成"同一请求指纹的另一行已经成功"（同一轮的另一份记录）：守卫要消灭的是再有第二行
        // 带着同一指纹进来时的第二次付费。
        harness.store.rows.clear()
        harness.store.rows["clean-redraw-sibling"] = succeeded.copy(
            taskId = "clean-redraw-sibling",
            revisionId = "revision-sibling",
            status = CleanRedrawTaskStatus.SUCCEEDED,
        )
        harness.classifyCalls = 0
        harness.generateCalls = 0

        val outcome = harness.runner.run(harness.identity())

        assertEquals(CleanRedrawTaskOutcome.ALREADY_SUCCEEDED, outcome)
        assertEquals("同指纹已有成功行：不许再跑分类", 0, harness.classifyCalls)
        assertEquals("同指纹已有成功行：不许再跑生成（付费轮）", 0, harness.generateCalls)
        assertEquals(
            "这一行本身标 SUPERSEDED（工作已由同指纹的成功行完成，不再试）",
            CleanRedrawTaskStatus.SUPERSEDED,
            harness.taskRow().status,
        )
    }

    @Test
    fun `a text only problem commits the task without redrawing`() = runBlocking {
        val harness = harness(classify = { ImagePipelineProblemKind.TEXT_ONLY })

        val outcome = harness.runner.run(harness.identity())

        assertEquals(CleanRedrawTaskOutcome.SUCCEEDED, outcome)
        assertEquals("TEXT_ONLY 不动作：不生成", 0, harness.generateCalls)
        assertTrue("TEXT_ONLY 不动作：不挂接", harness.attachCalls.isEmpty())
        assertEquals(CleanRedrawTaskStatus.SUCCEEDED, harness.taskRow().status)
    }

    @Test
    fun `a failed classify round lands its failure code`() = runBlocking {
        val harness = harness(classify = { null })

        val outcome = harness.runner.run(harness.identity())

        assertEquals(CleanRedrawTaskOutcome.FAILED, outcome)
        val row = harness.taskRow()
        assertEquals(CleanRedrawTaskStatus.FAILED, row.status)
        assertEquals(CleanRedrawFailureCode.CLASSIFY_FAILED, row.lastFailureCode)
        assertEquals(1, row.attemptCount)
        assertEquals(0, harness.generateCalls)
    }

    @Test
    fun `a failed redraw round lands its failure code`() = runBlocking {
        val harness = harness(generate = { _, _ -> null })

        val outcome = harness.runner.run(harness.identity())

        assertEquals(CleanRedrawTaskOutcome.FAILED, outcome)
        val row = harness.taskRow()
        assertEquals(CleanRedrawTaskStatus.FAILED, row.status)
        assertEquals(CleanRedrawFailureCode.REDRAW_FAILED, row.lastFailureCode)
        assertTrue("生成失败不挂接", harness.attachCalls.isEmpty())
    }

    @Test
    fun `an unreadable source image lands its failure code`() = runBlocking {
        val harness = harness(readBytes = { null })

        val outcome = harness.runner.run(harness.identity())

        assertEquals(CleanRedrawTaskOutcome.FAILED, outcome)
        assertEquals(
            CleanRedrawFailureCode.SOURCE_UNREADABLE,
            harness.taskRow().lastFailureCode,
        )
        assertEquals("读不出字节就不许生成", 0, harness.generateCalls)
    }

    @Test
    fun `a target that is no longer the current sheet is superseded before paying`() = runBlocking {
        val harness = harness()
        harness.store.currentRevision = false

        val outcome = harness.runner.run(harness.identity())

        assertEquals(CleanRedrawTaskOutcome.SUPERSEDED, outcome)
        assertEquals("目标已经不在了：生成这一轮都不该发生（不为挂不上的图付费）", 0, harness.generateCalls)
        assertTrue(
            "目标已经不在了：挂接这一轮都不该发生（不为挂不上的图动手）",
            harness.attachCalls.isEmpty(),
        )
        assertEquals(CleanRedrawTaskStatus.SUPERSEDED, harness.taskRow().status)
    }

    @Test
    fun `an attach that loses its target is superseded after generating`() = runBlocking {
        val harness = harness(attach = { _, _, _ -> false })

        val outcome = harness.runner.run(harness.identity())

        assertEquals(CleanRedrawTaskOutcome.SUPERSEDED, outcome)
        assertEquals(1, harness.generateCalls)
        assertTrue("挂接确实被尝试过（目标在生成期间消失）", harness.attachCalls.isNotEmpty())
        assertEquals(CleanRedrawTaskStatus.SUPERSEDED, harness.taskRow().status)
    }

    @Test
    fun `a resume retries an interrupted pending task with the same classify round`() = runBlocking {
        val harness = harness()
        // 模拟"进程在分类之后、生成之前被杀"：行停在 PENDING、尝试次数 1。
        val interrupted = harness.seedRow(
            status = CleanRedrawTaskStatus.PENDING,
            attemptCount = 1,
            createdAtEpochMillis = 500,
            updatedAtEpochMillis = 500,
        )

        val processed = harness.runner.resumePending(staleBeforeEpochMillis = 1_000)

        assertEquals("一行被接手", 1, processed)
        val request = harness.classifyRequests.single()
        assertEquals(
            "重建的分类请求必须与原来那一轮逐位相同（时刻取作业行的建立时刻）",
            500,
            request.occurredAtEpochMillis,
        )
        assertEquals(
            "作业指纹按同一身份重算必须不变（内容身份，不含时刻）",
            interrupted.requestFingerprint,
            ModelTaskLogicalOperationFingerprint.of(request),
        )
        val row = harness.taskRow()
        assertEquals(CleanRedrawTaskStatus.SUCCEEDED, row.status)
        assertEquals(2, row.attemptCount)
        assertEquals(1, harness.attachCalls.size)
    }

    @Test
    fun `a resume whose source image changed is superseded`() = runBlocking {
        val harness = harness()
        harness.seedRow(
            status = CleanRedrawTaskStatus.PENDING,
            attemptCount = 1,
            createdAtEpochMillis = 500,
            updatedAtEpochMillis = 500,
        )
        harness.source.value = harness.source.value!!.copy(contentSha256 = SHA_B)

        val processed = harness.runner.resumePending(staleBeforeEpochMillis = 1_000)

        assertEquals(1, processed)
        assertEquals("源图已不是那一张：不跑分类", 0, harness.classifyCalls)
        assertEquals(CleanRedrawTaskStatus.SUPERSEDED, harness.taskRow().status)
    }

    @Test
    fun `a resume whose source asset vanished is superseded`() = runBlocking {
        val harness = harness()
        harness.seedRow(
            status = CleanRedrawTaskStatus.PENDING,
            attemptCount = 1,
            createdAtEpochMillis = 500,
            updatedAtEpochMillis = 500,
        )
        harness.source.value = null

        val processed = harness.runner.resumePending(staleBeforeEpochMillis = 1_000)

        assertEquals(1, processed)
        assertEquals(0, harness.classifyCalls)
        assertEquals(CleanRedrawTaskStatus.SUPERSEDED, harness.taskRow().status)
    }

    @Test
    fun `an exhausted attempt budget skips without another paid round`() = runBlocking {
        val harness = harness()
        harness.seedRow(
            status = CleanRedrawTaskStatus.FAILED,
            attemptCount = MAX_CLEAN_REDRAW_ATTEMPTS,
            lastFailureCode = CleanRedrawFailureCode.REDRAW_FAILED,
            createdAtEpochMillis = 500,
            updatedAtEpochMillis = 500,
        )

        val outcome = harness.runner.run(harness.identity())
        val processed = harness.runner.resumePending(staleBeforeEpochMillis = 1_000)

        assertEquals(
            "行已存在且次数耗尽：这次触发的领取失败，什么都不跑",
            CleanRedrawTaskOutcome.SKIPPED,
            outcome,
        )
        assertEquals(0, processed)
        assertEquals(0, harness.classifyCalls)
        assertEquals(0, harness.generateCalls)
    }

    @Test
    fun `a fresh attempt within the budget is allowed`() = runBlocking {
        val harness = harness()
        harness.seedRow(
            status = CleanRedrawTaskStatus.FAILED,
            attemptCount = MAX_CLEAN_REDRAW_ATTEMPTS - 1,
            lastFailureCode = CleanRedrawFailureCode.REDRAW_FAILED,
            createdAtEpochMillis = 500,
            updatedAtEpochMillis = 500,
        )

        val processed = harness.runner.resumePending(staleBeforeEpochMillis = 1_000)

        assertEquals(1, processed)
        val row = harness.taskRow()
        assertEquals(CleanRedrawTaskStatus.SUCCEEDED, row.status)
        assertEquals(MAX_CLEAN_REDRAW_ATTEMPTS, row.attemptCount)
        assertNull("成功后失败码清空", row.lastFailureCode)
        assertEquals(1, harness.attachCalls.size)
    }

    /**
     * 首轮真实缺陷的回归（门禁报 `IllegalArgumentException: Only a failed clean redraw task may
     * carry a failure code`）：领取一条 FAILED 行时必须把上一轮的失败码清掉，否则读回的是
     * "PENDING + 旧失败码"，违反 [CleanRedrawTaskRecord] 的契约（有码 ⟺ FAILED）而直接抛——
     * 那样**失败行的续跑必断**，正是本轨要修的那条链。
     *
     * 观察点取"领取之后、落终态之前"：让分类轮抛取消（进程/作用域收尾不是失败），行于是停在
     * 在途（PENDING）上，此时才看得见"码已清、次数已 +1"。
     */
    @Test
    fun `claiming a failed task clears the stale failure code before the new attempt`() = runBlocking {
        val harness = harness(
            classify = { throw CancellationException("进程收尾") },
        )
        harness.seedRow(
            status = CleanRedrawTaskStatus.FAILED,
            attemptCount = 1,
            lastFailureCode = CleanRedrawFailureCode.REDRAW_FAILED,
            createdAtEpochMillis = 500,
            updatedAtEpochMillis = 500,
        )

        val failure = runCatching {
            harness.runner.resumePending(staleBeforeEpochMillis = 1_000)
        }.exceptionOrNull()

        assertTrue(
            "领取一条 FAILED 行不许抛契约异常（缺陷形态：失败行续跑在领取处就断）：$failure",
            failure is CancellationException,
        )
        assertEquals("这一轮确实走到了分类轮（说明领取成功了）", 1, harness.classifyCalls)
        val row = harness.taskRow()
        assertEquals("领取后在途", CleanRedrawTaskStatus.PENDING, row.status)
        assertEquals("次数已 +1", 2, row.attemptCount)
        assertNull(
            "上一轮的失败码必须随回到在途一起清掉（有码 ⟺ FAILED）",
            row.lastFailureCode,
        )
    }

    @Test
    fun `no egress means no ledger row and no paid round`() = runBlocking {
        val harness = harness(egressAllowed = false)

        val outcome = harness.runner.run(harness.identity())
        val processed = harness.runner.resumePending(staleBeforeEpochMillis = 1_000)

        assertEquals(CleanRedrawTaskOutcome.SKIPPED, outcome)
        assertEquals(0, processed)
        assertEquals("没配模型：不建行（没有尝试过就没有欠账）", 0, harness.store.rows.size)
        assertEquals(0, harness.classifyCalls)
        assertEquals(0, harness.generateCalls)
    }

    @Test
    fun `a resume leaves rows newer than the lease alone`() = runBlocking {
        val harness = harness()
        harness.seedRow(
            status = CleanRedrawTaskStatus.PENDING,
            attemptCount = 1,
            createdAtEpochMillis = 2_000,
            updatedAtEpochMillis = 2_000,
        )

        val processed = harness.runner.resumePending(staleBeforeEpochMillis = 1_000)

        assertEquals("本进程之外才有资格被接手：晚于租约截止的行不动", 0, processed)
        assertEquals(0, harness.classifyCalls)
        assertEquals(CleanRedrawTaskStatus.PENDING, harness.taskRow().status)
    }

    // ---- harness ----

    private class SourceHolder(var value: CanonicalSourceAssetRecord?)

    private class Harness(
        val store: FakeCleanRedrawTaskStore,
        val egressAllowed: Boolean,
        val readBytes: suspend (CanonicalSourceAssetRecord) -> ByteArray?,
        val classify: suspend (ModelTaskRequest) -> ImagePipelineProblemKind?,
        val generate: suspend (CanonicalSourceAssetRecord, ByteArray) -> CleanImageResult?,
        val attach: suspend (String, ByteArray, String) -> Boolean,
    ) {
        val source = SourceHolder(sourceAsset())
        var classifyCalls = 0
        var generateCalls = 0
        val attachCalls = mutableListOf<Triple<String, ByteArray, String>>()
        val classifyRequests = mutableListOf<ModelTaskRequest>()
        private var clock = 1_000L

        val runner = CleanRedrawTaskRunner(
            tasks = store,
            egressAllowed = { egressAllowed },
            readSourceAsset = { assetId -> source.value?.takeIf { it.sourceAssetId == assetId } },
            readSourceBytes = readBytes,
            classify = { request ->
                classifyCalls += 1
                classifyRequests += request
                classify(request)
            },
            generateClean = { record, bytes ->
                generateCalls += 1
                generate(record, bytes)
            },
            attach = { revisionId, bytes, mimeType ->
                attachCalls += Triple(revisionId, bytes, mimeType)
                attach(revisionId, bytes, mimeType)
            },
            now = { clock++ },
        )

        fun identity() = CleanRedrawTaskIdentity(
            revisionId = REVISION_ID,
            sourceAssetId = "asset-clean-1",
            subjectId = "draft-clean-1",
        )

        /** 本次触发的作业行（按修订找；合成出来的同指纹行用别的修订 id）。 */
        fun taskRow(): CleanRedrawTaskRecord =
            store.rows.values.single { it.revisionId == REVISION_ID }

        /** 直接把一行作业放进台账（模拟上一进程留下的行）。 */
        fun seedRow(
            status: String,
            attemptCount: Int,
            lastFailureCode: String? = null,
            createdAtEpochMillis: Long,
            updatedAtEpochMillis: Long,
        ): CleanRedrawTaskRecord {
            val fingerprint = contentFingerprint()
            val record = CleanRedrawTaskRecord(
                // 与执行器算出来的同一身份（同一修订 + 同一源图 + 同一指纹 → 同一行）。
                taskId = cleanRedrawTaskId(
                    revisionId = REVISION_ID,
                    sourceAssetId = "asset-clean-1",
                    sourceImageSha256 = SHA_A,
                    requestFingerprint = fingerprint,
                ),
                revisionId = REVISION_ID,
                sourceAssetId = "asset-clean-1",
                sourceImageSha256 = SHA_A,
                requestFingerprint = fingerprint,
                subjectId = "draft-clean-1",
                status = status,
                attemptCount = attemptCount,
                lastFailureCode = lastFailureCode,
                createdAtEpochMillis = createdAtEpochMillis,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
            store.rows[record.taskId] = record
            return record
        }

        /** 与执行器重建出来的分类输入同形（内容身份，不含时刻/同意位）。 */
        private fun contentFingerprint(): String =
            ModelTaskLogicalOperationFingerprint.of(
                ImagePipelineClassifyInput(
                    sourceAssetId = "asset-clean-1",
                    imageWidth = 96,
                    imageHeight = 128,
                    subjectIdOverride = "draft-clean-1",
                    sourceImageSha256 = SHA_A,
                ),
            )

        private companion object {
            const val REVISION_ID = "revision-clean-1"
        }
    }

    private fun harness(
        egressAllowed: Boolean = true,
        classify: suspend (ModelTaskRequest) -> ImagePipelineProblemKind? = {
            ImagePipelineProblemKind.WITH_FIGURE
        },
        generate: suspend (CanonicalSourceAssetRecord, ByteArray) -> CleanImageResult? =
            { _, _ -> CleanImageResult(bytes = CLEAN_BYTES, mimeType = "image/png") },
        attach: suspend (String, ByteArray, String) -> Boolean = { _, _, _ -> true },
        readBytes: suspend (CanonicalSourceAssetRecord) -> ByteArray? = { SOURCE_BYTES },
    ): Harness = Harness(
        store = FakeCleanRedrawTaskStore(),
        egressAllowed = egressAllowed,
        readBytes = readBytes,
        classify = classify,
        generate = generate,
        attach = attach,
    )

    private class FakeCleanRedrawTaskStore : CleanRedrawTaskPort {
        val rows = linkedMapOf<String, CleanRedrawTaskRecord>()
        var currentRevision = true

        override suspend fun createCleanRedrawTask(
            command: CreateCleanRedrawTaskCommand,
        ): CleanRedrawTaskRecord {
            rows[command.taskId]?.let { return it }
            val created = CleanRedrawTaskRecord(
                taskId = command.taskId,
                revisionId = command.revisionId,
                sourceAssetId = command.sourceAssetId,
                sourceImageSha256 = command.sourceImageSha256,
                requestFingerprint = command.requestFingerprint,
                subjectId = command.subjectId,
                status = CleanRedrawTaskStatus.PENDING,
                attemptCount = 0,
                lastFailureCode = null,
                createdAtEpochMillis = command.createdAtEpochMillis,
                updatedAtEpochMillis = command.createdAtEpochMillis,
            )
            rows[command.taskId] = created
            return created
        }

        override suspend fun readCleanRedrawTask(taskId: String): CleanRedrawTaskRecord? =
            rows[taskId]

        override suspend fun readCleanRedrawTaskByFingerprint(
            requestFingerprint: String,
        ): CleanRedrawTaskRecord? = rows.values.firstOrNull {
            it.requestFingerprint == requestFingerprint
        }

        override suspend fun readResumableCleanRedrawTasks(
            maxAttempts: Int,
            staleBeforeEpochMillis: Long,
        ): List<CleanRedrawTaskRecord> = rows.values
            .filter {
                it.status in CleanRedrawTaskStatus.RESUMABLE &&
                    it.attemptCount < maxAttempts &&
                    it.updatedAtEpochMillis <= staleBeforeEpochMillis
            }
            .sortedBy { it.createdAtEpochMillis }

        override suspend fun claimCleanRedrawTaskAttempt(
            command: ClaimCleanRedrawTaskAttemptCommand,
        ): CleanRedrawTaskRecord? {
            val row = rows[command.taskId] ?: return null
            if (
                row.status !in CleanRedrawTaskStatus.RESUMABLE ||
                row.attemptCount != command.expectedAttemptCount ||
                row.attemptCount >= command.maxAttempts
            ) {
                return null
            }
            val claimed = row.copy(
                status = CleanRedrawTaskStatus.PENDING,
                attemptCount = row.attemptCount + 1,
                // 与 DAO 的领取同形：上一轮的失败码随"回到在途"一起清掉（有码 ⟺ FAILED）。
                lastFailureCode = null,
                updatedAtEpochMillis = command.occurredAtEpochMillis,
            )
            rows[command.taskId] = claimed
            return claimed
        }

        override suspend fun finishCleanRedrawTask(
            command: FinishCleanRedrawTaskCommand,
        ): CleanRedrawTaskRecord? {
            val row = rows[command.taskId] ?: return null
            if (row.attemptCount != command.expectedAttemptCount) return null
            val finished = row.copy(
                status = command.status,
                lastFailureCode = command.lastFailureCode,
                updatedAtEpochMillis = command.occurredAtEpochMillis,
            )
            rows[command.taskId] = finished
            return finished
        }

        override suspend fun isCurrentProblemRevision(revisionId: String): Boolean =
            currentRevision
    }

}

private const val SHA_A =
    "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"

private const val SHA_B =
    "1111111111111111111111111111111111111111111111111111111111111111"

private val SOURCE_BYTES = byteArrayOf(1, 2, 3)
private val CLEAN_BYTES = byteArrayOf(9, 8, 7)

/** 夹具源图：id 与 sha 都来自同一条规范记录（真实链上 id 也是内容派生的）。 */
private fun sourceAsset(): CanonicalSourceAssetRecord = CanonicalSourceAssetRecord(
    sourceAssetId = "asset-clean-1",
    contentSha256 = SHA_A,
    relativePath = "source-assets/asset-clean-1.png",
    mimeType = "image/png",
    byteSize = SOURCE_BYTES.size.toLong(),
    width = 96,
    height = 128,
    sourceType = "CAMERA",
    createdAtEpochMillis = 1_000,
)
