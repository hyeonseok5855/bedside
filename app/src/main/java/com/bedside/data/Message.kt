package com.bedside.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 대화 한 턴. 원문 transcript는 원자산이라 매 턴 즉시 저장한다(결정 13, concept.md).
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 세션 날짜 "yyyy-MM-dd". */
    val sessionDate: String,
    /** "assistant" | "user" */
    val role: String,
    val text: String,
    val createdAt: Long,
)
