package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.TutorAgentContext
import com.tingyun.smartmistakebook.core.model.TutorBackupReference
import com.tingyun.smartmistakebook.core.model.agentContextSnapshot
import com.tingyun.smartmistakebook.core.model.withAgentContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * 把**应用层**才有的真实上下文折进每一次挂号请求：可恢复备份的授权列表。
 *
 * 工具环自身只认 `TutorAgentContext`；备份引用由用户在系统选择器里授权、存在应用私有
 * prefs，feature 层拿不到。装饰器保证模型看到的 `backupReferences` 永远是本机真实列表，
 * 而不是模型或 feature 层拼出来的身份。
 */
internal class TutorAgentContextRepository(
    private val delegate: ModelTaskRepository,
    private val knownSubjects: suspend () -> List<String>,
    private val backupReferences: suspend () -> List<TutorBackupReference>,
) : ModelTaskRepository by delegate {
    override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flow {
        val input = request.input
        if (input.agentContextSnapshot() == null &&
            input !is com.tingyun.smartmistakebook.core.model.TutorLobbyInput &&
            input !is com.tingyun.smartmistakebook.core.model.TutorPlanInput &&
            input !is com.tingyun.smartmistakebook.core.model.TutorRespondInput
        ) {
            emitAll(delegate.execute(request))
            return@flow
        }
        val base = input.agentContextSnapshot() ?: TutorAgentContext()
        val subjects = (base.subjects + runCatching { knownSubjects() }.getOrDefault(emptyList()))
            .distinct().take(32)
        val context = base.copy(
            subjects = subjects,
            backupReferences = backupReferences(),
        )
        emitAll(delegate.execute(request.copy(input = input.withAgentContext(context))))
    }
}
