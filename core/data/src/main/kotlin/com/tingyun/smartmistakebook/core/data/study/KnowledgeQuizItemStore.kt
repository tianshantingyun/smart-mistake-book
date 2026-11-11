package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.ImmutablePayloadConflictException
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 知识点复习题面的持久化（AUD-02）。
 *
 * 退场背景：题面此前只活在会话 ViewModel 的内存里（`currentItem`），进程被系统回收后
 * 内存态必然丢失，重建时只能"再出一次题"——又出一次网，而验收要求恢复不自动新出题/再出网。
 *
 * 落库位置与写口：**复用既有 assessment 呈现快照表**（`assessment_item_snapshot`）与它的
 * 既有写口 [StudyDatabasePort.saveAssessmentItemSnapshot]，不新造表——题面本来就是"呈现
 * 内容"，与学习证据分表正是这张表的既有语义（"Immutable presentation content, intentionally
 * separate from accepted learning evidence"）。`assessment_presentation` 用不上：它的行
 * 外键指向 `assessment_evidence_snapshot`，而后者只随 practice unit 存在，知识点复习的题
 * 没有 practice unit。
 *
 * 身份与不可变：一行 = 一个 `(learner, 会话, 知识点, 槽位)` 的题面。会话分量让"同一会话
 * 重试读回同一道题"与"隔日新会话生成新题"各自成立；槽位分量（KD-67 第二道校准题）让
 * 学生显式要求的第二道题有**自己的行**——与会话题面互不覆盖、各自独立核验。不可变让
 * "首见即真"成为可依赖的语义——同身份的第二笔写入撞 [ImmutablePayloadConflictException]
 * 时，本题面**不是**被覆盖，而是回读既有行，屏幕上的题面与库里的题面因此不可能分叉。
 *
 * 损坏（KD-41）：`options_snapshot`/`answer_spec_snapshot` 解不开、或 schema 不是本题面版本时
 * 既不是"已有可用题面"、也不是"没有题面"——是"**不可复用**的一行"。读口照旧返回 null
 * （绝不猜一个题面出来），写口则把这一行**原地覆盖重写**：不这么做，损坏行会把"重试出题"
 * 顶死在同一身份上（每次重试白烧一次模型调用、题面永远显示不出来）。
 */
