package com.bedside.health

import java.time.Instant

/**
 * 걸음 수 읽기. 수면과 마찬가지로 벤더를 인터페이스 뒤에 둔다.
 *
 * 걸음 수도 일기에 나열되는 값이 아니라 질문의 재료다("오늘 많이 걸었네요, 어디
 * 다녀왔어요?"). concept.md 참고.
 */
interface StepsReader {
    /** 오늘(자정~기준 시각) 걸음 수 합계. 기록이 없으면 null. */
    suspend fun readTodaySteps(reference: Instant): Long?
}
