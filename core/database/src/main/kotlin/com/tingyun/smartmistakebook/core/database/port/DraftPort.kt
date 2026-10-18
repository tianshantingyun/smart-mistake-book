package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.AppendProblemDraftSourceAssetCommand
import com.tingyun.smartmistakebook.core.database.AppendProblemDraftSourceAssetResult
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.CommitProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.ProblemDraftCommitOutcome
import com.tingyun.smartmistakebook.core.database.ConfirmAndCommitProblemDraftFromWorkspaceCommand
import com.tingyun.smartmistakebook.core.database.ConfirmProblemOrganizationCommand
import com.tingyun.smartmistakebook.core.database.ConfirmProblemOrganizationResult
import com.tingyun.smartmistakebook.core.database.ConsumeProblemDraftEditWorkspaceCommand
import com.tingyun.smartmistakebook.core.database.CreateProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.ProblemDraftEditWorkspaceRecord
import com.tingyun.smartmistakebook.core.database.ProblemDraftEditWorkspaceWriteResult
import com.tingyun.smartmistakebook.core.database.ProblemDraftRecord
import com.tingyun.smartmistakebook.core.database.ProblemDraftReplacementResult
import com.tingyun.smartmistakebook.core.database.ProblemDraftSplitResult
import com.tingyun.smartmistakebook.core.database.ProblemDraftWriteResult
import com.tingyun.smartmistakebook.core.database.ReplaceProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.ReviseProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.SaveProblemDraftEditWorkspaceCommand
import com.tingyun.smartmistakebook.core.database.SplitProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.ConfirmedProblemOrganizationRecord
import kotlinx.coroutines.flow.Flow

/**
 * Read-only port for problem draft operations.
 */
interface DraftReadPort {
    fun observePendingProblemDraftCount(): Flow<Int>
}

/**
 * Port for confirmed problem organization.
 */
interface OrganizationReadPort {
    fun observeConfirmedProblemOrganization(
        problemId: String,
        problemRevisionId: String,
    ): Flow<ConfirmedProblemOrganizationRecord>
}

/**
 * Port for problem draft writes and the draft edit workspace.
 */
interface DraftWritePort {
    suspend fun createProblemDraft(command: CreateProblemDraftCommand): ProblemDraftWriteResult

    suspend fun appendProblemDraftSourceAsset(
        command: AppendProblemDraftSourceAssetCommand,
    ): AppendProblemDraftSourceAssetResult

    suspend fun reviseProblemDraft(command: ReviseProblemDraftCommand): ProblemDraftWriteResult

    suspend fun replaceProblemDraft(
        command: ReplaceProblemDraftCommand,
    ): ProblemDraftReplacementResult

    suspend fun splitProblemDraft(
        command: SplitProblemDraftCommand,
    ): ProblemDraftSplitResult

    suspend fun readProblemDraft(draftId: String): ProblemDraftRecord?

    suspend fun readProblemDraftEditWorkspace(
        draftId: String,
    ): ProblemDraftEditWorkspaceRecord?

    suspend fun saveProblemDraftEditWorkspace(
        command: SaveProblemDraftEditWorkspaceCommand,
    ): ProblemDraftEditWorkspaceWriteResult

    suspend fun consumeProblemDraftEditWorkspace(
        command: ConsumeProblemDraftEditWorkspaceCommand,
    ): Boolean

    /**
     * 提交一份草稿（B5：回执里一并交回「这次提交有没有复用既有的题行」，口径见
     * [ProblemDraftCommitOutcome]）。判据不在本口，也不在调用方：只有 DAO 提交事务里那次
     * 题级去重查询说了算。
     */
    suspend fun commitProblemDraft(command: CommitProblemDraftCommand): ProblemDraftCommitOutcome

    suspend fun confirmAndCommitProblemDraftFromWorkspace(
        command: ConfirmAndCommitProblemDraftFromWorkspaceCommand,
    ): ProblemDraftCommitOutcome

    /**
     * Attaches a clean-redraw canonical asset to an already-committed problem
     * revision under the CLEAN_IMAGE role. Idempotent.
     *
     * Returns false when the revision does not exist, **or when it is no longer the revision
     * its error-book entry points at**（AUD-09 ⑤：晚到的净化结果只许挂到书里现在显示的那
     * 一版上）。调用方（净化作业）按 `SUPERSEDED` 记录这条 false，而不是当成一次失败去重试；
     * 同一判据的**读口**是 [CleanRedrawTaskPort.isCurrentProblemRevision]（付费之前先问它，
     * 别为一挂不上的图花钱）——判据只在这一处 SQL 与那一处读口上，不在这里再声明一次
     * （两个默认实现相撞会让 StudyDatabasePort 编译期就断）。
     */
    suspend fun attachCleanRedrawAsset(
        revisionId: String,
        asset: CanonicalSourceAssetRecord,
    ): Boolean
}
/**
 * Port for problem organization writes.
 */
interface OrganizationWritePort {
    suspend fun markRelationsStaleForRevision(
        problemRevisionId: String,
        updatedAtEpochMillis: Long,
    ): Int

    suspend fun confirmProblemOrganization(
        command: ConfirmProblemOrganizationCommand,
        /**
         * KF-32（3B 步骤三）：改绑账本事件要落的**学习者账本**。默认值与本地单学习者口径一致
         * （`RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID`），生产装配显式传入。
         */
        learnerId: String = "learner:local",
    ): ConfirmProblemOrganizationResult
}