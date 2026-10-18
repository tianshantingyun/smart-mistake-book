package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeExportRecord
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat

/**
 * 一条导出记录的交付格式：按 [MistakeExportRecord.displayName] 的扩展名判
 * （见 [MistakeExportFormat.ofDeliveryName]）。
 *
 * 它消灭的失败：成果入口的三个交付动作（保存/分享/打印）必须按格式分流，而记录表在本批
 * **零 schema**（不加格式列）。判据收在这一个函数里——记录写入侧（worker 用带扩展名的
 * 文件名）与读取侧（入口分流）不会各写一份 `endsWith`。
 */
fun MistakeExportRecord.deliveryFormat(): MistakeExportFormat =
    MistakeExportFormat.ofDeliveryName(displayName)
