package com.bedside.diary

import android.content.Context
import com.bedside.data.CollectedEvent
import com.bedside.data.Db
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 하루치 수집 이벤트를 텍스트 브리핑으로 만든다. LLM 시스템 프롬프트 뒤에 붙는다.
 *
 * 자료가 빈약해도 괜찮다 — 인터뷰어는 이걸 나열하지 않고 질문의 재료로만 쓴다
 * (data-sources.md 브리핑 규칙). 아무 것도 없으면 통상적인 질문으로 시작한다.
 */
object DayBriefing {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    suspend fun build(context: Context, date: LocalDate): String {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val events = Db.get(context).events().recent(200)
            .filter { it.occurredAt in start until end }
        return render(events)
    }

    /**
     * 이벤트 목록 → 브리핑 텍스트. DB/Android에 의존하지 않는 순수 함수라 단위 테스트한다.
     * 시각순 정렬은 여기서 한다. 비어 있으면 통상 질문 안내를 돌려준다.
     */
    internal fun render(events: List<CollectedEvent>): String {
        if (events.isEmpty()) {
            return "오늘 수집된 데이터가 거의 없다. 통상적인 질문으로 편하게 시작하라."
        }
        val lines = events.sortedBy { it.occurredAt }.map { e ->
            val t = timeFmt.format(Instant.ofEpochMilli(e.occurredAt))
            val what = when (e.source) {
                "geofence" -> "${e.label ?: ""} ${if (e.type == "enter") "도착" else "이탈"}"
                "steps" -> "걸음 ${e.value}"
                "photo" -> "사진 ${e.value}"
                "weight" -> "몸무게 ${e.value}kg"
                else -> "${e.source} ${e.value ?: ""}"
            }.trim()
            "- $t $what"
        }
        return "오늘의 타임라인(질문 재료, 나열하지 말 것):\n" + lines.joinToString("\n")
    }
}
