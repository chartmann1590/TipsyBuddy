package com.hartmann.crosspromo.api

import com.hartmann.crosspromo.model.PromoAnalyticsEvent
import com.hartmann.crosspromo.model.PromoEventsBatch
import com.hartmann.crosspromo.model.PromoResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json

/**
 * Minimal HTTP client for the cross-promo backend.
 *
 * Uses HttpURLConnection on purpose: zero third-party networking dependency,
 * so any Hartmann Studios app can adopt the SDK without inheriting OkHttp /
 * Retrofit version constraints. Payloads are tiny JSON; all I/O runs on
 * Dispatchers.IO via the repository — never on the main thread.
 */
internal class CrossPromoApi(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 8_000,
) {
    init {
        require(baseUrl.trim().startsWith("https://")) {
            "baseUrl must use https://"
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true // forward compatibility with backend additions
        isLenient = true
        coerceInputValues = true
    }

    data class RecommendationResult(
        val response: PromoResponse?,
        /** Raw body preserved so the repository can refresh its cache verbatim. */
        val rawBody: String?,
    )

    fun getRecommendations(params: Map<String, String>): RecommendationResult {
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val conn = openGet("${baseUrl.trimEnd('/')}/api/v1/recommendations?$query")
        return try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) return RecommendationResult(null, null)
            val body = readFully(conn)
            RecommendationResult(parseResponse(body), body)
        } catch (_: Exception) {
            RecommendationResult(null, null) // network failures are never fatal
        } finally {
            conn.disconnect()
        }
    }

    fun postEvents(events: List<PromoAnalyticsEvent>): Boolean {
        if (events.isEmpty()) return true
        val conn = try {
            openPost("${baseUrl.trimEnd('/')}/api/v1/events")
        } catch (_: Exception) {
            return false
        }
        return try {
            val payload = json.encodeToString(
                PromoEventsBatch.serializer(),
                PromoEventsBatch(events),
            )
            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    /** Lenient parse: malformed bodies yield null instead of throwing. */
    fun parseResponse(body: String?): PromoResponse? {
        if (body.isNullOrBlank()) return null
        return try {
            val parsed = json.decodeFromString(PromoResponse.serializer(), body)
            // Basic sanity: drop entries without an identity.
            parsed.copy(apps = parsed.apps.filter { it.packageName.isNotBlank() })
        } catch (_: Exception) {
            null
        }
    }

    private fun openGet(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        return conn
    }

    private fun openPost(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        return conn
    }

    private fun readFully(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }
}
