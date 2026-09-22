package com.hartmann.crosspromo

import android.app.Application
import com.hartmann.crosspromo.analytics.BackendAnalyticsAdapter
import com.hartmann.crosspromo.analytics.CompositeAnalytics
import com.hartmann.crosspromo.analytics.CrossPromoAnalytics
import com.hartmann.crosspromo.analytics.NoOpAnalytics
import com.hartmann.crosspromo.api.CrossPromoApi
import com.hartmann.crosspromo.cache.PromoCache
import com.hartmann.crosspromo.repository.CrossPromoRepository
import com.hartmann.crosspromo.util.ImpressionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Entry point. Initialize once from `Application.onCreate`:
 *
 *     HartmannCrossPromo.initialize(
 *         application = this,
 *         apiBaseUrl = BuildConfig.CROSS_PROMO_URL,
 *     )
 *
 * The source package is detected automatically via
 * `context.packageName` — never type it by hand. Afterwards drop
 * [HartmannCrossPromoRow][com.hartmann.crosspromo.ui.HartmannCrossPromoRow]
 * anywhere in Compose, or
 * [HartmannPromoView][com.hartmann.crosspromo.ui.HartmannPromoView] in XML.
 */
object HartmannCrossPromo {

    const val SDK_VERSION = "1.0.0"

    @Volatile
    private var holder: Holder? = null

    val isInitialized: Boolean get() = holder != null

    /**
     * @param apiBaseUrl e.g. "https://hartmann-crosspromo-api.<you>.workers.dev"
     * @param analytics extra sinks (Firebase adapter, …). The backend sink is
     *   always included unless [analytics] is [NoOpAnalytics] alone — pass
     *   [NoOpAnalytics] via [disableBackendAnalytics] to fully opt out.
     */
    fun initialize(
        application: Application,
        apiBaseUrl: String,
        analytics: CrossPromoAnalytics? = null,
        disableBackendAnalytics: Boolean = false,
    ) {
        require(apiBaseUrl.isNotBlank()) { "apiBaseUrl must not be blank" }
        val appContext = application.applicationContext
        val sourcePackage = appContext.packageName
        val api = CrossPromoApi(apiBaseUrl)
        val cache = PromoCache(appContext)
        val repository = CrossPromoRepository(api, cache, sourcePackage, SDK_VERSION)
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val backend = BackendAnalyticsAdapter(post = { events -> api.postEvents(events) }, scope = ioScope)
        val sink: CrossPromoAnalytics = when {
            disableBackendAnalytics && analytics == null -> NoOpAnalytics
            disableBackendAnalytics -> analytics ?: NoOpAnalytics
            analytics == null -> backend
            else -> CompositeAnalytics(listOf(backend, analytics))
        }
        holder = Holder(
            sourcePackage = sourcePackage,
            repository = repository,
            analytics = sink,
            api = api,
            ioScope = ioScope,
        )
    }

    internal fun requireHolder(): Holder =
        holder ?: error("HartmannCrossPromo.initialize(...) must be called first")

    /** For tests / previews only. */
    internal fun resetForTests() {
        holder = null
    }

    internal data class Holder(
        val sourcePackage: String,
        val repository: CrossPromoRepository,
        val analytics: CrossPromoAnalytics,
        val api: CrossPromoApi,
        val ioScope: CoroutineScope,
        /** Short-lived random session UUID (per process). No identity, no ad IDs. */
        val processSessionId: String = java.util.UUID.randomUUID().toString(),
    )

    internal fun impressionTracker(): ImpressionTracker =
        ImpressionTrackerHolder.tracker

    private object ImpressionTrackerHolder {
        val tracker = ImpressionTracker()
    }

    /** Fire-and-forget event flush helper for UI layers. */
    internal fun track(block: suspend () -> Unit) {
        val h = holder ?: return
        h.ioScope.launch {
            try {
                block()
            } catch (_: Exception) {
            }
        }
    }
}
