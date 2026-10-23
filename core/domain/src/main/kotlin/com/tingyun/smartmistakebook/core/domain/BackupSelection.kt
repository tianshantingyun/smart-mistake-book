package com.tingyun.smartmistakebook.core.domain

/**
 * 备份内容的四个用户类别（6C-2）。位值就是 manifest 的 `categoryMask`：
 * `ISSUE_BOOK=1`、`MASTERY=2`、`SESSIONS=4`、`IMAGES=8`，全选 `15`。
 *
 * 类别到表的映射不在这里：那是 schema 侧的机器清单（`SmbkSelectionPlan`，批次 18 T2）。
 * 本类型只负责用户可见的组合、闭包与 mask。
 */
enum class BackupContentCategory(val bit: Int) {
    /** 错题本：题目/题面修订/练习单元/草稿/标签/关系/题侧治理台账。 */
    ISSUE_BOOK(1),

    /** 掌握库：学习证据账本、投影、复习计划/队列/会话、预测与教学建议。 */
    MASTERY(2),

    /** 会话：讲题会话、消息与消息附图引用。 */
    SESSIONS(4),

    /** 图片：`canonical_source_asset` 行与其 `source-assets/` 字节。 */
    IMAGES(8),

    ;

    companion object {
        /** 四个类别全选。 */
        const val FULL_MASK: Int = 15

        val ALL: Set<BackupContentCategory> = linkedSetOf(
            ISSUE_BOOK,
            MASTERY,
            SESSIONS,
            IMAGES,
        )

        fun maskOf(categories: Iterable<BackupContentCategory>): Int =
            categories.fold(0) { mask, category -> mask or category.bit }
    }
}

/**
 * 类别依赖闭包：子类别 ⇒ 父类别（FK 图折叠后的 5 条跨类别边，schema 69 / closureVersion=2）。
 *
 * - `ISSUE_BOOK ⇒ IMAGES`：题面修订/草稿/草稿页束都引用资产行；
 * - `SESSIONS ⇒ ISSUE_BOOK + IMAGES`：会话暴露结果引用练习单元，消息附图引用资产；
 * - `MASTERY ⇒ SESSIONS`：暴露结果记录引用会话侧事实；传递后 `MASTERY` 等价于全选。
 *
 * 这里是闭包的**唯一实现**；机器清单里的 `expectedClosure` 必须与它逐条一致（T2 测试对拍）。
 */
private val CLOSURE: Map<BackupContentCategory, Set<BackupContentCategory>> = mapOf(
    BackupContentCategory.ISSUE_BOOK to setOf(BackupContentCategory.IMAGES),
    BackupContentCategory.SESSIONS to setOf(
        BackupContentCategory.ISSUE_BOOK,
        BackupContentCategory.IMAGES,
    ),
    BackupContentCategory.MASTERY to setOf(
        BackupContentCategory.ISSUE_BOOK,
        BackupContentCategory.SESSIONS,
    ),
)

/**
 * 一次备份的内容选择（6C-2）。
 *
 * **构造允许不自洽**：界面需要渲染「用户此刻勾了什么、还差什么」的中间态，因此
 * [of] 不抛闭包错误，也不补齐任何类别。真正创建备份前必须调用 [requireCreatable]：
 * 只有闭包自洽（[isClosed]）且 mask ∈ [ALLOWED_MASKS] 的选择才能成包。
 *
 * 产品裁定（批次 18，用户已确认）：
 * 1. 允许的组合只有 [ALLOWED_MASKS]：`9`（题+图）、`13`（题+会话+图）、`15`（全选）；
 * 2. `0`（空选择）与 `8`（仅图片）虽然闭包自洽，但**禁止创建**——前者是重置包，
 *    后者的资产恢复后会被孤儿回收清掉；
 * 3. 其余 mask 未闭合，一律拒绝；**不静默补齐、不静默缩水**。
 */
