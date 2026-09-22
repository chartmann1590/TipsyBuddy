package com.hartmann.crosspromo

import com.hartmann.crosspromo.api.CrossPromoApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromoResponseParsingTest {

    private val api = CrossPromoApi("https://example.com")

    @Test
    fun parsesRealShapedResponse() {
        val body = """
        {
          "version": 1,
          "requestId": "abc123",
          "generatedAt": "2026-09-22T13:00:00Z",
          "expiresAt": "2026-09-22T19:00:00Z",
          "apps": [
            {
              "packageName": "com.example.app",
              "name": "Example",
              "iconUrl": "https://example.com/icon.png",
              "shortDescription": "Example description",
              "rating": 4.7,
              "ratingCount": 2400,
              "installText": "100K+",
              "storeUrl": "https://play.google.com/store/apps/details?id=com.example.app",
              "selectionType": "popular"
            }
          ]
        }
        """.trimIndent()
        val parsed = api.parseResponse(body)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.apps.size)
        assertEquals("com.example.app", parsed.apps[0].packageName)
        assertEquals(4.7f, parsed.apps[0].rating)
        assertEquals("abc123", parsed.requestId)
    }

    @Test
    fun ignoresUnknownFieldsForForwardCompatibility() {
        val body = """{"version": 1, "futureField": {"nested": true}, "apps": [
          {"packageName": "com.example.a", "name": "A", "brandNewMetric": 42,
           "storeUrl": "https://play.google.com/store/apps/details?id=com.example.a"}
        ]}"""
        val parsed = api.parseResponse(body)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.apps.size)
    }

    @Test
    fun emptyResponseYieldsEmptyList() {
        val parsed = api.parseResponse("""{"version":1,"generatedAt":"x","apps":[]}""")
        assertNotNull(parsed)
        assertTrue(parsed!!.apps.isEmpty())
    }

    @Test
    fun malformedResponseReturnsNullInsteadOfCrashing() {
        assertNull(api.parseResponse(null))
        assertNull(api.parseResponse(""))
        assertNull(api.parseResponse("not json {{{"))
        assertNull(api.parseResponse("""{"apps": "oops"}"""))
    }

    @Test
    fun entriesWithoutPackageAreDropped() {
        val body = """{"version":1,"apps":[{"name":"Nameless"}, {"packageName":"com.example.ok","name":"Ok"}]}"""
        val parsed = api.parseResponse(body)
        assertNotNull(parsed)
        assertEquals(listOf("com.example.ok"), parsed!!.apps.map { it.packageName })
    }
}
