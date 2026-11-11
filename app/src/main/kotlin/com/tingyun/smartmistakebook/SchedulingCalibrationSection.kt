package com.tingyun.smartmistakebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.FsrsParameterFit
import com.tingyun.smartmistakebook.core.domain.PlannedReasonCalibration
import com.tingyun.smartmistakebook.core.domain.SchedulingCalibrationView
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton as OutlinedButton
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 复习排程校准的可见面（批次 49 P2-12）。
 *
 * 存在的理由：G6 幅度、打分权重、FSRS 参数此前都是**看不见的常量/存储值**——学生与维护者
 * 都无法回答"现在到底跑的是哪一组参数、它是什么时候、用多少样本拟合出来的、在同一批
 * 验证对上比默认参数好还是差"。没有这一面，"立即重拟合"与"重置参数"也没有可观察的出口。
 *
 * 展示口径（全部只读）：
 * - 当前生效的 21 维 FSRS 参数 + 来源（本地拟合 / 默认 / 导入）；
 * - 最近一次**成功采纳**的拟合（本地时间 / 可预测样本数 / 验证 logLoss 与默认基线）；
 * - 当前参数 vs 默认参数在同一 80/20 时间切分验证桶上的 logLoss；
 * - 各计划理由的实现回忆率（≥[PlannedReasonCalibration.MIN_SAMPLES] 例才进入建议门槛）；
 * - G6 考前扩容幅度与排程器版本（两者都读单源常量）；
 * - 来源校准见本页「来源校准」区块（[SourceCalibrationSection]），不在这里重复一份。
 *
 * 两个动作复用既有写路径：重拟合走 `StudyExperienceRepository.optimizeSchedulingParameters`
 * （采纳门在数据层），重置走 `SchedulingSettingsStore.setOptimizedParameters(null)`。
 * 参数在仓库构造时读取，所以两者都**下次启动生效**——页面如实说明，不假装立即生效。
 */
