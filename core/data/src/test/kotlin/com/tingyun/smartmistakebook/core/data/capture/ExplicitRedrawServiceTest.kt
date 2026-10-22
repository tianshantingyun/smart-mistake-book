package com.tingyun.smartmistakebook.core.data.capture

import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.ClaimCleanRedrawTaskAttemptCommand
import com.tingyun.smartmistakebook.core.database.CleanRedrawFailureCode
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskRecord
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskStatus
import com.tingyun.smartmistakebook.core.database.CreateCleanRedrawTaskCommand
import com.tingyun.smartmistakebook.core.database.FinishCleanRedrawTaskCommand
import com.tingyun.smartmistakebook.core.database.cleanRedrawTaskId
import com.tingyun.smartmistakebook.core.database.explicitRedrawOperationId
import com.tingyun.smartmistakebook.core.database.port.CleanRedrawTaskPort
import com.tingyun.smartmistakebook.core.database.port.RevisionImageSource
import com.tingyun.smartmistakebook.core.domain.CleanImageGenerator
import com.tingyun.smartmistakebook.core.domain.CleanImageRequest
import com.tingyun.smartmistakebook.core.domain.CleanImageResult
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawCommand
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawFailureReason
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawPhase
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawRequestResult
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawState
import com.tingyun.smartmistakebook.core.model.QUESTION_SOURCE_ROLE
import com.tingyun.smartmistakebook.core.model.VisualRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-06 T3：用户显式重绘的 JVM 单测。
 *
 * 验收范围（计划 §T3）：
 * - **成功**：用户动作建一行确定性作业 → 强制跳过分类 → 生成 → 挂接 → 终态 SUCCEEDED；
 * - **失败**：生成失败落 `REDRAW_FAILED`，额度内可重试；
 * - **晚到**：目标修订已不是当前题面时，付费前就 `SUPERSEDED`，一分钱不花、一张图不挂；
 * - **旧 revision 不污染**：挂接失败/目标移动都不写旧修订；
 * - **只在用户动作后派发**：观察本身不建行、不生成；没有模型/没源图时不建行；
 * - **启动对账**：只接手带显式前缀的行，保存期作业留给 capture 仓库。
 */
class ExplicitRedrawServiceTest {

    @Test
    fun `a user request persists one deterministic task and only then dispatches`() = runBlocking {
        val fixture = fixture()

        // 观察本身不建行、不派发（"打开详情页 0 派发"）。
        assertEquals(ExplicitRedrawState.Idle, fixture.service.observe(REVISION_ID).first())
        assertEquals(0, fixture.store.rows.size)
        assertEquals(0, fixture.generateCalls)

        val result = fixture.service.request(fixture.command())

        assertTrue("用户点击后应接受请求：$result", result is ExplicitRedrawRequestResult.Accepted)
        val row = fixture.store.rows.values.single()
        assertEquals(CleanRedrawTaskStatus.SUCCEEDED, row.status)
        assertEquals("生成一次", 1, fixture.generateCalls)
        assertEquals("挂接一次", 1, fixture.attachCalls)
        assertEquals(
            "显式重绘的身份必须带可区分的操作前缀",
            explicitRedrawOperationId(row.taskId),
            row.operationId,
        )
        val fingerprint = explicitRedrawRequestFingerprint(
            problemRevisionId = REVISION_ID,
            sourceAssetId = SOURCE_ASSET_ID,
            sourceImageSha256 = SHA_A,
            role = VisualRole.CLEAN_IMAGE,
        )
        assertEquals(
            "确定性身份 = 修订 + 源图 + 用户动作指纹",
            cleanRedrawTaskId(REVISION_ID, SOURCE_ASSET_ID, SHA_A, fingerprint),
            row.taskId,
        )
        assertEquals(fingerprint, row.requestFingerprint)
        assertEquals(
            "生成产物的审计 attempt 必须回填到规范资产 id",
            listOf("attempt-1" to explicitRedrawAssetId(CLEAN_BYTES)),
            fixture.backfilled,
        )
    }

