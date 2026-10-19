package com.tingyun.smartmistakebook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.feature.capture.CaptureShareRequest
import com.tingyun.smartmistakebook.feature.capture.captureShareRequestFrom
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val reviewOpenRequests = MutableStateFlow(0L)
    private val exportOpenRequests = MutableStateFlow(0L)

    /**
     * A7（4C）：系统分享进 App 的**唯一投递口**。值是"待接收的分享"，Root 消费后清空。
     * 只存原始字段，不在 Activity 里读文件：读取/校验/落盘都在 Root 侧的
     * `CaptureShareIntakeRoute` 里完成（失败原因要能显示出来）。
     */
    private val sharedCaptureRequests = MutableStateFlow<CaptureShareRequest?>(null)
    private var sharedCaptureRequestSequence = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 配置变更会重投同一个分享 intent；只在"本 Activity 首次创建"时收分享，避免旋转后
        // 重复导入同一张图。复习/导出通知的计数是幂等的，照旧每次消费。
        handleOpenIntent(intent, consumeShare = savedInstanceState == null)
        enableEdgeToEdge()
        setContent {
            SmartMistakeBookTheme {
                SmartMistakeBookRoot(reviewOpenRequests, exportOpenRequests, sharedCaptureRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        (application as SmartMistakeBookApplication).apply {
            refreshStudyExperience()
            refreshReviewReminderSchedule()
        }
    }

    /**
     * 外部意图（通知 contentIntent、启动参数、系统分享）的消费点：`onCreate` / `onNewIntent` 都走这里。
     *
     * `internal` 是测试缝：`MistakeExportBackgroundInstrumentedTest` 用通知同一个 intent builder
     * 喂进来，钉住"通知 action/extra → 消费点 → 成果入口"这条链（不经过系统 Activity 投递）。
     * 分享链同样从这里喂（`SharedCaptureEntryInstrumentedTest`）。
     */
    internal fun handleOpenIntent(intent: Intent?, consumeShare: Boolean = true) {
        if (intent == null) return
        if (
            intent.action == ReviewReminderContract.ACTION_OPEN_REVIEW &&
            intent.getBooleanExtra(ReviewReminderContract.EXTRA_OPEN_REVIEW, false)
        ) {
            requestReviewOpen()
        }
        // L7：导出完成通知点进来 → 「导出成果」。与复习提醒同一条"请求计数"通道。
        if (
            intent.action == MistakeExportContract.ACTION_OPEN_EXPORTS &&
            intent.getBooleanExtra(MistakeExportContract.EXTRA_OPEN_EXPORTS, false)
        ) {
            requestExportsOpen()
        }
        // A7：系统分享。没有 URI 的分享也投递——界面上如实说"没有可导入的文件"，
        // 比什么都不发生更好查。
        if (consumeShare) {
            captureShareRequestFrom(intent, ++sharedCaptureRequestSequence)?.let { request ->
                sharedCaptureRequests.value = request
            }
        }
    }

    /** Root 已把这条分享交给接收页：清掉，避免重组合/返回时再次触发。 */
    internal fun consumeSharedCapture(requestId: Long) {
        val current = sharedCaptureRequests.value
        if (current?.requestId == requestId) {
            sharedCaptureRequests.compareAndSet(current, null)
        }
    }

    internal fun requestReviewOpen() {
        reviewOpenRequests.value += 1L
    }

    internal fun requestExportsOpen() {
        exportOpenRequests.value += 1L
    }
}
