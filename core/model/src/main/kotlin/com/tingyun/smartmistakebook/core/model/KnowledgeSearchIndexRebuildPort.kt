package com.tingyun.smartmistakebook.core.model

/**
 * 恢复后派生搜索索引的重建口（6C-2 T5，产品裁定 ③-a+c）。
 *
 * 派生索引（`knowledge_search_feature` / `knowledge_search_index_state`）在**恢复侧恒裁**：
 * 无论备份是 mask 9/13 还是 15、无论旧包是 v1/v2，交换进来的库都不带任何派生行。索引是
 * `knowledge_node`（恒随包）的纯派生缓存，删掉不会丢事实；但它必须在下次数据库打开后
 * **按科重建**，否则首次召回的读时自愈会现场做一次整科重建（S21 记录过 16.6s 级长尾）。
 *
 * 这是一个**窄端口**：core:data 的恢复协调器只依赖这两件事，不依赖
 * `KnowledgeSearchIndexBuilder`（它在 core:database 里是 `internal`）。实现由
 * `RoomStudyDatabase` 提供（`StudyDatabasePort` 继承本接口）。
 *
 * **为什么声明在 core:model 而不是 core:domain**：本仓库的依赖方向契约（
 * `KernelWave0SchemaContractTest` 与 `RoomProjectionArchiveStore` 的 KDoc 已登记）规定
 * `core:database` 只依赖 `core:model`；而实现这个端口的 `StudyDatabasePort` 在
 * `core:database`。把接口放在 core:model 是唯一不破坏该契约的落点。`core:domain` 以
 * 类型别名暴露同一类型（`com.tingyun.smartmistakebook.core.domain.KnowledgeSearchIndexRebuildPort`），
 * 调用方仍可从领域层导入。
 *
 * 默认实现抛 `UnsupportedOperationException` 而不是空实现：一个"声称重建成功"的空实现
 * 会让待重建标记被清掉、索引却仍为空——那正是这个端口要消灭的失败（搜索静默无结果）。
 * 测试替身若不关心重建，应显式覆写并自行断言，而不是继承一个假成功。
 */
interface KnowledgeSearchIndexRebuildPort {
    /**
     * 库里出现过的知识点科目（`knowledge_node.subject` 去重）。
     *
     * 待重建标记里通常已经存了恢复时读到的科目列表；重建协调器会把两边合并
     * （恢复之后、重建之前新导入的科目也要一次建好）。
     */
    suspend fun listKnowledgeSubjects(): List<String> {
        throw UnsupportedOperationException("当前数据库实现未提供知识点科目列表")
    }

    /**
     * 整科重建 [subject] 的搜索特征，并在所有分块提交后推进版本锚点。
     * 实现必须与读时自愈走同一个 `KnowledgeSearchIndexBuilder` 口径（见 core:database）。
     */
    suspend fun rebuildKnowledgeSearchIndex(subject: String) {
        throw UnsupportedOperationException("当前数据库实现未提供搜索索引重建")
    }
}
