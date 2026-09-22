package com.hartmann.crosspromo.analytics

import com.hartmann.crosspromo.model.PromoAnalyticsEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Analytics interface. Host apps pick an implementation:
 * - [NoOpAnalytics] for opt-out builds
 * - [BackendAnalyticsAdapter] posts to the cross-promo backend (default)
 * - [FirebaseAnalyticsAdapter] forwards to an existing FirebaseAnalytics
 *   instance via a lambda, so this SDK has NO Firebase dependency.
 */
interface CrossPromoAnalytics {
    fun impression(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    )

    fun click(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    )
}

object NoOpAnalytics : CrossPromoAnalytics {
    override fun impression(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    ) = Unit

    override fun click(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    ) = Unit
}

/**
 * Forwards cross-promo events to Firebase Analytics when the HOST app
 * already uses Firebase. Pass `firebaseAnalytics::logEvent`-shaped lambda:
 *
 *     FirebaseAnalyticsAdapter { name, params -> firebaseAnalytics.logEvent(name, params.toBundle()) }
 *
 * Event names: `crosspromo_impression`, `crosspromo_click`.
 */
class FirebaseAnalyticsAdapter(
    private val logEvent: (name: String, params: Map<String, Any?>) -> Unit,
) : CrossPromoAnalytics {
    override fun impression(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    ) = logEvent("crosspromo_impression", bundleOf(sourcePackage, targetPackage, placement, rankPosition, selectionType, sessionId, recommendationRequestId, sdkVersion))

    override fun click(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    ) = logEvent("crosspromo_click", bundleOf(sourcePackage, targetPackage, placement, rankPosition, selectionType, sessionId, recommendationRequestId, sdkVersion))

    private fun bundleOf(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    ): Map<String, Any?> = mapOf(
        "source_package" to sourcePackage,
        "target_package" to targetPackage,
        "placement" to placement,
        "rank_position" to rankPosition,
        "selection_type" to (selectionType ?: "unknown"),
        "session_id" to sessionId,
        "recommendation_request_id" to recommendationRequestId,
        "sdk_version" to sdkVersion,
    )
}

/**
 * Posts events to the backend (batched, background thread, best-effort).
 * Failures are swallowed: analytics must never break the host app.
 */
class BackendAnalyticsAdapter(
    private val post: suspend (List<PromoAnalyticsEvent>) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : CrossPromoAnalytics {

    override fun impression(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    ) {
        send(
            PromoAnalyticsEvent(
                event = "promo_impression",
                eventId = UUID.randomUUID().toString(),
                sourcePackage = sourcePackage,
                targetPackage = targetPackage,
                placement = placement,
                timestamp = utcNow(),
                sessionId = sessionId,
                rankPosition = rankPosition,
                selectionType = selectionType,
                recommendationRequestId = recommendationRequestId,
                sdkVersion = sdkVersion,
            ),
        )
    }

    override fun click(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    ) {
        send(
            PromoAnalyticsEvent(
                event = "promo_click",
                eventId = UUID.randomUUID().toString(),
                sourcePackage = sourcePackage,
                targetPackage = targetPackage,
                placement = placement,
                timestamp = utcNow(),
                sessionId = sessionId,
                rankPosition = rankPosition,
                selectionType = selectionType,
                recommendationRequestId = recommendationRequestId,
                sdkVersion = sdkVersion,
            ),
        )
    }

    private fun send(event: PromoAnalyticsEvent) {
        scope.launch {
            try {
                post(listOf(event))
            } catch (_: Exception) {
                // Swallowed by design.
            }
        }
    }

    companion object {
        fun utcNow(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.format(Date())
        }
    }
}

/** Fan-out to several analytics implementations at once. */
class CompositeAnalytics(private val delegates: List<CrossPromoAnalytics>) : CrossPromoAnalytics {
    override fun impression(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    ) = delegates.forEach {
        try {
            it.impression(sourcePackage, targetPackage, placement, rankPosition, selectionType, sessionId, recommendationRequestId, sdkVersion)
        } catch (_: Exception) {
        }
    }

    override fun click(
        sourcePackage: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int,
        selectionType: String?,
        sessionId: String?,
        recommendationRequestId: String?,
        sdkVersion: String,
    ) = delegates.forEach {
        try {
            it.click(sourcePackage, targetPackage, placement, rankPosition, selectionType, sessionId, recommendationRequestId, sdkVersion)
        } catch (_: Exception) {
        }
    }
}
