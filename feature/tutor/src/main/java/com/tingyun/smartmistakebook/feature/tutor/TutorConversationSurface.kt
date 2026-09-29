package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.model.ModelLiveKind
import com.tingyun.smartmistakebook.core.model.ModelLiveText
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorToolTraceDisplay
import com.tingyun.smartmistakebook.core.model.decodeTutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.tutorToolTraceDisplay
import com.tingyun.smartmistakebook.core.ui.ErrorWarm
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.Paper
import com.tingyun.smartmistakebook.core.ui.SmartDimens
import com.tingyun.smartmistakebook.core.ui.ThinkingCollapsibleCard
import com.tingyun.smartmistakebook.core.ui.TutorStreamingReply
import com.tingyun.smartmistakebook.core.ui.TutorToolTraceLine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 在途一轮的实时文本：思考链 / 回答正文 / 工具进度，三条都来自 [ModelTaskRepository.observeLiveText]
 * 那条逐 token 通道。
 *
 * 消灭的失败：大厅与会话曾经各有一套"在途状态"——大厅接的是实时通道，会话读的是快照里那个
 * 旧的状态串（`streamingReplyBody`），于是同一屏里会话侧的正文最多只能一帧一帧地跳、思考链
 * 与工具调用根本不出现。现在两边共用这一个状态与同一条订阅，学生看到的是"长出来"的回答。
 */
internal data class TutorLiveTurn(
    val thinking: String? = null,
    val answer: String? = null,
    val toolNote: String? = null,
    /**
     * 这一轮的工具痕迹（B1）：来自 [ModelTaskRepository.observeToolTrace]，已经编码截断好的
     * `TutorTurnToolTrace` JSON。它比 [toolNote] 多两样东西——**可展开的明细**（每条查到了几条、
     * 被拒的是为什么）与**稳定的形状**（重开会话后从消息行读回来的是同一份）。
     */
    val toolTraceJson: String? = null,
) {
    /**
     * 思考卡正文：**只有思考链**。工具行不再混进来（B1）：思考是模型在推理，查阅是它对外做的事，
     * 合成一段之后学生分不出"它在想"和"它去查了"（而且工具行要能点开看详情，混进卡片就点不到了）。
     */
    val thinkingText: String?
        get() = thinking?.takeIf(String::isNotBlank)

    /** 回答是否已经开始写：一开始写，思考卡就从"正在思考…"收起。 */
    val hasAnswer: Boolean
        get() = !answer.isNullOrBlank()

    /**
     * 实时那一轮的工具小字：有痕迹就用痕迹（含明细），痕迹还没到时用流里的那句文本
     * （"正在查阅错题本…"）——两种都走同一个渲染模型，界面只有一条路径。
     */
    val toolTraceDisplay: TutorToolTraceDisplay?
        get() = decodeTutorTurnToolTrace(toolTraceJson)
            ?.let(::tutorToolTraceDisplay)
            ?: toolNote
                ?.takeIf(String::isNotBlank)
                ?.let { text -> TutorToolTraceDisplay(headline = text, rows = emptyList()) }

    companion object {
        val EMPTY = TutorLiveTurn()
    }
}

/**
 * 把一条实时文本折进在途状态：同一个请求上后到的通道覆盖前一条，没被覆盖的那条保留
 * （回答开始写之后，思考链仍然留在卡里，供学生回看）。
 *
 * 正文通道过 [answersWithModelEnvelope] 这一道（A6）：信封不是正文。发生在界面这一层的
 * 那半件事是**兜底**——网关已经按协议路由分流（Route B 的 content 根本不进正文通道），
 * 但 Route A 的终答轮同样把信封放在 `content` 里（prompt 只要求精确 JSON），所以"永不渲染
 * 信封 JSON"这条要求必须在这一层也成立。
 */
internal fun TutorLiveTurn.withLive(live: ModelLiveText?): TutorLiveTurn = when (live?.kind) {
    ModelLiveKind.THINKING -> copy(thinking = live.text)
    ModelLiveKind.ANSWER -> copy(answer = live.text.takeIf(String::answersWithModelEnvelope))
    ModelLiveKind.TOOL -> copy(toolNote = live.text)
    null -> this
}

