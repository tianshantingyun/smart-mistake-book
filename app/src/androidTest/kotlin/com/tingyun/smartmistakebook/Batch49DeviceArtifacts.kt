package com.tingyun.smartmistakebook

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * 批次 49 / P2-15 设备证据补件：把 Composable 屏幕真实渲染成 PNG + uiautomator 语义树。
 *
 * 消灭的具体失败：批次 49 的 UI 证据只有 Compose 语义断言（contentDescription/文案），
 * 没有落 PNG、没跑 `tools/ci/a11y_audit.py`——"无障碍/截图"两项在台账里只能记未做。
 * 本工具在仪器化测试内：
 * - `capturePng`：`captureToImage()` 写 MediaStore（`Pictures/SmartMistakeBookQA/`），
 *   测试后用 `adb pull` 取回；
 * - `dumpA11y`：`uiautomator dump` 写 `/sdcard/Download/<name>.xml`，供 `a11y_audit.py`
 *   离线审计可点击节点尺寸/朗读文本。
 *
 * 注意：这是测试证据，不是产品写路径；不新增任何产品依赖。
 */
internal object Batch49DeviceArtifacts {

    fun capturePng(rule: SemanticsNodeInteractionsProvider, name: String) {
        val resolver = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .contentResolver
        val collection = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        resolver.delete(
            collection,
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(name),
        )
        val uri = checkNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartMistakeBookQA/")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            ),
        )
        val bitmap: Bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        checkNotNull(resolver.openOutputStream(uri)).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "screenshot could not be encoded: $name"
            }
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
    }

    /**
     * 用 UiAutomation 的活动窗口语义树直接序列化成 uiautomator 形状的 XML。
     *
     * 为什么不用 `uiautomator dump` 子命令：仪器化测试运行期间 UiAutomation 已被测试
     * 进程占用，`uiautomator dump` 返回空输出（第一次实测就是 `uiautomator dump failed: `）。
     * `rootInActiveWindow` 读的是同一棵无障碍树，不依赖第二个 uiautomator 进程。
     */
    fun dumpA11y(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        // 默认 serviceInfo 不带检索交互窗口的 flag，rootInActiveWindow 会返回 null。
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val root = run {
            // 全类连跑时窗口切换有延迟：轮询最多 5s，别让 dump 的时序问题伪装成 UI 失败。
            var node = automation.rootInActiveWindow
            var attempts = 0
            while (node == null && attempts < 25) {
                SystemClock.sleep(200)
                node = automation.rootInActiveWindow
                attempts += 1
            }
            checkNotNull(node) { "no active accessibility window to dump" }
        }
        val xml = buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
            append("<hierarchy rotation=\"0\">\n")
            appendNode(root, this)
            append("</hierarchy>\n")
        }
        val dir = checkNotNull(instrumentation.targetContext.getExternalFilesDir("batch49")) {
            "external files dir unavailable"
        }
        File(dir, "$name.xml").writeText(xml, Charsets.UTF_8)
    }

    private fun appendNode(node: AccessibilityNodeInfo, out: StringBuilder) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        out.append("<node")
            .append(" class=\"").append(xmlEscape(node.className)).append('"')
            .append(" text=\"").append(xmlEscape(node.text)).append('"')
            .append(" content-desc=\"").append(xmlEscape(node.contentDescription)).append('"')
            .append(" clickable=\"").append(node.isClickable).append('"')
            .append(" enabled=\"").append(node.isEnabled).append('"')
            .append(" focused=\"").append(node.isFocused).append('"')
            .append(" selected=\"").append(node.isSelected).append('"')
            .append(" scrollable=\"").append(node.isScrollable).append('"')
            .append(" bounds=\"[").append(bounds.left).append(',').append(bounds.top)
            .append("][").append(bounds.right).append(',').append(bounds.bottom).append("]\"")
        if (node.childCount == 0) {
            out.append(" />\n")
        } else {
            out.append(">\n")
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { appendNode(it, out) }
            }
            out.append("</node>\n")
        }
    }

    private fun xmlEscape(value: CharSequence?): String =
        (value?.toString() ?: "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
