package com.tingyun.smartmistakebook.feature.tutor

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.domain.AppendTutorStudentMessageCommand
import com.tingyun.smartmistakebook.core.domain.ArchiveTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.ClearTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.ConfirmedTutorSession
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.DeleteTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.PauseTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.RecordTutorChoiceCommand
import com.tingyun.smartmistakebook.core.domain.RecordTutorMoveCommand
import com.tingyun.smartmistakebook.core.domain.RecordTutorSolutionExposureCommand
import com.tingyun.smartmistakebook.core.domain.RevealTutorSolutionCommand
import com.tingyun.smartmistakebook.core.domain.SaveTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.TutorAnswerExposureSurfaceKind
import com.tingyun.smartmistakebook.core.domain.TutorConversation
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationSnapshot
import com.tingyun.smartmistakebook.core.domain.TutorConversationStatus
import com.tingyun.smartmistakebook.core.domain.TutorInteractionRepository
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.domain.TutorTurnResponse
import com.tingyun.smartmistakebook.core.domain.UpdateTutorMessageStatusCommand
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import com.tingyun.smartmistakebook.core.model.TutorIntentDecision
import com.tingyun.smartmistakebook.core.model.TutorMoveType
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRoundQuestionDeclaration
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.TutorSuggestedMove
import com.tingyun.smartmistakebook.core.model.TutorTurnHistoryEntry
import com.tingyun.smartmistakebook.core.model.TutorTurnPlan
import com.tingyun.smartmistakebook.core.model.WritingLayer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * 替身输出里的本轮绑定：答案暴露要求"本轮确实有绑定题"（暴露记录是"学生看过这道题的答案"
 * 的证据，锚不到题就没有主人）。
 */
internal val EXPOSURE_BOUND_QUESTION = TutorRoundQuestionDeclaration(
    problemId = "bound-problem-1",
    problemRevisionId = "bound-revision-1",
    anchorTerms = listOf("答案"),
)

@RunWith(AndroidJUnit4::class)
abstract class CapturedTutorSessionTestBase {
    @get:Rule
    val composeRule = createComposeRule()

