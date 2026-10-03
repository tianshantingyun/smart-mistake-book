package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.TUTOR_TOOL_DECLARATIONS
import com.tingyun.smartmistakebook.core.domain.ModelApiKey
import com.tingyun.smartmistakebook.core.domain.ModelCapabilityVerification
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationMutationResult
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationUpdate
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult
import com.tingyun.smartmistakebook.core.domain.RestrictedModelAsset
import com.tingyun.smartmistakebook.core.domain.RestrictedModelAssetSource
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentDecision
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentInput
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOrigin
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOutput
import com.tingyun.smartmistakebook.core.model.CaptureParseInput
import com.tingyun.smartmistakebook.core.model.CaptureParseOutput
import com.tingyun.smartmistakebook.core.model.CaptureSourceAssetRef
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.KnowledgeBaseNodeContext
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeTeachingMaterialType
import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.ModelEgressAssetGrant
import com.tingyun.smartmistakebook.core.model.ModelEgressDataClass
import com.tingyun.smartmistakebook.core.model.ModelEgressManifest
import com.tingyun.smartmistakebook.core.model.ModelEgressPolicy
import com.tingyun.smartmistakebook.core.model.ModelEgressPurpose
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelGatewayEvent
import com.tingyun.smartmistakebook.core.model.ModelLiveKind
import com.tingyun.smartmistakebook.core.model.ModelGatewayExecution
import com.tingyun.smartmistakebook.core.model.ModelPromptPolicyVersions
import com.tingyun.smartmistakebook.core.model.ModelProviderProtocol
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationInput
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationOutput
import com.tingyun.smartmistakebook.core.model.RelatedProblemCandidate
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import com.tingyun.smartmistakebook.core.model.TutorEvidenceLevel
import com.tingyun.smartmistakebook.core.model.TutorChatHistoryEntry
import com.tingyun.smartmistakebook.core.model.TutorConversationMemory
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorLobbyOutput
import com.tingyun.smartmistakebook.core.model.TutorMemoryPreference
import com.tingyun.smartmistakebook.core.model.TutorMessageIntent
import com.tingyun.smartmistakebook.core.model.TutorRequestedLocalCapability
import com.tingyun.smartmistakebook.core.model.TutorKnowledgeEvidence
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorMoveType
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.TutorTurnHistoryEntry
import com.tingyun.smartmistakebook.core.model.TutorTeachingReference
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.model.MODEL_TASK_STATUS_MESSAGE_MAX_CHARS
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleModelGatewayTest {
    @Test
    fun configuredButUntestedProviderAdvertisesNoModelTasks() = runBlocking {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(
                CONFIGURATION.copy(capabilityVerification = null),
            ),
            assetSource = assetSource { _, _ -> asset() },
            clock = { AUTHORIZATION_NOW },
        )

        val capabilities = gateway.capabilities()

        assertTrue(capabilities.supportedTasks.isEmpty())
        assertFalse(capabilities.supportsImageInput)
        assertFalse(capabilities.supportsStructuredOutput)
        // An unverified provider must not be advertised as streaming-capable, else the gateway would
        // send a stream=true tutor request to an endpoint that was never confirmed to support SSE.
        assertFalse(capabilities.supportsStreaming)
    }

    @Test
    fun onlyCapabilitiesThatPassedTheExactTestAreAdvertised() = runBlocking {
        val verification = requireNotNull(CONFIGURATION.capabilityVerification).copy(
            supportsImageInput = false,
            supportsStructuredOutput = true,
        )
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(
                CONFIGURATION.copy(capabilityVerification = verification),
            ),
            assetSource = assetSource { _, _ -> asset() },
            clock = { AUTHORIZATION_NOW },
        )

        val capabilities = gateway.capabilities()

        assertTrue(capabilities.supports(ModelTaskKind.TUTOR_PLAN))
        assertTrue(capabilities.supports(ModelTaskKind.TUTOR_RESPOND))
        assertTrue(capabilities.supports(ModelTaskKind.TUTOR_LOBBY))
        assertTrue(capabilities.supports(ModelTaskKind.PROBLEM_CLASSIFY))
        assertFalse(capabilities.supports(ModelTaskKind.CAPTURE_ASSESS))
        assertFalse(capabilities.supportsImageInput)
        assertTrue(capabilities.supportsStructuredOutput)
    }

    @Test
    fun missingCredentialDoesNotOpenAssetOrCallTransport() = runBlocking {
        var assetOpened = false
        var transportCalled = false
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION, credentialAvailable = false),
            assetSource = assetSource { _, _ ->
                assetOpened = true
                asset()
            },
            transport = modelTransport { _ ->
                transportCalled = true
                ModelHttpResponse(200, "{}")
            },
            clock = { AUTHORIZATION_NOW },
        )
        val events = gateway.execute(authorizedAssessment(gateway)).toList()

        assertFalse(assetOpened)
        assertFalse(transportCalled)
        assertEquals(
            ModelFailureCode.MODEL_NOT_CONFIGURED,
            (events.single() as ModelGatewayEvent.Failed).failure.code,
        )
    }

    @Test
    fun readinessRevokedWhilePreparingFailsClosedBeforeTransport() = runBlocking {
        val store = FakeConfigurationStore(CONFIGURATION)
        var transportCalled = false
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = store,
            assetSource = assetSource { _, _ ->
                val revoked = requireNotNull(store.state.value.capabilityVerification).copy(
                    supportsImageInput = false,
                    supportsStructuredOutput = false,
                )
                store.state.value = store.state.value.copy(capabilityVerification = revoked)
                asset()
            },
            transport = modelTransport { _ ->
                transportCalled = true
                ModelHttpResponse(200, envelope(assessmentPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )
        val events = gateway.execute(authorizedAssessment(gateway)).toList()

        assertFalse(transportCalled)
        assertEquals(
            ModelFailureCode.EGRESS_AUTHORIZATION_INVALID,
            (events.last() as ModelGatewayEvent.Failed).failure.code,
        )
    }

    // ---- D-K4 删除的授权时效用例（清单路径不再核对 TTL）----
    //
    // 此前这里有两条：`approvalExpiringDuringImagePreparationFailsClosedBeforeTransport`（图片准备期间
    // 时钟越过 15 分钟 TTL）与 `approvalTooFarInFutureAfterImagePreparationFailsClosedBeforeTransport`
    // （授权时刻在未来超过 2 分钟时钟偏移）——都断言"到不了 transport"。随 `requireAuthorizes` 的
    // `isModelEgressApprovalFresh` 调用一起删除：它们构造的是 capture 轮 + 逐次清单这一**生产不存在**
    // 的状态（capture 是 agent-eligible，一律走全局同意通道、不带清单），而且清单时刻的唯一来源是
    // 客户端自己刚写的当前时间（研究报告 §4.6 R5），判据在运行时永远成立。
    // "外发前重新核对、失败关闭"这条机制本身仍由下面的凭据清除/轮换用例钉住（它们走的是 provider
    // 配置与能力核对，那部分保留了）。

    @Test
    fun credentialClearedDuringEndpointPreparationFailsClosedBeforeEnqueue() = runBlocking {
        val store = FakeConfigurationStore(CONFIGURATION)
        val transport = BlockingBeforeEnqueueTransport(
            ModelHttpResponse(200, envelope(assessmentPayload())),
        )
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = store,
            assetSource = assetSource { _, _ -> asset() },
            transport = transport,
            clock = { AUTHORIZATION_NOW },
        )
        val execution = authorizedAssessment(gateway)

        val pendingEvents = async { gateway.execute(execution).toList() }
        withTimeout(5_000L) { transport.endpointPrepared.await() }
        store.clearCredential()
        transport.continueToEnqueue.complete(Unit)
        val events = pendingEvents.await()

        assertEquals(0, transport.networkEnqueueCount)
        assertEquals(
            ModelFailureCode.EGRESS_AUTHORIZATION_INVALID,
            (events.last() as ModelGatewayEvent.Failed).failure.code,
        )
    }

    @Test
    fun credentialRotatedDuringEndpointPreparationFailsClosedBeforeEnqueue() = runBlocking {
        val store = FakeConfigurationStore(CONFIGURATION)
        val transport = BlockingBeforeEnqueueTransport(
            ModelHttpResponse(200, envelope(assessmentPayload())),
        )
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = store,
            assetSource = assetSource { _, _ -> asset() },
            transport = transport,
            clock = { AUTHORIZATION_NOW },
        )
        val execution = authorizedAssessment(gateway)

        val pendingEvents = async { gateway.execute(execution).toList() }
        withTimeout(5_000L) { transport.endpointPrepared.await() }
        store.rotateCredential()
        transport.continueToEnqueue.complete(Unit)
        val events = pendingEvents.await()

        assertEquals(0, transport.networkEnqueueCount)
        assertEquals(
            ModelFailureCode.EGRESS_AUTHORIZATION_INVALID,
            (events.last() as ModelGatewayEvent.Failed).failure.code,
        )
    }

    // `approvalExpiresDuringEndpointPreparationFailsClosedBeforeEnqueue` 同批复删（同为 TTL；见上）。
    @Test
    fun localOnlyPermitNeverReachesNetworkEnqueue() = runBlocking {
        val transport = BlockingBeforeEnqueueTransport(
            ModelHttpResponse(200, envelope(assessmentPayload())),
        )
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = transport,
            clock = { AUTHORIZATION_NOW },
        )
        val externalExecution = authorizedAssessment(gateway)
        val localOnlyExecution = ModelEgressPolicy.authorize(
            request = externalExecution.request,
            provider = gateway.capabilities().copy(
                executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS,
            ),
            nowEpochMillis = AUTHORIZATION_NOW,
        )

        val events = gateway.execute(localOnlyExecution).toList()

        assertEquals(0, transport.networkEnqueueCount)
        assertEquals(
            ModelFailureCode.EGRESS_AUTHORIZATION_INVALID,
            (events.last() as ModelGatewayEvent.Failed).failure.code,
        )
    }

    @Test
    fun assessmentUsesOnlyApprovedImageAndMapsBoundedJson() = runBlocking {
        var sentBody = ""
        var sentKey = ""
        val store = FakeConfigurationStore(CONFIGURATION)
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = store,
            assetSource = assetSource { _, assetId ->
                assertEquals(ASSET_ID, assetId)
                asset()
            },
            transport = modelTransport { request ->
                sentBody = request.body
                sentKey = request.headers.single { it.first == "Authorization" }.second
                ModelHttpResponse(200, envelope(assessmentPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedAssessment(gateway)).toList()
        val output = (events.last() as ModelGatewayEvent.Completed).output as CaptureAssessmentOutput

        assertEquals(CaptureAssessmentDecision.PASS, output.assessment.decision)
        assertEquals(CONFIGURATION.modelId, output.assessment.modelVersion)
        // WireRequest 只带 string headers：密钥以 Bearer 头到达协议层（等价旧 sentKey 断言）。
        // 注意：网关 finally 对 keyChars 的 Arrays.fill 清零在本接口形状下无观测面；下面两条
        // 分别覆盖「密钥到达线上」与「凭据容器用毕即关闭」，均不覆盖该清零本身。
        assertEquals("Bearer secret", sentKey)
        assertThrows(IllegalStateException::class.java) {
            requireNotNull(store.lastIssuedKey).copyChars()
        }
        assertTrue(sentBody.contains("data:image/jpeg;base64,"))
        assertFalse(sentBody.contains(DRAFT_ID))
        assertFalse(sentBody.contains(ASSET_ID))
    }

    @Test
    fun mockWebServerReceivesAuthorizedImageThroughRealEnqueuePath() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(envelope(assessmentPayload())),
            )
            server.start()
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val gateway = OpenAiCompatibleModelGateway(
                configurationStore = FakeConfigurationStore(CONFIGURATION),
                assetSource = assetSource { _, _ -> asset() },
                transport = modelTransport { wireRequest ->
                    val body = wireRequest.body
                    val request = Request.Builder()
                        .url(server.url("/chat/completions"))
                        .header("Authorization", "Bearer secret")
                        .post(body.toRequestBody(jsonMediaType))
                        .build()
                    OkHttpClient().newCall(request).execute().use { response ->
                        ModelHttpResponse(response.code, response.body.string())
                    }
                },
                clock = { AUTHORIZATION_NOW },
            )

            val events = gateway.execute(authorizedAssessment(gateway)).toList()
            val output = (events.last() as ModelGatewayEvent.Completed).output
                as CaptureAssessmentOutput

            assertEquals(CaptureAssessmentDecision.PASS, output.assessment.decision)
            val recorded = server.takeRequest()
            assertTrue(recorded.body.readUtf8().contains("data:image/jpeg;base64,"))
            assertTrue(recorded.getHeader("Authorization").orEmpty().contains("Bearer secret"))
        }
    }

    /**
     * 学生会话里附的图片必须真的出网。
     *
     * 会话走的是"已配置模型 = 全局同意"通道（没有逐次披露清单），所以图片能不能出去完全
     * 取决于 gateway 是否把 `studentImageAssetRefs` 解析成读取计划——这一条就是钉住那个分支。
     */
    @Test
    fun imageBearingTutorRespondSendsTheStudentsImageOnTheWire() = runBlocking {
        var sentBody = ""
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, assetId ->
                assertEquals(ASSET_ID, assetId)
                asset()
            },
            transport = modelTransport { request ->
                sentBody = request.body
                ModelHttpResponse(200, envelope(tutorRespondPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(
            consentedTutorRespond(
                gateway = gateway,
                input = tutorRespondInput().copy(studentImageAssetRefs = listOf(ASSET_ID)),
            ),
        ).toList()

        assertTrue(events.last() is ModelGatewayEvent.Completed)
        assertTrue(
            "学生附的图片必须以图片内容片段出网",
            sentBody.contains("data:image/jpeg;base64,"),
        )
        // 资产 id 是内部标识，不该出现在提示词或线上报文里。
        assertFalse(sentBody.contains(ASSET_ID))
    }

    /** 纯文本会话不读任何资产、也不带图片片段：文本模型照常可用。 */
    @Test
    fun textOnlyTutorRespondReadsNoAssetAndSendsNoImageBytes() = runBlocking {
        var sentBody = ""
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, assetId ->
                error("A text-only tutor response must not read asset $assetId")
            },
            transport = modelTransport { request ->
                sentBody = request.body
                ModelHttpResponse(200, envelope(tutorRespondPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(
            consentedTutorRespond(gateway = gateway, input = tutorRespondInput()),
        ).toList()

        assertTrue(events.last() is ModelGatewayEvent.Completed)
        assertFalse(sentBody.contains("data:image/jpeg;base64,"))
    }

    @Test
    fun http429MapsToRateLimitedFailureThroughTheGatewayExecutionPath() = runBlocking {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ ->
                ModelHttpResponse(429, """{"error":{"message":"too many requests"}}""")
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedAssessment(gateway)).toList()
        val failed = events.last() as ModelGatewayEvent.Failed

        assertEquals(ModelFailureCode.RATE_LIMITED, failed.failure.code)
        assertTrue(failed.failure.retryable)
    }

    @Test
    fun http5xxMapsToRetryableServiceFailureThroughTheGatewayExecutionPath() = runBlocking {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ ->
                ModelHttpResponse(503, "service unavailable")
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedAssessment(gateway)).toList()
        val failed = events.last() as ModelGatewayEvent.Failed

        assertEquals(ModelFailureCode.SERVICE_UNAVAILABLE, failed.failure.code)
        assertTrue(failed.failure.retryable)
    }

    @Test
    fun http401And403MapToAuthenticationFailureWithoutRetrying() = runBlocking {
        listOf(401, 403).forEach { statusCode ->
            val gateway = OpenAiCompatibleModelGateway(
                configurationStore = FakeConfigurationStore(CONFIGURATION),
                assetSource = assetSource { _, _ -> asset() },
                transport = modelTransport { _ ->
                    ModelHttpResponse(statusCode, "unauthorized")
                },
                clock = { AUTHORIZATION_NOW },
            )

            val events = gateway.execute(authorizedAssessment(gateway)).toList()
            val failed = events.last() as ModelGatewayEvent.Failed

            assertEquals(ModelFailureCode.AUTHENTICATION_FAILED, failed.failure.code)
            assertFalse(failed.failure.retryable)
        }
    }

    @Test
    fun connectionFailureMapsToNetworkUnavailableAndTimeoutMapsToTimeout() = runBlocking {
        val networkGateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ -> throw IOException("connection refused") },
            clock = { AUTHORIZATION_NOW },
        )
        val networkEvents = networkGateway.execute(authorizedAssessment(networkGateway)).toList()
        assertEquals(
            ModelFailureCode.NETWORK_UNAVAILABLE,
            (networkEvents.last() as ModelGatewayEvent.Failed).failure.code,
        )

        val timeoutGateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ -> throw SocketTimeoutException("timed out") },
            clock = { AUTHORIZATION_NOW },
        )
        val timeoutEvents = timeoutGateway.execute(authorizedAssessment(timeoutGateway)).toList()
        assertEquals(
            ModelFailureCode.TIMEOUT,
            (timeoutEvents.last() as ModelGatewayEvent.Failed).failure.code,
        )
    }

    @Test
    fun assessmentMapsIndependentQuestionRegionsInReadingOrder() = runBlocking {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ ->
                ModelHttpResponse(200, envelope(splitAssessmentPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedAssessment(gateway)).toList()
        val output = (events.last() as ModelGatewayEvent.Completed).output as CaptureAssessmentOutput

        assertEquals(CaptureAssessmentDecision.SPLIT, output.assessment.decision)
        assertEquals(2, output.assessment.questionRegions.size)
        assertEquals(0.05, output.assessment.questionRegions.first().top, 0.0)
        assertEquals(0.55, output.assessment.questionRegions.last().top, 0.0)
    }

    @Test
    fun oversizedMultiPageRequestFailsBeforeOpeningAnyApprovedAsset() = runBlocking {
        var assetOpened = false
        var transportCalled = false
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ ->
                assetOpened = true
                asset()
            },
            transport = modelTransport { _ ->
                transportCalled = true
                ModelHttpResponse(200, envelope(parsePayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(
            authorizedParseWithDeclaredAssetBytes(
                gateway = gateway,
                byteSizes = listOf(14L * 1_024L * 1_024L, 14L * 1_024L * 1_024L),
            ),
        ).toList()
        val failure = (events.single() as ModelGatewayEvent.Failed).failure

        assertFalse(assetOpened)
        assertFalse(transportCalled)
        assertEquals(ModelFailureCode.PROVIDER_REJECTED_INPUT, failure.code)
        assertEquals("本次题图总量超过单次发送上限，请减少图片后重试", failure.message)
        assertFalse(failure.retryable)
        assertFalse(failure.message.contains(ASSET_ID))
    }

    @Test
    fun parseOverwritesTrustFieldsAndNeverPreselectsAnswer() = runBlocking {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ ->
                ModelHttpResponse(200, envelope(parsePayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedParse(gateway)).toList()
        val output = (events.last() as ModelGatewayEvent.Completed).output as CaptureParseOutput
        val choice = output.capturedDocument.document.blocks.single() as ContentBlock.ChoiceGroup
        val evidence = output.capturedDocument.blockEvidence.single()

        assertEquals("document-$DRAFT_ID", output.capturedDocument.document.id)
        assertEquals("block-1", choice.id)
        assertEquals(listOf("choice-1-1", "choice-1-2"), choice.choices.map { it.id })
        assertNull(choice.selectedChoiceId)
        assertEquals(ASSET_ID, evidence.sourceAssetId)
        assertEquals(QuestionBlockProvenance.MODEL_DOCUMENT_PARSE, evidence.provenance)
        assertEquals(QuestionBlockReviewStatus.NEEDS_REVIEW, evidence.reviewStatus)
        assertEquals(CONFIGURATION.modelId, evidence.producerVersion)
    }

    @Test
    fun parseReconstructsCartesianFigureWithLocalIdsAndDiagramEvidence() = runBlocking {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ ->
                ModelHttpResponse(200, envelope(figureParsePayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedParse(gateway)).toList()
        val output = (events.last() as ModelGatewayEvent.Completed).output as CaptureParseOutput
        val figure = output.capturedDocument.document.blocks.single() as ContentBlock.Figure
        val schema = figure.schema as FigureSchema.Cartesian
        val evidence = output.capturedDocument.blockEvidence.single()

        assertEquals("block-1", figure.id)
        assertEquals("polyline-1-1", schema.polylines.single().id)
        assertEquals("x", schema.xAxis.label)
        assertEquals("y", schema.yAxis.label)
        assertEquals(WritingLayer.DIAGRAM, evidence.writingLayer)
        assertEquals(ASSET_ID, evidence.sourceAssetId)
        assertEquals(QuestionBlockReviewStatus.NEEDS_REVIEW, evidence.reviewStatus)
    }

    @Test
    fun parseRejectsInvalidFigureAxisInsteadOfFallingBackSilently() = runBlocking {
        val invalidPayload = figureParsePayload().replaceFirst(
            oldValue = "\"maximum\":2.0",
            newValue = "\"maximum\":-2.0",
        )
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ ->
                ModelHttpResponse(200, envelope(invalidPayload))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val failed = gateway.execute(authorizedParse(gateway)).toList().last()
            as ModelGatewayEvent.Failed

        assertEquals(ModelFailureCode.INVALID_RESPONSE, failed.failure.code)
        assertFalse(failed.failure.retryable)
    }

    @Test
    fun authenticationFailureIsPermanentAndActionable() = runBlocking {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ -> ModelHttpResponse(401, "") },
            clock = { AUTHORIZATION_NOW },
        )

        val failed = gateway.execute(authorizedAssessment(gateway)).toList().last()
            as ModelGatewayEvent.Failed

        assertEquals(ModelFailureCode.AUTHENTICATION_FAILED, failed.failure.code)
        assertFalse(failed.failure.retryable)
        assertTrue(failed.failure.message.contains("API Key"))
    }

    @Test
    fun tutorPlanSendsNoImageOrLocalIdsAndReconstructsTrustedContext() = runBlocking {
        var assetOpened = false
        var sentBody = ""
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ ->
                assetOpened = true
                asset()
            },
            transport = modelTransport { request ->
                val body = request.body
                sentBody = body
                ModelHttpResponse(200, envelope(tutorPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedTutor(gateway)).toList()
        val output = (events.last() as ModelGatewayEvent.Completed).output as TutorPlanOutput

        assertFalse(assetOpened)
        assertTrue(sentBody.contains("求函数的单调区间"))
        assertTrue(sentBody.contains("导数符号"))
        assertTrue(sentBody.contains("完整例题：先找导数为零的分界点"))
        assertTrue(sentBody.contains("“包含题目和解答”不等于题库"))
        assertTrue(sentBody.contains("不是学生作答、不是掌握证据"))
        assertFalse(sentBody.contains(TUTOR_SESSION_ID))
        assertFalse(sentBody.contains("node-derivative"))
        assertFalse(sentBody.contains("teaching-method-1"))
        assertFalse(sentBody.contains("data:image"))
        assertTrue(sentBody.contains("严禁生成新题、同类题、变式题、校准题"))
        assertTrue(sentBody.contains("diagnosticQuestion是可选的当前题内交互块"))
        assertTrue(sentBody.contains("ID或未列出的字段"))
        assertTrue(sentBody.contains("不得出现图片、SVG、HTML、CSS、JS、代码"))
        assertTrue(sentBody.contains("inferredKnowledgeLabels给当前题涉及的1到8个知识标签"))
        // Gap 10: the tutor plan prompt must tell the model how to treat CONFLICTED
        // knowledge (previously mastered, recently independently wrong) — the highest-value
        // tutoring focus — rather than leaving it to the model's generic understanding.
        assertTrue(sentBody.contains("level=CONFLICTED的知识点表示“曾掌握但近期出现独立错误”"))
        assertEquals(TUTOR_SESSION_ID, output.sessionId)
        assertEquals("question-confirmed", output.questionDocumentId)
        val diagnostic = requireNotNull(output.plan.diagnosticItem)
        assertEquals("choice-1", diagnostic.correctChoiceId)
        assertTrue(diagnostic.knowledgeNodeIds.isEmpty())
    }

    @Test
    fun tutorPlanAcceptsExplanationWithoutInventingAChoiceQuestion() = runBlocking {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> error("Tutor must not open image assets") },
            transport = modelTransport { _ ->
                ModelHttpResponse(200, envelope(tutorPayload(includeDiagnostic = false)))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val output = gateway.execute(authorizedTutor(gateway)).toList().last()
            .let { it as ModelGatewayEvent.Completed }
            .output as TutorPlanOutput

        assertEquals(null, output.plan.diagnosticItem)
        assertTrue(output.plan.solutionMarkdown.contains("符号不等式"))
        assertTrue(output.plan.alternateMethodMarkdown.contains("符号表"))
    }

    @Test
    fun tutorPlanAcceptsNoContextualMovesInsteadOfForcingButtons() = runBlocking {
        val output = executeTutorPayload(
            tutorPayload(includeNextMoves = false),
        ).last().let { it as ModelGatewayEvent.Completed }.output as TutorPlanOutput

        assertTrue(output.plan.suggestedMoves.isEmpty())
    }

    @Test
    fun tutorResponseUsesOnlyBoundedCurrentQuestionTextAndAllowsOmittedOptionalContent() = runBlocking {
        var assetOpened = false
        var sentBody = ""
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ ->
                assetOpened = true
                asset()
            },
            transport = modelTransport { request ->
                val body = request.body
                sentBody = body
                ModelHttpResponse(200, envelope(tutorRespondPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val output = gateway.execute(authorizedTutorRespond(gateway)).toList().last()
            .let { it as ModelGatewayEvent.Completed }
            .output as TutorRespondOutput

        assertFalse(assetOpened)
        assertTrue(gateway.capabilities().supports(ModelTaskKind.TUTOR_RESPOND))
        assertTrue(sentBody.contains("这一步为什么要先判断导数符号？"))
        assertTrue(sentBody.contains("先找到导数的零点。"))
        assertTrue(sentBody.contains("CHANGE_REPRESENTATION"))
        assertTrue(sentBody.contains("只解决studentMessage表达的一个当前题目标"))
        assertTrue(sentBody.contains("不得擅自把所有消息都当作讲题要求"))
        assertTrue(sentBody.contains("模型只提出本地动作申请"))
        assertTrue(sentBody.contains("严禁生成新题、同类题、变式题、校准题"))
        assertTrue(sentBody.contains("不要默认给最终答案"))
        assertTrue(sentBody.contains("solutionRevealed是必填的JSON布尔值"))
        assertTrue(sentBody.contains("不得返回diagnosticQuestion、选择题"))
        assertTrue(sentBody.contains("不得返回ID或schemaVersion"))
        assertFalse(sentBody.contains(TUTOR_SESSION_ID))
        assertFalse(sentBody.contains("node-derivative"))
        assertFalse(sentBody.contains("data:image"))
        assertEquals(TUTOR_SESSION_ID, output.sessionId)
        assertEquals("question-confirmed", output.questionDocumentId)
        assertEquals(4, output.responseOrdinal)
        assertEquals(2, output.cycleOrdinal)
        assertEquals(3, output.turnOrdinal)
        assertFalse(output.solutionRevealed)
        assertEquals(TutorMessageIntent.CURRENT_QUESTION_HELP, output.intentDecision.intent)
        assertTrue(output.suggestedMoves.isEmpty())
    }

    @Test
    fun tutorLobbySendsOnlyTheMessageContextAndParsesBoundedIntent() = runBlocking {
        var sentBody = ""
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> error("Lobby must not read image assets") },
            transport = modelTransport { request ->
                val body = request.body
                sentBody = body
                ModelHttpResponse(
                    200,
                    envelope(
                        Json.encodeToString(
                            buildJsonObject {
                                put(
                                    "intentDecision",
                                    tutorIntentPayload(
                                        intent = TutorMessageIntent.MISTAKE_NOTEBOOK_LOOKUP,
                                        confidence = 0.94,
                                        explicitActionRequest = true,
                                        capability =
                                            TutorRequestedLocalCapability.READ_MISTAKE_NOTEBOOK,
                                        lookupTerms = listOf("函数"),
                                    ),
                                )
                                put("messageMarkdown", "我会先让本机查找和函数有关的错题。")
                            },
                        ),
                    ),
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val completed = gateway.execute(authorizedTutorLobby(gateway)).toList().last()
            as ModelGatewayEvent.Completed
        val output = completed.output as TutorLobbyOutput

        assertTrue(sentBody.contains("帮我找函数错题"))
        assertTrue(sentBody.contains("你好，你现在想做什么？"))
        assertTrue(sentBody.contains("模型无权保存、删除、修改错题或学习记录"))
        assertFalse(sentBody.contains("confirmedQuestion"))
        assertFalse(sentBody.contains("relevantLearningEvidence"))
        assertEquals(
            TutorRequestedLocalCapability.READ_MISTAKE_NOTEBOOK,
            output.intentDecision.requestedLocalCapability,
        )
        assertEquals("我会先让本机查找和函数有关的错题。", output.messageMarkdown)
    }

    @Test
    fun tutorLobbyParsesThinkingMarkdownFromWire() = runBlocking {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> error("Lobby must not read image assets") },
            transport = modelTransport { _ ->
                ModelHttpResponse(
                    200,
                    envelope(
                        Json.encodeToString(
                            buildJsonObject {
                                put(
                                    "intentDecision",
                                    tutorIntentPayload(),
                                )
                                put("messageMarkdown", "这一步先确认你想从哪继续。")
                                put("thinkingMarkdown", "先在本地确认意图，不需要任何写入。")
                            },
                        ),
                    ),
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val output = (gateway.execute(authorizedTutorLobby(gateway)).toList().last()
            as ModelGatewayEvent.Completed).output as TutorLobbyOutput
        assertEquals("先在本地确认意图，不需要任何写入。", output.thinkingMarkdown)
    }

    @Test
    fun tutorLobbyRejectsAttachedImagesAsAnUnknownKey() = runBlocking {
        // A6：大厅输出的 attachedImages 解析已删（死分支），wire 键从白名单移除——
        // 模型再吐这个键就是未知键，整条输出 fail-closed 拒（不是"忽略一下"）。
        // Respond/Plan 的 attachedImages 是兼容 fallback，仍解析仍渲染（见本文件
        // tutorResponseParsesAttachedImagesFromWire）。
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> error("Lobby must not read image assets") },
            transport = modelTransport { _ ->
                ModelHttpResponse(
                    200,
                    envelope(
                        Json.encodeToString(
                            buildJsonObject {
                                put("intentDecision", tutorIntentPayload())
                                put("messageMarkdown", "我画一张图给你。")
                                put(
                                    "attachedImages",
                                    JsonArray(
                                        listOf(
                                            buildJsonObject {
                                                put("imageId", "process-1")
                                                put("kind", "GENERATE_PROCESS")
                                                put("description", "数轴标注导数符号区间")
                                            },
                                        ),
                                    ),
                                )
                            },
                        ),
                    ),
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val failed = gateway.execute(authorizedTutorLobby(gateway)).toList().last()
            as ModelGatewayEvent.Failed
        assertEquals(ModelFailureCode.INVALID_RESPONSE, failed.failure.code)
    }

    @Test
    fun tutorResponseParsesIntentWithoutGrantingDatabaseAuthority() = runBlocking {
        val output = parsedTutorResponse(
            tutorRespondPayload(
                messageMarkdown = "我会把请求交给本机处理，读取前不会改动任何学习记录。",
                intentDecision = tutorIntentPayload(
                    intent = TutorMessageIntent.MISTAKE_NOTEBOOK_LOOKUP,
                    confidence = 0.91,
                    explicitActionRequest = true,
                    capability = TutorRequestedLocalCapability.READ_MISTAKE_NOTEBOOK,
                    lookupTerms = listOf("函数", "单调性"),
                ),
            ),
        )

        assertEquals(TutorMessageIntent.MISTAKE_NOTEBOOK_LOOKUP, output.intentDecision.intent)
        assertEquals(
            TutorRequestedLocalCapability.READ_MISTAKE_NOTEBOOK,
            output.intentDecision.requestedLocalCapability,
        )
        assertEquals(listOf("函数", "单调性"), output.intentDecision.lookupTerms)
        assertFalse(output.solutionRevealed)
    }

    @Test
    fun missingIntentFailsClosedAsAmbiguousInsteadOfAssumingCurrentQuestionHelp() = runBlocking {
        val output = parsedTutorResponse(
            tutorRespondPayload(intentDecision = null),
        )

        assertEquals(TutorMessageIntent.AMBIGUOUS, output.intentDecision.intent)
        assertEquals(0.0, output.intentDecision.confidence, 0.0)
    }

    @Test
    fun tutorResponseRejectsIntentCapabilityMismatch() = runBlocking {
        val payload = tutorRespondPayload(
            intentDecision = tutorIntentPayload(
                intent = TutorMessageIntent.CASUAL_CONVERSATION,
                confidence = 0.96,
                explicitActionRequest = true,
                capability = TutorRequestedLocalCapability.READ_MISTAKE_NOTEBOOK,
            ),
        )

        val failed = executeTutorRespondPayload(payload).last() as ModelGatewayEvent.Failed
        assertEquals(ModelFailureCode.INVALID_RESPONSE, failed.failure.code)
    }

    @Test
    fun tutorResponseRequiresAStrictSolutionRevealBoolean() = runBlocking {
        assertTrue(parsedTutorResponse(tutorRespondPayload(solutionRevealed = true)).solutionRevealed)

        val missingDeclaration = Json.encodeToString(
            buildJsonObject { put("messageMarkdown", "这是当前题的完整答案。") },
        )
        val quotedDeclaration = tutorRespondPayload(
            extraTopLevel = "solutionRevealed" to JsonPrimitive("true"),
        )
        listOf(missingDeclaration, quotedDeclaration).forEach { payload ->
            val failed = executeTutorRespondPayload(payload).last() as ModelGatewayEvent.Failed
            assertEquals(ModelFailureCode.INVALID_RESPONSE, failed.failure.code)
            assertFalse(failed.failure.retryable)
        }
    }

    @Test
    fun tutorResponseParsesThinkingMarkdownFromWire() = runBlocking {
        val payload = tutorRespondPayload(
            extraTopLevel = "thinkingMarkdown" to JsonPrimitive("先判断导数的符号区间。"),
        )
        val output = parsedTutorResponse(payload)
        assertEquals("先判断导数的符号区间。", output.thinkingMarkdown)
    }

    @Test
    fun tutorResponseAdmitsMissingThinkingMarkdownAsNull() = runBlocking {
        val output = parsedTutorResponse(tutorRespondPayload())
        assertEquals(null, output.thinkingMarkdown)
    }

    @Test
    fun tutorResponseRejectsUnsafeThinkingMarkdown() = runBlocking {
        val payload = tutorRespondPayload(
            extraTopLevel = "thinkingMarkdown" to JsonPrimitive("试着执行<script>run()</script>"),
        )
        val failed = executeTutorRespondPayload(payload).last() as ModelGatewayEvent.Failed
        assertEquals(ModelFailureCode.INVALID_RESPONSE, failed.failure.code)
    }

    @Test
    fun tutorResponseParsesAttachedImagesFromWire() = runBlocking {
        val payload = tutorRespondPayload(
            extraTopLevel = "attachedImages" to JsonArray(
                listOf(
                    buildJsonObject {
                        put("imageId", "process-1")
                        put("kind", "GENERATE_PROCESS")
                        put("description", "数轴标注导数符号区间")
                        put("accessibilityText", "导数符号区间图")
                    },
                    buildJsonObject {
                        put("imageId", "redraw-1")
                        put("kind", "REDRAW_PROBLEM")
                        put("description", "重绘题面去除手写")
                    },
                ),
            ),
        )
        val output = parsedTutorResponse(payload)
        assertEquals(2, output.attachedImages.size)
        assertEquals(AttachedImageKind.GENERATE_PROCESS, output.attachedImages[0].kind)
        assertEquals("数轴标注导数符号区间", output.attachedImages[0].description)
        assertEquals("导数符号区间图", output.attachedImages[0].accessibilityText)
        assertEquals(AttachedImageKind.REDRAW_PROBLEM, output.attachedImages[1].kind)
        assertEquals("redraw-1", output.attachedImages[1].imageId)
    }

    @Test
    fun tutorResponseAdmitsMissingAttachedImagesAsEmpty() = runBlocking {
        val output = parsedTutorResponse(tutorRespondPayload())
        assertEquals(emptyList<AttachedImage>(), output.attachedImages)
    }

    @Test
    fun tutorResponseRejectsUnknownAttachedImageKey() = runBlocking {
        val payload = tutorRespondPayload(
            extraTopLevel = "attachedImages" to JsonArray(
                listOf(
                    buildJsonObject {
                        put("imageId", "bad-1")
                        put("kind", "GENERATE_PROCESS")
                        put("description", "图")
                        put("sourceAssetId", "asset-9")
                    },
                ),
            ),
        )
        val failed = executeTutorRespondPayload(payload).last() as ModelGatewayEvent.Failed
        assertEquals(ModelFailureCode.INVALID_RESPONSE, failed.failure.code)
    }


    @Test
    fun tutorPlanParsesThinkingMarkdownFromWire() = runBlocking {
        val payload = tutorPayload(
            extraTopLevel = "thinkingMarkdown" to JsonPrimitive("这题先核对学生对导数符号的理解。"),
        )
        val output = executeTutorPayload(payload).last()
            .let { it as ModelGatewayEvent.Completed }
            .output as TutorPlanOutput
        assertEquals("这题先核对学生对导数符号的理解。", output.plan.thinkingMarkdown)
    }

    @Test
    fun tutorPlanAdmitsMissingThinkingMarkdownAsNull() = runBlocking {
        val output = executeTutorPayload(tutorPayload()).last()
            .let { it as ModelGatewayEvent.Completed }
            .output as TutorPlanOutput
        assertEquals(null, output.plan.thinkingMarkdown)
    }

    @Test
    fun tutorResponseRejectsUnknownOrActiveOutputFields() = runBlocking {
        val invalidPayloads = listOf(
            tutorRespondPayload(
                extraTopLevel = "scene" to buildJsonObject {
                    put("kind", "spatial_canvas")
                    put("title", "任意画布")
                },
            ),
            tutorRespondPayload(extraTopLevel = "unexpected" to JsonPrimitive("value")),
            tutorRespondPayload(extraTopLevel = "imageUrl" to JsonPrimitive("asset.png")),
            tutorRespondPayload(
                nextMoves = buildJsonArray {
                    add(buildJsonObject {
                        put("label", "执行任意动作")
                        put("type", "DEEPEN_REASONING")
                        put("action", "run")
                    })
                },
            ),
            tutorRespondPayload(messageMarkdown = "`println(1)`"),
            tutorRespondPayload(messageMarkdown = "<div>答案</div>"),
            tutorRespondPayload(messageMarkdown = "答案见 https://example.com"),
        )

        invalidPayloads.forEach { payload ->
            val failed = executeTutorRespondPayload(payload).last() as ModelGatewayEvent.Failed
            assertEquals(ModelFailureCode.INVALID_RESPONSE, failed.failure.code)
            assertFalse(failed.failure.retryable)
        }
    }

    @Test
    fun tutorFollowUpUsesPersistedChoiceAndRequestedTeachingMove() = runBlocking {
        var sentBody = ""
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> error("Tutor must not open image assets") },
            transport = modelTransport { request ->
                val body = request.body
                sentBody = body
                ModelHttpResponse(200, envelope(tutorPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )
        val history = TutorTurnHistoryEntry(
            turnOrdinal = 1,
            diagnosticStemMarkdown = "导数先正后负时，原函数怎样变化？",
            selectedChoiceMarkdown = "先减后增",
            selectionWasCorrect = false,
            feedbackMarkdown = "你把导数正负与增减的对应关系反过来了。",
            requestedMove = TutorMoveType.CHANGE_REPRESENTATION,
        )

        val events = gateway.execute(
            authorizedTutor(
                gateway,
                tutorInput().copy(turnOrdinal = 2, priorTurns = listOf(history)),
            ),
        ).toList()
        val output = (events.last() as ModelGatewayEvent.Completed).output as TutorPlanOutput

        assertTrue(sentBody.contains("第1轮第2步讲解"))
        assertTrue(sentBody.contains("先减后增"))
        assertTrue(sentBody.contains("CHANGE_REPRESENTATION"))
        assertEquals(2, output.turnOrdinal)
    }

    @Test
    fun laterTutorCycleReceivesExactStudentWordsWithoutPermissionToProbe() = runBlocking {
        var sentBody = ""
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> error("Tutor must not open image assets") },
            transport = modelTransport { request ->
                val body = request.body
                sentBody = body
                ModelHttpResponse(200, envelope(tutorPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )
        val exactMessages = listOf(
            "  我卡在配方法第二步\n",
            "为什么这里要同时加上 4？  ",
        )
        val input = tutorInput().copy(
            cycleOrdinal = 2,
            priorConversationMemory = TutorConversationMemory(
                completedCycleCount = 1,
                answeredTurnCount = 0,
                correctChoiceCount = 0,
                solutionWasRevealed = true,
            ),
            priorCycleStudentMessages = exactMessages,
        )

        val events = gateway.execute(authorizedTutor(gateway, input)).toList()
        val output = (events.last() as ModelGatewayEvent.Completed).output as TutorPlanOutput

        assertTrue(sentBody.contains("priorCycleStudentMessages"))
        assertTrue(sentBody.contains("我卡在配方法第二步"))
        assertTrue(sentBody.indexOf("我卡在配方法第二步") < sentBody.indexOf("为什么这里要同时加上 4"))
        assertTrue(sentBody.contains("不得据此额外出题、诊断、校准或探测能力"))
        assertTrue(sentBody.contains("不得用conversationMemory覆盖、否定或改写这些原话"))
        assertEquals(2, output.cycleOrdinal)
        assertEquals(1, output.turnOrdinal)
    }

    @Test
    fun organizationUsesAliasesAndReconstructsRelationTargetsLocally() = runBlocking {
        var assetOpened = false
        var sentBody = ""
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ ->
                assetOpened = true
                asset()
            },
            transport = modelTransport { request ->
                val body = request.body
                sentBody = body
                ModelHttpResponse(200, envelope(organizationPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedOrganization(gateway)).toList()
        val output = (events.last() as ModelGatewayEvent.Completed).output
            as ProblemOrganizationOutput

        assertFalse(assetOpened)
        assertTrue(sentBody.contains("candidate-1"))
        assertTrue(sentBody.contains("导数变式"))
        assertFalse(sentBody.contains(ORGANIZATION_PROBLEM_ID))
        assertFalse(sentBody.contains(RELATED_PROBLEM_ID))
        assertFalse(sentBody.contains("node-derivative"))
        assertFalse(sentBody.contains("data:image"))
        assertEquals(ORGANIZATION_PROBLEM_ID, output.problemId)
        assertEquals(RELATED_PROBLEM_ID, output.plan.relations.single().targetProblemId)
        assertEquals("revision-related", output.plan.relations.single().targetProblemRevisionId)
    }

    @Test
    fun publicAddressPolicyRejectsLocalAndReservedNetworks() {
        listOf(
            "127.0.0.1",
            "10.0.0.1",
            "100.64.0.1",
            "169.254.169.254",
            "192.168.1.1",
            "198.51.100.4",
            "203.0.113.8",
            "::1",
            "fc00::1",
            "2001:db8::1",
        ).forEach { address ->
            assertFalse(address, InetAddress.getByName(address).isPubliclyRoutable())
        }
        assertTrue(InetAddress.getByName("8.8.8.8").isPubliclyRoutable())
        assertTrue(InetAddress.getByName("2606:4700:4700::1111").isPubliclyRoutable())
    }

    private suspend fun authorizedAssessment(
        gateway: OpenAiCompatibleModelGateway,
    ): ModelGatewayExecution {
        val capabilities = gateway.capabilities()
        val input = CaptureAssessmentInput(
            draftId = DRAFT_ID,
            sourceAssetId = ASSET_ID,
            origin = CaptureAssessmentOrigin.LIBRARY,
            imageWidth = 100,
            imageHeight = 200,
        )
        val request = ModelTaskRequest(
            requestId = "assessment-request",
            input = input,
            occurredAtEpochMillis = REQUEST_OCCURRED_AT,
            egressManifest = manifest(capabilities, "assessment-authorization"),
        )
        return ModelEgressPolicy.authorize(request, capabilities, AUTHORIZATION_NOW)
    }

    private suspend fun authorizedParse(
        gateway: OpenAiCompatibleModelGateway,
    ): ModelGatewayExecution {
        val capabilities = gateway.capabilities()
        val input = CaptureParseInput(
            draftId = DRAFT_ID,
            origin = CaptureAssessmentOrigin.LIBRARY,
            basisRevisionNumber = 1,
            sourceAssets = listOf(
                CaptureSourceAssetRef(
                    assetId = ASSET_ID,
                    sha256 = SHA,
                    width = 100,
                    height = 200,
                    pageIndex = 0,
                ),
            ),
            assessmentRequestId = "assessment-request",
        )
        val request = ModelTaskRequest(
            requestId = "parse-request",
            input = input,
            occurredAtEpochMillis = REQUEST_OCCURRED_AT,
            egressManifest = manifest(capabilities, "parse-authorization"),
        )
        return ModelEgressPolicy.authorize(request, capabilities, AUTHORIZATION_NOW)
    }

    private suspend fun authorizedParseWithDeclaredAssetBytes(
        gateway: OpenAiCompatibleModelGateway,
        byteSizes: List<Long>,
    ): ModelGatewayExecution {
        val capabilities = gateway.capabilities()
        val assetIds = byteSizes.indices.map { index -> "declared-asset-${index + 1}" }
        val sourceAssets = assetIds.mapIndexed { index, assetId ->
            CaptureSourceAssetRef(
                assetId = assetId,
                sha256 = SHA,
                width = 100,
                height = 200,
                pageIndex = index,
            )
        }
        val assessmentRequestIds = byteSizes.indices.map { index ->
            "declared-assessment-${index + 1}"
        }
        val input = CaptureParseInput(
            draftId = DRAFT_ID,
            origin = CaptureAssessmentOrigin.LIBRARY,
            basisRevisionNumber = 1,
            sourceAssets = sourceAssets,
            assessmentRequestId = assessmentRequestIds.first(),
            assessmentRequestIds = assessmentRequestIds,
        )
        val manifest = ModelEgressManifest(
            authorizationId = "oversized-parse-authorization",
            subjectId = DRAFT_ID,
            purpose = ModelEgressPurpose.CAPTURE_TO_DOCUMENT,
            authorizedTaskKinds = setOf(ModelTaskKind.CAPTURE_ASSESS, ModelTaskKind.CAPTURE_PARSE),
            providerId = capabilities.providerId,
            modelId = capabilities.modelId,
            providerConfigurationVersion = capabilities.providerConfigurationVersion,
            promptPolicyVersion = ModelPromptPolicyVersions.CAPTURE_DOCUMENT,
            approvedAtEpochMillis = AUTHORIZATION_APPROVED_AT,
            assets = sourceAssets.mapIndexed { index, source ->
                ModelEgressAssetGrant(
                    assetId = source.assetId,
                    sha256 = source.sha256,
                    byteSize = byteSizes[index],
                    width = source.width,
                    height = source.height,
                )
            },
            disclosedData = ModelEgressManifest.CAPTURE_IMAGE_DISCLOSURE,
        )
        val request = ModelTaskRequest(
            requestId = "oversized-parse-request",
            input = input,
            occurredAtEpochMillis = REQUEST_OCCURRED_AT,
            egressManifest = manifest,
        )
        return ModelEgressPolicy.authorize(request, capabilities, AUTHORIZATION_NOW)
    }

    private suspend fun authorizedTutor(
        gateway: OpenAiCompatibleModelGateway,
        input: TutorPlanInput = tutorInput(),
    ): ModelGatewayExecution {
        val capabilities = gateway.capabilities()
        val request = ModelTaskRequest(
            requestId = "tutor-request",
            input = input,
            occurredAtEpochMillis = REQUEST_OCCURRED_AT,
            egressManifest = ModelEgressManifest(
                authorizationId = "tutor-authorization",
                // K1c：讲题任务的槽键/发送范围主语是**会话 id**（会话行身份），
                // 不再是讲题会话 id——清单主语必须与请求主语同源（ModelEgress.kt 的校验）。
                subjectId = TutorConversationIds.captured(TUTOR_SESSION_ID),
                purpose = ModelEgressPurpose.TUTORING,
                authorizedTaskKinds = setOf(ModelTaskKind.TUTOR_PLAN),
                providerId = capabilities.providerId,
                modelId = capabilities.modelId,
                providerConfigurationVersion = capabilities.providerConfigurationVersion,
                promptPolicyVersion = ModelPromptPolicyVersions.TUTOR_PLAN,
                approvedAtEpochMillis = AUTHORIZATION_APPROVED_AT,
                assets = emptyList(),
                disclosedData = ModelEgressManifest.TUTOR_PLAN_DISCLOSURE,
            ),
        )
        return ModelEgressPolicy.authorize(request, capabilities, AUTHORIZATION_NOW)
    }

    private fun tutorInput() = TutorPlanInput(
        sessionId = TUTOR_SESSION_ID,
        draftRevisionNumber = 3,
        subject = "MATH",
        questionDocument = QuestionDocument(
            id = "question-confirmed",
            blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
        ),
        relevantLearningEvidence = listOf(
            TutorKnowledgeEvidence(
                "node-derivative",
                "导数符号",
                TutorEvidenceLevel.LEARNING,
                0.3,
            ),
        ),
        projectionIsCurrent = true,
        reviewedTeachingReferences = listOf(
            TutorTeachingReference(
                materialId = "teaching-method-1",
                subject = "MATH",
                materialType = KnowledgeTeachingMaterialType.WORKED_EXAMPLE,
                title = "由导数符号判断单调性",
                summaryMarkdown = "先找分界点，再判断各区间内的导数符号。",
                applicabilityMarkdown = "适用于由导数判断函数单调性的当前题。",
                contentMarkdown = "完整例题：先找导数为零的分界点，再列符号表。",
                boundaryMarkdown = "必须结合当前题的定义域。",
                knowledgeNodeIds = listOf("node-derivative"),
            ),
        ),
    )

    private suspend fun authorizedTutorRespond(
        gateway: OpenAiCompatibleModelGateway,
        input: TutorRespondInput = tutorRespondInput(),
    ): ModelGatewayExecution {
        val capabilities = gateway.capabilities()
        val request = ModelTaskRequest(
            requestId = "tutor-respond-request",
            input = input,
            occurredAtEpochMillis = REQUEST_OCCURRED_AT,
            egressManifest = ModelEgressManifest(
                authorizationId = "tutor-respond-authorization",
                subjectId = TutorConversationIds.captured(TUTOR_SESSION_ID),
                purpose = ModelEgressPurpose.TUTORING,
                authorizedTaskKinds = setOf(ModelTaskKind.TUTOR_RESPOND),
                providerId = capabilities.providerId,
                modelId = capabilities.modelId,
                providerConfigurationVersion = capabilities.providerConfigurationVersion,
                promptPolicyVersion = ModelPromptPolicyVersions.TUTOR_RESPOND,
                approvedAtEpochMillis = AUTHORIZATION_APPROVED_AT,
                assets = emptyList(),
                disclosedData = ModelEgressManifest.TUTOR_RESPOND_DISCLOSURE,
            ),
        )
        return ModelEgressPolicy.authorize(request, capabilities, AUTHORIZATION_NOW)
    }

    private suspend fun authorizedTutorLobby(
        gateway: OpenAiCompatibleModelGateway,
    ): ModelGatewayExecution {
        val capabilities = gateway.capabilities()
        val input = TutorLobbyInput(
            conversationId = "tutor-lobby",
            messageOrdinal = 2,
            studentMessage = "帮我找函数错题",
            priorMessages = listOf(
                TutorChatHistoryEntry(
                    studentMessage = "你好",
                    assistantMarkdown = "你好，你现在想做什么？",
                ),
            ),
        )
        val request = ModelTaskRequest(
            requestId = "tutor-lobby-request",
            input = input,
            occurredAtEpochMillis = REQUEST_OCCURRED_AT,
            egressManifest = ModelEgressManifest(
                authorizationId = "tutor-lobby-authorization",
                subjectId = input.conversationId,
                purpose = ModelEgressPurpose.TUTORING,
                authorizedTaskKinds = setOf(ModelTaskKind.TUTOR_LOBBY),
                providerId = capabilities.providerId,
                modelId = capabilities.modelId,
                providerConfigurationVersion = capabilities.providerConfigurationVersion,
                promptPolicyVersion = ModelPromptPolicyVersions.TUTOR_LOBBY,
                approvedAtEpochMillis = AUTHORIZATION_APPROVED_AT,
                assets = emptyList(),
                disclosedData = ModelEgressManifest.TUTOR_LOBBY_DISCLOSURE,
            ),
        )
        return ModelEgressPolicy.authorize(request, capabilities, AUTHORIZATION_NOW)
    }

    private fun tutorRespondInput() = TutorRespondInput(
        sessionId = TUTOR_SESSION_ID,
        draftRevisionNumber = 3,
        subject = "MATH",
        questionDocument = tutorInput().questionDocument,
        relevantLearningEvidence = tutorInput().relevantLearningEvidence,
        projectionIsCurrent = true,
        reviewedTeachingReferences = tutorInput().reviewedTeachingReferences,
        responseOrdinal = 4,
        cycleOrdinal = 2,
        turnOrdinal = 3,
        studentMessage = "这一步为什么要先判断导数符号？",
        visibleTutorContextMarkdown = "当前讲解正在分析导数符号与单调性的对应。",
        priorMessages = listOf(
            TutorChatHistoryEntry(
                studentMessage = "先从哪里开始？",
                assistantMarkdown = "先找到导数的零点。",
            ),
        ),
        requestedMove = TutorMoveType.CHANGE_REPRESENTATION,
    )

    private suspend fun authorizedOrganization(
        gateway: OpenAiCompatibleModelGateway,
    ): ModelGatewayExecution {
        val capabilities = gateway.capabilities()
        val input = ProblemOrganizationInput(
            problemId = ORGANIZATION_PROBLEM_ID,
            problemRevisionId = "revision-current",
            practiceUnitId = "unit-current",
            subject = SubjectKind.MATH,
            questionDocument = QuestionDocument(
                id = "question-current",
                blocks = listOf(ContentBlock.Paragraph("stem-current", "求函数的单调区间")),
            ),
            relevantLearningEvidence = emptyList(),
            relationCandidates = listOf(
                RelatedProblemCandidate(
                    problemId = RELATED_PROBLEM_ID,
                    problemRevisionId = "revision-related",
                    subject = SubjectKind.MATH,
                    title = "导数变式",
                    questionDocument = QuestionDocument(
                        id = "question-related",
                        blocks = listOf(
                            ContentBlock.Paragraph("stem-related", "讨论参数函数的单调性"),
                        ),
                    ),
                ),
            ),
            knowledgeBaseNodes = listOf(
                KnowledgeBaseNodeContext(
                    knowledgeNodeId = "math-derivative-sign-monotonicity",
                    subject = SubjectKind.MATH,
                    canonicalName = "根据导数符号判断函数单调性",
                    aliases = emptyList(),
                    kind = KnowledgeNodeKind.REASONING,
                    granularity = KnowledgeNodeGranularity.ATOMIC,
                    parentCanonicalName = "利用导数研究函数单调性",
                    taxonomyVersion = "math-v1",
                    verificationStatus = KnowledgeNodeVerificationStatus.CURATED,
                    boundaryMarkdown = "不包含求导公式的机械计算。",
                ),
            ),
        )
        val request = ModelTaskRequest(
            requestId = "organization-request",
            input = input,
            occurredAtEpochMillis = REQUEST_OCCURRED_AT,
            egressManifest = ModelEgressManifest(
                authorizationId = "organization-authorization",
                subjectId = input.subjectId,
                purpose = ModelEgressPurpose.CLASSIFICATION,
                authorizedTaskKinds = setOf(ModelTaskKind.PROBLEM_CLASSIFY),
                providerId = capabilities.providerId,
                modelId = capabilities.modelId,
                providerConfigurationVersion = capabilities.providerConfigurationVersion,
                promptPolicyVersion = ModelPromptPolicyVersions.PROBLEM_ORGANIZATION,
                approvedAtEpochMillis = AUTHORIZATION_APPROVED_AT,
                assets = emptyList(),
                disclosedData = ModelEgressManifest.PROBLEM_ORGANIZATION_DISCLOSURE,
            ),
        )
        return ModelEgressPolicy.authorize(request, capabilities, AUTHORIZATION_NOW)
    }

    private fun manifest(
        capabilities: com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot,
        authorizationId: String,
    ) = ModelEgressManifest(
        authorizationId = authorizationId,
        subjectId = DRAFT_ID,
        purpose = ModelEgressPurpose.CAPTURE_TO_DOCUMENT,
        authorizedTaskKinds = setOf(ModelTaskKind.CAPTURE_ASSESS, ModelTaskKind.CAPTURE_PARSE),
        providerId = capabilities.providerId,
        modelId = capabilities.modelId,
        providerConfigurationVersion = capabilities.providerConfigurationVersion,
        promptPolicyVersion = ModelPromptPolicyVersions.CAPTURE_DOCUMENT,
        approvedAtEpochMillis = AUTHORIZATION_APPROVED_AT,
        assets = listOf(ModelEgressAssetGrant(ASSET_ID, SHA, IMAGE.size.toLong(), 100, 200)),
        disclosedData = ModelEgressManifest.CAPTURE_IMAGE_DISCLOSURE,
    )

    private fun asset() = RestrictedModelAsset(
        assetId = ASSET_ID,
        sha256 = SHA,
        mimeType = "image/jpeg",
        byteSize = IMAGE.size.toLong(),
        width = 100,
        height = 200,
        stream = ByteArrayInputStream(IMAGE),
    )

    @Test
    fun streamingTutorPlanEmitsIncrementalProgressBeforeCompletion() = runBlocking {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ ->
                ModelHttpResponse(
                    statusCode = 200,
                    body = envelope(tutorPayload()),
                    // Each frame is its own long delta so the accumulated body clears the 64-char
                    // emit threshold and the gateway surfaces a genuine progressive prefix.
                    streamChunks = listOf(
                        "开场：这道题先看导数变号，再讨论驻点与极值的分布。补充：注意定义域端点是否闭合，从而判断单调区间是否能够合并。",
                    ),
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedTutor(gateway)).toList()
        // The gateway also emits pre-flight phase Progress events (reading image, validating) and a
        // terminal Completed. The streamed content is the final Progress before Completion.
        val streamedContent = events
            .filterIsInstance<ModelGatewayEvent.Progress>()
            .lastOrNull()
            ?.userMessage
        val terminal = events.last() as ModelGatewayEvent.Completed

        // The provider streamed tutor content, so the gateway must surface a final progressive
        // message that is exactly the concatenated delta body.
        assertEquals(
            "开场：这道题先看导数变号，再讨论驻点与极值的分布。补充：注意定义域端点是否闭合，从而判断单调区间是否能够合并。",
            streamedContent,
        )
        // The final event still carries a parseable tutor plan.
        assertEquals(TUTOR_SESSION_ID, (terminal.output as TutorPlanOutput).sessionId)
    }

    @Test
    fun streamingPrefixStaysWithinTheStatusBudgetForLongAnswers() = runBlocking {
        // A real tutor answer passes the 500-character status budget quickly. The gateway used to
        // emit the unbounded running prefix, and persisting it aborted the whole task mid-stream
        // ("Model task message exceeds budget"), so the answer never reached the student.
        val longAnswer = "先看导数变号，再讨论驻点与极值的分布。" .repeat(80)
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ ->
                ModelHttpResponse(
                    statusCode = 200,
                    body = envelope(tutorPayload()),
                    streamChunks = listOf(longAnswer),
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedTutor(gateway)).toList()
        val progressMessages = events
            .filterIsInstance<ModelGatewayEvent.Progress>()
            .map { it.userMessage }
        val terminal = events.last() as ModelGatewayEvent.Completed

        assertTrue(longAnswer.length > MODEL_TASK_STATUS_MESSAGE_MAX_CHARS)
        assertTrue(progressMessages.isNotEmpty())
        assertTrue(
            progressMessages.all { it.length <= MODEL_TASK_STATUS_MESSAGE_MAX_CHARS },
        )
        // The complete answer still lands in the terminal event.
        assertEquals(TUTOR_SESSION_ID, (terminal.output as TutorPlanOutput).sessionId)
    }

    @Test
    fun liveReasoningFramesSurfaceBeforeTheAnswerWhileTheCallIsStillRunning() = runBlocking {
        // A reasoning model thinks for a long time before answering. The gateway must surface that
        // chain-of-thought as progress while the call is still in flight, bounded by the same status
        // budget as any other progress message.
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { request ->
                request.onReasoningDelta?.invoke("第一步：代入定义域。")
                delay(900)
                request.onReasoningDelta?.invoke("第二步：讨论端点是否闭合。")
                delay(900)
                request.onReasoningDelta?.invoke("第三步：合并区间。")
                delay(900)
                ModelHttpResponse(
                    statusCode = 200,
                    body = envelope(tutorPayload()),
                    streamChunks = listOf("答案是 3。"),
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedTutor(gateway)).toList()
        val reasoningProgress = events
            .filterIsInstance<ModelGatewayEvent.Progress>()
            .map { it.userMessage }
            .filter { it.contains("定义域") }

        assertTrue("reasoning must surface as live progress", reasoningProgress.isNotEmpty())
        assertTrue(
            "the live reasoning text stays within the status budget",
            reasoningProgress.all { it.length <= MODEL_TASK_STATUS_MESSAGE_MAX_CHARS },
        )
        // The answer itself still arrives as the terminal output.
        assertEquals(TUTOR_SESSION_ID, ((events.last() as ModelGatewayEvent.Completed).output as TutorPlanOutput).sessionId)
    }

    @Test
    fun liveReasoningFramesStaySparseEnoughForTheEventBudget() = runBlocking {
        // The repository rejects a task that produces too many gateway events, so the live thinking
        // must stay sparse (a few backed-off frames) rather than one frame per poll — otherwise a
        // long reasoning run kills its own reply.
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { request ->
                repeat(120) { index ->
                    request.onReasoningDelta?.invoke("思考片段$index ")
                    delay(40)
                }
                ModelHttpResponse(
                    statusCode = 200,
                    body = envelope(tutorPayload()),
                    streamChunks = listOf("答案是 3。"),
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedTutor(gateway)).toList()
        val progressFrames = events.filterIsInstance<ModelGatewayEvent.Progress>()

        assertTrue("live thinking must still surface", progressFrames.any { it.userMessage.contains("思考片段") })
        assertTrue(
            "progress frames must stay sparse, got ${progressFrames.size}",
            progressFrames.size <= 16,
        )
        assertEquals(
            TUTOR_SESSION_ID,
            ((events.last() as ModelGatewayEvent.Completed).output as TutorPlanOutput).sessionId,
        )
    }

    @Test
    fun aRouteBRoundNeverPutsItsEnvelopeOnTheAnswerChannel() = runBlocking {
        // Route B（json_object / prompt 信封，Anthropic/Gemini/Responses 恒走）：这一轮的 content
        // 增量是**信封**，把它当正文流出去，学生看到的就是一段 JSON（A6）。思考链照常流。
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { request ->
                request.onReasoningDelta?.invoke("先判断定义域。")
                request.onContentDelta?.invoke("{\"intentDecision\":{\"intent\":\"CURRENT_QUESTION_HELP\"}")
                delay(400)
                request.onContentDelta?.invoke(",\"messageMarkdown\":\"第一步：求导。\"}")
                delay(400)
                ModelHttpResponse(
                    statusCode = 200,
                    body = envelope(tutorPayload()),
                    streamChunks = listOf("{\"intentDecision\":{\"intent\":\"CURRENT_QUESTION_HELP\"}"),
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedTutor(gateway)).toList()
        val live = events.filterIsInstance<ModelGatewayEvent.LiveProgress>()

        assertTrue(
            "思考链必须照常逐 token 流出来",
            live.any { it.kind == ModelLiveKind.THINKING && it.text.contains("定义域") },
        )
        assertTrue(
            "Route B 回合的 ANSWER 通道必须为空（信封不是正文）：" +
                live.filter { it.kind == ModelLiveKind.ANSWER }.map { it.text },
            live.none { it.kind == ModelLiveKind.ANSWER },
        )
        // 终态照常解析出正文（信封在解析层被拆开，学生最终读到的是 messageMarkdown）。
        assertEquals(
            TUTOR_SESSION_ID,
            ((events.last() as ModelGatewayEvent.Completed).output as TutorPlanOutput).sessionId,
        )
    }

    @Test
    fun aRouteAAnswerRoundStillStreamsItsBodyCharacterByCharacter() = runBlocking {
        // Route A（原生 tools 且端点被证明支持）：content 增量才是逐字长出来的正文——这条通道
        // 不能因为 Route B 的修复被一起关掉（A3：任何地方都有流）。
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(
                CONFIGURATION.copy(
                    capabilityVerification = requireNotNull(CONFIGURATION.capabilityVerification)
                        .copy(supportsFunctionCalling = true),
                ),
            ),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { request ->
                request.onContentDelta?.invoke("第一步：")
                delay(400)
                request.onContentDelta?.invoke("求导。")
                delay(400)
                ModelHttpResponse(
                    statusCode = 200,
                    body = envelope(tutorPayload()),
                    streamChunks = listOf("第一步：求导。"),
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(
            authorizedTutor(
                gateway,
                input = tutorInput().copy(toolDeclarations = TUTOR_TOOL_DECLARATIONS.toList()),
            ),
        ).toList()
        val answerFrames = events.filterIsInstance<ModelGatewayEvent.LiveProgress>()
            .filter { it.kind == ModelLiveKind.ANSWER }
            .map { it.text }

        assertEquals("第一步：求导。", answerFrames.lastOrNull())
    }

    @Test
    fun aNativeToolRoundMovesItsNarrationOutOfTheBodyIntoTheToolUnit() = runBlocking {
        // Route A 的工具轮：模型一边说"我先查一下错题本"一边申请工具。那句话是**叙述**，属于
        // 查阅单元；正文位置必须被撤回（发一条空正文），否则它会留在回答里冒充答案。
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(
                CONFIGURATION.copy(
                    capabilityVerification = requireNotNull(CONFIGURATION.capabilityVerification)
                        .copy(supportsFunctionCalling = true),
                ),
            ),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { request ->
                request.onContentDelta?.invoke("我先查一下错题本。")
                // 先让它按正文发出去（轮询周期 120ms），再报出工具调用增量：这正是"叙述
                // 曾经留在正文位置"的那条路径，撤回必须发生。
                delay(400)
                request.onToolCallDelta?.invoke()
                delay(400)
                ModelHttpResponse(
                    statusCode = 200,
                    body = envelope(tutorPayload()),
                    streamChunks = emptyList(),
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(
            authorizedTutor(
                gateway,
                input = tutorInput().copy(toolDeclarations = TUTOR_TOOL_DECLARATIONS.toList()),
            ),
        ).toList()
        val live = events.filterIsInstance<ModelGatewayEvent.LiveProgress>()

        assertTrue(
            "工具轮的叙述必须出现在查阅单元（TOOL 通道）：" +
                live.filter { it.kind == ModelLiveKind.TOOL }.map { it.text },
            live.any { it.kind == ModelLiveKind.TOOL && it.text.contains("我先查一下错题本") },
        )
        assertEquals(
            "正文位置必须被撤回（最后一条 ANSWER 是空的）",
            "",
            live.filter { it.kind == ModelLiveKind.ANSWER }.lastOrNull()?.text,
        )
    }

    @Test
    fun longStreamedRepliesKeepTheirProgressFrameCountBounded() = runBlocking {
        // Providers stream one delta per token, so a long answer arrives as thousands of frames.
        // The emission stride has to adapt to the transcript length, otherwise the repository's
        // gateway-event cap kills the very reply that is still streaming.
        val chunks = (1..2_000).map { index -> "片段$index " }
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { _ ->
                ModelHttpResponse(
                    statusCode = 200,
                    body = envelope(tutorPayload()),
                    streamChunks = chunks,
                )
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedTutor(gateway)).toList()
        val progressFrames = events.filterIsInstance<ModelGatewayEvent.Progress>()

        assertTrue(
            "progress frames must stay bounded, got ${progressFrames.size}",
            progressFrames.size <= 32,
        )
        assertEquals(
            TUTOR_SESSION_ID,
            ((events.last() as ModelGatewayEvent.Completed).output as TutorPlanOutput).sessionId,
        )
    }

    @Test
    fun capabilityFingerprintChangesWhenTheProtocolChanges() = runBlocking {
        val openAi = gatewayWithProtocol(ModelProviderProtocol.OPENAI_CHAT_COMPLETIONS).capabilities()
        val anthropic = gatewayWithProtocol(ModelProviderProtocol.ANTHROPIC_MESSAGES).capabilities()

        // 协议进指纹：切换协议必须让旧的 providerId / 版本串失效（spec §3.2）。
        assertTrue(openAi.providerId != anthropic.providerId)
        assertTrue(
            anthropic.providerConfigurationVersion
                .startsWith(ModelProviderProtocol.ANTHROPIC_MESSAGES.wireId),
        )
    }

    @Test
    fun configuredProtocolDrivesTheWireProtocolInsteadOfAlwaysDefaulting() = runBlocking {
        var sentUrl = ""
        var sentBody = ""
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(
                CONFIGURATION.copy(protocol = ModelProviderProtocol.GEMINI_GENERATE_CONTENT),
            ),
            assetSource = assetSource { _, _ -> asset() },
            transport = modelTransport { request ->
                sentUrl = request.url.toString()
                sentBody = request.body
                ModelHttpResponse(200, geminiEnvelope(assessmentPayload()))
            },
            clock = { AUTHORIZATION_NOW },
        )

        val events = gateway.execute(authorizedAssessment(gateway)).toList()

        assertTrue(events.last() is ModelGatewayEvent.Completed)
        // 配置的协议真的驱动了线上形状：Gemini 端点 + contents/parts 信封（不是 OpenAI 的 messages）。
        assertTrue("应走 Gemini 端点，实际=$sentUrl", sentUrl.contains(":generateContent"))
        assertTrue(sentBody.contains("\"contents\""))
        assertTrue(sentBody.contains("\"parts\""))
        assertTrue("不得出现 OpenAI 的 messages 信封", !sentBody.contains("\"messages\""))
    }

    private fun geminiEnvelope(content: String): String = Json.encodeToString(
        buildJsonObject {
            put(
                "candidates",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                "content",
                                buildJsonObject {
                                    put(
                                        "parts",
                                        buildJsonArray {
                                            add(buildJsonObject { put("text", content) })
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    private fun gatewayWithProtocol(
        protocol: ModelProviderProtocol,
        onPost: () -> Unit = {},
    ): OpenAiCompatibleModelGateway = OpenAiCompatibleModelGateway(
        configurationStore = FakeConfigurationStore(CONFIGURATION.copy(protocol = protocol)),
        assetSource = assetSource { _, _ -> asset() },
        transport = modelTransport { _ ->
            onPost()
            ModelHttpResponse(200, envelope(assessmentPayload()))
        },
        clock = { AUTHORIZATION_NOW },
    )

    private fun modelTransport(
        post: suspend (WireRequest) -> ModelHttpResponse,
    ): ModelHttpTransport = ModelHttpTransport { request, beforeEnqueue ->
        beforeEnqueue()
        post(request)
    }

    private fun assetSource(
        open: suspend (ModelGatewayExecution, String) -> RestrictedModelAsset,
    ): RestrictedModelAssetSource = object : RestrictedModelAssetSource {
        override suspend fun open(
            execution: ModelGatewayExecution,
            assetId: String,
        ): RestrictedModelAsset = open(execution, assetId)
    }

    /**
     * 会话带图走的是全局同意通道：没有逐次披露清单，能力门自己把关（见 ModelEgressTest）。
     * 这正是 production 的形状——`TutorModelTaskPolicy` 构造会话请求时就是
     * `agentConsentGranted = external` + `egressManifest = null`。
     */
    private suspend fun consentedTutorRespond(
        gateway: OpenAiCompatibleModelGateway,
        input: TutorRespondInput,
    ): ModelGatewayExecution {
        val capabilities = gateway.capabilities()
        val request = ModelTaskRequest(
            requestId = "tutor-respond-images",
            input = input,
            occurredAtEpochMillis = REQUEST_OCCURRED_AT,
            agentConsentGranted = true,
            egressManifest = null,
        )
        return ModelEgressPolicy.authorize(request, capabilities, AUTHORIZATION_NOW)
    }

    private fun tutorRespondPayload(): String = Json.encodeToString(
        buildJsonObject {
            put(
                "intentDecision",
                buildJsonObject {
                    put("intent", "CURRENT_QUESTION_HELP")
                    put("confidence", 0.9)
                    put("explicitActionRequest", false)
                    put("memoryPreference", "UNCHANGED")
                    put("requestedLocalCapability", "NONE")
                },
            )
            put("messageMarkdown", "先看导数的符号。")
            put("solutionRevealed", false)
        },
    )

    private fun envelope(content: String): String = Json.encodeToString(
        buildJsonObject {
            put(
                "choices",
                buildJsonArray {
                    add(buildJsonObject {
                        put("message", buildJsonObject { put("content", content) })
                    })
                },
            )
        },
    )

    private fun assessmentPayload(): String = Json.encodeToString(
        buildJsonObject {
            put("decision", "PASS")
            put("issues", buildJsonArray {})
            put("suggestedActions", buildJsonArray {})
        },
    )

    private fun splitAssessmentPayload(): String = Json.encodeToString(
        buildJsonObject {
            put("decision", "SPLIT")
            put("issues", buildJsonArray {
                add(buildJsonObject {
                    put("code", "MULTIPLE_QUESTIONS")
                    put("severity", "BLOCKING")
                    put("message", "画面中有两道独立题目")
                })
            })
            put("suggestedActions", buildJsonArray {})
            put("questionRegions", buildJsonArray {
                add(buildJsonObject {
                    put("left", 0.05); put("top", 0.05)
                    put("right", 0.95); put("bottom", 0.45)
                })
                add(buildJsonObject {
                    put("left", 0.05); put("top", 0.55)
                    put("right", 0.95); put("bottom", 0.95)
                })
            })
        },
    )

    private fun parsePayload(): String = Json.encodeToString(
        buildJsonObject {
            put("title", "函数单调性")
            put(
                "blocks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "choice_group")
                            put("pageIndex", 0)
                            put("region", buildJsonObject {
                                put("left", 0.1); put("top", 0.2)
                                put("right", 0.9); put("bottom", 0.8)
                            })
                            put("writingLayer", "PRINTED")
                            put("confidence", 0.94)
                            put("promptMarkdown", "函数 ${'$'}f(x)${'$'} 的单调区间是")
                            put(
                                "choices",
                                buildJsonArray {
                                    add(buildJsonObject { put("markdown", "${'$'}(0,+∞)${'$'}") })
                                    add(buildJsonObject { put("markdown", "${'$'}(-∞,0)${'$'}") })
                                },
                            )
                            put("selectedChoiceId", "malicious-answer")
                            put("sourceAssetId", "malicious-asset")
                            put("id", "malicious-block")
                        },
                    )
                },
            )
        },
    )

    private fun figureParsePayload(): String = Json.encodeToString(
        buildJsonObject {
            put("title", "一次函数图像")
            put(
                "blocks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "figure")
                            put("pageIndex", 0)
                            put("region", buildJsonObject {
                                put("left", 0.1); put("top", 0.1)
                                put("right", 0.9); put("bottom", 0.9)
                            })
                            put("writingLayer", "PRINTED")
                            put("confidence", 0.91)
                            put("title", "函数 y=x")
                            put("alternativeText", "直线 y=x 经过原点")
                            put(
                                "schema",
                                buildJsonObject {
                                    put("type", "cartesian")
                                    put("xAxis", axis(-2.0, 2.0, "x"))
                                    put("yAxis", axis(-2.0, 2.0, "y"))
                                    put(
                                        "polylines",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("id", "untrusted-model-id")
                                                    put("label", "y=x")
                                                    put("style", "PRIMARY")
                                                    put(
                                                        "points",
                                                        buildJsonArray {
                                                            add(coordinate(-2.0, -2.0))
                                                            add(coordinate(2.0, 2.0))
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                    put("points", buildJsonArray { add(buildJsonObject {
                                        put("x", 0.0); put("y", 0.0); put("label", "O")
                                        put("style", "EMPHASIS")
                                    }) })
                                    put("labels", buildJsonArray { add(buildJsonObject {
                                        put("x", 1.0); put("y", 1.0); put("text", "A")
                                    }) })
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    private fun axis(minimum: Double, maximum: Double, label: String) = buildJsonObject {
        put("minimum", minimum)
        put("maximum", maximum)
        put("label", label)
        put("tickCount", 5)
    }

    private fun coordinate(x: Double, y: Double) = buildJsonObject {
        put("x", x)
        put("y", y)
    }
    private fun tutorPayload(
        includeDiagnostic: Boolean = true,
        includeNextMoves: Boolean = true,
        extraTopLevel: Pair<String, JsonElement>? = null,
    ): String = Json.encodeToString(
        buildJsonObject {
            put("openingMarkdown", "先看导数符号怎样变化，不急着写最终区间。")
            if (includeDiagnostic) {
                put(
                    "diagnosticQuestion",
                    buildJsonObject {
                        put("stemMarkdown", "导数先正后负时，原函数怎样变化？")
                        put("promptMarkdown", "请选择最能说明理由的一项")
                        put(
                            "choices",
                            buildJsonArray {
                                add(buildJsonObject {
                                    put("markdown", "先增后减")
                                    put("feedbackMarkdown", "导数正负和单调性对应正确。")
                                    put("isCorrect", true)
                                })
                                add(buildJsonObject {
                                    put("markdown", "先减后增")
                                    put("feedbackMarkdown", "正负对应关系反了。")
                                    put("isCorrect", false)
                                })
                                add(buildJsonObject {
                                    put("markdown", "始终递增")
                                    put("feedbackMarkdown", "忽略了导数变号。")
                                    put("isCorrect", false)
                                })
                            },
                        )
                    },
                )
            }
            put("solutionMarkdown", "求导并解符号不等式，再写出单调区间。")
            put("alternateMethodMarkdown", "画导函数符号表，从图像变化理解单调性。")
            put("difficultyReasonMarkdown", "区分正负对应错误和变号遗漏。")
            put("targetedEvidenceLabels", buildJsonArray { add(JsonPrimitive("导数符号")) })
            put(
                "inferredKnowledgeLabels",
                buildJsonArray {
                    add(JsonPrimitive("导数"))
                    add(JsonPrimitive("函数单调性"))
                },
            )
            if (includeNextMoves) {
                put(
                    "nextMoves",
                    buildJsonArray {
                        add(buildJsonObject {
                            put("label", "沿导数变号继续推")
                            put("type", "DEEPEN_REASONING")
                        })
                        add(buildJsonObject {
                            put("label", "改用函数图像理解")
                            put("type", "CHANGE_REPRESENTATION")
                        })
                        add(buildJsonObject {
                            put("label", "查看完整讲解")
                            put("type", "REVEAL_SOLUTION")
                        })
                    },
                )
            }
            extraTopLevel?.let { (key, value) -> put(key, value) }
        },
    )

    private fun tutorRespondPayload(
        messageMarkdown: String = "导数符号决定原函数在当前区间内的增减方向。",
        solutionRevealed: Boolean = false,
        nextMoves: JsonElement? = null,
        intentDecision: JsonElement? = tutorIntentPayload(),
        extraTopLevel: Pair<String, JsonElement>? = null,
    ): String = Json.encodeToString(
        buildJsonObject {
            intentDecision?.let { put("intentDecision", it) }
            put("messageMarkdown", messageMarkdown)
            put("solutionRevealed", solutionRevealed)
            nextMoves?.let { put("nextMoves", it) }
            extraTopLevel?.let { (key, value) -> put(key, value) }
        },
    )

    private fun tutorIntentPayload(
        intent: TutorMessageIntent = TutorMessageIntent.CURRENT_QUESTION_HELP,
        confidence: Double = 0.98,
        explicitActionRequest: Boolean = false,
        memoryPreference: TutorMemoryPreference = TutorMemoryPreference.UNCHANGED,
        capability: TutorRequestedLocalCapability = TutorRequestedLocalCapability.NONE,
        lookupTerms: List<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("intent", intent.name)
        put("confidence", confidence)
        put("explicitActionRequest", explicitActionRequest)
        put("memoryPreference", memoryPreference.name)
        put("requestedLocalCapability", capability.name)
        put(
            "lookupTerms",
            buildJsonArray {
                lookupTerms.forEach { term -> add(JsonPrimitive(term)) }
            },
        )
    }

    private suspend fun executeTutorPayload(payload: String): List<ModelGatewayEvent> {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> error("Tutor must not open image assets") },
            transport = modelTransport { _ -> ModelHttpResponse(200, envelope(payload)) },
            clock = { AUTHORIZATION_NOW },
        )
        return gateway.execute(authorizedTutor(gateway)).toList()
    }

    private suspend fun parsedTutorResponse(payload: String): TutorRespondOutput =
        executeTutorRespondPayload(payload).last()
            .let { it as ModelGatewayEvent.Completed }
            .output as TutorRespondOutput

    private suspend fun executeTutorRespondPayload(payload: String): List<ModelGatewayEvent> {
        val gateway = OpenAiCompatibleModelGateway(
            configurationStore = FakeConfigurationStore(CONFIGURATION),
            assetSource = assetSource { _, _ -> error("Tutor response must not open image assets") },
            transport = modelTransport { _ -> ModelHttpResponse(200, envelope(payload)) },
            clock = { AUTHORIZATION_NOW },
        )
        return gateway.execute(authorizedTutorRespond(gateway)).toList()
    }

    private fun stableTutorSuffix(input: TutorPlanInput): String =
        MessageDigest.getInstance("SHA-256")
            .digest(
                "${input.sessionId}\n${input.draftRevisionNumber}\n${input.cycleOrdinal}\n${input.turnOrdinal}"
                    .toByteArray(StandardCharsets.UTF_8),
            )
            .joinToString("") { "%02x".format(it) }
            .take(20)

    private fun stableTutorRespondSuffix(input: TutorRespondInput): String =
        MessageDigest.getInstance("SHA-256")
            .digest(
                "${input.sessionId}\n${input.draftRevisionNumber}\n${input.responseOrdinal}"
                    .toByteArray(StandardCharsets.UTF_8),
            )
            .joinToString("") { "%02x".format(it) }
            .take(20)

    private fun organizationPayload(): String = Json.encodeToString(
        buildJsonObject {
            put("summaryMarkdown", "这是一道利用导数判断单调性的题。")
            put("reviewPriorityMarkdown", "这道题综合使用导数符号与单调性，适合近期复习。")
            put("schemaVersion", 2)
            put("targetedEvidenceLabels", buildJsonArray {})
            put(
                "classifications",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("dimension", "KNOWLEDGE")
                            put("displayName", "利用导数研究函数单调性")
                            put("rationaleMarkdown", "核心步骤是求导并判断符号。")
                            put("confidence", 0.94)
                        },
                    )
                },
            )
            put(
                "relations",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("targetAlias", "candidate-1")
                            put("kind", "VARIANT_OF")
                            put("rationaleMarkdown", "两题共享导数符号分析。")
                            put("confidence", 0.82)
                        },
                    )
                },
            )
            put(
                "atomicKnowledge",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("referenceId", "atom-1")
                            put("canonicalName", "根据导数符号判断函数单调性")
                            put("aliases", buildJsonArray {})
                            put("kind", "REASONING")
                            put("parentKnowledgeDisplayName", "利用导数研究函数单调性")
                            put("existingAlias", "knowledge-1")
                            put("prerequisiteReferenceIds", buildJsonArray {})
                            put("observableOutcomeMarkdown", "能由导数符号确定函数增减区间。")
                            put("boundaryMarkdown", "不包含求导公式的机械计算。")
                            put("confidence", 0.94)
                        },
                    )
                },
            )
            put(
                "stepAttributions",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("stepOrdinal", 1)
                            put("stepSummaryMarkdown", "求导后判断导数符号。")
                            put(
                                "atomicReferenceIds",
                                buildJsonArray { add(JsonPrimitive("atom-1")) },
                            )
                        },
                    )
                },
            )
            put("groundingRequests", buildJsonArray {})
        },
    )

    private class BlockingBeforeEnqueueTransport(
        private val response: ModelHttpResponse,
    ) : ModelHttpTransport {
        val endpointPrepared = CompletableDeferred<Unit>()
        val continueToEnqueue = CompletableDeferred<Unit>()
        var networkEnqueueCount = 0
            private set

        override suspend fun post(
            request: WireRequest,
            beforeEnqueue: suspend () -> Unit,
        ): ModelHttpResponse {
            endpointPrepared.complete(Unit)
            continueToEnqueue.await()
            beforeEnqueue()
            networkEnqueueCount += 1
            return response
        }
    }

    private class FakeConfigurationStore(
        snapshot: ModelConfigurationSnapshot,
        private var credentialAvailable: Boolean = true,
    ) : ModelConfigurationStore {
        val state = MutableStateFlow(snapshot)
        private var credentialSecret = "secret"
        /** 最近一次签发出来的密钥容器；用于断言网关用毕即 close（key 生命周期）。 */
        var lastIssuedKey: ModelApiKey? = null
            private set
        override val configuration: Flow<ModelConfigurationSnapshot> = state

        override suspend fun save(
            update: ModelConfigurationUpdate,
            apiKey: ModelApiKey,
        ): ModelConfigurationMutationResult = error("Not used")

        override suspend fun rotateApiKey(apiKey: ModelApiKey): ModelConfigurationMutationResult =
            error("Not used")

        override suspend fun readCredential(): ModelCredentialReadResult =
            if (credentialAvailable) {
                val issued = ModelApiKey.from(credentialSecret.toCharArray())
                lastIssuedKey = issued
                ModelCredentialReadResult.Available(state.value, issued)
            } else {
                ModelCredentialReadResult.Missing
            }

        override suspend fun clear(): ModelConfigurationMutationResult = error("Not used")

        fun clearCredential() {
            credentialAvailable = false
        }

        fun rotateCredential() {
            val rotatedVersion = "${state.value.configurationVersion}-rotated"
            val rotatedUpdatedAt = state.value.updatedAtEpochMillis + 1
            state.value = state.value.copy(
                updatedAtEpochMillis = rotatedUpdatedAt,
                configurationVersion = rotatedVersion,
                capabilityVerification = state.value.capabilityVerification?.copy(
                    configurationVersion = rotatedVersion,
                    configurationUpdatedAtEpochMillis = rotatedUpdatedAt,
                ),
            )
            credentialSecret = "rotated-secret"
        }
    }

    private companion object {
        const val DRAFT_ID = "draft-1"
        const val ASSET_ID = "asset-1"
        const val TUTOR_SESSION_ID = "tutor-session-secret-local-id"
        const val ORGANIZATION_PROBLEM_ID = "problem-current-secret-local-id"
        const val RELATED_PROBLEM_ID = "problem-related-secret-local-id"
        const val REQUEST_OCCURRED_AT = 1_000_000L
        const val AUTHORIZATION_APPROVED_AT = 1_000_001L
        const val AUTHORIZATION_NOW = 1_000_002L
        const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val IMAGE = byteArrayOf(1, 2, 3, 4)
        val CONFIGURATION = ModelConfigurationSnapshot(
            provider = "兼容模型服务",
            baseUrl = "https://api.example.com/v1",
            modelId = "vision-model",
            isConfigured = true,
            updatedAtEpochMillis = 123,
            configurationVersion = "test-configuration-v1",
            capabilityVerification = ModelCapabilityVerification(
                provider = "兼容模型服务",
                baseUrl = "https://api.example.com/v1",
                modelId = "vision-model",
                configurationVersion = "test-configuration-v1",
                configurationUpdatedAtEpochMillis = 123,
                supportsImageInput = true,
                supportsStructuredOutput = true,
                testedAtEpochMillis = 124,
            ),
        )
    }
}
