package com.tingyun.smartmistakebook.feature.review

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingyun.smartmistakebook.core.domain.*
import com.tingyun.smartmistakebook.core.ui.*
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import com.tingyun.smartmistakebook.core.ui.ClaudeAlertDialog as AlertDialog
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperReviewRoute(
    repository: PaperReviewRepository, initialPaperId: String?, details: MistakeDetailRepository,
    onBack: () -> Unit, onOpenExports: (String) -> Unit, onDetailedAnswers: (String) -> Unit,
    onAskAgent: (String, Int, Set<String>) -> Unit,
    externalMessage: String? = null, externalBusy: Boolean = false, onDismissExternalMessage: () -> Unit = {},
) {
    val factory = remember(repository) { viewModelFactory { initializer { PaperReviewViewModel(repository, createSavedStateHandle()) } } }
    val vm: PaperReviewViewModel = viewModel(factory = factory)
    val working by vm.busy.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val draftVersion by vm.draftVersion.collectAsStateWithLifecycle()
    val busy = working || externalBusy
    var selected by rememberSaveable { mutableStateOf(initialPaperId) }
    val papers by remember(repository) { repository.observe().catch {
        vm.reportFailure("纸卷记录暂时无法读取，请返回后重试。"); emit(emptyList())
    } }.collectAsStateWithLifecycle(initialValue = null)
    val detail by remember(repository, selected) {
        (selected?.let(repository::observe) ?: flowOf(null)).catch {
            vm.reportFailure("这份纸卷暂时无法读取，请返回后重试。", selected); emit(null)
        }
    }.collectAsStateWithLifecycle(initialValue = null)
    val current = detail?.takeIf { it.paperId == selected } ?: papers?.firstOrNull { it.paperId == selected }
    var phase by rememberSaveable(selected) { mutableIntStateOf(-1) }
    var ordinal by rememberSaveable(selected) { mutableIntStateOf(-1) }
    LaunchedEffect(current?.paperId) {
        current?.let { paper ->
            if (phase < 0) phase = if (paper.photos.isEmpty()) 0 else 2
            if (ordinal < 0) ordinal = paper.questions.firstOrNull { !it.applied }?.ordinal ?: paper.questions.firstOrNull()?.ordinal ?: -1
        }
    }
    var discardDialog by remember { mutableStateOf(false) }
    fun leave() {
        if (busy) return
        if (current != null && vm.hasUnsaved(current.paperId, current.questions)) discardDialog = true
        else if (selected != null) { selected = null; vm.clearMessage() } else onBack()
    }
    BackHandler { leave() }
    val context = LocalContext.current
    var viewer by remember { mutableStateOf<Pair<String, String>?>(null) }
    var uploadSheet by rememberSaveable { mutableStateOf(false) }
    var uploadOrdinal by rememberSaveable { mutableStateOf<Int?>(null) }
    var corrected by rememberSaveable { mutableStateOf(false) }
    // The picker result belongs to the context captured when it was launched.
    var intakePaper by rememberSaveable { mutableStateOf<String?>(null) }
    var intakeOrdinal by rememberSaveable { mutableStateOf<Int?>(null) }
    var intakeCorrected by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var agentSheet by rememberSaveable { mutableStateOf(false) }
    var selectedPhotos by rememberSaveable(selected, ordinal) { mutableStateOf(emptyList<String>()) }
    fun uploaded(uris: List<String>) {
        val id = intakePaper ?: return
        if (uris.isNotEmpty()) vm.upload(id, uris, intakeCorrected, intakeOrdinal) { phase = 2 }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> uploaded(uris.map(Uri::toString)) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { uploaded(listOf(it)) }
    }
    fun freezeIntake() { intakePaper = selected; intakeOrdinal = uploadOrdinal; intakeCorrected = corrected; uploadSheet = false }
    fun openCamera() {
        freezeIntake()
        try {
            val folder = File(context.cacheDir, "captured_images").apply { check(exists() || mkdirs()) }
            val file = File.createTempFile("paper-answer-", ".jpg", folder)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.capture.fileprovider", file)
            cameraUri = uri.toString(); camera.launch(uri)
        } catch (_: Exception) { vm.reportFailure("相机暂时不可用，请从相册选择照片。", selected, uploadOrdinal) }
    }
    fun uploadFor(target: Int?) { uploadOrdinal = target; corrected = false; uploadSheet = true }
    fun pickTime(paper: PaperReview, question: PaperReviewQuestion) {
        val calendar = Calendar.getInstance().apply { vm.draft(paper.paperId, question).answeredAt?.let { timeInMillis = it } }
        val picker = DatePickerDialog(context, { _, year, month, day ->
            calendar.set(year, month, day)
            TimePickerDialog(context, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                val chosen = calendar.timeInMillis
                val at = if (chosen / 60_000 == paper.createdAtEpochMillis / 60_000) maxOf(chosen, paper.createdAtEpochMillis) else chosen
                vm.setAnsweredAt(paper, question, at)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        picker.datePicker.minDate = paper.createdAtEpochMillis
        picker.datePicker.maxDate = System.currentTimeMillis()
        picker.show()
    }
    Column(Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = SmartDimens.MaximumContentWidth).testTag("paper_review_screen")) {
        ClaudePageHeader(if (selected == null) "纸质复习" else "纸质复习工作台", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onBack = ::leave)
        val visibleFeedback = feedback?.takeIf { it.paperId == null || it.paperId == selected }
        if (visibleFeedback != null || !externalMessage.isNullOrBlank()) {
            WorkspaceNotice(externalMessage?.takeIf(String::isNotBlank) ?: visibleFeedback?.text.orEmpty(), Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("paper_review_message"),
                tone = if (!externalMessage.isNullOrBlank() || visibleFeedback?.error == true) WorkspaceNoticeTone.ERROR else WorkspaceNoticeTone.SUCCESS)
            InlineActionButton("收起提示", { vm.clearMessage(); onDismissExternalMessage() }, Modifier.padding(horizontal = 16.dp))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (selected == null) {
                item { WorkspaceNotice("打印作答后，回传照片并逐题确认。导出不计为完成复习。", title = "把纸上练习接回复习记录") }
                when {
                    papers == null -> item { Text("正在读取纸卷记录…") }
                    papers!!.isEmpty() -> item {
                        ScreenStatePanel(
                            icon = Icons.Outlined.Print,
                            title = "还没有纸质复习记录",
                            supportingText = "从今日复习导出错题，打印作答后回传。",
                            actionLabel = "返回今日复习", onAction = onBack,
                            modifier = Modifier.testTag("paper_review_empty"),
                        )
                    }
                    else -> items(papers!!, key = { it.paperId }) { paper -> ClaudeCard(onClick = { selected = paper.paperId }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(paperDateTime(paper.createdAtEpochMillis), style = MaterialTheme.typography.titleMedium)
                            Text("${paper.questions.size} 道题 · ${paperStatus(paper)}", color = SmartColors.InkSecondary)
                            LinearProgressIndicator(progress = { if (paper.questions.isEmpty()) 0f else paper.completedCount.toFloat() / paper.questions.size }, modifier = Modifier.fillMaxWidth())
                            Text("已计入 ${paper.completedCount} / ${paper.questions.size} · ${paper.photos.size} 张作答照片", style = MaterialTheme.typography.bodySmall, color = SmartColors.InkSecondary)
                        }
                    } }
                }
            } else if (current == null) item { WorkspaceNotice(if (papers == null) "正在读取纸卷…" else "这份纸卷记录暂时不可用，请返回选择其他纸卷。") }
            else {
                val paper = current
                val question = paper.questions.firstOrNull { it.ordinal == ordinal }
                item {
                    WorkspaceJourney(listOf("打印纸卷", "回传作答", "核对结果"), phase.coerceAtLeast(0),
                        onStepClick = { phase = it }, enabled = !busy)
                    Text("${paper.questions.size} 道题 · 已计入 ${paper.completedCount} 道", Modifier.padding(top = 8.dp), color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
                }
                when (phase) {
                    0 -> item { WorkspacePanel {
                        Icon(Icons.Outlined.Print, null, tint = SmartColors.JadeDark)
                        Text("先把原卷打印出来", style = MaterialTheme.typography.titleLarge)
                        Text("${paperDateTime(paper.createdAtEpochMillis)}生成 · 作答后回到这里回传", color = SmartColors.InkSecondary)
                        when (paper.exportStatus) {
                            MistakeExportStatus.SUCCEEDED -> PrimaryActionButton("打开原卷 / 打印", { paper.exportId?.let(onOpenExports) }, enabled = !busy && paper.exportId != null, modifier = Modifier.fillMaxWidth())
                            MistakeExportStatus.RUNNING -> WorkspaceNotice("原卷正在生成，可稍后打开。")
                            else -> {
                                if (paper.exportStatus == MistakeExportStatus.FAILED) WorkspaceNotice(paper.exportFailure ?: "原卷生成失败，请重试。", tone = WorkspaceNoticeTone.ERROR)
                                PrimaryActionButton(if (paper.exportStatus == MistakeExportStatus.FAILED) "重新生成原卷" else "生成原卷", { vm.export(paper.paperId) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        InlineActionButton(if (externalBusy) "正在准备答案…" else "另存详细答案", { vm.clearMessage(); onDismissExternalMessage(); onDetailedAnswers(paper.paperId) }, enabled = !busy)
                        PaperDivider()
                        Text("已在纸上完成作答？", style = MaterialTheme.typography.titleSmall)
                        PrimaryActionButton("回传作答照片", { phase = 1 }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    } }
                    1 -> {
                        item { WorkspacePanel {
                            Icon(Icons.Outlined.AddPhotoAlternate, null, tint = SmartColors.JadeDark)
                            Text("回传纸上作答", style = MaterialTheme.typography.titleLarge)
                            Text("上传整卷照片，或逐题补充。原始照片完整保留。", color = SmartColors.InkSecondary)
                            if (paper.photos.isEmpty()) {
                                PrimaryActionButton("添加整卷作答照片", { uploadFor(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            } else {
                                OutlineActionChip("补充整卷作答照片", { uploadFor(null) }, enabled = !busy)
                                PrimaryActionButton("核对作答结果 · ${paper.photos.size} 张照片", { phase = 2 }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            }
                        } }
                        items(paper.photos, key = { it.photoId }) { photo -> PaperPhotoCard(photo, paper.photos.indexOf(photo), { uri, label -> viewer = uri to label }) }
                    }
                    else -> {
                        item { WorkspacePanel {
                            Text("逐题核对", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                paper.questions.forEachIndexed { index, q -> OutlineActionChip("${index + 1}${if (q.applied) " ✓" else ""}", { ordinal = q.ordinal }, selected = ordinal == q.ordinal, enabled = !busy) }
                            }
                            if (question != null) Text("第 ${paper.questions.indexOf(question) + 1} 题 · ${if (question.applied) "已计入" else "待核对"}", style = MaterialTheme.typography.labelLarge, color = SmartColors.InkSecondary)
                        } }
                        if (question != null) {
                            item(key = "material:${question.ordinal}") { PaperQuestionMaterial(question, details) { uri, label -> viewer = uri to label } }
                            val photos = paperReviewPhotosForQuestion(paper.photos, question.ordinal)
                            items(photos, key = { "answer:${it.photoId}" }) { photo -> PaperPhotoCard(photo, photos.indexOf(photo), { uri, label -> viewer = uri to label }) }
                            item(key = "result:${question.ordinal}") {
                                val draft = remember(draftVersion, paper, question) { vm.draft(paper.paperId, question) }
                                val correctedPhoto = photos.any { it.corrected }
                                LaunchedEffect(correctedPhoto, question.ordinal) {
                                    if (correctedPhoto && !draft.corrected && !question.applied) vm.edit(paper.paperId, question) { it.copy(corrected = true) }
                                }
                                PaperResultForm(question, draft, correctedPhoto, photos.isNotEmpty(), busy,
                                    draft.answeredAt?.let { validPaperAnsweredAt(paper.createdAtEpochMillis, it, System.currentTimeMillis()) } == true,
                                    onEdit = { vm.edit(paper.paperId, question, it) }, onTime = { pickTime(paper, question) },
                                    onNow = { vm.setAnsweredAt(paper, question, System.currentTimeMillis()) },
                                    onSave = { vm.confirm(paper.paperId, question) {
                                        if (draft.result != null) ordinal = paper.questions.firstOrNull { !it.applied && it.ordinal != question.ordinal }?.ordinal ?: question.ordinal
                                    } },
                                    onUpload = { uploadFor(question.ordinal) }, onAskAgent = { selectedPhotos = emptyList(); agentSheet = true })
                            }
                        }
                        item { if (paper.questions.any { !it.applied && it.selectedResult != null }) InlineActionButton("继续计入已确认结果", { vm.apply(paper.paperId) }, enabled = !busy) }
                    }
                }
            }
        }
    }
    if (uploadSheet) WorkspaceSheet("添加作答照片", { uploadSheet = false }, icon = Icons.Outlined.AddPhotoAlternate,
        subtitle = if (uploadOrdinal == null) "整卷照片将用于这份纸卷的逐题核对" else "照片只关联当前题目", actions = {
            PrimaryActionButton("拍照", ::openCamera, enabled = !busy, modifier = Modifier.fillMaxWidth())
            InlineActionButton("从相册选择", { freezeIntake(); gallery.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }) {
        WorkspaceChoiceRow("尚未订正", "照片保留学生最初的作答", !corrected, { corrected = false })
        WorkspaceChoiceRow("已经订正", "核对时还需说明订正前的结果", corrected, { corrected = true })
    }
    if (agentSheet && current != null) {
        val photos = paperReviewPhotosForQuestion(current.photos, ordinal)
        WorkspaceSheet("选择要检查的作答照片", { agentSheet = false }, icon = Icons.Outlined.AutoAwesome,
            subtitle = "已选 ${selectedPhotos.size} / ${photos.size} 张 · 一次最多 $MAX_TUTOR_MESSAGE_IMAGES 张", actions = {
                PrimaryActionButton("带照片前往智能体", {
                    vm.clearMessage(); onDismissExternalMessage(); onAskAgent(current.paperId, ordinal, selectedPhotos.toSet()); agentSheet = false
                }, enabled = !busy && selectedPhotos.size in 1..MAX_TUTOR_MESSAGE_IMAGES && selectedPhotos.all { id -> photos.any { it.photoId == id } }, modifier = Modifier.fillMaxWidth())
            }) {
            WorkspaceNotice("只发送勾选的照片。超过上限请分批检查；智能体不会代替你确认复习结果。")
            photos.forEachIndexed { index, photo ->
                PaperCheckRow("照片 ${index + 1}${if (photo.corrected) " · 已订正" else " · 未订正"}", photo.photoId in selectedPhotos,
                    photo.photoId in selectedPhotos || selectedPhotos.size < MAX_TUTOR_MESSAGE_IMAGES) { checked ->
                    selectedPhotos = if (checked) selectedPhotos + photo.photoId else selectedPhotos - photo.photoId
                }
                PaperImage(photo.localUri, "作答照片 ${index + 1}") { uri, label -> viewer = uri to label }
            }
        }
    }
    viewer?.let { (uri, label) -> LocalImageViewer(uri, label, { viewer = null }) }
    if (discardDialog) AlertDialog(onDismissRequest = { discardDialog = false }, title = { Text("离开纸卷？") },
        text = { Text("尚未保存的核对结果将被放弃，已回传的照片和已保存结果会保留。") },
        confirmButton = { TextButton({ current?.let { vm.discard(it.paperId) }; discardDialog = false; selected = null; vm.clearMessage() }) { Text("放弃未保存结果") } },
        dismissButton = { TextButton({ discardDialog = false }) { Text("继续核对") } }, dangerConfirm = true)
}
