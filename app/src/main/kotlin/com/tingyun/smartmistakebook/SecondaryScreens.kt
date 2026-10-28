package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.ui.ErrorText
import androidx.compose.foundation.layout.heightIn
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import com.tingyun.smartmistakebook.core.ui.ClaudeAlertDialog as AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import com.tingyun.smartmistakebook.core.ui.ClaudeFilterChip as FilterChip
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton as OutlinedButton
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.work.WorkManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingyun.smartmistakebook.core.domain.ModelApiKey
import com.tingyun.smartmistakebook.core.domain.ModelCapabilityTestResult
import com.tingyun.smartmistakebook.core.domain.ModelCapabilityTester
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationIssue
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationMutationResult
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationUpdate
import com.tingyun.smartmistakebook.core.data.backup.BackupRestoreDatabaseClosedException
import com.tingyun.smartmistakebook.core.domain.BackupActivity
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory
import com.tingyun.smartmistakebook.core.domain.BackupManifestSummary
import com.tingyun.smartmistakebook.core.domain.BackupOptions
import com.tingyun.smartmistakebook.core.domain.BackupReceipt
import com.tingyun.smartmistakebook.core.domain.BackupRepository
import com.tingyun.smartmistakebook.core.domain.BackupSelection
import com.tingyun.smartmistakebook.core.domain.BackupSelectionSummary
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.RestoreSettingsOutcome
import com.tingyun.smartmistakebook.core.domain.StorageInventory
import com.tingyun.smartmistakebook.core.domain.currentCapabilityVerification
import com.tingyun.smartmistakebook.core.model.ModelProviderProtocol
import com.tingyun.smartmistakebook.core.model.AppCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationReport
import com.tingyun.smartmistakebook.core.model.NetworkMode
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import com.tingyun.smartmistakebook.diagnostics.DiagnosticsBundleExporter
import com.tingyun.smartmistakebook.diagnostics.DiagnosticsBootstrap
import java.io.OutputStream
import java.util.Arrays
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SecondaryHeader(title: String, onBack: () -> Unit) {
    com.tingyun.smartmistakebook.core.ui.ClaudePageHeader(title, onBack = onBack)
}

