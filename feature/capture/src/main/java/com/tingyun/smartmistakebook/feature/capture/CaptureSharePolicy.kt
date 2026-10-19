package com.tingyun.smartmistakebook.feature.capture

/**
 * A7（4C）：系统分享进 App 的**外部输入门**与计划判定。
 *
 * 这个文件只有纯逻辑（无 Android 类型），JVM 单测直接钉住每一条门；Android 侧的取字节、
 * 校验魔数、落私有缓存见 [CaptureShareIntake]。
 *
 * 显式的门（顺序即实现顺序，**先便宜后昂贵**，未通过的门不会打开任何流）：
 * 1. 去重后为空 → [CaptureShareRejection.NOTHING_SHARED]；
 * 2. 去重后超过 [MAX_SHARE_IMPORT_FILES] 个 → [CaptureShareRejection.TOO_MANY_FILES]
 *    （在碰 ContentResolver 之前拒绝，避免 31 个恶意 URI 触发 31 次 provider 打开）；
 * 3. 任一路径不是 `content://` → [CaptureShareRejection.UNSUPPORTED_SCHEME]
 *    （不把 `file://`/`http(s)://` 交给 ContentResolver，也不读任意路径）；
 * 4. 类型不是图片 / PDF → [CaptureShareRejection.UNSUPPORTED_TYPE]；
 * 5. 图片与 PDF 混在一起 → [CaptureShareRejection.MIXED_TYPES]；
 * 6. 多于一份 PDF → [CaptureShareRejection.MULTIPLE_PDFS]；
 * 7. 单文件超预算（图片 [MAX_SHARE_IMAGE_BYTES]、PDF [MAX_SHARE_PDF_BYTES]）或图片总量超过
 *    [MAX_SHARE_TOTAL_BYTES] → [CaptureShareRejection.FILE_TOO_LARGE]。
 *
 * 读侧（`ContentResolver.query` 的 `OpenableColumns.SIZE`）可能缺失或撒谎，因此第 7 条只是
 * **早拒绝**；真正的强制门是 [CaptureShareIntake] 的有界拷贝（图片）与既有批量管线
 * （PDF 128 MB / 2~30 页、图片每张 20 MB / 总量 256 MB）。两者同时存在，不是二选一。
 */
const val MAX_SHARE_IMPORT_FILES = 30

/**
 * 分享入口的单文件预算，与拍照/相册入口的 `MAX_CAPTURE_BYTES` 相同（20 MB）。
 *
 * 故意写成两份常量而不是互相引用：本文件是纯 JVM 逻辑，不想让一个安全预算隐式依赖
 * `CaptureCache.kt` 的 Android 依赖。`CaptureSharePolicyTest` 用断言把两个值钉在一起，
 * 任何一边漂移都会红。
 */
const val MAX_SHARE_IMAGE_BYTES = 20L * 1_024L * 1_024L

/** PDF 单文件预算：与 `PdfBatchImportPageRenderer` 的私有 128 MB 上限同口径。 */
const val MAX_SHARE_PDF_BYTES = 128L * 1_024L * 1_024L

/** 一次分享的图片总量预算：与 `AndroidBatchImportSourceStaging` 的 256 MB 同口径。 */
internal const val MAX_SHARE_TOTAL_BYTES = 256L * 1_024L * 1_024L

/** 从系统分享 intent 里带出来的原始请求（未校验；失败原因交给 [CaptureShareIntake]）。 */
data class CaptureShareRequest(
    val requestId: Long,
    val action: String?,
    val mimeType: String?,
    val uris: List<String>,
)

enum class CaptureShareRejection {
    NOTHING_SHARED,
    TOO_MANY_FILES,
    UNSUPPORTED_SCHEME,
    UNSUPPORTED_TYPE,
    MIXED_TYPES,
    MULTIPLE_PDFS,
    FILE_TOO_LARGE,
    NOT_AN_IMAGE,
    NOT_A_PDF,
    UNREADABLE,
    IMPORT_FAILED,

    /**
     * 读授权已经不可用：进程重建后授权随进程消失、被系统收回，或超过了接收窗口。
     *
     * 单独一个理由，不能并进 [UNREADABLE]——文件本身没坏，重试同一 URI 也不会成功，
     * 用户的出路是从系统里重新分享。
     */
    AUTHORIZATION_EXPIRED,
}

