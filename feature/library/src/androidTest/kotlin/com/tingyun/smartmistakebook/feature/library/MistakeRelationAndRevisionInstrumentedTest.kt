package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.ArchivedMistakeRef
import com.tingyun.smartmistakebook.core.domain.ConfirmedMistakeOrganization
import com.tingyun.smartmistakebook.core.domain.ConfirmedProblemRelation
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationPreparation
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationRepository
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionChange
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionChangeKind
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionReceipt
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionSummary
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.ProblemOrganizationConfirmation
import com.tingyun.smartmistakebook.core.domain.ProblemOrganizationSelection
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemCommand
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemResult
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentFingerprint
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ProblemRelationKind
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import com.tingyun.smartmistakebook.core.domain.MistakeEditRepository
import com.tingyun.smartmistakebook.core.domain.MistakeEditJob
import com.tingyun.smartmistakebook.core.domain.MistakeEditRequest
import com.tingyun.smartmistakebook.core.model.EditBasis
import com.tingyun.smartmistakebook.core.model.MistakeEditJobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MistakeRelationAndRevisionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmedRelationExplainsTrustAndOpensOnlyTheExactLocalRevision() {
        var openedEntryId: String? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(revisionNumber = 2, revisionId = "revision-2"),
                    onBack = {},
                    onExport = {},
                    organizationRepository = confirmedRelationRepository(),
                    modelTasks = NoOpModelTasks,
                    profile = StudyProfileOverview(),
                    catalogEntries = listOf(catalogEntry("entry-2", "problem-2", "revision-2")),
                    onOpenRelatedMistake = { openedEntryId = it },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_confirmed_relation_0").performScrollTo()
        composeRule.onNodeWithText("这题是它的变式").assertExists()
        composeRule.onNodeWithText("二次函数变式").assertExists()
        composeRule.onNodeWithText("点按打开").assertExists()
        composeRule.onNodeWithTag("mistake_confirmed_relation_0").performClick()

        assertEquals("entry-2", openedEntryId)
    }

    @Test
    fun relationToAnOlderRevisionIsShownAsStaleInsteadOfSilentlyRedirected() {
        var openedEntryId: String? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(revisionNumber = 2, revisionId = "revision-2"),
                    onBack = {},
                    onExport = {},
                    organizationRepository = confirmedRelationRepository(),
                    modelTasks = NoOpModelTasks,
                    profile = StudyProfileOverview(),
                    catalogEntries = listOf(catalogEntry("entry-2", "problem-2", "revision-3")),
                    onOpenRelatedMistake = { openedEntryId = it },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_confirmed_relation_0").performScrollTo()
        composeRule.onNodeWithText("目标题面已更新，请重新整理关系").assertExists()

        assertNull(openedEntryId)
    }

    @Test
    fun versionHistoryCanSelectAnImmutableOlderRevision() {
        var selectedRevisionId: String? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(revisionNumber = 2, revisionId = "revision-2"),
                    onBack = {},
                    onExport = {},
                    revisionHistory = revisionHistory(),
                    onSelectRevision = { selectedRevisionId = it.problemRevisionId },
                )
            }
        }

        composeRule.onNodeWithText("正在查看第 2 版 · 当前").assertExists()
        composeRule.onNodeWithTag("mistake_revision_history_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_revision_1").performScrollTo().performClick()

        assertEquals("revision-1", selectedRevisionId)
    }

    @Test
    fun historicalRevisionIsReadOnlyAndHasAnObviousWayBackToCurrent() {
        var selectedRevisionId: String? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(revisionNumber = 1, revisionId = "revision-1"),
                    onBack = {},
                    onExport = {},
                    revisionHistory = revisionHistory(),
                    selectedRevisionId = "revision-1",
                    onSelectRevision = { selectedRevisionId = it.problemRevisionId },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_start_tutor").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_detail_historical_notice").assertExists()
        composeRule.onNodeWithText("导出这一版 A4").assertExists()
        composeRule.onNodeWithTag("mistake_detail_return_current_revision")
            .performScrollTo()
            .performClick()

        assertEquals("revision-2", selectedRevisionId)
    }

    @Test
    fun currentRevisionOffersEdit() {
        var editRequested = 0
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(revisionNumber = 2, revisionId = "revision-2"),
                    onBack = {},
                    onExport = {},
                    onEditRequested = { editRequested += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_edit").performScrollTo().performClick()
        assertEquals(1, editRequested)
    }

    @Test
    fun historicalRevisionStaysReadOnlyWithoutEditEntry() {
        var editRequested = 0
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(revisionNumber = 1, revisionId = "revision-1"),
                    onBack = {},
                    onExport = {},
                    revisionHistory = revisionHistory(),
                    selectedRevisionId = "revision-1",
                    onEditRequested = { editRequested += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_edit").assertDoesNotExist()
        assertEquals(0, editRequested)
    }

    @Test
    fun detailEditEntryOpensTheEditorOnTheCurrentRevision() {
        val repository = EditableDetailRepository(
            state = readyState(revisionNumber = 2, revisionId = "revision-2"),
            reviseResult = savedResult(),
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailRoute(
                    errorBookEntryId = "entry-1",
                    repository = repository,
                    organizationRepository = confirmedRelationRepository(),
                    modelTasks = NoOpModelTasks,
                    profile = StudyProfileOverview(),
                    catalogEntries = listOf(catalogEntry("entry-1", "problem-1", "revision-2")),
                    onBack = {},
                    onExport = {},
                    onTutor = {},
                    onOpenRelatedMistake = {},
                    onOpenModelSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_edit").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_revision_edit_screen").assertExists()
        composeRule.onNodeWithTag("mistake_revision_edit_title").assertExists()
        composeRule.onNodeWithTag("mistake_revision_edit_save").assertExists()
    }

    @Test
    fun editorShowsDiffAndSavesAgainstTheExpectedRevision() {
        val repository = EditableDetailRepository(
            state = readyState(revisionNumber = 2, revisionId = "revision-2"),
            reviseResult = savedResult(),
        )
        var done = false
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeRevisionEditRoute(
                    entryId = "entry-1",
                    repository = repository,
                    onBack = {},
                    onDone = { done = true },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_revision_edit_title")
            .performTextReplacement("更正后的标题")
        composeRule.onNodeWithTag("mistake_revision_edit_diff").assertExists()
        composeRule.onNodeWithTag("mistake_revision_edit_save").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { repository.lastCommand != null }

        val command = checkNotNull(repository.lastCommand)
        assertEquals("revision-2", command.expectedRevisionId)
        assertEquals(2, command.expectedRevisionNumber)
        assertEquals("更正后的标题", command.questionDocument.document.title)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(
                    "已保存第 3 版；旧版本与历史作答仍可查看。",
                ).assertExists()
            }.isSuccess
        }
        composeRule.onNodeWithTag("mistake_revision_edit_done").assertIsDisplayed().performClick()
        assertTrue(done)
    }

    @Test
    fun editorConflictKeepsTheDraftAndOffersReload() {
        val repository = EditableDetailRepository(
            state = readyState(revisionNumber = 2, revisionId = "revision-2"),
            reviseResult = ReviseMistakeProblemResult.Conflict(
                expectedRevisionId = "revision-2",
                expectedRevisionNumber = 2,
                currentRevisionId = "revision-3",
                currentRevisionNumber = 3,
                currentRevisionTitle = "别处更新后的题",
            ),
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeRevisionEditRoute(
                    entryId = "entry-1",
                    repository = repository,
                    onBack = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithTag("mistake_revision_edit_title")
            .performTextReplacement("我的未保存草稿")
        composeRule.onNodeWithTag("mistake_revision_edit_save").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { repository.lastCommand != null }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mistake_revision_edit_conflict").assertExists()
        composeRule.onNodeWithTag("mistake_revision_edit_reload").assertExists()
        composeRule.onNodeWithTag("mistake_revision_edit_title")
            .assertTextContains("我的未保存草稿")
    }

    @Test
    fun adoptedObserverCannotCloseThePreviewBeforeLocalDraftReplacement() {
        val baseline = readyState(2, "revision-2")
        val repository = EditableDetailRepository(baseline, savedResult())
        val receipt = CompletableDeferred<Unit>()
        val jobs = MutableStateFlow<MistakeEditJob?>(null)
        val edits = object : MistakeEditRepository {
            override suspend fun latestForBasis(basis: EditBasis): MistakeEditJob {
                val source = baseline.questionDocument
                return MistakeEditJob("mistake-edit:test", basis, MistakeEditJobStatus.READY,
                    sourceDraft = source, preparedDraft = source.copy(document = source.document.copy(title = "智能体改后的标题")))
                    .also { jobs.value = it }
            }
            override fun observe(operationId: String) = jobs
            override suspend fun readySuggestion(operationId: String, currentBasis: EditBasis) = jobs.value?.takeIf { it.basis == currentBasis }
            override suspend fun markAdopted(operationId: String, currentBasis: EditBasis): Boolean {
                jobs.value = jobs.value!!.copy(status = MistakeEditJobStatus.ADOPTED)
                receipt.await()
                return true
            }
            override suspend fun requestEdit(request: MistakeEditRequest, agentConsentGranted: Boolean) = error("Must reuse ready preview")
            override suspend fun cancel(operationId: String) = Unit
            override suspend fun retry(operationId: String, agentConsentGranted: Boolean) = Unit
            override suspend fun resumePendingJobs(agentConsentGranted: Boolean) = Unit
        }
        composeRule.setContent { SmartMistakeBookTheme {
            MistakeRevisionEditRoute("entry-1", repository, {}, {}, agentEdits = edits, modelEgressAllowed = true)
        } }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("采用到草稿").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("采用到草稿").performClick()
        composeRule.waitUntil(5_000) { jobs.value?.status == MistakeEditJobStatus.ADOPTED }
        composeRule.onNodeWithText("正在采用到草稿…").assertIsDisplayed()
        assertNull(repository.lastCommand)
        receipt.complete(Unit)
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("智能体修改", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        composeRule.onNode(hasText("智能体改后的标题") and hasAnyAncestor(hasTestTag("mistake_revision_edit_preview"))).assertExists()
        composeRule.onNodeWithTag("mistake_revision_edit_manual_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_revision_edit_title").performScrollTo().assertTextContains("智能体改后的标题")
        assertNull(repository.lastCommand)
        composeRule.onNodeWithTag("mistake_revision_edit_save").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) { repository.lastCommand != null }
        assertEquals("智能体改后的标题", repository.lastCommand!!.questionDocument.document.title)
    }

    @Test
    fun paragraphOnlyEditorOpensAgentWithTargetsAndPreviewsUnsavedManualDraft() {
        val repository = EditableDetailRepository(readyState(2, "revision-2"), savedResult())
        val edits = object : MistakeEditRepository {
            override suspend fun latestForBasis(basis: EditBasis): MistakeEditJob? = null
            override fun observe(operationId: String): Flow<MistakeEditJob?> = flowOf(null)
            override suspend fun readySuggestion(operationId: String, currentBasis: EditBasis): MistakeEditJob? = null
            override suspend fun markAdopted(operationId: String, currentBasis: EditBasis) = false
            override suspend fun requestEdit(request: MistakeEditRequest, agentConsentGranted: Boolean): String = error("No model calls expected")
            override suspend fun cancel(operationId: String) = Unit
            override suspend fun retry(operationId: String, agentConsentGranted: Boolean) = Unit
            override suspend fun resumePendingJobs(agentConsentGranted: Boolean) = Unit
        }
        composeRule.setContent { SmartMistakeBookTheme {
            MistakeRevisionEditRoute("entry-1", repository, {}, {}, agentEdits = edits, modelEgressAllowed = true)
        } }
        composeRule.onNodeWithText("修改范围 · 已选 1/32 块 · 展开").assertExists()
        composeRule.onNodeWithContentDescription("关闭对话框").performClick()
        composeRule.onNodeWithTag("mistake_revision_edit_block_0").assertDoesNotExist()
        composeRule.onNode(hasText("求函数最值。") and hasAnyAncestor(hasTestTag("mistake_revision_edit_preview"))).assertExists()
        composeRule.onNodeWithTag("mistake_revision_edit_manual_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_revision_edit_block_0").performScrollTo().performTextReplacement("证明函数存在最大值。")
        composeRule.onNodeWithTag("mistake_revision_edit_manual_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_revision_edit_block_0").assertDoesNotExist()
        composeRule.onNode(hasText("证明函数存在最大值。") and hasAnyAncestor(hasTestTag("mistake_revision_edit_preview"))).assertExists()
        assertNull(repository.lastCommand)
        composeRule.onNodeWithTag("mistake_revision_edit_save").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) { repository.lastCommand != null }
        assertEquals("证明函数存在最大值。", (repository.lastCommand!!.questionDocument.document.blocks.single() as ContentBlock.Paragraph).markdown)
    }

    private fun savedResult(): ReviseMistakeProblemResult.Saved {
        val edited = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "question-revision-3",
                title = "更正后的标题",
                blocks = listOf(ContentBlock.Paragraph("stem", "更正后的题干。")),
            ),
            blockEvidence = listOf(
                QuestionBlockEvidence(
                    blockId = "stem",
                    sourceAssetId = "",
                    sourceRegion = null,
                    writingLayer = WritingLayer.UNKNOWN,
                    provenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
                    confidence = null,
                    reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    producerVersion = null,
                ),
            ),
        )
        return ReviseMistakeProblemResult.Saved(
            receipt = MistakeRevisionReceipt(
                entryId = "entry-1",
                problemId = "problem-1",
                problemRevisionId = "revision-3",
                revisionNumber = 3,
                previousRevisionId = "revision-2",
                previousRevisionNumber = 2,
                title = "更正后的标题",
                contentFingerprint = CapturedQuestionDocumentFingerprint.of(edited),
                createdAtEpochMillis = 3_000,
                changes = listOf(
                    MistakeRevisionChange(
                        kind = MistakeRevisionChangeKind.TITLE,
                        label = "标题",
                        before = "函数最值（第 2 版）",
                        after = "更正后的标题",
                    ),
                ),
            ),
            created = true,
        )
    }

    private inner class EditableDetailRepository(
        private val state: MistakeDetailState.Ready,
        private val reviseResult: ReviseMistakeProblemResult,
    ) : MistakeDetailRepository {
        var lastCommand: ReviseMistakeProblemCommand? = null

        override fun observe(errorBookEntryId: String): Flow<MistakeDetailState> = flowOf(state)
        override fun observeExact(key: MistakeRevisionKey): Flow<MistakeDetailState> = flowOf(state)
        override suspend fun readExact(key: MistakeRevisionKey): MistakeDetailState = state
        override fun observeRevisionHistory(
            errorBookEntryId: String,
        ): Flow<List<MistakeRevisionSummary>> = flowOf(revisionHistory())
        override suspend fun updateUserNote(
            entryId: String,
            note: String?,
            updatedAtEpochMillis: Long,
        ): Boolean = true
        override suspend fun setFavorite(
            entryId: String,
            isFavorite: Boolean,
            updatedAtEpochMillis: Long,
        ): Boolean = true
        override suspend fun archiveEntry(entryId: String, at: Long): Boolean = true
        override suspend fun restoreEntry(entryId: String, at: Long): Boolean = true
        override fun observeArchived(): Flow<List<ArchivedMistakeRef>> = flowOf(emptyList())
        override suspend fun reviseProblem(
            command: ReviseMistakeProblemCommand,
        ): ReviseMistakeProblemResult {
            lastCommand = command
            return reviseResult
        }
    }

    private fun confirmedRelationRepository(): MistakeOrganizationRepository =
        object : MistakeOrganizationRepository {
            override suspend fun prepare(
                key: MistakeRevisionKey,
                profile: StudyProfileOverview,
                provider: ProviderCapabilitySnapshot,
                attempt: Int,
                occurredAtEpochMillis: Long,
                approvedAtEpochMillis: Long,
            ): MistakeOrganizationPreparation = error("Confirmed data must not prepare a new request")

            override fun observeConfirmed(key: MistakeRevisionKey): Flow<ConfirmedMistakeOrganization> =
                flowOf(
                    ConfirmedMistakeOrganization(
                        relations = listOf(
                            ConfirmedProblemRelation(
                                targetProblemId = "problem-2",
                                targetProblemRevisionId = "revision-2",
                                kind = ProblemRelationKind.VARIANT_OF,
                                confidence = 0.87,
                            ),
                        ),
                    ),
                )

            override suspend fun correctConfirmedOrganization(
                key: MistakeRevisionKey,
                selection: ProblemOrganizationSelection,
                correctedAtEpochMillis: Long,
            ): ProblemOrganizationConfirmation = error("not used in this test")

            override fun observeOrganizationOptions(
                key: MistakeRevisionKey,
            ): Flow<com.tingyun.smartmistakebook.core.domain.MistakeOrganizationOptions> =
                flowOf(com.tingyun.smartmistakebook.core.domain.MistakeOrganizationOptions("", emptyList(), emptyList()))

            override suspend fun applySuccessfulOrganization(
                requestId: String,
            ): ProblemOrganizationConfirmation = error("Confirmed data must not be applied again")

            override suspend fun confirm(
                requestId: String,
                selection: ProblemOrganizationSelection,
                acceptedAtEpochMillis: Long,
            ): ProblemOrganizationConfirmation = error("Confirmed data must not be saved again")
        }

    private fun readyState(revisionNumber: Int, revisionId: String) = MistakeDetailState.Ready(
        detail = MistakeDetail(
            identity = MistakeDetailIdentity(
                errorBookEntryId = "entry-1",
                problemId = "problem-1",
                problemRevisionId = revisionId,
                revisionNumber = revisionNumber,
                title = "函数最值（第 $revisionNumber 版）",
                subject = "MATH",
            ),
            fallbackMarkdown = "求函数最值。",
            source = MistakeSourceSet.Missing,
        ),
        questionDocument = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "question-$revisionId",
                blocks = listOf(ContentBlock.Paragraph("paragraph-$revisionId", "求函数最值。")),
            ),
            blockEvidence = listOf(QuestionBlockEvidence("paragraph-$revisionId", "", null, WritingLayer.UNKNOWN,
                QuestionBlockProvenance.USER_TRANSCRIPTION, null, QuestionBlockReviewStatus.USER_CONFIRMED, null)),
        ),
    )

    private fun revisionHistory() = listOf(
        MistakeRevisionSummary(
            entryId = "entry-1",
            problemId = "problem-1",
            problemRevisionId = "revision-2",
            revisionNumber = 2,
            title = "函数最值（校对版）",
            createdAtEpochMillis = 1_752_988_800_000,
            isCurrent = true,
        ),
        MistakeRevisionSummary(
            entryId = "entry-1",
            problemId = "problem-1",
            problemRevisionId = "revision-1",
            revisionNumber = 1,
            title = "函数最值（初版）",
            createdAtEpochMillis = 1_752_902_400_000,
            isCurrent = false,
        ),
    )

    private fun catalogEntry(entryId: String, problemId: String, revisionId: String) =
        StudyCatalogEntry(
            entryId = entryId,
            problemId = problemId,
            problemRevisionId = revisionId,
            practiceUnitId = "practice-$entryId",
            subject = "MATH",
            title = "二次函数变式",
            problemMarkdown = "讨论参数范围。",
            sourceKey = null,
            isCuratedExample = false,
            nextReviewAtEpochMillis = null,
            retrievability = null,
        )

    private object NoOpModelTasks : ModelTaskRepository {
        override suspend fun capabilities() = ProviderCapabilitySnapshot(
            providerId = "provider",
            providerDisplayName = "测试模型",
            modelId = "model-v1",
            supportedTasks = setOf(ModelTaskKind.PROBLEM_CLASSIFY),
            supportsImageInput = true,
            supportsStructuredOutput = true,
            supportsStreaming = false,
            providerConfigurationVersion = "config-v1",
        )

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> = flowOf(emptyList())

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> =
            error("Confirmed data must not execute another model request")
    }
}
