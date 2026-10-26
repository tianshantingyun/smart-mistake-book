package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.port.SeedAssessmentPort
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationSource
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationSourceVerifier
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationUpgradeDecision
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationUpgradeRejection
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationUpgradeRequest
import com.tingyun.smartmistakebook.core.domain.decideAnswerKeyVerificationUpgrade
import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification

/**
 * `assessment_item_snapshot.verification_status` 的**单列**写端口（G5-A / KD-67）。
 *
 * 为什么单开一个窄口，而不是复用整行写：
 * - `SeedAssessmentPort.saveAssessmentItemSnapshot` 的不可变闸把"同身份不同载荷"直接
 *   判冲突（首见即真）；升级状态用它会撞 `ImmutablePayloadConflictException`。
 * - `SeedAssessmentPort.overwriteAssessmentItemSnapshot` 是 **KD-41 损坏行修复口**，
 *   契约只允许"既有行解不开"时使用。拿它做正常行的元数据升级，会让"修复口"同时成为
 *   内容覆盖的旁路，题面不可变语义不再有单一守卫。
 *
 * 因此核验升级只提交"状态迁移"本身。实现方必须：
 * 1. 用一条 `UPDATE assessment_item_snapshot SET verification_status = :new
 *    WHERE assessment_item_snapshot_id = :id AND verification_status = :expected`
 *    完成 CAS——行不存在或当前状态已变 → 返回 false，**不得**新建行、不得整行重写；
 * 2. 不触碰任何其他列（题干/选项/键载荷/揭示态/时间戳全部原样）。
 *
 * **生产 adapter 状态（批次 23 T2 已接线）**：core/database 的
 * [SeedAssessmentPort.compareAndSetAssessmentItemVerificationStatus] 与
 * [StudyDatabaseAssessmentItemVerificationStatusWriter] 已落，生产形态的构造器默认走
 * 单列 CAS。确认流本身仍缺 feature 侧消费者与生产调用点（KD-67 ①③），所以本条仍 open。
 */
interface AssessmentItemVerificationStatusWriter {
    /**
     * CAS 写：仅当该 id 当前状态等于 [expectedCurrentStatus] 时把状态改为
     * [newVerificationStatus]；返回 true = 已写入，false = 行不存在或当前状态不符。
     */
    suspend fun compareAndSetVerificationStatus(
        assessmentItemSnapshotId: String,
        expectedCurrentStatus: String,
        newVerificationStatus: String,
    ): Boolean
}

/**
 * 生产 adapter（KD-67 / 批次 23 T2）：把 [AssessmentItemVerificationStatusWriter] 接到
 * core/database 的单列 CAS 口 [SeedAssessmentPort.compareAndSetAssessmentItemVerificationStatus]。
 *
 * 消灭的失败：批次 22 只落了端口契约与测试替身，确认流在生产路径上没有写实现——要么
 * 整行 save 撞不可变闸，要么借道 KD-41 损坏行修复口，两条路都会破坏题面不可变语义。
 * 本 adapter 只提交"状态迁移"本身；受影响行数 0 如实回报 false。
 */
class StudyDatabaseAssessmentItemVerificationStatusWriter(
    private val database: SeedAssessmentPort,
) : AssessmentItemVerificationStatusWriter {
    override suspend fun compareAndSetVerificationStatus(
        assessmentItemSnapshotId: String,
        expectedCurrentStatus: String,
        newVerificationStatus: String,
    ): Boolean = database.compareAndSetAssessmentItemVerificationStatus(
        assessmentItemSnapshotId = assessmentItemSnapshotId,
        expectedCurrentStatus = expectedCurrentStatus,
        newVerificationStatus = newVerificationStatus,
    ) > 0
}

