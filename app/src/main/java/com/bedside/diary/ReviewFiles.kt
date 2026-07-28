package com.bedside.diary

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * 주간·월간 회고를 마크다운 파일로 저장·열람한다. 일기(Diary/)와 분리된 Review/ 아래.
 * id는 주간이 "2026-W30", 월간이 "2026-07" 형태.
 */
object ReviewFiles {

    enum class Kind { WEEK, MONTH }

    data class Entry(val id: String, val kind: Kind, val preview: String)

    private fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "Review").apply { mkdirs() }

    private fun fileFor(context: Context, id: String): File = File(dir(context), "$id.md")

    fun weekId(date: LocalDate): String {
        val wf = WeekFields.ISO
        val week = date.get(wf.weekOfWeekBasedYear())
        val year = date.get(wf.weekBasedYear())
        return "%d-W%02d".format(year, week)
    }

    fun monthId(date: LocalDate): String = "%d-%02d".format(date.year, date.monthValue)

    /** 주간 회고가 덮는 [월, 일] 구간(ISO 주: 월~일). */
    fun weekRange(date: LocalDate): Pair<LocalDate, LocalDate> {
        val monday = date.with(WeekFields.ISO.dayOfWeek(), 1)
        return monday to monday.plusDays(6)
    }

    fun monthRange(date: LocalDate): Pair<LocalDate, LocalDate> {
        val first = date.withDayOfMonth(1)
        return first to first.withDayOfMonth(date.lengthOfMonth())
    }

    fun exists(context: Context, id: String): Boolean = fileFor(context, id).exists()

    fun read(context: Context, id: String): String {
        val f = fileFor(context, id)
        return if (f.exists()) f.readText() else ""
    }

    fun save(context: Context, id: String, markdown: String): File {
        val f = fileFor(context, id)
        f.writeText(markdown)
        return f
    }

    fun list(context: Context): List<Entry> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.sortedByDescending { it.name }
            ?.map {
                val id = it.name.removeSuffix(".md")
                val kind = if (id.contains("-W")) Kind.WEEK else Kind.MONTH
                Entry(id, kind, firstLine(it.readText()))
            }
            ?: emptyList()

    private fun firstLine(markdown: String): String =
        markdown.lineSequence()
            .map { it.trim().removePrefix("#").trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(50)
            ?: ""
}
