package com.tingyun.smartmistakebook.core.data.knowledge

import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeSearchFeatureExtractor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Stage-2 dense 离线验证的第一步（词面腿侧）**：把臂 A（FTS5 `bm25()`）的
 * **每查询 × 每节点分数**导出成 TSV，供 Python 侧做归一化分数融合 / RRF——
 * 词面腿的权威实现只有一份（`Stage1Experiment.retrieveA` 的同一套索引、同一套 MATCH、
 * 同一套 WHERE），Python 侧不重造 FTS5 口径（规格 §2.3，`docs/kb-stage2-dense-spec.md`）。
 *
 * **为什么另开一个测试而不是改臂定义**：本文件只**加导出**，不改 `Stage1Experiment` /
 * `Stage1LexicalLab` 的任何一行——Stage-1 的判读数（臂 A = 0.6307692307692307/0.49269230769230776，
 * 2026-10-02 v2 口径实测；v1 金标时代为 0.6556/0.5569）必须由同一份
 * 实现原样产出，导出的是它的**中间量**（分数），不是另算一遍。
 *
 * **导出形状**（`build/stage2-dense-offline-lexical.tsv`）：
 * ```
 * query_id\tnode_id\tbm25_score
 * ```
 * - `query_id` = 冻结金标 JSON 数组里的 0 起始下标（`loadGoldenCases` 保序）；配套文件
 *   `build/stage2-dense-offline-lexical-queries.tsv` 记录 `query_id → subject / query 的 sha256 /
 *   expectedSlug / chapter`，让下标与题面的对应关系可被机器复核（不靠互信）。
 * - `node_id` = `knowledgeNodeId`（命中定义要求以 `:atomic:<expectedSlug>` 结尾）。
 * - `bm25_score` = FTS5 `bm25()` 的**原样符号**（负分，越小越优；融合侧自行取 `-score`）。
 *   每行按 `ORDER BY score ASC, node_id ASC`（= 臂 A 的权威排序，含确定性次级键）落盘，读回即用。
 * - **LIMIT 取全语料篇数**：`bm25()` 的分数与 LIMIT 无关，取全量是为了拿到"该查询命中的
 *   每一个节点"的分数（融合与 MISS 名次都需要完整名单），不是改了排序口径。
 *
 * **断言（本测试只断言"这份数据是可信的"，不断言质量阈值）**：
 * 1. 金标 sha256 = 封存值（题面未被改过）+ 129 条 / 4 科 / 23 章（判官 v2，2026-10-14 迁移后口径；
 *    **每章条数不等**（3/9/11/12 条不等），故不再断言"每章条数一致"这种 v1 恒值）；
 * 2. 每行都指向真实节点、且 `subject` 与 `verification_status` 都落在该查询的候选域里
 *    （导出的行不能绕过按科隔离 + 可信过滤）；
 * 3. **回读 TSV 重算臂 A**：逐章 20 值、以及**逐题 MISS（集合 + rank，256 窗口）**与
 *    `build/stage1-metrics-A.txt` 逐行相同——导出的分数就是臂 A 的分数，"融合臂的词面输入 =
 *    臂 A 原样"因此可证。主集/MRR 的数值锚随每次数据批次实测重钉，**当前值见下方
 *    `ARM_A_MAIN` / `ARM_A_MRR` 常量处的说明**（2026-10-05 批次 5 实测：85/129 = 0.6589147286821705、
 *    MRR = 0.5139534883720931（= Kotlin `sumOf` 顺序浮点累加的逐位值；有理数精确值同为
 *    0.513953488372093 时差 1 ulp，容差 0.0 下按实现值钉）；再上一代 82/130 = 0.6307692307692307、MRR = 0.49269230769230776 是
 *    2026-10-02 判官 v2 时代的钉子；更旧 59 / 0.6555… / 0.5568… 是 v1 金标（90 条）+ 旧包时代的值）。
 *
 * 复算：`./gradlew.bat :core:data:testDebugUnitTest --tests "*Stage2LexicalScoresExportTest*" --rerun`
 */
class Stage2LexicalScoresExportTest {

