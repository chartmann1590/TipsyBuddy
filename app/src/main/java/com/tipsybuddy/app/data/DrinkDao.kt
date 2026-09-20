package com.tipsybuddy.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DrinkDao {
    @Insert
    suspend fun insertDrink(drink: DrinkEntity): Long

    @Delete
    suspend fun deleteDrink(drink: DrinkEntity)

    @Query("SELECT * FROM drinks WHERE sessionDate = :date ORDER BY timestamp DESC")
    fun getDrinksForSession(date: String): Flow<List<DrinkEntity>>

    @Query("SELECT * FROM drinks ORDER BY timestamp DESC")
    fun getAllDrinks(): Flow<List<DrinkEntity>>

    @Query("SELECT DISTINCT sessionDate FROM drinks WHERE category != 'Water' ORDER BY sessionDate DESC")
    fun getAllDrinkingDates(): Flow<List<String>>

    @Query("SELECT * FROM drinks WHERE sessionDate = :date ORDER BY timestamp ASC")
    suspend fun getDrinksForDateDirect(date: String): List<DrinkEntity>

    @Query("DELETE FROM drinks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM drinks")
    suspend fun clearAll()
}
