package com.tipsybuddy.app.translation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.tipsybuddy.app.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class LanguageItem(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flagEmoji: String = ""
)

sealed interface ModelDownloadStatus {
    data object Idle : ModelDownloadStatus
    data class Downloading(val progressMessage: String = "Downloading offline ML model (~30MB)...") : ModelDownloadStatus
    data object Ready : ModelDownloadStatus
    data class Error(val message: String) : ModelDownloadStatus
}

class AppTranslationManager private constructor(private val context: Context) {

    private val userPrefs = UserPreferences(context)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // Reactive Compose states
    private val _currentLanguageState = mutableStateOf(userPrefs.appLanguageCode)
    val currentLanguageState: State<String> = _currentLanguageState

    private val _versionState = mutableIntStateOf(0)
    val versionState: State<Int> = _versionState

    private val _downloadStatus = mutableStateOf<ModelDownloadStatus>(ModelDownloadStatus.Idle)
    val downloadStatus: State<ModelDownloadStatus> = _downloadStatus

    // Active translator instance (null when English)
    private var activeTranslator: Translator? = null
    private var activeTargetLang: String = "en"

    // In-memory cache for fast lookups
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val pendingRequests = ConcurrentHashMap.newKeySet<String>()

    private var diskCachePrefs: SharedPreferences = context.getSharedPreferences(
        "tipsy_trans_${userPrefs.appLanguageCode}",
        Context.MODE_PRIVATE
    )

    init {
        val initialLang = userPrefs.appLanguageCode
        if (initialLang != "en") {
            setupTranslator(initialLang, autoDownload = false)
        }
    }

    /**
     * Top popular / recommended languages for quick chips
     */
    val popularLanguages: List<LanguageItem> = listOf(
        LanguageItem("en", "English", "English", "🇺🇸"),
        LanguageItem("es", "Spanish", "Español", "🇪🇸"),
        LanguageItem("fr", "French", "Français", "🇫🇷"),
        LanguageItem("de", "German", "Deutsch", "🇩🇪"),
        LanguageItem("pt", "Portuguese", "Português", "🇧🇷"),
        LanguageItem("it", "Italian", "Italiano", "🇮🇹"),
        LanguageItem("zh", "Chinese", "中文", "🇨🇳"),
        LanguageItem("ja", "Japanese", "日本語", "🇯🇵"),
        LanguageItem("ko", "Korean", "한국어", "🇰🇷"),
        LanguageItem("hi", "Hindi", "हिन्दी", "🇮🇳"),
        LanguageItem("ar", "Arabic", "العربية", "🇸🇦"),
        LanguageItem("ru", "Russian", "Русский", "🇷🇺"),
        LanguageItem("nl", "Dutch", "Nederlands", "🇳🇱"),
        LanguageItem("pl", "Polish", "Polski", "🇵🇱"),
        LanguageItem("tr", "Turkish", "Türkçe", "🇹🇷"),
        LanguageItem("vi", "Vietnamese", "Tiếng Việt", "🇻🇳"),
        LanguageItem("sv", "Swedish", "Svenska", "🇸🇪"),
        LanguageItem("uk", "Ukrainian", "Українська", "🇺🇦"),
        LanguageItem("id", "Indonesian", "Bahasa Indonesia", "🇮🇩"),
        LanguageItem("el", "Greek", "Ελληνικά", "🇬🇷")
    )