@Composable
internal fun SchedulingCalibrationSection(
    provider: suspend () -> SchedulingCalibrationView?,
    onOptimize: (suspend () -> Boolean)? = null,
    onReset: (suspend () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf<SchedulingCalibrationView?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(provider, refreshToken) {
        // 装配会重放整份 review_log 算验证损失；放在默认调度器上，避免大账本时卡住主线程。
        loaded = false
        unavailable = false
        try {
            view = withContext(Dispatchers.Default) { provider() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            unavailable = true
            view = null
        } finally {
            loaded = true
        }
    }

    Column(modifier = modifier) {
        PaperDivider(Modifier.padding(vertical = 16.dp))
        SectionHeader("复习排程校准")
        Text(
            text = "这里显示当前生效的复习排程参数与它们的校准证据。参数改动在下次启动时生效。",
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("scheduling_calibration_intro"),
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        when {
            unavailable -> Text(
                text = "排程校准数据暂不可用，请稍后再试。",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("scheduling_calibration_error"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )

            !loaded -> Text(
                text = "正在读取排程校准…",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("scheduling_calibration_loading"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )

            // 实现不提供该视图（接口默认 null）：如实说"不提供"，不假装成"没有数据"。
            view == null -> Text(
                text = "此版本暂不提供排程校准视图。",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("scheduling_calibration_unavailable"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )

            else -> {
                val loadedView = requireNotNull(view)
                CalibrationSummary(loadedView)
                ActionRow(
                    busy = busy,
                    hasOptimize = onOptimize != null,
                    hasReset = onReset != null,
                    parameterSource = loadedView.parameterSource,
                    onOptimize = {
                        busy = true
                        actionMessage = null
                        scope.launch {
                            try {
                                val adopted = withContext(Dispatchers.Default) { onOptimize!!.invoke() }
                                actionMessage = if (adopted) {
                                    "已采纳新的拟合参数，下次启动生效。"
                                } else {
                                    "本次没有采纳新参数：可预测样本不足，或候选参数没有优于现有参数。"
                                }
                                refreshToken += 1
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                actionMessage = "重新拟合没有完成，请稍后再试。"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onReset = {
                        busy = true
                        actionMessage = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.Default) { onReset!!.invoke() }
                                actionMessage = "已恢复默认参数，下次启动生效。"
                                refreshToken += 1
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                actionMessage = "重置没有完成，请稍后再试。"
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
                actionMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag("scheduling_calibration_action_message"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationSummary(view: SchedulingCalibrationView) {
    Text(
        text = "排程器版本 ${view.plannerVersion}",
        modifier = Modifier
            .padding(top = 10.dp)
            .testTag("scheduling_calibration_version"),
        color = InkSecondary,
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        text = "G6 考前扩容上限 +${(view.examCapacityRampMaxExtraFraction * 100).roundToInt()}%",
        modifier = Modifier
            .padding(top = 2.dp)
            .testTag("scheduling_calibration_g6"),
        color = InkSecondary,
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        text = "G6 幅度来自离线模拟标定（model-based）：30 KC / 300 题的合成世界未过采纳门，" +
            "真库样本不足，暂维持现值；改幅度属产品裁定，不在本页自动调整。",
        modifier = Modifier
            .padding(top = 2.dp)
            .testTag("scheduling_calibration_g6_note"),
        color = InkSecondary,
        style = MaterialTheme.typography.bodySmall,
    )

    Text(
        text = "参数来源：" + schedulingParameterSourceText(view.parameterSource),
        modifier = Modifier
            .padding(top = 12.dp)
            .testTag("scheduling_calibration_parameter_source"),
        color = InkSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
    // 21 维一次全给：展示少一维，学生就看不到实际生效的参数。
    view.parameters.chunked(PARAMETERS_PER_ROW).forEachIndexed { rowIndex, row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEachIndexed { columnIndex, value ->
                val index = rowIndex * PARAMETERS_PER_ROW + columnIndex
                Text(
                    text = schedulingParameterRowText(index, value),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scheduling_param_w$index"),
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // 末行不足三列时补齐占位，保持列宽一致。
            repeat(PARAMETERS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    val fit = view.lastFit
    if (fit != null) {
        Text(
            text = schedulingFitRecordText(fit, ZoneId.systemDefault()),
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag("scheduling_calibration_last_fit"),
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Text(
            text = "本机还没有拟合记录。当前参数是默认值或从备份导入，页面不会把导入的参数说成本地拟合。",
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag("scheduling_calibration_no_fit"),
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Text(
        text = schedulingValidationLossText(
            current = view.currentValidationLogLoss,
            baseline = view.baselineValidationLogLoss,
        ),
        modifier = Modifier
            .padding(top = 4.dp)
            .testTag("scheduling_calibration_validation_loss"),
        color = InkSecondary,
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        text = "当前可预测样本 ${view.predictableSampleCount} 例；拟合器需 ≥${view.fitSampleFloor} 例才会产出候选参数。",
        modifier = Modifier
            .padding(top = 2.dp)
            .testTag("scheduling_calibration_sample_count"),
        color = InkSecondary,
        style = MaterialTheme.typography.bodySmall,
    )

    Text(
        text = "按计划理由的实现回忆率",
        modifier = Modifier
            .padding(top = 14.dp)
            .testTag("scheduling_calibration_reason_header"),
        color = InkSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (view.perReason.isEmpty()) {
        Text(
            text = "还没有带计划理由的可拟合复习样本（讲题判定与看答案行不计入）。",
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("scheduling_calibration_reason_empty"),
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        view.perReason.forEach { calibration ->
            Text(
                text = calibration.plannedReason,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("scheduling_reason_${calibration.plannedReason}"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = schedulingReasonRecallText(calibration),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .testTag("scheduling_reason_stats_${calibration.plannedReason}"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = schedulingReasonVerdictText(calibration),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .testTag("scheduling_reason_verdict_${calibration.plannedReason}"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ActionRow(
    busy: Boolean,
    hasOptimize: Boolean,
    hasReset: Boolean,
    parameterSource: SchedulingCalibrationView.ParameterSource,
    onOptimize: () -> Unit,
    onReset: () -> Unit,
) {
    if (!hasOptimize && !hasReset) return
    Spacer(Modifier.height(14.dp))
    if (hasOptimize) {
        PrimaryActionButton(
            text = if (busy) "正在重拟合" else "立即重拟合",
            onClick = onOptimize,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("scheduling_calibration_optimize"),
        )
    }
    if (hasReset) {
        OutlinedButton(
            onClick = onReset,
            // 已经是默认参数时没有可重置的东西；按钮置灰而不是隐藏，学生能看到这个动作存在。
            enabled = !busy && parameterSource != SchedulingCalibrationView.ParameterSource.DEFAULT,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag("scheduling_calibration_reset"),
        ) {
            Text(if (busy) "正在重置" else "重置参数")
        }
        Text(
            text = "重置会丢弃本机拟合参数，回到 FSRS-6 默认值；下次启动生效，之后可以再拟合。",
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("scheduling_calibration_reset_note"),
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 参数按 3 列排版：21 维刚好 7 行，竖屏不横向滚动。 */
private const val PARAMETERS_PER_ROW = 3

internal fun schedulingParameterRowText(index: Int, value: Double): String =
    "w$index " + schedulingParameterValueText(value)

/** 参数值一律点号小数、固定 3 位：中文区域下不能变成 "0,212"，也不该出现科学计数。 */
internal fun schedulingParameterValueText(value: Double): String =
    String.format(Locale.ROOT, "%.3f", value)

internal fun schedulingParameterSourceText(
    source: SchedulingCalibrationView.ParameterSource,
): String = when (source) {
    SchedulingCalibrationView.ParameterSource.LOCAL_FIT -> "本地拟合参数"
    SchedulingCalibrationView.ParameterSource.DEFAULT -> "默认参数（本机尚未完成拟合）"
    SchedulingCalibrationView.ParameterSource.IMPORTED -> "已导入参数（没有本地拟合记录）"
}

internal fun schedulingFitRecordText(fit: FsrsParameterFit, zoneId: ZoneId): String {
    val adoptedAt = Instant.ofEpochMilli(fit.adoptedAtEpochMillis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT))
    return "最近拟合 $adoptedAt · 可预测样本 ${fit.sampleCount} 例 · 验证损失 " +
        "${schedulingLossText(fit.validationLogLoss)}（默认参数基线 " +
        "${schedulingLossText(fit.baselineValidationLogLoss)}）"
}

internal fun schedulingValidationLossText(current: Double?, baseline: Double?): String {
    val suffix = if (current == null || baseline == null) {
        "（没有验证对，暂不可算）"
    } else {
        "（同一 80/20 时间切分）"
    }
    return "当前参数验证损失 ${schedulingLossText(current)} · 默认参数基线 " +
        "${schedulingLossText(baseline)}$suffix"
}

/**
 * 损失缺值一律显示 "—"：用 0.0 顶替会读成"完美预测"，比空白更误导。
 */
private fun schedulingLossText(value: Double?): String =
    if (value == null || !value.isFinite()) "—" else String.format(Locale.ROOT, "%.3f", value)

internal fun schedulingReasonRecallText(calibration: PlannedReasonCalibration): String =
    "实现回忆率 ${schedulingPercentText(calibration.realizedRecallRate)} · " +
        "总体基线 ${schedulingPercentText(calibration.overallRecallRate)}"

internal fun schedulingReasonVerdictText(calibration: PlannedReasonCalibration): String =
    if (calibration.hasSufficientSamples) {
        "样本 ${calibration.sampleCount} 例：达到观察门槛，可人工讨论权重（不自动改写）。"
    } else {
        "样本 ${calibration.sampleCount} 例：还不够（需 ≥${PlannedReasonCalibration.MIN_SAMPLES} 例），" +
            "暂不建议调这个理由的权重。"
    }

private fun schedulingPercentText(value: Double): String =
    if (value.isNaN()) "—" else "${(value * 100).roundToInt()}%"
