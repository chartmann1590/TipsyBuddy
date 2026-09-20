package com.tipsybuddy.wear.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.tipsybuddy.wear.data.WearHealthVitals
import com.tipsybuddy.wear.sync.WearSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class HeartRateSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val _peakHeartRate = MutableStateFlow(0)
    val peakHeartRate: StateFlow<Int> = _peakHeartRate.asStateFlow()

    private val _stepsTonight = MutableStateFlow(0)
    val stepsTonight: StateFlow<Int> = _stepsTonight.asStateFlow()

    private var initialStepOffset = -1

    val hasHeartRateSensor: Boolean
        get() = heartRateSensor != null

    val hasStepSensor: Boolean
        get() = stepCounterSensor != null

    fun startListening() {
        heartRateSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        stepCounterSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values.firstOrNull()?.roundToInt() ?: 0
                if (bpm > 30) {
                    _currentHeartRate.value = bpm
                    if (bpm > _peakHeartRate.value) {
                        _peakHeartRate.value = bpm
                    }
                    syncCurrentVitals()
                }
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values.firstOrNull()?.toInt() ?: 0
                if (initialStepOffset == -1) {
                    initialStepOffset = totalSteps
                }
                val currentSessionSteps = (totalSteps - initialStepOffset).coerceAtLeast(0)
                _stepsTonight.value = currentSessionSteps
                syncCurrentVitals()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name} -> $accuracy")
    }

    fun syncCurrentVitals() {
        val hr = _currentHeartRate.value
        val peak = _peakHeartRate.value
        val steps = _stepsTonight.value
        val isTachycardia = hr >= 95

        val vitals = WearHealthVitals(
            heartRateBpm = hr,
            peakHeartRateBpm = peak,
            stepsTonight = steps,
            isTachycardiaRisk = isTachycardia,
            lastRecordedTimestamp = System.currentTimeMillis()
        )
        WearSyncManager.getInstance(context).sendHealthStats(vitals)
    }

    companion object {
        private const val TAG = "WearHealthSensor"
    }
}
