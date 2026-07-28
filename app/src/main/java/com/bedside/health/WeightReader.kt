package com.bedside.health

import java.time.Instant

/** 몸무게 표본 하나. */
data class WeightSample(
    val time: Instant,
    val kilograms: Double,
)

/**
 * 몸무게 읽기. 수면·걸음과 마찬가지로 벤더를 인터페이스 뒤에 둔다.
 *
 * 몸무게는 매일 재는 값이 아니라 "가장 최근 기록"이 의미 있다. 이 값도 일기에
 * 나열하는 게 아니라 필요할 때 질문 재료로만 쓴다.
 */
interface WeightReader {
    /** 기준 시각 이전의 가장 최근 몸무게. 기록이 없으면 null. */
    suspend fun readLatestWeight(reference: Instant): WeightSample?
}
