package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.FigureSchema

/**
 * 图形块的**占高口径**（纯 Kotlin，不初始化 android.graphics）。
 *
 * 为什么单独存在：分页计划器（[MistakePdfPagePlanner]，JVM 可测）必须能算"这张图占多高"
 * 而不碰到 `DeterministicPdfFigureRenderer` 的 Paint 字段——那个对象一被类初始化就会构造
 * `Paint`，在纯 JVM 上直接失败。绘制与该函数共用这里的常量，占高与实际画出的行高不会漂开。
 */
internal object PdfFigureLayout {
    const val CARTESIAN_HEIGHT = 280f
    const val TABLE_ROW_HEIGHT = 18f
    const val FIGURE_VERTICAL_CHROME = 48f

    fun height(block: MistakePdfBlock.Figure): Float = when (val schema = block.schema) {
        is FigureSchema.Cartesian -> CARTESIAN_HEIGHT
        is FigureSchema.SymbolTable ->
            FIGURE_VERTICAL_CHROME + TABLE_ROW_HEIGHT * (schema.rows.size + 1)
        is FigureSchema.Unknown -> error("Unknown figure schemas are rejected before export")
    }
}
