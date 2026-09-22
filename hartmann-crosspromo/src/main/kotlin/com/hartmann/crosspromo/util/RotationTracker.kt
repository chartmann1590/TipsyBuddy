package com.hartmann.crosspromo.util

/**
 * Pure rotation helpers (unit-tested without Android).
 *
 * The backend is stateless; rotation is achieved by sending recently shown
 * targets as `exclude=` so the server picks other eligible apps first.
 * The client persists the ring in [com.hartmann.crosspromo.cache.PromoCache].
 */
object RotationTracker {

    /**
     * Build the `exclude` query value: recent targets first, then the current
     * caller-supplied extras. Capped so the URL stays small.
     */
    fun buildExclude(recentTargets: List<String>, extra: List<String> = emptyList()): String {
        return (recentTargets + extra)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_EXCLUDE)
            .joinToString(",")
    }

    /**
     * Merge freshly shown packages into the recent ring (most-recent-first,
     * deduplicated, capped).
     */
    fun mergeRecent(current: List<String>, shown: List<String>, max: Int = MAX_RECENT): List<String> {
        return (shown + current).distinct().take(max)
    }

    const val MAX_EXCLUDE = 20
    const val MAX_RECENT = 12
}
