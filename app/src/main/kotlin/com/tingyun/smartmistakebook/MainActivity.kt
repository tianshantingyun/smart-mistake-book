package com.tingyun.smartmistakebook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val reviewOpenRequests = MutableStateFlow(0L)
    private val exportOpenRequests = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOpenIntent(intent)
        enableEdgeToEdge()
        setContent {
            SmartMistakeBookTheme {
                SmartMistakeBookRoot(reviewOpenRequests, exportOpenRequests)
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
        (application as SmartMistakeBookApplication).refreshStudyExperience()
    }

    /**
     * 外部意图（通知 contentIntent、启动参数）的消费点：`onCreate` / `onNewIntent` 都走这里。
     *
     * `internal` 是测试缝：`MistakeExportBackgroundInstrumentedTest` 用通知同一个 intent builder
     * 喂进来，钉住"通知 action/extra → 消费点 → 成果入口"这条链（不经过系统 Activity 投递）。
     */
    internal fun handleOpenIntent(intent: Intent?) {
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
    }

    internal fun requestReviewOpen() {
        reviewOpenRequests.value += 1L
    }

    internal fun requestExportsOpen() {
        exportOpenRequests.value += 1L
    }
}
