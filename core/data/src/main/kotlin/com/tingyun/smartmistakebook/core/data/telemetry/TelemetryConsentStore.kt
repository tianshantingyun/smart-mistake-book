package com.tingyun.smartmistakebook.core.data.telemetry

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * 遥测同意的唯一写入口（R5 §3.1/§3.5/§4 V1/V11）：
 * `grant()` / `withdraw()` / `invalidateForPolicyChange()`。其它类不得直接写这份 DataStore。
 *
 * 默认值：没有任何键时 [current] 返回 `UNKNOWN`（等价 OFF，`isEnabled=false`）。DataStore
 * 里不会出现“默认为 true”的布尔键；只有显式 `grant()` 才写入 GRANTED。
 *
 * [snapshot] 是给非挂起的采集门用的进程内缓存；新进程启动时它是 UNKNOWN，直到调用方
 * `await` 一次 [current]/[refresh]。这是 fail-closed：宁可在刷新前不采，也不在未知时放行。
 */
class TelemetryConsentStore(
    context: Context,
    scope: CoroutineScope,
) {
    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = {
            context.applicationContext.preferencesDataStoreFile(DATASTORE_FILE)
        },
    )

    @Volatile
    private var cached: TelemetryConsentSnapshot = TelemetryConsentSnapshot()

    val state: Flow<TelemetryConsentSnapshot> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::decode)
        .distinctUntilChanged()
        .onEach { snapshot -> cached = snapshot }

    suspend fun current(): TelemetryConsentSnapshot {
        val snapshot = state.first()
        cached = snapshot
        return snapshot
    }

    suspend fun refresh(): TelemetryConsentSnapshot = current()

    /** 非挂起读取：给采集点用；未刷新时返回 UNKNOWN（门为 false）。 */
    fun snapshot(): TelemetryConsentSnapshot = cached

    suspend fun grant(
        policy: TelemetryPolicy,
        ageBand: TelemetryAgeBand,
        guardianConfirmed: Boolean,
        grantedAtEpochMillis: Long = System.currentTimeMillis(),
    ): TelemetryConsentSnapshot {
        require(TelemetryGate.ageGateSatisfied(ageBand, guardianConfirmed)) {
            "consent refused: age gate is not satisfied for $ageBand (guardianConfirmed=$guardianConfirmed)"
        }
        dataStore.edit { values ->
            values[STATE] = TelemetryConsentState.GRANTED.wireName
            values[POLICY_VERSION] = policy.version
            values[POLICY_HASH] = policy.hash
            values[GRANTED_AT] = grantedAtEpochMillis
            values.remove(WITHDRAWN_AT)
            values[AGE_BAND] = ageBand.wireName
            values[GUARDIAN_CONFIRMED] = guardianConfirmed
        }
        return current()
    }

    suspend fun withdraw(withdrawnAtEpochMillis: Long = System.currentTimeMillis()): TelemetryConsentSnapshot {
        dataStore.edit { values ->
            values[STATE] = TelemetryConsentState.WITHDRAWN.wireName
            values[WITHDRAWN_AT] = withdrawnAtEpochMillis
        }
        return current()
    }

    /**
     * 政策目的/类型/接收方/保留期发生实质变化时调用：旧同意失效，回到 UNKNOWN，必须重新同意。
     * 保留最后一次 policy/grant 时间作为审计痕迹，但 state 已不是 GRANTED，门不会放行。
     */
    suspend fun invalidateForPolicyChange(): TelemetryConsentSnapshot {
        dataStore.edit { values ->
            values[STATE] = TelemetryConsentState.UNKNOWN.wireName
        }
        return current()
    }

    private fun decode(values: androidx.datastore.preferences.core.Preferences): TelemetryConsentSnapshot {
        val state = values[STATE]
            ?.let { raw -> TelemetryConsentState.entries.firstOrNull { it.wireName == raw } }
            ?: TelemetryConsentState.UNKNOWN
        val ageBand = values[AGE_BAND]
            ?.let { raw -> TelemetryAgeBand.entries.firstOrNull { it.wireName == raw } }
        return TelemetryConsentSnapshot(
            state = state,
            policyVersion = values[POLICY_VERSION],
            policyHash = values[POLICY_HASH],
            grantedAtEpochMillis = values[GRANTED_AT],
            withdrawnAtEpochMillis = values[WITHDRAWN_AT],
            ageBand = ageBand,
            guardianConfirmed = values[GUARDIAN_CONFIRMED] == true,
        )
    }

    private companion object {
        const val DATASTORE_FILE = "telemetry_consent"
        val STATE = stringPreferencesKey("consent_state")
        val POLICY_VERSION = stringPreferencesKey("policy_version")
        val POLICY_HASH = stringPreferencesKey("policy_hash")
        val GRANTED_AT = longPreferencesKey("granted_at_epoch_millis")
        val WITHDRAWN_AT = longPreferencesKey("withdrawn_at_epoch_millis")
        val AGE_BAND = stringPreferencesKey("age_band")
        val GUARDIAN_CONFIRMED = booleanPreferencesKey("guardian_confirmed")
    }
}
