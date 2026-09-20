package com.tipsybuddy.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val venueName: String,
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionDate: String
)
