package com.tingyun.smartmistakebook

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeBindingSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRelationSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
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
import kotlinx.coroutines.runBlocking

/**
 * D-M M1 测试夹具（app androidTest 源集，**不是**生产接缝）：生产 `M1CuratedStudySeed` /
 * `StudyFixtureSource` 随 fixture 系统退场后，UI 场景需要的 curated 内容在这里自给自足。
 *
 * 内容与退场的 debug fixture 逐字等价（同 id / 题面 markdown / 选项 / 答案规格 / 绑定），
 * 但写入走**库文件直写**（跨模块没有可直写的 DAO）：先让 Room 建好 schema（读一次版本号），
 * 再用 `SQLiteDatabase` 以 INSERT OR IGNORE 写行；WAL 下多连接安全。
 */
internal object AppCuratedStudyFixture {
    const val FIXTURE_VERSION = "m1-curated-v1"
    const val TAXONOMY_VERSION = "cn-highschool-m1-v1"

    fun bundle(includeTutorMistake: Boolean = false): StudySeedBundle {
        val questions = curatedQuestions()
        return StudySeedBundle(
            problems = questions.map(QuestionSeed::problemRecord),
            revisions = questions.map(QuestionSeed::revisionRecord),
            practiceUnits = questions.map(QuestionSeed::practiceUnitRecord),
            errorBookEntries = questions.filter { question ->
                question.initiallyInMistakeBook ||
                    (includeTutorMistake && question.slug == "derivative-sign-change")
            }
                .map(QuestionSeed::errorBookEntryRecord),
            knowledgeNodes = questions.map(QuestionSeed::knowledgeNodeRecord),
            knowledgeBindings = questions.map(QuestionSeed::knowledgeBindingRecord),
            relations = listOf(
                ProblemRelationSeedRecord(
                    relationId = "relation:m1:derivative-sign-prerequisite-extrema",
                    sourceProblemId = "problem:m1:math:derivative-sign-change",
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
        )
    }

    private fun curatedQuestions() = listOf(
        QuestionSeed(
            slug = "closed-interval-extrema",
            subject = "MATH",
            title = "闭区间上的函数最值",
            problemMarkdown = """
                已知函数 ${'$'}f(x)=x^3-3x+1${'$'}，求它在闭区间 ${'$'}[-2,2]${'$'} 上的最大值与最小值。
            """.trimIndent(),
            choices = listOf(
                "A" to "最大值为 2，最小值为 -2",
                "B" to "最大值为 3，最小值为 -1",
                "C" to "最大值为 1，最小值为 -3",
                "D" to "最大值为 4，最小值为 -4",
            ),
            correctChoiceId = "B",
            knowledgeCode = "math.derivative.closed_interval_extrema",
            knowledgeName = "利用导数求闭区间最值",
            estimatedSeconds = 240,
            initiallyInMistakeBook = true,
        ),
        QuestionSeed(
            slug = "derivative-sign-change",
            subject = "MATH",
            title = "由导数符号判断单调区间",
            problemMarkdown = """
                设函数 ${'$'}f(x)${'$'} 在实数集上可导，且
                ${'$'}f'(x)=(x-1)(x+2)${'$'}。下列关于 ${'$'}f(x)${'$'} 单调性的判断正确的是哪一项？
            """.trimIndent(),
            choices = listOf(
                "A" to "在 (-∞,-2) 与 (1,+∞) 上递增，在 (-2,1) 上递减",
                "B" to "在 (-∞,-2) 与 (1,+∞) 上递减，在 (-2,1) 上递增",
                "C" to "在 (-∞,1) 上递增，在 (1,+∞) 上递减",
                "D" to "在实数集上始终递增",
            ),
            correctChoiceId = "A",
            knowledgeCode = "math.derivative.monotonicity",
            knowledgeName = "导数符号与函数单调性",
            estimatedSeconds = 150,
            initiallyInMistakeBook = false,
        ),
        QuestionSeed(
            slug = "electromagnetic-direction",
            subject = "PHYSICS",
            title = "运动导体中的电荷偏转方向",
            problemMarkdown = """
                一根竖直金属棒在水平面内向右匀速运动，空间中存在垂直纸面向里的匀强磁场。
                忽略其他作用，达到稳定后金属棒哪一端电势较高？
            """.trimIndent(),
            choices = listOf(
                "A" to "下端电势较高",
                "B" to "上端电势较高",
                "C" to "两端电势始终相等",
                "D" to "两端电势高低周期性交替",
            ),
            correctChoiceId = "B",
            knowledgeCode = "physics.electromagnetism.lorentz_force_direction",
            knowledgeName = "洛伦兹力方向与动生电动势",
            estimatedSeconds = 150,
            initiallyInMistakeBook = true,
        ),
        QuestionSeed(
            slug = "conic-eccentricity",
            subject = "MATH",
            title = "椭圆离心率",
            problemMarkdown = """
                椭圆 ${'$'}\frac{x^2}{25}+\frac{y^2}{9}=1${'$'} 的离心率是多少？
            """.trimIndent(),
            choices = listOf(
                "A" to "3/5",
                "B" to "2/3",
                "C" to "4/5",
                "D" to "5/4",
            ),
            correctChoiceId = "C",
            knowledgeCode = "math.conic.ellipse_eccentricity",
            knowledgeName = "椭圆标准方程与离心率",
            estimatedSeconds = 120,
            initiallyInMistakeBook = true,
        ),
        QuestionSeed(
            slug = "chemical-equilibrium",
            subject = "CHEMISTRY",
            title = "用浓度商判断平衡移动",
            problemMarkdown = """
                恒温下，密闭容器中的反应 ${'$'}N_2O_4(g) \rightleftharpoons 2NO_2(g)${'$'} 已达到平衡。
                瞬间将容器体积压缩为原来的一半。只考虑体积突变后的浓度商与平衡常数，平衡将怎样移动？
            """.trimIndent(),
            choices = listOf(
                "A" to "不移动，因为温度没有改变",
                "B" to "向右移动，因为各物质浓度都增大",
                "C" to "先向右移动，再向左移动",
                "D" to "向左移动，因为突变后 Qc 大于 Kc",
            ),
            correctChoiceId = "D",
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
        val knowledgeCode: String,
        val knowledgeName: String,
        val estimatedSeconds: Int,
        val initiallyInMistakeBook: Boolean,
    ) {
        private val problemId = "problem:m1:${subject.lowercase()}:$slug"
        private val revisionId = "revision:m1:$slug:r1"
        private val practiceUnitId = "practice:m1:$slug:whole"
        private val answerSpecId = "answer:m1:$slug:r1"
        private val knowledgeNodeId = "knowledge:m1:$knowledgeCode"
        private val answerSpecSnapshot =
            "{\"schema\":\"single-choice.v1\",\"correctChoiceId\":\"$correctChoiceId\"}"
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
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private const val SEED_CREATED_AT_EPOCH_MILLIS = 1_767_225_600_000L
}

/**
 * 把 curated 夹具写进 app 的库文件（INSERT OR IGNORE，重复播种 no-op）。
 * 先读一次版本号让 Room 惰性建好 schema，再以第二个连接直写；WAL 下多连接安全。
 */
internal fun seedAppStudyFacts(
    context: Context,
    database: StudyDatabasePort,
    databaseName: String,
    bundle: StudySeedBundle,
) {
    runBlocking { database.readDatabaseVersion() }
    val sqlite = SQLiteDatabase.openDatabase(
        context.getDatabasePath(databaseName).absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE,
    )
    try {
        sqlite.beginTransaction()
        try {
            bundle.problems.forEach { sqlite.insertIgnoring("problem", it.toValues()) }
            bundle.revisions.forEach { sqlite.insertIgnoring("problem_revision", it.toValues()) }
            bundle.practiceUnits.forEach { sqlite.insertIgnoring("practice_unit", it.toValues()) }
            bundle.errorBookEntries.forEach {
                sqlite.insertIgnoring("error_book_entry", it.toValues())
            }
            bundle.knowledgeNodes.forEach { sqlite.insertIgnoring("knowledge_node", it.toValues()) }
            bundle.knowledgeBindings.forEach {
                sqlite.insertIgnoring("practice_unit_knowledge_binding", it.toValues())
            }
            bundle.relations.forEach { sqlite.insertIgnoring("problem_relation", it.toValues()) }
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    } finally {
        sqlite.close()
    }
}

private fun SQLiteDatabase.insertIgnoring(table: String, values: ContentValues) {
    insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE)
}

private fun ProblemSeedRecord.toValues() = ContentValues().apply {
    put("problem_id", problemId)
    put("canonical_fingerprint", canonicalFingerprint)
    put("subject", subject)
    put("created_at_epoch_millis", createdAtEpochMillis)
    putNull("archived_at_epoch_millis")
}

private fun ProblemRevisionSeedRecord.toValues() = ContentValues().apply {
    put("revision_id", revisionId)
    put("problem_id", problemId)
    put("revision_number", revisionNumber)
    put("title", title)
    put("problem_markdown", problemMarkdown)
    put("question_document_snapshot", questionDocumentSnapshot)
    put("answer_spec_id", answerSpecId)
    put("answer_spec_snapshot", answerSpecSnapshot)
    put("answer_verification_status", answerVerificationStatus)
    put("source_type", sourceType)
    put("source_reference", sourceReference)
    put("content_fingerprint", contentFingerprint)
    put("created_at_epoch_millis", createdAtEpochMillis)
}

private fun PracticeUnitSeedRecord.toValues() = ContentValues().apply {
    put("practice_unit_id", practiceUnitId)
    put("problem_id", problemId)
    put("problem_revision_id", problemRevisionId)
    put("unit_key", unitKey)
    put("unit_kind", unitKind)
    put("title", title)
    put("prompt_markdown", promptMarkdown)
    put("estimated_seconds", estimatedSeconds)
    put("created_at_epoch_millis", createdAtEpochMillis)
}

private fun ErrorBookEntrySeedRecord.toValues() = ContentValues().apply {
    put("entry_id", entryId)
    put("practice_unit_id", practiceUnitId)
    put("problem_id", problemId)
    put("current_revision_id", currentRevisionId)
    put("source_key", sourceKey)
    put("status", status)
    put("accepted_at_epoch_millis", acceptedAtEpochMillis)
    put("updated_at_epoch_millis", updatedAtEpochMillis)
    putNull("user_note")
}

private fun KnowledgeNodeSeedRecord.toValues() = ContentValues().apply {
    put("knowledge_node_id", knowledgeNodeId)
    put("stable_code", stableCode)
    put("subject", subject)
    put("display_name", displayName)
    put("canonical_name", canonicalName)
    put("node_kind", nodeKind)
    put("granularity", granularity)
    put("aliases_text", aliases.joinToString("\u001f"))
    put("boundary_markdown", boundaryMarkdown)
    put("verification_status", verificationStatus)
    put("parent_knowledge_node_id", parentKnowledgeNodeId)
    put("taxonomy_version", taxonomyVersion)
    put("created_at_epoch_millis", createdAtEpochMillis)
}

private fun KnowledgeBindingSeedRecord.toValues() = ContentValues().apply {
    put("binding_id", bindingId)
    put("practice_unit_id", practiceUnitId)
    put("knowledge_node_id", knowledgeNodeId)
    put("basis_revision_id", basisRevisionId)
    put("strength", strength)
    put("source_type", sourceType)
    put("taxonomy_version", taxonomyVersion)
    put("accepted_at_epoch_millis", acceptedAtEpochMillis)
}

private fun ProblemRelationSeedRecord.toValues() = ContentValues().apply {
    put("relation_id", relationId)
    put("source_problem_id", sourceProblemId)
    put("target_problem_id", targetProblemId)
    put("relation_type", relationType)
    put("status", status)
    put("source_basis_revision_id", sourceBasisRevisionId)
    put("target_basis_revision_id", targetBasisRevisionId)
    put("confidence", confidence)
    put("created_at_epoch_millis", createdAtEpochMillis)
    put("updated_at_epoch_millis", updatedAtEpochMillis)
}
