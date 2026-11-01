package com.tingyun.smartmistakebook.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-02 身份层（`visualId = programId`）的 JVM 钉板。**它先于实现写**（批次 30 / 任务 4）：
 * 没有这一层时，`programId` 由调用方随手给，同一份源码被"重写"后已显示的图会静默变成
 * 另一张；把身份改成**内容派生**之后，"源码/初始 state/basis/版本/角色任一变化 = 另一个 id"
 * 是纯函数的性质，不需要真库就能证明。
 *
 * 三条不变量：
 * 1. **确定性**：同样输入永远同一 id（重试/重放不换身份）；
 * 2. **区分度**：六个输入各自变化都换 id；
 * 3. **长度前缀**：`"ab" + "c"` 与 `"a" + "bc"` 不能撞（无前缀拼接会撞）。
 *
 * 同时钉住一个**黄金向量**：长度前缀的字节布局一旦改动，已落库的 programId 就不再匹配
 * （等于所有历史程序身份漂移），所以它必须是显式变更，不能悄悄漂。
 *
 * **快照身份（schema 71 / S1）**：`deriveSnapshotId` 自 v71 起是
 * `sha256(lp(programId) ‖ lp(stateSha) ‖ lp(drawSha) ‖ lp(rendererVersion) ‖ lp(fontVersion))`。
 * 这里同样钉黄金向量，并额外钉住"渲染/字体版本变化必须换 id"——版本不进派生式时，painter
 * 升级后的同内容提交会撞上旧行 id，`INSERT OR IGNORE` 把它吞掉，历史图从此只能只读恢复。
 */
class TutorVisualIdentityTest {

    @Test
    fun `program id is deterministic and pinned to the documented byte layout`() {
        val first = identity()
        val second = identity()

        assertEquals("同一内容必须派生同一身份（重放/重试不换 id）", first, second)
        assertEquals(
            "黄金向量：u32be 长度前缀 + 六段字节，改布局等于让历史身份全部漂移",
            GOLDEN_PROGRAM_ID,
            first,
        )
        assertTrue("身份前缀固定", first.startsWith(TutorVisualIdentity.PROGRAM_ID_PREFIX))
        assertEquals(
            "SHA-256 的十六进制长度",
            TutorVisualIdentity.PROGRAM_ID_PREFIX.length + 64,
            first.length,
        )
    }

    @Test
    fun `every identity input changes the id`() {
        val base = identity()
        val variants = mapOf(
            "source" to identity(sourceJs = "draw(2);"),
            "initial state" to identity(initialStateJson = """{"n":1}"""),
            "basis" to identity(basisFingerprint = "basis-other"),
            "api version" to identity(apiVersion = 2),
            "sdk version" to identity(sdkVersion = "visual-sdk-v2"),
            "role" to identity(role = "TUTOR_EXPLANATION"),
        )

        variants.forEach { (label, variant) ->
            assertNotEquals("$label 变化必须产生另一个 programId", base, variant)
        }
    }

    @Test
    fun `length prefixes keep adjacent variable fields unambiguous`() {
        // 无长度前缀的朴素拼接会把这两组输入摘要成同一串。
        val left = identity(sourceJs = "ab", initialStateJson = "c")
        val right = identity(sourceJs = "a", initialStateJson = "bc")

        assertNotEquals("长度前缀必须消除相邻字段的拼接歧义", left, right)
    }

    @Test
    fun `snapshot id is content and render-version addressed under the program`() {
        val first = snapshotId()

        assertEquals(
            "同 program 同内容同版本必须同一快照 id（重复提交幂等的基础）",
            first,
            snapshotId(),
        )
        assertEquals(
            "黄金向量：五段 u32be 长度前缀 + SHA-256 截断 32 位；改布局等于让历史快照身份漂移",
            GOLDEN_SNAPSHOT_ID,
            first,
        )
        assertTrue("快照身份前缀固定", first.startsWith(TutorVisualIdentity.SNAPSHOT_ID_PREFIX))
        assertEquals(
            "前缀 + 32 位十六进制截断",
            TutorVisualIdentity.SNAPSHOT_ID_PREFIX.length + 32,
            first.length,
        )

        assertNotEquals("state 变化必须换 id", first, snapshotId(stateJson = """{"n":2}"""))
        assertNotEquals("draw 变化必须换 id", first, snapshotId(drawCommandsJson = """{"ops":[1]}"""))
        assertNotEquals(
            "不同 program 的快照身份必须分开",
            first,
            snapshotId(programId = "visual-program-other"),
        )
        // schema 71 / S1 的核心：版本是身份的一部分。
        assertNotEquals(
            "渲染器版本变化必须产生另一个快照 id（否则新版本会撞旧行 id 被静默复用）",
            first,
            snapshotId(rendererVersion = "visual-renderer-v2"),
        )
        assertNotEquals(
            "字体清单版本变化必须产生另一个快照 id（否则像素门升级后历史图永久只读）",
            first,
            snapshotId(fontManifestVersion = "font-manifest-v2"),
        )
    }

