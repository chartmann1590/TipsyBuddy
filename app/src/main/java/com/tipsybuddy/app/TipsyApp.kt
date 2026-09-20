package com.tipsybuddy.app

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
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
    }
}
