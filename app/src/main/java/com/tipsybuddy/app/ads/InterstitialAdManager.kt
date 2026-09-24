package com.tipsybuddy.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.tipsybuddy.app.billing.SubscriptionManager

/**
 * Loads / shows AdMob interstitials with light frequency capping.
 * Fails silently if an ad cannot load — never blocks drink logging UX.
 */
object InterstitialAdManager {

    private const val TAG = "TipsyAds"
    /** Minimum drinks logged between interstitial attempts. */
    private const val MIN_ACTIONS_BETWEEN_SHOWS = 3
    /** Minimum wall-clock gap between successful shows. */
    private const val MIN_INTERVAL_MS = 90_000L

    private var interstitial: InterstitialAd? = null
    private var isLoading = false
    private var actionsSinceLastShow = 0
    private var lastShownAtMs = 0L
    private var subscriptionManager: SubscriptionManager? = null

    private fun getSubscriptionManager(context: Context): SubscriptionManager {
        return subscriptionManager ?: SubscriptionManager.getInstance(context.applicationContext).also { subscriptionManager = it }
    }

    fun preload(context: Context) {
        if (getSubscriptionManager(context).isAdFree.value) return
        if (interstitial != null || isLoading) return
        isLoading = true
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            AdsConfig.interstitialUnitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    interstitial = ad
                    Log.d(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    interstitial = null
                    Log.w(TAG, "Interstitial failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Call after a natural moment (e.g. drink logged). Increments the action
     * counter and shows only when frequency caps allow and an ad is ready.
     */
    fun onNaturalMoment(activity: Activity?) {
        if (activity != null) {
            if (getSubscriptionManager(activity).isAdFree.value) return
        }
        actionsSinceLastShow++
        if (activity == null || activity.isFinishing) {
            preload(activity?.applicationContext ?: return)
            return
        }
        maybeShow(activity)
    }

    private fun maybeShow(activity: Activity) {
        if (getSubscriptionManager(activity).isAdFree.value) return
        val ready = interstitial
        val now = System.currentTimeMillis()
        val cappedByActions = actionsSinceLastShow < MIN_ACTIONS_BETWEEN_SHOWS
        val cappedByTime = (now - lastShownAtMs) < MIN_INTERVAL_MS

        if (ready == null || cappedByActions || cappedByTime) {
            if (ready == null) preload(activity)
            return
        }

        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial failed to show: ${error.message}")
                interstitial = null
                preload(activity)
            }

            override fun onAdShowedFullScreenContent() {
                lastShownAtMs = System.currentTimeMillis()
                actionsSinceLastShow = 0
            }
        }

        try {
            ready.show(activity)
            interstitial = null
        } catch (t: Throwable) {
            Log.w(TAG, "Interstitial show crashed (ignored): ${t.message}")
            interstitial = null
            preload(activity)
        }
    }
}


/** Walk ContextWrappers to the hosting Activity (Compose LocalContext is often wrapped). */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}
