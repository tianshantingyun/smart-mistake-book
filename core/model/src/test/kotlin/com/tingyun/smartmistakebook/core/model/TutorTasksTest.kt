package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorTasksTest {
    @Test
    fun tutorRequestAndOutputRoundTripWithoutGrantingMasteryAuthority() {
        val request = request()
        val output = output()

        val decodedRequest = ModelTaskCodec.decodeRequest(ModelTaskCodec.encodeRequest(request))
        val decodedOutput = ModelTaskCodec.decodeOutput(ModelTaskCodec.encodeOutput(output))

        assertEquals(request, decodedRequest)
        assertEquals(output, decodedOutput)
        assertTrue(ModelTaskCompletionValidator.validate(request, output).isEmpty())
        assertTrue(requireNotNull(output.plan.diagnosticItem).knowledgeNodeIds.isEmpty())
    }

    @Test
    fun explanationOnlyTurnRoundTripsWithoutAnArtificialChoiceBlock() {
        val explanationOnly = output().copy(
            plan = output().plan.copy(diagnosticItem = null),
        )

        assertEquals(
            explanationOnly,
            ModelTaskCodec.decodeOutput(ModelTaskCodec.encodeOutput(explanationOnly)),
        )
        assertTrue(ModelTaskCompletionValidator.validate(request(), explanationOnly).isEmpty())
    }

    @Test
    fun currentQuestionTextResponseRoundTripsWithoutADiagnosticChoice() {
        val request = respondRequest(
            input = respondInput().copy(studentMessage = "请直接告诉我这道题的完整答案"),
        )
        val output = respondOutput().copy(
            solutionRevealed = true,
            intentDecision = TutorIntentDecision(
                intent = TutorMessageIntent.CURRENT_QUESTION_HELP,
                confidence = 1.0,
                explicitActionRequest = false,
                memoryPreference = TutorMemoryPreference.UNCHANGED,
                requestedLocalCapability = TutorRequestedLocalCapability.NONE,
            ),
        )

        assertEquals(request, ModelTaskCodec.decodeRequest(ModelTaskCodec.encodeRequest(request)))
        assertEquals(output, ModelTaskCodec.decodeOutput(ModelTaskCodec.encodeOutput(output)))
        assertTrue(ModelTaskCompletionValidator.validate(request, output).isEmpty())
        assertTrue(output.solutionRevealed)
        assertTrue(output.suggestedMoves.isEmpty())
    }

    @Test
    fun respondThinkingMarkdownRoundTripsAndValidates() {
        val withThinking = respondOutput().copy(
            thinkingMarkdown = "先看导数为正是否意味着函数始终上升。",
        )
        assertEquals(
            withThinking,
            ModelTaskCodec.decodeOutput(ModelTaskCodec.encodeOutput(withThinking)),
        )
        assertTrue(ModelTaskCompletionValidator.validate(respondRequest(), withThinking).isEmpty())
        assertEquals("先看导数为正是否意味着函数始终上升。", withThinking.thinkingMarkdown)
    }

    @Test
    fun respondThinkingRejectsActiveContentAndOversizedText() {
        assertTrue(
            runCatching {
                respondOutput().copy(thinkingMarkdown = "先算一下<script>run()</script>")
            }.isFailure,
        )
        assertTrue(
            runCatching {
                respondOutput().copy(thinkingMarkdown = "详".repeat(TutorTurnPlan.MAX_THINKING_CHARS + 1))
            }.isFailure,
        )
        // 空 thinking 视为未提供，允许。
        assertEquals(null, respondOutput().thinkingMarkdown)
    }

    @Test
    fun legacyRespondWithoutThinkingDecodesToNullThinking() {
        // 旧产出（无 thinkingMarkdown 键）应向后兼容解码，thinking 填默认 null。
        val encoded = ModelTaskCodec.encodeOutput(respondOutput())
        val legacy = encoded.replace("\"thinkingMarkdown\":null,", "")
        assertTrue("测试前提：编码应含 thinkingMarkdown 键", encoded != legacy)
        val decoded = ModelTaskCodec.decodeOutput(legacy) as TutorRespondOutput
        assertEquals(null, decoded.thinkingMarkdown)
    }

    @Test
    fun attachedImagesRoundTripThroughTheOutputCodec() {
        val withImage = respondOutput().copy(
            attachedImages = listOf(
                AttachedImage(
                    imageId = "process-1",
                    kind = AttachedImageKind.GENERATE_PROCESS,
                    description = "用数轴标注 f'(x) 在 (−∞,0) 为正、(0,∞) 为负，展示导数符号区间。",
                    accessibilityText = "数轴上导数符号区间的标注图",
                ),
                AttachedImage(
                    imageId = "redraw-1",
                    kind = AttachedImageKind.REDRAW_PROBLEM,
                    description = "重绘题目原图，去除手写笔迹。",
                ),
            ),
        )
        assertEquals(
            withImage,
            ModelTaskCodec.decodeOutput(ModelTaskCodec.encodeOutput(withImage)),
        )
        assertTrue(ModelTaskCompletionValidator.validate(respondRequest(), withImage).isEmpty())
        assertEquals(2, withImage.attachedImages.size)
    }

    @Test
    fun attachedImageRejectsActiveContentAndOversizedDescription() {
        assertTrue(
            runCatching {
                respondOutput().copy(
                    attachedImages = listOf(
                        AttachedImage(
                            imageId = "bad-1",
                            kind = AttachedImageKind.GENERATE_PROCESS,
                            description = "画图<script>run()</script>",
                        ),
                    ),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                respondOutput().copy(
                    attachedImages = listOf(
                        AttachedImage(
                            imageId = "bad-2",
                            kind = AttachedImageKind.GENERATE_PROCESS,
                            description = "描".repeat(AttachedImage.MAX_ATTACHED_DESC_CHARS + 1),
                        ),
                    ),
                )
            }.isFailure,
        )
    }

    @Test
    fun attachedImagesNeverExceedTheReplyBudget() {
        val overflow = List(AttachedImage.MAX_ATTACHED_IMAGES + 1) { index ->
            AttachedImage(
                imageId = "img-$index",
                kind = AttachedImageKind.GENERATE_PROCESS,
                description = "第 $index 张图",
            )
        }
        assertTrue(runCatching { respondOutput().copy(attachedImages = overflow) }.isFailure)
    }

    @Test
    fun legacyRespondWithoutAttachedImagesDecodesToEmpty() {
        val encoded = ModelTaskCodec.encodeOutput(respondOutput())
        val legacy = encoded.replace("\"attachedImages\":[],", "")
        assertTrue("测试前提：编码应含 attachedImages 键", encoded != legacy)
        val decoded = ModelTaskCodec.decodeOutput(legacy) as TutorRespondOutput
        assertEquals(emptyList<AttachedImage>(), decoded.attachedImages)
    }

    @Test
    fun planThinkingMarkdownRoundTripsAndValidates() {
        val withThinking = output().copy(
            plan = output().plan.copy(thinkingMarkdown = "这题先确认学生对导数符号的理解。"),
        )
        assertEquals(
            withThinking,
            ModelTaskCodec.decodeOutput(ModelTaskCodec.encodeOutput(withThinking)),
        )
        assertTrue(ModelTaskCompletionValidator.validate(request(), withThinking).isEmpty())
        assertEquals("这题先确认学生对导数符号的理解。", withThinking.plan.thinkingMarkdown)
    }

    @Test
    fun legacyTextResponseDefaultsToNoSolutionExposure() {
        val encoded = ModelTaskCodec.encodeOutput(respondOutput())
        val legacy = encoded
            .replace(",\"cycleOrdinal\":1", "")
            .replace(",\"turnOrdinal\":1", "")
            .replace(",\"solutionRevealed\":false", "")

        assertTrue(encoded != legacy)
        val decoded = ModelTaskCodec.decodeOutput(legacy) as TutorRespondOutput
        assertEquals(1, decoded.cycleOrdinal)
        assertEquals(1, decoded.turnOrdinal)
        assertEquals(false, decoded.solutionRevealed)
    }

    @Test
    fun legacyTutorPlanDefaultsToNoEarlierStudentMessages() {
        val encoded = ModelTaskCodec.encodeRequest(request())
        val legacy = encoded.replace(",\"priorCycleStudentMessages\":[]", "")

        assertTrue(encoded != legacy)
        val decoded = ModelTaskCodec.decodeRequest(legacy).input as TutorPlanInput
        assertTrue(decoded.priorCycleStudentMessages.isEmpty())
    }

    @Test
    fun studentMessagePreservesOrdinaryMathCodeAndLinksButRejectsUnsafeControls() {
        val exact = "  x < 3 时为什么？\n参考 https://example.com 和 `f'(x)`  "

        assertEquals(exact, respondInput().copy(studentMessage = exact).studentMessage)
        assertEquals(
            exact,
            TutorChatHistoryEntry(exact, "仍然只解释当前题。").studentMessage,
        )
        assertTrue(
            runCatching { respondInput().copy(studentMessage = "不可见\u202E控制") }.isFailure,
        )
    }

    @Test
    fun tutorResponseCompletionRequiresExactPersistedContextIdentity() {
        val mismatches = listOf(
            respondOutput().copy(sessionId = "another-session"),
            respondOutput().copy(draftRevisionNumber = 3),
            respondOutput().copy(questionDocumentId = "another-question"),
            respondOutput().copy(responseOrdinal = 4),
            respondOutput().copy(cycleOrdinal = 2),
            respondOutput().copy(turnOrdinal = 2),
        )

        mismatches.forEach { mismatch ->
            assertEquals(
                listOf(ModelTaskCompletionIssueCode.TUTOR_CONTEXT_MISMATCH),
                ModelTaskCompletionValidator.validate(respondRequest(), mismatch).map { it.code },
            )
        }
    }

    @Test
    fun nonQuestionIntentCannotSmuggleTeachingOrAnswerContent() {
        val lookupIntent = TutorIntentDecision(
            intent = TutorMessageIntent.MISTAKE_NOTEBOOK_LOOKUP,
            confidence = 0.92,
            explicitActionRequest = true,
            memoryPreference = TutorMemoryPreference.UNCHANGED,
            requestedLocalCapability = TutorRequestedLocalCapability.READ_MISTAKE_NOTEBOOK,
        )
        val unsafe = respondOutput().copy(
            solutionRevealed = true,
            suggestedMoves = listOf(
                TutorSuggestedMove(
                    "respond-1",
                    "继续关键一步",
                    TutorMoveType.DEEPEN_REASONING,
                ),
            ),
            intentDecision = lookupIntent,
        )

        assertEquals(
            listOf(ModelTaskCompletionIssueCode.TUTOR_INTENT_BOUNDARY_VIOLATION),
            ModelTaskCompletionValidator.validate(respondRequest(), unsafe).map { it.code },
        )
    }

    @Test
    fun completeAnswerRequiresExplicitStudentAuthorityBeforeTaskCompletion() {
        val unauthorizedInput = respondInput().copy(
            studentMessage = "我觉得这个答案不对",
            requestedMove = null,
        )
        val solution = respondOutput().copy(
            solutionRevealed = true,
            intentDecision = TutorIntentDecision.currentQuestionDefault(),
        )

        assertEquals(
            listOf(ModelTaskCompletionIssueCode.TUTOR_INTENT_BOUNDARY_VIOLATION),
            ModelTaskCompletionValidator.validate(
                respondRequest(unauthorizedInput),
                solution,
            ).map { it.code },
        )

        val explicitTextInput = unauthorizedInput.copy(studentMessage = "请告诉我答案")
        assertTrue(
            ModelTaskCompletionValidator.validate(
                respondRequest(explicitTextInput),
                solution,
            ).isEmpty(),
        )

        val explicitButtonInput = unauthorizedInput.copy(
            studentMessage = "继续",
            requestedMove = TutorMoveType.REVEAL_SOLUTION,
        )
        assertTrue(
            ModelTaskCompletionValidator.validate(
                respondRequest(explicitButtonInput),
                solution,
            ).isEmpty(),
        )
    }

    @Test
    fun intentCannotRequestALocalCapabilityWithoutAnExplicitMatchingGoal() {
        assertTrue(
            runCatching {
                TutorIntentDecision(
                    intent = TutorMessageIntent.CASUAL_CONVERSATION,
                    confidence = 0.95,
                    explicitActionRequest = true,
                    memoryPreference = TutorMemoryPreference.UNCHANGED,
                    requestedLocalCapability =
                        TutorRequestedLocalCapability.READ_MISTAKE_NOTEBOOK,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                TutorIntentDecision(
                    intent = TutorMessageIntent.MISTAKE_NOTEBOOK_LOOKUP,
                    confidence = 0.95,
                    explicitActionRequest = false,
                    memoryPreference = TutorMemoryPreference.UNCHANGED,
                    requestedLocalCapability =
                        TutorRequestedLocalCapability.READ_MISTAKE_NOTEBOOK,
                )
            }.isFailure,
        )
    }

    @Test
    fun tutorResponseTextHistoryAndMoveBudgetsAreStrict() {
        assertTrue(
            runCatching {
                respondInput().copy(
                    studentMessage = "问".repeat(TutorRespondInput.MAX_STUDENT_MESSAGE_CHARS + 1),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                respondInput().copy(
                    priorMessages = List(TutorRespondInput.MAX_PRIOR_MESSAGES + 1) {
                        TutorChatHistoryEntry("为什么？", "仍然只解释当前题。")
                    },
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                respondInput().copy(
                    priorMessages = List(2) {
                        TutorChatHistoryEntry(
                            studentMessage = "问".repeat(TutorRespondInput.MAX_STUDENT_MESSAGE_CHARS),
                            assistantMarkdown = "解".repeat(TutorRespondOutput.MAX_MESSAGE_MARKDOWN_CHARS),
                        )
                    },
                )
            }.isFailure,
        )
        assertTrue(
            runCatching { respondOutput().copy(messageMarkdown = "`不允许的代码`") }.isFailure,
        )
        assertTrue(
            runCatching { respondOutput().copy(messageMarkdown = "查看检索召回结果") }.isFailure,
        )
        assertTrue(
            runCatching {
                output().copy(plan = output().plan.copy(openingMarkdown = "先看原子知识关系"))
            }.isFailure,
        )
        assertTrue(
            runCatching {
                TutorSuggestedMove(
                    id = "internal-label",
                    label = "查看检索召回",
                    type = TutorMoveType.CONNECT_KNOWLEDGE,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                output().copy(
                    plan = output().plan.copy(inferredKnowledgeLabels = listOf("学习投影")),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                respondOutput().copy(
                    suggestedMoves = listOf(
                        TutorSuggestedMove("respond-1", "继续关键一步", TutorMoveType.DEEPEN_REASONING),
                        TutorSuggestedMove("respond-2", "针对当前误区", TutorMoveType.TARGET_MISCONCEPTION),
                        TutorSuggestedMove("respond-3", "换一种表示", TutorMoveType.CHANGE_REPRESENTATION),
                        TutorSuggestedMove("respond-4", "联系当前知识", TutorMoveType.CONNECT_KNOWLEDGE),
                    ),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                respondOutput().copy(
                    suggestedMoves = listOf(
                        TutorSuggestedMove("duplicate-1", "继续关键一步", TutorMoveType.DEEPEN_REASONING),
                        TutorSuggestedMove("duplicate-2", "再继续一步", TutorMoveType.DEEPEN_REASONING),
                    ),
                )
            }.isFailure,
        )
    }

    @Test
    fun contextualMovesMayBeAbsentAndNeverExceedThree() {
        assertTrue(output().plan.suggestedMoves.isEmpty())
        assertTrue(
            runCatching {
                output().plan.copy(
                    suggestedMoves = listOf(
                        TutorSuggestedMove("a", "看关键条件", TutorMoveType.DEEPEN_REASONING),
                        TutorSuggestedMove("b", "换成图像", TutorMoveType.CHANGE_REPRESENTATION),
                        TutorSuggestedMove("c", "联系定义", TutorMoveType.CONNECT_KNOWLEDGE),
                        TutorSuggestedMove("d", "查看讲解", TutorMoveType.REVEAL_SOLUTION),
                    ),
                )
            }.isFailure,
        )
    }

    @Test
    fun meaningfulTwoChoiceInteractionIsAllowedWhenTheCurrentQuestionNeedsIt() {
        val twoChoicePlan = output().plan.copy(
            diagnosticItem = requireNotNull(output().plan.diagnosticItem).copy(
                choices = requireNotNull(output().plan.diagnosticItem).choices.take(2),
            ),
        )

        assertEquals(2, requireNotNull(twoChoicePlan.diagnosticItem).choices.size)
    }

    @Test
    fun actionOnlyConversationMemoryRetainsTheDirectTeachingBoundary() {
        val memory = TutorConversationMemory(
            completedCycleCount = 1,
            answeredTurnCount = 0,
            correctChoiceCount = 0,
            lastRequestedMove = TutorMoveType.CHANGE_REPRESENTATION,
            solutionWasRevealed = true,
        )

        assertEquals(0, memory.answeredTurnCount)
        assertEquals(null, memory.lastFeedbackMarkdown)
        assertEquals(TutorMoveType.CHANGE_REPRESENTATION, memory.lastRequestedMove)
        assertTrue(memory.solutionWasRevealed)
        val laterRequest = request().copy(
            input = input().copy(cycleOrdinal = 2, priorConversationMemory = memory),
        )
        assertEquals(
            laterRequest,
            ModelTaskCodec.decodeRequest(ModelTaskCodec.encodeRequest(laterRequest)),
        )
    }

    @Test
    fun laterCyclePreservesExactEarlierStudentMessagesInOrder() {
        val memory = TutorConversationMemory(
            completedCycleCount = 1,
            answeredTurnCount = 0,
            correctChoiceCount = 0,
            solutionWasRevealed = true,
        )
        val exactMessages = listOf(
            "  我卡在配方法第二步\n",
            "为什么这里要同时加上 4？  ",
        )
        val laterRequest = request().copy(
            input = input().copy(
                cycleOrdinal = 2,
                priorConversationMemory = memory,
                priorCycleStudentMessages = exactMessages,
            ),
        )

        val decoded = ModelTaskCodec.decodeRequest(
            ModelTaskCodec.encodeRequest(laterRequest),
        ).input as TutorPlanInput

        assertEquals(exactMessages, decoded.priorCycleStudentMessages)
    }

    @Test
    fun earlierStudentMessageBudgetsAndCycleBoundaryAreStrict() {
        val memory = TutorConversationMemory(
            completedCycleCount = 1,
            answeredTurnCount = 0,
            correctChoiceCount = 0,
            solutionWasRevealed = true,
        )
        assertTrue(
            runCatching {
                input().copy(priorCycleStudentMessages = listOf("不应出现在第一轮"))
            }.isFailure,
        )
        assertTrue(
            runCatching {
                input().copy(
                    cycleOrdinal = 2,
                    priorConversationMemory = memory,
                    priorCycleStudentMessages = List(
                        TutorPlanInput.MAX_PRIOR_CYCLE_STUDENT_MESSAGES + 1,
                    ) { "卡点-$it" },
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                input().copy(
                    cycleOrdinal = 2,
                    priorConversationMemory = memory,
                    priorCycleStudentMessages = List(6) {
                        "问".repeat(TutorRespondInput.MAX_STUDENT_MESSAGE_CHARS)
                    },
                )
            }.isFailure,
        )
    }

    @Test
    fun conversationMemoryRequiresFeedbackExactlyWhenAChoiceWasAnswered() {
        assertTrue(
            runCatching {
                TutorConversationMemory(
                    completedCycleCount = 1,
                    answeredTurnCount = 0,
                    correctChoiceCount = 0,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                TutorConversationMemory(
                    completedCycleCount = 1,
                    answeredTurnCount = 1,
                    correctChoiceCount = 1,
                    solutionWasRevealed = true,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                TutorConversationMemory(
                    completedCycleCount = 1,
                    answeredTurnCount = 0,
                    correctChoiceCount = 0,
                    lastFeedbackMarkdown = "不能伪造选择反馈",
                    solutionWasRevealed = true,
                )
            }.isFailure,
        )
    }

    @Test
    fun modelCannotClaimEvidenceThatWasNotDisclosed() {
        val invalid = output().copy(
            plan = output().plan.copy(targetedEvidenceLabels = listOf("未发送的知识点")),
        )

        assertEquals(
            listOf(ModelTaskCompletionIssueCode.MODEL_CLAIMED_UNDISCLOSED_EVIDENCE),
            ModelTaskCompletionValidator.validate(request(), invalid).map { it.code },
        )
    }

    @Test
    fun modelCannotUseMasteredEvidenceAsTheDiagnosticTarget() {
        val request = request().copy(
            input = input().copy(
                relevantLearningEvidence = listOf(
                    TutorKnowledgeEvidence(
                        "node-linear",
                        "一次函数基础",
                        TutorEvidenceLevel.MASTERED,
                        0.92,
                    ),
                ),
            ),
        )
        val output = output().copy(
            plan = output().plan.copy(targetedEvidenceLabels = listOf("一次函数基础")),
        )

        assertEquals(
            listOf(ModelTaskCompletionIssueCode.MODEL_TARGETED_MASTERED_EVIDENCE),
            ModelTaskCompletionValidator.validate(request, output).map { it.code },
        )
    }

    @Test
    fun tutorEgressAllowsOnlyConfirmedDocumentAndRelevantSummary() {
        val request = request()
        val provider = provider()

        val execution = ModelEgressPolicy.authorize(request, provider, 1)

        assertTrue(execution.permit is ModelExecutionPermit.External)
        assertTrue(requireNotNull(request.egressManifest).assets.isEmpty())
        assertEquals(
            ModelEgressManifest.TUTOR_PLAN_DISCLOSURE,
            request.egressManifest.disclosedData,
        )
    }

    @Test
    fun followUpTurnCarriesOnlyBoundedContiguousConversationEvidence() {
        val history = TutorTurnHistoryEntry(
            turnOrdinal = 1,
            diagnosticStemMarkdown = "若导数先正后负，原函数怎样变化？",
            selectedChoiceMarkdown = "先减后增",
            selectionWasCorrect = false,
            feedbackMarkdown = "你把导数正负与增减的对应关系反过来了。",
            requestedMove = TutorMoveType.CHANGE_REPRESENTATION,
        )
        val request = request().copy(
            input = input().copy(turnOrdinal = 2, priorTurns = listOf(history)),
        )
        val output = output().copy(turnOrdinal = 2)

        assertEquals(
            request,
            ModelTaskCodec.decodeRequest(ModelTaskCodec.encodeRequest(request)),
        )
        assertTrue(ModelTaskCompletionValidator.validate(request, output).isEmpty())
    }

    @Test
    fun outputCannotJumpToAnotherConversationTurn() {
        assertEquals(
            listOf(ModelTaskCompletionIssueCode.TUTOR_CONTEXT_MISMATCH),
            ModelTaskCompletionValidator.validate(
                request(),
                output().copy(turnOrdinal = 2),
            ).map { it.code },
        )
    }

    @Test
    fun outputCannotJumpToAnotherConversationCycle() {
        val memory = TutorConversationMemory(
            completedCycleCount = 1,
            answeredTurnCount = 4,
            correctChoiceCount = 2,
            lastFeedbackMarkdown = "定义域已稳定，符号变化仍需练习。",
        )
        val laterRequest = request().copy(
            input = input().copy(cycleOrdinal = 2, priorConversationMemory = memory),
        )

        assertEquals(
            listOf(ModelTaskCompletionIssueCode.TUTOR_CONTEXT_MISMATCH),
            ModelTaskCompletionValidator.validate(
                laterRequest,
                output().copy(cycleOrdinal = 1),
            ).map { it.code },
        )
    }

    @Test
    fun staleQuestionMemoryCannotExposeAFalseRetentionEstimate() {
        assertTrue(
            runCatching {
                TutorQuestionLearningEvidence(
                    independentRecallCount = 1,
                    assistedRecallCount = 0,
                    retrievalFailureCount = 1,
                    answerRevealCount = 0,
                    retentionEstimate = 0.7,
                    reviewStatus = TutorQuestionReviewStatus.STALE,
                )
            }.isFailure,
        )
    }

    @Test
    fun anAttachedQuestionWithoutAnyKnownAnchorIsRejectedAtConstruction() {
        // 显式添加的题**就是**本轮的题锚：只给 attachedQuestion 而不给 knownRoundQuestion，
        // 提示词会按所附之题讲，写门控与答案暴露却按"无题轮"走——同一轮出现两个事实。
        val rejected = runCatching {
            respondInput().copy(
                attachedQuestion = attachedQuestion("problem-attached", "revision-attached"),
            )
        }

        assertTrue(rejected.isFailure)
    }

    @Test
    fun anAttachedQuestionMissingFromTheRoundMenuIsRejectedByTheKnownAnchorGuard() {
        // 附加题与已知锚一致，但菜单里没有它：菜单是"模型能指哪几道"的选项集，锚不在菜单内
        // 意味着模型无论怎么声明都核不过（写门控只能回退到锚）。这条必须**构造期**就拦，
        // 否则派发出去的是一条本地无法核认的请求。
        val rejected = runCatching {
            respondInput().copy(
                boundQuestionCandidates = listOf(
                    relatedCandidate("problem-other", "revision-other"),
                ),
                knownRoundQuestion = relatedCandidate("problem-attached", "revision-attached"),
                attachedQuestion = attachedQuestion("problem-attached", "revision-attached"),
            )
        }

        assertTrue(rejected.isFailure)
    }

    private fun request(): ModelTaskRequest {
        val provider = provider()
        return ModelTaskRequest(
            requestId = "tutor-plan-request",
            input = input(),
            occurredAtEpochMillis = 1,
            egressManifest = ModelEgressManifest(
                authorizationId = "tutor-authorization",
                // 清单主语必须与 `TutorPlanInput.subjectId` 同口径（K1c：槽键主语 = 会话 id 的
                // 派生对话 id，`TutorConversationIds.captured`），否则清单在 requireAuthorizes 的
                // 第一条核对上就被拒。
                subjectId = TutorConversationIds.captured(SESSION_ID),
                purpose = ModelEgressPurpose.TUTORING,
                authorizedTaskKinds = setOf(ModelTaskKind.TUTOR_PLAN),
                providerId = provider.providerId,
                modelId = provider.modelId,
                providerConfigurationVersion = provider.providerConfigurationVersion,
                promptPolicyVersion = ModelPromptPolicyVersions.TUTOR_PLAN,
                approvedAtEpochMillis = 1,
                assets = emptyList(),
                disclosedData = ModelEgressManifest.TUTOR_PLAN_DISCLOSURE,
            ),
        )
    }

    private fun input() = TutorPlanInput(
        sessionId = SESSION_ID,
        draftRevisionNumber = 2,
        subject = "MATH",
        questionDocument = QuestionDocument(
            id = "question-1",
            blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
        ),
        relevantLearningEvidence = listOf(
            TutorKnowledgeEvidence("node-derivative", "导数符号", TutorEvidenceLevel.LEARNING, 0.35),
        ),
        projectionIsCurrent = true,
    )

    private fun output() = TutorPlanOutput(
        sessionId = SESSION_ID,
        draftRevisionNumber = 2,
        questionDocumentId = "question-1",
        plan = TutorTurnPlan(
            openingMarkdown = "先判断导数的符号如何变化。",
            diagnosticItem = TutorAssessmentItem(
                id = "diagnostic-1",
                stemMarkdown = "若导数先正后负，原函数怎样变化？",
                choices = listOf(
                    TutorChoice("a", "先增后减", "抓住了导数符号与单调性的对应。"),
                    TutorChoice("b", "先减后增", "你把正负对应关系反过来了。"),
                    TutorChoice("c", "始终递增", "需要关注导数变号，而不只是出现过正值。"),
                ),
                correctChoiceId = "a",
            ),
            solutionMarkdown = "先求导，再解导数大于零与小于零的区间。",
            alternateMethodMarkdown = "也可以画出导函数的符号表，从图像变化理解单调性。",
            difficultyReasonMarkdown = "这里区分符号对应错误与变号遗漏。",
            targetedEvidenceLabels = listOf("导数符号"),
            inferredKnowledgeLabels = listOf("导数", "函数单调性"),
        ),
        modelVersion = "model-v1",
    )

    private fun respondInput() = TutorRespondInput(
        sessionId = SESSION_ID,
        draftRevisionNumber = 2,
        subject = "MATH",
        questionDocument = input().questionDocument,
        relevantLearningEvidence = input().relevantLearningEvidence,
        projectionIsCurrent = true,
        questionLearningEvidence = null,
        responseOrdinal = 3,
        studentMessage = "为什么导数为正时原函数递增？",
        visibleTutorContextMarkdown = "刚才已经确认要从导数符号理解当前题。",
        priorMessages = listOf(
            TutorChatHistoryEntry(
                studentMessage = "先看哪一步？",
                assistantMarkdown = "先确定导数在各区间的符号。",
            ),
        ),
        requestedMove = TutorMoveType.DEEPEN_REASONING,
    )

    private fun relatedCandidate(
        problemId: String,
        problemRevisionId: String,
    ) = RelatedProblemCandidate(
        problemId = problemId,
        problemRevisionId = problemRevisionId,
        subject = SubjectKind.MATH,
        title = "候选题 $problemId",
        questionDocument = QuestionDocument(
            id = "question-$problemId",
            blocks = listOf(ContentBlock.Paragraph("stem", "求 $problemId 的单调区间")),
        ),
    )

    private fun attachedQuestion(
        problemId: String,
        problemRevisionId: String,
    ) = AttachedRoundQuestion(
        problemId = problemId,
        problemRevisionId = problemRevisionId,
        revisionNumber = 2,
        subject = SubjectKind.MATH,
        title = "附加题 $problemId",
        questionDocument = QuestionDocument(
            id = "question-$problemId",
            blocks = listOf(ContentBlock.Paragraph("stem", "求 $problemId 的单调区间")),
        ),
    )

    private fun respondOutput() = TutorRespondOutput(
        sessionId = SESSION_ID,
        draftRevisionNumber = 2,
        questionDocumentId = "question-1",
        responseOrdinal = 3,
        messageMarkdown = "因为导数描述原函数的瞬时变化方向，导数为正表示函数值随自变量增加而上升。",
        modelVersion = "model-v1",
    )

    private fun respondRequest(
        input: TutorRespondInput = respondInput(),
    ): ModelTaskRequest {
        val provider = provider().copy(supportedTasks = setOf(ModelTaskKind.TUTOR_RESPOND))
        return ModelTaskRequest(
            requestId = "tutor-respond-request",
            input = input,
            occurredAtEpochMillis = 1,
            egressManifest = ModelEgressManifest(
                authorizationId = "tutor-respond-authorization",
                subjectId = SESSION_ID,
                purpose = ModelEgressPurpose.TUTORING,
                authorizedTaskKinds = setOf(ModelTaskKind.TUTOR_RESPOND),
                providerId = provider.providerId,
                modelId = provider.modelId,
                providerConfigurationVersion = provider.providerConfigurationVersion,
                promptPolicyVersion = ModelPromptPolicyVersions.TUTOR_RESPOND,
                approvedAtEpochMillis = 1,
                assets = emptyList(),
                disclosedData = ModelEgressManifest.TUTOR_RESPOND_DISCLOSURE,
            ),
        )
    }

    private fun provider() = ProviderCapabilitySnapshot(
        providerId = "provider-1",
        providerDisplayName = "兼容模型",
        modelId = "model-1",
        supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN),
        supportsImageInput = true,
        supportsStructuredOutput = true,
        supportsStreaming = false,
        providerConfigurationVersion = "configuration-v1",
    )

    private companion object {
        const val SESSION_ID = "tutor-session-1"
    }
}
