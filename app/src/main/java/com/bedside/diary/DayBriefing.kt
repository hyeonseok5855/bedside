package com.bedside.diary

import android.content.Context
import com.bedside.data.CollectedEvent
import com.bedside.data.Db
import com.bedside.health.HealthAvailability
import com.bedside.health.HealthConnectReader
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
        // 수면은 지난밤 기록이라 오늘 이벤트 창에 안 걸린다. 대화 시점에 직접 읽어 붙인다
        // (수집기는 매시간 돌아 중복이 되므로 여기서 라이브로). 결정 41.
        return listOf(sleepLine(context), render(events))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    /** 지난밤 수면 한 줄. 권한 없거나 기록 없으면 "". */
    private suspend fun sleepLine(context: Context): String {
        val health = HealthConnectReader(context)
        // 수면만 직접 읽는다. hasReadPermission()은 수면·걸음·몸무게 '전부'를 요구해서,
        // 몸무게 권한이 없으면 수면까지 막혀 버린다(설정 화면은 직접 읽어서 됐던 이유). 결정 41.
        if (runCatching { health.availability() }.getOrNull() != HealthAvailability.AVAILABLE) return ""
        val s = runCatching { health.readLastNight(Instant.now()) }.getOrNull() ?: return ""
        val h = s.totalMinutes / 60
        val m = s.totalMinutes % 60
        val range = "${timeFmt.format(s.start)}~${timeFmt.format(s.end)}"
        val stages = if (s.stageMinutes.isEmpty()) {
            ""
        } else {
            " · " + s.stageMinutes.entries.joinToString(", ") { "${it.key} ${it.value}분" }
        }
        return "지난밤 수면(질문 재료): 총 ${h}시간 ${m}분 ($range)$stages"
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