    @Test
    fun `a repeated request never pays twice for the same revision and source`() = runBlocking {
        val fixture = fixture()
        fixture.service.request(fixture.command())
        assertEquals(1, fixture.generateCalls)

        val second = fixture.service.request(fixture.command())

        assertTrue(
            "同一修订+源图已经成功过：第二次点击只回 AlreadySettled",
            second is ExplicitRedrawRequestResult.AlreadySettled,
        )
        assertEquals("不再生成", 1, fixture.generateCalls)
        assertEquals("仍然只有一行作业", 1, fixture.store.rows.size)
    }

    @Test
    fun `a second click while the first attempt is in flight does not dispatch again`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(generateGate = gate)

        launch(Dispatchers.Unconfined) { fixture.service.request(fixture.command()) }
        assertEquals("第一次点击进入生成器", 1, fixture.generateCalls)
        assertEquals("第一次尝试已被领取", 1, fixture.store.rows.values.single().attemptCount)

        val second = fixture.service.request(fixture.command())

        assertTrue(
            "在途期间第二次点击只回 Accepted，不再起第二个执行者",
            second is ExplicitRedrawRequestResult.Accepted,
        )
        assertEquals("在途期间不得重复派发", 1, fixture.generateCalls)
        assertEquals("只领取一次", 1, fixture.store.rows.values.single().attemptCount)

