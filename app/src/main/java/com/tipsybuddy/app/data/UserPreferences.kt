package com.tipsybuddy.app.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tipsy_buddy_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(value) = prefs.edit().putString("user_name", value).apply()

    var weightLbs: Float
        get() = prefs.getFloat("weight_lbs", 160.0f)
        set(value) = prefs.edit().putFloat("weight_lbs", value).apply()

    var gender: String // "MALE" or "FEMALE"
        get() = prefs.getString("gender", "MALE") ?: "MALE"
        set(value) = prefs.edit().putString("gender", value).apply()

    var homeAddress: String
        get() = prefs.getString("home_address", "") ?: ""
        set(value) = prefs.edit().putString("home_address", value).apply()

    var homeLat: Double
        get() = prefs.getFloat("home_lat", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("home_lat", value.toFloat()).apply()

    var homeLon: Double
        get() = prefs.getFloat("home_lon", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("home_lon", value.toFloat()).apply()

    var preferredRide: String // "UBER" or "LYFT"
        get() = prefs.getString("preferred_ride", "UBER") ?: "UBER"
        set(value) = prefs.edit().putString("preferred_ride", value).apply()

    var emergencyContactName: String
        get() = prefs.getString("emergency_name", "") ?: ""
        set(value) = prefs.edit().putString("emergency_name", value).apply()

    var emergencyContactPhone: String
        get() = prefs.getString("emergency_phone", "") ?: ""
        set(value) = prefs.edit().putString("emergency_phone", value).apply()

    var liveSessionId: String
        get() {
            var id = prefs.getString("live_session_id", null)
            if (id == null) {
                id = "tb-" + UUID.randomUUID().toString().substring(0, 6)
                prefs.edit().putString("live_session_id", id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString("live_session_id", value).apply()

    var isLiveSharing: Boolean
        get() = prefs.getBoolean("is_live_sharing", false)
        set(value) = prefs.edit().putBoolean("is_live_sharing", value).apply()

    var liveExpiresAt: Long
        get() = prefs.getLong("live_expires_at", 0L)
        set(value) = prefs.edit().putLong("live_expires_at", value).apply()

    var liveDurationMinutes: Int
        get() = prefs.getInt("live_duration_mins", 120)
        set(value) = prefs.edit().putInt("live_duration_mins", value).apply()

    var activeNightDate: String
        get() = prefs.getString("active_night_date", "") ?: ""
        set(value) = prefs.edit().putString("active_night_date", value).apply()

    var lastSeenCheerAt: String
        get() = prefs.getString("last_seen_cheer_at", "") ?: ""
        set(value) = prefs.edit().putString("last_seen_cheer_at", value).apply()

    // Last known venue/status, persisted so the background LiveShareService can keep
    // syncing them even when the check-in screen isn't open.
    var liveVenueName: String
        get() = prefs.getString("live_venue_name", "") ?: ""
        set(value) = prefs.edit().putString("live_venue_name", value).apply()

    var liveStatusMessage: String
        get() = prefs.getString("live_status_message", "Partying at Venue 🍸") ?: "Partying at Venue 🍸"
        set(value) = prefs.edit().putString("live_status_message", value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("is_onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("is_onboarding_completed", value).apply()

    var appLanguageCode: String
        get() = prefs.getString("app_language_code", "en") ?: "en"
        set(value) = prefs.edit().putString("app_language_code", value).apply()

    var appLanguageName: String
        get() = prefs.getString("app_language_name", "English") ?: "English"
        set(value) = prefs.edit().putString("app_language_name", value).apply()

    var isAdFreeSubscribed: Boolean
        get() = prefs.getBoolean("is_ad_free_subscribed", false)
        set(value) = prefs.edit().putBoolean("is_ad_free_subscribed", value).apply()

    fun resetNewSessionId(): String {
        val newId = "tb-" + UUID.randomUUID().toString().substring(0, 6)
        liveSessionId = newId
        return newId
    }
}
