package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CommitTutorTurnWithVisualCommand
import com.tingyun.smartmistakebook.core.database.RepointTutorMessageVisualCommand
import com.tingyun.smartmistakebook.core.database.StageTutorVisualSnapshotCommand
import com.tingyun.smartmistakebook.core.database.TutorVisualCheckpointKind
import com.tingyun.smartmistakebook.core.database.TutorVisualIdentity
import com.tingyun.smartmistakebook.core.database.TutorVisualProgramWrite
import com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotRecord
import com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotWrite
import android.database.sqlite.SQLiteFullException
import com.tingyun.smartmistakebook.core.database.TutorVisualTurnCommitRecord
import com.tingyun.smartmistakebook.core.database.port.TutorVisualPort
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import java.nio.charset.StandardCharsets

/**
 * 一个可重放视觉程序的**内容输入**（身份派生的唯一来源）。
 *
 * 这里刻意不含 `programId`：身份由 [TutorVisualIdentity] 从这些字段派生，调用方没有机会
 * "给一个与内容不符的 id"。`learnerId` 不参与哈希（设计 §3.1 的派生式），但它会写进
 * `tutor_visual_program.learner_id` 并在同 id 复用时逐列核对——不同学员的同一份内容在
 * 同一库里会以冲突暴露，而不是被静默合并。这是本批登记的保守边界。
 */
data class TutorVisualProgramInput(
    val learnerId: String,
    /** QUESTION_DIAGRAM / TUTOR_EXPLANATION（进身份哈希）。 */
    val role: String,
    val sourceJs: String,
    val initialStateJson: String,
    /** 来源冻结摘要（题面/源图 sha 组合）。 */
    val basisFingerprint: String,
    val apiVersion: Int,
    val sdkVersion: String,
    /** 题面修订的软引用（多态来源不建外键）。 */
    val basisRevisionId: String? = null,
    /** 原模型请求/来源快照；调用方负责有界（超限写 NULL，不截成半截 JSON）。 */
    val requestSnapshot: String? = null,
) {
    init {
        require(learnerId.isNotBlank()) { "A visual program needs a learner id" }
        require(role.isNotBlank()) { "A visual program needs a role" }
        require(sourceJs.isNotEmpty()) { "A visual program needs source" }
        require(initialStateJson.isNotEmpty()) { "A visual program needs its initial state" }
        require(basisFingerprint.isNotBlank()) { "A visual program needs a basis fingerprint" }
        require(apiVersion >= 1) { "A visual program api version starts at 1" }
        require(sdkVersion.isNotBlank()) { "A visual program needs an sdk version" }
    }
}

/**
 * 一次可重放快照的**内容输入**（回放唯一输入）。
 *
 * `contentIds` 是引擎冻结的 contentId 集合（解析器的 `frozenContentIds` 入参），仓储负责
 * 按字典序编码成 JSON 数组（同一集合永远同一字节，幂等键才稳定）。
 */
