package com.tipsybuddy.wear.data

data class WearHealthVitals(
    val heartRateBpm: Int = 0,
    val peakHeartRateBpm: Int = 0,
    val stepsTonight: Int = 0,
    val isTachycardiaRisk: Boolean = false,
    val lastRecordedTimestamp: Long = System.currentTimeMillis()
)
