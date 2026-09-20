package com.tipsybuddy.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import com.tipsybuddy.wear.data.WearHealthVitals
import com.tipsybuddy.wear.data.WearQuickDrink
import com.tipsybuddy.wear.data.WearSessionState
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearSyncManager private constructor(private val context: Context) :
    DataClient.OnDataChangedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
    private val gson = Gson()

    private val _sessionState = MutableStateFlow(WearSessionState())
    val sessionState: StateFlow<WearSessionState> = _sessionState.asStateFlow()

    private val _isPhoneConnected = MutableStateFlow(false)
    val isPhoneConnected: StateFlow<Boolean> = _isPhoneConnected.asStateFlow()

    init {
        dataClient.addListener(this)
        capabilityClient.addListener(this, CAPABILITY_PHONE_APP)
        checkConnection()
        loadCurrentData()
        requestStateRefresh()
    }

    fun checkConnection() {
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                _isPhoneConnected.value = nodes.isNotEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking phone connection", e)
            }
        }
    }

    private fun loadCurrentData() {
        scope.launch {
            try {
                val dataItemBuffer = dataClient.getDataItems().await()
                for (item in dataItemBuffer) {
                    if (item.uri.path == PATH_SESSION_STATE) {
                        parseSessionDataItem(DataMapItem.fromDataItem(item).dataMap)
                    }
                }
                dataItemBuffer.release()
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading current session data", e)
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == PATH_SESSION_STATE) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                parseSessionDataItem(dataMap)
            }
        }
    }

    private fun parseSessionDataItem(dataMap: DataMap) {
        val state = WearSessionState(
            currentBac = dataMap.getDouble("bac", 0.0),
            bacZoneTitle = dataMap.getString("zone_title", "Sober"),
            bacZoneColorHex = dataMap.getLong("zone_color", 0xFF10B981),
            hoursToSober = dataMap.getDouble("hours_to_sober", 0.0),
            soberTargetTimeFormatted = dataMap.getString("sober_time", ""),
            alcoholCount = dataMap.getInt("alcohol_count", 0),
            waterCount = dataMap.getInt("water_count", 0),
            standardDrinks = dataMap.getDouble("standard_drinks", 0.0),
            hydrationScorePercent = dataMap.getInt("hydration_score", 100),
            latestVenueName = dataMap.getString("latest_venue", ""),
            emergencyPhone = dataMap.getString("emergency_phone", ""),
            lastSyncedTimestamp = dataMap.getLong("timestamp", System.currentTimeMillis())
        )
        _sessionState.value = state
        Log.d(TAG, "Updated session state: BAC=${state.currentBac}, Zone=${state.bacZoneTitle}")
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _isPhoneConnected.value = capabilityInfo.nodes.isNotEmpty()
    }

    fun requestStateRefresh() {
        scope.launch {
            sendMessageToPhone(PATH_REQUEST_REFRESH, ByteArray(0))
        }
    }

    fun sendQuickAddDrink(drink: WearQuickDrink) {
        scope.launch {
            val actionId = UUID.randomUUID().toString()
            val payload = mapOf(
                "actionId" to actionId,
                "name" to drink.name,
                "category" to drink.category,
                "volumeOz" to drink.volumeOz,
                "abv" to drink.abv,
                "price" to drink.price,
                "timestamp" to System.currentTimeMillis()
            )
            val json = gson.toJson(payload)
            queueActionViaDataApi(actionId, ACTION_TYPE_DRINK, json)
            sendMessageToPhone(PATH_QUICK_ADD_DRINK, json.toByteArray(Charsets.UTF_8))
        }
    }

    fun sendQuickCheckIn(venueName: String, address: String = "") {
        scope.launch {
            val actionId = UUID.randomUUID().toString()
            val payload = mapOf(
                "actionId" to actionId,
                "venueName" to venueName,
                "address" to address,
                "timestamp" to System.currentTimeMillis()
            )
            val json = gson.toJson(payload)
            queueActionViaDataApi(actionId, ACTION_TYPE_CHECK_IN, json)
            sendMessageToPhone(PATH_QUICK_CHECK_IN, json.toByteArray(Charsets.UTF_8))
        }
    }

    fun sendHealthStats(vitals: WearHealthVitals) {
        scope.launch {
            try {
                val putDataReq = PutDataMapRequest.create(PATH_HEALTH_STATS).apply {
                    dataMap.putInt("heart_rate", vitals.heartRateBpm)
                    dataMap.putInt("peak_heart_rate", vitals.peakHeartRateBpm)
                    dataMap.putInt("steps", vitals.stepsTonight)
                    dataMap.putBoolean("tachycardia_risk", vitals.isTachycardiaRisk)
                    dataMap.putLong("timestamp", vitals.lastRecordedTimestamp)
                }.asPutDataRequest().setUrgent()

                dataClient.putDataItem(putDataReq).await()
                Log.d(TAG, "Health stats synced via DataLayer: HR=${vitals.heartRateBpm}, Steps=${vitals.stepsTonight}")
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing health stats", e)
            }
        }
    }

    fun sendRequestRide() {
        scope.launch {
            val actionId = UUID.randomUUID().toString()
            val payload = mapOf(
                "actionId" to actionId,
                "timestamp" to System.currentTimeMillis()
            )
            val json = gson.toJson(payload)
            queueActionViaDataApi(actionId, ACTION_TYPE_RIDE, json)
            sendMessageToPhone(PATH_REQUEST_RIDE, json.toByteArray(Charsets.UTF_8))
        }
    }

    private suspend fun queueActionViaDataApi(actionId: String, actionType: String, payloadJson: String) {
        try {
            val putDataReq = PutDataMapRequest.create("$PATH_ACTIONS_PREFIX$actionId").apply {
                dataMap.putString("action_id", actionId)
                dataMap.putString("action_type", actionType)
                dataMap.putString("payload", payloadJson)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(putDataReq).await()
            Log.d(TAG, "Queued action $actionType ($actionId) in Wear DataLayer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue action $actionType via DataClient", e)
        }
    }

    private suspend fun sendMessageToPhone(path: String, data: ByteArray) {
        try {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected phone found for message $path; queued in DataLayer")
                return
            }
            for (node in nodes) {
                messageClient.sendMessage(node.id, path, data).await()
                Log.d(TAG, "Sent message $path to node ${node.displayName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message $path to phone (persisted via DataLayer)", e)
        }
    }

    companion object {
        private const val TAG = "TipsyWearSync"
        const val CAPABILITY_PHONE_APP = "tipsy_buddy_phone"
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
        private var instance: WearSyncManager? = null

        fun getInstance(context: Context): WearSyncManager {
            return instance ?: synchronized(this) {
                instance ?: WearSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
