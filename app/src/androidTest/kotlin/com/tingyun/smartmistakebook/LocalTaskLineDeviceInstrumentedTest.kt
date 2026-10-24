package com.tingyun.smartmistakebook

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.export.MistakeDocxDeliveryIntents
import com.tingyun.smartmistakebook.core.export.MistakeDocxDocumentSaver
import com.tingyun.smartmistakebook.core.export.MistakeDocxExporter
import com.tingyun.smartmistakebook.core.export.MistakeDocxSaveResult
import com.tingyun.smartmistakebook.core.export.MistakePdfEligibility
import com.tingyun.smartmistakebook.core.export.MistakePdfEligibilityResult
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * T7（PRD-11）本地任务线设备走查的**补缺最小用例**（批次 19 设备轨）。
 *
 * 链条「首装空态→手动建题→保存→再次打开→原生 PDF/DOCX 导出→保存/分享→备份→换机恢复」
 * 中既有组合已覆盖：首装空态→手动建题入口（[Prd06EmptyLibraryCaptureCtaInstrumentedTest]，
 * 到表单为止）、PDF 保存/分享/打印（[MistakeExportDeliveryInstrumentedTest]）、
 * 备份→换机恢复（core:data `DeviceMigrationDrillInstrumentedTest`）。本类只补两个真缺口：
 *
 * 1. **手动建题真的提交落库并跨页重开可见**——Prd06 停在"表单与提交键都在"；这里把
 *    题面敲进去、点提交、等回执，然后核对三件事：真实库 `problem` 行数 1、全链
 *    `model_task` 仍为 0（无模型派发 = 本地任务线的"网络请求观测=0"应用级证据）、
 *    离开再进入错题本还能看到这道题（再次打开）。
 * 2. **原生 DOCX 导出在设备上产出真文件**——PDF 半边已有交付接线测试；DOCX 半边
 *    用真 exporter 在设备上渲染一份 Word：完整性口径自证、ZIP 结构与正文真实、
 *    交付意图（CREATE_DOCUMENT/SEND 的 OOXML MIME）、SAF 保存器对 file:// 拒收、
 *    经 MediaStore 的真保存摘要一致。
 *
 * 用例 1 会清空已安装应用的真实库（首装空态），沿用 [DestructiveDeviceTestGuard]：
 * 默认跳过，仅专用测试设备 `-e smb_allow_data_reset true` 开启。
 */
