package com.tipsybuddy.app.wear

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "PhoneWearListenerService received message: ${messageEvent.path}")
        PhoneWearSyncManager.getInstance(applicationContext).onMessageReceived(messageEvent)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "PhoneWearListenerService received data changed")
        PhoneWearSyncManager.getInstance(applicationContext).onDataChanged(dataEvents)
    }

    companion object {
        private const val TAG = "PhoneWearListener"
    }
}
