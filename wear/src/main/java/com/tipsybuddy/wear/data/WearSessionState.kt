package com.tipsybuddy.wear.data

data class WearSessionState(
    val currentBac: Double = 0.0,
    val bacZoneTitle: String = "Sober",
    val bacZoneColorHex: Long = 0xFF10B981,
    val hoursToSober: Double = 0.0,
    val soberTargetTimeFormatted: String = "",
    val alcoholCount: Int = 0,
    val waterCount: Int = 0,
    val standardDrinks: Double = 0.0,
    val hydrationScorePercent: Int = 100,
    val latestVenueName: String = "",
    val emergencyPhone: String = "",
    val lastSyncedTimestamp: Long = System.currentTimeMillis()
)
