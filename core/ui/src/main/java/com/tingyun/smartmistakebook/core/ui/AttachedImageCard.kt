package com.tingyun.smartmistakebook.core.ui

import com.tingyun.smartmistakebook.core.ui.ClaudeDisclosure as AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import com.tingyun.smartmistakebook.core.model.AttachedImageResolution
import com.tingyun.smartmistakebook.core.model.AttachedImageUnavailableReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 附加图的默认折叠标题（纯函数，便于单测）：按 kind 给教育语境图题。 */
internal fun attachedImageCollapsedTitle(image: AttachedImage): String = when (image.kind) {
    AttachedImageKind.REDRAW_PROBLEM -> "重绘图 · ${image.description.take(18)}"
    AttachedImageKind.GENERATE_PROCESS -> "过程图 · ${image.description.take(18)}"
}

/**
 * A4（4B）：配图卡的三态。
 *
 * - [Generating]：解析/生成中（转圈 + 文案）；旋转/重建后重新解析时也会短暂出现；
 * - [Failed]：这次没有本地可渲染的图——带**可读原因**；只有可安全重试的原因才显示
 *   “重新生成”（显式用户动作，走共享协调器，不在渲染时自动发请求）；
 * - [Ready]：本地 URI 到手，展开后仍需本地解码；解码失败在卡内呈现，不改生成事实。
 *
 * KD-58：此前只有一句静态“图未能生成”，缓存未命中与真正失败长得一样，也没有原因。
 */
sealed interface AttachedImageRenderState {
    data object Generating : AttachedImageRenderState

    data class Ready(val localUri: String) : AttachedImageRenderState

    data class Failed(
        val reason: AttachedImageUnavailableReason = AttachedImageUnavailableReason.NOT_GENERATED,
        val detail: String? = null,
    ) : AttachedImageRenderState
}

/** 旧的 `String?` 结果 → 渲染状态（null = 本地没有可用副本，最常见是缓存未命中）。 */
internal fun attachedImageRenderState(localUri: String?): AttachedImageRenderState =
    if (localUri != null) {
        AttachedImageRenderState.Ready(localUri)
    } else {
        AttachedImageRenderState.Failed(AttachedImageUnavailableReason.NOT_GENERATED)
    }

/** 显式解析结果 → 渲染状态（保留原因与有界诊断）。 */
internal fun attachedImageRenderState(
    resolution: AttachedImageResolution,
): AttachedImageRenderState = when (resolution) {
    is AttachedImageResolution.Ready -> AttachedImageRenderState.Ready(resolution.localUri)
    is AttachedImageResolution.Unavailable -> AttachedImageRenderState.Failed(
        reason = resolution.reason,
        detail = resolution.detail,
    )
}

/** “重新生成”按钮的唯一文案（测试与 UI 共用）。 */
internal const val ATTACHED_IMAGE_REGENERATE_LABEL: String = "重新生成"

/**
 * 折叠行上的状态提示：暂不可用在折叠态就能看见，不必展开才发现少了一张图。
 */
internal fun attachedImageStatusHint(state: AttachedImageRenderState): String? = when (state) {
    AttachedImageRenderState.Generating -> "生成中"
    is AttachedImageRenderState.Ready -> null
    is AttachedImageRenderState.Failed -> "暂不可用"
}

/** 这张图当前是否应提供显式“重新生成”。损坏/缺源不盲目重生成。 */
internal fun attachedImageCanRegenerate(state: AttachedImageRenderState.Failed): Boolean =
    when (state.reason) {
        AttachedImageUnavailableReason.NOT_GENERATED,
        AttachedImageUnavailableReason.REGENERATION_UNAVAILABLE,
        AttachedImageUnavailableReason.REGENERATION_REJECTED,
        AttachedImageUnavailableReason.REGENERATION_FAILED,
        -> true

        AttachedImageUnavailableReason.DAMAGED,
        AttachedImageUnavailableReason.LOCAL_READ_FAILED,
        AttachedImageUnavailableReason.SOURCE_UNAVAILABLE,
        -> false
    }

/** 失败态的主文案（中文、说清“没有自动发请求”）。 */
internal fun attachedImageFailureMessage(state: AttachedImageRenderState.Failed): String =
    when (state.reason) {
        AttachedImageUnavailableReason.NOT_GENERATED ->
            "这张配图在本机没有已生成的副本；打开历史消息不会自动联网生成。"

        AttachedImageUnavailableReason.DAMAGED ->
            "这张配图在本机的副本已损坏，未自动重新生成。"

        AttachedImageUnavailableReason.LOCAL_READ_FAILED ->
            "本机读取这张配图失败（没有发起联网生成）。"

        AttachedImageUnavailableReason.SOURCE_UNAVAILABLE ->
            "缺少这张图所需的题面原图，无法重绘。"

        AttachedImageUnavailableReason.REGENERATION_UNAVAILABLE ->
            "本机没有可用的生图通道（未配置图像模型）。"

        AttachedImageUnavailableReason.REGENERATION_REJECTED ->
            "这次重新生成没有通过派发检查。"

        AttachedImageUnavailableReason.REGENERATION_FAILED ->
            "重新生成没有成功。"
    }

