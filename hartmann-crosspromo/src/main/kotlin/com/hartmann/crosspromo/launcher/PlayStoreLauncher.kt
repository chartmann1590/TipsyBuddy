package com.hartmann.crosspromo.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

/**
 * Opens official Google Play listings. NEVER downloads APKs, NEVER sideloads,
 * NEVER installs anything directly — the CTA always lands on the Play Store
 * (or the HTTPS listing in a browser as a fallback).
 */
object PlayStoreLauncher {

    /**
     * Attribution extras appended to the Play URL as `referrer`, using the
     * documented UTM scheme consumed by the Play Install Referrer API:
     * https://developer.android.com/google/play/installreferrer
     */
    fun buildReferrer(sourcePackage: String, targetPackage: String): String {
        val params = linkedMapOf(
            "utm_source" to sourcePackage,
            "utm_medium" to "crosspromo",
            "utm_campaign" to "hartmann_crosspromo",
            "utm_content" to targetPackage,
        )
        return params.entries.joinToString("&") { (k, v) -> "$k=${urlEncode(v)}" }
    }

    /** Pure string builders (unit-testable on the JVM without Android). */
    fun marketUri(targetPackage: String, sourcePackage: String?): String {
        val base = "market://details?id=$targetPackage"
        val referrer = if (!sourcePackage.isNullOrBlank()) buildReferrer(sourcePackage, targetPackage) else null
        return if (referrer != null) "$base&referrer=${urlEncode(referrer)}" else base
    }

    /** Pure string builders (unit-testable on the JVM without Android). */
    fun webUri(targetPackage: String, sourcePackage: String?): String {
        val base = "https://play.google.com/store/apps/details?id=$targetPackage"
        val referrer = if (!sourcePackage.isNullOrBlank()) buildReferrer(sourcePackage, targetPackage) else null
        return if (referrer != null) "$base&referrer=${urlEncode(referrer)}" else base
    }

    /**
     * Launch the listing. Returns true if *something* was opened. Never throws:
     * if Play is missing we fall back to the browser, and if nothing can
     * handle it we simply return false (the host UI stays put).
     */
    fun launch(context: Context, targetPackage: String, sourcePackage: String?): Boolean {
        if (targetPackage.isBlank()) return false
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(marketUri(targetPackage, sourcePackage))).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Prefer the Play Store app when it can handle the intent.
        if (market.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(market)
                return true
            } catch (_: ActivityNotFoundException) {
                // Fall through to browser.
            } catch (_: SecurityException) {
                return false
            }
        }
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(webUri(targetPackage, sourcePackage))).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (web.resolveActivity(context.packageManager) == null) return false
            context.startActivity(web)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun urlEncode(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8")
        } catch (_: UnsupportedEncodingException) {
            value
        }
    }
}
