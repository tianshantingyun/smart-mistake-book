package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden tests: fixed formulas must produce fixed AST structures and fixed
 * box dimensions (at the default 16px metrics). Any change to parser output
 * or the layout algorithm shows up here as an explicit snapshot diff.
 */
class MathBoxGoldenTest {

    private val delta = 0.001f

    @Test
    fun nativeAdvanceWidthsAlsoApplyToSmallerScripts() {
        val metrics = MathMetrics.DEFAULT.copy(measureTextWidth = { text, size -> text.length * size * .9f })
        val box = MathBoxBuilder.buildBox(MathParser.parse("m^2"), metrics) as MathBox.HBox
        assertEquals(16f * .9f, box.children[0].width, delta)
        assertEquals(16f * .72f * .9f, box.children[1].width, delta)
        assertTrue(requireNotNull(box.childPositions)[1].x >= box.children[0].width)
    }

    @Test
    fun adjacentVariablesStayTogetherAndRelationsHaveSpace() {
        val product = MathBoxBuilder.buildBox(MathParser.parse("at")) as MathBox.HBox
        assertEquals(product.children.sumOf { it.width.toDouble() }.toFloat(), product.width, delta)
        val relation = MathBoxBuilder.buildBox(MathParser.parse("a=t")) as MathBox.HBox
        assertTrue(relation.width > relation.children.sumOf { it.width.toDouble() }.toFloat())
        assertEquals("dx", ReadableMathText.formula("\\,dx\\!"))
    }

    @Test
    fun scriptsHaveSeparatePositionsAndShareTheSameHorizontalSlot() {
        val box = MathBoxBuilder.buildBox(MathParser.parse("x_0^2")) as MathBox.HBox
        val positions = requireNotNull(box.childPositions)
        assertEquals(3, box.children.size)
        assertEquals(positions[1].x, positions[2].x, delta)
        assertTrue(positions[1].y + box.children[1].baseline < positions[0].y + box.children[0].baseline)
        assertTrue(positions[2].y + box.children[2].baseline > positions[0].y + box.children[0].baseline)
        assertTrue(box.children[1].height < box.children[0].height)
        box.children.forEachIndexed { index, child ->
            assertTrue(positions[index].y >= 0f && positions[index].y + child.height <= box.height + delta)
        }
    }

    @Test
    fun fractionLayoutKeepsTheRuleBetweenNumeratorAndDenominator() {
        val box = MathBoxBuilder.buildBox(MathParser.parse("\\frac{x^2}{3}")) as MathBox.FractionBox
        assertTrue(requireNotNull(box.barY) > box.numerator.height)
        assertTrue(requireNotNull(box.denominatorY) > requireNotNull(box.barY))
        assertTrue(requireNotNull(box.denominatorY) + box.denominator.height <= box.height + delta)
    }

    @Test
    fun sizingCommandsAndBoxedAnswersKeepTheMathematicalContent() {
        val text = ReadableMathText.formula("\\boxed{\\left[x^2\\right]_0^2}")
        assertTrue(text.contains("x"))
        assertTrue(!text.contains("left") && !text.contains("right") && !text.contains("boxed"))
    }

    // ---- AST goldens -------------------------------------------------------

    @Test
    fun `golden ast - fraction`() {
        assertEquals(
            MathNode.Fraction(MathNode.Atom("1"), MathNode.Atom("2")),
            MathParser.parse("\\frac{1}{2}"),
        )
    }

    @Test
    fun `golden ast - radical`() {
        assertEquals(
            MathNode.Radical(MathNode.Atom("x")),
            MathParser.parse("\\sqrt{x}"),
        )
    }

    @Test
    fun `golden ast - pmatrix`() {
        assertEquals(
            MathNode.Matrix(
                rows = listOf(
                    listOf(MathNode.Atom("a"), MathNode.Atom("b")),
                    listOf(MathNode.Atom("c"), MathNode.Atom("d")),
                ),
                leftDelimiter = "(",
                rightDelimiter = ")",
            ),
            MathParser.parse("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}"),
        )
    }

    @Test
    fun `golden ast - cases`() {
        assertEquals(
            MathNode.Cases(listOf(MathNode.Atom("a"), MathNode.Atom("b"))),
            MathParser.parse("\\begin{cases} a \\\\ b \\end{cases}"),
        )
    }

    @Test
    fun `golden ast - superscript`() {
        assertEquals(
            MathNode.Superscript(MathNode.Atom("x"), MathNode.Atom("2")),
            MathParser.parse("x^2"),
        )
    }

