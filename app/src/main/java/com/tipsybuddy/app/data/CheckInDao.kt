package com.tipsybuddy.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: CheckInEntity): Long

    @Query("SELECT * FROM check_ins ORDER BY timestamp DESC LIMIT 1")
    fun getLatestCheckIn(): Flow<CheckInEntity?>

    @Query("SELECT * FROM check_ins WHERE sessionDate = :date ORDER BY timestamp DESC LIMIT 1")
    suspend fun getCheckInForDate(date: String): CheckInEntity?

    @Query("SELECT * FROM check_ins ORDER BY timestamp DESC")
    fun getAllCheckIns(): Flow<List<CheckInEntity>>

    @Query("DELETE FROM check_ins")
    suspend fun clearAll()
}
