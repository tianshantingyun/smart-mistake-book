package com.tingyun.smartmistakebook.core.database.entity

import com.tingyun.smartmistakebook.core.database.dao.TUTOR_INTERACTION_MODE_NORMAL
import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * 会话行 = **会话身份的唯一落点**（K1）。三张退役表的事实并入本行：
 *
 * - `tutor_session`（拍照会话工作态）→ [captureDraftId] / [captureDraftRevisionNumber]；
 * - `tutor_session_problem_anchor`（会话锚定的题）→ [anchorProblemRevisionId] /
 *   [anchorPracticeUnitId] / [anchorSource] / [anchoredAtEpochMillis]（实现铁律 D-K1 §5
 *   "并入 / 退役"，锚是**会话级**事实，会话行是它唯一的家）。
 *
 * 为什么锚不放到 `tutor_answer_exposure_outcome` 上：曝光账本只记"这次曝光量出了什么"，
 * 而"这个会话锚在哪道题上"在**会话**的键上（一个会话一个锚）。放在会话行上，绑定一次、
 * 读一次都不需要跨表；账本里的 `anchor_source`/`anchored_at_epoch_millis` 是**物化快照**
 * （与 outcome 的 `problem_revision_id`/`practice_unit_id` 同一手法），不是第二份权威。
 *
 * [anchorRevisionId] 与 [captureDraftId] 的分工：前者是**会话锚**（拍照会话 = 会话 id，
 * 错题讲题 = `questionDocumentId:draftRevisionNumber`，两个入口的锚不是一个键空间），
 * 后者是**这张会话绑定的那份采集草稿修订**（唯一索引 `draft_id` 的替代物，用于
 * "同一草稿只能有一个进行中的讲题会话"与"草稿工作区是否还有人用"两处判定）。
 */
@Entity(
    tableName = "tutor_conversation",
    indices = [
        Index(value = ["updated_at_epoch_millis"]),
        Index(value = ["status"]),
        Index(value = ["conversation_area"]),
        Index(value = ["capture_draft_id"]),
        Index(value = ["anchor_practice_unit_id", "anchor_learner_id"]),
        Index(
            value = [
                "anchor_kind",
                "anchor_id",
                "anchor_revision_id",
            ],
        ),
    ],
)
internal data class TutorConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    /**
     * 会话区（D-Q1H/M10）：AGENT / REVIEW_MISTAKE / REVIEW_KNOWLEDGE，集合开放。
     * 旧行回填见 [TUTOR_CONVERSATION_AREA_MIGRATION_51_52]；新行由创建方显式给出。
     */
    @ColumnInfo(name = "conversation_area", defaultValue = "'AGENT'")
    val conversationArea: String,
    /**
     * 交互模式（D-Q9，52→53 加列）：NORMAL / GUIDED，集合开放（与会话区同一手法，存字符串）。
     *
     * 默认值给 NORMAL，理由与 [conversationArea] 相同：迁移前的旧行当年只有正常模式这一种形态，
     * 迁移**不回填、不猜测**（默认值就是当年的事实）。新行由创建方显式给出（复习栏默认引导属阶段 5）。
     *
     * Kotlin 侧的默认值只服务**三条自动建行路径**（采集会话绑定 / 曝光账本 / 轮次行）：它们建的
     * 都是 `AGENT` 区的会话，模式就是正常。复习栏在阶段 5 引入时由创建方显式给 GUIDED。
     */
    @ColumnInfo(name = "interaction_mode", defaultValue = "'NORMAL'")
    val interactionMode: String = TUTOR_INTERACTION_MODE_NORMAL,
    @ColumnInfo(name = "anchor_kind")
    val anchorKind: String,
    @ColumnInfo(name = "anchor_id")
    val anchorId: String?,
    @ColumnInfo(name = "anchor_revision_id")
    val anchorRevisionId: String?,
    /** 原 `tutor_session.draft_id`：本会话绑定的采集草稿（拍照/导入会话才有）。 */
    @ColumnInfo(name = "capture_draft_id")
    val captureDraftId: String? = null,
    /** 原 `tutor_session.draft_revision_number`：绑定的**已确认**草稿修订号。 */
    @ColumnInfo(name = "capture_draft_revision_number")
    val captureDraftRevisionNumber: Int? = null,
    /**
     * 原 `tutor_session_problem_anchor`：本会话锚定到的那道题。
     * 三相一起写、一起为空（只给修订不给练习单元定位不到题，反之亦然）。
     */
    @ColumnInfo(name = "anchor_learner_id")
    val anchorLearnerId: String? = null,
    @ColumnInfo(name = "anchor_problem_revision_id")
    val anchorProblemRevisionId: String? = null,
    @ColumnInfo(name = "anchor_practice_unit_id")
    val anchorPracticeUnitId: String? = null,
    /** `DRAFT_COMMIT` / `SAVED_MISTAKE`；NULL = 从未锚定（无题会话）。 */
    @ColumnInfo(name = "anchor_source")
    val anchorSource: String? = null,
    @ColumnInfo(name = "anchored_at_epoch_millis")
    val anchoredAtEpochMillis: Long? = null,
    val status: String,
    val title: String?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "last_turn_ordinal")
    val lastTurnOrdinal: Int,
    @ColumnInfo(name = "student_draft")
    val studentDraft: String?,
)

