package com.tingyun.smartmistakebook.feature.capture

import androidx.compose.foundation.layout.heightIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tingyun.smartmistakebook.core.ui.WorkspacePageHeader
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import com.tingyun.smartmistakebook.core.ui.ClaudePageHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A7（4C）：系统分享的录入入口。
 *
 * 这一屏只活很短：接收 → 单图交给录入页、多图/PDF 交给整卷管线、失败就把原因与出路
 * 如实摆出来（重试 / 去手动录入 / 关闭）。失败时的"原文件还在"为真：接收过程中的所有
 * 临时字节都在自有缓存里，且失败路径会删掉预拷件；学生的原文件从未被移动或改写。
 *
 * **"再试一次"为什么能成功（KD-57）**：接收失败不撤销读授权；只有成功（字节已落到自有
 * 缓存/既有管线）或用户明确放弃（手动录入 / 关闭 / 返回）时才释放。可重试失败留在原地，
 * 再试一次读的是同一批仍被授权的 URI。
 *
 * **Expired 是派生值**：`request == null` 只是其中一种来源。授权在读取时被收回
 * （[CaptureShareRejection.AUTHORIZATION_EXPIRED]）或超过接收窗口
 * （[CAPTURE_SHARE_AUTHORIZATION_TIMEOUT_MILLIS]）都会翻成 Expired；收下分享的时刻
 * 跟着 `rememberSaveable` 走，所以进程重建恢复出来的非空请求不会变成永不过期。
 *
 * [clock] / [authorizationTimeoutMillis] 是仪器化用例的时钟缝：超时是十分钟级别的行为，
 * 不注入时钟就只能在测试里真等十分钟，或者根本测不到 Expired。
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
    authorizationTimeoutMillis: Long = CAPTURE_SHARE_AUTHORIZATION_TIMEOUT_MILLIS,
    clock: () -> Long = { System.currentTimeMillis() },
) {
    val context = LocalContext.current
    val intake = remember(context, batchImports) {
        CaptureShareIntake(context = context.applicationContext, batchImports = batchImports)
    }
    var retryNonce by remember(request?.requestId) { mutableIntStateOf(0) }
    var attempt by remember(request?.requestId) {
        mutableStateOf<CaptureShareRouteState>(CaptureShareRouteState.Working)
    }
    // 收下分享的时刻必须跟着 Saver 走：进程重建后按时钟继续过期，而不是"恢复即永久可读"。
    var authorizedAtEpochMillis by rememberSaveable(request?.requestId) {
        mutableStateOf(clock())
    }
    // "现在"刻意不保存：恢复后必须用当前时钟重算，否则旧时间跟着回来就永不过期。
    var nowEpochMillis by remember(request?.requestId) { mutableLongStateOf(clock()) }
    // 读取时授权被拒（intake 返回 AUTHORIZATION_EXPIRED）：由授权有效性派生的 Expired。
    // 跟着 Saver 走：旋转/重建后仍是 Expired，不再对同一批失效 URI 重读一次。
    var expiredByResult by rememberSaveable(request?.requestId) { mutableStateOf(false) }
    val expired = request == null ||
        expiredByResult ||
        captureShareAuthorizationExpired(
            nowEpochMillis = nowEpochMillis,
            authorizedAtEpochMillis = authorizedAtEpochMillis,
            timeoutMillis = authorizationTimeoutMillis,
        )
    val routeState: CaptureShareRouteState = if (expired) {
        CaptureShareRouteState.Expired
    } else {
        attempt
    }

    // 到 deadline 主动翻成过期：失败卡上的"再试一次"不能在窗口结束后继续可点。
    LaunchedEffect(request?.requestId, authorizedAtEpochMillis, authorizationTimeoutMillis) {
        if (request == null) return@LaunchedEffect
        while (true) {
            val now = clock()
            nowEpochMillis = now
            val remaining = authorizedAtEpochMillis + authorizationTimeoutMillis - now
            if (remaining <= 0L) break
            delay(remaining)
        }
    }

    LaunchedEffect(request?.requestId, retryNonce) {
        val pending = request ?: return@LaunchedEffect
        val attemptAt = clock()
        nowEpochMillis = attemptAt
        if (
            captureShareAuthorizationExpired(
                nowEpochMillis = attemptAt,
                authorizedAtEpochMillis = authorizedAtEpochMillis,
                timeoutMillis = authorizationTimeoutMillis,
            )
        ) {
            return@LaunchedEffect
        }
        attempt = CaptureShareRouteState.Working
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
                if (result.reason == CaptureShareRejection.AUTHORIZATION_EXPIRED) {
                    expiredByResult = true
                } else {
                    attempt = CaptureShareRouteState.Failed(
                        reason = result.reason,
                        message = result.message,
                    )
                }
            }
        }
    }

    // 明确放弃（手动录入 / 关闭 / 返回）才释放仍握着的授权；成功路径由 intake 负责。
    // 撤销是 Binder 调用：放到 IO 上做，导航/清空 pending 仍回主线程，不阻塞输入。
    val abandonScope = rememberCoroutineScope()
    val abandonShare: (() -> Unit) -> Unit = { next ->
        val pending = request
        if (pending == null) {
            next()
        } else {
            abandonScope.launch {
                try {
                    intake.releaseReadGrants(pending)
                } finally {
                    next()
                }
            }
        }
    }
    // 系统返回键由 NavHost 直接 pop 的话不会走 abandonShare：外部 URI 读授权会一直留着，
    // Root 的 pendingSharedCapture 也不清空。这里接管返回，与页头返回/关闭同一出口。
    BackHandler { abandonShare(onDismiss) }

    RootPageColumn(
        modifier = modifier.testTag("share_intake_root"),
    ) {
        CaptureShareHeader(onBack = { abandonShare(onDismiss) })
        WorkspacePageHeader("分享录入", subtitle = "从其他应用分享进来的图片或 PDF 会直接进入录入。")
        Spacer(Modifier.height(14.dp))
        when (val current = routeState) {
            CaptureShareRouteState.Working -> {
                WorkspacePanel {
                    Text(
                        text = "正在接收分享的文件…",
                        style = MaterialTheme.typography.titleSmall,
                        color = SmartColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("share_intake_progress"),
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = SmartColors.Jade,
                    )
                    Text(
                        text = "文件只在本机读取和保存，不会上传。",
                        color = SmartColors.InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            CaptureShareRouteState.Expired -> CaptureShareFailureCard(
                message = CAPTURE_SHARE_EXPIRED_MESSAGE,
                retryable = false,
                onRetry = {},
                onManualEntry = { abandonShare(onManualEntry) },
                onDismiss = { abandonShare(onDismiss) },
            )
            is CaptureShareRouteState.Failed -> CaptureShareFailureCard(
                message = current.message,
                retryable = captureShareRejectionIsRetryable(current.reason),
                onRetry = {
                    val retryAt = clock()
                    nowEpochMillis = retryAt
                    if (
                        !captureShareAuthorizationExpired(
                            nowEpochMillis = retryAt,
                            authorizedAtEpochMillis = authorizedAtEpochMillis,
                            timeoutMillis = authorizationTimeoutMillis,
                        )
                    ) {
                        attempt = CaptureShareRouteState.Working
                        retryNonce += 1
                    }
                },
                onManualEntry = { abandonShare(onManualEntry) },
                onDismiss = { abandonShare(onDismiss) },
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
        color = SmartColors.ContentSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            SmartColors.ErrorText.copy(alpha = 0.25f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                        .heightIn(min = 48.dp)
                        .testTag("share_intake_retry"),
                )
            }
            OutlineActionChip(
                text = "去手动录入",
                onClick = onManualEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("share_intake_manual"),
            )
            OutlineActionChip(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("share_intake_close"),
            )
        }
    }
}

@Composable
private fun CaptureShareHeader(onBack: () -> Unit) {
    ClaudePageHeader("接收分享", onBack = onBack)
}
