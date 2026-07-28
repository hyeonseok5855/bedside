package com.bedside.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: Message): Long

    @Query("SELECT * FROM messages WHERE sessionDate = :date ORDER BY id ASC")
    suspend fun forSession(date: String): List<Message>
}
