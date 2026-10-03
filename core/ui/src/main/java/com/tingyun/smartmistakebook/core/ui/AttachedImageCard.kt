package com.tingyun.smartmistakebook.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
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
 * - [Failed]：这次没成——给学生一个**重试**出口，重试走幂等键（已生成过的图不会再付费）；
 * - [Ready]：本地 URI 到手，展开即见图。
 *
 * 消灭的失败：此前只有"图尚未生成"一句静态占位——生成中、失败、成功三种状态在学生眼里
 * 一模一样，失败后也没有任何可点的出口。
 */
sealed interface AttachedImageRenderState {
    data object Generating : AttachedImageRenderState
    data class Ready(val localUri: String) : AttachedImageRenderState
    data object Failed : AttachedImageRenderState
}

/** 解析结果 → 渲染状态（纯函数：null = 没拿到 URI = 这次失败）。 */
internal fun attachedImageRenderState(localUri: String?): AttachedImageRenderState =
    localUri?.let(AttachedImageRenderState::Ready) ?: AttachedImageRenderState.Failed

/**
 * 讲题回复附加图锚点：默认折叠为一行缩略摘要（图标 + 图题），点击展开为 [BoundedLocalImage]。
 * 图以 [imageLocalUri]（本地 file/content）渲染，绝不联网；[imageLocalUri] 为空时渲染一个"图未就绪"占位。
 * 与 [ThinkingCollapsibleCard] 同源——教育语境下先给文字讲解，图作为可选展开，不抢正文。
 *
 * A4：状态三态化（[state]）；[onRetry] 非空时失败态给一个"重试"按钮（生产里重试走幂等键）。
 */
@Composable
fun AttachedImageCard(
    image: AttachedImage,
    state: AttachedImageRenderState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = attachedImageCollapsedTitle(image)
    val accessibilityText = image.accessibilityText.ifBlank { image.description }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = accessibilityText,
                tint = InkSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (expanded) "已展开" else title,
                color = InkSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起图" else "展开图",
                tint = InkSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            when (state) {
                is AttachedImageRenderState.Ready -> BoundedLocalImage(
                    imageUri = state.localUri,
                    contentDescription = accessibilityText,
                    expanded = expanded,
                    modifier = Modifier.fillMaxWidth(),
                )

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
                        color = InkMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                AttachedImageRenderState.Failed -> Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Text(
                        text = "图未能生成。",
                        color = InkMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    onRetry?.let { retry ->
                        TextButton(
                            onClick = retry,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 0.dp,
                                vertical = 2.dp,
                            ),
                        ) {
                            Text(text = "重试", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
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
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起图" else "展开图",
                tint = InkSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            when (state) {
                is AttachedImageRenderState.Ready -> BoundedLocalImage(
                    imageUri = state.localUri,
                    contentDescription = "本轮生成的配图",
                    expanded = expanded,
                    modifier = Modifier.fillMaxWidth(),
                )

                AttachedImageRenderState.Generating -> Text(
                    text = "配图正在读取…",
                    color = InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                AttachedImageRenderState.Failed -> Text(
                    text = "配图暂不可用。",
                    color = InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
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
 * the figures fill in (or show a "生成中/未生成" placeholder) without blocking.
 *
 * This is the lightweight standalone pipeline for model-authored bitmap figures —
 * structured scene rendering was deleted with the visual chain (D-Q5), so this is
 * the only figure path a reply has.
 *
 * A4：三态（生成中/失败可重试/就绪）。重试只重跑**这一张**的解析——生产实现按幂等键
 * 命中已生成资产，重试不会重复付费。
 */
@Composable
fun AttachedImagesSection(
    images: List<AttachedImage>,
    resolve: suspend (AttachedImage) -> String?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var states by remember(images) {
        mutableStateOf<Map<String, AttachedImageRenderState>>(
            images.associate { image -> image.imageId to AttachedImageRenderState.Generating },
        )
    }
    suspend fun resolveInto(image: AttachedImage) {
        val uri = runCatching { resolve(image) }.getOrNull()
        states = states + (image.imageId to attachedImageRenderState(uri))
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
                onRetry = {
                    states = states + (image.imageId to AttachedImageRenderState.Generating)
                    scope.launch { resolveInto(image) }
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
