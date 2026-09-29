package com.tingyun.smartmistakebook.core.domain

/**
 * 聊天附图的上库接续（A4 的第三条执行路径）。
 *
 * 消灭的具体失败：学生把照片作为消息附件发出去之后，那些图只活在规范资产库里——**没有任何
 * "上库"接续**，图变不成一道待处理的题。学生在确认卡上点"加入错题本"时，这条路径必须真的把
 * 图片送进录入链路（单页 → 一份待处理草稿；多页 → 一个批量导入任务），而不是说一句"已保存"。
 *
 * 走的是既有链路：`CaptureWorkflowRepository.importDraft` / `BatchImportRepository.createBatchImport`
 * ——不新造第二条入库通道（第二条通道就是第二个真相）。
 */
interface TutorAttachedImageIntake {
    /**
     * 把这条消息附带的图片送进录入链路。
     *
     * @return 落成与否 + 给学生看的一句话结果；没落成时 [landed] 必须是 false。
     */
    suspend fun intake(
        assetIds: List<String>,
        occurredAtEpochMillis: Long,
    ): TutorAttachedImageIntakeResult
}

/**
 * 一次"送进录入链路"的结果。
 *
 * [landed] 是"库里真的出现了条目"的断言（草稿行 / 导入任务行），不是"调用没抛异常"：
 * 学生点了同意而本地没做成时，回喂给模型的那一行必须说"没执行"。
 */
data class TutorAttachedImageIntakeResult(
    val landed: Boolean,
    val detail: String,
) {
    init {
        require(detail.isNotBlank()) { "An attached-image intake result needs a detail" }
    }
}
