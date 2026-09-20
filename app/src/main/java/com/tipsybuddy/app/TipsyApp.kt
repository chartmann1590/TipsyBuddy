package com.tipsybuddy.app

import android.app.Application
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
    }
}
