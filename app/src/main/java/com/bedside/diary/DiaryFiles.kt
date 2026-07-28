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

    /** 일기 목록 항목: 날짜("yyyy-MM-dd")와 미리보기 한 줄. */
    data class Entry(val date: String, val preview: String)

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
            ?.map { Entry(date = it.name.removeSuffix(".md"), preview = firstLine(it.readText())) }
            ?: emptyList()

    /** 문체 레퍼런스용 최근 일기 본문(오늘 제외). 결정 23. */
    fun recentBodies(context: Context, excludeDate: String, limit: Int): List<String> =
        list(context).asSequence()
            .filter { it.date != excludeDate }
            .take(limit)
            .map { read(context, it.date) }
            .filter { it.isNotBlank() }
            .toList()

    private fun firstLine(markdown: String): String =
        markdown.lineSequence()
            .map { it.trim().removePrefix("#").trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("![") }
            ?.take(50)
            ?: ""
}
