package com.tingyun.smartmistakebook.core.database

import android.util.Log

/**
 * M1a（渲染侧材料覆盖率）的**最小生产可观测点**：材料取数返回后打一行固定字段的结构化日志。
 *
 * **它消灭的失败**：`readBestKnowledgeTeachingMaterialPerNode` 每次返回"每节点最佳一条"，
 * 但生产上没有任何一处记录"这次查询了几个节点、几条材料、哪些节点空着"——材料覆盖率只能靠
 * 离线镜像（`docs/kb-batch23-2026-10-26.md:167`）或从模型可见文案 `（该节点无材料）`
 * 反推（`docs/ops/kb-observability-handoff.md` §2.1 采集点①、§2.2）。文案反推恰好吃不掉
 * 两种必须分开的失败：**节点本就没有绑定**（内容缺口，M1b）与**有绑定但查询没返回**
 * （缺陷，M2 的 `materialQueryEmpty`）。
 *
 * 边界（不越界）：
 * 1. **只记计数与节点 id**：不含题面、摘要、知识点名等学生/内容文本（延续"零新增隐私面"，
 *    `docs/ops/kb-observability-handoff.md` §4 字段契约）；`emptyNodeIds` 只取前
 *    [MAX_EMPTY_NODE_IDS] 个，截断与否可由 `nodesWithoutMaterial` 与列表长度推出。
 * 2. **只是一行日志**：不做采样、不聚合、不上报——"率/分布/跨设备"属指标面
 *    （`docs/ops/kb-observability-handoff.md` §5.1，待立项）。
 * 3. **不改产品结果**：写入侧（[sink]）抛异常被吞掉；日志本身不做任何读取或转换。
 *
 * 归因缺口（登记，不在本文件补）：本层拿不到入口（`KNOWLEDGE_READ` / 预披露 / 复习 /
 * 重教…），入口标签需要调用方传参，落在各入口自己的文件里
 * （`docs/ops/kb-observability-handoff.md` §2.3 边界、§5.1）。
 */
internal object KnowledgeMaterialCoverageTelemetry {

    /** `emptyNodeIds` 的条数上限：覆盖率的分子/分母已给全量，明细只留能用肉眼核对的前几条。 */
    const val MAX_EMPTY_NODE_IDS = 10

    /** 指标日志的 tag：按交接单 §4 的字段契约独立成一个（不复用 `KnowledgeInstall` 等业务 tag）。 */
    internal const val TAG = "kbobs"

    /**
     * 行首固定前缀 + 版本：改字段即改 `v`，采集侧不会静默错位解析
     * （`docs/ops/kb-observability-handoff.md` §4、§3 反模式 1）。
     */
    internal const val LINE_PREFIX = "v=1 metric=material_coverage"

    /**
     * 可注入的捕获口；null（默认）= 写 logcat。测试与调试实现用它替换 logcat 行。
     *
     * 为什么不是"额外再写一份"：JVM 单测里 `android.util.Log` 是抛异常的空壳，若总是先写
     * logcat，捕获路径在 JVM 上根本跑不起来。所以捕获口存在时**替代**logcat 行，
     * 二者语义同一（同一行、同一字段），与 `ModelCallFailureDiagnostics` 的"默认关"不同——
     * 本指标的默认形态是**开**（生产每取一次材料记一行）。
     */
    @Volatile
    var sink: ((String) -> Unit)? = null

    /**
     * 记录一次材料取数的覆盖率。调用点只许放在**取数返回后**：入参就是这次查询的事实，
     * 不做二次读取（不为遥测多打一次库）。
     *
     * @param queriedNodeIds 本次查询请求的节点集（分母）。
     * @param nodeIdsWithMaterial 本次查询**真的返回了材料**的节点 id（分子；与请求集求交，
     *   调用方多传的 id 不计入）。
     * @param returnedMaterials 本次查询返回的材料条数（每节点最佳一条时通常 = 分子）。
     */
    fun record(
        subject: String,
        queriedNodeIds: Collection<String>,
        nodeIdsWithMaterial: Collection<String>,
        returnedMaterials: Int,
    ) {
        val line = formatLine(subject, queriedNodeIds, nodeIdsWithMaterial, returnedMaterials)
        val capture = sink
        if (capture != null) {
            // 捕获是旁路：写入实现出错不得把一次成功的材料读取变成失败。
            runCatching { capture(line) }
            return
        }
        Log.i(TAG, line)
    }

    private fun formatLine(
        subject: String,
        queriedNodeIds: Collection<String>,
        nodeIdsWithMaterial: Collection<String>,
        returnedMaterials: Int,
    ): String {
        require(subject.isNotBlank()) { "subject must not be blank" }
        require(returnedMaterials >= 0) { "returned material count must not be negative" }
        val queried = queriedNodeIds.toSet()
        val withMaterial = nodeIdsWithMaterial.toSet().intersect(queried)
        val emptyNodeIds = (queried - withMaterial).sorted().take(MAX_EMPTY_NODE_IDS)
        return buildString {
            append(LINE_PREFIX)
            append(" subject=").append(subject)
            append(" queriedNodes=").append(queried.size)
            append(" returnedMaterials=").append(returnedMaterials)
            append(" nodesWithMaterial=").append(withMaterial.size)
            append(" nodesWithoutMaterial=").append(queried.size - withMaterial.size)
            append(" emptyNodeIds=[")
            append(emptyNodeIds.joinToString(", "))
            append(']')
        }
    }
}
