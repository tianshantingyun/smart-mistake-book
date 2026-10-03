package com.tingyun.smartmistakebook.core.data.capture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A3：图片出网门只有**一份实现**（`core:data.model` 的 `resolveImageCredential`）。
 *
 * 消灭的失败：`ConfiguredCleanImageGenerator` 曾内联抄一份同样的判定（"构建不可出网 /
 * 没配凭证 / 能力测试没确认图像输入 → 拒"），`ImageCredentialGate.kt` 的 KDoc 也自认
 * "Mirrors the gating in the clean-redraw generator"。两份判定一旦漂开，同一份用户配置
 * 会在"保存时重绘"与"模型配图 / 生图"两条链上做出不同决定——而这类漂移不会让任何行为
 * 用例变红（两条链各有自己的 happy path）。
 *
 * 这条用例用**源码对拍**钉住"单一来源"：本模块里 `supportsImageInput` 只允许出现在
 * `ImageCredentialGate.kt` 一处；三条图片链都必须经 `resolveImageCredential`。
 * 这是变异测试的静态落点——把内联门抄回去，这条就红。
 */
class ImageCredentialGateSingleSourceTest {

    private fun source(relativePath: String): String {
        val file = File("src/main/kotlin/com/tingyun/smartmistakebook/core/data/$relativePath")
        assertTrue("测试前提：源码文件存在 ${file.absolutePath}", file.isFile)
        return file.readText()
    }

    @Test
    fun theCapabilityGateExistsOnlyInTheSharedImplementation() {
        val gate = source("model/ImageCredentialGate.kt")
        assertTrue("门实现必须读能力位", gate.contains("supportsImageInput"))

        listOf(
            "capture/ConfiguredCleanImageGenerator.kt",
            "model/AttachedImageGeneratorFactory.kt",
            "model/TutorFigureGenerator.kt",
        ).forEach { path ->
            val chain = source(path)
            assertTrue(
                "$path 必须走 resolveImageCredential（唯一门实现）",
                chain.contains("resolveImageCredential("),
            )
            assertFalse(
                "$path 不得内联 capabilityVerification/supportsImageInput 判定（第二份门实现）",
                chain.contains("supportsImageInput"),
            )
        }
    }
}
