package com.tingyun.smartmistakebook

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo

internal const val MISTAKE_EXPORT_CHANNEL_ID = "mistake_export_results"

/** 通知点进「导出成果」的入口契约（与复习提醒的 ACTION_OPEN_REVIEW 同一形态）。 */
internal object MistakeExportContract {
    const val ACTION_OPEN_EXPORTS =
        "com.tingyun.smartmistakebook.action.OPEN_EXPORTS"
    const val EXTRA_OPEN_EXPORTS = "open_exports"
}

/**
 * 打开「导出成果」的唯一意图构造点：通知的 contentIntent 与外部入口共用它，
 * `MainActivity.recordOpenRequests` 消费的 action/extra 与这里逐字一致
 * （`MistakeExportBackgroundInstrumentedTest` 用同一个 builder 启动 MainActivity 钉端到端）。
 */
internal fun mistakeExportOpenIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(MistakeExportContract.ACTION_OPEN_EXPORTS)
        .putExtra(MistakeExportContract.EXTRA_OPEN_EXPORTS, true)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

/**
 * 后台导出的通知（阶段 4A 批 4 · L7）。
 *
 * **两态都如实**：完成说完成、失败说失败（失败正文就是记录里的学生可读原因）。
 *
 * **未授权不硬要**（Android 13+ POST_NOTIFICATIONS）：发不出通知时**什么都不做**——
 * 结果仍在「导出成果」入口里，那里是永远可用的应用内落点；不弹权限请求、不把导出结果
 * 标记成"已通知"。[canPost] 与复习提醒共用同一判定（`canPostNotificationsOnChannel`），
 * 学生在系统设置里关掉渠道同样被如实尊重。
 */
internal class MistakeExportNotifications(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            MISTAKE_EXPORT_CHANNEL_ID,
            "导出成果",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A4 导出在后台整理完成、或这次没有完成时告诉你"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun canPost(): Boolean = applicationContext.canPostNotificationsOnChannel(
        MISTAKE_EXPORT_CHANNEL_ID,
    )

    fun postSucceeded(exportId: String, displayName: String) {
        post(
            exportId = exportId,
            title = "A4 导出已完成",
            text = "$displayName 已可以保存、分享或打印。",
        )
    }

    fun postFailed(exportId: String, failureMessage: String) {
        post(
            exportId = exportId,
            title = "这次导出没有完成",
            text = failureMessage,
        )
    }

    /**
     * API < 31 上 expedited 任务的前台通知（见 `ExportPdfWorker.getForegroundInfo`）。
     * 这一条只说明"正在整理"，不代表结果。
     */
    fun foregroundInfo(): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, MISTAKE_EXPORT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在整理 A4 版式")
            .setContentText("导出会在后台完成，你可以先离开这个页面。")
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openExportsPendingIntent(FOREGROUND_NOTIFICATION_ID))
            .build()
        return ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun post(exportId: String, title: String, text: String) {
        if (!canPost()) return
        ensureChannel()
        val notificationId = notificationIdFor(exportId)
        val notification = NotificationCompat.Builder(applicationContext, MISTAKE_EXPORT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openExportsPendingIntent(notificationId))
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // 授权可能在检查与发送之间被收回：结果仍在应用内入口，静默收场即可。
        }
    }

    private fun openExportsPendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            applicationContext,
            requestCode,
            mistakeExportOpenIntent(applicationContext),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationIdFor(exportId: String): Int =
        NOTIFICATION_ID_BASE + (exportId.hashCode() and 0x0fff_ffff)

    private companion object {
        const val NOTIFICATION_ID_BASE = 4_100_000
        const val FOREGROUND_NOTIFICATION_ID = 4_199_999
    }
}
