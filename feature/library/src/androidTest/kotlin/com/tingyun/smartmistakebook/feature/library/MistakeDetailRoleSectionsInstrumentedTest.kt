package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionSummary
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemCommand
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemResult
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4E WG-06 T2b：详情页按视觉角色拆成"题目资产"和"讲解资产"两个区。
 *
 * 旧数据只有 CLEAN_IMAGE/QUESTION_SOURCE 时题目区逐位不变（由 JVM 测试锁序）；
 * 这里锁 UI：讲解图默认折叠、展开才出现、题目区永不出现讲解图；展开与状态恢复
 * （模拟旋转）都不写库——详情页没有图片生成入口，任何派发都只能来自外部动作。
 */
@RunWith(AndroidJUnit4::class)
class MistakeDetailRoleSectionsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun questionSectionOmitsExplanationFiguresAndExplanationFiguresAreCollapsedByDefault() {
        val repository = ReadOnlyDetailRepository()
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    repository = repository,
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_source_toggle")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("mistake_detail_source_content").assertExists()
        // 题目区只有 3 张题目资产（干净题面、原图、题图）；第 4 张讲解图必须不在这里。
        composeRule.onNodeWithTag("mistake_detail_source_asset_1").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_2").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_3").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_4").assertDoesNotExist()
        composeRule.onNodeWithText("重绘").assertExists()

        // 讲解区默认折叠：只有入口，没有内容。
        composeRule.onNodeWithTag("mistake_detail_explanation_toggle")
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_content").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_detail_explanation_asset_1").assertDoesNotExist()

        composeRule.onNodeWithTag("mistake_detail_explanation_toggle").performClick()
        composeRule.onNodeWithTag("mistake_detail_explanation_content").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_asset_1").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_asset_2").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_asset_3").assertDoesNotExist()

        assertEquals(0, repository.writes)
    }

    @Test
    fun expandedSectionsSurviveStateRestoreWithoutAnyWriteOrDispatch() {
        val repository = ReadOnlyDetailRepository()
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(),
                    onBack = {},
                    onExport = {},
                    repository = repository,
                )
            }
        }
        composeRule.onNodeWithTag("mistake_detail_source_toggle")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("mistake_detail_explanation_toggle")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("mistake_detail_explanation_content").assertExists()

        // 模拟旋转/配置变更：rememberSaveable 恢复展开状态，且仍不产生任何写入。
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("mistake_detail_source_content").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_content").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_4").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_detail_explanation_asset_1").assertExists()
        assertEquals(0, repository.writes)
    }

    @Test
    fun legacyCleanImageStillRendersFirstAndHasNoExplanationSection() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = readyState(
                        listOf(
                            asset("original", "QUESTION_SOURCE"),
                            asset("clean", VisualRole.CLEAN_IMAGE.name),
                        ),
                    ),
                    onBack = {},
                    onExport = {},
                    repository = ReadOnlyDetailRepository(),
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_source_toggle")
            .performScrollTo()
            .performClick()
        // 旧数据：重绘仍在第一位，原图紧随其后，页面上不出现讲解区。
        composeRule.onNodeWithTag("mistake_detail_source_asset_1").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_2").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_3").assertDoesNotExist()
        composeRule.onNodeWithText("重绘").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_detail_explanation_content").assertDoesNotExist()
    }

    private fun readyState(
        assets: List<MistakeSourceAsset> = defaultAssets(),
    ) = MistakeDetailState.Ready(
        detail = MistakeDetail(
            identity = MistakeDetailIdentity(
                errorBookEntryId = "entry-1",
                problemId = "problem-1",
                problemRevisionId = "revision-1",
                revisionNumber = 1,
                title = "函数最值",
                subject = "MATH",
            ),
            fallbackMarkdown = "求函数最值。",
            source = MistakeSourceSet.Present(assets),
        ),
        questionDocument = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "question",
                title = "函数最值",
                blocks = listOf(ContentBlock.Paragraph(id = "stem", markdown = "求函数最值。")),
            ),
            blockEvidence = emptyList(),
        ),
    )

    private fun defaultAssets() = listOf(
        asset("original", "QUESTION_SOURCE"),
        asset("clean", VisualRole.CLEAN_IMAGE.name),
        asset("diagram", VisualRole.QUESTION_DIAGRAM.name),
        asset("explain-a", VisualRole.TUTOR_EXPLANATION.name),
        asset("explain-b", VisualRole.TUTOR_EXPLANATION.name),
    )

    private fun asset(id: String, role: String) = MistakeSourceAsset(
        role = role,
        sourceAssetId = id,
        contentSha256 = "a".repeat(64),
        mimeType = "image/png",
        byteSize = 1_024,
        width = 800,
        height = 600,
        sourceType = "CAMERA",
        createdAtEpochMillis = 1_000,
        location = MistakeSourceLocation.Available("file:///nonexistent-$id.png"),
    )

    /** 详情页只读：任何写/派发路径被触发都让用例失败，而不是悄悄记账。 */
    private class ReadOnlyDetailRepository : MistakeDetailRepository {
        var writes = 0
            private set

        override fun observe(errorBookEntryId: String): Flow<MistakeDetailState> =
            flowOf(MistakeDetailState.NotFound)

        override fun observeExact(key: MistakeRevisionKey): Flow<MistakeDetailState> =
            flowOf(MistakeDetailState.NotFound)

        override suspend fun readExact(key: MistakeRevisionKey): MistakeDetailState =
            MistakeDetailState.NotFound

        override suspend fun reviseProblem(
            command: ReviseMistakeProblemCommand,
        ): ReviseMistakeProblemResult {
            writes += 1
            error("详情页不得发起题目修订")
        }

        override suspend fun updateUserNote(
            entryId: String,
            note: String?,
            updatedAtEpochMillis: Long,
        ): Boolean {
            writes += 1
            error("详情页打开/展开/旋转不得写备注")
        }

        override suspend fun setFavorite(
            entryId: String,
            isFavorite: Boolean,
            updatedAtEpochMillis: Long,
        ): Boolean {
            writes += 1
            error("详情页打开/展开/旋转不得改收藏")
        }

        override suspend fun archiveEntry(entryId: String, at: Long): Boolean {
            writes += 1
            error("详情页打开/展开/旋转不得归档")
        }

        override suspend fun restoreEntry(entryId: String, at: Long): Boolean {
            writes += 1
            error("详情页打开/展开/旋转不得恢复")
        }

        override fun observeRevisionHistory(
            errorBookEntryId: String,
        ): Flow<List<MistakeRevisionSummary>> = flowOf(emptyList())
    }
}
