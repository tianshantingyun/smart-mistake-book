package com.tingyun.smartmistakebook.core.data.knowledge

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialBindingVerdict
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialBindingVerdictSource
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialNodeRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **D-0 端到端演练**：合成 v3 侧车文本走真实 installer/reconciler 路径，落到真实 DB。
 *
 * 只有这条能同时钉住三层，因为各层自己的测试都看不到另两层：
 * ① **解析 → 包校验 → 调和全绿**：真 codec（`ReviewedTeachingMaterialSidecarJsonCodec`）解析测试内
 *    构造的 v3 侧车文本 → 真 `KnowledgeBasePack.validate()` → 真
 *    `BundledKnowledgeBaseInstaller.reconcile`（→ `applyKnowledgeContentUpdate` →
 *    `replaceMaterialBindings`），进度表 skipped 必须为 0；
 * ② **字段在包级可见**：`KnowledgeBasePack.teachingMaterialBindings` 上同一材料的两条绑定，
 *    已审计的那条带完整三元组、未审计的那条三字段全 null；
 * ③ **DB 端如实不含**：绑定表只有 `material_id / knowledge_node_id / role` 三列，
 *    裁定三元组的列**在库里连名字都不存在**。「落库即丢弃」是已裁定的边界（无消费方 → 零 schema），
 *    不是事故——所以这里把它写成断言，而不是等着它哪天变成事故。
 *
 * 夹具：**真实包结构**（真 `moe-2025-four-subjects-v1.json` 的包体，读侧只读）+ 测试内构造的
 * v3 侧车文本。合成文本不落任何随包资源目录。
 *
 * 教训（本用例实测才会暴露的一层）：材料侧车可以引用**包内来源**
 * （codec 的可用来源是 `pack.sources + sidecar.sources`），但调和器的材料契约只拿
 * `command.teachingSources` 去匹配（`RoomKnowledgeContentReconciler.planMaterials`）——
 * 所以夹具必须像真实侧车那样**自带教学活动来源**，否则材料会在调和期被跳过。
 */
@RunWith(AndroidJUnit4::class)
class BindingVerdictEndToEndDrillInstrumentedTest {

    @Test
    fun v3VerdictTripleIsVisibleAtPackLevelAndHonestlyAbsentFromTheBindingTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "kb-binding-verdict-drill-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            // ---- 夹具：真实包体 + 测试内构造的 v3 侧车文本 ----
            val base = ReviewedKnowledgePackJsonCodec.decode(readBundledResource(PACK_RESOURCE))
            val auditedNode = base.nodes.firstOrNull { it.knowledgeNodeId == AUDITED_NODE_ID }
            val unauditedNode = base.nodes.firstOrNull { it.knowledgeNodeId == UNAUDITED_NODE_ID }
            assertTrue(
                "夹具靶节点必须真的在随包里，包结构一变这条演练就该红：$AUDITED_NODE_ID",
                auditedNode != null,
            )
            assertTrue(
                "夹具的未审计靶节点必须真的在随包里：$UNAUDITED_NODE_ID",
                unauditedNode != null,
            )
            val sidecarText = v3SidecarJson(
                subject = requireNotNull(auditedNode).subject,
                materialReviewedAtEpochMillis = auditedNode.createdAtEpochMillis,
            )

            // ---- ① 解析（真 codec，内部含包级 validate）----
            val sidecar = ReviewedTeachingMaterialSidecarJsonCodec.decode(sidecarText, base)
            assertEquals(PACK_ID, sidecar.packId)
            assertEquals("合成侧车只有一条材料", 1, sidecar.materials.size)
            assertEquals("同一材料上已审计与未审计绑定混排", 2, sidecar.bindings.size)
            assertEquals("夹具自带一条教学活动来源", 1, sidecar.sources.size)
            assertEquals(DRILL_SOURCE_ID, sidecar.sources.single().sourceId)
            val material = sidecar.materials.single()

            // ---- ① 聚合（与 `BundledKnowledgePackResources.loadResource` 的三段同形；
            //      夹具材料有绑定，所以生产里"未绑定材料剔除"那一步在此为 no-op）----
            val pack = base.copy(
                teachingSources = sidecar.sources,
                teachingMaterials = sidecar.materials,
                teachingMaterialBindings = sidecar.bindings,
            ).also { it.validate() }

