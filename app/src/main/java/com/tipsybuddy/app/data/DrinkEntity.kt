package com.tipsybuddy.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drinks")
data class DrinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String, // Beer, Wine, Cocktail, Shot, Seltzer, Water, Custom
    val volumeOz: Double,
    val abv: Double,
    val price: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionDate: String // Format: YYYY-MM-DD
)
