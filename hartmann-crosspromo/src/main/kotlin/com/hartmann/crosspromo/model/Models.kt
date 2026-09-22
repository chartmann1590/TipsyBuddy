package com.hartmann.crosspromo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One promoted app. Unknown JSON properties are ignored so the backend can
 * add fields without breaking installed clients (forward compatibility).
 */
@Serializable
data class PromoApp(
    @SerialName("packageName") val packageName: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("iconUrl") val iconUrl: String? = null,
    @SerialName("shortDescription") val shortDescription: String? = null,
    @SerialName("rating") val rating: Float? = null,
    @SerialName("ratingCount") val ratingCount: Long? = null,
    @SerialName("installText") val installText: String? = null,
    @SerialName("storeUrl") val storeUrl: String = "",
    @SerialName("selectionType") val selectionType: String? = null,
)

@Serializable
data class PromoResponse(
    @SerialName("version") val version: Int = 1,
    @SerialName("requestId") val requestId: String? = null,
    @SerialName("generatedAt") val generatedAt: String? = null,
    @SerialName("expiresAt") val expiresAt: String? = null,
    @SerialName("apps") val apps: List<PromoApp> = emptyList(),
)

/** Analytics event payload sent to POST /api/v1/events. */
@Serializable
data class PromoAnalyticsEvent(
    @SerialName("event") val event: String,
    @SerialName("eventId") val eventId: String? = null,
    @SerialName("sourcePackage") val sourcePackage: String,
    @SerialName("targetPackage") val targetPackage: String,
    @SerialName("placement") val placement: String,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("rankPosition") val rankPosition: Int? = null,
    @SerialName("selectionType") val selectionType: String? = null,
    @SerialName("recommendationRequestId") val recommendationRequestId: String? = null,
    @SerialName("sdkVersion") val sdkVersion: String? = null,
)

@Serializable
data class PromoEventsBatch(
    @SerialName("events") val events: List<PromoAnalyticsEvent>,
)
