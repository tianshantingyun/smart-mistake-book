package com.tingyun.smartmistakebook.feature.tutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository

/**
 * [TutorConversationViewModel] 的手工装配（与 `TutorSessionViewModelFactory` 同一风格）：
 * 依赖由调用方按 app 的服务定位器给进来，不引 Hilt。
 *
 * [initialConversationId] 是**显式恢复**用的会话 id（从历史列表点回来）。为 null 或空白表示
 * "本轮新开"——这是默认路径，也是 A1 的意思：进入智能体栏永远不会认领上一次的会话。
 *
 * [area] 是**会话区**（K1 的判别列）：这条会话属于哪一栏由入口显式给（交互面的配置对象
 * `TutorSurfaceConfig.area` 就是它），不从题面或入口反推。本阶段三个入口都是智能体栏。
 */
internal class TutorConversationViewModelFactory(
    private val conversations: TutorConversationRepository,
    private val modelTasks: ModelTaskRepository,
    private val imageIntake: LobbyMessageImageIntake?,
    private val initialConversationId: String?,
    private val area: String = TutorConversationAreas.AGENT,
    /** 确认卡的落库端口（A4）：null = 这个入口不接确认卡（无库界面与测试替身）。 */
    private val pendingRequests: AgentPendingRequestRepository? = null,
    /** 确认卡三条执行路径的落点；装配处注入。 */
    private val localActionLandings: TutorLocalActionLandings = TutorLocalActionLandings(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        require(modelClass.isAssignableFrom(TutorConversationViewModel::class.java)) {
            "Unknown TutorConversationViewModel class: ${modelClass.name}"
        }
        return TutorConversationViewModel(
            savedStateHandle = extras.createSavedStateHandle(),
            conversations = conversations,
            modelTasks = modelTasks,
            imageIntake = imageIntake,
            initialConversationId = initialConversationId,
            conversationArea = area,
            pendingRequests = pendingRequests,
            localActionLandings = localActionLandings,
        ) as T
    }
}