/**
 * 讲题回复附加图锚点：默认折叠为一行缩略摘要（图标 + 图题），点击展开为 [BoundedLocalImage]。
 * 图以 [imageLocalUri]（本地 file/content）渲染，绝不联网。
 *
 * KD-58：[onRegenerate] 是**显式用户动作**——只有用户点“重新生成”才会进入共享 coordinator；
 * 打开消息/展开/旋转不会触发任何生图请求。
 */
@Composable
fun AttachedImageCard(
    image: AttachedImage,
    state: AttachedImageRenderState,
    modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable(image.imageId) { mutableStateOf(false) }
    var viewing by rememberSaveable(image.imageId, state) { mutableStateOf(false) }
    var localLoadState by remember(image.imageId, state) { mutableStateOf(LocalImageLoadState.LOADING) }
    // A URI resolving successfully does not guarantee that its bytes can still be decoded.
    val displayState = if (state is AttachedImageRenderState.Ready &&
        localLoadState == LocalImageLoadState.UNAVAILABLE
    ) {
        AttachedImageRenderState.Failed(AttachedImageUnavailableReason.LOCAL_READ_FAILED)
    } else {
        state
    }
    val title = attachedImageCollapsedTitle(image)
    val accessibilityText = image.accessibilityText.ifBlank { image.description }
    val statusHint = attachedImageStatusHint(displayState)
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SubtleSurface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    if (!expanded && state is AttachedImageRenderState.Ready &&
                        localLoadState == LocalImageLoadState.UNAVAILABLE
                    ) {
                        // Explicitly reopening retries local bytes, never image generation.
                        localLoadState = LocalImageLoadState.LOADING
                    }
                    expanded = !expanded
                }
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = accessibilityText,
                tint = InkSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = buildString {
                    append(title)
                    statusHint?.let { append(" · ").append(it) }
                },
                color = InkSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起图" else "展开图",
                tint = InkSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
        ) {
            when (displayState) {
                is AttachedImageRenderState.Ready -> Column {
                    BoundedLocalImage(
                    imageUri = displayState.localUri,
                    contentDescription = accessibilityText,
                    expanded = expanded,
                    modifier = Modifier.fillMaxWidth(),
                    onLoadStateChange = { localLoadState = it },
                    )
                    InlineActionButton("查看大图", { viewing = true })
                }

                AttachedImageRenderState.Generating -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "图正在生成，稍候…",
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                is AttachedImageRenderState.Failed -> Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Text(
                        text = attachedImageFailureMessage(displayState),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (accessibilityText.isNotBlank()) {
                        Text(
                            text = accessibilityText,
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    displayState.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                        Text(
                            text = detail,
                            color = InkSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (attachedImageCanRegenerate(displayState)) {
                        onRegenerate?.let { regenerate ->
                            TextButton(
                                onClick = regenerate,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 0.dp,
                                    vertical = 2.dp,
                                ),
                                modifier = Modifier.testTag(
                                    "attached_image_regenerate_${image.imageId}",
                                ),
                            ) {
                                Text(
                                    text = ATTACHED_IMAGE_REGENERATE_LABEL,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (viewing && state is AttachedImageRenderState.Ready) {
        LocalImageViewer(state.localUri, accessibilityText, { viewing = false })
    }
}

/**
 * A2（4B）：**已持久化**的生成图卡（工具链 `GENERATE_FIGURE` 的结果）。
 *
 * 与 [AttachedImageCard] 的区别：它没有模型写的图题——图的身份就是规范资产 id（消息行的持久
 * 引用给的），学生看到的是"配图"。解析从资产行 + vault 读回（本地，绝不出网）：旋转/进程重建
 * 后同一张图仍在，不会再触发一次付费生成。
 */
@Composable
fun GeneratedFigureCard(
    assetId: String,
    state: AttachedImageRenderState,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(assetId) { mutableStateOf(false) }
    var viewing by rememberSaveable(assetId, state) { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SubtleSurface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = "本轮生成的配图",
                tint = InkSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (expanded) "已展开" else "配图",
                color = InkSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起图" else "展开图",
                tint = InkSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
        ) {
            when (state) {
                is AttachedImageRenderState.Ready -> Column {
                    BoundedLocalImage(
                    imageUri = state.localUri,
                    contentDescription = "本轮生成的配图",
                    expanded = expanded,
                    modifier = Modifier.fillMaxWidth(),
                    )
                    InlineActionButton("查看大图", { viewing = true })
                }

                AttachedImageRenderState.Generating -> Text(
                    text = "配图正在读取…",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                is AttachedImageRenderState.Failed -> Text(
                    text = "配图暂不可用。",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
    if (viewing && state is AttachedImageRenderState.Ready) {
        LocalImageViewer(state.localUri, "本轮生成的配图", { viewing = false })
    }
}

/**
 * A2（4B）：**持久引用**的解析缓存——同一个资产 id 在组合之间只解析一次。
 *
 * 消灭的失败：旋转/重建后重新组合时，同一批配图 id 会被再解析一遍；解析在生产里是"读库 +
 * 逐位核对文件"的本地成本，重复解析虽不出网，但"同一 id 只解析一次"是"从持久引用重建"
 * 这条链可被单测钉住的最小事实。缓存按 id 存**结果**（含失败）：失败是这张资产当前的
 * 真实状态，重试/重生成不是界面能替学生决定的事（工具链的失败出口在工具卡）。
 */
internal class GeneratedFigureAssetCache(
    private val resolveAsset: suspend (String) -> String?,
) {
    private val states = mutableMapOf<String, AttachedImageRenderState>()

    /** 已解析过的状态（组合的初始态用它，避免先闪一下"生成中"）。 */
    fun cached(assetId: String): AttachedImageRenderState? = states[assetId]

    /** 解析一次并记住；同一 id 的后续调用直接返回缓存。 */
    suspend fun stateOf(assetId: String): AttachedImageRenderState {
        states[assetId]?.let { return it }
        val state = attachedImageRenderState(
            runCatching { resolveAsset(assetId) }.getOrNull(),
        )
        states[assetId] = state
        return state
    }
}

/**
 * Renders a reply's [images] as a stack of foldable figure cards, resolving each
 * intent to its local URI asynchronously. The text reply renders immediately and
 * the figures fill in (or show a readable "暂不可用" state) without blocking.
 *
 * WG-05 / KD-58：
 * - [resolve] 是 **cache-only**：打开/展开/旋转只读本地资产；
 * - [regenerate] 是用户显式动作的出口（绑定原消息的父操作，经共享协调器）；
 *   为 null 时不显示按钮。
 */
@Composable
fun AttachedImagesSection(
    images: List<AttachedImage>,
    resolve: suspend (AttachedImage) -> AttachedImageResolution,
    modifier: Modifier = Modifier,
    regenerate: (suspend (AttachedImage) -> AttachedImageResolution)? = null,
) {
    val scope = rememberCoroutineScope()
    var states by remember(images) {
        mutableStateOf<Map<String, AttachedImageRenderState>>(
            images.associate { image -> image.imageId to AttachedImageRenderState.Generating },
        )
    }
    suspend fun resolveInto(image: AttachedImage) {
        val resolution = try {
            resolve(image)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AttachedImageResolution.Unavailable(
                AttachedImageUnavailableReason.LOCAL_READ_FAILED,
                failure.message?.take(AttachedImageResolution.MAX_DETAIL_CHARS),
            )
        }
        states = states + (image.imageId to attachedImageRenderState(resolution))
    }
    LaunchedEffect(images) {
        images.forEach { image ->
            states = states + (image.imageId to AttachedImageRenderState.Generating)
            resolveInto(image)
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        images.forEach { image ->
            AttachedImageCard(
                image = image,
                state = states[image.imageId] ?: AttachedImageRenderState.Generating,
                onRegenerate = regenerate?.let { action ->
                    val retry: () -> Unit = {
                        states = states + (image.imageId to AttachedImageRenderState.Generating)
                        scope.launch {
                            val resolution = try {
                                action(image)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                AttachedImageResolution.Unavailable(
                                    AttachedImageUnavailableReason.REGENERATION_FAILED,
                                    failure.message?.take(AttachedImageResolution.MAX_DETAIL_CHARS),
                                )
                            }
                            states = states + (image.imageId to attachedImageRenderState(resolution))
                        }
                    }
                    retry
                },
            )
        }
    }
}

/**
 * A2（4B）：助手消息里**持久引用**的生成图（`tutor_message_source_asset` 的资产 id），
 * 逐张解析为本地 URI 后渲染 [GeneratedFigureCard]。
 *
 * 消灭的失败：生成图的 URI 此前只活在 Compose `remember` 里——旋转/进程重建后要么图没了，
 * 要么重新出网生成（重复付费）。现在读的是消息行上的引用（持久事实），解析只走本地资产行，
 * 且同一 id 经 [GeneratedFigureAssetCache] 只解析一次（二次组合直接复用）。
 */
@Composable
fun GeneratedFiguresSection(
    assetIds: List<String>,
    resolveAsset: suspend (String) -> String?,
    modifier: Modifier = Modifier,
) {
    val cache = remember(resolveAsset) { GeneratedFigureAssetCache(resolveAsset) }
    var states by remember(assetIds) {
        mutableStateOf<Map<String, AttachedImageRenderState>>(
            assetIds.associateWith { assetId ->
                cache.cached(assetId) ?: AttachedImageRenderState.Generating
            },
        )
    }
    LaunchedEffect(assetIds) {
        assetIds.forEach { assetId ->
            if (states[assetId] is AttachedImageRenderState.Ready) return@forEach
            states = states + (assetId to AttachedImageRenderState.Generating)
            states = states + (assetId to cache.stateOf(assetId))
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        assetIds.forEach { assetId ->
            GeneratedFigureCard(
                assetId = assetId,
                state = states[assetId] ?: AttachedImageRenderState.Generating,
            )
        }
    }
}
