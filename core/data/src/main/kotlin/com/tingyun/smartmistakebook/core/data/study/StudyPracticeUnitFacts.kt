package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.port.PracticeUnitAssessmentRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.port.PracticeUnitKnowledgeBindingRecord
import com.tingyun.smartmistakebook.core.domain.PracticeUnitBindingFacts
import com.tingyun.smartmistakebook.core.domain.derivePracticeUnitBindingAttributions
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentCodec
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.TeachingArtifactVerification
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import com.tingyun.smartmistakebook.core.model.VerifiedTeachingArtifact
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * D-M M1 功能内核：机器可判题的目录来源从 fixture 换成**库内事实**。
 *
 * 旧实现（`StudyFixtureSource`/`requireTeachingArtifact`）把「这道题是否可机判、
 * 选项与标准答案是什么」交给 debug 源集注册的 M1 目录；release 构建里该目录恒为空，
 * 于是**任何** practice unit 都会在提交/揭示入口抛
 * `outside the verified M1 catalog`——应用从未发布故未见。
 *
 * 本类把同一批事实改由三段库内真值派生（计划 §3 步骤一第 3 条）：
 * 1. **practice unit 记录**：标题/题面/估计时长，及 problem / problem_revision 的
 *    科目、结构化题面（`question_document_snapshot`）、答案规格与验证状态；
 * 2. **当前绑定**：practice_unit_knowledge_binding 的现役绑定集（过滤到当前 revision 与
 *    同一 taxonomy），成为证据快照的知识归属；
 * 3. **题目字段**：结构化题面里唯一的 ChoiceGroup 是选项集，`single-choice.v1` 答案规格
 *    给出标准答案。
 * 无可用绑定时走**未归类 pseudo 桶**（spec §3.4）：`ensurePseudoKnowledgeBinding`
 * 幂等物化 `pseudo:<SUBJECT>` 节点与绑定，证据仍可见于掌握库（插眼 8 的桶）。
 *
 * **不可判题 → 返回 null / 抛错**：没有结构化选项或没有 VERIFIED 的
 * `single-choice.v1` 答案规格（拍摄保存题的答案规格是 null、状态 UNKNOWN）时，
 * 不猜答案——artifact 为 null，界面走既有的讲题判定通道（与 release 行为一致）。
 *
 * **快照不可变**：证据快照 id 用「题 + revision + taxonomy + 归属集」做内容寻址，
 * 归属集变化（改绑）会得到新 id，历史 attempt 指向的旧快照行不被改写。
 * capturedAt 取 revision 的创建时刻（确定性），保证同一 id 的重写是逐位 no-op。
 */
