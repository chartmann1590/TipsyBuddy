plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tipsybuddy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tipsybuddy.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Default = Google official test AdMob IDs (safe for debug / local builds).
        // PRODUCTION: override these in buildTypes.release below before Play upload.
        // See docs/ADMOB.md and AdsConfig.kt.
        val testAppId = "ca-app-pub-3940256099942544~3347511713"
        val testBannerId = "ca-app-pub-3940256099942544/6300978111"
        val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"

        buildConfigField("String", "ADMOB_APP_ID", "\"$testAppId\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$testInterstitialId\"")
        manifestPlaceholders["admobAppId"] = testAppId
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // -----------------------------------------------------------------
            // PRODUCTION AdMob IDs — replace placeholders before shipping.
            // Create App + Banner + Interstitial units in AdMob console, then:
            //   ADMOB_APP_ID          = ca-app-pub-XXXX~YYYY
            //   ADMOB_BANNER_ID       = ca-app-pub-XXXX/BBBB
            //   ADMOB_INTERSTITIAL_ID = ca-app-pub-XXXX/IIII
            // Until replaced, release still uses Google test IDs so builds work.
            // -----------------------------------------------------------------
            val prodAppId = "ca-app-pub-3940256099942544~3347511713" // TODO: production App ID
            val prodBannerId = "ca-app-pub-3940256099942544/6300978111" // TODO: production banner
            val prodInterstitialId = "ca-app-pub-3940256099942544/1033173712" // TODO: production interstitial
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

    // OpenStreetMap Android (100% free open-source map)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Wear OS Data Layer Communication
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google Mobile Ads (AdMob) — version from gradle/libs.versions.toml
    implementation(libs.play.services.ads)
}