internal class KnowledgeQuizItemStore(
    private val database: StudyDatabasePort,
    private val learnerId: String,
) {

    /** 读回该会话该知识点已持久化的题面；无行或损坏 = null。 */
    suspend fun read(
        sessionId: Long,
        knowledgeNodeId: String,
        slot: KnowledgeQuizQuestionSlot = KnowledgeQuizQuestionSlot.REVIEW,
    ): TutorAssessmentItem? {
        val stored = readSnapshot(sessionId, knowledgeNodeId, slot)?.item ?: return null
        // PRD-02：题面快照一字不改；但**读给会话**的题面要带上账本里最新有效的键替代，
        // 否则 UI 的本地判题（`item.evaluateChoice`）与写入口的更正键会分叉，ViewModel 的
        // 判答一致性校验会当场失败。没有更正时行为逐位不变。
        //
        // KD-43（修复）：两处必须与写入口同源——
        // 1. 候选排序传账本序（读不完整时由 `latestCorrectedKey` 显式降级为时间序）；
        // 2. 只接受**本会话**的键替代。题面快照按 `(learner, 会话, 知识点)` 存储，而
        //    `itemId` 是 `knowledge-quiz:item:<node>` 的常量：隔日新会话会生成新题面/新选项，
        //    若把上一会话的键套到新题面，轻则 `correctChoiceId` 不在选项集中而抛异常，
        //    重则用旧题的键给新题判分。
        val rows = database.readChatEvidenceByLearner(learnerId)
        val correctedKey = latestCorrectedKey(
            rows = rows,
            knowledgeNodeId = knowledgeNodeId,
            itemId = stored.id,
            ledgerOrder = if (rows.hasKeyCorrectionEvidence(stored.id)) {
                database.correctionLedgerOrder(learnerId)
            } else {
                null
            },
            sessionId = sessionId,
        )
        // 防御：同会话的替代键若不在本题选项集内（历史脏数据），不把会抛异常的题面端上屏幕；
        // 保持快照里的原键。正常情况下这条分支不可达。
        val appliesToThisItem = correctedKey != null &&
            correctedKey != stored.correctChoiceId &&
            stored.choices.any { it.id == correctedKey }
        return if (appliesToThisItem) {
            stored.copy(correctChoiceId = requireNotNull(correctedKey))
        } else {
            stored
        }
    }

    /**
     * 读回该身份那一行呈现快照的**审计视图**（题面 + 键版本引用，AUD-11 ③）：
     * 行在那 id / revision / 键载荷 schema 就报得出，题面本身读不回时 `item == null`
     * ——坏行如实说坏，不猜题面（与 [read] 同一次解析，屏幕与审计面读到的题面不可能分叉）。
     */
    suspend fun readSnapshot(
        sessionId: Long,
        knowledgeNodeId: String,
        slot: KnowledgeQuizQuestionSlot = KnowledgeQuizQuestionSlot.REVIEW,
    ): KnowledgeQuizItemSnapshotView? {
        val record = database.findAssessmentItemSnapshot(
            assessmentItemSnapshotId = knowledgeQuizItemSnapshotId(
                learnerId = learnerId,
                sessionId = sessionId,
                knowledgeNodeId = knowledgeNodeId,
                slot = slot,
            ),
        ) ?: return null
        val spec = answerSpec(record)
        return KnowledgeQuizItemSnapshotView(
            assessmentItemSnapshotId = record.assessmentItemSnapshotId,
            itemRevision = record.itemRevision,
            answerSpecSchema = spec?.schema,
            verificationStatus = record.verificationStatus,
            scoringMode = record.scoringMode,
            item = decode(record, spec),
        )
    }

    /**
     * 按**题面 id** 读回该知识点两个槽位中对应的那一行（KD-67 第二道校准题）。
     *
     * 消灭的失败：答案键写入/确认的既有读口按 `(会话, 知识点)` 取**会话题面槽**。第二道
     * 校准题与第一道题在同一个 `(会话, 知识点)` 下，键可以不同——继续读第一槽就会拿第一道
     * 题的键去判第二道题的作答、或把第二道题的确认来源校验到第一行上。调用方已经知道
     * 它要处理哪道题（证据行/更正命令里带着题面 id）时，必须按 id 定位，不能按槽猜。
     *
     * 两槽都读不到该 id（含行损坏、题面解不开）→ null：调用方按"这道题不在了"的失败出口
     * 处置，不退回读另一道题顶替。
     */
    suspend fun readSnapshotForItem(
        sessionId: Long,
        knowledgeNodeId: String,
        itemId: String,
    ): KnowledgeQuizItemSnapshotView? {
        for (slot in KnowledgeQuizQuestionSlot.entries) {
            val view = readSnapshot(sessionId, knowledgeNodeId, slot) ?: continue
            if (view.item?.id == itemId) return view
        }
        return null
    }

    /**
     * 落库并回读：返回库里那一行的题面。同身份已有**可复用**行时返回既有行（首见即真）；
     * 既有行损坏时**覆盖重写**为 `item` 并回读（KD-41，重试因此可恢复）。
     *
     * null = 该行最终仍读不出来（本次要写的题面自己就解不开、或冲突根本不是本 id 的行引起的）
     * ——调用方按失败出口处置，不拿内存里的题面顶替库里的题面。
     *
     * [verificationStatus]（KD-67）：调用方已知的键核验状态。默认 `UNKNOWN` 保持模型生成
     * 题面的既有语义（没有独立支持）；调用方有独立核验/用户确认依据时可显式写入。
     * 损坏行重写时**不会**把库内已有的已核验状态降回 `UNKNOWN`：修复口只修内容，
     * 状态列的原值是另一条事实（谁确认过这道题），不能被"重试出题"顺手抹掉。
     */
    suspend fun save(
        sessionId: Long,
        knowledgeNodeId: String,
        item: TutorAssessmentItem,
        occurredAtEpochMillis: Long,
        verificationStatus: String = StudyDbValue.VerificationStatus.UNKNOWN,
        /**
         * 落库槽位（KD-67 第二道校准题）：默认 [KnowledgeQuizQuestionSlot.REVIEW]，
         * 既有调用方/既有行身份逐字不变。第二道题写 [KnowledgeQuizQuestionSlot.CALIBRATION]，
         * 两槽各自的不可变闸、损坏行修复与核验状态互不影响。
         */
        slot: KnowledgeQuizQuestionSlot = KnowledgeQuizQuestionSlot.REVIEW,
    ): TutorAssessmentItem? {
        val snapshotId = knowledgeQuizItemSnapshotId(
            learnerId = learnerId,
            sessionId = sessionId,
            knowledgeNodeId = knowledgeNodeId,
            slot = slot,
        )
        val incoming = record(
            snapshotId = snapshotId,
            item = item,
            occurredAtEpochMillis = occurredAtEpochMillis,
            verificationStatus = verificationStatus,
        )
        try {
            database.saveAssessmentItemSnapshot(incoming)
        } catch (_: ImmutablePayloadConflictException) {
            // 同一身份已有行且与本次不同。两种可能，按"这行还能不能用"分派：
            // - 解得开 = 正常行（首见即真）：不是故障，是"这道题已经出过"。吃掉冲突回读既有行，
            //   保证学生作答的那道题 = 之后恢复读回的那道题；
            // - 解不开 = 损坏行（KD-41，不可复用）：改前这里只能回读出 null，而调用方会把
            //   "重试出题"新生成的题面再交给本口、再撞同一行、再丢弃——白烧一次模型调用、
            //   题面永远显示不出来。损坏行没有可保护的内容，原地覆盖重写，让重试有出路。
            val existing = database.findAssessmentItemSnapshot(snapshotId)
            if (existing != null && decode(existing) == null) {
                // 损坏行身上唯一值得保留的元数据就是核验状态：调用方没显式给新状态时，
                // 沿用库里的值（KD-67：一次已经发生的确认不因重试而消失）。
                val effectiveStatus = if (verificationStatus == StudyDbValue.VerificationStatus.UNKNOWN) {
                    existing.verificationStatus
                } else {
                    verificationStatus
                }
                database.overwriteAssessmentItemSnapshot(
                    if (effectiveStatus == incoming.verificationStatus) {
                        incoming
                    } else {
                        incoming.copy(verificationStatus = effectiveStatus)
                    },
                )
            }
        }
        return read(sessionId, knowledgeNodeId, slot)
    }

    private fun record(
        snapshotId: String,
        item: TutorAssessmentItem,
        occurredAtEpochMillis: Long,
        verificationStatus: String,
    ): AssessmentItemSnapshotSeedRecord = AssessmentItemSnapshotSeedRecord(
        assessmentItemSnapshotId = snapshotId,
        itemRevision = ITEM_REVISION,
        // 知识点复习的题不属于任何 practice unit：两列同时为 null 是校验口允许的形状
        //（题面快照的归属由 tutor_content_snapshot_id 那一列承担）。
        practiceUnitId = null,
        problemRevisionId = null,
        // 这张表的"内容快照"列在这里记的是**题面自身的生成身份**（会话+知识点）：
        // 同一题面身份只该有一行，所以它与行 id 同源（不是 teaching material id——
        // 一份材料可能覆盖多个知识点，用它会让第二个知识点撞唯一索引）。
        tutorContentSnapshotId = snapshotId,
        promptMarkdown = item.stemMarkdown,
        optionsSnapshot = optionsJson.encodeToString(
            ListSerializer(TutorChoice.serializer()),
            item.choices,
        ),
        answerSpecSnapshot = answerSpecJson.encodeToString(
            KnowledgeQuizAnswerSpec.serializer(),
            KnowledgeQuizAnswerSpec(
                schema = ANSWER_SPEC_SCHEMA,
                itemId = item.id,
                correctChoiceId = item.correctChoiceId,
                knowledgeNodeIds = item.knowledgeNodeIds.sorted(),
            ),
        ),
        // PRD-02：**两轴拆分**。`scoringMode = AUTO_VERIFIED` 只声明"对错可由本地
        // `selectedChoiceId == correctChoiceId` 机械确定"；它**不**表示键本身已被独立核验。
        // 模型生成的题面没有独立支持，故调用方默认传 `UNKNOWN`——写入口据此只保留
        // 会话反馈与真实选择，不新增数值掌握证据（要计分须先把该行升级为 VERIFIED/
        // USER_ASSERTED，例如用户明确更正或独立核验流程）。KD-67：状态不再是写死的常量，
        // 已核验的题面（重写/迁移）可以如实写回自己的状态。
        verificationStatus = verificationStatus,
        assessmentEligibility = StudyDbValue.AssessmentEligibility.SESSION_ONLY,
        scoringMode = StudyDbValue.ScoringMode.AUTO_VERIFIED,
        // 题面不依赖学习快照版本，也不参与投影检查点：两列落固定标记与 0，不伪造一个投影位点。
        learnerSnapshotVersion = LEARNER_SNAPSHOT_VERSION,
        projectionCheckpoint = 0L,
        hintLevelAtPresentation = 0,
        answerRevealState = ANSWER_REVEAL_STATE_HIDDEN,
        createdAtEpochMillis = occurredAtEpochMillis,
    )

    /**
     * 解键载荷（`answer_spec_snapshot`）；解不开 = null。
     *
     * 与 [decode] 分开一层：**键版本引用**（`spec.schema`）在"题面读不回"时也报得出来——
     * AUD-11 ③ 的审计面要能说清"这行答的是哪一版键/这行坏在哪"，把版本闸与题面构造混在
     * 一个函数里会让坏行只剩一个 null。
     */
    private fun answerSpec(record: AssessmentItemSnapshotSeedRecord): KnowledgeQuizAnswerSpec? =
        runCatching {
            answerSpecJson.decodeFromString(KnowledgeQuizAnswerSpec.serializer(), record.answerSpecSnapshot)
        }.getOrNull()

    /** 解一行（自带键载荷解析）。[readSnapshot] 走带 `spec` 的重载，避免同一行解析两遍。 */
    private fun decode(record: AssessmentItemSnapshotSeedRecord): TutorAssessmentItem? =
        decode(record, answerSpec(record))

    private fun decode(
        record: AssessmentItemSnapshotSeedRecord,
        spec: KnowledgeQuizAnswerSpec?,
    ): TutorAssessmentItem? {
        val choices = runCatching {
            optionsJson.decodeFromString(ListSerializer(TutorChoice.serializer()), record.optionsSnapshot)
        }.getOrNull() ?: return null
        if (spec == null) return null
        if (spec.schema != ANSWER_SPEC_SCHEMA) return null
        // 形状校验交给模型自己的 init（≥2 选项、答案属于选项集、选项 id 唯一）：
        // 解出来的东西不满足这些约束 = 这行坏了，返回 null 而不是把坏题面端上屏幕。
        return runCatching {
            TutorAssessmentItem(
                id = spec.itemId,
                stemMarkdown = record.promptMarkdown,
                choices = choices,
                correctChoiceId = spec.correctChoiceId,
                knowledgeNodeIds = spec.knowledgeNodeIds.toSet(),
            )
        }.getOrNull()
    }

    private companion object {
        const val ITEM_REVISION = 1
        const val LEARNER_SNAPSHOT_VERSION = "knowledge-quiz-review-v1"
        const val ANSWER_REVEAL_STATE_HIDDEN = "HIDDEN"
        const val ANSWER_SPEC_SCHEMA = "knowledge-quiz-item.v1"

        val optionsJson = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
        }
        val answerSpecJson = knowledgeQuizAnswerSpecJson
    }
}

