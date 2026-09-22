package com.hartmann.crosspromo.util

/**
 * Impression de-duplication: one displayed recommendation per card/session
 * generates exactly one impression, regardless of recompositions.
 * Pure logic — unit-tested without Android.
 */
class ImpressionTracker {
    private val seen = LinkedHashSet<String>()
    private val maxKeys = 200

    /**
     * Returns true the first time this key is recorded (i.e. the caller
     * should emit an impression), false for repeats.
     */
    @Synchronized
    fun shouldTrack(key: String): Boolean {
        if (seen.contains(key)) return false
        seen.add(key)
        while (seen.size > maxKeys) {
            val oldest = seen.iterator().next()
            seen.remove(oldest)
        }
        return true
    }

    /** Key scope: one card per recommendation response per placement. */
    fun key(requestId: String?, targetPackage: String, placement: String): String {
        return "${requestId ?: "noreq"}|$placement|$targetPackage"
    }

    @Synchronized
    fun reset() {
        seen.clear()
    }
}
