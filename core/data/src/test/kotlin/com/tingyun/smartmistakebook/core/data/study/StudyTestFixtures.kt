package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeBindingSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRelationSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.database.port.PracticeUnitAssessmentRecord
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentCodec
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentFingerprint
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.StructuredChoice
import com.tingyun.smartmistakebook.core.model.WritingLayer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * D-M M1 测试夹具（**测试源集**，不是生产接缝）：生产侧 `M1CuratedStudySeed` /
 * `StudyFixtureSource` 随 fixture 系统退场后，需要这份内容的测试（本模块的 JVM 用例与
 * app 的仪器化用例各自持有等价夹具）在这里自给自足。
 *
 * 内容与退场的 M1 curated 目录逐字等价（同 id/题面/选项/答案规格/绑定），
 * 但**不再**提供 `teachingArtifact`/`evidenceSnapshot` 直供：提交/揭示路径已改为
 * 从 practice unit + 当前绑定 + 题目字段派生（`StudyPracticeUnitFacts`），
 * 夹具只需把库内事实写进假库。
 */
internal object CuratedStudyTestFixture {
    const val FIXTURE_VERSION = "m1-curated-v1"
    const val TAXONOMY_VERSION = "cn-highschool-m1-v1"
    const val TUTOR_PROBLEM_ID = "problem:m1:math:derivative-sign-change"
    const val TUTOR_PRACTICE_UNIT_ID = "practice:m1:derivative-sign-change:whole"

    fun bundle(includeTutorMistake: Boolean = false): StudySeedBundle {
        val questions = curatedQuestions()
        return StudySeedBundle(            problems = questions.map(QuestionSeed::problemRecord),
            revisions = questions.map(QuestionSeed::revisionRecord),
            practiceUnits = questions.map(QuestionSeed::practiceUnitRecord),
            errorBookEntries = questions.filter { question ->
                question.initiallyInMistakeBook ||
                    (includeTutorMistake && question.slug == TUTOR_QUESTION_SLUG)
            }
                .map(QuestionSeed::errorBookEntryRecord),
            knowledgeNodes = questions.map(QuestionSeed::knowledgeNodeRecord),
            knowledgeBindings = questions.map(QuestionSeed::knowledgeBindingRecord),
            relations = listOf(
                ProblemRelationSeedRecord(
                    relationId = "relation:m1:derivative-sign-prerequisite-extrema",
                    sourceProblemId = TUTOR_PROBLEM_ID,
                    targetProblemId = "problem:m1:math:closed-interval-extrema",
                    relationType = StudyDbValue.RelationType.PREREQUISITE_OF,
                    status = StudyDbValue.RelationStatus.ACTIVE,
                    sourceBasisRevisionId = "revision:m1:derivative-sign-change:r1",
                    targetBasisRevisionId = "revision:m1:closed-interval-extrema:r1",
                    confidence = 1.0,
                    createdAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
                    updatedAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
                ),
            ),
            assessmentItems = questions.map(QuestionSeed::assessmentItemRecord),
        )
    }

    /** 提交/揭示派生路径读取的题目事实（每个 practice unit 一条）。 */
    fun assessmentRecords(): List<PracticeUnitAssessmentRecord> =
        curatedQuestions().map(QuestionSeed::assessmentRecord)

