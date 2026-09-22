package com.tipsybuddy.app.data.feedback

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Max decoded image size accepted for upload (~8MB to match the Worker limit). */
const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

/**
 * Converts a content [Uri] to a Base64 (NO_WRAP) string safely:
 * closes streams, enforces a size cap, surfaces useful errors.
 */
fun uriToBase64(context: Context, uri: Uri): Result<String> {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > MAX_IMAGE_BYTES) {
                    return Result.failure(IllegalArgumentException("Image is too large (max ~8MB)."))
                }
                buffer.write(chunk, 0, read)
            }
            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("Selected image is empty."))
            }
            Result.success(Base64.encodeToString(bytes, Base64.NO_WRAP))
        } ?: Result.failure(IllegalArgumentException("Unable to open selected image."))
    } catch (e: Exception) {
        Result.failure(IllegalArgumentException("Unable to read image: ${e.message}"))
    }
}

/** Guess a file extension from the content resolver MIME type (defaults to png). */
fun extensionForUri(context: Context, uri: Uri): String {
    val mime = try {
        context.contentResolver.getType(uri)
    } catch (_: Exception) {
        null
    }
    return when (mime?.lowercase(Locale.US)) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/png" -> "png"
        else -> {
            val path = uri.lastPathSegment.orEmpty().lowercase(Locale.US)
            when {
                path.endsWith(".jpg") || path.endsWith(".jpeg") -> "jpg"
                path.endsWith(".webp") -> "webp"
                else -> "png"
            }
        }
    }
}

/** Generates a safe unique attachment filename, e.g. issue-20260921-214501-a8f3.png */
fun uniqueIssueFileName(ext: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val rand = UUID.randomUUID().toString().substring(0, 4)
    return "issue-$stamp-$rand.${ext.lowercase(Locale.US)}"
}

/** Generates a safe unique comment attachment filename. */
fun uniqueCommentFileName(issueNumber: Int, ext: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val rand = UUID.randomUUID().toString().substring(0, 4)
    return "comment-$issueNumber-$stamp-$rand.${ext.lowercase(Locale.US)}"
}

/** Builds the Markdown issue body from form fields + optional attachment/diagnostics. */
fun buildIssueBody(
    description: String,
    name: String,
    email: String,
    attachmentUrl: String?,
    diagnostics: String?,
): String = buildString {
    appendLine("## Description")
    appendLine()
    appendLine(description.trim())
    val cleanName = name.trim()
    val cleanEmail = email.trim()
    if (cleanName.isNotBlank() || cleanEmail.isNotBlank()) {
        appendLine()
        appendLine("## Contact Info")
        appendLine()
        appendLine("- Name: ${cleanName.ifBlank { "Not provided" }}")
        appendLine("- Email: ${cleanEmail.ifBlank { "Not provided" }}")
    }
    if (!attachmentUrl.isNullOrBlank()) {
        appendLine()
        appendLine("## Attachment")
        appendLine()
        appendLine("![Screenshot]($attachmentUrl)")
    }
    if (!diagnostics.isNullOrBlank()) {
        appendLine()
        append(diagnostics.trim())
    }
}.trimEnd() + "\n"

/** Builds the Markdown comment body from reply text + optional attachment. */
fun buildCommentBody(reply: String, attachmentUrl: String?): String = buildString {
    appendLine("## Reply")
    appendLine()
    appendLine(reply.trim())
    if (!attachmentUrl.isNullOrBlank()) {
        appendLine()
        appendLine("## Attachment")
        appendLine()
        appendLine("![Screenshot]($attachmentUrl)")
    }
}.trimEnd() + "\n"
