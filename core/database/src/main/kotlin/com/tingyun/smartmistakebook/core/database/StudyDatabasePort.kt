package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.port.AgentPendingRequestPort
import com.tingyun.smartmistakebook.core.database.port.AttemptWritePort
import com.tingyun.smartmistakebook.core.database.port.BackupPort
import com.tingyun.smartmistakebook.core.database.port.BatchImportReadPort
import com.tingyun.smartmistakebook.core.database.port.BatchImportWritePort
import com.tingyun.smartmistakebook.core.database.port.BindingAuditPort
import com.tingyun.smartmistakebook.core.database.port.CaptureReadPort
import com.tingyun.smartmistakebook.core.database.port.CaptureWritePort
import com.tingyun.smartmistakebook.core.database.port.CleanRedrawTaskPort
import com.tingyun.smartmistakebook.core.database.port.DraftReadPort
import com.tingyun.smartmistakebook.core.database.port.DraftWritePort
import com.tingyun.smartmistakebook.core.database.port.ErrorCausePort
import com.tingyun.smartmistakebook.core.database.port.ExportRecordPort
import com.tingyun.smartmistakebook.core.database.port.FigureGenerationAuditPort
import com.tingyun.smartmistakebook.core.database.port.FigureGenerationRecorderPort
import com.tingyun.smartmistakebook.core.database.port.KnowledgeQuestionLatticePort
import com.tingyun.smartmistakebook.core.database.port.KnowledgeReadPort
import com.tingyun.smartmistakebook.core.database.port.KnowledgeWritePort
import com.tingyun.smartmistakebook.core.database.port.LearningLedgerPort
import com.tingyun.smartmistakebook.core.database.port.LearningProjectionPort
import com.tingyun.smartmistakebook.core.database.port.LibraryReadPort
import com.tingyun.smartmistakebook.core.database.port.MasteryAdvisoryPort
import com.tingyun.smartmistakebook.core.database.port.MistakeReadPort
import com.tingyun.smartmistakebook.core.database.port.OrganizationReadPort
import com.tingyun.smartmistakebook.core.database.port.OrganizationWritePort
import com.tingyun.smartmistakebook.core.database.port.TutorMasteryOverviewPort
import com.tingyun.smartmistakebook.core.database.port.PredictionAuditPort
import com.tingyun.smartmistakebook.core.database.port.PracticeUnitAssessmentReadPort
import com.tingyun.smartmistakebook.core.database.port.ReviewReadPort
import com.tingyun.smartmistakebook.core.database.port.ReviewWritePort
import com.tingyun.smartmistakebook.core.database.port.SeedAssessmentPort
import com.tingyun.smartmistakebook.core.database.port.SplitImportPort
import com.tingyun.smartmistakebook.core.database.port.TutorAnswerExposurePort
import com.tingyun.smartmistakebook.core.database.port.TutorReadPort
import com.tingyun.smartmistakebook.core.database.port.TutorSessionPort
import com.tingyun.smartmistakebook.core.database.port.TutorVisualPort
import com.tingyun.smartmistakebook.core.database.port.TutorWritePort
import com.tingyun.smartmistakebook.core.model.ImmutablePayloadConflict
import com.tingyun.smartmistakebook.core.model.KnowledgeSearchIndexRebuildPort

const val MAX_REVIEW_COMPLETION_HISTORY_DAYS = 1_500
const val MAX_KNOWLEDGE_RECALL_CANDIDATES = 512

/**
 * S16（W4-1 组装批量化）：批量咨询行的两道界。
 *
 * - [MAX_TEACHING_ADVISORY_ROWS_PER_UNIT] 与单题查询
 *   `LearningDao.observeTeachingAdvisories` 的 `LIMIT 50` 对齐——批量读分组后按它截断，
 *   保持"每题只看最新 50 行"的逐位同义；
 * - [MAX_TEACHING_ADVISORY_BATCH_UNITS] 是 `IN (...)` 的单查询题数上限（SQLite
 *   bound parameter < 999 的安全取值）。
 */
const val MAX_TEACHING_ADVISORY_ROWS_PER_UNIT = 50
const val MAX_TEACHING_ADVISORY_BATCH_UNITS = 400

class AttemptIdempotencyConflictException(submissionId: String) :
    IllegalStateException("submissionId $submissionId was already used for a different payload")

class AssessmentSequenceConflictException(message: String) : IllegalStateException(message)

/**
 * 「同标识、不同载荷」的写入冲突：这个标识已经有一行，而这次带来的内容不一样。
 *
 * [ImmutablePayloadConflict] 是它的**跨层标记**：界面层（feature:*）按依赖方向看不到本模块，
 * 只能按标记判定；按类名判定会在开着 R8 的发布变体上静默失效（KD-35 的兜底正是这条路径）。
 */
class ImmutablePayloadConflictException(entityType: String, entityId: String) :
    IllegalStateException("$entityType $entityId already exists with a different payload"),
    ImmutablePayloadConflict

class ProblemOrganizationAuthorityConflictException(problemRevisionId: String) :
    IllegalStateException("A user correction already owns organization for $problemRevisionId")

