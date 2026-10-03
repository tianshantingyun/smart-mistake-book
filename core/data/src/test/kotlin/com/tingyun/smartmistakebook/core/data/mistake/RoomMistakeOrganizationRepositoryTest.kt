package com.tingyun.smartmistakebook.core.data.mistake

import com.tingyun.smartmistakebook.core.data.study.FakeStudyDatabasePort
import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseNotReadyException
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.ProblemOrganizationRelationKey
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.UserProblemClassification
import com.tingyun.smartmistakebook.core.model.BindingAcceptanceSource
import com.tingyun.smartmistakebook.core.model.AtomicKnowledgeSuggestion
import com.tingyun.smartmistakebook.core.model.ClassificationDimension
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.KnowledgeBaseNodeContext
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeGroundingRequest
import com.tingyun.smartmistakebook.core.model.ProblemClassificationSuggestion
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationInput
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationOutput
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationPlan
import com.tingyun.smartmistakebook.core.model.ProblemRelationKind
import com.tingyun.smartmistakebook.core.model.ProblemRelationSuggestion
import com.tingyun.smartmistakebook.core.model.ProblemStepKnowledgeAttribution
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.RelatedProblemCandidate
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.model.TeachingAdvisoryRecord
import com.tingyun.smartmistakebook.core.model.TutorDifficultyTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMistakeOrganizationRepositoryTest {
    /**
     * 消灭的失败（D-Q3，消费点①）：改前 prepare() 第一行就是
     * `BundledKnowledgeBaseInstaller.install(database)`——首装 16.7 秒卡在学生点"整理"的
     * 关键路径上，装失败还会被上层统一 catch 成"provider 未配置"。现在没就绪就抛出可识别的
     * 类型，调用方据此说"准备中"。
     */
    @Test
    fun prepareRefusesWhileTheKnowledgeBaseIsNotReady() = runBlocking {
        val port = FakeStudyDatabasePort()
        val repository = RoomMistakeOrganizationRepository(
            database = port,
            knowledgeBaseAvailability = MutableStateFlow(KnowledgeBaseAvailability.Preparing),
        )

        val failure = runCatching { repository.prepareForTest() }.exceptionOrNull()

        assertTrue(
            "未就绪必须是可识别的类型，不能是任意异常（更不是 PROVIDER_NOT_CONFIGURED）: $failure",
            failure is KnowledgeBaseNotReadyException,
        )
        assertEquals(
            KnowledgeBaseAvailability.Preparing,
            (failure as KnowledgeBaseNotReadyException).availability,
        )
    }

    @Test
    fun prepareReportsTheConcreteUnavailableStateRatherThanRetryingAnInstall() = runBlocking {
        val port = FakeStudyDatabasePort()
        val repository = RoomMistakeOrganizationRepository(
            database = port,
            knowledgeBaseAvailability = MutableStateFlow(
                KnowledgeBaseAvailability.Unavailable("startup:knowledge:42"),
            ),
        )

        val failure = runCatching { repository.prepareForTest() }.exceptionOrNull()

        assertEquals(
            "失败态要原样带出来（出路是横幅重试，不是这里再装一次）",
            KnowledgeBaseAvailability.Unavailable("startup:knowledge:42"),
            (failure as KnowledgeBaseNotReadyException).availability,
        )
    }

    /** 就绪时门放行：空库会走到真正的业务校验（版本不再当前），而不是停在就绪门。 */
    @Test
    fun preparePassesTheGateOnceTheKnowledgeBaseIsReady() = runBlocking {
        val repository = RoomMistakeOrganizationRepository(
            database = FakeStudyDatabasePort(),
            knowledgeBaseAvailability = MutableStateFlow(KnowledgeBaseAvailability.Ready),
        )

        val failure = runCatching { repository.prepareForTest() }.exceptionOrNull()

        assertNotNull("就绪后应继续走到业务校验并失败在那里", failure)
        assertFalse(
            "就绪后不得再停在就绪门: $failure",
            failure is KnowledgeBaseNotReadyException,
        )
    }

    /** 一次 prepare 的最小调用形状：这些用例只关心就绪门发生在哪一步。 */
    private suspend fun RoomMistakeOrganizationRepository.prepareForTest() = prepare(
        key = MistakeRevisionKey(
            entryId = "entry-1",
            problemId = "problem-1",
            problemRevisionId = "revision-1",
        ),
        profile = StudyProfileOverview(),
        provider = ProviderCapabilitySnapshot(
            providerId = "provider:test",
            providerDisplayName = "测试模型",
            modelId = "model:test",
            supportedTasks = setOf(ModelTaskKind.PROBLEM_CLASSIFY),
            supportsImageInput = false,
            supportsStructuredOutput = true,
            supportsStreaming = true,
            executionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
            providerConfigurationVersion = "cfg-v1",
        ),
        attempt = 0,
        occurredAtEpochMillis = 1_000,
        approvedAtEpochMillis = 1_000,
    )

    @Test
    fun modelJudgedDifficultyTierBecomesAPerQuestionAdvisoryRow() {
        // spec batch-intake-spec §2 L2：模型判的难度档要能被排程按题读回。
        val advisory = requireNotNull(
            buildDifficultyTierAdvisory(
                organizationRequestId = "organization-request-1",
                practiceUnitId = "practice-1",
                learnerId = "learner:local",
                tier = TutorDifficultyTier.HARD,
                acceptedAtEpochMillis = 2_000,
            ),
        )

        assertEquals("learner:local", advisory.learnerId)
        assertEquals("practice-1", advisory.practiceUnitId)
        assertEquals(null, advisory.knowledgeNodeId)
        assertEquals(TeachingAdvisoryRecord.KIND_DIFFICULTY_TIER, advisory.advisoryKind)
        // 只存语义档名，不存秒数——秒数由本地常数表出。
        assertEquals(TutorDifficultyTier.HARD.name, advisory.payloadMarkdown)
        assertEquals(2_000L, advisory.createdAtEpochMillis)
    }

    @Test
    fun theAdvisoryKeyIsStableAcrossRetriesOfTheSameOrganization() {
        // UNIQUE(learner, source_id, kind) 靠 source_id 去重：同一次整理重放
        // （重试/恢复）不得产生第二条难度声明。
        val first = requireNotNull(
            buildDifficultyTierAdvisory("request-x", "practice-1", "learner:local", TutorDifficultyTier.EASY, 1_000),
        )
        val replay = requireNotNull(
            buildDifficultyTierAdvisory("request-x", "practice-1", "learner:local", TutorDifficultyTier.EASY, 9_999),
        )

        assertEquals(first.advisoryId, replay.advisoryId)
        assertEquals(first.sourceId, replay.sourceId)
    }

    @Test
    fun anUnjudgedDifficultyWritesNoRowAtAll() {
        // 没判过难度就不写行（排程退回数值代理）——绝不写一条"默认中档"，
        // 那正是本次要消灭的失败。
        assertEquals(
            null,
            buildDifficultyTierAdvisory(
                organizationRequestId = "organization-request-1",
                practiceUnitId = "practice-1",
                learnerId = "learner:local",
                tier = null,
                acceptedAtEpochMillis = 2_000,
            ),
        )
    }

    @Test
    fun repeatedOntologyGapKeepsOccurrencesButSharesOneGroundingKey() {
        val first = buildKnowledgeGroundingRecords(
            organizationRequestId = "organization-request-1",
            organizationRequestFingerprint = "a".repeat(64),
            input = input(),
            requests = listOf(
                KnowledgeGroundingRequest(
                    query = "高中数学  导数符号  单调性",
                    expectedParentKnowledgeDisplayName = "函数性质",
                    reasonMarkdown = "现有本体没有足够细的原子节点。",
                ),
            ),
            occurredAtEpochMillis = 2_000,
        ).single()
        val second = buildKnowledgeGroundingRecords(
            organizationRequestId = "organization-request-2",
            organizationRequestFingerprint = "b".repeat(64),
            input = input(),
            requests = listOf(
                KnowledgeGroundingRequest(
                    query = "高中数学 导数符号 单调性",
                    expectedParentKnowledgeDisplayName = "函数性质",
                    reasonMarkdown = "另一道题再次命中同一个本体缺口。",
                ),
            ),
            occurredAtEpochMillis = 3_000,
        ).single()

        assertEquals(first.groundingKey, second.groundingKey)
        assertTrue(first.groundingRequestId != second.groundingRequestId)
        assertEquals("MATH", first.subject)
        assertEquals(0, first.requestOrdinal)
    }

    @Test
    fun explicitCorrectionBecomesDeterministicUserCorrectedFacts() {
        val first = buildConfirmationCommand(
            requestId = "organization-request",
            input = input(),
            classifications = classifications(),
            relations = relations(),
            acceptedAtEpochMillis = 2_000,
        )
        val replay = buildConfirmationCommand(
            requestId = "organization-request",
            input = input(),
            classifications = classifications(),
            relations = relations(),
            acceptedAtEpochMillis = 2_000,
        )

        assertEquals(first, replay)
        assertEquals(2, first.classifications.size)
        assertEquals(setOf("CHAPTER", "KNOWLEDGE"), first.classifications.map { it.dimension }.toSet())
        assertEquals(1, first.knowledgeNodes.size)
        assertEquals(1, first.knowledgeBindings.size)
        assertEquals("USER_CORRECTED", first.classifications.first().acceptanceSource)
        assertEquals("USER_CORRECTED", first.knowledgeBindings.single().sourceType)
        assertEquals("user-corrected-v1", first.classifications.first().taxonomyVersion)
        assertEquals("ACTIVE", first.relations.single().status)
        assertFalse(first.replaceRelations)
        assertTrue(first.payloadFingerprint.matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun knowledgeNodeIdentityDoesNotChangeWithAcceptanceAuthority() {
        val automatic = buildConfirmationCommand(
            requestId = "automatic-request",
            input = input(),
            classifications = classifications(),
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
        )
        val corrected = buildConfirmationCommand(
            requestId = "corrected-request",
            input = input(),
            classifications = classifications(),
            relations = emptyList(),
            acceptedAtEpochMillis = 3_000,
            acceptanceSource = BindingAcceptanceSource.USER_CORRECTED,
        )

        assertEquals(
            automatic.knowledgeNodes.single().knowledgeNodeId,
            corrected.knowledgeNodes.single().knowledgeNodeId,
        )
        assertEquals("LOCAL_POLICY_ACCEPTED", automatic.knowledgeBindings.single().sourceType)
        assertEquals("USER_CORRECTED", corrected.knowledgeBindings.single().sourceType)
        assertEquals("organization-v1", automatic.knowledgeNodes.single().taxonomyVersion)
        assertEquals("organization-v1", corrected.knowledgeNodes.single().taxonomyVersion)
    }

    @Test
    fun automaticRerunPreservesAcceptedLabelsThatTheModelOmits() {
        val existing = buildConfirmationCommand(
            requestId = "existing-organization",
            input = input(),
            classifications = classifications() +
                classification(ClassificationDimension.KNOWLEDGE, "链式法则", 0.99),
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
        ).classifications
        val incoming = listOf(
            classification(ClassificationDimension.CHAPTER, "函数", 0.95),
            classification(ClassificationDimension.KNOWLEDGE, "导数符号", 0.95),
        )

        val merged = mergeAutomaticClassifications(existing, incoming)

        assertEquals(
            setOf("函数", "二次函数最值", "链式法则", "导数符号"),
            merged.mapTo(linkedSetOf(), ProblemClassificationSuggestion::displayName),
        )
    }

    @Test
    fun exactRelationRemovalUsesDeterministicScopedIdentityWithoutFullReplacement() {
        val command = buildConfirmationCommand(
            requestId = "remove-relation-request",
            input = input(),
            classifications = classifications(),
            relations = emptyList(),
            acceptedAtEpochMillis = 4_000,
            relationRemovals = setOf(
                ProblemOrganizationRelationKey(
                    targetProblemId = "problem-2",
                    targetProblemRevisionId = "revision-2",
                    kind = ProblemRelationKind.VARIANT_OF,
                ),
            ),
        )

        assertEquals(1, command.relationIdsToRemove.size)
        assertTrue(command.relationIdsToRemove.single().startsWith("relation:"))
        assertFalse(command.replaceRelations)
    }

    @Test
    fun incompleteHierarchyCannotBecomeAConfirmationCommand() {
        val selectedKnowledge = classifications().filter {
            it.dimension == ClassificationDimension.KNOWLEDGE
        }

        assertTrue(
            runCatching {
                buildConfirmationCommand(
                    requestId = "organization-request",
                    input = input(),
                    classifications = selectedKnowledge,
                    relations = emptyList(),
                    acceptedAtEpochMillis = 2_000,
                )
            }.isFailure,
        )
    }

    @Test
    fun completeHighConfidenceHierarchyIsAcceptedAndNormalized() {
        val output = output(
            classifications = listOf(
                classification(ClassificationDimension.CHAPTER, " 函数 ", 0.78),
                classification(ClassificationDimension.CHAPTER, "函数", 0.95),
                classification(ClassificationDimension.KNOWLEDGE, "二次函数最值", 0.90),
            ),
            relations = relations().map { it.copy(confidence = 0.90) },
        )

        val accepted = requireNotNull(acceptOrganizationLocally(input(), output))

        assertEquals(listOf("函数", "二次函数最值"), accepted.classifications.map { it.displayName })
        assertEquals(listOf("problem-2"), accepted.relations.map { it.targetProblemId })
        val command = buildConfirmationCommand(
            requestId = "organization-request",
            input = input(),
            classifications = accepted.classifications,
            relations = accepted.relations,
            acceptedAtEpochMillis = 2_000,
            atomicKnowledge = accepted.atomicKnowledge,
            stepAttributions = accepted.stepAttributions,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
        )
        assertEquals("LOCAL_POLICY_ACCEPTED", command.classifications.single {
            it.dimension == "CHAPTER"
        }.acceptanceSource)
        assertEquals("LOCAL_POLICY_ACCEPTED", command.knowledgeBindings.single().sourceType)
        assertEquals("local-policy-v1", command.classifications.first().taxonomyVersion)
    }

    @Test
    fun lowConfidenceOrIncompleteHierarchyIsNotAccepted() {
        val lowChapter = output(
            classifications = listOf(
                classification(ClassificationDimension.CHAPTER, "函数", 0.77),
                classification(ClassificationDimension.KNOWLEDGE, "导数", 0.99),
            ),
        )
        val missingKnowledge = output(
            classifications = listOf(
                classification(ClassificationDimension.CHAPTER, "函数", 0.99),
                classification(ClassificationDimension.KNOWLEDGE, "导数", 0.77),
            ),
        )

        assertEquals(null, acceptOrganizationLocally(input(), lowChapter))
        assertEquals(null, acceptOrganizationLocally(input(), missingKnowledge))
    }

    @Test
    fun ungroundedAtomicKnowledgeCannotEnterSubjectMasteryMemory() {
        val grounded = output()
        val ungrounded = grounded.copy(
            plan = grounded.plan.copy(
                atomicKnowledge = grounded.plan.atomicKnowledge.map { atom ->
                    atom.copy(matchedKnowledgeNodeId = null)
                },
            ),
        )

        assertEquals(null, acceptOrganizationLocally(input(), ungrounded))
    }

    @Test
    fun invalidOrLowConfidenceRelationDoesNotBlockClassification() {
        val invalidTarget = relations().single().copy(
            targetProblemId = "not-in-candidates",
            targetProblemRevisionId = "unknown-revision",
            confidence = 0.99,
        )
        val lowConfidence = relations().single().copy(confidence = 0.89)
        val accepted = requireNotNull(
            acceptOrganizationLocally(
                input(),
                output(relations = listOf(invalidTarget, lowConfidence)),
            ),
        )

        assertEquals(2, accepted.classifications.size)
        assertTrue(accepted.relations.isEmpty())
        val command = buildConfirmationCommand(
            requestId = "organization-request",
            input = input(),
            classifications = accepted.classifications,
            relations = accepted.relations,
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
        )
        assertFalse(command.replaceRelations)
    }

    @Test
    fun outputForAnotherRevisionCannotBeLocallyAccepted() {
        val mismatched = output().copy(problemRevisionId = "revision-2")

        assertEquals(null, acceptOrganizationLocally(input(), mismatched))
    }

    @Test
    fun relationShortlistPrioritizesSharedKnowledgeOverAlphabeticalOrder() {
        val current = mistake(
            problemId = "current",
            title = "函数单调性",
            knowledge = listOf("导数符号"),
        )
        val alphabeticallyFirstButUnrelated = mistake(
            problemId = "a-unrelated",
            title = "A集合运算",
            knowledge = listOf("集合"),
        )
        val related = mistake(
            problemId = "z-related",
            title = "利用导数研究单调区间",
            knowledge = listOf("导数符号"),
        )

        assertTrue(
            relationCandidateScore(current, related) >
                relationCandidateScore(current, alphabeticallyFirstButUnrelated),
        )
    }

    @Test
    fun offlineCorrectionCommandIsDeterministicAndUserCorrected() {
        val facts = OfflineCorrectionFacts(
            problemId = "problem-1",
            problemRevisionId = "revision-1",
            practiceUnitId = "practice-1",
            subject = SubjectKind.MATH,
            questionDocument = document("question-1", "求函数最值"),
        )
        val first = buildOfflineCorrectionCommand(
            entryId = "entry-1",
            current = facts,
            userClassifications = listOf(
                UserProblemClassification(ClassificationDimension.CHAPTER, "函数"),
                UserProblemClassification(ClassificationDimension.KNOWLEDGE, "二次函数最值"),
            ),
            correctedAtEpochMillis = 4_000,
        )
        val replay = buildOfflineCorrectionCommand(
            entryId = "entry-1",
            current = facts,
            userClassifications = listOf(
                UserProblemClassification(ClassificationDimension.CHAPTER, "函数"),
                UserProblemClassification(ClassificationDimension.KNOWLEDGE, "二次函数最值"),
            ),
            correctedAtEpochMillis = 4_000,
        )

        assertEquals(first, replay)
        assertEquals("problem-1", first.problemId)
        assertEquals("revision-1", first.problemRevisionId)
        assertEquals("practice-1", first.practiceUnitId)
        assertEquals(2, first.classifications.size)
        assertEquals(setOf("CHAPTER", "KNOWLEDGE"), first.classifications.map { it.dimension }.toSet())
        assertEquals("USER_CORRECTED", first.classifications.first().acceptanceSource)
        assertEquals("USER_CORRECTED", first.knowledgeBindings.single().sourceType)
        assertEquals(1, first.knowledgeNodes.size)
        assertEquals(1, first.knowledgeBindings.size)
        assertEquals(0, first.relations.size)
        assertFalse(first.replaceRelations)
        assertEquals(emptySet<String>(), first.relationIdsToRemove)
    }

    @Test
    fun offlineCorrectionRequiresBothDimensions() {
        val facts = OfflineCorrectionFacts(
            problemId = "problem-1",
            problemRevisionId = "revision-1",
            practiceUnitId = "practice-1",
            subject = SubjectKind.MATH,
            questionDocument = document("question-1", "求函数最值"),
        )
        val missingKnowledge = runCatching {
            buildOfflineCorrectionCommand(
                entryId = "entry-1",
                current = facts,
                userClassifications = listOf(
                    UserProblemClassification(ClassificationDimension.CHAPTER, "函数"),
                ),
                correctedAtEpochMillis = 4_000,
            )
        }
        assertTrue(missingKnowledge.isFailure)

        val missingChapter = runCatching {
            buildOfflineCorrectionCommand(
                entryId = "entry-1",
                current = facts,
                userClassifications = listOf(
                    UserProblemClassification(ClassificationDimension.KNOWLEDGE, "二次函数最值"),
                ),
                correctedAtEpochMillis = 4_000,
            )
        }
        assertTrue(missingChapter.isFailure)
    }

    private fun input() = ProblemOrganizationInput(
        problemId = "problem-1",
        problemRevisionId = "revision-1",
        practiceUnitId = "practice-1",
        subject = SubjectKind.MATH,
        questionDocument = document("question-1", "求函数最值"),
        relevantLearningEvidence = emptyList(),
        relationCandidates = listOf(
            RelatedProblemCandidate(
                problemId = "problem-2",
                problemRevisionId = "revision-2",
                subject = SubjectKind.MATH,
                title = "相关变式",
                questionDocument = document("question-2", "讨论参数范围"),
            ),
        ),
        knowledgeBaseNodes = listOf(
            KnowledgeBaseNodeContext(
                knowledgeNodeId = "math-atomic-core-operation",
                subject = SubjectKind.MATH,
                canonicalName = "识别并执行核心运算步骤",
                aliases = emptyList(),
                kind = KnowledgeNodeKind.PROCEDURE,
                granularity = KnowledgeNodeGranularity.ATOMIC,
                parentCanonicalName = "二次函数最值",
                taxonomyVersion = "math-v1",
                verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED,
                boundaryMarkdown = "只记录本题实际使用的运算能力。",
            ),
        ),
    )

    private fun classifications() = listOf(
        ProblemClassificationSuggestion(
            dimension = ClassificationDimension.KNOWLEDGE,
            displayName = "二次函数最值",
            rationaleMarkdown = "核心知识点。",
            confidence = 0.9,
        ),
        ProblemClassificationSuggestion(
            dimension = ClassificationDimension.CHAPTER,
            displayName = "函数",
            rationaleMarkdown = "属于函数板块。",
            confidence = 0.8,
        ),
    )

    private fun classification(
        dimension: ClassificationDimension,
        displayName: String,
        confidence: Double,
    ) = ProblemClassificationSuggestion(
        dimension = dimension,
        displayName = displayName,
        rationaleMarkdown = "本地策略测试。",
        confidence = confidence,
    )

    private fun output(
        classifications: List<ProblemClassificationSuggestion> = classifications(),
        relations: List<ProblemRelationSuggestion> = emptyList(),
    ) = ProblemOrganizationOutput(
        problemId = "problem-1",
        problemRevisionId = "revision-1",
        practiceUnitId = "practice-1",
        plan = ProblemOrganizationPlan(
            summaryMarkdown = "整理完成。",
            reviewPriorityMarkdown = "复习当前题目。",
            targetedEvidenceLabels = emptyList(),
            classifications = classifications,
            relations = relations,
            schemaVersion = 2,
            atomicKnowledge = listOf(
                AtomicKnowledgeSuggestion(
                    referenceId = "atom-1",
                    canonicalName = "识别并执行核心运算步骤",
                    aliases = emptyList(),
                    kind = KnowledgeNodeKind.PROCEDURE,
                    parentKnowledgeDisplayName = classifications.first {
                        it.dimension == ClassificationDimension.KNOWLEDGE
                    }.displayName.trim().replace(Regex("\\s+"), " "),
                    matchedKnowledgeNodeId = "math-atomic-core-operation",
                    prerequisiteReferenceIds = emptyList(),
                    observableOutcomeMarkdown = "能独立完成题目中的核心运算步骤。",
                    boundaryMarkdown = "只记录本题实际使用的运算能力。",
                    confidence = 0.9,
                ),
            ),
            stepAttributions = listOf(
                ProblemStepKnowledgeAttribution(
                    stepOrdinal = 1,
                    stepSummaryMarkdown = "完成核心运算。",
                    atomicReferenceIds = listOf("atom-1"),
                ),
            ),
        ),
        modelVersion = "test-model",
    )

    private fun relations() = listOf(
        ProblemRelationSuggestion(
            targetProblemId = "problem-2",
            targetProblemRevisionId = "revision-2",
            kind = ProblemRelationKind.VARIANT_OF,
            rationaleMarkdown = "知识点相同，参数条件不同。",
            confidence = 0.8,
        ),
    )

    private fun document(id: String, markdown: String) = QuestionDocument(
        id = id,
        blocks = listOf(ContentBlock.Paragraph("$id-block", markdown)),
    )

    private fun mistake(
        problemId: String,
        title: String,
        knowledge: List<String>,
    ) = MistakeRecord(
        entryId = "entry-$problemId",
        problemId = problemId,
        problemRevisionId = "revision-$problemId",
        practiceUnitId = "practice-$problemId",
        sourceKey = null,
        subject = "MATH",
        title = title,
        problemMarkdown = title,
        status = "ACTIVE",
        createdAtEpochMillis = 1,
        nextReviewAtEpochMillis = null,
        retrievability = null,
        knowledgeLabels = knowledge,
    )

    /**
     * L3：选项形状不足时按既有 KB 读口补齐——当前值在前（种子语义不变），
     * 随后是已审知识树节点；MODEL_CANDIDATE 不是"已审目录"，不得进入可点选选项。
     */
    @Test
    fun organizationOptionsSeedWithCurrentValuesAndFillFromTheReviewedTree() {
        val options = buildOrganizationOptions(
            subject = "MATH",
            confirmedClassifications = listOf(
                binding(ClassificationDimension.CHAPTER, "chapter-functions", "函数"),
                binding(ClassificationDimension.KNOWLEDGE, "knowledge-extrema", "函数最值"),
            ),
            treeNodes = listOf(
                treeNode(
                    "topic-functions",
                    "函数的概念与性质",
                    KnowledgeNodeGranularity.TOPIC,
                    KnowledgeNodeVerificationStatus.CURATED,
                ),
                treeNode(
                    "topic-extrema",
                    "函数最值",
                    KnowledgeNodeGranularity.TOPIC,
                    KnowledgeNodeVerificationStatus.CURATED,
                ),
                treeNode(
                    "atomic-triangle",
                    "三角函数的图象与变换",
                    KnowledgeNodeGranularity.ATOMIC,
                    KnowledgeNodeVerificationStatus.SOURCE_GROUNDED,
                ),
                treeNode(
                    "atomic-model-guess",
                    "模型猜测的知识点",
                    KnowledgeNodeGranularity.ATOMIC,
                    KnowledgeNodeVerificationStatus.MODEL_CANDIDATE,
                ),
            ),
        )

        assertEquals("MATH", options.subject)
        // 当前值在前；树上的 TOPIC 节点随后补齐（同名树节点不重复出现在本维度）。
        assertEquals(
            listOf("函数", "函数的概念与性质", "函数最值"),
            options.chapters.map { it.displayName },
        )
        // ATOMIC 级：当前确认的知识点在前，已审原子节点随后；模型猜测被剔除。
        assertEquals(
            listOf("函数最值", "三角函数的图象与变换"),
            options.knowledgeNodes.map { it.displayName },
        )
        // 选项身份直接来自知识树节点 id（不新造目录）。
        assertEquals("topic-functions", options.chapters[1].labelId)
        assertEquals("atomic-triangle", options.knowledgeNodes[1].labelId)
    }

    private fun binding(
        dimension: ClassificationDimension,
        labelId: String,
        displayName: String,
    ) = com.tingyun.smartmistakebook.core.database.ProblemClassificationBindingRecord(
        bindingId = "binding-$labelId",
        problemId = "problem-1",
        basisRevisionId = "revision-1",
        dimension = dimension.name,
        labelId = labelId,
        displayName = displayName,
        taxonomyVersion = "user-corrected-v1",
        acceptanceSource = BindingAcceptanceSource.USER_CORRECTED.name,
        acceptedAtEpochMillis = 1,
    )

    private fun treeNode(
        id: String,
        displayName: String,
        granularity: KnowledgeNodeGranularity,
        verificationStatus: KnowledgeNodeVerificationStatus,
    ) = com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord(
        knowledgeNodeId = id,
        stableCode = "code:$id",
        subject = "MATH",
        displayName = displayName,
        parentKnowledgeNodeId = null,
        taxonomyVersion = "math-v1",
        createdAtEpochMillis = 1,
        canonicalName = displayName,
        nodeKind = KnowledgeNodeKind.TOPIC.name,
        granularity = granularity.name,
        verificationStatus = verificationStatus.name,
    )
}
