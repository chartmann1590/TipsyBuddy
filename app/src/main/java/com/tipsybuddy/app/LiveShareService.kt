package com.tipsybuddy.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tipsybuddy.app.data.AppDatabase
import com.tipsybuddy.app.data.FirebaseLiveSync
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.data.showWaveNotification
import com.tipsybuddy.app.domain.BacCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Foreground service that keeps pushing location/BAC to Firestore and polling for waves
 * while live sharing is on, so syncing continues even when the app is backgrounded or the
 * screen is off. The persistent notification is required by Android to allow this.
 */
class LiveShareService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var userPrefs: UserPreferences
    private lateinit var liveSync: FirebaseLiveSync
    private lateinit var db: AppDatabase
    private lateinit var locationManager: LocationManager
    @Volatile private var latestLocation: Location? = null
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (isLocationFresh(location) && location.elapsedRealtimeNanos >= (latestLocation?.elapsedRealtimeNanos ?: 0L)) {
                latestLocation = location
            }
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Required for Android 8 compatibility")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private fun isLocationFresh(location: Location): Boolean {
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return ageNanos in 0L..MAX_LOCATION_AGE_NANOS
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        userPrefs = UserPreferences(this)
        liveSync = FirebaseLiveSync(this)
        db = AppDatabase.getInstance(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!userPrefs.isLiveSharing || userPrefs.liveExpiresAt <= System.currentTimeMillis() || !hasLocationPermission()) {
            userPrefs.isLiveSharing = false
            stopSelf()
            return
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification("Waiting for GPS fix…"),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
            startLocationUpdates()
        } catch (e: SecurityException) {
            userPrefs.isLiveSharing = false
            stopSelf()
            return
        }
        startSyncLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        if (::locationManager.isInitialized) locationManager.removeUpdates(locationListener)
        super.onDestroy()
    }

    private fun startSyncLoop() {
        scope.launch {
            while (userPrefs.isLiveSharing) {
                if (userPrefs.liveExpiresAt <= System.currentTimeMillis() || !hasLocationPermission()) {
                    userPrefs.isLiveSharing = false
                    break
                }

                val location = latestLocation?.takeIf { isLocationFresh(it) }
                val dateStr = userPrefs.activeNightDate.ifBlank {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                }
                val drinks = db.drinkDao().getDrinksForDateDirect(dateStr)
                val bac = BacCalculator.calculate(
                    drinks = drinks,
                    weightLbs = userPrefs.weightLbs,
                    gender = userPrefs.gender
                ).bac

                val synced = location != null && liveSync.syncSession(
                    sessionId = userPrefs.liveSessionId,
                    userName = userPrefs.userName,
                    venue = userPrefs.liveVenueName.ifBlank { "Night Out" },
                    status = userPrefs.liveStatusMessage,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    bac = bac,
                    homeAddress = userPrefs.homeAddress,
                    phone = userPrefs.emergencyContactPhone,
                    expiresAtMillis = userPrefs.liveExpiresAt
                )

                val cheerAt = liveSync.fetchLatestCheer(userPrefs.liveSessionId)
                if (cheerAt != null && cheerAt != userPrefs.lastSeenCheerAt) {
                    userPrefs.lastSeenCheerAt = cheerAt
                    showWaveNotification(this@LiveShareService)
                }

                updateNotification(
                    when {
                        location == null -> "Waiting for GPS fix…"
                        synced -> "Last synced ${timeNow()} • lat ${"%.4f".format(location.latitude)}"
                        else -> "Sync unavailable • retrying"
                    }
                )

                delay(SYNC_INTERVAL_MS)
            }
            stopSelf()
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        val precise = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = if (precise) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            else listOf(LocationManager.NETWORK_PROVIDER)
        providers.filter { it in locationManager.allProviders }.forEach { provider ->
            locationManager.getLastKnownLocation(provider)?.let { cached ->
                if (isLocationFresh(cached)) {
                    locationListener.onLocationChanged(cached)
                }
            }
            locationManager.requestLocationUpdates(provider, SYNC_INTERVAL_MS, 0f, locationListener, Looper.getMainLooper())
        }
    }

    private fun timeNow() = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())

    private fun buildNotification(statusText: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Sharing Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while your location is being shared with friends"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🟢 Live sharing active")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    companion object {
        private const val CHANNEL_ID = "live_share_status"
        private const val NOTIFICATION_ID = 4042
        private const val SYNC_INTERVAL_MS = 8000L
        private const val MAX_LOCATION_AGE_MS = 60_000L
        private val MAX_LOCATION_AGE_NANOS = TimeUnit.MILLISECONDS.toNanos(MAX_LOCATION_AGE_MS)
    }
}
