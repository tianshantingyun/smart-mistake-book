package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.Serializable

/** Where a provider executes. External providers always require an exact disclosure manifest. */
@Serializable
enum class ModelExecutionLocation {
    LOCAL_NO_EGRESS,
    EXTERNAL_PROVIDER,
    UNAVAILABLE,
}

@Serializable
enum class ModelEgressPurpose {
    CAPTURE_TO_DOCUMENT,
    TUTORING,
    CLASSIFICATION,
}

@Serializable
enum class ModelEgressDataClass {
    SANITIZED_IMAGE_BYTES,
    IMAGE_DIMENSIONS,
    SELECTED_IMAGE_REGION,
    CONFIRMED_QUESTION_DOCUMENT,
    RELEVANT_LEARNING_EVIDENCE,
    QUESTION_LEARNING_EVIDENCE,
    RELATED_QUESTION_CANDIDATES,
    SUBJECT_KNOWLEDGE_BASE,
    OTHER_CAPTURE_ASSETS,
    FULL_LEARNING_HISTORY,
    API_CREDENTIALS,
    CAPTURE_METADATA,
    STUDENT_TUTOR_MESSAGE,
    TUTOR_CONVERSATION_CONTEXT,
    MODEL_AUTHORED_VISUAL_CANDIDATE,
}

/** One source of truth for the prompt whose exact scope the student approved. */
object ModelPromptPolicyVersions {
    const val CAPTURE_DOCUMENT = "capture-document-policy-v1"
    /**
     * v13：单一代号通道映射表 + Plan 工具环 + 教学材料加载失败披露（ADR 0001 / D5-D8）。
     * v14（D-M M7）：工具面新增两枚咨询工具（ADVISORY_READ/ADVISORY_WRITE），其描述随声明进提示词。
     */
    const val TUTOR_PLAN = "tutor-plan-v14-advisory-tools"
    /**
     * v20：写工具改收代号（原始 id 退出提示词）+ 代号映射表 + 无题轮不结构性拒写（D5/D6）。
     * v21（D-M M7）：工具面新增两枚咨询工具（描述与写工具段），代号表显式含本科「未分类」兜底桶。
     */
    const val TUTOR_RESPOND = "tutor-respond-v21-advisory-tools"
    /**
     * v8：工具面教学口径随 D6/D7 更新（写不写由模型语义判定；代号用法）。
     * v10（A4）：允许申请 OFFER_SAVE_CURRENT_QUESTION（本地渲染确认卡、学生点了才执行），
     * 并回喂上一轮确认卡的裁决结果。v9 及更早的轮次没有这两段文本。
     * v11（批次 0 条目 5b）：大厅提示词加止血行——本轮没有科目上下文时不要申请
     * KNOWLEDGE_READ / MASTERY_READ（没有科目范围，只会拿到空结果）。v10 及更早的轮次没有这段文本。
     * v12（D-M M7）：大厅声明面新增两枚咨询工具，止血行覆盖它们（大厅没有科目/会话范围）。
     */
    const val TUTOR_LOBBY = "tutor-lobby-v12-advisory-tools"
    const val LEARNING_SUMMARIZE = "learning-summarize-v1-tutor-debrief"
    const val PROBLEM_ORGANIZATION = "problem-organization-v4-atomic"
    const val KNOWLEDGE_QUIZ = "knowledge-quiz-v1-boundary-anchored"

    fun currentFor(kind: ModelTaskKind): String? = when (kind) {
        ModelTaskKind.CAPTURE_ASSESS,
        ModelTaskKind.CAPTURE_PARSE,
        ModelTaskKind.IMAGE_PIPELINE_CLASSIFY,
        -> CAPTURE_DOCUMENT
        ModelTaskKind.TUTOR_PLAN -> TUTOR_PLAN
        ModelTaskKind.TUTOR_RESPOND -> TUTOR_RESPOND
        ModelTaskKind.TUTOR_LOBBY -> TUTOR_LOBBY
        ModelTaskKind.LEARNING_SUMMARIZE -> LEARNING_SUMMARIZE
        ModelTaskKind.PROBLEM_CLASSIFY -> PROBLEM_ORGANIZATION
        ModelTaskKind.KNOWLEDGE_QUIZ -> KNOWLEDGE_QUIZ
        ModelTaskKind.PROBLEM_RELATE,
        ModelTaskKind.TUTOR_EVALUATE,
        ModelTaskKind.REVIEW_RERANK,
        -> null
    }
}

