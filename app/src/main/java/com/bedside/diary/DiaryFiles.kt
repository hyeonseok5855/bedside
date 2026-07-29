package com.bedside.diary

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * 일기를 마크다운 파일로 저장·열람한다. v1은 앱 외부 파일 디렉터리에 둔다
 * (파일 관리자에서 열람 가능). Documents/Diary로 옮기는 건 후속.
 */
object DiaryFiles {

    private fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "Diary").apply { mkdirs() }

    private fun fileFor(context: Context, date: String): File = File(dir(context), "$date.md")
    private fun moodFileFor(context: Context, date: String): File = File(dir(context), "$date.mood")

    /** 일기 목록 항목: 날짜("yyyy-MM-dd") + 미리보기 한 줄 + 무드(없으면 null). */
    data class Entry(val date: String, val preview: String, val mood: String? = null)

    /** 검색 결과: 날짜 + 매치 주변 스니펫. */
    data class Hit(val date: String, val snippet: String)

    fun save(context: Context, date: LocalDate, markdown: String): File {
        val file = fileFor(context, date.toString())
        file.writeText(markdown)
        return file
    }

    fun read(context: Context, date: String): String {
        val file = fileFor(context, date)
        return if (file.exists()) file.readText() else ""
    }

    /** 최신순 일기 목록. */
    fun list(context: Context): List<Entry> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.sortedByDescending { it.name }
            ?.map {
                val date = it.name.removeSuffix(".md")
                Entry(date = date, preview = firstLine(it.readText()), mood = getMood(context, date))
            }
            ?: emptyList()

    // 무드는 일기 본문과 분리된 사이드카(.mood)에 단어 하나로. 본문엔 이모지·센서를 넣지
    // 않는다는 규칙을 지키면서, 편집·회고·문체참조가 무드에 오염되지 않게 한다.
    fun getMood(context: Context, date: String): String? =
        moodFileFor(context, date).let { if (it.exists()) it.readText().trim().ifBlank { null } else null }

    fun setMood(context: Context, date: String, mood: String?) {
        val f = moodFileFor(context, date)
        if (mood.isNullOrBlank()) { if (f.exists()) f.delete() } else f.writeText(mood.trim())
    }

    /** 본문 전문 검색(대소문자 무시). 최신순, 매치 주변 스니펫과 함께. */
    fun search(context: Context, query: String): List<Hit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return list(context).mapNotNull { entry ->
            val body = read(context, entry.date)
            val idx = body.indexOf(q, ignoreCase = true)
            if (idx < 0) return@mapNotNull null
            val from = (idx - 20).coerceAtLeast(0)
            val to = (idx + q.length + 40).coerceAtMost(body.length)
            val snippet = body.substring(from, to).replace("\n", " ").trim()
            Hit(entry.date, (if (from > 0) "…" else "") + snippet + (if (to < body.length) "…" else ""))
        }
    }

    /** [from, to] 구간(ISO 날짜 문자열, 포함)의 일기를 오래된 순으로. 회고용. */
    fun bodiesInRange(context: Context, from: String, to: String): List<Pair<String, String>> =
        list(context)
            .filter { it.date in from..to }
            .sortedBy { it.date }
            .map { it.date to read(context, it.date) }

    /** 문체 레퍼런스용 최근 일기 본문(오늘 제외). 결정 23. */
    fun recentBodies(context: Context, excludeDate: String, limit: Int): List<String> =
        list(context).asSequence()
            .filter { it.date != excludeDate }
            .take(limit)
            .map { read(context, it.date) }
            .filter { it.isNotBlank() }
            .toList()

    /** 그때의 오늘 회상 항목: 얼마나 전인지 라벨 + 날짜 + 미리보기. */
    data class Recall(val label: String, val date: String, val preview: String)

    /** 그날의 수면 통계(분). 인사이트용, 일기와 함께 사이드카(.stats)에 저장(결정 49). */
    data class Stats(val sleepMin: Int, val deepMin: Int, val remMin: Int, val awakeMin: Int)

    private fun statsFileFor(context: Context, date: String): File = File(dir(context), "$date.stats")

    fun saveStats(context: Context, date: LocalDate, s: Stats) {
        statsFileFor(context, date.toString())
            .writeText("sleep=${s.sleepMin},deep=${s.deepMin},rem=${s.remMin},awake=${s.awakeMin}")
    }

    fun getStats(context: Context, date: String): Stats? {
        val f = statsFileFor(context, date)
        if (!f.exists()) return null
        val m = f.readText().split(",").mapNotNull {
            val kv = it.split("=")
            if (kv.size == 2) kv[0].trim() to (kv[1].trim().toIntOrNull() ?: 0) else null
        }.toMap()
        return Stats(m["sleep"] ?: 0, m["deep"] ?: 0, m["rem"] ?: 0, m["awake"] ?: 0)
    }

    /** 일주일/한 달/1년 전 '오늘'의 일기 중 존재하는 것들(결정 47). */
    fun onThisDay(context: Context, today: LocalDate): List<Recall> {
        val horizons = listOf(
            "일주일 전" to today.minusWeeks(1),
            "한 달 전" to today.minusMonths(1),
            "1년 전" to today.minusYears(1),
        )
        return horizons.mapNotNull { (label, d) ->
            val date = d.toString()
            val body = read(context, date)
            if (body.isBlank()) null else Recall(label, date, firstLine(body))
        }
    }

    private fun firstLine(markdown: String): String =
        markdown.lineSequence()
            .map { it.trim().removePrefix("#").trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("![") }
            ?.take(50)
            ?: ""
}
