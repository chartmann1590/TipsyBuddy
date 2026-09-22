package com.hartmann.crosspromo.cache

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.crossPromoStore by preferencesDataStore(name = "hartmann_crosspromo")

/**
 * Lightweight DataStore cache: no Room needed.
 *
 * Stores per-placement recommendation JSON (stale-while-revalidate), a ring
 * of recently shown targets for rotation, and a random short-lived session
 * UUID. No personal data, no advertising IDs, no device identifiers.
 */
internal class PromoCache(private val appContext: Context) {

    private val store = appContext.crossPromoStore

    suspend fun readCached(placement: String): CachedRecommendations? {
        val prefs = store.data.first()
        val body = prefs[stringPreferencesKey(keyBody(placement))] ?: return null
        return CachedRecommendations(
            rawBody = body,
            generatedAtMs = prefs[longPreferencesKey(keyTime(placement))] ?: 0L,
            sourcePackage = prefs[stringPreferencesKey(KEY_SOURCE)] ?: "",
        )
    }

    suspend fun writeCached(placement: String, rawBody: String, sourcePackage: String) {
        store.edit { prefs ->
            prefs[stringPreferencesKey(keyBody(placement))] = rawBody.take(MAX_CACHED_BYTES)
            prefs[longPreferencesKey(keyTime(placement))] = System.currentTimeMillis()
            prefs[stringPreferencesKey(KEY_SOURCE)] = sourcePackage
        }
    }

    /** Recently shown targets (most-recent-first, capped) for rotation. */
    suspend fun readRecentTargets(): List<String> {
        val prefs = store.data.first()
        val raw = prefs[stringPreferencesKey(KEY_RECENT)] ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_RECENT)
    }

    suspend fun addRecentTargets(packages: List<String>) {
        if (packages.isEmpty()) return
        store.edit { prefs ->
            val current = (prefs[stringPreferencesKey(KEY_RECENT)] ?: "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val merged = (packages + current).distinct().take(MAX_RECENT)
            prefs[stringPreferencesKey(KEY_RECENT)] = merged.joinToString(",")
        }
    }

    /** Short-lived random session id, rotated daily. Pure UUID, no identity. */
    suspend fun sessionId(): String {
        val prefs = store.data.map { it[stringPreferencesKey(KEY_SESSION)] to it[longPreferencesKey(KEY_SESSION_TIME)] }.first()
        val id = prefs.first
        val at = prefs.second ?: 0L
        if (!id.isNullOrBlank() && System.currentTimeMillis() - at < SESSION_TTL_MS) return id
        val fresh = UUID.randomUUID().toString()
        store.edit {
            it[stringPreferencesKey(KEY_SESSION)] = fresh
            it[longPreferencesKey(KEY_SESSION_TIME)] = System.currentTimeMillis()
        }
        return fresh
    }

    data class CachedRecommendations(
        val rawBody: String,
        val generatedAtMs: Long,
        val sourcePackage: String,
    )

    private fun keyBody(placement: String) = "rec_body_$placement"
    private fun keyTime(placement: String) = "rec_time_$placement"

    companion object {
        private const val KEY_SOURCE = "source_package"
        private const val KEY_RECENT = "recent_targets"
        private const val KEY_SESSION = "session_id"
        private const val KEY_SESSION_TIME = "session_time"
        private const val MAX_RECENT = 12
        private const val MAX_CACHED_BYTES = 32_000
        private const val SESSION_TTL_MS = 24L * 3600_000L
    }
}
