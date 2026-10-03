package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.CommitProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.CommitTutorSessionCommand
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeSearchFeatureExtractor
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialNodeBindingRecord
import com.tingyun.smartmistakebook.core.database.ProblemDraftRecord
import com.tingyun.smartmistakebook.core.database.port.MasteryAggregateRecord
import com.tingyun.smartmistakebook.core.database.port.SubjectMasteryRecord
import com.tingyun.smartmistakebook.core.database.TutorMessageRecord
import com.tingyun.smartmistakebook.core.database.TutorTurnResponseRecord
import com.tingyun.smartmistakebook.core.data.model.TutorFigureGenerator
import com.tingyun.smartmistakebook.core.data.model.TutorFigureRequest
import com.tingyun.smartmistakebook.core.data.model.TutorFigureResult
import com.tingyun.smartmistakebook.core.data.model.TutorFigureStatus
import com.tingyun.smartmistakebook.core.domain.MasteryEstimateMath
import com.tingyun.smartmistakebook.core.domain.MasteryWriteGate
import com.tingyun.smartmistakebook.core.domain.tutorSessionObjectiveRecord
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentValidator
import com.tingyun.smartmistakebook.core.model.KnowledgeAnchorClass
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialNodeRole
import com.tingyun.smartmistakebook.core.model.TeachingAdvisoryRecord
import com.tingyun.smartmistakebook.core.model.TutorAdvisoryKind
import com.tingyun.smartmistakebook.core.model.TutorAdvisoryScope
import com.tingyun.smartmistakebook.core.model.TutorDifficultyTier
import com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection
import com.tingyun.smartmistakebook.core.model.TutorEvidenceRecency
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.TutorKnowledgeCode
import com.tingyun.smartmistakebook.core.model.TutorKnowledgeCodeRole
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolOutcome
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.isReady
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * `tutor_message.role` value produced by `TutorConversationDao` for the
 * student's own turn. Only these rows count as "学生原话" when verifying
 * evidence anchors — assistant text is the model's own output and cannot
 * corroborate its own claims.
 */
private const val STUDENT_MESSAGE_ROLE = "STUDENT"

/**
 * How many knowledge nodes the keyword search may resolve in one focused
 * `MASTERY_READ`. This bounds *resolution*, not the answer: a wider net only
 * wastes budget on looser matches, since every resolved node has to fit the
 * result budget anyway.
 */
private const val MASTERY_FOCUS_RESOLUTION_LIMIT = 24

/**
 * Characters held back from the result budget so the truncation note itself
 * always fits. A note that got cut off would leave the model reading a partial
 * list as if it were complete — the failure the note exists to prevent.
 */
private const val TRUNCATION_NOTE_RESERVE_CHARS = 240

/**
 * 一次工具执行的本地结果：**模型可见的**那半（[outcome]）与**只在本地用的**那半（[resultCount]）。
 *
 * 为什么分成两个字段而不是塞进 [TutorToolOutcome]：outcome 是模型输入的一部分
 * （工具轮结果会随下一轮 prompt 走，参与 `ModelTaskFingerprint`），新增字段就要动模型输入的
 * 指纹；而"这次拿到了几行"只是学生那行小字要用的东西（B1 的痕迹），模型根本不需要它。
 * 分成两半之后，模型可见的形状与指纹一个字节都没变。
 */
internal data class TutorToolExecution(
    val outcome: TutorToolOutcome,
    /**
     * 这次拿到的结果条数（行/节点）。`0` 是有意义的取值（查了、没有匹配：B4 的"无可读范围"），
     * null = 该调用不产出行集（写工具，或被拒而根本没执行）。
     */
    val resultCount: Int? = null,
)

/**
 * Executes locally authorized read tools for the tutor tool loop
 * (spec model-intent-routing §2/§4). Every outcome is a capped markdown
 * digest — the model never sees raw rows, and failures become error
 * outcomes instead of exceptions so the loop can continue.
 */
