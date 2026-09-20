package com.tipsybuddy.wear.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.tipsybuddy.wear.data.WearHealthVitals
import com.tipsybuddy.wear.sync.WearSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class HeartRateSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val prefs = context.getSharedPreferences("wear_sensor_prefs", Context.MODE_PRIVATE)

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

    init {
        restorePersistedBaseline()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun restorePersistedBaseline() {
        val todayStr = getTodayDateString()
        val savedStepDate = prefs.getString(KEY_STEP_BASELINE_DATE, null)
        if (savedStepDate == todayStr) {
            initialStepOffset = prefs.getInt(KEY_STEP_BASELINE_OFFSET, -1)
            Log.d(TAG, "Restored step baseline offset: $initialStepOffset for $todayStr")
        } else {
            initialStepOffset = -1
        }

        val savedPeakDate = prefs.getString(KEY_PEAK_HR_DATE, null)
        if (savedPeakDate == todayStr) {
            val savedPeak = prefs.getInt(KEY_PEAK_HR, 0)
            _peakHeartRate.value = savedPeak
            Log.d(TAG, "Restored peak HR: $savedPeak for $todayStr")
        }
    }

    fun startListening() {
        val hasBodySensors = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        val hasActivityRecognition = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasBodySensors) {
            heartRateSensor?.let {
                try {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                    Log.d(TAG, "Registered heart rate sensor listener")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException registering heart rate sensor", e)
                }
            }
        } else {
            Log.w(TAG, "Cannot start heart rate sensor: BODY_SENSORS permission not granted")
        }

        if (hasActivityRecognition) {
            stepCounterSensor?.let {
                try {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                    Log.d(TAG, "Registered step counter sensor listener")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException registering step counter sensor", e)
                }
            }
        } else {
            Log.w(TAG, "Cannot start step sensor: ACTIVITY_RECOGNITION permission not granted")
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val todayStr = getTodayDateString()

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values.firstOrNull()?.roundToInt() ?: 0
                if (bpm > 30) {
                    _currentHeartRate.value = bpm
                    if (bpm > _peakHeartRate.value) {
                        _peakHeartRate.value = bpm
                        prefs.edit()
                            .putString(KEY_PEAK_HR_DATE, todayStr)
                            .putInt(KEY_PEAK_HR, bpm)
                            .apply()
                    }
                    syncCurrentVitals()
                }
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values.firstOrNull()?.toInt() ?: 0
                val savedStepDate = prefs.getString(KEY_STEP_BASELINE_DATE, null)
                val savedOffset = prefs.getInt(KEY_STEP_BASELINE_OFFSET, -1)

                // Establish new baseline if new session/day, not yet set, or device was rebooted (totalSteps < savedOffset)
                if (savedStepDate != todayStr || savedOffset == -1 || totalSteps < savedOffset) {
                    initialStepOffset = totalSteps
                    prefs.edit()
                        .putString(KEY_STEP_BASELINE_DATE, todayStr)
                        .putInt(KEY_STEP_BASELINE_OFFSET, totalSteps)
                        .apply()
                    Log.d(TAG, "Established new step baseline: $totalSteps for $todayStr")
                } else {
                    initialStepOffset = savedOffset
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
        private const val KEY_STEP_BASELINE_DATE = "step_baseline_date"
        private const val KEY_STEP_BASELINE_OFFSET = "step_baseline_offset"
        private const val KEY_PEAK_HR_DATE = "peak_hr_date"
        private const val KEY_PEAK_HR = "peak_hr"
    }
}