/** 分享接收的终局。成功两种落点：单图进录入页、多图/PDF 进既有整卷管线。 */
sealed interface CaptureShareResult {
    data class SingleImage(val ownedUri: String) : CaptureShareResult

    data class ImageBatch(val jobId: String) : CaptureShareResult

    data class Pdf(val jobId: String) : CaptureShareResult

    data class Rejected(
        val reason: CaptureShareRejection,
        val message: String,
    ) : CaptureShareResult
}

internal enum class CaptureShareKind {
    IMAGE,
    PDF,
}

internal sealed interface CaptureSharePlan {
    data class Images(val uris: List<String>) : CaptureSharePlan

    data class Pdf(val uri: String) : CaptureSharePlan

    data class Rejected(val reason: CaptureShareRejection) : CaptureSharePlan
}

/**
 * 只按声明类型判种类；不读文件。带 image 前缀的 MIME 视为图片（具体类型留给魔数校验）。
 * 刻意不写出 image 加通配符的 MIME 形式：Kotlin 块注释可嵌套，那一写法会让本段注释
 * 一直吞到文件尾（2026-10-19 门禁 Unclosed comment 的根因，改回时请守住这条）。
 */
internal fun captureShareKindOf(mimeType: String?): CaptureShareKind? = when {
    mimeType == null -> null
    mimeType.equals("application/pdf", ignoreCase = true) -> CaptureShareKind.PDF
    mimeType.startsWith("image/", ignoreCase = true) -> CaptureShareKind.IMAGE
    else -> null
}

/**
 * 分享内容的计划判定。`kindOf` / `sizeOf` 只在数量与 scheme 门通过后、对至多
 * [MAX_SHARE_IMPORT_FILES] 个 URI 调用——外部输入先过便宜的门。
 */
internal fun planCaptureShare(
    rawUris: List<String>,
    kindOf: (String) -> CaptureShareKind?,
    sizeOf: (String) -> Long?,
): CaptureSharePlan {
    val uris = rawUris
        .asSequence()
        .filter(String::isNotBlank)
        .distinct()
        .toList()
    if (uris.isEmpty()) return CaptureSharePlan.Rejected(CaptureShareRejection.NOTHING_SHARED)
    if (uris.size > MAX_SHARE_IMPORT_FILES) {
        return CaptureSharePlan.Rejected(CaptureShareRejection.TOO_MANY_FILES)
    }
    if (uris.any { !it.isContentScheme() }) {
        return CaptureSharePlan.Rejected(CaptureShareRejection.UNSUPPORTED_SCHEME)
    }
    val kinds = uris.map(kindOf)
    if (kinds.any { it == null }) {
        return CaptureSharePlan.Rejected(CaptureShareRejection.UNSUPPORTED_TYPE)
    }
    val images = uris.filterIndexed { index, _ -> kinds[index] == CaptureShareKind.IMAGE }
    val pdfs = uris.filterIndexed { index, _ -> kinds[index] == CaptureShareKind.PDF }
    if (images.isNotEmpty() && pdfs.isNotEmpty()) {
        return CaptureSharePlan.Rejected(CaptureShareRejection.MIXED_TYPES)
    }
    if (pdfs.size > 1) return CaptureSharePlan.Rejected(CaptureShareRejection.MULTIPLE_PDFS)
    if (pdfs.size == 1) {
        val pdf = pdfs.single()
        if ((sizeOf(pdf) ?: 0L) > MAX_SHARE_PDF_BYTES) {
            return CaptureSharePlan.Rejected(CaptureShareRejection.FILE_TOO_LARGE)
        }
        return CaptureSharePlan.Pdf(pdf)
    }
    // 每张图只查一次大小：单张门与总量门共用同一批读数。
    val imageSizes = images.map(sizeOf)
    if (imageSizes.any { size -> (size ?: 0L) > MAX_SHARE_IMAGE_BYTES }) {
        return CaptureSharePlan.Rejected(CaptureShareRejection.FILE_TOO_LARGE)
    }
    if (imageSizes.sumOf { size -> size ?: 0L } > MAX_SHARE_TOTAL_BYTES) {
        return CaptureSharePlan.Rejected(CaptureShareRejection.FILE_TOO_LARGE)
    }
    return CaptureSharePlan.Images(images)
}

private fun String.isContentScheme(): Boolean =
    length > "content://".length && regionMatches(0, "content://", 0, 10, ignoreCase = true)

