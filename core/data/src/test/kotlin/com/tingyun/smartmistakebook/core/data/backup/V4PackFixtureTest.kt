package com.tingyun.smartmistakebook.core.data.backup

import com.tingyun.smartmistakebook.core.domain.BackupValidation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

/**
 * KD-83 / WS5 item 42–44：**真实字节**的 closure 4（schema 72）备份包夹具。
 *
 * 本测试消灭的具体失败（批次 41 记录 F14 / R4 §0.1）：
 * 1. 旧 v4 用例的键集、版本号、前缀、摘要全部由被测代码（`ClosureRuleTables`）现算，
 *    规则表漂了测试跟着漂、用例照绿。这里换成 `tools/backup_fixtures/v4/` 的**冻结字节**
 *    与**另一个实现（Python hashlib）算出的 golden**；生产实现与 golden 只做单向比对。
 * 2. 旧 v4 用例的载荷是 16 字节假文件，行数恒 0。这里载荷是 `72.json` 物化的真库
 *    （`user_version=72`、91 张声明表、2 视图、`room_master_table`、真实行），
 *    声明行数与 golden 逐表相等。
 * 3. `ClosureRuleTables.v4` 的键集是 `v5 表集合 − 错因两表 + material_feature` 的算术派生
 *    （`ClosureRuleTables.kt:71-78`），没有任何东西钉住它与历史 91 键相等。本测试用冻结
 *    字面量 + `72.json` 实体集合双向绑定它。
 *
 * **JVM 能证到哪一层（如实）**：codec 接受、投影覆盖 92 键、摘要与独立 oracle 一致、
 * 负向包在 codec 前门被拒。**行数对账与 72→73 迁移不在这里**——它们要读真库、
 * 要在设备上跑，见 `BackupV4PackRestoreInstrumentedTest`。
 */
class V4PackFixtureTest {

    private val golden: JsonObject by lazy {
        Json.parseToJsonElement(File(repoRoot(), GOLDEN_JSON).readText()).jsonObject
    }

