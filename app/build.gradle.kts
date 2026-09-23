import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// AdMob ID resolution — no production IDs are hardcoded in this file.
// Priority (first non-blank wins):
//   1. Gradle property  -P ADMOB_APP_ID / ADMOB_BANNER_ID / ADMOB_INTERSTITIAL_ID
//      (or the same keys in ~/.gradle/gradle.properties)
//   2. Environment      ADMOB_APP_ID / ADMOB_BANNER_ID / ADMOB_INTERSTITIAL_ID
//      (GitHub Actions maps repository Secrets of the same names to env —
//       see .github/workflows/android-release.yml)
//   3. local.properties admob.app.id / admob.banner.id / admob.interstitial.id
//      (gitignored — local dev machine only, safe for real IDs)
//   4. Fallback: Google official test IDs (safe, never real traffic)
// Debug builds always use the test IDs. Release builds use the resolved IDs,
// so CI with Secrets (or a dev machine with local.properties) gets production
// ads while every other build keeps working with test ads.
// ---------------------------------------------------------------------------
val localAdMobProps = Properties()
run {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { stream -> localAdMobProps.load(stream) }
    }
}

fun resolveAdMobId(
    gradleKey: String,
    localKey: String,
    envKey: String,
    fallback: String
): String {
    val fromGradle = project.findProperty(gradleKey)?.toString()
    if (!fromGradle.isNullOrBlank()) {
        return fromGradle.trim()
    }
    val fromEnv = System.getenv(envKey)
    if (!fromEnv.isNullOrBlank()) {
        return fromEnv.trim()
    }
    val fromLocal = localAdMobProps.getProperty(localKey)
    if (!fromLocal.isNullOrBlank()) {
        return fromLocal.trim()
    }
    return fallback
}

