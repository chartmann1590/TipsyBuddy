package com.hartmann.crosspromo

import com.hartmann.crosspromo.attribution.HartmannInstallAttribution
import com.hartmann.crosspromo.launcher.PlayStoreLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreLauncherTest {

    @Test
    fun marketUriUsesMarketSchemeWithReferrer() {
        val uri = PlayStoreLauncher.marketUri("com.example.target", "com.example.source")
        assertTrue(uri.startsWith("market://details?id=com.example.target"))
        assertTrue(uri.contains("referrer="))
        assertTrue(uri.contains("utm_medium"))
        assertTrue(uri.contains("crosspromo"))
    }

    @Test
    fun webUriIsHttpsFallbackWithReferrer() {
        val uri = PlayStoreLauncher.webUri("com.example.target", "com.example.source")
        assertTrue(uri.startsWith("https://play.google.com/store/apps/details"))
        assertTrue(uri.contains("referrer="))
    }

    @Test
    fun noReferrerWhenSourceUnknown() {
        val uri = PlayStoreLauncher.marketUri("com.example.target", null)
        assertFalse(uri.contains("referrer="))
        assertEquals("market://details?id=com.example.target", uri)
    }

    @Test
    fun referrerCarriesAttributionFields() {
        val referrer = PlayStoreLauncher.buildReferrer("com.example.source", "com.example.target")
        assertTrue(referrer.contains("utm_source=com.example.source"))
        assertTrue(referrer.contains("utm_medium=crosspromo"))
        assertTrue(referrer.contains("utm_campaign=hartmann_crosspromo"))
        assertTrue(referrer.contains("utm_content=com.example.target"))
    }
}

class AttributionParserTest {

    @Test
    fun parsesCrossPromoReferrer() {
        val attr = HartmannInstallAttribution.parseReferrer(
            "utm_source=com.example.source&utm_medium=crosspromo&utm_campaign=hartmann_crosspromo&utm_content=com.example.target",
        )
        assertTrue(attr != null)
        assertEquals("com.example.source", attr!!.sourcePackage)
        assertEquals("crosspromo", attr.medium)
        assertEquals("hartmann_crosspromo", attr.campaign)
        assertEquals("com.example.target", attr.contentPackage)
        assertTrue(attr.isCrossPromo)
    }

    @Test
    fun nonCrossPromoReferrerIsNotFlagged() {
        val attr = HartmannInstallAttribution.parseReferrer("utm_source=google-play&utm_medium=organic")
        assertTrue(attr != null)
        assertFalse(attr!!.isCrossPromo)
    }

    @Test
    fun nullOrGarbageReturnsNull() {
        assertNull(HartmannInstallAttribution.parseReferrer(null))
        assertNull(HartmannInstallAttribution.parseReferrer(""))
        assertNull(HartmannInstallAttribution.parseReferrer(";;;"))
    }
}