/**
 * 一条会话消息（学生 / 助手）。**唯一对话文本权威**（K1a）。
 *
 * 讲题区的**轮次事实**并入本表（原 `tutor_turn_response`，51→52）：一轮 = 一行
 * （`round_cycle_ordinal` + `round_turn_ordinal` 定位），选择题的三件事实（检查题题干 /
 * 所选选项与对错 / 反馈文本）与揭示、动作两个标志都落在这行上。并入的理由不是省表，
 * 是"这一轮到底发生了什么"与"这一轮说了什么"此前分成两处、可以互相矛盾：轮次行说学生
 * 选了 B，消息行上没有对应痕迹；写侧门控的引文语料也读不到选择。一行装起来之后，
 * 本地判对（`choice_was_correct`）、轮次历史（`TutorTurnHistoryEntry`）与时间线渲染
 * （`ChoiceFeedback`）读的是同一行。
 *
 * 非轮次行（大厅聊天）的轮次列全为 NULL，照常只是一条消息。
 */
@Entity(
    tableName = "tutor_message",
    foreignKeys = [
        ForeignKey(
            entity = TutorConversationEntity::class,
            parentColumns = ["conversation_id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversation_id", "ordinal"], unique = true),
        Index(
            value = ["conversation_id", "round_cycle_ordinal", "round_turn_ordinal"],
            unique = true,
        ),
        Index(value = ["logical_operation_id"]),
        Index(value = ["status"]),
    ],
)
internal data class TutorMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    val ordinal: Int,
    val role: String,
    @ColumnInfo(name = "body_markdown")
    val bodyMarkdown: String,
    /** 模型给出的思考轨迹（折叠展示、不回喂模型）；旧行与失败行保持 NULL。 */
    @ColumnInfo(name = "thinking_markdown")
    val thinkingMarkdown: String? = null,
    /**
     * 本轮绑定的题引用（只在学生消息行上写）。NULL = 无题轮，也包含"迁移前写下的旧行"——
     * 旧行的题归属当年无从判定，不回填、不猜测。两列要么都为空、要么都非空：
     * 只给 id 不给 revision 定位不到确定题面，等于没有绑定。
     */
    @ColumnInfo(name = "bound_problem_id")
    val boundProblemId: String? = null,
    @ColumnInfo(name = "bound_problem_revision_id")
    val boundProblemRevisionId: String? = null,
    /**
     * 本行所属的讲题轮次（原 `tutor_turn_response` 的键，51→52 迁入）。
     * NULL = 不是轮次行（大厅聊天消息）。同会话同轮次唯一（见索引）。
     */
    @ColumnInfo(name = "round_cycle_ordinal")
    val roundCycleOrdinal: Int? = null,
    @ColumnInfo(name = "round_turn_ordinal")
    val roundTurnOrdinal: Int? = null,
    /**
     * 这一轮讲的是哪一份题面（原 `tutor_turn_response.question_document_id` +
     * `revision_number`）。会话可能换题（学生本轮显式附加另一道题），所以是**轮次级**事实，
     * 不能从会话的锚推。
     */
    @ColumnInfo(name = "round_question_document_id")
    val roundQuestionDocumentId: String? = null,
    @ColumnInfo(name = "round_revision_number")
    val roundRevisionNumber: Int? = null,
    /** 检查题题干（原 `tutor_turn_response.diagnostic_stem_markdown`）。 */
    @ColumnInfo(name = "choice_stem_markdown")
    val choiceStemMarkdown: String? = null,
    /** 学生所选选项（原 `tutor_turn_response.selected_choice_id/_markdown`）。 */
    @ColumnInfo(name = "choice_selected_id")
    val choiceSelectedId: String? = null,
    @ColumnInfo(name = "choice_selected_markdown")
    val choiceSelectedMarkdown: String? = null,
    /**
     * 本地判对结果（原 `tutor_turn_response.selection_was_correct`）。
     * NULL = 本轮没有选择题（或旧行无法回填）。**本地判对优先于模型判词**，读侧据此
     * 反证模型的正向声明（`tutorSessionObjectiveRecord`）。
     */
    @ColumnInfo(name = "choice_was_correct")
    val choiceWasCorrect: Boolean? = null,
    /** 反馈文本（原 `tutor_turn_response.feedback_markdown`）。 */
    @ColumnInfo(name = "choice_feedback_markdown")
    val choiceFeedbackMarkdown: String? = null,
    /**
     * 本轮是否揭示过解法（原 `tutor_turn_response.solution_revealed`，51→52 迁入）。
     * 曝光面校验（PLAN_SOLUTION）与讲题写门据此读。
     */
    @ColumnInfo(name = "solution_revealed", defaultValue = "0")
    val solutionRevealed: Boolean = false,
    /**
     * 学生本轮选择的下一步动作（原 `tutor_turn_response.requested_move`，51→52 迁入）。
     * NULL = 未选。
     */
    @ColumnInfo(name = "requested_move")
    val requestedMove: String? = null,
    /**
     * 这一轮**智能体查阅了什么**的痕迹（B1，52→53 加列）：工具、条数、通过还是被拒（含拒因）。
     * NULL = 没有痕迹（旧行、以及这一轮没发起过工具调用）——迁移不回填、不猜测。
     *
     * 存的是本地形状的 JSON（[com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace]），
     * 只进界面、不进模型：它不参与任何模型输入的指纹。
     */
    @ColumnInfo(name = "tool_trace_json")
    val toolTraceJson: String? = null,
    val status: String,
    @ColumnInfo(name = "logical_operation_id")
    val logicalOperationId: String?,
    @ColumnInfo(name = "reply_to_message_id")
    val replyToMessageId: String?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "completed_at_epoch_millis")
    val completedAtEpochMillis: Long?,
    @ColumnInfo(name = "error_code")
    val errorCode: String?,
)

/**
 * 学生消息附图的规范资产引用。独立 link 表让孤儿清理能按引用判定保留图片；
 * 消息删除时级联删除引用，无人引用的资产由 OrphanAssetGc 清理。
 */
@Entity(
    tableName = "tutor_message_source_asset",
    primaryKeys = ["message_id", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = TutorMessageEntity::class,
            parentColumns = ["message_id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CanonicalSourceAssetEntity::class,
            parentColumns = ["source_asset_id"],
            childColumns = ["source_asset_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["source_asset_id"]),
    ],
)
internal data class TutorMessageSourceAssetEntity(
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "source_asset_id")
    val sourceAssetId: String,
    val ordinal: Int,
)
