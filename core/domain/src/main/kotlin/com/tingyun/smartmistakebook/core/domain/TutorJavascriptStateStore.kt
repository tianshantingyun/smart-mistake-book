package com.tingyun.smartmistakebook.core.domain

/**
 * Durable state for `RUN_JAVASCRIPT`.
 *
 * The per-turn `TutorAgentContext` only lives for one model task; this store gives the same
 * conversation's later turns (and process restarts) the last completed JavaScript state.
 * Implementations must keep the value ≤16 KiB and treat a missing/corrupt value as null.
 */
interface TutorJavascriptStateStore {
    suspend fun read(scopeKey: String): String?
    suspend fun write(scopeKey: String, stateJson: String)
}
