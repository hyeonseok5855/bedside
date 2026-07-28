package com.bedside.health

import java.time.Instant

/**
 * 하룻밤 수면 요약.
 *
 * 이 값들은 일기에 그대로 적히는 대상이 아니라, 대화 질문을 만들기 위한 재료다.
 * ("어젯밤 잘 잤어요?"의 근거) — CLAUDE.md / concept.md 참고.
 */
data class SleepSummary(
    val start: Instant,
    val end: Instant,
    val totalMinutes: Long,
    /** 단계 이름 → 분. 제공자가 단계를 안 주면 비어 있을 수 있다. */
    val stageMinutes: Map<String, Long>,
)

enum class HealthAvailability { AVAILABLE, UPDATE_REQUIRED, NOT_SUPPORTED }

/**
 * 수면 데이터 읽기. 벤더(Health Connect)를 인터페이스 뒤에 둔다.
 * 지금 구현은 Health Connect 하나뿐이지만 교체·테스트를 위해 분리한다.
 */
interface SleepReader {
    fun availability(): HealthAvailability
    suspend fun hasReadPermission(): Boolean

    /** 기준 시각 직전 밤의 수면 요약. 기록이 없으면 null. */
    suspend fun readLastNight(reference: Instant): SleepSummary?
}
