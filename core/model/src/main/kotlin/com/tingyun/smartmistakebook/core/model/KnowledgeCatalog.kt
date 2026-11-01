package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.Serializable

/** A reviewed knowledge node's identity; names alone are never binding keys. */
@Serializable
data class KnowledgeNodeSelection(val nodeId: String, val taxonomyVersion: String) {
    init {
        require(nodeId.isNotBlank())
        require(taxonomyVersion.isNotBlank())
    }
}

@Serializable
data class CatalogCursor(
    val querySha256: String,
    val catalogRevision: String,
    val lastSortKey: String,
) {
    init {
        require(querySha256.matches(Regex("[a-f0-9]{64}")))
        require(catalogRevision.isNotBlank())
        require(lastSortKey.isNotBlank() && lastSortKey.length <= 4096)
    }
}

data class CatalogQuery(val text: String, val cursor: CatalogCursor? = null, val pageSize: Int = 32) {
    init {
        require(text.length <= 512) { "Catalog query exceeds the input limit" }
        require(pageSize in 1..64) { "Catalog page size is outside its budget" }
    }
}

data class CatalogHit(
    val node: KnowledgeNodeSelection,
    val subject: String,
    val chapterPath: List<String>,
    val name: String,
    val snippet: String,
    val keywords: List<String>,
)

data class CatalogPage(
    val hits: List<CatalogHit>,
    val next: CatalogCursor?,
    val catalogRevision: String,
) {
    init {
        require(catalogRevision.isNotBlank())
        require(next == null || next.catalogRevision == catalogRevision)
        require(hits.map { it.node.nodeId }.distinct().size == hits.size)
    }
}

@Serializable
data class KnowledgeCatalogPosition(val score: Int, val subject: String, val name: String, val nodeId: String) {
    init { require(score >= 0 && subject.isNotBlank() && name.isNotBlank() && nodeId.isNotBlank()) }
}