    @Test
    fun goldenPackBytesMatchFrozenSealAndCodecAccepts() {
        val pack = fixtureFile(GOLDEN_NAME)
        val bytes = pack.readBytes()
        assertEquals("冻结尺寸被改过：${pack.path}", golden.long("packBytes"), bytes.size.toLong())
        assertEquals("冻结 sha256 被改过：${pack.path}", golden.string("packSha256"), sha256Hex(bytes))
        assertEquals(
            "封存文件与包字节不符：${pack.path}.sha256",
            golden.string("packSha256"),
            File(pack.path + ".sha256").readText().trim(),
        )

        val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(bytes))
        assertTrue(
            "真实 v4 字节必须被 codec 接受：${(validation as? BackupValidation.Invalid)?.reason}",
            validation is BackupValidation.Valid,
        )
        val manifest = (validation as BackupValidation.Valid).manifest
        assertEquals("清单必须自称 schema 72", 72, manifest.databaseSchemaVersion)
        assertEquals("清单必须是 v3 格式", 3, manifest.formatVersion)
        assertEquals(
            "声明行数必须是 91 键（v4 规则）",
            91,
            manifest.selectionRowCounts?.size ?: -1,
        )
        assertEquals("合成件必须自报身份，不得伪装用户备份", "v4-fixture", manifest.appVersionName)
    }

    @Test
    fun goldenPackProjectsToCurrentSelectionKeySpace() {
        val destination = unpackDestination("v4-pack-projection")
        val outcome = SmbkArchiveCodec.unpackDetailed(
            ByteArrayInputStream(fixtureFile(GOLDEN_NAME).readBytes()),
            destination,
        )
        assertTrue(
            "真实 v4 字节必须可解包：${(outcome.validation as? BackupValidation.Invalid)?.reason}",
            outcome.validation is BackupValidation.Valid,
        )
        val projection = requireNotNull(outcome.projection) { "v4 包必须给出恢复投影" }
        assertEquals(4, projection.declaredClosureVersion)
        assertEquals("v4 声明 91 键", 91, projection.declaredRowCounts.size)
        assertEquals(
            "投影后的归一化键空间必须等于当前 92 键",
            SmbkSelectionPlan.rowCountKeys,
            projection.normalizedRowCounts.keys,
        )
        assertEquals("v4→v5 新增表按 0 补齐", 0L, projection.normalizedRowCounts["error_cause_observation"])
        assertEquals(0L, projection.normalizedRowCounts["error_cause_binding"])
        assertFalse(
            "被删表不得留在归一化键空间",
            "knowledge_search_material_feature" in projection.normalizedRowCounts,
        )
        assertEquals(
            "黄金件的 material_feature 必须真的是 0 行",
            0L,
            projection.declaredRowCounts.getValue("knowledge_search_material_feature"),
        )
    }

    @Test
    fun frozenKeySetMatchesProductionRuleTableAndSchema72() {
        val frozen = golden["rowCountKeys"]!!.jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()
        assertEquals("冻结字面量必须是 91 键", 91, frozen.size)

        assertEquals(
            "生产 ClosureRuleTables.v4 的键集与历史 91 键漂开（算术派生没有钉子）",
            frozen,
            ClosureRuleTables.v4.rowCountKeys,
        )
        assertEquals("closure 4 的裁剪上限必须是 83", 83, ClosureRuleTables.v4.trimOrderSize)
        assertEquals("closure 4 的摘要前缀", "smbk-selection-v4", ClosureRuleTables.v4.digestPrefix)
        assertEquals("closure 4 的版本号", 4, ClosureRuleTables.v4.closureVersion)
        assertEquals("v4 新增表", setOf("error_cause_observation", "error_cause_binding", "mistake_edit_job", "detailed_solution_manifest", "detailed_solution", "mistake_export_group", "paper_review", "paper_review_question", "paper_review_photo"), ClosureRuleTables.v4.addedTables)
        assertEquals("v4 被删表", setOf("knowledge_search_material_feature"), ClosureRuleTables.v4.droppedTables)

        // 第二源：v4 时点的导出 schema 实体集。与冻结字面量双向相等（缺一即失败）。
        val schema72 = Json.parseToJsonElement(
            File(repoRoot(), SCHEMA72_JSON).readText(),
        ).jsonObject.getValue("database").jsonObject
        val schemaTables = schema72.getValue("entities").jsonArray
            .map { it.jsonObject.getValue("tableName").jsonPrimitive.content }
            .toSet()
        assertEquals("72.json 实体数", 91, schemaTables.size)
        assertEquals("冻结键集 ≠ 72.json 实体集", frozen, schemaTables)
        assertEquals(
            "room_master_table 身份哈希必须是 72.json 的那一个",
            golden.string("schemaIdentityHash"),
            schema72.getValue("identityHash").jsonPrimitive.content,
        )
        // R3：摘要依赖类别的枚举 **name**；改名等于所有旧包摘要失配。
        assertEquals(
            setOf("ISSUE_BOOK", "MASTERY", "SESSIONS", "IMAGES"),
            SmbkSelectionPlan.categoriesOf(15).map { it.name }.toSet(),
        )
    }

    @Test
    fun productionDigestMatchesIndependentOracleForGoldenRowCounts() {
        val rowCounts = golden.getValue("rowCounts").jsonObject.mapValues { entry ->
            entry.value.jsonPrimitive.content.toLong()
        }
        assertEquals("golden 行数必须覆盖 91 键", 91, rowCounts.size)
        assertEquals(
            "生产 digest 与 Python hashlib 重算的 golden 不一致",
            golden.string("closureDigest"),
            ClosureRuleTables.digest(
                rule = ClosureRuleTables.v4,
                categories = SmbkSelectionPlan.categoriesOf(15),
                assetPolicy = "INCLUDED",
                rowCounts = rowCounts,
            ),
        )
        assertEquals(
            "非 0 表必须是冻结的那 4 张",
            mapOf(
                "attempt_submission" to 2L,
                "content_install_state" to 1L,
                "knowledge_node" to 1L,
                "problem" to 1L,
            ),
            golden.getValue("nonZeroTables").jsonObject.mapValues { it.value.jsonPrimitive.content.toLong() },
        )
    }

    @Test
    fun selfConsistentForgedRowCountPassesCodec() {
        // N1：声明 problem=5、实测 1，并**按声明重算摘要**。codec 只看自洽性 → 放行；
        // 这一层绿是设计，证明"自洽伪造"只能由恢复侧 R1（逐表 COUNT 对拍）抓。
        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(fixtureFile(FORGED_NAME).readBytes()),
        )
        assertTrue(
            "自洽伪造包必须在 codec 层通过（否则 R1 的用例证明不了它是最后一道）：$validation",
            validation is BackupValidation.Valid,
        )
        assertEquals(
            5L,
            (validation as BackupValidation.Valid).manifest.selectionRowCounts?.get("problem")
                ?: -1L,
        )
    }

    @Test
    fun droppedTableWithRowsIsRejectedByCodec() {
        // N2：被删表 material_feature 写 1 行并按 1 声明（摘要自洽）。codec 的 D3 门必须在
        // 任何行数对账之前整包拒绝——按 0 补齐等于静默丢用户数据。
        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(fixtureFile(DROPPED_NONEMPTY_NAME).readBytes()),
        )
        assertTrue("被删表非空必须被 codec 拒绝：$validation", validation is BackupValidation.Invalid)
        val reason = (validation as BackupValidation.Invalid).reason
        assertTrue("拒因必须点名被删表：$reason", reason.contains("knowledge_search_material_feature"))
        assertTrue("拒因必须说明是已废弃表/丢行：$reason", reason.contains("已废弃表"))
    }

    @Test
    fun payloadByteFlipIsRejected() {
        val bytes = fixtureFile(GOLDEN_NAME).readBytes()
        // 翻一位载荷字节：zip 的 CRC / sha256 / checksums 任一门都必须把它拦下。
        val flipped = bytes.copyOf()
        flipped[flipped.size / 2] = (flipped[flipped.size / 2].toInt() xor 0xFF).toByte()
        val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(flipped))
        assertTrue("篡改后的包必须被拒绝：$validation", validation is BackupValidation.Invalid)
        assertNotNull((validation as BackupValidation.Invalid).reason)
    }

    @Test
    fun assetMirrorMatchesAuthoritySourceByteForByte() {
        FIXTURE_FILE_NAMES.forEach { name ->
            val authority = File(repoRoot(), "$GOLDEN_REPO_DIR/$name")
            val mirror = File(repoRoot(), "$GOLDEN_ASSET_DIR/$name")
            assertTrue("权威源缺失：${authority.path}", authority.isFile)
            assertTrue(
                "androidTest 副本缺失：${mirror.path}（跑 ./gradlew :core:data:syncV4PackFixtures 生成）",
                mirror.isFile,
            )
            assertTrue(
                "副本与权威源不是逐字节相同（副本过期或被手改）：${authority.path} vs ${mirror.path}",
                authority.readBytes().contentEquals(mirror.readBytes()),
            )
        }
    }

    // ------------------------------------------------------------------

    private fun fixtureFile(name: String): File {
        val file = File(repoRoot(), "$GOLDEN_REPO_DIR/$name")
        assertTrue("夹具缺失：${file.path}", file.isFile)
        return file
    }

    private fun unpackDestination(prefix: String): File {
        val destination = File.createTempFile(prefix, "").apply {
            delete()
            mkdirs()
        }
        destination.deleteOnExit()
        return destination
    }

    private fun repoRoot(): File =
        locateRepoRoot() ?: error("找不到仓库根（settings.gradle.kts）")

    private fun locateRepoRoot(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return dir
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun JsonObject.string(key: String): String =
        getValue(key).jsonPrimitive.content

    private fun JsonObject.long(key: String): Long =
        getValue(key).jsonPrimitive.content.toLong()

    private companion object {
        const val GOLDEN_REPO_DIR = "tools/backup_fixtures/v4"
        const val GOLDEN_ASSET_DIR = "core/data/src/androidTest/assets/backup/v4"
        const val GOLDEN_JSON = "$GOLDEN_REPO_DIR/v4-pack.golden.json"
        const val SCHEMA72_JSON =
            "core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/72.json"
        const val GOLDEN_NAME = "closure4-schema72-mask15.smbk"
        const val FORGED_NAME = "forged-count.smbk"
        const val DROPPED_NONEMPTY_NAME = "dropped-nonempty.smbk"
        val FIXTURE_FILE_NAMES = listOf(
            GOLDEN_NAME,
            "$GOLDEN_NAME.sha256",
            FORGED_NAME,
            "$FORGED_NAME.sha256",
            DROPPED_NONEMPTY_NAME,
            "$DROPPED_NONEMPTY_NAME.sha256",
            "v4-pack.golden.json",
        )
    }
}
