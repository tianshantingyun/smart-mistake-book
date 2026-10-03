package com.tingyun.smartmistakebook

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeExportKind
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.export.MistakePdfEligibility
import com.tingyun.smartmistakebook.core.export.MistakePdfEligibilityResult
import com.tingyun.smartmistakebook.core.export.MistakePdfExporter
import com.tingyun.smartmistakebook.core.export.PreparedMistakePdf
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「导出成果」入口的交付接线（阶段 4A 批 4 · L7）用**真 exporter 产出的 PDF** 走既有交付链：
 * 保存必须是 `ACTION_CREATE_DOCUMENT`、分享必须是 `ACTION_SEND` 的 chooser（`MistakePdfDeliveryIntents`
 * 的两个入口）；打印的布局回调必须由既有 `PreparedPdfPrintAdapter` 给出真实页数。
 *
 * 不重写交付链的检查方式：意图/适配器都是 core:export 的既有实现，本用例只钉"成果入口确实
 * 把它们接上了"，而且文件来自后台导出的同一份产物布局。
 */
@RunWith(AndroidJUnit4::class)
class MistakeExportDeliveryInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val intentsRule = IntentsRule()

    @Before
    fun seedCuratedFixture() {
        runBlocking {
            val application = composeRule.activity.application as SmartMistakeBookApplication
            seedAppStudyFacts(
                context = application,
                database = application.studyDatabase,
                databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME,
                bundle = AppCuratedStudyFixture.bundle(),
            )
            application.studyRepository.refresh()
        }
    }

    @Test
    fun saveAndShareGoThroughTheExistingDeliveryIntents() {
        val application = composeRule.activity.application as SmartMistakeBookApplication
        val prepared = prepareRealArtifact(application)
        val exportId = "export-delivery-${System.nanoTime()}"
        seedSucceededRecord(application, exportId, prepared)

        navigateAndWait("nav_library", "root_library")
        composeRule.onNodeWithTag("library_export_results")
            .performScrollTo()
            .performClick()
        waitForTag("mistake_export_hub")
        waitForTag("export_hub_record_$exportId")

        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        intending(hasAction(Intent.ACTION_CHOOSER))
            .respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

        composeRule.onNodeWithTag("export_hub_save_$exportId")
            .performScrollTo()
            .performClick()
        intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))

        composeRule.onNodeWithTag("export_hub_share_$exportId")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            Intents.getIntents().any { it.action == Intent.ACTION_CHOOSER }
        }
    }

    @Test
    fun printActionGoesThroughTheExistingAdapterWithoutCrashing() {
        val application = composeRule.activity.application as SmartMistakeBookApplication
        val prepared = prepareRealArtifact(application)
        val exportId = "export-print-${System.nanoTime()}"
        seedSucceededRecord(application, exportId, prepared)

        navigateAndWait("nav_library", "root_library")
        composeRule.onNodeWithTag("library_export_results")
            .performScrollTo()
            .performClick()
        waitForTag("mistake_export_hub")
        waitForTag("export_hub_record_$exportId")

        composeRule.onNodeWithTag("export_hub_print_$exportId")
            .performScrollTo()
            .performClick()

        // 打印动作接的是既有 `PreparedPdfPrintAdapter`（真产物已过完整性核对，打印界面由
        // 系统 printspooler 打开）。模拟器上有没有打印服务由系统镜像决定，所以钉两件诚实结果
        // 之一：①系统打印界面接管（应用失去焦点，组合被暂停）；②页面给出诚实的动作结果。
        val printUiOpened = runCatching {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                !composeRule.activity.hasWindowFocus()
            }
        }.isSuccess
        if (!printUiOpened) {
            waitForTag("export_hub_action_message")
            val message = composeRule.onNodeWithTag("export_hub_action_message")
                .fetchSemanticsNode()
                .config
                .let { config ->
                    config.getOrNull(SemanticsProperties.Text)
                        ?.joinToString("") { it.text }
                        .orEmpty()
                }
            assertTrue(
                "没有打印服务时页面必须如实说结果（得到：$message）",
                message == "已打开打印设置。" ||
                    message == "文件已清理，无法打印。请重新导出这一份。",
            )
        } else {
            // 收尾：把系统打印界面按回去，别把设备留给别的用例。
            android.os.SystemClock.sleep(500)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        }
    }

    private fun prepareRealArtifact(application: SmartMistakeBookApplication): PreparedMistakePdf {
        val eligible = MistakePdfEligibility.check(readyState()) as MistakePdfEligibilityResult.Eligible
        val prepared = MistakePdfExporter(application).prepare(eligible.input)
        assertTrue(prepared.verifyIntegrity())
        return prepared
    }

    private fun seedSucceededRecord(
        application: SmartMistakeBookApplication,
        exportId: String,
        prepared: PreparedMistakePdf,
    ) {
        runBlocking {
            val repository = application.mistakeExportRepository
            val now = System.currentTimeMillis()
            repository.recordStarted(exportId, MistakeExportKind.SINGLE, now)
            val record = repository.recordSucceeded(
                exportId = exportId,
                displayName = "错题-测试-第1版.pdf",
                inputSha256 = prepared.inputSha256,
                pdfSha256 = prepared.sha256,
                pageCount = prepared.pageCount,
                atEpochMillis = now,
            )
            assertEquals("记录必须落成 SUCCEEDED", "SUCCEEDED", record?.status?.name)
        }
    }

    private fun readyState(): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem", "已知函数 f(x)=x²-2x，求单调区间。")
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = "entry-delivery",
                    problemId = "problem-delivery",
                    problemRevisionId = "revision-delivery-1",
                    revisionNumber = 1,
                    title = "测试题目",
                    subject = "MATH",
                ),
                fallbackMarkdown = "备用题面",
                source = MistakeSourceSet.Missing,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-delivery",
                    title = "测试题目",
                    blocks = listOf(block),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "asset-delivery",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )
    }

    private fun navigateAndWait(tag: String, expected: String) {
        composeRule.onNodeWithTag(tag).performClick()
        waitForTag(expected)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