/** 核验升级的写方结果（写方不抛业务异常；每个失败都有可判别出口）。 */
sealed interface KnowledgeQuizItemVerificationUpgradeOutcome {
    /** 已写入；[verificationStatus] 是落库值（`VERIFIED` / `USER_ASSERTED`）。 */
    data class Upgraded(
        val assessmentItemSnapshotId: String,
        val verificationStatus: String,
        val source: AnswerKeyVerificationSource,
    ) : KnowledgeQuizItemVerificationUpgradeOutcome

    /**
     * 该行已经是目标状态（重试幂等）。不写库、不报错——调用方不应把一次成功的重试
     * 当成"第二次升级"或来源校验失败。
     */
    data class AlreadyUpgraded(
        val assessmentItemSnapshotId: String,
        val verificationStatus: String,
    ) : KnowledgeQuizItemVerificationUpgradeOutcome

    /** 领域判据拒绝（来源不符/当前不是 UNKNOWN/目标非法）。 */
    data class Rejected(
        val reason: AnswerKeyVerificationUpgradeRejection,
    ) : KnowledgeQuizItemVerificationUpgradeOutcome

    /** 该 `(learner, 会话, 知识点)` 身份没有快照行。 */
    data object SnapshotNotFound : KnowledgeQuizItemVerificationUpgradeOutcome

    /**
     * 行存在但题面读不回（选项/键载荷损坏或 schema 不认识）。核验升级不能把一行坏题面
     * 标成"已核验"——那会让下游以为键可信，而题面根本端不上屏幕。
     */
    data object SnapshotUnreadable : KnowledgeQuizItemVerificationUpgradeOutcome

    /**
     * CAS 失败且当前状态既不是本次目标、也不是原状态：另一并发写方改了状态。
     * 本口不重试、不覆盖，调用方重新读一次再决定。
     */
    data object ConcurrentVerificationChange : KnowledgeQuizItemVerificationUpgradeOutcome
}

/**
 * G5-A 核验写方 API（KD-67）：把知识点复习题面快照的 `verificationStatus` 从 `UNKNOWN`
 * 显式升级为 `VERIFIED` / `USER_ASSERTED`，供未来的确认流（用户确认更正 / 独立核验）
 * 调用。生产写端口（[StudyDatabaseAssessmentItemVerificationStatusWriter] → core/database
 * 单列 CAS）已于批次 23 T2 接线；UI/会话流与生产调用点仍未接（KD-67 ①③）。
 *
 * 完整判定链（每一环都 fail-closed）：
 * 1. 按 `(learner, 会话, 知识点)` 读既有快照行；无行 → [SnapshotNotFound]；
 * 2. 行内状态串必须是可识别的 `UNKNOWN`/`VERIFIED`/`USER_ASSERTED`；无法识别 → 拒绝
 *    （按"不是 UNKNOWN"处理，绝不把脏值升级成已核验）；
 * 3. 题面必须读得回（与 PRD-02 `mayWriteNumericEvidence` 的 `itemReadable` 同源）；
 * 4. 已经是目标状态 → **先验来源形状与回执存在性**，再回 [AlreadyUpgraded]（幂等，不写库）；
 * 5. 当前不是 UNKNOWN（且不是目标）→ [AnswerKeyVerificationUpgradeRejection.CURRENT_IS_NOT_UNVERIFIED]；
 * 6. [decideAnswerKeyVerificationUpgrade] 判来源/目标/当前状态；
 * 7. [AnswerKeyVerificationSourceVerifier] 查回执真实存在且属于本学习者/本题面；失败 →
 *    [AnswerKeyVerificationUpgradeRejection.SOURCE_NOT_VERIFIED]；
 * 8. 通过后只调 [AssessmentItemVerificationStatusWriter] 做单列 CAS；其余列不动。
 *
 * 调用方约束：
 * - 一个实例绑定一个 `(database, learnerId)`；`database` 在使用期间不得关闭。
 * - 方法只读一次 + 一次 CAS，可并发调用；并发写同一行时由 CAS 分出唯一赢家，
 *   输家按重读结果拿到 [AlreadyUpgraded]（并发方写成了同一目标）或
 *   [ConcurrentVerificationChange]（写成了别的状态）。
 * - 不要在调用前自行改写行或用整行覆盖"绕过"本 API：确认流必须留下
 *   [AnswerKeyVerificationSource]（PRD-02 的来源语义）。
 */
