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
            unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
            requestId = "organization-request",
            input = input(),
            classifications = classifications(),
            relations = relations(),
            acceptedAtEpochMillis = 2_000,
        )
        val replay = buildConfirmationCommand(
            unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
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
        // KF-07：没有通过持久化校验的原子节点时不再把 topic 节点按 strength=1.0 兜底全绑；
        // 绑定集保持为空 = 诚实未分类，题面由 `ensurePseudoKnowledgeBinding` 的 pseudo 占位接管。
        assertTrue(first.knowledgeBindings.isEmpty())
        assertEquals("USER_CORRECTED", first.classifications.first().acceptanceSource)
        assertEquals("user-corrected-v1", first.classifications.first().taxonomyVersion)
        assertEquals("ACTIVE", first.relations.single().status)
        assertFalse(first.replaceRelations)
        assertTrue(first.payloadFingerprint.matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun knowledgeNodeIdentityDoesNotChangeWithAcceptanceAuthority() {
        val automatic = buildConfirmationCommand(
            unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
            requestId = "automatic-request",
            input = input(),
            classifications = classifications(),
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
        )
        val corrected = buildConfirmationCommand(
            unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
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
        // KF-07：无原子节点落地时两条权威路径都不再伪造 topic 绑定。
        assertTrue(automatic.knowledgeBindings.isEmpty())
        assertTrue(corrected.knowledgeBindings.isEmpty())
        assertEquals("organization-v1", automatic.knowledgeNodes.single().taxonomyVersion)
        assertEquals("organization-v1", corrected.knowledgeNodes.single().taxonomyVersion)
    }

    @Test
    fun automaticRerunPreservesAcceptedLabelsThatTheModelOmits() {
        val existing = buildConfirmationCommand(
            unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
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

    /**
     * KF-30：自动接受过的标签不再以 1.0 自锁。预算满时，本轮更高置信的自动提议可以把旧的
     * 自动标签挤出——一次错误的自动接受不该只能等用户手动来改。
     */
    @Test
    fun higherConfidenceAutomaticProposalReplacesStaleAutomaticLabelsWithinBudget() {
        val existing = autoChapters("板块 A", "板块 B", "板块 C") + binding(
            ClassificationDimension.KNOWLEDGE,
            "knowledge-stale",
            "旧知识点",
            BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
        )
        val incoming = listOf(
            classification(ClassificationDimension.CHAPTER, "板块 A2", 0.9),
            classification(ClassificationDimension.CHAPTER, "板块 B2", 0.9),
            classification(ClassificationDimension.CHAPTER, "板块 C2", 0.9),
        )

        val merged = mergeAutomaticClassifications(existing, incoming)

        // 章节预算（3）被更高置信的本轮提议占满，旧自动章节全部让位；知识点没有竞争，照常保留。
        assertEquals(
            setOf("板块 A2", "板块 B2", "板块 C2", "旧知识点"),
            merged.mapTo(linkedSetOf(), ProblemClassificationSuggestion::displayName),
        )
        assertEquals(
            "旧自动标签被覆盖后不得残留",
            emptySet<String>(),
            merged.mapTo(linkedSetOf(), ProblemClassificationSuggestion::displayName)
                .intersect(setOf("板块 A", "板块 B", "板块 C")),
        )
    }

    /**
     * KF-30：用户确认语义的标签（USER_CORRECTED / USER_CONFIRMED）不可被自动提议覆盖，也不因
     * 预算满被挤掉——用户权威是合并的下界，与来源=auto 的标签区别对待。
     */
    @Test
    fun userOwnedLabelsSurviveHigherConfidenceAutomaticProposals() {
        val existing = listOf(
            binding(
                ClassificationDimension.CHAPTER,
                "chapter-user-1",
                "函数",
                BindingAcceptanceSource.USER_CONFIRMED,
            ),
            binding(
                ClassificationDimension.CHAPTER,
                "chapter-user-2",
                "数列",
                BindingAcceptanceSource.USER_CONFIRMED,
            ),
            binding(
                ClassificationDimension.CHAPTER,
                "chapter-user-3",
                "概率",
                BindingAcceptanceSource.USER_CONFIRMED,
            ),
            binding(
                ClassificationDimension.KNOWLEDGE,
                "knowledge-user",
                "函数最值",
                BindingAcceptanceSource.USER_CORRECTED,
            ),
        )
        val incoming = listOf(
            classification(ClassificationDimension.CHAPTER, "函数", 1.0),
            classification(ClassificationDimension.CHAPTER, "三角函数", 0.99),
            classification(ClassificationDimension.CHAPTER, "立体几何", 0.99),
            classification(ClassificationDimension.CHAPTER, "解析几何", 0.99),
            classification(ClassificationDimension.KNOWLEDGE, "函数最值", 1.0),
        )

        val merged = mergeAutomaticClassifications(existing, incoming)

        // 用户章节占满预算：更高置信的自动提议一个也挤不进来；同名的 incoming 也不覆盖用户行。
        assertEquals(
            listOf("函数", "数列", "概率"),
            merged.filter { it.dimension == ClassificationDimension.CHAPTER }
                .map(ProblemClassificationSuggestion::displayName),
        )
        assertEquals(1.0, merged.single { it.displayName == "函数" }.confidence, 0.0)
        assertEquals(
            listOf("函数最值"),
            merged.filter { it.dimension == ClassificationDimension.KNOWLEDGE }
                .map(ProblemClassificationSuggestion::displayName),
        )
    }

    /**
     * KF-30 负向边界钉住：同置信或更低置信的本轮提议不覆盖存量自动标签——只有**严格更高**置信
     * 才让位（存量基线 = 它曾通过的接受门）。
     */
    @Test
    fun equalOrLowerConfidenceProposalsDoNotReplaceAcceptedAutomaticLabels() {
        val existing = autoChapters("板块 A", "板块 B", "板块 C")
        val equal = mergeAutomaticClassifications(
            existing,
            listOf(
                classification(
                    ClassificationDimension.CHAPTER,
                    "板块 D",
                    CLASSIFICATION_ACCEPTANCE_CONFIDENCE,
                ),
                classification(
                    ClassificationDimension.CHAPTER,
                    "板块 E",
                    CLASSIFICATION_ACCEPTANCE_CONFIDENCE,
                ),
                classification(
                    ClassificationDimension.CHAPTER,
                    "板块 F",
                    CLASSIFICATION_ACCEPTANCE_CONFIDENCE,
                ),
            ),
        )
        val lower = mergeAutomaticClassifications(
            existing,
            listOf(classification(ClassificationDimension.CHAPTER, "板块 D", 0.5)),
        )

        val retainedNames = setOf("板块 A", "板块 B", "板块 C")
        assertEquals(
            retainedNames,
            equal.mapTo(linkedSetOf(), ProblemClassificationSuggestion::displayName),
        )
        assertTrue("同置信不得留下更高置信的假象", equal.all { it.confidence <= CLASSIFICATION_ACCEPTANCE_CONFIDENCE })
        assertEquals(
            retainedNames,
            lower.mapTo(linkedSetOf(), ProblemClassificationSuggestion::displayName),
        )
    }

    /**
     * KF-30 (b) 的红样例：存量自动标签的**真实**接受分数落库后，低于它的本轮提议不得顶掉它。
     *
     * 红证（实施本列之前跑本用例）：旧行为只知道"它曾过门"，把存量一律估成 0.78 ⇒
     * 0.79 的提议 > 0.78 ⇒ 曾以 0.99 被接受的标签被替换（实际 merged 置信度 = 0.79），
     * 本断言以 `expected:<[0.99]> but was:<[0.79]>` 失败。列落地后存量真值可读（0.99），
     * 保留存量 ⇒ 绿。两阶段形态与原始输出见提交 evidence。
     */
    @Test
    fun aProposalBelowTheRecordedAcceptanceScoreCannotReplaceTheStoredLabel() {
        val existing = listOf(
            binding(
                dimension = ClassificationDimension.KNOWLEDGE,
                labelId = "knowledge-stale",
                displayName = "旧知识点",
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                acceptedConfidence = 0.99,
            ),
        )
        val incoming = listOf(
            classification(ClassificationDimension.KNOWLEDGE, "旧知识点", 0.79),
        )

        val merged = mergeAutomaticClassifications(existing, incoming)

        assertEquals(
            "存量真值 0.99 严格高于 0.79：必须保留存量（旧行为会返回 0.79）",
            listOf(0.99),
            merged.map(ProblemClassificationSuggestion::confidence),
        )
        // 写侧：已知真值的存量不是"未知"，重写时带着真值走——不得降级成门值/NULL。
        val command = buildConfirmationCommand(
            requestId = "organization-request",
            input = input(),
            classifications = merged + classification(ClassificationDimension.CHAPTER, "函数", 0.95),
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
            unknownAcceptedConfidenceKeys = unknownAcceptedConfidenceKeys(existing, merged),
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
        )
        assertEquals(
            0.99,
            command.classifications.single { it.displayName == "旧知识点" }.acceptedConfidence!!,
            0.0,
        )
    }

    /** KF-30：等值（0.80 vs 0.80）保留存量——判据「严格更高者胜、等值保留存量」一行不改。 */
    @Test
    fun anEqualRecordedScoreKeepsTheStoredLabel() {
        val existing = listOf(
            binding(
                dimension = ClassificationDimension.KNOWLEDGE,
                labelId = "knowledge-equal",
                displayName = "旧知识点",
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                acceptedConfidence = 0.80,
            ),
        )
        val incoming = listOf(
            classification(ClassificationDimension.KNOWLEDGE, "旧知识点", 0.80),
        )

        val merged = mergeAutomaticClassifications(existing, incoming)

        assertEquals(
            "同置信先入者胜：保留存量，不得被本轮同值提议替换",
            listOf(0.80),
            merged.map(ProblemClassificationSuggestion::confidence),
        )
        // K3 复核 P2：0.80/0.80 等值时"保留存量"与"被同值提议替换"的分数无法区分——
        // 用保留态独有的理由文本把两条路径分开（替换会带上本轮提议的理由）。
        assertTrue(
            "等值必须保留存量：理由应仍是存量被保留的口径",
            merged.single().rationaleMarkdown.contains("此前由自动整理接受"),
        )
    }

    /**
     * KF-30 Q2 interim：升级前旧行的列值是 NULL = **未知**，按接受门 0.78 参与比较——
     * 严格更高且 ≥ 门值的新提议可以替换它（未知**不冻结**），真值随本轮落库（系统自纠）。
     */
    @Test
    fun anUnknownLegacyScoreIsReplacedByAProposalAboveTheGate() {
        val existing = listOf(
            binding(
                dimension = ClassificationDimension.KNOWLEDGE,
                labelId = "knowledge-legacy",
                displayName = "旧知识点",
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                acceptedConfidence = null,
            ),
        )
        val incoming = listOf(
            classification(ClassificationDimension.KNOWLEDGE, "旧知识点", 0.80),
        )

        val merged = mergeAutomaticClassifications(existing, incoming)
        val unknownKeys = unknownAcceptedConfidenceKeys(existing, merged)

        assertEquals(
            "0.80 严格高于门值 0.78：旧行不得冻结",
            listOf(0.80),
            merged.map(ProblemClassificationSuggestion::confidence),
        )
        assertEquals(
            "被替换的键不再是未知 ⇒ 写侧落本轮真值",
            emptySet<Pair<ClassificationDimension, String>>(),
            unknownKeys,
        )
        val command = buildConfirmationCommand(
            requestId = "organization-request",
            input = input(),
            classifications = merged + classification(ClassificationDimension.CHAPTER, "函数", 0.95),
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
            unknownAcceptedConfidenceKeys = unknownKeys,
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
        )
        assertEquals(
            "系统自纠：被替换的旧行落上本轮真值 0.80",
            0.80,
            command.classifications.single { it.displayName == "旧知识点" }.acceptedConfidence!!,
            0.0,
        )
    }

    /**
     * KF-30 Q2 interim 的另一半：NULL 按门值参与比较 ⇒ 与门值等值（或更低）的本轮提议不得替换它；
     * 被保留的旧行**不得**把门值当成"曾以 0.78 接受"写回——真值仍未知，写 NULL。
     */
    @Test
    fun anUnknownLegacyScoreSurvivesAProposalAtTheGateAndIsNotFabricated() {
        val existing = listOf(
            binding(
                dimension = ClassificationDimension.CHAPTER,
                labelId = "chapter-known",
                displayName = "函数",
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                acceptedConfidence = 0.95,
            ),
            binding(
                dimension = ClassificationDimension.KNOWLEDGE,
                labelId = "knowledge-legacy",
                displayName = "旧知识点",
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                acceptedConfidence = null,
            ),
        )
        val incoming = listOf(
            classification(
                ClassificationDimension.KNOWLEDGE,
                "旧知识点",
                CLASSIFICATION_ACCEPTANCE_CONFIDENCE,
            ),
        )

        val merged = mergeAutomaticClassifications(existing, incoming)

        val unknownKeys = unknownAcceptedConfidenceKeys(existing, merged)

        assertEquals(
            "等值保留存量：门值提议不得替换未知旧行",
            listOf(0.95, CLASSIFICATION_ACCEPTANCE_CONFIDENCE),
            merged.map(ProblemClassificationSuggestion::confidence),
        )
        assertEquals(
            "被保留的未知键必须标记为 NULL，已知键不在其中",
            setOf(ClassificationDimension.KNOWLEDGE to "旧知识点"),
            unknownKeys,
        )
        val command = buildConfirmationCommand(
            requestId = "organization-request",
            input = input(),
            classifications = merged,
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
            unknownAcceptedConfidenceKeys = unknownKeys,
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
        )
        assertEquals(
            "未知旧行重写后仍然是 NULL（不伪造门值）",
            null,
            command.classifications.single { it.displayName == "旧知识点" }.acceptedConfidence,
        )
        assertEquals(
            "已知存量在本轮重写时带着真值",
            0.95,
            command.classifications.single { it.displayName == "函数" }.acceptedConfidence!!,
            0.0,
        )
    }

    /**
     * K5-A（R2 口径 1）：存量未知行被严格更高（且 ≥ 门值）的提议替换时，合并裁决必须带
     * 可观察理由 `incumbent_unknown`——证据上把"按策略替换"（存量未知，按策略门解释）与
     * "按分数替换"（存量已知，按落库分数）分开。行为判据与 K3 完全一致：0.80 > 门值 0.78。
     */
    @Test
    fun anUnknownIncumbentReplacedAboveTheGateCarriesTheUnknownPolicyReason() {
        val existing = listOf(
            binding(
                dimension = ClassificationDimension.KNOWLEDGE,
                labelId = "knowledge-legacy",
                displayName = "旧知识点",
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                acceptedConfidence = null,
            ),
        )
        val incoming = listOf(
            classification(ClassificationDimension.KNOWLEDGE, "旧知识点", 0.80),
        )

        val outcome = mergeAutomaticClassificationsWithReasons(existing, incoming).single()

        assertEquals(
            "未知存量按策略门解释：理由必须是 incumbent_unknown（不是按分数）",
            "incumbent_unknown",
            outcome.incumbentReason?.wireReason,
        )
        assertTrue("该结果必须真的来自替换：未知行不得冻结", outcome.replacedIncumbent)
        assertEquals(0.80, outcome.suggestion.confidence, 0.0)
    }

    /**
     * K5-A：同一次合并里两条替换路径的**理由可区分**——未知存量替换带 `incumbent_unknown`、
     * 已知存量替换带 `incumbent_known`（按分数）；等值保留的存量不算"替换"；无同名存量的
     * 本轮新提议没有可解释的存量（理由为 null）；用户权威存量不参与自动比较（理由为 null）。
     */
    @Test
    fun replacementEvidenceSeparatesTheGatePolicyFromTheRecordedScore() {
        val existing = listOf(
            binding(
                dimension = ClassificationDimension.KNOWLEDGE,
                labelId = "knowledge-legacy",
                displayName = "旧知识点",
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                acceptedConfidence = null,
            ),
            binding(
                dimension = ClassificationDimension.CHAPTER,
                labelId = "chapter-recorded",
                displayName = "函数",
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                acceptedConfidence = 0.80,
            ),
            binding(
                dimension = ClassificationDimension.KNOWLEDGE,
                labelId = "knowledge-equal",
                displayName = "导数符号",
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                acceptedConfidence = 0.90,
            ),
            binding(
                dimension = ClassificationDimension.CHAPTER,
                labelId = "chapter-user",
                displayName = "数列",
                acceptanceSource = BindingAcceptanceSource.USER_CORRECTED,
                acceptedConfidence = null,
            ),
        )
        val incoming = listOf(
            classification(ClassificationDimension.KNOWLEDGE, "旧知识点", 0.80),
            classification(ClassificationDimension.CHAPTER, "函数", 0.85),
            classification(ClassificationDimension.KNOWLEDGE, "导数符号", 0.90),
            classification(ClassificationDimension.CHAPTER, "数列", 1.0),
            classification(ClassificationDimension.KNOWLEDGE, "新增知识点", 0.95),
        )

        val byName = mergeAutomaticClassificationsWithReasons(existing, incoming)
            .associateBy { it.suggestion.displayName }

        assertEquals(
            "未知存量替换 = 按策略替换",
            "incumbent_unknown",
            byName.getValue("旧知识点").incumbentReason?.wireReason,
        )
        assertTrue(byName.getValue("旧知识点").replacedIncumbent)
        assertEquals(
            "已知存量替换 = 按分数替换",
            "incumbent_known",
            byName.getValue("函数").incumbentReason?.wireReason,
        )
        assertTrue(byName.getValue("函数").replacedIncumbent)
        assertEquals(
            "等值保留存量：理由仍是存量解释，且不得标成替换",
            "incumbent_known",
            byName.getValue("导数符号").incumbentReason?.wireReason,
        )
        assertFalse(byName.getValue("导数符号").replacedIncumbent)
        assertEquals(
            "无同名存量的新提议没有可解释的存量",
            null,
            byName.getValue("新增知识点").incumbentReason,
        )
        assertFalse(byName.getValue("新增知识点").replacedIncumbent)
        assertEquals(
            "用户权威存量不参与自动比较：没有 known/unknown 理由",
            null,
            byName.getValue("数列").incumbentReason,
        )
        assertFalse(byName.getValue("数列").replacedIncumbent)
    }

    /** KF-30 写侧：自动路径写本轮真实分数；用户路径（含离线纠正）没有模型分数 ⇒ NULL。 */
    @Test
    fun automaticWritesCarryRealScoresWhileUserWritesCarryNone() {
        val automatic = buildConfirmationCommand(
            unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
            requestId = "organization-request",
            input = input(),
            classifications = classifications(),
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
        )
        val user = buildConfirmationCommand(
            unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
            requestId = "organization-request",
            input = input(),
            classifications = classifications(),
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.USER_CORRECTED,
        )

        assertEquals(
            "自动路径写建议的真值（0.9 / 0.8）",
            listOf(0.9, 0.8),
            automatic.classifications.map { it.acceptedConfidence },
        )
        assertTrue(
            "用户/离线路径没有模型分数：权威由 acceptanceSource 表达，置信度写 NULL",
            user.classifications.all { it.acceptedConfidence == null },
        )
    }

    /**
     * KF-30 决定：`accepted_confidence` **不进**命令指纹（canonicalPayload）。
     *
     * 判据（不得让「重复确认」意外变成冲突）：真实分数是重确认会刷新的记录值，标签身份没变
     * 就不是新载荷；若进指纹，一次"重复确认 + 分数刷新"会被 `problem_organization_receipt`
     * 判成载荷冲突。本用例钉住"只有分数不同 ⇒ 指纹与 commandId 不变"，同时用非空断言证明
     * 两次命令的记录值确实不同（用例非恒真）。
     */
    @Test
    fun refreshingOnlyTheAcceptedConfidenceKeepsTheCommandIdentity() {
        fun commandWith(unknownKeys: Set<Pair<ClassificationDimension, String>>) =
            buildConfirmationCommand(
                requestId = "organization-request",
                input = input(),
                classifications = classifications(),
                relations = emptyList(),
                acceptedAtEpochMillis = 2_000,
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                unknownAcceptedConfidenceKeys = unknownKeys,
                candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
            )

        val recordedScores = commandWith(emptySet())
        val unknownScores = commandWith(
            classifications().mapTo(linkedSetOf()) { it.dimension to it.displayName },
        )

        assertEquals(
            "只有接受置信度不同，命令指纹必须不变（重复确认不会变成载荷冲突）",
            recordedScores.payloadFingerprint,
            unknownScores.payloadFingerprint,
        )
        assertEquals(
            "commandId 同理（自动路径 = sha256(requestId)，与分数无关）",
            recordedScores.commandId,
            unknownScores.commandId,
        )
        assertEquals(
            "两次命令确实记录了不同的置信度（用例非恒真）",
            listOf(0.9, 0.8),
            recordedScores.classifications.map { it.acceptedConfidence },
        )
        assertTrue(
            "另一份写 NULL",
            unknownScores.classifications.all { it.acceptedConfidence == null },
        )
    }

    /**
     * KF-07：原子提议一个都没能落地（父级对不上本轮知识分类）时，命令不再把全部 topic 节点按
     * strength=1.0 兜底全绑；绑定集为空 = 诚实未分类，由 pseudo 桶接管。
     */
    @Test
    fun unmatchedAtomsLeaveNoTopicFallbackBindings() {
        val orphanAtom = AtomicKnowledgeSuggestion(
            referenceId = "atom-orphan",
            canonicalName = "识别并执行核心运算步骤",
            aliases = emptyList(),
            kind = KnowledgeNodeKind.PROCEDURE,
            parentKnowledgeDisplayName = "另一个未采纳的知识点",
            matchedKnowledgeNodeId = "math-atomic-core-operation",
            prerequisiteReferenceIds = emptyList(),
            observableOutcomeMarkdown = "能独立完成题目中的核心运算步骤。",
            boundaryMarkdown = "只记录本题实际使用的运算能力。",
            confidence = 0.9,
        )

        val command = buildConfirmationCommand(
            unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
            requestId = "unmatched-atom-request",
            input = input(),
            classifications = classifications(),
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
            atomicKnowledge = listOf(orphanAtom),
        )

        assertTrue(command.knowledgeBindings.isEmpty())
        // 分类层级本身不变：topic 节点仍照常物化，只是不再被错绑成掌握证据。
        assertEquals(1, command.knowledgeNodes.size)
        assertEquals(1, command.classifications.count { it.dimension == "KNOWLEDGE" })
    }

    @Test
    fun exactRelationRemovalUsesDeterministicScopedIdentityWithoutFullReplacement() {
        val command = buildConfirmationCommand(
            unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
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
                    unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
                    candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
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
            unknownAcceptedConfidenceKeys = emptySet(), // K3 复核 P2：必填（本夹具无保留旧行）
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
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
            // K3 复核 P2：本参数已改必填（编译期消灭"忘传即伪造门值"）；本用例无保留旧行。
            unknownAcceptedConfidenceKeys = emptySet(),
            candidateSource = ClassificationCandidateSource.NONE, // K5-B：本夹具不落候选
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
        assertEquals(1, first.knowledgeNodes.size)
        // KF-07：离线纠正没有可校验的原子节点，不再伪造 topic 绑定；pseudo 桶接管归属。
        assertTrue(first.knowledgeBindings.isEmpty())
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

    /**
     * K5-B（schema 65）：确认命令带**全量模型候选**（含被拒候选）与自报分数——"被拒候选也要落"
     * 是本项的全部意义。三条边界：被接受=确认集 ∩ 本轮放行；没过门/被预算挤掉的候选照样落行、
     * 分数原样保留；候选集**不进**命令指纹（它是"考虑过什么"的记录，不是载荷）。
     */
    @Test
    fun rejectedModelCandidatesAreRecordedWithTheirSelfReportedScores() {
        val admitted = listOf(
            classification(ClassificationDimension.CHAPTER, "函数", 0.80),
            classification(ClassificationDimension.KNOWLEDGE, "二次函数最值", 0.90),
        )
        val rejected = classification(ClassificationDimension.KNOWLEDGE, "导数符号", 0.40)
        val source = ClassificationCandidateSource(
            candidates = admitted + rejected,
            acceptedKeys = admitted.mapTo(hashSetOf()) { it.dimension to it.displayName.lowercase() },
        )
        val command = buildConfirmationCommand(
            requestId = "organization-request",
            input = input(),
            classifications = admitted,
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
            unknownAcceptedConfidenceKeys = emptySet(),
            candidateSource = source,
        )

        assertEquals("全量候选都要落（含被拒）", 3, command.classificationCandidates.size)
        val byName = command.classificationCandidates.associateBy { it.displayName }
        assertEquals(
            "被接受候选：确认集里且本轮放行 ⇒ accepted = true",
            listOf(true, true),
            listOf(byName.getValue("函数").accepted, byName.getValue("二次函数最值").accepted),
        )
        val rejectedRow = byName.getValue("导数符号")
        assertFalse("没过门的候选必须原样落成被拒", rejectedRow.accepted)
        assertEquals("被拒候选的自报分数必须原样保留（选择偏差要看的就是它）", 0.40, rejectedRow.confidence, 0.0)
        assertEquals("LOCAL_POLICY_ACCEPTED", rejectedRow.acceptanceSource)
        assertEquals(2_000L, rejectedRow.acceptedAtEpochMillis)
        assertEquals(
            "同一轮同一候选身份只有一行（跨轮由 acceptedAt 区分）",
            command.classificationCandidates.map { it.candidateId }.distinct().size,
            command.classificationCandidates.size,
        )
        assertEquals(
            "候选身份按 (revision, acceptedAt, dimension, labelId) 内容寻址：重放得到同一批 id",
            command.classificationCandidates,
            buildConfirmationCommand(
                requestId = "organization-request",
                input = input(),
                classifications = admitted,
                relations = emptyList(),
                acceptedAtEpochMillis = 2_000,
                acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
                unknownAcceptedConfidenceKeys = emptySet(),
                candidateSource = source,
            ).classificationCandidates,
        )
        val withoutCandidates = buildConfirmationCommand(
            requestId = "organization-request",
            input = input(),
            classifications = admitted,
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
            unknownAcceptedConfidenceKeys = emptySet(),
            candidateSource = ClassificationCandidateSource.NONE,
        )
        assertEquals(
            "候选集不进命令指纹：记录「考虑过什么」不改变命令身份（重复确认不得变成载荷冲突）",
            withoutCandidates.payloadFingerprint,
            command.payloadFingerprint,
        )
    }

    /**
     * K5-B：只看"确认集里有没有同名标签"会把**重提同名旧标签但本轮被拒**误记成接受——
     * 接受标记必须同时要求"本轮放行"（[ClassificationCandidateSource.acceptedKeys]）。
     */
    @Test
    fun aCandidateWhoseLabelExistsButWasNotAdmittedThisRoundStaysRejected() {
        val command = buildConfirmationCommand(
            requestId = "organization-request",
            input = input(),
            classifications = classifications(),
            relations = emptyList(),
            acceptedAtEpochMillis = 2_000,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
            unknownAcceptedConfidenceKeys = emptySet(),
            candidateSource = ClassificationCandidateSource(
                candidates = listOf(classification(ClassificationDimension.KNOWLEDGE, "二次函数最值", 0.40)),
                acceptedKeys = emptySet(),
            ),
        )

        assertFalse(
            "同名标签在确认集里 ≠ 本候选被接受（本轮没放行）",
            command.classificationCandidates.single().accepted,
        )
    }

    /** K5-B：离线纠正没有模型输出——候选集必须是空的（显式 NONE，不是"忘了传"）。 */
    @Test
    fun offlineCorrectionRecordsNoModelCandidates() {
        val command = buildOfflineCorrectionCommand(
            entryId = "entry-1",
            current = OfflineCorrectionFacts(
                problemId = "problem-1",
                problemRevisionId = "revision-1",
                practiceUnitId = "practice-1",
                subject = SubjectKind.MATH,
                questionDocument = document("question-1", "求函数最值"),
            ),
            userClassifications = listOf(
                UserProblemClassification(ClassificationDimension.CHAPTER, "函数"),
                UserProblemClassification(ClassificationDimension.KNOWLEDGE, "二次函数最值"),
            ),
            correctedAtEpochMillis = 4_000,
        )

        assertTrue("离线纠正没有模型候选可落", command.classificationCandidates.isEmpty())
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
        acceptanceSource: BindingAcceptanceSource = BindingAcceptanceSource.USER_CORRECTED,
        // KF-30：存量行的真实接受置信度；null = 未知（升级前旧行）。
        acceptedConfidence: Double? = null,
    ) = com.tingyun.smartmistakebook.core.database.ProblemClassificationBindingRecord(
        bindingId = "binding-$labelId",
        problemId = "problem-1",
        basisRevisionId = "revision-1",
        dimension = dimension.name,
        labelId = labelId,
        displayName = displayName,
        taxonomyVersion = "user-corrected-v1",
        acceptanceSource = acceptanceSource.name,
        acceptedAtEpochMillis = 1,
        acceptedConfidence = acceptedConfidence,
    )

    /** 一组自动接受的章节标签（KF-30 合并用例的存量侧）。 */
    private fun autoChapters(vararg displayNames: String) = displayNames.mapIndexed { index, name ->
        binding(
            dimension = ClassificationDimension.CHAPTER,
            labelId = "chapter-auto-$index",
            displayName = name,
            acceptanceSource = BindingAcceptanceSource.LOCAL_POLICY_ACCEPTED,
        )
    }

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
