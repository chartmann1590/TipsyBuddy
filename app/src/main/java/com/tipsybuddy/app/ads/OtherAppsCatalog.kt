package com.tipsybuddy.app.ads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Cross-promo catalog for Settings → "Our other apps".
 * Package IDs looked up from chartmann1590 GitHub applicationId fields.
 */
data class OtherApp(
    val name: String,
    val tagline: String,
    val emoji: String,
    /** Play Store applicationId when known; null → search / GitHub fallback. */
    val packageId: String?,
    val githubUrl: String,
    val playSearchQuery: String
)

object OtherAppsCatalog {
    val apps: List<OtherApp> = listOf(
        OtherApp(
            name = "DriveVault",
            tagline = "Privacy-first dashcam with GPS overlays",
            emoji = "🚗",
            packageId = "com.drivevault.dashcam",
            githubUrl = "https://github.com/chartmann1590/DriveVault",
            playSearchQuery = "DriveVault dashcam"
        ),
        OtherApp(
            name = "Live Transcribe",
            tagline = "Live Android speech transcription",
            emoji = "🎤",
            packageId = "com.charles.livecaptionn",
            githubUrl = "https://github.com/chartmann1590/LiveTranscribe-Android",
            playSearchQuery = "Live Transcribe Charles"
        ),
        OtherApp(
            name = "FocusFlow",
            tagline = "Pomodoro timer & task manager",
            emoji = "⏱️",
            packageId = "com.focusflow",
            githubUrl = "https://github.com/chartmann1590/FocusFlow",
            playSearchQuery = "FocusFlow Pomodoro"
        ),
        OtherApp(
            name = "PixelDream",
            tagline = "On-device AI image generation",
            emoji = "✨",
            packageId = "com.hartmann.pixeldream",
            githubUrl = "https://github.com/chartmann1590/pixeldream",
            playSearchQuery = "PixelDream AI"
        ),
        OtherApp(
            name = "ScamRadar",
            tagline = "On-device AI scam & phishing detector",
            emoji = "🛡️",
            packageId = "com.charles.scamradar.app",
            githubUrl = "https://github.com/chartmann1590/ScamRadar",
            playSearchQuery = "ScamRadar"
        ),
        OtherApp(
            name = "Pixel Fish Tank",
            tagline = "Cozy pixel-art virtual pet fish",
            emoji = "🐠",
            packageId = "com.charles.virtualpet.fishtank",
            githubUrl = "https://github.com/chartmann1590/Pixel-Fish-Tank",
            playSearchQuery = "Pixel Fish Tank"
        )
    )

    fun open(context: Context, app: OtherApp) {
        val pkg = app.packageId
        if (!pkg.isNullOrBlank()) {
            // Prefer Play Store market:// deep link
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$pkg")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(marketIntent)
                return
            } catch (_: ActivityNotFoundException) {
                // Fall through to HTTPS Play URL
            }
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (_: ActivityNotFoundException) {
                // Fall through
            }
        }

        // Search Play, then GitHub as last resort
        val searchUri = Uri.parse(
            "https://play.google.com/store/search?q=${Uri.encode(app.playSearchQuery)}&c=apps"
        )
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, searchUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(app.githubUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
