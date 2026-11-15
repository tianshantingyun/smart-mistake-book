package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.data.model.TutorFigureFeedback
import com.tingyun.smartmistakebook.core.data.model.TutorFigureInspector
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.MistakeDetailRecord
import com.tingyun.smartmistakebook.core.database.LibraryCatalogRow
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.ModelEgressAssetGrant
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorAgentContext
import com.tingyun.smartmistakebook.core.model.TutorAgentQuestion
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorBackupReference
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end wiring of the four general-agent tools at the runner boundary. */
class TutorAgentToolWiringTest {
    private fun context(
        selected: TutorAgentQuestion? = null,
        agent: TutorAgentContext? = null,
    ) = RoomTutorToolRunner.Context(
        subject = selected?.subject ?: agent?.subject ?: "MATH",
        selectedQuestion = selected,
        agentContext = agent,
    )

    private fun libraryRow(entryId: String, title: String) = LibraryCatalogRow(
        entryId = entryId,
        title = title,
        problemMarkdown = "求函数的最值。",
        subject = "MATH",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        nextReviewAtEpochMillis = null,
        retrievability = null,
        problemId = "problem-$entryId",
        problemRevisionId = "revision-$entryId",
        practiceUnitId = "unit-$entryId",
    )

    @Test
    fun selectQuestionReadsOnlyDisclosedEntriesAndReturnsFullContext() = runBlocking {
        val port = object : FakeStudyDatabasePort() {
            override suspend fun readMistakeDetail(errorBookEntryId: String): MistakeDetailRecord? =
                if (errorBookEntryId == "entry-1") MistakeDetailRecord(
                    entryId = "entry-1",
                    problemId = "problem-1",
                    problemRevisionId = "revision-1",
                    revisionNumber = 1,
                    subject = "MATH",
                    title = "二次函数最值",
                    problemMarkdown = "求函数在区间上的最值。",
                    questionDocumentSnapshot = Json.encodeToString(
                        QuestionDocument.serializer(),
                        QuestionDocument(
                            id = "revision-1",
                            title = "二次函数最值",
                            blocks = listOf(ContentBlock.Paragraph("stem", "求函数在区间上的最值。")),
                        ),
                    ),
                    contentFingerprint = "fp",
                    sourceAssets = emptyList(),
                    tutorSessionId = "tutor-session-1",
                ) else null
        }
        val runner = RoomTutorToolRunner(
            port,
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            null,
        )
        val agent = TutorAgentContext(subject = "MATH", subjects = listOf("MATH"), knownEntryIds = listOf("entry-1"))

        val execution = runner.runTraced(
            TutorToolCall(
                tool = TutorToolName.SELECT_QUESTION,
                rationale = "选择本轮检索到的题",
                parameters = mapOf("entryId" to "entry-1"),
            ),
            context(agent = agent),
        )

        assertTrue(execution.outcome.summaryMarkdown, execution.outcome.ok)
        assertEquals("entry-1", execution.selectedQuestion?.entryId)
        assertEquals("tutor-session-1", execution.selectedQuestion?.tutorSessionId)
        assertEquals("二次函数最值", execution.selectedQuestion?.title)
        assertTrue(execution.outcome.dataJson?.contains("problem-1") == true)
    }

    @Test
    fun readFigureOnlyReadsDisclosedAssetsAndReturnsTheGrant() = runBlocking {
        val grant = ModelEgressAssetGrant("figure-1", "a".repeat(64), 10, 16, 16)
        val inspector = object : TutorFigureInspector {
            override suspend fun inspect(assetId: String) = TutorFigureFeedback("""{"assetId":"$assetId"}""", grant)
        }
        val runner = RoomTutorToolRunner(
            FakeStudyDatabasePort(),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            null,
            null,
            null,
            inspector,
        )
        val agent = TutorAgentContext(knownFigureIds = listOf("figure-1"))

        val execution = runner.runTraced(
            TutorToolCall(
                tool = TutorToolName.READ_FIGURE,
                rationale = "检查刚生成的配图",
                parameters = mapOf("assetId" to "figure-1"),
            ),
            context(agent = agent),
        )

        assertTrue(execution.outcome.ok)
        assertEquals(grant, execution.feedbackImage)
    }

    @Test
    fun runJavascriptBridgesThroughARealHostToolAndCommitsStateOnCompletion() = runBlocking {
        val engine = object : VisualExecutionEngine {
            var calls = 0
            override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
                calls += 1
                return if (calls == 1) {
                    VisualExecutionResult.Computed(
                        """{"kind":"yield","index":0,"key":"tool:APP_CONTEXT:{}","callType":"tool","name":"APP_CONTEXT","args":{}}""",
                    )
                } else {
                    VisualExecutionResult.Computed(
                        """{"kind":"complete","consumed":1,"value":{"ok":true},"stateUpdated":true,"state":{"count":1}}""",
                    )
                }
            }

            override fun close() = Unit
        }
        val runner = RoomTutorToolRunner(
            FakeStudyDatabasePort(),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            null,
            engine,
        )

        val execution = runner.runTraced(
            TutorToolCall(
                tool = TutorToolName.RUN_JAVASCRIPT,
                rationale = "读取应用真实上下文",
                script = "return agent.callTool('APP_CONTEXT', {});",
            ),
            context(agent = TutorAgentContext(subject = "MATH")),
        )

        assertTrue(execution.outcome.summaryMarkdown, execution.outcome.ok)
        assertEquals("""{"ok":true}""", execution.outcome.dataJson)
        assertEquals("""{"count":1}""", execution.javascriptState)
    }

    @Test
    fun notebookReadUsesRealPaginationAndReturnsSelectableEntries() = runBlocking {
        val port = FakeStudyDatabasePort()
        port.libraryRows += listOf(
            libraryRow("entry-a", "函数一"),
            libraryRow("entry-b", "函数二"),
            libraryRow("entry-c", "函数三"),
        )
        val runner = RoomTutorToolRunner(port, MutableStateFlow(KnowledgeBaseAvailability.Ready), null)

        val execution = runner.runTraced(
            TutorToolCall(
                tool = TutorToolName.NOTEBOOK_READ,
                rationale = "翻页查看",
                parameters = mapOf("offset" to "1", "limit" to "1"),
            ),
            context(),
        )

        assertTrue(execution.outcome.ok)
        assertEquals(1, execution.resultCount)
        assertEquals(listOf("entry-b"), execution.problemEntryIds)
        assertTrue(execution.outcome.dataJson?.contains("函数二") == true)
    }

    @Test
    fun appContextExposesRealSubjectsAndBackupReferences() = runBlocking {
        val runner = RoomTutorToolRunner(
            FakeStudyDatabasePort(),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            null,
        )
        val agent = TutorAgentContext(
            subject = "MATH",
            subjects = listOf("MATH", "PHYSICS"),
            knownEntryIds = listOf("entry-1"),
            backupReferences = listOf(TutorBackupReference("backup-1", "周五备份", 10, 100)),
        )

        val execution = runner.runTraced(
            TutorToolCall(TutorToolName.APP_CONTEXT, "读取应用上下文"),
            context(agent = agent),
        )

        assertTrue(execution.outcome.ok)
        val data = requireNotNull(execution.outcome.dataJson)
        assertTrue(data.contains("MATH"))
        assertTrue(data.contains("PHYSICS"))
        assertTrue(data.contains("backup-1"))
        assertTrue(data.contains("entry-1"))
    }
}
