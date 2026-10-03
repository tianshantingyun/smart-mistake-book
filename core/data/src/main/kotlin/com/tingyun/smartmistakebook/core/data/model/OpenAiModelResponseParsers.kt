package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult
import com.tingyun.smartmistakebook.core.domain.ModelGateway
import com.tingyun.smartmistakebook.core.domain.RestrictedModelAssetSource
import com.tingyun.smartmistakebook.core.domain.currentCapabilityVerification
import com.tingyun.smartmistakebook.core.model.CaptureAssessment
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentAction
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentDecision
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentInput
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentIssue
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentIssueCode
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOutput
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentSeverity
import com.tingyun.smartmistakebook.core.model.CapturePageRelation
import com.tingyun.smartmistakebook.core.model.CaptureParseInput
import com.tingyun.smartmistakebook.core.model.CaptureParseOutput
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureAxis
import com.tingyun.smartmistakebook.core.model.FigureCoordinate
import com.tingyun.smartmistakebook.core.model.FigureLabel
import com.tingyun.smartmistakebook.core.model.FigurePoint
import com.tingyun.smartmistakebook.core.model.FigurePolyline
import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.FigureSeriesStyle
import com.tingyun.smartmistakebook.core.model.ImagePipelineClassifyOutput
import com.tingyun.smartmistakebook.core.model.ImagePipelineProblemKind
import com.tingyun.smartmistakebook.core.model.MODEL_EGRESS_MAX_ASSET_BYTES
import com.tingyun.smartmistakebook.core.model.ModelEgressAuthorizationException
import com.tingyun.smartmistakebook.core.model.ModelEgressPolicy
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelExecutionPermit
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelGatewayEvent
import com.tingyun.smartmistakebook.core.model.ModelGatewayExecution
import com.tingyun.smartmistakebook.core.model.ModelRequestBudgetExceededException
import com.tingyun.smartmistakebook.core.model.ModelRequestPayloadBudget
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskOutput
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationInput
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.StructuredChoice
import com.tingyun.smartmistakebook.core.model.StructuredContentLimits
import com.tingyun.smartmistakebook.core.model.StructuredContentSanitizer
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import com.tingyun.smartmistakebook.core.model.TutorDifficultyTier
import com.tingyun.smartmistakebook.core.model.TutorAdvisoryKind
import com.tingyun.smartmistakebook.core.model.TutorAdvisoryScope
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest
import com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.TutorUnderstandingTier
import com.tingyun.smartmistakebook.core.model.TutorDebriefInput
import com.tingyun.smartmistakebook.core.model.TutorDebriefOutput
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.domain.TutorRoundQuestionBindingPolicy
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolRequestsOutput
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorLobbyOutput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRoundQuestionDeclaration
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.TutorIntentDecision
import com.tingyun.smartmistakebook.core.model.TutorSuggestedMove
import com.tingyun.smartmistakebook.core.model.TutorTurnPlan
import com.tingyun.smartmistakebook.core.model.WritingLayer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString

internal fun JsonObject.toAssessment(modelVersion: String): CaptureAssessment {
    requireOnlyKeys(CAPTURE_ASSESSMENT_WIRE_KEYS)
    val decision = enumValue<CaptureAssessmentDecision>(requiredString("decision"))
    val issues = array("issues").map { item ->
        val issue = item.objectValue()
        issue.requireOnlyKeys(CAPTURE_ASSESSMENT_ISSUE_WIRE_KEYS)
        CaptureAssessmentIssue(
            code = enumValue(issue.requiredString("code")),
            severity = enumValue(issue.requiredString("severity")),
            region = issue.optionalRegion(),
            message = issue.requiredString("message").take(512),
        )
    }
    val actions = array("suggestedActions").map { item ->
        enumValue<CaptureAssessmentAction>(item.jsonPrimitive.content)
    }.distinct()
    // questionRegions are page-local normalized coordinates; a valid response
    // must satisfy the model-layer contract enforced inside CaptureAssessment.
    val questionRegions = optionalArray("questionRegions").map { item ->
        item.objectValue().toRegion()
    }
    return CaptureAssessment(
        decision = decision,
        issues = issues,
        suggestedActions = actions,
        questionRegions = questionRegions,
        followingPageRelations = optionalArray("followingPageRelations").map { relation ->
            enumValue<CapturePageRelation>(relation.jsonPrimitive.content)
        },
        modelVersion = modelVersion,
    )
}

