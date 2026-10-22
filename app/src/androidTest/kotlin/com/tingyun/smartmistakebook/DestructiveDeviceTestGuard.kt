package com.tingyun.smartmistakebook

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * 会清空**真实应用数据库**的设备用例的显式开关。
 *
 * 这些用例启动的是生产 `MainActivity`/`SmartMistakeBookApplication`，`clearAllData()` 删的是
 * 已安装应用的数据目录（不是测试 APK 的隔离目录）。在非专用设备上误跑一次就会清掉学生的
 * 错题/复习进度；KDoc 里写"仅用于专用测试设备"挡不住 `am instrument`。
 *
 * 因此默认**跳过**，只有显式传入 instrumentation 参数才执行：
 *
 * ```
 * adb shell am instrument -w \
 *   -e smb_allow_data_reset true \
 *   -e class com.tingyun.smartmistakebook.Prd06EmptyLibraryCaptureCtaInstrumentedTest \
 *   com.tingyun.smartmistakebook.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * 这是"设备守卫"而不是"隔离库"：当前 Application 的数据库实例在 `onCreate` 固定打开默认库，
 * 单测进程内替换会绕开生产装配，失去本用例要证明的"真实接线"。
 */
internal object DestructiveDeviceTestGuard {
    const val ALLOW_DATA_RESET_ARGUMENT: String = "smb_allow_data_reset"

    /** 必须在任何 `clearAllData()` / `deleteDatabase()` 之前调用。 */
    fun requireExplicitOptIn() {
        val enabled = InstrumentationRegistry.getArguments()
            .getString(ALLOW_DATA_RESET_ARGUMENT)
            ?.toBooleanStrictOrNull() == true
        assumeTrue(
            "这会清空已安装应用的真实数据库；只在专用测试设备上传 " +
                "-e $ALLOW_DATA_RESET_ARGUMENT true 再跑。",
            enabled,
        )
    }
}
