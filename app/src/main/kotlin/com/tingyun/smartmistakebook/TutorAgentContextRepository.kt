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
    private val reviewContext: suspend () -> String? = { null },
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
        val known = try {
            knownSubjects().filter(String::isNotBlank)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val subjects = (base.subjects + known).distinct().takeLast(32)
        val backups = try {
            backupReferences()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 备份目录不可读只应让“恢复备份”动作缺失引用，不应让整轮讲题失败。
            base.backupReferences
        }
        val review = try {
            reviewContext()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            base.reviewContextJson
        }
        val context = base.copy(
            subjects = subjects,
            backupReferences = backups,
            reviewContextJson = review ?: base.reviewContextJson,
        )
        emitAll(delegate.execute(request.copy(input = input.withAgentContext(context))))
    }
}