data class TutorVisualSnapshotInput(
    /** StateV1 规范状态（≠ 程序初始 state 时也照存）。 */
    val stateJson: String,
    val sceneSchemaVersion: Int,
    val sceneJson: String,
    val renderSchemaVersion: Int,
    /** DrawOps V1 文本（重放唯一输入）。 */
    val drawCommandsJson: String,
    val contentIds: Set<String> = emptySet(),
    val viewportWidthDu: Int,
    val viewportHeightDu: Int,
    /** fontScale×1000（整数存储，避免浮点确定性争议）。 */
    val fontScaleMilli: Int,
    val theme: String,
    val timeMs: Long,
    val seed: Long,
    val runtimeVersion: String,
    val compilerVersion: String,
    val rendererVersion: String,
    val fontManifestVersion: String,
    /** NOT_CHECKED / VERIFIED / MISMATCH。 */
    val questionVerificationStatus: String,
    /** 可选预览 PNG（`canonical_source_asset` 的 RESTRICT 引用）。 */
    val previewAssetId: String? = null,
    val checkpointKind: String = TutorVisualCheckpointKind.INITIAL,
) {
    init {
        require(stateJson.isNotEmpty()) { "A visual snapshot needs its state" }
        require(sceneSchemaVersion >= 1) { "A scene schema version starts at 1" }
        require(sceneJson.isNotEmpty()) { "A visual snapshot needs its scene" }
        require(renderSchemaVersion >= 1) { "A render schema version starts at 1" }
        require(drawCommandsJson.isNotEmpty()) { "A replayable snapshot needs draw commands" }
        require(viewportWidthDu in 1..VisualExecutionBudget.MAX_CANVAS_EDGE_DU) {
            "A visual snapshot viewport width must be within the canvas budget"
        }
        require(viewportHeightDu in 1..VisualExecutionBudget.MAX_CANVAS_EDGE_DU) {
            "A visual snapshot viewport height must be within the canvas budget"
        }
        require(fontScaleMilli >= 1) { "A visual snapshot font scale must be positive" }
        require(theme.isNotBlank()) { "A visual snapshot needs a theme" }
        require(timeMs >= 0L) { "A visual snapshot time must not be negative" }
        require(runtimeVersion.isNotBlank()) { "A visual snapshot needs a runtime version" }
        require(compilerVersion.isNotBlank()) { "A visual snapshot needs a compiler version" }
        require(rendererVersion.isNotBlank()) { "A visual snapshot needs a renderer version" }
        require(fontManifestVersion.isNotBlank()) { "A visual snapshot needs a font manifest version" }
        require(questionVerificationStatus.isNotBlank()) {
            "A visual snapshot needs a question verification status"
        }
        require(checkpointKind.isNotBlank()) { "A visual snapshot needs a checkpoint kind" }
    }
}

/** S0 拒绝的有界原因（不是异常类型猜谜）。 */
enum class TutorVisualCommitRejection {
    /** 磁盘可用空间不足以落下这条快照的派生内容（state + scene + draw）。 */
    DISK_SPACE_INSUFFICIENT,

    /** 淘汰未固定旧快照后，本 program 的可回收缓存仍超 4 MiB（契约 §8.1）。 */
    PER_PROGRAM_BUDGET,

    /** 淘汰未固定旧快照后，全库可回收视觉缓存仍超 32 MiB（契约 §8.1）。 */
    GLOBAL_BUDGET,
}

/**
 * 提交前的有界拒绝。**它消灭的具体失败**：磁盘写满时让 `SQLiteFullException` 直接穿透，
 * 调用方无法区分"这次图没提交"与"库坏了"，也没有任何可读原因；更糟的是把异常当成
 * "消息也没写"，而实际上消息可能已经落库。
 *
 * 拒绝发生在**任何数据库写之前**（S0），因此"拒绝"= DB 零新增行，语义是干净的。
 */
