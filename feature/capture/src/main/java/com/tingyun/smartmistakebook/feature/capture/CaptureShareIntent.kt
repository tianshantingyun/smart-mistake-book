package com.tingyun.smartmistakebook.feature.capture

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * 把系统分享 intent 收成**未校验**的 [CaptureShareRequest]。
 *
 * 这里只做"从哪些字段取 URI"这一件事：`EXTRA_STREAM`（单/多）、`ClipData`（部分发送方
 * 用剪贴板数据代替 EXTRA_STREAM）、以及 `data`（少数发送方塞在这里）。去重保序；
 * 校验（scheme / 类型 / 数量 / 大小）全部在 [planCaptureShare] 与 [CaptureShareIntake]，
 * 这样失败原因才能如实落到界面上，而不是在这里悄悄丢掉。
 *
 * 空 URI 列表也返回一个请求：[CaptureShareIntake] 会给出"没有可导入的文件"，比什么都不
 * 发生更诚实。
 */
fun captureShareRequestFrom(intent: Intent?, requestId: Long): CaptureShareRequest? {
    if (intent == null) return null
    val action = intent.action
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    val uris = LinkedHashSet<String>()
    intentStreamUris(intent, action).forEach { uri -> uris += uri.toString() }
    return CaptureShareRequest(
        requestId = requestId,
        action = action,
        mimeType = intent.type,
        uris = uris.toList(),
    )
}

private fun intentStreamUris(intent: Intent, action: String): List<Uri> {
    val streams = if (action == Intent.ACTION_SEND_MULTIPLE) {
        // 少数发送方把单张图塞进 SEND_MULTIPLE 的 EXTRA_STREAM（不是 ArrayList）：
        // 列表取不到时退回单值读取，避免把一次合法的分享当成"没有文件"。
        intent.streamUriList().ifEmpty { listOfNotNull(intent.streamUri()) }
    } else {
        // 反向同理：ACTION_SEND 里也可能混进一个 ArrayList。取到内容就不再读另一形态。
        listOfNotNull(intent.streamUri()).ifEmpty { intent.streamUriList() }
    }
    val clipUris = intent.clipData
        ?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }
        .orEmpty()
    val explicit = streams + clipUris
    // `data` 只是兜底：分享 intent 通常把内容放在 EXTRA_STREAM/ClipData 里。两者都有内容时
    // 不再把 data 并进来——它可能是一个与本次分享无关的普通 URI（例如发送方自己的页面地址），
    // 并进来会把一份合法的分享误判成混合类型。
    return if (explicit.isNotEmpty()) explicit else listOfNotNull(intent.data)
}

private fun Intent.streamUri(): Uri? = runCatching {
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    }
}.getOrNull()

private fun Intent.streamUriList(): List<Uri> = runCatching {
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
}.getOrNull().orEmpty()

/**
 * 分享请求的 `rememberSaveable` 保存器：旋转/进程重建后，录入页仍在等同一个分享结果时
 * 不会退化成"分享已失效"。
 *
 * 保存形态刻意拍平成一串 Long/String（Compose 的 Bundle 保存只保证这些基础类型），
 * 恢复时 `requestId` + 3 个头部字段 + 剩余 URI。
 */
val CaptureShareRequestSaver: Saver<CaptureShareRequest?, Any> = listSaver(
    save = { request ->
        if (request == null) {
            emptyList()
        } else {
            listOf(
                request.requestId,
                request.action.orEmpty(),
                request.mimeType.orEmpty(),
            ) + request.uris
        }
    },
    restore = { saved ->
        if (saved.isEmpty()) {
            null
        } else {
            CaptureShareRequest(
                requestId = saved[0] as Long,
                action = (saved[1] as String).ifEmpty { null },
                mimeType = (saved[2] as String).ifEmpty { null },
                uris = saved.drop(3).map { it as String },
            )
        }
    },
)
