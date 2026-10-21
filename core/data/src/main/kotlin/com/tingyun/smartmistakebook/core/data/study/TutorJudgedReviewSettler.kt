package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.database.ReviewAttemptWriteCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorTurnResponseRecord
import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import com.tingyun.smartmistakebook.core.database.port.PracticeUnitKnowledgeBindingRecord
import com.tingyun.smartmistakebook.core.domain.LogDurationModel
import com.tingyun.smartmistakebook.core.domain.TutorJudgedReviewSettlement
import com.tingyun.smartmistakebook.core.domain.TutorJudgedReviewSettlementResult
import com.tingyun.smartmistakebook.core.domain.TutorJudgedReviewSettlementStatus
import com.tingyun.smartmistakebook.core.domain.tutorSessionObjectiveRecord
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.AttemptSubmittedResponse
import com.tingyun.smartmistakebook.core.database.AttemptWriteCommand
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LocalModelJudgedContract
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import kotlinx.coroutines.flow.first

/**
 * 讲题判定的题目级结算（第2条：复习作答不再由学生自评）。
 *
 * 判定合成顺序（行为证据胜出，`f506ff7a` 的既有原则）：
 * 1. 本轮有本地核对的检查题作答 → **本地判定就是判定**：一条答错即判错（模型口头说对不作数）；
 * 2. 没有检查题作答 → 取该会话最近一条**通过门控的**模型判词方向（开放作答只能靠它）。
 *    AUD-11 ①（2026-10-16 裁定）：模型判词还必须在**本题 KC 集**（`MistakeRecord.knowledgeNodeIds`）
 *    上有锚定的 accepted 证据——前置/无关节点的证据只作观察，不再有权翻转整题；只有外部节点
 *    证据时按第 3 条走 `NO_VERDICT`（保持阻塞、可见），而不是判对（AUD-11 报告 §1.2 实测的
 *    半权 0.075 证据被放大成 0.5 的 6.67× 跨粒度放大即由这条路径产生）。
 *    **KD-44（2026-10-17 裁定：后台自动解决、零用户参与）补齐「空 KC 集」这一档**：诚实未分类的题
 *    在可见 KC 目录里读不到自己那条伪绑定（`ProblemDao` 的 KC 集要求 KNOWLEDGE 分类行，
 *    `sourceType=PSEUDO_FALLBACK` 的绑定没有），锚定集因此恒为空、判词被整条丢弃 → 复习项
 *    **永久阻塞**；现在锚定集改用该题 revision 的**伪绑定**节点（`pseudo:<科目>`，见
 *    [pseudoAnchorBinding]），判词照常结算、归属指向那条伪绑定行、队列照常推进。
 *    模型写在别的真实节点上的证据仍不构成判词（跨粒度不放行的口径不变）；
 * 3. 两样都没有（含空 KC 集题连一条可锚定的模型证据都没有）→ 不写任何证据、不推进队列
 *    （`NO_VERDICT`）。产品裁定：无判定时保持阻塞，既不伪造"本次未作答"记录，也不给假的记忆更新。
 *
 * 定价是本地的、非独立的：题目级只落 HARD（对）/ AGAIN（错），权重 0.5。依据见
 * `docs/research/model-judged-verdict-pricing.md`。
 *
 * AUD-11 ①「归属落账」：模型判词分支把**驱动证据**落成快照的一条归属
 * （`KnowledgeEvidenceAttribution` = 驱动证据的节点 + 该题该 revision 的现役绑定 + 驱动证据的
 * 生效权重），使"哪条节点/证据驱动了判决"在审计面可见；本地核对分支没有 chat-evidence 驱动
 * （判定来自检查题作答），归属保持为空。
 *
 * **驱动证据的 `evidence_id` 落不进来**（实施者核实的边界）：`KnowledgeEvidenceAttribution`
 * 没有证据 id 字段，唯一的自由槽 `bindingId` 必须解析到 `practice_unit_knowledge_binding` 的
 * 真实行（`AttemptTransactionDao.saveEvidenceSnapshot` 的五元组核对），塞证据 id 会直接抛
 * `ImmutablePayloadConflictException`。要落下 id 需给归属模型加列（schema/迁移/指纹同批），
 * 不在本轨；当前审计面按"节点 + 绑定 + 会话/时刻"回溯那条证据。
 *
 * 投影后果（实施者核实，2026-10-16）：归属进入 `LearningProjector.applyAttempt` 的知识点通道——
 * 按 spec §2.13「全 KC 各记完整证据」，归属决定**哪些 KC** 收到证据、幅度取整条 attempt 的证据
 * 权重（0.5，`attribution.weight` 只作展示/排序），因此被锚定的那个 KC 会收到与题级同源的 0.5，
 * 且与模型 chat-evidence 通道对同一节点的另一笔（≤0.15）并存。**零版本 bump**：投影器与两条归属
 * 派生路径一行未动，新增内容只出现在**新**快照上 → 固定账本的投影逐位不变（判别先例：台账
 * §3.12 M4 测验写通道 / §3.14 M2 提交期接线——写路径新增内容、对固定账本逐位不变 → 零 bump）。
 *
 * KD-44 的伪锚定只是**当时**的归属锚点，不需要自纠代码（与裁定"不加任何面向用户的绑定指引"
 * 同一条纪律）：题事后被归类/改绑后，重放（`LearningProjector.attributionsForAttempt` 的 upcast）
 * 从**当前绑定**重派生归属，历史证据自然改挂真实节点（KF-32 机制；
 * `BindingChangedReplayDrainerTest` 的 KD-44 用例钉住这条）。
 *
 * 幂等：attempt/submission 由 (复习会话, 队列项) 派生，重复结算只生效一次（第二次是回放）。
 * 快照 id **内容寻址**（结算身份 + 归属内容）：同内容重放命中同一行（KD-37 的时间戳容忍继续
 * 生效），内容真变写新行——见 `snapshotId` 处的注释（不这样做，归属落账会把"判词内容变了"的
 * 重试变成 KD-37 同类的确定性身份死路）。
 */
