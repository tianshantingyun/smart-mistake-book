package com.tingyun.smartmistakebook.core.model

/**
 * 来源标签（S2，`docs/agent-first-refactor-decisions-2026-09-23.md` · D-S）。
 *
 * **语义是交叉参照，不是答案出处**（台账 2026-09-27 补录原话："S2 来源标签语义 = 交叉参照
 * （'此处与课本材料相符'），不是答案出处；文案不得让人误读为'模型从这里拿的答案'"）。
 * 消灭的具体失败：模型正文里出现"与课本一致"这类说法时无从核对——它引用的课本材料可能
 * **本轮根本没有披露给它**，于是学生看到一条无可追溯、也无法证伪的"课本说"。
 *
 * 本地只能核对一件事：**这句话引用的材料，这一轮有没有真的给过模型**。给了 → 标签留下；
 * 没给（模型自己编的标题、或记的是别的轮次/别的会话的材料）→ 整条标签剥掉——剥掉而不是
 * 改成"未经核对"，因为一句被本地标注过的"与课本一致"反而更像背书。
 *
 * 唯一的规范写法是 `（与课本一致：<材料标题>）`。[LEGACY_LABEL_PREFIXES] 里的几种
 * （"来自：""来源：""出自："）**一律剥掉**：它们把交叉参照读成出处，正是台账点名要避免的
 * 误读；模型若用了这几种写法，宁可没有标签，也不能留下读错的标签。
 */
private val CANONICAL_LABEL_PREFIXES = listOf("（与课本一致：", "(与课本一致：")

private val LEGACY_LABEL_PREFIXES = listOf(
    "（来自：",
    "(来自：",
    "（来源：",
    "(来源：",
    "（出自：",
    "(出自：",
)

private val LABEL_SUFFIXES = listOf("）", ")")

/**
 * 按本轮**已披露的材料标题**校验正文里的来源标签，未披露的一律剥掉。
 *
 * 纯函数：不碰任何本地状态，输入只有正文与本轮披露的标题集合——因此它可以在落库前、
 * 渲染前、测试里被同样地调用一次。
 *
 * 规则：
 * 1. 只认 [CANONICAL_LABEL_PREFIXES] 起的标签；标题取到同一行内最近的右括号为止
 *    （标题里本身带括号的，按最后一个右括号收口由调用方保证——本地标题来自知识库，
 *    不含右括号是既有事实）；
 * 2. 标题**逐字相同**（首尾空白与内部空白差异容忍）才保留：本地不做模糊匹配，
 *    模糊匹配等于把"核对"降级成"看起来像"；
 * 3. [LEGACY_LABEL_PREFIXES] 起的标签无论标题是否披露都剥掉（读到的是出处，不是交叉参照）；
 * 4. 剥掉标签后收拾留下的空白：连续空格合并成一个、行尾空格与中文标点前的空格去掉
 *    （把标签整条挖走而不收拾，会留下一句"这句话空格 成立"这样的残迹）。
 */
fun stripUndisclosedSourceLabels(
    markdown: String,
    disclosedMaterialTitles: Collection<String>,
): String {
    if (markdown.isEmpty()) return markdown
    val disclosed = disclosedMaterialTitles.map(::normalizeMaterialTitle).toSet()
    val labels = listOf(CANONICAL_LABEL_PREFIXES, LEGACY_LABEL_PREFIXES)
    var result = markdown
    labels.forEachIndexed { index, prefixes ->
        val keepDisclosed = index == 0
        result = stripLabels(
            markdown = result,
            prefixes = prefixes,
            keep = { title -> keepDisclosed && normalizeMaterialTitle(title) in disclosed },
        )
    }
    return result
}

private fun stripLabels(
    markdown: String,
    prefixes: List<String>,
    keep: (String) -> Boolean,
): String {
    var result = markdown
    prefixes.forEach { prefix ->
        var searchFrom = 0
        while (true) {
            val start = result.indexOf(prefix, startIndex = searchFrom)
            if (start < 0) break
            val titleStart = start + prefix.length
            val end = LABEL_SUFFIXES
                .map { suffix -> result.indexOf(suffix, startIndex = titleStart) }
                .filter { index -> index >= 0 }
                .minOrNull()
            if (end == null) break
            val title = result.substring(titleStart, end)
            // 标签不跨行：跨行的"最近右括号"是别处的括号，不是这条标签的收口。
            if (title.contains('\n')) {
                searchFrom = titleStart
                continue
            }
            if (keep(title)) {
                searchFrom = end + 1
                continue
            }
            val labelEnd = end + 1
            result = (result.removeRange(start, labelEnd)).tidyLabelRemoval()
            searchFrom = start
        }
    }
    return result
}

/** 标题比对前的归一：去掉首尾与内部空白差异（"必修一 · 函数单调性"与"必修一·函数单调性"同一条）。 */
private fun normalizeMaterialTitle(title: String): String =
    title.filterNot { character -> character.isWhitespace() }

/** 剥掉标签后的残迹收拾：合并连续空格、去掉行尾空格与中文标点前的空格、去掉中文之间的空格。 */
private fun String.tidyLabelRemoval(): String = lineSequence()
    .joinToString(separator = "\n") { line ->
        line.replace(Regex(" {2,}"), " ")
            .replace(Regex(" +([，。；：、）！？])"), "$1")
            .trimEnd()
            .dropSpacesBetweenCjk()
    }

/**
 * 中文之间的孤立空格直接删掉：把标签整条挖走之后留下的那个空格在两个汉字之间，读起来是
 * "结论 成立"这样的残迹（中文正文本来就不用词间空格，所以这条收拾是安全的）。
 */
private fun String.dropSpacesBetweenCjk(): String {
    val builder = StringBuilder()
    forEachIndexed { index, character ->
        val keep = character != ' ' ||
            builder.isEmpty() ||
            index + 1 >= length ||
            !isCjk(builder.last()) ||
            !isCjk(this[index + 1])
        if (keep) builder.append(character)
    }
    return builder.toString()
}

private fun isCjk(character: Char): Boolean =
    character.code in 0x3000..0x9FFF || character.code in 0xFF00..0xFFEF
