package com.tingyun.smartmistakebook.core.data.tutor

import com.tingyun.smartmistakebook.core.domain.BatchImportRepository
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureInputSource
import com.tingyun.smartmistakebook.core.domain.CaptureWorkflowRepository
import com.tingyun.smartmistakebook.core.domain.CreateBatchImportRequest
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorAttachedImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorAttachedImageIntakeResult
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聊天附图的"上库接续"实现（A4 的 ③）：把这条消息附带的图片送进**既有**录入链路。
 *
 * 消灭的具体失败：聊天附图进了规范资产库就没有下文——学生在确认卡上点"加入错题本"，此前没有
 * 任何一条路径会把那张图变成题。这里按图片张数走既有链路的两条腿：
 *
 * | 张数 | 链路 | 库里出现什么 |
 * |---|---|---|
 * | 1 | `CaptureWorkflowRepository.importDraft`（单页录入） | 一份待处理草稿（`problem_draft` 行） |
 * | ≥2 | `BatchImportRepository.createBatchImport`（批量录入） | 一个导入任务 + 每页一行 |
 *
 * **为什么 ≥2 才走批量**：`CreateBatchImportRequest` 的契约就是 2..30 页（单张题目走普通录入，
 * 界面文案也这么教学生）——单页硬塞进批量导入会直接抛参数错误，那不是"真落库"，是崩。
 *
 * 幂等：请求标识由"这批资产 id"确定性派生，重复裁决（重放同一张卡）落回同一份草稿/同一个任务。
 */
internal class RoomTutorAttachedImageIntake(
    private val images: LobbyMessageImageIntake,
    private val capture: CaptureWorkflowRepository,
    private val batchImports: BatchImportRepository,
) : TutorAttachedImageIntake {
    override suspend fun intake(
        assetIds: List<String>,
        occurredAtEpochMillis: Long,
    ): TutorAttachedImageIntakeResult = withContext(Dispatchers.IO) {
        val distinct = assetIds.distinct()
        if (distinct.isEmpty()) {
            return@withContext TutorAttachedImageIntakeResult(
                landed = false,
                detail = "这条消息没有可保存的图片。",
            )
        }
        val uris = distinct.mapNotNull { assetId -> images.resolveIntakeUri(assetId) }
        if (uris.isEmpty()) {
            return@withContext TutorAttachedImageIntakeResult(
                landed = false,
                detail = "这些图片已经不在本机了，这次没有存进去。",
            )
        }
        val requestId = "tutor-image-intake:${stableKey(distinct)}"
        if (uris.size == 1) {
            capture.importDraft(
                CaptureDraftImportRequest(
                    requestId = requestId,
                    localUri = uris.single(),
                    source = CaptureInputSource.PHOTO_PICKER,
                    origin = CaptureEntryOrigin.TUTOR,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                ),
            )
            TutorAttachedImageIntakeResult(
                landed = true,
                detail = "已经放进录入，稍后可以在录入界面继续处理这张图。",
            )
        } else {
            batchImports.createBatchImport(
                CreateBatchImportRequest(
                    requestId = requestId,
                    localUris = uris,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                ),
            )
            TutorAttachedImageIntakeResult(
                landed = true,
                detail = "已经加入录入队列（${uris.size} 页）。",
            )
        }
    }

    private fun stableKey(assetIds: List<String>): String = MessageDigest.getInstance("SHA-256")
        .digest(assetIds.sorted().joinToString("\u001F").toByteArray(StandardCharsets.UTF_8))
        .take(12)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

object TutorAttachedImageIntakeFactory {
    fun create(
        images: LobbyMessageImageIntake,
        capture: CaptureWorkflowRepository,
        batchImports: BatchImportRepository,
    ): TutorAttachedImageIntake = RoomTutorAttachedImageIntake(
        images = images,
        capture = capture,
        batchImports = batchImports,
    )
}