/**
 * 讲题轮次出网披露的**唯一**计算口径：按运行时状态选择，不按 kind 分叉猜。
 *
 * 生产里只有**两态**（`docs/tutor-surface-unification.md` §5.5 与 §6 障碍 3），函数也就只表达
 * 两态，两个具名入口各自对应一个真实存在的轮次：
 * - [noQuestionRound]：无题轮（大厅）。学生消息 + 会话上下文，**不含题面**；附图消息追加图片类目。
 * - [questionRound]：有题轮（Respond）。题面、学习证据、会话上下文、学科知识库；本轮真的带了
 *   候选菜单时再追加 [ModelEgressDataClass.RELATED_QUESTION_CANDIDATES]。
 *
 * 这里此前还有一个"有题带图"的第三态（`carriesQuestion = true, includesImage = true`），它在
 * **三个校验调用点都不可达**：`ModelEgressManifest` 的 init 里两个标志由**同一个** kind 派生
 * （`carriesQuestion` 要 TUTOR_RESPOND、`includesImage` 要 TUTOR_LOBBY，互斥），`requireAuthorizes`
 * 的 Respond 分支硬编码 `includesImage = false` 且 `assets` 必须为空，Lobby 分支
 * `carriesQuestion = false`。一个表达不出来的状态留在签名里，只会让人以为它已经接线（甚至据此
 * 怀疑那条既有断言过时），所以删掉分支、把两个真实状态拆成两个具名函数：不合法的组合从此
 * **构造不出来**，而不是靠注释提醒。
 *
 * 〔D-K4 补正〕清单路径上的逐 kind 核对（含"Respond 清单不得带图字节"）已随 7 个不可达分支持
 * 删除：真正的边界在 **init**（披露集合必须精确等于本 kind 的口径）与**资产源逐字节核对**
 * （`AndroidRestrictedModelAssetSource`）。图字节能不能出去由"请求侧真的带了哪个资产 +
 * provider 支持图片输入 + 该资产在清单 grant 内且内容未变"三件事共同决定，不再由 kind 分支复述。
 * 两条分档仍按 schema 走（schema<4 的 Respond 行当年没有 SUBJECT_KNOWLEDGE_BASE；schema<6 的
 * 大厅行不能带图）：旧行 decode 时会重跑这条校验，漏掉 legacy 分档就会让升级后的旧行直接抛
 * 异常（bf8be888 的同一类事故）。
 */
object TutorRoundDisclosure {
    /**
     * 无题轮（大厅）：纯文本时是最小披露，附图消息按 schema 六追加图片类目。
     *
     * 没有"候选菜单"这一维：`TutorLobbyInput` 根本没有菜单字段，无题轮不存在"菜单里的别的题"。
     */
    fun noQuestionRound(
        includesImage: Boolean,
        schemaVersion: Int = ModelEgressManifest.CURRENT_SCHEMA_VERSION,
    ): Set<ModelEgressDataClass> = ModelEgressManifest.tutorLobbyDisclosureForSchema(
        schemaVersion = schemaVersion,
        includesImage = includesImage,
    )

    /**
     * 有题轮（Respond）：题面 / 学习证据 / 学科知识库，带候选菜单时追加菜单类目。
     *
     * 没有"图片"这一维：生产里题轮**不带清单**（agent-eligible，走全局同意通道；见
     * `TutorModelTaskPolicy` 的 `egressManifest = null`），所以没有"带不带图"可选。
     */
    fun questionRound(
        includesQuestionCandidates: Boolean,
        schemaVersion: Int = ModelEgressManifest.CURRENT_SCHEMA_VERSION,
    ): Set<ModelEgressDataClass> = buildSet {
        addAll(ModelEgressManifest.tutorRespondDisclosureForSchema(schemaVersion))
        if (includesQuestionCandidates) {
            add(ModelEgressDataClass.RELATED_QUESTION_CANDIDATES)
        }
    }
}

/**
 * 本轮披露集合是否覆盖**候选菜单**（错题本里别的题面，`RELATED_QUESTION_CANDIDATES`）。
 *
 * 它回答的是"这一轮的披露面装不装得下别的题的可识别内容"，取的是同一个请求侧事实：
 * 本轮**真的**带了菜单（`TutorRespondInput.boundQuestionCandidates` 非空）才算覆盖。
 * 无题轮没有菜单字段，一票否决。
 *
 * 谁在消费它：
 * - 清单侧：大厅分支仍按它核对 `includesQuestionCandidates`（无题轮没有菜单字段，"清单说覆盖了"
 *   就是多报）；
 * - 产出侧：`core:data` 的 `NOTEBOOK_READ` 按它决定结果形态——披露面覆盖菜单时才逐条点名别的题，
 *   否则只给条数与检索词（错题本条目标题属于这一类的可识别内容）。
 *
 * 披露粒度是**类目**：清单声明的是"这一类出网了"，逐条对齐只有图片资产的 grant 做得到。所以
 * 判据取"本轮披露集合覆盖了这一类"，而不是"这条标题恰好是菜单里的某一条"。
 */
fun ModelTaskInput.disclosesQuestionCandidates(): Boolean =
    this is TutorRespondInput && boundQuestionCandidates.isNotEmpty()

