package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.data.model.TutorFigureFeedback
import com.tingyun.smartmistakebook.core.data.model.TutorFigureInspector
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.MistakeDetailRecord
import com.tingyun.smartmistakebook.core.database.LibraryCatalogRow
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.TutorToolResultChunk
import com.tingyun.smartmistakebook.core.domain.TutorToolResultStore
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.model.CatalogHit
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeSelection
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end wiring of the four general-agent tools at the runner boundary. */
class TutorAgentToolWiringTest {
    private class FakeResultStore : TutorToolResultStore {
        val values = linkedMapOf<String, String>()
        private var next = 0

        override suspend fun write(scopeKey: String, tool: String, payload: String): String {
            val id = "r-${(++next).toString().padStart(64, '0')}"
            values[id] = payload
            return id
        }

        override suspend fun read(resultId: String, offset: Int, limit: Int): TutorToolResultChunk? {
            val payload = values[resultId] ?: return null
            val end = minOf(payload.length, offset + limit)
            return TutorToolResultChunk(resultId, payload.substring(offset, end), offset, payload.length)
        }

        override suspend fun prune(nowEpochMillis: Long) = Unit
    }

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
    fun aLargeSelectedQuestionDocumentIsOmittedInsteadOfFailingTheWholeToolCall() = runBlocking {
        val longStem = "长题干".repeat(3_000)
        val port = object : FakeStudyDatabasePort() {
            override suspend fun readMistakeDetail(errorBookEntryId: String): MistakeDetailRecord? =
                MistakeDetailRecord(
                    entryId = "entry-big",
                    problemId = "problem-big",
                    problemRevisionId = "revision-big",
                    revisionNumber = 1,
                    subject = "MATH",
                    title = "长题",
                    problemMarkdown = longStem,
                    questionDocumentSnapshot = Json.encodeToString(
                        QuestionDocument.serializer(),
                        QuestionDocument(
                            id = "revision-big",
                            title = "长题",
                            blocks = listOf(ContentBlock.Paragraph("stem", longStem)),
                        ),
                    ),
                    contentFingerprint = "fp",
                    sourceAssets = emptyList(),
                )
        }
        val runner = RoomTutorToolRunner(
            port,
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            null,
        )
        val execution = runner.runTraced(
            TutorToolCall(
                tool = TutorToolName.SELECT_QUESTION,
                rationale = "选择长题",
                parameters = mapOf("entryId" to "entry-big"),
            ),
            context(agent = TutorAgentContext(
                subject = "MATH",
                knownEntryIds = listOf("entry-big"),
            )),
        )

        assertTrue(execution.outcome.summaryMarkdown, execution.outcome.ok)
        val data = requireNotNull(execution.outcome.dataJson)
        assertTrue(data, data.length <= 6_000)
        assertTrue(data, data.contains("\"documentOmitted\":true"))
        assertTrue(data, data.contains("entry-big"))
        assertEquals("entry-big", execution.selectedQuestion?.entryId)
    }

    @Test
    fun appContextStaysWithinTheToolDataBudgetForLargeContexts() = runBlocking {
        val bigQuestion = TutorAgentQuestion(
            entryId = "entry-big",
            problemId = "problem-big",
            problemRevisionId = "revision-big",
            subject = "MATH",
            title = "长题",
            document = QuestionDocument(
                id = "revision-big",
                title = "长题",
                blocks = listOf(ContentBlock.Paragraph("stem", "长题干".repeat(3_000))),
            ),
        )
        val runner = RoomTutorToolRunner(
            FakeStudyDatabasePort(),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            null,
        )
        val execution = runner.runTraced(
            TutorToolCall(TutorToolName.APP_CONTEXT, "读取应用上下文"),
            context(agent = TutorAgentContext(
                subject = "MATH",
                subjects = (1..32).map { "SUBJECT-$it" },
                selectedQuestion = bigQuestion,
                knownEntryIds = (1..180).map { "entry-$it" },
                knownFigureIds = (1..32).map { "figure-$it" },
                backupReferences = (1..20).map {
                    TutorBackupReference("backup-$it", "备份-$it", 10L * it, 100L * it)
                },
            )),
        )

        assertTrue(execution.outcome.summaryMarkdown, execution.outcome.ok)
        val data = requireNotNull(execution.outcome.dataJson)
        assertTrue("data length=${data.length}", data.length <= 6_000)
        assertTrue(data, data.contains("\"documentOmitted\":true"))
        assertFalse(data, data.contains("长题干长题干长题干长题干长题干"))
    }

