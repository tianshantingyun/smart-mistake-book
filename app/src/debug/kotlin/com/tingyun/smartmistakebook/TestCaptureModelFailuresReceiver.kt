package com.tingyun.smartmistakebook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * debug-only 开关：用 adb 广播打开/关闭"失败模型调用响应原文"的本机捕获
 * （[DebugModelFailureCapture]）。**默认关**——不显式打开就什么也不写。
 *
 * ```
 * # 开：开始采集失败构成（落 private filesDir/debug-model-failures/，最多 20 条、原文 64 KiB/条）
 * adb shell am broadcast -p com.tingyun.smartmistakebook.localfirst \
 *   -a com.tingyun.smartmistakebook.TEST_CAPTURE_MODEL_FAILURES --ez enabled true
 * # 关：不再采集（已落盘条目保留，供复核）
 * adb shell am broadcast -p com.tingyun.smartmistakebook.localfirst \
 *   -a com.tingyun.smartmistakebook.TEST_CAPTURE_MODEL_FAILURES --ez enabled false
 * ```
 *
 * 与 `TestSeedModelConfigReceiver` 同一条边界：本类只存在于 debug 源集、由
 * `app/src/debug/AndroidManifest.xml` 声明（`exported` 是 adb 驱动所必需，边界靠源集而不是
 * exported 标志），release / benchmark 变体里没有它——`tools/ci/check_release_manifest.py`
 * 另有一道门钉住"debug 组件不进 release 合并清单、且确实进了 debug 合并清单"。
 *
 * 两条使用须知（照实写，别踩）：
 * - **开关随进程消亡**：这是进程内状态，应用被系统回收或重启后回到默认关，需要重发广播；广播本身
 *   能唤醒未运行的进程，但被 force-stop 过的要加 `--include-stopped-packages`。
 * - **落盘内容含学生内容**：只写应用私有目录，用完按 [DebugModelFailureCapture] 的 KDoc 清掉。
 */
class TestCaptureModelFailuresReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TEST_CAPTURE_MODEL_FAILURES) return
        val application = context.applicationContext
        // 缺省 = 关：只有显式 --ez enabled true 才武装捕获（"默认关"不靠调用方守规矩）。
        if (intent.getBooleanExtra(EXTRA_ENABLED, false)) {
            DebugModelFailureCapture.install(application)
        } else {
            DebugModelFailureCapture.uninstall()
        }
    }

    companion object {
        const val ACTION_TEST_CAPTURE_MODEL_FAILURES =
            "com.tingyun.smartmistakebook.TEST_CAPTURE_MODEL_FAILURES"
        const val EXTRA_ENABLED = "enabled"
    }
}