    // ---- Box dimension goldens (DEFAULT metrics: 16px font) ---------------

    @Test
    fun `golden box - atom`() {
        val box = MathBoxBuilder.buildBox(MathParser.parse("x"))
        assertTrue(box is MathBox.Leaf)
        assertEquals(8f, box.width, delta)
        assertEquals(16f, box.height, delta)
        assertEquals(12.8f, box.baseline, delta)
        assertEquals("x", (box as MathBox.Leaf).content)
    }

    @Test
    fun `golden box - fraction`() {
        val box = MathBoxBuilder.buildBox(MathParser.parse("\\frac{1}{2}"))
        assertTrue(box is MathBox.FractionBox)
        // width = max(8,8) + 2*8; height = 16+16+4+1; baseline = 16+2+0.5+16*0.3
        assertEquals(24f, box.width, delta)
        assertEquals(37f, box.height, delta)
        assertEquals(23.3f, box.baseline, delta)
    }

    @Test
    fun `golden box - radical`() {
        val box = MathBoxBuilder.buildBox(MathParser.parse("\\sqrt{x}"))
        assertTrue(box is MathBox.RadicalBox)
        // width = 8 + 2*8; height = 16 + 4; baseline = 12.8 + 4
        assertEquals(24f, box.width, delta)
        assertEquals(20f, box.height, delta)
        assertEquals(16.8f, box.baseline, delta)
    }

    @Test
    fun `golden box - matrix grid`() {
        val box = MathBoxBuilder.buildBox(
            MathParser.parse("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}"),
        )
        assertTrue(box is MathBox.MatrixBox)
        val matrix = box as MathBox.MatrixBox
        assertEquals(2, matrix.rows.size)
        assertEquals(2, matrix.rows[0].size)
        // width = 8+8 (columns) + 8 (gap) + 16 (delimiters)
        assertEquals(40f, matrix.width, delta)
        // height = 16*2 + 4 spacing
        assertEquals(36f, matrix.height, delta)
        assertEquals(18f, matrix.baseline, delta)
    }

    @Test
    fun `golden box - cases`() {
        val box = MathBoxBuilder.buildBox(
            MathParser.parse("\\begin{cases} a \\\\ b \\end{cases}"),
        )
        assertTrue(box is MathBox.CasesBox)
        val cases = box as MathBox.CasesBox
        assertEquals(2, cases.rows.size)
        assertEquals(4f, cases.rowGap, delta)
        // width = brace(12) + row(8) + padding(8)
        assertEquals(28f, cases.width, delta)
        // height = 16 + 16 + 4 spacing
        assertEquals(36f, cases.height, delta)
        assertEquals(18f, cases.baseline, delta)
    }

    @Test
    fun `golden box - aligned rows`() {
        val box = MathBoxBuilder.buildBox(
            MathParser.parse("\\begin{aligned} a \\\\ b \\end{aligned}"),
        )
        assertTrue(box is MathBox.AlignedBox)
        val aligned = box as MathBox.AlignedBox
        assertEquals(2, aligned.rows.size)
        assertEquals(8f, aligned.width, delta)
        assertEquals(36f, aligned.height, delta)
    }

    // ---- Rendered-text goldens ---------------------------------------------

    @Test
    fun `golden render - fraction`() {
        assertEquals("(1)/(2)", ReadableMathText.formula("\\frac{1}{2}"))
    }

    @Test
    fun `golden render - radical`() {
        assertEquals("√(x)", ReadableMathText.formula("\\sqrt{x}"))
    }

    @Test
    fun `golden render - superscript`() {
        assertEquals("x²", ReadableMathText.formula("x^2"))
    }

    @Test
    fun `golden render - cases`() {
        assertEquals(
            "{ 1; 0",
            ReadableMathText.formula("\\begin{cases} 1 \\\\ 0 \\end{cases}"),
        )
    }

    // ---- Pipeline goldens ----------------------------------------------------

    @Test
    fun `golden pipeline - quadratic formula parses within budget`() {
        val result = parseAndBuildBox("\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}")
        assertTrue(result.isSuccess)
        assertTrue(result.node is MathNode.Fraction)
        assertTrue(result.nodeCount <= MathBudget.MAX_AST_NODE_COUNT)
        assertTrue(result.depth <= MathBudget.MAX_AST_DEPTH)
    }
}