internal fun JsonObject.toCapturedDocument(
    input: CaptureParseInput,
    modelVersion: String,
): CaptureParseOutput {
    val wireBlocks = array("blocks")
    require(wireBlocks.isNotEmpty() && wireBlocks.size <= StructuredContentLimits.MAX_BLOCKS)
    val metadata = mutableListOf<BlockMetadata>()
    val blocks = wireBlocks.mapIndexed { blockIndex, item ->
        val block = item.objectValue()
        val blockId = "block-${blockIndex + 1}"
        val blockType = block.requiredString("type")
        val source = input.sourceAssets.getOrNull(block.optionalInt("pageIndex") ?: 0)
            ?: throw InvalidModelResponseException()
        val region = block.requiredRegion()
        val selectedRegion = source.selectedRegion
        if (selectedRegion != null && !selectedRegion.contains(region)) {
            throw InvalidModelResponseException()
        }
        metadata += BlockMetadata(
            sourceAssetId = source.assetId,
            region = region,
            writingLayer = if (blockType == "figure" || blockType == "diagram_note") {
                WritingLayer.DIAGRAM
            } else {
                enumValue(block.requiredString("writingLayer"))
            },
            confidence = block.optionalDouble("confidence"),
        )
        when (blockType) {
            "paragraph" -> ContentBlock.Paragraph(
                id = blockId,
                markdown = block.requiredString("markdown"),
            )
            "formula" -> ContentBlock.Formula(
                id = blockId,
                latex = block.requiredString("latex"),
                alternativeText = block.requiredString("alternativeText"),
            )
            "choice_group" -> ContentBlock.ChoiceGroup(
                id = blockId,
                promptMarkdown = block.requiredString("promptMarkdown"),
                choices = block.array("choices").mapIndexed { choiceIndex, choiceItem ->
                    val choice = choiceItem.objectValue()
                    StructuredChoice(
                        id = "choice-${blockIndex + 1}-${choiceIndex + 1}",
                        markdown = choice.requiredString("markdown"),
                        accessibilityLabel = choice.optionalString("accessibilityLabel"),
                    )
                },
                selectedChoiceId = null,
            )
            "figure" -> ContentBlock.Figure(
                id = blockId,
                title = block.optionalString("title"),
                alternativeText = block.requiredString("alternativeText"),
                schema = block.objectValue("schema").toFigureSchema(blockIndex),
            )
            "diagram_note" -> ContentBlock.Paragraph(
                id = blockId,
                markdown = "图示：${block.requiredString("alternativeText")}",
            )
            else -> throw InvalidModelResponseException()
        }
    }
    val rawDocument = QuestionDocument(
        id = "document-${input.draftId}",
        title = optionalString("title"),
        blocks = blocks,
    )
    val sanitized = StructuredContentSanitizer.sanitize(rawDocument)
    if (sanitized.issues.isNotEmpty() || sanitized.document.blocks.size != metadata.size) {
        throw InvalidModelResponseException()
    }
    val evidence = sanitized.document.blocks.mapIndexed { index, block ->
        val meta = metadata[index]
        QuestionBlockEvidence(
            blockId = block.id,
            sourceAssetId = meta.sourceAssetId,
            sourceRegion = meta.region,
            writingLayer = meta.writingLayer,
            provenance = QuestionBlockProvenance.MODEL_DOCUMENT_PARSE,
            confidence = meta.confidence,
            reviewStatus = QuestionBlockReviewStatus.NEEDS_REVIEW,
            producerVersion = modelVersion,
        )
    }
    return CaptureParseOutput(
        capturedDocument = CapturedQuestionDocument(
            document = sanitized.document,
            blockEvidence = evidence,
        ),
        modelVersion = modelVersion,
    )
}

