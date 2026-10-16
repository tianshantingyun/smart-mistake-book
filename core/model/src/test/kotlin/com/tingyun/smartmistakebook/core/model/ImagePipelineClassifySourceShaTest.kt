package com.tingyun.smartmistakebook.core.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * AUD-09（schema 17）：分类轮指纹带上**源图字节 sha**。
 *
 * 这组用例钉三件事：
 * 1. sha 真的进指纹——同一 id/尺寸、不同字节的两张图不再是同一个请求；
 * 2. 新键真的进编码——快照里带着 sha，读回后重算的指纹与存库值一致（否则 `ModelTaskSnapshot`
 *    的 require 会把已经落库的行读回即炸）；
 * 3. 旧行读回不漂——升级前写下的分类行没有这个键，重算指纹必须按"当年没有它"进行
 *    （strip 的判据是请求的 schemaVersion，不是值是否为空）。
 */
class ImagePipelineClassifySourceShaTest {

    @Test
    fun `the classify fingerprint changes when only the source image sha changes`() {
        val first = classifyRequest(sourceImageSha256 = SHA_A)
        val sameBytes = classifyRequest(sourceImageSha256 = SHA_A)
        val otherBytes = classifyRequest(sourceImageSha256 = SHA_B)
        val noSha = classifyRequest(sourceImageSha256 = null)

        assertEquals(
            "同一张图（同 sha）的指纹必须相同",
            ModelTaskFingerprint.of(first),
            ModelTaskFingerprint.of(sameBytes),
        )
        assertNotEquals(
            "换了源图字节（sha 不同）就不是同一个请求——净化作业的去重正是按这条判据",
            ModelTaskFingerprint.of(first),
            ModelTaskFingerprint.of(otherBytes),
        )
        assertNotEquals(
            "没有 sha 与有 sha 不是同一个请求",
            ModelTaskFingerprint.of(first),
            ModelTaskFingerprint.of(noSha),
        )
    }

    @Test
    fun `the encoded classify snapshot carries the source image sha`() {
        val encoded = ModelTaskCodec.encodeRequest(classifyRequest(sourceImageSha256 = SHA_A))
        assertTrue(
            "快照必须逐字带上源图 sha：$encoded",
            "\"sourceImageSha256\":\"$SHA_A\"" in encoded,
        )
        val decoded = ModelTaskCodec.decodeRequest(encoded)
        assertEquals(
            "读回后的重算指纹必须与写库时一致",
            ModelTaskFingerprint.of(classifyRequest(sourceImageSha256 = SHA_A)),
            ModelTaskFingerprint.of(decoded),
        )
        assertEquals(
            SHA_A,
            (decoded.input as ImagePipelineClassifyInput).sourceImageSha256,
        )
    }

    /**
     * 升级前的分类行（schema 16，没有这个键）读回后重算指纹必须与存库值一致：
     * 存库值当年的算法就是"对不含该键的 JSON 求 sha256"。
     */
    @Test
    fun `an upgrade era classify row without the key keeps its stored fingerprint`() {
        val legacyEncoded = ModelTaskCodec.encodeRequest(
            ModelTaskRequest(
                schemaVersion = 16,
                requestId = "save-decision:legacy-16",
                input = ImagePipelineClassifyInput(
                    sourceAssetId = "asset-legacy",
                    imageWidth = 96,
                    imageHeight = 128,
                    subjectIdOverride = "draft-legacy",
                ),
                occurredAtEpochMillis = 1_000,
                agentConsentGranted = true,
            ),
        )
        assertTrue(
            "先确认新编码器确实会写下这个键（不然下面的 strip 是空转）",
            "\"sourceImageSha256\":null" in legacyEncoded,
        )
        val legacyRow = legacyEncoded.replace(",\"sourceImageSha256\":null", "")

        val decoded = ModelTaskCodec.decodeRequest(legacyRow)
        assertEquals(16, decoded.schemaVersion)
        assertEquals(null, (decoded.input as ImagePipelineClassifyInput).sourceImageSha256)
        assertEquals(
            "旧行重算指纹必须等于当年写库的摘要（差一位都会让已落库的行读不回来）",
            sha256Hex(legacyRow),
            ModelTaskFingerprint.of(decoded),
        )
    }

