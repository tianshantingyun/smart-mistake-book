package com.tingyun.smartmistakebook.core.database

import androidx.paging.PagingSource
import androidx.room3.RoomRawQuery
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.tingyun.smartmistakebook.core.database.dao.MistakeRow
import com.tingyun.smartmistakebook.core.database.dao.ArchivedEntrySummaryRow
import com.tingyun.smartmistakebook.core.database.dao.CanonicalSourceAssetRow
import com.tingyun.smartmistakebook.core.database.dao.ReviewLogSampleProjection
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeQuestionLatticeView
import com.tingyun.smartmistakebook.core.database.entity.LlmTeachingAdvisoryEntity
import com.tingyun.smartmistakebook.core.database.LearningLedgerRead
import com.tingyun.smartmistakebook.core.database.PersistedAnswerRevealFact
import com.tingyun.smartmistakebook.core.database.PersistedReviewLogLast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import com.tingyun.smartmistakebook.core.database.dao.TutorVisualDao
import com.tingyun.smartmistakebook.core.database.entity.FigureGenerationAttemptEntity
import com.tingyun.smartmistakebook.core.database.entity.FigureGenerationOperationEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorVisualSnapshotEntity
import com.tingyun.smartmistakebook.core.database.port.BeginFigureGenerationAttemptCommand
import com.tingyun.smartmistakebook.core.database.port.BeginFigureGenerationOperationCommand
import com.tingyun.smartmistakebook.core.database.port.BeginLocalFigureGenerationAttemptCommand
import com.tingyun.smartmistakebook.core.database.port.FinishFigureGenerationAttemptCommand
import com.tingyun.smartmistakebook.core.database.port.FinishFigureGenerationOperationCommand
import com.tingyun.smartmistakebook.core.database.port.FigureGenerationAttemptIdentity
import com.tingyun.smartmistakebook.core.database.port.FigureGenerationReconcileCounts
import com.tingyun.smartmistakebook.core.database.port.StudentModelPredictionRecord
import com.tingyun.smartmistakebook.core.database.port.ResolvedStudentModelPredictionRecord
import com.tingyun.smartmistakebook.core.database.port.FullLibraryScanNodeRecord
import com.tingyun.smartmistakebook.core.database.port.TutorWritePort
import com.tingyun.smartmistakebook.core.database.port.RevisionImageSource
import com.tingyun.smartmistakebook.core.database.port.PracticeUnitAssessmentRecord
import com.tingyun.smartmistakebook.core.database.port.PracticeUnitKnowledgeBindingRecord
import com.tingyun.smartmistakebook.core.database.port.KnowledgeQuestionLatticeRecord
import com.tingyun.smartmistakebook.core.database.port.MasteryAggregateRecord
import com.tingyun.smartmistakebook.core.database.port.MistakeRevisionWritePort
import com.tingyun.smartmistakebook.core.database.port.ReviseCommittedProblemCommand
import com.tingyun.smartmistakebook.core.database.port.ReviseCommittedProblemResult
import com.tingyun.smartmistakebook.core.database.port.SubjectMasteryRecord
import com.tingyun.smartmistakebook.core.model.TeachingAdvisoryRecord