internal fun JsonObject.toImagePipelineClassify(
    modelVersion: String,
): ImagePipelineClassifyOutput {
    val kind = requiredString("problemKind")
    val problemKind = enumValue<ImagePipelineProblemKind>(kind.uppercase())
    val textMarkdown = optionalString("textMarkdown") ?: ""
    val formulas = array("formulas").mapNotNull { item ->
        (item as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    }
    return ImagePipelineClassifyOutput(
        problemKind = problemKind,
        textMarkdown = textMarkdown,
        formulas = formulas,
        modelVersion = modelVersion,
    )
}

internal fun JsonObject.toFigureSchema(blockIndex: Int): FigureSchema =
    when (requiredString("type")) {
        "cartesian" -> FigureSchema.Cartesian(
            xAxis = objectValue("xAxis").toFigureAxis(),
            yAxis = objectValue("yAxis").toFigureAxis(),
            polylines = array("polylines").mapIndexed { index, item ->
                val polyline = item.objectValue()
                FigurePolyline(
                    id = "polyline-${blockIndex + 1}-${index + 1}",
                    points = polyline.array("points").map { it.objectValue().toFigureCoordinate() },
                    label = polyline.optionalString("label"),
                    style = polyline.optionalStyle(),
                )
            },
            points = array("points").map { item ->
                val point = item.objectValue()
                FigurePoint(
                    coordinate = point.toFigureCoordinate(),
                    label = point.optionalString("label"),
                    style = point.optionalStyle(),
                )
            },
            labels = array("labels").map { item ->
                val label = item.objectValue()
                FigureLabel(
                    coordinate = label.toFigureCoordinate(),
                    text = label.requiredString("text"),
                )
            },
        )
        "symbol_table" -> FigureSchema.SymbolTable(
            headers = array("headers").map { it.requiredPrimitiveString() },
            rows = array("rows").map { row ->
                (row as? JsonArray)?.map { it.requiredPrimitiveString() }
                    ?: throw InvalidModelResponseException()
            },
        )
        else -> throw InvalidModelResponseException()
    }

internal fun JsonObject.toFigureAxis(): FigureAxis = FigureAxis(
    minimum = requiredFiniteDouble("minimum"),
    maximum = requiredFiniteDouble("maximum"),
    label = optionalString("label").orEmpty(),
    tickCount = optionalInt("tickCount") ?: 5,
)

internal fun JsonObject.toFigureCoordinate(): FigureCoordinate = FigureCoordinate(
    x = requiredFiniteDouble("x"),
    y = requiredFiniteDouble("y"),
)

internal fun JsonObject.optionalStyle(): FigureSeriesStyle =
    optionalString("style")?.let { enumValue(it) } ?: FigureSeriesStyle.PRIMARY

internal fun JsonObject.toTutorPlan(
    input: TutorPlanInput,
    modelVersion: String,
): TutorPlanOutput {
    requireOnlyKeys(TUTOR_PLAN_WIRE_KEYS)
    val stableSuffix = MessageDigest.getInstance("SHA-256")
        .digest(
            "${input.sessionId}\n${input.draftRevisionNumber}\n${input.cycleOrdinal}\n${input.turnOrdinal}"
                .toByteArray(StandardCharsets.UTF_8),
        )
        .joinToString("") { "%02x".format(it) }
        .take(20)
    val diagnosticItem = optionalObject("diagnosticQuestion")?.let { diagnostic ->
        diagnostic.requireOnlyKeys(TUTOR_DIAGNOSTIC_WIRE_KEYS)
        val wireChoices = diagnostic.array("choices")
        require(wireChoices.size in 2..TutorTurnPlan.MAX_INTERACTION_CHOICES)
        val correctIndexes = wireChoices.mapIndexedNotNull { index, item ->
            index.takeIf { item.objectValue().requiredBoolean("isCorrect") }
        }
        require(correctIndexes.size == 1)
        val choices = wireChoices.mapIndexed { index, item ->
            val choice = item.objectValue()
            choice.requireOnlyKeys(TUTOR_DIAGNOSTIC_CHOICE_WIRE_KEYS)
            TutorChoice(
                id = "choice-${index + 1}",
                markdown = choice.requiredString("markdown"),
                feedbackMarkdown = choice.requiredString("feedbackMarkdown"),
            )
        }
        TutorAssessmentItem(
            id = "tutor-diagnostic-$stableSuffix",
            stemMarkdown = diagnostic.requiredString("stemMarkdown"),
            promptMarkdown = diagnostic.optionalString("promptMarkdown"),
            choices = choices,
            correctChoiceId = choices[correctIndexes.single()].id,
            knowledgeNodeIds = emptySet(),
        )
    }
    val disclosedLabels = input.relevantLearningEvidence.mapTo(hashSetOf()) { it.displayName }
    val targetedLabels = array("targetedEvidenceLabels")
        .map { it.jsonPrimitive.content }
        .distinct()
    require(targetedLabels.all(disclosedLabels::contains))
    val inferredLabels = array("inferredKnowledgeLabels")
        .map { it.jsonPrimitive.content }
        .distinct()
    val suggestedMoves = array("nextMoves").mapIndexed { index, item ->
        val move = item.objectValue()
        move.requireOnlyKeys(TUTOR_MOVE_WIRE_KEYS)
        TutorSuggestedMove(
            id = "move-${input.cycleOrdinal}-${input.turnOrdinal}-${index + 1}",
            label = move.requiredString("label"),
            type = enumValue(move.requiredString("type")),
        )
    }
    return TutorPlanOutput(
        sessionId = input.sessionId,
        draftRevisionNumber = input.draftRevisionNumber,
        questionDocumentId = input.questionDocument.id,
        plan = TutorTurnPlan(
            openingMarkdown = requiredString("openingMarkdown"),
            diagnosticItem = diagnosticItem,
            solutionMarkdown = requiredString("solutionMarkdown"),
            alternateMethodMarkdown = requiredString("alternateMethodMarkdown"),
            difficultyReasonMarkdown = requiredString("difficultyReasonMarkdown"),
            targetedEvidenceLabels = targetedLabels,
            inferredKnowledgeLabels = inferredLabels,
            suggestedMoves = suggestedMoves,
            thinkingMarkdown = optionalString("thinkingMarkdown"),
        ),
        modelVersion = modelVersion,
        cycleOrdinal = input.cycleOrdinal,
        turnOrdinal = input.turnOrdinal,
        attachedImages = optionalArray("attachedImages").map(JsonElement::toAttachedImage),
    )
}

internal fun JsonObject.toTutorRespond(
    input: TutorRespondInput,
    modelVersion: String,
): TutorRespondOutput {
    requireOnlyKeys(TUTOR_RESPOND_WIRE_KEYS)
    val stableSuffix = MessageDigest.getInstance("SHA-256")
        .digest(
            "${input.sessionId}\n${input.draftRevisionNumber}\n${input.responseOrdinal}"
                .toByteArray(StandardCharsets.UTF_8),
        )
        .joinToString("") { "%02x".format(it) }
        .take(20)
    val suggestedMoves = optionalArray("nextMoves").mapIndexed { index, element ->
        val move = element.objectValue()
        move.requireOnlyKeys(TUTOR_MOVE_WIRE_KEYS)
        TutorSuggestedMove(
            id = "tutor-respond-move-$stableSuffix-${index + 1}",
            label = move.requiredString("label"),
            type = enumValue(move.requiredString("type")),
        )
    }
    val intentDecision = optionalObject("intentDecision")?.toTutorIntentDecision()
        ?: TutorIntentDecision.ambiguousDefault()
    // 本地两条校验在**这里**落地：核不过的声明不进入输出，输出里的 boundQuestion 就是
    // "本轮真正绑定的题"。下游（写门控、答案暴露、落库、渲染）因此不必各自再判一次，
    // 也不会出现"某个调用点忘了校验"的漏口——这个解析器是 TutorRespondOutput 的唯一生产者。
    val boundQuestion = TutorRoundQuestionBindingPolicy.resolve(
        candidates = input.boundQuestionCandidates,
        declaration = optionalObject("boundQuestion")?.toBoundQuestionDeclaration(),
        studentMessage = input.studentMessage,
        // 学生显式附加了题的一轮：附加题被钉住，指向别的候选的声明在这里就被判无效。
        pinnedQuestion = input.attachedQuestion?.toCandidate(),
    )
    return TutorRespondOutput(
        sessionId = input.sessionId,
        draftRevisionNumber = input.draftRevisionNumber,
        questionDocumentId = input.questionDocument.id,
        responseOrdinal = input.responseOrdinal,
        cycleOrdinal = input.cycleOrdinal,
        turnOrdinal = input.turnOrdinal,
        messageMarkdown = requiredString("messageMarkdown"),
        solutionRevealed = requiredBoolean("solutionRevealed"),
        suggestedMoves = suggestedMoves,
        intentDecision = intentDecision,
        thinkingMarkdown = optionalString("thinkingMarkdown"),
        attachedImages = optionalArray("attachedImages").map(JsonElement::toAttachedImage),
        boundQuestion = boundQuestion,
        modelVersion = modelVersion,
    )
}

internal val TUTOR_TOOL_REQUESTS_WIRE_KEYS = setOf("intentDecision", "toolRequests")
private val TUTOR_TOOL_CALL_WIRE_KEYS =
    setOf(
        "tool",
        "terms",
        "rationale",
        "direction",
        "understanding",
        "difficultyTier",
        "confidence",
        "extendedResult",
        // 写工具的本次调用锚在哪道题（problemId + problemRevisionId + anchorTerms）。
        // 三个键与解析出的声明字段同名，便于两条路由（本信封 / 原生 tool_calls arguments）共用。
        "problemId",
        "problemRevisionId",
        "anchorTerms",
        // D-M M7 咨询工具的作用域/档位/正文（与原生 tool_calls arguments 同名同义）。
        "advisoryScope",
        "advisoryKind",
        "payloadMarkdown",
        // 4B A1 生图工具的图种类与画面说明（wire 键名 kind/description，与原生 tool_calls
        // arguments 及 Route A 的 function schema 逐字一致）。
        "kind",
        "description",
    )

internal fun JsonObject.toTutorToolRequests(
    modelVersion: String,
): TutorToolRequestsOutput {
    requireOnlyKeys(TUTOR_TOOL_REQUESTS_WIRE_KEYS)
    return TutorToolRequestsOutput(
        intentDecision = objectValue("intentDecision").toTutorIntentDecision(),
        calls = optionalArray("toolRequests")
            ?.mapIndexed { index, element ->
                val call = element.objectValue()
                call.requireOnlyKeys(TUTOR_TOOL_CALL_WIRE_KEYS)
                TutorToolCall(
                    tool = enumValue(call.requiredString("tool")),
                    rationale = call.requiredString("rationale"),
                    terms = call.optionalArray("terms").map(JsonElement::requiredPrimitiveString),
                    direction = call.optionalString("direction")?.let { enumValue<TutorEvidenceDirection>(it) },
                    understanding = call.optionalString("understanding")?.let { enumValue<TutorUnderstandingTier>(it) },
                    difficultyTier = call.optionalString("difficultyTier")?.let { enumValue<TutorDifficultyTier>(it) },
                    confidence = call.optionalDouble("confidence") ?: 0.8,
                    extendedResult = call.optionalBoolean("extendedResult") ?: false,
                    boundQuestion = call.toCallBoundQuestion(),
                    advisoryScope = call.optionalString("advisoryScope")
                        ?.let { enumValue<TutorAdvisoryScope>(it) },
                    advisoryKind = call.optionalString("advisoryKind")
                        ?.let { enumValue<TutorAdvisoryKind>(it) },
                    payloadMarkdown = call.optionalString("payloadMarkdown"),
                    // 4B A1：与原生 tool_calls arguments 同名同义（同一份 TutorToolCall
                    // 契约校验兜底：缺 kind/超长 description/带 terms 都在构造层拒）。
                    figureKind = call.optionalString("kind")?.let { enumValue<TutorFigureKind>(it) },
                    figureDescription = call.optionalString("description"),
                )
            }
            ?: throw InvalidModelResponseException(),
        modelVersion = modelVersion,
    )
}

internal fun JsonObject.toTutorLobby(
    input: TutorLobbyInput,
    modelVersion: String,
): TutorLobbyOutput {
    requireOnlyKeys(TUTOR_LOBBY_WIRE_KEYS)
    return TutorLobbyOutput(
        conversationId = input.conversationId,
        messageOrdinal = input.messageOrdinal,
        messageMarkdown = requiredString("messageMarkdown"),
        intentDecision = objectValue("intentDecision").toTutorIntentDecision(),
        thinkingMarkdown = optionalString("thinkingMarkdown"),
        // A6：大厅输出的 attachedImages 解析已删（死分支：解析后 `TutorLobbyRoute` 零引用、
        // 从不渲染）。wire 键也已从白名单移除——模型再吐这个键就是**未知键**，整条输出按
        // fail-closed 拒（与"未知键一律拒"同一条纪律，不是"忽略一下"）。
        // Respond/Plan 的 attachedImages 是另一回事：它们是无工具端点时的兼容 fallback，仍解析仍渲染。
        localActions = optionalArray("localActions").map(JsonElement::toLocalActionRequest),
        modelVersion = modelVersion,
    )
}

/**
 * 一条本地动作请求的解析（**模型只能选不能造**）。
 *
 * 消灭的具体失败：动作 id 与参数此前没有任何形状核对——一个"看着像"的动作会被当成未知动作
 * 静默放过，或者带着一堆本地不认识的键走到落库口。这里的规则是**逐字**的：
 *
 * - `action` 必须是白名单里**逐字相同**的 id（大小写 / 别名一律无效）；
 * - 参数键必须在该动作**声明过的参数形状**里（本版四个动作的形状都是空集，所以任何参数键都是
 *   无效输出）；值必须是非空字符串；
 * - 声明为必填的参数不能缺。
 *
 * 任何一条不满足 → [InvalidModelResponseException]：这不是"忽略掉多余信息"，这是**这条输出没有
 * 按契约说话**——与工具轮 `requireOnlyKeys` 同一口径（宁可让这一轮失败重来，也不让一条形状不对的
 * 请求走到"挂卡 / 执行"那一步）。
 */
internal fun JsonElement.toLocalActionRequest(): TutorLocalActionRequest {
    val element = objectValue()
    val actionId = element.requiredString(LOCAL_ACTION_WIRE_ACTION_KEY)
    val parameters = JsonObject(element.filterKeys { key -> key != LOCAL_ACTION_WIRE_ACTION_KEY })
    return localActionRequest(actionId = actionId, arguments = parameters)
}

/**
 * 动作 id + 参数对象 → 动作请求；两条路由**共用这一处形状核对**（Route B 的 `action` 键、
 * Route A 的原生函数名），免得"合法的动作请求"有两套口径。
 */
internal fun localActionRequest(
    actionId: String,
    arguments: JsonObject,
): TutorLocalActionRequest {
    val action = TutorLocalAction.fromActionId(actionId)
        ?: throw InvalidModelResponseException()
    val declared = action.parameters
    val declaredNames = declared.map { parameter -> parameter.parameterName }.toSet()
    val parameters = buildMap {
        arguments.forEach { (key, value) ->
            if (key !in declaredNames) throw InvalidModelResponseException()
            put(key, value.requiredPrimitiveString())
        }
    }
    declared.filter { parameter -> parameter.required }.forEach { parameter ->
        if (parameters[parameter.parameterName].isNullOrBlank()) {
            throw InvalidModelResponseException()
        }
    }
    return TutorLocalActionRequest(action = action, parameters = parameters)
}

/** 动作请求里的动作 id 键；其余键一律来自该动作声明的参数形状。 */
internal const val LOCAL_ACTION_WIRE_ACTION_KEY = "action"

internal fun JsonObject.toTutorIntentDecision(): TutorIntentDecision {
    requireOnlyKeys(TUTOR_INTENT_WIRE_KEYS)
    return TutorIntentDecision(
        intent = enumValue(requiredString("intent")),
        confidence = optionalDouble("confidence") ?: throw InvalidModelResponseException(),
        explicitActionRequest = requiredBoolean("explicitActionRequest"),
        memoryPreference = enumValue(requiredString("memoryPreference")),
        requestedLocalCapability = enumValue(requiredString("requestedLocalCapability")),
        lookupTerms = optionalArray("lookupTerms").map(JsonElement::requiredPrimitiveString),
    )
}


internal val TUTOR_DEBRIEF_WIRE_KEYS = setOf(
    "misconceptionMarkdown",
    "teachingFocusLabels",
)
internal val TUTOR_PLAN_WIRE_KEYS = setOf(
    "openingMarkdown",
    "diagnosticQuestion",
    "solutionMarkdown",
    "alternateMethodMarkdown",
    "difficultyReasonMarkdown",
    "targetedEvidenceLabels",
    "inferredKnowledgeLabels",
    "nextMoves",
    "thinkingMarkdown",
    "attachedImages",
)
internal val TUTOR_RESPOND_WIRE_KEYS =
    setOf(
        "intentDecision",
        "messageMarkdown",
        "solutionRevealed",
        "nextMoves",
        "thinkingMarkdown",
        "attachedImages",
        // 本轮"在说哪一道题"的声明（schema 11）。白名单是**逐字段**的：模型多写一个键就整条
        // 响应无效，所以新声明必须在这里登记，否则合法的声明也会被判成无效响应。
        "boundQuestion",
    )
internal val TUTOR_BOUND_QUESTION_WIRE_KEYS =
    setOf("problemId", "problemRevisionId", "anchorTerms")

/**
 * 从**扁平字段**里读出这一对象声明的题锚（`problemId` + `problemRevisionId` + 可选 `anchorTerms`）。
 *
 * 两条路由共用：json_object 信封里是调用对象上的三个键，原生 `tool_calls` 里是 arguments 这个
 * 扁平 JSON 的同样三个键。缺 `problemId` 或 `problemRevisionId` 就当作**没有声明**（null）——
 * 半个锚定位不到确定题面，等于没有；"要不要信"由本地两条校验决定，不在这里。
 */
internal fun JsonObject.toCallBoundQuestion(): TutorRoundQuestionDeclaration? {
    val problemId = optionalString("problemId") ?: return null
    val problemRevisionId = optionalString("problemRevisionId") ?: return null
    return TutorRoundQuestionDeclaration(
        problemId = problemId,
        problemRevisionId = problemRevisionId,
        anchorTerms = optionalArray("anchorTerms").map(JsonElement::requiredPrimitiveString),
    )
}

/** 声明字段的**形状**解析（白名单收口）；"要不要信"由本地校验决定，不在这里。 */
internal fun JsonObject.toBoundQuestionDeclaration(): TutorRoundQuestionDeclaration {
    requireOnlyKeys(TUTOR_BOUND_QUESTION_WIRE_KEYS)
    return TutorRoundQuestionDeclaration(
        problemId = requiredString("problemId"),
        problemRevisionId = requiredString("problemRevisionId"),
        anchorTerms = optionalArray("anchorTerms").map(JsonElement::requiredPrimitiveString),
    )
}
/**
 * 大厅输出信封的键白名单（A6 起不再含 `attachedImages`）：大厅从不渲染模型申请的配图，
 * 那条解析是死分支（`TutorLobbyRoute` 对 `AttachedImage` 零引用），且会把"本地不消费的
 * 模型输入"静默放进来。移除后模型吐 `attachedImages` 即未知键 → 整条输出无效（fail-closed）。
 */
internal val TUTOR_LOBBY_WIRE_KEYS =
    setOf("intentDecision", "messageMarkdown", "thinkingMarkdown", "localActions")
internal val ATTACHED_IMAGE_WIRE_KEYS =
    setOf("imageId", "kind", "description", "accessibilityText")

/** Parses one model-authored [AttachedImage] intent from its wire object. */
internal fun JsonElement.toAttachedImage(): AttachedImage {
    val obj = objectValue()
    obj.requireOnlyKeys(ATTACHED_IMAGE_WIRE_KEYS)
    val kind = enumValue<AttachedImageKind>(obj.requiredString("kind"))
    return AttachedImage(
        imageId = obj.requiredString("imageId"),
        kind = kind,
        description = obj.requiredString("description"),
        accessibilityText = obj.optionalString("accessibilityText").orEmpty(),
    )
}
internal val TUTOR_INTENT_WIRE_KEYS = setOf(
    "intent",
    "confidence",
    "explicitActionRequest",
    "memoryPreference",
    "requestedLocalCapability",
    "lookupTerms",
)
internal val TUTOR_DIAGNOSTIC_WIRE_KEYS = setOf("stemMarkdown", "promptMarkdown", "choices")
internal val TUTOR_DIAGNOSTIC_CHOICE_WIRE_KEYS =
    setOf("markdown", "feedbackMarkdown", "isCorrect")
internal val TUTOR_MOVE_WIRE_KEYS = setOf("label", "type")

internal fun JsonObject.toTutorDebrief(
    input: TutorDebriefInput,
    modelVersion: String,
): TutorDebriefOutput {
    requireOnlyKeys(TUTOR_DEBRIEF_WIRE_KEYS)
    val misconception = optionalString("misconceptionMarkdown")
    val focusLabels = optionalArray("teachingFocusLabels")
        .mapNotNull { element -> (element as? kotlinx.serialization.json.JsonPrimitive)?.content }
    return TutorDebriefOutput(
        sessionId = input.sessionId,
        practiceUnitId = input.practiceUnitId,
        misconceptionMarkdown = misconception,
        teachingFocusLabels = focusLabels,
        modelVersion = modelVersion,
    )
}
