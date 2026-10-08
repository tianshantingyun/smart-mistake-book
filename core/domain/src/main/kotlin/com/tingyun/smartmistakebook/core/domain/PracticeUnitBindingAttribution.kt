package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution

/**
 * 一条现役绑定的领域侧事实（KF-32 重派生用）。
 *
 * 为什么不是 `core:database` 的 `PracticeUnitKnowledgeBindingRecord`：`core:domain` 不依赖
 * `core:database`（依赖方向），由调用方（`StudyProjectionDrainer`）在边界处把端口记录映射成
 * 本类型。
 */
data class PracticeUnitBindingFacts(
    val bindingId: String,
    val practiceUnitId: String,
    val knowledgeNodeId: String,
    val taxonomyVersion: String,
)

/**
 * KF-32「按新绑定重放」的**唯一归属派生函数**：把一道题的现役绑定集合派生为证据归因集合。
 *
 * 口径（镜像写入期 `StudyPracticeUnitFacts.attributionSetFor` 的构造，两条路必须逐条同规则，
 * 否则"重放复现写时结果"不再成立）：
 * - taxonomy 取绑定集合里**字典序最小**的版本，只用该版本的绑定（同题多 taxonomy 时不混算）；
 * - 权重**按数量均分**（`1.0 / size`；绑定记录不含 strength，均分即守恒——`DIRECT` 归属总权重
 *   不得超过 1 个单位）；
 * - 归因按 `bindingId` 字典序排列，首个为 `PRIMARY`、其余 `SECONDARY`，certainty 全 `DIRECT`；
 * - `basisRevisionId` 由调用方给（写路径 = 当前 revision；重放 = 该 attempt 快照自己的 revision，
 *   历史归属锚点保留），避免重放把历史事实改写成"今天的问题版本"。
 *
 * 没有任何可用绑定时返回空列表——调用方的空绑定分支按同一条规则兜底（K1 起两侧逐条同规则：
 * 写路径 `StudyPracticeUnitFacts.attributionSetFor` 物化伪绑定后按同规则直接造伪归属；
 * 重放装配侧 `StudyProjectionDrainer` 物化伪绑定**事实**后经本函数派生）。空列表只剩"绑定全被
 * 过滤"的异常形态，由调用方的 `ifEmpty` 退回写时快照。
 *
 * 消费点：`StudyPracticeUnitFacts.attributionSetFor`（写时快照）与
 * `LearningProjector` 的重放重派生（upcast）。**增量 `project()` 不调用**：写时快照在写入瞬间
 * 就是当前绑定，无需重派生。
 */
fun derivePracticeUnitBindingAttributions(
    bindings: List<PracticeUnitBindingFacts>,
    basisRevisionId: String,
): List<KnowledgeEvidenceAttribution> {
    require(basisRevisionId.isNotBlank()) { "Attribution basis revision must not be blank" }
    val usable = bindings.filter { it.bindingId.isNotBlank() && it.knowledgeNodeId.isNotBlank() }
    if (usable.isEmpty()) return emptyList()
    val taxonomyVersion = usable.minOf(PracticeUnitBindingFacts::taxonomyVersion)
    val bindingSet = usable.filter { it.taxonomyVersion == taxonomyVersion }
    val weight = 1.0 / bindingSet.size
    return bindingSet
        .sortedBy(PracticeUnitBindingFacts::bindingId)
        .mapIndexed { index, binding ->
            KnowledgeEvidenceAttribution(
                bindingId = binding.bindingId,
                knowledgeNodeId = binding.knowledgeNodeId,
                weight = weight,
                basisRevisionId = basisRevisionId,
                taxonomyVersion = taxonomyVersion,
                role = if (index == 0) {
                    EvidenceAttributionRole.PRIMARY
                } else {
                    EvidenceAttributionRole.SECONDARY
                },
                certainty = EvidenceAttributionCertainty.DIRECT,
            )
        }
}