            // ---- ② 字段在包级可见 ----
            val audited = pack.teachingMaterialBindings.single { it.knowledgeNodeId == AUDITED_NODE_ID }
            assertEquals(KnowledgeMaterialNodeRole.PRIMARY.name, audited.role)
            assertEquals(KnowledgeMaterialBindingVerdict.KEEP.name, audited.verdict)
            assertEquals(KnowledgeMaterialBindingVerdictSource.MODEL_AUDIT.name, audited.verdictSource)
            assertEquals(JUDGED_AT_EPOCH_MILLIS, requireNotNull(audited.judgedAtEpochMillis))

            // ---- ④ 未审计绑定：三元组整体缺席 ----
            val unaudited = pack.teachingMaterialBindings.single { it.knowledgeNodeId == UNAUDITED_NODE_ID }
            assertEquals(KnowledgeMaterialNodeRole.SUPPORTING.name, unaudited.role)
            assertNull(unaudited.verdict)
            assertNull(unaudited.verdictSource)
            assertNull(unaudited.judgedAtEpochMillis)

            // ---- ① 调和（真实 installer/reconciler 路径）----
            BundledKnowledgeBaseInstaller.reconcile(store, pack, manifest = null)

            val installState = requireNotNull(store.readContentInstallState(PACK_ID)) {
                "调和跑完必须留下进度行"
            }
            assertEquals(
                "调和必须全绿（逐对象跳过是可见的，skipped_detail 见下）：${installState.skippedDetail}",
                0,
                installState.skippedCount,
            )
            assertEquals(
                "合成材料必须真的落库",
                1,
                store.readKnowledgeTeachingMaterialsByIds(setOf(material.materialId)).size,
            )
            assertEquals(
                "侧车自带的教学活动来源必须真的落库（含指纹）",
                listOf(DRILL_SOURCE_FINGERPRINT),
                store.readKnowledgeSourcesByIds(setOf(DRILL_SOURCE_ID)).map { it.contentFingerprint },
            )

            // ---- ③ DB 端如实不含：绑定表恰好三列语义 ----
            val bindingColumns = readRows(
                context,
                databaseName,
                "PRAGMA table_info(knowledge_teaching_material_node_binding)",
            ).map { requireNotNull(it["name"]) }
            assertEquals(
                "绑定表列集必须恰好是这三列（裁定三元组在库端如实不含）：$bindingColumns",
                listOf("knowledge_node_id", "material_id", "role"),
                bindingColumns.sorted(),
            )

            val bindingRows = readRows(
                context,
                databaseName,
                "SELECT material_id, knowledge_node_id, role FROM knowledge_teaching_material_node_binding " +
                    "WHERE material_id = '${material.materialId}'",
            )
            assertEquals("两条绑定都必须落库", 2, bindingRows.size)
            assertTrue(
                "每行只有三列语义：${bindingRows.firstOrNull()?.keys}",
                bindingRows.all { it.size == 3 },
            )
            assertEquals(
                setOf(
                    AUDITED_NODE_ID to KnowledgeMaterialNodeRole.PRIMARY.name,
                    UNAUDITED_NODE_ID to KnowledgeMaterialNodeRole.SUPPORTING.name,
                ),
                bindingRows.map { it["knowledge_node_id"] to it["role"] }.toSet(),
            )

            // 裁定列在库里连名字都不存在——"落库即丢弃"是边界，不是事故，故直接钉死查询会失败。
            val selectVerdict = runCatching {
                readRows(
                    context,
                    databaseName,
                    "SELECT verdict FROM knowledge_teaching_material_node_binding LIMIT 1",
                )
            }
            val failure = selectVerdict.exceptionOrNull()
            assertTrue("SELECT verdict 必须失败（库里没有这一列）：$selectVerdict", failure is SQLiteException)
            assertTrue(
                "失败原因必须是缺列：${failure?.message}",
                failure?.message.orEmpty().contains("no such column"),
            )