internal class RoomTutorToolRunner(
    private val port: StudyDatabasePort,
    /**
     * 知识能力就绪位（D-Q3）：只有 KNOWLEDGE_READ 读它。内容还在后台就位时，
     * 回"还在准备"而不是"没有匹配的知识点"——后者把"还没好"说成了"没有"。
     */
    private val knowledgeBaseAvailability: StateFlow<KnowledgeBaseAvailability>,
    /**
     * A1 生图执行缝（4B）：null = 本装配没有生图通道（测试直调/未配置）。生产由 app 经
     * `ModelTaskRepositoryFactory` 注入 `TutorFigureGeneratorFactory` 的产物；
     * null 时 `GENERATE_FIGURE` 回 fail-closed 的"不可用"结果，**不**假装生成成功。
     */
    private val figureGenerator: TutorFigureGenerator? = null,
) {
    /** D-M M4：两条证据写通道的唯一写入口（本执行器只提供讲题通道的语义输入）。 */
    private val evidenceWriter = KnowledgeEvidenceWriter(port)

    /** 观测面：工具环协议测试断言执行器确实被调用。 */
    var executedCallCount: Int = 0
        private set

    /** Student context the tools need; lobby sessions have no subject. */
    data class Context(
        val subject: String?,
        val learnerId: String = "learner:local",
        val conversationId: String? = null,
        /**
         * The tutor session id (bare, not the prefixed conversation anchor).
         * NOTEBOOK_WRITE needs it to resolve the capture draft: a tutor session's
         * sessionId differs from its draftId, so the write path goes
         * sessionId -> readTutorSession -> draftId -> readProblemDraft(draftId).
         */
        val tutorSessionId: String? = null,
        /**
         * 当前教学轮次（`TutorRespondInput.cycleOrdinal`）。MASTERY_UPDATE 的
         * 客观交叉核对只数**本轮**的检查题作答：`restartCycle` 会在同一题上开新一轮
         * 重教，上一轮的答错正是重教的理由，永久计入会让门不可达。
         */
        val cycleOrdinal: Int = 1,
        /**
         * Namespace for deterministic evidence ids (the model-task requestId).
         * Null keeps the legacy nanoTime fallback for direct/test callers;
         * the tool loop always supplies it so a retried MASTERY_UPDATE is
         * idempotent instead of appending duplicate evidence.
         */
        val evidenceIdNamespace: String? = null,
        /**
         * Real-time attention factor for the current tutoring session
         * (research tutor-evidence-gate §2): [MasteryWriteGate] rejects a
         * write below its floor. Defaults to fully-attentive when the UI
         * collection channel is not wired.
         */
        val attentionFactor: Double = 1.0,
        /**
         * Whether this call may use the extended result budget.
         *
         * The round-level rule ("at most one extended result per round") lives in
         * the repository, because that is the only place that can see a round's
         * sibling calls; it passes the verdict down here. Defaults to allowed so
         * a direct caller asking for the larger budget gets it — the guard exists
         * to stop three oversized results stacking into one prompt, not to make
         * the request silently do nothing.
         */
        val allowsExtendedResult: Boolean = true,
        /**
         * 本轮披露集合是否覆盖**候选菜单**（
         * [com.tingyun.smartmistakebook.core.model.ModelTaskInput.disclosesQuestionCandidates]）：
         * 决定 `NOTEBOOK_READ` 的产出形态。
         *
         * 覆盖时（学生本轮显式带题 / 本地检索到候选）别的题的标题与科目属于**已披露**的
         * `RELATED_QUESTION_CANDIDATES`，逐条点名是被许可的能力；不覆盖时（无题轮、或本轮没有
         * 菜单）它们属于清单里**列为禁止**的类目，结果只给条数与检索词。
         *
         * 默认 false 是 fail-closed：直调者没说是哪一档就不列别的题。
         */
        val roundDisclosesQuestionCandidates: Boolean = false,
        /**
         * 会话代号注册表（单一代号通道，D5）：
         * - KNOWLEDGE_READ 对新发现节点追加披露（分配/复用代号），输出只给代号+名称+摘要，
         *   原始 id 不进结果文本；
         * - MASTERY_UPDATE 的 `terms[0]` 是代号，由此解析回原始 id 后才进门（anchor_class
         *   按角色机械确立，非 CONFIRMED 的权重减半在写入时施加）。
         * null = 直调/无会话（大厅）：KNOWLEDGE_READ 无代号可发（回退序号形态），
         * MASTERY_UPDATE 没有可写的目标 → **空范围**（ok=true + "本轮无可写目标"，K2a）。
         */
        val knowledgeCodeRegistry: TutorKnowledgeCodeRegistry? = null,
    ) {
        init {
            require(attentionFactor in 0.0..1.0) { "Attention factor must be in 0..1" }
            require(cycleOrdinal > 0) { "Tutor cycle ordinal must be positive" }
        }
    }

    suspend fun run(call: TutorToolCall, context: Context): TutorToolOutcome =
        runTraced(call, context).outcome

    /**
     * 与 [run] 同一件事，另外把**只在本地存在**的结果条数带出来（B1 的痕迹用它）。
     *
     * 无范围时的形态是**裁定过的**（K2a / spec §3.1）：读工具返回"本轮无可读范围"、写工具返回
     * "无可写目标"，**都是 ok=true**——不报错、不消耗额外预算。消灭的失败是"无题轮每个工具都
     * 注定失败"：模型被教导的是"调用=失败"，于是下一轮换着法再试，白烧派遣预算（台账 D-K2 ②）。
     */
    suspend fun runTraced(call: TutorToolCall, context: Context): TutorToolExecution {
        executedCallCount += 1
        return try {
        when (call.tool) {
            TutorToolName.KNOWLEDGE_READ -> {
                val subject = context.subject
                if (subject.isNullOrBlank()) {
                    emptyScopeRead(
                        tool = call.tool,
                        what = "这个知识点",
                    )
                } else {
                    knowledgeRead(subject, call.terms, context)
                }
            }
            TutorToolName.NOTEBOOK_READ -> notebookRead(call.terms, context)
            TutorToolName.MASTERY_READ -> masteryRead(call, context, context.allowsExtendedResult)
            TutorToolName.MASTERY_UPDATE -> masteryUpdate(call, context)
            TutorToolName.NOTEBOOK_WRITE -> notebookWrite(context)
            TutorToolName.ADVISORY_READ -> advisoryRead(call, context)
            TutorToolName.ADVISORY_WRITE -> advisoryWrite(call, context)
            TutorToolName.GENERATE_FIGURE -> generateFigure(call, context)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        TutorToolExecution(
            outcome = TutorToolOutcome(
                tool = call.tool,
                ok = false,
                summaryMarkdown = "查询没有完成，可以换个说法再试。",
                errorKind = "failed",
            ),
        )
    }
    }

    /**
     * 无范围读（K2a）：ok=true + "本轮无可读范围"。
     *
     * [resultCount] = 0 是刻意的：它让痕迹那一行说"本轮无可读范围"而不是"0 条"（B4 的话术），
     * 也让 [TutorToolTraceEntry] 的不变量（被拒才没有条数）在空范围上仍然成立。
     */
    private fun emptyScopeRead(tool: TutorToolName, what: String): TutorToolExecution =
        TutorToolExecution(
            outcome = TutorToolOutcome(
                tool = tool,
                ok = true,
                summaryMarkdown = "本轮无可读范围：这次对话还没有科目上下文，读不到$what。" +
                    "不必重复查询，直接回答学生的问题。",
            ),
            resultCount = 0,
        )

    /**
     * 无范围写（K2a）：ok=true + "无可写目标"——**仍不写**，只是把"没有可写的目标"如实说出来
     * 而不是报错。写不写由本地门决定，与这句话无关。
     */
    private fun emptyScopeWrite(tool: TutorToolName, reason: String): TutorToolExecution =
        TutorToolExecution(
            outcome = TutorToolOutcome(
                tool = tool,
                ok = true,
                summaryMarkdown = "本轮无可写目标：$reason。不必重复尝试，直接回答学生的问题。",
            ),
        )

    /**
     * 读知识点（KNOWLEDGE_READ）：检索 → 返回**代号 + 名称 + 边界前 80 字 + 绑定材料摘要**。
     *
     * 路由 = **v1 生产形状：裸 B 路 limit=[KNOWLEDGE_READ_NODE_LIMIT]**（SQL 倒排二值 TF 排序，
     * 无 A 路精排）。2026-09-22 曾统一成 B512→A 精排（与归类/拍照同源），但金标实测显示该
     * 路由在主集上 0.5222→0.3444、p95 663ms 超预算，是净伤害（详见
     * docs/kb-vector-topic-decision.md §3.2 与 KD-24），当日回滚到本形状——零回归。
     * B 路二值 TF 的"少公共 gram 精确节点排不进前 5"偏置是已知词面缺陷（登记册 D-01），
     * 按 D12 预注册由已开启的 dense 兜底议题解决，不在词面路由上打补丁。
     *
     * 单一代号通道（D5，关审计断链 E-03）：此前只回"名字+边界"，模型没有任何渠道拿到可写入的
     * 知识点标识，MASTERY_UPDATE 因此在聊天路径结构性不可达。现在每个返回节点都带本会话代号
     * （新发现节点在此**追加披露**——注册表分配 K(n+1) 起的新码，仓库把追加集写回下一轮输入
     * 的映射表），原始 id 不进结果文本。材料摘要让模型"知道这个知识点讲什么"而不必再发一轮
     * 查询，单条预算内（总结果仍受 2k 轮工具结果预算约束）。
     */
    private suspend fun knowledgeRead(
        subject: String,
        terms: List<String>,
        context: Context,
    ): TutorToolExecution {
        // D-Q3：内容还在后台就位（首装中 / 上次失败待重试）时如实说"准备中"。
        // ok=true 是有意的：适配层只渲染 ok=true 的 summary（失败形态只给 `[失败 kind]`），
        // 这里需要模型把"稍后再查"讲给学生，所以不能走失败形态。
        val availability = knowledgeBaseAvailability.value
        if (!availability.isReady) {
            android.util.Log.i(
                "TutorKnowledgeContext",
                "KNOWLEDGE_READ withheld: knowledge base not ready ($availability)",
            )
            return TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = TutorToolName.KNOWLEDGE_READ,
                    ok = true,
                    summaryMarkdown = "知识库还在准备中，本次没有可读的知识点；稍后再查一次即可。",
                ),
                resultCount = 0,
            )
        }
        val questionText = terms.joinToString(" ")
        val features = KnowledgeSearchFeatureExtractor.fromQuestion(questionText)
        val nodes = port.readSubjectKnowledgeRecallCandidates(
            subject = subject,
            searchFeatures = features,
            limit = KNOWLEDGE_READ_NODE_LIMIT,
            queryText = questionText,
        )
        if (nodes.isEmpty()) {
            return TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = TutorToolName.KNOWLEDGE_READ,
                    ok = true,
                    summaryMarkdown = "知识库里没有匹配的知识点。",
                ),
                resultCount = 0,
            )
        }
        val registry = context.knowledgeCodeRegistry
        val materialSummaries = nodeMaterialSummaries(subject, nodes)
        val lines = nodes.mapIndexed { index, node ->
            val code = registry?.assign(
                TutorKnowledgeCode(
                    knowledgeNodeId = node.knowledgeNodeId,
                    displayName = node.displayName,
                    role = TutorKnowledgeCodeRole.TOOL_DISCOVERED,
                ),
            )
            val marker = code ?: "${index + 1}"
            val boundary = node.boundaryMarkdown?.take(80)
            val material = materialSummaries[node.knowledgeNodeId]
                ?.let { "｜材料：${it.take(KNOWLEDGE_READ_MATERIAL_DIGEST_CHARS)}" }
                .orEmpty()
            "$marker. ${node.displayName}${boundary?.let { "：$it" } ?: ""}$material"
        }
        val header = if (registry != null) {
            "知识点候选 ${nodes.size} 个（代号 K1..Kn 本会话有效，写掌握度时 terms 填代号）："
        } else {
            "知识点候选 ${nodes.size} 个："
        }
        return TutorToolExecution(
            outcome = TutorToolOutcome(
                tool = TutorToolName.KNOWLEDGE_READ,
                ok = true,
                summaryMarkdown = "$header\n${lines.joinToString("\n")}",
            ),
            resultCount = nodes.size,
        )
    }

    /**
     * 每个节点取一条**PRIMARY 绑定优先**的材料摘要（无 PRIMARY 取第一条），供 KNOWLEDGE_READ
     * 结果内联。只读 summary 字段（材料全文走教学参考/材料卷通道），保持 2k 预算内。
     */
    private suspend fun nodeMaterialSummaries(
        subject: String,
        nodes: List<KnowledgeNodeSeedRecord>,
    ): Map<String, String> {
        val nodeIds = nodes.mapTo(linkedSetOf()) { it.knowledgeNodeId }
        val materials = port.readKnowledgeTeachingMaterialsForNodes(
            subject = subject,
            knowledgeNodeIds = nodeIds,
            limit = nodes.size,
        )
        if (materials.isEmpty()) return emptyMap()
        val bindings = port.readKnowledgeTeachingMaterialNodeBindings(
            materials.mapTo(linkedSetOf()) { it.materialId },
        )
        val primaryByNode = bindings
            .filter { it.role == KnowledgeMaterialNodeRole.PRIMARY.name }
            .groupBy(KnowledgeTeachingMaterialNodeBindingRecord::knowledgeNodeId)
        val byNode = bindings.groupBy(KnowledgeTeachingMaterialNodeBindingRecord::knowledgeNodeId)
        val materialById = materials.associateBy(KnowledgeTeachingMaterialRecord::materialId)
        return nodes.mapNotNull { node ->
            val materialId = primaryByNode[node.knowledgeNodeId]?.firstOrNull()?.materialId
                ?: byNode[node.knowledgeNodeId]?.firstOrNull()?.materialId
            materialId?.let { id -> materialById[id]?.summaryMarkdown }
                ?.let { summary -> node.knowledgeNodeId to summary }
        }.toMap()
    }

    private companion object {
        /** 裸 B 路召回的最终展示条数（v1 生产形状；B512→A 统一路由已回滚，见 KD-24）。 */
        const val KNOWLEDGE_READ_NODE_LIMIT = 5
        const val KNOWLEDGE_READ_MATERIAL_DIGEST_CHARS = 60
    }

    /**
     * 检索错题本（`NOTEBOOK_READ`）。
     *
     * 产出形态按**本轮披露范围**分两档（F5）：
     * - 本轮披露集合覆盖候选菜单（`RELATED_QUESTION_CANDIDATES`，即学生显式带题 / 本地检索到
     *   候选的那一轮）：别的题的标题与科目属于**已披露**的那一类，逐条点名是既有能力。
     * - 不覆盖（无题轮，或本轮没带菜单）：它们属于清单里**列为禁止**的类目，只回**条数与检索词**。
     *   此前这里无条件列出标题+科目，而大厅清单把 `RELATED_QUESTION_CANDIDATES` /
     *   `CONFIRMED_QUESTION_DOCUMENT` 列为禁止 —— 清单一处少报了一个真实出网的类目。
     *   解法是收紧产出，**不是**放宽披露集合：要不要把这一档披露出去，是用户的裁定。
     *   要更丰富的错题本结果，先改披露边界（加类目并按 bf8be888 的纪律升 manifest schema）。
     */
    private suspend fun notebookRead(terms: List<String>, context: Context): TutorToolExecution {
        val searchText = terms.joinToString(" ").take(120)
        val rows = port.libraryCatalogPage(
            searchText = searchText,
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
            sort = "RECENTLY_CREATED",
            offset = 0,
            limit = 6,
        )
        if (rows.isEmpty()) {
            return TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = TutorToolName.NOTEBOOK_READ,
                    ok = true,
                    summaryMarkdown = "错题本里没有匹配的条目。",
                ),
                resultCount = 0,
            )
        }
        if (!context.roundDisclosesQuestionCandidates) {
            val termNote = terms.takeIf(List<String>::isNotEmpty)
                ?.let { "（检索词：${it.joinToString("、")}）" }
                .orEmpty()
            return TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = TutorToolName.NOTEBOOK_READ,
                    ok = true,
                    summaryMarkdown = "错题本里匹配 ${rows.size} 条$termNote。本轮披露范围不含别的题的标题，" +
                        "故只给条数；不要臆造或复述任何题目标题，" +
                        "需要具体某道题时请学生在错题本里查看或从错题本选择。",
                ),
                resultCount = rows.size,
            )
        }
        val lines = rows.mapIndexed { index, row ->
            "${index + 1}. ${row.title}（${row.subject}）"
        }
        return TutorToolExecution(
            outcome = TutorToolOutcome(
                tool = TutorToolName.NOTEBOOK_READ,
                ok = true,
                summaryMarkdown = "错题本匹配 ${rows.size} 条：\n${lines.joinToString("\n")}",
            ),
            resultCount = rows.size,
        )
    }

    /**
     * 写错题本（T4）：把当前会话已识别、校验通过的真实题存入错题本。
     * 学生确认门已在授权层（explicitActionRequest）把关；此处只做"当前会话确有已识别题面
     * → 校验 → commit"。题面来源强锚定到 draft（学生手机上识别过的真实题），不是模型凭空
     * 生成——防臆造。commitTutorSession 内部幂等（已保存则只返回，不重复落库）。
     *
     * 无会话（无题轮/大厅）时走 [emptyScopeWrite]（K2a）：**没有可写目标**是正常形态，不是错误。
     * 会话在、但题面还没识别出来（reference_not_found）或还没就绪（not_ready）仍是真错误——
     * 那两种情况"有目标但目标不成立"，与"根本没有目标"不是一回事。
     */
    private suspend fun notebookWrite(context: Context): TutorToolExecution {
        val sessionId = context.tutorSessionId
        if (sessionId.isNullOrBlank()) {
            return emptyScopeWrite(
                tool = TutorToolName.NOTEBOOK_WRITE,
                reason = "这次对话没有正在处理的题目",
            )
        }
        // 解析真实 draftId：tutor session 的 sessionId ≠ draftId，需先经
        // readTutorSession(sessionId) 拿记录里的 draftId，再用它读题面 draft。
        val tutorSession = port.readTutorSession(sessionId)
            ?: return TutorToolExecution(
                TutorToolOutcome(
                    tool = TutorToolName.NOTEBOOK_WRITE,
                    ok = false,
                    summaryMarkdown = "当前会话未建立完整讲题上下文，无法保存。",
                    errorKind = "no_tutor_session",
                ),
            )
        val draftId = tutorSession.draftId
        val draft = port.readProblemDraft(draftId)
            ?: return TutorToolExecution(
                TutorToolOutcome(
                    tool = TutorToolName.NOTEBOOK_WRITE,
                    ok = false,
                    summaryMarkdown = "当前会话还没有识别出题目，无法保存。",
                    errorKind = "reference_not_found",
                ),
            )
        val revision = draft.currentRevision
        val issues = CapturedQuestionDocumentValidator.validateForCommit(revision.questionDocument)
        if (issues.isNotEmpty()) {
            return TutorToolExecution(
                TutorToolOutcome(
                    tool = TutorToolName.NOTEBOOK_WRITE,
                    ok = false,
                    summaryMarkdown = "题目尚未准备就绪，无法保存。",
                    errorKind = "not_ready",
                ),
            )
        }
        val now = System.currentTimeMillis()
        val problemId = revision.draftId
        val result = port.commitTutorSession(
            CommitTutorSessionCommand(
                sessionId = sessionId,
                commit = CommitProblemDraftCommand(
                    commandId = "commit-$draftId-$now",
                    draftId = draftId,
                    expectedRevisionNumber = revision.revisionNumber,
                    problemId = problemId,
                    problemRevisionId = "$draftId-rev-${revision.revisionNumber}",
                    practiceUnitId = draftId,
                    errorBookEntryId = "entry-$draftId-$now",
                    estimatedSeconds = 60,
                    committedAtEpochMillis = now,
                ),
            ),
        )
        return TutorToolExecution(
            outcome = if (result.created) {
                TutorToolOutcome(
                    tool = TutorToolName.NOTEBOOK_WRITE,
                    ok = true,
                    summaryMarkdown = "已保存到错题本。",
                )
            } else {
                TutorToolOutcome(
                    tool = TutorToolName.NOTEBOOK_WRITE,
                    ok = true,
                    summaryMarkdown = "这道题已在错题本里。",
                )
            },
        )
    }

    /**
     * Reads the learner's mastery of the current subject, knowledge-node grained.
     *
     * Two modes, chosen by whether the model named anything:
     * - **list** (`terms` empty): the whole subject's nodes that have evidence,
     *   weakest first — this is what closes the old `take(24)` blind slice,
     *   which was ordered by practice-unit id and therefore showed an arbitrary
     *   24 bindings regardless of how weak or relevant they were.
     * - **focus** (`terms` given): the nodes those words resolve to, each with
     *   its structured history aggregates.
     *
     * The subject is a **disclosure boundary**, not a filter of convenience:
     * the tool may only ever return the subject this session is already working
     * in, which is why the query is scoped by it rather than filtered after the
     * fact. That is also what keeps the result inside the already-disclosed
     * "bounded learning evidence" class (see the tool-loop wiring design §3.6).
     *
     * There is no row cap — the character budget is the real bound — so an
     * oversized result is truncated with a visible note telling the model how to
     * narrow (more specific terms) or to ask for the larger budget.
     */
    private suspend fun masteryRead(
        call: TutorToolCall,
        context: Context,
        allowsExtendedResult: Boolean,
    ): TutorToolExecution {
        val subject = context.subject?.takeIf(String::isNotBlank)
        if (subject == null) {
            // K2a：无题轮/大厅没有科目上下文 = 无范围，不是失败（成功形态走 emptyScopeRead）。
            return emptyScopeRead(
                tool = TutorToolName.MASTERY_READ,
                what = "这个学生的掌握情况",
            )
        }
        val rows = port.readSubjectMastery(context.learnerId, subject)
        if (rows.isEmpty()) {
            return TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = TutorToolName.MASTERY_READ,
                    ok = true,
                    summaryMarkdown = "还没有足够的学习记录来评估掌握情况。",
                ),
                resultCount = 0,
            )
        }
        val budget = if (call.extendedResult && allowsExtendedResult) {
            TutorToolOutcome.MAX_TOOL_RESULT_CHARS_EXTENDED
        } else {
            TutorToolOutcome.MAX_TOOL_RESULT_CHARS
        }
        val now = System.currentTimeMillis()
        val focusNodes = if (call.terms.isEmpty()) {
            null
        } else {
            resolveFocusNodes(subject, call.terms)
        }
        if (focusNodes != null && focusNodes.isEmpty()) {
            return TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = TutorToolName.MASTERY_READ,
                    ok = true,
                    summaryMarkdown = "没有找到与${call.terms.joinToString("、")}匹配的知识点，" +
                        "可以换用材料或题面里的原词再试。",
                ),
                resultCount = 0,
            )
        }
        val selected = (if (focusNodes == null) rows else rows.filter { it.knowledgeNodeId in focusNodes })
            // Sorted here rather than relying on the query's ORDER BY: the query
            // has no LIMIT, so what matters is the order in force when the result
            // budget truncates — the rows dropped have to be the *least* urgent
            // ones, and a weaker node is always more urgent than a stronger one.
            .sortedWith(
                compareBy(
                    SubjectMasteryRecord::lowerBoundIndependentCorrect,
                    SubjectMasteryRecord::displayName,
                    SubjectMasteryRecord::knowledgeNodeId,
                ),
            )
        val aggregates = if (focusNodes == null || selected.isEmpty()) {
            emptyMap()
        } else {
            port.readMasteryAggregates(context.learnerId, selected.mapTo(linkedSetOf()) { it.knowledgeNodeId })
                .associateBy(MasteryAggregateRecord::knowledgeNodeId)
        }
        val unmeasured = focusNodes.orEmpty()
            .filterKeys { nodeId -> selected.none { it.knowledgeNodeId == nodeId } }
        return TutorToolExecution(
            outcome = TutorToolOutcome(
                tool = TutorToolName.MASTERY_READ,
                ok = true,
                summaryMarkdown = renderMasteryRead(
                    subject = subject,
                    rows = selected,
                    aggregates = aggregates,
                    unmeasuredNodes = unmeasured,
                    subjectNodeCount = port.countReviewableKnowledgeNodes(subject),
                    focused = focusNodes != null,
                    atEpochMillis = now,
                    budgetChars = budget,
                ),
            ),
            resultCount = selected.size,
        )
    }

    /**
     * Resolves the model's words to knowledge nodes of the current subject via
     * the reviewed search index, keeping the display names so a node that has no
     * evidence yet can still be reported by name rather than as an opaque id.
     *
     * 路由 = **v1 生产形状：裸 B 路 limit=[MASTERY_FOCUS_RESOLUTION_LIMIT]**（聚焦的
     * 输出形态与条数上限不变，D7）。2026-09-22 曾统一成 B512→A 精排，金标实测净伤害
     * 后当日回滚（docs/kb-vector-topic-decision.md §3.2、KD-24）。
     */
    private suspend fun resolveFocusNodes(subject: String, terms: List<String>): Map<String, String> {
        val features = KnowledgeSearchFeatureExtractor.fromQuestion(terms.joinToString(" "))
        if (features.isEmpty()) return emptyMap()
        return port.readSubjectKnowledgeRecallCandidates(
            subject = subject,
            searchFeatures = features,
            limit = MASTERY_FOCUS_RESOLUTION_LIMIT,
        ).associate { node -> node.knowledgeNodeId to node.displayName }
    }

    private fun renderMasteryRead(
        subject: String,
        rows: List<SubjectMasteryRecord>,
        aggregates: Map<String, MasteryAggregateRecord>,
        unmeasuredNodes: Map<String, String>,
        subjectNodeCount: Int,
        focused: Boolean,
        atEpochMillis: Long,
        budgetChars: Int,
    ): String {
        val body = BudgetedLines(budgetChars - TRUNCATION_NOTE_RESERVE_CHARS)
        val header = buildString {
            append("掌握情况（科目 $subject")
            if (focused) append("，聚焦查询") else append("，按最弱优先")
            append("）：")
            if (focused) {
                append("命中 ${rows.size} 个已有证据的知识点")
                if (unmeasuredNodes.isNotEmpty()) append("，另有 ${unmeasuredNodes.size} 个尚无学习证据")
            } else {
                append("${rows.size} 个知识点已有学习证据")
                val remaining = subjectNodeCount - rows.size
                if (remaining > 0) append("，该科另有 $remaining 个尚无学习证据")
            }
            append('。')
        }
        body.add(header)
        body.add("列：序号. 名称|粒度|保守掌握度|区间|证据量|状态|最近证据|最近独立错误|绑定错题数")
        rows.forEachIndexed { index, row ->
            body.add(
                "${index + 1}. ${row.displayName}|${row.granularity}|" +
                    "${"%.2f".format(row.lowerBoundIndependentCorrect)}|" +
                    "${masteryIntervalText(row)}|" +
                    "${"%.2f".format(row.evidenceMass)}|${row.status}|" +
                    "${TutorEvidenceRecency.of(row.lastEvidenceAtEpochMillis, atEpochMillis)}|" +
                    "${TutorEvidenceRecency.of(row.lastIndependentErrorAtEpochMillis, atEpochMillis)}|" +
                    "${row.boundQuestionCount}",
            )
            aggregates[row.knowledgeNodeId]?.let { detail ->
                appendAggregateDetail(body, detail, row, atEpochMillis)
            }
        }
        unmeasuredNodes.entries.sortedBy { it.value }.forEach { (_, name) ->
            body.add("$name|尚无学习证据")
        }
        return body.render(
            truncationNote = "已截断：还有 ${body.droppedCount} 项未显示。" +
                "可用更具体的 terms 收窄查询，或对单次查询申请扩展预算（extendedResult=true）。",
        )
    }

    /**
     * KF-20（批次 2 §2.2）：区间列由 s/f **现算**（`MasteryEstimateMath.interval`，两位小数）。
     * 无证据（s + f <= 0）退化为 `—`——"没有证据"不是"区间很宽"。
     */
    private fun masteryIntervalText(row: SubjectMasteryRecord): String {
        if (row.successWeight + row.failureWeight <= 0.0) return "—"
        val interval = MasteryEstimateMath.interval(row.successWeight, row.failureWeight)
        return "${"%.2f".format(interval.lower)}~${"%.2f".format(interval.upper)}"
    }

    private fun appendAggregateDetail(
        body: BudgetedLines,
        detail: MasteryAggregateRecord,
        row: SubjectMasteryRecord,
        atEpochMillis: Long,
    ) {
        body.add(
            "   独立答对 ${detail.independentCorrectCount} 次（跨 " +
                "${detail.independentCorrectItemFamilyCount} 个题目族、" +
                "${detail.independentCorrectStudyDayCount} 个学习日），最近 " +
                "${TutorEvidenceRecency.of(detail.lastIndependentCorrectAtEpochMillis, atEpochMillis)}",
        )
        body.add(
            "   独立错误 ${detail.independentErrorCount} 次，最近 " +
                "${TutorEvidenceRecency.of(detail.lastIndependentErrorAtEpochMillis, atEpochMillis)}",
        )
        body.add(
            "   讲题/测验证据 接受 ${detail.acceptedModelEvidenceCount} 条、" +
                "被拒 ${detail.rejectedModelEvidenceCount} 条，最近接受 " +
                "${TutorEvidenceRecency.of(detail.lastAcceptedModelEvidenceAtEpochMillis, atEpochMillis)}",
        )
        // KF-20（批次 2 §2.2）：记忆行（E 判据的展示依据）——无记忆卡时不输出该行。
        val stability = row.memoryStabilityDays
        val lastAttempt = row.lastAttemptAtEpochMillis
        if (stability != null && lastAttempt != null) {
            body.add(
                "   记忆：稳定度 ${"%.1f".format(stability)} 天，上次作答 " +
                    TutorEvidenceRecency.of(lastAttempt, atEpochMillis),
            )
        }
    }

    /**
     * Collects lines up to a character budget and counts what did not fit.
     *
     * The count is the point: a silently shortened list would read as the whole
     * subject, and the model would conclude there is nothing more to look at.
     */
    private class BudgetedLines(private val budgetChars: Int) {
        private val lines = mutableListOf<String>()
        private var usedChars = 0
        var droppedCount = 0
            private set

        fun add(line: String) {
            val cost = line.length + 1
            if (usedChars + cost > budgetChars) {
                droppedCount += 1
                return
            }
            lines += line
            usedChars += cost
        }

        fun render(truncationNote: String): String = buildString {
            append(lines.joinToString("\n"))
            if (droppedCount > 0) {
                append('\n')
                append(truncationNote)
            }
        }
    }


    private suspend fun masteryUpdate(call: TutorToolCall, context: Context): TutorToolExecution {
        // 模型只给语义元素（direction/understanding/代号锚），weight 与
        // 一切门控由本地 MasteryWriteGate 决定——模型无数值权，无关键词猜测。
        // TutorToolCall.init 已强制 MASTERY_UPDATE 必须带 direction/understanding；
        // 此处仍按"宁漏记"防御：缺字段时拒写而非默认负向。
        val direction = call.direction
        val understanding = call.understanding
        // 单一代号通道（D5）：terms[0] 是**本会话代号**（K1..Kn），不是原始 id。
        // 白名单前哨在仓库的轮次判定（Route B 背底）与 native schema enum（Route A 约束
        // 解码）；这里是解析落点——代号 → 原始 id + 角色（anchor_class 由角色机械确立）。
        val codeTerm = call.terms.firstOrNull().orEmpty()
        val registry = context.knowledgeCodeRegistry
        if (registry == null) {
            // K2a：这次对话根本没有代号通道（大厅 / 无会话）＝**没有已披露的知识点**，
            // 也就没有可写目标。这是正常形态，不是"调用失败"；写口一如既往什么都没有发生。
            return emptyScopeWrite(
                tool = TutorToolName.MASTERY_UPDATE,
                reason = "这次对话还没有已披露的知识点",
            )
        }
        val resolved = registry.resolve(codeTerm)
        if (resolved == null) {
            // 结构性拒（协议错误路径）：编造/未披露代号没有可解析的目标，不进 gate、
            // 不落任何观察行（没有知识节点可以挂靠审计）。有代号通道而代号对不上，
            // 与"没有通道"是两回事——前者是协议错误，后者是空范围（上面那支）。
            return TutorToolExecution(
                TutorToolOutcome(
                    tool = TutorToolName.MASTERY_UPDATE,
                    ok = false,
                    summaryMarkdown = "该代号不在本会话已披露的知识点中，未执行。",
                    errorKind = "invalid_knowledge_code",
                ),
            )
        }
        val knowledgeNodeId = resolved.knowledgeNodeId
        // 结果回显（批次 0 条目 5a）：这次写入落到哪个知识点——**会话代号 + 节点名**。
        // 原始 id 永不进结果文本（D5 契约：原始 id 不进 prompt/工具参数）；名称本就随
        // 代号表与教学参考对模型披露过，回显不越界。消灭的失败：代号解析错/写进无关节点时，
        // 接受与被拒两种结果此前都读不出目标，模型与学生都没法当场发现写错了知识点。
        // 解析出的条目必带本会话代号（注册表只对已赋码条目建索引），null 说明注册表坏掉——
        // 宁可失败关闭，也不要印出「null」或静默退回无目标的旧形态。
        val targetCode = requireNotNull(resolved.code) {
            "A resolved knowledge code must carry its session code"
        }
        val targetLabel = "$targetCode「${resolved.displayName}」"
        val anchorClass = KnowledgeAnchorClass.of(resolved.role).name
        val now = System.currentTimeMillis()
        if (direction == null || understanding == null) {
            return TutorToolExecution(
                TutorToolOutcome(
                    tool = TutorToolName.MASTERY_UPDATE,
                    ok = false,
                    summaryMarkdown = "这条学习证据缺少模型的方向/理解判断，未计入掌握度。",
                    errorKind = "rejected:missing_semantics",
                ),
            )
        }

        // 写入口（D-M M4）：锚定、三路配额、门评估、D9 降权、幂等 id、被拒观察行
        // 全部在 KnowledgeEvidenceWriter 一处发生——本执行器只提供讲题通道的语义输入
        //（方向/理解档/理由/置信 + 两处本地可核查性核对）。
        val writeOutcome = evidenceWriter.write(
            KnowledgeEvidenceWriteRequest(
                learnerId = context.learnerId,
                channel = KnowledgeEvidenceChannel.MODEL_CHAT,
                namespace = context.evidenceIdNamespace,
                discriminator = call.tool.name,
                conversationId = context.conversationId,
                knowledgeNodeId = knowledgeNodeId,
                direction = direction,
                understanding = understanding,
                evidenceConfidence = call.confidence,
                reasonMarkdown = call.rationale,
                anchorClass = anchorClass,
                requiredSubject = context.subject,
                // 讲题通道本地拿不到"学生懂了"的客观佐证（研究 §1：本地无可靠语义
                // 信号），故 MASTERED 的可核查性改为数模型 rationale 里逐字引用的
                // 证据锚条数（档2，spec 2026-09-06 §1；档1 prompt 规范同源）。
                hasObjectiveSupport = false,
                // 只数**引文真出现在本会话文本里**的锚：档1 规范要求"逐字引用学生
                // 原话"，仅数引号会让 `"因为""所以"` 这类编造凑够门槛。
                // **正向各档都消费这个值**（MASTERED ≥2，其余正向 ≥1，2026-09-13 的正向底线），
                // 因此不能只在 MASTERED 时核对——否则 CONFIDENT 会拿"引号数"冒充"已核实锚"，
                // 编造的引文照样本进库。负向不消费，省掉这次回读。
                evidenceAnchorCount = if (direction == TutorEvidenceDirection.POSITIVE) {
                    MasteryWriteGate.verifiedEvidenceAnchorCount(
                        rationale = call.rationale,
                        verifiableText = verifiableSessionText(context),
                    )
                } else {
                    0
                },
                // 反向的客观核对（研究 tutor-evidence-gate §3.2）：学生在本轮答错过
                // 模型自己出的检查题时，模型再判 POSITIVE 就是口头声明压过行为证据。
                // 这与"有没有佐证"是两个方向——此处查的是"有没有反驳"。门只对
                // POSITIVE 消费它，故只在正向判断时才付这次回读的成本。
                objectiveAnswersContradictPositive =
                    direction == TutorEvidenceDirection.POSITIVE &&
                        objectiveAnswersContradictPositive(context),
                attentionFactor = context.attentionFactor,
                occurredAtEpochMillis = now,
            ),
        )
        return when (writeOutcome) {
            is KnowledgeEvidenceWriteOutcome.Accepted -> TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = TutorToolName.MASTERY_UPDATE,
                    ok = true,
                    summaryMarkdown = "学习证据已记录：$targetLabel ${direction.name} " +
                        "weight=${writeOutcome.weight}",
                ),
            )
            is KnowledgeEvidenceWriteOutcome.Rejected -> TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = TutorToolName.MASTERY_UPDATE,
                    ok = false,
                    summaryMarkdown = "这条学习证据未通过校验，未计入掌握度" +
                        "（${writeOutcome.reason.name}）——目标知识点 $targetLabel。",
                    errorKind = "rejected:${writeOutcome.reason.name}",
                ),
            )
        }
    }

    /**
     * A1（4B）：生图工具的执行分支。模型只给语义（kind + description）。
     *
     * **源图只可能来自本地**：REDRAW_PROBLEM 经 会话 → 草稿 → 规范资产记录 解析，由生成器
     * 在读取时再逐位核对（`vault.resolve` 是唯一实现）；模型的任何字段都进不了源图
     * （[TutorFigureRequest] 里根本没有模型可填的图片字段）。
     *
     * 结果只回 id + 状态（不回字节）；**没有当前题时两种 kind 都不生成**（F2：无锚点的
     * GENERATE_PROCESS 也是一次付费出网），按 K2a 空范围处理（ok=true、不报错，免得模型
     * 下一轮换着法再试），通道缺失或生成失败才回错误结果。
     */
    private suspend fun generateFigure(call: TutorToolCall, context: Context): TutorToolExecution {
        // TutorToolCall.init 已强制 GENERATE_FIGURE 必带 kind + description；此处按
        // "宁拒不猜"防御：缺字段时不生成而不是默认一种图。
        val kind = requireNotNull(call.figureKind) {
            "GENERATE_FIGURE kind is contract-enforced"
        }
        val description = requireNotNull(call.figureDescription) {
            "GENERATE_FIGURE description is contract-enforced"
        }
        val generator = figureGenerator
            ?: return TutorToolExecution(
                TutorToolOutcome(
                    tool = call.tool,
                    ok = false,
                    summaryMarkdown = "本机没有可用的生图通道，未生成配图；不要反复申请。",
                    errorKind = FIGURE_UNAVAILABLE,
                ),
            )
        // F2：**两种 kind 的共同前置**是"有当前题"——意图矩阵本意是"大厅/查库/闲聊轮没有
        // 可画的题"，但 Route B 的意图由模型自报，不能只靠矩阵。没有当前题时两种图都不生成
        // （GENERATE_PROCESS 也一样：没有题锚的过程图只是一次无锚点的付费生成），
        // 按 K2a 回 ok=true 空范围、**不调用生成器**。
        val draft = currentDraft(context)
            ?: return TutorToolExecution(
                TutorToolOutcome(
                    tool = call.tool,
                    ok = true,
                    summaryMarkdown = "本轮没有可画的题：这次对话没有正在处理的题目。" +
                        "不必重复申请，直接回答学生的问题。",
                ),
            )
        val sourceSheet = when (kind) {
            // REDRAW 额外要求当前题的规范题面资产；`ProblemDraftRecord.sourceAsset` 非空
            // 是构造契约（记录里必然带页 0 源资产），所以这里没有第二个空分支。
            TutorFigureKind.REDRAW_PROBLEM -> draft.sourceAsset
            TutorFigureKind.GENERATE_PROCESS -> null
        }
        val result = try {
            generator.generate(
                TutorFigureRequest(
                    kind = kind,
                    description = description,
                    sourceSheet = sourceSheet,
                    // GENERATE_PROCESS 的当前题上下文（标题；本地取，模型不可填）。
                    problemContext = draft?.currentRevision?.title,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            TutorFigureResult(figureId = null, status = TutorFigureStatus.FAILED)
        }
        return when (result.status) {
            TutorFigureStatus.GENERATED -> TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = call.tool,
                    ok = true,
                    // F1：只回 id + 状态。**不得**向模型承诺"图会出现在回复里"——批 1 没有
                    // 渲染路径（持久引用/渲染是批 2 的 A2），承诺会让模型引导学生去找看不到的图。
                    summaryMarkdown = "配图已生成（id：${result.figureId}，状态：已生成）。" +
                        "不要再申请同一张图。",
                ),
            )

            TutorFigureStatus.FAILED -> TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = call.tool,
                    ok = false,
                    summaryMarkdown = "配图没有生成成功，可以稍后再试一次。",
                    errorKind = FIGURE_FAILED,
                ),
            )

            TutorFigureStatus.UNAVAILABLE -> TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = call.tool,
                    ok = false,
                    summaryMarkdown = "本机没有可用的生图通道（未配置可用的图像模型），" +
                        "未生成配图；不要反复申请。",
                    errorKind = FIGURE_UNAVAILABLE,
                ),
            )
        }
    }

    /** 当前题的草稿（会话 → draftId → draft）；没有会话/草稿/空 id 时 null。 */
    private suspend fun currentDraft(context: Context): ProblemDraftRecord? {
        val sessionId = context.tutorSessionId?.takeIf(String::isNotBlank) ?: return null
        val draftId = port.readTutorSession(sessionId)?.draftId?.takeIf(String::isNotBlank)
            ?: return null
        return port.readProblemDraft(draftId)
    }

    /**
     * D-M M7：咨询工具的**读侧**（ADVISORY_READ）——按节点/题/科目取最近 N 条。
     *
     * 三个作用域与写侧同一套解析（见 [advisoryWrite]）：NODE = terms[0] 代号（D5：只收
     * 代号，原始 id 永不进工具参数）；PROBLEM = 本会话当前题（practice unit 由会话记录解析，
     * 不接受模型给的 id）；SUBJECT = 本会话科目。科目是**披露边界**而非便利过滤：没有科目
     * 上下文（大厅）时是空范围，不是"读全库"。
     *
     * 产出形态与其它读工具一致：ok=true 的 markdown 摘要（含条数），失败一律空范围/错误
     * 结果而不是异常；[TutorToolExecution.resultCount] 供痕迹那行小字用。
     */
    private suspend fun advisoryRead(call: TutorToolCall, context: Context): TutorToolExecution {
        val scope = call.advisoryScope
            ?: if (call.terms.isEmpty()) TutorAdvisoryScope.SUBJECT else TutorAdvisoryScope.NODE
        val subject = context.subject?.takeIf(String::isNotBlank)
        val target: AdvisoryReadTarget = when (scope) {
            TutorAdvisoryScope.NODE -> {
                val codeTerm = call.terms.firstOrNull().orEmpty()
                val registry = context.knowledgeCodeRegistry
                    ?: return emptyScopeRead(call.tool, "这个知识点的教学备注")
                val resolved = registry.resolve(codeTerm)
                    ?: return TutorToolExecution(
                        TutorToolOutcome(
                            tool = call.tool,
                            ok = false,
                            summaryMarkdown = "该代号不在本会话已披露的知识点中，未执行。",
                            errorKind = INVALID_KNOWLEDGE_CODE,
                        ),
                    )
                if (subject == null) {
                    return emptyScopeRead(call.tool, "这个知识点的教学备注")
                }
                AdvisoryReadTarget(
                    knowledgeNodeId = resolved.knowledgeNodeId,
                    practiceUnitId = null,
                    subject = subject,
                    subjectSourceId = null,
                    nodeLabel = resolved.displayName,
                )
            }

            TutorAdvisoryScope.PROBLEM -> {
                val practiceUnitId = currentAdvisoryPracticeUnitId(context)
                    ?: return emptyScopeRead(call.tool, "这道题的教学备注")
                AdvisoryReadTarget(
                    knowledgeNodeId = null,
                    practiceUnitId = practiceUnitId,
                    subject = null,
                    subjectSourceId = null,
                    nodeLabel = ADVISORY_PROBLEM_LABEL,
                )
            }

            TutorAdvisoryScope.SUBJECT -> {
                subject ?: return emptyScopeRead(call.tool, "本科目的教学备注")
                AdvisoryReadTarget(
                    knowledgeNodeId = null,
                    practiceUnitId = null,
                    subject = subject,
                    subjectSourceId = advisoryToolSourceId(TutorAdvisoryScope.SUBJECT, subject),
                    nodeLabel = ADVISORY_SUBJECT_LABEL,
                )
            }
        }
        val rows = port.readTeachingAdvisoriesForTool(
            learnerId = context.learnerId,
            knowledgeNodeId = target.knowledgeNodeId,
            practiceUnitId = target.practiceUnitId,
            subject = target.subject,
            subjectSourceId = target.subjectSourceId,
            limit = ADVISORY_READ_LIMIT,
        )
        if (rows.isEmpty()) {
            return TutorToolExecution(
                outcome = TutorToolOutcome(
                    tool = call.tool,
                    ok = true,
                    summaryMarkdown = "还没有${target.nodeLabel}的教学备注。",
                ),
                resultCount = 0,
            )
        }
        // 节点名回显：行上挂着节点时用名称而不是 id（原始 id 不进结果文本，D5 契约）。
        val nodeNames = port.readKnowledgeNodesByIds(
            rows.mapNotNullTo(linkedSetOf()) { row -> row.knowledgeNodeId },
        ).associate { node -> node.knowledgeNodeId to node.displayName }
        val now = System.currentTimeMillis()
        val body = BudgetedLines(TutorToolOutcome.MAX_TOOL_RESULT_CHARS - TRUNCATION_NOTE_RESERVE_CHARS)
        body.add("教学备注 ${rows.size} 条（最近优先，${advisoryScopeLabel(scope)}）：")
        rows.forEachIndexed { index, row ->
            val label = row.knowledgeNodeId?.let(nodeNames::get)
                ?: if (row.practiceUnitId != null) ADVISORY_PROBLEM_LABEL else target.nodeLabel
            val kind = advisoryKindLabel(row.advisoryKind)
            val payload = row.payloadMarkdown.take(ADVISORY_READ_PAYLOAD_DIGEST_CHARS)
            body.add(
                "${index + 1}. [$kind] $label：$payload" +
                    "（${TutorEvidenceRecency.of(row.createdAtEpochMillis, now)}）",
            )
        }
        return TutorToolExecution(
            outcome = TutorToolOutcome(
                tool = call.tool,
                ok = true,
                summaryMarkdown = body.render(
                    truncationNote = "已截断：另有 ${body.droppedCount} 条未显示。" +
                        "可用更具体的作用域（节点代号或当前题）收窄查询。",
                ),
            ),
            resultCount = rows.size,
        )
    }

    /**
     * D-M M7：咨询工具的**写侧**（ADVISORY_WRITE）——三 kind 限枚举 + 作用域校验 + 稳定键 upsert。
     *
     * "每一参数都校验"（Anthropic《Memory tool》官方口径，计划 §2.3）：kind 是 enum（另在协议
     * 层约束解码）；scope 三选一且各自的目标必须由本地解析——NODE 收代号并核对节点**真实存在
     * 且属于本会话科目**（复用既有 `readKnowledgeNodesByIds` 读口，与证据写入口同一条锚定
     * 判据）；PROBLEM/SUBJECT 的目标来自会话上下文，模型给的 id 一律不接受；payload 有上限，
     * DIFFICULTY_TIER 另限 EASY/MEDIUM/HARD。任一不过 = 结构性拒（模型拿到原因，本地什么都没写）。
     *
     * 稳定键 upsert：source_id 由"作用域 + 目标"确定性派生，命中
     * `(learner_id, source_id, advisory_kind)` 唯一索引即更新旧行——同一条共识反复写不会堆积
     * （curate 语义的存储半边）。
     */
    private suspend fun advisoryWrite(call: TutorToolCall, context: Context): TutorToolExecution {
        val scope = requireNotNull(call.advisoryScope) { "ADVISORY_WRITE scope is contract-enforced" }
        val kind = requireNotNull(call.advisoryKind) { "ADVISORY_WRITE kind is contract-enforced" }
        val payload = requireNotNull(call.payloadMarkdown) {
            "ADVISORY_WRITE payload is contract-enforced"
        }.trim()
        if (kind == TutorAdvisoryKind.DIFFICULTY_TIER) {
            // 难度档的消费方（StudyReviewPlannerService）按 TutorDifficultyTier 解析 payload；
            // 自由文本会被静默忽略——那正是"写了但没人读得懂"的假记录，挡在校验层。
            val tier = runCatching { TutorDifficultyTier.valueOf(payload) }.getOrNull()
                ?: return TutorToolExecution(
                    TutorToolOutcome(
                        tool = call.tool,
                        ok = false,
                        summaryMarkdown = "难度备注的 payload 只能是 EASY、MEDIUM 或 HARD，未执行。",
                        errorKind = INVALID_ADVISORY_PAYLOAD,
                    ),
                )
            // 复用本地枚举的落库名（与既有 DIFFICULTY_TIER 写通道同一形状）。
            return recordAdvisory(
                call = call,
                context = context,
                scope = scope,
                kind = kind,
                payload = tier.name,
            )
        }
        return recordAdvisory(
            call = call,
            context = context,
            scope = scope,
            kind = kind,
            payload = payload,
        )
    }

    /** 解析写/读目标之后的一次落库（upsert）。 */
    private suspend fun recordAdvisory(
        call: TutorToolCall,
        context: Context,
        scope: TutorAdvisoryScope,
        kind: TutorAdvisoryKind,
        payload: String,
    ): TutorToolExecution {
        val subject = context.subject?.takeIf(String::isNotBlank)
        val target: AdvisoryTarget = when (scope) {
            TutorAdvisoryScope.NODE -> {
                if (subject == null) {
                    return emptyScopeWrite(call.tool, ADVISORY_EMPTY_REASON_NO_SUBJECT)
                }
                val registry = context.knowledgeCodeRegistry
                    ?: return emptyScopeWrite(call.tool, ADVISORY_EMPTY_REASON_NO_CODE)
                val resolved = registry.resolve(call.terms.firstOrNull().orEmpty())
                    ?: return TutorToolExecution(
                        TutorToolOutcome(
                            tool = call.tool,
                            ok = false,
                            summaryMarkdown = "该代号不在本会话已披露的知识点中，未执行。",
                            errorKind = INVALID_KNOWLEDGE_CODE,
                        ),
                    )
                val node = port.readKnowledgeNodesByIds(setOf(resolved.knowledgeNodeId)).firstOrNull()
                if (node == null || node.subject != subject) {
                    // 节点不存在或不属于本会话科目：编造/越界的代号没有合法目标（与
                    // KnowledgeEvidenceWriter 的锚定判据同一精神）。
                    return TutorToolExecution(
                        TutorToolOutcome(
                            tool = call.tool,
                            ok = false,
                            summaryMarkdown = "该代号对应的知识点不存在或不在本会话科目内，未执行。",
                            errorKind = INVALID_KNOWLEDGE_CODE,
                        ),
                    )
                }
                AdvisoryTarget(
                    knowledgeNodeId = node.knowledgeNodeId,
                    practiceUnitId = null,
                    sourceId = advisoryToolSourceId(scope, node.knowledgeNodeId),
                    label = "${resolved.code}「${node.displayName}」",
                )
            }

            TutorAdvisoryScope.PROBLEM -> {
                val practiceUnitId = currentAdvisoryPracticeUnitId(context)
                    ?: return emptyScopeWrite(call.tool, ADVISORY_EMPTY_REASON_NO_QUESTION)
                AdvisoryTarget(
                    knowledgeNodeId = null,
                    practiceUnitId = practiceUnitId,
                    sourceId = advisoryToolSourceId(scope, practiceUnitId),
                    label = ADVISORY_PROBLEM_LABEL,
                )
            }

            TutorAdvisoryScope.SUBJECT -> {
                subject ?: return emptyScopeWrite(call.tool, ADVISORY_EMPTY_REASON_NO_SUBJECT)
                AdvisoryTarget(
                    knowledgeNodeId = null,
                    practiceUnitId = null,
                    sourceId = advisoryToolSourceId(scope, subject),
                    label = "$ADVISORY_SUBJECT_LABEL（$subject）",
                )
            }
        }
        val now = System.currentTimeMillis()
        port.upsertTeachingAdvisories(
            listOf(
                TeachingAdvisoryRecord(
                    // 稳定键：PK = (learner, 作用域目标, kind) 的线性化——与唯一索引
                    // `(learner_id, source_id, advisory_kind)` **同冲突面**，所以 REPLACE
                    // 只会替换本 learner 的同一目标行，绝不会因 PK 撞上而删掉别人的行。
                    // 同一目标的重复写因此是"更新同一行"，重试幂等、不堆积。
                    advisoryId = "${context.learnerId}:${target.sourceId}:${kind.name}",
                    learnerId = context.learnerId,
                    practiceUnitId = target.practiceUnitId,
                    knowledgeNodeId = target.knowledgeNodeId,
                    advisoryKind = kind.name,
                    payloadMarkdown = payload,
                    confidence = null,
                    sourceId = target.sourceId,
                    createdAtEpochMillis = now,
                ),
            ),
        )
        return TutorToolExecution(
            outcome = TutorToolOutcome(
                tool = call.tool,
                ok = true,
                summaryMarkdown = "教学备注已记录（${advisoryKindLabel(kind.name)}）：${target.label}。",
            ),
        )
    }

    /**
     * 当前题的 practice unit：与会话记录解析出的 draft 一致——与 NOTEBOOK_WRITE 的
     * `sessionId -> readTutorSession -> draftId` 同一路径，也是 commit 时 `practiceUnitId = draftId`
     * 的口径。会话不存在/没有 draft = 没有当前题（空范围，不是失败）。
     *
     * **边界（不改行为，复核 2026-10-03 P2 登记）**：PROBLEM 目标只验证会话 draftId 非空，
     * **不验证** draft / practice unit 是否仍存在；advisory 表没有指向 practice_unit 的外键，
     * 因此接受"孤儿行"。读侧按 practice unit 过滤，孤儿行只会孤零零地留在表里（没有读者
     * 会把它错配到别的题）。要更强的一致性得给表加 FK 或写前回读 draft——两条都会改变
     * 审计层的语义，不在本批范围。
     */
    private suspend fun currentAdvisoryPracticeUnitId(context: Context): String? =
        context.tutorSessionId?.takeIf(String::isNotBlank)
            ?.let { sessionId -> port.readTutorSession(sessionId)?.draftId }
            ?.takeIf(String::isNotBlank)

    private data class AdvisoryReadTarget(
        val knowledgeNodeId: String?,
        val practiceUnitId: String?,
        val subject: String?,
        val subjectSourceId: String?,
        /** 空结果时的目标措辞（"这个知识点"/"这道题"/"本科目"）。 */
        val nodeLabel: String,
    )

    private data class AdvisoryTarget(
        val knowledgeNodeId: String?,
        val practiceUnitId: String?,
        val sourceId: String,
        val label: String,
    )

    /**
     * 本轮学生客观作答有没有推翻正向判断（研究 `tutor-evidence-gate-research.md` §3.2）。
     *
     * 只数**当前轮**：`restartCycle` 会在同一题上开新一轮重教，上一轮的答错正是
     * 重教的理由；把历史轮次的答错永久计入，学生重教后答对也洗不掉，门就成了
     * 不可达的死门（与档2 修的 0.18 死常数同类）。
     *
     * 无会话上下文（Lobby 派遣 / 测试直调）时不强加核对——没有会话就没有客观作答
     * 可言。读失败会冒泡到 [run] 的 catch 变成 failed outcome，即写不进去，
     * 不会因此误放行。
     */
    private suspend fun objectiveAnswersContradictPositive(context: Context): Boolean {
        val sessionId = context.tutorSessionId?.takeIf(String::isNotBlank) ?: return false
        val correctness = port.observeTutorTurnResponses(sessionId)
            .first()
            .filter { it.cycleOrdinal == context.cycleOrdinal }
            .mapNotNull(TutorTurnResponseRecord::selectionWasCorrect)
        return tutorSessionObjectiveRecord(correctness).contradictsPositiveClaim
    }

    /**
     * 本会话里学生**确实产出过**的文本，供证据锚核对（[MasteryWriteGate.verifiedEvidenceAnchorCount]）。
     *
     * 两个来源，都是本地事实而非模型自报：
     * - 学生消息原文（`tutor_message` 的 STUDENT 行）；
     * - 学生的客观作答（本轮检查题所选选项文本），属于档1 规范里的"可观察行为"。
     *
     * 读失败或没有会话上下文时返回空串：核对函数对空语料返回 0 锚，于是
     * MASTERED 判断被拒——写不进去，不会因读失败而误放行。
     */
    private suspend fun verifiableSessionText(context: Context): String {
        val sessionId = context.tutorSessionId?.takeIf(String::isNotBlank)
        val conversationId = context.conversationId?.takeIf(String::isNotBlank)
        val studentMessages = conversationId
            ?.let { id ->
                port.observeTutorMessages(id)
                    .first()
                    .filter { it.role == STUDENT_MESSAGE_ROLE }
                    .map(TutorMessageRecord::bodyMarkdown)
            }
            .orEmpty()
        val objectiveAnswers = sessionId
            ?.let { id ->
                port.observeTutorTurnResponses(id)
                    .first()
                    .filter { it.cycleOrdinal == context.cycleOrdinal }
                    .mapNotNull(TutorTurnResponseRecord::selectedChoiceMarkdown)
            }
            .orEmpty()
        return (studentMessages + objectiveAnswers).joinToString("\n")
    }
}