    @Test
    fun largeToolDataIsKeptAsAnArtifactAndCanBePagedBack() = runBlocking {
        val store = FakeResultStore()
        val runner = RoomTutorToolRunner(
            FakeStudyDatabasePort(),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            resultStore = store,
        )
        val agent = TutorAgentContext(
            subject = "MATH",
            knownEntryIds = (1..180).map { "entry-$it-" + "x".repeat(40) },
            backupReferences = (1..20).map {
                TutorBackupReference("backup-$it", "备份-$it", 10L * it, 100L * it)
            },
        )
        val appContext = runner.runTraced(
            TutorToolCall(TutorToolName.APP_CONTEXT, "读取应用上下文"),
            context(agent = agent),
        )
        val data = requireNotNull(appContext.outcome.dataJson)
        assertTrue(data, data.length <= 6_000)
        val resultId = Regex("\"resultId\":\"([^\"]+)\"").find(data)?.groupValues?.get(1)
        assertNotNull(data, resultId)

        val chunk = runner.runTraced(
            TutorToolCall(
                TutorToolName.READ_RESULT,
                "回读完整应用上下文",
                parameters = mapOf("resultId" to requireNotNull(resultId)),
            ),
            context(agent = agent),
        )
        assertTrue(chunk.outcome.summaryMarkdown, chunk.outcome.ok)
        val chunkData = requireNotNull(chunk.outcome.dataJson)
        assertTrue(chunkData, chunkData.length <= 6_000)
        assertTrue(chunkData, chunkData.contains("knownEntryIds"))
    }

    @Test
    fun reviewPlanReadReturnsTheInjectedReadOnlyFacts() = runBlocking {
        val reviewJson = """{"plan":{"scheduledCount":3,"estimatedSeconds":600},"scheduling":{"dailyReviewMinutes":20}}"""
        val runner = RoomTutorToolRunner(
            FakeStudyDatabasePort(),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
        )
        val execution = runner.runTraced(
            TutorToolCall(TutorToolName.REVIEW_PLAN_READ, "读取今天的复习计划"),
            context(agent = TutorAgentContext(reviewContextJson = reviewJson)),
        )

        assertTrue(execution.outcome.summaryMarkdown, execution.outcome.ok)
        val data = requireNotNull(execution.outcome.dataJson)
        assertTrue(data, data.contains("scheduledCount"))
        assertTrue(data, data.contains("dailyReviewMinutes"))
    }

    @Test
    fun knowledgeCatalogReadReturnsExactNodeIdentitiesForReclassification() = runBlocking {
        val runner = RoomTutorToolRunner(
            FakeStudyDatabasePort(),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            catalogSearch = { query ->
                assertEquals("单调性", query)
                listOf(
                    CatalogHit(
                        node = KnowledgeNodeSelection("node-monotonic", "tax-v7"),
                        subject = "MATH",
                        chapterPath = listOf("必修一", "函数"),
                        name = "函数的单调性",
                        snippet = "用定义判断单调性。",
                        keywords = listOf("单调"),
                    ),
                    CatalogHit(
                        node = KnowledgeNodeSelection("node-other", "tax-v7"),
                        subject = "PHYSICS",
                        chapterPath = listOf("力学"),
                        name = "速度",
                        snippet = "速度是矢量。",
                        keywords = listOf("速度"),
                    ),
                )
            },
        )

        val execution = runner.runTraced(
            TutorToolCall(TutorToolName.KNOWLEDGE_CATALOG_READ, "查单调性的目录身份", terms = listOf("单调性")),
            context(),
        )

        assertTrue(execution.outcome.summaryMarkdown, execution.outcome.ok)
        val summary = execution.outcome.summaryMarkdown
        assertTrue(summary, summary.contains("node-monotonic@tax-v7"))
        assertFalse("科目过滤必须生效：$summary", summary.contains("node-other"))
        val data = requireNotNull(execution.outcome.dataJson)
        assertTrue(data, data.contains("\"taxonomyVersion\":\"tax-v7\""))
        assertEquals(1, execution.resultCount)
    }

