package com.tipsybuddy.app.translation

import androidx.compose.runtime.compositionLocalOf

val LocalAppTranslationManager = compositionLocalOf<AppTranslationManager> {
    AppTranslationManager.getInstance()
}

/**
 * Translates a given text using the singleton AppTranslationManager.
 * Standard Kotlin function so it can be called safely in any Compose or non-Compose context.
 */
fun tr(text: String): String {
    if (text.isBlank()) return text
    return try {
        AppTranslationManager.getInstance().translate(text)
    } catch (e: Exception) {
        text
    }
}

/**
 * Translates a format string and formats it with the provided arguments.
 */
fun trFormat(format: String, vararg args: Any): String {
    val translatedFormat = com.tipsybuddy.app.translation.tr(format)
    return try {
        if (args.isNotEmpty()) {
            String.format(translatedFormat, *args)
        } else {
            translatedFormat
        }
    } catch (e: Exception) {
        translatedFormat
    }
}

/**
 * Extension helper for quick translation of string literals.
 * Example: "Tonight".tr()
 */
@JvmName("trString")
fun String.tr(): String {
    if (this.isBlank()) return this
    return try {
        AppTranslationManager.getInstance().translate(this)
    } catch (e: Exception) {
        this
    }
}

/**
 * Extension helper for formatted translation strings.
 * Example: "Sober in %d mins".tr(mins)
 */
@JvmName("trStringFormat")
fun String.tr(vararg args: Any): String {
    val translatedFormat = try {
        AppTranslationManager.getInstance().translate(this)
    } catch (e: Exception) {
        this
    }
    return try {
        if (args.isNotEmpty()) {
            String.format(translatedFormat, *args)
        } else {
            translatedFormat
        }
    } catch (e: Exception) {
        translatedFormat
    }
}