/**
 * 键载荷的 JSON 口径（单源）：写口与 [knowledgeQuizAnswerSpecOf] 必须逐字段一致，
 * 否则"写进去解得开、读出来解不开"会变成一行静默损坏。
 */
private val knowledgeQuizAnswerSpecJson = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
}

/**
 * 一行知识点复习题面快照的**审计视图**（AUD-11 ③）：题面 + 它的键版本引用。
 *
 * 为什么需要它：证据行里只有方向/权重/理由等 11 个语义列，题面与答案键在
 * `assessment_item_snapshot`（AUD-02）。错键要能被审计、被纠正（语义留 5B）之前，
 * 先得能回答"这笔证据读的是哪一道题、哪一版键"。
 *
 * - [itemRevision]：呈现行的版本（同一身份首见即真，故它是题面的版本分量）；
 * - [answerSpecSchema]：键载荷的 schema（`answer_spec_snapshot.schema`）——键版本闸；
 * - [item]：库里的题面（含标准答案 `correctChoiceId`）；读不回 = null。
 */
internal data class KnowledgeQuizItemSnapshotView(
    val assessmentItemSnapshotId: String,
    val itemRevision: Int,
    val answerSpecSchema: String?,
    /** 键**独立核验**状态（`VERIFIED`/`USER_ASSERTED`/`UNKNOWN`）；与本地可判性两轴。 */
    val verificationStatus: String?,
    /** 本地判分方式（`AUTO_VERIFIED` 等）；**不**蕴含键已验证。 */
    val scoringMode: String?,
    val item: TutorAssessmentItem?,
)

