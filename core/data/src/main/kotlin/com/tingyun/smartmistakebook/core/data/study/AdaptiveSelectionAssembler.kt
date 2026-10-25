package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.data.knowledge.KnowledgePrerequisiteReader
import com.tingyun.smartmistakebook.core.database.LearningLedgerReadStatus
import com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.AdaptiveCorrectnessPredictor
import com.tingyun.smartmistakebook.core.domain.AdaptiveQuestionCandidate
import com.tingyun.smartmistakebook.core.domain.AdaptiveTargetKind
import com.tingyun.smartmistakebook.core.domain.EloAnswerRecord
import com.tingyun.smartmistakebook.core.domain.EloLearnerState
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.EvidenceCorrection
import com.tingyun.smartmistakebook.core.model.EvidenceCorrectionKind
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * G5-A 读侧装配（R5 提案 §3-A 的"本地双端口"之二；实现规格
 * `docs/research/2026-10-25-elo-predictor-spec.md` §4.2，下称 R4）。
 *
 * 它填的是 R5 提案 §1.2① 指认的缺口：选择器请求契约完整，但生产侧没有人把
 * "库里的题（错题/已生成的测验题）"变成 [AdaptiveQuestionCandidate] 列表——没有
 * 本装配器，`AdaptiveSelectionRequest.candidates` 永远没有生产来源。
 *
 * **只读**：消费既有读面 `loadLearningLedger` / `findAssessmentItemSnapshot`（经
 * [KnowledgeQuizItemStore] 的解码与损坏处置）/ `observeMistakes`；零新表、零写路径、
 * 零 ModelTaskKind、零提示词、零请求 schema（裁定 1：G5 选项 A）。**选择决策不经
 * 模型**：本类只做读侧装配与预测填充，`AdaptiveQuestionSelector` 本体一行不改，
 * 决策族（ASK/NO_SAFE_CANDIDATE/…）仍是它的确定性函数。
 *
 * 候选池（两个来源）：
 * 1. **知识点复习题快照**——本批只装**当次会话池**（`readSnapshot(当前会话, 节点)`
 *    可读即候选）；"跨会话复用旧题面"是 R5 提案 §5 登记的未裁项，本批不代裁；
 * 2. **既有 itemFamily 映射的错题**——itemFamilyId 与 `StudyReviewPlannerService`
 *    :373 同源（`saved-question:<practiceUnitId>`），空绑定的 pseudo 兜底形态同源
 *    （:366-374）但**不物化**（`ensurePseudoKnowledgeBinding` 是写口，装配面禁写；
 *    未物化的 pseudo 节点在调用方的掌握读面上自然缺失 → fail-closed）。
 *
 * 预测输入 = 既有作答证据读面（chat evidence 台账的 `knowledge-quiz:` 通道）；先修稳定度
 * 经 [assemblePrerequisiteStability] 由既有 `KnowledgePrerequisiteReader` 关系图 +
 * 调用方已拿到的 [LearnerSnapshot] 记忆卡 join（KF-16 读侧接线）。
 *
 * 失败语义（R4 §4.5 fail-closed 矩阵）：台账 GAP/CONFLICT/读失败 → 全部候选
 * `predictedCorrectness = null`（选择器既有 `PREDICTION_UNKNOWN_STALE_OR_OUTSIDE_CORRIDOR`
 * 出口）；无候选时退化现状（不生成、不报错）。走廊 fail-closed 是默认行为，本类
 * 不放宽任何判据。
 *
 * **接线状态（如实登记，2026-10-26 复核修正）**：本类是 `internal`，feature/review 是另一个
 * Gradle 模块，**无法直接实例化**；全仓除本文件 KDoc 与测试外零引用，生产调用点不存在。
 * 计划 §5 的"唯一 seam 是热区 VM"只是风险描述，不是已落地的一行接线。要让 feature/review
 * 使用，必须先在本模块暴露 public 端口（或经 core/domain 接口 + 装配实现），再改热区 VM；
 * 本批不写热区，故 G5-A 保持**未接线（partial/blocked）**。
 *
 * **生产候选的已知死端（fail-closed，不是可放宽项）**：唯一的生产题面写入方
 * `KnowledgeQuizItemStore.record` 固定 `verificationStatus = UNKNOWN`，本类只把
 * `VERIFIED`/`USER_ASSERTED` 置真，错题候选恒假；`AdaptiveQuestionSelector.safeBaseCandidates`
 * 要求 `contentVerified = true`，因此两个候选源都会落到 `CLARIFY_OR_CONFIRM`。当前
 * feature/app 没有该决策的消费者，也没有把快照行升级为 VERIFIED/USER_ASSERTED 的写方——
 * 即使按计划接线，G5-A 也到不了 ASK。解除死端需要"核验写方 + CLARIFY 消费者"两个产品件，
 * 不是本装配器可以单方面放宽的判据。测试
 * `assembled production candidates stay fail-closed to clarify until verified` 钉住该出口。
 *
 * 消费面：装配器实例是会话/仓库作用域；水位缓存由 [stateMutex] 串行保护，可并发调用；
 * 方法内所有 `runCatching` 已改为**放行协程取消**的 fail-closed 读（见 [readOrNull]），
 * 取消不会被吞成 null/emptyList。
 */
