package com.tipsybuddy.app

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.hartmann.crosspromo.HartmannCrossPromo
import com.tipsybuddy.app.ads.AdsConfig
import com.tipsybuddy.app.ads.InterstitialAdManager
import org.osmdroid.config.Configuration
import java.io.File

class TipsyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Configure OpenStreetMap caching
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = packageName
        osmConfig.osmdroidBasePath = File(cacheDir, "osmdroid")
        osmConfig.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")

        // Initialize Google Play Billing / Subscription Manager
        com.tipsybuddy.app.billing.SubscriptionManager.getInstance(this)

        // Initialize Google ML Kit Translation Engine
        com.tipsybuddy.app.translation.AppTranslationManager.initialize(this)

        // Google Mobile Ads (AdMob) — uses test unit IDs in debug by default
        try {
            MobileAds.initialize(this) { status ->
                Log.d(
                    "TipsyAds",
                    "MobileAds initialized (testAds=${AdsConfig.usingTestAds}): $status"
                )
            }
            InterstitialAdManager.preload(this)
        } catch (t: Throwable) {
            Log.w("TipsyAds", "MobileAds init failed (ads disabled): ${t.message}")
        }

        // Hartmann Studios dynamic cross-promotion (optional; silent when unconfigured).
        // Source package is detected automatically from context.packageName.
        try {
            val crossPromoUrl = BuildConfig.CROSS_PROMO_URL
            if (crossPromoUrl.isNotBlank()) {
                HartmannCrossPromo.initialize(application = this, apiBaseUrl = crossPromoUrl)
                Log.d("TipsyCrossPromo", "Cross-promo SDK initialized")
            } else {
                Log.d("TipsyCrossPromo", "Cross-promo unconfigured (CROSS_PROMO_URL blank)")
            }
        } catch (t: Throwable) {
            Log.w("TipsyCrossPromo", "Cross-promo init failed (promos disabled): ${t.message}")
        }
    }
}