/**
 * 一行呈现快照的**键载荷**（`answer_spec_snapshot` 解码结果）；解不开/版本不认识 = null。
 *
 * 核验升级的来源校验需要"这道题的 itemId 与当前标准答案"，而它只需要读列、不需要题面
 * 可读（题面可读性由升级口单独判）。放在这里而不是让调用方各自 decode：键载荷的 schema
 * 闸只该有一处。
 */
internal fun knowledgeQuizAnswerSpecOf(
    record: AssessmentItemSnapshotSeedRecord,
): KnowledgeQuizAnswerSpec? = runCatching {
    knowledgeQuizAnswerSpecJson.decodeFromString(KnowledgeQuizAnswerSpec.serializer(), record.answerSpecSnapshot)
}.getOrNull()

/**
 * 一道知识点复习题面的行身份：`(learner, 会话, 知识点)`。
 *
 * learner 进 id 的理由：这张表没有 learner 列（呈现内容本身与学习者无关），而"哪一场会话
 * 的哪一道题"是学习者级的呈现状态——不把 learner 编进 id，同设备上两个学习者同一毫秒的
 * 会话会互相顶掉题面。
 */
internal fun knowledgeQuizItemSnapshotId(
    learnerId: String,
    sessionId: Long,
    knowledgeNodeId: String,
): String = "knowledge-quiz-item:$learnerId:$sessionId:$knowledgeNodeId"