/**
 * 字符数上限：咨询工具的读回一次最多 [ADVISORY_READ_LIMIT] 条。取值理由：8 条 ×
 * 单条摘要 140 字符 ≈ 1.2k，加上表头与时间档位仍在 2k 单轮结果预算内；"想拿更多"应由
 * 更具体的作用域收窄查询解决，而不是放大单次结果（数值上限本地定，模型只给语义作用域）。
 */
private const val ADVISORY_READ_LIMIT = 8

/** 读回里单条 payload 的摘要字符数（完整内容留在库里；超出部分不影响模型看到主旨）。 */
private const val ADVISORY_READ_PAYLOAD_DIGEST_CHARS = 140

/** 咨询写/读里"当前题"与"本科目"的本地措辞。 */
private const val ADVISORY_PROBLEM_LABEL = "这道题"
private const val ADVISORY_SUBJECT_LABEL = "本科目"

/** 咨询工具的三种空范围原因（K2a 口径：ok=true 的"没得写/没得读"，不是错误）。 */
private const val ADVISORY_EMPTY_REASON_NO_SUBJECT = "这次对话还没有科目上下文"
private const val ADVISORY_EMPTY_REASON_NO_CODE = "这次对话还没有已披露的知识点"
private const val ADVISORY_EMPTY_REASON_NO_QUESTION = "这次对话没有正在处理的题目"

