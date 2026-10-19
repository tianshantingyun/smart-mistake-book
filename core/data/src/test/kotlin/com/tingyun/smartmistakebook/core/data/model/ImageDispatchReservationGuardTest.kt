package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.FigureDispatchBindingTestFactory
import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchKind
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchReservation
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * KD-59 + WG-05 的**预留防线**：
 *
 * - 旧的无预留重载是 `DeprecationLevel.ERROR`，且默认的带预留实现**不再委托**给它——
 *   只实现旧重载的替身拿不到任何调用（默认 fail-closed）；
 * - [FigureImageDispatchBinding] 的构造器收窄为 `internal`，生产只能从账本授予的预留构造，
 *   测试走显式 opt-in 的 `forTest` 工厂；
 * - 全仓生产源码里，图像通道的 `generate/redrawClean` 调用必须带 `dispatch`（静态对拍）。
 */
@OptIn(FigureDispatchBindingTestFactory::class)
class ImageDispatchReservationGuardTest {

    @Test
    fun `the legacy channel entry points are deprecation errors`() {
        val source = source(
            "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/ImageGenerationChannel.kt",
        )

        assertEquals(
            "两个旧重载都必须标 ERROR 级弃用",
            2,
            Regex("@Deprecated\\(").findAll(source).count(),
        )
        assertTrue("必须是 ERROR 级：$source", source.contains("DeprecationLevel.ERROR"))
    }

    @Test
    fun `the dispatch binding constructor is internal and has an explicit test factory`() {
        val source = source(
            "core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/FigureGenerationCoordinator.kt",
        )

        assertTrue(
            "构造器必须 internal（生产只能从账本预留构造）",
            source.contains("internal constructor("),
        )
        assertTrue("必须有生产构造入口：$source", source.contains("fun fromReservation("))
        assertTrue("必须有显式测试工厂：$source", source.contains("fun forTest("))
    }

    @Test
    fun `a binding built from a reservation mirrors the reservation identity`() {
        val reservation = RemoteModelDispatchReservation(
            reservationId = "figure-dispatch:1",
            operationId = "op-1",
            parent = FigureDispatchParent.ModelTask("task-1", false),
            remoteKind = RemoteModelDispatchKind.IMAGE_GENERATION,
            method = FigureGenerationMethod.IMAGE_MODEL,
            purpose = FigureGenerationPurpose.EXPLANATION,
            policyVersion = "figure-generation-policy-v1",
            sourceImageSha256 = figureSha256Hex(byteArrayOf(1)),
            configurationFingerprint = "config-1",
            attemptOrdinal = 2,
            reservedAtEpochMillis = 1,
        )

        val binding = FigureImageDispatchBinding.fromReservation(reservation)

        assertEquals(reservation.reservationId, binding.reservationId)
        assertEquals(reservation.operationId, binding.operationId)
        assertEquals(reservation.method, binding.method)
        assertEquals(reservation.purpose, binding.purpose)
        assertEquals(reservation.attemptOrdinal, binding.attemptOrdinal)
        assertEquals(reservation.sourceImageSha256, binding.sourceImageSha256)
        assertEquals(reservation.configurationFingerprint, binding.configurationFingerprint)
    }

    @Test
    fun `a legacy-only channel cannot be reached through the reserved entry point`() = runBlocking {
        val channel = LegacyOnlyChannel()
        val binding = FigureImageDispatchBinding.forTest(
            reservationId = "figure-dispatch:test",
            operationId = "op-test",
            method = FigureGenerationMethod.IMAGE_MODEL,
            purpose = FigureGenerationPurpose.EXPLANATION,
            attemptOrdinal = 1,
            sourceImageSha256 = null,
            configurationFingerprint = "config-test",
        )

        val error = runCatching {
            channel.generate(ImageGenerationRequest(prompt = "画一条数轴"), binding)
        }.exceptionOrNull()

        assertTrue(
            "默认的带预留实现必须自己 fail-closed，不得委托给可覆盖的旧重载：$error",
            error is ImageDispatchNotReservedException,
        )
        assertEquals("旧重载一次都不许被调到", 0, channel.legacyCalls)
    }

