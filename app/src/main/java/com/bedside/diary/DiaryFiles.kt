package com.bedside.diary

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * 일기를 마크다운 파일로 저장한다. v1은 앱 외부 파일 디렉터리에 둔다
 * (파일 관리자에서 열람 가능). Documents/Diary로 옮기는 건 후속.
 */
object DiaryFiles {

    fun save(context: Context, date: LocalDate, markdown: String): File {
        val dir = File(context.getExternalFilesDir(null), "Diary").apply { mkdirs() }
        val file = File(dir, "$date.md")
        file.writeText(markdown)
        return file
    }
}
