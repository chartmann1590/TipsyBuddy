package com.tipsybuddy.wear.sync

import android.util.Log
import com.google.android.gms.wearable.*

class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged received ${dataEvents.count} events")
        val syncManager = WearSyncManager.getInstance(applicationContext)
        syncManager.onDataChanged(dataEvents)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived: path=${messageEvent.path}")
    }

    companion object {
        private const val TAG = "WearDataListener"
    }
}