@Serializable
data class ModelEgressAssetGrant(
    val assetId: String,
    val sha256: String,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    /** Null means the student approved the whole canonical image. */
    val selectedRegion: NormalizedSourceRegion? = null,
) {
    init {
        assetId.requireSafeModelText("Egress asset id", ModelTaskRequest.MAX_ID_CHARS, false)
        require(sha256.length == SHA_256_HEX_CHARS && sha256.all(Char::isLowerHexDigit)) {
            "Egress asset hash must be a lowercase SHA-256 value"
        }
        require(byteSize in 1L..MODEL_EGRESS_MAX_ASSET_BYTES) {
            "Egress asset byte size is outside the supported range"
        }
        require(width in 1..MAX_CAPTURE_SOURCE_DIMENSION) {
            "Egress asset width is outside the supported range"
        }
        require(height in 1..MAX_CAPTURE_SOURCE_DIMENSION) {
            "Egress asset height is outside the supported range"
        }
        require(width.toLong() * height <= MAX_CAPTURE_SOURCE_PIXELS) {
            "Egress asset exceeds the pixel budget"
        }
        require(selectedRegion == null || selectedRegion.isValidModelRegion()) {
            "Egress asset selected region is invalid"
        }
    }
}

/**
 * Immutable proof of what the student approved for one exact capture and provider configuration.
 * It intentionally contains no URI, local path, API key, question history, or free-form prompt.
 *
 * 只声明**已披露**什么（[disclosedData]，精确等于本轮口径）。此前这里还有一个"未披露集"
 * `prohibitedData` 字段：它由 `schemaVersion` 与 [disclosedData] 唯一决定（全集 − 已披露），
 * 生产**读取 0 处**、没有任何决策消费它（研究报告 §4.6 R6），D-K4 删除。旧行仍带着那个键，
 * 读回路径见 [ModelTaskCodec.decodeRequest] 的 strip；旧行的存库指纹复原见
 * `ModelTasks.kt` 的 `withLegacyEgressProhibitedData`。
 */