/**
 * 这段文本是不是**学生能看的正文**（而不是模型信封）。
 *
 * 判据只有一条却足够稳：去围栏后以 `{` 开头，且第一个键名与其后的名字**是信封固定键的前缀**
 * ——流式期间键名本身就是逐字长出来的（`{"intentDec`），所以必须按前缀判。真实正文里以 `{"`
 * 开头的句子只在它是 JSON 时才出现。
 */
internal fun String.answersWithModelEnvelope(): Boolean = !looksLikeModelEnvelopeJson(this)

/** [answersWithModelEnvelope] 的否定式，名字直白：这段文本就是信封。 */
internal fun looksLikeModelEnvelopeJson(text: String): Boolean {
    val trimmed = text.trimStart()
        .removePrefix("```json")
        .removePrefix("```")
        .trimStart()
    if (!trimmed.startsWith("{")) return false
    val key = trimmed.drop(1).trimStart()
    if (!key.startsWith("\"")) return false
    val name = key.drop(1).takeWhile { it != '"' }
    if (name.isEmpty()) return true
    return MODEL_ENVELOPE_KEYS.any { known -> known.startsWith(name) }
}

/**
 * 模型信封的固定键（各任务信封的首键之一）。判据取"是其中某个键的前缀"，所以流式期间半个键名
 * 也算信封——不会因为还没长全就先当正文渲染出来。
 */
private val MODEL_ENVELOPE_KEYS = setOf(
    "intentDecision",
    "messageMarkdown",
    "thinkingMarkdown",
    "toolRequests",
    "plan",
    "assessment",
    "questionDocument",
    "organization",
    "quiz",
    "debrief",
)

/**
 * 唯一的一条实时流：网关把逐 token 文本发到这条通道上（不落库、不计事件上限），这里把它折成
 * [TutorLiveTurn] 交给界面。大厅与会话都从这里读，不再各自实现一遍。
 */
internal suspend fun ModelTaskRepository.observeTutorLiveTurn(
    requestId: String,
    onTurn: (TutorLiveTurn) -> Unit,
) {
    var turn = TutorLiveTurn.EMPTY
    // 两条通道合流（B1）：逐 token 文本（思考/正文）与这一轮的工具痕迹。痕迹比文本晚到一步
    // （文本在派发前就说"正在查阅…"，痕迹要等工具跑完），所以两条都要订，缺一条就会
    // "生成中看得到、明细点不开"或者反过来。
    combine(
        observeLiveText(requestId),
        observeToolTrace(requestId),
    ) { live, toolTrace -> turn.withLive(live).copy(toolTraceJson = toolTrace) }
        .collect { next ->
            if (next != turn) {
                turn = next
                onTurn(next)
            }
        }
}

/**
 * 本会话当前活跃的一轮（A3）：**会话 id + 在途任务的请求 id** 一起作订阅键。
 *
 * 消灭的失败：实时订阅此前只挂"回应轮"的结果，讲题侧的**计划/讲解阶段**读的一直是快照里那个
 * 旧状态串——同一段等待，有的阶段有逐 token 的流、有的阶段只有一句"正在准备"（学生原话：
 * "别分什么 plan，这个流是在任何地方它都需要有"）。现在按**本会话当前在途的那一条任务**订阅，
 * 计划 / 回应 / 智能体栏三种任务一视同仁。
 */
internal data class TutorActiveLiveTurn(
    val conversationKey: String,
    val requestId: String,
) {
    /** 订阅键：会话变了、或者换了在途任务，就是另一次订阅。 */
    val subscriptionKey: String get() = "$conversationKey#$requestId"
}

/**
 * 本会话当前在途的那一轮：取更新时间最新的一条非终态任务。
 *
 * 入参是**同一个会话下的全部任务**（不按任务类型分桶）——分桶正是"只有回应轮有流"的成因。
 */
internal fun activeTutorLiveTurn(
    conversationKey: String,
    tasks: List<ModelTaskSnapshot>,
): TutorActiveLiveTurn? = tasks
    .filter(ModelTaskSnapshot::isTutorLiveStatus)
    .maxByOrNull(ModelTaskSnapshot::updatedAtEpochMillis)
    ?.let { task -> TutorActiveLiveTurn(conversationKey, task.request.requestId) }

