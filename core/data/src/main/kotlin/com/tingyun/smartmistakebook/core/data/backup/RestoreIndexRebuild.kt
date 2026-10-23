package com.tingyun.smartmistakebook.core.data.backup

import android.content.Context
import android.util.Log
import com.tingyun.smartmistakebook.core.domain.KnowledgeSearchIndexRebuildPort
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 恢复后派生搜索索引的「待重建」事实（6C-2 T5，产品裁定 ③-a+c）。
 *
 * 恢复侧**恒裁**两张派生索引表（`knowledge_search_feature` /
 * `knowledge_search_index_state`），所以交换完成后库里的搜索索引一定是空的。这份事实必须
 * 跨进程存活到**下一次数据库打开**之后——恢复本身已经关掉了 room3 连接池，本进程不能再
 * 打开库重建。
 *
 * 为什么独立成文件而不是塞进 [RestoreJournal]：journal 的语义是"一次恢复的进行态"，
 * 成功路径会在 Phase 7 清掉它（启动恢复对 `COMPLETED` 也只做清理）。把待重建事实放进去，
 * 要么成功时丢失，要么得让一条已完成的 journal 永久留在 `noBackupFilesDir` 里冒充"中断的
 * 恢复"。独立标记与恢复生命周期解耦：交换提交后单独落盘；标记写失败只告警，读时自愈兜底。
 *
 * 标记只记录"哪些科要重建"；权威的索引内容始终由库里 `knowledge_node` 重新派生。
 */
internal object RestoreIndexRebuildMarker {

    /** 标记文件名；放在 `noBackupFilesDir`（与恢复 journal 同一目录，但不参与 journal 扫描）。 */
    internal const val FILE_NAME = "restore-index-rebuild-pending.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun file(context: Context): File = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).isFile

    /** 原子写：临时文件 + fsync + rename；失败抛出让调用方决定是否只是告警。 */
    fun write(context: Context, pending: PendingRestoreIndexRebuild) {
        val target = file(context)
        val parent = target.parentFile ?: return
        check(parent.isDirectory || parent.mkdirs()) { "无法创建待重建标记目录" }
        val text = json.encodeToString(PendingRestoreIndexRebuild.serializer(), pending)
        val temp = File(parent, "$FILE_NAME.tmp")
        temp.outputStream().use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
            runCatching { output.fd.sync() }
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("无法写入待重建标记")
        }
        fsyncDir(parent)
    }

    /**
     * 读不出/读不懂都返回 null。调用方必须先看 [exists]：**标记存在但解析失败**
     * 不能被当成"没有待重建"（否则空索引会永远不再重建），协调器会把这种情况
     * 当成"科目未知的待重建"，回落到库内科目查询。
     */
    fun read(context: Context): PendingRestoreIndexRebuild? {
        val target = file(context)
        if (!target.isFile) return null
        return try {
            json.decodeFromString(PendingRestoreIndexRebuild.serializer(), target.readText())
        } catch (failure: Exception) {
            Log.w(TAG, "待重建标记无法解析，将在下次重建时按库内科目兜底：${failure.message}")
            null
        }
    }

    fun clear(context: Context) {
        val target = file(context)
        if (target.exists() && !target.delete()) {
            Log.w(TAG, "待重建标记删除失败：${target.name}")
        }
        fsyncDir(target.parentFile)
    }

    private const val TAG = "RestoreIndexRebuild"
}

/**
 * 待重建标记的载荷。
 *
 * [subjects] 是恢复时从**包内库**读到的科目列表（`knowledge_node.subject` 去重）；它可能
 * 为空（那份备份没有知识点），此时重建协调器回落到
 * [KnowledgeSearchIndexRebuildPort.listKnowledgeSubjects]。
 *
 * 刻意**不**记录 `selectionMask` 以外的成包细节：重建只关心"库里有哪些科"，不关心这次包
 * 带了哪几类内容——`knowledge_node` 恒随包，科目集合才是唯一输入。
 */
