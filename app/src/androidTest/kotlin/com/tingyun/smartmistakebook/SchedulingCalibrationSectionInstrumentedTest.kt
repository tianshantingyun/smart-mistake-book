package com.tingyun.smartmistakebook

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.FsrsParameterFit
import com.tingyun.smartmistakebook.core.domain.PlannedReasonCalibration
import com.tingyun.smartmistakebook.core.domain.SchedulingCalibrationView
import com.tingyun.smartmistakebook.core.model.AppCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.NetworkMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 校准可见页（批次 49 P2-12）的语义断言。
 *
 * 这一面必须让学生真正看到：21 维参数（一维不少）、参数来源（本地拟合 / 默认 / 导入）、
 * 最近拟合的时点/样本/验证损失与基线、按理由校准表、G6 幅度与排程器版本，以及两个
 * 复用既有写路径的动作。这里钉住的是"页面是否把这些事实真的放上屏"，
 * 不是数据层口径（后者在 `SchedulingCalibrationViewBuilderTest`）。
 */
@RunWith(AndroidJUnit4::class)
class SchedulingCalibrationSectionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsAllTwentyOneParametersTheFitRecordValidationLossReasonTableAndG6Version() {
        composeRule.setContent {
            MaterialTheme {
                SchedulingCalibrationSection(
                    provider = { localFitView() },
                    onOptimize = { true },
                    onReset = {},
                )
            }
        }
        awaitLoaded()

        composeRule.onNodeWithTag("scheduling_calibration_version")
            .assertTextEquals("排程器版本 review-planner-v4")
        composeRule.onNodeWithTag("scheduling_calibration_g6").assertTextEquals("G6 考前扩容上限 +50%")
        composeRule.onNodeWithTag("scheduling_calibration_g6_note").assertExists()
        composeRule.onNodeWithTag("scheduling_calibration_parameter_source")
            .assertTextEquals("参数来源：本地拟合参数")

        // 21 维全部在屏，且下标从 w0 连续到 w20（少一维 = 学生看不到实际生效的参数）。
        repeat(21) { index ->
            composeRule.onNodeWithTag("scheduling_param_w$index").assertExists()
        }
        composeRule.onNodeWithTag("scheduling_param_w0").assertTextEquals("w0 0.100")
        composeRule.onNodeWithTag("scheduling_param_w20").assertTextEquals("w20 2.100")

        composeRule.onNodeWithTag("scheduling_calibration_last_fit")
            .assertTextContains("可预测样本 1234 例", substring = true)
        composeRule.onNodeWithTag("scheduling_calibration_validation_loss").assertTextEquals(
            "当前参数验证损失 0.301 · 默认参数基线 0.355（同一 80/20 时间切分）",
        )
        composeRule.onNodeWithTag("scheduling_calibration_sample_count")
            .assertTextContains("当前可预测样本 1234 例", substring = true)
        composeRule.onNodeWithTag("scheduling_calibration_sample_count")
            .assertTextContains("需 ≥400 例", substring = true)

        composeRule.onNodeWithTag("scheduling_reason_DUE_RECALL_RISK").assertExists()
        composeRule.onNodeWithTag("scheduling_reason_stats_DUE_RECALL_RISK")
            .assertTextEquals("实现回忆率 63% · 总体基线 75%")
        composeRule.onNodeWithTag("scheduling_reason_verdict_DUE_RECALL_RISK")
            .assertTextEquals("样本 240 例：达到观察门槛，可人工讨论权重（不自动改写）。")

        composeRule.onNodeWithTag("scheduling_calibration_optimize").assertIsEnabled()
        composeRule.onNodeWithTag("scheduling_calibration_reset").assertIsEnabled()
        composeRule.onNodeWithTag("scheduling_calibration_reset_note").assertExists()

