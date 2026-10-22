package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawCommand
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawFailureReason
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawPhase
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawRequestResult
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawState
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4E WG-06 T3：详情页显式重绘的**设备侧**验收（Compose）。
 *
 * 三条硬边界：
 * 1. 打开/滚动/旋转（状态恢复）**0 派发**——只有点击重绘按钮才调用命令；
 * 2. 命令携带当前修订 + 期望修订 + CLEAN_IMAGE + 用户理由（current-revision CAS 的输入）；
 * 3. 进行中禁用入口；失败显示原因且额度内出现"重试重绘"；成功后入口消失。
 */
@RunWith(AndroidJUnit4::class)
class MistakeDetailExplicitRedrawInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun redrawOnlyDispatchesOnUserActionAndShowsRunningFailureAndSuccess() {
        val repository = RedrawDetailRepository()
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

        // 打开详情页：有入口，但一次命令也没发。
        composeRule.onNodeWithTag("mistake_detail_redraw").performScrollTo().assertExists()
        assertEquals(0, repository.commands.size)

        composeRule.onNodeWithTag("mistake_detail_redraw").performClick()
        composeRule.waitForIdle()
        assertEquals("只有点击才派发", 1, repository.commands.size)
        val command = repository.commands.single()
        assertEquals("revision-1", command.problemRevisionId)
        assertEquals("revision-1", command.expectedRevisionId)
        assertEquals(VisualRole.CLEAN_IMAGE, command.role)
        assertTrue(command.reason.isNotBlank())

        // 进行中：入口禁用，进度可见。
        repository.emit(
            ExplicitRedrawState(
                phase = ExplicitRedrawPhase.RUNNING,
                taskId = "task-1",
                attemptCount = 1,
                updatedAtEpochMillis = 2_000,
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mistake_detail_redraw_progress").assertExists()
        composeRule.onNodeWithTag("mistake_detail_redraw").assertIsNotEnabled()

        // 失败且额度未耗尽：展示原因 + 重试入口；重试仍是一次新的用户命令。
        repository.emit(
            ExplicitRedrawState(
                phase = ExplicitRedrawPhase.FAILED,
                taskId = "task-1",
                attemptCount = 1,
                failureReason = ExplicitRedrawFailureReason.SOURCE_UNREADABLE,
                canRetry = true,
                updatedAtEpochMillis = 2_000,
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mistake_detail_redraw_status").assertExists()
        composeRule.onNodeWithText("重试重绘").performScrollTo().assertExists()
        composeRule.onNodeWithTag("mistake_detail_redraw").performClick()
        composeRule.waitForIdle()
        assertEquals("重试是另一次用户动作", 2, repository.commands.size)

        // 成功：状态可见，入口不再出现（同一请求不会重复付费）。
        repository.emit(
            ExplicitRedrawState(
                phase = ExplicitRedrawPhase.SUCCEEDED,
                taskId = "task-1",
                attemptCount = 1,
                updatedAtEpochMillis = 2_000,
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("已生成新的干净题面。").performScrollTo().assertExists()
        composeRule.onNodeWithTag("mistake_detail_redraw").assertDoesNotExist()
    }

    @Test
    fun openingAndRotatingNeverDispatch() {
        val repository = RedrawDetailRepository()
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

        composeRule.onNodeWithTag("mistake_detail_redraw").performScrollTo().assertExists()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertTrue("观察状态可以发生", repository.observeCalls > 0)
        assertEquals("打开/旋转 0 派发", 0, repository.commands.size)
    }

    private fun readyState() = MistakeDetailState.Ready(
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
            source = MistakeSourceSet.Present(
                listOf(
                    sourceAsset("original", "QUESTION_SOURCE"),
                    sourceAsset("clean", VisualRole.CLEAN_IMAGE.name),
                ),
            ),
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

    private fun sourceAsset(id: String, role: String) = MistakeSourceAsset(
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

    /** 只读详情 + 可控的重绘状态；命令被记录下来而不是真的落库。 */
    private class RedrawDetailRepository : MistakeDetailRepository {
        val commands = mutableListOf<ExplicitRedrawCommand>()
        var observeCalls = 0
            private set
        private val redrawState = MutableStateFlow<ExplicitRedrawState>(ExplicitRedrawState.Idle)

        override fun observe(errorBookEntryId: String): Flow<MistakeDetailState> =
            flowOf(MistakeDetailState.NotFound)

        override fun observeExact(key: MistakeRevisionKey): Flow<MistakeDetailState> =
            flowOf(MistakeDetailState.NotFound)

        override suspend fun readExact(key: MistakeRevisionKey): MistakeDetailState =
            MistakeDetailState.NotFound

        override fun supportsExplicitRedraw(): Boolean = true

        override fun observeExplicitRedraw(problemRevisionId: String): Flow<ExplicitRedrawState> {
            observeCalls += 1
            return redrawState
        }

        override suspend fun requestExplicitRedraw(
            command: ExplicitRedrawCommand,
        ): ExplicitRedrawRequestResult {
            commands += command
            return ExplicitRedrawRequestResult.Accepted(redrawState.value)
        }

        fun emit(state: ExplicitRedrawState) {
            redrawState.value = state
        }
    }
}
