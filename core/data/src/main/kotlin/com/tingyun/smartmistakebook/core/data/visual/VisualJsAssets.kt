package com.tingyun.smartmistakebook.core.data.visual

/**
 * SceneV1 编译器 / visualSdk 的 JS 资产装载（批次 27 T1；R1 §3.5：资产为 core:data
 * resources，UTF-8 随包）。
 *
 * **它消灭的具体失败**：JS 资产若以 Kotlin 长字符串常量内嵌在引擎源文件里，资产与
 * Node 测试台（`tools/visual_compiler/run_tests.mjs`）就各持一份副本，改编译器必须
 * 双处同步且无漂移报警。resources 单源让测试台与生产读**同一文件**；装载失败（缺文件/
 * 空白内容）在首次求值前显式爆炸，而不是静默编译出空指令序列。
 *
 * 惰性装载：构造 [QuickJsVisualExecutionEngine] 不触碰资源（JVM 单测的
 * "本地单测不执行任何 QuickJs 路径"不变量不受影响——资源读取本身也不涉原生库）；
 * 首个 compile 阶段/首个 prepare-render 阶段才读。
 */
internal object VisualJsAssets {

    /** SceneV1 → DrawOps V1 编译器（compile 第三上下文专用；无用户源码、无 SDK）。 */
    val compilerJs: String by lazy { readAsset("visual/visual-compiler.js", "visual-compiler.js") }

    /** SDK 图形原语（prepare/render 上下文；bootstrap 之后、用户源码之前求值）。 */
    val sdkJs: String by lazy { readAsset("visual/visual-sdk.js", "visual-sdk.js") }

    private fun readAsset(resourceName: String, displayName: String): String {
        val stream = VisualJsAssets::class.java.classLoader
            ?.getResourceAsStream(resourceName)
            ?: error("Missing bundled visual JS asset $displayName")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        check(text.isNotBlank()) { "Bundled visual JS asset $displayName is empty" }
        return text
    }
}
