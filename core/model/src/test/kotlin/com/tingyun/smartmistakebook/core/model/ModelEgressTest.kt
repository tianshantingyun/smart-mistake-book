package com.tingyun.smartmistakebook.core.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import com.tingyun.smartmistakebook.core.model.RelatedProblemCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelEgressTest {
    @Test
    fun preparedRequestBudgetKeepsSmallImagePayloadsUnchanged() {
        val nonImageJsonBytes = 1_024L

        val estimatedBytes = ModelRequestPayloadBudget.requirePreparedRequestFits(
            nonImageJsonUtf8Bytes = nonImageJsonBytes,
            assetByteSizes = listOf(4L, 5L),
        )

        assertEquals(nonImageJsonBytes + 8L + 8L, estimatedBytes)
    }

    @Test
    fun preparedRequestBudgetRejectsAnOversizedMultiPageTotal() {
        val failure = runCatching {
            ModelRequestPayloadBudget.requirePreparedRequestFits(
                nonImageJsonUtf8Bytes = 1_024L,
                assetByteSizes = listOf(14L * 1_024L * 1_024L, 14L * 1_024L * 1_024L),
            )
        }.exceptionOrNull()

        assertTrue(failure is ModelRequestBudgetExceededException)
        assertEquals("Model request exceeds the upload budget", failure?.message)
    }

    @Test
    fun preparedRequestBudgetFailsClosedWhenBase64ArithmeticWouldOverflow() {
        val failure = runCatching {
            ModelRequestPayloadBudget.requirePreparedRequestFits(
                nonImageJsonUtf8Bytes = 0L,
                assetByteSizes = listOf(Long.MAX_VALUE),
            )
        }.exceptionOrNull()

        assertTrue(failure is ModelRequestBudgetExceededException)
    }

    @Test
    fun exactCaptureApprovalAuthorizesOnlyTheBoundExternalProviderAndAsset() {
        val request = request(manifest())

        val execution = ModelEgressPolicy.authorize(request, externalProvider(), 101)

        assertTrue(execution.permit is ModelExecutionPermit.External)
        assertEquals(request, execution.request)
    }

    @Test
    fun externalProviderCannotRunWithoutStudentApproval() {
        val failure = runCatching {
            ModelEgressPolicy.authorize(request(manifest = null), externalProvider(), 101)
        }.exceptionOrNull() as ModelEgressAuthorizationException

        assertEquals(ModelFailureCode.EGRESS_AUTHORIZATION_REQUIRED, failure.failureCode)
    }

    @Test
    fun providerConfigurationChangeInvalidatesAnExistingApproval() {
        val failure = runCatching {
            ModelEgressPolicy.authorize(
                request(manifest()),
                externalProvider().copy(providerConfigurationVersion = "provider-config-v2"),
                101,
            )
        }.exceptionOrNull() as ModelEgressAuthorizationException

        assertEquals(ModelFailureCode.EGRESS_AUTHORIZATION_INVALID, failure.failureCode)
    }

    @Test
    fun captureApprovalRejectsAnyUndisclosedExtraDataClass() {
        val extra = ModelEgressDataClass.RELEVANT_LEARNING_EVIDENCE

        val failure = runCatching {
            manifest().copy(
                disclosedData = ModelEgressManifest.CAPTURE_IMAGE_DISCLOSURE + extra,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun legacySchemaOneFingerprintStillMatchesItsOriginalJsonShape() {
        val legacyJson =
            """{"schemaVersion":1,"requestId":"capture-assess:legacy","input":{"type":"capture_assessment","draftId":"draft-1","sourceAssetId":"asset-1","origin":"LIBRARY","imageWidth":1080,"imageHeight":1440},"occurredAtEpochMillis":100}"""
        val request = ModelTaskCodec.decodeRequest(legacyJson)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(legacyJson.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(1, request.schemaVersion)
        assertEquals(expected, ModelTaskFingerprint.of(request))
    }

    @Test
    fun schemaOneTutorPlanFingerprintOmitsNewStudentContextField() {
        val request = legacyTutorPlanRequest().copy(
            schemaVersion = 1,
            egressManifest = null,
        )
        val legacyJson = ModelTaskCodec.encodeRequest(request)
            .replace(",\"priorCycleStudentMessages\":[]", "")
            .replace(",\"egressManifest\":null", "")
            .replace(",\"agentConsentGranted\":false", "")
            // schema 1 时代还没有 schema 13 引入的 Plan 载体键——当年的编码不含它们。
            .replace(",\"toolDeclarations\":[]", "")
            .replace(",\"toolRoundResults\":[]", "")
            .replace(",\"knowledgeCodes\":[]", "")
            .replace(",\"teachingReferencesLoadFailed\":false", "")
        val decoded = ModelTaskCodec.decodeRequest(legacyJson)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(legacyJson.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, ModelTaskFingerprint.of(decoded))
    }

    @Test
    fun persistedSchemaOneTutorPlanManifestStillDecodesButCannotAuthorizeACurrentPrompt() {
        val request = legacyTutorPlanRequest()
        val decoded = ModelTaskCodec.decodeRequest(ModelTaskCodec.encodeRequest(request))

        assertEquals(1, requireNotNull(decoded.egressManifest).schemaVersion)
        assertTrue(
            runCatching {
            ModelEgressPolicy.authorize(
                decoded,
                tutorProvider(ModelTaskKind.TUTOR_PLAN),
                101,
            )
            }.exceptionOrNull() is ModelEgressAuthorizationException,
        )
        assertTrue(
            runCatching {
                legacyTutorPlanManifest().copy(
                    authorizedTaskKinds = setOf(ModelTaskKind.TUTOR_RESPOND),
                    disclosedData = ModelEgressManifest.TUTOR_RESPOND_DISCLOSURE,
                )
            }.isFailure,
        )
    }

    @Test
    fun persistedSchemaTwoTutorPlanManifestKeepsItsOriginalDisclosure() {
        val legacyManifest = legacyTutorPlanManifest().copy(
            schemaVersion = 2,
        )
        val request = legacyTutorPlanRequest().copy(
            schemaVersion = 2,
            egressManifest = legacyManifest,
        )
        val encoded = ModelTaskCodec.encodeRequest(request)
        // 当年这份 schema 2 行的形状：`prohibitedData` 是**派生键**（schema 2 的宇宙 − 已披露）。
        // 数组内容**手工写出**（不从生产代码算），这样本用例才能真正钉住"复原出来的是当年那份字节"：
        // schema 2 的宇宙 = 全枚举 − MODEL_AUTHORED_VISUAL_CANDIDATE（schema<5 的清单不含该成员），
        // 已披露 = LEGACY_TUTOR_PLAN_DISCLOSURE（题面 / 学习证据 / 题级学习证据）。
        val legacyJson = encoded.replace(",\"priorCycleStudentMessages\":[]", "")
            .replace(",\"agentConsentGranted\":false", "")
            // schema 2 时代还没有 schema 13 引入的 Plan 载体键——当年的编码不含它们。
            .replace(",\"toolDeclarations\":[]", "")
            .replace(",\"toolRoundResults\":[]", "")
            .replace(",\"knowledgeCodes\":[]", "")
            .replace(",\"teachingReferencesLoadFailed\":false", "")
            .replace(
                ",\"disclosedData\":[\"CONFIRMED_QUESTION_DOCUMENT\",\"RELEVANT_LEARNING_EVIDENCE\"," +
                    "\"QUESTION_LEARNING_EVIDENCE\"]",
                ",\"disclosedData\":[\"CONFIRMED_QUESTION_DOCUMENT\",\"RELEVANT_LEARNING_EVIDENCE\"," +
                    "\"QUESTION_LEARNING_EVIDENCE\"],\"prohibitedData\":[\"SANITIZED_IMAGE_BYTES\"," +
                    "\"IMAGE_DIMENSIONS\",\"SELECTED_IMAGE_REGION\",\"RELATED_QUESTION_CANDIDATES\"," +
                    "\"SUBJECT_KNOWLEDGE_BASE\",\"OTHER_CAPTURE_ASSETS\",\"FULL_LEARNING_HISTORY\"," +
                    "\"API_CREDENTIALS\",\"CAPTURE_METADATA\",\"STUDENT_TUTOR_MESSAGE\"," +
                    "\"TUTOR_CONVERSATION_CONTEXT\"]",
            )
        val expectedFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(legacyJson.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val decoded = ModelTaskCodec.decodeRequest(legacyJson)

        assertEquals(2, decoded.schemaVersion)
        assertEquals(2, requireNotNull(decoded.egressManifest).schemaVersion)
        assertEquals(expectedFingerprint, ModelTaskFingerprint.of(decoded))
        assertTrue(
            runCatching {
            ModelEgressPolicy.authorize(
                decoded,
                tutorProvider(ModelTaskKind.TUTOR_PLAN),
                101,
            )
            }.exceptionOrNull() is ModelEgressAuthorizationException,
        )
    }

    @Test
    fun currentTutorPlanDisclosureIncludesBoundedConversationContext() {
        assertEquals(
            ModelEgressManifest.LEGACY_TUTOR_PLAN_DISCLOSURE + setOf(
                ModelEgressDataClass.STUDENT_TUTOR_MESSAGE,
                ModelEgressDataClass.TUTOR_CONVERSATION_CONTEXT,
                ModelEgressDataClass.SUBJECT_KNOWLEDGE_BASE,
            ),
            ModelEgressManifest.TUTOR_PLAN_DISCLOSURE,
        )
    }

    @Test
    fun tutorResponseApprovalUsesTheExactNewDisclosure() {
        val manifest = tutorRespondManifest()
        val request = tutorRespondRequest(manifest)

        assertEquals(
            setOf(
                ModelEgressDataClass.CONFIRMED_QUESTION_DOCUMENT,
                ModelEgressDataClass.RELEVANT_LEARNING_EVIDENCE,
                ModelEgressDataClass.QUESTION_LEARNING_EVIDENCE,
                ModelEgressDataClass.STUDENT_TUTOR_MESSAGE,
                ModelEgressDataClass.TUTOR_CONVERSATION_CONTEXT,
                ModelEgressDataClass.SUBJECT_KNOWLEDGE_BASE,
            ),
            manifest.disclosedData,
        )
        assertTrue(
            ModelEgressPolicy.authorize(
                request,
                tutorProvider(ModelTaskKind.TUTOR_RESPOND),
                101,
            ).permit is ModelExecutionPermit.External,
        )
        assertTrue(
            runCatching {
                manifest.copy(
                    disclosedData = manifest.disclosedData -
                        ModelEgressDataClass.TUTOR_CONVERSATION_CONTEXT,
                )
            }.isFailure,
        )
    }

    // ---- D-K4 删除的授权时效用例（manifest 路径不再核对 TTL）----
    //
    // 此前这里有一条 `staleOrFutureApprovalIsRejectedBeforeExternalExecution`：过期（+15 分钟）
    // 与未来时刻（+2 分钟时钟偏移）都被拒。它跟着 `requireAuthorizes` 的
    // `isModelEgressApprovalFresh` 调用一起删除：清单时刻（`approvedAtEpochMillis`）的唯一来源是
    // 客户端自己刚写的当前时间，所有派发/恢复点都在同一帧重盖（研究报告 §4.6 R5），判据在运行时
    // 永远成立——它拦不住任何真实请求。保留的时效语义只有两处：
    // ① `TutorAutoStartAuthorization.matches` 的"刚拍完可以直接开始讲题"租约（独立测试）；
    // ② 下面两条 tutor 轮的"授权可以早于请求"豁免，以及大厅那条"授权不得早于请求"的断言
    //    （`approvedAtEpochMillis >= occurredAtEpochMillis`，仍在 requireAuthorizes 里）。
    @Test
    fun tutorConversationGrantMayPrecedeANewMessageWhileItIsStillFresh() {
        val manifest = tutorRespondManifest().copy(approvedAtEpochMillis = 90)

        val execution = ModelEgressPolicy.authorize(
            tutorRespondRequest(manifest),
            tutorProvider(ModelTaskKind.TUTOR_RESPOND),
            101,
        )

        assertTrue(execution.permit is ModelExecutionPermit.External)
    }

    @Test
    fun tutorConversationGrantMayPrecedeANewPlanWhileItIsStillFresh() {
        val request = legacyTutorPlanRequest().copy(
            schemaVersion = ModelTaskRequest.CURRENT_SCHEMA_VERSION,
            egressManifest = currentTutorPlanManifest().copy(approvedAtEpochMillis = 90),
        )

        val execution = ModelEgressPolicy.authorize(
            request,
            tutorProvider(ModelTaskKind.TUTOR_PLAN),
            101,
        )

        assertTrue(execution.permit is ModelExecutionPermit.External)
    }

    @Test
    fun lobbyApprovalFromTheSameSendDecisionAuthorizesTheMessage() {
        val execution = ModelEgressPolicy.authorize(
            tutorLobbyRequest(tutorLobbyManifest(approvedAtEpochMillis = 100))
                .copy(occurredAtEpochMillis = 100),
            tutorProvider(ModelTaskKind.TUTOR_LOBBY),
            100,
        )

        assertTrue(execution.permit is ModelExecutionPermit.External)
    }

    @Test
    fun lobbyApprovalOneMillisecondOlderThanTheMessageIsRejected() {
        // 大厅发送一度各自读一次时钟：授权时刻与请求时刻只要错开一毫秒，请求就在任何网络
        // 动作之前被本地拒掉（stage=PREPARING、attempt=0），学生看到"刚发出去一秒就说
        // 没准备好"。这条要求本身是对的——过期的授权不得给新请求背书——所以修法只能是
        // 让两个时刻来自同一次用户动作，而不是放宽这条校验。
        val rejected = runCatching {
            ModelEgressPolicy.authorize(
                tutorLobbyRequest(tutorLobbyManifest(approvedAtEpochMillis = 99))
                    .copy(occurredAtEpochMillis = 100),
                tutorProvider(ModelTaskKind.TUTOR_LOBBY),
                100,
            )
        }.exceptionOrNull() as ModelEgressAuthorizationException

        assertEquals(ModelFailureCode.EGRESS_AUTHORIZATION_INVALID, rejected.failureCode)
    }

    @Test
    fun aLobbyMessageMayCarryEarlierImagesOnlyWithinItsGrantedScope() {
        // 追问带上文图片：授权范围必须逐张覆盖它们。少授权一张就拒绝——图片不因为来自
        // 历史消息而少一分披露；这些字节这次同样要出网。
        val carried = CaptureSourceAssetRef(
            assetId = "asset-old",
            sha256 = "b".repeat(64),
            width = 1_080,
            height = 1_440,
            pageIndex = 0,
        )
        val granted = tutorLobbyManifest(
            approvedAtEpochMillis = 100,
        ).copy(
            assets = listOf(
                ModelEgressAssetGrant(
                    assetId = "asset-old",
                    sha256 = "b".repeat(64),
                    byteSize = 2_048,
                    width = 1_080,
                    height = 1_440,
                ),
            ),
            disclosedData = ModelEgressManifest.TUTOR_LOBBY_IMAGE_DISCLOSURE,
        )

        val authorized = ModelEgressPolicy.authorize(
            tutorLobbyRequest(granted).copy(
                input = TutorLobbyInput(
                    conversationId = "tutor-conv-1",
                    messageOrdinal = 2,
                    studentMessage = "第三题",
                    contextImageAssetRefs = listOf(carried),
                ),
                occurredAtEpochMillis = 100,
            ),
            tutorProvider(ModelTaskKind.TUTOR_LOBBY),
            100,
        )
        val withoutGrant = runCatching {
            // 只授权纯文本的清单：这次偏偏要带一张上文图片出网，必须被拒。
            ModelEgressPolicy.authorize(
                tutorLobbyRequest(tutorLobbyManifest(approvedAtEpochMillis = 100)).copy(
                    input = TutorLobbyInput(
                        conversationId = "tutor-conv-1",
                        messageOrdinal = 2,
                        studentMessage = "第三题",
                        contextImageAssetRefs = listOf(carried),
                    ),
                    occurredAtEpochMillis = 100,
                ),
                tutorProvider(ModelTaskKind.TUTOR_LOBBY),
                100,
            )
        }.exceptionOrNull() as ModelEgressAuthorizationException

        assertTrue(authorized.permit is ModelExecutionPermit.External)
        assertEquals(ModelFailureCode.EGRESS_AUTHORIZATION_INVALID, withoutGrant.failureCode)
    }

    private fun request(manifest: ModelEgressManifest?) = ModelTaskRequest(
        requestId = "capture-assess:request-1",
        input = CaptureAssessmentInput(
            draftId = "draft-1",
            sourceAssetId = "asset-1",
            origin = CaptureAssessmentOrigin.LIBRARY,
            imageWidth = 1080,
            imageHeight = 1440,
        ),
        occurredAtEpochMillis = 100,
        egressManifest = manifest,
    )

    private fun manifest() = ModelEgressManifest(
        authorizationId = "approval-1",
        subjectId = "draft-1",
        purpose = ModelEgressPurpose.CAPTURE_TO_DOCUMENT,
        authorizedTaskKinds = setOf(
            ModelTaskKind.CAPTURE_ASSESS,
            ModelTaskKind.CAPTURE_PARSE,
        ),
        providerId = "provider-1",
        modelId = "vision-model-1",
        providerConfigurationVersion = "provider-config-v1",
        promptPolicyVersion = ModelPromptPolicyVersions.CAPTURE_DOCUMENT,
        approvedAtEpochMillis = 101,
        assets = listOf(
            ModelEgressAssetGrant(
                assetId = "asset-1",
                sha256 = "a".repeat(64),
                byteSize = 2_048,
                width = 1080,
                height = 1440,
            ),
        ),
        disclosedData = setOf(
            ModelEgressDataClass.SANITIZED_IMAGE_BYTES,
            ModelEgressDataClass.IMAGE_DIMENSIONS,
        ),
    )

    private fun legacyTutorPlanRequest() = ModelTaskRequest(
        requestId = "tutor-plan:legacy-request",
        input = TutorPlanInput(
            sessionId = "tutor-session-1",
            draftRevisionNumber = 2,
            subject = "MATH",
            questionDocument = confirmedQuestion(),
            relevantLearningEvidence = emptyList(),
            projectionIsCurrent = true,
        ),
        occurredAtEpochMillis = 100,
        egressManifest = legacyTutorPlanManifest(),
    )

    private fun legacyTutorPlanManifest() = ModelEgressManifest(
        schemaVersion = 1,
        authorizationId = "tutor-plan-legacy-approval",
        subjectId = TutorConversationIds.captured("tutor-session-1"),
        purpose = ModelEgressPurpose.TUTORING,
        authorizedTaskKinds = setOf(ModelTaskKind.TUTOR_PLAN),
        providerId = "provider-1",
        modelId = "vision-model-1",
        providerConfigurationVersion = "provider-config-v1",
        promptPolicyVersion = "tutor-plan-v1",
        approvedAtEpochMillis = 101,
        assets = emptyList(),
        disclosedData = ModelEgressManifest.LEGACY_TUTOR_PLAN_DISCLOSURE,
    )

    /**
     * 学生可以在讲题会话里附上自己的图片，而 Respond 走的是"已配置模型 = 全局同意"这条通道，
     * 没有逐次披露清单兜底。所以图片能否出网必须由图片能力门自己把关：文本模型既拿不到
     * 全局同意的放行，也没有 manifest 可依，只能失败关闭。
     */
    @Test
    fun anImageBearingRespondRoundNeedsAnImageCapableProvider() {
        val request = ModelTaskRequest(
            requestId = "tutor-respond:message-images",
            input = TutorRespondInput(
                sessionId = "tutor-session-1",
                draftRevisionNumber = 2,
                subject = "MATH",
                questionDocument = confirmedQuestion(),
                relevantLearningEvidence = emptyList(),
                projectionIsCurrent = true,
                responseOrdinal = 1,
                studentMessage = "看看我写的这一步对不对。",
                studentImageAssetRefs = listOf("asset-1"),
            ),
            occurredAtEpochMillis = 100,
            agentConsentGranted = true,
        )

        val denied = runCatching {
            ModelEgressPolicy.authorize(
                request = request,
                provider = tutorProvider(ModelTaskKind.TUTOR_RESPOND),
                nowEpochMillis = 100,
            )
        }.exceptionOrNull()
        assertTrue(
            "A text-only provider must not receive a student image: $denied",
            denied is ModelEgressAuthorizationException,
        )

        val authorized = ModelEgressPolicy.authorize(
            request = request,
            provider = tutorProvider(ModelTaskKind.TUTOR_RESPOND).copy(supportsImageInput = true),
            nowEpochMillis = 100,
        )
        assertEquals(ModelExecutionPermit.ProviderConsented, authorized.permit)
    }

    /** 纯文本的 Respond 不受图片能力限制：它本来就不带图片字节。 */
    @Test
    fun aTextOnlyRespondRoundStillRunsOnATextOnlyProvider() {
        val authorized = ModelEgressPolicy.authorize(
            request = tutorRespondRequest(tutorRespondManifest()).copy(
                egressManifest = null,
                agentConsentGranted = true,
            ),
            provider = tutorProvider(ModelTaskKind.TUTOR_RESPOND),
            nowEpochMillis = 100,
        )

        assertEquals(ModelExecutionPermit.ProviderConsented, authorized.permit)
    }

    private fun currentTutorPlanManifest() = ModelEgressManifest(
        authorizationId = "tutor-plan-current-approval",
        subjectId = TutorConversationIds.captured("tutor-session-1"),
        purpose = ModelEgressPurpose.TUTORING,
        authorizedTaskKinds = setOf(ModelTaskKind.TUTOR_PLAN),
        providerId = "provider-1",
        modelId = "vision-model-1",
        providerConfigurationVersion = "provider-config-v1",
        promptPolicyVersion = ModelPromptPolicyVersions.TUTOR_PLAN,
        approvedAtEpochMillis = 101,
        assets = emptyList(),
        disclosedData = ModelEgressManifest.TUTOR_PLAN_DISCLOSURE,
    )

    // ---- 披露口径（测试锁，不是运行时门）----
    // 每一态断言"disclosedData == expected"：这是**共享口径**本身（`TutorRoundDisclosure` 是唯一
    // 计算入口，`ModelEgressManifest.init` 逐 kind 要求披露集合精确等于它），任何一边单独改动都会
    // 在这里对不上。此前每条还断言"未披露集 == 全集 − 已披露"——那个派生键（`prohibitedData`）
    // 已随 D-K4 删除（研究报告 §4.6 R6：生产读取 0 处），它的守恒由 init 的精确相等校验蕴含，
    // 不再需要一份常量副本。
    //
    // 这里此前还有一条 `a question round with an image additionally discloses image classes`
    // 的逐态用例。它测的是**生产里不可达**的组合（题轮 + 图字节）：三个校验调用点没有一个能
    // 传入这一组合（init 里两个标志由同一个 kind 派生而互斥、Respond 分支硬编码无图且
    // `assets` 必须为空、Lobby 分支 carriesQuestion=false），函数收敛成两个具名入口之后
    // 这个组合**构造不出来**，所以用例随之删除——它不是被放宽，而是不再有对应的状态。

    @Test
    fun `a no-question round discloses no question document`() {
        val expected = TutorRoundDisclosure.noQuestionRound(includesImage = false)

        assertEquals(ModelEgressManifest.TUTOR_LOBBY_DISCLOSURE, expected)
        assertFalse(ModelEgressDataClass.CONFIRMED_QUESTION_DOCUMENT in expected)
    }

    @Test
    fun `a question round discloses the confirmed question document`() {
        val expected = TutorRoundDisclosure.questionRound(includesQuestionCandidates = false)

        assertEquals(ModelEgressManifest.TUTOR_RESPOND_DISCLOSURE, expected)
        assertTrue(ModelEgressDataClass.CONFIRMED_QUESTION_DOCUMENT in expected)
        // 题轮不带图片类目：生产里题轮根本不走清单路径（agent-eligible，走全局同意通道），
        // 图字节的边界由请求侧资产 + provider 图片能力 + 资产源逐字节核对共同把住。
        assertFalse(ModelEgressDataClass.SANITIZED_IMAGE_BYTES in expected)
        assertFalse(ModelEgressDataClass.IMAGE_DIMENSIONS in expected)
    }

    @Test
    fun `a no-question round with an image keeps the existing lobby image disclosure`() {
        // 这一态不是本轮新加的，而是大厅既有的附图通道：写成同一口径后取值必须逐字不变。
        val expected = TutorRoundDisclosure.noQuestionRound(includesImage = true)

        assertEquals(ModelEgressManifest.TUTOR_LOBBY_IMAGE_DISCLOSURE, expected)
        assertFalse(ModelEgressDataClass.CONFIRMED_QUESTION_DOCUMENT in expected)
    }

    @Test
    fun `a carried candidate menu is disclosed as its own data class`() {
        val withMenu = TutorRoundDisclosure.questionRound(includesQuestionCandidates = true)

        assertEquals(
            ModelEgressManifest.TUTOR_RESPOND_DISCLOSURE +
                ModelEgressDataClass.RELATED_QUESTION_CANDIDATES,
            withMenu,
        )
        assertFalse(ModelEgressDataClass.RELATED_QUESTION_CANDIDATES in ModelEgressManifest.TUTOR_RESPOND_DISCLOSURE)
    }

    @Test
    fun `only a question round that carries a menu discloses other questions`() {
        // 这一个判据同时被两处消费：清单侧核对 `includesQuestionCandidates`，core:data 侧决定
        // NOTEBOOK_READ 能不能逐条点名别的题。把三种输入钉在一处，免得两侧对"覆盖了菜单"
        // 各有一套说法。
        assertFalse(
            tutorRespondRequest(tutorRespondManifest()).input.disclosesQuestionCandidates(),
        )
        assertTrue(
            tutorRespondRequestWithMenu(tutorRespondManifest()).input.disclosesQuestionCandidates(),
        )
        assertFalse(tutorLobbyRequest(tutorLobbyManifest()).input.disclosesQuestionCandidates())
    }

    @Test
    fun `a lobby manifest cannot claim to cover a candidate menu`() {
        // 无题轮不存在候选菜单（`TutorLobbyInput` 没有菜单字段），所以"清单说覆盖了菜单"就是
        // **多报**一个本轮根本不存在的类目。这条断言此前只在 requireAuthorizes 里成立（init
        // 会把该标志透传给披露计算并因此放行这种清单），现在构造期就拒——清单一旦落库就是
        // 耐久记录，能在构造期拦住的错误不该等到逐次核对。
        val covered = ModelEgressManifest.TUTOR_LOBBY_DISCLOSURE +
            ModelEgressDataClass.RELATED_QUESTION_CANDIDATES

        assertThrows(IllegalArgumentException::class.java) {
            tutorLobbyManifest().copy(
                schemaVersion = ModelEgressManifest.CURRENT_SCHEMA_VERSION,
                disclosedData = covered,
                includesQuestionCandidates = true,
            )
        }
    }

    @Test
    fun `a manifest that covers the candidate menu authorizes the round`() {
        val covered = ModelEgressManifest.TUTOR_RESPOND_DISCLOSURE +
            ModelEgressDataClass.RELATED_QUESTION_CANDIDATES
        val manifest = tutorRespondManifest().copy(
            disclosedData = covered,
            includesQuestionCandidates = true,
        )

        val execution = ModelEgressPolicy.authorize(
            request = tutorRespondRequestWithMenu(manifest),
            provider = tutorProvider(ModelTaskKind.TUTOR_RESPOND),
            nowEpochMillis = 101,
        )

        assertTrue(execution.permit is ModelExecutionPermit.External)
    }

    // ---- D-K4 删除的三个"逐次核对"用例（**没有**对应的运行时门了，故不留测试）----
    //
    // ① `a manifest that understates the candidate menu is rejected` /
    //    `a manifest that overstates the candidate menu is rejected too`：两条都由
    //    `requireAuthorizes` 的 Respond 分支持核对 `includesQuestionCandidates` 与请求是否一致。
    //    该分支不可达——**生产里题轮不带清单**（`TutorModelTaskPolicy` 的 `egressManifest = null`，
    //    agent-eligible 走全局同意通道），所以它拦不住任何真实请求：要给这两条用例造出场景，
    //    必须手工拼一份生产不存在的"题轮 + 清单"。真正的边界在请求侧事实本身
    //    （`disclosesQuestionCandidates()` 由 `core:data` 的 NOTEBOOK_READ 消费）与 init 的
    //    schema 门（schema<7 的旧清单不得声称覆盖菜单），后两条仍有测试。
    // ② `a question round manifest still refuses image assets`：同样只在那条不可达分支里成立。
    //    有题带图的图字节边界在别处：请求侧真的带了哪个资产（`studentImageAssetRefs`）+
    //    provider 支持图片输入（`agentConsentMatches`/`requiresImageInput`）+ 资产源逐字节核对
    //    （`AndroidRestrictedModelAssetSource`，`core:data` 有独立测试）。
    //
    // 这份说明是删除依据，不是"测试锁"：被删掉的行为已经不存在，留一条会绿的用例只会让人
    // 以为那两道门还在。

    @Test
    fun `a legacy manifest cannot claim to cover a candidate menu`() {
        assertThrows(IllegalArgumentException::class.java) {
            tutorRespondManifest().copy(
                schemaVersion = 6,
                includesQuestionCandidates = true,
            )
        }
    }

    private fun tutorRespondRequestWithMenu(manifest: ModelEgressManifest) = ModelTaskRequest(
        requestId = "tutor-respond:request-menu",
        input = TutorRespondInput(
            sessionId = "tutor-session-1",
            draftRevisionNumber = 2,
            subject = "MATH",
            questionDocument = confirmedQuestion(),
            relevantLearningEvidence = emptyList(),
            projectionIsCurrent = true,
            responseOrdinal = 1,
            cycleOrdinal = 1,
            turnOrdinal = 1,
            studentMessage = "再把光的折射那道题讲一遍",
            boundQuestionCandidates = listOf(
                RelatedProblemCandidate(
                    problemId = "problem-other",
                    problemRevisionId = "revision-other",
                    subject = SubjectKind.PHYSICS,
                    title = "光的折射实验",
                    questionDocument = QuestionDocument(
                        id = "question-other",
                        blocks = listOf(
                            ContentBlock.Paragraph("stem-other", "入射角与折射角的关系"),
                        ),
                    ),
                ),
            ),
        ),
        occurredAtEpochMillis = 100,
        egressManifest = manifest,
    )

    private fun tutorRespondRequest(manifest: ModelEgressManifest) = ModelTaskRequest(
        requestId = "tutor-respond:request-1",
        input = TutorRespondInput(
            sessionId = "tutor-session-1",
            draftRevisionNumber = 2,
            subject = "MATH",
            questionDocument = confirmedQuestion(),
            relevantLearningEvidence = emptyList(),
            projectionIsCurrent = true,
            responseOrdinal = 1,
            studentMessage = "请解释当前题这一步。",
        ),
        occurredAtEpochMillis = 100,
        egressManifest = manifest,
    )

    private fun tutorRespondManifest() = ModelEgressManifest(
        authorizationId = "tutor-respond-approval",
        // 清单主语必须与 `TutorRespondInput.subjectId` 同口径（K1c：会话 id 的派生对话 id），
        // 否则清单在 requireAuthorizes 的主语核对上就被拒——这正是那条断言要拦的错配。
        subjectId = TutorConversationIds.captured("tutor-session-1"),
        purpose = ModelEgressPurpose.TUTORING,
        authorizedTaskKinds = setOf(ModelTaskKind.TUTOR_RESPOND),
        providerId = "provider-1",
        modelId = "vision-model-1",
        providerConfigurationVersion = "provider-config-v1",
        promptPolicyVersion = ModelPromptPolicyVersions.TUTOR_RESPOND,
        approvedAtEpochMillis = 101,
        assets = emptyList(),
        disclosedData = ModelEgressManifest.TUTOR_RESPOND_DISCLOSURE,
    )

    private fun tutorLobbyRequest(manifest: ModelEgressManifest) = ModelTaskRequest(
        requestId = "tutor-lobby:request-1",
        input = TutorLobbyInput(
            conversationId = "tutor-conv-1",
            messageOrdinal = 1,
            studentMessage = "解一下这个题吧",
        ),
        occurredAtEpochMillis = 100,
        egressManifest = manifest,
    )

    private fun tutorLobbyManifest(approvedAtEpochMillis: Long = 101) = ModelEgressManifest(
        authorizationId = "tutor-lobby-approval",
        subjectId = "tutor-conv-1",
        purpose = ModelEgressPurpose.TUTORING,
        authorizedTaskKinds = setOf(ModelTaskKind.TUTOR_LOBBY),
        providerId = "provider-1",
        modelId = "vision-model-1",
        providerConfigurationVersion = "provider-config-v1",
        promptPolicyVersion = ModelPromptPolicyVersions.TUTOR_LOBBY,
        approvedAtEpochMillis = approvedAtEpochMillis,
        assets = emptyList(),
        disclosedData = ModelEgressManifest.TUTOR_LOBBY_DISCLOSURE,
    )

    private fun confirmedQuestion() = QuestionDocument(
        id = "question-1",
        blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
    )

    private fun tutorProvider(kind: ModelTaskKind) = externalProvider().copy(
        supportedTasks = setOf(kind),
        supportsImageInput = false,
    )

    private fun externalProvider() = ProviderCapabilitySnapshot(
        providerId = "provider-1",
        providerDisplayName = "我的视觉模型",
        modelId = "vision-model-1",
        supportedTasks = setOf(ModelTaskKind.CAPTURE_ASSESS, ModelTaskKind.CAPTURE_PARSE),
        supportsImageInput = true,
        supportsStructuredOutput = true,
        supportsStreaming = true,
        executionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
        providerConfigurationVersion = "provider-config-v1",
    )
}
