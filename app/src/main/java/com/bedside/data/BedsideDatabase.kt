package com.bedside.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CollectedEvent::class], version = 1, exportSchema = false)
abstract class BedsideDatabase : RoomDatabase() {
    abstract fun events(): EventDao
}
