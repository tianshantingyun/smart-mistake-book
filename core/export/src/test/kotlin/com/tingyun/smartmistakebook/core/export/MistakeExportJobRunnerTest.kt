package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 后台导出编排（[MistakeExportJobRunner]）的 JVM 用例：单题/批量的可导出判定、两档失败
 * 文案与完整性核对都在这里钉住，不必等真机。
 *
 * 它替代的是旧导出页面的 `prepareMistakeExport`（页面内、离开即取消）——同样的判定链，
 * 现在能在 JVM 上完整演练。
 */
class MistakeExportJobRunnerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `an eligible single export renders and names itself from the visible revision`() = runBlocking {
        val runner = runner(renderPdf = { input -> verifiedPrepared(input.inputSha256) })

        val outcome = runner.run(
            MistakeExportJobRequest.Single(exportId = "export-1", key = EXACT_KEY),
        )

        val rendered = outcome as MistakeExportJobOutcome.Rendered
        assertEquals("错题-函数单调区间-第3版.pdf", rendered.displayName)
        assertTrue(rendered.prepared.verifyIntegrity())
    }

    @Test
    fun `the request layout reaches the renderer for single and batch exports`() = runBlocking {
        // 4B 批 4：sheet 上学生定稿的版式必须一路走到渲染输入（版式也是缓存键的一部分）。
        val layout = MistakePdfLayout(
            templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
            columnCount = 2,
            includeAnswer = true,
        )
        val seen = mutableListOf<MistakePdfLayout>()
        val runner = runner(
            renderPdf = { input ->
                seen += input.layout
                verifiedPrepared(input.inputSha256)
            },
        )

        runner.run(
            MistakeExportJobRequest.Single(
                exportId = "export-layout-1",
                key = EXACT_KEY,
                layout = layout,
            ),
        )
        runner.run(
            MistakeExportJobRequest.Batch(
                exportId = "export-layout-2",
                entryIds = listOf(EXACT_KEY.entryId),
                layout = layout,
            ),
        )

        assertEquals(listOf(layout, layout), seen)
    }

    @Test
    fun `an invalid layout is refused where the request is built`() {
        // P2-1 防御：坏版式不许走到 WorkManager（解码失败/渲染失败都是晚失败）。
        val failure = runCatching {
            MistakeExportJobRequest.Batch(
                exportId = "export-bad",
                entryIds = listOf(EXACT_KEY.entryId),
                layout = MistakePdfLayout(fontScale = 9),
            )
        }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class.java, failure?.javaClass)
    }

    @Test
    fun `a legacy record is blocked with the honest content reason`() = runBlocking {
        val runner = runner(states = mapOf(EXACT_KEY.entryId to legacyState()))

        val outcome = runner.run(
            MistakeExportJobRequest.Single(exportId = "export-1", key = EXACT_KEY),
        )

        val blocked = outcome as MistakeExportJobOutcome.Blocked
        assertTrue(blocked.message.contains("旧格式"))
    }

    @Test
    fun `a mismatched revision is blocked without rendering`() = runBlocking {
        var rendered = false
        val runner = runner(
            states = mapOf(
                EXACT_KEY.entryId to readyState(
                    identity = identity(problemRevisionId = "another-revision"),
                ),
            ),
            renderPdf = { input ->
                rendered = true
                verifiedPrepared(input.inputSha256)
            },
        )

        val outcome = runner.run(
            MistakeExportJobRequest.Single(exportId = "export-1", key = EXACT_KEY),
        )

        assertTrue(outcome is MistakeExportJobOutcome.Blocked)
        assertEquals(false, rendered)
    }

    @Test
    fun `a page limit failure becomes the student facing render failure`() = runBlocking {
        val runner = runner(
            renderPdf = { throw MistakePdfExportException(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED) },
        )

        val outcome = runner.run(
            MistakeExportJobRequest.Single(exportId = "export-1", key = EXACT_KEY),
        )

        val failed = outcome as MistakeExportJobOutcome.Failed
        assertTrue(failed.message.contains("超出了单题 A4 导出的范围"))
    }

    @Test
    fun `an unexpected render exception becomes a generic failure`() = runBlocking {
        val runner = runner(renderPdf = { throw IllegalStateException("disk on fire") })

        val outcome = runner.run(
            MistakeExportJobRequest.Single(exportId = "export-1", key = EXACT_KEY),
        )

        val failed = outcome as MistakeExportJobOutcome.Failed
        assertTrue(failed.message.contains("请稍后重新导出"))
    }

    @Test
    fun `a corrupted prepared artifact is refused instead of reported as delivered`() = runBlocking {
        val runner = runner(
            renderPdf = {
                // 父目录名与 inputSha256 不一致 = 完整性核对必失败。
                PreparedMistakePdf(
                    file = File(temporaryFolder.newFolder("fake"), PREPARED_PDF_FILE_NAME)
                        .apply { writeBytes("%PDF-1.4".toByteArray()) },
                    sha256 = "a".repeat(64),
                    inputSha256 = "b".repeat(64),
                    pageCount = 1,
                )
            },
        )

        val outcome = runner.run(
            MistakeExportJobRequest.Single(exportId = "export-1", key = EXACT_KEY),
        )

        assertTrue(outcome is MistakeExportJobOutcome.Failed)
    }

    @Test
    fun `a batch omits ineligible entries and names itself by the included count`() = runBlocking {
        val states = mapOf(
            "entry-a" to readyState(identity = identity(entryId = "entry-a", title = "题 A")),
            "entry-b" to legacyState(entryId = "entry-b"),
            "entry-c" to readyState(identity = identity(entryId = "entry-c", title = "题 C")),
        )
        val runner = runner(states = states, renderPdf = { input -> verifiedPrepared(input.inputSha256) })

        val outcome = runner.run(
            MistakeExportJobRequest.Batch(exportId = "export-2", entryIds = states.keys.toList()),
        )

        val rendered = outcome as MistakeExportJobOutcome.Rendered
        assertEquals("错题练习-2道.pdf", rendered.displayName)
    }

    @Test
    fun `a batch with nothing exportable is blocked and an empty batch too`() = runBlocking {
        val runner = runner(
            states = mapOf("entry-b" to legacyState(entryId = "entry-b")),
            renderPdf = { input -> verifiedPrepared(input.inputSha256) },
        )

        val nothingReady = runner.run(
            MistakeExportJobRequest.Batch(exportId = "export-2", entryIds = listOf("entry-b")),
        ) as MistakeExportJobOutcome.Blocked
        assertTrue(nothingReady.message.contains("还没有整理完整"))

        val empty = runner.run(
            MistakeExportJobRequest.Batch(exportId = "export-3", entryIds = emptyList()),
        ) as MistakeExportJobOutcome.Blocked
        assertEquals("当前没有可导出的错题。", empty.message)
    }

    @Test
    fun `an over limit batch is blocked before reading any detail`() = runBlocking {
        var read = false
        val runner = runner(
            states = emptyMap(),
            readByEntryId = { id ->
                read = true
                readyState(identity = identity(entryId = id))
            },
            renderPdf = { input -> verifiedPrepared(input.inputSha256) },
        )

        val outcome = runner.run(
            MistakeExportJobRequest.Batch(
                exportId = "export-4",
                entryIds = (1..(MistakePdfBatchEligibility.MAX_QUESTIONS + 1)).map { "entry-$it" },
            ),
        )

        assertTrue(outcome is MistakeExportJobOutcome.Blocked)
        assertEquals(false, read)
    }

    // ---- fixtures ----

    private fun runner(
        states: Map<String, MistakeDetailState> = mapOf(EXACT_KEY.entryId to readyState()),
        readByEntryId: (suspend (String) -> MistakeDetailState)? = null,
        renderPdf: (MistakePdfExportInput) -> PreparedMistakePdf = { input ->
            verifiedPrepared(input.inputSha256)
        },
    ) = MistakeExportJobRunner(
        readExact = { key -> states[key.entryId] ?: MistakeDetailState.NotFound },
        readByEntryId = readByEntryId ?: { entryId ->
            states[entryId] ?: MistakeDetailState.NotFound
        },
        renderPdf = renderPdf,
    )

    /** 真的可核对的产物：真文件 + 真摘要 + 目录名 == inputSha256。 */
    private fun verifiedPrepared(inputSha256: String): PreparedMistakePdf {
        val directory = temporaryFolder.newFolder(inputSha256)
        val pdf = File(directory, PREPARED_PDF_FILE_NAME).apply {
            writeBytes("%PDF-1.4 fake".toByteArray())
        }
        val digest = sha256File(pdf)
        File(directory, PREPARED_DIGEST_FILE_NAME).writeText("$digest\n")
        return PreparedMistakePdf(
            file = pdf,
            sha256 = digest,
            inputSha256 = inputSha256,
            pageCount = 1,
        )
    }

    private fun readyState(
        identity: MistakeDetailIdentity = identity(),
    ): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem", "已知函数 f(x)=x²，求单调区间。")
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = identity,
                fallbackMarkdown = "备用题面",
                source = MistakeSourceSet.Missing,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-1",
                    title = identity.title,
                    blocks = listOf(block),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "asset-1",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )
    }

    private fun legacyState(entryId: String = EXACT_KEY.entryId) = MistakeDetailState.Legacy(
        MistakeDetail(
            identity = identity(entryId = entryId),
            fallbackMarkdown = "旧题面",
            source = MistakeSourceSet.Missing,
        ),
    )

    private fun identity(
        entryId: String = EXACT_KEY.entryId,
        problemRevisionId: String = EXACT_KEY.problemRevisionId,
        title: String = "函数单调区间",
    ) = MistakeDetailIdentity(
        errorBookEntryId = entryId,
        problemId = EXACT_KEY.problemId,
        problemRevisionId = problemRevisionId,
        revisionNumber = 3,
        title = title,
        subject = "MATH",
    )

    private companion object {
        val EXACT_KEY = MistakeRevisionKey(
            entryId = "entry-1",
            problemId = "problem-1",
            problemRevisionId = "revision-3",
        )
    }
}
