package com.tingyun.smartmistakebook

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal const val REVIEW_REMINDER_CHANNEL_ID = "daily_review_reminder"

internal interface ReviewReminderSystem {
    fun canPostNotifications(): Boolean
    fun ensureNotificationChannel()
    fun schedule(triggerAtEpochMillis: Long)
    fun cancel()
    fun postNotification(pendingCount: Int)
}

internal class ReviewReminderPlatform(
    context: Context,
) : ReviewReminderSystem {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun canPostNotifications(): Boolean = applicationContext.canPostReviewNotifications()

    override fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            REVIEW_REMINDER_CHANNEL_ID,
            "复习提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "每天在你选择的时间提醒打开今日错题复习"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun schedule(triggerAtEpochMillis: Long) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtEpochMillis,
            reminderPendingIntent(),
        )
    }

    override fun cancel() {
        alarmManager.cancel(reminderPendingIntent())
    }

    override fun postNotification(pendingCount: Int) {
        require(pendingCount > 0)
        if (!canPostNotifications()) return
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, REVIEW_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("今天的复习已准备好")
            .setContentText("有 $pendingCount 道题待复习，点这里开始。")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openReviewPendingIntent())
            .build()
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the explicit check and the system call.
        }
    }

    private fun reminderPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        REMINDER_REQUEST_CODE,
        Intent(applicationContext, ReviewReminderReceiver::class.java)
            .setAction(ReviewReminderContract.ACTION_DAILY_REMINDER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openReviewPendingIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        OPEN_REVIEW_REQUEST_CODE,
        Intent(applicationContext, MainActivity::class.java)
            .setAction(ReviewReminderContract.ACTION_OPEN_REVIEW)
            .putExtra(ReviewReminderContract.EXTRA_OPEN_REVIEW, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val NOTIFICATION_ID = 2_031
        const val REMINDER_REQUEST_CODE = 2_031
        const val OPEN_REVIEW_REQUEST_CODE = 2_032
    }
}

internal fun Context.canPostReviewNotifications(): Boolean =
    canPostNotificationsOnChannel(REVIEW_REMINDER_CHANNEL_ID)

/**
 * 渠道级通知可达性：[channelId] 为 null 读默认渠道口径的联合判定——权限、系统总开关、
 * 渠道重要度。复习提醒与后台导出共用（未授权时各自退化为应用内入口，不硬要权限）。
 */
internal fun Context.canPostNotificationsOnChannel(channelId: String): Boolean {
    if (!hasPostNotificationsPermission()) return false
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    val channel = getSystemService(NotificationManager::class.java)
        .getNotificationChannel(channelId)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

internal fun Context.hasPostNotificationsPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
