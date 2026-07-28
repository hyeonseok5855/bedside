package com.bedside.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CollectedEvent::class, Message::class], version = 2, exportSchema = false)
abstract class BedsideDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun messages(): MessageDao
}
