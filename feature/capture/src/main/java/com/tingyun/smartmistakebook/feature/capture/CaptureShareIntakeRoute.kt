package com.tingyun.smartmistakebook.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.BatchImportRepository
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import com.tingyun.smartmistakebook.core.ui.SmartColors
import kotlinx.coroutines.CancellationException

/**
 * A7（4C）：系统分享的录入入口。
 *
 * 这一屏只活很短：接收 → 单图交给录入页、多图/PDF 交给整卷管线、失败就把原因与出路
 * 如实摆出来（重试 / 去手动录入 / 关闭）。失败时的"原文件还在"为真：接收过程中的所有
 * 临时字节都在自有缓存里，且失败路径会删掉预拷件；学生的原文件从未被移动或改写。
 *
 * `request == null` 是前台恢复后的形态：分享 intent 里的临时授权随进程消失，无法再读，
 * 因此直接说"已经失效，请重新分享"，而不是留在一个永远转圈的进度条上。
 */
@Composable
fun CaptureShareIntakeRoute(
    request: CaptureShareRequest?,
    batchImports: BatchImportRepository,
    onSingleImageReady: (String) -> Unit,
    onImportStarted: () -> Unit,
    onManualEntry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val intake = remember(context, batchImports) {
        CaptureShareIntake(context = context.applicationContext, batchImports = batchImports)
    }
    var retryNonce by remember(request?.requestId) { mutableIntStateOf(0) }
    var state by remember(request?.requestId) {
        mutableStateOf<CaptureShareRouteState>(
            if (request == null) CaptureShareRouteState.Expired else CaptureShareRouteState.Working,
        )
    }

    LaunchedEffect(request?.requestId, retryNonce) {
        val pending = request ?: return@LaunchedEffect
        state = CaptureShareRouteState.Working
        // 外部输入不吃掉整进程：任何未归类的异常都要变成一条如实的、可重试的失败。
        val result = try {
            intake.intake(pending)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CaptureShareResult.Rejected(
                reason = CaptureShareRejection.IMPORT_FAILED,
                message = captureShareRejectionMessage(CaptureShareRejection.IMPORT_FAILED),
            )
        }
        when (result) {
            is CaptureShareResult.SingleImage -> onSingleImageReady(result.ownedUri)
            is CaptureShareResult.ImageBatch -> onImportStarted()
            is CaptureShareResult.Pdf -> onImportStarted()
            is CaptureShareResult.Rejected -> {
                state = CaptureShareRouteState.Failed(
                    reason = result.reason,
                    message = result.message,
                )
            }
        }
    }

    RootPageColumn(
        modifier = modifier.testTag("share_intake_root"),
    ) {
        CaptureShareHeader(onBack = onDismiss)
        Text(
            text = "从其他应用分享进来的图片或 PDF 会直接进入录入。",
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        when (val current = state) {
            CaptureShareRouteState.Working -> {
                Text(
                    text = "正在接收分享的文件…",
                    style = MaterialTheme.typography.titleSmall,
                    color = SmartColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("share_intake_progress"),
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = SmartColors.Jade,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "文件只在本机读取和保存，不会上传。",
                    color = SmartColors.InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            CaptureShareRouteState.Expired -> CaptureShareFailureCard(
                message = CAPTURE_SHARE_EXPIRED_MESSAGE,
                retryable = false,
                onRetry = {},
                onManualEntry = onManualEntry,
                onDismiss = onDismiss,
            )
            is CaptureShareRouteState.Failed -> CaptureShareFailureCard(
                message = current.message,
                retryable = captureShareRejectionIsRetryable(current.reason),
                onRetry = {
                    state = CaptureShareRouteState.Working
                    retryNonce += 1
                },
                onManualEntry = onManualEntry,
                onDismiss = onDismiss,
            )
        }
    }
}

private sealed interface CaptureShareRouteState {
    data object Working : CaptureShareRouteState

    data object Expired : CaptureShareRouteState

    data class Failed(
        val reason: CaptureShareRejection,
        val message: String,
    ) : CaptureShareRouteState
}

internal const val CAPTURE_SHARE_EXPIRED_MESSAGE =
    "这次分享的内容已经失效，请从系统里重新分享一次。"

@Composable
private fun CaptureShareFailureCard(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onManualEntry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("share_intake_rejected"),
        color = SmartColors.ErrorWarm.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            SmartColors.ErrorWarm.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "这次分享没有导入",
                style = MaterialTheme.typography.titleSmall,
                color = SmartColors.Ink,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = SmartColors.InkSecondary,
            )
            Text(
                text = "原文件还在原来的位置；也可以直接从录入页手动选图。",
                style = MaterialTheme.typography.labelSmall,
                color = SmartColors.InkSecondary,
            )
            if (retryable) {
                PrimaryActionButton(
                    text = "再试一次",
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_intake_retry"),
                )
            }
            OutlineActionChip(
                text = "去手动录入",
                onClick = onManualEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("share_intake_manual"),
            )
            OutlineActionChip(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("share_intake_close"),
            )
        }
    }
}

@Composable
private fun CaptureShareHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
        }
        Spacer(Modifier.size(4.dp))
        Text("接收分享", style = MaterialTheme.typography.headlineSmall)
    }
}