internal class TutorJudgedReviewSettler(
    private val database: StudyDatabasePort,
    private val learnerId: String,
    private val durationModel: LogDurationModel,
    private val reviewLogSink: ReviewLogSink,
    private val writeContext: StudyWriteContext,
    private val learnerSnapshot: suspend () -> LearnerSnapshot,
) {
    suspend fun settle(
        settlement: TutorJudgedReviewSettlement,
    ): TutorJudgedReviewSettlementResult {
        val reviewPlan = requireNotNull(
            database.observeReviewPlanForSession(settlement.sessionId).first(),
        ) { "No persisted review plan owns session ${settlement.sessionId}" }
        val session = requireNotNull(
            (reviewPlan.activeSession ?: reviewPlan.latestSession)?.takeIf {
                it.reviewSessionId == settlement.sessionId
            },
        ) { "Review session ${settlement.sessionId} does not belong to its persisted plan" }
        val orderedQueue = reviewPlan.queue.sortedBy { it.ordinal }
        // 队列项由**本次结算的题**定位（计划内 practice unit 唯一），不能拿
        // expectedStateVersion 当序号：它是会话写入版本（每次提交/撤销各 +1），undo 后与
        // currentOrdinal 解耦；而幂等重放又可能晚于推进（currentOrdinal 已指向下一题）。
        // 口径与 `submitReviewChoice` / `submitReviewSelfReport` 一致；版本只用于写事务 CAS。
        val queueItem = requireNotNull(
            orderedQueue.singleOrNull { it.practiceUnitId == settlement.practiceUnitId },
        ) { "Settlement belongs to a practice unit outside the persisted plan" }
        val mistake = requireNotNull(
            database.observeMistakes().first()
                .singleOrNull { it.practiceUnitId == settlement.practiceUnitId },
        ) { "The planned saved question is no longer active" }

        val isHeadItem = session.currentOrdinal == queueItem.ordinal
        // 判词从**最近锚定的若干个会话**里找，而不是只读最新一条（KD-36）：讲题页每次进入
        // 都是一个新会话（A1「进入即新开」），学生"答过一次、又进去看了一眼就退出"时，
        // 最新锚定的会话里没有作答行——只读最新一条就会把真正作答的那次错过去，
        // 表现成静默 NO_VERDICT：既不结算也不说为什么。取"最近一次形成判定的会话"，
        // 选择行与结算锚点就落在同一个会话上；新鲜度门槛仍拦住旧复习留下的判词。
        val recentAnchors = database.readRecentTutorSessionAnchors(
            practiceUnitId = settlement.practiceUnitId,
            learnerId = learnerId,
            limit = TUTOR_VERDICT_ANCHOR_SCAN_LIMIT,
        )
        // 新→旧取第一个**有判词**的会话（`resolveVerdict` 是 suspend，所以用显式循环）。
        var latestVerdict: Verdict? = null
        for (anchor in recentAnchors) {
            latestVerdict = resolveVerdict(anchor.sessionId, mistake)
            if (latestVerdict != null) break
        }
        val verdict = latestVerdict?.takeIf { candidate ->
            // 只认**本次复习这一项**期间的讲题：上次推进之前的判词早已被上一次结算消费
            // （或本就不属于这次复习），否则一进复习页就会被旧判词直接判定通过。
            // 只对"当前队首项"要求新鲜度——已结算项的重复结算交给写入幂等处理，
            // 而完成态的 lastActiveAt 必然晚于当初那条判词，拿它当门槛会把回放误判成无判词。
            !isHeadItem || candidate.atEpochMillis >= session.lastActiveAtEpochMillis
        }
        // 无判定（模型证据不锚定在本题 KC 集上也算）的统一出口：不写、不推进。
        // 队列项保持到期，下次进入复习还会出现。
        fun noVerdict() = TutorJudgedReviewSettlementResult(
            status = TutorJudgedReviewSettlementStatus.NO_VERDICT,
            progress = session.toProgress(orderedQueue.size),
            nextPracticeUnitId = orderedQueue
                .getOrNull(session.currentOrdinal)
                ?.practiceUnitId,
        )
        if (verdict == null) return noVerdict()

        // AUD-11 ①「归属落账」：模型判词分支的驱动证据落成一条归属（驱动证据的节点 + 该题该
        // revision 的现役绑定）。绑定解析不出来 = 这条证据的锚定在库里不可核实：不伪造归属、
        // 不推进（与"没有锚定证据"同一条出口，保持阻塞、可见）。KD-44：空 KC 集题的锚定就是那条
        // 伪绑定行（见 `pseudoAnchorBinding`），判词把它一并带出——归属落账不再重复解析。
        val attribution = verdict.drivingEvidence?.let { drivingEvidence ->
            attributionForDrivingEvidence(
                practiceUnitId = mistake.practiceUnitId,
                problemRevisionId = mistake.problemRevisionId,
                drivingEvidence = drivingEvidence,
                pseudoAnchor = verdict.pseudoAnchor,
            )
        }
        if (verdict.drivingEvidence != null && attribution == null) return noVerdict()

        val isCorrect = verdict.isCorrect
        val evidence = LearningEvidence(
            direction = if (isCorrect) {
                LearningEvidenceDirection.POSITIVE
            } else {
                LearningEvidenceDirection.NEGATIVE
            },
            weight = MODEL_JUDGED_EVIDENCE_WEIGHT,
            reason = if (isCorrect) {
                LearningEvidenceReason.MODEL_JUDGED_CORRECT
            } else {
                LearningEvidenceReason.MODEL_JUDGED_INCORRECT
            },
        )
        val outcome = if (isCorrect) {
            ProblemMemoryOutcome.ASSISTED_RECALL
        } else {
            ProblemMemoryOutcome.RETRIEVAL_FAILURE
        }
        val revisionKey = "${mistake.practiceUnitId}\n${mistake.problemRevisionId}"
        val snapshot = AssessmentEvidenceSnapshot(
            // AUD-11 ①：归属进入快照载荷后，"稳定 id = 稳定内容"的前提不再成立——一次重试若
            // 带着**不同的判词内容**回来（模型判词↔本地核对导致归属从有到无、换驱动节点、换
            // 权重、改绑换 taxonomy），同一 snapshotId 会被 `sameEvidenceSnapshotIdentity` 判成
            // "另一份载荷"而永久冲突（KD-37 的同类确定性死路：快照写入在幂等回放之前，一撞就
            // 再也写不进去）。因此 id 改为**内容寻址**（题 + revision + 归属集，与
            // `StudyPracticeUnitFacts` 的快照 id 同规则）：同内容重试仍命中同一行（KD-37 的时间戳
            // 容忍继续生效），内容真变则写新行、不冲突；旧行不参与投影，只是历史。
            snapshotId = writeContext.stableId(
                "tutor-judged-snapshot",
                buildString {
                    append(settlement.requestId)
                    attribution?.let { entry ->
                        append('\n').append(entry.bindingId)
                        append('\n').append(entry.knowledgeNodeId)
                        append('\n').append(entry.weight)
                        append('\n').append(entry.taxonomyVersion)
                    }
                },
            ),
            assessmentItemId = LocalModelJudgedContract.ASSESSMENT_ITEM_ID_PREFIX +
                writeContext.stableId("item", revisionKey),
            practiceUnitId = mistake.practiceUnitId,
            problemRevisionId = mistake.problemRevisionId,
            answerSpecId = LocalModelJudgedContract.ANSWER_SPEC_ID,
            itemFamilyId = LocalModelJudgedContract.ITEM_FAMILY_ID,
            sourceBundleId = null,
            // 归属与快照的 taxonomy 必须逐字一致（`DatabaseContractValidator`），而归属的
            // taxonomy 又必须与库里那条绑定行一致（`AttemptTransactionDao.saveEvidenceSnapshot`
            // 的五元组核对）——有归属时两者都由该绑定决定；无归属（本地核对分支）维持本通道
            // 自己的合同串。
            taxonomyVersion = attribution?.taxonomyVersion ?: LocalModelJudgedContract.TAXONOMY_VERSION,
            verification = AssessmentSnapshotVerification.VERIFIED,
            calibration = CalibrationSnapshot.unknown(),
            // 本地核对分支的驱动是检查题作答（没有节点可挂）→ 归属为空；模型判词分支落驱动证据
            // 的那一条（AUD-11 ①）。这条通道仍然只驱动题目级排期，但归属另开知识点通道——见类
            // KDoc 的「投影后果」。
            attributions = listOfNotNull(attribution),
            capturedAtEpochMillis = settlement.occurredAtEpochMillis,
        )
        // 0 表示由结算自己推导（避免调用方凭空给一个时长）：本轮判词发生时间 − 上次推进时间。
        val durationSeconds = if (settlement.durationSeconds > 0) {
            settlement.durationSeconds
        } else {
            ((verdict.atEpochMillis - session.lastActiveAtEpochMillis) / 1_000L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
        val priorMemory = learnerSnapshot().problemMemoryStates[settlement.practiceUnitId]
        database.saveAssessmentEvidenceSnapshot(snapshot)
        val writeResult = database.recordReviewAttempt(
            ReviewAttemptWriteCommand(
                attempt = AttemptWriteCommand(
                    learnerId = learnerId,
                    submissionId = writeContext.stableId(
                        "submission",
                        "tutor-judged:${settlement.sessionId}:${settlement.expectedStateVersion}",
                    ),
                    attemptId = writeContext.stableId(
                        "attempt",
                        "tutor-judged:${settlement.sessionId}:${settlement.expectedStateVersion}",
                    ),
                    presentationId = settlement.presentationId,
                    assessmentSnapshotId = snapshot.snapshotId,
                    submittedResponse = AttemptSubmittedResponse.Choice(
                        choiceId = if (isCorrect) "tutor-judged:correct" else "tutor-judged:incorrect",
                        choiceMarkdown = if (isCorrect) {
                            "讲题判定：这次做对了"
                        } else {
                            "讲题判定：这次还没做对"
                        },
                        submittedAtEpochMillis = settlement.occurredAtEpochMillis,
                    ),
                    evidence = evidence,
                    problemMemoryOutcome = outcome,
                    occurredAtEpochMillis = settlement.occurredAtEpochMillis,
                    durationSeconds = durationSeconds,
                    studyDay = writeContext.studyDayAt(settlement.occurredAtEpochMillis),
                ),
                sessionId = settlement.sessionId,
                expectedStateVersion = settlement.expectedStateVersion,
                reviewQueueItemId = queueItem.reviewQueueItemId,
                practiceUnitId = queueItem.practiceUnitId,
            ),
        )
        if (writeResult.attempt.created) {
            reviewLogSink.record(
                practiceUnitId = settlement.practiceUnitId,
                evidence = evidence,
                occurredAtEpochMillis = settlement.occurredAtEpochMillis,
                durationSeconds = durationSeconds,
                studyDay = writeContext.studyDayAt(settlement.occurredAtEpochMillis),
                sourceKind = if (verdict.fromLocalCheck) {
                    // 裁决 18：本地核对是干净信号，单独落档并**参与** FSRS 参数拟合。
                    ReviewLogSink.SOURCE_KIND_LOCAL_CHECKED
                } else {
                    // 模型判词保持排除（κ≈0.70 判定噪声），校准单列一档。
                    ReviewLogSink.SOURCE_KIND_MODEL_JUDGED
                },
                sourceId = writeResult.attempt.attempt.attemptId,
                priorMemory = priorMemory,
                plannedReason = queueItem.reasonSnapshot.takeIf(String::isNotBlank),
            )
            durationModel.record(
                learnerId = learnerId,
                subjectId = mistake.subject,
                itemType = null,
                difficulty = 5.0, // unused dimension; kept for API stability
                durationSeconds = durationSeconds.toDouble(),
            )
        }
        val progress = writeResult.advance.session.toProgress(orderedQueue.size)
        return TutorJudgedReviewSettlementResult(
            status = TutorJudgedReviewSettlementStatus.RECORDED,
            isCorrect = isCorrect,
            attemptId = writeResult.attempt.attempt.attemptId,
            created = writeResult.attempt.created,
            progress = progress,
            nextPracticeUnitId = orderedQueue
                .getOrNull(progress.currentOrdinal)
                ?.practiceUnitId,
        )
    }

    /**
     * 本会话的判定：本地核对优先，其次模型判词，都没有返回 null。
     * 只统计**最新一轮**（cycle）的检查题：`restartCycle` 重教之后，上一轮的答错
     * 正是重教的理由，不该把它永久算在头上（与 `tutorSessionObjectiveRecord` 同一原因）。
     *
     * 模型判词分支的取数口径（AUD-11 ①）：只认**门控接受**（`rejected_reason == null` 且
     * `weight > 0`）且 `knowledge_node_id ∈ 锚定集`的最新一条。锚定集 = 本题 KC 集；
     * **空 KC 集**（诚实未分类的题）时 = 该题 revision 的**伪绑定**节点（KD-44，解析见
     * [pseudoAnchorBinding]），所以这类题的判词不再被整条丢弃；反过来，题面之外的真实节点
     * 证据仍不构成判词。
     *
     * 一条可供锚定的证据都没有时**不解析锚定**——空 KC 集题的锚定解析会就地物化伪绑定，而
     * 真·无判词（连接受过的证据都没有）的结算不该因此多写一行。
     */
    private suspend fun resolveVerdict(
        tutorSessionId: String,
        mistake: MistakeRecord,
    ): Verdict? {
        val responses = database.observeTutorTurnResponses(tutorSessionId).first()
        val latestCycle = responses.maxOfOrNull(TutorTurnResponseRecord::cycleOrdinal)
        if (latestCycle != null) {
            val currentCycle = responses.filter { it.cycleOrdinal == latestCycle }
            val record = tutorSessionObjectiveRecord(
                currentCycle.mapNotNull(TutorTurnResponseRecord::selectionWasCorrect),
            )
            if (record.answeredCount > 0) {
                val judgedAt = currentCycle.mapNotNull(TutorTurnResponseRecord::choiceSubmittedAtEpochMillis)
                    .maxOrNull()
                    ?: currentCycle.maxOf(TutorTurnResponseRecord::updatedAtEpochMillis)
                return Verdict(
                    isCorrect = !record.contradictsPositiveClaim,
                    atEpochMillis = judgedAt,
                    // 本地核对：检查题自带标准答案，判定是确定性的（台账裁决 18）。
                    fromLocalCheck = true,
                )
            }
        }
        // AUD-11 ①：跨粒度的证据无权翻转整题——前置 KC 的较新正向证据（半权 0.075）
        // 曾压过目标 KC 的负向证据，把整题判成做对并按 0.5 记账（报告 §1.2 实测）。
        val eligibleEvidence = database
            .readChatEvidenceByConversation(TutorConversationIds.captured(tutorSessionId))
            .filter { it.rejected_reason == null && it.weight > 0.0 }
        if (eligibleEvidence.isEmpty()) return null
        // KD-44：空 KC 集（诚实未分类）题的锚定集 = 该题 revision 的伪绑定节点；物化失败
        // （返回 null）→ 锚定集为空 = 与"锚定不可核实"同一条出口：不判、不写、不推进。
        val pseudoAnchor = if (mistake.knowledgeNodeIds.isEmpty()) {
            pseudoAnchorBinding(mistake)
        } else {
            null
        }
        val anchorNodeIds = pseudoAnchor
            ?.let { binding -> setOf(binding.knowledgeNodeId) }
            ?: mistake.knowledgeNodeIds
        val evidence = eligibleEvidence
            .filter { it.knowledge_node_id in anchorNodeIds }
            .maxByOrNull { it.created_at_epoch_millis }
            ?: return null
        val isCorrect = when (evidence.direction) {
            LearningEvidenceDirection.POSITIVE.name -> true
            LearningEvidenceDirection.NEGATIVE.name -> false
            else -> return null
        }
        return Verdict(
            isCorrect = isCorrect,
            atEpochMillis = evidence.created_at_epoch_millis,
            // 模型判词：开放作答，κ≈0.70 的判定噪声（台账裁决 18）。
            fromLocalCheck = false,
            drivingEvidence = evidence,
            pseudoAnchor = pseudoAnchor,
        )
    }

    /**
     * KD-44：本题的**伪绑定**——空 KC 集（诚实未分类）题的锚定与归属都落在这条行上。
     * 已物化则直接取，未物化则复用既有物化先例就地物化
     * （`StudyPracticeUnitFacts.attributionSetFor` 的空绑定分支与 `StudyProjectionDrainer.pseudoBindingFacts`
     * 是同一份规则：同一个 `ensurePseudoKnowledgeBinding` 端口、同一 taxonomy 常量、revision 取本题、
     * 科目取 `problem.subject`、时间取 revision 创建时刻 ⇒ 确定性、重放幂等）。
     *
     * **为什么用"表里全部行"读口而不是"当前绑定"**：题一旦被确认过（哪怕确认出的绑定集为空），
     * `readCurrentKnowledgeBindingsForPracticeUnit` 只认"最近一次确认那一批"，此前物化的伪绑定行
     * 不在那一批里（真库口径见 `BindingChangedReplayDrainerTest` 的幂等 (b) 分支）；而归属必须指向
     * 一条**真实存在**的绑定行（写入口 `saveEvidenceSnapshot` 按五元组核对）。这也是调用方要把
     * 解析结果显式带进 [attributionForDrivingEvidence] 的原因。
     *
     * 安全性（"审计遗迹"口径不适用）：候选被 (题, revision, `pseudo:<科目>`) 三重限定——同题同
     * revision 的伪绑定行全指向同一个科目兜底节点，不存在"改绑后旧节点继续吃证据"的那种分叉；
     * 取 bindingId 字典序最小者只为确定性（planning 侧 `pseudo-plan-v1` 与证据侧
     * `pseudo-evidence-v1` 并存时优先后者，与写路径/重放选择同一个 taxonomy）。
     *
     * 返回 null = 无法物化（题没有评估记录，或端口/数据拒绝）→ 调用方按"锚定不可核实"处置。
     */
    private suspend fun pseudoAnchorBinding(
        mistake: MistakeRecord,
    ): PracticeUnitKnowledgeBindingRecord? {
        val record = database.readPracticeUnitAssessment(mistake.practiceUnitId) ?: return null
        val pseudoNodeId = "pseudo:${record.subject.uppercase()}"
        database.readPracticeUnitKnowledgeBindings(mistake.practiceUnitId)
            .filter { binding ->
                binding.bindingId.isNotBlank() &&
                    binding.knowledgeNodeId == pseudoNodeId &&
                    binding.basisRevisionId == mistake.problemRevisionId
            }
            .minByOrNull(PracticeUnitKnowledgeBindingRecord::bindingId)
            ?.let { existing -> return existing }
        return database.ensurePseudoKnowledgeBinding(
            practiceUnitId = mistake.practiceUnitId,
            problemRevisionId = mistake.problemRevisionId,
            taxonomyVersion = StudyPracticeUnitFacts.PSEUDO_ATTRIBUTION_TAXONOMY_VERSION,
            subject = record.subject,
            acceptedAtEpochMillis = record.revisionCreatedAtEpochMillis,
        )
    }

    /**
     * AUD-11 ①：驱动证据的知识归属。绑定取该题**该 revision 的现役绑定**（与写路径/重放共用
     * 的读口 `readCurrentPracticeUnitKnowledgeBindings`），同节点多行时取 bindingId 字典序最小
     * 者（确定性）；找不到返回 null——调用方按"锚定不可核实"处置，不伪造归属（题面 KC 集与
     * 库内绑定不一致属异常态，不该猜一条挂上）。
     *
     * KD-44：空 KC 集题的现役绑定就是那条**伪绑定**（[pseudoAnchorBinding]），而它按回执口径读不到
     * （见该函数的 KDoc），所以由调用方作为显式候选传入；同一套过滤（节点 + revision）对两条来路
     * 一视同仁——伪绑定行只有节点与 revision 都对上驱动证据时才会被选中，不因为"兜底"而放宽。
     *
     * 为什么必须是**真实绑定行**：`AttemptTransactionDao.saveEvidenceSnapshot` 对每条归属做
     * binding/practiceUnit/node/basisRevision/taxonomy 五元组核对，悬挂引用直接抛
     * `ImmutablePayloadConflictException`；`DatabaseContractValidator` 另要求归属的
     * basisRevision/taxonomy 与快照逐字一致，所以快照的 taxonomy 也随该绑定
     * （不再恒为 `LocalModelJudgedContract.TAXONOMY_VERSION`）。
     *
     * `weight` 记驱动证据的**生效权重**（门控与 D9 降权施加之后）；投影幅度不吃这个字段
     * （spec §2.13 记整条证据），它记录的是"这条归属由多强的一条证据驱动"。
     */
    private suspend fun attributionForDrivingEvidence(
        practiceUnitId: String,
        problemRevisionId: String,
        drivingEvidence: LearnerChatEvidenceEntity,
        /** KD-44：空 KC 集题的伪绑定（见 [pseudoAnchorBinding]）；真绑定题恒 null。 */
        pseudoAnchor: PracticeUnitKnowledgeBindingRecord? = null,
    ): KnowledgeEvidenceAttribution? {
        val candidates = database.readCurrentPracticeUnitKnowledgeBindings(practiceUnitId) +
            listOfNotNull(pseudoAnchor)
        val binding = candidates
            .filter { candidate ->
                candidate.bindingId.isNotBlank() &&
                    candidate.knowledgeNodeId == drivingEvidence.knowledge_node_id &&
                    candidate.basisRevisionId == problemRevisionId
            }
            .minByOrNull { it.bindingId }
            ?: return null
        return KnowledgeEvidenceAttribution(
            bindingId = binding.bindingId,
            knowledgeNodeId = binding.knowledgeNodeId,
            // 门控保证 weight ∈ (0, 1]；上界兜一层，归属合同的区间是 (0, 1]。
            weight = drivingEvidence.weight.coerceAtMost(1.0),
            basisRevisionId = problemRevisionId,
            taxonomyVersion = binding.taxonomyVersion,
            role = EvidenceAttributionRole.PRIMARY,
            certainty = EvidenceAttributionCertainty.DIRECT,
        )
    }

    private data class Verdict(
        val isCorrect: Boolean,
        val atEpochMillis: Long,
        /** 判定来源：true=本地核对（进拟合集），false=模型判词（保持排除）。 */
        val fromLocalCheck: Boolean,
        /** 模型判词分支的**驱动证据行**（本地核对分支没有 chat-evidence 驱动：null）。 */
        val drivingEvidence: LearnerChatEvidenceEntity? = null,
        /**
         * KD-44：这条判词用的**伪绑定锚点**（只有空 KC 集题的模型判词分支非 null）。
         * 归属落账直接消费它——那一行按回执口径读不到（见 [pseudoAnchorBinding]），
         * 也不能在这里重复解析（重复解析只会再走一次同一幂等物化）。
         */
        val pseudoAnchor: PracticeUnitKnowledgeBindingRecord? = null,
    )

    internal companion object {
        /**
         * 题目级证据权重【I 先验】：区间 0.5–0.6 的保守端（等于既有 assisted 上限的下沿），
         * 校准达标前不动；依据见 `docs/research/model-judged-verdict-pricing.md` §4(i)。
         */
        const val MODEL_JUDGED_EVIDENCE_WEIGHT = 0.5

        /**
         * 判词回看的锚点上限：结算最多在最近这么多个讲题会话里找判词。
         * 有界（不会翻遍整段历史），也够覆盖"作答后又进了一次讲题页"的错配。
         */
        const val TUTOR_VERDICT_ANCHOR_SCAN_LIMIT = 8
    }
}
