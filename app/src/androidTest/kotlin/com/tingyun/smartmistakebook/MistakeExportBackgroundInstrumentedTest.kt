package com.tingyun.smartmistakebook

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeExportRecord
import com.tingyun.smartmistakebook.core.domain.MistakeExportRepository
import com.tingyun.smartmistakebook.core.domain.MistakeExportStatus
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRunner
import com.tingyun.smartmistakebook.core.export.MistakePdfExporter
import com.tingyun.smartmistakebook.core.export.PreparedMistakePdfToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **「导出不随页面丢」的真证据**（阶段 4A 批 4 · L7）。
 *
 * 走真实入口：错题详情点「导出 A4」→ 页面被导航离开（成果入口 → 返回详情 → 返回错题本，
 * 触发导出的详情页随之销毁）→ 后台 work 仍持完成、记录落 SUCCEEDED、产物能按记录里的
 * 钥匙重新打开。改前导出是 `produceState + rememberCoroutineScope` 的页面内任务，
 * 页面一销毁渲染即取消——这条用例会红。
 */
@RunWith(AndroidJUnit4::class)
class MistakeExportBackgroundInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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
    fun exportCompletesAfterTheDetailingPageIsLeftAndStaysRetrievable() {
        val application = composeRule.activity.application as SmartMistakeBookApplication
        val repository = application.mistakeExportRepository
        val recordsBefore = runBlocking { repository.observeRecords().first() }
            .map(MistakeExportRecord::exportId)
            .toSet()

        navigateAndWait("nav_library", "root_library")
        waitForTag("library_item_entry:m1:closed-interval-extrema")
        composeRule.onNodeWithTag("library_item_entry:m1:closed-interval-extrema")
            .performScrollTo()
            .performClick()
        waitForTag("mistake_detail_ready")

        composeRule.onNodeWithTag("mistake_detail_export_a4")
            .performScrollTo()
            .performClick()
        waitForTag("mistake_export_hub")

        // 离开：成果入口 → 详情 → 错题本。详情页（触发导出的页面）在这里被销毁。
        composeRule.onNodeWithTag("mistake_export_hub_back").performClick()
        waitForTag("mistake_detail_ready")
        composeRule.onNodeWithTag("mistake_detail_back").performClick()
        waitForTag("root_library")

        val record = runBlocking { awaitTerminalExport(repository, recordsBefore) }
        assertEquals(
            "导出必须已经完成（失败原因：${record.failureMessage}）",
            MistakeExportStatus.SUCCEEDED,
            record.status,
        )

        // 可取回：按记录里的钥匙重开同一份产物，且是真的 PDF。
        val token = PreparedMistakePdfToken.fromPersistedValue(
            record.preparedPdfValueOrNull(),
        )
        assertNotNull("成功记录必须带齐重开钥匙", token)
        val prepared = MistakePdfExporter(application).reopenVerified(checkNotNull(token))
        assertNotNull("离开页面后产物必须仍可重开", prepared)
        assertTrue(prepared!!.verifyIntegrity())
        assertTrue(
            prepared.file.inputStream().use { input ->
                val header = ByteArray(4)
                input.read(header) == header.size &&
                    String(header, Charsets.US_ASCII) == "%PDF"
            },
        )

        // 成果入口自己也读得到这份结果（列表 文件名/时间/状态）。
        composeRule.onNodeWithTag("library_export_results")
            .performScrollTo()
            .performClick()
        waitForTag("mistake_export_hub")
        waitForTag("export_hub_record_${record.exportId}")
    }

    @Test
    fun theNotificationDeepLinkIntentReachesTheResultsHub() {
        // 通知的 contentIntent 由 `mistakeExportOpenIntent` 构造；MainActivity.onCreate/onNewIntent
        // 消费的是同一个 action/extra（都走 `handleOpenIntent`）。这里把通知同一个 builder 产出的
        // intent 直接喂给消费点，钉住"通知 → 消费 → 成果入口"这条链（系统 Activity 投递本身
        // 由平台负责，通知侧另有 contentIntent 非空断言）。
        val intent = mistakeExportOpenIntent(composeRule.activity)
        assertEquals(MistakeExportContract.ACTION_OPEN_EXPORTS, intent.action)
        assertEquals(
            true,
            intent.getBooleanExtra(MistakeExportContract.EXTRA_OPEN_EXPORTS, false),
        )

        composeRule.activity.handleOpenIntent(intent)

        waitForTag("mistake_export_hub")
    }

    /**
     * "离开页面瞬间仍在进行"的可控证据（复核 P2）：把渲染闸门关上再点导出，记录必然停在
     * RUNNING；离开并销毁详情页后放开闸门，导出才完成。若导出仍是页面内任务（离开即取消），
     * 放开闸门也不会有 SUCCEEDED。
     */
    @Test
    fun exportIsStillRunningWhenThePageIsLeftAndCompletesAfterTheGateOpens() {
        val application = composeRule.activity.application as SmartMistakeBookApplication
        val repository = application.mistakeExportRepository
        val realRunner = application.mistakeExportJobRunner
        val gate = java.util.concurrent.CountDownLatch(1)
        application.mistakeExportJobRunner = MistakeExportJobRunner(
            readExact = application.mistakeDetailRepository::readExact,
            readByEntryId = { entryId ->
                application.mistakeDetailRepository.observe(entryId)
                    .first { it != MistakeDetailState.Loading }
            },
            renderPdf = { input ->
                gate.await(30, java.util.concurrent.TimeUnit.SECONDS)
                MistakePdfExporter(application).prepare(input)
            },
        )
        try {
            val recordsBefore = runBlocking { repository.observeRecords().first() }
                .map(MistakeExportRecord::exportId)
                .toSet()

            navigateAndWait("nav_library", "root_library")
            waitForTag("library_item_entry:m1:closed-interval-extrema")
            composeRule.onNodeWithTag("library_item_entry:m1:closed-interval-extrema")
                .performScrollTo()
                .performClick()
            waitForTag("mistake_detail_ready")
            composeRule.onNodeWithTag("mistake_detail_export_a4")
                .performScrollTo()
                .performClick()
            waitForTag("mistake_export_hub")

            // 渲染被闸门挡住：离开之前，这条记录必须仍在进行。
            val running = runBlocking {
                withTimeout(15_000) {
                    repository.observeRecords().first { records ->
                        records.any {
                            it.exportId !in recordsBefore &&
                                it.status == MistakeExportStatus.RUNNING
                        }
                    }
                }.first { it.exportId !in recordsBefore }
            }
            assertEquals(MistakeExportStatus.RUNNING, running.status)

            // 离开并销毁详情页（触发导出的那一页），此刻导出仍未完成。
            composeRule.onNodeWithTag("mistake_export_hub_back").performClick()
            waitForTag("mistake_detail_ready")
            composeRule.onNodeWithTag("mistake_detail_back").performClick()
            waitForTag("root_library")
            assertEquals(
                "离开页面时导出必须仍在进行（闸门未开）",
                MistakeExportStatus.RUNNING,
                runBlocking { repository.readRecord(running.exportId) }?.status,
            )

            gate.countDown()
            val terminal = runBlocking { awaitTerminalExport(repository, recordsBefore) }
            assertEquals(
                "离开页面后导出仍必须完成（失败原因：${terminal.failureMessage}）",
                MistakeExportStatus.SUCCEEDED,
                terminal.status,
            )
        } finally {
            gate.countDown()
            application.mistakeExportJobRunner = realRunner
        }
    }

    private suspend fun awaitTerminalExport(
        repository: MistakeExportRepository,
        recordsBefore: Set<String>,
    ): MistakeExportRecord = withTimeout(60_000) {
        repository.observeRecords()
            .first { records ->
                records.any { it.exportId !in recordsBefore && it.status != MistakeExportStatus.RUNNING }
            }
            .first { it.exportId !in recordsBefore }
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
