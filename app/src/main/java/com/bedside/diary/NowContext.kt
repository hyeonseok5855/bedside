package com.bedside.diary

import android.content.Context
import com.bedside.calendar.CalendarReader
import com.bedside.location.LocationNow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "지금"의 맥락 — 시각·요일·시간대·위치(그리고 오늘 일정). 하루 종일 이어지는 대화라
 * 매 메시지 직전에 새로 만들어 시스템 프롬프트 뒤에 붙인다. AI가 "수요일 오전 9시 반,
 * 회사 근처 → 방금 출근했겠네"처럼 지금이 하루의 어느 지점인지 헤아리게 한다.
 * 나열 금지는 프롬프트가 담당 — 여기선 재료만 준다.
 */
object NowContext {

    private val dayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.KOREAN)
    private val timeFmt = DateTimeFormatter.ofPattern("a h시 m분", Locale.KOREAN)

    suspend fun build(context: Context): String {
        val now = LocalDateTime.now()
        val where = LocationNow.currentPlace(context)
        val calendar = CalendarReader.formatForContext(CalendarReader.today(context))
        return render(now, where, calendar)
    }

    /** 순수 조립부. 단위 테스트 가능. */
    internal fun render(now: LocalDateTime, where: String?, calendar: String): String = buildString {
        append("# 지금 (질문에 참고, 나열하지 말 것)\n")
        append("- 시각: ").append(now.format(dayFmt)).append(' ')
            .append(now.format(timeFmt)).append(" (").append(timeOfDay(now.hour)).append(")\n")
        if (!where.isNullOrBlank()) append("- 위치: ").append(where).append('\n')
        if (calendar.isNotBlank()) append(calendar)
    }

    private fun timeOfDay(hour: Int): String = when (hour) {
        in 5..8 -> "이른 아침"
        in 9..11 -> "오전"
        in 12..13 -> "점심때"
        in 14..17 -> "오후"
        in 18..21 -> "저녁"
        else -> "밤/새벽"
    }
}
