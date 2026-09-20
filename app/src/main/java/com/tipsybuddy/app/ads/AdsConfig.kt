package com.tipsybuddy.app.ads

import com.tipsybuddy.app.BuildConfig

/**
 * Central AdMob unit ID configuration.
 *
 * Debug / default builds use Google's official test unit IDs so ads always load
 * in development without risking invalid traffic against a production account.
 *
 * ---------------------------------------------------------------------------
 * WHERE TO PUT PRODUCTION ADMOB IDs
 * ---------------------------------------------------------------------------
 * 1) AndroidManifest.xml — <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID"
 *    android:value="${admobAppId}" />  (placeholder filled from build.gradle.kts)
 * 2) app/build.gradle.kts — release buildType BuildConfig fields:
 *      ADMOB_APP_ID, ADMOB_BANNER_ID, ADMOB_INTERSTITIAL_ID
 *    and manifestPlaceholders["admobAppId"]
 * 3) Optionally override via this object if you prefer a single source of truth.
 *
 * Create units in AdMob console → Apps → TipsyBuddy → Ad units.
 * Never commit real publisher secrets beyond public ad unit IDs.
 */
object AdsConfig {

    // Google's official sample / test IDs (safe for debug & CI).
    // https://developers.google.com/admob/android/test-ads
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    /**
     * PRODUCTION PLACEHOLDERS — replace in app/build.gradle.kts release {} before Play upload.
     * Example shape (do not use these literally):
     *   ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY   (App ID)
     *   ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ   (Banner / Interstitial)
     */
    const val PRODUCTION_APP_ID_PLACEHOLDER = "ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY"
    const val PRODUCTION_BANNER_PLACEHOLDER = "ca-app-pub-XXXXXXXXXXXXXXXX/BBBBBBBBBB"
    const val PRODUCTION_INTERSTITIAL_PLACEHOLDER = "ca-app-pub-XXXXXXXXXXXXXXXX/IIIIIIIIII"

    val appId: String
        get() = BuildConfig.ADMOB_APP_ID.ifBlank { TEST_APP_ID }

    val bannerUnitId: String
        get() = BuildConfig.ADMOB_BANNER_ID.ifBlank { TEST_BANNER_UNIT_ID }

    val interstitialUnitId: String
        get() = BuildConfig.ADMOB_INTERSTITIAL_ID.ifBlank { TEST_INTERSTITIAL_UNIT_ID }

    /** True when still on Google test IDs (expected for debug). */
    val usingTestAds: Boolean
        get() = bannerUnitId == TEST_BANNER_UNIT_ID ||
            interstitialUnitId == TEST_INTERSTITIAL_UNIT_ID ||
            appId == TEST_APP_ID
}
