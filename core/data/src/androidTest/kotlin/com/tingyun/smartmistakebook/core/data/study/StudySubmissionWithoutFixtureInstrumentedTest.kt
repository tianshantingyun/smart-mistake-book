package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.domain.StudyAnswerRevealRequest
import com.tingyun.smartmistakebook.core.domain.StudyChoiceSubmission
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **D-M M1 功能内核的验收（真 Room，无任何 fixture 源）**：提交/揭示路径不再有
 * `requireTeachingArtifact` 的 fixture 目录门——机器可判题的事实全部从
 * practice unit 记录 + 当前绑定 + 题目字段派生。
 *
 * 两条路径都在这里被钉住：
 * 1. 有绑定：artifact 派生 → 提交判分 → 揭示出讲解（完整链，fixture 不存在于本模块
 *    任何类路径——生产 fixture 系统已删除）；
 * 2. 无绑定：证据归属落 `pseudo:<SUBJECT>` 桶（spec §3.4），提交仍然成功。
 */
@RunWith(AndroidJUnit4::class)
class StudySubmissionWithoutFixtureInstrumentedTest {

    @Test
    fun machineCheckableQuestionCompletesSubmitAndRevealWithoutAnyFixtureSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "m1-no-fixture-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
        )
        try {
            // 「录入」：把这道四选一题按库内事实写入（题面含 ChoiceGroup + VERIFIED 的
            // single-choice.v1 答案规格 + 现役绑定）。
            seedStudyFacts(context, database, databaseName, machineCheckableSeed())
            repository.initialize()

            // 派生目录：没有 fixture 注册表，artifact 仍可从 practice unit 记录读出。
            val artifact = requireNotNull(repository.teachingArtifact(UNIT_ID)) {
                "machine-checkable practice unit must derive a teaching artifact from DB facts"
            }
            val assessmentItem = artifact.assessmentItems.single()
            assertEquals("B", assessmentItem.correctChoiceId)
            assertEquals(4, assessmentItem.choices.size)

            val occurredAt = START_AT
            val submitted = repository.submitChoice(
                StudyChoiceSubmission(
                    requestId = "no-fixture-submit-1",
                    presentationId = "presentation:no-fixture",
                    practiceUnitId = UNIT_ID,
                    selectedChoiceId = "B",
                    responseOrdinal = 1,
                    durationSeconds = 30,
                    occurredAtEpochMillis = occurredAt,
                ),
            )
            assertTrue("提交必须落成一次 attempt", submitted.created)
            assertTrue("选中标准答案必须判对", submitted.isCorrect)

            val reveal = repository.revealAnswer(
                StudyAnswerRevealRequest(
                    requestId = "no-fixture-reveal-1",
                    presentationId = "presentation:no-fixture",
                    practiceUnitId = UNIT_ID,
                    occurredAtEpochMillis = occurredAt + 1_000,
                ),
            )
            assertTrue("揭示必须落成一次 outcome", reveal.created)
            assertTrue(
                "揭示必须给出非空讲解：${reveal.explanationMarkdown}",
                reveal.explanationMarkdown.isNotBlank(),
            )
        } finally {
            repository.close()
            database.close()
            applicationScope.cancel()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun unboundQuestionAttributesItsEvidenceToThePseudoSubjectBucket() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "m1-pseudo-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
        )
        try {
            seedStudyFacts(context, database, databaseName, machineCheckableSeed(includeBinding = false))
            repository.initialize()

            val submitted = repository.submitChoice(
                StudyChoiceSubmission(
                    requestId = "pseudo-submit-1",
                    presentationId = "presentation:pseudo",
                    practiceUnitId = UNIT_ID,
                    selectedChoiceId = "B",
                    responseOrdinal = 1,
                    durationSeconds = 20,
                    occurredAtEpochMillis = START_AT,
                ),
            )
            assertTrue("无绑定的机器可判题也必须能提交", submitted.created)
            assertTrue(submitted.isCorrect)

            // 无绑定 → 派生方物化 pseudo 桶绑定（spec §3.4）；证据快照的归属必须非空
            //（saveAssessmentEvidenceSnapshot 的空归属校验本身就是一层门）。
            val bindings = database.readPracticeUnitKnowledgeBindings(UNIT_ID)
            assertNotNull(
                "无绑定题必须落 pseudo:<SUBJECT> 绑定，实际：$bindings",
                bindings.singleOrNull { it.knowledgeNodeId == "pseudo:MATH" },
            )
        } finally {
            repository.close()
            database.close()
            applicationScope.cancel()
            context.deleteDatabase(databaseName)
        }
    }

    private fun machineCheckableSeed(includeBinding: Boolean = true): StudySeedBundle {
        val blocks = listOf(
            ContentBlock.Paragraph("no-fixture-stem", "下列哪一项是闭区间最值比较的正确做法？"),
            ContentBlock.ChoiceGroup(
                id = "no-fixture-choices",
                promptMarkdown = "请选择一个答案",
                choices = listOf(
                    StructuredChoice(id = "A", markdown = "只比较驻点"),
                    StructuredChoice(id = "B", markdown = "同时比较驻点与端点"),
                    StructuredChoice(id = "C", markdown = "只比较端点"),
                    StructuredChoice(id = "D", markdown = "取端点平均值"),
                ),
            ),
        )
        val document = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "document:no-fixture:r1",
                title = "闭区间最值",
                blocks = blocks,
            ),
            blockEvidence = blocks.map { block ->
                QuestionBlockEvidence(
                    blockId = block.id,
                    sourceAssetId = "no-fixture-source",
                    sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                    writingLayer = WritingLayer.PRINTED,
                    provenance = QuestionBlockProvenance.USER_CORRECTION,
                    reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                )
            },
        )
        return StudySeedBundle(
            problems = listOf(
                ProblemSeedRecord(
                    problemId = "problem:no-fixture",
                    canonicalFingerprint = "fp-no-fixture",
                    subject = "MATH",
                    createdAtEpochMillis = SEED_AT,
                ),
            ),
            revisions = listOf(
                ProblemRevisionSeedRecord(
                    revisionId = "revision:no-fixture",
                    problemId = "problem:no-fixture",
                    revisionNumber = 1,
                    title = "闭区间最值",
                    problemMarkdown = "下列哪一项是闭区间最值比较的正确做法？",
                    questionDocumentSnapshot = CapturedQuestionDocumentCodec.encode(document),
                    answerSpecId = "answer:no-fixture",
                    answerSpecSnapshot = "{\"schema\":\"single-choice.v1\",\"correctChoiceId\":\"B\"}",
                    answerVerificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
                    sourceType = "TEST_LOCAL",
                    sourceReference = null,
                    contentFingerprint = CapturedQuestionDocumentFingerprint.of(document),
                    createdAtEpochMillis = SEED_AT,
                ),
            ),
            practiceUnits = listOf(
                PracticeUnitSeedRecord(
                    practiceUnitId = UNIT_ID,
                    problemId = "problem:no-fixture",
                    problemRevisionId = "revision:no-fixture",
                    unitKey = "whole",
                    unitKind = "WHOLE_PROBLEM",
                    title = "闭区间最值",
                    promptMarkdown = "下列哪一项是闭区间最值比较的正确做法？",
                    estimatedSeconds = 120,
                    createdAtEpochMillis = SEED_AT,
                ),
            ),
            errorBookEntries = listOf(
                ErrorBookEntrySeedRecord(
                    entryId = "entry:no-fixture",
                    practiceUnitId = UNIT_ID,
                    problemId = "problem:no-fixture",
                    currentRevisionId = "revision:no-fixture",
                    sourceKey = "test:no-fixture",
                    acceptedAtEpochMillis = SEED_AT,
                    updatedAtEpochMillis = SEED_AT,
                ),
            ),
            knowledgeBindings = if (includeBinding) {
                listOf(
                    com.tingyun.smartmistakebook.core.database.KnowledgeBindingSeedRecord(
                        bindingId = "binding:no-fixture",
                        practiceUnitId = UNIT_ID,
                        knowledgeNodeId = "knowledge:no-fixture",
                        basisRevisionId = "revision:no-fixture",
                        strength = 1.0,
                        sourceType = "TEST_LOCAL",
                        taxonomyVersion = "test-taxonomy-v1",
                        acceptedAtEpochMillis = SEED_AT,
                    ),
                )
            } else {
                emptyList()
            },
        )
    }

    private companion object {
        const val UNIT_ID = "practice:no-fixture:whole"

        /** 2026-01-10 10:00 +08：远离纪元（04:00 学习日界下纪元附近日序为负）。 */
        const val START_AT = 1_768_010_400_000L
        const val SEED_AT = 1_768_010_000_000L
    }
}