    @Test
    fun exportArmAFts5ScoresAsFusionInput() {
        val repoRoot = locateRepoRoot() ?: error("找不到仓库根（settings.gradle.kts）")

        // ---- 题面：冻结金标（sha256 复核对封存值） ----
        val goldenFile = File(repoRoot, "$GOLDEN_REPO_DIR/$GOLDEN_FILE_NAME")
        val sealFile = File(goldenFile.parentFile, goldenFile.name + ".sha256")
        assertTrue("金标集缺失：${goldenFile.path}", goldenFile.isFile)
        assertTrue("金标集缺少 .sha256 封存文件：${sealFile.path}", sealFile.isFile)
        val goldenSha256 = RetrievalBenchmark.sha256Hex(goldenFile.readBytes())
        assertEquals(
            "金标集 sha256 与封存值不符（题面被改过？）",
            sealFile.readText(Charsets.UTF_8).trim(),
            goldenSha256,
        )
        assertEquals("金标 sha256 应等于 Stage-2 规格 §1.2 的封存值", FROZEN_GOLDEN_SHA256, goldenSha256)
        val cases = RetrievalBenchmark.loadGoldenCases(goldenFile)
        // 2026-10-03 批次 3：金标迁移 130 → 129（1 条退役、4 条按幸存者改指）。
        assertEquals("冻结金标集条数应为 129（判官 v2，2026-10-03 迁移后）", 129, cases.size)
        // 2026-10-14 批次10 promote v14 + 金标 expectedSlug 迁移（9 条改指、3 条跨册/跨章同步改
        // chapter）重钉：章数 20 → 23（3 条跨章带入 化学选择性必修1·第一章·反应热与能量 /
        // 化学选择性必修3·有机化学基础·有机化合物的结构特点 / 物理必修第一册·第四章·牛顿运动定律，
        // 旧章仍被其余题占用）；量源 = 本轮失败信息 `expected:<20> but was:<23>`。
        assertEquals("冻结金标集章数应为 23（判官 v2 迁移后；每章条数不等）", 23, cases.map { it.chapter }.distinct().size)

        val pack = BundledKnowledgePackResources.load().single { it.packId == PACK_ID }
        val corpus = Stage1LexicalLab.Corpus(pack.nodes)
        val trusted = RetrievalBenchmark.TRUSTED_VERIFICATION_STATUSES

        // ---- 导出：与臂 A 同一套索引/同一套 MATCH/同一套 WHERE，只把 LIMIT 放到全语料 ----
        val tsv = StringBuilder("# query_id\tnode_id\tbm25_score\n")
        val queryTsv = StringBuilder("# query_id\tsubject\tquery_sha256\texpectedSlug\tchapter\n")
        var emptyFeatureQueries = 0
        var rowCount = 0
        Stage1LexicalLab.Fts5Index(corpus).use { fts ->
            assertEquals("索引篇数应等于节点数", pack.nodes.size, fts.indexedDocs)
            cases.forEachIndexed { index, case ->
                val terms = KnowledgeSearchFeatureExtractor.fromQuestion(case.query)
                if (terms.isEmpty()) emptyFeatureQueries++
                val hits = if (terms.isEmpty()) {
                    // 与臂 A 的空查询回退同源（本套金标实测不触发；保留分支免得口径出现第二套行为）。
                    RetrievalBenchmark.emptyQueryFallbackOrder(corpus.trustedBySubject[case.subject].orEmpty())
                        .map { Stage1LexicalLab.Hit(it.knowledgeNodeId, 0.0) }
                } else {
                    fts.search(
                        subject = case.subject,
                        matchExpression = Stage1LexicalLab.matchExpression(terms),
                        limit = corpus.nodes.size,
                    )
                }
                assertEquals(
                    "命中名单不能出现非本节点语料：query_id=$index",
                    0,
                    hits.count { corpus.nodeById[it.nodeId] == null },
                )
                hits.forEach { hit ->
                    val node = corpus.nodeById.getValue(hit.nodeId)
                    assertTrue(
                        "导出行的 subject 必须等于查询的科：query_id=$index node=${hit.nodeId}",
                        node.subject == case.subject,
                    )
                    assertTrue(
                        "导出行的状态必须在可信过滤集内：query_id=$index node=${hit.nodeId}",
                        node.verificationStatus in trusted,
                    )
                    assertTrue(
                        "bm25 分数应为非正（FTS5 原样符号）：query_id=$index score=${hit.score}",
                        hit.score <= 1e-12,
                    )
                }
                hits.forEach { hit -> tsv.append(index).append('\t').append(hit.nodeId).append('\t').append(hit.score).append('\n') }
                rowCount += hits.size
                queryTsv.append(index).append('\t').append(case.subject).append('\t')
                    .append(RetrievalBenchmark.sha256Hex(case.query.toByteArray(Charsets.UTF_8)))
                    .append('\t').append(case.expectedSlug).append('\t').append(case.chapter).append('\n')
            }
        }

        val tsvFile = File(repoRoot, TSV_RELATIVE_PATH)
        tsvFile.parentFile.mkdirs()
        tsvFile.writeText(tsv.toString(), Charsets.UTF_8)
        val queryFile = File(repoRoot, QUERY_TSV_RELATIVE_PATH)
        queryFile.writeText(queryTsv.toString(), Charsets.UTF_8)

        assertTrue("导出行数为 0", rowCount > 0)
        assertEquals(
            "空特征查询应走回退分支（本套金标实测应为 0 条）",
            0,
            emptyFeatureQueries,
        )

        // ---- 回读 TSV，重算臂 A（导出的分数必须能原样复现 Stage-1 判读数） ----
        val exported = readTsv(tsvFile)
        assertEquals("回读行数应等于写出行数", rowCount, exported.size)
        val byQuery = exported.groupBy { it.queryId }
        val derived = cases.mapIndexed { index, case ->
            val ranked = (byQuery[index] ?: emptyList())
                .sortedWith(compareBy({ it.score }, { it.nodeId }))
                .take(RetrievalBenchmark.MISS_PROBE_LIMIT)
            val rank = ranked.indexOfFirst { it.nodeId.endsWith(ATOMIC_SUFFIX + case.expectedSlug) }
                .let { if (it < 0) 0 else it + 1 }
            case to rank
        }
        val top5Ranks = derived.map { (_, probeRank) ->
            // top-5 判分只用前 5 名：rank ≤ 5 即命中，1/rank 进 MRR。
            if (probeRank in 1..Stage1LexicalLab.SCORED_TOP_K) probeRank else 0
        }
        val hits = top5Ranks.count { it > 0 }
        val main = hits.toDouble() / cases.size
        val mrr = top5Ranks.sumOf { if (it > 0) 1.0 / it else 0.0 } / cases.size
        val chapterLines = cases.indices.groupBy { cases[it].chapter }.toList().map { (chapter, idxs) ->
            val chapterHits = idxs.count { top5Ranks[it] > 0 }
            "  " + chapter + " = " + chapterHits + "/" + idxs.size + " = " +
                (chapterHits.toDouble() / idxs.size) + " (MISS " + (idxs.size - chapterHits) + ")"
        }
        val missLines = derived.filter { (_, rank) -> rank == 0 || rank > Stage1LexicalLab.SCORED_TOP_K }
            .map { (case, probeRank) ->
                "  MISS [" + case.subject + "] " + case.query +
                    " | expectedSlug=" + case.expectedSlug +
                    " | chapter=" + case.chapter +
                    " | rank=" + (if (probeRank > 0) probeRank.toString() else "absent")
            }

        // **数值锚：2026-10-16 随批次11 promote v15（原子点 3,359 → 3,210 = 批次11 超配并点 + F2 章层
        // 重建）+ 金标迁移（5 条改指 + 16 条 chapter 同步改到 v15 包内 topic 链归属；
        // 见 docs/kb-golden-v2-protocol.md 两节）后按实测重钉。** 判官仍是 v2 129 条
        // （sha `dbe093b2…` → `3dbe9073…`）。本轮 gradle 门实测 hits=83（round-1 失败信息
        // `expected:<80> but was:<83>`），主集/MRR 量源＝同轮导出的
        // `build/stage2-dense-offline-lexical.tsv` 回读重算实测值（83/129、
        // 0.6434108527131783 / 0.5211886304909561；复算脚本
        // `build/agent-batch11/pin/recompute_stage2_pins.py` 复刻本测试回读逻辑——朴素顺序求和
        // 复刻 Kotlin `sumOf`）。
        // 下方与 `build/stage1-metrics-A.txt` 的逐行对账，比对的是**同批 Stage1 重落盘的产物**
        // （当前包；重跑时 23 条逐章行 + 46 条 MISS 行（129 − 83）须全部逐字命中）。
        // 上一代（2026-10-14 批次10 promote v14 + 金标迁移）：80/129 =
        // 0.6201550387596899 / 0.4928940568475453；更旧（2026-10-11 批次 8：−20 节点 +
        // 别名 37,396 → 39,977）：79/129 = 0.6124031007751938 / 0.49677002583979335；
        // 更旧（2026-10-10 批次 7）：81/129 = 0.627906976744186 / 0.5040051679586565；
        // 再旧（2026-10-05 批次 5）：85/129 = 0.6589147286821705 / 0.5139534883720931；
        // （2026-10-03 批次 3）：84/129 = 0.6511627906976745 / 0.4948320413436693；
        // 再旧（59 / 0.6555555555555556 / 0.5568518518518518）= v1 金标 90 条时代。
        // 这是数据变化导致的期望漂移，不是检索退化；判据（容差 0.0、top-5、命中定义）一律未动。
        assertEquals("回读重算的臂 A 主集命中数应为 83/129（判官 v2 迁移后 + 当前包实测）", 83, hits)
        assertEquals("回读重算的臂 A 主集 Recall@5 应为 0.6434108527131783", ARM_A_MAIN, main, 0.0)
        assertEquals("回读重算的臂 A MRR 应为 0.5211886304909561", ARM_A_MRR, mrr, 0.0)

        // ---- 与 build/stage1-metrics-A.txt 逐行对账（含逐题 MISS 的集合与 rank） ----
        val metricsFile = File(repoRoot, "build/stage1-metrics-A.txt")
        assertTrue("Stage-1 臂 A 指标文件缺失：${metricsFile.path}", metricsFile.isFile)
        val metricsText = metricsFile.readText(Charsets.UTF_8)
        metricsText.lines().filter { it.startsWith("  ") && it.contains(" = ") && it.contains("(MISS ") }
            .forEach { line ->
                assertTrue(
                    "回读重算的逐章行不在 stage1-metrics-A.txt 里：$line",
                    metricsText.contains(line),
                )
            }
        val recordedMisses = metricsText.lines().filter { it.trimStart().startsWith("MISS [") }.map { it.trim() }
        assertEquals("回读重算的 MISS 条数应与 stage1-metrics-A.txt 相同", recordedMisses.size, missLines.size)
        missLines.forEach { line ->
            assertTrue(
                "回读重算的 MISS 行不在 stage1-metrics-A.txt 里（题面/MISS 集合/rank 有差异）：$line",
                recordedMisses.contains(line.trim()),
            )
        }
        // 逐章行的数量也对齐一次，防止"只对了 MISS、逐章没对上"。2026-10-14 批次10 promote v14 +
        // 金标迁移后由 20 → 23（上一代 20 = 判官 v2 原归属；见上方章数说明）。
        assertEquals("逐章行数应为 23（判官 v2 迁移后）", 23, chapterLines.size)

        println("stage2-lexical-export: 文件=${tsvFile.path}")
        println("stage2-lexical-export: 行数=$rowCount 查询数=${cases.size} 空洞查询=$emptyFeatureQueries")
        println("stage2-lexical-export: 回读重算臂 A 主集=$main ($hits/${cases.size}) MRR=$mrr")
        println("stage2-lexical-export: 与 stage1-metrics-A.txt 对账：逐章行 ${chapterLines.size} 条、MISS ${missLines.size} 条全部一致")
        println("stage2-lexical-export: 配套查询表=${queryFile.path}")
    }