    /**
     * All languages supported by Google ML Kit
     */
    val allSupportedLanguages: List<LanguageItem> by lazy {
        try {
            val popularCodes = popularLanguages.map { it.code }.toSet()
            val mlKitCodes = TranslateLanguage.getAllLanguages()
            val extraList = mlKitCodes
                .filterNot { popularCodes.contains(it) }
                .map { code ->
                    val locale = Locale(code)
                    val disp = locale.getDisplayLanguage(Locale.ENGLISH).replaceFirstChar { it.uppercase() }
                    val native = locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase() }
                    LanguageItem(
                        code = code,
                        displayName = if (disp.isNotBlank()) disp else code.uppercase(),
                        nativeName = if (native.isNotBlank()) native else disp,
                        flagEmoji = "🌐"
                    )
                }
                .sortedBy { it.displayName }
            popularLanguages + extraList
        } catch (e: Exception) {
            popularLanguages
        }
    }

    fun getLanguageItem(code: String): LanguageItem {
        return allSupportedLanguages.find { it.code.equals(code, ignoreCase = true) }
            ?: LanguageItem(code, code.uppercase(), code.uppercase(), "🌐")
    }

    /**
     * Select a language and download the offline ML model if needed.
     */
    fun selectLanguage(
        item: LanguageItem,
        onStatusChange: (ModelDownloadStatus) -> Unit = {},
        onFinished: (Boolean) -> Unit = {}
    ) {
        userPrefs.appLanguageCode = item.code
        userPrefs.appLanguageName = item.displayName
        _currentLanguageState.value = item.code

        diskCachePrefs = context.getSharedPreferences("tipsy_trans_${item.code}", Context.MODE_PRIVATE)
        memoryCache.clear()
        pendingRequests.clear()

        if (item.code == "en") {
            activeTranslator?.close()
            activeTranslator = null
            activeTargetLang = "en"
            _downloadStatus.value = ModelDownloadStatus.Ready
            onStatusChange(ModelDownloadStatus.Ready)
            _versionState.intValue++
            onFinished(true)
            return
        }

        setupTranslator(item.code, autoDownload = true, onStatusChange = onStatusChange, onFinished = onFinished)
    }

    private fun setupTranslator(
        targetCode: String,
        autoDownload: Boolean,
        onStatusChange: (ModelDownloadStatus) -> Unit = {},
        onFinished: (Boolean) -> Unit = {}
    ) {
        val mlKitLang = TranslateLanguage.fromLanguageTag(targetCode)
        if (mlKitLang == null) {
            val err = "Language $targetCode is not supported by ML Kit"
            _downloadStatus.value = ModelDownloadStatus.Error(err)
            onStatusChange(ModelDownloadStatus.Error(err))
            onFinished(false)
            return
        }

        activeTranslator?.close()
        activeTargetLang = targetCode

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(mlKitLang)
            .build()

        val translator = Translation.getClient(options)
        activeTranslator = translator

        if (autoDownload) {
            _downloadStatus.value = ModelDownloadStatus.Downloading()
            onStatusChange(ModelDownloadStatus.Downloading())

            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    _downloadStatus.value = ModelDownloadStatus.Ready
                    onStatusChange(ModelDownloadStatus.Ready)
                    _versionState.intValue++
                    preTranslateCommonPhrases(translator, targetCode)
                    onFinished(true)
                }
                .addOnFailureListener { exception ->
                    val msg = exception.localizedMessage ?: "Failed to download model"
                    Log.e(TAG, "ML Kit model download failed", exception)
                    _downloadStatus.value = ModelDownloadStatus.Error(msg)
                    onStatusChange(ModelDownloadStatus.Error(msg))
                    onFinished(false)
                }
        } else {
            // Already downloaded or will check lazily
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    _downloadStatus.value = ModelDownloadStatus.Ready
                    _versionState.intValue++
                }
                .addOnFailureListener {
                    // Offline or not downloaded yet
                }
        }
    }

    /**
     * Synchronous translate lookup with asynchronous ML Kit fallback & caching
     */
    fun translate(sourceText: String): String {
        if (activeTargetLang == "en" || sourceText.isBlank()) {
            return sourceText
        }

        // 1. In-memory cache
        val memHit = memoryCache[sourceText]
        if (memHit != null) return memHit

        // 2. Persistent SharedPreferences cache
        val diskHit = diskCachePrefs.getString(sourceText, null)
        if (diskHit != null) {
            memoryCache[sourceText] = diskHit
            return diskHit
        }

        // 3. Queue on-device translation
        val translator = activeTranslator
        if (translator != null && pendingRequests.add(sourceText)) {
            translator.translate(sourceText)
                .addOnSuccessListener { translatedText ->
                    if (translatedText.isNotBlank()) {
                        memoryCache[sourceText] = translatedText
                        diskCachePrefs.edit().putString(sourceText, translatedText).apply()
                        _versionState.intValue++
                    }
                    pendingRequests.remove(sourceText)
                }
                .addOnFailureListener {
                    pendingRequests.remove(sourceText)
                }
        }

        return sourceText
    }

    /**
     * Batch translates key app phrases in background to immediately prime the cache
     */
    private fun preTranslateCommonPhrases(translator: Translator, langCode: String) {
        scope.launch {
            val keyPhrases = listOf(
                "Tonight", "Log", "Calendar", "Health", "Live Share", "Rides", "Profile",
                "EST. BAC", "Sober in", "Tonight's Tab", "Hydration", "Check In",
                "Call Uber", "Call Lyft", "Log Drink", "Quick Add", "Custom Drink",
                "Beer", "Wine", "Liquor", "Cocktail", "Shot", "Seltzer", "Cider", "Other",
                "Water", "Standard Drinks", "Add Drink", "Drink History", "Volume (oz)",
                "ABV (%)", "Price ($)", "Save", "Cancel", "Delete", "Confirm", "Search",
                "Safe Ride & Transit", "Call Emergency Contact", "Take Me Home",
                "Profile & Settings", "Weight", "Gender", "Home Address", "Emergency Contact",
                "App Language", "Change Language", "Revisit App Tour", "Clear All Data",
                "Wear OS Smartwatch Companion", "Home Screen Widget & Safety", "Get Started",
                "Choose Your Language", "Meet Your Drinking Buddy", "Smart BAC & Hydration",
                "Next", "Back", "Skip", "Ready!", "Sober", "Mild Buzz", "Tipsy Zone",
                "High Risk", "Danger Zone", "Current Session", "Total Spent"
            )

            for (phrase in keyPhrases) {
                if (memoryCache.containsKey(phrase) || diskCachePrefs.contains(phrase)) continue
                try {
                    translator.translate(phrase)
                        .addOnSuccessListener { result ->
                            if (result.isNotBlank()) {
                                memoryCache[phrase] = result
                                diskCachePrefs.edit().putString(phrase, result).apply()
                            }
                        }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    /**
     * Shuts down the translation manager and cancels all background coroutines.
     */
    fun close() {
        job.cancel()
        activeTranslator?.close()
        activeTranslator = null
    }

    companion object {
        private const val TAG = "AppTranslationManager"

        @Volatile
        private var INSTANCE: AppTranslationManager? = null

        fun initialize(context: Context): AppTranslationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppTranslationManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getInstance(context: Context? = null): AppTranslationManager {
            val existing = INSTANCE
            if (existing != null) return existing
            return synchronized(this) {
                INSTANCE ?: if (context != null) {
                    AppTranslationManager(context.applicationContext).also { INSTANCE = it }
                } else {
                    throw IllegalStateException("AppTranslationManager has not been initialized. Call initialize(context) in Application.onCreate() first.")
                }
            }
        }
    }
}
