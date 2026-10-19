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
 * `ImageCredentialGate.kt` 一处；仍在出网的两条图片链都必须经 `resolveImageCredential`。
 *
 * WG-05 起旧 `attachedImages` 工厂改为 cache-only（显式重生成交给共享 coordinator），
 * 因此它不再属于"需要过凭证门"的链路；这条用例额外钉住它**不得**再内联一份门。
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

        // WG-05 / KD-58：历史 attachedImages 工厂已改为 cache-only + 显式重生成经共享
        // coordinator——它本身不再读凭证/建通道，因此既不能有第二份门，也不该再被列进
        // “需要过凭证门”的链路清单。
        val legacyFactory = source("model/AttachedImageGeneratorFactory.kt")
        assertFalse(
            "旧工厂不得再读凭证（生成已移交给 coordinator）",
            legacyFactory.contains("resolveImageCredential"),
        )
        assertFalse(
            "旧工厂不得内联能力判定",
            legacyFactory.contains("supportsImageInput"),
        )
    }
}
