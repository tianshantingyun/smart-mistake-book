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
 * 损坏：`options_snapshot`/`answer_spec_snapshot` 解不开、或 schema 不是本题面版本时返回
 * null——调用方走既有失败出口（题面读不回就说读不回），绝不猜一个题面出来。
 */
internal class KnowledgeQuizItemStore(
    private val database: StudyDatabasePort,
    private val learnerId: String,
) {

    /** 读回该会话该知识点已持久化的题面；无行或损坏 = null。 */
    suspend fun read(sessionId: Long, knowledgeNodeId: String): TutorAssessmentItem? {
        val record = database.findAssessmentItemSnapshot(
            assessmentItemSnapshotId = knowledgeQuizItemSnapshotId(
                learnerId = learnerId,
                sessionId = sessionId,
                knowledgeNodeId = knowledgeNodeId,
            ),
        ) ?: return null
        return decode(record)
    }

    /**
     * 落库并回读：返回库里那一行的题面（同身份已有行时返回既有行）。null = 行读写不出来
     * （损坏）——调用方按失败出口处置，不拿内存里的题面顶替库里的题面。
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
        try {
            database.saveAssessmentItemSnapshot(
                record(
                    snapshotId = snapshotId,
                    item = item,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                ),
            )
        } catch (_: ImmutablePayloadConflictException) {
            // 同一身份已有行（首见即真）：不是故障，是"这道题已经出过"。吃掉冲突回读既有行，
            // 保证学生作答的那道题 = 之后恢复读回的那道题。
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

    private fun decode(record: AssessmentItemSnapshotSeedRecord): TutorAssessmentItem? {
        val choices = runCatching {
            optionsJson.decodeFromString(ListSerializer(TutorChoice.serializer()), record.optionsSnapshot)
        }.getOrNull() ?: return null
        val spec = runCatching {
            answerSpecJson.decodeFromString(KnowledgeQuizAnswerSpec.serializer(), record.answerSpecSnapshot)
        }.getOrNull() ?: return null
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