    @Test
    fun knowledgeCatalogReadWithoutAWiredCatalogFailsVisibly() = runBlocking {
        val runner = RoomTutorToolRunner(
            FakeStudyDatabasePort(),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
        )

        val execution = runner.runTraced(
            TutorToolCall(TutorToolName.KNOWLEDGE_CATALOG_READ, "查目录", terms = listOf("函数")),
            context(),
        )

        assertFalse(execution.outcome.ok)
        assertEquals("catalog_unavailable", execution.outcome.errorKind)
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
    fun runJavascriptMergesNestedNotebookEntriesAndLetsActionsTargetThem() = runBlocking {
        val port = FakeStudyDatabasePort()
        port.libraryRows += libraryRow("entry-a", "函数一")
        val engine = object : VisualExecutionEngine {
            var calls = 0
            override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
                calls += 1
                return when (calls) {
                    1 -> VisualExecutionResult.Computed(
                        """{"kind":"yield","index":0,"key":"tool:NOTEBOOK_READ:{\"limit\":10,\"terms\":[]}","callType":"tool","name":"NOTEBOOK_READ","args":{"terms":[],"limit":10}}""",
                    )
                    2 -> VisualExecutionResult.Computed(
                        """{"kind":"yield","index":1,"key":"action:SET_FAVORITE:{\"entryId\":\"entry-a\",\"favorite\":\"true\"}","callType":"action","name":"SET_FAVORITE","args":{"entryId":"entry-a","favorite":"true"}}""",
                    )
                    else -> VisualExecutionResult.Computed(
                        """{"kind":"complete","consumed":2,"value":{"ok":true},"stateUpdated":false,"state":null}""",
                    )
                }
            }

            override fun close() = Unit
        }
        val commands = mutableListOf<com.tingyun.smartmistakebook.core.domain.TutorLocalActionCommand>()
        val executor = object : com.tingyun.smartmistakebook.core.domain.TutorLocalActionExecutor {
            override suspend fun execute(
                command: com.tingyun.smartmistakebook.core.domain.TutorLocalActionCommand,
            ): com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome {
                commands += command
                return com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome(
                    kind = command.request.action.actionId,
                    decision = "SUCCEEDED",
                    detail = "ok",
                    ok = true,
                )
            }
        }
        val runner = RoomTutorToolRunner(
            port,
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            null,
            engine,
            executor,
        )

        val execution = runner.runTraced(
            TutorToolCall(
                tool = TutorToolName.RUN_JAVASCRIPT,
                rationale = "检索后收藏",
                script = """
                    const page = agent.callTool('NOTEBOOK_READ', {terms: [], limit: 10});
                    return agent.callAction('SET_FAVORITE', {
                        entryId: page.data.items[0].entryId, favorite: 'true'
                    });
                """.trimIndent(),
            ),
            context(agent = TutorAgentContext(subject = "MATH")),
        )

        assertTrue(execution.outcome.summaryMarkdown, execution.outcome.ok)
        assertEquals(listOf("entry-a"), execution.problemEntryIds)
        assertEquals("entry-a", commands.single().context.libraryProblemId)
        assertEquals(TutorLocalAction.SET_FAVORITE, commands.single().request.action)
    }

    @Test
    fun runJavascriptKeepsNestedFigureGrantsForTheNextModelRound() = runBlocking {
        val grant = ModelEgressAssetGrant("figure-1", "a".repeat(64), 10, 16, 16)
        val inspector = object : TutorFigureInspector {
            override suspend fun inspect(assetId: String) =
                TutorFigureFeedback("""{"assetId":"$assetId"}""", grant)
        }
        val engine = object : VisualExecutionEngine {
            var calls = 0
            override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
                calls += 1
                return when (calls) {
                    1 -> VisualExecutionResult.Computed(
                        """{"kind":"yield","index":0,"key":"tool:READ_FIGURE:{\"assetId\":\"figure-1\"}","callType":"tool","name":"READ_FIGURE","args":{"assetId":"figure-1"}}""",
                    )
                    else -> VisualExecutionResult.Computed(
                        """{"kind":"complete","consumed":1,"value":{"ok":true},"stateUpdated":false,"state":null}""",
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
            null,
            inspector,
        )
        val execution = runner.runTraced(
            TutorToolCall(
                tool = TutorToolName.RUN_JAVASCRIPT,
                rationale = "检查图片",
                script = "return agent.callTool('READ_FIGURE', {assetId: 'figure-1'});",
            ),
            context(agent = TutorAgentContext(subject = "MATH", knownFigureIds = listOf("figure-1"))),
        )

        assertTrue(execution.outcome.summaryMarkdown, execution.outcome.ok)
        assertEquals(listOf(grant), execution.feedbackImages)
        assertEquals(listOf("figure-1"), execution.figureIds)
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
