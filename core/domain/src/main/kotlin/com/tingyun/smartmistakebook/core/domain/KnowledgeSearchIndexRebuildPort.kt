package com.tingyun.smartmistakebook.core.domain

/**
 * 恢复后派生搜索索引的重建口（6C-2 T5）——领域层的公开名字。
 *
 * 真实声明在 `core:model`（`com.tingyun.smartmistakebook.core.model.KnowledgeSearchIndexRebuildPort`）。
 * 原因：本仓库的依赖方向契约规定 `core:database` 只依赖 `core:model`，而
 * `StudyDatabasePort` 在 `core:database` 里实现这个端口；接口若留在 core:domain，
 * 就必须给 core:database 增加一条违反契约的依赖。`core:domain` 以类型别名保持
 * 领域层的导入面不变，调用方（core:data 的恢复协调器）仍写
 * `com.tingyun.smartmistakebook.core.domain.KnowledgeSearchIndexRebuildPort`。
 */
typealias KnowledgeSearchIndexRebuildPort = com.tingyun.smartmistakebook.core.model.KnowledgeSearchIndexRebuildPort
