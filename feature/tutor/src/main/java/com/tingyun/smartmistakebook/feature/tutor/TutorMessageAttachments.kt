package com.tingyun.smartmistakebook.feature.tutor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import com.tingyun.smartmistakebook.core.ui.ClaudeLayout
import com.tingyun.smartmistakebook.core.ui.claudeInteractiveRow
import com.tingyun.smartmistakebook.core.ui.Ink
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import com.tingyun.smartmistakebook.core.ui.ClaudeAlertDialog as AlertDialog
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.MAX_TUTOR_MESSAGE_IMAGES
import com.tingyun.smartmistakebook.core.ui.BoundedLocalImage
import com.tingyun.smartmistakebook.core.ui.LocalImageViewer
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.Paper
import com.tingyun.smartmistakebook.core.ui.SmartDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 学生待发送的附件：只持有本地 uri，真正的登记（进规范资产库、拿哈希与尺寸）发生在发送时。
 *
 * 抽在这里而不是留在某一个入口里，是因为大厅与讲题会话都要用同一套选图/预览/气泡渲染——
 * 两份实现迟早会分叉，而分叉的表现会是"同一个操作在两个页面行为不同"。
 */
internal data class PendingMessageImage(
    val localUri: String,
)

private data class ViewedMessageImage(val uri: String, val title: String)

/** Query only local display metadata; a provider failure keeps the honest numbered fallback. */
private fun localImageDisplayName(context: Context, localUri: String): String? = runCatching {
    val uri = Uri.parse(localUri)
    val name = when (uri.scheme) {
        "content" -> context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        "file" -> uri.lastPathSegment
        else -> null
    }
    name?.filterNot(Char::isISOControl)?.take(120)?.takeIf(String::isNotBlank)
}.getOrNull()

/**
 * 输入框上方的附件预览行（微信式）：缩略图 + 删除角标。
 *
 * [testTagPrefix] 让两个入口各自保留可定位的标签，UI 测试不会互相串到另一个页面。
 */
@Composable
internal fun PendingMessageImagesRow(
    images: List<PendingMessageImage>,
    onRemove: (Int) -> Unit,
    testTagPrefix: String,
) {
    var viewing by remember(images) { mutableStateOf<ViewedMessageImage?>(null) }
    val context = LocalContext.current
    val scroll = rememberLazyListState()
    Column {
        LazyRow(
            state = scroll,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("${testTagPrefix}_pending_images"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(images.size) { index ->
                val uri = images[index].localUri
                val displayName by key(uri) {
                    produceState<String?>(null) {
                        value = withContext(Dispatchers.IO) { localImageDisplayName(context, uri) }
                    }
                }
                val label = displayName ?: "图片 ${index + 1}"
                Row(Modifier.border(1.dp, Ink.copy(alpha = .1f), RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically) {
                    BoundedLocalImage(
                        imageUri = images[index].localUri,
                        contentDescription = "待发送图片 ${index + 1}，$label",
                        expanded = false,
                        collapsedMaxHeight = 48.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewing = ViewedMessageImage(uri, "待发送图片 ${index + 1} · $label") },
                    )
                    Column(Modifier.widthIn(max = 144.dp).padding(horizontal = 8.dp)) {
                        Text(label, style = ClaudeLayout.ControlText, color = Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("本机图片 · 待发送", style = ClaudeLayout.ControlText, color = InkSecondary)
                    }
                    IconButton(
                        onClick = { onRemove(index) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("${testTagPrefix}_pending_image_remove_$index"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "移除图片 ${index + 1}，$label",
                            tint = InkSecondary,
                            modifier = Modifier
                                .size(20.dp),
                        )
                    }
                }
            }
        }
        if (scroll.canScrollBackward || scroll.canScrollForward) Text(
            "共 ${images.size} 张，可左右滑动查看", color = InkSecondary, style = ClaudeLayout.ControlText,
            modifier = Modifier.padding(bottom = 8.dp).testTag("${testTagPrefix}_images_overflow"))
    }
    viewing?.let { LocalImageViewer(it.uri, it.title, { viewing = null }) }
}

/** 气泡内的消息图片行：从规范资产解析本地 uri 后内嵌渲染（微信式）。 */
@Composable
internal fun MessageImagesRow(
    assetIds: List<String>,
    imageIntake: LobbyMessageImageIntake,
    testTagPrefix: String,
) {
    var viewing by remember(assetIds) { mutableStateOf<ViewedMessageImage?>(null) }
    val scroll = rememberLazyListState()
    val uris by produceState<Map<String, String?>>(emptyMap(), assetIds, imageIntake) {
        val resolved = buildMap {
            for (assetId in assetIds) {
                put(assetId, imageIntake.resolveImageUri(assetId))
            }
        }
        value = resolved
    }
    Column {
        LazyRow(state = scroll, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(assetIds.size) { index ->
                val assetId = assetIds[index]
                val uri = uris[assetId]
                if (uri != null) {
                    BoundedLocalImage(
                        imageUri = uri,
                        contentDescription = "消息图片 ${index + 1}",
                        expanded = false,
                        collapsedMaxHeight = 140.dp,
                        modifier = Modifier
                            .size(width = 120.dp, height = 120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewing = ViewedMessageImage(uri, "消息图片 ${index + 1}") }
                            .testTag("${testTagPrefix}_message_image_$index"),
                    )
                } else {
                    Text(
                        text = "图片暂时打不开",
                        color = InkSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        if (scroll.canScrollBackward || scroll.canScrollForward) Text(
            "共 ${assetIds.size} 张，可左右滑动查看", color = InkSecondary, style = ClaudeLayout.ControlText,
            modifier = Modifier.testTag("${testTagPrefix}_images_overflow"))
    }
    viewing?.let { LocalImageViewer(it.uri, it.title, { viewing = null }) }
}

/** 加号菜单：拍一张新照片、从相册选择，或从错题库挑一道题加进来。 */
@Composable
internal fun MessageAttachmentDialog(
    onDismiss: () -> Unit,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    testTagPrefix: String,
    /** 页面没有错题库入口时留空，那一项就不出现。 */
    onPickFromLibrary: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        emphasizeConfirm = false,
        title = { Text("添加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 三个动作同权，所以都排在内容区；确认键留给"取消"。
                AttachmentMenuRow(
                    text = "拍照",
                    icon = Icons.Outlined.CameraAlt,
                    onClick = onLaunchCamera,
                    testTag = "${testTagPrefix}_attach_camera",
                )
                AttachmentMenuRow(
                    text = "从相册选择",
                    icon = Icons.Outlined.PhotoLibrary,
                    onClick = onLaunchGallery,
                    testTag = "${testTagPrefix}_attach_gallery",
                )
                onPickFromLibrary?.let { pick ->
                    AttachmentMenuRow(
                        text = "从错题本选择",
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        onClick = pick,
                        testTag = "${testTagPrefix}_attach_library",
                    )
                }
                // 两个入口都会用这个菜单，但选中后的落地不同：大厅是打开那道题的讲题页，
                // 讲题会话里是把它接在发送的下一轮上。所以这里只说两处都成立的那件事
                // ——选中之后，要讲的就是这道题——不写"作为这一轮"这种只对其中一个成立的表述。
                Text(
                    text = "最多添加 ${MAX_TUTOR_MESSAGE_IMAGES} 张图片；从错题本选题后，将切换到那道题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("${testTagPrefix}_attach_cancel"),
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AttachmentMenuRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .claudeInteractiveRow(onClick)
            .padding(vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(text = text, modifier = Modifier.padding(start = 10.dp))
    }
}
