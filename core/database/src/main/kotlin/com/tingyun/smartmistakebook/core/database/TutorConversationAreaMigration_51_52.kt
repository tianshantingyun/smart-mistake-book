package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v51→52：会话事实模型收敛（K1）。
 *
 * 三张表退役、事实并入新家（设计 `docs/superpowers/specs/2026-09-25-agent-first-refactor-design.md`
 * §4.1「并入 / 退役」）：
 *
 * | 退役表 | 新家 |
 * |---|---|
 * | `tutor_session`（拍照会话工作态） | `tutor_conversation` 的 `capture_draft_id` / `capture_draft_revision_number` |
 * | `tutor_session_problem_anchor`（会话锚定的题） | `tutor_conversation` 的锚块（learner / 题修订 / 练习单元 / 来源 / 时刻） |
 * | `tutor_turn_response`（讲题轮次事实） | `tutor_message` 的轮次块（按 `conversation_id + round_cycle_ordinal + round_turn_ordinal` 定位，唯一索引） |
 *
 * 为什么锚落在**会话行**而不是曝光账本：锚是**会话级**事实（一个会话一个锚），会话行是它唯一的
 * 家；账本上的 `anchor_source` / `anchored_at_epoch_millis` 是**物化快照**（与账本已有的
 * `problem_revision_id` / `practice_unit_id` 同一手法：曝光物化那一刻的锚被抄进不可变账本，
 * 会话后来被删也不影响历史账本的完整）。
 *
 * ## 非破坏性
 *
 * 旧行全部可读，且**不猜测**：
 * - 采集会话的绑定两列直接来自 `tutor_session`（原值照抄）；
 * - 锚块五列直接来自 `tutor_session_problem_anchor`（原值照抄）；
 * - 轮次块十一列直接来自 `tutor_turn_response`（原值照抄，含选择题三件事实与揭示/动作两个标志）。
 *   轮次行的 `ordinal` 是**内部排序键**，旧行按 `1000000 + rowid` 落在不与既有消息冲突的高位区间
 *   （旧会话的消息序号是低位小整数），`last_turn_ordinal` 随之上抬——序号不是学生可见事实，
 *   它只保证 `(conversation_id, ordinal)` 唯一这条不破；
 * - 旧会话行（`tutor_conversation`）如果因为三张表的事实才存在，本迁移**补建**它（会话 id 约定
 *   `tutor-conv:captured:<sessionId>`）：有轮次、有锚的会话是有内容的会话，不补建就等于丢事实。
 *   补建行的锚修订列填该会话的题修订，其余可空列保持 NULL——**不编造**当初没有记下的值。
 *
 * ## 存量清理
 *
 * 删除一条消息都没有的会话行（K1b「空会话不落库」的存量部分）。放在轮次搬迁**之后**：
 * 只有轮次、没有聊天消息的会话是有内容的会话，必须留下。
 */
