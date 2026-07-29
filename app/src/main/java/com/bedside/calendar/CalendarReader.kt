package com.bedside.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 오늘의 일정을 읽는다. 삼성 캘린더를 포함해 기기에 동기화된 모든 캘린더는
 * 안드로이드 표준 CalendarContract로 노출되므로 벤더 SDK가 필요 없다.
 *
 * 일정은 일기에 나열되는 게 아니라 질문의 재료다("오후에 미팅 있었네, 어땠어?").
 */
object CalendarReader {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    data class Event(
        val title: String,
        val start: Long,
        val end: Long,
        val allDay: Boolean,
        val location: String?,
    )

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** 오늘(자정~자정) 일정. 권한 없으면 빈 목록. 시작 시각 순. */
    suspend fun today(context: Context, date: LocalDate = LocalDate.now()): List<Event> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext emptyList()

            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            // Instances는 반복 일정을 펼쳐준다. URI 경로에 [begin, end] ms를 붙인다.
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(start.toString())
                .appendPath(end.toString())
                .build()
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
            )

            val out = mutableListOf<Event>()
            runCatching {
                context.contentResolver.query(
                    uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { c ->
                    val ti = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                    val bi = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                    val ei = c.getColumnIndexOrThrow(CalendarContract.Instances.END)
                    val ai = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                    val li = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                    while (c.moveToNext()) {
                        val title = c.getString(ti)?.trim().takeUnless { it.isNullOrBlank() } ?: "(제목 없음)"
                        out += Event(
                            title = title,
                            start = c.getLong(bi),
                            end = c.getLong(ei),
                            allDay = c.getInt(ai) == 1,
                            location = c.getString(li)?.trim().takeUnless { it.isNullOrBlank() },
                        )
                    }
                }
            }
            out.take(12)
        }

    /** NowContext에 넣을 텍스트. 비면 "". */
    fun formatForContext(events: List<Event>): String {
        if (events.isEmpty()) return ""
        return buildString {
            append("- 오늘 일정:\n")
            events.forEach { e ->
                val time = if (e.allDay) "종일" else timeFmt.format(Instant.ofEpochMilli(e.start))
                append("  - ").append(time).append(' ').append(e.title)
                e.location?.let { append(" (").append(it).append(')') }
                append('\n')
            }
        }
    }
}