class TutorVisualCommitRejectedException(
    val reason: TutorVisualCommitRejection,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * D-02 仓储层：把"一次 assistant 轮次的正文/trace"与"首张可见图"合成**一次提交**。
 *
 * **它消灭的具体失败**：调用方分两次写（先消息、再图）时，进程死在两次之间会留下
 * "正文在、图不在、且无记录说明图本该来"的不可恢复中间态。这里只暴露一个入口，
 * 事务边界在 [TutorVisualPort.commitTutorTurnWithVisual] 内。
 *
 * 身份与预算在**进入事务之前**算好：
 * - `programId` 由内容派生（[programIdFor]），调用方无法伪造；
 * - 源码 64 KiB / state 16 KiB / 事务 JSON 256 KiB 的预算在 S0 拒绝；
 * - 磁盘可用空间 < 本条快照的派生字节 ⇒ [TutorVisualCommitRejectedException]。
 *
 * [freeSpaceBytes] 是注入式读口（默认足够大）：生产注入 `File.usableSpace`，测试注入 0
 * 覆盖"存储满"路径——与 `BackupSnapshotTrimmer` 的 `freeSpaceProvider` 同型。
 */
class TutorVisualCommitRepository(
    private val database: TutorVisualPort,
    private val freeSpaceBytes: () -> Long = { Long.MAX_VALUE },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 对外 visualId 与落库 `program_id` 的**同一个值**。 */
    fun programIdFor(program: TutorVisualProgramInput): String =
        TutorVisualIdentity.deriveProgramId(
            sourceJs = program.sourceJs,
            initialStateJson = program.initialStateJson,
            basisFingerprint = program.basisFingerprint,
            apiVersion = program.apiVersion,
            sdkVersion = program.sdkVersion,
            role = program.role,
        )

    fun snapshotIdFor(
        programId: String,
        snapshot: TutorVisualSnapshotInput,
    ): String = TutorVisualIdentity.deriveSnapshotId(
        programId = programId,
        stateSha256 = TutorVisualIdentity.sha256Hex(snapshot.stateJson),
        drawCommandsSha256 = TutorVisualIdentity.sha256Hex(snapshot.drawCommandsJson),
    )

    /**
     * S2 + S3：只落不可变 program 与 STAGING 快照（尚未被任何消息引用）。
     * 被取消的调用留下 STAGING 行，由 [reconcileStaleStaging] 回收；LIVE 行不受影响。
     */
    suspend fun stageSnapshot(
        program: TutorVisualProgramInput,
        snapshot: TutorVisualSnapshotInput,
        ownerRequestId: String,
    ): TutorVisualSnapshotRecord {
        requireS0(program, snapshot)
        val now = clock()
        val programWrite = program.toWrite(now)
        val baseSnapshotWrite = snapshot.toWrite(programWrite.programId, now)
        val checkpointPlan = planCheckpoint(
            programId = programWrite.programId,
            snapshot = snapshot,
            snapshotId = baseSnapshotWrite.snapshotId,
        )
        val snapshotWrite = baseSnapshotWrite.copy(
            checkpointKind = checkpointPlan.incomingKind,
        )
        return translateFullDisk {
            database.stageTutorVisualSnapshot(
                StageTutorVisualSnapshotCommand(
                    program = programWrite,
                    snapshot = snapshotWrite,
                    ownerRequestId = ownerRequestId,
                ),
            )
        }
    }

    /**
     * S4：**一次提交**完成 assistant 正文/trace + 首张可见图。
     *
     * 失败语义：
     * - S0 拒绝（超预算/磁盘不足）→ [TutorVisualCommitRejectedException] / [IllegalArgumentException]，
     *   DB 零新增行；
     * - 同 `(messageId, ordinal)` 已指向另一张快照 → 数据库层 `ImmutablePayloadConflictException`
     *   （换图只能走 [repointMessageVisual] 的指针 CAS）；
     * - 任意一步失败 → 整个事务回滚，消息/link/program/snapshot 一起不成立。
     */
    suspend fun commitAssistantTurnWithVisual(
        message: AppendTutorAssistantMessageDatabaseCommand,
        program: TutorVisualProgramInput,
        snapshot: TutorVisualSnapshotInput,
        ownerRequestId: String,
        visualOrdinal: Int = 0,
        uiStateJson: String? = null,
        formDigest: String? = null,
    ): TutorVisualTurnCommitRecord {
        requireS0(program, snapshot)
        val now = clock()
        val programWrite = program.toWrite(now)
        val baseSnapshotWrite = snapshot.toWrite(programWrite.programId, now)
        val checkpointPlan = planCheckpoint(
            programId = programWrite.programId,
            snapshot = snapshot,
            snapshotId = baseSnapshotWrite.snapshotId,
        )
        val snapshotWrite = baseSnapshotWrite.copy(
            checkpointKind = checkpointPlan.incomingKind,
        )
        return translateFullDisk {
            database.commitTutorTurnWithVisual(
                CommitTutorTurnWithVisualCommand(
                    message = message,
                    program = programWrite,
                    snapshot = snapshotWrite,
                    ownerRequestId = ownerRequestId,
                    visualOrdinal = visualOrdinal,
                    uiStateJson = uiStateJson,
                    formDigest = formDigest,
                    demoteCheckpointSnapshotIds = checkpointPlan.demoteToIntermediate,
                    evictCheckpointSnapshotIds = checkpointPlan.evictUnpinnedSnapshotIds,
                ),
            )
        }
    }

    /** 指针 CAS：false = 迟到 checkpoint 的期望版本已过期，当前指针不被覆盖。 */
    suspend fun repointMessageVisual(
        messageId: String,
        ordinal: Int,
        expectedRevision: Int,
        nextSnapshotId: String,
        nowEpochMillis: Long = clock(),
    ): Boolean = database.repointTutorMessageVisual(
        RepointTutorMessageVisualCommand(
            messageId = messageId,
            ordinal = ordinal,
            expectedRevision = expectedRevision,
            nextSnapshotId = nextSnapshotId,
            nowEpochMillis = nowEpochMillis,
        ),
    )

    /** 启动/取消后的对账：只删过期且无引用的 STAGING；返回删除行数。 */
    suspend fun reconcileStaleStaging(staleBeforeEpochMillis: Long): Int =
        database.reconcileStaleTutorVisualStaging(staleBeforeEpochMillis)

    // ---- S0：预算与磁盘（全部在任何数据库写之前） ----

    private fun requireS0(
        program: TutorVisualProgramInput,
        snapshot: TutorVisualSnapshotInput,
    ) {
        require(utf8Size(program.sourceJs) <= VisualExecutionBudget.MAX_SOURCE_JS_BYTES) {
            "A visual program source exceeds the ${VisualExecutionBudget.MAX_SOURCE_JS_BYTES}-byte budget"
        }
        require(utf8Size(program.initialStateJson) <= VisualExecutionBudget.MAX_STATE_JSON_BYTES) {
            "A visual program initial state exceeds the state budget"
        }
        require(utf8Size(snapshot.stateJson) <= VisualExecutionBudget.MAX_STATE_JSON_BYTES) {
            "A visual snapshot state exceeds the state budget"
        }
        listOf(
            "scene" to snapshot.sceneJson,
            "draw commands" to snapshot.drawCommandsJson,
        ).forEach { (label, json) ->
            require(utf8Size(json) <= VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES) {
                "A visual snapshot $label exceeds the transaction JSON budget"
            }
        }

        val requiredBytes = utf8Size(snapshot.stateJson) +
            utf8Size(snapshot.sceneJson) +
            utf8Size(snapshot.drawCommandsJson)
        val availableBytes = freeSpaceBytes()
        if (availableBytes < requiredBytes) {
            throw TutorVisualCommitRejectedException(
                TutorVisualCommitRejection.DISK_SPACE_INSUFFICIENT,
                "A visual snapshot needs $requiredBytes bytes but only $availableBytes bytes are free",
            )
        }
    }

    /**
     * checkpoint 保留/配额策略的**生产接线**（批次 30 / D-05）。
     *
     * 它消灭的具体失败：`VisualCheckpointRetention` 此前只有纯策略与测试，写入方永远写
     * `INITIAL`、从不降级/淘汰、从不按 4 MiB / 32 MiB 拒提交——预算门在真实写入上等于不存在。
     *
     * 语义：
     * - 快照行**已存在**（重复提交/幂等回放）→ 不重跑策略、不改既有行；`incomingKind` 取既有
     *   行的 kind（DB 的复用路径本来也不会 UPDATE 它）。
     * - 新快照 → 调 [VisualCheckpointRetention.plan]；[VisualCheckpointPlan.Rejected] 映射成
     *   [TutorVisualCommitRejectedException]（在**任何数据库写之前**抛出）。
     */
    private suspend fun planCheckpoint(
        programId: String,
        snapshot: TutorVisualSnapshotInput,
        snapshotId: String,
    ): VisualCheckpointPlan.Retained {
        val existing = database.readVisualCheckpointSummaries(programId).map { row ->
            VisualCheckpointRow(
                snapshotId = row.snapshotId,
                kind = row.checkpointKind,
                pinned = row.pinned,
                storedBytes = row.storedBytes,
            )
        }
        existing.firstOrNull { it.snapshotId == snapshotId }?.let { stored ->
            return VisualCheckpointPlan.Retained(
                incomingKind = stored.kind ?: TutorVisualCheckpointKind.INITIAL,
                demoteToIntermediate = emptyList(),
                evictUnpinnedSnapshotIds = emptyList(),
                retainedBytesAfterEviction = existing.sumOf { it.storedBytes },
            )
        }
        val plan = VisualCheckpointRetention.plan(
            incomingSnapshotId = snapshotId,
            incomingStoredBytes = visualCheckpointStoredBytes(
                stateJson = snapshot.stateJson,
                sceneJson = snapshot.sceneJson,
                drawCommandsJson = snapshot.drawCommandsJson,
            ),
            existing = existing,
            availableDiskBytes = freeSpaceBytes(),
            globalStoredBytes = database.readGlobalVisualCheckpointStoredBytes(),
        )
        return when (plan) {
            is VisualCheckpointPlan.Retained -> plan
            is VisualCheckpointPlan.Rejected -> throw TutorVisualCommitRejectedException(
                reason = when (plan.reason) {
                    VisualCheckpointRejection.PER_PROGRAM_BUDGET ->
                        TutorVisualCommitRejection.PER_PROGRAM_BUDGET
                    VisualCheckpointRejection.GLOBAL_BUDGET ->
                        TutorVisualCommitRejection.GLOBAL_BUDGET
                    VisualCheckpointRejection.DISK_FULL ->
                        TutorVisualCommitRejection.DISK_SPACE_INSUFFICIENT
                },
                message = "Visual checkpoint rejected: ${plan.diagnostic}",
            )
        }
    }

    private fun TutorVisualProgramInput.toWrite(now: Long): TutorVisualProgramWrite =
        TutorVisualProgramWrite(
            programId = programIdFor(this),
            learnerId = learnerId,
            role = role,
            sourceSha256 = TutorVisualIdentity.sha256Hex(sourceJs),
            sourceJs = sourceJs,
            initialStateJson = initialStateJson,
            initialStateSha256 = TutorVisualIdentity.sha256Hex(initialStateJson),
            apiVersion = apiVersion,
            sdkVersion = sdkVersion,
            basisRevisionId = basisRevisionId,
            basisFingerprint = basisFingerprint,
            requestSnapshot = requestSnapshot,
            createdAtEpochMillis = now,
        )

    private fun TutorVisualSnapshotInput.toWrite(
        programId: String,
        now: Long,
    ): TutorVisualSnapshotWrite {
        val stateSha256 = TutorVisualIdentity.sha256Hex(stateJson)
        val drawCommandsSha256 = TutorVisualIdentity.sha256Hex(drawCommandsJson)
        return TutorVisualSnapshotWrite(
            snapshotId = TutorVisualIdentity.deriveSnapshotId(
                programId = programId,
                stateSha256 = stateSha256,
                drawCommandsSha256 = drawCommandsSha256,
            ),
            programId = programId,
            stateJson = stateJson,
            stateSha256 = stateSha256,
            sceneSchemaVersion = sceneSchemaVersion,
            sceneJson = sceneJson,
            sceneSha256 = TutorVisualIdentity.sha256Hex(sceneJson),
            renderSchemaVersion = renderSchemaVersion,
            drawCommandsJson = drawCommandsJson,
            drawCommandsSha256 = drawCommandsSha256,
            contentIdsJson = buildJsonArray {
                contentIds.sorted().forEach { contentId -> add(JsonPrimitive(contentId)) }
            }.toString(),
            viewportWidthDu = viewportWidthDu,
            viewportHeightDu = viewportHeightDu,
            fontScaleMilli = fontScaleMilli,
            theme = theme,
            timeMs = timeMs,
            seed = seed,
            runtimeVersion = runtimeVersion,
            compilerVersion = compilerVersion,
            rendererVersion = rendererVersion,
            fontManifestVersion = fontManifestVersion,
            questionVerificationStatus = questionVerificationStatus,
            previewAssetId = previewAssetId,
            checkpointKind = checkpointKind,
            createdAtEpochMillis = now,
        )
    }

    /**
     * S0 之外的第二道兜底：预检与真实写入之间磁盘仍可能被填满。此时 Room 已回滚事务
     * （没有半条记录），把库异常翻译成**有界拒绝**，而不是让 `SQLiteFullException` 穿透成
     * "调用方以为库坏了/消息可能写了"。
     */
    private suspend fun <T> translateFullDisk(block: suspend () -> T): T =
        try {
            block()
        } catch (full: SQLiteFullException) {
            throw TutorVisualCommitRejectedException(
                TutorVisualCommitRejection.DISK_SPACE_INSUFFICIENT,
                "The database ran out of space while committing a visual snapshot",
                full,
            )
        }

    private fun utf8Size(value: String): Int = value.toByteArray(StandardCharsets.UTF_8).size
}
