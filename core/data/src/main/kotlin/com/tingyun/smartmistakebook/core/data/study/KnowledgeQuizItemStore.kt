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
 * 身份与不可变：一行 = 一个 `(learner, 会话, 知识点)` 的题面。会话分量让"同一会话重试读回
 * 同一道题"与"隔日新会话生成新题"各自成立；不可变让"首见即真"成为可依赖的语义——同身份
 * 的第二笔写入撞 [ImmutablePayloadConflictException] 时，本题面**不是**被覆盖，而是回读
 * 既有行，屏幕上的题面与库里的题面因此不可能分叉。
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
    suspend fun read(sessionId: Long, knowledgeNodeId: String): TutorAssessmentItem? =
        readSnapshot(sessionId, knowledgeNodeId)?.item

    /**
     * 读回该身份那一行呈现快照的**审计视图**（题面 + 键版本引用，AUD-11 ③）：
     * 行在那 id / revision / 键载荷 schema 就报得出，题面本身读不回时 `item == null`
     * ——坏行如实说坏，不猜题面（与 [read] 同一次解析，屏幕与审计面读到的题面不可能分叉）。
     */
    suspend fun readSnapshot(sessionId: Long, knowledgeNodeId: String): KnowledgeQuizItemSnapshotView? {
        val record = database.findAssessmentItemSnapshot(
            assessmentItemSnapshotId = knowledgeQuizItemSnapshotId(
                learnerId = learnerId,
                sessionId = sessionId,
                knowledgeNodeId = knowledgeNodeId,
            ),
        ) ?: return null
        val spec = answerSpec(record)
        return KnowledgeQuizItemSnapshotView(
            assessmentItemSnapshotId = record.assessmentItemSnapshotId,
            itemRevision = record.itemRevision,
            answerSpecSchema = spec?.schema,
            item = decode(record, spec),
        )
    }

    /**
     * 落库并回读：返回库里那一行的题面。同身份已有**可复用**行时返回既有行（首见即真）；
     * 既有行损坏时**覆盖重写**为 `item` 并回读（KD-41，重试因此可恢复）。
     *
     * null = 该行最终仍读不出来（本次要写的题面自己就解不开、或冲突根本不是本 id 的行引起的）
     * ——调用方按失败出口处置，不拿内存里的题面顶替库里的题面。
     */
    suspend fun save(
        sessionId: Long,
        knowledgeNodeId: String,
        item: TutorAssessmentItem,
        occurredAtEpochMillis: Long,
    ): TutorAssessmentItem? {
        val snapshotId = knowledgeQuizItemSnapshotId(
            learnerId = learnerId,
            sessionId = sessionId,
            knowledgeNodeId = knowledgeNodeId,
        )
        val incoming = record(
            snapshotId = snapshotId,
            item = item,
            occurredAtEpochMillis = occurredAtEpochMillis,
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
                database.overwriteAssessmentItemSnapshot(incoming)
            }
        }
        return read(sessionId, knowledgeNodeId)
    }

    private fun record(
        snapshotId: String,
        item: TutorAssessmentItem,
        occurredAtEpochMillis: Long,
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
        // 本题面是本地机械判定的单选（对错由 selectedChoiceId == correctChoiceId 得出），
        // 落 VERIFIED + AUTO_VERIFIED；会话专用（不产生 attempt 记录）落 SESSION_ONLY。
        verificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
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
        val answerSpecJson = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
        }
    }
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
    val item: TutorAssessmentItem?,
)

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