        gate.complete(Unit)
        while (fixture.store.rows.values.single().status != CleanRedrawTaskStatus.SUCCEEDED) {
            yield()
        }
        assertEquals("最终仍只生成一次", 1, fixture.generateCalls)
    }

    @Test
    fun `a stale expected revision is rejected before any task row exists`() = runBlocking {
        val fixture = fixture()

        val result = fixture.service.request(fixture.command(expectedRevisionId = "revision-newer"))

        assertEquals(ExplicitRedrawRequestResult.NotCurrentRevision, result)
        assertEquals("旧版详情页的请求不建行", 0, fixture.store.rows.size)
        assertEquals(0, fixture.generateCalls)
        assertTrue(fixture.attachRevisions.isEmpty())
    }

    @Test
    fun `a moved head is rejected before any task row exists`() = runBlocking {
        val fixture = fixture()
        fixture.store.currentRevision = false

        val result = fixture.service.request(fixture.command())

        assertEquals(ExplicitRedrawRequestResult.NotCurrentRevision, result)
        assertEquals(0, fixture.store.rows.size)
        assertEquals(0, fixture.generateCalls)
    }

    @Test
    fun `the forced path redraws even when classification would say text only`() = runBlocking {
        val fixture = fixture()
        // 服务内部的 runner 分类实现恒为 null：若强制路径误走分类，这一轮会落 CLASSIFY_FAILED。
        val result = fixture.service.request(fixture.command())

        assertTrue(result is ExplicitRedrawRequestResult.Accepted)
        val row = fixture.store.rows.values.single()
        assertEquals("用户明确要求重绘：TEXT_ONLY 不许短路", CleanRedrawTaskStatus.SUCCEEDED, row.status)
        assertEquals(1, fixture.generateCalls)
        assertNull("成功失败码清空", row.lastFailureCode)
    }

    @Test
    fun `a generation failure lands its code and can be retried within the budget`() = runBlocking {
        val fixture = fixture()
        fixture.generate = { null }

        fixture.service.request(fixture.command())

        var row = fixture.store.rows.values.single()
        assertEquals(CleanRedrawTaskStatus.FAILED, row.status)
        assertEquals(CleanRedrawFailureCode.REDRAW_FAILED, row.lastFailureCode)
        var state = fixture.service.observe(REVISION_ID).first()
        assertEquals(ExplicitRedrawPhase.FAILED, state.phase)
        assertEquals(ExplicitRedrawFailureReason.REDRAW_FAILED, state.failureReason)
        assertTrue("额度内可重试", state.canRetry)

        fixture.generate = { fixture.cleanResult() }
        fixture.service.request(fixture.command())

        row = fixture.store.rows.values.single()
        assertEquals(CleanRedrawTaskStatus.SUCCEEDED, row.status)
        assertEquals("重试用掉第二次尝试", 2, row.attemptCount)
        assertEquals(2, fixture.generateCalls)
        state = fixture.service.observe(REVISION_ID).first()
        assertEquals(ExplicitRedrawPhase.SUCCEEDED, state.phase)
    }

    @Test
    fun `an exhausted retry budget stops before another paid round`() = runBlocking {
        val fixture = fixture()
        fixture.generate = { null }
        repeat(fixture.maxAttempts) { fixture.service.request(fixture.command()) }
        assertEquals(fixture.maxAttempts, fixture.generateCalls)
        val failed = fixture.store.rows.values.single()
        assertEquals(CleanRedrawTaskStatus.FAILED, failed.status)
        val before = fixture.generateCalls

        val result = fixture.service.request(fixture.command())

        assertTrue(result is ExplicitRedrawRequestResult.AlreadySettled)
        assertEquals("额度耗尽不再领取", before, fixture.generateCalls)
        val state = fixture.service.observe(REVISION_ID).first()
        assertEquals(ExplicitRedrawPhase.FAILED, state.phase)
        assertEquals("重试次数已用完", false, state.canRetry)
    }

    @Test
    fun `a late result for a superseded revision is not generated or attached`() = runBlocking {
        val fixture = fixture()
        val taskId = fixture.seedExplicitTask()
        fixture.store.currentRevision = false

        val outcome = fixture.service.executeTask(taskId)

        assertEquals(CleanRedrawTaskOutcome.SUPERSEDED, outcome)
        assertEquals("目标已不是当前题面：付费前就拦下", 0, fixture.generateCalls)
        assertTrue("旧修订不挂任何图", fixture.attachRevisions.isEmpty())
        assertEquals(
            CleanRedrawTaskStatus.SUPERSEDED,
            fixture.store.rows.getValue(taskId).status,
        )
    }

    @Test
    fun `an attach that loses its target is superseded and leaves the old revision untouched`() = runBlocking {
        val fixture = fixture()
        fixture.attachResult = false
        val taskId = fixture.seedExplicitTask()

        val outcome = fixture.service.executeTask(taskId)

        assertEquals(CleanRedrawTaskOutcome.SUPERSEDED, outcome)
        assertEquals(1, fixture.generateCalls)
        assertEquals("挂接确实被尝试过（目标在生成期间消失）", 1, fixture.attachRevisions.size)
        assertEquals(
            CleanRedrawTaskStatus.SUPERSEDED,
            fixture.store.rows.getValue(taskId).status,
        )
    }

    @Test
    fun `resume only continues explicit rows and leaves capture rows to the capture pass`() = runBlocking {
        val fixture = fixture()
        val explicit = fixture.seedExplicitTask(attemptCount = 1)
        val capture = fixture.store.createCleanRedrawTask(
            CreateCleanRedrawTaskCommand(
                taskId = "clean-redraw-capture-1",
                revisionId = REVISION_ID,
                sourceAssetId = SOURCE_ASSET_ID,
                sourceImageSha256 = SHA_A,
                requestFingerprint = "b".repeat(64),
                subjectId = "draft-1",
                createdAtEpochMillis = 500,
                operationId = null,
            ),
        ).let { row ->
            // 合成上一进程留下的在途状态（attempt 1）。
            row.copy(
                status = CleanRedrawTaskStatus.PENDING,
                attemptCount = 1,
                updatedAtEpochMillis = 500,
            ).also { fixture.store.rows[it.taskId] = it }
        }

        val processed = fixture.service.resumePending(staleBeforeEpochMillis = 1_000)

        assertEquals("只接手显式重绘那一行", 1, processed)
        assertEquals(
            CleanRedrawTaskStatus.SUCCEEDED,
            fixture.store.rows.getValue(explicit).status,
        )
        assertEquals(
            "保存期作业留给 capture 对账",
            CleanRedrawTaskStatus.PENDING,
            fixture.store.rows.getValue(capture.taskId).status,
        )
        assertEquals(1, fixture.generateCalls)
    }

    @Test
    fun `no configured model means no ledger row and no dispatch`() = runBlocking {
        val fixture = fixture(egressAllowed = false, generatorAvailable = false)

        val result = fixture.service.request(fixture.command())

        assertEquals(ExplicitRedrawRequestResult.Unavailable, result)
        assertEquals("没有尝试过就没有欠账", 0, fixture.store.rows.size)
        assertEquals(0, fixture.generateCalls)
    }

    /**
     * F12 回归：装配里有 generator 只说明"可能出网"；真正的凭证/能力门在生成器内部。
     * 能力不可用时必须在**建行/领尝试之前**返回 Unavailable，否则会白耗一次尝试额度，
     * 用户看到的也是"重绘失败"而不是"先配置模型"。
     */
    @Test
    fun `no credential means Unavailable before a ledger row or attempt exists`() = runBlocking {
        val fixture = fixture(generatorAvailable = true, imageCapabilityAvailable = false)

        val result = fixture.service.request(fixture.command())

        assertEquals(ExplicitRedrawRequestResult.Unavailable, result)
        assertEquals("没有凭证就不建行", 0, fixture.store.rows.size)
        assertEquals(0, fixture.generateCalls)
    }

    /** F12：启动对账也不能在凭证缺失时逐个领取旧行、把尝试额度耗成失败。 */
    @Test
    fun `resume does not consume attempts when no credential is available`() = runBlocking {
        val fixture = fixture(imageCapabilityAvailable = false)
        val taskId = fixture.seedExplicitTask(attemptCount = 1)

        val processed = fixture.service.resumePending(staleBeforeEpochMillis = 2_000)

        assertEquals("没有能力：这一轮一行也不动", 0, processed)
        val row = fixture.store.rows.getValue(taskId)
        assertEquals("尝试次数不得被空耗", 1, row.attemptCount)
        assertEquals(CleanRedrawTaskStatus.PENDING, row.status)
        assertEquals(0, fixture.generateCalls)
    }

    @Test
    fun `a revision without a usable source image creates no task`() = runBlocking {
        val fixture = fixture()
        fixture.store.sources = emptyList()

        val result = fixture.service.request(fixture.command())

        assertEquals(ExplicitRedrawRequestResult.NoSource, result)
        assertEquals(0, fixture.store.rows.size)
        assertEquals(0, fixture.generateCalls)
    }

    @Test
    fun `only the clean image role is supported by this track`() = runBlocking {
        val fixture = fixture()

        val result = fixture.service.request(
            fixture.command(role = VisualRole.TUTOR_EXPLANATION),
        )

        assertEquals(ExplicitRedrawRequestResult.Rejected("UNSUPPORTED_ROLE"), result)
        assertEquals(0, fixture.store.rows.size)
        assertEquals(0, fixture.generateCalls)
    }

    @Test
    fun `the source picker prefers the original photo over a previous clean image`() {
        val original = RevisionImageSource(QUESTION_SOURCE_ROLE, "asset-original", SHA_A)
        val clean = RevisionImageSource(VisualRole.CLEAN_IMAGE.name, "asset-clean", SHA_B)
        val evidence = RevisionImageSource(VisualRole.SOURCE_EVIDENCE.name, "asset-evidence", SHA_C)
        val explanation = RevisionImageSource(
            VisualRole.TUTOR_EXPLANATION.name,
            "asset-explanation",
            SHA_A,
        )

        assertEquals(
            original,
            pickExplicitRedrawSource(listOf(explanation, clean, evidence, original)),
        )
        assertEquals(clean, pickExplicitRedrawSource(listOf(explanation, clean)))
        assertEquals(evidence, pickExplicitRedrawSource(listOf(explanation, evidence)))
        assertNull(
            "讲解图/题图不是整页题面，不能当重绘源",
            pickExplicitRedrawSource(listOf(explanation)),
        )
    }

    // ---- fixture ----

    private class Fixture(
        val store: FakeCleanRedrawStore,
        val service: ExplicitRedrawService,
        var generate: suspend (CleanImageRequest) -> CleanImageResult?,
        var attachResult: Boolean,
        val cleanBytes: ByteArray,
        val maxAttempts: Int,
    ) {
        var generateCalls = 0
        var attachCalls = 0
        val attachRevisions = mutableListOf<String>()
        val backfilled = mutableListOf<Pair<String, String>>()
        private var clock = 1_000L

        fun command(
            expectedRevisionId: String = REVISION_ID,
            role: VisualRole = VisualRole.CLEAN_IMAGE,
        ) = ExplicitRedrawCommand(
            problemRevisionId = REVISION_ID,
            expectedRevisionId = expectedRevisionId,
            reason = "详情页用户请求重绘",
            role = role,
        )

        fun cleanResult(): CleanImageResult = CleanImageResult(
            bytes = cleanBytes,
            mimeType = "image/png",
            attemptId = "attempt-1",
        )

        fun seedExplicitTask(attemptCount: Int = 0): String {
            val fingerprint = explicitRedrawRequestFingerprint(
                problemRevisionId = REVISION_ID,
                sourceAssetId = SOURCE_ASSET_ID,
                sourceImageSha256 = SHA_A,
                role = VisualRole.CLEAN_IMAGE,
            )
            val taskId = cleanRedrawTaskId(REVISION_ID, SOURCE_ASSET_ID, SHA_A, fingerprint)
            store.rows[taskId] = CleanRedrawTaskRecord(
                taskId = taskId,
                revisionId = REVISION_ID,
                sourceAssetId = SOURCE_ASSET_ID,
                sourceImageSha256 = SHA_A,
                requestFingerprint = fingerprint,
                subjectId = "explicit-redraw:$REVISION_ID",
                status = CleanRedrawTaskStatus.PENDING,
                attemptCount = attemptCount,
                lastFailureCode = null,
                createdAtEpochMillis = 500,
                updatedAtEpochMillis = 500,
                operationId = explicitRedrawOperationId(taskId),
            )
            return taskId
        }
    }

    private fun fixture(
        egressAllowed: Boolean = true,
        generatorAvailable: Boolean = true,
        imageCapabilityAvailable: Boolean = true,
        generateGate: CompletableDeferred<Unit>? = null,
    ): Fixture {
        val store = FakeCleanRedrawStore()
        lateinit var fixture: Fixture
        val generator = if (generatorAvailable) {
            object : CleanImageGenerator {
                override suspend fun generateClean(request: CleanImageRequest): CleanImageResult? {
                    fixture.generateCalls += 1
                    generateGate?.await()
                    return fixture.generate(request)
                }

                override suspend fun isAvailable(): Boolean = imageCapabilityAvailable
            }
        } else {
            null
        }
        val service = ExplicitRedrawService(
            tasks = store,
            egressAllowed = { egressAllowed },
            readRevisionSources = { revisionId ->
                if (revisionId == REVISION_ID) store.sources else emptyList()
            },
            readSourceAsset = { assetId ->
                if (assetId == SOURCE_ASSET_ID) sourceRecord() else null
            },
            readSourceBytes = { SOURCE_BYTES },
            cleanImageGenerator = generator,
            attach = { revisionId, _, _ ->
                fixture.attachCalls += 1
                fixture.attachRevisions += revisionId
                fixture.attachResult
            },
            recordOutputAsset = { attemptId, assetId ->
                fixture.backfilled += attemptId to assetId
            },
            scope = CoroutineScope(Dispatchers.Unconfined),
            maxAttempts = MAX_ATTEMPTS,
            now = { 1_000L },
        )
        fixture = Fixture(
            store = store,
            service = service,
            generate = { CleanImageResult(bytes = CLEAN_BYTES, mimeType = "image/png", attemptId = "attempt-1") },
            attachResult = true,
            cleanBytes = CLEAN_BYTES,
            maxAttempts = MAX_ATTEMPTS,
        )
        return fixture
    }

    private class FakeCleanRedrawStore : CleanRedrawTaskPort {
        val rows = linkedMapOf<String, CleanRedrawTaskRecord>()
        var currentRevision = true
        var sources: List<RevisionImageSource> = listOf(
            RevisionImageSource(QUESTION_SOURCE_ROLE, SOURCE_ASSET_ID, SHA_A),
        )

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
                operationId = command.operationId,
            )
            rows[command.taskId] = created
            return created
        }

        override suspend fun readCleanRedrawTask(taskId: String): CleanRedrawTaskRecord? =
            rows[taskId]

        override suspend fun readCleanRedrawTaskByFingerprint(
            requestFingerprint: String,
        ): CleanRedrawTaskRecord? = rows.values
            .filter { it.requestFingerprint == requestFingerprint }
            .minByOrNull { it.createdAtEpochMillis }

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
            // 与 DAO 的领取 SQL 同形（见 CleanRedrawTaskContract 的租约说明）。
            val claimable = row.status == CleanRedrawTaskStatus.FAILED ||
                (row.status == CleanRedrawTaskStatus.PENDING &&
                    (row.attemptCount == 0 ||
                        row.updatedAtEpochMillis <= command.staleBeforeEpochMillis))
            if (
                !claimable ||
                row.attemptCount != command.expectedAttemptCount ||
                row.attemptCount >= command.maxAttempts
            ) {
                return null
            }
            val claimed = row.copy(
                status = CleanRedrawTaskStatus.PENDING,
                attemptCount = row.attemptCount + 1,
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

        override suspend fun readLatestCleanRedrawTask(
            revisionId: String,
            operationIdPrefix: String?,
        ): CleanRedrawTaskRecord? = latest(revisionId, operationIdPrefix)

        override fun observeLatestCleanRedrawTask(
            revisionId: String,
            operationIdPrefix: String?,
        ): Flow<CleanRedrawTaskRecord?> = flowOf(latest(revisionId, operationIdPrefix))

        override suspend fun readRevisionImageSources(
            problemRevisionId: String,
        ): List<RevisionImageSource> = if (problemRevisionId == REVISION_ID) sources else emptyList()

        private fun latest(
            revisionId: String,
            operationIdPrefix: String?,
        ): CleanRedrawTaskRecord? = rows.values
            .filter { row ->
                row.revisionId == revisionId &&
                    (operationIdPrefix == null || row.operationId?.startsWith(operationIdPrefix) == true)
            }
            .sortedWith(compareByDescending<CleanRedrawTaskRecord> { it.createdAtEpochMillis }.thenByDescending { it.taskId })
            .firstOrNull()
    }

    private companion object {
        const val REVISION_ID = "revision-explicit-1"
        const val SOURCE_ASSET_ID = "asset-original-1"
        const val MAX_ATTEMPTS = 3
        const val SHA_256_HEX_CHARS = 64
        val SHA_A = "a".repeat(SHA_256_HEX_CHARS)
        val SHA_B = "b".repeat(SHA_256_HEX_CHARS)
        val SHA_C = "c".repeat(SHA_256_HEX_CHARS)
        val SOURCE_BYTES = byteArrayOf(1, 2, 3)
        val CLEAN_BYTES = byteArrayOf(9, 8, 7)

        fun sourceRecord(): CanonicalSourceAssetRecord = CanonicalSourceAssetRecord(
            sourceAssetId = SOURCE_ASSET_ID,
            contentSha256 = SHA_A,
            relativePath = "source-assets/asset-original-1.png",
            mimeType = "image/png",
            byteSize = SOURCE_BYTES.size.toLong(),
            width = 96,
            height = 128,
            sourceType = "CAMERA",
            createdAtEpochMillis = 1_000,
        )
    }
}