    @Test
    fun `snapshot length prefixes keep adjacent version fields unambiguous`() {
        // 无长度前缀的朴素拼接会把这两组输入摘要成同一串。
        val left = snapshotId(rendererVersion = "a", fontManifestVersion = "bc")
        val right = snapshotId(rendererVersion = "ab", fontManifestVersion = "c")

        assertNotEquals("长度前缀必须消除相邻版本字段的拼接歧义", left, right)
    }

    @Test
    fun `sha256 helper matches the standard vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            TutorVisualIdentity.sha256Hex("abc"),
        )
        assertEquals(
            "对 UTF-8 字节取摘要（多字节字符按字节计）",
            TutorVisualIdentity.sha256Hex("图".toByteArray(Charsets.UTF_8)),
            TutorVisualIdentity.sha256Hex("图"),
        )
    }

    private fun identity(
        sourceJs: String = SOURCE_JS,
        initialStateJson: String = INITIAL_STATE_JSON,
        basisFingerprint: String = BASIS_FINGERPRINT,
        apiVersion: Int = API_VERSION,
        sdkVersion: String = SDK_VERSION,
        role: String = ROLE,
    ): String = TutorVisualIdentity.deriveProgramId(
        sourceJs = sourceJs,
        initialStateJson = initialStateJson,
        basisFingerprint = basisFingerprint,
        apiVersion = apiVersion,
        sdkVersion = sdkVersion,
        role = role,
    )

    private fun snapshotId(
        programId: String = PROGRAM_ID,
        stateJson: String = SNAPSHOT_STATE_JSON,
        drawCommandsJson: String = SNAPSHOT_DRAW_COMMANDS_JSON,
        rendererVersion: String = RENDERER_VERSION,
        fontManifestVersion: String = FONT_MANIFEST_VERSION,
    ): String = TutorVisualIdentity.deriveSnapshotId(
        programId = programId,
        stateSha256 = TutorVisualIdentity.sha256Hex(stateJson),
        drawCommandsSha256 = TutorVisualIdentity.sha256Hex(drawCommandsJson),
        rendererVersion = rendererVersion,
        fontManifestVersion = fontManifestVersion,
    )

    private companion object {
        const val SOURCE_JS = "draw();"
        const val INITIAL_STATE_JSON = "{}"
        const val BASIS_FINGERPRINT = "basis-batch30"
        const val API_VERSION = 1
        const val SDK_VERSION = "visual-sdk-v1"
        const val ROLE = "QUESTION_DIAGRAM"
        const val PROGRAM_ID = "visual-program-batch30"
        const val SNAPSHOT_STATE_JSON = """{"n":1}"""
        const val SNAPSHOT_DRAW_COMMANDS_JSON = """{"ops":[]}"""
        const val RENDERER_VERSION = "visual-renderer-v1"
        const val FONT_MANIFEST_VERSION = "font-manifest-v1"

        /**
         * 由外部脚本按文档布局独立算出（u32be(len) ‖ bytes，五段）：
         * `sha256(lp("visual-program-batch30") lp(sha256('{"n":1}'))
         *  lp(sha256('{"ops":[]}')) lp("visual-renderer-v1") lp("font-manifest-v1"))[:32]`。
         */
        const val GOLDEN_SNAPSHOT_ID = "visual-snapshot-1f055235820cba524d912db1488b1174"

        /**
         * 由外部脚本按文档布局独立算出（u32be(len) ‖ bytes，六段）：
         * `sha256(lp("draw();") lp("{}") lp("basis-batch30") lp("1") lp("visual-sdk-v1")
         * lp("QUESTION_DIAGRAM"))`。
         */
        const val GOLDEN_PROGRAM_ID =
            "visual-program-8e8b99a013b3a3468facb145bce512a8aa12f1240b7ffa9246d04d772eeccf7e"
    }
}