/**
 * 订阅"本会话当前在途那一轮"的实时文本：会话或在途任务一变就换订阅，不再在途时清空。
 *
 * 订阅放在组合里而不是某一条派发路径的协程里：会话有三条派发路径（新发送 / 失败重试 / 恢复
 * 未完成任务），只接其中一条就会出现"哪条路径忘了接"——那正是大厅此前自己踩过的坑
 * （正常发送路径从不读实时状态，只有"继续回复"看得到）。
 */
@Composable
internal fun rememberTutorLiveTurn(
    modelTasks: ModelTaskRepository,
    active: TutorActiveLiveTurn?,
): TutorLiveTurn {
    var turn by remember(active?.subscriptionKey) { mutableStateOf(TutorLiveTurn.EMPTY) }
    LaunchedEffect(modelTasks, active?.subscriptionKey) {
        val requestId = active?.requestId ?: return@LaunchedEffect
        modelTasks.observeTutorLiveTurn(requestId) { live -> turn = live }
    }
    return turn
}

/** 生成中的任务状态：这些状态下任务快照自带的状态文本值得显示（还没有逐 token 文本时）。 */
internal val TUTOR_LIVE_TASK_STATUSES = setOf(
    ModelTaskStatus.WAITING_FOR_MODEL,
    ModelTaskStatus.QUEUED,
    ModelTaskStatus.RUNNING,
    ModelTaskStatus.STREAMING,
)

/**
 * 生成中的快照 → 思考卡的兜底状态行。
 *
 * 这是原 `ModelTaskSnapshot.streamingReplyBody()` 的位置。它此前只认 STREAMING，且在大厅与会话
 * 两处被当成两个不同的东西：大厅当"模型正在做什么"的状态行，会话当**回答正文**。合并成一条
 * 实时流之后，正文一律来自 [ModelTaskRepository.observeLiveText] 的 ANSWER 通道，快照只提供这
 * 一行兜底文本——所以状态集取两处的并集（大厅原本就覆盖 WAITING/QUEUED/RUNNING）。
 */
internal fun ModelTaskSnapshot.tutorLiveStatusText(): String? {
    if (status !in TUTOR_LIVE_TASK_STATUSES) return null
    // 状态行也是学生看得见的文本：流式期间它可能是被截断的正文前缀（= 信封），同样不许渲染
    // （A6：UI 侧永不渲染信封 JSON）。
    return when (request.input) {
        is TutorRespondInput, is TutorLobbyInput -> userMessage
            .takeIf { it.isNotBlank() && it.answersWithModelEnvelope() }
        // A3：计划阶段也要有话说——它此前读同一份快照却什么都拿不到，于是计划阶段在屏幕上
        // 只有一句没有信息的占位。任务自己的状态行优先，没有就给这句。
        is TutorPlanInput -> userMessage
            .takeIf { it.isNotBlank() && it.answersWithModelEnvelope() }
            ?: "正在准备这道题的讲解…"
        else -> null
    }
}

/**
 * 在途状态：思考卡（思考链 + 工具进度，自动展开、逐 token 增长；回答一开始写就收起）+
 * 逐 token 的回答正文（[TutorStreamingReply]）+ 什么都还没到时的一句占位。
 *
 * 大厅与会话共用这一个实现；两处各自的 testTag 由调用方传入，被测试按住的标签继续存在。
 */