    /**
     * 反向：schema ≥ 17 的行即使 sha 为空，那个键也是**当年写下的键**，不许被 strip 掉
     * （否则同一行的写库指纹与读回指纹会分叉）。
     */
    @Test
    fun `a current schema row keeps an explicitly null sha in its fingerprint`() {
        val request = ModelTaskRequest(
            schemaVersion = ModelTaskRequest.IMAGE_CLASSIFY_SOURCE_SHA_SCHEMA_VERSION,
            requestId = "save-decision:current-null-sha",
            input = ImagePipelineClassifyInput(
                sourceAssetId = "asset-current",
                imageWidth = 96,
                imageHeight = 128,
            ),
            occurredAtEpochMillis = 1_000,
            agentConsentGranted = true,
        )
        val encoded = ModelTaskCodec.encodeRequest(request)
        assertTrue("\"sourceImageSha256\":null" in encoded)
        assertEquals(
            "schema ≥ 17 的行必须按带键的编码重算指纹",
            sha256Hex(encoded),
            ModelTaskFingerprint.of(request),
        )
        assertNotEquals(
            "带键与不带键是两条不同的历史行，不能算出同一个指纹",
            sha256Hex(encoded.replace(",\"sourceImageSha256\":null", "")),
            ModelTaskFingerprint.of(request),
        )
    }

    /**
     * 逻辑操作指纹（无 schema 通道）同样要前后一致：升级前的分类行没有 sha 键，升级后重算它
     * （`verifyIntegrity` 在 transition/reserve 时会算）必须与存库值相同——否则旧行会被判成脏行。
     * 空 sha 与"没有这个键"等价；非空 sha 是真正的另一次操作（另一张图）。
     */
    @Test
    fun `an upgrade era classify input keeps its logical operation fingerprint`() {
        val upgradeEra = ImagePipelineClassifyInput(
            sourceAssetId = "asset-legacy",
            imageWidth = 96,
            imageHeight = 128,
            subjectIdOverride = "draft-legacy",
        )
        // 升级前的操作指纹是"kind + 换行 + 不含 sha 键的输入 JSON"的摘要（当年没有这个键）。
        val legacyJson = operationJson
            .encodeToString(ModelTaskInput.serializer(), upgradeEra)
            .replace(",\"sourceImageSha256\":null", "")
        assertEquals(
            "空 sha 不落键：升级前落库的操作指纹必须原样重算得回",
            sha256Hex("${upgradeEra.kind.name}\n$legacyJson"),
            ModelTaskLogicalOperationFingerprint.of(upgradeEra),
        )
        assertNotEquals(
            "另一张图（sha 不同）是另一次逻辑操作",
            ModelTaskLogicalOperationFingerprint.of(upgradeEra),
            ModelTaskLogicalOperationFingerprint.of(
                upgradeEra.copy(sourceImageSha256 = SHA_A),
            ),
        )
    }

    @Test
    fun `a legacy schema request cannot carry the source image sha`() {
        try {
            ModelTaskRequest(
                schemaVersion = 16,
                requestId = "save-decision:legacy-with-sha",
                input = ImagePipelineClassifyInput(
                    sourceAssetId = "asset-legacy",
                    imageWidth = 96,
                    imageHeight = 128,
                    sourceImageSha256 = SHA_A,
                ),
                occurredAtEpochMillis = 1_000,
                agentConsentGranted = true,
            )
            fail("schema < 17 的请求不许带源图 sha（旧编码器笔下不存在这个键）")
        } catch (expected: IllegalArgumentException) {
            assertTrue(
                "拒绝原因要说清是 schema 门：${expected.message}",
                expected.message.orEmpty().contains("source image sha"),
            )
        }
    }

    @Test
    fun `a malformed source image sha is rejected`() {
        listOf(
            "not-a-sha",
            SHA_A.uppercase(),
            SHA_A.dropLast(1),
            SHA_A + "0",
        ).forEach { malformed ->
            try {
                ImagePipelineClassifyInput(
                    sourceAssetId = "asset-1",
                    imageWidth = 96,
                    imageHeight = 128,
                    sourceImageSha256 = malformed,
                )
                fail("畸形 sha 必须被拒：$malformed")
            } catch (_: IllegalArgumentException) {
                // 期望路径：畸形 sha 在输入构造处即被拒，不会带着它出网或进指纹。
            }
        }
    }

    private fun classifyRequest(sourceImageSha256: String?) = ModelTaskRequest(
        requestId = "save-decision:revision-1",
        input = ImagePipelineClassifyInput(
            sourceAssetId = "asset-1",
            imageWidth = 96,
            imageHeight = 128,
            subjectIdOverride = "draft-1",
            sourceImageSha256 = sourceImageSha256,
        ),
        occurredAtEpochMillis = 1_000,
        agentConsentGranted = true,
    )

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    /** 与 `ModelTaskLogicalOperationFingerprint` 的 `logicalOperationJson` 逐项同形。 */
    private val operationJson = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    private companion object {
        /**
         * 两张"同 id、同尺寸、不同字节"的源图。SHA_A 带字母（大小写是两种不同的字面量，
         * 规范化判据只认小写）；SHA_B 全数字（`.uppercase()` 是它自己，不能用来测大写拒）。
         */
        const val SHA_A =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        const val SHA_B =
            "1111111111111111111111111111111111111111111111111111111111111111"
    }
}