internal class AdaptiveSelectionAssembler(
    private val database: StudyDatabasePort,
    private val learnerId: String,
    private val predictor: AdaptiveCorrectnessPredictor,
) {

    /** 水位缓存的两字段必须一起读/写：mutex 防止"新水位 + 旧状态"的撕裂读。 */
    private val stateMutex = Mutex()
    private var cachedWatermark: Long? = null
    private var cachedState: EloLearnerState? = null

    /**
     * 台账前缀 → [EloAnswerRecord] 有效流（R4 §2.2/§2.3）：
     * 1) `loadLearningLedger`；status != COMPLETE → null（调用方对全部候选不出预测）；
     * 2) 只取 `ChatEvidenceSubmitted` 中证据 id 以 `knowledge-quiz:` 开头的事件
     *    （通道前缀单源 [KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ]；MODEL_CHAT 讲题
     *    通道是模型判词方向，不是机械判定的对错，计入会污染对错流——R4 §2.4）；
     * 3) `EvidenceCorrection` 按 VOID/REPLACE 解析（链式"更正的更正"按台账序，语义与
     *    掌握度投影同源，见 [resolveEvidenceEffect] 的 KDoc）；被排除的证据不进流，
     *    被替代的证据保持**原 eventSequence 位置与原 occurredAt**、按新对错出现；
     * 4) 每事件按 `conversation_id` 解出会话 → 题面快照 → `choices.size`；
     *    快照行缺失/损坏/解不开 → 该事件 optionsCount = null（guess=0，猜测修正关闭，
     *    proso 自身降级路径）——逐事件降级，不全局失效；
     * 5) 按 eventSequence 升序返回（validPrefix 本就按台账序，排序为幂等防御）。
     *
     * 读失败（含解析期异常）一律 null：调用方 fail-closed，不猜部分结果。
     */
    suspend fun loadEloAnswerStream(): List<EloAnswerRecord>? {
        val ledger = readOrNull { database.loadLearningLedger(learnerId) } ?: return null
        if (ledger.status != LearningLedgerReadStatus.COMPLETE) return null
        return readOrNull { effectiveQuizStream(ledger.validPrefix) }
    }

    /**
     * 当前 Elo 状态 = 有效流的纯折叠，附**水位缓存**（R4 §3.3）：缓存键 = 完整前缀的
     * 最大 eventSequence；命中且相等 → 直接复用（台账 append-only，任何 EvidenceCorrection
     * 本就触发既有机制的全量重放，缓存不可能藏住修正）；键变化 → 重折叠覆盖。
     * 空台账 → [EloLearnerState.INITIAL]（非 null：无历史 = 冷启动预测，走廊必拒）。
     * GAP/CONFLICT/读失败 → null（fail-closed）。
     */
    suspend fun currentEloState(): EloLearnerState? = stateMutex.withLock {
        val ledger = readOrNull { database.loadLearningLedger(learnerId) } ?: return@withLock null
        if (ledger.status != LearningLedgerReadStatus.COMPLETE) return@withLock null
        val watermark = ledger.validPrefix.maxOfOrNull { it.event.eventSequence } ?: 0L
        cachedState?.takeIf { cachedWatermark == watermark }?.let { return@withLock it }
        val stream = readOrNull { effectiveQuizStream(ledger.validPrefix) } ?: return@withLock null
        val state = predictor.replay(stream)
        cachedWatermark = watermark
        cachedState = state
        state
    }

    /**
     * 候选装配：当次会话题面快照池 + 既有 itemFamily 映射的错题 →
     * [AdaptiveQuestionCandidate]（含 predictedCorrectness 填充）。
     *
     * 字段映射的诚实口径（无既有模型的量一律取"不声称"值，不编造）：
     * - `contentVerified`：题面行 = 快照自身核验轴（`VERIFIED`/`USER_ASSERTED` 为真，
     *   模型生成行生于 UNKNOWN → 假，走选择器 CLARIFY_OR_CONFIRM 出口，绝不静默放行）；
     *   错题行在本读面没有可判定的独立核验状态 → 恒假（fail-closed）。
     * - `answerWouldBeRevealed`：题面行按 `answer_reveal_state` 列值现读（非 HIDDEN 即真），
     *   不凭"没见过揭示路径"假设为假；错题行按 false（重做呈现题面，非揭示答案）。
     * - `informationValue`/`fatigueCost`：本读面没有信息量/疲劳模型 → 0.0（不声称；
     *   未核验候选本就进不了打分面）。
     * - `targetKind`：FOUNDATION（知识点复习/错题重做的基准意图；其余意图没有证据来源，
     *   不虚构映射）。
     * - 多节点候选（错题可绑多节点）没有单一预测键 → `predictedCorrectness = null`
     *   （不做跨节点聚合猜测）；错题预测的 optionsCount 取 null（猜测修正关闭）。
     *
     * 装配顺序确定（节点字典序 → 错题 practiceUnitId 序）；assessmentItemId 全局去重
     * （选择器要求候选 id 唯一；快照 item id 与错题 practiceUnitId 是不相交命名空间）。
     */
    suspend fun assembleCandidates(
        sessionId: Long,
        targetKnowledgeNodeIds: Set<String>,
        decisionAtEpochMillis: Long,
    ): List<AdaptiveQuestionCandidate> {
        val eloState = currentEloState()
        val itemStore = KnowledgeQuizItemStore(database, learnerId)
        val seenItemIds = mutableSetOf<String>()
        val candidates = mutableListOf<AdaptiveQuestionCandidate>()

        for (knowledgeNodeId in targetKnowledgeNodeIds.sorted()) {
            val row = database.findAssessmentItemSnapshot(
                assessmentItemSnapshotId = knowledgeQuizItemSnapshotId(learnerId, sessionId, knowledgeNodeId),
            ) ?: continue
            // 损坏/缺失行不可复用 → 无候选（读口语义：绝不猜一个题面出来）。
            val item = itemStore.readSnapshot(sessionId, knowledgeNodeId)?.item ?: continue
            if (!seenItemIds.add(item.id)) continue
            val verified = row.verificationStatus == StudyDbValue.VerificationStatus.VERIFIED ||
                row.verificationStatus == StudyDbValue.VerificationStatus.USER_ASSERTED
            // answer_reveal_state 列值字面（写口单源 KnowledgeQuizItemStore.ANSWER_REVEAL_STATE_HIDDEN，
            // 该常量为 private，故此处按列值比较；漂移会被 Store 写入侧单源拦住）。
            val revealed = row.answerRevealState != QUIZ_REVEAL_STATE_HIDDEN
            val candidateNodeIds = item.knowledgeNodeIds.ifEmpty { setOf(knowledgeNodeId) }
            candidates += AdaptiveQuestionCandidate(
                assessmentItemId = item.id,
                knowledgeNodeIds = candidateNodeIds,
                itemFamilyId = item.id,
                targetKind = AdaptiveTargetKind.FOUNDATION,
                contentVerified = verified,
                answerWouldBeRevealed = revealed,
                informationValue = 0.0,
                // 单节点候选才可按该节点出预测；Elo 状态不可得 → 全部 null（fail-closed）。
                predictedCorrectness = eloState
                    ?.takeIf { candidateNodeIds.size == 1 }
                    ?.let { predictor.predictInterval(it, candidateNodeIds.single(), item.choices.size, decisionAtEpochMillis) },
            )
        }

        val mistakes = readOrNull { database.observeMistakes().first() } ?: emptyList()
        for (mistake in mistakes.sortedBy { it.practiceUnitId }.distinctBy { it.practiceUnitId }) {
            val nodeIds = mistake.knowledgeNodeIds.ifEmpty {
                setOf("pseudo:${mistake.subject.uppercase()}")
            }
            if (!seenItemIds.add(mistake.practiceUnitId)) continue
            candidates += AdaptiveQuestionCandidate(
                assessmentItemId = mistake.practiceUnitId,
                knowledgeNodeIds = nodeIds,
                itemFamilyId = SAVED_QUESTION_FAMILY_PREFIX + mistake.practiceUnitId,
                targetKind = AdaptiveTargetKind.FOUNDATION,
                contentVerified = false,
                answerWouldBeRevealed = false,
                informationValue = 0.0,
                predictedCorrectness = eloState
                    ?.takeIf { nodeIds.size == 1 }
                    ?.let { predictor.predictInterval(it, nodeIds.single(), null, decisionAtEpochMillis) },
            )
        }
        return candidates
    }

    /**
     * 目标节点 → 其先修的记忆稳定度——`AdaptiveSelectionRequest.prerequisiteStabilityDaysByNode`
     * 的生产填充面（KF-16 读侧接线，裁决 28）。语义与选择器字段 KDoc 单源：先修未知
     * （无记忆卡 / 从未作答）→ 集合里的 null（消费方忽略）；目标节点无已知先修 → 无条目
     * （"不知道前置 ≠ 没有前置"）。只读消费既有 [KnowledgePrerequisiteReader]（关系图）与
     * 调用方已拿到的 [learnerSnapshot]（记忆卡），不重读快照、不写图。
     *
     * 消灭的失败：该字段此前只能由各调用点自己拼（图 + 快照 join），第二份实现一旦漂移，
     * 先修压制会在部分调用点上静默失效，而请求契约看起来完全合法。
     */
    suspend fun assemblePrerequisiteStability(
        targetKnowledgeNodeIds: Set<String>,
        learnerSnapshot: LearnerSnapshot,
    ): Map<String, Collection<Double?>> {
        if (targetKnowledgeNodeIds.isEmpty()) return emptyMap()
        val graph = KnowledgePrerequisiteReader(database).graphFor(targetKnowledgeNodeIds)
        return buildMap {
            for (target in targetKnowledgeNodeIds.sorted()) {
                val prerequisiteIds = graph.prerequisitesByDependent[target] ?: continue
                put(
                    target,
                    prerequisiteIds.sorted().map { prerequisiteId ->
                        learnerSnapshot.knowledgeMasteryStates[prerequisiteId]?.memoryStabilityDays
                    },
                )
            }
        }
    }

    /**
     * 台账前缀的有效 quiz 事件流。修正式语法的解析与
     * `LearningProjector.resolveEvidenceEffect`（core/domain，private）同语义：
     * 撤销 = 整条排除；替代 = 最后一条**未被撤销**的 REPLACE（该更正自己又被更正/撤销
     * 时递归到它的最终有效效果）；目标图按严格递增账本序构造，悬空/自指/成环结构上
     * 不可能（读边界已验）。方向权重（weight）不进 Elo 的二元对错，故这里只投影方向。
     */
    private suspend fun effectiveQuizStream(
        ledgerPrefix: List<PersistedLearningLedgerEvent>,
    ): List<EloAnswerRecord> {
        val correctionsByTarget = linkedMapOf<String, MutableList<EvidenceCorrection>>()
        val quizEvidence = linkedMapOf<String, ChatEvidenceSubmitted>()
        val seenEvidenceIds = linkedSetOf<String>()
        for (persisted in ledgerPrefix) {
            when (val event = persisted.event) {
                is ChatEvidenceSubmitted -> {
                    if (event.evidenceId.startsWith(QUIZ_ID_PREFIX)) {
                        quizEvidence[event.evidenceId] = event
                    }
                    seenEvidenceIds += event.evidenceId
                }
                is EvidenceCorrection -> {
                    require(event.targetEvidenceId in seenEvidenceIds) {
                        "An evidence correction must target an earlier chat evidence or correction"
                    }
                    correctionsByTarget.getOrPut(event.targetEvidenceId) { mutableListOf() }.add(event)
                    seenEvidenceIds += event.correctionId
                }
                else -> Unit
            }
        }
        val itemStore = KnowledgeQuizItemStore(database, learnerId)
        // 同一 (会话,节点) 可有多笔作答事件（重提/更正流），选项数按行身份缓存：
        // 快照行不可变（首见即真），逐事件重复解码是纯浪费。
        val optionsCountBySnapshot = HashMap<Pair<Long, String>, Int?>()
        val records = mutableListOf<EloAnswerRecord>()
        for ((evidenceId, event) in quizEvidence) {
            val effect = resolveEvidenceEffect(
                evidenceId = evidenceId,
                ownDirection = event.direction,
                correctionsByTarget = correctionsByTarget,
            )
            if (effect !is QuizEvidenceEffect.Applied) continue
            val direction = effect.direction
            val sessionId = knowledgeQuizSessionIdOf(event.conversationId)
            val optionsCount = sessionId?.let {
                optionsCountBySnapshot.getOrPut(it to event.knowledgeNodeId) {
                    itemStore.readSnapshot(it, event.knowledgeNodeId)?.item?.choices?.size
                }
            }
            records += EloAnswerRecord(
                evidenceId = evidenceId,
                knowledgeNodeId = event.knowledgeNodeId,
                // POSITIVE=答对 / NEGATIVE=答错（R4 §2.2；quiz 通道是本地机械判分，
                // direction 只会出这两值；异常 NONE 按保守的"答错"处理）。
                isCorrect = direction == LearningEvidenceDirection.POSITIVE,
                optionsCount = optionsCount,
                occurredAtEpochMillis = event.occurredAtEpochMillis,
                eventSequence = event.eventSequence,
            )
        }
        return records.sortedBy(EloAnswerRecord::eventSequence)
    }

    /**
     * 一条 quiz 证据的最终有效效果（方向投影版）——与
     * `LearningProjector.resolveEvidenceEffect`（:829-868）逐条同语义的镜像实现：
     * - 更正按 target 归组（第一遍按事件序收集）；
     * - 更正的有效性 = 没有（有效的）VOID 更正指向它（递归 memo）；
     * - 生效更正取**最后一条**：VOID → 排除；REPLACE → 其替代载荷，且若该更正自身
     *   又有生效更正，递归到它的最终效果（"改两次/撤销更正"）。
     * 结果只取决于账本前缀，不取决于读的时刻——增量与全量同源，装配与掌握度投影
     * 同源。core/domain 的解析器是 private（重放内核的 API 面不为本批扩张），故在
     * 消费侧镜像；两处语义的同步由链式用例的测试钉住（VOID/REPLACE/更正的更正）。
     */
    private fun resolveEvidenceEffect(
        evidenceId: String,
        ownDirection: LearningEvidenceDirection,
        correctionsByTarget: Map<String, List<EvidenceCorrection>>,
    ): QuizEvidenceEffect {
        val effectiveness = HashMap<String, Boolean>()
        fun isEffective(correction: EvidenceCorrection): Boolean =
            effectiveness.getOrPut(correction.correctionId) {
                correctionsByTarget[correction.correctionId].orEmpty()
                    .filter { it.kind == EvidenceCorrectionKind.VOID }
                    .none { isEffective(it) }
            }

        val resolved = HashMap<String, QuizEvidenceEffect>()
        fun resolve(id: String, fallback: QuizEvidenceEffect): QuizEvidenceEffect =
            resolved.getOrPut(id) {
                val active = correctionsByTarget[id].orEmpty().filter(::isEffective)
                val last = active.lastOrNull() ?: return@getOrPut fallback
                when (last.kind) {
                    EvidenceCorrectionKind.VOID -> QuizEvidenceEffect.Excluded
                    EvidenceCorrectionKind.REPLACE -> resolve(
                        id = last.correctionId,
                        fallback = QuizEvidenceEffect.Applied(last.replacementDirection),
                    )
                }
            }
        return resolve(evidenceId, QuizEvidenceEffect.Applied(ownDirection))
    }

    private sealed interface QuizEvidenceEffect {
        data class Applied(val direction: LearningEvidenceDirection) : QuizEvidenceEffect
        data object Excluded : QuizEvidenceEffect
    }

    /**
     * fail-closed 读：任何读/解析失败 → null（调用方降级），但**协程取消原样重抛**。
     *
     * `runCatching` 捕获 Throwable（含 CancellationException），会把结构化并发的取消吞成
     * null/emptyList，令已取消的调用继续做无意义工作。本类所有 suspend/IO 读口都走这里。
     */
    private suspend fun <T> readOrNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            null
        }

    private companion object {
        /** quiz 证据 id 的通道前缀（单源 [KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ.idPrefix]）。 */
        val QUIZ_ID_PREFIX = "${KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ.idPrefix}:"

        /** 错题候选的 itemFamily 映射前缀（与 StudyReviewPlannerService :373 同源）。 */
        const val SAVED_QUESTION_FAMILY_PREFIX = "saved-question:"

        /** `assessment_item_snapshot.answer_reveal_state` 的隐藏值（写口单源同值）。 */
        const val QUIZ_REVEAL_STATE_HIDDEN = "HIDDEN"
    }
}