internal class RoomStudyDatabase(
    internal val database: StudyDatabase,
    /** 可选稠密腿（Stage-3）；`null` = 纯词面，见 `DenseRecallReranker`。 */
    denseRerank: DenseRecallReranker? = null,
    /**
     * ④-6（K1 批 1）：检索索引「已核验完整」判定缓存——**读路径（召回）与三条写路径
     * （内容安装 / 整科重建 / 整理确认）共用同一实例**才有意义。生产一个进程一个库实例，
     * 所以实例内缓存 = 进程内每科一次；测试可注入自定义实例（负向/计数断言缝）。
     */
    private val searchIndexCompleteness: KnowledgeSearchIndexCompleteness =
        KnowledgeSearchIndexCompleteness(),
) : StudyDatabasePort, MistakeRevisionWritePort {
    private val knowledgeResearchReviewStore = RoomKnowledgeResearchReviewStore(database)

    private val problemOrganization =
        RoomProblemOrganizationStore(database, searchIndexCompleteness)
    private val batchImports = RoomBatchImportStore(database)
    private val splitImports = RoomSplitImportStore(database)
    private val masteryOverview = RoomMasteryOverviewStore(database)
    private val librarySearch = RoomLibrarySearchStore(database)
    private val knowledgeBase = RoomKnowledgeBaseStore(
        database,
        knowledgeResearchReviewStore,
        denseRerank,
        searchIndexCompleteness,
    )

    /**
     * 6C-2 T5：恢复后按科重建派生搜索索引。与读时自愈/内容安装共用同一个
     * [KnowledgeSearchIndexCompleteness] 实例——重建进入/退出都会失效该科判定，
     * 否则并发召回可能把重建前的"完整"判定继续当数。
     */
    private val knowledgeSearchIndexBuilder =
        KnowledgeSearchIndexBuilder(database, searchIndexCompleteness)

    /** 内容调和是独立关注点，与读取/导入分开（见 `RoomKnowledgeContentReconciler` 的 KDoc）。 */
    private val contentReconciler = RoomKnowledgeContentReconciler(database, searchIndexCompleteness)
    private val backupSupport = RoomBackupSupportStore(database)
    private val studentModel = RoomStudentModelStore(database)
    private val pendingCaptures = RoomPendingCaptureStore(database)

    /** W0-1/Q2 回退工具：归档读回 + 专用恢复路径（版本/schema/无归档三道拒绝在进事务前判）。 */
    private val projectionArchive = RoomProjectionArchiveStore(database)
    override fun observeMistakes(): Flow<List<MistakeRecord>> =
        database.problemDao().observeActiveMistakes().map { rows -> rows.map(MistakeRow::toRecord) }

    override fun libraryPagingSource(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        favoriteOnly: Boolean,
    ): PagingSource<Int, LibraryCatalogRow> =
        librarySearch.pagingSource(
            searchText = searchText,
            subjectId = subjectId,
            sectionId = sectionId,
            masteryId = masteryId,
            createdFromEpochMillis = createdFromEpochMillis,
            createdToEpochMillis = createdToEpochMillis,
            sort = sort,
            favoriteOnly = favoriteOnly,
        )

    override fun librarySearchPagingSource(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        tokens: List<String>,
        favoriteOnly: Boolean,
    ): PagingSource<Int, LibraryCatalogRow> =
        librarySearch.searchPagingSource(
            matchQuery = matchQuery,
            subjectId = subjectId,
            sectionId = sectionId,
            masteryId = masteryId,
            createdFromEpochMillis = createdFromEpochMillis,
            createdToEpochMillis = createdToEpochMillis,
            sort = sort,
            tokens = tokens,
            favoriteOnly = favoriteOnly,
        )

    override suspend fun librarySearchCount(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        favoriteOnly: Boolean,
    ): Int = librarySearch.searchCount(
        matchQuery = matchQuery,
        subjectId = subjectId,
        sectionId = sectionId,
        masteryId = masteryId,
        createdFromEpochMillis = createdFromEpochMillis,
        createdToEpochMillis = createdToEpochMillis,
        favoriteOnly = favoriteOnly,
    )

    override suspend fun librarySearchPage(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        tokens: List<String>,
        offset: Int,
        limit: Int,
        favoriteOnly: Boolean,
    ): List<LibraryCatalogRow> = librarySearch.searchPage(
        matchQuery = matchQuery,
        subjectId = subjectId,
        sectionId = sectionId,
        masteryId = masteryId,
        createdFromEpochMillis = createdFromEpochMillis,
        createdToEpochMillis = createdToEpochMillis,
        sort = sort,
        tokens = tokens,
        offset = offset,
        limit = limit,
        favoriteOnly = favoriteOnly,
    )

    override suspend fun librarySearchFacets(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        facet: String,
        favoriteOnly: Boolean,
    ): List<LibraryFacetCountRecord> = librarySearch.searchFacets(
        matchQuery = matchQuery,
        subjectId = subjectId,
        sectionId = sectionId,
        masteryId = masteryId,
        createdFromEpochMillis = createdFromEpochMillis,
        createdToEpochMillis = createdToEpochMillis,
        facet = facet,
        favoriteOnly = favoriteOnly,
    )

    override suspend fun refreshLibrarySearchProjection() = librarySearch.refreshProjection()

    override suspend fun libraryCatalogPage(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        offset: Int,
        limit: Int,
        favoriteOnly: Boolean,
    ): List<LibraryCatalogRow> = librarySearch.catalogPage(
        searchText = searchText,
        subjectId = subjectId,
        sectionId = sectionId,
        masteryId = masteryId,
        createdFromEpochMillis = createdFromEpochMillis,
        createdToEpochMillis = createdToEpochMillis,
        sort = sort,
        offset = offset,
        limit = limit,
        favoriteOnly = favoriteOnly,
    )

    override suspend fun libraryCatalogCount(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        favoriteOnly: Boolean,
    ): Int = librarySearch.catalogCount(
        searchText = searchText,
        subjectId = subjectId,
        sectionId = sectionId,
        masteryId = masteryId,
        createdFromEpochMillis = createdFromEpochMillis,
        createdToEpochMillis = createdToEpochMillis,
        favoriteOnly = favoriteOnly,
    )

    override suspend fun libraryCatalogFacets(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        facet: String,
        favoriteOnly: Boolean,
    ): List<LibraryFacetCountRecord> = librarySearch.catalogFacets(
        searchText = searchText,
        subjectId = subjectId,
        sectionId = sectionId,
        masteryId = masteryId,
        createdFromEpochMillis = createdFromEpochMillis,
        createdToEpochMillis = createdToEpochMillis,
        facet = facet,
        favoriteOnly = favoriteOnly,
    )

    override fun observeLearningLedgerHead(learnerId: String): Flow<Long> {
        require(learnerId.isNotBlank())
        return database.learningDao().observeLedgerHead(learnerId)
    }

    override fun observePendingProblemDraftCount(): Flow<Int> =
        database.problemDraftTransactionDao().observePendingDraftCount()

    override fun observeTutorTurnResponses(sessionId: String): Flow<List<TutorTurnResponseRecord>> {
        require(sessionId.isNotBlank())
        return database.tutorInteractionDao().observe(sessionId)
    }

    override fun observeRecentTutorConversations(
        limit: Int,
        conversationArea: String,
    ): Flow<List<TutorConversationRecord>> {
        require(limit > 0) { "Tutor conversation limit must be positive" }
        return database.tutorConversationDao().observeRecent(limit, conversationArea)
    }

    override fun observeTutorMessages(
        conversationId: String,
    ): Flow<List<TutorMessageRecord>> {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        return database.tutorConversationDao().observeMessages(conversationId)
    }

    override fun observeTutorConversation(
        conversationId: String,
    ): Flow<TutorConversationRecord?> {
        require(conversationId.isNotBlank()) { "Tutor conversation id must not be blank" }
        return database.tutorConversationDao().observeConversation(conversationId)
    }

    override fun observePendingCaptureDrafts(): Flow<List<PendingCaptureDraftRecord>> =
        pendingCaptures.observeDrafts()

    override fun observeActiveSplitImports(): Flow<List<SplitImportJobRecord>> =
        splitImports.observe()

    override suspend fun readSplitImportJob(jobId: String): SplitImportJobRecord? {
        require(jobId.isNotBlank())
        return splitImports.read(jobId)
    }

    override suspend fun readLatestReadyBatchSplitJob(batchJobId: String): SplitImportJobRecord? {
        require(batchJobId.isNotBlank())
        return splitImports.readLatestReadyBatchSplitJob(batchJobId)
    }

    override suspend fun createSplitImportJob(
        command: CreateSplitImportJobCommand,
        questions: List<SplitImportQuestionSeed>,
    ): SplitImportJobRecord = splitImports.create(command, questions)

    override suspend fun markSplitImportReady(
        jobId: String,
        questionCount: Int,
        occurredAtEpochMillis: Long,
    ): Boolean = splitImports.markReady(jobId, questionCount, occurredAtEpochMillis)

    override suspend fun updateSplitImportSelection(
        jobId: String,
        questionOrdinal: Int,
        selected: Boolean,
        occurredAtEpochMillis: Long,
    ): Boolean = splitImports.updateSelected(jobId, questionOrdinal, selected, occurredAtEpochMillis)

    override suspend fun markSplitImportQuestionConfirmed(
        jobId: String,
        questionOrdinal: Int,
        confirmState: String,
        splitDraftId: String?,
        occurredAtEpochMillis: Long,
    ): Boolean = splitImports.markQuestionConfirmed(
        jobId,
        questionOrdinal,
        confirmState,
        splitDraftId,
        occurredAtEpochMillis,
    )

    override suspend fun markSplitImportQuestionSavedByDraftId(
        splitDraftId: String,
        occurredAtEpochMillis: Long,
    ): Boolean = splitImports.markQuestionSavedByDraftId(splitDraftId, occurredAtEpochMillis)

    override suspend fun reconcileCommittedQuestionDrafts(
        occurredAtEpochMillis: Long,
    ): Int = splitImports.reconcileCommittedQuestionDrafts(occurredAtEpochMillis)

    override suspend fun readSplitImportJobByDraft(splitDraftId: String): SplitImportJobRecord? =
        splitImports.readBySplitDraftId(splitDraftId)

    override suspend fun completeSplitImportJob(
        jobId: String,
        occurredAtEpochMillis: Long,
    ): Boolean = splitImports.complete(jobId, occurredAtEpochMillis)

    override suspend fun abandonSplitImportJob(
        jobId: String,
        occurredAtEpochMillis: Long,
    ): Boolean = splitImports.abandon(jobId, occurredAtEpochMillis)

    override fun observeBatchImportJobs(): Flow<List<BatchImportJobRecord>> = batchImports.observe()

    override fun observeReviewPlan(reviewPlanId: String): Flow<ReviewPlanBundle?> =
        database.reviewDao().observePlan(reviewPlanId).map { it?.toRecord() }

    override fun observeReviewPlanForSession(sessionId: String): Flow<ReviewPlanBundle?> =
        database.reviewDao().observePlanForSession(sessionId).map { it?.toRecord() }

    override fun observeActiveReviewPlan(learnerId: String): Flow<ReviewPlanBundle?> =
        database.reviewDao().observeActivePlans(learnerId).map { activePlans ->
            when (activePlans.size) {
                0 -> null
                1 -> activePlans.single().toRecord()
                else -> throw ImmutablePayloadConflictException(
                    "active_review_session",
                    learnerId,
                )
            }
        }

    override fun observeCurrentReviewPlan(
        learnerId: String,
        localDayEpochDay: Long,
        timeZoneId: String,
    ): Flow<ReviewPlanBundle?> = database.reviewDao()
        .observeCurrentPlan(learnerId, localDayEpochDay, timeZoneId)
        .map { it?.toRecord() }

    override fun observeCompletedReviewLocalDays(
        learnerId: String,
        limit: Int,
    ): Flow<List<Long>> {
        require(learnerId.isNotBlank())
        require(limit in 1..MAX_REVIEW_COMPLETION_HISTORY_DAYS)
        return database.reviewDao().observeCompletedLocalDays(learnerId, limit)
    }

    override suspend fun countMistakes(): Int = database.problemDao().countActiveMistakes()

    override suspend fun checkpointForBackup() = backupSupport.checkpointForBackup()

    override suspend fun snapshotForBackup(
        sourceDatabaseFile: File,
        snapshotTarget: File,
    ) = backupSupport.snapshotForBackup(sourceDatabaseFile, snapshotTarget)

    override suspend fun clearAllData() = backupSupport.clearAllData()

    override suspend fun recordStudentModelPredictions(
        predictions: List<StudentModelPredictionRecord>,
    ) = studentModel.recordPredictions(predictions)

    override suspend fun resolveStudentModelPredictions(
        practiceUnitId: String,
        wasIndependentCorrect: Boolean,
        observedAtEpochMillis: Long,
        responseLatencyMs: Long?,
        hintCount: Int,
    ): Int = studentModel.resolvePredictions(
        practiceUnitId = practiceUnitId,
        wasIndependentCorrect = wasIndependentCorrect,
        observedAtEpochMillis = observedAtEpochMillis,
        responseLatencyMs = responseLatencyMs,
        hintCount = hintCount,
    )

    override suspend fun readResolvedStudentModelPredictions(
        modelId: String,
        modelVersion: String,
    ): List<ResolvedStudentModelPredictionRecord> =
        studentModel.readResolvedPredictions(modelId, modelVersion)

    override suspend fun findLastPredictionLatencyMs(practiceUnitId: String): Long? =
        studentModel.findLastLatencyMs(practiceUnitId)

    override suspend fun readPracticeUnitKnowledgeBindings(
        practiceUnitId: String,
    ): List<PracticeUnitKnowledgeBindingRecord> =
        database.problemOrganizationDao()
            .readKnowledgeBindingsForPracticeUnit(practiceUnitId)
            .map { row ->
                PracticeUnitKnowledgeBindingRecord(
                    bindingId = row.bindingId,
                    practiceUnitId = row.practiceUnitId,
                    knowledgeNodeId = row.knowledgeNodeId,
                    basisRevisionId = row.basisRevisionId,
                    taxonomyVersion = row.taxonomyVersion,
                    acceptedAtEpochMillis = row.acceptedAtEpochMillis,
                )
            }

    override suspend fun readCurrentPracticeUnitKnowledgeBindings(
        practiceUnitId: String,
    ): List<PracticeUnitKnowledgeBindingRecord> =
        database.problemOrganizationDao()
            .readCurrentKnowledgeBindingsForPracticeUnit(practiceUnitId)
            .map { row ->
                PracticeUnitKnowledgeBindingRecord(
                    bindingId = row.bindingId,
                    practiceUnitId = row.practiceUnitId,
                    knowledgeNodeId = row.knowledgeNodeId,
                    basisRevisionId = row.basisRevisionId,
                    taxonomyVersion = row.taxonomyVersion,
                    acceptedAtEpochMillis = row.acceptedAtEpochMillis,
                )
            }

    override suspend fun readPracticeUnitAssessment(
        practiceUnitId: String,
    ): PracticeUnitAssessmentRecord? =
        database.practiceUnitAssessmentDao()
            .readPracticeUnitAssessment(practiceUnitId)
            ?.let { row ->
                PracticeUnitAssessmentRecord(
                    practiceUnitId = row.practiceUnitId,
                    problemId = row.problemId,
                    problemRevisionId = row.problemRevisionId,
                    subject = row.subject,
                    unitTitle = row.unitTitle,
                    promptMarkdown = row.promptMarkdown,
                    questionDocumentSnapshot = row.questionDocumentSnapshot,
                    answerSpecId = row.answerSpecId,
                    answerSpecSnapshot = row.answerSpecSnapshot,
                    answerVerificationStatus = row.answerVerificationStatus,
                    sourceType = row.sourceType,
                    sourceReference = row.sourceReference,
                    revisionCreatedAtEpochMillis = row.revisionCreatedAtEpochMillis,
                )
            }

    override suspend fun recordTeachingAdvisories(entries: List<TeachingAdvisoryRecord>) {
        database.learningDao().recordTeachingAdvisories(entries.map(TeachingAdvisoryRecord::toEntity))
    }

    override suspend fun upsertTeachingAdvisories(entries: List<TeachingAdvisoryRecord>) {
        database.learningDao().upsertTeachingAdvisories(entries.map(TeachingAdvisoryRecord::toEntity))
    }

    /** D-M M7：咨询工具的取数口（节点/题/科目可选过滤）；limit 由调用方给本地常量。 */
    override suspend fun readTeachingAdvisoriesForTool(
        learnerId: String,
        knowledgeNodeId: String?,
        practiceUnitId: String?,
        subject: String?,
        subjectSourceId: String?,
        limit: Int,
    ): List<TeachingAdvisoryRecord> = database.learningDao()
        .readTeachingAdvisoriesForTool(
            learnerId = learnerId,
            knowledgeNodeId = knowledgeNodeId,
            practiceUnitId = practiceUnitId,
            subject = subject,
            subjectSourceId = subjectSourceId,
            limit = limit,
        )
        .map(LlmTeachingAdvisoryEntity::toRecord)

    override fun observeTeachingAdvisories(
        learnerId: String,
        practiceUnitId: String?,
    ): Flow<List<TeachingAdvisoryRecord>> =
        database.learningDao().observeTeachingAdvisories(learnerId, practiceUnitId)
            .map { rows -> rows.map(LlmTeachingAdvisoryEntity::toRecord) }

    /**
     * S16（W4-1 组装批量化）：一条 `IN (...)` 查询取回一组题的咨询行，按题分组后
     * 截断到每题最新 [MAX_TEACHING_ADVISORY_ROWS_PER_UNIT] 行 —— 与单题查询
     * （`... ORDER BY created_at DESC LIMIT 50`）逐位同义。SQLite 的 bound
     * parameter 上限以下按块切分（[MAX_TEACHING_ADVISORY_BATCH_UNITS]）。
     */
    override suspend fun readTeachingAdvisoriesForUnits(
        learnerId: String,
        practiceUnitIds: Collection<String>,
    ): List<TeachingAdvisoryRecord> {
        if (practiceUnitIds.isEmpty()) return emptyList()
        val advisories = mutableListOf<TeachingAdvisoryRecord>()
        practiceUnitIds.toList().chunked(MAX_TEACHING_ADVISORY_BATCH_UNITS).forEach { chunk ->
            val rows = database.learningDao()
                .readTeachingAdvisoriesForUnits(learnerId, chunk)
                .map(LlmTeachingAdvisoryEntity::toRecord)
            rows.groupBy(TeachingAdvisoryRecord::practiceUnitId)
                .values
                .forEach { perUnitRows ->
                    advisories += perUnitRows.take(MAX_TEACHING_ADVISORY_ROWS_PER_UNIT)
                }
        }
        return advisories
    }

    override fun observeKnowledgeQuestionLattice(
        learnerId: String,
    ): Flow<List<KnowledgeQuestionLatticeRecord>> =
        database.problemDao().observeKnowledgeQuestionLattice(learnerId)
            .map { rows -> rows.map(KnowledgeQuestionLatticeView::toRecord) }

    override suspend fun readSubjectMastery(
        learnerId: String,
        subject: String,
    ): List<SubjectMasteryRecord> = masteryOverview.readSubjectMastery(learnerId, subject)

    override suspend fun readMasteryAggregates(
        learnerId: String,
        knowledgeNodeIds: Set<String>,
    ): List<MasteryAggregateRecord> = masteryOverview.readMasteryAggregates(learnerId, knowledgeNodeIds)

    override suspend fun countReviewableKnowledgeNodes(subject: String): Int =
        masteryOverview.countReviewableKnowledgeNodes(subject)

    override suspend fun readDatabaseVersion(): Int {
        var version = 0
        database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared("PRAGMA user_version") { statement ->
                if (statement.step()) {
                    version = statement.getLong(0).toInt()
                }
            }
        }
        return version
    }

    override suspend fun ensurePseudoKnowledgeBinding(
        practiceUnitId: String,
        problemRevisionId: String,
        taxonomyVersion: String,
        subject: String,
        acceptedAtEpochMillis: Long,
    ): PracticeUnitKnowledgeBindingRecord? = knowledgeBase.ensurePseudoKnowledgeBinding(
        practiceUnitId = practiceUnitId,
        problemRevisionId = problemRevisionId,
        taxonomyVersion = taxonomyVersion,
        subject = subject,
        acceptedAtEpochMillis = acceptedAtEpochMillis,
    )

    override suspend fun ensurePseudoKnowledgeNode(
        subject: String,
        atEpochMillis: Long,
    ): KnowledgeNodeSeedRecord? = knowledgeBase.ensurePseudoKnowledgeNode(subject, atEpochMillis)

    override suspend fun readSubjectKnowledgeNodes(
        subject: String,
        limit: Int,
    ): List<KnowledgeNodeSeedRecord> = knowledgeBase.readSubjectKnowledgeNodes(subject, limit)

    /**
     * 6C-2 T5：恢复后派生索引按科重建的窄端口（`KnowledgeSearchIndexRebuildPort`）。
     *
     * 走与内容安装/读时自愈相同的 [KnowledgeSearchIndexBuilder.rebuildSubject]（KD-51 的
     * 分块短写事务、锚点最后写），不另写一套重建逻辑。恢复本身已经关闭了 room3 连接池，
     * 所以调用方（I1）只会在**数据库重新打开之后**调用这里。
     */
    override suspend fun listKnowledgeSubjects(): List<String> =
        database.problemOrganizationDao().readKnowledgeSubjects()

    override suspend fun rebuildKnowledgeSearchIndex(subject: String) {
        knowledgeSearchIndexBuilder.rebuildSubject(subject)
    }

    override suspend fun readSubjectKnowledgeRecallCandidates(
        subject: String,
        searchFeatures: Set<String>,
        limit: Int,
        queryText: String?,
    ): List<KnowledgeNodeSeedRecord> = knowledgeBase.readSubjectKnowledgeRecallCandidates(
        subject = subject,
        searchFeatures = searchFeatures,
        limit = limit,
        queryText = queryText,
    )

    override suspend fun readKnowledgeNodesByIds(ids: Set<String>): List<KnowledgeNodeSeedRecord> =
        knowledgeBase.readKnowledgeNodesByIds(ids)

    override suspend fun readActiveKnowledgeNodeIds(ids: Set<String>): Set<String> =
        knowledgeBase.readActiveKnowledgeNodeIds(ids)

    override suspend fun readFullLibraryScanNodes(): List<FullLibraryScanNodeRecord> =
        knowledgeBase.readFullLibraryScanNodes()

    override suspend fun readKnowledgeSourcesByIds(ids: Set<String>): List<KnowledgeSourceSeedRecord> =
        knowledgeBase.readKnowledgeSourcesByIds(ids)

    override suspend fun readSubjectKnowledgeNodeRelations(
        subject: String,
        limit: Int,
    ): List<KnowledgeNodeRelationRecord> = knowledgeBase.readSubjectKnowledgeNodeRelations(
        subject = subject,
        limit = limit,
    )

    override suspend fun readKnowledgeNodeRelationsForDependents(
        subject: String,
        dependentKnowledgeNodeIds: Set<String>,
    ): List<KnowledgeNodeRelationRecord> = knowledgeBase.readKnowledgeNodeRelationsForDependents(
        subject = subject,
        dependentKnowledgeNodeIds = dependentKnowledgeNodeIds,
    )

    override suspend fun readKnowledgeTeachingMaterialsForNodes(
        subject: String,
        knowledgeNodeIds: Set<String>,
        limit: Int,
    ): List<KnowledgeTeachingMaterialRecord> = knowledgeBase.readKnowledgeTeachingMaterialsForNodes(
        subject = subject,
        knowledgeNodeIds = knowledgeNodeIds,
        limit = limit,
    )

    override suspend fun readBestKnowledgeTeachingMaterialPerNode(
        subject: String,
        knowledgeNodeIds: Set<String>,
    ): Map<String, KnowledgeTeachingMaterialRecord> =
        knowledgeBase.readBestKnowledgeTeachingMaterialPerNode(
            subject = subject,
            knowledgeNodeIds = knowledgeNodeIds,
        )

    override suspend fun readKnowledgeTeachingMaterialsByIds(
        materialIds: Set<String>,
    ): List<KnowledgeTeachingMaterialRecord> =
        knowledgeBase.readKnowledgeTeachingMaterialsByIds(materialIds)

    override suspend fun readKnowledgeTeachingMaterialNodeBindings(
        materialIds: Set<String>,
    ): List<KnowledgeTeachingMaterialNodeBindingRecord> =
        knowledgeBase.readKnowledgeTeachingMaterialNodeBindings(materialIds)

    override suspend fun importKnowledgeNodeRelations(relations: List<KnowledgeNodeRelationRecord>) =
        knowledgeBase.importKnowledgeNodeRelations(relations)

    override suspend fun importKnowledgeTeachingMaterials(
        materials: List<KnowledgeTeachingMaterialRecord>,
        bindings: List<KnowledgeTeachingMaterialNodeBindingRecord>,
        sources: List<KnowledgeSourceSeedRecord>,
    ) = knowledgeBase.importKnowledgeTeachingMaterials(
        materials = materials,
        bindings = bindings,
        sources = sources,
    )

    override suspend fun readKnowledgeNodeSourceBindings(
        knowledgeNodeIds: Set<String>,
    ): List<KnowledgeNodeSourceBindingSeedRecord> =
        knowledgeBase.readKnowledgeNodeSourceBindings(knowledgeNodeIds)

    override suspend fun readKnowledgeNodeSuccessors(): Map<String, String> =
        knowledgeBase.readKnowledgeNodeSuccessors()

    /** S20：表级失效探针 → 无值信号（见 `KnowledgeReadPort.observeKnowledgeNodeChanges`）。 */
    override fun observeKnowledgeNodeChanges(): Flow<Unit> =
        database.problemOrganizationDao().observeKnowledgeNodeChangeSignal().map { }

    override suspend fun importKnowledgeBase(
        sources: List<KnowledgeSourceSeedRecord>,
        nodes: List<KnowledgeNodeSeedRecord>,
        bindings: List<KnowledgeNodeSourceBindingSeedRecord>,
    ) = knowledgeBase.importKnowledgeBase(
        sources = sources,
        nodes = nodes,
        bindings = bindings,
    )

    override suspend fun applyKnowledgeContentUpdate(
        command: KnowledgeContentUpdateCommand,
    ): KnowledgeContentUpdateResult = contentReconciler.applyKnowledgeContentUpdate(command)

    override suspend fun readContentInstallState(packId: String): ContentInstallStateRecord? =
        contentReconciler.readContentInstallState(packId)

    override suspend fun recordContentInstallState(record: ContentInstallStateRecord) =
        contentReconciler.recordContentInstallState(record)

    override suspend fun applyReviewedKnowledgePack(
        command: ApplyReviewedKnowledgePackCommand,
    ): List<KnowledgeGroundingResolutionRecord> =
        knowledgeBase.applyReviewedKnowledgePack(command)

    override suspend fun applyApprovedKnowledgeResearchPack(
        command: ApplyApprovedKnowledgeResearchPackCommand,
    ): List<KnowledgeGroundingResolutionRecord> =
        knowledgeBase.applyApprovedKnowledgeResearchPack(command)

    override fun observePendingKnowledgeGroundingRequests(
        limit: Int,
    ): Flow<List<KnowledgeGroundingRequestRecord>> =
        knowledgeBase.observePendingKnowledgeGroundingRequests(limit)

    override fun observePendingKnowledgeGroundingSummaries(
        limit: Int,
    ): Flow<List<KnowledgeGroundingSummaryRecord>> =
        knowledgeBase.observePendingKnowledgeGroundingSummaries(limit)

    override fun observeReviewedKnowledgeCoverage(): Flow<List<ReviewedKnowledgeCoverageRecord>> =
        knowledgeBase.observeReviewedKnowledgeCoverage()

    override suspend fun enqueueKnowledgeResearchReviewBundle(
        bundle: KnowledgeResearchReviewBundleRecord,
    ) = knowledgeResearchReviewStore.enqueue(bundle)

    override suspend fun readPendingKnowledgeResearchReviewBundles(
        limit: Int,
    ): List<KnowledgeResearchReviewBundleRecord> =
        knowledgeResearchReviewStore.readPending(limit)

    override suspend fun readKnowledgeResearchReviewBundle(
        bundleId: String,
    ): KnowledgeResearchReviewBundleRecord? =
        knowledgeResearchReviewStore.read(bundleId)

    override suspend fun decideKnowledgeResearchReviewBundle(
        command: DecideKnowledgeResearchReviewBundleCommand,
    ): KnowledgeResearchReviewBundleRecord =
        knowledgeResearchReviewStore.decide(command)

    override suspend fun recordKnowledgeGroundingRequests(
        requests: List<KnowledgeGroundingRequestRecord>,
    ) = knowledgeBase.recordKnowledgeGroundingRequests(requests)

    override suspend fun resolveKnowledgeGrounding(
        command: ResolveKnowledgeGroundingCommand,
    ): KnowledgeGroundingResolutionRecord = knowledgeBase.resolveKnowledgeGrounding(command)

    override suspend fun readKnowledgeGroundingResolution(
        groundingKey: String,
    ): KnowledgeGroundingResolutionRecord? =
        knowledgeBase.readKnowledgeGroundingResolution(groundingKey)

    override suspend fun findMistakeBySourceKey(sourceKey: String): MistakeRecord? {
        require(sourceKey.isNotBlank()) { "sourceKey must not be blank" }
        return database.problemDao().findMistakeBySourceKey(sourceKey)?.toRecord()
    }

    override suspend fun readMistakeDetail(errorBookEntryId: String): MistakeDetailRecord? {
        require(errorBookEntryId.isNotBlank()) { "errorBookEntryId must not be blank" }
        return database.mistakeDetailDao().read(errorBookEntryId)
    }

    override suspend fun readExactMistakeDetail(
        entryId: String,
        problemId: String,
        problemRevisionId: String,
    ): MistakeDetailRecord? {
        require(entryId.isNotBlank()) { "entryId must not be blank" }
        require(problemId.isNotBlank()) { "problemId must not be blank" }
        require(problemRevisionId.isNotBlank()) { "problemRevisionId must not be blank" }
        return database.mistakeDetailDao().readExact(entryId, problemId, problemRevisionId)
    }

    override suspend fun readCurrentMistakeDetails(
        entryIds: List<String>,
    ): List<MistakeDetailRecord> {
        require(entryIds.size <= 100) { "Mistake-detail batch is too large" }
        require(entryIds.all(String::isNotBlank)) { "entryIds must not contain blank values" }
        return database.mistakeDetailDao().readCurrentBatch(entryIds)
    }

    override suspend fun readMistakeRevisionHistory(
        errorBookEntryId: String,
    ): List<MistakeRevisionSummaryRecord> {
        require(errorBookEntryId.isNotBlank()) { "errorBookEntryId must not be blank" }
        return database.mistakeDetailDao().readRevisionHistory(errorBookEntryId)
    }

    override suspend fun updateErrorBookEntryNote(
        entryId: String,
        note: String?,
        updatedAtEpochMillis: Long,
    ): Boolean = database.mistakeDetailDao().setUserNote(entryId, note, updatedAtEpochMillis)

    override suspend fun setErrorBookEntryFavorite(
        entryId: String,
        isFavorite: Boolean,
        updatedAtEpochMillis: Long,
    ): Boolean = database.mistakeDetailDao().setEntryFavorite(
        entryId = entryId,
        isFavorite = isFavorite,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    override suspend fun createProblemDraft(
        command: CreateProblemDraftCommand,
    ): ProblemDraftWriteResult = database.problemDraftTransactionDao().create(command)

    override suspend fun appendProblemDraftSourceAsset(
        command: AppendProblemDraftSourceAssetCommand,
    ): AppendProblemDraftSourceAssetResult =
        database.problemDraftTransactionDao().appendSourceAsset(command)

    override suspend fun reviseProblemDraft(
        command: ReviseProblemDraftCommand,
    ): ProblemDraftWriteResult = database.problemDraftTransactionDao().revise(command)

    override suspend fun replaceProblemDraft(
        command: ReplaceProblemDraftCommand,
    ): ProblemDraftReplacementResult = database.withWriteTransaction {
        val draft = database.problemDraftTransactionDao().read(command.replacedDraftId)
        val workspace = draft
            ?.takeIf { it.status == StudyDbValue.ProblemDraftStatus.EDITING }
            ?.let { database.problemDraftEditWorkspaceDao().read(it.draftId) }
        val result = database.problemDraftTransactionDao().replace(command)
        workspace?.let { database.problemDraftEditWorkspaceDao().deleteExactAfterFinalization(it.toConsumeCommand()) }
        result
    }

    override suspend fun splitProblemDraft(
        command: SplitProblemDraftCommand,
    ): ProblemDraftSplitResult = database.withWriteTransaction {
        val draft = database.problemDraftTransactionDao().read(command.replacedDraftId)
        val workspace = draft
            ?.takeIf { it.status == StudyDbValue.ProblemDraftStatus.EDITING }
            ?.let { database.problemDraftEditWorkspaceDao().read(it.draftId) }
        val result = database.problemDraftTransactionDao().split(command)
        workspace?.let {
            database.problemDraftEditWorkspaceDao().deleteExactAfterFinalization(
                it.toConsumeCommand(),
            )
        }
        result
    }

    /**
     * A8：手动建题的空源草稿是**合法记录**——`sourceAsset == null`、`sourceAssets` 为空，
     * 不是"图丢了"。读取分支由 ProblemDraftTransactionDao 统一实现（空态 + 半空坏行
     * fail-closed）；这里不再对空源行硬崩。
     */
    override suspend fun readProblemDraft(draftId: String): ProblemDraftRecord? {
        require(draftId.isNotBlank()) { "draftId must not be blank" }
        return database.problemDraftTransactionDao().read(draftId)
    }

    override suspend fun readCanonicalSourceAsset(
        sourceAssetId: String,
    ): CanonicalSourceAssetRecord? {
        require(sourceAssetId.isNotBlank()) { "sourceAssetId must not be blank" }
        return database.problemDraftTransactionDao().readCanonicalSourceAsset(sourceAssetId)
    }

    override suspend fun readUnreferencedCanonicalAssets(): List<CanonicalSourceAssetRecord> =
        database.pendingCaptureDao()
            .findUnreferencedCanonicalAssets()
            .map(CanonicalSourceAssetRow::toRecord)

    /**
     * 引用集合 = 全表行 − 未引用行（AUD-05：备份成包/恢复闭合的"必须进包的图"）。
     *
     * 引用口径含图生成尝试产物（`figure_generation_attempt.output_asset_id`）——因为它与
     * 清理认领共用 [PendingCaptureDao.findUnreferencedCanonicalAssets] 这一处判定，备份/恢复
     * 自动纳入，不需要第二个 EXISTS 列表（见 `CaptureReadPort.readReferencedCanonicalAssets`）。
     *
     * 两条查询读的是**同一份快照库文件**（成包时先把实时库快照下来，再对快照文件开只读口），
     * 那份文件没有写者，因此相减得到的引用集合与归档里的库自带的自洽：既不会把快照之后
     * 才登记的图算进来（快照里根本没有那行），也不会漏掉快照里已引用的图。
     */
    override suspend fun readReferencedCanonicalAssets(): List<CanonicalSourceAssetRecord> {
        val dao = database.pendingCaptureDao()
        val unreferenced = dao.findUnreferencedCanonicalAssets()
            .mapTo(HashSet<String>()) { row -> row.sourceAssetId }
        return dao.findAllCanonicalAssets()
            .filterNot { row -> row.sourceAssetId in unreferenced }
            .map(CanonicalSourceAssetRow::toRecord)
    }

    override suspend fun abandonPendingProblemDraft(
        draftId: String,
        expectedRevisionNumber: Int,
        abandonedAtEpochMillis: Long,
    ): Boolean = database.problemDraftTransactionDao().abandonPendingDraft(
        draftId = draftId,
        expectedRevisionNumber = expectedRevisionNumber,
        abandonedAtEpochMillis = abandonedAtEpochMillis,
    )

    override suspend fun claimUnreferencedCanonicalAssets(
        createdBeforeEpochMillis: Long,
    ): List<CanonicalSourceAssetRecord> = database.withWriteTransaction {
        val dao = database.pendingCaptureDao()
        val claimed = dao.findUnreferencedCanonicalAssets()
            .filter { it.createdAtEpochMillis < createdBeforeEpochMillis }
        claimed.forEach { dao.deleteCanonicalSourceAssetById(it.sourceAssetId) }
        claimed.map(CanonicalSourceAssetRow::toRecord)
    }

    override suspend fun insertOrphanCanonicalAssetForTest(asset: CanonicalSourceAssetRecord) {
        upsertCanonicalSourceAssetRow(asset)
    }

    override suspend fun registerCanonicalSourceAsset(asset: CanonicalSourceAssetRecord) {
        upsertCanonicalSourceAssetRow(asset)
    }

    /**
     * 登记资产行；同 id 行已存在时只刷新 `created_at_epoch_millis`，像素与尺寸不动。
     *
     * 刷新是必需的：id 与文件名都由内容哈希推出，同一张图再次登记会命中
     * `INSERT OR IGNORE` 而保留旧时间戳，于是孤儿清理的宽限期（按该字段过滤）对
     * "会话被删后学生重发同一张图"失效——那条行会在"登记 → 建立引用"的窗口里被认领回收。
     *
     * 写调用方给的值而不是当前墙钟：题图校验按"请求 occurredAt == 行内值"做相等比较
     * （见 RoomCaptureWorkflowMappings.matchesAssessment），写新墙钟会让已持久化的请求失配。
     * 也不用 UPSERT——`ON CONFLICT DO UPDATE` 需要 SQLite 3.24 / API 30+，本项目 minSdk 23。
     */
    private suspend fun upsertCanonicalSourceAssetRow(asset: CanonicalSourceAssetRecord) {
        database.withRawConnection(isReadOnly = false) { connection ->
            connection.usePrepared(
                "INSERT OR IGNORE INTO canonical_source_asset (" +
                    "source_asset_id, content_sha256, relative_path, mime_type, byte_size, " +
                    "width, height, source_type, created_at_epoch_millis" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ) { statement ->
                statement.bindText(1, asset.sourceAssetId)
                statement.bindText(2, asset.contentSha256)
                statement.bindText(3, asset.relativePath)
                statement.bindText(4, asset.mimeType)
                statement.bindLong(5, asset.byteSize)
                statement.bindLong(6, asset.width.toLong())
                statement.bindLong(7, asset.height.toLong())
                statement.bindText(8, asset.sourceType)
                statement.bindLong(9, asset.createdAtEpochMillis)
                statement.step()
            }
            connection.usePrepared(
                "UPDATE canonical_source_asset SET created_at_epoch_millis = ? " +
                    "WHERE source_asset_id = ?",
            ) { statement ->
                statement.bindLong(1, asset.createdAtEpochMillis)
                statement.bindText(2, asset.sourceAssetId)
                statement.step()
            }
        }
    }

    override suspend fun readPendingCaptureDraft(draftId: String): PendingCaptureDraftRecord? =
        pendingCaptures.readDraft(draftId)

    override suspend fun createBatchImportJob(
        command: CreateBatchImportJobCommand,
    ): BatchImportJobRecord = batchImports.create(command)

    override suspend fun readBatchImportJob(jobId: String): BatchImportJobRecord? =
        batchImports.read(jobId)

    override suspend fun updateBatchImportJobStatus(
        jobId: String,
        expectedStatus: String,
        nextStatus: String,
        occurredAtEpochMillis: Long,
    ): Boolean = batchImports.updateStatus(
        jobId,
        expectedStatus,
        nextStatus,
        occurredAtEpochMillis,
    )

    override suspend fun requeueInterruptedBatchImportPages(
        jobId: String,
        occurredAtEpochMillis: Long,
    ): Int = batchImports.requeueInterrupted(jobId, occurredAtEpochMillis)

    override suspend fun claimNextBatchImportPage(
        jobId: String,
        occurredAtEpochMillis: Long,
    ): BatchImportPageRecord? = batchImports.claimNext(jobId, occurredAtEpochMillis)

    override suspend fun completeBatchImportPage(
        jobId: String,
        pageIndex: Int,
        draftId: String,
        occurredAtEpochMillis: Long,
    ): Boolean = batchImports.completePage(jobId, pageIndex, draftId, occurredAtEpochMillis)

    override suspend fun readNextPendingBatchImportSplitPage(
        jobId: String,
    ): BatchImportPageRecord? = batchImports.readNextPendingSplitPage(jobId)

    override suspend fun settleBatchImportPageSplit(
        jobId: String,
        pageIndex: Int,
        occurredAtEpochMillis: Long,
    ): Boolean = batchImports.settlePageSplit(jobId, pageIndex, occurredAtEpochMillis)

    override suspend fun claimBatchImportBoundary(
        jobId: String,
        pageIndex: Int,
        occurredAtEpochMillis: Long,
    ): Boolean = batchImports.claimBoundary(jobId, pageIndex, occurredAtEpochMillis)

    override suspend fun requeueInterruptedBatchImportBoundaries(
        jobId: String,
        occurredAtEpochMillis: Long,
    ): Int = batchImports.requeueInterruptedBoundaries(jobId, occurredAtEpochMillis)

    override suspend fun resolveBatchImportBoundary(
        command: ResolveBatchImportBoundaryCommand,
    ): BatchImportJobRecord = batchImports.resolveBoundary(command)

    override suspend fun failBatchImportBoundary(
        jobId: String,
        pageIndex: Int,
        occurredAtEpochMillis: Long,
    ): Boolean = batchImports.failBoundary(jobId, pageIndex, occurredAtEpochMillis)

    override suspend fun failBatchImportPage(
        jobId: String,
        pageIndex: Int,
        failureCode: String,
        occurredAtEpochMillis: Long,
    ): Boolean = batchImports.failPage(jobId, pageIndex, failureCode, occurredAtEpochMillis)

    override suspend fun retryBatchImportPage(
        jobId: String,
        pageIndex: Int,
        occurredAtEpochMillis: Long,
    ): Boolean = batchImports.retryPage(jobId, pageIndex, occurredAtEpochMillis)

    override suspend fun skipBatchImportPage(
        jobId: String,
        pageIndex: Int,
        occurredAtEpochMillis: Long,
    ): Boolean = batchImports.skipPage(jobId, pageIndex, occurredAtEpochMillis)

    override suspend fun finishBatchImportIfSettled(
        jobId: String,
        occurredAtEpochMillis: Long,
    ): Boolean = batchImports.finishIfSettled(jobId, occurredAtEpochMillis)

    override suspend fun hasRetainedBatchImportSourceUri(sourceUri: String): Boolean {
        return batchImports.hasRetainedSourceUri(sourceUri)
    }

    override suspend fun readProblemDraftEditWorkspace(
        draftId: String,
    ): ProblemDraftEditWorkspaceRecord? =
        database.problemDraftEditWorkspaceDao().read(draftId)

    override suspend fun saveProblemDraftEditWorkspace(
        command: SaveProblemDraftEditWorkspaceCommand,
    ): ProblemDraftEditWorkspaceWriteResult =
        database.problemDraftEditWorkspaceDao().save(command)

    override suspend fun consumeProblemDraftEditWorkspace(
        command: ConsumeProblemDraftEditWorkspaceCommand,
    ): Boolean = database.problemDraftEditWorkspaceDao().consume(command)

    override suspend fun commitProblemDraft(
        command: CommitProblemDraftCommand,
    ): ProblemDraftCommitOutcome = database.problemDraftTransactionDao().commit(command)

    override suspend fun confirmAndCommitProblemDraftFromWorkspace(
        command: ConfirmAndCommitProblemDraftFromWorkspaceCommand,
    ): ProblemDraftCommitOutcome = database.withWriteTransaction {
        DatabaseContractValidator.validateConfirmAndCommitProblemDraftFromWorkspace(command)
        database.problemDraftTransactionDao().replayCommit(
            command = command.commit,
            allowTutorDraft = false,
        )?.let { return@withWriteTransaction it }
        val workspace = database.problemDraftEditWorkspaceDao()
            .requireExactForConfirmation(command.workspace)
        val confirmedRevision = workspace.toConfirmedRevision(command.workspace)
        database.problemDraftTransactionDao().revise(
            ReviseProblemDraftCommand(
                draftId = command.workspace.draftId,
                expectedRevisionNumber = command.workspace.basisRevisionNumber,
                revision = confirmedRevision,
            ),
        )
        val result = database.problemDraftTransactionDao().commit(command.commit)
        database.problemDraftEditWorkspaceDao().deleteExactAfterFinalization(
            command.workspace.toConsumeCommand(),
        )
        result
    }

    override suspend fun attachCleanRedrawAsset(
        revisionId: String,
        asset: CanonicalSourceAssetRecord,
        role: String,
    ): Boolean = database.withWriteTransaction {
        database.problemDraftTransactionDao().attachCleanRedrawAsset(
            revisionId = revisionId,
            asset = asset,
            role = role,
        )
    }

    override suspend fun confirmTutorSession(
        command: ConfirmTutorSessionCommand,
    ): TutorSessionWriteResult = database.problemDraftTransactionDao().confirmTutorSession(command)

    override suspend fun confirmTutorSessionFromWorkspace(
        command: ConfirmTutorSessionFromWorkspaceCommand,
    ): TutorSessionWriteResult = database.withWriteTransaction {
        DatabaseContractValidator.validateConfirmTutorSessionFromWorkspace(command)
        database.problemDraftTransactionDao().readTutorSession(command.sessionId)?.let { existing ->
            if (
                existing.draftId != command.workspace.draftId ||
                existing.draftRevisionNumber != command.workspace.basisRevisionNumber + 1 ||
                existing.createdAtEpochMillis != command.workspace.finalOccurredAtEpochMillis
            ) {
                throw ImmutablePayloadConflictException("tutor_session", command.sessionId)
            }
            return@withWriteTransaction TutorSessionWriteResult(
                created = false,
                session = existing,
            )
        }
        val workspace = database.problemDraftEditWorkspaceDao()
            .requireExactForConfirmation(command.workspace)
        val result = database.problemDraftTransactionDao().confirmTutorSession(
            ConfirmTutorSessionCommand(
                sessionId = command.sessionId,
                draftId = command.workspace.draftId,
                expectedRevisionNumber = command.workspace.basisRevisionNumber,
                confirmedRevision = workspace.toConfirmedRevision(command.workspace),
                createdAtEpochMillis = command.workspace.finalOccurredAtEpochMillis,
            ),
        )
        database.problemDraftEditWorkspaceDao().deleteExactAfterFinalization(
            command.workspace.toConsumeCommand(),
        )
        result
    }

    override suspend fun readTutorSession(sessionId: String): TutorSessionRecord? {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        return database.problemDraftTransactionDao().readTutorSession(sessionId)
    }

    override suspend fun createTutorConversation(
        command: CreateTutorConversationDatabaseCommand,
    ): TutorConversationRecord = database.tutorConversationDao().createConversation(command)

    override suspend fun appendTutorStudentMessage(
        command: AppendTutorStudentMessageDatabaseCommand,
    ): TutorMessageRecord = database.tutorConversationDao().appendStudentMessage(command)

    override suspend fun bindTutorStudentMessageQuestion(
        command: BindStudentMessageQuestionDatabaseCommand,
    ): TutorMessageRecord = database.tutorConversationDao().bindStudentMessageQuestion(command)

    override suspend fun readTutorMessageSourceAssets(
        messageIds: List<String>,
    ): List<TutorMessageSourceAssetRecord> =
        database.tutorConversationDao().readMessageSourceAssets(messageIds)

    override suspend fun appendTutorAssistantMessage(
        command: AppendTutorAssistantMessageDatabaseCommand,
    ): TutorMessageRecord = database.tutorConversationDao().appendAssistantMessage(command)

    override suspend fun updateTutorMessageStatus(
        command: UpdateTutorMessageStatusDatabaseCommand,
    ): TutorMessageRecord = database.tutorConversationDao().updateMessageStatus(command)

    override suspend fun setTutorInteractionMode(
        command: SetTutorInteractionModeDatabaseCommand,
    ): TutorConversationRecord = database.tutorConversationDao().setInteractionMode(command)

    override suspend fun pauseTutorConversation(
        conversationId: String,
        updatedAtEpochMillis: Long,
    ): TutorConversationRecord = database.tutorConversationDao().pauseConversation(
        conversationId = conversationId,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    override suspend fun archiveTutorConversation(
        conversationId: String,
        updatedAtEpochMillis: Long,
    ): TutorConversationRecord = database.tutorConversationDao().archiveConversation(
        conversationId = conversationId,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    override suspend fun deleteTutorConversation(conversationId: String) {
        database.tutorConversationDao().deleteConversation(conversationId)
    }

    override suspend fun createAgentPendingRequest(
        command: CreateAgentPendingRequestDatabaseCommand,
    ): AgentPendingRequestRecord =
        database.agentPendingRequestDao().createRequest(command)

    override suspend fun resolveAgentPendingRequest(
        command: ResolveAgentPendingRequestDatabaseCommand,
    ): AgentPendingRequestRecord =
        database.agentPendingRequestDao().resolveRequest(command)

    override fun observePendingAgentRequests(
        conversationArea: String?,
    ): Flow<List<AgentPendingRequestRecord>> =
        database.agentPendingRequestDao().observePending(conversationArea)

    override suspend fun readPendingAgentRequests(
        conversationArea: String?,
    ): List<AgentPendingRequestRecord> =
        database.agentPendingRequestDao().readPending(conversationArea)

    override suspend fun readResolvedAgentPendingRequests(
        conversationArea: String,
        limit: Int,
    ): List<AgentPendingRequestRecord> =
        database.agentPendingRequestDao().readResolved(conversationArea = conversationArea, limit = limit)

    override suspend fun createMistakeExportRecord(
        command: CreateMistakeExportRecordCommand,
    ): MistakeExportRecordRow = database.mistakeExportRecordDao().createRunning(command)

    override suspend fun completeMistakeExportRecord(
        command: CompleteMistakeExportRecordCommand,
    ): MistakeExportRecordRow? = database.mistakeExportRecordDao().complete(command)

    override suspend fun failMistakeExportRecord(
        command: FailMistakeExportRecordCommand,
    ): MistakeExportRecordRow? = database.mistakeExportRecordDao().fail(command)

    override fun observeMistakeExportRecords(limit: Int): Flow<List<MistakeExportRecordRow>> =
        database.mistakeExportRecordDao().observe(limit)

    override suspend fun readMistakeExportRecord(exportId: String): MistakeExportRecordRow? =
        database.mistakeExportRecordDao().read(exportId)

    override suspend fun reconcileStaleMistakeExportRecords(
        staleBeforeEpochMillis: Long,
        atEpochMillis: Long,
        failureMessage: String,
    ): Int = database.mistakeExportRecordDao().reconcileStaleRunning(
        staleBeforeEpochMillis = staleBeforeEpochMillis,
        atEpochMillis = atEpochMillis,
        failureMessage = failureMessage,
    )

    override suspend fun pruneMistakeExportRecords(keepNewest: Int): Int =
        database.mistakeExportRecordDao().prune(keepNewest)

    /**
     * 4E WG-08 启动对账：只落 `FAILED` 终态，不重派、不抹产物（见 `FigureGenerationAuditPort`）。
     * 判定与两段更新在 DAO 的同一个写事务里完成。
     */
    override suspend fun reconcileStaleFigureGenerationRuns(
        staleBeforeEpochMillis: Long,
        atEpochMillis: Long,
        diagnostics: String,
    ): FigureGenerationReconcileCounts =
        database.figureGenerationReconcileDao().reconcileStaleRunning(
            staleBeforeEpochMillis = staleBeforeEpochMillis,
            atEpochMillis = atEpochMillis,
            diagnostics = diagnostics,
        )

    /**
     * 4E WG-08 T1b：建立操作行（`PENDING`）。同一 `operationId` 的重放是 IGNORE——
     * 既有行保留它的状态，不因重放被覆盖回 PENDING。三个审计计数在建立时全为 0，
     * 只在 [finishFigureGenerationOperation] 落终态时写。
     */
    override suspend fun beginFigureGenerationOperation(
        command: BeginFigureGenerationOperationCommand,
    ): Boolean {
        database.figureGenerationDao().insertOperation(
            FigureGenerationOperationEntity(
                operationId = command.operationId,
                learnerId = command.learnerId,
                sourceKind = command.sourceKind,
                sourceId = command.sourceId,
                basisFingerprint = command.basisFingerprint,
                purpose = command.purpose,
                policyVersion = command.policyVersion,
                status = command.status,
                reservedRemoteDispatches = 0,
                usedRemoteDispatches = 0,
                finalAnswerReserved = false,
                createdAtEpochMillis = command.createdAtEpochMillis,
                updatedAtEpochMillis = command.createdAtEpochMillis,
            ),
        )
        return true
    }

    override suspend fun markFigureGenerationOperationRunning(
        operationId: String,
        updatedAtEpochMillis: Long,
    ): Boolean = database.figureGenerationDao().updateOperationStatus(
        operationId = operationId,
        status = "RUNNING",
        updatedAtEpochMillis = updatedAtEpochMillis,
    ) > 0

    override suspend fun finishFigureGenerationOperation(
        command: FinishFigureGenerationOperationCommand,
    ): Boolean = database.figureGenerationDao().finishOperation(
        operationId = command.operationId,
        status = command.status,
        reservedRemoteDispatches = command.reservedRemoteDispatches,
        usedRemoteDispatches = command.usedRemoteDispatches,
        finalAnswerReserved = command.finalAnswerReserved,
        updatedAtEpochMillis = command.updatedAtEpochMillis,
    ) > 0

    override suspend fun beginFigureGenerationAttempt(
        command: BeginFigureGenerationAttemptCommand,
    ): Boolean {
        database.figureGenerationDao().insertAttempt(
            FigureGenerationAttemptEntity(
                attemptId = command.attemptId,
                operationId = command.operationId,
                attemptOrdinal = command.attemptOrdinal,
                method = command.method,
                status = command.status,
                sourceSha256 = command.sourceSha256,
                inputFingerprint = command.inputFingerprint,
                reservationId = command.reservationId,
                startedAtEpochMillis = command.startedAtEpochMillis,
            ),
        )
        return true
    }

    /**
     * 本地方法（JS）尝试：序号由 DAO 的 `@Transaction` 方法在同一个写事务里分配
     * （负数区间，与账本远程序号不相交）；`reservation_id` 为 NULL。
     */
    override suspend fun beginLocalFigureGenerationAttempt(
        command: BeginLocalFigureGenerationAttemptCommand,
    ): FigureGenerationAttemptIdentity? {
        val entity = database.figureGenerationDao().insertLocalAttempt(
            operationId = command.operationId,
            method = command.method,
            status = command.status,
            sourceSha256 = command.sourceSha256,
            inputFingerprint = command.inputFingerprint,
            startedAtEpochMillis = command.startedAtEpochMillis,
        )
        return FigureGenerationAttemptIdentity(
            attemptId = entity.attemptId,
            attemptOrdinal = entity.attemptOrdinal,
        )
    }

    override suspend fun finishFigureGenerationAttempt(
        command: FinishFigureGenerationAttemptCommand,
    ): Boolean = database.figureGenerationDao().finishAttempt(
        attemptId = command.attemptId,
        status = command.status,
        outputAssetId = command.outputAssetId,
        diagnostics = command.diagnostics,
        endedAtEpochMillis = command.endedAtEpochMillis,
    ) > 0

    override suspend fun attachFigureGenerationAttemptOutput(
        attemptId: String,
        outputAssetId: String,
    ): Boolean = database.figureGenerationDao().attachAttemptOutputAsset(
        attemptId = attemptId,
        outputAssetId = outputAssetId,
    ) > 0

    override suspend fun createCleanRedrawTask(
        command: CreateCleanRedrawTaskCommand,
    ): CleanRedrawTaskRecord = database.cleanRedrawTaskDao().createTask(command)

    override suspend fun readCleanRedrawTask(taskId: String): CleanRedrawTaskRecord? =
        database.cleanRedrawTaskDao().readTask(taskId)

    override suspend fun readCleanRedrawTaskByFingerprint(
        requestFingerprint: String,
    ): CleanRedrawTaskRecord? =
        database.cleanRedrawTaskDao().readTaskByFingerprint(requestFingerprint)

    override suspend fun readResumableCleanRedrawTasks(
        maxAttempts: Int,
        staleBeforeEpochMillis: Long,
    ): List<CleanRedrawTaskRecord> = database.cleanRedrawTaskDao().readResumableTasks(
        maxAttempts = maxAttempts,
        staleBeforeEpochMillis = staleBeforeEpochMillis,
    )

    override suspend fun claimCleanRedrawTaskAttempt(
        command: ClaimCleanRedrawTaskAttemptCommand,
    ): CleanRedrawTaskRecord? = database.cleanRedrawTaskDao().claimTaskAttempt(command)

    override suspend fun finishCleanRedrawTask(
        command: FinishCleanRedrawTaskCommand,
    ): CleanRedrawTaskRecord? = database.cleanRedrawTaskDao().finishTaskAttempt(command)

    override suspend fun isCurrentProblemRevision(revisionId: String): Boolean =
        database.problemDraftTransactionDao().isCurrentProblemRevision(revisionId)

    override suspend fun readLatestCleanRedrawTask(
        revisionId: String,
        operationIdPrefix: String?,
    ): CleanRedrawTaskRecord? = database.cleanRedrawTaskDao()
        .readLatestTaskForRevision(revisionId, operationIdPrefix)

    override fun observeLatestCleanRedrawTask(
        revisionId: String,
        operationIdPrefix: String?,
    ): Flow<CleanRedrawTaskRecord?> = database.cleanRedrawTaskDao()
        .observeLatestTaskForRevision(revisionId, operationIdPrefix)

    override suspend fun readRevisionImageSources(
        problemRevisionId: String,
    ): List<RevisionImageSource> = database.cleanRedrawTaskDao()
        .readRevisionImageSources(problemRevisionId)

    override suspend fun saveTutorConversationDraft(
        conversationId: String,
        draft: String,
        updatedAtEpochMillis: Long,
    ) {
        database.tutorConversationDao().saveStudentDraft(
            SaveTutorConversationDraftDatabaseCommand(
                conversationId = conversationId,
                draft = draft,
                updatedAtEpochMillis = updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun clearTutorConversationDraft(
        conversationId: String,
        updatedAtEpochMillis: Long,
    ) {
        database.tutorConversationDao().clearStudentDraft(
            ClearTutorConversationDraftDatabaseCommand(
                conversationId = conversationId,
                updatedAtEpochMillis = updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun commitTutorSession(
        command: CommitTutorSessionCommand,
    ): ProblemDraftCommitOutcome = database.withWriteTransaction {
        val result = database.problemDraftTransactionDao().commitTutorSession(command)
        val receipt = result.commit.receipt
        database.tutorExposureDao().bindAnchor(
            PersistTutorSessionAnchorCommand(
                sessionId = command.sessionId,
                problemRevisionId = receipt.problemRevisionId,
                practiceUnitId = receipt.practiceUnitId,
                source = "DRAFT_COMMIT",
                anchoredAtEpochMillis = receipt.committedAtEpochMillis,
            ),
        )
        result
    }

    override suspend fun endTutorSession(
        command: EndTutorSessionCommand,
    ): EndTutorSessionResult = database.withWriteTransaction {
        val session = database.problemDraftTransactionDao().readTutorSession(command.sessionId)
        val workspace = session?.let { database.problemDraftEditWorkspaceDao().read(it.draftId) }
        val result = database.problemDraftTransactionDao().endTutorSession(command)
        workspace?.let { database.problemDraftEditWorkspaceDao().deleteExactAfterFinalization(it.toConsumeCommand()) }
        result
    }

    override suspend fun recordTutorChoice(
        command: PersistTutorChoiceCommand,
    ): TutorTurnResponseRecord = database.tutorInteractionDao().recordChoice(command)

    override suspend fun recordTutorMove(
        command: PersistTutorMoveCommand,
    ): TutorTurnResponseRecord = database.tutorInteractionDao().recordMove(command)

    override suspend fun revealTutorSolution(
        command: PersistTutorRevealCommand,
    ): TutorTurnResponseRecord = database.tutorInteractionDao().revealSolution(command)

    override suspend fun recordTutorSolutionExposure(
        command: PersistTutorAnswerExposureCommand,
    ): TutorAnswerExposureRecord = database.tutorExposureDao().recordVisibleExposure(command)

    override suspend fun bindTutorSessionProblemAnchor(
        command: PersistTutorSessionAnchorCommand,
    ): TutorSessionProblemAnchorRecord = database.tutorExposureDao().bindAnchor(command)

    override suspend fun readLatestTutorSessionAnchor(
        practiceUnitId: String,
        learnerId: String,
    ): TutorSessionProblemAnchorRecord? = database.tutorExposureDao()
        .readLatestAnchorForPracticeUnit(practiceUnitId, learnerId)

    override suspend fun readRecentTutorSessionAnchors(
        practiceUnitId: String,
        learnerId: String,
        limit: Int,
    ): List<TutorSessionProblemAnchorRecord> = database.tutorExposureDao()
        .readRecentAnchorsForPracticeUnit(practiceUnitId, learnerId, limit)

    override suspend fun reconcileTutorAnswerExposures(learnerId: String, limit: Int): Int =
        database.tutorExposureDao().reconcilePending(learnerId, limit)

    override suspend fun readTutorAnswerExposure(
        modelTaskRequestId: String,
    ): TutorAnswerExposureRecord? =
        database.tutorExposureDao().readExposure(modelTaskRequestId)

    override suspend fun readTutorAnswerExposures(
        modelTaskRequestIds: Set<String>,
    ): List<TutorAnswerExposureRecord> =
        database.tutorExposureDao().readExposures(modelTaskRequestIds)

    override fun observeModelTask(
        requestId: String,
    ) = database.modelTaskTransactionDao().observe(requestId)

    override fun observeModelTasks(
        subjectId: String,
        kind: com.tingyun.smartmistakebook.core.model.ModelTaskKind,
    ) = database.modelTaskTransactionDao().observeBySubject(subjectId, kind)

    override fun observeRecentModelTasks(
        subjectId: String,
        kind: com.tingyun.smartmistakebook.core.model.ModelTaskKind,
        limit: Int,
    ) = database.modelTaskTransactionDao().observeRecentBySubject(subjectId, kind, limit)

    override suspend fun readModelTask(requestId: String) =
        database.modelTaskTransactionDao().read(requestId)

    override suspend fun createModelTask(command: CreateModelTaskCommand) =
        database.modelTaskTransactionDao().create(command)

    override suspend fun reserveModelTaskRemoteDispatch(
        command: ReserveModelTaskRemoteDispatchCommand,
    ) = database.modelTaskTransactionDao().reserveRemoteDispatch(command)

    override suspend fun reserveModelTaskOperationDispatch(
        command: com.tingyun.smartmistakebook.core.database.ReserveModelTaskOperationDispatchCommand,
    ) = database.modelTaskTransactionDao().reserveOperationRemoteDispatch(command)

    override suspend fun transitionModelTask(command: TransitionModelTaskCommand) =
        database.modelTaskTransactionDao().transition(command)

    override suspend fun saveAssessmentItemSnapshot(item: AssessmentItemSnapshotSeedRecord) {
        DatabaseContractValidator.validateAssessmentItem(item)
        database.immutableLearningFactDao().saveAssessmentItem(item.toEntity())
    }

    override suspend fun findAssessmentItemSnapshot(
        assessmentItemSnapshotId: String,
    ): AssessmentItemSnapshotSeedRecord? = database.immutableLearningFactDao()
        .findAssessmentItem(assessmentItemSnapshotId)
        ?.toRecord()

    /**
     * G5-A / KD-67：`verification_status` 单列 CAS（契约见
     * `SeedAssessmentPort.compareAndSetAssessmentItemVerificationStatus`）。
     *
     * 只提交状态迁移：一条参数化 `UPDATE`，SET 只写 `verification_status`，WHERE 同时
     * 限定 id 与调用方读到的当前状态；其余列原样。返回**真实受影响行数**（0/1），
     * 不新增行、不做 upsert——行不存在与状态已变都如实回报 0。
     */
    override suspend fun compareAndSetAssessmentItemVerificationStatus(
        assessmentItemSnapshotId: String,
        expectedCurrentStatus: String,
        newVerificationStatus: String,
    ): Int {
        DatabaseContractValidator.validateAssessmentItemVerificationStatusCas(
            assessmentItemSnapshotId = assessmentItemSnapshotId,
            expectedCurrentStatus = expectedCurrentStatus,
            newVerificationStatus = newVerificationStatus,
        )
        return database.immutableLearningFactDao().compareAndSetAssessmentItemVerificationStatus(
            assessmentItemSnapshotId = assessmentItemSnapshotId,
            expectedCurrentStatus = expectedCurrentStatus,
            newVerificationStatus = newVerificationStatus,
        )
    }

    /**
     * KD-41：损坏行的原地修复口（契约见
     * `SeedAssessmentPort.overwriteAssessmentItemSnapshot`——只对"既有行解不开"用）。
     *
     * 覆盖写进来的内容走与 [saveAssessmentItemSnapshot] 同一道契约校验：修复口不能变成绕过
     * 契约的旁路（借"修复"把非法行塞进库）。
     */
    override suspend fun overwriteAssessmentItemSnapshot(item: AssessmentItemSnapshotSeedRecord) {
        DatabaseContractValidator.validateAssessmentItem(item)
        database.immutableLearningFactDao().overwriteAssessmentItem(item.toEntity())
    }

    override suspend fun saveAssessmentEvidenceSnapshot(
        snapshot: com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot,
    ) {
        database.attemptTransactionDao().saveAssessmentEvidenceSnapshot(snapshot)
    }

    override suspend fun appendAssessmentEvent(event: AssessmentEventSeedRecord) {
        DatabaseContractValidator.validateAssessmentEvent(event)
        database.immutableLearningFactDao().saveAssessmentEvent(event.toEntity())
    }

    override suspend fun recordAttempt(command: AttemptWriteCommand): AttemptWriteResult {
        val result = database.attemptTransactionDao().recordAttempt(command)
        return AttemptWriteResult(
            submissionId = result.submissionId,
            created = result.created,
            attempt = result.attempt,
            canonicalFingerprint = result.canonicalFingerprint,
            outboxId = result.outbox.outboxId,
        )
    }

    override suspend fun recordReviewAttempt(
        command: ReviewAttemptWriteCommand,
    ): ReviewAttemptWriteResult = database.withWriteTransaction {
        val attempt = this@RoomStudyDatabase.recordAttempt(command.attempt)
        val persistedPracticeUnitId = attempt.attempt.assessmentSnapshot.practiceUnitId
        if (persistedPracticeUnitId != command.practiceUnitId) {
            throw ImmutablePayloadConflictException("review_attempt", attempt.attempt.attemptId)
        }
        val advanceCommand = ReviewSessionAdvanceCommand(
            sessionId = command.sessionId,
            expectedStateVersion = command.expectedStateVersion,
            reviewQueueItemId = command.reviewQueueItemId,
            practiceUnitId = persistedPracticeUnitId,
            attemptId = attempt.attempt.attemptId,
            submissionId = attempt.submissionId,
            presentationId = attempt.attempt.presentationId,
            occurredAtEpochMillis = attempt.attempt.occurredAtEpochMillis,
        )
        DatabaseContractValidator.validateReviewSessionAdvance(advanceCommand)
        val transition = database.reviewPlanTransactionDao().advanceSession(
            command = advanceCommand,
            attemptCreatedInCurrentTransaction = attempt.created,
        )
        val advance = ReviewSessionAdvanceResult(
            created = transition.created,
            session = transition.session.toRecord(),
            receipt = transition.receipt.toRecord(),
        )
        ReviewAttemptWriteResult(attempt = attempt, advance = advance)
    }

    /**
     * PRD-05：attempt 级撤销的原子边界。
     *
     * 一个写事务内完成三件事：
    1. 追加 VOID 更正行 + outbox（即持久回执；同一 correction id 重放返回 created=false）；
    2. 会话指针回退一格（`review_session` 原位更新 + 新 revision 行；不删除旧 revision）；
    3. 把该 attempt 的 review_log 行标记为 `scheduling_eligible=0`（行保留供审计）。
     *
     * 崩溃语义：三件事同事务提交，杀进程要么全有要么全无；重试靠 correction id 幂等。
     */
    override suspend fun undoReviewAttempt(
        command: ReviewAttemptUndoCommand,
    ): ReviewAttemptUndoResult = database.withWriteTransaction {
        val correction = this@RoomStudyDatabase.appendAttemptCorrection(command.correction)
        val rewind = database.reviewPlanTransactionDao().rewindSession(
            command = ReviewSessionRewindCommand(
                sessionId = command.sessionId,
                expectedStateVersion = command.expectedStateVersion,
                attemptId = command.attemptId,
                practiceUnitId = command.practiceUnitId,
                undoneAtEpochMillis = command.undoneAtEpochMillis,
            ),
            applyRewind = correction.created,
        )
        if (correction.created) {
            database.learningDao().markReviewLogSchedulingIneligible(
                learnerId = command.learnerId,
                sourceId = command.attemptId,
            )
        }
        ReviewAttemptUndoResult(
            created = correction.created,
            correction = correction.correction,
            canonicalFingerprint = correction.canonicalFingerprint,
            outboxId = correction.outboxId,
            session = rewind.session,
        )
    }

    override suspend fun recordAnswerReveal(
        command: AnswerRevealWriteCommand,
    ): AnswerRevealWriteResult {
        val result = database.attemptTransactionDao().recordAnswerReveal(command)
        return AnswerRevealWriteResult(
            created = result.created,
            outcome = result.outcome,
            canonicalFingerprint = result.canonicalFingerprint,
            outboxId = result.outbox.outboxId,
        )
    }

    override suspend fun reconcileAnswerRevealOutcomes(
        learnerId: String,
        limit: Int,
    ): List<AnswerRevealWriteResult> = database.attemptTransactionDao()
        .reconcileAnswerRevealOutcomes(learnerId, limit)
        .map { result ->
            AnswerRevealWriteResult(
                created = result.created,
                outcome = result.outcome,
                canonicalFingerprint = result.canonicalFingerprint,
                outboxId = result.outbox.outboxId,
            )
        }

    override suspend fun appendAttemptCorrection(
        correction: AttemptCorrectionRecord,
    ): AttemptCorrectionResult {
        val result = database.attemptTransactionDao().appendCorrection(correction)
        return AttemptCorrectionResult(
            created = result.created,
            correction = result.correction,
            canonicalFingerprint = result.canonicalFingerprint,
            outboxId = result.outbox.outboxId,
        )
    }

    override suspend fun findAttemptPersistence(
        submissionId: String,
    ): AttemptPersistenceRecord? = database.learningDao().findAttemptPersistence(submissionId)

    override suspend fun recordReviewLogEntries(entries: List<ReviewLogEntry>) {
        database.learningDao().recordReviewLog(entries.map(ReviewLogEntry::toEntity))
    }

    override suspend fun readReviewLogSamples(learnerId: String, limit: Int): List<ReviewLogSampleRecord> =
        database.learningDao().readReviewLogSamples(learnerId, limit)
            .map(ReviewLogSampleProjection::toRecord)

    override suspend fun readLastReviewLogAt(
        learnerId: String,
        practiceUnitId: String,
        sourceKind: String,
    ): Long? = database.learningDao().readLastReviewLogAt(learnerId, practiceUnitId, sourceKind)

    override suspend fun findLastReviewLogRow(
        learnerId: String,
        practiceUnitId: String,
    ): PersistedReviewLogLast? = database.learningDao()
        .findLastReviewLogRow(learnerId, practiceUnitId)
        ?.let { PersistedReviewLogLast(it.lastRating, it.lastReviewedAtUtc) }

    override suspend fun findAttemptAdvanceProof(
        attemptId: String,
    ): AttemptAdvanceProofRecord? = database.learningDao().findAttemptAdvanceProof(attemptId)

    override suspend fun markRelationsStaleForRevision(
        problemRevisionId: String,
        updatedAtEpochMillis: Long,
    ): Int {
        require(problemRevisionId.isNotBlank()) { "problemRevisionId must not be blank" }
        require(updatedAtEpochMillis >= 0) { "updatedAtEpochMillis cannot be negative" }
        return database.problemDao().markRelationsStaleForRevision(
            problemRevisionId = problemRevisionId,
            staleStatus = StudyDbValue.RelationStatus.STALE,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    override suspend fun loadProjectionBatch(
        projectionName: String,
        learnerId: String,
        limit: Int,
    ): ProjectionBatch = database.projectionTransactionDao()
        .loadProjectionBatch(projectionName, learnerId, limit)

    override suspend fun recordChatEvidence(
        entries: List<com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity>,
    ): com.tingyun.smartmistakebook.core.database.dao.ChatEvidenceWriteReceipt =
        database.chatEvidenceDao().insertAsLedgerEvents(entries)

    override suspend fun readChatEvidenceByLearner(learnerId: String): List<com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity> =
        database.chatEvidenceDao().readByLearner(learnerId)

    override suspend fun readChatEvidenceByConversation(conversationId: String): List<com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity> =
        database.chatEvidenceDao().readByConversation(conversationId)

    override suspend fun lastAcceptedChatEvidenceAtForKc(
        learnerId: String,
        knowledgeNodeId: String,
        sourceKind: String,
    ): Long? = database.chatEvidenceDao().lastAcceptedAtForKc(learnerId, knowledgeNodeId, sourceKind)

    override suspend fun countAcceptedChatEvidenceSince(learnerId: String, sinceEpochMillis: Long): Int =
        database.chatEvidenceDao().countAcceptedSince(learnerId, sinceEpochMillis)

    override suspend fun countAcceptedChatEvidenceInConversation(conversationId: String): Int =
        database.chatEvidenceDao().countAcceptedInConversation(conversationId)

    override suspend fun countRejectedChatEvidenceByReason(learnerId: String): List<com.tingyun.smartmistakebook.core.database.dao.RejectedReasonCountRow> =
        database.chatEvidenceDao().countRejectedByReason(learnerId)

    override suspend fun countAcceptedChatEvidencePerHour(learnerId: String, sinceEpochMillis: Long): List<com.tingyun.smartmistakebook.core.database.dao.HourlyAcceptedCountRow> =
        database.chatEvidenceDao().countAcceptedPerHour(learnerId, sinceEpochMillis)

    override suspend fun loadLearningLedger(learnerId: String): LearningLedgerRead =
        database.projectionTransactionDao().loadLearningLedger(learnerId)

    override suspend fun readCurrentLearnerSnapshot(
        projectionName: String,
        learnerId: String,
    ): PersistedLearnerSnapshot? = database.projectionTransactionDao()
        .readCurrentSnapshot(projectionName, learnerId)

    override suspend fun commitProjection(
        commit: ProjectionCommit,
    ): PersistedLearnerSnapshot = database.projectionTransactionDao().commitProjection(commit)

    override suspend fun archiveProjectionSnapshot(record: ProjectionArchiveRecord) =
        database.projectionTransactionDao().archiveProjectionSnapshot(record)

    override suspend fun readLatestArchivedProjection(
        projectionName: String,
        learnerId: String,
    ): PersistedProjectionArchive? =
        projectionArchive.readLatestArchivedProjection(projectionName, learnerId)

    override suspend fun restoreArchivedProjection(
        projectionName: String,
        learnerId: String,
        expectedProjectorVersion: String,
        restoredAtEpochMillis: Long,
    ): PersistedLearnerSnapshot = projectionArchive.restoreArchivedProjection(
        projectionName = projectionName,
        learnerId = learnerId,
        expectedProjectorVersion = expectedProjectorVersion,
        restoredAtEpochMillis = restoredAtEpochMillis,
    )

    override suspend fun saveReviewPlan(bundle: ReviewPlanBundle) {
        DatabaseContractValidator.validateReviewBundle(bundle)
        database.reviewPlanTransactionDao().savePlan(
            plan = bundle.plan.toEntity(),
            queue = bundle.queue.map(ReviewQueueItemRecord::toEntity),
            knowledgeNodes = bundle.queue.flatMap(ReviewQueueItemRecord::toKnowledgeNodeEntities),
            reasons = bundle.queue.flatMap(ReviewQueueItemRecord::toReasonEntities),
            activeSession = bundle.activeSession?.toEntity(),
            isCurrent = bundle.isCurrent,
        )
    }

    override suspend fun saveReviewSession(session: ReviewSessionRecord) {
        DatabaseContractValidator.validateReviewSessionCreation(session)
        database.reviewPlanTransactionDao().saveSession(session.toEntity())
    }

    @Deprecated("New review transitions must use recordReviewAttempt")
    override suspend fun advanceReviewSession(
        command: ReviewSessionAdvanceCommand,
    ): ReviewSessionAdvanceResult {
        DatabaseContractValidator.validateReviewSessionAdvance(command)
        val result = database.reviewPlanTransactionDao().advanceSession(
            command = command,
            attemptCreatedInCurrentTransaction = false,
        )
        return ReviewSessionAdvanceResult(
            created = result.created,
            session = result.session.toRecord(),
            receipt = result.receipt.toRecord(),
        )
    }

    override suspend fun readAssessmentSnapshotP0(
        assessmentItemSnapshotId: String,
    ): AssessmentItemSnapshotSeedRecord? = database.immutableLearningFactDao()
        .findAssessmentItem(assessmentItemSnapshotId)
        ?.toRecord()

    override suspend fun readAttemptP0(attemptId: String): PersistedAttemptP0? =
        database.learningDao().readAttempt(attemptId)

    override suspend fun readCorrectionP0(correctionId: String): PersistedCorrectionP0? =
        database.learningDao().readCorrection(correctionId)

    override suspend fun readAnswerRevealP0(outcomeId: String): PersistedAnswerRevealP0? =
        database.learningDao().readAnswerReveal(outcomeId)

    override suspend fun findAnswerRevealForPresentation(
        learnerId: String,
        presentationId: String,
    ): PersistedAnswerRevealFact? = database.learningDao().findAnswerRevealFact(learnerId, presentationId)

    override fun observeConfirmedProblemOrganization(
        problemId: String,
        problemRevisionId: String,
    ): Flow<ConfirmedProblemOrganizationRecord> =
        problemOrganization.observe(problemId, problemRevisionId)

    override suspend fun confirmProblemOrganization(
        command: ConfirmProblemOrganizationCommand,
        learnerId: String,
    ): ConfirmProblemOrganizationResult = problemOrganization.confirm(command, learnerId)

    override suspend fun recordBindingAuditSample(
        command: RecordBindingAuditSampleCommand,
        maxPerSubjectWeek: Int,
    ): Boolean = database.bindingAuditSampleDao().record(command, maxPerSubjectWeek)

    override fun observeBindingAuditSamples(
        status: String?,
    ): Flow<List<BindingAuditSampleRow>> = database.bindingAuditSampleDao().observe(status)

    override suspend fun readBindingAuditSamples(status: String?): List<BindingAuditSampleRow> =
        database.bindingAuditSampleDao().read(status)

    override suspend fun reviewBindingAuditSample(
        sampleId: String,
        verdict: String,
        reviewedAtEpochMillis: Long,
    ): BindingAuditSampleRow? =
        database.bindingAuditSampleDao().review(sampleId, verdict, reviewedAtEpochMillis)

    // ---- D-02（批次 30）：视觉身份不可变 + STAGING→LIVE + 正文/trace/link 同事务 ----

    override suspend fun stageTutorVisualSnapshot(
        command: StageTutorVisualSnapshotCommand,
    ): TutorVisualSnapshotRecord = database.withWriteTransaction {
        val dao = database.tutorVisualDao()
        ensureTutorVisualProgram(dao, command.program)
        val snapshot = ensureTutorVisualSnapshot(dao, command.snapshot, command.ownerRequestId)
        // 与 plan 的"淘汰后净值"口径对齐（批次 32 复核修复）：事务前的 plan 已把可回收行
        // 从预算里扣除，若这里不执行同一份淘汰集，就会在"总量超、净值不超"时把本可提交的
        // stage 拒掉（与契约 §8.1 的方向相反）。SQL 仍重查 pin/kind/status/租约；stage 不
        // demote——新行还是 STAGING，不能抢旧 LATEST 的角色。
        if (command.evictCheckpointSnapshotIds.isNotEmpty()) {
            dao.evictUnpinnedCheckpoints(
                snapshotIds = command.evictCheckpointSnapshotIds,
                latestKind = TutorVisualCheckpointKind.LATEST,
                stagingStatus = TutorVisualStatus.STAGING,
                committingOwnerRequestId = command.ownerRequestId,
                leaseExpiredBeforeEpochMillis = command.stagingLeaseExpiredBeforeEpochMillis,
            )
        }
        // 事务内预算重判（批次 32）：已在事务外 `planCheckpoint` 判过一次，但那次读到的总量
        // 可能已被并发提交抬高。这里在**写完本行之后**重读，超了就让 Room 回滚本行。
        // 既有 LIVE 行是幂等回放（本次不新增任何字节），不重判，避免把"重放已存在的图"拒掉。
        if (snapshot.status != TutorVisualStatus.LIVE) {
            enforceVisualCheckpointBudget(
                dao = dao,
                programId = command.program.programId,
                perProgramBudgetBytes = command.perProgramBudgetBytes,
                globalBudgetBytes = command.globalBudgetBytes,
            )
        }
        snapshot.toRecord()
    }

    /**
     * S4 的**唯一**落点：program/snapshot 的复用或创建、assistant 消息（正文/trace）、
     * `STAGING → LIVE`、link 全部在 `withWriteTransaction` 内完成。任何一步抛错 →
     * Room 回滚整个事务（消息、link、program/snapshot 都不留）。
     *
     * 顺序是"先确保快照 = LIVE，再插 link"：link 的 SQL 门只认 LIVE（与设计的
     * `INSERT … SELECT … WHERE status='LIVE'` 同一道结构门），把提升放在 link 之前只是让
     * 这道门在同事务内可满足，不是放宽它。
     */
    override suspend fun commitTutorTurnWithVisual(
        command: CommitTutorTurnWithVisualCommand,
    ): TutorVisualTurnCommitRecord = database.withWriteTransaction {
        val dao = database.tutorVisualDao()
        ensureTutorVisualProgram(dao, command.program)
        var snapshot = ensureTutorVisualSnapshot(dao, command.snapshot, command.ownerRequestId)
        // 幂等回放（行本来就是 LIVE）不新增字节，不参与本次重判；否则见事务末尾的预算门。
        val snapshotWasAlreadyLive = snapshot.status == TutorVisualStatus.LIVE

        // 正文 + thinking + tool trace 是同一条消息行；它和下面的提升/link 共处一个事务。
        val message = database.tutorConversationDao().appendAssistantMessage(command.message)

        if (snapshot.status != TutorVisualStatus.LIVE) {
            val promotedAt = maxOf(
                snapshot.createdAtEpochMillis,
                command.message.createdAtEpochMillis,
            )
            val changed = dao.promoteSnapshot(
                snapshotId = snapshot.snapshotId,
                stagingStatus = TutorVisualStatus.STAGING,
                liveStatus = TutorVisualStatus.LIVE,
                promotedAtEpochMillis = promotedAt,
            )
            snapshot = if (changed == 1) {
                snapshot.copy(
                    status = TutorVisualStatus.LIVE,
                    ownerRequestId = null,
                    promotedAtEpochMillis = promotedAt,
                )
            } else {
                // 被另一个提交先提升了：LIVE 是终态，复用它；仍是 STAGING 才是冲突。
                val raced = dao.findSnapshot(snapshot.snapshotId)
                    ?: throw ImmutablePayloadConflictException(
                        "tutor_visual_snapshot",
                        snapshot.snapshotId,
                    )
                if (raced.status != TutorVisualStatus.LIVE) {
                    throw ImmutablePayloadConflictException(
                        "tutor_visual_snapshot",
                        snapshot.snapshotId,
                    )
                }
                raced
            }
        }

        val link = dao.linkMessageVisual(
            messageId = message.messageId,
            ordinal = command.visualOrdinal,
            programId = command.program.programId,
            snapshotId = snapshot.snapshotId,
            uiStateJson = command.uiStateJson,
            formDigest = command.formDigest,
            nowEpochMillis = message.createdAtEpochMillis,
        )

        // checkpoint 保留策略的同事务落点（批次 30 / D-05，批次 31 收紧，批次 32 加租约）：
        // 先降级旧 LATEST，再淘汰"仍未固定、非 INITIAL、仍为 STAGING 且租约已过期/属于本
        // owner"的中间态。两条 SQL 都在事务内，任一步失败整体回滚；淘汰 SQL 在事务内**重查**
        // pin/kind/status/租约（策略在事务外算，引用、可见性与 owner 都可能并发变化）。
        // LIVE 是用户已见事实，即使未被引用也不当缓存删。
        if (command.demoteCheckpointSnapshotIds.isNotEmpty()) {
            dao.demoteLatestCheckpoints(
                programId = command.program.programId,
                snapshotIds = command.demoteCheckpointSnapshotIds,
                latestKind = TutorVisualCheckpointKind.LATEST,
            )
        }
        if (command.evictCheckpointSnapshotIds.isNotEmpty()) {
            dao.evictUnpinnedCheckpoints(
                snapshotIds = command.evictCheckpointSnapshotIds,
                latestKind = TutorVisualCheckpointKind.LATEST,
                stagingStatus = TutorVisualStatus.STAGING,
                committingOwnerRequestId = command.ownerRequestId,
                leaseExpiredBeforeEpochMillis = command.stagingLeaseExpiredBeforeEpochMillis,
            )
        }

        // 事务内预算重判（批次 32）：必须放在 demote/evict 之后、以事务内刚写完的字节总量为准。
        // 两个并发提交各自在事务外读到"没超"的旧总量时，第二个事务在这里看到第一个事务的
        // 结果 → 超限即抛，Room 回滚消息/link/program/snapshot，杜绝"双过"。
        if (!snapshotWasAlreadyLive) {
            enforceVisualCheckpointBudget(
                dao = dao,
                programId = command.program.programId,
                perProgramBudgetBytes = command.perProgramBudgetBytes,
                globalBudgetBytes = command.globalBudgetBytes,
            )
        }

        TutorVisualTurnCommitRecord(
            message = message,
            snapshot = snapshot.toRecord(),
            link = link.toRecord(),
        )
    }

    override suspend fun repointTutorMessageVisual(
        command: RepointTutorMessageVisualCommand,
    ): Boolean = database.tutorVisualDao().repointMessageVisual(
        messageId = command.messageId,
        ordinal = command.ordinal,
        expectedRevision = command.expectedRevision,
        nextSnapshotId = command.nextSnapshotId,
        nowEpochMillis = command.nowEpochMillis,
        liveStatus = TutorVisualStatus.LIVE,
    ) == 1

    override suspend fun reconcileStaleTutorVisualStaging(staleBeforeEpochMillis: Long): Int {
        require(staleBeforeEpochMillis >= 0L) {
            "A visual staging reconcile threshold must not be negative"
        }
        return database.withWriteTransaction {
            database.tutorVisualDao().deleteStaleStagingSnapshots(
                stagingStatus = TutorVisualStatus.STAGING,
                staleBeforeEpochMillis = staleBeforeEpochMillis,
            )
        }
    }

    override suspend fun readTutorVisualProgram(programId: String): TutorVisualProgramRecord? {
        require(programId.isNotBlank()) { "A visual program id must not be blank" }
        return database.tutorVisualDao().findProgram(programId)?.toRecord()
    }

    override suspend fun readTutorVisualSnapshot(snapshotId: String): TutorVisualSnapshotRecord? {
        require(snapshotId.isNotBlank()) { "A visual snapshot id must not be blank" }
        return database.tutorVisualDao().findSnapshot(snapshotId)?.toRecord()
    }

    override suspend fun readTutorMessageVisual(
        messageId: String,
        ordinal: Int,
    ): TutorMessageVisualRecord? {
        require(messageId.isNotBlank()) { "A visual message id must not be blank" }
        require(ordinal >= 0) { "A visual ordinal must not be negative" }
        return database.tutorVisualDao().findMessageVisual(messageId, ordinal)?.toRecord()
    }

    override suspend fun countTutorVisualProgramsForTest(programId: String): Int {
        require(programId.isNotBlank()) { "A visual program id must not be blank" }
        return database.tutorVisualDao().countPrograms(programId)
    }

    override suspend fun readVisualCheckpointSummaries(
        programId: String,
    ): List<TutorVisualCheckpointSummary> {
        require(programId.isNotBlank()) { "A visual program id must not be blank" }
        return database.tutorVisualDao().checkpointSummaries(programId).map { row ->
            TutorVisualCheckpointSummary(
                snapshotId = row.snapshotId,
                checkpointKind = row.checkpointKind,
                status = row.status,
                pinned = row.pinned != 0,
                storedBytes = row.storedBytes,
            )
        }
    }

    override suspend fun readGlobalVisualCheckpointStoredBytes(): Long =
        database.tutorVisualDao().totalCheckpointStoredBytes()

    /**
     * **写入事务内**的 checkpoint 预算重判（批次 32 任务 7）。
     *
     * 它消灭的具体失败：预算只在 `TutorVisualCommitRepository.planCheckpoint` 里、事务**之前**
     * 判。两次并发提交各自读到未被对方抬高的总量，都能通过；随后两次写入都成功，最终占用
     * 超过 4 MiB / 32 MiB 且没有任何门拦。进程内 `Mutex` 只挡同一 repository 实例，第二个
     * 实例/进程仍可双过。
     *
     * 这里在事务内、**在本事务已经写入并执行完 demote/evict 之后**读取：
     * - 本 program 的字节总量（[TutorVisualDao.programCheckpointStoredBytes]）；
     * - 全库字节总量（[TutorVisualDao.totalCheckpointStoredBytes]）。
     *
     * SQLite 的写事务是串行的：后开始的事务要么看到前一个事务已提交的行，要么在写锁竞争时
     * 失败/重试，不存在"两边都读到旧总量并都提交"。超限抛
     * [TutorVisualCheckpointBudgetExceededException]，Room 回滚本事务的全部写入。
     *
     * 判定顺序与 `VisualCheckpointRetention.plan` 一致：先 per-program，再 global。
     */
    private suspend fun enforceVisualCheckpointBudget(
        dao: TutorVisualDao,
        programId: String,
        perProgramBudgetBytes: Long,
        globalBudgetBytes: Long,
    ) {
        val programBytes = dao.programCheckpointStoredBytes(programId)
        if (programBytes > perProgramBudgetBytes) {
            throw TutorVisualCheckpointBudgetExceededException(
                axis = TutorVisualCheckpointBudgetAxis.PER_PROGRAM,
                message = "The visual checkpoint cache for this program holds $programBytes bytes, " +
                    "above the per-program budget $perProgramBudgetBytes",
            )
        }
        val globalBytes = dao.totalCheckpointStoredBytes()
        if (globalBytes > globalBudgetBytes) {
            throw TutorVisualCheckpointBudgetExceededException(
                axis = TutorVisualCheckpointBudgetAxis.GLOBAL,
                message = "The visual checkpoint cache holds $globalBytes bytes across all programs, " +
                    "above the global budget $globalBudgetBytes",
            )
        }
    }

    /**
     * 同 id 不同载荷 ⇒ 冲突；同 id 同载荷 ⇒ 幂等成功（`created_at` 是元数据，不参与比较）。
     *
     * 写库前**重新派生身份**并比对：`visualId = programId` 不是调用方可以自己声明的值——
     * 一个与内容不符的 id 在这里是 `IllegalArgumentException`，不会落成"身份与内容脱钩"的行。
     */
    private suspend fun ensureTutorVisualProgram(
        dao: TutorVisualDao,
        write: TutorVisualProgramWrite,
    ) {
        val derivedProgramId = TutorVisualIdentity.deriveProgramId(
            sourceJs = write.sourceJs,
            initialStateJson = write.initialStateJson,
            basisFingerprint = write.basisFingerprint,
            apiVersion = write.apiVersion,
            sdkVersion = write.sdkVersion,
            role = write.role,
        )
        require(write.programId == derivedProgramId) {
            "A visual program id must be derived from its content (visualId = programId)"
        }
        dao.insertProgram(write.toEntity())
        val stored = dao.findProgram(write.programId)
            ?: throw ImmutablePayloadConflictException("tutor_visual_program", write.programId)
        if (!TutorVisualWriteChecks.sameProgramPayload(stored, write)) {
            throw ImmutablePayloadConflictException("tutor_visual_program", write.programId)
        }
    }

    /**
     * S3 的重复提交分支（研究复核高危：LIVE 命中/同 owner/他 owner/无行 四分支）：
     * 1. 同内容快照已 **LIVE** → 直接复用（重复提交第二次返回既有 LIVE 快照）；
     * 2. 同内容快照是 **STAGING 且 owner 相同** → 复用；
     * 3. 同内容快照是 **STAGING 且 owner 不同** → CAS 转租约，0 行命中即冲突；
     * 4. 无既有行 → 插入（`INSERT OR IGNORE` + 回读；并发输家回读赢家行）。
     *
     * 唯一键覆盖 `(program, state, draw, renderer, font)`（schema 71 / S1），所以复用前还要
     * 逐列比对内容载荷：同键不同 scene/视口/预览绝不能静默复用；而**版本不同的再提交根本不会
     * 命中这个查重**——它是一行新快照。
     *
     * **写库前重新派生 `snapshotId` 并比对**（批次 31）：与 [ensureTutorVisualProgram] 同形。
     * 改前这里是"调用方给什么 id 就落什么 id"——一个与内容不符的 id 会落成"身份与内容脱钩"
     * 的行，而 program 层的同类输入早已被 `require` 拒绝；同一模块两条身份规则不一致本身就是
     * 缺陷。`require`（而非冲突异常）沿用 program 层分流：调用方构造错误 = 参数错误，
     * 并发/重复 = `ImmutablePayloadConflictException`。
     *
     * **digest 列本身也必须与 JSON 内容一致**（批次 31 复核修复 state/draw，批次 33 补 scene）：
     * 只比对
     * `snapshotId == derive(programId, stateSha256, drawCommandsSha256, renderer, font)` 是
     * 循环论证——那两个 digest 是调用方传入的；若不同时校验
     * `stateSha256 == sha256Hex(stateJson)` 与 `drawCommandsSha256 == sha256Hex(drawCommandsJson)`，
     * 直接经 `TutorVisualPort.stage/commit` 的写者仍可落"内容与 digest/身份脱钩"的行；同一段
     * 内容配不同 digest 字符串还会被唯一键当成不同内容而重复入库。生产调用点
     * （`TutorVisualCommitRepository`）本就从 JSON 计算 digest，因此这条门挡的是直接调 DB
     * 端口的写者——正是 DB 契约的职责。
     *
     * `sceneSha256` 是**第三条**轴，且处境与前两条不同：scene 既不进身份派生式，也不进唯一键
     * （schema 71 的键是 program+state+draw+renderer+font），所以"id 自洽"这一层根本覆盖不到
     * 它——一个与 `sceneJson` 不符的 scene digest 会被原样写进**不可变**行（本类没有 UPDATE
     * 路径），而 [TutorVisualWriteChecks.sameSnapshotPayload] 逐列比对 scene 时只会把这个错误
     * 当作"内容"，之后既发现不了也改不掉。`scene_sha256` 当前也**没有读取方**（冷恢复的
     * `VisualSnapshotRestoreRecord` 连 scene 字段都不携带；`scene_json` 只被预算按字节统计），
     * 正因如此更该在写入端绑死：一旦将来有读取方按 digest 判断"这份 scene 是否就是我编译过的
     * 那份"，错误值会变成静默的错误答案。三条 digest 的校验位置刻意保持一致（都在写库前的
     * 复派生门里，而不是各 `init`），这样负向用例才能构造出"载荷合法、内容与 digest 脱钩"的
     * 输入来打这道门。
     */
    private suspend fun ensureTutorVisualSnapshot(
        dao: TutorVisualDao,
        write: TutorVisualSnapshotWrite,
        ownerRequestId: String,
    ): TutorVisualSnapshotEntity {
        require(write.stateSha256 == TutorVisualIdentity.sha256Hex(write.stateJson)) {
            "A visual snapshot state digest must match its persisted state JSON"
        }
        require(write.drawCommandsSha256 == TutorVisualIdentity.sha256Hex(write.drawCommandsJson)) {
            "A visual snapshot draw-commands digest must match its persisted draw commands JSON"
        }
        require(write.sceneSha256 == TutorVisualIdentity.sha256Hex(write.sceneJson)) {
            "A visual snapshot scene digest must match its persisted scene JSON"
        }
        val derivedSnapshotId = TutorVisualIdentity.deriveSnapshotId(
            programId = write.programId,
            stateSha256 = write.stateSha256,
            drawCommandsSha256 = write.drawCommandsSha256,
            rendererVersion = write.rendererVersion,
            fontManifestVersion = write.fontManifestVersion,
        )
        require(write.snapshotId == derivedSnapshotId) {
            "A visual snapshot id must be derived from its program, content and render versions"
        }
        val existing = dao.findSnapshotByContent(
            programId = write.programId,
            stateSha256 = write.stateSha256,
            drawCommandsSha256 = write.drawCommandsSha256,
            rendererVersion = write.rendererVersion,
            fontManifestVersion = write.fontManifestVersion,
        )
        if (existing != null) {
            requireSameVisualSnapshotPayload(existing, write)
            return claimStagingOwner(dao, existing, ownerRequestId)
        }

        dao.insertSnapshot(write.toEntity(ownerRequestId))
        val stored = dao.findSnapshotByContent(
            programId = write.programId,
            stateSha256 = write.stateSha256,
            drawCommandsSha256 = write.drawCommandsSha256,
            rendererVersion = write.rendererVersion,
            fontManifestVersion = write.fontManifestVersion,
        ) ?: dao.findSnapshot(write.snapshotId)
            ?: throw ImmutablePayloadConflictException("tutor_visual_snapshot", write.snapshotId)
        requireSameVisualSnapshotPayload(stored, write)
        return claimStagingOwner(dao, stored, ownerRequestId)
    }

    private suspend fun claimStagingOwner(
        dao: TutorVisualDao,
        snapshot: TutorVisualSnapshotEntity,
        ownerRequestId: String,
    ): TutorVisualSnapshotEntity {
        if (snapshot.status == TutorVisualStatus.LIVE) return snapshot
        if (snapshot.ownerRequestId == ownerRequestId) return snapshot
        val changed = dao.reassignStagingOwner(
            snapshotId = snapshot.snapshotId,
            stagingStatus = TutorVisualStatus.STAGING,
            expectedOwnerRequestId = snapshot.ownerRequestId,
            nextOwnerRequestId = ownerRequestId,
        )
        if (changed == 1) return snapshot.copy(ownerRequestId = ownerRequestId)
        // CAS 输了：只有"被提升为 LIVE"或"已经转给本 owner"可以继续，其余按冲突放弃。
        val raced = dao.findSnapshot(snapshot.snapshotId)
            ?: throw ImmutablePayloadConflictException("tutor_visual_snapshot", snapshot.snapshotId)
        return when {
            raced.status == TutorVisualStatus.LIVE -> raced
            raced.ownerRequestId == ownerRequestId -> raced
            else -> throw ImmutablePayloadConflictException("tutor_visual_snapshot", snapshot.snapshotId)
        }
    }

    private fun requireSameVisualSnapshotPayload(
        entity: TutorVisualSnapshotEntity,
        write: TutorVisualSnapshotWrite,
    ) {
        if (!TutorVisualWriteChecks.sameSnapshotPayload(entity, write)) {
            throw ImmutablePayloadConflictException("tutor_visual_snapshot", entity.snapshotId)
        }
    }

    override fun close() = database.close()

    override suspend fun archiveErrorBookEntry(entryId: String, at: Long): Boolean =
        database.withWriteTransaction {
            val row = database.mistakeDetailDao().readEntryRevision(entryId) ?: return@withWriteTransaction false
            val changed = database.mistakeDetailDao().setEntryStatus(entryId, StudyDbValue.ErrorBookStatus.ARCHIVED, at)
            if (changed) {
                // Remove from FTS search so archived mistakes stop appearing in search.
                database.libraryFtsSearchDao().deleteContent(row.currentRevisionId)
                database.libraryFtsSearchDao().clearOutboxFor(row.currentRevisionId)
            }
            changed
        }

    override suspend fun restoreErrorBookEntry(entryId: String, at: Long): Boolean =
        database.withWriteTransaction {
            val row = database.mistakeDetailDao().readEntryRevision(entryId) ?: return@withWriteTransaction false
            val changed = database.mistakeDetailDao().setEntryStatus(entryId, StudyDbValue.ErrorBookStatus.ACTIVE, at)
            if (changed) {
                // Rebuild the FTS row so the restored mistake is searchable again.
                librarySearch.reindexRevision(row.currentRevisionId)
            }
            changed
        }

    override fun observeArchivedErrorBookEntries(): Flow<List<ArchivedEntrySummaryRow>> =
        database.mistakeDetailDao().archivedEntries()

    // PRD-03: the controlled revision-edit command. Kept as the last member so the in-flight
    // KDoc-only hunk above stays untouched.
    override suspend fun reviseCommittedProblem(
        command: ReviseCommittedProblemCommand,
    ): ReviseCommittedProblemResult = database.mistakeDetailDao().reviseCommittedProblem(command)
}


