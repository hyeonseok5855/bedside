package com.bedside.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 수집된 이벤트 한 건. 소스별로 표를 나누지 않고 통일된 이벤트 타입에 적립한다
 * (karlicoss/HPI 패턴 — decisions.md 벤치마크). 이 값들은 일기의 내용이 아니라
 * 질문의 재료다.
 */
@Entity(tableName = "events")
data class CollectedEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "geofence" | "steps" | "sleep" | "photo" ... */
    val source: String,
    /** "enter" | "exit" | "snapshot" ... */
    val type: String,
    /** 사람이 읽을 라벨(예: "집", "회사"). 없으면 null. */
    val label: String? = null,
    /** 페이로드(좌표, 수치 등). 형식은 소스마다. 없으면 null. */
    val value: String? = null,
    /** 실제 발생 시각(epoch millis). */
    val occurredAt: Long,
    /** DB에 적립한 시각(epoch millis). */
    val recordedAt: Long,
)