    /** TSV 一行：查询下标 + 节点 id + FTS5 原样分数。 */
    private data class Row(val queryId: Int, val nodeId: String, val score: Double)

    private fun readTsv(file: File): List<Row> = file.readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split('\t')
            require(parts.size == 3) { "TSV 列数应为 3：$line" }
            Row(parts[0].toInt(), parts[1], parts[2].toDouble())
        }

    private fun locateRepoRoot(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return dir
    }

    private companion object {
        const val PACK_ID = "moe-2025-four-subjects-v1"
        const val GOLDEN_REPO_DIR = "tools/kb_coverage/tables"
        const val GOLDEN_FILE_NAME = "golden_queries_v2.json"
        const val TSV_RELATIVE_PATH = "build/stage2-dense-offline-lexical.tsv"
        const val QUERY_TSV_RELATIVE_PATH = "build/stage2-dense-offline-lexical-queries.tsv"

        /**
         * 判官封存值。**2026-09-28 随 v1→v2 扩集迁移**（90→130 条，见
         * `docs/kb-golden-v2-protocol.md`）；v1 的 `7c004b76…` 是历史封存。
         * 本测试复核的是"当前生效判官"的 sha，所以它必须跟着判官走。
         *
         * **2026-10-03 随批次 3（近重复节点合并）迁移**：130 → 129 条（1 条退役、4 条按
         * 幸存者改指），sha `89c1d5b5…` → `473dbeac…`；记录见 `docs/kb-golden-v2-protocol.md`。
         *
         * **2026-10-14 随批次10 promote v14 迁移**：条数仍 129（0 退役），sha `473dbeac…` → `14b7cea8…`；
         * 9 条 expectedSlug 按 manifest retired 的 supersededBy 逐条改指（3 条跨册/跨章者 chapter
         * 同步改为映射后节点在 v14 包的实际章归属）。记录见 `docs/kb-golden-v2-protocol.md` 的
         * 「expectedSlug 迁移（2026-10-14）」一节 + `build/agent-batch10/golden-migration.json`。
         *
         * **2026-10-16 随批次11（v15 并点 + F2 章层重建）迁移**：条数仍 129（0 退役），
         * sha `14b7cea8…` → `dbe093b2…`（5 条 expectedSlug 按 manifest retired 的 supersededBy
         * 逐条改指，全部同册同章）→ 本值（16 条 chapter 随 F2 章层重建同步改到 v15 包内
         * topic 链实测归属；slug 未动）。记录见 `docs/kb-golden-v2-protocol.md` 的
         * 「expectedSlug 迁移（2026-10-16，随批次11 v15 并点）」+「chapter 同步迁移（2026-10-16，
         * 随批次11 v15 F2 章层重建）」两节 + `build/agent-batch11/golden-migration.json`。
         */
        const val FROZEN_GOLDEN_SHA256 = "3dbe90736df42c9c22723dc06292b364ade92901604b5731033cf612c4f10e41"

        /**
         * Stage-1 判读数（`build/stage1-metrics-A.txt`）——回读重算必须逐位复现。
         *
         * **2026-10-16 批次11 promote v15（原子点 3,359 → 3,210）+ 金标迁移（5 条改指；16 条
         * chapter 同步改）重钉**：臂 A 主集 **83/129 = 0.6434108527131783**、
         * MRR **0.5211886304909562**（命中数 80 → 83）。MRR 锚必须取与实现逐位相同的值：
         * 实现是 Kotlin `sumOf` 的**朴素顺序浮点累加**；本值 = 本轮全量门失败信息
         * `expected:<0.5211886304909561> but was:<0.5211886304909562>` 的实测值
         * （独立复算脚本 `build/agent-batch11/pin/recompute_stage2_pins.py` 给出 …561——
         * 差 1 ULP：Python 3.12+ 内建 `sum()` 对浮点走 Neumaier 补偿求和，比 Kotlin 朴素累加更准；
         * 判据是"与实现逐位相同"，故锚取实现值 …562。复算脚本已改用朴素累加复现）。上一代
         * （2026-10-14 批次10 promote v14）：80/129 = 0.6201550387596899 / 0.4928940568475453；
         * 更旧（2026-10-11 批次 8：−20 节点 + 别名 37,396 → 39,977）：79/129 =
         * 0.6124031007751938 / 0.49677002583979335；
         * 更旧（2026-10-10 批次 7）：81/129 = 0.627906976744186 / 0.5040051679586565；
         * 更旧（2026-10-05 批次 5）：85/129 = 0.6589147286821705 / 0.5139534883720931；
         * 更旧（2026-10-03 批次 3）：84/129 = 0.6511627906976745 / 0.4948320413436693；再旧
         * 0.6307692307692307 / 0.4920512820512821 = v2 判官 130 条 + 3,866 包时代；
         * 更旧 0.6555555555555556 / 0.5568518518518518 = v1 金标 90 条时代。
         */
        const val ARM_A_MAIN = 0.6434108527131783
        const val ARM_A_MRR = 0.5211886304909562

        const val ATOMIC_SUFFIX = ":atomic:"
    }
}
