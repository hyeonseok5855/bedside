package com.bedside.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: CollectedEvent): Long

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Query("SELECT * FROM events ORDER BY occurredAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<CollectedEvent>
}