android {
    namespace = "com.tipsybuddy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tipsybuddy.app"
        minSdk = 26
        targetSdk = 35
        val envVersionCode = System.getenv("ANDROID_VERSION_CODE")
        val envVersionName = System.getenv("ANDROID_VERSION_NAME")
        versionCode = envVersionCode?.toIntOrNull() ?: 1
        versionName = envVersionName ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Default = Google official test AdMob IDs (safe for debug / local builds).
        // Release resolves production IDs from Secrets / local.properties.
        // See docs/ADMOB.md and AdsConfig.kt.
        val testAppId = "ca-app-pub-3940256099942544~3347511713"
        val testBannerId = "ca-app-pub-3940256099942544/6300978111"
        val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"

        buildConfigField("String", "ADMOB_APP_ID", "\"$testAppId\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$testInterstitialId\"")
        manifestPlaceholders["admobAppId"] = testAppId

        // Feedback Worker URL (non-secret). Resolution order:
        //   1. Gradle property  -P feedback.worker.url (or ~/.gradle/gradle.properties)
        //   2. Environment      FEEDBACK_WORKER_URL
        //   3. local.properties feedback.worker.url (gitignored, local dev only)
        //   4. Fallback: deployed production Worker URL (non-secret).
        // Never put a GitHub PAT here; Android holds no GitHub credentials.
        val feedbackWorkerUrl = run {
            val fromGradle = project.findProperty("feedback.worker.url")?.toString()?.trim()
            if (!fromGradle.isNullOrBlank()) return@run fromGradle
            val fromEnv = System.getenv("FEEDBACK_WORKER_URL")?.trim()
            if (!fromEnv.isNullOrBlank()) return@run fromEnv
            val fromLocal = localAdMobProps.getProperty("feedback.worker.url")?.trim()
            if (!fromLocal.isNullOrBlank()) return@run fromLocal
            "https://tipsybuddy-feedback-api.charles-h-hartmann1.workers.dev"
        }
        buildConfigField("String", "FEEDBACK_WORKER_URL", "\"$feedbackWorkerUrl\"")

        // Dynamic cross-promotion backend URL (non-secret). Resolution order:
        //   1. Gradle property  -P crosspromo.api.url (or ~/.gradle/gradle.properties)
        //   2. Environment      CROSSPROMO_API_URL
        //   3. local.properties crosspromo.api.url (gitignored, local dev only)
        //   4. Fallback: "" (SDK stays uninitialized; Settings keeps legacy list).
        val crossPromoUrl = run {
            val fromGradle = project.findProperty("crosspromo.api.url")?.toString()?.trim()
            if (!fromGradle.isNullOrBlank()) return@run fromGradle
            val fromEnv = System.getenv("CROSSPROMO_API_URL")?.trim()
            if (!fromEnv.isNullOrBlank()) return@run fromEnv
            val fromLocal = localAdMobProps.getProperty("crosspromo.api.url")?.trim()
            if (!fromLocal.isNullOrBlank()) return@run fromLocal
            ""
        }
        buildConfigField("String", "CROSS_PROMO_URL", "\"$crossPromoUrl\"")
    }

    signingConfigs {
        create("release") {
            val sf = localAdMobProps.getProperty("tipsybuddy.storeFile")
                ?: localAdMobProps.getProperty("nutrisnap.storeFile")
                ?: System.getenv("KEYSTORE_FILE")
            if (!sf.isNullOrBlank()) {
                val f = rootProject.file(sf)
                if (f.exists()) {
                    storeFile = f
                }
            }
            storePassword = localAdMobProps.getProperty("tipsybuddy.storePassword")
                ?: localAdMobProps.getProperty("nutrisnap.storePassword")
                ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = localAdMobProps.getProperty("tipsybuddy.keyAlias")
                ?: localAdMobProps.getProperty("nutrisnap.keyAlias")
                ?: System.getenv("KEY_ALIAS") ?: ""
            keyPassword = localAdMobProps.getProperty("tipsybuddy.keyPassword")
                ?: localAdMobProps.getProperty("nutrisnap.keyPassword")
                ?: System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            }
            // Production AdMob IDs resolved from Secrets / local.properties.
            // Never hardcode real publisher IDs here — see header comment.
            // Without Secrets (or local.properties), release falls back to
            // Google test IDs so the build never breaks.
            val prodAppId = resolveAdMobId(
                "ADMOB_APP_ID", "admob.app.id", "ADMOB_APP_ID",
                "ca-app-pub-3940256099942544~3347511713"
            )
            val prodBannerId = resolveAdMobId(
                "ADMOB_BANNER_ID", "admob.banner.id", "ADMOB_BANNER_ID",
                "ca-app-pub-3940256099942544/6300978111"
            )
            val prodInterstitialId = resolveAdMobId(
                "ADMOB_INTERSTITIAL_ID", "admob.interstitial.id", "ADMOB_INTERSTITIAL_ID",
                "ca-app-pub-3940256099942544/1033173712"
            )
            // Log only WHICH pool is used — never the ID values themselves.
            val usingProdAds = !prodAppId.startsWith("ca-app-pub-3940256099942544~") ||
                !prodBannerId.startsWith("ca-app-pub-3940256099942544/") ||
                !prodInterstitialId.startsWith("ca-app-pub-3940256099942544/")
            println("AdMob release build: using " + if (usingProdAds) "PRODUCTION" else "TEST" + " ad IDs")
            buildConfigField("String", "ADMOB_APP_ID", "\"$prodAppId\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$prodBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$prodInterstitialId\"")
            manifestPlaceholders["admobAppId"] = prodAppId
        }
        debug {
            isMinifyEnabled = false
            // Explicit test IDs for clarity (same as defaultConfig)
            val testAppId = "ca-app-pub-3940256099942544~3347511713"
            val testBannerId = "ca-app-pub-3940256099942544/6300978111"
            val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"
            buildConfigField("String", "ADMOB_APP_ID", "\"$testAppId\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$testInterstitialId\"")
            manifestPlaceholders["admobAppId"] = testAppId
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // Explicit fragment version: registerForActivityResult (MainActivity) requires
    // androidx.fragment >= 1.3.0 for release lint; without this a transitive
    // 1.1.0 wins and :app:lintVitalRelease fails, blocking every prod build.
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking & JSON for Firebase Firestore REST Sync
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)

    // In-app GitHub-backed feedback reporter (via Cloudflare Worker proxy)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)

    // Hartmann Studios dynamic cross-promotion SDK (reusable module).
    // No new permissions; no QUERY_ALL_PACKAGES; no ad IDs.
    implementation(project(":hartmann-crosspromo"))

    // OpenStreetMap Android (100% free open-source map)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Wear OS Data Layer Communication
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google Mobile Ads (AdMob) — version from gradle/libs.versions.toml
    implementation(libs.play.services.ads)

    // Google ML Kit On-Device Translation (free, offline after model download)
    implementation(libs.mlkit.translate)
}
