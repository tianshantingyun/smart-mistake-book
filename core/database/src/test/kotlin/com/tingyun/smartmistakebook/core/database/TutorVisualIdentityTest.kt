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
    fun `snapshot id is content addressed under the program`() {
        val state = TutorVisualIdentity.sha256Hex("""{"n":1}""")
        val draw = TutorVisualIdentity.sha256Hex("""{"ops":[]}""")
        val otherState = TutorVisualIdentity.sha256Hex("""{"n":2}""")
        val otherDraw = TutorVisualIdentity.sha256Hex("""{"ops":[1]}""")

        val first = TutorVisualIdentity.deriveSnapshotId(PROGRAM_ID, state, draw)
        assertEquals(
            "同 program 同 state 同 draw 必须同一快照 id（重复提交幂等的基础）",
            first,
            TutorVisualIdentity.deriveSnapshotId(PROGRAM_ID, state, draw),
        )
        assertNotEquals(first, TutorVisualIdentity.deriveSnapshotId(PROGRAM_ID, otherState, draw))
        assertNotEquals(first, TutorVisualIdentity.deriveSnapshotId(PROGRAM_ID, state, otherDraw))
        assertNotEquals(
            "不同 program 的快照身份必须分开",
            first,
            TutorVisualIdentity.deriveSnapshotId("visual-program-other", state, draw),
        )
        assertTrue(first.startsWith(TutorVisualIdentity.SNAPSHOT_ID_PREFIX))
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

    private companion object {
        const val SOURCE_JS = "draw();"
        const val INITIAL_STATE_JSON = "{}"
        const val BASIS_FINGERPRINT = "basis-batch30"
        const val API_VERSION = 1
        const val SDK_VERSION = "visual-sdk-v1"
        const val ROLE = "QUESTION_DIAGRAM"
        const val PROGRAM_ID = "visual-program-batch30"

        /**
         * 由外部脚本按文档布局独立算出（u32be(len) ‖ bytes，六段）：
         * `sha256(lp("draw();") lp("{}") lp("basis-batch30") lp("1") lp("visual-sdk-v1")
         * lp("QUESTION_DIAGRAM"))`。
         */
        const val GOLDEN_PROGRAM_ID =
            "visual-program-8e8b99a013b3a3468facb145bce512a8aa12f1240b7ffa9246d04d772eeccf7e"
    }
}