    @Test
    fun `the legacy attached-image factory no longer owns an image backend`() {
        val source = source(
            "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/AttachedImageGeneratorFactory.kt",
        )

        assertFalse("不得自己建图像通道", source.contains("OpenAiImageGenerationChannel"))
        assertFalse("不得自己解析端点", source.contains("resolveGuardedEdits"))
        assertFalse("不得自己读凭证", source.contains("resolveImageCredential"))
        assertTrue(
            "显式重生成必须依赖共享协调器：$source",
            source.contains("FigureGenerationCoordinator"),
        )
    }

    @Test
    fun `no production image channel call is made without a dispatch binding`() {
        val offenders = productionKotlinFiles()
            .flatMap(::unboundChannelCalls)

        assertTrue(
            "发现无预留的图像通道调用：${offenders.joinToString()}",
            offenders.isEmpty(),
        )
    }

    // -----------------------------------------------------------------

    private fun source(relativeToRepo: String): String {
        val file = File(repositoryRoot(), relativeToRepo)
        assertTrue("测试前提：源码文件存在 ${file.absolutePath}", file.isFile)
        return file.readText()
    }

    private fun productionKotlinFiles(): List<File> {
        val repoRoot = repositoryRoot()
        val interfaceFile = File(
            repoRoot,
            "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/ImageGenerationChannel.kt",
        ).canonicalFile
        return listOf("core", "feature", "app")
            .flatMap { module ->
                File(repoRoot, module).walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .filter { it.path.replace('\\', '/').contains("/src/main/") }
                    .toList()
            }
            // 接口文件自身声明旧重载；它的默认实现已改为 fail-closed（下面另有运行时用例）。
            .filter { it.canonicalFile != interfaceFile }
    }

    /**
     * 只匹配“对图像通道变量的调用”（`channel.generate(...)` / `backend.channel().generate(...)`）。
     * 其它 `.generate(...)`（模型协调器、仓库等）不是这条防线的对象。
     */
    /** 从测试工作目录向上找 `settings.gradle.kts`，避免模块目录/仓库根两种 cwd 下静默空扫。 */
    private fun repositoryRoot(): File {
        var directory = File("").absoluteFile
        while (true) {
            if (File(directory, "settings.gradle.kts").isFile) return directory.canonicalFile
            directory = directory.parentFile
                ?: error("settings.gradle.kts not found above the test working directory")
        }
    }

    private fun unboundChannelCalls(file: File): List<String> {
        val text = stripComments(file.readText())
        val calls = Regex(
            """(?:[A-Za-z_][A-Za-z0-9_]*\.)?(?:image)?channel(?:\(\))?\.(generate|redrawClean)\s*\(""",
        ).findAll(text)
        return calls.mapNotNull { match ->
            val arguments = argumentsAt(text, match.range.last)
            if (arguments != null && !arguments.contains("dispatch")) {
                "${file.path}: ${match.value}"
            } else {
                null
            }
        }.toList()
    }

    private fun argumentsAt(text: String, openIndex: Int): String? {
        if (text.getOrNull(openIndex) != '(') return null
        var depth = 0
        for (index in openIndex until text.length) {
            when (text[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return text.substring(openIndex + 1, index)
                }
            }
        }
        return null
    }

    private fun stripComments(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//.*"""), "")

    /** 只实现旧重载的替身（模拟“新实现者沿用旧形状”）。 */
    @Suppress("DEPRECATION_ERROR")
    private class LegacyOnlyChannel : ImageGenerationChannel {
        var legacyCalls: Int = 0
            private set

        override suspend fun generate(request: ImageGenerationRequest): ImageRedrawResult {
            legacyCalls += 1
            return ImageRedrawResult(imageBytes = byteArrayOf(1), mimeType = "image/png")
        }

        override suspend fun redrawClean(request: ImageRedrawRequest): ImageRedrawResult {
            legacyCalls += 1
            return ImageRedrawResult(imageBytes = byteArrayOf(1), mimeType = "image/png")
        }
    }
}
