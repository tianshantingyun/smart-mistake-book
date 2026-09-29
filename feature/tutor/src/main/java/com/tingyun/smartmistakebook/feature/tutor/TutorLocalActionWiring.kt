package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.CaptureWorkflowRepository
import com.tingyun.smartmistakebook.core.domain.SaveTutorDraftRequest
import com.tingyun.smartmistakebook.core.domain.SaveTutorDraftToLibraryUseCase
import com.tingyun.smartmistakebook.core.domain.TutorAttachedImageIntake

/**
 * 确认卡三条执行路径的**装配**（A4）：装配处（app）只交真实依赖，路由与调用顺序由这里定。
 *
 * 消灭的具体失败：三条落点此前一条也没有接在卡上——① 只有拍照会话自己那个按钮（真落库，
 * 但没有卡）、② 错题入口的按钮只导航、③ 附图根本没有上库接续。这里把它们接成同一张卡可执行
 * 的三个目标，判定交给 `core:domain` 的 [com.tingyun.smartmistakebook.core.domain.tutorLocalActionTarget]。
 *
 * @param captureRepository 拍照草稿 → 错题本条目（①，既有 `SaveTutorDraftToLibraryUseCase`）。
 * @param attachedImageIntake 聊天附图 → 录入链路（③）。null = 这个入口不接第三条路。
 * @param openNotebook 打开错题本（②）；有具体题时参数是那道题的 id。
 */
fun tutorLocalActionLandings(
    openNotebook: (problemId: String?) -> Unit,
    captureRepository: CaptureWorkflowRepository? = null,
    attachedImageIntake: TutorAttachedImageIntake? = null,
): TutorLocalActionLandings {
    val saveToLibrary = captureRepository?.let(::SaveTutorDraftToLibraryUseCase)
    return TutorLocalActionLandings(
        saveCaptureDraft = { sessionId ->
            val repository = captureRepository
            val useCase = saveToLibrary
            val session = repository?.readTutorSession(sessionId)
            if (useCase == null || session == null) {
                null
            } else {
                useCase(
                    SaveTutorDraftRequest(
                        draftId = session.draftId,
                        sessionId = session.sessionId,
                        occurredAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                if (session.isSaved) "这道题已经在错题本里了。" else "已经加入错题本。"
            }
        },
        openLibraryProblem = openNotebook,
        intakeAttachedImages = { assetIds, occurredAtEpochMillis ->
            attachedImageIntake?.intake(assetIds, occurredAtEpochMillis)
        },
    )
}