@Composable
internal fun CapabilityScreen(
    capabilities: AppCapabilitySnapshot,
    configurationStore: ModelConfigurationStore?,
    capabilityTester: ModelCapabilityTester?,
    onBack: () -> Unit,
    calibrationReportProvider: (suspend () -> CalibrationReport)? = null,
    /** 来源校准（含讲题判定那档）；见 [SourceCalibrationSection]。 */
    sourceCalibrationProvider: (suspend () -> List<com.tingyun.smartmistakebook.core.domain.SourceCalibration>)? = null,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val form: CapabilityFormViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val screenScope = form.operationScope
    val loadedConfiguration = configurationStore?.configuration
        ?.collectAsStateWithLifecycle(initialValue = null)
        ?.value
    val configuration = loadedConfiguration ?: ModelConfigurationSnapshot()
    var provider by form::provider
    var baseUrl by form::baseUrl
    var modelId by form::modelId
    var protocol by form::protocol
    var apiKey by form::apiKey
    var operation by form::operation
    var operationMessage by form::operationMessage
    /**
     * 清除配置与密钥**无法撤销**：清掉之后 Provider/Base URL/模型名/API Key 全丢，
     * 而密钥通常不在用户手边。删除全部数据那种更重的动作都有确认框，这里不能直清。
     */
    var confirmClear by remember { mutableStateOf(false) }
    val formEnabled = operation == CapabilityOperation.IDLE && loadedConfiguration != null
    var calibrationReport by remember { mutableStateOf<CalibrationReport?>(null) }
    var calibrationUnavailable by remember { mutableStateOf(false) }

    LaunchedEffect(calibrationReportProvider) {
        val provider = calibrationReportProvider ?: return@LaunchedEffect
        calibrationReport = try {
            provider()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            calibrationUnavailable = true
            null
        }
    }

    LaunchedEffect(loadedConfiguration?.configurationVersion, loadedConfiguration?.updatedAtEpochMillis) {
        // 未加载不是空配置，不能用首帧占位覆盖保留的编辑草稿。
        loadedConfiguration?.let(form::load)
    }

    RootPageColumn(modifier = Modifier.testTag("capability_screen")) {
        val committing = operation == CapabilityOperation.SAVING || operation == CapabilityOperation.CLEARING
        androidx.activity.compose.BackHandler(enabled = committing) { }
        SecondaryHeader(title = "模型设置", onBack = { if (!committing) onBack() })

        if (capabilities.networkRequestsAllowed && configurationStore != null) {
            Spacer(Modifier.height(16.dp))
            WorkspacePanel {
                Column {
                    SectionHeader("模型服务")
                    Text(
                        text = "应用直接连接你选择的服务商，不经过本应用云端。费用由你的服务商账户承担。",
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag("capability_direct_connection_notice"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "API Key 只保存在本机安全存储中，只用于连接服务商，不会作为内容发给模型。",
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 10.dp)
                            .testTag("capability_secret_notice"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "接口协议（须与服务商一致）",
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .testTag("capability_protocol_label"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    com.tingyun.smartmistakebook.core.ui.ClaudeSegmentedControl(
                        segments = ModelProviderProtocol.entries.map { candidate ->
                            com.tingyun.smartmistakebook.core.ui.ClaudeSegment(candidate.name,
                                candidate.displayLabel(), "capability_protocol_${candidate.name}")
                        },
                        selectedId = protocol.name,
                        onSelect = { selected -> protocol = ModelProviderProtocol.valueOf(selected); operationMessage = null },
                        enabled = formEnabled,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag("capability_protocol_group"),
                    )
                    OutlinedTextField(
                        value = provider,
                        onValueChange = {
                            provider = it
                            operationMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("capability_provider"),
                        label = { Text("服务商") },
                        enabled = formEnabled,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = {
                            modelId = it
                            operationMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("capability_model_id"),
                        label = { Text("模型 ID") },
                        enabled = formEnabled,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            operationMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("capability_base_url"),
                        label = { Text("Base URL（${protocol.baseUrlHint()}）") },
                        enabled = formEnabled,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            operationMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("capability_api_key"),
                        label = { Text("API Key") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = formEnabled,
                        singleLine = true,
                    )
                    PrimaryActionButton(
                        text = if (operation == CapabilityOperation.SAVING) {
                            "正在保存"
                        } else {
                            "保存配置"
                        },
                        onClick = {
                            val submittedProvider = provider
                            val submittedBaseUrl = baseUrl
                            val submittedModelId = modelId
                            val submittedProtocol = protocol
                            val submittedApiKey = apiKey
                            operation = CapabilityOperation.SAVING
                            operationMessage = null
                            screenScope.launch {
                                val sourceChars = submittedApiKey.toCharArray()
                                val secret = ModelApiKey.from(sourceChars)
                                try {
                                    val result = configurationStore.save(
                                        update = ModelConfigurationUpdate(
                                            provider = submittedProvider,
                                            baseUrl = submittedBaseUrl,
                                            modelId = submittedModelId,
                                            protocol = submittedProtocol,
                                        ),
                                        apiKey = secret,
                                    )
                                    operationMessage = result.toUserMessage(
                                        successMessage = "配置已保存在本机，请继续测试模型。",
                                    )
                                    if (result is ModelConfigurationMutationResult.Success) apiKey = ""
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    operationMessage = "本机安全存储发生异常，配置未生效。"
                                } finally {
                                    secret.close()
                                    Arrays.fill(sourceChars, '\u0000')
                                    operation = CapabilityOperation.IDLE
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .testTag("capability_save"),
                        enabled = formEnabled && provider.isNotBlank() &&
                            baseUrl.isNotBlank() && modelId.isNotBlank() && apiKey.isNotBlank(),
                    )
                    Text(
                        text = "测试仅发送内置样例，不发送你的题目或学习记录。",
                        modifier = Modifier.padding(top = 12.dp),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = {
                            operation = CapabilityOperation.TESTING
                            operationMessage = null
                            screenScope.launch {
                                try {
                                    operationMessage = capabilityTester
                                        ?.testSavedConfiguration()
                                        ?.toCapabilityTestMessage()
                                        ?: "能力测试暂不可用，请稍后再试。"
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    operationMessage = "能力测试暂未完成，请稍后再试。"
                                } finally {
                                    operation = CapabilityOperation.IDLE
                                }
                            }
                        },
                        enabled = formEnabled && capabilityTester != null &&
                            configuration.isConfigured && apiKey.isBlank() &&
                            provider == configuration.provider &&
                            baseUrl == configuration.baseUrl && modelId == configuration.modelId &&
                            protocol == configuration.protocol,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("capability_test"),
                    ) {
                        Text(
                            if (operation == CapabilityOperation.TESTING) {
                                "正在测试"
                            } else {
                                "测试模型能力"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        enabled = formEnabled &&
                            (configuration.isConfigured || configuration.provider.isNotBlank()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("capability_clear"),
                    ) {
                        Text(
                            if (operation == CapabilityOperation.CLEARING) {
                                "正在清除"
                            } else {
                                "清除本机配置与密钥"
                            },
                        )
                    }
                    operationMessage?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .testTag("capability_operation_message"),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "当前版本未启用大模型服务，因此不显示服务商、地址、API Key 和能力测试。",
                modifier = Modifier.testTag("capability_strict_offline_notice"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (calibrationReportProvider != null) {
            Spacer(Modifier.height(16.dp))
            WorkspacePanel {
                Column {
                    SectionHeader("学习预测效果")
                    val report = calibrationReport
                    when {
                        calibrationUnavailable -> Text(
                            text = "校准数据暂不可用，请稍后再试。",
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .testTag("capability_calibration_error"),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        report == null -> Text(
                            text = "正在读取校准数据…",
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .testTag("capability_calibration_loading"),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        report.resolvedPredictions == 0 -> Text(
                            text = "暂无可对比的预测。完成复习后，将在这里显示预测与实际作答的对比结果。",
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .testTag("capability_calibration_empty"),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        else -> Column(Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "模型 ${report.modelVersion.modelId} · ${report.modelVersion.version}",
                                modifier = Modifier.testTag("capability_calibration_model"),
                                color = InkSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "已验证预测 ${report.resolvedPredictions}/${report.totalPredictions}",
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .testTag("capability_calibration_resolved"),
                                color = Ink,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Brier 分数 " + "%.4f".format(report.overallBrierScore),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .testTag("capability_calibration_brier"),
                                color = Ink,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            report.overallLogLoss?.let { logLoss ->
                                Text(
                                    text = "对数损失 " + "%.4f".format(logLoss),
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .testTag("capability_calibration_log_loss"),
                                    color = Ink,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Text(
                                text = "期望校准误差 ECE " + "%.4f".format(report.expectedCalibrationError),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .testTag("capability_calibration_ece"),
                                color = Ink,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "最大分桶偏差 " + "%.4f".format(report.maximumCalibrationDeviation),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .testTag("capability_calibration_max_deviation"),
                                color = Ink,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (sourceCalibrationProvider != null) {
                        SourceCalibrationSection(
                            provider = requireNotNull(sourceCalibrationProvider),
                        )
                    }
                }
            }
        }
    }
    // 确认框放在函数末尾（而非列内），因为它只在配置存在时可达：触发它的按钮本身
    // 就在 `configurationStore != null` 的作用域里，这里把非空条件带上一并收窄。
    if (confirmClear && configurationStore != null) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除本机配置与密钥？") },
            text = {
                Text(
                    "将删除本机保存的服务商、地址、模型名和 API Key，且无法撤销。" +
                        "密钥需要重新输入。已保存的错题与学习记录不受影响。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        operation = CapabilityOperation.CLEARING
                        operationMessage = null
                        screenScope.launch {
                            try {
                                operationMessage = configurationStore.clear().toUserMessage(
                                    successMessage = "本机配置与密钥已清除。",
                                    storageUnavailableMessage = "本机安全存储暂不可用，配置未清除，密钥仍在。",
                                )
                                apiKey = ""
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                operationMessage = "本机安全存储发生异常，配置未清除。"
                            } finally {
                                operation = CapabilityOperation.IDLE
                            }
                        }
                    },
                    modifier = Modifier.testTag("capability_clear_confirm"),
                ) {
                    Text("确认清除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClear = false },
                    modifier = Modifier.testTag("capability_clear_cancel"),
                ) {
                    Text("取消")
                }
            },
        )
    }
}

internal fun capabilityVerificationSummary(configuration: ModelConfigurationSnapshot): String {
    val verification = configuration.currentCapabilityVerification()
        ?: return "配置已保存在本机，请先测试模型能力。使用拍照或讲题时，相关内容会直接发送给所选模型。"
    return when {
        verification.authenticationFailed ->
            "上次验证未通过，请检查或更新 API Key 后重试。"
        verification.supportsImageInput && verification.supportsStructuredOutput ->
            "测试通过：可识别题图并讲解。点击发送即交给模型处理，不再另行确认。"
        verification.supportsStructuredOutput ->
            "文字讲解可用，题图识别未通过测试。"
        verification.supportsImageInput ->
            "模型能识别测试图片，但回复格式不符合要求，暂不能使用 AI 功能。"
        else -> "能力测试未通过，当前模型暂不兼容。"
    }
}

internal fun ModelCapabilityTestResult.toCapabilityTestMessage(): String = when (this) {
    is ModelCapabilityTestResult.Completed -> when {
        verification.supportsImageInput && verification.supportsStructuredOutput ->
            "测试通过：图片读取和讲解都可用。"
        verification.supportsStructuredOutput ->
            "文字讲解可用，题图识别未通过测试。"
        verification.supportsImageInput ->
            "模型能识别图片，但回复格式不符合要求，暂不能使用 AI 功能。"
        else -> "测试未通过：当前模型暂不兼容。"
    }
    ModelCapabilityTestResult.NotConfigured -> "请先保存配置，再测试。"
    ModelCapabilityTestResult.ConfigurationChanged -> "配置刚刚发生变化，请重新测试。"
    ModelCapabilityTestResult.AuthenticationFailed -> "API Key 无效或已失效，请更新后重试。"
    ModelCapabilityTestResult.ConnectionFailed -> "暂时无法连接服务商，请检查网络后重试。"
    ModelCapabilityTestResult.ProviderUnavailable -> "服务商暂时没有完成测试，请稍后重试。"
    ModelCapabilityTestResult.InvalidServiceAddress -> "服务地址不可用，请检查后重新保存。"
    ModelCapabilityTestResult.StorageUnavailable -> "本机安全存储暂不可用，请稍后重试。"
}

@Composable
private fun CapabilityRow(icon: ImageVector, title: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(JadeSoft, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = JadeActive)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = InkSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun DataPrivacyScreen(
    capabilities: AppCapabilitySnapshot,
    onBack: () -> Unit,
) {
    RootPageColumn {
        SecondaryHeader(title = "数据与隐私", onBack = onBack)
        Spacer(Modifier.height(16.dp))
        WorkspacePanel {
            Column {
                SectionHeader("本机数据清单")
                Text(
                    "当前题面、原图、学习记录和复习进度都保存在本机应用空间。未完成的临时照片会按过期规则清理，不会自动进入错题本。",
                    modifier = Modifier.padding(top = 10.dp),
                    color = InkSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        WorkspacePanel {
            Column {
                SectionHeader("智能服务")
                Text(
                    text = when (capabilities.networkMode) {
                        NetworkMode.LOCAL_FIRST ->
                            "拍照、讲题或整理是你主动发起时才会进行；发起即把当前内容交给已配置模型，不再逐次询问。学习记录和整个错题本不会交给模型自行查看或修改。"

                        NetworkMode.STRICT_OFFLINE ->
                            "当前版本不使用联网智能服务，本机内容不会发送给模型服务。"
                    },
                    modifier = Modifier.padding(top = 10.dp),
                    color = InkSecondary,
                )
            }
        }
    }
}

@Composable
internal fun StorageScreen(
    onBack: () -> Unit,
    backupRepository: BackupRepository,
    /**
     * [replaceExisting] 为真表示这条消息来自**恢复成功的回执**（含包声明的 selection 与
     * 设置结果），比仓库在关库前发布的那条进度/计数消息更完整，必须覆盖它；失败/删除路径
     * 仍以仓库先发布的那条原因为准（`replaceExisting=false`）。
     */
    onDatabaseClosed: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val operations: StorageOperationsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val scope = operations.operationScope
    var inventory by remember { mutableStateOf<StorageInventory?>(null) }
    var inventoryFailed by remember { mutableStateOf(false) }
    var inventoryRetry by remember { mutableStateOf(0) }
    var operationMessage by operations::message
    var operationBusy by operations::busy
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // T6：诊断包导出的操作消息（与备份操作消息分开，避免互相覆盖）。
    var diagnosticsMessage by remember { mutableStateOf<String?>(null) }
    // 6C-2：点「创建」那一刻的内容选择。系统文件选择器返回前面板仍可能重组，
    // 因此把选择冻结在这里再交给 create，而不是在回调里重新读界面状态。
    var pendingSelectionMask by rememberSaveable {
        mutableStateOf(BackupContentCategory.FULL_MASK)
    }
    // 6C-2 恢复前置确认：先校验拿到包声明的 selection，再让用户确认"未包含的类别会被
    // 清空"。确认前不调用 restore——通用按钮不能一按就替换整机数据。
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }

    LaunchedEffect(backupRepository, inventoryRetry) {
        val result = runCatching { backupRepository.inspect() }
        inventory = result.getOrNull()
        // 读取失败与"正在读取"必须分开：不能永远停在加载中文案。
        inventoryFailed = result.isFailure
        OrphanAssetGc.enqueue(context)
    }
    val orphanGcInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(OrphanAssetGc.UNIQUE_NAME)
    }.collectAsState(initial = emptyList())
    val orphanGcStatus = orphanAssetGcStatusLine(
        orphanAssetGcPhase(orphanGcInfos.map { info -> info.state.name }),
    )
    // KD-54：备份快照阶段的显式状态（写入口按同一道门短暂排队）。
    val backupActivity by backupRepository.activity.collectAsState()

    fun performRestore(uri: Uri) {
        operationBusy = true
        operationMessage = "正在校验并恢复备份…"
        scope.launch {
            try {
                val receipt = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        backupRepository.restore(input)
                    }
                }
                operationMessage = if (receipt != null) {
                    storageRestoreCompletionMessage(
                        problemCount = receipt.problemCount,
                        assetCount = receipt.assetCount,
                        settings = receipt.settings,
                        restartRequired = receipt.restartRequired,
                        selection = receipt.selection,
                    )
                } else {
                    "无法读取所选备份。"
                }
                if (receipt?.restartRequired == true) {
                    // 回执含 selection（未包含类别已清空），必须覆盖仓库发出的通用进度/计数文案。
                    onDatabaseClosed(operationMessage.orEmpty(), true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                operationMessage = storageRestoreFailureMessage(failure)
                if (failure is BackupRestoreDatabaseClosedException) {
                    onDatabaseClosed(operationMessage.orEmpty(), false)
                }
            } finally {
                operationBusy = false
            }
        }
    }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        operationBusy = true
        operationMessage = "正在创建备份…"
        scope.launch {
            try {
                val selection = backupSelectionOfMask(pendingSelectionMask)
                val receipt = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        createBackupArchive(backupRepository, output, selection)
                    }
                }
                operationMessage = if (receipt != null) {
                    "备份完成：${receipt.problemCount} 道题、${receipt.assetCount} 张题图。" +
                        backupSelectionSentence(receipt.selectionMask)
                } else {
                    "无法写入所选位置，备份未完成。"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                operationMessage = "备份失败，原数据不受影响。${failure.message.orEmpty()}"
            } finally {
                operationBusy = false
            }
        }
    }

    val validateBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        operationBusy = true
        operationMessage = "正在校验备份…"
        scope.launch {
            try {
                val validation = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        backupRepository.validate(input)
                    }
                }
                operationMessage = when (validation) {
                    is BackupValidation.Valid ->
                        "备份有效：${validation.checkedFileCount} 个文件全部通过校验。" +
                            backupManifestSelectionSentence(validation.manifest)
                    is BackupValidation.Invalid -> "备份无效：${validation.reason}"
                    null -> "无法读取所选备份。"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                operationMessage = "校验失败，文件可能已损坏。${failure.message.orEmpty()}"
            } finally {
                operationBusy = false
            }
        }
    }

    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        operationBusy = true
        operationMessage = "正在校验备份…"
        scope.launch {
            try {
                // 先校验：拿到 manifest 的 selection 后才能把「这份包只含…、未包含的类别
                // 会被清空」说全。校验只读流，不碰本机数据；确认后再走 restore。
                val validation = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        backupRepository.validate(input)
                    }
                }
                when (validation) {
                    is BackupValidation.Valid -> {
                        pendingRestore = PendingRestore(uri, validation.manifest)
                        operationMessage = null
                    }
                    is BackupValidation.Invalid ->
                        operationMessage = "备份无效，恢复未开始：${validation.reason}"
                    null -> operationMessage = "无法读取所选备份。"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                operationMessage = "校验失败，文件可能已损坏，恢复未开始。${failure.message.orEmpty()}"
            } finally {
                operationBusy = false
            }
        }
    }

    // T6：导出诊断包——SAF CreateDocument + 保存后复读校验（照备份导出的既有模式）。
    // 诊断记录只存本机，导出是它离开设备的唯一路径（用户显式选择目标，不自动外发）。
    val exportDiagnostics = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        operationBusy = true
        diagnosticsMessage = "正在导出诊断包…"
        scope.launch {
            try {
                val ledger = DiagnosticsBootstrap.ledger
                diagnosticsMessage = if (ledger == null) {
                    "本机暂无诊断记录。"
                } else {
                    val savedDigest = withContext(Dispatchers.IO) {
                        val bundle = DiagnosticsBundleExporter.bundleText(
                            events = ledger.readAll(),
                            meta = DiagnosticsBundleExporter.metaFrom(
                                context,
                                DiagnosticsBootstrap.flavor,
                            ),
                        )
                        DiagnosticsBundleExporter.writeAndVerify(context, uri, bundle)
                    }
                    if (savedDigest != null) {
                        "诊断包已导出并校验通过。"
                    } else {
                        "无法写入所选位置，导出未完成。"
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                diagnosticsMessage = "导出失败，本机记录不受影响。${failure.message.orEmpty()}"
            } finally {
                operationBusy = false
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除全部数据？") },
            text = {
                Text("会删除本机数据库、题图、设置和临时文件，且不会自动备份。请先确认已创建备份。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        operationBusy = true
                        operationMessage = "正在删除全部数据…"
                        scope.launch {
                            try {
                                val receipt = withContext(Dispatchers.IO) {
                                    backupRepository.deleteAllData()
                                }
                                operationMessage =
                                    "已删除数据库 ${formatBytes(receipt.deletedDatabaseBytes)}、" +
                                        "题图 ${formatBytes(receipt.deletedAssetBytes)}、" +
                                        "设置和临时文件。请重新打开应用。"
                                onDatabaseClosed(operationMessage.orEmpty(), false)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                operationMessage =
                                    "删除未全部完成，请重启后重试。${failure.message.orEmpty()}"
                                onDatabaseClosed(operationMessage.orEmpty(), false)
                            } finally {
                                operationBusy = false
                            }
                        }
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
    }

    pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("恢复这份备份？") },
            text = {
                Text(
                    text = backupRestoreConfirmationText(pending.manifest),
                    modifier = Modifier.testTag("storage_restore_selection_confirmation"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        performRestore(pending.uri)
                    },
                ) {
                    Text("确认恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text("取消")
                }
            },
        )
    }

    RootPageColumn(modifier = Modifier.testTag("storage_screen")) {
        androidx.activity.compose.BackHandler(enabled = operationBusy) { }
        SecondaryHeader(title = "存储、备份与导出", onBack = { if (!operationBusy) onBack() })
        Spacer(Modifier.height(16.dp))
        WorkspacePanel {
            Column {
                SectionHeader("本机存储")
                val current = inventory
                Text(
                    text = when {
                        current != null ->
                            "学习记录 ${formatBytes(current.databaseBytes)} · 题图 ${formatBytes(current.assetBytes)} · 可清理临时文件 ${formatBytes(current.cleanableBytes)}"
                        inventoryFailed -> "暂时读不到本机占用。"
                        else -> "正在读取本机占用…"
                    },
                    modifier = Modifier.padding(top = 10.dp),
                    color = if (inventoryFailed) ErrorText else InkSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (inventoryFailed && current == null) {
                    OutlinedButton(
                        onClick = { inventoryRetry += 1 },
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .testTag("storage_inventory_retry"),
                    ) { Text("重试读取") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        WorkspacePanel {
            Column {
                SectionHeader("备份内容")
                Text(
                    "仅备份下方所选内容，不含 API Key、发送授权和临时文件。" +
                        "恢复前会校验文件并请你确认内容，再替换本机数据；失败时尝试恢复原数据。",
                    modifier = Modifier.padding(top = 10.dp),
                    color = InkSecondary,
                )
                if (backupActivity != BackupActivity.Idle) {
                    Text(
                        "正在创建备份，其他保存操作会在备份完成后继续。",
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .testTag("storage_backup_activity"),
                        color = InkSecondary,
                    )
                }
                BackupContentSelectionSection(
                    enabled = !operationBusy,
                    busy = operationBusy,
                    onCreateBackup = { selection ->
                        pendingSelectionMask = selection.mask
                        createBackup.launch("smart-mistake-book-${System.currentTimeMillis()}.smbk")
                    },
                )
                OutlinedButton(
                    onClick = { validateBackup.launch(arrayOf("application/octet-stream", "application/zip")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("storage_validate_backup"),
                    enabled = !operationBusy,
                ) {
                    Text("检查备份文件")
                }
                OutlinedButton(
                    onClick = {
                        restoreBackup.launch(
                            arrayOf("application/octet-stream", "application/zip"),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("storage_restore_backup"),
                    enabled = !operationBusy,
                ) {
                    Text("从备份恢复（替换当前数据）")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        WorkspacePanel {
            Column {
                SectionHeader("数据维护")
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("storage_delete_all"),
                    enabled = !operationBusy,
                ) {
                    Text("删除全部数据")
                }
                OutlinedButton(
                    onClick = {
                        if (operationBusy) return@OutlinedButton
                        operationBusy = true
                        operationMessage = null
                        scope.launch {
                            try {
                                val removed = withContext(Dispatchers.IO) {
                                    backupRepository.cleanupOrphanAssets()
                                }
                                operationMessage = if (removed == 0) {
                                    "没有可清理的未使用题图。"
                                } else {
                                    "已清理 $removed 张未使用的题图。"
                                }
                                inventory = withContext(Dispatchers.IO) {
                                    backupRepository.inspect()
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                operationMessage =
                                    "清理未完成，原题图不受影响。${failure.message.orEmpty()}"
                            } finally {
                                operationBusy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("storage_cleanup_orphans"),
                    enabled = !operationBusy,
                ) {
                    Text("清理未使用的题图")
                }
                orphanGcStatus?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag("storage_orphan_gc_status"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                operationMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag("storage_operation_message"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        // T6 诊断节：启动自检"上次异常退出"的只读提示 + 本机诊断包导出入口。
        // 横幅不自动外发、可忽略；Root/启动横幅是本批禁改热区，提示落在本设置页，
        // 学生反馈问题时从同一页导出诊断包（本地优先闭环，R2 选型 §1）。
        Spacer(Modifier.height(16.dp))
        WorkspacePanel {
            Column {
                SectionHeader("诊断")
                DiagnosticsBootstrap.lastExitNotice?.let { notice ->
                    Text(
                        text = notice,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag("diagnostics_exit_notice"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "崩溃和无响应记录仅保存在本机，有容量上限，不会自动上传。" +
                        "诊断包含最近的问题记录和应用版本，可用于反馈问题。",
                    modifier = Modifier.padding(top = 10.dp),
                    color = InkSecondary,
                )
                OutlinedButton(
                    onClick = {
                        exportDiagnostics.launch(
                            "smart-mistake-book-diagnostics-${System.currentTimeMillis()}.txt",
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("diagnostics_export"),
                    enabled = !operationBusy,
                ) {
                    Text("导出诊断包")
                }
                diagnosticsMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag("diagnostics_operation_message"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        WorkspacePanel {
            Column {
                SectionHeader("单题导出")
                Text(
                    "单道错题可在详情页保存或打印 PDF；错题本还能把当前筛选结果整理成一份 A4 练习。两种方式都只使用已经确认的正式题面。",
                    modifier = Modifier.padding(top = 10.dp),
                    color = InkSecondary,
                )
            }
        }
    }
}

/**
 * 6C-2 备份内容勾选面板：四个类别、依赖闭包与 0/8 的界面禁令。
 *
 * 消灭的失败：
 * 1. 用户勾「错题本」却漏掉图片 → 包内题面引用缺失的资产，恢复后详情页无图。
 *    勾选走 [BackupSelection.closed] 的同一份闭包（不在这里复制依赖表）。
 * 2. 取消「会话」却留下依赖它的「掌握库」→ 未闭合组合被仓储拒绝（或要求静默补齐）。
 *    取消一个类别时联动取消所有依赖它的已选类别。
 * 3. 用户清空选择或只留图片 → mask 0（重置包）或 mask 8（恢复后被孤儿回收清空）。
 *    勾选/取消只在结果 ∈ {9,13,15} 时生效；其余点击被忽略并把原因写在行下。
 *
 * 选择状态归面板所有：进入页面默认全选，离开页面丢弃（[rememberSaveable] 只跨重建）。
 */
@Composable
internal fun BackupContentSelectionSection(
    enabled: Boolean,
    busy: Boolean,
    onCreateBackup: (BackupSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedMask by rememberSaveable {
        mutableStateOf(BackupContentCategory.FULL_MASK)
    }
    val selection = backupSelectionOfMask(selectedMask)

    Column(modifier = modifier.testTag("backup_selection_section")) {
        Text(
            "选择要备份的内容。题图随错题本保存；学习进度需同时包含讲题记录。" +
                "未勾选的内容不进入备份，恢复时会清空本机对应内容。",
            modifier = Modifier.padding(top = 6.dp),
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        for (category in BackupContentCategory.ALL) {
            val checked = category in selection.categories
            // 只有「取消后仍是允许组合」的类别可点；0/8 因此永远不可达。
            val removable = checked &&
                toggleBackupCategory(selection, category, checked = false) != selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { next ->
                        selectedMask = toggleBackupCategory(selection, category, next).mask
                    },
                    enabled = enabled && (!checked || removable),
                    modifier = Modifier.testTag("backup_category_${category.name}"),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        backupCategoryLabel(category),
                        color = Ink,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        backupCategoryDescription(category),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (checked && !removable) {
                        Text(
                            backupCategoryLockReason(category),
                            modifier = Modifier.testTag("backup_category_lock_${category.name}"),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        Text(
            backupSelectionSummaryText(selection),
            modifier = Modifier
                .padding(top = 10.dp)
                .testTag("backup_selection_summary"),
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        PrimaryActionButton(
            text = if (busy) "正在处理…" else "创建备份",
            onClick = { onCreateBackup(selection) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag("storage_create_backup"),
            enabled = enabled,
        )
    }
}

/** mask → 选择。只认四个已知位；界面永远从允许组合生成，不做补齐。 */
internal fun backupSelectionOfMask(mask: Int): BackupSelection =
    BackupSelection.of(
        BackupContentCategory.ALL
            .filter { category -> mask and category.bit != 0 }
            .toSet(),
    )

/** 成包调用点：选择必须先过域闸门，再交给仓储（0/8 与未闭合组合不会到达仓储）。 */
internal suspend fun createBackupArchive(
    repository: BackupRepository,
    output: OutputStream,
    selection: BackupSelection,
): BackupReceipt {
    selection.requireCreatable()
    return repository.create(output, BackupOptions(selection = selection))
}

/**
 * 勾选传播（唯一入口）：勾选补全依赖闭包；取消联动取消依赖它的类别。
 *
 * 结果不是 {9,13,15} 时原样返回（点击无效）——0/8 与未闭合组合不可达，
 * 也不静默补齐或缩水。闭包计算复用 [BackupSelection.closed]，不复制依赖表。
 */
internal fun toggleBackupCategory(
    selection: BackupSelection,
    category: BackupContentCategory,
    checked: Boolean,
): BackupSelection {
    val current = selection.categories
    if (checked) {
        if (category in current) return selection
        val candidate = BackupSelection.of(current + category).closed()
        return if (candidate.mask in BackupSelection.ALLOWED_MASKS) candidate else selection
    }
    if (category !in current) return selection
    val remaining = current
        .filter { selected ->
            selected != category &&
                category !in BackupSelection.of(setOf(selected)).closed().categories
        }
        .toSet()
    val candidate = BackupSelection.of(remaining)
    return if (candidate.mask in BackupSelection.ALLOWED_MASKS) candidate else selection
}

internal fun backupCategoryLabel(category: BackupContentCategory): String = when (category) {
    BackupContentCategory.ISSUE_BOOK -> "错题本"
    BackupContentCategory.MASTERY -> "学习进度"
    BackupContentCategory.SESSIONS -> "讲题记录"
    BackupContentCategory.IMAGES -> "图片"
}

private fun backupCategoryDescription(category: BackupContentCategory): String = when (category) {
    BackupContentCategory.ISSUE_BOOK -> "题目及历史版本、练习、草稿、标签和关联题。"
    BackupContentCategory.MASTERY -> "作答记录、复习计划、学习预测和建议。"
    BackupContentCategory.SESSIONS -> "讲题对话、消息和附图。"
    BackupContentCategory.IMAGES -> "已保存的题图，随错题本一起备份。"
}

private fun backupCategoryLockReason(category: BackupContentCategory): String = when (category) {
    BackupContentCategory.ISSUE_BOOK -> "错题本和题图是备份的基础，至少保留。"
    BackupContentCategory.IMAGES -> "题图随错题本一起备份，不能单独取消。"
    BackupContentCategory.SESSIONS -> "其他已选内容需要讲题记录，不能单独取消。"
    BackupContentCategory.MASTERY -> "其他已选内容需要学习进度，不能单独取消。"
}

internal fun backupCategoriesLabel(categories: Iterable<BackupContentCategory>): String =
    categories.sortedBy { it.ordinal }.joinToString("、") { backupCategoryLabel(it) }

/**
 * 类别集合 → 内容命名（批次 19 T2-6）：全选只列类别；子集括注**不含**的类别
 * （「错题本、图片（不含掌握库、会话）」口径）。创建面板摘要、创建回执、恢复前确认、
 * 恢复回执四处共用——同一份包在四处看到的是同一句话，不再一律像"完整备份"。
 */
internal fun backupSelectionContentLabel(categories: Set<BackupContentCategory>): String {
    val included = backupCategoriesLabel(categories)
    if (BackupContentCategory.maskOf(categories) == BackupContentCategory.FULL_MASK) {
        return included
    }
    val excluded = BackupContentCategory.ALL - categories
    return "$included（不含${backupCategoriesLabel(excluded)}）"
}

/**
 * 资产策略短语（批次 19 T2-2）：转述 manifest 的 `assetPolicy`——INCLUDED=题图字节随包；
 * 其它策略如实报值不猜（当前读侧只放行 INCLUDED，后半分支给未来策略留诚实转述）。
 */
internal fun backupAssetPolicyPhrase(assetPolicy: String): String =
    if (assetPolicy == BackupSelectionSummary.ASSET_POLICY_INCLUDED) {
        "题图随包"
    } else {
        "题图策略未知（$assetPolicy）"
    }

internal fun backupSelectionSummaryText(selection: BackupSelection): String {
    val label = "将备份：${backupSelectionContentLabel(selection.categories)}。"
    return if (selection.isFull) {
        label
    } else {
        label + "未勾选的内容不进入备份，恢复时会清空本机对应内容。"
    }
}

/** 成包回执里的内容选择（来自仓储如实回报的 mask）；资产策略按写侧唯一值随句说明。 */
internal fun backupSelectionSentence(mask: Int): String =
    "备份内容：${backupSelectionContentLabel(backupSelectionOfMask(mask).categories)}；" +
        backupAssetPolicyPhrase(BackupSelectionSummary.ASSET_POLICY_INCLUDED) + "。"

/**
 * 包声明的选择的一句话（校验成功提示、恢复前确认共用）。
 *
 * v1/v2（selection=null）按全量说明；子集必须点名**未包含的类别**并说明恢复的替换语义
 * 会把它们清空——这正是设计 §3.3 要求恢复前说全的那句话。资产策略（assetPolicy）
 * 与创建回执同口径转述（批次 19 T2-2/T2-6）。
 */
internal fun backupManifestSelectionSentence(manifest: BackupManifestSummary): String {
    val selection = manifest.selection
        ?: return "备份内容：全部（旧版备份按全量恢复）。"
    val label = "备份内容：${backupSelectionContentLabel(selection.categories)}；" +
        backupAssetPolicyPhrase(selection.assetPolicy) + "。"
    if (selection.isFull) return label
    return label + "恢复时，备份中不含的内容也会从本机清空。"
}

/** 恢复前确认框正文：包内容 + 替换语义 + "当前数据不会自动备份"。 */
internal fun backupRestoreConfirmationText(manifest: BackupManifestSummary): String =
    backupManifestSelectionSentence(manifest) +
        "恢复将替换本机全部数据，且不会自动备份。请先创建备份。"

/** 已通过校验、等待用户确认的恢复目标。 */
private data class PendingRestore(
    val uri: Uri,
    val manifest: BackupManifestSummary,
)

/**
 * 恢复完成后的面向用户文案（KD-47）。
 *
 * 消灭的失败：`WRITE_FAILED` 旧文案说“本机设置保持原样”，但恢复设置是**多个键的顺序写**
 * （选项、考试、优化参数、开关、提醒时间），非原子——中途失败时前面已成功的键会保留，
 * 本机设置可能半新半旧。文案只能如实说“未能完整写入、可能有部分设置已变”。
 *
 * 6C-2：回执必须说明这份包带了哪些类别；`null` 是 v1/v2 旧包，按全量理解。
 * 恢复成功时这条文案会以 `replaceExisting=true` 交给重启页，覆盖仓库在关库前发布的
 * 通用进度/计数消息——用户最终看到的重启说明必须包含 selection。
 */
internal fun storageRestoreCompletionMessage(
    problemCount: Int,
    assetCount: Int,
    settings: RestoreSettingsOutcome,
    restartRequired: Boolean,
    selection: BackupSelectionSummary? = null,
): String {
    val settingsNote = if (settings == RestoreSettingsOutcome.WRITE_FAILED) {
        "设置未能完整写入，可能有部分设置已变。"
    } else {
        ""
    }
    val restartNote = if (restartRequired) {
        "请完全退出并重新打开应用。"
    } else {
        ""
    }
    val selectionNote = if (selection == null) {
        "备份内容：全部（旧版备份按全量恢复）。"
    } else {
        // 恢复是替换语义：没勾的类别已被清空，回执必须如实说明（T1 RestoreReceipt.selection）。
        // 批次 19 T2-2/T2-6：与恢复前确认、创建回执同口径——点名不含的类别 + 转述资产策略。
        val replacedNote = if (selection.isFull) "" else "备份中未包含的内容已从本机清空。"
        "备份内容：${backupSelectionContentLabel(selection.categories)}；" +
            backupAssetPolicyPhrase(selection.assetPolicy) + "。$replacedNote"
    }
    return "恢复完成：$problemCount 道题、$assetCount 张题图。$selectionNote$settingsNote$restartNote"
}

internal fun storageRestoreFailureMessage(failure: Exception): String =
    if (failure is BackupRestoreDatabaseClosedException) {
        val detail = failure.message?.takeIf(String::isNotBlank)
            ?: "恢复未完成，当前本机数据状态需要核对。"
        "$detail 请关闭应用后重新打开，并核对本机数据。"
    } else {
        "恢复失败，已尝试保留原数据。${failure.message.orEmpty()}"
    }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

private fun ModelConfigurationMutationResult.toUserMessage(
    successMessage: String,
    /** 失败文案随操作不同：保存失败是"未生效"，清除失败是"未清除、密钥仍在"。 */
    storageUnavailableMessage: String = "本机安全存储暂不可用，配置未生效。",
): String = when (this) {
    is ModelConfigurationMutationResult.Success -> successMessage

    is ModelConfigurationMutationResult.Rejected -> {
        val fields = issues.mapTo(linkedSetOf()) { issue ->
            when (issue) {
                ModelConfigurationIssue.PROVIDER_REQUIRED,
                ModelConfigurationIssue.PROVIDER_TOO_LONG,
                ModelConfigurationIssue.PROVIDER_CONTAINS_CONTROL_CHARACTER,
                -> "服务商"

                ModelConfigurationIssue.BASE_URL_REQUIRED,
                ModelConfigurationIssue.BASE_URL_TOO_LONG,
                ModelConfigurationIssue.BASE_URL_INVALID,
                ModelConfigurationIssue.BASE_URL_CONTAINS_CONTROL_CHARACTER,
                ModelConfigurationIssue.BASE_URL_CREDENTIALS_NOT_ALLOWED,
                ModelConfigurationIssue.BASE_URL_QUERY_OR_FRAGMENT_NOT_ALLOWED,
                ModelConfigurationIssue.BASE_URL_PATH_NOT_ALLOWED,
                ModelConfigurationIssue.INSECURE_HTTP_NOT_ALLOWED,
                -> "Base URL（请填写不含账号信息、查询参数或不安全路径的 HTTPS 地址）"

                ModelConfigurationIssue.MODEL_ID_REQUIRED,
                ModelConfigurationIssue.MODEL_ID_TOO_LONG,
                ModelConfigurationIssue.MODEL_ID_CONTAINS_CONTROL_CHARACTER,
                -> "模型 ID"

                ModelConfigurationIssue.API_KEY_REQUIRED,
                ModelConfigurationIssue.API_KEY_TOO_LONG,
                ModelConfigurationIssue.API_KEY_CONTAINS_WHITESPACE_OR_CONTROL_CHARACTER,
                -> "API Key"
            }
        }
        "配置未保存，请检查：${fields.joinToString("、")}。"
    }

    ModelConfigurationMutationResult.MissingConfiguration -> "本机没有可更新的配置。"
    ModelConfigurationMutationResult.StorageUnavailable -> storageUnavailableMessage
}

/** 设置界面里协议的中文标签（spec 2026-09-08-multi-protocol §3.2 显式协议选择）。 */
private fun ModelProviderProtocol.displayLabel(): String = when (this) {
    ModelProviderProtocol.OPENAI_CHAT_COMPLETIONS -> "OpenAI 兼容（默认）"
    ModelProviderProtocol.OPENAI_RESPONSES -> "OpenAI Responses"
    ModelProviderProtocol.ANTHROPIC_MESSAGES -> "Anthropic Messages"
    ModelProviderProtocol.GEMINI_GENERATE_CONTENT -> "Google Gemini"
}

/**
 * Base URL 该填什么由协议决定：各协议的端点路径不同（如 Anthropic 是 /v1/messages、
 * Gemini 是 /v1beta/models/{model}:generateContent），填错会直接连不通。
 */
private fun ModelProviderProtocol.baseUrlHint(): String = when (this) {
    ModelProviderProtocol.OPENAI_CHAT_COMPLETIONS -> "例如 https://服务地址/v1"
    ModelProviderProtocol.OPENAI_RESPONSES -> "例如 https://api.openai.com/v1"
    ModelProviderProtocol.ANTHROPIC_MESSAGES -> "例如 https://api.anthropic.com"
    ModelProviderProtocol.GEMINI_GENERATE_CONTENT -> "例如 https://generativelanguage.googleapis.com"
}
