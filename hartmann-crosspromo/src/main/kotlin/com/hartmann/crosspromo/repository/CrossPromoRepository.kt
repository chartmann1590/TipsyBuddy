package com.hartmann.crosspromo.repository

import com.hartmann.crosspromo.api.CrossPromoApi
import com.hartmann.crosspromo.cache.PromoCache
import com.hartmann.crosspromo.model.PromoApp
import com.hartmann.crosspromo.model.PromoResponse
import com.hartmann.crosspromo.util.RotationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stale-while-revalidate repository:
 *
 *   UI opens -> emit cached recommendations instantly (if any)
 *            -> fetch fresh in the background
 *            -> update cache + emit when the response is non-empty
 *
 * Empty / failed responses are NOT cached over good data, and an empty
 * response never clears the UI when a cache exists — the section simply
 * keeps showing the last good data, or hides when there is nothing at all.
 * Cross-promo is never mission-critical.
 */
internal class CrossPromoRepository(
    private val api: CrossPromoApi,
    private val cache: PromoCache,
    private val sourcePackage: String,
    private val sdkVersion: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _states = mutableMapOf<String, MutableStateFlow<PromoState>>()
    private val inFlight = mutableSetOf<String>()
    private val lock = Any()

    fun observe(placement: String, limit: Int): StateFlow<PromoState> {
        return synchronized(lock) {
            _states.getOrPut(placement) { MutableStateFlow(PromoState.Loading) }
        }.also { refresh(placement, limit) }.asStateFlow()
    }

    fun refresh(placement: String, limit: Int) {
        if (!synchronized(lock) { inFlight.add(placement) }) return
        scope.launch {
            try {
                val state = synchronized(lock) {
                    _states.getOrPut(placement) { MutableStateFlow(PromoState.Loading) }
                }
                // 1) Instant cached content.
                val cached = withContext(Dispatchers.IO) { cache.readCached(placement) }
                val cachedResponse = cached?.rawBody?.let(api::parseResponse)
                if (cachedResponse != null && cachedResponse.apps.isNotEmpty()) {
                    state.value = PromoState.Ready(cachedResponse, fromCache = true)
                }
                // 2) Background revalidation.
                val fresh = withContext(Dispatchers.IO) { fetchFresh(placement, limit) }
                if (fresh != null && fresh.apps.isNotEmpty()) {
                    state.value = PromoState.Ready(fresh, fromCache = false)
                } else if (cachedResponse == null || cachedResponse.apps.isEmpty()) {
                    // Nothing usable at all -> render nothing (hide section).
                    if (state.value is PromoState.Loading) state.value = PromoState.Empty
                }
            } finally {
                synchronized(lock) { inFlight.remove(placement) }
            }
        }
    }

    private suspend fun fetchFresh(placement: String, limit: Int): PromoResponse? {
        val sessionId = try {
            cache.sessionId()
        } catch (_: Exception) {
            null
        }
        val recent = try {
            cache.readRecentTargets()
        } catch (_: Exception) {
            emptyList()
        }
        val params = mutableMapOf(
            "sourcePackage" to sourcePackage,
            "placement" to placement,
            "limit" to limit.toString(),
            "sdkVersion" to sdkVersion,
        )
        if (sessionId != null) params["sessionId"] = sessionId
        val exclude = RotationTracker.buildExclude(recent)
        if (exclude.isNotEmpty()) params["exclude"] = exclude
        val result = api.getRecommendations(params)
        val response = result.response
        if (response != null && response.apps.isNotEmpty() && result.rawBody != null) {
            try {
                cache.writeCached(placement, result.rawBody, sourcePackage)
                cache.addRecentTargets(response.apps.map { it.packageName })
            } catch (_: Exception) {
                // Cache failures must not break serving.
            }
        }
        return response?.takeIf { it.apps.isNotEmpty() }
    }

    /** Snapshot for the legacy View system (suspending, no Flow needed). */
    suspend fun snapshot(placement: String, limit: Int): List<PromoApp> {
        val cached = try {
            cache.readCached(placement)?.rawBody?.let(api::parseResponse)
        } catch (_: Exception) {
            null
        }
        if (cached != null && cached.apps.isNotEmpty()) {
            scope.launch { refresh(placement, limit) }
            return cached.apps
        }
        val fresh = try {
            withContext(Dispatchers.IO) { fetchFresh(placement, limit) }
        } catch (_: Exception) {
            null
        }
        return fresh?.apps ?: cached?.apps ?: emptyList()
    }

    sealed interface PromoState {
        data object Loading : PromoState
        data object Empty : PromoState // hide the section, show no errors
        data class Ready(val response: PromoResponse, val fromCache: Boolean) : PromoState
    }
}