        // P2-15 设备证据补件：校准可见页 PNG + uiautomator 语义树。
        Batch49DeviceArtifacts.capturePng(composeRule, "batch49-calibration-section.png")
        Batch49DeviceArtifacts.dumpA11y("batch49-calibration-section")
    }

    @Test
    fun importedParametersWithoutAFitRecordAreNotPresentedAsALocalFit() {
        composeRule.setContent {
            MaterialTheme {
                SchedulingCalibrationSection(
                    provider = {
                        baseView().copy(
                            parameterSource = SchedulingCalibrationView.ParameterSource.IMPORTED,
                            lastFit = null,
                        )
                    },
                )
            }
        }
        awaitLoaded()

        composeRule.onNodeWithTag("scheduling_calibration_parameter_source")
            .assertTextEquals("参数来源：已导入参数（没有本地拟合记录）")
        composeRule.onNodeWithTag("scheduling_calibration_no_fit").assertExists()
        composeRule.onNodeWithTag("scheduling_calibration_last_fit").assertDoesNotExist()
    }

    @Test
    fun resetIsDisabledWhenTheDefaultParametersAreAlreadyInEffect() {
        composeRule.setContent {
            MaterialTheme {
                SchedulingCalibrationSection(
                    provider = {
                        baseView().copy(
                            parameterSource = SchedulingCalibrationView.ParameterSource.DEFAULT,
                            lastFit = null,
                        )
                    },
                    onReset = {},
                )
            }
        }
        awaitLoaded()

        composeRule.onNodeWithTag("scheduling_calibration_parameter_source")
            .assertTextEquals("参数来源：默认参数（本机尚未完成拟合）")
        composeRule.onNodeWithTag("scheduling_calibration_reset").assertIsNotEnabled()
    }

    @Test
    fun optimizeClickGoesThroughTheRepositoryCallbackAndSaysNothingWasAdopted() {
        var optimizeCalls = 0
        var providerCalls = 0
        composeRule.setContent {
            MaterialTheme {
                SchedulingCalibrationSection(
                    provider = { providerCalls += 1; localFitView() },
                    onOptimize = { optimizeCalls += 1; false },
                )
            }
        }
        awaitLoaded()

        composeRule.onNodeWithTag("scheduling_calibration_optimize").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { optimizeCalls == 1 }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("scheduling_calibration_action_message")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // 未采纳时必须说明原因方向（样本不足或候选不优），不能静默当成功。
        composeRule.onNodeWithTag("scheduling_calibration_action_message")
            .assertTextEquals("本次没有采纳新参数：可预测样本不足，或候选参数没有优于现有参数。")
        // 动作之后重读一次装配（页面上的数字不能停在点击前）。
        composeRule.waitUntil(timeoutMillis = 5_000) { providerCalls >= 2 }
    }

    @Test
    fun resetClickGoesThroughTheSettingsStoreCallbackAndSaysItAppliesNextLaunch() {
        var resetCalls = 0
        composeRule.setContent {
            MaterialTheme {
                SchedulingCalibrationSection(
                    provider = { localFitView() },
                    onReset = { resetCalls += 1 },
                )
            }
        }
        awaitLoaded()

        composeRule.onNodeWithTag("scheduling_calibration_reset").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { resetCalls == 1 }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("scheduling_calibration_action_message")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("scheduling_calibration_action_message")
            .assertTextEquals("已恢复默认参数，下次启动生效。")
    }

    @Test
    fun aFailingProviderShowsAnErrorInsteadOfPretendingThereAreNoParameters() {
        composeRule.setContent {
            MaterialTheme {
                SchedulingCalibrationSection(provider = { error("review_log unavailable") })
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("scheduling_calibration_error")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("scheduling_calibration_error")
            .assertTextEquals("排程校准数据暂不可用，请稍后再试。")
        composeRule.onNodeWithTag("scheduling_param_w0").assertDoesNotExist()
    }

    @Test
    fun anImplementationWithoutTheViewSaysSoInsteadOfShowingEmptyParameters() {
        composeRule.setContent {
            MaterialTheme { SchedulingCalibrationSection(provider = { null }) }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("scheduling_calibration_unavailable")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("此版本暂不提供排程校准视图。").assertExists()
    }

    @Test
    fun theCapabilityPageHostsTheCalibrationSection() {
        composeRule.setContent {
            MaterialTheme {
                CapabilityScreen(
                    capabilities = AppCapabilitySnapshot(
                        networkMode = NetworkMode.STRICT_OFFLINE,
                        cameraCaptureAvailable = false,
                        trustedOcrAvailable = false,
                        tutorTeachingEnabled = false,
                        remoteModelConfigured = false,
                    ),
                    configurationStore = null,
                    capabilityTester = null,
                    onBack = {},
                    schedulingCalibrationProvider = { localFitView() },
                    onOptimizeSchedulingParameters = { false },
                    onResetSchedulingParameters = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("scheduling_calibration_version")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // 入口不新建导航体系：面板就在能力页里，且参数/动作都可达。
        composeRule.onNodeWithTag("capability_scheduling_calibration").assertExists()
        composeRule.onNodeWithTag("scheduling_calibration_optimize").assertExists()
        composeRule.onNodeWithTag("scheduling_calibration_reset").assertExists()
    }

    private fun awaitLoaded() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("scheduling_calibration_version")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun localFitView(): SchedulingCalibrationView = baseView().copy(
        parameterSource = SchedulingCalibrationView.ParameterSource.LOCAL_FIT,
        lastFit = FsrsParameterFit(
            adoptedAtEpochMillis = 1_760_000_000_000L,
            sampleCount = 1_234,
            validationLogLoss = 0.312,
            baselineValidationLogLoss = 0.355,
        ),
    )

    /** 21 维参数 w0..w20 = 0.1, 0.2, … 2.1（下标与展示位一一对应，便于断言）。 */
    private fun baseView(): SchedulingCalibrationView = SchedulingCalibrationView(
        parameters = (1..21).map { it * 0.1 },
        parameterSource = SchedulingCalibrationView.ParameterSource.DEFAULT,
        lastFit = null,
        predictableSampleCount = 1_234,
        currentValidationLogLoss = 0.3012,
        baselineValidationLogLoss = 0.3554,
        perReason = listOf(
            PlannedReasonCalibration(
                plannedReason = "DUE_RECALL_RISK",
                sampleCount = 240,
                realizedRecallRate = 0.625,
                overallRecallRate = 0.75,
            ),
        ),
        examCapacityRampMaxExtraFraction = 0.5,
        plannerVersion = "review-planner-v4",
    )
}
