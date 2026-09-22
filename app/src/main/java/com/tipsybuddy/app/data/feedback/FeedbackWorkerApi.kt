package com.tipsybuddy.app.data.feedback

import android.content.pm.PackageManager
import com.tipsybuddy.app.BuildConfig
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the Cloudflare feedback Worker.
 *
 * Base URL is [BuildConfig.FEEDBACK_WORKER_URL] (non-secret). The app never
 * talks to GitHub directly and never holds a GitHub token — repository routing
 * and `Authorization: Bearer <GITHUB_TOKEN>` live inside the Worker.
 */
@OptIn(ExperimentalSerializationApi::class)
class FeedbackWorkerApi(
    val workerUrl: String = BuildConfig.FEEDBACK_WORKER_URL.trim().trimEnd('/'),
    client: OkHttpClient? = null,
) {
    val isConfigured: Boolean = isFeedbackConfigured(workerUrl)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val appVersion: String by lazy {
        try {
            val pkg = FeedbackWorkerApi::class.java.`package`?.name ?: "com.tipsybuddy.app"
            pkg
        } catch (_: Exception) {
            "com.tipsybuddy.app"
        }
    }

    private val http: OkHttpClient by lazy {
        client ?: OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    private fun userAgent(): String {
        val version = try {
            BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            "unknown"
        }
        return "TipsyBuddy-Android/$version"
    }

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val message: String, val httpCode: Int? = null) : Result<Nothing>()
    }

    private fun requireConfigured(): Result.Err? =
        if (!isConfigured) {
            Result.Err("Feedback service is not configured for this build.")
        } else {
            null
        }

    private inline fun <reified T> parseBody(raw: String): T? =
        try {
            json.decodeFromString<T>(raw)
        } catch (_: Exception) {
            null
        }

    private fun errorMessage(code: Int, raw: String): String {
        parseBody<ApiError>(raw)?.error?.takeIf { it.isNotBlank() }?.let { return it }
        return when (code) {
            400 -> "Invalid request. Please check your input."
            404 -> "Report not found on GitHub."
            405 -> "Unsupported operation."
            413 -> "Attachment or text is too large."
            429 -> "Too many requests. Please try again later."
            in 500..599 -> "Feedback service is temporarily unavailable."
            else -> "Request failed (HTTP $code)."
        }
    }

    private fun post(path: String, payload: String): Result<String> {
        requireConfigured()?.let { return it }
        return try {
            val request = Request.Builder()
                .url("$workerUrl/$path".replace("//api", "/api"))
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent())
                .build()
            http.newCall(request).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (res.isSuccessful) Result.Ok(raw)
                else Result.Err(errorMessage(res.code, raw), res.code)
            }
        } catch (e: Exception) {
            Result.Err("Network error: ${e.message ?: "unable to reach feedback service."}")
        }
    }

    private fun get(path: String): Result<String> {
        requireConfigured()?.let { return it }
        return try {
            val request = Request.Builder()
                .url("$workerUrl/$path".replace("//api", "/api"))
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", userAgent())
                .build()
            http.newCall(request).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (res.isSuccessful) Result.Ok(raw)
                else Result.Err(errorMessage(res.code, raw), res.code)
            }
        } catch (e: Exception) {
            Result.Err("Network error: ${e.message ?: "unable to reach feedback service."}")
        }
    }

    fun createIssue(title: String, body: String): Result<CreatedIssueResponse> {
        val payload = json.encodeToString(
            CreateIssueRequest.serializer(),
            CreateIssueRequest(title = title, body = body),
        )
        return when (val r = post("api/issues", payload)) {
            is Result.Ok -> {
                val parsed = parseBody<CreatedIssueResponse>(r.value)
                    ?: return Result.Err("Unexpected response from feedback service.")
                Result.Ok(parsed)
            }
            is Result.Err -> r
        }
    }

    fun getIssue(number: Int): Result<FeedbackIssue> {
        if (number < 1) return Result.Err("Invalid issue number.")
        return when (val r = get("api/issues/$number")) {
            is Result.Ok -> {
                val parsed = parseBody<FeedbackIssue>(r.value)
                    ?: return Result.Err("Unexpected response from feedback service.")
                Result.Ok(parsed)
            }
            is Result.Err -> r
        }
    }

    fun getComments(number: Int): Result<List<FeedbackComment>> {
        if (number < 1) return Result.Err("Invalid issue number.")
        return when (val r = get("api/issues/$number/comments")) {
            is Result.Ok -> {
                val parsed = try {
                    json.decodeFromString(ListSerializer(FeedbackComment.serializer()), r.value)
                } catch (_: Exception) {
                    return Result.Err("Unexpected response from feedback service.")
                }
                Result.Ok(parsed)
            }
            is Result.Err -> r
        }
    }

    fun postComment(number: Int, body: String): Result<FeedbackComment> {
        if (number < 1) return Result.Err("Invalid issue number.")
        if (body.isBlank()) return Result.Err("Reply cannot be empty.")
        val payload = json.encodeToString(
            PostCommentRequest.serializer(),
            PostCommentRequest(body = body),
        )
        return when (val r = post("api/issues/$number/comments", payload)) {
            is Result.Ok -> {
                val parsed = parseBody<FeedbackComment>(r.value)
                    ?: return Result.Err("Unexpected response from feedback service.")
                Result.Ok(parsed)
            }
            is Result.Err -> r
        }
    }

    fun uploadAsset(fileName: String, contentBase64: String): Result<UploadAssetResponse> {
        if (fileName.isBlank() || contentBase64.isBlank()) {
            return Result.Err("Attachment is missing.")
        }
        val payload = json.encodeToString(
            UploadAssetRequest.serializer(),
            UploadAssetRequest(fileName = fileName, contentBase64 = contentBase64),
        )
        return when (val r = post("api/assets", payload)) {
            is Result.Ok -> {
                val parsed = parseBody<UploadAssetResponse>(r.value)
                    ?: return Result.Err("Unexpected response from feedback service.")
                Result.Ok(parsed)
            }
            is Result.Err -> r
        }
    }

    companion object {
        /** App version label for diagnostics, resolved with a PackageManager when available. */
        fun appVersionLabel(pm: PackageManager?, packageName: String): Pair<String, Long> {
            if (pm == null) return Pair(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
            return try {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageName, 0)
                @Suppress("DEPRECATION")
                val code = info.versionCode.toLong()
                Pair(info.versionName ?: BuildConfig.VERSION_NAME, code)
            } catch (_: Exception) {
                Pair(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
            }
        }
    }
}
