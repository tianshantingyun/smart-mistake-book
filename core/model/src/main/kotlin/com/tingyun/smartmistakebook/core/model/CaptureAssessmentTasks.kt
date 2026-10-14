package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class CaptureAssessmentDecision {
    PASS,
    RECAPTURE,
    NEED_MORE_IMAGE,
    SPLIT,
}

@Serializable
enum class CaptureAssessmentIssueCode {
    MISSING_OPTIONS,
    KEY_TEXT_UNREADABLE,
    GLARE_COVERS_FORMULA,
    OCCLUDED,
    MULTIPLE_QUESTIONS,
}

@Serializable
enum class CaptureAssessmentSeverity {
    BLOCKING,
    REVIEW,
}

@Serializable
enum class CaptureAssessmentAction {
    RECAPTURE,
    ADD_IMAGE,
    CONTINUE_ANYWAY,
}

@Serializable
enum class CapturePageRelation {
    SAME_QUESTION,
    NEXT_QUESTION,
    UNSURE,
}

@Serializable
data class CaptureAssessmentIssue(
    val code: CaptureAssessmentIssueCode,
    val severity: CaptureAssessmentSeverity,
    val region: NormalizedSourceRegion? = null,
    val message: String,
) {
    init {
        message.requireSafeModelText(
            label = "Capture assessment issue message",
            maxChars = MAX_MESSAGE_CHARS,
            allowLineBreaks = true,
        )
        require(region == null || region.isValidModelRegion()) {
            "Capture assessment issue region is invalid"
        }
    }
}

@Serializable
data class CaptureAssessment(
    val decision: CaptureAssessmentDecision,
    val issues: List<CaptureAssessmentIssue>,
    val suggestedActions: List<CaptureAssessmentAction>,
    val modelVersion: String,
    val questionRegions: List<NormalizedSourceRegion> = emptyList(),
    val followingPageRelations: List<CapturePageRelation> = emptyList(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    init {
        require(schemaVersion in MIN_SUPPORTED_SCHEMA_VERSION..CURRENT_SCHEMA_VERSION) {
            "Unsupported capture assessment schema"
        }
        require(issues.size <= MAX_ISSUES) { "Capture assessment has too many issues" }
        require(suggestedActions.size <= MAX_ACTIONS) {
            "Capture assessment has too many actions"
        }
        require(suggestedActions.distinct().size == suggestedActions.size) {
            "Capture assessment actions must be unique"
        }
        modelVersion.requireSafeModelText(
            label = "Capture assessment model version",
            maxChars = MAX_MODEL_VERSION_CHARS,
            allowLineBreaks = false,
        )
        require(
            decision == CaptureAssessmentDecision.PASS || issues.isNotEmpty(),
        ) { "A blocked capture assessment must explain at least one issue" }
        require(
            decision != CaptureAssessmentDecision.PASS ||
                issues.none { it.severity == CaptureAssessmentSeverity.BLOCKING },
        ) { "A passing capture assessment cannot contain blocking issues" }
        require(
            decision != CaptureAssessmentDecision.PASS ||
                CaptureAssessmentAction.RECAPTURE !in suggestedActions &&
                CaptureAssessmentAction.ADD_IMAGE !in suggestedActions,
        ) { "A passing capture assessment cannot request another image" }
        require(
            decision != CaptureAssessmentDecision.RECAPTURE ||
                CaptureAssessmentAction.RECAPTURE in suggestedActions,
        ) { "A recapture decision must suggest recapturing" }
        require(
            decision != CaptureAssessmentDecision.NEED_MORE_IMAGE ||
                CaptureAssessmentAction.ADD_IMAGE in suggestedActions,
        ) { "A missing-image decision must suggest adding an image" }
        val multipleQuestionIssues = issues.filter {
            it.code == CaptureAssessmentIssueCode.MULTIPLE_QUESTIONS
        }
        // KD-34 未闭合项：下面那组"多道独立题 ⟺ SPLIT / 非 SPLIT ⟹ 无区域"的耦合是**单页语义**
        // ——"这页有几道独立题 → 走本地分题流"。`followingPageRelations` 非空 = 这是一次**多页
        // 关系轮**（A1 多页变体的合法形状），它的消费方只读 decision 与 followingPageRelations
        // （RoomBatchImportRepository 的边界评估），区域规则早已下移到切分消费侧（A2）。模型在
        // 关系轮里顺带报 MULTIPLE_QUESTIONS + RECAPTURE、或带上整册 questionRegions，不该在这里
        // 把整轮打死——那会让边界评估落 INVALID_RESPONSE（且 requestId 确定性 ⇒ 永不重派发），
        // 连 followingPageRelations 一起丢掉。
        //
        // 单页轮的 followingPageRelations 必须为空（任务级完成校验按
        // `followingSourceAssets.size == followingPageRelations.size` 兜底，
        // ModelTaskCompletionValidator），所以这两条耦合在单页语义上逐字保留、不放宽。
        val isPageRelationRound = followingPageRelations.isNotEmpty()
        if (schemaVersion >= MULTIPLE_QUESTION_SPLIT_SCHEMA_VERSION) {
            require(multipleQuestionIssues.all { it.severity == CaptureAssessmentSeverity.BLOCKING }) {
                "Multiple independent questions must be treated as blocking"
            }
            if (!isPageRelationRound) {
                require(
                    multipleQuestionIssues.isEmpty() || decision == CaptureAssessmentDecision.SPLIT,
                ) { "Multiple independent questions must enter the local split flow" }
                require(
                    decision != CaptureAssessmentDecision.SPLIT || multipleQuestionIssues.isNotEmpty(),
                ) { "A split decision must identify multiple independent questions" }
                // 分题区域能不能真的切（2 到 12 个、唯一、最小尺寸、两两重叠上限）是切分消费侧
                // 的判据：手动框选与自动拆分都经 core:domain 的 SplitCaptureDraftRequest 契约，
                // 判据本身是 core:domain 的 isUsableSplitRegionSet。本契约只保留 wire 形状与
                // decision 耦合——多页关系请求里模型顺带返回的整册 questionRegions 不该在这里被拒，
                // 那会让整轮任务落 INVALID_RESPONSE，连 followingPageRelations 一起丢掉。
                require(decision == CaptureAssessmentDecision.SPLIT || questionRegions.isEmpty()) {
                    "Only a split decision may contain question regions"
                }
            }
        } else if (schemaVersion >= MULTIPLE_QUESTION_GATE_SCHEMA_VERSION) {
            require(decision != CaptureAssessmentDecision.SPLIT && questionRegions.isEmpty()) {
                "Legacy capture assessments cannot request local splitting"
            }
            require(multipleQuestionIssues.all { it.severity == CaptureAssessmentSeverity.BLOCKING }) {
                "Multiple independent questions must be treated as blocking"
            }
            require(
                multipleQuestionIssues.isEmpty() || decision == CaptureAssessmentDecision.RECAPTURE,
            ) { "A legacy single-question capture cannot continue with multiple questions" }
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
        private const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        private const val MULTIPLE_QUESTION_GATE_SCHEMA_VERSION = 2
        private const val MULTIPLE_QUESTION_SPLIT_SCHEMA_VERSION = 3
        const val MAX_ISSUES = 12
        const val MAX_ACTIONS = 3
    }
}