class KnowledgeQuizItemVerificationUpgrader(
    private val database: StudyDatabasePort,
    private val learnerId: String,
    private val statusWriter: AssessmentItemVerificationStatusWriter,
    private val sourceVerifier: AnswerKeyVerificationSourceVerifier,
) {

    /**
     * 生产形态（KD-67 / 批次 23 T2）：写端口固定走 core/database 的单列 CAS adapter，
     * 调用方只需提供来源校验实现。保留四参构造器是为了测试/替换实现注入同一个窄口。
     */
    constructor(
        database: StudyDatabasePort,
        learnerId: String,
        sourceVerifier: AnswerKeyVerificationSourceVerifier,
    ) : this(
        database = database,
        learnerId = learnerId,
        statusWriter = StudyDatabaseAssessmentItemVerificationStatusWriter(database),
        sourceVerifier = sourceVerifier,
    )

    suspend fun upgrade(
        sessionId: Long,
        knowledgeNodeId: String,
        targetVerification: AnswerKeyVerification,
        source: AnswerKeyVerificationSource,
    ): KnowledgeQuizItemVerificationUpgradeOutcome {
        require(knowledgeNodeId.isNotBlank()) { "Knowledge node id must not be blank" }
        require(targetVerification != AnswerKeyVerification.UNKNOWN) {
            "Verification upgrade target must be VERIFIED or USER_ASSERTED"
        }

        val snapshotId = knowledgeQuizItemSnapshotId(learnerId, sessionId, knowledgeNodeId)
        val row = database.findAssessmentItemSnapshot(snapshotId)
            ?: return KnowledgeQuizItemVerificationUpgradeOutcome.SnapshotNotFound
        val currentVerification = row.verificationStatus.toAnswerKeyVerification()
            ?: return KnowledgeQuizItemVerificationUpgradeOutcome.Rejected(
                AnswerKeyVerificationUpgradeRejection.CURRENT_IS_NOT_UNVERIFIED,
            )
        // 坏题面不得被标成已核验（同 PRD-02 的 itemReadable 轴）。读口本身不猜题面。
        val itemReadable = KnowledgeQuizItemStore(database, learnerId)
            .readSnapshot(sessionId, knowledgeNodeId)
            ?.item != null
        if (!itemReadable) {
            return KnowledgeQuizItemVerificationUpgradeOutcome.SnapshotUnreadable
        }
        if (currentVerification == targetVerification) {
            // 幂等路径也必须先验来源：否则一条没有回执的"重试"会被显示成"已确认"。
            sourceOnlyRejection(targetVerification, source, snapshotId)?.let { rejection ->
                return KnowledgeQuizItemVerificationUpgradeOutcome.Rejected(rejection)
            }
            return KnowledgeQuizItemVerificationUpgradeOutcome.AlreadyUpgraded(
                assessmentItemSnapshotId = snapshotId,
                verificationStatus = row.verificationStatus,
            )
        }
        if (currentVerification != AnswerKeyVerification.UNKNOWN) {
            return KnowledgeQuizItemVerificationUpgradeOutcome.Rejected(
                AnswerKeyVerificationUpgradeRejection.CURRENT_IS_NOT_UNVERIFIED,
            )
        }

        val decision = decideAnswerKeyVerificationUpgrade(
            AnswerKeyVerificationUpgradeRequest(
                learnerId = learnerId,
                assessmentItemSnapshotId = snapshotId,
                currentVerification = currentVerification,
                targetVerification = targetVerification,
                source = source,
            ),
        )
        val allowed = when (decision) {
            is AnswerKeyVerificationUpgradeDecision.Rejected ->
                return KnowledgeQuizItemVerificationUpgradeOutcome.Rejected(decision.reason)

            is AnswerKeyVerificationUpgradeDecision.Allowed -> decision
        }
        // 来源形状正确还不够：id 必须指向真实的回执/核验记录（端口由确认流实现）。
        if (!sourceVerifier.verify(source, learnerId, snapshotId)) {
            return KnowledgeQuizItemVerificationUpgradeOutcome.Rejected(
                AnswerKeyVerificationUpgradeRejection.SOURCE_NOT_VERIFIED,
            )
        }
        val newStatus = allowed.targetVerification.toPersistedVerificationStatus()
        val written = statusWriter.compareAndSetVerificationStatus(
            assessmentItemSnapshotId = snapshotId,
            expectedCurrentStatus = row.verificationStatus,
            newVerificationStatus = newStatus,
        )
        if (written) {
            return KnowledgeQuizItemVerificationUpgradeOutcome.Upgraded(
                assessmentItemSnapshotId = snapshotId,
                verificationStatus = newStatus,
                source = allowed.source,
            )
        }
        // CAS 失败：再读一次区分"并发改成了本次目标"（幂等重试）与"被改成别的状态"。
        val latest = database.findAssessmentItemSnapshot(snapshotId)
            ?: return KnowledgeQuizItemVerificationUpgradeOutcome.SnapshotNotFound
        return if (latest.verificationStatus == newStatus) {
            KnowledgeQuizItemVerificationUpgradeOutcome.AlreadyUpgraded(
                assessmentItemSnapshotId = snapshotId,
                verificationStatus = newStatus,
            )
        } else {
            KnowledgeQuizItemVerificationUpgradeOutcome.ConcurrentVerificationChange
        }
    }

    /**
     * 已核验行（幂等重试）上的来源校验：目标/来源同型与确认者归属复用
     * [decideAnswerKeyVerificationUpgrade]（把当前值当作 UNKNOWN，只走来源判据），
     * 再要求回执真的存在。返回 null 表示来源可信。
     */
    private suspend fun sourceOnlyRejection(
        targetVerification: AnswerKeyVerification,
        source: AnswerKeyVerificationSource,
        snapshotId: String,
    ): AnswerKeyVerificationUpgradeRejection? {
        val shape = decideAnswerKeyVerificationUpgrade(
            AnswerKeyVerificationUpgradeRequest(
                learnerId = learnerId,
                assessmentItemSnapshotId = snapshotId,
                currentVerification = AnswerKeyVerification.UNKNOWN,
                targetVerification = targetVerification,
                source = source,
            ),
        )
        if (shape is AnswerKeyVerificationUpgradeDecision.Rejected) return shape.reason
        if (!sourceVerifier.verify(source, learnerId, snapshotId)) {
            return AnswerKeyVerificationUpgradeRejection.SOURCE_NOT_VERIFIED
        }
        return null
    }

    private fun String.toAnswerKeyVerification(): AnswerKeyVerification? = when (this) {
        StudyDbValue.VerificationStatus.UNKNOWN -> AnswerKeyVerification.UNKNOWN
        StudyDbValue.VerificationStatus.VERIFIED -> AnswerKeyVerification.VERIFIED
        StudyDbValue.VerificationStatus.USER_ASSERTED -> AnswerKeyVerification.USER_ASSERTED
        else -> null
    }

    /**
     * 领域枚举 → 落库值。显式映射而不是 `name`：两处一旦有一边改名，这里会编译失败，
     * 不会静默写进一个 `DatabaseContractValidator` 认识的别的值。
     */
    private fun AnswerKeyVerification.toPersistedVerificationStatus(): String = when (this) {
        AnswerKeyVerification.UNKNOWN -> StudyDbValue.VerificationStatus.UNKNOWN
        AnswerKeyVerification.VERIFIED -> StudyDbValue.VerificationStatus.VERIFIED
        AnswerKeyVerification.USER_ASSERTED -> StudyDbValue.VerificationStatus.USER_ASSERTED
    }
}