@Serializable
data class ModelEgressManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val authorizationId: String,
    val subjectId: String,
    val purpose: ModelEgressPurpose,
    val authorizedTaskKinds: Set<ModelTaskKind>,
    val providerId: String,
    val modelId: String,
    val providerConfigurationVersion: String,
    val promptPolicyVersion: String,
    val approvedAtEpochMillis: Long,
    val assets: List<ModelEgressAssetGrant>,
    val disclosedData: Set<ModelEgressDataClass>,
    /**
     * 本次授权是否覆盖**本轮候选菜单**（错题本里别的题面）。它是请求侧的事实，不是策略选择：
     * `TutorRespondInput.boundQuestionCandidates` 非空就必须为 true，否则披露集合会少报一个
     * 真实出网的类目（`requireAuthorizes` 会逐次核对这张清单与请求是否一致）。
     */
    val includesQuestionCandidates: Boolean = false,
) {
    init {
        require(schemaVersion in MIN_SUPPORTED_SCHEMA_VERSION..CURRENT_SCHEMA_VERSION) {
            "Unsupported egress manifest schema"
        }
        require(schemaVersion >= QUESTION_CANDIDATE_SCHEMA_VERSION || !includesQuestionCandidates) {
            "Legacy egress manifests cannot cover a bound-question candidate menu"
        }
        authorizationId.requireSafeModelText(
            "Egress authorization id",
            ModelTaskRequest.MAX_ID_CHARS,
            false,
        )
        subjectId.requireSafeModelText("Egress subject id", ModelTaskRequest.MAX_ID_CHARS, false)
        providerId.requireSafeModelText("Egress provider id", MAX_PROVIDER_ID_CHARS, false)
        modelId.requireSafeModelText("Egress model id", MAX_MODEL_VERSION_CHARS, false)
        providerConfigurationVersion.requireSafeModelText(
            "Egress provider configuration version",
            MAX_MODEL_VERSION_CHARS,
            false,
        )
        promptPolicyVersion.requireSafeModelText(
            "Egress prompt policy version",
            MAX_MODEL_VERSION_CHARS,
            false,
        )
        require(approvedAtEpochMillis >= 0) { "Egress approval time must not be negative" }
        require(authorizedTaskKinds.isNotEmpty()) { "Egress task scope must not be empty" }
        require(assets.size <= MAX_CAPTURE_SOURCE_ASSETS) { "Egress asset scope exceeds budget" }
        require(assets.map(ModelEgressAssetGrant::assetId).distinct().size == assets.size) {
            "Egress asset ids must be unique"
        }
        require(disclosedData.isNotEmpty()) { "Egress disclosure set must not be empty" }
        require(disclosedData.all { it in dataClassUniverseForSchema(schemaVersion) }) {
            "Egress manifest references a data class outside its schema"
        }
        require(
            purpose == ModelEgressPurpose.CAPTURE_TO_DOCUMENT ||
                purpose == ModelEgressPurpose.TUTORING ||
                purpose == ModelEgressPurpose.CLASSIFICATION,
        ) { "This egress purpose has no implemented least-disclosure policy" }
        if (purpose == ModelEgressPurpose.CAPTURE_TO_DOCUMENT) {
            require(assets.isNotEmpty()) { "Capture egress asset scope must not be empty" }
            val expectedDisclosure = CAPTURE_IMAGE_DISCLOSURE + if (
                assets.any { asset -> asset.selectedRegion != null }
            ) {
                setOf(ModelEgressDataClass.SELECTED_IMAGE_REGION)
            } else {
                emptySet()
            }
            require(
                authorizedTaskKinds == setOf(
                    ModelTaskKind.CAPTURE_ASSESS,
                    ModelTaskKind.CAPTURE_PARSE,
                ),
            ) { "Capture egress must be limited to assessment and document parsing" }
            require(disclosedData == expectedDisclosure) {
                "Capture egress disclosure must match the exact approved image scope"
            }
        }
        if (purpose == ModelEgressPurpose.TUTORING) {
            val tutoringKind = authorizedTaskKinds.singleOrNull()
            require(
                tutoringKind == ModelTaskKind.TUTOR_PLAN ||
                    schemaVersion >= 2 && tutoringKind == ModelTaskKind.TUTOR_RESPOND ||
                    schemaVersion >= 4 && tutoringKind == ModelTaskKind.TUTOR_LOBBY ||
                    schemaVersion >= 5 && tutoringKind == ModelTaskKind.KNOWLEDGE_QUIZ,
            ) {
                "Tutor egress must authorize exactly one supported tutoring task"
            }
            // 讲题轮次的披露**只**走这一条显式口径：按"本轮有没有题 / 有没有图 / 有没有候选菜单"
            // 运行时选择，而不是按 kind 分叉各猜一遍。大厅＝无题轮，Respond＝有题轮；大厅附图追加
            // 图片类目，题轮带候选菜单追加该菜单的类目。旧 schema 行（无候选菜单这一维）读回时
            // includesQuestionCandidates 为 false，取值与改前逐字一致。
            val expectedDisclosure = when (tutoringKind) {
                ModelTaskKind.TUTOR_RESPOND -> TutorRoundDisclosure.questionRound(
                    includesQuestionCandidates = includesQuestionCandidates,
                    schemaVersion = schemaVersion,
                )
                ModelTaskKind.TUTOR_LOBBY -> TutorRoundDisclosure.noQuestionRound(
                    includesImage = assets.isNotEmpty(),
                    schemaVersion = schemaVersion,
                )
                ModelTaskKind.TUTOR_PLAN -> tutorPlanDisclosureForSchema(schemaVersion)
                ModelTaskKind.KNOWLEDGE_QUIZ -> KNOWLEDGE_QUIZ_DISCLOSURE
            }
            if (tutoringKind == ModelTaskKind.TUTOR_LOBBY && assets.isNotEmpty()) {
                require(schemaVersion >= LOBBY_IMAGE_SCHEMA_VERSION) {
                    "Tutor lobby image egress requires egress manifest schema six"
                }
                require(assets.size <= MAX_LOBBY_IMAGE_ASSETS) {
                    "Tutor lobby image scope exceeds the message budget"
                }
            }
            // Lobby：无图时纯文本最小披露；有图时按 schema 六的图片披露集合。
            require(disclosedData == expectedDisclosure) {
                "Tutor egress disclosure must exactly match the authorized tutoring task"
            }
        }
        if (purpose == ModelEgressPurpose.CLASSIFICATION) {
            require(authorizedTaskKinds == setOf(ModelTaskKind.PROBLEM_CLASSIFY)) {
                "Classification egress must be limited to organizing one confirmed revision"
            }
            require(assets.isEmpty()) {
                "Problem organization must use confirmed documents, not image bytes"
            }
            require(disclosedData == PROBLEM_ORGANIZATION_DISCLOSURE) {
                "Classification egress disclosure must match the bounded organization context"
            }
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 7
        private const val MIN_SUPPORTED_SCHEMA_VERSION = 1

        /** schema 6 起：学生一次性说明后，Lobby 可携带学生选择的消息配图。 */
        internal const val LOBBY_IMAGE_SCHEMA_VERSION = 6

        /** schema 7 起：授权清单可以覆盖本轮候选菜单（`includesQuestionCandidates`）。 */
        internal const val QUESTION_CANDIDATE_SCHEMA_VERSION = 7

        /** 一条消息最多附带的图片数（学生裁定）。 */
        const val MAX_LOBBY_IMAGE_ASSETS = 9

        val SCHEMA_V1_DATA_CLASSES = setOf(
            ModelEgressDataClass.SANITIZED_IMAGE_BYTES,
            ModelEgressDataClass.IMAGE_DIMENSIONS,
            ModelEgressDataClass.SELECTED_IMAGE_REGION,
            ModelEgressDataClass.CONFIRMED_QUESTION_DOCUMENT,
            ModelEgressDataClass.RELEVANT_LEARNING_EVIDENCE,
            ModelEgressDataClass.QUESTION_LEARNING_EVIDENCE,
            ModelEgressDataClass.RELATED_QUESTION_CANDIDATES,
            ModelEgressDataClass.OTHER_CAPTURE_ASSETS,
            ModelEgressDataClass.FULL_LEARNING_HISTORY,
            ModelEgressDataClass.API_CREDENTIALS,
            ModelEgressDataClass.CAPTURE_METADATA,
        )

        val CAPTURE_IMAGE_DISCLOSURE = setOf(
            ModelEgressDataClass.SANITIZED_IMAGE_BYTES,
            ModelEgressDataClass.IMAGE_DIMENSIONS,
        )

        val LEGACY_TUTOR_PLAN_DISCLOSURE = setOf(
            ModelEgressDataClass.CONFIRMED_QUESTION_DOCUMENT,
            ModelEgressDataClass.RELEVANT_LEARNING_EVIDENCE,
            ModelEgressDataClass.QUESTION_LEARNING_EVIDENCE,
        )

        private val SCHEMA_THREE_TUTOR_PLAN_DISCLOSURE = LEGACY_TUTOR_PLAN_DISCLOSURE + setOf(
            ModelEgressDataClass.STUDENT_TUTOR_MESSAGE,
            ModelEgressDataClass.TUTOR_CONVERSATION_CONTEXT,
        )

        val TUTOR_PLAN_DISCLOSURE = SCHEMA_THREE_TUTOR_PLAN_DISCLOSURE +
            ModelEgressDataClass.SUBJECT_KNOWLEDGE_BASE

        internal fun tutorPlanDisclosureForSchema(
            schemaVersion: Int,
        ): Set<ModelEgressDataClass> = when {
            schemaVersion >= 4 -> TUTOR_PLAN_DISCLOSURE
            schemaVersion >= 3 -> SCHEMA_THREE_TUTOR_PLAN_DISCLOSURE
            else -> LEGACY_TUTOR_PLAN_DISCLOSURE
        }

        private val LEGACY_TUTOR_RESPOND_DISCLOSURE = setOf(
            ModelEgressDataClass.CONFIRMED_QUESTION_DOCUMENT,
            ModelEgressDataClass.RELEVANT_LEARNING_EVIDENCE,
            ModelEgressDataClass.QUESTION_LEARNING_EVIDENCE,
            ModelEgressDataClass.STUDENT_TUTOR_MESSAGE,
            ModelEgressDataClass.TUTOR_CONVERSATION_CONTEXT,
        )

        val TUTOR_RESPOND_DISCLOSURE = LEGACY_TUTOR_RESPOND_DISCLOSURE +
            ModelEgressDataClass.SUBJECT_KNOWLEDGE_BASE

        /** Knowledge review quiz discloses only the bounded node material + prior mastery. */
        val KNOWLEDGE_QUIZ_DISCLOSURE = setOf(
            ModelEgressDataClass.SUBJECT_KNOWLEDGE_BASE,
            ModelEgressDataClass.RELEVANT_LEARNING_EVIDENCE,
        )

        internal fun tutorRespondDisclosureForSchema(
            schemaVersion: Int,
        ): Set<ModelEgressDataClass> = if (schemaVersion >= 4) {
            TUTOR_RESPOND_DISCLOSURE
        } else {
            LEGACY_TUTOR_RESPOND_DISCLOSURE
        }

        val TUTOR_LOBBY_DISCLOSURE = setOf(
            ModelEgressDataClass.STUDENT_TUTOR_MESSAGE,
            ModelEgressDataClass.TUTOR_CONVERSATION_CONTEXT,
        )

        /** 附图消息的披露：在纯文本范围上追加图片类目（首次一次性说明后长期有效）。 */
        val TUTOR_LOBBY_IMAGE_DISCLOSURE = TUTOR_LOBBY_DISCLOSURE + CAPTURE_IMAGE_DISCLOSURE

        /**
         * Lobby 披露按请求是否附图与 schema 版本选择：旧 schema 行读回时仍按纯文本
         * 集合校验（assets 必须为空），新 schema 才允许图片类目。
         */
        internal fun tutorLobbyDisclosureForSchema(
            schemaVersion: Int,
            includesImage: Boolean,
        ): Set<ModelEgressDataClass> =
            if (includesImage && schemaVersion >= LOBBY_IMAGE_SCHEMA_VERSION) {
                TUTOR_LOBBY_IMAGE_DISCLOSURE
            } else {
                TUTOR_LOBBY_DISCLOSURE
            }

        /**
         * 版本化宇宙：**只用于旧行读回校验**，不是当前能力的清单。
         *
         * 两个消费者都在旧行路径上：① `disclosedData` 必须落在本 schema 的词汇表内；
         * ② 旧行的存库指纹复原——当年写库的 `prohibitedData` 数组正是
         * `dataClassUniverseForSchema(schemaVersion) - disclosedData`（删掉这个函数就复原不出来，
         * 旧行的请求指纹校验会直接抛完整性异常）。
         *
         * `MODEL_AUTHORED_VISUAL_CANDIDATE` 保留在枚举里（结构化视觉链已随 D-Q5 删除，没有任何
         * 披露口径再产出它）：它是**已持久化的词汇表成员**——schema≥5 的旧清单把它写进
         * `prohibitedData`，删掉这个枚举值会让那些行在 `ModelTaskCodec.decodeRequest` 时因未知
         * 枚举名直接抛异常（旧行不再可读），指纹复原也会少一个成员。
         * schema<5 的分支也照旧保留：那时的清单确实不含该成员。
         */
        internal fun dataClassUniverseForSchema(
            schemaVersion: Int,
        ): Set<ModelEgressDataClass> = when {
            schemaVersion == 1 -> SCHEMA_V1_DATA_CLASSES
            schemaVersion < 5 ->
                ModelEgressDataClass.entries.toSet() -
                    ModelEgressDataClass.MODEL_AUTHORED_VISUAL_CANDIDATE
            else -> ModelEgressDataClass.entries.toSet()
        }

        val PROBLEM_ORGANIZATION_DISCLOSURE = setOf(
            ModelEgressDataClass.CONFIRMED_QUESTION_DOCUMENT,
            ModelEgressDataClass.RELATED_QUESTION_CANDIDATES,
            ModelEgressDataClass.SUBJECT_KNOWLEDGE_BASE,
        )
    }
}

sealed interface ModelExecutionPermit {
    data object LocalOnly : ModelExecutionPermit

    data class External(val manifest: ModelEgressManifest) : ModelExecutionPermit

    /**
     * Granted for agent-eligible kinds (capture assess/parse/classify and tutor
     * plan/respond) when the user has enabled global model-agent consent in
     * Settings ("configuring the model = consent"). Carries no per-asset grant: the
     * request's own asset refs plus the consent flag authorize the read. Lobby and
     * the organization/summarize routes always require a manifest.
     */
    data object ProviderConsented : ModelExecutionPermit
}

class ModelGatewayExecution internal constructor(
    val request: ModelTaskRequest,
    val permit: ModelExecutionPermit,
)

class ModelEgressAuthorizationException(
    val failureCode: ModelFailureCode,
    override val message: String,
) : IllegalArgumentException(message)

class ModelRequestBudgetExceededException :
    IllegalArgumentException("Model request exceeds the upload budget")

/**
 * Computes the complete Base64 contribution without allocating it. [nonImageJsonUtf8Bytes] must
 * include the request envelope and every image data-URL prefix, but not the Base64 characters.
 */
object ModelRequestPayloadBudget {
    const val MAX_PREPARED_REQUEST_BYTES: Long =
        MODEL_EXTERNAL_TRANSPORT_REQUEST_LIMIT_BYTES - 1L

    fun requirePreparedRequestFits(
        nonImageJsonUtf8Bytes: Long,
        assetByteSizes: Iterable<Long>,
    ): Long {
        if (nonImageJsonUtf8Bytes !in 0L..MAX_PREPARED_REQUEST_BYTES) {
            throw ModelRequestBudgetExceededException()
        }
        var estimatedBytes = nonImageJsonUtf8Bytes
        assetByteSizes.forEach { byteSize ->
            val encodedBytes = base64EncodedBytes(byteSize)
            if (
                encodedBytes > MAX_PREPARED_REQUEST_BYTES ||
                estimatedBytes > MAX_PREPARED_REQUEST_BYTES - encodedBytes
            ) {
                throw ModelRequestBudgetExceededException()
            }
            estimatedBytes += encodedBytes
        }
        return estimatedBytes
    }

    private fun base64EncodedBytes(byteSize: Long): Long {
        if (byteSize <= 0L) throw ModelRequestBudgetExceededException()
        val groups = byteSize / BASE64_INPUT_GROUP_BYTES +
            if (byteSize % BASE64_INPUT_GROUP_BYTES == 0L) 0L else 1L
        if (groups > Long.MAX_VALUE / BASE64_OUTPUT_GROUP_BYTES) {
            throw ModelRequestBudgetExceededException()
        }
        return groups * BASE64_OUTPUT_GROUP_BYTES
    }

    private const val BASE64_INPUT_GROUP_BYTES = 3L
    private const val BASE64_OUTPUT_GROUP_BYTES = 4L
}

/**
 * True when this agent-eligible request may egress to the configured provider
 * under global consent, WITHOUT an image-capability constraint (a structured-only
 * provider still runs text-only PLAN/RESPOND under consent). Image capability is
 * enforced separately via [ModelTaskInput.requiresImageInput] at authorize time.
 * Single source of truth for authorize() and the gateway's pre-flight.
 */
fun ModelTaskRequest.agentConsentMatches(provider: ProviderCapabilitySnapshot): Boolean =
    agentConsentGranted &&
        input.isAgentConsentEligible &&
        provider.executionLocation == ModelExecutionLocation.EXTERNAL_PROVIDER &&
        provider.supports(input.kind)

/** True when the input discloses image bytes that require an image-capable provider. */
fun ModelTaskInput.requiresImageInput(): Boolean =
    isAgentConsentEligible && requestsImageBytes

object ModelEgressPolicy {
    fun authorize(
        request: ModelTaskRequest,
        provider: ProviderCapabilitySnapshot,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ModelGatewayExecution = when (provider.executionLocation) {
        ModelExecutionLocation.LOCAL_NO_EGRESS,
        ModelExecutionLocation.UNAVAILABLE,
        -> ModelGatewayExecution(request, ModelExecutionPermit.LocalOnly)

        ModelExecutionLocation.EXTERNAL_PROVIDER -> {
            // Global-consent path: when the user enabled model-agent consent and this
            // is an agent-eligible round, the request may egress to the configured
            // provider without a per-item manifest. Image-bearing kinds additionally
            // require the provider to accept images. Lobby / organization routes still
            // require a manifest.
            if (
                request.agentConsentMatches(provider) &&
                (!request.input.requiresImageInput() || provider.supportsImageInput)
            ) {
                return ModelGatewayExecution(request, ModelExecutionPermit.ProviderConsented)
            }
            val manifest = request.egressManifest ?: throw ModelEgressAuthorizationException(
                ModelFailureCode.EGRESS_AUTHORIZATION_REQUIRED,
                "需要你确认本次发送范围后，才能交给模型处理",
            )
            runCatching { manifest.requireAuthorizes(request, provider, nowEpochMillis) }
                .getOrElse { cause ->
                    throw ModelEgressAuthorizationException(
                        ModelFailureCode.EGRESS_AUTHORIZATION_INVALID,
                        "本次发送范围与当前模型或题图不一致，请重新确认",
                    ).apply { initCause(cause) }
                }
            ModelGatewayExecution(request, ModelExecutionPermit.External(manifest))
        }
    }

    /**
     * Revalidates the exact permit immediately before an external transport is invoked.
     * Callers must not replace a local or stale permit by authorizing the request again.
     */
    fun requireCurrentExternalAuthorization(
        execution: ModelGatewayExecution,
        provider: ProviderCapabilitySnapshot,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        when (val permit = execution.permit) {
            is ModelExecutionPermit.External -> {
                val permittedManifest = permit.manifest
                if (execution.request.egressManifest != permittedManifest) {
                    throw invalidCurrentAuthorization()
                }
                val currentExecution = authorize(execution.request, provider, nowEpochMillis)
                val currentManifest = (currentExecution.permit as? ModelExecutionPermit.External)?.manifest
                    ?: throw invalidCurrentAuthorization()
                if (currentManifest != permittedManifest) {
                    throw invalidCurrentAuthorization()
                }
            }
            ModelExecutionPermit.ProviderConsented -> {
                // Re-validate the global-consent condition holds right now (toggle still on,
                // provider still the configured one, image-capable for image kinds). The
                // per-byte asset gate is enforced separately in the restricted asset source.
                if (
                    execution.request.agentConsentMatches(provider) &&
                    (!execution.request.input.requiresImageInput() || provider.supportsImageInput)
                ) {
                    return
                }
                throw invalidCurrentAuthorization()
            }
            ModelExecutionPermit.LocalOnly -> throw invalidCurrentAuthorization()
        }
    }

    private fun invalidCurrentAuthorization() = ModelEgressAuthorizationException(
        ModelFailureCode.EGRESS_AUTHORIZATION_INVALID,
        "本次发送范围与当前模型或题图不一致，请重新确认",
    )
}

private fun ModelEgressManifest.requireAuthorizes(
    request: ModelTaskRequest,
    provider: ProviderCapabilitySnapshot,
    nowEpochMillis: Long,
) {
    require(nowEpochMillis >= 0) { "Current time must not be negative" }
    require(subjectId == request.input.subjectId) { "Egress subject does not match request" }
    require(request.input.kind in authorizedTaskKinds) { "Task kind is outside egress scope" }
    require(providerId == provider.providerId) { "Egress provider does not match" }
    require(modelId == provider.modelId) { "Egress model does not match" }
    require(providerConfigurationVersion == provider.providerConfigurationVersion) {
        "Egress provider configuration changed"
    }
    val currentPromptPolicy = ModelPromptPolicyVersions.currentFor(request.input.kind)
    require(currentPromptPolicy != null && promptPolicyVersion == currentPromptPolicy) {
        "Egress prompt policy changed"
    }
    // 授权时效（15 分钟 TTL / 2 分钟时钟偏移）此前在这里逐次核对，D-K4 删除：清单时刻的唯一来源
    // 是客户端自己刚写的当前时间，所有派发/恢复点都在同一帧重盖（研究报告 §4.6 R5），判据永远成立。
    // `isModelEgressApprovalFresh` 本体保留——`TutorAutoStartAuthorization` 用它表达"自动开始讲题"
    // 的独立租约语义，与清单路径无关。nowEpochMillis 仍是本函数的入参（调用方时钟口径不变）。
    // Tutor consent is a short-lived, question-bound conversation lease. The UI must hold a
    // current in-memory lease; the manifest still binds every exact plan/response payload here.
    if (request.input !is TutorPlanInput && request.input !is TutorRespondInput) {
        require(approvedAtEpochMillis >= request.occurredAtEpochMillis) {
            "Egress approval predates the request"
        }
    }
    // 清单路径上只剩两条**真的会被走到**的路线（其余 kind 一律在 authorize 里走全局同意通道，
    // 见 ModelExecutionPermit.ProviderConsented；不可达分支已于 D-K4 删除）：
    // - 大厅（TUTOR_LOBBY）：非 agent-consent-eligible，逐次披露清单是它唯一的出网授权；
    // - 组织（PROBLEM_CLASSIFY）：同上，确认过的题面走逐次清单。
    when (val input = request.input) {
        is TutorLobbyInput -> {
            require(schemaVersion >= 4) { "Tutor lobby requires egress manifest schema four" }
            require(purpose == ModelEgressPurpose.TUTORING)
            // 本条消息的图 + 上文图片（最近一次带图消息的那几张）：两者都是本次真正出网的字节，
            // 所以授权范围必须逐张覆盖它们，缺一张就拒绝——图片不因"来自历史消息"而少一分披露。
            val disclosedImages = input.sourceImageAssetRefs + input.contextImageAssetRefs
            val includesImage = disclosedImages.isNotEmpty()
            // 大厅输入没有候选菜单字段：无题轮不存在"菜单里的别的题"，这个判据恒为 false
            // （与下面 `noQuestionRound` 不含该维一致：清单说覆盖了菜单同样被拒）。
            val carriesQuestionCandidates = input.disclosesQuestionCandidates()
            if (includesImage) {
                require(schemaVersion >= ModelEgressManifest.LOBBY_IMAGE_SCHEMA_VERSION) {
                    "Tutor lobby image egress requires egress manifest schema six"
                }
                require(
                    input.sourceImageAssetRefs.size <= ModelEgressManifest.MAX_LOBBY_IMAGE_ASSETS &&
                        input.contextImageAssetRefs.size <= ModelEgressManifest.MAX_LOBBY_IMAGE_ASSETS,
                ) {
                    "Tutor lobby image count exceeds the message budget"
                }
                require(assets.size == disclosedImages.size) {
                    "Lobby image scope changed"
                }
                disclosedImages.forEach { source ->
                    val grant = assets.singleOrNull { it.assetId == source.assetId }
                        ?: error("Lobby image is outside egress scope")
                    require(
                        grant.sha256 == source.sha256 &&
                            grant.width == source.width &&
                            grant.height == source.height &&
                            grant.selectedRegion == null &&
                            source.selectedRegion == null,
                    ) { "Lobby image changed after approval" }
                }
            } else {
                require(assets.isEmpty()) { "Tutor lobby cannot disclose image assets" }
            }
            require(includesQuestionCandidates == carriesQuestionCandidates) {
                "Egress manifest candidate-menu scope disagrees with the request"
            }
            val expectedDisclosure = TutorRoundDisclosure.noQuestionRound(
                includesImage = includesImage,
                schemaVersion = schemaVersion,
            )
            require(disclosedData == expectedDisclosure)
        }

        is ProblemOrganizationInput -> {
            require(purpose == ModelEgressPurpose.CLASSIFICATION)
            require(assets.isEmpty()) { "Problem organization cannot disclose image assets" }
            require(disclosedData == ModelEgressManifest.PROBLEM_ORGANIZATION_DISCLOSURE)
        }

        // 其余 kind 没有清单路径：agent-eligible 轮次带 `agentConsentGranted`、不带
        // `egressManifest`（见 feature 各 ModelTaskPolicy），要在清单路径上给它们伪造一份清单，
        // 生产代码也造不出来。删掉的 7 个分支持此前逐 kind 复述的断言（purpose / assets 为空 /
        // 披露集合）要么由 init 的精确相等校验蕴含、要么需要一份生产不存在的清单，运行时拦不住
        // 任何东西。
        else -> Unit
    }
}

/**
 * 授权时刻是否还在短时窗口内（15 分钟 TTL / 2 分钟时钟偏移）。
 *
 * 消费方只剩 `TutorAutoStartAuthorization.matches`：那是"刚拍完的题可以直接开始第一次讲题、
 * 不必二次确认"的一次性租约。清单路径**不再**核对它（D-K4：清单时刻的唯一来源就是客户端自己
 * 刚写的当前时间，运行时到不了过期状态；研究报告 §4.6 R5）。
 */
fun ModelEgressManifest.isModelEgressApprovalFresh(nowEpochMillis: Long): Boolean {
    return isModelEgressApprovalFresh(approvedAtEpochMillis, nowEpochMillis)
}

fun isModelEgressApprovalFresh(
    approvedAtEpochMillis: Long,
    nowEpochMillis: Long,
): Boolean {
    if (nowEpochMillis < 0) return false
    return if (approvedAtEpochMillis > nowEpochMillis) {
        approvedAtEpochMillis - nowEpochMillis <= MODEL_EGRESS_MAX_CLOCK_SKEW_MILLIS
    } else {
        nowEpochMillis - approvedAtEpochMillis <= MODEL_EGRESS_APPROVAL_TTL_MILLIS
    }
}

const val MODEL_EGRESS_APPROVAL_TTL_MILLIS: Long = 15L * 60L * 1_000L
const val MODEL_EGRESS_MAX_CLOCK_SKEW_MILLIS: Long = 2L * 60L * 1_000L

const val MODEL_EGRESS_MAX_ASSET_BYTES = 24L * 1_024L * 1_024L
