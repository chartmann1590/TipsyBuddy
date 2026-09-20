package com.tipsybuddy.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DrinkEntity::class, CheckInEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drinkDao(): DrinkDao
    abstract fun checkInDao(): CheckInDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tipsy_buddy.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