@Serializable
internal data class PendingRestoreIndexRebuild(
    /** 标记形状版本；将来扩展字段时递增。 */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** 写下这条标记的恢复 id（诊断/日志用）。 */
    val restoreId: String,
    val createdAtEpochMillis: Long,
    /** 这次恢复的内容选择 mask（诊断用；v1/v2 全量包为 null）。 */
    val selectionMask: Int? = null,
    /** 待重建的科目；空 = 恢复时没有知识点，协调器按库内科目兜底。 */
    val subjects: List<String> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/** 待重建处理的最终状态。 */
enum class RestoreIndexRebuildStatus {
    /** 没有待重建标记（没有刚恢复过的库，或已经重建完成）。 */
    NOTHING_PENDING,

    /** 所有科目的索引都已重建，标记已清除。 */
    REBUILT,

    /** 至少一个科目失败：数据库保持已恢复的数据，不动；标记保留，下次打开应用重试。 */
    PARTIAL_FAILURE,
}

/**
 * 重建回执：给界面/日志的一句如实说明（成功时说明重建了几科；失败时列出科目与原因，
 * 并明确"待重建标记已保留"——不把失败伪装成完成）。
 */
data class RestoreIndexRebuildOutcome(
    val status: RestoreIndexRebuildStatus,
    val rebuiltSubjects: List<String>,
    val failedSubjects: List<String>,
    val message: String,
)

/**
 * 恢复后按科重建搜索索引（I1 在**数据库重新打开之后**调用本对象）。
 *
 * 语义三条：
 * 1. **失败不回滚数据库**：库里的业务数据已经是恢复后的那份，索引只是派生缓存；
 *    这里只把失败科目留在标记里，下一次启动再试。
 * 2. **成功才清标记**：部分失败时把标记改成只剩失败科目——已完成的科不会每次启动重跑。
 * 3. **取消原样传播**：协程取消不是"这一科失败"，不能把它写进标记后当成失败吞掉。
 */
object RestoreIndexRebuildCoordinator {

    suspend fun rebuildPending(
        context: Context,
        database: KnowledgeSearchIndexRebuildPort,
    ): RestoreIndexRebuildOutcome {
        val appContext = context.applicationContext
        if (!RestoreIndexRebuildMarker.exists(appContext)) {
            return RestoreIndexRebuildOutcome(
                status = RestoreIndexRebuildStatus.NOTHING_PENDING,
                rebuiltSubjects = emptyList(),
                failedSubjects = emptyList(),
                message = "没有待重建的搜索索引",
            )
        }
        // 标记存在但解析失败：仍按"待重建"处理，科目回落到库内查询；
        // 读不懂不能等于"没有待重建"，否则空索引会永远不再重建。
        val pending = RestoreIndexRebuildMarker.read(appContext)
            ?: PendingRestoreIndexRebuild(
                restoreId = "unreadable-marker",
                createdAtEpochMillis = 0L,
                subjects = emptyList(),
            )

        val listedFromDatabase = try {
            database.listKnowledgeSubjects()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Log.w(TAG, "无法列出知识点科目，回落到标记里的科目：${failure.message}")
            null
        }
        if (listedFromDatabase == null && pending.subjects.isEmpty()) {
            // 连科目都列不出来、标记里也没有：保留标记，下次再试；不谎报成功。
            return RestoreIndexRebuildOutcome(
                status = RestoreIndexRebuildStatus.PARTIAL_FAILURE,
                rebuiltSubjects = emptyList(),
                failedSubjects = emptyList(),
                message = "搜索索引重建失败：无法列出知识点科目；待重建标记已保留",
            )
        }
        // 合并两边：恢复之后、重建之前若又导入了新科目的知识点，也要一次建好。
        val targets = (pending.subjects + listedFromDatabase.orEmpty()).distinct()

        val rebuilt = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val reasons = mutableListOf<String>()
        targets.forEach { subject ->
            try {
                database.rebuildKnowledgeSearchIndex(subject)
                rebuilt += subject
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                failed += subject
                reasons += "$subject：${failure.message.orEmpty()}"
                Log.w(TAG, "搜索索引重建失败 subject=$subject", failure)
            }
        }

        if (failed.isEmpty()) {
            RestoreIndexRebuildMarker.clear(appContext)
            return RestoreIndexRebuildOutcome(
                status = RestoreIndexRebuildStatus.REBUILT,
                rebuiltSubjects = rebuilt,
                failedSubjects = emptyList(),
                message = "搜索索引已按 ${rebuilt.size} 科重建完成",
            )
        }

        // 只保留失败科目：已完成的科不再每次启动重跑。
        runCatching {
            RestoreIndexRebuildMarker.write(
                appContext,
                pending.copy(subjects = failed),
            )
        }.onFailure { markerFailure ->
            Log.w(TAG, "待重建标记改写失败，保留原标记：${markerFailure.message}")
        }
        return RestoreIndexRebuildOutcome(
            status = RestoreIndexRebuildStatus.PARTIAL_FAILURE,
            rebuiltSubjects = rebuilt,
            failedSubjects = failed,
            message = "搜索索引重建未完成（${reasons.joinToString("；")}）；" +
                "数据库保持已恢复的数据，待重建标记已保留，下次打开应用会重试",
        )
    }

    private const val TAG = "RestoreIndexRebuild"
}
