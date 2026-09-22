package com.hartmann.crosspromo.attribution

/**
 * Install attribution via Google's supported Play Install Referrer API.
 *
 * How it works end-to-end:
 *  1. [com.hartmann.crosspromo.launcher.PlayStoreLauncher] appends
 *     `referrer=utm_source%3D<source>%26utm_medium%3Dcrosspromo…` to the
 *     Play listing URL.
 *  2. When the user installs, Play makes that string available through the
 *     Install Referrer API (`com.android.installreferrer:installreferrer`).
 *  3. On first launch the TARGET app reads it and calls [parseReferrer].
 *
 * This module intentionally does NOT bundle the Install Referrer client: the
 * host app adds it only if it wants attribution —
 *
 *     implementation("com.android.installreferrer:installreferrer:2.2")
 *
 * then (Kotlin sketch, runs once, e.g. from Application.onCreate):
 *
 *     val client = InstallReferrerClient.newBuilder(this).build()
 *     client.startConnection(object : InstallReferrerStateListener {
 *         override fun onInstallReferrerSetupFinished(code: Int) {
 *             if (code == InstallReferrerResponse.OK) {
 *                 val raw = client.installReferrer.installReferrer
 *                 HartmannInstallAttribution.parseReferrer(raw)?.let { attr ->
 *                     if (attr.medium == "crosspromo") {
 *                         // POST {event: promo_install, ...} to /api/v1/events
 *                     }
 *                 }
 *                 client.endConnection()
 *             }
 *         }
 *         override fun onInstallReferrerServiceDisconnected() = Unit
 *     })
 *
 * Only the documented Install Referrer API is used — no invented parameters
 * are assumed to work beyond the standard `referrer` passthrough.
 */
object HartmannInstallAttribution {

    data class Attribution(
        /** Package of the Hartmann app that showed the promo. */
        val sourcePackage: String?,
        val medium: String?,
        val campaign: String?,
        /** Package of the promoted app (should equal this app's id). */
        val contentPackage: String?,
        val raw: String,
    ) {
        /** True when this install plausibly came from a Hartmann cross-promo. */
        val isCrossPromo: Boolean
            get() = medium == "crosspromo" && !sourcePackage.isNullOrBlank()
    }

    /**
     * Parse a raw install-referrer string (`utm_source=X&utm_medium=…`).
     * Returns null for null/blank input or when no usable fields exist.
     * Never throws.
     */
    fun parseReferrer(raw: String?): Attribution? {
        if (raw.isNullOrBlank()) return null
        return try {
            val params = raw.split("&")
                .mapNotNull { part ->
                    val eq = part.indexOf('=')
                    if (eq <= 0) return@mapNotNull null
                    val key = part.substring(0, eq).trim()
                    val value = java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8")
                    if (key.isEmpty()) null else key to value
                }
                .toMap()
            val source = params["utm_source"]
            val medium = params["utm_medium"]
            val campaign = params["utm_campaign"]
            val content = params["utm_content"]
            if (source.isNullOrBlank() && medium.isNullOrBlank() && campaign.isNullOrBlank()) return null
            Attribution(
                sourcePackage = source?.takeIf { it.isNotBlank() },
                medium = medium?.takeIf { it.isNotBlank() },
                campaign = campaign?.takeIf { it.isNotBlank() },
                contentPackage = content?.takeIf { it.isNotBlank() },
                raw = raw,
            )
        } catch (_: Exception) {
            null
        }
    }
}
