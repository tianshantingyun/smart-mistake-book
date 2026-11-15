package com.tingyun.smartmistakebook.core.model

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-06（批次 35）：工具描述按能力集合（figureMethods）派生，并用**长/短两版哈希快照**
 * 钉住默认文本（不是"包含某词"）。
 *
 * 覆盖 12 枚工具（8 枚讲题工具 + APP_CONTEXT / SELECT_QUESTION / READ_FIGURE /
 * RUN_JAVASCRIPT）。`GENERATE_FIGURE` 在含 JS 时按方法重写；其余工具不读方法参数。
 *
 * 快照是**有意变更**才更新的：改默认文案必须同时改这里的哈希，并在提交里说明原因。
 */
class TutorToolDescriptionsSnapshotTest {

    private val jsAndImage = setOf(FigureGenerationMethod.JS, FigureGenerationMethod.IMAGE_MODEL)
    private val jsOnly = setOf(FigureGenerationMethod.JS)

    @Test
    fun theDefaultDescriptionsMatchThePinnedHashes() {
        TutorToolName.entries.forEach { tool ->
            assertEquals(
                "长描述快照漂移：${tool.name}",
                DEFAULT_LONG_HASHES.getValue(tool.name),
                sha256(tool.purposeDescription()),
            )
            assertEquals(
                "短描述快照漂移：${tool.name}",
                DEFAULT_SHORT_HASHES.getValue(tool.name),
                sha256(tool.nativePurposeDescription()),
            )
        }
    }

    @Test
    fun aJavascriptCapabilityRewritesTheFigureDescriptionPerMethod() {
        listOf(jsAndImage, jsOnly).forEach { methods ->
            val longText = TutorToolName.GENERATE_FIGURE.purposeDescription(methods)
            assertTrue("必须按方法表述：含 method=JS：$longText", longText.contains("method=JS"))
            assertTrue("必须指名独立 program 字段：$longText", longText.contains("program 字段"))
            assertTrue(
                "必须禁止把程序写进 description：$longText",
                longText.contains("不要把程序写进 description"),
            )
            assertFalse(
                "JS 可用时不得再写图像模型缺省旧句：$longText",
                longText.contains("本地未配置可用的图像模型时也不会生成"),
            )
            assertTrue(
                "无当前题不生成的语义必须保留：$longText",
                longText.contains("没有当前题（如大厅轮次）时不会生成"),
            )

            val shortText = TutorToolName.GENERATE_FIGURE.nativePurposeDescription(methods)
            assertTrue("短版必须按方法表述：含 method=JS：$shortText", shortText.contains("method=JS"))
            assertTrue("短版必须指名 program 字段：$shortText", shortText.contains("program 字段"))
            assertFalse(
                "JS 可用时短版不得出现图像模型缺省语义：$shortText",
                shortText.contains("未配置") && shortText.contains("图像模型"),
            )
        }
    }

    @Test
    fun theOtherToolsIgnoreTheFigureMethodsParameter() {
        // 参数对非 GENERATE_FIGURE 工具是死参数（R1 §2.4）：含 JS 集合下与默认逐字节相同。
        TutorToolName.entries.filter { it != TutorToolName.GENERATE_FIGURE }.forEach { tool ->
            assertEquals(tool.purposeDescription(), tool.purposeDescription(jsAndImage))
            assertEquals(tool.nativePurposeDescription(), tool.nativePurposeDescription(jsAndImage))
        }
    }

    @Test
    fun theDefaultArgumentEqualsAnExplicitImageModelOnlySet() {
        TutorToolName.entries.forEach { tool ->
            assertEquals(
                tool.purposeDescription(setOf(FigureGenerationMethod.IMAGE_MODEL)),
                tool.purposeDescription(),
            )
            assertEquals(
                tool.nativePurposeDescription(setOf(FigureGenerationMethod.IMAGE_MODEL)),
                tool.nativePurposeDescription(),
            )
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        private val DEFAULT_LONG_HASHES: Map<String, String> = mapOf(
            "APP_CONTEXT" to "1d19071e1c43e5dd0580ab7aeefd461b6c73bfee33ee3a9544f24aa5e10c3a06",
            "SELECT_QUESTION" to "2368127a78bf91fde9a23d325b68b22e2b110a4375e07d965508b06e9df312b9",
            "READ_FIGURE" to "bbbf44e84e119016c74d0da4ff03ed9344db81da07fb9e8a49772ba6bd5ce899",
            "RUN_JAVASCRIPT" to "772ecd2fa22f103a1aa89d6d3183bed2d5bfe409ff4c636ff493b5ed424a8eb7",
            "KNOWLEDGE_READ" to "a3014232b1e0e3245bde16395bca3a0e1e03217a33b8f2077e1a96da744c8007",
            "NOTEBOOK_READ" to "624952d4b12263e60e981e97abab792873c3bec617024a0779768b0ee9b107c3",
            "MASTERY_READ" to "ecc00fa28c352e8617398979ef94492c00e9981fcfb40a55f0274dedef801816",
            "NOTEBOOK_WRITE" to "9b4839701d882c55e3a53ae17fee664db359708e640b3f96fcc381469e1edabc",
            "MASTERY_UPDATE" to "dfe95a71a887ffc3e3c5549f2213b1fbfc53f4c25633e6981f58e74aab0c000f",
            "ADVISORY_READ" to "c6e56be8c2998db28da8fdf5f0c4231f8f97fe4fa82dee5697f410791a8bdfbf",
            "ADVISORY_WRITE" to "d89017979706f3caee0a84fddb264f162ae762371d672f9256016c3dd5c8411c",
            "GENERATE_FIGURE" to "317526f0543758c7c0b66c8ce0cdb90f296cfc7020451f0a37c3514f30fdbc26",
        )

        private val DEFAULT_SHORT_HASHES: Map<String, String> = mapOf(
            "APP_CONTEXT" to "1d19071e1c43e5dd0580ab7aeefd461b6c73bfee33ee3a9544f24aa5e10c3a06",
            "SELECT_QUESTION" to "2368127a78bf91fde9a23d325b68b22e2b110a4375e07d965508b06e9df312b9",
            "READ_FIGURE" to "bbbf44e84e119016c74d0da4ff03ed9344db81da07fb9e8a49772ba6bd5ce899",
            "RUN_JAVASCRIPT" to "772ecd2fa22f103a1aa89d6d3183bed2d5bfe409ff4c636ff493b5ed424a8eb7",
            "KNOWLEDGE_READ" to "e2732acc8089a739cd1013189d11fc6ca12d750f5f42b2a4e5875f836490f364",
            "NOTEBOOK_READ" to "624952d4b12263e60e981e97abab792873c3bec617024a0779768b0ee9b107c3",
            "MASTERY_READ" to "a2af698be350d02c21a48bb840f1074cb62a26b92a5fb34079f33cb7e2e4ded0",
            "NOTEBOOK_WRITE" to "9b4839701d882c55e3a53ae17fee664db359708e640b3f96fcc381469e1edabc",
            "MASTERY_UPDATE" to "6a66cdf86bf06d78f8219f74b8876d55ecffe58d1e7c2024c2b13af0c5ff1afc",
            "ADVISORY_READ" to "3198b1d1f3051b6ffd300f1002a1b59167c9c59597280674ed765041e33a15c7",
            "ADVISORY_WRITE" to "4f021a04346c3d1b56dbe78e61be369adbbc712b13e6aa996cbf11eef8b6165d",
            "GENERATE_FIGURE" to "822ceef0238b43abb89e818ef60d193f2ceea3656eb5d052d387db11274811e0",
        )
    }
}
