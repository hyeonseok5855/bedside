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

    // sessionDate는 ISO(yyyy-MM-dd)라 문자열 비교로 날짜 범위가 성립한다.
    @Query("SELECT * FROM messages WHERE sessionDate >= :since ORDER BY id ASC")
    suspend fun since(since: String): List<Message>
}
