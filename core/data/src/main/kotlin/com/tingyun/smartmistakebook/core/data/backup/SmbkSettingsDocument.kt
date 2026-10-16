package com.tingyun.smartmistakebook.core.data.backup

import com.tingyun.smartmistakebook.core.domain.DEFAULT_REVIEW_REMINDER_MINUTES_AFTER_MIDNIGHT
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
import com.tingyun.smartmistakebook.core.domain.ReviewReminderPreferences
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 备份包内 `settings.json` 的 v1 文档（**自带 schema 版本**，与归档的 `formatVersion` 解耦：
 * 归档格式可以继续演进，设置文档的字段集合各自先升自己的版本）。
 *
 * 只装**偏好**，其余一概不装：
 *   - 装：`desired_retention`、`exam_calendar`、`optimized_fsrs_parameters`、
 *     提醒 `enabled` + `minutes_after_midnight`（各自对应 DataStore 里那几个键）；
 *   - 不装 `notification_delivery_history`（"今天已经提醒过"的去重台账——搬到另一天恢复
 *     等于把旧包那天的投递记录搬到现在，会吞掉或重复当天的提醒）、模型 API Key、
 *     授权租约、sleep journal（设备本地凭据与随时间滚动的观测，跨设备搬运只会说谎）。
 *
 * 版本闸门在 [SmbkSettingsCodec.rejectionReasonFor]：比 [MAX_READABLE_SCHEMA_VERSION]
 * 新的文档在**交换前**可读拒绝（与归档 `formatVersion` 同一纪律）。
 */
@Serializable
internal data class SmbkSettingsDocumentV1(
    @SerialName("schemaVersion")
    val schemaVersion: Int = SETTINGS_SCHEMA_VERSION,
    @SerialName("desiredRetention")
    val desiredRetention: Double,
    @SerialName("examCalendar")
    val examCalendar: List<ExamCalendarEntry> = emptyList(),
    /** 本地拟合出的 FSRS 参数；null = 该设备还没拟合过。 */
    @SerialName("optimizedFsrsParameters")
    val optimizedFsrsParameters: List<Double>? = null,
    @SerialName("reviewReminderEnabled")
    val reviewReminderEnabled: Boolean = false,
    @SerialName("reviewReminderMinutesAfterMidnight")
    val reviewReminderMinutesAfterMidnight: Int = DEFAULT_REVIEW_REMINDER_MINUTES_AFTER_MIDNIGHT,
) {
    companion object {
        const val SETTINGS_SCHEMA_VERSION = 1
        const val MIN_READABLE_SCHEMA_VERSION = 1
        const val MAX_READABLE_SCHEMA_VERSION = 1
    }
}

/** `settings.json` 的序列化与兼容判定（包内唯一读写点）。 */
internal object SmbkSettingsCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(document: SmbkSettingsDocumentV1): ByteArray =
        json.encodeToString(SmbkSettingsDocumentV1.serializer(), document)
            .toByteArray(Charsets.UTF_8)

    /** 解出文档；结构不合法（缺字段、有未知字段、领域类型自校验不通过）都抛。 */
    fun decode(text: String): SmbkSettingsDocumentV1 =
        json.decodeFromString(SmbkSettingsDocumentV1.serializer(), text)

    /**
     * 读侧兼容判定：schema 版本与领域约束。返回可读拒因，null 表示通过。
     *
     * 用**构造领域对象**来校验值域（保留带 `0.7..0.97`、提醒分钟 `0 until 1440`、
     * FSRS 参数个数与有限性），而不是在这里再抄一份边界：越界值若放过去，就只能等写 DataStore
     * 那一刻才失败——那是一次"数据库已切换、设置没进来"的半恢复。此处拒绝发生在**交换前**。
     */
    fun rejectionReasonFor(document: SmbkSettingsDocumentV1): String? {
        if (document.schemaVersion > SmbkSettingsDocumentV1.MAX_READABLE_SCHEMA_VERSION) {
            return "备份设置版本过新，暂不支持恢复"
        }
        if (document.schemaVersion < SmbkSettingsDocumentV1.MIN_READABLE_SCHEMA_VERSION) {
            return "备份设置版本过旧，暂不支持恢复"
        }
        return try {
            SchedulingOptions(document.desiredRetention)
            document.optimizedFsrsParameters?.toDoubleArray()?.let(FsrsScheduleMath::requireValid)
            ReviewReminderPreferences(
                enabled = document.reviewReminderEnabled,
                minutesAfterMidnight = document.reviewReminderMinutesAfterMidnight,
            )
            null
        } catch (failure: Exception) {
            "备份设置无效：${failure.message.orEmpty()}"
        }
    }
}