@Composable
internal fun TutorLiveTurnBlock(
    turn: TutorLiveTurn,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    placeholder: String = TUTOR_LIVE_PLACEHOLDER,
    answerTestTag: String = "tutor_streaming_reply",
    /**
     * 正在生成时的「停止」（A2）：非 null 就在这一轮底下露出来。停止是**动作**不是状态，
     * 所以它长在在途块里（此时还没有那条助手消息行可挂动作条）。
     */
    onStop: (() -> Unit)? = null,
    stopTestTag: String = "tutor_live_stop",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val thinkingText = turn.thinkingText ?: statusText
        val toolTraceDisplay = remember(turn.toolTraceJson, turn.toolNote) { turn.toolTraceDisplay }
        ThinkingCollapsibleCard(
            thinkingMarkdown = thinkingText,
            thinking = !turn.hasAnswer,
        )
        // 工具痕迹**单独一行**（B1）：不混进思考卡，点开可看明细。它比回答先出现、随流更新。
        TutorToolTraceLine(
            display = toolTraceDisplay,
            modifier = Modifier.padding(top = 2.dp),
            testTag = "tutor_live_tool_trace",
        )
        turn.answer?.takeIf(String::isNotBlank)?.let { answer ->
            TutorStreamingReply(
                markdown = answer,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .testTag(answerTestTag),
            )
        }
        if (thinkingText == null && toolTraceDisplay == null && !turn.hasAnswer) {
            TutorPrompt(
                text = placeholder,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        onStop?.let { stop ->
            OutlineActionChip(
                text = TUTOR_SURFACE_STOP_LABEL,
                onClick = stop,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(stopTestTag),
            )
        }
    }
}

internal const val TUTOR_LIVE_PLACEHOLDER = "正在回复…"

/**
 * 失败卡（唯一实现）：一句正文 + 可选的原因 + 至多两个出口（检查模型设置 / 重新发送 / 重试）。
 *
 * 出口的文案与 testTag 由调用方给：大厅有「检查模型设置 / 重新发送」，会话有「检查模型设置 /
 * 重试」，这些标签被测试按住，不能因为合并而改名。
 */
@Composable
internal fun TutorTurnFailureCard(
    detail: String,
    modifier: Modifier = Modifier,
    reason: String? = null,
    reasonTestTag: String? = null,
    primaryActionLabel: String? = null,
    primaryActionTestTag: String = "tutor_failure_action",
    onPrimaryAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    secondaryActionTestTag: String = "tutor_failure_secondary_action",
    onSecondaryAction: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ErrorWarm.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ErrorWarm.copy(alpha = 0.36f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = detail,
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            reason?.let { text ->
                Text(
                    text = text,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .then(
                            if (reasonTestTag != null) {
                                Modifier.testTag(reasonTestTag)
                            } else {
                                Modifier
                            },
                        ),
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            primaryActionLabel?.let { label ->
                OutlineActionChip(
                    text = label,
                    onClick = onPrimaryAction,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(primaryActionTestTag),
                )
            }
            secondaryActionLabel?.let { label ->
                OutlineActionChip(
                    text = label,
                    onClick = onSecondaryAction,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(secondaryActionTestTag),
                )
            }
        }
    }
}

/** 贴尾滚动最多试几次：估算落点修正一次即可到位，上限只为不在这里死循环。 */
private const val TUTOR_TAIL_FOLLOW_ATTEMPTS = 3

/**
 * 这个列表是不是**真的**在末尾（D6）。
 *
 * 判据是"**末条完整落在视口内**"（可见项的偏移 + 尺寸 <= 视口底），不是"末条的序号出现在
 * 可见项里"。后者在末条只露出一点时也成立，于是 [followTutorTail] 会在落点还差几百像素时
 * 就收手——刚加载完的长会话里末条的高度只能靠估算，第一滚必然偏短，而这次判真让"再滚一次
 * 才是真正的末尾"那一步永远不发生。真机上表现为"最新那条只露了个头"，用例里则是
 * `assertIsDisplayed` 判它整段被裁掉（`boundsInRoot` 为零）。
 *
 * 内容比视口短时恒为真；末条本身比视口还高（一整段很长的讲解）时永远不为真——那时滚到末条
 * 即到位，循环按次数上限退出，与收紧之前的行为一致。
 */
private fun LazyListState.isAtTail(): Boolean {
    val layout = layoutInfo
    if (layout.totalItemsCount == 0) return true
    val last = layout.visibleItemsInfo.lastOrNull() ?: return false
    return last.index == layout.totalItemsCount - 1 &&
        last.offset + last.size <= layout.viewportEndOffset
}

/**
 * 滚到列表真正的末尾。
 *
 * 消灭的失败（D6 残留）：`scrollToItem(末条)` 对**还没测量过**的末条用的是估算高度，落点会停在
 * 真正末尾之前——最新那条回复半截（甚至整条）落在屏幕外，学生以为没有新回复。真机诊断：
 * 12 条会话加载完后，滚动量停在 3306/3406，最后一条根本没被组合。第一次滚完末条已经被组合、
 * 有了真实高度，再滚一次才是真正的末尾；每轮都拿**刚布局完**的可见项判断是否已到底。
 */
private suspend fun followTutorTail(listState: LazyListState) {
    repeat(TUTOR_TAIL_FOLLOW_ATTEMPTS) {
        val itemCount = snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it > 0 }
        listState.scrollToItem(itemCount - 1)
        withFrameNanos { }
        if (listState.isAtTail()) return
    }
}

/**
 * 讲题页面的唯一屏幕组件：标题栏 + 列表（自动跟随最新）+ 在途状态 + 输入区。
 *
 * 消灭的失败：大厅此前是一个自己拼的 `Column`（`RootPageLazyColumn` + 分隔线 + 另一个输入组件），
 * 会话是这一套 `TutorConversationFrame`——同一个页面的两种状态长得不一样，而且大厅没有"跟随最新"
 * 这条逻辑：回答到达后画面停在旧位置（真机截图确认）。两处现在都是这一个组件。
 *
 * @param liveTurn 当前在途的一轮；null 表示没有在途（不渲染在途区）。
 * @param liveStatusText 还没有逐 token 文本时的兜底状态行（来自任务快照）。
 * @param liveAnswerTestTag 逐 token 回答正文的标签：大厅 / 会话各有一个被测试按住的标签。
 */
@Composable
internal fun TutorConversationFrame(
    header: @Composable () -> Unit,
    autoScrollVersion: Any?,
    forceFollowToken: Any? = null,
    blockAutoFollowToken: Any? = null,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    listViewportModifier: Modifier = Modifier,
    liveTurn: TutorLiveTurn? = null,
    liveStatusText: String? = null,
    livePlaceholder: String = TUTOR_LIVE_PLACEHOLDER,
    liveAnswerTestTag: String = "tutor_streaming_reply",
    /** 在途一轮的「停止」（A2）：null = 这个入口这一轮不提供停止。 */
    liveTurnStopAction: (() -> Unit)? = null,
    liveTurnStopTestTag: String = "tutor_live_stop",
    composer: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    var initialTailPositioned by remember(listState) { mutableStateOf(false) }
    var followsTail by remember(listState) { mutableStateOf(true) }
    var handledForceToken by remember(listState) { mutableStateOf<Any?>(null) }
    var handledBlockToken by remember(listState) { mutableStateOf<Any?>(null) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
            val atEnd = layout.totalItemsCount == 0 || lastVisible == layout.totalItemsCount - 1
            listState.isScrollInProgress to atEnd
        }.collect { (scrolling, atEnd) ->
            if (scrolling) {
                followsTail = atEnd
            } else if (atEnd) {
                followsTail = true
            }
        }
    }
    LaunchedEffect(autoScrollVersion, forceFollowToken, blockAutoFollowToken, listState) {
        val forceFollow = forceFollowToken != null && forceFollowToken != handledForceToken
        val blockAutoFollow = initialTailPositioned &&
            blockAutoFollowToken != null &&
            blockAutoFollowToken != handledBlockToken
        val shouldFollow = !initialTailPositioned ||
            !blockAutoFollow && (followsTail || forceFollow)
        withFrameNanos { }
        if (shouldFollow) {
            followTutorTail(listState)
            followsTail = true
        } else if (blockAutoFollow) {
            followsTail = false
        }
        initialTailPositioned = true
        if (forceFollowToken != null) handledForceToken = forceFollowToken
        if (blockAutoFollowToken != null) handledBlockToken = blockAutoFollowToken
    }

    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Paper),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = SmartDimens.MaximumContentWidth)
                .fillMaxSize()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = SmartDimens.ContentHorizontalPadding,
                    top = 8.dp,
                    end = SmartDimens.ContentHorizontalPadding,
                ),
            ) {
                header()
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("tutor_conversation_list")
                    .then(listViewportModifier),
                state = listState,
                contentPadding = PaddingValues(
                    start = SmartDimens.ContentHorizontalPadding,
                    top = 12.dp,
                    end = SmartDimens.ContentHorizontalPadding,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
                if (liveTurn != null) {
                    item("tutor_live_turn") {
                        TutorLiveTurnBlock(
                            turn = liveTurn,
                            statusText = liveStatusText,
                            placeholder = livePlaceholder,
                            answerTestTag = liveAnswerTestTag,
                            onStop = liveTurnStopAction,
                            stopTestTag = liveTurnStopTestTag,
                        )
                    }
                }
            }
            composer?.let {
                Surface(color = Paper, shadowElevation = 4.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = SmartDimens.ContentHorizontalPadding,
                                vertical = 8.dp,
                            ),
                    ) {
                        it()
                    }
                }
            }
        }
        if (!followsTail && initialTailPositioned) {
            ScrollToBottomButton(
                onClick = {
                    followsTail = true
                    forceFollowToken?.let { handledForceToken = it }
                    // 同一个贴尾助手：点「回到最新」也要真的到最新，而不是停在估算落点上。
                    coroutineScope.launch { followTutorTail(listState) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp)
                    .testTag("tutor_scroll_to_bottom"),
            )
        }
    }
}