    private fun curatedQuestions() = listOf(
        QuestionSeed(
            slug = "closed-interval-extrema",
            subject = "MATH",
            title = "闭区间上的函数最值",
            problemMarkdown = "已知函数 f(x)=x^3-3x+1，求它在闭区间 [-2,2] 上的最大值与最小值。",
            choices = listOf(
                "A" to "最大值为 2，最小值为 -2",
                "B" to "最大值为 3，最小值为 -1",
                "C" to "最大值为 1，最小值为 -3",
                "D" to "最大值为 4，最小值为 -4",
            ),
            correctChoiceId = "B",
            explanationMarkdown = "先求导，比较驻点与端点后取最大/最小。",
            knowledgeCode = "math.derivative.closed_interval_extrema",
            knowledgeName = "利用导数求闭区间最值",
            estimatedSeconds = 240,
            initiallyInMistakeBook = true,
        ),
        QuestionSeed(
            slug = "derivative-sign-change",
            subject = "MATH",
            title = "由导数符号判断单调区间",
            problemMarkdown = "设函数 f(x) 在实数集上可导，且 f'(x)=(x-1)(x+2)。下列关于单调性的判断正确的是哪一项？",
            choices = listOf(
                "A" to "在 (-∞,-2) 与 (1,+∞) 上递增，在 (-2,1) 上递减",
                "B" to "在 (-∞,-2) 与 (1,+∞) 上递减，在 (-2,1) 上递增",
                "C" to "在 (-∞,1) 上递增，在 (1,+∞) 上递减",
                "D" to "在实数集上始终递增",
            ),
            correctChoiceId = "A",
            explanationMarkdown = "导数在 x=-2,1 处为零，三个区间符号依次为正、负、正。",
            knowledgeCode = "math.derivative.monotonicity",
            knowledgeName = "导数符号与函数单调性",
            estimatedSeconds = 150,
            initiallyInMistakeBook = false,
        ),
        QuestionSeed(
            slug = "electromagnetic-direction",
            subject = "PHYSICS",
            title = "运动导体中的电荷偏转方向",
            problemMarkdown = "一根竖直金属棒在水平面内向右匀速运动，空间中存在垂直纸面向里的匀强磁场。哪一端电势较高？",
            choices = listOf(
                "A" to "下端电势较高",
                "B" to "上端电势较高",
                "C" to "两端电势始终相等",
                "D" to "两端电势高低周期性交替",
            ),
            correctChoiceId = "B",
            explanationMarkdown = "速度向右、磁场向里，洛伦兹力方向向上。",
            knowledgeCode = "physics.electromagnetism.lorentz_force_direction",
            knowledgeName = "洛伦兹力方向与动生电动势",
            estimatedSeconds = 150,
            initiallyInMistakeBook = true,
        ),
        QuestionSeed(
            slug = "conic-eccentricity",
            subject = "MATH",
            title = "椭圆离心率",
            problemMarkdown = "椭圆 x^2/25+y^2/9=1 的离心率是多少？",
            choices = listOf(
                "A" to "3/5",
                "B" to "2/3",
                "C" to "4/5",
                "D" to "5/4",
            ),
            correctChoiceId = "C",
            explanationMarkdown = "a=5,b=3，c=4，离心率 e=c/a=4/5。",
            knowledgeCode = "math.conic.ellipse_eccentricity",
            knowledgeName = "椭圆标准方程与离心率",
            estimatedSeconds = 120,
            initiallyInMistakeBook = true,
        ),
        QuestionSeed(
            slug = "chemical-equilibrium",
            subject = "CHEMISTRY",
            title = "用浓度商判断平衡移动",
            problemMarkdown = "恒温下 N2O4(g) ⇌ 2NO2(g) 已达平衡，体积压缩为一半，平衡将怎样移动？",
            choices = listOf(
                "A" to "不移动，因为温度没有改变",
                "B" to "向右移动，因为各物质浓度都增大",
                "C" to "先向右移动，再向左移动",
                "D" to "向左移动，因为突变后 Qc 大于 Kc",
            ),
            correctChoiceId = "D",
            explanationMarkdown = "体积减半瞬间 Qc=2Kc>Kc，系统向左移动。",
            knowledgeCode = "chemistry.equilibrium.reaction_quotient",
            knowledgeName = "浓度商与化学平衡移动",
            estimatedSeconds = 180,
            initiallyInMistakeBook = true,
        ),
    )