    protected fun captureCurrentTutorScreen(displayName: String = "tutor-active-current.png") {
        val resolver = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .contentResolver
        val collection = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        val relativePath = "Pictures/SmartMistakeBookQA/"
        resolver.delete(
            collection,
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(displayName),
        )
        val uri = checkNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            ),
        )
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val saved = checkNotNull(resolver.openOutputStream(uri)).use { stream ->
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
        check(saved) { "Tutor screenshot could not be encoded" }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
    }

    protected fun longSession(): ConfirmedTutorSession {
        val baseSession = session()
        return baseSession.copy(
            questionDocument = baseSession.questionDocument.copy(
                document = baseSession.questionDocument.document.copy(
                    blocks = listOf(
                        ContentBlock.Paragraph(
                            id = "stem",
                            markdown = (1..80).joinToString("\n") { line ->
                                "第 $line 行：继续分析这道题的已知条件。"
                            },
                        ),
                    ),
                ),
            ),
        )
    }

    protected fun session(): ConfirmedTutorSession {
        val sourceAssetId = "asset-1"
        return ConfirmedTutorSession(
            sessionId = "session-1",
            draftId = "draft-1",
            draftRevisionNumber = 2,
            subject = "MATH",
            title = "求函数的单调区间",
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-1",
                    title = "求函数的单调区间",
                    blocks = listOf(
                        ContentBlock.Paragraph(
                            id = "stem",
                            markdown = "已知 f(x)=x³-3x，求它的单调区间。",
                        ),
                    ),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = "stem",
                        sourceAssetId = sourceAssetId,
                        sourceRegion = NormalizedSourceRegion(
                            left = 0.0,
                            top = 0.0,
                            right = 1.0,
                            bottom = 1.0,
                        ),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
            sourceImageUri = "file:///data/user/0/example/files/source.jpg",
            createdAtEpochMillis = 1_000,
            isSaved = false,
            errorBookEntryId = null,
        )
    }

    protected fun unavailableModelTasks(): ModelTaskRepository = object : ModelTaskRepository {
        override suspend fun capabilities() = ProviderCapabilitySnapshot(
            providerId = "unconfigured",
            providerDisplayName = "尚未配置模型",
            modelId = "unconfigured",
            supportedTasks = emptySet(),
            supportsImageInput = false,
            supportsStructuredOutput = false,
            supportsStreaming = false,
            executionLocation = ModelExecutionLocation.UNAVAILABLE,
        )

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> = flowOf(emptyList())

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> =
            error("Unavailable provider must not execute")
    }

    protected fun succeededExplanationOnlyModelTasks(
        session: ConfirmedTutorSession,
        solutionMarkdown: String = tutorOutput().plan.solutionMarkdown,
    ): RecordingModelTaskRepository {
        val provider = ProviderCapabilitySnapshot(
            providerId = "configured-provider",
            providerDisplayName = "已配置模型",
            modelId = "tutor-model-v1",
            supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN),
            supportsImageInput = false,
            supportsStructuredOutput = true,
            supportsStreaming = true,
        )
        val request = buildTutorPlanRequest(
            session = session,
            profile = StudyProfileOverview(),
            provider = provider,
            requestId = "tutor-plan-action-only",
            occurredAtEpochMillis = 100,
        )
        val output = tutorOutput().copy(
            plan = tutorOutput().plan.copy(
                diagnosticItem = null,
                solutionMarkdown = solutionMarkdown,
            ),
        )
        return RecordingModelTaskRepository(
            provider = provider,
            snapshot = ModelTaskSnapshot(
                taskId = "task-action-only",
                request = request,
                requestFingerprint = ModelTaskFingerprint.of(request),
                status = com.tingyun.smartmistakebook.core.model.ModelTaskStatus.SUCCEEDED,
                stateVersion = 1,
                stage = ModelTaskStage.COMPLETE,
                userMessage = "当前题讲解已准备",
                attemptCount = 1,
                provider = provider,
                output = output,
                createdAtEpochMillis = 100,
                updatedAtEpochMillis = 200,
            ),
        )
    }

    protected fun emptyInteractions(): TutorInteractionRepository = object : TutorInteractionRepository {
        override fun observe(sessionId: String): Flow<List<TutorTurnResponse>> = flowOf(emptyList())

        override suspend fun recordChoice(command: RecordTutorChoiceCommand): TutorTurnResponse =
            error("No interaction write expected")

        override suspend fun recordMove(command: RecordTutorMoveCommand): TutorTurnResponse =
            error("No interaction write expected")

        override suspend fun revealSolution(command: RevealTutorSolutionCommand): TutorTurnResponse =
            error("No interaction write expected")

        override suspend fun recordSolutionExposure(
            command: RecordTutorSolutionExposureCommand,
        ) = error("No exposure write expected")
    }

    /**
     * 会话替身（K1a 之后：正文 / 思考块 / 学生气泡 / 工具痕迹的唯一权威都在这条表上）。
     *
     * 两条与内核同口径的行为，缺任何一条都会让"界面上的气泡"与"库里的事实"分叉：
     *
     * 1. **写入按消息 id 幂等**：真实 DAO 的插入冲突按 id 命中即返回既有行，同一次派发重放
     *    多少次都只有一行。替身若按次追加，同一条逻辑操作写两遍就会攒出两条同 id 的行——
     *    而界面按 `messageId` 做列表键，那是直接崩；
     * 2. **可预置已落库的行**（[FakeTutorConversations.seed]）：页面重建 / 进程重开时读到的是
     *    这一份。"上一轮已经答过"这类用例只能靠它——正文不再从任务快照回落。
     *
     * @param recordedStudentMessages 可选：把每次 `appendStudentMessage` 的命令记下来，
     *   供"学生文字必须落库"的用例断言（写侧门控的引文核对依赖这些行）。
     */
    protected class FakeTutorConversations(
        private val recordedStudentMessages: MutableList<AppendTutorStudentMessageCommand> =
            mutableListOf(),
        private val recordedAssistantMessages: MutableList<AppendTutorAssistantMessageCommand> =
            mutableListOf(),
        seedMessages: List<TutorMessage> = emptyList(),
    ) : TutorConversationRepository {
        private val snapshot = MutableStateFlow(
            seedMessages.takeIf(List<TutorMessage>::isNotEmpty)?.let { messages ->
                TutorConversationSnapshot(
                    conversation = conversationRow(messages),
                    messages = messages,
                )
            },
        )

        /**
         * 写入顺序（与 `RecordingTutorInteractions.writeOrder` 同一手法）：钉住"学生文字在派发
         * **之前**就落库"（写侧门控的引文核对要读得到它），以及"助手行在这一轮成功之后才落"。
         */
        val writeOrder = mutableListOf<String>()

        fun recordEvent(event: String) {
            writeOrder += event
        }

        /** 当前落库的全部消息行（断言"这一轮的行真的存在"用它，不去读私有快照）。 */
        fun messages(): List<TutorMessage> = snapshot.value?.messages.orEmpty()

        /** 预置 / 追加**已落库**的消息行（非 suspend：装配期与 runOnIdle 里用，替身没有真 IO）。 */
        fun seed(messages: List<TutorMessage>) {
            val existing = snapshot.value?.messages.orEmpty()
            val merged = existing + messages.filterNot { candidate ->
                existing.any { it.messageId == candidate.messageId }
            }
            if (merged == existing) return
            snapshot.value = TutorConversationSnapshot(
                conversation = conversationRow(merged),
                messages = merged,
            )
        }

        /** 会话计数器分配的下一位（K1c 单数轴）：与真实 DAO 的 `last_turn_ordinal + 1` 同一口径。 */
        fun nextOrdinal(): Int = (snapshot.value?.messages?.maxOfOrNull(TutorMessage::ordinal) ?: 0) + 1

        private fun conversationRow(messages: List<TutorMessage>) = TutorConversation(
            conversationId = messages.firstOrNull()?.conversationId
                ?: TutorConversationIds.captured("session-1"),
            anchorKind = TutorConversationAnchorKind.EPHEMERAL_DRAFT,
            anchorId = "session-1",
            anchorRevisionId = "document-1:2",
            status = TutorConversationStatus.ACTIVE,
            title = null,
            // 建行时间 = 首条消息的时间（真实 DAO 就在那一刻建行），没有消息时给一个基准值。
            createdAtEpochMillis = messages.minOfOrNull(TutorMessage::createdAtEpochMillis) ?: 1_000,
            updatedAtEpochMillis = maxOf(
                messages.minOfOrNull(TutorMessage::createdAtEpochMillis) ?: 1_000,
                messages.maxOfOrNull(TutorMessage::createdAtEpochMillis) ?: 1_000,
            ),
            lastTurnOrdinal = messages.maxOfOrNull(TutorMessage::ordinal) ?: 0,
            messageCount = messages.size,
        )

        override fun observeRecent(
            limit: Int,
            area: String,
        ): Flow<List<TutorConversation>> = snapshot.map { current ->
            current?.conversation
                ?.takeIf { conversation -> conversation.area == area }
                ?.let(::listOf)
                .orEmpty()
        }

        override fun observeConversation(
            conversationId: String,
        ): Flow<TutorConversationSnapshot?> = snapshot

        override suspend fun createConversation(
            command: CreateTutorConversationCommand,
        ): TutorConversation {
                val conversation = TutorConversation(
                    conversationId = command.conversationId,
                    anchorKind = command.anchorKind,
                    anchorId = command.anchorId,
                    anchorRevisionId = command.anchorRevisionId,
                    status = TutorConversationStatus.ACTIVE,
                    title = command.title,
                    createdAtEpochMillis = command.createdAtEpochMillis,
                    updatedAtEpochMillis = command.createdAtEpochMillis,
                    lastTurnOrdinal = 0,
                )
                snapshot.value = TutorConversationSnapshot(conversation, emptyList())
                return conversation
            }

            override suspend fun appendStudentMessage(
                command: AppendTutorStudentMessageCommand,
            ): TutorMessage {
                recordedStudentMessages += command
                writeOrder += "student-message"
                // 幂等按消息 id（真实 DAO 的插入冲突口径）：同一次派发重放多少次都只有一行。
                snapshot.value?.messages
                    ?.firstOrNull { message -> message.messageId == command.messageId }
                    ?.let { existing -> return existing }
                // 与内核同一口径（K1c）：未给号时由会话计数器分配下一位。
                val ordinal = command.ordinal
                    ?: ((snapshot.value?.conversation?.lastTurnOrdinal ?: 0) + 1)
                val message = TutorMessage(
                    messageId = command.messageId,
                    conversationId = command.conversationId,
                    ordinal = ordinal,
                    role = TutorMessageRole.STUDENT,
                    bodyMarkdown = command.bodyMarkdown,
                    status = TutorMessageStatus.PERSISTED,
                    logicalOperationId = command.logicalOperationId,
                    replyToMessageId = null,
                    createdAtEpochMillis = command.createdAtEpochMillis,
                    completedAtEpochMillis = command.createdAtEpochMillis,
                    errorCode = null,
                )
                snapshot.value = ensureConversationRow().let { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            // 行的时间戳不许倒退（`TutorConversation` 的不变量）：派生行的时间
                            // 早于建行时以建行时间为准（真实 DAO 里这两列同样不允许倒挂）。
                            updatedAtEpochMillis = maxOf(
                                current.conversation.createdAtEpochMillis,
                                command.createdAtEpochMillis,
                            ),
                            lastTurnOrdinal = ordinal,
                        ),
                        messages = current.messages + message,
                    ).withMessageCount()
                }
                return message
            }

            override suspend fun appendAssistantMessage(
                command: AppendTutorAssistantMessageCommand,
            ): TutorMessage {
                recordedAssistantMessages += command
                writeOrder += "assistant-message"
                // 幂等按消息 id（同上）：同一条逻辑操作的助手行只有一行。
                snapshot.value?.messages
                    ?.firstOrNull { message -> message.messageId == command.messageId }
                    ?.let { existing -> return existing }
                // 与内核同一口径（K1c 单数轴）：未给号时由会话计数器分配下一位。
                val ordinal = command.ordinal
                    ?: ((snapshot.value?.conversation?.lastTurnOrdinal ?: 0) + 1)
                val message = TutorMessage(
                    messageId = command.messageId,
                    conversationId = command.conversationId,
                    ordinal = ordinal,
                    role = TutorMessageRole.ASSISTANT,
                    bodyMarkdown = command.bodyMarkdown,
                    thinkingMarkdown = command.thinkingMarkdown,
                    status = command.status,
                    logicalOperationId = command.logicalOperationId,
                    replyToMessageId = command.replyToMessageId,
                    createdAtEpochMillis = command.createdAtEpochMillis,
                    completedAtEpochMillis = command.completedAtEpochMillis,
                    errorCode = command.errorCode,
                    toolTraceJson = command.toolTraceJson,
                )
                snapshot.value = ensureConversationRow().let { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            updatedAtEpochMillis = maxOf(
                                current.conversation.createdAtEpochMillis,
                                command.completedAtEpochMillis ?: command.createdAtEpochMillis,
                            ),
                            lastTurnOrdinal = ordinal,
                        ),
                        messages = current.messages + message,
                    ).withMessageCount()
                }
                return message
            }

            /**
             * 写入前保证会话行在（K1b：行由第一条消息自己保证）。
             *
             * 零消息时 [TutorConversationSnapshot] 为 null；真实仓库在这一刻会按 `createConversation`
             * 建行，所以替身在这里补一行，之后 `messages.size` 与 `messageCount` 同步推进——
             * 历史列表按消息条数过滤（`tutorHistoryConversations`），行数对不上就会"明明说过话
             * 却在历史里找不到"。
             */
            private fun ensureConversationRow(): TutorConversationSnapshot =
                snapshot.value ?: TutorConversationSnapshot(
                    conversation = conversationRow(emptyList()),
                    messages = emptyList(),
                )

            private fun TutorConversationSnapshot.withMessageCount() = copy(
                conversation = conversation.copy(messageCount = messages.size),
            )

            override suspend fun updateMessageStatus(
                command: UpdateTutorMessageStatusCommand,
            ): TutorMessage {
                val current = snapshot.value ?: error("No tutor conversation")
                val existing = current.messages.first { it.messageId == command.messageId }
                val updated = existing.copy(
                    status = command.nextStatus,
                    bodyMarkdown = command.bodyMarkdown ?: existing.bodyMarkdown,
                    completedAtEpochMillis = command.completedAtEpochMillis,
                    errorCode = command.errorCode,
                )
                snapshot.value = current.copy(
                    messages = current.messages.map { message ->
                        if (message.messageId == command.messageId) updated else message
                    },
                )
                return updated
            }

            override suspend fun pauseConversation(
                command: PauseTutorConversationCommand,
            ): TutorConversation {
                val current = snapshot.value ?: error("No tutor conversation")
                val conversation = current.conversation.copy(
                    status = TutorConversationStatus.PAUSED,
                    updatedAtEpochMillis = command.occurredAtEpochMillis,
                )
                snapshot.value = current.copy(conversation = conversation)
                return conversation
            }

            override suspend fun archiveConversation(
                command: ArchiveTutorConversationCommand,
            ): TutorConversation {
                val current = snapshot.value ?: error("No tutor conversation")
                val conversation = current.conversation.copy(
                    status = TutorConversationStatus.ARCHIVED,
                    updatedAtEpochMillis = command.occurredAtEpochMillis,
                )
                snapshot.value = current.copy(conversation = conversation)
                return conversation
            }

            override suspend fun deleteConversation(
                command: DeleteTutorConversationCommand,
            ) {
                snapshot.value = null
            }

            override suspend fun saveDraft(
                command: SaveTutorConversationDraftCommand,
            ) {
                snapshot.value = snapshot.value?.let { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            studentDraft = command.draft,
                            updatedAtEpochMillis = command.occurredAtEpochMillis,
                        ),
                    )
                }
            }

            override suspend fun clearDraft(
                command: ClearTutorConversationDraftCommand,
            ) {
                snapshot.value = snapshot.value?.let { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            studentDraft = null,
                            updatedAtEpochMillis = command.occurredAtEpochMillis,
                        ),
                    )
                }
            }
        }

    /**
     * 会话替身工厂（见 [FakeTutorConversations]）。
     *
     * @param recordedStudentMessages 学生消息写入的命令流水（"学生文字必须落库"的用例断言它）。
     * @param seedMessages 预置的**已落库消息行**：正文 / 思考块 / 学生气泡的唯一来源。
     */
    protected fun emptyConversations(
        recordedStudentMessages: MutableList<AppendTutorStudentMessageCommand> = mutableListOf(),
        recordedAssistantMessages: MutableList<AppendTutorAssistantMessageCommand> = mutableListOf(),
        seedMessages: List<TutorMessage> = emptyList(),
    ): FakeTutorConversations = FakeTutorConversations(
        recordedStudentMessages = recordedStudentMessages,
        recordedAssistantMessages = recordedAssistantMessages,
        seedMessages = seedMessages,
    )

    /**
     * 一条**已落库**的助手行：消息 id 与逻辑操作都由请求 id 派生（与写侧
     * `tutorAssistantMessageId` / `recordTutorAssistantTurn` 同一式），时间线按
     * `logicalOperationId` 找它——所以替身造的行必须与生产写下的行逐位同形。
     */
    protected fun persistedAssistantTurn(
        requestId: String,
        bodyMarkdown: String,
        ordinal: Int,
        thinkingMarkdown: String? = null,
        toolTraceJson: String? = null,
        replyToMessageId: String? = null,
        conversationId: String = TutorConversationIds.captured("session-1"),
        createdAtEpochMillis: Long = ordinal.toLong(),
    ) = TutorMessage(
        messageId = tutorAssistantMessageId(requestId),
        conversationId = conversationId,
        ordinal = ordinal,
        role = TutorMessageRole.ASSISTANT,
        bodyMarkdown = bodyMarkdown,
        thinkingMarkdown = thinkingMarkdown,
        status = TutorMessageStatus.SUCCEEDED,
        logicalOperationId = requestId,
        replyToMessageId = replyToMessageId,
        createdAtEpochMillis = createdAtEpochMillis,
        completedAtEpochMillis = createdAtEpochMillis,
        errorCode = null,
        toolTraceJson = toolTraceJson,
    )

    /** 一条**已落库**的学生行（写侧 `tutorStudentMessageId` 同一式）。 */
    protected fun persistedStudentTurn(
        requestId: String,
        bodyMarkdown: String,
        ordinal: Int,
        conversationId: String = TutorConversationIds.captured("session-1"),
        createdAtEpochMillis: Long = ordinal.toLong(),
    ) = TutorMessage(
        messageId = tutorStudentMessageId(requestId),
        conversationId = conversationId,
        ordinal = ordinal,
        role = TutorMessageRole.STUDENT,
        bodyMarkdown = bodyMarkdown,
        status = TutorMessageStatus.PERSISTED,
        logicalOperationId = requestId,
        replyToMessageId = null,
        createdAtEpochMillis = createdAtEpochMillis,
        completedAtEpochMillis = createdAtEpochMillis,
        errorCode = null,
    )

    /**
     * 助手回复底部的**曝光锚点**（1dp 定位点；"底部进视口才落账"的判据挂在它上面）。
     *
     * 标签 = `tutor_solution_bottom_<轮次 stableId>`（见 `TutorSessionPanel.solutionBottomModifier`），
     * 回复轮的 stableId = `reply:<请求 id>`（见 `TutorConversationTimelineItem.Reply`）。
     */
    protected fun replyBottomAnchorTag(requestId: String): String =
        "tutor_solution_bottom_reply:$requestId"

    protected fun tutorResponse(choiceId: String): TutorTurnResponse {
        val output = tutorOutput()
        val item = requireNotNull(output.plan.diagnosticItem)
        val evaluation = item.evaluateChoice(choiceId)
        return TutorTurnResponse(
            sessionId = output.sessionId,
            questionDocumentId = output.questionDocumentId,
            revisionNumber = output.draftRevisionNumber,
            cycleOrdinal = output.cycleOrdinal,
            turnOrdinal = output.turnOrdinal,
            diagnosticStemMarkdown = item.stemMarkdown,
            selectedChoiceId = evaluation.choice.id,
            selectedChoiceMarkdown = evaluation.choice.markdown,
            selectionWasCorrect = evaluation.isCorrect,
            feedbackMarkdown = requireNotNull(evaluation.choice.feedbackMarkdown),
            submittedAtEpochMillis = 1_000,
            updatedAtEpochMillis = 1_000,
        )
    }

    protected fun actionResponse(
        requestedMove: TutorMoveType? = null,
        solutionRevealed: Boolean = false,
    ): TutorTurnResponse {
        val output = tutorOutput()
        return TutorTurnResponse(
            sessionId = output.sessionId,
            questionDocumentId = output.questionDocumentId,
            revisionNumber = output.draftRevisionNumber,
            cycleOrdinal = output.cycleOrdinal,
            turnOrdinal = output.turnOrdinal,
            diagnosticStemMarkdown = null,
            selectedChoiceId = null,
            selectedChoiceMarkdown = null,
            selectionWasCorrect = null,
            feedbackMarkdown = null,
            requestedMove = requestedMove,
            solutionRevealed = solutionRevealed,
            submittedAtEpochMillis = 1_000,
            updatedAtEpochMillis = 1_000,
        )
    }

    protected fun tutorOutput() = TutorPlanOutput(
        sessionId = "session-1",
        draftRevisionNumber = 2,
        questionDocumentId = "document-1",
        plan = TutorTurnPlan(
            openingMarkdown = "先判断导数的正负变化。",
            diagnosticItem = TutorAssessmentItem(
                id = "diagnostic-1",
                stemMarkdown = "导数先正后负时，原函数怎样变化？",
                choices = listOf(
                    TutorChoice("choice-1", "先增后减", "这个对应关系是正确的。"),
                    TutorChoice("choice-2", "先减后增", "你把正负关系反过来了。"),
                    TutorChoice("choice-3", "始终递增", "这里忽略了导数变号。"),
                ),
                correctChoiceId = "choice-1",
            ),
            solutionMarkdown = "完整主解法内容",
            alternateMethodMarkdown = "符号表替代解法内容",
            difficultyReasonMarkdown = "用于区分符号对应和变号遗漏。",
            targetedEvidenceLabels = emptyList(),
            inferredKnowledgeLabels = listOf("导数", "函数单调性"),
            suggestedMoves = listOf(
                TutorSuggestedMove(
                    id = "change",
                    label = "换一种思路",
                    type = TutorMoveType.CHANGE_REPRESENTATION,
                ),
                TutorSuggestedMove(
                    id = "solution",
                    label = "查看完整讲解",
                    type = TutorMoveType.REVEAL_SOLUTION,
                ),
            ),
        ),
        modelVersion = "model-v1",
    )

    protected inner class RestartCycleModelTaskRepository(
        session: ConfirmedTutorSession,
        exactStudentMessage: String,
    ) : ModelTaskRepository {
        private val provider = ProviderCapabilitySnapshot(
            providerId = "configured-provider",
            providerDisplayName = "已配置模型",
            modelId = "tutor-model-v1",
            supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN, ModelTaskKind.TUTOR_RESPOND),
            supportsImageInput = false,
            supportsStructuredOutput = true,
            supportsStreaming = true,
        )
        private val priorTurns = (1 until TutorPlanInput.MAX_TURNS).map { turnOrdinal ->
            TutorTurnHistoryEntry(
                turnOrdinal = turnOrdinal,
                diagnosticStemMarkdown = "第 $turnOrdinal 步应怎样继续？",
                selectedChoiceMarkdown = "先减后增",
                selectionWasCorrect = false,
                feedbackMarkdown = "继续围绕当前题修正这一步。",
                requestedMove = TutorMoveType.CHANGE_REPRESENTATION,
            )
        }
        private val planRequest = buildTutorPlanRequest(
            session = session,
            profile = StudyProfileOverview(),
            provider = provider,
            requestId = "tutor-plan-restart-source",
            occurredAtEpochMillis = 100,
            priorTurns = priorTurns,
        )
        private val planSnapshot = ModelTaskSnapshot(
            taskId = "task-tutor-plan-restart-source",
            request = planRequest,
            requestFingerprint = ModelTaskFingerprint.of(planRequest),
            status = ModelTaskStatus.SUCCEEDED,
            stateVersion = 1,
            stage = ModelTaskStage.COMPLETE,
            userMessage = "讲解已准备好",
            attemptCount = 1,
            provider = provider,
            output = tutorOutput().copy(turnOrdinal = TutorPlanInput.MAX_TURNS),
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 101,
        )
        private val respondRequest = buildTutorRespondRequest(
            question = session.toTutorQuestionContext(),
            profile = StudyProfileOverview(),
            provider = provider,
            requestId = "tutor-respond-stuck-step",
            occurredAtEpochMillis = 50,
            responseOrdinal = 1,
            cycleOrdinal = 1,
            turnOrdinal = TutorPlanInput.MAX_TURNS,
            studentMessage = exactStudentMessage,
            visibleTutorContextMarkdown = "正在讲配方法。",
            priorMessages = emptyList(),
        )
        private val respondSnapshot = ModelTaskSnapshot(
            taskId = "task-tutor-respond-stuck-step",
            request = respondRequest,
            requestFingerprint = ModelTaskFingerprint.of(respondRequest),
            status = ModelTaskStatus.CANCELLED,
            stateVersion = 1,
            stage = ModelTaskStage.COMPLETE,
            userMessage = "回复未完成",
            attemptCount = 1,
            provider = provider,
            output = null,
            createdAtEpochMillis = 50,
            updatedAtEpochMillis = 51,
        )
        val planRequests = mutableListOf<ModelTaskRequest>()

        override suspend fun capabilities(): ProviderCapabilitySnapshot = provider

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(
            listOf(planSnapshot, respondSnapshot).firstOrNull {
                it.request.requestId == requestId
            },
        )

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> = when (kind) {
            ModelTaskKind.TUTOR_PLAN -> flowOf(listOf(planSnapshot))
            ModelTaskKind.TUTOR_RESPOND -> flowOf(listOf(respondSnapshot))
            else -> flowOf(emptyList())
        }

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flow {
            planRequests += request
            emit(
                ModelTaskSnapshot(
                    taskId = "task:${request.requestId}",
                    request = request,
                    requestFingerprint = ModelTaskFingerprint.of(request),
                    status = ModelTaskStatus.RUNNING,
                    stateVersion = 1,
                    stage = ModelTaskStage.PREPARING,
                    userMessage = "正在继续讲解",
                    attemptCount = 1,
                    provider = provider,
                    createdAtEpochMillis = request.occurredAtEpochMillis,
                    updatedAtEpochMillis = request.occurredAtEpochMillis,
                ),
            )
        }
    }

    protected class FixedTutorInteractions(
        private val responses: List<TutorTurnResponse>,
    ) : TutorInteractionRepository {
        override fun observe(sessionId: String): Flow<List<TutorTurnResponse>> = flowOf(responses)

        override suspend fun recordChoice(command: RecordTutorChoiceCommand): TutorTurnResponse =
            error("No choice write expected")

        override suspend fun recordMove(command: RecordTutorMoveCommand): TutorTurnResponse =
            error("No move write expected")

        override suspend fun revealSolution(command: RevealTutorSolutionCommand): TutorTurnResponse =
            error("No reveal write expected")

        override suspend fun recordSolutionExposure(
            command: RecordTutorSolutionExposureCommand,
        ) = error("No exposure write expected")
    }

    protected class SuspendingExposureTutorInteractions : TutorInteractionRepository {
        val exposureCommands = mutableListOf<RecordTutorSolutionExposureCommand>()
        var cancellationCount: Int = 0

        override fun observe(sessionId: String): Flow<List<TutorTurnResponse>> = flowOf(emptyList())

        override suspend fun recordChoice(command: RecordTutorChoiceCommand): TutorTurnResponse =
            error("No choice write expected")

        override suspend fun recordMove(command: RecordTutorMoveCommand): TutorTurnResponse =
            error("No move write expected")

        override suspend fun revealSolution(command: RevealTutorSolutionCommand): TutorTurnResponse =
            TutorTurnResponse(
                sessionId = command.sessionId,
                questionDocumentId = command.questionDocumentId,
                revisionNumber = command.revisionNumber,
                cycleOrdinal = command.cycleOrdinal,
                turnOrdinal = command.turnOrdinal,
                diagnosticStemMarkdown = null,
                selectedChoiceId = null,
                selectedChoiceMarkdown = null,
                selectionWasCorrect = null,
                feedbackMarkdown = null,
                requestedMove = null,
                solutionRevealed = true,
                submittedAtEpochMillis = command.occurredAtEpochMillis,
                updatedAtEpochMillis = command.occurredAtEpochMillis,
            )

        override suspend fun recordSolutionExposure(command: RecordTutorSolutionExposureCommand) {
            exposureCommands += command
            try {
                awaitCancellation()
            } finally {
                cancellationCount += 1
            }
        }
    }

    protected class RecordingModelTaskRepository(
        private val provider: ProviderCapabilitySnapshot,
        private val snapshot: ModelTaskSnapshot,
    ) : ModelTaskRepository {
        var executeCalls: Int = 0

        override suspend fun capabilities(): ProviderCapabilitySnapshot = provider

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(
            snapshot.takeIf { it.request.requestId == requestId },
        )

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> = flowOf(
            listOf(snapshot).filter {
                it.request.input.subjectId == subjectId && it.request.input.kind == kind
            },
        )

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flow {
            executeCalls += 1
            error("An explanation-only action must not manufacture another tutor turn")
        }
    }

    protected inner class ChatModelTaskRepository(
        private val session: ConfirmedTutorSession,
        restoredPendingMessage: String? = null,
        restoredSucceededMessage: String? = null,
        private val restoredSucceededRevealsSolution: Boolean = false,
        private val restoredSucceededIntentDecision: TutorIntentDecision =
            TutorIntentDecision.currentQuestionDefault(),
        private val restoredSucceededSuggestedMoves: List<TutorSuggestedMove> = emptyList(),
        private val restoredCycleOrdinal: Int = 1,
        private val restoredTurnOrdinal: Int = 1,
        private val restoredFailureStatus: ModelTaskStatus? = null,
        private val restoredFailureCode: ModelFailureCode? = null,
        private val restoredFailureMessage: String = "请解释为什么要分区间",
        private val restoredRequestedMove: TutorMoveType? = null,
        private val restoredPlanStatus: ModelTaskStatus = ModelTaskStatus.SUCCEEDED,
        private val includeInitialPlan: Boolean = true,
        externalProvider: Boolean = false,
        currentCapabilities: ProviderCapabilitySnapshot? = null,
        private val holdRespondExecution: Boolean = false,
        /**
         * 会话替身（K1a）：**恢复出来的轮次**在真实世界里早就有自己的消息行，替身按生产的
         * 派生式把它们补上——正文 / 思考块 / 学生气泡只有消息行这一个来源，替身不给行，
         * 界面上那个气泡就根本不存在（"上一轮已经答过"的用例会直接断言不到）。
         *
         * 派发路径上的行不在这里写：那条路走的是内核自己的写入器（面板拿到同一个仓库）。
         */
        conversations: FakeTutorConversations? = null,
    ) : ModelTaskRepository {
        private val conversations: FakeTutorConversations? = conversations
        private val provider = ProviderCapabilitySnapshot(
            providerId = "configured-provider",
            providerDisplayName = "已配置模型",
            modelId = "tutor-model-v1",
            supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN, ModelTaskKind.TUTOR_RESPOND),
            supportsImageInput = false,
            supportsStructuredOutput = true,
            supportsStreaming = true,
            executionLocation = if (externalProvider) {
                ModelExecutionLocation.EXTERNAL_PROVIDER
            } else {
                ModelExecutionLocation.LOCAL_NO_EGRESS
            },
        )
        private var currentCapabilitiesOverride = currentCapabilities
        private val planRequest = buildTutorPlanRequest(
            session = session,
            profile = StudyProfileOverview(),
            provider = provider,
            requestId = "tutor-plan-chat",
            occurredAtEpochMillis = 100,
        )
        private val planSnapshot = ModelTaskSnapshot(
            taskId = "task-plan-chat",
            request = planRequest,
            requestFingerprint = ModelTaskFingerprint.of(planRequest),
            status = restoredPlanStatus,
            stateVersion = 1,
            stage = if (restoredPlanStatus == ModelTaskStatus.SUCCEEDED) {
                ModelTaskStage.COMPLETE
            } else {
                ModelTaskStage.PREPARING
            },
            userMessage = if (restoredPlanStatus == ModelTaskStatus.SUCCEEDED) {
                "讲解已准备好"
            } else {
                "正在准备讲解"
            },
            attemptCount = 1,
            provider = provider,
            output = tutorOutput().takeIf {
                restoredPlanStatus == ModelTaskStatus.SUCCEEDED
            },
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 200,
        )
        val planTasks = MutableStateFlow(
            if (includeInitialPlan) listOf(planSnapshot) else emptyList(),
        )
        val respondTasks = MutableStateFlow<List<ModelTaskSnapshot>>(emptyList())
        val planRequests = mutableListOf<ModelTaskRequest>()
        val respondRequests = mutableListOf<ModelTaskRequest>()
        var executePlanCalls: Int = 0
        var executeRespondCalls: Int = 0
        val capabilitySnapshot: ProviderCapabilitySnapshot
            get() = currentCapabilitiesOverride ?: provider

        fun useCapabilities(capabilities: ProviderCapabilitySnapshot) {
            currentCapabilitiesOverride = capabilities
        }

        init {
            require(
                listOf(
                    restoredPendingMessage != null,
                    restoredSucceededMessage != null,
                    restoredFailureStatus != null,
                ).count { it } <= 1,
            )
            require(
                restoredFailureStatus == null || restoredFailureStatus in setOf(
                    ModelTaskStatus.RETRYABLE_FAILURE,
                    ModelTaskStatus.PERMANENT_FAILURE,
                    ModelTaskStatus.CANCELLED,
                ),
            )
            require(restoredFailureCode == null || restoredFailureStatus != null)
            val restoredMessage = restoredPendingMessage ?: restoredSucceededMessage
                ?: restoredFailureStatus?.let { restoredFailureMessage }
            // 恢复出来的轮次对应的**已落库消息行**（K1a：正文只有这一个来源），见构造函数注释。
            val persistedTurns = mutableListOf<TutorMessage>()
            if (includeInitialPlan && restoredPlanStatus == ModelTaskStatus.SUCCEEDED) {
                // 首轮讲解的正文 = 计划的 openingMarkdown（写侧 `TutorPlanCommands` 同一处）。
                persistedTurns += persistedAssistantTurn(
                    requestId = planRequest.requestId,
                    bodyMarkdown = tutorOutput().plan.openingMarkdown,
                    ordinal = persistedTurns.size + 1,
                )
            }
            if (restoredMessage != null) {
                val restoredRequest = buildTutorRespondRequest(
                    question = session.toTutorQuestionContext(),
                    profile = StudyProfileOverview(),
                    provider = provider,
                    requestId = "tutor-respond-restored",
                    occurredAtEpochMillis = 300,
                    responseOrdinal = 1,
                    cycleOrdinal = restoredCycleOrdinal,
                    turnOrdinal = restoredTurnOrdinal,
                    studentMessage = restoredMessage,
                    visibleTutorContextMarkdown = "先判断导数的正负变化。",
                    priorMessages = emptyList(),
                    requestedMove = restoredRequestedMove,
                )
                val restoredInput = restoredRequest.input as TutorRespondInput
                val restoredSucceeded = restoredSucceededMessage != null
                val restoredStatus = when {
                    restoredSucceeded -> ModelTaskStatus.SUCCEEDED
                    restoredFailureStatus != null -> restoredFailureStatus
                    else -> ModelTaskStatus.WAITING_FOR_MODEL
                }
                respondTasks.value = listOf(
                    responseSnapshot(
                        request = restoredRequest,
                        status = restoredStatus,
                        output = if (restoredSucceeded) {
                            TutorRespondOutput(
                                sessionId = restoredInput.sessionId,
                                draftRevisionNumber = restoredInput.draftRevisionNumber,
                                questionDocumentId = restoredInput.questionDocument.id,
                                responseOrdinal = restoredInput.responseOrdinal,
                                cycleOrdinal = restoredInput.cycleOrdinal,
                                turnOrdinal = restoredInput.turnOrdinal,
                                messageMarkdown = "先看导数在临界点两侧的符号。",
                                solutionRevealed = restoredSucceededRevealsSolution,
                                // 答案暴露按绑定：暴露记录要求本轮确实有绑定题。替身直接构造
                                // 输出，所以在这里显式声明（真实路径由解析层写入校验过的绑定）。
                                boundQuestion = EXPOSURE_BOUND_QUESTION,
                                suggestedMoves = restoredSucceededSuggestedMoves,
                                intentDecision = restoredSucceededIntentDecision,
                                modelVersion = "model-v1",
                            )
                        } else {
                            null
                        },
                        stateVersion = if (restoredSucceeded) 3 else 1,
                    ),
                )
                // K1a：这一轮**已经写下的**消息行（学生行在派发前就落，助手行在这一轮成功时落）。
                // 取消 / 失败的轮次没有助手行——那一轮没有正文，只有账本上的状态。
                persistedTurns += persistedStudentTurn(
                    requestId = restoredRequest.requestId,
                    bodyMarkdown = restoredMessage,
                    ordinal = persistedTurns.size + 1,
                )
                if (restoredSucceeded) {
                    persistedTurns += persistedAssistantTurn(
                        requestId = restoredRequest.requestId,
                        bodyMarkdown = "先看导数在临界点两侧的符号。",
                        ordinal = persistedTurns.size + 1,
                        replyToMessageId = tutorStudentMessageId(restoredRequest.requestId),
                    )
                }
            }
            // 首轮讲解也是"已经答过"的一轮：它的正文同样只在消息行里。
            conversations?.seed(persistedTurns)
        }

        override suspend fun capabilities(): ProviderCapabilitySnapshot =
            currentCapabilitiesOverride ?: provider

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(
            (planTasks.value + respondTasks.value)
                .firstOrNull { it.request.requestId == requestId },
        )

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> = when (kind) {
            ModelTaskKind.TUTOR_PLAN -> planTasks
            ModelTaskKind.TUTOR_RESPOND -> respondTasks
            else -> flowOf(emptyList())
        }

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flow {
            when (val input = request.input) {
                is TutorPlanInput -> {
                    executePlanCalls += 1
                    planRequests += request
                    conversations?.recordEvent("dispatch")
                    val running = planSnapshot.copy(
                        request = request,
                        requestFingerprint = ModelTaskFingerprint.of(request),
                        status = ModelTaskStatus.RUNNING,
                        stateVersion = 2,
                        stage = ModelTaskStage.PREPARING,
                        userMessage = "正在准备讲解",
                        output = null,
                    )
                    planTasks.value = listOf(running)
                    emit(running)
                    val succeeded = running.copy(
                        status = ModelTaskStatus.SUCCEEDED,
                        stateVersion = 3,
                        stage = ModelTaskStage.COMPLETE,
                        userMessage = "讲解已准备好",
                        output = tutorOutput().copy(
                            sessionId = input.sessionId,
                            draftRevisionNumber = input.draftRevisionNumber,
                            questionDocumentId = input.questionDocument.id,
                            cycleOrdinal = input.cycleOrdinal,
                            turnOrdinal = input.turnOrdinal,
                        ),
                    )
                    planTasks.value = listOf(succeeded)
                    emit(succeeded)
                }
                is TutorRespondInput -> {
                    executeRespondCalls += 1
                    respondRequests += request
                    conversations?.recordEvent("dispatch")
                    val running = responseSnapshot(
                        request = request,
                        status = ModelTaskStatus.RUNNING,
                        output = null,
                        stateVersion = 2,
                    )
                    upsert(running)
                    emit(running)
                    if (holdRespondExecution) awaitCancellation()
                    val succeeded = responseSnapshot(
                        request = request,
                        status = ModelTaskStatus.SUCCEEDED,
                        output = TutorRespondOutput(
                            sessionId = input.sessionId,
                            draftRevisionNumber = input.draftRevisionNumber,
                            questionDocumentId = input.questionDocument.id,
                            responseOrdinal = input.responseOrdinal,
                            cycleOrdinal = input.cycleOrdinal,
                            turnOrdinal = input.turnOrdinal,
                            messageMarkdown = "先看导数在临界点两侧的符号。",
                            intentDecision = TutorIntentDecision.currentQuestionDefault(),
                            modelVersion = "model-v1",
                        ),
                        stateVersion = 3,
                    )
                    upsert(succeeded)
                    emit(succeeded)
                }
                else -> error("Chat repository only executes tutor tasks")
            }
        }

        fun publishRestoredSolutionReply(messageMarkdown: String = "先看完整推导。") {
            val current = respondTasks.value.last()
            val input = current.request.input as TutorRespondInput
            // 这一轮**此刻才成功**：助手行按生产的派生式补上（同一次派发只有一行，重复 publish 幂等）。
            conversations?.let { repository ->
                repository.seed(
                    listOf(
                        persistedAssistantTurn(
                            requestId = current.request.requestId,
                            bodyMarkdown = messageMarkdown,
                            ordinal = repository.nextOrdinal(),
                            replyToMessageId = tutorStudentMessageId(current.request.requestId),
                        ),
                    ),
                )
            }
            upsert(
                responseSnapshot(
                    request = current.request,
                    status = ModelTaskStatus.SUCCEEDED,
                    output = TutorRespondOutput(
                        sessionId = input.sessionId,
                        draftRevisionNumber = input.draftRevisionNumber,
                        questionDocumentId = input.questionDocument.id,
                        responseOrdinal = input.responseOrdinal,
                        cycleOrdinal = input.cycleOrdinal,
                        turnOrdinal = input.turnOrdinal,
                        messageMarkdown = messageMarkdown,
                        solutionRevealed = true,
                        boundQuestion = EXPOSURE_BOUND_QUESTION,
                        intentDecision = TutorIntentDecision.currentQuestionDefault(),
                        modelVersion = "model-v1",
                    ),
                    stateVersion = current.stateVersion + 1,
                ),
            )
        }

        fun publishLatestResponseFailure() {
            val current = respondTasks.value.last()
            upsert(
                responseSnapshot(
                    request = current.request,
                    status = ModelTaskStatus.RETRYABLE_FAILURE,
                    output = null,
                    stateVersion = current.stateVersion + 1,
                ),
            )
        }

        private fun upsert(snapshot: ModelTaskSnapshot) {
            respondTasks.value = respondTasks.value
                .filterNot { it.request.requestId == snapshot.request.requestId }
                .plus(snapshot)
        }

        private fun responseSnapshot(
            request: ModelTaskRequest,
            status: ModelTaskStatus,
            output: TutorRespondOutput?,
            stateVersion: Long,
        ) = ModelTaskSnapshot(
            taskId = "task:${request.requestId}",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = status,
            stateVersion = stateVersion,
            stage = if (status == ModelTaskStatus.SUCCEEDED) {
                ModelTaskStage.COMPLETE
            } else {
                ModelTaskStage.PREPARING
            },
            userMessage = if (status == ModelTaskStatus.SUCCEEDED) {
                "回复已准备好"
            } else {
                "正在回复"
            },
            attemptCount = 1,
            provider = request.egressManifest?.let { manifest ->
                currentCapabilitiesOverride?.takeIf { candidate ->
                    manifest.providerId == candidate.providerId &&
                        manifest.modelId == candidate.modelId &&
                        manifest.providerConfigurationVersion ==
                        candidate.providerConfigurationVersion
                }
            } ?: currentCapabilitiesOverride ?: provider,
            output = output,
            failure = when (status) {
                ModelTaskStatus.RETRYABLE_FAILURE -> ModelTaskFailure(
                    code = restoredFailureCode ?: ModelFailureCode.TIMEOUT,
                    message = "暂时没有完成",
                    retryable = true,
                )
                ModelTaskStatus.PERMANENT_FAILURE -> ModelTaskFailure(
                    code = restoredFailureCode ?: ModelFailureCode.PROVIDER_REJECTED_INPUT,
                    message = "这次无法完成",
                    retryable = false,
                )
                else -> null
            },
            createdAtEpochMillis = request.occurredAtEpochMillis,
            updatedAtEpochMillis = request.occurredAtEpochMillis + stateVersion,
        )
    }

    protected class RecordingTutorInteractions : TutorInteractionRepository {
        val responses = MutableStateFlow<List<TutorTurnResponse>>(emptyList())
        var moveCommand: RecordTutorMoveCommand? = null
        var revealCommand: RevealTutorSolutionCommand? = null
        val revealCommands = mutableListOf<RevealTutorSolutionCommand>()
        val exposureCommands = mutableListOf<RecordTutorSolutionExposureCommand>()
        val recordedExposureKeys = linkedSetOf<String>()
        val writeOrder = mutableListOf<String>()

        override fun observe(sessionId: String): Flow<List<TutorTurnResponse>> = responses

        override suspend fun recordChoice(command: RecordTutorChoiceCommand): TutorTurnResponse =
            TutorTurnResponse(
                sessionId = command.sessionId,
                questionDocumentId = command.questionDocumentId,
                revisionNumber = command.revisionNumber,
                cycleOrdinal = command.cycleOrdinal,
                turnOrdinal = command.turnOrdinal,
                diagnosticStemMarkdown = command.diagnosticStemMarkdown,
                selectedChoiceId = command.selectedChoiceId,
                selectedChoiceMarkdown = command.selectedChoiceMarkdown,
                selectionWasCorrect = command.selectionWasCorrect,
                feedbackMarkdown = command.feedbackMarkdown,
                requestedMove = null,
                solutionRevealed = false,
                submittedAtEpochMillis = command.occurredAtEpochMillis,
                updatedAtEpochMillis = command.occurredAtEpochMillis,
            ).also { response ->
                responses.value = listOf(response)
            }

        override suspend fun recordMove(command: RecordTutorMoveCommand): TutorTurnResponse {
            moveCommand = command
            val current = responses.value.singleOrNull { response ->
                response.sessionId == command.sessionId &&
                    response.questionDocumentId == command.questionDocumentId &&
                    response.revisionNumber == command.revisionNumber &&
                    response.cycleOrdinal == command.cycleOrdinal &&
                    response.turnOrdinal == command.turnOrdinal
            }
            return (
                current?.copy(
                    requestedMove = command.requestedMove,
                    updatedAtEpochMillis = maxOf(
                        current.updatedAtEpochMillis,
                        command.occurredAtEpochMillis,
                    ),
                ) ?: action(command)
                ).also { response -> responses.value = listOf(response) }
        }

        override suspend fun revealSolution(command: RevealTutorSolutionCommand): TutorTurnResponse {
            revealCommand = command
            revealCommands += command
            writeOrder += "reveal"
            val revealed = revealResponse(
                sessionId = command.sessionId,
                questionDocumentId = command.questionDocumentId,
                revisionNumber = command.revisionNumber,
                cycleOrdinal = command.cycleOrdinal,
                turnOrdinal = command.turnOrdinal,
                occurredAtEpochMillis = command.occurredAtEpochMillis,
            )
            responses.value = listOf(revealed)
            return revealed
        }

        override suspend fun recordSolutionExposure(command: RecordTutorSolutionExposureCommand) {
            if (command.surfaceKind == TutorAnswerExposureSurfaceKind.PLAN_SOLUTION) {
                require(
                    responses.value.any { response ->
                        response.sessionId == command.sessionId &&
                            response.questionDocumentId == command.questionDocumentId &&
                            response.revisionNumber == command.revisionNumber &&
                            response.cycleOrdinal == command.cycleOrdinal &&
                            response.turnOrdinal == command.turnOrdinal &&
                            response.solutionRevealed
                    },
                ) { "A plan exposure requires the exact solution reveal first" }
            }
            exposureCommands += command
            writeOrder += "exposure"
            recordedExposureKeys += "${command.sessionId}:${command.cycleOrdinal}:${command.turnOrdinal}"
        }

        private fun revealResponse(
            sessionId: String,
            questionDocumentId: String,
            revisionNumber: Int,
            cycleOrdinal: Int,
            turnOrdinal: Int,
            occurredAtEpochMillis: Long,
        ): TutorTurnResponse {
            val current = responses.value.singleOrNull()
            return current?.copy(
                solutionRevealed = true,
                updatedAtEpochMillis = maxOf(current.updatedAtEpochMillis, occurredAtEpochMillis),
            ) ?: TutorTurnResponse(
                sessionId = sessionId,
                questionDocumentId = questionDocumentId,
                revisionNumber = revisionNumber,
                cycleOrdinal = cycleOrdinal,
                turnOrdinal = turnOrdinal,
                diagnosticStemMarkdown = null,
                selectedChoiceId = null,
                selectedChoiceMarkdown = null,
                selectionWasCorrect = null,
                feedbackMarkdown = null,
                solutionRevealed = true,
                submittedAtEpochMillis = occurredAtEpochMillis,
                updatedAtEpochMillis = occurredAtEpochMillis,
            )
        }

        private fun action(command: RecordTutorMoveCommand) = TutorTurnResponse(
            sessionId = command.sessionId,
            questionDocumentId = command.questionDocumentId,
            revisionNumber = command.revisionNumber,
            cycleOrdinal = command.cycleOrdinal,
            turnOrdinal = command.turnOrdinal,
            diagnosticStemMarkdown = null,
            selectedChoiceId = null,
            selectedChoiceMarkdown = null,
            selectionWasCorrect = null,
            feedbackMarkdown = null,
            requestedMove = command.requestedMove,
            submittedAtEpochMillis = command.occurredAtEpochMillis,
            updatedAtEpochMillis = command.occurredAtEpochMillis,
        )
    }
}
