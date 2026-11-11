package com.tingyun.smartmistakebook

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 导出通知的两态（阶段 4A 批 4 · L7）：**授权 → 两态都如实**；
 * **未授权（Android 13+）→ 不硬要权限、不发通知**，结果仍落在「已导出的文件」的应用内入口。
 *
 * 拒绝态的运行前提（撤销运行期权限会杀进程，不能在测试进程内自我 revoke）：
 * 先 `adb shell pm revoke <pkg> android.permission.POST_NOTIFICATIONS`，再带
 * `-e smb_notifications_denied=true` 启动。类级直接跑会在该用例 fail-closed。
 */
@RunWith(AndroidJUnit4::class)
class MistakeExportNotificationsInstrumentedTest {

    @Test
    fun grantedPermissionPostsBothTerminalStatesWithADeepLink() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        manager.deleteNotificationChannel(MISTAKE_EXPORT_CHANNEL_ID)
        grantNotificationPermission(context.packageName)
        try {
            assertTrue(context.canPostNotificationsOnChannel(MISTAKE_EXPORT_CHANNEL_ID))
            val notifications = MistakeExportNotifications(context)

            notifications.postSucceeded(
                exportId = "export-notify-success",
                displayName = "错题-函数单调区间-第3版.pdf",
            )
            val success = waitForNotification(manager, "A4 导出已完成")
            assertEquals(
                "错题-函数单调区间-第3版.pdf 已可以保存、分享或打印。",
                success.extras.getString(Notification.EXTRA_TEXT),
            )
            assertNotNull("点通知要能回应用内", success.contentIntent)

            notifications.postFailed(
                exportId = "export-notify-failed",
                failureMessage = "这版题面还不完整，暂时无法导出。",
            )
            val failure = waitForNotification(manager, "这次导出没有完成")
            assertEquals(
                "失败通知的正文必须照抄记录里的原因，不许泛化成\"出错了\"",
                "这版题面还不完整，暂时无法导出。",
                failure.extras.getString(Notification.EXTRA_TEXT),
            )
        } finally {
            manager.cancelAll()
        }
    }

    @Test
    fun withoutPermissionNothingIsPostedAndNothingThrows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        // Android 在撤销运行期权限时会杀掉目标进程；在测试进程内自我 revoke 会
        // 让本次 instrumentation 以 "Process crashed." 收尾（批次 44 A 域实测）。
        // 因此拒绝态必须由外部在启动 instrumentation 之前设置，并用参数声明；
        // 缺失该参数时直接失败，而不是 assumeTrue 跳过（跳过不计通过）。
        check(permissionWasRevokedBeforeLaunch()) {
            "拒绝态用例必须在启动前由外部执行 pm revoke，并传 " +
                "-e smb_notifications_denied=true"
        }
        assertFalse(context.canPostNotificationsOnChannel(MISTAKE_EXPORT_CHANNEL_ID))
        val notifications = MistakeExportNotifications(context)

        // 不弹权限请求、不抛异常、不留下任何通知：结果仍在应用内入口。
        notifications.postSucceeded(
            exportId = "export-notify-denied-success",
            displayName = "错题-函数单调区间-第3版.pdf",
        )
        notifications.postFailed(
            exportId = "export-notify-denied-failed",
            failureMessage = "这版题面还不完整，暂时无法导出。",
        )
        SystemClock.sleep(200)
        assertTrue(
            "未授权时不许有导出通知",
            manager.activeNotifications.none { notification ->
                val title = notification.notification.extras
                    .getString(Notification.EXTRA_TITLE)
                title == "A4 导出已完成" || title == "这次导出没有完成"
            },
        )
    }

    private fun permissionWasRevokedBeforeLaunch(): Boolean =
        InstrumentationRegistry.getArguments()
            .getString("smb_notifications_denied")
            ?.toBooleanStrictOrNull() == true

    private fun grantNotificationPermission(packageName: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    private fun waitForNotification(
        manager: NotificationManager,
        title: String,
    ): Notification {
        repeat(40) {
            manager.activeNotifications
                .map { it.notification }
                .firstOrNull { it.extras.getString(Notification.EXTRA_TITLE) == title }
                ?.let { return it }
            SystemClock.sleep(50)
        }
        error("Export notification \"$title\" was not posted within 2 seconds.")
    }
}
