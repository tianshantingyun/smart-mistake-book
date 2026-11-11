package com.tingyun.smartmistakebook.core.data.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.FsrsParameterFit
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import com.tingyun.smartmistakebook.core.domain.SchedulingSettingsStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * DataStore-backed scheduling settings (spec mastery-scheduling 2.4/2.17/2.20):
 * desired retention, the FSRS kill switch, the exam calendar, and locally
 * optimized FSRS parameters awaiting the next launch.
 */
class DataStoreSchedulingSettingsStore(
    context: Context,
    scope: CoroutineScope,
) : SchedulingSettingsStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = {
            context.applicationContext.preferencesDataStoreFile(DATASTORE_FILE)
        },
    )

    override val options: Flow<SchedulingOptions> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { values ->
            SchedulingOptions(
                dailyReviewMinutes = values[DAILY_REVIEW_MINUTES]?.coerceIn(5, 120) ?: 20,
                desiredRetention = values[DESIRED_RETENTION]
                    ?: SchedulingOptions().desiredRetention,
            )
        }
        .distinctUntilChanged()

    override suspend fun setOptions(options: SchedulingOptions) {
        dataStore.edit { values ->
            values[DESIRED_RETENTION] = options.desiredRetention
            values[DAILY_REVIEW_MINUTES] = options.dailyReviewMinutes
            // KF-11：FSRS 开关已删除；历史库里的 `fsrs_enabled` 键不再读写（遗留数据无害）。
        }
    }

    override val exams: Flow<List<ExamCalendarEntry>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { values ->
            values[EXAMS]?.mapNotNull(::decodeExam).orEmpty()
        }
        .distinctUntilChanged()

    override suspend fun addExam(entry: ExamCalendarEntry) {
        dataStore.edit { values ->
            val current = values[EXAMS].orEmpty().mapNotNull(::decodeExam)
            val updated = current.filterNot { it.entryId == entry.entryId } + entry
            values[EXAMS] = updated.map(::encodeExam).toSet()
        }
    }

    override suspend fun removeExam(entryId: String) {
        dataStore.edit { values ->
            val current = values[EXAMS].orEmpty().mapNotNull(::decodeExam)
            values[EXAMS] = current.filterNot { it.entryId == entryId }.map(::encodeExam).toSet()
        }
    }

    override val optimizedParameters: Flow<DoubleArray?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { values ->
            values[OPTIMIZED_PARAMETERS]?.split(PARAMETER_SEPARATOR)
                ?.mapNotNull { token -> token.toDoubleOrNull() }
                ?.takeIf { it.size == FSRS_PARAMETER_COUNT }
                ?.toDoubleArray()
        }
        .distinctUntilChanged()

    /**
     * 拟合记录与参数同库（批次 49 P2-12）：只存参数的话，进程重启后"这组参数是什么时候
     * 拟合出来的"永久丢失，校准页只能显示一个没有时点的样本数。
     * 时点/样本/验证损失三键缺任一 = 记录不完整 → null（不拼半个记录出来）；
     * 默认参数基线可缺（采纳当时没有验证对）。
     */
    override val parameterFit: Flow<FsrsParameterFit?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { values ->
            val adoptedAt = values[PARAMETER_FIT_AT] ?: return@map null
            val samples = values[PARAMETER_FIT_SAMPLES] ?: return@map null
            val validationLoss = values[PARAMETER_FIT_VALIDATION_LOSS] ?: return@map null
            FsrsParameterFit(
                adoptedAtEpochMillis = adoptedAt,
                sampleCount = samples,
                validationLogLoss = validationLoss,
                baselineValidationLogLoss = values[PARAMETER_FIT_BASELINE_LOSS],
            )
        }
        .distinctUntilChanged()

    override suspend fun recordParameterFit(fit: FsrsParameterFit?) {
        dataStore.edit { values -> values.writeParameterFit(fit) }
    }

    override suspend fun setOptimizedParameters(parameters: DoubleArray?) {
        dataStore.edit { values ->
            if (parameters == null) {
                values.remove(OPTIMIZED_PARAMETERS)
            } else {
                values[OPTIMIZED_PARAMETERS] = parameters.joinToString(PARAMETER_SEPARATOR)
            }
            // 参数换了，旧拟合记录就不再描述当前参数（重置、备份恢复都会走到这里）。
            // 先清、后由采纳路径 recordParameterFit 写新记录：宁可页面显示"没有拟合记录"，
            // 也不能把旧时点贴到一组新参数上。
            values.writeParameterFit(null)
        }
    }

    private fun decodeExam(raw: String): ExamCalendarEntry? = try {
        json.decodeFromString(ExamCalendarEntry.serializer(), raw)
    } catch (_: Exception) {
        null
    }

    private fun encodeExam(entry: ExamCalendarEntry): String =
        json.encodeToString(ExamCalendarEntry.serializer(), entry)

    /**
     * 拟合记录的四个键同写同清（批次 49 P2-12）：只写一半会让读取方拼出一个
     * "有时点、没样本"的假记录。基线损失可缺（采纳当时没有验证对），其余三个必需。
     */
    private fun MutablePreferences.writeParameterFit(fit: FsrsParameterFit?) {
        if (fit == null) {
            remove(PARAMETER_FIT_AT)
            remove(PARAMETER_FIT_SAMPLES)
            remove(PARAMETER_FIT_VALIDATION_LOSS)
            remove(PARAMETER_FIT_BASELINE_LOSS)
        } else {
            this[PARAMETER_FIT_AT] = fit.adoptedAtEpochMillis
            this[PARAMETER_FIT_SAMPLES] = fit.sampleCount
            this[PARAMETER_FIT_VALIDATION_LOSS] = fit.validationLogLoss
            fit.baselineValidationLogLoss
                ?.let { this[PARAMETER_FIT_BASELINE_LOSS] = it }
                ?: remove(PARAMETER_FIT_BASELINE_LOSS)
        }
    }

    private companion object {
        const val DATASTORE_FILE = "scheduling_settings"
        const val FSRS_PARAMETER_COUNT = 21
        const val PARAMETER_SEPARATOR = ","
        val DAILY_REVIEW_MINUTES = intPreferencesKey("daily_review_minutes")
        val DESIRED_RETENTION = doublePreferencesKey("desired_retention")
        val EXAMS = stringSetPreferencesKey("exam_calendar")
        val OPTIMIZED_PARAMETERS = stringPreferencesKey("optimized_fsrs_parameters")
        val PARAMETER_FIT_AT = longPreferencesKey("parameter_fit_adopted_at_epoch_millis")
        val PARAMETER_FIT_SAMPLES = intPreferencesKey("parameter_fit_sample_count")
        val PARAMETER_FIT_VALIDATION_LOSS = doublePreferencesKey("parameter_fit_validation_log_loss")
        val PARAMETER_FIT_BASELINE_LOSS = doublePreferencesKey("parameter_fit_baseline_log_loss")
    }
}