/**
 * 知识点复习题面的**槽位**（KD-67 第二道校准题）。
 *
 * 一个 `(learner, 会话, 知识点)` 过去只允许一道题面（单槽）。冲突 KC 卡的「先做校准题」
 * 语义是"真正多出一道题"时，第二道题必须有**自己的行**：与第一道同名会把选择器的候选
 * 去重/答案键更正前缀/证据身份全部撞在一起；覆盖第一道则让学生刚答过的题面消失。
 *
 * 槽位只区分**呈现行身份**，不改变任何核验/计分判据：两个槽各自独立核验、独立降级。
 */
enum class KnowledgeQuizQuestionSlot {
    /** 本次会话的常规复习题面（`knowledge-quiz-item:<learner>:<会话>:<节点>`，既有身份不变）。 */
    REVIEW,

    /** 学生显式要求后生成的第二道校准题（`knowledge-quiz-item-calibration:...`）。 */
    CALIBRATION,
}

/** 槽位限定的题面行身份；[KnowledgeQuizQuestionSlot.REVIEW] 与既有函数逐字一致。 */
internal fun knowledgeQuizItemSnapshotId(
    learnerId: String,
    sessionId: Long,
    knowledgeNodeId: String,
    slot: KnowledgeQuizQuestionSlot,
): String = when (slot) {
    KnowledgeQuizQuestionSlot.REVIEW ->
        knowledgeQuizItemSnapshotId(learnerId, sessionId, knowledgeNodeId)

    KnowledgeQuizQuestionSlot.CALIBRATION ->
        knowledgeQuizCalibrationItemSnapshotId(learnerId, sessionId, knowledgeNodeId)
}

/** 第二道校准题的题面行身份（与会话题面行**不同**的独立身份）。 */
internal fun knowledgeQuizCalibrationItemSnapshotId(
    learnerId: String,
    sessionId: Long,
    knowledgeNodeId: String,
): String = "knowledge-quiz-item-calibration:$learnerId:$sessionId:$knowledgeNodeId"

/**
 * 第二道校准题的**题面 id**（`TutorAssessmentItem.id`）。
 *
 * 为什么必须与会话题面 id（`knowledge-quiz:item:<节点>`）不同，而不是复用同一 id：
 * 1. 选择器要求候选 `assessmentItemId` 唯一（`AdaptiveSelectionRequest` init）——
 *    两道题同 id 会在装配面被去重，第二道题结构上不可能被选中；
 * 2. 答案键更正的机器前缀是 `answer-key-correction:<itemId>:<键>:`，同 id 会让两道题的
 *    键替代流互相污染，一道题的确认被当成另一道题的历史事实。
 */
fun knowledgeQuizCalibrationItemId(knowledgeNodeId: String): String =
    "knowledge-quiz:calibration:$knowledgeNodeId"

/**
 * 题面里的答案规格（`answer_spec_snapshot` 列的内容）：选项在 `options_snapshot` 列，
 * 这里记题面 id、标准答案与知识归属。`schema` 是版本闸：解到不认识的版本按损坏处置。
 */
@Serializable
internal data class KnowledgeQuizAnswerSpec(
    val schema: String,
    val itemId: String,
    val correctChoiceId: String,
    val knowledgeNodeIds: List<String>,
)
