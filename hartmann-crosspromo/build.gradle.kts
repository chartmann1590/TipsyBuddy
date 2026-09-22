plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hartmann.crosspromo"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        // No permissions declared here on purpose. In particular this SDK
        // must NEVER request QUERY_ALL_PACKAGES; installed-app detection is
        // intentionally unsupported to stay clear of Play policy risk.
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Icon loading (Compose + View). Host apps already using Coil share the cache.
    implementation(libs.coil.compose)
    implementation(libs.coil.base)

    // ---- Unit tests (pure JVM, no device needed) ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