@RunWith(AndroidJUnit4::class)
class LocalTaskLineDeviceInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun guardedSetup() {
        DestructiveDeviceTestGuard.requireExplicitOptIn()
    }

    // ------------------------------------------------------------------
    // 缺口一：手动建题 → 提交落库 → 再次打开
    // ------------------------------------------------------------------

    @Test
    fun manualCreateCommitsToTheRealLibraryReopensVisibleAndStaysModelFree() {
        val application = composeRule.activity.application as SmartMistakeBookApplication
        runBlocking {
            application.studyDatabase.clearAllData()
            application.studyRepository.refresh()
        }

        waitForTag("root_review")
        waitForTag("review_capture_button")
        composeRule.onNodeWithTag("review_capture_button")
            .performScrollTo()
            .performClick()
        waitForTag("capture_mode_chooser")
        composeRule.onNodeWithTag("capture_mode_manual")
            .performScrollTo()
            .performClick()
        waitForTag("capture_correction_form")

        // 手动建题的编辑器可能默认展开（A8 文案形态）；未展开时点"开始输入题面"。
        if (composeRule.onAllNodesWithTag("capture_edit_disclosure").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("capture_edit_disclosure").performClick()
        }
        composeRule.onNodeWithTag("capture_title_input").performTextInput(MANUAL_TITLE)
        composeRule.onNodeWithTag("capture_transcription_input").performTextInput(MANUAL_STEM)

        composeRule.onNodeWithTag("capture_commit_button")
            .performScrollTo()
            .performClick()
        waitForTag("capture_committed_receipt")

        // 真实落库：一道题；全程零模型任务（本地任务线不发起任何模型调用/网络请求）。
        assertEquals("手动建题必须真的落进真实库", 1, countProblems(application))
        assertEquals(
            "本地任务线全链不允许新增任何模型调用（网络请求观测=0 的应用级证据）",
            0,
            countModelTaskRows(application),
        )

        // 再次打开：退出录入、回到根导航、进错题本，这道题还在且可见。
        pressBackUntilRootReview()
        composeRule.onNodeWithTag("nav_library").performClick()
        waitForTag("root_library")
        assertTrue(
            "再次打开错题本必须能看到手动保存的题（标题：$MANUAL_TITLE）",
            composeRule.onAllNodesWithText(MANUAL_TITLE, substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    // ------------------------------------------------------------------
    // 缺口二：原生 DOCX 导出在设备上产出真文件
    // ------------------------------------------------------------------

    @Test
    fun nativeDocxExportProducesARealWordArtifactOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val eligible = MistakePdfEligibility.check(docxReadyState()) as MistakePdfEligibilityResult.Eligible
        val prepared = MistakeDocxExporter(context).prepare(eligible.input)
        assertTrue("DOCX 完整性口径必须在设备上自证通过", prepared.verifyIntegrity())

        // 真 Word（OOXML）包结构：ZIP + [Content_Types].xml + word/document.xml，正文含题面。
        ZipFile(prepared.file).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("必须是 OOXML 包：缺 [Content_Types].xml", "[Content_Types].xml" in names)
            assertTrue("必须是 OOXML 包：缺 word/document.xml", "word/document.xml" in names)
            val documentXml = zip.getInputStream(zip.getEntry("word/document.xml"))
                .use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
            assertTrue(
                "document.xml 必须包含导出题面（实际 ${documentXml.length} 字符）",
                documentXml.contains(DOCX_STEM_PLAIN),
            )
        }

        // 交付意图：保存 = ACTION_CREATE_DOCUMENT + OOXML MIME；分享 = ACTION_SEND 带流。
        val saveIntent = MistakeDocxDeliveryIntents.createDocument("错题-T7.docx")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, saveIntent.action)
        assertEquals(MistakeDocxDeliveryIntents.MIME_TYPE, saveIntent.type)
        val shareIntent = MistakeDocxDeliveryIntents.share(context, prepared, "错题-T7.docx")
        assertEquals(Intent.ACTION_SEND, shareIntent.action)
        assertEquals(MistakeDocxDeliveryIntents.MIME_TYPE, shareIntent.type)
        assertNotNull(shareIntent.extras?.get(Intent.EXTRA_STREAM))

        // 保存器 fail-closed：非 content:// 的目标必须拒收。
        assertEquals(
            "file:// 目标必须拒收（SAF 保存只认 content://）",
            MistakeDocxSaveResult.DESTINATION_UNAVAILABLE,
            MistakeDocxDocumentSaver.save(context, prepared, Uri.fromFile(File(context.cacheDir, "t7.docx"))),
        )

        // 经 MediaStore 的真保存：写入、回读摘要与产物一致。
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "smbk-t7-${System.nanoTime()}.docx")
            put(MediaStore.MediaColumns.MIME_TYPE, MistakeDocxDeliveryIntents.MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        assertNotNull("模拟器必须允许向 Downloads 插入一行（否则本断言链无法在设备上验证）", destination)
        val saved = MistakeDocxDocumentSaver.save(context, prepared, destination!!)
        assertEquals("SAF 保存必须成功", MistakeDocxSaveResult.SAVED, saved)
        val copiedDigest = resolver.openInputStream(destination)?.use { stream ->
            sha256Hex(stream.readBytes())
        } ?: error("保存后的 MediaStore 流必须可读")
        assertEquals("保存副本摘要必须与产物一致", prepared.sha256, copiedDigest)
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(destination, values, null, null)
    }

    // ------------------------------------------------------------------
    // 夹具与助手
    // ------------------------------------------------------------------

    private fun docxReadyState(): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem", DOCX_STEM_PLAIN)
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = "entry-t7-docx",
                    problemId = "problem-t7-docx",
                    problemRevisionId = "revision-t7-docx-1",
                    revisionNumber = 1,
                    title = "T7 导出题",
                    subject = "MATH",
                ),
                fallbackMarkdown = "备用题面",
                source = MistakeSourceSet.Missing,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-t7-docx",
                    title = "T7 导出题",
                    blocks = listOf(block),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "asset-t7-docx",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )
    }

    private fun countProblems(context: Context): Int =
        rawCount(context, "SELECT COUNT(*) FROM `problem`")

    private fun countModelTaskRows(context: Context): Int =
        rawCount(context, "SELECT COUNT(*) FROM `model_task`")

    private fun rawCount(context: Context, sql: String): Int {
        val path = context.getDatabasePath(StudyDatabaseFactory.DEFAULT_DATABASE_NAME).absolutePath
        return SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(sql, null).use { cursor ->
                check(cursor.moveToFirst()) { "COUNT query returned no row: $sql" }
                cursor.getInt(0)
            }
        }
    }

    private fun pressBackUntilRootReview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var attempts = 0
        while (
            composeRule.onAllNodesWithTag("root_review").fetchSemanticsNodes().isEmpty() &&
            attempts < 4
        ) {
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            attempts++
            composeRule.waitForIdle()
            android.os.SystemClock.sleep(400)
        }
        waitForTag("root_review")
    }

    private fun waitForTag(tag: String) {
        waitUntil { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 20_000, condition = condition)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val MANUAL_TITLE = "T7本地线题目"
        const val MANUAL_STEM = "求 f(x)=x-2x 的单调区间。"
        const val DOCX_STEM_PLAIN = "求函数 f(x)=x-2x 的单调区间"
    }
}