internal fun captureShareRejectionMessage(reason: CaptureShareRejection): String = when (reason) {
    CaptureShareRejection.NOTHING_SHARED ->
        "这次分享里没有可导入的文件。"
    CaptureShareRejection.TOO_MANY_FILES ->
        "一次最多导入 $MAX_SHARE_IMPORT_FILES 个文件，请分批分享。"
    CaptureShareRejection.UNSUPPORTED_SCHEME ->
        "只能导入本机的文件。请从相册或文件管理器重新分享。"
    CaptureShareRejection.UNSUPPORTED_TYPE ->
        "只支持图片和 PDF。可以截图后从相册分享，或先导出成 PDF。"
    CaptureShareRejection.MIXED_TYPES ->
        "一次只能导入一种：请只分享图片，或只分享一份 PDF。"
    CaptureShareRejection.MULTIPLE_PDFS ->
        "一次只能导入一份 PDF。"
    CaptureShareRejection.FILE_TOO_LARGE ->
        "文件超过大小上限：图片每张 20 MB、PDF 128 MB，没有导入。"
    CaptureShareRejection.NOT_AN_IMAGE ->
        "这个文件不是可识别的图片，可能已损坏或格式不受支持。"
    CaptureShareRejection.NOT_A_PDF ->
        "这个文件不是可识别的 PDF，可能已损坏。"
    CaptureShareRejection.UNREADABLE ->
        "这个文件读不出来，可能已损坏或没有读取权限。"
    CaptureShareRejection.IMPORT_FAILED ->
        "文件已经收到，但这次没有保存成功，请稍后重试。"
    CaptureShareRejection.AUTHORIZATION_EXPIRED -> CAPTURE_SHARE_EXPIRED_MESSAGE
}

/** 可重试的失败：读取/落库的瞬时失败；类型、大小、数量与授权失效重试也不会变。 */
internal fun captureShareRejectionIsRetryable(reason: CaptureShareRejection): Boolean =
    reason == CaptureShareRejection.UNREADABLE || reason == CaptureShareRejection.IMPORT_FAILED

/** 授权失效的出口文案：和 [CaptureShareRejection.AUTHORIZATION_EXPIRED] 共用同一句。 */
internal const val CAPTURE_SHARE_EXPIRED_MESSAGE =
    "这次分享的内容已经失效，请从系统里重新分享一次。"

/**
 * 一次分享的读授权最长可用 10 分钟。
 *
 * 系统只把读授权给到接收它的活动/任务；进程被回收后 `rememberSaveable` 还能把请求恢复成
 * 非空，但授权已经不在了。接收页因此按"收下分享的时刻 + 上限"派生过期，而不是把恢复出来
 * 的非空请求当成永远可读；正常的手动重试远在窗口内完成。
 */
const val CAPTURE_SHARE_AUTHORIZATION_TIMEOUT_MILLIS = 10L * 60L * 1_000L

/**
 * 分享授权是否已超时。
 *
 * 恰好走到上限即判过期（页面上的定时器在 deadline 处停下，不会留下一个永远可点的
 * "再试一次"）。`now < authorizedAt`（系统时钟回拨）不判过期，避免一次回拨把还在窗口内的
 * 分享直接判死；非正的窗口一律立即过期。
 */
internal fun captureShareAuthorizationExpired(
    nowEpochMillis: Long,
    authorizedAtEpochMillis: Long,
    timeoutMillis: Long = CAPTURE_SHARE_AUTHORIZATION_TIMEOUT_MILLIS,
): Boolean {
    if (timeoutMillis <= 0L) return true
    return nowEpochMillis - authorizedAtEpochMillis >= timeoutMillis
}

/**
 * 录入页此刻还有一道题在进行：分享的图片不覆盖它。
 *
 * 改前若直接套用"作为新图"，`ApplyAsNew(resetDraft = true)` 会静默清掉学生还没提交的
 * 这道题——分享是"再录一道"，不是"放弃当前这道"。
 */
internal const val CAPTURE_SHARED_IMAGE_BUSY_ERROR =
    "分享的图片没有导入：录入页里还有一道题在进行，先完成或离开这一题再分享。"

/** 分享预拷件已被清掉（缓存清理/进程回收）：只能重新分享。 */
internal const val CAPTURE_SHARED_IMAGE_MISSING_ERROR =
    "分享的图片没有导入：临时文件已失效，请重新分享。"