@Composable
private fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = JadeActive,
        contentColor = Paper,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = "回到最新",
        )
    }
}

/**
 * 讲题页面的唯一输入区。大厅与会话共用：同一个输入框、同一个发送按钮、同一套附图入口。
 *
 * 消灭的失败：两处此前是两个组件——大厅那个在纯图消息（正文为空、只带了图）时发送键是灰的
 * （`value.isNotBlank()` 才可点），而两边的发送入口本来就都允许纯图发送；同一个页面里"能不能
 * 发出去"取决于学生从哪个状态进来。
 */
@Composable
internal fun TutorChatComposer(
    value: String,
    enabled: Boolean,
    sending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "问这道题，或说出你卡住的步骤",
    /** 非 null 时显示左侧“+”按钮并调用它打开添加菜单（拍照 / 相册 / 从错题库选择）。 */
    onOpenAttachMenu: (() -> Unit)? = null,
    /** 输入框上方的附件预览行（微信式，可选）。 */
    attachmentPreview: (@Composable () -> Unit)? = null,
    /** 已选好待发送的附件数：纯图消息也能发出（正文由调用方补一句兜底文本）。 */
    attachmentCount: Int = 0,
    /**
     * 此刻发不出去（A5）：原因写在输入区里，发送键同时变灰——输入框本身照常可用，
     * 学生可以先打字，但不会出现"按下去什么也不发生"的假按钮。
     */
    sendBlocked: Boolean = false,
) {
    val canSend = !sendBlocked && enabled && !sending && (value.isNotBlank() || attachmentCount > 0)
    Column(modifier = modifier.fillMaxWidth()) {
        attachmentPreview?.invoke()
        OutlinedTextField(
            value = value,
            onValueChange = { changed ->
                onValueChange(changed.take(TutorRespondInput.MAX_STUDENT_MESSAGE_CHARS))
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tutor_chat_composer"),
            enabled = enabled,
            placeholder = { Text(placeholder) },
            minLines = 1,
            maxLines = 4,
            leadingIcon = onOpenAttachMenu?.let { openAttachMenu ->
                {
                    IconButton(
                        onClick = openAttachMenu,
                        enabled = enabled,
                        modifier = Modifier.testTag("tutor_chat_attach"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "添加图片",
                            tint = JadeActive,
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { if (canSend) onSend() },
            ),
            trailingIcon = {
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.testTag("tutor_chat_send"),
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = JadeActive,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "发送这条消息",
                            tint = JadeActive,
                        )
                    }
                }
            },
            supportingText = if (value.length >= 1_000) {
                { Text("${value.length}/${TutorRespondInput.MAX_STUDENT_MESSAGE_CHARS}") }
            } else {
                null
            },
            shape = RoundedCornerShape(14.dp),
        )
    }
}
