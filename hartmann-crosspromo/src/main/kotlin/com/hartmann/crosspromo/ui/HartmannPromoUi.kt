package com.hartmann.crosspromo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hartmann.crosspromo.HartmannCrossPromo
import com.hartmann.crosspromo.R
import com.hartmann.crosspromo.launcher.PlayStoreLauncher
import com.hartmann.crosspromo.model.PromoApp
import com.hartmann.crosspromo.repository.CrossPromoRepository

/**
 * "More from Hartmann Studios" section. Safe defaults: a labeled horizontal
 * carousel of 2–3 cards, no popups, no forced interaction. Renders nothing
 * while loading / on failure / when disabled remotely — cross-promo never
 * blocks host content.
 *
 * Place it on settings/about screens, the bottom of home, or a dedicated
 * "More Apps" section. An impression is counted once per card when it first
 * becomes part of the composition (meaningfully visible in a LazyRow item).
 */
@Composable
fun HartmannCrossPromoRow(
    placement: String,
    modifier: Modifier = Modifier,
    limit: Int = 3,
    title: String = "More from Hartmann Studios",
) {
    if (!HartmannCrossPromo.isInitialized) return
    val holder = remember { HartmannCrossPromo.requireHolder() }
    val flow = remember(placement, limit) { holder.repository.observe(placement, limit.coerceIn(1, 6)) }
    val state by flow.collectAsState()

    val ready = state as? CrossPromoRepository.PromoState.Ready ?: return
    if (ready.response.apps.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item { Spacer(Modifier.width(4.dp)) }
            itemsIndexed(
                ready.response.apps.take(limit.coerceIn(1, 6)),
                key = { _, app -> app.packageName },
            ) { index, app ->
                HartmannPromoCard(
                    app = app,
                    placement = placement,
                    rankPosition = index + 1,
                    requestId = ready.response.requestId,
                    sourcePackage = holder.sourcePackage,
                )
            }
            item { Spacer(Modifier.width(4.dp)) }
        }
    }
}

/**
 * Single promo card. Branded as our own cross-promotion ("View App" CTA) —
 * never styled as a Google ad or Play Store recommendation.
 */
@Composable
fun HartmannPromoCard(
    app: PromoApp,
    placement: String,
    rankPosition: Int,
    requestId: String?,
    sourcePackage: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val holder = remember { HartmannCrossPromo.requireHolder() }
    val tracker = remember { HartmannCrossPromo.impressionTracker() }
    val impressionKey = remember(requestId, app.packageName, placement) {
        tracker.key(requestId, app.packageName, placement)
    }

    // Count one impression when the card first composes (LazyRow items compose
    // on visibility). The tracker dedupes across recompositions.
    LaunchedEffect(impressionKey) {
        if (tracker.shouldTrack(impressionKey)) {
            runCatching {
                holder.analytics.impression(
                    sourcePackage = sourcePackage,
                    targetPackage = app.packageName,
                    placement = placement,
                    rankPosition = rankPosition,
                    selectionType = app.selectionType,
                    sessionId = holder.processSessionId,
                    recommendationRequestId = requestId,
                    sdkVersion = HartmannCrossPromo.SDK_VERSION,
                )
            }
        }
    }

    val cardDescription = "${app.name}. ${app.shortDescription ?: "App by Hartmann Studios"}. " +
        (app.rating?.let { "Rated $it stars. " } ?: "") + "View app on Google Play."

    Card(
        modifier = modifier
            .width(260.dp)
            .semantics { contentDescription = cardDescription }
            .clickable {
                holder.analytics.click(
                    sourcePackage = sourcePackage,
                    targetPackage = app.packageName,
                    placement = placement,
                    rankPosition = rankPosition,
                    selectionType = app.selectionType,
                    sessionId = holder.processSessionId,
                    recommendationRequestId = requestId,
                    sdkVersion = HartmannCrossPromo.SDK_VERSION,
                )
                PlayStoreLauncher.launch(context, app.packageName, sourcePackage)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!app.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = app.iconUrl,
                    contentDescription = null, // card already describes the app
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name.ifBlank { app.packageName },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!app.shortDescription.isNullOrBlank()) {
                    Text(
                        text = app.shortDescription!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                app.rating?.let { rating ->
                    Text(
                        text = "★ ${String.format(java.util.Locale.US, "%.1f", rating)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    holder.analytics.click(
                        sourcePackage = sourcePackage,
                        targetPackage = app.packageName,
                        placement = placement,
                        rankPosition = rankPosition,
                        selectionType = app.selectionType,
                        sessionId = holder.processSessionId,
                        recommendationRequestId = requestId,
                        sdkVersion = HartmannCrossPromo.SDK_VERSION,
                    )
                    PlayStoreLauncher.launch(context, app.packageName, sourcePackage)
                }) {
                    Text(stringResource(R.string.hcp_view_app))
                }
            }
        }
    }
}