internal val TUTOR_CONVERSATION_AREA_MIGRATION_51_52 = object : Migration(51, 52) {
    override suspend fun migrate(connection: SQLiteConnection) {
        addConversationAreaAndAnchorColumns(connection)
        addRoundColumnsToMessages(connection)
        createNewIndices(connection)
        relocateCaptureSessions(connection)
        relocateSessionAnchors(connection)
        relocateTurnResponses(connection)
        // 插眼 5（持久确认卡）：新表随本版一起落，DDL 在 AgentPendingRequestMigration.kt。
        createAgentPendingRequestTable(connection)
        markProblemRevisionConversationsAsReviewMistake(connection)
        rebuildAnswerExposureOutcome(connection)
        relabelTutorTaskSubjects(connection)
        connection.execSQL("DROP TABLE IF EXISTS `tutor_turn_response`")
        connection.execSQL("DROP TABLE IF EXISTS `tutor_session_problem_anchor`")
        connection.execSQL("DROP TABLE IF EXISTS `tutor_session`")
        // D-Q5 死重删除：结构化视觉交互链整条退役（渲染器/事件汇/记录表一起删）。
        // 表建在 v33，本版是它唯一的下线点；旧安装的这张表随迁移消失。
        connection.execSQL("DROP TABLE IF EXISTS `visual_interaction_attempt`")
        dropEmptyConversations(connection)
    }

    /**
     * `conversation_area` 是新建列，NOT NULL 且给默认值（SQLite 只允许带默认值的 NOT NULL 加列）；
     * 锚块五列（学习者 / 题修订 / 练习单元 / 来源 / 锚定时刻）来自 `tutor_session_problem_anchor`，
     * 采集绑定两列来自 `tutor_session`——值全部照抄，本迁移不做派生（派生在下面的搬迁步骤里）。
     */
    private fun addConversationAreaAndAnchorColumns(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `tutor_conversation` ADD COLUMN `conversation_area` TEXT NOT NULL DEFAULT 'AGENT'",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_conversation` ADD COLUMN `capture_draft_id` TEXT",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_conversation` ADD COLUMN `capture_draft_revision_number` INTEGER",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_conversation` ADD COLUMN `anchor_learner_id` TEXT",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_conversation` ADD COLUMN `anchor_problem_revision_id` TEXT",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_conversation` ADD COLUMN `anchor_practice_unit_id` TEXT",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_conversation` ADD COLUMN `anchor_source` TEXT",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_conversation` ADD COLUMN `anchored_at_epoch_millis` INTEGER",
        )
    }

    /**
     * 轮次块十一列：三件选择题事实（题干 / 所选选项与对错 / 反馈）、两个标志（揭示解法、所选动作）、
     * 轮次键两列、本轮题面两列。旧消息行这些列全为 NULL（不是轮次行）；`solution_revealed`
     * 带默认值 0，旧消息行读回 `false`（没有揭示过解法）。
     */
    private fun addRoundColumnsToMessages(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `round_cycle_ordinal` INTEGER",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `round_turn_ordinal` INTEGER",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `round_question_document_id` TEXT",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `round_revision_number` INTEGER",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `choice_stem_markdown` TEXT",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `choice_selected_id` TEXT",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `choice_selected_markdown` TEXT",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `choice_was_correct` INTEGER",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `choice_feedback_markdown` TEXT",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `solution_revealed` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL(
            "ALTER TABLE `tutor_message` ADD COLUMN `requested_move` TEXT",
        )
    }

    private fun createNewIndices(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tutor_conversation_conversation_area` " +
                "ON `tutor_conversation` (`conversation_area`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tutor_conversation_capture_draft_id` " +
                "ON `tutor_conversation` (`capture_draft_id`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_tutor_conversation_anchor_practice_unit_id_anchor_learner_id` " +
                "ON `tutor_conversation` (`anchor_practice_unit_id`, `anchor_learner_id`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_tutor_message_conversation_id_round_cycle_ordinal_round_turn_ordinal` " +
                "ON `tutor_message` (`conversation_id`, `round_cycle_ordinal`, `round_turn_ordinal`)",
        )
    }

    /**
     * 拍照会话的工作态 → 会话行的两列。会话 id 与锚修订按仓库既有约定派生：
     * `tutor-conv:captured:<sessionId>` 与 `<draftId>:<revisionNumber>`（见 `TutorSessionViewModel`
     * 与 `TutorRespondCommands.ensureStudentConversation`），不是新发明的形状。
     */
    private fun relocateCaptureSessions(connection: SQLiteConnection) {
        connection.execSQL(
            """
            INSERT OR IGNORE INTO `tutor_conversation` (
                `conversation_id`, `conversation_area`, `anchor_kind`, `anchor_id`, `anchor_revision_id`,
                `capture_draft_id`, `capture_draft_revision_number`, `status`, `title`,
                `created_at_epoch_millis`, `updated_at_epoch_millis`, `last_turn_ordinal`, `student_draft`
            )
            SELECT 'tutor-conv:captured:' || `session_id`, 'AGENT', 'EPHEMERAL_DRAFT', `session_id`,
                   `draft_id` || ':' || `draft_revision_number`,
                   `draft_id`, `draft_revision_number`, 'ACTIVE', NULL,
                   `created_at_epoch_millis`, `created_at_epoch_millis`, 0, NULL
            FROM `tutor_session`
            """,
        )
        // 会话行**通常已经存在**（`TutorSessionViewModel` 在进入讲题页时就建了它），
        // 于是上面的 INSERT OR IGNORE 什么也不做——绑定必须**补写**到已存在的行上，
        // 只 INSERT 会让所有真实旧安装的采集绑定静默丢失。只填 NULL，不覆盖。
        connection.execSQL(
            """
            UPDATE `tutor_conversation`
            SET `capture_draft_id` = (
                    SELECT session.`draft_id` FROM `tutor_session` AS session
                    WHERE session.`session_id` = `tutor_conversation`.`anchor_id`
                ),
                `capture_draft_revision_number` = (
                    SELECT session.`draft_revision_number` FROM `tutor_session` AS session
                    WHERE session.`session_id` = `tutor_conversation`.`anchor_id`
                )
            WHERE `capture_draft_id` IS NULL
              AND EXISTS (
                SELECT 1 FROM `tutor_session` AS session
                WHERE session.`session_id` = `tutor_conversation`.`anchor_id`
              )
            """,
        )
    }

    /**
     * 会话锚 → 会话行的锚块。join 键用会话行的 `anchor_id`（讲题会话把它写成会话 id，
     * 与锚表的主键同一个键空间），不用字符串切分。
     *
     * 有锚、但没有会话行的会话先补建：它已经有轮次/锚 = 有内容，补建行只填能确定的列。
     */
    private fun relocateSessionAnchors(connection: SQLiteConnection) {
        connection.execSQL(
            """
            INSERT OR IGNORE INTO `tutor_conversation` (
                `conversation_id`, `conversation_area`, `anchor_kind`, `anchor_id`, `anchor_revision_id`,
                `status`, `title`, `created_at_epoch_millis`, `updated_at_epoch_millis`,
                `last_turn_ordinal`, `student_draft`
            )
            SELECT 'tutor-conv:captured:' || `session_id`, 'AGENT', 'EPHEMERAL_DRAFT', `session_id`,
                   `problem_revision_id`, 'ACTIVE', NULL,
                   `anchored_at_epoch_millis`, `anchored_at_epoch_millis`, 0, NULL
            FROM `tutor_session_problem_anchor`
            """,
        )
        connection.execSQL(
            """
            UPDATE `tutor_conversation`
            SET `anchor_learner_id` = (
                    SELECT `learner_id` FROM `tutor_session_problem_anchor` AS anchor
                    WHERE anchor.`session_id` = `tutor_conversation`.`anchor_id`
                ),
                `anchor_problem_revision_id` = (
                    SELECT `problem_revision_id` FROM `tutor_session_problem_anchor` AS anchor
                    WHERE anchor.`session_id` = `tutor_conversation`.`anchor_id`
                ),
                `anchor_practice_unit_id` = (
                    SELECT `practice_unit_id` FROM `tutor_session_problem_anchor` AS anchor
                    WHERE anchor.`session_id` = `tutor_conversation`.`anchor_id`
                ),
                `anchor_source` = (
                    SELECT `anchor_source` FROM `tutor_session_problem_anchor` AS anchor
                    WHERE anchor.`session_id` = `tutor_conversation`.`anchor_id`
                ),
                `anchored_at_epoch_millis` = (
                    SELECT `anchored_at_epoch_millis` FROM `tutor_session_problem_anchor` AS anchor
                    WHERE anchor.`session_id` = `tutor_conversation`.`anchor_id`
                ),
                `updated_at_epoch_millis` = MAX(
                    `updated_at_epoch_millis`,
                    COALESCE((
                        SELECT `anchored_at_epoch_millis` FROM `tutor_session_problem_anchor` AS anchor
                        WHERE anchor.`session_id` = `tutor_conversation`.`anchor_id`
                    ), 0)
                )
            WHERE EXISTS (
                SELECT 1 FROM `tutor_session_problem_anchor` AS anchor
                WHERE anchor.`session_id` = `tutor_conversation`.`anchor_id`
            )
            """,
        )
    }

    /**
     * 轮次事实 → 轮次行（`tutor_message` 上的一行）。
     *
     * `ordinal` 用 `1000000 + rowid`：旧会话的消息序号是低位小整数，这个区间保证
     * `(conversation_id, ordinal)` 唯一索引不被撞（撞了 `INSERT OR IGNORE` 会**静默丢掉**
     * 真实轮次）。序号只是内部排序键，学生看不到它。
     *
     * `message_id` 与写侧同一派生式 `tutor-round:<sessionId>:<cycle>:<turn>`，所以迁移后
     * 新一轮的重放（同轮次号）读到的还是这一行，不会多出一行。
     */
    private fun relocateTurnResponses(connection: SQLiteConnection) {
        connection.execSQL(
            """
            INSERT OR IGNORE INTO `tutor_conversation` (
                `conversation_id`, `conversation_area`, `anchor_kind`, `anchor_id`, `anchor_revision_id`,
                `status`, `title`, `created_at_epoch_millis`, `updated_at_epoch_millis`,
                `last_turn_ordinal`, `student_draft`
            )
            SELECT 'tutor-conv:captured:' || `session_id`, 'AGENT', 'EPHEMERAL_DRAFT', `session_id`,
                   `question_document_id` || ':' || `revision_number`, 'ACTIVE', NULL,
                   MIN(`submitted_at_epoch_millis`), MAX(`updated_at_epoch_millis`), 0, NULL
            FROM `tutor_turn_response`
            GROUP BY `session_id`, `question_document_id`, `revision_number`
            """,
        )
        connection.execSQL(
            """
            INSERT OR IGNORE INTO `tutor_message` (
                `message_id`, `conversation_id`, `ordinal`, `role`, `body_markdown`,
                `round_cycle_ordinal`, `round_turn_ordinal`,
                `round_question_document_id`, `round_revision_number`,
                `choice_stem_markdown`, `choice_selected_id`, `choice_selected_markdown`,
                `choice_was_correct`, `choice_feedback_markdown`,
                `solution_revealed`, `requested_move`,
                `status`, `logical_operation_id`, `reply_to_message_id`,
                `created_at_epoch_millis`, `completed_at_epoch_millis`, `error_code`
            )
            SELECT 'tutor-round:' || `session_id` || ':' || `cycle_ordinal` || ':' || `turn_ordinal`,
                   'tutor-conv:captured:' || `session_id`,
                   1000000 + `rowid`,
                   'LOCAL_EVENT', '',
                   `cycle_ordinal`, `turn_ordinal`,
                   `question_document_id`, `revision_number`,
                   `diagnostic_stem_markdown`, `selected_choice_id`, `selected_choice_markdown`,
                   `selection_was_correct`, `feedback_markdown`,
                   `solution_revealed`, `requested_move`,
                   'PERSISTED', NULL, NULL,
                   COALESCE(`choice_submitted_at_epoch_millis`, `submitted_at_epoch_millis`),
                   MAX(`updated_at_epoch_millis`, `submitted_at_epoch_millis`),
                   NULL
            FROM `tutor_turn_response`
            """,
        )
        connection.execSQL(
            """
            UPDATE `tutor_conversation`
            SET `last_turn_ordinal` = MAX(
                `last_turn_ordinal`,
                COALESCE((
                    SELECT MAX(round.`ordinal`) FROM `tutor_message` AS round
                    WHERE round.`conversation_id` = `tutor_conversation`.`conversation_id`
                      AND round.`round_cycle_ordinal` IS NOT NULL
                ), 0)
            )
            WHERE EXISTS (
                SELECT 1 FROM `tutor_message` AS round
                WHERE round.`conversation_id` = `tutor_conversation`.`conversation_id`
                  AND round.`round_cycle_ordinal` IS NOT NULL
            )
            """,
        )
    }

    /**
     * 会话区回填（D-Q1M10 前的存量语义）：`anchor_kind='PROBLEM_REVISION'` 的会话是错题讲题，
     * 归复习栏的错题复习区；其余旧行无法区分拍照/错题/大厅，统一留 `AGENT`（不猜测）。
     */
    private fun markProblemRevisionConversationsAsReviewMistake(connection: SQLiteConnection) {
        connection.execSQL(
            """
            UPDATE `tutor_conversation`
            SET `conversation_area` = 'REVIEW_MISTAKE'
            WHERE `anchor_kind` = 'PROBLEM_REVISION'
            """,
        )
    }

    /**
     * 曝光账本重建（create-new → INSERT SELECT → DROP → RENAME → 重建 5 个索引）：
     * 三列新增（本地判对结果 + 锚来源/锚定时刻快照），并**去掉**对 `tutor_session_problem_anchor`
     * 的外键（那张表退役）。不用 `DROP COLUMN`：minSdk 23 的 SQLite 不支持，且改外键本来就要重建。
     *
     * 三个新列用子查询回填旧行：判对结果按（会话 + 周期 + 轮次）从轮次表取，锚两列按会话取。
     * 取不到就留 NULL（= 旧行没有这个事实），不编造。
     */
    private fun rebuildAnswerExposureOutcome(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tutor_answer_exposure_outcome_new` (
                `outcome_id` TEXT NOT NULL,
                `exposure_id` TEXT NOT NULL,
                `learner_id` TEXT NOT NULL,
                `session_id` TEXT NOT NULL,
                `question_document_id` TEXT NOT NULL,
                `question_revision_number` INTEGER NOT NULL,
                `cycle_ordinal` INTEGER NOT NULL,
                `turn_ordinal` INTEGER NOT NULL,
                `problem_revision_id` TEXT NOT NULL,
                `practice_unit_id` TEXT NOT NULL,
                `selection_was_correct` INTEGER,
                `anchor_source` TEXT,
                `anchored_at_epoch_millis` INTEGER,
                `event_sequence` INTEGER NOT NULL,
                `canonical_fingerprint` TEXT NOT NULL,
                `occurred_at_epoch_millis` INTEGER NOT NULL,
                PRIMARY KEY(`outcome_id`),
                FOREIGN KEY(`exposure_id`) REFERENCES `tutor_answer_exposure`(`exposure_id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`practice_unit_id`, `problem_revision_id`)
                    REFERENCES `practice_unit`(`practice_unit_id`, `problem_revision_id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """,
        )
        connection.execSQL(
            """
            INSERT INTO `tutor_answer_exposure_outcome_new` (
                `outcome_id`, `exposure_id`, `learner_id`, `session_id`,
                `question_document_id`, `question_revision_number`,
                `cycle_ordinal`, `turn_ordinal`,
                `problem_revision_id`, `practice_unit_id`,
                `selection_was_correct`, `anchor_source`, `anchored_at_epoch_millis`,
                `event_sequence`, `canonical_fingerprint`, `occurred_at_epoch_millis`
            )
            SELECT outcome.`outcome_id`, outcome.`exposure_id`, outcome.`learner_id`, outcome.`session_id`,
                   outcome.`question_document_id`, outcome.`question_revision_number`,
                   outcome.`cycle_ordinal`, outcome.`turn_ordinal`,
                   outcome.`problem_revision_id`, outcome.`practice_unit_id`,
                   (
                       SELECT round.`selection_was_correct` FROM `tutor_turn_response` AS round
                       WHERE round.`session_id` = outcome.`session_id`
                         AND round.`cycle_ordinal` = outcome.`cycle_ordinal`
                         AND round.`turn_ordinal` = outcome.`turn_ordinal`
                       LIMIT 1
                   ),
                   (
                       SELECT anchor.`anchor_source` FROM `tutor_session_problem_anchor` AS anchor
                       WHERE anchor.`session_id` = outcome.`session_id`
                       LIMIT 1
                   ),
                   (
                       SELECT anchor.`anchored_at_epoch_millis`
                       FROM `tutor_session_problem_anchor` AS anchor
                       WHERE anchor.`session_id` = outcome.`session_id`
                       LIMIT 1
                   ),
                   outcome.`event_sequence`, outcome.`canonical_fingerprint`,
                   outcome.`occurred_at_epoch_millis`
            FROM `tutor_answer_exposure_outcome` AS outcome
            """,
        )
        connection.execSQL("DROP TABLE `tutor_answer_exposure_outcome`")
        connection.execSQL(
            "ALTER TABLE `tutor_answer_exposure_outcome_new` RENAME TO `tutor_answer_exposure_outcome`",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_tutor_answer_exposure_outcome_exposure_id` " +
                "ON `tutor_answer_exposure_outcome` (`exposure_id`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tutor_answer_exposure_outcome_session_id` " +
                "ON `tutor_answer_exposure_outcome` (`session_id`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_tutor_answer_exposure_outcome_practice_unit_id_problem_revision_id` " +
                "ON `tutor_answer_exposure_outcome` (`practice_unit_id`, `problem_revision_id`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_tutor_answer_exposure_outcome_learner_id_event_sequence` " +
                "ON `tutor_answer_exposure_outcome` (`learner_id`, `event_sequence`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_tutor_answer_exposure_outcome_learner_id_outcome_id` " +
                "ON `tutor_answer_exposure_outcome` (`learner_id`, `outcome_id`)",
        )
    }

    /**
     * `model_task` 槽键主语从"讲题会话 id"改成"会话 id"（K1c，见 `TutorPlanInput.subjectId`）。
     * 只改 PLAN / RESPOND 两类：视觉与复盘任务的槽键口径没变，改了反而会让它们的旧行对不上
     * `observeBySubject` 的读侧。
     *
     * `model_task_operation` 一并跟：它的 `subject_id` 只用于"同一逻辑操作重放时主客体一致"的
     * 校验（`ModelTaskTransactionDao.create`），不跟就会在同一条操作被重放时判成换了主语。
     */
    private fun relabelTutorTaskSubjects(connection: SQLiteConnection) {
        connection.execSQL(
            """
            UPDATE `model_task`
            SET `subject_id` = 'tutor-conv:captured:' || `subject_id`
            WHERE `task_kind` IN ('TUTOR_PLAN', 'TUTOR_RESPOND')
              AND `subject_id` NOT LIKE 'tutor-conv:%'
            """,
        )
        connection.execSQL(
            """
            UPDATE `model_task_operation`
            SET `subject_id` = 'tutor-conv:captured:' || `subject_id`
            WHERE `task_kind` IN ('TUTOR_PLAN', 'TUTOR_RESPOND')
              AND `subject_id` NOT LIKE 'tutor-conv:%'
            """,
        )
    }

    /** K1b 存量清理：一条消息都没有的会话行删掉（空会话不落库）。 */
    private fun dropEmptyConversations(connection: SQLiteConnection) {
        connection.execSQL(
            """
            DELETE FROM `tutor_conversation`
            WHERE NOT EXISTS (
                SELECT 1 FROM `tutor_message` AS message
                WHERE message.`conversation_id` = `tutor_conversation`.`conversation_id`
            )
            """,
        )
    }
}
