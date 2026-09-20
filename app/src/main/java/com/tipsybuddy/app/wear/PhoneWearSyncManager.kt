package com.tipsybuddy.app.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import com.tipsybuddy.app.R
import com.tipsybuddy.app.data.AppDatabase
import com.tipsybuddy.app.data.CheckInEntity
import com.tipsybuddy.app.data.DrinkEntity
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.domain.BacCalculator
import com.tipsybuddy.app.domain.BacResult
import com.tipsybuddy.app.widget.TipsyWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class PhoneWatchVitals(
    val heartRateBpm: Int = 0,
    val peakHeartRateBpm: Int = 0,
    val stepsTonight: Int = 0,
    val isTachycardiaRisk: Boolean = false,
    val lastSyncedTimestamp: Long = 0L,
    val isWatchConnected: Boolean = false
)

class PhoneWearSyncManager private constructor(private val context: Context) :
    MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
    private val gson = Gson()

    private val _watchVitals = MutableStateFlow(loadCachedVitals())
    val watchVitals: StateFlow<PhoneWatchVitals> = _watchVitals.asStateFlow()

    private val processedActionIds = Collections.synchronizedSet(
        Collections.newSetFromMap(
            object : LinkedHashMap<String, Boolean>(100, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > 200
                }
            }
        )
    )

    private fun isAlreadyProcessed(id: String): Boolean {
        if (id.isBlank()) return false
        synchronized(processedActionIds) {
            if (processedActionIds.contains(id)) {
                return true
            }
            processedActionIds.add(id)
            return false
        }
    }

    init {
        // PhoneWearListenerService is registered in AndroidManifest.xml and forwards
        // MESSAGE_RECEIVED and DATA_CHANGED events to this singleton.
        // Dynamic registration of messageClient and dataClient is omitted to avoid duplicate execution.
        capabilityClient.addListener(this, CAPABILITY_WATCH_APP)
        checkWatchConnected()
    }

    fun checkWatchConnected() {
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                _watchVitals.value = _watchVitals.value.copy(isWatchConnected = nodes.isNotEmpty())
            } catch (e: Exception) {
                Log.e(TAG, "Error checking watch connection", e)
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _watchVitals.value = _watchVitals.value.copy(isWatchConnected = capabilityInfo.nodes.isNotEmpty())
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        scope.launch {
            try {
                when (messageEvent.path) {
                    PATH_QUICK_ADD_DRINK -> {
                        val json = String(messageEvent.data, Charsets.UTF_8)
                        val map = gson.fromJson(json, Map::class.java)
                        val actionId = map["actionId"]?.toString() ?: "${messageEvent.sourceNodeId}_${messageEvent.requestId}"
                        if (isAlreadyProcessed(actionId)) {
                            Log.d(TAG, "Duplicate quick drink message ignored: $actionId")
                            return@launch
                        }

                        val name = map["name"]?.toString() ?: "Drink"
                        val category = map["category"]?.toString() ?: "Custom"
                        val volumeOz = (map["volumeOz"] as? Number)?.toDouble() ?: 12.0
                        val abv = (map["abv"] as? Number)?.toDouble() ?: 5.0
                        val price = (map["price"] as? Number)?.toDouble() ?: 0.0

                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val db = AppDatabase.getInstance(context)
                        db.drinkDao().insertDrink(
                            DrinkEntity(
                                name = name,
                                category = category,
                                volumeOz = volumeOz,
                                abv = abv,
                                price = price,
                                timestamp = System.currentTimeMillis(),
                                sessionDate = todayStr
                            )
                        )
                        TipsyWidgetProvider.updateAllWidgets(context)
                        pushLatestStateToWatch()
                    }

                    PATH_QUICK_CHECK_IN -> {
                        val json = String(messageEvent.data, Charsets.UTF_8)
                        val map = gson.fromJson(json, Map::class.java)
                        val actionId = map["actionId"]?.toString() ?: "${messageEvent.sourceNodeId}_${messageEvent.requestId}"
                        if (isAlreadyProcessed(actionId)) {
                            Log.d(TAG, "Duplicate quick check-in message ignored: $actionId")
                            return@launch
                        }

                        val venueName = map["venueName"]?.toString() ?: "Venue"
                        val address = map["address"]?.toString() ?: ""

                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val db = AppDatabase.getInstance(context)
                        db.checkInDao().insertCheckIn(
                            CheckInEntity(
                                venueName = venueName,
                                address = address,
                                sessionDate = todayStr
                            )
                        )
                        pushLatestStateToWatch()
                    }

                    PATH_REQUEST_RIDE -> {
                        val actionId = "${messageEvent.sourceNodeId}_${messageEvent.requestId}"
                        if (isAlreadyProcessed(actionId)) {
                            Log.d(TAG, "Duplicate ride request ignored: $actionId")
                            return@launch
                        }
                        triggerRideHome()
                    }

                    PATH_REQUEST_REFRESH -> {
                        pushLatestStateToWatch()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling message ${messageEvent.path}", e)
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: ""
                when {
                    path == PATH_HEALTH_STATS -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val hr = dataMap.getInt("heart_rate", 0)
                        val peak = dataMap.getInt("peak_heart_rate", 0)
                        val steps = dataMap.getInt("steps", 0)
                        val isTachycardia = dataMap.getBoolean("tachycardia_risk", false)
                        val timestamp = dataMap.getLong("timestamp", System.currentTimeMillis())

                        val newVitals = PhoneWatchVitals(
                            heartRateBpm = hr,
                            peakHeartRateBpm = peak,
                            stepsTonight = steps,
                            isTachycardiaRisk = isTachycardia,
                            lastSyncedTimestamp = timestamp,
                            isWatchConnected = true
                        )
                        _watchVitals.value = newVitals
                        saveCachedVitals(newVitals)
                        Log.d(TAG, "Received watch health vitals: HR=$hr, Steps=$steps")
                    }

                    path.startsWith(PATH_ACTIONS_PREFIX) -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val actionId = dataMap.getString("action_id") ?: event.dataItem.uri.lastPathSegment ?: ""
                        val actionType = dataMap.getString("action_type") ?: ""
                        val payloadJson = dataMap.getString("payload") ?: ""
                        val uri = event.dataItem.uri

                        scope.launch {
                            try {
                                if (actionId.isNotEmpty() && !isAlreadyProcessed(actionId)) {
                                    handleOfflineAction(actionType, payloadJson)
                                } else {
                                    Log.d(TAG, "Action $actionId already processed, deleting DataItem")
                                }
                                dataClient.deleteDataItems(uri).await()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error handling offline action $actionId", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleOfflineAction(actionType: String, payloadJson: String) {
        when (actionType) {
            ACTION_TYPE_DRINK -> {
                val map = gson.fromJson(payloadJson, Map::class.java)
                val name = map["name"]?.toString() ?: "Drink"
                val category = map["category"]?.toString() ?: "Custom"
                val volumeOz = (map["volumeOz"] as? Number)?.toDouble() ?: 12.0
                val abv = (map["abv"] as? Number)?.toDouble() ?: 5.0
                val price = (map["price"] as? Number)?.toDouble() ?: 0.0
                val timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()

                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
                val db = AppDatabase.getInstance(context)
                db.drinkDao().insertDrink(
                    DrinkEntity(
                        name = name,
                        category = category,
                        volumeOz = volumeOz,
                        abv = abv,
                        price = price,
                        timestamp = timestamp,
                        sessionDate = todayStr
                    )
                )
                TipsyWidgetProvider.updateAllWidgets(context)
                pushLatestStateToWatch()
            }

            ACTION_TYPE_CHECK_IN -> {
                val map = gson.fromJson(payloadJson, Map::class.java)
                val venueName = map["venueName"]?.toString() ?: "Venue"
                val address = map["address"]?.toString() ?: ""
                val timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()

                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
                val db = AppDatabase.getInstance(context)
                db.checkInDao().insertCheckIn(
                    CheckInEntity(
                        venueName = venueName,
                        address = address,
                        sessionDate = todayStr
                    )
                )
                pushLatestStateToWatch()
            }

            ACTION_TYPE_RIDE -> {
                triggerRideHome()
            }
        }
    }

    fun pushSessionState(
        bacResult: BacResult,
        latestVenue: String = "",
        emergencyPhone: String = ""
    ) {
        scope.launch {
            try {
                val soberTimeStr = if (bacResult.hoursToSober > 0.0) {
                    val soberCal = Calendar.getInstance().apply {
                        add(Calendar.MINUTE, (bacResult.hoursToSober * 60).toInt())
                    }
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(soberCal.time)
                } else {
                    "Now"
                }

                val putDataReq = PutDataMapRequest.create(PATH_SESSION_STATE).apply {
                    dataMap.putDouble("bac", bacResult.bac)
                    dataMap.putString("zone_title", bacResult.zone.title)
                    dataMap.putLong("zone_color", bacResult.zone.colorHex)
                    dataMap.putDouble("hours_to_sober", bacResult.hoursToSober)
                    dataMap.putString("sober_time", soberTimeStr)
                    dataMap.putInt("alcohol_count", bacResult.alcoholCount)
                    dataMap.putInt("water_count", bacResult.waterCount)
                    dataMap.putDouble("standard_drinks", bacResult.standardDrinks)
                    dataMap.putInt("hydration_score", bacResult.hydrationScorePercent)
                    dataMap.putString("latest_venue", latestVenue)
                    dataMap.putString("emergency_phone", emergencyPhone)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                dataClient.putDataItem(putDataReq).await()
                Log.d(TAG, "Pushed session state to watch: BAC=${bacResult.bac}")
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing session state to watch", e)
            }
        }
    }

    fun pushLatestStateToWatch() {
        scope.launch {
            val db = AppDatabase.getInstance(context)
            val userPrefs = UserPreferences(context)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val drinks = db.drinkDao().getDrinksForDateDirect(todayStr)
            val latestCheckIn = db.checkInDao().getLatestCheckInDirect()

            val bacResult = BacCalculator.calculate(
                drinks = drinks,
                weightLbs = userPrefs.weightLbs,
                gender = userPrefs.gender
            )

            pushSessionState(
                bacResult = bacResult,
                latestVenue = latestCheckIn?.venueName ?: "",
                emergencyPhone = userPrefs.emergencyContactPhone
            )
        }
    }

    private fun triggerRideHome() {
        val userPrefs = UserPreferences(context)
        val preferredRide = userPrefs.preferredRide
        val isLyft = preferredRide.equals("LYFT", ignoreCase = true)
        val rideName = if (isLyft) "Lyft" else "Uber"

        val appUri = if (isLyft) Uri.parse("lyft://") else Uri.parse("uber://")
        val webUri = if (isLyft) Uri.parse("https://ride.lyft.com") else Uri.parse("https://m.uber.com")

        val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val targetIntent = if (appIntent.resolveActivity(context.packageManager) != null) {
            appIntent
        } else {
            Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            RIDE_NOTIFICATION_ID,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_RIDE_REQUESTS,
                "Ride Home Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts triggered from Wear OS to summon rides home"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_RIDE_REQUESTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚗 Ride Home Requested from Watch")
            .setContentText("Tap to open $rideName and confirm your pickup.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "Open $rideName",
                    pendingIntent
                ).build()
            )
            .build()

        try {
            notificationManager.notify(RIDE_NOTIFICATION_ID, notification)
            Log.d(TAG, "Posted ride home notification for $rideName")
        } catch (e: Exception) {
            Log.e(TAG, "Error posting ride notification", e)
        }

        // Attempt direct launch if allowed by current foreground context
        try {
            context.startActivity(targetIntent)
        } catch (e: Exception) {
            Log.d(TAG, "Direct activity launch restricted (Android 10+ background restriction); notification posted")
        }
    }

    private fun saveCachedVitals(vitals: PhoneWatchVitals) {
        val prefs = context.getSharedPreferences("wear_vitals_cache", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("hr", vitals.heartRateBpm)
            .putInt("peak_hr", vitals.peakHeartRateBpm)
            .putInt("steps", vitals.stepsTonight)
            .putBoolean("tachycardia", vitals.isTachycardiaRisk)
            .putLong("timestamp", vitals.lastSyncedTimestamp)
            .apply()
    }

    private fun loadCachedVitals(): PhoneWatchVitals {
        val prefs = context.getSharedPreferences("wear_vitals_cache", Context.MODE_PRIVATE)
        return PhoneWatchVitals(
            heartRateBpm = prefs.getInt("hr", 0),
            peakHeartRateBpm = prefs.getInt("peak_hr", 0),
            stepsTonight = prefs.getInt("steps", 0),
            isTachycardiaRisk = prefs.getBoolean("tachycardia", false),
            lastSyncedTimestamp = prefs.getLong("timestamp", 0L)
        )
    }

    companion object {
        private const val TAG = "PhoneWearSync"
        private const val RIDE_NOTIFICATION_ID = 4040
        private const val CHANNEL_RIDE_REQUESTS = "ride_requests_channel"
        const val CAPABILITY_WATCH_APP = "tipsy_buddy_watch"
        const val PATH_SESSION_STATE = "/tipsy/session_state"
        const val PATH_HEALTH_STATS = "/tipsy/health_stats"
        const val PATH_ACTIONS_PREFIX = "/tipsy/actions/"
        const val ACTION_TYPE_DRINK = "quick_add_drink"
        const val ACTION_TYPE_CHECK_IN = "quick_check_in"
        const val ACTION_TYPE_RIDE = "request_ride"
        const val PATH_QUICK_ADD_DRINK = "/tipsy/quick_add_drink"
        const val PATH_QUICK_CHECK_IN = "/tipsy/quick_check_in"
        const val PATH_REQUEST_RIDE = "/tipsy/request_ride"
        const val PATH_REQUEST_REFRESH = "/tipsy/request_refresh"

        @Volatile
        private var instance: PhoneWearSyncManager? = null

        fun getInstance(context: Context): PhoneWearSyncManager {
            return instance ?: synchronized(this) {
                instance ?: PhoneWearSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