/** 代号不在本会话已披露集合：协议层结构性拒（与轮次门/MASTERY_UPDATE 同一结果码）。 */
private const val INVALID_KNOWLEDGE_CODE = "invalid_knowledge_code"

/** DIFFICULTY_TIER 的 payload 不是 EASY/MEDIUM/HARD：写侧参数校验拒（不落行）。 */
private const val INVALID_ADVISORY_PAYLOAD = "invalid_advisory_payload"

/** A1 生图：本装配没有生图通道（未配置可用图像模型 / 本构建不可出网）。 */
private const val FIGURE_UNAVAILABLE = "figure_unavailable"

/** A1 生图：通道在但这次没成（出网/解码/落盘失败）。 */
private const val FIGURE_FAILED = "figure_failed"

/**
 * 咨询工具的**稳定键**（D-M M7 的 upsert 半边）：作用域 + 目标 ⇒ 恒定的 source_id，
 * 命中 `(learner_id, source_id, advisory_kind)` 唯一索引即更新旧行。对科目作用域，
 * 读侧用同一个函数生成查询键——命名只有这一处出处，读写不可能漂开。
 */
internal fun advisoryToolSourceId(scope: TutorAdvisoryScope, targetKey: String): String =
    "advisory-tool:${scope.name.lowercase()}:$targetKey"

/** 咨询档位 → 模型可见的短标签（未知档位原样回显，不编造）。 */
private fun advisoryKindLabel(kindName: String): String = when (kindName) {
    TeachingAdvisoryRecord.KIND_TEACHING_FOCUS -> "讲法"
    TeachingAdvisoryRecord.KIND_MISCONCEPTION -> "误区"
    TeachingAdvisoryRecord.KIND_DIFFICULTY_TIER -> "难度"
    else -> kindName
}

private fun advisoryScopeLabel(scope: TutorAdvisoryScope): String = when (scope) {
    TutorAdvisoryScope.NODE -> "按知识点"
    TutorAdvisoryScope.PROBLEM -> "按当前题"
    TutorAdvisoryScope.SUBJECT -> "按科目"
}
