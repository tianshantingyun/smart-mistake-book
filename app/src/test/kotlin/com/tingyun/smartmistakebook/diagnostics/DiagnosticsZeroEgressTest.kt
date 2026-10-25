package com.tingyun.smartmistakebook.diagnostics

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T6：默认零外发的结构性断言（"网络观测=0"）。证明边界要如实说清：
 *
 * 1. strictOffline flavor 的 manifest 用 `tools:node="remove"` 物理移除 INTERNET 与
 *    ACCESS_NETWORK_STATE（app/src/strictOffline/AndroidManifest.xml）——该 flavor 的
 *    APK 无网络权限，任何代码路径都连不了网。
 * 2. 主 manifest（两个 flavor 共享的部分）不申请任何网络权限；localFirst 的网络权限
 *    是模型调用线的，诊断代码不触碰。
 * 3. 诊断源码不引用任何网络 API（见 FORBIDDEN_NETWORK_TOKENS）——不存在"本地构造了
 *    请求等着发"的路径。导出走的 ContentResolver/SAF 是本机 IPC，不是网络。
 *
 * 这是结构层面的"默认零外发"证明；不做运行时抓包（无设备面）。
 */
class DiagnosticsZeroEgressTest {

    @Test
    fun mainManifestRequestsNoNetworkPermission() {
        val manifest = appModuleFile("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
        assertFalse(
            "主 manifest 不得申请 INTERNET（localFirst 的网络权限在它自己的 flavor manifest）",
            manifest.contains("android.permission.INTERNET"),
        )
        assertFalse(
            "主 manifest 不得申请 ACCESS_NETWORK_STATE",
            manifest.contains("android.permission.ACCESS_NETWORK_STATE"),
        )
    }

    @Test
    fun strictOfflineManifestPhysicallyRemovesNetworkPermissions() {
        val manifest = appModuleFile("src/strictOffline/AndroidManifest.xml").readText(Charsets.UTF_8)
        assertTrue(
            "strictOffline 必须显式移除 INTERNET",
            manifest.contains("android.permission.INTERNET") &&
                manifest.contains("tools:node=\"remove\""),
        )
        assertTrue(
            "strictOffline 必须显式移除 ACCESS_NETWORK_STATE",
            manifest.contains("android.permission.ACCESS_NETWORK_STATE"),
        )
    }

    @Test
    fun diagnosticsSourcesReferenceNoNetworkApis() {
        val diagnosticsDir = File(appModuleDir(), "src/main/kotlin/com/tingyun/smartmistakebook/diagnostics")
        val sources = diagnosticsDir.listFiles { file -> file.extension == "kt" }
        assertNotNull("诊断源码目录必须存在：${diagnosticsDir.absolutePath}", sources)
        assertTrue("诊断源码目录不能为空", sources!!.isNotEmpty())
        val offenders = sources
            .flatMap { file ->
                val text = file.readText(Charsets.UTF_8)
                FORBIDDEN_NETWORK_TOKENS
                    .filter { text.contains(it) }
                    .map { token -> "${file.name} 含 $token" }
            }
        assertTrue(
            "诊断源码不得引用任何网络 API：$offenders",
            offenders.isEmpty(),
        )
    }

    private fun appModuleFile(relative: String): File {
        val file = File(appModuleDir(), relative)
        assertTrue("找不到 $relative（相对 ${File(".").absolutePath}）", file.isFile)
        return file
    }

    private fun appModuleDir(): File {
        // Gradle 单测的工作目录是模块目录（app/）；本地工具链运行时是仓库根。
        val candidates = listOf(File("app"), File("."))
        return candidates.firstOrNull { candidate ->
            File(candidate, "src/main/AndroidManifest.xml").isFile
        } ?: error("无法定位 app 模块目录（当前目录 ${File(".").absolutePath}）")
    }

    private companion object {
        val FORBIDDEN_NETWORK_TOKENS = listOf(
            "java.net.",
            "HttpURLConnection",
            "URLConnection",
            "openConnection",
            "okhttp",
            "retrofit",
            ".Socket(",
            "URL(",
        )
    }
}