    private data class QuestionSeed(
        val slug: String,
        val subject: String,
        val title: String,
        val problemMarkdown: String,
        val choices: List<Pair<String, String>>,
        val correctChoiceId: String,
        val explanationMarkdown: String,
        val knowledgeCode: String,
        val knowledgeName: String,
        val estimatedSeconds: Int,
        val initiallyInMistakeBook: Boolean,
    ) {
        private val problemId = "problem:m1:${subject.lowercase()}:$slug"
        private val revisionId = "revision:m1:$slug:r1"
        val practiceUnitId = "practice:m1:$slug:whole"
        private val answerSpecId = "answer:m1:$slug:r1"
        val assessmentId = "assessment:m1:$slug:r1"
        private val knowledgeNodeId = "knowledge:m1:$knowledgeCode"
        private val optionsSnapshot = choices.toOptionsSnapshot()
        private val answerSpecSnapshot = "{\"schema\":\"single-choice.v1\",\"correctChoiceId\":\"$correctChoiceId\"}"
        private val capturedQuestionDocument by lazy {
            val blocks = listOf(
                ContentBlock.Paragraph("$slug-stem", problemMarkdown),
                ContentBlock.ChoiceGroup(
                    id = "$slug-choices",
                    promptMarkdown = "请选择一个答案",
                    choices = choices.map { (choiceId, markdown) ->
                        StructuredChoice(id = choiceId, markdown = markdown)
                    },
                ),
            )
            CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document:m1:$slug:r1",
                    title = title,
                    blocks = blocks,
                ),
                blockEvidence = blocks.map { block ->
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "curated-reference:$slug",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    )
                },
            )
        }

        fun problemRecord() = ProblemSeedRecord(
            problemId = problemId,
            canonicalFingerprint = sha256("$subject\n$problemMarkdown"),
            subject = subject,
            createdAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
        )

        fun revisionRecord() = ProblemRevisionSeedRecord(
            revisionId = revisionId,
            problemId = problemId,
            revisionNumber = 1,
            title = title,
            problemMarkdown = problemMarkdown,
            questionDocumentSnapshot = CapturedQuestionDocumentCodec.encode(capturedQuestionDocument),
            answerSpecId = answerSpecId,
            answerSpecSnapshot = answerSpecSnapshot,
            answerVerificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
            sourceType = "CURATED_LOCAL_EXAMPLE",
            sourceReference = FIXTURE_VERSION,
            contentFingerprint = CapturedQuestionDocumentFingerprint.of(capturedQuestionDocument),
            createdAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
        )

        fun practiceUnitRecord() = PracticeUnitSeedRecord(
            practiceUnitId = practiceUnitId,
            problemId = problemId,
            problemRevisionId = revisionId,
            unitKey = "whole",
            unitKind = "WHOLE_PROBLEM",
            title = title,
            promptMarkdown = problemMarkdown,
            estimatedSeconds = estimatedSeconds,
            createdAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
        )

        fun errorBookEntryRecord() = ErrorBookEntrySeedRecord(
            entryId = "entry:m1:$slug",
            practiceUnitId = practiceUnitId,
            problemId = problemId,
            currentRevisionId = revisionId,
            sourceKey = "seed:$FIXTURE_VERSION:$slug",
            acceptedAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
            updatedAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
        )

        fun knowledgeNodeRecord() = KnowledgeNodeSeedRecord(
            knowledgeNodeId = knowledgeNodeId,
            stableCode = knowledgeCode,
            subject = subject,
            displayName = knowledgeName,
            parentKnowledgeNodeId = null,
            taxonomyVersion = TAXONOMY_VERSION,
            createdAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
        )

        fun knowledgeBindingRecord() = KnowledgeBindingSeedRecord(
            bindingId = "binding:m1:$slug:$knowledgeCode",
            practiceUnitId = practiceUnitId,
            knowledgeNodeId = knowledgeNodeId,
            basisRevisionId = revisionId,
            strength = 1.0,
            sourceType = "CURATED_VERIFIED",
            taxonomyVersion = TAXONOMY_VERSION,
            acceptedAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
        )

        fun assessmentItemRecord() = AssessmentItemSnapshotSeedRecord(
            assessmentItemSnapshotId = assessmentId,
            itemRevision = 1,
            practiceUnitId = practiceUnitId,
            problemRevisionId = revisionId,
            tutorContentSnapshotId = "teaching:m1:$slug:r1",
            promptMarkdown = problemMarkdown,
            optionsSnapshot = optionsSnapshot,
            answerSpecSnapshot = answerSpecSnapshot,
            verificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
            assessmentEligibility = StudyDbValue.AssessmentEligibility.ATTEMPT_ELIGIBLE,
            scoringMode = StudyDbValue.ScoringMode.AUTO_VERIFIED,
            learnerSnapshotVersion = "seed-no-learning-history",
            projectionCheckpoint = 0L,
            hintLevelAtPresentation = 0,
            answerRevealState = "HIDDEN",
            createdAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
        )

        /** 派生路径读取的题目事实（与旧 fixture 的 artifact/evidence 同源字段）。 */
        fun assessmentRecord() = PracticeUnitAssessmentRecord(
            practiceUnitId = practiceUnitId,
            problemId = problemId,
            problemRevisionId = revisionId,
            subject = subject,
            unitTitle = title,
            promptMarkdown = problemMarkdown,
            questionDocumentSnapshot = CapturedQuestionDocumentCodec.encode(capturedQuestionDocument),
            answerSpecId = answerSpecId,
            answerSpecSnapshot = answerSpecSnapshot,
            answerVerificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
            sourceType = "CURATED_LOCAL_EXAMPLE",
            sourceReference = FIXTURE_VERSION,
            revisionCreatedAtEpochMillis = SEED_CREATED_AT_EPOCH_MILLIS,
        )
    }

    private fun List<Pair<String, String>>.toOptionsSnapshot(): String = joinToString(
        prefix = "{\"schema\":\"choice-options.v1\",\"options\":[",
        postfix = "]}",
    ) { (id, text) ->
        "{\"id\":\"${id.jsonEscaped()}\",\"text\":\"${text.jsonEscaped()}\"}"
    }

    private fun String.jsonEscaped(): String = buildString(length) {
        this@jsonEscaped.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.isISOControl()) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private const val SEED_CREATED_AT_EPOCH_MILLIS = 1_767_225_600_000L
    private const val TUTOR_QUESTION_SLUG = "derivative-sign-change"
}
