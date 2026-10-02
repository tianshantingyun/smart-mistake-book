package com.tingyun.smartmistakebook.core.database


/**
 * D-M M1 测试夹具（androidTest 源集，**不是**生产接缝）：`seedFixture` 端口随 fixture
 * 系统退场后，本模块的仪器化测试用**直写 DAO** 的方式落同一批行。
 *
 * 语义与旧 `FixtureSeedDao.seed` 的差别（刻意保留的差别，不静默）：
 * - 纯 INSERT IGNORE：重复播种是 no-op，不再做"同 id 不同载荷即抛"的不可变回放校验
 *   （那条契约随 fixture 系统删除，相应的用例已随之删除/改钉真实写入路径）；
 * - review plan/session 走公开写路径 `ReviewDao.savePlan`/`saveSession`（含队列项展开与
 *   revision 行），不再由夹具手写；
 * - 缺失外键（例如 revision 不存在）仍由 SQLite 外键约束拒绝——负向用例继续有效。
 */
internal suspend fun StudyDatabasePort.seedStudyFacts(bundle: StudySeedBundle): SeedResult {
    val room = this as? RoomStudyDatabase
        ?: error("seedStudyFacts requires the Room-backed StudyDatabasePort implementation")
    DatabaseContractValidator.validateSeedBundle(bundle)
    var problemCount = 0
    var entryCount = 0
    val problemDao = room.database.problemDao()
    bundle.problems.forEach { record ->
        if (problemDao.insertProblems(listOf(record.toEntity())).single() != -1L) problemCount++
    }
    bundle.revisions.forEach { record ->
        problemDao.insertRevisions(listOf(record.toEntity()))
    }
    bundle.practiceUnits.forEach { record ->
        problemDao.insertPracticeUnits(listOf(record.toEntity()))
    }
    bundle.errorBookEntries.forEach { record ->
        if (problemDao.insertErrorBookEntries(listOf(record.toEntity())).single() != -1L) entryCount++
    }
    bundle.knowledgeNodes.forEach { record ->
        problemDao.insertKnowledgeNodes(listOf(record.toEntity()))
    }
    bundle.knowledgeBindings.forEach { record ->
        problemDao.insertKnowledgeBindings(listOf(record.toEntity()))
    }
    bundle.relations.forEach { record ->
        problemDao.insertRelations(listOf(record.toEntity()))
    }
    bundle.assessmentItems.forEach { record ->
        saveAssessmentItemSnapshot(record)
    }
    bundle.assessmentEvents.forEach { record ->
        appendAssessmentEvent(record)
    }
    val reviewDao = room.database.reviewPlanTransactionDao()
    bundle.reviewPlans.forEach { plan ->
        val queue = bundle.reviewQueueItems
            .filter { it.reviewPlanId == plan.reviewPlanId }
            .sortedBy(ReviewQueueItemRecord::ordinal)
        val sessions = bundle.reviewSessions.filter { it.reviewPlanId == plan.reviewPlanId }
        val activeSession = sessions.singleOrNull {
            it.status == StudyDbValue.ReviewStatus.IN_PROGRESS
        }
        reviewDao.savePlan(
            plan = plan.toEntity(),
            queue = queue.map(ReviewQueueItemRecord::toEntity),
            knowledgeNodes = queue.flatMap(ReviewQueueItemRecord::toKnowledgeNodeEntities),
            reasons = queue.flatMap(ReviewQueueItemRecord::toReasonEntities),
            activeSession = activeSession?.toEntity(),
            isCurrent = false,
        )
        sessions.forEach { session ->
            reviewDao.saveSession(session.toEntity())
        }
    }
    return SeedResult(
        insertedProblemCount = problemCount,
        insertedErrorBookEntryCount = entryCount,
    )
}

/**
 * 故意种下"一个 learner 两条 IN_PROGRESS 会话"的 legacy 损坏态：公开写路径
 * （`ReviewDao.saveSession` → `requireInitialSession`）会拒绝它，而这正是
 * `legacyMultipleActiveSessionsFailClosedWithoutAdvancingEither` 要验证的读路径前提。
 * 直写 review_session 行（不带 revision 行——被测路径在触碰 revision 之前就该 fail-closed）。
 */
internal suspend fun RoomStudyDatabase.seedLegacyActiveReviewSessions(
    sessions: List<ReviewSessionRecord>,
) {
    database.withRawConnection(isReadOnly = false) { connection ->
        sessions.forEach { session ->
            connection.usePrepared(
                """
                INSERT OR IGNORE INTO review_session (
                    review_session_id, review_plan_id, status, active_session_key,
                    started_at_epoch_millis, last_active_at_epoch_millis,
                    completed_at_epoch_millis, current_ordinal, time_budget_seconds,
                    projection_checkpoint, state_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ) { statement ->
                statement.bindText(1, session.reviewSessionId)
                statement.bindText(2, session.reviewPlanId)
                statement.bindText(3, session.status)
                val activeSessionKey = session.reviewPlanId
                    .takeIf { session.status == StudyDbValue.ReviewStatus.IN_PROGRESS }
                if (activeSessionKey != null) {
                    statement.bindText(4, activeSessionKey)
                } else {
                    statement.bindNull(4)
                }
                statement.bindLong(5, session.startedAtEpochMillis)
                statement.bindLong(6, session.lastActiveAtEpochMillis)
                session.completedAtEpochMillis?.let { statement.bindLong(7, it) }
                    ?: statement.bindNull(7)
                statement.bindLong(8, session.currentOrdinal.toLong())
                statement.bindLong(9, session.timeBudgetSeconds.toLong())
                statement.bindLong(10, session.projectionCheckpoint)
                statement.bindLong(11, session.stateVersion)
                statement.step()
            }
        }
    }
}
