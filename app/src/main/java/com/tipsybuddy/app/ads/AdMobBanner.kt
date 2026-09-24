package com.tipsybuddy.app.ads

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdListener

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.tipsybuddy.app.billing.SubscriptionManager

private const val TAG = "TipsyAds"

/**
 * Adaptive / anchored banner via AndroidView. Failures are logged only —
 * the parent layout stays intact if the ad never loads.
 * Suppressed if the user has an active ad-free subscription.
 */
@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val subscriptionManager = remember { SubscriptionManager.getInstance(context) }
    val isAdFree by subscriptionManager.isAdFree.collectAsState()

    if (isAdFree) {
        return
    }
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                val adView = AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdsConfig.bannerUnitId
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.w(TAG, "Banner failed to load: ${error.message}")
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
                addView(
                    adView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
    )
}