class BackupSelection private constructor(
    categories: Set<BackupContentCategory>,
) {
    /** 防御性复制：构造后再改传入的可变集合不会改变本实例的 mask 与闭包判定。 */
    val categories: Set<BackupContentCategory> = LinkedHashSet(categories)

    /** 四个类别位的按位或；与 manifest 的 `categoryMask` 同义。 */
    val mask: Int = BackupContentCategory.maskOf(categories)

    val isFull: Boolean = mask == BackupContentCategory.FULL_MASK

    /** 闭包是否自洽；`0` 与 `8` 也是自洽的，是否可创建另见 [isCreatable]。 */
    val isClosed: Boolean = missingDependencies().isEmpty()

    /** `create()` 可接受的选择：闭包自洽且 mask ∈ [ALLOWED_MASKS]。 */
    val isCreatable: Boolean = isClosed && mask in ALLOWED_MASKS

    /**
     * 缺哪条依赖：键是已选类别，值是它**直接**要求的父类别中尚未选中的那些。
     * 空表 = 闭包自洽。界面用它解释某项为什么被点亮；不会拿它做补齐。
     */
    fun missingDependencies(): Map<BackupContentCategory, Set<BackupContentCategory>> {
        val missing = LinkedHashMap<BackupContentCategory, Set<BackupContentCategory>>()
        for (category in categories) {
            val absent = CLOSURE[category].orEmpty()
                .filterTo(LinkedHashSet<BackupContentCategory>()) { it !in categories }
            if (absent.isNotEmpty()) {
                missing[category] = absent
            }
        }
        return missing
    }

    /**
     * 交互传播用：把显式勾选闭合成自洽集合（只增不减）。
     *
     * 这是界面「勾错题本 ⇒ 自动点亮图片」的同一个函数；仓储**不能**用它代替拒绝——
     * `create()` 必须先要求调用方给出闭包自洽的选择（见 [requireCreatable]）。
     */
    fun closed(): BackupSelection {
        val expanded = LinkedHashSet(categories)
        var changed = true
        while (changed) {
            changed = false
            for (category in expanded.toList()) {
                for (dependency in CLOSURE[category].orEmpty()) {
                    if (expanded.add(dependency)) {
                        changed = true
                    }
                }
            }
        }
        return if (expanded == categories) this else of(expanded)
    }

    /**
     * 仓储层闸门：`create()` 在写任何字节前调用它。
     *
     * @throws IllegalArgumentException 闭包不自洽，或 mask 不在产品允许的组合内。
     *   **不会**自动 [closed] 后继续——静默补齐会让用户少要的数据被多带走。
     */
    fun requireCreatable(): BackupSelection {
        require(isClosed) {
            "备份内容选择未闭合：${missingDependencies()}；不会静默补齐"
        }
        require(mask in ALLOWED_MASKS) {
            "备份内容组合 mask=$mask 不被允许；只能选择 9（题目+图片）、" +
                "13（题目+会话+图片）或 15（全选）"
        }
        return this
    }

    fun summary(): BackupSelectionSummary = BackupSelectionSummary(mask = mask, categories = categories)

    override fun equals(other: Any?): Boolean =
        this === other || (other is BackupSelection && categories == other.categories)

    override fun hashCode(): Int = categories.hashCode()

    override fun toString(): String = "BackupSelection(mask=$mask, categories=$categories)"

    companion object {
        /**
         * 允许成包的 mask（用户裁定）：9（题+图）、13（题+会话+图）、15（全选）。
         * `0` 与 `8` 刻意不在其中。
         */
        val ALLOWED_MASKS: Set<Int> = linkedSetOf(9, 13, 15)

        val FULL: BackupSelection = BackupSelection(BackupContentCategory.ALL)

        /** 构造任意组合（可为空、可含 8），供界面渲染中间态；不做闭包补齐。 */
        fun of(categories: Set<BackupContentCategory>): BackupSelection =
            BackupSelection(categories.toSet())
    }
}

/**
 * 回执/manifest 摘要里的选择描述（不含行数等成包细节）。
 *
 * 不变量：`mask` 必须与 `categories` 的位和一致——这正是「如实回执」的最小条件，
 * 避免出现「回执说只含题，实际却带了会话」这种自相矛盾的描述。
 */
data class BackupSelectionSummary(
    val mask: Int,
    val categories: Set<BackupContentCategory>,
) {
    init {
        require(mask in 0..BackupContentCategory.FULL_MASK) {
            "备份内容选择 mask 越界：$mask"
        }
        require(BackupContentCategory.maskOf(categories) == mask) {
            "备份内容选择 mask=$mask 与类别 $categories 不一致"
        }
    }

    val isFull: Boolean get() = mask == BackupContentCategory.FULL_MASK
}
