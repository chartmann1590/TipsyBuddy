package com.hartmann.crosspromo

import com.hartmann.crosspromo.util.ImpressionTracker
import com.hartmann.crosspromo.util.RotationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationTrackerTest {

    @Test
    fun buildExcludeMergesAndCaps() {
        val exclude = RotationTracker.buildExclude(
            recentTargets = listOf("com.example.a", "com.example.b"),
            extra = listOf("com.example.c"),
        )
        assertEquals("com.example.a,com.example.b,com.example.c", exclude)
    }

    @Test
    fun buildExcludeDedupesAndDropsBlanks() {
        val exclude = RotationTracker.buildExclude(
            listOf("com.example.a", "com.example.a", "  ", "com.example.b"),
        )
        assertEquals("com.example.a,com.example.b", exclude)
    }

    @Test
    fun buildExcludeIsCapped() {
        val many = (1..50).map { "com.example.app$it" }
        val exclude = RotationTracker.buildExclude(many)
        assertEquals(RotationTracker.MAX_EXCLUDE, exclude.split(",").size)
    }

    @Test
    fun mergeRecentKeepsMostRecentFirst() {
        val merged = RotationTracker.mergeRecent(
            current = listOf("com.example.a", "com.example.b"),
            shown = listOf("com.example.c"),
        )
        assertEquals(listOf("com.example.c", "com.example.a", "com.example.b"), merged)
    }
}

class ImpressionTrackerTest {

    @Test
    fun tracksOncePerKeyAcrossRecompositions() {
        val tracker = ImpressionTracker()
        val key = tracker.key("req1", "com.example.a", "settings")
        assertTrue(tracker.shouldTrack(key))
        assertFalse(tracker.shouldTrack(key))
        assertFalse(tracker.shouldTrack(key))
    }

    @Test
    fun differentPlacementsAndTargetsAreIndependent() {
        val tracker = ImpressionTracker()
        assertTrue(tracker.shouldTrack(tracker.key("req1", "com.example.a", "settings")))
        assertTrue(tracker.shouldTrack(tracker.key("req1", "com.example.a", "home")))
        assertTrue(tracker.shouldTrack(tracker.key("req1", "com.example.b", "settings")))
    }

    @Test
    fun newRecommendationResponseRetracks() {
        val tracker = ImpressionTracker()
        assertTrue(tracker.shouldTrack(tracker.key("req1", "com.example.a", "settings")))
        assertTrue(tracker.shouldTrack(tracker.key("req2", "com.example.a", "settings")))
    }
}
