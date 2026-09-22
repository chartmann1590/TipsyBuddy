package com.tipsybuddy.app.data.feedback

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.tipsybuddy.app.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generic app/device diagnostics for feedback reports.
 * Collects only non-sensitive device info — never contacts, location,
 * credentials, tokens, or secret BuildConfig values.
 */
object DiagnosticsHelper {

    fun collect(context: Context): String {
        val pm = context.packageManager
        val (versionName, versionCode) = FeedbackWorkerApi.appVersionLabel(pm, context.packageName)
        val locale = Locale.getDefault().toString()
        val tz = TimeZone.getDefault().id
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())

        val (freeStorage, totalStorage) = storageStats()
        val (freeMem, totalMem) = memoryStats(context)

        return buildString {
            appendLine("## Diagnostics")
            appendLine()
            appendLine("- App: TipsyBuddy")
            appendLine("- Package: ${context.packageName}")
            appendLine("- Version: $versionName ($versionCode)")
            appendLine("- Device: ${Build.BRAND} ${Build.MODEL}")
            appendLine("- Manufacturer: ${Build.MANUFACTURER}")
            appendLine("- Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("- Locale: $locale")
            appendLine("- Time Zone: $tz")
            appendLine("- Storage Free/Total: ${formatBytes(freeStorage)} / ${formatBytes(totalStorage)}")
            appendLine("- Memory Free/Total: ${formatBytes(freeMem)} / ${formatBytes(totalMem)}")
            appendLine("- Reported At: $now")
        }.trimEnd()
    }

    private fun storageStats(): Pair<Long, Long> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val free = stat.availableBytes
            val total = stat.totalBytes
            Pair(free, total)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    private fun memoryStats(context: Context): Pair<Long, Long> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            Pair(info.availMem, info.totalMem)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "unknown"
        val gb = bytes / 1_000_000_000.0
        if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb)
        val mb = bytes / 1_000_000.0
        if (mb >= 1) return String.format(Locale.US, "%.0f MB", mb)
        return String.format(Locale.US, "%d KB", bytes / 1_000)
    }

    /** Standalone app version/build line (used by UI headers, not diagnostics). */
    fun appVersionLine(context: Context): String {
        val (name, code) = FeedbackWorkerApi.appVersionLabel(context.packageManager, context.packageName)
        return "$name ($code)"
    }

    /** Reference to satisfy unused-import checks if BuildConfig access changes. */
    @Suppress("unused")
    private fun buildConfigMarker(): String = BuildConfig.APPLICATION_ID
}