internal class StudyPracticeUnitFacts(
    private val database: StudyDatabasePort,
    private val writeContext: StudyWriteContext,
) {
    /** 机器可判题的讲解工件；不可判题返回 null（界面据此走讲题判定通道）。 */
    suspend fun artifactFor(practiceUnitId: String): VerifiedTeachingArtifact? {
        val record = database.readPracticeUnitAssessment(practiceUnitId) ?: return null
        val assessmentItem = assessmentItemOf(record) ?: return null
        return VerifiedTeachingArtifact(
            id = writeContext.stableId(ARTIFACT_ID_NAMESPACE, practiceUnitId),
            subject = record.subject,
            title = record.unitTitle,
            problemMarkdown = record.promptMarkdown,
            explanationMarkdown = explanationOf(assessmentItem),
            verification = TeachingArtifactVerification.DETERMINISTICALLY_VALIDATED,
            assessmentItems = listOf(assessmentItem),
            followUps = emptyList(),
            knowledgeNodeIds = knowledgeNodeIds(record.practiceUnitId),
        )
    }

    /**
     * 本题当前绑定的知识点（现役绑定集，去重、字典序）。教学材料范围与先修补救用；
     * 无绑定时为空集（先修/重教面因此不猜测归属）。
     *
     * "现役"与 [attributionSetFor]、重放共用同一读口（`readCurrentPracticeUnitKnowledgeBindings`
     * ——最近一次确认那一批）：改绑后**被证据引用而保留的旧绑定是审计遗迹**（RESTRICT 外键挡住
     * 物删），按全量读算进来会让教学材料范围/先修补救仍指向已改掉的旧节点，与证据归属口径分叉。
     */
    suspend fun knowledgeNodeIds(practiceUnitId: String): Set<String> =
        database.readCurrentPracticeUnitKnowledgeBindings(practiceUnitId)
            .mapTo(sortedSetOf(), PracticeUnitKnowledgeBindingRecord::knowledgeNodeId)

    /**
     * 提交/揭示两条路径所需的全部事实。题目不可机判时抛出——这两条路径只在界面已经
     * 拿到非空 artifact 后可达，抛错是"目录与界面口径分叉"的显式失败而不是静默降级。
     */
    suspend fun submissionFacts(practiceUnitId: String): StudySubmissionFacts {
        val record = database.readPracticeUnitAssessment(practiceUnitId)
            ?: error("Practice unit $practiceUnitId does not exist in the local library")
        val assessmentItem = assessmentItemOf(record)
            ?: error("Practice unit $practiceUnitId is not a machine-checkable question")
        val (taxonomyVersion, attributions) = attributionSetFor(record)
        val answerSpecId = requireNotNull(record.answerSpecId)
        return StudySubmissionFacts(
            assessmentItem = assessmentItem,
            explanationMarkdown = explanationOf(assessmentItem),
            evidenceSnapshot = AssessmentEvidenceSnapshot(
                snapshotId = writeContext.stableId(
                    SNAPSHOT_ID_NAMESPACE,
                    buildString {
                        append(record.practiceUnitId).append('\n')
                        append(record.problemRevisionId).append('\n')
                        append(taxonomyVersion).append('\n')
                        attributions.forEach { attribution ->
                            append(attribution.bindingId).append(':')
                            append(attribution.knowledgeNodeId).append(':')
                            append(attribution.weight).append(':')
                            append(attribution.role.name).append(':')
                            append(attribution.certainty.name).append('\n')
                        }
                    },
                ),
                assessmentItemId = assessmentItem.id,
                practiceUnitId = record.practiceUnitId,
                problemRevisionId = record.problemRevisionId,
                answerSpecId = answerSpecId,
                // 保存下来的题没有来源包身份（practice unit 记录里没有该列）：
                // null = 不参与同源错峰（KF-17），与无 fixture 的 release 行为一致。
                itemFamilyId = ITEM_FAMILY_ID_PREFIX + record.practiceUnitId,
                sourceBundleId = null,
                taxonomyVersion = taxonomyVersion,
                verification = AssessmentSnapshotVerification.VERIFIED,
                calibration = CalibrationSnapshot.unknown(),
                attributions = attributions,
                capturedAtEpochMillis = record.revisionCreatedAtEpochMillis,
            ),
        )
    }

    /**
     * 选项 + 标准答案的派生。门槛（缺一即不可机判）：
     * - 答案验证状态 VERIFIED 且带非空 answerSpecId；
     * - 答案规格是可解析的 `single-choice.v1`（含 correctChoiceId），且标准答案属于选项集；
     * - 结构化题面里恰有一个 ChoiceGroup，至少两个不同 id 的选项。
     */
    private suspend fun assessmentItemOf(record: PracticeUnitAssessmentRecord): TutorAssessmentItem? {
        if (record.answerVerificationStatus != StudyDbValue.VerificationStatus.VERIFIED) return null
        val answerSpecId = record.answerSpecId?.takeIf(String::isNotBlank) ?: return null
        val correctChoiceId = parseCorrectChoiceId(record.answerSpecSnapshot) ?: return null
        val document = record.questionDocumentSnapshot
            ?.takeIf(String::isNotBlank)
            ?.let { snapshot -> runCatching { CapturedQuestionDocumentCodec.decode(snapshot) }.getOrNull() }
            ?: return null
        val choiceGroups = document.document.blocks.filterIsInstance<ContentBlock.ChoiceGroup>()
        val group = choiceGroups.singleOrNull() ?: return null
        val choices = group.choices.map { choice ->
            TutorChoice(id = choice.id, markdown = choice.markdown)
        }
        if (choices.size < 2 || choices.map(TutorChoice::id).distinct().size != choices.size) {
            return null
        }
        if (choices.none { it.id == correctChoiceId }) return null
        return TutorAssessmentItem(
            id = writeContext.stableId(
                ASSESSMENT_ITEM_ID_NAMESPACE,
                "${record.practiceUnitId}\n${record.problemRevisionId}\n$answerSpecId",
            ),
            stemMarkdown = record.promptMarkdown,
            choices = choices,
            correctChoiceId = correctChoiceId,
            knowledgeNodeIds = knowledgeNodeIds(record.practiceUnitId),
        )
    }

    /**
     * 知识归属：先取**当前 revision + 同一 taxonomy** 的**当前**绑定（taxonomy 取字典序最小者，
     * 确定性），权重按数量均分（绑定记录不含 strength；DIRECT 归属总和不得超过 1 个单位，
     * 均分即守恒）；一个绑定都没有时物化 pseudo 桶并按 1.0 归属。
     *
     * 用 `readCurrentPracticeUnitKnowledgeBindings`（最近一次确认那一批）而不是表里的全部行：
     * 改绑后被证据引用而保留的旧绑定是审计遗迹，算进来会让新写的证据继续喂旧节点，并与重放的
     * "当前绑定"口径分叉（规则与理由见该端口方法的 KDoc）。
     *
     * 派生规则单源在 `core:domain` 的 `derivePracticeUnitBindingAttributions`
     * （KF-32：重放期按同一规则从当前绑定重派生，两条路必须同规则）。
     */
    private suspend fun attributionSetFor(
        record: PracticeUnitAssessmentRecord,
    ): Pair<String, List<KnowledgeEvidenceAttribution>> {
        val currentBindings = database.readCurrentPracticeUnitKnowledgeBindings(record.practiceUnitId)
            .filter { binding ->
                binding.basisRevisionId == record.problemRevisionId &&
                    binding.knowledgeNodeId.isNotBlank() &&
                    binding.bindingId.isNotBlank()
            }
            .sortedBy(PracticeUnitKnowledgeBindingRecord::bindingId)
        if (currentBindings.isNotEmpty()) {
            val taxonomyVersion = currentBindings.minOf(PracticeUnitKnowledgeBindingRecord::taxonomyVersion)
            val attributions = derivePracticeUnitBindingAttributions(
                bindings = currentBindings.map { binding ->
                    PracticeUnitBindingFacts(
                        bindingId = binding.bindingId,
                        practiceUnitId = binding.practiceUnitId,
                        knowledgeNodeId = binding.knowledgeNodeId,
                        taxonomyVersion = binding.taxonomyVersion,
                    )
                },
                basisRevisionId = record.problemRevisionId,
            )
            return taxonomyVersion to attributions
        }
        val pseudo = database.ensurePseudoKnowledgeBinding(
            practiceUnitId = record.practiceUnitId,
            problemRevisionId = record.problemRevisionId,
            taxonomyVersion = PSEUDO_ATTRIBUTION_TAXONOMY_VERSION,
            subject = record.subject,
            acceptedAtEpochMillis = record.revisionCreatedAtEpochMillis,
        ) ?: error("Could not materialize the pseudo knowledge node for ${record.practiceUnitId}")
        return pseudo.taxonomyVersion to listOf(
            KnowledgeEvidenceAttribution(
                bindingId = pseudo.bindingId,
                knowledgeNodeId = pseudo.knowledgeNodeId,
                weight = 1.0,
                basisRevisionId = record.problemRevisionId,
                taxonomyVersion = pseudo.taxonomyVersion,
                role = EvidenceAttributionRole.PRIMARY,
                certainty = EvidenceAttributionCertainty.DIRECT,
            ),
        )
    }

    /** 揭示内容：库内没有作者讲解，能诚实给出的就是标准答案本身。 */
    private fun explanationOf(assessmentItem: TutorAssessmentItem): String {
        val correct = assessmentItem.choices.first { it.id == assessmentItem.correctChoiceId }
        return "正确答案：${correct.markdown}"
    }

    private fun parseCorrectChoiceId(answerSpecSnapshot: String?): String? {
        val snapshot = answerSpecSnapshot?.takeIf(String::isNotBlank) ?: return null
        val payload = runCatching { Json.parseToJsonElement(snapshot) }.getOrNull() as? JsonObject
            ?: return null
        val schema = payload[SCHEMA_FIELD].primitiveContentOrNull() ?: return null
        if (schema != SINGLE_CHOICE_SCHEMA) return null
        return payload[CORRECT_CHOICE_FIELD].primitiveContentOrNull()
            ?.takeIf(String::isNotBlank)
    }

    private fun kotlinx.serialization.json.JsonElement?.primitiveContentOrNull(): String? =
        this?.let { element -> runCatching { element.jsonPrimitive.content }.getOrNull() }

    internal companion object {
        /**
         * pseudo 归属的 taxonomy 标记。与规划侧 pseudo 绑定（`pseudo-plan-v1`）分开：
         * 两次物化的绑定 id 因此不同，但都指向同一个 `pseudo:<SUBJECT>` 节点，
         * 归属集里的节点去重由派生方按集合语义保证。
         */
        const val PSEUDO_ATTRIBUTION_TAXONOMY_VERSION = "pseudo-evidence-v1"
        const val ITEM_FAMILY_ID_PREFIX = "saved-question:"
        const val SINGLE_CHOICE_SCHEMA = "single-choice.v1"
        private const val SCHEMA_FIELD = "schema"
        private const val CORRECT_CHOICE_FIELD = "correctChoiceId"
        private const val ARTIFACT_ID_NAMESPACE = "practice-unit-artifact"
        private const val ASSESSMENT_ITEM_ID_NAMESPACE = "assessment-item"
        private const val SNAPSHOT_ID_NAMESPACE = "assessment-snapshot"
    }
}

/** 提交/揭示共用的派生结果（选项、标准答案、讲解文本与不可变证据快照）。 */
internal data class StudySubmissionFacts(
    val assessmentItem: TutorAssessmentItem,
    val explanationMarkdown: String,
    val evidenceSnapshot: AssessmentEvidenceSnapshot,
)
