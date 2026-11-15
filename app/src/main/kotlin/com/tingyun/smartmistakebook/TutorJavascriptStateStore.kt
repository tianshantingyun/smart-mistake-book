package com.tingyun.smartmistakebook

import android.content.Context
import com.tingyun.smartmistakebook.core.domain.TutorJavascriptStateStore
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Process-durable RUN_JAVASCRIPT state keyed by conversation/session.
 *
 * A separate prefs file keeps it out of feature stores; values are bounded to the same
 * 16 KiB budget the engine enforces and malformed rows are treated as absent.
 */
internal class SharedPreferencesTutorJavascriptStateStore(context: Context) : TutorJavascriptStateStore {
    private val preferences = context.applicationContext
        .getSharedPreferences("tutor-javascript-state", Context.MODE_PRIVATE)

    override suspend fun read(scopeKey: String): String? = withContext(Dispatchers.IO) {
        preferences.getString(key(scopeKey), null)?.takeIf { value ->
            value.toByteArray(Charsets.UTF_8).size <= MAX_STATE_BYTES &&
                runCatching { Json.parseToJsonElement(value) }.isSuccess
        }
    }

    override suspend fun write(scopeKey: String, stateJson: String) {
        require(stateJson.toByteArray(Charsets.UTF_8).size <= MAX_STATE_BYTES) {
            "JavaScript state exceeds the 16 KiB budget"
        }
        require(runCatching { Json.parseToJsonElement(stateJson) }.isSuccess) {
            "JavaScript state must be valid JSON"
        }
        withContext(Dispatchers.IO) {
            check(preferences.edit().putString(key(scopeKey), stateJson).commit()) {
                "JavaScript state could not be persisted"
            }
        }
    }

    private fun key(scopeKey: String): String = "state-" + MessageDigest.getInstance("SHA-256")
        .digest(scopeKey.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val MAX_STATE_BYTES = 16 * 1024
    }
}
