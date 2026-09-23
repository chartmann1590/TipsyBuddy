import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProps = Properties()
run {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { stream -> localProps.load(stream) }
    }
}

android {
    namespace = "com.tipsybuddy.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tipsybuddy.app"
        minSdk = 30
        // targetSdk 34 (not 35): androidx.wear.compose.foundation 1.3.1's
        // ScalingLazyColumn reads the reduce_motion Settings.Global key,
        // which throws SecurityException for targetSdk 35+ on some system images.
        targetSdk = 34
        val envVersionCode = System.getenv("ANDROID_WEAR_VERSION_CODE")
            ?: System.getenv("ANDROID_VERSION_CODE")?.let { (it.toLong() + 1).toString() }
        val envVersionName = System.getenv("ANDROID_VERSION_NAME")
        versionCode = envVersionCode?.toIntOrNull() ?: 2
        versionName = envVersionName ?: "1.0.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val sf = localProps.getProperty("tipsybuddy.storeFile")
                ?: localProps.getProperty("nutrisnap.storeFile")
                ?: System.getenv("KEYSTORE_FILE")
            if (!sf.isNullOrBlank()) {
                val f = rootProject.file(sf)
                if (f.exists()) {
                    storeFile = f
                }
            }
            storePassword = localProps.getProperty("tipsybuddy.storePassword")
                ?: localProps.getProperty("nutrisnap.storePassword")
                ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = localProps.getProperty("tipsybuddy.keyAlias")
                ?: localProps.getProperty("nutrisnap.keyAlias")
                ?: System.getenv("KEY_ALIAS") ?: ""
            keyPassword = localProps.getProperty("tipsybuddy.keyPassword")
                ?: localProps.getProperty("nutrisnap.keyPassword")
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
        }
        debug {
            isMinifyEnabled = false
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
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // Wear OS Compose & UI
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.androidx.wear)

    // Google Play Services Wearable Data Layer
    implementation(libs.play.services.wearable)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.gson)
}