            // 走 App 自己的读回路径：绑定记录的三元组字段必须是 null（字段到此为止）。
            val readBack = store.readKnowledgeTeachingMaterialNodeBindings(setOf(material.materialId))
            assertEquals("读回路径应看到两条绑定", 2, readBack.size)
            assertTrue(
                "读回路径必须如实不含裁定三元组：$readBack",
                readBack.all {
                    it.verdict == null && it.verdictSource == null && it.judgedAtEpochMillis == null
                },
            )
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 测试内构造的 v3 侧车文本：包 id、节点 id、材料审校时刻全部来自真实包，其余是合成夹具。
     *
     * `sources` 里那条来源不是装饰：调和器的材料契约只认 `command.teachingSources`
     * （`RoomKnowledgeContentReconciler.planMaterials`），而 codec 允许引用包内来源——
     * 两条边界的交集要求夹具**自带来源**，真实侧车也是这么写的。
     */
    private fun v3SidecarJson(
        subject: String,
        materialReviewedAtEpochMillis: Long,
    ): String = """
        {
          "schemaVersion": 3,
          "packId": "$PACK_ID",
          "sources": [{
            "sourceId": "$DRILL_SOURCE_ID",
            "subject": "$subject",
            "sourceType": "AUTHORIZED_EDUCATION_MATERIAL",
            "title": "D-0 演练教学活动来源",
            "publisher": "演练夹具",
            "edition": "2026",
            "sourceUri": "https://example.org/d0-binding-verdict-drill",
            "licenseStatus": "LICENSED",
            "contentFingerprint": "$DRILL_SOURCE_FINGERPRINT",
            "importedAtEpochMillis": $DRILL_SOURCE_IMPORTED_AT_EPOCH_MILLIS,
            "contentUsePolicy": "ADAPTATION_ALLOWED",
            "licenseExpression": "CC-BY-NC-SA-4.0",
            "licenseUri": "https://creativecommons.org/licenses/by-nc-sa/4.0/",
            "attributionText": "演练夹具《D-0 绑定裁定三元组》，依 CC BY-NC-SA 4.0 改编。"
          }],
          "materials": [{
            "slug": "drill-binding-verdict-triple",
            "subject": "$subject",
            "type": "WORKED_EXAMPLE",
            "title": "D-0 演练材料：绑定裁定三元组",
            "summaryMarkdown": "同一材料上混排已审计与未审计绑定。",
            "applicabilityMarkdown": "仅用于 D-0 端到端演练，不作为真实教学内容。",
            "contentMarkdown": "这条材料只证明裁定三元组能随包走到库门口。",
            "boundaryMarkdown": "演练夹具，不进真实包。",
            "derivationKind": "REVIEWED_SYNTHESIS",
            "sourceId": "$DRILL_SOURCE_ID",
            "sourceLocator": "D-0 演练夹具",
            "reviewedAtEpochMillis": $materialReviewedAtEpochMillis,
            "bindings": [
              {"knowledgeNodeId":"$AUDITED_NODE_ID","role":"PRIMARY","verdict":"KEEP","verdictSource":"MODEL_AUDIT","judgedAtEpochMillis":$JUDGED_AT_EPOCH_MILLIS},
              {"knowledgeNodeId":"$UNAUDITED_NODE_ID","role":"SUPPORTING"}
            ]
          }]
        }
    """.trimIndent()

    private fun readBundledResource(resourceName: String): String = requireNotNull(
        BindingVerdictEndToEndDrillInstrumentedTest::class.java.classLoader
            ?.getResourceAsStream(resourceName),
    ) { "Missing bundled knowledge resource $resourceName" }
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }

    // ---- 直连 SQLite 读回（与既有 KnowledgeContentUpdateDrillInstrumentedTest 同法）----

    private fun readRows(
        context: Context,
        databaseName: String,
        sql: String,
    ): List<Map<String, String?>> = readable(context, databaseName).use { db ->
        db.rawQuery(sql, null).use { cursor ->
            val rows = mutableListOf<Map<String, String?>>()
            while (cursor.moveToNext()) {
                val row = mutableMapOf<String, String?>()
                for (i in 0 until cursor.columnCount) row[cursor.getColumnName(i)] = cursor.getString(i)
                rows += row
            }
            rows
        }
    }

    private fun readable(context: Context, databaseName: String) = SQLiteDatabase.openDatabase(
        context.getDatabasePath(databaseName).path,
        null,
        SQLiteDatabase.OPEN_READONLY,
    )

    private companion object {
        const val PACK_ID = "moe-2025-four-subjects-v1"
        const val PACK_RESOURCE = "knowledge/moe-2025-four-subjects-v1.json"

        /** 随包真实原子点（`数学必修第一册·第三章·函数的概念与性质` 下），直接钉住真实包结构。 */
        const val AUDITED_NODE_ID = "kb:moe-2025-four-subjects-v1:math:atomic:函数的概念"
        const val UNAUDITED_NODE_ID = "kb:moe-2025-four-subjects-v1:math:atomic:函数的定义域"

        /** 审计运行日：`content_audit_2026-09-25.csv` 那一轮（2026-09-25T00:00:00Z）。 */
        const val JUDGED_AT_EPOCH_MILLIS = 1_790_294_400_000L

        /** 合成教学活动来源（真实侧车自带来源的形态）。 */
        const val DRILL_SOURCE_ID = "source:drill:d0-binding-verdict"
        const val DRILL_SOURCE_FINGERPRINT =
            "D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0D0"
        const val DRILL_SOURCE_IMPORTED_AT_EPOCH_MILLIS = 1_784_764_800_000L
    }
}
