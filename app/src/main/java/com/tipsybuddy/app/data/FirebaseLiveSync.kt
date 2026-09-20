package com.tipsybuddy.app.data

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class FirebaseLiveSync(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun getBatteryLevel(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
        } catch (e: Exception) {
            85
        }
    }

    suspend fun syncSession(
        sessionId: String,
        userName: String,
        venue: String,
        status: String,
        latitude: Double,
        longitude: Double,
        bac: Double,
        homeAddress: String,
        phone: String,
        expiresAtMillis: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val battery = getBatteryLevel()
            val expiresAtIso = if (expiresAtMillis > 0) isoFormat.format(Date(expiresAtMillis)) else isoFormat.format(Date(System.currentTimeMillis() + 7200000))
            val updatedAtIso = isoFormat.format(Date())

            val jsonBody = JSONObject().apply {
                val fields = JSONObject().apply {
                    put("userName", JSONObject().put("stringValue", userName))
                    put("venue", JSONObject().put("stringValue", venue))
                    put("status", JSONObject().put("stringValue", status))
                    put("latitude", JSONObject().put("doubleValue", latitude))
                    put("longitude", JSONObject().put("doubleValue", longitude))
                    put("bac", JSONObject().put("doubleValue", bac))
                    put("battery", JSONObject().put("integerValue", battery))
                    put("homeAddress", JSONObject().put("stringValue", homeAddress))
                    put("phone", JSONObject().put("stringValue", phone))
                    put("expiresAt", JSONObject().put("stringValue", expiresAtIso))
                    put("updatedAt", JSONObject().put("stringValue", updatedAtIso))
                }
                put("fields", fields)
            }

            val url = "https://firestore.googleapis.com/v1/projects/party-quips-2026/databases/(default)/documents/sessions/${sessionId}"
            val request = Request.Builder()
                .url(url)
                .patch(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val isSuccess = response.isSuccessful
                if (!isSuccess) {
                    Log.w("TipsyLiveSync", "Firestore sync failed: ${response.code} ${response.message}")
                }
                isSuccess
            }
        } catch (e: Exception) {
            Log.e("TipsyLiveSync", "Exception syncing live location", e)
            false
        }
    }
}