class ProjectionCasConflictException(message: String) : IllegalStateException(message)

/**
 * 归档恢复被拒（W0-1/Q2 回退工具，`docs/research/kernel-projection-rollback.md`）。
 *
 * 拒绝是显式异常而不是 `null`/空快照：对调用方来说"没恢复"与"恢复成一份空/错的投影"
 * 是两件完全不同的事，后者会把一次拒绝伪装成一次成功的回退。原因见 [reason]；
 * [cause] 只在"拒绝是因为底层抛出"时携带（如 JSON 解码失败）。
 */
class ProjectionRestoreRejectedException(
    val reason: ProjectionRestoreRejection,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class LearningLedgerIntegrityException(message: String) : IllegalStateException(message)

/**
 * 账本大到无法一次全量重放。
 *
 * 与 [ProjectionCasConflictException] **不是一族**，也**不可重试**：CAS 冲突的语义是
 * 「检查点被别的写者改过，重读一次就能继续」，而这条的语义是「账本本身超出设计能重放的规模」，
 * 重试同一份账本只会得到同一个结果。两者合并会让调用方无法只在其中一个上做重试。
 *
 * 抛出时**旧检查点必须原样保留**（不提交部分结果）——恢复路径靠它成立
 * （见 `docs/adr/0003-replay-horizon.md`：出路是重放地平线，不是调大上界）。
 */
class ProjectionReplayLimitExceededException(message: String) : IllegalStateException(message)

/**
 * 一次排空的步数预算用完了，但**积压还在**。
 *
 * 与 [ProjectionCasConflictException] 同样**不是一族**——CAS 冲突的语义是「检查点被别的写者
 * 改过，重读一次就能继续」，而这条的语义是「这一次排空走得不够远」。把两者合并，调用方就
 * 无法只在其中一个上做重试，也无法在日志里分辨"有人在别的线程写"与"积压太多"这两种
 * 完全不同的现场（审计 N-06 的原始错法：64 次提交已经落库、检查点在本次调用里根本没变过，
 * 报出来的却是一句"检查点在排空途中被别的写者改过"）。
 *
 * **与 [ProjectionReplayLimitExceededException] 的关键区别是它可重试**：上界那条的输入是
 * 账本总条数，重试同一份账本只会得到同一个结果；这条的输入是"这一次走了多少步"，
 * 而每次提交都是独立事务——**已经完成的工作已经落库**，下一次调用从那个检查点接着走。
 * 抛出时因此**不**保留旧检查点（已经推进的部分就是要保留的），这与上界那条的承诺正相反。
 */
class ProjectionDrainBudgetExhaustedException(message: String) : IllegalStateException(message)

class ProblemDraftEditWorkspaceConflictException(message: String) : IllegalStateException(message)

class ProblemDraftEditWorkspaceIntegrityException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class DatabaseContractViolationException(message: String) : IllegalArgumentException(message)

/**
 * The whole persistence contract. Method groups live in the per-aggregate
 * interfaces under core.database.port; command/record types live in
 * [StudyDatabaseRecords]; stable SQLite strings live in [StudyDbValue].
 */
interface StudyDatabasePort : AutoCloseable, ModelTaskDatabasePort,
    LibraryReadPort, TutorReadPort, TutorWritePort,
    CaptureReadPort, CaptureWritePort, KnowledgeReadPort, KnowledgeWritePort,
    ReviewReadPort, MistakeReadPort, BackupPort, BatchImportReadPort, BatchImportWritePort,
    DraftReadPort, OrganizationReadPort,
    LearningLedgerPort, PredictionAuditPort, MasteryAdvisoryPort,
    KnowledgeQuestionLatticePort, TutorMasteryOverviewPort,
    DraftWritePort, TutorSessionPort, TutorAnswerExposurePort,
    SeedAssessmentPort, AttemptWritePort, OrganizationWritePort,
    LearningProjectionPort, ReviewWritePort, SplitImportPort,
    PracticeUnitAssessmentReadPort,
    AgentPendingRequestPort,
    ExportRecordPort,
    BindingAuditPort,
    CleanRedrawTaskPort,
    FigureGenerationAuditPort,
    FigureGenerationRecorderPort,
    // PR#13（T10/T5）：持久修改作业端口（merge 时丢失，手工并集补回）。
    com.tingyun.smartmistakebook.core.database.port.MistakeEditJobPort,
    KnowledgeSearchIndexRebuildPort,
    ErrorCausePort,
    // PR#13（T8）：逐题答案/卷面冻结端口（merge 时丢失，手工并集补回）。
    com.tingyun.smartmistakebook.core.database.port.DetailedSolutionPort,
    TutorVisualPort {

    /** PR#13（T8）：卷面复核存储入口；默认不可用（Room 实现覆盖）。 */
    val paperReviews: com.tingyun.smartmistakebook.core.database.dao.PaperReviewStore
        get() = throw UnsupportedOperationException("Paper review storage is unavailable")

    /** Diagnostic: the SQLite PRAGMA user_version of the opened database. */
    suspend fun readDatabaseVersion(): Int = 0
}
