package com.tingyun.smartmistakebook.feature.library

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.ConfirmedMistakeOrganization
import com.tingyun.smartmistakebook.core.domain.ConfirmedProblemRelation
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationPreparation
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationOptions
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationRepository
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.OrganizationOption
import com.tingyun.smartmistakebook.core.domain.ProblemOrganizationConfirmation
import com.tingyun.smartmistakebook.core.domain.ProblemOrganizationSelection
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.model.AtomicKnowledgeSuggestion
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ClassificationDimension
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.ModelEgressManifest
import com.tingyun.smartmistakebook.core.model.ModelEgressPurpose
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelPromptPolicyVersions
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProblemClassificationSuggestion
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationInput
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationOutput
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationPlan
import com.tingyun.smartmistakebook.core.model.ProblemRelationKind
import com.tingyun.smartmistakebook.core.model.ProblemStepKnowledgeAttribution
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.RelatedProblemCandidate
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MistakeOrganizationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun studentSeesExactDisclosureAndOrganizationStartsAutomatically() {
        val organization = FakeOrganizationRepository()
        var executeCalls = 0
        val countingTasks = object : ModelTaskRepository by FakeModelTasks {
            override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> {
                executeCalls += 1
                // 保持等待、不发射快照：本用例只验证"自动发起"，成败无关。
                return flow { }
            }
        }
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = countingTasks,
                    profile = StudyProfileOverview(),
                )
            }
        }

        // 披露文案仍然展示；整理与拍照/讲题同口径：不再需要点"同意"，
        // 准备完成即自动发起（发起即发送）。
        waitForTag("mistake_organization_consent")
        composeRule.captureLibraryQaScreenshot("mistake-organization-consent-current.png")

        composeRule.onNodeWithText("确认本次发送范围").assertExists()
        composeRule.onNodeWithText(
            "会把当前题面、1 道同科目题面发给测试模型，只用于整理板块、细化知识点和题目关系；不发送原图或学习记录。",
        ).assertExists()
        composeRule.onNodeWithText("API 密钥", substring = true).assertDoesNotExist()
        // 无需任何点击：模型调用被自动发起。
        composeRule.waitUntil(timeoutMillis = 20_000) {
            executeCalls >= 1
        }
    }

    @Test
    fun successfulResultIsAppliedWithoutCheckboxesAndEditorOpensOnlyOnRequest() {
        val organization = FakeOrganizationRepository()
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = successfulModelTasks(),
                    profile = StudyProfileOverview(),
                )
            }
        }

        // 整理自动发起，无需点击"同意"。
        waitForTag("mistake_organization_applied")

        assertEquals("organization-test-0", organization.appliedRequestId)
        assertEquals(1, organization.prepareCallCount)
        assertNull(organization.lastSelection)
        composeRule.onNodeWithText("按选择保存").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_classification_0").assertDoesNotExist()
        composeRule.onNodeWithText("已整理").assertExists()
        composeRule.onNodeWithText("板块：函数").assertExists()
        composeRule.onNodeWithText("知识点：二次函数最值").assertExists()

        composeRule.onNodeWithTag("mistake_organization_correct_toggle")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("mistake_classification_0").assertExists()
        composeRule.onNodeWithTag("mistake_add_classification_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_custom_label").performScrollTo()
            .performTextInput("导数零点与单调区间")
        composeRule.onNodeWithTag("mistake_custom_add").performScrollTo().performClick()
        composeRule.onNodeWithText("你补充的", substring = true).assertExists()
        composeRule.onNodeWithTag("mistake_organization_confirm").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            organization.lastSelection != null
        }

        val correction = requireNotNull(organization.lastSelection).userClassifications.single()
        assertEquals(ClassificationDimension.KNOWLEDGE, correction.dimension)
        assertEquals("导数零点与单调区间", correction.displayName)
    }

    @Test
    fun rotationKeepsExternalPendingTaskPausedUntilOneNewConfirmation() {
        val organization = FakeOrganizationRepository()
        val persistedRequest = organizationRequest(
            requestId = "organization-rotation-pending",
            occurredAtEpochMillis = 10_000L,
            approvedAtEpochMillis = 10_500L,
        )
        val modelTasks = DurableOrganizationModelTasks(
            initialTask = pendingSnapshot(persistedRequest),
        )
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = modelTasks,
                    profile = StudyProfileOverview(),
                )
            }
        }

        waitForTag("mistake_organization_paused")
        assertTrue(modelTasks.executedRequests.isEmpty())

        restorationTester.emulateSavedInstanceStateRestore()
        waitForTag("mistake_organization_paused")
        assertTrue(modelTasks.executedRequests.isEmpty())
        composeRule.onNodeWithTag("mistake_organization_continue").performClick()
        waitForTag("mistake_organization_applied")

        assertEquals(0, organization.prepareCallCount)
        assertRenewedExternalRequest(persistedRequest, modelTasks.executedRequests.single())
    }

    @Test
    fun reentryHydratesExistingSucceededTaskWithoutPreparingOrExecutingAgain() {
        val organization = FakeOrganizationRepository()
        val persistedRequest = organizationRequest(
            requestId = "organization-existing-success",
            occurredAtEpochMillis = 12_345L,
            approvedAtEpochMillis = 12_999L,
        )
        val modelTasks = DurableOrganizationModelTasks(
            initialTask = successfulSnapshot(persistedRequest),
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = modelTasks,
                    profile = StudyProfileOverview(),
                )
            }
        }

        waitForTag("mistake_organization_applied")

        assertEquals(0, organization.prepareCallCount)
        assertTrue(modelTasks.executedRequests.isEmpty())
        assertEquals(persistedRequest.requestId, organization.appliedRequestId)
        composeRule.onNodeWithTag("mistake_organization_consent").assertDoesNotExist()
    }

    @Test
    fun reentryRequiresOneNewConfirmationBeforeResumingExternalPendingTask() {
        val organization = FakeOrganizationRepository()
        val persistedRequest = organizationRequest(
            requestId = "organization-existing-pending",
            occurredAtEpochMillis = 23_456L,
            approvedAtEpochMillis = 23_999L,
        )
        val modelTasks = DurableOrganizationModelTasks(
            initialTask = pendingSnapshot(persistedRequest),
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = modelTasks,
                    profile = StudyProfileOverview(),
                )
            }
        }

        waitForTag("mistake_organization_paused")

        assertEquals(0, organization.prepareCallCount)
        assertTrue(modelTasks.executedRequests.isEmpty())
        composeRule.onNodeWithText("整理已暂停").assertExists()
        composeRule.onNodeWithTag("mistake_organization_continue").performClick()
        waitForTag("mistake_organization_applied")

        val resumedRequest = modelTasks.executedRequests.single()
        assertRenewedExternalRequest(persistedRequest, resumedRequest)
        assertEquals(resumedRequest.requestId, organization.appliedRequestId)
        composeRule.onNodeWithTag("mistake_organization_paused").assertDoesNotExist()
    }

    @Test
    fun reentryRequiresOneNewConfirmationBeforeRetryingExternalFailure() {
        val organization = FakeOrganizationRepository()
        val persistedRequest = organizationRequest(
            requestId = "organization-existing-retryable",
            occurredAtEpochMillis = 24_456L,
            approvedAtEpochMillis = 24_999L,
        )
        val modelTasks = DurableOrganizationModelTasks(
            initialTask = retryableSnapshot(persistedRequest),
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = modelTasks,
                    profile = StudyProfileOverview(),
                )
            }
        }

        waitForTag("mistake_organization_paused")
        assertTrue(modelTasks.executedRequests.isEmpty())
        composeRule.onNodeWithText("整理已暂停").assertExists()
        composeRule.onNodeWithTag("mistake_organization_continue").performClick()
        waitForTag("mistake_organization_applied")

        assertRenewedExternalRequest(persistedRequest, modelTasks.executedRequests.single())
    }

    @Test
    fun reentryAutomaticallyResumesLocalPendingTaskWithItsExactRequest() {
        val organization = FakeOrganizationRepository()
        val persistedRequest = organizationRequest(
            requestId = "organization-local-pending",
            occurredAtEpochMillis = 34_567L,
            approvedAtEpochMillis = 34_567L,
        ).copy(egressManifest = null)
        val localProvider = PROVIDER.copy(executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS)
        val modelTasks = DurableOrganizationModelTasks(
            initialTask = pendingSnapshot(persistedRequest),
            provider = localProvider,
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = modelTasks,
                    profile = StudyProfileOverview(),
                )
            }
        }

        waitForTag("mistake_organization_applied")

        assertEquals(0, organization.prepareCallCount)
        assertEquals(listOf(persistedRequest), modelTasks.executedRequests)
        composeRule.onNodeWithTag("mistake_organization_paused").assertDoesNotExist()
    }

    @Test
    fun incompleteResultKeepsTheScreenToOneRetryAction() {
        val organization = FakeOrganizationRepository(automaticApplied = false)
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = successfulModelTasks(),
                    profile = StudyProfileOverview(),
                )
            }
        }

        // 整理自动发起，无需点击"同意"。
        waitForTag("mistake_organization_incomplete")

        composeRule.onNodeWithText("暂未整理完整").assertExists()
        composeRule.onNodeWithTag("mistake_organization_retry").assertExists()
        composeRule.onNodeWithTag("mistake_organization_correct_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_classification_0").assertDoesNotExist()
    }

    @Test
    fun capabilityFailureOffersWorkingRetryAndSettingsActions() {
        val organization = FakeOrganizationRepository()
        var capabilityCalls = 0
        var settingsOpened = false
        val recoveringTasks = object : ModelTaskRepository by FakeModelTasks {
            override suspend fun capabilities(): ProviderCapabilitySnapshot {
                capabilityCalls += 1
                if (capabilityCalls == 1) error("temporary capability failure")
                return PROVIDER
            }
        }
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = recoveringTasks,
                    profile = StudyProfileOverview(),
                    onOpenModelSettings = { settingsOpened = true },
                )
            }
        }

        waitForTag("mistake_organization_capability_failure")
        composeRule.onNodeWithText("检查模型设置").performClick()
        assertTrue(settingsOpened)
        composeRule.onNodeWithText("重试").performClick()
        waitForTag("mistake_organization_consent")
        assertEquals(2, capabilityCalls)
    }

    @Test
    fun localApplyRetryReusesSuccessfulRequestWithoutExecutingModelAgain() {
        val organization = FakeOrganizationRepository(applyFailuresBeforeSuccess = 1)
        val delegate = successfulModelTasks()
        var executeCalls = 0
        val countingTasks = object : ModelTaskRepository by delegate {
            override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> {
                executeCalls += 1
                return delegate.execute(request)
            }
        }
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = countingTasks,
                    profile = StudyProfileOverview(),
                )
            }
        }

        // 整理自动发起，无需点击"同意"。
        waitForTag("mistake_organization_apply_failure")
        composeRule.onNodeWithTag("mistake_organization_apply_retry").performClick()
        waitForTag("mistake_organization_applied")

        assertEquals(1, executeCalls)
        assertEquals(2, organization.applyCallCount)
        assertEquals("organization-test-0", organization.appliedRequestId)
    }

    @Test
    fun plannedRemovalOfExistingRelationCanBeUndoneBeforeSave() {
        val organization = FakeOrganizationRepository(
            relationsAfterApply = listOf(
                ConfirmedProblemRelation(
                    targetProblemId = "problem-2",
                    targetProblemRevisionId = "revision-2",
                    kind = ProblemRelationKind.VARIANT_OF,
                    confidence = 0.95,
                ),
            ),
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = successfulModelTasks(),
                    profile = StudyProfileOverview(),
                )
            }
        }

        // 整理自动发起，无需点击"同意"。
        waitForTag("mistake_organization_applied")
        composeRule.onNodeWithTag("mistake_organization_correct_toggle")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("mistake_existing_relation_remove_0")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("恢复").assertExists()
        composeRule.onNodeWithTag("mistake_existing_relation_remove_0").performClick()
        composeRule.onNodeWithText("移除").assertExists()
        composeRule.onNodeWithTag("mistake_organization_confirm").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) { organization.lastSelection != null }

        assertTrue(requireNotNull(organization.lastSelection).relationRemovals.isEmpty())
    }

    @Test
    fun knowledgeTreePickJoinsTheSavedCorrectionSelection() {
        val organization = FakeOrganizationRepository()
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = successfulModelTasks(),
                    profile = StudyProfileOverview(),
                )
            }
        }

        // 整理自动发起并应用后，只有学生主动打开编辑器才出现修改面。
        waitForTag("mistake_organization_applied")
        composeRule.onNodeWithTag("mistake_organization_correct_toggle")
            .performScrollTo()
            .performClick()

        // 「从知识树选择」消费 observeOrganizationOptions 的选项。
        composeRule.onNodeWithTag("mistake_tree_picker_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_tree_query").performTextInput("三角")
        composeRule.onNodeWithTag("mistake_tree_knowledge_atomic-triangle").performClick()
        composeRule.onNodeWithTag("mistake_tree_done").performClick()
        composeRule.onNodeWithText("三角函数的图象与变换", substring = true).assertExists()

        // 选择结果并入 confirm 的 selection（同一条保存路径），而不是只留在界面。
        composeRule.onNodeWithTag("mistake_organization_confirm").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            organization.lastSelection != null
        }

        val selection = requireNotNull(organization.lastSelection)
        val picked = selection.userClassifications.single()
        assertEquals(ClassificationDimension.KNOWLEDGE, picked.dimension)
        assertEquals("三角函数的图象与变换", picked.displayName)
    }

    @Test
    fun treePickerGroupsOptionsAndMarksWhatIsAlreadySelected() {
        val organization = FakeOrganizationRepository()
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    organizationRepository = organization,
                    modelTasks = successfulModelTasks(),
                    profile = StudyProfileOverview(),
                )
            }
        }

        waitForTag("mistake_organization_applied")
        composeRule.onNodeWithTag("mistake_organization_correct_toggle")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("mistake_tree_picker_toggle").performScrollTo().performClick()

        // 弹窗按真实结构分两组：板块 ← TOPIC 级、知识点 ← ATOMIC 级。
        composeRule.onNodeWithTag("mistake_tree_list").assertExists()
        composeRule.onNodeWithText("板块").assertExists()
        composeRule.onNodeWithText("知识点").assertExists()

        // 章节路径：筛选后知识点选项被隐藏（"概念"只在板块名里），点选树上的板块节点，行立即标"已选"。
        composeRule.onNodeWithTag("mistake_tree_query").performTextInput("概念")
        composeRule.onNodeWithTag("mistake_tree_knowledge_atomic-triangle").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_tree_chapter_topic-functions").performClick()
        composeRule.onNodeWithText("已选").assertExists()

        // 清掉筛选：知识点选项重现，点选后共两行带"已选"标记。
        composeRule.onNodeWithTag("mistake_tree_query").performTextClearance()
        composeRule.onNodeWithTag("mistake_tree_knowledge_atomic-triangle").performClick()
        composeRule.onAllNodesWithText("已选").assertCountEquals(2)
    }

    @Test
    fun reentryAfterACorrectionStillOffersTheTreeEditorFromThePreservedState() {
        val organization = FakeOrganizationRepository()
        val detailVisible = mutableStateOf(true)
        composeRule.setContent {
            SmartMistakeBookTheme {
                if (detailVisible.value) {
                    MistakeDetailContent(
                        state = readyState(),
                        onBack = {},
                        onExport = {},
                        organizationRepository = organization,
                        modelTasks = successfulModelTasks(),
                        profile = StudyProfileOverview(),
                    )
                }
            }
        }

        // 第一次进入：自动整理应用后从知识树挑一个知识点并保存。
        waitForTag("mistake_organization_applied")
        composeRule.onNodeWithTag("mistake_organization_correct_toggle")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("mistake_tree_picker_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_tree_knowledge_atomic-triangle").performClick()
        composeRule.onNodeWithTag("mistake_tree_done").performClick()
        composeRule.onNodeWithTag("mistake_organization_confirm").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) { organization.confirmCallCount == 1 }

        // 离开详情再重进：apply 重放，因为已存在用户修改而停在保留态。
        composeRule.runOnIdle { detailVisible.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { detailVisible.value = true }
        waitForTag("mistake_organization_user_correction_preserved")

        // P1：保留态同样给出修改入口；编辑器以已保留的分类值为初始选择。
        composeRule.onNodeWithTag("mistake_organization_correct_toggle")
            .performScrollTo()
            .assertExists()
            .performClick()
        composeRule.onNodeWithTag("mistake_tree_picker_toggle").performScrollTo().assertExists()
        // 已保留的分类值就是编辑器的初始选择（"你补充的"行）。
        composeRule.onNodeWithText("你补充的 · 知识点 · 三角函数的图象与变换").assertExists()

        // 再改一次（这次走板块路径）并落库到同一条 confirm 写路径。
        composeRule.onNodeWithTag("mistake_tree_picker_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_tree_query").performTextInput("函数")
        composeRule.onNodeWithTag("mistake_tree_chapter_topic-functions").performClick()
        composeRule.onNodeWithTag("mistake_tree_done").performClick()
        composeRule.onNodeWithTag("mistake_organization_confirm").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) { organization.confirmCallCount == 2 }

        val second = requireNotNull(organization.lastSelection)
        assertTrue(second.userClassifications.any { it.displayName == "函数的概念与性质" })
        // 上一次的修改仍在集合里（编辑器用保留值做了初始选择）。
        assertTrue(second.userClassifications.any { it.displayName == "三角函数的图象与变换" })
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertRenewedExternalRequest(
        persisted: ModelTaskRequest,
        resumed: ModelTaskRequest,
    ) {
        assertNotEquals(persisted.requestId, resumed.requestId)
        assertEquals(persisted.input, resumed.input)
        assertEquals(persisted.occurredAtEpochMillis, resumed.occurredAtEpochMillis)
        val persistedManifest = requireNotNull(persisted.egressManifest)
        val resumedManifest = requireNotNull(resumed.egressManifest)
        assertNotEquals(persistedManifest.authorizationId, resumedManifest.authorizationId)
        assertNotEquals(
            persistedManifest.approvedAtEpochMillis,
            resumedManifest.approvedAtEpochMillis,
        )
        assertEquals(PROVIDER.providerId, resumedManifest.providerId)
        assertEquals(PROVIDER.modelId, resumedManifest.modelId)
        assertEquals(
            PROVIDER.providerConfigurationVersion,
            resumedManifest.providerConfigurationVersion,
        )
        assertEquals(
            ModelPromptPolicyVersions.PROBLEM_ORGANIZATION,
            resumedManifest.promptPolicyVersion,
        )
    }

    private fun readyState() = MistakeDetailState.Ready(
        detail = MistakeDetail(
            identity = MistakeDetailIdentity(
                errorBookEntryId = KEY.entryId,
                problemId = KEY.problemId,
                problemRevisionId = KEY.problemRevisionId,
                revisionNumber = 1,
                title = "函数最值",
                subject = "MATH",
            ),
            fallbackMarkdown = "求函数最值。",
            source = MistakeSourceSet.Missing,
        ),
        questionDocument = CapturedQuestionDocument(
            document = document("question", "求函数最值。"),
            blockEvidence = emptyList(),
        ),
    )

    private class FakeOrganizationRepository(
        private val automaticApplied: Boolean = true,
        private var applyFailuresBeforeSuccess: Int = 0,
        private val relationsAfterApply: List<ConfirmedProblemRelation> = emptyList(),
        private val organizationOptions: MistakeOrganizationOptions = MistakeOrganizationOptions(
            subject = "MATH",
            chapters = listOf(
                OrganizationOption(labelId = "topic-functions", displayName = "函数的概念与性质"),
            ),
            knowledgeNodes = listOf(
                OrganizationOption(labelId = "atomic-triangle", displayName = "三角函数的图象与变换"),
            ),
        ),
    ) : MistakeOrganizationRepository {
        var lastSelection: ProblemOrganizationSelection? = null
        var appliedRequestId: String? = null
        var applyCallCount: Int = 0
        var prepareCallCount: Int = 0
        var confirmCallCount: Int = 0
        /** 与真实现同语义：一旦用户改过，自动整理一律不覆盖（apply 报 preserved）。 */
        private var userCorrected = false
        private val confirmed = MutableStateFlow(ConfirmedMistakeOrganization())

        override suspend fun prepare(
            key: MistakeRevisionKey,
            profile: StudyProfileOverview,
            provider: ProviderCapabilitySnapshot,
            attempt: Int,
            occurredAtEpochMillis: Long,
            approvedAtEpochMillis: Long,
        ): MistakeOrganizationPreparation {
            prepareCallCount += 1
            val input = ProblemOrganizationInput(
                problemId = key.problemId,
                problemRevisionId = key.problemRevisionId,
                practiceUnitId = "practice-1",
                subject = SubjectKind.MATH,
                questionDocument = document("question", "求函数最值。"),
                relevantLearningEvidence = emptyList(),
                relationCandidates = listOf(
                    RelatedProblemCandidate(
                        problemId = "problem-2",
                        problemRevisionId = "revision-2",
                        subject = SubjectKind.MATH,
                        title = "二次函数变式",
                        questionDocument = document("related", "讨论参数范围。"),
                    ),
                ),
            )
            val requestId = "organization-test-$attempt"
            return MistakeOrganizationPreparation(
                request = ModelTaskRequest(
                    requestId = requestId,
                    input = input,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                    egressManifest = ModelEgressManifest(
                        authorizationId = "authorization:$requestId",
                        subjectId = input.subjectId,
                        purpose = ModelEgressPurpose.CLASSIFICATION,
                        authorizedTaskKinds = setOf(ModelTaskKind.PROBLEM_CLASSIFY),
                        providerId = provider.providerId,
                        modelId = provider.modelId,
                        providerConfigurationVersion = provider.providerConfigurationVersion,
                        promptPolicyVersion = ModelPromptPolicyVersions.PROBLEM_ORGANIZATION,
                        approvedAtEpochMillis = approvedAtEpochMillis,
                        assets = emptyList(),
                        disclosedData = ModelEgressManifest.PROBLEM_ORGANIZATION_DISCLOSURE,
                    ),
                ),
                relatedCandidateTitles = listOf("二次函数变式"),
            )
        }

        override fun observeConfirmed(key: MistakeRevisionKey): Flow<ConfirmedMistakeOrganization> =
            confirmed

        override suspend fun correctConfirmedOrganization(
            key: MistakeRevisionKey,
            selection: ProblemOrganizationSelection,
            correctedAtEpochMillis: Long,
        ): ProblemOrganizationConfirmation {
            lastSelection = selection
            return ProblemOrganizationConfirmation(
                created = true,
                classificationCount = selection.userClassifications.size,
                relationCount = 0,
            )
        }

        override fun observeOrganizationOptions(
            key: MistakeRevisionKey,
        ): Flow<MistakeOrganizationOptions> = MutableStateFlow(organizationOptions)

        override suspend fun applySuccessfulOrganization(
            requestId: String,
        ): ProblemOrganizationConfirmation {
            appliedRequestId = requestId
            applyCallCount += 1
            if (applyFailuresBeforeSuccess > 0) {
                applyFailuresBeforeSuccess -= 1
                error("temporary local apply failure")
            }
            if (!automaticApplied) {
                return ProblemOrganizationConfirmation(false, 0, 0, applied = false)
            }
            if (userCorrected) {
                // 已有 USER_CORRECTED 分类：重进详情时 apply 重放并停在这个保留态，
                // 因此修改入口必须在保留态同样可达（P1）。
                return ProblemOrganizationConfirmation(
                    created = false,
                    classificationCount = confirmed.value.classifications.size,
                    relationCount = confirmed.value.relations.size,
                    applied = false,
                    preservedUserCorrection = true,
                )
            }
            confirmed.value = ConfirmedMistakeOrganization(
                classifications = listOf(
                    com.tingyun.smartmistakebook.core.domain.ConfirmedProblemClassification(
                        ClassificationDimension.CHAPTER,
                        "chapter-functions",
                        "函数",
                    ),
                    com.tingyun.smartmistakebook.core.domain.ConfirmedProblemClassification(
                        ClassificationDimension.KNOWLEDGE,
                        "knowledge-quadratic-maximum",
                        "二次函数最值",
                    ),
                ),
                relations = relationsAfterApply,
            )
            return ProblemOrganizationConfirmation(true, 2, 0)
        }

        override suspend fun confirm(
            requestId: String,
            selection: ProblemOrganizationSelection,
            acceptedAtEpochMillis: Long,
        ): ProblemOrganizationConfirmation {
            lastSelection = selection
            confirmCallCount += 1
            userCorrected = true
            // 与真实现一致：保存后确认集合 = 勾选的模型建议 + 用户补充/树选择（USER_CORRECTED）。
            confirmed.value = ConfirmedMistakeOrganization(
                classifications = buildList {
                    selection.classificationIndexes.sorted().forEach { index ->
                        MODEL_PLAN_CLASSIFICATIONS.getOrNull(index)?.let { (dimension, displayName) ->
                            add(
                                com.tingyun.smartmistakebook.core.domain.ConfirmedProblemClassification(
                                    dimension = dimension,
                                    labelId = "user-corrected:$displayName",
                                    displayName = displayName,
                                ),
                            )
                        }
                    }
                    selection.userClassifications.forEach { classification ->
                        add(
                            com.tingyun.smartmistakebook.core.domain.ConfirmedProblemClassification(
                                dimension = classification.dimension,
                                labelId = "user-corrected:${classification.displayName}",
                                displayName = classification.displayName,
                            ),
                        )
                    }
                },
                relations = confirmed.value.relations,
            )
            return ProblemOrganizationConfirmation(true, 1 + selection.userClassifications.size, 0)
        }
    }

    private inner class DurableOrganizationModelTasks(
        initialTask: ModelTaskSnapshot? = null,
        private val provider: ProviderCapabilitySnapshot = PROVIDER,
    ) : ModelTaskRepository {
        private val persistedTasks = MutableStateFlow(listOfNotNull(initialTask))
        val executedRequests = mutableListOf<ModelTaskRequest>()

        override suspend fun capabilities() = provider

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> =
            flowOf(persistedTasks.value.singleOrNull { it.request.requestId == requestId })

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> = persistedTasks

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flow {
            executedRequests += request
            val succeeded = successfulSnapshot(request, provider)
            persistedTasks.value = persistedTasks.value
                .filterNot { it.request.requestId == request.requestId } + succeeded
            emit(succeeded)
        }
    }

    private fun organizationRequest(
        requestId: String,
        occurredAtEpochMillis: Long,
        approvedAtEpochMillis: Long,
    ): ModelTaskRequest {
        val input = ProblemOrganizationInput(
            problemId = KEY.problemId,
            problemRevisionId = KEY.problemRevisionId,
            practiceUnitId = "practice-1",
            subject = SubjectKind.MATH,
            questionDocument = document("question", "求函数最值。"),
            relevantLearningEvidence = emptyList(),
            relationCandidates = listOf(
                RelatedProblemCandidate(
                    problemId = "problem-2",
                    problemRevisionId = "revision-2",
                    subject = SubjectKind.MATH,
                    title = "二次函数变式",
                    questionDocument = document("related", "讨论参数范围。"),
                ),
            ),
        )
        return ModelTaskRequest(
            requestId = requestId,
            input = input,
            occurredAtEpochMillis = occurredAtEpochMillis,
            egressManifest = ModelEgressManifest(
                authorizationId = "authorization:$requestId",
                subjectId = input.subjectId,
                purpose = ModelEgressPurpose.CLASSIFICATION,
                authorizedTaskKinds = setOf(ModelTaskKind.PROBLEM_CLASSIFY),
                providerId = PROVIDER.providerId,
                modelId = PROVIDER.modelId,
                providerConfigurationVersion = PROVIDER.providerConfigurationVersion,
                promptPolicyVersion = ModelPromptPolicyVersions.PROBLEM_ORGANIZATION,
                approvedAtEpochMillis = approvedAtEpochMillis,
                assets = emptyList(),
                disclosedData = ModelEgressManifest.PROBLEM_ORGANIZATION_DISCLOSURE,
            ),
        )
    }

    private fun pendingSnapshot(request: ModelTaskRequest) = ModelTaskSnapshot(
        taskId = "task-${request.requestId}",
        request = request,
        requestFingerprint = ModelTaskFingerprint.of(request),
        status = ModelTaskStatus.WAITING_FOR_MODEL,
        stateVersion = 0,
        stage = ModelTaskStage.WAITING,
        userMessage = "等待模型",
        attemptCount = 0,
        createdAtEpochMillis = request.occurredAtEpochMillis,
        updatedAtEpochMillis = request.occurredAtEpochMillis,
    )

    private fun retryableSnapshot(request: ModelTaskRequest) = pendingSnapshot(request).copy(
        status = ModelTaskStatus.RETRYABLE_FAILURE,
        stateVersion = 1,
        userMessage = "整理暂时没有完成",
        attemptCount = 1,
        failure = ModelTaskFailure(
            code = ModelFailureCode.TIMEOUT,
            message = "连接暂时中断",
            retryable = true,
        ),
        updatedAtEpochMillis = request.occurredAtEpochMillis + 1,
    )

    private fun successfulSnapshot(
        request: ModelTaskRequest,
        provider: ProviderCapabilitySnapshot = PROVIDER,
    ): ModelTaskSnapshot {
        val input = request.input as ProblemOrganizationInput
        val output = ProblemOrganizationOutput(
            problemId = input.problemId,
            problemRevisionId = input.problemRevisionId,
            practiceUnitId = input.practiceUnitId,
            plan = ProblemOrganizationPlan(
                summaryMarkdown = "模型给出初步整理，请确认。",
                reviewPriorityMarkdown = "建议近期复习。",
                targetedEvidenceLabels = emptyList(),
                classifications = MODEL_PLAN_CLASSIFICATIONS.mapIndexed { index, (dimension, displayName) ->
                    ProblemClassificationSuggestion(
                        dimension = dimension,
                        displayName = displayName,
                        rationaleMarkdown = if (index == 0) {
                            "模型识别的所属板块。"
                        } else {
                            "模型识别的主要知识点。"
                        },
                        confidence = if (dimension == ClassificationDimension.CHAPTER) 0.91 else 0.82,
                    )
                },
                relations = emptyList(),
                schemaVersion = ProblemOrganizationPlan.SCHEMA_VERSION,
                atomicKnowledge = listOf(
                    AtomicKnowledgeSuggestion(
                        referenceId = "atom-1",
                        canonicalName = "比较顶点与区间端点的函数值",
                        aliases = emptyList(),
                        kind = KnowledgeNodeKind.PROCEDURE,
                        parentKnowledgeDisplayName = "二次函数最值",
                        matchedKnowledgeNodeId = null,
                        prerequisiteReferenceIds = emptyList(),
                        observableOutcomeMarkdown = "能找出候选位置并比较对应函数值。",
                        boundaryMarkdown = "只包含本题确定二次函数最值所需的比较步骤。",
                        confidence = 0.9,
                    ),
                ),
                stepAttributions = listOf(
                    ProblemStepKnowledgeAttribution(
                        stepOrdinal = 1,
                        stepSummaryMarkdown = "找出顶点与区间端点并比较函数值。",
                        atomicReferenceIds = listOf("atom-1"),
                    ),
                ),
            ),
            modelVersion = "model-v1",
        )
        return ModelTaskSnapshot(
            taskId = "task-${request.requestId}",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = ModelTaskStatus.SUCCEEDED,
            stateVersion = 1,
            stage = ModelTaskStage.COMPLETE,
            userMessage = "整理完成",
            attemptCount = 1,
            provider = provider,
            output = output,
            failure = null,
            createdAtEpochMillis = request.occurredAtEpochMillis,
            updatedAtEpochMillis = maxOf(
                request.occurredAtEpochMillis,
                request.egressManifest?.approvedAtEpochMillis ?: request.occurredAtEpochMillis,
            ),
        )
    }

    private fun successfulModelTasks(): ModelTaskRepository = object : ModelTaskRepository {
        override suspend fun capabilities() = PROVIDER

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> = flowOf(emptyList())

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> =
            flowOf(successfulSnapshot(request))
    }

    private object FakeModelTasks : ModelTaskRepository {
        override suspend fun capabilities() = PROVIDER

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> = flowOf(emptyList())

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> =
            error("The consent test must not execute a model request")
    }

    private companion object {
        val KEY = MistakeRevisionKey("entry-1", "problem-1", "revision-1")

        /** 模型建议的分类（顺序即 plan 索引）：替身的 confirm 用它重建保存后的确认集合。 */
        val MODEL_PLAN_CLASSIFICATIONS = listOf(
            ClassificationDimension.CHAPTER to "函数",
            ClassificationDimension.KNOWLEDGE to "二次函数最值",
        )
        val PROVIDER = ProviderCapabilitySnapshot(
            providerId = "provider",
            providerDisplayName = "测试模型",
            modelId = "model-v1",
            supportedTasks = setOf(ModelTaskKind.PROBLEM_CLASSIFY),
            supportsImageInput = true,
            supportsStructuredOutput = true,
            supportsStreaming = false,
            providerConfigurationVersion = "config-v1",
        )

        fun document(id: String, text: String) = QuestionDocument(
            id = id,
            blocks = listOf(ContentBlock.Paragraph("$id-block", text)),
        )
    }
}
